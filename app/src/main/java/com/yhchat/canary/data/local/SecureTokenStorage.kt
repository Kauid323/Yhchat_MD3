package com.yhchat.canary.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 安全的Token存储管理器
 * 使用EncryptedSharedPreferences替代SQLCipher，体积更小，安全性同样高
 */
class SecureTokenStorage(context: Context) {
    
    companion object {
        private const val PREFS_NAME = "yhchat_secure_tokens"
        private const val KEY_USER_TOKEN = "user_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_LAST_LOGIN_TIME = "last_login_time"

        @Volatile
        private var INSTANCE: SecureTokenStorage? = null

        fun getInstance(context: Context): SecureTokenStorage {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SecureTokenStorage(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val encryptedPrefs: SharedPreferences
    private val isEncrypted: Boolean

    @Volatile
    private var memoryUserId: String? = null
    @Volatile
    private var memoryUserToken: String? = null
    
    init {
        var encrypted = false
        encryptedPrefs = try {
            // 创建或获取主密钥
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            // 创建加密的SharedPreferences
            encrypted = true
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // 如果创建加密SharedPreferences失败，回退到普通SharedPreferences
            // 这种情况通常发生在较老的设备上（如Android 4.x或某些定制ROM）
            android.util.Log.w("SecureTokenStorage", "加密SharedPreferences创建失败，回退到普通存储: ${e.message}")
            encrypted = false
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        isEncrypted = encrypted

        // 预热内存缓存，避免后续每次读取产生昂贵的硬件密钥加解密
        memoryUserId = encryptedPrefs.getString(KEY_USER_ID, null)
        memoryUserToken = encryptedPrefs.getString(KEY_USER_TOKEN, null)
    }
    
    /**
     * 保存指定用户的Token
     */
    fun saveAccountToken(userId: String, token: String) {
        encryptedPrefs.edit()
            .putString("token_$userId", token)
            .apply()
    }

    /**
     * 获取指定用户的Token
     */
    fun getAccountToken(userId: String): String? {
        return encryptedPrefs.getString("token_$userId", null)
    }

    /**
     * 删除指定用户的Token
     */
    fun removeAccountToken(userId: String) {
        encryptedPrefs.edit()
            .remove("token_$userId")
            .apply()
    }

    /**
     * 保存用户Token
     */
    fun saveUserToken(token: String, userId: String) {
        memoryUserToken = token
        memoryUserId = userId
        encryptedPrefs.edit()
            .putString(KEY_USER_TOKEN, token)
            .putString(KEY_USER_ID, userId)
            .putLong(KEY_LAST_LOGIN_TIME, System.currentTimeMillis())
            .apply()
        
        // 同时保存到账户列表Token中
        if (userId.isNotEmpty()) {
            saveAccountToken(userId, token)
        }
    }
    
    /**
     * 获取用户Token
     */
    fun getUserToken(): String? {
        return memoryUserToken ?: encryptedPrefs.getString(KEY_USER_TOKEN, null)?.also { memoryUserToken = it }
    }
    
    /**
     * 获取用户ID
     */
    fun getUserId(): String? {
        return memoryUserId ?: encryptedPrefs.getString(KEY_USER_ID, null)?.also { memoryUserId = it }
    }
    
    /**
     * 获取最后登录时间
     */
    fun getLastLoginTime(): Long {
        return encryptedPrefs.getLong(KEY_LAST_LOGIN_TIME, 0L)
    }
    
    /**
     * 检查Token是否存在
     */
    fun hasValidToken(): Boolean {
        val token = getUserToken()
        return !token.isNullOrEmpty()
    }
    
    /**
     * 清除所有Token数据
     */
    fun clearTokens() {
        memoryUserId = null
        memoryUserToken = null
        encryptedPrefs.edit()
            .remove(KEY_USER_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_LAST_LOGIN_TIME)
            .apply()
    }
    
    /**
     * 清除所有数据
     */
    fun clearAll() {
        memoryUserId = null
        memoryUserToken = null
        encryptedPrefs.edit().clear().apply()
    }
}
