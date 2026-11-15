package com.yhchat.canary.data.local

import android.content.Context
import android.content.SharedPreferences

/**
 * 本地 API 服务器的配置。
 *
 * 设计原则：**默认关闭**。这是一个把账号数据暴露到局域网的开关，
 * 不该在用户没明确同意的情况下自己开着。
 */
object LocalApiSettings {

    private const val PREFS = "local_api_settings"

    /** 总开关 */
    const val KEY_ENABLED = "enabled"
    /** 监听端口 */
    const val KEY_PORT = "port"
    /** 是否只允许本机访问（默认开，局域网访问要用户手动放开） */
    const val KEY_LOCALHOST_ONLY = "localhost_only"

    /** 默认端口。挑一个不常用的，避免和其他开发服务撞车 */
    const val DEFAULT_PORT = 8848

    /** 端口可选范围（1024 以下需要 root） */
    val PORT_RANGE = 1024..65535

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun port(context: Context): Int =
        prefs(context).getInt(KEY_PORT, DEFAULT_PORT).coerceIn(PORT_RANGE)

    fun setPort(context: Context, port: Int) {
        prefs(context).edit().putInt(KEY_PORT, port.coerceIn(PORT_RANGE)).apply()
    }

    /**
     * 是否仅本机可访问。
     *
     * 默认 true：绑 127.0.0.1。网关/手机里的 agent 用 127.0.0.1 就够了，
     * 不需要把账号数据暴露到整个局域网。
     */
    fun isLocalhostOnly(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LOCALHOST_ONLY, true)

    fun setLocalhostOnly(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_LOCALHOST_ONLY, value).apply()
    }
}
