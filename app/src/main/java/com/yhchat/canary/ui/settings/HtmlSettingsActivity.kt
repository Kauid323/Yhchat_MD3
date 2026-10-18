package com.yhchat.canary.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yhchat.canary.ui.theme.YhchatCanaryTheme

/**
 * HTML设置Activity
 */
class HtmlSettingsActivity : ComponentActivity() {
    
    companion object {
        fun start(context: Context) {
            val intent = Intent(context, HtmlSettingsActivity::class.java)
            context.startActivity(intent)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.yhchat.canary.ui.base.SystemBarUtils.setupTransparentSystemBars(this)
        
        setContent {
            YhchatCanaryTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HtmlSettingsScreen(
                        onBackClick = { finish() }
                    )
                }
            }
        }
    }
}

/**
 * HTML设置界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HtmlSettingsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { 
        context.getSharedPreferences("html_settings", Context.MODE_PRIVATE) 
    }
    
    // 设置状态
    var enableJavaScript by remember { 
        mutableStateOf(prefs.getBoolean("enable_javascript", true)) 
    }
    var allowZoom by remember { 
        mutableStateOf(prefs.getBoolean("allow_zoom", true)) 
    }
    var loadImages by remember { 
        mutableStateOf(prefs.getBoolean("load_images", true)) 
    }
    var cacheMode by remember { 
        mutableStateOf(prefs.getInt("cache_mode", 0)) 
    }
    var userAgent by remember { 
        mutableStateOf(prefs.getString("user_agent", "default") ?: "default") 
    }
    
    // 缓存模式选项
    val cacheModeOptions = listOf(
        "默认缓存" to 0,
        "无缓存" to 1,
        "仅缓存" to 2,
        "缓存优先" to 3
    )
    
    // User Agent选项
    val userAgentOptions = listOf(
        "默认" to "default",
        "桌面版Chrome" to "desktop_chrome",
        "移动版Chrome" to "mobile_chrome",
        "iOS Safari" to "ios_safari"
    )
    
    // 保存设置函数
    fun saveSettings() {
        prefs.edit()
            .putBoolean("enable_javascript", enableJavaScript)
            .putBoolean("allow_zoom", allowZoom)
            .putBoolean("load_images", loadImages)
            .putInt("cache_mode", cacheMode)
            .putString("user_agent", userAgent)
            .apply()
    }
    
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "HTML设置",
                    fontWeight = FontWeight.SemiBold
                )
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "返回"
                    )
                }
            },
            actions = {
                TextButton(
                    onClick = {
                        // 重置
                        enableJavaScript = true
                        allowZoom = true
                        loadImages = true
                        cacheMode = 0
                        userAgent = "default"
                        saveSettings()
                    }
                ) {
                    Text("重置")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                scrolledContainerColor = MaterialTheme.colorScheme.background
            )
        )
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 基本设置
            item {
                HtmlSettingsCard(
                    title = "基本设置",
                    content = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                         ) {
                            // JavaScript设置
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "启用JavaScript",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "允许网页运行JavaScript代码",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = enableJavaScript,
                                    onCheckedChange = { 
                                        enableJavaScript = it
                                        saveSettings()
                                    }
                                )
                            }
                            
                            HorizontalDivider()
                            
                            // 缩放设置
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "允许缩放",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "允许手势缩放网页内容",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = allowZoom,
                                    onCheckedChange = { 
                                        allowZoom = it
                                        saveSettings()
                                    }
                                )
                            }
                            
                            HorizontalDivider()
                            
                            // 图片加载设置
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "加载图片",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "自动加载网页中的图片",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = loadImages,
                                    onCheckedChange = { 
                                        loadImages = it
                                        saveSettings()
                                    }
                                )
                            }
                        }
                    }
                )
            }
            
            // 高级设置
            item {
                HtmlSettingsCard(
                    title = "高级设置",
                    content = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // 缓存模式
                            Column {
                                Text(
                                    text = "缓存模式",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                
                                cacheModeOptions.forEach { (label, value) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = cacheMode == value,
                                            onClick = { 
                                                cacheMode = value
                                                saveSettings()
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                            
                            HorizontalDivider()
                            
                            // User Agent
                            Column {
                                Text(
                                    text = "User Agent",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                
                                userAgentOptions.forEach { (label, value) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = userAgent == value,
                                            onClick = { 
                                                userAgent = value
                                                saveSettings()
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                )
            }
            
            // 说明信息
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                            text = "设置说明",
                        style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                    )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                            text = "这些设置将影响应用内所有网页的显示效果\n" +
                                  "禁用JavaScript可能导致部分网页功能异常\n" +
                                  "修改缓存模式会影响页面加载速度\n" +
                                  "更改User Agent可能影响网站兼容性",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * HTML设置卡片组件
 */
@Composable
private fun HtmlSettingsCard(
    title: String,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
            ) {
                Text(
                    text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            content()
        }
    }
}
