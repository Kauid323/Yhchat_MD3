package com.yhchat.canary.ui.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhchat.canary.data.model.ChatAddInfo
import com.yhchat.canary.data.model.ChatAddType
import com.yhchat.canary.data.model.UserHomepageInfo
import com.yhchat.canary.data.repository.TokenRepository
import com.yhchat.canary.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 聊天添加ViewModel
 */
@HiltViewModel
class ChatAddViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val tokenRepository: TokenRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ChatAddUiState())
    val uiState: StateFlow<ChatAddUiState> = _uiState.asStateFlow()
    
    /**
     * 加载分享信息（yhfx分享链接）
     */
    fun loadShareInfo(key: String, ts: String) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    error = null
                )
                
                Log.d("ChatAddViewModel", "开始加载分享信息: key=$key, ts=$ts")
                
                userRepository.getShareInfo(key, ts).fold(
                    onSuccess = { shareInfo ->
                        // 根据分享信息构建 ChatAddInfo
                        val chatAddType = when (shareInfo.chatType) {
                            1 -> ChatAddType.USER
                            2 -> ChatAddType.GROUP
                            3 -> ChatAddType.BOT
                            else -> {
                                _uiState.value = _uiState.value.copy(
                                    isLoading = false,
                                    error = "不支持的会话类型"
                                )
                                return@fold
                            }
                        }
                        
                        val chatAddInfo = ChatAddInfo(
                            id = shareInfo.chatId,
                            type = chatAddType,
                            displayName = shareInfo.chatName ?: "",
                            avatarUrl = if (shareInfo.imageUrl?.startsWith("http") == true) {
                                shareInfo.imageUrl
                            } else {
                                "https://chat-img.jwznb.com/${shareInfo.imageUrl ?: ""}"
                            },
                            description = ""
                        )
                        
                        // 继续加载详细信息
                        loadChatInfo(chatAddInfo)
                    },
                    onFailure = { exception ->
                        Log.e("ChatAddViewModel", "加载分享信息失败", exception)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = exception.message ?: "加载分享信息失败"
                        )
                    }
                )
            } catch (e: Exception) {
                Log.e("ChatAddViewModel", "加载分享信息异常", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "加载失败"
                )
            }
        }
    }
    
    /**
     * 加载聊天信息
     */
    fun loadChatInfo(chatAddInfo: ChatAddInfo) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    error = null,
                    chatInfo = chatAddInfo
                )
                
                Log.d("ChatAddViewModel", "开始加载聊天信息: type=${chatAddInfo.type}, id=${chatAddInfo.id}")
                
                when (chatAddInfo.type) {
                    ChatAddType.USER -> loadUserInfo(chatAddInfo)
                    ChatAddType.GROUP -> loadGroupInfo(chatAddInfo)
                    ChatAddType.BOT -> loadBotInfo(chatAddInfo)
                }
            } catch (e: Exception) {
                Log.e("ChatAddViewModel", "加载聊天信息异常", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "加载失败"
                )
            }
        }
    }
    
    /**
     * 加载用户信息
     */
    private suspend fun loadUserInfo(chatAddInfo: ChatAddInfo) {
        userRepository.getUserHomepage(chatAddInfo.id).fold(
            onSuccess = { userInfo ->
                val updatedChatInfo = chatAddInfo.copy(
                    displayName = userInfo.nickname,
                    avatarUrl = userInfo.avatarUrl,
                    description = buildUserDescription(userInfo),
                    additionalInfo = if (userInfo.isVip == 1) "VIP用户" else ""
                )
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    chatInfo = updatedChatInfo,
                    error = null
                )
            },
            onFailure = { exception ->
                Log.e("ChatAddViewModel", "加载用户信息失败", exception)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = exception.message ?: "加载用户信息失败"
                )
            }
        )
    }
    
    /**
     * 加载群聊信息
     */
    private suspend fun loadGroupInfo(chatAddInfo: ChatAddInfo) {
        userRepository.getGroupInfo(chatAddInfo.id).fold(
            onSuccess = { groupInfo ->
                val updatedChatInfo = chatAddInfo.copy(
                    displayName = groupInfo.name,
                    avatarUrl = groupInfo.avatarUrl,
                    description = groupInfo.introduction,
                    additionalInfo = "${groupInfo.memberCount} 人"
                )
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    chatInfo = updatedChatInfo,
                    error = null
                )
            },
            onFailure = { exception ->
                Log.e("ChatAddViewModel", "加载群聊信息失败", exception)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = exception.message ?: "加载群聊信息失败"
                )
            }
        )
    }
    
    /**
     * 加载机器人信息
     */
    private suspend fun loadBotInfo(chatAddInfo: ChatAddInfo) {
        userRepository.getBotInfo(chatAddInfo.id).fold(
            onSuccess = { botInfo ->
                val updatedChatInfo = chatAddInfo.copy(
                    displayName = botInfo.nickname,
                    avatarUrl = botInfo.avatarUrl,
                    description = botInfo.introduction,
                    additionalInfo = if (botInfo.isPrivate == 1) "私有机器人" else "公开机器人"
                )
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    chatInfo = updatedChatInfo,
                    error = null
                )
            },
            onFailure = { exception ->
                Log.e("ChatAddViewModel", "加载机器人信息失败", exception)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = exception.message ?: "加载机器人信息失败"
                )
            }
        )
    }
    
    /**
     * 添加聊天对象
     */
    fun addChat() {
        viewModelScope.launch {
            try {
                val chatInfo = _uiState.value.chatInfo ?: return@launch
                
                Log.d("ChatAddViewModel", "开始添加聊天对象: type=${chatInfo.type}, id=${chatInfo.id}")
                
                _uiState.value = _uiState.value.copy(
                    isAdding = true,
                    addError = null
                )
                
                // 严格遵守Deep Link类型映射规范：user对应1，group对应2，bot对应3
                userRepository.addFriend(
                    chatId = chatInfo.id,
                    chatType = chatInfo.type.chatType, // 使用正确的chatType映射
                    remark = "通过分享链接添加"
                ).fold(
                    onSuccess = {
                        Log.d("ChatAddViewModel", "添加成功")
                        _uiState.value = _uiState.value.copy(
                            isAdding = false,
                            isAddSuccess = true,
                            addError = null
                        )
                    },
                    onFailure = { exception ->
                        Log.e("ChatAddViewModel", "添加聊天对象失败", exception)
                        _uiState.value = _uiState.value.copy(
                            isAdding = false,
                            addError = exception.message ?: "添加失败"
                        )
                    }
                )
            } catch (e: Exception) {
                Log.e("ChatAddViewModel", "添加聊天对象异常", e)
                _uiState.value = _uiState.value.copy(
                    isAdding = false,
                    addError = e.message ?: "添加失败"
                )
            }
        }
    }
    
    /**
     * 构建用户描述信息
     */
    private fun buildUserDescription(userInfo: UserHomepageInfo): String {
        val parts = mutableListOf<String>()
        
        if (userInfo.registerTimeText.isNotEmpty()) {
            parts.add("注册时间: ${userInfo.registerTimeText}")
        }
        
        if (userInfo.onLineDay > 0) {
            parts.add("在线天数: ${userInfo.onLineDay} 天")
        }
        
        if (userInfo.continuousOnLineDay > 0) {
            parts.add("连续在线: ${userInfo.continuousOnLineDay} 天")
        }
        
        if (userInfo.medals.isNotEmpty()) {
            val medalNames = userInfo.medals.take(3).map { it.name }
            parts.add("勋章: ${medalNames.joinToString(", ")}")
        }
        
        return parts.joinToString("\n")
    }
}

/**
 * 聊天添加UI状态
 */
data class ChatAddUiState(
    val isLoading: Boolean = false,
    val chatInfo: ChatAddInfo? = null,
    val error: String? = null,
    val isAdding: Boolean = false,
    val isAddSuccess: Boolean = false,
    val addError: String? = null
)