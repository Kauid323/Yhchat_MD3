package com.yhchat.canary.ui.chat.ChatComponents

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yhchat.canary.data.model.BotLlmParamValue
import com.yhchat.canary.proto.bot.board
import com.yhchat.canary.proto.group.Bot_data
import com.yhchat.canary.ui.chat.BotBoardContent
import com.yhchat.canary.ui.chat.ChatUiState
import com.yhchat.canary.ui.components.rememberBooleanPreference
import com.yhchat.canary.ui.chat.GroupBotBoardsSection as GroupBotBoardsSectionImpl

/**
 * 单个机器人看板区域（用于单聊）
 */
@Composable
fun SingleBotBoardSection(
    chatId: String,
    chatType: Int,
    uiState: ChatUiState,
    onOpenBotLlmParams: (String) -> Unit,
    onImageClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val botBoardEnabled by rememberBooleanPreference("chat_settings", "show_bot_board", true)

    var showBotBoard by remember { mutableStateOf(false) }

    if (chatType == 3 && botBoardEnabled) {
        val botBoard = uiState.botBoard
        val llmItem = uiState.botLlmRefParams.firstOrNull { it.botId == chatId }
        val boardData = remember(botBoard) { botBoard?.getBoardList()?.firstOrNull() }
        val hasBoardContent = boardData?.content?.isNotBlank() == true
        if (hasBoardContent || llmItem != null) {
                Column(
                    modifier = modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .animateContentSize(
                            animationSpec = tween(
                                durationMillis = 180,
                                easing = FastOutSlowInEasing
                            )
                        )
                ) {
                    // 展开/收起按钮
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showBotBoard = !showBotBoard }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "看板",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "机器人看板",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = if (showBotBoard) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (showBotBoard) "收起" else "展开",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    llmItem?.let {
                        TextButton(
                            onClick = { onOpenBotLlmParams(it.botId) },
                            modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)
                        ) {
                            Text(
                                text = "${it.botNickname.ifBlank { "机器人" }}的大模型参数",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    
                    // 看板内容（展开时显示）
                    AnimatedVisibility(
                        visible = showBotBoard && hasBoardContent,
                        enter = fadeIn(animationSpec = tween(140)),
                        exit = fadeOut(animationSpec = tween(90))
                    ) {
                        boardData?.let { data ->
                            BotBoardContent(
                                boardData = data,
                                onImageClick = onImageClick
                            )
                        }
                    }
                }
        }
    }
}

/**
 * 群聊中的机器人看板列表区域
 */
@Composable
fun GroupBotBoardsSection(
    chatType: Int,
    groupBots: List<Bot_data>,
    groupBotBoards: Map<String, board.Board_data>,
    botLlmRefParams: Map<String, String>,
    botLlmParamValues: Map<String, List<BotLlmParamValue>>,
    onOpenBotLlmParams: (String) -> Unit,
    onImageClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val botBoardEnabled by rememberBooleanPreference("chat_settings", "show_bot_board", true)
    
    if (chatType == 2 && groupBots.isNotEmpty() && botBoardEnabled) {
        GroupBotBoardsSectionImpl(
            groupBots = groupBots,
            groupBotBoards = groupBotBoards,
            botLlmRefParams = botLlmRefParams,
            botLlmParamValues = botLlmParamValues,
            onOpenBotLlmParams = onOpenBotLlmParams,
            onImageClick = onImageClick
        )
    }
}
