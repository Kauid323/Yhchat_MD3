package com.yhchat.canary.ui.bot

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhchat.canary.data.api.ApiClient
import com.yhchat.canary.data.di.RepositoryFactory
import com.yhchat.canary.data.model.CreatedBot
import com.yhchat.canary.data.model.DeleteFriendRequest
import com.yhchat.canary.data.repository.BotRepository
import com.yhchat.canary.data.repository.TokenRepository
import com.yhchat.canary.proto.bot.bot_stop
import com.yhchat.canary.proto.bot.bot_stop_send
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class BotManagementViewModel : ViewModel() {
    private lateinit var tokenRepository: TokenRepository
    private lateinit var botRepository: BotRepository

    private val _uiState = MutableStateFlow(BotManagementUiState())
    val uiState: StateFlow<BotManagementUiState> = _uiState.asStateFlow()

    fun init(context: Context) {
        val appContext = context.applicationContext
        tokenRepository = RepositoryFactory.getTokenRepository(appContext)
        botRepository = RepositoryFactory.getBotRepository(appContext)
    }

    fun load(botId: String, initialToken: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, currentBotToken = initialToken) }

            try {
                botRepository.getMyBotList().fold(
                    onSuccess = { bots ->
                        val currentToken = bots.firstOrNull { it.botId == botId }?.token ?: initialToken
                        _uiState.update { it.copy(botList = bots, currentBotToken = currentToken) }
                    },
                    onFailure = {
                    }
                )

                botRepository.getBotInfo(botId).fold(
                    onSuccess = { botInfo ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                botIsStop = botInfo.data.isStop == 1L
                            )
                        }
                    },
                    onFailure = {
                        _uiState.update { state -> state.copy(isLoading = false) }
                    }
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun toggleBotStop(botId: String) {
        if (_uiState.value.isStoppingBot) return
        val operation = if (_uiState.value.botIsStop) 0 else 1
        _uiState.update { it.copy(isStoppingBot = true, error = null, actionMessage = null) }
        viewModelScope.launch {
            try {
                val userToken = tokenRepository.getTokenSync()
                check(!userToken.isNullOrBlank()) { "未登录，请重新登录后操作" }
                val request = bot_stop_send.newBuilder()
                    .setBotId(botId)
                    .setOperation(operation.toLong())
                    .build()
                val requestBody = request.toByteArray()
                    .toRequestBody("application/x-protobuf".toMediaType())
                val response = ApiClient.apiService.stopBot(userToken, requestBody)
                if (!response.isSuccessful) {
                    response.errorBody()?.close()
                    error("请求失败: HTTP ${response.code()}")
                }
                val bytes = response.body()?.use { it.bytes() }
                check(bytes != null && bytes.isNotEmpty()) { "服务器响应为空，无法确认操作结果" }
                val result = bot_stop.parseFrom(bytes)
                check(result.hasStatus()) { "服务器响应缺少操作状态，无法确认操作结果" }
                val status = result.status
                check(status.code == 1) {
                    status.msg.takeIf { it.isNotBlank() } ?: "机器人操作失败（状态码 ${status.code}）"
                }
                // 接口已经确认成功，直接应用目标状态，避免紧接着读取到旧缓存又把开关改回去。
                _uiState.update {
                    it.copy(
                        botIsStop = operation == 1,
                        error = null,
                        actionMessage = if (operation == 1) "机器人停用成功" else "机器人启用成功"
                    )
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(error = error.message?.takeIf(String::isNotBlank) ?: "机器人操作失败，请稍后重试")
                }
            } finally {
                _uiState.update { it.copy(isStoppingBot = false) }
            }
        }
    }

    fun deleteBot(botId: String) {
        viewModelScope.launch {
            val userToken = tokenRepository.getTokenSync() ?: return@launch
            _uiState.update { it.copy(isDeletingBot = true, error = null) }

            val request = DeleteFriendRequest(
                chatId = botId,
                chatType = 3
            )

            runCatching {
                ApiClient.apiService.deleteFriend(userToken, request)
            }.onSuccess { resp ->
                if (resp.body()?.code == 1) {
                    _uiState.update {
                        it.copy(
                            isDeletingBot = false,
                            actionMessage = "机器人删除成功",
                            deleteSuccess = true
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isDeletingBot = false,
                            error = resp.body()?.message ?: "删除失败"
                        )
                    }
                }
            }.onFailure { error ->
                _uiState.update { it.copy(isDeletingBot = false, error = error.message) }
            }
        }
    }

    fun consumeActionMessage() {
        _uiState.update { it.copy(actionMessage = null) }
    }

    fun consumeDeleteSuccess() {
        _uiState.update { it.copy(deleteSuccess = false) }
    }
}

data class BotManagementUiState(
    val botList: List<CreatedBot> = emptyList(),
    val isLoading: Boolean = true,
    val isStoppingBot: Boolean = false,
    val isDeletingBot: Boolean = false,
    val botIsStop: Boolean = false,
    val currentBotToken: String = "",
    val error: String? = null,
    val actionMessage: String? = null,
    val deleteSuccess: Boolean = false
)
