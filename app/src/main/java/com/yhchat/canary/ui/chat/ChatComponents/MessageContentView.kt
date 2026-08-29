package com.yhchat.canary.ui.chat.ChatComponents

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import coil.compose.AsyncImage
import com.yhchat.canary.data.model.ChatMessage
import com.yhchat.canary.data.model.MessageContent
import com.yhchat.canary.ui.chat.ChatComponents.a2ui.model.A2UiSpec
import com.yhchat.canary.ui.chat.ChatComponents.a2ui.parser.looksLikeA2UiJson
import com.yhchat.canary.ui.chat.ChatComponents.a2ui.parser.parseA2UiSpec
import com.yhchat.canary.ui.adaptive.YhCircularProgressIndicator
import com.yhchat.canary.ui.adaptive.YhHorizontalDivider
import com.yhchat.canary.ui.adaptive.YhIcon as Icon
import com.yhchat.canary.ui.adaptive.YhIconButton
import com.yhchat.canary.ui.adaptive.YhLinearProgressIndicator
import com.yhchat.canary.ui.adaptive.YhSurface
import com.yhchat.canary.ui.adaptive.YhText as Text
import com.yhchat.canary.ui.community.PostDetailActivity
import com.yhchat.canary.ui.components.DownloadManager
import com.yhchat.canary.ui.components.DownloadState
import com.yhchat.canary.ui.components.ExpressionText
import com.yhchat.canary.ui.components.ImageUtils
import com.yhchat.canary.ui.components.LinkDetector
import com.yhchat.canary.ui.components.LinkText
import com.yhchat.canary.ui.components.MarkdownText
import com.yhchat.canary.ui.components.MessageSelectionContainer
import com.yhchat.canary.ui.components.htmltext.HtmlTextMessage
import com.yhchat.canary.ui.components.rememberBooleanPreference
import com.yhchat.canary.ui.share.ExternalShareUtils
import com.yhchat.canary.ui.webview.WebViewActivity
import com.yhchat.canary.utils.UnifiedLinkHandler
import org.json.JSONObject
import java.net.URL
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 消息内容视图
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageContentView(
    message: ChatMessage,
    content: MessageContent,
    contentType: Int,
    isMyMessage: Boolean,
    conversationChatType: Int = 1,
    modifier: Modifier = Modifier,
    enableHardware: Boolean = true, // 控制是否允许硬件位图
    onImageClick: (String) -> Unit = {},
    onLongClick: () -> Unit = {},
    onQuoteMessageClick: (String) -> Unit = {},
    onQuote: (String, String) -> Unit = { _, _ -> },
    onEdit: (ChatMessage) -> Unit = {},
    onRecall: (String) -> Unit = {},
    onPlusOne: (ChatMessage) -> Unit = {},
    onForward: (ChatMessage) -> Unit = {},
    onFavorite: (ChatMessage) -> Unit = {},
    onMultiSelect: (() -> Unit)? = null,
    isStreaming: Boolean = false
) {
    val textColor = if (isMyMessage) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val context = androidx.compose.ui.platform.LocalContext.current

    val showHtmlRawText by rememberBooleanPreference("message_settings", "show_html_raw_text", false)
    val showMarkdownRawText by rememberBooleanPreference("message_settings", "show_markdown_raw_text", false)
    val showInlineExpressions by rememberBooleanPreference("display_settings", "show_inline_expressions", true)
    val openHtmlInInternalBrowser = remember(message.msgId, content.text) {
        ({
            val html = content.text?.takeIf { it.isNotBlank() }
            if (html != null) {
                WebViewActivity.startHtml(
                    context = context,
                    html = html,
                    title = "HTML消息",
                    baseUrl = "https://www.yhchat.com/"
                )
            }
        })
    }

    Column(modifier = modifier) {
        when (contentType) {
            8 -> {
                // HTML消息
                content.text?.let { htmlContent ->
                    if (showHtmlRawText) {
                        // 显示HTML原文 - 使用MessageSelectionContainer
                        MessageSelectionContainer(
                            onCopyAll = {
                                // best-effort: copy raw html text
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("message", htmlContent)
                                clipboard.setPrimaryClip(clip)
                            },
                            onFreeCopy = {
                                // free copy UI is implemented by MessageContextMenu; keep container hook for parity
                            },
                            onQuote = { onQuote(message.msgId, htmlContent) },
                            onForward = { onForward(message) },
                            onEdit = if (message.contentType in listOf(1, 3, 8) && isMyMessage) {
                                { onEdit(message) }
                            } else null,
                            onDelete = if (isMyMessage) {
                                { onRecall(message.msgId) }
                            } else null,
                            onPlusOne = { onPlusOne(message) },
                            onFavorite = { onFavorite(message) },
                            onMultiSelect = onMultiSelect,
                            onOpenInInternalBrowser = openHtmlInInternalBrowser,
                            onExternalShare = { selectedText ->
                                ExternalShareUtils.shareText(context, selectedText?.takeIf { it.isNotBlank() } ?: htmlContent, message.chatId ?: message.recvId)
                            }
                        ) {
                            Text(
                                text = htmlContent,
                                color = textColor,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        // NOTE: don't wrap rich HTML renderer with SelectionContainer.
                        // SelectionContainer competes for pointer input and can make <a> clicks flaky.
                        // Keep HTML links clickable; long-press actions are available via the message context menu.
                        HtmlTextMessage(
                            html = htmlContent,
                            modifier = Modifier.fillMaxWidth(),
                            color = textColor,
                            style = MaterialTheme.typography.bodyMedium,
                            onImageClick = onImageClick,
                            onUriClick = { url ->
                                UnifiedLinkHandler.handleLink(context, url)
                            },
                            useAdvancedRenderer = true
                        )
                    }
                }
            }
            2 -> {
                // 图片消息
                content.imageUrl?.let { imageUrl ->
                    if (isTgsUrl(imageUrl)) {
                        TgsPreviewMessage(
                            url = imageUrl,
                            modifier = Modifier
                                .sizeIn(
                                    minWidth = 120.dp,
                                    maxWidth = 280.dp,
                                    maxHeight = 360.dp
                                )
                                .clip(RoundedCornerShape(8.dp))
                                .combinedClickable(
                                    onClick = { onImageClick(imageUrl) },
                                    onLongClick = onLongClick
                                )
                        )
                    } else {
                        AsyncImage(
                            model = ImageUtils.createImageRequest(
                                context = androidx.compose.ui.platform.LocalContext.current,
                                url = imageUrl,
                                enableHardware = enableHardware
                            ),
                            contentDescription = "图片",
                            modifier = Modifier
                                .sizeIn(
                                    minWidth = 120.dp,
                                    maxWidth = 280.dp,
                                    maxHeight = 360.dp
                                )
                                .clip(RoundedCornerShape(8.dp))
                                .combinedClickable(
                                    onClick = { onImageClick(imageUrl) },
                                    onLongClick = onLongClick
                                ),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    }
                }
                content.text?.let { text ->
                    if (text.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = text,
                            color = textColor,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            4 -> {
                // 文件消息
                content.fileName?.let { fileName ->
                    content.fileUrl?.let { fileUrl ->
                        val downloadState by remember(fileUrl) { 
                            androidx.compose.runtime.derivedStateOf { 
                                DownloadManager.getDownloadState(fileUrl) 
                            } 
                        }
                        
                        YhSurface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .combinedClickable(
                                    onClick = {
                                        when (downloadState) {
                                            is DownloadState.Idle, is DownloadState.Error, is DownloadState.Cancelled -> {
                                                DownloadManager.startDownload(
                                                    context = context,
                                                    fileUrl = fileUrl,
                                                    fileName = fileName,
                                                    fileSize = content.fileSize ?: 0L,
                                                    msgId = message.msgId
                                                )
                                            }
                                            is DownloadState.Completed -> {
                                                // 文件已下载完成，可以打开文件
                                                handleFileDownload(
                                                    context = context,
                                                    fileUrl = fileUrl,
                                                    fileName = fileName,
                                                    fileSize = content.fileSize ?: 0L,
                                                    msgId = message.msgId
                                                )
                                            }
                                            else -> {
                                                // 正在下载中，不做任何操作
                                            }
                                        }
                                    },
                                    onLongClick = onLongClick
                                ),
                            color = textColor.copy(alpha = 0.1f)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
            Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "文件",
                                        tint = textColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = fileName,
                                            color = textColor,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            content.fileSize?.let { size ->
                                                Text(
                                                    text = formatFileSize(size),
                                                    color = textColor.copy(alpha = 0.7f),
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                            
                                            // 显示下载状态文本
                                            when (val currentState = downloadState) {
                                                is DownloadState.Downloading -> {
                                                    if (currentState.total > 0) {
                                                        val percentage = (currentState.progress * 100 / currentState.total).coerceIn(0, 100)
                                                        Text(
                                                            text = " • $percentage%",
                                                            color = MaterialTheme.colorScheme.primary,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                    }
                                                }
                                                is DownloadState.Completed -> {
                                                    Text(
                                                        text = " • 已下载",
                                                        color = MaterialTheme.colorScheme.primary,
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                }
                                                is DownloadState.Error -> {
                                                    Text(
                                                        text = " • 下载失败",
                                                        color = MaterialTheme.colorScheme.error,
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                }
                                                is DownloadState.Cancelled -> {
                                                    Text(
                                                        text = " • 已取消",
                                                        color = textColor.copy(alpha = 0.7f),
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                }
                                                else -> {}
                                            }
                                        }
                                    }
                                    
                                    // 下载状态图标和取消按钮
                                    when (downloadState) {
                                        is DownloadState.Downloading -> {
                                            YhIconButton(
                                                onClick = {
                                                    DownloadManager.cancelDownload(context, fileUrl)
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
        Icon(
                                                    imageVector = Icons.Default.Cancel,
                                                    contentDescription = "取消下载",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        is DownloadState.Completed -> {
        Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "下载完成",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        is DownloadState.Error -> {
        Icon(
                                                imageVector = Icons.Default.Error,
                                                contentDescription = "下载失败",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        else -> {
        Icon(
                                                imageVector = Icons.Default.Download,
                                                contentDescription = "下载",
                                                tint = textColor.copy(alpha = 0.7f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                                
                                // 下载进度条
                                val currentState = downloadState
                                if (currentState is DownloadState.Downloading && currentState.total > 0) {
                                    YhLinearProgressIndicator(
                                        progress = (currentState.progress.toFloat() / currentState.total.toFloat()).coerceIn(0f, 1f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp)
                                            .padding(bottom = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            11 -> {
                // 语音消息
                content.audioUrl?.let { audioUrl ->
                    AudioMessageView(
                        audioUrl = audioUrl,
                        duration = content.audioTime ?: 0,
                        textColor = textColor,
                        senderName = "语音消息"
                    )
                }
            }
            14 -> {
                content.text?.let { text ->
                    val prefs = remember { context.getSharedPreferences("message_settings", Context.MODE_PRIVATE) }
                    var showA2UiRawText by remember { mutableStateOf(prefs.getBoolean("show_a2ui_raw_text", false)) }
                    
                    if (showA2UiRawText) {
                        // 显示A2UI原文
                        MessageSelectionContainer(
                            onQuote = { onQuote(message.msgId, text) },
                            onForward = { onForward(message) },
                            onEdit = if (message.contentType in listOf(1, 3, 8) && isMyMessage) {
                                { onEdit(message) }
                            } else null,
                            onDelete = if (isMyMessage) {
                                { onRecall(message.msgId) }
                            } else null,
                            onPlusOne = { onPlusOne(message) },
                            onFavorite = { onFavorite(message) },
                            onMultiSelect = onMultiSelect,
                            onExternalShare = { selectedText ->
                                ExternalShareUtils.shareText(context, selectedText?.takeIf { it.isNotBlank() } ?: text, message.chatId ?: message.recvId)
                            }
                        ) {
                            Text(
                                text = text,
                                color = textColor,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        // 使用 A2UiFormRenderer 解析和渲染 A2UI 消息
                        val a2UiSpecs = remember(text) { parseA2UiSpecsBySurface(text) }

                        if (a2UiSpecs.isNotEmpty()) {
                            // 提取A2UI JSON之外的文本内容
                            val (beforeText, afterText) = remember(text) {
                                extractTextAroundA2UiJson(text)
                            }

                            Column(modifier = Modifier.fillMaxWidth()) {
                                // 显示A2UI JSON之前的文本
                                if (beforeText.isNotBlank()) {
                                    Text(
                                        text = beforeText,
                                        color = textColor,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                // 显示A2UI表单
                                a2UiSpecs.forEachIndexed { index, spec ->
                                    if (index > 0) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }
                                    A2UiFormMessage(
                                        spec = spec,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                // 显示A2UI JSON之后的文本
                                if (afterText.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = afterText,
                                        color = textColor,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        } else if (text.isNotBlank()) {
                            MessageSelectionContainer(
                                onQuote = { onQuote(message.msgId, text) },
                                onForward = { onForward(message) },
                                onEdit = if (message.contentType in listOf(1, 3, 8) && isMyMessage) {
                                    { onEdit(message) }
                                } else null,
                                onDelete = if (isMyMessage) {
                                    { onRecall(message.msgId) }
                                } else null,
                                onPlusOne = { onPlusOne(message) },
                                onFavorite = { onFavorite(message) },
                                onMultiSelect = onMultiSelect,
                                onExternalShare = { selectedText ->
                                    ExternalShareUtils.shareText(context, selectedText?.takeIf { it.isNotBlank() } ?: text, message.chatId ?: message.recvId)
                                }
                            ) {
                                Text(
                                    text = text,
                                    color = textColor,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
            3 -> {
                // Markdown消息
                content.text?.let { markdownText ->
                    if (showMarkdownRawText) {
                        // 显示Markdown原文 - 使用MessageSelectionContainer
                        MessageSelectionContainer(
                            onQuote = { onQuote(message.msgId, markdownText) },
                            onForward = { onForward(message) },
                            onEdit = if (message.contentType in listOf(1, 3, 8) && isMyMessage) {
                                { onEdit(message) }
                            } else null,
                            onDelete = if (isMyMessage) {
                                { onRecall(message.msgId) }
                            } else null,
                            onPlusOne = { onPlusOne(message) },
                            onFavorite = { onFavorite(message) },
                            onMultiSelect = onMultiSelect,
                            onExternalShare = { selectedText ->
                                ExternalShareUtils.shareText(context, selectedText?.takeIf { it.isNotBlank() } ?: markdownText, message.chatId ?: message.recvId)
                            }
                        ) {
                            Text(
                                text = markdownText,
                                color = textColor,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        // 正常渲染Markdown - 使用MessageSelectionContainer包裹
                        MessageSelectionContainer(
                            onQuote = { onQuote(message.msgId, markdownText) },
                            onForward = { onForward(message) },
                            onEdit = if (message.contentType in listOf(1, 3, 8) && isMyMessage) {
                                { onEdit(message) }
                            } else null,
                            onDelete = if (isMyMessage) {
                                { onRecall(message.msgId) }
                            } else null,
                            onPlusOne = { onPlusOne(message) },
                            onFavorite = { onFavorite(message) },
                            onMultiSelect = onMultiSelect,
                            onExternalShare = { selectedText ->
                                ExternalShareUtils.shareText(context, selectedText?.takeIf { it.isNotBlank() } ?: markdownText, message.chatId ?: message.recvId)
                            }
                        ) {
                            MarkdownText(
                                markdown = markdownText,
                                textColor = if (isMyMessage) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                backgroundColor = Color.Transparent, // 使用透明背景，继承消息气泡背景
                                onImageClick = onImageClick,
                                enableTextSelection = false,
                                persistRenderState = !isStreaming,
                                modifier = Modifier
                                    .wrapContentWidth()
                            )
                        }
                    }
                }
            }
            6 -> {
                // 文章消息
                PostMessageView(
                    postId = content.postId,
                    postTitle = content.postTitle,
                    postContent = content.postContent,
                    postContentType = content.postContentType,
                    textColor = textColor,
                    isMyMessage = isMyMessage
                )
            }
            7 -> {
                // 表情消息 (包括表情包和个人收藏表情)
                val stickerPackId = content.stickerPackId
                val expressionId = content.expressionId

                // 判断是个人表情还是表情包
                val isPersonalExpression = expressionId != null && expressionId != "0"
                val isStickerPack = stickerPackId != null && stickerPackId != 0L

                content.imageUrl?.let { imageUrl ->
                    if (isTgsUrl(imageUrl)) {
                        TgsPreviewMessage(
                            url = imageUrl,
                            modifier = Modifier
                                .size(120.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .combinedClickable(
                                    onClick = {
                                        if (isPersonalExpression) {
                                            onImageClick(imageUrl)
                                        } else if (isStickerPack) {
                                            com.yhchat.canary.ui.sticker.StickerPackDetailActivity.start(
                                                context = context,
                                                stickerPackId = stickerPackId?.toString() ?: ""
                                            )
                                        } else {
                                            onImageClick(imageUrl)
                                        }
                                    },
                                    onLongClick = onLongClick
                                )
                        )
                    } else {
                        AsyncImage(
                            model = ImageUtils.createStickerImageRequest(
                                context = context,
                                url = imageUrl,
                                enableHardware = enableHardware
                            ),
                            contentDescription = when {
                                isPersonalExpression -> "个人收藏表情"
                                isStickerPack -> "表情包"
                                else -> "表情"
                            },
                            modifier = Modifier
                                .size(120.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .combinedClickable(
                                    onClick = {
                                        if (isPersonalExpression) {
                                            // 个人表情：打开图片预览
                                            onImageClick(imageUrl)
                                        } else if (isStickerPack) {
                                            // 表情包：跳转到表情包详情
                                            com.yhchat.canary.ui.sticker.StickerPackDetailActivity.start(
                                                context = context,
                                                stickerPackId = stickerPackId?.toString() ?: ""
                                            )
                                        } else {
                                            // 默认：图片预览
                                            onImageClick(imageUrl)
                                        }
                                    },
                                    onLongClick = onLongClick
                                ),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )
                    }
                } ?: run {
                    // 如果没有 imageUrl，尝试使用 stickerUrl 拼接完整URL
                    content.stickerUrl?.let { stickerUrl ->
                        val fullUrl = if (stickerUrl.startsWith("http")) {
                            stickerUrl
                        } else {
                            "https://chat-img.jwznb.com/$stickerUrl"
                        }

                        if (isTgsUrl(fullUrl)) {
                            TgsPreviewMessage(
                                url = fullUrl,
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .combinedClickable(
                                        onClick = {
                                            if (isPersonalExpression) {
                                                onImageClick(fullUrl)
                                            } else if (isStickerPack) {
                                                com.yhchat.canary.ui.sticker.StickerPackDetailActivity.start(
                                                    context = context,
                                                    stickerPackId = stickerPackId?.toString() ?: ""
                                                )
                                            } else {
                                                onImageClick(fullUrl)
                                            }
                                        },
                                        onLongClick = onLongClick
                                    )
                            )
                        } else {
                            AsyncImage(
                                model = ImageUtils.createStickerImageRequest(
                                    context = context,
                                    url = fullUrl,
                                    enableHardware = enableHardware
                                ),
                                contentDescription = "表情",
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .combinedClickable(
                                        onClick = {
                                            if (isPersonalExpression) {
                                                onImageClick(fullUrl)
                                            } else if (isStickerPack) {
                                                com.yhchat.canary.ui.sticker.StickerPackDetailActivity.start(
                                                    context = context,
                                                    stickerPackId = stickerPackId?.toString() ?: ""
                                                )
                                            } else {
                                                onImageClick(fullUrl)
                                            }
                                        },
                                        onLongClick = onLongClick
                                    ),
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit
                            )
                        }
                    }
                }
            }
            10 -> {
                // 视频消息 (contentType 10)
                content.videoUrl?.let { videoUrl ->
                    VideoMessageView(
                        videoUrl = videoUrl,
                        textColor = textColor,
                        modifier = Modifier.fillMaxWidth(),
                        onLongClick = onLongClick
                    )
                }
            }
            5 -> {
                // 表单消息 - 解析并展示表单内容
                FormMessageView(
                    content = content,
                    textColor = textColor,
                    isMyMessage = isMyMessage
                )
            }
            else -> {
                // 其他类型消息，显示文本内容
                content.text?.let { text ->
                    // 使用自定义SelectionContainer，在文本选择菜单中添加消息操作
                            MessageSelectionContainer(
                                onQuote = if (message.contentType in listOf(1, 3, 8)) {
                                    { onQuote(message.msgId, text) }
                                } else null,
                                onEdit = if (message.contentType in listOf(1, 3, 8) && isMyMessage) {
                                    { onEdit(message) }
                                } else null,
                                onDelete = when (conversationChatType) {
                                    2 -> if (message.msgDeleteTime == null) {{ onRecall(message.msgId) }} else null
                                    1, 3 -> if (isMyMessage && message.msgDeleteTime == null) {{ onRecall(message.msgId) }} else null
                                    else -> null
                                },
                                onPlusOne = { onPlusOne(message) },
                                onFavorite = { onFavorite(message) },
                                onExternalShare = { selectedText ->
                                    ExternalShareUtils.shareText(context, selectedText?.takeIf { it.isNotBlank() } ?: text, message.chatId ?: message.recvId)
                                }
                            ) {
                        val containsExpressionToken = text.contains("[.") && text.contains("]")
                        val containsLink = LinkDetector.containsLink(text)
                        if (showInlineExpressions && (containsExpressionToken || containsLink)) {
                            ExpressionText(
                                text = text,
                                style = MaterialTheme.typography.bodyMedium.copy(color = textColor),
                                linkColor = MaterialTheme.colorScheme.primary
                            )
                        } else if (containsLink) {
                            // 包含链接的文本
                            LinkText(
                                text = text,
                                style = MaterialTheme.typography.bodyMedium.copy(color = textColor),
                                linkColor = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            // 普通文本
                            Text(
                                text = text,
                                color = textColor,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }

        // 引用消息
        content.quoteMsgText?.let { quoteText: String ->
            val safeQuoteText = quoteText.ifBlank { "[引用消息]" }
            Spacer(modifier = Modifier.height(4.dp))
            YhSurface(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .combinedClickable(
                        onClick = {
                            // 点击引用消息，跳转到该消息
                            message.quoteMsgId?.let { quoteMsgId ->
                                if (quoteMsgId.isNotEmpty()) {
                                    onQuoteMessageClick(quoteMsgId)
                                }
                            }
                        },
                        onLongClick = {
                            // 长按引用消息，也跳转到该消息
                            message.quoteMsgId?.let { quoteMsgId ->
                                if (quoteMsgId.isNotEmpty()) {
                                    onQuoteMessageClick(quoteMsgId)
                                }
                            }
                        }
                    ),
                color = textColor.copy(alpha = 0.1f)
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 引用消息的媒体预览（图片或视频）
                    val hasQuoteMedia = content.quoteImageUrl != null || content.quoteVideoUrl != null

                    if (hasQuoteMedia) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(6.dp))
                        ) {
                            when {
                                // 引用的视频
                                content.quoteVideoUrl != null -> {
                                    // 视频缩略图（可以尝试显示第一帧或使用占位符）
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(textColor.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        // 视频图标
                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "引用视频",
                                            tint = textColor.copy(alpha = 0.7f),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    // 显示视频时长（如果有）
                                    content.quoteVideoTime?.let { duration ->
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(2.dp)
                                                .background(
                                                    Color.Black.copy(alpha = 0.6f),
                                                    RoundedCornerShape(2.dp)
                                                )
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = formatAudioDuration(duration),
                                                color = Color.White,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 9.sp
                                            )
                                        }
                                    }
                                }
                                // 引用的图片
                                content.quoteImageUrl != null -> {
                                    AsyncImage(
                                        model = ImageUtils.createImageRequest(
                                            context = androidx.compose.ui.platform.LocalContext.current,
                                            url = content.quoteImageUrl
                                        ),
                                        contentDescription = "引用图片",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clickable { onImageClick(content.quoteImageUrl) },
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    // 引用消息文本 - 支持文本选择
                    Column(
                        modifier = Modifier.widthIn(
                            max = if (hasQuoteMedia) 180.dp else 240.dp
                        )
                    ) {
                        Text(
                            text = safeQuoteText,
                            color = textColor.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "点击跳转",
                                color = textColor.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }

        // 按钮（用于表单消息等）
        content.buttons?.let { buttonsJson ->
            if (buttonsJson.isNotBlank() && buttonsJson != "null") {
                MessageButtons(
                    buttonsJson = buttonsJson,
                    message = message,
                    textColor = textColor,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * 文章消息视图
 */
@Composable
private fun PostMessageView(
    postId: String?,
    postTitle: String?,
    postContent: String?,
    postContentType: String?,
    textColor: Color,
    isMyMessage: Boolean
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    if (postId.isNullOrEmpty()) {
        Text(
            text = "文章消息",
            color = textColor,
            style = MaterialTheme.typography.bodyMedium
        )
        return
    }

    // 文章卡片
    YhSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                // 点击跳转到文章详情
                val intent = Intent(context, PostDetailActivity::class.java).apply {
                    putExtra("post_id", postId.toIntOrNull() ?: 0)
                    putExtra("post_title", postTitle ?: "文章详情")
                }
                context.startActivity(intent)
            }
            .border(
                width = 1.dp,
                color = if (isMyMessage) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f)
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                },
                shape = RoundedCornerShape(8.dp)
            ),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // 文章图标和标题 - 支持文本选择
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "📄",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = postTitle ?: "文章",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            // 文章内容预览 - 支持文本选择
            if (!postContent.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))

                when (postContentType) {
                    "2" -> {
                        // Markdown内容预览
                        Text(
                            text = postContent.take(100) + if (postContent.length > 100) "..." else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor.copy(alpha = 0.8f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    else -> {
                        // 普通文本内容预览
                        Text(
                            text = postContent.take(100) + if (postContent.length > 100) "..." else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor.copy(alpha = 0.8f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 查看详情提示
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "点击查看文章详情 →",
                style = MaterialTheme.typography.labelSmall,
                color = if (isMyMessage) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.primary
                },
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * 表单消息视图
 * 解析并展示表单消息内容
 */
@Composable
private fun FormMessageView(
    content: MessageContent,
    textColor: Color,
    isMyMessage: Boolean,
    modifier: Modifier = Modifier
) {
    // 解析表单数据
    val formItems = remember(content.form) {
        parseFormData(content.form)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 显示文本内容（如果有）
        content.text?.let { text ->
            if (text.isNotBlank()) {
                Text(
                    text = text,
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (formItems.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    YhHorizontalDivider(
                        color = textColor.copy(alpha = 0.2f),
                        thickness = 0.5.dp
                    )
                }
            }
        }

        // 渲染表单项
        if (formItems.isNotEmpty()) {
            formItems.forEach { item ->
                FormItemView(
                    item = item,
                    textColor = textColor,
                    isMyMessage = isMyMessage
                )
            }
        }
    }
}

/**
 * 表单项数据类
 */
private data class FormItem(
    val id: String,
    val type: String,
    val label: String,
    val value: Any? = null,
    val selectIndex: Int? = null,
    val selectValue: String? = null
)

/**
 * 解析表单JSON数据
 */
private fun parseFormData(formJson: String?): List<FormItem> {
    if (formJson.isNullOrBlank()) return emptyList()

    return try {
        val jsonObject = JSONObject(formJson)
        val items = mutableListOf<FormItem>()

        jsonObject.keys().forEach { key ->
            val itemObj = jsonObject.getJSONObject(key)
            items.add(
                FormItem(
                    id = itemObj.optString("id", key),
                    type = itemObj.optString("type", ""),
                    label = itemObj.optString("label", ""),
                    value = when {
                        itemObj.has("value") -> {
                            when {
                                itemObj.get("value") is Boolean -> itemObj.getBoolean("value")
                                else -> itemObj.optString("value", "")
                            }
                        }
                        else -> null
                    },
                    selectIndex = if (itemObj.has("selectIndex")) itemObj.optInt("selectIndex") else null,
                    selectValue = if (itemObj.has("selectValue") && !itemObj.isNull("selectValue")) {
                        itemObj.optString("selectValue")
                    } else {
                        null
                    }
                )
            )
        }
        items
    } catch (e: Exception) {
        android.util.Log.e("FormMessageView", "Failed to parse form data", e)
        emptyList()
    }
}

/**
 * 单个表单项视图
 */
@Composable
private fun FormItemView(
    item: FormItem,
    textColor: Color,
    isMyMessage: Boolean
) {
    val secondaryTextColor = textColor.copy(alpha = 0.7f)
    val accentColor = if (isMyMessage) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.primary
    }

    when (item.type.lowercase()) {
        "radio" -> {
            // 单选框
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = accentColor
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = secondaryTextColor
                    )
                    Text(
                        text = item.selectValue ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        "input" -> {
            // 单行输入框
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = secondaryTextColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                YhSurface(
                    modifier = Modifier.fillMaxWidth(),
                    color = textColor.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = item.value?.toString() ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        "switch" -> {
            // 开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor
                )
                YhSurface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (item.value == true) accentColor else textColor.copy(alpha = 0.3f)
                ) {
                    Text(
                        text = if (item.value == true) "开" else "关",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (item.value == true) {
                            if (isMyMessage) MaterialTheme.colorScheme.primary else Color.White
                        } else textColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        "textarea" -> {
            // 多行文本框
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = secondaryTextColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                YhSurface(
                    modifier = Modifier.fillMaxWidth(),
                    color = textColor.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = item.value?.toString() ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        "select" -> {
            // 选择器
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = accentColor
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = secondaryTextColor
                    )
                    Text(
                        text = item.selectValue ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        "checkbox" -> {
            // 复选框
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (item.value == true) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp)
                        .background(
                            if (item.value == true) accentColor else Color.Transparent,
                            RoundedCornerShape(4.dp)
                        )
                        .padding(2.dp),
                    tint = if (item.value == true) {
                        if (isMyMessage) MaterialTheme.colorScheme.primary else Color.White
                    } else textColor.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor
                )
            }
        }

        else -> {
            // 未知类型，显示原始数据
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = secondaryTextColor
                )
                Text(
                    text = item.value?.toString() ?: item.selectValue ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor
                )
            }
        }
    }
}

/**
 * 提取A2UI JSON前后的文本内容
 */
private fun extractTextAroundA2UiJson(rawText: String): Pair<String, String> {
    if (rawText.isBlank()) return Pair("", "")
    
    var firstJsonStart = -1
    var lastJsonEnd = -1
    var depth = 0
    var inString = false
    var escaped = false
    
    rawText.forEachIndexed { index, current ->
        if (escaped) {
            escaped = false
            return@forEachIndexed
        }
        
        if (inString && current == '\\') {
            escaped = true
            return@forEachIndexed
        }
        
        if (current == '"') {
            inString = !inString
            return@forEachIndexed
        }
        
        if (!inString) {
            when (current) {
                '{' -> {
                    if (depth == 0 && firstJsonStart == -1) {
                        firstJsonStart = index
                    }
                    depth += 1
                }
                '}' -> {
                    depth -= 1
                    if (depth == 0 && firstJsonStart != -1) {
                        // 检查这是否是有效的A2UI JSON
                        val candidate = rawText.substring(firstJsonStart, index + 1)
                        val isA2UiJson = looksLikeA2UiJson(candidate)
                        
                        if (isA2UiJson) {
                            lastJsonEnd = index + 1
                        }
                    }
                }
            }
        }
    }
    
    return if (firstJsonStart != -1 && lastJsonEnd != -1) {
        val beforeText = rawText.substring(0, firstJsonStart).trim()
        val afterText = rawText.substring(lastJsonEnd).trim()
        Pair(beforeText.trim(), afterText.trim())
    } else {
        Pair(rawText.trim(), "")
    }
}

private fun extractA2UiJsonMessages(rawText: String): List<String> {
    if (rawText.isBlank()) return emptyList()
    val objects = mutableListOf<String>()
    var startIndex = -1
    var depth = 0
    var inString = false
    var escaped = false

    rawText.forEachIndexed { index, current ->
        if (startIndex == -1) {
            if (current == '{') {
                startIndex = index
                depth = 1
                inString = false
                escaped = false
            }
            return@forEachIndexed
        }

        if (escaped) {
            escaped = false
            return@forEachIndexed
        }

        if (inString && current == '\\') {
            escaped = true
            return@forEachIndexed
        }

        if (current == '"') {
            inString = !inString
            return@forEachIndexed
        }

        if (!inString) {
            when (current) {
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) {
                        val candidate = rawText.substring(startIndex, index + 1)
                        if (looksLikeA2UiJson(candidate)) {
                            objects += candidate
                        }
                        startIndex = -1
                    }
                }
            }
        }
    }

    return objects
}

private fun parseA2UiSpecsBySurface(rawText: String): List<A2UiSpec> {
    val messages = extractA2UiJsonMessages(rawText)
    if (messages.isEmpty()) return emptyList()

    val grouped = linkedMapOf<String, MutableList<String>>()
    val anonymous = mutableListOf<String>()
    messages.forEach { message ->
        val surfaceId = extractA2UiSurfaceId(message)
        if (surfaceId.isNullOrBlank()) {
            anonymous += message
        } else {
            grouped.getOrPut(surfaceId) { mutableListOf() } += message
        }
    }

    return buildList {
        grouped.values.forEach { surfaceMessages ->
            parseA2UiSpec(surfaceMessages.joinToString(separator = "\n"))?.let(::add)
        }
        if (anonymous.isNotEmpty()) {
            parseA2UiSpec(anonymous.joinToString(separator = "\n"))?.let(::add)
        }
    }
}

private fun extractA2UiSurfaceId(rawText: String): String? {
    val json = runCatching { JSONObject(rawText) }.getOrNull() ?: return null
    return json.optJSONObject("createSurface")?.optString("surfaceId")
        ?: json.optJSONObject("updateComponents")?.optString("surfaceId")
        ?: json.optJSONObject("updateDataModel")?.optString("surfaceId")
        ?: json.optJSONObject("deleteSurface")?.optString("surfaceId")
}

private fun resolveA2UiSurfaceId(messages: List<String>): String? {
    messages.forEach { raw ->
        val surfaceId = extractA2UiSurfaceId(raw)
        if (!surfaceId.isNullOrBlank()) {
            return surfaceId
        }
    }
    return null
}

private fun isTgsUrl(url: String): Boolean {
    val clean = url.substringBefore('?').substringBefore('#').lowercase()
    return clean.endsWith(".tgs")
}

@Composable
private fun TgsPreviewMessage(
    url: String,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lottieJsonState = produceState<String?>(initialValue = null, url) {
        value = loadTgsJson(context, url)
    }
    val lottieJson = lottieJsonState.value
    val composition by rememberLottieComposition(
        spec = LottieCompositionSpec.JsonString(lottieJson.orEmpty())
    )

    Box(
        modifier = modifier.background(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
            shape = RoundedCornerShape(8.dp)
        ),
        contentAlignment = Alignment.Center
    ) {
        when {
            lottieJson == null -> {
                YhCircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            }
            composition != null -> {
                LottieAnimation(
                    composition = composition,
                    iterations = LottieConstants.IterateForever,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                Text(
                    text = "TGS加载失败",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private suspend fun loadTgsJson(context: Context, url: String): String? {
    return withContext(Dispatchers.IO) {
        try {
            val connection = (URL(url).openConnection() as java.net.HttpURLConnection).apply {
                instanceFollowRedirects = true
                if (url.contains(".jwznb.com")) {
                    setRequestProperty("Referer", "https://myapp.jwznb.com")
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
                }
                connect()
            }
            connection.inputStream.use { input ->
                GZIPInputStream(input).bufferedReader(Charsets.UTF_8).use { reader ->
                    reader.readText()
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
