package com.yhchat.canary.ui.chat.ChatComponents

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.yhchat.canary.ui.chat.ChatViewModel
import com.yhchat.canary.ui.components.FloatingVoiceWindow
import kotlinx.coroutines.launch

/**
 * TTS 浮动窗口组件
 */
@Composable
fun TtsFloatingWindow(
    visible: Boolean,
    chatId: String,
    chatType: Int,
    quotedMessageId: String?,
    quotedMessageText: String?,
    quotedImageUrl: String?,
    quotedImageName: String?,
    quotedVideoUrl: String?,
    quotedVideoTime: Long?,
    viewModel: ChatViewModel,
    onClose: () -> Unit,
    onQuoteClear: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    if (visible) {
        FloatingVoiceWindow(
            chatId = chatId,
            chatType = chatType,
            onClose = onClose,
            onSendAudio = { audioUri ->
                // 发送合成的音频
                android.util.Log.d("TtsFloatingWindow", "发送TTS合成的音频: $audioUri")
                
                // 调用 ViewModel 上传并发送音频
                coroutineScope.launch {
                    try {
                        viewModel.uploadAndSendAudio(
                            context = context,
                            audioUri = audioUri,
                            quoteMsgId = quotedMessageId,
                            quoteMsgText = quotedMessageText,
                            quoteImageUrl = quotedImageUrl,
                            quoteImageName = quotedImageName,
                            quoteVideoUrl = quotedVideoUrl,
                            quoteVideoTime = quotedVideoTime
                        )
                        // 清除引用状态
                        onQuoteClear()
                        
                        Toast.makeText(context, "语音消息已发送", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        android.util.Log.e("TtsFloatingWindow", "发送TTS音频失败", e)
                        Toast.makeText(context, "发送失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }
}
