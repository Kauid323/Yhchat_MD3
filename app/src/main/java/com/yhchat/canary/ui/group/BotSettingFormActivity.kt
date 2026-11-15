package com.yhchat.canary.ui.group

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yhchat.canary.data.model.BotSettingItem
import com.yhchat.canary.data.model.BotSettingType
import com.yhchat.canary.data.model.controlType
import com.yhchat.canary.ui.base.BaseActivity
import com.yhchat.canary.ui.theme.YhchatCanaryTheme

/**
 * 机器人设置表单页。
 *
 * 表单结构完全由机器人自己在服务端的 setting 定义决定，客户端不硬编码任何一项 ——
 * 打开页面时拉 /v1/bot/get-user-settings-json，按每项的 type 渲染对应 M3 控件。
 *
 * 对应入口：群聊机器人管理页里，`Bot_data.setting` 非空的机器人才会显示"设置"按钮。
 */
class BotSettingFormActivity : BaseActivity() {

    private val viewModel: BotSettingFormViewModel by viewModels()

    companion object {
        private const val EXTRA_BOT_ID = "bot_id"
        private const val EXTRA_BOT_NAME = "bot_name"
        private const val EXTRA_GROUP_ID = "group_id"

        fun start(context: Context, botId: String, botName: String, groupId: String) {
            context.startActivity(
                Intent(context, BotSettingFormActivity::class.java).apply {
                    putExtra(EXTRA_BOT_ID, botId)
                    putExtra(EXTRA_BOT_NAME, botName)
                    putExtra(EXTRA_GROUP_ID, groupId)
                }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val botId = intent.getStringExtra(EXTRA_BOT_ID) ?: run {
            finish()
            return
        }
        val botName = intent.getStringExtra(EXTRA_BOT_NAME).orEmpty().ifBlank { "机器人" }
        val groupId = intent.getStringExtra(EXTRA_GROUP_ID) ?: run {
            finish()
            return
        }

        setContent {
            YhchatCanaryTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BotSettingFormScreen(
                        botId = botId,
                        botName = botName,
                        groupId = groupId,
                        viewModel = viewModel,
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BotSettingFormScreen(
    botId: String,
    botName: String,
    groupId: String,
    viewModel: BotSettingFormViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(botId, groupId) {
        viewModel.load(botId, groupId)
    }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeUserMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "机器人设置",
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = botName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (!uiState.isLoading && uiState.error == null) {
                        Button(
                            onClick = { viewModel.save(botId, groupId) { } },
                            enabled = !uiState.isSaving && uiState.isDirty,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            if (uiState.isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("保存")
                            }
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                uiState.error != null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = uiState.error.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { viewModel.load(botId, groupId) }) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("重试")
                        }
                    }
                }

                else -> {
                    BotSettingFormContent(
                        viewModel = viewModel,
                        uiState = uiState
                    )
                }
            }
        }
    }
}

/**
 * 表单主体。
 *
 * 每一项都是独立卡片：标签在上，控件在下。textarea 的占位提示可能很长
 * （服务端拿它当说明文档用），所以单独一行小字，不和标签抢位置。
 */
@Composable
private fun BotSettingFormContent(
    viewModel: BotSettingFormViewModel,
    uiState: BotSettingFormUiState
) {
    val ordered = uiState.items

    if (ordered.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "这个机器人没有可配置的设置项",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(ordered, key = { it.id }) { item ->
            when (item.controlType) {
                BotSettingType.Switch -> SwitchSettingCard(
                    item = item,
                    checked = uiState.switches[item.id] ?: item.switchValue,
                    enabled = !uiState.isSaving,
                    onCheckedChange = { viewModel.setSwitch(item.id, it) }
                )

                BotSettingType.Radio -> RadioSettingCard(
                    item = item,
                    selectedIndex = uiState.radios[item.id] ?: (item.selectIndex ?: 0),
                    enabled = !uiState.isSaving,
                    onSelect = { viewModel.setRadio(item.id, it) }
                )

                BotSettingType.TextArea, BotSettingType.Text -> TextSettingCard(
                    item = item,
                    value = uiState.texts[item.id] ?: item.textValue,
                    enabled = !uiState.isSaving,
                    singleLine = item.controlType == BotSettingType.Text,
                    onValueChange = { viewModel.setText(item.id, it) }
                )

                // 未知类型不静默丢弃：显示成只读一行，让用户知道服务端返回了它但客户端还不认识。
                // 静默跳过的话，保存时这一项会被漏掉，用户以为改成功了
                BotSettingType.Unknown -> UnknownSettingCard(item)
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** 开关项：左侧标签，右侧 Switch，整行可点 */
@Composable
private fun SwitchSettingCard(
    item: BotSettingItem,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (item.placeholder.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.placeholder,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = if (enabled) onCheckedChange else null,
                enabled = enabled
            )
        }
    }
}

/**
 * 单选项：标签 + 一排 FilterChip。
 *
 * 服务端用 `#` 分隔选项；选项数量不多（这里是 3 个），横向铺开比下拉更直观。
 */
@Composable
private fun RadioSettingCard(
    item: BotSettingItem,
    selectedIndex: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit
) {
    val options = item.options

    SettingCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = item.label,
                style = MaterialTheme.typography.bodyLarge
            )

            if (options.isEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "该项没有可选项",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEachIndexed { index, option ->
                        FilterChip(
                            selected = index == selectedIndex,
                            onClick = { if (enabled) onSelect(index) },
                            enabled = enabled,
                            label = { Text(option) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 文本项。
 *
 * 单行用 [singleLine]，多行给 4~10 行高度。占位提示常常是一长段说明
 * （服务端会写"用英文逗号分割，留空则为…"），所以放在输入框下方而不是 placeholder 里，
 * 否则用户一输入就看不见说明了。
 */
@Composable
private fun TextSettingCard(
    item: BotSettingItem,
    value: String,
    enabled: Boolean,
    singleLine: Boolean,
    onValueChange: (String) -> Unit
) {
    SettingCard {
        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = value,
                onValueChange = { if (enabled) onValueChange(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(item.label) },
                enabled = enabled,
                singleLine = singleLine,
                minLines = if (singleLine) 1 else 3,
                maxLines = if (singleLine) 1 else 10,
                shape = RoundedCornerShape(16.dp)
            )

            if (item.placeholder.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = item.placeholder,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 客户端还不认识的 type：只读展示，至少让用户看到服务端有这项 */
@Composable
private fun UnknownSettingCard(item: BotSettingItem) {
    SettingCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = item.label,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "暂不支持的设置类型：${item.type.ifBlank { item.key.toString() }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 统一的卡片外壳：圆角 + surfaceContainerHigh，跟设置页那套观感对齐 */
@Composable
private fun SettingCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        content()
    }
}
