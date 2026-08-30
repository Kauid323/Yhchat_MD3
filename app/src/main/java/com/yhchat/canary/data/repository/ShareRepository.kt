package com.yhchat.canary.data.repository

import android.util.Log
import com.yhchat.canary.data.api.ApiService
import com.yhchat.canary.data.model.CreateShareRequest
import com.yhchat.canary.data.model.ShareData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 分享仓库
 */
@Singleton
class ShareRepository @Inject constructor(
    private val apiService: ApiService,
    private val tokenRepository: TokenRepository
) {
    companion object {
        private const val TAG = "ShareRepository"
    }

    /**
     * 创建分享链接
     */
    suspend fun createShareLink(
        chatId: String,
        chatType: Int,
        chatName: String
    ): Result<ShareData> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = tokenRepository.getTokenSync()
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "Token为空")
                return@withContext Result.failure(Exception("未登录"))
            }

            val request = CreateShareRequest(
                chatId = chatId,
                chatType = chatType,
                chatName = chatName
            )
            
            Log.d(TAG, "创建分享链接: chatId=$chatId, chatType=$chatType")
            
            val response = apiService.createShareLink(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Log.d(TAG, "✅ 分享链接创建成功: ${body.data.getFullShareUrl()}")
                    Result.success(body.data)
                } else {
                    val error = "创建分享链接失败: ${body?.msg}"
                    Log.e(TAG, error)
                    Result.failure(Exception(error))
                }
            } else {
                val error = "创建分享链接失败: ${response.code()}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 创建分享链接异常", e)
            Result.failure(e)
        }
    }
}

