package com.yhchat.canary.ui.contacts

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhchat.canary.data.repository.FriendRepository
import com.yhchat.canary.data.repository.TokenRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 联系人数据
 */
data class Contact(
    val chatId: String,
    val name: String,
    val avatarUrl: String,
    val permissionLevel: Int = 0,
    val noDisturb: Boolean = false,
    val chatType: Int = 1 // 1-用户，2-群聊，3-机器人
)

/**
 * 联系人分组
 */
data class ContactGroup(
    val title: String,
    val contacts: List<Contact>
)

data class FriendRequestItem(
    val requestId: Int,
    val receiverName: String,
    val receiverAvatar: String,
    val name: String,
    val avatar: String,
    val groupName: String,
    val groupAvatar: String,
    val inviterId: String,
    val sourceType: Int,
    val targetType: Int,
    val targetId: String,
    val receiverId: String,
    val result: Int,
    val processedAt: Long,
    val inviteAt: Long,
    val inviteAtStr: String,
    val botName: String,
    val botAvatar: String,
    val processorName: String,
    val note: String
) {
    val usesGroupAgreeInvite: Boolean
        get() = inviterId.isNotBlank() && (
            sourceType == 2 || targetType == 2 || groupName.isNotBlank() ||
            sourceType == 3 || targetType == 3 || botName.isNotBlank()
        )

    val typeLabel: String
        get() = when {
            sourceType == 2 || targetType == 2 || groupName.isNotBlank() -> "群聊申请/邀请"
            sourceType == 3 || targetType == 3 || botName.isNotBlank() -> "机器人申请/邀请"
            else -> "好友申请"
        }

    val displayName: String
        get() = name.ifBlank {
            botName.ifBlank {
                groupName.ifBlank {
                    receiverName.ifBlank { "未知联系人" }
                }
            }
        }

    val displayAvatarUrl: String
        get() = avatar.ifBlank {
            botAvatar.ifBlank {
                groupAvatar.ifBlank { receiverAvatar }
            }
        }
}

/**
 * 通讯录UI状态
 */
data class ContactsUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val friends: List<Contact> = emptyList(),
    val groups: List<Contact> = emptyList(),
    val bots: List<Contact> = emptyList(),
    val myBots: List<Contact> = emptyList(),  // 我创建的机器人
    val friendRequests: List<FriendRequestItem> = emptyList(),
    val friendRequestTotal: Int = 0,
    val friendRequestPending: Int = 0,
    val friendRequestsLoading: Boolean = false,
    val friendRequestsExpanded: Boolean = false,
    val selectedFriendRequest: FriendRequestItem? = null,
    val friendRequestProcessing: Boolean = false,
    val friendsExpanded: Boolean = false,  // 默认不展开
    val groupsExpanded: Boolean = false,  // 默认不展开
    val botsExpanded: Boolean = false,  // 默认不展开
    val myBotsExpanded: Boolean = false  // 默认不展开
)

@HiltViewModel
class ContactsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val friendRepository: FriendRepository,
    private val tokenRepository: TokenRepository,
    private val botRepository: com.yhchat.canary.data.repository.BotRepository
) : ViewModel() {
    
    private val tag = "ContactsViewModel"
    private val blocklistRepository by lazy { com.yhchat.canary.data.repository.BlocklistRepository(context) }
    
    private val _uiState = MutableStateFlow(ContactsUiState())
    val uiState: StateFlow<ContactsUiState> = _uiState.asStateFlow()
    
    init {
        loadContacts()
    }
    
    /**
     * 加载所有联系人
     */
    fun loadContacts() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                
                // Log.d(tag, "开始加载通讯录...")
                
                val result = friendRepository.getAddressBookList()
                
                result.fold(
                    onSuccess = { data ->
                        // Log.d(tag, "✅ 通讯录加载成功，总分组数: ${data.dataCount}")
                        
                        // 解析protobuf数据
                        val friends = mutableListOf<Contact>()
                        val groups = mutableListOf<Contact>()
                        val bots = mutableListOf<Contact>()
                        
                        // Log.d(tag, "开始解析分组数据...")
                        data.dataList.forEachIndexed { index, group ->
                            // Log.d(tag, "处理分组[$index]: ${group.listName}, 成员数: ${group.dataCount}")
                        }
                        
                        data.dataList.forEach { group ->
                            when (group.listName) {
                                "好友", "用户" -> {
                                    group.dataList.forEach { item ->
                                        friends.add(
                                            Contact(
                                                chatId = item.chatId,
                                                name = item.name,
                                                avatarUrl = item.avatarUrl,
                                                permissionLevel = item.permissonLevel,
                                                noDisturb = item.noDisturb,
                                                chatType = 1 // 用户
                                            )
                                        )
                                    }
                                }
                                "我加入的群聊" -> {
                                    group.dataList.forEach { item ->
                                        groups.add(
                                            Contact(
                                                chatId = item.chatId,
                                                name = item.name,
                                                avatarUrl = item.avatarUrl,
                                                permissionLevel = item.permissonLevel,
                                                noDisturb = item.noDisturb,
                                                chatType = 2 // 群聊
                                            )
                                        )
                                    }
                                }
                                "机器人" -> {
                                    group.dataList.forEach { item ->
                                        bots.add(
                                            Contact(
                                                chatId = item.chatId,
                                                name = item.name,
                                                avatarUrl = item.avatarUrl,
                                                permissionLevel = item.permissonLevel,
                                                noDisturb = item.noDisturb,
                                                chatType = 3 // 机器人
                                            )
                                        )
                                    }
                                }
                            }
                        }
                        
                        val blockedSet = blocklistRepository.getBlockedUserIdsSet()
                        val filteredFriends = if (blockedSet.isEmpty()) friends else friends.filter { it.chatId !in blockedSet }
                        
                        // Log.d(tag, "好友数量: ${filteredFriends.size}, 群聊数量: ${groups.size}, 机器人数量: ${bots.size}")
                        
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            friends = filteredFriends,
                            groups = groups,
                            bots = bots
                        )
                    },
                    onFailure = { error ->
                        Log.e(tag, "❌ 通讯录加载失败: ${error.message}", error)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = error.message ?: "加载失败"
                        )
                    }
                )
            } catch (e: Exception) {
                Log.e(tag, "❌ 通讯录加载异常", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "加载异常"
                )
            }
        }
    }
    
    /**
     * 切换好友列表展开/收起
     */
    fun toggleFriendsExpanded() {
        _uiState.value = _uiState.value.copy(
            friendsExpanded = !_uiState.value.friendsExpanded
        )
    }

    fun toggleFriendRequestsExpanded() {
        val newExpanded = !_uiState.value.friendRequestsExpanded
        _uiState.value = _uiState.value.copy(friendRequestsExpanded = newExpanded)
        if (newExpanded && !_uiState.value.friendRequestsLoading && _uiState.value.friendRequests.isEmpty()) {
            loadFriendRequestList()
        }
    }

    fun loadFriendRequestList() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(friendRequestsLoading = true)
            friendRepository.getFriendRequestList().fold(
                onSuccess = { resp ->
                    val blockedSet = blocklistRepository.getBlockedUserIdsSet()
                    val list = resp.requestsList.map { r ->
                        FriendRequestItem(
                            requestId = r.requestId,
                            receiverName = r.receiverName,
                            receiverAvatar = r.receiverAvatar,
                            name = r.name,
                            avatar = r.avatar,
                            groupName = r.groupName,
                            groupAvatar = r.groupAvatar,
                            inviterId = r.inviterId,
                            sourceType = r.sourceType,
                            targetType = r.targetType,
                            targetId = r.targetId,
                            receiverId = r.receiverId,
                            result = r.result,
                            processedAt = r.processedAt,
                            inviteAt = r.inviteAt,
                            inviteAtStr = r.inviteAtStr,
                            botName = r.botName,
                            botAvatar = r.botAvatar,
                            processorName = r.processorName,
                            note = r.note
                        )
                    }.filter {
                        if (blockedSet.isEmpty()) true
                        else it.inviterId !in blockedSet && it.receiverId !in blockedSet
                    }
                    _uiState.value = _uiState.value.copy(
                        friendRequestsLoading = false,
                        friendRequests = list,
                        friendRequestTotal = resp.total,
                        friendRequestPending = resp.pending
                    )
                },
                onFailure = { e ->
                    Log.e(tag, "❌ 请求/邀请列表加载失败", e)
                    _uiState.value = _uiState.value.copy(friendRequestsLoading = false)
                }
            )
        }
    }

    fun selectFriendRequest(item: FriendRequestItem?) {
        _uiState.value = _uiState.value.copy(selectedFriendRequest = item)
    }

    fun agreeSelectedFriendRequest() {
        val item = _uiState.value.selectedFriendRequest ?: return
        if (_uiState.value.friendRequestProcessing) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(friendRequestProcessing = true)
            friendRepository.agreeApply(
                id = item.requestId, 
                agree = 1, 
                usesGroupAgreeInvite = item.usesGroupAgreeInvite
            ).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(friendRequestProcessing = false)
                    loadFriendRequestList()
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(friendRequestProcessing = false)
                }
            )
        }
    }

    fun rejectSelectedFriendRequest() {
        val item = _uiState.value.selectedFriendRequest ?: return
        if (_uiState.value.friendRequestProcessing) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(friendRequestProcessing = true)
            friendRepository.agreeApply(
                id = item.requestId, 
                agree = 2,
                usesGroupAgreeInvite = item.usesGroupAgreeInvite
            ).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(friendRequestProcessing = false)
                    loadFriendRequestList()
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(friendRequestProcessing = false)
                }
            )
        }
    }
    
    /**
     * 切换群聊列表展开/收起
     */
    fun toggleGroupsExpanded() {
        _uiState.value = _uiState.value.copy(
            groupsExpanded = !_uiState.value.groupsExpanded
        )
    }
    
    /**
     * 切换机器人列表展开/收起
     */
    fun toggleBotsExpanded() {
        _uiState.value = _uiState.value.copy(
            botsExpanded = !_uiState.value.botsExpanded
        )
    }
    
    /**
     * 切换"我创建的机器人"展开状态
     */
    fun toggleMyBotsExpanded() {
        val newExpanded = !_uiState.value.myBotsExpanded
        _uiState.value = _uiState.value.copy(
            myBotsExpanded = newExpanded
        )
        
        // 如果展开且还没有加载数据，则加载
        if (newExpanded && _uiState.value.myBots.isEmpty()) {
            loadMyBots()
        }
    }
    
    /**
     * 加载我创建的机器人列表
     */
    private fun loadMyBots() {
        viewModelScope.launch {
            try {
                // Log.d(tag, "开始加载我创建的机器人列表...")
                
                val result = botRepository.getMyBotList()
                
                result.fold(
                    onSuccess = { botList ->
                        // Log.d(tag, "✅ 我创建的机器人列表加载成功，共 ${botList.size} 个")
                        
                        val myBots = botList.map { bot ->
                            Contact(
                                chatId = bot.botId,
                                name = bot.nickname,
                                avatarUrl = bot.avatarUrl,
                                chatType = 3
                            )
                        }
                        
                        _uiState.value = _uiState.value.copy(myBots = myBots)
                    },
                    onFailure = { error ->
                        Log.e(tag, "❌ 加载我创建的机器人列表失败", error)
                    }
                )
            } catch (e: Exception) {
                Log.e(tag, "加载我创建的机器人列表异常", e)
            }
        }
    }
    
    /**
     * 清除错误
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

