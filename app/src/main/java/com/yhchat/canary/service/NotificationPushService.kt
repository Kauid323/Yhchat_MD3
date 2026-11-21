package com.yhchat.canary.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.yhchat.canary.MainActivity
import com.yhchat.canary.R
import com.yhchat.canary.data.di.RepositoryFactory
import com.yhchat.canary.data.websocket.ConnectionState
import com.yhchat.canary.data.websocket.WebSocketManager
import com.yhchat.canary.data.websocket.WebSocketService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 常驻服务管理连接恢复；通知仍由同一个 WebSocketService 单例产生。 */
class NotificationPushService : Service() {
    companion object {
        private const val TAG = "NotificationPushService"
        private const val CHANNEL_ID = "notification_service"
        private const val NOTIFICATION_ID = 3001
        private const val ACTION_START = "com.yhchat.canary.action.NOTIFICATION_SERVICE_START"
        private const val ACTION_STOP = "com.yhchat.canary.action.NOTIFICATION_SERVICE_STOP"
        const val PREFS_NAME = "notification_settings"
        const val KEY_AUTO_START = "notification_service_auto_start"

        @Volatile var isRunning: Boolean = false
            private set

        fun isAutoStartEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_AUTO_START, true)

        private fun isWebSocketEnabled(context: Context): Boolean =
            !context.getSharedPreferences("display_settings", Context.MODE_PRIVATE)
                .getBoolean("disable_websocket", false)

        fun start(context: Context) {
            if (!isAutoStartEnabled(context) || !isWebSocketEnabled(context)) return
            try {
                ContextCompat.startForegroundService(context,
                    Intent(context, NotificationPushService::class.java).setAction(ACTION_START))
            } catch (e: Exception) {
                Log.w(TAG, "前台服务启动被拒：${e.message}")
            }
        }

        fun stop(context: Context) {
            // stopService 不会为了发送 STOP 而把已经停止的服务重新创建出来。
            context.stopService(Intent(context, NotificationPushService::class.java))
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WsEntryPoint {
        fun webSocketService(): WebSocketService
        fun webSocketManager(): WebSocketManager
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val socketService: WebSocketService by lazy {
        val entryPoint = EntryPointAccessors.fromApplication(applicationContext, WsEntryPoint::class.java)
        // 系统仅重建服务、尚未打开界面时，也要创建负责消息落库的监听器。
        entryPoint.webSocketManager()
        entryPoint.webSocketService()
    }
    private val connectivity by lazy {
        getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    private var stateJob: Job? = null
    private var healthJob: Job? = null
    private var callbackRegistered = false
    private var currentNetwork: Network? = null
    private var connectionText = "正在连接…"

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            serviceScope.launch {
                val changed = currentNetwork != null && currentNetwork != network
                currentNetwork = network
                requestConnection(changed)
            }
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                serviceScope.launch {
                    if (network == connectivity.activeNetwork) requestConnection()
                }
            }
        }

        override fun onLost(network: Network) {
            serviceScope.launch {
                if (currentNetwork == network) {
                    currentNetwork = null
                    updateNotification("网络已断开，恢复网络后自动重连")
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForeground 失败必须终止启动流程，不能继续假装服务已常驻。
        try {
            startForeground(NOTIFICATION_ID, buildNotification(connectionText))
        } catch (e: Exception) {
            Log.w(TAG, "无法进入前台：${e.message}")
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_STOP || !isAutoStartEnabled(this) || !isWebSocketEnabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (resolveUserId().isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        isRunning = true
        observeConnection()
        registerNetworkCallback()
        requestConnection()
        if (healthJob?.isActive != true) {
            healthJob = serviceScope.launch {
                while (isActive) {
                    delay(30_000L)
                    // 不持有永久唤醒锁；设备恢复运行后检查息屏期间留下的半开连接。
                    requestConnection()
                }
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // 划掉最近任务不关闭共享连接；强行停止应用则必须尊重系统停止语义。
        requestConnection()
    }

    override fun onDestroy() {
        isRunning = false
        if (callbackRegistered) runCatching { connectivity.unregisterNetworkCallback(networkCallback) }
        callbackRegistered = false
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun resolveUserId(): String? = runCatching {
        RepositoryFactory.getTokenRepository(this).getUserIdSync()?.takeIf { it.isNotBlank() }
            ?: RepositoryFactory.getAccountRepository(this).getCurrentUserId()
    }.getOrElse {
        Log.w(TAG, "读取登录用户失败", it)
        null
    }

    private fun requestConnection(networkChanged: Boolean = false) {
        serviceScope.launch(Dispatchers.IO) {
            if (!isAutoStartEnabled(this@NotificationPushService) || !isWebSocketEnabled(this@NotificationPushService)) {
                stopSelf()
                return@launch
            }
            val userId = resolveUserId()?.takeIf { it.isNotBlank() } ?: run {
                stopSelf()
                return@launch
            }
            if (connectivity.activeNetwork == null) {
                updateNotification("等待网络恢复")
                return@launch
            }
            try {
                socketService.ensureConnected(userId, "android", networkChanged)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "恢复推送连接失败", e)
                updateNotification("连接失败，将自动重试")
            }
        }
    }

    private fun observeConnection() {
        if (stateJob?.isActive == true) return
        stateJob = serviceScope.launch {
            socketService.connectionState.collect { state ->
                updateNotification(when (state) {
                    ConnectionState.Connected -> "消息推送已连接"
                    ConnectionState.Connecting -> "正在连接…"
                    ConnectionState.Disconnected -> "连接已断开，等待重连"
                    is ConnectionState.Error -> "连接异常：${state.message}"
                })
            }
        }
    }

    private fun registerNetworkCallback() {
        if (callbackRegistered) return
        currentNetwork = connectivity.activeNetwork
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connectivity.registerDefaultNetworkCallback(networkCallback)
            } else {
                connectivity.registerNetworkCallback(
                    NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
                    networkCallback)
            }
            callbackRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "网络监听注册失败，保留定期连接检查", e)
        }
    }

    private fun buildNotification(text: String): Notification {
        val contentIntent = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("消息推送")
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        connectionText = text
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        runCatching { manager.notify(NOTIFICATION_ID, buildNotification(text)) }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "消息推送服务", NotificationManager.IMPORTANCE_LOW).apply {
            description = "保持消息连接，并在网络恢复后自动重连"
            setShowBadge(false)
        })
    }
}
