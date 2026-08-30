package com.yhchat.canary.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.yhchat.canary.data.local.AppDatabase
import com.yhchat.canary.data.local.DownloadedFileRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 文件下载服务
 * 支持带进度的后台文件下载，并显示通知
 */
class FileDownloadService : Service() {

    companion object {
        private const val TAG = "FileDownloadService"
        private const val NOTIFICATION_CHANNEL_ID = "file_download_channel"
        private const val NOTIFICATION_ID = 2001
        
        const val ACTION_DOWNLOAD = "action_download"
        const val ACTION_CANCEL = "action_cancel"
        const val EXTRA_FILE_URL = "extra_file_url"
        const val EXTRA_FILE_NAME = "extra_file_name"
        const val EXTRA_FILE_SIZE = "extra_file_size"
        const val EXTRA_AUTO_OPEN = "extra_auto_open"
        const val EXTRA_DOWNLOAD_ID = "extra_download_id"
        const val EXTRA_MSG_ID = "extra_msg_id"
        
        // 下载进度回调接口
        interface DownloadProgressCallback {
            fun onProgress(downloadId: String, progress: Int, total: Int)
            fun onCompleted(downloadId: String, filePath: String)
            fun onError(downloadId: String, error: String)
            fun onCancelled(downloadId: String)
        }
        
        private val progressCallbacks = mutableMapOf<String, DownloadProgressCallback>()
        
        /**
         * 启动文件下载
         */
        fun startDownload(
            context: Context,
            fileUrl: String,
            fileName: String,
            fileSize: Long,
            msgId: String? = null,
            autoOpen: Boolean = false,
            downloadId: String = UUID.randomUUID().toString(),
            progressCallback: DownloadProgressCallback? = null
        ): String {
            if (progressCallback != null) {
                progressCallbacks[downloadId] = progressCallback
            }
            
            val intent = Intent(context, FileDownloadService::class.java).apply {
                action = ACTION_DOWNLOAD
                putExtra(EXTRA_FILE_URL, fileUrl)
                putExtra(EXTRA_FILE_NAME, fileName)
                putExtra(EXTRA_FILE_SIZE, fileSize)
                putExtra(EXTRA_AUTO_OPEN, autoOpen)
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
                putExtra(EXTRA_MSG_ID, msgId)
            }
            ContextCompat.startForegroundService(context, intent)
            return downloadId
        }
        
        /**
         * 取消文件下载
         */
        fun cancelDownload(context: Context, downloadId: String) {
            val intent = Intent(context, FileDownloadService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            context.startService(intent)
        }
    }
    
    private val binder = FileDownloadBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var notificationManager: NotificationManager? = null
    private val activeDownloads = mutableMapOf<String, kotlinx.coroutines.Job>()
    private val cancelledDownloads = mutableSetOf<String>()
    
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("Referer", "https://myapp.jwznb.com")
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
                .build()
            chain.proceed(request)
        }
        .build()
    
    inner class FileDownloadBinder : Binder() {
        fun getService(): FileDownloadService = this@FileDownloadService
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        // Log.d(TAG, "FileDownloadService created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DOWNLOAD -> {
                val fileUrl = intent.getStringExtra(EXTRA_FILE_URL)
                val fileName = intent.getStringExtra(EXTRA_FILE_NAME)
                val fileSize = intent.getLongExtra(EXTRA_FILE_SIZE, 0L)
                val autoOpen = intent.getBooleanExtra(EXTRA_AUTO_OPEN, false)
                val downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID) ?: UUID.randomUUID().toString()
                val msgId = intent.getStringExtra(EXTRA_MSG_ID)
                
                if (!fileUrl.isNullOrEmpty() && !fileName.isNullOrEmpty()) {
                    startDownloadFile(fileUrl, fileName, fileSize, msgId, autoOpen, downloadId)
                } else {
                    Log.e(TAG, "Invalid download parameters")
                    stopSelf()
                }
            }
            ACTION_CANCEL -> {
                val downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID)
                if (!downloadId.isNullOrEmpty()) {
                    cancelDownload(downloadId)
                }
            }
        }
        
        return START_NOT_STICKY
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "文件下载",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示文件下载进度"
                setSound(null, null)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }
    
    private fun cancelDownload(downloadId: String) {
        // Log.d(TAG, "Cancelling download: $downloadId")
        cancelledDownloads.add(downloadId)
        activeDownloads[downloadId]?.cancel()
        activeDownloads.remove(downloadId)
        progressCallbacks[downloadId]?.onCancelled(downloadId)
        progressCallbacks.remove(downloadId)
        
        updateNotification("下载已取消", "下载已取消", 0, 0, true)
        stopSelf()
    }
    
    private fun startDownloadFile(
        fileUrl: String,
        fileName: String,
        fileSize: Long,
        msgId: String?,
        autoOpen: Boolean,
        downloadId: String
    ) {
        // Log.d(TAG, "Starting download: $fileName from $fileUrl (autoOpen=$autoOpen, msgId=$msgId)")

        val downloadTarget = createDownloadTarget(fileName)
        if (downloadTarget == null) {
            updateNotification(fileName, "下载失败: 无法创建下载目录", 0, 0, true)
            stopSelf()
            return
        }
        
        // 开始前台服务
        startForeground(NOTIFICATION_ID, createNotification(fileName, "准备下载...", 0, 0))
        
        // 在后台线程下载文件
        val downloadJob = serviceScope.launch(Dispatchers.IO) {
            try {
                if (!msgId.isNullOrBlank()) {
                    val existingFile = resolveExistingFileByMsgId(
                        msgId = msgId,
                        fallbackFileName = fileName,
                        expectedFileSize = fileSize
                    )
                    if (existingFile != null) {
                        serviceScope.launch(Dispatchers.Main) {
                            updateNotification(fileName, "文件已存在，直接打开", 100, 100, true)
                            progressCallbacks[downloadId]?.onCompleted(downloadId, existingFile.absolutePath)
                            openFile(existingFile)
                        }
                        return@launch
                    }
                }

                // 检查是否已取消
                if (cancelledDownloads.contains(downloadId)) {
                    // Log.d(TAG, "Download cancelled before start: $downloadId")
                    return@launch
                }
                
                val completedPath = when (downloadTarget) {
                    is DownloadTarget.MediaStore -> {
                        downloadFileWithProgressToUri(fileUrl, downloadTarget.uri, fileName, fileSize, downloadId)
                        markMediaStoreComplete(downloadTarget.uri)
                        downloadTarget.uri.toString()
                    }
                    is DownloadTarget.FileTarget -> {
                        val downloadDir = downloadTarget.file.parentFile ?: downloadTarget.file
                        val targetFile = File(downloadDir, fileName)
                        downloadFileWithProgress(fileUrl, targetFile, fileName, fileSize, downloadId)
                        // Log.d(TAG, "Download completed: ${targetFile.absolutePath}")

                        serviceScope.launch(Dispatchers.Main) {
                            updateNotification(fileName, "下载完成 - ${targetFile.name}", 100, 100, true)
                            Toast.makeText(this@FileDownloadService, "文件下载完成: ${targetFile.name}", Toast.LENGTH_LONG).show()
                            progressCallbacks[downloadId]?.onCompleted(downloadId, targetFile.absolutePath)
                        }
                        targetFile.absolutePath
                    }
                }

                if (!msgId.isNullOrBlank()) {
                    runCatching {
                        AppDatabase.getDatabase(this@FileDownloadService)
                            .downloadedFileRecordDao()
                            .upsert(
                                DownloadedFileRecord(
                                    msgId = msgId,
                                    fileName = fileName,
                                    fileSize = fileSize
                                )
                            )
                    }
                }

                serviceScope.launch(Dispatchers.Main) {
                    updateNotification(
                        fileName,
                        "下载完成",
                        100,
                        100,
                        true
                    )
                    progressCallbacks[downloadId]?.onCompleted(downloadId, completedPath)
                }

                // 如果需要自动打开文件
                if (autoOpen) {
                    serviceScope.launch(Dispatchers.Main) {
                        when (downloadTarget) {
                            is DownloadTarget.MediaStore -> openFileUri(downloadTarget.uri)
                            is DownloadTarget.FileTarget -> openFile(File(completedPath))
                        }
                    }
                }
                
                // 延迟一段时间后停止服务
                serviceScope.launch(Dispatchers.Main) {
                    kotlinx.coroutines.delay(3000)
                    stopSelf()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                if (downloadTarget is DownloadTarget.MediaStore) {
                    runCatching { contentResolver.delete(downloadTarget.uri, null, null) }
                }
                launch(Dispatchers.Main) {
                    if (!cancelledDownloads.contains(downloadId)) {
                        updateNotification(fileName, "下载失败: ${e.message}", 0, 0, true)
                        Toast.makeText(this@FileDownloadService, "下载失败: ${e.message}", Toast.LENGTH_LONG).show()
                        progressCallbacks[downloadId]?.onError(downloadId, e.message ?: "Unknown error")
                    }
                    stopSelf()
                }
            } finally {
                activeDownloads.remove(downloadId)
                progressCallbacks.remove(downloadId)
            }
        }
        
        activeDownloads[downloadId] = downloadJob
    }

    private sealed class DownloadTarget {
        data class FileTarget(val file: File) : DownloadTarget()
        data class MediaStore(val uri: Uri) : DownloadTarget()
    }

    private fun createDownloadTarget(fileName: String): DownloadTarget? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, guessMimeType(fileName))
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/yhchat/")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) DownloadTarget.MediaStore(uri) else null
        } else {
            val downloadDir = File("/storage/emulated/0/Download/yhchat/")
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }
            if (!downloadDir.exists() || !downloadDir.isDirectory) {
                Log.e(TAG, "❌ Failed to create download directory: ${downloadDir.absolutePath}")
                null
            } else {
                DownloadTarget.FileTarget(File(downloadDir, fileName))
            }
        }
    }

    private fun guessMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }

    private fun markMediaStoreComplete(uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            contentResolver.update(uri, values, null, null)
        }
    }

    private suspend fun downloadFileWithProgressToUri(
        fileUrl: String,
        targetUri: Uri,
        fileName: String,
        expectedSize: Long,
        downloadId: String
    ) {
        val request = Request.Builder()
            .url(fileUrl)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}")
            }

            val responseBody = response.body ?: throw Exception("Empty response body")
            val totalBytes = responseBody.contentLength().takeIf { it > 0 } ?: expectedSize

            // Log.d(TAG, "Downloading ${fileName}, total size: $totalBytes bytes")

            responseBody.byteStream().use { inputStream ->
                val outputStream = contentResolver.openOutputStream(targetUri)
                    ?: throw Exception("无法创建下载文件")
                outputStream.use { stream ->
                    writeStreamWithProgress(stream, inputStream, fileName, totalBytes, downloadId)
                }
            }
        }
    }

    private fun writeStreamWithProgress(
        outputStream: OutputStream,
        inputStream: java.io.InputStream,
        fileName: String,
        totalBytes: Long,
        downloadId: String
    ) {
        val buffer = ByteArray(8192)
        var downloadedBytes = 0L
        var lastProgressUpdate = 0L

        serviceScope.launch(Dispatchers.Main) {
            updateNotification(fileName, "下载中...", 0, if (totalBytes > 0) 100 else 0)
        }

        while (true) {
            if (cancelledDownloads.contains(downloadId)) {
                // Log.d(TAG, "Download cancelled during progress: $downloadId")
                throw Exception("Download cancelled")
            }

            val bytesRead = inputStream.read(buffer)
            if (bytesRead == -1) break

            outputStream.write(buffer, 0, bytesRead)
            downloadedBytes += bytesRead

            val currentTime = System.currentTimeMillis()
            if (currentTime - lastProgressUpdate > 200 || downloadedBytes == totalBytes) {
                lastProgressUpdate = currentTime
                val percentage = if (totalBytes > 0) {
                    ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                } else {
                    0
                }
                serviceScope.launch(Dispatchers.Main) {
                    val progressText = if (totalBytes > 0) {
                        "下载中... $percentage% (${formatFileSize(downloadedBytes)}/${formatFileSize(totalBytes)})"
                    } else {
                        "下载中... ${formatFileSize(downloadedBytes)}"
                    }
                    updateNotification(fileName, progressText, percentage, 100)
                    progressCallbacks[downloadId]?.onProgress(downloadId, percentage, 100)
                }
            }
        }
    }
    
    private suspend fun downloadFileWithProgress(
        fileUrl: String,
        targetFile: File,
        fileName: String,
        expectedSize: Long,
        downloadId: String
    ) {
        val request = Request.Builder()
            .url(fileUrl)
            .build()
        
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}")
            }
            
            val responseBody = response.body ?: throw Exception("Empty response body")
            val totalBytes = responseBody.contentLength().takeIf { it > 0 } ?: expectedSize
            
            // Log.d(TAG, "Downloading ${targetFile.name}, total size: $totalBytes bytes")
            
            responseBody.byteStream().use { inputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var downloadedBytes = 0L
                    var lastProgressUpdate = 0L
                    
                    serviceScope.launch(Dispatchers.Main) {
                        updateNotification(fileName, "下载中...", 0, totalBytes.toInt())
                    }
                    
                    while (true) {
                        // 检查是否已取消
                        if (cancelledDownloads.contains(downloadId)) {
                            // Log.d(TAG, "Download cancelled during progress: $downloadId")
                            throw Exception("Download cancelled")
                        }
                        
                        val bytesRead = inputStream.read(buffer)
                        if (bytesRead == -1) break
                        
                        outputStream.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        
                        // 限制进度更新频率，避免过于频繁
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastProgressUpdate > 200 || downloadedBytes == totalBytes) {
                            lastProgressUpdate = currentTime
                            
                            // 使用百分比计算进度，避免大文件时的整数溢出
                            val percentage = if (totalBytes > 0) {
                                ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                            } else {
                                0
                            }
                            
                            serviceScope.launch(Dispatchers.Main) {
                                val progressText = if (totalBytes > 0) {
                                    "下载中... $percentage% (${formatFileSize(downloadedBytes)}/${formatFileSize(totalBytes)})"
                                } else {
                                    "下载中... ${formatFileSize(downloadedBytes)}"
                                }
                                updateNotification(fileName, progressText, percentage, 100)
                                // 传递百分比而不是原始字节数
                                progressCallbacks[downloadId]?.onProgress(downloadId, percentage, 100)
                            }
                        }
                    }
                }
            }
            
            // Log.d(TAG, "Download to temp file completed: ${targetFile.absolutePath}")
        }
    }
    
    private suspend fun resolveExistingFileByMsgId(
        msgId: String,
        fallbackFileName: String,
        expectedFileSize: Long
    ): File? {
        val dao = AppDatabase.getDatabase(this).downloadedFileRecordDao()
        val record = dao.getByMsgId(msgId) ?: return null
        val expectedName = record.fileName.takeIf { it.isNotBlank() } ?: fallbackFileName
        val expectedSize = record.fileSize.takeIf { it > 0L } ?: return null
        val dir = File("/storage/emulated/0/Download/yhchat/")
        if (!dir.exists() || !dir.isDirectory) return null
        val direct = File(dir, expectedName)
        if (direct.exists() && direct.isFile) {
            if (direct.name == expectedName && direct.length() == expectedSize) {
                return direct
            }
            return null
        }
        return null
    }
    
    /**
     * 打开文件（使用外部应用）
     */
    private fun openFile(file: File) {
        try {
            if (file.extension.equals("exe", ignoreCase = true)) {
                shareFile(file)
                return
            }
            val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    file
                )
            } else {
                Uri.fromFile(file)
            }
            
            val mimeType = when (file.extension.lowercase()) {
                "mp4", "avi", "mkv", "mov", "wmv", "flv", "webm" -> "video/*"
                "mp3", "wav", "flac", "aac", "ogg" -> "audio/*"
                "jpg", "jpeg", "png", "gif", "bmp", "webp" -> "image/*"
                "pdf" -> "application/pdf"
                "txt" -> "text/plain"
                "zip", "rar", "7z" -> "application/zip"
                else -> "*/*"
            }
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            // 检查是否有应用可以处理这个Intent
            val packageManager = packageManager
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                // Log.d(TAG, "Opened file with external app: ${file.name}")
            } else {
                // 没有应用可以打开，尝试使用选择器
                val chooserIntent = Intent.createChooser(intent, "选择应用打开").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(chooserIntent)
                // Log.d(TAG, "Opened file chooser for: ${file.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open file: ${file.name}", e)
            serviceScope.launch(Dispatchers.Main) {
                Toast.makeText(this@FileDownloadService, "无法打开文件: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun shareFile(file: File) {
        try {
            val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            } else {
                Uri.fromFile(file)
            }
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "分享文件").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share file: ${file.name}", e)
            serviceScope.launch(Dispatchers.Main) {
                Toast.makeText(this@FileDownloadService, "无法分享文件: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openFileUri(uri: Uri) {
        try {
            val mimeType = contentResolver.getType(uri) ?: "*/*"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val packageManager = packageManager
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                // Log.d(TAG, "Opened file uri with external app: $uri")
            } else {
                val chooserIntent = Intent.createChooser(intent, "选择应用打开").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(chooserIntent)
                // Log.d(TAG, "Opened file chooser for uri: $uri")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open file uri: $uri", e)
            serviceScope.launch(Dispatchers.Main) {
                Toast.makeText(this@FileDownloadService, "无法打开文件: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun createNotification(
        fileName: String,
        progressText: String,
        progress: Int,
        maxProgress: Int
    ): android.app.Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(fileName)
            .setContentText(progressText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(maxProgress, progress, maxProgress == 0)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
    }
    
    private fun updateNotification(
        fileName: String,
        progressText: String,
        progress: Int,
        maxProgress: Int,
        isCompleted: Boolean = false
    ) {
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(fileName)
            .setContentText(progressText)
            .setSmallIcon(
                if (isCompleted) android.R.drawable.stat_sys_download_done 
                else android.R.drawable.stat_sys_download
            )
            .setProgress(maxProgress, progress, maxProgress == 0 && !isCompleted)
            .setOngoing(!isCompleted)
            .setAutoCancel(isCompleted)
            .build()
        
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }
    
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> "${bytes / 1024}KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)}MB"
            else -> "${bytes / (1024 * 1024 * 1024)}GB"
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Log.d(TAG, "FileDownloadService destroyed")
    }
}
