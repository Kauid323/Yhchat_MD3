package com.yhchat.canary.data.repository

import android.content.Context
import com.yhchat.canary.data.local.AppDatabase
import com.yhchat.canary.data.local.CachedConversation
import com.yhchat.canary.data.local.CachedConversationDao
import com.yhchat.canary.data.local.CachedMessage
import com.yhchat.canary.data.local.CachedMessageDao
import com.yhchat.canary.data.model.ChatMessage
import com.yhchat.canary.data.model.Conversation
import com.yhchat.canary.data.model.MessageContent
import com.yhchat.canary.data.model.MessageSender
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 缓存仓库 - 管理本地加密数据库
 */
@Singleton
class CacheRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    
    private val database: AppDatabase
        get() = AppDatabase.getDatabase(context)
    private val conversationDao: CachedConversationDao
        get() = database.cachedConversationDao()
    private val messageDao: CachedMessageDao
        get() = database.cachedMessageDao()
    
    // ========== 会话缓存 ==========
    
    /**
     * 获取所有缓存的会话
     */
    fun getCachedConversations(userId: String? = null): Flow<List<Conversation>> {
        val dao = if (!userId.isNullOrBlank()) {
            AppDatabase.getDatabase(context, userId).cachedConversationDao()
        } else {
            conversationDao
        }
        return dao.getAllConversations().map { cachedList ->
            cachedList.map { cached ->
                Conversation(
                    chatId = cached.chatId,
                    chatType = cached.chatType,
                    name = cached.name,
                    chatContent = cached.chatContent,
                    timestampMs = cached.timestampMs,
                    timestamp = cached.timestamp,
                    unreadMessage = cached.unreadMessage,
                    at = cached.at,
                    avatarUrl = cached.avatarUrl,
                    doNotDisturb = cached.doNotDisturb,
                    certificationLevel = cached.certificationLevel
                )
            }
        }
    }
    
    /**
     * 同步获取所有缓存的会话
     */
    suspend fun getCachedConversationsSync(userId: String? = null): List<Conversation> {
        val dao = if (!userId.isNullOrBlank()) {
            AppDatabase.getDatabase(context, userId).cachedConversationDao()
        } else {
            conversationDao
        }
        return dao.getAllConversationsSync().map { cached ->
            Conversation(
                chatId = cached.chatId,
                chatType = cached.chatType,
                name = cached.name,
                chatContent = cached.chatContent,
                timestampMs = cached.timestampMs,
                timestamp = cached.timestamp,
                unreadMessage = cached.unreadMessage,
                at = cached.at,
                avatarUrl = cached.avatarUrl,
                doNotDisturb = cached.doNotDisturb,
                certificationLevel = cached.certificationLevel
            )
        }
    }
    
    /**
     * 缓存会话列表
     */
    suspend fun cacheConversations(conversations: List<Conversation>, userId: String? = null) {
        val dao = if (!userId.isNullOrBlank()) {
            AppDatabase.getDatabase(context, userId).cachedConversationDao()
        } else {
            conversationDao
        }
        val cachedConversations = conversations.map { conversation ->
            CachedConversation(
                chatId = conversation.chatId,
                chatType = conversation.chatType,
                name = conversation.name,
                chatContent = conversation.chatContent,
                timestampMs = conversation.timestampMs,
                timestamp = conversation.timestamp,
                unreadMessage = conversation.unreadMessage,
                at = conversation.at,
                avatarUrl = conversation.avatarUrl,
                doNotDisturb = conversation.doNotDisturb,
                certificationLevel = conversation.certificationLevel
            )
        }
        dao.insertConversations(cachedConversations)
    }
    
    /**
     * 更新会话的最后消息
     */
    suspend fun updateConversationLastMessage(chatId: String, lastMessage: String, timestamp: Long) {
        conversationDao.updateLastMessage(chatId, lastMessage, timestamp)
    }

    /**
     * 更新会话免打扰状态
     */
    suspend fun updateConversationDoNotDisturb(chatId: String, doNotDisturb: Int) {
        conversationDao.updateDoNotDisturb(chatId, doNotDisturb)
    }
    
    /**
     * 标记会话为已读
     */
    suspend fun markConversationAsRead(chatId: String) {
        conversationDao.markAsRead(chatId)
    }
    
    /**
     * 从缓存中删除会话
     */
    suspend fun deleteConversationFromCache(chatId: String) {
        // 先查找会话
        val conversation = conversationDao.getAllConversationsSync()
            .find { it.chatId == chatId }
        
        if (conversation != null) {
            // 删除会话
            conversationDao.deleteConversation(conversation)
            
            // 同时删除相关消息
            messageDao.clearChatMessages(chatId)
        }
    }
    
    // ========== 消息缓存 ==========
    
    /**
     * 获取缓存的消息
     */
    fun getCachedMessages(chatId: String): Flow<List<ChatMessage>> {
        return messageDao.getMessages(chatId).map { cachedList ->
            cachedList.map { cached ->
                ChatMessage(
                    msgId = cached.msgId,
                    sender = MessageSender(
                        chatId = cached.senderChatId,
                        chatType = cached.chatType,
                        name = cached.senderName,
                        avatarUrl = cached.senderAvatarUrl
                    ),
                    direction = cached.direction,
                    contentType = cached.contentType,
                    content = MessageContent(
                        text = cached.contentText,
                        imageUrl = cached.contentImageUrl,
                        fileName = cached.contentFileName,
                        fileUrl = cached.contentFileUrl,
                        quoteMsgText = cached.quoteMsgText,
                        quoteImageUrl = cached.quoteImageUrl
                    ),
                    sendTime = cached.sendTime,
                    msgSeq = cached.msgSeq,
                    editTime = cached.editTime,
                    msgDeleteTime = cached.msgDeleteTime,
                    quoteMsgId = cached.quoteMsgId
                )
            }
        }
    }
    
    /**
     * 同步获取缓存的消息
     */
    suspend fun getCachedMessagesSync(chatId: String): List<ChatMessage> {
        return messageDao.getMessagesSync(chatId).map { cached ->
            ChatMessage(
                msgId = cached.msgId,
                sender = MessageSender(
                    chatId = cached.senderChatId,
                    chatType = cached.chatType,
                    name = cached.senderName,
                    avatarUrl = cached.senderAvatarUrl
                ),
                direction = cached.direction,
                contentType = cached.contentType,
                content = MessageContent(
                    text = cached.contentText,
                    imageUrl = cached.contentImageUrl,
                    fileName = cached.contentFileName,
                    fileUrl = cached.contentFileUrl,
                    quoteMsgText = cached.quoteMsgText,
                    quoteImageUrl = cached.quoteImageUrl
                ),
                sendTime = cached.sendTime,
                msgSeq = cached.msgSeq,
                editTime = cached.editTime,
                msgDeleteTime = cached.msgDeleteTime,
                quoteMsgId = cached.quoteMsgId
            )
        }
    }
    
    /**
     * 缓存消息
     */
    suspend fun cacheMessage(message: ChatMessage) {
        // 判断消息类型并确定存储位置
        val isPrivateChat = message.chatId == message.recvId
        val targetChatId: String
        val targetChatType: Int
        
        if (isPrivateChat) {
            // 私聊消息：存储在与发送者的私聊中
            targetChatId = message.sender.chatId
            targetChatType = message.sender.chatType
        } else {
            // 群聊消息：存储在群聊中
            targetChatId = message.chatId!!
            targetChatType = message.chatType!!
        }
        
        val cachedMessage = CachedMessage(
            msgId = message.msgId,
            chatId = targetChatId,
            chatType = targetChatType,
            senderChatId = message.sender.chatId,
            senderName = message.sender.name,
            senderAvatarUrl = message.sender.avatarUrl,
            direction = message.direction,
            contentType = message.contentType,
            contentText = message.content.text,
            contentImageUrl = message.content.imageUrl,
            contentFileName = message.content.fileName,
            contentFileUrl = message.content.fileUrl,
            quoteMsgText = message.content.quoteMsgText,
            quoteImageUrl = message.content.quoteImageUrl,
            sendTime = message.sendTime,
            msgSeq = message.msgSeq,
            editTime = message.editTime,
            msgDeleteTime = message.msgDeleteTime,
            quoteMsgId = message.quoteMsgId
        )
        messageDao.insertMessage(cachedMessage)
    }
    
    /**
     * 批量缓存消息
     */
    suspend fun cacheMessages(messages: List<ChatMessage>) {
        val cachedMessages = messages.map { message ->
            // 判断消息类型并确定存储位置
            val isPrivateChat = message.chatId == message.recvId
            val targetChatId: String
            val targetChatType: Int
            
            if (isPrivateChat) {
                // 私聊消息：存储在与发送者的私聊中
                targetChatId = message.sender.chatId
                targetChatType = message.sender.chatType
            } else {
                // 群聊消息：存储在群聊中
                targetChatId = message.chatId!!
                targetChatType = message.chatType!!
            }
            
            CachedMessage(
                msgId = message.msgId,
                chatId = targetChatId,
                chatType = targetChatType,
                senderChatId = message.sender.chatId,
                senderName = message.sender.name,
                senderAvatarUrl = message.sender.avatarUrl,
                direction = message.direction,
                contentType = message.contentType,
                contentText = message.content.text,
                contentImageUrl = message.content.imageUrl,
                contentFileName = message.content.fileName,
                contentFileUrl = message.content.fileUrl,
                quoteMsgText = message.content.quoteMsgText,
                quoteImageUrl = message.content.quoteImageUrl,
                sendTime = message.sendTime,
                msgSeq = message.msgSeq,
                editTime = message.editTime,
                msgDeleteTime = message.msgDeleteTime,
                quoteMsgId = message.quoteMsgId
            )
        }
        messageDao.insertMessages(cachedMessages)
    }
    
    /**
     * 清理旧消息（超过30天）
     */
    suspend fun cleanOldMessages() {
        val thirtyDaysAgo = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L)
        messageDao.deleteOldMessages(thirtyDaysAgo)
    }
    
    /**
     * 通过消息ID获取消息
     */
    suspend fun getMessageById(msgId: String): ChatMessage? {
        val cachedMessage = messageDao.getMessageById(msgId) ?: return null
        
        return ChatMessage(
            msgId = cachedMessage.msgId,
            sender = MessageSender(
                chatId = cachedMessage.senderChatId,
                chatType = cachedMessage.chatType,
                name = cachedMessage.senderName,
                avatarUrl = cachedMessage.senderAvatarUrl
            ),
            direction = cachedMessage.direction,
            contentType = cachedMessage.contentType,
            content = MessageContent(
                text = cachedMessage.contentText,
                imageUrl = cachedMessage.contentImageUrl,
                fileName = cachedMessage.contentFileName,
                fileUrl = cachedMessage.contentFileUrl,
                quoteMsgText = cachedMessage.quoteMsgText,
                quoteImageUrl = cachedMessage.quoteImageUrl
            ),
            sendTime = cachedMessage.sendTime,
            msgSeq = cachedMessage.msgSeq,
            editTime = cachedMessage.editTime,
            msgDeleteTime = cachedMessage.msgDeleteTime,
            quoteMsgId = cachedMessage.quoteMsgId
        )
    }
    
    /**
     * 删除指定消息
     */
    suspend fun deleteMessage(msgId: String) {
        messageDao.deleteMessage(msgId)
    }
    
    /**
     * 获取会话的最后一条消息
     */
    suspend fun getLastMessage(chatId: String, chatType: Int): ChatMessage? {
        val cachedMessage = messageDao.getLastMessage(chatId, chatType) ?: return null
        
        return ChatMessage(
            msgId = cachedMessage.msgId,
            sender = MessageSender(
                chatId = cachedMessage.senderChatId,
                chatType = cachedMessage.chatType,
                name = cachedMessage.senderName,
                avatarUrl = cachedMessage.senderAvatarUrl
            ),
            direction = cachedMessage.direction,
            contentType = cachedMessage.contentType,
            content = MessageContent(
                text = cachedMessage.contentText,
                imageUrl = cachedMessage.contentImageUrl,
                fileName = cachedMessage.contentFileName,
                fileUrl = cachedMessage.contentFileUrl,
                quoteMsgText = cachedMessage.quoteMsgText,
                quoteImageUrl = cachedMessage.quoteImageUrl
            ),
            sendTime = cachedMessage.sendTime,
            msgSeq = cachedMessage.msgSeq,
            editTime = cachedMessage.editTime,
            msgDeleteTime = cachedMessage.msgDeleteTime,
            quoteMsgId = cachedMessage.quoteMsgId
        )
    }
    
    /**
     * 清除所有缓存数据
     */
    suspend fun clearAllCache() {
        conversationDao.clearAllConversations()
        messageDao.clearAllMessages()
    }
}
