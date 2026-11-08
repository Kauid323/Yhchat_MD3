package com.yhchat.canary.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * 设置页里「当前要高亮的条目标题」。
 *
 * 搜索结果跳过来时，目标条目闪一下高亮，不然滚过去了也看不出落到哪儿。
 *
 * 为什么用 CompositionLocal 而不是给每个 `SettingsItemCell` 加参数：
 * 设置页的条目是几十个 lambda，逐处加参数要改所有调用点，且容易漏。
 * 这里只让两个基础组件（`SettingsItemCell`、`SettingsSwitchItem`）读它，
 * 调用点一个字都不用动。
 *
 * 值为 null 表示没有要高亮的项。
 */
internal val LocalSettingsHighlightTitle =
    staticCompositionLocalOf<String?> { null }

/**
 * 高亮的持续时间。
 *
 * 太长会让人以为那行是「选中」状态；太短又看不清落在哪。1.2 秒够扫一眼。
 */
internal const val SettingsHighlightDurationMillis = 1200L

/**
 * 标题命中 [LocalSettingsHighlightTitle] 时，给这一行铺一层主题色的淡底。
 *
 * 用 `animateFloatAsState` 而不是直接画静态背景：淡入淡出才看得出是「刚跳过来的」，
 * 而不是「这行本来就有底色」。alpha 从 0 到目标值再回 0 需要一个驱动，
 * 这里靠组合时的一次性动画完成 —— 高亮标题在跳转后被清掉，
 * 所以动画只跑一遍。
 */
@Composable
internal fun Modifier.settingsSearchHighlight(title: String): Modifier {
    val highlightedTitle = LocalSettingsHighlightTitle.current
    val active = highlightedTitle != null && highlightedTitle == title

    val alpha by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "settingsSearchHighlightAlpha"
    )

    if (alpha <= 0.001f) return this

    return this
        .clip(RoundedCornerShape(4.dp))
        .background(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f * alpha)
        )
}