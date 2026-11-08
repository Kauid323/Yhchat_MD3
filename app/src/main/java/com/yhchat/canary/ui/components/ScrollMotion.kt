package com.yhchat.canary.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 滚动驱动的动效基础设施，移植自 Oolong 的 `ScrollMotion.kt` / `PageScroll.kt`。
 *
 * 设计要点（跟原版一致）：
 *  - 内容该怎么滚还怎么滚，这套东西**只读**滚动量，绝不抢滚动；
 *  - 滚动量被换算成 0..1 的 [ScrollProgress]，在 `graphicsLayer` 的绘制阶段读，
 *    于是滚动只引起重绘，不引起重组、也不引起重新布局；
 *  - 顶栏的隐藏、头像的吸附、标题的缩小，全部走这条路。
 */

/**
 * 某个滚动驱动变化走到了哪，0 到 1。
 * 要在绘制阶段（比如 `graphicsLayer` 块里）读 [value] 或 [fraction]，
 * 这样滚动只会重画跟着它的东西。
 */
@Stable
class ScrollProgress internal constructor(private val read: () -> Float) {
    val value: Float get() = read()

    /** 在整个变化里，[range] 这一段走到了哪，0 之前是 0、之后是 1。 */
    fun fraction(range: ClosedFloatingPointRange<Float>): Float {
        val span = range.endInclusive - range.start
        return if (span > 0f) {
            ((value - range.start) / span).coerceIn(0f, 1f)
        } else {
            if (value >= range.start) 1f else 0f
        }
    }
}

/**
 * 跟着**滚动方向**走的进度，而不是跟着位置：内容往上滚它往 1 走，
 * 往下滚它往 0 回，被拉过顶部也回 0。
 *
 * 顶栏的收起/回来就是它：滑出去时顶栏一起走，往回滑一点它立刻回来。
 *
 * @param away 走完这段距离算「完全收起」
 * @param snapping 停下时不在半路，滑向更近的一端
 * @param enabled 为 false 时恒为 0（比如头部还没滚走时不该收顶栏）
 */
@Composable
fun rememberScrollProgress(
    away: Dp,
    snapping: Boolean = true,
    enabled: () -> Boolean = { true },
    resetKey: Any? = null,
    page: PageScrollState? = LocalPageScroll.current
): ScrollProgress {
    val distance = with(LocalDensity.current) { away.toPx() }
    val scope = rememberCoroutineScope()
    val currentEnabled by rememberUpdatedState(enabled)
    val progress = remember(page, distance, snapping, resetKey) {
        DirectedProgress(distance, snapping, scope) { currentEnabled() }
    }
    if (page != null) {
        DisposableEffect(page, progress) {
            page.add(progress)
            onDispose { page.remove(progress) }
        }
    }
    LaunchedEffect(progress) {
        snapshotFlow { currentEnabled() }.collect { if (!it) progress.reset() }
    }
    return remember(progress) { ScrollProgress { progress.value } }
}

/**
 * 跟随滚动方向的进度状态。
 *
 * Oolong 原版的 `toggle` 模式这里去掉了 —— 本项目顶栏不需要「翻到 1 就不回头」的行为，
 * 要的是跟着手指走的那种手感。
 */
internal class DirectedProgress(
    private val distance: Float,
    private val snapping: Boolean,
    private val scope: CoroutineScope,
    private val enabled: () -> Boolean
) {
    private var offset by mutableFloatStateOf(0f)
    private var animating: Job? = null
    private var animatingTo = Float.NaN

    val value: Float get() = if (distance > 0f) offset / distance else 0f

    private fun animateTo(target: Float) {
        if (animating?.isActive == true && animatingTo == target) return
        animating?.cancel()
        if (offset == target) return
        animatingTo = target
        animating = scope.launch {
            animate(offset, target, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { v, _ ->
                offset = v
            }
        }
    }

    fun reset() = animateTo(0f)

    fun onScroll(consumed: Float, available: Float) {
        if (!enabled()) {
            reset()
            return
        }
        if (consumed == 0f) {
            if (available > 0f) animateTo(0f)
            return
        }
        animating?.cancel()
        offset = (offset - consumed).coerceIn(0f, distance)
    }

    fun onScrollEnd() {
        if (snapping && offset > 0f && offset < distance) {
            animateTo(if (offset > distance / 2) distance else 0f)
        }
    }
}

/**
 * 一页内容的滚动状态：内容离顶部多远，以及它在往哪个方向走。
 *
 * 顶栏和内容共享它 —— 顶栏读 [scrollWatcher] 拿方向，内容用 [pageScroll] 上报位置。
 */
@Stable
class PageScrollState internal constructor() {
    internal class Source(val scrolled: () -> Int, val scrollable: ScrollableState?)

    private val sources = mutableStateListOf<Source>()
    private val directed = mutableListOf<DirectedProgress>()

    internal fun add(progress: DirectedProgress) = directed.add(progress)

    internal fun remove(progress: DirectedProgress) = directed.remove(progress)

    /** 监听这一页的每一次滚动，一点都不吃它。 */
    internal val scrollWatcher = object : NestedScrollConnection {
        override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: NestedScrollSource): androidx.compose.ui.geometry.Offset {
            directed.forEach { it.onScroll(consumed.y, available.y) }
            return androidx.compose.ui.geometry.Offset.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            directed.forEach { it.onScrollEnd() }
            return Velocity.Zero
        }
    }

    internal fun add(source: Source) = sources.add(source)

    internal fun remove(source: Source) = sources.remove(source)

    /** 内容被滚走了多少像素，没有列表上报时为 null。 */
    val scrolled: Int?
        get() = sources.firstOrNull()?.scrolled()

    /** 页面上那个主列表，供「拖页头也能滚内容」用。 */
    internal val firstScrollable: ScrollableState?
        get() = sources.firstNotNullOfOrNull { it.scrollable }

    /** 顶栏和内容都摆在这个布局里，吸附类的东西按它定位。 */
    internal var frame: LayoutCoordinates? = null

    internal fun boundsOf(coordinates: LayoutCoordinates): Rect {
        val frame = frame?.takeIf { it.isAttached } ?: return coordinates.boundsInWindow(clipBounds = false)
        return frame.localBoundingBoxOf(coordinates, clipBounds = false)
    }

    /** 顶栏那一行在 [frame] 里的上下边，没布局完时是 NaN。 */
    internal var barRowTop by mutableFloatStateOf(Float.NaN)
    internal var barRowBottom by mutableFloatStateOf(Float.NaN)

    /** 每个 [matchedGeometryAnchor] 上次被摆在哪。 */
    internal val anchors = mutableStateMapOf<Any, Rect>()
}

/** 顶栏和内容共享的滚动状态。 */
val LocalPageScroll = compositionLocalOf<PageScrollState?> { null }

/** 造一个 [PageScrollState] 交给页面。 */
@Composable
fun rememberPageScrollState(): PageScrollState = remember { PageScrollState() }

/** 让这段内容把自己的滚动位置报给 [PageScrollState]。 */
@Composable
fun Modifier.pageScroll(scrollable: ScrollableState?, scrolled: () -> Int): Modifier {
    val state = LocalPageScroll.current ?: return this
    val source = remember(state, scrollable) { PageScrollState.Source(scrolled, scrollable) }
    DisposableEffect(state, source) {
        state.add(source)
        onDispose { state.remove(source) }
    }
    return this
}

/** 懒列表版：它的第一项被顶上去多少，就是滚了多少。 */
@Composable
fun Modifier.pageScroll(state: LazyListState): Modifier {
    return pageScroll(state) {
        state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }?.let { -it.offset } ?: Int.MAX_VALUE
    }
}

/** 懒网格版。 */
@Composable
fun Modifier.pageScroll(state: LazyGridState): Modifier = pageScroll(state) {
    state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }?.let { -it.offset.y } ?: Int.MAX_VALUE
}

/** 普通滚动列版。 */
@Composable
fun Modifier.pageScroll(state: ScrollState): Modifier = pageScroll(state) { state.value }

/**
 * 标出 [matchedGeometry] 配对的起点：它自己不画任何东西，只报告自己占哪。
 * 滚出屏幕被销毁的锚点，会记住上次的位置。
 */
@Composable
fun Modifier.matchedGeometryAnchor(key: Any): Modifier {
    val page = LocalPageScroll.current ?: return this
    return onGloballyPositioned { page.anchors[key] = page.boundsOf(it) }
}

/**
 * 把这一块画在锚点 [key] 于进度 0 时的位置，进度 1 时画在它自己布局的位置，
 * 中间移动并缩放，高度对齐锚点。
 *
 * 它只改「怎么画」，所以跟着滚动只是重绘，不会重新布局 —— 这是头像
 * 从大头部一路缩进顶栏还依然流畅的原因。
 */
@Composable
fun Modifier.matchedGeometry(key: Any, progress: ScrollProgress): Modifier {
    val page = LocalPageScroll.current ?: return this
    val own = remember { mutableStateOf(Rect.Zero) }
    return onGloballyPositioned { own.value = page.boundsOf(it) }
        .graphicsLayer {
            val anchor = page.anchors[key]
            val self = own.value
            if (anchor == null || self.height <= 0f) {
                alpha = 0f
                return@graphicsLayer
            }
            val p = progress.value
            val scale = lerp(anchor.height / self.height, 1f, p)
            transformOrigin = TransformOrigin(0f, 0f)
            scaleX = scale
            scaleY = scale
            translationX = lerp(anchor.left - self.left, 0f, p)
            translationY = lerp(anchor.top - self.top, 0f, p)
        }
}

/**
 * 把拖拽这一块当作在滚页面内容，用于画在内容之上而不是内容里的东西，
 * 比如页头 —— 拖它也能滚下面的列表。
 */
@Composable
fun Modifier.scrollsPageContent(): Modifier {
    val page = LocalPageScroll.current ?: return this
    val forwarding = remember(page) { PageContentScrollable(page) }
    return scrollable(forwarding, Orientation.Vertical, reverseDirection = true)
}

/** 把滚动转给当前在视图里的那个内容。 */
private class PageContentScrollable(private val page: PageScrollState) : ScrollableState {
    private val target: ScrollableState? get() = page.firstScrollable

    override suspend fun scroll(scrollPriority: MutatePriority, block: suspend ScrollScope.() -> Unit) {
        target?.scroll(scrollPriority, block)
    }

    override fun dispatchRawDelta(delta: Float): Float = target?.dispatchRawDelta(delta) ?: 0f

    override val isScrollInProgress: Boolean get() = target?.isScrollInProgress ?: false
}
