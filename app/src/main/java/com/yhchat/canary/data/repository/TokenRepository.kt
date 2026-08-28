package com.yhchat.canary.data.repository

import android.content.Context
import com.yhchat.canary.data.local.SecureTokenStorage
import com.yhchat.canary.data.local.UserToken
import com.yhchat.canary.data.local.UserTokenDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Token存储仓库 - 使用EncryptedSharedPreferences替代SQLCipher
 */
class TokenRepository @Inject constructor(
    private val userTokenDao: UserTokenDao,
    private val context: Context? = null
) {
    
    private val secureStorage: SecureTokenStorage? by lazy {
        context?.let { SecureTokenStorage.getInstance(it) }
    }
    
    /**
     * 保存Token和用户ID
     */
    suspend fun saveToken(token: String, userId: String = "") {
        // 优先使用安全存储
        secureStorage?.let {
            it.saveUserToken(token, userId)
            return
        }
        
        // 回退到数据库存储
        val userToken = UserToken(token = token)
        userTokenDao.insertToken(userToken)
    }
    
    /**
     * 保存Token（保持兼容性）
     */
    suspend fun saveToken(token: String) {
        saveToken(token, "")
    }
    
    /**
     * 获取Token
     */
    fun getToken(): Flow<UserToken?> {
        return flow {
            // 优先从安全存储获取
            secureStorage?.let { storage ->
                val token = storage.getUserToken()
                if (!token.isNullOrEmpty()) {
                    emit(UserToken(token = token))
                    return@flow
                }
            }
            
            // 回退到数据库
            userTokenDao.getToken().collect { emit(it) }
        }
    }
    
    /**
     * 同步获取Token
     */
    suspend fun getTokenSync(): String? {
        // 优先从安全存储获取
        secureStorage?.let { storage ->
            val token = storage.getUserToken()
            if (!token.isNullOrEmpty()) {
                return token
            }
        }
        
        // 回退到数据库
        return userTokenDao.getTokenSync()?.token
    }
    
    /**
     * 获取用户ID
     */
    suspend fun getUserId(): String? {
        return secureStorage?.getUserId()
    }
    
    /**
     * 同步获取用户ID
     */
    fun getUserIdSync(): String? {
        return secureStorage?.getUserId()
    }
    
    /**
     * 清除Token
     */
    suspend fun clearToken() {
        // 清除安全存储
        secureStorage?.clearTokens()
        
        // 清除数据库
        userTokenDao.clearTokens()
    }
    
    /**
     * 检查是否已登录
     */
    suspend fun isLoggedIn(): Boolean {
        return getTokenSync() != null
    }
    
    /**
     * 设置Context（用于创建SecureTokenStorage）
     */
    fun setContext(context: Context): TokenRepository {
        return TokenRepository(userTokenDao, context)
    }
}
