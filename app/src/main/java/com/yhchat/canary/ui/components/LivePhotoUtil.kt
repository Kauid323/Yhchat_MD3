package com.yhchat.canary.ui.components

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.URL
import java.security.MessageDigest

/**
 * 实况照片 (Live Photo / Motion Photo) 工具类
 * 
 * 原理：
 * Android/iOS 的实况照片（Motion Photo / Live Photo）通常将 MP4 视频流直接内嵌在 JPEG / HEIC 文件尾部或特定段落中。
 * 本工具直接扫描文件二进制字节寻找 MP4 `ftyp` Box 头部签名与有效 Brand，提取出内嵌的 MP4 视频流。
 */
object LivePhotoUtil {

    private const val TAG = "LivePhotoUtil"

    // MP4 Box header bytes: 'f', 't', 'y', 'p'
    private val FTYP_BYTES = byteArrayOf(0x66, 0x74, 0x79, 0x70)

    // 常见的有效 MP4 / MOV Major Brands
    private val VALID_BRANDS = listOf(
        "isom", "iso2", "mp41", "mp42", "qt  ", "MSNV", "heic", "mif1", "3gp4", "3gp6", "caep", "avc1"
    ).map { it.toByteArray(Charsets.US_ASCII) }

    /**
     * 检查本地或网络图片是否包含实况照片 (Live Photo / Motion Photo)
     * 通过扫描文件字节中的内嵌 MP4 签名 (ftyp box)
     * @return 提取出的视频文件，若不是 Live Photo 则返回 null
     */
    suspend fun extractLivePhotoVideo(context: Context, imageUrl: String): File? = withContext(Dispatchers.IO) {
        try {
            val cacheKey = md5(imageUrl)
            val cachedVideoFile = File(context.cacheDir, "live_video_$cacheKey.mp4")
            if (cachedVideoFile.exists() && cachedVideoFile.length() > 1024) {
                return@withContext cachedVideoFile
            }

            // 获取原图文件（本地文件或下载临时文件）
            val sourceFile = resolveSourceFile(context, imageUrl) ?: return@withContext null

            // 扫描字节寻找内嵌视频偏移量
            val videoOffset = findEmbeddedVideoOffset(sourceFile)
            if (videoOffset > 0 && videoOffset < sourceFile.length()) {
                val videoLength = sourceFile.length() - videoOffset
                if (videoLength >= 10240) { // 至少 10KB
                    RandomAccessFile(sourceFile, "r").use { raf ->
                        raf.seek(videoOffset)
                        FileOutputStream(cachedVideoFile).use { out ->
                            val buffer = ByteArray(64 * 1024)
                            var remaining = videoLength
                            while (remaining > 0) {
                                val toRead = Math.min(buffer.size.toLong(), remaining).toInt()
                                val read = raf.read(buffer, 0, toRead)
                                if (read <= 0) break
                                out.write(buffer, 0, read)
                                remaining -= read
                            }
                        }
                    }
                    // Log.d(TAG, "成功提取 Live Photo 视频: offset=$videoOffset, size=$videoLength, file=${cachedVideoFile.absolutePath}")
                    return@withContext cachedVideoFile
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "提取 Live Photo 失败: $imageUrl", e)
            null
        }
    }

    /**
     * 在图片文件中直接扫描 MP4 ftyp 签名字节
     * 返回内嵌视频在文件中的起始字节偏移量 (ftyp box 开始处 = ftyp 索引 - 4)
     */
    private fun findEmbeddedVideoOffset(file: File): Long {
        val fileLength = file.length()
        if (fileLength < 10240) return -1L

        RandomAccessFile(file, "r").use { raf ->
            // 采用 1MB 缓冲区进行滑动窗口扫描
            val bufferSize = 1024 * 1024
            val buffer = ByteArray(bufferSize + 16)
            var scanPosition = 0L

            while (scanPosition < fileLength - 16) {
                raf.seek(scanPosition)
                val bytesRead = raf.read(buffer, 0, bufferSize)
                if (bytesRead <= 8) break

                for (i in 4 until bytesRead - 4) {
                    if (buffer[i] == FTYP_BYTES[0] &&
                        buffer[i + 1] == FTYP_BYTES[1] &&
                        buffer[i + 2] == FTYP_BYTES[2] &&
                        buffer[i + 3] == FTYP_BYTES[3]
                    ) {
                        // 检查前面 4 字节的 box size (通常在 8 到 128 之间)
                        val boxSize = ((buffer[i - 4].toInt() and 0xFF) shl 24) or
                                ((buffer[i - 3].toInt() and 0xFF) shl 16) or
                                ((buffer[i - 2].toInt() and 0xFF) shl 8) or
                                (buffer[i - 1].toInt() and 0xFF)

                        if (boxSize in 8..128) {
                            // 检查后面的 major brand
                            val brandOffset = i + 4
                            val isBrandValid = VALID_BRANDS.any { brand ->
                                brand.indices.all { bIdx ->
                                    brandOffset + bIdx < bytesRead && buffer[brandOffset + bIdx] == brand[bIdx]
                                }
                            }

                            if (isBrandValid) {
                                val candidateOffset = scanPosition + (i - 4)
                                // 必须大于 0（不是纯视频文件本身），且距离文件尾部有合理长度
                                if (candidateOffset > 1024 && (fileLength - candidateOffset) >= 10240) {
                                    return candidateOffset
                                }
                            }
                        }
                    }
                }

                // 推进扫描窗口，重叠 16 字节防止跨边界截断
                scanPosition += (bytesRead - 16).coerceAtLeast(1)
            }
        }
        return -1L
    }

    private suspend fun resolveSourceFile(context: Context, imageUrl: String): File? {
        return if (imageUrl.startsWith("file://") || imageUrl.startsWith("/")) {
            val path = if (imageUrl.startsWith("file://")) imageUrl.removePrefix("file://") else imageUrl
            File(path).takeIf { it.exists() }
        } else if (imageUrl.startsWith("content://")) {
            val uri = android.net.Uri.parse(imageUrl)
            val tempFile = File(context.cacheDir, "live_temp_${md5(imageUrl)}.tmp")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            tempFile.takeIf { it.exists() }
        } else {
            // 网络图片下载到缓存临时文件
            val tempFile = File(context.cacheDir, "live_net_${md5(imageUrl)}.tmp")
            if (tempFile.exists() && tempFile.length() > 0) return tempFile

            val connection = (URL(imageUrl).openConnection() as java.net.HttpURLConnection).apply {
                instanceFollowRedirects = true
                if (imageUrl.contains(".jwznb.com")) {
                    setRequestProperty("Referer", "https://myapp.jwznb.com")
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
                }
                connect()
            }
            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            tempFile.takeIf { it.exists() }
        }
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
