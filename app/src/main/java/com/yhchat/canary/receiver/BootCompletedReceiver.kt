package com.yhchat.canary.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.yhchat.canary.data.local.SecureTokenStorage
import com.yhchat.canary.service.NotificationPushService

/**
 * 开机自启：把消息推送的常驻服务重新拉起来。
 *
 * 只处理 BOOT_COMPLETED / MY_PACKAGE_REPLACED。
 * 没有登录态的时候拉起来也没意义，反而白占一个前台服务，
 * 所以先看 SecureTokenStorage 里有没有 token。
 */
class BootCompletedReceiver : BroadcastReceiver() {

    private val tag = "BootCompletedReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        if (!NotificationPushService.isAutoStartEnabled(context)) {
            Log.d(tag, "常驻服务自启已被用户关闭，跳过")
            return
        }

        // 没登录就别起了：服务里拿不到 userId 也只会停在"未登录"上
        val hasToken = try {
            SecureTokenStorage.getInstance(context).hasValidToken()
        } catch (e: Exception) {
            Log.w(tag, "读取登录态失败: ${e.message}")
            false
        }
        if (!hasToken) {
            Log.d(tag, "没有登录态，跳过自启")
            return
        }

        Log.d(tag, "开机广播 $action，拉起消息推送服务")
        NotificationPushService.start(context)
    }
}
