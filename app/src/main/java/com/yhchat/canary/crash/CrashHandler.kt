package com.yhchat.canary.crash

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import com.yhchat.canary.ui.crash.CrashReportActivity
import kotlin.system.exitProcess

object CrashHandler {
    @Volatile
    private var installed = false

    @Volatile
    private var visibleActivityCount = 0

    /**
     * 崩溃页运行在独立进程（AndroidManifest 中声明的 :crash）中，
     * 主进程被杀掉后它依然存活，因此崩溃日志可以在崩溃的瞬间直接显示，不需要重启应用。
     */

    /** 等系统把崩溃页拉起来之后再结束主进程，避免启动请求被取消。 */
    private const val KILL_DELAY_MS = 400L

    fun install(context: Context) {
        if (installed) return
        installed = true

        val appContext = context.applicationContext
        (appContext as? Application)?.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: android.app.Activity) {
                    if (activity !is CrashReportActivity) {
                        visibleActivityCount++
                    }
                }

                override fun onActivityStopped(activity: android.app.Activity) {
                    if (activity !is CrashReportActivity) {
                        visibleActivityCount = (visibleActivityCount - 1).coerceAtLeast(0)
                    }
                }

                override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityResumed(activity: android.app.Activity) = Unit
                override fun onActivityPaused(activity: android.app.Activity) = Unit
                override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: android.app.Activity) = Unit
            }
        )

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val reportPath = if (shouldShowCrashActivity(throwable)) {
                runCatching {
                    CrashReportStore.writeCrashReport(appContext, thread, throwable).absolutePath
                }.getOrNull()
            } else {
                null
            }

            if (reportPath == null) {
                previousHandler?.uncaughtException(thread, throwable)
                return@setDefaultUncaughtExceptionHandler
            }

            launchCrashActivity(appContext, reportPath)
            killCurrentProcessDelayed()
        }
    }

    private fun shouldShowCrashActivity(throwable: Throwable): Boolean {
        // 崩溃页自身出问题时不要再弹一次，避免死循环。
        if (isCrashScreenFailure(throwable)) return false
        // 应用处于前台时才有权限直接拉起崩溃页（Android 10+ 后台启动限制）。
        return visibleActivityCount > 0
    }

    private fun isCrashScreenFailure(throwable: Throwable): Boolean {
        var current: Throwable? = throwable
        var depth = 0
        while (current != null && depth < 16) {
            if (current.stackTrace.any { it.className == CrashReportActivity::class.java.name }) return true
            current = current.cause
            depth++
        }
        return false
    }

    fun showCaughtException(context: Context, throwable: Throwable) {
        val appContext = context.applicationContext
        val reportFile = CrashReportStore.writeCrashReport(
            context = appContext,
            thread = Thread.currentThread(),
            throwable = throwable
        )
        val starter = {
            launchCrashActivity(appContext, reportFile.absolutePath)
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            starter()
        } else {
            Handler(Looper.getMainLooper()).post {
                starter()
            }
        }
    }

    private fun launchCrashActivity(context: Context, reportPath: String) {
        val intent = CrashReportActivity.createIntent(context, reportPath).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
        }
        runCatching { context.startActivity(intent) }
            .onFailure { android.util.Log.e("CrashHandler", "启动崩溃页失败", it) }
    }

    private fun killCurrentProcessDelayed() {
        Thread {
            runCatching { Thread.sleep(KILL_DELAY_MS) }
            Process.killProcess(Process.myPid())
            exitProcess(10)
        }.apply {
            name = "crash-process-killer"
            isDaemon = true
        }.start()
    }
}
