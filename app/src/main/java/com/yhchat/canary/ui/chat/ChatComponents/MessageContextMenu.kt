package com.yhchat.canary.ui.chat.ChatComponents

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.yhchat.canary.data.model.ChatMessage
import com.yhchat.canary.service.AudioCacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** 气泡锚定的消息下拉菜单；子菜单在同一浮层内展开，返回时先收起子菜单。 */
@Composable
fun MessageContextMenu(
    message: ChatMessage,
    showRecall: Boolean,
    onDismiss: () -> Unit,
    onCopyAll: () -> Unit,
    onFreeCopy: () -> Unit,
    onQuote: () -> Unit,
    onForward: () -> Unit,
    onRecall: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onAddExpression: (() -> Unit)? = null,
    onBlockUser: (() -> Unit)? = null,
    onSaveAudio: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onSpeechToText: (() -> Unit)? = null,
    onPlusOne: (() -> Unit)? = null,
    onFavorite: (() -> Unit)? = null,
    onMultiSelect: (() -> Unit)? = null,
    onOpenInInternalBrowser: (() -> Unit)? = null,
    onExternalShare: (() -> Unit)? = null,
    expanded: Boolean = true
) {
    var openedSection by remember(message.msgId, expanded) { mutableStateOf<String?>(null) }
    val primary = MaterialTheme.colorScheme.primary
    val error = MaterialTheme.colorScheme.error
    val items = buildList {
        add(MessageMenuEntry("引用", Icons.Default.FormatQuote, onQuote))
        add(MessageMenuEntry("转发", Icons.AutoMirrored.Filled.Send, onForward))
        add(
            MessageMenuEntry(
                label = "复制与分享",
                icon = Icons.Default.ContentCopy,
                children = buildList {
                    add(MessageMenuEntry("复制全部", Icons.Default.ContentCopy, onCopyAll))
                    if (message.contentType in listOf(1, 3, 8, 14)) {
                        add(MessageMenuEntry("自由复制", Icons.Default.ContentPaste, onFreeCopy))
                    }
                    onExternalShare?.let { add(MessageMenuEntry("外部分享", Icons.Default.Share, it)) }
                }
            )
        )
        val managementActions = buildList {
            onPlusOne?.let { add(MessageMenuEntry("+1", Icons.Default.AddCircle, it, primary)) }
            onFavorite?.let { add(MessageMenuEntry("收藏", Icons.Default.Star, it)) }
            onMultiSelect?.let { add(MessageMenuEntry("多选", Icons.Default.CheckBox, it)) }
            if (message.contentType in listOf(1, 3, 8, 14)) {
                onEdit?.let { add(MessageMenuEntry("编辑", Icons.Default.Edit, it)) }
            }
            if (showRecall) add(MessageMenuEntry("撤回消息", Icons.Default.Delete, onRecall, error))
        }
        if (managementActions.isNotEmpty()) {
            add(MessageMenuEntry("消息管理", Icons.Default.MoreHoriz, children = managementActions))
        }
        val contentActions = buildList {
            if (message.contentType == 8) {
                onOpenInInternalBrowser?.let {
                    add(MessageMenuEntry("在内置浏览器中打开", Icons.Default.Language, it))
                }
            }
            if (message.contentType == 7) {
                onAddExpression?.let { add(MessageMenuEntry("添加表情", Icons.Default.AddCircle, it)) }
            }
            onBlockUser?.let { add(MessageMenuEntry("屏蔽用户", Icons.Default.Block, it, error)) }
        }
        if (contentActions.isNotEmpty()) {
            add(MessageMenuEntry("内容与用户", Icons.Default.MoreHoriz, children = contentActions))
        }
        if (message.contentType == 11) {
            val audioActions = buildList {
                onSaveAudio?.let { add(MessageMenuEntry("保存语音", Icons.Default.Download, it)) }
                onAddToPlaylist?.let { add(MessageMenuEntry("加入播放列表", Icons.Default.QueueMusic, it)) }
                onSpeechToText?.let { add(MessageMenuEntry("语音转文字", Icons.Default.Edit, it)) }
            }
            if (audioActions.isNotEmpty()) {
                add(MessageMenuEntry("语音操作", Icons.Default.QueueMusic, children = audioActions))
            }
        }
    }
    val section = items.firstOrNull { it.label == openedSection }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = {
            if (openedSection != null) openedSection = null else onDismiss()
        },
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 224.dp, max = 280.dp)
                .animateContentSize(tween(180))
        ) {
            if (section != null) {
                DropdownMenuItem(
                    text = { Text(section.label, style = MaterialTheme.typography.titleSmall) },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回上一级")
                    },
                    onClick = { openedSection = null }
                )
                HorizontalDivider()
            }
            (section?.children ?: items).forEach { entry ->
                val tint = entry.tint ?: MaterialTheme.colorScheme.onSurface
                DropdownMenuItem(
                    text = { Text(entry.label, color = tint) },
                    leadingIcon = { Icon(entry.icon, contentDescription = null, tint = tint) },
                    trailingIcon = if (entry.children.isNotEmpty()) {
                        {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "展开子菜单")
                        }
                    } else null,
                    onClick = {
                        if (entry.children.isNotEmpty()) {
                            openedSection = entry.label
                        } else {
                            onDismiss()
                            entry.action?.invoke()
                        }
                    }
                )
            }
        }
    }
}

private data class MessageMenuEntry(
    val label: String,
    val icon: ImageVector,
    val action: (() -> Unit)? = null,
    val tint: Color? = null,
    val children: List<MessageMenuEntry> = emptyList()
)

/**
 * 保存语音到本地
 */
suspend fun saveVoiceToSavedAudios(context: Context, audioUrl: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val resolver = context.contentResolver
        val cacheManager = AudioCacheManager(context)

        val cachedFile = cacheManager.getCachedAudioFile(audioUrl)
            ?.takeIf { it.exists() && cacheManager.verifyCachedFile(audioUrl) }

        val displayName = run {
            val last = audioUrl.substringAfterLast('/').substringBefore('?').ifBlank { "voice_${System.currentTimeMillis()}" }
            if (last.contains('.')) last else "$last.m4a"
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/*")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/yhchat/audio/")
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val targetDir = java.io.File(downloadsDir, "yhchat/audio")
                if (!targetDir.exists()) targetDir.mkdirs()
                val targetFile = java.io.File(targetDir, displayName)
                put(MediaStore.MediaColumns.DATA, targetFile.absolutePath)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            resolver.insert(Uri.parse("content://media/external/downloads"), values)
        } else {
            resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
        } ?: return@withContext false
        try {
            resolver.openOutputStream(uri)?.use { out ->
                if (cachedFile != null) {
                    cachedFile.inputStream().use { input ->
                        input.copyTo(out)
                    }
                } else {
                    val client = OkHttpClient.Builder()
                        .addInterceptor { chain ->
                            val request = chain.request().newBuilder()
                                .addHeader("Referer", "https://myapp.jwznb.com")
                                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
                                .build()
                            chain.proceed(request)
                        }
                        .build()

                    val req = Request.Builder().url(audioUrl).build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@withContext false
                        val body = resp.body ?: return@withContext false
                        body.byteStream().use { input ->
                            input.copyTo(out)
                        }
                    }
                }
                out.flush()
            } ?: return@withContext false

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val done = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                resolver.update(uri, done, null, null)
            }
            true
        } catch (e: Exception) {
            // 清理失败项
            runCatching { resolver.delete(uri, null, null) }
            false
        }
    } catch (_: IOException) {
        false
    } catch (_: Exception) {
        false
    }
}
