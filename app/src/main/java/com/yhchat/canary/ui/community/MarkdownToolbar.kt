package com.yhchat.canary.ui.community

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatStrikethrough
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
 * 正文输入框上方的 Markdown 工具条。
 *
 * 高度压到 [MarkdownToolbarHeight]，只在编辑正文（[visible]）时出现。
 * 图标用 Material3 自带的，不引额外资源 —— 这些符号没有官方专门的 markdown 图标，
 * 但 FormatBold / FormatItalic / Title 这类本来就是干这个的，认起来没障碍。
 *
 * @param value 当前正文，用来决定替换范围
 * @param onValueChange 回写新的正文和光标位置
 * @param visible 是否显示。键盘没弹、输入框没聚焦时收起来，不占屏幕
 */
@Composable
internal fun MarkdownToolbar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    visible: Boolean
) {
    if (!visible) return

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            // 贴在输入框上方，所以上边给圆角、下边贴平
            .height(MarkdownToolbarHeight),
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            MarkdownActions.forEach { action ->
                IconButton(
                    onClick = { onValueChange(applyMarkdown(value, action)) },
                    modifier = Modifier.size(MarkdownToolbarHeight),
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = action.label,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * 工具条高度。
 *
 * 40dp 是 Material 的最小可点尺寸，再小按钮就不好按了；比正常 48dp 矮一档，
 * 让它明显是「附属工具」而不是主操作区。
 */
private val MarkdownToolbarHeight = 40.dp

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