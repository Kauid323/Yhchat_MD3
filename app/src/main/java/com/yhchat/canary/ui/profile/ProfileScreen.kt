package com.yhchat.canary.ui.profile

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.yhchat.canary.data.api.ApiClient
import com.yhchat.canary.data.di.RepositoryFactory
import com.yhchat.canary.data.model.BetaInfo
import com.yhchat.canary.data.model.SaveUserDataRequest
import com.yhchat.canary.data.model.UserProfile
import com.yhchat.canary.data.repository.NavigationRepository
import com.yhchat.canary.data.repository.TokenRepository
import com.yhchat.canary.data.repository.UserRepository
import com.yhchat.canary.ui.adaptive.YhAlertDialog
import com.yhchat.canary.ui.adaptive.YhBottomSheet
import com.yhchat.canary.ui.adaptive.YhButton
import com.yhchat.canary.ui.adaptive.YhCircularProgressIndicator
import com.yhchat.canary.ui.adaptive.YhDropdownSelector
import com.yhchat.canary.ui.adaptive.YhDropdownMenu
import com.yhchat.canary.ui.adaptive.YhDropdownMenuItem
import com.yhchat.canary.ui.adaptive.YhIcon as Icon
import com.yhchat.canary.ui.adaptive.YhIconButton
import com.yhchat.canary.ui.adaptive.YhLinearProgressIndicator
import com.yhchat.canary.ui.adaptive.YhOutlinedTextField
import com.yhchat.canary.ui.adaptive.YhPullToRefresh
import com.yhchat.canary.ui.adaptive.YhRadioButton
import com.yhchat.canary.ui.adaptive.YhSurface
import com.yhchat.canary.ui.adaptive.YhText as Text
import com.yhchat.canary.ui.adaptive.YhTextButton
import com.yhchat.canary.ui.adaptive.YhTopBar
import com.yhchat.canary.ui.adaptive.isMiuixUi
import com.yhchat.canary.ui.base.SystemBarUtils
import com.yhchat.canary.ui.coin.CoinDetailActivity
import com.yhchat.canary.ui.coin.CoinRecordActivity
import com.yhchat.canary.ui.coin.CoinShopActivity
import com.yhchat.canary.ui.components.ScrollAwareNavigationState
import com.yhchat.canary.ui.components.observeScrollForNavigation
import com.yhchat.canary.ui.components.rememberBooleanPreference
import com.yhchat.canary.ui.settings.SettingsActivity
import com.yhchat.canary.ui.settings.SettingsCustomItem
import com.yhchat.canary.ui.settings.SettingsGroup
import com.yhchat.canary.ui.stats.UserStatsActivity
import com.yhchat.canary.ui.webview.WebViewActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 我的界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    userRepository: UserRepository? = null,
    tokenRepository: TokenRepository? = null,
    navigationRepository: NavigationRepository? = null,
    navigationState: ScrollAwareNavigationState? = null
) {
    val context = LocalContext.current
    val viewModel = remember {
        val repo = userRepository ?: UserRepository(ApiClient.apiService, null, context)
        if (tokenRepository != null) {
            repo.setTokenRepository(tokenRepository)
        }
        ProfileViewModel(repo)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val changeInviteCodeState by viewModel.changeInviteCodeState.collectAsStateWithLifecycle()
    val changeNicknameState by viewModel.changeNicknameState.collectAsStateWithLifecycle()
    val changeAvatarState by viewModel.changeAvatarState.collectAsStateWithLifecycle()
    val betaState by viewModel.betaState.collectAsStateWithLifecycle()
    val userDataState by viewModel.userDataState.collectAsStateWithLifecycle()
    val saveUserDataState by viewModel.saveUserDataState.collectAsStateWithLifecycle()
    
    // 修改邀请码弹窗状态
    var showChangeInviteCodeDialog by remember { mutableStateOf(false) }
    // 修改用户名称弹窗状态
    var showChangeNicknameDialog by remember { mutableStateOf(false) }
    // 图片选择器状态
    var showImagePicker by remember { mutableStateOf(false) }

    var showUserDataDialog by remember { mutableStateOf(false) }
    
    // 图片选择器
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { 
            viewModel.changeAvatar(context, it)
        }
    }
    
    // 仅在首次加载时拉取用户资料（切换Tab不重复加载）
    LaunchedEffect(Unit) {
        if (uiState.userProfile == null && !uiState.isLoading) {
            viewModel.loadUserProfile()
        }
    }

    // 下拉刷新状态
    var refreshing by remember { mutableStateOf(false) }

    // 刷新完成后关闭指示器
    LaunchedEffect(uiState.isLoading, uiState.isRefreshing) {
        if (!uiState.isLoading && !uiState.isRefreshing && refreshing) {
            refreshing = false
        }
    }
    
    // 监听头像修改状态
    LaunchedEffect(changeAvatarState.isSuccess) {
        if (changeAvatarState.isSuccess) {
            Toast.makeText(context, "头像修改成功", Toast.LENGTH_SHORT).show()
            viewModel.resetChangeAvatarState()
        }
    }
    
    LaunchedEffect(changeAvatarState.error) {
        changeAvatarState.error?.let { error ->
            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
            viewModel.resetChangeAvatarState()
        }
    }

    LaunchedEffect(saveUserDataState.isSuccess) {
        if (saveUserDataState.isSuccess) {
            Toast.makeText(context, "保存成功", Toast.LENGTH_SHORT).show()
            showUserDataDialog = false
            viewModel.resetSaveUserDataState()
        }
    }

    LaunchedEffect(saveUserDataState.error) {
        saveUserDataState.error?.let { error ->
            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
            viewModel.resetSaveUserDataState()
        }
    }

    val listState = rememberLazyListState()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        navigationState?.show()
    }

    DisposableEffect(lifecycleOwner, navigationState) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                navigationState?.show()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // 顶部应用栏
        YhTopBar(
            title = "我的",
            large = false,
            actions = {
                YhIconButton(
                    onClick = {
                        SettingsActivity.start(context, navigationRepository, tokenRepository)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "设置"
                    )
                }
            }
        )

        // 监听滚动状态，自动隐藏/显示导航栏
        if (navigationState != null) {
            observeScrollForNavigation(listState, navigationState)
        }

        YhPullToRefresh(
            isRefreshing = uiState.isRefreshing || refreshing,
            onRefresh = {
                refreshing = true
                viewModel.refreshProfile()
            },
            modifier = Modifier.weight(1f)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            when {
                uiState.isLoading && uiState.userProfile == null -> {
                    item("profile_loading") {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            YhCircularProgressIndicator()
                        }
                    }
                }
                uiState.error != null && uiState.userProfile == null -> {
                    item("profile_error") {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp)
                        ) {
                            Text(
                                text = "加载失败",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = uiState.error ?: "未知错误",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            YhButton(
                                onClick = { viewModel.loadUserProfile() }
                            ) {
                                Text("重试")
                            }
                        }
                    }
                }
                uiState.userProfile != null -> {
                    item("profile_content") {
                        uiState.userProfile?.let { userProfile ->
                            UserProfileContent(
                                userProfile = userProfile,
                                navigationRepository = navigationRepository,
                                tokenRepository = tokenRepository,
                                viewModel = viewModel,
                                userDataState = userDataState,
                                saveUserDataState = saveUserDataState,
                                showUserDataDialog = showUserDataDialog,
                                onShowUserDataDialog = {
                                    showUserDataDialog = true
                                    viewModel.loadUserData()
                                },
                                onDismissUserDataDialog = {
                                    if (!saveUserDataState.isLoading) {
                                        showUserDataDialog = false
                                        viewModel.resetSaveUserDataState()
                                    }
                                },
                                onSaveUserData = { req ->
                                    viewModel.saveUserData(req)
                                },
                                onShowChangeInviteCodeDialog = { showChangeInviteCodeDialog = true },
                                onShowChangeNicknameDialog = { showChangeNicknameDialog = true },
                                imagePickerLauncher = imagePickerLauncher,
                                changeAvatarState = changeAvatarState,
                                betaState = betaState,
                                changeInviteCodeState = changeInviteCodeState,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                else -> {
                    item("profile_empty") {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "暂无用户信息",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
    }
    
    // 修改邀请码弹窗
    if (showChangeInviteCodeDialog) {
        ChangeInviteCodeDialog(
            currentInviteCode = uiState.userProfile?.invitationCode ?: "",
            changeInviteCodeState = changeInviteCodeState,
            onConfirm = { newCode ->
                viewModel.changeInviteCode(newCode)
            },
            onDismiss = {
                showChangeInviteCodeDialog = false
                viewModel.resetChangeInviteCodeState()
            }
        )
    }
    
    // 修改用户名称弹窗
    if (showChangeNicknameDialog) {
        ChangeNicknameDialog(
            currentNickname = uiState.userProfile?.nickname ?: "",
            changeNicknameState = changeNicknameState,
            onConfirm = { newNickname ->
                viewModel.changeNickname(newNickname)
            },
            onDismiss = {
                showChangeNicknameDialog = false
                viewModel.resetChangeNicknameState()
            }
        )
    }
}
@Composable
private fun UserProfileContent(
    userProfile: UserProfile,
    navigationRepository: NavigationRepository? = null,
    tokenRepository: TokenRepository? = null,
    viewModel: ProfileViewModel,
    userDataState: UserDataState,
    saveUserDataState: SaveUserDataState,
    showUserDataDialog: Boolean,
    onShowUserDataDialog: () -> Unit,
    onDismissUserDataDialog: () -> Unit,
    onSaveUserData: (SaveUserDataRequest) -> Unit,
    onShowChangeInviteCodeDialog: () -> Unit = {},
    onShowChangeNicknameDialog: () -> Unit = {},
    imagePickerLauncher: ActivityResultLauncher<String>,
    changeAvatarState: ChangeAvatarState,
    betaState: BetaState,
    changeInviteCodeState: ChangeInviteCodeState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // 读取布局设置
    val showUserId by rememberBooleanPreference("layout_settings", "profile_show_user_id", true)
    val showVipBadge by rememberBooleanPreference("layout_settings", "profile_show_vip_badge", true)
    val showBetaBadge by rememberBooleanPreference("layout_settings", "profile_show_beta_badge", true)
    val showPersonalInfo by rememberBooleanPreference("layout_settings", "profile_show_personal_info", true)
    val showPhone by rememberBooleanPreference("layout_settings", "profile_show_phone", true)
    val showEmail by rememberBooleanPreference("layout_settings", "profile_show_email", true)
    val showCoin by rememberBooleanPreference("layout_settings", "profile_show_coin", true)
    val showVipExpire by rememberBooleanPreference("layout_settings", "profile_show_vip_expire", true)
    val showInviteCode by rememberBooleanPreference("layout_settings", "profile_show_invite_code", true)
    
    Column(
        modifier = modifier
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val profileHeaderCardColor = rememberProfileHeaderCardColor()

        // 头像和姓名部分
        YhSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 4.dp,
            color = profileHeaderCardColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 头像（可点击修改）
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clickable { 
                            imagePickerLauncher.launch("image/*")
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (!userProfile.avatarUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = userProfile.avatarUrl,
                            contentDescription = "用户头像",
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "默认头像",
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    // 修改头像指示器
                    YhSurface(
                        modifier = Modifier
                            .size(24.dp)
                            .align(Alignment.BottomEnd),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        shadowElevation = 2.dp
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "修改头像",
                            modifier = Modifier
                                .size(16.dp)
                                .padding(4.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    
                    // 上传中指示器
                    if (changeAvatarState.isLoading) {
                        YhSurface(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center
                            ) {
                                YhCircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 用户名（带编辑按钮）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = userProfile.nickname,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    YhIconButton(
                        onClick = onShowChangeNicknameDialog,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "修改用户名",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // 用户ID
                if (showUserId) {
                    Text(
                        text = "ID: ${userProfile.userId}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable {
                            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("userId", userProfile.userId)
                            clipboardManager.setPrimaryClip(clip)
                            Toast.makeText(context, "已复制用户ID", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                
                // VIP 标识和内测标识
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    // VIP 标识
                    if (showVipBadge && userProfile.isVip == 1) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(16.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "VIP",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "VIP",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    // 内测标识
                    if (showBetaBadge && betaState.betaInfo?.isBetaUser == true) {
                        var showBetaInfo by remember { mutableStateOf(false) }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable { showBetaInfo = true }
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "内测用户",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "内测",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        // 内测信息弹窗
                        if (showBetaInfo) {
                            BetaInfoDialog(
                                betaInfo = betaState.betaInfo!!,
                                onDismiss = { showBetaInfo = false }
                            )
                        }
                    }
                }
            }
        }
        
        val detailItems = mutableListOf<@Composable () -> Unit>()

        if (showPersonalInfo) {
            detailItems += {
                ProfileSettingsInfoRow(
                    icon = Icons.Default.Person,
                    title = "个人信息",
                    subtitle = "完善资料，体现自己个性",
                    trailingContent = {
                        YhTextButton(onClick = onShowUserDataDialog) {
                            Text("编辑")
                        }
                    }
                )
            }
        }

        // 手机号（带显示/隐藏切换）
        if (showPhone && !TextUtils.isEmpty(userProfile.phone)) {
            detailItems += {
                var showFullPhone by remember { mutableStateOf(false) }
                ProfileSettingsInfoRow(
                    icon = Icons.Default.Phone,
                    title = "手机号",
                    subtitle = if (showFullPhone) userProfile.phone!! else formatPhoneNumber(userProfile.phone!!),
                    trailingContent = {
                        YhIconButton(onClick = { showFullPhone = !showFullPhone }) {
                            Icon(
                                imageVector = if (showFullPhone) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showFullPhone) "隐藏手机号" else "显示手机号",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )
            }
        }

        // 邮箱
        if (showEmail) {
            detailItems += {
                val hasEmail = !userProfile.email.isNullOrEmpty()
                ProfileSettingsInfoRow(
                    icon = Icons.Default.Email,
                    title = "邮箱",
                    subtitle = userProfile.email ?: "未绑定",
                    trailingContent = {
                        YhTextButton(
                            onClick = {
                                val clazz = if (hasEmail) {
                                    EmailModificationActivity::class.java
                                } else {
                                    EmailBindingActivity::class.java
                                }
                                context.startActivity(Intent(context, clazz))
                            }
                        ) {
                            Text(if (hasEmail) "修改" else "去绑定")
                        }
                    }
                )
            }
        }

        // 云湖币（可点击）
        if (showCoin && userProfile.coin != null) {
            var showCoinMenu by remember { mutableStateOf(false) }
            detailItems += {
                ProfileSettingsInfoRow(
                    icon = Icons.Default.Star,
                    title = "金币",
                    subtitle = "%.2f".format(userProfile.coin),
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = { showCoinMenu = true }
                )
            }

            if (showCoinMenu) {
                CoinMenuBottomSheet(
                    tokenRepository = tokenRepository,
                    onDismiss = { showCoinMenu = false }
                )
            }
        }

        // VIP到期时间
        if (showVipExpire && userProfile.isVip == 1 && userProfile.vipExpiredTime != null && userProfile.vipExpiredTime > 0) {
            detailItems += {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val expiredDate = Date(userProfile.vipExpiredTime * 1000)
                ProfileSettingsInfoRow(
                    icon = Icons.Default.Star,
                    title = "VIP到期时间",
                    subtitle = dateFormat.format(expiredDate)
                )
            }
        }

        // 邀请码
        if (showInviteCode && !TextUtils.isEmpty(userProfile.invitationCode)) {
            var showInviteCodeMenu by remember { mutableStateOf(false) }
            detailItems += {
                ProfileSettingsInfoRow(
                    icon = Icons.Default.Person,
                    title = "邀请码",
                    subtitle = userProfile.invitationCode!!,
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = { showInviteCodeMenu = true }
                )
            }

            if (showInviteCodeMenu) {
                InviteCodeMenuBottomSheet(
                    currentInviteCode = userProfile.invitationCode!!,
                    changeInviteCodeState = changeInviteCodeState,
                    viewModel = viewModel,
                    onDismiss = { showInviteCodeMenu = false },
                    onShowChangeInviteCodeDialog = onShowChangeInviteCodeDialog
                )
            }
        }

        if (detailItems.isNotEmpty()) {
            SettingsGroup(
                title = "详细信息",
                items = detailItems,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (showUserDataDialog) {
            UserDataEditBottomSheet(
                userDataState = userDataState,
                saveUserDataState = saveUserDataState,
                onDismiss = { onDismissUserDataDialog() },
                onSave = onSaveUserData
            )
        }
        
        // 添加底部间距，确保内容不会贴底
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun rememberProfileHeaderCardColor(): Color {
    if (!isMiuixUi) {
        return MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) {
        Color(0xFF1E1E20)
    } else {
        Color(0xFFF7F7F8)
    }
}

@Composable
private fun ProfileSettingsInfoRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    SettingsCustomItem(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!subtitle.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (trailingContent != null) {
                Row(verticalAlignment = Alignment.CenterVertically, content = trailingContent)
            }
        }
    }
}

/**
 * 金币菜单底部弹窗（MD3风格）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoinMenuBottomSheet(
    tokenRepository: TokenRepository? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    YhBottomSheet(
        show = true,
        title = "金币功能",
        onDismissRequest = onDismiss,
    ) {
        val activity = context as? Activity
        val sheetColor = MaterialTheme.colorScheme.surface
        val darkIcons = sheetColor.luminance() > 0.5f
        if (activity != null) {
            SystemBarUtils.ApplyNavigationBarColor(activity, sheetColor, darkIcons)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Text(
                text = "金币功能",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            
            // 金币商城
            CoinMenuItem(
                icon = Icons.Default.Star,
                label = "金币商城",
                onClick = {
                    val intent = Intent(context, CoinShopActivity::class.java)
                    context.startActivity(intent)
                    onDismiss()
                }
            )
            
            // 任务中心
            CoinMenuItem(
                icon = Icons.Default.AccountCircle,
                label = "任务中心",
                onClick = {
                    val intent = Intent(context, CoinDetailActivity::class.java)
                    context.startActivity(intent)
                    onDismiss()
                }
            )
            
            // 金币明细
            CoinMenuItem(
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                label = "金币明细",
                onClick = {
                    val intent = Intent(context, CoinRecordActivity::class.java)
                    context.startActivity(intent)
                    onDismiss()
                }
            )

            // 云湖周周抽
            CoinMenuItem(
                icon = Icons.Default.Star,
                label = "云湖周周抽",
                onClick = {
                    coroutineScope.launch {
                        val repo = tokenRepository ?: RepositoryFactory.getTokenRepository(context)
                        val token = repo.getTokenSync()
                        WebViewActivity.start(
                            context = context,
                            url = "https://huodong.yhchat.com/zhouzhouchou/index",
                            title = "云湖周周抽",
                            token = token
                        )
                        onDismiss()
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CoinMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserDataEditBottomSheet(
    userDataState: UserDataState,
    saveUserDataState: SaveUserDataState,
    onDismiss: () -> Unit,
    onSave: (SaveUserDataRequest) -> Unit
) {
    val configuration = LocalConfiguration.current

    var introduction by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("3") }
    var selectedYear by remember { mutableStateOf<Int?>(null) }
    var selectedMonth by remember { mutableStateOf<Int?>(null) }
    var selectedDay by remember { mutableStateOf<Int?>(null) }
    var province by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var district by remember { mutableStateOf("") }
    var locationCode by remember { mutableStateOf("") }

    LaunchedEffect(userDataState.userData) {
        val data = userDataState.userData
        if (data != null) {
            introduction = data.introduction ?: ""
            gender = (data.gender ?: 3).toString()
            val birthdaySec = data.birthday ?: 0L
            if (birthdaySec > 0) {
                val cal = Calendar.getInstance().apply {
                    timeInMillis = birthdaySec * 1000L
                }
                selectedYear = cal.get(Calendar.YEAR)
                selectedMonth = cal.get(Calendar.MONTH) + 1
                selectedDay = cal.get(Calendar.DAY_OF_MONTH)
            } else {
                selectedYear = null
                selectedMonth = null
                selectedDay = null
            }
            province = data.province ?: ""
            city = data.city ?: ""
            district = data.district ?: ""
            locationCode = data.locationCode ?: ""
        }
    }

    YhBottomSheet(
        show = true,
        title = "个人信息",
        fullScreen = true,
        onDismissRequest = {
            if (!saveUserDataState.isLoading) {
                onDismiss()
            }
        }
    ) {
        val activity = LocalContext.current as? Activity
        val sheetColor = MaterialTheme.colorScheme.surface
        val darkIcons = sheetColor.luminance() > 0.5f
        if (activity != null) {
            SystemBarUtils.ApplyNavigationBarColor(activity, sheetColor, darkIcons)
        }
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "个人信息",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                YhTextButton(
                    onClick = {
                        if (!saveUserDataState.isLoading) {
                            onDismiss()
                        }
                    },
                    enabled = !saveUserDataState.isLoading
                ) {
                    Text("关闭")
                }
            }

            if (userDataState.isLoading) {
                YhLinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            userDataState.error?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            YhOutlinedTextField(
                value = introduction,
                onValueChange = { introduction = it },
                label = { Text("个人简介") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !saveUserDataState.isLoading
            )

            Text(
                text = "性别",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    YhRadioButton(
                        selected = gender == "1",
                        onClick = { if (!saveUserDataState.isLoading) gender = "1" },
                        enabled = !saveUserDataState.isLoading
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("男")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    YhRadioButton(
                        selected = gender == "2",
                        onClick = { if (!saveUserDataState.isLoading) gender = "2" },
                        enabled = !saveUserDataState.isLoading
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("女")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    YhRadioButton(
                        selected = gender == "3",
                        onClick = { if (!saveUserDataState.isLoading) gender = "3" },
                        enabled = !saveUserDataState.isLoading
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("其他")
                }
            }

            Text(
                text = "生日（年 / 月 / 日）",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            val nowCal = remember { Calendar.getInstance() }
            val currentYear = nowCal.get(Calendar.YEAR)
            val yearOptions = remember(currentYear) { (1900..currentYear).toList().reversed() }
            val monthOptions = remember { (1..12).toList() }

            val maxDayInMonth = remember(selectedYear, selectedMonth) {
                val year = selectedYear ?: 2000
                val month = selectedMonth ?: 1
                Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month - 1)
                }.getActualMaximum(Calendar.DAY_OF_MONTH)
            }
            val dayOptions = remember(maxDayInMonth) { (1..maxDayInMonth).toList() }

            LaunchedEffect(maxDayInMonth) {
                val cur = selectedDay
                if (cur != null && cur > maxDayInMonth) {
                    selectedDay = maxDayInMonth
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                YhDropdownSelector(
                    items = yearOptions.map { it.toString() },
                    selectedIndex = yearOptions.indexOf(selectedYear),
                    onSelectedIndexChange = { index ->
                        selectedYear = yearOptions.getOrNull(index)
                    },
                    modifier = Modifier.weight(1f),
                    label = "年",
                    enabled = !saveUserDataState.isLoading
                )

                YhDropdownSelector(
                    items = monthOptions.map { it.toString() },
                    selectedIndex = monthOptions.indexOf(selectedMonth),
                    onSelectedIndexChange = { index ->
                        selectedMonth = monthOptions.getOrNull(index)
                    },
                    modifier = Modifier.weight(1f),
                    label = "月",
                    enabled = !saveUserDataState.isLoading
                )

                YhDropdownSelector(
                    items = dayOptions.map { it.toString() },
                    selectedIndex = dayOptions.indexOf(selectedDay),
                    onSelectedIndexChange = { index ->
                        selectedDay = dayOptions.getOrNull(index)
                    },
                    modifier = Modifier.weight(1f),
                    label = "日",
                    enabled = !saveUserDataState.isLoading
                )
            }

            YhOutlinedTextField(
                value = province,
                onValueChange = { province = it },
                label = { Text("省") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !saveUserDataState.isLoading
            )

            YhOutlinedTextField(
                value = city,
                onValueChange = { city = it },
                label = { Text("市") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !saveUserDataState.isLoading
            )

            YhOutlinedTextField(
                value = district,
                onValueChange = { district = it },
                label = { Text("区") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !saveUserDataState.isLoading
            )

            YhOutlinedTextField(
                value = locationCode,
                onValueChange = { locationCode = it },
                label = { Text("地区编码") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !saveUserDataState.isLoading
            )

            Spacer(modifier = Modifier.height(4.dp))

            YhButton(
                onClick = {
                    if (saveUserDataState.isLoading) return@YhButton
                    val birthday = if (selectedYear != null && selectedMonth != null && selectedDay != null) {
                        Calendar.getInstance().apply {
                            set(Calendar.YEAR, selectedYear!!)
                            set(Calendar.MONTH, selectedMonth!! - 1)
                            set(Calendar.DAY_OF_MONTH, selectedDay!!)
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                        }.timeInMillis / 1000L
                    } else {
                        0L
                    }
                    onSave(
                        SaveUserDataRequest(
                            introduction = introduction,
                            gender = gender.toIntOrNull() ?: 0,
                            birthday = birthday,
                            province = province,
                            city = city,
                            district = district,
                            locationCode = locationCode
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !saveUserDataState.isLoading
            ) {
                if (saveUserDataState.isLoading) {
                    YhCircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("保存")
                }
            }
        }
    }
}

/**
 * 修改邀请码弹窗
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChangeInviteCodeDialog(
    currentInviteCode: String,
    changeInviteCodeState: ChangeInviteCodeState,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newInviteCode by remember { mutableStateOf(currentInviteCode) }
    
    // 成功后自动关闭弹窗
    LaunchedEffect(changeInviteCodeState.isSuccess) {
        if (changeInviteCodeState.isSuccess) {
            delay(1500) // 显示成功提示1.5秒
            onDismiss()
        }
    }
    
    YhAlertDialog(
        onDismissRequest = { if (!changeInviteCodeState.isLoading) onDismiss() },
        title = {
            Text(
                text = "修改邀请码",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "请输入新的邀请码：",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                YhOutlinedTextField(
                    value = newInviteCode,
                    onValueChange = { newInviteCode = it },
                    label = { Text("邀请码") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !changeInviteCodeState.isLoading,
                    singleLine = true,
                    isError = changeInviteCodeState.error != null
                )
                
                // 错误提示
                changeInviteCodeState.error?.let { error ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                
                // 成功提示
                if (changeInviteCodeState.isSuccess) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "修改成功！",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            YhButton(
                onClick = { onConfirm(newInviteCode) },
                enabled = !changeInviteCodeState.isLoading && newInviteCode.isNotBlank() && !changeInviteCodeState.isSuccess
            ) {
                if (changeInviteCodeState.isLoading) {
                    YhCircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = when {
                        changeInviteCodeState.isLoading -> "修改中..."
                        changeInviteCodeState.isSuccess -> "已修改"
                        else -> "确定"
                    }
                )
            }
        },
        dismissButton = {
            YhTextButton(
                onClick = onDismiss,
                enabled = !changeInviteCodeState.isLoading
            ) {
                Text("取消")
            }
        }
    )
}

/**
 * 修改用户名称弹窗
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChangeNicknameDialog(
    currentNickname: String,
    changeNicknameState: ChangeNicknameState,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newNickname by remember { mutableStateOf(currentNickname) }
    
    // 成功后自动关闭弹窗
    LaunchedEffect(changeNicknameState.isSuccess) {
        if (changeNicknameState.isSuccess) {
            delay(1500) // 显示成功提示1.5秒
            onDismiss()
        }
    }
    
    YhAlertDialog(
        onDismissRequest = { if (!changeNicknameState.isLoading) onDismiss() },
        title = {
            Text(
                text = "修改用户名",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "请输入新的用户名：",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                YhOutlinedTextField(
                    value = newNickname,
                    onValueChange = { newNickname = it },
                    label = { Text("用户名") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !changeNicknameState.isLoading,
                    singleLine = true,
                    isError = changeNicknameState.error != null
                )
                
                // 错误提示
                changeNicknameState.error?.let { error ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                
                // 成功提示
                if (changeNicknameState.isSuccess) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "修改成功！",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            YhButton(
                onClick = { onConfirm(newNickname) },
                enabled = !changeNicknameState.isLoading && newNickname.isNotBlank() && !changeNicknameState.isSuccess
            ) {
                if (changeNicknameState.isLoading) {
                    YhCircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = when {
                        changeNicknameState.isLoading -> "修改中..."
                        changeNicknameState.isSuccess -> "已修改"
                        else -> "确定"
                    }
                )
            }
        },
        dismissButton = {
            YhTextButton(
                onClick = onDismiss,
                enabled = !changeNicknameState.isLoading
            ) {
                Text("取消")
            }
        }
    )
}

/**
 * 内测信息弹窗
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BetaInfoDialog(
    betaInfo: BetaInfo,
    onDismiss: () -> Unit
) {
    YhAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "内测",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "内测功能",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "恭喜您成为云湖内测用户！",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                Text(
                    text = betaInfo.info,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2
                )
            }
        },
        confirmButton = {
            YhButton(
                onClick = onDismiss
            ) {
                Text("我知道了")
            }
        }
    )
}

/**
 * 格式化手机号，隐藏中间4位
 */
private fun formatPhoneNumber(phone: String): String {
    return if (phone.length == 11) {
        // 中国手机号格式：138****5678
        "${phone.substring(0, 3)}****${phone.substring(7)}"
    } else if (phone.length > 7) {
        // 其他长度：保留前3位和后4位
        val start = phone.substring(0, 3)
        val end = phone.substring(phone.length - 4)
        "$start****$end"
    } else {
        // 长度不足，直接返回
        phone
    }
}

/**
 * 邀请码菜单底部弹窗
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InviteCodeMenuBottomSheet(
    currentInviteCode: String,
    changeInviteCodeState: ChangeInviteCodeState,
    viewModel: ProfileViewModel?,
    onDismiss: () -> Unit,
    onShowChangeInviteCodeDialog: () -> Unit
) {
    val context = LocalContext.current

    YhBottomSheet(
        show = true,
        title = "邀请码功能",
        onDismissRequest = onDismiss,
    ) {
        val activity = context as? Activity
        val sheetColor = MaterialTheme.colorScheme.surface
        val darkIcons = sheetColor.luminance() > 0.5f
        if (activity != null) {
            SystemBarUtils.ApplyNavigationBarColor(activity, sheetColor, darkIcons)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Text(
                text = "邀请码功能",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            
            // 修改邀请码
            InviteCodeMenuItem(
                icon = Icons.Default.Edit,
                label = "修改邀请码",
                onClick = {
                    onShowChangeInviteCodeDialog()
                    onDismiss()
                }
            )
            
            // 查看云湖总用户
            InviteCodeMenuItem(
                icon = Icons.Default.Person,
                label = "查看云湖总用户",
                onClick = {
                    val intent = Intent(context, UserStatsActivity::class.java)
                    context.startActivity(intent)
                    onDismiss()
                }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun InviteCodeMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}



