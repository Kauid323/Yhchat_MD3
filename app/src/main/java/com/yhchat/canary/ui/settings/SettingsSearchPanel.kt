package com.yhchat.canary.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

/**
 * 设置搜索面板。
 *
 * 用 [Dialog] 而不是塞进 Scaffold 里：搜索是覆盖整页的临时状态，
 * 走 Dialog 有自己的窗口，不用改动设置页原有的布局层级，返回键和点外部也能关。
 *
 * `usePlatformDefaultWidth = false` 让它占满整屏，而不是 Material 默认那种
 * 四周留白的卡片 —— 搜索要能显示足够多的结果行。
 */
@Composable
internal fun SettingsSearchPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onOpenEntry: (SettingsEntry) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val view = LocalView.current

    // 面板底色，也是状态栏那块露出来的颜色
    val panelColor = MaterialTheme.colorScheme.surfaceContainer
    // 底色偏亮时状态栏图标用深色，偏暗时用浅色。
    // 用 luminance 判断而不是 isSystemInDarkTheme —— 应用有自己的主题偏好，
    // 系统深色但应用选了浅色时，按系统判断会把图标刷成白的、浮在白底上。
    val lightStatusBarIcons = panelColor.luminance() > 0.5f

    // 把对话框自己那个窗口的状态栏/导航栏设成透明，并把图标色按主题反过来。
    // 只设 decorFitsSystemWindows = false 时窗口是铺开了，但窗口的状态栏底色
    // 还是系统默认，深色主题下就压着一条灰。
    LaunchedEffect(lightStatusBarIcons) {
        val dialogWindow = (view.parent as? DialogWindowProvider)?.window ?: return@LaunchedEffect
        dialogWindow.statusBarColor = android.graphics.Color.TRANSPARENT
        dialogWindow.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.getInsetsController(dialogWindow, view).apply {
            isAppearanceLightStatusBars = lightStatusBarIcons
            isAppearanceLightNavigationBars = lightStatusBarIcons
        }
    }

    // 只在 query 变化时重算，避免每帧都跑一遍匹配
    val hits = remember(query) { SettingsSearch.search(query) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            // 窗口铺到状态栏下面。不这么设的话，Dialog 的窗口不含状态栏区域，
            // 那里露出的是系统默认灰，而不是下面这层 Surface 的底色 ——
            // 看起来就是「搜索一打开，状态栏变灰」。
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = panelColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                SearchFieldRow(
                    query = query,
                    onQueryChange = onQueryChange,
                    onDismiss = onDismiss,
                    focusRequester = focusRequester
                )

                // 进来就聚焦并弹键盘。搜索页让人再点一次输入框是多余的
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                    keyboard?.show()
                }

                HorizontalDivider()

                when {
                    query.isBlank() -> SearchHintState("输入关键词搜索设置")
                    hits.isEmpty() -> SearchHintState("没有找到「$query」相关的设置")
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        // key 用 title+section 组合：二级页和首页可能有同名条目
                        items(hits, key = { it.entry.title + it.entry.section }) { hit ->
                            SettingsSearchResultRow(
                                hit = hit,
                                onClick = { onOpenEntry(hit.entry) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 顶部的返回按钮 + 输入框。 */
@Composable
private fun SearchFieldRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    focusRequester: FocusRequester
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "退出搜索"
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            placeholder = { Text("搜索设置项") },
            singleLine = true,
            shape = CircleShape,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "清空"
                        )
                    }
                }
            }
        )
    }
}

/** 搜索结果的空态 / 提示文字。 */
@Composable
private fun SearchHintState(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 一条搜索结果。
 *
 * 副标题区分条目归属，让用户知道点下去会发生什么：
 *   - 二级页里的条目 → 显示页名，右侧箭头向右，表示会打开新页面
 *   - 首页上的条目 → 显示「分组 · 设置首页」，箭头向下，表示在首页里滚过去
 *
 * 关键词命中时第一行右侧挂一个「关键词」小标签。用户搜「音量」却看到「语速」时，
 * 有这个标签才不会被当成搜错了。
 */
@Composable
private fun SettingsSearchResultRow(
    hit: SettingsSearchHit,
    onClick: () -> Unit
) {
    val entry = hit.entry
    val inSubPage = entry.page != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (inSubPage) entry.section else "${entry.section} · 设置首页",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (hit.matchedOn == "keyword") {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = "关键词",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }

        Icon(
            imageVector = if (inSubPage) {
                Icons.AutoMirrored.Filled.ArrowForward
            } else {
                Icons.Default.KeyboardArrowDown
            },
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}