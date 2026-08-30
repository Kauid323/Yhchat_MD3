package com.yhchat.canary.ncm

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log

/**
 * 网易云音乐账号管理
 * 负责网页登录 Cookie 的持久化存储、读取以及全量 Cookie 请求头构造
 */
object NcmAccountManager {

    private const val TAG = "NcmAccountManager"
    private const val PREFS_NAME = "ncm_account_prefs"
    private const val KEY_COOKIE = "ncm_cookie"
    private const val KEY_LOGIN_TIME = "ncm_login_time"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 保存网易云 Cookie
     */
    fun saveCookie(context: Context, cookie: String) {
        if (cookie.isBlank()) return
        val currentCookie = getCookie(context)
        // 合并 Cookie 键值对，避免覆盖已有的其他字段
        val mergedCookie = mergeCookies(currentCookie, cookie)
        getPrefs(context).edit()
            .putString(KEY_COOKIE, mergedCookie)
            .putLong(KEY_LOGIN_TIME, System.currentTimeMillis())
            .apply()
        Log.d(TAG, "网易云 Cookie 已持久化保存, 长度=${mergedCookie.length}")
    }

    /**
     * 获取已保存的 Cookie
     */
    fun getCookie(context: Context): String? {
        return getPrefs(context).getString(KEY_COOKIE, null)?.takeIf { it.isNotBlank() }
    }

    /**
     * 是否已登录网易云
     */
    fun isLoggedIn(context: Context): Boolean {
        val cookie = getCookie(context) ?: return false
        // MUSIC_U 是网易云核心登录凭证，__csrf 为跨站凭证
        return cookie.contains("MUSIC_U") || cookie.contains("__csrf")
    }

    /**
     * 清除登录状态与 Cookie
     */
    fun clearCookie(context: Context) {
        getPrefs(context).edit().clear().apply()
        Log.d(TAG, "网易云 Cookie 已清除")
    }

    /**
     * 构造带默认客户端标识的完整 Cookie 字符串
     * 规范：versioncode=8010060; EVNSM=1.0.0; osver={安卓版本}; packageType=release; ntes_kaola_ad=1; mobilename={手机型号}; os=android; channel=netease;
     */
    fun buildFullCookie(context: Context): String {
        val savedCookie = getCookie(context).orEmpty()
        val defaultFields = "versioncode=8010060; EVNSM=1.0.0; osver=${Build.VERSION.RELEASE}; packageType=release; ntes_kaola_ad=1; mobilename=${Build.MODEL}; os=android; channel=netease;"
        return if (savedCookie.isBlank()) {
            defaultFields
        } else {
            mergeCookies(defaultFields, savedCookie)
        }
    }

    /**
     * 合并两个 Cookie 字符串，按 key 覆盖
     */
    private fun mergeCookies(oldCookie: String?, newCookie: String): String {
        val cookieMap = mutableMapOf<String, String>()

        fun parseIntoMap(cookieStr: String?) {
            if (cookieStr.isNullOrBlank()) return
            val pairs = cookieStr.split(";")
            for (pair in pairs) {
                val trimmed = pair.trim()
                if (trimmed.isEmpty()) continue
                val eqIdx = trimmed.indexOf('=')
                if (eqIdx > 0) {
                    val key = trimmed.substring(0, eqIdx).trim()
                    val value = trimmed.substring(eqIdx + 1).trim()
                    cookieMap[key] = value
                }
            }
        }

        parseIntoMap(oldCookie)
        parseIntoMap(newCookie)

        return cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }
}
