package com.yhchat.canary.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yhchat.canary.data.di.RepositoryFactory
import com.yhchat.canary.data.model.BotInfo
import com.yhchat.canary.data.model.GroupDetail
import com.yhchat.canary.data.model.UserInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ComprehensiveSearchViewModel(application: Application) : AndroidViewModel(application) {
    
    private val webApiService = RepositoryFactory.webApiService
    private val friendRepository = RepositoryFactory.getFriendRepository(application)
    private val tokenRepository = RepositoryFactory.getTokenRepository(application)
    private val groupRepository = RepositoryFactory.getGroupRepository(application)
    
    private val _uiState = MutableStateFlow(ComprehensiveSearchUiState())
    val uiState: StateFlow<ComprehensiveSearchUiState> = _uiState.asStateFlow()
    
    /**
     * 群聊搜索。
     *
     * 走 proto 的 /v1/group/info-add-friend（群口令查询），和 [SearchViewModel] 保持一致。
     *
     * 以前这里用的是 webApiService.getGroupInfo（Web 端 JSON 接口），有两个问题：
     *   1. JSON 响应缺字段，只能拿一堆硬编码值兜底（permissionLevel=0、adminIds=空、
     *      communityName=""、directJoin 和 historyMsgEnabled 还被赋成同一个 readHistory），
     *      导致搜索结果里私密/免打扰/社区/管理员这些信息全是错的；
     *   2. 它查的是 groupId，而群口令搜索才是这个入口真正要的语义。
     *
     * proto 接口返回的 GroupDetail 字段是全的，不需要再猜。
     */
    fun searchGroup(keyword: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            groupRepository.getGroupByKeyword(keyword).fold(
                onSuccess = { group ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        groupResult = group,
                        error = null
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        groupResult = null,
                        // 群口令查不到时服务端返回的是「无此群」之类的文案，直接透给用户；
                        // 空消息就退回到通用提示，避免 Toast 上什么都看不到
                        error = error.message?.takeIf { it.isNotBlank() } ?: "未找到对应的群聊"
                    )
                }
            )
        }
    }
    
    fun searchUser(userId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                val response = webApiService.getUserHomepage(userId)
                
                if (response.isSuccessful && response.body()?.code == 1) {
                    val userProfile = response.body()!!.data.user
                    val userInfo = UserInfo(
                        userId = userProfile.userId,
                        nickname = userProfile.nickname,
                        avatarUrl = userProfile.avatarUrl,
                        registerTime = userProfile.registerTime,
                        registerTimeText = userProfile.registerTimeText,
                        onLineDay = userProfile.onLineDay,
                        continuousOnLineDay = userProfile.continuousOnLineDay,
                        isVip = userProfile.isVip
                    )
                    
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        userResult = userInfo,
                        error = null
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = response.body()?.msg ?: "获取用户信息失败"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "网络错误"
                )
            }
        }
    }
    
    fun searchBot(botId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                val response = webApiService.getBotInfo(mapOf("botId" to botId))
                
                if (response.isSuccessful) {
                    val responseBody = response.body()
                    if (responseBody?.code == 1) {
                        val botData = responseBody.data.bot
                        val botInfo = BotInfo(
                            id = botData.id,
                            botId = botData.botId,
                            nickname = botData.nickname,
                            nicknameId = botData.nicknameId,
                            avatarId = botData.avatarId,
                            avatarUrl = botData.avatarUrl,
                            introduction = botData.introduction,
                            createBy = botData.createBy,
                            createTime = botData.createTime,
                            headcount = botData.headcount,
                            isPrivate = botData.isPrivate ?: 0
                        )
                        
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            botResult = botInfo,
                            error = null
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = responseBody?.msg ?: "获取机器人信息失败"
                        )
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = response.body()?.msg ?: "获取机器人信息失败"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "网络错误"
                )
            }
        }
    }
    
    fun addGroup(groupId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAdding = true)
            
            try {
                val token = tokenRepository.getTokenSync() ?: ""
                val result = friendRepository.applyFriend(token, groupId, 2, "申请加入群聊")
                
                if (result.code == 1) {
                    _uiState.value = _uiState.value.copy(
                        isAdding = false,
                        showGroupDialog = false,
                        error = null
                    )
                    // TODO: 显示成功提示
                } else {
                    _uiState.value = _uiState.value.copy(
                        isAdding = false,
                        error = result.message
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isAdding = false,
                    error = e.message ?: "添加群聊失败"
                )
            }
        }
    }
    
    fun addUser(userId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAdding = true)
            
            try {
                val token = tokenRepository.getTokenSync() ?: ""
                val result = friendRepository.applyFriend(token, userId, 1, "申请添加好友")
                
                if (result.code == 1) {
                    _uiState.value = _uiState.value.copy(
                        isAdding = false,
                        showUserDialog = false,
                        error = null
                    )
                    // TODO: 显示成功提示
                } else {
                    _uiState.value = _uiState.value.copy(
                        isAdding = false,
                        error = result.message
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isAdding = false,
                    error = e.message ?: "添加用户失败"
                )
            }
        }
    }
    
    fun addBot(botId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAdding = true)
            
            try {
                val token = tokenRepository.getTokenSync() ?: ""
                val result = friendRepository.applyFriend(token, botId, 3, "添加机器人")
                
                if (result.code == 1) {
                    _uiState.value = _uiState.value.copy(
                        isAdding = false,
                        showBotDialog = false,
                        error = null
                    )
                    // TODO: 显示成功提示
                } else {
                    _uiState.value = _uiState.value.copy(
                        isAdding = false,
                        error = result.message
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isAdding = false,
                    error = e.message ?: "添加机器人失败"
                )
            }
        }
    }
    
    fun showGroupDialog(group: GroupDetail) {
        _uiState.value = _uiState.value.copy(showGroupDialog = true)
    }
    
    fun hideGroupDialog() {
        _uiState.value = _uiState.value.copy(showGroupDialog = false)
    }
    
    fun showUserDialog(user: UserInfo) {
        _uiState.value = _uiState.value.copy(showUserDialog = true)
    }
    
    fun hideUserDialog() {
        _uiState.value = _uiState.value.copy(showUserDialog = false)
    }
    
    fun showBotDialog(bot: BotInfo) {
        _uiState.value = _uiState.value.copy(showBotDialog = true)
    }
    
    fun hideBotDialog() {
        _uiState.value = _uiState.value.copy(showBotDialog = false)
    }
    
    fun clearResults() {
        _uiState.value = _uiState.value.copy(
            groupResult = null,
            userResult = null,
            botResult = null,
            error = null
        )
    }
}

data class ComprehensiveSearchUiState(
    val isLoading: Boolean = false,
    val isAdding: Boolean = false,
    val error: String? = null,
    val groupResult: GroupDetail? = null,
    val userResult: UserInfo? = null,
    val botResult: BotInfo? = null,
    val showGroupDialog: Boolean = false,
    val showUserDialog: Boolean = false,
    val showBotDialog: Boolean = false
)
