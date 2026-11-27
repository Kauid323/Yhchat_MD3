package com.yhchat.canary.ui.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhchat.canary.data.model.CommunityBoard
import com.yhchat.canary.data.model.CommunityPost
import com.yhchat.canary.data.repository.CommunityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 社区ViewModel
 */
@HiltViewModel
class CommunityViewModel @Inject constructor(
    private val communityRepository: CommunityRepository
) : ViewModel() {
    
    // 分区列表状态
    private val _boardListState = MutableStateFlow(BoardListState())
    val boardListState: StateFlow<BoardListState> = _boardListState.asStateFlow()
    
    // 关注分区列表状态
    private val _followingBoardListState = MutableStateFlow(BoardListState())
    val followingBoardListState: StateFlow<BoardListState> = _followingBoardListState.asStateFlow()
    
    // 全部分区列表状态
    private val _allBoardListState = MutableStateFlow(BoardListState())
    val allBoardListState: StateFlow<BoardListState> = _allBoardListState.asStateFlow()

    private val _createdBoardListState = MutableStateFlow(BoardListState())
    val createdBoardListState: StateFlow<BoardListState> = _createdBoardListState.asStateFlow()
    
    // 我的文章列表状态
    private val _myPostListState = MutableStateFlow(MyPostListState())
    val myPostListState: StateFlow<MyPostListState> = _myPostListState.asStateFlow()

    // 我的收藏文章列表状态
    private val _collectPostListState = MutableStateFlow(CollectPostListState())
    val collectPostListState: StateFlow<CollectPostListState> = _collectPostListState.asStateFlow()
    
    // 文章列表状态
    private val _postListState = MutableStateFlow(CommunityPostListState())
    val postListState: StateFlow<CommunityPostListState> = _postListState.asStateFlow()

    private val _recommendPostListState = MutableStateFlow(RecommendPostListState())
    val recommendPostListState: StateFlow<RecommendPostListState> = _recommendPostListState.asStateFlow()

    private val _latestPostListState = MutableStateFlow(CommunityPostListState())
    val latestPostListState: StateFlow<CommunityPostListState> = _latestPostListState.asStateFlow()
    
    private val _oldestPostListState = MutableStateFlow(CommunityPostListState())
    val oldestPostListState: StateFlow<CommunityPostListState> = _oldestPostListState.asStateFlow()
    private var oldestPostJob: kotlinx.coroutines.Job? = null

    fun loadOldestPostList(token: String, page: Int = 1, isRefresh: Boolean = false) {
        val current = _oldestPostListState.value
        if (page > 1 && (current.isLoading || current.isRefreshing || !current.hasMore)) return
        oldestPostJob?.cancel()
        _oldestPostListState.value = current.copy(
            isLoading = !isRefresh, isRefreshing = isRefresh, error = null
        )
        oldestPostJob = viewModelScope.launch {
            val result = communityRepository.getPostList(token = token, baId = 1, typ = 3, size = 20, page = page)
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            result.onSuccess { response ->
                val posts = (if (page == 1) response.data.posts else current.posts + response.data.posts).distinctBy { it.id }
                _oldestPostListState.value = current.copy(
                    posts = posts, currentPage = page, total = response.data.total,
                    hasMore = posts.size < response.data.total && response.data.posts.isNotEmpty(),
                    isLoading = false, isRefreshing = false, error = null
                )
            }.onFailure { error ->
                _oldestPostListState.value = _oldestPostListState.value.copy(
                    isLoading = false, isRefreshing = false, error = error.message ?: "加载最旧文章失败"
                )
            }
        }
    }

    fun loadMoreOldestPosts(token: String) {
        loadOldestPostList(token, page = _oldestPostListState.value.currentPage + 1)
    }

    // 文章详情状态
    private val _postDetailState = MutableStateFlow(PostDetailState())
    val postDetailState: StateFlow<PostDetailState> = _postDetailState.asStateFlow()
    
    // 评论列表状态
    private val _commentListState = MutableStateFlow(CommentListState())
    val commentListState: StateFlow<CommentListState> = _commentListState.asStateFlow()
    
    /**
     * 加载分区列表 - 加载所有分区
     */
    fun loadBoardList(token: String) {
        viewModelScope.launch {
            _boardListState.value = _boardListState.value.copy(isLoading = true, error = null)
            
            // 使用大的size参数来获取所有分区
            communityRepository.getBoardList(
                token = token,
                typ = 2,
                size = 1000, // 设置足够大的数量来获取所有分区
                page = 1
            ).fold(
                onSuccess = { response ->
                    _boardListState.value = _boardListState.value.copy(
                        isLoading = false,
                        boards = response.data.boards,
                        total = response.data.total,
                        currentPage = 1,
                        hasMore = false // 已加载全部，不需要更多
                    )
                },
                onFailure = { error ->
                    _boardListState.value = _boardListState.value.copy(
                        isLoading = false,
                        error = error.message ?: "加载分区列表失败"
                    )
                }
            )
        }
    }

    fun loadRecommendPostList(token: String, page: Int = 1, isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isRefresh) {
                _recommendPostListState.value = _recommendPostListState.value.copy(isRefreshing = true, error = null)
            } else {
                _recommendPostListState.value = _recommendPostListState.value.copy(isLoading = true, error = null)
            }

            communityRepository.getRecommendPostList(
                token = token,
                size = 20,
                page = page
            ).fold(
                onSuccess = { response ->
                    val newPosts = if (page == 1) {
                        response.data.posts
                    } else {
                        _recommendPostListState.value.posts + response.data.posts
                    }
                    _recommendPostListState.value = _recommendPostListState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        posts = newPosts,
                        total = response.data.total,
                        currentPage = page,
                        hasMore = newPosts.size < response.data.total,
                        error = null
                    )
                },
                onFailure = { error ->
                    _recommendPostListState.value = _recommendPostListState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = error.message ?: "加载推荐文章失败"
                    )
                }
            )
        }
    }

    fun loadMoreRecommendPosts(token: String) {
        val current = _recommendPostListState.value
        if (current.isLoading || !current.hasMore) return
        loadRecommendPostList(token, page = current.currentPage + 1, isRefresh = false)
    }

    fun loadLatestPostList(token: String, page: Int = 1, isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isRefresh) {
                _latestPostListState.value = _latestPostListState.value.copy(
                    isRefreshing = true,
                    isLoading = false,
                    error = null
                )
            } else {
                _latestPostListState.value = _latestPostListState.value.copy(
                    isLoading = true,
                    isRefreshing = false,
                    error = null
                )
            }

            communityRepository.getPostList(
                token = token,
                baId = 0,
                typ = 4,
                size = 20,
                page = page
            ).fold(
                onSuccess = { response ->
                    val newPosts = if (page == 1) {
                        response.data.posts
                    } else {
                        _latestPostListState.value.posts + response.data.posts
                    }
                    _latestPostListState.value = _latestPostListState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        posts = newPosts,
                        total = response.data.total,
                        currentPage = page,
                        hasMore = newPosts.size < response.data.total,
                        error = null
                    )
                },
                onFailure = { error ->
                    _latestPostListState.value = _latestPostListState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = error.message ?: "加载最新文章失败"
                    )
                }
            )
        }
    }

    fun loadMoreLatestPosts(token: String) {
        val current = _latestPostListState.value
        if (current.isLoading || !current.hasMore) return
        loadLatestPostList(token, page = current.currentPage + 1, isRefresh = false)
    }

    fun refreshLatestPostList(token: String) {
        loadLatestPostList(token, page = 1, isRefresh = true)
    }

    /**
     * 加载我的收藏文章列表
     */
    fun loadCollectPostList(token: String, page: Int = 1, isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isRefresh) {
                _collectPostListState.value = _collectPostListState.value.copy(isRefreshing = true, error = null)
            } else {
                _collectPostListState.value = _collectPostListState.value.copy(isLoading = true, error = null)
            }

            communityRepository.getCollectPostList(
                token = token,
                size = 20,
                page = page
            ).fold(
                onSuccess = { response ->
                    val newPosts = if (page == 1) {
                        response.data.posts
                    } else {
                        _collectPostListState.value.posts + response.data.posts
                    }

                    _collectPostListState.value = _collectPostListState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        posts = newPosts,
                        total = response.data.total,
                        currentPage = page,
                        hasMore = newPosts.size < response.data.total
                    )
                },
                onFailure = { error ->
                    _collectPostListState.value = _collectPostListState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = error.message ?: "加载我的收藏失败"
                    )
                }
            )
        }
    }

    fun loadMoreCollectPosts(token: String) {
        val current = _collectPostListState.value
        if (current.isLoading || !current.hasMore) return
        loadCollectPostList(token, page = current.currentPage + 1, isRefresh = false)
    }

    fun refreshCollectPostList(token: String) {
        loadCollectPostList(token, page = 1, isRefresh = true)
    }

    fun loadBaFollowerTotal(
        token: String,
        baId: Int,
        onSuccess: (Int) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            communityRepository.getBaFollowerList(
                token = token,
                baId = baId,
                size = 1,
                page = 1,
                memberName = ""
            ).fold(
                onSuccess = { resp ->
                    onSuccess(resp.data.total)
                },
                onFailure = { e ->
                    onError(e.message ?: "获取关注者数量失败")
                }
            )
        }
    }

    fun editBoard(
        token: String,
        baId: Int,
        name: String,
        avatar: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            communityRepository.editBoard(
                token = token,
                baId = baId,
                name = name,
                avatar = avatar
            ).fold(
                onSuccess = {
                    onSuccess()
                    refreshCreatedBoardList(token)
                },
                onFailure = { error ->
                    onError(error.message ?: "编辑分区失败")
                }
            )
        }
    }

    fun deleteBoard(
        token: String,
        baId: Int,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            communityRepository.deleteBoard(
                token = token,
                baId = baId
            ).fold(
                onSuccess = {
                    onSuccess()
                    refreshCreatedBoardList(token)
                },
                onFailure = { error ->
                    onError(error.message ?: "删除分区失败")
                }
            )
        }
    }

    fun loadCreatedBoardList(token: String) {
        viewModelScope.launch {
            _createdBoardListState.value = _createdBoardListState.value.copy(isLoading = true, error = null)

            communityRepository.getBoardList(
                token = token,
                typ = 3,
                size = 1000,
                page = 1
            ).fold(
                onSuccess = { response ->
                    _createdBoardListState.value = _createdBoardListState.value.copy(
                        isLoading = false,
                        boards = response.data.boards,
                        total = response.data.total,
                        currentPage = 1,
                        hasMore = false
                    )
                },
                onFailure = { error ->
                    _createdBoardListState.value = _createdBoardListState.value.copy(
                        isLoading = false,
                        error = error.message ?: "加载我创建的分区失败"
                    )
                }
            )
        }
    }

    fun refreshCreatedBoardList(token: String) {
        viewModelScope.launch {
            _createdBoardListState.value = _createdBoardListState.value.copy(isRefreshing = true, error = null)

            communityRepository.getBoardList(
                token = token,
                typ = 3,
                size = 1000,
                page = 1
            ).fold(
                onSuccess = { response ->
                    _createdBoardListState.value = _createdBoardListState.value.copy(
                        isRefreshing = false,
                        boards = response.data.boards,
                        total = response.data.total,
                        currentPage = 1,
                        hasMore = false
                    )
                },
                onFailure = { error ->
                    _createdBoardListState.value = _createdBoardListState.value.copy(
                        isRefreshing = false,
                        error = error.message ?: "刷新我创建的分区失败"
                    )
                }
            )
        }
    }
    
    /**
     * 加载关注的分区列表
     */
    fun loadFollowingBoardList(token: String) {
        viewModelScope.launch {
            _followingBoardListState.value = _followingBoardListState.value.copy(isLoading = true, error = null)
            
            communityRepository.getFollowingBoardList(
                token = token,
                typ = 1,
                size = 1000,
                page = 1
            ).fold(
                onSuccess = { response ->
                    _followingBoardListState.value = _followingBoardListState.value.copy(
                        isLoading = false,
                        boards = response.data.boards,
                        total = response.data.total,
                        currentPage = 1,
                        hasMore = false
                    )
                },
                onFailure = { error ->
                    _followingBoardListState.value = _followingBoardListState.value.copy(
                        isLoading = false,
                        error = error.message ?: "加载关注分区列表失败"
                    )
                }
            )
        }
    }
    
    /**
     * 加载全部分区列表
     */
    fun loadAllBoardList(token: String) {
        viewModelScope.launch {
            _allBoardListState.value = _allBoardListState.value.copy(isLoading = true, error = null)
            
            communityRepository.getFollowingBoardList(
                token = token,
                typ = 4, // 使用typ=4获取全部分区
                size = 1000,
                page = 1
            ).fold(
                onSuccess = { response ->
                    _allBoardListState.value = _allBoardListState.value.copy(
                        isLoading = false,
                        boards = response.data.boards,
                        total = response.data.total,
                        currentPage = 1,
                        hasMore = false
                    )
                },
                onFailure = { error ->
                    _allBoardListState.value = _allBoardListState.value.copy(
                        isLoading = false,
                        error = error.message ?: "加载全部分区列表失败"
                    )
                }
            )
        }
    }
    
    /**
     * 加载我的文章列表（一次性加载全部）
     */
    fun loadMyPostList(token: String, page: Int = 1, isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isRefresh) {
                _myPostListState.value = _myPostListState.value.copy(isRefreshing = true, error = null)
            } else {
                _myPostListState.value = _myPostListState.value.copy(isLoading = true, error = null)
            }
            
            communityRepository.getMyPostList(
                token = token,
                size = 9000,  // 一次性加载所有文章
                page = page
            ).fold(
                onSuccess = { response ->
                    _myPostListState.value = _myPostListState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        posts = response.data.posts,
                        total = response.data.total,
                        currentPage = page
                    )
                },
                onFailure = { error ->
                    _myPostListState.value = _myPostListState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = error.message ?: "加载我的文章列表失败"
                    )
                }
            )
        }
    }
    
    
    /**
     * 加载文章列表
     */
    fun loadPostList(token: String, baId: Int, page: Int = 1) {
        viewModelScope.launch {
            _postListState.value = _postListState.value.copy(
                isLoading = true, 
                error = null,
                boardId = baId
            )
            
            communityRepository.getPostList(
                token = token,
                baId = baId,
                typ = 1,
                size = 20,
                page = page
            ).fold(
                onSuccess = { response ->
                    _postListState.value = _postListState.value.copy(
                        isLoading = false,
                        posts = response.data.posts,
                        total = response.data.total,
                        currentPage = page,
                        hasMore = page * 20 < response.data.total
                    )
                },
                onFailure = { error ->
                    _postListState.value = _postListState.value.copy(
                        isLoading = false,
                        error = error.message ?: "加载文章列表失败"
                    )
                }
            )
        }
    }
    
    /**
     * 加载更多文章
     */
    fun loadMorePosts(token: String) {
        val currentState = _postListState.value
        if (currentState.isLoading || !currentState.hasMore || currentState.boardId == null) return
        
        loadPostList(token, currentState.boardId, currentState.currentPage + 1)
    }
    
    /**
     * 加载文章详情
     */
    fun loadPostDetail(token: String, postId: Int) {
        viewModelScope.launch {
            _postDetailState.value = _postDetailState.value.copy(isLoading = true, error = null)
            
            communityRepository.getPostDetail(token, postId).fold(
                onSuccess = { response ->
                    _postDetailState.value = _postDetailState.value.copy(
                        isLoading = false,
                        post = response.data.post,
                        board = response.data.board,
                        isAdmin = response.data.isAdmin
                    )
                },
                onFailure = { error ->
                    _postDetailState.value = _postDetailState.value.copy(
                        isLoading = false,
                        error = error.message ?: "加载文章详情失败"
                    )
                }
            )
        }
    }
    
    /**
     * 加载评论列表
     */
    fun loadCommentList(token: String, postId: Int, page: Int = 1) {
        viewModelScope.launch {
            _commentListState.value = _commentListState.value.copy(
                isLoading = true, 
                error = null
            )
            
            communityRepository.getCommentList(
                token = token,
                postId = postId,
                size = 10,
                page = page
            ).fold(
                onSuccess = { response ->
                    _commentListState.value = _commentListState.value.copy(
                        isLoading = false,
                        comments = response.data.comments,
                        total = response.data.total,
                        isAdmin = response.data.isAdmin,
                        currentPage = page,
                        hasMore = page * 10 < response.data.total
                    )
                },
                onFailure = { error ->
                    _commentListState.value = _commentListState.value.copy(
                        isLoading = false,
                        error = error.message ?: "加载评论列表失败"
                    )
                }
            )
        }
    }
    
    /**
     * 点赞文章
     */
    fun likePost(token: String, postId: Int) {
        viewModelScope.launch {
            communityRepository.likePost(token, postId).fold(
                onSuccess = {
                    // 更新本地状态
                    updatePostLikeStatus(postId, true)
                },
                onFailure = { error ->
                    _postDetailState.value = _postDetailState.value.copy(
                        error = error.message ?: "点赞失败"
                    )
                }
            )
        }
    }
    
    /**
     * 收藏文章
     */
    fun collectPost(token: String, postId: Int) {
        viewModelScope.launch {
            communityRepository.collectPost(token, postId).fold(
                onSuccess = {
                    // 更新本地状态
                    updatePostCollectStatus(postId, true)
                },
                onFailure = { error ->
                    _postDetailState.value = _postDetailState.value.copy(
                        error = error.message ?: "收藏失败"
                    )
                }
            )
        }
    }
    
    /**
     * 评论文章
     */
    fun commentPost(token: String, postId: Int, content: String, commentId: Int = 0) {
        viewModelScope.launch {
            communityRepository.commentPost(token, postId, content, commentId).fold(
                onSuccess = {
                    // 重新加载评论列表
                    loadCommentList(token, postId)
                },
                onFailure = { error ->
                    _commentListState.value = _commentListState.value.copy(
                        error = error.message ?: "评论失败"
                    )
                }
            )
        }
    }
    
    /**
     * 更新文章点赞状态
     */
    private fun updatePostLikeStatus(postId: Int, isLiked: Boolean) {
        val currentState = _postDetailState.value
        currentState.post?.let { post ->
            val updatedPost = post.copy(
                isLiked = if (isLiked) "1" else "0",
                likeNum = if (isLiked) post.likeNum + 1 else post.likeNum - 1
            )
            _postDetailState.value = currentState.copy(post = updatedPost)
        }
    }
    
    /**
     * 更新文章收藏状态
     */
    private fun updatePostCollectStatus(postId: Int, isCollected: Boolean) {
        val currentState = _postDetailState.value
        currentState.post?.let { post ->
            val updatedPost = post.copy(
                isCollected = if (isCollected) 1 else 0,
                collectNum = if (isCollected) post.collectNum + 1 else post.collectNum - 1
            )
            _postDetailState.value = currentState.copy(post = updatedPost)
        }
    }
    
    /**
     * 删除文章
     */
    fun deletePost(token: String, postId: Int) {
        viewModelScope.launch {
            communityRepository.deletePost(token, postId).fold(
                onSuccess = {
                    // 删除成功，从列表中移除该文章
                    val currentPosts = _myPostListState.value.posts
                    val updatedPosts = currentPosts.filter { it.id != postId }
                    _myPostListState.value = _myPostListState.value.copy(
                        posts = updatedPosts,
                        total = _myPostListState.value.total - 1
                    )
                },
                onFailure = { error ->
                    _myPostListState.value = _myPostListState.value.copy(
                        error = error.message ?: "删除文章失败"
                    )
                }
            )
        }
    }
    
    /**
     * 刷新我的文章列表
     */
    fun refreshMyPostList(token: String) {
        loadMyPostList(token, page = 1, isRefresh = true)
    }
    
    /**
     * 刷新分区列表
     */
    fun refreshBoardList(token: String) {
        viewModelScope.launch {
            _boardListState.value = _boardListState.value.copy(isRefreshing = true, error = null)
            
            communityRepository.getBoardList(
                token = token,
                typ = 2,
                size = 1000,
                page = 1
            ).fold(
                onSuccess = { response ->
                    _boardListState.value = _boardListState.value.copy(
                        isRefreshing = false,
                        boards = response.data.boards,
                        total = response.data.total,
                        currentPage = 1,
                        hasMore = false
                    )
                },
                onFailure = { error ->
                    _boardListState.value = _boardListState.value.copy(
                        isRefreshing = false,
                        error = error.message ?: "刷新分区列表失败"
                    )
                }
            )
        }
    }
    
    /**
     * 刷新关注分区列表
     */
    fun refreshFollowingBoardList(token: String) {
        viewModelScope.launch {
            _followingBoardListState.value = _followingBoardListState.value.copy(isRefreshing = true, error = null)
            
            communityRepository.getFollowingBoardList(
                token = token,
                typ = 1,
                size = 1000,
                page = 1
            ).fold(
                onSuccess = { response ->
                    _followingBoardListState.value = _followingBoardListState.value.copy(
                        isRefreshing = false,
                        boards = response.data.boards,
                        total = response.data.total,
                        currentPage = 1,
                        hasMore = false
                    )
                },
                onFailure = { error ->
                    _followingBoardListState.value = _followingBoardListState.value.copy(
                        isRefreshing = false,
                        error = error.message ?: "刷新关注分区列表失败"
                    )
                }
            )
        }
    }
    
    /**
     * 刷新全部分区列表
     */
    fun refreshAllBoardList(token: String) {
        viewModelScope.launch {
            _allBoardListState.value = _allBoardListState.value.copy(isRefreshing = true, error = null)
            
            communityRepository.getFollowingBoardList(
                token = token,
                typ = 4, // 使用typ=4获取全部分区
                size = 1000,
                page = 1
            ).fold(
                onSuccess = { response ->
                    _allBoardListState.value = _allBoardListState.value.copy(
                        isRefreshing = false,
                        boards = response.data.boards,
                        total = response.data.total,
                        currentPage = 1,
                        hasMore = false
                    )
                },
                onFailure = { error ->
                    _allBoardListState.value = _allBoardListState.value.copy(
                        isRefreshing = false,
                        error = error.message ?: "刷新全部分区列表失败"
                    )
                }
            )
        }
    }
    
    /**
     * 关注/取消关注分区
     */
    fun followBoard(token: String, boardId: Int) {
        viewModelScope.launch {
            communityRepository.followBoard(token, boardId).fold(
                onSuccess = {
                    // 重新加载关注分区列表
                    loadFollowingBoardList(token)
                },
                onFailure = { error ->
                    _followingBoardListState.value = _followingBoardListState.value.copy(
                        error = error.message ?: "关注操作失败"
                    )
                }
            )
        }
    }
    
    /**
     * 清除错误
     */
    /**
     * 创建分区
     */
    fun createBoard(token: String, name: String, avatar: String, onSuccess: (Int) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                communityRepository.createBoard(token, name, avatar).fold(
                    onSuccess = { response ->
                        if (response.code == 1) {
                            onSuccess(response.data.id)
                            // 刷新分区列表
                            loadBoardList(token)
                            loadAllBoardList(token)
                            loadCreatedBoardList(token)
                        } else {
                            onError(response.msg)
                        }
                    },
                    onFailure = { error ->
                        onError(error.message ?: "创建分区失败")
                    }
                )
            } catch (e: Exception) {
                onError("创建分区失败: ${e.message}")
            }
        }
    }

    fun manageBoard(
        token: String,
        baId: Int,
        visibleRange: Int,
        publishAuthority: Int,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            communityRepository.manageBoard(
                token = token,
                baId = baId,
                visibleRange = visibleRange,
                publishAuthority = publishAuthority
            ).fold(
                onSuccess = {
                    onSuccess()
                    refreshCreatedBoardList(token)
                },
                onFailure = { error ->
                    onError(error.message ?: "管理分区失败")
                }
            )
        }
    }
    
    fun clearError() {
        _boardListState.value = _boardListState.value.copy(error = null)
        _followingBoardListState.value = _followingBoardListState.value.copy(error = null)
        _allBoardListState.value = _allBoardListState.value.copy(error = null)
        _createdBoardListState.value = _createdBoardListState.value.copy(error = null)
        _myPostListState.value = _myPostListState.value.copy(error = null)
        _collectPostListState.value = _collectPostListState.value.copy(error = null)
        _postListState.value = _postListState.value.copy(error = null)
        _recommendPostListState.value = _recommendPostListState.value.copy(error = null)
        _latestPostListState.value = _latestPostListState.value.copy(error = null)
        _postDetailState.value = _postDetailState.value.copy(error = null)
        _commentListState.value = _commentListState.value.copy(error = null)
    }
}

data class RecommendPostListState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val posts: List<CommunityPost> = emptyList(),
    val total: Int = 0,
    val currentPage: Int = 1,
    val hasMore: Boolean = false,
    val error: String? = null
)

data class CommunityPostListState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val posts: List<CommunityPost> = emptyList(),
    val total: Int = 0,
    val currentPage: Int = 1,
    val hasMore: Boolean = false,
    val boardId: Int? = null,
    val error: String? = null
)

/**
 * 分区列表状态
 */
data class BoardListState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val boards: List<CommunityBoard> = emptyList(),
    val total: Int = 0,
    val currentPage: Int = 1,
    val hasMore: Boolean = false,
    val error: String? = null
)

/**
 * 社区文章列表状态
 */
/**
 * 我的文章列表状态
 */
data class MyPostListState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val posts: List<CommunityPost> = emptyList(),
    val total: Int = 0,
    val currentPage: Int = 1,
    val error: String? = null
)

data class CollectPostListState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val posts: List<CommunityPost> = emptyList(),
    val total: Int = 0,
    val currentPage: Int = 1,
    val hasMore: Boolean = false,
    val error: String? = null
)
