package com.yhchat.canary.ui.components

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext

/** On-screen thumbnail anchors, removed with their composition. No bitmap or Activity retained. */
internal object ViewerThumbnails {
    private data class Entry(val url: String, val coordinates: LayoutCoordinates, val window: Any?)
    private val entries = linkedMapOf<Any, Entry>()
    fun place(key: Any, url: String, coordinates: LayoutCoordinates, window: Any?) { entries[key] = Entry(url, coordinates, window) }
    fun remove(key: Any) { entries.remove(key) }
    fun bounds(url: String, window: Any?): Rect? = entries.values.toList().asReversed().firstNotNullOfOrNull { (candidate, coordinates, owner) ->
        if (candidate != url || owner != window || !coordinates.isAttached) null
        else runCatching { coordinates.boundsInWindow().takeIf { it.width > 1f && it.height > 1f } }.getOrNull()
    }
}

fun Modifier.imageViewerThumbnail(url: String): Modifier = composed {
    // Offscreen message-image exports must never replace real on-screen anchors.
    if (LocalContext.current is ImageUtils.ImageExportContext) return@composed this
    val view = androidx.compose.ui.platform.LocalView.current
    val key = remember(url) { Any() }
    DisposableEffect(key) { onDispose { ViewerThumbnails.remove(key) } }
    this.onGloballyPositioned { ViewerThumbnails.place(key, url, it, view.windowToken) }
}
