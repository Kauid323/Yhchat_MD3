package com.yhchat.canary.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.yhchat.canary.R
import com.yhchat.canary.data.di.RepositoryFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 处理通知快捷回复的接收器
 */
class NotificationReplyReceiver : BroadcastReceiver() {
    
    private val tag = "NotificationReplyReceiver"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    companion object {
        const val KEY_TEXT_REPLY = "key_text_reply"
        const val EXTRA_CHAT_ID = "extra_chat_id"
        const val EXTRA_CHAT_TYPE = "extra_chat_type"
        const val EXTRA_CHAT_NAME = "extra_chat_name"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(tag, "📨 收到通知动作广播: ${intent.action}")
        
        // 处理"标记为已读"动作
        if (intent.action == "ACTION_MARK_AS_READ") {
            handleMarkAsRead(context, intent)
            return
        }
        
        // 获取用户输入的回复文本
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        val replyText = remoteInput?.getCharSequence(KEY_TEXT_REPLY)?.toString()
        
        if (replyText.isNullOrBlank()) {
            Log.w(tag, "⚠️ 回复文本为空")
            return
        }
        
        // 获取会话信息
        val chatId = intent.getStringExtra(EXTRA_CHAT_ID) ?: return
        val chatType = intent.getIntExtra(EXTRA_CHAT_TYPE, 1)
        val chatName = intent.getStringExtra(EXTRA_CHAT_NAME) ?: "会话"
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        
        Log.d(tag, "💬 快捷回复: chatId=$chatId, chatType=$chatType, text=$replyText")
        
        // 显示"正在发送"通知
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        showSendingNotification(context, notificationManager, notificationId, chatName)
        
        // 在后台发送消息
        val pendingResult = goAsync()
        
        scope.launch {
            try {
                val tokenRepository = RepositoryFactory.getTokenRepository(context)
                val messageRepository = RepositoryFactory.getMessageRepository(context)
                
                // 发送消息
                val result = messageRepository.sendMessage(
                    chatId = chatId,
                    chatType = chatType,
                    text = replyText,
                    contentType = 1 // 纯文本
                )
                
                result.fold(
                    onSuccess = {
                        Log.d(tag, "✅ 快捷回复发送成功: $replyText")
                        showSuccessNotification(context, notificationManager, notificationId, chatName, replyText)
                    },
                    onFailure = { error ->
                        Log.e(tag, "❌ 快捷回复发送失败", error)
                        showErrorNotification(context, notificationManager, notificationId, chatName, error.message)
                    }
                )
            } catch (e: Exception) {
                Log.e(tag, "❌ 快捷回复异常", e)
                showErrorNotification(context, notificationManager, notificationId, chatName, e.message)
            } finally {
                pendingResult.finish()
            }
        }
    }
    
    /**
     * 处理标记为已读动作
     */
    private fun handleMarkAsRead(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val chatId = intent.getStringExtra(EXTRA_CHAT_ID) ?: return
        
        Log.d(tag, "✓ 标记会话为已读: chatId=$chatId, notificationId=$notificationId")
        
        // 取消通知
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
        
        // 通知历史会在下次消息到来时自动清理，或在打开会话时清理
        Log.d(tag, "通知已取消，历史将在打开会话时清理")
    }
    
    /**
     * 显示"正在发送"通知
     */
    private fun showSendingNotification(
        context: Context,
        notificationManager: NotificationManager,
        notificationId: Int,
        chatName: String
    ) {
        val notification = NotificationCompat.Builder(context, "chat_messages")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(chatName)
            .setContentText("发送中...")
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        notificationManager.notify(notificationId, notification)
    }
    
    /**
     * 显示"发送成功"通知（使用MessagingStyle显示已发送的消息）
     */
    private fun showSuccessNotification(
        context: Context,
        notificationManager: NotificationManager,
        notificationId: Int,
        chatName: String,
        sentMessage: String
    ) {
        // 使用 MessagingStyle 来显示已发送的消息
        val me = androidx.core.app.Person.Builder()
            .setName("我")
            .build()
        
        val messagingStyle = NotificationCompat.MessagingStyle(me)
            .setConversationTitle(chatName)
            .addMessage(
                sentMessage,
                System.currentTimeMillis(),
                me // 显示为"我"发送的消息
            )
        
        val notification = NotificationCompat.Builder(context, "chat_messages")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setStyle(messagingStyle)
            .setContentText("消息已发送")
            .setTimeoutAfter(3000)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        notificationManager.notify(notificationId, notification)
    }
    
    /**
     * 显示"发送失败"通知
     */
    private fun showErrorNotification(
        context: Context,
        notificationManager: NotificationManager,
        notificationId: Int,
        chatName: String,
        errorMessage: String?
    ) {
        val notification = NotificationCompat.Builder(context, "chat_messages")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(chatName)
            .setContentText("发送失败: ${errorMessage ?: "未知错误"}")
            .setTimeoutAfter(5000)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        
        notificationManager.notify(notificationId, notification)
    }
}
