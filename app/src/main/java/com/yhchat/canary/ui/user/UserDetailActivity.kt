package com.yhchat.canary.ui.user

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.yhchat.canary.data.model.MedalInfo
import com.yhchat.canary.data.model.RemarkExtraEntry
import com.yhchat.canary.data.model.RemarkInfo
import com.yhchat.canary.data.model.UserDetail
import com.yhchat.canary.ui.adaptive.YhAlertDialog
import com.yhchat.canary.ui.adaptive.YhBottomSheet
import com.yhchat.canary.ui.adaptive.YhButton
import com.yhchat.canary.ui.adaptive.YhCard
import com.yhchat.canary.ui.adaptive.YhCircularProgressIndicator
import com.yhchat.canary.ui.adaptive.YhHorizontalDivider
import com.yhchat.canary.ui.adaptive.YhIcon as Icon
import com.yhchat.canary.ui.adaptive.YhIconButton
import com.yhchat.canary.ui.adaptive.YhOutlinedButton
import com.yhchat.canary.ui.adaptive.YhOutlinedTextField
import com.yhchat.canary.ui.adaptive.YhRichListItem
import com.yhchat.canary.ui.adaptive.YhScaffold
import com.yhchat.canary.ui.adaptive.YhSurface
import com.yhchat.canary.ui.adaptive.YhSwitch
import com.yhchat.canary.ui.adaptive.YhText as Text
import com.yhchat.canary.ui.adaptive.YhTextButton
import com.yhchat.canary.ui.adaptive.YhTopBar
import com.yhchat.canary.ui.adaptive.yhTopBarNestedScroll
import com.yhchat.canary.ui.base.BaseActivity
import com.yhchat.canary.ui.chat.ChatActivity
import com.yhchat.canary.ui.community.BoardDetailActivity
import com.yhchat.canary.ui.components.ReportDialog
import com.yhchat.canary.ui.theme.YhchatCanaryTheme

/**
 * 用户详情Activity
 */
class UserDetailActivity : BaseActivity() {
    private val viewModel: UserDetailViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return UserDetailViewModel(this@UserDetailActivity) as T
            }
        }
    }
    
    companion object {
        private const val EXTRA_USER_ID = "user_id"
        private const val EXTRA_USER_NAME = "user_name"
        private const val EXTRA_GROUP_ID = "group_id"
        
        fun start(context: Context, userId: String, userName: String = "", groupId: String? = null) {
            val intent = Intent(context, UserDetailActivity::class.java).apply {
                putExtra(EXTRA_USER_ID, userId)
                putExtra(EXTRA_USER_NAME, userName)
                groupId?.let { putExtra(EXTRA_GROUP_ID, it) }
            }
            context.startActivity(intent)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val userId = intent.getStringExtra(EXTRA_USER_ID) ?: ""
        val userName = intent.getStringExtra(EXTRA_USER_NAME) ?: ""
        val groupId = intent.getStringExtra(EXTRA_GROUP_ID)
        
        setContent {
            YhchatCanaryTheme {
                UserDetailScreen(
                    userId = userId,
                    userName = userName,
                    groupId = groupId,
                    viewModel = viewModel,
                    onBackClick = { finish() }
                )
            }
        }
    }
}

@Composable
private fun RemarkEditorDialog(
    config: RemarkEditorConfig,
    remarkInfo: RemarkInfo,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, List<RemarkExtraEntry>) -> Unit
) {
    val mode = config.mode
    var primaryValue by remember(config) { mutableStateOf(config.initialPrimary) }
    var otherKey by remember(config) { mutableStateOf(config.initialKey) }
    var otherValue by remember(config) { mutableStateOf(config.initialValue) }

    val title = when (mode) {
        RemarkEditorMode.Name -> "编辑备注名"
        RemarkEditorMode.Phone -> "编辑手机号"
        is RemarkEditorMode.Other -> if (mode.isNew) "添加其他备注" else "编辑其他备注"
    }

    val confirmEnabled = when (mode) {
        is RemarkEditorMode.Other -> otherKey.isNotBlank() && otherValue.isNotBlank()
        else -> true
    } && !isSaving

    val showDelete = when (mode) {
        RemarkEditorMode.Name -> remarkInfo.remarkName.isNotEmpty()
        RemarkEditorMode.Phone -> remarkInfo.phoneNumber.isNotEmpty()
        is RemarkEditorMode.Other -> !mode.isNew && config.targetIndex != null
    }

    fun handleSave() {
        when (mode) {
            RemarkEditorMode.Name -> onSave(primaryValue.trim(), remarkInfo.phoneNumber, remarkInfo.extraRemarks)
            RemarkEditorMode.Phone -> onSave(remarkInfo.remarkName, primaryValue.trim(), remarkInfo.extraRemarks)
            is RemarkEditorMode.Other -> {
                val updated = remarkInfo.extraRemarks.toMutableList()
                val entry = RemarkExtraEntry(otherKey.trim(), otherValue.trim())
                val index = config.targetIndex
                if (!mode.isNew && index != null && index in updated.indices) {
                    updated[index] = entry
                } else {
                    updated.add(entry)
                }
                onSave(remarkInfo.remarkName, remarkInfo.phoneNumber, updated)
            }
        }
    }

    fun handleDelete() {
        when (mode) {
            RemarkEditorMode.Name -> onSave("", remarkInfo.phoneNumber, remarkInfo.extraRemarks)
            RemarkEditorMode.Phone -> onSave(remarkInfo.remarkName, "", remarkInfo.extraRemarks)
            is RemarkEditorMode.Other -> {
                val updated = remarkInfo.extraRemarks.toMutableList()
                val index = config.targetIndex
                if (index != null && index in updated.indices) {
                    updated.removeAt(index)
                }
                onSave(remarkInfo.remarkName, remarkInfo.phoneNumber, updated)
            }
        }
    }

    YhAlertDialog(
        onDismissRequest = {
            if (!isSaving) onDismiss()
        },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (mode) {
                    RemarkEditorMode.Name, RemarkEditorMode.Phone -> {
                        YhOutlinedTextField(
                            value = primaryValue,
                            onValueChange = { primaryValue = it },
                            singleLine = true,
                            label = { Text(if (mode is RemarkEditorMode.Name) "备注名" else "手机号") },
                            enabled = !isSaving
                        )
                    }

                    is RemarkEditorMode.Other -> {
                        YhOutlinedTextField(
                            value = otherKey,
                            onValueChange = { otherKey = it },
                            singleLine = true,
                            label = { Text("备注名") },
                            enabled = !isSaving
                        )
                        YhOutlinedTextField(
                            value = otherValue,
                            onValueChange = { otherValue = it },
                            label = { Text("备注内容") },
                            enabled = !isSaving
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showDelete) {
                    YhTextButton(onClick = { if (!isSaving) handleDelete() }, enabled = !isSaving) {
                        Text("删除")
                    }
                }
                YhButton(onClick = { handleSave() }, enabled = confirmEnabled) {
                    if (isSaving) {
                        YhCircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("确定")
                    }
                }
            }
        },
        dismissButton = {
            YhTextButton(onClick = { if (!isSaving) onDismiss() }, enabled = !isSaving) {
                Text("取消")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDetailScreen(
    userId: String,
    userName: String,
    groupId: String?,
    viewModel: UserDetailViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context as? Activity
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val showCollapsedTitle by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    val isLightTheme = !androidx.compose.foundation.isSystemInDarkTheme()
    
    val navigationBarColor = MaterialTheme.colorScheme.background

    SideEffect {
        val window = activity?.window ?: return@SideEffect

        // 设置 system bars 颜色
        @Suppress("DEPRECATION")
        window.statusBarColor = navigationBarColor.toArgb()
        @Suppress("DEPRECATION")
        window.navigationBarColor = navigationBarColor.toArgb()

        // 设置图标颜色：浅色主题用深色图标；深色主题用浅色图标
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = isLightTheme
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            controller.isAppearanceLightNavigationBars = isLightTheme
        }

        // Android 10+ 禁用系统强制对比度，避免导航栏被强制变亮
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }
    
    LaunchedEffect(userId, groupId) {
        viewModel.load(userId, groupId)
    }

    LaunchedEffect(uiState.actionMessage) {
        uiState.actionMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeActionMessage()
        }
    }

    LaunchedEffect(uiState.friendDeleted) {
        if (uiState.friendDeleted) {
            viewModel.consumeFriendDeleted()
            onBackClick()
        }
    }
    
    YhScaffold(
        topBar = {
            YhTopBar(
                title = "用户详情",
                large = true,
                navigationIcon = {
                    YhIconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    YhIconButton(onClick = { viewModel.openMoreSheet() }) {
                        Icon(imageVector = Icons.Default.MoreVert, contentDescription = "更多")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    YhCircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.error != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = uiState.error ?: "加载失败",
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        YhButton(onClick = {
                            // 重新加载
                            // 这里简单处理，实际应触发LaunchedEffect重新执行
                        }) {
                            Text("重试")
                        }
                    }
                }
                uiState.userDetail != null -> {
                    val userDetail = uiState.userDetail!!
                    UserDetailContent(
                        userDetail = userDetail,
                        createdBoards = uiState.createdBoards,
                        isLoadingBoards = uiState.isLoadingBoards,
                        token = uiState.token,
                        listState = listState,
                        isInAddressBook = uiState.isInAddressBook,
                        isCheckingAddressBook = uiState.isCheckingAddressBook,
                        onPrimaryAction = {
                            if (uiState.isInAddressBook) {
                                val intent = Intent(context, ChatActivity::class.java).apply {
                                    putExtra("chatId", userId)
                                    putExtra("chatType", 1)
                                    putExtra("chatName", userDetail.name)
                                }
                                context.startActivity(intent)
                            } else {
                                viewModel.openAddFriendDialog()
                            }
                        },
                        onShowUserInfo = viewModel::openUserInfoDialog,
                        onAvatarClick = viewModel::openImageViewer,
                        onEditRemarkName = { viewModel.openNameEditor(userDetail.id) },
                        onEditRemarkPhone = { viewModel.openPhoneEditor(userDetail.id) },
                        onAddOtherRemark = { viewModel.openOtherRemarkEditor(userDetail.id, null, "", "") },
                        onEditOtherRemark = { index, entry ->
                            viewModel.openOtherRemarkEditor(
                                userDetail.id,
                                index,
                                entry.key,
                                entry.value
                            )
                        }
                    )
                }
            }
        }

        if (uiState.showUserInfoDialog && uiState.userDetail != null) {
            UserInfoDialog(
                userDetail = uiState.userDetail!!,
                onDismiss = viewModel::dismissUserInfoDialog
            )
        }

        if (uiState.showImageViewer && uiState.currentImageUrl.isNotBlank()) {
            com.yhchat.canary.ui.components.ImageViewer(
                imageUrl = uiState.currentImageUrl,
                onDismiss = viewModel::dismissImageViewer
            )
        }

        if (uiState.remarkEditorConfig != null && uiState.userDetail != null) {
            val remarkInfo = uiState.userDetail?.remarkInfo ?: RemarkInfo("", "", "", emptyList())
            val config = uiState.remarkEditorConfig!!
            RemarkEditorDialog(
                config = config,
                remarkInfo = remarkInfo,
                isSaving = uiState.isSavingRemark,
                onDismiss = {
                    if (!uiState.isSavingRemark) {
                        viewModel.dismissRemarkEditor()
                    }
                },
                onSave = { name, phone, others ->
                    if (!uiState.isSavingRemark) {
                        viewModel.saveRemark(config.friendId, name, phone, others)
                    }
                }
            )
        }
        
        if (uiState.showAddFriendDialog) {
            YhAlertDialog(
                onDismissRequest = {
                    if (!uiState.isAddingFriend) {
                        viewModel.dismissAddFriendDialog()
                    }
                },
                title = { Text("添加好友") },
                text = {
                    Column {
                        Text("确定要添加 ${uiState.userDetail?.name ?: userName} 为好友吗？")
                        Spacer(modifier = Modifier.height(8.dp))
                        YhOutlinedTextField(
                            value = uiState.addFriendRemark,
                            onValueChange = viewModel::updateAddFriendRemark,
                            label = { Text("申请备注（可选）") },
                            singleLine = true,
                            enabled = !uiState.isAddingFriend,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    YhButton(
                        onClick = {
                            if (uiState.isAddingFriend) return@YhButton
                            viewModel.confirmAddFriend(userId)
                        },
                        enabled = !uiState.isAddingFriend
                    ) {
                        if (uiState.isAddingFriend) {
                            YhCircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("添加")
                        }
                    }
                },
                dismissButton = {
                    YhTextButton(
                        onClick = viewModel::dismissAddFriendDialog,
                        enabled = !uiState.isAddingFriend
                    ) {
                        Text("取消")
                    }
                }
            )
        }

        if (uiState.showGagDialog && groupId != null) {
            GagMemberDialog(
                userName = uiState.userDetail?.name ?: userName,
                isLoading = uiState.isProcessingMemberAction,
                onConfirm = { gagTime ->
                    if (!uiState.isProcessingMemberAction) {
                        viewModel.gagMember(groupId, userId, gagTime)
                        viewModel.dismissGagDialog()
                    }
                },
                onDismiss = {
                    if (!uiState.isProcessingMemberAction) {
                        viewModel.dismissGagDialog()
                    }
                }
            )
        }

        if (uiState.showMoreSheet) {
            YhBottomSheet(
                show = true,
                title = "更多",
                onDismissRequest = viewModel::dismissMoreSheet
            ) {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    SheetSectionHeader(title = "互动")

                    SheetActionItem(
                        icon = Icons.Default.ManageSearch,
                        title = "搜索聊天记录",
                        onClick = {
                            viewModel.dismissMoreSheet()
                            com.yhchat.canary.ui.group.ChatSearchActivity.start(
                                context,
                                userId,
                                1,
                                uiState.userDetail?.name ?: userName
                            )
                        }
                    )

                    SheetActionItem(
                        icon = Icons.Default.Search,
                        title = "账号信息",
                        onClick = {
                            viewModel.dismissMoreSheet()
                            viewModel.openUserInfoDialog()
                        }
                    )

                    SheetActionItem(
                        icon = Icons.Default.Share,
                        title = "分享好友",
                        onClick = {
                            viewModel.dismissMoreSheet()
                            viewModel.openShareDialog()
                        }
                    )

                    SheetActionItem(
                        icon = Icons.Default.Report,
                        title = "举报",
                        onClick = {
                            viewModel.dismissMoreSheet()
                            viewModel.openReportDialog()
                        }
                    )

                    SheetActionItem(
                        icon = Icons.Default.Wallpaper,
                        title = "聊天背景",
                        onClick = {
                            viewModel.dismissMoreSheet()
                            com.yhchat.canary.ui.background.ChatBackgroundActivity.start(
                                context,
                                userId,
                                uiState.userDetail?.name ?: userName
                            )
                        }
                    )

                    YhRichListItem(
                        headlineContent = { Text("免打扰") },
                        supportingContent = {
                            Text(if (uiState.isNoNotify) "已开启" else "未开启")
                        },
                        leadingContent = {
                            Icon(
                                imageVector = if (uiState.isNoNotify) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = {
                            if (uiState.isSettingNoNotify) {
                                YhCircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                YhSwitch(
                                    checked = uiState.isNoNotify,
                                    onCheckedChange = { checked ->
                                        if (uiState.isSettingNoNotify) return@YhSwitch
                                        viewModel.setNoNotify(userId, checked)
                                    },
                                    enabled = !uiState.isSettingNoNotify
                                )
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                    YhHorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                    if (!uiState.isCheckingAddressBook && uiState.isInAddressBook) {
                        SheetActionItem(
                            icon = Icons.Default.Delete,
                            title = "删除好友",
                            isDestructive = true,
                            onClick = {
                                viewModel.dismissMoreSheet()
                                viewModel.openDeleteFriendDialog()
                            }
                        )
                    }

                    if (groupId != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        SheetSectionHeader(title = "群聊管理")

                        if (uiState.targetUserPermission == 2) {
                            SheetActionItem(
                                icon = Icons.Default.AdminPanelSettings,
                                title = "卸任管理员",
                                enabled = !uiState.isProcessingMemberAction,
                                onClick = {
                                    viewModel.dismissMoreSheet()
                                    if (!uiState.isProcessingMemberAction) {
                                        viewModel.setMemberRole(groupId, userId, 0)
                                    }
                                }
                            )
                        } else if (uiState.targetUserPermission == 0) {
                            SheetActionItem(
                                icon = Icons.Default.AdminPanelSettings,
                                title = "设为管理员",
                                enabled = !uiState.isProcessingMemberAction,
                                onClick = {
                                    viewModel.dismissMoreSheet()
                                    if (!uiState.isProcessingMemberAction) {
                                        viewModel.setMemberRole(groupId, userId, 2)
                                    }
                                }
                            )
                        }

                        SheetActionItem(
                            icon = Icons.Default.Delete,
                            title = "踢出群聊",
                            enabled = !uiState.isProcessingMemberAction,
                            isDestructive = true,
                            onClick = {
                                viewModel.dismissMoreSheet()
                                if (!uiState.isProcessingMemberAction) {
                                    viewModel.removeMember(groupId, userId)
                                }
                            }
                        )
                        SheetActionItem(
                            icon = Icons.Default.Block,
                            title = "禁言",
                            enabled = !uiState.isProcessingMemberAction,
                            onClick = {
                                viewModel.dismissMoreSheet()
                                viewModel.openGagDialog()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }

        if (uiState.showDeleteFriendDialog) {
            YhAlertDialog(
                onDismissRequest = viewModel::dismissDeleteFriendDialog,
                title = {
                    Text(
                        text = "删除好友",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text("确定要删除好友「${uiState.userDetail?.name ?: userName}」吗？")
                },
                confirmButton = {
                    YhButton(
                        onClick = {
                            viewModel.dismissDeleteFriendDialog()
                            viewModel.deleteFriend(userId)
                        },
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ) {
                        Text("删除", color = MaterialTheme.colorScheme.onError)
                    }
                },
                dismissButton = {
                    YhTextButton(onClick = viewModel::dismissDeleteFriendDialog) {
                        Text("取消")
                    }
                }
            )
        }

        if (uiState.showReportDialog) {
            ReportDialog(
                chatId = userId,
                chatType = 1,
                chatName = uiState.userDetail?.name ?: userName,
                onDismiss = viewModel::dismissReportDialog,
                onSuccess = {
                    Toast.makeText(context, "举报已提交", Toast.LENGTH_SHORT).show()
                }
            )
        }
        
        if (uiState.showShareDialog) {
            com.yhchat.canary.ui.components.ShareDialog(
                chatId = userId,
                chatType = 1, // 用户
                chatName = uiState.userDetail?.name ?: userName,
                onDismiss = viewModel::dismissShareDialog
            )
        }
    }
}

@Composable
private fun SheetSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun SheetActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isDestructive: Boolean = false
) {
    val containerColor = MaterialTheme.colorScheme.surface
    val baseTextColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val baseIconColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    val alpha = if (enabled) 1f else 0.45f

    YhRichListItem(
        headlineContent = {
            Text(
                text = title,
                color = baseTextColor.copy(alpha = alpha)
            )
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = baseIconColor.copy(alpha = alpha)
            )
        },
        modifier = Modifier.fillMaxWidth(),
        containerColor = containerColor,
        onClick = if (enabled) onClick else null
    )
    YhHorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
}

@Composable
private fun GagMemberDialog(
    userName: String,
    isLoading: Boolean,
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
        onDismissRequest = if (!isLoading) onDismiss else { {} },
        title = { Text("禁言 $userName") },
        text = {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                gagOptions.forEach { (gagTime, label) ->
                    YhTextButton(
                        onClick = { onConfirm(gagTime) },
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(label, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            YhTextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("取消")
            }
        }
    )
}

@Composable
fun UserDetailContent(
    userDetail: UserDetail,
    createdBoards: List<com.yhchat.canary.data.model.BoardsByCreateItem>,
    isLoadingBoards: Boolean,
    token: String,
    listState: androidx.compose.foundation.lazy.LazyListState,
    isInAddressBook: Boolean,
    isCheckingAddressBook: Boolean,
    onPrimaryAction: () -> Unit,
    onShowUserInfo: () -> Unit,
    onAvatarClick: (String) -> Unit,
    onEditRemarkName: () -> Unit,
    onEditRemarkPhone: () -> Unit,
    onAddOtherRemark: () -> Unit,
    onEditOtherRemark: (Int, RemarkExtraEntry) -> Unit
) {
    val context = LocalContext.current
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .yhTopBarNestedScroll(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            UserHeroCard(
                avatarUrl = userDetail.avatarUrl,
                name = userDetail.name,
                idText = userDetail.id,
                isVip = userDetail.isVip == 1,
                medals = userDetail.medalList,
                isPrimaryLoading = isCheckingAddressBook,
                primaryButtonText = if (isInAddressBook) "发消息" else "添加",
                onPrimaryAction = onPrimaryAction,
                onInfoClick = onShowUserInfo,
                onAvatarClick = onAvatarClick
            )
        }

        userDetail.remarkInfo?.let { remark ->
            val nameValue = remark.remarkName.ifEmpty { "未设置" }
            val phoneValue = remark.phoneNumber.ifEmpty { "未设置" }
            item {
                InfoSectionCard(
                    title = "备注信息",
                    action = {
                        YhIconButton(onClick = onAddOtherRemark) {
                            Icon(Icons.Default.Add, contentDescription = "添加备注")
                        }
                    }
                ) {
                    InfoRow(
                        label = "备注名",
                        value = nameValue,
                        valueColor = if (remark.remarkName.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEditRemarkName() }
                    )
                    InfoRow(
                        label = "手机号",
                        value = phoneValue,
                        valueColor = if (remark.phoneNumber.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEditRemarkPhone() }
                    )
                    Text(
                        text = "其他备注",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                    if (remark.extraRemarks.isEmpty()) {
                        Text(
                            text = "暂无其他备注",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        remark.extraRemarks.forEachIndexed { index, entry ->
                            val label = entry.key.ifBlank { "备注${index + 1}" }
                            InfoRow(
                                label = label,
                                value = entry.value,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onEditOtherRemark(index, entry) }
                            )
                        }
                    }
                }
            }
        }

        userDetail.profileInfo?.let { profile ->
            item {
                InfoSectionCard(title = "个人资料") {
                        if (profile.lastActiveTime.isNotEmpty()) {
                            InfoRow("上次活跃", profile.lastActiveTime)
                        }
                        if (profile.introduction.isNotEmpty()) {
                            InfoRow("简介", profile.introduction)
                        }
                        val genderText = when (profile.gender) {
                            1 -> "男"
                            2 -> "女"
                            else -> "其他"
                        }
                        InfoRow("性别", genderText)
                        if (profile.birthday > 0) {
                            val birthdayDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                                .format(java.util.Date(profile.birthday * 1000))
                            InfoRow("生日", birthdayDate)
                        }
                        if (profile.city.isNotEmpty()) {
                            InfoRow("城市", profile.city)
                        }
                        if (profile.district.isNotEmpty()) {
                            InfoRow("地区", profile.district)
                        }
                }
            }
        }

        item {
            InfoSectionCard(title = "创建的分区") {
                    Spacer(modifier = Modifier.height(8.dp))
                    when {
                        isLoadingBoards -> {
                            YhCircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                        createdBoards.isEmpty() -> {
                            Text(
                                text = "暂无",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        else -> {
                            createdBoards.forEach { board ->
                                YhSurface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val intent = Intent(context, BoardDetailActivity::class.java).apply {
                                                putExtra("board_id", board.id)
                                                putExtra("board_name", board.name)
                                                putExtra("token", token)
                                            }
                                            context.startActivity(intent)
                                        }
                                        .padding(vertical = 4.dp),
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(board.avatar)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = board.name,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "ID: ${board.id}",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
private fun UserHeroCard(
    avatarUrl: String,
    name: String,
    idText: String,
    isVip: Boolean,
    medals: List<MedalInfo>,
    isPrimaryLoading: Boolean,
    primaryButtonText: String,
    onPrimaryAction: () -> Unit,
    onInfoClick: () -> Unit,
    onAvatarClick: (String) -> Unit
) {
    val context = LocalContext.current
    YhCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 28.dp,
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(avatarUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "用户头像",
                    modifier = Modifier
                        .size(86.dp)
                        .clip(CircleShape)
                        .clickable(enabled = avatarUrl.isNotBlank()) {
                            onAvatarClick(avatarUrl)
                        },
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (isVip) {
                            VipBadge()
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "ID: $idText",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable {
                            val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboardManager.setPrimaryClip(android.content.ClipData.newPlainText("userId", idText))
                            Toast.makeText(context, "已复制用户ID", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
            if (medals.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    medals.take(4).forEach { medal ->
                        YhSurface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                        ) {
                            Text(
                                text = medal.name,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                YhButton(
                    onClick = onPrimaryAction,
                    shape = RoundedCornerShape(18.dp),
                    enabled = !isPrimaryLoading
                ) {
                    if (isPrimaryLoading) {
                        YhCircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text(primaryButtonText)
                    }
                }
                YhOutlinedButton(
                    onClick = onInfoClick,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("账号信息")
                }
            }
        }
    }
}

@Composable
private fun VipBadge() {
    YhSurface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.primary
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "VIP",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
            Text(
                text = "VIP",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun InfoSectionCard(
    title: String,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    YhCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    action?.invoke()
                }
                content()
            }
        )
    }
}

@Composable
private fun UserInfoDialog(
    userDetail: UserDetail,
    onDismiss: () -> Unit
) {
    YhAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            YhTextButton(onClick = onDismiss) {
                Text("确定")
            }
        },
        title = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(userDetail.name)
                if (userDetail.isVip == 1) {
                    VipBadge()
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow("UID", userDetail.id)
                InfoRow("注册时间", userDetail.registerTime)
                InfoRow("在线天数", "${userDetail.onlineDay} 天")
                InfoRow("连续在线", "${userDetail.continuousOnlineDay} 天")
                if (userDetail.ipGeo.isNotEmpty()) {
                    InfoRow("IP归属地", userDetail.ipGeo)
                }
                if (userDetail.isVip == 1 && userDetail.vipExpiredTime > 0) {
                    val expireDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                        .format(java.util.Date(userDetail.vipExpiredTime * 1000))
                    InfoRow("VIP到期时间", expireDate)
                }
                if (userDetail.banTime > 0) {
                    val banDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                        .format(java.util.Date(userDetail.banTime * 1000))
                    InfoRow("封禁结束时间", banDate, MaterialTheme.colorScheme.error)
                }
            }
        }
    )
}

@Composable
fun InfoRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = valueColor
        )
    }
}

