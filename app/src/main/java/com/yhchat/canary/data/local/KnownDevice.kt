package com.yhchat.canary.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 已知设备记录。
 *
 * 用途：在线设备接口只返回**当前在线**的设备，已下线的就消失了。
 * 这里把见过的设备都留一份，界面就能同时展示「在线」和「历史」两类，
 * 用户也能看出「这台设备上次是什么时候用的」。
 *
 * 主键用 (userId + deviceId)：同一个设备在不同账号下是两条记录，
 * 因为设备列表本身是按账号查的，混在一起会串号。
 */
@Entity(
    tableName = "known_devices",
    primaryKeys = ["userId", "deviceId"]
)
data class KnownDevice(
    val userId: String,
    val deviceId: String,
    val platform: String,
    /** 最近一次出现在在线列表里的时间（秒级时间戳，与服务端一致） */
    val lastSeenTimestamp: Long,
    /** 服务端给的登录时间（秒级），首次见到时写入 */
    val loginTimestamp: Long,
    /** 是否已主动下线；下线后仍保留在历史里 */
    val offlineByUser: Boolean = false,
    /** 最近一次确认在线的本地时间（毫秒），用于排序 */
    val updatedAtMillis: Long = System.currentTimeMillis()
)
