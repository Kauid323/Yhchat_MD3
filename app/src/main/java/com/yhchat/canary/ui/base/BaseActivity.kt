package com.yhchat.canary.ui.base

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat

/**
 * 基础Activity类
 * 统一处理字体大小设置和其他全局配置
 */
abstract class BaseActivity : ComponentActivity() {
    
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        // 在super.onCreate之前应用字体设置
        applyFontScale()
        super.onCreate(savedInstanceState)

        // 全局开启沉浸式，系统手势条区域透明
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        SystemBarUtils.transparentNavigationBar(window)
    }
    
    /**
     * 应用字体大小设置
     */
    @Suppress("DEPRECATION")
    private fun applyFontScale() {
        val prefs = getSharedPreferences("display_settings", Context.MODE_PRIVATE)
        val fontScale = prefs.getFloat("font_scale", 100f)
        
        // 将百分比转换为系统字体缩放因子 (1-100% -> 0.01-1.0)
        val scaleFactor = fontScale / 100f
        
        // 应用字体缩放
        val configuration = resources.configuration
        configuration.fontScale = scaleFactor
        resources.updateConfiguration(configuration, resources.displayMetrics)
    }
    
    override fun onResume() {
        super.onResume()
        // 在Activity恢复时重新应用字体设置，以防用户在设置中更改了字体大小
        applyFontScale()
    }
}
