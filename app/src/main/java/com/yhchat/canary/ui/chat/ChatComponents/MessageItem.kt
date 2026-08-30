package com.yhchat.canary.ui.chat.ChatComponents

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.LocalIndication
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yhchat.canary.data.model.ChatMessage
import com.yhchat.canary.ui.adaptive.YhAlertDialog
import com.yhchat.canary.ui.adaptive.YhCheckbox
import com.yhchat.canary.ui.adaptive.YhIcon as Icon
import com.yhchat.canary.ui.adaptive.YhIconButton
import com.yhchat.canary.ui.adaptive.YhOutlinedButton
import com.yhchat.canary.ui.adaptive.YhOutlinedTextField
import com.yhchat.canary.ui.adaptive.YhSurface
import com.yhchat.canary.ui.adaptive.YhText as Text
import com.yhchat.canary.ui.adaptive.YhTextButton
import com.yhchat.canary.ui.components.ImageUtils
import com.yhchat.canary.ui.components.htmltext.AdvancedHtmlRenderer
import com.yhchat.canary.ui.components.rememberBooleanPreference
import com.yhchat.canary.ui.components.rememberMessageActionMenuLauncher
import com.yhchat.canary.ui.webview.WebViewActivity
import com.yhchat.canary.ui.share.ExternalShareUtils
import com.yhchat.canary.ui.chat.ChatComponents.appendToAutoQueue
import com.yhchat.canary.utils.FavoriteMessageStore
import com.yhchat.canary.utils.UnifiedLinkHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 消息项
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageItem(
    message: ChatMessage,
    isMyMessage: Boolean,
    conversationChatType: Int,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    isMultiSelectMode: Boolean = false,
    onSelectionToggle: (() -> Unit)? = null,
    onMultiSelect: (() -> Unit)? = null,
    onImageClick: (String) -> Unit = {},
    onAvatarClick: (String, String, Int) -> Unit = { _, _, _ -> },
    onAvatarLongClick: (String, String) -> Unit = { _, _ -> },
    onAddExpression: (String) -> Unit = {},
    onQuote: (String, String) -> Unit = { _, _ -> },
    onForward: (ChatMessage) -> Unit = {},
    onRecall: (String) -> Unit = {},
    onEdit: (ChatMessage) -> Unit = {},
    onBlockUser: (String, String, String?) -> Unit = { _, _, _ -> },
    onSpeechToText: (String) -> Unit = {},
    onPlusOne: (ChatMessage) -> Unit = {},
    onQuoteMessageClick: (String) -> Unit = {},
    memberPermission: Int? = null,
    groupOwnerId: String? = null,
    groupAdminIds: List<String> = emptyList(),
    isCollapsed: Boolean = false,
    onToggleCollapse: (() -> Unit)? = null,
    isStreaming: Boolean = false,
    enableContextMenu: Boolean = true
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    var showContextMenuDialog by remember { mutableStateOf(false) }

    var showFreeCopyDialog by remember { mutableStateOf(false) }
    var freeCopyText by remember { mutableStateOf("") }
    val favoriteMessage: () -> Unit = {
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                FavoriteMessageStore.saveMessage(message)
            }
            Toast.makeText(
                context,
                if (result.isSuccess) "已收藏" else "收藏失败：${result.exceptionOrNull()?.message ?: "未知错误"}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    val openHtmlInInternalBrowser: () -> Unit = remember(message.msgId, message.content.text) {
        {
            val html = message.content.text
            if (!html.isNullOrBlank()) {
                WebViewActivity.startHtml(
                    context = context,
                    html = html,
                    title = "HTML消息",
                    baseUrl = "https://www.yhchat.com/"
                )
            }
        }
    }
    val messageOperationText = remember(message.contentType, message.content) {
        message.content.operationText(message.contentType)
    }
    val copyAllMessage: () -> Unit = {
        if (messageOperationText.isNotBlank()) {
            val clip = android.content.ClipData.newPlainText("message", messageOperationText)
            clipboardManager.setPrimaryClip(clip)
            Toast.makeText(context, "已复制全部", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "此消息没有可复制内容", Toast.LENGTH_SHORT).show()
        }
    }
    val quoteMessage: () -> Unit = {
        val quotedText = "${
            message.sender.name
        } : ${messageOperationText.ifBlank { "[${message.contentType.messageTypeLabel()}]" }}"
        onQuote(message.msgId, quotedText)
    }
    val externalShareMessage: () -> Unit = {
        ExternalShareUtils.shareMessage(context, message)
    }
    val addExpressionAction: (() -> Unit)? = if (message.contentType == 7) {
        {
            val expressionId = message.content.expressionId
            if (!expressionId.isNullOrEmpty()) {
                onAddExpression(expressionId)
                Toast.makeText(context, "已添加表情", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "无法获取表情ID", Toast.LENGTH_SHORT).show()
            }
        }
    } else {

        null
    }
    val saveAudioAction: (() -> Unit)? = if (message.contentType == 11 && !message.content.audioUrl.isNullOrBlank()) {
        {
            val audioUrl = message.content.audioUrl
            if (!audioUrl.isNullOrBlank()) {
                coroutineScope.launch {
                    val saved = saveVoiceToSavedAudios(context, audioUrl)
                    Toast.makeText(
                        context,
                        if (saved) "已保存语音" else "保存失败",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    } else {
        null
    }
    val addToPlaylistAction: (() -> Unit)? = if (message.contentType == 11 && !message.content.audioUrl.isNullOrBlank()) {
        {
            val audioUrl = message.content.audioUrl!!
            val senderName = message.sender.name
            val dur = message.content.audioTime ?: 0L
            val durMs = if (dur > 600L) dur else dur * 1000L
            val displayTitle = if (senderName.isNotBlank()) "$senderName 的语音" else "语音消息"
            coroutineScope.launch {
                appendToAutoQueue(context, displayTitle, audioUrl, "CHAT", durMs)
                Toast.makeText(context, "已加入播放列表", Toast.LENGTH_SHORT).show()
            }
        }
    } else {
        null
    }
    val speechToTextAction: (() -> Unit)? = if (message.contentType == 11 && !message.content.audioUrl.isNullOrBlank()) {
        {
            val audioUrl = message.content.audioUrl
            if (!audioUrl.isNullOrBlank()) {
                onSpeechToText(audioUrl)
            }
        }
    } else {
        null
    }
    val showRecall = when (conversationChatType) {
        2 -> true
        1, 3 -> isMyMessage
        else -> false
    }
    val showCustomActionContainer = rememberMessageActionMenuLauncher(
        enabled = !isMultiSelectMode,
        onCopyAll = copyAllMessage,
        onFreeCopy = if (message.contentType in listOf(1, 3, 8, 14) && messageOperationText.isNotBlank()) {
            {
                freeCopyText = messageOperationText
                showFreeCopyDialog = true
            }
        } else null,
        onQuote = quoteMessage,
        onForward = { onForward(message) },
        onEdit = if (message.contentType in listOf(1, 3, 8, 14) && isMyMessage) {
            { onEdit(message) }
        } else null,
        onDelete = if (showRecall) {
            { onRecall(message.msgId) }
        } else null,
        onPlusOne = { onPlusOne(message) },
        onFavorite = favoriteMessage,
        onMultiSelect = onMultiSelect,
        onOpenInInternalBrowser = if (message.contentType == 8) openHtmlInInternalBrowser else null,
        onAddExpression = addExpressionAction,
        onBlockUser = if (!isMyMessage) {
            { onBlockUser(message.sender.chatId, message.sender.name, message.sender.avatarUrl) }
        } else null,
        onSaveAudio = saveAudioAction,
        onSpeechToText = speechToTextAction,
        onExternalShare = externalShareMessage
    )
    var lastActionMenuOpenAt by remember(message.msgId) { mutableStateOf(0L) }
    val openMessageActions: () -> Unit = {
        if (!isMultiSelectMode) {
            val now = android.os.SystemClock.uptimeMillis()
            if (now - lastActionMenuOpenAt >= 350L) {
                lastActionMenuOpenAt = now
                if (enableContextMenu) {
                    showContextMenuDialog = true
                } else {
                    showCustomActionContainer()
                }
            }
        }
    }

    if (message.msgDeleteTime != null) {
        RecallMessageItem(message = message, modifier = modifier)
        return
    }

    if (message.contentType == 9) {
        TipMessageItem(message = message, modifier = modifier)
        return
    }

    var tagsExpanded by remember(message.msgId) { mutableStateOf(false) }
    val rowInteractionSource = remember(message.msgId) { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isHighlighted) Modifier.background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    RoundedCornerShape(8.dp)
                ) else Modifier
            )
            // Do not handle normal taps here, otherwise it will intercept inner clickable content
            // (e.g. HTML <a> buttons). Long-press is implemented via pointerInput to open the
            // context menu without consuming regular clicks.
            .then(
                if (isMultiSelectMode) {
                    Modifier.combinedClickable(onClick = { onSelectionToggle?.invoke() })
                } else {
                    Modifier
                }
            )
            .indication(rowInteractionSource, LocalIndication.current)
            .pointerInput(message.msgId, isMultiSelectMode, openMessageActions) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val press = PressInteraction.Press(down.position)
                    rowInteractionSource.tryEmit(press)
                    val longPress = awaitLongPressOrCancellation(down.id)
                    if (longPress != null) {
                        openMessageActions()
                    }
                    val up = waitForUpOrCancellation()
                    if (up == null) {
                        rowInteractionSource.tryEmit(PressInteraction.Cancel(press))
                    } else {
                        rowInteractionSource.tryEmit(PressInteraction.Release(press))
                    }
                }
            }
            .padding(end = if (isMyMessage) 8.dp else 0.dp, start = if (isMyMessage) 0.dp else 8.dp),
        horizontalArrangement = if (isMyMessage) Arrangement.End else Arrangement.Start
    ) {
        if (!isMyMessage) {
            AsyncImage(
                model = ImageUtils.createAvatarImageRequest(
                    context = LocalContext.current,
                    url = message.sender.avatarUrl
                ),
                contentDescription = message.sender.name,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .combinedClickable(
                        onClick = { onAvatarClick(message.sender.chatId, message.sender.name, message.sender.chatType) },
                        onLongClick = { onAvatarLongClick(message.sender.chatId, message.sender.name) }
                    ),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .then(
                    // 只有图片类型消息限制最大宽度
                    if (message.contentType == 2 || message.contentType == 4 || message.contentType == 6) {
                        Modifier.widthIn(max = 280.dp)
                    } else {
                        Modifier
                    }
                ),
            horizontalAlignment = if (isMyMessage) Alignment.End else Alignment.Start
        ) {
            SenderNameAndTags(
                message = message,
                isMyMessage = isMyMessage,
                conversationChatType = conversationChatType,
                tagsExpanded = tagsExpanded,
                onToggleExpand = { tagsExpanded = !tagsExpanded },
                memberPermission = memberPermission,
                groupOwnerId = groupOwnerId,
                groupAdminIds = groupAdminIds
            )

            if (message.cmd != null && message.cmd.name.isNotEmpty()) {
                Text(
                    text = "/${message.cmd.name}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            val bubbleShape = remember(isMyMessage) {
                RoundedCornerShape(
                    topStart = if (isMyMessage) 16.dp else 4.dp,
                    topEnd = if (isMyMessage) 4.dp else 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 16.dp
                )
            }
            val bubbleColor = if (isMyMessage) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }

            YhSurface(
                modifier = Modifier
                    .wrapContentWidth()
                    .then(
                        if (!isMyMessage) {
                            Modifier.border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                shape = bubbleShape
                            )
                        } else {
                            Modifier
                        }
                    )
                    .clip(bubbleShape)
                    // Long-press anywhere inside the bubble (including padding/empty area) to open context menu.
                    // This does not handle normal taps, so inner links/buttons keep working.
                    .pointerInput(message.msgId, isMultiSelectMode, openMessageActions) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val longPress = awaitLongPressOrCancellation(down.id)
                            if (longPress != null) {
                                openMessageActions()
                            }
                            waitForUpOrCancellation()
                        }
                    },
                color = bubbleColor,
                shape = bubbleShape,
                shadowElevation = if (isMyMessage) 0.dp else 1.dp
            ) {
                if (isCollapsed && onToggleCollapse != null) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        val textColor = if (isMyMessage) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

                        val htmlPreviewShown = message.contentType == 8 && !message.content.text.isNullOrBlank()

                        if (htmlPreviewShown) {
                            HtmlCollapsedPreview(
                                html = message.content.text.orEmpty(),
                                bubbleColor = bubbleColor,
                                modifier = Modifier.fillMaxWidth(),
                                onImageClick = onImageClick,
                                onLinkClick = { link -> UnifiedLinkHandler.handleLink(context, link) }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        } else {
                            message.content.text?.let { previewText ->
                                Text(
                                    text = previewText.take(200) + if (previewText.length > 200) "..." else "",
                                    color = textColor,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 3,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }

                        Text(
                            text = "消息过长（${message.content.text?.length ?: 0}字），已自动折叠",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        YhOutlinedButton(
                            onClick = onToggleCollapse,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("展开", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                } else {
                    MessageContentView(
                        message = message,
                        content = message.content,
                        contentType = message.contentType,
                        isMyMessage = isMyMessage,
                        conversationChatType = conversationChatType,
                        modifier = Modifier.padding(12.dp),
                        onImageClick = { imageUrl -> onImageClick(imageUrl) },
                        onLongClick = openMessageActions,
                        onQuoteMessageClick = onQuoteMessageClick,
                        onQuote = onQuote,
                        onEdit = onEdit,
                        onRecall = onRecall,
                        onPlusOne = onPlusOne,
                        onForward = onForward,
                        onFavorite = { _ -> favoriteMessage() },
                        onMultiSelect = onMultiSelect,
                        isStreaming = isStreaming
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = formatTimestamp(message.sendTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (message.editTime != null && message.editTime > 0) {
                    var showEditHistory by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier.combinedClickable(onClick = { showEditHistory = true }),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "已编辑",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "查看编辑历史",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                    }

                    if (showEditHistory) {
                        EditHistoryDialog(
                            msgId = message.msgId,
                            onDismiss = { showEditHistory = false }
                        )
                    }
                }
            }
        }

        if (isMyMessage) {
            Spacer(modifier = Modifier.width(8.dp))

            AsyncImage(
                model = ImageUtils.createAvatarImageRequest(
                    context = LocalContext.current,
                    url = message.sender.avatarUrl
                ),
                contentDescription = "我",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .combinedClickable(
                        onClick = { onAvatarClick(message.sender.chatId, message.sender.name, message.sender.chatType) },
                        onLongClick = { onAvatarLongClick(message.sender.chatId, message.sender.name) }
                    ),
                contentScale = ContentScale.Crop
            )
        }
    }



    if (showContextMenuDialog) {
        MessageContextMenu(
            message = message,
            showRecall = showRecall,
            onDismiss = { showContextMenuDialog = false },
            onCopyAll = {
                copyAllMessage()
                showContextMenuDialog = false
            },
            onFreeCopy = {
                freeCopyText = messageOperationText
                showContextMenuDialog = false
                showFreeCopyDialog = true
            },
            onQuote = {
                quoteMessage()
                showContextMenuDialog = false
            },
            onForward = {
                onForward(message)
                showContextMenuDialog = false
            },
            onRecall = {
                onRecall(message.msgId)
                showContextMenuDialog = false
            },
            onEdit = if (message.contentType in listOf(1, 3, 8, 14) && isMyMessage) {
                {
                    onEdit(message)
                    showContextMenuDialog = false
                }
            } else null,
            onAddExpression = addExpressionAction?.let { action ->
                {
                    action()
                    showContextMenuDialog = false
                }
            },
            onBlockUser = if (!isMyMessage) {
                {
                    onBlockUser(message.sender.chatId, message.sender.name, message.sender.avatarUrl)
                    showContextMenuDialog = false
                }
            } else null,
            onSaveAudio = saveAudioAction?.let { action ->
                {
                    action()
                    showContextMenuDialog = false
                }
            },
            onAddToPlaylist = addToPlaylistAction?.let { action ->
                {
                    action()
                    showContextMenuDialog = false
                }
            },
            onSpeechToText = speechToTextAction?.let { action ->
                {
                    action()
                    showContextMenuDialog = false
                }
            },
            onPlusOne = {
                onPlusOne(message)
                showContextMenuDialog = false
            },
            onFavorite = {
                favoriteMessage()
                showContextMenuDialog = false
            },
            onMultiSelect = {
                onMultiSelect?.invoke()
                showContextMenuDialog = false
            },
            onOpenInInternalBrowser = if (message.contentType == 8) {
                {
                    openHtmlInInternalBrowser()
                    showContextMenuDialog = false
                }
            } else null,
            onExternalShare = {
                externalShareMessage()
                showContextMenuDialog = false
            }
        )
    }

    if (showFreeCopyDialog) {
        YhAlertDialog(
            onDismissRequest = { showFreeCopyDialog = false },
            title = { Text("自由复制") },
            text = {
                YhOutlinedTextField(
                    value = freeCopyText,
                    onValueChange = { freeCopyText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("内容") },
                    singleLine = false
                )
            },
            confirmButton = {
                YhTextButton(
                    onClick = {
                        val clip = android.content.ClipData.newPlainText("message", freeCopyText)
                        clipboardManager.setPrimaryClip(clip)
                        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                        showFreeCopyDialog = false
                    }
                ) { Text("复制") }
            },
            dismissButton = {
                YhTextButton(onClick = { showFreeCopyDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
fun RecallMessageItem(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = "${message.sender.name} 在 ${formatRecallTime(message.msgDeleteTime!!)} 撤回了一条消息",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun TipMessageItem(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = message.content.text ?: "系统提示",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SenderNameAndTags(
    message: ChatMessage,
    isMyMessage: Boolean,
    conversationChatType: Int,
    tagsExpanded: Boolean,
    onToggleExpand: () -> Unit,
    memberPermission: Int? = null,
    groupOwnerId: String? = null,
    groupAdminIds: List<String> = emptyList()
) {
    val showOwnerBadge by rememberBooleanPreference("layout_settings", "chat_show_owner_badge", true)
    val showAdminBadge by rememberBooleanPreference("layout_settings", "chat_show_admin_badge", true)
    val showMemberTags by rememberBooleanPreference("layout_settings", "chat_show_member_tags", true)
    val shouldHideTagsForPrivateChat = conversationChatType == 1

    val tags = message.sender.tag ?: emptyList()
    val hasMultipleTags = tags.size > 1
    val resolvedMemberPermission = if (shouldHideTagsForPrivateChat) {
        null
    } else {
        resolveMemberPermission(
            senderChatId = message.sender.chatId,
            memberPermission = memberPermission,
            groupOwnerId = groupOwnerId,
            groupAdminIds = groupAdminIds
        )
    }

    Column(
        modifier = Modifier
            .wrapContentWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = if (isMyMessage) Alignment.End else Alignment.Start
    ) {
        Row(
            modifier = Modifier.wrapContentWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (isMyMessage)
                Arrangement.spacedBy(6.dp, Alignment.End)
            else
                Arrangement.spacedBy(6.dp, Alignment.Start)
        ) {
            Text(
                text = message.sender.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )

            if (message.sender.chatType == 3) {
                YhSurface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "机器人",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            when (resolvedMemberPermission) {
                100 -> if (showOwnerBadge) {
                    YhSurface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFFF9800)
                    ) {
                        Text(
                            text = "群主",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                2 -> if (showAdminBadge) {
                    YhSurface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF2196F3)
                    ) {
                        Text(
                            text = "管理员",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (showMemberTags && !shouldHideTagsForPrivateChat) {
                tags.take(1).forEach { tag ->
                    YhSurface(
                        shape = RoundedCornerShape(4.dp),
                        color = parseTagColor(tag.color)
                    ) {
                        Text(
                            text = tag.text,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                if (hasMultipleTags) {
                    YhIconButton(
                        onClick = onToggleExpand,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = if (tagsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (tagsExpanded) "收起标签" else "展开标签",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        if (showMemberTags && !shouldHideTagsForPrivateChat && tagsExpanded && tags.size > 1) {
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                modifier = Modifier.wrapContentWidth(),
                horizontalArrangement = if (isMyMessage)
                    Arrangement.spacedBy(6.dp, Alignment.End)
                else
                    Arrangement.spacedBy(6.dp, Alignment.Start),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                tags.drop(1).forEach { tag ->
                    YhSurface(
                        shape = RoundedCornerShape(4.dp),
                        color = parseTagColor(tag.color)
                    ) {
                        Text(
                            text = tag.text,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

fun Modifier.passThroughLongPress(onLongClick: () -> Unit): Modifier = composed {
    pointerInput(onLongClick) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val longPress = awaitLongPressOrCancellation(down.id)
            if (longPress != null) {
                onLongClick()
                waitForUpOrCancellation()
            }
        }
    }
}

private fun com.yhchat.canary.data.model.MessageContent.operationText(contentType: Int): String {
    return when (contentType) {
        2 -> listOfNotNull(text, imageUrl).joinToString("\n").ifBlank { imageUrl.orEmpty() }
        4 -> listOfNotNull(
            fileName?.let { "文件：$it" },
            fileSize?.let { "大小：${formatFileSize(it)}" },
            fileUrl
        ).joinToString("\n")
        6 -> listOfNotNull(
            postTitle?.let { "文章：$it" },
            postContent,
            postId?.let { "postId: $it" }
        ).joinToString("\n")
        7 -> listOfNotNull(
            text,
            imageUrl,
            stickerUrl,
            expressionId?.let { "expressionId: $it" },
            stickerItemId?.let { "stickerItemId: $it" },
            stickerPackId?.let { "stickerPackId: $it" }
        ).joinToString("\n")
        10 -> videoUrl.orEmpty()
        11 -> listOfNotNull(
            audioUrl,
            audioTime?.let { "时长：${formatAudioDuration(it)}" }
        ).joinToString("\n")
        else -> listOfNotNull(
            text,
            imageUrl,
            fileUrl,
            videoUrl,
            audioUrl,
            postTitle,
            postContent,
            stickerUrl
        ).joinToString("\n")
    }
}

private fun Int.messageTypeLabel(): String {
    return when (this) {
        2 -> "图片消息"
        4 -> "文件消息"
        5 -> "表单消息"
        6 -> "文章消息"
        7 -> "表情消息"
        10 -> "视频消息"
        11 -> "语音消息"
        14 -> "A2UI消息"
        else -> "消息"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AnimatedMessageItem(
    message: ChatMessage,
    isMyMessage: Boolean,
    conversationChatType: Int,
    modifier: Modifier = Modifier,
    enableAnimations: Boolean = true,
    isHighlighted: Boolean = false,
    isMultiSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onSelectionToggle: ((String) -> Unit)? = null,
    onMultiSelect: (() -> Unit)? = null,
    onImageClick: (String) -> Unit = {},
    onAvatarClick: (String, String, Int) -> Unit = { _, _, _ -> },
    onAvatarLongClick: (String, String) -> Unit = { _, _ -> },
    onAddExpression: (String) -> Unit = {},
    onQuote: (String, String) -> Unit = { _, _ -> },
    onForward: (ChatMessage) -> Unit = {},
    onRecall: (String) -> Unit = {},
    onEdit: (ChatMessage) -> Unit = {},
    onBlockUser: (String, String, String?) -> Unit = { _, _, _ -> },
    onSpeechToText: (String) -> Unit = {},
    onPlusOne: (ChatMessage) -> Unit = {},
    onQuoteMessageClick: (String) -> Unit = {},
    memberPermission: Int? = null,
    groupOwnerId: String? = null,
    groupAdminIds: List<String> = emptyList(),
    isCollapsed: Boolean = false,
    onToggleCollapse: (() -> Unit)? = null,
    isStreaming: Boolean = false,
    enableContextMenu: Boolean = true
) {
    val isNewMessage = remember(message.msgId) {
        val currentTime = System.currentTimeMillis()
        val messageTime = message.sendTime
        currentTime - messageTime < 2000
    }

    var isVisible by remember(message.msgId) { mutableStateOf(!isNewMessage) }

    LaunchedEffect(message.msgId) {
        if (isNewMessage) {
            isVisible = true
        }
    }

    if (!enableAnimations) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isMultiSelectMode) Modifier.combinedClickable(onClick = { onSelectionToggle?.invoke(message.msgId) }) else Modifier),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isMultiSelectMode) {
                YhCheckbox(
                    checked = isSelected,
                    onCheckedChange = { onSelectionToggle?.invoke(message.msgId) },
                    modifier = Modifier
                        .padding(start = 4.dp, end = 4.dp)
                        .size(36.dp)
                )
            }

            Box(modifier = if (isMultiSelectMode) Modifier.weight(1f) else Modifier.fillMaxWidth()) {
                MessageItem(
                    message = message,
                    isMyMessage = isMyMessage,
                    conversationChatType = conversationChatType,
                    modifier = Modifier.fillMaxWidth(),
                    isHighlighted = isHighlighted,
                    isMultiSelectMode = isMultiSelectMode,
                    onSelectionToggle = { onSelectionToggle?.invoke(message.msgId) },
                    onMultiSelect = onMultiSelect,
                    onImageClick = onImageClick,
                    onAvatarClick = onAvatarClick,
                    onAvatarLongClick = onAvatarLongClick,
                    onAddExpression = onAddExpression,
                    onQuote = onQuote,
                    onForward = onForward,
                    onRecall = onRecall,
                    onEdit = onEdit,
                    onBlockUser = onBlockUser,
                    onSpeechToText = onSpeechToText,
                    onPlusOne = onPlusOne,
                    onQuoteMessageClick = onQuoteMessageClick,
                    memberPermission = memberPermission,
                    groupOwnerId = groupOwnerId,
                    groupAdminIds = groupAdminIds,
                    isCollapsed = isCollapsed,
                    onToggleCollapse = onToggleCollapse,
                    isStreaming = isStreaming,
                    enableContextMenu = enableContextMenu
                )
            }
        }
        return
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { fullHeight -> fullHeight / 2 },
            animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
        ),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isMultiSelectMode) Modifier.combinedClickable(onClick = { onSelectionToggle?.invoke(message.msgId) }) else Modifier),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isMultiSelectMode) {
                YhCheckbox(
                    checked = isSelected,
                    onCheckedChange = { onSelectionToggle?.invoke(message.msgId) },
                    modifier = Modifier
                        .padding(start = 4.dp, end = 4.dp)
                        .size(36.dp)
                )
            }

            Box(modifier = if (isMultiSelectMode) Modifier.weight(1f) else Modifier.fillMaxWidth()) {
                MessageItem(
                    message = message,
                    isMyMessage = isMyMessage,
                    conversationChatType = conversationChatType,
                    modifier = Modifier.fillMaxWidth(),
                    isHighlighted = isHighlighted,
                    isMultiSelectMode = isMultiSelectMode,
                    onSelectionToggle = { onSelectionToggle?.invoke(message.msgId) },
                    onMultiSelect = onMultiSelect,
                    onImageClick = onImageClick,
                    onAvatarClick = onAvatarClick,
                    onAvatarLongClick = onAvatarLongClick,
                    onAddExpression = onAddExpression,
                    onQuote = onQuote,
                    onForward = onForward,
                    onRecall = onRecall,
                    onEdit = onEdit,
                    onBlockUser = onBlockUser,
                    onSpeechToText = onSpeechToText,
                    onPlusOne = onPlusOne,
                    onQuoteMessageClick = onQuoteMessageClick,
                    memberPermission = memberPermission,
                    groupOwnerId = groupOwnerId,
                    groupAdminIds = groupAdminIds,
                    isCollapsed = isCollapsed,
                    onToggleCollapse = onToggleCollapse,
                    isStreaming = isStreaming,
                    enableContextMenu = enableContextMenu
                )
            }
        }
    }
}

@Composable
private fun HtmlCollapsedPreview(
    html: String,
    bubbleColor: Color,
    modifier: Modifier = Modifier,
    onImageClick: (String) -> Unit,
    onLinkClick: (String) -> Unit
) {
    val plainTextLength = remember(html) {
        html.replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .length
    }
    val showBottomFade = remember(html, plainTextLength) {
        plainTextLength > 80 ||
            html.contains("<img", ignoreCase = true) ||
            html.contains("<table", ignoreCase = true) ||
            html.contains("<ul", ignoreCase = true) ||
            html.contains("<ol", ignoreCase = true) ||
            html.contains("<blockquote", ignoreCase = true) ||
            html.contains("<br", ignoreCase = true)
    }

    Box(
        modifier = modifier
            .heightIn(max = 200.dp)
            .clip(RoundedCornerShape(8.dp))
            .clipToBounds()
    ) {
        AdvancedHtmlRenderer(
            html = html,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = if (showBottomFade) 8.dp else 0.dp),
            onImageClick = onImageClick,
            onLinkClick = onLinkClick
        )

        if (showBottomFade) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, bubbleColor.copy(alpha = 0.95f))
                        )
                    )
            )
        }
    }
}

private fun resolveMemberPermission(
    senderChatId: String,
    memberPermission: Int?,
    groupOwnerId: String?,
    groupAdminIds: List<String>
): Int? {
    if (memberPermission != null) return memberPermission
    if (groupOwnerId != null && senderChatId == groupOwnerId) return 100
    if (groupAdminIds.contains(senderChatId)) return 2
    return null
}

fun formatTimestamp(timestamp: Long): String {
    val date = Date(timestamp)
    val now = Date()

    val nowCalendar = Calendar.getInstance().apply { time = now }
    val msgCalendar = Calendar.getInstance().apply { time = date }

    val todayCalendar = Calendar.getInstance().apply {
        time = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    val isSameYear = nowCalendar.get(Calendar.YEAR) == msgCalendar.get(Calendar.YEAR)

    return when {
        date.after(todayCalendar.time) -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        date.after(Date(todayCalendar.timeInMillis - 86400000)) -> "昨天 " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        isSameYear -> SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(date)
        else -> SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(date)
    }
}

fun formatRecallTime(timestamp: Long): String {
    val date = Date(timestamp)
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
}

fun parseTagColor(colorString: String): Color {
    return try {
        val cleanColor = colorString.trim()
        if (cleanColor.startsWith("#")) {
            val hex = cleanColor.substring(1)
            when (hex.length) {
                6 -> {
                    val rgb = hex.toLong(16)
                    Color(
                        red = ((rgb shr 16) and 0xFF) / 255f,
                        green = ((rgb shr 8) and 0xFF) / 255f,
                        blue = (rgb and 0xFF) / 255f
                    )
                }
                8 -> {
                    val argb = hex.toLong(16)
                    Color(
                        red = ((argb shr 16) and 0xFF) / 255f,
                        green = ((argb shr 8) and 0xFF) / 255f,
                        blue = (argb and 0xFF) / 255f,
                        alpha = ((argb shr 24) and 0xFF) / 255f
                    )
                }
                else -> Color.Gray
            }
        } else {
            Color.Gray
        }
    } catch (_: Exception) {
        Color.Gray
    }
}
