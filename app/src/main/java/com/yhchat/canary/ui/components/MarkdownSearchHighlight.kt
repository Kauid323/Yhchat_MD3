package com.yhchat.canary.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle

/** Add only paint styling: text, link annotations, metrics and line wrapping stay unchanged. */
internal fun highlightRenderedText(text: AnnotatedString, query: String, background: Color): AnnotatedString {
    if (query.isBlank()) return text
    val builder = AnnotatedString.Builder(text)
    var offset = 0
    while (offset < text.length) {
        val index = text.text.indexOf(query, offset, ignoreCase = true)
        if (index < 0) break
        builder.addStyle(SpanStyle(background = background), index, index + query.length)
        offset = index + query.length
    }
    return builder.toAnnotatedString()
}
