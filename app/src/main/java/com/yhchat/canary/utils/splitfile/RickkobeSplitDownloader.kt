package com.yhchat.canary.utils.splitfile

import android.content.Context
import android.net.Uri
import com.yhchat.canary.data.model.ChatMessage
import com.yhchat.canary.data.repository.MessageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object RickkobeSplitDownloader {
    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()

    private suspend fun openResource(initial: Request): okhttp3.Response {
        var request = initial
        repeat(5) { hop ->
            currentCoroutineContext().ensureActive()
            val url = request.url
            require(url.isHttps && url.port == 443 && url.username.isEmpty() && url.password.isEmpty() &&
                url.host in setOf("chat-file.jwznb.com", "chat-file-oss.jwznb.com", "chat-storage1.jwznb.com")) { "不支持的分块资源地址" }
            val response = client.newCall(request).execute()
            if (response.code !in setOf(301, 302, 303, 307, 308)) return response
            val location = response.header("Location")
            response.close()
            check(hop < 4 && location != null) { "分块重定向次数过多或缺少地址" }
            val next = url.resolve(location) ?: error("分块重定向地址无效")
            request = request.newBuilder().url(next).build()
        }
        error("分块重定向失败")
    }

    suspend fun discover(repository: MessageRepository, anchor: ChatMessage, chatId: String, chatType: Int,
        manifest: RickkobeSplitProtocol.Manifest, initialMessages: List<ChatMessage>,
        onStatus: (String) -> Unit): List<RickkobeSplitProtocol.Part> {
        val parts = sortedMapOf<Int, RickkobeSplitProtocol.Part>()
        fun ingest(messages: List<ChatMessage>) {
            for (message in messages) {
                if (message.msgDeleteTime != null || message.sender.chatId != anchor.sender.chatId ||
                    message.sender.chatType != anchor.sender.chatType) continue
                if (message.chatId != null && message.chatId != chatId) continue
                if (message.chatType != null && message.chatType != chatType) continue
                val envelope = RickkobeSplitProtocol.parse(message.content.text)
                val candidates = if (envelope != null && envelope.manifest.id == manifest.id) {
                    check(envelope.manifest == manifest) { "同一传输的清单内容冲突" }
                    envelope.parts
                } else listOfNotNull(RickkobeSplitProtocol.fromMessage(message, manifest))
                for (part in candidates) {
                    val old = parts[part.index]
                    check(old == null || (old.size == part.size && old.sha256 == part.sha256 && old.key == part.key)) { "分块 ${part.index + 1} 索引冲突" }
                    parts[part.index] = part
                }
            }
        }
        ingest(initialMessages)
        var cursor: String? = null
        val visited = hashSetOf<String>()
        for (page in 0 until 1000) {
            if (parts.size == manifest.count) break
            currentCoroutineContext().ensureActive()
            onStatus("查找分块 ${parts.size}/${manifest.count} · 第 ${page + 1} 页")
            val messages = repository.getMessages(chatId, chatType, 100, cursor).getOrThrow()
            currentCoroutineContext().ensureActive()
            if (messages.isEmpty()) break
            ingest(messages)
            val oldest = messages.minWithOrNull(compareBy<ChatMessage> { it.msgSeq ?: Long.MAX_VALUE }.thenBy { it.sendTime }) ?: break
            if (!visited.add(oldest.msgId)) break
            cursor = oldest.msgId
        }
        check(parts.size == manifest.count) { "分块尚未收齐（${parts.size}/${manifest.count}），请等待发送完成后重试" }
        return parts.values.toList()
    }

    suspend fun download(context: Context, manifest: RickkobeSplitProtocol.Manifest,
        parts: List<RickkobeSplitProtocol.Part>, onStatus: (String) -> Unit): Uri = withContext(Dispatchers.IO) {
        require(parts.size == manifest.count && parts.map { it.index }.sorted() == (0 until manifest.count).toList())
        val folder = File(context.cacheDir, "rickkobe_download_${java.util.UUID.randomUUID()}")
        check(folder.mkdirs()) { "无法创建下载缓存" }
        try {
            val outputFile = File(folder, "merged")
            val wholeSha = MessageDigest.getInstance("SHA-256")
            val wholeMd5 = MessageDigest.getInstance("MD5")
            val coroutine = currentCoroutineContext()
            outputFile.outputStream().use { output ->
                for (part in parts.sortedBy { it.index }) {
                    coroutine.ensureActive()
                    require(Regex("[a-zA-Z0-9_.-]{1,240}").matches(part.key))
                    onStatus("下载并校验 ${part.index + 1}/${manifest.count}")
                    val request = Request.Builder().url("https://chat-file.jwznb.com/${part.key}")
                        .header("Referer", "https://myapp.jwznb.com").build()
                    val sha = MessageDigest.getInstance("SHA-256")
                    var size = 0L
                    openResource(request).use { response ->
                        check(response.isSuccessful) { "分块下载失败：HTTP ${response.code}" }
                        val body = response.body ?: error("分块响应为空")
                        body.byteStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                coroutine.ensureActive()
                                val n = input.read(buffer)
                                if (n < 0) break
                                if (n == 0) continue
                                size += n
                                check(size <= part.size) { "分块长度超出索引" }
                                output.write(buffer, 0, n)
                                sha.update(buffer, 0, n); wholeSha.update(buffer, 0, n); wholeMd5.update(buffer, 0, n)
                            }
                        }
                    }
                    check(size == manifest.partSize(part.index) && size == part.size && hex(sha.digest()) == part.sha256) { "分块 ${part.index + 1} 校验失败" }
                }
            }
            check(outputFile.length() == manifest.size && hex(wholeSha.digest()) == manifest.sha256 && hex(wholeMd5.digest()) == manifest.md5) { "完整文件校验失败" }
            coroutine.ensureActive()
            onStatus("校验完成，保存到下载目录…")
            SplitFileDownloader.saveToDownloads(context, outputFile, manifest.name)
        } finally { folder.deleteRecursively() }
    }
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
}
