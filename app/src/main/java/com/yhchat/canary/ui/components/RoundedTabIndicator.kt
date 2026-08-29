package com.yhchat.canary.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TabIndicatorScope
import androidx.compose.material3.TabPosition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val DefaultIndicatorHeight = 3.dp

@Composable
fun TabIndicatorScope.RoundedCornerTabIndicator(
    index: Int,
    indicatorColor: Color = MaterialTheme.colorScheme.primary,
    indicatorHeight: Dp = DefaultIndicatorHeight
) {
    var leftAnimatable by remember { mutableStateOf<Animatable<Dp, AnimationVector1D>?>(null) }
    var widthAnimatable by remember { mutableStateOf<Animatable<Dp, AnimationVector1D>?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Box(
        Modifier
            .tabIndicatorLayout { measurable: Measurable, constraints: Constraints, tabPositions: List<TabPosition> ->
                if (tabPositions.isEmpty()) {
                    val placeable = measurable.measure(Constraints.fixed(0, 0))
                    return@tabIndicatorLayout layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(0, constraints.maxHeight)
                    }
                }

                val safeIndex = index.coerceIn(0, tabPositions.lastIndex)
                val tabPosition = tabPositions[safeIndex]

                val targetWidth = tabPosition.contentWidth.coerceAtLeast(24.dp)
                val targetLeft = tabPosition.left + (tabPosition.width - targetWidth) / 2

                val leftAnim = leftAnimatable
                    ?: Animatable(targetLeft, Dp.VectorConverter).also { leftAnimatable = it }
                val widthAnim = widthAnimatable
                    ?: Animatable(targetWidth, Dp.VectorConverter).also { widthAnimatable = it }

                val animSpec = spring<Dp>(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )

                if (leftAnim.targetValue != targetLeft) {
                    coroutineScope.launch {
                        leftAnim.animateTo(targetLeft, animSpec)
                    }
                }

                if (widthAnim.targetValue != targetWidth) {
                    coroutineScope.launch {
                        widthAnim.animateTo(targetWidth, animSpec)
                    }
                }

                val currentLeft = leftAnim.value.roundToPx()
                val currentWidth = widthAnim.value.roundToPx().coerceAtLeast(0)
                val indicatorHeightPx = indicatorHeight.roundToPx()

                val placeable = measurable.measure(
                    Constraints.fixed(
                        width = currentWidth,
                        height = indicatorHeightPx
                    )
                )

                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(
                        x = currentLeft,
                        y = constraints.maxHeight - indicatorHeightPx
                    )
                }
            }
            .fillMaxSize()
            .drawWithContent {
                val path = Path().apply {
                    val cornerRadius = CornerRadius(size.height, size.height)
                    addRoundRect(
                        RoundRect(
                            rect = Rect(offset = Offset.Zero, size = size),
                            topLeft = cornerRadius,
                            topRight = cornerRadius,
                            bottomLeft = CornerRadius.Zero,
                            bottomRight = CornerRadius.Zero
                        )
                    )
                }
                drawPath(path, indicatorColor)
            }
    )
}
