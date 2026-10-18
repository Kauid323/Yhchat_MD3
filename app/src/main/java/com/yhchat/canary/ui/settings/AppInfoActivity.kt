package com.yhchat.canary.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.yhchat.canary.crash.CrashHandler
import com.yhchat.canary.R
import com.yhchat.canary.ui.base.BaseActivity
import com.yhchat.canary.ui.chat.ChatActivity
import com.yhchat.canary.ui.theme.YhchatCanaryTheme
import com.yhchat.canary.utils.UnifiedLinkHandler
import dagger.hilt.android.AndroidEntryPoint

/**
 * 应用详情 Activity
 */
@AndroidEntryPoint
class AppInfoActivity : BaseActivity() {
    
    companion object {
        const val APP_VERSION = "21.7"
        const val APP_NAME = "Yhchat Canary"

        const val DEVELOPER_NAME_1 = "Kauid323"
        const val DEVELOPER_NAME_2 = "那狗吧"
        const val DEVELOPER_URL_1 = "https://github.com/Kauid323/"
        const val DEVELOPER_URL_2 = "yunhu://chat-add?id=8516939&type=user"
        const val GITHUB_REPO_URL = "https://github.com/Kauid323/Yhchat_md3"
        const val DEFAULT_VERSION_TAG = "v0.0.21-5"
        const val IS_LATEST_BUILD_PREVIEW = false

        fun start(context: Context) {
            val intent = Intent(context, AppInfoActivity::class.java)
            context.startActivity(intent)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YhchatCanaryTheme {
                val updateViewModel: UpdateViewModel = viewModel()
                
                AppInfoScreen(
                    updateViewModel = updateViewModel,
                    isLatestBuildPreview = IS_LATEST_BUILD_PREVIEW,
                    onBack = { finish() },
                    onDeveloperClick = { url ->
                        handleDeveloperClick(url)
                    },
                    onGithubClick = {
                        openUrl(GITHUB_REPO_URL)
                    },
                    onDownloadUpdate = { url ->
                        openUrl(url)
                    }
                )
            }
        }
    }
    
    /**
     * 处理开发者点击
     */
    private fun handleDeveloperClick(url: String) {
        if (url.startsWith("yunhu://")) {
            // 使用统一链接处理器处理内链
            UnifiedLinkHandler.handleLink(this, url)
        } else {
            // 打开外部链接
            openUrl(url)
        }
    }
    
    /**
     * 打开 URL
     */
    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            CrashHandler.showCaughtException(this, e)
            e.printStackTrace()
        }
    }
}

/**
 * 应用详情界面
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun AppInfoScreen(
    updateViewModel: UpdateViewModel,
    isLatestBuildPreview: Boolean,
    onBack: () -> Unit,
    onDeveloperClick: (String) -> Unit,
    onGithubClick: () -> Unit,
    onDownloadUpdate: (String) -> Unit
) {
    var showImageViewer by remember { mutableStateOf(false) }
    var currentImageUrl by remember { mutableStateOf("") }
    var showChatDebugDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    val updateState by updateViewModel.updateState.collectAsState()
    
    LaunchedEffect(Unit) {
        updateViewModel.syncCurrentVersion(AppInfoActivity.DEFAULT_VERSION_TAG)
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "应用详情",
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(top = 24.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = coil.request.ImageRequest.Builder(LocalContext.current)
                    .data(R.mipmap.ic_launcher)
                    .crossfade(true)
                    .build(),
                contentDescription = "AppIcon",
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .combinedClickable(
                        onClick = {
                        },
                        onLongClick = {
                        }
                    ),
                contentScale = ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 应用名称
            Text(
                text = AppInfoActivity.APP_NAME,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 版本号
            Text(
                text = "版本 ${AppInfoActivity.APP_VERSION}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            HorizontalDivider()
            
            Spacer(modifier = Modifier.height(24.dp))
            
            val appInfoItems = listOf<@Composable () -> Unit>(
                {
                    AppInfoItem(
                        icon = Icons.Default.Person,
                        title = "开发者",
                        content = {
                            DeveloperText(
                                developer1 = AppInfoActivity.DEVELOPER_NAME_1,
                                developer2 = AppInfoActivity.DEVELOPER_NAME_2,
                                onDeveloper1Click = { onDeveloperClick(AppInfoActivity.DEVELOPER_URL_1) },
                                onDeveloper2Click = { onDeveloperClick(AppInfoActivity.DEVELOPER_URL_2) }
                            )
                        }
                    )
                },
                {
                    AppInfoItem(
                        icon = Icons.Default.SystemUpdate,
                        title = if (isLatestBuildPreview) "检查更新 " else "检查更新",
                        content = if (isLatestBuildPreview) {
                            {
                                Text(
                                    text = "你用的是最新预览版",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else null,
                        onClick = {
                            if (isLatestBuildPreview) {
                                android.widget.Toast.makeText(context, "你现在是最新版本了", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                updateViewModel.checkForUpdate(isLatestBuildPreview)
                            }
                        },
                        showArrow = true,
                        isLoading = updateState.isLoading
                    )
                },
                {
                    AppInfoItem(
                        icon = Icons.Default.Code,
                        title = "GitHub 源代码",
                        onClick = onGithubClick,
                        showArrow = true
                    )
                },
                {
                    AppInfoItem(
                        icon = Icons.Default.Description,
                        title = "许可证",
                        content = {
                            Text(
                                text = "GPL v3",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            )

            SettingsGroup(
                title = "应用信息",
                items = appInfoItems,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "全球首款纯净自由的云湖第三方客户端，由坚如磐石的GPL协议与世界顶级人工智能技术强力驱动。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
    
    if (showChatDebugDialog) {
        ChatDebugDialog(
            onDismiss = { showChatDebugDialog = false },
            onConfirm = { chatId, chatType ->
                val intent = Intent(context, ChatActivity::class.java).apply {
                    putExtra("chatId", chatId)
                    putExtra("chatType", chatType)
                }
                context.startActivity(intent)
                showChatDebugDialog = false
            }
        )
    }
    
    
    // 更新对话框
    updateState.updateInfo?.let { updateInfo ->
        if (updateInfo.hasUpdate) {
            UpdateDialog(
                updateInfo = updateInfo,
                isPreviewMode = isLatestBuildPreview,
                onDismiss = {
                    updateViewModel.clearUpdateInfo()
                },
                onUpdate = {
                    onDownloadUpdate(updateInfo.downloadUrl)
                    updateViewModel.clearUpdateInfo()
                }
            )
        } else {
            LaunchedEffect(updateInfo) {
            }
            
            AlertDialog(
                onDismissRequest = {
                    updateViewModel.clearUpdateInfo()
                },
                title = { Text("检查更新") },
                text = { Text("当前已是最新版本") },
                confirmButton = {
                    TextButton(onClick = {
                        updateViewModel.clearUpdateInfo()
                    }) {
                        Text("确定")
                    }
                }
            )
        }
    }

    updateState.error?.let { error ->
        AlertDialog(
            onDismissRequest = {
                updateViewModel.clearError()
            },
            title = { Text("检查更新失败") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = {
                    updateViewModel.clearError()
                }) {
                    Text("确定")
                }
            }
        )
    }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatDebugDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit
) {
    var chatId by remember { mutableStateOf("") }
    var chatTypeText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "test",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = chatId,
                    onValueChange = { 
                        chatId = it
                        errorMessage = ""
                    },
                    label = { Text("chatID") },
                    placeholder = { Text("GunMu") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = chatTypeText,
                    onValueChange = { 
                        chatTypeText = it
                        errorMessage = ""
                    },
                    label = { Text("chatType") },
                    placeholder = { Text("1-user, 2-group, 3-bot") },
                    supportingText = {
                        Text(
                            text = "1 = user\n2 = group\n3 = bot",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (chatId.isBlank()) {
                        errorMessage = "chatId"
                        return@TextButton
                    }
                    
                    val chatType = chatTypeText.toIntOrNull()
                    if (chatType == null || chatType !in 1..3) {
                        errorMessage = "must be 1,2 or 3"
                        return@TextButton
                    }
                    onConfirm(chatId, chatType)
                },
                enabled = chatId.isNotBlank() && chatTypeText.isNotBlank()
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 应用信息项组件
 */
@Composable
private fun AppInfoItem(
    icon: ImageVector,
    title: String,
    content: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    showArrow: Boolean = false,
    isLoading: Boolean = false
) {
    SettingsCustomItem(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                
                if (content != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    content()
                }
            }
            
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else if (showArrow) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 可点击的开发者文本组件
 */
@Composable
private fun DeveloperText(
    developer1: String,
    developer2: String,
    onDeveloper1Click: () -> Unit,
    onDeveloper2Click: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = developer1,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.clickable(onClick = onDeveloper1Click)
        )
        
        Text(
            text = " / ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Text(
            text = developer2,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.clickable(onClick = onDeveloper2Click)
        )
    }
}

/**
 * 更新对话框
 */
@Composable
private fun UpdateDialog(
    updateInfo: com.yhchat.canary.data.model.UpdateInfo,
    isPreviewMode: Boolean = false,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = updateInfo.updateTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (isPreviewMode) {
                    Text(
                        text = "预览版模式 - 显示最新版本",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (isPreviewMode) "最新版本: ${updateInfo.latestVersion}" else "发现新版本: ${updateInfo.latestVersion}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = "发布时间: ${updateInfo.publishTime}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "更新内容:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = updateInfo.updateContent,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            Button(onClick = onUpdate) {
                Text(if (isPreviewMode) "下载最新版" else "立即更新")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isPreviewMode) "关闭" else "稍后提醒")
            }
        }
    )
}

/**
 * 版本设置对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VersionSettingDialog(
    currentVersion: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var version by remember { mutableStateOf(currentVersion) }
    var errorMessage by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "设置当前版本",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "设置当前应用版本，用于检查更新时的版本比较。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                OutlinedTextField(
                    value = version,
                    onValueChange = { 
                        version = it
                        errorMessage = ""
                    },
                    label = { Text("版本号") },
                    placeholder = { Text("例如: v0.0.19-7-1") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = {
                        Text(
                            text = "格式: v主版本.次版本.修订版本-构建号",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                )
                
                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (version.isBlank()) {
                        errorMessage = "版本号不能为空"
                        return@TextButton
                    }
                    onConfirm(version)
                },
                enabled = version.isNotBlank()
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

