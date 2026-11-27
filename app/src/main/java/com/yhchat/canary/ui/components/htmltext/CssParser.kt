package com.yhchat.canary.ui.components.htmltext

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * CSS 样式数据类
 */
data class CssStyle(
    val color: Color? = null,
    val backgroundColor: Color? = null,
    val fontSize: TextUnit? = null,
    val fontWeight: FontWeight? = null,
    val fontStyle: FontStyle? = null,
    val textDecoration: TextDecoration? = null,
    val textAlign: TextAlign? = null,
    val width: Dp? = null,
    val maxWidth: Dp? = null,
    val minWidth: Dp? = null,
    val height: Dp? = null,
    val minHeight: Dp? = null,
    /**
     * 百分比宽高。
     *
     * 这段 HTML 里 `width:24px` 是绝对的，但真实世界的富文本经常写 `width:100%`、
     * `border-radius:50%`。百分比没法在解析期换算成 Dp（要知道父容器宽度），
     * 所以单独存一份，由渲染层用 BoxWithConstraints 的可用宽度去解析。
     */
    val widthPercent: Float? = null,
    val heightPercent: Float? = null,
    val margin: Dp? = null,
    val marginTop: Dp? = null,
    val marginRight: Dp? = null,
    val marginBottom: Dp? = null,
    val marginLeft: Dp? = null,
    /** margin: auto —— 用于水平居中 */
    val marginAuto: Boolean = false,
    val padding: Dp? = null,
    val paddingTop: Dp? = null,
    val paddingRight: Dp? = null,
    val paddingBottom: Dp? = null,
    val paddingLeft: Dp? = null,
    val borderRadius: Dp? = null,
    /** border-radius: 50% —— 圆形头像就靠它 */
    val borderRadiusIsCircle: Boolean = false,
    val border: String? = null,
    val borderTop: String? = null,
    val borderRight: String? = null,
    val borderBottom: String? = null,
    val borderLeft: String? = null,
    val boxShadow: String? = null,
    val lineHeight: TextUnit? = null,
    val lineHeightMultiplier: Float? = null,
    val fontFamily: String? = null,
    val backgroundGradient: List<Color>? = null,
    val display: String? = null,
    val flexDirection: String? = null,
    val alignItems: String? = null,
    val gap: Dp? = null,
    val flex: Float? = null,
    val flexGrow: Float? = null,
    val flexShrink: Float? = null,
    val flexWrap: String? = null,
    val justifyContent: String? = null,
    val objectFit: String? = null,
    val wordWrap: String? = null,
    val whiteSpace: String? = null,
    val borderCollapse: String? = null,
    val borderSpacing: Dp? = null,
    val listStyleType: String? = null,
    val transition: String? = null,
    val outline: String? = null,
    /**
     * `color: inherit` —— 显式要求继承父级颜色。
     *
     * 必须和"没写 color"区分开：链接默认会被染成主题色并加下划线，
     * 而 `color:inherit;text-decoration:none` 是作者明确要求"看起来像普通文字"。
     * 两者都解析成 `color == null` 的话，这个意图就丢了。
     */
    val colorInherit: Boolean = false
)

/**
 * CSS 解析器
 */
object CssParser {

    private data class BoxValues(
        val top: Dp? = null,
        val right: Dp? = null,
        val bottom: Dp? = null,
        val left: Dp? = null
    )
    
    /**
     * 解析 style 属性字符串
     */
    fun parseStyle(styleString: String): CssStyle {
        val properties = mutableMapOf<String, String>()
        
        // 分割 CSS 属性
        val importantProperties = mutableSetOf<String>()
        readCssDeclarations(styleString).forEach { (property, rawValue) ->
            val important = Regex("(?i)\\s*!important\\s*$").containsMatchIn(rawValue)
            val value = rawValue.replace(Regex("(?i)\\s*!important\\s*$"), "").trim()
            if (important || property !in importantProperties) {
                properties[property] = value
                if (important) importantProperties.add(property)
            }
        }

        val marginBox = parseBoxValues(properties["margin"])
        val paddingBox = parseBoxValues(properties["padding"])
        val parsedLineHeight = properties["line-height"]?.let { parseLineHeight(it) }
        val flexParts = properties["flex"]?.let { parseFlexShorthand(it) }
        val listStyleType = properties["list-style-type"]
            ?: properties["list-style"]?.let { parseListStyleType(it) }
        val backgroundShorthand = properties["background"]
        val resolvedBackgroundColor = properties["background-color"]?.let { parseColor(it) }
            ?: backgroundShorthand?.let { parseBackgroundColor(it) }
        val radiusRaw = properties["border-radius"]
        val marginRaw = properties["margin"]
        val colorRaw = properties["color"]?.trim()

        return CssStyle(
            color = colorRaw?.let { parseColor(it) },
            // color:inherit 单独记下来：它要求"跟随父级"，不能当作"没设置"
            colorInherit = colorRaw?.equals("inherit", ignoreCase = true) == true,
            backgroundColor = resolvedBackgroundColor,
            fontSize = properties["font-size"]?.let { parseFontSize(it) },
            fontWeight = properties["font-weight"]?.let { parseFontWeight(it) },
            fontStyle = properties["font-style"]?.let { parseFontStyle(it) },
            textDecoration = properties["text-decoration"]?.let { parseTextDecoration(it) },
            textAlign = properties["text-align"]?.let { parseTextAlign(it) },
            width = properties["width"]?.let { parseDimension(it) },
            maxWidth = properties["max-width"]?.let { parseDimension(it) },
            minWidth = properties["min-width"]?.let { parseDimension(it) },
            height = properties["height"]?.let { parseDimension(it) },
            minHeight = properties["min-height"]?.let { parseDimension(it) },
            widthPercent = properties["width"]?.let { parsePercent(it) },
            heightPercent = properties["height"]?.let { parsePercent(it) },
            margin = properties["margin"]?.let { parseSingleDimension(it) },
            marginTop = properties["margin-top"]?.let { parseDimension(it) } ?: marginBox.top,
            marginRight = properties["margin-right"]?.let { parseDimension(it) } ?: marginBox.right,
            marginBottom = properties["margin-bottom"]?.let { parseDimension(it) } ?: marginBox.bottom,
            marginLeft = properties["margin-left"]?.let { parseDimension(it) } ?: marginBox.left,
            // margin:0 auto 是块级居中的写法，Android 上没有 auto 的概念，
            // 记一个标记让渲染层用 CenterHorizontally 代替
            marginAuto = marginRaw?.let { isAutoHorizontalMargin(it) } == true,
            padding = properties["padding"]?.let { parseSingleDimension(it) },
            paddingTop = properties["padding-top"]?.let { parseDimension(it) } ?: paddingBox.top,
            paddingRight = properties["padding-right"]?.let { parseDimension(it) } ?: paddingBox.right,
            paddingBottom = properties["padding-bottom"]?.let { parseDimension(it) } ?: paddingBox.bottom,
            paddingLeft = properties["padding-left"]?.let { parseDimension(it) } ?: paddingBox.left,
            borderRadius = radiusRaw?.let { parseDimension(it) },
            // border-radius:50% 换算不出 Dp（要看元素尺寸），单独标记成圆形
            borderRadiusIsCircle = radiusRaw?.let { it.contains("%") && (parsePercent(it) ?: 0f) >= 50f } == true,
            border = properties["border"],
            borderTop = properties["border-top"],
            borderRight = properties["border-right"],
            borderBottom = properties["border-bottom"],
            borderLeft = properties["border-left"],
            boxShadow = properties["box-shadow"],
            lineHeight = parsedLineHeight?.first,
            lineHeightMultiplier = parsedLineHeight?.second,
            fontFamily = properties["font-family"],
            backgroundGradient = properties["background"]?.let { parseGradient(it) } 
                ?: properties["background-image"]?.let { parseGradient(it) },
            display = properties["display"],
            flexDirection = properties["flex-direction"],
            alignItems = properties["align-items"],
            gap = properties["gap"]?.let { parseDimension(it) },
            flex = properties["flex"]?.toFloatOrNull() ?: flexParts?.first,
            flexGrow = properties["flex-grow"]?.toFloatOrNull() ?: flexParts?.first,
            flexShrink = properties["flex-shrink"]?.toFloatOrNull() ?: flexParts?.second,
            flexWrap = properties["flex-wrap"],
            justifyContent = properties["justify-content"],
            objectFit = properties["object-fit"],
            wordWrap = properties["word-wrap"],
            whiteSpace = properties["white-space"],
            borderCollapse = properties["border-collapse"],
            borderSpacing = properties["border-spacing"]?.let { parseSingleDimension(it) },
            listStyleType = listStyleType,
            transition = properties["transition"],
            outline = properties["outline"]
        )
    }

    private fun parseBackgroundColor(value: String): Color? {
        val normalized = value.trim().lowercase()
        if (normalized.isEmpty()) return null
        if (normalized.contains("gradient(")) return null
        if (normalized.contains("url(")) return null

        val token = normalized
            .split(Regex("\\s+"))
            .firstOrNull { part ->
                part.startsWith("#") ||
                    part.startsWith("rgb(") ||
                    part.startsWith("rgba(") ||
                    part.matches(Regex("^[a-z]+$"))
            }
            ?: return null

        return parseColor(token)
    }

    private fun parseBoxValues(value: String?): BoxValues {
        if (value.isNullOrBlank()) return BoxValues()
        val parts = value.trim().split(Regex("\\s+")).mapNotNull { parseDimension(it) }
        if (parts.isEmpty()) return BoxValues()
        return when (parts.size) {
            1 -> BoxValues(parts[0], parts[0], parts[0], parts[0])
            2 -> BoxValues(parts[0], parts[1], parts[0], parts[1])
            3 -> BoxValues(parts[0], parts[1], parts[2], parts[1])
            else -> BoxValues(parts[0], parts[1], parts[2], parts[3])
        }
    }

    private fun parseLineHeight(value: String): Pair<TextUnit?, Float?> {
        val trimmed = value.trim().lowercase()
        return when {
            trimmed == "normal" -> null to null
            trimmed.endsWith("px") || trimmed.endsWith("sp") || trimmed.endsWith("em") || trimmed.endsWith("pt") ->
                parseFontSize(trimmed) to null
            trimmed.endsWith("%") -> {
                val percent = trimmed.removeSuffix("%").toFloatOrNull()
                null to percent?.div(100f)
            }
            else -> {
                val number = trimmed.toFloatOrNull()
                if (number != null && number <= 10f) {
                    null to number
                } else {
                    parseFontSize(trimmed) to null
                }
            }
        }
    }

    private fun parseFlexShorthand(value: String): Pair<Float, Float>? {
        val normalized = value.trim().lowercase()
        if (normalized.isEmpty()) return null
        return when (normalized) {
            "none" -> 0f to 0f
            "auto", "initial" -> 1f to 1f
            else -> {
                val tokens = normalized.split(Regex("\\s+")).filter { it.isNotBlank() }
                val grow = tokens.getOrNull(0)?.toFloatOrNull()
                val shrink = tokens.getOrNull(1)?.toFloatOrNull()
                when {
                    grow != null && shrink != null -> grow to shrink
                    grow != null -> grow to 1f
                    else -> null
                }
            }
        }
    }

    private fun parseListStyleType(value: String): String? {
        val normalized = value.trim().lowercase()
        if (normalized.isEmpty()) return null
        return when {
            normalized.contains("none") -> "none"
            normalized.contains("disc") -> "disc"
            normalized.contains("circle") -> "circle"
            normalized.contains("square") -> "square"
            normalized.contains("decimal") -> "decimal"
            else -> null
        }
    }
    
    /**
     * 解析渐变背景
     */
    private fun parseGradient(value: String): List<Color>? {
        if (!value.contains("linear-gradient")) return null
        return try {
            val colors = mutableListOf<Color>()
            val regex = "#[0-9a-fA-F]{3,8}|rgba?\\([^)]+\\)|[a-z]+".toRegex()
            regex.findAll(value).forEach { match ->
                val color = parseColor(match.value)
                if (color != null) colors.add(color)
            }
            if (colors.size >= 2) colors else null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 解析颜色值
     */
    fun parseColor(colorString: String): Color? {
        return try {
            val color = colorString.trim().lowercase()
            when {
                color.startsWith("#") -> {
                    // 十六进制颜色
                    val hex = color.substring(1)
                    when (hex.length) {
                        3 -> {
                            // #RGB -> #RRGGBB
                            val r = hex[0].toString().repeat(2)
                            val g = hex[1].toString().repeat(2)
                            val b = hex[2].toString().repeat(2)
                            Color(android.graphics.Color.parseColor("#$r$g$b"))
                        }
                        6 -> {
                            // #RRGGBB
                            Color(android.graphics.Color.parseColor(color))
                        }
                        8 -> {
                            // #AARRGGBB
                            Color(android.graphics.Color.parseColor(color))
                        }
                        else -> null
                    }
                }
                color.startsWith("rgb(") && color.endsWith(")") -> {
                    // rgb(r, g, b)
                    val values = color.substring(4, color.length - 1)
                        .split(",")
                        .map { it.trim().toIntOrNull() ?: 0 }
                    if (values.size >= 3) {
                        Color(values[0], values[1], values[2])
                    } else null
                }
                color.startsWith("rgba(") && color.endsWith(")") -> {
                    // rgba(r, g, b, a)
                    val values = color.substring(5, color.length - 1).split(",")
                    if (values.size >= 4) {
                        val r = values[0].trim().toIntOrNull() ?: 0
                        val g = values[1].trim().toIntOrNull() ?: 0
                        val b = values[2].trim().toIntOrNull() ?: 0
                        val a = (values[3].trim().toFloatOrNull() ?: 1f) * 255
                        Color(r, g, b, a.toInt())
                    } else null
                }
                else -> {
                    // 命名颜色
                    parseNamedColor(color)
                }
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 解析命名颜色
     */
    private fun parseNamedColor(colorName: String): Color? {
        return when (colorName.lowercase()) {
            "red" -> Color.Red
            "green" -> Color.Green
            "blue" -> Color.Blue
            "black" -> Color.Black
            "white" -> Color.White
            "gray", "grey" -> Color.Gray
            "yellow" -> Color.Yellow
            "cyan" -> Color.Cyan
            "magenta" -> Color.Magenta
            "transparent" -> Color.Transparent
            else -> null
        }
    }
    
    /**
     * 解析字体大小
     */
    private fun parseFontSize(sizeString: String): TextUnit? {
        return try {
            val size = sizeString.trim().lowercase()
            when {
                size.endsWith("px") -> {
                    val value = size.substring(0, size.length - 2).toFloatOrNull()
                    value?.sp
                }
                size.endsWith("sp") -> {
                    val value = size.substring(0, size.length - 2).toFloatOrNull()
                    value?.sp
                }
                size.endsWith("em") -> {
                    val value = size.substring(0, size.length - 2).toFloatOrNull()
                    value?.let { (it * 16).sp } // 假设基础字体大小为 16sp
                }
                size == "small" -> 12.sp
                size == "medium" -> 16.sp
                size == "large" -> 20.sp
                size == "x-large" -> 24.sp
                size == "xx-large" -> 32.sp
                else -> {
                    // 尝试解析纯数字
                    size.toFloatOrNull()?.sp
                }
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 解析字体粗细
     */
    private fun parseFontWeight(weightString: String): FontWeight? {
        return when (weightString.trim().lowercase()) {
            "normal" -> FontWeight.Normal
            "bold" -> FontWeight.Bold
            "bolder" -> FontWeight.ExtraBold
            "lighter" -> FontWeight.Light
            "100" -> FontWeight.Thin
            "200" -> FontWeight.ExtraLight
            "300" -> FontWeight.Light
            "400" -> FontWeight.Normal
            "500" -> FontWeight.Medium
            "600" -> FontWeight.SemiBold
            "700" -> FontWeight.Bold
            "800" -> FontWeight.ExtraBold
            "900" -> FontWeight.Black
            else -> null
        }
    }
    
    /**
     * 解析字体样式
     */
    private fun parseFontStyle(styleString: String): FontStyle? {
        return when (styleString.trim().lowercase()) {
            "normal" -> FontStyle.Normal
            "italic" -> FontStyle.Italic
            "oblique" -> FontStyle.Italic // Compose 不支持 oblique，使用 italic 代替
            else -> null
        }
    }
    
    /**
     * 解析文本装饰
     */
    private fun parseTextDecoration(decorationString: String): TextDecoration? {
        return when (decorationString.trim().lowercase()) {
            "none" -> TextDecoration.None
            "underline" -> TextDecoration.Underline
            "line-through" -> TextDecoration.LineThrough
            else -> null
        }
    }
    
    /**
     * 解析文本对齐
     */
    private fun parseTextAlign(alignString: String): TextAlign? {
        return when (alignString.trim().lowercase()) {
            "left" -> TextAlign.Left
            "center" -> TextAlign.Center
            "right" -> TextAlign.Right
            "justify" -> TextAlign.Justify
            else -> null
        }
    }
    
    /**
     * 解析尺寸值（px、dp、sp、em等）
     */
    private fun parseSingleDimension(dimensionString: String): Dp? {
        if (dimensionString.trim().contains(" ")) return null
        return parseDimension(dimensionString)
    }

    /**
     * 解析百分比，返回 0..1 的小数。
     *
     * 和 [parseDimension] 分开：百分比在解析期无法换算成 Dp，必须交给渲染层
     * 结合父容器宽度处理。以前这里直接返回 null，导致 `width:100%`、
     * `border-radius:50%` 这些写法全部静默失效。
     */
    private fun parsePercent(dimensionString: String): Float? {
        val trimmed = dimensionString.trim()
        if (!trimmed.endsWith("%")) return null
        return trimmed.removeSuffix("%").toFloatOrNull()?.div(100f)?.coerceIn(0f, 1f)
    }

    /** margin 里出现 auto 就认为是「水平居中」 */
    private fun isAutoHorizontalMargin(value: String): Boolean {
        return value.split(Regex("\\s+")).any { it.equals("auto", ignoreCase = true) }
    }

    private fun parseDimension(dimensionString: String): Dp? {
        return try {
            val dimension = dimensionString.trim().lowercase()
            when {
                dimension.endsWith("px") -> {
                    val value = dimension.substring(0, dimension.length - 2).toFloatOrNull()
                    value?.dp
                }
                dimension.endsWith("dp") -> {
                    val value = dimension.substring(0, dimension.length - 2).toFloatOrNull()
                    value?.dp
                }
                dimension.endsWith("sp") -> {
                    val value = dimension.substring(0, dimension.length - 2).toFloatOrNull()
                    value?.dp // 转换为 dp，虽然不完全准确但可用
                }
                dimension.endsWith("em") -> {
                    val value = dimension.substring(0, dimension.length - 2).toFloatOrNull()
                    value?.let { (it * 16).dp } // 假设基础字体大小为 16dp
                }
                dimension == "auto" -> null
                else -> {
                    // 尝试解析纯数字，默认为 px
                    dimension.toFloatOrNull()?.dp
                }
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 将 CSS 样式转换为 Compose SpanStyle
     */
    fun cssToSpanStyle(cssStyle: CssStyle): SpanStyle {
        return SpanStyle(
            color = cssStyle.color ?: Color.Unspecified,
            fontSize = cssStyle.fontSize ?: TextUnit.Unspecified,
            fontWeight = cssStyle.fontWeight,
            fontStyle = cssStyle.fontStyle,
            textDecoration = cssStyle.textDecoration
        )
    }
}
