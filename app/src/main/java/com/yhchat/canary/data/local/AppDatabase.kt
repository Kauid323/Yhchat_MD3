package com.yhchat.canary.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.yhchat.canary.data.model.SavedAccount
import java.util.concurrent.ConcurrentHashMap

/**
 * 应用数据库 - 用户级别隔离存储
 * 每个账号拥有独立的数据库文件 yhchat_database_{userId}，切换账号时自动切换到对应数据库，
 * 原有账号数据完全保留，再次切换回来时直接加载对应数据。
 */
@Database(
    entities = [
        UserToken::class, 
        CachedConversation::class, 
        CachedMessage::class, 
        BlockedUser::class, 
        SavedAccount::class,
        CachedDiscoverData::class,
        CachedProfileData::class,
        DownloadedFileRecord::class,
        ShareTargetPreference::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun userTokenDao(): UserTokenDao
    abstract fun cachedConversationDao(): CachedConversationDao
    abstract fun cachedMessageDao(): CachedMessageDao
    abstract fun blockedUserDao(): BlockedUserDao
    abstract fun savedAccountDao(): SavedAccountDao
    abstract fun cachedDiscoverDataDao(): CachedDiscoverDataDao
    abstract fun cachedProfileDataDao(): CachedProfileDataDao
    abstract fun downloadedFileRecordDao(): DownloadedFileRecordDao
    abstract fun shareTargetPreferenceDao(): ShareTargetPreferenceDao
    
    companion object {
        private val INSTANCES = ConcurrentHashMap<String, AppDatabase>()
        
        fun getDatabase(context: Context, userId: String? = null): AppDatabase {
            val appContext = context.applicationContext
            val resolvedUserId = userId?.takeIf { it.isNotBlank() } 
                ?: runCatching { SecureTokenStorage(appContext).getUserId() }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: "default"
                
            return INSTANCES.computeIfAbsent(resolvedUserId) { uid ->
                val dbName = if (uid == "default") "yhchat_database" else "yhchat_database_$uid"
                
                checkAndMigrateLegacyDatabase(appContext, uid, dbName)
                
                Room.databaseBuilder(
                    appContext,
                    AppDatabase::class.java,
                    dbName
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
            }
        }
        
        private fun checkAndMigrateLegacyDatabase(context: Context, uid: String, targetDbName: String) {
            if (uid == "default") return
            try {
                val targetDbFile = context.getDatabasePath(targetDbName)
                if (!targetDbFile.exists()) {
                    val legacyDbFile = context.getDatabasePath("yhchat_database")
                    if (legacyDbFile.exists()) {
                        val currentUid = runCatching { SecureTokenStorage(context).getUserId() }.getOrNull()
                        if (currentUid == uid) {
                            legacyDbFile.copyTo(targetDbFile, overwrite = false)
                            val legacyWal = context.getDatabasePath("yhchat_database-wal")
                            if (legacyWal.exists()) {
                                legacyWal.copyTo(context.getDatabasePath("$targetDbName-wal"), overwrite = false)
                            }
                            val legacyShm = context.getDatabasePath("yhchat_database-shm")
                            if (legacyShm.exists()) {
                                legacyShm.copyTo(context.getDatabasePath("$targetDbName-shm"), overwrite = false)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("AppDatabase", "Legacy database migration skipped: ${e.message}")
            }
        }
        
        /**
         * 清除数据库实例（用于测试或重置）
         */
        fun clearInstance(userId: String? = null) {
            if (userId != null) {
                INSTANCES.remove(userId)?.let {
                    try { it.close() } catch (_: Exception) {}
                }
            } else {
                INSTANCES.values.forEach { db ->
                    try { db.close() } catch (_: Exception) {}
                }
                INSTANCES.clear()
            }
        }
    }
}
