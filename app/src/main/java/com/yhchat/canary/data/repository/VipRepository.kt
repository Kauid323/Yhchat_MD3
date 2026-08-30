package com.yhchat.canary.data.repository

import android.util.Log
import com.yhchat.canary.data.api.ApiService
import com.yhchat.canary.data.model.VipBenefit
import com.yhchat.canary.data.model.VipProductListData
import com.yhchat.canary.data.model.VipProductListRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * VIP会员仓库
 */
@Singleton
class VipRepository @Inject constructor(
    private val apiService: ApiService,
    private val tokenRepository: TokenRepository
) {
    companion object {
        private const val TAG = "VipRepository"
    }

    /**
     * 获取VIP价格列表
     */
    suspend fun getVipProductList(
        platform: String = "android"
    ): Result<VipProductListData> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = tokenRepository.getTokenSync()
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "Token为空")
                return@withContext Result.failure(Exception("未登录"))
            }

            val request = VipProductListRequest(platform = platform)
            val response = apiService.getVipProductList(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Log.d(TAG, "✅ 获取VIP价格列表成功: ${body.data.list.size}个套餐")
                    Result.success(body.data)
                } else {
                    val error = "获取VIP价格列表失败: ${body?.msg}"
                    Log.e(TAG, error)
                    Result.failure(Exception(error))
                }
            } else {
                val error = "获取VIP价格列表失败: ${response.code()}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 获取VIP价格列表异常", e)
            Result.failure(e)
        }
    }

    /**
     * 获取VIP特权列表
     */
    suspend fun getVipBenefitsList(): Result<List<VipBenefit>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = tokenRepository.getTokenSync()
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "Token为空")
                return@withContext Result.failure(Exception("未登录"))
            }

            val response = apiService.getVipBenefitsList(token)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Log.d(TAG, "✅ 获取VIP特权列表成功: ${body.data.list.size}个特权")
                    Result.success(body.data.list.sortedBy { it.sort })
                } else {
                    val error = "获取VIP特权列表失败: ${body?.msg}"
                    Log.e(TAG, error)
                    Result.failure(Exception(error))
                }
            } else {
                val error = "获取VIP特权列表失败: ${response.code()}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 获取VIP特权列表异常", e)
            Result.failure(e)
        }
    }
}

