package com.yhchat.canary.ui.components

internal data class MarkdownDetailsBlock(
    val summary: String,
    val content: String,
    val prefix: String?,
    val suffix: String?,
    val nextIndex: Int
)

private val detailsTag = Regex("</?details\\b[^>]*>", RegexOption.IGNORE_CASE)
private val summaryAtStart = Regex("^\\s*<summary\\b[^>]*>(.*?)</summary\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

/** Match tags rather than line boundaries: summary/body/closing tag may share a line. */
internal fun extractDetailsBlock(lines: List<String>, startIndex: Int): MarkdownDetailsBlock? {
    val first = lines.getOrNull(startIndex) ?: return null
    val opening = detailsTag.find(first) ?: return null
    // Block syntax only, so inline code mentioning <details> remains ordinary Markdown.
    if (opening.value.startsWith("</") || first.substring(0, opening.range.first).isNotBlank()) return null
    val block = StringBuilder()
    var depth = 0
    var inFence = false
    var fenceChar: Char? = null
    for (index in startIndex until lines.size) {
        val line = lines[index]
        if (index > startIndex) block.append('\n')
        val lineOffset = block.length
        block.append(line)
        val trimmed = line.trimStart()
        if (index > startIndex && (trimmed.startsWith("```") || trimmed.startsWith("~~~"))) {
            if (!inFence) { inFence = true; fenceChar = trimmed.first() }
            else if (trimmed.first() == fenceChar) { inFence = false; fenceChar = null }
            continue
        }
        if (inFence) continue
        for (tag in detailsTag.findAll(line)) {
            depth += if (tag.value.startsWith("</")) -1 else 1
            if (depth == 0) {
                val innerStart = opening.range.last + 1
                val innerEnd = lineOffset + tag.range.first
                if (innerEnd < innerStart) return null
                val inner = block.substring(innerStart, innerEnd)
                val summary = summaryAtStart.find(inner)
                val body = if (summary == null) inner else inner.substring(summary.range.last + 1)
                return MarkdownDetailsBlock(
                    summary = summary?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() } ?: "详情",
                    content = body.trim('\n', '\r', ' '),
                    prefix = first.substring(0, opening.range.first).takeIf { it.isNotBlank() },
                    suffix = line.substring(tag.range.last + 1).takeIf { it.isNotBlank() },
                    nextIndex = index + 1
                )
            }
        }
    }
    return null
}
