package com.yhchat.canary.utils.splitfile

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.yhchat.canary.data.model.SplitFileManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * 分块清单的解析、下载与合并。
 *
 * 接收端只做三件事：
 *   1. 读 `.splitfiles` 文件，解析出清单；
 *   2. 按 `index` 顺序逐块下载（七牛 key → 图床 URL）；
 *   3. 拼接成一个文件，落到系统下载目录。
 */
object SplitFileDownloader {

    private const val TAG = "SplitFileDownloader"

    /** 七牛资源的基础访问地址（和 Disk 那边的拼法保持一致） */
    private const val FILE_BASE_URL = "https://chat-img.jwznb.com/"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * 解析清单内容。
     *
     * @return 解析失败或协议不匹配时返回 null（不抛异常 —— 调用方通常是 UI）
     */
    fun parseManifest(json: String?): SplitFileManifest? {
        if (json.isNullOrBlank()) return null
        return try {
            val manifest = com.google.gson.Gson()
                .fromJson(json, SplitFileManifest::class.java)
                ?: return null

            // 协议校验：不是我们的清单就当普通文件处理
            if (manifest.protocol != SplitFileManifest.PROTOCOL_NAME) return null
            if (manifest.parts.isEmpty()) return null
            if (manifest.totalSize <= 0L) return null

            manifest
        } catch (e: Exception) {
            Log.w(TAG, "清单解析失败: ${e.message}")
            null
        }
    }

    /** 下载进度 */
    data class Progress(
        val currentChunk: Int = 0,
        val totalChunks: Int = 0,
        val chunkFraction: Float = 0f,
        val stage: Stage = Stage.Downloading,
        val errorMessage: String? = null
    ) {
        enum class Stage { Downloading, Merging, Done }

        val fraction: Float
            get() {
                if (totalChunks <= 0) return 0f
                return ((currentChunk + chunkFraction) / totalChunks).coerceIn(0f, 1f)
            }
    }

    /**
     * 下载并合并。
     *
     * @param manifest 已解析的清单
     * @param targetFileName 输出文件名（默认用清单里的 name）
     * @return 最终保存在下载目录的 Uri
     */
    suspend fun downloadAndMerge(
        context: Context,
        manifest: SplitFileManifest,
        targetFileName: String = manifest.fileName,
        onProgress: (Progress) -> Unit = {}
    ): Result<Uri> = withContext(Dispatchers.IO) {
        val workDir = File(context.cacheDir, "split_download_${System.currentTimeMillis()}")
        workDir.mkdirs()

        try {
            val totalChunks = manifest.parts.size
            val orderedParts = manifest.parts.sortedBy { it.index }

            // ---- 1. 逐块下载 ----
            val mergedTmp = File(workDir, "merged.tmp")
            FileOutputStream(mergedTmp).use { output ->
                orderedParts.forEachIndexed { position, part ->
                    onProgress(
                        Progress(
                            currentChunk = position,
                            totalChunks = totalChunks,
                            stage = Progress.Stage.Downloading
                        )
                    )

                    val url = if (part.key.startsWith("http")) {
                        part.key
                    } else {
                        FILE_BASE_URL + part.key.trimStart('/')
                    }

                    val request = Request.Builder()
                        .url(url)
                        // 图床需要 Referer，否则 403
                        .header("Referer", "https://myapp.jwznb.com")
                        .header(
                            "User-Agent",
                            "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36"
                        )
                        .get()
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            return@withContext Result.failure(
                                Exception("第 ${position + 1}/$totalChunks 块下载失败: HTTP ${response.code}")
                            )
                        }
                        val body = response.body
                            ?: return@withContext Result.failure(Exception("第 ${position + 1} 块响应为空"))

                        body.byteStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            var writtenThisChunk = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                output.write(buffer, 0, read)
                                writtenThisChunk += read

                                // 块内进度
                                if (part.size > 0) {
                                    onProgress(
                                        Progress(
                                            currentChunk = position,
                                            totalChunks = totalChunks,
                                            chunkFraction = (writtenThisChunk.toFloat() / part.size)
                                                .coerceIn(0f, 1f),
                                            stage = Progress.Stage.Downloading
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ---- 2. 大小校验 ----
            onProgress(
                Progress(
                    currentChunk = totalChunks,
                    totalChunks = totalChunks,
                    stage = Progress.Stage.Merging
                )
            )

            if (mergedTmp.length() != manifest.totalSize) {
                return@withContext Result.failure(
                    Exception(
                        "合并后大小不符：期望 ${manifest.totalSize} 字节，实际 ${mergedTmp.length()} 字节"
                    )
                )
            }

            // ---- 3. 发布到下载目录 ----
            val saved = saveToDownloads(context, mergedTmp, targetFileName)

            onProgress(
                Progress(
                    currentChunk = totalChunks,
                    totalChunks = totalChunks,
                    stage = Progress.Stage.Done
                )
            )

            Result.success(saved)
        } catch (e: Exception) {
            Log.e(TAG, "下载合并失败", e)
            Result.failure(e)
        } finally {
            runCatching { workDir.deleteRecursively() }
        }
    }

    /**
     * 把合并结果写进系统下载目录。
     *
     * Android 10+ 走 MediaStore（不需要存储权限），低版本直接写公共目录。
     */
    private fun saveToDownloads(context: Context, source: File, fileName: String): Uri {
        val safeName = fileName
            .replace('/', '_')
            .replace('\\', '_')
            .ifBlank { "merged_file" }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values)
                ?: throw IllegalStateException("无法在下载目录创建文件")

            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IllegalStateException("无法写入下载目录")

            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!dir.exists()) dir.mkdirs()
            val target = File(dir, safeName)
            source.inputStream().use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
            return Uri.fromFile(target)
        }
    }
}
