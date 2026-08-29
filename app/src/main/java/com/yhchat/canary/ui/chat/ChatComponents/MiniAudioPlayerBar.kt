package com.yhchat.canary.ui.chat.ChatComponents

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.provider.MediaStore
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import coil.compose.AsyncImage
import com.yhchat.canary.data.local.AppDatabase
import com.yhchat.canary.data.local.AudioPlayMode
import com.yhchat.canary.data.local.AudioPlaylist
import com.yhchat.canary.data.local.AudioPlaylistItem
import com.yhchat.canary.ncm.NcmApiClient
import com.yhchat.canary.ncm.NcmSong
import com.yhchat.canary.service.AudioPlayerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

// ─────────────────────────────────────────────────────────────────────────────
// 工具函数
// ─────────────────────────────────────────────────────────────────────────────

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}

// ─────────────────────────────────────────────────────────────────────────────
// 获取或创建"当前播放"自动队列
// ─────────────────────────────────────────────────────────────────────────────

suspend fun getOrCreateAutoQueue(context: Context): AudioPlaylist {
    val dao = AppDatabase.getDatabase(context).audioPlaylistDao()
    return dao.getAutoQueue() ?: run {
        val playlist = AudioPlaylist(
            id = "auto_queue",
            name = "当前播放",
            isAutoQueue = true
        )
        dao.insertPlaylist(playlist)
        playlist
    }
}

/** 将一首音频追加到"当前播放"队列（URL 相同则跳过） */
suspend fun appendToAutoQueue(context: Context, title: String, url: String, source: String = "CHAT") {
    val dao = AppDatabase.getDatabase(context).audioPlaylistDao()
    val playlist = getOrCreateAutoQueue(context)
    if (dao.findItemByUrl(playlist.id, url) != null) return // 已存在则跳过
    val maxOrder = dao.getMaxSortOrder(playlist.id) ?: -1
    dao.insertItem(
        AudioPlaylistItem(
            id = UUID.randomUUID().toString(),
            playlistId = playlist.id,
            title = title,
            url = url,
            source = source,
            sortOrder = maxOrder + 1
        )
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// 主组件：MiniAudioPlayerBar（聊天界面顶栏下方的微型条）
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MiniAudioPlayerBar(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var mediaController by remember { mutableStateOf<MediaControllerCompat?>(null) }
    var realIsPlaying by remember { mutableStateOf(false) }
    var optimisticIsPlaying by remember { mutableStateOf(false) }

    var title by remember { mutableStateOf("") }
    var coverUrl by remember { mutableStateOf<String?>(null) }
    var progressMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var hasEverPlayed by remember { mutableStateOf(false) }

    var debounceJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // 绑定 AudioPlayerService
    DisposableEffect(Unit) {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as? AudioPlayerService.AudioPlayerBinder ?: return
                val token = binder.getService().getSessionToken()
                val controller = MediaControllerCompat(context, token)
                mediaController = controller

                val meta = controller.metadata
                val state = controller.playbackState

                realIsPlaying = state?.state == PlaybackStateCompat.STATE_PLAYING
                optimisticIsPlaying = realIsPlaying
                title = meta?.getString(MediaMetadataCompat.METADATA_KEY_TITLE) ?: ""
                coverUrl = meta?.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI)
                    ?: meta?.getString(MediaMetadataCompat.METADATA_KEY_ART_URI)
                progressMs = state?.position ?: 0L
                durationMs = meta?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) ?: 0L
                if (realIsPlaying || title.isNotEmpty()) hasEverPlayed = true

                controller.registerCallback(object : MediaControllerCompat.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
                        realIsPlaying = state?.state == PlaybackStateCompat.STATE_PLAYING
                        optimisticIsPlaying = realIsPlaying
                        progressMs = state?.position ?: 0L
                        durationMs = mediaController?.metadata
                            ?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) ?: durationMs
                        if (realIsPlaying) hasEverPlayed = true
                    }
                    override fun onMetadataChanged(meta: MediaMetadataCompat?) {
                        val newTitle = meta?.getString(MediaMetadataCompat.METADATA_KEY_TITLE) ?: ""
                        if (newTitle.isNotEmpty()) {
                            title = newTitle
                            hasEverPlayed = true
                        }
                        coverUrl = meta?.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI)
                            ?: meta?.getString(MediaMetadataCompat.METADATA_KEY_ART_URI)
                        durationMs = meta?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) ?: durationMs
                    }
                })
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                mediaController = null
                realIsPlaying = false
                optimisticIsPlaying = false
                hasEverPlayed = false
            }
        }
        context.bindService(
            Intent(context, AudioPlayerService::class.java),
            connection,
            Context.BIND_AUTO_CREATE
        )
        onDispose { runCatching { context.unbindService(connection) } }
    }

    // 轮询播放进度
    LaunchedEffect(mediaController, realIsPlaying) {
        while (isActive && realIsPlaying && mediaController != null) {
            val state = mediaController?.playbackState
            if (state != null) {
                val timeDelta = android.os.SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
                val cur = if (state.state == PlaybackStateCompat.STATE_PLAYING)
                    (state.position + (timeDelta * state.playbackSpeed)).toLong()
                else state.position
                progressMs = cur.coerceAtLeast(0L)
            }
            delay(500)
        }
    }

    val isVisible = hasEverPlayed && title.isNotEmpty()
    val displayIsPlaying = optimisticIsPlaying

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut()
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showBottomSheet = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 封面或播放/暂停按钮
                if (!coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(Modifier.width(10.dp))
                }

                // 播放/暂停
                FilledIconButton(
                    onClick = {
                        val newState = !displayIsPlaying
                        optimisticIsPlaying = newState
                        debounceJob?.cancel()
                        debounceJob = coroutineScope.launch {
                            delay(300)
                            if (newState) mediaController?.transportControls?.play()
                            else mediaController?.transportControls?.pause()
                        }
                    },
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        imageVector = if (displayIsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (displayIsPlaying) "暂停" else "播放",
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${formatTime(progressMs)} / ${formatTime(durationMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        AudioPlayerService.stopPlayAudio(context)
                        title = ""
                        coverUrl = null
                        progressMs = 0L
                        durationMs = 0L
                        hasEverPlayed = false
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "关闭",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // ── BottomSheet ────────────────────────────────────────────────────────
    if (showBottomSheet) {
        AudioPlayerBottomSheet(
            title = title,
            coverUrl = coverUrl,
            progressMs = progressMs,
            durationMs = durationMs,
            isPlaying = displayIsPlaying,
            mediaController = mediaController,
            onProgressChange = { newMs -> progressMs = newMs },
            onTogglePlayPause = {
                val newState = !displayIsPlaying
                optimisticIsPlaying = newState
                debounceJob?.cancel()
                debounceJob = coroutineScope.launch {
                    delay(300)
                    if (newState) mediaController?.transportControls?.play()
                    else mediaController?.transportControls?.pause()
                }
            },
            onPlayUrl = { url, itemTitle ->
                coroutineScope.launch {
                    appendToAutoQueue(context, itemTitle, url)
                }
                AudioPlayerService.startPlayAudio(context, url, itemTitle)
            },
            onClosePlayer = {
                AudioPlayerService.stopPlayAudio(context)
                title = ""
                coverUrl = null
                progressMs = 0L
                durationMs = 0L
                hasEverPlayed = false
                showBottomSheet = false
            },
            onDismiss = { showBottomSheet = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// BottomSheet：分层结构（上层 Tab + 搜索，底层可滑动列表，前景浮动播放器）
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun AudioPlayerBottomSheet(
    title: String,
    coverUrl: String?,
    progressMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    mediaController: MediaControllerCompat?,
    onProgressChange: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onPlayUrl: (url: String, title: String) -> Unit,
    onClosePlayer: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val dao = remember { AppDatabase.getDatabase(context).audioPlaylistDao() }

    var selectedTab by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var isDragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }
    var playMode by remember { mutableStateOf(AudioPlayerService.getPlayMode(context)) }

    // 播放列表数据
    val allPlaylists by dao.getAllPlaylists().collectAsState(initial = emptyList())
    var currentPlaylistItems by remember { mutableStateOf<List<AudioPlaylistItem>>(emptyList()) }
    var selectedPlaylistForView by remember { mutableStateOf<AudioPlaylist?>(null) }

    // 本地已保存录音列表
    var localAudios by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) } // title to uri

    // 本地/队列搜索结果
    var searchResults by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    // 网易云音乐搜索状态
    var ncmSearchResults by remember { mutableStateOf<List<NcmSong>>(emptyList()) }
    var isNcmSearching by remember { mutableStateOf(false) }
    var ncmPlayingId by remember { mutableStateOf<Long?>(null) }

    // 对话框状态
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf<Pair<String, String>?>(null) } // url to title
    var newPlaylistName by remember { mutableStateOf("") }
    var showRenameDialog by remember { mutableStateOf<AudioPlaylist?>(null) }
    var renameText by remember { mutableStateOf("") }

    // 加载本地录音
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val results = mutableListOf<Pair<String, String>>()
            try {
                val resolver = context.contentResolver
                val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
                    android.net.Uri.parse("content://media/external/downloads")
                else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

                val projection = arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME
                )
                val selection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
                    "${MediaStore.MediaColumns.RELATIVE_PATH} = ?" else null
                val selectionArgs = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
                    arrayOf("Download/yhchat/audio/") else null

                resolver.query(uri, projection, selection, selectionArgs, "${MediaStore.MediaColumns.DISPLAY_NAME} ASC")
                    ?.use { cursor ->
                        val idIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                        val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(idIdx)
                            val name = cursor.getString(nameIdx) ?: continue
                            val contentUri = android.content.ContentUris.withAppendedId(uri, id).toString()
                            results.add(name.substringBeforeLast('.', name) to contentUri)
                        }
                    }
            } catch (_: Exception) {}
            localAudios = results
        }
    }

    // 加载当前选中播放列表的项
    LaunchedEffect(selectedPlaylistForView, allPlaylists) {
        val playlist = selectedPlaylistForView ?: allPlaylists.firstOrNull { it.isAutoQueue }
        selectedPlaylistForView = playlist
        if (playlist != null) {
            currentPlaylistItems = dao.getItemsForPlaylistSync(playlist.id)
            AudioPlayerService.setActivePlaylistId(context, playlist.id)
        }
    }

    // 搜索（支持本地、当前队列和网易云在线搜索）
    LaunchedEffect(searchQuery, selectedTab, currentPlaylistItems, localAudios) {
        if (searchQuery.isBlank()) {
            searchResults = emptyList()
            ncmSearchResults = emptyList()
            isNcmSearching = false
            return@LaunchedEffect
        }
        val q = searchQuery.trim()
        when (selectedTab) {
            0 -> {
                searchResults = currentPlaylistItems
                    .filter { it.title.lowercase().contains(q.lowercase()) }
                    .map { it.title to it.url }
            }
            1 -> {
                isNcmSearching = true
                delay(350) // 防抖
                val res = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    NcmApiClient.searchSongs(q).getOrDefault(emptyList())
                }
                ncmSearchResults = res
                isNcmSearching = false
            }
            2 -> {
                searchResults = localAudios.filter { it.first.lowercase().contains(q.lowercase()) }
            }
            else -> {
                searchResults = emptyList()
            }
        }
    }

    // 切歌函数（支持顺序、单曲循环、列表循环、随机播放）
    val currentIndex = currentPlaylistItems.indexOfFirst { it.title == title }
    fun playNext() {
        if (currentPlaylistItems.isEmpty()) return
        val nextIdx = when (playMode) {
            AudioPlayMode.SHUFFLE -> {
                if (currentPlaylistItems.size == 1) 0
                else {
                    var rand = currentPlaylistItems.indices.random()
                    if (rand == currentIndex && currentPlaylistItems.size > 1) {
                        rand = (rand + 1) % currentPlaylistItems.size
                    }
                    rand
                }
            }
            AudioPlayMode.SINGLE_LOOP -> if (currentIndex in currentPlaylistItems.indices) currentIndex else 0
            AudioPlayMode.LIST_LOOP -> if (currentIndex >= 0) (currentIndex + 1) % currentPlaylistItems.size else 0
            AudioPlayMode.SEQUENCE -> if (currentIndex in 0 until currentPlaylistItems.size - 1) currentIndex + 1 else 0
        }
        val nextItem = currentPlaylistItems[nextIdx]
        onPlayUrl(nextItem.url, nextItem.title)
    }

    fun playPrev() {
        if (currentPlaylistItems.isEmpty()) return
        val prevIdx = when (playMode) {
            AudioPlayMode.SHUFFLE -> {
                if (currentPlaylistItems.size == 1) 0
                else {
                    var rand = currentPlaylistItems.indices.random()
                    if (rand == currentIndex && currentPlaylistItems.size > 1) {
                        rand = (rand + 1) % currentPlaylistItems.size
                    }
                    rand
                }
            }
            AudioPlayMode.SINGLE_LOOP -> if (currentIndex in currentPlaylistItems.indices) currentIndex else 0
            AudioPlayMode.LIST_LOOP -> if (currentIndex > 0) currentIndex - 1 else currentPlaylistItems.size - 1
            AudioPlayMode.SEQUENCE -> if (currentIndex > 0) currentIndex - 1 else currentPlaylistItems.size - 1
        }
        val prevItem = currentPlaylistItems[prevIdx]
        onPlayUrl(prevItem.url, prevItem.title)
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var sheetHeightPx by remember { mutableIntStateOf(0) }
    var cardHeightPx by remember { mutableIntStateOf(0) }
    var minSheetOffset by remember { mutableFloatStateOf(Float.MAX_VALUE) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .onGloballyPositioned { coordinates ->
                    sheetHeightPx = coordinates.size.height
                }
        ) {
            // ── 顶部：Tab 栏 ──────────────────────────────────────────────
            val tabs = listOf("当前播放", "网易云", "本地录音", "我的列表")
            SecondaryTabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                containerColor = Color.Transparent,
                divider = {}
            ) {
                tabs.forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index; searchQuery = "" },
                        text = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // 搜索框（Tab 0、1、2）
            if (selectedTab in 0..2) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = if (selectedTab == 1) "搜索网易云音乐 (歌曲/歌手)..." else "搜索音频名称...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "清除")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.3f),
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
            } else {
                Spacer(Modifier.height(4.dp))
            }

            // ── 主体区域：底层可滑动列表 + 前景悬浮播放器 ────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // 【底层 / 背景】：可自由滑动的音频列表（contentPadding 预留底部播放器高度）
                when (selectedTab) {
                    // Tab 0: 当前播放队列
                    0 -> {
                        val items = if (searchQuery.isNotEmpty()) searchResults
                        else currentPlaylistItems.map { it.title to it.url }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 220.dp, start = 16.dp, end = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(
                                items = items,
                                key = { it.second }
                            ) { (itemTitle, url) ->
                                PlaylistItemCard(
                                    title = itemTitle,
                                    subtitle = if (url.startsWith("content://") || url.startsWith("file://")) "本地音频" else "网络音频",
                                    isCurrentlyPlaying = itemTitle == title,
                                    onClick = { onPlayUrl(url, itemTitle) },
                                    onLongClick = { showAddToPlaylistDialog = url to itemTitle }
                                )
                            }
                            if (items.isEmpty()) {
                                item {
                                    EmptyStateView(text = if (searchQuery.isNotEmpty()) "未找到匹配音频" else "暂无播放记录")
                                }
                            }
                        }
                    }

                    // Tab 1: 网易云音乐搜索与播放
                    1 -> {
                        if (isNcmSearching) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(36.dp))
                            }
                        } else if (searchQuery.isBlank()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = 40.dp, bottom = 220.dp, start = 24.dp, end = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.CloudQueue,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = "网易云音乐搜索",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "在上方搜索框输入歌曲名或歌手名，即可在线解析并播放高品质网易云音乐",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(top = 8.dp, bottom = 220.dp, start = 16.dp, end = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(items = ncmSearchResults, key = { it.id }) { ncmSong ->
                                    val isThisPlaying = ncmSong.name == title || "${ncmSong.name} - ${ncmSong.artist}" == title
                                    val isLoadingThis = ncmPlayingId == ncmSong.id
                                    NcmSongItemCard(
                                        song = ncmSong,
                                        isCurrentlyPlaying = isThisPlaying,
                                        isLoading = isLoadingThis,
                                        onClick = {
                                            coroutineScope.launch {
                                                ncmPlayingId = ncmSong.id
                                                val playUrl = NcmApiClient.getSongPlayUrl(ncmSong.id).getOrNull()
                                                ncmPlayingId = null
                                                if (!playUrl.isNullOrBlank()) {
                                                    val songTitle = "${ncmSong.name} - ${ncmSong.artist}"
                                                    appendToAutoQueue(context, songTitle, playUrl, "NCM")
                                                    onPlayUrl(playUrl, songTitle)
                                                } else {
                                                    Toast.makeText(context, "解析网易云音频失败", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        onAddToPlaylist = {
                                            coroutineScope.launch {
                                                val playUrl = NcmApiClient.getSongPlayUrl(ncmSong.id).getOrNull() ?: ""
                                                showAddToPlaylistDialog = playUrl to "${ncmSong.name} - ${ncmSong.artist}"
                                            }
                                        }
                                    )
                                }
                                if (ncmSearchResults.isEmpty()) {
                                    item {
                                        EmptyStateView(text = "未找到相关网易云歌曲")
                                    }
                                }
                            }
                        }
                    }

                    // Tab 2: 本地录音
                    2 -> {
                        val items = if (searchQuery.isNotEmpty()) searchResults else localAudios
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 220.dp, start = 16.dp, end = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(items = items, key = { it.second }) { (itemTitle, uri) ->
                                PlaylistItemCard(
                                    title = itemTitle,
                                    subtitle = "本地录音",
                                    isCurrentlyPlaying = itemTitle == title,
                                    onClick = {
                                        coroutineScope.launch { appendToAutoQueue(context, itemTitle, uri, "LOCAL") }
                                        AudioPlayerService.startPlaySavedAudio(context, uri, itemTitle)
                                    },
                                    onLongClick = { showAddToPlaylistDialog = uri to itemTitle }
                                )
                            }
                            if (items.isEmpty()) {
                                item {
                                    EmptyStateView(text = if (searchQuery.isNotEmpty()) "未找到匹配录音" else "暂无已保存的本地录音")
                                }
                            }
                        }
                    }

                    // Tab 3: 我的播放列表
                    3 -> {
                        val userPlaylists = allPlaylists.filter { !it.isAutoQueue }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 220.dp, start = 16.dp, end = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            item {
                                OutlinedButton(
                                    onClick = { showCreatePlaylistDialog = true },
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("新建播放列表", fontWeight = FontWeight.Medium)
                                }
                            }

                            items(userPlaylists, key = { it.id }) { playlist ->
                                PlaylistFolderCard(
                                    playlist = playlist,
                                    isSelected = selectedPlaylistForView?.id == playlist.id,
                                    onClick = {
                                        selectedPlaylistForView = playlist
                                        selectedTab = 0 // 切到列表查看
                                    },
                                    onRename = {
                                        renameText = playlist.name
                                        showRenameDialog = playlist
                                    },
                                    onDelete = {
                                        coroutineScope.launch { dao.deletePlaylist(playlist.id) }
                                    }
                                )
                            }
                        }
                    }
                }

                // 【顶层 / 前景】：悬浮在列表最下方的播放器控制面板（浮动卡片）
                // 滑动 BottomSheet 时卡片保持吸附在底部，直到拖拽手柄接触卡片时随之一同收回
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            cardHeightPx = coordinates.size.height
                        }
                        .graphicsLayer {
                            val curOffset = runCatching { sheetState.requireOffset() }.getOrNull() ?: 0f
                            if (curOffset > 0f && curOffset < minSheetOffset) {
                                minSheetOffset = curOffset
                            }
                            val y0 = if (minSheetOffset != Float.MAX_VALUE) minSheetOffset else curOffset
                            val delta = (curOffset - y0).coerceAtLeast(0f)
                            val maxShift = (sheetHeightPx - cardHeightPx).coerceAtLeast(0).toFloat()
                            val shift = delta.coerceIn(0f, maxShift)
                            translationY = -shift
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 顶部行：音频唱片/封面图标 + 标题 + 关闭按钮
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (!coverUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = coverUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.GraphicEq else Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (title.isNotEmpty()) title else "未在播放",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${if (isPlaying) "正在播放" else "已暂停"} · ${playMode.title}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = onClosePlayer,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "停止播放",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // 进度条 Slider
                        Slider(
                            value = if (durationMs > 0 && !isDragging)
                                (progressMs.toFloat() / durationMs).coerceIn(0f, 1f)
                            else if (isDragging) dragValue else 0f,
                            onValueChange = { v ->
                                isDragging = true
                                dragValue = v
                                onProgressChange((v * durationMs).toLong())
                            },
                            onValueChangeFinished = {
                                mediaController?.transportControls?.seekTo((dragValue * durationMs).toLong())
                                isDragging = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        // 进度时间戳
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatTime(progressMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatTime(durationMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(Modifier.height(4.dp))

                        // 控制按钮行（播放模式切换、上一首、播放/暂停、下一首、快进10秒）
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 播放模式切换按钮（顺序播放 -> 列表循环 -> 单曲循环 -> 随机播放）
                            IconButton(
                                onClick = {
                                    val next = playMode.next()
                                    playMode = next
                                    AudioPlayerService.setPlayMode(context, next)
                                    Toast.makeText(context, next.title, Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(42.dp)
                            ) {
                                Icon(
                                    imageVector = when (playMode) {
                                        AudioPlayMode.SEQUENCE -> Icons.Default.FormatListNumbered
                                        AudioPlayMode.LIST_LOOP -> Icons.Default.Repeat
                                        AudioPlayMode.SINGLE_LOOP -> Icons.Default.RepeatOne
                                        AudioPlayMode.SHUFFLE -> Icons.Default.Shuffle
                                    },
                                    contentDescription = playMode.title,
                                    tint = if (playMode != AudioPlayMode.SEQUENCE) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            IconButton(
                                onClick = { playPrev() },
                                modifier = Modifier.size(42.dp)
                            ) {
                                Icon(
                                    Icons.Default.SkipPrevious,
                                    contentDescription = "上一首",
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            FilledIconButton(
                                onClick = onTogglePlayPause,
                                modifier = Modifier.size(54.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "暂停" else "播放",
                                    modifier = Modifier.size(30.dp)
                                )
                            }

                            IconButton(
                                onClick = { playNext() },
                                modifier = Modifier.size(42.dp)
                            ) {
                                Icon(
                                    Icons.Default.SkipNext,
                                    contentDescription = "下一首",
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    val target = (progressMs + 10000L).coerceAtMost(durationMs)
                                    mediaController?.transportControls?.seekTo(target)
                                    onProgressChange(target)
                                },
                                modifier = Modifier.size(42.dp)
                            ) {
                                Icon(
                                    Icons.Default.FastForward,
                                    contentDescription = "快进10秒",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── 对话框：新建播放列表 ─────────────────────────────────────────────
    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false; newPlaylistName = "" },
            title = { Text("新建播放列表") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("列表名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            val playlist = AudioPlaylist(
                                id = UUID.randomUUID().toString(),
                                name = newPlaylistName.trim()
                            )
                            coroutineScope.launch { dao.insertPlaylist(playlist) }
                        }
                        showCreatePlaylistDialog = false
                        newPlaylistName = ""
                    }
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false; newPlaylistName = "" }) {
                    Text("取消")
                }
            }
        )
    }

    // ── 对话框：重命名播放列表 ──────────────────────────────────────────
    showRenameDialog?.let { playlist ->
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("重命名播放列表") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (renameText.isNotBlank()) {
                        coroutineScope.launch { dao.renamePlaylist(playlist.id, renameText.trim()) }
                    }
                    showRenameDialog = null
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) { Text("取消") }
            }
        )
    }

    // ── 对话框：加入指定播放列表 ─────────────────────────────────────────
    showAddToPlaylistDialog?.let { (url, itemTitle) ->
        AlertDialog(
            onDismissRequest = { showAddToPlaylistDialog = null },
            title = { Text("加入播放列表") },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(allPlaylists, key = { it.id }) { playlist ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch {
                                        val maxOrder = dao.getMaxSortOrder(playlist.id) ?: -1
                                        dao.insertItem(
                                            AudioPlaylistItem(
                                                id = UUID.randomUUID().toString(),
                                                playlistId = playlist.id,
                                                title = itemTitle,
                                                url = url,
                                                source = "CHAT",
                                                sortOrder = maxOrder + 1
                                            )
                                        )
                                    }
                                    showAddToPlaylistDialog = null
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            color = Color.Transparent
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.QueueMusic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Text(playlist.name, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                    item {
                        OutlinedButton(
                            onClick = { showCreatePlaylistDialog = true; showAddToPlaylistDialog = null },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("新建列表")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddToPlaylistDialog = null }) { Text("取消") }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 子组件：音频项卡片 / 播放列表文件夹卡片 / 空状态
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistItemCard(
    title: String,
    subtitle: String,
    isCurrentlyPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isCurrentlyPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = if (isCurrentlyPlaying) 4.dp else 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = if (isCurrentlyPlaying) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isCurrentlyPlaying) Icons.Default.GraphicEq else Icons.Default.Audiotrack,
                        contentDescription = null,
                        tint = if (isCurrentlyPlaying) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isCurrentlyPlaying) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isCurrentlyPlaying) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isCurrentlyPlaying) {
                Spacer(Modifier.width(8.dp))
                SuggestionChip(
                    onClick = onClick,
                    label = { Text("播放中", fontSize = 10.sp) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        labelColor = MaterialTheme.colorScheme.primary
                    ),
                    border = null
                )
            }
        }
    }
}

@Composable
private fun PlaylistFolderCard(
    playlist: AudioPlaylist,
    isSelected: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "点击查看列表内容",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "更多",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        onClick = { showMenu = false; onRename() },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                        onClick = { showMenu = false; onDelete() },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyStateView(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.MusicOff,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.outlineVariant
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NcmSongItemCard(
    song: NcmSong,
    isCurrentlyPlaying: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isCurrentlyPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = if (isCurrentlyPlaying) 4.dp else 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 封面图
            if (song.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = song.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isCurrentlyPlaying) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isCurrentlyPlaying) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.displaySubtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(24.dp)
                        .padding(2.dp),
                    strokeWidth = 2.dp
                )
            } else if (isCurrentlyPlaying) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                IconButton(
                    onClick = onAddToPlaylist,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.PlaylistAdd,
                        contentDescription = "添加到播放列表",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}


