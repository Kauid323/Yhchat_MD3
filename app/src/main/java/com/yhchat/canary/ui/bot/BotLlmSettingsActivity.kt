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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import com.yhchat.canary.ui.settings.SettingsCustomItem
import com.yhchat.canary.ui.settings.SettingsGroup
import com.yhchat.canary.ui.settings.SettingsSwitchItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yhchat.canary.ui.base.BaseActivity
import com.yhchat.canary.ui.bot.viewmodel.BotLlmSettingsViewModel
import com.yhchat.canary.ui.bot.viewmodel.ParamVariableType
import com.yhchat.canary.ui.bot.viewmodel.ParamVariableUi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import com.yhchat.canary.ui.theme.YhchatCanaryTheme

class BotLlmSettingsActivity : BaseActivity() {
    companion object {
        private const val EXTRA_BOT_ID = "extra_bot_id"
        private const val EXTRA_BOT_NAME = "extra_bot_name"

        fun start(context: Context, botId: String, botName: String) {
            val intent = Intent(context, BotLlmSettingsActivity::class.java).apply {
                putExtra(EXTRA_BOT_ID, botId)
                putExtra(EXTRA_BOT_NAME, botName)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val botId = intent.getStringExtra(EXTRA_BOT_ID) ?: ""
        val botName = intent.getStringExtra(EXTRA_BOT_NAME) ?: "机器人"
        if (botId.isEmpty()) {
            finish()
            return
        }
        setContent {
            YhchatCanaryTheme {
                BotLlmSettingsScreen(
                    botId = botId,
                    botName = botName,
                    onBack = { finish() }
                )
            }
        }
    }
}

@Composable
private fun ParamVariableRow(
    variable: ParamVariableUi,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = variable.label.ifBlank { variable.name.ifBlank { variable.id } },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "ID: ${variable.id}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "编辑变量")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "删除变量")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "类型: ${if (variable.type == ParamVariableType.Input) "输入" else "下拉"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "名称(name): ${variable.name.ifBlank { "(自动)" }}",
                style = MaterialTheme.typography.bodySmall
            )
            if (variable.type == ParamVariableType.Select) {
                Text(
                    text = "选项: ${variable.options.ifBlank { "未配置" }}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/**
 * MCP 配置里的一条服务。
 *
 * 标准 MCP 配置长这样：
 * ```json
 * {"mcpServers": {
 *   "filesystem": {"command": "npx", "args": ["-y", "@modelcontextprotocol/server-filesystem"], "env": {"ROOT": "/tmp"}},
 *   "remote":     {"url": "https://example.com/sse"}
 * }}
 * ```
 * 关键点是它**不是数组**，而是一个「名字 → 配置」的对象。所以渲染时不能
 * 当成列表逐项套同一个模板，得按 name 逐条展开成卡片，每条再按自己有没有
 * command / url / args / env 分别显示 —— 本地进程型和远程 URL 型字段不一样。
 *
 * type 用来标注这条走哪种连接方式，界面上一眼能分出来：
 *  - Local：有 command，是本地起的进程
 *  - Remote：只有 url，是远程服务
 *  - Unknown：都没有，配置本身不完整
 */
private data class McpServer(
    val name: String,
    val command: String? = null,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val url: String? = null
) {
    val type: String
        get() = when {
            command != null -> "本地进程"
            url != null -> "远程服务"
            else -> "配置不完整"
        }
}

/**
 * 解析 mcpJson。
 *
 * 容错优先：这段文本用户可以随便改，随时可能是半截 JSON、数组形态、
 * 或者根本不是一个合法对象。任何一步失败都只返回空列表，让界面显示
 * 「无法解析」提示，而不是抛异常把整页打崩。
 *
 * 两种形态都认：
 *   {"mcpServers": {...}}  ← 标准 MCP 配置
 *   {...}                  ← 直接把 server 表当根对象（有些用户这么写）
 */
private fun parseMcpJson(json: String): List<McpServer> {
    if (json.isBlank()) return emptyList()
    return runCatching {
        val root = JsonParser.parseString(json)
        if (!root.isJsonObject) return emptyList()
        val obj = root.asJsonObject
        // 优先取标准里的 mcpServers；没有就把根对象当作 server 表
        val servers = obj.getAsJsonObject("mcpServers") ?: obj
        servers.entrySet().mapNotNull { (name, element) ->
            if (!element.isJsonObject) return@mapNotNull null
            val server = element.asJsonObject
            McpServer(
                name = name,
                command = server.get("command")?.takeIf { it.isJsonPrimitive }?.asString,
                // args 必须是数组；写成单个字符串的配置也见过，包成单元素列表
                args = when {
                    server.get("args")?.isJsonArray == true ->
                        server.getAsJsonArray("args").mapNotNull { it.asStringOrNull() }
                    server.get("args")?.isJsonPrimitive == true ->
                        listOf(server.get("args").asString)
                    else -> emptyList()
                },
                env = server.getAsJsonObject("env")
                    ?.entrySet()
                    ?.associate { (k, v) -> k to (v.asStringOrNull() ?: "") }
                    .orEmpty(),
                url = server.get("url")?.takeIf { it.isJsonPrimitive }?.asString
            )
        }
    }.getOrElse { emptyList() }
}

/** JSON 元素取字符串；非字符串（数字/布尔/null）返回 null，由调用方决定怎么显示 */
private fun JsonElement.asStringOrNull(): String? =
    if (isJsonPrimitive && !asJsonPrimitive.isBoolean && !asJsonPrimitive.isNumber) asString
    else if (isJsonPrimitive) asString
    else null

/**
 * 一条 MCP 服务的卡片。
 *
 * 字段按「有才显示」排列：本地进程型显示命令/参数/环境变量，远程型显示 URL。
 * 空字段不占位 —— 一条只有 url 的服务如果还占着「命令：无」两行，
 * 卡片会显得比信息本身还长。
 */
@Composable
private fun McpServerCard(server: McpServer) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = server.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                // 类型标签用性别的胶囊样式，跟下面的键值行区分开
                Text(
                    text = server.type,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }

            if (server.command != null) {
                McpField(label = "命令", value = server.command)
            }
            if (server.args.isNotEmpty()) {
                McpField(label = "参数", value = server.args.joinToString(" "))
            }
            if (server.env.isNotEmpty()) {
                McpField(
                    label = "环境变量",
                    value = server.env.entries.joinToString("\n") { "${it.key}=${it.value}" }
                )
            }
            if (server.url != null) {
                McpField(label = "URL", value = server.url)
            }
        }
    }
}

/** 卡片里的一行「标签 + 值」。值用等宽字体，路径/参数这类内容更好读 */
@Composable
private fun McpField(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 原始 JSON 折叠区。
 *
 * 结构化渲染之后，原始文本仍然要留一个入口 —— 用户可能要把整段拷走，
 * 或者核对解析有没有丢东西。默认收起，点开才显示，避免和上面的结构化
 * 内容重复占屏。
 */
@Composable
private fun RawJsonSection(
    json: String,
    onCopy: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "原始 JSON",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCopy) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "复制原始 JSON",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp
                        else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "收起" else "展开",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Text(
                    text = json.ifBlank { "[]" },
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParamVariableDialog(
    initial: ParamVariableUi,
    onDismiss: () -> Unit,
    onConfirm: (ParamVariableUi) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var id by remember { mutableStateOf(initial.id) }
    var name by remember { mutableStateOf(initial.name) }
    var label by remember { mutableStateOf(initial.label) }
    var type by remember { mutableStateOf(initial.type) }
    var options by remember { mutableStateOf(initial.options) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = if (initial.id.isBlank()) "新增变量" else "编辑变量", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = id,
                    onValueChange = { id = it.trim() },
                    label = { Text("变量 ID (唯一)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("变量名称 (name)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("展示文案 (label)") },
                    singleLine = true
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ParamVariableType.values().forEach { option ->
                        FilterChip(
                            selected = type == option,
                            onClick = { type = option },
                            label = { Text(if (option == ParamVariableType.Input) "文本输入" else "下拉选择") }
                        )
                    }
                }

                if (type == ParamVariableType.Select) {
                    OutlinedTextField(
                        value = options,
                        onValueChange = { options = it },
                        label = { Text("下拉选项 (# 分隔)") },
                        supportingText = { Text("示例: 选项A#选项B#选项C") }
                    )
                }

                error?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (id.isBlank()) {
                        error = "变量 ID 不能为空"
                        return@TextButton
                    }
                    val variable = ParamVariableUi(
                        id = id,
                        name = name.ifBlank { id },
                        label = label.ifBlank { name.ifBlank { id } },
                        type = type,
                        options = if (type == ParamVariableType.Select) options else ""
                    )
                    onConfirm(variable)
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("DEPRECATION")
@Composable
private fun BotLlmSettingsScreen(
    botId: String,
    botName: String,
    onBack: () -> Unit,
    viewModel: BotLlmSettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current

    var isParamDialogVisible by remember { mutableStateOf(false) }
    var editingParam by remember { mutableStateOf<ParamVariableUi?>(null) }

    LaunchedEffect(Unit) {
        viewModel.init(context)
    }
    LaunchedEffect(botId) {
        viewModel.load(botId)
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
                title = { Text("$botName 大模型设置", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    TextButton(
                        enabled = !uiState.isSaving && !uiState.isLoading,
                        onClick = { viewModel.saveSettings() }
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .width(18.dp)
                                    .height(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(text = "保存")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                // 分组自带 16dp 水平内边距和 8dp 垂直内边距，外层只留上下，
                // 否则两边叠起来会比设置页整体往里缩一圈
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (uiState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            }

            uiState.error?.let { errorText ->
                Text(
                    text = errorText,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // 开关组直接走 SettingsGroup，不套 CardSection：
            // SettingsSwitchItem 自带 16dp 内边距，再被 CardSection 的
            // Column(padding 16dp) 包一层就变成 32dp，跟上面的分组对不齐。
            SettingsGroup(
                title = "基础开关",
                items = listOf(
                    {
                        SettingsSwitchItem(
                            icon = Icons.Default.PowerSettingsNew,
                            title = "启用大模型",
                            subtitle = "开启后机器人将使用所选 LLM 响应",
                            checked = uiState.isBigModel,
                            onCheckedChange = viewModel::toggleBigModel
                        )
                    },
                    {
                        SettingsSwitchItem(
                            icon = Icons.Default.Reply,
                            title = "需要自动回复",
                            subtitle = "开启后机器人收到消息会使用 LLM 回复",
                            checked = uiState.isNeedReply,
                            onCheckedChange = viewModel::toggleNeedReply
                        )
                    }
                )
            )

            CardSection(title = "模型选择") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "来自模型列表",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    TextButton(onClick = viewModel::refreshModelList, enabled = !uiState.isRefreshingModels) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = if (uiState.isRefreshingModels) "刷新中" else "刷新列表")
                    }
                }

                if (uiState.isRefreshingModels) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                DropdownTextField(
                    value = uiState.llmName,
                    onValueChange = viewModel::updateLlmName,
                    label = "模型组 (llmName)",
                    options = uiState.llmGroups.map { it.id to it.name },
                    onOptionClick = { viewModel.selectGroup(it) }
                )

                val selectedGroup = uiState.llmGroups.firstOrNull { it.id == uiState.selectedGroupId }
                val modelOptions = selectedGroup?.subItems.orEmpty().map { it.id to it.name }

                DropdownTextField(
                    value = uiState.llmModelName,
                    onValueChange = viewModel::updateLlmModelName,
                    label = "模型名称 (llmModelName)",
                    options = modelOptions,
                    onOptionClick = { viewModel.selectModel(it) },
                    enabled = true,
                    placeholder = if (modelOptions.isEmpty()) "可在此手动填写" else null
                )

                OutlinedTextField(
                    value = uiState.llmBaseUrl,
                    onValueChange = viewModel::updateBaseUrl,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("自定义 Base URL (可选)") }
                )

                OutlinedTextField(
                    value = uiState.historyCountInput,
                    onValueChange = viewModel::updateHistoryCount,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("历史上下文字数 (historyCount)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            CardSection(title = "鉴权 & Prompt") {
                OutlinedTextField(
                    value = uiState.keyInput,
                    onValueChange = viewModel::updateKeyInput,
                    modifier = Modifier
                        .fillMaxWidth(),
                    label = { Text("API Key") },
                    supportingText = { Text("直接粘贴 Key 或完整 JSON，系统会自动格式化为 JSON") },
                    minLines = 1
                )

                OutlinedTextField(
                    value = uiState.prompt,
                    onValueChange = viewModel::updatePrompt,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("系统 Prompt") },
                    minLines = 3
                )
            }

            CardSection(title = "参数变量") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "通过变量向模型传递输入或选项",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "支持文本输入 (input) 与下拉选择 (select) 两种类型",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            editingParam = ParamVariableUi()
                            isParamDialogVisible = true
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "新增变量")
                    }
                }

                if (uiState.paramVariables.isEmpty()) {
                    Text(
                        text = "暂无变量，点击右上角 + 添加。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        uiState.paramVariables.forEach { variable ->
                            ParamVariableRow(
                                variable = variable,
                                onEdit = {
                                    editingParam = variable
                                    isParamDialogVisible = true
                                },
                                onDelete = { viewModel.deleteParamVariable(variable.id) }
                            )
                        }
                    }
                }

                // 结构化列表在上面已经展示了每个变量的名称/类型/选项，
                // 这里再摆一份原始 JSON 全文就是同一份数据看两遍。
                // 原始文本收进折叠区，需要核对或整段拷走时才展开。
                RawJsonSection(
                    json = uiState.paramJson,
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(uiState.paramJson))
                        Toast.makeText(context, "paramJson 已复制", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            CardSection(title = "MCP 配置") {
                // 解析当前编辑中的文本，实时跟着输入更新 ——
                // 注意这里解析的是 uiState.mcpJson（输入框里的内容），
                // 不是回包里的 mcpJson，否则改了输入框上面的卡片不会变。
                val mcpServers = remember(uiState.mcpJson) {
                    parseMcpJson(uiState.mcpJson)
                }
                // 编辑模式：默认收起输入框，只显示结构化结果。
                // 一直摊着 4 行 JSON 输入框，等于把「配置界面」变成「文本编辑器」，
                // 那正是之前这个页面看着像调试工具的原因。
                var editingMcp by rememberSaveable { mutableStateOf(false) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "MCP 服务",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (mcpServers.isEmpty()) "尚未配置服务" else "已配置 ${mcpServers.size} 个服务",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { editingMcp = !editingMcp }) {
                        Text(if (editingMcp) "完成" else "编辑")
                    }
                }

                if (mcpServers.isEmpty()) {
                    Text(
                        text = if (uiState.mcpJson.isBlank()) {
                            "还没有 MCP 配置。点「编辑」粘贴 mcpServers 配置。"
                        } else {
                            "当前内容无法解析成 MCP 配置，点「编辑」检查 JSON 格式。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        mcpServers.forEach { server ->
                            McpServerCard(server)
                        }
                    }
                }

                if (editingMcp) {
                    OutlinedTextField(
                        value = uiState.mcpJson,
                        onValueChange = viewModel::updateMcpJson,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("mcpJson（JSON）") },
                        minLines = 6
                    )
                }

                RawJsonSection(
                    json = uiState.mcpJson,
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(uiState.mcpJson))
                        Toast.makeText(context, "mcpJson 已复制", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    if (isParamDialogVisible && editingParam != null) {
        val current = editingParam!!
        val isExisting = current.id.isNotBlank() && uiState.paramVariables.any { it.id == current.id }
        ParamVariableDialog(
            initial = current,
            onDismiss = {
                isParamDialogVisible = false
                editingParam = null
            },
            onConfirm = { variable ->
                viewModel.upsertParamVariable(variable)
                isParamDialogVisible = false
                editingParam = null
            },
            onDelete = if (isExisting) {
                {
                    viewModel.deleteParamVariable(current.id)
                    isParamDialogVisible = false
                    editingParam = null
                }
            } else null
        )
    }
}

/**
 * 一页设置分组。
 *
 * 内部用 [SettingsGroup]：它负责标题颜色（primary）、圆角（24dp）、
 * 条目间的 2dp 间隙和分组的 surfaceContainerHigh 底色 —— 跟设置页完全一致。
 * 这里把整块内容包进一个 [SettingsCustomItem] 里当「一条」，
 * 因为这几块的内容是自由排版的表单，不是一行行的标准设置项。
 *
 * 为什么不再用 Surface 手搓：那样每个区块的圆角、内边距、底色都得自己写，
 * 五个区块就是五份重复的排版代码，而且跟设置页差几个 dp 也看不出来。
 */
@Composable
private fun CardSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    SettingsGroup(
        title = title,
        items = listOf(
            {
                SettingsCustomItem {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        content()
                    }
                }
            }
        )
    )
}

@Composable
private fun DropdownTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    options: List<Pair<Int, String>>,
    onOptionClick: (Int) -> Unit,
    enabled: Boolean = true,
    placeholder: String? = null
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Box {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                singleLine = true,
                label = { Text(label) },
                placeholder = placeholder?.let { { Text(it) } },
                trailingIcon = {
                    IconButton(onClick = { expanded = true }) {
                        Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                }
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (id, text) ->
                    DropdownMenuItem(
                        text = { Text(text) },
                        onClick = {
                            expanded = false
                            onOptionClick(id)
                        }
                    )
                }
                if (options.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("暂无可选项") },
                        enabled = false,
                        onClick = { }
                    )
                }
            }
        }
    }
}
