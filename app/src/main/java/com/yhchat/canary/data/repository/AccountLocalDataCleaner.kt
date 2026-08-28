package com.yhchat.canary.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 账号切换时的本地数据处理。
 * 现已支持基于用户ID的独立数据库隔离（yhchat_database_{userId}），切换账号时各账号数据完整保留在各自数据库中，
 * 不再清除Room数据库记录。
 */
class AccountLocalDataCleaner(
    context: Context
) {
    private val appContext = context.applicationContext

    suspend fun clearForAccountSwitch() = withContext(Dispatchers.IO) {
        // 各账号已实现独立数据库及用户作用域存储，切换账号时保留各自历史数据
    }
}
