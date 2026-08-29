package com.yhchat.canary.ui.chat

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.MaterialTheme
import com.yhchat.canary.ui.adaptive.YhIcon as Icon
import com.yhchat.canary.ui.adaptive.YhText as Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yhchat.canary.BuildConfig
import com.yhchat.canary.data.api.ApiClient
import com.yhchat.canary.data.di.RepositoryFactory
import com.yhchat.canary.data.model.ChatMessage
import com.yhchat.canary.data.model.MsgForwardReceive
import com.yhchat.canary.ui.bot.BotDetailActivity
import com.yhchat.canary.ui.adaptive.YhAlertDialog
import com.yhchat.canary.ui.adaptive.YhCard
import com.yhchat.canary.ui.adaptive.YhCircularProgressIndicator
import com.yhchat.canary.ui.adaptive.YhFloatingActionButton
import com.yhchat.canary.ui.adaptive.YhPullToRefresh
import com.yhchat.canary.ui.adaptive.YhSurface
import com.yhchat.canary.ui.adaptive.YhTextButton
import com.yhchat.canary.ui.chat.ChatComponents.*
import com.yhchat.canary.ui.community.SendToChatBottomSheet
import com.yhchat.canary.ui.components.ChatInputBar
import com.yhchat.canary.ui.components.ImageViewer
import com.yhchat.canary.ui.components.MultiSelectBottomBar
import com.yhchat.canary.ui.components.VoiceMessageViewModel
import com.yhchat.canary.ui.components.rememberBooleanPreference
import com.yhchat.canary.ui.components.rememberIntPreference
import com.yhchat.canary.ui.live.LiveRoomLauncher
import com.yhchat.canary.ui.live.LiveRoomsBottomSheet
import com.yhchat.canary.ui.live.LiveRoomsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 聊天界面
 */
@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class
)
@Composable
fun ChatScreen(
    chatId: String,
    chatType: Int,
    chatName: String,
    userId: String,
    enableAnimations: Boolean = true,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = viewModel(),
    onAvatarClick: (String, String, Int, Int) -> Unit = { _, _, _, _ -> },  // 添加第4个参数：当前用户权限
    onImagePickerClick: () -> Unit = {},  // 图片选择器点击回调
    onCameraClick: () -> Unit = {},  // 相机点击回调
    onFilePickerClick: () -> Unit = {},  // 文件选择器点击回调
    onVideoPickerClick: () -> Unit = {},  // 视频选择器点击回调
    imageUriToSend: android.net.Uri? = null,  // 待发送的图片URI
    fileUriToSend: android.net.Uri? = null,  // 待发送的文件URI
    videoUriToSend: android.net.Uri? = null,  // 待发送的视频URI
    onImageSent: () -> Unit = {},  // 图片发送完成回调
    onFileSent: () -> Unit = {},  // 文件发送完成回调
    onVideoSent: () -> Unit = {},  // 视频发送完成回调
    // 搜索跳转参数
    searchTargetMsgId: String? = null,  // 搜索目标消息ID
    searchTargetMsgSeq: Long? = null,  // 搜索目标消息序列号
    isLargeScreen: Boolean = false,  // 大屏模式，跳过imePadding避免悬浮键盘跳动
    chatAvatarUrl: String? = null
) {
    val context = LocalContext.current
    val liveRoomsViewModel: LiveRoomsViewModel = hiltViewModel()

    val voiceMessageViewModel = remember(context) {
        VoiceMessageViewModel(
            apiService = ApiClient.apiService,
            tokenRepository = RepositoryFactory.getTokenRepository(context)
        )
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val liveRoomsState by liveRoomsViewModel.uiState.collectAsStateWithLifecycle()
    val messages = viewModel.messages
    val collapsedMessageIds = viewModel.collapsedMessageIds
    val reversedMessages by remember {
        derivedStateOf { messages.asReversed().toList() }
    }
    val messageItemKeys by remember {
        derivedStateOf { buildMessageItemKeys(reversedMessages) }
    }
    val messageListIdentity by remember {
        derivedStateOf { buildMessageListIdentity(messageItemKeys) }
    }
    
    // 获取当前用户的权限等级
    val currentUserPermission = if (chatType == 2) {
        // 群聊时，从groupMembers中获取当前用户权限
        viewModel.getCurrentUserPermission()
    } else {
        0
    }
    val chatIdLong = remember(chatId) {
        try {
            chatId.toLong()
        } catch (e: Exception) {
            0
        }
    }
    var inputText by rememberSaveable(chatId) { mutableStateOf("") }
    val defaultSendMessageTypePref by rememberIntPreference("chat_settings", "default_send_message_type", 1)
    val defaultSendMessageType = remember(defaultSendMessageTypePref) {
        if (defaultSendMessageTypePref == 1 || defaultSendMessageTypePref == 3 || defaultSendMessageTypePref == 8) {
            defaultSendMessageTypePref
        } else {
            1
        }
    }
    var selectedMessageType by rememberSaveable(chatId) { mutableStateOf(defaultSendMessageType) } // 1-文本, 3-Markdown, 8-HTML
    var selectedInstruction by remember { mutableStateOf<com.yhchat.canary.data.model.Instruction?>(null) } // 选中的指令
    val listState = rememberLazyListState()
    
    // @用户状态 (userId -> userName)
    var mentionedUsers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    
    // 图片预览状态
    var showImageViewer by rememberSaveable { mutableStateOf(false) }
    var currentImageUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var currentImageGallery by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var currentImageIndex by rememberSaveable { mutableStateOf(0) }
    val chatImageGallery by remember(messages) {
        derivedStateOf { buildChatImageGallery(messages) }
    }
    val liveEnabled = BuildConfig.WITH_LIVE && chatType == 2
    var showLiveRoomsSheet by rememberSaveable(chatId) { mutableStateOf(false) }
    
    // 滚动到底部按钮状态
    var showScrollToBottomButton by remember { mutableStateOf(false) }
    // 用户是否处于“粘底”状态（只由用户滚动行为改变，不受新消息插入瞬时布局影响）
    var shouldStickToBottom by remember { mutableStateOf(true) }
    // WS 新消息到来后的待执行自动滚动标记（等列表插入完成后再滚动）
    var pendingAutoScrollToBottom by remember { mutableStateOf(false) }
    // WS 新消息到来且用户不在底部时，保持当前可视锚点，避免列表自己跳动
    var pendingPreserveVisibleAnchor by remember { mutableStateOf<ListAnchorSnapshot?>(null) }
    val coroutineScope = rememberCoroutineScope()
    
    // 引用消息状态
    var quotedMessageId by remember { mutableStateOf<String?>(null) }
    var quotedMessageText by remember { mutableStateOf<String?>(null) }
    var quotedImageUrl by remember { mutableStateOf<String?>(null) }
    var quotedImageName by remember { mutableStateOf<String?>(null) }
    var quotedVideoUrl by remember { mutableStateOf<String?>(null) }
    var quotedVideoTime by remember { mutableStateOf<Long?>(null) }
    
    // 编辑消息状态
    var showEditDialog by remember { mutableStateOf(false) }
    var messageToEdit by remember { mutableStateOf<ChatMessage?>(null) }
    
    // 语音转文字状态
    var showSttDialog by remember { mutableStateOf(false) }
    var sttAudioUrl by remember { mutableStateOf<String?>(null) }
    var sttProgress by remember { mutableStateOf("") }
    var sttResult by remember { mutableStateOf<String?>(null) }
    
    // 注意：文件选择器已移至ChatActivity中处理，这里不再需要launcher
    var isSttProcessing by remember { mutableStateOf(false) }
    
    // 浮动TTS窗口状态
    var showFloatingTtsWindow by remember { mutableStateOf(false) }
    
    // 输入框焦点请求器
    val inputFocusRequester = remember { FocusRequester() }
    
    // 键盘显示状态
    var shouldShowKeyboard by remember { mutableStateOf(false) }
    
    // 机器人看板展开状态
    var showBotBoard by remember { mutableStateOf(false) }
    
    // 高亮消息ID（用于跳转到引用消息时高亮显示）
    var highlightedMessageId by remember { mutableStateOf<String?>(null) }
    
    // 多选模式状态
    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedMessageIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    var showForwardSheet by remember { mutableStateOf(false) }
    var forwardTargetMessage by remember { mutableStateOf<com.yhchat.canary.data.model.ChatMessage?>(null) }
    
    // 多选转发状态
    var showSendToChatBottomSheet by remember { mutableStateOf(false) }
    var messagesToForward by remember { mutableStateOf<List<com.yhchat.canary.data.model.ChatMessage>>(emptyList()) }
    
    // 生成图片预览状态
    var showImagePreviewDialog by remember { mutableStateOf(false) }
    var generatedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isGeneratingImage by remember { mutableStateOf(false) }
    var editingBotLlmParamsBotId by remember { mutableStateOf<String?>(null) }

    if (showForwardSheet) {
        val target = forwardTargetMessage
        if (target != null) {
            SendToChatBottomSheet(
                onDismiss = {
                    showForwardSheet = false
                    forwardTargetMessage = null
                },
                title = "转发至",
                sendButtonText = { count -> "发送($count)" },
                onSend = { selected ->
                    coroutineScope.launch {
                        val receive = selected.map { contact ->
                            MsgForwardReceive(chatId = contact.id, chatType = contact.type)
                        }
                        viewModel.forwardMessage(
                            msgId = target.msgId,
                            sourceChatType = chatType,
                            receive = receive
                        ).fold(
                            onSuccess = {
                                Toast.makeText(context, "转发成功", Toast.LENGTH_SHORT).show()
                            },
                            onFailure = { err ->
                                Toast.makeText(context, "转发失败: ${err.message}", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            )
        }
    }
    
    // 读取内容设置：是否允许发送空文本消息
    val isSendTextAllowEmptySetting = context.getSharedPreferences("send_text_settings", Context.MODE_PRIVATE).getBoolean("send_text_allow_empty", false)

    // 初始化聊天
    LaunchedEffect(chatId, chatType) {
        viewModel.initChat(chatId, chatType, userId)
    }

    // 读取布局设置：TTS按钮和TopAppBar显隐
    val showTtsButton by rememberBooleanPreference("layout_settings", "chat_show_tts_button", true)
    val showRefreshButton by rememberBooleanPreference("layout_settings", "chat_show_refresh_button", true)
    val hideTopAppBar by rememberBooleanPreference("layout_settings", "chat_hide_top_app_bar", false)
    val enableChatContextMenu by rememberBooleanPreference(
        "chat_settings",
        "enable_chat_context_menu",
        true
    )
    
    // 处理搜索跳转：当有搜索目标消息ID时，自动跳转到该消息
    LaunchedEffect(searchTargetMsgId) {
        if (!searchTargetMsgId.isNullOrEmpty()) {
            android.util.Log.d("ChatScreen", "🔍 搜索跳转：尝试定位到消息 $searchTargetMsgId (seq: $searchTargetMsgSeq)")
            // 延迟一下确保聊天已初始化
            kotlinx.coroutines.delay(500)
            viewModel.loadMessageByIdAndScroll(searchTargetMsgId)
        }
    }
    
    // 监听定向滚动/搜索跳转事件
    LaunchedEffect(uiState.scrollToMsgId, messages) {
        val targetId = uiState.scrollToMsgId
        if (!targetId.isNullOrEmpty() && messages.isNotEmpty()) {
            val targetIndex = reversedMessages.indexOfFirst { it.msgId == targetId }
            if (targetIndex != -1) {
                android.util.Log.d("ChatScreen", "🎯 找到跳转目标消息，索引: $targetIndex, msgId: $targetId")
                highlightedMessageId = targetId
                listState.animateScrollToItem(targetIndex)
                viewModel.clearScrollToMsgId()
                kotlinx.coroutines.delay(3000)
                highlightedMessageId = null
            }
        }
    }
    
    // 如果是机器人聊天，加载机器人信息和看板
    LaunchedEffect(chatId, chatType) {
        if (chatType == 3) {
            viewModel.loadBotInfo(chatId)
            viewModel.loadBotBoard(chatId, chatType)
        }
    }
    
    // 加载聊天背景
    LaunchedEffect(chatId) {
        viewModel.loadChatBackground(context, chatId)
    }

    LaunchedEffect(liveEnabled, chatId) {
        if (!liveEnabled) return@LaunchedEffect

        liveRoomsViewModel.refresh(groupId = chatId)
        while (true) {
            delay(15000)
            liveRoomsViewModel.refresh(groupId = chatId, silent = true)
        }
    }

    LaunchedEffect(liveEnabled) {
        if (!liveEnabled) return@LaunchedEffect

        liveRoomsViewModel.launchEvents.collectLatest { payload ->
            showLiveRoomsSheet = false
            val launched = LiveRoomLauncher.open(context, payload)
            if (!launched) {
                Toast.makeText(context, "live 页面未接入", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // 处理图片发送
    LaunchedEffect(imageUriToSend) {
        imageUriToSend?.let { uri ->
            android.util.Log.d("ChatScreen", "收到待发送的图片URI: $uri")
            viewModel.uploadAndSendImage(
                context = context,
                imageUri = uri,
                quoteMsgId = quotedMessageId,
                quoteMsgText = quotedMessageText,
                quoteImageUrl = quotedImageUrl,
                quoteImageName = quotedImageName,
                quoteVideoUrl = quotedVideoUrl,
                quoteVideoTime = quotedVideoTime
            )
            // 清除引用状态
            quotedMessageId = null
            quotedMessageText = null
            quotedImageUrl = null
            quotedImageName = null
            quotedVideoUrl = null
            quotedVideoTime = null
            // 通知已发送
            onImageSent()
        }
    }
    
    // 监听待发送的文件
    LaunchedEffect(fileUriToSend) {
        fileUriToSend?.let { uri ->
            android.util.Log.d("ChatScreen", "📁 收到待发送的文件URI: $uri")
            viewModel.uploadAndSendFile(
                context = context,
                fileUri = uri,
                quoteMsgId = quotedMessageId,
                quoteMsgText = quotedMessageText,
                quoteImageUrl = quotedImageUrl,
                quoteImageName = quotedImageName,
                quoteVideoUrl = quotedVideoUrl,
                quoteVideoTime = quotedVideoTime
            )
            // 清除引用状态
            quotedMessageId = null
            quotedMessageText = null
            quotedImageUrl = null
            quotedImageName = null
            quotedVideoUrl = null
            quotedVideoTime = null
            // 通知已发送
            onFileSent()
        }
    }
    
    // 监听待发送的视频
    LaunchedEffect(videoUriToSend) {
        videoUriToSend?.let { uri ->
            android.util.Log.d("ChatScreen", "📹 收到待发送的视频URI: $uri")
            viewModel.uploadAndSendVideo(
                context = context,
                videoUri = uri,
                quoteMsgId = quotedMessageId,
                quoteMsgText = quotedMessageText,
                quoteImageUrl = quotedImageUrl,
                quoteImageName = quotedImageName,
                quoteVideoUrl = quotedVideoUrl,
                quoteVideoTime = quotedVideoTime
            )
            // 清除引用状态
            quotedMessageId = null
            quotedMessageText = null
            quotedImageUrl = null
            quotedImageName = null
            quotedVideoUrl = null
            quotedVideoTime = null
            // 通知已发送
            onVideoSent()
        }
    }
    
    // 退出时保存读取位置和草稿
    DisposableEffect(Unit) {
        onDispose {
            viewModel.saveCurrentReadPosition()
            // 保存草稿
            if (inputText.isNotEmpty()) {
                viewModel.saveDraft(inputText)
            } else {
                viewModel.clearDraft()
            }
        }
    }
    
    // 进入时恢复草稿
    LaunchedEffect(chatId, chatType) {
        val draft = viewModel.getDraft(chatId, chatType)
        if (!draft.isNullOrEmpty()) {
            inputText = draft
        }
    }
    
    // 监听远程草稿更新（多端同步）
    val remoteDraftInput by viewModel.remoteDraftInput.collectAsStateWithLifecycle()
    var lastAppliedDraft by remember { mutableStateOf<String?>(null) }
    var lastInputTime by remember { mutableLongStateOf(0L) }
    
    LaunchedEffect(remoteDraftInput) {
        remoteDraftInput?.let { draft ->
            val currentTime = System.currentTimeMillis()
            
            // 防止重复应用相同的草稿
            // 如果用户最近500ms内有输入操作，则忽略远程草稿（避免覆盖用户正在输入的内容）
            if (draft != lastAppliedDraft && 
                draft != inputText && 
                (currentTime - lastInputTime > 100)) {
                
                android.util.Log.d("ChatScreen", "🔄 应用远程草稿同步: '${draft.take(50)}${if (draft.length > 50) "..." else ""}'")
                inputText = draft
                lastAppliedDraft = draft
                
                // 延迟300ms后清除远程草稿状态（防抖）
                delay(300)
                viewModel.clearRemoteDraftInput()
            } else {
                android.util.Log.d("ChatScreen", "⏭️ 跳过远程草稿（用户正在输入或内容相同）")
                viewModel.clearRemoteDraftInput()
            }
        }
    }
    
    // 跟踪用户输入时间（用于判断是否应该应用远程草稿）
    LaunchedEffect(inputText) {
        lastInputTime = System.currentTimeMillis()
    }
    
    // 与“回到最新消息”按钮保持一致：index=0 且 offset<=100 视为在底部
    fun isAtBottom(): Boolean {
        return listState.firstVisibleItemIndex == 0 &&
                listState.firstVisibleItemScrollOffset <= 100
    }

    // 监听滚动状态：更新“粘底”状态，并决定是否显示“回到最新消息”按钮
    LaunchedEffect(
        listState.firstVisibleItemIndex,
        listState.firstVisibleItemScrollOffset,
        listState.isScrollInProgress
    ) {
        val atBottomNow = isAtBottom()

        // 只有用户在滚动，或已经回到底部时，才更新粘底状态；
        // 避免新消息插入导致的瞬时偏移把“粘底”误判为 false
        if (listState.isScrollInProgress || atBottomNow) {
            shouldStickToBottom = atBottomNow
        }

        showScrollToBottomButton = !atBottomNow
    }
    
    // WebSocket 新消息处理：仅在底部时自动滚动
    LaunchedEffect(uiState.newMessageReceived) {
        if (uiState.newMessageReceived) {
            // 记录是否需要在“消息真正插入后”执行自动滚动/保持当前可视位置
            if (shouldStickToBottom) {
                pendingAutoScrollToBottom = true
                pendingPreserveVisibleAnchor = null
            } else {
                pendingAutoScrollToBottom = false
                val anchorIndex = listState.firstVisibleItemIndex
                val anchorKey = messageItemKeys.getOrNull(anchorIndex)
                pendingPreserveVisibleAnchor = anchorKey?.let {
                    ListAnchorSnapshot(
                        messageKey = it,
                        scrollOffset = listState.firstVisibleItemScrollOffset,
                        listIdentity = messageListIdentity
                    )
                }
            }
            
            // 重置新消息标记
            viewModel.resetNewMessageFlag()
        }
    }

    // 消息数量变化后再执行自动滚动，避免时序问题导致“未滚到最新”
    LaunchedEffect(messageListIdentity, pendingAutoScrollToBottom, pendingPreserveVisibleAnchor) {
        if (pendingAutoScrollToBottom) {
            listState.scrollToItem(0)
            pendingAutoScrollToBottom = false
            return@LaunchedEffect
        }

        val anchorSnapshot = pendingPreserveVisibleAnchor
        if (anchorSnapshot != null && messageListIdentity != anchorSnapshot.listIdentity) {
            val restoredIndex = messageItemKeys.indexOf(anchorSnapshot.messageKey)
            if (restoredIndex >= 0) {
                listState.scrollToItem(
                    index = restoredIndex,
                    scrollOffset = anchorSnapshot.scrollOffset
                )
            }
            pendingPreserveVisibleAnchor = null
        }
    }

    // 处理系统返回键/手势返回
    BackHandler (enabled = isMultiSelectMode){
        if (isMultiSelectMode) {
            isMultiSelectMode = false
            selectedMessageIds = emptySet()
        } else {
            onBackClick()
        }
    }

    // 下拉刷新状态（刷新最新消息）
    // 应用聊天背景
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // 背景图片
        if (uiState.chatBackgroundUrl != null) {
            coil.compose.AsyncImage(
                model = uiState.chatBackgroundUrl,
                contentDescription = "聊天背景",
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                alpha = 0.3f  // 半透明效果
            )
        }
    
    YhSurface(
            modifier = Modifier.fillMaxSize(),
            color = if (uiState.chatBackgroundUrl != null) {
                Color.Transparent
            } else {
                MaterialTheme.colorScheme.background
            }
    ) {
        Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (!isLargeScreen) Modifier.imePadding() else Modifier)  // 大屏悬浮键盘不推动界面
        ) {
        // 顶部应用栏（受布局设置控制）
        if (!hideTopAppBar) {
            ChatTopAppBar(
                chatId = chatId,
                chatType = chatType,
                chatName = chatName,
                avatarUrl = chatAvatarUrl,
                uiState = uiState,
                showTtsButton = showTtsButton,
                showRefreshButton = showRefreshButton,
                showLiveButton = liveEnabled && liveRoomsState.rooms.isNotEmpty(),
                onBackClick = onBackClick,
                onRefreshClick = { viewModel.refreshLatestMessages() },
                onTtsClick = { showFloatingTtsWindow = true },
                onLiveClick = {
                    showLiveRoomsSheet = true
                    liveRoomsViewModel.refresh(groupId = chatId)
                },
                modifier = Modifier.zIndex(3f)
            )
        }
        
        com.yhchat.canary.ui.chat.ChatComponents.MiniAudioPlayerBar(
            modifier = Modifier.zIndex(2.5f)
        )
        
        
        // 单个机器人看板区域
        SingleBotBoardSection(
            chatId = chatId,
            chatType = chatType,
            uiState = uiState,
            onOpenBotLlmParams = { botId -> editingBotLlmParamsBotId = botId },
            modifier = Modifier.zIndex(2f),
            onImageClick = { url ->
                currentImageUrl = url
                currentImageGallery = listOf(url)
                currentImageIndex = 0
                showImageViewer = true
            }
        )
        
        // 群聊中的机器人看板列表
        GroupBotBoardsSection(
            chatType = chatType,
            groupBots = uiState.groupBots,
            groupBotBoards = uiState.groupBotBoards,
            botLlmRefParams = uiState.botLlmRefParams.associate { it.botId to it.botNickname },
            botLlmParamValues = uiState.botLlmParamValues,
            onOpenBotLlmParams = { botId -> editingBotLlmParamsBotId = botId },
            modifier = Modifier.zIndex(2f),
            onImageClick = { url ->
                currentImageUrl = url
                currentImageGallery = listOf(url)
                currentImageIndex = 0
                showImageViewer = true
            }
        )
        
        // 错误信息
        uiState.error?.let { error ->
            YhCard(
                modifier = Modifier
                    .zIndex(2f)
                    .fillMaxWidth()
                    .padding(8.dp),
                containerColor = MaterialTheme.colorScheme.errorContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    YhTextButton(
                        onClick = { viewModel.clearError() }
                    ) {
                        Text("关闭")
                    }
                }
            }
        }

        // 消息列表（占据中间可用空间）
        YhPullToRefresh(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refreshLatestMessages() },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
        ) {
            if (uiState.isLoading && messages.isEmpty()) {
                // 初始加载状态
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    YhCircularProgressIndicator()
                }
            } else {
                val groupOwnerId = uiState.groupInfo?.ownerId
                val groupAdminIds = uiState.groupInfo?.adminIds ?: emptyList()
                val messageListModifier = Modifier.fillMaxSize()

                LazyColumn(
                    state = listState,
                    modifier = messageListModifier,
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    reverseLayout = true // 最新消息在底部
                ) {
                    items(
                        count = reversedMessages.size,
                        key = { index -> messageItemKeys[index] },
                        contentType = { index ->
                            reversedMessages[index].contentType
                        }
                    ) { index ->
                        val message = reversedMessages[index]
                        // 获取发送者的权限等级（仅群聊）
                        val memberPermission = uiState.groupMembers[message.sender.chatId]?.permissionLevel
                        val isMyMessage = if (userId.isNotBlank() && message.sender.chatId.isNotBlank()) {
                            message.sender.chatId == userId
                        } else {
                            viewModel.isMyMessage(message)
                        }
                        val isStreaming = viewModel.isMessageStreaming(message.msgId)
                        val itemModifier = Modifier.fillMaxWidth()
                        
                        AnimatedMessageItem(
                            message = message,
                            isMyMessage = isMyMessage,
                            conversationChatType = chatType,
                            modifier = itemModifier,
                            enableAnimations = enableAnimations,
                            isHighlighted = message.msgId == highlightedMessageId,
                            isMultiSelectMode = isMultiSelectMode,
                            isSelected = selectedMessageIds.contains(message.msgId),
                            isStreaming = isStreaming,
                            enableContextMenu = enableChatContextMenu,
                            onSelectionToggle = { msgId ->
                                selectedMessageIds = if (selectedMessageIds.contains(msgId)) {
                                    selectedMessageIds - msgId
                                } else {
                                    selectedMessageIds + msgId
                                }
                            },
                            onMultiSelect = {
                                isMultiSelectMode = true
                                selectedMessageIds = setOf(message.msgId)
                            },
                            onForward = { msg ->
                                forwardTargetMessage = msg
                                showForwardSheet = true
                            },
                            onImageClick = { imageUrl ->
                                val gallery = if (chatImageGallery.isNotEmpty() && chatImageGallery.contains(imageUrl)) {
                                    chatImageGallery
                                } else {
                                    listOf(imageUrl)
                                }
                                currentImageUrl = imageUrl
                                currentImageGallery = gallery
                                currentImageIndex = gallery.indexOf(imageUrl).takeIf { it >= 0 } ?: 0
                                showImageViewer = true
                            },
                            onAvatarClick = { senderId, name, senderChatType ->
                                // 处理头像点击事件
                                if (senderChatType == 3) { // 机器人
                                    val intent = Intent(context, BotDetailActivity::class.java).apply {
                                        putExtra(BotDetailActivity.EXTRA_BOT_ID, senderId)
                                        putExtra(BotDetailActivity.EXTRA_BOT_NAME, name)
                                    }
                                    context.startActivity(intent)
                                } else if (senderChatType == 1) {
                                    com.yhchat.canary.ui.user.UserDetailActivity.start(
                                        context = context,
                                        userId = senderId,
                                        userName = name,
                                        groupId = if (chatType == 2) chatId else null
                                    )
                                } else {
                                    onAvatarClick(senderId, name, senderChatType, currentUserPermission)
                                }
                            },
                            onAvatarLongClick = { userId, userName ->
                                // 长按头像@用户
                                android.util.Log.d("ChatScreen", "长按头像@用户: userId=$userId, userName=$userName")
                                
                                // 添加@用户到映射表
                                mentionedUsers = mentionedUsers + (userId to userName)
                                
                                // 在输入框添加@用户名
                                val mentionText = "@$userName "
                                inputText = if (inputText.isEmpty()) {
                                    mentionText
                                } else {
                                    "$inputText$mentionText"
                                }
                                
                                // 自动聚焦输入框并显示键盘
                                coroutineScope.launch {
                                    inputFocusRequester.requestFocus()
                                    shouldShowKeyboard = true
                                    delay(100)
                                    shouldShowKeyboard = false
                                }
                            },
                            onAddExpression = viewModel::addExpressionToFavorites,
                            onQuote = { msgId, msgText ->
                                // 设置引用消息，格式：发送者名称 : 内容
                                val senderName = message.sender.name
                                val content = message.content.text ?: ""
                                val quotedText = "$senderName：$content"
                                quotedMessageId = msgId
                                quotedMessageText = quotedText
                                
                                // 提取引用消息的图片信息（如果有）
                                when (message.contentType) {
                                    2 -> {
                                        // 图片消息
                                        val imageUrl = message.content.imageUrl?.takeIf { it.isNotBlank() }
                                        quotedImageUrl = imageUrl
                                        quotedImageName = imageUrl
                                            ?.substringAfterLast("/")
                                            ?.substringBefore("?")
                                            ?.takeIf { it.isNotBlank() }
                                        android.util.Log.d("ChatScreen", "📷 引用图片消息: url=$quotedImageUrl")
                                    }
                                    10 -> {
                                        // 视频消息
                                        quotedVideoUrl = message.content.videoUrl
                                        quotedVideoTime = message.content.audioTime // 视频时长字段
                                        android.util.Log.d("ChatScreen", "🎬 引用视频消息: url=$quotedVideoUrl, time=$quotedVideoTime")
                                    }
                                    else -> {
                                        // 清空媒体引用
                                        quotedImageUrl = null
                                        quotedImageName = null
                                        quotedVideoUrl = null
                                        quotedVideoTime = null
                                    }
                                }
                                
                                // 自动聚焦输入框并显示键盘
                                coroutineScope.launch {
                                    inputFocusRequester.requestFocus()
                                    // 触发键盘显示
                                    shouldShowKeyboard = true
                                    // 延迟重置状态，避免重复触发
                                    delay(100)
                                    shouldShowKeyboard = false
                                }
                            },
                            onRecall = { msgId ->
                                // 撤回消息 - 执行撤回，动画自动触发
                                android.util.Log.d("ChatScreen", "🗑️ 用户点击撤回: msgId=$msgId")
                                viewModel.recallMessage(msgId)
                                // 动画会在消息状态更新时自动触发
                            },
                            onEdit = { message ->
                                // 编辑消息
                                messageToEdit = message
                                showEditDialog = true
                            },
                            onBlockUser = { userId, userName, avatarUrl ->
                                // 屏蔽用户
                                viewModel.blockUser(userId, userName, avatarUrl)
                                Toast.makeText(context, "已屏蔽用户：$userName", Toast.LENGTH_SHORT).show()
                            },
                            onSpeechToText = { audioUrl ->
                                // 语音转文字
                                sttAudioUrl = audioUrl
                                sttResult = null
                                sttProgress = "准备中..."
                                showSttDialog = true
                                isSttProcessing = true
                                coroutineScope.launch {
                                    com.yhchat.canary.utils.STTUtils.speechToText(
                                        context = context,
                                        audioUrl = audioUrl,
                                        onProgress = { progress -> sttProgress = progress }
                                    ).fold(
                                        onSuccess = { text ->
                                            sttResult = text
                                            sttProgress = "识别完成"
                                            isSttProcessing = false
                                        },
                                        onFailure = { error ->
                                            sttResult = null
                                            sttProgress = "识别失败: ${error.message}"
                                            isSttProcessing = false
                                        }
                                    )
                                }
                            },
                            onPlusOne = { msg ->
                                // +1 发送同样消息
                                android.util.Log.d("ChatScreen", "🔄 +1消息: contentType=${msg.contentType}, msgId=${msg.msgId}")
                                
                                when (msg.contentType) {
                                    1, 3, 8 -> {
                                        // 文本/Markdown/HTML消息
                                        msg.content.text?.let { text ->
                                            viewModel.sendMessage(
                                                text = text,
                                                contentType = msg.contentType,
                                                isSendTextAllowEmptySetting = isSendTextAllowEmptySetting, // +1有必要允许发送空文本消息吗，嗯也许用来+1发送别人发的引用消息吧。嗯似乎引用消息的+1还没做好
                                            )
                                            Toast.makeText(context, "+1 文本消息已发送", Toast.LENGTH_SHORT).show()
                                        } ?: Toast.makeText(context, "消息内容为空", Toast.LENGTH_SHORT).show()
                                    }
                                    2 -> {
                                        // 图片消息 - 使用imageUrl发送
                                        msg.content.imageUrl?.let { imageUrl ->
                                            val width = msg.content.width?.toInt() ?: 0
                                            val height = msg.content.height?.toInt() ?: 0
                                            val fileSize = msg.content.fileSize ?: 0L
                                            
                                            android.util.Log.d("ChatScreen", "📷 +1图片: url=$imageUrl, size=${width}x${height}")
                                            viewModel.sendImageByUrl(imageUrl, width, height, fileSize)
                                            Toast.makeText(context, "+1 图片消息已发送", Toast.LENGTH_SHORT).show()
                                        } ?: Toast.makeText(context, "图片链接为空", Toast.LENGTH_SHORT).show()
                                    }
                                    4 -> {
                                        // 文件消息
                                        val fileName = msg.content.fileName
                                        val fileUrl = msg.content.fileUrl
                                        val fileSize = msg.content.fileSize
                                        
                                        if (!fileName.isNullOrEmpty() && !fileUrl.isNullOrEmpty()) {
                                            android.util.Log.d("ChatScreen", "📁 +1文件: name=$fileName, url=$fileUrl")
                                            viewModel.sendFileByUrl(fileName, fileUrl, fileSize ?: 0L)
                                            Toast.makeText(context, "+1 文件消息已发送", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "文件信息不完整", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    6 -> {
                                        // 文章消息
                                        val postId = msg.content.postId
                                        val postTitle = msg.content.postTitle
                                        val postContent = msg.content.postContent
                                        val postContentType = msg.content.postContentType
                                        
                                        if (!postId.isNullOrEmpty()) {
                                            android.util.Log.d("ChatScreen", "📄 +1文章: id=$postId, title=$postTitle")
                                            viewModel.sendPostMessage(
                                                postId = postId,
                                                postTitle = postTitle ?: "",
                                                postContent = postContent ?: "",
                                                postType = postContentType ?: "1"
                                            )
                                            Toast.makeText(context, "+1 文章消息已发送", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "文章ID为空", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    7 -> {
                                        // 表情消息（个人表情或表情包贴纸）
                                        val expressionId = msg.content.expressionId
                                        val stickerPackId = msg.content.stickerPackId
                                        val stickerItemId = msg.content.stickerItemId
                                        val imageUrl = msg.content.imageUrl
                                        
                                        when {
                                            !expressionId.isNullOrEmpty() && expressionId != "0" -> {
                                                // 个人收藏表情
                                                android.util.Log.d("ChatScreen", "😀 +1个人表情: id=$expressionId")
                                                if (!imageUrl.isNullOrEmpty()) {
                                                    val expression = com.yhchat.canary.data.model.Expression(
                                                        id = expressionId.toLongOrNull() ?: 0L,
                                                        url = imageUrl,
                                                        urlOriginal = imageUrl,
                                                        createTime = System.currentTimeMillis(),
                                                        createBy = userId
                                                    )
                                                    viewModel.sendExpressionMessage(expression)
                                                    Toast.makeText(context, "+1 表情已发送", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "表情链接为空", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                            stickerPackId != null && stickerPackId != 0L && stickerItemId != null && stickerItemId != 0L -> {
                                                // 表情包贴纸
                                                android.util.Log.d("ChatScreen", "🎭 +1表情包: packId=$stickerPackId, itemId=$stickerItemId")
                                                if (!imageUrl.isNullOrEmpty()) {
                                                    val stickerUrl = msg.content.stickerUrl ?: imageUrl
                                                    val stickerItem = com.yhchat.canary.data.model.StickerItem(
                                                        id = stickerItemId,
                                                        name = "表情贴纸",
                                                        url = stickerUrl,
                                                        stickerPackId = stickerPackId,
                                                        createBy = userId,
                                                        createTime = System.currentTimeMillis()
                                                    )
                                                    viewModel.sendStickerMessage(stickerItem)
                                                    Toast.makeText(context, "+1 表情包已发送", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "表情包链接为空", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                            else -> {
                                                Toast.makeText(context, "表情信息不完整", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                    10 -> {
                                        // 视频消息
                                        msg.content.videoUrl?.let { videoUrl ->
                                            android.util.Log.d("ChatScreen", "🎬 +1视频: url=$videoUrl")
                                            viewModel.sendVideoByUrl(videoUrl)
                                            Toast.makeText(context, "+1 视频消息已发送", Toast.LENGTH_SHORT).show()
                                        } ?: Toast.makeText(context, "视频链接为空", Toast.LENGTH_SHORT).show()
                                    }
                                    11 -> {
                                        // 语音消息
                                        val audioUrl = msg.content.audioUrl
                                        val audioTime = msg.content.audioTime
                                        
                                        if (!audioUrl.isNullOrEmpty() && audioTime != null) {
                                            android.util.Log.d("ChatScreen", "🎤 +1语音: url=$audioUrl, duration=${audioTime}s")
                                            viewModel.sendAudioByUrl(audioUrl, audioTime)
                                            Toast.makeText(context, "+1 语音消息已发送", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "语音信息不完整", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    else -> {
                                        android.util.Log.w("ChatScreen", "⚠️ 不支持+1此类型消息: contentType=${msg.contentType}")
                                        Toast.makeText(context, "暂不支持+1此类型消息", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onQuoteMessageClick = { quoteMsgId ->
                                val existing = messages.any { it.msgId == quoteMsgId }
                                if (!existing) {
                                    Toast.makeText(context, "正在查找引用消息...", Toast.LENGTH_SHORT).show()
                                }
                                viewModel.loadMessageByIdAndScroll(quoteMsgId)
                            },
                            memberPermission = memberPermission,
                            groupOwnerId = groupOwnerId,
                            groupAdminIds = groupAdminIds,
                            isCollapsed = collapsedMessageIds.contains(message.msgId),
                            onToggleCollapse = { viewModel.toggleMessageCollapse(message.msgId) }
                        )
                    }

                    // 加载更多指示器
                    if (messages.isNotEmpty()) {
                        item {
                            LaunchedEffect(Unit) {
                                viewModel.loadMoreMessages()
                            }

                            if (uiState.isLoading) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    YhCircularProgressIndicator(
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 空状态
                    if (messages.isEmpty() && !uiState.isLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "暂无消息\n开始对话吧",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            // "回到最新消息"浮动按钮
            androidx.compose.animation.AnimatedVisibility(
                visible = showScrollToBottomButton,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .fillMaxSize()
                    .wrapContentSize(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                YhFloatingActionButton(
                    onClick = {
                        coroutineScope.launch {
                            shouldStickToBottom = true
                            pendingAutoScrollToBottom = false
                            // 滚动到最新消息（索引0，因为是 reverseLayout）
                            listState.scrollToItem(0)
                        }
                    },
                    modifier = Modifier.size(48.dp),
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "回到最新消息"
                    )
                }
            }
        }

        // 菜单按钮栏（仅群聊显示，且设置允许）
        val showMenuButtons by rememberBooleanPreference("chat_settings", "show_menu_buttons", true)
        if (chatType == 2 && uiState.menuButtons.isNotEmpty() && showMenuButtons) {
            com.yhchat.canary.ui.components.MenuButtonBar(
                menuButtons = uiState.menuButtons,
                onButtonClick = { button ->
                    val buttonValue = button.content
                    
                    // 检查按钮值是否是可处理的链接
                    when {
                        com.yhchat.canary.utils.UnifiedLinkHandler.isHandleableLink(buttonValue) -> {
                            // 使用 UnifiedLinkHandler 处理 yunhu://, yhfx 分享链接, yhchat.com 文章链接
                            com.yhchat.canary.utils.UnifiedLinkHandler.handleLink(context, buttonValue)
                        }
                        (buttonValue as String).startsWith("http://") || (buttonValue as String).startsWith("https://") -> {
                            // 其他 HTTP/HTTPS 链接，使用系统浏览器打开
                            try {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(buttonValue))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "无法打开链接", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                        else -> {
                            // 不是链接，发送按钮请求
                            viewModel.clickMenuButton(button)
                        }
                    }
                }
            )
        }

        // 底部栏：多选模式 vs 输入模式
        if (isMultiSelectMode) {
            // 多选模式底部操作栏（带上浮动画）
            AnimatedVisibility(
                visible = true,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(300)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(200)
                ) + fadeOut(animationSpec = tween(200))
            ) {
                MultiSelectBottomBar(
                    selectedCount = selectedMessageIds.size,
                    onForward = {
                        // 批量转发
                        if (selectedMessageIds.isNotEmpty()) {
                            val selectedMessages = messages.filter { selectedMessageIds.contains(it.msgId) }
                            showSendToChatBottomSheet = true
                            messagesToForward = selectedMessages
                        }
                    },
                    onGenerateImage = {
                        // 生成图片
                        if (selectedMessageIds.isNotEmpty()) {
                            isGeneratingImage = true
                            coroutineScope.launch {
                                try {
                                    val selectedMessages = messages
                                        .filter { selectedMessageIds.contains(it.msgId) }
                                        .sortedBy { it.sendTime }
                                    val bitmap = generateMessagesImage(
                                        context = context,
                                        messages = selectedMessages,
                                        chatName = chatName
                                    )
                                    generatedImageBitmap = bitmap
                                    showImagePreviewDialog = true
                                } catch (e: Exception) {
                                    android.util.Log.e("ChatScreen", "生成图片失败", e)
                                    Toast.makeText(context, "生成图片失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isGeneratingImage = false
                                }
                            }
                        }
                    },
                    onRecall = {
                        // 批量撤回
                        if (selectedMessageIds.isNotEmpty()) {
                            viewModel.recallMessagesBatch(selectedMessageIds.toList())
                            Toast.makeText(context, "正在撤回 ${selectedMessageIds.size} 条消息...", Toast.LENGTH_SHORT).show()
                            isMultiSelectMode = false
                            selectedMessageIds = emptySet()
                        }
                    },
                    onClose = {
                        isMultiSelectMode = false
                        selectedMessageIds = emptySet()
                    },
                    modifier = Modifier.navigationBarsPadding()
                )
            }
        } else {
            // 上传附件时的进度条
            if (uiState.uploadProgress != null) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = true,
                    enter = androidx.compose.animation.expandVertically(),
                    exit = androidx.compose.animation.shrinkVertically()
                ) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(androidx.compose.material3.MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.foundation.layout.Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.Text(
                                    text = "正在上传附件...",
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
                                androidx.compose.material3.Text(
                                    text = "${(uiState.uploadProgress!! * 100).toInt()}%",
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.primary
                                )
                            }
                            androidx.compose.material3.IconButton(
                                onClick = { viewModel.cancelUpload() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "取消上传",
                                    modifier = Modifier.size(16.dp),
                                    tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { uiState.uploadProgress ?: 0f },
                            modifier = Modifier.fillMaxWidth().height(2.dp),
                            color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            trackColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
                        )
                    }
                }
            }

        // 底部输入栏
            ChatInputBar(
                text = inputText,
                onTextChange = { inputText = it },
                onSendMessage = {
                    if (inputText.isNotEmpty()||isSendTextAllowEmptySetting) {
                        val messageText = inputText
                        
                        // 解析@用户，提取用户ID列表
                        val mentionedIdsList = mutableListOf<String>()
                        val mentionRegex = Regex("@([^\\s@]+)")
                        mentionRegex.findAll(messageText).forEach { matchResult ->
                            val userName = matchResult.groupValues[1]
                            // 从 mentionedUsers 映射中找到对应的 userId
                            mentionedUsers.entries.find { it.value == userName }?.let { entry ->
                                mentionedIdsList.add(entry.key)
                            }
                        }
                        
                        if (selectedInstruction != null) {
                            android.util.Log.d("ChatScreen", "📤 发送指令消息: /${selectedInstruction?.name}, commandId=${selectedInstruction?.id}, text=$messageText")
                        } else {
                            android.util.Log.d("ChatScreen", "📤 发送普通消息: $messageText" + 
                                if (mentionedIdsList.isNotEmpty()) ", @${mentionedIdsList.size}人: $mentionedIdsList" else ""
                            )
                        }
                        
                        // 根据选择的消息类型发送消息，带上引用信息、指令ID和@用户列表
                        viewModel.sendMessage(
                            text = messageText,
                            contentType = selectedMessageType,
                            quoteMsgId = quotedMessageId,
                            quoteMsgText = quotedMessageText,
                            quoteImageUrl = quotedImageUrl,
                            quoteImageName = quotedImageName,
                            quoteVideoUrl = quotedVideoUrl,
                            quoteVideoTime = quotedVideoTime,
                            commandId = selectedInstruction?.id,  // 传递指令ID
                            mentionedIds = if (mentionedIdsList.isNotEmpty()) mentionedIdsList else null,  // 传递@用户ID列表
                            onSuccess = {
                                inputText = ""
                                // 清除@用户映射
                                mentionedUsers = emptyMap()
                                // 发送后重置为默认类型
                                selectedMessageType = defaultSendMessageType
                                // 清除引用状态
                                quotedMessageId = null
                                quotedMessageText = null
                                quotedImageUrl = null
                                quotedImageName = null
                                quotedVideoUrl = null
                                quotedVideoTime = null
                                // 清除选中的指令
                                selectedInstruction = null
                                // 发送消息后自动滚动到最新消息
                                coroutineScope.launch {
                                    shouldStickToBottom = true
                                    pendingAutoScrollToBottom = false
                                    listState.scrollToItem(0)
                                }
                                // 发送成功后清除草稿
                                viewModel.clearDraft()
                            },
                            onError = { error ->
                                // 发送失败，保留输入框内容
                                android.util.Log.e("ChatScreen", "发送消息失败: $error")
                                // 可以选择显示Toast提示
                                // android.widget.Toast.makeText(context, "发送失败: $error", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            isSendTextAllowEmptySetting = isSendTextAllowEmptySetting
                        )
                    }
                },
                onImageClick = {
                    // 使用从ChatActivity传递的回调
                    onImagePickerClick()
                },
                onFileClick = {
                    // 使用从ChatActivity传递的回调
                    onFilePickerClick()
                },
                onVideoClick = {
                    // 使用从ChatActivity传递的回调
                    onVideoPickerClick()
                },
                onVoiceMessageSend = { fileKey, fileHash, fileSize, audioDuration ->
                    android.util.Log.w(
                        "ChatScreen",
                        "🎤 voice message sent: chatId=$chatId chatType=$chatType key=$fileKey hash=$fileHash size=$fileSize duration=$audioDuration"
                    )
                },
                onDraftChange = { draftText ->
                    viewModel.sendDraftInput(draftText)
                },
                onCameraClick = {
                    // 使用从ChatActivity传递的回调
                    onCameraClick()
                },
                selectedMessageType = selectedMessageType,
                onMessageTypeChange = { newType ->
                    // 只能选择一个类型，点击已选中的类型则取消（回到文本）
                    selectedMessageType = if (selectedMessageType == newType) 1 else newType
                },
                quotedMessageText = quotedMessageText,
                onClearQuote = {
                    quotedMessageId = null
                    quotedMessageText = null
                    quotedImageUrl = null
                    quotedImageName = null
                    quotedVideoUrl = null
                    quotedVideoTime = null
                },
                onExpressionClick = { expression ->
                    // 发送表情消息（contentType=7）
                    viewModel.sendExpressionMessage(
                        expression = expression,
                        quoteMsgId = quotedMessageId,
                        quoteMsgText = quotedMessageText,
                        quoteImageUrl = quotedImageUrl,
                        quoteImageName = quotedImageName,
                        quoteVideoUrl = quotedVideoUrl,
                        quoteVideoTime = quotedVideoTime
                    )
                    // 清除引用状态
                    quotedMessageId = null
                    quotedMessageText = null
                    quotedImageUrl = null
                    quotedImageName = null
                    quotedVideoUrl = null
                    quotedVideoTime = null
                },
                onStickerClick = { stickerItem ->
                    // 发送表情包贴纸消息（contentType=7）
                    viewModel.sendStickerMessage(
                        stickerItem = stickerItem,
                        quoteMsgId = quotedMessageId,
                        quoteMsgText = quotedMessageText,
                        quoteImageUrl = quotedImageUrl,
                        quoteImageName = quotedImageName,
                        quoteVideoUrl = quotedVideoUrl,
                        quoteVideoTime = quotedVideoTime
                    )
                    // 清除引用状态
                    quotedMessageId = null
                    quotedMessageText = null
                    quotedImageUrl = null
                    quotedImageName = null
                    quotedVideoUrl = null
                    quotedVideoTime = null
                },
                onInstructionClick = { instruction ->
                    android.util.Log.d("ChatScreen", "🎯 用户点击指令: /${instruction.name} (id=${instruction.id}, type=${instruction.type})")
                    
                    // 根据指令类型处理
                    when (instruction.type) {
                        1 -> {
                            android.util.Log.d("ChatScreen", "📝 普通指令，应用默认文本: ${instruction.defaultText}")
                            // 普通指令：应用默认文本（如果有）
                            selectedInstruction = instruction
                            if (instruction.defaultText.isNotEmpty() && inputText.isBlank()) {
                                inputText = instruction.defaultText
                            }
                        }
                        2 -> {
                            android.util.Log.d("ChatScreen", "⚡ 直发指令，立即发送消息")
                            // 直发指令：发送 "/{指令名称}"
                            val textToSend = "/${instruction.name}"
                            android.util.Log.d("ChatScreen", "📤 直发指令发送文本: '$textToSend'")
                            
                            // 立即发送消息
                            selectedInstruction = instruction
                            viewModel.sendMessage(
                                text = textToSend,
                                contentType = selectedMessageType,
                                quoteMsgId = quotedMessageId,
                                quoteMsgText = quotedMessageText,
                                quoteImageUrl = quotedImageUrl,
                                quoteImageName = quotedImageName,
                                quoteVideoUrl = quotedVideoUrl,
                                quoteVideoTime = quotedVideoTime,
                                commandId = instruction.id,
                                isSendTextAllowEmptySetting = isSendTextAllowEmptySetting,// 这里有必要允许发送空文本消息吗，Bot的直发指令应该不能是空文本吧
                            )
                            inputText = ""
                            selectedInstruction = null
                            quotedMessageId = null
                            quotedMessageText = null
                            quotedImageUrl = null
                            quotedImageName = null
                            quotedVideoUrl = null
                            quotedVideoTime = null
                        }
                        5 -> {
                            android.util.Log.d("ChatScreen", "📋 表单指令，打开表单填写界面")
                            // 表单指令：打开表单填写Activity
                            com.yhchat.canary.ui.bot.InstructionFormActivity.start(
                                context = context,
                                instruction = instruction,
                                chatId = chatId,
                                chatType = chatType,
                                chatName = chatName
                            )
                        }
                        else -> {
                            android.util.Log.w("ChatScreen", "⚠️ 未知指令类型: ${instruction.type}")
                            // 其他类型指令暂不处理
                            selectedInstruction = instruction
                        }
                    }
                },
                groupId = if (chatType == 2) chatId else null,  // 只在群聊中传递groupId
                botId = if (chatType == 3) chatId else null,  // 机器人私聊传递botId
                selectedInstruction = selectedInstruction,  // 传递选中的指令
                onClearInstruction = {
                    selectedInstruction = null
                },
                focusRequester = inputFocusRequester,  // 传递焦点请求器
                shouldShowKeyboard = shouldShowKeyboard,  // 传递键盘显示状态
                chatId = chatId,
                chatType = chatType.toLong(),
                voiceViewModel = voiceMessageViewModel,
                modifier = Modifier
                    .navigationBarsPadding()  // 自适应导航栏
                    .padding(
                        start = 0.dp,  // 去掉左右padding让输入框占满宽度
                        end = 0.dp,
                        top = 1.dp,
                        bottom = 0.dp  // 导航栏padding已处理
                    )
            )
        }  // 闭合else（输入模式）
        }  // 闭合Column
        }  // 闭合Surface
    }  // 闭合Box（聊天背景容器）
    
    // 图片预览器
    if (showImageViewer && !currentImageUrl.isNullOrEmpty()) {
        ImageViewer(
            imageUrls = currentImageGallery.ifEmpty { listOf(currentImageUrl!!) },
            initialIndex = currentImageIndex,
            onDismiss = {
                showImageViewer = false
                currentImageUrl = null
                currentImageGallery = emptyList()
                currentImageIndex = 0
            }
        )
    }
    
    // 消息编辑对话框
    if (showEditDialog && messageToEdit != null) {
        MessageEditDialog(
            message = messageToEdit!!,
            onDismiss = {
                showEditDialog = false
                messageToEdit = null
            },
            onConfirm = { content, contentType ->
                viewModel.editMessage(
                    chatId = chatId,
                    chatType = chatType,
                    msgId = messageToEdit!!.msgId,
                    content = content,
                    contentType = contentType,
                    quoteMsgId = messageToEdit!!.quoteMsgId,
                    quoteMsgText = messageToEdit!!.content.quoteMsgText,
                    quoteImageUrl = messageToEdit!!.content.quoteImageUrl,
                    quoteImageName = messageToEdit!!.content.quoteImageName,
                    quoteVideoUrl = messageToEdit!!.content.quoteVideoUrl,
                    quoteVideoTime = messageToEdit!!.content.quoteVideoTime,
                    buttons = messageToEdit!!.content.buttons
                )
                showEditDialog = false
                messageToEdit = null
            }
        )
    }

    val selectedBotLlmId = editingBotLlmParamsBotId
    if (selectedBotLlmId != null) {
        val llmValues = uiState.botLlmParamValues[selectedBotLlmId].orEmpty()
        if (llmValues.isNotEmpty()) {
            val llmName = uiState.botLlmRefParams
                .firstOrNull { it.botId == selectedBotLlmId }
                ?.botNickname
                .orEmpty()
            BotLlmParamsDialog(
                botName = llmName,
                params = llmValues,
                onDismiss = { editingBotLlmParamsBotId = null },
                onInputChange = { paramId, value ->
                    viewModel.updateBotLlmInputValue(selectedBotLlmId, paramId, value)
                },
                onSelectChange = { paramId, value ->
                    viewModel.updateBotLlmSelectValue(selectedBotLlmId, paramId, value)
                }
            )
        }
    }

    // 语音转文字对话框
    if (showSttDialog) {
        SpeechToTextDialog(
            isProcessing = isSttProcessing,
            progress = sttProgress,
            result = sttResult,
            onDismiss = {
                showSttDialog = false
                sttAudioUrl = null
                sttResult = null
                sttProgress = ""
                isSttProcessing = false
            },
            onCopyResult = { text ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("stt_result", text)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "已复制识别结果", Toast.LENGTH_SHORT).show()
            },
            onInsertToInput = { text ->
                inputText = if (inputText.isEmpty()) text else "$inputText $text"
                showSttDialog = false
                sttAudioUrl = null
                sttResult = null
                sttProgress = ""
                isSttProcessing = false
            }
        )
    }
    
    // 生成图片加载中指示器
    if (isGeneratingImage) {
        YhAlertDialog(
            onDismissRequest = {},
            title = { Text("生成中...") },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    YhCircularProgressIndicator()
                }
            },
            confirmButton = {}
        )
    }
    
    // 消息图片预览对话框
    if (showImagePreviewDialog && generatedImageBitmap != null) {
        MessageImagePreviewDialog(
            bitmap = generatedImageBitmap!!,
            selectedCount = selectedMessageIds.size,
            chatName = chatName,
            onDismiss = {
                showImagePreviewDialog = false
                generatedImageBitmap?.recycle()
                generatedImageBitmap = null
            },
            onSave = {
                coroutineScope.launch {
                    val success = saveBitmapToGallery(context, generatedImageBitmap!!)
                    Toast.makeText(
                        context,
                        if (success) "图片已保存到 Pictures/YhChat" else "保存失败",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    if (showLiveRoomsSheet && liveEnabled) {
        LiveRoomsBottomSheet(
            rooms = liveRoomsState.rooms,
            isLoading = liveRoomsState.isLoading,
            joiningRoomId = liveRoomsState.joiningRoomId,
            error = liveRoomsState.error,
            onDismiss = { showLiveRoomsSheet = false },
            onRetry = {
                liveRoomsViewModel.clearError()
                liveRoomsViewModel.refresh(groupId = chatId)
            },
            onJoin = { room ->
                liveRoomsViewModel.join(room)
            }
        )
    }

    
    // 浮动TTS窗口
    TtsFloatingWindow(
        visible = showFloatingTtsWindow,
        chatId = chatId,
        chatType = chatType,
        quotedMessageId = quotedMessageId,
        quotedMessageText = quotedMessageText,
        quotedImageUrl = quotedImageUrl,
        quotedImageName = quotedImageName,
        quotedVideoUrl = quotedVideoUrl,
        quotedVideoTime = quotedVideoTime,
        viewModel = viewModel,
        onClose = { showFloatingTtsWindow = false },
        onQuoteClear = {
            quotedMessageId = null
            quotedMessageText = null
            quotedImageUrl = null
            quotedImageName = null
            quotedVideoUrl = null
            quotedVideoTime = null
        }
    )
    
    
}

private data class ListAnchorSnapshot(
    val messageKey: String,
    val scrollOffset: Int,
    val listIdentity: String
)

private fun buildChatImageGallery(messages: List<ChatMessage>): List<String> {
    return messages.asSequence()
        .mapNotNull { message ->
            when (message.contentType) {
                2 -> message.content.imageUrl?.takeIf { it.isNotBlank() }
                7 -> message.content.imageUrl?.takeIf { it.isNotBlank() }
                    ?: message.content.stickerUrl
                        ?.takeIf { it.isNotBlank() }
                        ?.let { if (it.startsWith("http")) it else "https://chat-img.jwznb.com/$it" }
                else -> null
            }
        }
        .distinct()
        .toList()
}

private fun buildMessageItemKeys(messages: List<ChatMessage>): List<String> {
    val keyUsageCount = mutableMapOf<String, Int>()
    return messages.map { message ->
        val baseKey = buildStableMessageIdentity(message)
        val usedCount = keyUsageCount.getOrDefault(baseKey, 0)
        keyUsageCount[baseKey] = usedCount + 1
        if (usedCount == 0) baseKey else "$baseKey#dup$usedCount"
    }
}

private fun buildMessageListIdentity(keys: List<String>): String {
    return keys.joinToString(separator = "\u001F")
}

private fun buildStableMessageIdentity(message: ChatMessage): String {
    if (message.msgId.isNotBlank()) {
        return "msg:${message.msgId}"
    }

    val msgSeq = message.msgSeq
    if (msgSeq != null && msgSeq > 0L) {
        return "seq:${message.chatId.orEmpty()}:${message.chatType ?: -1}:$msgSeq"
    }

    return buildString {
        append("fallback:")
        append(message.sender.chatId)
        append('|')
        append(message.sender.chatType)
        append('|')
        append(message.chatId.orEmpty())
        append('|')
        append(message.chatType ?: -1)
        append('|')
        append(message.recvId.orEmpty())
        append('|')
        append(message.direction)
        append('|')
        append(message.contentType)
        append('|')
        append(message.sendTime)
        append('|')
        append(message.editTime ?: -1L)
        append('|')
        append(message.msgDeleteTime ?: -1L)
        append('|')
        append(message.quoteMsgId.orEmpty())
        append('|')
        append(message.content.text.orEmpty())
        append('|')
        append(message.content.imageUrl.orEmpty())
        append('|')
        append(message.content.fileUrl.orEmpty())
        append('|')
        append(message.content.fileName.orEmpty())
        append('|')
        append(message.content.videoUrl.orEmpty())
        append('|')
        append(message.content.audioUrl.orEmpty())
        append('|')
        append(message.content.stickerUrl.orEmpty())
        append('|')
        append(message.content.postId.orEmpty())
        append('|')
        append(message.content.expressionId.orEmpty())
        append('|')
        append(message.content.buttons.orEmpty())
        append('|')
        append(message.content.form.orEmpty())
    }
}
