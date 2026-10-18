package com.yhchat.canary.ui.share

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build

/**
 * 「发送到会话」选中的目标
 */
data class SendToChatTarget(
    val chatId: String,
    /** 1-好友 2-群聊 3-机器人 */
    val chatType: Int
)

/**
 * 媒体的种类，决定发出去的是图片消息 / 视频消息 / 附件消息
 */
enum class SendToChatMediaKind {
    IMAGE,
    VIDEO,
    FILE
}

/**
 * 一条待发送的媒体。
 *
 * 外部应用分享进来时，会按 [mimeType] 自动判定 [kind]：图片发图片消息、视频发视频消息，
 * 其余（音频、压缩包、文档……）都按附件发。
 */
data class SendToChatMediaItem(
    val uri: String,
    val kind: SendToChatMediaKind,
    /** 真实 MIME，能拿到才带 */
    val mimeType: String? = null
)

/**
 * 发送到会话的内容。
 *
 * 文本 / 图片 / 视频 / 附件 / 文章 / 转发消息 共用同一个「选择会话」界面：
 * 调用方只要调用 [SendToChatActivity.start]，把对应的实现类传进来即可。
 */
sealed interface SendToChatContent {

    /** 文本分享 */
    data class Text(val text: String) : SendToChatContent

    /** 图片 / 视频 / 附件分享（可多个，逐个发送） */
    data class Media(val items: List<SendToChatMediaItem>) : SendToChatContent

    /** 文章分享 */
    data class Post(
        val postId: String,
        val title: String,
        val content: String,
        /** 文章内容类型，默认 "1" */
        val postType: String = "1"
    ) : SendToChatContent

    /** 转发聊天消息（可多条，转发给同一批会话） */
    data class ForwardMessage(
        val msgIds: List<String>,
        val sourceChatType: Int
    ) : SendToChatContent

    /** 顶栏标题下面那行说明 */
    val summary: String
        get() = when (this) {
            is SendToChatContent.Text ->
                text.trim().replace("\n", " ").take(60).ifBlank { "文本" }

            is SendToChatContent.Media -> summaryOf(items)

            is SendToChatContent.Post -> title.ifBlank { "文章" }

            is SendToChatContent.ForwardMessage ->
                if (msgIds.size <= 1) "1 条消息" else "${msgIds.size} 条消息"
        }

    companion object {
        const val EXTRA_TITLE = "send_to_chat_title"
        const val EXTRA_KIND = "send_to_chat_kind"
        const val EXTRA_TEXT = "send_to_chat_text"
        const val EXTRA_URIS = "send_to_chat_uris"
        const val EXTRA_MEDIA_KINDS = "send_to_chat_media_kinds"
        const val EXTRA_MEDIA_MIMES = "send_to_chat_media_mimes"
        const val EXTRA_POST_ID = "send_to_chat_post_id"
        const val EXTRA_POST_TITLE = "send_to_chat_post_title"
        const val EXTRA_POST_CONTENT = "send_to_chat_post_content"
        const val EXTRA_POST_TYPE = "send_to_chat_post_type"
        const val EXTRA_MSG_IDS = "send_to_chat_msg_ids"
        const val EXTRA_SOURCE_CHAT_TYPE = "send_to_chat_source_chat_type"

        private const val KIND_TEXT = "text"
        private const val KIND_MEDIA = "media"
        private const val KIND_POST = "post"
        private const val KIND_FORWARD = "forward"

        // ==================== 应用内调用方的便捷入口 ====================

        /** 分享图片：一定按图片消息发 */
        fun images(uris: List<String>): Media =
            Media(uris.map { SendToChatMediaItem(it, SendToChatMediaKind.IMAGE) })

        /** 分享视频：一定按视频消息发 */
        fun videos(uris: List<String>): Media =
            Media(uris.map { SendToChatMediaItem(it, SendToChatMediaKind.VIDEO) })

        /** 分享附件：即使内容本身是图片，也按附件发（比如从群网盘分享） */
        fun files(uris: List<String>): Media =
            Media(uris.map { SendToChatMediaItem(it, SendToChatMediaKind.FILE) })

        // ==================== Intent 读写 ====================

        /** 把内容写进 Intent */
        fun writeTo(intent: Intent, content: SendToChatContent) {
            when (content) {
                is SendToChatContent.Text -> {
                    intent.putExtra(EXTRA_KIND, KIND_TEXT)
                    intent.putExtra(EXTRA_TEXT, content.text)
                }

                is SendToChatContent.Media -> {
                    intent.putExtra(EXTRA_KIND, KIND_MEDIA)
                    intent.putStringArrayListExtra(EXTRA_URIS, ArrayList(content.items.map { it.uri }))
                    intent.putStringArrayListExtra(
                        EXTRA_MEDIA_KINDS,
                        ArrayList(content.items.map { it.kind.name })
                    )
                    intent.putStringArrayListExtra(
                        EXTRA_MEDIA_MIMES,
                        ArrayList(content.items.map { it.mimeType.orEmpty() })
                    )
                }

                is SendToChatContent.Post -> {
                    intent.putExtra(EXTRA_KIND, KIND_POST)
                    intent.putExtra(EXTRA_POST_ID, content.postId)
                    intent.putExtra(EXTRA_POST_TITLE, content.title)
                    intent.putExtra(EXTRA_POST_CONTENT, content.content)
                    intent.putExtra(EXTRA_POST_TYPE, content.postType)
                }

                is SendToChatContent.ForwardMessage -> {
                    intent.putExtra(EXTRA_KIND, KIND_FORWARD)
                    intent.putStringArrayListExtra(EXTRA_MSG_IDS, ArrayList(content.msgIds))
                    intent.putExtra(EXTRA_SOURCE_CHAT_TYPE, content.sourceChatType)
                }
            }
        }

        /**
         * 从 Intent 还原内容。
         *
         * 优先读本应用自己写的 extras；没有的话按系统分享（ACTION_SEND / ACTION_SEND_MULTIPLE）
         * 解析：文本、图片、视频、附件都支持。
         *
         * @param resolver 解析外部 Uri 真实 MIME 用（判断是图片还是视频），外部分享时传 context.contentResolver
         */
        fun fromIntent(intent: Intent?, resolver: ContentResolver? = null): SendToChatContent? {
            if (intent == null) return null

            when (intent.getStringExtra(EXTRA_KIND)) {
                KIND_TEXT -> intent.getStringExtra(EXTRA_TEXT)?.let { return SendToChatContent.Text(it) }

                KIND_MEDIA -> mediaFromExtras(intent)?.let { return it }

                KIND_POST -> {
                    val postId = intent.getStringExtra(EXTRA_POST_ID)
                    if (!postId.isNullOrBlank()) {
                        return SendToChatContent.Post(
                            postId = postId,
                            title = intent.getStringExtra(EXTRA_POST_TITLE).orEmpty(),
                            content = intent.getStringExtra(EXTRA_POST_CONTENT).orEmpty(),
                            postType = intent.getStringExtra(EXTRA_POST_TYPE)
                                ?.takeIf { it.isNotBlank() } ?: "1"
                        )
                    }
                }

                KIND_FORWARD -> {
                    val msgIds = intent.getStringArrayListExtra(EXTRA_MSG_IDS)
                    if (!msgIds.isNullOrEmpty()) {
                        return SendToChatContent.ForwardMessage(
                            msgIds = msgIds,
                            sourceChatType = intent.getIntExtra(EXTRA_SOURCE_CHAT_TYPE, 1)
                        )
                    }
                }
            }

            return fromSystemShare(intent, resolver)
        }

        // ==================== 内部实现 ====================

        private fun summaryOf(items: List<SendToChatMediaItem>): String =
            items.groupBy { it.kind }
                .toList()
                .sortedBy { (kind, _) -> kind.ordinal }
                .joinToString("、") { (kind, list) -> kindSummary(kind, list.size) }
                .ifBlank { "没有可发送的内容" }

        private fun kindSummary(kind: SendToChatMediaKind, count: Int): String = when (kind) {
            SendToChatMediaKind.IMAGE -> if (count <= 1) "1 张图片" else "$count 张图片"
            SendToChatMediaKind.VIDEO -> if (count <= 1) "1 个视频" else "$count 个视频"
            SendToChatMediaKind.FILE -> if (count <= 1) "1 个附件" else "$count 个附件"
        }

        private fun mediaFromExtras(intent: Intent): SendToChatContent.Media? {
            val uris = intent.getStringArrayListExtra(EXTRA_URIS).orEmpty()
            val kinds = intent.getStringArrayListExtra(EXTRA_MEDIA_KINDS).orEmpty()
            val mimes = intent.getStringArrayListExtra(EXTRA_MEDIA_MIMES).orEmpty()

            val items = uris.mapIndexedNotNull { index, raw ->
                raw.takeIf { it.isNotBlank() }?.let { uri ->
                    SendToChatMediaItem(
                        uri = uri,
                        kind = kinds.getOrNull(index)
                            ?.let { name -> SendToChatMediaKind.entries.firstOrNull { it.name == name } }
                            ?: SendToChatMediaKind.FILE,
                        mimeType = mimes.getOrNull(index)?.takeIf { it.isNotBlank() }
                    )
                }
            }

            return items.takeIf { it.isNotEmpty() }?.let { SendToChatContent.Media(it) }
        }

        /** 系统分享进来的文本 / 图片 / 视频 / 附件 */
        private fun fromSystemShare(
            intent: Intent,
            resolver: ContentResolver?
        ): SendToChatContent? {
            if (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_SEND_MULTIPLE) {
                return null
            }

            val streamUris = intent.streamUris()
            if (streamUris.isNotEmpty()) {
                // 有些 App 用 */* 分享图片/视频，所以以每个 Uri 的真实 MIME 为准
                val fallbackMime = intent.type?.takeIf { it.isNotBlank() && it != "*/*" }
                return SendToChatContent.Media(
                    streamUris.map { uri -> classify(uri, resolver, fallbackMime) }
                )
            }

            return intent.getStringExtra(Intent.EXTRA_TEXT)
                ?.takeIf { it.isNotBlank() }
                ?.let { SendToChatContent.Text(it) }
        }

        /** 判定一条分享进来的媒体是图片 / 视频 / 附件 */
        private fun classify(
            uri: Uri,
            resolver: ContentResolver?,
            fallbackMime: String?
        ): SendToChatMediaItem {
            val mime = resolver?.getType(uri)?.takeIf { it.isNotBlank() } ?: fallbackMime
            val kind = when {
                mime == null -> kindFromUri(uri)
                mime.startsWith("image/") -> SendToChatMediaKind.IMAGE
                mime.startsWith("video/") -> SendToChatMediaKind.VIDEO
                else -> SendToChatMediaKind.FILE
            }
            return SendToChatMediaItem(uri = uri.toString(), kind = kind, mimeType = mime)
        }

        /** MIME 拿不到时按扩展名猜 */
        private fun kindFromUri(uri: Uri): SendToChatMediaKind =
            when (uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase()) {
                "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif" ->
                    SendToChatMediaKind.IMAGE

                "mp4", "m4v", "mov", "mkv", "webm", "avi", "3gp", "3gpp", "flv", "wmv", "ts", "mpeg", "mpg" ->
                    SendToChatMediaKind.VIDEO

                else -> SendToChatMediaKind.FILE
            }

        @Suppress("DEPRECATION")
        private fun Intent.streamUris(): List<Uri> {
            val single = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            }
            if (single != null) return listOf(single)

            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java) ?: emptyList()
            } else {
                getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM) ?: emptyList()
            }
        }
    }
}
