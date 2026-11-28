package com.yhchat.canary.ui.chat.ChatComponents

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yhchat.canary.data.di.RepositoryFactory
import com.yhchat.canary.data.model.ChatMessage
import com.yhchat.canary.utils.splitfile.RickkobeSplitProtocol
import com.yhchat.canary.utils.splitfile.RickkobeSplitDownloader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

@Composable
fun RickkobeSplitFileCard(message: ChatMessage, envelope: RickkobeSplitProtocol.Envelope) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember(message.msgId) { mutableStateOf(false) }
    var status by remember(message.msgId) { mutableStateOf("") }
    val manifest = envelope.manifest
    Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(manifest.name, style = MaterialTheme.typography.titleSmall)
        Text("Yunhu-rickkobe · ${manifest.size / 1024} KiB · ${manifest.count} 块", style = MaterialTheme.typography.bodySmall)
        if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        TextButton(enabled = !busy, onClick = {
            busy = true
            scope.launch {
                try {
                    val chatId = message.chatId?.takeIf { it.isNotBlank() } ?: error("缺少会话信息，请重新加载消息")
                    val chatType = message.chatType ?: error("缺少会话类型")
                    val repository = RepositoryFactory.getMessageRepository(context.applicationContext)
                    val parts = RickkobeSplitDownloader.discover(repository, message, chatId, chatType, manifest, listOf(message)) { status = it }
                    withContext(Dispatchers.IO) {
                        RickkobeSplitDownloader.download(context.applicationContext, manifest, parts) { text ->
                            scope.launch { status = text }
                        }
                    }
                    status = "已校验并保存到下载目录"
                    Toast.makeText(context, status, Toast.LENGTH_LONG).show()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    status = error.message ?: "下载失败，请重试"
                } finally { busy = false }
            }
        }) { Text(if (busy) "处理中…" else "下载并还原") }
    }
}
