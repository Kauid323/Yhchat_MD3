package com.yhchat.canary.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.yhchat.canary.MainActivity
import com.yhchat.canary.data.di.RepositoryFactory
import com.yhchat.canary.data.model.SavedAccount
import com.yhchat.canary.data.model.UserProfile
import com.yhchat.canary.data.repository.AccountRepository
import com.yhchat.canary.data.repository.NavigationRepository
import com.yhchat.canary.data.repository.TokenRepository
import com.yhchat.canary.data.repository.UserRepository
import com.yhchat.canary.data.websocket.ConnectionState
import com.yhchat.canary.data.websocket.WebSocketService
import com.yhchat.canary.service.AudioPlayerService
import com.yhchat.canary.ui.adaptive.YhCard
import com.yhchat.canary.ui.adaptive.YhAlertDialog
import com.yhchat.canary.ui.adaptive.YhBottomSheet
import com.yhchat.canary.ui.adaptive.YhButton
import com.yhchat.canary.ui.adaptive.YhDropdownMenu
import com.yhchat.canary.ui.adaptive.YhDropdownMenuItem
import com.yhchat.canary.ui.adaptive.YhFilterChip
import com.yhchat.canary.ui.adaptive.YhHorizontalDivider
import com.yhchat.canary.ui.adaptive.YhIcon as Icon
import com.yhchat.canary.ui.adaptive.YhIconButton
import com.yhchat.canary.ui.adaptive.YhListItem
import com.yhchat.canary.ui.adaptive.YhOutlinedButton
import com.yhchat.canary.ui.adaptive.YhOutlinedTextField
import com.yhchat.canary.ui.adaptive.YhRadioButton
import com.yhchat.canary.ui.adaptive.YhScaffold
import com.yhchat.canary.ui.adaptive.YhSlider
import com.yhchat.canary.ui.adaptive.YhSettingsGroup
import com.yhchat.canary.ui.adaptive.YhSettingsItemCell
import com.yhchat.canary.ui.adaptive.YhSettingsSwitchItem
import com.yhchat.canary.ui.adaptive.YhSwitch
import com.yhchat.canary.ui.adaptive.YhSurface
import com.yhchat.canary.ui.adaptive.YhTabRow
import com.yhchat.canary.ui.adaptive.YhText as Text
import com.yhchat.canary.ui.adaptive.YhTextButton
import com.yhchat.canary.ui.adaptive.YhTopBar
import com.yhchat.canary.ui.adaptive.yhTopBarNestedScroll
import com.yhchat.canary.ui.community.BoardDetailActivity
import com.yhchat.canary.ui.community.PostDetailActivity
import com.yhchat.canary.ui.components.rememberBooleanPreference
import com.yhchat.canary.ui.login.LoginActivity
import com.yhchat.canary.ui.sticker.StickerPackDetailActivity
import com.yhchat.canary.ui.share.ExternalShareManagerActivity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch

/**
 * 设置界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navigationRepository: NavigationRepository? = null,
    tokenRepository: TokenRepository? = null,
    accountRepository: AccountRepository? = null,
    onLogout: () -> Unit = {},
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // 聊天界面动画偏好
    val chatPrefs = remember { context.getSharedPreferences("chat_settings", Context.MODE_PRIVATE) }
    val enableChatAnimations by rememberBooleanPreference(
        preferencesName = "chat_settings",
        key = "enable_chat_animations",
        defaultValue = true
    )
    val enableMessageListDragAnimation by rememberBooleanPreference(
        preferencesName = "chat_settings",
        key = "enable_message_list_drag_animation",
        defaultValue = true
    )
    val enableChatContextMenu by rememberBooleanPreference(
        preferencesName = "chat_settings",
        key = "enable_chat_context_menu",
        defaultValue = true
    )

    // 获取用户信息
    var userEmail by remember { mutableStateOf("") }

    val resolvedAccountRepository = accountRepository ?: remember { RepositoryFactory.getAccountRepository(context) }
    val savedAccounts: List<SavedAccount> by resolvedAccountRepository.getAllAccounts()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val currentUserId: String? = resolvedAccountRepository.getCurrentUserId()
    val appContext = context.applicationContext
    val wsEntryPoint = remember(appContext) {
        EntryPointAccessors.fromApplication(appContext, SettingsWsEntryPoint::class.java)
    }
    val webSocketService = remember(wsEntryPoint) { wsEntryPoint.webSocketService() }
    var showAccountSwitchSheet by remember { mutableStateOf(false) }
    var showYhTools by remember { mutableStateOf(false) }

    LaunchedEffect(tokenRepository, resolvedAccountRepository) {
        tokenRepository?.let { tokenRepo: TokenRepository ->
            val userRepo: UserRepository = com.yhchat.canary.data.di.RepositoryFactory.getUserRepository(context)
            userRepo.setTokenRepository(tokenRepo)
            userRepo.getUserProfile().onSuccess { profile: UserProfile ->
                userEmail = profile.email ?: ""
                val token = tokenRepo.getTokenSync()
                if (!token.isNullOrBlank()) {
                    tokenRepo.saveToken(token, profile.userId)
                    resolvedAccountRepository.saveAccount(
                        userId = profile.userId,
                        name = profile.nickname,
                        avatarUrl = profile.avatarUrl,
                        displayId = profile.userId,
                        token = token
                    )
                }
            }
        }
    }
    
    var showLogoutDialog by remember { mutableStateOf(false) }
    YhScaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            YhTopBar(
                title = "设置",
                large = true,
                navigationIcon = {
                    YhIconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
            )
        }
    ) { innerPadding ->
        // 设置项列表
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .yhTopBarNestedScroll()
                .padding(innerPadding),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // 界面设置
            item {
                SettingsGroup(
                    title = "界面",
                    items = listOf(
                        {
                            SettingsItemCell(
                                icon = Icons.Default.ViewAgenda,
                                title = "布局设置",
                                subtitle = "自定义各界面的显示项",
                                onClick = {
                                    LayoutSettingsActivity.start(context)
                                }
                            )
                        },
                        {
                            SettingsItemCell(
                                icon = Icons.Default.Menu,
                                title = "底部导航栏设置",
                                subtitle = "自定义导航栏显示和排序",
                                onClick = {
                                    navigationRepository?.let {
                                        NavigationSettingsActivity.start(context, it)
                                    }
                                }
                            )
                        }
                        ,
                        {
                            SettingsSwitchItem(
                                icon = Icons.Default.PlayArrow,
                                title = "聊天界面动画",
                                subtitle = if (enableChatAnimations) "启用聊天界面内的动画效果" else "禁用聊天界面内的动画效果",
                                checked = enableChatAnimations,
                                onCheckedChange = { checked ->
                                    chatPrefs.edit().putBoolean("enable_chat_animations", checked).apply()
                                }
                            )
                        },
                        {
                            SettingsSwitchItem(
                                icon = Icons.Default.DragHandle,
                                title = "消息列表拖拽动画",
                                subtitle = if (enableMessageListDragAnimation) {
                                    "启用聊天消息列表的位置变化动画"
                                } else {
                                    "禁用聊天消息列表的位置变化动画"
                                },
                                checked = enableMessageListDragAnimation,
                                onCheckedChange = { checked ->
                                    chatPrefs.edit()
                                        .putBoolean("enable_message_list_drag_animation", checked)
                                        .apply()
                                }
                            )
                        },
                        {
                            SettingsSwitchItem(
                                icon = Icons.Default.MoreVert,
                                title = "聊天上下文弹窗",
                                subtitle = if (enableChatContextMenu) {
                                    "长按消息时显示默认消息操作弹窗"
                                } else {
                                    "禁用长按消息的默认消息操作弹窗"
                                },
                                checked = enableChatContextMenu,
                                onCheckedChange = { checked ->
                                    chatPrefs.edit()
                                        .putBoolean("enable_chat_context_menu", checked)
                                        .apply()
                                }
                            )
                        }
                    )
                )
            }
            
            // 账户设置
            item {
                SettingsGroup(
                    title = "账户",
                    items = listOf(
                        {
                            SettingsItemCell(
                                icon = Icons.Default.Devices,
                                title = "在线设备",
                                subtitle = "查看当前登录的设备",
                                onClick = {
                                    OnlineDevicesActivity.start(context)
                                }
                            )
                        },
                        {
                            SettingsItemCell(
                                icon = Icons.Default.SwapHoriz,
                                title = "切换账号",
                                subtitle = "已添加 ${savedAccounts.size} 个账号",
                                onClick = {
                                    showAccountSwitchSheet = true
                                }
                            )
                        },
                        {
                            SettingsItemCell(
                                icon = Icons.Default.Lock,
                                title = "修改密码",
                                subtitle = "更改账户登录密码",
                                onClick = {
                                    // 启动修改密码Activity，传递用户邮箱
                                    val intent = ChangePasswordActivity.createIntent(context, userEmail)
                                    context.startActivity(intent)
                                }
                            )
                        },
                        {
                            SettingsItemCell(
                                icon = Icons.Default.Block,
                                title = "黑名单",
                                subtitle = "管理屏蔽的用户列表",
                                onClick = {
                                    com.yhchat.canary.ui.blocklist.BlacklistActivity.start(context)
                                }
                            )
                        },
                        {
                            var ncmLoggedIn by remember { mutableStateOf(com.yhchat.canary.ncm.NcmAccountManager.isLoggedIn(context)) }
                            var showNcmAccountDialog by remember { mutableStateOf(false) }

                            LaunchedEffect(Unit) {
                                ncmLoggedIn = com.yhchat.canary.ncm.NcmAccountManager.isLoggedIn(context)
                            }

                            if (showNcmAccountDialog) {
                                YhAlertDialog(
                                    onDismissRequest = { showNcmAccountDialog = false },
                                    title = { Text("网易云账号管理") },
                                    text = { Text("当前已登录网易云音乐账号。\n如需更换或重新抓取Cookie，可点击“重新登录”；如需退出可点击“退出登录”。") },
                                    confirmButton = {
                                        YhButton(onClick = {
                                            showNcmAccountDialog = false
                                            com.yhchat.canary.ui.webview.WebViewActivity.startNcmLogin(context)
                                        }) {
                                            Text("重新登录")
                                        }
                                    },
                                    dismissButton = {
                                        YhTextButton(onClick = {
                                            com.yhchat.canary.ncm.NcmAccountManager.clearCookie(context)
                                            ncmLoggedIn = false
                                            showNcmAccountDialog = false
                                            android.widget.Toast.makeText(context, "已退出网易云账号", android.widget.Toast.LENGTH_SHORT).show()
                                        }) {
                                            Text("退出登录")
                                        }
                                    }
                                )
                            }

                            SettingsItemCell(
                                icon = Icons.Default.CloudQueue,
                                title = "网易云账号",
                                subtitle = if (ncmLoggedIn) "已登录 (点击可管理或退出)" else "未登录，点击进入网页端电脑版登录",
                                onClick = {
                                    if (ncmLoggedIn) {
                                        showNcmAccountDialog = true
                                    } else {
                                        com.yhchat.canary.ui.webview.WebViewActivity.startNcmLogin(context)
                                    }
                                }
                            )
                        }
                    )
                )
            }
            
            // 内容设置
            item {
                ContentSettingsGroup(context = context)
            }

            item {
                SettingsGroup(
                    title = "分享",
                    items = listOf(
                        {
                            SettingsItemCell(
                                icon = Icons.Default.Share,
                                title = "外部分享",
                                subtitle = "管理默认分享应用和会话专属分享目标",
                                onClick = {
                                    ExternalShareManagerActivity.start(context)
                                }
                            )
                        }
                    )
                )
            }

            item {
                SettingsGroup(
                    title = "云湖工具",
                    items = listOf(
                        {
                            SettingsItemCell(
                                icon = Icons.Default.Build,
                                title = "云湖工具箱",
                                subtitle = if (showYhTools) "点击收起工具栏" else "社区/表情包/WebSocket 快捷入口",
                                onClick = { showYhTools = !showYhTools }
                            )
                        }
                    )
                )
            }

            item {
                AnimatedVisibility(
                    visible = showYhTools,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    YhToolsPanel(
                        context = context,
                        currentUserId = currentUserId,
                        accountRepository = resolvedAccountRepository
                    )
                }
            }

            item {
                SettingsGroup(
                    title = "通知",
                    items = listOf(
                        {
                            SettingsItemCell(
                                icon = Icons.Default.Close,
                                title = "强制关闭音乐通知",
                                subtitle = "立即关闭语音/音频播放前台通知，不影响普通消息通知",
                                onClick = {
                                    AudioPlayerService.forceCloseMusicNotification(context)
                                    android.widget.Toast.makeText(
                                        context,
                                        "已尝试关闭音乐通知",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                },
                                isDestructive = true
                            )
                        }
                    )
                )
            }
            
            // 显示设置
            item {
                DisplaySettingsGroup(context = context)
            }
            
            // 主题设置
            item {
                ThemeSettingsGroup(context = context)
            }
            
            // 个性化设置
            item {
                PersonalizationSettingsGroup(context = context)
            }
            
            // 关于应用
            item {
                SettingsGroup(
                    title = "关于",
                    items = listOf(
                        {
                            SettingsItemCell(
                                icon = Icons.Default.Info,
                                title = "应用详情",
                                subtitle = "查看应用版本和开发者信息",
                                onClick = {
                                    AppInfoActivity.start(context)
                                }
                            )
                        }
                    )
                )
            }
            
            // 账户设置
            item {
                SettingsGroup(
                    title = "账户管理",
                    items = listOf(
                        {
                            SettingsItemCell(
                                icon = Icons.AutoMirrored.Filled.ExitToApp,
                                title = "退出登录",
                                subtitle = "安全退出当前账户",
                                onClick = {
                                    showLogoutDialog = true
                                },
                                isDestructive = true
                            )
                        }
                    )
                )
            }
            
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // 退出登录确认对话框
    if (showLogoutDialog) {
        YhAlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
                Text(
                    text = "确认退出登录"
                )
            },
            text = {
                Text(
                    text = "退出登录后需要重新输入账号密码，确定要退出吗？"
                )
            },
            confirmButton = {
                YhTextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    }
                ) {
                    Text("退出", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                YhTextButton(
                    onClick = { showLogoutDialog = false }
                ) {
                    Text("取消")
                }
            }
        )
    }

    if (showAccountSwitchSheet) {
        AccountSwitchBottomSheet(
            currentUserId = currentUserId,
            accounts = savedAccounts,
            onDismiss = { showAccountSwitchSheet = false },
            onAddAccount = {
                showAccountSwitchSheet = false
                LoginActivity.start(context)
            },
            onDeleteAccount = { userId ->
                coroutineScope.launch {
                    resolvedAccountRepository.deleteAccount(userId)
                }
            },
            onSwitchAccount = { userId ->
                coroutineScope.launch {
                    val repo = accountRepository ?: resolvedAccountRepository
                    if (repo.getCurrentUserId() == userId) {
                        showAccountSwitchSheet = false
                        return@launch
                    }
                    webSocketService.disconnect()
                    webSocketService.clearRuntimeCaches()
                    val switched = repo.switchToAccount(userId)
                    if (switched) {
                        showAccountSwitchSheet = false
                        webSocketService.connect(userId, "android")
                        val intent = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        context.startActivity(intent)
                        (context as? Activity)?.finish()
                    } else {
                        android.widget.Toast
                            .makeText(context, "切换失败：未找到该账号 token，请重新登录", android.widget.Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountSwitchBottomSheet(
    currentUserId: String?,
    accounts: List<SavedAccount>,
    onDismiss: () -> Unit,
    onAddAccount: () -> Unit,
    onDeleteAccount: (String) -> Unit,
    onSwitchAccount: (String) -> Unit
) {
    YhBottomSheet(
        onDismissRequest = onDismiss
    ) {
        val activity = LocalContext.current as? android.app.Activity
        val sheetColor = MaterialTheme.colorScheme.surface
        val darkIcons = sheetColor.luminance() > 0.5f
        if (activity != null) {
            com.yhchat.canary.ui.base.SystemBarUtils.ApplyNavigationBarColor(activity, sheetColor, darkIcons)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "切换账号",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                YhTextButton(onClick = onAddAccount) {
                    Text("添加账号")
                }
            }

            YhHorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(accounts, key = { it.userId }) { account ->
                    val isCurrent = account.userId == currentUserId

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSwitchAccount(account.userId) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!account.avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = account.avatarUrl,
                                contentDescription = "头像",
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "头像",
                                modifier = Modifier.size(44.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = account.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                if (isCurrent) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    YhFilterChip(
                                        selected = true,
                                        onClick = {},
                                    ) {
                                        Text("当前")
                                    }
                                }
                            }
                            Text(
                                text = account.displayId,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        YhIconButton(
                            onClick = { onDeleteAccount(account.userId) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                if (accounts.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "还没有添加账号",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            YhButton(onClick = onAddAccount) {
                                Text("添加账号")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 显示设置组
 */
@Composable
private fun DisplaySettingsGroup(
    context: Context,
    modifier: Modifier = Modifier
) {
    val prefs = remember { 
        context.getSharedPreferences("display_settings", Context.MODE_PRIVATE) 
    }
    
    // 内联表情显示开关
    var showInlineExpressions by remember { 
        mutableStateOf(prefs.getBoolean("show_inline_expressions", true)) 
    }

    // 表情选择器点击后自动收回
    var autoDismissExpressionPicker by remember {
        mutableStateOf(prefs.getBoolean("auto_dismiss_expression_picker", true))
    }

    SettingsGroup(
        title = "显示设置",
        items = listOf(
            {
                SettingsSwitchItem(
                    icon = Icons.Default.EmojiEmotions,
                    title = "显示内联表情",
                    subtitle = "在聊天气泡中直接渲染 [.表情]",
                    checked = showInlineExpressions,
                    onCheckedChange = { checked ->
                        showInlineExpressions = checked
                        prefs.edit().putBoolean("show_inline_expressions", checked).apply()
                    }
                )
            },
            {
                SettingsSwitchItem(
                    icon = Icons.Default.KeyboardArrowDown,
                    title = "选择表情后自动收回",
                    subtitle = if (autoDismissExpressionPicker) "点击表情后会自动关闭表情面板" else "点击表情后不会自动关闭表情面板",
                    checked = autoDismissExpressionPicker,
                    onCheckedChange = { checked ->
                        autoDismissExpressionPicker = checked
                        prefs.edit().putBoolean("auto_dismiss_expression_picker", checked).apply()
                    }
                )
            },
            { LongPressSendMarkdownSettingItem(context = context) },
            { FontSizeSettingItem(context = context) },
            { GlobalScaleSettingItem(context = context) },
            { MemoryAutoCleanSettingItem(context = context) },
            { DataSaverSettingItem(context = context) },
            { WebSocketSettingItem(context = context) },
            { WebPQualitySettingItem(context = context) },
            { HtmlRawTextSettingItem(context = context) },
            { MarkdownRawTextSettingItem(context = context) },
            { A2UiRawTextSettingItem(context = context) },
            { LongMessageAutoCollapseSettingItem(context = context) }
        )
    )
}

/**
 * TTS (文字转语音) 设置组
 */
@Composable
private fun TTSSettingsGroup(
    context: Context,
    modifier: Modifier = Modifier
) {
    val prefs = remember {
        context.getSharedPreferences("tts_settings", Context.MODE_PRIVATE)
    }
    
    var ttsEnabled by remember {
        mutableStateOf(prefs.getBoolean("tts_enabled", false))
    }
    
    var speechRate by remember {
        mutableFloatStateOf(prefs.getFloat("tts_speech_rate", 1.0f))
    }
    
    var pitch by remember {
        mutableFloatStateOf(prefs.getFloat("tts_pitch", 1.0f))
    }
    
    SettingsGroup(
        title = "文字转语音 (TTS)",
        items = listOf(
            {
                SettingsSwitchItem(
                    icon = Icons.Default.RecordVoiceOver,
                    title = "启用TTS功能",
                    subtitle = if (ttsEnabled) "输入文字后可转换为语音发送" else "关闭文字转语音功能",
                    checked = ttsEnabled,
                    onCheckedChange = { checked ->
                        ttsEnabled = checked
                        prefs.edit().putBoolean("tts_enabled", checked).apply()
                    }
                )
            },
            {
                TTSSpeechRateSettingItem(
                    speechRate = speechRate,
                    enabled = ttsEnabled,
                    onSpeechRateChange = { newRate ->
                        speechRate = newRate
                        prefs.edit().putFloat("tts_speech_rate", newRate).apply()
                    }
                )
            },
            {
                TTSPitchSettingItem(
                    pitch = pitch,
                    enabled = ttsEnabled,
                    onPitchChange = { newPitch ->
                        pitch = newPitch
                        prefs.edit().putFloat("tts_pitch", newPitch).apply()
                    }
                )
            }
        )
    )
}

/**
 * TTS语速设置项
 */
@Composable
private fun TTSSpeechRateSettingItem(
    speechRate: Float,
    enabled: Boolean,
    onSpeechRateChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsCustomItem {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = "语速",
                    modifier = Modifier.size(24.dp),
                    tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "语速",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "当前: ${String.format("%.1f", speechRate)}x",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            YhSlider(
                value = speechRate,
                onValueChange = onSpeechRateChange,
                valueRange = 0.5f..2.0f,
                steps = 14,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "慢 (0.5x)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "快 (2.0x)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * TTS音调设置项
 */
@Composable
private fun TTSPitchSettingItem(
    pitch: Float,
    enabled: Boolean,
    onPitchChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsCustomItem {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "音调",
                    modifier = Modifier.size(24.dp),
                    tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "音调",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "当前: ${String.format("%.1f", pitch)}x",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            YhSlider(
                value = pitch,
                onValueChange = onPitchChange,
                valueRange = 0.5f..2.0f,
                steps = 14,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "低 (0.5x)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "高 (2.0x)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 主题设置组
 */
@Composable
private fun ThemeSettingsGroup(
    context: Context,
    modifier: Modifier = Modifier
) {
    var showThemeDialog by remember { mutableStateOf(false) }
    val prefs = remember {
        context.getSharedPreferences("display_settings", Context.MODE_PRIVATE)
    }
    var themeMode by remember {
        mutableStateOf(prefs.getString("theme_mode", "system") ?: "system")
    }

    val themeOptions = listOf(
        "system" to "跟随系统",
        "light" to "浅色主题",
        "dark" to "深色主题"
    )
    val currentThemeLabel = themeOptions.find { it.first == themeMode }?.second ?: "跟随系统"

    SettingsGroup(
        title = "主题设置",
        items = listOf(
            {
                SettingsItemCell(
                    icon = Icons.Default.Palette,
                    title = "主题模式",
                    subtitle = "当前: $currentThemeLabel",
                    onClick = { showThemeDialog = true }
                )
            }
        )
    )

    if (showThemeDialog) {
        YhAlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("选择主题模式") },
            text = {
                Column {
                    themeOptions.forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    themeMode = value
                                    prefs.edit().putString("theme_mode", value).apply()
                                    showThemeDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            YhRadioButton(
                                selected = themeMode == value,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                YhTextButton(onClick = { showThemeDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun LongPressSendMarkdownSettingItem(
    context: Context,
    modifier: Modifier = Modifier
) {
    val prefs = remember {
        context.getSharedPreferences("chat_settings", Context.MODE_PRIVATE)
    }

    var enabled by remember {
        mutableStateOf(prefs.getBoolean("long_press_send_markdown_enabled", false))
    }

    var seconds by remember {
        mutableStateOf(prefs.getInt("long_press_send_markdown_seconds", 3).coerceIn(1, 10))
    }

    SettingsCustomItem {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "长按自动发送 Markdown",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "长按发送自动发 Markdown",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (enabled) "已开启：长按 ${seconds}s 后自动以 Markdown 发送" else "默认关闭",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                YhSwitch(
                    checked = enabled,
                    onCheckedChange = { checked ->
                        enabled = checked
                        prefs.edit().putBoolean("long_press_send_markdown_enabled", checked).apply()
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            YhSlider(
                value = seconds.toFloat(),
                onValueChange = { seconds = it.toInt().coerceIn(1, 10) },
                onValueChangeFinished = {
                    prefs.edit().putInt("long_press_send_markdown_seconds", seconds).apply()
                },
                valueRange = 1f..10f,
                steps = 8,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "1s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "10s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * WebSocket 设置项
 */
@Composable
private fun WebSocketSettingItem(
    context: Context,
    modifier: Modifier = Modifier
) {
    val prefs = remember { 
        context.getSharedPreferences("display_settings", Context.MODE_PRIVATE) 
    }
    
    var disableWebSocket by remember { 
        mutableStateOf(prefs.getBoolean("disable_websocket", false)) 
    }
    
    SettingsSwitchItem(
        icon = Icons.Default.CloudOff,
        title = "禁用 WebSocket",
        subtitle = if (disableWebSocket) "已禁用实时消息推送，需重启应用生效" else "启用实时消息推送",
        checked = disableWebSocket,
        onCheckedChange = { checked ->
            disableWebSocket = checked
            prefs.edit().putBoolean("disable_websocket", checked).apply()
        },
        isError = disableWebSocket
    )
}

/**
 * 字体大小设置项
 */
@Composable
private fun FontSizeSettingItem(
    context: Context,
    modifier: Modifier = Modifier
) {
    val prefs = remember { 
        context.getSharedPreferences("display_settings", Context.MODE_PRIVATE) 
    }
    
    var fontScale by remember { 
        mutableStateOf(prefs.getFloat("font_scale", 100f)) 
    }
    
    SettingsCustomItem {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.FormatSize,
                    contentDescription = "字体大小",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "字体大小",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${fontScale.toInt()}% (重启应用生效)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 滑动条
            YhSlider(
                value = fontScale,
                onValueChange = { newValue ->
                    fontScale = newValue
                },
                onValueChangeFinished = {
                    // 保存设置
                    prefs.edit()
                        .putFloat("font_scale", fontScale)
                        .apply()
                },
                valueRange = 1f..100f,
                modifier = Modifier.fillMaxWidth()
            )
            
            // 质量说明
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "小 (1%)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "大 (100%)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 全局组件大小无极调节
 */
@Composable
private fun GlobalScaleSettingItem(
    context: Context,
    modifier: Modifier = Modifier
) {
    val prefs = remember {
        context.getSharedPreferences("display_settings", Context.MODE_PRIVATE)
    }

    var scale by remember {
        mutableStateOf(prefs.getFloat("global_scale", 1.0f))
    }

    SettingsCustomItem {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "组件大小",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "组件大小",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = String.format("%.0f%% (部分界面需重启生效)", scale * 100f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "50%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.width(8.dp))

                YhSlider(
                    value = scale,
                    onValueChange = { newValue ->
                        val clamped = newValue.coerceIn(0.5f, 1.5f)
                        scale = clamped
                        prefs.edit().putFloat("global_scale", clamped).apply()
                    },
                    valueRange = 0.5f..1.5f,
                    steps = 0,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "150%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MemoryAutoCleanSettingItem(
    context: Context,
    modifier: Modifier = Modifier
) {
    val prefs = remember {
        context.getSharedPreferences("display_settings", Context.MODE_PRIVATE)
    }

    var enabled by remember {
        mutableStateOf(prefs.getBoolean("memory_auto_clean_enabled", false))
    }
    var thresholdValueText by remember {
        mutableStateOf(prefs.getFloat("memory_auto_clean_threshold_value", 512f).toString())
    }
    var unit by remember {
        mutableStateOf(prefs.getString("memory_auto_clean_threshold_unit", "MB") ?: "MB")
    }
    var unitExpanded by remember { mutableStateOf(false) }

    SettingsCustomItem {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = "内存自动清理",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "内存阈值自动清理",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (enabled) "达到阈值时自动清理缓存" else "关闭时不自动清理",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                YhSwitch(
                    checked = enabled,
                    onCheckedChange = { checked ->
                        enabled = checked
                        prefs.edit().putBoolean("memory_auto_clean_enabled", checked).apply()
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                YhOutlinedTextField(
                    value = thresholdValueText,
                    onValueChange = { newValue ->
                        thresholdValueText = newValue
                        val parsed = newValue.toFloatOrNull()
                        if (parsed != null) {
                            prefs.edit().putFloat("memory_auto_clean_threshold_value", parsed).apply()
                        }
                    },
                    enabled = enabled,
                    singleLine = true,
                    label = { Text("阈值") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Box {
                    YhOutlinedButton(
                        onClick = { unitExpanded = true },
                        enabled = enabled
                    ) {
                        Text(unit)
                    }

                    YhDropdownMenu(
                        expanded = unitExpanded,
                        onDismissRequest = { unitExpanded = false }
                    ) {
                        YhDropdownMenuItem(
                            text = { Text("MB") },
                            onClick = {
                                unit = "MB"
                                prefs.edit().putString("memory_auto_clean_threshold_unit", unit).apply()
                                unitExpanded = false
                            }
                        )
                        YhDropdownMenuItem(
                            text = { Text("GB") },
                            onClick = {
                                unit = "GB"
                                prefs.edit().putString("memory_auto_clean_threshold_unit", unit).apply()
                                unitExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 省流量模式（不加载全局任何图片）
 */
@Composable
private fun DataSaverSettingItem(
    context: Context,
    modifier: Modifier = Modifier
) {
    val prefs = remember {
        context.getSharedPreferences("display_settings", Context.MODE_PRIVATE)
    }

    var dataSaver by remember {
        mutableStateOf(prefs.getBoolean("data_saver", false))
    }

    SettingsSwitchItem(
        icon = Icons.Default.DataSaverOn,
        title = "省流量模式",
        subtitle = if (dataSaver) "不加载全局图片（头像/消息图片/背景等）" else "默认关闭",
        checked = dataSaver,
        onCheckedChange = { checked ->
            dataSaver = checked
            prefs.edit().putBoolean("data_saver", checked).apply()
        },
        isError = dataSaver
    )
}

/**
 * 机器人看板显示设置项
 */
@Composable
private fun BotBoardSettingItem(
    context: Context,
    modifier: Modifier = Modifier
) {
    val prefs = remember { 
        context.getSharedPreferences("chat_settings", Context.MODE_PRIVATE) 
    }
    
    var showBotBoard by remember { 
        mutableStateOf(prefs.getBoolean("show_bot_board", true)) 
    }
    
    SettingsSwitchItem(
        icon = Icons.Default.Dashboard,
        title = "显示机器人看板",
        subtitle = if (showBotBoard) "在聊天界面显示群聊机器人看板和单机器人看板" else "隐藏所有机器人看板",
        checked = showBotBoard,
        onCheckedChange = { checked ->
            showBotBoard = checked
            prefs.edit().putBoolean("show_bot_board", checked).apply()
        }
    )
}

/**
 * 菜单按钮栏显示设置项
 */
@Composable
private fun MenuButtonsSettingItem(
    context: Context,
    modifier: Modifier = Modifier
) {
    val prefs = remember { 
        context.getSharedPreferences("chat_settings", Context.MODE_PRIVATE) 
    }
    
    var showMenuButtons by remember { 
        mutableStateOf(prefs.getBoolean("show_menu_buttons", true)) 
    }
    
    SettingsSwitchItem(
        icon = Icons.Default.Apps,
        title = "显示菜单按钮栏",
        subtitle = if (showMenuButtons) "在聊天输入框上方显示菜单按钮栏" else "隐藏菜单按钮栏",
        checked = showMenuButtons,
        onCheckedChange = { checked ->
            showMenuButtons = checked
            prefs.edit().putBoolean("show_menu_buttons", checked).apply()
        }
    )
}

/**
 * 个性化设置组
 */
@Composable
private fun PersonalizationSettingsGroup(
    context: Context,
    modifier: Modifier = Modifier
) {
    var showColorPickerDialog by remember { mutableStateOf(false) }
    
    SettingsGroup(
        title = "个性化",
        items = listOf(
            {
                SettingsItemCell(
                    icon = Icons.Default.Wallpaper,
                    title = "聊天背景",
                    subtitle = "设置全局聊天背景",
                    onClick = {
                        com.yhchat.canary.ui.background.ChatBackgroundActivity.start(
                            context,
                            "all",
                            "全局"
                        )
                    }
                )
            },
            {
                ThemeColorSettingItem(
                    context = context,
                    onColorPickerClick = { showColorPickerDialog = true }
                )
            }
        )
    )
    
    // 颜色选择对话框
    if (showColorPickerDialog) {
        ColorPickerDialog(
            context = context,
            onDismiss = { showColorPickerDialog = false }
        )
    }
}

/**
 * 主题颜色设置项
 */
@Composable
private fun ThemeColorSettingItem(
    context: Context,
    onColorPickerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val prefs = remember { 
        context.getSharedPreferences("theme_settings", Context.MODE_PRIVATE) 
    }
    
    val currentColorInt = prefs.getInt("custom_primary_color", 0xFF6200EE.toInt())
    val currentColor = Color(currentColorInt)
    val isCustomColor = currentColorInt != 0xFF6200EE.toInt()
    
    SettingsCustomItem(onClick = onColorPickerClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Palette,
                contentDescription = "主题颜色",
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "主题颜色",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isCustomColor) {
                        "已自定义（会覆盖莫奈取色，需重启生效）"
                    } else {
                        "使用默认配色或莫奈取色（Android 12+）"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // 当前颜色预览
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(currentColor)
                    .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "编辑",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 颜色选择对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorPickerDialog(
    context: Context,
    onDismiss: () -> Unit
) {
    val prefs = remember { 
        context.getSharedPreferences("theme_settings", Context.MODE_PRIVATE) 
    }
    
    val currentColorInt = prefs.getInt("custom_primary_color", 0xFF6200EE.toInt())
    var selectedColor by remember { mutableStateOf(Color(currentColorInt)) }
    var colorInput by remember { 
        mutableStateOf(String.format("#%06X", currentColorInt and 0xFFFFFF)) 
    }
    var errorMessage by remember { mutableStateOf("") }
    
    // 预设颜色
    val presetColors = listOf(
        Color(0xFF6200EE), // 默认紫色
        Color(0xFFFF5722), // 橙红色
        Color(0xFFF44336), // 红色
        Color(0xFFE91E63), // 粉色
        Color(0xFF9C27B0), // 紫色
        Color(0xFF673AB7), // 深紫色
        Color(0xFF3F51B5), // 靛蓝色
        Color(0xFF2196F3), // 蓝色
        Color(0xFF03A9F4), // 浅蓝色
        Color(0xFF00BCD4), // 青色
        Color(0xFF009688), // 蓝绿色
        Color(0xFF4CAF50), // 绿色
        Color(0xFF8BC34A), // 浅绿色
        Color(0xFFCDDC39), // 黄绿色
        Color(0xFFFFEB3B), // 黄色
        Color(0xFFFFC107), // 琥珀色
        Color(0xFFFF9800), // 橙色
        Color(0xFF795548), // 棕色
        Color(0xFF607D8B), // 蓝灰色
        Color(0xFF000000)  // 黑色
    )
    
    YhAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "自定义主题颜色",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 颜色预览
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(selectedColor)
                            .border(3.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column {
                        Text(
                            text = "当前颜色",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = colorInput,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // 颜色输入框
                YhOutlinedTextField(
                    value = colorInput,
                    onValueChange = { input ->
                        colorInput = input.uppercase()
                        errorMessage = ""
                        
                        // 验证并解析颜色
                        if (input.matches(Regex("^#[0-9A-Fa-f]{6}$"))) {
                            try {
                                val colorInt = android.graphics.Color.parseColor(input)
                                selectedColor = Color(colorInt)
                            } catch (e: Exception) {
                                errorMessage = "无效的颜色值"
                            }
                        } else if (input.isNotEmpty() && !input.startsWith("#")) {
                            errorMessage = "颜色值必须以 # 开头"
                        } else if (input.length > 7) {
                            errorMessage = "颜色值格式: #RRGGBB (6位十六进制)"
                        }
                    },
                    label = { Text("颜色值") },
                    placeholder = { Text("#6200EE") },
                    supportingText = {
                        if (errorMessage.isNotEmpty()) {
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Text("格式: #RRGGBB (例如 #6200EE)")
                        }
                    },
                    isError = errorMessage.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Tag,
                            contentDescription = "颜色值"
                        )
                    }
                )
                
                // 预设颜色选择器
                Text(
                    text = "快速选择",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                
                // 颜色网格
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetColors.chunked(5).forEach { rowColors ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowColors.forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (color == selectedColor) 3.dp else 1.dp,
                                            color = if (color == selectedColor) 
                                                MaterialTheme.colorScheme.primary 
                                            else 
                                                MaterialTheme.colorScheme.outline,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            selectedColor = color
                                            colorInput = String.format("#%06X", color.toArgb() and 0xFFFFFF)
                                            errorMessage = ""
                                        }
                                )
                            }
                        }
                    }
                }
                
                // 重置按钮
                YhTextButton(
                    onClick = {
                        selectedColor = Color(0xFF6200EE)
                        colorInput = "#6200EE"
                        errorMessage = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "重置",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("重置为默认颜色")
                }
            }
        },
        confirmButton = {
            YhTextButton(
                onClick = {
                    // 保存颜色
                    if (colorInput.matches(Regex("^#[0-9A-Fa-f]{6}$"))) {
                        val colorInt = android.graphics.Color.parseColor(colorInput)
                        prefs.edit().putInt("custom_primary_color", colorInt).apply()
                        android.widget.Toast.makeText(
                            context,
                            "主题颜色已保存，重启应用后生效",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        onDismiss()
                    } else {
                        errorMessage = "请输入有效的颜色值"
                    }
                },
                enabled = colorInput.matches(Regex("^#[0-9A-Fa-f]{6}$"))
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            YhTextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * WebP压缩质量设置项
 */
@Composable
private fun WebPQualitySettingItem(
    context: Context,
    modifier: Modifier = Modifier
) {
    val prefs = remember { 
        context.getSharedPreferences("image_settings", Context.MODE_PRIVATE) 
    }
    
    var webpQuality by remember { 
        mutableFloatStateOf(prefs.getInt("webp_quality", 95).toFloat()) 
    }
    
    SettingsCustomItem {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = "图片压缩",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "WebP压缩质量",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "当前: ${webpQuality.toInt()}% (数值越高质量越好，文件越大)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 滑动条
            YhSlider(
                value = webpQuality,
                onValueChange = { newValue ->
                    webpQuality = newValue
                },
                onValueChangeFinished = {
                    // 保存设置
                    prefs.edit()
                        .putInt("webp_quality", webpQuality.toInt())
                        .apply()
                },
                valueRange = 10f..100f,
                steps = 17, // 10-100，每5一个步长，共18个值，所以17个步长
                modifier = Modifier.fillMaxWidth()
            )
            
            // 质量说明
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "低质量 (10%)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "高质量 (100%)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * HTML消息显示原文设置项
 */
@Composable
private fun HtmlRawTextSettingItem(
    context: Context,
    modifier: Modifier = Modifier
) {
    val prefs = remember { 
        context.getSharedPreferences("message_settings", Context.MODE_PRIVATE) 
    }
    
    var showHtmlRawText by remember { 
        mutableStateOf(prefs.getBoolean("show_html_raw_text", false)) 
    }
    
    SettingsSwitchItem(
        icon = Icons.Default.Code,
        title = "HTML消息显示原文",
        subtitle = if (showHtmlRawText) "显示HTML源代码而不是渲染后的网页" else "正常渲染HTML消息",
        checked = showHtmlRawText,
        onCheckedChange = { checked ->
            showHtmlRawText = checked
            prefs.edit().putBoolean("show_html_raw_text", checked).apply()
        },
        isError = false
    )
}

/**
 * Markdown消息显示原文设置项
 */
@Composable
private fun MarkdownRawTextSettingItem(
    context: Context,
    modifier: Modifier = Modifier
) {
    val prefs = remember { 
        context.getSharedPreferences("message_settings", Context.MODE_PRIVATE) 
    }
    
    var showMarkdownRawText by remember { 
        mutableStateOf(prefs.getBoolean("show_markdown_raw_text", false)) 
    }
    
    SettingsSwitchItem(
        icon = Icons.Default.TextFields,
        title = "Markdown消息显示原文",
        subtitle = if (showMarkdownRawText) "显示Markdown源代码而不是渲染后的格式" else "正常渲染Markdown消息",
        checked = showMarkdownRawText,
        onCheckedChange = { checked ->
            showMarkdownRawText = checked
            prefs.edit().putBoolean("show_markdown_raw_text", checked).apply()
        },
        isError = false
    )
}

@Composable
private fun LongMessageAutoCollapseSettingItem(
    context: Context,
    modifier: Modifier = Modifier
) {
    val prefs = remember {
        context.getSharedPreferences("chat_settings", Context.MODE_PRIVATE)
    }

    var autoCollapse by remember {
        mutableStateOf(prefs.getBoolean("auto_collapse_long_messages", true))
    }

    SettingsSwitchItem(
        icon = Icons.Default.UnfoldLess,
        title = "长文自动折叠",
        subtitle = if (autoCollapse) "文本/Markdown 超过800字、HTML超过1500字时自动折叠" else "总是直接展开显示长消息",
        checked = autoCollapse,
        onCheckedChange = { checked ->
            autoCollapse = checked
            prefs.edit().putBoolean("auto_collapse_long_messages", checked).apply()
        }
    )
}

/**
 * A2UI消息显示原文设置项
 */
@Composable
private fun A2UiRawTextSettingItem(
    context: Context,
    modifier: Modifier = Modifier
) {
    val prefs = remember { 
        context.getSharedPreferences("message_settings", Context.MODE_PRIVATE) 
    }
    
    var showA2UiRawText by remember { 
        mutableStateOf(prefs.getBoolean("show_a2ui_raw_text", false)) 
    }
    
    SettingsSwitchItem(
        icon = Icons.Default.Code,
        title = "A2UI消息显示原文",
        subtitle = if (showA2UiRawText) "显示A2UI JSON源代码而不是渲染后的界面" else "正常渲染A2UI消息",
        checked = showA2UiRawText,
        onCheckedChange = { checked ->
            showA2UiRawText = checked
            prefs.edit().putBoolean("show_a2ui_raw_text", checked).apply()
        },
        isError = false
    )
}

/**
 * 设置组容器 (SplicedColumnGroup 风格)
 */
@Composable
fun SettingsGroup(
    title: String? = null,
    items: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier
) {
    YhSettingsGroup(title = title, items = items, modifier = modifier)
}

/**
 * 标准设置项
 */
@Composable
fun SettingsItemCell(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    YhSettingsItemCell(
        icon = icon,
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        isDestructive = isDestructive
    )
}

/**
 * 带 Switch 的设置项
 */
@Composable
fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isError: Boolean = false
) {
    YhSettingsSwitchItem(
        icon = icon,
        title = title,
        subtitle = subtitle,
        checked = checked,
        onCheckedChange = onCheckedChange,
        isError = isError
    )
}

/**
 * 自定义内容的设置项
 */
@Composable
fun SettingsCustomItem(
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    if (onClick != null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
        ) {
            content()
        }
    } else {
        Box(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

/**
 * 内容设置组
 */
@Composable
private fun ContentSettingsGroup(context: Context) {
    // 读取图片上传设置
    val imagePrefs = remember { context.getSharedPreferences("image_settings", Context.MODE_PRIVATE) }
    var uploadOriginalImage by remember { mutableStateOf(imagePrefs.getBoolean("upload_original_image", false)) }
    // 是否允许发送空文本消息设置 //TODO 英语不好不知道起什么变量名
    val sendTextPrefs = remember { context.getSharedPreferences("send_text_settings", Context.MODE_PRIVATE) }
    var is_send_text_allow_empty by remember { mutableStateOf(sendTextPrefs.getBoolean("send_text_allow_empty", false)) }
    
    SettingsGroup(
        title = "内容",
        items = listOf(
            {
                SettingsItemCell(
                    icon = Icons.Default.Web,
                    title = "HTML设置",
                    subtitle = "网页内容显示设置",
                    onClick = {
                        HtmlSettingsActivity.start(context)
                    }
                )
            },
            {
                SettingsSwitchItem(
                    icon = Icons.Default.Image,
                    title = "上传原图",
                    subtitle = if (uploadOriginalImage) "上传原始图片，不进行WebP压缩" else "上传时自动压缩为WebP格式",
                    checked = uploadOriginalImage,
                    onCheckedChange = { checked ->
                        uploadOriginalImage = checked
                        imagePrefs.edit().putBoolean("upload_original_image", checked).apply()
                    }
                )
            },
            {
                SettingsItemCell(
                    icon = Icons.Default.GraphicEq,
                    title = "已保存的语音",
                    subtitle = "管理 Download/yhchat/audio/ 下的语音",
                    onClick = {
                        SavedAudiosActivity.start(context)
                    }
                )
            },
            {//不知道这个开关放这个位置合不合适
                SettingsSwitchItem(
                    icon = Icons.Default.Send,
                    title = "允许发送空文本消息",
                    subtitle = "允许发送空文本消息",
                    checked = is_send_text_allow_empty,
                    onCheckedChange = { checked ->
                        is_send_text_allow_empty = checked
                        sendTextPrefs.edit().putBoolean("send_text_allow_empty", checked).apply()
                    }
                )
            },
        )
    )
}

/**
 * 显示设置项
 */
@Composable
fun ThemeSettingItem(context: Context) {
    ThemeSettingsGroup(context = context)
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SettingsWsEntryPoint {
    fun webSocketService(): WebSocketService
}

@Composable
private fun YhToolsPanel(
    context: Context,
    currentUserId: String?,
    accountRepository: AccountRepository
) {
    var selectedTab by remember { mutableStateOf(0) }
    var postIdInput by remember { mutableStateOf("") }
    var boardIdInput by remember { mutableStateOf("") }
    var stickerPackIdInput by remember { mutableStateOf("") }
    var wsPlatformInput by remember { mutableStateOf("android") }
    val coroutineScope = rememberCoroutineScope()

    val appContext = context.applicationContext
    val wsEntryPoint = remember(appContext) {
        EntryPointAccessors.fromApplication(appContext, SettingsWsEntryPoint::class.java)
    }
    val webSocketService = remember(wsEntryPoint) { wsEntryPoint.webSocketService() }
    val connectionState by webSocketService.connectionState.collectAsStateWithLifecycle(initialValue = ConnectionState.Disconnected)

    val stateText = when (val state = connectionState) {
        ConnectionState.Connected -> "已连接"
        ConnectionState.Connecting -> "连接中"
        ConnectionState.Disconnected -> "未连接"
        is ConnectionState.Error -> "错误: ${state.message}"
    }

    SettingsGroup(
        items = listOf(
            {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    YhTabRow(
                        tabs = listOf("社区", "表情包", "WebSocket"),
                        selectedTabIndex = selectedTab,
                        onTabSelected = { selectedTab = it }
                    )

                    when (selectedTab) {
                        0 -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                YhOutlinedTextField(
                                    value = postIdInput,
                                    onValueChange = { postIdInput = it },
                                    modifier = Modifier.weight(1f),
                                    label = { Text("文章ID") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                YhButton(
                                    onClick = {
                                        val postId = postIdInput.toIntOrNull()
                                        if (postId == null) {
                                            android.widget.Toast.makeText(context, "文章ID必须是数字", android.widget.Toast.LENGTH_SHORT).show()
                                            return@YhButton
                                        }
                                        val intent = Intent(context, PostDetailActivity::class.java).apply {
                                            putExtra("post_id", postId)
                                        }
                                        context.startActivity(intent)
                                    }
                                ) {
                                    Text("进入")
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                YhOutlinedTextField(
                                    value = boardIdInput,
                                    onValueChange = { boardIdInput = it },
                                    modifier = Modifier.weight(1f),
                                    label = { Text("社区ID") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                YhButton(
                                    onClick = {
                                        val boardId = boardIdInput.toIntOrNull()
                                        if (boardId == null) {
                                            android.widget.Toast.makeText(context, "社区ID必须是数字", android.widget.Toast.LENGTH_SHORT).show()
                                            return@YhButton
                                        }
                                        val intent = Intent(context, BoardDetailActivity::class.java).apply {
                                            putExtra("board_id", boardId)
                                        }
                                        context.startActivity(intent)
                                    }
                                ) {
                                    Text("进入")
                                }
                            }
                        }
                        1 -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                YhOutlinedTextField(
                                    value = stickerPackIdInput,
                                    onValueChange = { stickerPackIdInput = it },
                                    modifier = Modifier.weight(1f),
                                    label = { Text("表情包ID") },
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                YhButton(
                                    onClick = {
                                        val stickerPackId = stickerPackIdInput.trim()
                                        if (stickerPackId.isBlank()) {
                                            android.widget.Toast.makeText(context, "表情包ID不能为空", android.widget.Toast.LENGTH_SHORT).show()
                                            return@YhButton
                                        }
                                        StickerPackDetailActivity.start(context, stickerPackId)
                                    }
                                ) {
                                    Text("进入")
                                }
                            }
                        }
                        2 -> {
                            Text(
                                text = "状态: $stateText",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                YhOutlinedButton(onClick = {
                                    webSocketService.disconnect()
                                }) {
                                    Text("停止")
                                }
                                YhOutlinedTextField(
                                    value = wsPlatformInput,
                                    onValueChange = { wsPlatformInput = it },
                                    modifier = Modifier.width(140.dp),
                                    label = { Text("在线版本") },
                                    singleLine = true
                                )
                                YhButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            val tokenRepo = RepositoryFactory.getTokenRepository(context)
                                            val userId = currentUserId
                                                ?: accountRepository.getCurrentUserId()
                                                ?: tokenRepo.getUserIdSync()
                                            if (userId.isNullOrBlank()) {
                                                android.widget.Toast.makeText(context, "未获取到用户ID", android.widget.Toast.LENGTH_SHORT).show()
                                                return@launch
                                            }
                                            val platform = wsPlatformInput.trim().ifBlank { "android" }
                                            webSocketService.connect(userId, platform)
                                        }
                                    }
                                ) {
                                    Text("重连")
                                }
                            }
                        }
                    }
                }
            }
        )
    )
}
