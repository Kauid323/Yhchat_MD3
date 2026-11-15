@file:OptIn(ExperimentalMaterial3Api::class)
package com.yhchat.canary.ui.community

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yhchat.canary.data.di.RepositoryFactory
import com.yhchat.canary.ui.base.BaseActivity
import com.yhchat.canary.ui.theme.YhchatCanaryTheme

/**
 * 我的文章Activity
 */
class MyPostsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val token = intent.getStringExtra("token") ?: ""
        
        setContent {
            YhchatCanaryTheme {
                val viewModel: CommunityViewModel = viewModel {
                    CommunityViewModel(
                        communityRepository = RepositoryFactory.getCommunityRepository(this@MyPostsActivity)
                    )
                }
                
                MyPostsScreen(
                    token = token,
                    viewModel = viewModel,
                    onBackClick = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyPostsScreen(
    token: String,
    viewModel: CommunityViewModel,
    onBackClick: () -> Unit,
    onPostNavigate: ((postId: Int, postTitle: String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val myPostListState by viewModel.myPostListState.collectAsState()

    // 草稿 ViewModel 由这里自己取，而不是让调用方传。
    //
    // 原来想过写成 `draftViewModel?.draftBoxState?.collectAsState() ?: remember {...}`，
    // 但那样 remember 处于条件分支里 —— 参数为 null 时每次重组都会新建状态对象，
    // 且 Compose 不允许在条件里调用 remember。改成无条件取，调用方零负担。
    val draftViewModel: CommunityDraftViewModel = viewModel {
        CommunityDraftViewModel(
            communityRepository = RepositoryFactory.getCommunityRepository(context)
        )
    }
    val draftBoxState by draftViewModel.draftBoxState.collectAsState()
    
    // 搜索状态
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    // 本地过滤文章列表
    val filteredPosts = remember(myPostListState.posts, searchQuery) {
        if (searchQuery.isBlank()) {
            myPostListState.posts
        } else {
            myPostListState.posts.filter { post ->
                post.title.contains(searchQuery, ignoreCase = true) ||
                post.content.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // 草稿同样支持搜索，规则和文章保持一致
    val filteredDrafts = remember(draftBoxState.drafts, searchQuery) {
        if (searchQuery.isBlank()) {
            draftBoxState.drafts
        } else {
            draftBoxState.drafts.filter { draft ->
                draft.title.contains(searchQuery, ignoreCase = true) ||
                draft.content.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    
    // 草稿编辑回来后要刷新：用户可能在编辑页把草稿发布了（草稿消失）、
    // 或者又存了一次（排序/时间变了）。不刷新的话列表停在旧状态。
    val draftEditorLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) {
        if (token.isNotEmpty()) {
            draftViewModel.loadDrafts(token)
            viewModel.refreshMyPostList(token)
        }
    }

    // 加载数据
    LaunchedEffect(token) {
        if (token.isNotEmpty()) {
            viewModel.loadMyPostList(token)
            draftViewModel.loadDrafts(token)
        }
    }
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    if (isSearching) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("搜索我的文章...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    } else {
                        Text(
                            text = "我的文章",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
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
                    if (isSearching) {
                        IconButton(
                            onClick = {
                                isSearching = false
                                searchQuery = ""
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "取消搜索"
                            )
                        }
                    } else {
                        IconButton(onClick = { isSearching = true }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "搜索我的文章"
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            val intent = android.content.Intent(context, BlockedUsersActivity::class.java).apply {
                                putExtra("token", token)
                            }
                            context.startActivity(intent)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = "屏蔽用户"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        
        // 内容区域
        PullToRefreshBox(
            isRefreshing = myPostListState.isRefreshing,
            onRefresh = {
                viewModel.refreshMyPostList(token)
                draftViewModel.loadDrafts(token)
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            MyPostsContentWithDrafts(
                drafts = filteredDrafts,
                draftsLoading = draftBoxState.isLoading,
                posts = filteredPosts,
                postsLoading = myPostListState.isLoading,
                error = myPostListState.error ?: draftBoxState.error,
                searchQuery = searchQuery,
                onDraftClick = { draft -> openDraftEditor(context, token, draft, draftEditorLauncher) },
                onDraftDelete = { draft -> draftViewModel.deleteDraft(token, draft.id) },
                deletingDraftId = draftBoxState.deletingDraftId,
                onPostClick = { post ->
                    if (onPostNavigate != null) {
                        onPostNavigate(post.id, post.title)
                    } else {
                        val intent = android.content.Intent(context, PostDetailActivity::class.java).apply {
                            putExtra("post_id", post.id)
                            putExtra("token", token)
                        }
                        context.startActivity(intent)
                    }
                },
                onPostEdit = { post ->
                    val intent = android.content.Intent(context, EditPostActivity::class.java).apply {
                        putExtra("post_id", post.id)
                        putExtra("token", token)
                        putExtra("original_title", post.title)
                        putExtra("original_content", post.content)
                        putExtra("content_type", post.contentType)
                        putExtra("board_id", post.baId)
                    }
                    context.startActivity(intent)
                },
                onDeletePost = { postId ->
                    viewModel.deletePost(token, postId)
                }
            )
        }
    }
}

/**
 * 把草稿点开进入发布页继续编辑。
 *
 * 复用 CreatePostActivity 而不是单独做一个「草稿编辑页」：草稿和发布用的是同一套
 * 标题/正文/Markdown 工具栏，分成两个界面等于把同一份代码维护两遍。
 * 带上 draft_id 后，发布页保存时会走 update 而不是 insert。
 *
 * 传 launcher 而不是直接 startActivity：用户在里面可能把草稿发布了（草稿被删）
 * 或又存了一次（时间变了），回到列表必须重新拉一遍，否则看到的还是旧的。
 */
private fun openDraftEditor(
    context: android.content.Context,
    token: String,
    draft: com.yhchat.canary.data.model.Draft,
    launcher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>? = null
) {
    val intent = android.content.Intent(context, CreatePostActivity::class.java).apply {
        putExtra("board_id", draft.boardId)
        putExtra("board_name", draft.boardName)
        putExtra("token", token)
        putExtra("draft_id", draft.id)
        putExtra("draft_title", draft.title)
        putExtra("draft_content", draft.content)
        putExtra("draft_markdown_mode", draft.isMarkdownMode)
    }
    if (launcher != null) {
        launcher.launch(intent)
    } else {
        context.startActivity(intent)
    }
}
