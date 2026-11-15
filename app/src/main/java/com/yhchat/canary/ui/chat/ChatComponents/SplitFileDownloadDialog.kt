package com.yhchat.canary.ui.chat.ChatComponents

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yhchat.canary.data.model.SplitFileManifest
import com.yhchat.canary.utils.splitfile.SplitFileDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 收到 `.splitfiles` 文件时的确认与下载弹窗。
 *
 * 用户点开清单文件后：
 *   1. 解析清单，展示文件名、大小、块数；
 *   2. 询问是否下载还原；
 *   3. 确认后逐块下载、合并、写入系统下载目录。
 */
@Composable
fun SplitFileDownloadDialog(
    context: Context,
    manifest: SplitFileManifest,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isDownloading by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf("") }
    var fraction by remember { mutableStateOf(0f) }

    AlertDialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        icon = {
            Icon(
                imageVector = Icons.Default.ContentCut,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("分块文件") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = manifest.fileName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${formatSize(manifest.totalSize)} · ${manifest.parts.size} 块",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = "这是一个被切成多块的大文件。下载后会合并还原，保存到系统下载目录。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isDownloading) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = progressText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isDownloading,
                onClick = {
                    isDownloading = true
                    fraction = 0f
                    progressText = "准备下载…"
                    scope.launch {
                        val result = SplitFileDownloader.downloadAndMerge(
                            context = context,
                            manifest = manifest
                        ) { progress ->
                            fraction = progress.fraction
                            progressText = when (progress.stage) {
                                SplitFileDownloader.Progress.Stage.Downloading ->
                                    "正在下载 ${progress.currentChunk + 1}/${progress.totalChunks} 块"
                                SplitFileDownloader.Progress.Stage.Merging -> "正在合并…"
                                SplitFileDownloader.Progress.Stage.Done -> "完成"
                            }
                        }

                        isDownloading = false
                        result.fold(
                            onSuccess = {
                                Toast.makeText(
                                    context,
                                    "已保存到下载目录：${manifest.fileName}",
                                    Toast.LENGTH_LONG
                                ).show()
                                onDismiss()
                            },
                            onFailure = { e ->
                                Toast.makeText(
                                    context,
                                    "下载失败：${e.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        )
                    }
                }
            ) {
                Text(if (isDownloading) "下载中…" else "下载并还原")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isDownloading) {
                Text("取消")
            }
        }
    )
}

private fun formatSize(bytes: Long): String {
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

/**
 * 读取云端清单文件的内容并解析。
 *
 * 挂起函数：网络请求必须在 IO 线程。
 */
suspend fun loadSplitManifest(context: Context, fileUrl: String): SplitFileManifest? =
    withContext(Dispatchers.IO) {
        runCatching {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val request = okhttp3.Request.Builder()
                .url(fileUrl)
                .header("Referer", "https://myapp.jwznb.com")
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36"
                )
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val text = response.body?.string() ?: return@withContext null
                SplitFileDownloader.parseManifest(text)
            }
        }.getOrNull()
    }
