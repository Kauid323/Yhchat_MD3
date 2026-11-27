package com.yhchat.canary.ui.components.htmltext

/** Small CSS scanner shared by declarations, selectors and stylesheets. No browser/native dependency. */
internal fun stripCssComments(css: String): String {
    val out = StringBuilder(css.length)
    var quote: Char? = null
    var index = 0
    while (index < css.length) {
        val char = css[index]
        if (char == '\\' && index + 1 < css.length) {
            out.append(char).append(css[index + 1]); index += 2; continue
        }
        if (quote != null) {
            out.append(char)
            if (char == quote) quote = null
            index++; continue
        }
        if (char == '\'' || char == '"') {
            quote = char
        } else if (char == '/' && css.getOrNull(index + 1) == '*') {
            val end = css.indexOf("*/", index + 2)
            if (end < 0) break
            index = end + 2
            continue
        }
        out.append(char)
        index++
    }
    return out.toString()
}

/** Offsets of punctuation outside strings, escapes and nested ()/[]/{} groups. */
private fun cssTopLevelDelimiters(css: String, isDelimiter: (Char) -> Boolean): List<Int> {
    val offsets = mutableListOf<Int>()
    val brackets = ArrayDeque<Char>()
    var quote: Char? = null
    var index = 0
    while (index < css.length) {
        val char = css[index]
        if (char == '\\') { index += 2; continue }
        if (quote != null) {
            if (char == quote) quote = null
        } else if (char == '\'' || char == '"') {
            quote = char
        } else {
            if (brackets.isEmpty() && isDelimiter(char)) offsets += index
            when (char) {
                '(' -> brackets.addLast(')')
                '[' -> brackets.addLast(']')
                '{' -> brackets.addLast('}')
                ')', ']', '}' -> if (brackets.lastOrNull() == char) brackets.removeLast()
            }
        }
        index++
    }
    return offsets
}

internal fun splitCssTopLevel(css: String, delimiter: Char): List<String> {
    val result = mutableListOf<String>()
    var start = 0
    for (offset in cssTopLevelDelimiters(css) { it == delimiter }) {
        result += css.substring(start, offset)
        start = offset + 1
    }
    result += css.substring(start)
    return result
}

internal fun tokenizeCssSelector(selector: String): List<String> {
    val tokens = mutableListOf<String>()
    var start = 0
    for (offset in cssTopLevelDelimiters(selector) { it.isWhitespace() || it == '>' }) {
        selector.substring(start, offset).takeIf { it.isNotBlank() }?.let(tokens::add)
        if (selector[offset] == '>') tokens += ">"
        start = offset + 1
    }
    selector.substring(start).takeIf { it.isNotBlank() }?.let(tokens::add)
    return tokens
}

internal data class CssBlock(val selector: String, val declarations: String)

/** Read only top-level rules. Unsupported @media/@supports/keyframes stay isolated as a whole. */
internal fun readCssBlocks(stylesheet: String): List<CssBlock> {
    val css = stripCssComments(stylesheet)
    val result = mutableListOf<CssBlock>()
    var quote: Char? = null
    var parentheses = 0
    var brackets = 0
    var depth = 0
    var ruleStart = 0
    var bodyStart = -1
    var selector = ""
    var index = 0
    while (index < css.length) {
        val char = css[index]
        if (char == '\\') { index += 2; continue }
        if (quote != null) {
            if (char == quote) quote = null
        } else if (char == '\'' || char == '"') quote = char
        else when (char) {
            '(' -> parentheses++
            ')' -> parentheses = (parentheses - 1).coerceAtLeast(0)
            '[' -> brackets++
            ']' -> brackets = (brackets - 1).coerceAtLeast(0)
            ';' -> if (depth == 0 && parentheses == 0 && brackets == 0) ruleStart = index + 1
            '{' -> if (parentheses == 0 && brackets == 0) {
                if (depth == 0) {
                    selector = css.substring(ruleStart, index).trim()
                    bodyStart = index + 1
                }
                depth++
            }
            '}' -> if (parentheses == 0 && brackets == 0 && depth > 0) {
                depth--
                if (depth == 0) {
                    if (selector.isNotBlank() && !selector.startsWith('@')) {
                        result += CssBlock(selector, css.substring(bodyStart, index))
                    }
                    ruleStart = index + 1
                }
            }
        }
        index++
    }
    return result
}

internal fun readCssDeclarations(style: String): List<Pair<String, String>> =
    splitCssTopLevel(stripCssComments(style), ';').mapNotNull { declaration ->
        val colon = cssTopLevelDelimiters(declaration) { it == ':' }.firstOrNull() ?: return@mapNotNull null
        val name = declaration.substring(0, colon).trim().lowercase()
        val value = declaration.substring(colon + 1).trim()
        if (name.isEmpty() || value.isEmpty()) null else name to value
    }
