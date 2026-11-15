package com.yhchat.canary.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yhchat.canary.data.local.LocalApiSettings
import com.yhchat.canary.ui.base.BaseActivity
import com.yhchat.canary.ui.theme.YhchatCanaryTheme
import com.yhchat.canary.utils.localapi.LocalApiRouter
import com.yhchat.canary.utils.localapi.LocalApiServer
import kotlinx.coroutines.launch

/**
 * 本地 API 管理页。
 *
 * 把 App 自己的能力（会话/消息/详情/发送）以 JSON HTTP 的形式暴露出来，
 * 供本机上的 agent 调用。默认关闭。
 */
class LocalApiActivity : BaseActivity() {

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, LocalApiActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YhchatCanaryTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LocalApiScreen(onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalApiScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var enabled by remember { mutableStateOf(LocalApiServer.isRunning) }
    var portText by remember { mutableStateOf(LocalApiSettings.port(context).toString()) }
    var localhostOnly by remember { mutableStateOf(LocalApiSettings.isLocalhostOnly(context)) }
    var statusText by remember { mutableStateOf(if (LocalApiServer.isRunning) "运行中" else "已停止") }
    var isBusy by remember { mutableStateOf(false) }

    // 已生效的端口（区别于输入框里正在编辑的文本）
    var appliedPort by remember { mutableStateOf(LocalApiSettings.port(context)) }

    // 端口输入合法性：为空 / 越界都算错误，但不弹 Toast，用输入框自带的错误态提示
    val portError: String? = remember(portText) {
        val p = portText.toIntOrNull()
        when {
            portText.isBlank() -> "请输入端口"
            p == null -> "端口必须是数字"
            p !in LocalApiSettings.PORT_RANGE ->
                "端口需在 ${LocalApiSettings.PORT_RANGE.first}-${LocalApiSettings.PORT_RANGE.last} 之间"
            else -> null
        }
    }

    // 地址栏用「已生效端口」而不是输入框文本 —— 否则用户还没点应用，
    // 上面的地址就跟着输入框变了，显示的其实是个并未监听中的地址
    val baseUrl = "http://${if (localhostOnly) "127.0.0.1" else "0.0.0.0"}:$appliedPort"

    fun refreshStatus() {
        enabled = LocalApiServer.isRunning
        appliedPort = LocalApiSettings.port(context)
        statusText = when {
            LocalApiServer.isRunning -> "运行中"
            LocalApiServer.error != null -> "启动失败：${LocalApiServer.error}"
            else -> "已停止"
        }
    }

    LaunchedEffect(Unit) { refreshStatus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("本地 API", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---- 开关 ----
            item {
                SectionCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isBusy) {
                                isBusy = true
                                // 先同步输入框里的端口：用户可能改了但还没点"应用"，
                                // 这时候直接开开关应该按他填的端口起，而不是偷偷用旧端口
                                portText.toIntOrNull()
                                    ?.takeIf { it in LocalApiSettings.PORT_RANGE }
                                    ?.let { LocalApiSettings.setPort(context, it) }

                                scope.launch {
                                    if (enabled) {
                                        LocalApiServer.stop(context)
                                    } else {
                                        // start 会 bind 端口，是阻塞调用，走异步
                                        val result = LocalApiServer.startAsync(context)
                                        result.onFailure { e ->
                                            Toast.makeText(
                                                context,
                                                e.message ?: "启动失败",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                    isBusy = false
                                    refreshStatus()
                                }
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("本地服务器", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (LocalApiServer.isRunning) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        if (isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Switch(checked = enabled, onCheckedChange = null)
                        }
                    }
                }
            }

            // ---- 地址 ----
            if (LocalApiServer.isRunning) {
                item {
                    SectionCard {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "服务地址",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = baseUrl,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                        as? ClipboardManager
                                    cm?.setPrimaryClip(ClipData.newPlainText("baseUrl", baseUrl))
                                    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Default.ContentCopy, "复制", Modifier.size(18.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "不带 token，仅本机可访问。用 GET $baseUrl/ 可列出全部端点。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ---- 配置 ----
            item {
                SectionCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("监听端口", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = portText,
                            onValueChange = { portText = it.filter { ch -> ch.isDigit() }.take(5) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("端口") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(14.dp),
                            // 即时反馈：端口合法才允许点"应用"，不用等点了才知道错
                            isError = portError != null,
                            supportingText = {
                                Text(
                                    text = portError ?: "范围 ${LocalApiSettings.PORT_RANGE.first}-${LocalApiSettings.PORT_RANGE.last}，默认 ${LocalApiSettings.DEFAULT_PORT}",
                                    color = if (portError != null) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                enabled = portError == null && !isBusy && portText != appliedPort.toString(),
                                onClick = {
                                    val p = portText.toIntOrNull() ?: return@TextButton
                                    isBusy = true
                                    scope.launch {
                                        // 后台执行：bind 端口是阻塞调用，不能在主线程做
                                        val result = LocalApiServer.restartAsync(context, p)
                                        isBusy = false
                                        result.fold(
                                            onSuccess = {
                                                appliedPort = p
                                                refreshStatus()
                                                Toast.makeText(
                                                    context,
                                                    if (LocalApiServer.isRunning) "已切换到端口 $p" else "端口已保存，服务未启动",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            },
                                            onFailure = { e ->
                                                // 端口被占用是最常见的原因，直接说清楚
                                                val msg = e.message.orEmpty()
                                                val friendly = if (
                                                    msg.contains("already in use", true) ||
                                                    msg.contains("EADDRINUSE", true) ||
                                                    msg.contains("BindException", true)
                                                ) {
                                                    "端口 $p 已被占用，换一个试试"
                                                } else {
                                                    msg.ifBlank { "切换失败" }
                                                }
                                                Toast.makeText(context, friendly, Toast.LENGTH_LONG).show()
                                                refreshStatus()
                                            }
                                        )
                                    }
                                }
                            ) { Text("应用") }
                            TextButton(
                                enabled = !isBusy,
                                onClick = { portText = LocalApiSettings.DEFAULT_PORT.toString() }
                            ) { Text("恢复默认") }
                        }
                    }
                }
            }

            // ---- 仅本机 ----
            item {
                SectionCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("仅本机可访问", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = if (localhostOnly) {
                                    "绑定 127.0.0.1，只有本机进程能连"
                                } else {
                                    "绑定 0.0.0.0，同一局域网内的设备也能连"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = localhostOnly,
                            enabled = !isBusy,
                            onCheckedChange = { value ->
                                localhostOnly = value
                                LocalApiSettings.setLocalhostOnly(context, value)
                                // 绑定地址变了必须重启才生效。
                                // 重启是阻塞操作，必须放后台，不能在这里直接调。
                                if (LocalApiServer.isRunning) {
                                    isBusy = true
                                    scope.launch {
                                        val result = LocalApiServer.restartAsync(
                                            context,
                                            LocalApiSettings.port(context)
                                        )
                                        isBusy = false
                                        result.onFailure { e ->
                                            Toast.makeText(
                                                context,
                                                e.message ?: "重启失败",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                        refreshStatus()
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // ---- 端点列表 ----
            item {
                Text(
                    text = "可用端点",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                )
            }

            items(LocalApiRouter.ENDPOINTS) { endpoint ->
                SectionCard {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = endpoint.method,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (endpoint.method == "POST") {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = endpoint.path,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = endpoint.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (endpoint.params.isNotBlank()) {
                            Text(
                                text = endpoint.params,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

/** 统一的卡片外壳 */
@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        content()
    }
}
