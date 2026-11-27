package com.yhchat.canary.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.yhchat.canary.data.di.RepositoryFactory
import com.yhchat.canary.data.model.ChatMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

internal data class GalleryPicture(val id: String, val url: String, val seq: Long)

internal fun ChatMessage.galleryPicture(): GalleryPicture? {
    if (msgDeleteTime != null || contentType !in setOf(2, 7)) return null
    val url = content.imageUrl?.takeIf { it.isNotBlank() }
        ?: content.stickerUrl?.takeIf { it.isNotBlank() }?.let {
            if (it.startsWith("http://") || it.startsWith("https://")) it else "https://chat-img.jwznb.com/$it"
        } ?: return null
    val seq = msgSeq ?: 0L
    val id = msgId.takeIf { it.isNotBlank() }?.let { "msg:$it" } ?: "seq:$seq:$url"
    return GalleryPicture(id, url, seq)
}

/** Independent viewer pagination: does not prepend messages to the visible chat list. */
@Composable
fun ChatImageViewer(
    chatId: String,
    chatType: Int,
    initialMessages: List<ChatMessage>,
    initialMessageId: String,
    fallbackUrl: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current.applicationContext
    val repository = remember(context) { RepositoryFactory.getMessageRepository(context) }
    val scope = rememberCoroutineScope()
    val seeds = remember(chatId, chatType, initialMessageId) {
        val available = initialMessages.mapNotNull { it.galleryPicture() }.distinctBy { it.id }
            .sortedWith(compareBy<GalleryPicture> { it.seq }.thenBy { it.id })
        if (available.any { it.id == "msg:$initialMessageId" || it.url == fallbackUrl }) available
        else emptyList()
    }
    val initialIndex = remember(seeds, initialMessageId, fallbackUrl) {
        seeds.indexOfFirst { it.id == "msg:$initialMessageId" }.takeIf { it >= 0 }
            ?: seeds.indexOfFirst { it.url == fallbackUrl }.coerceAtLeast(0)
    }
    var pictures by remember(seeds) { mutableStateOf(seeds) }
    var olderCursor by remember(seeds) { mutableStateOf(seeds.map { it.seq }.filter { it > 0 }.minOrNull()) }
    var newerCursor by remember(seeds) { mutableStateOf(seeds.map { it.seq }.filter { it > 0 }.maxOrNull()) }
    var loadingOlder by remember(seeds) { mutableStateOf(false) }
    var loadingNewer by remember(seeds) { mutableStateOf(false) }
    var endOlder by remember(seeds) { mutableStateOf(olderCursor == null) }
    var endNewer by remember(seeds) { mutableStateOf(newerCursor == null) }
    var olderError by remember(seeds) { mutableStateOf<String?>(null) }
    var newerError by remember(seeds) { mutableStateOf<String?>(null) }
    var closing by remember { mutableStateOf(false) }

    fun load(older: Boolean, retry: Boolean = false) {
        if (closing) return
        if (older && (loadingOlder || endOlder || (!retry && olderError != null))) return
        if (!older && (loadingNewer || endNewer || (!retry && newerError != null))) return
        val reference = (if (older) olderCursor else newerCursor) ?: return
        if (older) { loadingOlder = true; olderError = null }
        else { loadingNewer = true; newerError = null }
        scope.launch {
            try {
                var cursor = reference
                // Deleted or duplicate-only pages can still advance the cursor. Scan a bounded
                // number of pages, then offer an explicit retry rather than loop indefinitely.
                var found = false
                for (attempt in 0 until 5) {
                    val messages = repository.getPictureMessages(chatId, chatType, cursor, older).getOrThrow()
                    ensureActive()
                    val beyond = messages.filter { message ->
                        val seq = message.msgSeq ?: 0L
                        seq > 0 && (if (older) seq < cursor else seq > cursor)
                    }
                    if (beyond.isEmpty()) {
                        if (older) endOlder = true else endNewer = true
                        found = true
                        break
                    }
                    cursor = if (older) beyond.minOf { it.msgSeq!! } else beyond.maxOf { it.msgSeq!! }
                    if (older) olderCursor = cursor else newerCursor = cursor
                    val known = pictures.mapTo(hashSetOf()) { it.id }
                    val added = beyond.mapNotNull { it.galleryPicture() }.filter { it.id !in known }.distinctBy { it.id }
                    if (added.isNotEmpty()) {
                        pictures = (pictures + added).distinctBy { it.id }
                            .sortedWith(compareBy<GalleryPicture> { it.seq }.thenBy { it.id })
                        found = true
                        break
                    }
                }
                if (!found) error("暂未找到可显示的图片，点击继续加载")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val message = error.message?.takeIf { it.isNotBlank() } ?: "加载更多图片失败"
                if (older) olderError = message else newerError = message
            } finally {
                if (older) loadingOlder = false else loadingNewer = false
            }
        }
    }

    if (pictures.isEmpty()) {
        ImageViewer(fallbackUrl, onDismiss)
        return
    }
    // Stable message IDs retain the viewed picture when older pictures are inserted at the front.
    ImageViewer(
        imageUrls = pictures.map { it.url },
        initialIndex = initialIndex,
        imageKeys = pictures.map { it.id },
        onPageSettled = { index ->
            if (index == 0) load(older = true)
            if (index == pictures.lastIndex) load(older = false)
        },
        isLoadingMore = loadingOlder || loadingNewer,
        loadMoreError = listOfNotNull(olderError, newerError).distinct().joinToString("\n").takeIf { it.isNotBlank() },
        onRetryLoadMore = {
            if (olderError != null) load(older = true, retry = true)
            if (newerError != null) load(older = false, retry = true)
        },
        onDismiss = { closing = true; onDismiss() }
    )
}
