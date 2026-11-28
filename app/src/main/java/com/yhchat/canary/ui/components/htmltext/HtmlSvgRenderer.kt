package com.yhchat.canary.ui.components.htmltext

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import coil.size.Scale

internal data class HtmlSvgAsset(
    val markup: String,
    val width: Dp,
    val height: Dp,
    val description: String?
)

// HTML parsing folds names to lowercase; SVG's XML decoder needs these names restored.
private val svgTagNames = """
    altGlyph altGlyphDef altGlyphItem animateColor animateMotion animateTransform clipPath
    feBlend feColorMatrix feComponentTransfer feComposite feConvolveMatrix feDiffuseLighting
    feDisplacementMap feDistantLight feDropShadow feFlood feFuncA feFuncB feFuncG feFuncR
    feGaussianBlur feImage feMerge feMergeNode feMorphology feOffset fePointLight
    feSpecularLighting feSpotLight feTile feTurbulence foreignObject glyphRef linearGradient
    radialGradient textPath
""".trim().split(Regex("\\s+")).associateBy { it.lowercase() }

private val svgAttributeNames = """
    attributeName attributeType baseFrequency baseProfile calcMode clipPathUnits diffuseConstant
    edgeMode filterUnits filterRes gradientTransform gradientUnits kernelMatrix kernelUnitLength
    keyPoints keySplines keyTimes lengthAdjust limitingConeAngle markerHeight markerUnits markerWidth
    maskContentUnits maskUnits numOctaves pathLength patternContentUnits patternTransform patternUnits
    pointsAtX pointsAtY pointsAtZ preserveAlpha preserveAspectRatio primitiveUnits refX refY
    repeatCount repeatDur requiredExtensions requiredFeatures specularConstant specularExponent
    spreadMethod startOffset stdDeviation stitchTiles surfaceScale systemLanguage tableValues
    targetX targetY textLength viewBox viewTarget xChannelSelector yChannelSelector zoomAndPan
""".trim().split(Regex("\\s+")).associateBy { it.lowercase() }

private val svgImportantSuffix = Regex("\\s*!important\\s*$", RegexOption.IGNORE_CASE)
private val svgXmlName = Regex("[A-Za-z_][A-Za-z0-9_.:-]*")
private val svgViewBoxSeparator = Regex("[,\\s]+")
private const val MAX_SVG_DIMENSION = 4096f
private const val MAX_SVG_MARKUP_LENGTH = 256 * 1024
private const val MAX_SVG_RASTER_DIMENSION = 2048

/** Collapse the already specificity-ordered declarations, including unsupported SVG paint properties. */
private fun HtmlNode.Element.svgDeclarations(): Map<String, String> {
    val declarations = linkedMapOf<String, String>()
    val important = mutableSetOf<String>()
    readCssDeclarations(attrs[SVG_STYLES_ATTR] ?: attrs["style"].orEmpty()).forEach { (property, rawValue) ->
        val hasPriority = svgImportantSuffix.containsMatchIn(rawValue)
        if (hasPriority || property !in important) {
            declarations[property] = rawValue.replace(svgImportantSuffix, "").trim()
            if (hasPriority) important += property
        }
    }
    return declarations
}

private fun StringBuilder.appendSvgXml(value: String) {
    value.forEach { char ->
        when (char) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> if (char >= ' ' || char == '\n' || char == '\r' || char == '\t') append(char)
        }
    }
}

private fun Color.svgColor(): String {
    val argb = toArgb()
    val red = (argb ushr 16) and 0xff
    val green = (argb ushr 8) and 0xff
    val blue = argb and 0xff
    val alpha = (argb ushr 24) / 255f
    return "rgba($red,$green,$blue,$alpha)"
}

private fun serializeHtmlSvg(
    root: HtmlNode.Element,
    textColor: Color?,
    width: Dp,
    height: Dp,
    fontSize: Dp
): String = buildString {
    fun appendNode(node: HtmlNode) {
        if (length > MAX_SVG_MARKUP_LENGTH) return
        when (node) {
            is HtmlNode.Text -> appendSvgXml(node.text)
            is HtmlNode.Element -> {
                // CSS has already been matched in the HTML tree; scripts and HTML subtrees have no SVG paint.
                if (node.tag in setOf("script", "style", "foreignobject")) return
                val tag = svgTagNames[node.tag] ?: node.tag
                if (!svgXmlName.matches(tag)) return
                val attributes = node.attrs.filterKeys {
                    !it.startsWith("__") && it != "style" && svgXmlName.matches(it)
                }.toMutableMap()
                val declarations = node.svgDeclarations().toMutableMap()
                if (node === root) {
                    attributes["xmlns"] = "http://www.w3.org/2000/svg"
                    attributes["xmlns:xlink"] = "http://www.w3.org/1999/xlink"
                    // Compose owns the outer viewport; use its resolved dimensions for the decoder too.
                    attributes["width"] = width.value.toString()
                    attributes["height"] = height.value.toString()
                    declarations.remove("width")
                    declarations.remove("height")
                    if ("font-size" !in declarations && "font-size" !in attributes) {
                        attributes["font-size"] = fontSize.value.toString()
                    }
                    if (textColor != null && textColor != Color.Unspecified) {
                        // The standalone SVG otherwise loses the HTML parent's color for currentColor.
                        if (declarations["color"]?.lowercase() in setOf("inherit", "currentcolor", "unset")) {
                            declarations["color"] = textColor.svgColor()
                        } else if ("color" !in declarations &&
                            ("color" !in attributes || attributes["color"]?.lowercase() in setOf("inherit", "currentcolor", "unset"))
                        ) {
                            attributes["color"] = textColor.svgColor()
                        }
                    }
                }
                if (declarations.isNotEmpty()) {
                    attributes["style"] = declarations.entries.joinToString(";") { (key, value) -> "$key:$value" }
                }
                append('<').append(tag)
                attributes.forEach { (name, value) ->
                    append(' ').append(svgAttributeNames[name] ?: name).append("=\"")
                    appendSvgXml(value)
                    append('"')
                }
                append('>')
                node.children.forEach(::appendNode)
                append("</").append(tag).append('>')
            }
        }
    }
    appendNode(root)
}

internal fun buildHtmlSvgAsset(
    node: HtmlNode.Element,
    textColor: Color?,
    fontSize: Dp,
    containerWidth: Dp? = null,
    containerHeight: Dp? = null
): HtmlSvgAsset? {
    val declarations = node.svgDeclarations()
    val viewBox = node.attrs["viewbox"]?.trim()?.split(svgViewBoxSeparator)
        ?.map { it.toFloatOrNull() ?: Float.NaN }
        ?.takeIf { it.size == 4 && it.all { value -> value.isFinite() } && it[2] > 0f && it[3] > 0f }
    val ratio = viewBox?.let { it[2] / it[3] }?.takeIf { it.isFinite() && it > 0f } ?: 1f
    fun dimension(raw: String?, container: Dp?): Dp? {
        val value = raw?.trim()?.lowercase() ?: return null
        val number = when {
            value.endsWith("rem") -> value.dropLast(3).toFloatOrNull()?.times(16f)
            value.endsWith("em") -> value.dropLast(2).toFloatOrNull()?.times(fontSize.value)
            value.endsWith("%") -> value.dropLast(1).toFloatOrNull()?.let { percent -> container?.value?.times(percent / 100f) }
            value.endsWith("px") || value.endsWith("dp") -> value.dropLast(2).toFloatOrNull()
            value.endsWith("pt") -> value.dropLast(2).toFloatOrNull()?.times(4f / 3f)
            else -> value.toFloatOrNull()
        }
        return number?.takeIf { it.isFinite() && it >= 0f }?.coerceAtMost(MAX_SVG_DIMENSION)?.dp
    }
    val width = if ("width" in declarations) dimension(declarations["width"], containerWidth) else
        node.style.width ?: dimension(node.attrs["width"], containerWidth)
    val height = if ("height" in declarations) dimension(declarations["height"], containerHeight) else
        node.style.height ?: dimension(node.attrs["height"], containerHeight)
    val resolvedWidth = (width ?: height?.let { it * ratio } ?: viewBox?.get(2)?.dp ?: fontSize)
        .value.takeIf { it.isFinite() && it > 0f }?.coerceAtMost(MAX_SVG_DIMENSION)?.dp ?: return null
    val resolvedHeight = (height ?: (resolvedWidth / ratio))
        .value.takeIf { it.isFinite() && it > 0f }?.coerceAtMost(MAX_SVG_DIMENSION)?.dp ?: return null
    val markup = serializeHtmlSvg(node, textColor, resolvedWidth, resolvedHeight, fontSize)
    if (markup.length > MAX_SVG_MARKUP_LENGTH) return null
    val description = node.attrs["aria-label"] ?: node.children.filterIsInstance<HtmlNode.Element>()
        .firstOrNull { it.tag == "title" }?.children?.filterIsInstance<HtmlNode.Text>()?.joinToString("") { it.text }
    return HtmlSvgAsset(markup, resolvedWidth, resolvedHeight, description)
}

@Composable
internal fun HtmlSvgImage(asset: HtmlSvgAsset, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val request = remember(context, asset.markup, density) {
        ImageRequest.Builder(context)
            .data(asset.markup.toByteArray(Charsets.UTF_8))
            .size(
                with(density) { asset.width.toPx().toInt().coerceIn(1, MAX_SVG_RASTER_DIMENSION) },
                with(density) { asset.height.toPx().toInt().coerceIn(1, MAX_SVG_RASTER_DIMENSION) }
            )
            .scale(Scale.FIT)
            .decoderFactory(SvgDecoder.Factory(useViewBoundsAsIntrinsicSize = false))
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = asset.description,
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}

@Composable
internal fun HtmlSvgNode(node: HtmlNode.Element, inheritedText: CssStyle) {
    val text = node.style.mergeText(inheritedText)
    val density = LocalDensity.current
    val fontSize = with(density) { (text.fontSize ?: 14.sp).toDp() }
    BoxWithConstraints {
        val asset = buildHtmlSvgAsset(
            node, text.color, fontSize,
            maxWidth.takeIf { it > 0.dp && it < Dp.Infinity },
            maxHeight.takeIf { it > 0.dp && it < Dp.Infinity }
        ) ?: return@BoxWithConstraints
        HtmlSvgImage(
            asset,
            Modifier.htmlBoxModel(node.style.copy(
                width = asset.width, height = asset.height, widthPercent = null, heightPercent = null
            ))
        )
    }
}
