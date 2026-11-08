package com.yhchat.canary.ui.community

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 评论输入面板。
 *
 * 参照 Oolong 的回复面板思路：不在页面底部常驻一个输入条，而是**按需弹起**一大块区域，
 * 让键盘和输入区一起把屏幕下半部分占满。理由：
 *   - 文章详情的正文和评论都要滚，常驻输入条会一直压住内容；
 *   - 弹起式能给出足够大的输入区，写长评论不会只看到一行；
 *   - 点「回复某条评论」时，弹起来的那块能带上「回复 @某人」的上下文。
 *
 * @param replyTo 回复目标。null 表示评论文章本身；非 null 表示回复某条评论
 * @param sending 发送中，用来禁用按钮和显示状态
 * @param onSend 点发送，传出正文
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CommentComposerSheet(
    replyTo: ReplyTarget?,
    sending: Boolean,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // 面板要装键盘和输入区，不要圆角那种小卡片观感
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            // 回复目标提示。评论文章时不显示，回复某条评论时说明回复谁
            replyTo?.let { target ->
                ReplyTargetChip(target = target, onClear = null)
                Spacer(modifier = Modifier.height(10.dp))
            }

            // 输入区。给一个足够大的高度，长评论能一眼看到好几行
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp)
                        .padding(14.dp)
                        .focusRequester(focusRequester),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        if (text.isEmpty()) {
                            Text(
                                text = replyTo?.let { "回复 ${it.name}" } ?: "说点什么…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        inner()
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 底部操作：字数 + 发送
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = "${text.length}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))

                FilledIconButton(
                    onClick = {
                        val content = text.trim()
                        if (content.isNotEmpty() && !sending) {
                            onSend(content)
                        }
                    },
                    enabled = text.isNotBlank() && !sending,
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "发送",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

    // 弹起来就聚焦：不聚焦等于多一次点击
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
}

/**
 * 回复目标的小标签。
 *
 * 只显示「回复 @名字」一行，长名字省略 —— 面板里空间要留给输入区，
 * 这里不需要展示被回复的原文（原文在上面的评论列表里就在眼前）。
 */
@Composable
private fun ReplyTargetChip(
    target: ReplyTarget,
    onClear: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Text(
                text = "回复 ${target.name}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        if (onClear != null) {
            IconButton(
                onClick = onClear,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "取消回复",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * 回复目标：回复哪条评论，以及那条评论的作者名。
 *
 * 只带 id 和名字，不引用整条评论对象 —— 评论列表会刷新（发完评论会重载第 1 页），
 * 持有对象会让面板里显示的内容跟列表脱节。
 */
internal data class ReplyTarget(
    val commentId: Int,
    val name: String
)