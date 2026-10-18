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
    
    // 加载数据
    LaunchedEffect(token) {
        if (token.isNotEmpty()) {
            viewModel.loadMyPostList(token)
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
            onRefresh = { viewModel.refreshMyPostList(token) },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            MyPostListContent(
                posts = filteredPosts,
                isLoading = myPostListState.isLoading,
                error = myPostListState.error,
                searchQuery = searchQuery,
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
                onDeletePost = { postId ->
                    viewModel.deletePost(token, postId)
                },
                context = context,
                token = token
            )
        }
    }
}
