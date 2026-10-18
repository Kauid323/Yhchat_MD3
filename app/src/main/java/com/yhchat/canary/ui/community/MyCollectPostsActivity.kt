@file:OptIn(ExperimentalMaterial3Api::class)
package com.yhchat.canary.ui.community

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yhchat.canary.data.di.RepositoryFactory
import com.yhchat.canary.ui.base.BaseActivity
import com.yhchat.canary.ui.theme.YhchatCanaryTheme

class MyCollectPostsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val token = intent.getStringExtra("token") ?: ""

        setContent {
            YhchatCanaryTheme {
                val vm: CommunityViewModel = viewModel {
                    CommunityViewModel(
                        communityRepository = RepositoryFactory.getCommunityRepository(this@MyCollectPostsActivity)
                    )
                }

                MyCollectPostsScreen(
                    token = token,
                    viewModel = vm,
                    onBackClick = { finish() }
                )
            }
        }
    }
}

@Composable
fun MyCollectPostsScreen(
    token: String,
    viewModel: CommunityViewModel,
    onBackClick: () -> Unit,
    onPostNavigate: ((postId: Int, postTitle: String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val collectState by viewModel.collectPostListState.collectAsState()

    LaunchedEffect(token) {
        if (token.isNotEmpty()) {
            viewModel.loadCollectPostList(token, page = 1, isRefresh = false)
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "我的收藏",
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = collectState.isRefreshing,
            onRefresh = { viewModel.refreshCollectPostList(token) },
            state = rememberPullToRefreshState(),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                collectState.error != null -> {
                    Card(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = collectState.error ?: "加载失败",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                collectState.posts.isEmpty() && collectState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                collectState.posts.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无收藏",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(collectState.posts) { post ->
                            PostListItem(
                                post = post,
                                onClick = {
                                    if (onPostNavigate != null) {
                                        onPostNavigate(post.id, post.title)
                                    } else {
                                        val intent = android.content.Intent(context, PostDetailActivity::class.java).apply {
                                            putExtra("post_id", post.id)
                                            putExtra("token", token)
                                        }
                                        context.startActivity(intent)
                                    }
                                }
                            )
                        }

                        if (collectState.posts.isNotEmpty() && collectState.hasMore) {
                            item {
                                Button(
                                    onClick = { viewModel.loadMoreCollectPosts(token) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    enabled = !collectState.isLoading
                                ) {
                                    if (collectState.isLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier
                                                .padding(end = 8.dp),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                    Text(if (collectState.isLoading) "加载中..." else "加载更多")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
