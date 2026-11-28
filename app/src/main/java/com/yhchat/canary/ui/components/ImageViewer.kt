package com.yhchat.canary.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface as AndroidSurface
import android.view.TextureView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.Surface
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.MotionPhotosOn
import kotlinx.coroutines.delay
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Precision
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.zip.GZIPInputStream

@Composable
fun ImageViewer(
    imageUrl: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    ImageViewer(
        imageUrls = listOf(imageUrl),
        initialIndex = 0,
        onDismiss = onDismiss,
        modifier = modifier
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageViewer(
    imageUrls: List<String>,
    initialIndex: Int = 0,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    imageKeys: List<String>? = null,
    onPageSettled: ((Int) -> Unit)? = null,
    isLoadingMore: Boolean = false,
    loadMoreError: String? = null,
    onRetryLoadMore: (() -> Unit)? = null
) {
    // Keep duplicate URLs: two messages can contain the same picture; pager keys use the index.
    val validIndices = remember(imageUrls) { imageUrls.indices.filter { imageUrls[it].isNotBlank() } }
    val sanitizedUrls = remember(imageUrls) { validIndices.map { imageUrls[it] } }
    val keys = remember(imageUrls, imageKeys) {
        validIndices.map { index -> imageKeys?.getOrNull(index) ?: "$index:${imageUrls[index]}" }
    }
    if (sanitizedUrls.isEmpty()) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestDismiss by rememberUpdatedState(onDismiss)
    val openingIndex = remember(imageUrls, initialIndex) {
        imageUrls.take(initialIndex.coerceAtLeast(0)).count { it.isNotBlank() }.coerceIn(0, sanitizedUrls.lastIndex)
    }
    // PagerState is outside Dialog's subcomposition. Its page count can update before
    // the Dialog recomposes; callbacks must not capture an older URL/key list.
    val currentPages by rememberUpdatedState(keys.zip(sanitizedUrls))
    val pagerState = rememberPagerState(initialPage = openingIndex, pageCount = { currentPages.size })
    val zoomedPages = remember { mutableStateMapOf<String, Boolean>() }
    val gestureLockedPages = remember { mutableStateMapOf<String, Boolean>() }
    val refreshKeys = remember { mutableStateMapOf<String, Int>() }
    val progress = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    var drag by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }
    var returning by remember { mutableStateOf(false) }
    var viewerHeight by remember { mutableFloatStateOf(1f) }
    var viewport by remember { mutableStateOf(Size.Zero) }
    val displayedRects = remember { mutableStateMapOf<String, Rect>() }
    val ownerWindow = androidx.compose.ui.platform.LocalView.current.windowToken
    var near by remember { mutableStateOf(ViewerThumbnails.bounds(sanitizedUrls[openingIndex], ownerWindow)) }
    var closingRect by remember { mutableStateOf<Rect?>(null) }
    val currentKey = keys[pagerState.currentPage.coerceIn(0, keys.lastIndex)]
    val zoomed = zoomedPages[currentKey] == true
    val locked = gestureLockedPages[currentKey] == true
    val currentImageUrl = sanitizedUrls[pagerState.currentPage.coerceIn(0, sanitizedUrls.lastIndex)]
    val dragFraction = (drag.y / viewerHeight.coerceAtLeast(1f)).coerceIn(0f, 1f)
    val dragScale = 1f - dragFraction * 0.45f
    val backdrop = progress.value * (1f - (dragFraction * 2).coerceIn(0f, 1f))

    fun close() {
        if (closing) return
        near = ViewerThumbnails.bounds(currentImageUrl, ownerWindow)
        val shown = displayedRects[currentKey]
        closingRect = shown?.let {
            val center = Offset(viewport.width / 2f, viewport.height / 2f)
            Rect(center + (it.topLeft - center) * dragScale + drag,
                center + (it.bottomRight - center) * dragScale + drag)
        }
        closing = true
        scope.launch {
            progress.animateTo(0f, tween(200))
            latestDismiss()
        }
    }
    val canDrag by rememberUpdatedState(!closing && !returning && progress.value == 1f && !zoomed && !locked && !pagerState.isScrollInProgress)
    val releaseDrag by rememberUpdatedState<(Boolean) -> Unit> { dismiss ->
        dragging = false
        if (dismiss) close() else {
            returning = true
            val from = drag
            scope.launch {
                try {
                    animate(0f, 1f, animationSpec = tween(220)) { value, _ -> drag = from * (1f - value) }
                } finally {
                    drag = Offset.Zero
                    returning = false
                }
            }
        }
    }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(240)) }
    LaunchedEffect(sanitizedUrls, openingIndex) {
        if (pagerState.currentPage !in sanitizedUrls.indices) pagerState.scrollToPage(openingIndex)
    }
    val notifyPage by rememberUpdatedState(onPageSettled)
    LaunchedEffect(pagerState.settledPage, pagerState.isScrollInProgress, keys, closing) {
        // Give the pager a layout pass to relocate its stable key after a prepend.
        androidx.compose.runtime.withFrameNanos { }
        val page = pagerState.currentPage
        val expectedKey = currentPages.getOrNull(page)?.first
        val laidOutKey = pagerState.layoutInfo.visiblePagesInfo.firstOrNull { it.index == page }?.key
        if (!closing && !pagerState.isScrollInProgress && expectedKey != null && laidOutKey == expectedKey) {
            notifyPage?.invoke(page)
        }
    }
    Dialog(
        onDismissRequest = { close() },
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false,
            usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        BackHandler { close() }
        val view = androidx.compose.ui.platform.LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
            window?.setDimAmount(0f)
            window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            onDispose { }
        }
        Box(modifier.fillMaxSize().onSizeChanged { viewerHeight = it.height.toFloat(); viewport = Size(it.width.toFloat(), it.height.toFloat()) }) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = backdrop.coerceIn(0f, 1f))))
            Box(
                Modifier.fillMaxSize()
                    .viewerDismissGesture(
                        enabled = { canDrag },
                        onDrag = { dragging = true; drag = it },
                        onRelease = { releaseDrag(it) }
                    )
                    .graphicsLayer {
                        alpha = if (near != null && progress.value < 1f && !isTgsUrl(currentImageUrl)) 0f else progress.value
                        translationX = drag.x
                        translationY = drag.y
                        scaleX = (0.9f + 0.1f * progress.value) * dragScale
                        scaleY = scaleX
                    }
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    pageSpacing = 16.dp,
                    userScrollEnabled = !zoomed && !locked && !dragging && !returning && !closing,
                    key = { page -> currentPages.getOrNull(page)?.first ?: "viewer-pending-page:$page" }
                ) { page ->
                    val picture = currentPages.getOrNull(page) ?: return@HorizontalPager
                    val pageKey = picture.first
                    ZoomableImagePage(
                        imageUrl = picture.second,
                        reloadKey = refreshKeys[pageKey] ?: 0,
                        active = page == pagerState.currentPage,
                        onToggleControls = { close() },
                        onZoomStateChange = { zoomedPages[pageKey] = it },
                        onDisplayedRect = { displayedRects[pageKey] = it },
                        onGestureLockChange = { gestureLockedPages[pageKey] = it }
                    )
                }
            }
            // Fly the current picture between its on-screen thumbnail and fitted/zoomed bounds.
            val source = near
            if (source != null && progress.value < 1f && viewport.width > 0f && !isTgsUrl(currentImageUrl)) {
                val destination = if (closing) closingRect else displayedRects[currentKey]
                val far = destination ?: Rect(Offset.Zero, viewport)
                val rect = androidx.compose.ui.geometry.lerp(source, far, progress.value)
                val density = LocalDensity.current
                AsyncImage(
                    model = createViewerImageRequest(context, currentImageUrl, viewport.width.toInt().coerceAtLeast(1), viewport.height.toInt().coerceAtLeast(1)),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                        .requiredSize(with(density) { rect.width.coerceAtLeast(1f).toDp() }, with(density) { rect.height.coerceAtLeast(1f).toDp() })
                        .clip(RoundedCornerShape((8f * (1f - progress.value)).dp))
                )
            }
            val showChrome = progress.value == 1f && !closing && !dragging && !returning && !zoomed
            AnimatedVisibility(showChrome, enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    ViewerAction(Icons.Default.Close, "关闭", onClick = { close() })
                    Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.14f), contentColor = Color.White) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isLoadingMore) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Text("${pagerState.currentPage + 1} / ${sanitizedUrls.size}", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            if (loadMoreError != null && showChrome) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 24.dp, vertical = 88.dp),
                    shape = RoundedCornerShape(16.dp), color = Color(0xFF242424), contentColor = Color.White
                ) {
                    Row(Modifier.padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(loadMoreError, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        androidx.compose.material3.TextButton(onClick = { onRetryLoadMore?.invoke() }, enabled = !isLoadingMore) {
                            Text("重试", color = Color.White)
                        }
                    }
                }
            }
            AnimatedVisibility(showChrome, enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 20.dp)) {
                Surface(shape = CircleShape, color = Color(0xFF242424).copy(alpha = 0.92f), contentColor = Color.White) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ViewerAction(Icons.Default.Refresh, "重载") {
                            val pageKey = currentKey
                            refreshKeys[pageKey] = (refreshKeys[pageKey] ?: 0) + 1
                        }
                        ViewerAction(Icons.Default.Download, "保存") { downloadImageToGallery(context, currentImageUrl) }
                        ViewerAction(Icons.Default.Share, "分享") { shareImage(context, currentImageUrl) }
                        ViewerAction(Icons.Default.ContentCopy, "复制链接") {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("图片链接", currentImageUrl))
                            Toast.makeText(context, "已复制图片链接", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewerAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, colors = IconButtonDefaults.iconButtonColors(
        containerColor = Color.White.copy(alpha = 0.12f), contentColor = Color.White)) {
        Icon(icon, label)
    }
}
@Composable
fun SimpleImageViewer(
    imageUrl: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    ImageViewer(
        imageUrl = imageUrl,
        onDismiss = onDismiss,
        modifier = modifier
    )
}

@Composable
fun AdvancedImageViewer(
    imageUrl: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    ImageViewer(
        imageUrl = imageUrl,
        onDismiss = onDismiss,
        modifier = modifier
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ZoomableImagePage(
    imageUrl: String,
    reloadKey: Int = 0,
    active: Boolean = true,
    onToggleControls: () -> Unit,
    onZoomStateChange: (Boolean) -> Unit,
    onGestureLockChange: (Boolean) -> Unit,
    onDisplayedRect: (Rect) -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val requestWidth = with(density) { configuration.screenWidthDp.dp.roundToPx() * 2 }
    val requestHeight = with(density) { configuration.screenHeightDp.dp.roundToPx() * 2 }
    var scale by remember(imageUrl) { mutableFloatStateOf(1f) }
    var offsetX by remember(imageUrl) { mutableFloatStateOf(0f) }
    var offsetY by remember(imageUrl) { mutableFloatStateOf(0f) }
    var containerSize by remember(imageUrl) { mutableStateOf(Size.Zero) }
    val imageRequest = remember(context, imageUrl, requestWidth, requestHeight, reloadKey) {
        createViewerImageRequest(
            context = context,
            imageUrl = imageUrl,
            width = requestWidth,
            height = requestHeight
        ).newBuilder().apply {
            if (reloadKey > 0) {
                memoryCachePolicy(coil.request.CachePolicy.DISABLED)
                diskCachePolicy(coil.request.CachePolicy.DISABLED)
            }
        }.build()
    }
    val painter = rememberAsyncImagePainter(
        model = imageRequest
    )

    // 扫描图片文件字节，异步检测与提取实况照片 (Live Photo / Motion Photo) 内嵌视频
    val liveVideoFileState = produceState<File?>(initialValue = null, imageUrl, reloadKey) {
        value = LivePhotoUtil.extractLivePhotoVideo(context, imageUrl)
    }
    val liveVideoFile = liveVideoFileState.value
    var isLivePhotoPlaying by remember(imageUrl) { mutableStateOf(false) }

    // 自适应计算原图在 Fit 模式下的显示分辨率、尺寸与位置
    val intrinsicSize = painter.intrinsicSize
    val hasIntrinsic = intrinsicSize.isSpecified && intrinsicSize.width > 0f && intrinsicSize.height > 0f
    val (fittedWidthDp, fittedHeightDp) = if (hasIntrinsic && containerSize.width > 0f && containerSize.height > 0f) {
        val imageAspect = intrinsicSize.width / intrinsicSize.height
        val containerAspect = containerSize.width / containerSize.height
        val (widthPx, heightPx) = if (imageAspect > containerAspect) {
            containerSize.width to (containerSize.width / imageAspect)
        } else {
            (containerSize.height * imageAspect) to containerSize.height
        }
        with(density) { widthPx.toDp() } to with(density) { heightPx.toDp() }
    } else {
        with(density) { containerSize.width.toDp() } to with(density) { containerSize.height.toDp() }
    }

    val fittedSize = with(density) { Size(fittedWidthDp.toPx(), fittedHeightDp.toPx()) }
    LaunchedEffect(fittedSize, containerSize, scale, offsetX, offsetY) {
        if (fittedSize.width > 0f && fittedSize.height > 0f) {
            val width = fittedSize.width * scale
            val height = fittedSize.height * scale
            val left = (containerSize.width - width) / 2f + offsetX
            val top = (containerSize.height - height) / 2f + offsetY
            onDisplayedRect(Rect(left, top, left + width, top + height))
        }
    }
    DisposableEffect(imageUrl) {
        onDispose { onZoomStateChange(false); onGestureLockChange(false) }
    }
    var zoomAnimation by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    LaunchedEffect(active, reloadKey) {
        zoomAnimation?.cancel()
        scale = 1f; offsetX = 0f; offsetY = 0f
        isLivePhotoPlaying = false
        onGestureLockChange(false)
    }
    val pageScope = rememberCoroutineScope()
    fun toggleZoom(tap: Offset) {
        zoomAnimation?.cancel()
        val targetScale = if (kotlin.math.abs(scale - 1f) > 0.02f) 1f else 2.5f
        val centered = tap - Offset(containerSize.width / 2f, containerSize.height / 2f)
        val targetOffset = if (targetScale == 1f) Offset.Zero else boundImageOffset(
            containerSize, targetScale, fittedSize, centered * (1f - targetScale)
        )
        val fromScale = scale
        val fromOffset = Offset(offsetX, offsetY)
        onGestureLockChange(true)
        zoomAnimation = pageScope.launch {
            try {
                animate(0f, 1f, animationSpec = tween(220)) { fraction, _ ->
                    scale = fromScale + (targetScale - fromScale) * fraction
                    val offset = fromOffset + (targetOffset - fromOffset) * fraction
                    offsetX = offset.x
                    offsetY = offset.y
                }
            } finally { onGestureLockChange(false) }
        }
    }
    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        zoomAnimation?.cancel()
        if (!zoomChange.isFinite() || zoomChange <= 0f) return@rememberTransformableState
        val newScale = (scale * zoomChange).coerceIn(0.5f, 5f)
        if (newScale <= 1f) {
            scale = newScale
            offsetX = 0f
            offsetY = 0f
            return@rememberTransformableState
        }

        val boundedOffset = boundImageOffset(
            containerSize = containerSize,
            contentSize = fittedSize,
            scale = newScale,
            desiredOffset = Offset(
                x = offsetX + panChange.x,
                y = offsetY + panChange.y
            )
        )

        scale = newScale
        offsetX = boundedOffset.x
        offsetY = boundedOffset.y
    }

    LaunchedEffect(scale) {
        onZoomStateChange(scale > 1.02f)
    }

    Box(
        modifier = Modifier.fillMaxSize().clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        if (isTgsUrl(imageUrl)) {
            TgsImagePage(
                imageUrl = imageUrl,
                reloadKey = reloadKey,
                onToggleControls = onToggleControls
            )
            return@Box
        }

        when (painter.state) {
            is AsyncImagePainter.State.Loading -> {
                CircularProgressIndicator()
            }

            is AsyncImagePainter.State.Error -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "图片加载失败",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = imageUrl,
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }

            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged {
                            containerSize = Size(it.width.toFloat(), it.height.toFloat())
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // 底层静止原图
                    androidx.compose.foundation.Image(
                        painter = painter,
                        contentDescription = "预览图片",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(0.dp))
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY
                            ),
                        contentScale = ContentScale.Fit
                    )

                    // 实况图片播放层：精确自适应原图 Fit 显示分辨率、尺寸、缩放与位移
                    if (active && isLivePhotoPlaying && liveVideoFile != null) {
                        LivePhotoVideoPlayer(
                            videoFile = liveVideoFile,
                            isPlaying = true,
                            modifier = Modifier
                                .size(fittedWidthDp, fittedHeightDp)
                                .clip(RoundedCornerShape(0.dp))
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offsetX,
                                    translationY = offsetY
                                )
                        )
                    }

                    // 统一手势处理层（缩放、双击、长按播放实况、点击显隐控制栏）
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .transformable(
                                state = transformableState,
                                canPan = { scale > 1.02f }
                            )
                            .pointerInput(imageUrl) {
                                awaitEachGesture {
                                    var lockPager = false
                                    var hasPressedPointers: Boolean
                                    do {
                                        val event = awaitPointerEvent()
                                        val pressedCount = event.changes.count { it.pressed }
                                        val shouldLock = pressedCount > 1 || scale > 1.02f
                                        hasPressedPointers = event.changes.any { it.pressed }
                                        if (shouldLock != lockPager) {
                                            lockPager = shouldLock
                                            onGestureLockChange(lockPager)
                                        }
                                    } while (hasPressedPointers)
                                    if (lockPager) {
                                        onGestureLockChange(false)
                                    }
                                }
                            }
                            .pointerInput(imageUrl, liveVideoFile) {
                                if (liveVideoFile != null) {
                                    detectTapGestures(
                                        onPress = {
                                            try { tryAwaitRelease() } finally { isLivePhotoPlaying = false }
                                        },
                                        onLongPress = { isLivePhotoPlaying = true },
                                        onDoubleTap = { toggleZoom(it) },
                                        onTap = {
                                            onToggleControls()
                                        }
                                    )
                                } else {
                                    detectTapGestures(
                                        onDoubleTap = { toggleZoom(it) },
                                        onTap = {
                                            onToggleControls()
                                        },
                                        onLongPress = {
                                            if (scale <= 1f) {
                                                onToggleControls()
                                            }
                                        }
                                    )
                                }
                            }
                    )
                }

                // 实况照片 (Live Photo) 标识徽章
                if (liveVideoFile != null) {
                    Surface(
                        onClick = { isLivePhotoPlaying = !isLivePhotoPlaying },
                        shape = RoundedCornerShape(20.dp),
                        color = if (isLivePhotoPlaying) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.6f),
                        contentColor = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(start = 16.dp, top = 60.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MotionPhotosOn,
                                contentDescription = "实况图片",
                                modifier = Modifier.size(16.dp),
                                tint = if (isLivePhotoPlaying) MaterialTheme.colorScheme.onPrimary else Color.White
                            )
                            Text(
                                text = if (isLivePhotoPlaying) "实况 播放中" else "实况",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isLivePhotoPlaying) MaterialTheme.colorScheme.onPrimary else Color.White
                            )
                        }
                    }
                }
            }
        }

    }
}

/**
 * 原生实况视频播放组件
 * 使用 TextureView + MediaPlayer 进行高帧率、低开销的平滑实况循环播放
 */
@Composable
private fun LivePhotoVideoPlayer(
    videoFile: File,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    var mediaPlayer by remember(videoFile) { mutableStateOf<MediaPlayer?>(null) }
    var isSurfaceReady by remember(videoFile) { mutableStateOf(false) }

    DisposableEffect(videoFile) {
        val player = MediaPlayer().apply {
            setDataSource(videoFile.absolutePath)
            isLooping = true
            prepareAsync()
        }
        mediaPlayer = player

        onDispose {
            try {
                player.stop()
                player.release()
            } catch (_: Exception) {}
            mediaPlayer = null
        }
    }

    LaunchedEffect(isPlaying, mediaPlayer, isSurfaceReady) {
        val player = mediaPlayer ?: return@LaunchedEffect
        if (isSurfaceReady) {
            if (isPlaying) {
                if (!player.isPlaying) {
                    player.start()
                }
            } else {
                if (player.isPlaying) {
                    player.pause()
                    player.seekTo(0)
                }
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                        mediaPlayer?.setSurface(AndroidSurface(surface))
                        isSurfaceReady = true
                        if (isPlaying && mediaPlayer?.isPlaying == false) {
                            mediaPlayer?.start()
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        isSurfaceReady = false
                        mediaPlayer?.setSurface(null)
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                }
            }
        },
        modifier = modifier
    )
}

@Composable
private fun TgsImagePage(
    imageUrl: String,
    reloadKey: Int = 0,
    onToggleControls: () -> Unit
) {
    val context = LocalContext.current
    val lottieJsonState = produceState<String?>(initialValue = null, imageUrl, reloadKey) {
        value = loadTgsAsLottieJson(context, imageUrl)
    }
    val lottieJson = lottieJsonState.value

    if (lottieJson.isNullOrBlank()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "TGS 加载失败",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = imageUrl,
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
        return
    }

    val composition by rememberLottieComposition(
        spec = LottieCompositionSpec.JsonString(lottieJson)
    )

    if (composition == null) {
        CircularProgressIndicator()
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(imageUrl) {
                detectTapGestures(
                    onTap = { onToggleControls() },
                    onLongPress = { onToggleControls() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        LottieAnimation(
            composition = composition,
            iterations = LottieConstants.IterateForever,
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun boundImageOffset(
    containerSize: Size,
    scale: Float,
    contentSize: Size = containerSize,
    desiredOffset: Offset
): Offset {
    val maxX = (((contentSize.width * scale) - containerSize.width) / 2f).coerceAtLeast(0f)
    val maxY = (((contentSize.height * scale) - containerSize.height) / 2f).coerceAtLeast(0f)
    return Offset(
        x = desiredOffset.x.coerceIn(-maxX, maxX),
        y = desiredOffset.y.coerceIn(-maxY, maxY)
    )
}

private fun createViewerImageRequest(
    context: Context,
    imageUrl: String,
    width: Int,
    height: Int
): ImageRequest {
    return ImageRequest.Builder(context)
        .data(imageUrl)
        .apply {
            if (imageUrl.contains(".jwznb.com")) {
                setHeader("Referer", "https://myapp.jwznb.com")
                setHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
            }
        }
        .size(width, height)
        .precision(Precision.INEXACT)
        .crossfade(false)
        .build()
}

private fun isTgsUrl(url: String): Boolean {
    val clean = url.substringBefore('?').substringBefore('#').lowercase()
    return clean.endsWith(".tgs")
}

private suspend fun loadTgsAsLottieJson(context: Context, imageUrl: String): String? {
    return withContext(Dispatchers.IO) {
        try {
            val connection = (URL(imageUrl).openConnection() as java.net.HttpURLConnection).apply {
                instanceFollowRedirects = true
                if (imageUrl.contains(".jwznb.com")) {
                    setRequestProperty("Referer", "https://myapp.jwznb.com")
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
                }
                connect()
            }

            connection.inputStream.use { input ->
                GZIPInputStream(input).bufferedReader(Charsets.UTF_8).use { reader ->
                    reader.readText()
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}

private fun extensionForContentType(contentType: String?): String {
    val type = contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
    return when (type) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "image/jpeg", "image/jpg" -> "jpg"
        else -> "jpg"
    }
}

private fun looksLikeImageExtension(extension: String): Boolean {
    return when (extension.lowercase()) {
        "jpg", "jpeg", "png", "webp", "gif" -> true
        else -> false
    }
}

private fun downloadImageToGallery(context: Context, imageUrl: String) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val picturesDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "云湖"
            )
            if (!picturesDir.exists()) {
                picturesDir.mkdirs()
            }
            val url = URL(imageUrl)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.instanceFollowRedirects = true
            if (imageUrl.contains(".jwznb.com")) {
                connection.setRequestProperty("Referer", "https://myapp.jwznb.com")
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
            }
            connection.connect()

            val contentType = connection.contentType ?: "image/*"
            val extFromType = extensionForContentType(contentType)
            val rawName = imageUrl.substringAfterLast("/", "")
                .substringBefore("?")
                .substringBefore("#")
            val rawExt = rawName.substringAfterLast('.', "").trim()
            val finalExt = if (rawExt.isNotBlank() && looksLikeImageExtension(rawExt)) {
                if (rawExt.equals("jpeg", ignoreCase = true)) "jpg" else rawExt.lowercase()
            } else {
                extFromType
            }
            val baseName = rawName.substringBeforeLast('.', "").takeIf { it.isNotBlank() }
                ?: "image_${System.currentTimeMillis()}"
            val fileName = "$baseName.$finalExt"

            var targetFile = File(picturesDir, fileName)
            var counter = 1
            while (targetFile.exists()) {
                targetFile = File(picturesDir, "${baseName}_$counter.$finalExt")
                counter++
            }

            connection.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), arrayOf(contentType), null)

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "图片已保存到相册", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

private fun shareImage(context: Context, imageUrl: String) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val cacheDir = context.cacheDir

            val url = URL(imageUrl)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.instanceFollowRedirects = true
            if (imageUrl.contains(".jwznb.com")) {
                connection.setRequestProperty("Referer", "https://myapp.jwznb.com")
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
            }
            connection.connect()

            val ext = extensionForContentType(connection.contentType)
            val tempFile = File(cacheDir, "share_${System.currentTimeMillis()}.$ext")

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                tempFile
            )

            withContext(Dispatchers.Main) {
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    type = "image/*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "分享图片"))
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
