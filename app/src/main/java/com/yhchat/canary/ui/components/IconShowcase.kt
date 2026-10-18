package com.yhchat.canary.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 图标展示界面 - 用于查看所有可用的图标
 */
@Composable
fun IconShowcase(
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 聊天图标
        item {
            IconCategory(
                title = "聊天图标",
                icons = IconSelector.chatIcons
            )
        }
        
        // 社区图标
        item {
            IconCategory(
                title = "社区图标",
                icons = IconSelector.communityIcons
            )
        }
        
        // 用户图标
        item {
            IconCategory(
                title = "用户图标",
                icons = IconSelector.userIcons
            )
        }
        
        // 功能图标
        item {
            IconCategory(
                title = "功能图标",
                icons = IconSelector.functionIcons
            )
        }
        
        // 导航图标
        item {
            IconCategory(
                title = "导航图标",
                icons = IconSelector.navigationIcons
            )
        }
        
        // 状态图标
        item {
            IconCategory(
                title = "状态图标",
                icons = IconSelector.statusIcons
            )
        }
    }
}

/**
 * 图标分类组件
 */
@Composable
private fun IconCategory(
    title: String,
    icons: List<Pair<String, ImageVector>>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(icons, key = { it.first }) { (name, icon) ->
                    IconItem(
                        name = name,
                        icon = icon
                    )
                }
            }
        }
    }
}

/**
 * 单个图标项
 */
@Composable
private fun IconItem(
    name: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = name,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
