package com.yhchat.canary.ui.group

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.yhchat.canary.ui.adaptive.YhAlertDialog
import com.yhchat.canary.ui.adaptive.YhButton
import com.yhchat.canary.ui.adaptive.YhCheckbox
import com.yhchat.canary.ui.adaptive.YhCircularProgressIndicator
import com.yhchat.canary.ui.adaptive.YhIcon as Icon
import com.yhchat.canary.ui.adaptive.YhIconButton
import com.yhchat.canary.ui.adaptive.YhOutlinedTextField
import com.yhchat.canary.ui.adaptive.YhScaffold
import com.yhchat.canary.ui.adaptive.YhSwitch
import com.yhchat.canary.ui.adaptive.YhSurface
import com.yhchat.canary.ui.adaptive.YhText as Text
import com.yhchat.canary.ui.adaptive.YhTextButton
import com.yhchat.canary.ui.adaptive.YhTopBar
import com.yhchat.canary.ui.adaptive.yhTopBarNestedScroll
import com.yhchat.canary.ui.components.ImageUtils
import com.yhchat.canary.ui.components.ImageViewer
import com.yhchat.canary.ui.settings.SettingsCustomItem
import com.yhchat.canary.ui.settings.SettingsGroup
import com.yhchat.canary.ui.settings.SettingsItemCell

@Composable
fun GroupSettingsScreenRoot(
    groupId: String,
    groupName: String,
    viewModel: GroupSettingsViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(groupId) {
        viewModel.loadGroupInfo(groupId)
    }

    YhScaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            YhTopBar(
                title = "群聊设置 · $groupName",
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
                    if (uiState.isEditing) {
                        YhIconButton(
                            onClick = { viewModel.saveEditing() },
                            enabled = !uiState.isSaving && !uiState.isUploadingAvatar
                        ) {
                            if (uiState.isSaving) {
                                YhCircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "保存"
                                )
                            }
                        }
                    } else {
                        YhIconButton(onClick = { viewModel.startEditing() }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "编辑"
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
                    GroupSettingsContent(
                        uiState = uiState,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupSettingsContent(
    uiState: GroupSettingsUiState,
    viewModel: GroupSettingsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val groupInfo = uiState.groupInfo!!
    val currentAvatarUrl = if (uiState.isEditing) {
        uiState.editedAvatarUrl.ifBlank { groupInfo.avatarUrl }
    } else {
        groupInfo.avatarUrl
    }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            viewModel.uploadGroupAvatar(context, selectedUri)
        }
    }

    LazyColumn(
        modifier = modifier.yhTopBarNestedScroll(),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            SettingsGroup(
                title = "基本信息",
                items = listOf(
                    {
                        SettingsCustomItem {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateContentSize()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(contentAlignment = Alignment.BottomEnd) {
                                    AsyncImage(
                                        model = ImageUtils.createImageRequest(
                                            context = LocalContext.current,
                                            url = currentAvatarUrl
                                        ),
                                        contentDescription = "群头像",
                                        modifier = Modifier
                                            .size(100.dp)
                                            .clip(CircleShape)
                                            .clickable(enabled = currentAvatarUrl.isNotBlank()) {
                                                viewModel.openImageViewer()
                                            },
                                        contentScale = ContentScale.Crop
                                    )
                                    if (uiState.isEditing) {
                                        YhSurface(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clickable(enabled = !uiState.isUploadingAvatar) {
                                                    imagePickerLauncher.launch("image/*")
                                                },
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primary,
                                            shadowElevation = 4.dp
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                if (uiState.isUploadingAvatar) {
                                                    YhCircularProgressIndicator(
                                                        modifier = Modifier.size(16.dp),
                                                        strokeWidth = 2.dp
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.PhotoCamera,
                                                        contentDescription = "编辑头像",
                                                        tint = MaterialTheme.colorScheme.onPrimary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    {
                        if (uiState.isEditing) {
                            GroupSettingsTextFieldItem(
                                icon = Icons.Default.Edit,
                                title = "群聊名称",
                                value = uiState.editedName,
                                onValueChange = viewModel::updateEditedName
                            )
                        } else {
                            GroupSettingsValueItem(
                                icon = Icons.Default.Edit,
                                title = "群聊名称",
                                value = groupInfo.name
                            )
                        }
                    },
                    {
                        if (uiState.isEditing) {
                            GroupSettingsTextFieldItem(
                                icon = Icons.Default.Info,
                                title = "群聊简介",
                                value = uiState.editedIntroduction,
                                onValueChange = viewModel::updateEditedIntroduction,
                                maxLines = 3
                            )
                        } else {
                            GroupSettingsValueItem(
                                icon = Icons.Default.Info,
                                title = "群聊简介",
                                value = groupInfo.introduction.ifBlank { "未设置" }
                            )
                        }
                    }
                )
            )
        }

        item {
            SettingsGroup(
                title = "群聊设置",
                items = listOf(
                    {
                        GroupSettingsSwitchItem(
                            icon = Icons.Default.Check,
                            title = "进群免审核",
                            subtitle = "允许用户直接加入群聊，无需审核",
                            checked = if (uiState.isEditing) uiState.editedDirectJoin else groupInfo.directJoin,
                            onCheckedChange = viewModel::updateEditedDirectJoin,
                            enabled = uiState.isEditing
                        )
                    },
                    {
                        GroupSettingsSwitchItem(
                            icon = Icons.Default.Info,
                            title = "查看历史消息",
                            subtitle = "允许新成员查看群聊历史消息",
                            checked = if (uiState.isEditing) uiState.editedHistoryMsg else groupInfo.historyMsgEnabled,
                            onCheckedChange = viewModel::updateEditedHistoryMsg,
                            enabled = uiState.isEditing
                        )
                    },
                    {
                        GroupSettingsSwitchItem(
                            icon = Icons.Default.Lock,
                            title = "私有群聊",
                            subtitle = "设置为私有群聊",
                            checked = if (uiState.isEditing) uiState.editedPrivate else groupInfo.isPrivate,
                            onCheckedChange = viewModel::updateEditedPrivate,
                            enabled = uiState.isEditing
                        )
                    },
                    {
                        if (uiState.isEditing) {
                            GroupSettingsTextFieldItem(
                                icon = Icons.Default.Edit,
                                title = "群聊分类",
                                value = uiState.editedCategoryName,
                                onValueChange = viewModel::updateEditedCategoryName
                            )
                        } else {
                            GroupSettingsValueItem(
                                icon = Icons.Default.Edit,
                                title = "群聊分类",
                                value = groupInfo.categoryName.ifBlank { "未设置" }
                            )
                        }
                    },
                    {
                        GroupSettingsValueItem(
                            icon = Icons.Default.Lock,
                            title = "群口令",
                            value = groupInfo.groupCode?.takeIf { it.isNotBlank() } ?: "未设置",
                            onClick = viewModel::openKeywordDialog
                        )
                    }
                )
            )
        }

        item {
            SettingsGroup(
                title = "消息管理",
                items = listOf(
                    {
                        GroupSettingsValueItem(
                            icon = Icons.Default.Delete,
                            title = "消息自动销毁",
                            value = autoDeleteMessageText(groupInfo.autoDeleteMessage),
                            onClick = { viewModel.showAutoDeleteMessageDialog() }
                        )
                    },
                    {
                        GroupSettingsValueItem(
                            icon = Icons.Default.Edit,
                            title = "消息类型限制",
                            value = if (groupInfo.limitedMsgType.isEmpty()) {
                                "未设置限制"
                            } else {
                                "已限制 ${groupInfo.limitedMsgType.split(",").size} 种消息类型"
                            },
                            onClick = { viewModel.showMessageTypeLimitDialog() }
                        )
                    },
                    {
                        GroupSettingsSwitchItem(
                            icon = Icons.Default.People,
                            title = "隐藏群成员",
                            subtitle = "开启后，群成员列表将对普通成员隐藏",
                            checked = groupInfo.hideGroupMembers,
                            onCheckedChange = viewModel::setHideGroupMembers,
                            enabled = !uiState.isSettingHideGroupMembers,
                            showLoading = uiState.isSettingHideGroupMembers
                        )
                    },
                    {
                        GroupSettingsSwitchItem(
                            icon = Icons.Default.Folder,
                            title = "锁定群云盘",
                            subtitle = "开启后，群成员无法上传文件到群云盘",
                            checked = groupInfo.denyMembersUploadToGroupDisk,
                            onCheckedChange = viewModel::setDenyMembersUploadToGroupDisk,
                            enabled = !uiState.isSettingDenyMembersUploadToGroupDisk,
                            showLoading = uiState.isSettingDenyMembersUploadToGroupDisk
                        )
                    }
                )
            )
        }

        item {
            SettingsGroup(
                title = "管理",
                items = listOf(
                    {
                        SettingsItemCell(
                            icon = Icons.Default.Edit,
                            title = "群组标签",
                            subtitle = "管理群组成员标签",
                            onClick = {
                                GroupTagManagementActivity.start(
                                    context,
                                    groupInfo.groupId,
                                    groupInfo.name
                                )
                            }
                        )
                    },
                    {
                        SettingsItemCell(
                            icon = Icons.Default.Settings,
                            title = "群聊机器人",
                            subtitle = "查看和管理群聊中的机器人",
                            onClick = {
                                GroupBotManagementActivity.start(
                                    context,
                                    groupInfo.groupId,
                                    groupInfo.name
                                )
                            }
                        )
                    }
                )
            )
        }
        item {
            SettingsGroup(
                title = "群聊信息",
                items = listOf(
                    {
                        GroupSettingsValueItem(
                            icon = Icons.Default.Info,
                            title = "群ID",
                            value = groupInfo.groupId
                        )
                    },
                    {
                        GroupSettingsValueItem(
                            icon = Icons.Default.People,
                            title = "成员数量",
                            value = "${groupInfo.memberCount} 人"
                        )
                    },
                    {
                        GroupSettingsValueItem(
                            icon = Icons.Default.Person,
                            title = "创建者",
                            value = groupInfo.createBy
                        )
                    },
                    {
                        GroupSettingsValueItem(
                            icon = Icons.Default.Info,
                            title = "所属社区",
                            value = groupInfo.communityName.ifBlank { "未关联社区" }
                        )
                    }
                )
            )
        }

        uiState.saveError?.let { error ->
            item {
                StatusMessageCard(
                    text = error,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        if (uiState.isSaveSuccess) {
            item {
                StatusMessageCard(
                    text = "保存成功",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }

    if (uiState.showImageViewer && currentAvatarUrl.isNotBlank()) {
        ImageViewer(
            imageUrl = currentAvatarUrl,
            onDismiss = viewModel::dismissImageViewer
        )
    }

    if (uiState.showKeywordDialog) {
        YhAlertDialog(
            onDismissRequest = viewModel::dismissKeywordDialog,
            title = { Text("设置群口令") },
            text = {
                YhOutlinedTextField(
                    value = uiState.keywordInput,
                    onValueChange = viewModel::updateKeywordInput,
                    label = { Text("群口令") },
                    enabled = !uiState.isSettingKeyword,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                YhButton(
                    onClick = {
                        viewModel.confirmKeyword(groupInfo.groupId)
                    },
                    enabled = !uiState.isSettingKeyword
                ) {
                    if (uiState.isSettingKeyword) {
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
                    onClick = viewModel::dismissKeywordDialog,
                    enabled = !uiState.isSettingKeyword
                ) {
                    Text("取消")
                }
            }
        )
    }

    if (uiState.showMessageTypeLimitDialog) {
        MessageTypeLimitDialog(
            selectedTypes = uiState.selectedMessageTypes,
            isLoading = uiState.isSettingMessageTypeLimit,
            onToggleType = viewModel::toggleMessageType,
            onConfirm = viewModel::confirmMessageTypeLimit,
            onDismiss = viewModel::dismissMessageTypeLimitDialog
        )
    }

    if (uiState.showAutoDeleteMessageDialog) {
        val options = listOf(
            0 to "永久不删",
            90 to "2个月",
            365 to "1年",
            730 to "2年"
        )

        YhAlertDialog(
            onDismissRequest = {
                if (!uiState.isSettingAutoDeleteMessage) {
                    viewModel.dismissAutoDeleteMessageDialog()
                }
            },
            title = { Text("消息自动销毁") },
            text = {
                Column {
                    options.forEach { (value, label) ->
                        YhTextButton(
                            onClick = {
                                if (!uiState.isSettingAutoDeleteMessage) {
                                    viewModel.setAutoDeleteMessage(groupInfo.groupId, value)
                                }
                            },
                            enabled = !uiState.isSettingAutoDeleteMessage,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(label)
                                if (groupInfo.autoDeleteMessage.toInt() == value) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null
                                    )
                                }
                            }
                        }
                    }
                    if (uiState.isSettingAutoDeleteMessage) {
                        Spacer(modifier = Modifier.height(8.dp))
                        YhCircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                YhTextButton(
                    onClick = { viewModel.dismissAutoDeleteMessageDialog() },
                    enabled = !uiState.isSettingAutoDeleteMessage
                ) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun GroupSettingsValueItem(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: (() -> Unit)? = null
) {
    SettingsCustomItem(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (onClick != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun GroupSettingsTextFieldItem(
    icon: ImageVector,
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    maxLines: Int = 1
) {
    SettingsCustomItem {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            YhOutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                maxLines = maxLines,
                singleLine = maxLines == 1
            )
        }
    }
}

@Composable
private fun GroupSettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean,
    showLoading: Boolean = false
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
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.6f)
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
private fun StatusMessageCard(
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    YhSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = containerColor,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Text(
            text = text,
            color = contentColor,
            modifier = Modifier.padding(16.dp)
        )
    }
}

private fun autoDeleteMessageText(days: Long): String {
    return when (days.toInt()) {
        0 -> "永久不删"
        90 -> "2个月"
        365 -> "1年"
        730 -> "2年"
        else -> if (days <= 0) "永久不删" else "${days}天"
    }
}

@Composable
private fun MessageTypeLimitDialog(
    selectedTypes: Set<Int>,
    isLoading: Boolean,
    onToggleType: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val messageTypes = listOf(
        1 to "文本消息",
        2 to "图片消息",
        3 to "Markdown消息",
        4 to "文件消息",
        6 to "帖子消息",
        7 to "表情消息",
        8 to "HTML消息",
        10 to "视频消息",
        11 to "语音消息",
        13 to "语音通话",
        14 to "A2UI消息"
    )

    YhAlertDialog(
        onDismissRequest = if (!isLoading) onDismiss else { {} },
        title = { Text("消息类型限制") },
        text = {
            LazyColumn {
                item {
                    Text(
                        text = "选择要限制的消息类型：",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(messageTypes, key = { it.first }) { (type, name) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isLoading) { onToggleType(type) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        YhCheckbox(
                            checked = selectedTypes.contains(type),
                            onCheckedChange = { onToggleType(type) },
                            enabled = !isLoading
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            YhButton(
                onClick = onConfirm,
                enabled = !isLoading
            ) {
                if (isLoading) {
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
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("取消")
            }
        }
    )
}
