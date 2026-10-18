package com.yhchat.canary.ui.components.htmltext

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun HtmlTextMessage(
    html: String,
    modifier: Modifier = Modifier,
    linkBoxModifier: (text: String, link: String) -> Modifier = { _, _ -> Modifier },
    urlSpanStyle: SpanStyle = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline
    ),
    colorMapping: Map<Color, Color> = emptyMap(),
    inlineContent: Map<String, InlineTextContent> = mapOf(),
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    onUriClick: ((String) -> Unit)? = null,
    onImageClick: ((String) -> Unit)? = null,
    enableCssSupport: Boolean = true,
    useAdvancedRenderer: Boolean = true
) {
    if (useAdvancedRenderer) {
        // 使用高级渲染器处理复杂的 CSS 布局
        AdvancedHtmlRenderer(
            html = html,
            modifier = modifier,
            onImageClick = onImageClick,
            onLinkClick = onUriClick
        )
    } else {
        // 使用传统的 AnnotatedString 渲染器
        val parsed = htmlToAnnotatedStringWithImages(
            html = html,
            urlSpanStyle = urlSpanStyle,
            colorMapping = colorMapping,
            enableCssSupport = enableCssSupport
        )
        val annotatedString = parsed.annotatedString

        val density = LocalDensity.current
        val placeholderSizeSp = with(density) { 220.dp.toSp() }

        // 从 AnnotatedString 中提取内联内容注解并构建 InlineTextContent 映射
        val inlineContentAnnotations = annotatedString.getStringAnnotations("inlineContent", 0, annotatedString.length)
        val imageInlineContent: Map<String, InlineTextContent> = inlineContentAnnotations.associate { annotation ->
            val id = annotation.item
            val src = parsed.inlineImages[id] ?: ""
            id to InlineTextContent(
                placeholder = Placeholder(
                    width = placeholderSizeSp,
                    height = placeholderSizeSp,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.AboveBaseline
                )
            ) {
                val context = LocalContext.current
                val imageRequest = if (src.contains("jwznb.com", ignoreCase = true)) {
                    ImageRequest.Builder(context)
                        .data(src)
                        .addHeader("Referer", "https://myapp.jwznb.com")
                        .build()
                } else {
                    src
                }

                AsyncImage(
                    model = imageRequest,
                    contentDescription = "图片",
                    modifier = Modifier
                        .size(220.dp)
                        .clickable(enabled = onImageClick != null) {
                            onImageClick?.invoke(src)
                        },
                    contentScale = ContentScale.Fit
                )
            }
        }

        val mergedInlineContent = if (inlineContent.isEmpty()) imageInlineContent else (inlineContent + imageInlineContent)

        BaseHtmlText(
            modifier = modifier,
            annotatedString = annotatedString,
            linkBoxModifier = linkBoxModifier,
            onTextLayout = onTextLayout,
            onUriClick = onUriClick,
        ) { textModifier, onLayout ->
            Text(
                text = annotatedString,
                modifier = textModifier,
                color = color,
                style = style,
                inlineContent = mergedInlineContent,
                onTextLayout = onLayout,
            )
        }
    }
}
