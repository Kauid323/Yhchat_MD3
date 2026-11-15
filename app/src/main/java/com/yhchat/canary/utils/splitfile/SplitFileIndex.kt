package com.yhchat.canary.utils.splitfile

import com.yhchat.canary.data.model.ChatMessage

/**
 * 一个分块文件消息在任务里的位置。
 *
 * 资源 key / 消息 ID / 大小都从**真实文件消息**上取，而不是从文件名猜 ——
 * 文件名只提供块号和块哈希。
 */
data class SplitChunkEntry(
    val chunkIndex: Int,
    /** 该块声明的 SHA-256（来自文件名） */
    val chunkSha256: String,
    /** 文件消息里的资源 key（裸 key，不含 URL 前缀） */
    val resourceKey: String,
    /** 消息 ID，用于去重和撤回判断 */
    val msgId: String,
    /** 该分块文件的实际大小 */
    val size: Long,
    /** 分块文件名（用于展示和排错） */
    val fileName: String,
    /** 下载地址 */
    val fileUrl: String
)

/**
 * 一个完整的传输任务：清单 + 已就位的分块。
 *
 * 协议里有几条硬约束，这里都用数据结构表达出来：
 *   - **不同发送者不能混进同一任务**：任务身份是 (会话类型, 会话 ID, 发送者, 传输 ID)；
 *   - **撤回的文件不纳入索引**；
 *   - **重复索引去重，冲突索引报错**。
 */
data class SplitTransferTask(
    /** 任务身份，见 [SplitTaskKey] */
    val key: SplitTaskKey,
    val manifest: SplitManifest,
    /** 已索引到的分块，按块号排序 */
    val chunks: List<SplitChunkEntry>,
    /** 索引过程中发现的冲突（同一块号出现不同资源） */
    val conflicts: List<SplitChunkConflict>
) {
    val receivedCount: Int get() = chunks.size

    val missingCount: Int get() = (manifest.chunkCount - chunks.size).coerceAtLeast(0)

    /** 缺块明确显示数量 —— 协议要求，所以这里直接给列表 */
    val missingIndexes: List<Int>
        get() {
            val have = chunks.mapTo(mutableSetOf()) { it.chunkIndex }
            return (1..manifest.chunkCount).filter { it !in have }
        }

    /** 块数齐全但没做完整性校验前，只能叫「可下载」 */
    val isReadyToMerge: Boolean get() = missingCount == 0 && conflicts.isEmpty()

    /** 合并后的总大小应当与清单一致，便于尽早发现异常 */
    val receivedBytes: Long get() = chunks.sumOf { it.size }
}

/**
 * 任务身份。
 *
 * 协议明确要求「识别依据为会话类型、会话 ID、发送者及传输 ID」，
 * 「不同发送者的分块不能混入同一任务」—— 这个 key 就是该规则的落地。
 */
data class SplitTaskKey(
    val chatType: Int,
    val chatId: String,
    val senderId: String,
    val transferId: String
)

/** 同一块号被两条不同消息占用（资源 key 或消息 ID 不一致） */
data class SplitChunkConflict(
    val chunkIndex: Int,
    val existingMsgId: String,
    val incomingMsgId: String,
    val existingResourceKey: String,
    val incomingResourceKey: String
)

/**
 * 分块消息索引。
 *
 * 设计取舍：协议要求「先处理当前批次中的清单，再处理文件」，且「支持历史消息倒序返回、
 * 实时到达及并行乱序」。所以这里做成**增量式**的：清单和分块可以按任意顺序喂进来，
 * 索引只负责累积，任何时候都能问「现在有哪些任务、每个任务缺哪些块」。
 *
 * 这不是线程安全类 —— 调用方（ViewModel / Repository）应在单一线程或加锁使用。
 */
class SplitFileIndex {

    // 清单：按任务身份存
    private val manifests = mutableMapOf<SplitTaskKey, SplitManifest>()

    // 分块：任务身份 -> 块号 -> 分块
    private val chunks = mutableMapOf<SplitTaskKey, MutableMap<Int, SplitChunkEntry>>()

    // 冲突记录
    private val conflicts = mutableMapOf<SplitTaskKey, MutableList<SplitChunkConflict>>()

    // 已撤回的消息 ID：命中就不索引
    private val recalledMsgIds = mutableSetOf<String>()

    // 清单所依附的消息 ID，用于「手动下载时从清单附近向前查分块」
    private val manifestMsgIds = mutableMapOf<SplitTaskKey, String>()

    /**
     * 标记一条消息已撤回。
     *
     * 协议：撤回的文件不纳入索引。撤回可能在分块已经索引之后才到达，
     * 所以这里同时把已索引的那条剔掉。
     */
    fun markRecalled(msgId: String) {
        if (msgId.isBlank()) return
        recalledMsgIds += msgId
        // 从已有索引里剔除
        chunks.values.forEach { byIndex ->
            val hit = byIndex.entries.firstOrNull { it.value.msgId == msgId } ?: return@forEach
            byIndex.remove(hit.key)
        }
    }

    fun isRecalled(msgId: String): Boolean = msgId in recalledMsgIds

    /**
     * 喂入一条聊天消息。清单或分块都可以，顺序无所谓。
     *
     * @return 这条消息产生了什么变化，便于 UI 决定要不要刷新
     */
    fun offer(message: ChatMessage): SplitOfferResult {
        val msgId = message.msgId
        if (msgId.isBlank()) return SplitOfferResult.Ignored
        if (isRecalled(msgId)) return SplitOfferResult.Ignored

        // 1) 先试清单：清单一定是文本消息
        val manifest = SplitFileProtocol.parseManifest(message.content.text)
        if (manifest != null) {
            val key = taskKeyOf(message, manifest.transferId) ?: return SplitOfferResult.Ignored
            val existing = manifests[key]
            if (existing == null) {
                manifests[key] = manifest
                manifestMsgIds[key] = msgId
                return SplitOfferResult.ManifestAdded(key)
            }
            // 同一任务重复收到清单：协议要求"重复索引去重"。
            // 但如果内容不一致，保留先到的，不覆盖 —— 先到的通常是对应本批分块的那条
            return if (existing == manifest) {
                SplitOfferResult.Duplicate
            } else {
                SplitOfferResult.Duplicate
            }
        }

        // 2) 再试分块文件
        val chunkName = SplitFileProtocol.parseChunkFileName(message.content.fileName)
            ?: return SplitOfferResult.Ignored

        val resourceKey = extractResourceKey(message.content.fileUrl)
            ?: return SplitOfferResult.Ignored

        val size = message.content.fileSize ?: 0L
        if (size <= 0L) return SplitOfferResult.Ignored

        val key = taskKeyOf(message, chunkName.transferId) ?: return SplitOfferResult.Ignored

        val byIndex = chunks.getOrPut(key) { mutableMapOf() }
        val incoming = SplitChunkEntry(
            chunkIndex = chunkName.chunkIndex,
            chunkSha256 = chunkName.chunkSha256,
            resourceKey = resourceKey,
            msgId = msgId,
            size = size,
            fileName = message.content.fileName.orEmpty(),
            fileUrl = message.content.fileUrl.orEmpty()
        )

        val existing = byIndex[chunkName.chunkIndex]
        if (existing == null) {
            byIndex[chunkName.chunkIndex] = incoming
            return SplitOfferResult.ChunkAdded(key, chunkName.chunkIndex)
        }

        // 同一块号重复出现：
        //   - 资源 key 和消息 ID 都一样 → 真·重复，去重即可（协议：重复索引去重）
        //   - 否则就是冲突（协议：冲突索引报错）
        return if (existing.resourceKey == incoming.resourceKey && existing.msgId == incoming.msgId) {
            SplitOfferResult.Duplicate
        } else {
            conflicts.getOrPut(key) { mutableListOf() }.add(
                SplitChunkConflict(
                    chunkIndex = chunkName.chunkIndex,
                    existingMsgId = existing.msgId,
                    incomingMsgId = incoming.msgId,
                    existingResourceKey = existing.resourceKey,
                    incomingResourceKey = incoming.resourceKey
                )
            )
            // 保留先到的：分块内容可能完全不同，随便换成后到的会拼出坏文件
            SplitOfferResult.ChunkConflict(key, chunkName.chunkIndex)
        }
    }

    /** 当前所有任务（有清单且至少有一个分块） */
    fun snapshot(): List<SplitTransferTask> {
        return manifests.map { (key, manifest) ->
            val byIndex = chunks[key].orEmpty()
            SplitTransferTask(
                key = key,
                manifest = manifest,
                chunks = byIndex.values.sortedBy { it.chunkIndex },
                conflicts = conflicts[key].orEmpty().toList()
            )
        }
    }

    /** 取单个任务 */
    fun task(key: SplitTaskKey): SplitTransferTask? {
        val manifest = manifests[key] ?: return null
        return SplitTransferTask(
            key = key,
            manifest = manifest,
            chunks = chunks[key].orEmpty().values.sortedBy { it.chunkIndex },
            conflicts = conflicts[key].orEmpty().toList()
        )
    }

    /**
     * 已经收到清单的任务身份。
     *
     * 协议要求：清单尚未加载、且本机无匹配任务的孤立分块**仍显示为普通文件**。
     * UI 判断某个分块该不该画成传输卡片，就要先问这个方法 ——
     * 只有清单到齐了，散落的分块才应该被"收编"进卡片。
     */
    fun hasManifest(key: SplitTaskKey): Boolean = manifests.containsKey(key)

    /**
     * 某个分块对应的任务身份（不管清单到了没有）。
     *
     * 分块先到时调用方拿它去查 [hasManifest]，据此决定显示成
     * 「孤立分块（普通文件）」还是「传输卡片的一部分」。
     */
    fun taskKeyOfChunk(msgId: String): SplitTaskKey? {
        chunks.forEach { (key, byIndex) ->
            if (byIndex.values.any { it.msgId == msgId }) return key
        }
        return null
    }

    /** 某个传输 ID 对应的任务身份（用于按文件名反查） */
    fun findTaskKeyByTransferId(transferId: String): SplitTaskKey? =
        manifests.keys.firstOrNull { it.transferId == transferId }

    /** 清单消息 ID，用于「从清单附近向前查分块」 */
    fun manifestMsgIdOf(key: SplitTaskKey): String? = manifestMsgIds[key]

    /**
     * 分块是否已被识别为任务的一部分。
     *
     * 协议要求：清单尚未加载、且本机无匹配任务的孤立分块，**仍显示为普通文件**，
     * 需要加载到它前面的清单才能变成传输卡片。UI 层用这个判定即可。
     */
    fun isKnownChunk(msgId: String): Boolean =
        chunks.values.any { byIndex -> byIndex.values.any { it.msgId == msgId } }

    /** 清空（切换会话/账号时调用，避免串数据） */
    fun clear() {
        manifests.clear()
        chunks.clear()
        conflicts.clear()
        recalledMsgIds.clear()
        manifestMsgIds.clear()
    }

    /**
     * 任务身份：会话类型 + 会话 ID + 发送者 + 传输 ID。
     *
     * 群聊里同一条分块可能由不同人发，协议明确禁止混任务，所以发送者必须进 key。
     */
    private fun taskKeyOf(message: ChatMessage, transferId: String): SplitTaskKey? {
        val chatId = message.chatId
            ?: message.recvId
            ?: return null
        val chatType = message.chatType
            ?: message.sender.chatType
        val senderId = message.sender.chatId
        if (chatId.isBlank() || senderId.isBlank()) return null
        return SplitTaskKey(
            chatType = chatType,
            chatId = chatId,
            senderId = senderId,
            transferId = transferId
        )
    }

    /**
     * 从文件 URL 里取出「裸资源 key」。
     *
     * 协议说资源 key 要从真实文件消息获取，且"裸资源 key"需要通过原有校验。
     * 云湖的文件 URL 形如 `https://chat-img.jwznb.com/<key>` 或带 query，
     * 这里取最后一段路径并去掉 query —— 和下载器拼 URL 的方式保持一致。
     */
    private fun extractResourceKey(fileUrl: String?): String? {
        if (fileUrl.isNullOrBlank()) return null
        val withoutQuery = fileUrl.substringBefore('?').substringBefore('#')
        val lastSegment = withoutQuery.trimEnd('/').substringAfterLast('/')
        return lastSegment.takeIf { it.isNotBlank() }
    }
}

/** 喂入一条消息后的结果 */
sealed interface SplitOfferResult {
    /** 与分块协议无关，忽略 */
    data object Ignored : SplitOfferResult

    /** 重复内容，已去重 */
    data object Duplicate : SplitOfferResult

    /** 新增了一个清单 */
    data class ManifestAdded(val key: SplitTaskKey) : SplitOfferResult

    /** 新增了一个分块 */
    data class ChunkAdded(val key: SplitTaskKey, val chunkIndex: Int) : SplitOfferResult

    /** 同一块号出现不同资源，属于冲突 */
    data class ChunkConflict(val key: SplitTaskKey, val chunkIndex: Int) : SplitOfferResult
}
