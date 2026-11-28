package com.yhchat.canary.ui.components.htmltext

import org.jsoup.nodes.DataNode
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser

internal const val DETAILS_OPEN_STYLE_ATTR = "__details_open_style__"
internal const val SVG_STYLES_ATTR = "__svg_styles__"

private data class SelectorPart(
    val tag: String?,
    val ids: Set<String>,
    val directParent: Boolean = false,
    val classes: Set<String>,
    val attrPresence: Set<String>,
    val attrEquals: Map<String, String>,
    val pseudoClasses: List<String>
)

private data class CssRule(
    val selectorRaw: String,
    val parts: List<SelectorPart>,
    val declaration: String
)

private data class NodeContext(
    val tag: String,
    val attrs: Map<String, String>,
    val isRoot: Boolean,
    val elementIndex: Int,
    val siblingCount: Int,
    val isEmpty: Boolean
)

private fun Element.selectorContext(attrs: Map<String, String>): NodeContext = NodeContext(
    tag = normalName(),
    attrs = attrs,
    isRoot = parent() is org.jsoup.nodes.Document,
    elementIndex = elementSiblingIndex(),
    siblingCount = parent()?.childrenSize() ?: 1,
    // CSS :empty ignores comments, but even whitespace text prevents a match.
    isEmpty = childNodes().none { child ->
        child is Element || (child is TextNode && child.wholeText.isNotEmpty()) ||
            (child is DataNode && child.wholeData.isNotEmpty())
    }
)

private val supportedStructuralPseudos = setOf("root", "first-child", "last-child", "only-child", "empty")
private val cssPseudoRegex = Regex(":([\\w-]+)")

private val cssAttrRegex = Regex("""\[\s*([\w-]+)\s*(?:=\s*(?:"([^"]*)"|'([^']*)'|([^\s\]]+))\s*)?\]""")

internal fun parseHtmlTree(
    html: String,
    externalStylesheets: Map<String, String> = emptyMap()
): HtmlNode.Element {
    val source = parseNativeHtmlDocument(html)
    val cssRules = parseCssRules(extractStyleBlocks(source, externalStylesheets))

    fun convert(node: Node, ancestors: List<NodeContext>): HtmlNode? = when (node) {
        is TextNode -> HtmlNode.Text(node.wholeText)
        is DataNode -> HtmlNode.Text(node.wholeData)
        is Element -> {
            val tag = node.normalName()
            val attrs = node.htmlAttributes().toMutableMap()
            val context = node.selectorContext(attrs.toMap())
            val declarations = resolveMatchedDeclarations(context, ancestors, cssRules)
            val inlineStyle = attrs["style"].orEmpty()
            if (node.tag().namespace() == Parser.NamespaceSvg) {
                attrs[SVG_STYLES_ATTR] = buildRawStyle(declarations, inlineStyle)
            }
            resolveDetailsOpenDeclaration(tag, cssRules).takeIf { it.isNotBlank() }?.let {
                attrs[DETAILS_OPEN_STYLE_ATTR] = it
            }
            val result = HtmlNode.Element(
                tag = tag,
                style = buildCssStyle(declarations, inlineStyle),
                attrs = attrs.toMap()
            )
            // Keep metadata elements for selector ancestry, but never expose their source as text.
            val inert = tag == "script" || tag == "style" ||
                (tag == "template" && node.tag().namespace() == Parser.NamespaceHtml)
            if (!inert) {
                val childAncestors = ancestors + context
                node.childNodes().forEach { child ->
                    convert(child, childAncestors)?.let(result.children::add)
                }
            }
            result
        }
        else -> null // Comments, doctypes and processing instructions are not rendered.
    }

    val root = HtmlNode.Element(tag = "#root")
    source.document.childNodes().forEach { node ->
        convert(node, emptyList())?.let(root.children::add)
    }
    return root
}

/** Match styles only after HTML5 tree repair; template contents do not style the document. */
private fun extractStyleBlocks(
    source: ParsedHtmlDocument,
    externalStylesheets: Map<String, String>
): List<String> = buildList {
    source.activeElements.forEach { element ->
        when (element.normalName()) {
            "style" -> {
                val type = element.attr("type").trim()
                if (type.isEmpty() || type.equals("text/css", ignoreCase = true)) {
                    // Raw-text style contents are DataNodes; SVG style can contain TextNodes.
                    val css = buildString {
                        element.childNodes().forEach { child ->
                            when (child) {
                                is DataNode -> append(child.wholeData)
                                is TextNode -> append(child.wholeText)
                            }
                        }
                    }
                    if (css.isNotBlank()) add(css)
                }
            }
            "link" -> if (element.tag().namespace() == Parser.NamespaceHtml &&
                isStylesheetLink(element.htmlAttributes())) {
                externalStylesheets[element.attr("href")]?.takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }
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
    val withoutAttrs = rawPart.replace(cssAttrRegex, "")
    val pseudos = cssPseudoRegex.findAll(withoutAttrs).map { it.groupValues[1].lowercase() }.toList()
    if (pseudos.any { it !in supportedStructuralPseudos }) return null
    val simple = withoutAttrs.replace(cssPseudoRegex, "")
    // Reject unsupported functional pseudo-classes and combinators, rather than broadening a rule.
    if (!simple.matches(Regex("(?:[A-Za-z][\\w-]*|\\*)?(?:[.#][\\w-]+)*"))) return null
    val tag = Regex("^[A-Za-z][\\w-]*").find(simple)?.value?.lowercase()
    val classes = Regex("\\.([\\w-]+)").findAll(simple).map { it.groupValues[1] }.toSet()
    val ids = Regex("#([\\w-]+)").findAll(simple).map { it.groupValues[1] }.toSet()
    if (simple.isEmpty() && attrPresence.isEmpty() && pseudos.isEmpty()) return null
    return SelectorPart(
        tag = tag, ids = ids, classes = classes, attrPresence = attrPresence,
        attrEquals = attrEquals, pseudoClasses = pseudos
    )
}
private fun resolveMatchedDeclarations(
    current: NodeContext,
    ancestors: List<NodeContext>,
    rules: List<CssRule>
): List<String> {
    val ancestorChain = ancestors.filter { it.tag != "#root" }
    return rules
        .filter { it.matches(ancestorChain, current) }
        .sortedWith(compareBy<CssRule> { rule -> rule.parts.sumOf { it.ids.size } }
            .thenBy { rule -> rule.parts.sumOf { it.classes.size + it.attrPresence.size + it.pseudoClasses.size } }
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
    if (!pseudoClasses.all { pseudo ->
        when (pseudo) {
            "root" -> node.isRoot
            "first-child" -> node.elementIndex == 0
            "last-child" -> node.elementIndex == node.siblingCount - 1
            "only-child" -> node.siblingCount == 1
            "empty" -> node.isEmpty
            else -> false
        }
    }) return false

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

private fun buildRawStyle(declarations: List<String>, inlineStyle: String): String = buildString {
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

private fun buildCssStyle(declarations: List<String>, inlineStyle: String): CssStyle {
    val stylePayload = buildRawStyle(declarations, inlineStyle)
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

/** Bounded, iterative text fallback, independent of the render-tree depth/node limits. */
internal fun htmlToPlainText(html: String, maxRawChars: Int = MAX_HTML_INPUT_LENGTH): String {
    if (html.isEmpty() || maxRawChars <= 0) return ""
    val raw = truncateAtSafeBoundary(html, maxRawChars.coerceAtMost(MAX_HTML_INPUT_LENGTH))
    val document = Parser.htmlParser().parseInput(raw, "")
    data class PendingText(val node: Node, val closing: Boolean = false)
    val pending = ArrayDeque<PendingText>()
    pending.addLast(PendingText(document))
    val text = StringBuilder()
    while (pending.isNotEmpty()) {
        val (node, closing) = pending.removeLast()
        if (closing) {
            text.append('\n')
            continue
        }
        when (node) {
            is TextNode -> text.append(node.wholeText)
            is Element -> {
                val tag = node.normalName()
                if (tag in setOf("head", "script", "style", "template") || node.hasAttr("hidden")) continue
                if (tag == "br") {
                    text.append('\n')
                    continue
                }
                if (tag == "img") text.append(node.attr("alt"))
                val block = isBlockTag(tag)
                if (block) {
                    text.append('\n')
                    pending.addLast(PendingText(node, closing = true))
                }
                for (index in node.childNodeSize() - 1 downTo 0) {
                    pending.addLast(PendingText(node.childNode(index)))
                }
            }
        }
    }
    return text.toString()
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
