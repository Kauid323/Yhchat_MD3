package com.yhchat.canary.data.repository

import android.content.Context
import android.util.Log
import com.yhchat.canary.data.local.AppDatabase
import com.yhchat.canary.data.local.BlockedUser
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

/**
 * 黑名单Repository
 */
class BlocklistRepository(private val context: Context) {
    private val tag = "BlocklistRepository"
    private val blockedUserDao: com.yhchat.canary.data.local.BlockedUserDao
        get() = AppDatabase.getDatabase(context).blockedUserDao()

    @Volatile
    private var cachedUserId: String? = null
    @Volatile
    private var cachedPrefs: android.content.SharedPreferences? = null

    private fun getPrefs(): android.content.SharedPreferences {
        val currentUid = com.yhchat.canary.data.local.SecureTokenStorage.getInstance(context).getUserId() ?: "default"
        if (cachedUserId != currentUid || cachedPrefs == null) {
            cachedUserId = currentUid
            cachedPrefs = context.getSharedPreferences("blocklist_settings_$currentUid", Context.MODE_PRIVATE)
        }
        return cachedPrefs!!
    }
    
    /**
     * 黑名单是否启用
     */
    fun isBlocklistEnabled(): Boolean {
        return getPrefs().getBoolean("blocklist_enabled", false)
    }
    
    /**
     * 设置黑名单启用状态
     */
    fun setBlocklistEnabled(enabled: Boolean) {
        getPrefs().edit().putBoolean("blocklist_enabled", enabled).apply()
        Log.d(tag, "Blocklist enabled: $enabled")
    }
    
    /**
     * 获取所有被屏蔽用户（Flow）
     */
    fun getAllBlockedUsers(): Flow<List<BlockedUser>> {
        return blockedUserDao.getAllBlockedUsers()
    }
    
    /**
     * 获取所有被屏蔽用户（同步）
     */
    suspend fun getAllBlockedUsersSync(): List<BlockedUser> {
        return blockedUserDao.getAllBlockedUsersSync()
    }
    
    /**
     * 检查用户是否被屏蔽
     */
    suspend fun isUserBlocked(userId: String): Boolean {
        if (!isBlocklistEnabled()) return false
        return blockedUserDao.isUserBlocked(userId)
    }
    
    /**
     * 获取被屏蔽用户
     */
    suspend fun getBlockedUser(userId: String): BlockedUser? {
        return blockedUserDao.getBlockedUser(userId)
    }
    
    /**
     * 添加用户到黑名单
     */
    suspend fun blockUser(
        userId: String,
        userName: String = "",
        reason: String = "",
        avatarUrl: String? = null
    ): Result<Unit> {
        return try {
            val blockedUser = BlockedUser(
                userId = userId,
                userName = userName,
                reason = reason,
                blockedTime = System.currentTimeMillis(),
                avatarUrl = avatarUrl
            )
            blockedUserDao.insertBlockedUser(blockedUser)
            Log.d(tag, "Blocked user: $userId ($userName)")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Error blocking user", e)
            Result.failure(e)
        }
    }
    
    /**
     * 更新被屏蔽用户信息
     */
    suspend fun updateBlockedUser(user: BlockedUser): Result<Unit> {
        return try {
            blockedUserDao.updateBlockedUser(user)
            Log.d(tag, "Updated blocked user: ${user.userId}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Error updating blocked user", e)
            Result.failure(e)
        }
    }
    
    /**
     * 从黑名单移除用户
     */
    suspend fun unblockUser(userId: String): Result<Unit> {
        return try {
            blockedUserDao.deleteBlockedUserById(userId)
            Log.d(tag, "Unblocked user: $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Error unblocking user", e)
            Result.failure(e)
        }
    }
    
    /**
     * 清空黑名单
     */
    suspend fun clearBlocklist(): Result<Unit> {
        return try {
            blockedUserDao.clearAllBlockedUsers()
            Log.d(tag, "Cleared all blocked users")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Error clearing blocklist", e)
            Result.failure(e)
        }
    }
    
    /**
     * 获取黑名单用户数量
     */
    suspend fun getBlockedUsersCount(): Int {
        return blockedUserDao.getBlockedUsersCount()
    }
    
    /**
     * 导出黑名单为JSON
     */
    suspend fun exportToJson(): String {
        val users = getAllBlockedUsersSync()
        val jsonArray = JSONArray()
        
        users.forEach { user ->
            val jsonObject = JSONObject()
            jsonObject.put("userId", user.userId)
            jsonObject.put("userName", user.userName)
            jsonObject.put("reason", user.reason)
            jsonObject.put("blockedTime", user.blockedTime)
            jsonObject.put("avatarUrl", user.avatarUrl ?: "")
            jsonArray.put(jsonObject)
        }
        
        val result = JSONObject()
        result.put("version", 1)
        result.put("exportTime", System.currentTimeMillis())
        result.put("blockedUsers", jsonArray)
        
        return result.toString(2)  // 格式化输出
    }
    
    /**
     * 从JSON导入黑名单
     * @param mergeMode true-合并（保留现有），false-替换（清空现有）
     */
    suspend fun importFromJson(jsonString: String, mergeMode: Boolean = true): Result<Int> {
        return try {
            if (!mergeMode) {
                // 替换模式：先清空现有黑名单
                clearBlocklist()
            }
            
            val jsonObject = JSONObject(jsonString)
            val blockedUsersArray = jsonObject.getJSONArray("blockedUsers")
            val importedUsers = mutableListOf<BlockedUser>()
            
            for (i in 0 until blockedUsersArray.length()) {
                val userJson = blockedUsersArray.getJSONObject(i)
                val blockedUser = BlockedUser(
                    userId = userJson.getString("userId"),
                    userName = userJson.optString("userName", ""),
                    reason = userJson.optString("reason", ""),
                    blockedTime = userJson.optLong("blockedTime", System.currentTimeMillis()),
                    avatarUrl = if (userJson.has("avatarUrl") && !userJson.isNull("avatarUrl")) {
                        userJson.optString("avatarUrl")
                    } else {
                        null
                    }
                )
                importedUsers.add(blockedUser)
            }
            
            blockedUserDao.insertBlockedUsers(importedUsers)
            Log.d(tag, "Imported ${importedUsers.size} blocked users (merge=$mergeMode)")
            Result.success(importedUsers.size)
        } catch (e: Exception) {
            Log.e(tag, "Error importing blocklist from JSON", e)
            Result.failure(e)
        }
    }
}
