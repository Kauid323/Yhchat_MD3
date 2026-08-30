package com.yhchat.canary.data.repository

import android.util.Log
import com.yhchat.canary.data.api.ApiService
import com.yhchat.canary.data.model.Expression
import com.yhchat.canary.data.model.ExpressionActionRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 表情包仓库
 */
@Singleton
class ExpressionRepository @Inject constructor(
    private val apiService: ApiService,
    private val tokenRepository: TokenRepository
) {
    companion object {
        private const val TAG = "ExpressionRepository"
    }

    /**
     * 获取个人表情收藏列表
     */
    suspend fun getExpressionList(): Result<List<Expression>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = tokenRepository.getTokenSync()
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "Token为空")
                return@withContext Result.failure(Exception("未登录"))
            }

            val response = apiService.getExpressionList(token)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Log.d(TAG, "✅ 获取表情列表成功: ${body.data.expression.size}个表情")
                    Result.success(body.data.expression)
                } else {
                    val error = "获取表情列表失败: ${response.code()}"
                    Log.e(TAG, error)
                    Result.failure(Exception(error))
                }
            } else {
                val error = "获取表情列表失败: ${response.code()}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 获取表情列表异常", e)
            Result.failure(e)
        }
    }

    /**
     * 添加图片到个人表情收藏
     */
    suspend fun addExpression(imageUrl: String): Result<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = tokenRepository.getTokenSync()
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "Token为空")
                return@withContext Result.failure(Exception("未登录"))
            }

            val request = ExpressionActionRequest(url = imageUrl)
            val response = apiService.addExpression(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && (body.code == 1 || body.code == 200)) {
                    Log.d(TAG, "✅ 添加表情成功 (code: ${body.code})")
                    Result.success(true)
                } else {
                    val error = "添加表情失败: code=${body?.code}, msg=${body?.message ?: "未知错误"}"
                    Log.e(TAG, error)
                    Result.failure(Exception(error))
                }
            } else {
                val error = "添加表情失败: HTTP ${response.code()}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 添加表情异常", e)
            Result.failure(e)
        }
    }

    /**
     * 删除个人表情收藏中的表情
     */
    suspend fun deleteExpression(expressionId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = tokenRepository.getTokenSync()
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "Token为空")
                return@withContext Result.failure(Exception("未登录"))
            }

            val request = ExpressionActionRequest(id = expressionId)
            val response = apiService.deleteExpression(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && (body.code == 1 || body.code == 200)) {
                    Log.d(TAG, "✅ 删除表情成功 (code: ${body.code})")
                    Result.success(true)
                } else {
                    val error = "删除表情失败: code=${body?.code}, msg=${body?.message ?: "未知错误"}"
                    Log.e(TAG, error)
                    Result.failure(Exception(error))
                }
            } else {
                val error = "删除表情失败: HTTP ${response.code()}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 删除表情异常", e)
            Result.failure(e)
        }
    }

    /**
     * 置顶个人表情收藏中的表情
     */
    suspend fun topExpression(expressionId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = tokenRepository.getTokenSync()
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "Token为空")
                return@withContext Result.failure(Exception("未登录"))
            }

            val request = ExpressionActionRequest(id = expressionId)
            val response = apiService.topExpression(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && (body.code == 1 || body.code == 200)) {
                    Log.d(TAG, "✅ 置顶表情成功 (code: ${body.code})")
                    Result.success(true)
                } else {
                    val error = "置顶表情失败: code=${body?.code}, msg=${body?.message ?: "未知错误"}"
                    Log.e(TAG, error)
                    Result.failure(Exception(error))
                }
            } else {
                val error = "置顶表情失败: HTTP ${response.code()}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 置顶表情异常", e)
            Result.failure(e)
        }
    }

    /**
     * 添加已有表情包
     */
    suspend fun addExistingExpression(expressionId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = tokenRepository.getTokenSync()
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "Token为空")
                return@withContext Result.failure(Exception("未登录"))
            }

            val request = ExpressionActionRequest(id = expressionId)
            val response = apiService.addExistingExpression(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && (body.code == 1 || body.code == 200)) {
                    Log.d(TAG, "✅ 添加已有表情成功 (code: ${body.code})")
                    Result.success(true)
                } else {
                    val error = "添加已有表情失败: code=${body?.code}, msg=${body?.message ?: "未知错误"}"
                    Log.e(TAG, error)
                    Result.failure(Exception(error))
                }
            } else {
                val error = "添加已有表情失败: HTTP ${response.code()}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 添加已有表情异常", e)
            Result.failure(e)
        }
    }
}

