package com.yhchat.canary.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * 顶栏的主操作按钮（MD3 Expressive 的"填充"圆形图标按钮）。
 *
 * 一个顶栏里只给**最常用**的那个操作用它，导航类的按钮（返回、侧边栏、关闭）保持无背景，
 * 这样视觉焦点就落在这个页面真正的主操作上。
 *
 * @param icon 图标
 * @param contentDescription 无障碍描述
 * @param onClick 点击回调
 * @param enabled 是否可点
 * @param loading 是否显示进度（比如"保存"中），此时不显示图标
 */
@Composable
fun TopBarActionButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    FilledIconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription
            )
        }
    }
}
