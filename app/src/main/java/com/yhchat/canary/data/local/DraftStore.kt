package com.yhchat.canary.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 聊天草稿存储
 * 用于保存用户在聊天输入框中未发送的内容，按用户账号隔离
 */
@Singleton
class DraftStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "chat_draft_prefs",
        Context.MODE_PRIVATE
    )
    
    /**
     * 保存草稿
     * @param chatId 会话ID
     * @param chatType 会话类型
     * @param content 草稿内容
     */
    fun saveDraft(chatId: String, chatType: Int, content: String) {
        val key = getKey(chatId, chatType)
        if (content.isNotEmpty()) {
            prefs.edit().putString(key, content).apply()
        } else {
            clearDraft(chatId, chatType)
        }
    }
    
    /**
     * 获取草稿
     * @param chatId 会话ID
     * @param chatType 会话类型
     * @return 草稿内容，如果没有则返回 null
     */
    fun getDraft(chatId: String, chatType: Int): String? {
        val key = getKey(chatId, chatType)
        return prefs.getString(key, null)
    }
    
    /**
     * 清除草稿
     */
    fun clearDraft(chatId: String, chatType: Int) {
        val key = getKey(chatId, chatType)
        prefs.edit().remove(key).apply()
    }
    
    /**
     * 生成唯一的key（附带用户ID实现隔离）
     */
    private fun getKey(chatId: String, chatType: Int): String {
        val userId = SecureTokenStorage.getInstance(context).getUserId() ?: "default"
        return "${userId}_${chatType}_${chatId}"
    }
}
