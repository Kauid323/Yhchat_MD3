package com.yhchat.canary.data.repository

import android.content.Context
import com.yhchat.canary.data.local.AppDatabase
import com.yhchat.canary.data.local.SHARE_TARGET_SCOPE_CHAT
import com.yhchat.canary.data.local.SHARE_TARGET_SCOPE_GLOBAL
import com.yhchat.canary.data.local.ShareTargetPreference
import kotlinx.coroutines.flow.Flow

class ExternalShareRepository(private val context: Context) {
    private val dao: com.yhchat.canary.data.local.ShareTargetPreferenceDao
        get() = AppDatabase.getDatabase(context.applicationContext).shareTargetPreferenceDao()

    fun getAll(): Flow<List<ShareTargetPreference>> = dao.getAll()

    suspend fun getResolvedTarget(chatId: String?): ShareTargetPreference? {
        val perChat = chatId?.takeIf { it.isNotBlank() }?.let { dao.getForChat(it) }
        return perChat ?: dao.getGlobal()
    }

    suspend fun setGlobal(packageName: String, label: String) {
        dao.upsert(
            ShareTargetPreference(
                id = globalId(),
                scope = SHARE_TARGET_SCOPE_GLOBAL,
                packageName = packageName,
                label = label,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun setForChat(chatId: String, packageName: String, label: String) {
        dao.upsert(
            ShareTargetPreference(
                id = chatId(chatId),
                scope = SHARE_TARGET_SCOPE_CHAT,
                chatId = chatId,
                packageName = packageName,
                label = label,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun saveResolvedTarget(chatId: String?, packageName: String, label: String) {
        if (chatId.isNullOrBlank()) {
            setGlobal(packageName, label)
        } else {
            setForChat(chatId, packageName, label)
        }
    }

    suspend fun delete(id: String) = dao.deleteById(id)
    suspend fun deleteGlobal() = dao.deleteGlobal()
    suspend fun deleteForChat(chatId: String) = dao.deleteForChat(chatId)

    companion object {
        fun globalId(): String = "global"
        fun chatId(chatId: String): String = "chat:$chatId"
    }
}
