package com.yhchat.canary.ui.group

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhchat.canary.data.api.ApiClient
import com.yhchat.canary.data.model.GroupDetail
import com.yhchat.canary.data.repository.GroupRepository
import com.yhchat.canary.data.repository.TokenRepository
import com.yhchat.canary.utils.ImageUploadUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupSettingsUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val groupInfo: GroupDetail? = null,
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val isUploadingAvatar: Boolean = false,
    val saveError: String? = null,
    val isSaveSuccess: Boolean = false,
    // 编辑状态
    val editedName: String = "",
    val editedIntroduction: String = "",
    val editedAvatarUrl: String = "",
    val editedDirectJoin: Boolean = false,
    val editedHistoryMsg: Boolean = false,
    val editedPrivate: Boolean = false,
    val editedCategoryName: String = "",
    val editedCategoryId: Long = 0L,
    // 消息类型限制对话框
    val showMessageTypeLimitDialog: Boolean = false,
    val selectedMessageTypes: Set<Int> = emptySet(),
    val isSettingMessageTypeLimit: Boolean = false,

    val showAutoDeleteMessageDialog: Boolean = false,
    val isSettingAutoDeleteMessage: Boolean = false,

    val showImageViewer: Boolean = false,
    val showKeywordDialog: Boolean = false,
    val keywordInput: String = "",
    val isSettingKeyword: Boolean = false,

    val isSettingHideGroupMembers: Boolean = false,
    val isSettingDenyMembersUploadToGroupDisk: Boolean = false
)

@HiltViewModel
class GroupSettingsViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val tokenRepository: TokenRepository
) : ViewModel() {
    
    private val tag = "GroupSettingsViewModel"
    
    private val _uiState = MutableStateFlow(GroupSettingsUiState())
    val uiState: StateFlow<GroupSettingsUiState> = _uiState.asStateFlow()
    
    init {
        groupRepository.setTokenRepository(tokenRepository)
    }

    fun showAutoDeleteMessageDialog() {
        _uiState.value = _uiState.value.copy(showAutoDeleteMessageDialog = true)
    }

    fun dismissAutoDeleteMessageDialog() {
        _uiState.value = _uiState.value.copy(showAutoDeleteMessageDialog = false)
    }

    fun openImageViewer() {
        if (_uiState.value.groupInfo?.avatarUrl.isNullOrBlank()) return
        _uiState.value = _uiState.value.copy(showImageViewer = true)
    }

    fun dismissImageViewer() {
        _uiState.value = _uiState.value.copy(showImageViewer = false)
    }

    fun openKeywordDialog() {
        val keyword = _uiState.value.groupInfo?.groupCode ?: ""
        _uiState.value = _uiState.value.copy(
            showKeywordDialog = true,
            keywordInput = keyword,
            isSettingKeyword = false
        )
    }

    fun dismissKeywordDialog() {
        if (_uiState.value.isSettingKeyword) return
        _uiState.value = _uiState.value.copy(showKeywordDialog = false)
    }

    fun updateKeywordInput(keyword: String) {
        _uiState.value = _uiState.value.copy(keywordInput = keyword)
    }

    fun confirmKeyword(groupId: String) {
        val keyword = _uiState.value.keywordInput
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSettingKeyword = true)
            groupRepository.editGroupKeyword(groupId, keyword).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isSettingKeyword = false,
                        showKeywordDialog = false
                    )
                    loadGroupInfo(groupId)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isSettingKeyword = false,
                        saveError = error.message ?: "修改群口令失败"
                    )
                }
            )
        }
    }

    fun setAutoDeleteMessage(groupId: String, autoDeleteMessage: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSettingAutoDeleteMessage = true)

            groupRepository.editAutoDeleteMessage(groupId, autoDeleteMessage).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isSettingAutoDeleteMessage = false,
                        showAutoDeleteMessageDialog = false
                    )
                    loadGroupInfo(groupId)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isSettingAutoDeleteMessage = false,
                        error = error.message ?: "设置失败"
                    )
                }
            )
        }
    }

    fun setHideGroupMembers(hide: Boolean) {
        val groupInfo = _uiState.value.groupInfo ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSettingHideGroupMembers = true)

            groupRepository.setHideGroupMembers(groupInfo, hide).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(isSettingHideGroupMembers = false)
                    loadGroupInfo(groupInfo.groupId)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isSettingHideGroupMembers = false,
                        saveError = error.message ?: "设置失败"
                    )
                }
            )
        }
    }

    fun setDenyMembersUploadToGroupDisk(deny: Boolean) {
        val groupInfo = _uiState.value.groupInfo ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSettingDenyMembersUploadToGroupDisk = true)

            groupRepository.setDenyMembersUploadToGroupDisk(groupInfo.groupId, deny).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(isSettingDenyMembersUploadToGroupDisk = false)
                    loadGroupInfo(groupInfo.groupId)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isSettingDenyMembersUploadToGroupDisk = false,
                        saveError = error.message ?: "设置失败"
                    )
                }
            )
        }
    }
    
    /**
     * 加载群聊信息
     */
    fun loadGroupInfo(groupId: String) {
        viewModelScope.launch {
            Log.d(tag, "Loading group info for: $groupId")
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            groupRepository.getGroupInfo(groupId).fold(
                onSuccess = { groupInfo ->
                    Log.d(tag, "✅ Group info loaded: ${groupInfo.name}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        groupInfo = groupInfo,
                        // 初始化编辑状态
                        editedName = groupInfo.name,
                        editedIntroduction = groupInfo.introduction,
                        editedAvatarUrl = groupInfo.avatarUrl,
                        editedDirectJoin = groupInfo.directJoin,
                        editedHistoryMsg = groupInfo.historyMsgEnabled,
                        editedPrivate = groupInfo.isPrivate,
                        editedCategoryName = groupInfo.categoryName,
                        editedCategoryId = groupInfo.categoryId
                    )
                },
                onFailure = { error ->
                    Log.e(tag, "❌ Failed to load group info", error)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "加载群聊信息失败"
                    )
                }
            )
        }
    }
    
    /**
     * 开始编辑
     */
    fun startEditing() {
        _uiState.value = _uiState.value.copy(isEditing = true)
    }
    
    /**
     * 取消编辑
     */
    fun cancelEditing() {
        val groupInfo = _uiState.value.groupInfo
        if (groupInfo != null) {
            _uiState.value = _uiState.value.copy(
                isEditing = false,
                // 恢复原始值
                editedName = groupInfo.name,
                editedIntroduction = groupInfo.introduction,
                editedAvatarUrl = groupInfo.avatarUrl,
                editedDirectJoin = groupInfo.directJoin,
                editedHistoryMsg = groupInfo.historyMsgEnabled,
                editedPrivate = groupInfo.isPrivate,
                editedCategoryName = groupInfo.categoryName,
                editedCategoryId = groupInfo.categoryId
            )
        }
    }
    
    /**
     * 保存编辑
     */
    fun saveEditing() {
        val currentState = _uiState.value
        val groupInfo = currentState.groupInfo ?: return
        
        viewModelScope.launch {
            Log.d(tag, "Saving group settings for: ${groupInfo.groupId}")
            _uiState.value = _uiState.value.copy(isSaving = true, saveError = null)
            
            groupRepository.editGroupInfo(
                groupId = groupInfo.groupId,
                name = currentState.editedName,
                introduction = currentState.editedIntroduction,
                avatarUrl = currentState.editedAvatarUrl,
                directJoin = currentState.editedDirectJoin,
                historyMsg = currentState.editedHistoryMsg,
                categoryName = currentState.editedCategoryName,
                categoryId = currentState.editedCategoryId,
                isPrivate = currentState.editedPrivate
            ).fold(
                onSuccess = {
                    Log.d(tag, "✅ Group settings saved successfully")
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        isEditing = false,
                        isSaveSuccess = true
                    )
                    // 重新加载群聊信息
                    loadGroupInfo(groupInfo.groupId)
                },
                onFailure = { error ->
                    Log.e(tag, "❌ Failed to save group settings", error)
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        saveError = error.message ?: "保存失败"
                    )
                }
            )
        }
    }
    
    /**
     * 更新编辑字段
     */
    fun uploadGroupAvatar(context: Context, imageUri: Uri) {
        val currentState = _uiState.value
        if (!currentState.isEditing || currentState.groupInfo == null) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isUploadingAvatar = true,
                saveError = null
            )

            try {
                val userToken = tokenRepository.getTokenSync().orEmpty()
                if (userToken.isEmpty()) {
                    throw Exception("未登录")
                }

                val tokenResponse = ApiClient.apiService.getQiniuImageToken(userToken)
                if (!tokenResponse.isSuccessful || tokenResponse.body()?.data == null) {
                    throw Exception("获取上传token失败")
                }

                val uploadToken = tokenResponse.body()!!.data.token
                val uploadResult = ImageUploadUtil.uploadImage(context, imageUri, uploadToken)

                uploadResult.fold(
                    onSuccess = { uploadResponse ->
                        val imageUrl = "https://chat-img.jwznb.com/${uploadResponse.key}"
                        val updatedGroupInfo = _uiState.value.groupInfo?.copy(avatarUrl = imageUrl)
                        _uiState.value = _uiState.value.copy(
                            isUploadingAvatar = false,
                            editedAvatarUrl = imageUrl,
                            groupInfo = updatedGroupInfo
                        )
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            isUploadingAvatar = false,
                            saveError = "头像上传失败: ${error.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isUploadingAvatar = false,
                    saveError = "头像上传失败: ${e.message}"
                )
            }
        }
    }

    fun updateEditedName(name: String) {
        _uiState.value = _uiState.value.copy(editedName = name)
    }
    
    fun updateEditedIntroduction(introduction: String) {
        _uiState.value = _uiState.value.copy(editedIntroduction = introduction)
    }
    
    fun updateEditedAvatarUrl(avatarUrl: String) {
        _uiState.value = _uiState.value.copy(editedAvatarUrl = avatarUrl)
    }
    
    fun updateEditedDirectJoin(directJoin: Boolean) {
        _uiState.value = _uiState.value.copy(editedDirectJoin = directJoin)
    }
    
    fun updateEditedHistoryMsg(historyMsg: Boolean) {
        _uiState.value = _uiState.value.copy(editedHistoryMsg = historyMsg)
    }
    
    fun updateEditedPrivate(isPrivate: Boolean) {
        _uiState.value = _uiState.value.copy(editedPrivate = isPrivate)
    }
    
    fun updateEditedCategoryName(categoryName: String) {
        _uiState.value = _uiState.value.copy(editedCategoryName = categoryName)
    }
    
    /**
     * 清除错误
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    fun clearSaveError() {
        _uiState.value = _uiState.value.copy(saveError = null)
    }
    
    /**
     * 检查是否为管理员或群主
     */
    fun isAdminOrOwner(): Boolean {
        val groupInfo = _uiState.value.groupInfo ?: return false
        // 这里需要获取当前用户ID，暂时返回true用于测试
        // TODO: 实现真正的权限检查
        return groupInfo.permissionLevel == 100 || groupInfo.permissionLevel == 2
    }
    
    /**
     * 显示消息类型限制对话框
     */
    fun showMessageTypeLimitDialog() {
        val groupInfo = _uiState.value.groupInfo ?: return
        // 解析当前的消息类型限制
        val currentTypes = if (groupInfo.limitedMsgType.isNotEmpty()) {
            groupInfo.limitedMsgType.split(",").mapNotNull { it.toIntOrNull() }.toSet()
        } else {
            emptySet()
        }
        _uiState.value = _uiState.value.copy(
            showMessageTypeLimitDialog = true,
            selectedMessageTypes = currentTypes
        )
    }
    
    /**
     * 隐藏消息类型限制对话框
     */
    fun dismissMessageTypeLimitDialog() {
        _uiState.value = _uiState.value.copy(
            showMessageTypeLimitDialog = false,
            selectedMessageTypes = emptySet()
        )
    }
    
    /**
     * 切换消息类型选择
     */
    fun toggleMessageType(type: Int) {
        val current = _uiState.value.selectedMessageTypes
        val updated = if (current.contains(type)) {
            current - type
        } else {
            current + type
        }
        _uiState.value = _uiState.value.copy(selectedMessageTypes = updated)
    }
    
    /**
     * 确认设置消息类型限制
     */
    fun confirmMessageTypeLimit() {
        val groupInfo = _uiState.value.groupInfo ?: return
        val selectedTypes = _uiState.value.selectedMessageTypes
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSettingMessageTypeLimit = true)
            
            val typeString = selectedTypes.sorted().joinToString(",")
            Log.d(tag, "Setting message type limit: $typeString")
            
            groupRepository.setMessageTypeLimit(groupInfo.groupId, typeString).fold(
                onSuccess = {
                    Log.d(tag, "✅ Message type limit set successfully")
                    _uiState.value = _uiState.value.copy(
                        isSettingMessageTypeLimit = false,
                        showMessageTypeLimitDialog = false
                    )
                    // 重新加载群聊信息
                    loadGroupInfo(groupInfo.groupId)
                },
                onFailure = { error ->
                    Log.e(tag, "❌ Failed to set message type limit", error)
                    _uiState.value = _uiState.value.copy(
                        isSettingMessageTypeLimit = false,
                        saveError = error.message ?: "设置消息类型限制失败"
                    )
                }
            )
        }
    }
    
    /**
     * 获取当前用户ID
     */
    fun getCurrentUserId(): String {
        return tokenRepository.getUserIdSync() ?: ""
    }
}
