package com.yhchat.canary.ui.bot

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yhchat.canary.data.di.RepositoryFactory
import com.yhchat.canary.data.repository.BotRepository
import com.yhchat.canary.data.repository.CacheRepository
import com.yhchat.canary.data.repository.FriendRepository
import com.yhchat.canary.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.yhchat.canary.proto.bot.board
import com.yhchat.canary.proto.bot.bot_info

/**
 * 机器人详细信息 ViewModel
 */
class BotDetailViewModel(application: Application) : AndroidViewModel(application) {
    
    companion object {
        private const val TAG = "BotDetailViewModel"
    }
    
    private val botRepository: BotRepository by lazy {
        RepositoryFactory.getBotRepository(application)
    }
    private val friendRepository: FriendRepository by lazy {
        RepositoryFactory.getFriendRepository(application)
    }
    private val userRepository: UserRepository by lazy {
        RepositoryFactory.getUserRepository(application)
    }
    private val cacheRepository: CacheRepository by lazy {
        CacheRepository(application)
    }
    private val tokenRepository by lazy {
        RepositoryFactory.getTokenRepository(application)
    }
    
    private val _uiState = MutableStateFlow(BotDetailUiState())
    val uiState: StateFlow<BotDetailUiState> = _uiState.asStateFlow()
    
    /**
     * 加载机器人详细信息
     */
    fun loadBotDetail(botId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null
            )
            
            Log.d(TAG, "开始加载机器人详细信息: $botId")
            
            val result = botRepository.getBotInfo(botId)
            
            result.fold(
                onSuccess = { botInfo ->
                    Log.d(TAG, "✅ 机器人信息加载成功")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        botInfo = botInfo,
                        error = null
                    )
                },
                onFailure = { error ->
                    Log.e(TAG, "❌ 机器人信息加载失败", error)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "加载失败"
                    )
                }
            )
        }
    }
    
    /**
     * 加载看板信息
     */
    fun loadBoardInfo(chatId: String, chatType: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isBoardLoading = true,
                boardError = null
            )
            
            Log.d(TAG, "开始加载看板信息: chatId=$chatId, chatType=$chatType")
            
            val result = botRepository.getBotBoard(chatId, chatType)
            
            result.fold(
                onSuccess = { board ->
                    Log.d(TAG, "✅ 看板信息加载成功")
                    _uiState.value = _uiState.value.copy(
                        isBoardLoading = false,
                        boardInfo = board,
                        boardError = null
                    )
                },
                onFailure = { error ->
                    Log.e(TAG, "❌ 看板信息加载失败", error)
                    _uiState.value = _uiState.value.copy(
                        isBoardLoading = false,
                        boardError = error.message ?: "加载失败"
                    )
                }
            )
        }
    }

    fun loadInteractionState(botId: String) {
        viewModelScope.launch {
            val cachedConversation = cacheRepository
                .getCachedConversationsSync()
                .firstOrNull { it.chatId == botId && it.chatType == 3 }

            _uiState.update {
                it.copy(
                    isNoNotify = cachedConversation?.doNotDisturb == 1,
                    isCheckingAddressBook = true
                )
            }

            friendRepository.getAddressBookList().fold(
                onSuccess = { addressBook ->
                    val isInAddressBook = addressBook.dataList.any { group ->
                        group.dataList.any { item -> item.chatId == botId }
                    }
                    _uiState.update {
                        it.copy(
                            isInAddressBook = isInAddressBook,
                            isCheckingAddressBook = false
                        )
                    }
                },
                onFailure = {
                    _uiState.update {
                        it.copy(
                            isInAddressBook = false,
                            isCheckingAddressBook = false
                        )
                    }
                }
            )
        }
    }

    fun addBot(botId: String) {
        viewModelScope.launch {
            if (_uiState.value.isAddingBot) return@launch
            _uiState.update { it.copy(isAddingBot = true) }

            val localToken = tokenRepository.getTokenSync() ?: ""
            val result = friendRepository.applyFriend(
                token = localToken,
                chatId = botId,
                chatType = 3,
                remark = "添加机器人"
            )

            if (result.code == 1) {
                _uiState.update {
                    it.copy(
                        isAddingBot = false,
                        isInAddressBook = true,
                        actionMessage = "已添加机器人"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isAddingBot = false,
                        actionMessage = "添加机器人失败: ${result.message}"
                    )
                }
            }
        }
    }

    fun setNoNotify(botId: String, checked: Boolean) {
        viewModelScope.launch {
            if (_uiState.value.isSettingNoNotify) return@launch
            _uiState.update { it.copy(isNoNotify = checked, isSettingNoNotify = true) }

            friendRepository.setNoNotify(
                chatId = botId,
                noNotify = if (checked) 1 else 0
            ).fold(
                onSuccess = {
                    cacheRepository.updateConversationDoNotDisturb(
                        chatId = botId,
                        doNotDisturb = if (checked) 1 else 0
                    )
                    _uiState.update {
                        it.copy(
                            isSettingNoNotify = false,
                            actionMessage = "设置成功"
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isNoNotify = !checked,
                            isSettingNoNotify = false,
                            actionMessage = "设置失败：${error.message}"
                        )
                    }
                }
            )
        }
    }

    fun deleteBot(botId: String) {
        viewModelScope.launch {
            userRepository.deleteFriend(botId, 3).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            actionMessage = "已删除机器人",
                            deleteSuccess = true
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(actionMessage = "删除机器人失败: ${error.message}")
                    }
                }
            )
        }
    }

    fun clearBotContext(botId: String) {
        viewModelScope.launch {
            if (_uiState.value.isClearingContext) return@launch
            _uiState.update { it.copy(isClearingContext = true) }

            botRepository.cleanBotContext(botId).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isClearingContext = false,
                            actionMessage = "已清空机器人上下文"
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isClearingContext = false,
                            actionMessage = "清空上下文失败：${error.message ?: "未知错误"}"
                        )
                    }
                }
            )
        }
    }

    fun consumeActionMessage() {
        _uiState.update { it.copy(actionMessage = null) }
    }

    fun consumeDeleteSuccess() {
        _uiState.update { it.copy(deleteSuccess = false) }
    }
}

/**
 * 机器人详细信息 UI 状态
 */
data class BotDetailUiState(
    val isLoading: Boolean = false,
    val botInfo: bot_info? = null,
    val error: String? = null,
    val isBoardLoading: Boolean = false,
    val boardInfo: board? = null,
    val boardError: String? = null,
    val isInAddressBook: Boolean = false,
    val isCheckingAddressBook: Boolean = true,
    val isAddingBot: Boolean = false,
    val isNoNotify: Boolean = false,
    val isSettingNoNotify: Boolean = false,
    val actionMessage: String? = null,
    val deleteSuccess: Boolean = false,
    val isClearingContext: Boolean = false
)
