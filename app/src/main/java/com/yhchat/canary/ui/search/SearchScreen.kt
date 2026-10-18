@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.yhchat.canary.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.yhchat.canary.R

/**
 * 搜索界面
 */
@Composable
fun SearchScreen(
    onBackClick: () -> Unit,
    onItemClick: (chatId: String, chatType: Int, chatName: String) -> Unit,
    viewModel: SearchViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val uiState by viewModel.uiState.collectAsState()
    val searchResult by viewModel.searchResult.collectAsState()
    var searchText by remember { mutableStateOf("") }

    // 处理系统返回键/手势返回
    BackHandler {
        onBackClick()
    }
    


    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
        TopAppBar(
            title = {
                Text(
                    text = "搜索",
                    fontWeight = FontWeight.SemiBold
                )
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回"
                    )
                }
            },
            actions = {
                IconButton(onClick = { viewModel.search(searchText) }) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "搜索"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                scrolledContainerColor = MaterialTheme.colorScheme.background
            )
        )
        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            placeholder = { Text("搜索用户、群组和机器人") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    viewModel.search(searchText)
                }
            ),
            trailingIcon = {
                if (searchText.isNotEmpty()) {
                    IconButton(onClick = {
                        searchText = ""
                        viewModel.clearSearch()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "清除"
                        )
                    }
                }
            }
        )

        // 错误信息
        uiState.error?.let { error ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        // 搜索结果
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            searchResult?.let { result ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    // 根据API文档，result.list包含不同类别的搜索结果
                    result.list.forEach { category ->
                        category.list?.let { items ->
                            if (items.isNotEmpty()) {
                                item {
                                    Text(
                                        text = category.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                                itemsIndexed(items, key = { index, item -> "${item.friendId}_${item.friendType}_$index" }) { _, searchItem ->
                                    Card(

                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 4.dp)
                                            .clickable { 
                                                // 所有类型都直接进入聊天
                                                onItemClick(
                                                    searchItem.friendId,
                                                    searchItem.friendType,
                                                    searchItem.nickname
                                                )
                                            },
                                        shape = RoundedCornerShape(24.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(context)
                                                    .data(searchItem.avatarUrl)
                                                    .addHeader("Referer", "https://myapp.jwznb.com")
                                                    .crossfade(true)
                                                    .placeholder(R.drawable.ic_launcher_foreground)
                                                    .error(R.drawable.ic_launcher_foreground)
                                                    .build(),
                                                contentDescription = searchItem.nickname,
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(CircleShape),
                                                contentScale = ContentScale.Crop
                                            )
                                            
                                            Spacer(modifier = Modifier.width(12.dp))
                                            
                                            Column(
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(
                                                    text = searchItem.nickname,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                
                                                // 显示对应的ID
                                                val idLabel = when (searchItem.friendType) {
                                                    1 -> "用户ID"
                                                    2 -> "群ID"
                                                    3 -> "机器人ID"
                                                    else -> "ID"
                                                }
                                                Text(
                                                    text = "$idLabel: ${searchItem.friendId}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                
                                                if (searchItem.name?.isNotEmpty() == true) {
                                                    Text(
                                                        text = searchItem.name,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            
                                            // 显示类型标签
                                            Surface(
                                                color = when (searchItem.friendType) {
                                                    1 -> MaterialTheme.colorScheme.primaryContainer
                                                    2 -> MaterialTheme.colorScheme.secondaryContainer
                                                    3 -> MaterialTheme.colorScheme.tertiaryContainer
                                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                                },
                                                shape = MaterialTheme.shapes.small
                                            ) {
                                                Text(
                                                    text = when (searchItem.friendType) {
                                                        1 -> "用户"
                                                        2 -> "群组"
                                                        3 -> "机器人"
                                                        else -> "未知"
                                                    },
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = when (searchItem.friendType) {
                                                        1 -> MaterialTheme.colorScheme.onPrimaryContainer
                                                        2 -> MaterialTheme.colorScheme.onSecondaryContainer
                                                        3 -> MaterialTheme.colorScheme.onTertiaryContainer
                                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } ?: run {
                // 初始状态
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "输入关键词开始搜索",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        }
    }
}
