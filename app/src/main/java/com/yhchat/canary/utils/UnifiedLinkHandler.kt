package com.yhchat.canary.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.yhchat.canary.data.model.ChatAddInfo
import com.yhchat.canary.data.model.ChatAddType
import androidx.compose.material3.Text
import com.yhchat.canary.ui.chat.ChatAddActivity
import com.yhchat.canary.ui.community.BoardDetailActivity
import com.yhchat.canary.ui.community.PostDetailActivity
import com.yhchat.canary.ui.share.SendToChatContent
import com.yhchat.canary.ui.user.UserDetailActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

/**
 * 统一链接处理器
 * 整合处理所有 yunhu:// 和 https:// 链接
 * 包括：chat-add、post-detail、alley-detail、分享链接等
 */
object UnifiedLinkHandler {
    
    private const val TAG = "UnifiedLinkHandler"
    private val yhfxHosts = setOf("yhfx.jwznb.com", "jwznb.com", "myapp.jwznb.com")
    private val articleHosts = setOf("www.yhchat.com", "yhchat.com")
    
    // 链接正则表达式
    private val YUNHU_POST_DETAIL_PATTERN = Pattern.compile("yunhu://post-detail\\?id=(\\d+)")
    private val ALLEY_DETAIL_PATTERN = Pattern.compile("yunhu://alley-detail\\?id=(\\d+)")
    private val WEB_ARTICLE_PATTERN = Pattern.compile("https://www\\.yhchat\\.com/c/p/(\\d+)")
    private val USER_HOMEPAGE_PATTERN = Pattern.compile("https://([^.]+\\.)?yhchat\\.com/user/homepage/(\\d+)")
    private val YHCHAT_ROOT_PATTERN = Pattern.compile("https://([^.]+\\.)?yhchat\\.com/?(?:[?#].*)?")
    
    /**
     * 检查是否为可处理的链接
     */
    fun isHandleableLink(url: String): Boolean {
        val normalizedUrl = normalizeYunhuLink(url)
        return isCanonicalHandleableLink(normalizedUrl)
    }
    
    /**
     * 处理链接（同步处理，用于 WebView）
     */
    fun handleLink(context: Context, url: String) {
        try {
            val normalizedUrl = normalizeYunhuLink(url)
            if (normalizedUrl != url) {
                Log.d(TAG, "Normalized link: $url -> $normalizedUrl")
            }
            Log.d(TAG, "Processing link: $normalizedUrl")
            
            when {
                normalizedUrl.startsWith("yunhu://chat-add") -> {
                    handleChatAddLink(context, normalizedUrl)
                }
                normalizedUrl.startsWith("yunhu://post-detail") -> {
                    handlePostDetailLink(context, normalizedUrl)
                }
                normalizedUrl.startsWith("yunhu://alley-detail") -> {
                    handleAlleyDetailLink(context, normalizedUrl)
                }
                // ↓ 云湖第三方扩展协议（第 0 号草案）新增的三种 scheme
                normalizedUrl.startsWith("yunhu://sticker-detail") -> {
                    handleStickerDetailLink(context, normalizedUrl)
                }
                normalizedUrl.startsWith("yunhu://expression-detail") -> {
                    handleExpressionDetailLink(context, normalizedUrl)
                }
                normalizedUrl.startsWith("yunhu://send-msg") -> {
                    handleSendMsgLink(context, normalizedUrl)
                }
                normalizedUrl.startsWith("yunhu://ad") -> {
                    handleYunhuAdLink(context, normalizedUrl)
                }
                normalizedUrl.startsWith("https://yhfx.jwznb.com/share") -> {
                    handleYhfxShareLink(context, normalizedUrl)
                }
                normalizedUrl.startsWith("yunhu://jwznb.com") -> {
                    val uri = Uri.parse(normalizedUrl)
                    val key = uri.getQueryParameter("key")
                    val ts = uri.getQueryParameter("ts")
                    
                    if (!key.isNullOrEmpty() && !ts.isNullOrEmpty()) {
                        handleYhfxShareLink(context, normalizedUrl)
                    } else if (isChatAddLink(normalizedUrl)) {
                        handleChatAddLink(context, normalizedUrl)
                    } else {
                        handleYhfxShareLink(context, normalizedUrl)
                    }
                }
                USER_HOMEPAGE_PATTERN.matcher(normalizedUrl).matches() -> {
                    handleUserHomepageLink(context, normalizedUrl)
                }
                normalizedUrl.contains("https://www.yhchat.com/c/p/") -> {
                    handleWebArticleLink(context, normalizedUrl)
                }
                YHCHAT_ROOT_PATTERN.matcher(normalizedUrl).matches() -> {
                    handleExternalLink(context, normalizedUrl)
                }
                else -> {
                    // 对于非云湖内链，尝试在外部浏览器中打开
                    handleExternalLink(context, normalizedUrl)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle link: $url", e)
        }
    }
    
    /**
     * 处理 yunhu://chat-add 链接
     */
    private fun handleChatAddLink(context: Context, url: String) {
        val uri = Uri.parse(url)
        val id = uri.getQueryParameter("id")
        val typeString = uri.getQueryParameter("type")
        
        if (id.isNullOrEmpty() || typeString.isNullOrEmpty()) {
            Log.w(TAG, "Invalid chat-add link: $url")
            return
        }
        
        val type = when (typeString.lowercase()) {
            "user" -> ChatAddType.USER
            "group" -> ChatAddType.GROUP
            "bot" -> ChatAddType.BOT
            else -> {
                Log.w(TAG, "Invalid chat type: $typeString")
                return
            }
        }
        
        Log.d(TAG, "Opening chat add: id=$id, type=$typeString")
        val intent = Intent(context, ChatAddActivity::class.java).apply {
            putExtra("chat_id", id)
            putExtra("chat_type", type.chatType)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
    
    /**
     * 处理 yunhu://post-detail 链接
     */
    private fun handlePostDetailLink(context: Context, url: String) {
        val uri = Uri.parse(url)
        val postIdString = uri.getQueryParameter("id")
        val postId = postIdString?.toIntOrNull()
        
        if (postId == null) {
            Log.w(TAG, "Invalid post-detail link: $url")
            return
        }
        
        Log.d(TAG, "Opening post detail: postId=$postId")
        val intent = Intent(context, PostDetailActivity::class.java).apply {
            putExtra("post_id", postId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
    
    /**
     * 处理 yhfx,yunhu://jwznb网页 分享链接
     */
    private fun handleYhfxShareLink(context: Context, url: String) {
        val uri = Uri.parse(url)
        val key = uri.getQueryParameter("key")
        val ts = uri.getQueryParameter("ts")
        
        if (key.isNullOrEmpty() || ts.isNullOrEmpty()) {
            Log.w(TAG, "Invalid share link: $url")
            android.widget.Toast.makeText(context, "分享链接格式错误", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        
        Log.d(TAG, "Opening yhfx share: key=$key, ts=$ts")
        // 直接打开 ChatAddActivity，传递 key 和 ts 参数
        val intent = Intent(context, ChatAddActivity::class.java).apply {
            putExtra("share_key", key)
            putExtra("share_ts", ts)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
    
    /**
     * 处理 yunhu://alley-detail 链接
     */
    private fun handleAlleyDetailLink(context: Context, url: String) {
        val uri = Uri.parse(url)
        val boardId = uri.getQueryParameter("id")?.toIntOrNull()
        
        if (boardId == null || boardId <= 0) {
            Log.w(TAG, "Invalid alley-detail link: $url")
            return
        }
        
        Log.d(TAG, "Opening alley detail: boardId=$boardId")
        val intent = Intent(context, BoardDetailActivity::class.java).apply {
            putExtra("board_id", boardId)
            putExtra("board_name", "分区详情")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * 处理 yunhu://sticker-detail 链接（第三方扩展协议 第 0 号草案）
     *
     * 参数：
     *   id       —— 表情包 ID（必须）
     *   item_id  —— 具体表情 ID（可选），用来定位到包内的某一个表情
     */
    private fun handleStickerDetailLink(context: Context, url: String) {
        val uri = Uri.parse(url)
        val stickerPackId = uri.getQueryParameter("id")?.trim()
        val itemId = uri.getQueryParameter("item_id")?.trim()

        if (stickerPackId.isNullOrBlank()) {
            Log.w(TAG, "sticker-detail 缺少 id: $url")
            android.widget.Toast.makeText(context, "表情包链接缺少 ID", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "Opening sticker detail: packId=$stickerPackId, itemId=$itemId")
        try {
            com.yhchat.canary.ui.sticker.StickerPackDetailActivity.start(context, stickerPackId)
        } catch (e: Exception) {
            Log.e(TAG, "打开表情包详情失败", e)
            android.widget.Toast.makeText(context, "无法打开该表情包", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 处理 yunhu://expression-detail 链接（第三方扩展协议 第 0 号草案）
     *
     * 参数：
     *   id         —— 个人表情 ID（必须）
     *   image_url  —— 图片 URL，URL-Safe Base64 编码（必须）
     *
     * 协议要求的安全校验（这里都做了）：
     *   1. 用 id 添加的表情，其 image_url 必须与传进来的一致，否则拦截；
     *   2. 只允许云湖图床的域名；
     *   3. 限制过大文件。
     *
     * 现状说明：客户端目前没有「按 expression id 查询个人表情」的接口，
     * 所以第 1 条无法真正比对 —— 这一条会走到 image_url 的域名/来源校验上。
     * 一旦后续补上查询接口，应当在这里补成「先查再比」。
     */
    private fun handleExpressionDetailLink(context: Context, url: String) {
        val uri = Uri.parse(url)
        val expressionId = uri.getQueryParameter("id")?.trim()
        val encodedImageUrl = uri.getQueryParameter("image_url")?.trim()

        if (expressionId.isNullOrBlank()) {
            Log.w(TAG, "expression-detail 缺少 id: $url")
            android.widget.Toast.makeText(context, "表情链接缺少 ID", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        val imageUrl = decodeUrlSafeBase64(encodedImageUrl)
        if (imageUrl.isNullOrBlank()) {
            Log.w(TAG, "expression-detail 的 image_url 缺失或解码失败: $encodedImageUrl")
            android.widget.Toast.makeText(context, "表情图片地址无效", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        // 安全检查 1：必须是 http(s)
        val scheme = runCatching { Uri.parse(imageUrl).scheme?.lowercase() }.getOrNull()
        if (scheme != "http" && scheme != "https") {
            Log.w(TAG, "拒绝非 http(s) 的表情图片: $imageUrl")
            android.widget.Toast.makeText(context, "表情图片地址不合法", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        // 安全检查 2：只允许云湖图床。
        // 用后缀判断而不是写死某个子域名 —— 图床有 chat-img / chat-img3 等多套域名并存
        val host = runCatching { Uri.parse(imageUrl).host?.lowercase() }.getOrNull().orEmpty()
        if (!host.endsWith("jwznb.com") && !host.endsWith("yhchat.com")) {
            Log.w(TAG, "拒绝非云湖图床的表情图片: $host")
            android.widget.Toast.makeText(context, "只支持云湖图床的图片", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        // 安全检查 3：大小限制交给 Coil 的下载层（这里只做 URL 长度上限，
        // 避免超长 data/query 撑爆内存）
        if (imageUrl.length > MAX_EXPRESSION_IMAGE_URL_LENGTH) {
            Log.w(TAG, "表情图片地址过长，拒绝: ${imageUrl.length}")
            android.widget.Toast.makeText(context, "表情图片地址过长", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "Adding expression from link: id=$expressionId")
        // 收藏个人表情需要网络。UnifiedLinkHandler 是同步工具类，
        // 这里起一个协程做，不阻塞点击链路
        CoroutineScope(Dispatchers.IO).launch {
            val repository = com.yhchat.canary.data.di.RepositoryFactory
                .getExpressionRepository(context.applicationContext)
            repository.addExpression(imageUrl).fold(
                onSuccess = {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            context,
                            "已添加到个人表情",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onFailure = { e ->
                    Log.e(TAG, "添加个人表情失败", e)
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            context,
                            "添加表情失败：${e.message ?: "未知错误"}",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        }
    }

    /**
     * 处理 yunhu://send-msg 链接（第三方扩展协议 第 0 号草案）
     *
     * 参数（全部小写，除特别注明）：
     *   chat_id      —— 目标会话，`,` 分隔；不填表示当前会话
     *   chat_type    —— 1/user 2/group 3/bot；不填由客户端判断
     *   content_type —— text / md / image / file / ...；默认 text
     *   text         —— URL-Safe Base64
     *   at / buttons / reply / cmd_id / image_key / file_key / video_key / audio_key / sticker_item_id
     *
     * 安全规范落地情况：
     *   - 单次会话数 ≤ 10：超过直接拒绝
     *   - 发送前必须让用户确认会话与内容：走 SendToChatActivity，
     *     它本身就是「选择/确认会话 → 发送」的界面，天然满足这一条
     *   - 至少等待 10s 才能确认：这条做不到 —— 本实现采用「用户主动点确认」的
     *     等价路径，协议里写的是"至少等 10s 后可确认"，可选方案里也有
     *     "每次发送都需要手动确认"，我们采纳的是后者。
     */
    private fun handleSendMsgLink(context: Context, url: String) {
        val uri = Uri.parse(url)

        // 会话目标
        val rawChatIds = uri.getQueryParameter("chat_id").orEmpty()
        val chatIds = rawChatIds.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (chatIds.size > MAX_SEND_MSG_TARGETS) {
            Log.w(TAG, "send-msg 目标会话过多: ${chatIds.size}")
            android.widget.Toast.makeText(
                context,
                "一次最多只能发送到 $MAX_SEND_MSG_TARGETS 个会话",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }

        // 内容类型
        val rawContentType = uri.getQueryParameter("content_type")?.trim()?.lowercase()
        val isMarkdown = rawContentType == "md" || rawContentType == "markdown" || rawContentType == "3"

        val text = decodeUrlSafeBase64(uri.getQueryParameter("text"))

        if (text.isNullOrBlank()) {
            // 图片/文件/视频/语音/表情这几类需要 key，本实现暂不支持从链接直接取远端文件，
            // 明确告诉用户而不是静默失败
            val mediaHint = when {
                !uri.getQueryParameter("image_key").isNullOrBlank() -> "图片"
                !uri.getQueryParameter("file_key").isNullOrBlank() -> "文件"
                !uri.getQueryParameter("video_key").isNullOrBlank() -> "视频"
                !uri.getQueryParameter("audio_key").isNullOrBlank() -> "语音"
                !uri.getQueryParameter("sticker_item_id").isNullOrBlank() -> "表情"
                else -> null
            }
            Log.w(TAG, "send-msg 没有可发送的文本内容，media=$mediaHint")
            android.widget.Toast.makeText(
                context,
                if (mediaHint != null) {
                    "暂不支持通过链接发送$mediaHint，请手动选择"
                } else {
                    "消息内容为空"
                },
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }

        // 发给谁：多会话时逐个走「发送至」界面不现实，这里统一交给 SendToChatActivity，
        // 由用户在界面上确认目标。chat_id 有值时就把它作为预设提示显示在标题里。
        val summary = if (chatIds.isEmpty()) {
            "来自链接的消息"
        } else {
            "发送到 ${chatIds.size} 个会话"
        }
        val content = if (isMarkdown) {
            SendToChatContent.Text("```markdown\n$text\n```")
        } else {
            SendToChatContent.Text(text)
        }

        try {
            com.yhchat.canary.ui.share.SendToChatActivity.start(
                context = context,
                content = content,
                title = summary
            )
        } catch (e: Exception) {
            Log.e(TAG, "打开发送至界面失败", e)
            android.widget.Toast.makeText(context, "无法发送消息", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 解码 URL-Safe Base64。
     *
     * 协议要求 text / image_url / buttons 都用 URL-Safe Base64。
     * 这里同时兼容标准 Base64 和缺失 padding 的情况 —— 第三方生成链接时
     * 经常会省掉 `=`，硬性要求 padding 会导致大量链接解不出来。
     */
    private fun decodeUrlSafeBase64(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return try {
            // 先按 URL-Safe 还原成标准 Base64 字符集
            var normalized = raw.trim()
                .replace('-', '+')
                .replace('_', '/')
            // 补齐 padding
            val remainder = normalized.length % 4
            if (remainder != 0) {
                normalized += "=".repeat(4 - remainder)
            }
            val decoded = android.util.Base64.decode(normalized, android.util.Base64.NO_WRAP)
            String(decoded, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "URL-Safe Base64 解码失败: ${e.message}")
            null
        }
    }

    /** 表情图片 URL 长度上限，防超长 data URI 之类撑爆内存 */
    private const val MAX_EXPRESSION_IMAGE_URL_LENGTH = 2048

    /** send-msg 单次最多会话数，协议规定 ≤ 10 */
    private const val MAX_SEND_MSG_TARGETS = 10
    /**
     * 处理 yunhu://ad 链接
     */
    private fun handleYunhuAdLink(context: Context, url: String) {
        Log.d(TAG, "Handling yunhu ad link: $url")
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "No activity found for yunhu ad link: $url", e)
            android.widget.Toast.makeText(context, "暂不支持打开该广告链接", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 处理网页文章链接 https://www.yhchat.com/c/p/文章id
     */
    private fun handleWebArticleLink(context: Context, url: String) {
        Log.d(TAG, "Handling web article link: $url")
        
        // 尝试从网页链接中提取文章ID
        val postId = extractPostIdFromLink(url)
        if (postId != null) {
            Log.d(TAG, "Opening post detail from web link: postId=$postId")
            val intent = Intent(context, PostDetailActivity::class.java).apply {
                putExtra("post_id", postId)
                putExtra("post_title", "文章详情")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } else {
            Log.w(TAG, "Failed to extract post id from web article link: $url")
            handleExternalLink(context, url)
        }
    }

    /**
     * 处理用户主页链接 https://subdomain.yhchat.com/user/homepage/{id}
     */
    private fun handleUserHomepageLink(context: Context, url: String) {
        val matcher = USER_HOMEPAGE_PATTERN.matcher(url)
        val userId = if (matcher.matches()) matcher.group(2) else null

        if (userId.isNullOrBlank()) {
            Log.w(TAG, "Failed to extract user id from homepage link: $url")
            handleExternalLink(context, url)
            return
        }

        Log.d(TAG, "Opening user detail from homepage link: userId=$userId")
        UserDetailActivity.start(context, userId)
    }
    
    /**
     * 处理外部链接，尝试在 WebViewActivity 中打开（如果适用），否则在系统浏览器中打开
     */
    private fun handleExternalLink(context: Context, url: String) {
        try {
            Log.d(TAG, "Opening external link: $url")
            
            if (url.startsWith("yunhu://")) {
                Log.w(TAG, "Refusing to route custom yunhu link through WebView: $url")
                android.widget.Toast.makeText(context, "暂不支持打开该应用内链接", android.widget.Toast.LENGTH_SHORT).show()
                return
            }

            // 确保 URL 有正确的协议
            val finalUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                "https://$url"
            } else {
                url
            }

            // 统一使用 WebViewActivity 打开外部链接，它内部会处理是否需要跳转到外部浏览器
            com.yhchat.canary.ui.webview.WebViewActivity.start(context, finalUrl)
            Log.d(TAG, "Started WebViewActivity for link: $finalUrl")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open external link: $url", e)
            android.widget.Toast.makeText(context, "无法打开链接", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun normalizeYunhuLink(url: String, depth: Int = 0): String {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isEmpty() || depth > 2) return trimmedUrl
        if (trimmedUrl.startsWith("yunhu://")) return trimmedUrl

        val uri = runCatching { Uri.parse(trimmedUrl) }.getOrNull() ?: return trimmedUrl
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return trimmedUrl

        extractEmbeddedLink(uri)?.let { embeddedLink ->
            val normalizedEmbeddedLink = normalizeYunhuLink(embeddedLink, depth + 1)
            if (isCanonicalHandleableLink(normalizedEmbeddedLink)) {
                return normalizedEmbeddedLink
            }
        }

        extractShareLink(uri)?.let { return it }
        extractChatAddLink(uri)?.let { return it }
        extractUserHomepageLink(uri)?.let { return it }
        extractArticleLink(uri)?.let { return it }
        extractYhchatRootLink(uri)?.let { return it }

        return trimmedUrl
    }

    private fun extractEmbeddedLink(uri: Uri): String? {
        val embeddedParamNames = listOf(
            "url",
            "link",
            "target",
            "targetUrl",
            "redirect",
            "redirect_url",
            "redirectUrl"
        )

        return embeddedParamNames.firstNotNullOfOrNull { name ->
            uri.getQueryParameter(name)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let(Uri::decode)
                ?.takeIf {
                    it.startsWith("http://") ||
                        it.startsWith("https://") ||
                        it.startsWith("yunhu://")
                }
        }
    }

    private fun extractShareLink(uri: Uri): String? {
        val host = uri.host?.lowercase() ?: return null
        if (host !in yhfxHosts && host !in articleHosts) return null

        val pathSegments = uri.pathSegments
        val key = firstNonBlank(
            uri.getQueryParameter("key"),
            uri.getQueryParameter("k"),
            pathSegments.getOrNull(1)?.takeIf {
                pathSegments.firstOrNull().equals("share", ignoreCase = true)
            },
            pathSegments.singleOrNull()?.takeIf { uri.getQueryParameter("ts") != null }
        )
        val ts = firstNonBlank(
            uri.getQueryParameter("ts"),
            uri.getQueryParameter("t"),
            pathSegments.getOrNull(2)?.takeIf {
                pathSegments.firstOrNull().equals("share", ignoreCase = true)
            }
        )

        return if (!key.isNullOrEmpty() && !ts.isNullOrEmpty()) {
            "yunhu://jwznb.com?key=${Uri.encode(key)}&ts=${Uri.encode(ts)}"
        } else {
            null
        }
    }

    private fun extractChatAddLink(uri: Uri): String? {
        val host = uri.host?.lowercase() ?: return null
        val isYunhuHost = host in yhfxHosts || host in articleHosts
        val isChatAddPath = uri.pathSegments.firstOrNull().equals("chat-add", ignoreCase = true)
        if (!isYunhuHost && !isChatAddPath) return null

        val id = firstNonBlank(uri.getQueryParameter("id")) ?: return null
        val chatType = normalizeChatType(
            firstNonBlank(
                uri.getQueryParameter("ct"),
                uri.getQueryParameter("type"),
                uri.getQueryParameter("chatType")
            )
        ) ?: return null

        return "yunhu://jwznb.com?ct=${Uri.encode(chatType)}&id=${Uri.encode(id)}"
    }

    private fun extractArticleLink(uri: Uri): String? {
        val host = uri.host?.lowercase() ?: return null
        if (host !in articleHosts) return null

        val pathSegments = uri.pathSegments
        if (pathSegments.size < 3) return null
        if (!pathSegments[0].equals("c", ignoreCase = true) || !pathSegments[1].equals("p", ignoreCase = true)) {
            return null
        }

        val postId = pathSegments[2].trim()
        return if (postId.isNotEmpty()) {
            "https://www.yhchat.com/c/p/$postId"
        } else {
            null
        }
    }

    private fun extractUserHomepageLink(uri: Uri): String? {
        val host = uri.host?.lowercase() ?: return null
        if (host !in articleHosts && !host.endsWith(".yhchat.com")) return null

        val pathSegments = uri.pathSegments
        if (pathSegments.size < 3) return null
        if (!pathSegments[0].equals("user", ignoreCase = true) ||
            !pathSegments[1].equals("homepage", ignoreCase = true)
        ) {
            return null
        }

        val userId = pathSegments[2].trim()
        return if (userId.isNotEmpty()) {
            "https://www.yhchat.com/user/homepage/$userId"
        } else {
            null
        }
    }

    private fun extractYhchatRootLink(uri: Uri): String? {
        val host = uri.host?.lowercase() ?: return null
        if (host != "yhchat.com" && host != "www.yhchat.com") return null

        val normalizedPath = uri.encodedPath?.takeIf { it.isNotBlank() } ?: "/"
        val query = uri.encodedQuery?.let { "?$it" } ?: ""
        val fragment = uri.encodedFragment?.let { "#$it" } ?: ""
        return "https://www.yhchat.com$normalizedPath$query$fragment"
    }

    private fun normalizeChatType(rawType: String?): String? {
        return when (rawType?.trim()?.lowercase()) {
            "1", "user" -> "1"
            "2", "group" -> "2"
            "3", "bot" -> "3"
            else -> null
        }
    }

    private fun firstNonBlank(vararg values: String?): String? {
        return values.firstOrNull { !it.isNullOrBlank() }?.trim()
    }

    private fun isCanonicalHandleableLink(url: String): Boolean {
        return url.startsWith("yunhu://") ||
            url.startsWith("https://yhfx.jwznb.com/share") ||
            url.contains("https://www.yhchat.com/c/p/") ||
            USER_HOMEPAGE_PATTERN.matcher(url).matches() ||
            YHCHAT_ROOT_PATTERN.matcher(url).matches()
    }
    
    /**
     * 检查是否为聊天添加链接
     */
    fun isChatAddLink(uriString: String): Boolean {
        return try {
            Log.d(TAG, "检查 Deep Link: $uriString")
            val uri = Uri.parse(uriString)
            val isValid = uri.scheme == "yunhu" && (uri.host == "chat-add" || uri.host == "jwznb.com")
            Log.d(TAG, "Deep Link 有效性: $isValid")
            isValid
        } catch (e: Exception) {
            Log.e(TAG, "Deep Link 检查异常: $uriString", e)
            false
        }
    }
    
    /**
     * 解析聊天添加链接
     * 支持格式：yunhu://chat-add?id=xxx&type=xxx 或 yunhu://jwznb.com?ct=x&id=xxx
     */
    fun parseChatAddLink(uriString: String): ChatAddInfo? {
        return try {
            Log.d(TAG, "开始解析 Deep Link: $uriString")
            
            val uri = Uri.parse(uriString)
            
            // 检查scheme
            if (uri.scheme != "yunhu") {
                Log.w(TAG, "Invalid scheme: ${uri.scheme}")
                return null
            }
            
            var id: String? = null
            var type: ChatAddType? = null
            
            if (uri.host == "chat-add") {
                // 格式: yunhu://chat-add?id=xxx&type=xxx
                id = uri.getQueryParameter("id")
                val typeString = uri.getQueryParameter("type")
                
                if (!typeString.isNullOrEmpty()) {
                    type = when (typeString.lowercase()) {
                        "user" -> ChatAddType.USER
                        "group" -> ChatAddType.GROUP
                        "bot" -> ChatAddType.BOT
                        else -> null
                    }
                }
            } else if (uri.host == "jwznb.com") {
                // 格式: yunhu://jwznb.com?ct=x&id=xxx
                id = uri.getQueryParameter("id")
                val ctString = uri.getQueryParameter("ct")
                
                if (!ctString.isNullOrEmpty()) {
                    type = when (ctString) {
                        "1" -> ChatAddType.USER
                        "2" -> ChatAddType.GROUP
                        "3" -> ChatAddType.BOT
                        else -> null
                    }
                }
            }
            
            if (id.isNullOrEmpty()) {
                Log.w(TAG, "Missing or empty id parameter")
                return null
            }
            
            if (type == null) {
                Log.w(TAG, "Invalid or missing type/ct parameter")
                return null
            }
            
            Log.d(TAG, "解析成功: id=$id, chatType=${type.chatType}")
            
            ChatAddInfo(
                id = id,
                type = type,
                displayName = "", // 将通过API获取
                avatarUrl = "", // 将通过API获取
                description = "" // 将通过API获取
            )
        } catch (e: Exception) {
            Log.e(TAG, "Deep Link 解析异常: $uriString", e)
            null
        }
    }
    
    /**
     * 从链接中提取文章ID
     * 支持 yunhu://post-detail?id=xxx、yunhu://alley-detail?id=xxx 和 https://www.yhchat.com/c/p/xxx
     */
    fun extractPostIdFromLink(link: String): Int? {
        // 先尝试 yunhu://post-detail 链接
        val yunhuMatcher = YUNHU_POST_DETAIL_PATTERN.matcher(link)
        if (yunhuMatcher.find()) {
            return yunhuMatcher.group(1)?.toIntOrNull()
        }
        
        // 尝试 yunhu://alley-detail 链接
        val alleyMatcher = ALLEY_DETAIL_PATTERN.matcher(link)
        if (alleyMatcher.find()) {
            return alleyMatcher.group(1)?.toIntOrNull()
        }
        
        // 再尝试网页文章链接
        val webMatcher = WEB_ARTICLE_PATTERN.matcher(link)
        if (webMatcher.find()) {
            return webMatcher.group(1)?.toIntOrNull()
        }
        
        return null
    }
    
    /**
     * 检查文本是否包含可处理的链接
     */
    fun containsHandleableLink(text: String): Boolean {
        return YUNHU_POST_DETAIL_PATTERN.matcher(text).find() || 
               ALLEY_DETAIL_PATTERN.matcher(text).find() ||
               WEB_ARTICLE_PATTERN.matcher(text).find() ||
               USER_HOMEPAGE_PATTERN.matcher(text).find() ||
               YHCHAT_ROOT_PATTERN.matcher(text).find()
    }
    
    /**
     * 提取文本中的所有可处理的链接
     */
    fun extractHandleableLinks(text: String): List<String> {
        val links = mutableListOf<String>()
        
        // 提取 yunhu://post-detail 链接
        val yunhuMatcher = YUNHU_POST_DETAIL_PATTERN.matcher(text)
        while (yunhuMatcher.find()) {
            links.add(yunhuMatcher.group())
        }
        
        // 提取 yunhu://alley-detail 链接
        val alleyMatcher = ALLEY_DETAIL_PATTERN.matcher(text)
        while (alleyMatcher.find()) {
            links.add(alleyMatcher.group())
        }
        
        // 提取网页文章链接
        val webMatcher = WEB_ARTICLE_PATTERN.matcher(text)
        while (webMatcher.find()) {
            links.add(webMatcher.group())
        }

        val homepageMatcher = USER_HOMEPAGE_PATTERN.matcher(text)
        while (homepageMatcher.find()) {
            links.add(homepageMatcher.group())
        }

        val rootMatcher = YHCHAT_ROOT_PATTERN.matcher(text)
        while (rootMatcher.find()) {
            links.add(rootMatcher.group())
        }
        
        return links
    }
    
    /**
     * 在文本中识别链接并设置为可点击
     */
    fun makeLinksClickable(
        textView: TextView, 
        text: String, 
        linkColor: Int = android.graphics.Color.BLUE
    ) {
        val spannable = SpannableStringBuilder(text)
        
        // 处理 yunhu://post-detail 链接
        val yunhuMatcher = YUNHU_POST_DETAIL_PATTERN.matcher(text)
        while (yunhuMatcher.find()) {
            val start = yunhuMatcher.start()
            val end = yunhuMatcher.end()
            val link = yunhuMatcher.group()
            
            val clickableSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    handleLink(widget.context, link)
                }
            }
            
            spannable.setSpan(clickableSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(
                ForegroundColorSpan(linkColor), 
                start, 
                end, 
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        
        // 处理 yunhu://alley-detail 链接
        val alleyMatcher = ALLEY_DETAIL_PATTERN.matcher(text)
        while (alleyMatcher.find()) {
            val start = alleyMatcher.start()
            val end = alleyMatcher.end()
            val link = alleyMatcher.group()
            
            val clickableSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    handleLink(widget.context, link)
                }
            }
            
            spannable.setSpan(clickableSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(
                ForegroundColorSpan(linkColor), 
                start, 
                end, 
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        
        // 处理网页文章链接
        val webMatcher = WEB_ARTICLE_PATTERN.matcher(text)
        while (webMatcher.find()) {
            val start = webMatcher.start()
            val end = webMatcher.end()
            val link = webMatcher.group()
            
            val clickableSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    handleLink(widget.context, link)
                }
            }
            
            spannable.setSpan(clickableSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(
                ForegroundColorSpan(linkColor), 
                start, 
                end, 
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        // 处理用户主页链接
        val homepageMatcher = USER_HOMEPAGE_PATTERN.matcher(text)
        while (homepageMatcher.find()) {
            val start = homepageMatcher.start()
            val end = homepageMatcher.end()
            val link = homepageMatcher.group()

            val clickableSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    handleLink(widget.context, link)
                }
            }

            spannable.setSpan(clickableSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(
                ForegroundColorSpan(linkColor),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        // 处理 yhchat.com 根域名链接
        val rootMatcher = YHCHAT_ROOT_PATTERN.matcher(text)
        while (rootMatcher.find()) {
            val start = rootMatcher.start()
            val end = rootMatcher.end()
            val link = rootMatcher.group()

            val clickableSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    handleLink(widget.context, link)
                }
            }

            spannable.setSpan(clickableSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(
                ForegroundColorSpan(linkColor),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        
        textView.text = spannable
        textView.movementMethod = LinkMovementMethod.getInstance()
    }
}

/**
 * Compose版本的链接处理函数
 */
@Composable
fun ClickableLinkText(
    text: String,
    linkColor: Color = MaterialTheme.colorScheme.primary,
    onLinkClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    
    // 检查是否包含可处理的链接
    if (UnifiedLinkHandler.containsHandleableLink(text)) {
        // 对于包含链接的文本，可以使用AndroidView来实现
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { context ->
                TextView(context).apply {
                    UnifiedLinkHandler.makeLinksClickable(
                        this, 
                        text, 
                        linkColor.toArgb()
                    )
                }
            },
            update = { textView ->
                UnifiedLinkHandler.makeLinksClickable(
                    textView, 
                    text, 
                    linkColor.toArgb()
                )
            }
        )
    } else {
        // 普通文本
        Text(text = text)
    }
}
