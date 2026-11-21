package com.yhchat.canary.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.yhchat.canary.data.model.NavigationConfig
import com.yhchat.canary.data.model.NavigationItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 导航配置数据仓库
 */
@Singleton
class NavigationRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences("navigation_config", Context.MODE_PRIVATE)
    
    private val gson = Gson()
    
    private val _navigationConfig = MutableStateFlow(loadNavigationConfig())
    val navigationConfig: StateFlow<NavigationConfig> = _navigationConfig.asStateFlow()
    
    companion object {
        private const val KEY_NAVIGATION_CONFIG = "navigation_config"
    }
    
    /**
     * 加载导航配置
     */
    private fun loadNavigationConfig(): NavigationConfig {
        val configJson = sharedPreferences.getString(KEY_NAVIGATION_CONFIG, null)
        val config = if (configJson != null) {
            try {
                gson.fromJson(configJson, NavigationConfig::class.java)
                    ?: NavigationConfig.getDefault()
            } catch (e: Exception) {
                NavigationConfig.getDefault()
            }
        } else {
            NavigationConfig.getDefault()
        }

        // 本地已经落过配置的设备不会走 getDefault()，旧文案会一直留在本地，
        // 所以加载时纠正一次并回写，避免每次启动重复迁移。
        val migrated = migrateLegacyTitles(config)
        if (migrated != config) {
            sharedPreferences.edit()
                .putString(KEY_NAVIGATION_CONFIG, gson.toJson(migrated))
                .apply()
        }
        return migrated
    }

    /**
     * 修正历史配置里的旧文案。
     *
     * 早期版本把底部导航的「社区」写成了「社群」。导航项名称不支持用户自定义，
     * 因此这里按 id 直接纠正名称，顺序、可见性等其余字段保持用户原有配置不变。
     */
    private fun migrateLegacyTitles(config: NavigationConfig): NavigationConfig {
        val migratedItems = config.items.map { item ->
            if (item.id == "community" && (item.name == "社群" || item.title == "社群")) {
                item.copy(name = "社区", title = "社区")
            } else {
                item
            }
        }
        return if (migratedItems == config.items) config else config.copy(items = migratedItems)
    }
    
    /**
     * 保存导航配置
     */
    fun saveNavigationConfig(config: NavigationConfig) {
        val configJson = gson.toJson(config)
        sharedPreferences.edit()
            .putString(KEY_NAVIGATION_CONFIG, configJson)
            .apply()
        
        _navigationConfig.value = config
    }
    
    /**
     * 更新导航项的可见性
     */
    fun updateItemVisibility(itemId: String, isVisible: Boolean) {
        val currentConfig = _navigationConfig.value
        val updatedItems = currentConfig.items.map { item ->
            if (item.id == itemId) {
                item.copy(isVisible = isVisible)
            } else {
                item
            }
        }
        
        val updatedConfig = currentConfig.copy(items = updatedItems)
        saveNavigationConfig(updatedConfig)
    }
    
    /**
     * 更新导航项的顺序
     */
    fun updateItemsOrder(orderedItems: List<NavigationItem>) {
        val updatedItems = orderedItems.mapIndexed { index, item ->
            item.copy(order = index)
        }
        
        val currentConfig = _navigationConfig.value
        val updatedConfig = currentConfig.copy(items = updatedItems)
        saveNavigationConfig(updatedConfig)
    }
    
    /**
     * 重置为默认配置
     */
    fun resetToDefault() {
        saveNavigationConfig(NavigationConfig.getDefault())
    }
    
    /**
     * 获取可见的导航项
     */
    fun getVisibleItems(): List<NavigationItem> {
        return _navigationConfig.value.items.filter { it.isVisible }.sortedBy { it.order }
    }
    
    /**
     * 获取所有可用的导航项
     */
    fun getAllAvailableItems(): List<NavigationItem> {
        return NavigationConfig.getAllAvailableItems()
    }
}
