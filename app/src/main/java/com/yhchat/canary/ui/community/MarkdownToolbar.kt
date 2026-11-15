package com.yhchat.canary.ui.community

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

/**
 * Markdown 工具栏上的一个按钮：把 [prefix] / [suffix] 包住光标所在内容。
 *
 * 单独抽出来是为了让「有没有选中内容」的处理只有一份：
 *   - 有选中 → 把选中那段用符号包起来，光标落在符号外面
 *   - 没选中 → 插入一对符号，光标停在中间，直接开始打字
 *
 * 这是所有编辑器工具栏的通用行为，逐处重复写容易写得不一样。
 */
private data class MarkdownAction(
    val icon: ImageVector,
    val label: String,
    val prefix: String,
    val suffix: String = prefix
)

/** 工具栏上按顺序排的按钮。顺序跟常见 Markdown 编辑器一致。 */
private val MarkdownActions = listOf(
    MarkdownAction(Icons.Default.Title, "标题", "# ", ""),
    MarkdownAction(Icons.Default.FormatBold, "粗体", "**", "**"),
    MarkdownAction(Icons.Default.FormatItalic, "斜体", "*", "*"),
    MarkdownAction(Icons.Default.FormatStrikethrough, "删除线", "~~", "~~"),
    MarkdownAction(Icons.Default.FormatQuote, "引用", "> ", ""),
    MarkdownAction(Icons.Default.FormatListBulleted, "列表", "- ", ""),
    MarkdownAction(Icons.Default.Code, "代码", "`", "`"),
    MarkdownAction(Icons.Default.Link, "链接", "[", "](url)")
)

/**
 * 正文输入框的底部 Markdown 工具条。
 *
 * 只在编辑正文（[visible]）时出现，浮在内容最上层，键盘弹出时靠外层
 * `imePadding()` 自动上提到键盘上沿。
 *
 * 按钮不可聚焦：点了之后正文输入框仍保持焦点，工具栏不会因为失焦而消失，
 * 光标位置也保留着 —— 这是"在光标处插入"能成立的前提。
 *
 * @param value 当前正文，用来决定替换范围
 * @param onValueChange 回写新的正文和光标位置
 * @param visible 是否显示
 * @param onPickImagesClick 点图片按钮时触发；由调用方拉起系统图片选择器
 */
@Composable
internal fun MarkdownToolbar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    visible: Boolean,
    onPickImagesClick: (() -> Unit)? = null
) {
    if (!visible) return

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(MarkdownToolbarHeight),
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 按钮多了可能超出一行，允许横向滚动而不是挤在一起
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            MarkdownActions.forEach { action ->
                ToolbarButton(
                    icon = action.icon,
                    label = action.label,
                    onClick = { onValueChange(applyMarkdown(value, action)) }
                )
            }

            // 图片放在最后，和格式化按钮之间留一点视觉区隔
            if (onPickImagesClick != null) {
                ToolbarButton(
                    icon = Icons.Default.Image,
                    label = "插入图片",
                    onClick = onPickImagesClick
                )
            }
        }
    }
}

/**
 * 工具条上的单个按钮。
 *
 * 关键：`Modifier.clickable` 而不是 IconButton —— IconButton 默认可聚焦，
 * 点它会把焦点从 TextField 抢走，工具栏随即因为 `contentFocused == false` 隐藏，
 * 用户就再也点不到下一个按钮了。
 */
@Composable
private fun ToolbarButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(MarkdownToolbarHeight),
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(ToolbarIconSize)
        )
    }
}

/**
 * 工具条高度。
 *
 * 比 Material 最小可点尺寸（48dp）矮，比正常工具条（56dp）更矮，
 * 让它明显是「附属工具」。32dp 是在"不误触"和"省屏幕"之间的折中。
 *
 * 对外可见：发布页要按这个高度给正文留出底部间距，否则最后几行会被盖住。
 */
internal val MarkdownToolbarHeight = 32.dp

/** 图标尺寸：跟着工具条一起缩小，但保持可辨识 */
private val ToolbarIconSize = 16.dp

/**
 * 把 [action] 应用到 [value] 上，返回新的值和光标位置。
 *
 * 两种情况分开处理：
 *   - 有选中：`selection` 首尾都有内容，用符号包住，光标放到包好那段之后
 *   - 无选中：插入一对符号，光标停在两个符号中间
 *
 * 前缀本来以空格结尾的（`# `、`> `、`- `）不再加后缀，否则会变成 `# 文字# `，
 * 这种语法不成立。
 */
private fun applyMarkdown(value: TextFieldValue, action: MarkdownAction): TextFieldValue {
    val text = value.text
    val start = value.selection.min
    val end = value.selection.max

    return if (start != end) {
        // 有选中：包住选中段
        val selected = text.substring(start, end)
        val wrapped = action.prefix + selected + action.suffix
        val newText = text.replaceRange(start, end, wrapped)
        TextFieldValue(
            text = newText,
            // 光标放到包好的整段之后，接着往下写
            selection = TextRange(start + wrapped.length)
        )
    } else {
        // 无选中：插入一对符号，光标落在中间
        val inserted = action.prefix + action.suffix
        val newText = text.replaceRange(start, start, inserted)
        TextFieldValue(
            text = newText,
            selection = TextRange(start + action.prefix.length)
        )
    }
}

/**
 * 在当前光标位置插入一段任意文本（用于插入图片语法），
 * 插完把光标放到插入内容之后。
 *
 * 单独抽出来是因为图片插入是异步的：用户选完图、上传完成时，
 * 光标早就不是点按钮那一刻的位置了。调用方可以在上传前记下 selection，
 * 上传完成后用这个函数插到原位置。
 */
internal fun insertAtCursor(
    value: TextFieldValue,
    content: String,
    cursorOverride: Int? = null
): TextFieldValue {
    val insertPos = (cursorOverride ?: value.selection.min)
        .coerceIn(0, value.text.length)

    // 前后各补一个换行，避免图片语法和相邻文字粘在一起导致不渲染
    val needsLeadingBreak = insertPos > 0 && value.text[insertPos - 1] != '\n'
    val needsTrailingBreak = insertPos < value.text.length && value.text[insertPos] != '\n'

    val payload = buildString {
        if (needsLeadingBreak) append('\n')
        append(content)
        if (needsTrailingBreak) append('\n')
    }

    val newText = value.text.replaceRange(insertPos, insertPos, payload)
    return TextFieldValue(
        text = newText,
        selection = TextRange(insertPos + payload.length)
    )
}
