package com.yhchat.canary.data.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.yhchat.canary.data.api.ApiService
import com.yhchat.canary.data.local.AppDatabase
import com.yhchat.canary.data.local.CachedProfileData
import com.yhchat.canary.data.model.*
import com.yhchat.canary.proto.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton
import com.yhchat.canary.proto.user.OnlineDevicesResponse
import com.yhchat.canary.proto.user.edit_avatar
import com.yhchat.canary.proto.user.edit_avatar_send
import com.yhchat.canary.proto.user.edit_nickname
import com.yhchat.canary.proto.user.edit_nickname_send
import com.yhchat.canary.proto.user.get_user
import com.yhchat.canary.proto.user.info

/**
 * 用户数据仓库
 */
@Singleton
class UserRepository @Inject constructor(
    private val apiService: ApiService,
    private var tokenRepository: TokenRepository? = null,
    private var context: Context? = null
) {
    
    companion object {
        private const val TAG = "UserRepository"
        private const val CACHE_VALIDITY_DURATION = 24 * 60 * 60 * 1000L // 1天
    }
    
    // Web API 服务，用于获取用户、群聊和机器人信息
    private val webApiService = com.yhchat.canary.data.api.ApiClient.webApiService
    private val gson = Gson()

    fun setTokenRepository(tokenRepository: TokenRepository?) {
        this.tokenRepository = tokenRepository
    }
    
    fun setContext(context: Context?) {
        this.context = context
    }
    
    /**
     * 检查缓存是否有效
     */
    private fun isCacheValid(timestamp: Long): Boolean {
        return System.currentTimeMillis() - timestamp < CACHE_VALIDITY_DURATION
    }
    
    /**
     * 从缓存加载用户资料
     */
    suspend fun loadProfileFromCache(): UserProfile? = withContext(Dispatchers.IO) {
        try {
            context?.let { ctx ->
                val database = AppDatabase.getDatabase(ctx)
                val cachedData = database.cachedProfileDataDao().getCachedData()
                if (cachedData != null && isCacheValid(cachedData.timestamp)) {
                    val profile = gson.fromJson(cachedData.profileJson, UserProfile::class.java)
                    Log.d(TAG, "从缓存加载用户资料成功")
                    profile
                } else {
                    Log.d(TAG, "用户资料缓存无效或已过期")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "从缓存加载用户资料失败", e)
            null
        }
    }

    suspend fun saveUserRemarks(
        friendId: String,
        name: String,
        phone: String,
        others: List<RemarkExtraEntry>
    ): Result<Unit> {
        return try {
            val token = getToken() ?: return Result.failure(Exception("未登录"))
            val request = SaveUserRemarkRequest(
                friendId = friendId,
                name = name,
                phone = phone,
                others = RemarkInfoJsonAdapter.encode(others)
            )
            val response = apiService.saveUserRemarks(token, request)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception(body?.message ?: "备注保存失败"))
                }
            } else {
                Result.failure(Exception("备注保存失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 保存用户资料到缓存
     */
    suspend fun saveProfileToCache(profile: UserProfile) = withContext(Dispatchers.IO) {
        try {
            context?.let { ctx ->
                val database = AppDatabase.getDatabase(ctx)
                val profileJson = gson.toJson(profile)
                val cachedData = CachedProfileData(
                    id = 1,
                    profileJson = profileJson,
                    timestamp = System.currentTimeMillis()
                )
                database.cachedProfileDataDao().insertCachedData(cachedData)
                Log.d(TAG, "保存用户资料到缓存成功")
            }
        } catch (e: Exception) {
            Log.e(TAG, "保存用户资料到缓存失败", e)
        }
    }
    
    private suspend fun getToken(): String? {
        return tokenRepository?.getTokenSync()
    }
    
    suspend fun getTokenSync(): String? {
        return tokenRepository?.getTokenSync()
    }

    suspend fun getUserData(): Result<UserData> {
        return try {
            val token = getToken() ?: return Result.failure(Exception("未登录"))
            val response = apiService.getUserData(token)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1 && body.data?.data != null) {
                    Result.success(body.data.data)
                } else {
                    Result.failure(Exception(body?.msg ?: "获取个人信息失败"))
                }
            } else {
                Result.failure(Exception("获取个人信息失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveUserData(request: SaveUserDataRequest): Result<Unit> {
        return try {
            val token = getToken() ?: return Result.failure(Exception("未登录"))
            val response = apiService.saveUserData(token, request)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception(body?.message ?: "保存个人信息失败"))
                }
            } else {
                Result.failure(Exception("保存个人信息失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 获取用户信息
     */
    suspend fun getUserInfo(): Result<User> {
        return try {
            val token = getToken() ?: return Result.failure(Exception("未登录"))
            val response = apiService.getUserInfo(token)
            if (response.isSuccessful) {
                response.body()?.let { user ->
                    Result.success(user)
                } ?: Result.failure(Exception("用户信息为空"))
            } else {
                Result.failure(Exception("获取用户信息失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 获取用户个人资料（使用protobuf）
     */
    suspend fun getUserProfile(): Result<UserProfile> {
        return try {
            val token = getToken() ?: return Result.failure(Exception("未登录"))
            val response = apiService.getUserProfile(token)
            if (response.isSuccessful) {
                val responseBytes = response.body()?.bytes() ?: return Result.failure(Exception("响应为空"))
                
                // 使用protobuf解析
                val userInfoProto = info.newBuilder().mergeFrom(responseBytes).build()
                
                if (userInfoProto.status.code == 1) {
                    val userData = userInfoProto.data
                    val userProfile = UserProfile(
                        userId = userData.id,
                        nickname = userData.name,
                        avatarUrl = if (userData.avatarUrl.isNotEmpty()) userData.avatarUrl else null,
                        registerTime = 0L, // Proto中没有这个字段，设置默认值
                        registerTimeText = "", // Proto中没有这个字段，设置默认值
                        onLineDay = 0, // Proto中没有这个字段，设置默认值
                        continuousOnLineDay = 0, // Proto中没有这个字段，设置默认值
                        medals = emptyList(), // Proto中没有这个字段，设置默认值
                        isVip = if (userData.isVip != 0) userData.isVip else 0,
                        phone = if (userData.phone.isNotEmpty()) userData.phone else null,
                        email = if (userData.email.isNotEmpty()) userData.email else null,
                        coin = if (userData.coin != 0.0) userData.coin else null,
                        vipExpiredTime = if (userData.vipExpiredTime != 0L) userData.vipExpiredTime else null,
                        invitationCode = if (userData.invitationCode.isNotEmpty()) userData.invitationCode else null
                    )
                    Result.success(userProfile)
                } else {
                    Result.failure(Exception("获取用户信息失败: ${userInfoProto.status.msg}"))
                }
            } else {
                Result.failure(Exception("获取用户信息失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e("UserRepository", "获取用户个人资料异常", e)
            Result.failure(e)
        }
    }
    
    /**
     * 获取验证码
     */
    suspend fun getCaptcha(): Result<CaptchaData> {
        return try {
            println("开始获取验证码...")
            val response = apiService.getCaptcha()
            println("验证码API响应状态: ${response.code()}")
            println("验证码API响应体: ${response.body()}")
            if (response.isSuccessful) {
                val captchaResponse = response.body()
                if (captchaResponse?.code == 1 && captchaResponse.data != null) {
                    Result.success(captchaResponse.data)
                } else {
                    Result.failure(Exception(captchaResponse?.message ?: "获取验证码失败"))
                }
            } else {
                Result.failure(Exception("获取验证码失败: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            println("获取验证码异常: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * 获取邮箱验证码
     */
    suspend fun getEmailVerificationCode(email: String, captchaCode: String, captchaId: String): Result<Map<String, Any>> {
        return try {
            val request = EmailVerificationRequest(
                email = email,
                code = captchaCode,
                id = captchaId
            )
            val response = apiService.getEmailVerificationCode(request)
            if (response.isSuccessful) {
                val emailResponse = response.body()
                if (emailResponse?.get("code") == 1) {
                    Result.success(emailResponse)
                } else {
                    Result.failure(Exception(emailResponse?.get("msg")?.toString() ?: "获取邮箱验证码失败"))
                }
            } else {
                Result.failure(Exception("获取邮箱验证码失败: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 更改密码
     */
    suspend fun changePassword(request: ChangePasswordRequest): Result<Map<String, Any>> {
        return try {
            val response = apiService.changePassword(request)
            if (response.isSuccessful) {
                val changePasswordResponse = response.body()
                if (changePasswordResponse?.get("code") == 1) {
                    Result.success(changePasswordResponse)
                } else {
                    Result.failure(Exception(changePasswordResponse?.get("msg")?.toString() ?: "更改密码失败"))
                }
            } else {
                Result.failure(Exception("更改密码失败: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 获取短信验证码
     */
    suspend fun getSmsCaptcha(mobile: String, captchaCode: String, captchaId: String): Result<Boolean> {
        return try {
            val request = SmsCaptchaRequest(
                mobile = mobile,
                code = captchaCode,
                id = captchaId
            )
            val response = apiService.getSmsCaptcha(request)
            println("短信验证码API响应状态: ${response.code()}")
            println("短信验证码API响应体: ${response.body()}")
            if (response.isSuccessful) {
                val smsResponse = response.body()
                if (smsResponse?.get("code") == 1) {
                    Result.success(true)
                } else {
                    Result.failure(Exception(smsResponse?.get("msg")?.toString() ?: "获取短信验证码失败"))
                }
            } else {
                Result.failure(Exception("获取短信验证码失败: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            println("获取短信验证码异常: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * 首页搜索
     */
    suspend fun homeSearch(word: String): Result<com.yhchat.canary.data.model.SearchData> {
        return try {
            val token = getToken()
            println("搜索前获取到的token: $token")
            if (token == null) {
                println("token为空，返回未登录错误")
                return Result.failure(Exception("未登录"))
            }
            println("使用token进行搜索: $token")
            val request = com.yhchat.canary.data.model.SearchRequest(
                word = word
            )
            val response = apiService.homeSearch(token, request)
            println("搜索API响应状态: ${response.code()}")
            println("搜索API响应体: ${response.body()}")
            if (response.isSuccessful) {
                val searchResponse = response.body()
                if (searchResponse?.code == 1) {
                    Result.success(searchResponse.data)
                } else {
                    Result.failure(Exception(searchResponse?.msg ?: "搜索失败"))
                }
            } else {
                Result.failure(Exception("搜索失败: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            println("搜索异常: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * 获取置顶会话列表
     */
    suspend fun getStickyList(): Result<StickyData> {
        return try {
            val token = getToken() ?: return Result.failure(Exception("未登录"))
            val response = apiService.getStickyList(token)
            println("置顶会话API响应状态: ${response.code()}")
            println("置顶会话API响应体: ${response.body()}")
            if (response.isSuccessful) {
                val stickyResponse = response.body()
                if (stickyResponse?.code == 1 && stickyResponse.data != null) {
                    Result.success(stickyResponse.data)
                } else {
                    Result.failure(Exception(stickyResponse?.message ?: "获取置顶会话失败"))
                }
            } else {
                Result.failure(Exception("获取置顶会话失败: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            println("获取置顶会话异常: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * 添加置顶会话
     */
    suspend fun addSticky(chatId: String, chatType: Int): Result<Boolean> {
        return try {
            val token = getToken() ?: return Result.failure(Exception("未登录"))
            val request = StickyOperationRequest(chatId = chatId, chatType = chatType)
            val response = apiService.addSticky(token, request)
            println("添加置顶会话API响应状态: ${response.code()}")
            if (response.isSuccessful) {
                val apiResponse = response.body()
                if (apiResponse?.get("code") == 1) {
                    Result.success(true)
                } else {
                    Result.failure(Exception(apiResponse?.get("msg")?.toString() ?: "添加置顶会话失败"))
                }
            } else {
                Result.failure(Exception("添加置顶会话失败: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            println("添加置顶会话异常: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * 删除置顶会话
     */
    suspend fun deleteSticky(chatId: String, chatType: Int): Result<Boolean> {
        return try {
            val token = getToken() ?: return Result.failure(Exception("未登录"))
            val request = StickyOperationRequest(chatId = chatId, chatType = chatType)
            val response = apiService.deleteSticky(token, request)
            println("删除置顶会话API响应状态: ${response.code()}")
            if (response.isSuccessful) {
                val apiResponse = response.body()
                if (apiResponse?.get("code") == 1) {
                    Result.success(true)
                } else {
                    Result.failure(Exception(apiResponse?.get("msg")?.toString() ?: "删除置顶会话失败"))
                }
            } else {
                Result.failure(Exception("删除置顶会话失败: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            println("删除置顶会话异常: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * 置顶会话
     */
    suspend fun topSticky(id: Int): Result<Boolean> {
        return try {
            val token = getToken() ?: return Result.failure(Exception("未登录"))
            val request = StickyTopRequest(id = id)
            val response = apiService.topSticky(token, request)
            println("置顶会话API响应状态: ${response.code()}")
            if (response.isSuccessful) {
                val apiResponse = response.body()
                if (apiResponse?.get("code") == 1) {
                    Result.success(true)
                } else {
                    Result.failure(Exception(apiResponse?.get("msg")?.toString() ?: "置顶会话失败"))
                }
            } else {
                Result.failure(Exception("置顶会话失败: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            println("置顶会话异常: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * 修改邀请码
     */
    suspend fun changeInviteCode(code: String): Result<Boolean> {
        return try {
            val token = getToken() ?: return Result.failure(Exception("未登录"))
            val request = ChangeInviteCodeRequest(code = code)
            val response = apiService.changeInviteCode(token, request)
            if (response.isSuccessful) {
                val changeInviteCodeResponse = response.body()
                if (changeInviteCodeResponse?.get("code") == 1) {
                    Result.success(true)
                } else {
                    Result.failure(Exception(changeInviteCodeResponse?.get("msg")?.toString() ?: "修改邀请码失败"))
                }
            } else {
                Result.failure(Exception("修改邀请码失败: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 修改用户名称（使用protobuf）
     */
    suspend fun editNickname(nickname: String): Result<Boolean> {
        return try {
            val token = getToken() ?: return Result.failure(Exception("未登录"))
            
            // 构建protobuf请求
            val request = edit_nickname_send.newBuilder()
                .setName(nickname)
                .build()
            
            val requestBody = request.toByteArray()
                .toRequestBody("application/x-protobuf".toMediaType())
            
            val response = apiService.editNickname(token, requestBody)
            
            if (response.isSuccessful) {
                val responseBytes = response.body()?.bytes() ?: return Result.failure(Exception("响应为空"))
                
                // 解析protobuf响应
                val editNicknameResponse = edit_nickname.parseFrom(responseBytes)
                
                if (editNicknameResponse.status.code == 1) {
                    Result.success(true)
                } else {
                    Result.failure(Exception(editNicknameResponse.status.msg))
                }
            } else {
                Result.failure(Exception("修改用户名称失败: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e("UserRepository", "修改用户名称异常", e)
            Result.failure(e)
        }
    }

    /**
     * 获取用户主页信息
     */
    suspend fun getUserHomepage(userId: String): Result<UserHomepageInfo> {
        return try {
            val response = webApiService.getUserHomepage(userId)
            if (response.isSuccessful) {
                val userHomepageResponse = response.body()
                if (userHomepageResponse?.code == 1) {
                    Result.success(userHomepageResponse.data.user)
                } else {
                    Result.failure(Exception(userHomepageResponse?.msg ?: "获取用户信息失败"))
                }
            } else {
                Result.failure(Exception("获取用户信息失败: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 获取群聊信息
     */
    suspend fun getGroupInfo(groupId: String): Result<GroupDetail> {
        return try {
            val request = mapOf("groupId" to groupId)
            val response = webApiService.getGroupInfo(request)
            if (response.isSuccessful) {
                val groupInfoResponse = response.body()
                if (groupInfoResponse?.code == 1) {
                    val group = groupInfoResponse.data.group
                    val groupDetail = GroupDetail(
                        groupId = group.groupId,
                        name = group.name,
                        avatarUrl = group.avatarUrl,
                        introduction = group.introduction,
                        memberCount = group.headcount,
                        createBy = group.createBy,
                        directJoin = group.readHistory == 1,
                        permissionLevel = 0,
                        historyMsgEnabled = group.readHistory == 1,
                        categoryName = "",
                        categoryId = 0,
                        isPrivate = false,
                        doNotDisturb = false,
                        hideGroupMembers = false,
                        denyMembersUploadToGroupDisk = false,
                        communityId = 0,
                        communityName = "",
                        isTop = false,
                        adminIds = emptyList(),
                        ownerId = group.createBy,
                        limitedMsgType = "",
                        avatarId = group.avatarId.toLong(),
                        recommendation = null,
                        unbanTimestamp = null,
                        banReason = null
                    )
                    Result.success(groupDetail)
                } else {
                    Result.failure(Exception(groupInfoResponse?.msg ?: "获取群聊信息失败"))
                }
            } else {
                Result.failure(Exception("获取群聊信息失败: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 获取机器人信息
     */
    suspend fun getBotInfo(botId: String): Result<BotInfo> {
        return try {
            val request = mapOf("botId" to botId)
            val response = webApiService.getBotInfo(request)
            if (response.isSuccessful) {
                val botInfoResponse = response.body()
                if (botInfoResponse?.code == 1) {
                    Result.success(botInfoResponse.data.bot)
                } else {
                    Result.failure(Exception(botInfoResponse?.msg ?: "获取机器人信息失败"))
                }
            } else {
                Result.failure(Exception("获取机器人信息失败: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 添加好友/群聊/机器人
     */
    suspend fun addFriend(chatId: String, chatType: Int, remark: String = ""): Result<Boolean> {
        return try {
            val token = getToken() ?: return Result.failure(Exception("未登录"))
            val request = AddFriendRequest(
                chatId = chatId,
                chatType = chatType,
                remark = remark
            )
            val response = apiService.addFriend(token, request)
            if (response.isSuccessful) {
                val addFriendResponse = response.body()
                if (addFriendResponse?.code == 1) {
                    Result.success(true)
                } else {
                    Result.failure(Exception(addFriendResponse?.message ?: "添加失败"))
                }
            } else {
                Result.failure(Exception("添加失败: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 验证码登录
     */
    suspend fun verificationLogin(mobile: String, captcha: String, deviceId: String): Result<LoginData> {
        return try {
            val request = LoginRequest(
                mobile = mobile,
                captcha = captcha,
                deviceId = deviceId,
                platform = "yhchat canary 21.7 dev"
            )
            val response = apiService.verificationLogin(request)
            println("API响应状态: ${response.code()}")
            println("API响应体: ${response.body()}")
            if (response.isSuccessful) {
                val loginResponse = response.body()
                if (loginResponse?.code == 1 && loginResponse.data != null) {
                    Result.success(loginResponse.data)
                } else {
                    Result.failure(Exception(loginResponse?.message ?: "登录失败"))
                }
            } else {
                Result.failure(Exception("登录失败: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 邮箱登录
     */
    suspend fun emailLogin(email: String, password: String, deviceId: String): Result<LoginData> {
        return try {
            val request = LoginRequest(
                email = email,
                password = password,
                deviceId = deviceId,
                platform = "yhchat canary 21.7 dev"
            )
            val response = apiService.emailLogin(request)
            println("邮箱登录API响应状态: ${response.code()}")
            println("邮箱登录API响应体: ${response.body()}")
            if (response.isSuccessful) {
                val loginResponse = response.body()
                if (loginResponse?.code == 1 && loginResponse.data != null) {
                    Result.success(loginResponse.data)
                } else {
                    Result.failure(Exception(loginResponse?.message ?: "登录失败"))
                }
            } else {
                Result.failure(Exception("登录失败: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 退出登录
     */
    suspend fun logout(deviceId: String): Result<Boolean> {
        return try {
            val token = getToken() ?: return Result.failure(Exception("未登录"))
            val request = mapOf("deviceId" to deviceId)
            val response = apiService.logout(token, request)
            if (response.isSuccessful) {
                Result.success(true)
            } else {
                Result.failure(Exception("退出登录失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ========== 消息相关功能 ==========
    
    /**
     * 获取消息列表（按序列）
     */
    suspend fun listMessageBySeq(chatId: String, chatType: Int, msgSeq: Long = 0): Result<List<ChatMessage>> {
        return try {
            val token = getToken() ?: return Result.failure(Exception("未登录"))
            
            // 构建protobuf请求
            val requestBuilder = list_message_by_seq_send.newBuilder()
            requestBuilder.chatId = chatId
            requestBuilder.chatType = chatType.toLong()
            requestBuilder.msgSeq = msgSeq

            val requestBytes = requestBuilder.build().toByteArray()
            val requestBody = requestBytes.toRequestBody(
                "application/x-protobuf".toMediaType()
            ) as okhttp3.RequestBody
            
            val response = apiService.listMessageBySeq(token, requestBody)
            
            if (response.isSuccessful) {
                val responseBytes = response.body()?.bytes() ?: return Result.failure(Exception("响应为空"))
                val messageResponse = list_message_by_seq.parseFrom(responseBytes)
                
                if (messageResponse.status.code == 1) {
                    // 转换protobuf消息为数据类
                    val messages = messageResponse.msgList.map { protoMsg ->
                        ChatMessage(
                            msgId = protoMsg.msgId,
                            sender = MessageSender(
                                chatId = protoMsg.sender.chatId,
                                chatType = protoMsg.sender.chatType,
                                name = protoMsg.sender.name,
                                avatarUrl = protoMsg.sender.avatarUrl,
                                tagOld = protoMsg.sender.tagOldList,
                                tag = protoMsg.sender.tagList.map { tag ->
                                    MessageTag(
                                        id = tag.id,
                                        text = tag.text,
                                        color = tag.color
                                    )
                                }
                            ),
                            direction = protoMsg.direction,
                            contentType = protoMsg.contentType,
                            content = MessageContent(
                                text = protoMsg.content.text.takeIf { it.isNotEmpty() },
                                buttons = protoMsg.content.buttons.takeIf { it.isNotEmpty() },
                                imageUrl = protoMsg.content.imageUrl.takeIf { it.isNotEmpty() },
                                fileName = protoMsg.content.fileName.takeIf { it.isNotEmpty() },
                                fileUrl = protoMsg.content.fileUrl.takeIf { it.isNotEmpty() },
                                form = protoMsg.content.form.takeIf { it.isNotEmpty() },
                                quoteMsgText = protoMsg.content.quoteMsgText.takeIf { it.isNotEmpty() },
                                quoteImageUrl = protoMsg.content.quoteImageUrl.takeIf { it.isNotEmpty() },
                                quoteImageName = protoMsg.content.quoteImageName.takeIf { it.isNotEmpty() },
                                quoteVideoUrl = protoMsg.content.quoteVideoUrl.takeIf { it.isNotEmpty() },
                                quoteVideoTime = if (protoMsg.content.quoteVideoTime > 0) protoMsg.content.quoteVideoTime else null,
                                stickerUrl = protoMsg.content.stickerUrl.takeIf { it.isNotEmpty() },
                                postId = protoMsg.content.postId.takeIf { it.isNotEmpty() },
                                postTitle = protoMsg.content.postTitle.takeIf { it.isNotEmpty() },
                                postContent = protoMsg.content.postContent.takeIf { it.isNotEmpty() },
                                postContentType = protoMsg.content.postContentType.takeIf { it.isNotEmpty() },
                                expressionId = protoMsg.content.expressionId.takeIf { it.isNotEmpty() },
                                fileSize = if (protoMsg.content.fileSize > 0) protoMsg.content.fileSize.toLong() else null,
                                videoUrl = protoMsg.content.videoUrl.takeIf { it.isNotEmpty() },
                                audioUrl = protoMsg.content.audioUrl.takeIf { it.isNotEmpty() },
                                audioTime = if (protoMsg.content.audioTime > 0) protoMsg.content.audioTime.toLong() else null,
                                stickerItemId = if (protoMsg.content.stickerItemId > 0) protoMsg.content.stickerItemId.toLong() else null,
                                stickerPackId = if (protoMsg.content.stickerPackId > 0) protoMsg.content.stickerPackId.toLong() else null,
                                callText = protoMsg.content.callText.takeIf { it.isNotEmpty() },
                                callStatusText = protoMsg.content.callStatusText.takeIf { it.isNotEmpty() },
                                width = if (protoMsg.content.width > 0) protoMsg.content.width.toLong() else null,
                                height = if (protoMsg.content.height > 0) protoMsg.content.height.toLong() else null
                            ),
                            sendTime = protoMsg.sendTime.toLong(),
                            cmd = if (protoMsg.hasCmd()) {
                                MessageCmd(
                                    name = protoMsg.cmd.name,
                                    type = protoMsg.cmd.type.toInt()
                                )
                            } else null,
                            msgDeleteTime = if (protoMsg.msgDeleteTime > 0) protoMsg.msgDeleteTime.toLong() else null,
                            quoteMsgId = protoMsg.quoteMsgId.takeIf { (it as? String)?.isNotEmpty() == true },
                            msgSeq = if (protoMsg.msgSeq > 0) protoMsg.msgSeq.toLong() else null,
                            editTime = if (protoMsg.editTime > 0) protoMsg.editTime.toLong() else null
                        )
                    }
                    Result.success(messages)
                } else {
                    Result.failure(Exception("获取消息失败: ${messageResponse.status.msg}"))
                }
            } else {
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            println("获取消息列表异常: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    /**
     * 发送文本消息
     */
    suspend fun sendTextMessage(chatId: String, chatType: Int, text: String): Result<Boolean> {
        return try {
            val token = getToken() ?: return Result.failure(Exception("未登录"))
            
            // 生成消息ID
            val msgId = java.util.UUID.randomUUID().toString()
            
            // 构建protobuf请求
            val contentBuilder = com.yhchat.canary.proto.send_message_send.Content.newBuilder()
            contentBuilder.text = text

            val requestBuilder = com.yhchat.canary.proto.send_message_send.newBuilder()
            requestBuilder.msgId = msgId
            requestBuilder.chatId = chatId
            requestBuilder.chatType = chatType.toLong()
            requestBuilder.content = contentBuilder.build()
            requestBuilder.contentType = 1 // 文本消息

            val requestBytes = requestBuilder.build().toByteArray()
            val requestBody = requestBytes.toRequestBody(
                "application/x-protobuf".toMediaType()
            ) as okhttp3.RequestBody

            val response = apiService.sendMessage(token, requestBody)

            if (response.isSuccessful) {
                val responseBytes = response.body()?.bytes() ?: return Result.failure(Exception("响应为空"))
                val sendResponse = com.yhchat.canary.proto.send_message.parseFrom(responseBytes)
                
                if (sendResponse.status.code == 1) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("发送消息失败: ${sendResponse.status.msg}"))
                }
            } else {
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            println("发送消息异常: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    /**
     * 获取在线设备列表
     */
    suspend fun getOnlineDevices(): Result<List<com.yhchat.canary.data.model.DeviceInfo>> {
        return try {
            val token = getToken() ?: return Result.failure(Exception("未登录"))
            val response = apiService.getOnlineDevices(token)
            
            if (response.isSuccessful) {
                val responseBody = response.body()
                if (responseBody != null) {
                    // 解析 Protobuf 数据
                    val bytes = responseBody.bytes()
                    val onlineDevicesResponse = OnlineDevicesResponse.parseFrom(bytes)
                    
                    // 检查响应状态
                    if (onlineDevicesResponse.status.code == 1) {
                        // 转换为数据模型
                        val devices = onlineDevicesResponse.devicesList.map { protoDevice ->
                            com.yhchat.canary.data.model.DeviceInfo(
                                userId = protoDevice.userId,
                                platform = protoDevice.platform,
                                deviceId = protoDevice.deviceId,
                                loginTimestamp = protoDevice.loginTimestamp
                            )
                        }
                        Result.success(devices)
                    } else {
                        Result.failure(Exception("API返回错误: ${onlineDevicesResponse.status.msg}"))
                    }
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
    
    /**
     * 获取分享信息
     */
    suspend fun getShareInfo(key: String, ts: String): Result<ShareInfo> {
        return try {
            val token = getToken() ?: return Result.failure(Exception("未登录"))
            val request = ShareInfoRequest(key = key, ts = ts)
            val response = apiService.getShareInfo(token, request)
            
            if (response.isSuccessful) {
                response.body()?.let { body ->
                    if (body.code == 1 && body.data?.share != null) {
                        Result.success(body.data.share)
                    } else {
                        Result.failure(Exception(body.message ?: "获取分享信息失败"))
                    }
                } ?: Result.failure(Exception("响应为空"))
            } else {
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e("UserRepository", "获取分享信息异常", e)
            Result.failure(e)
        }
    }
    
    /**
     * 修改用户头像
     */
    suspend fun editAvatar(avatarUrl: String): Result<Boolean> {
        return try {
            val token = getToken() ?: return Result.failure(Exception("未登录"))
            
            // 构建ProtoBuf请求
            val requestBuilder = edit_avatar_send.newBuilder()
            requestBuilder.url = avatarUrl
            
            val requestBytes = requestBuilder.build().toByteArray()
            val requestBody = requestBytes.toRequestBody(
                "application/x-protobuf".toMediaType()
            )
            
            val response = apiService.editAvatar(token, requestBody)
            
            if (response.isSuccessful) {
                val responseBytes = response.body()?.bytes() ?: return Result.failure(Exception("响应为空"))
                val editResponse = edit_avatar.parseFrom(responseBytes)
                
                if (editResponse.status.code == 1) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("修改头像失败: ${editResponse.status.msg}"))
                }
            } else {
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e("UserRepository", "修改头像异常", e)
            Result.failure(e)
        }
    }
    
    /**
     * 获取用户详细信息
     */
    suspend fun getUserDetail(token: String, requestBody: RequestBody): Result<UserDetail> {
        return try {
            val response = apiService.getUserDetail(token, requestBody)
            
            if (response.isSuccessful) {
                val responseBytes = response.body()?.bytes() ?: return Result.failure(Exception("响应为空"))
                val getUserResponse = get_user.parseFrom(responseBytes)
                
                if (getUserResponse.status.code == 1) {
                    val data = getUserResponse.data
                    
                    // 解析备注信息
                    val remarkInfo = if (data.hasRemarkInfo()) {
                        RemarkInfo(
                            remarkName = data.remarkInfo.remarkName,
                            phoneNumber = data.remarkInfo.phoneNumber,
                            extraRemark = data.remarkInfo.extraRemark,
                            extraRemarks = RemarkInfoJsonAdapter.parse(data.remarkInfo.extraRemark)
                        )
                    } else null
                    
                    // 解析个人资料信息
                    val profileInfo = if (data.hasProfileInfo()) {
                        ProfileInfo(
                            lastActiveTime = data.profileInfo.lastActiveTime,
                            introduction = data.profileInfo.introduction,
                            gender = data.profileInfo.gender,
                            birthday = data.profileInfo.birthday,
                            city = data.profileInfo.city,
                            district = data.profileInfo.district,
                            address = data.profileInfo.address
                        )
                    } else null
                    
                    val userDetail = UserDetail(
                        id = data.id,
                        name = data.name,
                        nameId = data.nameId,
                        avatarUrl = data.avatarUrl,
                        avatarId = data.avatarId,
                        medalList = data.medalList.map { 
                            MedalInfo(it.id, it.name, it.sort)
                        },
                        registerTime = data.registerTime,
                        banTime = data.banTime,
                        onlineDay = data.onlineDay,
                        continuousOnlineDay = data.continuousOnlineDay,
                        isVip = data.isVip,
                        vipExpiredTime = data.vipExpiredTime,
                        remarkInfo = remarkInfo,
                        profileInfo = profileInfo,
                        ipGeo = data.ipGeo
                    )
                    Result.success(userDetail)
                } else {
                    Result.failure(Exception(getUserResponse.status.msg))
                }
            } else {
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun deleteFriend(chatId: String, chatType: Int): Result<Boolean> {
        return try {
            val token = getToken() ?: return Result.failure(Exception("未登录"))
            val request = DeleteFriendRequest(chatId = chatId, chatType = chatType)
            val response = apiService.deleteFriend(token, request)
            
            if (response.isSuccessful) {
                response.body()?.let { body ->
                    if (body.code == 1) {
                        Result.success(true)
                    } else {
                        Result.failure(Exception(body.message ?: "删除失败"))
                    }
                } ?: Result.failure(Exception("响应为空"))
            } else {
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e("UserRepository", "删除好友/群聊/机器人异常", e)
            Result.failure(e)
        }
    }
}
