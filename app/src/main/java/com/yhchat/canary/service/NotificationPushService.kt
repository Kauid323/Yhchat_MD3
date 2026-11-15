package com.yhchat.canary.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.yhchat.canary.MainActivity
import com.yhchat.canary.R
import com.yhchat.canary.data.di.RepositoryFactory
import com.yhchat.canary.data.websocket.ConnectionState
import com.yhchat.canary.data.websocket.WebSocketService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 常驻通知服务：把 WebSocket 连接挂在前台服务上。
 *
 * 为什么需要它：现在的 WS 只是 ConversationScreen 里的一个 LaunchedEffect 拉起来的，
 * 用户不在会话页（或者根本没打开过 App）连接就不存在，息屏后进程一被回收，
 * 推送就彻底断了 —— 通知自然收不到。
 *
 * 这里不重复实现连接逻辑，只是「占用一个前台服务 + 拉起同一个 WebSocketService 单例」，
 * 系统要回收进程时至少得先考虑这个前台服务。
 *
 * foregroundServiceType 用 specialUse 而不是 dataSync：
 * targetSdk 35+ 禁止从 BOOT_COMPLETED 启动 dataSync 类型的前台服务，
 * 开机自启会直接抛 ForegroundServiceStartNotAllowedException。
 */
class NotificationPushService : Service() {

    companion object {
        private const val TAG = "NotificationPushService"
        private const val CHANNEL_ID = "notification_service"
        private const val NOTIFICATION_ID = 3001

        private const val ACTION_START = "com.yhchat.canary.action.NOTIFICATION_SERVICE_START"
        private const val ACTION_STOP = "com.yhchat.canary.action.NOTIFICATION_SERVICE_STOP"

        /** 用户是否允许常驻服务（设置里那个开关），默认开 */
        const val PREFS_NAME = "notification_settings"
        const val KEY_AUTO_START = "notification_service_auto_start"

        /**
         * 服务当前是否活着。
         *
         * 用来避免「没在跑也发一条 startService(STOP)」——那会把服务创建出来再立刻
         * 销毁一次，日志里多两行没意义的生命周期，也会让刚退登的用户看到通知闪一下。
         */
        @Volatile
        var isRunning: Boolean = false
            private set

        /** 开机自启总开关的当前值；没写过就按默认开处理 */
        fun isAutoStartEnabled(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_AUTO_START, true)
        }

        /**
         * 启动常驻服务。
         *
         * 用 ContextCompat.startForegroundService：Android 8+ 必须走这个入口，
         * 否则 onStartCommand 里一 startForeground 就 ANR/崩溃。
         */
        fun start(context: Context) {
            val intent = Intent(context, NotificationPushService::class.java).apply {
                action = ACTION_START
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                // 后台被系统限制时会抛 ForegroundServiceStartNotAllowedException，
                // 开机广播里的启动因此可能失败；这里不拦，交给下次前台启动再补
                Log.w(TAG, "start failed: ${e.javaClass.simpleName} ${e.message}")
            }
        }

        fun stop(context: Context) {
            if (!isRunning) return
            val intent = Intent(context, NotificationPushService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "stop failed: ${e.message}")
            }
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WsEntryPoint {
        fun webSocketService(): WebSocketService
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var stateJob: Job? = null
    private var connectionText: String = "正在连接…"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 必须在 5 秒内 startForeground，否则系统直接抛 ANR/RemoteServiceException。
        // 所以先挂通知，再判断是要停还是要连 —— 哪怕是停止指令，也得先走一遍
        // startForeground，否则 startService 起来的这个实例会因为"没进前台"被判违规。
        startForegroundCompat()

        if (intent?.action == ACTION_STOP) {
            Log.d(TAG, "收到停止指令")
            stopSelf()
            return START_NOT_STICKY
        }

        val userId = resolveUserId()
        if (userId.isNullOrBlank()) {
            Log.w(TAG, "没有登录用户，常驻服务不建立连接")
            updateNotification("未登录")
            return START_STICKY
        }

        connectWebSocket(userId)
        return START_STICKY
    }

    /**
     * 从最近任务里划掉 App 时不要跟着死：返回 START_STICKY 让系统按需重建。
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "任务被移除，尝试保持常驻")
    }

    override fun onDestroy() {
        isRunning = false
        stateJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        // 用两参数版本，类型由清单里的 foregroundServiceType 决定。
        // 三参数版本要写死一个 type 常量，而 specialUse 是 API 34 才有的值，
        // 在 29~33 上塞进去会被判成「清单里没声明这个类型」直接崩。
        //
        // try/catch：Android 12+ 在应用处于后台时调 startForeground 会抛
        // ForegroundServiceStartNotAllowedException。真抛了说明这次拉起本来就不被允许，
        // 直接停掉比留一个没有前台通知、随时会被杀的裸服务干净。
        try {
            startForeground(NOTIFICATION_ID, buildNotification(connectionText))
        } catch (e: Exception) {
            Log.w(TAG, "startForeground 被拒: ${e.javaClass.simpleName} ${e.message}")
            stopSelf()
        }
    }

    private fun resolveUserId(): String? {
        return try {
            val tokenRepository = RepositoryFactory.getTokenRepository(this)
            tokenRepository.getUserIdSync()
                ?: RepositoryFactory.getAccountRepository(this).getCurrentUserId()
        } catch (e: Exception) {
            Log.e(TAG, "读取用户ID失败", e)
            null
        }
    }

    private fun connectWebSocket(userId: String) {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            WsEntryPoint::class.java
        )
        val webSocketService = entryPoint.webSocketService()

        serviceScope.launch {
            // connect 内部已经做了去重（isConnected / isConnecting），
            // 和 ConversationScreen 同时调用也不会开两条连接
            runCatching { webSocketService.connect(userId, "android") }
                .onFailure { Log.w(TAG, "连接 WebSocket 失败: ${it.message}") }
        }

        if (stateJob?.isActive != true) {
            stateJob = serviceScope.launch {
                webSocketService.connectionState.collect { state ->
                    connectionText = when (state) {
                        ConnectionState.Connected -> "消息推送已连接"
                        ConnectionState.Connecting -> "正在连接…"
                        ConnectionState.Disconnected -> "连接已断开，等待重连"
                        is ConnectionState.Error -> "连接异常：${state.message}"
                    }
                    updateNotification(connectionText)
                }
            }
        }
    }

    private fun buildNotification(text: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("消息推送")
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            // 常驻通知不该占着重要位置，LOW 让它缩在通知栏底部
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        runCatching { manager.notify(NOTIFICATION_ID, buildNotification(text)) }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "消息推送服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "保持消息推送连接时显示的常驻通知"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }
}
