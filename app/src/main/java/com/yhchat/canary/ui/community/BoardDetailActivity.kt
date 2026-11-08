@file:OptIn(ExperimentalMaterial3Api::class)

package com.yhchat.canary.ui.community

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.yhchat.canary.data.di.RepositoryFactory
import com.yhchat.canary.data.model.CommunityBoard
import com.yhchat.canary.data.model.CommunityPost
import com.yhchat.canary.ui.base.BaseActivity
import com.yhchat.canary.ui.components.ImageUtils
import com.yhchat.canary.ui.theme.YhchatCanaryTheme
import kotlinx.coroutines.launch

/**
 * 分区详情Activity - 显示某个分区的文章列表
 */
class BoardDetailActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val boardId = intent.getIntExtra("board_id", 0)
        val boardName = intent.getStringExtra("board_name") ?: "分区详情"
        val tokenFromIntent = intent.getStringExtra("token") ?: ""
        
        setContent {
            YhchatCanaryTheme {
                val tokenRepository = remember {
                    RepositoryFactory.getTokenRepository(this@BoardDetailActivity)
                }
                val resolvedToken by produceState(initialValue = tokenFromIntent, key1 = tokenFromIntent) {
                    if (tokenFromIntent.isNotBlank()) {
                        value = tokenFromIntent
                    } else {
                        value = runCatching { tokenRepository.getTokenSync().orEmpty() }.getOrDefault("")
                    }
                }
                val viewModel: BoardDetailViewModel = viewModel {
                    BoardDetailViewModel(
                        communityRepository = RepositoryFactory.getCommunityRepository(this@BoardDetailActivity),
                        tokenRepository = RepositoryFactory.getTokenRepository(this@BoardDetailActivity)
                    )
                }
                
                BoardDetailScreen(
                    boardId = boardId,
                    boardName = boardName,
                    token = resolvedToken,
                    viewModel = viewModel,
                    onBackClick = { finish() }
                )
            }
        }
    }
}

/**
 * 分区详情界面
 *
 * 参照 Oolong 的 ForumScreen：一页为「共享大头部 + 悬浮顶栏 + 文章流」，
 * 头部随列表上滚收进悬浮栏，栏下方的统计/关注区跟着淡出，正文不再垫一层实心 TopAppBar。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardDetailScreen(
    boardId: Int,
    boardName: String,
    token: String,
    viewModel: BoardDetailViewModel,
    onBackClick: () -> Unit,
    onPostNavigate: ((postId: Int, postTitle: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // 获取状态
    val boardDetailState by viewModel.boardDetailState.collectAsState()
    val postListState by viewModel.postListState.collectAsState()
    val followState by viewModel.followState.collectAsState()
    val blockState by viewModel.blockState.collectAsState()

    // 显示屏蔽对话框的状态
    var showBlockDialog by remember { mutableStateOf(false) }
    var selectedPost by remember { mutableStateOf<CommunityPost?>(null) }

    // 加载数据
    LaunchedEffect(boardId, token) {
        if (token.isNotEmpty() && boardId > 0) {
            viewModel.loadBoardDetail(token, boardId)
            viewModel.loadPostList(token, boardId)
        }
    }

    val board = boardDetailState.board
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // 头部滚走后，悬浮栏接管标题；用首项偏移量当作折叠进度
    val headerCollapsed by remember {
        derivedStateOf {
            val first = listState.firstVisibleItemIndex
            val offset = listState.firstVisibleItemScrollOffset
            first > 0 || offset > HeaderDockDistance
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = boardDetailState.isLoading || postListState.isLoading,
            onRefresh = {
                if (token.isNotBlank() && boardId > 0) {
                    viewModel.refresh(token, boardId)
                }
            },
            state = rememberPullToRefreshState(),
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                // 顶部留出悬浮栏高度，头部内容从栏下开始，不跟栏打架
                contentPadding = PaddingValues(top = FloatingBarHeight, bottom = 96.dp)
            ) {
                // ===== 大头部 =====
                item(key = "board_header") {
                    BoardHeader(
                        board = board,
                        boardName = boardName,
                        followState = followState,
                        onGroupListClick = {
                            board?.let { b ->
                                val intent = Intent(context, GroupListActivity::class.java).apply {
                                    putExtra("board_id", boardId)
                                    putExtra("board_name", b.name)
                                    putExtra("token", token)
                                }
                                context.startActivity(intent)
                            }
                        },
                        onFollowClick = { viewModel.followBoard(token, boardId) }
                    )
                }

                postListState.error?.let { error ->
                    item(key = "board_error") {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = error,
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // ===== 文章流 =====
                itemsIndexed(
                    items = postListState.posts,
                    key = { _, post -> post.id }
                ) { index, post ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (index > 0) {
                            HorizontalHairLine(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                        PostListItem(
                            post = post,
                            onClick = {
                                if (onPostNavigate != null) {
                                    onPostNavigate(post.id, post.title)
                                } else {
                                    val intent = Intent(context, PostDetailActivity::class.java).apply {
                                        putExtra("post_id", post.id)
                                        putExtra("post_title", post.title)
                                        putExtra("token", token)
                                    }
                                    context.startActivity(intent)
                                }
                            },
                            onLongClick = {
                                selectedPost = post
                                showBlockDialog = true
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }

                // ===== 加载更多 / 空态 / 首屏加载 =====
                if (postListState.posts.isNotEmpty() && postListState.hasMore) {
                    item(key = "board_load_more") {
                        LaunchedEffect(Unit) {
                            if (!postListState.isLoading) {
                                viewModel.loadMorePosts(token, boardId)
                            }
                        }
                        LoadMoreFooter(isLoading = postListState.isLoading)
                    }
                }

                if (postListState.posts.isEmpty() && !postListState.isLoading) {
                    item(key = "board_empty") {
                        EmptyPostsState()
                    }
                }

                if (postListState.isLoading && postListState.posts.isEmpty()) {
                    item(key = "board_first_load") {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            repeat(3) { PostCardPlaceholder() }
                        }
                    }
                }
            }
        }

        // ===== 悬浮栏（覆盖在列表之上，头部收起后渐显）=====
        BoardFloatingBar(
            title = board?.name ?: boardName,
            subtitle = board?.let { "${it.memberNum} 成员 · ${it.postNum} 文章" },
            collapsed = headerCollapsed,
            onBackClick = onBackClick,
            onSearchClick = {
                val intent = Intent(context, SearchActivity::class.java).apply {
                    putExtra("token", token)
                }
                context.startActivity(intent)
            },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // ===== 悬浮按钮：回顶 / 发布 =====
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AnimatedVisibility(
                visible = listState.firstVisibleItemIndex > 0,
                enter = fadeIn() + scaleIn(initialScale = 0.85f),
                exit = fadeOut() + scaleOut(targetScale = 0.85f)
            ) {
                Surface(
                    onClick = {
                        coroutineScope.launch { listState.animateScrollToItem(0) }
                    },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shadowElevation = 4.dp,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "回到顶部",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Surface(
                onClick = {
                    val intent = Intent(context, CreatePostActivity::class.java).apply {
                        putExtra("board_id", boardId)
                        putExtra("board_name", board?.name ?: boardName)
                        putExtra("token", token)
                    }
                    context.startActivity(intent)
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 8.dp,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "发布文章",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }

    // 屏蔽用户对话框
    if (showBlockDialog && selectedPost != null) {
        AlertDialog(
            onDismissRequest = {
                showBlockDialog = false
                selectedPost = null
            },
            title = { Text("屏蔽用户") },
            text = { Text("确定要屏蔽用户 ${selectedPost?.senderNickname} 吗？屏蔽后将不再看到该用户的文章。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedPost?.let { post ->
                            viewModel.blockUser(token, post.senderId)
                        }
                        showBlockDialog = false
                        selectedPost = null
                    },
                    enabled = !blockState.isLoading
                ) {
                    if (blockState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("确定")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showBlockDialog = false
                        selectedPost = null
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }

    // 屏蔽成功提示
    LaunchedEffect(blockState.isSuccess) {
        if (blockState.isSuccess) {
            // 显示成功提示
            viewModel.resetBlockState()
        }
    }

    // 屏蔽错误提示
    blockState.error?.let { error ->
        AlertDialog(
            onDismissRequest = { viewModel.resetBlockState() },
            title = { Text("错误") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { viewModel.resetBlockState() }) {
                    Text("确定")
                }
            }
        )
    }
}

/** 头部滚到该距离之后，悬浮栏接管标题。 */
private val HeaderDockDistance = 120

/** 悬浮栏总高（含状态栏内边距之外的行高），列表顶部据此留白。 */
private val FloatingBarHeight = 52.dp

/* -------------------------------------------------------------------------- */
/*  大头部                                                                    */
/* -------------------------------------------------------------------------- */

/**
 * 分区大头部：头像 + 分区名 + 简介 + 统计行 + 全宽胶囊关注按钮。
 * 收起时整块跟随列表走掉，不做钉住，标题交给悬浮栏。
 */
@Composable
private fun BoardHeader(
    board: CommunityBoard?,
    boardName: String,
    followState: FollowState,
    onGroupListClick: () -> Unit,
    onFollowClick: () -> Unit
) {
    var showImageViewer by remember(board?.avatar) { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = board?.let {
                    ImageUtils.createBoardImageRequest(
                        context = LocalContext.current,
                        url = it.avatar
                    )
                },
                contentDescription = board?.name ?: boardName,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(enabled = !board?.avatar.isNullOrBlank()) {
                        showImageViewer = true
                    },
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = board?.name ?: boardName,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val created = board?.createTimeText.orEmpty()
                if (created.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "创建于 $created",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 统计行：成员 / 文章 / 群聊（群聊可点，右侧带 ">"）
        Row(verticalAlignment = Alignment.Bottom) {
            BoardStat(
                value = board?.memberNum?.toString() ?: "—",
                label = "成员"
            )
            Spacer(modifier = Modifier.width(24.dp))
            BoardStat(
                value = board?.postNum?.toString() ?: "—",
                label = "文章"
            )
            if ((board?.groupNum ?: 0) > 0) {
                Spacer(modifier = Modifier.width(24.dp))
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.clickable { onGroupListClick() }
                ) {
                    BoardStat(
                        value = board?.groupNum?.toString() ?: "0",
                        label = "群聊"
                    )
                    Text(
                        text = "›",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                        modifier = Modifier.padding(start = 2.dp, bottom = 14.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 全宽胶囊关注按钮：已关注时转 tonal 且不可点
        val done = board?.isFollowed == "1"
        val busy = followState.isLoading
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .clip(CircleShape)
                .background(
                    when {
                        busy -> accent.copy(alpha = 0.4f)
                        done -> accent.copy(alpha = 0.14f)
                        else -> accent
                    }
                )
                .clickable(enabled = !done && !busy) { onFollowClick() },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                busy -> CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = if (done) accent else MaterialTheme.colorScheme.onPrimary
                )
                done -> {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "已关注",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accent
                    )
                }
                else -> Text(
                    text = "关注分区",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }

    if (showImageViewer && !board?.avatar.isNullOrBlank()) {
        com.yhchat.canary.ui.components.ImageViewer(
            imageUrl = board?.avatar.orEmpty(),
            onDismiss = { showImageViewer = false }
        )
    }
}

/** 头部里的一格统计：大号数字 + 小号标签。 */
@Composable
private fun BoardStat(value: String, label: String) {
    Column {
        Text(
            text = value,
            fontSize = 20.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/* -------------------------------------------------------------------------- */
/*  悬浮栏                                                                    */
/* -------------------------------------------------------------------------- */

/**
 * 悬浮在列表之上的胶囊栏：返回 + 标题。
 * 未收起时半透明（列表从它下面透出来），收起后加不透明度与阴影，成为真正的标题栏；
 * 副标题（成员/文章数）跟着淡入。
 */
@Composable
private fun BoardFloatingBar(
    title: String,
    subtitle: String?,
    collapsed: Boolean,
    onBackClick: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        // 收起后才出现的背景胶囊，让标题从正文里“浮”出来
        val barAlpha = if (collapsed) 1f else 0f
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .alpha(barAlpha),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f),
            shadowElevation = 3.dp
        ) {}

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                AnimatedVisibility(
                    visible = collapsed && !subtitle.isNullOrBlank(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Text(
                        text = subtitle.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(onClick = onSearchClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "搜索",
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/* -------------------------------------------------------------------------- */
/*  列表零碎                                                                  */
/* -------------------------------------------------------------------------- */

/** 极细分隔线，比 Divider 更安静。 */
@Composable
private fun HorizontalHairLine(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    )
}

/** 加载更多页脚。 */
@Composable
private fun LoadMoreFooter(isLoading: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = "继续上滑加载更多",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 空态。 */
@Composable
private fun EmptyPostsState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "这个分区还没有文章",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "点右下角的 + 写第一篇",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

/** 首屏加载时的骨架卡，形状对齐 CommunityPostCard。 */
@Composable
private fun PostCardPlaceholder() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    SkeletonBar(width = 96.dp)
                    Spacer(modifier = Modifier.height(6.dp))
                    SkeletonBar(width = 56.dp)
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            SkeletonBar(width = 220.dp)
            Spacer(modifier = Modifier.height(8.dp))
            SkeletonBar(width = 280.dp)
            Spacer(modifier = Modifier.height(8.dp))
            SkeletonBar(width = 160.dp)
        }
    }
}

@Composable
private fun SkeletonBar(width: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .width(width)
            .height(10.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    )
}

/**
 * 文章列表项
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PostListItem(
    post: CommunityPost,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    com.yhchat.canary.ui.community.CommunityPostCard(
        post = post,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
    )
}
