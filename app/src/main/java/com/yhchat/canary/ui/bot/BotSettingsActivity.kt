package com.yhchat.canary.ui.bot

import android.content.Context
import android.content.Intent
import android.os.Bundle
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.GroupRemove
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yhchat.canary.ui.base.BaseActivity
import com.yhchat.canary.ui.settings.SettingsCustomItem
import com.yhchat.canary.ui.settings.SettingsGroup
import com.yhchat.canary.ui.settings.SettingsItemCell
import com.yhchat.canary.ui.settings.SettingsSwitchItem
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import com.yhchat.canary.ui.theme.YhchatCanaryTheme

class BotSettingsActivity : BaseActivity() {
    companion object {
        private const val EXTRA_BOT_ID = "extra_bot_id"
        private const val EXTRA_BOT_NAME = "extra_bot_name"
        private const val EXTRA_BOT_TOKEN = "extra_bot_token"

        fun start(context: Context, botId: String, botName: String, botToken: String = "") {
            val intent = Intent(context, BotSettingsActivity::class.java).apply {
                putExtra(EXTRA_BOT_ID, botId)
                putExtra(EXTRA_BOT_NAME, botName)
                putExtra(EXTRA_BOT_TOKEN, botToken)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val botId = intent.getStringExtra(EXTRA_BOT_ID) ?: ""
        val botName = intent.getStringExtra(EXTRA_BOT_NAME) ?: "机器人"
        val initialBotToken = intent.getStringExtra(EXTRA_BOT_TOKEN) ?: ""
        if (botId.isEmpty()) { finish(); return }

        setContent {
            YhchatCanaryTheme {
                BotSettingsScreen(
                    botId = botId,
                    botName = botName,
                    initialBotToken = initialBotToken,
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("DEPRECATION")
@Composable
private fun BotSettingsScreen(
    botId: String,
    botName: String,
    initialBotToken: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val viewModel: BotSettingsViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(botId) {
        viewModel.init(context)
        viewModel.loadInitial(botId, initialBotToken)
    }

    LaunchedEffect(uiState.actionMessage) {
        uiState.actionMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeActionMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$botName 设置", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    // 分组自带 16dp 水平内边距和 8dp 垂直内边距，外层不再重复加，
                    // 否则会比设置页整体往里缩一圈，两边对不齐
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (uiState.error != null) {
                    Text(
                        text = uiState.error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // ===== 机器人 Token =====
                SettingsGroup(
                    title = "机器人 Token",
                    items = listOf(
                        {
                            // 只读展示 Token + 重置。不套 Card：
                            // 这一段本质是「一行带按钮的设置项」，用 SettingsCustomItem
                            // 承载即可，外面由 SettingsGroup 给圆角和分组背景。
                            SettingsCustomItem {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = uiState.token,
                                            onValueChange = { },
                                            modifier = Modifier.weight(1f),
                                            placeholder = { Text("暂无 Token") },
                                            singleLine = true,
                                            enabled = !uiState.isLoading,
                                            readOnly = true,
                                            trailingIcon = {
                                                IconButton(
                                                    onClick = {
                                                        if (uiState.token.isNotBlank()) {
                                                            clipboard.setText(AnnotatedString(uiState.token))
                                                            Toast.makeText(context, "已复制机器人 Token", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    enabled = uiState.token.isNotBlank()
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ContentCopy,
                                                        contentDescription = "复制机器人 Token"
                                                    )
                                                }
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        OutlinedButton(
                                            onClick = { viewModel.resetBotToken(botId) },
                                            enabled = !uiState.isLoading
                                        ) {
                                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("重置")
                                        }
                                    }
                                }
                            }
                        }
                    )
                )

                // ===== Webhook / 长连接 =====
                SettingsGroup(
                    title = "事件接收方式",
                    items = listOf(
                        {
                            // 两个单选合并成一组：用 SettingsSwitchItem 不合适（它是布尔开关），
                            // 这里是真·互斥二选一，用 RadioButton 保留原语义
                            SettingsCustomItem {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = uiState.callbackMode == 0,
                                            onClick = { viewModel.updateCallbackMode(0) },
                                            enabled = !uiState.isSavingWebhook && !uiState.isLoading
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(text = "Webhook 回调", style = MaterialTheme.typography.bodyLarge)
                                            Text(
                                                text = "机器人接收消息事件的回调地址",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = uiState.callbackMode == 1,
                                            onClick = { viewModel.updateCallbackMode(1) },
                                            enabled = !uiState.isSavingWebhook && !uiState.isLoading
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(text = "长连接", style = MaterialTheme.typography.bodyLarge)
                                            Text(
                                                text = "通过 WebSocket 持续接收事件",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        {
                            SettingsCustomItem {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    val wsSubscribeUrl = remember(uiState.token) {
                                        val tok = uiState.token.takeIf { it.isNotBlank() } ?: "{token}"
                                        "wss://ws.jwzhd.com/subscribe?token=$tok"
                                    }

                                    if (uiState.callbackMode == 0) {
                                        OutlinedTextField(
                                            value = uiState.webhookUrl,
                                            onValueChange = { viewModel.updateWebhookUrl(it) },
                                            modifier = Modifier.fillMaxWidth(),
                                            label = { Text("回调地址") },
                                            placeholder = { Text("http(s)://ip(域名)...") },
                                            singleLine = true,
                                            enabled = !uiState.isSavingWebhook && !uiState.isLoading
                                        )
                                    } else {
                                        OutlinedTextField(
                                            value = wsSubscribeUrl,
                                            onValueChange = { },
                                            modifier = Modifier.fillMaxWidth(),
                                            label = { Text("订阅地址") },
                                            singleLine = true,
                                            enabled = !uiState.isSavingWebhook && !uiState.isLoading,
                                            readOnly = true,
                                            trailingIcon = {
                                                IconButton(
                                                    onClick = {
                                                        clipboard.setText(AnnotatedString(wsSubscribeUrl))
                                                        Toast.makeText(context, "已复制 WS 地址", Toast.LENGTH_SHORT).show()
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ContentCopy,
                                                        contentDescription = "复制 WS 地址"
                                                    )
                                                }
                                            }
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                BotWsTestActivity.start(
                                                    context = context,
                                                    botId = botId,
                                                    botName = botName,
                                                    botToken = uiState.token,
                                                    wsUrl = wsSubscribeUrl
                                                )
                                            },
                                            enabled = !uiState.isSavingWebhook && !uiState.isLoading
                                        ) {
                                            Text("测试")
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Button(
                                            onClick = { viewModel.saveWebhook(botId) },
                                            enabled = !uiState.isSavingWebhook && !uiState.isLoading
                                        ) {
                                            if (uiState.isSavingWebhook) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("保存中...")
                                            } else {
                                                Text("保存")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    )
                )

                // ===== 订阅链接管理 =====
                SettingsGroup(
                    title = "订阅链接",
                    items = listOf(
                        {
                            SettingsCustomItem {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "如果机器人订阅链接失效，可以使用此功能恢复",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = { viewModel.resetBotLink(botId) },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = !uiState.isResettingLink && !uiState.isLoading
                                    ) {
                                        if (uiState.isResettingLink) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("恢复中...")
                                        } else {
                                            Text("恢复订阅链接")
                                        }
                                    }
                                }
                            }
                        }
                    )
                )

                // ===== 事件订阅 =====
                // 这一组是这一页最典型的部分：7 个布尔开关。
                // 全部用 SettingsSwitchItem，交给 SettingsGroup 拼成一块，
                // 每条之间有 2dp 间隙和合并圆角 —— 跟设置页完全一致。
                SettingsGroup(
                    title = "事件订阅",
                    items = listOf(
                        {
                            SettingsSwitchItem(
                                icon = Icons.Default.Message,
                                title = "普通消息事件",
                                subtitle = "机器人收到普通消息时推送",
                                checked = uiState.messageReceiveNormal,
                                onCheckedChange = { viewModel.toggleEvent(botId, "messageReceiveNormal", it) }
                            )
                        },
                        {
                            SettingsSwitchItem(
                                icon = Icons.Default.Terminal,
                                title = "指令消息事件",
                                subtitle = "机器人收到指令消息时推送",
                                checked = uiState.messageReceiveInstruction,
                                onCheckedChange = { viewModel.toggleEvent(botId, "messageReceiveInstruction", it) }
                            )
                        },
                        {
                            SettingsSwitchItem(
                                icon = Icons.Default.PersonAdd,
                                title = "关注机器人事件",
                                subtitle = "有用户关注机器人时推送",
                                checked = uiState.botFollowed,
                                onCheckedChange = { viewModel.toggleEvent(botId, "botFollowed", it) }
                            )
                        },
                        {
                            SettingsSwitchItem(
                                icon = Icons.Default.PersonRemove,
                                title = "取关机器人事件",
                                subtitle = "有用户取关机器人时推送",
                                checked = uiState.botUnfollowed,
                                onCheckedChange = { viewModel.toggleEvent(botId, "botUnfollowed", it) }
                            )
                        },
                        {
                            SettingsSwitchItem(
                                icon = Icons.Default.GroupAdd,
                                title = "加入群事件",
                                subtitle = "机器人被拉入群聊时推送",
                                checked = uiState.groupJoin,
                                onCheckedChange = { viewModel.toggleEvent(botId, "groupJoin", it) }
                            )
                        },
                        {
                            SettingsSwitchItem(
                                icon = Icons.Default.GroupRemove,
                                title = "退出群事件",
                                subtitle = "机器人被移出群聊时推送",
                                checked = uiState.groupLeave,
                                onCheckedChange = { viewModel.toggleEvent(botId, "groupLeave", it) }
                            )
                        },
                        {
                            SettingsSwitchItem(
                                icon = Icons.Default.Settings,
                                title = "机器人设置消息事件",
                                subtitle = "机器人被修改设置时推送",
                                checked = uiState.botSetting,
                                onCheckedChange = { viewModel.toggleEvent(botId, "botSetting", it) }
                            )
                        }
                    )
                )

                // ===== 使用统计 =====
                SettingsGroup(
                    title = "使用统计",
                    items = listOf(
                        {
                            SettingsItemCell(
                                icon = Icons.Default.BarChart,
                                title = "查看使用列表",
                                subtitle = "哪些用户和群聊正在使用这个机器人",
                                onClick = { BotUsageListActivity.start(context, botId, botName) }
                            )
                        }
                    )
                )

                // ===== 机器人指令 =====
                SettingsGroup(
                    title = "机器人指令",
                    items = listOf(
                        {
                            SettingsItemCell(
                                icon = Icons.Default.Terminal,
                                title = "管理指令",
                                subtitle = "增删改机器人的指令与回复",
                                onClick = { InstructionManagementActivity.start(context, botId, botName) }
                            )
                        },
                        {
                            SettingsCustomItem {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    if (uiState.isLoadingInstructions) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(60.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                        }
                                    } else if (uiState.instructions.isEmpty()) {
                                        Text(
                                            text = "暂无指令",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            uiState.instructions.take(3).forEach { instruction ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = instruction.name,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                        if (!instruction.desc.isNullOrEmpty()) {
                                                            Text(
                                                                text = instruction.desc,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        text = when (instruction.instructionType) {
                                                            1 -> "普通"
                                                            2 -> "直发"
                                                            5 -> "自定义"
                                                            else -> "其他"
                                                        },
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }

                                                if (instruction != uiState.instructions.take(3).last()) {
                                                    HorizontalDivider()
                                                }
                                            }

                                            if (uiState.instructions.size > 3) {
                                                Text(
                                                    text = "还有 ${uiState.instructions.size - 3} 个指令…",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(top = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
