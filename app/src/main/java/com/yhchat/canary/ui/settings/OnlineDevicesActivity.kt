package com.yhchat.canary.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yhchat.canary.data.model.DeviceInfo
import com.yhchat.canary.ui.base.BaseActivity
import com.yhchat.canary.ui.theme.YhchatCanaryTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 在线设备Activity
 */
@AndroidEntryPoint
class OnlineDevicesActivity : BaseActivity() {
    
    companion object {
        /**
         * 启动在线设备Activity
         */
        fun start(context: Context) {
            val intent = Intent(context, OnlineDevicesActivity::class.java)
            context.startActivity(intent)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            YhchatCanaryTheme {
                val deviceViewModel: DeviceViewModel = viewModel()
                
                OnlineDevicesScreen(
                    deviceViewModel = deviceViewModel,
                    onBack = { finish() }
                )
            }
        }
    }
}

/**
 * 在线设备界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineDevicesScreen(
    deviceViewModel: DeviceViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val deviceState by deviceViewModel.deviceState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(Unit) {
        deviceViewModel.init(context)
        deviceViewModel.loadOnlineDevices()
    }

    // 下线结果提示
    LaunchedEffect(deviceState.userMessage) {
        deviceState.userMessage?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            deviceViewModel.consumeUserMessage()
        }
    }
    
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "在线设备",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            deviceViewModel.loadOnlineDevices()
                        },
                        enabled = !deviceState.isLoading
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新"
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 错误提示
            deviceState.error?.let { error ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            
            // 设备列表
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ---- 在线设备 ----
                if (deviceState.onlineDevices.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "在线设备",
                            count = deviceState.onlineDevices.size
                        )
                    }
                    items(deviceState.onlineDevices, key = { "online_${it.deviceId}" }) { device ->
                        DeviceItem(
                            device = device,
                            isOperating = device.deviceId in deviceState.operatingDeviceIds,
                            onOffline = { deviceViewModel.offlineDevice(device) }
                        )
                    }
                }

                // ---- 历史设备 ----
                // 服务端只返回在线设备，这里展示的是本地记录过的、当前已不在线的设备。
                // 没有它的话，用户一下线就再也看不到那台设备了。
                if (deviceState.historyDevices.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "历史设备",
                            count = deviceState.historyDevices.size,
                            subtitle = "曾经登录过，当前不在线"
                        )
                    }
                    items(deviceState.historyDevices, key = { "history_${it.deviceId}" }) { device ->
                        DeviceItem(
                            device = device,
                            isOperating = false,
                            onOffline = null
                        )
                    }
                }

                // 空状态
                if (deviceState.devices.isEmpty() && !deviceState.isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeviceHub,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "暂无设备记录",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // 加载状态
                if (deviceState.isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
    
    // 错误对话框
    deviceState.error?.let { error ->
        AlertDialog(
            onDismissRequest = {
                deviceViewModel.clearError()
            },
            title = { Text("加载失败") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = {
                    deviceViewModel.clearError()
                }) {
                    Text("确定")
                }
            }
        )
    }
}

/**
 * 分组标题：在线设备 / 历史设备
 */
@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * 设备列表项。
 *
 * @param onOffline 非空时显示"下线"按钮；历史设备传 null（它已经不在线了）
 * @param isOperating 下线请求进行中，禁用按钮并显示进度
 */
@Composable
fun DeviceItem(
    device: DeviceInfo,
    modifier: Modifier = Modifier,
    isOperating: Boolean = false,
    onOffline: (() -> Unit)? = null
) {
    var showConfirm by remember { mutableStateOf(false) }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("下线该设备？") },
            text = {
                Text("「${device.getPlatformDisplayName()}」将被强制下线，需要重新登录才能继续使用。")
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    onOffline?.invoke()
                }) {
                    Text("下线", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("取消") }
            }
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (device.isOnline) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                // 历史设备压暗一档，和在线设备拉开层次
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 平台图标
            Icon(
                imageVector = device.getPlatformIcon(),
                contentDescription = device.platform,
                modifier = Modifier.size(32.dp),
                tint = if (device.isOnline) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                }
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = device.getPlatformDisplayName(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (device.isOnline) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    if (device.isOnline) {
                        Spacer(modifier = Modifier.width(8.dp))
                        // 在线状态点：比纯文字更省空间，也更容易扫读
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "设备ID: ${device.deviceId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Text(
                    text = "${if (device.isOnline) "登录时间" else "最后登录"}: ${device.getFormattedLoginTime()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 下线按钮：只给在线设备
            if (onOffline != null && device.isOnline) {
                Spacer(modifier = Modifier.width(8.dp))
                if (isOperating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    TextButton(onClick = { showConfirm = true }) {
                        Text(
                            text = "下线",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}
