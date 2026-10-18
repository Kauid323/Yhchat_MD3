package com.yhchat.canary.ui.community

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.yhchat.canary.data.di.RepositoryFactory
import com.yhchat.canary.data.model.CommunityGroup
import com.yhchat.canary.ui.theme.YhchatCanaryTheme

/**
 * 群聊列表Activity - 显示分区下的群聊列表
 */
class GroupListActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.yhchat.canary.ui.base.SystemBarUtils.setupTransparentSystemBars(this)
        
        val boardId = intent.getIntExtra("board_id", 0)
        val boardName = intent.getStringExtra("board_name") ?: "群聊列表"
        val token = intent.getStringExtra("token") ?: ""
        
        setContent {
            YhchatCanaryTheme {
                val viewModel: GroupListViewModel = viewModel {
                    val conversationRepository = RepositoryFactory.getConversationRepository(this@GroupListActivity)
                    val tokenRepository = RepositoryFactory.getTokenRepository(this@GroupListActivity)
                    conversationRepository.setTokenRepository(tokenRepository)

                    GroupListViewModel(
                        communityRepository = RepositoryFactory.getCommunityRepository(this@GroupListActivity),
                        friendRepository = RepositoryFactory.getFriendRepository(this@GroupListActivity),
                        conversationRepository = conversationRepository
                    )
                }

                GroupListScreen(
                    boardId = boardId,
                    boardName = boardName,
                    token = token,
                    viewModel = viewModel,
                    onBackClick = { finish() }
                )
            }
        }
    }
}

/**
 * 群聊列表界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupListScreen(
    boardId: Int,
    boardName: String,
    token: String,
    viewModel: GroupListViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val groupListState by viewModel.groupListState.collectAsState()
    var selectedGroup by remember { mutableStateOf<CommunityGroup?>(null) }
    // 加载数据
    LaunchedEffect(boardId, token) {
        if (token.isNotEmpty() && boardId > 0) {
            viewModel.loadGroupList(token, boardId)
        }
    }
    
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "$boardName - 群聊列表",
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
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = groupListState.isLoading,
            onRefresh = { viewModel.loadGroupList(token, boardId) },
            state = rememberPullToRefreshState(),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
        
        // 错误提示
        groupListState.error?.let { error ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        
        // 群聊列表
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(groupListState.groups) { group ->
                GroupListItem(
                    group = group,
                    onClick = { selectedGroup = group }
                )
            }
            
            // 加载更多按钮
            if (groupListState.groups.isNotEmpty() && groupListState.hasMore) {
                item {
                    Button(
                        onClick = { viewModel.loadMoreGroups(token, boardId) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        enabled = !groupListState.isLoading
                    ) {
                        if (groupListState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (groupListState.isLoading) "加载�?.." else "加载更多")
                    }
                }
            }
            
            if (groupListState.groups.isEmpty() && !groupListState.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "该分区暂无绑定群聊",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            if (groupListState.isLoading && groupListState.groups.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
            }
        }
    }
    }
    
    // 监听申请状态变化
    LaunchedEffect(selectedGroup) {
        if (selectedGroup != null) {
            viewModel.joinRequestState.collect { state ->
                if (state.isInConversations) {
                    // 已在通讯录中，直接进入聊天
                    val group = selectedGroup
                    if (group != null) {
                        val intent = android.content.Intent(context, com.yhchat.canary.ui.chat.ChatActivity::class.java).apply {
                            putExtra("chatId", group.groupId)
                            putExtra("chatType", 2)
                            putExtra("chatName", group.name)
                        }
                        context.startActivity(intent)
                    }
                    selectedGroup = null
                    viewModel.resetJoinRequestState()
                } else if (state.isSuccess) {
                    // 申请成功，延迟关闭弹窗
                    kotlinx.coroutines.delay(2000)
                    selectedGroup = null
                    viewModel.resetJoinRequestState()
                }
            }
        }
    }
    
    // 群聊详情弹窗
    selectedGroup?.let { group ->
        GroupDetailDialog(
            group = group,
            token = token,
            viewModel = viewModel,
            onDismiss = { 
                selectedGroup = null
                viewModel.resetJoinRequestState()
            },
             onJoinRequest = { groupId ->
                 viewModel.handleGroupJoin(token, groupId)
             }
        )
    }
}

/**
 * 群聊列表
  */
@Composable
fun GroupListItem(
    group: CommunityGroup,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 群聊头像
            AsyncImage(
                model = group.avatarUrl,
                contentDescription = group.name,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 群聊信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                if (group.introduction.isNotEmpty()) {
                    Text(
                        text = group.introduction,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                }
                
                Text(
                    text = "${group.headcount} 人",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 群聊详情弹窗
 */
@Composable
fun GroupDetailDialog(
    group: CommunityGroup,
    token: String,
    viewModel: GroupListViewModel,
    onDismiss: () -> Unit,
    onJoinRequest: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val joinRequestState by viewModel.joinRequestState.collectAsState()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = {
            Text(
                text = "群聊详情",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                // 群聊头像和名称
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = group.avatarUrl,
                        contentDescription = group.name,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column {
                        Text(
                            text = group.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            text = "${group.headcount} 人",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 群聊简介
                if (group.introduction.isNotEmpty()) {
                    Text(
                        text = "群聊简介",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = group.introduction,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                // 群聊设置
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column {
                        Text(
                            text = "历史记录",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (group.readHistory == 1) "开启" else "关闭",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Column {
                        Text(
                            text = "直接进群",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (group.alwaysAgree == 1) "开启" else "关闭",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // 错误提示
                joinRequestState.error?.let { error ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                
                // 成功提示
                if (joinRequestState.isSuccess) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "申请已发送",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
         confirmButton = {
             Button(
                 onClick = { onJoinRequest(group.groupId) },
                 enabled = !joinRequestState.isLoading && !joinRequestState.isSuccess && !joinRequestState.isChecking
             ) {
                 if (joinRequestState.isLoading || joinRequestState.isChecking) {
                     CircularProgressIndicator(
                         modifier = Modifier.size(16.dp),
                         strokeWidth = 2.dp
                     )
                     Spacer(modifier = Modifier.width(8.dp))
                 }
                 Text(
                     text = when {
                         joinRequestState.isChecking -> "检查中..."
                         joinRequestState.isLoading -> "申请中..."
                         joinRequestState.isSuccess -> "已申请"
                         joinRequestState.isInConversations -> "进入聊天"
                         else -> "进入群聊"
                     }
                 )
             }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}
