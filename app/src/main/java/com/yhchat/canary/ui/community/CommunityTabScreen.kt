@file:OptIn(ExperimentalMaterial3Api::class)
package com.yhchat.canary.ui.community

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.yhchat.canary.data.model.CommunityBoard
import com.yhchat.canary.data.model.CommunityPost
import com.yhchat.canary.ui.community.CommunityPostCard
import com.yhchat.canary.ui.components.ScrollAwareNavigationState
import com.yhchat.canary.ui.components.TopBarActionButton
import com.yhchat.canary.ui.components.observeScrollForNavigation
import com.yhchat.canary.ui.components.rememberBooleanPreference
import com.yhchat.canary.ui.settings.SettingsCustomItem
import com.yhchat.canary.ui.settings.SettingsGroup
import com.yhchat.canary.ui.settings.SettingsItemCell
import com.yhchat.canary.utils.ImageUploadUtil
import kotlinx.coroutines.launch

/**
 * 社区标签页界面
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CommunityTabScreen(
    token: String,
    modifier: Modifier = Modifier,
    viewModel: CommunityViewModel = viewModel(),
    navigationState: ScrollAwareNavigationState? = null,
    // 大屏分屏导航回调（null=使用默认Activity导航）
    onBoardNavigate: ((boardId: Int, boardName: String) -> Unit)? = null,
    onMyPostsNavigate: (() -> Unit)? = null,
    onMyCollectPostsNavigate: (() -> Unit)? = null,
    onBlockedUsersNavigate: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    // 获取布局设置
    val showHotTab by rememberBooleanPreference("layout_settings", "community_show_hot", true)
    val showAllTab by rememberBooleanPreference("layout_settings", "community_show_all", true)
    val showFollowingTab by rememberBooleanPreference("layout_settings", "community_show_following", true)
    val showMoreTab by rememberBooleanPreference("layout_settings", "community_show_more", true)
    
    // 获取状态
    val boardListState by viewModel.boardListState.collectAsState()
    val followingBoardListState by viewModel.followingBoardListState.collectAsState()
    val allBoardListState by viewModel.allBoardListState.collectAsState()
    val hotPostListState by viewModel.recommendPostListState.collectAsState()
    val latestPostListState by viewModel.latestPostListState.collectAsState()

    var selectedFeed by rememberSaveable { mutableStateOf("热门") }
    
    // 根据布局设置过滤可见的Tab
    val allTabs = listOf(
        "热门" to showHotTab,
        "全部" to showAllTab,
        "关注" to showFollowingTab,
        "更多" to showMoreTab
    )
    val visibleTabs = allTabs.filter { it.second }.map { it.first }
    val tabTitles = visibleTabs.ifEmpty { listOf("热门") } // 确保至少有一个Tab
    
    // 页面状态
    val pagerState = rememberPagerState { tabTitles.size }
    var selectedTab by remember { mutableStateOf(0) }
    
    // 监听页面变化，使用snapshotFlow来获得更好的响应性
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            selectedTab = page
        }
    }
    
    // 监听标签选择变化
    LaunchedEffect(selectedTab) {
        if (selectedTab != pagerState.currentPage) {
            pagerState.animateScrollToPage(selectedTab)
        }
    }

    fun openDrawer() {
        coroutineScope.launch { drawerState.open() }
    }

    fun closeDrawer() {
        coroutineScope.launch { drawerState.close() }
    }

    @Composable
    fun DrawerBody() {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            SecondaryTabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                // 用透明背景，跟侧边栏（ModalDrawerSheet）的背景保持一致，避免出现一条颜色不同的横条
                containerColor = Color.Transparent
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = index == selectedTab,
                        onClick = {
                            selectedTab = index
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        text = { Text(title) }
                    )
                }
            }

            LaunchedEffect(token) {
                if (token.isNotEmpty()) {
                    viewModel.loadBoardList(token)
                    viewModel.loadFollowingBoardList(token)
                    viewModel.loadAllBoardList(token)
                    viewModel.loadCreatedBoardList(token)
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val currentTabTitle = tabTitles.getOrNull(page) ?: ""
                when (currentTabTitle) {
                    "热门" -> {
                        PullToRefreshBox(
                            isRefreshing = boardListState.isRefreshing,
                            onRefresh = { viewModel.refreshBoardList(token) },
                            state = rememberPullToRefreshState()
                        ) {
                            BoardListContent(
                                boards = boardListState.boards,
                                isLoading = boardListState.isLoading,
                                error = boardListState.error,
                                onBoardClick = { board ->
                                    if (onBoardNavigate != null) {
                                        onBoardNavigate(board.id, board.name)
                                    } else {
                                        val intent = Intent(context, BoardDetailActivity::class.java).apply {
                                            putExtra("board_id", board.id)
                                            putExtra("board_name", board.name)
                                            putExtra("token", token)
                                        }
                                        context.startActivity(intent)
                                    }
                                }
                            )
                        }
                    }
                    "全部" -> {
                        PullToRefreshBox(
                            isRefreshing = allBoardListState.isRefreshing,
                            onRefresh = { viewModel.refreshAllBoardList(token) },
                            state = rememberPullToRefreshState()
                        ) {
                            BoardListContent(
                                boards = allBoardListState.boards,
                                isLoading = allBoardListState.isLoading,
                                error = allBoardListState.error,
                                onBoardClick = { board ->
                                    if (onBoardNavigate != null) {
                                        onBoardNavigate(board.id, board.name)
                                    } else {
                                        val intent = Intent(context, BoardDetailActivity::class.java).apply {
                                            putExtra("board_id", board.id)
                                            putExtra("board_name", board.name)
                                            putExtra("token", token)
                                        }
                                        context.startActivity(intent)
                                    }
                                }
                            )
                        }
                    }
                    "关注" -> {
                        PullToRefreshBox(
                            isRefreshing = followingBoardListState.isRefreshing,
                            onRefresh = { viewModel.refreshFollowingBoardList(token) },
                            state = rememberPullToRefreshState()
                        ) {
                            BoardListContent(
                                boards = followingBoardListState.boards,
                                isLoading = followingBoardListState.isLoading,
                                error = followingBoardListState.error,
                                onBoardClick = { board ->
                                    if (onBoardNavigate != null) {
                                        onBoardNavigate(board.id, board.name)
                                    } else {
                                        val intent = Intent(context, BoardDetailActivity::class.java).apply {
                                            putExtra("board_id", board.id)
                                            putExtra("board_name", board.name)
                                            putExtra("token", token)
                                        }
                                        context.startActivity(intent)
                                    }
                                }
                            )
                        }
                    }
                    "更多" -> {
                        MoreTabContent(
                            token = token,
                            viewModel = viewModel,
                            context = context,
                            onBoardNavigate = onBoardNavigate,
                            onMyPostsNavigate = onMyPostsNavigate,
                            onMyCollectPostsNavigate = onMyCollectPostsNavigate,
                            onBlockedUsersNavigate = onBlockedUsersNavigate,
                        )
                    }
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            // 必须包一层 ModalDrawerSheet：它负责侧边栏的宽度（240~360dp）、圆角、背景色和状态栏内边距。
            // 直接放 Column 会被按整屏宽度测量，导致侧边栏全屏并且没有背景。
            ModalDrawerSheet {
                DrawerBody()
            }
        },
        gesturesEnabled = true
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "社区",
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { openDrawer() }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "打开侧边栏"
                            )
                        }
                    },
                    actions = {
                        FeedToggleRow(
                            selected = selectedFeed,
                            onSelectedChange = { selectedFeed = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // 顶栏主操作（搜索）：MD3 Expressive 的填充按钮
                        TopBarActionButton(
                            icon = Icons.Default.Search,
                            contentDescription = "全局搜索",
                            onClick = {
                                val intent = Intent(context, SearchActivity::class.java).apply {
                                    putExtra("token", token)
                                }
                                context.startActivity(intent)
                            }
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        ) { paddingValues ->
            CommunityFeedScreen(
                token = token,
                feedType = selectedFeed,
                hotState = hotPostListState,
                latestState = latestPostListState,
                navigationState = navigationState,
                onRefreshHot = { viewModel.loadRecommendPostList(token, page = 1, isRefresh = true) },
                onRefreshLatest = { viewModel.refreshLatestPostList(token) },
                onLoadMoreHot = { viewModel.loadMoreRecommendPosts(token) },
                onLoadMoreLatest = { viewModel.loadMoreLatestPosts(token) },
                onPostClick = { post ->
                    val intent = Intent(context, PostDetailActivity::class.java).apply {
                        putExtra("post_id", post.id)
                        putExtra("post_title", post.title)
                        putExtra("token", token)
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        }
    }
}

@Composable
private fun FeedToggleRow(
    selected: String,
    onSelectedChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FeedToggleChip(
            text = "热门",
            selected = selected == "热门",
            onClick = { onSelectedChange("热门") }
        )
        Spacer(modifier = Modifier.width(6.dp))
        FeedToggleChip(
            text = "最新",
            selected = selected == "最新",
            onClick = { onSelectedChange("最新") }
        )
    }
}

@Composable
private fun FeedToggleChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 0.dp
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CommunityFeedScreen(
    token: String,
    feedType: String,
    hotState: RecommendPostListState,
    latestState: CommunityPostListState,
    navigationState: ScrollAwareNavigationState? = null,
    onRefreshHot: () -> Unit,
    onRefreshLatest: () -> Unit,
    onLoadMoreHot: () -> Unit,
    onLoadMoreLatest: () -> Unit,
    onPostClick: (CommunityPost) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    if (navigationState != null) {
        observeScrollForNavigation(listState, navigationState)
    }

    LaunchedEffect(token, feedType) {
        if (token.isNotEmpty()) {
            if (feedType == "最新") {
                if (latestState.posts.isEmpty() && !latestState.isLoading) {
                    onRefreshLatest()
                }
            } else {
                if (hotState.posts.isEmpty() && !hotState.isLoading) {
                    onRefreshHot()
                }
            }
        }
    }

    if (feedType == "最新") {
        LaunchedEffect(latestState.posts.size, latestState.hasMore, latestState.isLoading) {
            snapshotFlow {
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                val total = listState.layoutInfo.totalItemsCount
                Triple(lastVisible, total, latestState.hasMore)
            }.collect { (lastVisible, total, hasMore) ->
                if (hasMore && !latestState.isLoading && total > 0 && lastVisible >= total - 3) {
                    onLoadMoreLatest()
                }
            }
        }
        PullToRefreshBox(
            isRefreshing = latestState.isRefreshing,
            onRefresh = { onRefreshLatest() },
            state = rememberPullToRefreshState(),
            modifier = modifier
        ) {
            when {
                latestState.error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = latestState.error ?: "加载失败",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                latestState.posts.isEmpty() && latestState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(latestState.posts, key = { it.id }) { post ->
                            CommunityPostCard(
                                post = post,
                                onClick = { onPostClick(post) }
                            )
                        }

                        if (latestState.posts.isNotEmpty() && latestState.hasMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                        }

                        if (latestState.posts.isEmpty() && !latestState.isLoading) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "暂无最新文章",
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
    } else {
        LaunchedEffect(hotState.posts.size, hotState.hasMore, hotState.isLoading) {
            snapshotFlow {
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                val total = listState.layoutInfo.totalItemsCount
                Triple(lastVisible, total, hotState.hasMore)
            }.collect { (lastVisible, total, hasMore) ->
                if (hasMore && !hotState.isLoading && total > 0 && lastVisible >= total - 3) {
                    onLoadMoreHot()
                }
            }
        }
        PullToRefreshBox(
            isRefreshing = hotState.isRefreshing,
            onRefresh = { onRefreshHot() },
            state = rememberPullToRefreshState(),
            modifier = modifier
        ) {
            when {
                hotState.error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = hotState.error ?: "加载失败",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                hotState.posts.isEmpty() && hotState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(hotState.posts, key = { it.id }) { post ->
                            CommunityPostCard(
                                post = post,
                                onClick = { onPostClick(post) }
                            )
                        }

                        if (hotState.posts.isNotEmpty() && hotState.hasMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                        }

                        if (hotState.posts.isEmpty() && !hotState.isLoading) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "暂无热门文章",
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
    }
}

/**
 * 更多页面内容
 */
@Composable
fun MoreTabContent(
    token: String,
    viewModel: CommunityViewModel,
    context: android.content.Context,
    onBoardNavigate: ((boardId: Int, boardName: String) -> Unit)? = null,
    onMyPostsNavigate: (() -> Unit)? = null,
    onMyCollectPostsNavigate: (() -> Unit)? = null,
    onBlockedUsersNavigate: (() -> Unit)? = null,
) {
    // 读取布局设置
    val layoutPrefs = remember { context.getSharedPreferences("layout_settings", android.content.Context.MODE_PRIVATE) }
    val showMyPosts = remember { layoutPrefs.getBoolean("community_more_my_posts", true) }
    val showCollect = remember { layoutPrefs.getBoolean("community_more_collect", true) }
    val showBlocked = remember { layoutPrefs.getBoolean("community_more_blocked", true) }
    val showCreateBoard = remember { layoutPrefs.getBoolean("community_more_create_board", true) }
    val showManagedBoards = remember { layoutPrefs.getBoolean("community_more_managed_boards", true) }
    
    var showCreateBoardDialog by remember { mutableStateOf(false) }

    val createdBoardListState by viewModel.createdBoardListState.collectAsState()
    var showManageBoardSheet by remember { mutableStateOf(false) }
    var boardToManage by remember { mutableStateOf<CommunityBoard?>(null) }
    var manageVisibleRange by remember { mutableStateOf(0) }
    var managePublishAuthority by remember { mutableStateOf(0) }
    var isManaging by remember { mutableStateOf(false) }
    var manageError by remember { mutableStateOf<String?>(null) }
    var followersTotal by remember { mutableStateOf(0) }

    val coroutineScope = rememberCoroutineScope()
    var isEditingBoard by remember { mutableStateOf(false) }
    var editedBoardName by remember { mutableStateOf("") }
    var editedAvatarUrl by remember { mutableStateOf("") }
    var isUploadingAvatar by remember { mutableStateOf(false) }
    var editError by remember { mutableStateOf<String?>(null) }
    var showDeleteBoardDialog by remember { mutableStateOf(false) }
    var isDeletingBoard by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val selected = uri ?: return@rememberLauncherForActivityResult
        coroutineScope.launch {
            isUploadingAvatar = true
            editError = null
            try {
                val uploadToken = ImageUploadUtil.getQiniuUploadToken(context, token)
                if (uploadToken.isNotEmpty()) {
                    ImageUploadUtil.uploadImage(context, selected, uploadToken).fold(
                        onSuccess = { response ->
                            editedAvatarUrl = "https://chat-img.jwznb.com/${response.key}"
                        },
                        onFailure = { e ->
                            editError = e.message ?: "图片上传失败"
                        }
                    )
                } else {
                    editError = "获取上传token失败"
                }
            } catch (e: Exception) {
                editError = e.message ?: "图片上传失败"
            } finally {
                isUploadingAvatar = false
            }
        }
    }
    
    // 构建可见的菜单项列表
    val moreMenuItems = buildList<@Composable () -> Unit> {
        if (showMyPosts) {
            add {
                SettingsItemCell(
                    icon = Icons.Default.Person,
                    title = "我的文章",
                    subtitle = "查看和管理我发布的文章",
                    onClick = {
                        if (onMyPostsNavigate != null) {
                            onMyPostsNavigate()
                        } else {
                            val intent = Intent(context, MyPostsActivity::class.java).apply {
                                putExtra("token", token)
                            }
                            context.startActivity(intent)
                        }
                    }
                )
            }
        }
        if (showCollect) {
            add {
                SettingsItemCell(
                    icon = Icons.Default.Star,
                    title = "我的收藏",
                    subtitle = "查看我收藏的文章",
                    onClick = {
                        if (onMyCollectPostsNavigate != null) {
                            onMyCollectPostsNavigate()
                        } else {
                            val intent = Intent(context, MyCollectPostsActivity::class.java).apply {
                                putExtra("token", token)
                            }
                            context.startActivity(intent)
                        }
                    }
                )
            }
        }
        if (showBlocked) {
            add {
                SettingsItemCell(
                    icon = Icons.Default.Block,
                    title = "被屏蔽的用户",
                    subtitle = "管理已屏蔽的用户列表",
                    onClick = {
                        if (onBlockedUsersNavigate != null) {
                            onBlockedUsersNavigate()
                        } else {
                            val intent = Intent(context, BlockedUsersActivity::class.java).apply {
                                putExtra("token", token)
                            }
                            context.startActivity(intent)
                        }
                    }
                )
            }
        }
        if (showCreateBoard) {
            add {
                SettingsItemCell(
                    icon = Icons.Default.Add,
                    title = "新建分区",
                    subtitle = "创建一个新的文章分区",
                    onClick = {
                        showCreateBoardDialog = true
                    }
                )
            }
        }
    }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        if (moreMenuItems.isNotEmpty()) {
            item {
                SettingsGroup(
                    title = "更多",
                    items = moreMenuItems
                )
            }
        }

        if (showManagedBoards) {
            item {
                val boards = createdBoardListState.boards
                SettingsGroup(
                    title = "我管理的分区",
                    items = boards.map { board ->
                    {
                        SettingsCustomItem(
                            onClick = {
                                if (onBoardNavigate != null) {
                                    onBoardNavigate(board.id, board.name)
                                } else {
                                    val intent = Intent(context, BoardDetailActivity::class.java).apply {
                                        putExtra("board_id", board.id)
                                        putExtra("board_name", board.name)
                                        putExtra("token", token)
                                    }
                                    context.startActivity(intent)
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (board.avatar.isNotBlank()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(board.avatar)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                    } else {
                                        Surface(
                                            modifier = Modifier.fillMaxSize(),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = CircleShape
                                        ) {}
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = board.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "ID: ${board.id}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                TextButton(
                                    onClick = {
                                        boardToManage = board
                                        manageVisibleRange = 0
                                        managePublishAuthority = 0
                                        manageError = null
                                        isEditingBoard = false
                                        editedBoardName = board.name
                                        editedAvatarUrl = board.avatar
                                        editError = null
                                        showManageBoardSheet = true
                                    }
                                ) {
                                    Text(
                                        text = "管理",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }
                }
            )
            }
        }
    }
    
    // 创建分区弹窗
    if (showCreateBoardDialog) {
        CreateBoardDialog(
            token = token,
            viewModel = viewModel,
            context = context,
            onDismiss = { showCreateBoardDialog = false }
        )
    }

    if (showManageBoardSheet && boardToManage != null) {
        ModalBottomSheet(
            onDismissRequest = {
                showManageBoardSheet = false
            },
            sheetState = rememberModalBottomSheetState()
        ) {
            val board = boardToManage

            LaunchedEffect(showManageBoardSheet, board?.id) {
                val id = board?.id ?: return@LaunchedEffect
                if (!showManageBoardSheet) return@LaunchedEffect
                viewModel.loadBaFollowerTotal(
                    token = token,
                    baId = id,
                    onSuccess = { total ->
                        followersTotal = total
                    },
                    onError = {
                        followersTotal = 0
                    }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "管理分区",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .clickable(enabled = isEditingBoard && !isUploadingAvatar) {
                                imagePickerLauncher.launch("image/*")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val avatarToShow = if (isEditingBoard) editedAvatarUrl else (board?.avatar ?: "")
                        if (avatarToShow.isNotBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(avatarToShow)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = CircleShape
                            ) {}
                        }

                        if (isUploadingAvatar) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isEditingBoard) editedBoardName else board?.name.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "ID: ${board?.id ?: 0}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = "关注列表 ${followersTotal} >",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clickable {
                                    val current = board ?: return@clickable
                                    FollowersListActivity.start(
                                        context = context,
                                        token = token,
                                        baId = current.id,
                                        baName = current.name
                                    )
                                }
                        )
                    }

                    TextButton(
                        onClick = {
                            val current = board ?: return@TextButton
                            isEditingBoard = !isEditingBoard
                            editedBoardName = current.name
                            editedAvatarUrl = current.avatar
                            editError = null
                        }
                    ) {
                        Text(if (isEditingBoard) "取消修改" else "修改")
                    }
                }

                if (isEditingBoard) {
                    OutlinedTextField(
                        value = editedBoardName,
                        onValueChange = { input ->
                            if (input.length <= 10) {
                                editedBoardName = input
                            }
                        },
                        label = { Text("分区名称") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isUploadingAvatar
                    )

                    editError?.let { err ->
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Button(
                        onClick = {
                            val current = board ?: return@Button
                            val name = editedBoardName.trim()
                            val avatar = editedAvatarUrl.trim()
                            if (name.isBlank()) {
                                editError = "分区名称不能为空"
                                return@Button
                            }
                            if (avatar.isBlank()) {
                                editError = "分区头像不能为空"
                                return@Button
                            }
                            isManaging = true
                            viewModel.editBoard(
                                token = token,
                                baId = current.id,
                                name = name,
                                avatar = avatar,
                                onSuccess = {
                                    isManaging = false
                                    isEditingBoard = false
                                    boardToManage = current.copy(name = name, avatar = avatar)
                                },
                                onError = { msg ->
                                    isManaging = false
                                    editError = msg
                                }
                            )
                        },
                        enabled = !isManaging && !isUploadingAvatar,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isManaging) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("保存中")
                        } else {
                            Text("保存修改")
                        }
                    }
                }

                HorizontalDivider()

                Text(
                    text = "可见范围",
                    style = MaterialTheme.typography.titleMedium
                )
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = manageVisibleRange == 0,
                            onClick = { manageVisibleRange = 0 }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("所有人可见")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = manageVisibleRange == 1,
                            onClick = { manageVisibleRange = 1 }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("仅分区所有者可见")
                    }
                }

                Text(
                    text = "发布权限",
                    style = MaterialTheme.typography.titleMedium
                )
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = managePublishAuthority == 0,
                            onClick = { managePublishAuthority = 0 }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("所有人都可以发文章和评论")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = managePublishAuthority == 1,
                            onClick = { managePublishAuthority = 1 }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("仅所有者发文章，任何人可评论")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = managePublishAuthority == 2,
                            onClick = { managePublishAuthority = 2 }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("仅所有者发文章和评论")
                    }
                }

                manageError?.let { err ->
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Button(
                    onClick = {
                        val board = boardToManage ?: return@Button
                        isManaging = true
                        viewModel.manageBoard(
                            token = token,
                            baId = board.id,
                            visibleRange = manageVisibleRange,
                            publishAuthority = managePublishAuthority,
                            onSuccess = {
                                isManaging = false
                                showManageBoardSheet = false
                            },
                            onError = { msg ->
                                isManaging = false
                                manageError = msg
                            }
                        )
                    },
                    enabled = !isManaging && !isUploadingAvatar,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isManaging) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("提交中")
                    } else {
                        Text("保存")
                    }
                }

                HorizontalDivider()

                deleteError?.let { err ->
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Button(
                    onClick = {
                        deleteError = null
                        showDeleteBoardDialog = true
                    },
                    enabled = !isManaging && !isUploadingAvatar && !isDeletingBoard,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isDeletingBoard) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("删除中", color = MaterialTheme.colorScheme.onError)
                    } else {
                        Text("删除分区", color = MaterialTheme.colorScheme.onError)
                    }
                }
            }
        }

        if (showDeleteBoardDialog) {
            val current = boardToManage
            AlertDialog(
                onDismissRequest = {
                    if (!isDeletingBoard) {
                        showDeleteBoardDialog = false
                    }
                },
                title = { Text("删除分区") },
                text = {
                    Text("确定要删除分区「${current?.name.orEmpty()}」吗？删除后不可恢复。")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val b = current ?: return@Button
                            isDeletingBoard = true
                            deleteError = null
                            viewModel.deleteBoard(
                                token = token,
                                baId = b.id,
                                onSuccess = {
                                    isDeletingBoard = false
                                    showDeleteBoardDialog = false
                                    showManageBoardSheet = false
                                    boardToManage = null
                                },
                                onError = { msg ->
                                    isDeletingBoard = false
                                    deleteError = msg
                                }
                            )
                        },
                        enabled = !isDeletingBoard
                    ) {
                        Text("删除", color = MaterialTheme.colorScheme.onError)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showDeleteBoardDialog = false },
                        enabled = !isDeletingBoard
                    ) {
                        Text("取消")
                    }
                }
            )
        }
    }
}

/**
 * 更多选项卡片
 */
@Composable
fun MoreOptionCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 分区列表内容
 */
@Composable
fun BoardListContent(
    boards: List<CommunityBoard>,
    isLoading: Boolean,
    error: String?,
    onBoardClick: (CommunityBoard) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // 错误提示
        error?.let { errorMessage ->
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
                    text = errorMessage,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        
        // 分区列表
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(boards, key = { it.id }) { board ->
                BoardItem(
                    board = board,
                    onClick = { onBoardClick(board) }
                )
            }
            
            // 空状态
            if (boards.isEmpty() && !isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无分区",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // 加载状态
            if (isLoading && boards.isEmpty()) {
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

/**
 * 我的文章列表内容
 */
@Composable
fun MyPostListContent(
    posts: List<CommunityPost>,
    isLoading: Boolean,
    error: String?,
    onPostClick: (CommunityPost) -> Unit,
    onDeletePost: (Int) -> Unit,
    context: android.content.Context,
    token: String,
    searchQuery: String = "",
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // 错误提示
        error?.let { errorMessage ->
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
                    text = errorMessage,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        
        // 文章列表
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(posts, key = { it.id }) { post ->
                MyPostItem(
                    post = post,
                    onClick = { onPostClick(post) },
                    onEdit = { 
                        // 跳转到编辑文章Activity
                        val intent = Intent(
                            context, EditPostActivity::class.java).apply {
                            putExtra("post_id", post.id)
                            putExtra("token", token)
                            putExtra("original_title", post.title)
                            putExtra("original_content", post.content)
                            putExtra("content_type", post.contentType)
                            putExtra("board_id", post.baId)
                        }
                        context.startActivity(intent)
                    },
                    onDelete = {
                        onDeletePost(post.id)
                    }
                )
            }
            
            // 空状态
            if (posts.isEmpty() && !isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "未找到匹配的文章" else "暂无文章",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // 加载状态
            if (isLoading && posts.isEmpty()) {
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

/**
 * 我的文章项
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MyPostItem(
    post: CommunityPost,
    onClick: () -> Unit,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showContextMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showContextMenu = true }
            ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 文章标题
            Text(
                text = post.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 文章内容预览
            if (post.content.isNotEmpty()) {
                Text(
                    text = post.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // 文章信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = post.createTimeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ThumbUp,
                            contentDescription = "点赞",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${post.likeNum}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Comment,
                            contentDescription = "评论",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${post.commentNum}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "收藏",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${post.collectNum}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        // 上下文菜单
        if (showContextMenu) {
            PostContextMenu(
                onDismiss = { showContextMenu = false },
                onEdit = {
                    showContextMenu = false
                    onEdit()
                },
                onDelete = {
                    showContextMenu = false
                    showDeleteDialog = true
                }
            )
        }
        
        // 删除确认对话框
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("删除文章") },
                text = { Text("确定要删除这篇文章吗？删除后无法恢复。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            onDelete()
                        }
                    ) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("取消")
                    }
                }
            )
        }
    }
}

/**
 * 文章上下文菜单
 */
@Composable
fun PostContextMenu(
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("操作选项")
        },
        text = {
            Column {
                TextButton(
                    onClick = onEdit,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "编辑"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("编辑文章")
                    }
                }
                
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "删除文章",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
