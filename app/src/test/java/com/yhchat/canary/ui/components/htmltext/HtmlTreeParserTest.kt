package com.yhchat.canary.ui.components.htmltext

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test

class HtmlTreeParserTest {
    private fun HtmlNode.Element.elements(): List<HtmlNode.Element> =
        children.filterIsInstance<HtmlNode.Element>().flatMap { listOf(it) + it.elements() }

    @Test fun explicitBreakIsNotSourceWhitespace() {
        val root = parseHtmlTree("<p>first<br>second</p>")
        val paragraph = root.elements().first { it.tag == "p" }
        assertEquals(listOf("text", "br", "text"), paragraph.children.map {
            if (it is HtmlNode.Element) it.tag else "text"
        })
        assertEquals("hello world", normalizeHtmlText("hello\n  world", null))
        assertEquals("  hello\nworld", normalizeHtmlText("  hello\nworld", "pre"))
        assertEquals("hello\n world", normalizeHtmlText("hello\n  world", "pre-line"))
    }

    @Test fun idSpecificityBeatsLaterClassAndTagRules() {
        val tree = parseHtmlTree("""
            <style>#card { padding: 12px } .card { padding: 8px } div { padding: 4px }</style>
            <div id="card" class="card">text</div>
        """.trimIndent())
        assertEquals(12.dp, tree.elements().first { it.attrs["id"] == "card" }.style.padding)
    }

    @Test fun directChildDoesNotMatchGrandchild() {
        val tree = parseHtmlTree("""
            <style>.outer > span { padding: 9px }</style>
            <div class="outer"><span id="direct">a</span><div><span id="nested">b</span></div></div>
        """.trimIndent())
        assertEquals(9.dp, tree.elements().first { it.attrs["id"] == "direct" }.style.padding)
        assertNull(tree.elements().first { it.attrs["id"] == "nested" }.style.padding)
    }

    @Test fun hoverDoesNotApplyUnconditionally() {
        val tree = parseHtmlTree("<style>span:hover { display:none }</style><span>visible</span>")
        assertNull(tree.elements().first { it.tag == "span" }.style.display)
    }

    @Test fun importantDeclarationBeatsLaterInlineNormalDeclaration() {
        val tree = parseHtmlTree("""
            <style>.card { padding: 12px !important }</style>
            <div class="card" style="padding:2px">text</div>
        """.trimIndent())
        assertEquals(12.dp, tree.elements().first { it.tag == "div" }.style.padding)
    }

    @Test fun hiddenContentIsDetectedAtAnyDepth() {
        val tree = parseHtmlTree("<div><span hidden>x</span><p style='display:none'>y</p><script>z</script></div>")
        val hidden = tree.elements().filter { it.tag in setOf("span", "p", "script") }
        assertEquals(3, hidden.size)
        assertTrue(hidden.all { it.isHiddenHtmlNode() })
        assertFalse(HtmlNode.Element("html").isHiddenHtmlNode())
        assertFalse(HtmlNode.Element("body").isHiddenHtmlNode())
    }

    @Test fun flexWhitespaceDoesNotCreateExtraItems() {
        val image = HtmlNode.Element("img")
        val label = HtmlNode.Element("span")
        val items = htmlFlexItems(listOf(HtmlNode.Text("\n  "), image, HtmlNode.Text("\n "), label), null)
        assertEquals(listOf(image, label), items)
    }

    @Test fun flexAdjacentTextFormsOneItemAndKeepsOrder() {
        val image = HtmlNode.Element("img")
        val hidden = HtmlNode.Element("span", style = CssStyle(display = "none"))
        val items = htmlFlexItems(listOf(HtmlNode.Text("before"), hidden, HtmlNode.Text(" image"), image, HtmlNode.Text("after")), null)
        assertEquals(listOf(HtmlNode.Text("before image"), image, HtmlNode.Text("after")), items)
    }

    @Test fun authoredLineHeightOverridesDefaultMultiplier() {
        val style = CssStyle(lineHeightMultiplier = 1.5f).mergeWith(CssStyle(lineHeight = 24.sp))
        assertNull(style.lineHeightMultiplier)
        assertEquals(24.sp, style.toComposeTextStyle().lineHeight)
        val child = CssStyle(lineHeightMultiplier = 2f, fontSize = 12.sp).mergeText(style)
        assertNull(child.lineHeight)
        assertEquals(24.sp, child.toComposeTextStyle().lineHeight)
    }

    @Test fun strongKeepsAuthoredSizeAndColor() {
        val node = HtmlNode.Element("strong", style = CssStyle(color = Color.Red, fontSize = 20.sp), children = mutableListOf(HtmlNode.Text("bold")))
        val text = buildHtmlInlineText(listOf(node), CssStyle(color = Color.Black), Color.Blue, Color.Black, false)
        assertEquals("bold", text.text)
        assertTrue(text.spanStyles.any { it.item.color == Color.Red && it.item.fontSize == 20.sp && it.item.fontWeight == androidx.compose.ui.text.font.FontWeight.Bold })
    }

    @Test fun linksUseDefaultColorUnlessExplicitlyInherited() {
        val normal = HtmlNode.Element("a", attrs = mapOf("href" to "https://example.com"), children = mutableListOf(HtmlNode.Text("link")))
        val text = buildHtmlInlineText(listOf(normal), CssStyle(color = Color.Black), Color.Blue, Color.Black, false)
        assertEquals("https://example.com", text.getStringAnnotations("URL", 0, 4).single().item)
        assertTrue(text.spanStyles.any { it.item.color == Color.Blue })
        val inherited = buildHtmlInlineText(listOf(normal.copy(style = CssStyle(colorInherit = true))), CssStyle(color = Color.Black), Color.Blue, Color.Black, false)
        assertTrue(inherited.spanStyles.any { it.item.color == Color.Black })
    }

    @Test fun voidElementsDoNotCaptureFollowingContent() {
        val tree = parseHtmlTree("<div><input><hr><span>after</span></div>")
        val div = tree.elements().first { it.tag == "div" }
        assertEquals(listOf("input", "hr", "span"), div.children.filterIsInstance<HtmlNode.Element>().map { it.tag })
    }
}
