package com.yhchat.canary.ui.components.htmltext

import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test

class CssSyntaxTest {
    @Test fun delimitersInsideStringsAndUrlsStayInValue() {
        val declarations = readCssDeclarations("font-family:'a;b:c'; background:url(data:image/svg+xml;base64,abc); padding:12px")
        assertEquals(3, declarations.size)
        assertEquals("'a;b:c'", declarations[0].second)
        assertEquals("url(data:image/svg+xml;base64,abc)", declarations[1].second)
        assertEquals("padding" to "12px", declarations[2])
    }

    @Test fun commentsInsideQuotedValuesAreLiteral() {
        assertEquals("font-family:'/*literal*/';padding:8px", stripCssComments("font-family:'/*literal*/';/*comment*/padding:8px"))
    }

    @Test fun conditionalRulesDoNotLeakIntoUnconditionalRules() {
        val blocks = readCssBlocks("@media print { p { display:none } } @supports (display:grid) { div { padding:3px } } p { padding:8px }")
        assertEquals(1, blocks.size)
        assertEquals("p", blocks.single().selector)
        assertEquals("padding:8px", blocks.single().declarations.trim())
    }

    @Test fun bracesInsideValuesDoNotEndRule() {
        val blocks = readCssBlocks("p { content:'}'; padding:8px } span { padding:4px }")
        assertEquals(listOf("p", "span"), blocks.map { it.selector })
        assertTrue(blocks.first().declarations.contains("padding:8px"))
    }

    @Test fun selectorsPreserveAttributeWhitespaceAndCommas() {
        val selector = "[data-label='a,  b > c'] > span"
        assertEquals(listOf(selector, "p"), splitCssTopLevel("$selector,p", ','))
        assertEquals(listOf("[data-label='a,  b > c']", ">", "span"), tokenizeCssSelector(selector))
        val tree = parseHtmlTree("<style>$selector { padding:9px }</style><div data-label='a,  b > c'><span>x</span></div>")
        assertEquals(9.dp, descendants(tree).first { it.tag == "span" }.style.padding)
    }

    @Test fun emptyAttributeValueIsNotPresenceSelector() {
        val tree = parseHtmlTree("<style>[data-x=''] { padding:7px }</style><p data-x=''>a</p><p data-x='value'>b</p>")
        val nodes = descendants(tree).filter { it.tag == "p" }
        assertEquals(7.dp, nodes[0].style.padding)
        assertNull(nodes[1].style.padding)
    }

    @Test fun ancestorMatcherTriesFartherValidCandidate() {
        val tree = parseHtmlTree("<style>.a > .b span { padding:6px }</style><div class='a'><div class='b'><div><div class='b'><span>x</span></div></div></div></div>")
        assertEquals(6.dp, descendants(tree).first { it.tag == "span" }.style.padding)
    }

    private fun descendants(node: HtmlNode.Element): List<HtmlNode.Element> =
        node.children.filterIsInstance<HtmlNode.Element>().flatMap { listOf(it) + descendants(it) }
}
