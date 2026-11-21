@file:OptIn(ExperimentalMaterial3Api::class)
package com.yhchat.canary.ui.share

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yhchat.canary.ui.components.ConversationPicker
import com.yhchat.canary.ui.base.BaseActivity
import com.yhchat.canary.ui.share.viewmodel.SendToChatViewModel
import com.yhchat.canary.ui.theme.YhchatCanaryTheme
import dagger.hilt.android.AndroidEntryPoint

private const val MAX_TARGETS = 10

/**
 * 「选择会话并发送」界面。
 *
 * 文本 / 图片 / 视频 / 附件 / 文章 / 转发消息 都走这个 Activity，
 * 不再用 bottom sheet。调用方式见 [SendToChatActivity.start]。
 */
@AndroidEntryPoint
class SendToChatActivity : BaseActivity() {

    companion object {
        /**
         * 打开「选择会话并发送」界面。
         *
         * ```
         * SendToChatActivity.start(context, SendToChatContent.Text("你好"))
         * SendToChatActivity.start(context, SendToChatContent.images(listOf(uri.toString())))
         * SendToChatActivity.start(context, SendToChatContent.videos(listOf(uri.toString())))
         * SendToChatActivity.start(context, SendToChatContent.files(listOf(uri.toString())))
         * ```
         *
         * @param title 顶栏标题，默认「发送至」；转发场景可以传「转发至」
         */
        fun start(
            context: Context,
            content: SendToChatContent,
            title: String = "发送至"
        ) {
            val intent = Intent(context, SendToChatActivity::class.java).apply {
                putExtra(SendToChatContent.EXTRA_TITLE, title)
                SendToChatContent.writeTo(this, content)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val content = SendToChatContent.fromIntent(intent, contentResolver)
        if (content == null) {
            Toast.makeText(this, "没有可发送的内容", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val title = intent.getStringExtra(SendToChatContent.EXTRA_TITLE) ?: "发送至"

        setContent {
            YhchatCanaryTheme {
                SendToChatScreen(
                    content = content,
                    title = title,
                    onClose = { finish() }
                )
            }
        }
    }
}

@Composable
private fun SendToChatScreen(
    content: SendToChatContent,
    title: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val sendViewModel: SendToChatViewModel = hiltViewModel()
    val sending by sendViewModel.progress.collectAsState()
    var remark by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<Set<SendToChatTarget>>(emptySet()) }

    val onSend: () -> Unit = {
        sendViewModel.send(content, selected.toList(), remark = remark) { result ->
            result.fold(
                onSuccess = {
                    Toast.makeText(context, "发送成功", Toast.LENGTH_SHORT).show()
                    (context as? Activity)?.finish()
                },
                onFailure = { error ->
                    Toast.makeText(context, error.message ?: "发送失败", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭"
                        )
                    }
                },
                actions = {
                    // 顶栏主操作：发送
                    Button(
                        onClick = onSend,
                        enabled = selected.isNotEmpty() && !sending.isSending,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        if (sending.isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("发送(${selected.size})")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = paddingValues.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding(),
                    start = paddingValues.calculateStartPadding(LocalLayoutDirection.current),
                    end = paddingValues.calculateEndPadding(LocalLayoutDirection.current)
                )
        ) {
            // 发送进度（上传图片 / 附件时带百分比）
            if (sending.isSending) {
                val uploadProgress = sending.progress
                if (uploadProgress != null) {
                    LinearProgressIndicator(
                        progress = { uploadProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Text(
                    text = sending.status ?: "正在发送…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // 正在发送什么
            Text(
                text = content.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            OutlinedTextField(
                value = remark,
                onValueChange = { remark = it },
                label = { Text("备注（可选）") },
                supportingText = { Text("内容发送完成后，备注将作为普通文本消息发送") },
                enabled = !sending.isSending,
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            ConversationPicker(
                selected = selected,
                onSelectionChange = { selected = it },
                enabled = !sending.isSending,
                maxSelection = MAX_TARGETS,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        }
    }
}
