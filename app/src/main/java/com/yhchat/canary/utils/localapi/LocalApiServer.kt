package com.yhchat.canary.utils.localapi

import android.content.Context
import android.util.Log
import com.yhchat.canary.data.local.LocalApiSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 本地 API 服务的生命周期管理。
 *
 * 单例：整个 App 只可能有一个监听端口，多个实例会抢端口。
 */
object LocalApiServer {

    private const val TAG = "LocalApiServer"

    @Volatile
    private var server: LocalHttpServer? = null

    @Volatile
    private var lastError: String? = null

    /** 是否正在运行 */
    val isRunning: Boolean get() = server?.isRunning == true

    /** 当前监听地址，未运行时返回 null */
    val baseUrl: String?
        get() {
            val instance = server ?: return null
            if (!instance.isRunning) return null
            val host = if (LocalApiSettings.isLocalhostOnly(appContextRef ?: return null)) {
                "127.0.0.1"
            } else {
                "0.0.0.0"
            }
            return "http://$host:${LocalApiSettings.port(appContextRef!!)}"
        }

    /** 上一次启动失败的原因（端口占用等），用于界面上提示 */
    val error: String? get() = lastError

    private var appContextRef: Context? = null

    /**
     * 按当前设置启动服务。
     *
     * @return 成功或失败，界面据此提示
     */
    fun start(context: Context): Result<Unit> {
        val appContext = context.applicationContext
        appContextRef = appContext

        if (isRunning) return Result.success(Unit)

        val port = LocalApiSettings.port(appContext)
        // 默认只绑本机；用户明确放开才绑 0.0.0.0
        val host = if (LocalApiSettings.isLocalhostOnly(appContext)) "127.0.0.1" else "0.0.0.0"

        val router = LocalApiRouter(appContext)
        val instance = LocalHttpServer(host = host, port = port) { request ->
            router.route(request)
        }

        return instance.start()
            .onSuccess {
                server = instance
                lastError = null
                LocalApiSettings.setEnabled(appContext, true)
                Log.i(TAG, "本地 API 已启动: http://$host:$port")
            }
            .onFailure { e ->
                server = null
                lastError = e.message ?: "启动失败"
                LocalApiSettings.setEnabled(appContext, false)
                Log.e(TAG, "本地 API 启动失败", e)
            }
    }

    fun stop(context: Context) {
        val appContext = context.applicationContext
        server?.stop()
        server = null
        lastError = null
        LocalApiSettings.setEnabled(appContext, false)
    }

    /**
     * 切换端口：先停再按新端口启动。
     *
     * 注意这是**阻塞**操作（bind 端口会等），必须在后台线程调用。
     * 界面侧用 [restartAsync] 更安全。
     */
    fun restart(context: Context, newPort: Int): Result<Unit> {
        val appContext = context.applicationContext
        val wasRunning = isRunning
        server?.stop()
        server = null
        LocalApiSettings.setPort(appContext, newPort)
        return if (wasRunning || LocalApiSettings.isEnabled(appContext)) {
            start(appContext)
        } else {
            Result.success(Unit)
        }
    }

    /**
     * 后台线程执行 [restart]，结果回到调用方。
     *
     * 界面里改端口必须走这个 —— 直接在 Compose 的 onClick 里调 [restart]
     * 会阻塞主线程（bind 是同步的系统调用），换端口时界面会卡一下。
     */
    suspend fun restartAsync(context: Context, newPort: Int): Result<Unit> =
        withContext(Dispatchers.IO) { restart(context, newPort) }

    /** 后台线程执行 [start] */
    suspend fun startAsync(context: Context): Result<Unit> =
        withContext(Dispatchers.IO) { start(context) }

    /**
     * App 启动时按上次的开关状态自动恢复。
     *
     * 用户上次开着，重启后应该继续开着 —— 否则 agent 会莫名其妙连不上。
     */
    fun restoreIfEnabled(context: Context) {
        val appContext = context.applicationContext
        appContextRef = appContext
        if (LocalApiSettings.isEnabled(appContext) && !isRunning) {
            start(appContext)
        }
    }
}
