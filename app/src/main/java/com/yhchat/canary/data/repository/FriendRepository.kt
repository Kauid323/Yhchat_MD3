package com.yhchat.canary.data.repository

import android.util.Log
import com.yhchat.canary.data.api.ApiService
import com.yhchat.canary.data.api.NoNotifyRequest
import com.yhchat.canary.data.model.*
import com.yhchat.canary.proto.*
import com.yhchat.canary.proto.friend.request_list
import com.yhchat.canary.proto.user.address_book_list
import com.yhchat.canary.proto.user.address_book_list_send
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

/**
 * 好友数据仓库
 */
class FriendRepository @Inject constructor(
    private val apiService: ApiService,
    private val tokenRepository: TokenRepository
) {
    
    private val tag = "FriendRepository"

    /** 日志里不要输出完整 token，只保留前 6 位。 */
    private fun maskToken(token: String?): String {
        if (token.isNullOrEmpty()) return "empty"
        return if (token.length <= 6) "${token.length} chars" else "${token.take(6)}***(${token.length} chars)"
    }

    private fun logRequest(api: String, detail: String) {
        Log.d(tag, "📤 [$api] 请求 -> $detail")
    }

    private fun logResponse(api: String, detail: String) {
        Log.d(tag, "📥 [$api] 响应 <- $detail")
    }

    private fun logFailure(api: String, detail: String, error: Throwable? = null) {
        if (error != null) {
            Log.e(tag, "❌ [$api] $detail", error)
        } else {
            Log.e(tag, "❌ [$api] $detail")
        }
    }

    /**
     * 申请添加好友/群聊/机器人
     */
    suspend fun applyFriend(
        token: String,
        chatId: String,
        chatType: Int, // 1-用户，2-群聊，3-机器人
        remark: String
    ): ApiStatus {
        return try {
            val request = AddFriendRequest(
                chatId = chatId,
                chatType = chatType,
                remark = remark
            )
            logRequest("applyFriend", "token=${maskToken(token)}, chatId=$chatId, chatType=$chatType, remark=$remark")
            val response = apiService.addFriend(token, request)
            logResponse("applyFriend", "HTTP ${response.code()}, body=${response.body()}")
            if (response.isSuccessful) {
                response.body() ?: ApiStatus(code = 0, message = "响应为空")
            } else {
                logFailure("applyFriend", "HTTP ${response.code()}, errorBody=${response.errorBody()?.string()}")
                ApiStatus(code = 0, message = "网络请求失败: ${response.code()}")
            }
        } catch (e: Exception) {
            logFailure("applyFriend", "请求异常 chatId=$chatId", e)
            ApiStatus(code = 0, message = "请求失败: ${e.message}")
        }
    }
    
    /**
     * 获取所有聊天对象（通讯录）
     */
    suspend fun getAddressBookList(): Result<address_book_list> {
        return try {
            val tokenFlow = tokenRepository.getToken()
            val token = tokenFlow.first()?.token
            if (token.isNullOrEmpty()) {
                logFailure("getAddressBookList", "Token 为空，终止请求")
                return Result.failure(Exception("用户未登录"))
            }
            
            Log.d(tag, "📤 ========== 获取通讯录 ==========")
            
            // 构建protobuf请求
            val request = address_book_list_send.newBuilder()
                .setNumber("通讯录请求")
                .build()
            
            val requestBody = request.toByteArray().toRequestBody("application/x-protobuf".toMediaType())
            
            logRequest(
                "getAddressBookList",
                "token=${maskToken(token)}, contentType=application/x-protobuf, proto=$request, bytes=${requestBody.contentLength()}"
            )
            
            val response = apiService.getAddressBookList(token, requestBody)
            
            logResponse(
                "getAddressBookList",
                "HTTP ${response.code()}, message=${response.message()}, contentType=${response.headers()["Content-Type"]}"
            )
            
            if (response.isSuccessful) {
                response.body()?.let { responseBody ->
                    val bytes = responseBody.bytes()
                    logResponse("getAddressBookList", "响应体 ${bytes.size} bytes")
                    val addressBook = address_book_list.parseFrom(bytes)
                    
                    Log.d(tag, "📥 响应状态码: ${addressBook.status.code}")
                    Log.d(tag, "📥 响应消息: ${addressBook.status.msg}")
                    Log.d(tag, "📥 分组数量: ${addressBook.dataCount}")
                    
                    // 详细日志：打印每个分组的数据
                    addressBook.dataList.forEachIndexed { index, group ->
                        Log.d(tag, "📥 分组[$index] 名称: ${group.listName}, 成员数量: ${group.dataCount}")
                        group.dataList.forEachIndexed { itemIndex, item ->
                            Log.d(tag, "  ↳ 成员[$itemIndex] ID: ${item.chatId}, 名称: ${item.name}")
                        }
                    }
                    
                    if (addressBook.status.code == 1) {
                        Log.d(tag, "✅ ========== 通讯录获取成功！ ==========")
                        Result.success(addressBook)
                    } else {
                        logFailure("getAddressBookList", "业务失败: ${addressBook.status.msg}")
                        Result.failure(Exception(addressBook.status.msg))
                    }
                } ?: run {
                    logFailure("getAddressBookList", "HTTP ${response.code()} 但响应体为空")
                    Result.failure(Exception("响应体为空"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                logFailure("getAddressBookList", "HTTP错误: ${response.code()}, 错误详情: $errorBody")
                Result.failure(Exception("获取失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            logFailure("getAddressBookList", "获取通讯录异常", e)
            Result.failure(e)
        }
    }

    suspend fun getFriendRequestList(): Result<request_list> {
        return try {
            val token = tokenRepository.getToken().first()?.token
            if (token.isNullOrEmpty()) {
                logFailure("getFriendRequestList", "Token 为空，终止请求")
                return Result.failure(Exception("用户未登录"))
            }

            val requestBody = ByteArray(0).toRequestBody("application/x-protobuf".toMediaType())
            logRequest(
                "getFriendRequestList",
                "token=${maskToken(token)}, contentType=application/x-protobuf, bytes=${requestBody.contentLength()}"
            )
            val response = apiService.getFriendRequestList(token, requestBody)
            logResponse("getFriendRequestList", "HTTP ${response.code()}, message=${response.message()}")

            if (response.isSuccessful) {
                response.body()?.let { responseBody ->
                    val bytes = responseBody.bytes()
                    logResponse("getFriendRequestList", "响应体 ${bytes.size} bytes")
                    val data = request_list.parseFrom(bytes)
                    Log.d(
                        tag,
                        "📥 请求/邀请: status=${data.status.code}, msg=${data.status.msg}, total=${data.total}, pending=${data.pending}, count=${data.requestsCount}"
                    )
                    if (data.status.code == 1) {
                        Result.success(data)
                    } else {
                        logFailure("getFriendRequestList", "业务失败: ${data.status.msg}")
                        Result.failure(Exception(data.status.msg))
                    }
                } ?: run {
                    logFailure("getFriendRequestList", "HTTP ${response.code()} 但响应体为空")
                    Result.failure(Exception("响应体为空"))
                }
            } else {
                logFailure(
                    "getFriendRequestList",
                    "HTTP错误: ${response.code()}, 错误详情: ${response.errorBody()?.string()}"
                )
                Result.failure(Exception("获取失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            logFailure("getFriendRequestList", "获取请求列表异常", e)
            Result.failure(e)
        }
    }

    suspend fun agreeApply(
        id: Int,
        agree: Int = 1,
        usesGroupAgreeInvite: Boolean = false
    ): Result<BaseResponse> {
        return try {
            val token = tokenRepository.getToken().first()?.token
            if (token.isNullOrEmpty()) {
                logFailure("agreeApply", "Token 为空，终止请求")
                return Result.failure(Exception("用户未登录"))
            }

            val api = if (usesGroupAgreeInvite) "agreeInvite" else "agreeApply"
            val request = AgreeApplyRequest(id = id, agree = agree)
            logRequest(api, "token=${maskToken(token)}, id=$id, agree=$agree, body=$request")
            val response = if (usesGroupAgreeInvite) {
                apiService.agreeInvite(token, request)
            } else {
                apiService.agreeApply(token, request)
            }
            logResponse(api, "HTTP ${response.code()}, body=${response.body()}")
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Result.success(body)
                } else {
                    logFailure(api, "业务失败: ${body?.message}")
                    Result.failure(Exception(body?.message ?: "处理失败"))
                }
            } else {
                logFailure(api, "HTTP错误: ${response.code()}, 错误详情: ${response.errorBody()?.string()}")
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            logFailure("agreeApply", "处理请求异常 id=$id", e)
            Result.failure(e)
        }
    }

    suspend fun setNoNotify(
        chatId: String,
        noNotify: Int
    ): Result<ApiStatus> {
        return try {
            val token = tokenRepository.getToken().first()?.token
            if (token.isNullOrEmpty()) {
                logFailure("setNoNotify", "Token 为空，终止请求")
                return Result.failure(Exception("用户未登录"))
            }

            val request = NoNotifyRequest(chatId = chatId, noNotify = noNotify)
            logRequest("setNoNotify", "token=${maskToken(token)}, chatId=$chatId, noNotify=$noNotify")
            val response = apiService.setNoNotify(token, request)
            logResponse("setNoNotify", "HTTP ${response.code()}, body=${response.body()}")
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Result.success(body)
                } else {
                    logFailure("setNoNotify", "业务失败: ${body?.message}")
                    Result.failure(Exception(body?.message ?: "设置失败"))
                }
            } else {
                logFailure("setNoNotify", "HTTP错误: ${response.code()}, 错误详情: ${response.errorBody()?.string()}")
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            logFailure("setNoNotify", "设置免打扰异常 chatId=$chatId", e)
            Result.failure(e)
        }
    }
}
