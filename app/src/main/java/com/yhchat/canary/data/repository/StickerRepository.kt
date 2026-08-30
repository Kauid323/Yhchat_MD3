package com.yhchat.canary.data.repository

import android.util.Log
import com.yhchat.canary.data.api.ApiService
import com.yhchat.canary.data.model.StickerPack
import com.yhchat.canary.data.model.StickerPackActionRequest
import com.yhchat.canary.data.model.StickerPackDetailData
import com.yhchat.canary.data.model.StickerPackSortRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 贴纸仓库
 */
@Singleton
class StickerRepository @Inject constructor(
    private val apiService: ApiService,
    private val tokenRepository: TokenRepository
) {
    companion object {
        private const val TAG = "StickerRepository"
    }

    /**
     * 获取收藏表情包列表
     */
    suspend fun getStickerPackList(): Result<List<StickerPack>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = tokenRepository.getTokenSync()
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "Token为空")
                return@withContext Result.failure(Exception("未登录"))
            }

            val response = apiService.getStickerPackList(token)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Log.d(TAG, "✅ 获取贴纸包列表成功: ${body.data.stickerPacks.size}个贴纸包")
                    Result.success(body.data.stickerPacks)
                } else {
                    val error = "获取贴纸包列表失败: ${response.code().toString()}"
                    Log.e(TAG, error)
                    Result.failure(Exception(error))
                }
            } else {
                val error = "获取贴纸包列表失败: ${response.code()}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 获取贴纸包列表异常", e)
            Result.failure(e)
        }
    }

    /**
     * 查看表情包详情
     */
    suspend fun getStickerPackDetail(packId: Long): Result<StickerPackDetailData> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = tokenRepository.getTokenSync()
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "Token为空")
                return@withContext Result.failure(Exception("未登录"))
            }

            val request = StickerPackActionRequest(id = packId)
            val response = apiService.getStickerPackDetail(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Log.d(TAG, "✅ 获取贴纸包详情成功")
                    Result.success(body.data)
                        } else {
                    val error = "获取贴纸包详情失败: ${response.code().toString()}"
                    Log.e(TAG, error)
                    Result.failure(Exception(error))
                        }
                    } else {
                val error = "获取贴纸包详情失败: ${response.code()}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 获取贴纸包详情异常", e)
            Result.failure(e)
        }
    }
    
    /**
     * 添加表情包
     */
    suspend fun addStickerPack(packId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = tokenRepository.getTokenSync()
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "Token为空")
                return@withContext Result.failure(Exception("未登录"))
            }

            val request = StickerPackActionRequest(id = packId)
            val response = apiService.addStickerPack(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Log.d(TAG, "✅ 添加贴纸包成功")
                    Result.success(true)
                    } else {
                    val error = body?.message ?: "添加贴纸包失败: ${response.code()}"
                    Log.e(TAG, error)
                    Result.failure(Exception(error))
                    }
            } else {
                val error = "添加贴纸包失败: ${response.code()}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 添加贴纸包异常", e)
            Result.failure(e)
        }
    }
    
    /**
     * 移除收藏表情包
     */
    suspend fun removeStickerPack(packId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = tokenRepository.getTokenSync()
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "Token为空")
                return@withContext Result.failure(Exception("未登录"))
            }

            val request = StickerPackActionRequest(id = packId)
            val response = apiService.removeStickerPack(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Log.d(TAG, "✅ 移除贴纸包成功")
                    Result.success(true)
                } else {
                    val error = body?.message ?: "移除贴纸包失败: ${response.code()}"
                    Log.e(TAG, error)
                    Result.failure(Exception(error))
                }
            } else {
                val error = "移除贴纸包失败: ${response.code()}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 移除贴纸包异常", e)
            Result.failure(e)
        }
    }

    /**
     * 更改收藏表情包的排序
     * @param sortList List of Pair(id, sort) - sort越大排序越靠前
     */
    suspend fun sortStickerPacks(sortList: List<Pair<Long, Int>>): Result<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = tokenRepository.getTokenSync()
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "Token为空")
                return@withContext Result.failure(Exception("未登录"))
            }

            // 构建排序JSON数组字符串
            val jsonArray = JSONArray()
            sortList.forEach { (id, sort) ->
                val jsonObject = JSONObject()
                jsonObject.put("id", id.toString())
                jsonObject.put("sort", sort.toString())
                jsonArray.put(jsonObject)
            }
            val sortString = jsonArray.toString()
            
            val request = StickerPackSortRequest(sort = sortString)
            val response = apiService.sortStickerPacks(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Log.d(TAG, "✅ 贴纸包排序成功")
                    Result.success(true)
                } else {
                    val error = body?.message ?: "贴纸包排序失败: ${response.code()}"
                    Log.e(TAG, error)
                    Result.failure(Exception(error))
                }
            } else {
                val error = "贴纸包排序失败: ${response.code()}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 贴纸包排序异常", e)
            Result.failure(e)
        }
    }
}
