package com.yhchat.canary.ui.base

import android.app.Activity
import android.graphics.Color
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.graphics.Color as ComposeColor

/**
 * 系统导航栏工具类
 * 用于统一设置系统导航栏颜色和手势线适配
 */
object SystemBarUtils {
    @Suppress("DEPRECATION")
    fun transparentNavigationBar(window: android.view.Window) {
        // Transparency alone only reveals the window background when decor still fits
        // system bars. Let the app draw underneath; controls keep their own inset padding.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.navigationBarDividerColor = Color.TRANSPARENT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }

    /** Covers activities that do not inherit BaseActivity, including library activities. */
    fun installNavigationBarPolicy(application: android.app.Application) {
        application.registerActivityLifecycleCallbacks(object : android.app.Application.ActivityLifecycleCallbacks {
            private fun apply(activity: Activity) {
                transparentNavigationBar(activity.window)
                activity.window.decorView.post {
                    if (!activity.isDestroyed) transparentNavigationBar(activity.window)
                }
            }
            override fun onActivityCreated(activity: Activity, state: android.os.Bundle?) = apply(activity)
            override fun onActivityResumed(activity: Activity) = apply(activity)
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: android.os.Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    /**
     * 设置系统导航栏为透明，并适配深色/浅色主题
     */
    @Suppress("DEPRECATION")
    fun setupTransparentSystemBars(activity: Activity) {
        // 全局开启沉浸式，系统手势条区域透明
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        activity.window.statusBarColor = Color.TRANSPARENT
        transparentNavigationBar(activity.window)
    }
    
    /**
     * Compose 版本：设置系统导航栏颜色，让手势线自动适配应用主题
     */
    @Composable
    @Suppress("DEPRECATION")
    fun SetSystemNavigationBarColor(activity: Activity) {
        // 导航栏背景设为透明，让手势条区域完全透明
        val navigationBarColor = androidx.compose.ui.graphics.Color.Transparent
        val isLightTheme = !isSystemInDarkTheme()
        
        SideEffect {
            val window = activity.window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            // 设置系统导航栏背景色
            window.navigationBarColor = navigationBarColor.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.navigationBarDividerColor = Color.TRANSPARENT
            }

            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController?.apply {
                isAppearanceLightNavigationBars = isLightTheme
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }

            // Android 8.0+ 旧版设备仍需要 systemUiVisibility 兜底
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = if (isLightTheme) {
                    window.decorView.systemUiVisibility or
                        android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                } else {
                    window.decorView.systemUiVisibility and
                        android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                }
            }

            // Android 10+ 禁用系统强制对比度，完全使用我们设置的颜色
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
        }
    }

    @Composable
    @Suppress("DEPRECATION")
    fun ApplyNavigationBarColor(activity: Activity, color: ComposeColor, darkIcons: Boolean) {
        val isLightTheme = !isSystemInDarkTheme()

        SideEffect {
            val window = activity.window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            transparentNavigationBar(window)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.navigationBarDividerColor = Color.TRANSPARENT
            }

            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController?.apply {
                isAppearanceLightNavigationBars = darkIcons
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = if (darkIcons) {
                    window.decorView.systemUiVisibility or
                        android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                } else {
                    window.decorView.systemUiVisibility and
                        android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.setSystemBarsAppearance(
                    if (darkIcons)
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    else
                        0,
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                )
            }
        }

        DisposableEffect(color, darkIcons) {
            onDispose {
                val window = activity.window
                window.navigationBarColor = ComposeColor.Transparent.toArgb()

                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController?.isAppearanceLightNavigationBars = isLightTheme

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    @Suppress("DEPRECATION")
                    window.decorView.systemUiVisibility = if (isLightTheme) {
                        window.decorView.systemUiVisibility or
                            android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                    } else {
                        window.decorView.systemUiVisibility and
                            android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.insetsController?.setSystemBarsAppearance(
                        if (isLightTheme)
                            android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                        else
                            0,
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    )
                }
            }
        }
    }
}
