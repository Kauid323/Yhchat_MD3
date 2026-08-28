package com.yhchat.canary.ui.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhchat.canary.data.model.SaveUserDataRequest
import com.yhchat.canary.data.model.UserData
import com.yhchat.canary.data.model.UserProfile
import com.yhchat.canary.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 个人资料界面的ViewModel
 */
class ProfileViewModel(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()
    
    // 修改邀请码状态
    private val _changeInviteCodeState = MutableStateFlow(ChangeInviteCodeState())
    val changeInviteCodeState: StateFlow<ChangeInviteCodeState> = _changeInviteCodeState.asStateFlow()
    
    // 修改用户名称状态
    private val _changeNicknameState = MutableStateFlow(ChangeNicknameState())
    val changeNicknameState: StateFlow<ChangeNicknameState> = _changeNicknameState.asStateFlow()
    
    // 修改头像状态
    private val _changeAvatarState = MutableStateFlow(ChangeAvatarState())
    val changeAvatarState: StateFlow<ChangeAvatarState> = _changeAvatarState.asStateFlow()
    
    // 内测状态
    private val _betaState = MutableStateFlow(BetaState())
    val betaState: StateFlow<BetaState> = _betaState.asStateFlow()

    private val _userDataState = MutableStateFlow(UserDataState())
    val userDataState: StateFlow<UserDataState> = _userDataState.asStateFlow()

    private val _saveUserDataState = MutableStateFlow(SaveUserDataState())
    val saveUserDataState: StateFlow<SaveUserDataState> = _saveUserDataState.asStateFlow()

    /**
     * 加载用户个人资料
     */
    fun loadUserProfile(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            // 如果不是强制刷新，先尝试从缓存加载
            if (!forceRefresh) {
                val cachedProfile = userRepository.loadProfileFromCache()
                if (cachedProfile != null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        userProfile = cachedProfile,
                        error = null
                    )
                    // 加载内测状态
                    loadBetaInfo()
                    // 后台刷新数据
                    refreshProfileInBackground()
                    return@launch
                }
            }
            
            // 缓存无效或强制刷新，从网络加载
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                userRepository.getUserProfile().fold(
                    onSuccess = { profile ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            userProfile = profile,
                            error = null
                        )
                        // 保存到缓存
                        userRepository.saveProfileToCache(profile)
                        // 加载用户资料成功后，同时加载内测状态
                        loadBetaInfo()
                    },
                    onFailure = { exception ->
                        Log.e("ProfileViewModel", "加载用户资料失败", exception)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = exception.message ?: "加载用户资料失败"
                        )
                    }
                )
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "加载用户资料异常", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "加载用户资料异常"
                )
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    /**
     * 下拉刷新用户资料及内测数据
     */
    fun refreshProfile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            try {
                userRepository.getUserProfile().fold(
                    onSuccess = { profile ->
                        _uiState.value = _uiState.value.copy(
                            userProfile = profile,
                            error = null
                        )
                        // 保存到缓存
                        userRepository.saveProfileToCache(profile)
                        // 同时刷新内测状态
                        loadBetaInfo()
                    },
                    onFailure = { exception ->
                        Log.e("ProfileViewModel", "刷新用户资料失败", exception)
                    }
                )
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "刷新用户资料异常", e)
            } finally {
                _uiState.value = _uiState.value.copy(isRefreshing = false)
            }
        }
    }
    
    /**
     * 后台刷新用户资料
     */
    private fun refreshProfileInBackground() {
        viewModelScope.launch {
            userRepository.getUserProfile().fold(
                onSuccess = { profile ->
                    _uiState.value = _uiState.value.copy(
                        userProfile = profile,
                        error = null
                    )
                    // 保存到缓存
                    userRepository.saveProfileToCache(profile)
                },
                onFailure = { exception ->
                    Log.e("ProfileViewModel", "后台刷新用户资料失败", exception)
                    // 后台刷新失败不更新UI
                }
            )
        }
    }

    fun loadUserData() {
        viewModelScope.launch {
            _userDataState.value = _userDataState.value.copy(isLoading = true, error = null)
            userRepository.getUserData().fold(
                onSuccess = { data ->
                    _userDataState.value = _userDataState.value.copy(
                        isLoading = false,
                        userData = data,
                        error = null
                    )
                },
                onFailure = { e ->
                    _userDataState.value = _userDataState.value.copy(
                        isLoading = false,
                        error = e.message ?: "获取个人信息失败"
                    )
                }
            )
        }
    }

    fun saveUserData(request: SaveUserDataRequest) {
        viewModelScope.launch {
            _saveUserDataState.value = _saveUserDataState.value.copy(isLoading = true, error = null, isSuccess = false)
            userRepository.saveUserData(request).fold(
                onSuccess = {
                    _saveUserDataState.value = _saveUserDataState.value.copy(isLoading = false, error = null, isSuccess = true)
                    loadUserData()
                },
                onFailure = { e ->
                    _saveUserDataState.value = _saveUserDataState.value.copy(
                        isLoading = false,
                        error = e.message ?: "保存个人信息失败",
                        isSuccess = false
                    )
                }
            )
        }
    }

    fun resetSaveUserDataState() {
        _saveUserDataState.value = SaveUserDataState()
    }
    
    /**
     * 加载内测状态
     */
    fun loadBetaInfo() {
        viewModelScope.launch {
            _betaState.value = _betaState.value.copy(isLoading = true, error = null)
            
            try {
                val token = userRepository.getTokenSync() ?: ""
                val response = com.yhchat.canary.data.api.ApiClient.apiService.getBetaInfo(token)
                
                if (response.isSuccessful && response.body()?.code == 1) {
                    val betaInfo = response.body()?.data
                    _betaState.value = _betaState.value.copy(
                        isLoading = false,
                        betaInfo = betaInfo,
                        error = null
                    )
                } else {
                    _betaState.value = _betaState.value.copy(
                        isLoading = false,
                        error = response.body()?.msg ?: "获取内测状态失败"
                    )
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "获取内测状态异常", e)
                _betaState.value = _betaState.value.copy(
                    isLoading = false,
                    error = e.message ?: "获取内测状态异常"
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
     * 修改邀请码
     */
    fun changeInviteCode(code: String) {
        viewModelScope.launch {
            _changeInviteCodeState.value = _changeInviteCodeState.value.copy(
                isLoading = true, 
                error = null,
                isSuccess = false
            )
            
            userRepository.changeInviteCode(code).fold(
                onSuccess = {
                    _changeInviteCodeState.value = _changeInviteCodeState.value.copy(
                        isLoading = false,
                        isSuccess = true,
                        error = null
                    )
                    // 修改成功后重新加载用户资料
                    loadUserProfile()
                },
                onFailure = { exception ->
                    Log.e("ProfileViewModel", "修改邀请码失败", exception)
                    _changeInviteCodeState.value = _changeInviteCodeState.value.copy(
                        isLoading = false,
                        error = exception.message ?: "修改邀请码失败"
                    )
                }
            )
        }
    }
    
    /**
     * 重置修改邀请码状态
     */
    fun resetChangeInviteCodeState() {
        _changeInviteCodeState.value = ChangeInviteCodeState()
    }
    
    /**
     * 修改用户名称
     */
    fun changeNickname(nickname: String) {
        viewModelScope.launch {
            _changeNicknameState.value = _changeNicknameState.value.copy(
                isLoading = true, 
                error = null,
                isSuccess = false
            )
            
            userRepository.editNickname(nickname).fold(
                onSuccess = {
                    _changeNicknameState.value = _changeNicknameState.value.copy(
                        isLoading = false,
                        isSuccess = true,
                        error = null
                    )
                    // 修改成功后重新加载用户资料
                    loadUserProfile()
                },
                onFailure = { exception ->
                    Log.e("ProfileViewModel", "修改用户名称失败", exception)
                    _changeNicknameState.value = _changeNicknameState.value.copy(
                        isLoading = false,
                        error = exception.message ?: "修改用户名称失败"
                    )
                }
            )
        }
    }
    
    /**
     * 重置修改用户名称状态
     */
    fun resetChangeNicknameState() {
        _changeNicknameState.value = ChangeNicknameState()
    }
    
    /**
     * 修改用户头像
     */
    fun changeAvatar(context: android.content.Context, imageUri: android.net.Uri) {
        viewModelScope.launch {
            _changeAvatarState.value = _changeAvatarState.value.copy(isLoading = true, error = null)
            
            try {
                // 首先获取上传token
                val token = userRepository.getTokenSync() ?: ""
                val tokenResult = com.yhchat.canary.data.api.ApiClient.apiService.getQiniuImageToken(token)
                
                if (!tokenResult.isSuccessful || tokenResult.body()?.data == null) {
                    _changeAvatarState.value = _changeAvatarState.value.copy(
                        isLoading = false,
                        error = "获取上传token失败"
                    )
                    return@launch
                }
                
                val uploadToken = tokenResult.body()!!.data.token
                
                // 上传图片
                val uploadResult = com.yhchat.canary.utils.ImageUploadUtil.uploadImage(
                    context = context,
                    imageUri = imageUri,
                    uploadToken = uploadToken
                )
                
                uploadResult.fold(
                    onSuccess = { uploadResponse ->
                        // 构建完整的图片URL
                        val imageUrl = "https://chat-img.jwznb.com/${uploadResponse.key}"
                        
                        // 调用修改头像API
                        userRepository.editAvatar(imageUrl).fold(
                            onSuccess = {
                                _changeAvatarState.value = _changeAvatarState.value.copy(
                                    isLoading = false,
                                    isSuccess = true
                                )
                                // 重新加载用户资料
                                loadUserProfile()
                            },
                            onFailure = { exception ->
                                _changeAvatarState.value = _changeAvatarState.value.copy(
                                    isLoading = false,
                                    error = exception.message ?: "修改头像失败"
                                )
                            }
                        )
                    },
                    onFailure = { exception ->
                        _changeAvatarState.value = _changeAvatarState.value.copy(
                            isLoading = false,
                            error = "上传图片失败: ${exception.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                _changeAvatarState.value = _changeAvatarState.value.copy(
                    isLoading = false,
                    error = "修改头像异常: ${e.message}"
                )
            }
        }
    }
    
    /**
     * 重置修改头像状态
     */
    fun resetChangeAvatarState() {
        _changeAvatarState.value = ChangeAvatarState()
    }
}

/**
 * 个人资料界面UI状态
 */
data class ProfileUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val userProfile: UserProfile? = null,
    val error: String? = null
)

/**
 * 修改邀请码状态
 */
data class ChangeInviteCodeState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null
)

/**
 * 修改用户名称状态
 */
data class ChangeNicknameState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null
)

/**
 * 修改头像状态
 */
data class ChangeAvatarState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null
)

/**
 * 内测状态
 */
data class BetaState(
    val isLoading: Boolean = false,
    val betaInfo: com.yhchat.canary.data.model.BetaInfo? = null,
    val error: String? = null
)

data class UserDataState(
    val isLoading: Boolean = false,
    val userData: UserData? = null,
    val error: String? = null
)

data class SaveUserDataState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null
)
