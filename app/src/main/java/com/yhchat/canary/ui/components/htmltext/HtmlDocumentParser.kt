package com.yhchat.canary.ui.components.htmltext

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.parser.Parser

/**
 * HTML5 tree construction, before CSS matching or conversion to Compose nodes.
 *
 * Like Blink's HTMLTreeBuilder, the HTML parser owns insertion modes, implied end
 * tags, table foster parenting, formatting-element reconstruction and foreign
 * content. Do not reconstruct a second tree from tokenizer open/close callbacks:
 * those callbacks cannot represent reparented or reconstructed elements.
 */
internal data class ParsedHtmlDocument(
    val document: Document,
    // Document order, excluding the inert contents of HTML template elements.
    val activeElements: List<Element>
)

internal fun parseNativeHtmlDocument(html: String): ParsedHtmlDocument {
    require(html.length <= MAX_HTML_INPUT_LENGTH) { "HTML input limit exceeded" }
    val normalized = html.replace("\r\n", "\n").replace('\r', '\n')
    // Parse strings only: this does not fetch URLs or execute scripts.
    val document = Parser.htmlParser().parseInput(normalized, "")
    val elements = mutableListOf<Element>()
    data class Pending(val node: Node, val depth: Int, val active: Boolean)
    val pending = ArrayDeque<Pending>()
    pending.addLast(Pending(document, 0, true))
    var count = 0
    while (pending.isNotEmpty()) {
        val (node, depth, active) = pending.removeLast()
        // Validate before recursive conversion / CSS matching, including inert nodes.
        require(++count <= MAX_HTML_NODE_COUNT && depth <= MAX_HTML_NESTING_DEPTH) {
            "HTML tree limit exceeded"
        }
        val element = node as? Element
        if (active && element != null && node !== document) elements += element
        val childrenActive = active && !(element?.normalName() == "template" &&
            element.tag().namespace() == Parser.NamespaceHtml)
        for (index in node.childNodeSize() - 1 downTo 0) {
            pending.addLast(Pending(node.childNode(index), depth + 1, childrenActive))
        }
    }
    return ParsedHtmlDocument(document, elements)
}

internal fun Element.htmlAttributes(): Map<String, String> = attributes().associate {
    it.key.lowercase() to it.value
}.filterKeys { !it.startsWith("__") }

internal fun isStylesheetLink(attrs: Map<String, String>): Boolean {
    if ("disabled" in attrs) return false
    val rel = attrs["rel"].orEmpty().split(Regex("[\\t\\n\\u000C\\r ]+"))
    if (rel.none { it.equals("stylesheet", ignoreCase = true) } ||
        rel.any { it.equals("alternate", ignoreCase = true) }) return false
    val type = attrs["type"]?.trim()?.takeIf { it.isNotEmpty() }
    return type == null || type.equals("text/css", ignoreCase = true) ||
        type.startsWith("text/css;", ignoreCase = true)
}
