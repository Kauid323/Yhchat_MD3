package com.yhchat.canary.ui.chat.ChatComponents

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yhchat.canary.service.AudioPlayerService
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniAudioPlayerBar(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var mediaController by remember { mutableStateOf<MediaControllerCompat?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var progressMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var showBottomSheet by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as? AudioPlayerService.AudioPlayerBinder
                binder?.getService()?.let { playerService ->
                    val token = playerService.getSessionToken()
                    val controller = MediaControllerCompat(context, token)
                    mediaController = controller

                    val metadata = controller.metadata
                    val playbackState = controller.playbackState

                    isPlaying = playbackState?.state == PlaybackStateCompat.STATE_PLAYING
                    title = metadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE) ?: "未知"
                    progressMs = playbackState?.position ?: 0L
                    durationMs = metadata?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) ?: 0L

                    controller.registerCallback(object : MediaControllerCompat.Callback() {
                        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
                            isPlaying = state?.state == PlaybackStateCompat.STATE_PLAYING
                            progressMs = state?.position ?: 0L
                            durationMs = mediaController?.metadata?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) ?: durationMs
                        }

                        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
                            title = metadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE) ?: "未知"
                            isPlaying = mediaController?.playbackState?.state == PlaybackStateCompat.STATE_PLAYING
                            progressMs = mediaController?.playbackState?.position ?: 0L
                            durationMs = metadata?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) ?: durationMs
                        }
                    })
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                mediaController = null
            }
        }

        val intent = Intent(context, AudioPlayerService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)

        onDispose {
            context.unbindService(connection)
        }
    }

    LaunchedEffect(mediaController, isPlaying) {
        while (isActive && isPlaying && mediaController != null) {
            val state = mediaController?.playbackState
            if (state != null) {
                val position = state.position
                val timeDelta = android.os.SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
                val current = if (state.state == PlaybackStateCompat.STATE_PLAYING) {
                    (position + (timeDelta * state.playbackSpeed)).toLong()
                } else {
                    position
                }
                progressMs = current
            }
            delay(500)
        }
    }

    val isVisible = mediaController != null && (isPlaying || progressMs > 0 || title.isNotEmpty())
    
    if (isVisible) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { showBottomSheet = true }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (isPlaying) {
                        mediaController?.transportControls?.pause()
                    } else {
                        mediaController?.transportControls?.play()
                    }
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放"
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            val formattedProgress = formatTime(progressMs)
            val formattedDuration = formatTime(durationMs)
            
            Text(
                text = "$formattedProgress/$formattedDuration",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            IconButton(
                onClick = { 
                    AudioPlayerService.stopPlayAudio(context)
                    title = ""
                    progressMs = 0L
                    durationMs = 0L
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "关闭"
                )
            }
        }
    }

    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Slider(
                    value = if (durationMs > 0) (progressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f,
                    onValueChange = { newValue ->
                        val newPos = (newValue * durationMs).toLong()
                        progressMs = newPos
                        mediaController?.transportControls?.seekTo(newPos)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = formatTime(progressMs))
                    Text(text = formatTime(durationMs))
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                IconButton(
                    onClick = {
                        if (isPlaying) {
                            mediaController?.transportControls?.pause()
                        } else {
                            mediaController?.transportControls?.play()
                        }
                    },
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        modifier = Modifier.size(48.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
