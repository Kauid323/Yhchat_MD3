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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yhchat.canary.data.model.ChatMessage
import com.yhchat.canary.ui.chat.ChatViewModel
import org.json.JSONArray
import org.json.JSONObject

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

    // 支持旧格式二维数组，以及新格式扁平按钮数组；JSON解析结果与输入同生命周期缓存。
    val buttonData = remember(buttonsJson) {
        parseButtonRows(buttonsJson)
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
                        Button(
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
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = textColor.copy(alpha = 0.15f),
                                contentColor = textColor
                            )
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
 * 兼容扁平按钮数组（视为单行）和既有二维数组（每个子数组作为一行）。
 * 无效元素单独跳过，避免一个坏元素导致同组其他按钮丢失。
 */
private fun parseButtonRows(buttonsJson: String): List<List<ButtonData>> {
    return try {
        val root = JSONArray(buttonsJson)
        val rows = mutableListOf<List<ButtonData>>()
        val flatRow = mutableListOf<ButtonData>()

        fun parseButtonOrNull(item: Any?): ButtonData? {
            if (item !is JSONObject) return null
            return try {
                val text = item.opt("text") as? String ?: return null
                val actionType = item.optInt("actionType", 0)
                // 不为缺失的按钮内容生成默认按钮。
                if (text.isBlank() || actionType !in 1..3) return null
                ButtonData(
                    text = text,
                    actionType = actionType,
                    url = item.optString("url", ""),
                    value = item.optString("value", "")
                )
            } catch (e: Exception) {
                android.util.Log.w("MessageButtons", "Skipping invalid button entry", e)
                null
            }
        }

        fun appendNestedRow(row: JSONArray) {
            val buttons = mutableListOf<ButtonData>()
            for (index in 0 until row.length()) {
                parseButtonOrNull(row.opt(index))?.let(buttons::add)
            }
            if (buttons.isNotEmpty()) rows.add(buttons)
        }

        for (index in 0 until root.length()) {
            when (val item = root.opt(index)) {
                is JSONObject -> parseButtonOrNull(item)?.let(flatRow::add)
                is JSONArray -> {
                    if (flatRow.isNotEmpty()) {
                        rows.add(flatRow.toList())
                        flatRow.clear()
                    }
                    appendNestedRow(item)
                }
                // 忽略 null、字符串、数字等无效条目，但继续解析其他按钮。
            }
        }

        if (flatRow.isNotEmpty()) rows.add(flatRow.toList())
        rows
    } catch (e: Exception) {
        android.util.Log.e("MessageButtons", "Failed to parse buttons JSON", e)
        emptyList()
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
            // 将按钮的value作为button_report回调上报；不在客户端推断并执行撤回等操作。
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
