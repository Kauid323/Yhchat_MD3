package com.yhchat.canary.ui.create

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhchat.canary.data.di.RepositoryFactory
import com.yhchat.canary.data.repository.BotRepository
import com.yhchat.canary.data.repository.GroupRepository
import com.yhchat.canary.utils.ImageUploadUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CreateViewModel : ViewModel() {
    private lateinit var groupRepository: GroupRepository
    private lateinit var botRepository: BotRepository

    private val _groupUiState = MutableStateFlow(CreateUiState())
    val groupUiState: StateFlow<CreateUiState> = _groupUiState.asStateFlow()

    private val _botUiState = MutableStateFlow(CreateUiState())
    val botUiState: StateFlow<CreateUiState> = _botUiState.asStateFlow()

    companion object {
        private const val TAG = "CreateViewModel"
    }

    fun init(context: Context) {
        groupRepository = RepositoryFactory.getGroupRepository(context)
        botRepository = RepositoryFactory.getBotRepository(context)
    }

    fun uploadGroupAvatar(context: Context, uri: Uri, onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            _groupUiState.value = _groupUiState.value.copy(isLoading = true, error = null)
            try {
                val tokenRepository = RepositoryFactory.getTokenRepository(context)
                val userToken = tokenRepository.getTokenSync()
                if (userToken.isNullOrEmpty()) {
                    throw Exception("未登录")
                }

                val tokenResponse = RepositoryFactory.apiService.getQiniuImageToken(userToken)
                if (!tokenResponse.isSuccessful || tokenResponse.body()?.code != 1) {
                    throw Exception("获取上传token失败")
                }

                val uploadToken = tokenResponse.body()?.data?.token ?: throw Exception("上传token为空")

                val uploadResult = ImageUploadUtil.uploadImage(context, uri, uploadToken)
                uploadResult.fold(
                    onSuccess = { response ->
                        val imageUrl = "https://chat-img.jwznb.com/${response.key}"
                        Log.d(TAG, "✅ 群聊头像上传成功: $imageUrl")
                        onSuccess(imageUrl)
                        _groupUiState.value = _groupUiState.value.copy(isLoading = false)
                    },
                    onFailure = { error ->
                        throw error
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ 群聊头像上传失败", e)
                _groupUiState.value = _groupUiState.value.copy(
                    isLoading = false,
                    error = "头像上传失败: ${e.message}"
                )
            }
        }
    }

    fun uploadBotAvatar(context: Context, uri: Uri, onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            _botUiState.value = _botUiState.value.copy(isLoading = true, error = null)
            try {
                val tokenRepository = RepositoryFactory.getTokenRepository(context)
                val userToken = tokenRepository.getTokenSync()
                if (userToken.isNullOrEmpty()) {
                    throw Exception("未登录")
                }

                val tokenResponse = RepositoryFactory.apiService.getQiniuImageToken(userToken)
                if (!tokenResponse.isSuccessful || tokenResponse.body()?.code != 1) {
                    throw Exception("获取上传token失败")
                }

                val uploadToken = tokenResponse.body()?.data?.token ?: throw Exception("上传token为空")

                val uploadResult = ImageUploadUtil.uploadImage(context, uri, uploadToken)
                uploadResult.fold(
                    onSuccess = { response ->
                        val imageUrl = "https://chat-img.jwznb.com/${response.key}"
                        Log.d(TAG, "✅ 机器人头像上传成功: $imageUrl")
                        onSuccess(imageUrl)
                        _botUiState.value = _botUiState.value.copy(isLoading = false)
                    },
                    onFailure = { error ->
                        throw error
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ 机器人头像上传失败", e)
                _botUiState.value = _botUiState.value.copy(
                    isLoading = false,
                    error = "头像上传失败: ${e.message}"
                )
            }
        }
    }

    fun createGroup(
        name: String,
        introduction: String,
        avatarUrl: String,
        category: String,
        categoryId: Int,
        onSuccess: (String) -> Unit
    ) {
        viewModelScope.launch {
            Log.d(TAG, "🏗️ 创建群聊: name=$name, introduction=$introduction, avatarUrl=$avatarUrl, category=$category, categoryId=$categoryId")
            _groupUiState.value = _groupUiState.value.copy(isLoading = true, error = null)

            groupRepository.createGroup(
                name = name,
                introduction = introduction,
                avatarUrl = avatarUrl,
                category = category,
                categoryId = categoryId
            ).fold(
                onSuccess = { groupId ->
                    Log.d(TAG, "✅ 群聊创建成功: groupId=$groupId")
                    _groupUiState.value = _groupUiState.value.copy(isLoading = false)
                    onSuccess(groupId)
                },
                onFailure = { error ->
                    Log.e(TAG, "❌ 群聊创建失败", error)
                    _groupUiState.value = _groupUiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "创建失败"
                    )
                }
            )
        }
    }

    fun createBot(
        name: String,
        introduction: String,
        avatarUrl: String,
        isPrivate: Boolean,
        onSuccess: (String) -> Unit
    ) {
        viewModelScope.launch {
            Log.d(TAG, "🤖 创建机器人: name=$name, introduction=$introduction, avatarUrl=$avatarUrl, isPrivate=$isPrivate")
            _botUiState.value = _botUiState.value.copy(isLoading = true, error = null)

            botRepository.createBot(
                name = name,
                introduction = introduction,
                avatarUrl = avatarUrl,
                isPrivate = isPrivate
            ).fold(
                onSuccess = { botId ->
                    Log.d(TAG, "✅ 机器人创建成功: botId=$botId")
                    _botUiState.value = _botUiState.value.copy(isLoading = false)
                    onSuccess(botId)
                },
                onFailure = { error ->
                    Log.e(TAG, "❌ 机器人创建失败", error)
                    _botUiState.value = _botUiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "创建失败"
                    )
                }
            )
        }
    }
}

data class CreateUiState(
    val isLoading: Boolean = false,
    val error: String? = null
)
