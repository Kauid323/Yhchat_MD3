@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.yhchat.canary.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState

/**
 * 引用消息显示栏
 */
@Composable
fun QuotedMessageBar(
    quotedText: String,
    onClearQuote: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：引用指示器
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(32.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(2.dp)
                    )
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 引用文本
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "引用消息",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = quotedText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // 右侧：关闭按钮
            IconButton(
                onClick = onClearQuote,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "取消引用",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * 附件菜单 - 全屏 BottomSheet 版本
 */
@Composable
fun AttachmentMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onImageClick: (() -> Unit)?,
    onFileClick: (() -> Unit)?,
    onCameraClick: (() -> Unit)?,
    onVideoClick: (() -> Unit)? = null,
    onTextClick: (() -> Unit)? = null,
    onHtmlClick: (() -> Unit)? = null,
    onMarkdownClick: (() -> Unit)? = null,
    onA2UiClick: (() -> Unit)? = null,
    defaultMessageType: Int = 1,
    onDefaultMessageTypeChange: ((Int) -> Unit)? = null,
    selectedMessageType: Int = 1
) {
    if (!expanded) return

    // 三个分组各自独立记忆展开状态：默认全部展开，收起/展开任意一个都不影响其它分组
    // （之前是一个 expandedSection 字符串 → 手风琴式，只能开一个）
    var attachmentsExpanded by remember { mutableStateOf(true) }
    var messageTypeExpanded by remember { mutableStateOf(true) }
    var defaultTypeExpanded by remember { mutableStateOf(true) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                // 全部展开时内容会超过一屏，所以这里要能滚
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 第一块：附件
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    AttachmentSectionHeader(
                        title = "附件",
                        expanded = attachmentsExpanded,
                        showDivider = false,
                        onClick = { attachmentsExpanded = !attachmentsExpanded }
                    )
                    AnimatedVisibility(
                        visible = attachmentsExpanded,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column {
                            AttachmentSheetRow("图片", Icons.Default.Image, onClick = { onImageClick?.invoke() })
                            AttachmentSheetRow("拍照", Icons.Default.CameraAlt, onClick = { onCameraClick?.invoke() })
                            AttachmentSheetRow("视频", Icons.Default.VideoLibrary, onClick = { onVideoClick?.invoke() })
                            AttachmentSheetRow(
                                "文件",
                                Icons.Default.AttachFile,
                                onClick = { onFileClick?.invoke() },
                                showDivider = false
                            )
                        }
                    }
                }
            }

            // 第二块：当前消息类型
            if (onTextClick != null && onHtmlClick != null && onMarkdownClick != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        AttachmentSectionHeader(
                            title = "当前消息类型",
                            expanded = messageTypeExpanded,
                            showDivider = false,
                            onClick = { messageTypeExpanded = !messageTypeExpanded }
                        )
                        AnimatedVisibility(
                            visible = messageTypeExpanded,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            Column {
                                AttachmentSheetRow(
                                    "文本",
                                    Icons.Default.TextFields,
                                    checked = selectedMessageType == 1,
                                    onClick = { onTextClick.invoke() }
                                )
                                AttachmentSheetRow(
                                    "Markdown",
                                    Icons.AutoMirrored.Filled.Article,
                                    checked = selectedMessageType == 3,
                                    onClick = { onMarkdownClick.invoke() }
                                )
                                AttachmentSheetRow(
                                    "HTML",
                                    Icons.Default.Code,
                                    checked = selectedMessageType == 8,
                                    onClick = { onHtmlClick.invoke() },
                                    showDivider = onA2UiClick != null
                                )
                                if (onA2UiClick != null) {
                                    AttachmentSheetRow(
                                        "A2UI",
                                        Icons.Default.Settings,
                                        checked = selectedMessageType == 14,
                                        onClick = { onA2UiClick.invoke() },
                                        showDivider = false
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 第三块：默认消息类型
            if (onDefaultMessageTypeChange != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        AttachmentSectionHeader(
                            title = "默认消息类型",
                            expanded = defaultTypeExpanded,
                            showDivider = false,
                            onClick = { defaultTypeExpanded = !defaultTypeExpanded }
                        )
                        AnimatedVisibility(
                            visible = defaultTypeExpanded,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            Column {
                                AttachmentSheetRow(
                                    "默认文本",
                                    Icons.Default.TextFields,
                                    checked = defaultMessageType == 1,
                                    onClick = { onDefaultMessageTypeChange.invoke(1) }
                                )
                                AttachmentSheetRow(
                                    "默认Markdown",
                                    Icons.AutoMirrored.Filled.Article,
                                    checked = defaultMessageType == 3,
                                    onClick = { onDefaultMessageTypeChange.invoke(3) }
                                )
                                AttachmentSheetRow(
                                    "默认HTML",
                                    Icons.Default.Code,
                                    checked = defaultMessageType == 8,
                                    onClick = { onDefaultMessageTypeChange.invoke(8) },
                                    showDivider = onA2UiClick != null
                                )
                                if (onA2UiClick != null) {
                                    AttachmentSheetRow(
                                        "默认A2UI",
                                        Icons.Default.Settings,
                                        checked = defaultMessageType == 14,
                                        onClick = { onDefaultMessageTypeChange.invoke(14) },
                                        showDivider = false
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentSectionHeader(
    title: String,
    expanded: Boolean,
    showDivider: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        if (showDivider) {
            HorizontalDivider(modifier = Modifier.padding(top = 14.dp))
        }
    }
}

@Composable
private fun AttachmentSheetRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean = false,
    showDivider: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            if (checked) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "已选中",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(modifier = Modifier.padding(top = 14.dp))
        }
    }
}

@Composable
private fun MenuCheckedIndicator(checked: Boolean) {
    Checkbox(
        checked = checked,
        onCheckedChange = null,
        enabled = false,
        modifier = Modifier.size(22.dp)
    )
}

