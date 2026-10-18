package com.yhchat.canary.ui.components

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.yhchat.canary.data.model.NavigationItem

/**
 * 自适应导航栏 - LibChecker风格
 * 
 * 特点：
 * - 大屏设备显示侧边导航栏（NavigationRail）
 * - 小屏设备显示底部导航栏（NavigationBar）
 * - 支持滚动时自动隐藏
 * - Material Design 3 风格
 */
@Composable
fun AdaptiveNavigationBar(
    currentScreen: String,
    visibleItems: List<NavigationItem>,
    onScreenChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true
) {
    val configuration = LocalConfiguration.current
    val forceDisableLargeScreenMode by rememberBooleanPreference(
        preferencesName = "layout_settings",
        key = "force_disable_large_screen_mode",
        defaultValue = false
    )
    val isLargeScreen = isLargeScreenLayout(
        configuration = configuration,
        forceDisableLargeScreenMode = forceDisableLargeScreenMode
    )
    
    if (isLargeScreen) {
        // 侧边导航栏 - 平板模式
        NavigationRailBar(
            currentScreen = currentScreen,
            visibleItems = visibleItems,
            onScreenChange = onScreenChange,
            modifier = modifier
        )
    } else {
        // 底部导航栏 - 手机模式，支持自动隐藏
        AnimatedBottomNavigationBar(
            currentScreen = currentScreen,
            visibleItems = visibleItems,
            onScreenChange = onScreenChange,
            modifier = modifier,
            isVisible = isVisible
        )
    }
}

/**
 * 侧边导航栏 - 用于大屏设备
 */
@Composable
private fun NavigationRailBar(
    currentScreen: String,
    visibleItems: List<NavigationItem>,
    onScreenChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationRail(
        modifier = modifier
            .fillMaxHeight()
    ) {
        Spacer(Modifier.height(16.dp))
        
        visibleItems.forEach { item ->
            NavigationRailItem(
                icon = { Icon(item.getIcon(), contentDescription = item.title) },
                label = { Text(item.title) },
                selected = currentScreen == item.id,
                onClick = {
                    onScreenChange(item.id)
                }
            )
        }
    }
}

/**
 * 带动画的底部导航栏 - 支持滚动时自动隐藏
 * 参考LibChecker的实现：使用offset让整个导航栏滑出屏幕
 * 
 * LibChecker使用HideBottomViewOnScrollBehavior，内部动画参数：
 * - 时长: 225ms (ENTER_ANIMATION_DURATION / EXIT_ANIMATION_DURATION)
 * - 插值器: FastOutSlowInInterpolator (Material标准缓动)
 */
@Composable
private fun AnimatedBottomNavigationBar(
    currentScreen: String,
    visibleItems: List<NavigationItem>,
    onScreenChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true
) {
    // 使用Material风格的tween动画，模拟LibChecker的HideBottomViewOnScrollBehavior
    // LinearOutSlowInEasing = 缓出效果，更有缓冲感
    val offsetY by animateDpAsState(
        targetValue = if (isVisible) 0.dp else 100.dp,
        animationSpec = tween(
            durationMillis = 275, // 加长动画时长，增加缓冲感
            easing = LinearOutSlowInEasing // 缓出效果，从匀速到慢速
        ),
        label = "navBarOffset"
    )
    
    NavigationBar(
        modifier = modifier.offset(y = offsetY)
    ) {
        visibleItems.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.getIcon(), contentDescription = item.title) },
                label = { Text(item.title) },
                selected = currentScreen == item.id,
                onClick = {
                    onScreenChange(item.id)
                }
            )
        }
    }
}

/**
 * 带渐变效果的底部导航栏
 */
@Composable
fun GradientBottomNavigationBar(
    currentScreen: String,
    visibleItems: List<NavigationItem>,
    onScreenChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true
) {
    // 使用Material风格的tween动画
    val offsetY by animateDpAsState(
        targetValue = if (isVisible) 0.dp else 100.dp,
        animationSpec = tween(
            durationMillis = 400,
            easing = LinearOutSlowInEasing
        ),
        label = "navBarOffset"
    )
    
    Surface(
        modifier = modifier.offset(y = offsetY),
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        NavigationBar {
            visibleItems.forEach { item ->
                NavigationBarItem(
                    icon = { Icon(item.getIcon(), contentDescription = item.title) },
                    label = { Text(item.title) },
                    selected = currentScreen == item.id,
                    onClick = {
                        onScreenChange(item.id)
                    }
                )
            }
        }
    }
}
