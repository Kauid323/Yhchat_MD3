package com.yhchat.canary.data.repository

import android.util.Log
import com.yhchat.canary.data.api.ApiService
import com.yhchat.canary.data.model.ReportRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 举报仓库
 */
@Singleton
class ReportRepository @Inject constructor(
    private val apiService: ApiService,
    private val tokenRepository: TokenRepository
) {
    companion object {
        private const val TAG = "ReportRepository"
    }

    /**
     * 提交举报
     */
    suspend fun submitReport(
        chatId: String,
        chatType: Int,
        chatName: String,
        content: String,
        imageUrl: String = "",
        reason: String = ""
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = tokenRepository.getTokenSync()
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "Token为空")
                return@withContext Result.failure(Exception("未登录"))
            }

            val request = ReportRequest(
                chatId = chatId,
                chatType = chatType,
                chatName = chatName,
                content = content,
                url = imageUrl,
                reason = reason
            )
            
            Log.d(TAG, "提交举报: chatId=$chatId, chatType=$chatType, content=$content, reason=$reason")
            
            val response = apiService.submitReport(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Log.d(TAG, "✅ 举报提交成功")
                    Result.success(true)
                } else {
                    val error = "举报提交失败: ${body?.message ?: "未知错误"}"
                    Log.e(TAG, error)
                    Result.failure(Exception(error))
                }
            } else {
                val error = "举报提交失败: ${response.code()}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 举报提交异常", e)
            Result.failure(e)
        }
    }
}

