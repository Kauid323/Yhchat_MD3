package com.yhchat.canary

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.Coil
import coil.ImageLoader
import coil.request.CachePolicy
import com.yhchat.canary.data.di.RepositoryFactory
import com.yhchat.canary.ui.base.BaseActivity
import com.yhchat.canary.ui.chat.ChatAddActivity
import com.yhchat.canary.ui.chat.ChatScreen
import com.yhchat.canary.ui.community.*
import com.yhchat.canary.ui.components.AdaptiveNavigationBar
import com.yhchat.canary.ui.components.isLargeScreenLayout
import com.yhchat.canary.ui.components.rememberBooleanPreference
import com.yhchat.canary.ui.components.rememberScrollAwareNavigationState
import com.yhchat.canary.ui.contacts.ContactsScreen
import com.yhchat.canary.ui.conversation.ConversationScreen
import com.yhchat.canary.ui.conversation.ConversationViewModel
import com.yhchat.canary.ui.discover.DiscoverScreen
import com.yhchat.canary.ui.login.LoginScreen
import com.yhchat.canary.ui.login.LoginViewModel
import com.yhchat.canary.ui.profile.ProfileScreen
import com.yhchat.canary.ui.search.SearchScreen
import com.yhchat.canary.ui.search.SearchViewModel
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import com.yhchat.canary.ui.theme.YhchatCanaryTheme
import com.yhchat.canary.ui.user.UserDetailActivity
import com.yhchat.canary.utils.UnifiedLinkHandler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.and
import kotlin.apply
import kotlin.let
import kotlin.or
import kotlin.toString

@AndroidEntryPoint
class MainActivity : BaseActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching {
            enableEdgeToEdge()
        }
        // 处理 Deep Link
        handleDeepLink()
        // 配置Coil ImageLoader，为chat-img.jwznb.com添加Referer，支持GIF和WebP
        setupImageLoader()
        
        setContent {
            YhchatCanaryTheme {
                // 设置系统导航栏颜色，让手势线自动适配应用的导航栏颜色
                SetSystemNavigationBarColor()
                
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainContent()
                }
            }
        }
    }
    
    /**
     * 设置系统导航栏颜色，让手势线自动适配应用主题
     */
    @Composable
    private fun SetSystemNavigationBarColor() {
        // 导航栏背景设为透明，让手势条区域完全透明
        val navigationBarColor = Color.Transparent
        val isLightTheme = !isSystemInDarkTheme()
        
        SideEffect {
            runCatching {
                // 设置系统导航栏背景色
                @Suppress("DEPRECATION")
                window.navigationBarColor = navigationBarColor.toArgb()
            }
            
            // Android 8.0+ 支持设置导航栏图标颜色
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                runCatching {
                    applyLightNavigationBarIconsForOAndAbove(isLightTheme)
                }
            }
            
            // Android 10+ 禁用系统强制对比度，完全使用我们设置的颜色
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching {
                    disableNavigationBarContrastForQAndAbove()
                }
            }
            
            // Android 11+ 使用新的API设置导航栏外观
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching {
                    applyNavigationBarAppearanceForRAndAbove(isLightTheme)
                }
            }
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    @Suppress("DEPRECATION")
    private fun applyLightNavigationBarIconsForOAndAbove(isLightTheme: Boolean) {
        window.decorView.systemUiVisibility = if (isLightTheme) {
            window.decorView.systemUiVisibility or
                android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        } else {
            window.decorView.systemUiVisibility and
                android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun disableNavigationBarContrastForQAndAbove() {
        window.isNavigationBarContrastEnforced = false
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private fun applyNavigationBarAppearanceForRAndAbove(isLightTheme: Boolean) {
        window.insetsController?.setSystemBarsAppearance(
            if (isLightTheme) android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS else 0,
            android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        )
    }
    
    @Composable
    private fun MainContent() {
        val context = LocalContext.current
        // 使用MainViewModel
        val mainViewModel: MainViewModel = viewModel()
        val loginViewModel: LoginViewModel = viewModel()
        val isInitialized by mainViewModel.isInitialized.collectAsStateWithLifecycle()
        val isLoggedIn by mainViewModel.isLoggedIn.collectAsStateWithLifecycle()
        val savedToken by mainViewModel.savedToken.collectAsStateWithLifecycle()
        val savedUserId by mainViewModel.userId.collectAsStateWithLifecycle()
        val tokenRepository by mainViewModel.tokenRepositoryState.collectAsStateWithLifecycle()
        val userRepository by mainViewModel.userRepositoryState.collectAsStateWithLifecycle()

        // 本地状态
        var token by remember { mutableStateOf(savedToken ?: "") }
        var userId by remember { mutableStateOf(savedUserId ?: "") }
        var currentScreen by rememberSaveable { mutableStateOf("conversation") }
        var currentChatId by rememberSaveable { mutableStateOf("") }
        var currentChatType by rememberSaveable { mutableStateOf(0) }
        var currentChatName by rememberSaveable { mutableStateOf("") }
        var currentChatAvatarUrl by rememberSaveable { mutableStateOf<String?>(null) }
        // 大屏分屏模式：当前选中的聊天（右侧面板）
        var splitChatId by rememberSaveable { mutableStateOf("") }
        var splitChatType by rememberSaveable { mutableStateOf(0) }
        var splitChatName by rememberSaveable { mutableStateOf("") }
        var splitChatAvatarUrl by rememberSaveable { mutableStateOf<String?>(null) }
        
        // 大屏社区分屏状态
        var communityPanelType by rememberSaveable { mutableStateOf("") } // "", "board", "post", "myposts", "collect", "blocked"
        var communityBoardId by rememberSaveable { mutableStateOf(0) }
        var communityBoardName by rememberSaveable { mutableStateOf("") }
        var communityPostId by rememberSaveable { mutableStateOf(0) }
        var communityPostTitle by rememberSaveable { mutableStateOf("") }
        var communityPrevType by rememberSaveable { mutableStateOf("") }
        var communityPrevBoardId by rememberSaveable { mutableStateOf(0) }
        var communityPrevBoardName by rememberSaveable { mutableStateOf("") }
        
        // 保持ConversationScreen的ViewModel状态，避免重新创建
        val conversationViewModel: ConversationViewModel = viewModel()
        val searchViewModel: SearchViewModel = viewModel()

        // 左右滑动切换主页面已废除：固定禁用 pager 的手势滑动
        
        // 导航配置
        val navigationRepository = remember { RepositoryFactory.getNavigationRepository(this@MainActivity) }
        val accountRepository = remember { RepositoryFactory.getAccountRepository(this@MainActivity) }
        val navigationConfig by navigationRepository.navigationConfig.collectAsStateWithLifecycle()
        val visibleNavItems = navigationConfig.items.filter { it.isVisible }.sortedBy { it.order }

        // 同步ViewModel状态到本地状态
        LaunchedEffect(savedToken) {
            savedToken?.let { token = it }
        }
        LaunchedEffect(savedUserId) {
            savedUserId?.let { userId = it }
        }

        LaunchedEffect(tokenRepository) {
            loginViewModel.setTokenRepository(tokenRepository)
        }

        LaunchedEffect(accountRepository, userRepository) {
            val currentUserRepository = userRepository
            if (currentUserRepository != null) {
                loginViewModel.setAccountRepositories(accountRepository, currentUserRepository)
            }
        }

        when {
            !isLoggedIn -> {
                // 未登录，显示登录界面
                LoginScreen(
                    viewModel = loginViewModel,
                    onLoginSuccess = { loginToken, loginUserId ->
                        token = loginToken
                        userId = loginUserId
                        mainViewModel.onLoginSuccess(loginToken, loginUserId)
                    }
                )
            }
            else -> {
                // 主界面
                var currentPageItem by rememberSaveable { mutableStateOf(currentScreen) }
                var isPageFading by remember { mutableStateOf(false) }
                val pagerAlpha by animateFloatAsState(
                    targetValue = if (isPageFading) 0f else 1f,
                    animationSpec = tween(durationMillis = 140),
                    label = "mainPagerAlpha"
                )
                
                // 滚动感知导航栏状态
                val navigationState = rememberScrollAwareNavigationState()

                // 确保当前页面项始终可用
                LaunchedEffect(visibleNavItems) {
                    if (visibleNavItems.isEmpty()) {
                        currentPageItem = currentScreen
                    } else if (visibleNavItems.none { it.id == currentPageItem }) {
                        currentPageItem = visibleNavItems.first().id
                    }
                }

                // 页面同步逻辑：仅底部导航点击切页，不支持滑动手势切页
                LaunchedEffect(currentScreen, visibleNavItems) {
                    val targetItem = visibleNavItems.firstOrNull { it.id == currentScreen }
                    if (targetItem != null && currentPageItem != targetItem.id) {
                        isPageFading = true
                        delay(90)
                        currentPageItem = targetItem.id
                        isPageFading = false
                    }
                }
                
                // 切换页面时重置导航栏状态（仅在页面完全切换后显示）
                LaunchedEffect(currentPageItem) {
                    // 切换到新页面时显示导航栏
                    navigationState.show()
                }

                // 检测屏幕尺寸
                val configuration = LocalConfiguration.current
                val forceDisableLargeScreenMode by rememberBooleanPreference(
                    preferencesName = "layout_settings",
                    key = "force_disable_large_screen_mode",
                    defaultValue = false
                )
                val isLargeScreen = isLargeScreenLayout(
                    configuration = configuration,
                    forceDisableLargeScreenMode = forceDisableLargeScreenMode
                )

                if (isLargeScreen) {
                    // 大屏设备：使用Row布局，侧边栏在左边
                    Row(modifier = Modifier.fillMaxSize()) {
                        // 侧边导航栏
                        AdaptiveNavigationBar(
                            currentScreen = currentPageItem,
                            visibleItems = visibleNavItems,
                            onScreenChange = { screen ->
                                currentScreen = screen
                            },
                            isVisible = true
                        )
                        
                        // 主内容区域
                        Box(modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                        ) {
                        if (visibleNavItems.isNotEmpty()) {
                            val navItem = visibleNavItems.firstOrNull { it.id == currentPageItem } ?: visibleNavItems.first()
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .alpha(pagerAlpha)
                            ) {
                                    when (navItem.id) {
                                        "conversation" -> {
                                            // 大屏分屏：会话列表(1) + 聊天界面(2)
                                            Row(modifier = Modifier.fillMaxSize()) {
                                                // 左侧：会话列表 (占1/3)
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .fillMaxHeight()
                                                ) {
                                                    ConversationScreen(
                                                        token = token,
                                                        userId = userId,
                                                        onConversationClick = { chatId, chatType, chatName, avatarUrl ->
                                                            // 大屏模式：更新右侧面板而不是启动新Activity
                                                            splitChatId = chatId
                                                            splitChatType = chatType
                                                            splitChatName = chatName
                                                            splitChatAvatarUrl = avatarUrl
                                                        },
                                                        onSearchClick = {
                                                            if (isInitialized) {
                                                                currentScreen = "search"
                                                            }
                                                        },
                                                        onMenuClick = { },
                                                        onProfileClick = {
                                                            currentScreen = "profile"
                                                        },
                                                        tokenRepository = tokenRepository,
                                                        viewModel = conversationViewModel,
                                                        navigationState = navigationState
                                                    )
                                                }
                                                
                                                // 分隔线
                                                VerticalDivider(
                                                    modifier = Modifier.fillMaxHeight(),
                                                    thickness = 0.5.dp,
                                                    color = MaterialTheme.colorScheme.outlineVariant
                                                )
                                                
                                                // 右侧：聊天界面 (占2/3)
                                                Box(
                                                    modifier = Modifier
                                                        .weight(2f)
                                                        .fillMaxHeight()
                                                ) {
                                                    if (splitChatId.isNotEmpty()) {
                                                        // 使用 key 确保切换聊天时重建 ChatScreen
                                                        key(splitChatId, splitChatType) {
                                                            var imageUriToSend by remember { mutableStateOf<android.net.Uri?>(null) }
                                                            var fileUriToSend by remember { mutableStateOf<android.net.Uri?>(null) }
                                                            var videoUriToSend by remember { mutableStateOf<android.net.Uri?>(null) }
                                                            var cameraImageUri by remember { mutableStateOf<android.net.Uri?>(null) }

                                                            val imagePickerLauncher = rememberLauncherForActivityResult(
                                                                contract = ActivityResultContracts.GetContent()
                                                            ) { uri: android.net.Uri? ->
                                                                uri?.let { imageUriToSend = it }
                                                            }

                                                            val filePickerLauncher = rememberLauncherForActivityResult(
                                                                contract = ActivityResultContracts.GetContent()
                                                            ) { uri: android.net.Uri? ->
                                                                uri?.let { fileUriToSend = it }
                                                            }

                                                            val videoPickerLauncher = rememberLauncherForActivityResult(
                                                                contract = ActivityResultContracts.GetContent()
                                                            ) { uri: android.net.Uri? ->
                                                                uri?.let { videoUriToSend = it }
                                                            }

                                                            val cameraLauncher = rememberLauncherForActivityResult(
                                                                contract = ActivityResultContracts.TakePicture()
                                                            ) { success: Boolean ->
                                                                if (success) {
                                                                    cameraImageUri?.let { imageUriToSend = it }
                                                                }
                                                            }

                                                            ChatScreen(
                                                                chatId = splitChatId,
                                                                chatType = splitChatType,
                                                                chatName = splitChatName,
                                                                chatAvatarUrl = splitChatAvatarUrl,
                                                                userId = userId,
                                                                isLargeScreen = true,
                                                                onBackClick = {
                                                                    // 大屏模式：关闭右侧面板
                                                                    splitChatId = ""
                                                                    splitChatType = 0
                                                                    splitChatName = ""
                                                                    splitChatAvatarUrl = null
                                                                },
                                                                onAvatarClick = { avatarUserId, userName, chatType, _ ->
                                                                    val groupId = if (chatType == 2) splitChatId else null
                                                                    UserDetailActivity.start(
                                                                        context = this@MainActivity,
                                                                        userId = avatarUserId,
                                                                        userName = userName,
                                                                        groupId = groupId
                                                                    )
                                                                },
                                                                onImagePickerClick = {
                                                                    imagePickerLauncher.launch("image/*")
                                                                },
                                                                onFilePickerClick = {
                                                                    filePickerLauncher.launch("*/*")
                                                                },
                                                                onVideoPickerClick = {
                                                                    videoPickerLauncher.launch("video/*")
                                                                },
                                                                onCameraClick = {
                                                                    val photoFile = java.io.File(cacheDir, "camera_${System.currentTimeMillis()}.jpg")
                                                                    cameraImageUri = androidx.core.content.FileProvider.getUriForFile(
                                                                        this@MainActivity,
                                                                        "${packageName}.fileprovider",
                                                                        photoFile
                                                                    )
                                                                    cameraImageUri?.let { uri ->
                                                                        cameraLauncher.launch(uri)
                                                                    }
                                                                },
                                                                imageUriToSend = imageUriToSend,
                                                                fileUriToSend = fileUriToSend,
                                                                videoUriToSend = videoUriToSend,
                                                                onImageSent = {
                                                                    imageUriToSend = null
                                                                    cameraImageUri = null
                                                                },
                                                                onFileSent = {
                                                                    fileUriToSend = null
                                                                },
                                                                onVideoSent = {
                                                                    videoUriToSend = null
                                                                },
                                                                modifier = Modifier.fillMaxSize()
                                                            )
                                                        }
                                                    } else {
                                                        // 未选择聊天时的占位界面
                                                        Box(
                                                            modifier = Modifier.fillMaxSize(),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Column(
                                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                                                    contentDescription = null,
                                                                    modifier = Modifier.size(48.dp),
                                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                                                )
                                                                Text(
                                                                    text = "选择一个会话开始聊天",
                                                                    style = MaterialTheme.typography.bodyLarge,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        "community" -> {
                                            // 大屏分屏：社区标签页(1) + 详情面板(2)
                                            Row(modifier = Modifier.fillMaxSize()) {
                                                // 左侧：社区标签页 (占1/3)
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .fillMaxHeight()
                                                ) {
                                                    CommunityTabScreen(
                                                        token = token,
                                                        onBoardNavigate = { boardId, boardName ->
                                                            communityBoardId = boardId
                                                            communityBoardName = boardName
                                                            communityPanelType = "board"
                                                            communityPrevType = ""
                                                        },
                                                        onMyPostsNavigate = {
                                                            communityPanelType = "myposts"
                                                            communityPrevType = ""
                                                        },
                                                        onMyCollectPostsNavigate = {
                                                            communityPanelType = "collect"
                                                            communityPrevType = ""
                                                        },
                                                        onBlockedUsersNavigate = {
                                                            communityPanelType = "blocked"
                                                            communityPrevType = ""
                                                        },
                                                    )
                                                }
                                                
                                                // 分隔线
                                                VerticalDivider(
                                                    modifier = Modifier.fillMaxHeight(),
                                                    thickness = 0.5.dp,
                                                    color = MaterialTheme.colorScheme.outlineVariant
                                                )
                                                
                                                // 右侧：详情面板 (占2/3)
                                                Box(
                                                    modifier = Modifier
                                                        .weight(2f)
                                                        .fillMaxHeight()
                                                ) {
                                                    when (communityPanelType) {
                                                        "board" -> {
                                                            key("board_$communityBoardId") {
                                                                val boardDetailVm: BoardDetailViewModel = viewModel(key = "split_board_$communityBoardId") {
                                                                    BoardDetailViewModel(
                                                                        communityRepository = RepositoryFactory.getCommunityRepository(this@MainActivity),
                                                                        tokenRepository = RepositoryFactory.getTokenRepository(this@MainActivity)
                                                                    )
                                                                }
                                                                BoardDetailScreen(
                                                                    boardId = communityBoardId,
                                                                    boardName = communityBoardName,
                                                                    token = token,
                                                                    viewModel = boardDetailVm,
                                                                    onBackClick = {
                                                                        communityPanelType = ""
                                                                    },
                                                                    onPostNavigate = { postId, postTitle ->
                                                                        communityPrevType = "board"
                                                                        communityPrevBoardId = communityBoardId
                                                                        communityPrevBoardName = communityBoardName
                                                                        communityPostId = postId
                                                                        communityPostTitle = postTitle
                                                                        communityPanelType = "post"
                                                                    }
                                                                )
                                                            }
                                                        }
                                                        "post" -> {
                                                            key("post_$communityPostId") {
                                                                val postDetailVm: PostDetailViewModel = viewModel(key = "split_post_$communityPostId") {
                                                                    PostDetailViewModel(
                                                                        communityRepository = RepositoryFactory.getCommunityRepository(this@MainActivity),
                                                                        tokenRepository = RepositoryFactory.getTokenRepository(this@MainActivity),
                                                                        userRepository = RepositoryFactory.getUserRepository(this@MainActivity),
                                                                        friendRepository = RepositoryFactory.getFriendRepository(this@MainActivity)
                                                                    )
                                                                }
                                                                PostDetailScreen(
                                                                    postId = communityPostId,
                                                                    postTitle = communityPostTitle,
                                                                    viewModel = postDetailVm,
                                                                    onBackClick = {
                                                                        when (communityPrevType) {
                                                                            "board" -> {
                                                                                communityBoardId = communityPrevBoardId
                                                                                communityBoardName = communityPrevBoardName
                                                                                communityPanelType = "board"
                                                                                communityPrevType = ""
                                                                            }
                                                                            "myposts" -> {
                                                                                communityPanelType = "myposts"
                                                                                communityPrevType = ""
                                                                            }
                                                                            "collect" -> {
                                                                                communityPanelType = "collect"
                                                                                communityPrevType = ""
                                                                            }
                                                                            else -> {
                                                                                communityPanelType = ""
                                                                                communityPrevType = ""
                                                                            }
                                                                        }
                                                                    }
                                                                )
                                                            }
                                                        }
                                                        "myposts" -> {
                                                            val moreCommunityVm: CommunityViewModel = viewModel(key = "split_more_community") {
                                                                CommunityViewModel(
                                                                    communityRepository = RepositoryFactory.getCommunityRepository(this@MainActivity)
                                                                )
                                                            }
                                                            MyPostsScreen(
                                                                token = token,
                                                                viewModel = moreCommunityVm,
                                                                onBackClick = {
                                                                    communityPanelType = ""
                                                                },
                                                                onPostNavigate = { postId, postTitle ->
                                                                    communityPrevType = "myposts"
                                                                    communityPostId = postId
                                                                    communityPostTitle = postTitle
                                                                    communityPanelType = "post"
                                                                }
                                                            )
                                                        }
                                                        "collect" -> {
                                                            val moreCommunityVm: CommunityViewModel = viewModel(key = "split_more_community") {
                                                                CommunityViewModel(
                                                                    communityRepository = RepositoryFactory.getCommunityRepository(this@MainActivity)
                                                                )
                                                            }
                                                            MyCollectPostsScreen(
                                                                token = token,
                                                                viewModel = moreCommunityVm,
                                                                onBackClick = {
                                                                    communityPanelType = ""
                                                                },
                                                                onPostNavigate = { postId, postTitle ->
                                                                    communityPrevType = "collect"
                                                                    communityPostId = postId
                                                                    communityPostTitle = postTitle
                                                                    communityPanelType = "post"
                                                                }
                                                            )
                                                        }
                                                        "blocked" -> {
                                                            val blockedUsersVm: BlockedUsersViewModel = hiltViewModel()
                                                            BlockedUsersScreen(
                                                                token = token,
                                                                viewModel = blockedUsersVm,
                                                                onBackClick = {
                                                                    communityPanelType = ""
                                                                }
                                                            )
                                                        }
                                                        else -> {
                                                            // 未选择时的占位界面
                                                            Box(
                                                                modifier = Modifier.fillMaxSize(),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Column(
                                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                                                        contentDescription = null,
                                                                        modifier = Modifier.size(48.dp),
                                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                                                    )
                                                                    Text(
                                                                        text = "选择一个分区或功能",
                                                                        style = MaterialTheme.typography.bodyLarge,
                                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        "contacts" -> {
                                            ContactsScreen(
                                                navigationState = navigationState
                                            )
                                        }
                                        "discover" -> {
                                            DiscoverScreen(
                                                navigationState = navigationState
                                            )
                                        }
                                        "profile" -> {
                                            ProfileScreen(
                                                userRepository = userRepository,
                                                tokenRepository = tokenRepository,
                                                navigationRepository = navigationRepository,
                                                navigationState = navigationState
                                            )
                                        }
                                    }
                                }
                            } else {
                                // 没有可见的导航项时显示空状态
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("666全关了是吗，自己清数据解决。")
                                }
                            }

                            // 处理搜索页面
                            if (currentScreen == "search") {
                                if (isInitialized) {
                                    SearchScreen(
                                        onBackClick = {
                                            currentScreen = "conversation"
                                        },
                                        onItemClick = { chatId, chatType, chatName ->
                                            // 大屏搜索结果也在右侧面板打开
                                            splitChatId = chatId
                                            splitChatType = chatType
                                            splitChatName = chatName
                                            currentScreen = "conversation"
                                        },
                                        viewModel = searchViewModel,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                        CircularProgressIndicator()
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // 小屏设备：使用Box布局，导航栏覆盖在内容上方（像LibChecker一样）
                    Box(modifier = Modifier.fillMaxSize()) {
                        // 底部padding动态变化 - 当导航栏隐藏时变为0
                        val bottomPadding by animateDpAsState(
                            targetValue = if (navigationState.isVisible) 80.dp else 0.dp,
                            animationSpec = tween(
                                durationMillis = 400,
                                easing = LinearOutSlowInEasing
                            ),
                            label = "contentBottomPadding"
                        )
                        
                        // 主内容区域 - 底部留出导航栏空间
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = bottomPadding) // 导航栏高度动态调整
                        ) {
                            if (visibleNavItems.isNotEmpty()) {
                                val navItem = visibleNavItems.firstOrNull { it.id == currentPageItem } ?: visibleNavItems.first()
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .alpha(pagerAlpha)
                                ) {
                                        when (navItem.id) {
                                        "conversation" -> {
                                                ConversationScreen(
                                                    token = token,
                                                    userId = userId,
                                                    onConversationClick = { chatId, chatType, chatName, avatarUrl ->
                                                        launchChatActivity(
                                                            chatId = chatId,
                                                            chatType = chatType,
                                                            chatName = chatName,
                                                            avatarUrl = avatarUrl,
                                                            userId = userId
                                                        )
                                                    },
                                                    onSearchClick = {
                                                        if (isInitialized) {
                                                            currentScreen = "search"
                                                        }
                                                    },
                                                    onMenuClick = { },
                                                    onProfileClick = {
                                                        currentScreen = "profile"
                                                    },
                                                    tokenRepository = tokenRepository,
                                                    viewModel = conversationViewModel,
                                                    navigationState = navigationState
                                                )
                                            }
                                            "community" -> {
                                                CommunityScreen(
                                                    token = token,
                                                    navigationState = navigationState
                                                )
                                            }
                                            "contacts" -> {
                                                ContactsScreen(
                                                    navigationState = navigationState
                                                )
                                            }
                                            "discover" -> {
                                                DiscoverScreen(
                                                    navigationState = navigationState
                                                )
                                            }
                                            "profile" -> {
                                                ProfileScreen(
                                                    userRepository = userRepository,
                                                    tokenRepository = tokenRepository,
                                                    navigationRepository = navigationRepository,
                                                    navigationState = navigationState
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    // 没有可见的导航项时显示空状态
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("666全关了是吗，自己清数据解决。")
                                    }
                                }

                                // 处理搜索页面
                                if (currentScreen == "search") {
                                    if (isInitialized) {
                                        SearchScreen(
                                            onBackClick = {
                                                currentScreen = "conversation"
                                            },
                                            onItemClick = { chatId, chatType, chatName ->
                                                launchChatActivity(
                                                    chatId = chatId,
                                                    chatType = chatType,
                                                    chatName = chatName,
                                                    avatarUrl = null,
                                                    userId = userId
                                                )
                                            },
                                            viewModel = searchViewModel,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                        CircularProgressIndicator()
                                        }
                                    }
                                }
                            }
                        
                        // 底部导航栏 - 覆盖在内容上方，位于底部
                        AdaptiveNavigationBar(
                            currentScreen = currentPageItem,
                            visibleItems = visibleNavItems,
                            onScreenChange = { screen ->
                                currentScreen = screen
                            },
                            isVisible = navigationState.isVisible,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
    }
    
    /**
     * 显示聊天界面（辅助Composable）
     */
    @Composable
    private fun ShowChatScreen(
        currentChatId: String,
        currentChatType: Int,
        currentChatName: String,
        currentChatAvatarUrl: String?,
        userId: String,
        onBackClick: () -> Unit
    ) {
        if (currentChatId.isNotEmpty()) {
            var imageUriToSend by remember { mutableStateOf<android.net.Uri?>(null) }
            var fileUriToSend by remember { mutableStateOf<android.net.Uri?>(null) }
            var videoUriToSend by remember { mutableStateOf<android.net.Uri?>(null) }
            var cameraImageUri by remember { mutableStateOf<android.net.Uri?>(null) }

            val imagePickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri: android.net.Uri? ->
                uri?.let { imageUriToSend = it }
            }

            val filePickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri: android.net.Uri? ->
                uri?.let { fileUriToSend = it }
            }

            val videoPickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri: android.net.Uri? ->
                uri?.let { videoUriToSend = it }
            }

            val cameraLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.TakePicture()
            ) { success: Boolean ->
                if (success) {
                    cameraImageUri?.let { imageUriToSend = it }
                }
            }

            ChatScreen(
                chatId = currentChatId,
                chatType = currentChatType,
                chatName = currentChatName,
                chatAvatarUrl = currentChatAvatarUrl,
                userId = userId,
                onBackClick = onBackClick,
                onAvatarClick = { userId, userName, chatType, _ ->
                    val groupId = if (chatType == 2) currentChatId else null
                    UserDetailActivity.start(
                        context = this@MainActivity,
                        userId = userId,
                        userName = userName,
                        groupId = groupId
                    )
                },
                onImagePickerClick = {
                    imagePickerLauncher.launch("image/*")
                },
                onFilePickerClick = {
                    filePickerLauncher.launch("*/*")
                },
                onVideoPickerClick = {
                    videoPickerLauncher.launch("video/*")
                },
                onCameraClick = {
                    val photoFile = java.io.File(cacheDir, "camera_${System.currentTimeMillis()}.jpg")
                    cameraImageUri = androidx.core.content.FileProvider.getUriForFile(
                        this@MainActivity,
                        "${packageName}.fileprovider",
                        photoFile
                    )
                    cameraImageUri?.let { uri ->
                        cameraLauncher.launch(uri)
                    }
                },
                imageUriToSend = imageUriToSend,
                fileUriToSend = fileUriToSend,
                videoUriToSend = videoUriToSend,
                onImageSent = {
                    imageUriToSend = null
                    cameraImageUri = null
                },
                onFileSent = {
                    fileUriToSend = null
                },
                onVideoSent = {
                    videoUriToSend = null
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
    
    /**
     * 启动聊天Activity（辅助方法）
     */
    private fun launchChatActivity(
        chatId: String,
        chatType: Int,
        chatName: String,
        avatarUrl: String? = null,
        userId: String? = null
    ) {
        startActivity(Intent(this, com.yhchat.canary.ui.chat.ChatActivity::class.java).apply {
            putExtra("chatId", chatId)
            putExtra("chatType", chatType)
            putExtra("chatName", chatName)
            putExtra("avatarUrl", avatarUrl)
            putExtra("userId", userId)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
    }
    
    /**
     * 处理 Deep Link
     */
    private fun handleDeepLink() {
        intent?.data?.toString()?.let { uriString ->
            if (UnifiedLinkHandler.isChatAddLink(uriString)) {
                // 延迟执行，确保 Activity 初始化完成
                window.decorView.post {
                    ChatAddActivity.start(this, uriString)
                }
            }
        }
    }
    
    
    /**
     * 配置 ImageLoader
     */
    private fun setupImageLoader() {
        val imageLoader = ImageLoader.Builder(this)
            .components {
                // 添加GIF支持
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    add(coil.decode.ImageDecoderDecoder.Factory())
                } else {
                    add(coil.decode.GifDecoder.Factory())
                }
                // WebP支持已经内置在Coil中
            }
            .okHttpClient {
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val request = chain.request()
                        val url = request.url.toString()
                        
                        // 为chat-img.jwznb.com的请求添加Referer
                        if (url.contains("chat-img.jwznb.com")) {
                            val newRequest = request.newBuilder()
                                .addHeader("Referer", "https://myapp.jwznb.com")
                                .build()
                            chain.proceed(newRequest)
                        } else {
                            chain.proceed(request)
                        }
                    }
                    .build()
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
        
        Coil.setImageLoader(imageLoader)
    }
}


@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    Text(
        text = "欢迎使用云湖聊天！",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    YhchatCanaryTheme {
        MainScreen()
    }
}
