package com.yhchat.canary.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.yhchat.canary.data.model.SavedAccount

/**
 * 全局应用数据库 - 存储跨账号共享的保存账号列表等全局数据
 */
@Database(
    entities = [
        SavedAccount::class
    ],
    version = 1,
    exportSchema = false
)
abstract class GlobalDatabase : RoomDatabase() {

    abstract fun savedAccountDao(): SavedAccountDao

    companion object {
        @Volatile
        private var INSTANCE: GlobalDatabase? = null

        fun getDatabase(context: Context): GlobalDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GlobalDatabase::class.java,
                    "yhchat_global_database"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
