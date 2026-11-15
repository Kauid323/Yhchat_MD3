package com.yhchat.canary.utils.splitfile

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * 分块合并后的完整性校验。
 *
 * 协议要求：
 *   - 合并后**必须**通过完整 SHA-256 校验；
 *   - 可额外校验 MD5；
 *   - 各块 SHA-256 也应在拼接前校验（块级校验能定位到具体哪块坏了，
 *     比整包校验失败后只能整体重下有用得多）。
 *
 * 全部用流式读取，不把整个文件读进内存 —— 分块文件动辄几百 MB，
 * `readBytes()` 会直接 OOM。
 */
object SplitFileVerifier {

    private const val BUFFER_SIZE = 8192

    /** 校验单个文件的 SHA-256，返回小写十六进制 */
    fun sha256Of(file: File): String = digestOf(file, "SHA-256")

    /** 校验单个文件的 MD5，返回小写十六进制 */
    fun md5Of(file: File): String = digestOf(file, "MD5")

    fun sha256Of(stream: InputStream): String = digestOf(stream, "SHA-256")

    private fun digestOf(file: File, algorithm: String): String {
        if (!file.exists() || !file.isFile) return ""
        return try {
            file.inputStream().use { digestOf(it, algorithm) }
        } catch (e: Exception) {
            ""
        }
    }

    private fun digestOf(stream: InputStream, algorithm: String): String {
        return try {
            val digest = MessageDigest.getInstance(algorithm)
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
            digest.digest().toHex()
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * 校验一个分块文件是否与清单里记录的那一块哈希一致。
     *
     * @param expectedSha256 文件名里携带的块哈希
     */
    fun verifyChunk(chunkFile: File, expectedSha256: String): Boolean {
        if (expectedSha256.isBlank()) return false
        val actual = sha256Of(chunkFile)
        return actual.isNotEmpty() && actual.equals(expectedSha256, ignoreCase = true)
    }

    /**
     * 校验合并后的完整文件。
     *
     * @return null 表示通过；否则返回失败原因（直接可以显示给用户）
     */
    fun verifyMerged(mergedFile: File, manifest: SplitManifest): String? {
        if (!mergedFile.exists() || !mergedFile.isFile) {
            return "合并后的文件不存在"
        }

        // 大小先比，便宜且能立刻发现"少了块"这类问题
        if (mergedFile.length() != manifest.totalSize) {
            return "大小不符：期望 ${manifest.totalSize} 字节，实际 ${mergedFile.length()} 字节"
        }

        val actualSha256 = sha256Of(mergedFile)
        if (actualSha256.isEmpty()) return "无法计算 SHA-256"
        if (!actualSha256.equals(manifest.sha256, ignoreCase = true)) {
            return "SHA-256 校验未通过"
        }

        // MD5 是可选增强校验，清单里没带就跳过
        if (manifest.md5.isNotBlank()) {
            val actualMd5 = md5Of(mergedFile)
            if (actualMd5.isNotEmpty() && !actualMd5.equals(manifest.md5, ignoreCase = true)) {
                return "MD5 校验未通过"
            }
        }

        return null
    }

    private fun ByteArray.toHex(): String {
        val chars = CharArray(size * 2)
        for (i in indices) {
            val v = this[i].toInt() and 0xFF
            chars[i * 2] = HEX[v ushr 4]
            chars[i * 2 + 1] = HEX[v and 0x0F]
        }
        return String(chars)
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
