package com.yhchat.canary.ui.group

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.yhchat.canary.data.model.GroupMemberInfo
import com.yhchat.canary.data.repository.TagMemberInfo
import com.yhchat.canary.ui.components.ImageUtils
import com.yhchat.canary.ui.theme.YhchatCanaryTheme
import com.yhchat.canary.ui.user.UserDetailActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class GroupTagDetailActivity : ComponentActivity() {
    
    private val viewModel: GroupTagDetailViewModel by viewModels()
    
    companion object {
        private const val EXTRA_GROUP_ID = "group_id"
        private const val EXTRA_TAG_ID = "tag_id"
        private const val EXTRA_TAG_NAME = "tag_name"
        
        fun start(context: Context, groupId: String, tagId: Long, tagName: String) {
            val intent = Intent(context, GroupTagDetailActivity::class.java).apply {
                putExtra(EXTRA_GROUP_ID, groupId)
                putExtra(EXTRA_TAG_ID, tagId)
                putExtra(EXTRA_TAG_NAME, tagName)
            }
            context.startActivity(intent)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.yhchat.canary.ui.base.SystemBarUtils.setupTransparentSystemBars(this)
        
        val groupId = intent.getStringExtra(EXTRA_GROUP_ID) ?: run {
            finish()
            return
        }
        val tagId = intent.getLongExtra(EXTRA_TAG_ID, 0L)
        val tagName = intent.getStringExtra(EXTRA_TAG_NAME) ?: "标签"
        
        setContent {
            YhchatCanaryTheme {
                GroupTagDetailScreen(
                    groupId = groupId,
                    tagId = tagId,
                    tagName = tagName,
                    viewModel = viewModel,
                    onBackClick = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupTagDetailScreen(
    groupId: String,
    tagId: Long,
    tagName: String,
    viewModel: GroupTagDetailViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    LaunchedEffect(groupId, tagId) {
        viewModel.loadMembers(groupId, tagId)
        viewModel.loadGroupMembers(groupId)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.total > 0) "$tagName · ${uiState.total}位成员" else tagName,
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
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.openAddMemberDialog() }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "添加成员"
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.error != null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = uiState.error ?: "加载失败",
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadMembers(groupId, tagId) }) {
                            Text("重试")
                        }
                    }
                }
                uiState.members.isEmpty() -> {
                    Text(
                        text = "该标签还没有绑定成员",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.members) { member ->
                            TagMemberCard(
                                member = member,
                                onClick = {
                                    UserDetailActivity.start(context = context, userId = member.userId, userName = member.name, groupId = groupId)
                                },
                                onRemoveClick = {
                                    viewModel.requestRemoveMember(member)
                                }
                            )
                        }
                    }
                }
            }
        }
        
        // 添加成员对话框
        if (uiState.showAddMemberDialog) {
            AddMemberToTagDialog(
                currentMembers = uiState.members,
                groupMembers = uiState.groupMembers,
                searchQuery = uiState.addMemberSearchQuery,
                isLoadingGroupMembers = uiState.isLoadingGroupMembers,
                hasMoreGroupMembers = uiState.hasMoreGroupMembers,
                isLoadingMoreGroupMembers = uiState.isLoadingMoreGroupMembers,
                onSearchQueryChange = viewModel::updateAddMemberSearchQuery,
                onAddMember = { userId ->
                    viewModel.addTagToUser(userId, tagId, groupId)
                },
                onLoadMoreGroupMembers = {
                    viewModel.loadMoreGroupMembers(groupId)
                },
                onDismiss = { viewModel.dismissAddMemberDialog() }
            )
        }

        uiState.pendingRemoveMember?.let { pendingMember ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissRemoveMemberDialog() },
                title = { Text("移除标签") },
                text = { Text("确定要将 ${pendingMember.name} 从该标签中移除吗？") },
                confirmButton = {
                    Button(onClick = { viewModel.confirmRemoveMember(tagId, groupId) }) {
                        Text("移除")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissRemoveMemberDialog() }) {
                        Text("取消")
                    }
                }
            )
        }
    }
}

@Composable
fun AddMemberToTagDialog(
    currentMembers: List<TagMemberInfo>,
    groupMembers: List<GroupMemberInfo>,
    searchQuery: String,
    isLoadingGroupMembers: Boolean,
    hasMoreGroupMembers: Boolean,
    isLoadingMoreGroupMembers: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onAddMember: (String) -> Unit,
    onLoadMoreGroupMembers: () -> Unit,
    onDismiss: () -> Unit
) {

    // 过滤掉已经在标签中的成员
    val currentMemberIds = currentMembers.map { it.userId }.toSet()

    val availableMembers = groupMembers
        .filter { !currentMemberIds.contains(it.userId) }
        .filter { member ->
            if (searchQuery.isBlank()) true
            else member.name.contains(searchQuery, ignoreCase = true) ||
                 member.userId.contains(searchQuery)
        }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("添加成员到标签")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("搜索成员...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            ) {
                when {
                    isLoadingGroupMembers -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    availableMembers.isEmpty() && !isLoadingGroupMembers -> {
                        Text(
                            text = if (searchQuery.isBlank()) "没有可添加的成员" else "未找到匹配的成员",
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(availableMembers) { member ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onAddMember(member.userId)
                                            onDismiss()
                                        },
                                    shape = RoundedCornerShape(24.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = ImageUtils.createAvatarImageRequest(LocalContext.current, member.avatarUrl),
                                            contentDescription = "头像",
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                        
                                        Spacer(modifier = Modifier.width(12.dp))
                                        
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = member.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                
                                                // 权限标识
                                                if (member.permissionLevel == 100) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Surface(
                                                        shape = MaterialTheme.shapes.small,
                                                        color = MaterialTheme.colorScheme.errorContainer
                                                    ) {
                                                        Text(
                                                            text = "群主",
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onErrorContainer
                                                        )
                                                    }
                                                } else if (member.permissionLevel == 2) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Surface(
                                                        shape = MaterialTheme.shapes.small,
                                                        color = MaterialTheme.colorScheme.primaryContainer
                                                    ) {
                                                        Text(
                                                            text = "管理员",
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                                        )
                                                    }
                                                }
                                            }
                                            
                                            Text(
                                                text = "ID: ${member.userId}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                            
                            // 加载更多指示器
                            if (hasMoreGroupMembers) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isLoadingMoreGroupMembers) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                        } else {
                                            TextButton(
                                                onClick = onLoadMoreGroupMembers
                                            ) {
                                                Text("加载更多")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun TagMemberCard(
    member: TagMemberInfo,
    onClick: () -> Unit,
    onRemoveClick: () -> Unit
) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 头像
            AsyncImage(
                model = ImageUtils.createAvatarImageRequest(LocalContext.current, member.avatarUrl),
                contentDescription = "头像",
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 用户信息
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = member.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    
                    // 权限标识
                    if (member.permissionLevel == 100) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = "群主",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    } else if (member.permissionLevel == 2) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "管理员",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                
                // 禁言状态
                if (member.isGag) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "已禁言",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            // 移除按钮
            IconButton(onClick = onRemoveClick) {
                Icon(
                    imageVector = Icons.Default.PersonRemove,
                    contentDescription = "移除标签",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
