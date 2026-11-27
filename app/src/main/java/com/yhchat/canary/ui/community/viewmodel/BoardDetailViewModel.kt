package com.yhchat.canary.ui.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhchat.canary.data.model.CommunityBoard
import com.yhchat.canary.data.model.CommunityPost
import com.yhchat.canary.data.repository.CommunityRepository
import com.yhchat.canary.data.repository.TokenRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 分区详情ViewModel
 */
@HiltViewModel
class BoardDetailViewModel @Inject constructor(
    private val communityRepository: CommunityRepository,
    private val tokenRepository: TokenRepository
) : ViewModel() {
    
    private val _boardDetailState = MutableStateFlow(BoardDetailState())
    val boardDetailState: StateFlow<BoardDetailState> = _boardDetailState.asStateFlow()
    
    private val _postListState = MutableStateFlow(PostListState())
    val postListState: StateFlow<PostListState> = _postListState.asStateFlow()
    
    private val _followState = MutableStateFlow(FollowState())
    val followState: StateFlow<FollowState> = _followState.asStateFlow()
    
    private val _blockState = MutableStateFlow(BlockState())
    val blockState: StateFlow<BlockState> = _blockState.asStateFlow()
    
    /**
     * 加载分区详情
     */
    fun loadBoardDetail(token: String, boardId: Int) {
        viewModelScope.launch {
            _boardDetailState.value = _boardDetailState.value.copy(isLoading = true, error = null)
            
            communityRepository.getBoardInfo(token, boardId)
                .onSuccess { response ->
                    _boardDetailState.value = BoardDetailState(
                        board = response.data.board,
                        isLoading = false,
                        error = null
                    )
                }
                .onFailure { error ->
                    _boardDetailState.value = _boardDetailState.value.copy(
                        isLoading = false,
                        error = error.message
                    )
                }
        }
    }
    
    /**
     * 加载文章列表
     */
    private var postListJob: kotlinx.coroutines.Job? = null

    fun selectPostType(token: String, boardId: Int, typ: Int) {
        if (typ == _postListState.value.typ && boardId == _postListState.value.boardId) return
        loadPostList(token, boardId, typ = typ)
    }

    fun loadPostList(token: String, boardId: Int, page: Int = 1, typ: Int = _postListState.value.typ) {
        require(typ == 1 || typ == 2)
        if (page > 1 && (_postListState.value.isLoading || _postListState.value.typ != typ)) return
        postListJob?.cancel()
        val previous = _postListState.value
        _postListState.value = if (previous.typ != typ || previous.boardId != boardId) {
            PostListState(boardId = boardId, typ = typ, isLoading = true)
        } else previous.copy(isLoading = true, error = null)
        postListJob = viewModelScope.launch {
            val result = communityRepository.getPostList(token, boardId, typ = typ, page = page)
            // Repository wraps exceptions in Result; ignore cancelled requests after a tab switch.
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            result.onSuccess { response ->
                val newPosts = (if (page == 1) response.data.posts
                    else _postListState.value.posts + response.data.posts).distinctBy { it.id }
                _postListState.value = PostListState(
                    posts = newPosts,
                    total = response.data.total,
                    currentPage = page,
                    hasMore = newPosts.size < response.data.total && response.data.posts.isNotEmpty(),
                    boardId = boardId,
                    typ = typ
                )
            }.onFailure { error ->
                _postListState.value = _postListState.value.copy(
                    isLoading = false, error = error.message ?: "加载文章失败"
                )
            }
        }
    }
    /**
     * 加载更多文章
     */
    fun loadMorePosts(token: String, boardId: Int) {
        val currentPage = _postListState.value.currentPage
        if (_postListState.value.hasMore && !_postListState.value.isLoading) {
            loadPostList(token, boardId, currentPage + 1)
        }
    }
    
    /**
     * 刷新数据
     */
    fun refresh(token: String, boardId: Int) {
        loadBoardDetail(token, boardId)
        loadPostList(token, boardId, 1)
    }
    
    /**
     * 关注/取消关注分区
     */
    fun followBoard(token: String, boardId: Int) {
        viewModelScope.launch {
            _followState.value = _followState.value.copy(isLoading = true, error = null)
            
            val board = _boardDetailState.value.board
            val isCurrentlyFollowed = board?.isFollowed == "1"
            
            val result = if (isCurrentlyFollowed) {
                // 当前已关注，执行取消关注
                communityRepository.unfollowBoard(token, boardId)
            } else {
                // 当前未关注，执行关注
                communityRepository.followBoard(token, boardId)
            }
            
            result.onSuccess {
                _followState.value = _followState.value.copy(
                    isLoading = false,
                    isSuccess = true
                )
                // 重新加载分区详情以获取最新的关注状态
                loadBoardDetail(token, boardId)
            }.onFailure { error ->
                _followState.value = _followState.value.copy(
                    isLoading = false,
                    error = error.message
                )
            }
        }
    }
    
    /**
     * 重置关注状态
     */
    fun resetFollowState() {
        _followState.value = FollowState()
    }
    
    /**
     * 清除错误信息
     */
    fun clearError() {
        _boardDetailState.value = _boardDetailState.value.copy(error = null)
        _postListState.value = _postListState.value.copy(error = null)
        _followState.value = _followState.value.copy(error = null)
        _blockState.value = _blockState.value.copy(error = null)
    }
    
    /**
     * 屏蔽用户
     */
    fun blockUser(token: String, authorId: String) {
        viewModelScope.launch {
            _blockState.value = _blockState.value.copy(isLoading = true, error = null)
            
            communityRepository.setBlackList(token, authorId, 1)
                .onSuccess {
                    _blockState.value = _blockState.value.copy(
                        isLoading = false,
                        isSuccess = true
                    )
                    // 从列表中移除被屏蔽用户的文章
                    val currentPosts = _postListState.value.posts
                    val filteredPosts = currentPosts.filter { it.senderId != authorId }
                    _postListState.value = _postListState.value.copy(posts = filteredPosts)
                }
                .onFailure { error ->
                    _blockState.value = _blockState.value.copy(
                        isLoading = false,
                        error = error.message
                    )
                }
        }
    }
    
    /**
     * 重置屏蔽状态
     */
    fun resetBlockState() {
        _blockState.value = BlockState()
    }
}

/**
 * 分区详情状态
 */
data class BoardDetailState(
    val board: CommunityBoard? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * 文章列表状态
 */
data class PostListState(
    val isLoading: Boolean = false,
    val posts: List<CommunityPost> = emptyList(),
    val total: Int = 0,
    val currentPage: Int = 1,
    val hasMore: Boolean = false,
    val boardId: Int? = null,
    val typ: Int = 2,
    val error: String? = null
)

/**
 * 关注状态
 */
data class FollowState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null
)

/**
 * 屏蔽状态
 */
data class BlockState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null
)
