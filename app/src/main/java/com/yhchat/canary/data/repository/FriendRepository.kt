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
            val response = apiService.addFriend(token, request)
            if (response.isSuccessful) {
                response.body() ?: ApiStatus(code = 0, message = "响应为空")
            } else {
                ApiStatus(code = 0, message = "网络请求失败: ${response.code()}")
            }
        } catch (e: Exception) {
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
                Log.e(tag, "❌ Token为空")
                return Result.failure(Exception("用户未登录"))
            }
            
            // Log.d(tag, "📤 ========== 获取通讯录 ==========")
            
            // 构建protobuf请求
            val request = address_book_list_send.newBuilder()
                .setNumber("通讯录请求")
                .build()
            
            val requestBody = request.toByteArray().toRequestBody("application/x-protobuf".toMediaType())
            
            // Log.d(tag, "📤 发送请求...")
            
            val response = apiService.getAddressBookList(token, requestBody)
            
            // Log.d(tag, "📥 服务器响应码: ${response.code()}")
            
            if (response.isSuccessful) {
                response.body()?.let { responseBody ->
                    val bytes = responseBody.bytes()
                    val addressBook = address_book_list.parseFrom(bytes)
                    
                    // Log.d(tag, "📥 响应状态码: ${addressBook.status.code}")
                    // Log.d(tag, "📥 响应消息: ${addressBook.status.msg}")
                    // Log.d(tag, "📥 分组数量: ${addressBook.dataCount}")
                    
                    // 详细日志：打印每个分组的数据
                    addressBook.dataList.forEachIndexed { index, group ->
                        // Log.d(tag, "📥 分组[$index] 名称: ${group.listName}, 成员数量: ${group.dataCount}")
                        group.dataList.forEachIndexed { itemIndex, item ->
                            // Log.d(tag, "  ↳ 成员[$itemIndex] ID: ${item.chatId}, 名称: ${item.name}")
                        }
                    }
                    
                    if (addressBook.status.code == 1) {
                        // Log.d(tag, "✅ ========== 通讯录获取成功！ ==========")
                        Result.success(addressBook)
                    } else {
                        Log.e(tag, "❌ 获取失败: ${addressBook.status.msg}")
                        Result.failure(Exception(addressBook.status.msg))
                    }
                } ?: Result.failure(Exception("响应体为空"))
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(tag, "❌ HTTP错误: ${response.code()}, 错误详情: $errorBody")
                Result.failure(Exception("获取失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(tag, "❌ 获取通讯录异常", e)
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun getFriendRequestList(): Result<request_list> {
        return try {
            val token = tokenRepository.getToken().first()?.token
            if (token.isNullOrEmpty()) {
                return Result.failure(Exception("用户未登录"))
            }

            val requestBody = ByteArray(0).toRequestBody("application/x-protobuf".toMediaType())
            val response = apiService.getFriendRequestList(token, requestBody)

            if (response.isSuccessful) {
                response.body()?.let { responseBody ->
                    val bytes = responseBody.bytes()
                    val data = request_list.parseFrom(bytes)
                    if (data.status.code == 1) {
                        Result.success(data)
                    } else {
                        Result.failure(Exception(data.status.msg))
                    }
                } ?: Result.failure(Exception("响应体为空"))
            } else {
                Result.failure(Exception("获取失败: ${response.code()}"))
            }
        } catch (e: Exception) {
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
                return Result.failure(Exception("用户未登录"))
            }

            val request = AgreeApplyRequest(id = id, agree = agree)
            val response = if (usesGroupAgreeInvite) {
                apiService.agreeInvite(token, request)
            } else {
                apiService.agreeApply(token, request)
            }
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Result.success(body)
                } else {
                    Result.failure(Exception(body?.message ?: "处理失败"))
                }
            } else {
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
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
                return Result.failure(Exception("用户未登录"))
            }

            val response = apiService.setNoNotify(token, NoNotifyRequest(chatId = chatId, noNotify = noNotify))
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Result.success(body)
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
}
