package com.yhchat.canary.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yhchat.canary.data.api.ApiService
import com.yhchat.canary.data.di.RepositoryFactory
import com.yhchat.canary.data.model.SearchCategory
import com.yhchat.canary.data.model.SearchData
import com.yhchat.canary.data.model.SearchItem
import com.yhchat.canary.data.repository.ConversationRepository
import com.yhchat.canary.data.repository.FriendRepository
import com.yhchat.canary.data.repository.GroupRepository
import com.yhchat.canary.data.repository.TokenRepository
import com.yhchat.canary.data.repository.UserRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 申请入群状态
 */
data class JoinRequestState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null,
    val isChecking: Boolean = false,
    val isInConversations: Boolean = false
)

/**
 * 搜索ViewModel
 */
class SearchViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val apiService: ApiService = RepositoryFactory.apiService
    private val tokenRepository: TokenRepository = RepositoryFactory.getTokenRepository(appContext)
    private val friendRepository: FriendRepository = RepositoryFactory.getFriendRepository(appContext)
    private val conversationRepository: ConversationRepository = RepositoryFactory.getConversationRepository(appContext)

    private val userRepository: UserRepository
    private val groupRepository: GroupRepository = RepositoryFactory.getGroupRepository(appContext)

    init {
        userRepository = UserRepository(apiService, null)
        userRepository.setTokenRepository(tokenRepository)
    }

    // UI状态
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    // 搜索结果
    private val _searchResult = MutableStateFlow<SearchData?>(null)
    val searchResult: StateFlow<SearchData?> = _searchResult.asStateFlow()
    
    // 申请入群状态
    private val _joinRequestState = MutableStateFlow(JoinRequestState())
    val joinRequestState: StateFlow<JoinRequestState> = _joinRequestState.asStateFlow()

    /**
     * 执行搜索。
     *
     * 群聊不再走 home-search，而是先用「群口令」查 v1/group/info-add-friend；
     * 关键词对上群口令就直接把那个群作为结果返回。用户和机器人仍用 home-search。
     */
    fun search(word: String) {
        if (word.isBlank()) {
            _searchResult.value = null
            _uiState.value = _uiState.value.copy(error = "请输入搜索关键词")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val keyword = word.trim()

            // 两个接口并行：群口令查群 + home-search 查用户/机器人
            val (keywordGroup, homeSearchData) = coroutineScope {
                val groupDeferred = async { groupRepository.getGroupByKeyword(keyword).getOrNull() }
                val homeDeferred = async { userRepository.homeSearch(keyword).getOrNull() }
                groupDeferred.await() to homeDeferred.await()
            }

            if (keywordGroup == null && homeSearchData == null) {
                _searchResult.value = null
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "没有找到「$keyword」对应的群聊、用户或机器人"
                )
                return@launch
            }

            // 3. 合并：群口令命中的群放最前面，其余类别原样保留
            val categories = mutableListOf<SearchCategory>()

            if (keywordGroup != null) {
                categories += SearchCategory(
                    title = "群口令匹配",
                    list = listOf(
                        SearchItem(
                            friendId = keywordGroup.groupId,
                            friendType = 2,
                            nickname = keywordGroup.name,
                            name = null,
                            avatarUrl = keywordGroup.avatarUrl,
                            hit = 0
                        )
                    )
                )
            }

            homeSearchData?.list?.forEach { category ->
                val filtered = category.list
                    ?.filterNot { item ->
                        // 群口令已经命中的群，别在下面重复一份
                        keywordGroup != null && item.friendType == 2 && item.friendId == keywordGroup.groupId
                    }
                    .orEmpty()
                if (filtered.isNotEmpty()) {
                    categories += category.copy(list = filtered)
                }
            }

            _searchResult.value = SearchData(list = categories)
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    /**
     * 清除搜索结果
     */
    fun clearSearch() {
        _searchResult.value = null
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * 清除错误信息
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    /**
     * 申请入群
     */
    fun applyToJoinGroup(token: String, groupId: String) {
        viewModelScope.launch {
            try {
                _joinRequestState.value = _joinRequestState.value.copy(
                    isLoading = true,
                    error = null,
                    isSuccess = false
                )
                
                val response = friendRepository.applyFriend(
                    token = token,
                    chatId = groupId,
                    chatType = 2, // 群聊类型
                    remark = "申请加入群聊"
                )
                
                if (response.code == 1) {
                    _joinRequestState.value = _joinRequestState.value.copy(
                        isLoading = false,
                        isSuccess = true
                    )
                } else {
                    _joinRequestState.value = _joinRequestState.value.copy(
                        error = response.message,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _joinRequestState.value = _joinRequestState.value.copy(
                    error = "申请入群失败: ${e.message}",
                    isLoading = false
                )
            }
        }
    }
    
    /**
     * 重置申请状态
     */
    fun resetJoinRequestState() {
        _joinRequestState.value = JoinRequestState()
    }
    
    /**
     * 检查群聊是否已在会话列表中
     */
    suspend fun checkGroupInConversations(token: String, groupId: String): Boolean {
        return try {
            val result = conversationRepository.getConversations()
            if (result.isSuccess) {
                val conversations = result.getOrNull() ?: emptyList()
                conversations.any { conversation -> 
                    conversation.chatId == groupId && conversation.chatType == 2 // 2表示群聊
                }
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 处理群聊进入逻辑
     */
    fun handleGroupJoin(token: String, groupId: String) {
        viewModelScope.launch {
            try {
                _joinRequestState.value = _joinRequestState.value.copy(
                    isChecking = true,
                    error = null
                )
                
                val isInConversations = checkGroupInConversations(token, groupId)
                
                if (isInConversations) {
                    // 已在会话列表中，直接进入聊天
                    _joinRequestState.value = _joinRequestState.value.copy(
                        isChecking = false,
                        isInConversations = true
                    )
                } else {
                    // 不在会话列表中，申请加入
                    applyToJoinGroup(token, groupId)
                }
            } catch (e: Exception) {
                _joinRequestState.value = _joinRequestState.value.copy(
                    isChecking = false,
                    error = "检查失败: ${e.message}"
                )
            }
        }
    }
}

/**
 * 搜索UI状态
 */
data class SearchUiState(
    val isLoading: Boolean = false,
    val error: String? = null
)
