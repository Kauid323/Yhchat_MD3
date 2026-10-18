package com.yhchat.canary.ui.bot

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yhchat.canary.ui.base.BaseActivity
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import com.yhchat.canary.ui.theme.YhchatCanaryTheme
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BotWsTestActivity : BaseActivity() {
    companion object {
        private const val EXTRA_BOT_ID = "extra_bot_id"
        private const val EXTRA_BOT_NAME = "extra_bot_name"
        private const val EXTRA_BOT_TOKEN = "extra_bot_token"
        private const val EXTRA_WS_URL = "extra_ws_url"

        fun start(context: Context, botId: String, botName: String, botToken: String, wsUrl: String) {
            val intent = Intent(context, BotWsTestActivity::class.java).apply {
                putExtra(EXTRA_BOT_ID, botId)
                putExtra(EXTRA_BOT_NAME, botName)
                putExtra(EXTRA_BOT_TOKEN, botToken)
                putExtra(EXTRA_WS_URL, wsUrl)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val botId = intent.getStringExtra(EXTRA_BOT_ID) ?: ""
        val botName = intent.getStringExtra(EXTRA_BOT_NAME) ?: "机器人"
        val botToken = intent.getStringExtra(EXTRA_BOT_TOKEN) ?: ""
        val wsUrl = intent.getStringExtra(EXTRA_WS_URL) ?: ""

        setContent {
            YhchatCanaryTheme {
                BotWsTestScreen(
                    botId = botId,
                    botName = botName,
                    botToken = botToken,
                    initialWsUrl = wsUrl,
                    onBack = { finish() }
                )
            }
        }
    }
}

private data class WsMessageItem(
    val raw: String,
    val time: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("DEPRECATION")
@Composable
private fun BotWsTestScreen(
    botId: String,
    botName: String,
    botToken: String,
    initialWsUrl: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val client = remember { OkHttpClient() }
    val sdf = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    var wsUrl by remember { mutableStateOf(initialWsUrl) }
    var isConnected by remember { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }
    var webSocket by remember { mutableStateOf<WebSocket?>(null) }

    val messages = remember { mutableStateListOf<WsMessageItem>() }
    val listState = rememberLazyListState()

    fun addMessage(raw: String) {
        val time = sdf.format(Date())
        messages.add(WsMessageItem(raw = raw, time = time))
    }

    fun connect() {
        if (wsUrl.isBlank()) {
            Toast.makeText(context, "请输入 WS 地址", Toast.LENGTH_SHORT).show()
            return
        }

        isConnecting = true

        val request = Request.Builder().url(wsUrl).build()
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                mainHandler.post {
                    isConnecting = false
                    isConnected = true
                    addMessage("{\"type\":\"connected\"}")
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                mainHandler.post {
                    addMessage(text)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                mainHandler.post {
                    isConnected = false
                    isConnecting = false
                }
                webSocket.close(code, reason)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                mainHandler.post {
                    isConnected = false
                    isConnecting = false
                    Toast.makeText(context, "连接失败: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val socket = client.newWebSocket(request, listener)
        webSocket = socket
    }

    fun disconnect() {
        webSocket?.close(1000, "disconnect")
        webSocket = null
        isConnected = false
        isConnecting = false
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            disconnect()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("事件测试", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = wsUrl,
                    onValueChange = { wsUrl = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = !isConnected && !isConnecting,
                    placeholder = { Text("wss://...") }
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        if (isConnected) disconnect() else connect()
                    },
                    enabled = !isConnecting
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "连接中")
                    } else {
                        Text(text = if (isConnected) "断开" else "连接")
                    }
                }
            }

            HorizontalDivider()

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages) { item ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            shadowElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                IconButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(item.raw))
                                        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "复制消息"
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text = item.raw,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Text(
                            text = item.time,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
