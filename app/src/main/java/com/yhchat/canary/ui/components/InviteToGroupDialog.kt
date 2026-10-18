@file:OptIn(ExperimentalMaterial3Api::class)
package com.yhchat.canary.ui.components

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.yhchat.canary.data.di.RepositoryFactory
import com.yhchat.canary.data.repository.FriendRepository
import com.yhchat.canary.data.repository.GroupRepository
import com.yhchat.canary.ui.contacts.Contact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 邀请好友加入群聊对话框
 */
@Composable
fun InviteToGroupDialog(
    groupId: String,
    groupName: String,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val viewModel = remember { InviteDialogViewModel() }
    
    LaunchedEffect(Unit) {
        viewModel.init(context)
        viewModel.loadFriends()
    }
    
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    
    // 筛选好友
    val filteredFriends = remember(uiState.friends, searchQuery) {
        if (searchQuery.isBlank()) {
            uiState.friends
        } else {
            uiState.friends.filter { 
                it.name.contains(searchQuery, ignoreCase = true) || 
                it.chatId.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        val activity = LocalContext.current as? android.app.Activity
        val sheetColor = MaterialTheme.colorScheme.surface
        val darkIcons = sheetColor.luminance() > 0.5f
        if (activity != null) {
            com.yhchat.canary.ui.base.SystemBarUtils.ApplyNavigationBarColor(activity, sheetColor, darkIcons)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.95f)
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "邀请好友加入 $groupName",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
            }

            // 搜索框
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索好友...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "搜索")
                },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 内容区域
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                uiState.error != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = uiState.error ?: "加载失败",
                                color = MaterialTheme.colorScheme.error
                            )
                            TextButton(onClick = { viewModel.loadFriends() }) {
                                Text("重试")
                            }
                        }
                    }
                }

                filteredFriends.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isBlank()) "暂无好友" else "未找到匹配的好友",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredFriends, key = { it.chatId }) { friend ->
                            FriendInviteItem(
                                friend = friend,
                                isInviting = uiState.invitingFriendId == friend.chatId,
                                onClick = {
                                    viewModel.inviteFriend(
                                        chatId = friend.chatId,
                                        groupId = groupId,
                                        onSuccess = onSuccess
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 好友邀请项
 */
@Composable
private fun FriendInviteItem(
    friend: Contact,
    isInviting: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick, enabled = !isInviting),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 头像
            AsyncImage(
                model = ImageUtils.createAvatarImageRequest(context, friend.avatarUrl),
                contentDescription = friend.name,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = friend.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "ID: ${friend.chatId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // 邀请按钮/状态
            if (isInviting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Button(
                    onClick = onClick,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("邀请")
                }
            }
        }
    }
}

/**
 * 邀请对话框ViewModel
 */
class InviteDialogViewModel : ViewModel() {
    private lateinit var friendRepository: FriendRepository
    private lateinit var groupRepository: GroupRepository
    
    private val _uiState = MutableStateFlow(InviteDialogUiState())
    val uiState: StateFlow<InviteDialogUiState> = _uiState.asStateFlow()
    
    fun init(context: Context) {
        friendRepository = RepositoryFactory.getFriendRepository(context)
        groupRepository = RepositoryFactory.getGroupRepository(context)
    }
    
    fun loadFriends() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            friendRepository.getAddressBookList().fold(
                onSuccess = { data ->
                    // 提取好友列表
                    val friends = mutableListOf<Contact>()
                    data.dataList.forEach { group ->
                        if (group.listName == "好友" || group.listName == "用户") {
                            group.dataList.forEach { item ->
                                friends.add(
                                    Contact(
                                        chatId = item.chatId,
                                        name = item.name,
                                        avatarUrl = item.avatarUrl,
                                        chatType = 1
                                    )
                                )
                            }
                        }
                    }
                    
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        friends = friends
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message
                    )
                }
            )
        }
    }
    
    fun inviteFriend(chatId: String, groupId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(invitingFriendId = chatId)
            
            groupRepository.inviteToGroup(
                chatId = chatId,
                chatType = 1,  // 用户类型
                groupId = groupId
            ).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(invitingFriendId = null)
                    onSuccess()
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        invitingFriendId = null,
                        error = error.message
                    )
                }
            )
        }
    }
}

data class InviteDialogUiState(
    val isLoading: Boolean = false,
    val friends: List<Contact> = emptyList(),
    val invitingFriendId: String? = null,  // 正在邀请的好友ID
    val error: String? = null
)
