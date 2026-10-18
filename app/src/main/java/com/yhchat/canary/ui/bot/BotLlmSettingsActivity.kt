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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
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

@Composable
private fun JsonPreviewCard(
    title: String,
    json: String,
    onCopy: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                if (onCopy != null) {
                    IconButton(onClick = onCopy) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "复制 JSON")
                    }
                }
            }
            Text(
                text = json.ifBlank { "[]" },
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            )
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (uiState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            uiState.error?.let { errorText ->
                Text(
                    text = errorText,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            CardSection(title = "基础开关") {
                SettingsSwitchRow(
                    title = "启用大模型",
                    subtitle = "开启后机器人将使用所选 LLM 响应",
                    checked = uiState.isBigModel,
                    onCheckedChange = viewModel::toggleBigModel
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SettingsSwitchRow(
                    title = "需要自动回复",
                    subtitle = "开启后机器人收到消息会使用 LLM 回复",
                    checked = uiState.isNeedReply,
                    onCheckedChange = viewModel::toggleNeedReply
                )
            }

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

                JsonPreviewCard(
                    title = "paramJson 预览",
                    json = uiState.paramJson,
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(uiState.paramJson))
                        Toast.makeText(context, "paramJson 已复制", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            CardSection(title = "MCP 配置") {
                OutlinedTextField(
                    value = uiState.mcpJson,
                    onValueChange = viewModel::updateMcpJson,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("MCP 定义 (mcpJson)") },
                    minLines = 4
                )

                JsonPreviewCard(
                    title = "mcpJson 预览",
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

@Composable
private fun CardSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
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
