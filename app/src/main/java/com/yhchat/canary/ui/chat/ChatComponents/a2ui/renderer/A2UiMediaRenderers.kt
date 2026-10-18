package com.yhchat.canary.ui.chat.ChatComponents.a2ui.renderer

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

// A2UI AudioPlayer Component
@Composable
internal fun A2UiAudioPlayer(
    playerId: String,
    url: String,
    description: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    var isPlaying by remember(playerId) { mutableStateOf(false) }
    var isLoading by remember(playerId) { mutableStateOf(false) }
    var currentPosition by remember(playerId) { mutableFloatStateOf(0f) }
    var duration by remember(playerId) { mutableFloatStateOf(0f) }
    var currentTimeText by remember(playerId) { mutableStateOf("00:00") }
    var totalTimeText by remember(playerId) { mutableStateOf("00:00") }
    var isDragging by remember(playerId) { mutableStateOf(false) }
    var dragPosition by remember(playerId) { mutableFloatStateOf(0f) }
    
    // 平滑动画进度条
    val animatedProgress by animateFloatAsState(
        targetValue = if (isDragging) dragPosition else currentPosition,
        animationSpec = tween(durationMillis = if (isDragging) 0 else 200),
        label = "progress_animation"
    )
    
    // 使用 AudioPlayerService 而不是自己的 MediaPlayer
    var audioService by remember(playerId) { mutableStateOf<com.yhchat.canary.service.AudioPlayerService?>(null) }
    var serviceBound by remember(playerId) { mutableStateOf(false) }
    
    val serviceConnection = remember(playerId) {
        object : android.content.ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as? com.yhchat.canary.service.AudioPlayerService.AudioPlayerBinder
                audioService = binder?.getService()
                serviceBound = true
            }
            
            override fun onServiceDisconnected(name: android.content.ComponentName?) {
                audioService = null
                serviceBound = false
            }
        }
    }
    
    val handler = remember { Handler(Looper.getMainLooper()) }
    
    // 绑定到 AudioPlayerService
    DisposableEffect(playerId) {
        val intent = Intent(context, com.yhchat.canary.service.AudioPlayerService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        
        onDispose {
            if (serviceBound) {
                context.unbindService(serviceConnection)
            }
        }
    }
    
    // 监听服务状态并同步进度
    val updateProgressRunnable = remember(playerId) {
        object : Runnable {
            override fun run() {
                audioService?.let { service ->
                    try {
                        if (service.getCurrentAudioUrl() == url) {
                            val serviceIsPlaying = service.isPlaying()
                            val position = service.getCurrentPosition()
                            val durationMs = service.getCurrentDurationMs()
                            
                            if (!isDragging) {
                                isPlaying = serviceIsPlaying
                                currentPosition = position.toFloat()
                                duration = durationMs.toFloat()
                                currentTimeText = formatTime(position)
                                totalTimeText = formatTime(durationMs)
                            }
                        } else if (!isDragging) {
                            isPlaying = false
                        }
                        
                        // 继续更新
                        handler.postDelayed(this, 200)
                    } catch (e: Exception) {
                        // Handle errors
                    }
                }
            }
        }
    }
    
    
    // 开始进度更新
    LaunchedEffect(serviceBound, url) {
        if (serviceBound && url.isNotBlank()) {
            handler.post(updateProgressRunnable)
        }
    }
    
    // 清理资源
    DisposableEffect(playerId) {
        onDispose {
            handler.removeCallbacks(updateProgressRunnable)
        }
    }
    
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Audio icon and description
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Audio wave icon using Canvas
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Audio",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = if (description.isNotBlank()) description else "音频播放器",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = totalTimeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // 可拖动的进度条
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Slider(
                value = if (duration > 0) (animatedProgress / duration).coerceIn(0f, 1f) else 0f,
                onValueChange = { progress ->
                    if (duration > 0) {
                        isDragging = true
                        dragPosition = progress * duration
                        // 实时更新显示时间
                        currentTimeText = formatTime(dragPosition.toLong())
                    }
                },
                onValueChangeFinished = {
                    if (duration > 0 && serviceBound) {
                        // 拖动结束，通过服务跳转到指定位置
                        com.yhchat.canary.service.AudioPlayerService.seekTo(context, dragPosition.toLong())
                        currentPosition = dragPosition
                        isDragging = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = duration > 0 && serviceBound
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = currentTimeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = totalTimeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // Control buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Play/Pause button
            IconButton(
                onClick = {
                    if (url.isNotBlank()) {
                        if (isPlaying) {
                            // 暂停播放
                            context.startService(
                                Intent(context, com.yhchat.canary.service.AudioPlayerService::class.java).apply {
                                    action = "action_pause"
                                }
                            )
                        } else {
                            // 开始播放
                            val serviceUrl = audioService?.getCurrentAudioUrl()
                            if (serviceBound && serviceUrl == url) {
                                context.startService(
                                    Intent(context, com.yhchat.canary.service.AudioPlayerService::class.java).apply {
                                        action = "action_play"
                                    }
                                )
                            } else {
                                val title = if (description.isNotBlank()) description else "A2UI音频"
                                com.yhchat.canary.service.AudioPlayerService.startPlayAudio(context, url, title)
                            }
                        }
                    }
                },
                enabled = !isLoading && url.isNotBlank()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放"
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Stop button
            IconButton(
                onClick = {
                    // 停止播放并重置进度
                    com.yhchat.canary.service.AudioPlayerService.stopPlayAudio(context)
                    isPlaying = false
                    currentPosition = 0f
                    currentTimeText = "00:00"
                },
                enabled = !isLoading && serviceBound
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "停止"
                )
            }
        }
    }
}

// Helper function to format time
internal fun formatTime(milliseconds: Long): String {
    val seconds = (milliseconds / 1000).toInt()
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, remainingSeconds)
}

// A2UI VideoPlayer Component
@Composable
internal fun A2UiVideoPlayer(
    playerId: String,
    url: String,
    poster: String,
    fit: String,
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp = 300.dp,
    height: androidx.compose.ui.unit.Dp = 200.dp
) {
    val context = LocalContext.current
    
    var isPlaying by remember(playerId) { mutableStateOf(false) }
    var isLoading by remember(playerId) { mutableStateOf(false) }
    var showController by remember(playerId) { mutableStateOf(true) }
    var currentPosition by remember(playerId) { mutableFloatStateOf(0f) }
    var duration by remember(playerId) { mutableFloatStateOf(0f) }
    var currentTimeText by remember(playerId) { mutableStateOf("00:00") }
    var totalTimeText by remember(playerId) { mutableStateOf("00:00") }
    var isBuffering by remember(playerId) { mutableStateOf(false) }
    var hideControllerJob by remember(playerId) { mutableStateOf<Job?>(null) }
    
    var videoView by remember(playerId) { mutableStateOf<android.widget.VideoView?>(null) }
    val handler = remember { Handler(Looper.getMainLooper()) }
    val scope = rememberCoroutineScope()
    
    // 使用 AudioPlayerService 处理视频音频
    var audioService by remember(playerId) { mutableStateOf<com.yhchat.canary.service.AudioPlayerService?>(null) }
    var serviceBound by remember(playerId) { mutableStateOf(false) }
    
    val serviceConnection = remember(playerId) {
        object : android.content.ServiceConnection {
            override fun onServiceConnected(name: android.content.ComponentName?, service: IBinder?) {
                val binder = service as? com.yhchat.canary.service.AudioPlayerService.AudioPlayerBinder
                audioService = binder?.getService()
                serviceBound = true
            }
            
            override fun onServiceDisconnected(name: android.content.ComponentName?) {
                audioService = null
                serviceBound = false
            }
        }
    }
    
    // 绑定到 AudioPlayerService
    DisposableEffect(playerId) {
        val intent = Intent(context, com.yhchat.canary.service.AudioPlayerService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        
        onDispose {
            if (serviceBound) {
                context.unbindService(serviceConnection)
            }
        }
    }
    
    // Activity result launcher for fullscreen video
    val fullscreenLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                val returnedPosition = data.getLongExtra(
                    com.yhchat.canary.ui.video.FullscreenVideoActivity.RESULT_CURRENT_POSITION, 
                    currentPosition.toLong()
                )
                val returnedIsPlaying = data.getBooleanExtra(
                    com.yhchat.canary.ui.video.FullscreenVideoActivity.RESULT_IS_PLAYING, 
                    isPlaying
                )
                
                // 同步全屏播放器返回的进度和状态
                videoView?.let { view ->
                    view.seekTo(returnedPosition.toInt())
                    currentPosition = returnedPosition.toFloat()
                    currentTimeText = formatTime(returnedPosition)
                    
                    if (returnedIsPlaying && !view.isPlaying) {
                        view.start()
                        isPlaying = true
                    } else if (!returnedIsPlaying && view.isPlaying) {
                        view.pause()
                        isPlaying = false
                        showController = true
                    }
                }
            }
        }
    }
    
    // Content scale based on fit parameter
    val contentScale = when (fit.lowercase(Locale.ROOT)) {
        "cover", "crop" -> ContentScale.Crop
        "fill", "stretch" -> ContentScale.FillBounds
        "none" -> ContentScale.None
        else -> ContentScale.Fit
    }
    
    // Auto-hide controller after 3 seconds
    fun scheduleHideController() {
        hideControllerJob?.cancel()
        hideControllerJob = scope.launch {
            kotlinx.coroutines.delay(3000)
            if (isPlaying) {
                showController = false
            }
        }
    }
    
    // Update progress runnable
    val updateProgressRunnable = remember(playerId) {
        object : Runnable {
            override fun run() {
                videoView?.let { view ->
                    if (view.isPlaying) {
                        currentPosition = view.currentPosition.toFloat()
                        currentTimeText = formatTime(view.currentPosition.toLong())
                        handler.postDelayed(this, 100)
                    }
                }
            }
        }
    }
    
    // Cleanup on dispose
    DisposableEffect(playerId) {
        onDispose {
            hideControllerJob?.cancel()
            handler.removeCallbacks(updateProgressRunnable)
            videoView?.let { view ->
                if (view.isPlaying) {
                    view.stopPlayback()
                }
            }
        }
    }
    
    // Initialize video when URL changes
    LaunchedEffect(url, playerId) {
        if (url.isNotBlank()) {
            isLoading = true
        }
    }
    
    // Video control functions - 同时控制视频和音频服务
    fun playVideo() {
        videoView?.let { view ->
            view.start()
            isPlaying = true
            handler.post(updateProgressRunnable)
            scheduleHideController()
            
            // 同时启动音频服务来处理通知和后台播放
            if (url.isNotBlank()) {
                val title = "A2UI视频"
                com.yhchat.canary.service.AudioPlayerService.startPlayAudio(context, url, title)
            }
        }
    }
    
    fun pauseVideo() {
        videoView?.let { view ->
            view.pause()
            isPlaying = false
            handler.removeCallbacks(updateProgressRunnable)
            showController = true
            
            // 暂停音频服务
            com.yhchat.canary.service.AudioPlayerService.stopPlayAudio(context)
        }
    }
    
    fun stopVideo() {
        videoView?.let { view ->
            if (view.isPlaying) {
                view.stopPlayback()
            }
            view.seekTo(0)
            isPlaying = false
            currentPosition = 0f
            currentTimeText = "00:00"
            handler.removeCallbacks(updateProgressRunnable)
            showController = true
            
            // 停止音频服务
            com.yhchat.canary.service.AudioPlayerService.stopPlayAudio(context)
        }
    }
    
    fun seekForward(seconds: Int = 10) {
        videoView?.let { view ->
            val newPosition = (view.currentPosition + seconds * 1000).coerceAtMost(duration.toInt())
            view.seekTo(newPosition)
            currentPosition = newPosition.toFloat()
            currentTimeText = formatTime(newPosition.toLong())
            
            // 同步音频服务进度
            com.yhchat.canary.service.AudioPlayerService.seekTo(context, newPosition.toLong())
        }
    }
    
    fun seekBackward(seconds: Int = 10) {
        videoView?.let { view ->
            val newPosition = (view.currentPosition - seconds * 1000).coerceAtLeast(0)
            view.seekTo(newPosition)
            currentPosition = newPosition.toFloat()
            currentTimeText = formatTime(newPosition.toLong())
            
            // 同步音频服务进度
            com.yhchat.canary.service.AudioPlayerService.seekTo(context, newPosition.toLong())
        }
    }
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // VideoView
        AndroidView(
            factory = { ctx ->
                android.widget.VideoView(ctx).apply {
                    tag = playerId
                    videoView = this
                    
                    setOnPreparedListener { mp ->
                        mp.isLooping = false
                        duration = this.duration.toFloat()
                        totalTimeText = formatTime(this.duration.toLong())
                        isLoading = false
                        isBuffering = false
                    }
                    
                    setOnCompletionListener {
                        isPlaying = false
                        currentPosition = 0f
                        currentTimeText = "00:00"
                        handler.removeCallbacks(updateProgressRunnable)
                        showController = true
                    }
                    
                    setOnErrorListener { _, _, _ ->
                        isLoading = false
                        isBuffering = false
                        isPlaying = false
                        true
                    }
                    
                    setOnInfoListener { _, what, _ ->
                        when (what) {
                            MediaPlayer.MEDIA_INFO_BUFFERING_START -> {
                                isBuffering = true
                            }
                            MediaPlayer.MEDIA_INFO_BUFFERING_END -> {
                                isBuffering = false
                            }
                        }
                        false
                    }
                    
                    // Set video URI
                    if (url.isNotBlank()) {
                        try {
                            val uri = android.net.Uri.parse(url)
                            setVideoURI(uri)
                        } catch (e: Exception) {
                            // Ignore URI parse errors
                        }
                    }
                }
            },
            update = { view ->
                if (url.isNotBlank() && view.tag != playerId) {
                    view.tag = playerId
                    try {
                        val uri = android.net.Uri.parse(url)
                        view.setVideoURI(uri)
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clickable {
                    showController = true
                    if (isPlaying) {
                        scheduleHideController()
                    }
                }
        )
        
        // Poster image (shown when not playing)
        if (!isPlaying && poster.isNotBlank()) {
            AsyncImage(
                model = poster,
                contentDescription = "Video poster",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height),
                contentScale = contentScale
            )
        }
        
        // Loading/Buffering indicator
        if (isLoading || isBuffering) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp)
                )
            }
        }
        
        // Large center play button (shown when paused and not loading)
        if (!isPlaying && !isLoading && url.isNotBlank()) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable { playVideo() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "播放",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
        
        // Click on video to show/hide controller when playing
        if (isPlaying) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .clickable {
                        showController = !showController
                        if (showController) {
                            scheduleHideController()
                        }
                    }
            )
        }
        
        // Full controller overlay (always visible when showController is true)
        if (showController) {
            // Top gradient for better visibility
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.6f),
                                Color.Transparent
                            )
                        )
                    )
            )
            
            // Bottom control bar
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.8f)
                            )
                        )
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Progress bar with time
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Seek slider
                    Slider(
                        value = if (duration > 0) (currentPosition / duration).coerceIn(0f, 1f) else 0f,
                        onValueChange = { fraction ->
                            val newPosition = (fraction * duration).toInt()
                            videoView?.seekTo(newPosition)
                            currentPosition = newPosition.toFloat()
                            currentTimeText = formatTime(newPosition.toLong())
                        },
                        onValueChangeFinished = {
                            // 同步音频服务进度
                            com.yhchat.canary.service.AudioPlayerService.seekTo(context, currentPosition.toLong())
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    // Time display
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = currentTimeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                        Text(
                            text = totalTimeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Control buttons row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rewind 10s button
                    IconButton(
                        onClick = { seekBackward(10) },
                        enabled = !isLoading,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Replay,
                                contentDescription = "后退10秒",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = "10",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontSize = androidx.compose.ui.unit.TextUnit(8f, androidx.compose.ui.unit.TextUnitType.Sp)
                            )
                        }
                    }
                    
                    // Stop button
                    IconButton(
                        onClick = { stopVideo() },
                        enabled = !isLoading,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "停止",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    
                    // Play/Pause button (large)
                    IconButton(
                        onClick = {
                            if (isPlaying) {
                                pauseVideo()
                            } else {
                                playVideo()
                            }
                        },
                        enabled = !isLoading,
                        modifier = Modifier.size(56.dp)
                    ) {
                        if (isBuffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "暂停" else "播放",
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    
                    // Forward 10s button
                    IconButton(
                        onClick = { seekForward(10) },
                        enabled = !isLoading,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Replay,
                                contentDescription = "前进10秒",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = "10",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontSize = androidx.compose.ui.unit.TextUnit(8f, androidx.compose.ui.unit.TextUnitType.Sp)
                            )
                        }
                    }
                    
                    // Fullscreen button
                    IconButton(
                        onClick = {
                            // 使用 ActivityResultLauncher 启动全屏播放器以获取返回结果
                            val intent = Intent(context, com.yhchat.canary.ui.video.FullscreenVideoActivity::class.java).apply {
                                putExtra(com.yhchat.canary.ui.video.FullscreenVideoActivity.EXTRA_VIDEO_URL, url)
                                putExtra(com.yhchat.canary.ui.video.FullscreenVideoActivity.EXTRA_CURRENT_POSITION, currentPosition.toLong())
                                putExtra(com.yhchat.canary.ui.video.FullscreenVideoActivity.EXTRA_IS_PLAYING, isPlaying)
                            }
                            fullscreenLauncher.launch(intent)
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.OpenInFull,
                            contentDescription = "全屏",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}
