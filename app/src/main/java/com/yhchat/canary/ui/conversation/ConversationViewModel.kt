
package com.yhchat.canary.ui.conversation

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhchat.canary.data.model.Conversation
import com.yhchat.canary.data.model.StickyData
import com.yhchat.canary.data.repository.CacheRepository
import com.yhchat.canary.data.repository.ConversationRepository
import com.yhchat.canary.data.repository.TokenRepository
import com.yhchat.canary.data.repository.UserRepository
import com.yhchat.canary.data.websocket.MessageEvent
import com.yhchat.canary.data.websocket.WebSocketManager
import com.yhchat.canary.utils.NetworkUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 会话列表ViewModel - 集成置顶会话功能
 */
@HiltViewModel
class ConversationViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val conversationRepository: ConversationRepository,
    private val cacheRepository: CacheRepository,
    private val webSocketManager: WebSocketManager,
    private val userRepository: UserRepository
) : ViewModel() {

    companion object {
        /** 会话数据超过该时长未联网刷新过时，再次进入页面 / 回到前台会静默刷新一次 */
        private const val CONVERSATION_REFRESH_STALE_MS = 60_000L

        /** 同一时刻重复触发加载时的去重窗口，避免冷启动时 init 和 ON_RESUME 各请求一次 */
        private const val CONVERSATION_LOAD_DEDUP_MS = 3_000L

        private const val TAG = "ConversationViewModel"
    }

    private val blocklistRepository by lazy { com.yhchat.canary.data.repository.BlocklistRepository(context) }
    private var currentActiveUserId: String? = null
    private var cacheCollectionJob: kotlinx.coroutines.Job? = null

    /** 当前账号的 token，用于网络恢复后自动刷新 */
    private var currentToken: String = ""

    /** 上一次「发起」会话列表网络加载的时间，用于去重 */
    private var lastConversationsLoadStartedAtMs = 0L

    /** 上一次「成功」拿到网络会话数据的时间，0 表示本次进程内还没有成功联网加载过 */
    private var lastConversationsLoadedAtMs = 0L

    init {
        observeWebSocketMessages()
        observeNetworkRecovery()
    }

    fun setTokenRepository(tokenRepository: TokenRepository) {
        conversationRepository.setTokenRepository(tokenRepository)
        userRepository.setTokenRepository(tokenRepository)
    }

    // UI状态
    private val _uiState = MutableStateFlow(ConversationUiState())
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    // 会话列表
    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()
    
    // 置顶会话数据
    private val _stickyData = MutableStateFlow<StickyData?>(null)
    val stickyData: StateFlow<StickyData?> = _stickyData.asStateFlow()
    
    // 置顶会话加载状态（独立于普通会话）
    private val _stickyLoading = MutableStateFlow(false)
    val stickyLoading: StateFlow<Boolean> = _stickyLoading.asStateFlow()
    
    /**
     * 为指定账号初始化会话数据（切换账号时隔离重置；已有缓存时先展示缓存，同时联网静默刷新一次）
     */
    fun initForUser(userId: String, token: String) {
        currentToken = token
        if (currentActiveUserId == userId && _conversations.value.isNotEmpty()) {
            // 同一账号再次进入页面：只在数据较旧时静默刷新，避免频繁切页重复请求网络
            refreshConversationsIfStale(token)
            return
        }
        currentActiveUserId = userId
        
        // 取消旧账号的 Flow 收集
        cacheCollectionJob?.cancel()
        
        // 切换账号时重置置顶会话数据
        _stickyData.value = null
        
        // 绑定新账号对应数据库的 Flow
        cacheCollectionJob = viewModelScope.launch {
            cacheRepository.getCachedConversations(userId).collect { cachedConversations ->
                val blockedSet = blocklistRepository.getBlockedUserIdsSet()
                val filtered = if (blockedSet.isEmpty()) cachedConversations else cachedConversations.filter { conv ->
                    !(conv.chatType == 1 && conv.chatId in blockedSet)
                }
                _conversations.value = filtered
                if (filtered.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            }
        }
        
        // 有网：以服务器数据为准刷新（有本地缓存时先展示缓存，不显示加载圈）；
        // 没网：只用本地缓存，不发注定失败的请求（等网络恢复后会再自动刷新）
        viewModelScope.launch {
            val online = token.isNotBlank() && NetworkUtils.isNetworkAvailable(context)
            if (online) {
                val cached = cacheRepository.getCachedConversationsSync(userId)
                loadConversations(token, isForceOrPullRefresh = cached.isEmpty())
                loadStickyConversations()
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    /**
     * 数据超过 [CONVERSATION_REFRESH_STALE_MS] 未成功联网刷新过时，静默刷新一次会话列表。
     * 没网时直接跳过（只用缓存）；还没初始化账号（首次进入）时跳过，由 initForUser 负责；
     * 最近刚请求过（[CONVERSATION_LOAD_DEDUP_MS] 内）也会跳过，避免重复请求。
     */
    fun refreshConversationsIfStale(token: String) {
        if (token.isBlank()) return
        // 还没完成账号初始化时（首次进入）交给 initForUser 处理，避免重复请求
        if (currentActiveUserId == null) return
        if (!NetworkUtils.isNetworkAvailable(context)) return
        val now = System.currentTimeMillis()
        if (now - lastConversationsLoadStartedAtMs < CONVERSATION_LOAD_DEDUP_MS) return
        val lastLoadedAt = lastConversationsLoadedAtMs
        if (lastLoadedAt != 0L && now - lastLoadedAt < CONVERSATION_REFRESH_STALE_MS) return
        loadConversations(token, isForceOrPullRefresh = _conversations.value.isEmpty())
    }

    /**
     * 监听网络恢复：从「没网」变成「有网」时立刻刷新一次会话列表，
     * 保证「打开软件时没网、之后连上网」也能自动拿到最新数据。
     */
    private fun observeNetworkRecovery() {
        viewModelScope.launch {
            var lastAvailable = NetworkUtils.isNetworkAvailable(context)
            NetworkUtils.networkAvailabilityFlow(context).collect { available ->
                if (available && !lastAvailable) {
                    val token = currentToken
                    if (token.isNotBlank() && currentActiveUserId != null) {
                        Log.d(TAG, "网络已恢复，自动刷新会话列表")
                        loadConversations(token, isForceOrPullRefresh = _conversations.value.isEmpty())
                        loadStickyConversations()
                    }
                }
                lastAvailable = available
            }
        }
    }
    
    /**
     * 加载会话列表（从网络，并写入对应账号的专属数据库）
     */
    fun loadConversations(token: String, isForceOrPullRefresh: Boolean = true) {
        // 记录发起时间，用于去重（短时间重复进入页面不再重复请求）
        lastConversationsLoadStartedAtMs = System.currentTimeMillis()
        viewModelScope.launch {
            if (isForceOrPullRefresh || _conversations.value.isEmpty()) {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            }
            conversationRepository.getConversations()
                .onSuccess { conversationList ->
                    lastConversationsLoadedAtMs = System.currentTimeMillis()
                    val blockedSet = blocklistRepository.getBlockedUserIdsSet()
                    val filtered = if (blockedSet.isEmpty()) conversationList else conversationList.filter { conv ->
                        !(conv.chatType == 1 && conv.chatId in blockedSet)
                    }
                    _conversations.value = filtered
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    // 缓存到当前账号专属本地数据库
                    cacheRepository.cacheConversations(conversationList, currentActiveUserId)
                }
                .onFailure { error ->
                    val cachedConversations = cacheRepository.getCachedConversationsSync(currentActiveUserId)
                    if (cachedConversations.isNotEmpty()) {
                        val blockedSet = blocklistRepository.getBlockedUserIdsSet()
                        val filtered = if (blockedSet.isEmpty()) cachedConversations else cachedConversations.filter { conv ->
                            !(conv.chatType == 1 && conv.chatId in blockedSet)
                        }
                        _conversations.value = filtered
                        _uiState.value = _uiState.value.copy(isLoading = false)
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = error.message
                        )
                    }
                }
        }
    }

    /**
     * 观察WebSocket会话更新
     */
    private fun observeWebSocketMessages() {
        // 监听消息事件 (已移除，避免重复更新)
        
        // 同时监听会话更新事件（这个流专门用于会话列表更新）
        viewModelScope.launch {
            webSocketManager.getConversationUpdates().collect { update ->
                Log.d("ConversationViewModel", "Received ConversationUpdate: ${update::class.simpleName}")
                when (update) {
                    is com.yhchat.canary.data.websocket.ConversationUpdate.NewMessage -> {
                        updateConversationWithNewMessage(update.message)
                    }
                    is com.yhchat.canary.data.websocket.ConversationUpdate.MessageEdited -> {
                        updateConversationWithEditedMessage(update.message)
                    }
                }
            }
        }
    }
    
    /**
     * 用新消息更新会话列表
     */
    private fun updateConversationWithNewMessage(message: com.yhchat.canary.data.model.ChatMessage) {
        viewModelScope.launch {
            val currentConversations = _conversations.value.toMutableList()
            
            // 根据文档说明，只有当recvId和chatId相等时，才创建/更新与发送者的私聊会话
            // 否则，应该更新chatId对应的群聊会话
            val isPrivateChat = message.chatId == message.recvId
            val targetChatId = if (isPrivateChat) message.sender.chatId else message.chatId ?: ""
            val targetChatType = if (isPrivateChat) message.sender.chatType else message.chatType ?: 0
            
            if (blocklistRepository.isUserBlocked(message.sender.chatId) || (isPrivateChat && blocklistRepository.isUserBlocked(targetChatId))) {
                Log.d("ConversationViewModel", "Message from blocked user ignored for conversation: $targetChatId")
                return@launch
            }
            
            val conversationIndex = currentConversations.indexOfFirst { it.chatId == targetChatId }
            
            if (conversationIndex >= 0) {
                // 更新现有会话
                val conversation = currentConversations[conversationIndex]
                val messagePreview = getMessagePreview(message) ?: "[消息]"
                
                val updatedConversation = conversation.copy(
                    chatContent = messagePreview,
                    timestampMs = message.sendTime,
                    unreadMessage = if (conversation.doNotDisturb == 1) {
                        0
                    } else {
                        conversation.unreadMessage + 1
                    }
                )
                currentConversations[conversationIndex] = updatedConversation
                
                // 注意：这里不再调用 cacheRepository.updateConversationLastMessage
                // 因为 WebSocketManager 在收到消息时已经通过 ConversationRepository 更新了数据库
                // 这样避免了未读消息数被重复 +1 的问题。
                
                Log.d("ConversationViewModel", "Updated conversation: chatId=$targetChatId, unread=${updatedConversation.unreadMessage}, preview=$messagePreview")
            } else {
                // 创建新会话 - 只有在私聊或机器人对话时才创建
                if (isPrivateChat) {
                    val newConversation = Conversation(
                        chatId = message.sender.chatId,
                        chatType = message.sender.chatType,
                        name = message.sender.name,
                        chatContent = getMessagePreview(message) ?: "[消息]",
                        timestampMs = message.sendTime,
                        unreadMessage = 1,
                        at = 0,
                        avatarUrl = message.sender.avatarUrl,
                        timestamp = message.sendTime / 1000
                    )
                    currentConversations.add(0, newConversation)
                    
                    // 缓存新会话
                    cacheRepository.cacheConversations(listOf(newConversation), currentActiveUserId)
                } else {
                    // 对于群聊消息，如果会话不存在，我们不创建新会话
                    // 群聊会话应该通过API获取，而不是通过WebSocket消息创建
                    Log.d("ConversationViewModel", "Group conversation not found, skipping creation for chatId: ${message.chatId}")
                }
            }
            
            // 按时间排序
            currentConversations.sortByDescending { it.timestampMs }
            _conversations.value = currentConversations
            
            // 同步更新分页显示的会话列表
            Log.d("ConversationViewModel", "Updated conversation list, total: ${currentConversations.size}")
        }
    }
    
    /**
     * 用编辑的消息更新会话列表
     */
    private fun updateConversationWithEditedMessage(message: com.yhchat.canary.data.model.ChatMessage) {
        val currentConversations = _conversations.value.toMutableList()
        val conversationIndex = currentConversations.indexOfFirst { it.chatId == message.sender.chatId }
        
        if (conversationIndex >= 0) {
            val conversation = currentConversations[conversationIndex]
            val updatedConversation = conversation.copy(
                chatContent = getMessagePreview(message),
                timestampMs = message.sendTime
            )
            currentConversations[conversationIndex] = updatedConversation
            _conversations.value = currentConversations
            
            // 同步更新分页显示的会话列表
        }
    }
    
    /**
     * 获取消息预览文本
     */
    private fun getMessagePreview(message: com.yhchat.canary.data.model.ChatMessage): String {
        // 获取消息内容预览
        val contentPreview = when {
            !message.content.text.isNullOrEmpty() -> message.content.text
            !message.content.imageUrl.isNullOrEmpty() -> "[图片]"
            !message.content.fileUrl.isNullOrEmpty() -> {
                if (!message.content.fileName.isNullOrEmpty()) {
                    "[文件]${message.content.fileName}"
                } else {
                    "[文件]"
                }
            }
            !message.content.audioUrl.isNullOrEmpty() -> "语音消息"
            !message.content.videoUrl.isNullOrEmpty() -> "视频消息"
            !message.content.stickerUrl.isNullOrEmpty() -> "表情消息"
            !message.content.postTitle.isNullOrEmpty() -> "文章消息${message.content.postTitle}"
            else -> "[消息]"
        }
        
        // 确定目标会话类型
        val isPrivateChat = message.chatId == message.recvId
        val targetChatType = if (isPrivateChat) message.sender.chatType else (message.chatType ?: 1)
        
        // 确保返回非空字符串
        val nonNullContentPreview = contentPreview ?: "[消息]"
        
        Log.d("ConversationViewModel", "Message preview - chatType: $targetChatType, sender: ${message.sender.name}, content: $nonNullContentPreview")
        
        // 根据会话类型决定显示格式
        return when (targetChatType) {
            2, 3 -> {
                // 群聊(2)或机器人会话(3)：直接显示内容
                nonNullContentPreview
            }
            else -> {
                // 私聊(1)或其他：直接显示内容
                nonNullContentPreview
            }
        }
    }
    
    /**
     * 启动WebSocket连接
     */
    fun startWebSocket(userId: String) {
        viewModelScope.launch {
            webSocketManager.connect(userId)
        }
    }
    
    /**
     * 停止WebSocket连接
     */
    fun stopWebSocket() {
        webSocketManager.disconnect()
    }
    
    /**
     * 清除错误信息
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    /**
     * 加载置顶会话列表（独立加载，不影响普通会话）
     */
    fun loadStickyConversations() {
        viewModelScope.launch {
            _stickyLoading.value = true
            
            try {
                userRepository.getStickyList()
                    .onSuccess { stickyData ->
                        val blockedSet = blocklistRepository.getBlockedUserIdsSet()
                        val filteredSticky = if (blockedSet.isEmpty()) {
                            stickyData.sticky
                        } else {
                            stickyData.sticky.filter { item ->
                                !(item.chatType == 1 && item.chatId in blockedSet)
                            }
                        }
                        _stickyData.value = stickyData.copy(sticky = filteredSticky)
                        _stickyLoading.value = false
                    }
                    .onFailure { error ->
                        // 置顶会话加载失败不影响普通会话
                        _stickyData.value = null
                        _stickyLoading.value = false
                        // 不设置错误状态，静默失败
                    }
            } catch (e: Exception) {
                _stickyData.value = null
                _stickyLoading.value = false
            }
        }
    }
    
    /**
     * 删除会话
     */
    fun deleteConversation(chatId: String) {
        viewModelScope.launch {
            conversationRepository.removeConversation(chatId)
                .onSuccess {
                    // 从本地列表中移除会话
                    _conversations.value = _conversations.value.filter { it.chatId != chatId }
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(error = error.message)
                }
        }
    }
    
    /**
     * 置顶会话
     */
    fun toggleStickyConversation(conversation: Conversation) {
        viewModelScope.launch {
            // 检查是否已经置顶
            userRepository.getStickyList()
                .onSuccess { stickyData ->
                    val isSticky = stickyData.sticky?.any { it.chatId == conversation.chatId } == true
                    if (isSticky) {
                        // 取消置顶
                        userRepository.deleteSticky(conversation.chatId, conversation.chatType)
                            .onSuccess {
                                // 重新加载置顶列表
                                loadStickyConversations()
                            }
                    } else {
                        // 添加置顶
                        userRepository.addSticky(conversation.chatId, conversation.chatType)
                            .onSuccess {
                                // 重新加载置顶列表
                                loadStickyConversations()
                            }
                    }
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(error = error.message)
                }
        }
    }
    
    /**
     * 检查会话是否已置顶
     */
    suspend fun isConversationSticky(chatId: String): Boolean {
        return try {
            userRepository.getStickyList()
                .getOrNull()?.sticky?.any { it.chatId == chatId } == true
        } catch (e: Exception) {
            false
        }
    }
    
    fun markConversationAsRead(chatId: String) {
        viewModelScope.launch {
            conversationRepository.dismissNotification(chatId)
                .onSuccess {
                    clearConversationUnreadState(chatId)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(error = error.message)
                    Log.e("ConversationViewModel", "Failed to dismiss notification: chatId=$chatId, error=${error.message}")
                }
        }
    }

    private fun clearConversationUnreadState(chatId: String) {
        val updatedConversations = _conversations.value.map { conversation ->
            if (conversation.chatId == chatId) {
                conversation.copy(unreadMessage = 0, at = 0)
            } else {
                conversation
            }
        }

        _conversations.value = updatedConversations
        viewModelScope.launch {
            runCatching {
                cacheRepository.markConversationAsRead(chatId)
            }.onFailure { error ->
                Log.e("ConversationViewModel", "Failed to clear cached unread state: chatId=$chatId", error)
            }
        }
        Log.d("ConversationViewModel", "Cleared unread state from server-backed conversation: chatId=$chatId")
    }

/**
 * 会话UI状态
 */
data class ConversationUiState(
    val isLoading: Boolean = false,
    val error: String? = null
)

}
