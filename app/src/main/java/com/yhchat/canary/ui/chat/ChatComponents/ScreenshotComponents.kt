package com.yhchat.canary.ui.chat.ChatComponents

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yhchat.canary.data.model.ChatMessage
import com.yhchat.canary.ui.components.ImageUtils
import com.yhchat.canary.ui.theme.YhchatCanaryTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** 按所选消息独立排版导出，不读取屏幕，也不受当前聊天滚动位置或键盘影响。 */
suspend fun generateMessagesImage(
    context: Context,
    messages: List<ChatMessage>,
    chatName: String
): Bitmap = withContext(Dispatchers.Main.immediate) {
    require(messages.isNotEmpty()) { "请先选择消息" }
    var hostContext = context
    while (hostContext is android.content.ContextWrapper && hostContext !is android.app.Activity) {
        val base = hostContext.baseContext
        if (base === hostContext) break
        hostContext = base
    }
    val activity = hostContext as? android.app.Activity
        ?: throw IllegalArgumentException("请从聊天页面生成图片")
    check(!activity.isFinishing && !activity.isDestroyed) { "聊天页面已关闭" }
    val rootView = activity.window.decorView as? ViewGroup
        ?: throw IllegalStateException("无法创建导出画布")
    val tracker = ExportImageTracker()
    val exportContext = object : android.content.ContextWrapper(activity), ImageUtils.ImageExportContext {
        override val imageExportListener: coil.request.ImageRequest.Listener = tracker
    }
    // 不使用 INVISIBLE：它可能暂停 Compose 的帧和异步图片更新。透明父容器仅提供生命周期。
    val container = FrameLayout(activity).apply {
        alpha = 0f
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        layoutParams = ViewGroup.LayoutParams(1, 1)
    }
    val composeView = androidx.compose.ui.platform.ComposeView(activity)
    container.addView(composeView)
    rootView.addView(container)
    var result: Bitmap? = null
    try {
        composeView.setContent {
            androidx.compose.runtime.CompositionLocalProvider(LocalContext provides exportContext) {
                ScreenshotContent(imageWidth = 400.dp, chatName = chatName, messages = messages)
            }
        }
        fun measureContent() {
            composeView.measure(
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            composeView.layout(0, 0, composeView.measuredWidth, composeView.measuredHeight)
        }
        measureContent()
        val startedAt = android.os.SystemClock.elapsedRealtime()
        var previousHeight = -1
        var stableFrames = 0
        // 等图片成功/失败回调和重新测量都完成，不能只等一次 post 或固定延迟。
        while (true) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            awaitExportFrame()
            measureContent()
            val height = composeView.measuredHeight
            stableFrames = if (height == previousHeight) stableFrames + 1 else 0
            previousHeight = height
            val now = android.os.SystemClock.elapsedRealtime()
            if (tracker.pendingCount == 0 && stableFrames >= 3 && now - tracker.lastChangeAt >= 250L && now - startedAt >= 500L) break
            check(now - startedAt < 30_000L) { "图片仍在加载，请检查网络后重试" }
        }
        check(tracker.failedCount == 0) { "部分图片未加载成功，请检查网络后重新生成" }
        val width = composeView.measuredWidth
        val height = composeView.measuredHeight
        check(width > 0 && height > 0) { "生成内容为空" }
        val maxPixels = minOf(16_000_000L, Runtime.getRuntime().maxMemory() / 32L).coerceAtLeast(1_000_000L)
        val scale = minOf(1f, kotlin.math.sqrt(maxPixels.toDouble() / (width.toLong() * height)).toFloat(), 30_000f / height)
        check(width * scale >= 320f) { "所选记录过长，请减少消息数量后分批生成" }
        val bitmap = Bitmap.createBitmap(
            (width * scale).toInt().coerceAtLeast(1),
            (height * scale).toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        result = bitmap
        val canvas = android.graphics.Canvas(bitmap)
        canvas.scale(scale, scale)
        composeView.draw(canvas)
        result = null
        bitmap
    } catch (e: OutOfMemoryError) {
        throw IllegalStateException("可用内存不足，请减少所选消息后重试", e)
    } finally {
        result?.recycle()
        composeView.disposeComposition()
        rootView.removeView(container)
    }
}

/** 共享给导出组件的所有 ImageUtils 请求，统计加载终态，关闭淡入后再绘制。 */
private class ExportImageTracker : coil.request.ImageRequest.Listener {
    private val pending = java.util.Collections.newSetFromMap(
        java.util.IdentityHashMap<coil.request.ImageRequest, Boolean>())
    @Volatile var lastChangeAt: Long = android.os.SystemClock.elapsedRealtime()
        private set
    @Volatile var failedCount: Int = 0
        private set
    val pendingCount: Int get() = synchronized(this) { pending.size }
    @Synchronized override fun onStart(request: coil.request.ImageRequest) {
        pending.add(request)
        lastChangeAt = android.os.SystemClock.elapsedRealtime()
    }
    @Synchronized override fun onCancel(request: coil.request.ImageRequest) { finish(request) }
    @Synchronized override fun onError(request: coil.request.ImageRequest, result: coil.request.ErrorResult) {
        failedCount++
        finish(request)
    }
    @Synchronized override fun onSuccess(request: coil.request.ImageRequest, result: coil.request.SuccessResult) { finish(request) }
    private fun finish(request: coil.request.ImageRequest) {
        pending.remove(request)
        lastChangeAt = android.os.SystemClock.elapsedRealtime()
    }
}

private suspend fun awaitExportFrame() = suspendCancellableCoroutine<Unit> { continuation ->
    val choreographer = android.view.Choreographer.getInstance()
    val callback = android.view.Choreographer.FrameCallback {
        if (continuation.isActive) continuation.resume(Unit)
    }
    choreographer.postFrameCallback(callback)
    continuation.invokeOnCancellation { choreographer.removeFrameCallback(callback) }
}

@Composable
private fun ScreenshotContent(
    imageWidth: Dp,
    chatName: String,
    messages: List<ChatMessage>
) {
    YhchatCanaryTheme {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(2f, 1f)
        ) {
        Surface(
            modifier = Modifier
                .width(imageWidth)
                .wrapContentHeight()
                .background(MaterialTheme.colorScheme.background),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = chatName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                messages.forEach { message ->
                    RealMessageItemForScreenshot(
                        message = message,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                Text(
                    text = "共 ${messages.size} 条消息 · 生成于 ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
        }
    }
}

/**
 * 真实消息项组件 - 用于截图，完全复制MessageItem的UI
 */
@Composable
private fun RealMessageItemForScreenshot(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    val isMyMessage = false // 截图中统一显示为对方消息，便于阅读
    
    // 检查是否为撤回消息
    if (message.msgDeleteTime != null) {
        RecallMessageItem(
            message = message,
            modifier = modifier
        )
        return
    }
    
    // 检查是否为tip消息（类型9）
    if (message.contentType == 9) {
        TipMessageItem(
            message = message,
            modifier = modifier
        )
        return
    }
    
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        // 发送者头像（左侧）
        if (message.sender.avatarUrl.isNotBlank()) {
            AsyncImage(
                model = ImageUtils.createAvatarImageRequest(LocalContext.current, message.sender.avatarUrl, false),
                contentDescription = message.sender.name,
                modifier = Modifier.size(40.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Surface(modifier = Modifier.size(40.dp), shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                    Text(message.sender.name.take(1).ifBlank { "?" })
                }
            }
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .widthIn(max = 280.dp),
            horizontalAlignment = Alignment.Start
        ) {
            // 发送者姓名和标签
            SenderNameAndTagsForScreenshot(message = message)
            
            // 指令消息标识
            if (message.cmd != null && message.cmd.name.isNotEmpty()) {
                Text(
                    text = "/${message.cmd.name}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            // 消息气泡
            Surface(
                modifier = Modifier
                    .wrapContentWidth()
                    .clip(
                        RoundedCornerShape(
                            topStart = 4.dp,
                            topEnd = 16.dp,
                            bottomStart = 16.dp,
                            bottomEnd = 16.dp
                        )
                    ),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                MessageContentForScreenshot(
                    message = message,
                    modifier = Modifier.padding(12.dp)
                )
            }

            // 时间戳和编辑状态
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = formatTimestamp(message.sendTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 如果消息被编辑过，显示"已编辑"标记
                if (message.editTime != null && message.editTime > 0) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "已编辑",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "已编辑",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 截图专用的发送者姓名和标签组件
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SenderNameAndTagsForScreenshot(
    message: ChatMessage
) {
    val tags = message.sender.tag ?: emptyList()
     
    Column(
        modifier = Modifier
            .wrapContentWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            modifier = Modifier.wrapContentWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.Start)
        ) {
            Text(
                text = message.sender.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )

            if (message.sender.chatType == 3) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "机器人",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            tags.forEach { tag ->
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = parseTagColor(tag.color)
                ) {
                    Text(
                        text = tag.text,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * 截图专用的消息内容组件 - 使用真实的MessageContentView
 */
@Composable
private fun MessageContentForScreenshot(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    // 直接使用真实的MessageContentView组件，确保与原始ChatScreen完全一致
    MessageContentView(
        message = message,
        content = message.content,
        contentType = message.contentType,
        isMyMessage = false, // 截图中统一显示为对方消息
        conversationChatType = 2, // 假设为群聊
        modifier = modifier,
        enableHardware = false, // 强制禁用硬件位图
        onImageClick = { }, // 截图中不需要交互
        onLongClick = { }, // 截图中不需要交互
        onQuoteMessageClick = { }, // 截图中不需要交互
        onQuote = { _, _ -> }, // 截图中不需要交互
        onEdit = { }, // 截图中不需要交互
        onRecall = { }, // 截图中不需要交互
        onPlusOne = { } // 截图中不需要交互
    )
}

/** 保存成功后才发布到图库；失败或取消时删除尚未完成的条目。 */
suspend fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Boolean =
    withContext(Dispatchers.IO) {
        try {
            check(!bitmap.isRecycled) { "图片已释放，请重新生成" }
            val fileName = "messages_${System.currentTimeMillis()}.png"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/YhChat")
                    put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("无法创建图库文件")
                try {
                    resolver.openOutputStream(uri)?.use { output ->
                        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "图片编码失败" }
                    } ?: error("无法写入图库文件")
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    val published = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                    }
                    check(resolver.update(uri, published, null, null) > 0) { "图库文件发布失败" }
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    throw e
                }
            } else {
                @Suppress("DEPRECATION")
                val directory = java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "YhChat")
                check(directory.isDirectory || directory.mkdirs()) { "无法创建相册目录，请检查存储权限" }
                val file = java.io.File(directory, fileName)
                try {
                    file.outputStream().use { output ->
                        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "图片编码失败" }
                    }
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/png"), null)
                } catch (e: Exception) {
                    file.delete()
                    throw e
                }
            }
            true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("ScreenshotComponents", "保存图片失败", e)
            false
        }
    }

/** 分享缓存中的 PNG，不必先向图库写入，也不会把私有文件路径交给外部应用。 */
suspend fun shareMessagesImage(context: Context, bitmap: Bitmap) {
    val uri = withContext(Dispatchers.IO) {
        val directory = java.io.File(context.cacheDir, "chat_image_exports")
        check(directory.isDirectory || directory.mkdirs()) { "无法创建分享缓存" }
        // 分享接收方可能延迟读取，只清理一天以前的导出文件。
        directory.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000L }
            ?.forEach { it.delete() }
        val file = java.io.File(directory, "messages_${java.util.UUID.randomUUID()}.png")
        try {
            file.outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "图片编码失败" }
            }
            androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            file.delete()
            throw e
        }
    }
    withContext(Dispatchers.Main.immediate) {
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newRawUri("聊天记录", uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "分享聊天记录"))
    }
}
