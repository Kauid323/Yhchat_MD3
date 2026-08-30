package com.yhchat.canary.data.websocket

import android.util.Log
import com.yhchat.canary.data.model.ChatMessage
import com.yhchat.canary.data.repository.ConversationRepository
import com.yhchat.canary.data.repository.MessageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WebSocket管理器 - 协调WebSocket服务和数据存储
 * 负责处理收到的消息并更新本地数据
 */
@Singleton
@OptIn(FlowPreview::class)
class WebSocketManager @Inject constructor(
    private val webSocketService: WebSocketService,
    private val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository
) {
    private val tag = "WebSocketManager"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    init {
        // 在初始化时就开始持续监听WebSocket消息
        // 使用SupervisorJob确保一个子协程失败不会影响其他协程
        scope.launch {
            webSocketService.messageEvents.collect { event ->
                // Log.d(tag, "Received message event: ${event::class.simpleName}")
                handleMessageEvent(event)
            }
        }
        
        // 持续监听会话更新事件
        scope.launch {
            webSocketService.conversationUpdates.collect { update ->
                // Log.d(tag, "Received conversation update: ${update::class.simpleName}")
                handleConversationUpdate(update)
            }
        }
        
        // 持续监听草稿更新事件并保存到本地
        scope.launch {
            webSocketService.draftUpdates
                .debounce(500) // 防抖500ms，避免频繁保存
                .collect { draftUpdate ->
                    // Log.d(tag, "📝 保存远程草稿到本地: chatId=${draftUpdate.chatId}")
                    handleDraftUpdate(draftUpdate)
                }
        }
        
        // Log.d(tag, "WebSocketManager initialized with persistent listeners")
    }
    
    /**
     * 连接WebSocket
     */
    suspend fun connect(userId: String, platform: String = "android") {
        // Log.d(tag, "Connecting WebSocket for user: $userId")
        webSocketService.connect(userId, platform)
    }
    
    /**
     * 断开WebSocket连接
     */
    fun disconnect() {
        webSocketService.disconnect()
    }
    
    /**
     * 发送草稿同步
     */
    fun sendDraftInput(chatId: String, input: String) {
        webSocketService.sendDraftInput(chatId, input)
    }
    
    /**
     * 获取连接状态流
     */
    fun getConnectionState(): SharedFlow<ConnectionState> {
        return webSocketService.connectionState
    }
    
    /**
     * 获取消息事件流 - 供UI监听
     */
    fun getMessageEvents(): SharedFlow<MessageEvent> {
        return webSocketService.messageEvents
    }
    
    /**
     * 获取会话更新流 - 供会话列表UI监听
     */
    fun getConversationUpdates(): SharedFlow<ConversationUpdate> {
        return webSocketService.conversationUpdates
    }
    
    /**
     * 获取草稿更新流 - 供聊天界面UI监听（多端同步）
     */
    fun getDraftUpdates(): SharedFlow<DraftUpdate> {
        return webSocketService.draftUpdates
    }
    
    /**
     * 清除指定会话的通知历史
     */
    fun clearNotificationHistory(chatId: String, chatType: Int) {
        webSocketService.clearNotificationHistory(chatId, chatType)
    }
    
    /**
     * 清除头像缓存
     */
    fun clearAvatarCache() {
        webSocketService.clearAvatarCache()
    }
    
    /**
     * 处理消息事件
     */
    private suspend fun handleMessageEvent(event: MessageEvent) {
        try {
            when (event) {
                is MessageEvent.NewMessage -> {
                    // Log.d(tag, "Handling new message: ${event.message.msgId}")
                    
                    // 将新消息插入到本地数据库/缓存
                    insertMessageToLocal(event.message)
                }
                
                is MessageEvent.MessageEdited -> {
                    // Log.d(tag, "Handling edited message: ${event.message.msgId}")
                    
                    // 更新本地消息
                    updateLocalMessage(event.message)
                }
                
                is MessageEvent.MessageDeleted -> {
                    // Log.d(tag, "Handling deleted message: ${event.msgId}")
                    
                    // 删除本地消息
                    deleteLocalMessage(event.msgId)
                }
                
                is MessageEvent.DraftUpdated -> {
                    // Log.d(tag, "Handling draft update for chat: ${event.chatId}")
                    
                    // 可以在这里处理草稿同步，比如更新UI状态
                    // 暂时只记录日志
                }
                
                is MessageEvent.BotBoardMessage -> {
                    // Log.d(tag, "Handling bot board message from: ${event.botId}")
                    
                    // 处理机器人公告消息
                    // 可以显示为系统消息或特殊通知
                }
                
                is MessageEvent.StreamMessage -> {
                    // Log.d(tag, "Handling stream message update: msgId=${event.msgId}, chatId=${event.chatId}")
                    // 流式消息由前台实时渲染，无需写入本地数据库
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error handling message event", e)
        }
    }
    
    /**
     * 处理草稿更新（多端同步）
     */
    private suspend fun handleDraftUpdate(draftUpdate: com.yhchat.canary.data.websocket.DraftUpdate) {
        // 事件已经通过flow转发给ChatViewModel处理了，这里不需要做任何事
        // Log.d(tag, "草稿更新: chatId=${draftUpdate.chatId}")
    }
    
    /**
     * 处理会话更新
     */
    private suspend fun handleConversationUpdate(update: ConversationUpdate) {
        try {
            when (update) {
                is ConversationUpdate.NewMessage -> {
                    // Log.d(tag, "Updating conversation for new message from: ${update.message.sender.chatId}")
                    
                    // 更新会话的最后一条消息和时间
                    updateConversationLastMessage(update.message)
                }
                
                is ConversationUpdate.MessageEdited -> {
                    // Log.d(tag, "Updating conversation for edited message: ${update.message.msgId}")
                    
                    // 如果编辑的是最后一条消息，需要更新会话显示
                    updateConversationIfLastMessage(update.message)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error handling conversation update", e)
        }
    }
    
    /**
     * 将新消息插入到本地存储
     */
    private suspend fun insertMessageToLocal(message: ChatMessage) {
        try {
            // 直接插入新消息到本地数据库（Dao 使用 REPLACE 策略，避免额外查库）
            messageRepository.insertMessage(message)
            // Log.d(tag, "Inserted new message: ${message.msgId}")
        } catch (e: Exception) {
            Log.e(tag, "Error inserting message to local storage", e)
        }
    }
    
    /**
     * 更新本地消息
     */
    private suspend fun updateLocalMessage(message: ChatMessage) {
        try {
            messageRepository.updateMessage(message)
            // Log.d(tag, "Updated local message: ${message.msgId}")
        } catch (e: Exception) {
            Log.e(tag, "Error updating local message", e)
        }
    }
    
    /**
     * 删除本地消息
     */
    private suspend fun deleteLocalMessage(msgId: String) {
        try {
            messageRepository.deleteMessage(msgId)
            // Log.d(tag, "Deleted local message: $msgId")
        } catch (e: Exception) {
            Log.e(tag, "Error deleting local message", e)
        }
    }
    
    /**
     * 更新会话的最后一条消息
     */
    private suspend fun updateConversationLastMessage(message: ChatMessage) {
        try {
            // 根据文档说明，只有当recvId和chatId相等时，才创建/更新与发送者的私聊会话
            // 否则，应该更新chatId对应的群聊会话
            val isPrivateChat = message.chatId == message.recvId
            
            if (isPrivateChat) {
                // 私聊消息或机器人对话：创建/更新与发送者的私聊会话
                val targetChatId = message.sender.chatId
                val targetChatType = message.sender.chatType
                
                // Log.d(tag, "Updating private conversation:")
                // Log.d(tag, "  - Private chat with: ${message.sender.chatId} (${message.sender.name})")
                // Log.d(tag, "  - Message preview: ${getMessagePreview(message)}")
                
                conversationRepository.updateLastMessage(
                    chatId = targetChatId,
                    chatType = targetChatType,
                    lastMessage = getMessagePreview(message),
                    lastMessageTime = message.sendTime,
                    unreadCount = 1
                )
                
                // Log.d(tag, "Successfully updated private conversation: $targetChatId")
                
            } else {
                // 群聊消息：更新群聊会话，不创建私聊会话
                val targetChatId = message.chatId!!
                val targetChatType = message.chatType!!
                
                // Log.d(tag, "Updating group conversation:")
                // Log.d(tag, "  - Group: $targetChatId (type: $targetChatType)")
                // Log.d(tag, "  - Sender: ${message.sender.chatId} (${message.sender.name})")
                // Log.d(tag, "  - Message preview: ${getMessagePreview(message)}")
                
                conversationRepository.updateLastMessage(
                    chatId = targetChatId,
                    chatType = targetChatType,
                    lastMessage = getMessagePreview(message),
                    lastMessageTime = message.sendTime,
                    unreadCount = 1
                )
                
                // Log.d(tag, "Successfully updated group conversation: $targetChatId")
            }
            
        } catch (e: Exception) {
            Log.e(tag, "Error updating conversation last message", e)
        }
    }
    
    /**
     * 如果是最后一条消息则更新会话
     */
    private suspend fun updateConversationIfLastMessage(message: ChatMessage) {
        try {
            // 使用正确的chatId和chatType
            val targetChatId = message.chatId ?: message.sender.chatId
            val targetChatType = message.chatType ?: message.sender.chatType
            
            // 检查是否是会话的最后一条消息
            val lastMessage = messageRepository.getLastMessage(
                targetChatId, 
                targetChatType
            )
            
            if (lastMessage?.msgId == message.msgId) {
                // 是最后一条消息，更新会话显示
                conversationRepository.updateLastMessage(
                    chatId = targetChatId,
                    chatType = targetChatType,
                    lastMessage = getMessagePreview(message),
                    lastMessageTime = message.sendTime,
                    unreadCount = null // 不改变未读数
                )
                
                // Log.d(tag, "Updated conversation for edited last message: ${message.msgId}")
            }
            
        } catch (e: Exception) {
            Log.e(tag, "Error checking and updating conversation for edited message", e)
        }
    }
    
    /**
     * 获取消息预览文本
     */
    private fun getMessagePreview(message: ChatMessage): String {
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
            !message.content.audioUrl.isNullOrEmpty() -> "[语音]"
            !message.content.videoUrl.isNullOrEmpty() -> "[视频]"
            !message.content.stickerUrl.isNullOrEmpty() -> "[表情]"
            !message.content.postTitle.isNullOrEmpty() -> "[文章]${message.content.postTitle}"
            else -> "[消息]"
        }
        
        // 确定目标会话类型
        val targetChatType = message.chatType ?: message.sender.chatType
        
        // 根据会话类型决定显示格式
        return when (targetChatType) {
            2, 3 -> {


                "${message.sender.name}：$contentPreview"
            }
            else -> {
                // 私聊(1)或其他：只显示内容
                contentPreview
            }
        }
    }
    
    /**
     * 销毁管理器
     */
    fun destroy() {
        scope.cancel()
        webSocketService.destroy()
    }
}
