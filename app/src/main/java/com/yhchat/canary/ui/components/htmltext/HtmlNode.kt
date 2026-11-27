package com.yhchat.canary.ui.components.htmltext

sealed class HtmlNode {
    data class Element(
        val tag: String,
        val style: CssStyle = CssStyle(),
        val attrs: Map<String, String> = emptyMap(),
        val children: MutableList<HtmlNode> = mutableListOf()
    ) : HtmlNode()

    data class Text(
        val text: String
    ) : HtmlNode()
}

internal fun isInlineTag(tag: String): Boolean = when (tag) {
    "a", "abbr", "b", "code", "em", "i", "mark", "small", "span", "strong", "sub", "sup", "u", "s" -> true
    else -> false
}

internal fun isBlockTag(tag: String): Boolean = when (tag) {
    "html", "body", "main", "section", "article", "header", "footer", "nav", "aside", "figure", "figcaption", "hr",
    "blockquote", "caption", "details", "div", "h1", "h2", "h3", "h4", "h5", "h6",
    "li", "ol", "p", "pre", "summary", "table", "tbody", "td", "tfoot", "th", "thead", "tr", "ul" -> true
    else -> false
}
