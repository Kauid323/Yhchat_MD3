package com.yhchat.canary.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhchat.canary.data.api.ApiService
import com.yhchat.canary.data.api.WebApiService
import com.yhchat.canary.data.model.AddFriendRequest
import com.yhchat.canary.data.model.UserHomepageInfo
import com.yhchat.canary.data.repository.TokenRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 用户资料ViewModel - 完全重写版本
 * 基于 yhapi/web/v1/user.md 的 /v1/user/homepage 接口
 * 基于 yhapi/v1/friend.md 的 /v1/friend/apply 接口
 */
@HiltViewModel
class UserProfileViewModel @Inject constructor(
    private val apiService: ApiService,
    private val webApiService: WebApiService,
    private val tokenRepository: TokenRepository,
    private val groupRepository: com.yhchat.canary.data.repository.GroupRepository
) : ViewModel() {
    
    init {
        groupRepository.setTokenRepository(tokenRepository)
    }

    // UI状态
    private val _uiState = MutableStateFlow(UserProfileUiState())
    val uiState: StateFlow<UserProfileUiState> = _uiState.asStateFlow()

    // 用户资料
    private val _userProfile = MutableStateFlow<UserHomepageInfo?>(null)
    val userProfile: StateFlow<UserHomepageInfo?> = _userProfile.asStateFlow()
    
    // 群信息缓存（用于获取成员权限）
    private val groupInfoCache = mutableMapOf<String, com.yhchat.canary.data.model.GroupDetail>()
    private val groupMembersCache = mutableMapOf<String, Map<String, com.yhchat.canary.data.model.GroupMemberInfo>>()

    /**
     * 加载用户资料
     * 调用 GET /v1/user/homepage?userId={userId}
     */
    fun loadUserProfile(userId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            println("UserProfileViewModel: 开始加载用户资料, userId = $userId")

            try {
                val response = webApiService.getUserHomepage(userId)
                println("UserProfileViewModel: API响应 - isSuccessful: ${response.isSuccessful}, code: ${response.code()}")
                
                if (response.isSuccessful) {
                    val homepageResponse = response.body()
                    println("UserProfileViewModel: 响应体 - code: ${homepageResponse?.code}, msg: ${homepageResponse?.msg}")
                    
                    if (homepageResponse?.code == 1) {
                        _userProfile.value = homepageResponse.data.user
                        _uiState.value = _uiState.value.copy(isLoading = false)
                        println("UserProfileViewModel: 用户资料加载成功 - ${homepageResponse.data.user.nickname}")
                    } else {
                        val errorMsg = homepageResponse?.msg ?: "获取用户资料失败"
                        println("UserProfileViewModel: API返回错误 - $errorMsg")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = errorMsg
                        )
                    }
                } else {
                    val errorMsg = "网络请求失败: ${response.code()}"
                    println("UserProfileViewModel: 网络请求失败 - $errorMsg")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = errorMsg
                    )
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: "未知错误"
                println("UserProfileViewModel: 异常 - $errorMsg")
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = errorMsg
                )
            }
        }
    }

    /**
     * 显示添加好友对话框
     */
    fun showAddFriendDialog(userId: String, userName: String) {
        _uiState.value = _uiState.value.copy(
            showAddFriendDialog = AddFriendDialogData(
                userId = userId,
                userName = userName,
                remark = ""
            )
        )
    }

    /**
     * 隐藏添加好友对话框
     */
    fun dismissAddFriendDialog() {
        _uiState.value = _uiState.value.copy(showAddFriendDialog = null)
    }

    /**
     * 更新好友申请备注
     */
    fun updateFriendRemark(remark: String) {
        _uiState.value.showAddFriendDialog?.let { dialogData ->
            _uiState.value = _uiState.value.copy(
                showAddFriendDialog = dialogData.copy(remark = remark)
            )
        }
    }

    /**
     * 确认添加好友
     * 调用 POST /v1/friend/apply
     */
    fun confirmAddFriend() {
        val dialogData = _uiState.value.showAddFriendDialog ?: return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAddingFriend = true)

            try {
                // 获取token
                val token = tokenRepository.getTokenSync()
                if (token.isNullOrEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isAddingFriend = false,
                        error = "未登录，请先登录"
                    )
                    return@launch
                }

                // 构建请求体，按照 friend.md 文档
                val request = AddFriendRequest(
                    chatId = dialogData.userId,
                    chatType = 1, // 1-用户，2-群聊，3-机器人
                    remark = dialogData.remark.takeIf { it.isNotBlank() } ?: ""
                )

                val response = apiService.addFriend(token, request)

                if (response.isSuccessful) {
                    val result = response.body()
                    if (result?.code == 1) {
                        // 添加成功
                        _uiState.value = _uiState.value.copy(
                            isAddingFriend = false,
                            showAddFriendDialog = null,
                            error = null,
                            addFriendSuccess = true
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isAddingFriend = false,
                            error = result?.message ?: "添加好友失败"
                        )
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isAddingFriend = false,
                        error = "网络请求失败: ${response.code()}"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isAddingFriend = false,
                    error = e.message ?: "未知错误"
                )
            }
        }
    }

    /**
     * 清除错误信息
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * 清除添加好友成功状态
     */
    fun clearAddFriendSuccess() {
        _uiState.value = _uiState.value.copy(addFriendSuccess = false)
    }
    
    /**
     * 踢出群成员
     */
    fun removeMemberFromGroup(groupId: String, userId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessingMemberAction = true, error = null)
            
            groupRepository.removeMember(groupId, userId).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isProcessingMemberAction = false,
                        memberActionSuccess = "已踢出该成员"
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isProcessingMemberAction = false,
                        error = error.message ?: "踢出成员失败"
                    )
                }
            )
        }
    }
    
    /**
     * 禁言群成员
     */
    fun gagMemberInGroup(groupId: String, userId: String, gagTime: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessingMemberAction = true, error = null)
            
            groupRepository.gagMember(groupId, userId, gagTime).fold(
                onSuccess = {
                    val message = when (gagTime) {
                        0 -> "已取消禁言"
                        600 -> "已禁言10分钟"
                        3600 -> "已禁言1小时"
                        21600 -> "已禁言6小时"
                        43200 -> "已禁言12小时"
                        1 -> "已永久禁言"
                        else -> "禁言设置成功"
                    }
                    _uiState.value = _uiState.value.copy(
                        isProcessingMemberAction = false,
                        memberActionSuccess = message
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isProcessingMemberAction = false,
                        error = error.message ?: "禁言操作失败"
                    )
                }
            )
        }
    }
    
    /**
     * 设置成员角色（上任/卸任管理员）
     */
    fun setMemberRole(groupId: String, userId: String, userLevel: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessingMemberAction = true, error = null)
            
            groupRepository.setMemberRole(groupId, userId, userLevel).fold(
                onSuccess = {
                    val message = when (userLevel) {
                        0 -> "已卸任管理员"
                        2 -> "已设为管理员"
                        else -> "角色设置成功"
                    }
                    _uiState.value = _uiState.value.copy(
                        isProcessingMemberAction = false,
                        memberActionSuccess = message
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isProcessingMemberAction = false,
                        error = error.message ?: "设置角色失败"
                    )
                }
            )
        }
    }
    
    /**
     * 加载群信息和目标用户的成员信息
     */
    fun loadGroupInfoAndMemberInfo(groupId: String, targetUserId: String) {
        viewModelScope.launch {
            android.util.Log.d("UserProfileViewModel", "Loading group info for: $groupId")
            
            // 加载群信息
            groupRepository.getGroupInfo(groupId).fold(
                onSuccess = { groupInfo ->
                    groupInfoCache[groupId] = groupInfo
                    val currentUserId = tokenRepository.getUserIdSync()
                    val isOwner = groupInfo.ownerId == currentUserId || groupInfo.permissionLevel.toInt() == 100
                    
                    android.util.Log.d("UserProfileViewModel", "Group info loaded. CurrentUser: $currentUserId, Owner: ${groupInfo.ownerId}, Permission: ${groupInfo.permissionLevel}, IsOwner: $isOwner")
                    
                    // 加载群成员信息
                    loadGroupMembersAndUpdatePermission(groupId, targetUserId, isOwner)
                },
                onFailure = { error ->
                    android.util.Log.e("UserProfileViewModel", "Failed to load group info", error)
                }
            )
        }
    }
    
    /**
     * 加载群成员信息并更新目标用户权限
     */
    private fun loadGroupMembersAndUpdatePermission(groupId: String, targetUserId: String, isOwner: Boolean) {
        viewModelScope.launch {
            val allMembers = mutableListOf<com.yhchat.canary.data.model.GroupMemberInfo>()
            var page = 1
            var hasMore = true
            
            while (hasMore) {
                groupRepository.getGroupMembers(groupId, page = page, size = 50).fold(
                    onSuccess = { members ->
                        allMembers.addAll(members)
                        hasMore = members.size >= 50
                        page++
                    },
                    onFailure = {
                        hasMore = false
                    }
                )
            }
            
            // 转换为Map并缓存
            groupMembersCache[groupId] = allMembers.associateBy { it.userId }
            
            // 获取目标用户权限
            val targetPermission = allMembers.find { it.userId == targetUserId }?.permissionLevel ?: 0
            
            android.util.Log.d("UserProfileViewModel", "Target user permission: $targetPermission, IsOwner: $isOwner")
            
            // 更新UI状态
            _uiState.value = _uiState.value.copy(
                targetUserPermission = targetPermission,
                isGroupOwner = isOwner
            )
        }
    }
}

/**
 * 添加好友对话框数据
 */
data class AddFriendDialogData(
    val userId: String,
    val userName: String,
    val remark: String
)

/**
 * 用户资料UI状态
 */
data class UserProfileUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val showAddFriendDialog: AddFriendDialogData? = null,
    val isAddingFriend: Boolean = false,
    val addFriendSuccess: Boolean = false,
    val isProcessingMemberAction: Boolean = false,
    val memberActionSuccess: String? = null,
    val targetUserPermission: Int = 0,  // 目标用户在群中的权限
    val isGroupOwner: Boolean = false    // 当前用户是否为群主
)