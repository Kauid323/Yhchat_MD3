package com.yhchat.canary.utils

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.thegrizzlylabs.sardineandroid.DavResource
import com.thegrizzlylabs.sardineandroid.Sardine
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import com.yhchat.canary.data.model.MountSetting
import com.yhchat.canary.data.model.WebDAVFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * 基于 Sardine 库的 WebDAV 客户端工具类
 * 保留原有的解密逻辑，使用更成熟的 WebDAV 库实现完整功能
 */
object SardineWebDAVClient {
    
    private const val TAG = "SardineWebDAVClient"
    private const val MIN_MULTI_THREAD_SIZE = 4 * 1024 * 1024L // 4MB
    private const val DEFAULT_THREAD_COUNT = 4
    private const val MIN_CHUNK_SIZE = 1 * 1024 * 1024L // 1MB
    private val httpClient by lazy { OkHttpClient() }
    
    /**
     * 创建 Sardine WebDAV 客户端
     * @param mountSetting 已解密的挂载设置（包含解密后的用户名和密码）
     * @return Sardine 客户端实例
     */
    private fun createSardineClient(mountSetting: MountSetting): Sardine {
        return OkHttpSardine().apply {
            setCredentials(mountSetting.webdavUserName, mountSetting.webdavPassword)
        }
    }
    
    private fun resolvePath(mountSetting: MountSetting, relativePath: String): String {
        val normalizedRoot = mountSetting.webdavRootPath.trim('/')
        val normalizedRelative = relativePath.trim('/').takeIf { it.isNotEmpty() }?.split('/') ?: emptyList()
        val baseUrl = mountSetting.webdavUrl.trimEnd('/')
        val basePath = Uri.parse(baseUrl).path?.trim('/') ?: ""
        val baseSegments = if (basePath.isEmpty()) emptyList() else basePath.split('/')
        val baseContainsRoot = normalizedRoot.isNotEmpty() && baseSegments.lastOrNull() == normalizedRoot
        val segments = mutableListOf<String>()
        if (normalizedRoot.isNotEmpty() && !baseContainsRoot) {
            segments.add(normalizedRoot)
        }
        segments.addAll(normalizedRelative)
        return segments.joinToString("/")
    }
    
    /**
     * 测试 WebDAV 连接
     * @param mountSetting 已解密的挂载设置
     * @return 连接是否成功
     */
    suspend fun testConnection(mountSetting: MountSetting): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "测试WebDAV连接: ${mountSetting.webdavUrl}")
            
            val sardine = createSardineClient(mountSetting)
            val testUrl = buildUrl(mountSetting.webdavUrl, mountSetting.webdavRootPath)
            
            // 尝试列出根目录来测试连接
            sardine.exists(testUrl)
            
            Log.d(TAG, "WebDAV连接测试成功")
            true
        } catch (e: Exception) {
            Log.e(TAG, "WebDAV连接测试失败", e)
            false
        }
    }
    
    /**
     * 列出指定路径下的文件和文件夹
     * @param mountSetting 已解密的挂载设置
     * @param relativePath 相对于根路径的路径
     * @return WebDAV文件列表
     */
    suspend fun listFiles(
        mountSetting: MountSetting, 
        relativePath: String = ""
    ): Result<List<WebDAVFile>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "列出文件: ${mountSetting.mountName} - $relativePath")
            
            val sardine = createSardineClient(mountSetting)
            val fullPath = resolvePath(mountSetting, relativePath)
            val url = buildUrl(mountSetting.webdavUrl, fullPath)
            
            Log.d(TAG, "请求URL: $url")
            
            // 使用 Sardine 列出目录内容
            val resources: List<DavResource> = sardine.list(url)
            
            val files = resources.drop(1).mapNotNull { resource ->
                // 跳过当前目录项
                if (resource.path == fullPath || resource.path == "$fullPath/") {
                    return@mapNotNull null
                }
                
                // 解析文件名
                val fileName = resource.name ?: resource.path.substringAfterLast('/')
                if (fileName.isBlank()) return@mapNotNull null
                
                // 构建相对路径
                val fileRelativePath = if (relativePath.isEmpty()) {
                    fileName
                } else {
                    "$relativePath/$fileName"
                }
                
                WebDAVFile(
                    name = fileName,
                    path = fileRelativePath,
                    isDirectory = resource.isDirectory,
                    size = resource.contentLength ?: 0L,
                    lastModified = resource.modified?.time ?: 0L,
                    mountSetting = mountSetting
                )
            }
            
            Log.d(TAG, "成功列出 ${files.size} 个文件")
            Result.success(files)
            
        } catch (e: Exception) {
            Log.e(TAG, "列出文件失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 下载文件
     * @param context 上下文
     * @param file WebDAV文件信息
     * @param onProgress 下载进度回调 (已下载字节数, 总字节数)
     * @param onSuccess 下载成功回调 (本地文件路径)
     * @param onError 下载失败回调 (错误信息)
     */
    suspend fun downloadFile(
        context: Context,
        file: WebDAVFile,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
        onSuccess: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        try {
            ensureActive() // 检查协程是否被取消
            
            Log.d(TAG, "开始下载文件: ${file.name}")
            
            val sardine = createSardineClient(file.mountSetting)
            val fullPath = resolvePath(file.mountSetting, file.path)
            val url = buildUrl(file.mountSetting.webdavUrl, fullPath)
            
            Log.d(TAG, "下载URL: $url")
            
            // 创建公共下载目录: /storage/emulated/0/Download/yhchat/
            val downloadsRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val downloadsDir = File(downloadsRoot, "yhchat")
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            
            val useMultiThread = file.size > MIN_MULTI_THREAD_SIZE
            val resultPath = try {
                if (useMultiThread) {
                    downloadWithMultiThread(
                        file = file,
                        url = url,
                        downloadsDir = downloadsDir,
                        onProgress = onProgress
                    )
                } else {
                    downloadSingleThread(
                        sardine = sardine,
                        url = url,
                        downloadsDir = downloadsDir,
                        file = file,
                        onProgress = onProgress
                    )
                }
            } catch (e: Exception) {
                if (useMultiThread) {
                    Log.w(TAG, "多线程下载失败，回退到单线程", e)
                    downloadSingleThread(
                        sardine = sardine,
                        url = url,
                        downloadsDir = downloadsDir,
                        file = file,
                        onProgress = onProgress
                    )
                } else {
                    throw e
                }
            }
            
            withContext(Dispatchers.Main) {
                onSuccess(resultPath)
            }
            
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "下载文件失败: ${file.name}", e)
            withContext(Dispatchers.Main) {
                onError("下载失败: ${e.message}")
            }
        }
    }

    private suspend fun downloadWithMultiThread(
        file: WebDAVFile,
        url: String,
        downloadsDir: File,
        onProgress: (Long, Long) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val totalBytes = file.size
        require(totalBytes > 0) { "多线程下载需要已知文件大小" }
        val localFile = File(downloadsDir, file.name)
        val tempFile = File(downloadsDir, "${file.name}.multi.tmp")
        val chunkSize = max(MIN_CHUNK_SIZE, totalBytes / DEFAULT_THREAD_COUNT).coerceAtLeast(1L)
        val chunkCount = ceil(totalBytes.toDouble() / chunkSize.toDouble()).toInt().coerceAtLeast(1)
        val ranges = (0 until chunkCount).map { index ->
            val start = index * chunkSize
            val end = min(totalBytes - 1, start + chunkSize - 1)
            start..end
        }
        val progress = AtomicLong(0L)
        RandomAccessFile(tempFile, "rw").use { raf ->
            raf.setLength(totalBytes)
            coroutineScope {
                val jobs = mutableListOf<Job>()
                for (range in ranges) {
                    jobs += launch(Dispatchers.IO) {
                        downloadRange(
                            url = url,
                            mountSetting = file.mountSetting,
                            range = range,
                            raf = raf,
                            progress = progress,
                            totalBytes = totalBytes,
                            onProgress = onProgress
                        )
                    }
                }
                jobs.forEach { it.join() }
            }
        }
        if (!tempFile.renameTo(localFile)) {
            tempFile.delete()
            throw IOException("无法重命名多线程临时文件")
        }
        localFile.absolutePath
    }

    private suspend fun downloadRange(
        url: String,
        mountSetting: MountSetting,
        range: LongRange,
        raf: RandomAccessFile,
        progress: AtomicLong,
        totalBytes: Long,
        onProgress: (Long, Long) -> Unit
    ) {
        coroutineContext.ensureActive()
        val credential = Credentials.basic(mountSetting.webdavUserName, mountSetting.webdavPassword)
        val request = Request.Builder()
            .url(url)
            .addHeader("Range", "bytes=${range.first}-${range.last}")
            .addHeader("Authorization", credential)
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (response.code != 206) {
                throw IOException("服务器不支持多线程下载 (code=${response.code})")
            }
            val body = response.body ?: throw IOException("响应体为空")
            body.byteStream().use { inputStream ->
                val buffer = ByteArray(8192)
                var offset = range.first
                while (true) {
                    coroutineContext.ensureActive()
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1) break
                    synchronized(raf) {
                        raf.seek(offset)
                        raf.write(buffer, 0, bytesRead)
                    }
                    offset += bytesRead
                    val downloaded = progress.addAndGet(bytesRead.toLong())
                    withContext(Dispatchers.Main) {
                        onProgress(downloaded, totalBytes)
                    }
                }
            }
        }
    }

    private suspend fun downloadSingleThread(
        sardine: Sardine,
        url: String,
        downloadsDir: File,
        file: WebDAVFile,
        onProgress: (Long, Long) -> Unit
    ): String {
        val localFile = File(downloadsDir, file.name)
        var tempFile: File? = null
        try {
            tempFile = File(downloadsDir, "${file.name}.tmp")
            sardine.get(url).use { inputStream ->
                FileOutputStream(tempFile).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var totalBytesRead = 0L
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        coroutineContext.ensureActive()
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        withContext(Dispatchers.Main) {
                            onProgress(totalBytesRead, file.size)
                        }
                    }
                }
            }
            if (!tempFile.renameTo(localFile)) {
                throw IOException("无法重命名临时文件")
            }
            return localFile.absolutePath
        } catch (e: Exception) {
            tempFile?.delete()
            throw e
        }
    }

    
    /**
     * 上传文件
     * @param localFile 本地文件
     * @param mountSetting 已解密的挂载设置
     * @param remotePath 远程路径（相对于根路径）
     * @param onProgress 上传进度回调 (已上传字节数, 总字节数)
     * @param onSuccess 上传成功回调
     * @param onError 上传失败回调 (错误信息)
     */
    suspend fun uploadFile(
        localFile: File,
        mountSetting: MountSetting,
        remotePath: String,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        try {
            ensureActive()
            
            Log.d(TAG, "开始上传文件: ${localFile.name} -> $remotePath")
            
            val sardine = createSardineClient(mountSetting)
            val fullPath = resolvePath(mountSetting, remotePath)
            val url = buildUrl(mountSetting.webdavUrl, fullPath)
            
            Log.d(TAG, "上传URL: $url")
            
            // 使用 Sardine 上传文件
            sardine.put(url, localFile, null as String?)
            
            Log.d(TAG, "文件上传成功: ${localFile.name}")
            onSuccess()
            
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "上传文件失败: ${localFile.name}", e)
            onError("上传失败: ${e.message}")
        }
    }
    
    /**
     * 创建目录
     * @param mountSetting 已解密的挂载设置
     * @param remotePath 远程路径（相对于根路径）
     * @return 是否创建成功
     */
    suspend fun createDirectory(
        mountSetting: MountSetting,
        remotePath: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "创建目录: $remotePath")
            
            val sardine = createSardineClient(mountSetting)
            val fullPath = resolvePath(mountSetting, remotePath)
            val url = buildUrl(mountSetting.webdavUrl, fullPath)
            
            sardine.createDirectory(url)
            
            Log.d(TAG, "目录创建成功: $remotePath")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "创建目录失败: $remotePath", e)
            false
        }
    }
    
    /**
     * 删除文件或目录
     * @param mountSetting 已解密的挂载设置
     * @param remotePath 远程路径（相对于根路径）
     * @return 是否删除成功
     */
    suspend fun delete(
        mountSetting: MountSetting,
        remotePath: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "删除: $remotePath")
            
            val sardine = createSardineClient(mountSetting)
            val fullPath = resolvePath(mountSetting, remotePath)
            val url = buildUrl(mountSetting.webdavUrl, fullPath)
            
            sardine.delete(url)
            
            Log.d(TAG, "删除成功: $remotePath")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "删除失败: $remotePath", e)
            false
        }
    }
    
    /**
     * 移动/重命名文件或目录
     * @param mountSetting 已解密的挂载设置
     * @param sourcePath 源路径（相对于根路径）
     * @param targetPath 目标路径（相对于根路径）
     * @return 是否移动成功
     */
    suspend fun move(
        mountSetting: MountSetting,
        sourcePath: String,
        targetPath: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "移动: $sourcePath -> $targetPath")
            
            val sardine = createSardineClient(mountSetting)
            val sourceFullPath = resolvePath(mountSetting, sourcePath)
            val targetFullPath = resolvePath(mountSetting, targetPath)
            val sourceUrl = buildUrl(mountSetting.webdavUrl, sourceFullPath)
            val targetUrl = buildUrl(mountSetting.webdavUrl, targetFullPath)
            
            sardine.move(sourceUrl, targetUrl)
            
            Log.d(TAG, "移动成功: $sourcePath -> $targetPath")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "移动失败: $sourcePath -> $targetPath", e)
            false
        }
    }
    
    /**
     * 构建完整的WebDAV URL
     */
    private fun buildUrl(baseUrl: String, path: String): String {
        val cleanBaseUrl = baseUrl.trimEnd('/')
        val cleanPath = path.trim('/').split('/').joinToString("/") { part ->
            // URL编码路径段，并将 + 替换为 %20（URL路径中空格应该用 %20，而不是 +）
            URLEncoder.encode(part, StandardCharsets.UTF_8.toString())
                .replace("+", "%20")
        }
        
        return if (cleanPath.isEmpty()) {
            cleanBaseUrl
        } else {
            "$cleanBaseUrl/$cleanPath"
        }
    }
    
}
