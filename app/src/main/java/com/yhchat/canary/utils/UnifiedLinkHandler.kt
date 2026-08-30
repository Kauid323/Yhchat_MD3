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
import com.yhchat.canary.ui.adaptive.YhText
import com.yhchat.canary.ui.chat.ChatAddActivity
import com.yhchat.canary.ui.community.BoardDetailActivity
import com.yhchat.canary.ui.community.PostDetailActivity
import com.yhchat.canary.ui.user.UserDetailActivity
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
        YhText(text = text)
    }
}
