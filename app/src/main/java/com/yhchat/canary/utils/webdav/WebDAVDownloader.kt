package com.yhchat.canary.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import com.yhchat.canary.data.model.WebDAVFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * WebDAV 文件下载工具类
 */
object WebDAVDownloader {
    
    private const val TAG = "WebDAVDownloader"
    
    /**
     * 使用 Sardine 下载WebDAV文件到本地（推荐使用）
     * @param context 上下文
     * @param file WebDAV文件信息
     * @param onProgress 下载进度回调 (已下载字节数, 总字节数)
     * @param onSuccess 下载成功回调 (本地文件路径)
     * @param onError 下载失败回调 (错误信息)
     */
    suspend fun downloadFile(
        context: Context,
        file: WebDAVFile,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
        onSuccess: (localPath: String) -> Unit,
        onError: (error: String) -> Unit
    ) {
        // 直接使用 SardineWebDAVClient 的下载功能
        SardineWebDAVClient.downloadFile(
            context = context,
            file = file,
            onProgress = onProgress,
            onSuccess = onSuccess,
            onError = onError
        )
    }
    
    /**
     * 旧版下载方法（使用原始 OkHttp 实现）
     * @param context 上下文
     * @param file WebDAV文件信息
     * @param onProgress 下载进度回调 (已下载字节数, 总字节数)
     * @param onSuccess 下载成功回调 (本地文件路径)
     * @param onError 下载失败回调 (错误信息)
     */
    suspend fun downloadFileOld(
        context: Context,
        file: WebDAVFile,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
        onSuccess: (localPath: String) -> Unit,
        onError: (error: String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始下载文件: ${file.name}")
            
            // 创建下载目录
            val downloadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "yhchat")
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }
            
            // 生成本地文件名（避免重名）
            val localFile = generateUniqueFile(downloadDir, file.name)
            
            // 构建下载URL
            val downloadUrl = buildDownloadUrl(file)
            Log.d(TAG, "下载URL: $downloadUrl")
            
            // 创建HTTP客户端
            val client = OkHttpClient()
            
            // 创建下载请求
            val authHeader = Credentials.basic(
                file.mountSetting.webdavUserName,
                file.mountSetting.webdavPassword
            )
            
            Log.d(TAG, "WebDAV认证信息:")
            Log.d(TAG, "用户名: ${file.mountSetting.webdavUserName}")
            Log.d(TAG, "密码长度: ${file.mountSetting.webdavPassword.length}")
            Log.d(TAG, "Authorization头: $authHeader")
            
            val request = Request.Builder()
                .url(downloadUrl)
                .addHeader("authorization", authHeader)
                .build()
            
            // 执行下载
            Log.d(TAG, "发送下载请求...")
            val response = client.newCall(request).execute()
            Log.d(TAG, "收到响应: ${response.code} ${response.message}")
            
            if (!response.isSuccessful) {
                Log.e(TAG, "下载请求失败: HTTP ${response.code} ${response.message}")
                withContext(Dispatchers.Main) {
                    onError("下载失败: HTTP ${response.code} ${response.message}")
                }
                return@withContext
            }
            
            val responseBody = response.body
            if (responseBody == null) {
                Log.e(TAG, "响应体为空")
                withContext(Dispatchers.Main) {
                    onError("下载失败: 响应体为空")
                }
                return@withContext
            }
            
            val totalBytes = responseBody.contentLength()
            var downloadedBytes = 0L
            Log.d(TAG, "开始下载，文件大小: $totalBytes 字节")
            
            // 写入本地文件
            FileOutputStream(localFile).use { output ->
                responseBody.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        // 检查协程是否被取消
                        ensureActive()
                        
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        
                        // 更新进度
                        withContext(Dispatchers.Main) {
                            onProgress(downloadedBytes, totalBytes)
                        }
                    }
                }
            }
            
            Log.d(TAG, "文件下载完成: ${localFile.absolutePath}")
            
            // 下载成功
            withContext(Dispatchers.Main) {
                onSuccess(localFile.absolutePath)
            }
            
        } catch (e: CancellationException) {
            Log.d(TAG, "下载被取消: ${file.name}")
            // 协程取消时不调用错误回调，直接重新抛出异常
            throw e
        } catch (e: IOException) {
            Log.e(TAG, "下载文件失败", e)
            withContext(Dispatchers.Main) {
                onError("下载失败: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "下载文件失败", e)
            withContext(Dispatchers.Main) {
                onError("下载失败: ${e.message}")
            }
        }
    }
    
    /**
     * 构建下载URL
     */
    private fun buildDownloadUrl(file: WebDAVFile): String {
        val baseUrl = file.mountSetting.webdavUrl.trimEnd('/')
        val filePath = file.path.trimStart('/')
        
        // 对路径进行URL编码，但保留路径分隔符
        val encodedPath = filePath.split("/").joinToString("/") { segment ->
            if (segment.isNotEmpty()) {
                URLEncoder.encode(segment, StandardCharsets.UTF_8.toString())
                    .replace("+", "%20")
            } else {
                segment
            }
        }
        
        val fullUrl = "$baseUrl/$encodedPath".replace("//", "/").replace("http:/", "http://").replace("https:/", "https://")
        Log.d(TAG, "编码前路径: $filePath")
        Log.d(TAG, "编码后路径: $encodedPath")
        Log.d(TAG, "最终URL: $fullUrl")
        
        return fullUrl
    }
    
    /**
     * 生成唯一的文件名（避免重名）
     */
    private fun generateUniqueFile(dir: File, fileName: String): File {
        var file = File(dir, fileName)
        var counter = 1
        
        while (file.exists()) {
            val nameWithoutExt = fileName.substringBeforeLast('.')
            val extension = if (fileName.contains('.')) {
                ".${fileName.substringAfterLast('.')}"
            } else {
                ""
            }
            
            file = File(dir, "${nameWithoutExt}(${counter})${extension}")
            counter++
        }
        
        return file
    }
}
