package com.yhchat.canary.ui.settings

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.yhchat.canary.ui.components.rememberBooleanPreference

/**
 * 大文件分块与传输任务的设置组。
 *
 * 对应协议里的「完成后自动清缓存」：开启后，下载在合并、完整校验、发布成功后
 * 清掉分块；上传在所有文件消息确认成功后清掉暂存副本。
 *
 * 协议明确规定了几条不该清理的情况，这里在副标题里如实说明：
 * 暂停、失败、取消和未完成的任务**不清**，断点继续保留 ——
 * 否则用户重试时会发现要整包重下。
 */
@Composable
internal fun SplitFileSettingsGroup(context: Context) {
    val autoClean by rememberBooleanPreference(
        preferencesName = SPLIT_PREFS_NAME,
        key = KEY_AUTO_CLEAN_AFTER_DONE,
        defaultValue = false
    )

    SettingsGroup(
        title = "大文件分块与传输任务",
        items = listOf(
            {
                SettingsSwitchItem(
                    icon = Icons.Default.Recycling,
                    title = "传完文件自动清除缓存",
                    subtitle = if (autoClean) {
                        "已完成的任务会自动清理分块，任务记录和合并后的文件保留"
                    } else {
                        "默认关闭。开启后仅在完整校验通过并发布成功后清理"
                    },
                    checked = autoClean,
                    onCheckedChange = { enabled ->
                        context.getSharedPreferences(
                            SPLIT_PREFS_NAME,
                            Context.MODE_PRIVATE
                        ).edit()
                            .putBoolean(KEY_AUTO_CLEAN_AFTER_DONE, enabled)
                            .apply()
                    }
                )
            }
        )
    )
}

/** 分块下载相关偏好的存储位置，ChatViewModel 也会读这里 */
internal const val SPLIT_PREFS_NAME = "split_file_settings"

/** 传完之后是否自动清缓存 */
internal const val KEY_AUTO_CLEAN_AFTER_DONE = "auto_clean_after_done"

/** 供业务侧读取的便捷方法 */
fun isAutoCleanSplitCacheEnabled(context: Context): Boolean {
    return context.getSharedPreferences(SPLIT_PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_AUTO_CLEAN_AFTER_DONE, false)
}
