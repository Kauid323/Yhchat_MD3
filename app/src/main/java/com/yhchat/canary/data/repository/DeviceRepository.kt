package com.yhchat.canary.data.repository

import android.content.Context
import com.yhchat.canary.data.local.GlobalDatabase
import com.yhchat.canary.data.local.KnownDevice
import com.yhchat.canary.data.model.DeviceInfo

/**
 * 设备列表的本地持久化。
 *
 * ## 为什么要存本地
 *
 * 服务端 `/v1/user/clients` 只返回**当前在线**的设备。一旦某台设备下线（不管是自己点的
 * 还是关掉客户端），它就从列表里彻底消失了，用户再也看不到「我上次在哪个设备上登录过」。
 *
 * 所以这里把见过的设备都留一份：每次拉到在线列表就合并进去，下次打开时
 * **先展示本地记录**（立即可见，不等网络），网络回来后再用最新在线状态覆盖。
 *
 * ## 存储位置
 *
 * 用 GlobalDatabase 而不是账号库：设备记录按 (userId, deviceId) 双主键隔离，
 * 一个库就能装下所有账号的设备历史，切换账号也不会丢。
 */
class DeviceRepository(context: Context) {

    private val dao = GlobalDatabase.getDatabase(context.applicationContext).knownDeviceDao()

    /**
     * 把服务端返回的在线设备合并进本地。
     *
     * 合并规则：
     *   - 在线列表里的设备 → isOnline=true，刷新 lastSeen / updatedAt；
     *   - 不在列表里的历史设备 → 保留，但不改动（由调用方按"不在在线列表"判定为离线）；
     *   - 用户主动下线过的设备 → 清除 offlineByUser 标记（它又有活动了）。
     *
     * @return 合并后的完整设备列表（在线 + 历史），已按最近活跃排序
     */
    suspend fun mergeOnlineDevices(userId: String, onlineDevices: List<DeviceInfo>): List<DeviceInfo> {
        if (userId.isBlank()) return onlineDevices

        val existing = dao.getByUser(userId).associateBy { it.deviceId }
        val nowMillis = System.currentTimeMillis()

        val toWrite = onlineDevices.map { device ->
            val previous = existing[device.deviceId]
            KnownDevice(
                userId = userId,
                deviceId = device.deviceId,
                platform = device.platform,
                // 服务端给的登录时间优先；拿不到就沿用本地的
                loginTimestamp = device.loginTimestamp.takeIf { it > 0L }
                    ?: previous?.loginTimestamp
                    ?: 0L,
                lastSeenTimestamp = if (device.loginTimestamp > 0L) {
                    device.loginTimestamp
                } else {
                    previous?.lastSeenTimestamp ?: 0L
                },
                // 又出现在在线列表里 → 之前的下线标记清掉
                offlineByUser = false,
                updatedAtMillis = nowMillis
            )
        }
        if (toWrite.isNotEmpty()) {
            dao.upsertAll(toWrite)
        }

        return buildCombinedList(userId, onlineDevices)
    }

    /**
     * 读取本地记录并与在线列表合并成界面用的完整列表。
     *
     * 不传 [onlineDevices] 时（比如刚打开页面、网络还没回来）会把所有本地设备
     * 都当作"非在线"展示，这样用户至少能看到历史记录，而不是一片空白。
     */
    suspend fun buildCombinedList(
        userId: String,
        onlineDevices: List<DeviceInfo>
    ): List<DeviceInfo> {
        val onlineIds = onlineDevices.mapTo(mutableSetOf()) { it.deviceId }
        val known = dao.getByUser(userId)

        val fromOnline = onlineDevices.map { device ->
            device.copy(userId = userId, isOnline = true)
        }

        // 本地有、但当前不在线的设备
        val historyOnly = known
            .filter { it.deviceId !in onlineIds }
            .map { record ->
                DeviceInfo(
                    userId = record.userId,
                    platform = record.platform,
                    deviceId = record.deviceId,
                    loginTimestamp = record.loginTimestamp,
                    isOnline = false
                )
            }

        // 在线在前，历史在后；各自按最近活跃排序
        return fromOnline.sortedByDescending { it.loginTimestamp } +
            historyOnly.sortedByDescending { it.loginTimestamp }
    }

    /** 只读本地记录（网络不可用时用） */
    suspend fun getLocalDevices(userId: String): List<DeviceInfo> {
        return dao.getByUser(userId).map { record ->
            DeviceInfo(
                userId = record.userId,
                platform = record.platform,
                deviceId = record.deviceId,
                loginTimestamp = record.loginTimestamp,
                // 本地记录本身不带"当前是否在线"的信息，统一当离线展示；
                // 等在线接口回来后再纠正
                isOnline = false
            )
        }
    }

    /**
     * 标记某个设备已被用户主动下线。
     *
     * 只是落一个标记：设备仍然留在历史里（用户可能想再看一眼），
     * 但不再是"在线"状态。
     */
    suspend fun markOffline(userId: String, deviceId: String) {
        val previous = dao.find(userId, deviceId) ?: return
        dao.upsert(previous.copy(offlineByUser = true))
    }

    /** 清空某个账号的设备历史 */
    suspend fun clearForUser(userId: String) {
        dao.clearForUser(userId)
    }
}
