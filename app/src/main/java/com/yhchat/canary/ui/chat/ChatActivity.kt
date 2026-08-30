package com.yhchat.canary.ui.chat

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import com.yhchat.canary.ui.adaptive.YhText as Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.yhchat.canary.data.di.RepositoryFactory
import com.yhchat.canary.ui.base.BaseActivity
import com.yhchat.canary.ui.adaptive.isMiuixUi
import com.yhchat.canary.ui.adaptive.YhSurface
import com.yhchat.canary.ui.components.rememberBooleanPreference
import com.yhchat.canary.ui.theme.YhchatCanaryTheme
import com.yhchat.canary.ui.user.UserDetailActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ChatActivity : BaseActivity() {
    
    // 使用状态管理会话参数，以便在 onNewIntent 中更新
    private var chatId by mutableStateOf("")
    private var chatType by mutableStateOf(1)
    private var chatName by mutableStateOf("")
    private var chatAvatarUrl by mutableStateOf<String?>(null)
    private var currentUserId by mutableStateOf("")
    
    // 搜索跳转参数
    private var searchTargetMsgId by mutableStateOf<String?>(null)
    private var searchTargetMsgSeq by mutableStateOf<Long?>(null)
    private var launchedFromBubble by mutableStateOf(false)
    
    private var pendingImageUrisToSend by mutableStateOf<List<android.net.Uri>>(emptyList())

    // 图片选择器 - 系统选择器多选
    private val imagePickerLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        val selected = uris.filterNotNull()
        if (selected.isNotEmpty()) {
            android.util.// Log.d("ChatActivity", "图片已选择(多选): count=${selected.size}")
            pendingImageUrisToSend = selected
            imageUriToSend = selected.firstOrNull()
        }
    }
    
    // 文件选择器
    private val filePickerLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
            android.util.// Log.d("ChatActivity", "📁 文件已选择: $selectedUri")
            fileUriToSend = selectedUri
        }
    }
    
    // 视频选择器
    private val videoPickerLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
            android.util.// Log.d("ChatActivity", "📹 视频已选择: $selectedUri")
            videoUriToSend = selectedUri
        }
    }
    
    // 相机拍照
    private var cameraImageUri by mutableStateOf<android.net.Uri?>(null)
    private val cameraLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraImageUri?.let { uri ->
                android.util.// Log.d("ChatActivity", "拍照成功: $uri")
                imageUriToSend = uri
            }
        }
    }
    
    private var imageUriToSend by mutableStateOf<android.net.Uri?>(null)
    private var fileUriToSend by mutableStateOf<android.net.Uri?>(null)
    private var videoUriToSend by mutableStateOf<android.net.Uri?>(null)
    
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // 从Intent中读取参数
        updateChatParams(intent)
        
        setContent {
            YhchatCanaryTheme {
                val enableAnimations by rememberBooleanPreference(
                    preferencesName = "chat_settings",
                    key = "enable_chat_animations",
                    defaultValue = true,
                    mode = Context.MODE_PRIVATE
                )
                val topBarComposeColor = if (isMiuixUi) {
                    top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.surface
                }
                val topBarColor = topBarComposeColor.toArgb()
                val navigationBarColor = android.graphics.Color.TRANSPARENT
                val view = LocalView.current
                val isLightTheme = !androidx.compose.foundation.isSystemInDarkTheme()
                val useDarkStatusBarIcons = topBarComposeColor.luminance() > 0.5f
                
                SideEffect {
                    val insetsController = WindowCompat.getInsetsController(window, view)

                    // 设置状态栏
                    insetsController.isAppearanceLightStatusBars = useDarkStatusBarIcons
                    window.statusBarColor = topBarColor
                    
                    // 设置导航栏，让手势线自动适配
                    window.navigationBarColor = navigationBarColor
                    
                    // Android 8.0+ 设置导航栏图标和手势线颜色
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        insetsController.isAppearanceLightNavigationBars = isLightTheme
                    }
                    
                    // Android 10+ 禁用强制对比度
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        window.isNavigationBarContrastEnforced = false
                    }
                }
                YhSurface(color = MaterialTheme.colorScheme.background) {
                    if (chatId.isBlank()) {
                        Box(
                            modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (launchedFromBubble) "对话泡未拿到会话参数" else "会话参数为空",
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    } else {
                        ChatScreen(
                            chatId = chatId,
                            chatType = chatType,
                            chatName = chatName,
                            chatAvatarUrl = chatAvatarUrl,
                            enableAnimations = enableAnimations,
                            userId = currentUserId,
                            onBackClick = { finish() },
                            onAvatarClick = { userId, userName, chatType, currentUserPermission -> 
                                if (chatType != 3) {
                                    UserDetailActivity.start(
                                        context = this@ChatActivity,
                                        userId = userId,
                                        userName = userName,
                                        groupId = if (this@ChatActivity.chatType == 2) this@ChatActivity.chatId else null
                                    )
                                }
                            },
                            onImagePickerClick = {
                                imagePickerLauncher.launch("image/*")
                            },
                            onCameraClick = {
                                val photoFile = java.io.File(cacheDir, "camera_${System.currentTimeMillis()}.jpg")
                                cameraImageUri = androidx.core.content.FileProvider.getUriForFile(
                                    this@ChatActivity,
                                    "${packageName}.fileprovider",
                                    photoFile
                                )
                                cameraImageUri?.let { uri ->
                                    cameraLauncher.launch(uri)
                                }
                            },
                            onFilePickerClick = {
                                android.util.// Log.d("ChatActivity", "📁 启动文件选择器")
                                filePickerLauncher.launch("*/*")
                            },
                            onVideoPickerClick = {
                                android.util.// Log.d("ChatActivity", "📹 启动视频选择器")
                                videoPickerLauncher.launch("video/*")
                            },
                            imageUriToSend = imageUriToSend,
                            fileUriToSend = fileUriToSend,
                            videoUriToSend = videoUriToSend,
                            onImageSent = {
                                val current = imageUriToSend
                                val remaining = if (current == null) {
                                    pendingImageUrisToSend
                                } else {
                                    pendingImageUrisToSend.drop(1)
                                }
                                pendingImageUrisToSend = remaining
                                imageUriToSend = remaining.firstOrNull()

                                if (imageUriToSend == null) {
                                    cameraImageUri = null
                                }
                            },
                            onFileSent = {
                                android.util.// Log.d("ChatActivity", "📁 文件发送完成，清空URI")
                                fileUriToSend = null
                            },
                            onVideoSent = {
                                android.util.// Log.d("ChatActivity", "📹 视频发送完成，清空URI")
                                videoUriToSend = null
                            },
                            searchTargetMsgId = searchTargetMsgId,
                            searchTargetMsgSeq = searchTargetMsgSeq
                        )
                    }
                }
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 当Activity被复用时，更新参数
        android.util.// Log.d("ChatActivity", "onNewIntent called, updating chat params")
        updateChatParams(intent)
        // 更新当前Intent
        setIntent(intent)
    }
    
    private fun updateChatParams(intent: Intent) {
        val newChatId = intent.getStringExtra("chatId") ?: ""
        val newChatType = intent.getIntExtra("chatType", 1)
        val newChatName = intent.getStringExtra("chatName") ?: ""
        val newChatAvatarUrl = intent.getStringExtra("avatarUrl")
        val newUserId = intent.getStringExtra("userId")
        
        // 读取搜索跳转参数
        val newSearchTargetMsgId = intent.getStringExtra("searchTargetMsgId")
        val newSearchTargetMsgSeq = intent.getLongExtra("searchTargetMsgSeq", -1L).let { 
            if (it == -1L) null else it 
        }
        
        android.util.// Log.d("ChatActivity", "Updating chat params: chatId=$newChatId, chatType=$newChatType, chatName=$newChatName, searchTargetMsgId=$newSearchTargetMsgId, searchTargetMsgSeq=$newSearchTargetMsgSeq")
        
        chatId = newChatId
        chatType = newChatType
        chatName = newChatName
        chatAvatarUrl = newChatAvatarUrl
        currentUserId = newUserId?.takeIf { it.isNotBlank() }
            ?: RepositoryFactory.getTokenRepository(this).getUserIdSync().orEmpty()
        searchTargetMsgId = newSearchTargetMsgId
        searchTargetMsgSeq = newSearchTargetMsgSeq
        launchedFromBubble = intent.getBooleanExtra("fromBubble", false)
    }
    
    companion object {
        /**
         * 启动ChatActivity并跳转到指定消息
         */
        fun startWithSearchTarget(
            context: android.content.Context,
            chatId: String,
            chatType: Int,
            chatName: String,
            targetMsgId: String,
            targetMsgSeq: Long? = null
        ) {
            val intent = Intent(context, ChatActivity::class.java).apply {
                putExtra("chatId", chatId)
                putExtra("chatType", chatType)
                putExtra("chatName", chatName)
                putExtra("searchTargetMsgId", targetMsgId)
                targetMsgSeq?.let { putExtra("searchTargetMsgSeq", it) }
                // 添加标志以确保Activity正确启动
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            context.startActivity(intent)
        }
    }
}
