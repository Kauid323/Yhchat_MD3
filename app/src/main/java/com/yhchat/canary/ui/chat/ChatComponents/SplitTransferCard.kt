package com.yhchat.canary.ui.chat.ChatComponents

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yhchat.canary.ui.chat.SplitDownloadProgress
import com.yhchat.canary.ui.chat.SplitDownloadStage
import com.yhchat.canary.utils.splitfile.SplitTransferTask

/**
 * 大文件分块传输卡片。
 *
 * 协议里一个任务 = 一条 JSON 清单 + 若干条 `.part` 文件消息。
 * 这些分块消息本身不应该在消息流里各自刷屏，所以统一折叠进这张卡片。
 *
 * 卡片要能表达协议要求的几种状态：
 *   - 缺块时**明确显示数量**并允许重试；
 *   - 块齐但未校验 → 「可下载」，不谎称已完成；
 *   - 下载中显示阶段（下载 / 合并 / 校验），因为合并大文件时可能停顿几十秒，
 *     不给阶段提示用户会以为卡死了。
 */
@Composable
fun SplitTransferCard(
    task: SplitTransferTask,
    progress: SplitDownloadProgress?,
    isMyMessage: Boolean,
    modifier: Modifier = Modifier,
    onDownloadClick: () -> Unit,
    onCancelClick: () -> Unit,
    onClearCacheClick: () -> Unit,
    onOpenFileClick: (String) -> Unit
) {
    val manifest = task.manifest
    val textColor = if (isMyMessage) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (isMyMessage) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // ---- 标题行：文件类型图标 + 文件名 + 总大小 ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderZip,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = manifest.fileName,
                        color = textColor,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = buildString {
                            append(formatSplitSize(manifest.totalSize))
                            append(" · ")
                            append(manifest.chunkCount)
                            append(" 块")
                            manifest.mime?.let {
                                append(" · ")
                                append(it.substringAfterLast('/').uppercase())
                            }
                        },
                        color = textColor.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ---- 状态区 ----
            when {
                progress?.isCompleted == true -> {
                    CompletedRow(
                        progress = progress,
                        textColor = textColor,
                        onOpenFileClick = onOpenFileClick,
                        onClearCacheClick = onClearCacheClick
                    )
                }

                progress?.isRunning == true -> {
                    RunningRow(
                        progress = progress,
                        textColor = textColor,
                        onCancelClick = onCancelClick
                    )
                }

                else -> {
                    IdleRow(
                        task = task,
                        progress = progress,
                        textColor = textColor,
                        onDownloadClick = onDownloadClick
                    )
                }
            }
        }
    }
}

/** 未开始 / 失败待重试 */
@Composable
private fun IdleRow(
    task: SplitTransferTask,
    progress: SplitDownloadProgress?,
    textColor: Color,
    onDownloadClick: () -> Unit
) {
    val missing = task.missingCount
    val conflicts = task.conflicts.size

    val statusText = when {
        // 冲突优先提示：有冲突时算出来的块数是不可信的
        conflicts > 0 -> "有 $conflicts 个块号重复，无法合并"
        missing > 0 -> "缺 $missing 块，点击重试补齐"
        else -> "全部块已就位，可以合并"
    }

    val isError = conflicts > 0
    val statusColor = if (isError) {
        MaterialTheme.colorScheme.error
    } else {
        textColor.copy(alpha = 0.75f)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = statusText,
            color = statusColor,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )

        // 冲突状态不给下载按钮 —— 点了也只会失败
        if (!isError) {
            TextButton(onClick = onDownloadClick) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when {
                        // 之前失败过就明说"继续"，因为已下好的块会被跳过
                        progress?.isFailed == true -> "继续"
                        missing > 0 -> "补全"
                        else -> "下载合并"
                    }
                )
            }
        }
    }

    // 缺块时把具体块号列出来，方便判断是"刚开始收"还是"卡在某几块"
    if (missing > 0 && missing <= 20 && conflicts == 0) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "缺失：" + task.missingIndexes.joinToString("、"),
            color = textColor.copy(alpha = 0.55f),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

/** 下载 / 合并 / 校验中 */
@Composable
private fun RunningRow(
    progress: SplitDownloadProgress,
    textColor: Color,
    onCancelClick: () -> Unit
) {
    val stageText = when (progress.stage) {
        SplitDownloadStage.Downloading -> "正在下载 ${progress.doneChunks}/${progress.totalChunks} 块"
        SplitDownloadStage.Merging -> "正在合并…"
        SplitDownloadStage.Verifying -> "正在校验完整性…"
        else -> "处理中…"
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stageText,
                    color = textColor,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${(progress.fraction * 100).toInt()}%",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            if (progress.stage == SplitDownloadStage.Downloading) {
                LinearProgressIndicator(
                    progress = { progress.fraction },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                // 合并/校验阶段没有可靠进度，用不确定态，避免假进度条骗人
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        IconButton(onClick = onCancelClick, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Default.Cancel,
                contentDescription = "取消下载",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** 已完成 */
@Composable
private fun CompletedRow(
    progress: SplitDownloadProgress,
    textColor: Color,
    onOpenFileClick: (String) -> Unit,
    onClearCacheClick: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "已完成 · 校验通过",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            // 分块缓存在完成之后就没用了，给个明确的清理入口
            if (!progress.savedFilePath.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "分块缓存可清理",
                    color = textColor.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        TextButton(onClick = onClearCacheClick) {
            Icon(
                imageVector = Icons.Default.Recycling,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("清理", style = MaterialTheme.typography.labelMedium)
        }

        val path = progress.savedFilePath
        if (!path.isNullOrBlank()) {
            TextButton(onClick = { onOpenFileClick(path) }) {
                Text("打开")
            }
        }
    }
}

/** 卡片里用的尺寸格式化，和聊天里其它地方保持一致的观感 */
private fun formatSplitSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return if (index == 0) {
        "${value.toLong()} ${units[index]}"
    } else {
        String.format(java.util.Locale.US, "%.1f %s", value, units[index])
    }
}
