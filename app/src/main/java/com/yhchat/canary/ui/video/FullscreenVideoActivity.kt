package com.yhchat.canary.ui.video

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.Toast
import com.yhchat.canary.service.FileDownloadService
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import com.yhchat.canary.ui.theme.YhchatCanaryTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FullscreenVideoActivity : ComponentActivity() {
    private val viewModel: FullscreenVideoViewModel by viewModels()
    
    companion object {
        const val EXTRA_VIDEO_URL = "video_url"
        const val EXTRA_CURRENT_POSITION = "current_position"
        const val EXTRA_IS_PLAYING = "is_playing"
        const val RESULT_CURRENT_POSITION = "result_current_position"
        const val RESULT_IS_PLAYING = "result_is_playing"
        
        fun start(
            context: Context,
            videoUrl: String,
            currentPosition: Long = 0,
            isPlaying: Boolean = false
        ) {
            val intent = Intent(context, FullscreenVideoActivity::class.java).apply {
                putExtra(EXTRA_VIDEO_URL, videoUrl)
                putExtra(EXTRA_CURRENT_POSITION, currentPosition)
                putExtra(EXTRA_IS_PLAYING, isPlaying)
            }
            if (context is Activity) {
                context.startActivityForResult(intent, 1001)
            } else {
                context.startActivity(intent)
            }
        }
    }
    
    private var currentVideoPosition: Long = 0
    private var isVideoPlaying: Boolean = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 设置全屏和横屏
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        
        val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL) ?: ""
        val currentPosition = intent.getLongExtra(EXTRA_CURRENT_POSITION, 0)
        val isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, false)
        
        setContent {
            YhchatCanaryTheme {
                FullscreenVideoPlayer(
                    videoUrl = videoUrl,
                    initialPosition = currentPosition,
                    initialPlaying = isPlaying,
                    viewModel = viewModel,
                    onClose = { position, playing ->
                        currentVideoPosition = position
                        isVideoPlaying = playing
                        finish()
                    }
                )
            }
        }
    }
    
    override fun finish() {
        val resultIntent = Intent().apply {
            putExtra(RESULT_CURRENT_POSITION, currentVideoPosition)
            putExtra(RESULT_IS_PLAYING, isVideoPlaying)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        super.finish()
    }
}

@Composable
private fun FullscreenVideoPlayer(
    videoUrl: String,
    initialPosition: Long,
    initialPlaying: Boolean,
    viewModel: FullscreenVideoViewModel,
    onClose: (position: Long, isPlaying: Boolean) -> Unit
) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val handler = remember { Handler(Looper.getMainLooper()) }

    var videoView by remember { mutableStateOf<android.widget.VideoView?>(null) }
    var moreMenuExpanded by remember { mutableStateOf(false) }
    var isLandscape by remember { mutableStateOf(true) }

    LaunchedEffect(initialPosition, initialPlaying) {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        viewModel.initialize(
            initialPosition = initialPosition,
            initialPlaying = initialPlaying,
            initialVolume = currentVolume.toFloat() / maxVolume.toFloat()
        )
    }

    var hideControllerJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    
    // Update progress runnable
    val updateProgressRunnable = remember {
        object : Runnable {
            override fun run() {
                videoView?.let { view ->
                    if (view.isPlaying) {
                        viewModel.onProgressUpdated(view.currentPosition.toLong())
                        handler.postDelayed(this, 100)
                    }
                }
            }
        }
    }
    
    // Auto-hide controller
    fun scheduleHideController() {
        hideControllerJob?.cancel()
        hideControllerJob = scope.launch {
            delay(3000)
            if (uiState.isPlaying) {
                viewModel.setControlsVisible(false)
                viewModel.setVolumeSliderVisible(false)
            }
        }
    }
    
    // Video control functions
    fun playVideo() {
        videoView?.let { view ->
            view.start()
            viewModel.onPlayStarted()
            handler.post(updateProgressRunnable)
            scheduleHideController()
        }
    }
    
    fun pauseVideo() {
        videoView?.let { view ->
            view.pause()
            viewModel.onPaused()
            handler.removeCallbacks(updateProgressRunnable)
        }
    }
    
    fun seekForward(seconds: Int = 10) {
        videoView?.let { view ->
            val durationMs = uiState.durationMs
            val newPosition = (view.currentPosition + seconds * 1000)
                .coerceAtMost(durationMs.toInt())
            view.seekTo(newPosition)
            viewModel.onSeek(newPosition.toLong())
        }
    }
    
    fun seekBackward(seconds: Int = 10) {
        videoView?.let { view ->
            val newPosition = (view.currentPosition - seconds * 1000).coerceAtLeast(0)
            view.seekTo(newPosition)
            viewModel.onSeek(newPosition.toLong())
        }
    }
    
    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            hideControllerJob?.cancel()
            handler.removeCallbacks(updateProgressRunnable)
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { 
                val nextVisible = !uiState.showControls
                viewModel.setControlsVisible(nextVisible)
                if (nextVisible && uiState.isPlaying) {
                    scheduleHideController()
                }
            }
    ) {
        // 视频播放器
        AndroidView(
            factory = { ctx ->
                android.widget.VideoView(ctx).apply {
                    setVideoPath(videoUrl)
                    setOnPreparedListener { mediaPlayer ->
                        mediaPlayer.seekTo(initialPosition.toInt())
                        viewModel.onPrepared(this.duration.toLong())
                        if (initialPlaying) {
                            start()
                            viewModel.onPlayStarted()
                            handler.post(updateProgressRunnable)
                            scheduleHideController()
                        }
                        videoView = this
                    }
                    setOnCompletionListener {
                        viewModel.onCompletion()
                        handler.removeCallbacks(updateProgressRunnable)
                    }
                    setOnInfoListener { _, what, _ ->
                        when (what) {
                            MediaPlayer.MEDIA_INFO_BUFFERING_START -> {
                                viewModel.onBufferingChanged(true)
                            }
                            MediaPlayer.MEDIA_INFO_BUFFERING_END -> {
                                viewModel.onBufferingChanged(false)
                            }
                        }
                        false
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Loading indicator
        if (uiState.isBuffering) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp)
                )
            }
        }
        
        // 控制栏
        if (uiState.showControls) {
            // Top gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.7f),
                                Color.Transparent
                            )
                        )
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            onClose(uiState.currentPositionMs, uiState.isPlaying)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Box {
                        IconButton(onClick = { moreMenuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "更多操作",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = moreMenuExpanded,
                            onDismissRequest = { moreMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (isLandscape) "切换为竖屏" else "切换为横屏") },
                                onClick = {
                                    moreMenuExpanded = false
                                    isLandscape = !isLandscape
                                    (context as? Activity)?.requestedOrientation = if (isLandscape) {
                                        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                    } else {
                                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("保存视频") },
                                onClick = {
                                    moreMenuExpanded = false
                                    val fileName = videoUrl.substringAfterLast("/").ifEmpty {
                                        "video_${System.currentTimeMillis()}.mp4"
                                    }
                                    FileDownloadService.startDownload(
                                        context = context,
                                        fileUrl = videoUrl,
                                        fileName = fileName,
                                        fileSize = 0L,
                                        autoOpen = false
                                    )
                                    Toast.makeText(context, "开始下载视频：$fileName", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
            
            // Center play/pause button (when paused)
            if (!uiState.isPlaying && !uiState.isBuffering) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.7f))
                        .clickable { playVideo() }
                        .align(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "播放",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            
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
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Progress bar with time
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val durationMs = uiState.durationMs
                    // Seek slider
                    Slider(
                        value = if (durationMs > 0L) {
                            (uiState.currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        },
                        onValueChange = { fraction ->
                            val newPosition = (fraction * durationMs.toFloat()).toInt()
                            videoView?.seekTo(newPosition)
                            viewModel.onSeek(newPosition.toLong())
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    // Time display
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(uiState.currentPositionMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = formatTime(durationMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Control buttons row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rewind 10s button
                    IconButton(
                        onClick = { seekBackward(10) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay10,
                                contentDescription = "后退10秒",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    
                    // Play/Pause button (large)
                    IconButton(
                        onClick = {
                            if (uiState.isPlaying) {
                                pauseVideo()
                            } else {
                                playVideo()
                            }
                        },
                        modifier = Modifier.size(64.dp)
                    ) {
                        if (uiState.isBuffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 3.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (uiState.isPlaying) "暂停" else "播放",
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    
                    // Forward 10s button
                    IconButton(
                        onClick = { seekForward(10) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Forward10,
                                contentDescription = "前进10秒",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    
                    // Volume button
                    IconButton(
                        onClick = { 
                            val nextVisible = !uiState.showVolumeSlider
                            viewModel.setVolumeSliderVisible(nextVisible)
                            if (nextVisible) {
                                scheduleHideController()
                            }
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (uiState.volume > 0f) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = "音量",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                
                // Volume slider
                if (uiState.showVolumeSlider) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeDown,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Slider(
                            value = uiState.volume,
                            onValueChange = { newVolume ->
                                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                val volumeLevel = (newVolume * maxVolume).toInt()
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volumeLevel, 0)
                                viewModel.onVolumeChanged(newVolume)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

// Helper function to format time in mm:ss format
private fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
