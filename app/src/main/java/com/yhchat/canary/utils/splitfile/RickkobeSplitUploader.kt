package com.yhchat.canary.utils.splitfile

import android.content.Context
import android.net.Uri
import com.yhchat.canary.data.model.SplitUploadProgress
import com.yhchat.canary.data.repository.MessageRepository
import com.yhchat.canary.data.repository.SendMessagePayload
import com.yhchat.canary.utils.FileUploadUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Sends only the compact v2 protocol; v1 remains receive-only. */
object RickkobeSplitUploader {
    suspend fun upload(
        context: Context, uri: Uri, name: String, token: String, chunkSize: Long,
        repository: MessageRepository, chatId: String, chatType: Int,
        onProgress: (SplitUploadProgress) -> Unit
    ) = withContext(Dispatchers.IO) {
        require(RickkobeSplitProtocol.safeName(name)) { "文件名不符合 Yunhu-rickkobe 协议" }
        val chunk = chunkSize.coerceIn(1024L * 1024, 32L * 1024 * 1024)
        val id = UUID.randomUUID().toString().replace("-", "")
        val folder = File(context.cacheDir, "rickkobe_upload_$id")
        check(folder.mkdirs()) { "无法创建上传缓存" }
        try {
            val source = File(folder, "source")
            val sha = MessageDigest.getInstance("SHA-256")
            val md5 = MessageDigest.getInstance("MD5")
            val coroutine = currentCoroutineContext()
            context.contentResolver.openInputStream(uri)?.use { input ->
                source.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutine.ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        if (n == 0) continue
                        output.write(buffer, 0, n); sha.update(buffer, 0, n); md5.update(buffer, 0, n)
                        check(source.length() <= RickkobeSplitProtocol.MAX_PARTS * chunk) { "文件分块数量超过协议上限" }
                    }
                }
            } ?: error("无法读取源文件")
            check(source.length() > 0) { "文件为空" }
            val count = ((source.length() + chunk - 1) / chunk).toInt()
            val mime = context.contentResolver.getType(uri).orEmpty().takeIf { it.length <= 160 && it.none { c -> c == '\r' || c == '\n' || c == '\u0000' } }
                ?.takeIf { it.isNotBlank() } ?: "application/octet-stream"
            val manifest = RickkobeSplitProtocol.Manifest(id, name, source.length(), mime, chunk, count, hex(sha.digest()), hex(md5.digest()))
            check(repository.sendMessage(chatId = chatId, chatType = chatType, text = RickkobeSplitProtocol.serialize(manifest)).getOrThrow()) { "发送分块清单失败" }
            source.inputStream().use { input ->
                repeat(count) { index ->
                    coroutine.ensureActive()
                    val piece = File(folder, "piece")
                    val digest = MessageDigest.getInstance("SHA-256")
                    piece.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var remaining = manifest.partSize(index)
                        while (remaining > 0) {
                            coroutine.ensureActive()
                            val n = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                            check(n > 0) { "源文件提前结束" }
                            output.write(buffer, 0, n); digest.update(buffer, 0, n); remaining -= n
                        }
                    }
                    val partName = RickkobeSplitProtocol.partName(id, index, hex(digest.digest()))
                    onProgress(SplitUploadProgress(index, count, stage = SplitUploadProgress.Stage.Uploading))
                    val uploaded = FileUploadUtil.uploadFile(context, Uri.fromFile(piece), token, resourceKey = partName, onProgress = { fraction ->
                        onProgress(SplitUploadProgress(index, count, fraction, SplitUploadProgress.Stage.Uploading))
                    }).getOrThrow()
                    coroutine.ensureActive()
                    check(uploaded.fsize == piece.length()) { "上传分块大小不符" }
                    check(repository.sendMessage(chatId = chatId, chatType = chatType, payload = SendMessagePayload(
                        contentType = 4, fileName = partName, fileKey = uploaded.key, fileSize = uploaded.fsize
                    )).getOrThrow()) { "发送第 ${index + 1} 块失败" }
                    check(piece.delete()) { "无法清理已发送分块" }
                }
            }
            onProgress(SplitUploadProgress(count, count, stage = SplitUploadProgress.Stage.Done))
        } finally {
            folder.deleteRecursively()
        }
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
}
