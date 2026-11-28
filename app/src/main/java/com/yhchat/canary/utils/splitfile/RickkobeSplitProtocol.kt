package com.yhchat.canary.utils.splitfile

import com.yhchat.canary.data.model.ChatMessage
import org.json.JSONObject

/** Wire compatibility with richkobe src/core/split-files.ts; internal indexes are zero based. */
object RickkobeSplitProtocol {
    const val PROTOCOL = "yh-laoda"
    const val MAX_PARTS = 16384
    private const val MIB = 1024L * 1024
    private val idPattern = Regex("[a-f0-9]{32}")
    private val shaPattern = Regex("[a-f0-9]{64}")
    private val keyPattern = Regex("[a-zA-Z0-9_.-]{1,240}")
    private val partPattern = Regex("yh-split-([a-f0-9]{32})-(\\d{5})-([a-f0-9]{64})\\.part")

    data class Manifest(val id: String, val name: String, val size: Long, val mime: String,
        val chunkSize: Long, val count: Int, val sha256: String, val md5: String) {
        fun partSize(index: Int): Long = minOf(chunkSize, size - index * chunkSize)
    }
    data class Part(val index: Int, val size: Long, val sha256: String, val key: String, val messageId: String)
    data class Envelope(val manifest: Manifest, val event: String, val parts: List<Part> = emptyList())

    fun safeName(name: String): Boolean = name.isNotBlank() && name.length <= 240 &&
        name !in setOf(".", "..") && !name.endsWith('.') && !name.endsWith(' ') &&
        name.none { it.code < 32 || it.code == 127 || it in "/\\:<>\"|?*" } &&
        !Regex("(?i)(con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\\..*)?").matches(name)

    private fun integer(value: JSONObject, key: String): Long {
        val raw = value.get(key)
        require(raw is Number && raw.toDouble().isFinite() && raw.toDouble() == raw.toLong().toDouble())
        return raw.toLong()
    }

    private fun manifest(value: JSONObject, compact: Boolean): Manifest {
        if (!compact) require(value.getString("type") == "split_files" && value.getString("protocol") == PROTOCOL &&
            integer(value, "version") == 1L && value.getString("event") == "manifest")
        val size = integer(value, "size")
        val chunk = integer(value, if (compact) "chunk" else "chunkSize")
        require(size in 1..MAX_PARTS * 32 * MIB && chunk in MIB..32 * MIB)
        val count = ((size + chunk - 1) / chunk).toInt()
        require(count in 1..MAX_PARTS)
        if (!compact) require(integer(value, "count") == count.toLong())
        val id = value.getString("id")
        val name = value.getString("name")
        val sha = value.getString("sha256")
        val md5 = value.getString("md5")
        val mime = if (compact && !value.has("mime")) "application/octet-stream" else value.getString("mime")
        require(idPattern.matches(id) && safeName(name) && shaPattern.matches(sha) && idPattern.matches(md5))
        require(mime.length <= 160 && mime.none { it == '\r' || it == '\n' || it == '\u0000' })
        return Manifest(id, name, size, mime, chunk, count, sha, md5)
    }

    private fun part(value: JSONObject, manifest: Manifest): Part {
        val index = integer(value, "index")
        require(index >= 0 && index < manifest.count)
        val size = integer(value, "size")
        val sha = value.getString("sha256")
        val key = value.getString("key")
        val messageId = value.getString("messageId")
        require(size == manifest.partSize(index.toInt()) && shaPattern.matches(sha) && keyPattern.matches(key) && idPattern.matches(messageId))
        return Part(index.toInt(), size, sha, key, messageId)
    }

    fun parse(text: String?): Envelope? = runCatching {
        if (text == null || text.length > 12000 || !text.startsWith('{') || !text.contains("split_files")) return null
        val value = JSONObject(text)
        if (value.getString("type") != "split_files") return null
        if (value.has("v")) {
            require(integer(value, "v") == 2L)
            return Envelope(manifest(value, true), "manifest")
        }
        require(value.getString("protocol") == PROTOCOL && integer(value, "version") == 1L)
        val event = value.getString("event")
        val m = manifest(if (event == "manifest") value else value.getJSONObject("manifest"), false)
        require(value.getString("id") == m.id)
        when (event) {
            "manifest", "complete" -> Envelope(m, event)
            "index" -> {
                val page = integer(value, "page")
                val pages = integer(value, "pages")
                require(pages == (m.count + 11L) / 12 && page >= 0 && page < pages)
                val entries = value.getJSONArray("parts")
                require(entries.length() == minOf(12L, m.count - page * 12).toInt())
                val parts = (0 until entries.length()).map { part(entries.getJSONObject(it), m) }
                require(parts.withIndex().all { (n, p) -> p.index.toLong() == page * 12 + n })
                Envelope(m, event, parts)
            }
            else -> null
        }
    }.getOrNull()

    fun fromMessage(message: ChatMessage, manifest: Manifest): Part? = runCatching {
        if (message.msgDeleteTime != null || message.contentType != 4) return null
        val match = partPattern.matchEntire(message.content.fileName.orEmpty()) ?: return null
        if (match.groupValues[1] != manifest.id) return null
        val index = match.groupValues[2].toInt() - 1
        val value = JSONObject().put("index", index).put("size", message.content.fileSize)
            .put("sha256", match.groupValues[3]).put("key", message.content.fileUrl).put("messageId", message.msgId)
        part(value, manifest)
    }.getOrNull()

    fun partName(id: String, index: Int, sha256: String) = "yh-split-$id-${(index + 1).toString().padStart(5, '0')}-$sha256.part"

    fun serialize(manifest: Manifest): String = JSONObject().apply {
        put("type", "split_files"); put("v", 2); put("id", manifest.id); put("name", manifest.name)
        put("size", manifest.size); put("chunk", manifest.chunkSize); put("sha256", manifest.sha256); put("md5", manifest.md5)
        if (manifest.mime != "application/octet-stream") put("mime", manifest.mime)
    }.toString()
}
