package com.yhchat.canary.ui.search

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yhchat.canary.data.model.BotInfo
import com.yhchat.canary.data.model.GroupDetail
import com.yhchat.canary.data.model.UserInfo
import com.yhchat.canary.ui.theme.YhchatCanaryTheme
import kotlinx.coroutines.launch

class ComprehensiveSearchActivity : ComponentActivity() {
    
    private val viewModel: ComprehensiveSearchViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.yhchat.canary.ui.base.SystemBarUtils.setupTransparentSystemBars(this)
        
        setContent {
            YhchatCanaryTheme {
                ComprehensiveSearchScreen(
                    viewModel = viewModel,
                    onBackClick = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComprehensiveSearchScreen(
    viewModel: ComprehensiveSearchViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var searchText by remember { mutableStateOf("") }
    
    val tabs = listOf("群聊", "用户", "机器人")
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val coroutineScope = rememberCoroutineScope()
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
        TopAppBar(
            title = {
                Text(
                    text = "综合搜索",
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
                IconButton(onClick = {
                    if (searchText.isNotBlank()) {
                        when (pagerState.currentPage) {
                            0 -> viewModel.searchGroup(searchText.trim())
                            1 -> viewModel.searchUser(searchText.trim())
                            2 -> viewModel.searchBot(searchText.trim())
                        }
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "搜索"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                scrolledContainerColor = MaterialTheme.colorScheme.background
            )
        )
        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            placeholder = { Text("输入ID搜索...") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    if (searchText.isNotBlank()) {
                        when (pagerState.currentPage) {
                            0 -> viewModel.searchGroup(searchText.trim())
                            1 -> viewModel.searchUser(searchText.trim())
                            2 -> viewModel.searchBot(searchText.trim())
                        }
                    }
                }
            ),
            trailingIcon = {
                if (searchText.isNotEmpty()) {
                    IconButton(onClick = {
                        searchText = ""
                        viewModel.clearResults()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "清除"
                        )
                    }
                }
            }
        )
        
        // 标签
        SecondaryTabRow(
            selectedTabIndex = pagerState.currentPage
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = index == pagerState.currentPage,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    text = { Text(title) }
                )
            }
        }
        
        // 错误信息
        uiState.error?.let { error ->
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
                    text = error,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        
        // 内容区域
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> GroupSearchPage(
                    groupResult = uiState.groupResult,
                    isLoading = uiState.isLoading,
                    onGroupClick = { group ->
                        viewModel.showGroupDialog(group)
                    }
                )
                1 -> UserSearchPage(
                    userResult = uiState.userResult,
                    isLoading = uiState.isLoading,
                    onUserClick = { user ->
                        
                        com.yhchat.canary.ui.user.UserDetailActivity.start(
                            context = context,
                            userId = user.userId ?: "",
                            userName = user.nickname ?: ""
                        )
                    }
                )
                2 -> BotSearchPage(
                    botResult = uiState.botResult,
                    isLoading = uiState.isLoading,
                    onBotClick = { bot ->
                        viewModel.showBotDialog(bot)
                    }
                )
            }
        }
    }
    }
    
    // 显示详情弹窗
    if (uiState.showGroupDialog) {
    GroupInfoDialog(
            group = uiState.groupResult,
            onDismiss = { viewModel.hideGroupDialog() },
        onAdd = { group ->
            viewModel.addGroup(group.groupId)
            },
            isAdding = uiState.isAdding
        )
    }
    

    
    if (uiState.showBotDialog) {
        BotInfoDialog(
            bot = uiState.botResult,
            onDismiss = { viewModel.hideBotDialog() },
            onAdd = { bot ->
                viewModel.addBot(bot.botId ?: "")
            },
            isAdding = uiState.isAdding
        )
    }
}

@Composable
private fun GroupSearchPage(
    groupResult: GroupDetail?,
    isLoading: Boolean,
    onGroupClick: (GroupDetail) -> Unit
) {
    when {
        isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        groupResult != null -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) {
                item {
                    GroupResultCard(
                        group = groupResult,
                        onClick = { onGroupClick(groupResult) }
                    )
                }
            }
        }
        else -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "输入群聊ID进行搜索",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun UserSearchPage(
    userResult: UserInfo?,
    isLoading: Boolean,
    onUserClick: (UserInfo) -> Unit
) {
    when {
        isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        userResult != null -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) {
                item {
                    UserResultCard(
                        user = userResult,
                        onClick = { onUserClick(userResult) }
                    )
                }
            }
        }
        else -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "输入用户ID进行搜索",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun BotSearchPage(
    botResult: BotInfo?,
    isLoading: Boolean,
    onBotClick: (BotInfo) -> Unit
) {
    when {
        isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        botResult != null -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) {
                item {
                    BotResultCard(
                        bot = botResult,
                        onClick = { onBotClick(botResult) }
                    )
                }
            }
        }
        else -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "输入机器人ID进行搜索",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
