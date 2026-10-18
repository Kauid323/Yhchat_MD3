@file:OptIn(ExperimentalMaterial3Api::class)
package com.yhchat.canary.ui.chat

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yhchat.canary.data.model.BotLlmParamValue
import com.yhchat.canary.proto.bot.board
import com.yhchat.canary.proto.group.Bot_data
import com.yhchat.canary.ui.components.ImageUtils
import com.yhchat.canary.ui.components.MarkdownText
import com.yhchat.canary.ui.components.htmltext.HtmlTextMessage

@Composable
fun BotBoardContent(
    boardData: board.Board_data,
    onImageClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val safeBoardContent = remember(boardData.content) { boardData.content.ifBlank { "[绌哄唴瀹筣" } }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            when (boardData.contentType) {
                1 -> {
                    SelectionContainer {
                        Text(
                            text = safeBoardContent,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                2 -> { // Markdown
                    MarkdownText(
                        markdown = safeBoardContent,
                        modifier = Modifier.fillMaxWidth(),
                        onImageClick = onImageClick
                    )
                }
                3 -> { // HTML - local Compose renderer
                    val htmlVerticalScroll = rememberScrollState()
                    val htmlHorizontalScroll = rememberScrollState()
                    HtmlTextMessage(
                        html = safeBoardContent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .horizontalScroll(htmlHorizontalScroll)
                            .verticalScroll(htmlVerticalScroll),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        onImageClick = onImageClick,
                        useAdvancedRenderer = true
                    )
                }
                else -> {
                    SelectionContainer {
                        Text(
                            text = safeBoardContent,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BotLlmParamsDialog(
    botName: String,
    params: List<BotLlmParamValue>,
    onDismiss: () -> Unit,
    onInputChange: (paramId: String, value: String) -> Unit,
    onSelectChange: (paramId: String, value: String) -> Unit
) {
    val scrollState = rememberScrollState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "${botName.ifBlank { "Bot" }} Params")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                params.forEach { item ->
                    val title = item.label.ifBlank { item.name.ifBlank { item.id } }
                    if (item.type.equals("select", ignoreCase = true)) {
                        val options = remember(item.options) {
                            item.options.split("#").map { it.trim() }.filter { it.isNotBlank() }
                        }
                        var optionsExpanded by remember { mutableStateOf(false) }
                        val selectedOption = options.getOrNull(options.indexOf(item.selectValue.orEmpty())).orEmpty()
                        ExposedDropdownMenuBox(
                            expanded = optionsExpanded,
                            onExpandedChange = { optionsExpanded = !optionsExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = selectedOption,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(title) },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = optionsExpanded)
                                },
                                modifier = Modifier
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = optionsExpanded,
                                onDismissRequest = { optionsExpanded = false }
                            ) {
                                options.forEachIndexed { index, option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            onSelectChange(item.id, option)
                                            optionsExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = item.value.orEmpty(),
                            onValueChange = { onInputChange(item.id, it) },
                            label = { Text(title) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("瀹屾垚")
            }
        }
    )
}

@Composable
fun GroupBotBoardsSection(
    groupBots: List<Bot_data>,
    groupBotBoards: Map<String, board.Board_data>,
    botLlmRefParams: Map<String, String>,
    botLlmParamValues: Map<String, List<BotLlmParamValue>>,
    onOpenBotLlmParams: (String) -> Unit,
    onImageClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val botsWithBoards = remember(groupBots, groupBotBoards) {
        groupBots.filter { bot ->
            groupBotBoards[bot.botId]?.content?.isNotBlank() == true
        }
    }

    val botsWithLlmParams = remember(groupBots, botLlmRefParams, botLlmParamValues) {
        groupBots.filter { bot ->
            botLlmRefParams.containsKey(bot.botId) && botLlmParamValues[bot.botId].orEmpty().isNotEmpty()
        }
    }

    if (botsWithBoards.isEmpty() && botsWithLlmParams.isEmpty()) {
        return
    }

    var selectedBotId by remember { mutableStateOf<String?>(null) }
    val selectedBoardData = remember(selectedBotId, groupBotBoards) {
        selectedBotId?.let { groupBotBoards[it] }
    }
    val selectedBoardKey = remember(selectedBotId, selectedBoardData) {
        selectedBoardData?.let { boardData ->
            boardContentIdentity(selectedBotId, boardData)
        }
    }

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
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(
                items = botsWithBoards,
                key = { index, bot ->
                    "group_bot_board_${bot.botId}_${bot.name}_${bot.avatarUrl}_${index}"
                }
            ) { _, bot ->
                val isSelected = selectedBotId == bot.botId

                FilterChip(
                    selected = isSelected,
                    onClick = {
                        selectedBotId = if (isSelected) null else bot.botId
                    },
                    label = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AsyncImage(
                                model = ImageUtils.createBotImageRequest(
                                    context = context,
                                    url = bot.avatarUrl
                                ),
                                contentDescription = bot.name,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )

                            Text(
                                text = bot.name,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    leadingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "宸查€変腑",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else null
                )
            }

            items(
                items = botsWithLlmParams,
                key = { bot -> "group_bot_llm_params_${bot.botId}_${bot.name}" }
            ) { bot ->
                FilterChip(
                    selected = false,
                    onClick = { onOpenBotLlmParams(bot.botId) },
                    label = {
                        val botName = botLlmRefParams[bot.botId].orEmpty().ifBlank { bot.name }
                        Text(
                            text = "${botName}的大模型参数",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }

        AnimatedVisibility(
            visible = selectedBoardData != null,
            enter = fadeIn(animationSpec = tween(140)),
            exit = fadeOut(animationSpec = tween(90))
        ) {
            selectedBoardData?.let { boardData ->
                key(selectedBoardKey) {
                    val contentScrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 500.dp)
                            .verticalScroll(contentScrollState)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "${boardData.botName.ifBlank{ "Bot" }}的看板",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )

                            TextButton(
                                onClick = {
                                    val content = boardData.content.ifBlank { "" }
                                    clipboardManager.setText(AnnotatedString(content))
                                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "澶嶅埗鍘熸枃",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("复制原文")
                            }

                            IconButton(
                                onClick = { selectedBotId = null },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "鏀惰捣",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        BotBoardContentScrollable(
                            boardData = boardData,
                            selectedBotId = selectedBotId,
                            onImageClick = onImageClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BotBoardContentScrollable(
    boardData: board.Board_data,
    selectedBotId: String?,
    onImageClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val boardKey = remember(selectedBotId, boardData) {
        boardContentIdentity(selectedBotId, boardData)
    }

    key(boardKey) {
        val scrollState = rememberScrollState()
        Box(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp)
                .verticalScroll(scrollState)
        ) {
            BotBoardContent(boardData = boardData, onImageClick = onImageClick)
        }
    }
}

private fun boardContentIdentity(
    selectedBotId: String?,
    boardData: board.Board_data
): String {
    return buildString {
        append(selectedBotId.orEmpty())
        append('|')
        append(boardData.botId)
        append('|')
        append(boardData.botName)
        append('|')
        append(boardData.contentType)
        append('|')
        append(boardData.content.hashCode())
    }
}
