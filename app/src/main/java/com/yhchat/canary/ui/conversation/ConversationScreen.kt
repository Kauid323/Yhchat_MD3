package com.yhchat.canary.ui.conversation

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import com.yhchat.canary.R
import com.yhchat.canary.data.di.RepositoryFactory
import com.yhchat.canary.data.model.ChatType
import com.yhchat.canary.data.model.Conversation
import com.yhchat.canary.data.model.StickyItem
import com.yhchat.canary.data.repository.TokenRepository
import com.yhchat.canary.ui.adaptive.YhAlertDialog
import com.yhchat.canary.ui.adaptive.YhBasicComponent
import com.yhchat.canary.ui.adaptive.YhBottomSheet
import com.yhchat.canary.ui.adaptive.YhCard
import com.yhchat.canary.ui.adaptive.YhCircularProgressIndicator
import com.yhchat.canary.ui.adaptive.YhClickableSurface
import com.yhchat.canary.ui.adaptive.YhIcon as Icon
import com.yhchat.canary.ui.adaptive.YhIconButton
import com.yhchat.canary.ui.adaptive.YhPullToRefresh
import com.yhchat.canary.ui.adaptive.YhScaffold
import com.yhchat.canary.ui.adaptive.YhSurface
import com.yhchat.canary.ui.adaptive.YhText as Text
import com.yhchat.canary.ui.adaptive.YhTextButton
import com.yhchat.canary.ui.adaptive.YhTopBar
import com.yhchat.canary.ui.adaptive.yhTopBarNestedScroll
import com.yhchat.canary.ui.components.ConversationMenuDialog
import com.yhchat.canary.ui.components.observeScrollForNavigation
import com.yhchat.canary.ui.components.rememberBooleanPreference
import com.yhchat.canary.ui.components.rememberScrollAwareNavigationState
import com.yhchat.canary.ui.components.rememberSharedPreferences
import com.yhchat.canary.ui.components.rememberStringPreference
import com.yhchat.canary.ui.search.ComprehensiveSearchActivity
import com.yhchat.canary.ui.search.SearchViewModel
import com.yhchat.canary.utils.QRCodeUtil
import com.yhchat.canary.utils.UnifiedLinkHandler
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 会话列表界面
 */
@Composable
fun ConversationScreen(
    token: String,
    userId: String,
    onConversationClick: (String, Int, String, String?) -> Unit, // chatId, chatType, chatName, avatarUrl
    onSearchClick: () -> Unit,
    onMenuClick: () -> Unit,
    onProfileClick: () -> Unit = {},
    tokenRepository: TokenRepository? = null,
    viewModel: ConversationViewModel = viewModel(),
    modifier: Modifier = Modifier,
    navigationState: com.yhchat.canary.ui.components.ScrollAwareNavigationState? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val stickyData by viewModel.stickyData.collectAsState()
    val stickyLoading by viewModel.stickyLoading.collectAsState()
    
    // 读取显示置顶会话的设置
    val context = LocalContext.current
    val showStickyConversations by rememberBooleanPreference("display_settings", "show_sticky_conversations", true)
    val stickyLayoutMode by rememberStringPreference("display_settings", "sticky_layout_mode", "single_line")
    val stickyDisplayMode by rememberStringPreference("display_settings", "sticky_display_mode", "no_name")
    val stickyAlignment by rememberStringPreference("display_settings", "sticky_alignment", "center")
    val showStickyCollapseToggle by rememberBooleanPreference("display_settings", "show_sticky_collapse_toggle", true)
    var isStickyExpanded by rememberSaveable { mutableStateOf(true) }

    // 列表状态
    val listState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    
    // 监听滚动状态，自动隐藏/显示导航栏
    if (navigationState != null) {
        observeScrollForNavigation(listState, navigationState)
    }

    // 刷新状态 - 使用key保持状态
    var refreshing by rememberSaveable { mutableStateOf(false) }

    // 协程作用域
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    
    // 长按菜单状态
    var showConversationMenu by rememberSaveable { mutableStateOf(false) }
    var selectedConversation by remember { mutableStateOf<Conversation?>(null) }
    var isSelectedConversationSticky by remember { mutableStateOf(false) }
    
    // 添加菜单 BottomSheet 状态
    var showAddMenuBottomSheet by rememberSaveable { mutableStateOf(false) }
    
    // 布局设置
    val layoutShowAvatar by rememberBooleanPreference("layout_settings", "conversation_show_title", true)
    val layoutShowSearch by rememberBooleanPreference("layout_settings", "conversation_show_search", true)
    val layoutShowAddButton by rememberBooleanPreference("layout_settings", "conversation_show_add", true)
    val layoutShowUnreadBadge by rememberBooleanPreference("layout_settings", "conversation_show_unread_badge", true)
    val layoutShowConversationList by rememberBooleanPreference("layout_settings", "conversation_show_list", true)
    val layoutShowAddUser by rememberBooleanPreference("layout_settings", "add_menu_show_user", true)
    val layoutShowAddGroup by rememberBooleanPreference("layout_settings", "add_menu_show_group", true)
    val layoutShowScan by rememberBooleanPreference("layout_settings", "add_menu_show_scan", true)

    // 顶部搜索栏状态
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

    // 顶栏左侧自己的头像（来自 UserRepository.getUserProfile）
    var myAvatarUrl by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(token) {
        if (token.isBlank()) return@LaunchedEffect
        runCatching {
            val userRepo = RepositoryFactory.getUserRepository(context)
            userRepo.getUserProfile().onSuccess { profile ->
                myAvatarUrl = profile.avatarUrl
            }
        }
    }

    // 监听搜索状态变化：激活时自动请求焦点并弹出键盘，退出时清除焦点
    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            kotlinx.coroutines.delay(100)
            try { searchFocusRequester.requestFocus() } catch (_: Exception) {}
        } else {
            try {
                focusManager.clearFocus()
                searchFocusRequester.freeFocus()
            } catch (_: Exception) {}
        }
    }

    // 搜索ViewModel（用于API搜索）
    val searchViewModel: SearchViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val searchUiState by searchViewModel.uiState.collectAsState()
    val searchResult by searchViewModel.searchResult.collectAsState()

    // 本地会话过滤（按名称匹配）
    val filteredConversations = remember(conversations, searchQuery) {
        if (searchQuery.isBlank()) conversations
        else conversations.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    // 搜索激活时拦截返回键
    BackHandler(enabled = isSearchActive) {
        if (searchQuery.isNotEmpty()) {
            searchQuery = ""
            searchViewModel.clearSearch()
        } else {
            isSearchActive = false
            focusManager.clearFocus()
        }
    }

    // 扫一扫相关逻辑
    // 处理扫描结果
    val handleScanResult = remember(context) {
        { text: String ->
            if (text.isNotEmpty()) {
                if (UnifiedLinkHandler.isHandleableLink(text)) {
                    UnifiedLinkHandler.handleLink(context, text)
                } else {
                    // 尝试用浏览器打开
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(text))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "无法识别的内容: $text", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    // 扫码启动器
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result: ScanIntentResult ->
        if (result.contents != null) {
            handleScanResult(result.contents)
        }
    }
    // 相册启动器
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val text = QRCodeUtil.decodeFromUri(context, uri)
                if (text != null) {
                    handleScanResult(text)
                } else {
                    android.widget.Toast.makeText(context, "未识别到二维码", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 扫描方式选择弹窗状态
    var showScanMethodDialog by remember { mutableStateOf(false) }
    
    // 设置tokenRepository（只在第一次或tokenRepository变化时执行）
    LaunchedEffect(tokenRepository) {
        tokenRepository?.let { viewModel.setTokenRepository(it) }
    }
    
    // 启动WebSocket连接（只在第一次或token/userId变化时执行）
    // 检查是否禁用了 WebSocket
    val isWebSocketDisabledValue by rememberBooleanPreference("display_settings", "disable_websocket", false)
    LaunchedEffect(token, userId, isWebSocketDisabledValue) {
        if (token.isNotEmpty() && userId.isNotEmpty() && !isWebSocketDisabledValue) {
            viewModel.startWebSocket(userId)
        }
    }
    
    // 监听账号变化：当切换账号或初次进入时，初始化对应账号专属数据库与会话缓存
    LaunchedEffect(userId, token) {
        if (userId.isNotEmpty() && token.isNotEmpty()) {
            viewModel.initForUser(userId, token)
        }
    }
    
    YhScaffold(
        modifier = modifier,
        topBar = {
            YhTopBar(
                title = "消息",
                large = true,
                navigationIcon = {
                    if (layoutShowAvatar) {
                        val avatarOuterSize = 32.dp
                        val avatarRingWidth = 2.dp
                        Box(
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(avatarOuterSize)
                                .clip(CircleShape)
                                .border(avatarRingWidth, MaterialTheme.colorScheme.primary, CircleShape)
                                .clickable(onClick = onProfileClick),
                            contentAlignment = Alignment.Center
                        ) {
                            val avatarUrl72 = remember(myAvatarUrl) {
                                val url = myAvatarUrl
                                if (url.isNullOrBlank()) url
                                else if (url.contains("?")) url
                                else url + "?imageView2/2/w/150/h/150"
                            }
                            val avatarRequest = remember(avatarUrl72) {
                                avatarUrl72?.let { url ->
                                    ImageRequest.Builder(context)
                                        .data(url)
                                        .addHeader("Referer", "https://myapp.jwznb.com")
                                        .crossfade(true)
                                        .build()
                                }
                            }
                            AsyncImage(
                                model = avatarRequest,
                                contentDescription = "我的头像",
                                modifier = Modifier
                                    .size(avatarOuterSize - avatarRingWidth * 2)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop,
                                error = painterResource(id = com.yhchat.canary.R.drawable.ic_person)
                            )
                        }
                    }
                },
                actions = {
                    if (layoutShowSearch) {
                        YhIconButton(onClick = {
                            isSearchActive = true
                        }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "搜索",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    if (layoutShowAddButton) {
                        YhIconButton(onClick = { showAddMenuBottomSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "添加",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
        // 搜索栏：从顶栏位置滑入动画
        AnimatedVisibility(
            visible = isSearchActive,
            enter = slideInVertically(animationSpec = tween(250, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { -it } +
                    fadeIn(animationSpec = tween(200)),
            exit = slideOutVertically(animationSpec = tween(200, easing = androidx.compose.animation.core.FastOutLinearInEasing)) { -it } +
                    fadeOut(animationSpec = tween(160))
        ) {
            val searchBackgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
            val onSearchColor = MaterialTheme.colorScheme.onSurfaceVariant
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(searchBackgroundColor)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "退出搜索",
                    tint = onSearchColor,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable {
                            if (searchQuery.isNotEmpty()) {
                                searchQuery = ""
                                searchViewModel.clearSearch()
                            }
                            isSearchActive = false
                            focusManager.clearFocus()
                        }
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "搜索会话、联系人...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = onSearchColor.copy(alpha = 0.6f),
                            maxLines = 1
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            if (it.isNotEmpty()) {
                                searchViewModel.search(it)
                            } else {
                                searchViewModel.clearSearch()
                            }
                        },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = onSearchColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(searchFocusRequester),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            if (searchQuery.isNotEmpty()) searchViewModel.search(searchQuery)
                            focusManager.clearFocus()
                        }),
                        singleLine = true,
                        enabled = isSearchActive
                    )
                }
                if (searchQuery.isNotEmpty()) {
                    YhIconButton(
                        onClick = {
                            searchQuery = ""
                            searchViewModel.clearSearch()
                        },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "清除",
                            tint = onSearchColor
                        )
                    }
                }
            }
        }

        uiState.error?.let { error ->
            YhSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                shadowElevation = 0.dp,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        if (isSearchActive) {
            // ========== 搜索模式 ==========
            LazyColumn(
                state = searchListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                // 本地会话匹配结果
                if (filteredConversations.isNotEmpty()) {
                    item(key = "local_header") {
                        Text(
                            text = "本地筛选",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(
                        items = filteredConversations,
                        key = { "local_${it.chatId}" },
                        contentType = { "conversation" }
                    ) { conversation ->
                        ConversationItem(
                            conversation = conversation,
                            onClick = {
                                viewModel.markConversationAsRead(conversation.chatId)
                                onConversationClick(
                                    conversation.chatId,
                                    conversation.chatType,
                                    conversation.name,
                                    conversation.avatarUrl
                                )
                            },
                            onLongClick = {
                                selectedConversation = conversation
                                coroutineScope.launch {
                                    isSelectedConversationSticky = viewModel.isConversationSticky(conversation.chatId)
                                    showConversationMenu = true
                                }
                            }
                        )
                    }
                }

                // API搜索结果
                if (searchUiState.isLoading) {
                    item(key = "search_loading") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            YhCircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }

                searchResult?.let { result ->
                    result.list.forEachIndexed { catIndex, category ->
                        category.list?.let { items ->
                            if (items.isNotEmpty()) {
                                item(key = "api_header_${catIndex}_${category.title}") {
                                    Text(
                                        text = category.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                                itemsIndexed(
                                    items = category.list ?: emptyList(),
                                    key = { index, item -> "api_${catIndex}_${item.friendId}_${item.friendType}_$index" }
                                ) { index, searchItem ->
                                    YhSurface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onConversationClick(
                                                    searchItem.friendId,
                                                    searchItem.friendType,
                                                    searchItem.nickname,
                                                    searchItem.avatarUrl
                                                )
                                            },
                                        color = MaterialTheme.colorScheme.surface
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val avatarRequest = remember(searchItem.avatarUrl) {
                                                ImageRequest.Builder(context)
                                                    .data(searchItem.avatarUrl)
                                                    .addHeader("Referer", "https://myapp.jwznb.com")
                                                    .crossfade(true)
                                                    .build()
                                            }
                                            AsyncImage(
                                                model = avatarRequest,
                                                contentDescription = searchItem.nickname,
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(CircleShape),
                                                contentScale = ContentScale.Crop,
                                                error = painterResource(id = R.drawable.ic_person)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = searchItem.nickname,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                val idLabel = when (searchItem.friendType) {
                                                    1 -> "用户"
                                                    2 -> "群组"
                                                    3 -> "机器人"
                                                    else -> "ID"
                                                }
                                                Text(
                                                    text = "$idLabel: ${searchItem.friendId}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            YhSurface(
                                                color = when (searchItem.friendType) {
                                                    1 -> MaterialTheme.colorScheme.primaryContainer
                                                    2 -> MaterialTheme.colorScheme.secondaryContainer
                                                    3 -> MaterialTheme.colorScheme.tertiaryContainer
                                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                                },
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = when (searchItem.friendType) {
                                                        1 -> "用户"
                                                        2 -> "群组"
                                                        3 -> "机器人"
                                                        else -> "未知"
                                                    },
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 无结果提示
                if (searchQuery.isNotBlank() && filteredConversations.isEmpty() && searchResult == null && !searchUiState.isLoading) {
                    item(key = "no_results") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "未找到相关结果",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            // ========== 正常模式 ==========
            // 会话列表（支持下拉刷新）
            YhPullToRefresh(
                isRefreshing = refreshing,
                onRefresh = {
                    refreshing = true
                    viewModel.loadConversations(token, isForceOrPullRefresh = true)
                    viewModel.loadStickyConversations()
                    coroutineScope.launch {
                        kotlinx.coroutines.delay(600)
                        refreshing = false
                    }
                },
                modifier = Modifier.fillMaxSize().yhTopBarNestedScroll()
            ) {
                if (uiState.isLoading && conversations.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        YhCircularProgressIndicator()
                    }
                } else {
                    val stickyList = stickyData?.sticky ?: emptyList()
                    val stickyChatIds = remember(stickyList) { stickyList.map { it.chatId }.toSet() }
                    val isListLayout = stickyLayoutMode == "list"

                    // 优先去重：列表展示模式下去除普通会话列表中与置顶重复的会话
                    val displayConversations = remember(conversations, stickyChatIds, isListLayout) {
                        if (isListLayout) {
                            conversations.filter { it.chatId !in stickyChatIds }
                        } else {
                            conversations
                        }
                    }

                    // 模式3：列表展示（类似微信/QQ），置顶项以深色背景会话条目显示在最顶端
                    val stickyConversations = remember(stickyList, conversations) {
                        stickyList.map { stickyItem ->
                            conversations.find { it.chatId == stickyItem.chatId } ?: Conversation(
                                chatId = stickyItem.chatId,
                                chatType = stickyItem.chatType,
                                name = stickyItem.chatName,
                                chatContent = "",
                                timestampMs = 0L,
                                unreadMessage = 0,
                                at = 0,
                                avatarUrl = stickyItem.avatarUrl,
                                timestamp = 0L,
                                certificationLevel = stickyItem.certificationLevel
                            )
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        // 置顶会话列表
                        if (showStickyConversations && stickyList.isNotEmpty()) {
                            if (isListLayout) {
                                if (showStickyCollapseToggle) {
                                    item(key = "sticky_list_toggle_header") {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { isStickyExpanded = !isStickyExpanded }
                                                .padding(horizontal = 16.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.PushPin,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "置顶会话 (${stickyConversations.size})",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Icon(
                                                imageVector = if (isStickyExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = if (isStickyExpanded) "收起" else "展开",
                                                modifier = Modifier.size(20.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                if (!showStickyCollapseToggle || isStickyExpanded) {
                                    items(
                                        items = stickyConversations,
                                        key = { conv -> "sticky_list_${conv.chatId}" },
                                        contentType = { "sticky_conversation" }
                                    ) { conversation ->
                                        val chatId = conversation.chatId
                                        val chatType = conversation.chatType
                                        val chatName = conversation.name
                                        
                                        ConversationItem(
                                            conversation = conversation,
                                            isSticky = true,
                                            onClick = {
                                                viewModel.markConversationAsRead(chatId)
                                                onConversationClick(chatId, chatType, chatName, conversation.avatarUrl)
                                            },
                                            onLongClick = {
                                                selectedConversation = conversation
                                                coroutineScope.launch {
                                                    isSelectedConversationSticky = true
                                                    showConversationMenu = true
                                                }
                                            }
                                        )
                                    }

                                    if (displayConversations.isNotEmpty()) {
                                        item(key = "sticky_list_divider") {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                                    .height(1.dp)
                                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                            )
                                        }
                                    }
                                }
                            } else {
                                // 模式1与模式2：单行横滑或平铺展示
                                item(key = "sticky_conversations_section") {
                                    Column {
                                        if (stickyLayoutMode == "grid") {
                                            val flowArrangement = when (stickyAlignment) {
                                                "start" -> Alignment.Start
                                                "end" -> Alignment.End
                                                else -> Alignment.CenterHorizontally
                                            }
                                            FlowRow(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp, flowArrangement),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                stickyList.forEach { stickyItem ->
                                                    StickyConversationCard(
                                                        stickyItem = stickyItem,
                                                        displayMode = stickyDisplayMode,
                                                        onClick = {
                                                            viewModel.markConversationAsRead(stickyItem.chatId)
                                                            onConversationClick(
                                                                stickyItem.chatId,
                                                                stickyItem.chatType,
                                                                stickyItem.chatName,
                                                                stickyItem.avatarUrl
                                                            )
                                                        },
                                                        onLongClick = {
                                                            val stickyConversation = Conversation(
                                                                chatId = stickyItem.chatId,
                                                                chatType = stickyItem.chatType,
                                                                name = stickyItem.chatName,
                                                                chatContent = "",
                                                                timestampMs = 0L,
                                                                unreadMessage = 0,
                                                                at = 0,
                                                                avatarUrl = stickyItem.avatarUrl,
                                                                timestamp = 0L,
                                                                certificationLevel = stickyItem.certificationLevel
                                                            )
                                                            selectedConversation = stickyConversation
                                                            coroutineScope.launch {
                                                                isSelectedConversationSticky = true
                                                                showConversationMenu = true
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        } else {
                                            // 单行展示
                                            LazyRow(
                                                contentPadding = PaddingValues(horizontal = 8.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                items(
                                                    items = stickyList,
                                                    key = { stickyItem -> "sticky_${stickyItem.chatId}_${stickyItem.id}" },
                                                    contentType = { "sticky" }
                                                ) { stickyItem ->
                                                    StickyConversationCard(
                                                        stickyItem = stickyItem,
                                                        displayMode = stickyDisplayMode,
                                                        onClick = {
                                                            viewModel.markConversationAsRead(stickyItem.chatId)
                                                            onConversationClick(
                                                                stickyItem.chatId,
                                                                stickyItem.chatType,
                                                                stickyItem.chatName,
                                                                stickyItem.avatarUrl
                                                            )
                                                        },
                                                        onLongClick = {
                                                            val stickyConversation = Conversation(
                                                                chatId = stickyItem.chatId,
                                                                chatType = stickyItem.chatType,
                                                                name = stickyItem.chatName,
                                                                chatContent = "",
                                                                timestampMs = 0L,
                                                                unreadMessage = 0,
                                                                at = 0,
                                                                avatarUrl = stickyItem.avatarUrl,
                                                                timestamp = 0L,
                                                                certificationLevel = stickyItem.certificationLevel
                                                            )
                                                            selectedConversation = stickyConversation
                                                            coroutineScope.launch {
                                                                isSelectedConversationSticky = true
                                                                showConversationMenu = true
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                        
                                        // 分隔线
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp)
                                                .height(1.dp)
                                                .background(MaterialTheme.colorScheme.outlineVariant)
                                        )
                                    }
                                }
                            }
                        }
                        
                        // 普通会话列表 - 受布局设置控制
                        if (layoutShowConversationList) {
                            items(
                                items = displayConversations,
                                key = { conversation -> "conversation_${conversation.chatId}" },
                                contentType = { "conversation" }
                            ) { conversation ->
                                val chatId = conversation.chatId
                                val chatType = conversation.chatType
                                val chatName = conversation.name
                                
                                ConversationItem(
                                    conversation = conversation,
                                    isSticky = false,
                                    onClick = {
                                        viewModel.markConversationAsRead(chatId)
                                        onConversationClick(chatId, chatType, chatName, conversation.avatarUrl)
                                    },
                                    onLongClick = {
                                        selectedConversation = conversation
                                        coroutineScope.launch {
                                            isSelectedConversationSticky = viewModel.isConversationSticky(conversation.chatId)
                                            showConversationMenu = true
                                        }
                                    }
                                )
                            }
                            if (displayConversations.isEmpty() && (!isListLayout || stickyList.isEmpty())) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "暂无会话",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                    }
                                }
                            }
                        } else {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = "会话列表已隐藏",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        // 加载更多提示
                        if (uiState.isLoading && conversations.isNotEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    YhCircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            }
        } // end if isSearchActive / else
        } // end Column
    } // end YhScaffold
    
    // 长按菜单弹窗
    if (showConversationMenu && selectedConversation != null) {
        ConversationMenuDialog(
            conversation = selectedConversation!!,
            isSticky = isSelectedConversationSticky,
            onDismiss = { 
                showConversationMenu = false
                selectedConversation = null
            },
            onToggleSticky = {
                selectedConversation?.let { conversation ->
                    viewModel.toggleStickyConversation(conversation)
                }
            },
            onDelete = {
                selectedConversation?.let { conversation ->
                    viewModel.deleteConversation(conversation.chatId)
                }
            }
        )
    }
    
    // 扫描方式选择弹窗
    if (showScanMethodDialog) {
        YhAlertDialog(
            onDismissRequest = { showScanMethodDialog = false },
            title = { Text("扫一扫") },
            text = { Text("请选择扫描方式") },
            confirmButton = {
                YhTextButton(
                    onClick = {
                        showScanMethodDialog = false
                        val options = ScanOptions()
                        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        options.setPrompt("扫描二维码")
                        options.setBeepEnabled(false)
                        options.setBarcodeImageEnabled(true)
                        options.setOrientationLocked(false)
                        scanLauncher.launch(options)
                    }
                ) {
                    Text("相机扫码")
                }
            },
            dismissButton = {
                YhTextButton(
                    onClick = {
                        showScanMethodDialog = false
                        galleryLauncher.launch("image/*")
                    }
                ) {
                    Text("从相册选取")
                }
            }
        )
    }

    if (showAddMenuBottomSheet) {
        YhBottomSheet(
            show = showAddMenuBottomSheet,
            onDismissRequest = { showAddMenuBottomSheet = false }
        ) {
            val activity = context as? android.app.Activity
            val sheetColor = MaterialTheme.colorScheme.surface
            val darkIcons = sheetColor.luminance() > 0.5f
            if (activity != null) {
                com.yhchat.canary.ui.base.SystemBarUtils.ApplyNavigationBarColor(activity, sheetColor, darkIcons)
            }
            AddMenuBottomSheetContent(
                onAddUserGroupBot = {
                    showAddMenuBottomSheet = false
                    val intent = Intent(context, ComprehensiveSearchActivity::class.java)
                    context.startActivity(intent)
                },
                onCreateGroupBot = {
                    showAddMenuBottomSheet = false
                    val intent = Intent(context, com.yhchat.canary.ui.create.CreateActivity::class.java)
                    context.startActivity(intent)
                },
                onScan = {
                    showAddMenuBottomSheet = false
                    showScanMethodDialog = true
                },
                showAddUser = layoutShowAddUser,
                showAddGroup = layoutShowAddGroup,
                showScan = layoutShowScan
                )
            }
        }
}

/**
 * 添加菜单 BottomSheet 内容
 */
@Composable
private fun AddMenuBottomSheetContent(
    onAddUserGroupBot: () -> Unit,
    onCreateGroupBot: () -> Unit,
    onScan: () -> Unit,
    showAddUser: Boolean = true,
    showAddGroup: Boolean = true,
    showScan: Boolean = true
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
    ) {
        Text(
            text = "添加",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
        )
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        
        // 添加用户/群聊/机器人
        if (showAddUser) {
            YhBasicComponent(
                title = "添加用户/群聊/机器人",
                summary = "通过ID搜索并添加好友、群聊或机器人",
                onClick = onAddUserGroupBot,
                startAction = {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = "添加",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            )
        }
        
        // 创建群聊/机器人
        if (showAddGroup) {
            YhBasicComponent(
                title = "创建群聊/机器人",
                summary = "创建新的群聊或机器人",
                onClick = onCreateGroupBot,
                startAction = {
                    Icon(
                        imageVector = Icons.Default.GroupAdd,
                        contentDescription = "创建",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            )
        }
        
        // 扫一扫
        if (showScan) {
            YhBasicComponent(
                title = "扫一扫",
                summary = "扫描二维码添加好友或加入群聊",
                onClick = onScan,
                startAction = {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "扫一扫",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            )
        }
    }
}

/**
 * 会话项
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationItem(
    conversation: Conversation,
    isSticky: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val avatarUrl72 = remember(conversation.avatarUrl) {
        val url = conversation.avatarUrl
        if (url.isNullOrBlank()) {
            url
        } else if (url.contains("?")) {
            url
        } else {
            url + "?imageView2/2/w/72/h/72"
        }
    }

    // 性能优化：remember时间格式化对象和结果，避免每次重组都创建新对象
    val timeText = remember(conversation.timestampMs) {
        val nowCal = Calendar.getInstance()
        val msgCal = Calendar.getInstance().apply { timeInMillis = conversation.timestampMs }
        val isSameYear = nowCal.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR)

        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        when {
            conversation.timestampMs >= todayStart -> {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(conversation.timestampMs))
            }
            conversation.timestampMs >= todayStart - 86400000 -> {
                "昨天"
            }
            isSameYear -> {
                SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(conversation.timestampMs))
            }
            else -> {
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(conversation.timestampMs))
            }
        }
    }
    
    YhSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = if (isSticky) 2.dp else 3.dp),
        color = if (isSticky) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        } else {
            Color.Transparent
        },
        shadowElevation = 0.dp,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 头像 - 性能优化：remember ImageRequest避免重复构建
                Box {
                    val context = LocalContext.current
                    val avatarModel = remember(avatarUrl72) {
                        if (avatarUrl72 != null) {
                            ImageRequest.Builder(context)
                                .data(avatarUrl72)
                                .addHeader("Referer", "https://myapp.jwznb.com")
                                .crossfade(true)
                                .build()
                        } else {
                            null
                        }
                    }
                    AsyncImage(
                        model = avatarModel,
                        contentDescription = "头像",
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                        error = painterResource(id = com.yhchat.canary.R.drawable.ic_person)
                    )
                    
                    // 未读消息徽章
                    UnreadBadge(
                        count = conversation.unreadMessage,
                        isDoNotDisturb = conversation.doNotDisturb == 1,
                        modifier = Modifier.align(Alignment.TopEnd)
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // 会话信息
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = conversation.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            
                            // 认证标识
                            if (conversation.certificationLevel != null && conversation.certificationLevel > 0) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .background(
                                            when (conversation.certificationLevel) {
                                                1 -> Color(0xFF4CAF50) // 官方 - 绿色
                                                2 -> Color(0xFF2196F3) // 地区 - 蓝色
                                                else -> Color.Gray
                                            },
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = when (conversation.certificationLevel) {
                                            1 -> "官"
                                            2 -> "地"
                                            else -> "认"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            
                            // 免打扰图标
                            if (conversation.doNotDisturb == 1) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                                    contentDescription = "免打扰",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        Text(
                            text = timeText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(2.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 性能优化：remember displayContent避免重复计算
                        val displayContent = remember(conversation.chatContent, conversation.at) {
                            if (conversation.at > 0) {
                                "@${conversation.chatContent}"
                            } else {
                                conversation.chatContent
                            }
                        }
                        
                        Text(
                            text = displayContent,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (conversation.at > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        
                        // @标识
                        if (conversation.at > 0) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary,
                                        CircleShape
                                    )
                            )
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 70.dp)
                    .height(0.5.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            )
        }
    }
}

/**
 * 未读消息徽章组件 - 圆形设计
 *
 * @param count 未读消息数量
 * @param isDoNotDisturb 是否开启免打扰
 * @param modifier 修饰符
 */
@Composable
fun UnreadBadge(
    count: Int,
    isDoNotDisturb: Boolean = false,
    modifier: Modifier = Modifier
) {
    // 免打扰模式下不显示徽章
    if (isDoNotDisturb || count <= 0) return
    
    val displayText = remember(count) { count.toString() }
    val digitCount = remember(displayText) { displayText.length.coerceAtLeast(1) }
    
    val badgeSize by animateDpAsState(
        targetValue = (18 + (digitCount - 1) * 6).dp,
        animationSpec = tween(durationMillis = 200),
        label = "BadgeSize"
    )
    
    val fontSize = remember(digitCount) {
        when {
            digitCount >= 5 -> 7.sp
            digitCount >= 4 -> 8.sp
            digitCount >= 3 -> 8.5.sp
            digitCount >= 2 -> 9.sp
            else -> 10.sp
        }
    }
    
    Box(
        modifier = modifier
            .height(18.dp)
            .widthIn(min = 18.dp, max = badgeSize)
            .background(
                MaterialTheme.colorScheme.error,
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayText,
            color = MaterialTheme.colorScheme.onError,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            style = MaterialTheme.typography.labelSmall.copy(
                lineHeight = fontSize
            )
        )
    }
}

/**
 * 会话类型图标
 */
@Composable
fun ChatTypeIcon(chatType: Int) {
    val icon = when (chatType) {
        ChatType.USER.value -> "👤"
        ChatType.GROUP.value -> "👥"
        ChatType.BOT.value -> "🤖"
        else -> "💬"
    }
    
    Text(
        text = icon,
        fontSize = 20.sp
    )
}

/**
 * 置顶会话卡片 - 支持带名称、纯头像、纯名称标签三种外观
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StickyConversationCard(
    stickyItem: StickyItem,
    displayMode: String = "no_name",
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    when (displayMode) {
        "no_avatar" -> {
            // 不显示头像：以胶囊/标签样式展示会话名称
            YhSurface(
                modifier = modifier
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick
                    ),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (stickyItem.certificationLevel != null && stickyItem.certificationLevel > 0) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(
                                    when (stickyItem.certificationLevel) {
                                        1 -> Color(0xFF4CAF50) // 官方 - 绿色
                                        2 -> Color(0xFF2196F3) // 地区 - 蓝色
                                        else -> Color.Gray
                                    },
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when (stickyItem.certificationLevel) {
                                    1 -> "官"
                                    2 -> "地"
                                    else -> "认"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontSize = 8.sp
                            )
                        }
                    }
                    Text(
                        text = stickyItem.chatName,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        "with_name" -> {
            // 显示会话名称：头像 + 底部文字
            Column(
                modifier = modifier
                    .width(56.dp)
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(48.dp)
                ) {
                    val avatarRequest = remember(stickyItem.avatarUrl) {
                        ImageRequest.Builder(context)
                            .data(stickyItem.avatarUrl)
                            .addHeader("Referer", "https://myapp.jwznb.com")
                            .crossfade(true)
                            .build()
                    }
                    AsyncImage(
                        model = avatarRequest,
                        contentDescription = stickyItem.chatName,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                        error = painterResource(id = com.yhchat.canary.R.drawable.ic_person)
                    )

                    // 认证标识
                    if (stickyItem.certificationLevel != null && stickyItem.certificationLevel > 0) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .align(Alignment.BottomEnd)
                                .background(
                                    when (stickyItem.certificationLevel) {
                                        1 -> Color(0xFF4CAF50)
                                        2 -> Color(0xFF2196F3)
                                        else -> Color.Gray
                                    },
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when (stickyItem.certificationLevel) {
                                    1 -> "官"
                                    2 -> "地"
                                    else -> "认"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontSize = 8.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = stickyItem.chatName,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.width(58.dp),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        else -> {
            // 默认不显示会话名称：纯头像（带认证角标）
            Box(
                modifier = modifier
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.size(48.dp)
                ) {
                    val avatarRequest = remember(stickyItem.avatarUrl) {
                        ImageRequest.Builder(context)
                            .data(stickyItem.avatarUrl)
                            .addHeader("Referer", "https://myapp.jwznb.com")
                            .crossfade(true)
                            .build()
                    }
                    AsyncImage(
                        model = avatarRequest,
                        contentDescription = stickyItem.chatName,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                        error = painterResource(id = com.yhchat.canary.R.drawable.ic_person)
                    )

                    // 认证标识
                    if (stickyItem.certificationLevel != null && stickyItem.certificationLevel > 0) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .align(Alignment.BottomEnd)
                                .background(
                                    when (stickyItem.certificationLevel) {
                                        1 -> Color(0xFF4CAF50)
                                        2 -> Color(0xFF2196F3)
                                        else -> Color.Gray
                                    },
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when (stickyItem.certificationLevel) {
                                    1 -> "官"
                                    2 -> "地"
                                    else -> "认"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontSize = 8.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
