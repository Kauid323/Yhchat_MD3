package com.yhchat.canary.ui.chat.ChatComponents

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import com.yhchat.canary.ui.adaptive.YhIcon as Icon
import com.yhchat.canary.ui.adaptive.YhText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yhchat.canary.service.AudioPlayerService
import com.yhchat.canary.service.FileDownloadService
import com.yhchat.canary.ui.adaptive.YhSurface
import com.yhchat.canary.ui.chat.ChatComponents.appendToAutoQueue
import com.yhchat.canary.utils.PermissionUtils
import kotlinx.coroutines.launch

/**
 * 格式化文件大小
 */
fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)}MB"
        else -> "${bytes / (1024 * 1024 * 1024)}GB"
    }
}

/**
 * 格式化音频时长
 */
fun formatAudioDuration(seconds: Long): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "${minutes}:${remainingSeconds.toString().padStart(2, '0')}"
}

fun handleFileDownload(
    context: Context,
    fileUrl: String,
    fileName: String,
    fileSize: Long,
    msgId: String? = null
) {
    if (!PermissionUtils.hasAllDownloadPermissions(context)) {
        if (context is Activity) {
            PermissionUtils.requestAllDownloadPermissions(context)
            Toast.makeText(context, "请先授予下载所需权限", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "当前上下文无法申请权限", Toast.LENGTH_SHORT).show()
        }
        return
    }

    Toast.makeText(context, "开始下载：$fileName", Toast.LENGTH_SHORT).show()
    FileDownloadService.startDownload(
        context = context,
        fileUrl = fileUrl,
        fileName = fileName,
        fileSize = fileSize,
        msgId = msgId
    )
}

/**
 * 视频消息视图
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun VideoDownloadMessageView(
    videoUrl: String,
    textColor: Color,
    modifier: Modifier = Modifier,
    onLongClick: () -> Unit = {}
) {
    val context = LocalContext.current
    var downloadState by remember { mutableStateOf<DownloadState>(DownloadState.NotStarted) }

    YhSurface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = {
                    if (downloadState == DownloadState.NotStarted) {
                        downloadState = DownloadState.Downloading
                        val fileName = videoUrl.substringAfterLast("/").ifEmpty { "video_${System.currentTimeMillis()}.mp4" }

                        FileDownloadService.startDownload(
                            context = context,
                            fileUrl = videoUrl,
                            fileName = fileName,
                            fileSize = 0L,
                            autoOpen = true
                        )

                        Toast.makeText(context, "开始下载视频：$fileName", Toast.LENGTH_SHORT).show()
                    }
                },
                onLongClick = onLongClick
            ),
        color = textColor.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(textColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "视频",
                    tint = textColor,
                    modifier = Modifier.size(36.dp)
                )
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "视频消息",
                    color = textColor,
                    style = MaterialTheme.typography.bodyLarge
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when (downloadState) {
                        DownloadState.NotStarted -> "点击下载，使用外部播放器播放"
                        DownloadState.Downloading -> "正在下载..."
                        DownloadState.Completed -> "已下载"
                    },
                    color = textColor.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Icon(
                imageVector = when (downloadState) {
                    DownloadState.NotStarted -> Icons.Default.PlayArrow
                    DownloadState.Downloading -> Icons.Default.Add
                    DownloadState.Completed -> Icons.Default.Check
                },
                contentDescription = when (downloadState) {
                    DownloadState.NotStarted -> "下载"
                    DownloadState.Downloading -> "下载中"
                    DownloadState.Completed -> "完成"
                },
                tint = textColor.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * 下载状态
 */
enum class DownloadState {
    NotStarted,
    Downloading,
    Completed
}

/**
 * 语音消息视图
 */
@Composable
fun AudioMessageView(
    audioUrl: String,
    duration: Long,
    textColor: Color,
    senderName: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    var isCurrentlyPlaying by remember { mutableStateOf(false) }

    LaunchedEffect(audioUrl) {
        // 可在此同步当前播放状态
    }

    YhSurface(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable {
                val displayTitle = "$senderName 的语音"
                coroutineScope.launch {
                    appendToAutoQueue(context, displayTitle, audioUrl, "CHAT")
                }
                AudioPlayerService.startPlayAudio(
                    context = context,
                    audioUrl = audioUrl,
                    title = displayTitle
                )
                isCurrentlyPlaying = !isCurrentlyPlaying
            },
        color = textColor.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isCurrentlyPlaying) Icons.Default.Check else Icons.Default.PlayArrow,
                contentDescription = if (isCurrentlyPlaying) "暂停" else "播放",
                tint = textColor,
                modifier = Modifier.size(24.dp)
            )

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(5) { index ->
                    val height = if (isCurrentlyPlaying) {
                        (8 + (index * 2)).dp
                    } else {
                        6.dp
                    }

                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(height)
                            .background(
                                textColor.copy(alpha = 0.6f),
                                RoundedCornerShape(1.dp)
                            )
                    )
                }
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = formatAudioDuration(duration),
                color = textColor,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
