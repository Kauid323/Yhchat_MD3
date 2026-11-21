package com.yhchat.canary.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.yhchat.canary.data.model.Conversation
import com.yhchat.canary.ui.chat.ChatActivity
import com.yhchat.canary.ui.components.ConversationPicker
import com.yhchat.canary.ui.components.ImageUtils
import com.yhchat.canary.ui.contacts.ContactsViewModel
import com.yhchat.canary.ui.conversation.ConversationItem
import com.yhchat.canary.ui.group.SearchMessageItem
import com.yhchat.canary.ui.share.SendToChatTarget
import kotlinx.coroutines.delay
import kotlin.math.hypot

/** An in-Activity full-screen layer, revealed from the actual search-button bounds. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConversationSearchOverlay(
    origin: Rect,
    conversations: List<Conversation>,
    onDismiss: () -> Unit,
    onConversationClick: (Conversation) -> Unit,
    viewModel: GlobalChatSearchViewModel = hiltViewModel(),
    contactsViewModel: ContactsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    DisposableEffect(viewModel) {
        onDispose { viewModel.search("", emptySet(), emptySet()) }
    }
    val progress = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var targets by remember { mutableStateOf(emptySet<SendToChatTarget>()) }
    var types by remember { mutableStateOf(emptySet<Int>()) }
    var sheet by remember { mutableIntStateOf(0) } // 1 filters, 2 conversations
    var pickerDraft by remember { mutableStateOf(emptySet<SendToChatTarget>()) }
    val result by viewModel.state.collectAsState()
    val contacts by contactsViewModel.uiState.collectAsState()
    val contactMap = remember(contacts) {
        (contacts.friends + contacts.groups + contacts.bots).associateBy { SendToChatTarget(it.chatId, it.chatType) }
    }
    fun title(target: SendToChatTarget): String = contactMap[target]?.name
        ?: conversations.firstOrNull { it.chatId == target.chatId && it.chatType == target.chatType }?.name
        ?: target.chatId
    fun avatar(target: SendToChatTarget): String = contactMap[target]?.avatarUrl
        ?: conversations.firstOrNull { it.chatId == target.chatId && it.chatType == target.chatType }?.avatarUrl.orEmpty()

    LaunchedEffect(closing) {
        if (closing) focusManager.clearFocus()
        progress.animateTo(if (closing) 0f else 1f, tween(if (closing) 220 else 350))
        if (closing) onDismiss() else focusRequester.requestFocus()
    }
    BackHandler(enabled = sheet == 0) { closing = true }
    LaunchedEffect(query, targets, types, tab) {
        // Cancel stale results immediately, then debounce the new request.
        viewModel.search("", emptySet(), emptySet())
        if (tab == 1 && query.isNotBlank() && targets.isNotEmpty()) {
            delay(400)
            viewModel.search(query, targets, types)
        }
    }
    Surface(Modifier.fillMaxSize().onGloballyPositioned { offset = it.positionInWindow() }
        .drawWithContent {
            val center = origin.center - offset
            val radius = hypot(maxOf(center.x, size.width - center.x), maxOf(center.y, size.height - center.y))
            val start = maxOf(origin.width, origin.height) / 2f
            val r = start + (radius - start) * progress.value
            val path = Path().apply { addOval(Rect(center.x - r, center.y - r, center.x + r, center.y + r)) }
            clipPath(path) { this@drawWithContent.drawContent() }
        }, color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { closing = true }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "关闭搜索") }
                OutlinedTextField(query, { query = it }, Modifier.weight(1f).focusRequester(focusRequester), singleLine = true,
                    placeholder = { Text(if (tab == 0) "搜索会话名称或 ID" else "搜索聊天记录") })
            }
            SecondaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("搜索会话") })
                Tab(selected = tab == 1, onClick = { tab = 1; sheet = 1 }, text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("全局聊天记录")
                        Icon(Icons.Default.KeyboardArrowDown, "筛选", Modifier.size(20.dp))
                    }
                })
            }
            if (tab == 0) {
                val filtered = conversations.filter { query.isBlank() || it.name.contains(query, true) || it.chatId.contains(query, true) }
                LazyColumn(Modifier.weight(1f)) {
                    items(filtered, key = { "${it.chatType}:${it.chatId}" }) { conversation ->
                        ConversationItem(conversation, onClick = { onConversationClick(conversation) }, onLongClick = {})
                    }
                    if (filtered.isEmpty()) item { Text("没有匹配的会话", Modifier.padding(24.dp)) }
                }
            } else {
                TextButton(onClick = { sheet = 1 }) {
                    Text("${targets.size} 个会话 · ${if (types.isEmpty()) "全部类型" else "${types.size} 种类型"} · 修改筛选")
                }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (targets.isEmpty() || query.isBlank()) item {
                        Text(if (targets.isEmpty()) "请选择要搜索的会话" else "输入关键词搜索聊天记录")
                    }
                    itemsIndexed(result.results, key = { _, item -> item.key }) { index, item ->
                        if (index == 0 || result.results[index - 1].target != item.target) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AsyncImage(ImageUtils.createAvatarImageRequest(context, avatar(item.target)), null,
                                    Modifier.size(24.dp).clip(CircleShape))
                                Text(title(item.target), style = MaterialTheme.typography.labelLarge, modifier = Modifier.widthIn(max = 220.dp))
                                HorizontalDivider(Modifier.weight(1f))
                            }
                        }
                        SearchMessageItem(item.message, query) {
                            ChatActivity.startWithSearchTarget(context, item.target.chatId, item.target.chatType,
                                title(item.target), item.message.msgId, item.message.msgSeq)
                        }
                    }
                    if (result.error != null) item {
                        Column {
                            Text(result.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = viewModel::loadMore) { Text("重试") }
                        }
                    } else if (result.hasMore || result.loading) item {
                        if (result.loading) CircularProgressIndicator(Modifier.size(24.dp))
                        else LaunchedEffect(result.results.size) { viewModel.loadMore() }
                    } else if (query.isNotBlank() && targets.isNotEmpty()) item {
                        Text(if (result.results.isEmpty()) "未找到相关消息" else "已加载全部结果")
                    }
                }
            }
        }
    }
    if (sheet != 0) {
        ModalBottomSheet(onDismissRequest = { sheet = 0 }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            if (sheet == 2) {
                Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { sheet = 1 }) { Text("返回筛选") }
                        TextButton(onClick = { targets = pickerDraft; sheet = 1 }) { Text("确定 (${pickerDraft.size})") }
                    }
                    ConversationPicker(pickerDraft, { pickerDraft = it }, Modifier.weight(1f), viewModel = contactsViewModel)
                }
            } else {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("聊天记录筛选", style = MaterialTheme.typography.titleLarge)
                    FilledTonalButton(onClick = { pickerDraft = targets; sheet = 2 }) { Text("选择会话（${targets.size}）") }
                    Text("消息类型")
                    val choices = listOf(0 to "全部", 1 to "文本", 2 to "图片", 3 to "Markdown", 4 to "文件", 5 to "表单", 6 to "文章", 7 to "表情", 8 to "HTML", 11 to "语音", 13 to "语音通话")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        choices.forEach { (value, label) ->
                            FilterChip(selected = if (value == 0) types.isEmpty() else value in types,
                                onClick = { types = when { value == 0 -> emptySet(); value in types -> types - value; else -> types + value } },
                                label = { Text(label) })
                        }
                    }
                    Button(onClick = { sheet = 0 }, Modifier.fillMaxWidth()) { Text("完成") }
                }
            }
        }
    }
}
