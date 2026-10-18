package com.yhchat.canary.ui.bot

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.yhchat.canary.ui.base.BaseActivity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import com.yhchat.canary.ui.group.GroupInfoActivity
import com.yhchat.canary.ui.bot.viewmodel.BotUsageListViewModel
import com.yhchat.canary.ui.theme.YhchatCanaryTheme
import com.yhchat.canary.ui.user.UserDetailActivity
import kotlinx.coroutines.flow.collectLatest

class BotUsageListActivity : BaseActivity() {
    companion object {
        private const val EXTRA_BOT_ID = "extra_bot_id"
        private const val EXTRA_BOT_NAME = "extra_bot_name"

        fun start(context: Context, botId: String, botName: String) {
            val intent = Intent(context, BotUsageListActivity::class.java).apply {
                putExtra(EXTRA_BOT_ID, botId)
                putExtra(EXTRA_BOT_NAME, botName)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val botId = intent.getStringExtra(EXTRA_BOT_ID).orEmpty()
        val botName = intent.getStringExtra(EXTRA_BOT_NAME).orEmpty()
        if (botId.isBlank()) {
            finish()
            return
        }

        setContent {
            YhchatCanaryTheme {
                BotUsageListRoute(
                    botId = botId,
                    botName = botName.ifBlank { "机器人" },
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BotUsageListRoute(
    botId: String,
    botName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: BotUsageListViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val userListState = rememberLazyListState()
    val groupListState = rememberLazyListState()

    LaunchedEffect(botId) {
        viewModel.attach(context)
        viewModel.start(botId, botName)
    }

    LaunchedEffect(uiState.selectedTab, uiState.users.size, uiState.groups.size) {
        val listState = if (uiState.selectedTab == 0) userListState else groupListState
        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            Triple(lastVisible, total, listState.isScrollInProgress)
        }.collectLatest { (lastVisible, total, scrolling) ->
            if (scrolling && total > 0 && lastVisible >= total - 3) {
                viewModel.loadMore(botId)
            }
        }
    }

    val currentKeyword = uiState.keywordByTab[uiState.selectedTab].orEmpty()
    val currentItems = if (uiState.selectedTab == 0) uiState.users else uiState.groups
    val currentLoadingMore = if (uiState.selectedTab == 0) uiState.loadingMoreUsers else uiState.loadingMoreGroups

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("$botName 使用列表", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SecondaryTabRow(
                selectedTabIndex = uiState.selectedTab,
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf("用户列表(${uiState.userCount})", "群聊列表(${uiState.groupCount})").forEachIndexed { index, title ->
                    Tab(
                        selected = index == uiState.selectedTab,
                        onClick = { viewModel.selectTab(index) },
                        text = { Text(title) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = currentKeyword,
                onValueChange = { viewModel.updateKeyword(botId, it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                singleLine = true,
                placeholder = { Text("搜索") }
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.isLoading && currentItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.error != null && currentItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = uiState.error ?: "加载失败", color = MaterialTheme.colorScheme.error)
                }
            } else {
                LazyColumn(
                    state = if (uiState.selectedTab == 0) userListState else groupListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.selectedTab == 0) {
                        items(uiState.users, key = { it.userId }) { item ->
                            UsageUserItem(
                                name = item.name,
                                userId = item.userId,
                                avatarUrl = item.avatarUrl,
                                isVip = item.isVip,
                                onOpen = {
                                    UserDetailActivity.start(
                                        context = context,
                                        userId = item.userId,
                                        userName = item.name
                                    )
                                },
                                onKick = {
                                    viewModel.requestRemoveUser(item.userId, item.name)
                                }
                            )
                        }
                    } else {
                        items(uiState.groups, key = { it.groupId }) { item ->
                            UsageGroupItem(
                                name = item.name,
                                groupId = item.groupId,
                                avatarUrl = item.avatarUrl,
                                onOpen = {
                                    GroupInfoActivity.start(
                                        context = context,
                                        groupId = item.groupId,
                                        groupName = item.name
                                    )
                                },
                                onKick = {
                                    viewModel.requestRemoveGroup(item.groupId, item.name)
                                }
                            )
                        }
                    }

                    item {
                        if (currentLoadingMore) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    uiState.pendingRemoval?.let { pending ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissRemoval() },
            title = { Text("确认踢出") },
            text = { Text("确定要踢出 ${pending.displayName} 吗？") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmRemoval(botId) }) {
                    Text("踢出")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissRemoval() }) {
                    Text("取消")
                }
            }
        )
    }

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeToast()
        }
    }
}

@Composable
private fun UsageUserItem(
    name: String,
    userId: String,
    avatarUrl: String,
    isVip: Boolean,
    onOpen: () -> Unit,
    onKick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = name,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = userId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isVip) {
                Text(text = "VIP", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "更多")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("踢出") },
                        onClick = {
                            menuExpanded = false
                            onKick()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun UsageGroupItem(
    name: String,
    groupId: String,
    avatarUrl: String,
    onOpen: () -> Unit,
    onKick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = name,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = groupId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(imageVector = Icons.Filled.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(8.dp))
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "更多")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("踢出") },
                        onClick = {
                            menuExpanded = false
                            onKick()
                        }
                    )
                }
            }
        }
    }
}
