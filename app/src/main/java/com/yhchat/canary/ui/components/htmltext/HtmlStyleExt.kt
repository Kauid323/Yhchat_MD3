package com.yhchat.canary.ui.components.htmltext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal fun CssStyle.mergeText(parent: CssStyle): CssStyle = CssStyle(
    color = color ?: parent.color,
    // 子级写了 inherit 就跟随父级；否则保留自己的标记
    colorInherit = colorInherit || (color == null && parent.colorInherit),
    fontSize = fontSize ?: parent.fontSize,
    fontWeight = fontWeight ?: parent.fontWeight,
    fontStyle = fontStyle ?: parent.fontStyle,
    textDecoration = textDecoration ?: parent.textDecoration,
    textAlign = textAlign ?: parent.textAlign,
    lineHeight = lineHeight ?: if (lineHeightMultiplier == null) parent.lineHeight else null,
    lineHeightMultiplier = lineHeightMultiplier ?: if (lineHeight == null) parent.lineHeightMultiplier else null,
    fontFamily = fontFamily ?: parent.fontFamily,
    wordWrap = wordWrap ?: parent.wordWrap,
    whiteSpace = whiteSpace ?: parent.whiteSpace,
    listStyleType = listStyleType ?: parent.listStyleType
)

internal fun CssStyle.mergeWith(override: CssStyle): CssStyle = CssStyle(
    color = override.color ?: color,
    colorInherit = override.colorInherit || colorInherit,
    backgroundColor = override.backgroundColor ?: backgroundColor,
    fontSize = override.fontSize ?: fontSize,
    fontWeight = override.fontWeight ?: fontWeight,
    fontStyle = override.fontStyle ?: fontStyle,
    textDecoration = override.textDecoration ?: textDecoration,
    textAlign = override.textAlign ?: textAlign,
    width = override.width ?: width,
    maxWidth = override.maxWidth ?: maxWidth,
    minWidth = override.minWidth ?: minWidth,
    height = override.height ?: height,
    minHeight = override.minHeight ?: minHeight,
    widthPercent = override.widthPercent ?: widthPercent,
    heightPercent = override.heightPercent ?: heightPercent,
    margin = override.margin ?: margin,
    marginTop = override.marginTop ?: marginTop,
    marginRight = override.marginRight ?: marginRight,
    marginBottom = override.marginBottom ?: marginBottom,
    marginLeft = override.marginLeft ?: marginLeft,
    marginAuto = override.marginAuto || marginAuto,
    padding = override.padding ?: padding,
    paddingTop = override.paddingTop ?: paddingTop,
    paddingRight = override.paddingRight ?: paddingRight,
    paddingBottom = override.paddingBottom ?: paddingBottom,
    paddingLeft = override.paddingLeft ?: paddingLeft,
    borderRadius = override.borderRadius ?: borderRadius,
    borderRadiusIsCircle = override.borderRadiusIsCircle || borderRadiusIsCircle,
    border = override.border ?: border,
    borderTop = override.borderTop ?: borderTop,
    borderRight = override.borderRight ?: borderRight,
    borderBottom = override.borderBottom ?: borderBottom,
    borderLeft = override.borderLeft ?: borderLeft,
    boxShadow = override.boxShadow ?: boxShadow,
    lineHeight = override.lineHeight ?: if (override.lineHeightMultiplier == null) lineHeight else null,
    lineHeightMultiplier = override.lineHeightMultiplier ?: if (override.lineHeight == null) lineHeightMultiplier else null,
    fontFamily = override.fontFamily ?: fontFamily,
    backgroundGradient = override.backgroundGradient ?: backgroundGradient,
    display = override.display ?: display,
    flexDirection = override.flexDirection ?: flexDirection,
    alignItems = override.alignItems ?: alignItems,
    gap = override.gap ?: gap,
    flex = override.flex ?: flex,
    flexGrow = override.flexGrow ?: flexGrow,
    flexShrink = override.flexShrink ?: flexShrink,
    flexWrap = override.flexWrap ?: flexWrap,
    justifyContent = override.justifyContent ?: justifyContent,
    objectFit = override.objectFit ?: objectFit,
    wordWrap = override.wordWrap ?: wordWrap,
    whiteSpace = override.whiteSpace ?: whiteSpace,
    borderCollapse = override.borderCollapse ?: borderCollapse,
    borderSpacing = override.borderSpacing ?: borderSpacing,
    listStyleType = override.listStyleType ?: listStyleType,
    transition = override.transition ?: transition,
    outline = override.outline ?: outline
)

internal fun CssStyle.toComposeTextStyle(fallback: TextStyle = TextStyle.Default): TextStyle {
    val resolvedFontSize = fontSize ?: fallback.fontSize.takeIf { it != TextUnit.Unspecified } ?: 14.sp
    val resolvedLineHeight = when {
        lineHeight != null -> lineHeight
        lineHeightMultiplier != null -> resolvedFontSize * (lineHeightMultiplier ?: 1f)
        else -> TextUnit.Unspecified
    }
    return TextStyle(
        color = color ?: fallback.color,
        fontSize = resolvedFontSize,
        fontWeight = fontWeight ?: fallback.fontWeight ?: FontWeight.Normal,
        fontStyle = fontStyle ?: fallback.fontStyle ?: FontStyle.Normal,
        textDecoration = textDecoration ?: fallback.textDecoration ?: TextDecoration.None,
        textAlign = textAlign ?: fallback.textAlign,
        lineHeight = resolvedLineHeight,
        fontFamily = when (fontFamily?.lowercase()) {
            "serif" -> FontFamily.Serif
            "sans-serif" -> FontFamily.SansSerif
            "monospace" -> FontFamily.Monospace
            "cursive" -> FontFamily.Cursive
            else -> fallback.fontFamily ?: FontFamily.Default
        }
    )
}

internal fun Modifier.htmlBoxModel(style: CssStyle): Modifier {
    val marginTop = (style.marginTop ?: style.margin ?: 0.dp).coerceAtLeast(0.dp)
    val marginBottom = (style.marginBottom ?: style.margin ?: 0.dp).coerceAtLeast(0.dp)
    val marginStart = (style.marginLeft ?: style.margin ?: 0.dp).coerceAtLeast(0.dp)
    val marginEnd = (style.marginRight ?: style.margin ?: 0.dp).coerceAtLeast(0.dp)
    val paddingTop = (style.paddingTop ?: style.padding ?: 0.dp).coerceAtLeast(0.dp)
    val paddingBottom = (style.paddingBottom ?: style.padding ?: 0.dp).coerceAtLeast(0.dp)
    val paddingStart = (style.paddingLeft ?: style.padding ?: 0.dp).coerceAtLeast(0.dp)
    val paddingEnd = (style.paddingRight ?: style.padding ?: 0.dp).coerceAtLeast(0.dp)
    val radius = style.borderRadius
    // border-radius:50% 或 任意不小于短边的圆角都当圆形处理
    val shape: androidx.compose.ui.graphics.Shape? = when {
        style.borderRadiusIsCircle -> CircleShape
        radius != null -> RoundedCornerShape(radius)
        else -> null
    }

    var modifier = this
    val widthPercent = style.widthPercent
    if (style.width == null && widthPercent != null && widthPercent.isFinite()) {
        modifier = modifier.layout { measurable, constraints ->
            val fraction = widthPercent.coerceAtLeast(0f)
            val width = if (constraints.hasBoundedWidth) {
                val cssMin = (style.minWidth ?: 0.dp).coerceAtLeast(0.dp).roundToPx().coerceAtMost(constraints.maxWidth)
                val cssMax = style.maxWidth?.takeIf { it < Dp.Infinity }?.coerceAtLeast(0.dp)?.roundToPx()
                    ?.coerceIn(cssMin, constraints.maxWidth) ?: constraints.maxWidth
                (constraints.maxWidth.toDouble() * fraction).coerceIn(cssMin.toDouble(), cssMax.toDouble()).roundToInt()
            } else null
            val adjusted = if (width != null) constraints.copy(
                minWidth = width.coerceAtLeast(constraints.minWidth),
                maxWidth = width.coerceAtLeast(constraints.minWidth)
            ) else constraints
            val placeable = measurable.measure(adjusted)
            layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
        }
    }
    modifier = modifier.padding(
        top = marginTop,
        bottom = marginBottom,
        start = marginStart,
        end = marginEnd
    )

    val shadowElevation = style.boxShadow?.let(::parseShadowElevation)
    if (shadowElevation != null && shadowElevation > 0.dp) {
        modifier = modifier.shadow(
            elevation = shadowElevation,
            shape = shape ?: RoundedCornerShape(0.dp),
            clip = false
        )
    }

    val minWidth = (style.minWidth ?: 0.dp).coerceAtLeast(0.dp)
    val maxWidth = (style.maxWidth ?: Dp.Infinity).coerceAtLeast(minWidth)
    val resolvedWidth = style.width?.coerceIn(minWidth, maxWidth)
    if (resolvedWidth != null) modifier = modifier.width(resolvedWidth)
    else if (style.minWidth != null || style.maxWidth != null) {
        modifier = modifier.widthIn(min = minWidth, max = maxWidth)
    }
    val minHeight = (style.minHeight ?: 0.dp).coerceAtLeast(0.dp)
    if (style.height != null) modifier = modifier.height(style.height.coerceAtLeast(minHeight))
    else if (style.minHeight != null) modifier = modifier.heightIn(min = minHeight)

    if (shape != null) modifier = modifier.clip(shape)
    if (style.backgroundColor != null) modifier = modifier.background(style.backgroundColor)
    style.backgroundGradient?.takeIf { it.size >= 2 }?.let { colors ->
        modifier = modifier.background(androidx.compose.ui.graphics.Brush.linearGradient(colors))
    }

    val borderColor = style.color ?: Color(0xFFE0E0E0)
    val topBorder = parseHtmlBorder(style.borderTop ?: style.border, borderColor)
    val rightBorder = parseHtmlBorder(style.borderRight ?: style.border, borderColor)
    val bottomBorder = parseHtmlBorder(style.borderBottom ?: style.border, borderColor)
    val leftBorder = parseHtmlBorder(style.borderLeft ?: style.border, borderColor)
    if (listOf(topBorder, rightBorder, bottomBorder, leftBorder).any { it.width > 0.dp }) {
        modifier = modifier.drawBehind {
            val uniform = topBorder == rightBorder && topBorder == bottomBorder && topBorder == leftBorder
            if (uniform) {
                val stroke = topBorder.width.toPx().coerceAtMost(minOf(size.width, size.height))
                if (stroke > 0f) {
                    val inset = stroke / 2f
                    val radiusPx = if (style.borderRadiusIsCircle) minOf(size.width, size.height) / 2f
                        else (style.borderRadius?.toPx() ?: 0f)
                    drawRoundRect(
                        color = topBorder.color,
                        topLeft = Offset(inset, inset),
                        size = androidx.compose.ui.geometry.Size((size.width - stroke).coerceAtLeast(0f), (size.height - stroke).coerceAtLeast(0f)),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius((radiusPx - inset).coerceAtLeast(0f)),
                        style = Stroke(width = stroke, pathEffect = topBorder.pathEffect(stroke))
                    )
                }
            } else {
                fun edge(border: HtmlBorder, start: Offset, end: Offset, width: Float) {
                    if (width > 0f) drawLine(border.color, start, end, strokeWidth = width, pathEffect = border.pathEffect(width))
                }
                val top = topBorder.width.toPx().coerceAtMost(size.height)
                val bottom = bottomBorder.width.toPx().coerceAtMost(size.height)
                val left = leftBorder.width.toPx().coerceAtMost(size.width)
                val right = rightBorder.width.toPx().coerceAtMost(size.width)
                edge(topBorder, Offset(0f, top / 2), Offset(size.width, top / 2), top)
                edge(bottomBorder, Offset(0f, size.height - bottom / 2), Offset(size.width, size.height - bottom / 2), bottom)
                edge(leftBorder, Offset(left / 2, 0f), Offset(left / 2, size.height), left)
                edge(rightBorder, Offset(size.width - right / 2, 0f), Offset(size.width - right / 2, size.height), right)
            }
        }
    }
    return modifier.padding(
        top = paddingTop + topBorder.width,
        bottom = paddingBottom + bottomBorder.width,
        start = paddingStart + leftBorder.width,
        end = paddingEnd + rightBorder.width
    )
}

/**
 * 给图片 / 头像用的外壳：在 [htmlBoxModel] 之外补上圆角裁剪和百分比尺寸。
 *
 * 之前的图片节点完全不经过 htmlBoxModel，所以 `border-radius:50%` 被丢掉，
 * 群聊卡片里的圆头像一直显示成方图。这里单独抽一个函数，
 * 因为它需要父容器宽度来解百分比（[containerWidth]）。
 */
internal fun Modifier.htmlMediaBoxModel(style: CssStyle, containerWidth: Dp?, containerHeight: Dp? = null): Modifier {
    var modifier = this
    // 百分比宽高换算成实际 Dp；解析不出就退回绝对尺寸
    val resolvedWidth = style.width
        ?: style.widthPercent?.let { pct -> containerWidth?.let { it * pct } }
    val resolvedHeight = style.height
        ?: style.heightPercent?.let { pct -> containerHeight?.let { it * pct } }

    modifier = modifier.htmlBoxModel(
        style.copy(
            width = resolvedWidth,
            height = resolvedHeight
        )
    )
    return modifier
}

internal fun parseBorderColor(border: String): Color {
    val colorPart = border.trim().split(Regex("\\s+")).lastOrNull() ?: "#e0e0e0"
    return CssParser.parseColor(colorPart) ?: Color(0xFFE0E0E0)
}

private fun parseShadowElevation(boxShadow: String): androidx.compose.ui.unit.Dp {
    val values = Regex("(-?\\d+(?:\\.\\d+)?)px")
        .findAll(boxShadow)
        .mapNotNull { it.groupValues.getOrNull(1)?.toFloatOrNull() }
        .toList()
    if (values.isEmpty()) return 0.dp
    val blur = values.getOrNull(2) ?: values.last()
    return (blur / 2f).coerceAtLeast(1f).dp
}
