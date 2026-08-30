package com.yhchat.canary.ui.group

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import com.yhchat.canary.ui.adaptive.YhAlertDialog
import com.yhchat.canary.ui.adaptive.YhButton
import com.yhchat.canary.ui.adaptive.YhCard
import com.yhchat.canary.ui.adaptive.YhCircularProgressIndicator
import com.yhchat.canary.ui.adaptive.YhDropdownMenu
import com.yhchat.canary.ui.adaptive.YhDropdownMenuItem
import com.yhchat.canary.ui.adaptive.YhIcon as Icon
import com.yhchat.canary.ui.adaptive.YhIconButton
import com.yhchat.canary.ui.adaptive.YhOutlinedTextField
import com.yhchat.canary.ui.adaptive.YhScaffold
import com.yhchat.canary.ui.adaptive.YhSurface
import com.yhchat.canary.ui.adaptive.YhText as Text
import com.yhchat.canary.ui.adaptive.YhTextButton
import com.yhchat.canary.ui.adaptive.YhTopAppBar
import com.yhchat.canary.ui.components.ImageUtils
import com.yhchat.canary.ui.user.UserDetailActivity

@Composable
fun GroupMembersScreen(
    groupId: String,
    groupName: String,
    viewModel: GroupInfoViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var isSearching by remember { mutableStateOf(false) }
    var searchKeyword by remember { mutableStateOf("") }
    
    LaunchedEffect(groupId) {
        viewModel.loadGroupInfo(groupId)
    }
    
    // 搜索时自动触发
    LaunchedEffect(searchKeyword) {
        if (isSearching) {
            viewModel.searchMembers(groupId, searchKeyword)
        }
    }
    
    YhScaffold(
        topBar = {
            YhTopAppBar(
                title = { 
                    if (!isSearching) {
                        Column {
                            Text(
                                text = "群成员",
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "已加载 ${uiState.members.size} 人",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        YhOutlinedTextField(
                            value = searchKeyword,
                            onValueChange = { searchKeyword = it },
                            placeholder = { Text("搜索群成员...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                },
                navigationIcon = {
                    YhIconButton(onClick = {
                        if (isSearching) {
                            isSearching = false
                            searchKeyword = ""
                            viewModel.clearSearch(groupId)
                        } else {
                            onBackClick()
                        }
                    }) {
                        Icon(
                            imageVector = if (isSearching) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isSearching) "取消搜索" else "返回"
                        )
                    }
                },
                actions = {
                    if (!isSearching) {
                        YhIconButton(onClick = { isSearching = true }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "搜索"
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading -> {
                    YhCircularProgressIndicator(
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
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        YhButton(onClick = { viewModel.loadGroupInfo(groupId) }) {
                            Text("重试")
                        }
                    }
                }
                else -> {
                    GroupMembersContent(
                        groupId = groupId,
                        members = uiState.members,
                        isLoadingMembers = uiState.isLoadingMembers,
                        isLoadingMoreMembers = uiState.isLoadingMoreMembers,
                        hasMoreMembers = uiState.hasMoreMembers,
                        currentUserPermission = uiState.groupInfo?.permissionLevel?.toInt() ?: 0,
                        onLoadMore = { viewModel.loadMoreMembers(groupId) },
                        onRemoveMember = { userId -> viewModel.removeMember(groupId, userId) },
                        onGagMember = { userId, gagTime -> viewModel.gagMember(groupId, userId, gagTime) },
                        onSetMemberRole = { userId, userLevel -> viewModel.setMemberRole(groupId, userId, userLevel) },
                        onTransferGroupOwner = { userId -> viewModel.transferGroupOwner(groupId, userId) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
    
    // 显示成功消息
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearSuccessMessage()
        }
    }
    
    // 显示错误消息
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }
}

@Composable
private fun GroupMembersContent(
    groupId: String,
    members: List<GroupMemberInfo>,
    isLoadingMembers: Boolean,
    isLoadingMoreMembers: Boolean,
    hasMoreMembers: Boolean,
    currentUserPermission: Int = 0,
    onLoadMore: () -> Unit,
    onRemoveMember: (String) -> Unit = {},
    onGagMember: (String, Int) -> Unit = { _, _ -> },
    onSetMemberRole: (String, Int) -> Unit = { _, _ -> },
    onTransferGroupOwner: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    
    // 检测滚动到底部并加载更多
    LaunchedEffect(listState, isLoadingMembers, isLoadingMoreMembers, hasMoreMembers) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            val lastVisibleItem = visibleItems.lastOrNull()
            val totalItemsCount = layoutInfo.totalItemsCount
            
            // 判断是否滚动到了最后一个item
            lastVisibleItem != null && lastVisibleItem.index == totalItemsCount - 1
        }.collect { isAtBottom ->
            // 当滚动到底部且没在加载时，触发加载更多
            if (isAtBottom && !isLoadingMembers && !isLoadingMoreMembers && hasMoreMembers) {
                android.util.Log.d("GroupMembersScreen", "检测到滚动到底部，触发加载更多")
                onLoadMore()
            }
        }
    }
    
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isLoadingMembers && members.isEmpty()) {
            // 首次加载显示加载指示器
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    YhCircularProgressIndicator()
                }
            }
        } else {
            // 显示成员列表
            items(members) { member ->
                MemberItem(
                    member = member,
                    currentUserPermission = currentUserPermission,
                    groupId = groupId,
                    onRemoveMember = onRemoveMember,
                    onGagMember = onGagMember,
                    onSetMemberRole = onSetMemberRole,
                    onTransferGroupOwner = onTransferGroupOwner
                )
            }
            
            // 加载更多指示器
            if (isLoadingMoreMembers) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            YhCircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "加载更多成员...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else if (!hasMoreMembers && members.isNotEmpty()) {
                // 没有更多数据时显示提示
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "已加载全部成员",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberItem(
    member: GroupMemberInfo,
    currentUserPermission: Int = 0,
    groupId: String = "",
    onRemoveMember: ((String) -> Unit)? = null,
    onGagMember: ((String, Int) -> Unit)? = null,
    onSetMemberRole: ((String, Int) -> Unit)? = null,
    onTransferGroupOwner: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showGagDialog by remember { mutableStateOf(false) }
    var showTransferOwnerDialog by remember { mutableStateOf(false) }
    
    // 判断是否显示管理菜单：除了群主外的所有成员都显示
    val showAdminMenu = member.permissionLevel < 100
    val canTransferGroupOwner = currentUserPermission >= 100 && member.permissionLevel < 100
    
    YhCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                // 点击成员卡片跳转到用户详情页
                UserDetailActivity.start(context = context, userId = member.userId, userName = member.name, groupId = groupId)
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ImageUtils.createAvatarImageRequest(
                    context = LocalContext.current,
                    url = member.avatarUrl
                ),
                contentDescription = member.name,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = member.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    
                    // 群主/管理员标签
                    when (member.permissionLevel) {
                        100 -> {
                            Spacer(modifier = Modifier.width(8.dp))
                            YhSurface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = "群主",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        2 -> {
                            Spacer(modifier = Modifier.width(8.dp))
                            YhSurface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.secondary
                            ) {
                                Text(
                                    text = "管理员",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    
                    // VIP标签
                    if (member.isVip) {
                        Spacer(modifier = Modifier.width(8.dp))
                        YhSurface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = "VIP",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                
                Text(
                    text = "ID: ${member.userId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // 禁言状态
                if (member.isGag) {
                    Text(
                        text = "🔇 被禁言",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            // 管理菜单（除了群主外的所有成员都显示）
            if (showAdminMenu) {
                Box {
                    YhIconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "管理"
                        )
                    }
                    
                    YhDropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        // 根据成员权限显示上任/卸任管理员
                        if (member.permissionLevel == 2) {
                            // 管理员，显示卸任选项
                            YhDropdownMenuItem(
                                text = { Text("卸任管理员") },
                                onClick = {
                                    showMenu = false
                                    onSetMemberRole?.invoke(member.userId, 0)
                                }
                            )
                        } else if (member.permissionLevel == 0) {
                            // 普通成员，显示上任选项
                            YhDropdownMenuItem(
                                text = { Text("设为管理员") },
                                onClick = {
                                    showMenu = false
                                    onSetMemberRole?.invoke(member.userId, 2)
                                }
                            )
                        }

                        if (canTransferGroupOwner) {
                            YhDropdownMenuItem(
                                text = { Text("转让群主") },
                                onClick = {
                                    showMenu = false
                                    showTransferOwnerDialog = true
                                }
                            )
                        }
                        
                        YhDropdownMenuItem(
                            text = { Text("踢出群聊") },
                            onClick = {
                                showMenu = false
                                onRemoveMember?.invoke(member.userId)
                            }
                        )
                        YhDropdownMenuItem(
                            text = { Text("禁言") },
                            onClick = {
                                showMenu = false
                                showGagDialog = true
                            }
                        )
                    }
                }
            }
        }
    }
    
    // 禁言对话框
    if (showGagDialog) {
        GroupMemberGagDialog(
            memberName = member.name,
            onConfirm = { gagTime ->
                onGagMember?.invoke(member.userId, gagTime)
                showGagDialog = false
            },
            onDismiss = { showGagDialog = false }
        )
    }

    if (showTransferOwnerDialog) {
        YhAlertDialog(
            onDismissRequest = { showTransferOwnerDialog = false },
            title = { Text("转让群主") },
            text = { Text("确认将群主转让给 ${member.name} 吗？转让后你将不再是群主。") },
            confirmButton = {
                YhTextButton(
                    onClick = {
                        showTransferOwnerDialog = false
                        onTransferGroupOwner?.invoke(member.userId)
                    }
                ) {
                    Text("确认转让")
                }
            },
            dismissButton = {
                YhTextButton(onClick = { showTransferOwnerDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 群成员禁言对话框
 */
@Composable
private fun GroupMemberGagDialog(
    memberName: String,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val gagOptions = listOf(
        0 to "取消禁言",
        600 to "禁言10分钟",
        3600 to "禁言1小时",
        21600 to "禁言6小时",
        43200 to "禁言12小时",
        1 to "永久禁言"
    )
    
    YhAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("禁言 $memberName") },
        text = {
            Column {
                Text("选择禁言时长：", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                
                gagOptions.forEach { (gagTime, label) ->
                    YhTextButton(
                        onClick = { onConfirm(gagTime) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(label, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            YhTextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
