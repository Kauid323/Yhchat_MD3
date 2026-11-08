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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.yhchat.canary.data.di.RepositoryFactory
import com.yhchat.canary.data.model.CommunityBoard
import com.yhchat.canary.data.model.CommunityPost
import com.yhchat.canary.ui.base.BaseActivity
import com.yhchat.canary.ui.components.ImageUtils
import com.yhchat.canary.ui.components.LocalPageScroll
import com.yhchat.canary.ui.components.matchedGeometry
import com.yhchat.canary.ui.components.matchedGeometryAnchor
import com.yhchat.canary.ui.components.pageScroll
import com.yhchat.canary.ui.components.rememberPageScrollState
import com.yhchat.canary.ui.components.rememberScrollProgress
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

    // 整页的滚动状态：顶栏和内容共享它
    val pageScroll = rememberPageScrollState()

    // 大头部有没有滚走 —— 滚走之前顶栏不该收，否则一进来往上滑一点就没了
    val headerGone by remember {
        derivedStateOf {
            val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }
            // 第一项完全滚出可视区，就算滚走了
            info == null || info.offset + info.size < 0
        }
    }

    // 顶栏收起进度：只在头部滚走后才跟手，往上滑 → 1，往回滑 → 0
    val barAway = rememberScrollProgress(
        away = BoardBarRowHeight,
        enabled = { headerGone },
        page = pageScroll
    )

    // 打开文章：有外部导航回调就走回调，否则自己开详情页
    val openPost: (CommunityPost) -> Unit = { post ->
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
    }

    // 点顶栏回到分区头部（第一项）
    fun focusBoardHeader() {
        coroutineScope.launch { listState.animateScrollToItem(0) }
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
                    // 内容上报自己的滚动位置给顶栏
                    .pageScroll(listState),
                // 顶部留出顶栏高度，头部内容从栏下开始；列表可以滚到顶栏后面
                contentPadding = PaddingValues(top = TopBarHeight, bottom = 96.dp)
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
                // 置顶文章按 Oolong 的做法：不铺成卡片，只占一行 —— 左侧「置顶」胶囊 + 单行标题，
                // 全部排在列表最前面，之后才是普通文章；置顶和普通之间留一段空，分隔感由位置给出。
                // 「只看楼主」开启时置顶仍保留（置顶是分区自己的内容，不该被筛选掉）。
                val pinnedPosts = postListState.posts.filter { it.isSticky != 0 }
                val normalPosts = postListState.posts.filter { it.isSticky == 0 }

                itemsIndexed(
                    items = pinnedPosts,
                    key = { _, post -> "pinned_${post.id}" }
                ) { _, post ->
                    PinnedPostRow(
                        title = post.title,
                        onClick = { openPost(post) }
                    )
                }

                if (pinnedPosts.isNotEmpty() && normalPosts.isNotEmpty()) {
                    item(key = "pinned_gap") {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                itemsIndexed(
                    items = normalPosts,
                    key = { _, post -> post.id }
                ) { index, post ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (index > 0) {
                            VerticalHairLine(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                        PostListItem(
                            post = post,
                            onClick = { openPost(post) },
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

        // ===== 顶栏（覆盖在列表之上：返回 + 分区名，玻璃底、随滚动收起）=====
        BoardTopBar(
            hiddenFraction = { barAway.value },
            pinnedOffset = { 0 },
            onBackClick = onBackClick,
            titleContent = {
                BoardBarTitle(
                    title = board?.name ?: boardName,
                    boardAvatar = board?.avatar,
                    onBoardClick = { focusBoardHeader() }
                )
            },
            // 分区页是文章列表，没有评论可筛 —— 「只看楼主」只属于文章详情
            pinnedContent = {},
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

/**
 * 顶栏占位高度：顶栏行 56dp。
 * 列表的 `contentPadding.top` 用它，头部内容从顶栏下面开始。
 */
private val TopBarHeight = 56.dp

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
            // 大头像：它自己画出来，同时作为锚点告诉顶栏里那个小头像「该从哪来」
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
                    .matchedGeometryAnchor(BoardAvatarAnchor)
                    .clickable(enabled = !board?.avatar.isNullOrBlank()) {
                        showImageViewer = true
                    },
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                // 标题本身不画（顶栏里那个才画），只占位给顶栏的标题当锚点用
                Text(
                    text = board?.name ?: boardName,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .matchedGeometryAnchor(BoardNameAnchor)
                        .graphicsLayer { alpha = 0f }
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

        // 统计行在左，关注按钮挂在右边，跟统计数字同一行
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
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

            FollowPill(
                followed = board?.isFollowed == "1",
                busy = followState.isLoading,
                accent = accent,
                onClick = onFollowClick
            )
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

/**
 * 分区关注按钮，挂在统计行右侧。
 * 跟着统计行一起下沉，所以用 Alignment.Bottom 对齐，并自己补一点底部内边距，
 * 让胶囊底边跟统计标签的基线大致齐平。
 */
@Composable
private fun FollowPill(
    followed: Boolean,
    busy: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .padding(bottom = 2.dp)
            .height(32.dp)
            .clip(CircleShape)
            .background(
                when {
                    busy -> accent.copy(alpha = 0.4f)
                    followed -> accent.copy(alpha = 0.14f)
                    else -> accent
                }
            )
            .clickable(enabled = !followed && !busy) { onClick() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        when {
            busy -> CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = if (followed) accent else MaterialTheme.colorScheme.onPrimary
            )
            followed -> {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "已关注",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accent
                )
            }
            else -> Text(
                text = "关注",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

/* -------------------------------------------------------------------------- */
/*  顶栏                                                                      */
/* -------------------------------------------------------------------------- */

/**
 * 顶栏，机制照 Oolong 的 `GlassTopBar`。
 *
 * 跟之前那版最本质的区别是**背景**：不是实心色块，而是一层从上往下
 * **渐隐到完全透明**的玻璃。列表穿过去是柔的，不会撞在一条硬边上。
 *
 * 收起：整行按 [hiddenFraction] 往上滑走并淡出。这个 0..1 由
 * `rememberScrollProgress(away = 行高)` 给出 —— 内容往上滑它走向 1，
 * 往回滑一点它立刻回到 0，所以是「滑的时候自己收、再滑回来才出来」。
 * 状态栏那一块玻璃始终在，内容不会从状态栏底下硬切过去。
 *
 * @param hiddenFraction 0 显示、1 完全收起
 * @param pinnedOffset 下方常驻行比顶栏行低多少像素
 * @param pinnedContent 收起时也不走的那一行（分段控件）
 */
@Composable
private fun BoardTopBar(
    hiddenFraction: () -> Float,
    pinnedOffset: () -> Int,
    onBackClick: () -> Unit,
    titleContent: @Composable () -> Unit,
    pinnedContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusBar = WindowInsets.statusBars
    val page = LocalPageScroll.current

    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            // 背景：玻璃渐变，往下淡出到透明，不留硬边
            Box(
                modifier = Modifier
                    .layoutId(BoardBarLayer.Background)
                    .boardGlassBackground()
            )

            // 前景：返回 + 分区名（会跟着滚走）
            Column(
                modifier = Modifier
                    .layoutId(BoardBarLayer.Foreground)
                    .windowInsetsPadding(statusBar)
                    .onGloballyPositioned { coords ->
                        // 记下这一行在页面里的上下边，吸附的头像和标题靠它算进度
                        if (page != null) {
                            val row = page.boundsOf(coords)
                            page.barRowTop = row.top
                            page.barRowBottom = row.bottom
                        }
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(BoardBarRowHeight)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BoardNavButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) { titleContent() }
                }
            }

            // 常驻行：分段控件，顶栏收起时它留在原地
            Box(
                modifier = Modifier
                    .layoutId(BoardBarLayer.Pinned)
                    .windowInsetsPadding(statusBar.only(WindowInsetsSides.Horizontal))
            ) {
                pinnedContent()
            }
        }
    ) { measurables, constraints ->
        val loose = constraints.copy(minHeight = 0)
        val foreground = measurables.first { it.layoutId == BoardBarLayer.Foreground }.measure(loose)
        val pinned = measurables.firstOrNull { it.layoutId == BoardBarLayer.Pinned }?.measure(loose)
        val height = foreground.height + (pinned?.height ?: 0)

        // 隐藏时把状态栏以下的行整体往上推，pinned 行跟着上移
        val statusTop = statusBar.getTop(this)
        val hidden = ((foreground.height - statusTop) * hiddenFraction()).toInt()

        // 玻璃只铺到顶栏行底部，再往下多留一点，让渐变在硬边之前就开始
        val background = measurables.first { it.layoutId == BoardBarLayer.Background }
            .measure(Constraints.fixed(foreground.width, foreground.height + BoardBarEdgeExtend.roundToPx()))

        layout(foreground.width, height) {
            background.place(0, 0)
            // 完全收起时不摆前景：已淡到看不见，还留着会让玻璃每帧都去采样页面
            if (hiddenFraction() < 1f) {
                foreground.placeWithLayer(0, -hidden) { alpha = 1f - hiddenFraction() }
            }
            pinned?.place(0, foreground.height - hidden + pinnedOffset())
        }
    }
}

/** 顶栏那几层的布局 id。 */
private enum class BoardBarLayer { Background, Foreground, Pinned }

/** 顶栏行高。 */
private val BoardBarRowHeight = 56.dp

/** 玻璃往下伸出顶栏多少，让雾在硬边之前就开始。 */
private val BoardBarEdgeExtend = 12.dp

/**
 * 顶栏的玻璃底：从上往下渐隐到完全透明。
 *
 * 顶部留 92% 的 surface 色保证标题读得清，到 65% 处已经淡到 72%，
 * 底边彻底 0 —— 没有硬底边，这就是「玻璃」和「不透明色块」的全部差别。
 */
@Composable
private fun Modifier.boardGlassBackground(): Modifier {
    val base = MaterialTheme.colorScheme.surface
    return this.drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                0f to base.copy(alpha = 0.92f),
                0.65f to base.copy(alpha = 0.70f),
                1f to base.copy(alpha = 0f)
            )
        )
    }
}

/** 顶栏上的圆形悬浮按钮，返回用它。 */
@Composable
private fun BoardNavButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/**
 * 顶栏里的分区标题：头像 + 名称。
 *
 * 两者都用 [matchedGeometry] 跟大头部里同名的锚点配对 —— 大头部滚上来时，
 * 它会从 64dp 的大头像一路缩小、平移进顶栏这条 28dp 的位置，
 * 而不是「突然出现在这里」。
 */
@Composable
private fun BoardBarTitle(
    title: String,
    boardAvatar: String?,
    onBoardClick: () -> Unit
) {
    // 锚点进度：跟「元素和顶栏那一行的相对位置」走，不是跟滚动距离走
    val avatarDock = rememberScrollProgress(BoardAvatarAnchor)
    val nameDock = rememberScrollProgress(BoardNameAnchor)
    val openDetail = Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onBoardClick
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = boardAvatar?.let {
                ImageUtils.createBoardImageRequest(context = LocalContext.current, url = it)
            },
            contentDescription = title,
            modifier = Modifier
                .size(BoardDockedAvatarSize)
                .clip(CircleShape)
                .matchedGeometry(BoardAvatarAnchor, avatarDock)
                .then(openDetail),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .matchedGeometry(BoardNameAnchor, nameDock)
                .then(openDetail)
        )
    }
}

/** 配对用的两个 key：大头部里的和顶栏里的是同一对。 */
private object BoardAvatarAnchor
private object BoardNameAnchor

/** 吸附进顶栏后的头像尺寸。 */
private val BoardDockedAvatarSize = 28.dp

/* -------------------------------------------------------------------------- */
/*  列表零碎                                                                  */
/* -------------------------------------------------------------------------- */

/** 极细分隔线，比 Divider 更安静。 */
@Composable
private fun VerticalHairLine(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    )
}

/**
 * 置顶文章：照 Oolong 的 [TopThreadItem] 排 —— 一行解决，不铺成卡片。
 *
 * 左边「置顶」小胶囊（accent 底 14% alpha），右边标题单行省略，
 * 中间隔 16dp。点整行进文章。
 */
@Composable
private fun PinnedPostRow(
    title: String,
    onClick: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "置顶",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = accent,
            modifier = Modifier
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.14f))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        )
        Text(
            text = title.ifBlank { "无标题文章" },
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
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
