package com.yhchat.canary.utils.splitfile

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * 大文件分块消息协议（YH LaoDa v2，兼容 v1）。
 *
 * 场景：超过单文件上限的文件被切成若干 `.part` 分块，作为普通文件消息逐条发出，
 * 前面跟一条 JSON 清单。接收端靠清单 + 分块文件名把散落的消息重新拼成一个「传输任务」。
 *
 * 关键点（都来自协议文档）：
 *   - v2 只有一条压缩单行 JSON 清单，**没有 index / complete 之类的辅助文本消息**；
 *   - 分块文件名固定为 `yh-split-<id>-<五位序号>-<块SHA256>.part`，从 1 开始；
 *   - 清单里的 count 不是传输字段，由 ceil(size/chunk) 推导；
 *   - 不同发送者的分块绝不能混进同一任务。
 *
 * 这个文件只做「解析与校验」，不做 UI，也不碰网络 —— 纯粹的消息层适配。
 */
object SplitFileProtocol {

    /** 分块文件名前缀 */
    const val FILE_PREFIX = "yh-split-"

    /** 分块文件扩展名 */
    const val FILE_SUFFIX = ".part"

    /** 块号固定五位，从 1 开始 */
    const val CHUNK_INDEX_WIDTH = 5

    // 分块名正则：yh-split-<id>-<五位序号>-<该块 sha256>.part
    // id 允许字母数字和 - _（服务端/发送端生成的传输 ID）
    private val CHUNK_NAME_REGEX =
        Regex("^yh-split-([A-Za-z0-9_-]+)-(\\d{$CHUNK_INDEX_WIDTH})-([A-Fa-f0-9]{64})\\.part$")

    /**
     * 解析一条消息是不是分块清单。
     *
     * 只认 `type == "split_files"` 的 JSON 文本。刻意不认那些「像 JSON 但不是清单」的内容：
     * 协议里明确指出，未适配的客户端会把清单当成一条普通消息显示，
     * 所以这里的判定必须严格，误判会把正常聊天内容吃掉。
     *
     * @return 解析成功返回清单；不是清单或格式不对返回 null（不抛异常）
     */
    fun parseManifest(text: String?): SplitManifest? {
        if (text.isNullOrBlank()) return null
        val trimmed = text.trim()
        // 快速筛掉明显不是 JSON 的文本，避免对每条消息都跑一次 parser
        if (!trimmed.startsWith("{")) return null
        if (!trimmed.contains("\"split_files\"")) return null

        return try {
            val root = JsonParser.parseString(trimmed)
            if (!root.isJsonObject) return null
            parseManifestObject(root.asJsonObject)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseManifestObject(obj: JsonObject): SplitManifest? {
        val type = obj.optStringOrNull("type") ?: return null
        if (type != "split_files") return null

        // 兼容 v1：v1 的清单嵌在 manifest 字段里，且带 protocol / event 包装。
        // 文档要求"继续接收 v1 清单"，所以这里把两种形状都归一化成同一个模型。
        val payload = if (obj.has("manifest") && obj.get("manifest").isJsonObject) {
            obj.getAsJsonObject("manifest")
        } else {
            obj
        }

        val version = payload.optIntOrNull("v")
            ?: obj.optIntOrNull("v")
            ?: 1

        val id = payload.optStringOrNull("id")
            ?: obj.optStringOrNull("id")
            ?: return null
        if (id.isBlank()) return null

        val name = payload.optStringOrNull("name")
            ?: obj.optStringOrNull("name")
            ?: return null

        // size / chunk 必须是整数 —— 协议明确要求。浮点数说明发送端实现有问题，
        // 这种清单宁可不认，也不要算出错误的块数
        val size = payload.optLongOrNull("size") ?: return null
        val chunk = payload.optLongOrNull("chunk") ?: return null
        if (size <= 0L || chunk <= 0L) return null

        val sha256 = payload.optStringOrNull("sha256")?.lowercase() ?: return null
        val md5 = payload.optStringOrNull("md5")?.lowercase().orEmpty()

        val mime = payload.optStringOrNull("mime")
            // 协议：mime 仅在不等于 application/octet-stream 时才带
            ?.takeIf { it.isNotBlank() && it != "application/octet-stream" }

        // count 由 size 和 chunk 推导，不读字段
        val count = ((size + chunk - 1) / chunk).toInt()
        if (count <= 0) return null

        return SplitManifest(
            version = version,
            transferId = id,
            fileName = name,
            totalSize = size,
            chunkSize = chunk,
            chunkCount = count,
            sha256 = sha256,
            md5 = md5,
            mime = mime
        )
    }

    /**
     * 从分块文件名里取出传输 ID、块号和块哈希。
     *
     * 协议规定接收端「从文件名获取传输 ID、块号、块哈希」，资源 key / 消息 ID / 大小
     * 则从真实文件消息上取 —— 这条边界很重要，文件名不可信，不能拿它当文件大小依据。
     *
     * @return 不是分块文件、或格式不合法时返回 null
     */
    fun parseChunkFileName(fileName: String?): SplitChunkName? {
        if (fileName.isNullOrBlank()) return null
        // 只取最后一段，防止带有路径的前缀（某些发送端会带目录）
        val baseName = fileName.substringAfterLast('/').substringAfterLast('\\')
        val match = CHUNK_NAME_REGEX.matchEntire(baseName) ?: return null
        val (id, indexRaw, hash) = match.destructured
        val index = indexRaw.toIntOrNull() ?: return null
        if (index < 1) return null
        return SplitChunkName(
            transferId = id,
            chunkIndex = index,
            chunkSha256 = hash.lowercase()
        )
    }

    /** 判断一个文件名是不是分块文件（不要求完全合法，用于快速过滤） */
    fun looksLikeChunkFile(fileName: String?): Boolean {
        if (fileName.isNullOrBlank()) return false
        val baseName = fileName.substringAfterLast('/').substringAfterLast('\\')
        return baseName.startsWith(FILE_PREFIX) && baseName.endsWith(FILE_SUFFIX)
    }

    /**
     * 生成分块文件名。上传侧用；接收侧用 [parseChunkFileName]。
     *
     * 这个项目目前只做接收适配，写在这里是为了让格式定义只有一个来源 ——
     * 将来接上传时不会因为两处各写一遍而对不上。
     */
    fun buildChunkFileName(transferId: String, chunkIndex: Int, chunkSha256: String): String {
        require(transferId.isNotBlank()) { "transferId 不能为空" }
        require(chunkIndex >= 1) { "块号从 1 开始" }
        require(chunkSha256.length == 64) { "块哈希应为 64 位十六进制" }
        return "$FILE_PREFIX$transferId-${chunkIndex.toString().padStart(CHUNK_INDEX_WIDTH, '0')}-${chunkSha256.lowercase()}$FILE_SUFFIX"
    }
}

/**
 * 分块清单（v1 / v2 归一化后的统一形状）。
 */
data class SplitManifest(
    /** 1 或 2，仅用于展示和日志，逻辑上已经归一化 */
    val version: Int,
    /** 传输 ID，用于把清单和分块关联起来 */
    val transferId: String,
    /** 合并后的文件名 */
    val fileName: String,
    /** 合并后的总字节数 */
    val totalSize: Long,
    /** 每块字节数 */
    val chunkSize: Long,
    /** 块数 = ceil(totalSize / chunkSize) */
    val chunkCount: Int,
    /** 合并后的完整 SHA-256（64 位小写十六进制） */
    val sha256: String,
    /** 合并后的完整 MD5，可选 */
    val md5: String,
    /** MIME，可选 */
    val mime: String?
)

/** 从分块文件名解析出的信息 */
data class SplitChunkName(
    val transferId: String,
    /** 1 基块号 */
    val chunkIndex: Int,
    /** 该块的 SHA-256 */
    val chunkSha256: String
)

// optXxx 的辅助：Gson 的 optString 在字段缺失时返回 ""，这里要能区分"没有"和"空串"
private fun JsonObject.optStringOrNull(key: String): String? {
    val element = get(key) ?: return null
    if (element.isJsonNull) return null
    if (!element.isJsonPrimitive) return null
    val primitive = element.asJsonPrimitive
    return if (primitive.isString) primitive.asString else primitive.asString
}

private fun JsonObject.optIntOrNull(key: String): Int? {
    val element = get(key) ?: return null
    if (!element.isJsonPrimitive) return null
    val primitive = element.asJsonPrimitive
    if (!primitive.isNumber) return null
    // 协议要求整数：3.0 这种浮点表示也接受（JSON 里不区分），但 3.5 直接拒绝
    val asDouble = primitive.asDouble
    if (asDouble != asDouble.toInt().toDouble()) return null
    return asDouble.toInt()
}

private fun JsonObject.optLongOrNull(key: String): Long? {
    val element = get(key) ?: return null
    if (!element.isJsonPrimitive) return null
    val primitive = element.asJsonPrimitive
    if (!primitive.isNumber) return null
    val asDouble = primitive.asDouble
    if (asDouble != asDouble.toLong().toDouble()) return null
    return asDouble.toLong()
}
