@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.yhchat.canary.ui.discover

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yhchat.canary.data.api.ApiClient
import com.yhchat.canary.data.di.RepositoryFactory
import com.yhchat.canary.data.model.AddFriendRequest
import com.yhchat.canary.data.model.BotDetail
import com.yhchat.canary.data.model.BotDetailGroup
import com.yhchat.canary.data.model.BotDetailRequest
import com.yhchat.canary.ui.chat.ChatActivity
import com.yhchat.canary.ui.components.ImageUtils
import com.yhchat.canary.ui.theme.YhchatCanaryTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BotDetailActivity : ComponentActivity() {
    
    companion object {
        private const val EXTRA_BOT_ID = "bot_id"
        
        fun start(context: Context, botId: String) {
            val intent = Intent(context, BotDetailActivity::class.java)
            intent.putExtra(EXTRA_BOT_ID, botId)
            context.startActivity(intent)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.yhchat.canary.ui.base.SystemBarUtils.setupTransparentSystemBars(this)
        
        val botId = intent.getStringExtra(EXTRA_BOT_ID) ?: run {
            Toast.makeText(this, "参数错误", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        setContent {
            YhchatCanaryTheme {
                BotDetailScreen(
                    botId = botId,
                    onBackClick = { finish() }
                )
            }
        }
    }
}

@Composable
fun BotDetailScreen(
    botId: String,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val friendRepository = remember { RepositoryFactory.getFriendRepository(context) }
    
    var bot by remember { mutableStateOf<BotDetail?>(null) }
    var groups by remember { mutableStateOf<List<BotDetailGroup>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var isAddingBot by remember { mutableStateOf(false) }
    var addingGroupId by remember { mutableStateOf<String?>(null) }
    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    
    // 加载机器人详情
    LaunchedEffect(botId) {
        isLoading = true
        error = null
        
        val api = ApiClient.apiService
        val tokenRepo = RepositoryFactory.getTokenRepository(context)
        val tokenFlow = tokenRepo.getToken()
        val token = tokenFlow.first()?.token
        
        if (token != null) {
            runCatching {
                api.getBotDetail(token, BotDetailRequest(id = botId))
            }.onSuccess { response ->
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.code == 1 && body.data != null) {
                        bot = body.data.bot
                        groups = body.data.groups ?: emptyList()
                    } else {
                        error = body?.msg ?: "加载失败"
                    }
                } else {
                    error = "网络请求失败: ${response.code()}"
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
    
    // 添加机器人
    fun addBot() {
        scope.launch {
            isAddingBot = true
            val inContacts = friendRepository.getAddressBookList()
                .getOrNull()
                ?.dataList
                ?.any { data -> data.dataList.any { it.chatId == bot?.botId } } == true
            if (inContacts && bot != null) {
                openChat(context, bot!!.botId, 3, bot!!.nickname)
                isAddingBot = false
                return@launch
            }
            val api = ApiClient.apiService
            val tokenRepo = RepositoryFactory.getTokenRepository(context)
            val tokenFlow = tokenRepo.getToken()
            val token = tokenFlow.first()?.token
            
            if (token != null && bot != null) {
                runCatching {
                    api.addFriend(
                        token,
                        AddFriendRequest(
                            chatId = bot!!.botId,
                            chatType = 3,
                            remark = ""
                        )
                    )
                }.onSuccess { response ->
                    if (response.isSuccessful && response.body()?.code == 1) {
                        Toast.makeText(context, "已添加机器人", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(
                            context,
                            response.body()?.message ?: "添加失败",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    isAddingBot = false
                }.onFailure {
                    Toast.makeText(context, "网络错误", Toast.LENGTH_SHORT).show()
                    isAddingBot = false
                }
            } else {
                isAddingBot = false
            }
        }
    }
    
    // 添加群聊
    fun addGroup(groupId: String) {
        scope.launch {
            addingGroupId = groupId
            val inContacts = friendRepository.getAddressBookList()
                .getOrNull()
                ?.dataList
                ?.any { data -> data.dataList.any { it.chatId == groupId } } == true
            if (inContacts) {
                val groupName = groups.firstOrNull { it.groupId == groupId }?.name ?: ""
                openChat(context, groupId, 2, groupName)
                addingGroupId = null
                return@launch
            }
            val api = ApiClient.apiService
            val tokenRepo = RepositoryFactory.getTokenRepository(context)
            val tokenFlow = tokenRepo.getToken()
            val token = tokenFlow.first()?.token
            
            if (token != null) {
                runCatching {
                    api.addFriend(
                        token,
                        AddFriendRequest(
                            chatId = groupId,
                            chatType = 2,
                            remark = ""
                        )
                    )
                }.onSuccess { response ->
                    if (response.isSuccessful && response.body()?.code == 1) {
                        Toast.makeText(context, "已发送加群申请", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(
                            context,
                            response.body()?.message ?: "申请失败",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    addingGroupId = null
                }.onFailure {
                    Toast.makeText(context, "网络错误", Toast.LENGTH_SHORT).show()
                    addingGroupId = null
                }
            } else {
                addingGroupId = null
            }
        }
    }
    
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "机器人详情",
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                ),
                scrollBehavior = scrollBehavior
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
                
                bot != null -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 机器人信息卡片
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    // 头像
                                    if (bot!!.avatarUrl.isNotEmpty()) {
                                        AsyncImage(
                                            model = ImageUtils.createBotImageRequest(context, bot!!.avatarUrl),
                                            contentDescription = "机器人头头像",
                                            modifier = Modifier
                                                .size(80.dp)
                                                .clip(CircleShape)
                                                .clickable {
                                                    previewImageUrl = bot!!.avatarUrl
                                                },
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(80.dp)
                                                .clip(CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.SmartToy,
                                                contentDescription = "默认头像",
                                                modifier = Modifier.size(48.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    // 名称
                                    Text(
                                        text = bot!!.nickname,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    // ID
                                    Text(
                                        text = "ID: ${bot!!.botId}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    // 创建者         
                                    Text(
                                        text = "创建者 ${bot!!.createBy}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    
                                     bot!!.introduction?.takeIf { it.isNotEmpty() }?.let { intro ->
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = intro,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(12.dp))
                                    
                                    // 使用人数
                                    Text(
                                        text = "${bot!!.headcount}人使用",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    // 添加按钮
                                    Button(
                                        onClick = { addBot() },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = !isAddingBot
                                    ) {
                                        if (isAddingBot) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                        Text("添加机器人")
                                    }
                                }
                            }
                        }
                        
                        // 绑定该机器人的群聊
                        if (groups.isNotEmpty()) {
                            item {
                                Text(
                                    text = "绑定该机器人的群聊",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            // 群聊列表
                            items(groups) { group ->
                                GroupListItem(
                                    group = group,
                                    isAdding = addingGroupId == group.groupId,
                                    onAddClick = { addGroup(group.groupId) },
                                    onAvatarClick = {
                                        previewImageUrl = group.avatarUrl
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    previewImageUrl?.let { imageUrl ->
        com.yhchat.canary.ui.components.ImageViewer(
            imageUrl = imageUrl,
            onDismiss = { previewImageUrl = null }
        )
    }
}

@Composable
fun GroupListItem(
    group: BotDetailGroup,
    isAdding: Boolean,
    onAddClick: () -> Unit,
    onAvatarClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 群聊头像
            if (!group.avatarUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = ImageUtils.createAvatarImageRequest(LocalContext.current, group.avatarUrl),
                    contentDescription = "群聊头像",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable {
                            onAvatarClick()
                        },
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = "默认头像",
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 群聊信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (!group.introduction.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = group.introduction,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // 添加按钮
            Button(
                onClick = onAddClick,
                enabled = !isAdding,
                modifier = Modifier.height(36.dp)
            ) {
                if (isAdding) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("加入")
                }
            }
        }
    }
}

private fun openChat(context: Context, chatId: String, chatType: Int, chatName: String) {
    val intent = Intent(context, ChatActivity::class.java).apply {
        putExtra("chatId", chatId)
        putExtra("chatType", chatType)
        putExtra("chatName", chatName)
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
    context.startActivity(intent)
}
