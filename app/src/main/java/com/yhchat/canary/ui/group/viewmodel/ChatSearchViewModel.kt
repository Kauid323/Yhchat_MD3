package com.yhchat.canary.ui.group

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhchat.canary.data.api.ApiService
import com.yhchat.canary.data.model.ChatMessage
import com.yhchat.canary.data.model.ChatSearchMessage
import com.yhchat.canary.data.repository.MessageRepository
import com.yhchat.canary.data.repository.TokenRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject

data class ChatSearchState(
    val isLoading: Boolean = false,
    val searchResults: List<ChatMessage> = emptyList(),
    val error: String? = null,
    val searchQuery: String = "",
    val isLoadingMore: Boolean = false,
    val hasMoreResults: Boolean = true,
    val currentPage: Int = 0,
    val selectedMessage: ChatMessage? = null,
    val isLoadingMessageDetail: Boolean = false,
    val lastTimestamp: Long = 9999999999999L // 记录最后一条消息的时间戳
)

@HiltViewModel
class ChatSearchViewModel @Inject constructor(
    private val apiService: ApiService,
    private val tokenRepository: TokenRepository,
    private val messageRepository: MessageRepository
) : ViewModel() {
    
    private val _state = MutableStateFlow(ChatSearchState())
    val state: StateFlow<ChatSearchState> = _state.asStateFlow()
    
    companion object {
        private const val TAG = "ChatSearchViewModel"
    }
    
    fun updateSearchQuery(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
    }
    
    fun searchMessages(chatId: String, chatType: Int, query: String, isLoadMore: Boolean = false) {
        Log.d(TAG, "searchMessages: query='$query', chatId='$chatId', chatType=$chatType, isLoadMore=$isLoadMore")
        
        if (query.isBlank()) {
            Log.d(TAG, "searchMessages: query is blank, clearing results")
            _state.value = _state.value.copy(
                searchResults = emptyList(),
                currentPage = 0,
                hasMoreResults = true,
                lastTimestamp = 9999999999999L
            )
            return
        }
        
        viewModelScope.launch {
            if (isLoadMore) {
                Log.d(TAG, "searchMessages: loading more results, current page: ${_state.value.currentPage}")
                _state.value = _state.value.copy(isLoadingMore = true)
            } else {
                Log.d(TAG, "searchMessages: starting new search")
                _state.value = _state.value.copy(
                    isLoading = true, 
                    error = null,
                    currentPage = 0,
                    hasMoreResults = true,
                    lastTimestamp = 9999999999999L
                )
            }
            
            try {
                val userToken = tokenRepository.getToken().first()
                val token = userToken?.token
                Log.d(TAG, "searchMessages: got token, length=${token?.length ?: 0}")
                
                if (token.isNullOrBlank()) {
                    Log.w(TAG, "searchMessages: token is null or blank")
                    _state.value = _state.value.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        error = "用户未登录"
                    )
                    return@launch
                }
                
                val pageSize = 30
                // 使用时间戳进行分页，而不是累积数量
                val timestamp = if (isLoadMore) _state.value.lastTimestamp else 9999999999999L
                Log.d(TAG, "searchMessages: requesting with timestamp=$timestamp, pageSize=$pageSize, isLoadMore=$isLoadMore")

                val json = JSONObject().apply {
                    // keyword / word
                    put("keyword", query)
                    put("word", query)

                    // chatId / chat_id
                    put("chatId", chatId)
                    put("chat_id", chatId)

                    // chatType / chat_type
                    put("chatType", chatType)
                    put("chat_type", chatType)

                    // other fields
                    put("type", "all")
                    put("size", pageSize)
                    put("time", timestamp)
                    put("direction", 1)
                }

                val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

                val response = apiService.searchChatMessagesRaw(token, requestBody)
                Log.d(TAG, "searchMessages: API response received, isSuccessful=${response.isSuccessful}, code=${response.code()}")
                
                if (response.isSuccessful) {
                    val body = response.body()
                    Log.d(TAG, "searchMessages: response body code=${body?.code}, msg='${body?.msg}'")
                    
                    if (body?.code == 1) {
                        val searchResults = body.data?.list ?: emptyList()
                        Log.d(TAG, "searchMessages: got ${searchResults.size} search results")
                        
                        if (searchResults.isEmpty() && !isLoadMore) {
                            Log.d(TAG, "searchMessages: no results found for new search")
                            _state.value = _state.value.copy(
                                isLoading = false,
                                isLoadingMore = false,
                                searchResults = emptyList(),
                                hasMoreResults = false,
                                error = null
                            )
                            return@launch
                        }
                        
                        // 直接将搜索结果转换为ChatMessage对象（用于列表显示）
                        Log.d(TAG, "searchMessages: converting ${searchResults.size} search results to ChatMessage objects")
                        val fullMessages = searchResults.map { searchMessage ->
                            convertSearchMessageToChatMessage(searchMessage)
                        }
                        
                        Log.d(TAG, "searchMessages: successfully fetched ${fullMessages.size} full messages")
                        
                        val currentResults = if (isLoadMore) _state.value.searchResults else emptyList()
                        val newResults = if (isLoadMore) {
                            // 去重：只添加不在当前结果中的消息
                            val existingIds = currentResults.map { it.msgId }.toSet()
                            val filtered = fullMessages.filter { it.msgId !in existingIds }
                            Log.d(TAG, "searchMessages: filtered ${filtered.size} new messages from ${fullMessages.size} total")
                            filtered
                        } else {
                            fullMessages
                        }
                        
                        val finalResults = currentResults + newResults
                        
                        // 判断是否还有更多结果
                        // 只有在加载更多时返回空结果或去重后没有新消息时才认为没有更多
                        val hasMore = if (isLoadMore && (searchResults.isEmpty() || newResults.isEmpty())) {
                            Log.d(TAG, "searchMessages: load more returned empty or no new results, no more results")
                            false
                        } else {
                            // 第一次搜索或有新结果时，假设还有更多（让用户滑到底部时再判断）
                            true
                        }
                        
                        // 计算新的最老时间戳（用于下次加载更多）
                        // 从当前所有结果中找最老的消息时间戳
                        val newLastTimestamp = if (finalResults.isNotEmpty()) {
                            finalResults.minOfOrNull { it.sendTime } ?: 9999999999999L
                        } else {
                            9999999999999L
                        }
                        
                        Log.d(TAG, "searchMessages: final=${finalResults.size}, new=${newResults.size}, hasMore=$hasMore, timestamp=$newLastTimestamp")
                        
                        _state.value = _state.value.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            searchResults = finalResults,
                            hasMoreResults = hasMore,
                            lastTimestamp = newLastTimestamp,
                            error = null
                        )
                    } else {
                        Log.w(TAG, "searchMessages: API returned error code=${body?.code}, msg='${body?.msg}'")
                        _state.value = _state.value.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            error = body?.msg ?: "搜索失败"
                        )
                    }
                } else {
                    Log.e(TAG, "searchMessages: HTTP error ${response.code()}: ${response.message()}")
                    _state.value = _state.value.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        error = "网络请求失败: ${response.code()}"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "searchMessages: exception occurred", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    error = "搜索出错: ${e.message}"
                )
            }
        }
    }
    
    fun loadMoreResults(chatId: String, chatType: Int, query: String) {
        Log.d(TAG, "loadMoreResults: isLoadingMore=${_state.value.isLoadingMore}, hasMoreResults=${_state.value.hasMoreResults}")
        if (_state.value.isLoadingMore || !_state.value.hasMoreResults) {
            Log.d(TAG, "loadMoreResults: skipping - already loading or no more results")
            return
        }
        // 使用当前保存的搜索词，而不是传入的参数
        val currentQuery = _state.value.searchQuery.takeIf { it.isNotBlank() } ?: query
        Log.d(TAG, "loadMoreResults: triggering search for more results with query='$currentQuery', lastTimestamp=${_state.value.lastTimestamp}")
        searchMessages(chatId, chatType, currentQuery, isLoadMore = true)
    }
    
    fun showMessageDetail(message: ChatMessage) {
        Log.d(TAG, "showMessageDetail: showing message ${message.msgId}")
        // 直接显示搜索结果中的消息，不需要重新获取
        _state.value = _state.value.copy(selectedMessage = message)
    }
    
    fun hideMessageDetail() {
        Log.d(TAG, "hideMessageDetail: hiding message detail")
        _state.value = _state.value.copy(selectedMessage = null)
    }
    
    fun getMessageDetail(chatId: String, chatType: Int, messageId: String) {
        Log.d(TAG, "getMessageDetail: fetching detail for message $messageId")
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingMessageDetail = true)
            
            try {
                val userToken = tokenRepository.getToken().first()
                val token = userToken?.token
                Log.d(TAG, "getMessageDetail: got token, length=${token?.length ?: 0}")
                
                if (token.isNullOrBlank()) {
                    Log.w(TAG, "getMessageDetail: token is null or blank")
                    _state.value = _state.value.copy(
                        isLoadingMessageDetail = false,
                        error = "用户未登录"
                    )
                    return@launch
                }
                
                // 使用API获取完整的消息详情
                Log.d(TAG, "getMessageDetail: calling messageRepository.getMessageByIdFromApi($messageId)")
                val message = messageRepository.getMessageByIdFromApi(messageId, chatId, chatType)
                if (message != null) {
                    Log.d(TAG, "getMessageDetail: successfully got message detail, type=${message.contentType}")
                    _state.value = _state.value.copy(
                        isLoadingMessageDetail = false,
                        selectedMessage = message
                    )
                } else {
                    Log.w(TAG, "getMessageDetail: message not found via API")
                    _state.value = _state.value.copy(
                        isLoadingMessageDetail = false,
                        error = "无法获取消息详情"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "getMessageDetail: exception occurred", e)
                _state.value = _state.value.copy(
                    isLoadingMessageDetail = false,
                    error = "获取消息详情失败: ${e.message}"
                )
            }
        }
    }
    
    fun clearError() {
        Log.d(TAG, "clearError: clearing error state")
        _state.value = _state.value.copy(error = null)
    }
    
    /**
     * 将ChatSearchMessage转换为ChatMessage
     */
    private fun convertSearchMessageToChatMessage(searchMessage: ChatSearchMessage): ChatMessage {
        val sender = com.yhchat.canary.data.model.MessageSender(
            chatId = searchMessage.chatId,
            chatType = searchMessage.chatType,
            name = searchMessage.name,
            avatarUrl = searchMessage.avatarUrl ?: "",
            tagOld = emptyList(),
            tag = emptyList()
        )
        
        val content = com.yhchat.canary.data.model.MessageContent(
            text = searchMessage.content.takeIf { it.isNotBlank() }
        )
        
        return ChatMessage(
            msgId = searchMessage.id,
            sender = sender,
            direction = "left", // 搜索结果默认为左侧显示
            contentType = searchMessage.type.toIntOrNull() ?: 1,
            content = content,
            sendTime = searchMessage.time,
            cmd = null,
            msgDeleteTime = null,
            quoteMsgId = null,
            msgSeq = searchMessage.sequence,
            editTime = null,
            chatId = searchMessage.chatId,
            chatType = searchMessage.chatType,
            recvId = null
        )
    }
}
