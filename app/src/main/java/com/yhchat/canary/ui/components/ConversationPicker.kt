package com.yhchat.canary.ui.components

import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.yhchat.canary.ui.contacts.ContactsViewModel
import com.yhchat.canary.ui.share.SendToChatTarget
import kotlinx.coroutines.launch

/**
 * 可嵌入页面或底部面板的会话选择器。选中状态由调用方持有；null 上限表示不限数量。
 * 调用方应提供有界高度，让联系人列表独立滚动。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationPicker(
    selected: Set<SendToChatTarget>,
    onSelectionChange: (Set<SendToChatTarget>) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    maxSelection: Int? = null,
    viewModel: ContactsViewModel = hiltViewModel(),
    showSelectionSummary: Boolean = true
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val prefs = remember(context) {
        context.getSharedPreferences("send_to_chat_settings", Context.MODE_PRIVATE)
    }
    val tabs = remember { listOf("好友", "群聊", "机器人") }
    val pagerState = rememberPagerState(
        initialPage = prefs.getInt("selected_tab", 0).coerceIn(0, tabs.lastIndex),
        pageCount = { tabs.size }
    )
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(pagerState.settledPage) {
        prefs.edit().putInt("selected_tab", pagerState.settledPage).apply()
    }

    fun toggle(target: SendToChatTarget) {
        if (!enabled) return
        if (target in selected) {
            onSelectionChange(selected - target)
        } else if (maxSelection != null && selected.size >= maxSelection) {
            Toast.makeText(context, "最多选择 $maxSelection 个会话", Toast.LENGTH_SHORT).show()
        } else {
            onSelectionChange(selected + target)
        }
    }

    Column(modifier = modifier) {
        SecondaryTabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = androidx.compose.ui.graphics.Color.Transparent
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    enabled = enabled,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(title) }
                )
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("搜索名称或会话 ID") },
            enabled = enabled,
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )
        if (showSelectionSummary) Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (maxSelection == null) "已选择 ${selected.size} 个会话"
                    else "已选择 ${selected.size}/$maxSelection 个会话",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { onSelectionChange(emptySet()) }, enabled = enabled && selected.isNotEmpty()) {
                Text("清空")
            }
        }
        if (state.isLoading) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = enabled,
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) { page ->
                val source = when (page) {
                    0 -> state.friends
                    1 -> state.groups
                    else -> state.bots
                }
                val contacts = remember(source, query) {
                    val keyword = query.trim()
                    source.filter {
                        keyword.isEmpty() || it.name.contains(keyword, ignoreCase = true) ||
                            it.chatId.contains(keyword, ignoreCase = true)
                    }.distinctBy { SendToChatTarget(it.chatId, it.chatType) }
                }
                if (contacts.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = state.error ?: if (query.isBlank()) "暂无会话" else "没有匹配的会话",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (state.error != null) {
                            TextButton(onClick = { viewModel.loadContacts() }, enabled = enabled) { Text("重试") }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(contacts, key = { "${it.chatType}_${it.chatId}" }) { contact ->
                            val target = SendToChatTarget(contact.chatId, contact.chatType)
                            val checked = target in selected
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                    .toggleable(
                                        value = checked,
                                        enabled = enabled,
                                        role = Role.Checkbox,
                                        onValueChange = { toggle(target) }
                                    ).padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
                                Spacer(Modifier.width(16.dp))
                                AsyncImage(
                                    model = ImageUtils.createAvatarImageRequest(context, contact.avatarUrl),
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = contact.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
