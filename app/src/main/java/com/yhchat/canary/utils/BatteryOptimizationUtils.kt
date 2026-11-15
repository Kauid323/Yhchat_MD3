package com.yhchat.canary.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationManagerCompat

/**
 * 耗电策略 / 通知权限相关的跳板。
 *
 * 应用靠一条常驻的 WebSocket 收推送，被系统丢进「受限」「优化」桶里之后，
 * 息屏一会儿连接就会被掐掉，通知自然也就收不到了。这里的几个入口只是把用户
 * 引导到对应的系统页面 —— 具体放不放行是系统的决定，我们不做任何伪装。
 */
object BatteryOptimizationUtils {

    private const val TAG = "BatteryOptimization"

    /**
     * 当前是否已经在「无限制 / 不优化」名单里。
     *
     * Android 6 以下没有这套机制，直接当成不受限，免得设置项永远显示"未设置"。
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return true
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * 拉起「是否允许后台耗电无限制」的系统弹窗。
     *
     * 优先用 ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 直接问本应用，
     * 这是唯一能一步跳到「是否允许」的位置；部分 ROM（MIUI / EMUI / 一些
     * 精简包）把这条 action 删了，startActivity 会抛 ActivityNotFoundException，
     * 就退回到电池优化列表页让用户自己找。
     *
     * 注意不能靠 resolveActivity 判断是否存在：Android 11+ 的包可见性会让它
     * 对系统 action 也返回 null，从而误判成"不支持"。所以只认异常。
     *
     * @return true 表示至少成功打开了某个设置页
     */
    fun requestIgnoreBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false

        val direct = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (startSafely(context, direct)) return true

        val settingsList = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (startSafely(context, settingsList)) return true

        // 最后兜底：应用详情页，用户一般能在那里找到"电池"或"耗电管理"
        val appDetails = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startSafely(context, appDetails)
    }

    /** 打开本应用的通知设置页（开关、渠道、气泡都在这里） */
    fun openAppNotificationSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (startSafely(context, intent)) return true
        }

        // Android 8 以下（以及少数砍掉上面 action 的 ROM）走应用详情页
        val appDetails = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startSafely(context, appDetails)
    }

    /** 通知总开关当前是否放行（渠道级静音不在此列） */
    fun areNotificationsEnabled(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun startSafely(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No activity for ${intent.action}: ${e.message}")
            false
        } catch (e: SecurityException) {
            // 个别 ROM 会对 REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 做权限校验
            Log.w(TAG, "Denied for ${intent.action}: ${e.message}")
            false
        }
    }
}
