package com.yhchat.canary.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 网络状态工具：统一使用系统 [ConnectivityManager] 判断当前是否真的能联网。
 *
 * 之所以不直接用抛出异常的请求来判断，是因为「有没有网」会影响 UI 策略
 * （联网时以服务器数据为准，没网时才回退到本地缓存）。
 */
object NetworkUtils {

    private fun connectivityManager(context: Context): ConnectivityManager? =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    /**
     * 当前是否有可用网络。
     *
     * 同时要求 [NetworkCapabilities.NET_CAPABILITY_INTERNET]（网络声明可以上网）和
     * [NetworkCapabilities.NET_CAPABILITY_VALIDATED]（系统实际校验过能访问外网），
     * 避免连上了 WiFi 但其实是需要登录的认证页 / 无外网的情况。
     *
     * 极少数机型拿不到 ConnectivityManager 时返回 true，让上层照常发请求。
     */
    fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val cm = connectivityManager(context) ?: return true
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (e: Exception) {
            // 查询失败时按「有网」处理，让上层照常发请求（失败后会回退到缓存）
            true
        }
    }

    /**
     * 监听网络可用状态：先回调一次当前状态，之后网络变化时继续回调，已做去重。
     */
    fun networkAvailabilityFlow(context: Context): Flow<Boolean> = callbackFlow {
        val cm = connectivityManager(context)
        if (cm == null) {
            trySend(true)
            awaitClose { }
            return@callbackFlow
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(isNetworkAvailable(context))
            }

            override fun onLost(network: Network) {
                trySend(isNetworkAvailable(context))
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                trySend(isNetworkAvailable(context))
            }
        }

        trySend(isNetworkAvailable(context))
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                cm.registerDefaultNetworkCallback(callback)
            } else {
                cm.registerNetworkCallback(
                    NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build(),
                    callback
                )
            }
        } catch (e: Exception) {
            // 注册失败时保留当前状态，不影响调用方
        }

        awaitClose {
            try {
                cm.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
                // 忽略
            }
        }
    }.distinctUntilChanged()
}
