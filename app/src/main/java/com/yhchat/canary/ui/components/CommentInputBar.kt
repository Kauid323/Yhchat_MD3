package com.yhchat.canary.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * 评论输入栏组件
 * 可复用的评论发送组件，支持贴附在虚拟按键上方
 */
@Composable
fun CommentInputBar(
    commentText: String,
    onCommentTextChange: (String) -> Unit,
    onSendComment: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "写下你的评论...",
    isVisible: Boolean = true,
    isEnabled: Boolean = true,
    maxLines: Int = 5,
    minLines: Int = 1
) {
    if (isVisible) {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = commentText,
                    onValueChange = onCommentTextChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(placeholder) },
                    minLines = minLines,
                    maxLines = maxLines,
                    enabled = isEnabled,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Send,
                        keyboardType = KeyboardType.Text
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (commentText.isNotBlank()) {
                                val processedContent = commentText.trim()
                                onSendComment(processedContent)
                            }
                        }
                    )
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                IconButton(
                    onClick = {
                        if (commentText.isNotBlank()) {
                            val processedContent = commentText.trim()
                            onSendComment(processedContent)
                        }
                    },
                    enabled = commentText.isNotBlank() && isEnabled
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "发送评论",
                        tint = if (commentText.isNotBlank() && isEnabled)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 底部固定的评论输入栏
 * 贴附在虚拟按键上方，使用 WindowInsets 处理键盘遮挡
 */
@Composable
fun BottomCommentInputBar(
    commentText: String,
    onCommentTextChange: (String) -> Unit,
    onSendComment: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "写下你的评论...",
    isVisible: Boolean = true,
    isEnabled: Boolean = true,
    maxLines: Int = 5,
    minLines: Int = 1
) {
    if (isVisible) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .windowInsetsPadding(WindowInsets.ime),
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = commentText,
                    onValueChange = onCommentTextChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(placeholder) },
                    minLines = minLines,
                    maxLines = maxLines,
                    enabled = isEnabled,
                    shape = MaterialTheme.shapes.large,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Send,
                        keyboardType = KeyboardType.Text
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (commentText.isNotBlank()) {
                                val processedContent = commentText.trim()
                                onSendComment(processedContent)
                            }
                        }
                    )
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                FloatingActionButton(
                    onClick = {
                        if (commentText.isNotBlank()) {
                            val processedContent = commentText.trim()
                            onSendComment(processedContent)
                        }
                    },
                    modifier = Modifier.size(48.dp),
                    containerColor = if (commentText.isNotBlank() && isEnabled) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "发送评论",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
