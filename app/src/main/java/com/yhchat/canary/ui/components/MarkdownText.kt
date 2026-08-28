package com.yhchat.canary.ui.components

import android.os.Build
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.hrm.latex.renderer.Latex
import com.hrm.latex.renderer.LatexAutoWrap
import com.hrm.latex.renderer.measure.rememberLatexMeasurer
import com.hrm.latex.renderer.model.LatexConfig
import com.mikepenz.markdown.m3.Markdown as MikepenzMarkdown
import com.mikepenz.markdown.model.MarkdownState
import com.mikepenz.markdown.model.ReferenceLinkHandler
import com.mikepenz.markdown.model.rememberMarkdownState
import com.mikepenz.markdown.compose.LocalMarkdownColors
import com.mikepenz.markdown.compose.LocalMarkdownDimens
import com.mikepenz.markdown.compose.LocalMarkdownComponents
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownDivider
import com.mikepenz.markdown.compose.elements.VerticalMarkdownDivider
import com.mikepenz.markdown.compose.MarkdownElement
import com.mikepenz.markdown.annotator.annotatorSettings
import com.mikepenz.markdown.annotator.buildMarkdownAnnotatedString
import com.mikepenz.markdown.utils.getUnescapedTextInNode
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import com.yhchat.canary.ui.adaptive.YhIcon as Icon
import com.yhchat.canary.ui.adaptive.YhIconButton
import com.yhchat.canary.ui.adaptive.YhText as Text
import com.yhchat.canary.ui.components.htmltext.HtmlTextMessage
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import java.util.Collections
import kotlinx.coroutines.delay


/**
 * Markdown文本渲染组件
 *
 * Markdown 图片单独提取出来，用 Compose 自己渲染
 * 这样可以精确控制图片宽度、请求头和点击预览行为
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    backgroundColor: Color = Color.Transparent,
    onImageClick: ((String) -> Unit)? = null,
    imageReferer: String? = "https://myapp.jwznb.com",
    enableHtmlRendering: Boolean = true,
    highlightKeyword: String = "",
    enableTextSelection: Boolean = true,
    persistRenderState: Boolean = true
) {
    val context = LocalContext.current
    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    val latexEnabled = remember { Build.VERSION.SDK_INT >= Build.VERSION_CODES.M }
    val displayMarkdown = if (persistRenderState) {
        markdown
    } else {
        rememberStreamingMarkdown(markdown)
    }

    val normalizedMarkdown = MarkdownRendererCache.getNormalizedMarkdown(displayMarkdown)
    val segments = MarkdownRendererCache.getSegments(normalizedMarkdown)
    val latexConfig = remember(textColor, context) {
        LatexConfig(
            fontSize = 16.sp,
            color = textColor,
            darkColor = textColor,
            accessibilityEnabled = true,
            onHyperlinkClick = { url ->
                try {
                    openMarkdownLink(context, url)
                } catch (_: Exception) {
                }
            }
        )
    }

    Column(
        modifier = modifier
            .wrapContentWidth()
            .background(backgroundColor),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        segments.forEach { segment ->
            when (segment) {
                is MarkdownSegment.Text -> {
                    if (segment.content.isNotBlank()) {
                        val taskRuns = MarkdownRendererCache.getTaskRuns(segment.content)
                        val textContent: @Composable () -> Unit = {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                taskRuns.forEach { run ->
                                    when (run) {
                                        is TaskRun.Markdown -> {
                                            if (run.content.isBlank()) return@forEach
                                            MarkdownRendererCache.getMarkdownRenderBlocks(run.content).forEach { block ->
                                                if (block.isNotBlank()) {
                                                    MarkdownTextRun(
                                                        content = block,
                                                        latexConfig = latexConfig,
                                                        latexEnabled = latexEnabled,
                                                        highlightKeyword = highlightKeyword,
                                                        persistRenderState = persistRenderState,
                                                        enableTextSelection = enableTextSelection,
                                                        onLinkClicked = { url ->
                                                            try {
                                                                openMarkdownLink(context, url)
                                                            } catch (_: Exception) {
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                        is TaskRun.Task -> {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 2.dp),
                                                verticalAlignment = Alignment.Top
                                            ) {
                                                Icon(
                                                    imageVector = if (run.checked) {
                                                        Icons.Filled.CheckBox
                                                    } else {
                                                        Icons.Filled.CheckBoxOutlineBlank
                                                    },
                                                    contentDescription = if (run.checked) "checked task" else "unchecked task",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier
                                                        .size(18.dp)
                                                        .padding(top = 2.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Box(modifier = Modifier.fillMaxWidth()) {
                                                    MarkdownTextRun(
                                                        content = run.content,
                                                        latexConfig = latexConfig,
                                                        latexEnabled = latexEnabled,
                                                        highlightKeyword = highlightKeyword,
                                                        persistRenderState = persistRenderState,
                                                        enableTextSelection = enableTextSelection,
                                                        onLinkClicked = { url ->
                                                            try {
                                                                openMarkdownLink(context, url)
                                                            } catch (_: Exception) {
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        textContent()
                    }
                }

                is MarkdownSegment.Image -> {
                    MarkdownInlineImage(
                        url = segment.url,
                        alt = segment.alt,
                        imageReferer = imageReferer,
                        onClick = { url ->
                            onImageClick?.invoke(url) ?: run {
                                previewImageUrl = url
                            }
                        }
                    )
                }

                is MarkdownSegment.HtmlTable -> {
                    if (enableHtmlRendering) {
                        HtmlTextMessage(
                            html = segment.content,
                            modifier = Modifier.fillMaxWidth(),
                            color = textColor,
                            style = MaterialTheme.typography.bodyMedium,
                            onImageClick = { url ->
                                onImageClick?.invoke(url) ?: run {
                                    previewImageUrl = url
                                }
                            },
                            onUriClick = { url ->
                                try {
                                    openMarkdownLink(context, url)
                                } catch (_: Exception) {
                                }
                            },
                            useAdvancedRenderer = true
                        )
                    } else {
                        val htmlFallbackContent: @Composable () -> Unit = {
                            Text(
                                text = segment.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = textColor,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        htmlFallbackContent()
                    }
                }

                is MarkdownSegment.HtmlBlock -> {
                    if (enableHtmlRendering) {
                        HtmlTextMessage(
                            html = segment.content,
                            modifier = Modifier.fillMaxWidth(),
                            color = textColor,
                            style = MaterialTheme.typography.bodyMedium,
                            onImageClick = { url ->
                                onImageClick?.invoke(url) ?: run {
                                    previewImageUrl = url
                                }
                            },
                            onUriClick = { url ->
                                try {
                                    openMarkdownLink(context, url)
                                } catch (_: Exception) {
                                }
                            },
                            useAdvancedRenderer = true
                        )
                    } else {
                        val htmlBlockFallbackContent: @Composable () -> Unit = {
                            Text(
                                text = segment.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = textColor,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        htmlBlockFallbackContent()
                    }
                }
                
                is MarkdownSegment.CodeBlock -> {
                    CodeBlockComponent(
                        code = segment.code,
                        language = segment.language,
                        enableTextSelection = enableTextSelection
                    )
                }

                is MarkdownSegment.Details -> {
                    MarkdownDetailsBlock(
                        summary = segment.summary,
                        contentMarkdown = segment.content,
                        textColor = textColor,
                        backgroundColor = backgroundColor,
                        imageReferer = imageReferer,
                        onImageClick = onImageClick,
                        highlightKeyword = highlightKeyword
                    )
                }
            }
        }
    }

    previewImageUrl?.let { imageUrl ->
        ImageViewer(
            imageUrl = imageUrl,
            onDismiss = { previewImageUrl = null }
        )
    }
}

@Composable
private fun MarkdownTextRun(
    content: String,
    latexConfig: LatexConfig,
    latexEnabled: Boolean,
    highlightKeyword: String,
    persistRenderState: Boolean,
    enableTextSelection: Boolean,
    onLinkClicked: (String) -> Unit
) {
    val allowSelectionForThisRun =
        enableTextSelection && !MarkdownRendererCache.shouldDisableSelectionForPerformance(content)
    val maybeSelection: @Composable (@Composable () -> Unit) -> Unit = { body ->
        if (allowSelectionForThisRun) {
            SelectionContainer { body() }
        } else {
            body()
        }
    }

    if (!latexEnabled) {
        val highlightedMarkdown = MarkdownRendererCache.getHighlightedMarkdown(
            content,
            highlightKeyword
        )
        maybeSelection {
            Box(modifier = Modifier.fillMaxWidth()) {
                MikepenzMarkdownText(
                    markdown = highlightedMarkdown,
                    baseColor = latexConfig.color,
                    onLinkClicked = onLinkClicked,
                    persistRenderState = persistRenderState,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        return
    }

    val latexSegments = MarkdownRendererCache.getLatexSegments(content)
    if (latexSegments.size == 1 && latexSegments.first() is MarkdownInlineSegment.Markdown) {
        val highlightedMarkdown = MarkdownRendererCache.getHighlightedMarkdown(
            content,
            highlightKeyword
        )
        maybeSelection {
            Box(modifier = Modifier.fillMaxWidth()) {
                MikepenzMarkdownText(
                    markdown = highlightedMarkdown,
                    baseColor = latexConfig.color,
                    onLinkClicked = onLinkClicked,
                    persistRenderState = persistRenderState,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        return
    }

    val onlyInlineLatex = latexSegments.none { it is MarkdownInlineSegment.LatexBlock }
    if (onlyInlineLatex) {
        InlineMarkdownWithLatex(
            segments = latexSegments,
            latexConfig = latexConfig,
            highlightKeyword = highlightKeyword,
            enableTextSelection = enableTextSelection,
            onLinkClicked = onLinkClicked
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        latexSegments.forEach { segment ->
            when (segment) {
                is MarkdownInlineSegment.Markdown -> {
                    if (segment.content.isNotBlank()) {
                        val highlightedMarkdown = MarkdownRendererCache.getHighlightedMarkdown(
                            segment.content,
                            highlightKeyword
                        )
                        maybeSelection {
                            MikepenzMarkdownText(
                                markdown = highlightedMarkdown,
                                baseColor = latexConfig.color,
                                onLinkClicked = onLinkClicked,
                                persistRenderState = persistRenderState,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                is MarkdownInlineSegment.LatexInline -> {
                    LatexInlineText(
                        latex = segment.content,
                        latexConfig = latexConfig
                    )
                }

                is MarkdownInlineSegment.LatexBlock -> {
                    LatexAutoWrap(
                        latex = segment.content,
                        modifier = Modifier.fillMaxWidth(),
                        config = latexConfig
                    )
                }
            }
        }
    }
}

@Composable
private fun MikepenzMarkdownText(
    markdown: String,
    baseColor: Color,
    onLinkClicked: (String) -> Unit,
    persistRenderState: Boolean,
    modifier: Modifier = Modifier
) {
    val uriHandler = remember(onLinkClicked) {
        object : UriHandler {
            override fun openUri(uri: String) {
                onLinkClicked(uri)
            }
        }
    }
    val bodyStyle = MaterialTheme.typography.bodyMedium.copy(
        color = baseColor,
        lineHeight = 22.sp
    )
    val colors = com.mikepenz.markdown.m3.markdownColor(text = baseColor)
    val typography = com.mikepenz.markdown.m3.markdownTypography(
        h1 = MaterialTheme.typography.headlineMedium.copy(
            color = baseColor,
            fontWeight = FontWeight.Bold,
            lineHeight = 34.sp
        ),
        h2 = MaterialTheme.typography.headlineSmall.copy(
            color = baseColor,
            fontWeight = FontWeight.Bold,
            lineHeight = 30.sp
        ),
        h3 = MaterialTheme.typography.titleMedium.copy(
            color = baseColor,
            fontWeight = FontWeight.Bold,
            lineHeight = 24.sp
        ),
        h4 = MaterialTheme.typography.titleSmall.copy(
            color = baseColor,
            fontWeight = FontWeight.Bold,
            lineHeight = 22.sp
        ),
        h5 = MaterialTheme.typography.titleSmall.copy(
            color = baseColor,
            fontWeight = FontWeight.Bold,
            lineHeight = 22.sp
        ),
        h6 = MaterialTheme.typography.labelLarge.copy(
            color = baseColor,
            fontWeight = FontWeight.Bold,
            lineHeight = 20.sp
        ),
        text = bodyStyle,
        paragraph = bodyStyle,
        ordered = bodyStyle,
        bullet = bodyStyle,
        list = bodyStyle,
        quote = bodyStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
        code = MaterialTheme.typography.bodySmall.copy(
            color = baseColor,
            fontFamily = FontFamily.Monospace,
            lineHeight = 20.sp
        ),
        inlineCode = bodyStyle.copy(fontFamily = FontFamily.Monospace),
        textLink = TextLinkStyles(
            style = SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                textDecoration = TextDecoration.Underline
            )
        ),
        table = bodyStyle
    )
    val annotator = com.mikepenz.markdown.model.markdownAnnotator(
        config = com.mikepenz.markdown.model.markdownAnnotatorConfig(eolAsNewLine = true)
    )
    val flavour = remember { GFMFlavourDescriptor() }
    val parser = remember(flavour) { MarkdownParser(flavour) }
    val referenceLinkHandler = remember { PersistentReferenceLinkHandler() }
    CompositionLocalProvider(
        LocalUriHandler provides uriHandler
    ) {
        if (persistRenderState) {
            val markdownState = rememberPersistentMarkdownState(
                content = markdown,
                flavour = flavour,
                parser = parser,
                referenceLinkHandler = referenceLinkHandler
            )
            MikepenzMarkdown(
                markdownState = markdownState,
                modifier = modifier,
                colors = colors,
                typography = typography,
                annotator = annotator
            )
        } else {
            val markdownState = rememberMarkdownState(
                content = markdown,
                lookupLinks = true,
                retainState = false,
                flavour = flavour,
                parser = parser,
                referenceLinkHandler = referenceLinkHandler,
                immediate = true
            )
            MikepenzMarkdown(
                markdownState = markdownState,
                modifier = modifier,
                colors = colors,
                typography = typography,
                annotator = annotator
            )
        }
    }
}

@Composable
private fun rememberStreamingMarkdown(markdown: String): String {
    var lastNonBlankMarkdown by remember { mutableStateOf(markdown.takeIf { it.isNotBlank() }.orEmpty()) }
    var renderedMarkdown by remember { mutableStateOf(lastNonBlankMarkdown) }
    var lastRenderTimeMillis by remember { mutableStateOf(0L) }

    LaunchedEffect(markdown) {
        if (markdown.isNotBlank()) {
            lastNonBlankMarkdown = markdown
            val now = System.currentTimeMillis()
            val elapsed = now - lastRenderTimeMillis
            if (renderedMarkdown.isBlank() || elapsed >= STREAMING_MARKDOWN_FRAME_MS) {
                renderedMarkdown = markdown
                lastRenderTimeMillis = now
            } else {
                delay(STREAMING_MARKDOWN_FRAME_MS - elapsed)
                renderedMarkdown = lastNonBlankMarkdown
                lastRenderTimeMillis = System.currentTimeMillis()
            }
        } else if (lastNonBlankMarkdown.isNotBlank()) {
            renderedMarkdown = lastNonBlankMarkdown
        }
    }

    return renderedMarkdown.ifBlank { markdown }
}

private const val STREAMING_MARKDOWN_FRAME_MS = 32L

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InlineMarkdownWithLatex(
    segments: List<MarkdownInlineSegment>,
    latexConfig: LatexConfig,
    highlightKeyword: String,
    enableTextSelection: Boolean,
    onLinkClicked: (String) -> Unit
) {
    val lines = remember(segments) { splitInlineSegmentsByLine(segments) }
    val maybeSelection: @Composable (@Composable () -> Unit) -> Unit = { body ->
        if (enableTextSelection) {
            SelectionContainer { body() }
        } else {
            body()
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        lines.forEach { line ->
            if (line.isEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    line.forEach { segment ->
                        when (segment) {
                            is MarkdownInlineSegment.Markdown -> {
                                if (segment.content.isNotEmpty()) {
                                    val highlighted = remember(segment.content, highlightKeyword) {
                                        MarkdownRendererCache.getHighlightedMarkdown(
                                            segment.content,
                                            highlightKeyword
                                        )
                                    }
                                    maybeSelection {
                                        MikepenzMarkdownText(
                                            markdown = highlighted,
                                            baseColor = latexConfig.color,
                                            onLinkClicked = onLinkClicked,
                                            persistRenderState = true,
                                            modifier = Modifier.wrapContentWidth()
                                        )
                                    }
                                }
                            }

                            is MarkdownInlineSegment.LatexInline -> {
                                LatexInlineText(
                                    latex = segment.content,
                                    latexConfig = latexConfig
                                )
                            }

                            is MarkdownInlineSegment.LatexBlock -> Unit
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberPersistentMarkdownState(
    content: String,
    flavour: GFMFlavourDescriptor,
    parser: MarkdownParser,
    referenceLinkHandler: ReferenceLinkHandler
): MarkdownState {
    val cachedState = remember(content) {
        MarkdownRendererCache.getMarkdownState(
            content = content,
            flavour = flavour,
            parser = parser,
            referenceLinkHandler = referenceLinkHandler
        )
    }
    if (cachedState != null) {
        return cachedState
    }

    val state = rememberMarkdownState(
        content = content,
        lookupLinks = true,
        retainState = true,
        flavour = flavour,
        parser = parser,
        referenceLinkHandler = referenceLinkHandler,
        immediate = true
    )
    MarkdownRendererCache.putMarkdownState(content, state)
    return state
}

@Composable
private fun LatexInlineText(
    latex: String,
    latexConfig: LatexConfig
) {
    val density = LocalDensity.current
    val measurer = rememberLatexMeasurer(latexConfig)
    val dimensions = remember(latex, latexConfig) {
        runCatching { measurer.measure(latex, latexConfig) }.getOrNull()
    }

    if (dimensions == null) {
        Latex(
            latex = latex,
            config = latexConfig
        )
        return
    }

    val inlineContentId = remember(latex) { "latex_inline_$latex" }
    val annotated = remember(inlineContentId) {
        buildAnnotatedString {
            appendInlineContent(inlineContentId, "[latex]")
        }
    }
    val inlineContent = remember(inlineContentId, dimensions, latex, latexConfig, density) {
        mapOf(
            inlineContentId to InlineTextContent(
                Placeholder(
                    width = with(density) { dimensions.widthPx.toDp().toSp() },
                    height = with(density) { dimensions.heightPx.toDp().toSp() },
                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                )
            ) {
                Latex(
                    latex = latex,
                    config = latexConfig
                )
            }
        )
    }

    BasicText(
        text = annotated,
        inlineContent = inlineContent
    )
}

@Composable
private fun MarkdownInlineImage(
    url: String,
    alt: String?,
    imageReferer: String?,
    onClick: (String) -> Unit
) {
    val context = LocalContext.current
    val cleanUrl = remember(url) {
        url.trim().removeSurrounding("<", ">").trim()
    }

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(cleanUrl)
            .apply {
                // 筛选图片链接：只有当URL属于云湖相关域名（jwznb.com）时才添加云湖的 Referer 头，
                // 避免对第三方图床/外部CDN乱加 Referer 导致403防盗链错误
                val isYunhuImage = cleanUrl.contains("jwznb.com", ignoreCase = true) ||
                                   cleanUrl.contains("chat-img.jwznb.com", ignoreCase = true) ||
                                   cleanUrl.contains("myapp.jwznb.com", ignoreCase = true)
                if (isYunhuImage) {
                    val referer = imageReferer?.takeIf { it.isNotBlank() } ?: "https://myapp.jwznb.com"
                    setHeader("Referer", referer)
                }
                setHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
                crossfade(true)
            }
            .build(),
        contentDescription = alt ?: "Markdown图片",
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick(cleanUrl) },
        contentScale = ContentScale.FillWidth
    )
}

/**
 * 代码块组件，带复制按钮
 */
@Composable
private fun CodeBlockComponent(
    code: String,
    language: String?,
    enableTextSelection: Boolean
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // 语言标签和复制按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 语言标签
                if (!language.isNullOrBlank()) {
                    Text(
                        text = language,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                }
                
                // 复制按钮
                YhIconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("code", code)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "复制代码",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            
            // 代码内容
            val codeText: @Composable () -> Unit = {
                Text(
                    text = code,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 20.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
            if (enableTextSelection) {
                SelectionContainer { codeText() }
            } else {
                codeText()
            }
        }
    }
}

private sealed interface MarkdownSegment {
    data class Text(val content: String) : MarkdownSegment
    data class Image(val url: String, val alt: String?) : MarkdownSegment
    data class HtmlTable(val content: String) : MarkdownSegment
    data class HtmlBlock(val content: String) : MarkdownSegment
    data class CodeBlock(val code: String, val language: String?) : MarkdownSegment
    data class Details(val summary: String, val content: String) : MarkdownSegment
}

private sealed interface MarkdownInlineSegment {
    data class Markdown(val content: String) : MarkdownInlineSegment
    data class LatexInline(val content: String) : MarkdownInlineSegment
    data class LatexBlock(val content: String) : MarkdownInlineSegment
}

private object MarkdownRendererCache {
    private const val SEGMENT_CACHE_SIZE = 256
    private const val INLINE_SEGMENT_CACHE_SIZE = 512
    private const val NORMALIZED_MARKDOWN_CACHE_SIZE = 256
    private const val TASK_RUN_CACHE_SIZE = 512
    private const val HIGHLIGHT_CACHE_SIZE = 1024
    private const val DETAILS_STATE_CACHE_SIZE = 256
    private const val LATEX_SEGMENT_CACHE_SIZE = 1024
    private const val MARKDOWN_STATE_CACHE_SIZE = 128
    private const val MARKDOWN_RENDER_BLOCK_CACHE_SIZE = 256

    private val normalizedMarkdownCache = createLruCache<String, String>(NORMALIZED_MARKDOWN_CACHE_SIZE)
    private val segmentCache = createLruCache<String, List<MarkdownSegment>>(SEGMENT_CACHE_SIZE)
    private val inlineSegmentsCache = createLruCache<String, List<MarkdownSegment>>(INLINE_SEGMENT_CACHE_SIZE)
    private val taskRunsCache = createLruCache<String, List<TaskRun>>(TASK_RUN_CACHE_SIZE)
    private val highlightedMarkdownCache = createLruCache<String, String>(HIGHLIGHT_CACHE_SIZE)
    private val detailsExpandedCache = createLruCache<String, Boolean>(DETAILS_STATE_CACHE_SIZE)
    private val latexSegmentsCache = createLruCache<String, List<MarkdownInlineSegment>>(LATEX_SEGMENT_CACHE_SIZE)
    private val markdownStateCache = createLruCache<String, MarkdownState>(MARKDOWN_STATE_CACHE_SIZE)
    private val markdownRenderBlockCache = createLruCache<String, List<String>>(MARKDOWN_RENDER_BLOCK_CACHE_SIZE)

    fun getNormalizedMarkdown(markdown: String): String = normalizedMarkdownCache.cached(markdown) {
        processTaskLists(normalizeLoosePipeTables(normalizeHeadingSpacing(normalizeSingleTildeStrikethrough(markdown))))
    }

    fun getSegments(markdown: String): List<MarkdownSegment> = segmentCache.cached(markdown) {
        parseMarkdownSegments(markdown)
    }

    fun getTaskRuns(content: String): List<TaskRun> = taskRunsCache.cached(content) {
        splitTaskRuns(content)
    }

    fun getMarkdownRenderBlocks(content: String): List<String> = markdownRenderBlockCache.cached(content) {
        splitLinkHeavyMarkdownBlocks(content)
    }

    fun shouldDisableSelectionForPerformance(content: String): Boolean {
        return isLinkHeavyMarkdown(content)
    }

    fun getHighlightedMarkdown(content: String, keyword: String): String {
        if (keyword.isBlank() || content.isBlank()) return content
        return highlightedMarkdownCache.cached(content + "\u0000" + keyword.lowercase()) {
            injectHighlightMark(content, keyword)
        }
    }

    fun getLatexSegments(content: String): List<MarkdownInlineSegment> = latexSegmentsCache.cached(content) {
        splitLatexSegments(content)
    }

    fun getMarkdownState(
        content: String,
        flavour: GFMFlavourDescriptor,
        parser: MarkdownParser,
        referenceLinkHandler: ReferenceLinkHandler
    ): MarkdownState? = synchronized(markdownStateCache) {
        markdownStateCache[content]
    }

    fun putMarkdownState(content: String, state: MarkdownState) {
        synchronized(markdownStateCache) {
            markdownStateCache[content] = state
        }
    }

    fun getDetailsExpanded(key: String): Boolean = synchronized(detailsExpandedCache) {
        detailsExpandedCache[key] ?: false
    }

    fun setDetailsExpanded(key: String, expanded: Boolean) {
        synchronized(detailsExpandedCache) {
            detailsExpandedCache[key] = expanded
        }
    }

    fun getInlineSegments(content: String): List<MarkdownSegment> = inlineSegmentsCache.cached(content) {
        buildList {
            extractImagesFromContent(
                content = content,
                segments = this
            )
        }
    }
}

private class PersistentReferenceLinkHandler : ReferenceLinkHandler {
    private val links = Collections.synchronizedMap(mutableMapOf<String, String>())

    override fun store(label: String, destination: String?) {
        if (destination == null) {
            links.remove(label)
        } else {
            links[label] = destination
        }
    }

    override fun find(label: String): String {
        return links[label].orEmpty()
    }
}

private fun <K, V> createLruCache(maxSize: Int): MutableMap<K, V> =
    Collections.synchronizedMap(
        object : LinkedHashMap<K, V>(maxSize, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
                return size > maxSize
            }
        }
    )

private inline fun <K, V> MutableMap<K, V>.cached(key: K, valueProvider: () -> V): V {
    synchronized(this) {
        if (containsKey(key)) {
            @Suppress("UNCHECKED_CAST")
            return this[key] as V
        }
    }
    val computed = valueProvider()
    synchronized(this) {
        if (containsKey(key)) {
            @Suppress("UNCHECKED_CAST")
            return this[key] as V
        }
        this[key] = computed
    }
    return computed
}

private fun parseMarkdownSegments(markdown: String): List<MarkdownSegment> {
    val segments = mutableListOf<MarkdownSegment>()
    val lines = markdown.lines()
    var i = 0
    
    while (i < lines.size) {
        if (lines[i].trim().startsWith("```")) {
            val firstLine = lines[i].trim()
            val language = firstLine.substring(3).trim().ifBlank { null }
            i++ // 跳过开始标记
            
            // 找到代码块的结束
            val codeLines = mutableListOf<String>()
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            
            if (i < lines.size) {
                i++ // 跳过结束标记
            }
            
            val code = codeLines.joinToString("\n")
            segments += MarkdownSegment.CodeBlock(code, language)
        }
        else {
            val detailsBlock = extractDetailsBlock(lines, i)
            if (detailsBlock != null) {
                detailsBlock.prefix
                    ?.takeIf { it.isNotBlank() }
                    ?.let { extractImagesFromContent(it, segments) }
                segments += MarkdownSegment.Details(
                    summary = detailsBlock.summary,
                    content = detailsBlock.content
                )
                detailsBlock.suffix
                    ?.takeIf { it.isNotBlank() }
                    ?.let { extractImagesFromContent(it, segments) }
                i = detailsBlock.nextIndex
                continue
            }

            val htmlTableBlock = extractHtmlTableBlock(lines, i)
            if (htmlTableBlock != null) {
                htmlTableBlock.prefix
                    ?.takeIf { it.isNotBlank() }
                    ?.let { extractImagesFromContent(it, segments) }
                segments += MarkdownSegment.HtmlTable(htmlTableBlock.table)
                htmlTableBlock.suffix
                    ?.takeIf { it.isNotBlank() }
                    ?.let { extractImagesFromContent(it, segments) }
                i = htmlTableBlock.nextIndex
                continue
            }

            val htmlDivBlock = extractHtmlDivBlock(lines, i)
            if (htmlDivBlock != null) {
                htmlDivBlock.prefix
                    ?.takeIf { it.isNotBlank() }
                    ?.let { extractImagesFromContent(it, segments) }
                segments += MarkdownSegment.HtmlBlock(htmlDivBlock.html)
                htmlDivBlock.suffix
                    ?.takeIf { it.isNotBlank() }
                    ?.let { extractImagesFromContent(it, segments) }
                i = htmlDivBlock.nextIndex
                continue
            }

            val contentStart = i
            while (i < lines.size &&
                   !lines[i].trim().startsWith("```") &&
                   extractDetailsBlock(lines, i) == null &&
                   extractHtmlTableBlock(lines, i) == null &&
                   extractHtmlDivBlock(lines, i) == null) {
                i++
            }
            
            val content = lines.subList(contentStart, i).joinToString("\n")
            if (content.isNotBlank()) {
                // 在非表格、非代码块内容中提取图片
                extractImagesFromContent(content, segments)
            }
        }
    }
    
    if (segments.isEmpty()) {
        segments += MarkdownSegment.Text(processLineBreaks(markdown))
    }
    
    return segments
}

private data class MarkdownDetailsBlock(
    val summary: String,
    val content: String,
    val prefix: String?,
    val suffix: String?,
    val nextIndex: Int
)

private fun extractDetailsBlock(lines: List<String>, startIndex: Int): MarkdownDetailsBlock? {
    val trimmed = lines.getOrNull(startIndex)?.trim() ?: return null
    if (!trimmed.startsWith("<details")) return null

    val prefix = lines[startIndex].substringBefore("<details", missingDelimiterValue = lines[startIndex])
        .takeIf { it.isNotBlank() }

    var i = startIndex + 1
    val contentLines = mutableListOf<String>()
    var summary: String? = null

    while (i < lines.size) {
        val line = lines[i]
        val t = line.trim()

        if (t.startsWith("<summary")) {
            val afterTag = line.substringAfter(">", missingDelimiterValue = "")
            val sameLine = afterTag.substringBefore("</summary>", missingDelimiterValue = afterTag)
            if (line.contains("</summary>")) {
                summary = sameLine.trim()
            } else {
                val summaryLines = mutableListOf<String>()
                if (sameLine.isNotBlank()) summaryLines += sameLine
                i++
                while (i < lines.size && !lines[i].contains("</summary>")) {
                    summaryLines += lines[i]
                    i++
                }
                if (i < lines.size) {
                    val beforeClose = lines[i].substringBefore("</summary>")
                    if (beforeClose.isNotBlank()) summaryLines += beforeClose
                }
                summary = summaryLines.joinToString("\n").trim()
            }
        } else if (t.startsWith("</details")) {
            break
        } else {
            contentLines += line
        }
        i++
    }

    if (i >= lines.size) return null

    val content = contentLines.joinToString("\n").trim('\n', ' ')
    val suffixRemainder = lines[i].substringAfter("</details>", missingDelimiterValue = "")
        .takeIf { it.isNotBlank() }

    return MarkdownDetailsBlock(
        summary = summary?.takeIf { it.isNotBlank() } ?: "详情",
        content = content,
        prefix = prefix,
        suffix = suffixRemainder,
        nextIndex = i + 1
    )
}

@Composable
private fun MarkdownDetailsBlock(
    summary: String,
    contentMarkdown: String,
    textColor: Color,
    backgroundColor: Color,
    imageReferer: String?,
    onImageClick: ((String) -> Unit)?,
    highlightKeyword: String
) {
    val detailsCacheKey = remember(summary, contentMarkdown) {
        summary + "\u0000" + contentMarkdown
    }
    var expanded by remember(detailsCacheKey) {
        mutableStateOf(MarkdownRendererCache.getDetailsExpanded(detailsCacheKey))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .clickable {
                expanded = !expanded
                MarkdownRendererCache.setDetailsExpanded(detailsCacheKey, expanded)
            }
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = summary,
                color = textColor,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (expanded && contentMarkdown.isNotBlank()) {
            Box(modifier = Modifier.padding(top = 10.dp)) {
                MarkdownText(
                    markdown = contentMarkdown,
                    modifier = Modifier.fillMaxWidth(),
                    textColor = textColor,
                    backgroundColor = Color.Transparent,
                    imageReferer = imageReferer,
                    onImageClick = onImageClick,
                    highlightKeyword = highlightKeyword
                )
            }
        }
    }
}

private fun openMarkdownLink(context: Context, url: String) {
    val uri = Uri.parse(url)
    if (uri.scheme.equals("yunhu", ignoreCase = true) && uri.host.equals("ad", ignoreCase = true)) {
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return
    }

    if (com.yhchat.canary.utils.UnifiedLinkHandler.isHandleableLink(url)) {
        com.yhchat.canary.utils.UnifiedLinkHandler.handleLink(context, url)
        return
    }

    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private fun injectHighlightMark(markdown: String, keyword: String): String {
    if (keyword.isBlank() || markdown.isBlank()) return markdown
    val pattern = Regex(Regex.escape(keyword), RegexOption.IGNORE_CASE)
    return pattern.replace(markdown) { match ->
        // 使用行内代码包裹，利用现有 codeStyle 背景实现稳定高亮
        "`" + match.value + "`"
    }
}

/**
 * 从内容中提取图片，将图片和文本分离
 */
private fun extractImagesFromContent(content: String, segments: MutableList<MarkdownSegment>) {
    val regex = Regex("!\\[([^\\]]*)\\]\\(\\s*<?([^)\\s>]+)>?(?:\\s+[\"'][^\"']*[\"'])?\\s*\\)")
    var lastIndex = 0
    var foundAnyImage = false
    
    regex.findAll(content).forEach { match ->
        foundAnyImage = true
        val range = match.range
        if (range.first > lastIndex) {
            val textContent = content.substring(lastIndex, range.first)
            if (textContent.isNotBlank()) {
                segments += MarkdownSegment.Text(processLineBreaks(textContent))
            }
        }
        
        val alt = match.groupValues.getOrNull(1).orEmpty().ifBlank { null }
        val rawUrl = match.groupValues.getOrNull(2).orEmpty().trim()
        val url = rawUrl.removeSurrounding("<", ">").trim()
        if (url.isNotBlank()) {
            segments += MarkdownSegment.Image(url = url, alt = alt)
        }
        
        lastIndex = range.last + 1
    }
    
    // 处理剩余内容
    if (foundAnyImage && lastIndex < content.length) {
        val textContent = content.substring(lastIndex)
        if (textContent.isNotBlank()) {
            segments += MarkdownSegment.Text(processLineBreaks(textContent))
        }
    }
    
    // 如果没有找到图片，整个内容作为文本
    if (!foundAnyImage && content.isNotBlank()) {
        segments += MarkdownSegment.Text(processLineBreaks(content))
    }
}

private data class HtmlTableBlock(
    val prefix: String?,
    val table: String,
    val suffix: String?,
    val nextIndex: Int
)

private data class HtmlDivBlock(
    val prefix: String?,
    val html: String,
    val suffix: String?,
    val nextIndex: Int
)

private fun extractHtmlTableBlock(lines: List<String>, startIndex: Int): HtmlTableBlock? {
    val startLine = lines.getOrNull(startIndex) ?: return null
    val tableStartIndexInLine = startLine.indexOfHtmlTableStart()
    if (tableStartIndexInLine == -1) return null

    val prefix = startLine.substring(0, tableStartIndexInLine)
    val tableBuilder = StringBuilder(startLine.substring(tableStartIndexInLine))
    var index = startIndex

    while (!tableBuilder.containsHtmlTableEnd() && index + 1 < lines.size) {
        index++
        tableBuilder.append('\n').append(lines[index])
    }

    if (!tableBuilder.containsHtmlTableEnd()) return null

    val tableContent = tableBuilder.toString()
    val tableEndMatch = htmlTableEndRegex.find(tableContent) ?: return null
    val tableEnd = tableEndMatch.range.last + 1

    return HtmlTableBlock(
        prefix = prefix,
        table = tableContent.substring(0, tableEnd),
        suffix = tableContent.substring(tableEnd),
        nextIndex = index + 1
    )
}

private fun extractHtmlDivBlock(lines: List<String>, startIndex: Int): HtmlDivBlock? {
    val startLine = lines.getOrNull(startIndex) ?: return null
    val startMatch = htmlDivStartRegex.find(startLine) ?: return null
    val startPos = startMatch.range.first

    val prefix = startLine.substring(0, startPos)
    val builder = StringBuilder(startLine.substring(startPos))
    var index = startIndex
    var depth = countRegexMatches(builder, htmlDivStartRegex) - countRegexMatches(builder, htmlDivEndRegex)

    while (depth > 0 && index + 1 < lines.size) {
        index++
        builder.append('\n').append(lines[index])
        depth = countRegexMatches(builder, htmlDivStartRegex) - countRegexMatches(builder, htmlDivEndRegex)
    }

    if (depth > 0) return null

    val htmlContent = builder.toString()
    val endMatch = htmlDivEndRegex.findAll(htmlContent).lastOrNull() ?: return null
    val endPos = endMatch.range.last + 1

    return HtmlDivBlock(
        prefix = prefix,
        html = htmlContent.substring(0, endPos),
        suffix = htmlContent.substring(endPos),
        nextIndex = index + 1
    )
}

private fun countRegexMatches(text: CharSequence, regex: Regex): Int = regex.findAll(text).count()

private val htmlTableStartRegex = Regex("<table\\b[^>]*>", setOf(RegexOption.IGNORE_CASE))
private val htmlTableEndRegex = Regex("</table>", setOf(RegexOption.IGNORE_CASE))
private val htmlDivStartRegex = Regex("<div\\b[^>]*>", setOf(RegexOption.IGNORE_CASE))
private val htmlDivEndRegex = Regex("</div>", setOf(RegexOption.IGNORE_CASE))
private val latexBlockRegex = Regex("(?s)(^|\\n)\\$\\$(.+?)\\$\\$(?=\\n|$)")
private val latexInlineRegex = Regex("(?<!\\\\)\\$(.+?)(?<!\\\\)\\$")

private fun String.indexOfHtmlTableStart(): Int {
    return htmlTableStartRegex.find(this)?.range?.first ?: -1
}

private fun CharSequence.containsHtmlTableEnd(): Boolean {
    return htmlTableEndRegex.containsMatchIn(this)
}

/**
 * 处理文本换行，支持宽容换行
 * 将单个换行符转换为 Markdown 硬换行（行尾加两个空格）
 * 这样即使不按照严格的 Markdown 格式（双换行或行尾两空格），也能正确显示换行
 */
private fun processLineBreaks(text: String): String {
    val lines = text.lines()
    val result = mutableListOf<String>()
    
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmedLine = line.trim()
        
        // 空行保持原样，作为段落分隔
        if (trimmedLine.isEmpty()) {
            result.add(line)
            i++
            continue
        }
        
        // 检查是否是特殊 Markdown 语法行
        val isSpecialLine = trimmedLine.startsWith("#") ||           // 标题
                           trimmedLine.startsWith("-") ||            // 列表
                           trimmedLine.startsWith("*") ||            // 列表或强调
                           trimmedLine.startsWith("+") ||            // 列表
                           trimmedLine.startsWith(">") ||            // 引用
                           trimmedLine.startsWith("|") ||            // 表格
                           trimmedLine.startsWith("```") ||          // 代码块
                           trimmedLine.matches(Regex("^\\d+\\..*")) || // 有序列表
                           line.trimEnd().endsWith("  ")             // 已有硬换行标记
        
        // 检查下一行是否是特殊语法行或空行
        val nextLineIsSpecial = if (i + 1 < lines.size) {
            val nextTrimmed = lines[i + 1].trim()
            nextTrimmed.isEmpty() ||
            nextTrimmed.startsWith("#") ||
            nextTrimmed.startsWith("-") ||
            nextTrimmed.startsWith("*") ||
            nextTrimmed.startsWith("+") ||
            nextTrimmed.startsWith(">") ||
            nextTrimmed.startsWith("|") ||
            nextTrimmed.startsWith("```") ||
            nextTrimmed.matches(Regex("^\\d+\\..*"))
        } else {
            true // 最后一行
        }
        
        // 如果当前行不是特殊语法，且下一行也不是特殊语法或空行，添加硬换行标记
        if (!isSpecialLine && !nextLineIsSpecial) {
            result.add("$line  ")
        } else {
            result.add(line)
        }
        
        i++
    }
    
    return result.joinToString("\n")
}

private fun splitInlineSegmentsByLine(
    segments: List<MarkdownInlineSegment>
): List<List<MarkdownInlineSegment>> {
    if (segments.isEmpty()) return emptyList()

    val lines = mutableListOf<MutableList<MarkdownInlineSegment>>()
    var currentLine = mutableListOf<MarkdownInlineSegment>()

    fun flushLine() {
        lines += currentLine
        currentLine = mutableListOf()
    }

    segments.forEach { segment ->
        when (segment) {
            is MarkdownInlineSegment.Markdown -> {
                val parts = segment.content.split('\n')
                parts.forEachIndexed { index, part ->
                    val normalizedPart = part.removeSuffix("  ")
                    if (normalizedPart.isNotEmpty()) {
                        currentLine += MarkdownInlineSegment.Markdown(normalizedPart)
                    }
                    if (index != parts.lastIndex) {
                        flushLine()
                    }
                }
            }

            is MarkdownInlineSegment.LatexInline -> {
                currentLine += segment
            }

            is MarkdownInlineSegment.LatexBlock -> {
                currentLine += segment
            }
        }
    }

    lines += currentLine
    return lines
}

private fun splitLatexSegments(content: String): List<MarkdownInlineSegment> {
    if (!content.contains('$')) {
        return listOf(MarkdownInlineSegment.Markdown(content))
    }

    val segments = mutableListOf<MarkdownInlineSegment>()
    var currentIndex = 0

    latexBlockRegex.findAll(content).forEach { match ->
        val fullRange = match.range
        val blockPrefixLength = match.groups[1]?.value?.length ?: 0
        val contentStart = fullRange.first + blockPrefixLength

        if (contentStart > currentIndex) {
            val before = content.substring(currentIndex, contentStart)
            appendInlineLatexSegments(before, segments)
        }

        val latex = match.groups[2]?.value?.trim().orEmpty()
        if (latex.isNotBlank()) {
            segments += MarkdownInlineSegment.LatexBlock(latex)
        }

        currentIndex = fullRange.last + 1
    }

    if (currentIndex < content.length) {
        appendInlineLatexSegments(content.substring(currentIndex), segments)
    }

    if (segments.isEmpty()) {
        return listOf(MarkdownInlineSegment.Markdown(content))
    }

    return segments
}

private fun appendInlineLatexSegments(
    content: String,
    segments: MutableList<MarkdownInlineSegment>
) {
    if (content.isEmpty()) return
    if (!content.contains('$')) {
        segments += MarkdownInlineSegment.Markdown(content)
        return
    }

    var currentIndex = 0
    latexInlineRegex.findAll(content).forEach { match ->
        val range = match.range
        if (range.first > currentIndex) {
            val before = content.substring(currentIndex, range.first)
            if (before.isNotEmpty()) {
                segments += MarkdownInlineSegment.Markdown(before)
            }
        }

        val latex = match.groups[1]?.value?.trim().orEmpty()
        if (latex.isNotBlank()) {
            segments += MarkdownInlineSegment.LatexInline(latex)
        } else {
            segments += MarkdownInlineSegment.Markdown(match.value)
        }

        currentIndex = range.last + 1
    }

    if (currentIndex < content.length) {
        val trailing = content.substring(currentIndex)
        if (trailing.isNotEmpty()) {
            segments += MarkdownInlineSegment.Markdown(trailing)
        }
    }

    if (segments.isEmpty()) {
        segments += MarkdownInlineSegment.Markdown(content)
    }
}

/**
 * 处理任务列表语法
 * 将 - [x] 转换为 ☑ (已完成)
 * 将 - [ ] 转换为 ☐ (未完成)
 */
private fun processTaskLists(markdown: String): String {
    return markdown.lines().joinToString("\n") { line ->
        val trimmed = line.trimStart()
        when {
            // 已完成的任务 - [x] 或 - [X]
            trimmed.startsWith("- [x]") || trimmed.startsWith("- [X]") -> {
                val indent = line.takeWhile { it.isWhitespace() }
                val content = trimmed.substring(5).trim()
                "$indent☑ $content"
            }
            // 未完成的任务 - [ ]
            trimmed.startsWith("- [ ]") -> {
                val indent = line.takeWhile { it.isWhitespace() }
                val content = trimmed.substring(5).trim()
                "$indent☐ $content"
            }
            else -> line
        }
    }
}

private fun normalizeHeadingSpacing(markdown: String): String {
    val lines = markdown.lines()
    if (lines.isEmpty()) return markdown
    val output = mutableListOf<String>()
    var inFence = false
    val h1Regex = Regex("^\\s{0,3}#\\s+.+$")

    for (index in lines.indices) {
        val line = lines[index]
        val trimmed = line.trimStart()

        if (trimmed.startsWith("```")) {
            inFence = !inFence
            output += line
            continue
        }

        if (!inFence && h1Regex.matches(line)) {
            if (output.isNotEmpty() && output.last().isNotBlank()) {
                output += ""
            }
            output += line
            val next = lines.getOrNull(index + 1).orEmpty()
            if (next.isNotBlank()) {
                output += ""
            }
        } else {
            output += line
        }
    }

    return output.joinToString("\n")
}

private fun normalizeSingleTildeStrikethrough(markdown: String): String {
    if (!markdown.contains('~')) return markdown

    val output = StringBuilder(markdown.length)
    var inFence = false

    markdown.lineSequence().forEachIndexed { index, line ->
        if (index > 0) output.append('\n')

        val trimmed = line.trimStart()
        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            output.append(line)
            inFence = !inFence
            return@forEachIndexed
        }

        output.append(
            if (inFence) {
                line
            } else {
                normalizeSingleTildeInLine(line)
            }
        )
    }

    return output.toString()
}

private fun normalizeSingleTildeInLine(line: String): String {
    if (!line.contains('~')) return line

    val output = StringBuilder(line.length)
    var index = 0
    var inInlineCode = false

    while (index < line.length) {
        val current = line[index]
        if (current == '`') {
            output.append(current)
            inInlineCode = !inInlineCode
            index++
            continue
        }

        if (!inInlineCode && current == '~' && isSingleTildeDelimiter(line, index)) {
            val closeIndex = findSingleTildeClose(line, index + 1)
            if (closeIndex != -1) {
                output.append("~~")
                output.append(line.substring(index + 1, closeIndex))
                output.append("~~")
                index = closeIndex + 1
                continue
            }
        }

        output.append(current)
        index++
    }

    return output.toString()
}

private fun findSingleTildeClose(line: String, startIndex: Int): Int {
    var index = startIndex
    var inInlineCode = false

    while (index < line.length) {
        val current = line[index]
        if (current == '`') {
            inInlineCode = !inInlineCode
            index++
            continue
        }

        if (!inInlineCode && current == '~' && isSingleTildeDelimiter(line, index)) {
            val inner = line.substring(startIndex, index)
            if (inner.isNotBlank()) {
                return index
            }
        }

        index++
    }

    return -1
}

private fun isSingleTildeDelimiter(line: String, index: Int): Boolean {
    if (line.getOrNull(index - 1) == '~' || line.getOrNull(index + 1) == '~') return false
    if (index > 0 && line[index - 1] == '\\') return false
    return true
}

private fun normalizeLoosePipeTables(markdown: String): String {
    val lines = markdown.lines()
    if (lines.isEmpty()) return markdown

    val output = mutableListOf<String>()
    var i = 0
    var inFence = false

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        if (trimmed.startsWith("```")) {
            inFence = !inFence
            output += line
            i++
            continue
        }

        if (!inFence && isLoosePipeTableStart(lines, i)) {
            val block = mutableListOf<String>()
            while (i < lines.size && isLoosePipeTableLine(lines[i])) {
                block += lines[i]
                i++
            }
            output += normalizePipeTableBlock(block)
            continue
        }

        output += line
        i++
    }

    return output.joinToString("\n")
}

private fun isLoosePipeTableStart(lines: List<String>, index: Int): Boolean {
    val header = lines.getOrNull(index) ?: return false
    val separator = lines.getOrNull(index + 1) ?: return false
    return header.count { it == '|' } > 0 && isPipeTableSeparator(separator)
}

private fun isLoosePipeTableLine(line: String): Boolean {
    val trimmed = line.trim()
    if (trimmed.isEmpty()) return false
    if (trimmed.startsWith("***") || trimmed.startsWith("---")) return false
    return trimmed.contains('|') || isPipeTableSeparator(trimmed)
}

private fun isPipeTableSeparator(line: String): Boolean {
    val trimmed = line.trim().trim('|').trim()
    if (trimmed.isEmpty() || !trimmed.contains('-')) return false
    return trimmed.split('|').all { cell ->
        val value = cell.trim()
        value.length >= 3 && value.all { it == '-' || it == ':' }
    }
}

private fun normalizePipeTableBlock(block: List<String>): List<String> {
    if (block.size < 2) return block

    val parsedRows = block.map { line ->
        line.trim().trim('|').split('|').map { it.trim() }
    }
    val columnCount = parsedRows.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1

    return parsedRows.mapIndexed { index, cells ->
        val normalizedCells = cells.toMutableList()
        while (normalizedCells.size < columnCount) {
            normalizedCells += ""
        }

        if (index == 1 && isPipeTableSeparator(block[index])) {
            val separatorCells = normalizedCells.map { cell ->
                val trimmed = cell.trim()
                when {
                    trimmed.startsWith(":") && trimmed.endsWith(":") -> ":---:"
                    trimmed.endsWith(":") -> "---:"
                    trimmed.startsWith(":") -> ":---"
                    else -> "---"
                }
            }
            separatorCells.joinToString(prefix = "|", postfix = "|", separator = "|")
        } else {
            normalizedCells.joinToString(prefix = "|", postfix = "|", separator = "|")
        }
    }
}

private val InlineMarkdownLinkRegex = Regex("""\[[^\]]+]\([^)]+\)""")

private fun splitLinkHeavyMarkdownBlocks(content: String): List<String> {
    if (!isLinkHeavyMarkdown(content)) return listOf(content)

    val blocks = mutableListOf<String>()
    val current = mutableListOf<String>()
    var inFence = false

    fun flush() {
        if (current.isNotEmpty()) {
            blocks += current.joinToString("\n").trim('\n')
            current.clear()
        }
    }

    content.lines().forEach { line ->
        val trimmed = line.trim()
        val startsFence = trimmed.startsWith("```")

        if (!inFence && trimmed.isBlank()) {
            flush()
            return@forEach
        }

        if (!inFence && trimmed.startsWith("#") && current.isNotEmpty()) {
            flush()
        }

        current += line

        if (startsFence) {
            inFence = !inFence
        }

        if (!inFence && trimmed.startsWith("#")) {
            flush()
        }
    }

    flush()
    return blocks.ifEmpty { listOf(content) }
}

private fun isLinkHeavyMarkdown(content: String): Boolean {
    var count = 0
    for (match in InlineMarkdownLinkRegex.findAll(content)) {
        count++
        if (count >= LINK_HEAVY_MARKDOWN_THRESHOLD) return true
    }
    return false
}

private const val LINK_HEAVY_MARKDOWN_THRESHOLD = 32

private sealed interface TaskRun {
    data class Markdown(val content: String) : TaskRun
    data class Task(val checked: Boolean, val content: String) : TaskRun
}

private fun splitTaskRuns(content: String): List<TaskRun> {
    val runs = mutableListOf<TaskRun>()
    val markdownBuffer = mutableListOf<String>()

    fun flushMarkdown() {
        if (markdownBuffer.isNotEmpty()) {
            runs += TaskRun.Markdown(markdownBuffer.joinToString("\n"))
            markdownBuffer.clear()
        }
    }

    content.lines().forEach { line ->
        val trimmedStart = line.trimStart()
        when {
            trimmedStart.startsWith("☑ ") -> {
                flushMarkdown()
                runs += TaskRun.Task(
                    checked = true,
                    content = trimmedStart.removePrefix("☑ ").trim()
                )
            }
            trimmedStart.startsWith("☐ ") -> {
                flushMarkdown()
                runs += TaskRun.Task(
                    checked = false,
                    content = trimmedStart.removePrefix("☐ ").trim()
                )
            }
            else -> markdownBuffer += line
        }
    }
    flushMarkdown()
    return runs
}
