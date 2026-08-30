package com.yhchat.canary.ui.bot

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yhchat.canary.data.di.RepositoryFactory
import com.yhchat.canary.utils.ImageUploadUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class BotEditViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "BotEditViewModel"
    }

    private val botRepository = RepositoryFactory.getBotRepository(application)
    private val tokenRepository = RepositoryFactory.getTokenRepository(application)

    private val _uiState = MutableStateFlow(BotEditUiState())
    val uiState: StateFlow<BotEditUiState> = _uiState.asStateFlow()

    /**
     * 加载机器人信息
     */
    fun loadBotInfo(botId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                botId = botId
            )

            val result = botRepository.getBotInfo(botId)

            result.fold(
                onSuccess = { botInfo ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        avatarUrl = botInfo.data.avatarUrl,
                        nickname = botInfo.data.name,
                        introduction = botInfo.data.introduction,
                        isPrivate = botInfo.data.private == 1L
                    )
                    Log.d(TAG, "✅ 机器人信息加载成功")
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "加载失败"
                    )
                    Log.e(TAG, "❌ 加载机器人信息失败", error)
                }
            )
        }
    }

    /**
     * 上传头像
     */
    fun uploadAvatar(context: Context, imageUri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isUploading = true,
                error = null
            )

            try {
                // 1. 获取用户登录token
                val userToken = tokenRepository.getTokenSync() ?: ""
                if (userToken.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isUploading = false,
                        error = "未登录"
                    )
                    return@launch
                }

                Log.d(TAG, "📤 开始上传头像...")

                // 2. 获取七牛云上传token
                val retrofit = Retrofit.Builder()
                    .baseUrl("https://chat-go.jwzhd.com/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                val apiService = retrofit.create(com.yhchat.canary.data.api.ApiService::class.java)
                val tokenResponse = apiService.getQiniuImageToken(userToken)
                
                if (!tokenResponse.isSuccessful || tokenResponse.body()?.code != 1) {
                    _uiState.value = _uiState.value.copy(
                        isUploading = false,
                        error = "获取上传凭证失败"
                    )
                    Log.e(TAG, "获取七牛token失败: ${tokenResponse.code()}")
                    return@launch
                }
                
                val qiniuToken = tokenResponse.body()?.data?.token ?: ""
                if (qiniuToken.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isUploading = false,
                        error = "上传凭证无效"
                    )
                    return@launch
                }
                
                Log.d(TAG, "✅ 获取七牛token成功")

                // 3. 上传图片到七牛云
                val uploadResult = ImageUploadUtil.uploadImage(
                    context = context,
                    imageUri = imageUri,
                    uploadToken = qiniuToken
                )

                uploadResult.fold(
                    onSuccess = { uploadResponse ->
                        // 3. 构建图片URL
                        val avatarUrl = "https://chat-img.jwznb.com/${uploadResponse.key}"
                        
                        _uiState.value = _uiState.value.copy(
                            isUploading = false,
                            avatarUrl = avatarUrl
                        )
                        
                        Log.d(TAG, "✅ 头像上传成功: $avatarUrl")
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            isUploading = false,
                            error = "上传失败: ${error.message}"
                        )
                        Log.e(TAG, "❌ 头像上传失败", error)
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    error = "上传异常: ${e.message}"
                )
                Log.e(TAG, "❌ 头像上传异常", e)
            }
        }
    }

    /**
     * 保存机器人信息
     */
    fun saveBotInfo() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSaving = true,
                error = null
            )

            try {
                val result = botRepository.editBot(
                    botId = _uiState.value.botId,
                    nickname = _uiState.value.nickname,
                    introduction = _uiState.value.introduction,
                    avatarUrl = _uiState.value.avatarUrl,
                    isPrivate = _uiState.value.isPrivate
                )

                result.fold(
                    onSuccess = {
                        _uiState.value = _uiState.value.copy(
                            isSaving = false,
                            saveSuccess = true
                        )
                        Log.d(TAG, "✅ 机器人信息保存成功")
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            isSaving = false,
                            error = error.message ?: "保存失败"
                        )
                        Log.e(TAG, "❌ 保存机器人信息失败", error)
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = "保存异常: ${e.message}"
                )
                Log.e(TAG, "❌ 保存异常", e)
            }
        }
    }

    /**
     * 更新头像URL
     */
    fun updateAvatarUrl(url: String) {
        _uiState.value = _uiState.value.copy(avatarUrl = url)
    }

    /**
     * 更新机器人名称
     */
    fun updateNickname(nickname: String) {
        _uiState.value = _uiState.value.copy(nickname = nickname)
    }

    /**
     * 更新机器人简介
     */
    fun updateIntroduction(introduction: String) {
        _uiState.value = _uiState.value.copy(introduction = introduction)
    }

    /**
     * 更新隐私设置
     */
    fun updatePrivate(isPrivate: Boolean) {
        _uiState.value = _uiState.value.copy(isPrivate = isPrivate)
    }
}

data class BotEditUiState(
    val isLoading: Boolean = false,
    val isUploading: Boolean = false,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null,
    val botId: String = "",
    val avatarUrl: String = "",
    val nickname: String = "",
    val introduction: String = "",
    val isPrivate: Boolean = false
)

