package com.yhchat.canary.ui.community

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhchat.canary.data.model.CommunityBoard
import com.yhchat.canary.data.model.CommunityComment
import com.yhchat.canary.data.model.CommunityPost
import com.yhchat.canary.data.repository.CommunityRepository
import com.yhchat.canary.data.repository.FriendRepository
import com.yhchat.canary.data.repository.TokenRepository
import com.yhchat.canary.data.repository.UserRepository
import com.yhchat.canary.proto.user.address_book_list
import com.yhchat.canary.utils.ImageUploadUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 文章详情ViewModel
 */
@HiltViewModel
class PostDetailViewModel @Inject constructor(
    private val communityRepository: CommunityRepository,
    private val tokenRepository: TokenRepository,
    private val userRepository: UserRepository,
    private val friendRepository: com.yhchat.canary.data.repository.FriendRepository
) : ViewModel() {
    
    private val _postDetailState = MutableStateFlow(PostDetailState())
    val postDetailState: StateFlow<PostDetailState> = _postDetailState.asStateFlow()
    
    private val _commentListState = MutableStateFlow(CommentListState())
    val commentListState: StateFlow<CommentListState> = _commentListState.asStateFlow()

    private val _followingBoardListState = MutableStateFlow(BoardListState())
    val followingBoardListState: StateFlow<BoardListState> = _followingBoardListState.asStateFlow()

    private val _communityReportState = MutableStateFlow(CommunityReportState())
    val communityReportState: StateFlow<CommunityReportState> = _communityReportState.asStateFlow()

    /**
     * 评论是否正在发送。
     *
     * 单独一个 flow 而不是塞进 [CommentListState]：它是「输入面板上那一次点击」的状态，
     * 跟列表数据无关。塞进列表状态会让每次发送都触发整个列表重组。
     */
    private val _commentSending = MutableStateFlow(false)
    val commentSending: StateFlow<Boolean> = _commentSending.asStateFlow()
    
    /**
     * 使用TokenRepository获取token并加载文章详情
     */
    fun loadPostDetailWithToken(postId: Int, isRefresh: Boolean = false) {
        viewModelScope.launch {
            try {
                val token = tokenRepository.getTokenSync()
                if (token != null && postId > 0) {
                    loadPostDetail(token, postId, isRefresh)
                } else {
                    _postDetailState.value = _postDetailState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = if (token == null) "未登录" else "无效的文章ID"
                    )
                }
            } catch (e: Exception) {
                _postDetailState.value = _postDetailState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = "获取登录信息失败: ${e.message}"
                )
            }
        }
    }

    /**
     * 加载文章详情
     */
    fun loadPostDetail(token: String, postId: Int, isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isRefresh) {
                _postDetailState.value = _postDetailState.value.copy(isRefreshing = true, error = null)
            } else {
                _postDetailState.value = _postDetailState.value.copy(isLoading = true, error = null)
            }
            
            communityRepository.getPostDetail(token, postId)
                .onSuccess { response ->
                    // 获取用户信息以确认当前用户ID
                    userRepository.getUserProfile().fold(
                        onSuccess = { userProfile ->
                            _postDetailState.value = PostDetailState(
                                post = response.data.post,
                                board = response.data.board,
                                isAdmin = response.data.isAdmin,
                                currentUserId = userProfile.userId,
                                isLoading = false,
                                isRefreshing = false,
                                error = null
                            )
                        },
                        onFailure = {
                            // 如果获取用户信息失败，回退到本地存储的ID
                            _postDetailState.value = PostDetailState(
                                post = response.data.post,
                                board = response.data.board,
                                isAdmin = response.data.isAdmin,
                                currentUserId = tokenRepository.getUserIdSync(),
                                isLoading = false,
                                isRefreshing = false,
                                error = null
                            )
                        }
                    )
                    
                    // 同时加载评论列表(仅在初次加载或非刷新时)
                    if (!isRefresh || _commentListState.value.comments.isEmpty()) {
                        loadCommentListWithToken(postId)
                    }
                }
                .onFailure { error ->
                    _postDetailState.value = _postDetailState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = error.message
                    )
                }
        }
    }
    
    /**
     * 使用TokenRepository加载评论列表
     */
    fun loadCommentListWithToken(postId: Int, page: Int = 1) {
        viewModelScope.launch {
            try {
                val token = tokenRepository.getTokenSync()
                if (token != null) {
                    loadCommentList(token, postId, page)
                } else {
                    _commentListState.value = _commentListState.value.copy(error = "未登录")
                }
            } catch (e: Exception) {
                _commentListState.value = _commentListState.value.copy(error = "获取登录信息失败: ${e.message}")
            }
        }
    }

    /**
     * 加载评论列表
     */
    fun loadCommentList(token: String, postId: Int, page: Int = 1) {
        viewModelScope.launch {
            if (page == 1) {
                _commentListState.value = _commentListState.value.copy(isLoading = true, error = null)
            }
            
            communityRepository.getCommentList(token, postId, page = page)
                .onSuccess { response ->
                    val merged = if (page == 1) {
                        response.data.comments
                    } else {
                        _commentListState.value.comments + response.data.comments
                    }

                    // 后端按时间倒序返回（最新在前），这里统一排成正序：1L 在最上面。
                    //
                    // 必须「拼完再排」而不是「每页反转」：
                    // 分页时第 1 页是 [旧…新]，第 2 页又是 [旧…新]（更旧的一批），
                    // 逐页反转拼起来会变成 [旧…新][更旧…] 的错序。
                    // 在合并后的全集上按时间排，才与页码无关。
                    //
                    // 用 createTime 升序，同一秒的用 id 兜底 —— 楼中楼回复常常
                    // 落在同一秒，只按时间排会不稳定。
                    //
                    // 万一某条 createTime 是 0（后端漏给），它会被排到最前面，
                    // 反而比原来的倒序更乱。所以只要有一条时间无效就整批不排，
                    // 保持后端给的顺序 —— 那是已知可用的顺序。
                    // 这里不能写 reversed()：分页下「每批反转再拼」会错序。
                    val timeValid = merged.all { it.createTime > 0L }
                    val newComments = if (timeValid) {
                        merged.sortedWith(
                            compareBy<CommunityComment> { it.createTime }.thenBy { it.id }
                        )
                    } else {
                        merged
                    }

                    _commentListState.value = CommentListState(
                        comments = newComments,
                        isAdmin = response.data.isAdmin,
                        total = response.data.total,
                        currentPage = page,
                        hasMore = newComments.size < response.data.total,
                        isLoading = false,
                        error = null
                    )
                }
                .onFailure { error ->
                    _commentListState.value = _commentListState.value.copy(
                        isLoading = false,
                        error = error.message
                    )
                }
        }
    }
    
    /**
     * 使用TokenRepository点赞文章
     */
    fun likePostWithToken(postId: Int) {
        viewModelScope.launch {
            try {
                val token = tokenRepository.getTokenSync()
                if (token != null) {
                    likePost(token, postId)
                } else {
                    _postDetailState.value = _postDetailState.value.copy(error = "未登录")
                }
            } catch (e: Exception) {
                _postDetailState.value = _postDetailState.value.copy(error = "获取登录信息失败: ${e.message}")
            }
        }
    }

    /**
     * 使用TokenRepository收藏文章
     */
    fun collectPostWithToken(postId: Int) {
        viewModelScope.launch {
            try {
                val token = tokenRepository.getTokenSync()
                if (token != null) {
                    collectPost(token, postId)
                } else {
                    _postDetailState.value = _postDetailState.value.copy(error = "未登录")
                }
            } catch (e: Exception) {
                _postDetailState.value = _postDetailState.value.copy(error = "获取登录信息失败: ${e.message}")
            }
        }
    }

    /**
     * 使用TokenRepository打赏文章
     */
    fun rewardPostWithToken(postId: Int, amount: Double) {
        viewModelScope.launch {
            try {
                val token = tokenRepository.getTokenSync()
                if (token != null) {
                    rewardPost(token, postId, amount)
                } else {
                    _postDetailState.value = _postDetailState.value.copy(error = "未登录")
                }
            } catch (e: Exception) {
                _postDetailState.value = _postDetailState.value.copy(error = "获取登录信息失败: ${e.message}")
            }
        }
    }

    /**
     * 使用TokenRepository评论文章
     */
    fun commentPostWithToken(postId: Int, content: String, commentId: Int = 0) {
        viewModelScope.launch {
            // 整个「取 token → 发请求 → 重载列表」算一次发送，所以状态包在最外层。
            // 之前放在内层 commentPost 里，token 为 null 那条路不会把它置 true，
            // 面板上的发送键就会一直不转、看起来像没反应。
            _commentSending.value = true
            try {
                val token = tokenRepository.getTokenSync()
                if (token != null) {
                    communityRepository.commentPost(token, postId, content, commentId)
                        .onSuccess {
                            // 重新加载评论列表。这里直接等它跑完再收状态，
                            // 免得面板刚关、列表还在转圈
                            loadCommentList(token, postId, 1)
                        }
                        .onFailure { error ->
                            _commentListState.value =
                                _commentListState.value.copy(error = error.message)
                        }
                } else {
                    _commentListState.value = _commentListState.value.copy(error = "未登录")
                }
            } catch (e: Exception) {
                _commentListState.value =
                    _commentListState.value.copy(error = "获取登录信息失败: ${e.message}")
            } finally {
                // 放 finally：异常或取消也要把发送键放开
                _commentSending.value = false
            }
        }
    }

    /**
     * 使用TokenRepository加载更多评论
     */
    fun loadMoreCommentsWithToken(postId: Int) {
        val currentPage = _commentListState.value.currentPage
        if (_commentListState.value.hasMore && !_commentListState.value.isLoading) {
            loadCommentListWithToken(postId, currentPage + 1)
        }
    }

    /**
     * 使用TokenRepository点赞评论
     */
    fun likeCommentWithToken(postId: Int, commentId: Int) {
        viewModelScope.launch {
            try {
                val token = tokenRepository.getTokenSync()
                if (token != null) {
                    likeComment(token, postId, commentId)
                } else {
                    _commentListState.value = _commentListState.value.copy(error = "未登录")
                }
            } catch (e: Exception) {
                _commentListState.value = _commentListState.value.copy(error = "获取登录信息失败: ${e.message}")
            }
        }
    }

    /**
     * 使用TokenRepository删除评论
     */
    fun deleteCommentWithToken(postId: Int, commentId: Int) {
        viewModelScope.launch {
            try {
                val token = tokenRepository.getTokenSync()
                if (token != null) {
                    deleteComment(token, postId, commentId)
                } else {
                    _commentListState.value = _commentListState.value.copy(error = "未登录")
                }
            } catch (e: Exception) {
                _commentListState.value = _commentListState.value.copy(error = "获取登录信息失败: ${e.message}")
            }
        }
    }

    /**
     * 点赞文章
     */
    fun likePost(token: String, postId: Int) {
        viewModelScope.launch {
            communityRepository.likePost(token, postId)
                .onSuccess {
                    // 重新加载文章详情以获取最新状态
                    loadPostDetail(token, postId)
                }
                .onFailure { error ->
                    _postDetailState.value = _postDetailState.value.copy(error = error.message)
                }
        }
    }
    
    /**
     * 收藏文章
     */
    fun collectPost(token: String, postId: Int) {
        viewModelScope.launch {
            communityRepository.collectPost(token, postId)
                .onSuccess {
                    // 重新加载文章详情以获取最新状态
                    loadPostDetail(token, postId)
                }
                .onFailure { error ->
                    _postDetailState.value = _postDetailState.value.copy(error = error.message)
                }
        }
    }
    
    /**
     * 打赏文章
     */
    fun rewardPost(token: String, postId: Int, amount: Double) {
        viewModelScope.launch {
            communityRepository.rewardPost(token, postId, amount)
                .onSuccess {
                    // 重新加载文章详情以获取最新状态
                    loadPostDetail(token, postId)
                }
                .onFailure { error ->
                    _postDetailState.value = _postDetailState.value.copy(error = error.message)
                }
        }
    }
    
    /**
     * 评论文章
     */
    fun commentPost(token: String, postId: Int, content: String, commentId: Int = 0) {
        viewModelScope.launch {
            _commentSending.value = true
            try {
                communityRepository.commentPost(token, postId, content, commentId)
                    .onSuccess {
                        // 重新加载评论列表
                        loadCommentList(token, postId, 1)
                    }
                    .onFailure { error ->
                        _commentListState.value = _commentListState.value.copy(error = error.message)
                    }
            } finally {
                // 放在 finally：取消或异常也要把按钮放开，否则面板会一直卡在转圈
                _commentSending.value = false
            }
        }
    }
    
    /**
     * 加载更多评论
     */
    fun loadMoreComments(token: String, postId: Int) {
        val currentPage = _commentListState.value.currentPage
        if (_commentListState.value.hasMore && !_commentListState.value.isLoading) {
            loadCommentList(token, postId, currentPage + 1)
        }
    }
    
    /**
     * 点赞评论
     */
    fun likeComment(token: String, postId: Int, commentId: Int) {
        viewModelScope.launch {
            communityRepository.likeComment(token, commentId)
                .onSuccess {
                    // 重新加载评论列表以获取最新状态
                    loadCommentList(token, postId, 1)
                }
                .onFailure { error ->
                    _commentListState.value = _commentListState.value.copy(error = error.message)
                }
        }
    }

    /**
     * 删除评论
     */
    fun deleteComment(token: String, postId: Int, commentId: Int) {
        viewModelScope.launch {
            communityRepository.deleteComment(token, commentId)
                .onSuccess {
                    loadCommentList(token, postId, 1)
                }
                .onFailure { error ->
                    _commentListState.value = _commentListState.value.copy(
                        error = error.message ?: "删除评论失败"
                    )
                }
        }
    }
    
    /**
     * 使用TokenRepository刷新文章详情
     */
    fun refreshPostDetailWithToken(postId: Int) {
        loadPostDetailWithToken(postId, isRefresh = true)
    }

    /**
     * 删除文章
     */
    fun deletePost(token: String, postId: Int, onSuccess: () -> Unit) {
        viewModelScope.launch {
            communityRepository.deletePost(token, postId).fold(
                onSuccess = {
                    onSuccess()
                },
                onFailure = { error ->
                    _postDetailState.value = _postDetailState.value.copy(
                        error = error.message ?: "删除文章失败"
                    )
                }
            )
        }
    }

    /**
     * 刷新文章详情
     */
    fun refreshPostDetail(token: String, postId: Int) {
        loadPostDetail(token, postId, isRefresh = true)
    }
    
    /**
     * 清除错误信息
     */
    fun clearError() {
        _postDetailState.value = _postDetailState.value.copy(error = null)
        _commentListState.value = _commentListState.value.copy(error = null)
        _followingBoardListState.value = _followingBoardListState.value.copy(error = null)
    }

    fun loadFollowingBoardsWithToken() {
        viewModelScope.launch {
            _followingBoardListState.value = _followingBoardListState.value.copy(isLoading = true, error = null)
            try {
                val token = tokenRepository.getTokenSync()
                if (token.isNullOrBlank()) {
                    _followingBoardListState.value = _followingBoardListState.value.copy(
                        isLoading = false,
                        error = "未登录"
                    )
                    return@launch
                }

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
                            hasMore = false,
                            error = null
                        )
                    },
                    onFailure = { error ->
                        _followingBoardListState.value = _followingBoardListState.value.copy(
                            isLoading = false,
                            error = error.message ?: "加载关注分区失败"
                        )
                    }
                )
            } catch (e: Exception) {
                _followingBoardListState.value = _followingBoardListState.value.copy(
                    isLoading = false,
                    error = "获取登录信息失败: ${e.message}"
                )
            }
        }
    }

    fun movePostWithToken(
        postId: Int,
        targetBaId: Int,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val token = tokenRepository.getTokenSync()
                if (token.isNullOrBlank()) {
                    onError("未登录")
                    return@launch
                }

                communityRepository.movePost(token, postId, targetBaId).fold(
                    onSuccess = {
                        loadPostDetail(token, postId, isRefresh = true)
                        onSuccess()
                    },
                    onFailure = { error ->
                        val msg = error.message ?: "移动文章失败"
                        _postDetailState.value = _postDetailState.value.copy(error = msg)
                        onError(msg)
                    }
                )
            } catch (e: Exception) {
                onError("移动文章失败: ${e.message}")
            }
        }
    }

    fun togglePostStickyWithToken(
        postId: Int,
        sticky: Boolean,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val token = tokenRepository.getTokenSync()
                if (token.isNullOrBlank()) {
                    onError("未登录")
                    return@launch
                }

                communityRepository.setPostSticky(token, postId).fold(
                    onSuccess = {
                        _postDetailState.value = _postDetailState.value.copy(
                            post = _postDetailState.value.post?.copy(
                                isSticky = if (sticky) 1 else 0
                            )
                        )
                        loadPostDetail(token, postId, isRefresh = true)
                        onSuccess()
                    },
                    onFailure = { error ->
                        val msg = error.message ?: if (sticky) "置顶文章失败" else "取消置顶失败"
                        _postDetailState.value = _postDetailState.value.copy(error = msg)
                        onError(msg)
                    }
                )
            } catch (e: Exception) {
                onError((if (sticky) "置顶文章失败" else "取消置顶失败") + ": ${e.message}")
            }
        }
    }

    fun clearCommunityReportState() {
        _communityReportState.value = CommunityReportState()
    }

    fun submitCommunityReport(
        context: Context,
        typ: Int,
        id: Int,
        content: String,
        reason: String,
        imageUri: Uri?
    ) {
        viewModelScope.launch {
            _communityReportState.value = CommunityReportState(isSubmitting = true)
            try {
                val token = tokenRepository.getTokenSync()
                    ?: throw IllegalStateException("未登录")

                var imageUrl: String? = null
                if (imageUri != null) {
                    val uploadToken = communityRepository.getQiniuImageToken(token).getOrElse { throw it }
                    val uploadResult = ImageUploadUtil.uploadImage(
                        context = context,
                        imageUri = imageUri,
                        uploadToken = uploadToken
                    ).getOrElse { throw it }
                    imageUrl = "https://chat-img.jwznb.com/${uploadResult.key}"
                }

                communityRepository.submitCommunityReport(
                    token = token,
                    typ = typ,
                    id = id,
                    content = content,
                    reason = reason,
                    url = imageUrl
                ).fold(
                    onSuccess = {
                        _communityReportState.value = CommunityReportState(success = true)
                    },
                    onFailure = { error ->
                        _communityReportState.value = CommunityReportState(
                            error = error.message ?: "举报失败"
                        )
                    }
                )
            } catch (e: Exception) {
                _communityReportState.value = CommunityReportState(
                    error = e.message ?: "举报失败"
                )
            }
        }
    }
    
    /**
     * 异步获取当前token
     */
    suspend fun getTokenAsync(): String {
        return tokenRepository.getTokenSync() ?: ""
    }
    
    /**
     * 获取通讯录列表
     */
    suspend fun getAddressBookList(): Result<address_book_list> {
        return friendRepository.getAddressBookList()
    }
}

/**
 * 文章详情状态
 */
data class PostDetailState(
    val post: CommunityPost? = null,
    val board: CommunityBoard? = null,
    val isAdmin: Int = 0,
    val currentUserId: String? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null
)

/**
 * 评论列表状态
 */
data class CommentListState(
    val comments: List<CommunityComment> = emptyList(),
    val isAdmin: Int = 0,
    val total: Int = 0,
    val currentPage: Int = 1,
    val hasMore: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

data class CommunityReportState(
    val isSubmitting: Boolean = false,
    val success: Boolean = false,
    val error: String? = null
)
