package com.yhchat.canary.data.repository

import android.util.Log
import com.yhchat.canary.data.api.ApiService
import com.yhchat.canary.data.model.GoldCoinRecord
import com.yhchat.canary.data.model.GoldCoinRecordRequest
import com.yhchat.canary.data.model.MyTaskInfo
import com.yhchat.canary.data.model.Product
import com.yhchat.canary.data.model.ProductDetailRequest
import com.yhchat.canary.data.model.ProductListData
import com.yhchat.canary.data.model.ProductListRequest
import com.yhchat.canary.data.model.PurchaseProductRequest
import com.yhchat.canary.data.model.RewardRecord
import com.yhchat.canary.data.model.RewardRecordRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 金币系统仓库
 */
@Singleton
class CoinRepository @Inject constructor(
    private val apiService: ApiService,
    private val tokenRepository: TokenRepository
) {
    companion object {
        private const val TAG = "CoinRepository"
    }

    /**
     * 获取商品列表
     */
    suspend fun getProductList(
        size: Int = 100,
        page: Int = 1
    ): Result<ProductListData> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = tokenRepository.getTokenSync()
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "Token为空")
                return@withContext Result.failure(Exception("未登录"))
            }

            val request = ProductListRequest(size = size, page = page)
            val response = apiService.getProductList(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Log.d(TAG, "✅ 获取商品列表成功: ${body.data.list.size}个商品")
                    Result.success(body.data)
                } else {
                    val error = "获取商品列表失败: ${body?.msg}"
                    Log.e(TAG, error)
                    Result.failure(Exception(error))
                }
            } else {
                val error = "获取商品列表失败: ${response.code()}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 获取商品列表异常", e)
            Result.failure(e)
        }
    }

    /**
     * 获取我的金币任务信息
     */
    suspend fun getMyTaskInfo(): Result<MyTaskInfo> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = tokenRepository.getTokenSync()
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "Token为空")
                return@withContext Result.failure(Exception("未登录"))
            }

            val response = apiService.getMyTaskInfo(token)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Log.d(TAG, "✅ 获取金币任务信息成功")
                    Result.success(body.data)
                } else {
                    val error = "获取金币任务信息失败: ${body?.msg}"
                    Log.e(TAG, error)
                    Result.failure(Exception(error))
                }
            } else {
                val error = "获取金币任务信息失败: ${response.code()}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 获取金币任务信息异常", e)
            Result.failure(e)
        }
    }

    /**
     * 获取商品详情
     */
    suspend fun getProductDetail(productId: Long): Result<Product> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = tokenRepository.getTokenSync()
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "Token为空")
                return@withContext Result.failure(Exception("未登录"))
            }

            val request = ProductDetailRequest(id = productId)
            val response = apiService.getProductDetail(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Log.d(TAG, "✅ 获取商品详情成功")
                    Result.success(body.data.product)
                } else {
                    val error = "获取商品详情失败: ${body?.msg}"
                    Log.e(TAG, error)
                    Result.failure(Exception(error))
                }
            } else {
                val error = "获取商品详情失败: ${response.code()}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 获取商品详情异常", e)
            Result.failure(e)
        }
    }
    
    /**
     * 购买商品
     */
    suspend fun purchaseProduct(productId: Int, price: Int): Result<Int> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = tokenRepository.getTokenSync()
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "Token为空")
                return@withContext Result.failure(Exception("未登录"))
            }

            val request = PurchaseProductRequest(productId = productId, price = price)
            val response = apiService.purchaseProduct(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Log.d(TAG, "✅ 购买商品成功: 订单ID=${body.data.id}")
                    Result.success(body.data.id)
                } else {
                    val error = body?.msg ?: "购买失败"
                    Log.e(TAG, error)
                    Result.failure(Exception(error))
                }
            } else {
                val error = "购买失败: ${response.code()}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 购买商品异常", e)
            Result.failure(e)
        }
    }
    
    /**
     * 获取金币增减记录
     */
    suspend fun getCoinIncreaseDecreaseRecord(
        size: Int = 20,
        page: Int = 1
    ): Result<List<GoldCoinRecord>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = tokenRepository.getTokenSync()
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "Token为空")
                return@withContext Result.failure(Exception("未登录"))
            }

            val request = GoldCoinRecordRequest(size = size, page = page)
            val response = apiService.getGoldCoinIncreaseDecreaseRecord(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Log.d(TAG, "✅ 获取金币增减记录成功: ${body.data.goldCoinRecord.size}条记录")
                    Result.success(body.data.goldCoinRecord)
                } else {
                    val error = "获取金币增减记录失败: ${body?.msg}"
                    Log.e(TAG, error)
                    Result.failure(Exception(error))
                }
            } else {
                val error = "获取金币增减记录失败: ${response.code()}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 获取金币增减记录异常", e)
            Result.failure(e)
        }
    }
    
    /**
     * 获取打赏记录（文章/评论）
     */
    suspend fun getRewardRecord(
        type: String,  // "post" 或 "comment"
        size: Int = 20,
        page: Int = 1
    ): Result<List<RewardRecord>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = tokenRepository.getTokenSync()
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "Token为空")
                return@withContext Result.failure(Exception("未登录"))
            }

            val request = RewardRecordRequest(type = type, size = size, page = page)
            val response = apiService.getRewardRecord(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Log.d(TAG, "✅ 获取${if (type == "post") "文章" else "评论"}打赏记录成功: ${body.data.rewards.size}条记录")
                    Result.success(body.data.rewards)
                } else {
                    val error = "获取打赏记录失败: ${body?.msg}"
                    Log.e(TAG, error)
                    Result.failure(Exception(error))
                }
            } else {
                val error = "获取打赏记录失败: ${response.code()}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 获取打赏记录异常", e)
            Result.failure(e)
        }
    }
}

