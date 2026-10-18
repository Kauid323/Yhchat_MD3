@file:OptIn(ExperimentalMaterial3Api::class)
package com.yhchat.canary.ui.create

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yhchat.canary.ui.base.BaseActivity
import com.yhchat.canary.ui.theme.YhchatCanaryTheme

/**
 * 创建群聊/机器人 Activity
 */
class CreateActivity : BaseActivity() {
    private val viewModel: CreateViewModel by viewModels()
    
    companion object {
        private const val EXTRA_INITIAL_TAB = "initial_tab"
        const val TAB_GROUP = 0
        const val TAB_BOT = 1
        
        fun start(context: Context, initialTab: Int = TAB_GROUP) {
            val intent = Intent(context, CreateActivity::class.java).apply {
                putExtra(EXTRA_INITIAL_TAB, initialTab)
            }
            context.startActivity(intent)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val initialTab = intent.getIntExtra(EXTRA_INITIAL_TAB, TAB_GROUP)
        
        setContent {
            YhchatCanaryTheme {
                CreateScreen(
                    initialTab = initialTab,
                    viewModel = viewModel,
                    onBackClick = { finish() }
                )
            }
        }
    }
}

@Composable
fun CreateScreen(
    initialTab: Int = CreateActivity.TAB_GROUP,
    viewModel: CreateViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    
    LaunchedEffect(Unit) {
        viewModel.init(context)
    }
    
    var selectedTab by remember { mutableStateOf(initialTab) }
    val tabs = listOf("创建群聊", "创建机器人")
    
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "创建",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 选项卡
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                tabs.forEachIndexed { index, label ->
                    FilterChip(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        label = { Text(label) },
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
            
            // 内容区域
            when (selectedTab) {
                CreateActivity.TAB_GROUP -> {
                    CreateGroupContent(
                        viewModel = viewModel,
                        onSuccess = { groupId ->
                            Toast.makeText(context, "群聊创建成功！", Toast.LENGTH_SHORT).show()
                            onBackClick()
                        }
                    )
                }
                CreateActivity.TAB_BOT -> {
                    CreateBotContent(
                        viewModel = viewModel,
                        onSuccess = { botId ->
                            Toast.makeText(context, "机器人创建成功！", Toast.LENGTH_SHORT).show()
                            onBackClick()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CreateGroupContent(
    viewModel: CreateViewModel,
    onSuccess: (String) -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.groupUiState.collectAsState()
    
    var name by remember { mutableStateOf("") }
    var introduction by remember { mutableStateOf("") }
    var avatarUrl by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf(0) }
    
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.uploadGroupAvatar(context, it) { url ->
                avatarUrl = url
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 群聊头像
        Surface(
            modifier = Modifier
                .size(100.dp)
                .align(Alignment.CenterHorizontally),
            shape = MaterialTheme.shapes.large,
            onClick = { imagePickerLauncher.launch("image/*") }
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (avatarUrl.isNotEmpty()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = "群聊头像",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "添加头像",
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
        
        Text(
            text = "点击上方添加群聊头像",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        
        // 群聊名称
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("群聊名称 *") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        // 群聊简介
        OutlinedTextField(
            value = introduction,
            onValueChange = { introduction = it },
            label = { Text("群聊简介") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 5
        )
        
        // 分类名称
        OutlinedTextField(
            value = category,
            onValueChange = { category = it },
            label = { Text("分类名称") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        // 分类ID
        OutlinedTextField(
            value = if (categoryId == 0) "" else categoryId.toString(),
            onValueChange = { categoryId = it.toIntOrNull() ?: 0 },
            label = { Text("分类ID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 创建按钮
        Button(
            onClick = {
                if (name.isBlank()) {
                    Toast.makeText(context, "请输入群聊名称", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                viewModel.createGroup(
                    name = name,
                    introduction = introduction,
                    avatarUrl = avatarUrl,
                    category = category,
                    categoryId = categoryId,
                    onSuccess = onSuccess
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isLoading
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Text("创建群聊")
            }
        }
        
        // 错误提示
        if (uiState.error != null) {
            Text(
                text = uiState.error ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun CreateBotContent(
    viewModel: CreateViewModel,
    onSuccess: (String) -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.botUiState.collectAsState()
    
    var name by remember { mutableStateOf("") }
    var introduction by remember { mutableStateOf("") }
    var avatarUrl by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(false) }
    
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.uploadBotAvatar(context, it) { url ->
                avatarUrl = url
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 机器人头像
        Surface(
            modifier = Modifier
                .size(100.dp)
                .align(Alignment.CenterHorizontally),
            shape = MaterialTheme.shapes.large,
            onClick = { imagePickerLauncher.launch("image/*") }
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (avatarUrl.isNotEmpty()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = "机器人头像",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "添加头像",
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
        
        Text(
            text = "点击上方添加机器人头像",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        
        // 机器人名称
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("机器人名称 *") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        // 机器人简介
        OutlinedTextField(
            value = introduction,
            onValueChange = { introduction = it },
            label = { Text("机器人简介") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 5
        )
        
        // 是否私有
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("私有机器人")
            Switch(
                checked = isPrivate,
                onCheckedChange = { isPrivate = it }
            )
        }
        
        Text(
            text = "私有机器人只有你可以使用",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 创建按钮
        Button(
            onClick = {
                if (name.isBlank()) {
                    Toast.makeText(context, "请输入机器人名称", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                viewModel.createBot(
                    name = name,
                    introduction = introduction,
                    avatarUrl = avatarUrl,
                    isPrivate = isPrivate,
                    onSuccess = onSuccess
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isLoading
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Text("创建机器人")
            }
        }
        
        // 错误提示
        if (uiState.error != null) {
            Text(
                text = uiState.error ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
