package com.yhchat.canary.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import androidx.media.app.NotificationCompat.MediaStyle
import com.yhchat.canary.MainActivity
import com.yhchat.canary.R
import com.yhchat.canary.data.local.AppDatabase
import com.yhchat.canary.data.local.AudioPlayMode
import com.yhchat.canary.ncm.NcmAudioMatcher
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/**
 * 语音播放服务
 * 支持后台播放，即使退出聊天界面也不会停止
 */
class AudioPlayerService : Service() {
    
    companion object {
        private const val TAG = "AudioPlayerService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "audio_player_channel"
        private const val ACTION_PLAY = "action_play"
        private const val ACTION_PAUSE = "action_pause"
        private const val ACTION_STOP = "action_stop"
        private const val ACTION_NEXT = "action_next"
        private const val ACTION_PREV = "action_prev"
        private const val ACTION_SEEK = "action_seek"
        
        const val EXTRA_AUDIO_URL = "extra_audio_url"
        const val EXTRA_AUDIO_TITLE = "extra_audio_title"
        private const val EXTRA_LOCAL_AUDIO_PATH = "extra_local_audio_path"
        private const val EXTRA_IS_SAVED_AUDIO = "extra_is_saved_audio"
        private const val EXTRA_AUDIO_CONTENT_URI = "extra_audio_content_uri"
        private const val EXTRA_SEEK_POSITION_MS = "extra_seek_position_ms"

        private const val SAVED_AUDIO_DIR = "/storage/emulated/0/Download/yhchat/audio/"
        
        /**
         * 启动语音播放服务
         */
        fun startPlayAudio(context: Context, audioUrl: String, title: String = "语音消息") {
            val intent = Intent(context, AudioPlayerService::class.java).apply {
                action = ACTION_PLAY
                putExtra(EXTRA_AUDIO_URL, audioUrl)
                putExtra(EXTRA_AUDIO_TITLE, title)
            }
            context.startForegroundService(intent)
        }

        fun startPlayLocalAudio(context: Context, localPath: String, title: String = "语音") {
            val intent = Intent(context, AudioPlayerService::class.java).apply {
                action = ACTION_PLAY
                putExtra(EXTRA_LOCAL_AUDIO_PATH, localPath)
                putExtra(EXTRA_AUDIO_TITLE, title)
                putExtra(EXTRA_IS_SAVED_AUDIO, true)
            }
            context.startForegroundService(intent)
        }

        fun startPlaySavedAudio(context: Context, contentUri: String, title: String = "语音") {
            val intent = Intent(context, AudioPlayerService::class.java).apply {
                action = ACTION_PLAY
                putExtra(EXTRA_AUDIO_CONTENT_URI, contentUri)
                putExtra(EXTRA_AUDIO_TITLE, title)
                putExtra(EXTRA_IS_SAVED_AUDIO, true)
            }
            context.startForegroundService(intent)
        }
        
        /**
         * 停止语音播放服务
         */
        fun stopPlayAudio(context: Context) {
            val intent = Intent(context, AudioPlayerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun forceCloseMusicNotification(context: Context) {
            stopPlayAudio(context)
            runCatching {
                context.stopService(Intent(context, AudioPlayerService::class.java))
            }
        }

        fun setPlayMode(context: Context, mode: AudioPlayMode) {
            val prefs = context.getSharedPreferences("audio_player_settings", Context.MODE_PRIVATE)
            prefs.edit().putString("play_mode", mode.name).apply()
        }

        fun getPlayMode(context: Context): AudioPlayMode {
            val prefs = context.getSharedPreferences("audio_player_settings", Context.MODE_PRIVATE)
            val modeStr = prefs.getString("play_mode", AudioPlayMode.SEQUENCE.name) ?: AudioPlayMode.SEQUENCE.name
            return runCatching { AudioPlayMode.valueOf(modeStr) }.getOrDefault(AudioPlayMode.SEQUENCE)
        }

        fun setActivePlaylistId(context: Context, playlistId: String) {
            val prefs = context.getSharedPreferences("audio_player_settings", Context.MODE_PRIVATE)
            prefs.edit().putString("active_playlist_id", playlistId).apply()
        }

        fun getActivePlaylistId(context: Context): String {
            val prefs = context.getSharedPreferences("audio_player_settings", Context.MODE_PRIVATE)
            return prefs.getString("active_playlist_id", "auto_queue") ?: "auto_queue"
        }

        fun seekTo(context: Context, positionMs: Long) {
            val intent = Intent(context, AudioPlayerService::class.java).apply {
                action = ACTION_SEEK
                putExtra(EXTRA_SEEK_POSITION_MS, positionMs)
            }
            context.startService(intent)
        }
    }
    
    private val binder = AudioPlayerBinder()
    private var mediaPlayer: MediaPlayer? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val originalRequest = chain.request()
            val url = originalRequest.url.toString()
            
            val requestBuilder = originalRequest.newBuilder()
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
            
            // 只有当 URL 包含 .jwznb.com 时才添加 Referer
            if (url.contains(".jwznb.com")) {
                requestBuilder.addHeader("Referer", "https://myapp.jwznb.com")
            }
            
            chain.proceed(requestBuilder.build())
        }
        .build()
    
    private var currentAudioUrl: String? = null
    private var isPlaying: Boolean = false
    private lateinit var audioCacheManager: AudioCacheManager
    private var currentTitle: String = "语音消息"
    private var currentArtist: String = ""
    private var currentCoverUrl: String? = null
    private var currentCoverBitmap: Bitmap? = null
    private lateinit var audioManager: AudioManager
    private var audioFocusGranted: Boolean = false
    private lateinit var mediaSession: MediaSessionCompat
    private val progressPrefs by lazy { getSharedPreferences("audio_player_progress", Context.MODE_PRIVATE) }
    private var progressJob: Job? = null
    private var currentDurationMs: Long = 0L

    private data class SavedAudioItem(
        val contentUri: String,
        val title: String
    )

    private var currentLocalPath: String? = null
    private var currentContentUri: String? = null
    private val savedAudioQueue = mutableListOf<SavedAudioItem>()
    private var savedQueueIndex: Int = -1
    private var currentIsSavedAudio: Boolean = false
    
    inner class AudioPlayerBinder : Binder() {
        fun getService(): AudioPlayerService = this@AudioPlayerService
    }

    fun getSessionToken(): MediaSessionCompat.Token = mediaSession.sessionToken
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        audioCacheManager = AudioCacheManager(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        mediaSession = MediaSessionCompat(this, TAG).apply {
            @Suppress("DEPRECATION")
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    currentLocalPath?.let { path ->
                        playLocalAudio(path, currentTitle)
                    } ?: run {
                        currentAudioUrl?.let { url ->
                            playAudio(url, currentTitle)
                        } ?: run {
                            currentContentUri?.let { uri ->
                                playSavedAudioUri(uri, currentTitle)
                            }
                        }
                    }
                }

                override fun onPause() {
                    pauseAudio()
                }

                override fun onStop() {
                    stopAudio()
                }

                override fun onSkipToNext() {
                    skipToNext()
                }

                override fun onSkipToPrevious() {
                    skipToPrevious()
                }

                override fun onSeekTo(pos: Long) {
                    seekTo(pos)
                }
            })
            isActive = true
        }
        Log.d(TAG, "AudioPlayerService created")
    }

     private fun createNotificationChannel() {
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
             val channel = NotificationChannel(
                 CHANNEL_ID,
                 "语音播放",
                 NotificationManager.IMPORTANCE_LOW
             ).apply {
                 description = "语音消息播放通知"
                 setSound(null, null)
             }
             val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
             notificationManager.createNotificationChannel(channel)
         }
     }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val title = intent.getStringExtra(EXTRA_AUDIO_TITLE) ?: "语音消息"
                val localPath = intent.getStringExtra(EXTRA_LOCAL_AUDIO_PATH)
                val contentUri = intent.getStringExtra(EXTRA_AUDIO_CONTENT_URI)
                val isSaved = intent.getBooleanExtra(EXTRA_IS_SAVED_AUDIO, false)
                val audioUrl = intent.getStringExtra(EXTRA_AUDIO_URL)
                if (!contentUri.isNullOrBlank()) {
                    // 对于 contentUri：默认认为它可能来自“已保存音频”，因此尝试建立队列并判断
                    // 即使外部没有显式传 EXTRA_IS_SAVED_AUDIO，也能实现按文件夹顺序切歌
                    currentIsSavedAudio = true
                    currentContentUri = contentUri
                    currentLocalPath = null
                    refreshSavedAudioQueue(selectedContentUri = contentUri)
                    // 如果队列里找不到该 uri，说明它不是保存目录的音频，则禁用切歌
                    currentIsSavedAudio = savedQueueIndex in savedAudioQueue.indices
                    playSavedAudioUri(contentUri, title)
                } else if (!localPath.isNullOrBlank()) {
                    currentIsSavedAudio = isSaved
                    currentLocalPath = localPath
                    currentContentUri = null
                    playLocalAudio(localPath, title)
                } else {
                    if (!audioUrl.isNullOrBlank()) {
                        currentIsSavedAudio = false
                        currentLocalPath = null
                        currentContentUri = null
                        currentAudioUrl = audioUrl
                        playAudio(audioUrl, title)
                    } else {
                        // 通知栏“播放”按钮通常不会携带 extras，这里视为继续播放
                        resumeAudio()
                    }
                }
            }
            ACTION_PAUSE -> pauseAudio()
            ACTION_STOP -> stopAudio()
            ACTION_NEXT -> {
                skipToNext()
            }
            ACTION_PREV -> {
                skipToPrevious()
            }
            ACTION_SEEK -> {
                val pos = intent.getLongExtra(EXTRA_SEEK_POSITION_MS, -1L)
                if (pos >= 0L) {
                    seekTo(pos)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun refreshSavedAudioQueue(selectedContentUri: String?) {
        val merged = LinkedHashMap<String, SavedAudioItem>()
        querySavedAudiosFromMediaStore().forEach { merged[it.contentUri] = it }
        querySavedAudiosFromAuthorizedFolder().forEach { merged[it.contentUri] = it }

        val results = merged.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })

        savedAudioQueue.clear()
        savedAudioQueue.addAll(results)
        savedQueueIndex = if (!selectedContentUri.isNullOrBlank()) {
            savedAudioQueue.indexOfFirst { it.contentUri == selectedContentUri }
        } else {
            -1
        }
    }

    private fun querySavedAudiosFromMediaStore(): List<SavedAudioItem> {
        val resolver = contentResolver
        val relativePath = "Download/yhchat/audio/"
        val legacyDirPrefix = legacySavedAudiosDirPrefix()
        val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME
            )
        } else {
            arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.DATA
            )
        }
        val selection: String
        val selectionArgs: Array<String>
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selection = "${MediaStore.MediaColumns.RELATIVE_PATH}=?"
            selectionArgs = arrayOf(relativePath)
        } else {
            selection = "${MediaStore.MediaColumns.DATA} LIKE ?"
            selectionArgs = arrayOf("$legacyDirPrefix%")
        }
        val sortOrder = "${MediaStore.MediaColumns.DISPLAY_NAME} COLLATE NOCASE ASC"

        val results = mutableListOf<SavedAudioItem>()
        resolver.query(
            savedAudiosCollectionUri(),
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val name = cursor.getString(nameIndex) ?: "语音"
                val uri = ContentUris.withAppendedId(savedAudiosCollectionUri(), id)
                results.add(
                    SavedAudioItem(
                        contentUri = uri.toString(),
                        title = name.substringBeforeLast('.', name)
                    )
                )
            }
        }
        return results
    }

    private fun savedAudiosCollectionUri(): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Uri.parse("content://media/external/downloads")
        } else {
            MediaStore.Files.getContentUri("external")
        }
    }

    private fun legacySavedAudiosDirPrefix(): String {
        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        val dir = java.io.File(downloadsDir, "yhchat/audio")
        val path = dir.absolutePath
        return if (path.endsWith("/")) path else "$path/"
    }

    private fun querySavedAudiosFromAuthorizedFolder(): List<SavedAudioItem> {
        val prefs = getSharedPreferences("saved_audios", Context.MODE_PRIVATE)
        val treeUriStr = prefs.getString("saved_audios_tree_uri", null) ?: return emptyList()
        val treeUri = runCatching { Uri.parse(treeUriStr) }.getOrNull() ?: return emptyList()

        val tree = runCatching { DocumentFile.fromTreeUri(this, treeUri) }.getOrNull() ?: return emptyList()
        if (!tree.isDirectory) return emptyList()

        val results = mutableListOf<SavedAudioItem>()
        tree.listFiles().forEach { file ->
            if (!file.isFile) return@forEach
            val type = file.type
            if (type == null || !type.startsWith("audio/")) return@forEach
            val name = file.name ?: "音频"
            results.add(
                SavedAudioItem(
                    contentUri = file.uri.toString(),
                    title = name.substringBeforeLast('.', name)
                )
            )
        }
        return results
    }

    private fun handlePlaybackCompletion(tempAudioFileToDelete: File? = null) {
        Log.d(TAG, "handlePlaybackCompletion, currentTitle=$currentTitle")
        this@AudioPlayerService.isPlaying = false
        saveProgressForCurrentAudio(0L)
        stopProgressUpdates()
        updatePlaybackState(playing = false)

        tempAudioFileToDelete?.let { file ->
            if (file.name.startsWith("temp_audio_")) {
                file.delete()
                Log.d(TAG, "清理临时音频文件: ${file.name}")
            }
        }

        serviceScope.launch {
            val mode = getPlayMode(this@AudioPlayerService)
            when (mode) {
                AudioPlayMode.SINGLE_LOOP -> {
                    // 单曲循环：重新从头播放当前音频
                    currentLocalPath?.let { playLocalAudio(it, currentTitle) }
                        ?: currentContentUri?.let { playSavedAudioUri(it, currentTitle) }
                        ?: currentAudioUrl?.let { playAudio(it, currentTitle) }
                        ?: stopSelf()
                }
                AudioPlayMode.SEQUENCE, AudioPlayMode.LIST_LOOP, AudioPlayMode.SHUFFLE -> {
                    val dao = AppDatabase.getDatabase(this@AudioPlayerService).audioPlaylistDao()
                    val activePlaylistId = getActivePlaylistId(this@AudioPlayerService)
                    val items = dao.getItemsForPlaylistSync(activePlaylistId)
                    if (items.isEmpty()) {
                        stopSelf()
                        return@launch
                    }

                    val currentIdx = items.indexOfFirst {
                        (currentAudioUrl != null && it.url == currentAudioUrl) ||
                        (currentContentUri != null && it.url == currentContentUri) ||
                        it.title == currentTitle
                    }

                    val nextIdx = when (mode) {
                        AudioPlayMode.SHUFFLE -> {
                            if (items.size == 1) 0
                            else {
                                var rand = items.indices.random()
                                if (rand == currentIdx && items.size > 1) {
                                    rand = (rand + 1) % items.size
                                }
                                rand
                            }
                        }
                        AudioPlayMode.LIST_LOOP -> {
                            if (currentIdx >= 0) (currentIdx + 1) % items.size else 0
                        }
                        AudioPlayMode.SEQUENCE -> {
                            if (currentIdx in 0 until items.size - 1) currentIdx + 1 else null
                        }
                        else -> null
                    }

                    if (nextIdx != null && nextIdx in items.indices) {
                        val nextItem = items[nextIdx]
                        if (nextItem.url.startsWith("content://") || nextItem.url.startsWith("file://")) {
                            playSavedAudioUri(nextItem.url, nextItem.title)
                        } else {
                            playAudio(nextItem.url, nextItem.title)
                        }
                    } else {
                        stopSelf()
                    }
                }
            }
        }
    }

    private fun skipToNext() {
        serviceScope.launch {
            val mode = getPlayMode(this@AudioPlayerService)
            val dao = AppDatabase.getDatabase(this@AudioPlayerService).audioPlaylistDao()
            val activePlaylistId = getActivePlaylistId(this@AudioPlayerService)
            val items = dao.getItemsForPlaylistSync(activePlaylistId)

            if (items.isNotEmpty()) {
                val currentIdx = items.indexOfFirst {
                    (currentAudioUrl != null && it.url == currentAudioUrl) ||
                    (currentContentUri != null && it.url == currentContentUri) ||
                    it.title == currentTitle
                }
                val nextIdx = when (mode) {
                    AudioPlayMode.SHUFFLE -> {
                        if (items.size == 1) 0
                        else {
                            var rand = items.indices.random()
                            if (rand == currentIdx && items.size > 1) {
                                rand = (rand + 1) % items.size
                            }
                            rand
                        }
                    }
                    AudioPlayMode.SINGLE_LOOP -> if (currentIdx in items.indices) currentIdx else 0
                    AudioPlayMode.LIST_LOOP -> if (currentIdx >= 0) (currentIdx + 1) % items.size else 0
                    AudioPlayMode.SEQUENCE -> if (currentIdx in 0 until items.size - 1) currentIdx + 1 else 0
                }
                val nextItem = items[nextIdx]
                if (nextItem.url.startsWith("content://") || nextItem.url.startsWith("file://")) {
                    playSavedAudioUri(nextItem.url, nextItem.title)
                } else {
                    playAudio(nextItem.url, nextItem.title)
                }
            } else if (currentIsSavedAudio && !currentContentUri.isNullOrBlank()) {
                if (savedAudioQueue.isEmpty()) {
                    refreshSavedAudioQueue(selectedContentUri = currentContentUri)
                }
                val nextIndex = savedQueueIndex + 1
                if (nextIndex in savedAudioQueue.indices) {
                    savedQueueIndex = nextIndex
                    val item = savedAudioQueue[savedQueueIndex]
                    currentContentUri = item.contentUri
                    playSavedAudioUri(item.contentUri, item.title)
                }
            }
        }
    }

    private fun skipToPrevious() {
        serviceScope.launch {
            val mode = getPlayMode(this@AudioPlayerService)
            val dao = AppDatabase.getDatabase(this@AudioPlayerService).audioPlaylistDao()
            val activePlaylistId = getActivePlaylistId(this@AudioPlayerService)
            val items = dao.getItemsForPlaylistSync(activePlaylistId)

            if (items.isNotEmpty()) {
                val currentIdx = items.indexOfFirst {
                    (currentAudioUrl != null && it.url == currentAudioUrl) ||
                    (currentContentUri != null && it.url == currentContentUri) ||
                    it.title == currentTitle
                }
                val prevIdx = when (mode) {
                    AudioPlayMode.SHUFFLE -> {
                        if (items.size == 1) 0
                        else {
                            var rand = items.indices.random()
                            if (rand == currentIdx && items.size > 1) {
                                rand = (rand + 1) % items.size
                            }
                            rand
                        }
                    }
                    AudioPlayMode.SINGLE_LOOP -> if (currentIdx in items.indices) currentIdx else 0
                    AudioPlayMode.LIST_LOOP -> if (currentIdx > 0) currentIdx - 1 else items.size - 1
                    AudioPlayMode.SEQUENCE -> if (currentIdx > 0) currentIdx - 1 else items.size - 1
                }
                val prevItem = items[prevIdx]
                if (prevItem.url.startsWith("content://") || prevItem.url.startsWith("file://")) {
                    playSavedAudioUri(prevItem.url, prevItem.title)
                } else {
                    playAudio(prevItem.url, prevItem.title)
                }
            } else if (currentIsSavedAudio && !currentContentUri.isNullOrBlank()) {
                if (savedAudioQueue.isEmpty()) {
                    refreshSavedAudioQueue(selectedContentUri = currentContentUri)
                }
                val prevIndex = savedQueueIndex - 1
                if (prevIndex in savedAudioQueue.indices) {
                    savedQueueIndex = prevIndex
                    val item = savedAudioQueue[savedQueueIndex]
                    currentContentUri = item.contentUri
                    playSavedAudioUri(item.contentUri, item.title)
                }
            }
        }
    }

    private fun playAudio(audioUrl: String, title: String) {
        // 如果正在播放相同的音频，则停止
        if (currentAudioUrl == audioUrl && isPlaying) {

            stopAudio()
            return
        }
        
        // 停止当前播放
        releaseMediaPlayer()
        stopProgressUpdates()

        currentAudioUrl = audioUrl
        currentLocalPath = null
        currentContentUri = null
        currentTitle = title
        currentDurationMs = 0L

        requestAudioFocus()
        
        // 开始前台服务
        startForeground(NOTIFICATION_ID, createNotification(title, "正在缓冲..."))

        // 优先：若存在并验证通过的缓存文件，则直接播放缓存（避免重复请求）
        audioCacheManager.getCachedAudioFile(audioUrl)?.let { cachedFile ->
            if (audioCacheManager.verifyCachedFile(audioUrl)) {
                updateNotification(title, "从缓存加载")
                serviceScope.launch {
                    playAudioFile(cachedFile, title)
                }
                return
            }
        }

        // 否则：流式播放（边下边播），不再等待整段下载完成
        serviceScope.launch(Dispatchers.Main) {
            try {
                val uri = Uri.parse(audioUrl)
                val headers = buildStreamHeadersForUrl(audioUrl)

                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    setDataSource(this@AudioPlayerService, uri, headers)
                    prepareAsync()

                    setOnPreparedListener {
                        start()
                        this@AudioPlayerService.isPlaying = true
                        currentDurationMs = this.duration.toLong().coerceAtLeast(0L)
                        updateMetadata(title = title, durationMs = currentDurationMs)
                        val restored = restoreProgressForCurrentAudio()
                        if (restored > 0L && restored < currentDurationMs) {
                            runCatching { seekTo(restored) }
                        }
                        updatePlaybackState(playing = true)
                        updateNotification(title, "正在播放")
                        startProgressUpdates()
                    }

                    setOnCompletionListener {
                        handlePlaybackCompletion()
                    }

                    setOnErrorListener { _, what, extra ->
                        Log.e(TAG, "MediaPlayer错误(流式): what=$what, extra=$extra")
                        this@AudioPlayerService.isPlaying = false
                        saveProgressForCurrentAudio(getCurrentPositionMs())
                        stopProgressUpdates()
                        updatePlaybackState(playing = false)
                        updateNotification(title, "播放出错")
                        stopSelf()
                        true
                    }

                    setOnInfoListener { _, what, _ ->
                        when (what) {
                            MediaPlayer.MEDIA_INFO_BUFFERING_START -> updateNotification(title, "缓冲中...")
                            MediaPlayer.MEDIA_INFO_BUFFERING_END -> if (this@AudioPlayerService.isPlaying) {
                                updateNotification(title, "正在播放")
                            }
                        }
                        false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "流式播放音频失败", e)
                updateNotification(title, "播放失败")
                stopSelf()
            }
        }
    }

    private fun buildStreamHeadersForUrl(url: String): Map<String, String> {
        val headers = linkedMapOf(
            "User-Agent" to "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36"
        )
        if (url.contains(".jwznb.com")) {
            headers["Referer"] = "https://myapp.jwznb.com"
        }
        return headers
    }

     private suspend fun getOrDownloadAudio(audioUrl: String, title: String): File? = withContext(Dispatchers.IO) {
         // 1) URL 缓存
         audioCacheManager.getCachedAudioFile(audioUrl)?.let { cachedFile ->
             if (audioCacheManager.verifyCachedFile(audioUrl)) {
                 withContext(Dispatchers.Main) {
                     updateNotification(title, "从缓存加载")
                 }
                 return@withContext cachedFile
             }
         }

         // 2) 下载
         val audioData = downloadAudioData(audioUrl)
         if (audioData == null) {
             return@withContext null
         }

         // 3) 查重（基于内容哈希）
         audioCacheManager.findDuplicateAudioFile(audioData)?.let { duplicateFile ->
             withContext(Dispatchers.Main) {
                 updateNotification(title, "使用已缓存文件")
             }
             return@withContext duplicateFile
         }

         // 4) 写入缓存，失败则落到临时文件
         return@withContext try {
             val cached = audioCacheManager.cacheAudioFile(audioUrl, audioData)
             withContext(Dispatchers.Main) {
                 updateNotification(title, "缓存完成")
             }
             cached
         } catch (e: Exception) {
             Log.e(TAG, "缓存音频失败，使用临时文件", e)
             createTempAudioFile(audioData)
         }
     }

     private suspend fun downloadAudioData(audioUrl: String): ByteArray? = withContext(Dispatchers.IO) {
         try {
             val request = Request.Builder()
                 .url(audioUrl)
                 .build()
             okHttpClient.newCall(request).execute().use { response ->
                 if (!response.isSuccessful) return@withContext null
                 return@withContext response.body?.bytes()
             }
         } catch (e: Exception) {
             Log.e(TAG, "下载音频失败", e)
             return@withContext null
         }
     }

     private fun createTempAudioFile(audioData: ByteArray): File? {
         return try {
             val fileName = "temp_audio_${System.currentTimeMillis()}.m4a"
             val tempFile = File(cacheDir, fileName)
             FileOutputStream(tempFile).use { it.write(audioData) }
             tempFile
         } catch (e: Exception) {
             Log.e(TAG, "创建临时音频文件失败", e)
             null
         }
     }
    
    private fun playLocalAudio(localPath: String, title: String) {
        // 停止当前播放
        releaseMediaPlayer()
        stopProgressUpdates()

        currentAudioUrl = null
        currentLocalPath = localPath
        currentContentUri = null
        currentTitle = title
        currentDurationMs = 0L
        currentIsSavedAudio = false
        savedAudioQueue.clear()
        savedQueueIndex = -1

        requestAudioFocus()
        
        // 开始前台服务
        startForeground(NOTIFICATION_ID, createNotification(title, "正在下载..."))
        
        // 检查缓存并播放音频
        serviceScope.launch {
            try {
                val audioFile = File(localPath)
                if (audioFile.exists()) {
                    playAudioFile(audioFile, title)
                } else {
                    updateNotification(title, "获取音频失败")
                    stopSelf()
                }
            } catch (e: Exception) {
                Log.e(TAG, "播放音频失败", e)
                updateNotification(title, "播放失败")
                stopSelf()
            }
        }
    }
    
    private fun playSavedAudioUri(contentUri: String, title: String) {
        // 停止当前播放
        releaseMediaPlayer()
        stopProgressUpdates()

        currentAudioUrl = null
        currentLocalPath = null
        currentContentUri = contentUri
        currentTitle = title
        currentArtist = ""
        currentCoverUrl = null
        currentCoverBitmap = null
        currentDurationMs = 0L
        currentIsSavedAudio = true
        if (savedAudioQueue.isEmpty() || savedQueueIndex !in savedAudioQueue.indices) {
            refreshSavedAudioQueue(selectedContentUri = contentUri)
        }

        requestAudioFocus()
        
        // 开始前台服务
        startForeground(NOTIFICATION_ID, createNotification(title, "正在播放..."))
        
        // 播放音频
        serviceScope.launch(Dispatchers.Main) {
            try {
                val uri = Uri.parse(contentUri)
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    setDataSource(this@AudioPlayerService, uri)
                    prepareAsync()
                    setOnPreparedListener {
                        start()
                        this@AudioPlayerService.isPlaying = true
                        currentDurationMs = this.duration.toLong().coerceAtLeast(0L)
                        updateMetadata(title = title, durationMs = currentDurationMs)
                        val restored = restoreProgressForCurrentAudio()
                        if (restored > 0L && restored < currentDurationMs) {
                            runCatching { seekTo(restored) }
                        }
                        updatePlaybackState(playing = true)
                        updateNotification(title, "正在播放")
                        startProgressUpdates()

                        // 异步启动网易云听歌识别
                        serviceScope.launch(Dispatchers.IO) {
                            try {
                                val tempFile = File(cacheDir, "identify_saved_${System.currentTimeMillis()}.m4a")
                                contentResolver.openInputStream(uri)?.use { input ->
                                    FileOutputStream(tempFile).use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                if (tempFile.exists() && tempFile.length() > 0) {
                                    val matched = NcmAudioMatcher.matchAudio(this@AudioPlayerService, tempFile, title)
                                    tempFile.delete()
                                    if (matched != null) {
                                        withContext(Dispatchers.Main) {
                                            updateAudioInfo(
                                                newTitle = matched.name,
                                                newArtist = matched.artist,
                                                newCoverUrl = matched.coverUrl
                                            )
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "已保存音频识别异常", e)
                            }
                        }
                    }
                    setOnCompletionListener {
                        handlePlaybackCompletion()
                    }
                    setOnErrorListener { _, _, _ ->
                        this@AudioPlayerService.isPlaying = false
                        saveProgressForCurrentAudio(getCurrentPositionMs())
                        stopProgressUpdates()
                        updatePlaybackState(playing = false)
                        updateNotification(title, "播放出错")
                        stopSelf()
                        true
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "播放已保存语音失败", e)
                updateNotification(title, "播放失败")
                stopSelf()
            }
        }
    }
    
    private suspend fun playAudioFile(audioFile: File, title: String) = withContext(Dispatchers.Main) {
        currentArtist = ""
        currentCoverUrl = null
        currentCoverBitmap = null
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(audioFile.absolutePath)
                prepareAsync()
                
                setOnPreparedListener {
                    start()
                    this@AudioPlayerService.isPlaying = true
                    currentDurationMs = this.duration.toLong().coerceAtLeast(0L)
                    updateMetadata(title = title, durationMs = currentDurationMs)
                    val restored = restoreProgressForCurrentAudio()
                    if (restored > 0L && restored < currentDurationMs) {
                        runCatching { seekTo(restored) }
                    }
                    updatePlaybackState(playing = true)
                    updateNotification(title, "正在播放")
                    startProgressUpdates()
                    Log.d(TAG, "开始播放音频")

                    // 异步启动网易云听歌识别与热更新
                    serviceScope.launch(Dispatchers.IO) {
                        try {
                            val matched = NcmAudioMatcher.matchAudio(this@AudioPlayerService, audioFile, title)
                            if (matched != null) {
                                withContext(Dispatchers.Main) {
                                    updateAudioInfo(
                                        newTitle = matched.name,
                                        newArtist = matched.artist,
                                        newCoverUrl = matched.coverUrl
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "听歌识别失败", e)
                        }
                    }
                }
                
                setOnCompletionListener {
                    Log.d(TAG, "音频播放完成")
                    handlePlaybackCompletion(tempAudioFileToDelete = audioFile)
                }
                
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer错误: what=$what, extra=$extra")
                    this@AudioPlayerService.isPlaying = false
                    saveProgressForCurrentAudio(getCurrentPositionMs())
                    stopProgressUpdates()
                    updatePlaybackState(playing = false)
                    // 只清理临时文件，保留缓存文件
                    if (audioFile.name.startsWith("temp_audio_")) {
                        audioFile.delete()
                        Log.d(TAG, "清理临时音频文件")
                    }
                    updateNotification(title, "播放出错")
                    stopSelf()
                    true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "播放音频文件失败", e)
            saveProgressForCurrentAudio(getCurrentPositionMs())
            stopProgressUpdates()
            // 只清理临时文件，保留缓存文件
            if (audioFile.name.startsWith("temp_audio_")) {
                audioFile.delete()
                Log.d(TAG, "清理临时音频文件")
            }
            updateNotification(title, "播放失败")
            stopSelf()
        }
    }
    
    private fun pauseAudio() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                isPlaying = false
                saveProgressForCurrentAudio(getCurrentPositionMs())
                stopProgressUpdates()
                updatePlaybackState(playing = false)
                updateNotification(currentTitle, "已暂停")
            }
        }
    }
    
    private fun resumeAudio() {
        val player = mediaPlayer ?: return
        if (isPlaying) return
        runCatching {
            player.start()
            isPlaying = true
            updatePlaybackState(playing = true)
            updateNotification(currentTitle, "正在播放")
            startProgressUpdates()
        }
    }
    
    private fun stopAudio() {
        saveProgressForCurrentAudio(getCurrentPositionMs())
        stopProgressUpdates()
        releaseMediaPlayer()
        isPlaying = false
        currentAudioUrl = null
        currentLocalPath = null
        currentContentUri = null
        updatePlaybackState(playing = false)
        @Suppress("DEPRECATION")
        stopForeground(true)
        stopSelf()
    }
    
    private fun releaseMediaPlayer() {
        mediaPlayer?.apply {
            if (this@AudioPlayerService.isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null
    }

    private fun requestAudioFocus() {
        if (audioFocusGranted) return
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener(AudioManager.OnAudioFocusChangeListener { focusChange: Int ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        pauseAudio()
                    }
                })
                .build()
            audioManager.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                { focusChange: Int ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        pauseAudio()
                    }
                },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
        }
        audioFocusGranted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun updatePlaybackState(playing: Boolean) {
        val state = if (playing) {
            PlaybackStateCompat.STATE_PLAYING
        } else {
            PlaybackStateCompat.STATE_PAUSED
        }
        val actions = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_STOP or
            PlaybackStateCompat.ACTION_SEEK_TO or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS

        val position = getCurrentPositionMs()
        val speed = if (playing) 1f else 0f
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(state, position, speed)
                .setActions(actions)
                .build()
        )
    }

    private fun updateMetadata(title: String, durationMs: Long) {
        val builder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs)

        if (currentArtist.isNotBlank()) {
            builder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentArtist)
        }
        if (!currentCoverUrl.isNullOrBlank()) {
            builder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, currentCoverUrl)
            builder.putString(MediaMetadataCompat.METADATA_KEY_ART_URI, currentCoverUrl)
        }
        currentCoverBitmap?.let {
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, it)
        }
        currentContentUri?.let {
            builder.putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, it)
        }
        currentAudioUrl?.let {
            builder.putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, it)
        }
        
        mediaSession.setMetadata(builder.build())
    }

    /**
     * 听歌识曲识别成功后的热更新函数
     * 实时覆盖正在播放的音频标题、歌手、封面图，并热更新通知栏与播放列表
     */
    fun updateAudioInfo(newTitle: String, newArtist: String = "", newCoverUrl: String? = null) {
        Log.d(TAG, "听歌识曲热更新: title=$newTitle, artist=$newArtist, coverUrl=$newCoverUrl")
        currentTitle = newTitle
        currentArtist = newArtist
        currentCoverUrl = newCoverUrl

        updateMetadata(title = newTitle, durationMs = currentDurationMs)

        // 异步下载封面 Bitmap 供系统通知栏 LargeIcon 及 MediaSession 展示
        if (!newCoverUrl.isNullOrBlank() && currentCoverBitmap == null) {
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val req = Request.Builder().url(newCoverUrl).build()
                    okHttpClient.newCall(req).execute().use { resp ->
                        val bytes = resp.body?.bytes()
                        if (bytes != null) {
                            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (bmp != null) {
                                currentCoverBitmap = bmp
                                val updatedMeta = MediaMetadataCompat.Builder(mediaSession.controller.metadata)
                                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bmp)
                                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, bmp)
                                    .build()
                                mediaSession.setMetadata(updatedMeta)
                                withContext(Dispatchers.Main) {
                                    val status = if (isPlaying) "正在播放" else "已暂停"
                                    val subtext = if (currentArtist.isNotBlank()) "$currentArtist · $status" else status
                                    updateNotification(currentTitle, subtext)
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        val status = if (isPlaying) "正在播放" else "已暂停"
        val subtext = if (newArtist.isNotBlank()) "$newArtist · $status" else status
        updateNotification(newTitle, subtext)

        // 同步更新 Room 播放列表数据库项名称
        serviceScope.launch(Dispatchers.IO) {
            try {
                val dao = AppDatabase.getDatabase(this@AudioPlayerService).audioPlaylistDao()
                val activePlaylistId = getActivePlaylistId(this@AudioPlayerService)
                val items = dao.getItemsForPlaylistSync(activePlaylistId)
                val matchItem = items.firstOrNull {
                    (currentAudioUrl != null && it.url == currentAudioUrl) ||
                    (currentContentUri != null && it.url == currentContentUri)
                }
                if (matchItem != null && matchItem.title != newTitle) {
                    dao.updateItem(matchItem.copy(title = newTitle))
                }
            } catch (_: Exception) {}
        }
    }

    private fun getCurrentPositionMs(): Long {
        val pos = runCatching { mediaPlayer?.currentPosition?.toLong() ?: 0L }.getOrDefault(0L)
        return pos.coerceAtLeast(0L)
    }

    private fun seekTo(positionMs: Long) {
        val player = mediaPlayer ?: return
        val safePos = positionMs.coerceAtLeast(0L).coerceAtMost(currentDurationMs.takeIf { it > 0 } ?: Long.MAX_VALUE)
        runCatching { player.seekTo(safePos.toInt()) }
        saveProgressForCurrentAudio(safePos)
        updatePlaybackState(playing = isPlaying)
        updateNotification(currentTitle, if (isPlaying) "正在播放" else "已暂停")
    }

    private fun progressKeyFor(audioUrl: String): String = "progress_$audioUrl"

    private fun progressKeyForLocalPath(localPath: String): String = "progress_local_$localPath"

    private fun progressKeyForContentUri(contentUri: String): String = "progress_uri_$contentUri"

    private fun saveProgressForCurrentAudio(positionMs: Long) {
        val safePos = positionMs.coerceAtLeast(0L)
        val url = currentAudioUrl
        val localPath = currentLocalPath
        val uri = currentContentUri
        when {
            !url.isNullOrBlank() -> progressPrefs.edit().putLong(progressKeyFor(url), safePos).apply()
            !localPath.isNullOrBlank() -> progressPrefs.edit().putLong(progressKeyForLocalPath(localPath), safePos).apply()
            !uri.isNullOrBlank() -> progressPrefs.edit().putLong(progressKeyForContentUri(uri), safePos).apply()
        }
    }

    private fun restoreProgressForCurrentAudio(): Long {
        val url = currentAudioUrl
        val localPath = currentLocalPath
        val uri = currentContentUri
        val value = when {
            !url.isNullOrBlank() -> progressPrefs.getLong(progressKeyFor(url), 0L)
            !localPath.isNullOrBlank() -> progressPrefs.getLong(progressKeyForLocalPath(localPath), 0L)
            !uri.isNullOrBlank() -> progressPrefs.getLong(progressKeyForContentUri(uri), 0L)
            else -> 0L
        }
        return value.coerceAtLeast(0L)
    }

    private fun startProgressUpdates() {
        stopProgressUpdates()
        progressJob = serviceScope.launch {
            while (isActive) {
                if (isPlaying) {
                    val pos = getCurrentPositionMs()
                    saveProgressForCurrentAudio(pos)
                }
                updatePlaybackState(playing = isPlaying)
                val content = if (isPlaying) "正在播放" else "已暂停"
                updateNotification(currentTitle, content)
                delay(1000)
            }
        }
    }

    private fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }
    
    private fun createNotification(title: String, content: String): Notification {
        // 检查是否为 Flyme 系统
        if (isFlyme()) {
            return createFlymeLiveNotification(title, content)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        fun servicePendingIntent(action: String): PendingIntent {
            val i = Intent(this, AudioPlayerService::class.java).apply { this.action = action }
            return PendingIntent.getService(
                this,
                action.hashCode(),
                i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val prevAction = NotificationCompat.Action(
            android.R.drawable.ic_media_previous,
            "上一条",
            servicePendingIntent(ACTION_PREV)
        )

        val playPauseAction = if (isPlaying) {
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause,
                "暂停",
                servicePendingIntent(ACTION_PAUSE)
            )
        } else {
            NotificationCompat.Action(
                android.R.drawable.ic_media_play,
                "播放",
                servicePendingIntent(ACTION_PLAY)
            )
        }

        val stopAction = NotificationCompat.Action(
            android.R.drawable.ic_menu_close_clear_cancel,
            "停止",
            servicePendingIntent(ACTION_STOP)
        )

        val nextAction = NotificationCompat.Action(
            android.R.drawable.ic_media_next,
            "下一条",
            servicePendingIntent(ACTION_NEXT)
        )
        
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .addAction(prevAction)
            .addAction(playPauseAction)
            .addAction(stopAction)
            .addAction(nextAction)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 3)
            )
            .setOngoing(true)

        currentCoverBitmap?.let {
            builder.setLargeIcon(it)
        }

        val duration = currentDurationMs
        val position = getCurrentPositionMs()
        if (duration > 0L) {
            builder.setProgress(duration.toInt(), position.coerceAtMost(duration).toInt(), false)
        }

        return builder.build()
    }

    private fun isFlyme(): Boolean {
        val manufacturer = Build.MANUFACTURER ?: ""
        val display = Build.DISPLAY ?: ""
        return manufacturer.contains("meizu", ignoreCase = true) || display.contains("flyme", ignoreCase = true)
    }

    private fun createFlymeLiveNotification(title: String, content: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        fun servicePendingIntent(action: String): PendingIntent {
            val i = Intent(this, AudioPlayerService::class.java).apply { this.action = action }
            return PendingIntent.getService(
                this,
                action.hashCode(),
                i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        // 1. 创建胶囊视图
        val capsuleView = android.widget.RemoteViews(packageName, R.layout.layout_live_audio_capsule).apply {
            setTextViewText(R.id.tv_capsule_title, "$title - $content")
            setImageViewResource(R.id.iv_capsule_icon, if (isPlaying) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause)
        }

        // 2. 创建展开视图
        val expandedView = android.widget.RemoteViews(packageName, R.layout.layout_live_audio_expanded).apply {
            setTextViewText(R.id.tv_title, title)
            setTextViewText(R.id.tv_artist, content)
            
            // 设置按钮点击事件
            setOnClickPendingIntent(R.id.btn_prev, servicePendingIntent(ACTION_PREV))
            setOnClickPendingIntent(R.id.btn_next, servicePendingIntent(ACTION_NEXT))
            
            val playPauseIntent = if (isPlaying) servicePendingIntent(ACTION_PAUSE) else servicePendingIntent(ACTION_PLAY)
            setOnClickPendingIntent(R.id.btn_play_pause, playPauseIntent)
            
            // 更新按钮图标
            setImageViewResource(R.id.btn_play_pause, if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
        }

        // 3. 构建 Flyme Live Bundle
        val capsuleBundle = Bundle().apply {
            putInt("notification.live.capsuleStatus", 1)
            putInt("notification.live.capsuleType", 5)
            // 胶囊文本，当 layout_live_audio_capsule 未生效时可能使用此值
            putString("notification.live.capsuleContent", content)
            // 胶囊自定义视图
            putParcelable("notification.live.capsule.content.remote.view", capsuleView)
            
            // 胶囊背景颜色 (可选)
            // putInt("notification.live.capsuleBgColor", getColor(R.color.some_color))
        }

        val liveBundle = Bundle().apply {
            putBoolean("is_live", true)
            putInt("notification.live.operation", 0)
            putInt("notification.live.type", 2)
            putBundle("notification.live.capsule", capsuleBundle)
        }

        // 4. 构建通知
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(pendingIntent)
            .setCustomContentView(expandedView) // 设置展开后的自定义视图
            .setCustomBigContentView(expandedView) // 确保展开状态也使用该视图
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addExtras(liveBundle)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        return builder.build()
    }
    
    private fun updateNotification(title: String, content: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(title, content))
    }
    
    /**
     * 检查是否正在播放
     */
    fun isPlaying(): Boolean = isPlaying
    
    /**
     * 获取当前播放的音频URL
     */
    fun getCurrentAudioUrl(): String? = currentAudioUrl
    
    /**
     * 获取当前播放进度（毫秒）
     */
    fun getCurrentPosition(): Long = getCurrentPositionMs()

    /**
     * 获取当前音频总时长（毫秒）
     */
    fun getCurrentDurationMs(): Long = currentDurationMs
}
