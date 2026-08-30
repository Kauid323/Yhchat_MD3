package com.yhchat.canary.ui.chat.ChatComponents

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import com.yhchat.canary.ui.adaptive.YhText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yhchat.canary.data.model.ChatMessage
import com.yhchat.canary.ui.adaptive.YhButton
import com.yhchat.canary.ui.chat.ChatViewModel
import org.json.JSONArray

/**
 * 消息按钮组件
 */
@Composable
fun MessageButtons(
    buttonsJson: String,
    message: ChatMessage,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: ChatViewModel = viewModel()
    
    // 在Composable外部解析JSON
    val buttonData = remember(buttonsJson) {
        try {
            val buttonRows = JSONArray(buttonsJson)
            val rows = mutableListOf<List<ButtonData>>()
            
            for (i in 0 until buttonRows.length()) {
                val buttonRow = buttonRows.getJSONArray(i)
                val buttons = mutableListOf<ButtonData>()
                
                for (j in 0 until buttonRow.length()) {
                    val button = buttonRow.getJSONObject(j)
                    buttons.add(
                        ButtonData(
                            text = button.optString("text", "按钮"),
                            actionType = button.optInt("actionType", 0),
                            url = button.optString("url", ""),
                            value = button.optString("value", "")
                        )
                    )
                }
                rows.add(buttons)
            }
            rows
        } catch (e: Exception) {
            android.util.Log.e("MessageButtons", "Failed to parse buttons JSON", e)
            emptyList()
        }
    }
    
    if (buttonData.isNotEmpty()) {
        Column(modifier = modifier) {
            Spacer(modifier = Modifier.height(8.dp))
            
            // 遍历每一行按钮
            buttonData.forEach { buttonRow ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 遍历每一行的按钮
                    buttonRow.forEach { btnData ->
                        YhButton(
                            onClick = {
                                handleButtonClick(
                                    context = context,
                                    viewModel = viewModel,
                                    message = message,
                                    actionType = btnData.actionType,
                                    url = btnData.url,
                                    value = btnData.value,
                                    buttonText = btnData.text
                                )
                            },
                            modifier = Modifier.weight(1f),
                            containerColor = textColor.copy(alpha = 0.15f),
                            contentColor = textColor,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = btnData.text,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 按钮数据类
 */
data class ButtonData(
    val text: String,
    val actionType: Int,
    val url: String,
    val value: String
)

/**
 * 处理按钮点击事件
 */
fun handleButtonClick(
    context: Context,
    viewModel: ChatViewModel,
    message: ChatMessage,
    actionType: Int,
    url: String,
    value: String,
    buttonText: String
) {
    when (actionType) {
        1 -> {
            // 打开URL
            if (url.isNotBlank()) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
                }
            }
        }
        2 -> {
            // 复制文本
            if (value.isNotBlank()) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("button_value", value)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
            }
        }
        3 -> {
            // 按钮事件上报（button_report）
            val chatId = message.chatId ?: ""
            val chatType = message.chatType ?: 1
            
            viewModel.reportButtonClick(
                chatId = chatId,
                chatType = chatType,
                msgId = message.msgId,
                buttonValue = value
            )
            
            val chatTypeText = when (chatType) {
                1 -> "私聊"
                2 -> "群聊"
                3 -> "机器人"
                else -> "未知"
            }
            android.util.Log.d("ButtonClick", "点击按钮: 类型=$chatTypeText, chatId=$chatId, 按钮值=$value")
            Toast.makeText(context, "已点击：$buttonText", Toast.LENGTH_SHORT).show()
        }
        else -> {
            Toast.makeText(context, "未知按钮类型", Toast.LENGTH_SHORT).show()
        }
    }
}
