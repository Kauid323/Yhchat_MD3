package com.yhchat.canary.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yhchat.canary.data.model.NavigationItem

/**
 * 底部导航栏组件
 */
@Composable
fun BottomNavigationBar(
    currentScreen: String,
    visibleItems: List<NavigationItem>,
    onScreenChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier
    ) {
        visibleItems.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.getIcon(), contentDescription = item.title) },
                label = { Text(item.title) },
                selected = currentScreen == item.id,
                onClick = {
                    onScreenChange(item.id)
                }
            )
        }
    }
}
