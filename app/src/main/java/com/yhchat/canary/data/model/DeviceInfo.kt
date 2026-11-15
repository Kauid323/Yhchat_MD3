package com.yhchat.canary.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Phone
import androidx.compose.ui.graphics.vector.ImageVector
import com.google.gson.annotations.SerializedName

/**
 * 让设备下线：`POST /v1/user/device-offline`
 */
data class DeviceOfflineRequest(
    @SerializedName("deviceId")
    val deviceId: String
)

/**
 * 设备信息数据模型
 */
data class DeviceInfo(
    val userId: String,
    val platform: String,
    val deviceId: String,
    val loginTimestamp: Long,
    /**
     * 是否当前在线。
     *
     * 服务端只返回在线设备，但界面要把「历史设备」也列出来，
     * 所以同一个模型要能表达离线状态。
     */
    val isOnline: Boolean = true
) {
    /**
     * 获取平台显示名称
     */
    fun getPlatformDisplayName(): String {
        return when (platform.lowercase()) {
            "android" -> "Android"
            "windows" -> "Windows"
            "web" -> "网页版"
            "ios" -> "iOS"
            "mac" -> "macOS"
            "linux" -> "Linux"
            else -> platform
        }
    }
    
    /**
     * 获取平台图标资源
     */
    fun getPlatformIcon(): ImageVector {
        return when (platform.lowercase()) {
            "android" -> Icons.Filled.Phone
            "windows" -> Icons.Filled.Computer
            "web" -> Icons.Filled.Language
            "ios" -> Icons.Filled.Phone
            "mac" -> Icons.Filled.Computer
            "linux" -> Icons.Filled.Computer
            else -> Icons.Filled.DeviceHub
        }
    }
    
    /**
     * 格式化登录时间
     */
    fun getFormattedLoginTime(): String {
        return try {
            val date = java.util.Date(loginTimestamp * 1000) // 转换为毫秒
            val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            formatter.format(date)
        } catch (e: Exception) {
            "未知时间"
        }
    }
}