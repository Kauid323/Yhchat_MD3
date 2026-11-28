package com.yhchat.canary.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

/**
 * 滚动感知导航栏控制器
 * 
 * 模仿 LibChecker 的 HideBottomViewOnScrollBehavior
 * - 向下滚动（往底部翻）时隐藏导航栏
 * - 向上滚动（往顶部翻）时显示导航栏
 * - 需要累积一定滚动距离才触发状态改变
 * - 停止滚动时保持当前状态，不自动恢复
 */
@Composable
fun rememberScrollAwareNavigationState(): ScrollAwareNavigationState {
    val autoHideEnabled by rememberBooleanPreference("display_settings", "navigation_auto_hide", true)
    val state = remember { ScrollAwareNavigationState() }
    androidx.compose.runtime.SideEffect { state.updateAutoHideEnabled(autoHideEnabled) }
    return state
}

/**
 * 滚动感知导航栏状态
 */
class ScrollAwareNavigationState {
    private val _isVisible = mutableStateOf(true)
    val isVisible: Boolean by _isVisible
    
    private var autoHideEnabled = true

    fun updateAutoHideEnabled(enabled: Boolean) {
        if (autoHideEnabled == enabled) return
        autoHideEnabled = enabled
        show()
    }

    // 累积滚动距离
    private var accumulatedScroll = 0f
    
    // 滚动阈值（像素）- 需要累积这么多距离才触发状态改变
    // LibChecker 使用的是 view.measuredHeight，这里用固定值模拟
    private val scrollThreshold = 80f
    
    /**
     * 创建嵌套滚动连接
     */
    fun nestedScrollConnection() = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (!autoHideEnabled) return Offset.Zero
            val delta = available.y
            
            // 根据滚动方向累积或重置
            if (delta > 0 && accumulatedScroll < 0) {
                // 方向改变：从向下滚动变为向上滚动，重置累积
                accumulatedScroll = 0f
            } else if (delta < 0 && accumulatedScroll > 0) {
                // 方向改变：从向上滚动变为向下滚动，重置累积
                accumulatedScroll = 0f
            }
            
            accumulatedScroll += delta
            
            // 向下滚动（delta < 0，手指往上划）且累积滚动超过阈值时隐藏导航栏
            if (accumulatedScroll < -scrollThreshold && _isVisible.value) {
                _isVisible.value = false
                accumulatedScroll = 0f
            }
            // 向上滚动（delta > 0，手指往下划）且累积滚动超过阈值时显示导航栏
            else if (accumulatedScroll > scrollThreshold && !_isVisible.value) {
                _isVisible.value = true
                accumulatedScroll = 0f
            }
            
            return Offset.Zero
        }
    }
    
    /**
     * 处理滚动变化（用于LazyList等）
     * @param delta 滚动增量，正值表示向上滚动（往顶部翻），负值表示向下滚动（往底部翻）
     */
    fun onScroll(delta: Int) {
        if (!autoHideEnabled) return
        // 根据滚动方向累积或重置
        if (delta > 0 && accumulatedScroll < 0) {
            accumulatedScroll = 0f
        } else if (delta < 0 && accumulatedScroll > 0) {
            accumulatedScroll = 0f
        }
        
        accumulatedScroll += delta.toFloat()
        
        // 向下滚动（delta < 0）且累积滚动超过阈值时隐藏导航栏
        if (accumulatedScroll < -scrollThreshold && _isVisible.value) {
            _isVisible.value = false
            accumulatedScroll = 0f
        }
        // 向上滚动（delta > 0）且累积滚动超过阈值时显示导航栏
        else if (accumulatedScroll > scrollThreshold && !_isVisible.value) {
            _isVisible.value = true
            accumulatedScroll = 0f
        }
    }
    
    /**
     * 显示导航栏
     */
    fun show() {
        _isVisible.value = true
        accumulatedScroll = 0f
    }
    
    /**
     * 隐藏导航栏
     */
    fun hide() {
        _isVisible.value = false
        accumulatedScroll = 0f
    }
    
    /**
     * 切换导航栏可见性
     */
    fun toggle() {
        _isVisible.value = !_isVisible.value
        accumulatedScroll = 0f
    }
    
    /**
     * 重置累积滚动
     */
    fun resetAccumulation() {
        accumulatedScroll = 0f
    }
}

/**
 * 监听 LazyListState 的滚动状态并自动控制导航栏
 * 
 * 采用LibChecker的逻辑：
 * - 需要累积一定滚动距离才触发状态改变
 * - 停止滚动时保持当前状态，不自动恢复
 * - 只有在列表顶部时才强制显示导航栏
 */
@Composable
fun observeScrollForNavigation(listState: LazyListState, navigationState: ScrollAwareNavigationState) {
    var previousIndex by remember { mutableIntStateOf(listState.firstVisibleItemIndex) }
    var previousScrollOffset by remember { mutableIntStateOf(listState.firstVisibleItemScrollOffset) }
    
    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        // Avoid "auto show" caused by overscroll rebound or list settling when the user
        // is no longer actively scrolling. This keeps bars hidden at the bottom/end unless
        // the user actually scrolls up.
        if (!listState.isScrollInProgress) {
            previousIndex = listState.firstVisibleItemIndex
            previousScrollOffset = listState.firstVisibleItemScrollOffset
            return@LaunchedEffect
        }

        val currentIndex = listState.firstVisibleItemIndex
        val currentScrollOffset = listState.firstVisibleItemScrollOffset
        
        if (previousIndex != currentIndex || previousScrollOffset != currentScrollOffset) {
            // 计算滚动增量（正值表示向上滚动/往顶部翻，负值表示向下滚动/往底部翻）
            val indexDelta = previousIndex - currentIndex
            val offsetDelta = previousScrollOffset - currentScrollOffset
            
            // 综合计算滚动方向和距离
            // 如果index变了，使用一个较大的值来表示方向
            val delta = if (indexDelta != 0) {
                indexDelta * 200 // 每个item大约200px
            } else {
                offsetDelta
            }
            
            // 使用新的onScroll方法来处理累积滚动
            navigationState.onScroll(delta)
            
            // 仅在真正回到顶部时强制显示，避免滚动过程中直接闪现到终态
            if (currentIndex == 0 && currentScrollOffset == 0 && !navigationState.isVisible) {
                navigationState.show()
            }
            
            previousIndex = currentIndex
            previousScrollOffset = currentScrollOffset
        }
    }
}

/**
 * 监听 LazyGridState 的滚动状态并自动控制导航栏
 * 
 * 采用LibChecker的逻辑：
 * - 需要累积一定滚动距离才触发状态改变
 * - 停止滚动时保持当前状态，不自动恢复
 * - 只有在列表顶部时才强制显示导航栏
 */
@Composable
fun observeScrollForNavigation(gridState: LazyGridState, navigationState: ScrollAwareNavigationState) {
    var previousIndex by remember { mutableIntStateOf(gridState.firstVisibleItemIndex) }
    var previousScrollOffset by remember { mutableIntStateOf(gridState.firstVisibleItemScrollOffset) }
    
    LaunchedEffect(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset) {
        val currentIndex = gridState.firstVisibleItemIndex
        val currentScrollOffset = gridState.firstVisibleItemScrollOffset
        
        if (previousIndex != currentIndex || previousScrollOffset != currentScrollOffset) {
            // 计算滚动增量（正值表示向上滚动/往顶部翻，负值表示向下滚动/往底部翻）
            val indexDelta = previousIndex - currentIndex
            val offsetDelta = previousScrollOffset - currentScrollOffset
            
            // 综合计算滚动方向和距离
            val delta = if (indexDelta != 0) {
                indexDelta * 200
            } else {
                offsetDelta
            }
            
            // 使用新的onScroll方法来处理累积滚动
            navigationState.onScroll(delta)
            
            // 仅在真正回到顶部时强制显示，避免滚动过程中直接闪现到终态
            if (currentIndex == 0 && currentScrollOffset == 0 && !navigationState.isVisible) {
                navigationState.show()
            }
            
            previousIndex = currentIndex
            previousScrollOffset = currentScrollOffset
        }
    }
}

/**
 * 监听 ScrollState 的滚动状态并自动控制导航栏
 * 用于 Column + verticalScroll
 * 
 * 采用LibChecker的逻辑：
 * - 需要累积一定滚动距离才触发状态改变
 * - 停止滚动时保持当前状态，不自动恢复
 * - 只有在列表顶部时才强制显示导航栏
 */
@Composable
fun observeScrollForNavigation(scrollState: ScrollState, navigationState: ScrollAwareNavigationState) {
    var previousScrollOffset by remember { mutableIntStateOf(scrollState.value) }
    
    LaunchedEffect(scrollState.value) {
        val currentScrollOffset = scrollState.value
        
        if (previousScrollOffset != currentScrollOffset) {
            // 计算滚动增量（正值表示向上滚动/往顶部翻，负值表示向下滚动/往底部翻）
            val delta = previousScrollOffset - currentScrollOffset
            
            // 使用新的onScroll方法来处理累积滚动
            navigationState.onScroll(delta)
            
            // 仅在真正回到顶部时强制显示，避免滚动过程中直接闪现到终态
            if (currentScrollOffset == 0 && !navigationState.isVisible) {
                navigationState.show()
            }
            
            previousScrollOffset = currentScrollOffset
        }
    }
}
