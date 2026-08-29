package com.yhchat.canary.ui.webview

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.ViewGroup
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import com.yhchat.canary.crash.CrashHandler
import com.yhchat.canary.ui.adaptive.YhAlertDialog
import com.yhchat.canary.ui.adaptive.YhBottomAppBar
import com.yhchat.canary.ui.adaptive.YhButton
import com.yhchat.canary.ui.adaptive.YhDropdownMenu
import com.yhchat.canary.ui.adaptive.YhDropdownMenuItem
import com.yhchat.canary.ui.adaptive.YhHorizontalDivider
import com.yhchat.canary.ui.adaptive.YhIcon as Icon
import com.yhchat.canary.ui.adaptive.YhIconButton
import com.yhchat.canary.ui.adaptive.YhLinearProgressIndicator
import com.yhchat.canary.ui.adaptive.YhScaffold
import com.yhchat.canary.ui.adaptive.YhText as Text
import com.yhchat.canary.ui.adaptive.YhTextButton
import com.yhchat.canary.ui.adaptive.YhTopBar
import com.yhchat.canary.ui.base.BaseActivity
import com.yhchat.canary.ui.theme.YhchatCanaryTheme
import com.yhchat.canary.utils.UnifiedLinkHandler

class WebViewActivity : BaseActivity() {
    private val viewModel: WebViewViewModel by viewModels()

    companion object {
        private const val EXTRA_URL = "extra_url"
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_TOKEN = "extra_token"
        private const val EXTRA_HTML = "extra_html"
        private const val EXTRA_BASE_URL = "extra_base_url"
        const val EXTRA_IS_NCM_LOGIN = "extra_is_ncm_login"
        const val PC_DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        fun startNcmLogin(context: Context) {
            val intent = Intent(context, WebViewActivity::class.java).apply {
                putExtra(EXTRA_URL, "https://music.163.com/")
                putExtra(EXTRA_TITLE, "网易云音乐登录")
                putExtra(EXTRA_IS_NCM_LOGIN, true)
                if (context !is android.app.Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        }

        fun start(context: Context, url: String, title: String? = null, token: String? = null) {
            val intent = Intent(context, WebViewActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_TOKEN, token)
                if (context !is android.app.Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        }

        fun startHtml(
            context: Context,
            html: String,
            title: String? = null,
            baseUrl: String? = null,
            token: String? = null
        ) {
            val intent = Intent(context, WebViewActivity::class.java).apply {
                putExtra(EXTRA_HTML, html)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_BASE_URL, baseUrl)
                putExtra(EXTRA_TOKEN, token)
                if (context !is android.app.Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        }

        fun openInExternalBrowser(context: Context, url: String) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addCategory(Intent.CATEGORY_BROWSABLE)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                CrashHandler.showCaughtException(context, e)
                Toast.makeText(context, "无法打开浏览器", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val url = intent.getStringExtra(EXTRA_URL) ?: ""
        val html = intent.getStringExtra(EXTRA_HTML)
        val initialTitle = intent.getStringExtra(EXTRA_TITLE) ?: "加载中..."
        val token = intent.getStringExtra(EXTRA_TOKEN)
        val baseUrl = intent.getStringExtra(EXTRA_BASE_URL)

        val isNcmLogin = intent.getBooleanExtra(EXTRA_IS_NCM_LOGIN, false)

        if (url.isEmpty() && html.isNullOrEmpty()) {
            finish()
            return
        }

        setContent {
            YhchatCanaryTheme {
                WebViewScreen(
                    initialUrl = url,
                    initialHtml = html,
                    initialBaseUrl = baseUrl,
                    initialTitle = initialTitle,
                    token = token,
                    isNcmLogin = isNcmLogin,
                    viewModel = viewModel,
                    onBack = { finish() }
                )
            }
        }
    }
}

@Composable
fun WebViewScreen(
    initialUrl: String,
    initialHtml: String?,
    initialBaseUrl: String?,
    initialTitle: String,
    token: String?,
    isNcmLogin: Boolean = false,
    viewModel: WebViewViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var webView by remember { mutableStateOf<WebView?>(null) }

    val displayUrl = remember(initialUrl, initialHtml, initialBaseUrl) {
        when {
            initialUrl.isNotBlank() -> initialUrl
            !initialBaseUrl.isNullOrBlank() -> initialBaseUrl
            !initialHtml.isNullOrBlank() -> "本地HTML"
            else -> ""
        }
    }

    LaunchedEffect(displayUrl, initialTitle) {
        viewModel.initialize(displayUrl, initialTitle)
    }

    // 处理物理返回键
    BackHandler(enabled = uiState.canGoBack) {
        webView?.goBack()
    }

    // 资源释放
    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.loadUrl("about:blank")
            webView?.destroy()
            webView = null
        }
    }

    YhScaffold(
        topBar = {
            Column {
                YhTopBar(
                    title = uiState.title,
                    large = false,
                    navigationIcon = {
                        YhIconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "关闭"
                            )
                        }
                    },
                    actions = {
                        YhIconButton(onClick = { viewModel.setMenuVisible(true) }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                        YhDropdownMenu(
                            expanded = uiState.showMenu,
                            onDismissRequest = { viewModel.setMenuVisible(false) }
                        ) {
                            YhDropdownMenuItem(
                                text = { Text("刷新") },
                                onClick = {
                                    viewModel.setMenuVisible(false)
                                    webView?.reload()
                                },
                                leadingIcon = { Icon(Icons.Default.Refresh, null) }
                            )
                            YhDropdownMenuItem(
                                text = { Text("在浏览器中打开") },
                                onClick = {
                                    viewModel.setMenuVisible(false)
                                    if (uiState.url.startsWith("http://") || uiState.url.startsWith("https://")) {
                                        WebViewActivity.openInExternalBrowser(context, uiState.url)
                                    } else {
                                        Toast.makeText(context, "当前内容没有可打开的链接", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                leadingIcon = { Icon(Icons.Default.OpenInBrowser, null) }
                            )
                            YhDropdownMenuItem(
                                text = { Text("复制链接") },
                                onClick = {
                                    viewModel.setMenuVisible(false)
                                    if (uiState.url.isNotBlank() && uiState.url != "本地HTML") {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("url", uiState.url)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "当前内容没有可复制的链接", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, null) }
                            )
                            YhHorizontalDivider()
                            YhDropdownMenuItem(
                                text = { Text("关闭") },
                                onClick = {
                                    viewModel.setMenuVisible(false)
                                    onBack()
                                },
                                leadingIcon = { Icon(Icons.Default.Close, null) }
                            )
                        }
                    }
                )
                // 进度条
                if (uiState.progress < 1.0f) {
                    YhLinearProgressIndicator(
                        progress = uiState.progress,
                        modifier = Modifier.fillMaxWidth().height(2.dp)
                    )
                }
            }
        },
        bottomBar = {
            YhBottomAppBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
            ) {
                    YhIconButton(
                        onClick = { webView?.goBack() },
                        enabled = uiState.canGoBack,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "后退")
                    }
                    YhIconButton(
                        onClick = { webView?.goForward() },
                        enabled = uiState.canGoForward,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "前进")
                    }
                    YhIconButton(
                        onClick = {
                            if (uiState.progress < 1.0f) {
                                webView?.stopLoading()
                            } else {
                                webView?.reload()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (uiState.progress < 1.0f) Icons.Default.Close else Icons.Default.Refresh,
                            contentDescription = if (uiState.progress < 1.0f) "停止" else "刷新"
                        )
                    }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setupWebViewSettings(isNcmLogin)
                        
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                // 为所有请求添加 token 请求头
                                if (token != null && request != null) {
                                    val url = request.url.toString()
                                    try {
                                        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                                        connection.requestMethod = request.method
                                        connection.setRequestProperty("token", token)
                                        
                                        // 复制原始请求头
                                        request.requestHeaders.forEach { (key, value) ->
                                            connection.setRequestProperty(key, value)
                                        }
                                        
                                        connection.connect()
                                        
                                        val contentType = connection.contentType
                                        val encoding = connection.contentEncoding
                                        val inputStream = connection.inputStream
                                        
                                        return WebResourceResponse(
                                            contentType?.split(";")?.firstOrNull() ?: "text/html",
                                            encoding ?: "UTF-8",
                                            inputStream
                                        )
                                    } catch (e: Exception) {
                                        Log.w("WebViewActivity", "Failed to intercept WebView request: $url", e)
                                        // 如果拦截失败，返回 null 让 WebView 自己处理
                                        return null
                                    }
                                }
                                return super.shouldInterceptRequest(view, request)
                            }
                            
                            override fun onPageStarted(view: WebView?, urlStr: String?, favicon: Bitmap?) {
                                viewModel.onPageStarted(
                                    url = urlStr,
                                    canGoBack = view?.canGoBack() ?: false,
                                    canGoForward = view?.canGoForward() ?: false
                                )
                                if (isNcmLogin) {
                                    checkAndSaveNcmCookie(context)
                                }
                            }

                            override fun onPageFinished(view: WebView?, urlStr: String?) {
                                viewModel.onPageFinished(
                                    url = urlStr,
                                    title = view?.title,
                                    canGoBack = view?.canGoBack() ?: false,
                                    canGoForward = view?.canGoForward() ?: false
                                )
                                if (isNcmLogin) {
                                    checkAndSaveNcmCookie(context)
                                }
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val url = request?.url?.toString() ?: return false

                                if (UnifiedLinkHandler.isHandleableLink(url) ||
                                    (!url.startsWith("http://") && !url.startsWith("https://"))
                                ) {
                                    viewModel.requestExternalJump(url)
                                    return true
                                }
                                return false
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                viewModel.onProgressChanged(newProgress)
                            }

                            override fun onReceivedTitle(view: WebView?, webTitle: String?) {
                                viewModel.onReceivedTitle(webTitle)
                            }
                        }

                        setDownloadListener { downloadUrl, _, contentDisposition, mimetype, _ ->
                            viewModel.requestDownload(
                                url = downloadUrl,
                                contentDisposition = contentDisposition,
                                mimeType = mimetype
                            )
                        }

                        if (!initialHtml.isNullOrBlank()) {
                            loadDataWithBaseURL(
                                initialBaseUrl,
                                buildHtmlDocument(initialHtml),
                                "text/html",
                                "UTF-8",
                                null
                            )
                            viewModel.onPageFinished(
                                url = displayUrl,
                                title = initialTitle,
                                canGoBack = canGoBack(),
                                canGoForward = canGoForward()
                            )
                        } else if (token != null) {
                            val headers = mapOf("token" to token)
                            loadUrl(initialUrl, headers)
                        } else {
                            loadUrl(initialUrl)
                        }
                        webView = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    webView = view
                }
            )
        }
    }

    // 外部跳转拦截对话框
    if (uiState.showJumpDialog) {
        YhAlertDialog(
            onDismissRequest = { viewModel.dismissJumpDialog() },
            title = { Text("外部应用跳转请求") },
            text = { 
                Column {
                    Text("该网页请求打开外部应用，是否允许？")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = uiState.pendingJumpUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            confirmButton = {
                YhButton(onClick = {
                    val jumpUrl = uiState.pendingJumpUrl
                    viewModel.dismissJumpDialog()
                    try {
                        if (UnifiedLinkHandler.isHandleableLink(jumpUrl)) {
                            UnifiedLinkHandler.handleLink(context, jumpUrl)
                        } else {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(jumpUrl)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                    } catch (e: Exception) {
                        CrashHandler.showCaughtException(context, e)
                        Toast.makeText(context, "未找到可处理该链接的应用", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                YhTextButton(onClick = { viewModel.dismissJumpDialog() }) {
                    Text("忽略")
                }
            }
        )
    }

    if (uiState.showDownloadDialog) {
        val fileName = URLUtil.guessFileName(
            uiState.pendingDownloadUrl,
            uiState.pendingContentDisposition,
            uiState.pendingMimeType
        )
        YhAlertDialog(
            onDismissRequest = { viewModel.dismissDownloadDialog() },
            title = { Text("下载文件") },
            text = {
                Column {
                    Text("该网站请求下载文件，是否继续？")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            confirmButton = {
                YhButton(onClick = {
                    val downloadUrl = uiState.pendingDownloadUrl
                    val mimeType = uiState.pendingMimeType
                    viewModel.dismissDownloadDialog()
                    if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED) {
                        Toast.makeText(context, "外部存储不可用", Toast.LENGTH_LONG).show()
                        return@YhButton
                    }
                    try {
                        val request = DownloadManager.Request(downloadUrl.toUri()).apply {
                            setMimeType(mimeType)
                            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                            setTitle(fileName)
                            setDescription("正在下载文件...")
                            setAllowedOverRoaming(true)
                            setAllowedOverMetered(true)
                        }
                        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                        dm.enqueue(request)
                        Toast.makeText(context, "开始下载: $fileName", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        CrashHandler.showCaughtException(context, e)
                        Toast.makeText(context, "下载失败: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }) {
                    Text("下载")
                }
            },
            dismissButton = {
                YhTextButton(onClick = { viewModel.dismissDownloadDialog() }) {
                    Text("取消")
                }
            }
        )
    }
}

private var hasNotifiedNcmLogin = false

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.setupWebViewSettings(isNcmLogin: Boolean = false) {
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true
        useWideViewPort = true
        loadWithOverviewMode = true
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        cacheMode = WebSettings.LOAD_DEFAULT
        userAgentString = if (isNcmLogin) {
            WebViewActivity.PC_DESKTOP_UA
        } else {
            "$userAgentString Yunhu/Canary"
        }
    }
    try {
        val cookieManager = android.webkit.CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(this, true)
    } catch (e: Exception) {
        Log.w("WebViewActivity", "设置CookieManager失败", e)
    }
}

private fun checkAndSaveNcmCookie(context: Context) {
    try {
        val cookieManager = android.webkit.CookieManager.getInstance()
        val c1 = cookieManager.getCookie("https://music.163.com") ?: ""
        val c2 = cookieManager.getCookie("https://163.com") ?: ""
        val full = if (c1.isNotBlank() && c2.isNotBlank()) "$c1; $c2" else "$c1$c2"
        if (full.contains("MUSIC_U") || full.contains("__csrf")) {
            com.yhchat.canary.ncm.NcmAccountManager.saveCookie(context, full)
            if (!hasNotifiedNcmLogin) {
                hasNotifiedNcmLogin = true
                Toast.makeText(context, "网易云账号已登录，已保存Cookie", Toast.LENGTH_SHORT).show()
            }
        }
    } catch (e: Exception) {
        Log.w("WebViewActivity", "检查网易云Cookie失败", e)
    }
}

private fun buildHtmlDocument(html: String): String {
    val trimmed = html.trim()
    val hasHtmlTag = Regex("<\\s*html[\\s>]", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)
    val hasHeadTag = Regex("<\\s*head[\\s>]", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)
    val hasBodyTag = Regex("<\\s*body[\\s>]", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)
    if (hasHtmlTag) {
        return trimmed
    }
    val head = if (hasHeadTag) "" else "<head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"></head>"
    val bodyContent = if (hasBodyTag) trimmed else "<body>$trimmed</body>"
    return "<!DOCTYPE html><html>$head$bodyContent</html>"
}
