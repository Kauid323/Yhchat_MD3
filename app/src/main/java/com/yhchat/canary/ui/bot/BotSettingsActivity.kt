package com.yhchat.canary.ui.bot

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yhchat.canary.ui.base.BaseActivity
import com.yhchat.canary.ui.adaptive.YhButton
import com.yhchat.canary.ui.adaptive.YhCard
import com.yhchat.canary.ui.adaptive.YhCircularProgressIndicator
import com.yhchat.canary.ui.adaptive.YhHorizontalDivider
import com.yhchat.canary.ui.adaptive.YhIcon as Icon
import com.yhchat.canary.ui.adaptive.YhIconButton
import com.yhchat.canary.ui.adaptive.YhOutlinedButton
import com.yhchat.canary.ui.adaptive.YhOutlinedTextField
import com.yhchat.canary.ui.adaptive.YhRadioButton
import com.yhchat.canary.ui.adaptive.YhRichListItem
import com.yhchat.canary.ui.adaptive.YhScaffold
import com.yhchat.canary.ui.adaptive.YhSwitch
import com.yhchat.canary.ui.adaptive.YhText as Text
import com.yhchat.canary.ui.adaptive.YhTopBar
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

    YhScaffold(
        topBar = {
            YhTopBar(
                title = "$botName 设置",
                large = false,
                navigationIcon = {
                    YhIconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
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
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (uiState.error != null) {
                    Text(text = uiState.error ?: "", color = MaterialTheme.colorScheme.error)
                }

                // Token 区域
                YhCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 12.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "机器人 Token",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            YhOutlinedTextField(
                                value = uiState.token,
                                onValueChange = { },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("请输入机器人 Token") },
                                singleLine = true,
                                enabled = !uiState.isLoading,
                                readOnly = true,
                                trailingIcon = {
                                    YhIconButton(
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
                            
                            YhOutlinedButton(
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
                
                // Webhook 订阅地址区域
                YhCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 12.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Webhook 订阅地址",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        Text(
                            text = "机器人接收消息事件的回调地址",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                YhRadioButton(
                                    selected = uiState.callbackMode == 0,
                                    onClick = { viewModel.updateCallbackMode(0) },
                                    enabled = !uiState.isSavingWebhook && !uiState.isLoading
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "使用 Webhook 回调")
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                YhRadioButton(
                                    selected = uiState.callbackMode == 1,
                                    onClick = { viewModel.updateCallbackMode(1) },
                                    enabled = !uiState.isSavingWebhook && !uiState.isLoading
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "使用长连接接收事件")
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val wsSubscribeUrl = remember(uiState.token) {
                            val tok = uiState.token.takeIf { it.isNotBlank() } ?: "{token}"
                            "wss://ws.jwzhd.com/subscribe?token=$tok"
                        }

                        if (uiState.callbackMode == 0) {
                            YhOutlinedTextField(
                                value = uiState.webhookUrl,
                                onValueChange = { viewModel.updateWebhookUrl(it) },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("http(s)://ip(域名)...") },
                                singleLine = true,
                                enabled = !uiState.isSavingWebhook && !uiState.isLoading
                            )
                        } else {
                            YhOutlinedTextField(
                                value = wsSubscribeUrl,
                                onValueChange = { },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                enabled = !uiState.isSavingWebhook && !uiState.isLoading,
                                readOnly = true,
                                trailingIcon = {
                                    YhIconButton(
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
                            YhOutlinedButton(
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

                            YhButton(
                                onClick = { viewModel.saveWebhook(botId) },
                                enabled = !uiState.isSavingWebhook && !uiState.isLoading
                            ) {
                                if (uiState.isSavingWebhook) {
                                    YhCircularProgressIndicator(
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
                
                // 恢复订阅链接按钮
                YhCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 12.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "订阅链接管理",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        Text(
                            text = "如果机器人订阅链接失效，可以使用此功能恢复",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        YhButton(
                            onClick = { viewModel.resetBotLink(botId) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isResettingLink && !uiState.isLoading
                        ) {
                            if (uiState.isResettingLink) {
                                YhCircularProgressIndicator(
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

                // 订阅设置
                YhCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 12.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(text = "事件订阅", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

                        SubscriptionSwitch("普通消息事件", uiState.messageReceiveNormal) { checked ->
                            viewModel.toggleEvent(botId, "messageReceiveNormal", checked)
                        }
                        SubscriptionSwitch("指令消息事件", uiState.messageReceiveInstruction) { checked ->
                            viewModel.toggleEvent(botId, "messageReceiveInstruction", checked)
                        }
                        SubscriptionSwitch("关注机器人事件", uiState.botFollowed) { checked ->
                            viewModel.toggleEvent(botId, "botFollowed", checked)
                        }
                        SubscriptionSwitch("取关机器人事件", uiState.botUnfollowed) { checked ->
                            viewModel.toggleEvent(botId, "botUnfollowed", checked)
                        }
                        SubscriptionSwitch("加入群事件", uiState.groupJoin) { checked ->
                            viewModel.toggleEvent(botId, "groupJoin", checked)
                        }
                        SubscriptionSwitch("退出群事件", uiState.groupLeave) { checked ->
                            viewModel.toggleEvent(botId, "groupLeave", checked)
                        }
                        SubscriptionSwitch("机器人设置消息事件", uiState.botSetting) { checked ->
                            viewModel.toggleEvent(botId, "botSetting", checked)
                        }
                    }
                }

                YhCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 12.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "使用统计",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Text(
                            text = "查看哪些用户和群聊正在使用这个机器人",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        YhRichListItem(
                            headlineContent = { Text("查看使用列表") },
                            supportingContent = { Text("用户列表 / 群聊列表") },
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null
                                )
                            },
                            containerColor = Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp)),
                            onClick = {
                                BotUsageListActivity.start(context, botId, botName)
                            }
                        )
                    }
                }
                
                // 机器人指令列表
                YhCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 12.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "机器人指令",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            
                            YhIconButton(
                                onClick = {
                                    InstructionManagementActivity.start(context, botId, botName)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "管理指令"
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (uiState.isLoadingInstructions) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                YhCircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        } else if (uiState.instructions.isEmpty()) {
                            Text(
                                text = "暂无指令，点击右边按钮进入管理",
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
                                        YhHorizontalDivider()
                                    }
                                }
                                
                                if (uiState.instructions.size > 3) {
                                    Text(
                                        text = "还有 ${uiState.instructions.size - 3} 个指令...",
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
        }
    }
}

@Composable
private fun SubscriptionSwitch(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyMedium)
        YhSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
