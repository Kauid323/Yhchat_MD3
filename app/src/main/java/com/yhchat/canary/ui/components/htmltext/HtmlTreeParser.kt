package com.yhchat.canary.ui.components.htmltext

import com.mohamedrejeb.ksoup.html.parser.KsoupHtmlHandler
import com.mohamedrejeb.ksoup.html.parser.KsoupHtmlParser

private val HTML_VOID_TAGS = setOf("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr")

internal const val DETAILS_OPEN_STYLE_ATTR = "__details_open_style__"

private data class SelectorPart(
    val tag: String?,
    val ids: Set<String>,
    val directParent: Boolean = false,
    val classes: Set<String>,
    val attrPresence: Set<String>,
    val attrEquals: Map<String, String>
)

private data class CssRule(
    val selectorRaw: String,
    val parts: List<SelectorPart>,
    val declaration: String
)

private data class NodeContext(
    val tag: String,
    val attrs: Map<String, String>
)

private val styleBlockRegex = Regex("(?is)<style\\b[^>]*>(.*?)</style\\s*>")
private val cssAttrRegex = Regex("""\[\s*([\w-]+)\s*(?:=\s*(?:"([^"]*)"|'([^']*)'|([^\s\]]+))\s*)?\]""")
internal fun parseHtmlTree(html: String): HtmlNode.Element {
    val (htmlWithoutStyles, styleBlocks) = extractStyleBlocks(html)
    val cssRules = parseCssRules(styleBlocks)

    val root = HtmlNode.Element(tag = "#root")
    val stack = ArrayDeque<HtmlNode.Element>()
    stack.addLast(root)

    val parser = KsoupHtmlParser(
        handler = object : KsoupHtmlHandler {
            override fun onOpenTag(name: String, attributes: Map<String, String>, isImplied: Boolean) {
                val tag = name.lowercase()
                val normalizedAttrs = attributes.mapKeys { it.key.lowercase() }.toMutableMap()
                val matchedDeclarations = resolveMatchedDeclarations(
                    tag = tag,
                    attrs = normalizedAttrs,
                    ancestors = stack.map { NodeContext(it.tag, it.attrs) },
                    rules = cssRules
                )
                val openStateDeclaration = resolveDetailsOpenDeclaration(tag, cssRules)

                if (openStateDeclaration.isNotBlank()) {
                    normalizedAttrs[DETAILS_OPEN_STYLE_ATTR] = openStateDeclaration
                }

                val style = buildCssStyle(
                    declarations = matchedDeclarations,
                    inlineStyle = normalizedAttrs["style"].orEmpty()
                )

                when (tag) {
                    "br" -> stack.last().children.add(HtmlNode.Element(tag = tag, style = style, attrs = normalizedAttrs.toMap()))
                    "img" -> stack.last().children.add(
                        HtmlNode.Element(tag = tag, style = style, attrs = normalizedAttrs.toMap())
                    )
                    else -> {
                        val node = HtmlNode.Element(tag = tag, style = style, attrs = normalizedAttrs.toMap())
                        stack.last().children.add(node)
                        if (tag !in HTML_VOID_TAGS) stack.addLast(node)
                    }
                }
            }

            override fun onText(text: String) {
                if (text.isEmpty()) return
                stack.last().children.add(HtmlNode.Text(text))
            }

            override fun onCloseTag(name: String, isImplied: Boolean) {
                val tag = name.lowercase()
                if (tag in HTML_VOID_TAGS || stack.none { it.tag == tag }) return
                while (stack.size > 1) {
                    val last = stack.removeLast()
                    if (last.tag == tag) {
                        break
                    }
                }
            }
        }
    )

    parser.write(htmlWithoutStyles)
    parser.end()
    return root
}

private fun extractStyleBlocks(html: String): Pair<String, List<String>> {
    val blocks = mutableListOf<String>()
    val stripped = styleBlockRegex.replace(html) { match ->
        val content = match.groupValues.getOrNull(1).orEmpty()
        if (content.isNotBlank()) {
            blocks.add(content)
        }
        ""
    }
    return stripped to blocks
}

private fun parseCssRules(styleBlocks: List<String>): List<CssRule> {
    val rules = mutableListOf<CssRule>()
    styleBlocks.forEach { block ->
        readCssBlocks(block).forEach { rule ->
            val selectorGroup = rule.selector
            val declaration = rule.declarations.trim()
            if (selectorGroup.isBlank() || declaration.isBlank()) return@forEach

            splitCssTopLevel(selectorGroup, ',').forEach { rawSelector ->
                val normalizedSelector = rawSelector.trim()
                if (normalizedSelector.isBlank()) return@forEach

                val selectorTokens = tokenizeCssSelector(normalizedSelector)
                val parts = mutableListOf<SelectorPart>()
                var directParent = false
                var valid = true
                for (token in selectorTokens) {
                    if (token == ">") {
                        if (parts.isEmpty() || directParent) { valid = false; break }
                        directParent = true
                    } else {
                        val part = parseSelectorPart(token)
                        if (part == null) { valid = false; break }
                        parts.add(part.copy(directParent = directParent))
                        directParent = false
                    }
                }
                if (!valid || directParent || parts.isEmpty()) return@forEach
                rules.add(
                    CssRule(
                        selectorRaw = normalizedSelector.lowercase(),
                        parts = parts,
                        declaration = declaration
                    )
                )
            }
        }
    }
    return rules
}

private fun parseSelectorPart(rawPart: String): SelectorPart? {
    val attrPresence = linkedSetOf<String>()
    val attrEquals = linkedMapOf<String, String>()
    cssAttrRegex.findAll(rawPart).forEach { match ->
        val key = match.groupValues[1].trim().lowercase()
        attrPresence.add(key)
        if (match.value.contains('=')) {
            attrEquals[key] = match.groups[2]?.value ?: match.groups[3]?.value ?: match.groupValues[4]
        }
    }
    val simple = rawPart.replace(cssAttrRegex, "")
    // Unsupported pseudo-classes/combinators must not silently become unconditional selectors.
    if (!simple.matches(Regex("(?:[A-Za-z][\\w-]*|\\*)?(?:[.#][\\w-]+)*"))) return null
    val tag = Regex("^[A-Za-z][\\w-]*").find(simple)?.value?.lowercase()
    val classes = Regex("\\.([\\w-]+)").findAll(simple).map { it.groupValues[1] }.toSet()
    val ids = Regex("#([\\w-]+)").findAll(simple).map { it.groupValues[1] }.toSet()
    if (simple.isEmpty() && attrPresence.isEmpty()) return null
    return SelectorPart(tag = tag, ids = ids, classes = classes, attrPresence = attrPresence, attrEquals = attrEquals)
}
private fun resolveMatchedDeclarations(
    tag: String,
    attrs: Map<String, String>,
    ancestors: List<NodeContext>,
    rules: List<CssRule>
): List<String> {
    val current = NodeContext(tag = tag, attrs = attrs)
    val ancestorChain = ancestors.filter { it.tag != "#root" }
    return rules
        .filter { it.matches(ancestorChain, current) }
        .sortedWith(compareBy<CssRule> { rule -> rule.parts.sumOf { it.ids.size } }
            .thenBy { rule -> rule.parts.sumOf { it.classes.size + it.attrPresence.size } }
            .thenBy { rule -> rule.parts.count { it.tag != null } })
        .map { it.declaration }
}

private fun CssRule.matches(ancestors: List<NodeContext>, current: NodeContext): Boolean {
    if (parts.isEmpty()) return false
    if (!parts.last().matches(current)) return false
    // Backtrack through ancestor candidates: a nearer matching descendant may fail a
    // preceding direct-parent constraint while a farther one satisfies the whole selector.
    val memo = mutableMapOf<Pair<Int, Int>, Boolean>()
    fun matchPrevious(partIndex: Int, ancestorIndex: Int): Boolean {
        if (partIndex < 0) return true
        if (ancestorIndex < 0) return false
        return memo.getOrPut(partIndex to ancestorIndex) {
            if (parts[partIndex + 1].directParent) {
                parts[partIndex].matches(ancestors[ancestorIndex]) && matchPrevious(partIndex - 1, ancestorIndex - 1)
            } else {
                (ancestorIndex downTo 0).any { index ->
                    parts[partIndex].matches(ancestors[index]) && matchPrevious(partIndex - 1, index - 1)
                }
            }
        }
    }
    return matchPrevious(parts.lastIndex - 1, ancestors.lastIndex)
}
private fun SelectorPart.matches(node: NodeContext): Boolean {
    if (tag != null && tag != node.tag.lowercase()) return false
    if (ids.any { it != node.attrs["id"] }) return false

    val nodeAttrs = node.attrs.mapKeys { it.key.lowercase() }
    if (attrPresence.any { !nodeAttrs.containsKey(it) }) return false
    if (attrEquals.any { (key, value) -> nodeAttrs[key] != value }) return false

    if (classes.isNotEmpty()) {
        val nodeClasses = nodeAttrs["class"]
            .orEmpty()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .toSet()
        if (!classes.all { it in nodeClasses }) return false
    }
    return true
}

private fun buildCssStyle(declarations: List<String>, inlineStyle: String): CssStyle {
    val stylePayload = buildString {
        declarations.forEach { declaration ->
            val normalized = declaration.trim().removeSuffix(";")
            if (normalized.isNotBlank()) {
                append(normalized)
                append(';')
            }
        }
        val normalizedInline = inlineStyle.trim().removeSuffix(";")
        if (normalizedInline.isNotBlank()) {
            append(normalizedInline)
            append(';')
        }
    }
    return if (stylePayload.isBlank()) CssStyle() else CssParser.parseStyle(stylePayload)
}

private fun resolveDetailsOpenDeclaration(tag: String, rules: List<CssRule>): String {
    if (tag != "details") return ""
    return rules
        .asSequence()
        .filter { it.selectorRaw == "details[open]" }
        .map { it.declaration.trim().removeSuffix(";") }
        .filter { it.isNotBlank() }
        .joinToString(";")
}

/**
 * 单条 HTML 消息交给高级渲染器处理的最大长度。超过后降级为纯文本，
 * 避免解析/渲染体量过大的 HTML 导致内存溢出（OOM）或栈溢出。
 */
internal const val MAX_HTML_INPUT_LENGTH = 60_000

/** 高级渲染器允许渲染的最大节点数。 */
internal const val MAX_HTML_NODE_COUNT = 1_500

/** 高级渲染器允许的最大嵌套深度，避免深层嵌套导致栈溢出。 */
internal const val MAX_HTML_NESTING_DEPTH = 40

/** 降级展示时最多保留的纯文本字符数。 */
internal const val HTML_PLAIN_TEXT_LIMIT = 4_000

private val htmlTagRegex = Regex("(?s)<[^>]*>")
private val htmlHorizontalSpaceRegex = Regex("[\\t\\u000B\\f\\r ]+")
private val htmlBlankLineRegex = Regex("\\n{2,}")

/**
 * 在索引 [maxChars] 处截断字符串，但不会把一个代理对（surrogate pair）从中间切开。
 */
internal fun truncateAtSafeBoundary(text: String, maxChars: Int): String {
    if (maxChars <= 0) return ""
    if (text.length <= maxChars) return text
    var end = maxChars
    if (text[end].isLowSurrogate()) end -= 1
    return text.substring(0, end)
}

/**
 * 去掉 HTML 标签，得到用于降级展示的纯文本。
 *
 * 只会处理开头 [maxRawChars] 个字符，避免对超大字符串做正则替换导致内存问题。
 */
internal fun htmlToPlainText(html: String, maxRawChars: Int = MAX_HTML_INPUT_LENGTH): String {
    if (html.isEmpty()) return ""
    return truncateAtSafeBoundary(html, maxRawChars)
        .replace(htmlTagRegex, " ")
        .replace(htmlHorizontalSpaceRegex, " ")
        .replace(htmlBlankLineRegex, "\n")
        .trim()
}

/**
 * 判断节点树是否过于复杂（节点过多或嵌套过深）。
 *
 * 使用显式栈遍历，避免递归本身造成栈溢出。
 */
internal fun isHtmlTreeTooComplex(root: HtmlNode.Element): Boolean {
    val stack = ArrayDeque<Pair<HtmlNode.Element, Int>>()
    stack.addLast(root to 0)
    var nodeCount = 0
    while (stack.isNotEmpty()) {
        val (node, depth) = stack.removeLast()
        nodeCount++
        if (nodeCount > MAX_HTML_NODE_COUNT || depth > MAX_HTML_NESTING_DEPTH) return true
        node.children.forEach { child ->
            if (child is HtmlNode.Element) stack.addLast(child to depth + 1)
        }
    }
    return false
}

internal fun normalizeHtmlText(text: String, whiteSpace: String?): String {
    val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
    return when (whiteSpace?.lowercase()) {
        "pre", "pre-wrap", "break-spaces" -> normalized
        "pre-line" -> normalized.replace(Regex("[\\t\\u000B\\f ]+"), " ")
        else -> normalized.replace(Regex("[\\t\\n\\u000B\\f ]+"), " ")
    }
}
internal fun String.hasRenderableHtmlText(): Boolean {
    return any { char -> char == '\u00A0' || char == '\n' || !char.isWhitespace() }
}

internal fun positiveInt(value: String?, fallback: Int = 1): Int {
    val parsed = value?.trim()?.toIntOrNull()
    return if (parsed != null && parsed > 0) parsed else fallback
}
