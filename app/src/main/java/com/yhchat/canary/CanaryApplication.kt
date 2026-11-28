package com.yhchat.canary

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Process
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.yhchat.canary.crash.CrashHandler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

@HiltAndroidApp
class CanaryApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
        com.yhchat.canary.ui.base.SystemBarUtils.installNavigationBarPolicy(this)
        AppForegroundTracker.init()
        startMemoryAutoCleanLoop()

        // 上次开着本地 API 的话，重启后自动恢复。
        // 放到后台线程做：绑定端口是阻塞操作，不能在 onCreate 里卡主线程。
        appScope.launch {
            runCatching {
                com.yhchat.canary.utils.localapi.LocalApiServer.restoreIfEnabled(this@CanaryApplication)
            }
        }
    }

    private fun startMemoryAutoCleanLoop() {
        appScope.launch {
            while (isActive) {
                runCatching {
                    val prefs = getSharedPreferences("display_settings", Context.MODE_PRIVATE)
                    val enabled = prefs.getBoolean("memory_auto_clean_enabled", false)
                    if (!enabled) return@runCatching

                    val thresholdValue = prefs.getFloat("memory_auto_clean_threshold_value", 512f)
                    val unit = prefs.getString("memory_auto_clean_threshold_unit", "MB") ?: "MB"
                    val thresholdBytes = when (unit.uppercase()) {
                        "GB" -> (thresholdValue * 1024f * 1024f * 1024f).toLong()
                        else -> (thresholdValue * 1024f * 1024f).toLong()
                    }
                    if (thresholdBytes <= 0L) return@runCatching

                    val currentBytes = getCurrentProcessPssBytes()
                    if (currentBytes > thresholdBytes) {
                        clearAppCacheDir(cacheDir)
                    }
                }

                delay(60_000L)
            }
        }
    }

    private fun getCurrentProcessPssBytes(): Long {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val pid = Process.myPid()
        val memInfo = am.getProcessMemoryInfo(intArrayOf(pid))
        val pssKb = memInfo.firstOrNull()?.totalPss?.toLong() ?: 0L
        return pssKb * 1024L
    }

    private fun clearAppCacheDir(dir: File) {
        dir.listFiles()?.forEach { file ->
            runCatching {
                file.deleteRecursively()
            }
        }
    }
}

object AppForegroundTracker {
    @Volatile
    var isInForeground: Boolean = false
        private set

    @Volatile
    private var initialized: Boolean = false

    fun init() {
        if (initialized) return
        initialized = true

        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    isInForeground = true
                }

                override fun onStop(owner: LifecycleOwner) {
                    isInForeground = false
                }
            }
        )
    }
}
