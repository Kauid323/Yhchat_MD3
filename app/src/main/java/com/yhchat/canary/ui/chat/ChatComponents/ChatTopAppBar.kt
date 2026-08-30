package com.yhchat.canary.ui.chat.ChatComponents

import android.content.Intent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yhchat.canary.ui.chat.ChatUiState
import com.yhchat.canary.ui.adaptive.YhIcon as Icon
import com.yhchat.canary.ui.adaptive.YhIconButton
import com.yhchat.canary.ui.adaptive.YhSurface
import com.yhchat.canary.ui.adaptive.YhText as Text
import com.yhchat.canary.ui.adaptive.isMiuixUi
import com.yhchat.canary.ui.components.ImageUtils
import com.yhchat.canary.ui.components.isLargeScreenLayout
import com.yhchat.canary.ui.components.rememberBooleanPreference

/**
 * 聊天界面顶部应用栏
 */
@Composable
fun ChatTopAppBar(
    chatId: String,
    chatType: Int,
    chatName: String,
    avatarUrl: String? = null,
    uiState: ChatUiState,
    showTtsButton: Boolean,
    showRefreshButton: Boolean,
    showLiveButton: Boolean,
    onBackClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onTtsClick: () -> Unit,
    onLiveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val forceDisableLargeScreenMode by rememberBooleanPreference(
        preferencesName = "layout_settings",
        key = "force_disable_large_screen_mode",
        defaultValue = false
    )
    val isLargeScreen = isLargeScreenLayout(
        configuration = configuration,
        forceDisableLargeScreenMode = forceDisableLargeScreenMode
    )
    val safeChatName = chatName.ifBlank { "聊天" }
    
    val hideBackButtonInLargeScreen by rememberBooleanPreference(
        preferencesName = "layout_settings",
        key = "chat_hide_back_button_large_screen",
        defaultValue = true
    )
    
    // 判断是否应该显示返回按钮
    val shouldShowBackButton = if (isLargeScreen) {
        !hideBackButtonInLargeScreen
    } else {
        true // 小屏模式下总是显示返回按钮
    }

    val avatarModel = remember(context, avatarUrl, chatType) {
        avatarUrl?.takeIf { it.isNotBlank() }?.let { url ->
            if (chatType == 3) {
                ImageUtils.createBotImageRequest(context, url)
            } else {
                ImageUtils.createAvatarImageRequest(context, url)
            }
        }
    }
    val topBarContainerColor = if (isMiuixUi) {
        top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surface
    }

    val onTitleAreaClick = remember(context, chatId, chatType, safeChatName) {
        {
            when (chatType) {
                1 -> {
                    com.yhchat.canary.ui.user.UserDetailActivity.start(
                        context = context,
                        userId = chatId,
                        userName = safeChatName
                    )
                }
                2 -> {
                    val intent = Intent(context, com.yhchat.canary.ui.group.GroupInfoActivity::class.java)
                    intent.putExtra(com.yhchat.canary.ui.group.GroupInfoActivity.EXTRA_GROUP_ID, chatId)
                    intent.putExtra(com.yhchat.canary.ui.group.GroupInfoActivity.EXTRA_GROUP_NAME, safeChatName)
                    context.startActivity(intent)
                }
                3 -> {
                    com.yhchat.canary.ui.bot.BotDetailActivity.start(
                        context = context,
                        botId = chatId,
                        botName = safeChatName,
                        chatType = chatType
                    )
                }
            }
        }
    }

    // When the back button is hidden (usually on large screens), Material3 TopAppBar still applies
    // a fixed title inset reserved for the navigation icon, leaving an empty gap. Use a custom bar
    // so the avatar/title can truly align to the left edge.
    if (!shouldShowBackButton) {
        YhSurface(
            shadowElevation = 0.dp,
            modifier = modifier
                .background(topBarContainerColor)
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onTitleAreaClick)
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = avatarModel,
                            contentDescription = "会话头像",
                            modifier = Modifier
                                .size(36.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape),
                            contentScale = ContentScale.Crop,
                            error = painterResource(id = com.yhchat.canary.R.drawable.ic_person)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = safeChatName,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (chatType == 2 && uiState.groupMemberCount > 0) {
                                Text(
                                    text = "${uiState.groupMemberCount} 人",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            if (chatType == 3) {
                                val botInfo = uiState.botInfo
                                if (botInfo != null) {
                                    Text(
                                        text = "${botInfo.data.headcount} 人使用",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        if (showLiveButton) {
                            Spacer(modifier = Modifier.width(10.dp))
                            LiveWaveButton(onClick = onLiveClick)
                        }
                    }
                }

                if (showRefreshButton) {
                    YhIconButton(onClick = onRefreshClick) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新消息"
                        )
                    }
                }
                if (showTtsButton) {
                    YhIconButton(onClick = onTtsClick) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = "文字转语音",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (chatType == 1) {
                    YhIconButton(onClick = {
                        android.util.// Log.d("ChatTopAppBar", "Opening user detail: chatId=$chatId, chatName=$chatName")
                        com.yhchat.canary.ui.user.UserDetailActivity.start(
                            context = context,
                            userId = chatId,
                            userName = safeChatName
                        )
                    }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "用户详情"
                        )
                    }
                }
                if (chatType == 2) {
                    YhIconButton(onClick = {
                        android.util.// Log.d("ChatTopAppBar", "Opening group info: chatId=$chatId, chatName=$chatName")
                        val intent = Intent(context, com.yhchat.canary.ui.group.GroupInfoActivity::class.java)
                        intent.putExtra(com.yhchat.canary.ui.group.GroupInfoActivity.EXTRA_GROUP_ID, chatId)
                        intent.putExtra(com.yhchat.canary.ui.group.GroupInfoActivity.EXTRA_GROUP_NAME, safeChatName)
                        context.startActivity(intent)
                    }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "群聊详情"
                        )
                    }
                }
                if (chatType == 3) {
                    YhIconButton(onClick = {
                        android.util.// Log.d("ChatTopAppBar", "Opening bot detail: chatId=$chatId, chatName=$chatName")
                        com.yhchat.canary.ui.bot.BotDetailActivity.start(
                            context = context,
                            botId = chatId,
                            botName = safeChatName,
                            chatType = chatType
                        )
                    }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "机器人信息"
                        )
                    }
                }
            }
        }
        return
    }

    YhSurface(
        shadowElevation = 0.dp,
        modifier = modifier
            .background(topBarContainerColor)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            YhIconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回"
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onTitleAreaClick)
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = avatarModel,
                    contentDescription = "会话头像",
                    modifier = Modifier
                        .size(36.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape),
                    contentScale = ContentScale.Crop,
                    error = painterResource(id = com.yhchat.canary.R.drawable.ic_person)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = safeChatName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (chatType == 2 && uiState.groupMemberCount > 0) {
                        Text(
                            text = "${uiState.groupMemberCount} 人",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    if (chatType == 3) {
                        val botInfo = uiState.botInfo
                        if (botInfo != null) {
                            Text(
                                text = "${botInfo.data.headcount} 人使用",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }

                if (showLiveButton) {
                    Spacer(modifier = Modifier.width(10.dp))
                    LiveWaveButton(onClick = onLiveClick)
                }
            }

            if (showRefreshButton) {
                YhIconButton(onClick = onRefreshClick) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "刷新消息"
                    )
                }
            }
            // TTS按钮（受布局设置控制）
            if (showTtsButton) {
                YhIconButton(onClick = onTtsClick) {
                    Icon(
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = "文字转语音",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            // 用户详情按钮（只在单聊时显示）
            if (chatType == 1) {
                YhIconButton(onClick = {
                    android.util.// Log.d("ChatTopAppBar", "Opening user detail: chatId=$chatId, chatName=$chatName")
                    com.yhchat.canary.ui.user.UserDetailActivity.start(
                        context = context,
                        userId = chatId,
                        userName = safeChatName
                    )
                }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "用户详情"
                    )
                }
            }
            // 群聊信息菜单（只在群聊时显示）
            if (chatType == 2) {
                YhIconButton(onClick = {
                    android.util.// Log.d("ChatTopAppBar", "Opening group info: chatId=$chatId, chatName=$chatName")
                    val intent = Intent(context, com.yhchat.canary.ui.group.GroupInfoActivity::class.java)
                    intent.putExtra(com.yhchat.canary.ui.group.GroupInfoActivity.EXTRA_GROUP_ID, chatId)
                    intent.putExtra(com.yhchat.canary.ui.group.GroupInfoActivity.EXTRA_GROUP_NAME, safeChatName)
                    context.startActivity(intent)
                }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "群聊详情"
                    )
                }
            }
            // 机器人信息菜单（只在机器人聊天时显示）
            if (chatType == 3) {
                YhIconButton(onClick = {
                    android.util.// Log.d("ChatTopAppBar", "Opening bot detail: chatId=$chatId, chatName=$chatName")
                    com.yhchat.canary.ui.bot.BotDetailActivity.start(
                        context = context,
                        botId = chatId,
                        botName = safeChatName,
                        chatType = chatType
                    )
                }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "机器人信息"
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveWaveButton(
    onClick: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "live_wave")
    val scales = listOf(0, 160, 320).mapIndexed { index, delayMillis ->
        transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 700, delayMillis = delayMillis, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "live_bar_$index"
        )
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        scales.forEachIndexed { index, scale ->
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 18.dp)
                    .graphicsLayer {
                        scaleY = scale.value
                    }
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (index == 1) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
                        }
                    )
            )
        }
    }
}
