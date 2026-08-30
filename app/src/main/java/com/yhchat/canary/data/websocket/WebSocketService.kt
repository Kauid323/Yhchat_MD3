package com.yhchat.canary.data.websocket

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.LocusId
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.app.TaskStackBuilder
import androidx.core.graphics.drawable.IconCompat
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.yhchat.canary.AppForegroundTracker
import com.yhchat.canary.MainActivity
import com.yhchat.canary.R
import com.yhchat.canary.data.model.ChatMessage
import com.yhchat.canary.data.repository.CacheRepository
import com.yhchat.canary.data.repository.TokenRepository
import com.yhchat.canary.proto.chat_ws_go.WsMsg
import com.yhchat.canary.proto.chat_ws_go.bot_board_message
import com.yhchat.canary.proto.chat_ws_go.draft_input
import com.yhchat.canary.proto.chat_ws_go.edit_message
import com.yhchat.canary.proto.chat_ws_go.heartbeat_ack
import com.yhchat.canary.proto.chat_ws_go.push_message
import com.yhchat.canary.proto.chat_ws_go.stream_message
import com.yhchat.canary.ui.chat.ChatActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.*
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WebSocket服务，用于实时接收聊天消息和发送草稿同步
 */
@Singleton
class WebSocketService @Inject constructor(
    private val tokenRepository: TokenRepository,
    private val context: Context
) {
    private val tag = "WebSocketService"
    private val gson = Gson()
    
    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var isConnected = false
    private var isConnecting = false
    private var shouldReconnect = true
    private var reconnectAttempts = 0
    private var currentUserId: String? = null
    private var currentPlatform: String = "windows"
    private var currentDeviceId: String = UUID.randomUUID().toString().replace("-", "")
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // 消息事件流 - 用于UI更新
    private val _messageEvents = MutableSharedFlow<MessageEvent>(
        replay = 0,
        extraBufferCapacity = 100
    )
    val messageEvents: SharedFlow<MessageEvent> = _messageEvents.asSharedFlow()
    
    // 连接状态流
    private val _connectionState = MutableSharedFlow<ConnectionState>(
        replay = 1,
        extraBufferCapacity = 10
    )
    val connectionState: SharedFlow<ConnectionState> = _connectionState.asSharedFlow()

    // 会话更新流 - 当收到新消息时更新会话列表
    private val _conversationUpdates = MutableSharedFlow<ConversationUpdate>(
        replay = 0,
        extraBufferCapacity = 50
    )
    val conversationUpdates: SharedFlow<ConversationUpdate> = _conversationUpdates.asSharedFlow()
    
    // 草稿更新流 - 用于多端草稿同步
    private val _draftUpdates = MutableSharedFlow<DraftUpdate>(
        replay = 0,
        extraBufferCapacity = 100
    )
    val draftUpdates: SharedFlow<DraftUpdate> = _draftUpdates.asSharedFlow()
    
    // 黑名单仓库
    private val blocklistRepository by lazy { com.yhchat.canary.data.repository.BlocklistRepository(context) }
    
    // 通知消息历史 - 用于消息堆叠显示（chatId -> 消息列表）
    private val notificationMessageHistory = mutableMapOf<String, MutableList<NotificationMessage>>()
    
    // 头像缓存 - 避免重复加载（avatarUrl -> Bitmap）
    private val avatarBitmapCache = mutableMapOf<String, Bitmap>()
    
    // 通知消息数据类
    private data class NotificationMessage(
        val senderName: String,
        val content: String,
        val timestamp: Long,
        val senderAvatarUrl: String? = null // 发送者头像URL
    )

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // 禁用读取超时，依赖心跳机制
        .writeTimeout(10, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS) // OkHttp 自动 ping/pong 帧
        .retryOnConnectionFailure(true)
        .build()

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "chat_messages"
        private const val NOTIFICATION_ID_BASE = 1000
    }
    
    init {
        createNotificationChannel()
    }
    
    /**
     * 连接WebSocket
     */
    suspend fun connect(userId: String, platform: String = "windows") {
        shouldReconnect = true
        currentUserId = userId
        currentPlatform = platform

        if (isConnected && webSocket != null) {
            Log.d(tag, "Already connected")
            return
        }
        if (isConnecting) {
            Log.d(tag, "Connect already in progress, skip duplicate request")
            return
        }

        reconnectJob?.cancel()
        reconnectJob = null
        isConnecting = true
        
        // 确保完全清理旧连接
        webSocket?.let { oldSocket ->
            Log.d(tag, "Cleaning up old WebSocket connection")
            cleanup()
            oldSocket.close(1000, "Reconnecting")
            webSocket = null
            // 等待旧连接完全关闭
            delay(300)
        }

        val token = tokenRepository.getTokenSync()
        if (token == null) {
            Log.e(tag, "No token available")
            isConnecting = false
            scheduleReconnect("missing_token")
            _connectionState.emit(ConnectionState.Error("未登录"))
            return
        }
        
        try {
            Log.d(tag, "Connecting to WebSocket...")
            _connectionState.emit(ConnectionState.Connecting)
            
            val request = Request.Builder()
                .url("wss://chat-ws-go.jwzhd.com/ws")
                .build()
            
            webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (!isCurrentSocket(webSocket)) {
                        Log.w(tag, "Ignore onOpen from stale socket")
                        return
                    }
                    Log.d(tag, "WebSocket opened")
                    isConnected = true
                    isConnecting = false
                    reconnectAttempts = 0
                    reconnectJob?.cancel()
                    reconnectJob = null
                    
                    // 发送登录请求 - 参考yh_user_sdk/core/ws.py
                    val loginData = JsonObject().apply {
                        addProperty("seq", UUID.randomUUID().toString().replace("-", ""))
                        addProperty("cmd", "login")
                        add("data", JsonObject().apply {
                            addProperty("userId", userId)
                            addProperty("token", token)
                            addProperty("platform", platform)
                            addProperty("deviceId", currentDeviceId)
                        })
                    }
                    
                    val loginJson = gson.toJson(loginData)
                    Log.d(tag, "Sending login: $loginJson")
                    webSocket.send(loginJson)
                    
                    scope.launch {
                        _connectionState.emit(ConnectionState.Connected)
                    }
                    
                    // 启动心跳 - 30秒间隔
                    startHeartbeat(webSocket)
                }

                override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                    if (!isCurrentSocket(webSocket)) {
                        Log.w(tag, "Ignore binary message from stale socket")
                        return
                    }
                    try {
                        Log.d(tag, "📩 Received binary message (${bytes.size} bytes)")
                        handleBinaryMessage(bytes.toByteArray())
                    } catch (e: Exception) {
                        Log.e(tag, "❌ Error handling binary message", e)
                        // 不要因为解析错误而断开连接，继续接收下一条消息
                    }
                }
                
                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (!isCurrentSocket(webSocket)) {
                        Log.w(tag, "Ignore text message from stale socket")
                        return
                    }
                    try {
                        Log.d(tag, "Received text message (unexpected): $text")
                        // WebSocket 应该只返回二进制 protobuf 消息，不应该有文本消息
                        // 如果收到文本消息，可能是服务器错误或连接问题
                    } catch (e: Exception) {
                        Log.e(tag, "Error handling text message", e)
                    }
                }
                
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    if (!isCurrentSocket(webSocket)) {
                        Log.w(tag, "Ignore onClosing from stale socket")
                        return
                    }
                    Log.d(tag, "WebSocket closing: $code $reason")
                }
                
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (!isCurrentSocket(webSocket)) {
                        Log.w(tag, "Ignore onClosed from stale socket")
                        return
                    }
                    Log.d(tag, "WebSocket closed: $code $reason")
                    cleanup()
                    this@WebSocketService.webSocket = null
                    isConnecting = false
                    
                    scope.launch {
                        _connectionState.emit(ConnectionState.Disconnected)
                    }
                    scheduleReconnect("closed_$code")
                }
                
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (!isCurrentSocket(webSocket)) {
                        Log.w(tag, "Ignore onFailure from stale socket")
                        return
                    }
                    Log.e(tag, "❌ WebSocket failure: ${t.javaClass.simpleName} - ${t.message}", t)
                    if (response != null) {
                        Log.e(tag, "Response code: ${response.code}, message: ${response.message}")
                    }
                    cleanup()
                    this@WebSocketService.webSocket = null
                    isConnecting = false
                    
                    scope.launch {
                        _connectionState.emit(ConnectionState.Error(t.message ?: "连接失败"))
                    }
                    scheduleReconnect("failure_${t.javaClass.simpleName}")
                }
            })
            
        } catch (e: Exception) {
            isConnecting = false
            Log.e(tag, "Error connecting to WebSocket", e)
            _connectionState.emit(ConnectionState.Error(e.message ?: "连接失败"))
            scheduleReconnect("connect_exception_${e.javaClass.simpleName}")
        }
    }
    
    private fun isCurrentSocket(socket: WebSocket): Boolean {
        return socket === webSocket
    }

    private fun scheduleReconnect(reason: String) {
        if (!shouldReconnect) {
            Log.d(tag, "Reconnect disabled, skip. reason=$reason")
            return
        }

        val userId = currentUserId
        if (userId.isNullOrEmpty()) {
            Log.w(tag, "No userId cached, skip reconnect. reason=$reason")
            return
        }

        if (isConnected) {
            Log.d(tag, "Already connected, skip reconnect scheduling")
            return
        }

        if (reconnectJob?.isActive == true) {
            Log.d(tag, "Reconnect already scheduled, skip duplicate. reason=$reason")
            return
        }

        val attempt = reconnectAttempts + 1
        val maxExp = (attempt - 1).coerceAtMost(4)
        val backoffDelay = 2000L * (1L shl maxExp)
        val jitter = (0L..600L).random()
        val delayMs = (backoffDelay + jitter).coerceAtMost(30000L)

        reconnectJob = scope.launch {
            Log.d(tag, "Scheduling reconnect in ${delayMs}ms (attempt=$attempt, reason=$reason)")
            delay(delayMs)

            if (!shouldReconnect || isConnected) {
                Log.d(tag, "Reconnect canceled by state change")
                return@launch
            }

            reconnectAttempts = attempt
            runCatching {
                connect(userId, currentPlatform)
            }.onFailure { e ->
                Log.e(tag, "Reconnect attempt failed", e)
            }
        }
    }

    /**
     * 发送草稿同步消息
     */
    fun sendDraftInput(chatId: String, input: String) {
        if (!isConnected || webSocket == null) {
            Log.w(tag, "WebSocket not connected, cannot send draft")
            return
        }
        
        try {
            // 参考用户提供的草稿同步格式
            val dataObject = JsonObject().apply {
                addProperty("chatId", chatId)
                addProperty("input", input)
                addProperty("deviceId", currentDeviceId)
            }
            val draftData = JsonObject().apply {
                addProperty("seq", UUID.randomUUID().toString().replace("-", ""))
                addProperty("cmd", "inputInfo")
                add("data", dataObject)
            }
            
            val draftJson = gson.toJson(draftData)
            Log.d(tag, "Sending draft input: $draftJson")
            webSocket?.send(draftJson)
            
        } catch (e: Exception) {
            Log.e(tag, "Error sending draft input", e)
        }
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        Log.d(tag, "Disconnecting WebSocket")
        shouldReconnect = false
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempts = 0
        isConnecting = false
        cleanup()
        webSocket?.close(1000, "User disconnect")
        webSocket = null
    }
    
    /**
     * 启动心跳 - 参考yh_user_sdk/core/ws.py
     */
    private fun startHeartbeat(webSocket: WebSocket) {
        heartbeatJob = scope.launch {
            while (isConnected && currentCoroutineContext().isActive) {
                try {
                    delay(30000) // 30秒心跳间隔
                    
                    val heartbeatData = JsonObject().apply {
                        addProperty("seq", UUID.randomUUID().toString().replace("-", ""))
                        addProperty("cmd", "heartbeat")
                        add("data", JsonObject())
                    }
                    
                    val heartbeatJson = gson.toJson(heartbeatData)
                    Log.d(tag, "Sending heartbeat")
                    webSocket.send(heartbeatJson)
                    
                } catch (e: Exception) {
                    Log.e(tag, "Error sending heartbeat", e)
                    break
                }
            }
        }
    }

    /**
     * 处理二进制消息 - 参考yh_user_sdk/core/ws.py的decode方法
     */
    private fun handleBinaryMessage(bytes: ByteArray) {
        try {
            // 按照yh_user_sdk的方式，首先尝试解析为heartbeat_ack获取命令类型
            val tempMsg = heartbeat_ack.parseFrom(bytes)
            val cmd = tempMsg.info.cmd
            val seq = tempMsg.info.seq
            
            Log.d(tag, "Message command: $cmd, seq: $seq")
            
            when (cmd) {
                "login_ack" -> {
                    Log.d(tag, "✅ Received login_ack (binary protobuf)")
                    // 登录响应，连接已完全建立（如果服务器有发送的话）
                }
                
                "heartbeat_ack" -> {
                    Log.d(tag, "✅ Received heartbeat_ack")
                    // 心跳响应，连接正常
                }
                
                "push_message" -> {
                    // 新消息推送
                    val pushMessage = push_message.parseFrom(bytes)
                    if (pushMessage.hasData() && pushMessage.data.hasMsg()) {
                        val protoMsg = pushMessage.data.msg
                        val chatMessage = convertWsMsgToMessage(protoMsg)
                        
                        // 详细日志用于调试
                        Log.d(tag, "Push message details:")
                        Log.d(tag, "  - Message ID: ${chatMessage.msgId}")
                        Log.d(tag, "  - Sender: ${chatMessage.sender.chatId} (type: ${chatMessage.sender.chatType})")
                        Log.d(tag, "  - Target Chat: ${chatMessage.chatId} (type: ${chatMessage.chatType})")
                        Log.d(tag, "  - Receiver: ${chatMessage.recvId}")
                        Log.d(tag, "  - Content: ${chatMessage.content.text?.take(50) ?: "[非文本消息]"}")
                        
                        scope.launch {
                            val senderId = chatMessage.sender.chatId
                            if (blocklistRepository.isUserBlocked(senderId)) {
                                Log.d(tag, "Blocked user $senderId message ignored: ${chatMessage.msgId}")
                                return@launch
                            }
                            
                            // 发送消息事件供UI更新
                            _messageEvents.emit(MessageEvent.NewMessage(chatMessage))
                            
                            // 更新会话列表
                            _conversationUpdates.emit(ConversationUpdate.NewMessage(chatMessage))
                            
                            // 显示通知（在协程中）
                            showMessageNotificationAsync(chatMessage)
                        }
                        
                        Log.d(tag, "Received new message: ${chatMessage.msgId}")
                    }
                }
                
                "edit_message" -> {
                    // 消息编辑
                    val editMessage = edit_message.parseFrom(bytes)
                    if (editMessage.hasData() && editMessage.data.hasMsg()) {
                        val protoMsg = editMessage.data.msg
                        val chatMessage = convertWsMsgToMessage(protoMsg)

                        Log.d(
                            tag,
                            "Edit message details: msgId=${chatMessage.msgId}, " +
                                "chatId=${chatMessage.chatId}, recvId=${chatMessage.recvId}, " +
                                "sender=${chatMessage.sender.chatId}, contentType=${chatMessage.contentType}, " +
                                "text=${chatMessage.content.text?.take(50)}, " +
                                "buttons=${chatMessage.content.buttons?.take(50)}, " +
                                "quoteMsgText=${chatMessage.content.quoteMsgText?.take(50)}, " +
                                "editTime=${chatMessage.editTime}"
                        )
                        
                        scope.launch {
                            val senderId = chatMessage.sender.chatId
                            if (blocklistRepository.isUserBlocked(senderId)) {
                                Log.d(tag, "Blocked user $senderId edit message ignored: ${chatMessage.msgId}")
                                return@launch
                            }
                            
                            _messageEvents.emit(MessageEvent.MessageEdited(chatMessage))
                            _conversationUpdates.emit(ConversationUpdate.MessageEdited(chatMessage))
                        }
                        
                        Log.d(tag, "Received edited message: ${chatMessage.msgId}")
                    }
                }
                
                "draft_input" -> {
                    // 草稿同步（多端输入同步）
                    val draftInput = draft_input.parseFrom(bytes)
                    if (draftInput.hasData() && draftInput.data.hasDraft()) {
                        val draft = draftInput.data.draft
                        val displayInput = if (draft.input.length > 50) 
                            "${draft.input.take(50)}..." 
                        else 
                            draft.input
                        Log.d(tag, "📝 收到多端草稿同步: chatId=${draft.chatId}, length=${draft.input.length}, content='$displayInput'")
                        
                        scope.launch {
                            // 直接发射草稿更新事件，只需要chatId判断就够了
                            _draftUpdates.emit(
                                DraftUpdate(
                                    chatId = draft.chatId,
                                    input = draft.input
                                )
                            )
                            
                            Log.d(tag, "✅ 草稿更新事件已发射")
                        }
                    }
                }
                
                "bot_board_message" -> {
                    // 机器人公告
                    val botBoardMessage = bot_board_message.parseFrom(bytes)
                    if (botBoardMessage.hasData() && botBoardMessage.data.hasBoard()) {
                        val board = botBoardMessage.data.board
                        Log.d(tag, "Received bot board message from ${board.botId}: ${board.content}")
                        
                        scope.launch {
                            _messageEvents.emit(MessageEvent.BotBoardMessage(
                                board.botId, 
                                board.chatId, 
                                board.content
                            ))
                        }
                    }
                }
                
                "stream_message" -> {
                    // 流式消息
                    val streamMsg = stream_message.parseFrom(bytes)
                    if (streamMsg.hasData() && streamMsg.data.hasMsg()) {
                        val msg = streamMsg.data.msg
                        Log.d(tag, "Received stream message for chat ${msg.chatId}, msgId: ${msg.msgId}, content: ${msg.content}")
                        
                        scope.launch {
                            _messageEvents.emit(MessageEvent.StreamMessage(
                                msgId = msg.msgId,
                                recvId = msg.recvId,
                                chatId = msg.chatId,
                                content = msg.content
                            ))
                        }
                    }
                }
                
                else -> {
                    Log.d(tag, "Unhandled message command: $cmd")
                }
            }
            
        } catch (e: Exception) {
            Log.e(tag, "Error parsing binary message", e)
        }
    }
    
    /**
     * 将WebSocket Proto消息转换为应用内消息模型
     * 注意：消息应该放在protoMsg.chatId对应的会话中，而不是sender.chatId
     */
    private fun convertWsMsgToMessage(protoMsg: WsMsg): ChatMessage {
        val sender = com.yhchat.canary.data.model.MessageSender(
            chatId = protoMsg.sender.chatId,
            chatType = protoMsg.sender.chatType,
            name = protoMsg.sender.name,
            avatarUrl = protoMsg.sender.avatarUrl,
            tagOld = protoMsg.sender.tagOldList,
            tag = protoMsg.sender.tagList.map { tag ->
                com.yhchat.canary.data.model.MessageTag(
                    id = tag.id,
                    text = tag.text,
                    color = tag.color
                )
            }
        )

        val content = com.yhchat.canary.data.model.MessageContent(
            text = if (protoMsg.content.text.isNotEmpty()) protoMsg.content.text else null,
            buttons = if (protoMsg.content.buttons.isNotEmpty()) protoMsg.content.buttons else null,
            imageUrl = if (protoMsg.content.imageUrl.isNotEmpty()) protoMsg.content.imageUrl else null,
            fileName = if (protoMsg.content.fileName.isNotEmpty()) protoMsg.content.fileName else null,
            fileUrl = if (protoMsg.content.fileUrl.isNotEmpty()) protoMsg.content.fileUrl else null,
            form = if (protoMsg.content.form.isNotEmpty()) protoMsg.content.form else null,
            quoteMsgText = if (protoMsg.content.quoteMsgText.isNotEmpty()) protoMsg.content.quoteMsgText else null,
            stickerUrl = if (protoMsg.content.stickerUrl.isNotEmpty()) protoMsg.content.stickerUrl else null,
            postId = if (protoMsg.content.postId.isNotEmpty()) protoMsg.content.postId else null,
            postTitle = if (protoMsg.content.postTitle.isNotEmpty()) protoMsg.content.postTitle else null,
            postContent = if (protoMsg.content.postContent.isNotEmpty()) protoMsg.content.postContent else null,
            postContentType = if (protoMsg.content.postContentType.isNotEmpty()) protoMsg.content.postContentType else null,
            expressionId = if (protoMsg.content.expressionId.isNotEmpty()) protoMsg.content.expressionId else null,
            fileSize = if (protoMsg.content.fileSize > 0) protoMsg.content.fileSize else null,
            videoUrl = if (protoMsg.content.videoUrl.isNotEmpty()) protoMsg.content.videoUrl else null,
            audioUrl = if (protoMsg.content.audioUrl.isNotEmpty()) protoMsg.content.audioUrl else null,
            audioTime = if (protoMsg.content.audioTime > 0) protoMsg.content.audioTime else null,
            stickerItemId = if (protoMsg.content.stickerItemId > 0) protoMsg.content.stickerItemId else null,
            stickerPackId = if (protoMsg.content.stickerPackId > 0) protoMsg.content.stickerPackId else null,
            callText = if (protoMsg.content.callText.isNotEmpty()) protoMsg.content.callText else null,
            callStatusText = if (protoMsg.content.callStatusText.isNotEmpty()) protoMsg.content.callStatusText else null,
            width = if (protoMsg.content.width > 0) protoMsg.content.width else null,
            height = if (protoMsg.content.height > 0) protoMsg.content.height else null
        )

        val cmd = if (protoMsg.hasCmd()) {
            com.yhchat.canary.data.model.MessageCmd(
                name = protoMsg.cmd.name,
                type = protoMsg.cmd.id.toInt()
            )
        } else null

        val direction = when {
            protoMsg.sender.chatId.isBlank() -> ""
            !currentUserId.isNullOrBlank() && protoMsg.sender.chatId.isNotBlank() -> {
                if (protoMsg.sender.chatId == currentUserId) "right" else "left"
            }
            protoMsg.sender.chatId == protoMsg.recvId -> "right"
            else -> "left"
        }
        
        return ChatMessage(
            msgId = protoMsg.msgId,
            sender = sender,
            direction = direction,
            contentType = protoMsg.contentType,
            content = content,
            sendTime = protoMsg.timestamp,
            cmd = cmd,
            msgDeleteTime = if (protoMsg.deleteTime > 0) protoMsg.deleteTime else null,
            quoteMsgId = if (protoMsg.quoteMsgId.isNotEmpty()) protoMsg.quoteMsgId else null,
            msgSeq = if (protoMsg.msgSeq > 0) protoMsg.msgSeq else null,
            editTime = if (protoMsg.editTime > 0) protoMsg.editTime else null,
            chatId = protoMsg.chatId.takeIf { it.isNotBlank() },
            chatType = protoMsg.chatType.takeIf { it > 0 },
            recvId = protoMsg.recvId.takeIf { it.isNotBlank() }
        )
    }
    
    /**
     * 从缓存或网络加载头像（用于通知显示）
     * 优先使用内存缓存，然后是Coil磁盘缓存，最后从网络加载
     */
    private suspend fun loadAvatarBitmap(context: Context, avatarUrl: String?): Bitmap? {
        if (avatarUrl.isNullOrEmpty()) {
            Log.d(tag, "⚠️ 头像URL为空")
            return null
        }
        
        // 1. 检查内存缓存
        avatarBitmapCache[avatarUrl]?.let {
            Log.d(tag, "✅ 从内存缓存加载头像")
            return it
        }
        
        return try {
            // 提取文件名（用于日志）
            val fileName = avatarUrl.substringAfterLast("/").substringBefore("?")
            Log.d(tag, "🖼️ 加载通知头像: $fileName")
            
            // 2. 使用Coil加载（会自动使用磁盘缓存）
            val imageLoader = coil.Coil.imageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(avatarUrl)
                .addHeader("Referer", "https://myapp.jwznb.com")
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
                .allowHardware(false) // 不使用硬件加速，以便转换为Bitmap
                .size(128, 128) // 限制大小，节省内存和加载时间
                .build()
            
            val result = imageLoader.execute(request)
            if (result is SuccessResult) {
                val drawable = result.drawable
                if (drawable is BitmapDrawable) {
                    val bitmap = drawable.bitmap
                    Log.d(tag, "✅ 头像加载成功: ${bitmap.width}x${bitmap.height}")
                    
                    // 存入内存缓存
                    avatarBitmapCache[avatarUrl] = bitmap
                    
                    // 限制缓存大小（最多缓存50个头像）
                    if (avatarBitmapCache.size > 50) {
                        val oldestKey = avatarBitmapCache.keys.first()
                        avatarBitmapCache.remove(oldestKey)
                        Log.d(tag, "🗑️ 清理头像缓存: $oldestKey")
                    }
                    
                    bitmap
                } else {
                    Log.w(tag, "⚠️ 头像不是BitmapDrawable类型: ${drawable.javaClass.simpleName}")
                    null
                }
            } else {
                Log.w(tag, "⚠️ 头像加载失败: $fileName")
                null
            }
        } catch (e: Exception) {
            Log.e(tag, "❌ 加载头像异常: $avatarUrl", e)
            null
        }
    }
    
    /**
     * 获取会话头像URL（从数据库）
     */
    private suspend fun getConversationAvatarUrl(chatId: String, chatType: Int): String? {
        return try {
            val cacheRepository = CacheRepository(context)
            val conversation = cacheRepository.getCachedConversationsSync()
                .firstOrNull { it.chatId == chatId && it.chatType == chatType }
            
            conversation?.avatarUrl?.also {
                Log.d(tag, "✅ 从数据库获取会话头像: $it")
            }
        } catch (e: Exception) {
            Log.e(tag, "❌ 获取会话头像失败", e)
            null
        }
    }
    
    /**
     * 显示消息通知（异步版本，支持加载头像）
     */
    private suspend fun showMessageNotificationAsync(message: ChatMessage) {
        withContext(Dispatchers.IO) {
            try {
                showMessageNotification(message)
            } catch (e: Exception) {
                Log.e(tag, "Error showing notification", e)
            }
        }
    }
    
    /**
     * 显示消息通知
     */
    private suspend fun showMessageNotification(message: ChatMessage) {
        try {
            // 不为自己的消息显示通知
            if (message.sender.chatId == currentUserId) {
                return
            }

            // 应用在前台时不弹出系统通知
            if (AppForegroundTracker.isInForeground) {
                return
            }

            // 使用与会话列表一致的逻辑确定目标会话
            val isPrivateChat = message.chatId == message.recvId
            val targetChatId = if (isPrivateChat) {
                message.sender.chatId
            } else {
                message.chatId ?: message.sender.chatId
            }
            val targetChatType = if (isPrivateChat) {
                message.sender.chatType
            } else {
                message.chatType ?: message.sender.chatType
            }

            // 黑名单过滤：被屏蔽用户的消息不弹出通知
            if (blocklistRepository.isUserBlocked(message.sender.chatId) || (isPrivateChat && blocklistRepository.isUserBlocked(targetChatId))) {
                Log.d(tag, "Sender $targetChatId is in blocklist, skip notification")
                return
            }

            // 免打扰：不弹出该会话的系统通知
            val cacheRepository = CacheRepository(context)
            val isMuted = cacheRepository.getCachedConversationsSync()
                .firstOrNull { it.chatId == targetChatId && it.chatType == targetChatType }
                ?.doNotDisturb == 1
            
            if (isMuted) {
                Log.d(tag, "Conversation is muted (doNotDisturb==1), skip notification: chatId=$targetChatId")
                return
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // 构建通知内容
            val senderName = message.sender.name.ifEmpty { "未知用户" }
            val messageContent = when (message.contentType) {
                8 -> "HTML消息" // HTML消息
                3 -> "Markdown消息" // Markdown消息
                else -> when {
                    !message.content.text.isNullOrEmpty() -> message.content.text
                    !message.content.imageUrl.isNullOrEmpty() -> "[图片]"
                    !message.content.fileUrl.isNullOrEmpty() -> "[文件]"
                    !message.content.audioUrl.isNullOrEmpty() -> "[语音]"
                    !message.content.videoUrl.isNullOrEmpty() -> "[视频]"
                    !message.content.stickerUrl.isNullOrEmpty() -> "[表情]"
                    else -> "[消息]"
                }
            }
            
            // 确定会话名称和头像 - 尝试从缓存获取真实信息
            val conversationTitle = getConversationTitle(targetChatId, targetChatType, senderName)
            
            // 获取会话头像URL（用于通知的大图标）
            // 1. 对于私聊和机器人，使用对方的头像
            // 2. 对于群聊，使用群头像
            val conversationAvatarUrl = getConversationAvatarUrl(targetChatId, targetChatType)
            Log.d(tag, "🎭 会话头像URL: $conversationAvatarUrl (chatType=$targetChatType)")
            
            // 加载会话头像Bitmap（用于通知的大图标）
            val conversationAvatarBitmap = loadAvatarBitmap(context, conversationAvatarUrl)
            
            val chatIntent = Intent(context, ChatActivity::class.java).apply {
                putExtra("chatId", targetChatId)
                putExtra("chatType", targetChatType)
                putExtra("chatName", conversationTitle)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val pendingIntent = TaskStackBuilder.create(context).run {
                addNextIntent(Intent(context, MainActivity::class.java))
                addNextIntent(chatIntent)
                getPendingIntent(
                    targetChatId.hashCode(),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }
            
            // 创建快捷回复输入框
            val remoteInput = RemoteInput.Builder(com.yhchat.canary.receiver.NotificationReplyReceiver.KEY_TEXT_REPLY)
                .setLabel("输入消息...")
                .build()
            
            // 创建快捷回复Intent
            val notificationId = NOTIFICATION_ID_BASE + targetChatId.hashCode()
            val replyIntent = Intent(context, com.yhchat.canary.receiver.NotificationReplyReceiver::class.java).apply {
                putExtra(com.yhchat.canary.receiver.NotificationReplyReceiver.EXTRA_CHAT_ID, targetChatId)
                putExtra(com.yhchat.canary.receiver.NotificationReplyReceiver.EXTRA_CHAT_TYPE, targetChatType)
                putExtra(com.yhchat.canary.receiver.NotificationReplyReceiver.EXTRA_CHAT_NAME, conversationTitle)
                putExtra(com.yhchat.canary.receiver.NotificationReplyReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            }
            
            val replyPendingIntent = PendingIntent.getBroadcast(
                context,
                targetChatId.hashCode() + 1000, // 使用不同的requestCode
                replyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE // 必须使用MUTABLE才能接收RemoteInput
            )
            
            // 创建回复动作
            val replyAction = NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_send, // 使用系统发送图标
                "回复",
                replyPendingIntent
            )
                .addRemoteInput(remoteInput)
                .setAllowGeneratedReplies(true) // 允许智能回复
                .setShowsUserInterface(false) // 不显示UI，直接在通知中回复
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY) // 标记为回复动作
                .build()
            
            // 添加到消息历史
            val historyKey = "$targetChatId-$targetChatType"
            if (!notificationMessageHistory.containsKey(historyKey)) {
                notificationMessageHistory[historyKey] = mutableListOf()
            }
            notificationMessageHistory[historyKey]?.add(
                NotificationMessage(
                    senderName = senderName, 
                    content = messageContent, 
                    timestamp = message.sendTime,
                    senderAvatarUrl = message.sender.avatarUrl
                )
            )
            
            // 限制历史消息数量（最多保留10条）
            val messageHistory = notificationMessageHistory[historyKey] ?: mutableListOf()
            if (messageHistory.size > 10) {
                messageHistory.removeAt(0)
            }
            
            // 创建"我"的Person对象
            val mePerson = androidx.core.app.Person.Builder()
                .setName("我")
                .build()
            
            // 使用 MessagingStyle 显示消息历史堆叠
            val messagingStyle = NotificationCompat.MessagingStyle(mePerson)
                .setConversationTitle(conversationTitle)
                .setGroupConversation(targetChatType == 2) // 群聊时显示为群组会话
            
            // 为每个发送者创建Person对象（包含头像）
            // 预加载所有发送者的头像（限制最多加载5个不同的发送者头像，避免性能问题）
            val uniqueSenders = messageHistory.distinctBy { it.senderAvatarUrl }.take(5)
            val avatarCache = mutableMapOf<String?, Bitmap?>()
            
            uniqueSenders.forEach { msg ->
                if (msg.senderAvatarUrl != null && !avatarCache.containsKey(msg.senderAvatarUrl)) {
                    val bitmap = loadAvatarBitmap(context, msg.senderAvatarUrl)
                    avatarCache[msg.senderAvatarUrl] = bitmap
                    if (bitmap != null) {
                        Log.d(tag, "🎭 预加载发送者头像: ${msg.senderName}")
                    }
                }
            }
            
            // 为每个发送者创建Person对象
            val senderPersonCache = mutableMapOf<String, androidx.core.app.Person>()
            
            // 添加历史消息
            messageHistory.forEach { msg ->
                // 为每个发送者创建或复用Person对象
                val senderPerson = senderPersonCache.getOrPut(msg.senderName) {
                    val personBuilder = androidx.core.app.Person.Builder()
                        .setName(msg.senderName)
                    
                    // 使用缓存的头像
                    val cachedAvatar = avatarCache[msg.senderAvatarUrl]
                    if (cachedAvatar != null) {
                        personBuilder.setIcon(IconCompat.createWithBitmap(cachedAvatar))
                        Log.d(tag, "🎭 为发送者 ${msg.senderName} 设置头像")
                    }
                    
                    personBuilder.build()
                }
                
                messagingStyle.addMessage(
                    msg.content,
                    msg.timestamp,
                    senderPerson
                )
            }
            
            // 计算未读消息数
            val unreadCount = messageHistory.size
            Log.d(tag, "📊 会话 $conversationTitle 有 $unreadCount 条未读消息")
            
            // 创建"标记为已读"动作
            val markReadIntent = Intent(context, com.yhchat.canary.receiver.NotificationReplyReceiver::class.java).apply {
                action = "ACTION_MARK_AS_READ"
                putExtra(com.yhchat.canary.receiver.NotificationReplyReceiver.EXTRA_CHAT_ID, targetChatId)
                putExtra(com.yhchat.canary.receiver.NotificationReplyReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            }
            
            val markReadPendingIntent = PendingIntent.getBroadcast(
                context,
                targetChatId.hashCode() + 2000,
                markReadIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val markReadAction = NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_view,
                "标记已读",
                markReadPendingIntent
            )
                .setShowsUserInterface(false)
                .build()
            
            // 构建通知
            val notificationBuilder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setStyle(messagingStyle)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setNumber(unreadCount) // 显示未读消息数
                .addAction(replyAction) // 添加回复动作
                .addAction(markReadAction) // 添加标记已读动作
                .setOnlyAlertOnce(false) // 每次新消息都提醒
                .setGroup("chat_group_$targetChatId") // 按会话分组
            
            // 设置大图标（会话头像）
            if (conversationAvatarBitmap != null) {
                notificationBuilder.setLargeIcon(conversationAvatarBitmap)
                Log.d(tag, "🎭 通知设置会话头像 (大图标)")
            } else {
                Log.d(tag, "⚠️ 会话头像未加载，使用默认图标")
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // 为聊天创建桌面图标（用于绑定成对话通知）
                val conversationShortcutId = "shortcut_${targetChatId}" //TODO 变量取什么呢
                val conversationShortcutLocusId = "shortcut_locus_${targetChatId}"//TODO 变量取什么呢
                val conversationShortcutBuilder =
                    ShortcutInfo.Builder(context, conversationShortcutId)
                        .setLocusId(LocusId(conversationShortcutLocusId))
                        .setActivity(ComponentName(context, MainActivity::class.java))
                        .setShortLabel(conversationTitle)
                        .setLongLived(true)
                        .setIntent(
                            Intent(context, ChatActivity::class.java).apply {
                                putExtra("chatId", targetChatId)
                                putExtra("chatType", targetChatType)
                                putExtra("chatName", conversationTitle)
                                action = "com.yhchat.canary.action.OPEN_CHAT_SHORTCUT_$targetChatId"
                                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            }
                        )
                if (conversationAvatarBitmap != null)
                    conversationShortcutBuilder.setIcon(Icon.createWithAdaptiveBitmap(conversationAvatarBitmap))
                val conversationShortcut = conversationShortcutBuilder.build()
                val shortcutManager = context.getSystemService(Context.SHORTCUT_SERVICE) as ShortcutManager
                shortcutManager.pushDynamicShortcut(conversationShortcut) //添加图标到桌面APP的长按菜单中
                notificationBuilder.setShortcutId(conversationShortcutId)

                // 通知气泡（悬浮窗）
                val bubbleIntent = Intent(context, ChatActivity::class.java).apply {
                    putExtra("chatId", targetChatId)
                    putExtra("chatType", targetChatType)
                    putExtra("chatName", conversationTitle)
                    putExtra("fromBubble", true)
                    action = "com.yhchat.canary.action.OPEN_CHAT_BUBBLE_$targetChatId"
                    setPackage(context.packageName)
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val bubbleHeight = (context.resources.displayMetrics.density * 640).toInt()
                val notificationBubbleMetadata = NotificationCompat.BubbleMetadata.Builder(
                    PendingIntent.getActivity(
                        context,
                        notificationId,
                        bubbleIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    ),
                    if (conversationAvatarBitmap != null) {
                        IconCompat.createWithAdaptiveBitmap(conversationAvatarBitmap)
                    } else {
                        IconCompat.createWithResource(context, R.mipmap.ic_launcher)
                    }
                ).setDesiredHeight(bubbleHeight).build()
                notificationBuilder.setBubbleMetadata(notificationBubbleMetadata)
            }
            
            val notification = notificationBuilder.build()
            
            // 显示通知，使用正确的chatId的哈希作为通知ID
            notificationManager.notify(notificationId, notification)
            
            Log.d(tag, "✅ 显示带快捷回复的通知: $conversationTitle (ID: $notificationId)")
            
            Log.d(tag, "Shown notification for message from $senderName")
            
        } catch (e: Exception) {
            Log.e(tag, "Error showing notification", e)
        }
    }
    
    /**
     * 清除指定会话的通知消息历史（当用户打开会话时调用）
     */
    fun clearNotificationHistory(chatId: String, chatType: Int) {
        // 为了兼容性保留chatType参数，但历史key还是需要包含chatType的
        val historyKey = "$chatId-$chatType"
        notificationMessageHistory.remove(historyKey)
        Log.d(tag, "🗑️ 清除会话通知历史: $historyKey")
    }
    
    /**
     * 清除头像缓存
     */
    fun clearAvatarCache() {
        avatarBitmapCache.clear()
        Log.d(tag, "🗑️ 清除头像缓存")
    }

    /**
     * 清除账号相关的运行时缓存。
     */
    fun clearRuntimeCaches() {
        notificationMessageHistory.clear()
        avatarBitmapCache.clear()
        Log.d(tag, "🗑️ 清除WebSocket运行时缓存")
    }
    
    /**
     * 获取缓存统计信息
     */
    fun getCacheStats(): String {
        return "消息历史: ${notificationMessageHistory.size} 个会话, 头像缓存: ${avatarBitmapCache.size} 个"
    }
    
    /**
     * 获取会话标题
     */
    private fun getConversationTitle(chatId: String, chatType: Int, senderName: String): String {
        return try {
            // 尝试从缓存中获取会话名称
            val cacheRepository = CacheRepository(context)
            val cachedConversations = runBlocking {
                cacheRepository.getCachedConversationsSync()
            }
            
            val cachedConversation = cachedConversations.find { it.chatId == chatId }
            
            if (cachedConversation != null && cachedConversation.name.isNotEmpty()) {
                cachedConversation.name
            } else {
                // 缓存中没有找到，使用默认名称
                when (chatType) {
                    1 -> senderName // 私聊直接用发送者名称
                    2 -> "群聊" // 群聊
                    3 -> senderName // 机器人聊天用机器人名称
                    else -> "会话"
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to get conversation title from cache", e)
            // 出错时使用默认名称
            when (chatType) {
                1 -> senderName // 私聊直接用发送者名称
                2 -> "群聊" // 群聊
                3 -> senderName // 机器人聊天用机器人名称
                else -> "会话"
            }
        }
    }
    
    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "聊天消息",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "接收新的聊天消息通知"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setAllowBubbles(true)
                }
            }
            notificationManager.createNotificationChannel(channel)

            // 兼容已存在渠道：确保气泡开关也被应用
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                notificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_ID)?.let { existing ->
                    if (!existing.canBubble()) {
                        existing.setAllowBubbles(true)
                        notificationManager.createNotificationChannel(existing)
                    }
                }
            }
        }
    }
    
    /**
     * 清理资源
     */
    private fun cleanup() {
        isConnected = false
        isConnecting = false
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    /**
     * 销毁服务
     */
    fun destroy() {
        shouldReconnect = false
        disconnect()
        scope.cancel()
    }
}

/**
 * WebSocket连接状态
 */
sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

/**
 * 消息事件
 */
sealed class MessageEvent {
    data class NewMessage(val message: ChatMessage) : MessageEvent()
    data class MessageEdited(val message: ChatMessage) : MessageEvent()
    data class MessageDeleted(val msgId: String) : MessageEvent()
    data class DraftUpdated(val chatId: String, val input: String) : MessageEvent()
    data class BotBoardMessage(val botId: String, val chatId: String, val content: String) : MessageEvent()
    data class StreamMessage(val msgId: String, val recvId: String, val chatId: String, val content: String) : MessageEvent()
}

/**
 * 会话更新事件
 */
sealed class ConversationUpdate {
    data class NewMessage(val message: ChatMessage) : ConversationUpdate()
    data class MessageEdited(val message: ChatMessage) : ConversationUpdate()
}

/**
 * 草稿更新事件（多端同步）
 */
data class DraftUpdate(
    val chatId: String,  // 只需要chatId判断就够了
    val input: String,
    val timestamp: Long = System.currentTimeMillis()
)
