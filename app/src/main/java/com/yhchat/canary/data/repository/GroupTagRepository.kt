package com.yhchat.canary.data.repository

import android.util.Log
import com.google.gson.Gson
import com.yhchat.canary.data.api.*
import com.yhchat.canary.proto.group.tag_member
import com.yhchat.canary.proto.group.tag_member_send
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupTagRepository @Inject constructor(
    private val apiService: ApiService,
    private val tokenRepository: TokenRepository
) {
    private val TAG = "GroupTagRepository"
    private val gson = Gson()
    
    /**
     * 获取群组标签列表
     */
    suspend fun getGroupTagList(
        groupId: String,
        page: Int = 1,
        size: Int = 50,
        searchTag: String = ""
    ): Result<List<GroupTag>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = tokenRepository.getTokenSync()
            if (token.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("未登录"))
            }
            
            val request = GroupTagListRequest(
                groupId = groupId,
                size = size,
                page = page,
                tag = searchTag
            )
            
            val response = apiService.getGroupTagList(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    val tags = body.data?.list ?: emptyList()
                    Log.d(TAG, "获取群组标签成功: ${tags.size}个标签")
                    Result.success(tags)
                } else {
                    val error = body?.msg ?: "获取标签列表失败"
                    Log.e(TAG, "获取群组标签失败: $error")
                    Result.failure(Exception(error))
                }
            } else {
                val error = "获取标签列表失败: HTTP ${response.code()}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取群组标签异常", e)
            Result.failure(e)
        }
    }

    /**
     * 标签排序（提交完整排序列表）
     */
    suspend fun sortGroupTags(groupId: String, tags: List<GroupTag>): Result<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = tokenRepository.getTokenSync()
            if (token.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("未登录"))
            }

            val sortPayload = tags.mapIndexed { index, tag ->
                SortGroupTagItem(
                    groupId = groupId,
                    tagId = tag.id.toString(),
                    sort = (tags.size - index).toString()
                )
            }
            val request = SortGroupTagRequest(
                sort = gson.toJson(sortPayload),
                groupId = groupId
            )

            val response = apiService.sortGroupTag(token, request)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Log.d(TAG, "标签排序成功: ${tags.size}个标签")
                    Result.success(true)
                } else {
                    val error = body?.message ?: "标签排序失败"
                    Log.e(TAG, "标签排序失败: $error")
                    Result.failure(Exception(error))
                }
            } else {
                val error = "标签排序失败: HTTP ${response.code()}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "标签排序异常", e)
            Result.failure(e)
        }
    }
    
    /**
     * 创建群组标签
     */
    suspend fun createGroupTag(
        groupId: String,
        tag: String,
        color: String,
        desc: String = "",
        sort: Int = 0
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = tokenRepository.getTokenSync()
            if (token.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("未登录"))
            }
            
            val request = CreateGroupTagRequest(
                groupId = groupId,
                tag = tag,
                color = color,
                desc = desc,
                sort = sort
            )
            
            val response = apiService.createGroupTag(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Log.d(TAG, "创建群组标签成功")
                    Result.success(true)
                } else {
                    val error = body?.message ?: "创建标签失败"
                    Log.e(TAG, "创建群组标签失败: $error")
                    Result.failure(Exception(error))
                }
            } else {
                val error = "创建标签失败: HTTP ${response.code()}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "创建群组标签异常", e)
            Result.failure(e)
        }
    }
    
    /**
     * 编辑群组标签
     */
    suspend fun editGroupTag(
        id: Long,
        groupId: String,
        tag: String,
        color: String,
        desc: String = "",
        sort: Int = 0
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = tokenRepository.getTokenSync()
            if (token.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("未登录"))
            }
            
            val request = EditGroupTagRequest(
                id = id,
                groupId = groupId,
                tag = tag,
                color = color,
                desc = desc,
                sort = sort
            )
            
            val response = apiService.editGroupTag(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Log.d(TAG, "编辑群组标签成功")
                    Result.success(true)
                } else {
                    val error = body?.message ?: "编辑标签失败"
                    Log.e(TAG, "编辑群组标签失败: $error")
                    Result.failure(Exception(error))
                }
            } else {
                val error = "编辑标签失败: HTTP ${response.code()}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "编辑群组标签异常", e)
            Result.failure(e)
        }
    }
    
    /**
     * 删除群组标签
     */
    suspend fun deleteGroupTag(id: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = tokenRepository.getTokenSync()
            if (token.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("未登录"))
            }
            
            val request = DeleteGroupTagRequest(id = id)
            val response = apiService.deleteGroupTag(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Log.d(TAG, "删除群组标签成功")
                    Result.success(true)
                } else {
                    val error = body?.message ?: "删除标签失败"
                    Log.e(TAG, "删除群组标签失败: $error")
                    Result.failure(Exception(error))
                }
            } else {
                val error = "删除标签失败: HTTP ${response.code()}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "删除群组标签异常", e)
            Result.failure(e)
        }
    }
    
    /**
     * 关联用户标签
     */
    suspend fun relateUserTag(userId: String, tagId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = tokenRepository.getTokenSync()
            if (token.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("未登录"))
            }
            
            val request = RelateUserTagRequest(
                userId = userId,
                tagGroupId = tagId
            )
            val response = apiService.relateUserTag(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Log.d(TAG, "关联用户标签成功")
                    Result.success(true)
                } else {
                    val error = body?.message ?: "关联标签失败"
                    Log.e(TAG, "关联用户标签失败: $error")
                    Result.failure(Exception(error))
                }
            } else {
                val error = "关联标签失败: HTTP ${response.code()}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "关联用户标签异常", e)
            Result.failure(e)
        }
    }
    
    /**
     * 取消关联用户标签
     */
    suspend fun cancelRelateUserTag(userId: String, tagId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = tokenRepository.getTokenSync()
            if (token.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("未登录"))
            }
            
            val request = RelateUserTagRequest(
                userId = userId,
                tagGroupId = tagId
            )
            val response = apiService.cancelRelateUserTag(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Log.d(TAG, "取消关联用户标签成功")
                    Result.success(true)
                } else {
                    val error = body?.message ?: "取消关联失败"
                    Log.e(TAG, "取消关联用户标签失败: $error")
                    Result.failure(Exception(error))
                }
            } else {
                val error = "取消关联失败: HTTP ${response.code()}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "取消关联用户标签异常", e)
            Result.failure(e)
        }
    }
    
    /**
     * 获取标签绑定的用户列表
     */
    suspend fun getTagMembers(
        groupId: String,
        tagId: Long,
        page: Int = 1,
        size: Int = 50
    ): Result<TagMembersData> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = tokenRepository.getTokenSync()
            if (token.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("未登录"))
            }
            
            // 构建ProtoBuf请求
            val request = tag_member_send.newBuilder()
                .setData(
                    tag_member_send.Data.newBuilder()
                        .setSize(size)
                        .setPage(page)
                        .build()
                )
                .setGroupId(groupId)
                .setTagId(tagId)
                .build()
            
            val requestBody = request.toByteArray()
                .toRequestBody("application/x-protobuf".toMediaType())
            
            val response = apiService.getTagMembers(token, requestBody)
            
            if (response.isSuccessful) {
                val responseBody = response.body()
                if (responseBody != null) {
                    val protoBytes = responseBody.bytes()
                    val tagMemberProto = tag_member.parseFrom(protoBytes)
                    
                    if (tagMemberProto.status.code == 1) {
                        val members = tagMemberProto.userList.map { user ->
                            TagMemberInfo(
                                userId = user.userInfo.userId,
                                name = user.userInfo.name,
                                avatarUrl = user.userInfo.avatarUrl,
                                isVip = user.userInfo.isVip == 1,
                                permissionLevel = user.permissionLevel,
                                gagTime = user.gagTime,
                                isGag = user.isGag == 1
                            )
                        }
                        
                        Log.d(TAG, "获取标签成员成功: ${members.size}/${tagMemberProto.total}")
                        Result.success(
                            TagMembersData(
                                members = members,
                                total = tagMemberProto.total
                            )
                        )
                    } else {
                        val error = tagMemberProto.status.msg
                        Log.e(TAG, "获取标签成员失败: $error")
                        Result.failure(Exception(error))
                    }
                } else {
                    val error = "响应体为空"
                    Log.e(TAG, error)
                    Result.failure(Exception(error))
                }
            } else {
                val error = "获取标签成员失败: HTTP ${response.code()}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取标签成员异常", e)
            Result.failure(e)
        }
    }
}

/**
 * 标签成员数据
 */
data class TagMembersData(
    val members: List<TagMemberInfo>,
    val total: Long
)

/**
 * 标签成员信息
 */
data class TagMemberInfo(
    val userId: String,
    val name: String,
    val avatarUrl: String,
    val isVip: Boolean,
    val permissionLevel: Int,
    val gagTime: Long,
    val isGag: Boolean
)

