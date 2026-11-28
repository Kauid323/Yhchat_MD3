package com.yhchat.canary.data.repository

import com.yhchat.canary.data.api.ApiService
import com.yhchat.canary.data.api.BaFollowerListRequest
import com.yhchat.canary.data.api.BaManageSettingRequest
import com.yhchat.canary.data.api.BoardInfoRequest
import com.yhchat.canary.data.api.BoardListRequest
import com.yhchat.canary.data.api.BoardsByCreateRequest
import com.yhchat.canary.data.api.CollectPostListRequest
import com.yhchat.canary.data.api.CollectPostRequest
import com.yhchat.canary.data.api.CommentListRequest
import com.yhchat.canary.data.api.CommentReplyListRequest
import com.yhchat.canary.data.api.CommentPostRequest
import com.yhchat.canary.data.api.CreateBoardRequest
import com.yhchat.canary.data.api.CreateBoardResponse
import com.yhchat.canary.data.api.CreatePostRequest
import com.yhchat.canary.data.api.DeleteBoardRequest
import com.yhchat.canary.data.api.DeleteCommentRequest
import com.yhchat.canary.data.api.DeletePostRequest
import com.yhchat.canary.data.api.EditBoardRequest
import com.yhchat.canary.data.api.EditPostRequest
import com.yhchat.canary.data.api.FollowBoardRequest
import com.yhchat.canary.data.api.GroupListRequest
import com.yhchat.canary.data.api.GroupListResponse
import com.yhchat.canary.data.api.LikeCommentRequest
import com.yhchat.canary.data.api.LikePostRequest
import com.yhchat.canary.data.api.ManageBoardRequest
import com.yhchat.canary.data.api.MovePostRequest
import com.yhchat.canary.data.api.MyPostListRequest
import com.yhchat.canary.data.api.PostDetailRequest
import com.yhchat.canary.data.api.PostListRequest
import com.yhchat.canary.data.api.PostStickyRequest
import com.yhchat.canary.data.api.RecommendPostListRequest
import com.yhchat.canary.data.api.ReportPostRequest
import com.yhchat.canary.data.api.RewardPostRequest
import com.yhchat.canary.data.api.UnfollowBoardRequest
import com.yhchat.canary.data.model.ApiStatus
import com.yhchat.canary.data.model.BaFollowerListResponse
import com.yhchat.canary.data.model.BaseResponse
import com.yhchat.canary.data.model.BlockedUserListResponse
import com.yhchat.canary.data.model.BoardInfoResponse
import com.yhchat.canary.data.model.BoardListResponse
import com.yhchat.canary.data.model.BoardsByCreateResponse
import com.yhchat.canary.data.model.CommentListResponse
import com.yhchat.canary.data.model.CommunityPost
import com.yhchat.canary.data.model.CreatePostResponse
import com.yhchat.canary.data.model.DeleteDraftRequest
import com.yhchat.canary.data.model.Draft
import com.yhchat.canary.data.model.EditPostData
import com.yhchat.canary.data.model.EditPostResponse
import com.yhchat.canary.data.model.FollowingBoardListResponse
import com.yhchat.canary.data.model.MyPostListResponse
import com.yhchat.canary.data.model.PostDetailResponse
import com.yhchat.canary.data.model.PostListResponse
import com.yhchat.canary.data.model.SaveDraftRequest
import com.yhchat.canary.data.model.UpdateDraftRequest
import javax.inject.Inject

/**
 * 社区数据仓库
 */
class CommunityRepository @Inject constructor(
    private val apiService: ApiService
) {

    suspend fun getQiniuImageToken(token: String): Result<String> {
        return try {
            val response = apiService.getQiniuImageToken(token)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Result.success(body.data.token)
                } else {
                    Result.failure(Exception(body?.msg ?: "获取图片上传凭证失败"))
                }
            } else {
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun submitCommunityReport(
        token: String,
        typ: Int,
        id: Int,
        content: String,
        reason: String,
        url: String? = null
    ): Result<ApiStatus> {
        return try {
            val response = apiService.reportPost(
                token = token,
                request = ReportPostRequest(
                    typ = typ,
                    id = id,
                    content = content,
                    reason = reason,
                    url = url
                )
            )
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Result.success(body)
                } else {
                    Result.failure(Exception(body?.message ?: "举报失败"))
                }
            } else {
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 获取分区列表
     */
    suspend fun getBoardList(
        token: String,
        typ: Int = 2,
        size: Int = 20,
        page: Int = 1
    ): Result<BoardListResponse> {
        return try {
            val request = BoardListRequest(typ = typ, size = size, page = page)
            val response = apiService.getBoardList(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Result.success(body)
                } else {
                    Result.failure(Exception("API返回错误: ${body?.msg ?: "未知错误"}"))
                }
            } else {
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRecommendPostList(
        token: String,
        size: Int = 20,
        page: Int = 1
    ): Result<com.yhchat.canary.data.model.PostListResponse> {
        return try {
            val request = RecommendPostListRequest(size = size, page = page)
            val response = apiService.getRecommendPostList(token, request)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Result.success(body)
                } else {
                    Result.failure(Exception("API返回错误: ${body?.msg ?: "未知错误"}"))
                }
            } else {
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listBoardsByCreate(
        token: String,
        userId: String
    ): Result<BoardsByCreateResponse> {
        return try {
            val request = BoardsByCreateRequest(userId = userId)
            val response = apiService.listBoardsByCreate(token, request)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Result.success(body)
                } else {
                    Result.failure(Exception("API返回错误: ${body?.msg ?: "未知错误"}"))
                }
            } else {
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getBaFollowerList(
        token: String,
        baId: Int,
        size: Int = 20,
        page: Int = 1,
        memberName: String = ""
    ): Result<BaFollowerListResponse> {
        return try {
            val request = BaFollowerListRequest(id = baId, size = size, page = page, memberName = memberName)
            val response = apiService.getBaFollowerList(token, request)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Result.success(body)
                } else {
                    Result.failure(Exception("API返回错误: ${body?.msg ?: "未知错误"}"))
                }
            } else {
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setBaManageSetting(
        token: String,
        baId: Int,
        userId: String,
        userLevel: Int
    ): Result<ApiStatus> {
        return try {
            val request = BaManageSettingRequest(baId = baId, userId = userId, userLevel = userLevel)
            val response = apiService.setBaManageSetting(token, request)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Result.success(body)
                } else {
                    Result.failure(Exception("API返回错误: ${body?.message ?: "未知错误"}"))
                }
            } else {
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun manageBoard(
        token: String,
        baId: Int,
        visibleRange: Int,
        publishAuthority: Int
    ): Result<ApiStatus> {
        return try {
            val request = ManageBoardRequest(
                baId = baId,
                visibleRange = visibleRange,
                publishAuthority = publishAuthority
            )
            val response = apiService.manageBoard(token, request)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Result.success(body)
                } else {
                    Result.failure(Exception("API返回错误: ${body?.message ?: "未知错误"}"))
                }
            } else {
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun editBoard(
        token: String,
        baId: Int,
        name: String,
        avatar: String
    ): Result<ApiStatus> {
        return try {
            val request = EditBoardRequest(
                baId = baId,
                name = name,
                avatar = avatar
            )
            val response = apiService.editBoard(token, request)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Result.success(body)
                } else {
                    Result.failure(Exception("API返回错误: ${body?.message ?: "未知错误"}"))
                }
            } else {
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteBoard(
        token: String,
        baId: Int
    ): Result<ApiStatus> {
        return try {
            val request = DeleteBoardRequest(
                baId = baId
            )
            val response = apiService.deleteBoard(token, request)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Result.success(body)
                } else {
                    Result.failure(Exception("API返回错误: ${body?.message ?: "未知错误"}"))
                }
            } else {
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 获取关注的分区列表
     */
    suspend fun getFollowingBoardList(
        token: String,
        typ: Int = 1,
        size: Int = 20,
        page: Int = 1
    ): Result<FollowingBoardListResponse> {
        return try {
            val request = BoardListRequest(typ = typ, size = size, page = page)
            val response = apiService.getFollowingBoardList(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Result.success(body)
                } else {
                    Result.failure(Exception("API返回错误: ${body?.msg ?: "未知错误"}"))
                }
            } else {
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 获取我的文章列表
     */
    suspend fun getMyPostList(
        token: String,
        size: Int = 20,
        page: Int = 1
    ): Result<MyPostListResponse> {
        return try {
            val request = MyPostListRequest(size = size, page = page)
            val response = apiService.getMyPostList(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Result.success(body)
                } else {
                    Result.failure(Exception("API返回错误: ${body?.msg ?: "未知错误"}"))
                }
            } else {
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 获取分区信息
     */
    suspend fun getBoardInfo(
        token: String,
        boardId: Int
    ): Result<BoardInfoResponse> {
        return try {
            val request = BoardInfoRequest(id = boardId)
            val response = apiService.getBoardInfo(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Result.success(body)
                } else {
                    Result.failure(Exception("API返回错误: ${body?.msg ?: "未知错误"}"))
                }
            } else {
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 获取文章列表
     */
    suspend fun getPostList(
        token: String,
        baId: Int,
        typ: Int = 1,
        size: Int = 20,
        page: Int = 1
    ): Result<PostListResponse> {
        return try {
            val request = PostListRequest(typ = typ, baId = baId, size = size, page = page)
            val response = apiService.getPostList(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Result.success(body)
                } else {
                    Result.failure(Exception("API返回错误: ${body?.msg ?: "未知错误"}"))
                }
            } else {
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 获取我的收藏文章列表
     */
    suspend fun getCollectPostList(
        token: String,
        size: Int = 20,
        page: Int = 1
    ): Result<PostListResponse> {
        return try {
            val request = CollectPostListRequest(size = size, page = page)
            val response = apiService.getCollectPostList(token, request)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Result.success(body)
                } else {
                    Result.failure(Exception("API返回错误: ${body?.msg ?: "未知错误"}"))
                }
            } else {
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 获取文章详情
     */
    suspend fun getPostDetail(
        token: String,
        postId: Int
    ): Result<PostDetailResponse> {
        return try {
            val request = PostDetailRequest(id = postId)
            val response = apiService.getPostDetail(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Result.success(body)
                } else {
                    Result.failure(Exception("API返回错误: ${body?.msg ?: "未知错误"}"))
                }
            } else {
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 获取评论列表
     */
    suspend fun getCommentList(
        token: String,
        postId: Int,
        size: Int = 10,
        page: Int = 1
    ): Result<CommentListResponse> {
        return try {
            val request = CommentListRequest(postId = postId, size = size, page = page)
            val response = apiService.getCommentList(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Result.success(body)
                } else {
                    Result.failure(Exception("API返回错误: ${body?.msg ?: "未知错误"}"))
                }
            } else {
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 获取评论回复列表
     */
    suspend fun getCommentReplyList(
        token: String,
        commentId: Int,
        size: Int = 10,
        page: Int = 1
    ): Result<CommentListResponse> {
        return try {
            val request = CommentReplyListRequest(commentId = commentId, size = size, page = page)
            val response = apiService.getCommentReplyList(token, request)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Result.success(body)
                } else {
                    Result.failure(Exception("API返回错误: ${body?.msg ?: "未知错误"}"))
                }
            } else {
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 点赞文章
     */
    suspend fun likePost(
        token: String,
        postId: Int
    ): Result<ApiStatus> {
        return try {
            val request = LikePostRequest(id = postId)
            val response = apiService.likePost(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Result.success(body)
                } else {
                    Result.failure(Exception("API返回错误: ${body?.message ?: "未知错误"}"))
                }
            } else {
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 点赞评论
     */
    suspend fun likeComment(
        token: String,
        commentId: Int
    ): Result<ApiStatus> {
        return try {
            val request = LikeCommentRequest(id = commentId)
            val response = apiService.likeComment(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Result.success(body)
                } else {
                    Result.failure(Exception("API返回错误: ${body?.message ?: "未知错误"}"))
                }
            } else {
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 收藏文章
     */
    suspend fun collectPost(
        token: String,
        postId: Int
    ): Result<ApiStatus> {
        return try {
            val request = CollectPostRequest(id = postId)
            val response = apiService.collectPost(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Result.success(body)
                } else {
                    Result.failure(Exception("API返回错误: ${body?.message ?: "未知错误"}"))
                }
            } else {
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 打赏文章
     */
    suspend fun rewardPost(
        token: String,
        postId: Int,
        amount: Double
    ): Result<ApiStatus> {
        return try {
            val request = RewardPostRequest(postId = postId, amount = amount)
            val response = apiService.rewardPost(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Result.success(body)
                } else {
                    Result.failure(Exception("API返回错误: ${body?.message ?: "未知错误"}"))
                }
            } else {
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 评论文章
     */
    suspend fun commentPost(
        token: String,
        postId: Int,
        content: String,
        commentId: Int = 0
    ): Result<ApiStatus> {
        return try {
            val request = CommentPostRequest(postId = postId, commentId = commentId, content = content)
            val response = apiService.commentPost(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Result.success(body)
                } else {
                    Result.failure(Exception("API返回错误: ${body?.message ?: "未知错误"}"))
                }
            } else {
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 删除评论
     */
    suspend fun deleteComment(
        token: String,
        commentId: Int
    ): Result<ApiStatus> {
        return try {
            val request = DeleteCommentRequest(commentId = commentId)
            val response = apiService.deleteComment(token, request)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Result.success(body)
                } else {
                    Result.failure(Exception("API返回错误: ${body?.message ?: "未知错误"}"))
                }
            } else {
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 创建文章
     */
    suspend fun createPost(
        token: String,
        boardId: Int,
        title: String,
        content: String,
        contentType: Int,
        groupId: String = ""
    ): Result<CreatePostResponse> {
        return try {
            val request = CreatePostRequest(
                baId = boardId,
                groupId = groupId,
                title = title,
                content = content,
                contentType = contentType
            )
            val response = apiService.createPost(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Result.success(body)
                } else {
                    Result.failure(Exception("API返回错误: ${body?.msg ?: "未知错误"}"))
                }
            } else {
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 编辑文章
     */
    suspend fun editPost(
        token: String,
        postId: Int,
        title: String,
        content: String,
        contentType: Int
    ): EditPostResponse {
        val request = EditPostRequest(
            postId = postId,
            title = title,
            content = content,
            contentType = contentType
        )
        return apiService.editPost(token, request).body() ?: EditPostResponse(
            code = 0,
            data = EditPostData(postId = 0),
            msg = "网络错误"
        )
    }
    
    /**
     * 删除文章
     */
    suspend fun deletePost(
        token: String,
        postId: Int
    ): Result<ApiStatus> {
        return try {
            val request = DeletePostRequest(postId = postId)
            val response = apiService.deletePost(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Result.success(body)
                } else {
                    Result.failure(Exception("API返回错误: ${body?.message ?: "未知错误"}"))
                }
            } else {
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun movePost(
        token: String,
        postId: Int,
        targetBaId: Int
    ): Result<ApiStatus> {
        return try {
            val request = MovePostRequest(baId = targetBaId, id = postId)
            val response = apiService.movePost(token, request)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Result.success(body)
                } else {
                    Result.failure(Exception("API返回错误: ${body?.message ?: "未知错误"}"))
                }
            } else {
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setPostSticky(
        token: String,
        postId: Int
    ): Result<ApiStatus> {
        return try {
            val request = PostStickyRequest(postId = postId)
            val response = apiService.editPostSticky(token, request)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Result.success(body)
                } else {
                    Result.failure(Exception("API返回错误: ${body?.message ?: "未知错误"}"))
                }
            } else {
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 获取草稿列表
     * 草稿数据混在 my-post-list 里，按 isDraft=1 过滤
     */
    suspend fun getDraftList(token: String): Result<List<Draft>> {
        return try {
            val pageSize = 50
            var page = 1
            var total = Int.MAX_VALUE
            var loadedCount = 0
            val drafts = mutableListOf<Draft>()

            while (loadedCount < total) {
                val response = apiService.getMyPostList(
                    token = token,
                    request = MyPostListRequest(size = pageSize, page = page)
                )

                if (!response.isSuccessful) {
                    return Result.failure(Exception("网络请求失败: ${response.code()}"))
                }

                val body = response.body() ?: return Result.failure(Exception("响应体为空"))
                if (body.code != 1) {
                    return Result.failure(Exception(body.msg.ifBlank { "获取草稿失败" }))
                }

                val posts = body.data.posts
                total = body.data.total
                loadedCount += posts.size

                drafts += posts
                    .filter { it.isDraft == 1 }
                    .map { it.toDraftFromMyPostList() }

                if (posts.isEmpty() || posts.size < pageSize) {
                    break
                }
                page++
            }

            Result.success(drafts.sortedByDescending { it.updateTime })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 保存草稿
     */
    suspend fun saveDraft(
        token: String,
        boardId: Int,
        boardName: String,
        title: String,
        content: String,
        contentType: Int
    ): Result<Draft> {
        return try {
            val request = SaveDraftRequest(
                boardId = boardId,
                boardName = boardName.takeIf { it.isNotBlank() },
                title = title,
                content = content,
                contentType = contentType
            )
            val response = apiService.saveDraft(token, request)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    val savedDraft = body.data?.toDraft() ?: Draft(
                        id = "",
                        title = title,
                        content = content,
                        boardId = boardId,
                        boardName = boardName.ifBlank { "分区 $boardId" },
                        isMarkdownMode = contentType == 2,
                        createTime = System.currentTimeMillis(),
                        updateTime = System.currentTimeMillis()
                    )
                    Result.success(savedDraft)
                } else {
                    Result.failure(Exception(body?.msg ?: "保存草稿失败"))
                }
            } else {
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 更新草稿
     */
    suspend fun updateDraft(
        token: String,
        draftId: String,
        boardId: Int,
        boardName: String,
        title: String,
        content: String,
        contentType: Int
    ): Result<Draft> {
        return try {
            val request = UpdateDraftRequest(
                draftId = draftId,
                boardId = boardId,
                boardName = boardName.takeIf { it.isNotBlank() },
                title = title,
                content = content,
                contentType = contentType
            )
            val response = apiService.updateDraft(token, request)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    val updatedDraft = body.data?.toDraft() ?: Draft(
                        id = draftId,
                        title = title,
                        content = content,
                        boardId = boardId,
                        boardName = boardName.ifBlank { "分区 $boardId" },
                        isMarkdownMode = contentType == 2,
                        createTime = System.currentTimeMillis(),
                        updateTime = System.currentTimeMillis()
                    )
                    Result.success(updatedDraft)
                } else {
                    Result.failure(Exception(body?.msg ?: "更新草稿失败"))
                }
            } else {
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 删除草稿
     */
    suspend fun deleteDraft(
        token: String,
        draftId: String
    ): Result<BaseResponse> {
        return try {
            val request = DeleteDraftRequest(draftId = draftId)
            val response = apiService.deleteDraft(token, request)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Result.success(body)
                } else {
                    Result.failure(Exception(body?.message ?: "删除草稿失败"))
                }
            } else {
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 搜索社区内容
     */
    suspend fun searchCommunity(
        token: String,
        keyword: String,
        typ: Int = 3,
        size: Int = 50,
        page: Int = 1
    ): com.yhchat.canary.data.api.SearchResponse {
        val request = com.yhchat.canary.data.api.SearchRequest(typ = typ, keyword = keyword, size = size, page = page)
        return apiService.searchCommunity(token, request)
    }
    
    /**
     * 获取分区群聊列表
     */
    suspend fun getBoardGroupList(
        token: String,
        boardId: Int,
        size: Int = 20,
        page: Int = 1
    ): GroupListResponse {
        val request = GroupListRequest(baId = boardId, size = size, page = page)
        return apiService.getBoardGroupList(token, request)
    }
    
    /**
     * 关注分区
     */
    suspend fun followBoard(
        token: String,
        boardId: Int,
        followSource: Int = 2
    ): Result<ApiStatus> {
        return try {
            val request = FollowBoardRequest(baId = boardId, followSource = followSource)
            val response = apiService.followBoard(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Result.success(body)
                } else {
                    Result.failure(Exception("API返回错误: ${body?.message ?: "未知错误"}"))
                }
            } else {
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 取消关注分区
     */
    suspend fun unfollowBoard(
        token: String,
        boardId: Int
    ): Result<ApiStatus> {
        return try {
            val request = UnfollowBoardRequest(baId = boardId)
            val response = apiService.unfollowBoard(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Result.success(body)
                } else {
                    Result.failure(Exception("API返回错误: ${body?.message ?: "未知错误"}"))
                }
            } else {
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 屏蔽/取消屏蔽用户
     */
    suspend fun setBlackList(
        token: String,
        authorId: String,
        isAdd: Int
    ): Result<ApiStatus> {
        return try {
            val request = com.yhchat.canary.data.api.SetBlackListRequest(isAdd = isAdd, authorId = authorId)
            val response = apiService.setBlackList(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Result.success(body)
                } else {
                    Result.failure(Exception("API返回错误: ${body?.message ?: "未知错误"}"))
                }
            } else {
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 获取屏蔽用户列表
     */
    suspend fun getBlackList(
        token: String,
        size: Int = 20,
        page: Int = 1
    ): Result<BlockedUserListResponse> {
        return try {
            val request = com.yhchat.canary.data.api.BlackListRequest(size = size, page = page)
            val response = apiService.getBlackList(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Result.success(body)
                } else {
                    Result.failure(Exception("API返回错误: ${body?.msg ?: "未知错误"}"))
                }
            } else {
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 创建分区
     */
    suspend fun createBoard(
        token: String,
        name: String,
        avatar: String
    ): Result<CreateBoardResponse> {
        return try {
            val request = CreateBoardRequest(name = name, avatar = avatar)
            val response = apiService.createBoard(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    Result.success(body)
                } else {
                    Result.failure(Exception("响应体为空"))
                }
            } else {
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun CommunityPost.toDraftFromMyPostList(): Draft {
        val draftUpdateTime = when {
            editTime > 0 -> editTime
            updateTime > 0 -> updateTime
            lastActive > 0 -> lastActive
            else -> createTime
        }

        return Draft(
            id = id.toString(),
            title = title,
            content = content,
            boardId = baId,
            boardName = if (baId > 0) "分区 $baId" else "未分区",
            isMarkdownMode = contentType == 2,
            createTime = normalizeTimestampToMillis(createTime),
            updateTime = normalizeTimestampToMillis(draftUpdateTime)
        )
    }

    private fun normalizeTimestampToMillis(timestamp: Long): Long {
        return if (timestamp in 1..999_999_999_999) timestamp * 1000 else timestamp
    }
}
