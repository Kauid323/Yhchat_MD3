package com.yhchat.canary.ui.bot

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yhchat.canary.data.api.ApiClient
import com.yhchat.canary.data.di.RepositoryFactory
import com.yhchat.canary.data.model.BotInstruction
import com.yhchat.canary.data.model.BotInstructionRequest
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import com.yhchat.canary.ui.theme.YhchatCanaryTheme
import kotlinx.coroutines.launch

class InstructionEditActivity : ComponentActivity() {
    
    companion object {
        private const val EXTRA_BOT_ID = "bot_id"
        private const val EXTRA_BOT_NAME = "bot_name"
        private const val EXTRA_INSTRUCTION_ID = "instruction_id"
        
        fun start(context: Context, botId: String, botName: String, instructionId: Int?) {
            val intent = Intent(context, InstructionEditActivity::class.java).apply {
                putExtra(EXTRA_BOT_ID, botId)
                putExtra(EXTRA_BOT_NAME, botName)
                instructionId?.let { putExtra(EXTRA_INSTRUCTION_ID, it) }
            }
            context.startActivity(intent)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val botId = intent.getStringExtra(EXTRA_BOT_ID) ?: run {
            Toast.makeText(this, "参数错误", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val botName = intent.getStringExtra(EXTRA_BOT_NAME) ?: "机器人"
        val instructionId = if (intent.hasExtra(EXTRA_INSTRUCTION_ID)) {
            intent.getIntExtra(EXTRA_INSTRUCTION_ID, -1).takeIf { it != -1 }
        } else null
        
        setContent {
            YhchatCanaryTheme {
                InstructionEditScreen(
                    botId = botId,
                    botName = botName,
                    instructionId = instructionId,
                    onBackClick = { finish() },
                    onSaveSuccess = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstructionEditScreen(
    botId: String,
    botName: String,
    instructionId: Int?,
    onBackClick: () -> Unit,
    onSaveSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isEdit = instructionId != null
    
    var instruction by remember { mutableStateOf<BotInstruction?>(null) }
    var isLoading by remember { mutableStateOf(isEdit) }
    var error by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    
    // 表单字段
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var instructionType by remember { mutableStateOf(1) }
    var hintText by remember { mutableStateOf("") }
    var defaultText by remember { mutableStateOf("") }
    var customFields by remember { mutableStateOf<List<InstructionFormField>>(emptyList()) }
    
    // 加载指令详情（编辑模式）
    LaunchedEffect(instructionId) {
        if (instructionId != null) {
            isLoading = true
            error = null
            
            val api = ApiClient.apiService
            val token = RepositoryFactory.getTokenRepository(context).getTokenSync()
            
            if (token != null) {
                runCatching {
                    api.getBotInstructionList(token, BotInstructionRequest(botId))
                }.onSuccess { response ->
                    if (response.body()?.code == 1) {
                        val foundInstruction = response.body()?.data?.list?.find { it.id == instructionId }
                        if (foundInstruction != null) {
                            instruction = foundInstruction
                            name = foundInstruction.name
                            desc = foundInstruction.desc ?: ""
                            instructionType = foundInstruction.instructionType
                            hintText = foundInstruction.hintText ?: ""
                            defaultText = foundInstruction.defaultText ?: ""
                            customFields = parseCustomFields(foundInstruction.customJson)
                        } else {
                            error = "指令不存在"
                        }
                    } else {
                        error = response.body()?.msg ?: "加载失败"
                    }
                    isLoading = false
                }.onFailure { e ->
                    error = e.message ?: "未知错误"
                    isLoading = false
                }
            } else {
                error = "未登录"
                isLoading = false
            }
        }
    }
    
    // 保存指令
    fun saveInstruction() {
        if (name.isBlank()) {
            Toast.makeText(context, "请输入指令名称", Toast.LENGTH_SHORT).show()
            return
        }
        if (instructionType == 5 && customFields.isEmpty()) {
            Toast.makeText(context, "请至少添加一个自定义字段", Toast.LENGTH_SHORT).show()
            return
        }
        
        scope.launch {
            isSaving = true
            val api = ApiClient.apiService
            val token = RepositoryFactory.getTokenRepository(context).getTokenSync()
            
            if (token != null) {
                val customJson = if (instructionType == 5) buildCustomJson(customFields) else null
                runCatching {
                    if (isEdit && instruction != null) {
                        api.editBotInstruction(
                            token,
                            com.yhchat.canary.data.model.EditInstructionRequest(
                                id = instruction!!.id,
                                botId = botId,
                                name = name,
                                desc = desc.takeIf { it.isNotBlank() },
                                type = instructionType,
                                hintText = hintText.takeIf { it.isNotBlank() },
                                defaultText = defaultText.takeIf { it.isNotBlank() },
                                customJson = customJson
                            )
                        )
                    } else {
                        api.createBotInstruction(
                            token,
                            com.yhchat.canary.data.model.CreateInstructionRequest(
                                botId = botId,
                                name = name,
                                desc = desc,
                                type = instructionType,
                                hintText = hintText.takeIf { it.isNotBlank() },
                                defaultText = defaultText.takeIf { it.isNotBlank() },
                                customJson = customJson
                            )
                        )
                    }
                }.onSuccess { response ->
                    if (response.body()?.code == 1) {
                        Toast.makeText(
                            context, 
                            if (isEdit) "编辑成功" else "创建成功", 
                            Toast.LENGTH_SHORT
                        ).show()
                        onSaveSuccess()
                    } else {
                        Toast.makeText(
                            context,
                            response.body()?.message ?: "操作失败",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    isSaving = false
                }.onFailure { e ->
                    Toast.makeText(context, "网络错误: ${e.message}", Toast.LENGTH_SHORT).show()
                    isSaving = false
                }
            } else {
                isSaving = false
                Toast.makeText(context, "未登录", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEdit) "编辑指令 - $botName" else "创建指令 - $botName",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = { saveInstruction() },
                        enabled = !isSaving && !isLoading
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (isEdit) "保存" else "创建")
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
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                
                error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = error!!,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = onBackClick) {
                                Text("返回")
                            }
                        }
                    }
                }
                
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 基本信息卡片
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "基本信息",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                
                                // 指令名称
                                OutlinedTextField(
                                    value = name,
                                    onValueChange = { name = it },
                                    label = { Text("指令名称") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                
                                // 指令描述
                                OutlinedTextField(
                                    value = desc,
                                    onValueChange = { desc = it },
                                    label = { Text("指令描述") },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 2,
                                    maxLines = 4
                                )
                                
                                // 指令类型
                                Column {
                                    Text(
                                        text = "指令类型",
                                        style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            FilterChip(
                                                selected = instructionType == 1,
                                                onClick = { instructionType = 1 },
                                                label = { Text("普通指令") }
                                            )
                                            FilterChip(
                                                selected = instructionType == 2,
                                                onClick = { instructionType = 2 },
                                                label = { Text("直发指令") }
                                            )
                                            FilterChip(
                                                selected = instructionType == 5,
                                                onClick = { instructionType = 5 },
                                                label = { Text("自定义输入") }
                                            )
                                        }
                                    }
                                }
                                
                                // 对于普通指令和自定义输入指令，显示额外字段
                                if (instructionType == 1 || instructionType == 5) {
                                    OutlinedTextField(
                                        value = hintText,
                                        onValueChange = { hintText = it },
                                        label = { Text("输入框提示文字") },
                                        placeholder = { Text("例如: 请输入内容...") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                    
                                    OutlinedTextField(
                                        value = defaultText,
                                        onValueChange = { defaultText = it },
                                        label = { Text("输入框默认文字") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                }
                            }
                        }
                        
                        // 自定义字段配置（仅自定义输入指令）
                        if (instructionType == 5) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "自定义字段",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        
                                        var showAddFieldMenu by remember { mutableStateOf(false) }
                                        
                                        Box {
                                            IconButton(onClick = { showAddFieldMenu = true }) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = "添加字段"
                                                )
                                            }
                                            
                                            DropdownMenu(
                                                expanded = showAddFieldMenu,
                                                onDismissRequest = { showAddFieldMenu = false }
                                            ) {
                                                CustomFieldType.values().forEach { type ->
                                                    DropdownMenuItem(
                                                        text = { Text(type.displayName) },
                                                        onClick = {
                                                            customFields = customFields + InstructionFormField(type = type)
                                                            showAddFieldMenu = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    
                                    if (customFields.isEmpty()) {
                                        Text(
                                            text = "点击右上角 + 添加字段",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        customFields.forEachIndexed { index, field ->
                                            CustomFieldEditCard(
                                                field = field,
                                                onUpdate = { updated -> 
                                                    customFields = customFields.mapIndexed { i, f -> 
                                                        if (i == index) updated else f 
                                                    }
                                                },
                                                onRemove = { 
                                                    customFields = customFields.filterIndexed { i, _ -> i != index }
                                                }
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
    }
}

@Composable
private fun CustomFieldEditCard(
    field: InstructionFormField,
    onUpdate: (InstructionFormField) -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = field.type.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "删除字段",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            OutlinedTextField(
                value = field.label,
                onValueChange = { onUpdate(field.copy(label = it)) },
                label = { Text("字段标签") },
                placeholder = { Text("例如：姓名 / 选择项") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            if (field.type.supportsPlaceholder) {
                OutlinedTextField(
                    value = field.placeholder,
                    onValueChange = { onUpdate(field.copy(placeholder = it)) },
                    label = { Text("占位文本") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            
            if (field.type.requiresOptions) {
                OutlinedTextField(
                    value = field.options,
                    onValueChange = { onUpdate(field.copy(options = it)) },
                    label = { Text("选项（使用 # 分隔）") },
                    placeholder = { Text("北京#上海#广州") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3
                )
            }
            
            if (field.type == CustomFieldType.SWITCH) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("默认状态")
                    val checked = field.defaultValue == "1"
                    Switch(
                        checked = checked,
                        onCheckedChange = { onUpdate(field.copy(defaultValue = if (it) "1" else "0")) }
                    )
                }
            } else if (field.type.supportsDefault) {
                OutlinedTextField(
                    value = field.defaultValue,
                    onValueChange = { onUpdate(field.copy(defaultValue = it)) },
                    label = { Text("默认值") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }
    }
}

