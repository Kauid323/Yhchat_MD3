@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.yhchat.canary.ui.disk.webdav

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yhchat.canary.data.model.DownloadStatus
import com.yhchat.canary.data.model.WebDAVDownloadTask
import com.yhchat.canary.ui.base.BaseActivity
import com.yhchat.canary.ui.theme.YhchatCanaryTheme
import com.yhchat.canary.utils.WebDAVDownloadManager

class WebDAVDownloadListActivity : BaseActivity() {

    companion object {
        fun start(context: android.content.Context) {
            context.startActivity(android.content.Intent(context, WebDAVDownloadListActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YhchatCanaryTheme {
                DownloadTaskScreen(onBackClick = { finish() })
            }
        }
    }
}

@Composable
private fun DownloadTaskScreen(onBackClick: () -> Unit) {
    val tasks by WebDAVDownloadManager.tasks.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("下载列表") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (tasks.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("暂无下载任务", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    DownloadTaskCard(task = task)
                }
            }
        }
    }
}

@Composable
private fun DownloadTaskCard(task: WebDAVDownloadTask) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(task.fileName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("来源: ${task.mountName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = when (task.status) {
                    DownloadStatus.QUEUED -> "队列中"
                    DownloadStatus.DOWNLOADING -> "下载中 ${((task.progress) * 100).toInt()}%"
                    DownloadStatus.COMPLETED -> "下载完成"
                    DownloadStatus.FAILED -> "下载失败: ${task.errorMessage ?: "未知错误"}"
                    DownloadStatus.CANCELED -> "已取消"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = when (task.status) {
                    DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
            if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.QUEUED) {
                LinearProgressIndicator(
                    progress = { task.progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "${formatSize(task.downloadedBytes)} / ${if (task.totalBytes > 0) formatSize(task.totalBytes) else "未知"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.QUEUED) {
                TextButton(
                    onClick = { WebDAVDownloadManager.cancelDownload(task.id) },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "取消下载")
                    Text("取消")
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1 -> String.format("%.2f GB", gb)
        mb >= 1 -> String.format("%.2f MB", mb)
        kb >= 1 -> String.format("%.2f KB", kb)
        else -> "$bytes B"
    }
}
