package com.yhchat.canary.service

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * 音频缓存管理器
 * 支持SHA256和MD5哈希检查，避免重复下载相同的音频文件
 */
class AudioCacheManager(private val context: Context) {
    
    companion object {
        private const val TAG = "AudioCacheManager"
        private const val CACHE_DIR_NAME = "audio_cache"
        private const val MAX_CACHE_SIZE = 100 * 1024 * 1024 // 100MB
        private const val MAX_CACHE_FILES = 200 // 最大缓存文件数
    }
    
    private val cacheDir: File by lazy {
        File(context.cacheDir, CACHE_DIR_NAME).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }
    
    /**
     * 根据URL获取缓存的音频文件
     * @param audioUrl 音频URL
     * @return 如果存在缓存文件则返回文件，否则返回null
     */
    fun getCachedAudioFile(audioUrl: String): File? {
        val urlHash = calculateSHA256(audioUrl)
        val cacheFile = File(cacheDir, "$urlHash.m4a")
        
        return if (cacheFile.exists() && cacheFile.length() > 0) {
            Log.d(TAG, "找到缓存音频文件: ${cacheFile.absolutePath}")
            // 更新文件的最后修改时间，用于LRU策略
            cacheFile.setLastModified(System.currentTimeMillis())
            cacheFile
        } else {
            null
        }
    }
    
    /**
     * 缓存音频文件
     * @param audioUrl 音频URL
     * @param audioData 音频数据
     * @return 缓存的文件
     */
    fun cacheAudioFile(audioUrl: String, audioData: ByteArray): File {
        // 清理缓存（如果需要）
        cleanupCacheIfNeeded()
        
        val urlHash = calculateSHA256(audioUrl)
        val cacheFile = File(cacheDir, "$urlHash.m4a")
        
        // 计算音频数据的哈希值，用于验证文件完整性
        val dataHash = calculateSHA256(audioData)
        val hashFile = File(cacheDir, "$urlHash.hash")
        
        try {
            // 保存音频文件
            FileOutputStream(cacheFile).use { outputStream ->
                outputStream.write(audioData)
            }
            
            // 保存哈希值文件，用于后续验证
            FileOutputStream(hashFile).use { outputStream ->
                outputStream.write(dataHash.toByteArray())
            }
            
            Log.d(TAG, "音频文件已缓存: ${cacheFile.absolutePath}")
            return cacheFile
            
        } catch (e: Exception) {
            Log.e(TAG, "缓存音频文件失败", e)
            // 清理可能不完整的文件
            cacheFile.delete()
            hashFile.delete()
            throw e
        }
    }
    
    /**
     * 验证缓存文件的完整性
     * @param audioUrl 音频URL
     * @return 文件是否完整
     */
    fun verifyCachedFile(audioUrl: String): Boolean {
        val urlHash = calculateSHA256(audioUrl)
        val cacheFile = File(cacheDir, "$urlHash.m4a")
        val hashFile = File(cacheDir, "$urlHash.hash")
        
        if (!cacheFile.exists() || !hashFile.exists()) {
            return false
        }
        
        try {
            // 读取保存的哈希值
            val savedHash = hashFile.readText()
            
            // 计算当前文件的哈希值
            val currentHash = calculateSHA256(cacheFile)
            
            val isValid = savedHash == currentHash
            if (!isValid) {
                Log.w(TAG, "缓存文件哈希值不匹配，删除损坏的文件: ${cacheFile.name}")
                cacheFile.delete()
                hashFile.delete()
            }
            
            return isValid
            
        } catch (e: Exception) {
            Log.e(TAG, "验证缓存文件失败", e)
            return false
        }
    }
    
    /**
     * 检查是否存在相同内容的音频文件（基于文件内容哈希）
     * @param audioData 音频数据
     * @return 如果存在相同内容的文件则返回该文件，否则返回null
     */
    fun findDuplicateAudioFile(audioData: ByteArray): File? {
        val dataHash = calculateSHA256(audioData)
        
        cacheDir.listFiles { file ->
            file.name.endsWith(".m4a")
        }?.forEach { cacheFile ->
            try {
                val cachedDataHash = calculateSHA256(cacheFile)
                if (cachedDataHash == dataHash) {
                    Log.d(TAG, "找到内容相同的缓存文件: ${cacheFile.name}")
                    // 更新最后修改时间
                    cacheFile.setLastModified(System.currentTimeMillis())
                    return cacheFile
                }
            } catch (e: Exception) {
                Log.w(TAG, "检查文件时出错: ${cacheFile.name}", e)
            }
        }
        
        return null
    }
    
    /**
     * 清理缓存（基于LRU策略）
     */
    private fun cleanupCacheIfNeeded() {
        val cacheFiles = cacheDir.listFiles { file ->
            file.name.endsWith(".m4a")
        }?.toList() ?: return
        
        val totalSize = cacheFiles.sumOf { it.length() }
        
        // 如果缓存大小或文件数量超过限制，则清理最旧的文件
        if (totalSize > MAX_CACHE_SIZE || cacheFiles.size > MAX_CACHE_FILES) {
            val sortedFiles = cacheFiles.sortedBy { it.lastModified() }
            val filesToDelete = if (totalSize > MAX_CACHE_SIZE) {
                // 删除文件直到大小降到80%以下
                val targetSize = MAX_CACHE_SIZE * 0.8
                var currentSize = totalSize
                sortedFiles.takeWhile { file ->
                    val shouldDelete = currentSize > targetSize
                    if (shouldDelete) {
                        currentSize -= file.length()
                    }
                    shouldDelete
                }
            } else {
                // 删除多余的文件，保留最新的文件
                sortedFiles.take(cacheFiles.size - MAX_CACHE_FILES + 20)
            }
            
            filesToDelete.forEach { file ->
                try {
                    // 同时删除哈希文件
                    val hashFile = File(cacheDir, "${file.nameWithoutExtension}.hash")
                    file.delete()
                    hashFile.delete()
                    Log.d(TAG, "清理缓存文件: ${file.name}")
                } catch (e: Exception) {
                    Log.w(TAG, "删除缓存文件失败: ${file.name}", e)
                }
            }
        }
    }
    
    /**
     * 计算字符串的SHA256哈希值
     */
    private fun calculateSHA256(input: String): String {
        return calculateSHA256(input.toByteArray())
    }
    
    /**
     * 计算字节数组的SHA256哈希值
     */
    private fun calculateSHA256(input: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * 计算文件的SHA256哈希值，避免整文件读入内存
     */
    private fun calculateSHA256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    
    /**
     * 计算字节数组的MD5哈希值（备用）
     */
    private fun calculateMD5(input: ByteArray): String {
        val digest = MessageDigest.getInstance("MD5")
        val hashBytes = digest.digest(input)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * 获取缓存使用情况
     */
    fun getCacheStats(): CacheStats {
        val cacheFiles = cacheDir.listFiles { file ->
            file.name.endsWith(".m4a")
        }?.toList() ?: emptyList()
        
        val totalSize = cacheFiles.sumOf { it.length() }
        val fileCount = cacheFiles.size
        
        return CacheStats(
            fileCount = fileCount,
            totalSize = totalSize,
            maxSize = MAX_CACHE_SIZE.toLong(),
            maxFiles = MAX_CACHE_FILES
        )
    }
    
    /**
     * 清空所有缓存
     */
    fun clearAllCache() {
        try {
            cacheDir.listFiles()?.forEach { file ->
                file.delete()
            }
            Log.d(TAG, "已清空所有音频缓存")
        } catch (e: Exception) {
            Log.e(TAG, "清空缓存失败", e)
        }
    }
}

/**
 * 缓存统计信息
 */
data class CacheStats(
    val fileCount: Int,
    val totalSize: Long,
    val maxSize: Long,
    val maxFiles: Int
) {
    val usagePercent: Float
        get() = (totalSize.toFloat() / maxSize.toFloat()) * 100f
        
    val formattedSize: String
        get() = formatFileSize(totalSize)
        
    val formattedMaxSize: String
        get() = formatFileSize(maxSize)
    
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> "${bytes / 1024}KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)}MB"
            else -> "${bytes / (1024 * 1024 * 1024)}GB"
        }
    }
}
