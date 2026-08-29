package com.yhchat.canary.ui.chat.ChatComponents

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.MaterialTheme
import com.yhchat.canary.ui.adaptive.YhIcon as Icon
import com.yhchat.canary.ui.adaptive.YhText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yhchat.canary.data.model.ChatMessage
import com.yhchat.canary.service.AudioCacheManager
import com.yhchat.canary.ui.adaptive.YhAlertDialog
import com.yhchat.canary.ui.adaptive.YhTextButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * 消息上下文菜单
 */
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
    onAddToPlaylist: (() -> Unit)? = null,   // 加入播放列表
    onSpeechToText: (() -> Unit)? = null,  // 语音转文字
    onPlusOne: (() -> Unit)? = null,  // +1 复制发送同样消息
    onFavorite: (() -> Unit)? = null,  // 收藏消息到本地
    onMultiSelect: (() -> Unit)? = null,  // 多选
    onOpenInInternalBrowser: (() -> Unit)? = null,
    onExternalShare: (() -> Unit)? = null
) {
    YhAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "消息操作",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 复制全部
                ContextMenuAction(
                    onClick = onCopyAll,
                    icon = Icons.Default.ContentCopy,
                    text = "复制全部"
                )

                // 自由复制（仅对文本、Markdown、HTML消息显示）
                if (message.contentType in listOf(1, 3, 8)) {
                    ContextMenuAction(
                        onClick = onFreeCopy,
                        icon = Icons.Default.ContentPaste,
                        text = "自由复制"
                    )
                }

                if (message.contentType == 8 && onOpenInInternalBrowser != null) {
                    ContextMenuAction(
                        onClick = onOpenInInternalBrowser,
                        icon = Icons.Default.Language,
                        text = "在内置浏览器中打开"
                    )
                }
                
                // 引用
                ContextMenuAction(
                    onClick = onQuote,
                    icon = Icons.Default.FormatQuote,
                    text = "引用"
                )

                ContextMenuAction(
                    onClick = onForward,
                    icon = Icons.AutoMirrored.Filled.Send,
                    text = "转发"
                )

                if (onExternalShare != null) {
                    ContextMenuAction(
                        onClick = onExternalShare,
                        icon = Icons.Default.Share,
                        text = "外部分享"
                    )
                }
                
                // 编辑消息（仅对文本、Markdown、HTML、A2UI消息显示）
                if (onEdit != null && message.contentType in listOf(1, 3, 8, 14)) {
                    ContextMenuAction(
                        onClick = onEdit,
                        icon = Icons.Default.Edit,
                        text = "编辑"
                    )
                }
                
                // 添加表情（仅对消息类型7显示）
                if (onAddExpression != null && message.contentType == 7) {
                    ContextMenuAction(
                        onClick = onAddExpression,
                        icon = Icons.Default.AddCircle,
                        text = "添加表情"
                    )
                }

                // 保存语音（仅对语音消息显示）
                if (onSaveAudio != null && message.contentType == 11) {
                    ContextMenuAction(
                        onClick = onSaveAudio,
                        icon = Icons.Default.Download,
                        text = "保存语音"
                    )
                }

                // 加入播放列表（仅对语音消息显示）
                if (onAddToPlaylist != null && message.contentType == 11) {
                    ContextMenuAction(
                        onClick = onAddToPlaylist,
                        icon = Icons.Default.PlaylistAdd,
                        text = "加入播放列表"
                    )
                }
                
                // 语音转文字（仅对语音消息显示）
                if (onSpeechToText != null && message.contentType == 11) {
                    ContextMenuAction(
                        onClick = onSpeechToText,
                        icon = Icons.Default.Edit,
                        text = "语音转文字"
                    )
                }
                
                // +1 复制发送同样消息
                if (onPlusOne != null) {
                    ContextMenuAction(
                        onClick = onPlusOne,
                        icon = Icons.Default.AddCircle,
                        text = "+1",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                if (onFavorite != null) {
                    ContextMenuAction(
                        onClick = onFavorite,
                        icon = Icons.Default.Star,
                        text = "收藏"
                    )
                }
                
                // 多选
                if (onMultiSelect != null) {
                    ContextMenuAction(
                        onClick = onMultiSelect,
                        icon = Icons.Default.CheckBox,
                        text = "多选"
                    )
                }
                
                // 屏蔽用户
                if (onBlockUser != null) {
                    ContextMenuAction(
                        onClick = onBlockUser,
                        icon = Icons.Default.Block,
                        text = "屏蔽用户",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                
                // 撤回（危险操作）
                if (showRecall) {
                    ContextMenuAction(
                        onClick = {
                            onRecall()
                        },
                        icon = Icons.Default.Delete,
                        text = "撤回消息",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            YhTextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun ContextMenuAction(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    YhTextButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                modifier = Modifier.size(20.dp),
                tint = tint
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(text, color = tint)
        }
    }
}

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
