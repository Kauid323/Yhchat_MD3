package com.yhchat.canary.utils.splitfile

import com.yhchat.canary.data.model.ChatMessage

/**
 * 分块消息适配的对外入口。
 *
 * 上层（聊天页、会话列表）只需要认识这个类：
 *   - 消息进来时调 [offer]；
 *   - 渲染某条消息时调 [displayModeOf] 判断该画成传输卡片还是普通文件；
 *   - 需要任务列表时调 [tasks]。
 *
 * 内部把「解析 / 索引 / 校验」拆成了三个文件，这里只做编排，
 * 不掺业务判断，方便以后接上传方向时复用。
 *
 * ## 使用要点
 *
 * 1. **按会话隔离**：切换会话或账号时必须 [clear]，否则会把别的群的
 *    同名 transferId 搅在一起（协议禁止跨发送者、跨会话混任务）。
 * 2. **顺序无关**：清单和分块可以任意顺序喂进来，包括历史倒序和实时到达。
 * 3. **孤立分块不算任务**：清单没到之前，分块仍应显示为普通文件。
 */
class SplitFileAdapter {

    private val index = SplitFileIndex()

    /** 喂入一条消息，返回它的归类结果 */
    fun offer(message: ChatMessage): SplitOfferResult = index.offer(message)

    /** 批量喂入（历史消息一次性加载时用） */
    fun offerAll(messages: List<ChatMessage>): List<SplitOfferResult> =
        messages.map { index.offer(it) }

    /** 标记消息撤回；撤回的分块会从任务中剔除 */
    fun markRecalled(msgId: String) = index.markRecalled(msgId)

    /** 当前所有任务 */
    fun tasks(): List<SplitTransferTask> = index.snapshot()

    /** 某个传输 ID 对应的任务 */
    fun taskOf(transferId: String): SplitTransferTask? =
        index.findTaskKeyByTransferId(transferId)?.let { index.task(it) }

    /** 任务里还没收到的块号，用于「缺块显示数量并允许重试」 */
    fun missingIndexesOf(transferId: String): List<Int> =
        taskOf(transferId)?.missingIndexes.orEmpty()

    /** 清空索引（切会话 / 切账号时调用） */
    fun clear() = index.clear()

    /**
     * 决定一条消息该怎么显示。
     *
     * 这是给 UI 用的唯一判定入口，把协议里那几条"什么时候才算传输卡片"的规则
     * 收敛在一处，避免各个界面各写一遍判断而出现不一致。
     */
    fun displayModeOf(message: ChatMessage): SplitDisplayMode {
        // 清单本身永远显示成卡片（它就是任务的入口）
        val manifest = SplitFileProtocol.parseManifest(message.content.text)
        if (manifest != null) {
            return SplitDisplayMode.ManifestCard(manifest)
        }

        // 不是分块文件名 → 普通消息
        val chunkName = SplitFileProtocol.parseChunkFileName(message.content.fileName)
            ?: return SplitDisplayMode.Normal

        return when {
            // 已撤回
            index.isRecalled(message.msgId) -> SplitDisplayMode.Normal

            // 被索引进某个任务、且该任务的清单已经到齐 → 属于卡片，折叠起来不再单独显示
            index.isKnownChunk(message.msgId) -> {
                val key = index.taskKeyOfChunk(message.msgId)
                if (key != null && index.hasManifest(key)) {
                    SplitDisplayMode.PartOfTask(key)
                } else {
                    // 只有分块、没有清单：协议要求仍显示为普通文件
                    SplitDisplayMode.OrphanChunk(chunkName)
                }
            }

            // 完全没索引上（可能因为大小/资源 key 不合法），退化成普通文件
            else -> SplitDisplayMode.OrphanChunk(chunkName)
        }
    }
}

/** 一条消息在分块适配视角下的显示方式 */
sealed interface SplitDisplayMode {
    /** 普通消息，与分块无关 */
    data object Normal : SplitDisplayMode

    /** 这是一条清单，应渲染成传输任务卡片 */
    data class ManifestCard(val manifest: SplitManifest) : SplitDisplayMode

    /** 属于某个已建卡的任务，折叠进卡片，不在消息流里单独出现 */
    data class PartOfTask(val key: SplitTaskKey) : SplitDisplayMode

    /**
     * 孤立分块：文件名像分块，但还没有对应的清单。
     *
     * 协议明确要求这种情况**仍显示为普通文件** —— 因为清单可能还在更早的历史里，
     * 用户往前翻加载到清单之后，这条会自动变成卡片的一部分。
     */
    data class OrphanChunk(val chunkName: SplitChunkName) : SplitDisplayMode
}
