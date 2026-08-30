package com.yhchat.canary.ui.group

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import com.yhchat.canary.ui.base.BaseActivity
import com.yhchat.canary.ui.adaptive.YhSurface
import com.yhchat.canary.ui.theme.YhchatCanaryTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ChatSearchActivity : BaseActivity() {
    
    private val viewModel: ChatSearchViewModel by viewModels()
    
    companion object {
        const val EXTRA_CHAT_ID = "chatId"
        const val EXTRA_CHAT_TYPE = "chatType"
        const val EXTRA_CHAT_NAME = "chatName"
        
        fun start(context: android.content.Context, chatId: String, chatType: Int, chatName: String) {
            val intent = Intent(context, ChatSearchActivity::class.java).apply {
                putExtra(EXTRA_CHAT_ID, chatId)
                putExtra(EXTRA_CHAT_TYPE, chatType)
                putExtra(EXTRA_CHAT_NAME, chatName)
            }
            context.startActivity(intent)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // 增加字段适配转换，优先尝试原本的键名，若无则尝试下划线形式，确保兼容性
        val chatId = intent.getStringExtra(EXTRA_CHAT_ID) 
            ?: intent.getStringExtra("chat_id")
            ?: run {
                android.util.Log.e("ChatSearchActivity", "Missing chatId in intent")
                finish()
                return
            }
        
        val chatType = intent.getIntExtra(EXTRA_CHAT_TYPE, -1).takeIf { it != -1 }
            ?: intent.getIntExtra("chat_type", 2)
            
        val chatName = intent.getStringExtra(EXTRA_CHAT_NAME) 
            ?: intent.getStringExtra("chat_name")
            ?: "聊天"
        
        android.util.Log.d("ChatSearchActivity", "Opening chat search: id=$chatId, type=$chatType, name=$chatName")
        
        setContent {
            YhchatCanaryTheme {
                YhSurface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ChatSearchScreen(
                        chatId = chatId,
                        chatType = chatType,
                        chatName = chatName,
                        viewModel = viewModel,
                        onBackClick = { finish() },
                        onNavigateToChat = { targetChatId, targetChatType, targetChatName, msgId, msgSeq ->
                            // 启动ChatActivity并定位到目标消息
                            com.yhchat.canary.ui.chat.ChatActivity.startWithSearchTarget(
                                context = this@ChatSearchActivity,
                                chatId = targetChatId,
                                chatType = targetChatType,
                                chatName = targetChatName,
                                targetMsgId = msgId,
                                targetMsgSeq = msgSeq
                            )
                        }
                    )
                }
            }
        }
    }
}
