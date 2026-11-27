package com.yhchat.canary.ui.components.htmltext

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * HTML 的渲染源：[Tree] 表示可以交给高级渲染器正常渲染，
 * [PlainText] 表示内容过大/过复杂，已降级为纯文本以避免崩溃。
 */
private sealed interface HtmlRenderSource {
    data class Tree(val root: HtmlNode.Element) : HtmlRenderSource
    data class PlainText(val text: String) : HtmlRenderSource
}

/**
 * 准备渲染源。
 *
 * 会拦截下面这些危险输入，否则它们会导致 OOM 或递归栈溢出（表现为打开聊天就闪退）：
 *  - 超长 HTML（粘贴整页网页）；
 *  - 节点数量过多的 HTML；
 *  - 嵌套层数过深的 HTML。
 */
private fun prepareHtmlRenderSource(html: String): HtmlRenderSource {
    if (html.length > MAX_HTML_INPUT_LENGTH) {
        return HtmlRenderSource.PlainText(buildPlainTextFallback(html))
    }
    val root = runCatching { parseHtmlTree(html) }.getOrNull()
        ?: return HtmlRenderSource.PlainText(buildPlainTextFallback(html))
    if (isHtmlTreeTooComplex(root)) {
        return HtmlRenderSource.PlainText(buildPlainTextFallback(html))
    }
    return HtmlRenderSource.Tree(root)
}

/**
 * 构造降级用的纯文本，最多处理一段有限的原始内容，避免对大字符串做正则替换。
 */
private fun buildPlainTextFallback(html: String): String {
    val rawLimit = MAX_HTML_INPUT_LENGTH
    val raw = truncateAtSafeBoundary(html, rawLimit)
    val text = runCatching { htmlToPlainText(raw, rawLimit) }.getOrDefault("")
    val truncated = html.length > rawLimit || text.length > HTML_PLAIN_TEXT_LIMIT
    val shown = truncateAtSafeBoundary(text, HTML_PLAIN_TEXT_LIMIT)
    return if (truncated) "$shown\n\n…（内容过长，仅展示开头部分）" else shown
}

@Composable
fun AdvancedHtmlRenderer(
    html: String,
    modifier: Modifier = Modifier,
    onImageClick: ((String) -> Unit)? = null,
    onLinkClick: ((String) -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.onSurface,
    style: TextStyle = MaterialTheme.typography.bodyMedium
) {
    when (val source = remember(html) { prepareHtmlRenderSource(html) }) {
        is HtmlRenderSource.PlainText -> {
            Text(
                text = source.text,
                modifier = modifier,
                color = color,
                style = style
            )
        }
        is HtmlRenderSource.Tree -> {
            val defaultText = CssStyle(
                fontSize = style.fontSize.takeIf { it != TextUnit.Unspecified } ?: 14.sp,
                lineHeight = style.lineHeight.takeIf { it != TextUnit.Unspecified },
                fontWeight = style.fontWeight,
                color = color.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onSurface
            )
            Column(modifier = modifier) {
                // Adjacent inline nodes belong to one paragraph, including nodes at the document root.
                RenderInlineFlow(source.root.children, defaultText, onImageClick, onLinkClick)
            }
        }
    }
}

/**
 * 把写死的深色文字在深色主题下换成可读的颜色。
 *
 * 机器人发的 HTML 几乎都是按白色背景设计的（`color:#333`、`#111`、`#888`）。
 * 直接照搬的话，深色主题下就是黑底黑字、完全看不见。这里按亮度判断：
 *   - 深色主题：太暗的颜色替换成默认前景色
 *   - 浅色主题：太亮的颜色（`#fff` 这类）替换成默认前景色
 *
 * 保留中间地带的颜色（比如 `#2563eb` 蓝、`#e74c3c` 红），它们两种主题下都够醒目，
 * 一并替换反而会丢掉作者想表达的强调。
 */
private fun normalizeTextColorForTheme(
    color: Color?,
    defaultColor: Color,
    isDarkTheme: Boolean
): Color? {
    if (color == null) return null
    if (color.alpha < 0.2f) return defaultColor

    val luminance = color.luminance()
    return if (isDarkTheme) {
        // 深色主题下，亮度低于 0.28 的文字基本看不清；抬到默认前景色
        if (luminance < 0.28f) defaultColor else color
    } else {
        // 浅色主题下，亮度高于 0.90 的文字（近白）看不见
        if (luminance > 0.90f) defaultColor else color
    }
}

/**
 * 把写死的浅色背景在深色主题下压暗。
 *
 * 同上：`background:#f8f9fa` 是按白色页面设计的，深色主题下它是一块发光的白板，
 * 上面的浅灰文字（已被 [normalizeTextColorForTheme] 改成白字）反而糊在白底上。
 * 这里按亮度把它映射成深色主题里对应的容器色，保留「这是一个独立块」的视觉语义。
 */
private fun normalizeBackgroundColorForTheme(
    color: Color?,
    isDarkTheme: Boolean,
    surfaceContainer: Color
): Color? {
    if (color == null) return null
    if (!isDarkTheme) return color

    val luminance = color.luminance()
    // 深色主题下亮度 > 0.6 的背景（近白、浅灰）直接换成主题的容器色
    return if (luminance > 0.6f) surfaceContainer else color
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun HtmlFlexNode(
    node: HtmlNode.Element,
    inheritedText: CssStyle,
    onImageClick: ((String) -> Unit)?,
    onLinkClick: ((String) -> Unit)?
) {
    val direction = node.style.flexDirection?.lowercase().orEmpty()
    val isRow = direction !in setOf("column", "column-reverse")
    val gap = (node.style.gap ?: 0.dp).coerceAtLeast(0.dp)
    val children = remember(node, inheritedText.whiteSpace) {
        htmlFlexItems(node.children, inheritedText.whiteSpace).let {
            if (direction.endsWith("-reverse")) it.reversed() else it
        }
    }
    val horizontalArrangement = when (node.style.justifyContent?.lowercase()) {
        "center" -> Arrangement.spacedBy(gap, Alignment.CenterHorizontally)
        "end", "flex-end", "right" -> Arrangement.spacedBy(gap, Alignment.End)
        "space-between" -> Arrangement.SpaceBetween
        "space-around" -> Arrangement.SpaceAround
        "space-evenly" -> Arrangement.SpaceEvenly
        else -> Arrangement.spacedBy(gap)
    }
    val verticalAlignment = when (node.style.alignItems?.lowercase()) {
        "center" -> Alignment.CenterVertically
        "end", "flex-end" -> Alignment.Bottom
        else -> Alignment.Top
    }
    @Composable
    fun renderChild(child: HtmlNode) {
        RenderHtmlNode(child, inheritedText, onImageClick, onLinkClick, avoidFillMaxWidth = true)
    }
    if (isRow && node.style.flexWrap?.equals("wrap", ignoreCase = true) == true) {
        FlowRow(
            modifier = Modifier.htmlBoxModel(node.style)
                .then(if (node.style.width == null && node.style.widthPercent == null) Modifier.fillMaxWidth() else Modifier),
            horizontalArrangement = horizontalArrangement,
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            children.forEach { child -> Box { renderChild(child) } }
        }
    } else if (isRow) {
        Row(
            modifier = Modifier.htmlBoxModel(node.style)
                .then(if (node.style.width == null && node.style.widthPercent == null) Modifier.fillMaxWidth() else Modifier),
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = verticalAlignment
        ) {
            children.forEach { child ->
                val childStyle = (child as? HtmlNode.Element)?.style
                val grow = childStyle?.flexGrow ?: childStyle?.flex ?: 0f
                Box(Modifier.then(if (grow.isFinite() && grow > 0f) Modifier.weight(grow) else Modifier)) {
                    renderChild(child)
                }
            }
        }
    } else {
        Column(
            modifier = Modifier.htmlBoxModel(node.style)
                .then(if (node.style.width == null && node.style.widthPercent == null) Modifier.fillMaxWidth() else Modifier),
            verticalArrangement = when (node.style.justifyContent?.lowercase()) {
                "center" -> Arrangement.spacedBy(gap, Alignment.CenterVertically)
                "end", "flex-end" -> Arrangement.spacedBy(gap, Alignment.Bottom)
                "space-between" -> Arrangement.SpaceBetween
                "space-around" -> Arrangement.SpaceAround
                "space-evenly" -> Arrangement.SpaceEvenly
                else -> Arrangement.spacedBy(gap)
            },
            horizontalAlignment = when (node.style.alignItems?.lowercase()) {
                "center" -> Alignment.CenterHorizontally
                "end", "flex-end" -> Alignment.End
                else -> Alignment.Start
            }
        ) {
            children.forEach { child -> renderChild(child) }
        }
    }
}

/** CSS flex anonymous items: adjacent text is one item; formatting-only whitespace is not an item. */
internal fun htmlFlexItems(children: List<HtmlNode>, whiteSpace: String?): List<HtmlNode> {
    val items = mutableListOf<HtmlNode>()
    val text = StringBuilder()
    fun flushText() {
        val normalized = normalizeHtmlText(text.toString(), whiteSpace)
        if (normalized.any { !it.isWhitespace() || it == '\u00a0' }) items += HtmlNode.Text(normalized)
        text.setLength(0)
    }
    children.forEach { child ->
        when (child) {
            is HtmlNode.Text -> text.append(child.text)
            is HtmlNode.Element -> {
                if (!child.isHiddenHtmlNode()) {
                    flushText()
                    items += child
                }
            }
        }
    }
    flushText()
    return items
}
private val NON_CONTENT_TAGS = setOf("head", "meta", "title", "script", "style", "link", "template", "base")

internal fun HtmlNode.Element.isHiddenHtmlNode(): Boolean = tag in NON_CONTENT_TAGS || attrs.containsKey("hidden") || style.display?.trim()?.equals("none", ignoreCase = true) == true

@Composable
private fun RenderHtmlNode(
    node: HtmlNode,
    inheritedText: CssStyle,
    onImageClick: ((String) -> Unit)?,
    onLinkClick: ((String) -> Unit)?,
    avoidFillMaxWidth: Boolean = false
) {
    val defaultTextColor = MaterialTheme.colorScheme.onSurface
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val normalizedInheritedText = inheritedText.copy(
        color = normalizeTextColorForTheme(
            color = inheritedText.color,
            defaultColor = defaultTextColor,
            isDarkTheme = isDarkTheme
        )
    )

    if (node is HtmlNode.Element && node.isHiddenHtmlNode()) return
    when (node) {
        is HtmlNode.Text -> {
            Text(
                text = normalizeHtmlText(node.text, normalizedInheritedText.whiteSpace),
                style = normalizedInheritedText.toComposeTextStyle()
            )
        }
        is HtmlNode.Element -> {
            // 背景色也做主题归一化，再交给各个子渲染器。
            // 统一在这里改而不是每个子渲染器各改一遍：子渲染器有七八个，
            // 漏掉任何一个都会在深色主题下露出一块白底。
            val themeAdjustedNode = if (node.style.backgroundColor != null) {
                node.copy(
                    style = node.style.copy(
                        backgroundColor = normalizeBackgroundColorForTheme(
                            color = node.style.backgroundColor,
                            isDarkTheme = isDarkTheme,
                            surfaceContainer = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    )
                )
            } else {
                node
            }

            when (themeAdjustedNode.tag) {
                "hr" -> androidx.compose.material3.HorizontalDivider(Modifier.fillMaxWidth().padding(vertical = 8.dp))
                "img" -> HtmlImageNode(themeAdjustedNode, onImageClick)
                "details" -> HtmlDetailsNode(themeAdjustedNode, normalizedInheritedText, onImageClick, onLinkClick)
                "blockquote" -> HtmlBlockquoteNode(themeAdjustedNode, normalizedInheritedText, onImageClick, onLinkClick)
                "ul", "ol" -> HtmlListNode(themeAdjustedNode, normalizedInheritedText, onImageClick, onLinkClick)
                "table" -> HtmlTableNode(themeAdjustedNode, normalizedInheritedText, onImageClick, onLinkClick)
                else -> HtmlGenericNode(themeAdjustedNode, normalizedInheritedText, onImageClick, onLinkClick, avoidFillMaxWidth)
            }
        }
    }
}

@Composable
private fun HtmlGenericNode(
    node: HtmlNode.Element,
    inheritedText: CssStyle,
    onImageClick: ((String) -> Unit)?,
    onLinkClick: ((String) -> Unit)?,
    avoidFillMaxWidth: Boolean = false
) {
    val headingStyle = when (node.tag) {
        "h1" -> CssStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeightMultiplier = 1.35f, marginTop = 8.dp, marginBottom = 12.dp)
        "h2" -> CssStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeightMultiplier = 1.3f, marginTop = 6.dp, marginBottom = 10.dp)
        "h3" -> CssStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold, lineHeightMultiplier = 1.25f, marginTop = 4.dp, marginBottom = 8.dp)
        "h4" -> CssStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeightMultiplier = 1.2f, marginBottom = 6.dp)
        "h5" -> CssStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, marginBottom = 2.dp)
        "h6" -> CssStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, marginBottom = 2.dp)
        "p" -> CssStyle(marginBottom = 8.dp)
        "pre" -> CssStyle(fontFamily = "monospace", whiteSpace = "pre", padding = 12.dp, marginBottom = 8.dp)
        else -> CssStyle()
    }
    val effectiveStyle = headingStyle.mergeWith(node.style)
    val merged = effectiveStyle.mergeText(inheritedText)
    if (node.tag == "pre") {
        val preserveLineWidth = merged.whiteSpace?.lowercase() in setOf("pre", "nowrap")
        Column(Modifier.fillMaxWidth()
            .then(if (preserveLineWidth) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
            .htmlBoxModel(effectiveStyle)) {
            RenderInlineFlow(node.children, merged, onImageClick, onLinkClick)
        }
        return
    }
    val forceBlockLayout = shouldRenderAsBlockNode(node)
    
    // 妫€鏌ユ槸鍚︽槸閾炬帴鏍囩锛堢敤浜庡鐞嗗潡绾ч摼鎺ョ殑鍙偣鍑绘€э級
    val isLink = node.tag == "a"
    val href = node.attrs["href"]

    val displayMode = node.style.display?.trim()?.lowercase()

    if (displayMode == "flex") {
        HtmlFlexNode(node, merged, onImageClick, onLinkClick)
        return
    }

    if (displayMode == "inline-block") {
        Box(
            modifier = Modifier.htmlBoxModel(effectiveStyle)
        ) {
            RenderInlineFlow(node.children, merged, onImageClick, onLinkClick)
        }
        return
    }

    if (forceBlockLayout || (isBlockTag(node.tag) && !isInlineTag(node.tag)) || isLink) {
        // 链接只要带 href 就整块可点。
        //
        // 之前这里多加了 `&& hasBoxStyle(node.style)` 的限制，导致
        // `<a style="display:block;text-decoration:none">` 这类**没有 padding/背景/border**
        // 的块级链接完全没有点击区域 —— 里面写了 cursor:pointer 也没用，
        // 因为 cursor 对 Compose 没有意义。块级链接在聊天卡片里非常常见
        // （整张卡片就是链接），必须默认可点。
        val clickableModifier = if (isLink && !href.isNullOrBlank()) {
            Modifier.clickable { onLinkClick?.invoke(href) }
        } else {
            Modifier
        }

        Column(
            modifier = Modifier
                .then(clickableModifier)
                .htmlBoxModel(effectiveStyle)
                .then(if (avoidFillMaxWidth || effectiveStyle.width != null || effectiveStyle.widthPercent != null) Modifier else Modifier.fillMaxWidth())
        ) {
            RenderInlineFlow(node.children, merged, onImageClick, onLinkClick)
        }
    } else {
        // 瀵逛簬琛屽唴閾炬帴锛岄渶瑕佹纭鐞?<a> 鏍囩鐨?href
        val rendered = if (isLink && !href.isNullOrBlank()) {
            buildInlineAnnotatedWithLink(
                node.children.ifEmpty { mutableListOf(HtmlNode.Text("")) },
                merged,
                href
            )
        } else {
            buildInlineAnnotated(node.children.ifEmpty { mutableListOf(HtmlNode.Text("")) }, merged)
        }
        if (rendered.first.text.isNotEmpty()) {
            @Suppress("DEPRECATION")
            ClickableText(
                text = rendered.first,
                style = rendered.second,
                onClick = { offset ->
                    // end is exclusive; use offset+1 to hit the annotation range
                    rendered.first.getStringAnnotations("URL", offset, offset + 1)
                        .firstOrNull()
                        ?.let { onLinkClick?.invoke(it.item) }
                }
            )
        }
    }
}

private fun hasBoxStyle(style: CssStyle): Boolean {
    return style.backgroundColor != null ||
        style.padding != null || style.paddingTop != null || style.paddingRight != null || style.paddingBottom != null || style.paddingLeft != null ||
        style.margin != null || style.marginTop != null || style.marginRight != null || style.marginBottom != null || style.marginLeft != null ||
        style.borderRadius != null || style.border != null || style.borderTop != null || style.borderRight != null || style.borderBottom != null || style.borderLeft != null
}

@Composable
private fun RenderInlineFlow(
    children: List<HtmlNode>,
    inheritedText: CssStyle,
    onImageClick: ((String) -> Unit)?,
    onLinkClick: ((String) -> Unit)?
) {
    val inlineBuffer = mutableListOf<HtmlNode>()

    @Composable
    fun flushInline() {
        if (inlineBuffer.isEmpty()) return
        val (annotated, style) = buildInlineAnnotated(inlineBuffer.toList(), inheritedText)
        inlineBuffer.clear()
        if (!annotated.text.hasRenderableHtmlText()) return
        @Suppress("DEPRECATION")
        ClickableText(
            text = annotated,
            style = style,
            onClick = { offset ->
                // end is exclusive; use offset+1 to hit the annotation range
                annotated.getStringAnnotations("URL", offset, offset + 1)
                    .firstOrNull()
                    ?.let { onLinkClick?.invoke(it.item) }
            }
        )
    }

    children.forEach { child ->
        when (child) {
            is HtmlNode.Text -> inlineBuffer += child
            is HtmlNode.Element -> {
                if (child.isHiddenHtmlNode()) return@forEach
                if (shouldRenderInline(child)) {
                    inlineBuffer += child
                } else {
                    flushInline()
                    RenderHtmlNode(child, inheritedText, onImageClick, onLinkClick)
                }
            }
        }
    }
    flushInline()
}

private fun shouldRenderInline(node: HtmlNode.Element): Boolean {
    if (node.tag == "img") return false
    if (node.style.display != null) {
        when (node.style.display.lowercase()) {
            "block", "flex", "table", "table-row", "table-cell", "list-item" -> return false
            "inline-block" -> return true
        }
    }
    if (isBlockTag(node.tag) && !isInlineTag(node.tag)) return false
    if (node.children.any { child ->
            when (child) {
                is HtmlNode.Text -> false
                is HtmlNode.Element -> !shouldRenderInline(child)
            }
        }
    ) return false
    return true
}

private fun shouldRenderAsBlockNode(node: HtmlNode.Element): Boolean {
    if (node.style.display != null) {
        when (node.style.display.lowercase()) {
            "block", "flex", "table", "table-row", "table-cell", "list-item" -> return true
            "inline-block" -> return false
        }
    }
    return node.children.any { child ->
        child is HtmlNode.Element && !shouldRenderInline(child)
    }
}

@Composable
private fun buildInlineAnnotated(
    nodes: List<HtmlNode>,
    inheritedText: CssStyle
): Pair<AnnotatedString, TextStyle> {
    val defaultColor = MaterialTheme.colorScheme.onSurface
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val base = inheritedText.copy(color = normalizeTextColorForTheme(inheritedText.color, defaultColor, isDark))
    return buildHtmlInlineText(nodes, base, MaterialTheme.colorScheme.primary, defaultColor, isDark) to base.toComposeTextStyle()
}

/** Keep one inline traversal for both plain and linked containers. */
@Composable
private fun buildInlineAnnotatedWithLink(
    nodes: List<HtmlNode>, inheritedText: CssStyle, href: String
): Pair<AnnotatedString, TextStyle> = buildInlineAnnotated(
    listOf(HtmlNode.Element("a", attrs = mapOf("href" to href), children = nodes.toMutableList())), inheritedText
)

internal fun buildHtmlInlineText(
    nodes: List<HtmlNode>, inheritedText: CssStyle, linkColor: Color,
    defaultColor: Color, isDark: Boolean
): AnnotatedString = buildAnnotatedString {
    fun appendNode(node: HtmlNode, parent: CssStyle) {
        when (node) {
            is HtmlNode.Text -> append(normalizeHtmlText(node.text, parent.whiteSpace))
            is HtmlNode.Element -> {
                if (node.isHiddenHtmlNode()) return
                if (node.tag == "br") { append("\n"); return }
                val defaults = when (node.tag) {
                    "strong", "b" -> CssStyle(fontWeight = FontWeight.Bold)
                    "em", "i" -> CssStyle(fontStyle = FontStyle.Italic)
                    "s", "del", "strike" -> CssStyle(textDecoration = TextDecoration.LineThrough)
                    "u", "ins" -> CssStyle(textDecoration = TextDecoration.Underline)
                    "code", "kbd", "samp" -> CssStyle(fontFamily = "monospace")
                    "small" -> CssStyle(fontSize = (parent.fontSize ?: 14.sp) * 0.8f)
                    else -> CssStyle()
                }
                var resolved = defaults.mergeWith(node.style).mergeText(parent)
                val href = node.attrs["href"]?.takeIf { it.isNotBlank() && node.tag == "a" }
                if (href != null) {
                    // Check authored values before inheritance; otherwise every link appears to
                    // have an explicit color and loses the default link color.
                    val link = buildLinkSpanStyle(node.style, parent, linkColor)
                    resolved = resolved.copy(color = link.color, textDecoration = link.textDecoration)
                }
                resolved = resolved.copy(color = normalizeTextColorForTheme(resolved.color, defaultColor, isDark))
                val span = resolved.toComposeTextStyle().toSpanStyle().copy(
                    background = node.style.backgroundColor ?: Color.Unspecified,
                    baselineShift = when (node.tag) {
                        "sup" -> androidx.compose.ui.text.style.BaselineShift.Superscript
                        "sub" -> androidx.compose.ui.text.style.BaselineShift.Subscript
                        else -> null
                    }
                )
                if (href != null) pushStringAnnotation("URL", href)
                withStyle(span) { node.children.forEach { appendNode(it, resolved) } }
                if (href != null) pop()
            }
        }
    }
    nodes.forEach { appendNode(it, inheritedText) }
}
/**
 * 构造链接的 SpanStyle，尊重作者的显式样式意图。
 *
 * 默认行为是"染成主题色 + 加下划线"，但真实 HTML 里经常写：
 * ```html
 * <a href="..." style="display:block;text-decoration:none;color:inherit">
 * ```
 * 这种写法是在说「这是一个整块的点击区域，但**看起来要像普通文字**」——
 * 常见于把整张卡片做成链接的机器人消息。如果我们仍然强行染色加下划线，
 * 卡片标题就会变成蓝色的带下划线文本，跟设计稿完全对不上。
 *
 * 判定规则：
 *   - `color:inherit` → 用父级颜色，不染链接色
 *   - `text-decoration:none` → 不加下划线
 *   - 显式给了 color → 用作者给的颜色
 */
private fun buildLinkSpanStyle(
    merged: CssStyle,
    inheritedText: CssStyle,
    linkColor: Color
): SpanStyle {
    val resolvedColor = when {
        // 作者明确写了 color:inherit → 跟随父级
        merged.colorInherit -> inheritedText.color ?: Color.Unspecified
        // 作者给了具体颜色
        merged.color != null -> merged.color
        // 都没写 → 才用链接色
        else -> linkColor
    }
    // textDecoration 为 null 表示没写；写了 none 时 parseTextDecoration 会返回 TextDecoration.None，
    // 这里据此判断是否要补默认下划线
    val resolvedDecoration = merged.textDecoration ?: TextDecoration.Underline

    return SpanStyle(
        color = resolvedColor,
        textDecoration = resolvedDecoration
    )
}

@Composable
private fun HtmlImageNode(
    node: HtmlNode.Element,
    onImageClick: ((String) -> Unit)?
) {
    val src = node.attrs["src"].orEmpty()
    if (src.isEmpty()) return
    val context = LocalContext.current
    val model = com.yhchat.canary.ui.components.ImageUtils.createImageRequest(context, src)

    val imageScale = when (node.style.objectFit?.lowercase()) {
        "cover" -> ContentScale.Crop
        "contain" -> ContentScale.Fit
        "fill" -> ContentScale.FillBounds
        "none" -> ContentScale.None
        "scale-down" -> ContentScale.Inside
        else -> ContentScale.Fit
    }

    // 老式 HTML 里图片尺寸常写成属性（width="36" height="36"）而不是 style，
    // 只读 style 会让这类图片丢掉尺寸、被拉伸成整行宽 —— 这是最常见的头像变形原因。
    // 优先级：style > 属性（和浏览器一致，style 覆盖 attributes）。
    val attrWidth = node.attrs["width"]?.let { parseHtmlSizeAttr(it) }
    val attrHeight = node.attrs["height"]?.let { parseHtmlSizeAttr(it) }

    val attrWidthPercent = node.attrs["width"]?.trim()?.takeIf { it.endsWith('%') }
        ?.dropLast(1)?.toFloatOrNull()?.takeIf { it.isFinite() && it >= 0f }?.div(100f)
    val attrHeightPercent = node.attrs["height"]?.trim()?.takeIf { it.endsWith('%') }
        ?.dropLast(1)?.toFloatOrNull()?.takeIf { it.isFinite() && it >= 0f }?.div(100f)
    val effectiveStyle = node.style.copy(
        width = node.style.width ?: attrWidth.takeIf { node.style.widthPercent == null },
        height = node.style.height ?: attrHeight.takeIf { node.style.heightPercent == null },
        widthPercent = node.style.widthPercent ?: attrWidthPercent.takeIf { node.style.width == null },
        heightPercent = node.style.heightPercent ?: attrHeightPercent.takeIf { node.style.height == null }
    )
    val hasExplicitSize = effectiveStyle.width != null || effectiveStyle.height != null ||
        effectiveStyle.widthPercent != null || effectiveStyle.heightPercent != null

    // 百分比宽度相对于当前可用容器，而不是整个屏幕。
    BoxWithConstraints {
        val modifier = Modifier
            .then(if (onImageClick != null) Modifier.clickable { onImageClick(src) } else Modifier)
            .htmlMediaBoxModel(effectiveStyle, maxWidth.takeIf { it > 0.dp && it < Dp.Infinity })
            // 没有显式尺寸的图片才给兜底最小尺寸；
            // 有尺寸（比如 36px 头像）时再套 minWidth=120dp 会把头像撑成一块大方图
            .then(
                if (hasExplicitSize) Modifier
                else Modifier.defaultMinSize(minWidth = 120.dp, minHeight = 80.dp)
            )

        AsyncImage(
            model = model,
            contentDescription = node.attrs["alt"] ?: "图片",
            modifier = modifier,
            contentScale = imageScale
        )
    }
}

/**
 * 解析图片的 width/height 属性。
 *
 * HTML 属性可以是纯数字（`width="36"`，单位 px），也可能带单位或百分号。
 * 解析不出来就返回 null，交给后续兜底。
 */
private fun parseHtmlSizeAttr(raw: String): Dp? {
    val value = raw.trim()
    if (value.isEmpty()) return null
    // 百分比（width="100%"）交给 style 的百分比通道，这里不处理
    if (value.endsWith("%")) return null
    val numeric = value.removeSuffix("px").removeSuffix("PX").trim()
    return numeric.toFloatOrNull()?.takeIf { it.isFinite() && it >= 0f }?.dp
}

@Composable
private fun HtmlDetailsNode(
    node: HtmlNode.Element,
    inheritedText: CssStyle,
    onImageClick: ((String) -> Unit)?,
    onLinkClick: ((String) -> Unit)?
) {
    var expanded by remember { mutableStateOf(node.attrs.containsKey("open")) }
    val summary = node.children.firstOrNull { it is HtmlNode.Element && it.tag == "summary" }
    val content = node.children.filterNot { it == summary }
    val openStateStyle = remember(node.attrs[DETAILS_OPEN_STYLE_ATTR]) {
        node.attrs[DETAILS_OPEN_STYLE_ATTR]
            ?.takeIf { it.isNotBlank() }
            ?.let(CssParser::parseStyle)
    }
    val detailsStyle = if (expanded && openStateStyle != null) {
        node.style.mergeWith(openStateStyle)
    } else {
        node.style
    }

    // 三角箭头跟随 summary 自身的 CSS color。
    // 以前写死 MaterialTheme.colorScheme.primary，于是
    // `<summary style="color:#2563eb">` 的蓝色文字旁边配了个紫色箭头，很突兀。
    val summaryColor = (summary as? HtmlNode.Element)?.style?.color
        ?: MaterialTheme.colorScheme.primary

    Column(modifier = Modifier.fillMaxWidth().htmlBoxModel(detailsStyle)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
                contentDescription = if (expanded) "收起" else "展开",
                tint = summaryColor,
                modifier = Modifier.padding(end = 4.dp).size(18.dp)
            )
            Box(modifier = Modifier.weight(1f)) {
                if (summary != null) {
                    RenderHtmlNode(summary, inheritedText, onImageClick, onLinkClick)
                } else {
                    Text("详情", style = inheritedText.toComposeTextStyle())
                }
            }
        }
        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // 缩进跟箭头宽度对齐，看起来才是「展开的内容」而不是并列
                    .padding(start = 22.dp, top = 2.dp)
            ) {
                content.forEach { RenderHtmlNode(it, inheritedText, onImageClick, onLinkClick) }
            }
        }
    }
}

@Composable
private fun HtmlBlockquoteNode(
    node: HtmlNode.Element,
    inheritedText: CssStyle,
    onImageClick: ((String) -> Unit)?,
    onLinkClick: ((String) -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .htmlBoxModel(node.style.copy(borderLeft = node.style.borderLeft ?: "3px solid #e0e0e0"))
            .padding(start = 12.dp)
    ) {
        RenderInlineFlow(node.children, inheritedText, onImageClick, onLinkClick)
    }
}

@Composable
private fun HtmlListNode(
    node: HtmlNode.Element,
    inheritedText: CssStyle,
    onImageClick: ((String) -> Unit)?,
    onLinkClick: ((String) -> Unit)?
) {
    val items = node.children.filterIsInstance<HtmlNode.Element>().filter { it.tag == "li" }
    val ordered = node.tag == "ol"
    val start = positiveInt(node.attrs["start"], 1)
    val hideMarker = node.style.listStyleType?.equals("none", ignoreCase = true) == true

    Column(modifier = Modifier.fillMaxWidth().htmlBoxModel(node.style)) {
        if (items.isNotEmpty()) {
            items.forEachIndexed { index, item ->
                val marker = if (ordered) "${start + index}." else "•"
                Row(
                    modifier = Modifier.fillMaxWidth().htmlBoxModel(item.style),
                    verticalAlignment = Alignment.Top
                ) {
                    if (!hideMarker) {
                        Text(
                            text = "$marker ",
                            style = inheritedText.toComposeTextStyle(),
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        RenderInlineFlow(item.children, item.style.mergeText(inheritedText), onImageClick, onLinkClick)
                    }
                }
            }
        } else {
            node.children.forEach { RenderHtmlNode(it, inheritedText, onImageClick, onLinkClick) }
        }
    }
}

private data class HtmlTableCell(
    val node: HtmlNode.Element,
    val isHeader: Boolean,
    val colSpan: Int
)

private data class HtmlTableRow(
    val style: CssStyle,
    val cells: List<HtmlTableCell>
)

private data class HtmlTableData(
    val style: CssStyle,
    val rows: List<HtmlTableRow>,
    val cellPadding: Int,
    val borderSpacing: Int
)

private fun extractTable(table: HtmlNode.Element): HtmlTableData {
    val rows = mutableListOf<HtmlTableRow>()

    fun walk(node: HtmlNode) {
        if (node is HtmlNode.Element && !node.isHiddenHtmlNode()) {
            if (node.tag == "tr") {
                val cells = node.children
                    .filterIsInstance<HtmlNode.Element>()
                    .filter { (it.tag == "td" || it.tag == "th") && !it.isHiddenHtmlNode() }
                    .map {
                        HtmlTableCell(
                            node = it,
                            isHeader = it.tag == "th",
                            colSpan = positiveInt(it.attrs["colspan"], 1).coerceAtMost(100)
                        )
                    }
                rows += HtmlTableRow(node.style, cells)
            } else {
                node.children.forEach(::walk)
            }
        }
    }

    walk(table)
    return HtmlTableData(
        style = table.style,
        rows = rows,
        cellPadding = positiveInt(table.attrs["cellpadding"], 1),
        borderSpacing = positiveInt(table.attrs["cellspacing"], table.style.borderSpacing?.value?.toInt() ?: 0)
    )
}

@Composable
private fun HtmlTableNode(
    node: HtmlNode.Element,
    inheritedText: CssStyle,
    onImageClick: ((String) -> Unit)?,
    onLinkClick: ((String) -> Unit)?
) {
    val table = remember(node) { extractTable(node) }
    if (table.rows.isEmpty()) return
    val hState = rememberScrollState()
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp

    Column(modifier = Modifier.fillMaxWidth().htmlBoxModel(table.style)) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val viewportWidth = if (maxWidth < Dp.Infinity && maxWidth > 0.dp) {
                maxWidth
            } else {
                screenWidth
            }
            val columnCount = table.rows.maxOf { row -> row.cells.sumOf { it.colSpan } }.coerceAtLeast(1)
            val columnWidth = (viewportWidth / columnCount).coerceIn(120.dp, 280.dp)
            Box(
                modifier = Modifier
                    .width(viewportWidth)
                    .horizontalScroll(hState)
            ) {
                Column {
                    table.rows.forEach { row ->
                        Row(
                            modifier = Modifier
                                .then(if (row.style.backgroundColor != null) Modifier.background(row.style.backgroundColor) else Modifier)
                                .padding(bottom = table.borderSpacing.dp),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            row.cells.forEach { cell ->
                                val baseText = if (cell.isHeader) inheritedText.copy(fontWeight = FontWeight.Bold) else inheritedText
                                Box(
                                    modifier = Modifier
                                        .width(columnWidth * cell.colSpan)
                                        .then(
                                            if (cell.node.style.backgroundColor != null) Modifier.background(cell.node.style.backgroundColor)
                                            else Modifier
                                        )
                                        .padding(
                                            top = (cell.node.style.paddingTop ?: cell.node.style.padding ?: table.cellPadding.dp).coerceAtLeast(0.dp),
                                            bottom = (cell.node.style.paddingBottom ?: cell.node.style.padding ?: table.cellPadding.dp).coerceAtLeast(0.dp),
                                            start = (cell.node.style.paddingLeft ?: cell.node.style.padding ?: 12.dp).coerceAtLeast(0.dp),
                                            end = (cell.node.style.paddingRight ?: cell.node.style.padding ?: 12.dp).coerceAtLeast(0.dp)
                                        )
                                ) {
                                    Column {
                                        RenderInlineFlow(
                                            children = cell.node.children,
                                            inheritedText = cell.node.style.mergeText(baseText),
                                            onImageClick = onImageClick,
                                            onLinkClick = onLinkClick
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

