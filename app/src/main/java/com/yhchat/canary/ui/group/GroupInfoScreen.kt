package com.yhchat.canary.ui.group

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.yhchat.canary.data.di.RepositoryFactory
import com.yhchat.canary.data.model.GroupDetail
import com.yhchat.canary.data.model.GroupMemberInfo
import com.yhchat.canary.data.repository.CacheRepository
import com.yhchat.canary.ui.adaptive.YhAlertDialog
import com.yhchat.canary.ui.adaptive.YhButton
import com.yhchat.canary.ui.adaptive.YhCircularProgressIndicator
import com.yhchat.canary.ui.adaptive.YhIcon as Icon
import com.yhchat.canary.ui.adaptive.YhIconButton
import com.yhchat.canary.ui.adaptive.YhOutlinedTextField
import com.yhchat.canary.ui.adaptive.YhScaffold
import com.yhchat.canary.ui.adaptive.YhSwitch
import com.yhchat.canary.ui.adaptive.YhText as Text
import com.yhchat.canary.ui.adaptive.YhTextButton
import com.yhchat.canary.ui.adaptive.YhTopBar
import com.yhchat.canary.ui.adaptive.yhTopBarNestedScroll
import com.yhchat.canary.ui.components.ImageUtils
import com.yhchat.canary.ui.settings.SettingsCustomItem
import com.yhchat.canary.ui.settings.SettingsGroup
import com.yhchat.canary.ui.settings.SettingsItemCell
import com.yhchat.canary.ui.theme.YhchatCanaryTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GroupInfoScreenRoot(
    groupId: String,
    groupName: String,
    viewModel: GroupInfoViewModel,
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
    onShareClick: () -> Unit = {},
    onReportClick: () -> Unit = {},
    onSearchChatClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showShareDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showInviteDialog by remember { mutableStateOf(false) }
    var showExitGroupDialog by remember { mutableStateOf(false) }

    LaunchedEffect(groupId) {
        viewModel.loadGroupInfo(groupId)
    }

    YhchatCanaryTheme {
        YhScaffold(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            topBar = {
                YhTopBar(
                    title = groupName,
                    large = true,
                    navigationIcon = {
                        YhIconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    },
                    actions = {
                        YhIconButton(onClick = onSettingsClick) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "群聊设置"
                            )
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
                        YhCircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
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

                    uiState.groupInfo != null -> {
                        GroupInfoContent(
                            groupId = groupId,
                            groupName = groupName,
                            context = context,
                            groupInfo = uiState.groupInfo!!,
                            members = uiState.members,
                            isLoadingMembers = uiState.isLoadingMembers,
                            isLoadingMoreMembers = uiState.isLoadingMoreMembers,
                            hasMoreMembers = uiState.hasMoreMembers,
                            currentUserPermission = uiState.groupInfo!!.permissionLevel.toInt(),
                            onLoadMore = { viewModel.loadMoreMembers(groupId) },
                            onRemoveMember = { userId -> viewModel.removeMember(groupId, userId) },
                            onGagMember = { userId, gagTime -> viewModel.gagMember(groupId, userId, gagTime) },
                            onSetMemberRole = { userId, userLevel -> viewModel.setMemberRole(groupId, userId, userLevel) },
                            onShareClick = { showShareDialog = true },
                            onReportClick = { showReportDialog = true },
                            onSearchChatClick = onSearchChatClick,
                            onInviteClick = { showInviteDialog = true },
                            onExitClick = { showExitGroupDialog = true },
                            onRefresh = { viewModel.loadGroupInfo(groupId) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearSuccessMessage()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    if (showShareDialog) {
        com.yhchat.canary.ui.components.ShareDialog(
            chatId = groupId,
            chatType = 2,
            chatName = groupName,
            onDismiss = { showShareDialog = false }
        )
    }

    if (showReportDialog) {
        com.yhchat.canary.ui.components.ReportDialog(
            chatId = groupId,
            chatType = 2,
            chatName = groupName,
            onDismiss = { showReportDialog = false },
            onSuccess = {
                android.widget.Toast.makeText(context, "举报已提交", android.widget.Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showInviteDialog) {
        com.yhchat.canary.ui.components.InviteToGroupDialog(
            groupId = groupId,
            groupName = groupName,
            onDismiss = { showInviteDialog = false },
            onSuccess = {
                android.widget.Toast.makeText(context, "邀请已发送", android.widget.Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showExitGroupDialog) {
        ExitGroupDialog(
            groupName = groupName,
            onConfirm = {
                scope.launch {
                    try {
                        val userRepository = RepositoryFactory.getUserRepository(context)
                        userRepository.deleteFriend(groupId, 2).fold(
                            onSuccess = { _: Boolean ->
                                android.widget.Toast.makeText(context, "已退出群聊", android.widget.Toast.LENGTH_SHORT).show()
                                onBackClick()
                            },
                            onFailure = { error: Throwable ->
                                android.widget.Toast.makeText(context, "退出失败：${error.message}", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        )
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "退出失败：${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                showExitGroupDialog = false
            },
            onDismiss = { showExitGroupDialog = false }
        )
    }
}

@Composable
private fun GroupInfoContent(
    groupId: String,
    groupName: String,
    context: android.content.Context,
    groupInfo: GroupDetail,
    members: List<GroupMemberInfo>,
    isLoadingMembers: Boolean,
    isLoadingMoreMembers: Boolean,
    hasMoreMembers: Boolean,
    currentUserPermission: Int = 0,
    onLoadMore: () -> Unit,
    onRemoveMember: (String) -> Unit = {},
    onGagMember: (String, Int) -> Unit = { _, _ -> },
    onSetMemberRole: (String, Int) -> Unit = { _, _ -> },
    onShareClick: () -> Unit = {},
    onReportClick: () -> Unit = {},
    onSearchChatClick: () -> Unit = {},
    onInviteClick: () -> Unit = {},
    onExitClick: () -> Unit = {},
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isNoNotify by remember(groupInfo.doNotDisturb) { mutableStateOf(groupInfo.doNotDisturb) }
    var isSettingNoNotify by remember { mutableStateOf(false) }
    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    var showNicknameDialog by remember { mutableStateOf(false) }
    var nicknameInput by remember(groupInfo.myGroupNickname) { mutableStateOf(groupInfo.myGroupNickname.orEmpty()) }
    var isUpdatingNickname by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(listState, isLoadingMembers, isLoadingMoreMembers, hasMoreMembers) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem != null && lastVisibleItem.index == layoutInfo.totalItemsCount - 1
        }.collect { isAtBottom ->
            if (isAtBottom && !isLoadingMembers && !isLoadingMoreMembers && hasMoreMembers) {
                onLoadMore()
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.yhTopBarNestedScroll(),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            SettingsGroup(
                title = "群聊",
                items = listOf(
                    {
                        GroupHeaderSettingsItem(
                            groupInfo = groupInfo,
                            onPreviewAvatar = { previewImageUrl = groupInfo.avatarUrl },
                            onCopyGroupId = {
                                val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("groupId", groupInfo.groupId)
                                clipboardManager.setPrimaryClip(clip)
                                android.widget.Toast.makeText(context, "已复制群聊ID", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            onShareClick = onShareClick
                        )
                    },
                    {
                        SettingsItemCell(
                            icon = Icons.Default.People,
                            title = "群成员",
                            subtitle = "${groupInfo.memberCount} 名成员",
                            onClick = {
                                GroupMembersActivity.start(
                                    context = context,
                                    groupId = groupId,
                                    groupName = groupName
                                )
                            }
                        )
                    },
                    {
                        GroupSwitchSettingsItem(
                            icon = Icons.Default.NotificationsOff,
                            title = "免打扰",
                            subtitle = if (isNoNotify) "已开启" else "已关闭",
                            checked = isNoNotify,
                            enabled = !isSettingNoNotify,
                            showLoading = isSettingNoNotify,
                            onCheckedChange = { checked ->
                                if (isSettingNoNotify) return@GroupSwitchSettingsItem
                                isNoNotify = checked
                                isSettingNoNotify = true
                                scope.launch {
                                    val friendRepository = RepositoryFactory.getFriendRepository(context)
                                    friendRepository.setNoNotify(
                                        chatId = groupId,
                                        noNotify = if (checked) 1 else 0
                                    ).fold(
                                        onSuccess = {
                                            CacheRepository(context.applicationContext)
                                                .updateConversationDoNotDisturb(groupId, if (checked) 1 else 0)
                                            android.widget.Toast.makeText(context, "设置成功", android.widget.Toast.LENGTH_SHORT).show()
                                        },
                                        onFailure = { error ->
                                            isNoNotify = !checked
                                            android.widget.Toast.makeText(context, "设置失败：${error.message}", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                    isSettingNoNotify = false
                                }
                            }
                        )
                    }
                )
            )
        }

        if (groupInfo.introduction.isNotBlank()) {
            item {
                SettingsGroup(
                    title = "群聊简介",
                    items = listOf(
                        {
                            SettingsCustomItem {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateContentSize()
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "可直接长按选择并复制",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    SelectionContainer {
                                        Text(
                                            text = groupInfo.introduction,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    )
                )
            }
        }

        if (!groupInfo.banReason.isNullOrBlank() || groupInfo.unbanTimestamp != null) {
            item {
                SettingsGroup(
                    title = "封禁信息",
                    items = listOf(
                        {
                            SettingsCustomItem {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Text(
                                        text = "封禁原因: ${groupInfo.banReason?.takeIf { it.isNotBlank() } ?: "未设置"}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "解禁时间: ${formatGroupUnbanTime(groupInfo.unbanTimestamp)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    )
                )
            }
        }

        item {
            SettingsGroup(
                title = "功能",
                items = listOf(
                    {
                        SettingsItemCell(
                            icon = Icons.Default.Search,
                            title = "搜索聊天记录",
                            subtitle = "按关键词查找当前群的历史消息",
                            onClick = onSearchChatClick
                        )
                    },
                    {
                        SettingsItemCell(
                            icon = Icons.Default.Person,
                            title = "我的群昵称",
                            subtitle = groupInfo.myGroupNickname?.takeIf { it.isNotBlank() } ?: "未设置",
                            onClick = {
                                nicknameInput = groupInfo.myGroupNickname.orEmpty()
                                showNicknameDialog = true
                            }
                        )
                    },
                    {
                        SettingsItemCell(
                            icon = Icons.Default.Report,
                            title = "举报群聊",
                            subtitle = "提交违规举报",
                            onClick = onReportClick
                        )
                    },
                    {
                        SettingsItemCell(
                            icon = Icons.Default.Wallpaper,
                            title = "设置聊天背景",
                            subtitle = "单独设置这个群的聊天背景",
                            onClick = {
                                com.yhchat.canary.ui.background.ChatBackgroundActivity.start(
                                    context,
                                    groupId,
                                    groupName
                                )
                            }
                        )
                    },
                    {
                        SettingsItemCell(
                            icon = Icons.Default.Folder,
                            title = "群网盘",
                            subtitle = "查看群文件和共享内容",
                            onClick = {
                                com.yhchat.canary.ui.disk.GroupDiskActivity.start(context, groupId, groupName)
                            }
                        )
                    },
                    {
                        SettingsItemCell(
                            icon = Icons.Default.PersonAdd,
                            title = "邀请好友",
                            subtitle = "邀请其他联系人加入本群",
                            onClick = onInviteClick
                        )
                    },
                    {
                        SettingsItemCell(
                            icon = Icons.Default.Settings,
                            title = "群聊设置",
                            subtitle = "编辑群信息、权限和管理项",
                            onClick = {
                                val intent = android.content.Intent(context, GroupSettingsActivity::class.java)
                                intent.putExtra(GroupSettingsActivity.EXTRA_GROUP_ID, groupId)
                                intent.putExtra(GroupSettingsActivity.EXTRA_GROUP_NAME, groupName)
                                context.startActivity(intent)
                            }
                        )
                    },
                    {
                        SettingsItemCell(
                            icon = Icons.AutoMirrored.Filled.ExitToApp,
                            title = "退出群聊",
                            subtitle = "离开当前群聊",
                            onClick = onExitClick,
                            isDestructive = true
                        )
                    }
                )
            )
        }
    }

    if (showNicknameDialog) {
        YhAlertDialog(
            onDismissRequest = { if (!isUpdatingNickname) showNicknameDialog = false },
            title = { Text("设置我的群昵称") },
            text = {
                Column {
                    Text(
                        text = "当前昵称: ${groupInfo.myGroupNickname?.takeIf { it.isNotBlank() } ?: "未设置"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    YhOutlinedTextField(
                        value = nicknameInput,
                        onValueChange = { nicknameInput = it },
                        label = { Text("群昵称") },
                        enabled = !isUpdatingNickname,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                YhButton(
                    onClick = {
                        isUpdatingNickname = true
                        scope.launch(Dispatchers.IO) {
                            try {
                                val groupRepository = RepositoryFactory.getGroupRepository(context)
                                val result = groupRepository.editMyGroupNickname(groupId, nicknameInput)
                                withContext(Dispatchers.Main) {
                                    result.fold(
                                        onSuccess = {
                                            android.widget.Toast.makeText(context, "群昵称修改成功", android.widget.Toast.LENGTH_SHORT).show()
                                            isUpdatingNickname = false
                                            showNicknameDialog = false
                                            onRefresh()
                                        },
                                        onFailure = { error ->
                                            android.widget.Toast.makeText(context, "修改失败：${error.message}", android.widget.Toast.LENGTH_SHORT).show()
                                            isUpdatingNickname = false
                                        }
                                    )
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    android.widget.Toast.makeText(context, "修改失败：${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                    isUpdatingNickname = false
                                }
                            }
                        }
                    },
                    enabled = !isUpdatingNickname
                ) {
                    if (isUpdatingNickname) {
                        YhCircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("确定")
                    }
                }
            },
            dismissButton = {
                YhTextButton(
                    onClick = { showNicknameDialog = false },
                    enabled = !isUpdatingNickname
                ) {
                    Text("取消")
                }
            }
        )
    }
    
    previewImageUrl?.let { imageUrl ->
        com.yhchat.canary.ui.components.ImageViewer(
            imageUrl = imageUrl,
            onDismiss = { previewImageUrl = null }
        )
    }
}

@Composable
private fun GroupHeaderSettingsItem(
    groupInfo: GroupDetail,
    onPreviewAvatar: () -> Unit,
    onCopyGroupId: () -> Unit,
    onShareClick: () -> Unit
) {
    SettingsCustomItem {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ImageUtils.createImageRequest(
                    context = LocalContext.current,
                    url = groupInfo.avatarUrl
                ),
                contentDescription = "群头像",
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .clickable(enabled = groupInfo.avatarUrl.isNotBlank()) { onPreviewAvatar() },
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .animateContentSize()
            ) {
                Text(
                    text = groupInfo.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "ID: ${groupInfo.groupId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(onClick = onCopyGroupId)
                )
                Text(
                    text = "${groupInfo.memberCount} 名成员",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            YhIconButton(onClick = onShareClick) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "分享群聊",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun GroupSwitchSettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    showLoading: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingsCustomItem(onClick = if (enabled) ({ onCheckedChange(!checked) }) else null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (showLoading) {
                YhCircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                YhSwitch(
                    checked = checked,
                    onCheckedChange = if (enabled) onCheckedChange else null,
                    enabled = enabled
                )
            }
        }
    }
}

@Composable
fun ExitGroupDialog(
    groupName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    YhAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "退出群聊",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text("确定要退出群聊「$groupName」吗？")
        },
        confirmButton = {
            YhButton(onClick = onConfirm) {
                Text("退出")
            }
        },
        dismissButton = {
            YhTextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

private fun formatGroupUnbanTime(timestamp: Long?): String {
    if (timestamp == null || timestamp <= 0L) return "未设置"
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp * 1000L))
}
