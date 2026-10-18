package com.yhchat.canary.ui.share

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
 * 发送到会话的内容。
 *
 * 文本 / 图片 / 附件 / 文章 / 转发消息 共用同一个「选择会话」界面：
 * 调用方只要调用 [SendToChatActivity.start]，把对应的实现类传进来即可。
 */
sealed interface SendToChatContent {

    /** 文本分享 */
    data class Text(val text: String) : SendToChatContent

    /** 图片分享（可多张，逐张发送） */
    data class Images(val uris: List<String>) : SendToChatContent

    /** 附件分享（可多个，逐个发送） */
    data class Files(val uris: List<String>) : SendToChatContent

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

            is SendToChatContent.Images -> if (uris.size <= 1) "1 张图片" else "${uris.size} 张图片"

            is SendToChatContent.Files -> if (uris.size <= 1) "1 个附件" else "${uris.size} 个附件"

            is SendToChatContent.Post -> title.ifBlank { "文章" }

            is SendToChatContent.ForwardMessage ->
                if (msgIds.size <= 1) "1 条消息" else "${msgIds.size} 条消息"
        }

    companion object {
        const val EXTRA_TITLE = "send_to_chat_title"
        const val EXTRA_KIND = "send_to_chat_kind"
        const val EXTRA_TEXT = "send_to_chat_text"
        const val EXTRA_URIS = "send_to_chat_uris"
        const val EXTRA_POST_ID = "send_to_chat_post_id"
        const val EXTRA_POST_TITLE = "send_to_chat_post_title"
        const val EXTRA_POST_CONTENT = "send_to_chat_post_content"
        const val EXTRA_POST_TYPE = "send_to_chat_post_type"
        const val EXTRA_MSG_IDS = "send_to_chat_msg_ids"
        const val EXTRA_SOURCE_CHAT_TYPE = "send_to_chat_source_chat_type"

        private const val KIND_TEXT = "text"
        private const val KIND_IMAGES = "images"
        private const val KIND_FILES = "files"
        private const val KIND_POST = "post"
        private const val KIND_FORWARD = "forward"

        /** 把内容写进 Intent */
        fun writeTo(intent: Intent, content: SendToChatContent) {
            when (content) {
                is SendToChatContent.Text -> {
                    intent.putExtra(EXTRA_KIND, KIND_TEXT)
                    intent.putExtra(EXTRA_TEXT, content.text)
                }

                is SendToChatContent.Images -> {
                    intent.putExtra(EXTRA_KIND, KIND_IMAGES)
                    intent.putStringArrayListExtra(EXTRA_URIS, ArrayList(content.uris))
                }

                is SendToChatContent.Files -> {
                    intent.putExtra(EXTRA_KIND, KIND_FILES)
                    intent.putStringArrayListExtra(EXTRA_URIS, ArrayList(content.uris))
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
         * 解析：文本、图片、附件三种都支持。以后把图片分享 / 附件分享 / 文本分享接到
         * 系统分享面板时，这里不用再改。
         */
        fun fromIntent(intent: Intent?): SendToChatContent? {
            if (intent == null) return null

            when (intent.getStringExtra(EXTRA_KIND)) {
                KIND_TEXT -> intent.getStringExtra(EXTRA_TEXT)?.let { return SendToChatContent.Text(it) }

                KIND_IMAGES -> uriStrings(intent)?.let { return SendToChatContent.Images(it) }

                KIND_FILES -> uriStrings(intent)?.let { return SendToChatContent.Files(it) }

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

            return fromSystemShare(intent)
        }

        private fun uriStrings(intent: Intent): List<String>? =
            intent.getStringArrayListExtra(EXTRA_URIS)
                ?.filter { it.isNotBlank() }
                ?.takeIf { it.isNotEmpty() }

        /** 系统分享进来的文本 / 图片 / 附件 */
        private fun fromSystemShare(intent: Intent): SendToChatContent? {
            if (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_SEND_MULTIPLE) {
                return null
            }

            val streamUris = intent.streamUris()
            if (streamUris.isNotEmpty()) {
                val uris = streamUris.map { it.toString() }
                return if (intent.type?.startsWith("image/") == true) {
                    SendToChatContent.Images(uris)
                } else {
                    SendToChatContent.Files(uris)
                }
            }

            return intent.getStringExtra(Intent.EXTRA_TEXT)
                ?.takeIf { it.isNotBlank() }
                ?.let { SendToChatContent.Text(it) }
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
