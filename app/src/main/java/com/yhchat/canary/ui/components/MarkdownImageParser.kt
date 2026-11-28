package com.yhchat.canary.ui.components

import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import com.mikepenz.markdown.utils.getUnescapedTextInNode

internal data class MarkdownImageRange(
    val start: Int, val end: Int, val url: String, val alt: String?, val link: String?
)

/** Let the Markdown grammar resolve balanced parentheses, escapes and code spans. */
internal fun markdownImageRanges(content: String): List<MarkdownImageRange> {
    val root = MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(content)
    val images = mutableListOf<MarkdownImageRange>()
    fun descendant(node: ASTNode, type: org.intellij.markdown.IElementType): ASTNode? {
        if (node.type == type) return node
        return node.children.firstNotNullOfOrNull { descendant(it, type) }
    }
    fun destination(node: ASTNode): String? = (node.findChildOfType(MarkdownElementTypes.LINK_DESTINATION)
        ?: if (node.type == MarkdownElementTypes.IMAGE) descendant(node, MarkdownElementTypes.LINK_DESTINATION) else null)
        ?.getUnescapedTextInNode(content)?.trim()?.removeSurrounding("<", ">")?.takeIf { it.isNotBlank() }
    fun visit(node: ASTNode) {
        if (node.type in setOf(MarkdownElementTypes.CODE_SPAN, MarkdownElementTypes.CODE_BLOCK, MarkdownElementTypes.CODE_FENCE)) return
        if (node.type == MarkdownElementTypes.IMAGE) {
            val url = destination(node) ?: return
            val alt = node.findChildOfType(MarkdownElementTypes.LINK_TEXT)?.getUnescapedTextInNode(content)
                ?.removeSurrounding("[", "]")?.takeIf { it.isNotBlank() }
            val parentLink = generateSequence(node.parent) { it.parent }
                .firstOrNull { it.type == MarkdownElementTypes.INLINE_LINK }
            // Only replace a link whose complete label is this image. Mixed text/image
            // labels stay with the Markdown renderer to preserve their original structure.
            val link = parentLink?.takeIf {
                node.startOffset == it.startOffset + 1 && content.getOrNull(node.endOffset) == ']'
            }
            if (parentLink != null && link == null) return
            images += MarkdownImageRange(
                link?.startOffset ?: node.startOffset,
                link?.endOffset ?: node.endOffset,
                url, alt, link?.let(::destination)
            )
            return
        }
        node.children.forEach(::visit)
    }
    visit(root)
    return images.sortedBy { it.start }
}
