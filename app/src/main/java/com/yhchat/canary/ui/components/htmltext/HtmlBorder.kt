package com.yhchat.canary.ui.components.htmltext

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal data class HtmlBorder(val width: Dp, val color: Color, val style: String) {
    fun pathEffect(widthPx: Float): PathEffect? = when (style) {
        "dashed" -> PathEffect.dashPathEffect(floatArrayOf(widthPx * 3, widthPx * 2))
        "dotted" -> PathEffect.dashPathEffect(floatArrayOf(widthPx, widthPx))
        else -> null
    }
}

/** Parse CSS border shorthand without splitting the spaces inside rgb()/rgba(). */
internal fun parseHtmlBorder(value: String?, currentColor: Color): HtmlBorder {
    if (value.isNullOrBlank()) return HtmlBorder(0.dp, currentColor, "none")
    val tokens = splitCssTopLevel(value.replace('\n', ' ').replace('\t', ' '), ' ')
        .filter { it.isNotBlank() }.map { it.trim().lowercase() }
    val style = tokens.firstOrNull { it in setOf("none", "hidden", "solid", "dashed", "dotted", "double", "groove", "ridge", "inset", "outset") } ?: "none"
    val width = tokens.firstNotNullOfOrNull { token ->
        when (token) {
            "thin" -> 1.dp
            "medium" -> 3.dp
            "thick" -> 5.dp
            "0" -> 0.dp
            else -> if (token.endsWith("px") || token.endsWith("dp")) {
                token.dropLast(2).toFloatOrNull()?.takeIf { it.isFinite() && it >= 0f }?.dp
            } else null
        }
    } ?: 3.dp
    val color = tokens.firstNotNullOfOrNull { token ->
        if (token == "currentcolor") currentColor else CssParser.parseColor(token)
    } ?: currentColor
    return HtmlBorder(if (style == "none" || style == "hidden") 0.dp else width, color, style)
}
