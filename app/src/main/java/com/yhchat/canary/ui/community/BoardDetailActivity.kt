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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

    // 点顶部胶囊回到分区头部（第一项）
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

        // ===== 悬浮栏（覆盖在列表之上，三件式：返回 / 分区名 / 全部-只看楼主）=====
        BoardFloatingBar(
            title = board?.name ?: boardName,
            boardAvatar = board?.avatar,
            boardId = boardId,
            token = token,
            onBackClick = onBackClick,
            onBoardClick = {
                focusBoardHeader()
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

/** 悬浮栏占位高度（40dp 栏高 + 上下各 8dp 内边距），列表顶部据此留白。 */
private val FloatingBarHeight = 56.dp

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
/*  悬浮栏                                                                    */
/* -------------------------------------------------------------------------- */

/**
 * 顶部三件式悬浮栏，照截图排：
 *   ① 独立圆形白底返回按钮（固定 40dp 圆，不跟中间胶囊连）
 *   ② 中间白底胶囊：分区头像 + 分区名，撑满剩余宽度，始终可见
 *   ③ 右侧白底分段控件「全部 / 只看楼主」
 *
 * 三个都是**各自成块的实心白面**，浮在列表之上；列表从它们之间和下面滚过去。
 * 不再用整条通栏背景，所以滚动时不会有一条灰带横在中间。
 */
@Composable
private fun BoardFloatingBar(
    title: String,
    boardAvatar: String?,
    boardId: Int,
    token: String,
    onBackClick: () -> Unit,
    onBoardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ① 圆形返回
        CircleChromeButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp)
            )
        }

        // ② 分区名胶囊：头像 + 名称，点进分区详情
        Surface(
            onClick = onBoardClick,
            modifier = Modifier.weight(1f),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = ChromeElevation
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ChromeHeight)
                    .padding(start = 6.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = boardAvatar?.let {
                        ImageUtils.createBoardImageRequest(
                            context = LocalContext.current,
                            url = it
                        )
                    },
                    contentDescription = title,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // ③ 分段控件的位子是文章详情页的（「全部 / 只看楼主」筛的是评论、楼主是文章作者），
        // 分区页这里只保留返回 + 分区名两块。
    }
}

/** 顶部那几个悬浮块的统一高度与阴影。 */
private val ChromeHeight = 40.dp
private val ChromeElevation = 3.dp

/** 白底圆形图标按钮，返回键用的就是它。 */
@Composable
private fun CircleChromeButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(ChromeHeight),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = ChromeElevation
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

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
