package com.yhchat.canary.ui.chat

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.yhchat.canary.data.local.ReadPositionStore
import com.yhchat.canary.data.model.BotLlmParamValue
import com.yhchat.canary.data.model.BotLlmRefParamItem
import com.yhchat.canary.data.model.ChatMessage
import com.yhchat.canary.data.model.GroupDetail
import com.yhchat.canary.data.model.GroupMemberInfo
import com.yhchat.canary.data.model.MsgForwardReceive
import com.yhchat.canary.data.repository.BlocklistRepository
import com.yhchat.canary.data.repository.GroupRepository
import com.yhchat.canary.data.repository.MessageRepository
import com.yhchat.canary.data.repository.SendMessageMedia
import com.yhchat.canary.data.repository.SendMessagePayload
import com.yhchat.canary.data.repository.TokenRepository
import com.yhchat.canary.data.websocket.MessageEvent
import com.yhchat.canary.data.websocket.WebSocketManager
import com.yhchat.canary.proto.bot.board
import com.yhchat.canary.proto.bot.bot_info
import com.yhchat.canary.proto.group.Bot_data
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ChatUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isConnected: Boolean = false,
    val isRefreshing: Boolean = false,
    val newMessageReceived: Boolean = false,  // 标记是否收到新消息
    val groupInfo: GroupDetail? = null,
    val groupMembers: Map<String, GroupMemberInfo> = emptyMap(),  // 群成员信息：chatId -> GroupMemberInfo
    val groupMemberCount: Int = 0,
    val botInfo: bot_info? = null,
    val botBoard: board? = null,  // 机器人看板（单个机器人聊天）
    val groupBots: List<Bot_data> = emptyList(),  // 群聊中的机器人列表
    val groupBotBoards: Map<String, board.Board_data> = emptyMap(),  // 群聊机器人看板：botId -> board_data
    val botLlmRefParams: List<BotLlmRefParamItem> = emptyList(),
    val botLlmParamValues: Map<String, List<BotLlmParamValue>> = emptyMap(),
    val chatBackgroundUrl: String? = null,  // 聊天背景图片URL
    val menuButtons: List<com.yhchat.canary.data.model.MenuButton> = emptyList(),  // 群聊菜单按钮
    val uploadProgress: Float? = null, // 上传进度 (0.0 - 1.0)
    val scrollToMsgId: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val messageRepository: MessageRepository,
    private val tokenRepository: TokenRepository,
    private val webSocketManager: WebSocketManager,
    private val groupRepository: GroupRepository,
    private val readPositionStore: ReadPositionStore,
    private val apiService: com.yhchat.canary.data.api.ApiService,
    private val botRepository: com.yhchat.canary.data.repository.BotRepository,
    private val draftStore: com.yhchat.canary.data.local.DraftStore
) : ViewModel() {
    
    private val blocklistRepository = BlocklistRepository(context)
    private val gson = Gson()
    private val llmParamsPrefs = context.getSharedPreferences("bot_llm_params_cache", Context.MODE_PRIVATE)

    private var currentChatId: String = ""
    private var currentChatType: Int = 1
    private var currentUserId: String = ""
    private var hasMoreMessages: Boolean = true
    private var oldestMsgSeq: Long = 0
    private var oldestMsgId: String? = null
    private var webSocketCollectorJob: kotlinx.coroutines.Job? = null

    private val tag = "ChatViewModel"
    
    // 常量
    private companion object {
        const val DEFAULT_MSG_COUNT = 20
        const val DEFAULT_LOAD_MEMBERS_COUNT = 100
        const val MEMBERS_PER_PAGE = 50
    }

    suspend fun forwardMessage(
        msgId: String,
        sourceChatType: Int,
        receive: List<MsgForwardReceive>
    ): Result<Boolean> {
        return messageRepository.forwardMessage(
            msgId = msgId,
            sourceChatType = sourceChatType,
            receive = receive
        )
    }

    private var lastTextSendAtMs: Long = 0L
    private val textSendDebounceWindowMs: Long = 800L

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _messages = mutableStateListOf<ChatMessage>()
    val messages: List<ChatMessage> = _messages
    
    // 长消息折叠状态
    private val _collapsedMessageIds = mutableStateListOf<String>()
    val collapsedMessageIds: SnapshotStateList<String> = _collapsedMessageIds
    
    fun isMessageCollapsed(msgId: String): Boolean = _collapsedMessageIds.contains(msgId)
    
    fun toggleMessageCollapse(msgId: String) {
        if (_collapsedMessageIds.contains(msgId)) {
            _collapsedMessageIds.remove(msgId)
        } else {
            _collapsedMessageIds.add(msgId)
        }
    }

    private val collapsibleContentTypes = setOf(1, 3, 8)

    private val chatPrefs = context.getSharedPreferences("chat_settings", Context.MODE_PRIVATE)
    private var autoCollapseLongMessages = chatPrefs.getBoolean("auto_collapse_long_messages", true)
    private val chatPrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == "auto_collapse_long_messages") {
            autoCollapseLongMessages = prefs.getBoolean(key, true)
            if (autoCollapseLongMessages) {
                handleAutoCollapseForMessages(_messages)
            } else {
                _collapsedMessageIds.clear()
            }
        }
    }

    private fun shouldAutoCollapseMessage(message: ChatMessage): Boolean {
        if (!autoCollapseLongMessages) return false
        val text = message.content.text ?: return false
        val collapseThreshold = if (message.contentType == 8) 1500 else 800
        if (text.length < collapseThreshold) return false
        return collapsibleContentTypes.contains(message.contentType)
    }

    private fun handleAutoCollapseForMessages(messages: List<ChatMessage>) {
        if (!autoCollapseLongMessages) return
        for (msg in messages) {
            if (shouldAutoCollapseMessage(msg) && !_collapsedMessageIds.contains(msg.msgId)) {
                _collapsedMessageIds.add(msg.msgId)
            }
        }
    }
    
    // 流式消息缓存：msgId -> 累积的content
    private val streamingMessages = mutableMapOf<String, String>()
    private val streamingMessageSenders = mutableMapOf<String, com.yhchat.canary.data.model.MessageSender>()
    private val streamingMessageContentTypes = mutableMapOf<String, Int>()
    
    // 远程草稿输入（多端同步）
    private val _remoteDraftInput = MutableStateFlow<String?>(null)
    val remoteDraftInput: StateFlow<String?> = _remoteDraftInput.asStateFlow()
    
    // 上次发送草稿的时间戳（用于防止自己的草稿被立即应用回来）
    private var lastDraftSentTime = 0L
    
    init {
        messageRepository.setBotLlmParamsProvider { chatId, chatType ->
            if (chatId == currentChatId && chatType == currentChatType) {
                getBotLlmParamsForSending()
            } else {
                null
            }
        }
        chatPrefs.registerOnSharedPreferenceChangeListener(chatPrefsListener)
        // 监听多端草稿同步
        viewModelScope.launch {
            webSocketManager.getDraftUpdates().collect { draftUpdate ->
                handleDraftUpdate(draftUpdate)
            }
        }
    }

    /**
     * 处理草稿更新（多端同步）
     */
    private fun handleDraftUpdate(draftUpdate: com.yhchat.canary.data.websocket.DraftUpdate) {
        Log.d(tag, "📝 处理草稿更新: chatId=${draftUpdate.chatId}, currentChatId=$currentChatId, input='${draftUpdate.input.take(50)}${if (draftUpdate.input.length > 50) "..." else ""}'")
        
        // 如果草稿时间戳距离上次发送草稿时间小于1秒，可能是自己发送的，跳过
        val timeSinceLastSent = System.currentTimeMillis() - lastDraftSentTime
        if (timeSinceLastSent < 1000) {
            Log.d(tag, "⏭️ 跳过草稿更新（可能是自己发送的，时间差${timeSinceLastSent}ms）")
            return
        }
        
        // 只判断chatId就够了
        if (draftUpdate.chatId == currentChatId) {
            // 当前正在该聊天界面，直接更新输入框
            Log.d(tag, "✅ 当前在该聊天界面 (chatId匹配)，通知UI更新输入框")
            _remoteDraftInput.value = draftUpdate.input
        } else {
            // 不在该聊天界面，保存草稿到本地
            Log.d(tag, "💾 不在该聊天界面 (chatId=$currentChatId != ${draftUpdate.chatId})，保存草稿到本地")
            draftStore.saveDraft(draftUpdate.chatId, currentChatType, draftUpdate.input)
        }
    }
    
    /**
     * 清除远程草稿输入状态
     */
    fun clearRemoteDraftInput() {
        _remoteDraftInput.value = null
    }
    
    /**
     * 初始化聊天
     */
    fun initChat(chatId: String, chatType: Int, userId: String) {
        currentChatId = chatId
        currentChatType = chatType
        currentUserId = userId
        
        Log.d(tag, "Initializing chat: chatId=$chatId, chatType=$chatType, userId=$userId")
        
        // 清空之前的消息
        _messages.clear()
        _collapsedMessageIds.clear()
        hasMoreMessages = true
        oldestMsgSeq = 0
        oldestMsgId = null
        
        // 清除远程草稿输入状态（切换聊天时）
        _remoteDraftInput.value = null
        
        // 清除该会话的通知历史
        webSocketManager.clearNotificationHistory(chatId, chatType)

        loadBotLlmRefParams(chatId, chatType)
        
        // 如果是群聊，加载群成员信息、机器人列表和菜单按钮
        if (chatType == 2) { // 群聊
            loadGroupInfo(chatId)
            loadGroupBotsAndBoards(chatId)
            loadGroupMenuButtons(chatId)
        } else if (chatType == 3) { // 机器人
            loadBotInfo(chatId)
            loadBotBoard(chatId, 3)
        }
        
        // 开始监听WebSocket消息
        startListeningToWebSocketMessages()
        
        // 加载最新消息
        Log.d(tag, "Loading latest messages")
        loadMessages()
    }

    private fun llmParamsPrefKey(chatId: String, chatType: Int): String = "${chatType}_$chatId"

    private fun loadCachedBotLlmParams(chatId: String, chatType: Int): List<BotLlmParamValue> {
        val key = llmParamsPrefKey(chatId, chatType)
        val raw = llmParamsPrefs.getString(key, null) ?: return emptyList()
        return runCatching {
            val type = object : TypeToken<List<BotLlmParamValue>>() {}.type
            gson.fromJson<List<BotLlmParamValue>>(raw, type).orEmpty()
        }.getOrElse { emptyList() }
    }

    private fun saveCachedBotLlmParams(chatId: String, chatType: Int, values: List<BotLlmParamValue>) {
        val key = llmParamsPrefKey(chatId, chatType)
        llmParamsPrefs.edit().putString(key, gson.toJson(values)).apply()
    }

    private fun parseBotLlmParamValues(botId: String, paramJson: String): List<BotLlmParamValue> {
        if (paramJson.isBlank()) return emptyList()
        return runCatching {
            val mapType = object : TypeToken<List<Map<String, Any?>>>() {}.type
            val rawList = gson.fromJson<List<Map<String, Any?>>>(paramJson, mapType).orEmpty()
            rawList.mapIndexed { index, item ->
                val id = (item["id"] as? String).orEmpty().ifBlank { "param_$index" }
                val name = (item["name"] as? String).orEmpty().ifBlank { id }
                val label = (item["label"] as? String).orEmpty().ifBlank { name }
                val type = (item["type"] as? String).orEmpty().ifBlank { "input" }
                val options = (item["options"] as? String).orEmpty()
                BotLlmParamValue(
                    id = id,
                    name = name,
                    label = label,
                    type = type,
                    options = options,
                    value = null,
                    selectValue = null,
                    botId = botId
                )
            }
        }.getOrElse { emptyList() }
    }

    private fun loadBotLlmRefParams(chatId: String, chatType: Int) {
        viewModelScope.launch {
            botRepository.getBotLlmRefParams(chatId, chatType).fold(
                onSuccess = { list ->
                    if (list.isEmpty()) {
                        _uiState.value = _uiState.value.copy(
                            botLlmRefParams = emptyList(),
                            botLlmParamValues = emptyMap()
                        )
                        return@fold
                    }

                    val cachedByBot = loadCachedBotLlmParams(chatId, chatType).groupBy { it.botId }
                    val merged = list.associate { item ->
                        val parsed = parseBotLlmParamValues(item.botId, item.paramJson)
                        val cachedById = cachedByBot[item.botId].orEmpty().associateBy { it.id }
                        val values = parsed.map { field ->
                            val cached = cachedById[field.id]
                            if (field.type.equals("select", ignoreCase = true)) {
                                field.copy(selectValue = cached?.selectValue)
                            } else {
                                field.copy(value = cached?.value)
                            }
                        }
                        item.botId to values
                    }

                    _uiState.value = _uiState.value.copy(
                        botLlmRefParams = list,
                        botLlmParamValues = merged
                    )
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(
                        botLlmRefParams = emptyList(),
                        botLlmParamValues = emptyMap()
                    )
                }
            )
        }
    }

    private fun persistCurrentBotLlmParams() {
        if (currentChatId.isBlank()) return
        val flattened = _uiState.value.botLlmParamValues.values.flatten()
        saveCachedBotLlmParams(currentChatId, currentChatType, flattened)
    }

    fun updateBotLlmInputValue(botId: String, paramId: String, value: String) {
        val currentMap = _uiState.value.botLlmParamValues
        val updated = currentMap[botId].orEmpty().map { item ->
            if (item.id == paramId) item.copy(value = value) else item
        }
        _uiState.value = _uiState.value.copy(
            botLlmParamValues = currentMap.toMutableMap().apply { put(botId, updated) }
        )
        persistCurrentBotLlmParams()
    }

    fun updateBotLlmSelectValue(botId: String, paramId: String, value: String) {
        val currentMap = _uiState.value.botLlmParamValues
        val updated = currentMap[botId].orEmpty().map { item ->
            if (item.id == paramId) item.copy(selectValue = value) else item
        }
        _uiState.value = _uiState.value.copy(
            botLlmParamValues = currentMap.toMutableMap().apply { put(botId, updated) }
        )
        persistCurrentBotLlmParams()
    }

    private fun getBotLlmParamsForSending(): String? {
        if (_uiState.value.botLlmRefParams.isEmpty()) return null
        val filled = _uiState.value.botLlmParamValues.values
            .flatten()
            .mapNotNull { item ->
                if (item.type.equals("select", ignoreCase = true)) {
                    item.selectValue?.takeIf { it.isNotBlank() }?.let {
                        item.copy(selectValue = it, value = null)
                    }
                } else {
                    item.value?.takeIf { it.isNotBlank() }?.let {
                        item.copy(value = it, selectValue = null)
                    }
                }
            }
        return if (filled.isEmpty()) null else gson.toJson(filled)
    }

    /**
     * 加载群聊基本信息
     */
    private fun loadGroupInfo(groupId: String) {
        viewModelScope.launch {
            Log.d(tag, "Loading group info for: $groupId")
            groupRepository.setTokenRepository(tokenRepository)
            
            groupRepository.getGroupInfo(groupId).fold(
                onSuccess = { groupDetail ->
                    Log.d(tag, "✅ Group info loaded successfully")
                    _uiState.value = _uiState.value.copy(
                        groupInfo = groupDetail,
                        groupMemberCount = groupDetail.memberCount,
                        groupMembers = emptyMap()
                    )
                },
                onFailure = { error ->
                    Log.e(tag, "❌ Failed to load group info", error)
                    _uiState.value = _uiState.value.copy(error = "加载群信息失败: ${error.message}")
                }
            )
        }
    }

    /**
     * 加载群成员信息（加载群信息和部分成员以显示权限标签）
     */
    private fun loadGroupMembers(groupId: String) {
        viewModelScope.launch {
            Log.d(tag, "Loading group info and members for: $groupId")
            groupRepository.setTokenRepository(tokenRepository)
            
            // 加载群信息以获取成员总数
            groupRepository.getGroupInfo(groupId).fold(
                onSuccess = { groupInfo ->
                    Log.d(tag, "Group info loaded, member count: ${groupInfo.memberCount}")
                    
                    // 只加载前100人用于显示权限标签，避免加载所有成员
                    val membersToLoad = minOf(DEFAULT_LOAD_MEMBERS_COUNT, groupInfo.memberCount)
                    val pages = (membersToLoad + MEMBERS_PER_PAGE - 1) / MEMBERS_PER_PAGE  // 向上取整
                    val allMembers = mutableListOf<GroupMemberInfo>()
                    
                    for (page in 1..pages) {
                        groupRepository.getGroupMembers(groupId, page = page, size = MEMBERS_PER_PAGE).fold(
                            onSuccess = { members ->
                                allMembers.addAll(members)
                                Log.d(tag, "Loaded page $page: ${members.size} members, total: ${allMembers.size}")
                            },
                            onFailure = { error ->
                                Log.e(tag, "Failed to load group members page $page", error)
                            }
                        )
                    }
                    
                    // 转换为 Map: userId -> GroupMemberInfo
                    val membersMap: Map<String, GroupMemberInfo> = allMembers.associateBy { it.userId }
                    
                    _uiState.value = _uiState.value.copy(
                        groupMembers = membersMap,
                        groupMemberCount = groupInfo.memberCount
                    )
                    Log.d(tag, "Group members loaded: ${membersMap.size} members (for permission display)")
                },
                onFailure = { error ->
                    Log.e(tag, "Failed to load group info", error)
                }
            )
        }
    }
    
    /**
     * 加载群聊中的机器人列表和他们的看板
     */
    private fun loadGroupBotsAndBoards(chatId: String) {
        viewModelScope.launch {
            groupRepository.getGroupBots(chatId).fold(
                onSuccess = { bots ->
                    _uiState.value = _uiState.value.copy(
                        groupBots = bots,
                        groupBotBoards = emptyMap()
                    )
                    if (bots.isNotEmpty()) {
                        // 只需要调用一次API，获取所有机器人的看板
                        loadGroupBotBoards(chatId)
                    }
                },
                onFailure = { error ->
                    Log.e(tag, "加载群聊机器人列表失败", error)
                }
            )
        }
    }

    /**
     * 加载群聊内所有机器人的看板
     * @param chatId 群聊ID
     */
    private fun loadGroupBotBoards(chatId: String) {
        viewModelScope.launch {
            Log.d(tag, "开始加载群聊机器人看板: groupId=$chatId")
            // 使用chatType=2获取群聊的看板
            botRepository.getBotBoard(chatId, 2).fold(
                onSuccess = { boardResponse ->
                    val boardsDataList = boardResponse.boardList
                    Log.d(tag, "✅ 加载群聊看板成功: groupId=$chatId, 数量=${boardsDataList.size}")
                    val boardsMap = boardsDataList.associateBy { it.botId }
                    _uiState.value = _uiState.value.copy(groupBotBoards = boardsMap)
                },
                onFailure = { error ->
                    Log.e(tag, "加载群聊机器人看板失败", error)
                }
            )
        }
    }

    /**
     * 刷新机器人看板
     */
    fun refreshBotBoard(chatId: String, chatType: Int) {
        if (chatType == 2) {
            loadGroupBotBoards(chatId)
        } else if (chatType == 3) {
            loadBotBoard(chatId, 3)
        }
    }

    /**
     * 加载群菜单按钮
     */
    fun loadGroupMenuButtons(chatId: String) {
        viewModelScope.launch {
            groupRepository.getGroupMenuButtons(chatId).fold(
                onSuccess = { buttons ->
                    _uiState.value = _uiState.value.copy(menuButtons = buttons)
                },
                onFailure = { error ->
                    Log.e(tag, "加载群聊菜单按钮失败", error)
                }
            )
        }
    }

    /**
     * 点击群菜单按钮
     */
    fun clickMenuButton(button: com.yhchat.canary.data.model.MenuButton) {
        viewModelScope.launch {
            groupRepository.clickMenuButton(
                buttonId = button.id,
                chatId = currentChatId,
                chatType = currentChatType,
                value = button.content
            ).fold(
                onSuccess = {
                    Log.d(tag, "点击菜单按钮成功: ${button.id}")
                },
                onFailure = { error ->
                    Log.e(tag, "点击菜单按钮失败", error)
                }
            )
        }
    }

    /**
     * 开始监听WebSocket消息
     */
    private fun startListeningToWebSocketMessages() {
        webSocketCollectorJob?.cancel()
        webSocketCollectorJob = viewModelScope.launch {
            webSocketManager.getMessageEvents().collect { event ->
                when (event) {
                    is MessageEvent.NewMessage -> {
                        handleNewMessage(event.message)
                    }
                    is MessageEvent.MessageEdited -> {
                        handleEditedMessage(event.message)
                    }
                    is MessageEvent.MessageDeleted -> {
                        handleDeletedMessage(event.msgId)
                    }
                    is MessageEvent.StreamMessage -> {
                        handleStreamMessage(event)
                    }
                    else -> {
                        // 忽略其他事件类型
                    }
                }
            }
        }
    }

    /**
     * 处理新消息
     */
    private suspend fun handleNewMessage(message: ChatMessage) {
        val isBlocked = kotlin.runCatching {
            blocklistRepository.isUserBlocked(message.sender.chatId)
        }.getOrElse { false }
        if (isBlocked) {
            Log.d(tag, "Message from blocked user ${message.sender.chatId}, ignored in real-time")
            return
        }

        val normalizedMessage = normalizeMessageOwnership(message)
        val targetChatId = resolveMessageChatId(normalizedMessage)

        if (normalizedMessage.msgId.isNotBlank()) {
            streamingMessages.putIfAbsent(
                normalizedMessage.msgId,
                normalizedMessage.content.text ?: ""
            )
            streamingMessageSenders.putIfAbsent(normalizedMessage.msgId, normalizedMessage.sender)
            streamingMessageContentTypes[normalizedMessage.msgId] = normalizedMessage.contentType
            
            if (normalizedMessage.sender.chatType == 3) {
                Log.d(tag, "Initialized streaming cache for bot message: ${normalizedMessage.msgId}")
            }
        }
        
        if (targetChatId == currentChatId) {
            // 检查消息是否已存在，避免重复添加
            val existingIndex = _messages.indexOfFirst { it.msgId == normalizedMessage.msgId }
            if (existingIndex == -1) {
                // 按时间排序插入新消息
                val insertIndex = _messages.indexOfLast { it.sendTime <= normalizedMessage.sendTime } + 1
                _messages.add(insertIndex, normalizedMessage)
                Log.d(tag, "Inserted new real-time message at index $insertIndex: ${normalizedMessage.msgId}")
                
                handleAutoCollapseForMessages(listOf(normalizedMessage))
                
                // 标记收到新消息，触发UI更新
                _uiState.value = _uiState.value.copy(newMessageReceived = true)
                
                // 初始化流式消息缓存（如果是机器人消息，准备接收stream_message）
                if (normalizedMessage.sender.chatType == 3) {
                    streamingMessages[normalizedMessage.msgId] = normalizedMessage.content.text ?: ""
                    streamingMessageSenders[normalizedMessage.msgId] = normalizedMessage.sender
                    streamingMessageContentTypes[normalizedMessage.msgId] = normalizedMessage.contentType
                    Log.d(tag, "Initialized streaming cache for bot message: ${normalizedMessage.msgId}")
                }
            }
        }
    }

    /**
     * 处理撤回消息
     */
    private fun handleRecalledMessage(msgId: String) {
        val existingIndex = _messages.indexOfFirst { it.msgId == msgId }
        if (existingIndex != -1) {
            val message = _messages[existingIndex]
            _messages[existingIndex] = message.copy(msgDeleteTime = System.currentTimeMillis())
            Log.d(tag, "Marked recalled message in current list: $msgId")
        }
        streamingMessages.remove(msgId)
        streamingMessageSenders.remove(msgId)
        streamingMessageContentTypes.remove(msgId)
    }

    /**
     * 重置新消息标记
     */
    fun resetNewMessageFlag() {
        _uiState.value = _uiState.value.copy(newMessageReceived = false)
    }

    /**
     * 获取消息编辑历史
     */
    suspend fun getMessageEditHistory(msgId: String): Result<List<com.yhchat.canary.data.model.MessageEditRecord>> {
        return messageRepository.getMessageEditHistory(msgId)
    }

    /**
     * 添加表情到个人收藏
     */
    fun addExpressionToFavorites(expressionId: String) {
        viewModelScope.launch {
            try {
                val result = messageRepository.addExpressionToFavorites(expressionId)
                result.fold(
                    onSuccess = {
                        Log.d(tag, "添加表情成功: $expressionId")
                    },
                    onFailure = { error ->
                        Log.e(tag, "添加表情失败: ${error.message}")
                        _uiState.value = _uiState.value.copy(error = "添加表情失败: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                Log.e(tag, "添加表情异常", e)
                _uiState.value = _uiState.value.copy(error = "添加表情异常: ${e.message}")
            }
        }
    }

    /**
     * 获取用户Token（辅助方法）
     */
    private suspend fun getUserToken(): String? {
        val token = tokenRepository.getTokenSync()
        if (token.isNullOrEmpty()) {
            Log.e(tag, "Token为空")
            _uiState.value = _uiState.value.copy(error = "未登录")
        }
        return token
    }

    /**
     * 获取七牛上传Token的通用方法
     */
    private suspend fun getQiniuUploadToken(
        tokenType: String,
        getTokenApi: suspend (String) -> retrofit2.Response<com.yhchat.canary.data.api.QiniuTokenResponse>
    ): String? {
        val userToken = getUserToken() ?: return null

        Log.d(tag, "获取七牛${tokenType}上传token...")
        val tokenResponse = getTokenApi(userToken)

        if (!tokenResponse.isSuccessful || tokenResponse.body()?.code != 1) {
            Log.e(tag, "获取${tokenType}上传token失败: ${tokenResponse.code()}")
            _uiState.value = _uiState.value.copy(error = "获取${tokenType}上传token失败")
            return null
        }

        val uploadToken = tokenResponse.body()?.data?.token
        if (uploadToken.isNullOrEmpty()) {
            Log.e(tag, "${tokenType}上传token为空")
            _uiState.value = _uiState.value.copy(error = "获取${tokenType}上传token失败")
            return null
        }

        Log.d(tag, "获取到${tokenType}上传token: ${uploadToken.take(20)}...")
        return uploadToken
    }

    private var uploadJob: Job? = null

    /**
     * 取消当前附件上传
     */
    fun cancelUpload() {
        uploadJob?.cancel()
        uploadJob = null
        _uiState.value = _uiState.value.copy(uploadProgress = null)
    }

    /**
     * 上传并发送图片
     */
    fun uploadAndSendImage(
        context: android.content.Context,
        imageUri: android.net.Uri,
        quoteMsgId: String? = null,
        quoteMsgText: String? = null,
        quoteImageUrl: String? = null,
        quoteImageName: String? = null,
        quoteVideoUrl: String? = null,
        quoteVideoTime: Long? = null
    ) {
        uploadJob?.cancel()
        uploadJob = viewModelScope.launch {
            try {
                Log.d(tag, "开始上传并发送图片: $imageUri")
                _uiState.value = _uiState.value.copy(uploadProgress = 0f)

                val uploadToken = getQiniuUploadToken("图片") {
                    apiService.getQiniuImageToken(it)
                } ?: return@launch

                Log.d(tag, "开始上传图片到七牛云...")
                val uploadResult = com.yhchat.canary.utils.ImageUploadUtil.uploadImage(
                    context = context,
                    imageUri = imageUri,
                    uploadToken = uploadToken,
                    onProgress = { progress: Float ->
                        _uiState.value = _uiState.value.copy(uploadProgress = progress)
                    }
                )

                uploadResult.fold(
                    onSuccess = { uploadResponse ->
                        Log.d(tag, "图片上传成功！")
                        Log.d(tag, "   key: ${uploadResponse.key}")
                        Log.d(tag, "   hash: ${uploadResponse.hash}")
                        Log.d(tag, "   size: ${uploadResponse.fsize}")

                        val width = uploadResponse.avinfo?.video?.width ?: 1080
                        val height = uploadResponse.avinfo?.video?.height ?: 1920
                        val imageSuffix = uploadResponse.key.substringAfterLast('.', "jpg").lowercase()
                        val imageMime = when (imageSuffix) {
                            "png" -> "image/png"
                            "gif" -> "image/gif"
                            "webp" -> "image/webp"
                            else -> "image/jpeg"
                        }

                        Log.d(tag, "发送图片消息...")
                        val sendResult = messageRepository.sendMessage(
                            chatId = currentChatId,
                            chatType = currentChatType,
                            payload = SendMessagePayload(
                                contentType = 2,
                                imageKey = uploadResponse.key,
                                fileSize = uploadResponse.fsize,
                                quoteMsgId = quoteMsgId,
                                quoteMsgText = quoteMsgText,
                                quoteImageUrl = quoteImageUrl,
                                quoteImageName = quoteImageName,
                                quoteVideoUrl = quoteVideoUrl,
                                quoteVideoTime = quoteVideoTime,
                                media = SendMessageMedia(
                                    fileKey = uploadResponse.key,
                                    fileType = imageMime,
                                    fileSize = uploadResponse.fsize,
                                    fileSuffix = imageSuffix,
                                    imageWidth = width.toLong(),
                                    imageHeight = height.toLong()
                                )
                            )
                        )

                        sendResult.fold(
                            onSuccess = {
                                Log.d(tag, "图片消息发送成功！")
                                loadMessages(refresh = true)
                            },
                            onFailure = { error ->
                                Log.e(tag, "发送图片消息失败", error)
                                _uiState.value = _uiState.value.copy(error = "发送图片失败: ${error.message}")
                            }
                        )
                    },
                    onFailure = { error ->
                        Log.e(tag, "上传图片失败", error)
                        _uiState.value = _uiState.value.copy(error = "上传图片失败: ${error.message}")
                    }
                )

            } catch (e: CancellationException) {
                Log.d(tag, "上传图片已取消")
            } catch (e: Exception) {
                Log.e(tag, "上传并发送图片异常", e)
                _uiState.value = _uiState.value.copy(error = "发送图片失败: ${e.message}")
            } finally {
                _uiState.value = _uiState.value.copy(uploadProgress = null)
                uploadJob = null
            }
        }
    }

    /**
     * 上传并发送视频
     */
    fun uploadAndSendVideo(
        context: android.content.Context,
        videoUri: android.net.Uri,
        quoteMsgId: String? = null,
        quoteMsgText: String? = null,
        quoteImageUrl: String? = null,
        quoteImageName: String? = null,
        quoteVideoUrl: String? = null,
        quoteVideoTime: Long? = null
    ) {
        uploadJob?.cancel()
        uploadJob = viewModelScope.launch {
            try {
                Log.d(tag, "开始上传并发送视频: $videoUri")
                _uiState.value = _uiState.value.copy(uploadProgress = 0f)

                val uploadToken = getQiniuUploadToken("视频") {
                    apiService.getQiniuVideoToken(it)
                } ?: return@launch

                Log.d(tag, "开始上传视频到七牛云...")
                val uploadResult = com.yhchat.canary.utils.VideoUploadUtil.uploadVideo(
                    context = context,
                    videoUri = videoUri,
                    uploadToken = uploadToken,
                    onProgress = { progress: Float ->
                        _uiState.value = _uiState.value.copy(uploadProgress = progress)
                    }
                )

                uploadResult.fold(
                    onSuccess = { uploadResponse ->
                        Log.d(tag, "视频上传成功！")
                        Log.d(tag, "   key: ${uploadResponse.key}")
                        Log.d(tag, "   hash: ${uploadResponse.hash}")
                        Log.d(tag, "   size: ${uploadResponse.fsize}")

                        val videoSuffix = uploadResponse.key.substringAfterLast('.', "mp4").lowercase()
                        val sendResult = messageRepository.sendMessage(
                            chatId = currentChatId,
                            chatType = currentChatType,
                            payload = SendMessagePayload(
                                contentType = 10,
                                videoKey = uploadResponse.key,
                                quoteMsgId = quoteMsgId,
                                quoteMsgText = quoteMsgText,
                                quoteImageUrl = quoteImageUrl,
                                quoteImageName = quoteImageName,
                                quoteVideoUrl = quoteVideoUrl,
                                quoteVideoTime = quoteVideoTime,
                                media = SendMessageMedia(
                                    fileKey = uploadResponse.key,
                                    fileType = "video/mp4",
                                    fileHash = uploadResponse.hash,
                                    fileSize = uploadResponse.fsize,
                                    fileSuffix = videoSuffix
                                )
                            )
                        )

                        sendResult.fold(
                            onSuccess = {
                                Log.d(tag, "视频消息发送成功！")
                                loadMessages(refresh = true)
                            },
                            onFailure = { error ->
                                Log.e(tag, "发送视频消息失败", error)
                                _uiState.value = _uiState.value.copy(error = "发送视频失败: ${error.message}")
                            }
                        )
                    },
                    onFailure = { error ->
                        Log.e(tag, "上传视频失败", error)
                        _uiState.value = _uiState.value.copy(error = "上传视频失败: ${error.message}")
                    }
                )

            } catch (e: CancellationException) {
                Log.d(tag, "上传视频已取消")
            } catch (e: Exception) {
                Log.e(tag, "上传并发送视频异常", e)
                _uiState.value = _uiState.value.copy(error = "发送视频失败: ${e.message}")
            } finally {
                _uiState.value = _uiState.value.copy(uploadProgress = null)
                uploadJob = null
            }
        }
    }

    /**
     * 上传并发送文件
     */
    fun uploadAndSendFile(
        context: android.content.Context,
        fileUri: android.net.Uri,
        quoteMsgId: String? = null,
        quoteMsgText: String? = null,
        quoteImageUrl: String? = null,
        quoteImageName: String? = null,
        quoteVideoUrl: String? = null,
        quoteVideoTime: Long? = null
    ) {
        uploadJob?.cancel()
        uploadJob = viewModelScope.launch {
            try {
                Log.d(tag, "开始上传并发送文件")
                Log.d(tag, "文件URI: $fileUri")
                Log.d(tag, "当前chatId: $currentChatId, chatType: $currentChatType")
                _uiState.value = _uiState.value.copy(uploadProgress = 0f)

                val uploadToken = getQiniuUploadToken("文件") {
                    apiService.getQiniuFileToken(it)
                } ?: return@launch

                Log.d(tag, "开始上传文件到七牛云...")
                val uploadResult = com.yhchat.canary.utils.FileUploadUtil.uploadFile(
                    context = context,
                    fileUri = fileUri,
                    uploadToken = uploadToken,
                    onProgress = { progress: Float ->
                        _uiState.value = _uiState.value.copy(uploadProgress = progress)
                    }
                )

                uploadResult.fold(
                    onSuccess = { uploadResponse ->
                        Log.d(tag, "文件上传成功！")
                        Log.d(tag, "   key: ${uploadResponse.key}")
                        Log.d(tag, "   hash (etag): ${uploadResponse.hash}")
                        Log.d(tag, "   size: ${uploadResponse.fsize} bytes")

                        val fileName = getFileNameFromUri(context, fileUri) ?: "未知文件"
                        Log.d(tag, "原始文件名: $fileName")

                        val fileMd5 = uploadResponse.key.substringAfter("disk/").substringBefore(".")
                        Log.d(tag, "文件MD5: $fileMd5")

                        val fileKey = uploadResponse.key

                        Log.d(tag, "发送文件消息...")
                        Log.d(tag, "   fileName: $fileName")
                        Log.d(tag, "   fileKey: $fileKey")
                        Log.d(tag, "   fileSize: ${uploadResponse.fsize}")

                        val sendResult = messageRepository.sendMessage(
                            chatId = currentChatId,
                            chatType = currentChatType,
                            payload = SendMessagePayload(
                                contentType = 4,
                                fileName = fileName,
                                fileKey = fileKey,
                                fileSize = uploadResponse.fsize,
                                quoteMsgId = quoteMsgId,
                                quoteMsgText = quoteMsgText,
                                quoteImageUrl = quoteImageUrl,
                                quoteImageName = quoteImageName,
                                quoteVideoUrl = quoteVideoUrl,
                                quoteVideoTime = quoteVideoTime
                            )
                        )

                        sendResult.fold(
                            onSuccess = {
                                Log.d(tag, "文件消息发送成功！")
                                loadMessages(refresh = true)
                            },
                            onFailure = { error ->
                                Log.e(tag, "发送文件消息失败", error)
                                _uiState.value = _uiState.value.copy(error = "发送文件失败: ${error.message}")
                            }
                        )
                    },
                    onFailure = { error ->
                        Log.e(tag, "上传文件失败", error)
                        _uiState.value = _uiState.value.copy(error = "上传文件失败: ${error.message}")
                    }
                )

            } catch (e: CancellationException) {
                Log.d(tag, "上传文件已取消")
            } catch (e: Exception) {
                Log.e(tag, "上传并发送文件异常", e)
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(error = "发送文件失败: ${e.message}")
            } finally {
                _uiState.value = _uiState.value.copy(uploadProgress = null)
                uploadJob = null
            }
        }
    }

    /**
     * 上传并发送音频（用于TTS合成的音频）
     */
    fun uploadAndSendAudio(
        context: android.content.Context,
        audioUri: android.net.Uri,
        quoteMsgId: String? = null,
        quoteMsgText: String? = null,
        quoteImageUrl: String? = null,
        quoteImageName: String? = null,
        quoteVideoUrl: String? = null,
        quoteVideoTime: Long? = null
    ) {
        uploadJob?.cancel()
        uploadJob = viewModelScope.launch {
            try {
                Log.d(tag, "开始上传并发送音频")
                Log.d(tag, "音频URI: $audioUri")
                Log.d(tag, "当前chatId: $currentChatId, chatType: $currentChatType")
                _uiState.value = _uiState.value.copy(uploadProgress = 0f)

                val uploadToken = getQiniuUploadToken("音频") {
                    apiService.getQiniuAudioToken(it)
                } ?: return@launch

                val audioFile: File = getFileFromUri(context, audioUri) ?: run {
                    Log.e(tag, "无法读取音频文件")
                    _uiState.value = _uiState.value.copy(error = "无法读取音频文件")
                    return@launch
                }

                val accessKey = uploadToken.split(":").firstOrNull()
                if (accessKey.isNullOrEmpty()) {
                    Log.e(tag, "上传凭证格式错误")
                    _uiState.value = _uiState.value.copy(error = "上传凭证格式错误")
                    audioFile.delete()
                    return@launch
                }

                val uploadDomain = com.yhchat.canary.utils.AudioUtils.getQiniuUploadDomain(accessKey)
                if (uploadDomain.isNullOrEmpty()) {
                    Log.e(tag, "获取上传域名失败")
                    _uiState.value = _uiState.value.copy(error = "获取上传域名失败")
                    audioFile.delete()
                    return@launch
                }

                Log.d(tag, "上传域名: $uploadDomain")

                val uploadResponse = com.yhchat.canary.utils.AudioUtils.uploadAudioToQiniu(
                    file = audioFile,
                    token = uploadToken,
                    uploadUrl = uploadDomain,
                    onProgress = { progress ->
                        _uiState.value = _uiState.value.copy(uploadProgress = progress)
                    }
                )

                if (uploadResponse == null) {
                    Log.e(tag, "音频上传失败")
                    _uiState.value = _uiState.value.copy(error = "音频上传失败")
                    audioFile.delete()
                    return@launch
                }

                Log.d(tag, "音频上传成功！")
                Log.d(tag, "   key: ${uploadResponse.key}")
                Log.d(tag, "   hash (etag): ${uploadResponse.hash}")
                Log.d(tag, "   size: ${uploadResponse.fsize} bytes")

                val duration = com.yhchat.canary.utils.AudioUtils.getAudioDuration(audioFile)
                Log.d(tag, "音频时长: ${duration}秒")

                val audioSuffix = uploadResponse.key.substringAfterLast('.', "m4a").lowercase()
                val sendResult = messageRepository.sendMessage(
                    chatId = currentChatId,
                    chatType = currentChatType,
                    payload = SendMessagePayload(
                        contentType = 11,
                        audioKey = uploadResponse.key,
                        audioTime = duration,
                        fileSize = uploadResponse.fsize,
                        quoteMsgId = quoteMsgId,
                        quoteMsgText = quoteMsgText,
                        quoteImageUrl = quoteImageUrl,
                        quoteImageName = quoteImageName,
                        quoteVideoUrl = quoteVideoUrl,
                        quoteVideoTime = quoteVideoTime,
                        media = SendMessageMedia(
                            fileKey = uploadResponse.key,
                            fileType = "audio/m4a",
                            fileHash = uploadResponse.hash,
                            fileSize = uploadResponse.fsize,
                            fileSuffix = audioSuffix
                        )
                    )
                )

                sendResult.fold(
                    onSuccess = {
                        Log.d(tag, "语音消息发送成功！")
                        loadMessages(refresh = true)
                        audioFile.delete()
                    },
                    onFailure = { error ->
                        Log.e(tag, "发送语音消息失败", error)
                        _uiState.value = _uiState.value.copy(error = "发送语音失败: ${error.message}")
                        audioFile.delete()
                    }
                )

            } catch (e: CancellationException) {
                Log.d(tag, "上传音频已取消")
            } catch (e: Exception) {
                Log.e(tag, "上传并发送音频异常", e)
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(error = "发送音频失败: ${e.message}")
            } finally {
                _uiState.value = _uiState.value.copy(uploadProgress = null)
                uploadJob = null
            }
        }
    }

    /**
     * 从URI获取文件（复制到临时目录）
     */
    private fun getFileFromUri(context: android.content.Context, uri: android.net.Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val tempFile = java.io.File(context.cacheDir, "temp_audio_${System.currentTimeMillis()}.wav")

            inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            if (tempFile.exists() && tempFile.length() > 0) {
                tempFile
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 从URI获取文件名
     */
    private fun getFileNameFromUri(context: android.content.Context, uri: android.net.Uri): String? {
        var fileName: String? = null

        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "无法从ContentResolver获取文件名", e)
        }

        if (fileName == null) {
            uri.lastPathSegment?.let { segment ->
                fileName = if (segment.contains('/')) {
                    segment.substringAfterLast('/')
                } else if (segment.contains(':')) {
                    segment.substringAfterLast(':')
                } else {
                    segment
                }
            }
        }

        if (fileName.isNullOrBlank()) {
            fileName = "file_${System.currentTimeMillis()}"
        }

        return fileName
    }

    /**
     * 处理编辑的消息
     */
    private fun handleEditedMessage(message: ChatMessage) {
        val normalizedMessage = normalizeMessageOwnership(message)
        val existingIndex = _messages.indexOfFirst { it.msgId == normalizedMessage.msgId }
        if (existingIndex != -1) {
            val existingMessage = _messages[existingIndex]
            val mergedMessage = existingMessage.mergeEditedMessage(normalizedMessage)
            _messages[existingIndex] = mergedMessage
            streamingMessages.remove(normalizedMessage.msgId)
            streamingMessageSenders.remove(normalizedMessage.msgId)
            streamingMessageContentTypes.remove(normalizedMessage.msgId)
            Log.d(
                tag,
                "Updated edited message in current list: msgId=${normalizedMessage.msgId}, " +
                    "oldText=${existingMessage.content.text?.take(50)}, " +
                    "newText=${mergedMessage.content.text?.take(50)}, " +
                    "oldContentType=${existingMessage.contentType}, " +
                    "newContentType=${mergedMessage.contentType}, " +
                    "editTime=${mergedMessage.editTime}"
            )
            refreshEditedMessageFromApi(
                patchMessage = normalizedMessage,
                fallbackMessage = mergedMessage
            )
            return
        }

        val targetChatId = resolveMessageChatId(normalizedMessage)
        if (targetChatId == currentChatId) {
            Log.d(tag, "Edited message belongs to current chat but is not loaded yet: ${normalizedMessage.msgId}")
            refreshEditedMessageFromApi(
                patchMessage = normalizedMessage,
                fallbackMessage = normalizedMessage
            )
        }
    }
    
    /**
     * 处理删除的消息
     */
    private fun handleDeletedMessage(messageId: String) {
        val existingIndex = _messages.indexOfFirst { it.msgId == messageId }
        if (existingIndex != -1) {
            _messages.removeAt(existingIndex)
            Log.d(tag, "Removed deleted message: $messageId")
        }
        streamingMessages.remove(messageId)
        streamingMessageSenders.remove(messageId)
        streamingMessageContentTypes.remove(messageId)
    }
    
    /**
     * 处理流式消息
     */
    private fun handleStreamMessage(event: MessageEvent.StreamMessage) {
        // 判断消息是否属于当前聊天
        if (event.chatId != currentChatId) {
            return
        }
        
        Log.d(tag, "Handling stream message: msgId=${event.msgId}, content=${event.content}")
        
        // 查找是否已有此消息
        val existingIndex = _messages.indexOfFirst { it.msgId == event.msgId }
        
        val senderOverride = streamingMessageSenders[event.msgId]
        val cachedContentType = streamingMessageContentTypes[event.msgId]

        if (existingIndex == -1) {
            // 消息不存在，先创建基础消息（push_message的作用）
            val latestKnownSender = _messages.asReversed().firstOrNull {
                it.sender.chatType == 3 && it.chatId == event.chatId
            }?.sender

            val baseSender = if (currentChatType == 3) {
                // 机器人私聊的流式消息，sender 必须固定为 botId，不能用 recvId 兜底。
                senderOverride
                    ?: latestKnownSender
                    ?: buildDefaultBotSender(event.chatId, event.recvId)
            } else {
                senderOverride
                    ?: latestKnownSender
                    ?: resolveGroupBotSender(event.recvId)
                    ?: buildDefaultBotSender(event.chatId, event.recvId)
            }

            val initialContent = buildString {
                val cached = streamingMessages[event.msgId].orEmpty()
                if (cached.isNotEmpty()) append(cached)
                append(event.content)
            }

            val baseMessage = ChatMessage(
                msgId = event.msgId,
                sender = baseSender,
                direction = "left",
                contentType = cachedContentType ?: 1,
                content = com.yhchat.canary.data.model.MessageContent(
                    text = initialContent
                ),
                sendTime = System.currentTimeMillis(),
                cmd = null,
                msgDeleteTime = null,
                quoteMsgId = null,
                msgSeq = null,
                editTime = null,
                chatId = event.chatId,
                chatType = 3,
                recvId = event.recvId
            )
            
            val insertIndex = _messages.indexOfLast { it.sendTime <= baseMessage.sendTime } + 1
            _messages.add(insertIndex, baseMessage)
            streamingMessages[event.msgId] = initialContent
            streamingMessageSenders[event.msgId] = baseSender
            streamingMessageContentTypes.putIfAbsent(event.msgId, baseMessage.contentType)
            Log.d(tag, "Created base message for stream at index $insertIndex")
        } else {
            // 消息已存在，追加内容
            val existingMessage = _messages[existingIndex]
            val baseContent = streamingMessages[event.msgId] ?: existingMessage.content.text.orEmpty()
            val accumulatedContent = baseContent + event.content
            streamingMessages[event.msgId] = accumulatedContent
            val resolvedSender = if (currentChatType == 3) {
                senderOverride
                    ?: existingMessage.sender.takeIf { it.chatType == 3 && it.chatId == event.chatId }
                    ?: buildDefaultBotSender(event.chatId, event.recvId)
            } else {
                senderOverride
                    ?: existingMessage.sender.takeIf { it.chatType == 3 && it.chatId == event.chatId }
                    ?: resolveGroupBotSender(event.recvId)
                    ?: buildDefaultBotSender(event.chatId, event.recvId)
            }
            streamingMessageSenders.putIfAbsent(event.msgId, resolvedSender)

            val resolvedContentType = when {
                existingMessage.contentType == 14 -> 14
                cachedContentType == 14 -> 14
                else -> existingMessage.contentType.takeIf { it != 1 } ?: (cachedContentType ?: existingMessage.contentType)
            }

            val updatedMessage = existingMessage.copy(
                sender = if (existingMessage.sender.chatType == 3 && existingMessage.sender.chatId != event.chatId) {
                    resolvedSender
                } else {
                    existingMessage.sender
                },
                contentType = resolvedContentType,
                content = existingMessage.content.copy(text = accumulatedContent)
            )
            _messages[existingIndex] = updatedMessage
            streamingMessageContentTypes[event.msgId] = updatedMessage.contentType
            Log.d(tag, "Updated stream message at index $existingIndex")
        }
    }

    private fun resolveGroupBotSender(botId: String?): com.yhchat.canary.data.model.MessageSender? {
        if (botId.isNullOrBlank()) return null
        val bot = uiState.value.groupBots.firstOrNull { it.botId == botId }
        return bot?.let {
            com.yhchat.canary.data.model.MessageSender(
                chatId = it.botId,
                chatType = 3,
                name = it.name.ifBlank { "机器人" },
                avatarUrl = it.avatarUrl,
                tagOld = emptyList(),
                tag = emptyList()
            )
        }
    }

    private fun buildDefaultBotSender(chatId: String, recvId: String?): com.yhchat.canary.data.model.MessageSender {
        val botInfo = uiState.value.botInfo?.data
        val resolvedName = botInfo?.name?.takeIf { it.isNotBlank() } ?: "机器人"
        val resolvedAvatar = botInfo?.avatarUrl.orEmpty()
        // 机器人会话里，发送者始终应是 botId 本身；recvId 只是消息接收者，不能拿来当 sender。
        val resolvedChatId = chatId.takeIf { it.isNotBlank() } ?: recvId.orEmpty()
        return com.yhchat.canary.data.model.MessageSender(
            chatId = resolvedChatId,
            chatType = 3,
            name = resolvedName,
            avatarUrl = resolvedAvatar,
            tagOld = emptyList(),
            tag = emptyList()
        )
    }

    /**
     * 从指定位置加载消息（用于恢复上次阅读位置）
     */
    private fun loadMessagesFromPosition(msgId: String) {
        if (currentChatId.isEmpty()) {
            Log.w(tag, "Chat not initialized")
            return
        }

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)

                // 加载指定消息及前后20条消息
                val result = messageRepository.getMessages(
                    chatId = currentChatId,
                    chatType = currentChatType,
                    msgCount = 40,  // 加载更多消息以提供上下文
                    msgId = msgId
                )

                result.fold(
                    onSuccess = { newMessages ->
                        Log.d(tag, "Loaded ${newMessages.size} messages from position $msgId")
                        
                        // 过滤被屏蔽用户的消息
                        val filteredMessages = newMessages.filter { message ->
                            val isBlocked = kotlin.runCatching {
                                blocklistRepository.isUserBlocked(message.sender.chatId)
                            }.getOrElse { false }
                            !isBlocked
                        }
                        
                        if (filteredMessages.size < newMessages.size) {
                            Log.d(tag, "Filtered out ${newMessages.size - filteredMessages.size} messages from blocked users")
                        }
                        
                        _messages.clear()
                        _messages.addAll(filteredMessages.sortedBy { it.sendTime })

                        // 更新最旧消息的序列号和ID
                        if (newMessages.isNotEmpty()) {
                            oldestMsgId = filteredMessages.minByOrNull { it.sendTime }?.msgId
                            oldestMsgSeq = filteredMessages.minByOrNull { it.sendTime }?.msgSeq ?: 0L
                        }

                        _uiState.value = _uiState.value.copy(isLoading = false, error = null)
                    },
                    onFailure = { error ->
                        Log.e(tag, "Failed to load messages from position: $msgId", error)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "加载消息失败: ${error.message}"
                        )
                        // 失败则加载最新消息
                        loadMessages()
                    }
                )
            } catch (e: Exception) {
                Log.e(tag, "Exception loading messages from position", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "加载消息异常: ${e.message}"
                )
                // 异常则加载最新消息
                loadMessages()
            }
        }
    }

    /**
     * 通过消息ID加载消息（用于跳转到引用消息或搜索结果定位）
     * 使用 list-message-by-mid-seq API 精确定位并加载消息
     * @param quoteMsgId 目标消息ID
     * @param targetMsgSeq 消息序列号，未指定或点击引用消息时默认为 64 位无符号整数（uint64）的最大值 (-1L / 0xFFFFFFFFFFFFFFFFL)
     */
    fun loadMessageByIdAndScroll(quoteMsgId: String, targetMsgSeq: Long? = null) {
        if (currentChatId.isEmpty()) {
            Log.w(tag, "⚠️ 聊天未初始化，无法加载消息")
            return
        }

        viewModelScope.launch {
            try {
                // 点击被引用消息时传入的 seq 默认为 64 位无符号整数（uint64）的最大值 (即 -1L)
                val seqToUse = targetMsgSeq ?: -1L
                Log.d(tag, "📍 开始通过 msgId 加载引用/搜索消息: $quoteMsgId, targetMsgSeq=$seqToUse")
                Log.d(tag, "📊 当前聊天: chatId=$currentChatId, chatType=$currentChatType")
                
                // 步骤1: 先在已加载的消息列表中通过msgId精确查找
                Log.d(tag, "🔍 步骤1: 在已加载的 ${_messages.size} 条消息中查找 msgId: $quoteMsgId")
                val existingMessage = _messages.find { it.msgId == quoteMsgId }
                
                if (existingMessage != null) {
                    Log.d(tag, "✅ 消息已在列表中，msgId: $quoteMsgId，发送时间: ${existingMessage.sendTime}")
                    _uiState.value = _uiState.value.copy(scrollToMsgId = quoteMsgId)
                    return@launch
                }
                
                Log.d(tag, "⚠️ 消息不在列表中，准备从服务器加载")

                // 步骤2: 先尝试从本地缓存获取目标消息
                Log.d(tag, "📡 步骤2: 先从本地缓存获取目标消息")
                val cachedMessage = messageRepository.getMessageById(quoteMsgId)
                
                if (cachedMessage != null) {
                    Log.d(tag, "✅ 从缓存获取到目标消息，msgSeq=${cachedMessage.msgSeq}")
                    val existingIndex = _messages.indexOfFirst { it.msgId == cachedMessage.msgId }
                    if (existingIndex == -1) {
                        val insertIndex = _messages.indexOfLast { it.sendTime <= cachedMessage.sendTime } + 1
                        _messages.add(insertIndex, cachedMessage)
                        handleAutoCollapseForMessages(listOf(cachedMessage))
                        Log.d(tag, "🎉 目标消息已成功插入列表位置: $insertIndex, sendTime: ${cachedMessage.sendTime}")
                    }
                    _uiState.value = _uiState.value.copy(scrollToMsgId = quoteMsgId)
                    return@launch
                } else {
                    Log.w(tag, "⚠️ 本地缓存中未找到消息，尝试使用 list-message-by-mid-seq 进行链式加载")
                    
                    // 记录调用前已有消息中最旧消息的时间戳（用于判断区间断层）
                    val minExistingSendTime = _messages.minOfOrNull { it.sendTime }
                    val timeGapThresholdMs = 4 * 60 * 60 * 1000L // 4小时时间差阈值
                    val maxChainIterations = 10 // 最多连续向后抓取10次，防止无消息死循环
                    
                    val accumulatedMessages = mutableListOf<ChatMessage>()
                    val fetchedMsgIds = mutableSetOf<String>()
                    
                    var nextQueryMsgId: String? = quoteMsgId
                    var nextQueryMsgSeq: Long = seqToUse
                    var iteration = 0
                    
                    while (nextQueryMsgId != null && iteration < maxChainIterations) {
                        iteration++
                        Log.d(tag, "📡 步骤3.${iteration}: 调用 list-message-by-mid-seq API, msgId=$nextQueryMsgId, msgSeq=$nextQueryMsgSeq")
                        
                        val result = messageRepository.getMessagesByMsgId(
                            chatId = currentChatId,
                            chatType = currentChatType,
                            msgId = nextQueryMsgId,
                            msgCount = 30,
                            msgSeq = nextQueryMsgSeq
                        )
                        
                        val fetchedList = result.getOrNull()
                        if (fetchedList.isNullOrEmpty()) {
                            Log.d(tag, "📡 第 $iteration 次请求未获取到消息，停止向后连接")
                            break
                        }
                        
                        // 过滤被屏蔽用户的消息
                        val validMessages = fetchedList.filter { message ->
                            val isBlocked = kotlin.runCatching {
                                blocklistRepository.isUserBlocked(message.sender.chatId)
                            }.getOrElse { false }
                            !isBlocked
                        }
                        
                        val freshMsgs = validMessages.filter { it.msgId !in fetchedMsgIds }
                        if (freshMsgs.isEmpty()) {
                            Log.d(tag, "📡 第 $iteration 次请求没有返回更多新消息，停止向后连接")
                            break
                        }
                        
                        for (m in freshMsgs) {
                            fetchedMsgIds.add(m.msgId)
                            accumulatedMessages.add(m)
                        }
                        
                        // 获取当前累积获取到的消息中时间戳最大的一条消息
                        val maxNewMessage = accumulatedMessages.maxByOrNull { it.sendTime }
                        if (minExistingSendTime == null || maxNewMessage == null) {
                            // 原本列表为空，无需与旧消息对比时间差
                            break
                        }
                        
                        val timeDiff = minExistingSendTime - maxNewMessage.sendTime
                        Log.d(tag, "⏱️ 时间戳对比: 抓取最新=${maxNewMessage.sendTime}, 原有最旧=$minExistingSendTime, 差值=${timeDiff / 1000}秒 (${timeDiff / (3600 * 1000)}小时)")
                        
                        // 如果获取到的最新消息时间已接近（相差在4小时以内）或已超过原有最旧消息，说明已经成功衔接
                        if (timeDiff <= timeGapThresholdMs) {
                            Log.d(tag, "✅ 时间戳相差在4小时以内或已衔接上，无需继续向后请求")
                            break
                        }
                        
                        // 避免相同的 msgId 重复死循环
                        if (maxNewMessage.msgId == nextQueryMsgId) {
                            Log.w(tag, "⚠️ 消息ID未向前推进，停止链式请求")
                            break
                        }
                        
                        nextQueryMsgId = maxNewMessage.msgId
                        nextQueryMsgSeq = maxNewMessage.msgSeq.takeIf { it > 0L } ?: -1L
                        Log.d(tag, "⏳ 时间差超过 4 小时，以最新消息 ($nextQueryMsgId) 再次请求 by-mid-seq...")
                    }
                            
                    // 去重并按发送时间升序排列
                    val newMsgs = accumulatedMessages.filter { newMsg ->
                        _messages.none { it.msgId == newMsg.msgId }
                    }.sortedBy { it.sendTime }
                    Log.d(tag, "➕ 准备按时间戳插入 ${newMsgs.size} 条新消息（去重后）")
                    
                    if (newMsgs.isNotEmpty()) {
                        for (newMsg in newMsgs) {
                            val insertIndex = _messages.indexOfLast { it.sendTime <= newMsg.sendTime } + 1
                            _messages.add(insertIndex, newMsg)
                        }
                        handleAutoCollapseForMessages(newMsgs)
                        
                        // 更新最旧消息的序列号和ID
                        val oldest = _messages.minByOrNull { it.sendTime }
                        if (oldest != null) {
                            oldestMsgId = oldest.msgId
                            oldestMsgSeq = oldest.msgSeq
                        }
                        Log.d(tag, "✅ 成功按时间戳插入，当前共 ${_messages.size} 条消息")
                        
                        // 确认目标消息是否在最终列表中
                        val finalCheck = _messages.find { it.msgId == quoteMsgId }
                        if (finalCheck != null) {
                            Log.d(tag, "🎉 目标消息 msgId: $quoteMsgId 已成功加入消息列表")
                        } else {
                            Log.e(tag, "❌ 目标消息 msgId: $quoteMsgId 未能加入消息列表")
                        }
                    } else {
                        Log.d(tag, "ℹ️ 没有新消息需要添加（可能都已存在）")
                    }
                    _uiState.value = _uiState.value.copy(scrollToMsgId = quoteMsgId)
                }
            } catch (e: Exception) {
                Log.e(tag, "❌ 加载消息异常 msgId: $quoteMsgId", e)
                _uiState.value = _uiState.value.copy(error = "加载引用消息异常")
            }
        }
    }
    
    fun clearScrollToMsgId() {
        _uiState.value = _uiState.value.copy(scrollToMsgId = null)
    }
    /**
     * 加载消息列表
     */
    fun loadMessages(refresh: Boolean = false) {
        if (currentChatId.isEmpty()) {
            Log.w(tag, "Chat not initialized")
            return
        }

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isLoading = !refresh,
                    isRefreshing = refresh,
                    error = null
                )

                val result = if (!oldestMsgId.isNullOrEmpty() && !refresh) {
                    // 加载更多历史消息 - 使用最老的消息ID
                    messageRepository.getMessages(
                        chatId = currentChatId,
                        chatType = currentChatType,
                        msgCount = DEFAULT_MSG_COUNT,
                        msgId = oldestMsgId
                    )
                } else {
                    // 加载最新消息
                    messageRepository.getMessages(
                        chatId = currentChatId,
                        chatType = currentChatType,
                    )
                }

                result.fold(
                    onSuccess = { newMessages ->
                        Log.d(tag, "Loaded ${newMessages.size} messages")
                        
                        // 过滤被屏蔽用户的消息
                        val filteredMessages = newMessages.filter { message ->
                            val isBlocked = kotlin.runCatching {
                                blocklistRepository.isUserBlocked(message.sender.chatId)
                            }.getOrElse { false }
                            !isBlocked
                        }
                        
                        if (filteredMessages.size < newMessages.size) {
                            Log.d(tag, "Filtered out ${newMessages.size - filteredMessages.size} messages from blocked users")
                        }
                        
                        if (refresh) {
                            // 刷新时替换所有消息
                            _messages.clear()
                            _collapsedMessageIds.clear()
                            val sortedMessages = filteredMessages.sortedBy { it.sendTime }
                            _messages.addAll(sortedMessages)
                            handleAutoCollapseForMessages(sortedMessages)
                        } else {
                            // 加载更多时添加到现有消息前面
                            val sortedNewMessages = filteredMessages.sortedBy { it.sendTime }
                            _messages.addAll(0, sortedNewMessages)
                            handleAutoCollapseForMessages(sortedNewMessages)
                        }

                        // 更新最旧消息的序列号和ID
                        if (newMessages.isNotEmpty()) {
                            oldestMsgSeq = newMessages.minOfOrNull { it.msgSeq ?: 0L } ?: oldestMsgSeq
                            // 找到发送时间最早的消息ID作为oldestMsgId
                            val oldestMessage = newMessages.minByOrNull { it.sendTime }
                            if (oldestMessage != null) {
                                oldestMsgId = oldestMessage.msgId
                                Log.d(tag, "Updated oldestMsgId to: $oldestMsgId, sendTime: ${oldestMessage.sendTime}")
                            }
                        }

                        // 检查是否还有更多消息
                        hasMoreMessages = newMessages.size >= DEFAULT_MSG_COUNT

                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = null
                        )
                    },
                    onFailure = { exception ->
                        Log.e(tag, "Failed to load messages", exception)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = exception.message ?: "加载消息失败"
                        )
                    }
                )
            } catch (e: Exception) {
                Log.e(tag, "Error loading messages", e)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                    isRefreshing = false,
                    error = e.message ?: "加载消息失败"
                    )
                }
        }
    }
    
    /**
     * 保存草稿
     */
    fun saveDraft(content: String) {
        if (currentChatId.isNotEmpty()) {
            draftStore.saveDraft(currentChatId, currentChatType, content)
        }
    }

    /**
     * 获取草稿
     */
    fun getDraft(chatId: String, chatType: Int): String? {
        return draftStore.getDraft(chatId, chatType)
    }

    /**
     * 清除草稿
     */
    fun clearDraft() {
        if (currentChatId.isNotEmpty()) {
            draftStore.clearDraft(currentChatId, currentChatType)
        }
    }

    /**
     * 加载更多历史消息
     */
    fun loadMoreMessages() {
        if (!hasMoreMessages || _uiState.value.isLoading) {
            Log.d(tag, "No more messages to load or already loading")
            return
        }
        
        Log.d(tag, "Loading more messages from msgId: $oldestMsgId, seq: $oldestMsgSeq")
        loadMessages(refresh = false)
    }

    /**
     * 发送文本消息
     */
    fun sendTextMessage(text: String, quoteMsgId: String? = null) {
        sendMessage(text, 1, quoteMsgId)
    }
    
    /**
     * 发送消息（支持不同类型）
     * @param text 消息文本
     * @param contentType 消息类型：1-文本，3-Markdown，8-HTML
     * @param quoteMsgId 引用消息ID
     * @param quoteMsgText 引用消息文本
     * @param commandId 指令ID
     * @param mentionedIds @的用户ID列表
     */
    fun sendMessage(
        text: String, 
        contentType: Int = 1, 
        quoteMsgId: String? = null,
        quoteMsgText: String? = null,
        quoteImageUrl: String? = null,
        quoteImageName: String? = null,
        quoteVideoUrl: String? = null,
        quoteVideoTime: Long? = null,
        commandId: Long? = null,
        mentionedIds: List<String>? = null,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {},
        isSendTextAllowEmptySetting: Boolean = false,//TODO 英语不好不知道起什么变量名
    ) {
        if (currentChatId.isEmpty()) {
            Log.w(tag, "Chat not initialized")
            onError("Chat not initialized")
            return
        }
        // 如果是指令消息，允许空文本；否则检查文本是否为空；如果设置了允许发送空文本消息的话也行
        if (text.isEmpty() && commandId == null && !isSendTextAllowEmptySetting) {
            Log.w(tag, "Cannot send empty message without command")
            onError("Cannot send empty message")
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastTextSendAtMs < textSendDebounceWindowMs) {
            Log.d(tag, "Ignore duplicated send in debounce window: ${now - lastTextSendAtMs}ms")
            return
        }
        lastTextSendAtMs = now
        
        viewModelScope.launch {
            try {
                val typeText = when (contentType) {
                    3 -> "Markdown"
                    8 -> "HTML"
                    else -> "文本"
                }
                val mentionInfo = if (!mentionedIds.isNullOrEmpty()) " (@${mentionedIds.size}人)" else ""
                val quoteMediaInfo = when {
                    !quoteImageUrl.isNullOrEmpty() -> " (引用图片)"
                    !quoteVideoUrl.isNullOrEmpty() -> " (引用视频)"
                    !quoteMsgId.isNullOrEmpty() -> " (引用消息)"
                    else -> ""
                }
                Log.d(tag, "Sending $typeText message: $text$quoteMediaInfo$mentionInfo")
                
                val result = messageRepository.sendMessage(
                    chatId = currentChatId,
                    chatType = currentChatType,
                    text = text,
                    contentType = contentType,
                    quoteMsgId = quoteMsgId,
                    quoteMsgText = quoteMsgText,
                    quoteImageUrl = quoteImageUrl,
                    quoteImageName = quoteImageName,
                    quoteVideoUrl = quoteVideoUrl,
                    quoteVideoTime = quoteVideoTime,
                    commandId = commandId,
                    mentionedIds = mentionedIds
                )

                result.fold(
                    onSuccess = { success ->
                        if (success) {
                            Log.d(tag, "$typeText message sent successfully")
                            // 发送成功后刷新消息列表以获取最新消息
                            loadMessages(refresh = true)
                            onSuccess()
                        } else {
                            Log.e(tag, "Failed to send $typeText message")
                            _uiState.value = _uiState.value.copy(error = "发送失败")
                            onError("发送失败")
                        }
                    },
                    onFailure = { error ->
                        Log.e(tag, "Error sending $typeText message", error)
                        _uiState.value = _uiState.value.copy(error = "发送失败: ${error.message}")
                        onError(error.message ?: "发送失败")
                    }
                )

            } catch (e: Exception) {
                Log.e(tag, "Error sending message", e)
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "发送消息失败"
                )
            }
        }
    }
    
    /**
     * 发送表情消息（从表情选择器选择）
     */
    fun sendExpressionMessage(
        expression: com.yhchat.canary.data.model.Expression,
        quoteMsgId: String? = null,
        quoteMsgText: String? = null,
        quoteImageUrl: String? = null,
        quoteImageName: String? = null,
        quoteVideoUrl: String? = null,
        quoteVideoTime: Long? = null
    ) {
        if (currentChatId.isEmpty()) {
            Log.w(tag, "Chat not initialized")
            return
        }
        
        viewModelScope.launch {
            try {
                Log.d(tag, "Sending expression message: id=${expression.id}, url=${expression.url}")
                
                val result = messageRepository.sendMessage(
                    chatId = currentChatId,
                    chatType = currentChatType,
                    payload = SendMessagePayload(
                        contentType = 7,
                        expressionId = expression.id.toString(),
                        imageKey = expression.url,
                        quoteMsgId = quoteMsgId,
                        quoteMsgText = quoteMsgText,
                        quoteImageUrl = quoteImageUrl,
                        quoteImageName = quoteImageName,
                        quoteVideoUrl = quoteVideoUrl,
                        quoteVideoTime = quoteVideoTime
                    )
                )
                
                result.fold(
                    onSuccess = { success ->
                        if (success) {
                            Log.d(tag, "Expression message sent successfully")
                            loadMessages(refresh = true)
                        }
                    },
                    onFailure = { exception ->
                        Log.e(tag, "Failed to send expression message", exception)
                        _uiState.value = _uiState.value.copy(
                            error = exception.message ?: "发送表情失败"
                        )
                    }
                )
            } catch (e: Exception) {
                Log.e(tag, "Error sending expression message", e)
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "发送表情失败"
                )
            }
        }
    }
    
    /**
     * 发送表情包贴纸消息
     */
    fun sendStickerMessage(
        stickerItem: com.yhchat.canary.data.model.StickerItem,
        quoteMsgId: String? = null,
        quoteMsgText: String? = null,
        quoteImageUrl: String? = null,
        quoteImageName: String? = null,
        quoteVideoUrl: String? = null,
        quoteVideoTime: Long? = null
    ) {
        if (currentChatId.isEmpty()) {
            Log.w(tag, "Chat not initialized")
            return
        }
        
        viewModelScope.launch {
            try {
                Log.d(tag, "Sending sticker message: id=${stickerItem.id}, url=${stickerItem.url}")
                
                val result = messageRepository.sendMessage(
                    chatId = currentChatId,
                    chatType = currentChatType,
                    payload = SendMessagePayload(
                        contentType = 7,
                        imageKey = stickerItem.url,
                        stickerItemId = stickerItem.id,
                        stickerPackId = stickerItem.stickerPackId,
                        quoteMsgId = quoteMsgId,
                        quoteMsgText = quoteMsgText,
                        quoteImageUrl = quoteImageUrl,
                        quoteImageName = quoteImageName,
                        quoteVideoUrl = quoteVideoUrl,
                        quoteVideoTime = quoteVideoTime
                    )
                )
                
                result.fold(
                    onSuccess = { success ->
                        if (success) {
                            Log.d(tag, "Sticker message sent successfully")
                            loadMessages(refresh = true)
                        }
                    },
                    onFailure = { exception ->
                        Log.e(tag, "Failed to send sticker message", exception)
                        _uiState.value = _uiState.value.copy(
                            error = exception.message ?: "发送表情包失败"
                        )
                    }
                )
            } catch (e: Exception) {
                Log.e(tag, "Error sending sticker message", e)
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "发送表情包失败"
                )
            }
        }
    }
    
    /**
     * 发送草稿输入（输入框内容变化时调用）
     */
    fun sendDraftInput(inputText: String) {
        if (currentChatId.isNotEmpty()) {
            viewModelScope.launch {
                try {
                    // 记录发送时间，防止立即应用自己的草稿
                    lastDraftSentTime = System.currentTimeMillis()
                    
                    webSocketManager.sendDraftInput(currentChatId, inputText)
                    Log.d(tag, "📤 发送草稿输入: chatId=$currentChatId, length=${inputText.length}")
                } catch (e: Exception) {
                    Log.e(tag, "Failed to send draft input", e)
                }
            }
        }
    }
    
    /**
     * 通过URL发送图片消息（用于+1功能）
     */
    fun sendImageByUrl(imageUrl: String, width: Int, height: Int, fileSize: Long) {
        if (currentChatId.isEmpty()) {
            Log.w(tag, "Chat not initialized")
            return
        }
        
        viewModelScope.launch {
            try {
                Log.d(tag, "📷 +1发送图片: url=$imageUrl, size=${width}x${height}")
                
                // 从URL提取图片key（假设URL格式为 https://chat-img.jwznb.com/xxx）
                val imageKey = imageUrl.substringAfterLast("/").substringBefore("?")
                val imageSuffix = imageKey.substringAfterLast('.', "jpg").lowercase()
                val imageMime = when (imageSuffix) {
                    "png" -> "image/png"
                    "gif" -> "image/gif"
                    "webp" -> "image/webp"
                    else -> "image/jpeg"
                }
                
                val result = messageRepository.sendMessage(
                    chatId = currentChatId,
                    chatType = currentChatType,
                    payload = SendMessagePayload(
                        contentType = 2,
                        imageKey = imageKey,
                        fileSize = fileSize,
                        media = SendMessageMedia(
                            fileKey = imageKey,
                            fileType = imageMime,
                            fileSize = fileSize,
                            fileSuffix = imageSuffix,
                            imageWidth = width.toLong(),
                            imageHeight = height.toLong()
                        )
                    )
                )
                
                result.fold(
                    onSuccess = { 
                        Log.d(tag, "✅ +1图片消息发送成功")
                        loadMessages(refresh = true)
                    },
                    onFailure = { exception ->
                        Log.e(tag, "❌ +1图片消息发送失败", exception)
                        _uiState.value = _uiState.value.copy(error = "发送图片失败: ${exception.message}")
                    }
                )
            } catch (e: Exception) {
                Log.e(tag, "❌ +1图片消息异常", e)
                _uiState.value = _uiState.value.copy(error = "发送图片异常")
            }
        }
    }
    
    /**
     * 通过URL发送文件消息（用于+1功能）
     */
    fun sendFileByUrl(fileName: String, fileUrl: String, fileSize: Long) {
        if (currentChatId.isEmpty()) {
            Log.w(tag, "Chat not initialized")
            return
        }
        
        viewModelScope.launch {
            try {
                Log.d(tag, "📁 +1发送文件: name=$fileName, url=$fileUrl")
                
                // 从URL提取文件key
                val fileKey = fileUrl.substringAfterLast("/").substringBefore("?")
                
                val result = messageRepository.sendMessage(
                    chatId = currentChatId,
                    chatType = currentChatType,
                    payload = SendMessagePayload(
                        contentType = 4,
                        fileName = fileName,
                        fileKey = fileKey,
                        fileSize = fileSize
                    )
                )
                
                result.fold(
                    onSuccess = { 
                        Log.d(tag, "✅ +1文件消息发送成功")
                        loadMessages(refresh = true)
                    },
                    onFailure = { exception ->
                        Log.e(tag, "❌ +1文件消息发送失败", exception)
                        _uiState.value = _uiState.value.copy(error = "发送文件失败: ${exception.message}")
                    }
                )
            } catch (e: Exception) {
                Log.e(tag, "❌ +1文件消息异常", e)
                _uiState.value = _uiState.value.copy(error = "发送文件异常")
            }
        }
    }
    
    /**
     * 通过URL发送视频消息（用于+1功能）
     */
    fun sendVideoByUrl(videoUrl: String) {
        if (currentChatId.isEmpty()) {
            Log.w(tag, "Chat not initialized")
            return
        }
        
        viewModelScope.launch {
            try {
                Log.d(tag, "🎬 +1发送视频: url=$videoUrl")
                
                // 从URL提取视频key
                val videoKey = videoUrl.substringAfterLast("/").substringBefore("?")
                
                val videoSuffix = videoKey.substringAfterLast('.', "mp4").lowercase()
                val result = messageRepository.sendMessage(
                    chatId = currentChatId,
                    chatType = currentChatType,
                    payload = SendMessagePayload(
                        contentType = 10,
                        videoKey = videoKey,
                        media = SendMessageMedia(
                            fileKey = videoKey,
                            fileType = "video/mp4",
                            fileSuffix = videoSuffix
                        )
                    )
                )
                
                result.fold(
                    onSuccess = { 
                        Log.d(tag, "✅ +1视频消息发送成功")
                        loadMessages(refresh = true)
                    },
                    onFailure = { exception ->
                        Log.e(tag, "❌ +1视频消息发送失败", exception)
                        _uiState.value = _uiState.value.copy(error = "发送视频失败: ${exception.message}")
                    }
                )
            } catch (e: Exception) {
                Log.e(tag, "❌ +1视频消息异常", e)
                _uiState.value = _uiState.value.copy(error = "发送视频异常")
            }
        }
    }
    
    /**
     * 通过URL发送语音消息（用于+1功能）
     */
    fun sendAudioByUrl(audioUrl: String, audioDuration: Long) {
        if (currentChatId.isEmpty()) {
            Log.w(tag, "Chat not initialized")
            return
        }
        
        viewModelScope.launch {
            try {
                Log.d(tag, "🎤 +1发送语音: url=$audioUrl, duration=${audioDuration}s")
                
                // 从URL提取音频key
                val audioKey = audioUrl.substringAfterLast("/").substringBefore("?")
                
                val audioSuffix = audioKey.substringAfterLast('.', "m4a").lowercase()
                val result = messageRepository.sendMessage(
                    chatId = currentChatId,
                    chatType = currentChatType,
                    payload = SendMessagePayload(
                        contentType = 11,
                        audioKey = audioKey,
                        audioTime = audioDuration,
                        media = SendMessageMedia(
                            fileKey = audioKey,
                            fileType = "video/mp4",
                            fileSuffix = audioSuffix
                        )
                    )
                )
                
                result.fold(
                    onSuccess = { 
                        Log.d(tag, "✅ +1语音消息发送成功")
                        loadMessages(refresh = true)
                    },
                    onFailure = { exception ->
                        Log.e(tag, "❌ +1语音消息发送失败", exception)
                        _uiState.value = _uiState.value.copy(error = "发送语音失败: ${exception.message}")
                    }
                )
            } catch (e: Exception) {
                Log.e(tag, "❌ +1语音消息异常", e)
                _uiState.value = _uiState.value.copy(error = "发送语音异常")
            }
        }
    }
    
    /**
     * 发送文章消息（用于+1功能）
     */
    fun sendPostMessage(postId: String, postTitle: String, postContent: String, postType: String) {
        if (currentChatId.isEmpty()) {
            Log.w(tag, "Chat not initialized")
            return
        }
        
        viewModelScope.launch {
            try {
                Log.d(tag, "📄 +1发送文章: id=$postId, title=$postTitle")
                
                val result = messageRepository.sendMessage(
                    chatId = currentChatId,
                    chatType = currentChatType,
                    payload = SendMessagePayload(
                        contentType = 6,
                        postId = postId,
                        postTitle = postTitle,
                        postContent = postContent,
                        postType = postType
                    )
                )
                
                result.fold(
                    onSuccess = { 
                        Log.d(tag, "✅ +1文章消息发送成功")
                        loadMessages(refresh = true)
                    },
                    onFailure = { exception ->
                        Log.e(tag, "❌ +1文章消息发送失败", exception)
                        _uiState.value = _uiState.value.copy(error = "发送文章失败: ${exception.message}")
                    }
                )
            } catch (e: Exception) {
                Log.e(tag, "❌ +1文章消息异常", e)
                _uiState.value = _uiState.value.copy(error = "发送文章异常")
            }
        }
    }
    
    /**
     * 添加新收到的消息（通过WebSocket）
     */
    fun addNewMessage(message: ChatMessage) {
        Log.d(tag, "Adding new message: ${message.msgId}")
        
        // 检查黑名单
        viewModelScope.launch {
            val isBlocked = blocklistRepository.isUserBlocked(message.sender.chatId)
            if (isBlocked) {
                Log.d(tag, "Message from blocked user ${message.sender.chatId}, ignored")
                return@launch
            }
            
            // 检查消息是否已存在
            val existingIndex = _messages.indexOfFirst { it.msgId == message.msgId }
            if (existingIndex != -1) {
                // 消息已存在，更新它
                _messages[existingIndex] = message
                Log.d(tag, "Updated existing message: ${message.msgId}")
            } else {
                // 新消息，按时间排序插入
                val insertIndex = _messages.indexOfLast { it.sendTime <= message.sendTime } + 1
                _messages.add(insertIndex, message)
                Log.d(tag, "Inserted new message at index: $insertIndex")
            }
        }
    }
    
    /**
     * 更新消息（编辑后）
     */
    fun updateMessage(message: ChatMessage) {
        val index = _messages.indexOfFirst { it.msgId == message.msgId }
        if (index != -1) {
            _messages[index] = message
            Log.d(tag, "Updated message: ${message.msgId}")
        }
    }
    
    /**
     * 删除消息（撤回）
     */
    fun removeMessage(msgId: String) {
        val index = _messages.indexOfFirst { it.msgId == msgId }
        if (index != -1) {
            _messages.removeAt(index)
            Log.d(tag, "Removed message: $msgId")
        }
    }
    
    /**
     * 撤回消息
     */
    fun recallMessage(msgId: String) {
        viewModelScope.launch {
            try {
                Log.d(tag, "开始撤回消息: $msgId")
                
                val result = messageRepository.recallMessage(
                    chatId = currentChatId,
                    chatType = currentChatType,
                    msgId = msgId
                )
                
                result.fold(
                    onSuccess = {
                        Log.d(tag, "消息撤回成功: $msgId")
                        // 找到并更新消息，将其标记为已撤回
                        val index = _messages.indexOfFirst { it.msgId == msgId }
                        if (index != -1) {
                            val message = _messages[index]
                            _messages[index] = message.copy(
                                msgDeleteTime = System.currentTimeMillis()
                            )
                            Log.d(tag, "更新消息为撤回状态")
                        }
                    },
                    onFailure = { error ->
                        Log.e(tag, "消息撤回失败: ${error.message}", error)
                        _uiState.value = _uiState.value.copy(error = error.message)
                    }
                )
            } catch (e: Exception) {
                Log.e(tag, "撤回消息异常", e)
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }
    
    /**
     * 批量撤回消息
     */
    fun recallMessagesBatch(msgIds: List<String>) {
        viewModelScope.launch {
            try {
                Log.d(tag, "开始批量撤回消息: ${msgIds.size} 条")
                
                val result = messageRepository.recallMessagesBatch(
                    chatId = currentChatId,
                    chatType = currentChatType,
                    msgIds = msgIds
                )
                
                result.fold(
                    onSuccess = {
                        Log.d(tag, "批量撤回成功: ${msgIds.size} 条")
                        // 更新本地消息为已撤回状态
                        val currentTime = System.currentTimeMillis()
                        msgIds.forEach { msgId ->
                            val index = _messages.indexOfFirst { it.msgId == msgId }
                            if (index != -1) {
                                val message = _messages[index]
                                _messages[index] = message.copy(
                                    msgDeleteTime = currentTime
                                )
                            }
                        }
                    },
                    onFailure = { error ->
                        Log.e(tag, "批量撤回失败: ${error.message}", error)
                        _uiState.value = _uiState.value.copy(error = "批量撤回失败: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                Log.e(tag, "批量撤回消息异常", e)
                _uiState.value = _uiState.value.copy(error = "批量撤回异常: ${e.message}")
            }
        }
    }
    
    /**
     * 编辑消息
     */
    fun editMessage(
        chatId: String,
        chatType: Int,
        msgId: String,
        content: String,
        contentType: Int,
        quoteMsgId: String? = null,
        quoteMsgText: String? = null,
        quoteImageUrl: String? = null,
        quoteImageName: String? = null,
        quoteVideoUrl: String? = null,
        quoteVideoTime: Long? = null,
        buttons: String? = null
    ) {
        viewModelScope.launch {
            try {
                // 找到要编辑的消息
                val message = _messages.find { it.msgId == msgId }
                if (message == null) {
                    Log.e(tag, "未找到要编辑的消息: $msgId")
                    return@launch
                }
                
                val result = messageRepository.editMessage(
                    chatId = chatId,
                    chatType = chatType,
                    msgId = msgId,
                    content = content,
                    contentType = contentType,
                    quoteMsgId = quoteMsgId,
                    quoteMsgText = quoteMsgText,
                    quoteImageUrl = message.content.quoteImageUrl,
                    quoteImageName = message.content.quoteImageName,
                    quoteVideoUrl = message.content.quoteVideoUrl,
                    quoteVideoTime = message.content.quoteVideoTime,
                    buttons = buttons
                )
                
                result.fold(
                    onSuccess = {
                        Log.d(tag, "消息编辑成功: $msgId")
                        // 找到并更新消息内容
                        val index = _messages.indexOfFirst { it.msgId == msgId }
                        if (index != -1) {
                            val message = _messages[index]
                            _messages[index] = message.copy(
                                content = message.content.copy(
                                    text = content,
                                    quoteMsgText = quoteMsgText ?: message.content.quoteMsgText,
                                    buttons = buttons ?: message.content.buttons
                                ),
                                quoteMsgId = quoteMsgId ?: message.quoteMsgId,
                                contentType = contentType,
                                editTime = System.currentTimeMillis()
                            )
                            Log.d(tag, "更新消息内容")
                        }
                    },
                    onFailure = { error ->
                        Log.e(tag, "消息编辑失败: ${error.message}", error)
                        _uiState.value = _uiState.value.copy(error = error.message)
                    }
                )
            } catch (e: Exception) {
                Log.e(tag, "编辑消息异常", e)
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }
    
    /**
     * 清除错误状态
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    /**
     * 加载机器人信息
     */
    fun loadBotInfo(botId: String) {
        viewModelScope.launch {
            botRepository.getBotInfo(botId).fold(
                onSuccess = { botInfo ->
                    _uiState.value = _uiState.value.copy(botInfo = botInfo)
                    Log.d(tag, "机器人信息加载成功: ${botInfo.data.name}")
                },
                onFailure = { error ->
                    Log.e(tag, "加载机器人信息失败", error)
                }
            )
        }
    }
    
    /**
     * 加载机器人看板
     */
    fun loadBotBoard(chatId: String, chatType: Int) {
        viewModelScope.launch {
            botRepository.getBotBoard(chatId, chatType).fold(
                onSuccess = { board ->
                    // 单个机器人私聊时，boardList应该只有一个元素
                    if (board.boardCount > 0) {
                        _uiState.value = _uiState.value.copy(botBoard = board)
                    }
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(error = "加载机器人看板失败: ${error.message}")
                }
            )
        }
    }
    
    /**
     * 加载聊天背景
     */
    fun loadChatBackground(context: android.content.Context, chatId: String) {
        viewModelScope.launch {
            try {
                val chatBackgroundRepository = com.yhchat.canary.data.di.RepositoryFactory.getChatBackgroundRepository(context)
                
                // 获取背景列表
                chatBackgroundRepository.getChatBackgroundList().fold(
                    onSuccess = { backgrounds ->
                        // 查找特定聊天背景，如果没有则查找全局背景
                        val specificBg = backgrounds.find { it.chatId == chatId }
                        val globalBg = backgrounds.find { it.chatId == "all" }
                        val backgroundUrl = specificBg?.imgUrl ?: globalBg?.imgUrl
                        
                        _uiState.value = _uiState.value.copy(chatBackgroundUrl = backgroundUrl)
                        Log.d(tag, "聊天背景加载成功: $backgroundUrl")
                    },
                    onFailure = { error ->
                        Log.e(tag, "加载聊天背景失败", error)
                    }
                )
            } catch (e: Exception) {
                Log.e(tag, "加载聊天背景异常", e)
            }
        }
    }
    
    /**
     * 保存当前读取位置（退出聊天时调用）
     */
    fun saveCurrentReadPosition() {
        if (currentChatId.isEmpty() || _messages.isEmpty()) {
            return
        }
        
        // 取最新的消息作为读取位置
        val latestMessage = _messages.maxByOrNull { it.sendTime }
        if (latestMessage != null && latestMessage.msgSeq != null) {
            readPositionStore.saveReadPosition(
                chatId = currentChatId,
                chatType = currentChatType,
                msgId = latestMessage.msgId,
                msgSeq = latestMessage.msgSeq!!
            )
            Log.d(tag, "Saved read position: msgId=${latestMessage.msgId}, msgSeq=${latestMessage.msgSeq}")
        }
    }
    
    /**
     * 获取当前会话的未读消息数
     * @return 未读消息数，如果无法计算则返回null
     */
    fun getUnreadCount(): Int? {
        if (currentChatId.isEmpty()) {
            return null
        }
        
        val readPosition = readPositionStore.getReadPosition(currentChatId, currentChatType)
        if (readPosition == null || _messages.isEmpty()) {
            return null
        }
        
        // 取最新消息的 msgSeq
        val latestMsgSeq = _messages.maxOfOrNull { it.msgSeq ?: 0L } ?: return null
        val lastReadMsgSeq = readPosition.second
        
        // 计算未读数
        val unreadCount = (latestMsgSeq - lastReadMsgSeq).toInt()
        return if (unreadCount > 0) unreadCount else 0
    }
    
    /**
     * 刷新消息
     */
    fun refreshMessages() {
        Log.d(tag, "Refreshing messages")
        oldestMsgSeq = 0
        oldestMsgId = null
        hasMoreMessages = true
        loadMessages(refresh = true)
    }
    
    /**
     * 刷新最新消息（下拉刷新使用）
     * 调用 list-msg API 获取最新的消息
     */
    fun refreshLatestMessages() {
        if (currentChatId.isEmpty()) {
            Log.w(tag, "Chat not initialized")
            return
        }

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isRefreshing = true, error = null)

                // 不带 msgId 参数，获取最新的消息
                val result = messageRepository.getMessages(
                    chatId = currentChatId,
                    chatType = currentChatType,
                    msgCount = DEFAULT_MSG_COUNT
                )

                result.fold(
                    onSuccess = { newMessages ->
                        Log.d(tag, "Refreshed ${newMessages.size} latest messages")
                        
                        val filteredMessages = newMessages.filter { message ->
                            val isBlocked = kotlin.runCatching {
                                blocklistRepository.isUserBlocked(message.sender.chatId)
                            }.getOrElse { false }
                            !isBlocked
                        }
                        
                        if (filteredMessages.isNotEmpty()) {
                            // 合并新消息到现有消息列表
                            val existingMsgIds = _messages.map { it.msgId }.toSet()
                            val uniqueNewMessages = filteredMessages.filter { it.msgId !in existingMsgIds }
                            
                            if (uniqueNewMessages.isNotEmpty()) {
                                // 添加不重复的新消息
                                _messages.addAll(uniqueNewMessages.sortedBy { it.sendTime })
                                Log.d(tag, "Added ${uniqueNewMessages.size} new messages")
                            }
                            
                            // 按发送时间重新排序所有消息
                            val sortedMessages = _messages.sortedBy { it.sendTime }
                            _messages.clear()
                            _messages.addAll(sortedMessages)
                        }

                        _uiState.value = _uiState.value.copy(isRefreshing = false, error = null)
                    },
                    onFailure = { exception ->
                        Log.e(tag, "Failed to refresh latest messages", exception)
                        _uiState.value = _uiState.value.copy(
                            isRefreshing = false,
                            error = "刷新失败: ${exception.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                Log.e(tag, "Exception refreshing latest messages", e)
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    error = "刷新异常: ${e.message}"
                )
            }
        }
    }

    /**
     * 获取当前用户ID
     */
    fun getCurrentUserId(): String = currentUserId

    /**
     * 检查消息是否来自当前用户
     * 使用direction字段判断：right=自己发送，left=对方发送
     */
    fun isMyMessage(message: ChatMessage): Boolean {
        return when {
            currentUserId.isNotBlank() && message.sender.chatId.isNotBlank() -> {
                message.sender.chatId == currentUserId
            }
            else -> message.direction == "right"
        }
    }
    
    /**
     * 获取当前用户的权限等级
     */
    fun getCurrentUserPermission(): Int {
        val currentUser = _uiState.value.groupMembers[currentUserId]
        return currentUser?.permissionLevel
            ?: _uiState.value.groupInfo?.permissionLevel
            ?: 0
    }
    
    /**
     * 检查消息是否正在流式接收中
     */
    fun isMessageStreaming(msgId: String): Boolean {
        return streamingMessages.containsKey(msgId)
    }
    
    /**
     * 清除流式消息缓存（当流式消息完成时调用）
     */
    fun clearStreamingMessage(msgId: String) {
        streamingMessages.remove(msgId)
        streamingMessageContentTypes.remove(msgId)
        Log.d(tag, "Cleared streaming message: $msgId")
    }

    private fun normalizeMessageOwnership(message: ChatMessage): ChatMessage {
        if (currentUserId.isBlank() || message.sender.chatId.isBlank()) {
            return message
        }
        val normalizedDirection = if (message.sender.chatId == currentUserId) "right" else "left"
        return if (message.direction == normalizedDirection) {
            message
        } else {
            message.copy(direction = normalizedDirection)
        }
    }

    private fun resolveMessageChatId(message: ChatMessage): String? {
        val chatId = message.chatId?.takeIf { it.isNotBlank() }
        if (message.chatType != 1) {
            return chatId
        }

        if (currentUserId.isNotBlank()) {
            if (message.sender.chatId == currentUserId) {
                return message.recvId?.takeIf { it.isNotBlank() } ?: chatId
            }
            if (message.sender.chatId.isNotBlank()) {
                return message.sender.chatId
            }
        }

        return chatId ?: message.recvId?.takeIf { it.isNotBlank() }
    }

    private fun refreshEditedMessageFromApi(
        patchMessage: ChatMessage,
        fallbackMessage: ChatMessage
    ) {
        val resolvedChatId = fallbackMessage.chatId
            ?.takeIf { it.isNotBlank() }
            ?: patchMessage.chatId?.takeIf { it.isNotBlank() }
            ?: resolveMessageChatId(fallbackMessage)
            ?: resolveMessageChatId(patchMessage)
        val resolvedChatType = fallbackMessage.chatType
            ?.takeIf { it > 0 }
            ?: patchMessage.chatType?.takeIf { it > 0 }
            ?: currentChatType.takeIf { resolvedChatId == currentChatId }

        if (resolvedChatId.isNullOrBlank() || resolvedChatType == null || resolvedChatType <= 0) {
            Log.w(
                tag,
                "Skip refreshing edited message from API: msgId=${patchMessage.msgId}, " +
                    "chatId=$resolvedChatId, chatType=$resolvedChatType"
            )
            return
        }

        viewModelScope.launch {
            val latestMessage = messageRepository.getMessageByIdFromApi(
                messageId = patchMessage.msgId,
                chatId = resolvedChatId,
                chatType = resolvedChatType
            )

            if (latestMessage == null) {
                Log.w(
                    tag,
                    "Edited message refresh from API returned null: msgId=${patchMessage.msgId}, " +
                        "chatId=$resolvedChatId, chatType=$resolvedChatType"
                )
                return@launch
            }

            val normalizedLatestMessage = normalizeMessageOwnership(latestMessage)
            val latestIndex = _messages.indexOfFirst { it.msgId == normalizedLatestMessage.msgId }
            if (latestIndex == -1) {
                Log.d(tag, "Edited message refreshed from API but not present in UI list: ${normalizedLatestMessage.msgId}")
                return@launch
            }

            _messages[latestIndex] = normalizedLatestMessage
            streamingMessages.remove(normalizedLatestMessage.msgId)
            streamingMessageContentTypes.remove(normalizedLatestMessage.msgId)
            Log.d(
                tag,
                "Replaced edited message with API payload: msgId=${normalizedLatestMessage.msgId}, " +
                    "text=${normalizedLatestMessage.content.text?.take(80)}, " +
                    "contentType=${normalizedLatestMessage.contentType}, " +
                    "editTime=${normalizedLatestMessage.editTime}"
            )
        }
    }
    
    /**
     * 上报按钮点击事件
     */
    fun reportButtonClick(
        chatId: String,
        chatType: Int,
        msgId: String,
        buttonValue: String
    ) {
        viewModelScope.launch {
            try {
                val userId = tokenRepository.getUserId() ?: ""
                messageRepository.reportButtonClick(
                    chatId = chatId,
                    chatType = chatType,
                    msgId = msgId,
                    userId = userId,
                    buttonValue = buttonValue
                )
                Log.d(tag, "Button click reported successfully: msgId=$msgId, value=$buttonValue")
            } catch (e: Exception) {
                Log.e(tag, "Failed to report button click", e)
            }
        }
    }
    
    /**
     * 屏蔽用户
     */
    fun blockUser(userId: String, userName: String, avatarUrl: String?) {
        viewModelScope.launch {
            try {
                blocklistRepository.setBlocklistEnabled(true)

                blocklistRepository.blockUser(
                    userId = userId,
                    userName = userName,
                    avatarUrl = avatarUrl
                )
                Log.d(tag, "Blocked user: $userId ($userName)")
                
                // 从当前消息列表中移除该用户的所有消息
                val beforeSize = _messages.size
                _messages.removeAll { it.sender.chatId == userId }
                val removedCount = beforeSize - _messages.size
                if (removedCount > 0) {
                    Log.d(tag, "Removed $removedCount messages from blocked user: $userId")
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to block user", e)
                _uiState.value = _uiState.value.copy(error = "屏蔽用户失败: ${e.message}")
            }
        }
    }
    
    /**
     * ViewModel被清理时保存读取位置
     */
    override fun onCleared() {
        chatPrefs.unregisterOnSharedPreferenceChangeListener(chatPrefsListener)
        super.onCleared()
        // 保存当前读取位置，防止用户从后台直接结束应用
        saveCurrentReadPosition()
        Log.d(tag, "ChatViewModel cleared, read position saved")
    }
}

private fun ChatMessage.mergeEditedMessage(edited: ChatMessage): ChatMessage {
    return copy(
        sender = sender.mergeEditedSender(edited.sender),
        direction = edited.direction.takeIf { it.isNotBlank() && edited.sender.chatId.isNotBlank() } ?: direction,
        contentType = edited.contentType.takeIf { it != 0 } ?: contentType,
        content = content.mergeEditedContent(edited.content),
        sendTime = edited.sendTime.takeIf { it > 0 } ?: sendTime,
        cmd = edited.cmd ?: cmd,
        msgDeleteTime = edited.msgDeleteTime ?: msgDeleteTime,
        quoteMsgId = edited.quoteMsgId.orExistingIfBlank(quoteMsgId),
        msgSeq = edited.msgSeq.orExistingIfNonPositive(msgSeq),
        editTime = edited.editTime.orExistingIfNonPositive(editTime),
        chatId = edited.chatId.orExistingIfBlank(chatId),
        chatType = edited.chatType.orExistingIfNonPositive(chatType),
        recvId = edited.recvId.orExistingIfBlank(recvId)
    )
}

private fun com.yhchat.canary.data.model.MessageContent.mergeEditedContent(
    edited: com.yhchat.canary.data.model.MessageContent
): com.yhchat.canary.data.model.MessageContent {
    return copy(
        text = edited.text.orExistingIfBlank(text),
        buttons = edited.buttons.orExistingIfBlank(buttons),
        imageUrl = edited.imageUrl.orExistingIfBlank(imageUrl),
        fileName = edited.fileName.orExistingIfBlank(fileName),
        fileUrl = edited.fileUrl.orExistingIfBlank(fileUrl),
        form = edited.form.orExistingIfBlank(form),
        quoteMsgText = edited.quoteMsgText.orExistingIfBlank(quoteMsgText),
        quoteImageUrl = edited.quoteImageUrl.orExistingIfBlank(quoteImageUrl),
        quoteImageName = edited.quoteImageName.orExistingIfBlank(quoteImageName),
        quoteVideoUrl = edited.quoteVideoUrl.orExistingIfBlank(quoteVideoUrl),
        quoteVideoTime = edited.quoteVideoTime.orExistingIfNonPositive(quoteVideoTime),
        stickerUrl = edited.stickerUrl.orExistingIfBlank(stickerUrl),
        postId = edited.postId.orExistingIfBlank(postId),
        postTitle = edited.postTitle.orExistingIfBlank(postTitle),
        postContent = edited.postContent.orExistingIfBlank(postContent),
        postContentType = edited.postContentType.orExistingIfBlank(postContentType),
        expressionId = edited.expressionId.orExistingIfBlank(expressionId),
        fileSize = edited.fileSize.orExistingIfNonPositive(fileSize),
        videoUrl = edited.videoUrl.orExistingIfBlank(videoUrl),
        audioUrl = edited.audioUrl.orExistingIfBlank(audioUrl),
        audioTime = edited.audioTime.orExistingIfNonPositive(audioTime),
        stickerItemId = edited.stickerItemId.orExistingIfNonPositive(stickerItemId),
        stickerPackId = edited.stickerPackId.orExistingIfNonPositive(stickerPackId),
        callText = edited.callText.orExistingIfBlank(callText),
        callStatusText = edited.callStatusText.orExistingIfBlank(callStatusText),
        width = edited.width.orExistingIfNonPositive(width),
        height = edited.height.orExistingIfNonPositive(height)
    )
}

private fun com.yhchat.canary.data.model.MessageSender.mergeEditedSender(
    edited: com.yhchat.canary.data.model.MessageSender
): com.yhchat.canary.data.model.MessageSender {
    if (edited.chatId.isBlank()) {
        return this
    }

    return copy(
        chatId = edited.chatId,
        chatType = edited.chatType.takeIf { it > 0 } ?: chatType,
        name = edited.name.orExistingIfBlank(name) ?: name,
        avatarUrl = edited.avatarUrl.orExistingIfBlank(avatarUrl) ?: avatarUrl,
        tagOld = edited.tagOld.takeUnless { it.isNullOrEmpty() } ?: tagOld,
        tag = edited.tag.takeUnless { it.isNullOrEmpty() } ?: tag
    )
}

private fun String?.orExistingIfBlank(existing: String?): String? {
    return this?.takeIf { it.isNotBlank() } ?: existing
}

private fun Int?.orExistingIfNonPositive(existing: Int?): Int? {
    return this?.takeIf { it > 0 } ?: existing
}

private fun Long?.orExistingIfNonPositive(existing: Long?): Long? {
    return this?.takeIf { it > 0 } ?: existing
}
