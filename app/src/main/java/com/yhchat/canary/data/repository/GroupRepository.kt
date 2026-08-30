package com.yhchat.canary.data.repository

import android.util.Log
import com.google.protobuf.InvalidProtocolBufferException
import com.yhchat.canary.data.model.GroupDetail
import com.yhchat.canary.data.model.GroupMemberInfo
import com.yhchat.canary.proto.group.Bot_data
import com.yhchat.canary.proto.group.bot_list
import com.yhchat.canary.proto.group.bot_list_send
import com.yhchat.canary.proto.group.edit_group
import com.yhchat.canary.proto.group.edit_group_send
import com.yhchat.canary.proto.group.info
import com.yhchat.canary.proto.group.info_send
import com.yhchat.canary.proto.group.list_member
import com.yhchat.canary.proto.group.list_member_send
import com.yhchat.canary.proto.instruction.get_bot_instruction
import com.yhchat.canary.proto.instruction.instruction_list
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupRepository @Inject constructor(
    private val apiService: com.yhchat.canary.data.api.ApiService
) {

    private val tag = "GroupRepository"
    private val baseUrl = "https://chat-go.jwzhd.com"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private var tokenRepository: TokenRepository? = null

    fun setTokenRepository(tokenRepository: TokenRepository) {
        this.tokenRepository = tokenRepository
    }

    /**
     * 设置隐藏群成员
     * POST /v1/group/edit-group (protobuf)
     */
    suspend fun setHideGroupMembers(
        groupInfo: GroupDetail,
        hide: Boolean
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        Log.d(tag, "🙈 Setting hideGroupMembers for group: ${groupInfo.groupId} => $hide")
        val token = tokenRepository?.getTokenSync()
        if (token.isNullOrEmpty()) {
            return@withContext Result.failure(Exception("未登录"))
        }

        return@withContext try {
            val requestData = edit_group_send.newBuilder()
                .setGroupId(groupInfo.groupId)
                .setName(groupInfo.name)
                .setIntroduction(groupInfo.introduction)
                .setAvatarUrl(groupInfo.avatarUrl)
                .setDirectJoin(if (groupInfo.directJoin) 1 else 0)
                .setHistoryMsg(if (groupInfo.historyMsgEnabled) 1 else 0)
                .setCategoryName(groupInfo.categoryName)
                .setCategoryId(groupInfo.categoryId)
                .setPrivate(if (groupInfo.isPrivate) 1 else 0)
                .setHideGroupMembers(if (hide) 1 else 0)
                .build()

            val requestBody = requestData.toByteArray()
                .toRequestBody("application/x-protobuf".toMediaTypeOrNull())

            val request = Request.Builder()
                .url("$baseUrl/v1/group/edit-group")
                .addHeader("token", token)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("网络请求失败: ${response.code}"))
            }

            val responseBytes = response.body?.bytes() ?: return@withContext Result.failure(Exception("响应为空"))
            val editResponse = edit_group.parseFrom(responseBytes)
            if (editResponse.status.code == 1) {
                Result.success(true)
            } else {
                Result.failure(Exception(editResponse.status.msg))
            }
        } catch (e: Exception) {
            Log.e(tag, "❌ Failed to set hideGroupMembers", e)
            Result.failure(e)
        }
    }

    /**
     * 设置禁止群成员上传到群云盘
     * POST /v1/group/edit-stop-member-upload-group-file (json)
     */
    suspend fun setDenyMembersUploadToGroupDisk(
        groupId: String,
        deny: Boolean
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        Log.d(tag, "📁 Setting denyMembersUploadToGroupDisk for group: $groupId => $deny")
        val token = tokenRepository?.getTokenSync()
        if (token.isNullOrEmpty()) {
            return@withContext Result.failure(Exception("未登录"))
        }

        return@withContext try {
            val json = JSONObject()
                .put("groupId", groupId)
                .put("stopMemberUploadGroupFile", if (deny) 1 else 0)

            val request = Request.Builder()
                .url("$baseUrl/v1/group/edit-stop-member-upload-group-file")
                .addHeader("token", token)
                .post(json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            val response = client.newCall(request).execute()
            val bodyText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("网络请求失败: ${response.code}"))
            }

            val obj = runCatching { JSONObject(bodyText) }.getOrNull()
            val code = obj?.optInt("code", 0) ?: 0
            val msg = obj?.optString("msg", "") ?: ""
            if (code == 1) {
                Result.success(true)
            } else {
                Result.failure(Exception(if (msg.isNotEmpty()) msg else "设置失败"))
            }
        } catch (e: Exception) {
            Log.e(tag, "❌ Failed to set denyMembersUploadToGroupDisk", e)
            Result.failure(e)
        }
    }

    /**
     * 获取机器人私聊指令列表（ProtoBuf API）
     */
    suspend fun getBotInstructionList(botId: String): Result<List<com.yhchat.canary.data.model.Instruction>> {
        return try {
            val token = tokenRepository?.getTokenSync()
            if (token == null) {
                return Result.failure(Exception("未登录"))
            }

            val request = get_bot_instruction.newBuilder()
                .setId(botId)
                .build()

            val requestBody = request.toByteArray()
                .toRequestBody("application/x-protobuf".toMediaTypeOrNull())

            val response = apiService.getBotInstructionListProto(token, requestBody)

            if (response.isSuccessful) {
                val responseBody = response.body()?.bytes()
                if (responseBody != null) {
                    val parsed = instruction_list.parseFrom(responseBody)
                    if (parsed.status.code == 1L) {
                        val instructions = parsed.dataList.map { item ->
                            com.yhchat.canary.data.model.Instruction(
                                botId = item.botId,
                                botName = "",
                                name = item.commandName,
                                desc = item.commandDescription,
                                id = item.commandId.toLong(),
                                sort = 0,
                                auth = 0,
                                type = item.instructionType.toInt(),
                                hintText = "",
                                defaultText = item.defaultText,
                                form = item.botSettingsJson
                            )
                        }
                        Log.d(tag, "✅ 从ProtoBuf获取到机器人指令 ${instructions.size} 条")
                        Result.success(instructions)
                    } else {
                        Result.failure(Exception(parsed.status.msg))
                    }
                } else {
                    Result.failure(Exception("响应体为空"))
                }
            } else {
                Result.failure(Exception("请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(tag, "获取机器人私聊指令失败", e)
            Result.failure(e)
        }
    }

    /**
     * 设置群聊消息自动销毁时间
     * POST /v1/group/edit-auto-delete-message
     */
    suspend fun editAutoDeleteMessage(
        groupId: String,
        autoDeleteMessage: Int
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val token = tokenRepository?.getTokenSync()
        if (token.isNullOrEmpty()) {
            return@withContext Result.failure(Exception("未登录"))
        }

        return@withContext try {
            val request = com.yhchat.canary.data.api.EditAutoDeleteMessageRequest(
                groupId = groupId,
                autoDeleteMessage = autoDeleteMessage
            )

            val response = apiService.editAutoDeleteMessage(token, request)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Result.success(true)
                } else {
                    Result.failure(Exception(body?.message ?: "设置失败"))
                }
            } else {
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 获取群聊信息
     */
    suspend fun getGroupInfo(groupId: String): Result<GroupDetail> = withContext(Dispatchers.IO) {
        Log.d(tag, "🔍 Getting group info for: $groupId")
        val token = tokenRepository?.getTokenSync()
        if (token.isNullOrEmpty()) {
            Log.e(tag, "❌ No token available")
            return@withContext Result.failure(Exception("未登录"))
        }

        Log.d(tag, "Token available, length: ${token.length}")

        return@withContext try {
            // 构建protobuf请求
            val request = info_send.newBuilder()
                .setGroupId(groupId)
                .build()

            Log.d(tag, "Request protobuf built, groupId: $groupId")

            val requestBody = request.toByteArray()
                .toRequestBody("application/x-protobuf".toMediaTypeOrNull())

            val httpRequest = Request.Builder()
                .url("$baseUrl/v1/group/info")
                .addHeader("token", token)
                .post(requestBody)
                .build()

            Log.d(tag, "Sending request to: $baseUrl/v1/group/info")

            val response = client.newCall(httpRequest).execute()

            Log.d(tag, "✅ Response code: ${response.code}")
            Log.d(tag, "Response message: ${response.message}")
            Log.d(tag, "Response headers: ${response.headers}")

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "No error body"
                Log.e(tag, "❌ Request failed with code ${response.code}: $errorBody")
                return@withContext Result.failure(IOException("请求失败: ${response.code} - ${response.message}"))
            }

            val responseBody = response.body?.bytes()
            if (responseBody == null) {
                Log.e(tag, "❌ Response body is null")
                return@withContext Result.failure(IOException("响应为空"))
            }

            Log.d(tag, "✅ Response body size: ${responseBody.size} bytes")

            // 解析protobuf响应
            Log.d(tag, "Parsing protobuf response, size: ${responseBody.size} bytes")
            val infoResponse = info.parseFrom(responseBody)

            Log.d(
                tag,
                "Protobuf parsed. Status code: ${infoResponse.status.code}, msg: ${infoResponse.status.msg}"
            )

            if (infoResponse.status.code != 1) {
                Log.e(tag, "❌ Server returned error: ${infoResponse.status.msg}")
                return@withContext Result.failure(Exception(infoResponse.status.msg))
            }

            val data = infoResponse.data
            Log.d(
                tag,
                "Group data: groupId=${data.groupId}, name=${data.name}, members=${data.member}"
            )

            val groupInfo = GroupDetail(
                groupId = data.groupId,
                name = data.name,
                avatarUrl = data.avatarUrl,
                introduction = data.introduction,
                memberCount = data.member.toInt(),
                createBy = data.createBy,
                directJoin = data.directJoin == 1,
                permissionLevel = data.permissonLevel,
                historyMsgEnabled = data.historyMsg == 1,
                categoryName = data.categoryName,
                categoryId = data.categoryId,
                isPrivate = data.private == 1,
                doNotDisturb = data.doNotDisturb == 1,
                hideGroupMembers = data.hideGroupMembers == 1L,
                denyMembersUploadToGroupDisk = data.denyMembersUploadToGroupDisk == 1L,
                communityId = data.communityId,
                communityName = data.communityName,
                isTop = data.top == 1,
                adminIds = data.adminList,
                ownerId = data.owner,
                limitedMsgType = data.limitedMsgType,
                avatarId = data.avatarId,
                recommendation = data.recommandation,
                myGroupNickname = data.myGroupNickname.takeIf { it.isNotEmpty() },
                groupCode = data.groupCode.takeIf { it.isNotEmpty() },
                autoDeleteMessage = data.autoDeleteMessage,
                unbanTimestamp = if (data.unbanTimestamp > 0L) data.unbanTimestamp else null,
                banReason = data.banReason.takeIf { it.isNotBlank() }
            )

            Log.d(
                tag,
                "✅ Group info successfully created: ${groupInfo.name}, members: ${groupInfo.memberCount}"
            )
            Result.success(groupInfo)

        } catch (e: InvalidProtocolBufferException) {
            Log.e(tag, "❌ Protobuf parse error: ${e.message}", e)
            Log.e(tag, "Error details: ${e.stackTraceToString()}")
            Result.failure(e)
        } catch (e: IOException) {
            Log.e(tag, "❌ Network/IO error: ${e.message}", e)
            Log.e(tag, "Error details: ${e.stackTraceToString()}")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(tag, "❌ Unknown error: ${e.message}", e)
            Log.e(tag, "Error type: ${e::class.java.simpleName}")
            Log.e(tag, "Error details: ${e.stackTraceToString()}")
            Result.failure(e)
        }
    }

    /**
     * 获取群成员列表
     */
    suspend fun getGroupMembers(
        groupId: String,
        size: Int = 50,
        page: Int = 1,
        keywords: String = ""
    ): Result<List<GroupMemberInfo>> = withContext(Dispatchers.IO) {
        val token = tokenRepository?.getTokenSync()
        if (token.isNullOrEmpty()) {
            return@withContext Result.failure(Exception("未登录"))
        }

        return@withContext try {
            // 构建protobuf请求
            val requestBuilder = list_member_send.newBuilder()
                .setGroupId(groupId)
                .setData(
                    list_member_send.Data.newBuilder()
                        .setSize(size)
                        .setPage(page)
                        .build()
                )
            
            // 如果有搜索关键词，添加到请求中
            if (keywords.isNotEmpty()) {
                requestBuilder.setKeywords(keywords)
            }
            
            val request = requestBuilder.build()

            val requestBody = request.toByteArray()
                .toRequestBody("application/x-protobuf".toMediaTypeOrNull())

            val httpRequest = Request.Builder()
                .url("$baseUrl/v1/group/list-member")
                .addHeader("token", token)
                .post(requestBody)
                .build()

            val response = client.newCall(httpRequest).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("请求失败: ${response.code}"))
            }

            val responseBody = response.body?.bytes()
                ?: return@withContext Result.failure(IOException("响应为空"))

            // 解析protobuf响应
            val listResponse = list_member.parseFrom(responseBody)

            if (listResponse.status.code != 1) {
                return@withContext Result.failure(Exception(listResponse.status.msg))
            }

            val members = listResponse.userList.map { user ->
                GroupMemberInfo(
                    userId = user.userInfo.userId,
                    name = user.userInfo.name,
                    avatarUrl = user.userInfo.avatarUrl,
                    isVip = user.userInfo.isVip == 1,
                    permissionLevel = user.permissionLevel,
                    gagTime = user.gagTime,
                    isGag = user.isGag == 1
                )
            }

            Log.d(tag, "Group members loaded: ${members.size}")
            Result.success(members)

        } catch (e: InvalidProtocolBufferException) {
            Log.e(tag, "Protobuf parse error", e)
            Result.failure(e)
        } catch (e: IOException) {
            Log.e(tag, "Network error", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(tag, "Unknown error", e)
            Result.failure(e)
        }
    }
    
    /**
     * 搜索群成员
     */
    suspend fun searchGroupMembers(
        groupId: String,
        keywords: String,
        size: Int = 50,
        page: Int = 1
    ): Result<List<GroupMemberInfo>> {
        // 直接调用 getGroupMembers 并传入 keywords
        return getGroupMembers(groupId, size, page, keywords)
    }
    
    /**
     * 编辑群聊信息
     */
    suspend fun editGroupInfo(
        groupId: String,
        name: String,
        introduction: String,
        avatarUrl: String,
        directJoin: Boolean,
        historyMsg: Boolean,
        categoryName: String,
        categoryId: Long,
        isPrivate: Boolean
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        Log.d(tag, "✏️ Editing group info for: $groupId")
        val token = tokenRepository?.getTokenSync()
        if (token.isNullOrEmpty()) {
            Log.e(tag, "❌ No token available")
            return@withContext Result.failure(Exception("未登录"))
        }

        return@withContext try {
            // 构建请求
            val requestBuilder = edit_group_send.newBuilder()
                .setGroupId(groupId)
                .setName(name)
                .setIntroduction(introduction)
                .setAvatarUrl(avatarUrl)
                .setDirectJoin(if (directJoin) 1 else 0)
                .setHistoryMsg(if (historyMsg) 1 else 0)
                .setCategoryName(categoryName)
                .setCategoryId(categoryId)
                .setPrivate(if (isPrivate) 1 else 0)

            val requestData = requestBuilder.build()
            val requestBytes = requestData.toByteArray()
            val requestBody = requestBytes.toRequestBody("application/x-protobuf".toMediaTypeOrNull())

            val request = Request.Builder()
                .url("$baseUrl/v1/group/edit-group")
                .addHeader("token", token)
                .post(requestBody)
                .build()

            Log.d(tag, "📤 Sending edit group request...")
            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val responseData = response.body?.bytes()
                if (responseData != null) {
                    val editResponse = edit_group.parseFrom(responseData)
                    if (editResponse.status.code == 1) {
                        Log.d(tag, "✅ Group info edited successfully")
                        Result.success(true)
                    } else {
                        Log.e(tag, "❌ Edit failed: ${editResponse.status.msg}")
                        Result.failure(Exception(editResponse.status.msg))
                    }
                } else {
                    Log.e(tag, "❌ Empty response body")
                    Result.failure(Exception("响应为空"))
                }
            } else {
                Log.e(tag, "❌ HTTP error: ${response.code}")
                Result.failure(Exception("网络请求失败: ${response.code}"))
            }
        } catch (e: InvalidProtocolBufferException) {
            Log.e(tag, "Protobuf parse error", e)
            Result.failure(e)
        } catch (e: IOException) {
            Log.e(tag, "Network error", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(tag, "Unknown error", e)
            Result.failure(e)
        }
    }
    
    /**
     * 设置消息类型限制
     * POST /v1/group/msg-type-limit
     */
    suspend fun setMessageTypeLimit(
        groupId: String,
        messageTypes: String  // 例如: "1,2,3,4"
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        Log.d(tag, "🚫 Setting message type limit for group: $groupId, types: $messageTypes")
        val token = tokenRepository?.getTokenSync()
        if (token.isNullOrEmpty()) {
            Log.e(tag, "❌ No token available")
            return@withContext Result.failure(Exception("未登录"))
        }

        return@withContext try {
            val requestBody = """{"groupId":"$groupId","type":"$messageTypes"}"""
                .toRequestBody("application/json".toMediaTypeOrNull())

            val request = Request.Builder()
                .url("$baseUrl/v1/group/msg-type-limit")
                .addHeader("token", token)
                .post(requestBody)
                .build()

            Log.d(tag, "📤 Sending message type limit request...")
            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val responseText = response.body?.string()
                Log.d(tag, "✅ Message type limit set successfully: $responseText")
                Result.success(true)
            } else {
                Log.e(tag, "❌ HTTP error: ${response.code}")
                Result.failure(Exception("设置消息类型限制失败: ${response.code}"))
            }
        } catch (e: IOException) {
            Log.e(tag, "Network error", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(tag, "Unknown error", e)
            Result.failure(e)
        }
    }
    
    /**
     * 踢出群成员
     * POST /v1/group/remove-member
     */
    suspend fun removeMember(
        groupId: String,
        userId: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        Log.d(tag, "👢 Removing member from group: $groupId, userId: $userId")
        val token = tokenRepository?.getTokenSync()
        if (token.isNullOrEmpty()) {
            Log.e(tag, "❌ No token available")
            return@withContext Result.failure(Exception("未登录"))
        }

        return@withContext try {
            val requestBody = """{"groupId":"$groupId","userId":"$userId"}"""
                .toRequestBody("application/json".toMediaTypeOrNull())

            val request = Request.Builder()
                .url("$baseUrl/v1/group/remove-member")
                .addHeader("token", token)
                .post(requestBody)
                .build()

            Log.d(tag, "📤 Sending remove member request...")
            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val responseText = response.body?.string()
                Log.d(tag, "✅ Member removed successfully: $responseText")
                Result.success(true)
            } else {
                Log.e(tag, "❌ HTTP error: ${response.code}")
                Result.failure(Exception("踢出用户失败: ${response.code}"))
            }
        } catch (e: IOException) {
            Log.e(tag, "Network error", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(tag, "Unknown error", e)
            Result.failure(e)
        }
    }
    
    /**
     * 转让群主
     * POST /v1/group/transfer-group
     */
    suspend fun transferGroupOwner(
        groupId: String,
        userId: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        Log.d(tag, "👑 Transferring group owner: groupId=$groupId, userId=$userId")
        val token = tokenRepository?.getTokenSync()
        if (token.isNullOrEmpty()) {
            Log.e(tag, "❌ No token available")
            return@withContext Result.failure(Exception("未登录"))
        }

        return@withContext try {
            val requestBody = """{"groupId":"$groupId","userId":"$userId"}"""
                .toRequestBody("application/json".toMediaTypeOrNull())

            val request = Request.Builder()
                .url("$baseUrl/v1/group/transfer-group")
                .addHeader("token", token)
                .post(requestBody)
                .build()

            Log.d(tag, "📤 Sending transfer group owner request...")
            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val responseText = response.body?.string()
                Log.d(tag, "✅ Group owner transferred successfully: $responseText")
                Result.success(true)
            } else {
                Log.e(tag, "❌ HTTP error: ${response.code}")
                Result.failure(Exception("转让群主失败: ${response.code}"))
            }
        } catch (e: IOException) {
            Log.e(tag, "Network error", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(tag, "Unknown error", e)
            Result.failure(e)
        }
    }

    
    /**
     * 禁言群成员
     * POST /v1/group/gag-member
     * @param gagTime 禁言时间: 0-取消禁言, 600-10分钟, 3600-1小时, 21600-6小时, 43200-12小时, 1-永久禁言
     */
    suspend fun gagMember(
        groupId: String,
        userId: String,
        gagTime: Int
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        Log.d(tag, "🔇 Gagging member in group: $groupId, userId: $userId, gagTime: $gagTime")
        val token = tokenRepository?.getTokenSync()
        if (token.isNullOrEmpty()) {
            Log.e(tag, "❌ No token available")
            return@withContext Result.failure(Exception("未登录"))
        }

        return@withContext try {
            val requestBody = """{"groupId":"$groupId","userId":"$userId","gag":$gagTime}"""
                .toRequestBody("application/json".toMediaTypeOrNull())

            val request = Request.Builder()
                .url("$baseUrl/v1/group/gag-member")
                .addHeader("token", token)
                .post(requestBody)
                .build()

            Log.d(tag, "📤 Sending gag member request...")
            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val responseText = response.body?.string()
                Log.d(tag, "✅ Member gagged successfully: $responseText")
                Result.success(true)
            } else {
                Log.e(tag, "❌ HTTP error: ${response.code}")
                Result.failure(Exception("禁言用户失败: ${response.code}"))
            }
        } catch (e: IOException) {
            Log.e(tag, "Network error", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(tag, "Unknown error", e)
            Result.failure(e)
        }
    }
    
    /**
     * 设置管理员（上任/卸任）
     * POST /v1/group/manage-setting
     * @param userLevel 用户等级: 0-普通成员, 2-管理员
     */
    suspend fun setMemberRole(
        groupId: String,
        userId: String,
        userLevel: Int
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        Log.d(tag, "⚙️ Setting member role in group: $groupId, userId: $userId, userLevel: $userLevel")
        val token = tokenRepository?.getTokenSync()
        if (token.isNullOrEmpty()) {
            Log.e(tag, "❌ No token available")
            return@withContext Result.failure(Exception("未登录"))
        }

        return@withContext try {
            val requestBody = """{"groupId":"$groupId","userId":"$userId","userLevel":$userLevel}"""
                .toRequestBody("application/json".toMediaTypeOrNull())

            val request = Request.Builder()
                .url("$baseUrl/v1/group/manage-setting")
                .addHeader("token", token)
                .post(requestBody)
                .build()

            Log.d(tag, "📤 Sending set member role request...")
            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val responseText = response.body?.string()
                Log.d(tag, "✅ Member role set successfully: $responseText")
                Result.success(true)
            } else {
                Log.e(tag, "❌ HTTP error: ${response.code}")
                Result.failure(Exception("设置成员角色失败: ${response.code}"))
            }
        } catch (e: IOException) {
            Log.e(tag, "Network error", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(tag, "Unknown error", e)
            Result.failure(e)
        }
    }
    
    /**
     * 获取群机器人列表（ProtoBuf API，包含详细指令信息）
     */
    suspend fun getGroupBotList(groupId: String): Result<List<com.yhchat.canary.data.model.Instruction>> {
        return try {
            val token = tokenRepository?.getTokenSync()
            if (token == null) {
                return Result.failure(Exception("未登录"))
            }
            
            // 构建ProtoBuf请求
            val request = bot_list_send.newBuilder()
                .setGroupId(groupId)
                .build()
            
            val requestBody = request.toByteArray()
                .toRequestBody("application/x-protobuf".toMediaTypeOrNull())
            
            val response = apiService.getGroupBotList(token, requestBody)
            
            if (response.isSuccessful) {
                val responseBody = response.body()?.bytes()
                if (responseBody != null) {
                    val botListResponse = bot_list.parseFrom(responseBody)
                    
                    if (botListResponse.status.code == 1) {
                        // 解析指令列表 - 注意：protobuf中字段名是instruction（单数）
                        val instructions = botListResponse.instructionList.map { instructionData ->
                            com.yhchat.canary.data.model.Instruction(
                                id = instructionData.id,
                                botId = instructionData.botId,
                                name = instructionData.name,
                                desc = instructionData.desc,
                                botName = instructionData.botName,
                                type = instructionData.type,
                                hintText = instructionData.hintText,
                                defaultText = instructionData.defaultText,
                                form = instructionData.form,
                                sort = instructionData.sort.toInt(),
                                auth = 0 // ProtoBuf中没有auth字段，默认为0
                            )
                        }
                        
                        Log.d(tag, "✅ 从ProtoBuf获取到 ${instructions.size} 条指令")
                        
                        // 同时获取机器人列表，供后续使用
                        val bots = botListResponse.botList.map { botData ->
                            Log.d(tag, "  机器人: ${botData.name} (${botData.botId})")
                            botData
                        }
                        
                        Log.d(tag, "从ProtoBuf获取到 ${instructions.size} 条指令")
                        Result.success(instructions)
                    } else {
                        Result.failure(Exception(botListResponse.status.msg))
                    }
                } else {
                    Result.failure(Exception("响应体为空"))
                }
            } else {
                Result.failure(Exception("请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(tag, "获取群机器人列表失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 获取群聊中的机器人列表（返回机器人数据）
     */
    suspend fun getGroupBots(groupId: String): Result<List<Bot_data>> {
        return try {
            val token = tokenRepository?.getTokenSync()
            if (token == null) {
                return Result.failure(Exception("未登录"))
            }
            
            // 构建ProtoBuf请求
            val request = bot_list_send.newBuilder()
                .setGroupId(groupId)
                .build()
            
            val requestBody = request.toByteArray()
                .toRequestBody("application/x-protobuf".toMediaTypeOrNull())
            
            val response = apiService.getGroupBotList(token, requestBody)
            
            if (response.isSuccessful) {
                val responseBody = response.body()?.bytes()
                if (responseBody != null) {
                    val botListResponse = bot_list.parseFrom(responseBody)
                    
                    if (botListResponse.status.code == 1) {
                        val bots = botListResponse.botList
                        Log.d(tag, "✅ 获取到 ${bots.size} 个群机器人")
                        Result.success(bots)
                    } else {
                        Result.failure(Exception(botListResponse.status.msg))
                    }
                } else {
                    Result.failure(Exception("响应体为空"))
                }
            } else {
                Result.failure(Exception("请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(tag, "获取群机器人列表失败", e)
            Result.failure(e)
        }
    }

    suspend fun editBotGroupPermission(
        groupId: String,
        botId: String,
        allowEditGroupInfo: Int,
        allowGagMember: Int,
        allowRemoveMember: Int,
        allowGroupTagManage: Int
    ): Result<Boolean> {
        return try {
            val token = tokenRepository?.getTokenSync()
            if (token.isNullOrEmpty()) {
                return Result.failure(Exception("未登录"))
            }

            val response = apiService.editBotGroupPermission(
                token = token,
                request = com.yhchat.canary.data.api.BotGroupPermissionEditRequest(
                    groupId = groupId,
                    botId = botId,
                    allowEditGroupInfo = allowEditGroupInfo,
                    allowGagMember = allowGagMember,
                    allowRemoveMember = allowRemoveMember,
                    allowGroupTagManage = allowGroupTagManage
                )
            )

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Result.success(true)
                } else {
                    Result.failure(Exception(body?.message ?: "更新机器人权限失败"))
                }
            } else {
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(tag, "编辑机器人群权限失败", e)
            Result.failure(e)
        }
    }

    suspend fun getBotGroupPermission(
        groupId: String,
        botId: String
    ): Result<com.yhchat.canary.data.api.BotGroupPermissionData> {
        return try {
            val token = tokenRepository?.getTokenSync()
            if (token.isNullOrEmpty()) {
                return Result.failure(Exception("未登录"))
            }

            val response = apiService.getBotGroupPermission(
                token = token,
                request = com.yhchat.canary.data.api.BotGroupPermissionGetRequest(
                    botId = botId,
                    groupId = groupId
                )
            )

            if (response.isSuccessful) {
                val body = response.body()
                val data = body?.data
                if (body != null && body.code == 1 && data != null) {
                    Result.success(data)
                } else {
                    Result.failure(Exception(body?.msg ?: "获取机器人权限失败"))
                }
            } else {
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(tag, "获取机器人群权限失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 获取群指令列表（JSON API）
     */
    suspend fun getInstructionList(groupId: String): Result<List<com.yhchat.canary.data.model.Instruction>> {
        return try {
            val token = tokenRepository?.getTokenSync()
            if (token == null) {
                return Result.failure(Exception("未登录"))
            }
            
            val response = apiService.getInstructionList(
                token = token,
                request = com.yhchat.canary.data.model.GroupIdRequest(groupId = groupId)
            )
            
            if (response.isSuccessful && response.body()?.code == 1) {
                val instructions: List<com.yhchat.canary.data.model.Instruction> = response.body()?.data?.instructions ?: emptyList()
                Result.success(instructions)
            } else {
                Result.failure(Exception(response.body()?.msg ?: "获取指令列表失败"))
            }
        } catch (e: Exception) {
            Log.e(tag, "获取指令列表失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 获取群聊菜单按钮
     */
    suspend fun getGroupMenuButtons(groupId: String): Result<List<com.yhchat.canary.data.model.MenuButton>> {
        return try {
            val token = tokenRepository?.getTokenSync()
            if (token == null) {
                return Result.failure(Exception("未登录"))
            }
            
            // 构建ProtoBuf请求
            val request = bot_list_send.newBuilder()
                .setGroupId(groupId)
                .build()
            
            val requestBody = request.toByteArray()
                .toRequestBody("application/x-protobuf".toMediaTypeOrNull())
            
            val response = apiService.getGroupBotList(token, requestBody)
            
            if (response.isSuccessful) {
                val responseBody = response.body()?.bytes()
                if (responseBody != null) {
                    val botListResponse = bot_list.parseFrom(responseBody)
                    
                    if (botListResponse.status.code == 1) {
                        // 解析菜单按钮列表
                        val menuButtons = botListResponse.menuList.map { menuData ->
                            com.yhchat.canary.data.model.MenuButton(
                                id = menuData.id,
                                botId = menuData.botId,
                                name = menuData.name,
                                content = menuData.content,
                                menuType = menuData.menuType,
                                createTime = menuData.createTime,
                                menuAction = menuData.menuAction,
                                select = menuData.select
                            )
                        }
                        
                        Log.d(tag, "✅ 获取到 ${menuButtons.size} 个菜单按钮")
                        menuButtons.forEach { button ->
                            Log.d(tag, "  - 按钮: id=${button.id}, name=${button.name}, content=${button.content}, botId=${button.botId}, type=${button.menuType}")
                        }
                        Result.success(menuButtons)
                    } else {
                        Result.failure(Exception(botListResponse.status.msg))
                    }
                } else {
                    Result.failure(Exception("响应体为空"))
                }
            } else {
                Result.failure(Exception("请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(tag, "获取群聊菜单按钮失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 点击菜单按钮
     */
    suspend fun clickMenuButton(
        buttonId: Long,
        chatId: String,
        chatType: Int,
        value: String = ""
    ): Result<Boolean> {
        return try {
            val token = tokenRepository?.getTokenSync()
            if (token == null) {
                return Result.failure(Exception("未登录"))
            }
            
            val request = com.yhchat.canary.data.api.MenuEventRequest(
                id = buttonId,
                chatId = chatId,
                chatType = chatType,
                value = value
            )
            
            val response = apiService.menuEvent(token, request)
            
            if (response.isSuccessful && response.body()?.code == 1) {
                Log.d(tag, "✅ 菜单按钮点击成功")
                Result.success(true)
            } else {
                Result.failure(Exception(response.body()?.message ?: "点击失败"))
            }
        } catch (e: Exception) {
            Log.e(tag, "菜单按钮点击失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 邀请加入群聊
     */
    suspend fun inviteToGroup(chatId: String, chatType: Int, groupId: String): Result<Boolean> {
        return try {
            val token = tokenRepository?.getTokenSync()
            if (token == null) {
                return Result.failure(Exception("未登录"))
            }
            
            val response = apiService.inviteToGroup(
                token = token,
                request = com.yhchat.canary.data.model.InviteGroupRequest(
                    chatId = chatId,
                    chatType = chatType,
                    groupId = groupId
                )
            )
            
            if (response.isSuccessful && response.body()?.code == 1) {
                Result.success(true)
            } else {
                Result.failure(Exception(response.body()?.message ?: "邀请失败"))
            }
        } catch (e: Exception) {
            Log.e(tag, "邀请失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 设置我的群昵称
     */
    suspend fun editMyGroupNickname(groupId: String, nickname: String): Result<Boolean> {
        return try {
            val token = tokenRepository?.getTokenSync()
            if (token == null) {
                return Result.failure(Exception("未登录"))
            }
            
            val response = apiService.editMyGroupNickname(
                token = token,
                request = mapOf(
                    "groupId" to groupId,
                    "nickname" to nickname
                )
            )
            
            if (response.isSuccessful) {
                Log.d(tag, "✅ 群昵称修改成功")
                Result.success(true)
            } else {
                Result.failure(Exception("修改群昵称失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(tag, "修改群昵称失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 设置群口令（仅群主）
     */
    suspend fun editGroupKeyword(groupId: String, keyword: String): Result<Boolean> {
        return try {
            val token = tokenRepository?.getTokenSync()
            if (token == null) {
                return Result.failure(Exception("未登录"))
            }
            
            val response = apiService.editGroupKeyword(
                token = token,
                request = mapOf(
                    "groupId" to groupId,
                    "keyword" to keyword
                )
            )
            
            if (response.isSuccessful && response.body()?.code == 1) {
                Log.d(tag, "✅ 群口令修改成功")
                Result.success(true)
            } else {
                Result.failure(Exception("修改群口令失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(tag, "修改群口令失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 创建群聊
     */
    suspend fun createGroup(
        name: String,
        introduction: String,
        avatarUrl: String,
        category: String,
        categoryId: Int
    ): Result<String> {
        return try {
            val token = tokenRepository?.getTokenSync()
            if (token == null) {
                return Result.failure(Exception("未登录"))
            }
            
            // 构建ProtoBuf请求
            val request = com.yhchat.canary.proto.group.create_group_send.newBuilder()
                .setName(name)
                .setIntroduction(introduction)
                .setAvatarUrl(avatarUrl)
                .setCategory(category)
                .setCategoryId(categoryId)
                .build()
            
            val requestBody = request.toByteArray()
                .toRequestBody("application/x-protobuf".toMediaTypeOrNull())
            
            Log.d(tag, "🏗️ 创建群聊: name=$name")
            val response = apiService.createGroup(token, requestBody)
            
            if (response.isSuccessful) {
                val responseBody = response.body()?.bytes()
                if (responseBody != null) {
                    val createGroupResponse = com.yhchat.canary.proto.group.create_group.parseFrom(responseBody)
                    
                    if (createGroupResponse.status.code == 1) {
                        val groupId = createGroupResponse.groupId
                        Log.d(tag, "✅ 群聊创建成功: groupId=$groupId")
                        Result.success(groupId)
                    } else {
                        Result.failure(Exception(createGroupResponse.status.msg))
                    }
                } else {
                    Result.failure(Exception("响应体为空"))
                }
            } else {
                Result.failure(Exception("请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(tag, "创建群聊失败", e)
            Result.failure(e)
        }
    }
}
