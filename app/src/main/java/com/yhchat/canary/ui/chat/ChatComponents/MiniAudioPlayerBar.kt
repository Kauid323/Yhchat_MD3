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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yhchat.canary.data.local.AppDatabase
import com.yhchat.canary.data.local.AudioPlaylist
import com.yhchat.canary.data.local.AudioPlaylistItem
import com.yhchat.canary.service.AudioPlayerService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
// 主组件：MiniAudioPlayerBar
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniAudioPlayerBar(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var mediaController by remember { mutableStateOf<MediaControllerCompat?>(null) }
    // 真实状态（来自 MediaController 回调）
    var realIsPlaying by remember { mutableStateOf(false) }
    // 乐观更新状态（点击后立刻翻转，等服务回调校正）
    var optimisticIsPlaying by remember { mutableStateOf(false) }

    var title by remember { mutableStateOf("") }
    var progressMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var showBottomSheet by remember { mutableStateOf(false) }
    // 防止服务未就绪时 isVisible 闪烁出现
    var hasEverPlayed by remember { mutableStateOf(false) }

    // 防抖 Job
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

    // 轮询进度（只在播放时跑，Slider 拖动期间跳过更新由 isDragging 控制）
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

    // 只有曾经播放过、且当前媒体仍有标题，才显示
    val isVisible = hasEverPlayed && title.isNotEmpty()
    val displayIsPlaying = optimisticIsPlaying

    if (isVisible) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { showBottomSheet = true }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 播放/暂停（乐观更新 + 防抖 300ms）
            IconButton(
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
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (displayIsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (displayIsPlaying) "暂停" else "播放"
                )
            }

            Spacer(Modifier.width(8.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.width(8.dp))

            Text(
                text = "${formatTime(progressMs)}/${formatTime(durationMs)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.width(4.dp))

            IconButton(
                onClick = {
                    AudioPlayerService.stopPlayAudio(context)
                    title = ""
                    progressMs = 0L
                    durationMs = 0L
                    hasEverPlayed = false
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "关闭")
            }
        }
    }

    // ── BottomSheet ────────────────────────────────────────────────────────
    if (showBottomSheet) {
        AudioPlayerBottomSheet(
            title = title,
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
            onDismiss = { showBottomSheet = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// BottomSheet：播放详情 + 播放列表
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun AudioPlayerBottomSheet(
    title: String,
    progressMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    mediaController: MediaControllerCompat?,
    onProgressChange: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onPlayUrl: (url: String, title: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val dao = remember { AppDatabase.getDatabase(context).audioPlaylistDao() }

    var selectedTab by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var isDragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }

    // 播放列表数据
    val allPlaylists by dao.getAllPlaylists().collectAsState(initial = emptyList())
    var currentPlaylistItems by remember { mutableStateOf<List<AudioPlaylistItem>>(emptyList()) }
    var selectedPlaylistForView by remember { mutableStateOf<AudioPlaylist?>(null) }

    // 本地已保存录音列表
    var localAudios by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) } // title to uri

    // 搜索结果
    var searchResults by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

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
        }
    }

    // 搜索
    LaunchedEffect(searchQuery, selectedTab, currentPlaylistItems, localAudios) {
        if (searchQuery.isBlank()) {
            searchResults = emptyList()
            return@LaunchedEffect
        }
        val q = searchQuery.lowercase()
        searchResults = when (selectedTab) {
            0 -> currentPlaylistItems
                .filter { it.title.lowercase().contains(q) }
                .map { it.title to it.url }
            1 -> localAudios.filter { it.first.lowercase().contains(q) }
            else -> emptyList()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            // ── 顶部：当前播放控制 ──────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(12.dp))

                // Slider（拖动时不被轮询覆盖）
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatTime(progressMs), style = MaterialTheme.typography.labelSmall)
                    Text(formatTime(durationMs), style = MaterialTheme.typography.labelSmall)
                }

                Spacer(Modifier.height(8.dp))

                // 控制按钮行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { mediaController?.transportControls?.skipToPrevious() }) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "上一首", modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    FilledIconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "暂停" else "播放",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    IconButton(onClick = { mediaController?.transportControls?.skipToNext() }) {
                        Icon(Icons.Default.SkipNext, contentDescription = "下一首", modifier = Modifier.size(32.dp))
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── Tab：当前播放 / 本地录音 / 我的播放列表 ────────────────────
            val tabs = listOf("当前播放", "本地录音", "我的列表")
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index; searchQuery = "" },
                        text = { Text(label, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }

            // 搜索框（Tab 0 和 1 可用）
            if (selectedTab in 0..1) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("搜索") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "清除")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // ── Tab 内容 ──────────────────────────────────────────────────
            when (selectedTab) {
                // Tab 0: 当前播放队列
                0 -> {
                    val items = if (searchQuery.isNotEmpty()) searchResults else
                        currentPlaylistItems.map { it.title to it.url }
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(
                            items = if (searchQuery.isNotEmpty()) searchResults
                            else currentPlaylistItems.map { it.title to it.url },
                            key = { it.second }
                        ) { (itemTitle, url) ->
                            PlaylistItemRow(
                                title = itemTitle,
                                isCurrentlyPlaying = itemTitle == title,
                                onClick = { onPlayUrl(url, itemTitle) },
                                onLongClick = { showAddToPlaylistDialog = url to itemTitle }
                            )
                        }
                        if (items.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("暂无音频", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                // Tab 1: 本地录音
                1 -> {
                    val items = if (searchQuery.isNotEmpty()) searchResults else localAudios
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(items = items, key = { it.second }) { (itemTitle, uri) ->
                            PlaylistItemRow(
                                title = itemTitle,
                                isCurrentlyPlaying = false,
                                onClick = {
                                    coroutineScope.launch { appendToAutoQueue(context, itemTitle, uri, "LOCAL") }
                                    AudioPlayerService.startPlaySavedAudio(context, uri, itemTitle)
                                },
                                onLongClick = { showAddToPlaylistDialog = uri to itemTitle }
                            )
                        }
                        if (items.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("暂无本地录音", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                // Tab 2: 我的播放列表
                2 -> {
                    val userPlaylists = allPlaylists.filter { !it.isAutoQueue }
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(userPlaylists, key = { it.id }) { playlist ->
                            PlaylistRow(
                                playlist = playlist,
                                onClick = {
                                    selectedPlaylistForView = playlist
                                    selectedTab = 0 // 切到当前播放 tab 显示内容
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
                        item {
                            TextButton(
                                onClick = { showCreatePlaylistDialog = true },
                                modifier = Modifier.fillMaxWidth().padding(16.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("新建播放列表")
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
                TextButton(
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
            title = { Text("重命名") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
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
                LazyColumn {
                    items(allPlaylists, key = { it.id }) { playlist ->
                        TextButton(
                            onClick = {
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
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(playlist.name) }
                    }
                    item {
                        TextButton(
                            onClick = { showCreatePlaylistDialog = true; showAddToPlaylistDialog = null },
                            modifier = Modifier.fillMaxWidth()
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
// 子组件
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistItemRow(
    title: String,
    isCurrentlyPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isCurrentlyPlaying) Icons.Default.GraphicEq else Icons.Default.AudioFile,
            contentDescription = null,
            tint = if (isCurrentlyPlaying) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isCurrentlyPlaying) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PlaylistRow(
    playlist: AudioPlaylist,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.QueueMusic, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text = playlist.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
        Box {
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.MoreVert, contentDescription = "更多")
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
                        Icon(Icons.Default.Delete, contentDescription = null,
                            tint = MaterialTheme.colorScheme.error)
                    }
                )
            }
        }
    }
}


