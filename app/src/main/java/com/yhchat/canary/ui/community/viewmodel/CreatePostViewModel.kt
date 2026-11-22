package com.yhchat.canary.ui.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhchat.canary.data.repository.CommunityRepository
import com.yhchat.canary.data.repository.TokenRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 创建文章ViewModel
 */
@HiltViewModel
class CreatePostViewModel @Inject constructor(
    private val communityRepository: CommunityRepository,
    private val tokenRepository: TokenRepository
) : ViewModel() {
    
    private val _createPostState = MutableStateFlow(CreatePostState())
    val createPostState: StateFlow<CreatePostState> = _createPostState.asStateFlow()
    
    /**
     * 创建文章
     */
    fun createPost(
        token: String,
        boardId: Int,
        title: String,
        content: String,
        contentType: Int,
        groupId: String = ""
    ) {
        viewModelScope.launch {
            _createPostState.value = _createPostState.value.copy(
                isLoading = true,
                error = null,
                isSuccess = false
            )
            
            communityRepository.createPost(
                token = token,
                boardId = boardId,
                title = title,
                content = content,
                contentType = contentType,
                groupId = groupId
            )
                .onSuccess { response ->
                    _createPostState.value = CreatePostState(
                        isLoading = false,
                        isSuccess = true,
                        postId = response.data.postId,
                        error = null
                    )
                }
                .onFailure { error ->
                    _createPostState.value = _createPostState.value.copy(
                        isLoading = false,
                        error = error.message ?: "发布失败"
                    )
                }
        }
    }
    
    fun editPost(token: String, postId: Int, title: String, content: String, contentType: Int) {
        if (_createPostState.value.isLoading) return
        _createPostState.value = CreatePostState(isLoading = true)
        viewModelScope.launch {
            try {
                val response = communityRepository.editPost(token, postId, title, content, contentType)
                _createPostState.value = if (response.code == 1) {
                    CreatePostState(isSuccess = true, postId = postId)
                } else {
                    CreatePostState(error = response.msg ?: "编辑文章失败")
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _createPostState.value = CreatePostState(error = error.message ?: "编辑文章失败")
            }
        }
    }

    /**
     * 清除错误信息
     */
    fun clearError() {
        _createPostState.value = _createPostState.value.copy(error = null)
    }

    // ------------------------------------------------------------------
    // 正文插图
    // ------------------------------------------------------------------

    private val _insertImagesState = MutableStateFlow(InsertImagesState())
    val insertImagesState: StateFlow<InsertImagesState> = _insertImagesState.asStateFlow()

    /**
     * 上传选中的图片并返回它们的 Markdown 语法。
     *
     * 为什么是一次性传多个、一次性回调：多选场景下如果逐张回调，
     * 用户会看到正文里图片一个个冒出来、顺序还可能乱。
     * 全部传完再按选择顺序一次性插入，正文更整洁。
     *
     * @param onAllUploaded 全部成功后回调 Markdown 片段列表（顺序与入参一致）
     */
    fun uploadImages(
        context: android.content.Context,
        uriList: List<android.net.Uri>,
        onAllUploaded: (List<String>) -> Unit
    ) {
        if (uriList.isEmpty()) return

        viewModelScope.launch {
            _insertImagesState.value = InsertImagesState(
                isUploading = true,
                total = uriList.size,
                uploaded = 0
            )

            val snippets = mutableListOf<String>()
            val failed = mutableListOf<String>()

            for ((index, uri) in uriList.withIndex()) {
                val result = uploadSingleImage(context, uri)
                result.fold(
                    onSuccess = { key ->
                        // 和 Disk 那边的拼法保持一致：图床前缀 + key
                        snippets += "![图片](${IMAGE_BASE_URL}$key)"
                        _insertImagesState.value = _insertImagesState.value.copy(
                            uploaded = index + 1
                        )
                    },
                    onFailure = { e ->
                        failed += e.message ?: "未知错误"
                        _insertImagesState.value = _insertImagesState.value.copy(
                            uploaded = index + 1
                        )
                    }
                )
            }

            _insertImagesState.value = InsertImagesState(
                isUploading = false,
                total = uriList.size,
                uploaded = uriList.size,
                failedCount = failed.size
            )

            if (snippets.isNotEmpty()) {
                onAllUploaded(snippets)
            }
            if (failed.isNotEmpty()) {
                _createPostState.value = _createPostState.value.copy(
                    error = if (snippets.isEmpty()) {
                        "图片上传失败：${failed.first()}"
                    } else {
                        "${failed.size} 张图片上传失败，其余 ${snippets.size} 张已插入"
                    }
                )
            }
        }
    }

    /** 上传单张图片，返回七牛 key */
    private suspend fun uploadSingleImage(
        context: android.content.Context,
        uri: android.net.Uri
    ): Result<String> {
        // token 可能已过期，每张都重新取一次太浪费，用当前登录态取一次即可
        val token = tokenRepository.getTokenSync()
            ?: return Result.failure(Exception("未登录"))

        val uploadToken = communityRepository.getQiniuImageToken(token)
            .getOrElse { return Result.failure(Exception("获取上传凭证失败: ${it.message}")) }

        return com.yhchat.canary.utils.ImageUploadUtil.uploadImage(
            context = context,
            imageUri = uri,
            uploadToken = uploadToken
        ).map { it.key }
    }

    companion object {
        /** 图床访问前缀 */
        private const val IMAGE_BASE_URL = "https://chat-img.jwznb.com/"
    }
}

/**
 * 插图上传状态
 */
data class InsertImagesState(
    val isUploading: Boolean = false,
    val total: Int = 0,
    val uploaded: Int = 0,
    val failedCount: Int = 0
) {
    val fraction: Float
        get() = if (total <= 0) 0f else (uploaded.toFloat() / total).coerceIn(0f, 1f)
}

/**
 * 创建文章状态
 */
data class CreatePostState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val postId: Int? = null,
    val error: String? = null
)
