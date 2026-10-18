@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
package com.yhchat.canary.ui.share

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.yhchat.canary.ui.base.BaseActivity
import com.yhchat.canary.ui.contacts.Contact
import com.yhchat.canary.ui.contacts.ContactsViewModel
import com.yhchat.canary.ui.share.viewmodel.SendToChatViewModel
import com.yhchat.canary.ui.theme.YhchatCanaryTheme
import dagger.hilt.android.AndroidEntryPoint

private const val MAX_TARGETS = 10
private const val SEND_TAB_COUNT = 3
private val SEND_TABS = listOf("好友", "群聊", "机器人")

/**
 * 「选择会话并发送」界面。
 *
 * 文本 / 图片 / 视频 / 附件 / 文章 / 转发消息 都走这个 Activity，
 * 不再用 bottom sheet。调用方式见 [SendToChatActivity.start]。
 */
@AndroidEntryPoint
class SendToChatActivity : BaseActivity() {

    companion object {
        /**
         * 打开「选择会话并发送」界面。
         *
         * ```
         * SendToChatActivity.start(context, SendToChatContent.Text("你好"))
         * SendToChatActivity.start(context, SendToChatContent.images(listOf(uri.toString())))
         * SendToChatActivity.start(context, SendToChatContent.videos(listOf(uri.toString())))
         * SendToChatActivity.start(context, SendToChatContent.files(listOf(uri.toString())))
         * ```
         *
         * @param title 顶栏标题，默认「发送至」；转发场景可以传「转发至」
         */
        fun start(
            context: Context,
            content: SendToChatContent,
            title: String = "发送至"
        ) {
            val intent = Intent(context, SendToChatActivity::class.java).apply {
                putExtra(SendToChatContent.EXTRA_TITLE, title)
                SendToChatContent.writeTo(this, content)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val content = SendToChatContent.fromIntent(intent, contentResolver)
        if (content == null) {
            Toast.makeText(this, "没有可发送的内容", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val title = intent.getStringExtra(SendToChatContent.EXTRA_TITLE) ?: "发送至"

        setContent {
            YhchatCanaryTheme {
                SendToChatScreen(
                    content = content,
                    title = title,
                    onClose = { finish() }
                )
            }
        }
    }
}

@Composable
private fun SendToChatScreen(
    content: SendToChatContent,
    title: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val contactsViewModel: ContactsViewModel = hiltViewModel()
    val sendViewModel: SendToChatViewModel = hiltViewModel()
    val contactsState by contactsViewModel.uiState.collectAsState()
    val sending by sendViewModel.progress.collectAsState()

    val prefs = remember(context) {
        context.getSharedPreferences("send_to_chat_settings", Context.MODE_PRIVATE)
    }

    var searchQuery by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Set<SendToChatTarget>>(emptySet()) }
    var selectedTab by remember {
        mutableIntStateOf(prefs.getInt("selected_tab", 0).coerceIn(0, SEND_TAB_COUNT - 1))
    }

    val pagerState = rememberPagerState(initialPage = selectedTab, pageCount = { SEND_TAB_COUNT })

    LaunchedEffect(selectedTab) {
        if (pagerState.currentPage != selectedTab) {
            pagerState.animateScrollToPage(selectedTab)
        }
    }
    LaunchedEffect(pagerState.currentPage) {
        if (selectedTab != pagerState.currentPage) {
            selectedTab = pagerState.currentPage
        }
    }
    LaunchedEffect(selectedTab) {
        prefs.edit().putInt("selected_tab", selectedTab).apply()
    }

    val onToggle: (SendToChatTarget) -> Unit = { target ->
        selected = if (selected.contains(target)) {
            selected - target
        } else if (selected.size >= MAX_TARGETS) {
            Toast.makeText(context, "最多选择 $MAX_TARGETS 个会话", Toast.LENGTH_SHORT).show()
            selected
        } else {
            selected + target
        }
    }

    val onSend: () -> Unit = {
        sendViewModel.send(content, selected.toList()) { result ->
            result.fold(
                onSuccess = {
                    Toast.makeText(context, "发送成功", Toast.LENGTH_SHORT).show()
                    (context as? Activity)?.finish()
                },
                onFailure = { error ->
                    Toast.makeText(context, error.message ?: "发送失败", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭"
                        )
                    }
                },
                actions = {
                    // 顶栏主操作：发送
                    Button(
                        onClick = onSend,
                        enabled = selected.isNotEmpty() && !sending.isSending,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        if (sending.isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("发送(${selected.size})")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = paddingValues.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding(),
                    start = paddingValues.calculateStartPadding(LocalLayoutDirection.current),
                    end = paddingValues.calculateEndPadding(LocalLayoutDirection.current)
                )
        ) {
            // 发送进度（上传图片 / 附件时带百分比）
            if (sending.isSending) {
                val uploadProgress = sending.progress
                if (uploadProgress != null) {
                    LinearProgressIndicator(
                        progress = { uploadProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Text(
                    text = sending.status ?: "正在发送…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // 正在发送什么
            Text(
                text = content.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            SecondaryTabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth()
            ) {
                SEND_TABS.forEachIndexed { index, tabTitle ->
                    Tab(
                        selected = index == selectedTab,
                        onClick = { selectedTab = index },
                        text = { Text(tabTitle) }
                    )
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("搜索") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp)
                    .height(50.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )

            if (contactsState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f)
                ) { page ->
                    val sourceList: List<Contact> = when (page) {
                        0 -> contactsState.friends
                        1 -> contactsState.groups
                        2 -> contactsState.bots
                        else -> emptyList()
                    }

                    val pageList = remember(sourceList, searchQuery) {
                        if (searchQuery.isBlank()) {
                            sourceList
                        } else {
                            sourceList.filter { it.name.contains(searchQuery, ignoreCase = true) }
                        }
                    }

                    if (pageList.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when {
                                    !contactsState.error.isNullOrBlank() -> contactsState.error ?: "加载失败"
                                    searchQuery.isBlank() -> "暂无可发送的会话"
                                    else -> "没有匹配的会话"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(pageList, key = { item -> "${item.chatType}_${item.chatId}" }) { item ->
                                val target = SendToChatTarget(item.chatId, item.chatType)
                                val isSelected = selected.contains(target)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onToggle(target) }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isSelected) {
                                            Icons.Default.CheckBox
                                        } else {
                                            Icons.Default.CheckBoxOutlineBlank
                                        },
                                        contentDescription = null,
                                        tint = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )

                                    Spacer(modifier = Modifier.width(16.dp))

                                    AsyncImage(
                                        model = item.avatarUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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
