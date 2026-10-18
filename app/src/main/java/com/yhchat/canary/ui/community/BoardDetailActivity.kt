@file:OptIn(ExperimentalMaterial3Api::class)

package com.yhchat.canary.ui.community

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.yhchat.canary.data.di.RepositoryFactory
import com.yhchat.canary.data.model.CommunityBoard
import com.yhchat.canary.data.model.CommunityPost
import com.yhchat.canary.ui.base.BaseActivity
import com.yhchat.canary.ui.components.ImageUtils
import com.yhchat.canary.ui.theme.YhchatCanaryTheme

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
    
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = boardDetailState.board?.name ?: boardName,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val intent = Intent(context, SearchActivity::class.java).apply {
                                putExtra("token", token)
                            }
                            context.startActivity(intent)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "搜索"
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val intent = Intent(context, CreatePostActivity::class.java).apply {
                        putExtra("board_id", boardId)
                        putExtra("board_name", boardDetailState.board?.name ?: boardName)
                        putExtra("token", token)
                    }
                    context.startActivity(intent)
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "发布文章"
                )
            }
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = boardDetailState.isLoading || postListState.isLoading,
            onRefresh = {
                if (token.isNotBlank() && boardId > 0) {
                    viewModel.refresh(token, boardId)
                }
            },
            state = rememberPullToRefreshState(),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    boardDetailState.board?.let { board ->
                        BoardInfoCard(
                            board = board,
                            onGroupListClick = {
                                val intent = Intent(context, GroupListActivity::class.java).apply {
                                    putExtra("board_id", boardId)
                                    putExtra("board_name", board.name)
                                    putExtra("token", token)
                                }
                                context.startActivity(intent)
                            },
                            onFollowClick = { viewModel.followBoard(token, boardId) },
                            followState = followState,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }

                postListState.error?.let { error ->
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
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

                items(postListState.posts) { post ->
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
                        }
                    )
                }

                if (postListState.posts.isNotEmpty() && postListState.hasMore) {
                    item {
                        LaunchedEffect(Unit) {
                            if (!postListState.isLoading) {
                                viewModel.loadMorePosts(token, boardId)
                            }
                        }

                        if (postListState.isLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                }

                if (postListState.posts.isEmpty() && !postListState.isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "暂无文章",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (postListState.isLoading && postListState.posts.isEmpty()) {
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
 * 分区信息卡片
 */
@Composable
fun BoardInfoCard(
    board: CommunityBoard,
    onGroupListClick: () -> Unit,
    onFollowClick: () -> Unit,
    followState: FollowState,
    modifier: Modifier = Modifier
) {
    var showImageViewer by remember(board.avatar) { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ImageUtils.createBoardImageRequest(
                    context = LocalContext.current,
                    url = board.avatar
                ),
                contentDescription = board.name,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = board.avatar.isNotBlank()) {
                        showImageViewer = true
                    },
                contentScale = ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = board.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    InfoItem(label = "成员", value = board.memberNum.toString())
                    InfoItem(label = "文章", value = board.postNum.toString())
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onGroupListClick() }
                    ) {
                    InfoItem(label = "群聊", value = board.groupNum.toString())
                        if (board.groupNum > 0) {
                            Text(
                                text = " >",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "创建于 ${board.createTimeText}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // 关注状态按钮
            Button(
                onClick = onFollowClick,
                enabled = !followState.isLoading,
                modifier = Modifier.height(32.dp)
            ) {
                if (followState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 1.dp
                    )
                } else {
                    Text(
                        text = if (board.isFollowed == "1") "已关注" else "未关注",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    if (showImageViewer && board.avatar.isNotBlank()) {
        com.yhchat.canary.ui.components.ImageViewer(
            imageUrl = board.avatar,
            onDismiss = { showImageViewer = false }
        )
    }
}

/**
 * 信息项
 */
@Composable
private fun InfoItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
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
