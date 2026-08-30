package com.yhchat.canary.data.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.yhchat.canary.data.api.ApiService
import com.yhchat.canary.data.local.AppDatabase
import com.yhchat.canary.data.local.CachedDiscoverData
import com.yhchat.canary.data.model.RecommendBot
import com.yhchat.canary.data.model.RecommendCategoryRequest
import com.yhchat.canary.data.model.RecommendGroup
import com.yhchat.canary.data.model.RecommendGroupListRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 发现功能Repository
 */
class DiscoverRepository(
    private val apiService: ApiService,
    private val tokenRepository: TokenRepository,
    private val context: Context
) {
    companion object {
        private const val TAG = "DiscoverRepository"
        private const val CACHE_VALIDITY_DURATION = 24 * 60 * 60 * 1000L // 1天
    }

    private val database: AppDatabase
        get() = AppDatabase.getDatabase(context)
    private val cachedDiscoverDataDao: com.yhchat.canary.data.local.CachedDiscoverDataDao
        get() = database.cachedDiscoverDataDao()
    private val gson = Gson()

    /**
     * 检查缓存是否有效
     */
    private fun isCacheValid(timestamp: Long): Boolean {
        return System.currentTimeMillis() - timestamp < CACHE_VALIDITY_DURATION
    }

    /**
     * 从缓存加载数据
     */
    suspend fun loadFromCache(): Pair<List<RecommendGroup>, List<RecommendBot>>? = withContext(Dispatchers.IO) {
        try {
            val cachedData = cachedDiscoverDataDao.getCachedData()
            if (cachedData != null && isCacheValid(cachedData.timestamp)) {
                val groupsType = object : TypeToken<List<RecommendGroup>>() {}.type
                val botsType = object : TypeToken<List<RecommendBot>>() {}.type
                val groups = gson.fromJson<List<RecommendGroup>>(cachedData.groupsJson, groupsType)
                val bots = gson.fromJson<List<RecommendBot>>(cachedData.botsJson, botsType)
                // Log.d(TAG, "从缓存加载数据成功")
                Pair(groups, bots)
            } else {
                // Log.d(TAG, "缓存无效或已过期")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "从缓存加载数据失败", e)
            null
        }
    }

    /**
     * 保存数据到缓存
     */
    suspend fun saveToCache(groups: List<RecommendGroup>, bots: List<RecommendBot>) = withContext(Dispatchers.IO) {
        try {
            val groupsJson = gson.toJson(groups)
            val botsJson = gson.toJson(bots)
            val cachedData = CachedDiscoverData(
                id = 1,
                groupsJson = groupsJson,
                botsJson = botsJson,
                timestamp = System.currentTimeMillis()
            )
            cachedDiscoverDataDao.insertCachedData(cachedData)
        } catch (e: Exception) {
            Log.e(TAG, "保存数据到缓存失败", e)
        }
    }

    /**
     * 获取发现群聊分类列表
     */
    suspend fun getGroupCategories(): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val token = tokenRepository.getTokenSync()
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "Token为空")
                return@withContext Result.failure(Exception("未登录"))
            }

            val response = apiService.getRecommendCategoryList(
                token,
                RecommendCategoryRequest()
            )

            if (response.isSuccessful) {
                val body = response.body()
                if (body?.code == 1) {
                    val categories = body.data?.categories ?: emptyList()
                    Result.success(categories)
                } else {
                    Log.e(TAG, "获取群聊分类失败: ${body?.msg}")
                    Result.failure(Exception(body?.msg ?: "获取失败"))
                }
            } else {
                Log.e(TAG, "获取群聊分类请求失败: ${response.code()}")
                Result.failure(Exception("网络请求失败"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取群聊分类异常", e)
            Result.failure(e)
        }
    }

    /**
     * 获取推荐群聊列表
     */
    suspend fun getRecommendGroups(
        category: String = "",
        keyword: String = "",
        size: Int = 30,
        page: Int = 1
    ): Result<List<RecommendGroup>> = withContext(Dispatchers.IO) {
        try {
            val token = tokenRepository.getTokenSync()
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "Token为空")
                return@withContext Result.failure(Exception("未登录"))
            }

            val response = apiService.getRecommendGroupList(
                token,
                RecommendGroupListRequest(category, keyword, size, page)
            )

            if (response.isSuccessful) {
                val body = response.body()
                if (body?.code == 1) {
                    val groups = body.data?.groupList ?: emptyList()
                    // Log.d(TAG, "获取推荐群聊成功: ${groups.size}个群聊")
                    Result.success(groups)
                } else {
                    Log.e(TAG, "获取推荐群聊失败: ${body?.msg}")
                    Result.failure(Exception(body?.msg ?: "获取失败"))
                }
            } else {
                Log.e(TAG, "获取推荐群聊请求失败: ${response.code()}")
                Result.failure(Exception("网络请求失败"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取推荐群聊异常", e)
            Result.failure(e)
        }
    }

    /**
     * 获取推荐机器人列表
     */
    suspend fun getRecommendBots(): Result<List<RecommendBot>> = withContext(Dispatchers.IO) {
        try {
            val token = tokenRepository.getTokenSync()
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "Token为空")
                return@withContext Result.failure(Exception("未登录"))
            }

            val response = apiService.getRecommendBotList(token)

            if (response.isSuccessful) {
                val body = response.body()
                if (body?.code == 1) {
                    val bots = body.data?.botList ?: emptyList()
                    // Log.d(TAG, "获取推荐机器人成功: ${bots.size}个机器人")
                    Result.success(bots)
                } else {
                    Log.e(TAG, "获取推荐机器人失败: ${body?.msg}")
                    Result.failure(Exception(body?.msg ?: "获取失败"))
                }
            } else {
                Log.e(TAG, "获取推荐机器人请求失败: ${response.code()}")
                Result.failure(Exception("网络请求失败"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取推荐机器人异常", e)
            Result.failure(e)
        }
    }
}

