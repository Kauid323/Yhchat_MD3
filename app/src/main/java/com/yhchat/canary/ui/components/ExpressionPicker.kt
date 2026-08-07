package com.yhchat.canary.ui.components

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import com.yhchat.canary.ui.adaptive.YhIcon as Icon
import com.yhchat.canary.ui.adaptive.YhText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.yhchat.canary.data.di.RepositoryFactory
import com.yhchat.canary.data.model.Expression
import com.yhchat.canary.data.model.StickerItem
import com.yhchat.canary.data.model.StickerPack
import com.yhchat.canary.data.repository.ExpressionRepository
import com.yhchat.canary.data.repository.StickerRepository
import com.yhchat.canary.ui.adaptive.YhCircularProgressIndicator
import com.yhchat.canary.ui.adaptive.YhClickableSurface
import com.yhchat.canary.ui.adaptive.YhIconButton
import com.yhchat.canary.ui.adaptive.YhSurface
import com.yhchat.canary.ui.adaptive.YhTextButton
import com.yhchat.canary.ui.sticker.StickerPackManagerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 表情选择器
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpressionPicker(
    onExpressionClick: (Expression) -> Unit,  // 点击表情后的回调（传递完整的Expression对象）
    onStickerClick: (StickerItem) -> Unit = {},  // 点击表情包贴纸的回调
    onDefaultExpressionClick: (String) -> Unit = {}, // 点击默认表情的回调 (传递表情名称，如 [.书呆])
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel = remember { ExpressionPickerViewModel() }
    val autoDismissAfterPick by rememberBooleanPreference(
        "display_settings",
        "auto_dismiss_expression_picker",
        true
    )
    
    LaunchedEffect(Unit) {
        viewModel.init(context)
        viewModel.loadExpressions()
        viewModel.loadStickerPacks()
        viewModel.loadDefaultExpressions(context)
    }
    
    val uiState by viewModel.uiState.collectAsState()
    val tabs = remember(uiState.stickerPacks) {
        buildList<ExpressionPickerTab> {
            add(ExpressionPickerTab.DefaultExpressions)
            add(ExpressionPickerTab.FavoriteExpressions)
            uiState.stickerPacks.forEach { stickerPack ->
                add(ExpressionPickerTab.StickerPackTab(stickerPack))
            }
        }
    }
    val totalTabs = tabs.size
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { totalTabs }
    )
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    
    YhSurface(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Tab 切换
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                YhIconButton(
                    onClick = { StickerPackManagerActivity.start(context) }
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "表情包设置"
                    )
                }

                ExpressionPickerTabRow(
                    tabs = tabs,
                    selectedTabIndex = pagerState.currentPage,
                    onTabSelected = { index -> 
                        coroutineScope.launch { 
                            pagerState.animateScrollToPage(index) 
                        } 
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            
            // 内容区域
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        YhCircularProgressIndicator()
                    }
                }
                
                uiState.error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = uiState.error ?: "加载失败",
                                color = MaterialTheme.colorScheme.error
                            )
                            YhTextButton(onClick = { 
                                viewModel.loadExpressions()
                                viewModel.loadStickerPacks()
                                viewModel.loadDefaultExpressions(context)
                            }) {
                                Text("重试")
                            }
                        }
                    }
                }
                
                else -> {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        when {
                            page == 0 -> {
                                // 默认表情
                                if (uiState.defaultExpressions.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "暂无默认表情",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(7), // 默认表情显示更小，一行7个
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        items(uiState.defaultExpressions) { fileName ->
                                            // fileName example: [.书呆]​​.svg
                                            val name = fileName.substringBeforeLast(".svg").trim() // [.书呆]

                                            AsyncImage(
                                                model = ImageRequest.Builder(context)
                                                    .data("file:///android_asset/fengtwemoji/${android.net.Uri.encode(fileName)}")
                                                    .decoderFactory(SvgDecoder.Factory())
                                                    .build(),
                                                contentDescription = name,
                                                modifier = Modifier
                                                    .aspectRatio(1f)
                                                    .clickable {
                                                        onDefaultExpressionClick(name)
                                                        if (autoDismissAfterPick) {
                                                            onDismiss()
                                                        }
                                                    },
                                                contentScale = ContentScale.Fit
                                            )
                                        }
                                    }
                                }
                            }

                            page == 1 -> {
                                // 我的表情
                                if (uiState.expressions.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "暂无收藏的表情\n长按聊天中的图片添加到表情收藏",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(4),
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(uiState.expressions) { expression ->
                                            AsyncImage(
                                                model = expression.getFullUrl(),
                                                contentDescription = "表情",
                                                modifier = Modifier
                                                    .size(80.dp)
                                                    .clickable {
                                                        onExpressionClick(expression)  // 传递完整的Expression对象
                                                        if (autoDismissAfterPick) {
                                                            onDismiss()
                                                        }
                                                    },
                                                contentScale = ContentScale.Fit
                                            )
                                        }
                                    }
                                }
                            }

                            else -> {
                                // 表情包内容
                                val selectedStickerPack = uiState.stickerPacks.getOrNull(page - 2)
                                if (selectedStickerPack != null) {
                                    if (selectedStickerPack.stickerItems.isEmpty()) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "该表情包暂无内容",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    } else {
                                        LazyVerticalGrid(
                                            columns = GridCells.Fixed(4),
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding = PaddingValues(8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            items(selectedStickerPack.stickerItems) { stickerItem ->
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    modifier = Modifier
                                                        .clickable {
                                                            onStickerClick(stickerItem)
                                                            if (autoDismissAfterPick) {
                                                                onDismiss()
                                                            }
                                                        }
                                                ) {
                                                    AsyncImage(
                                                        model = ImageUtils.createStickerImageRequest(
                                                            context = context,
                                                            url = stickerItem.getFullUrl()
                                                        ),
                                                        contentDescription = stickerItem.name,
                                                        modifier = Modifier
                                                            .size(64.dp),
                                                        contentScale = ContentScale.Fit
                                                    )

                                                    Spacer(modifier = Modifier.height(4.dp))

                                                    Text(
                                                        text = stickerItem.name,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        textAlign = TextAlign.Center
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "表情包加载失败",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private sealed interface ExpressionPickerTab {
    data object DefaultExpressions : ExpressionPickerTab
    data object FavoriteExpressions : ExpressionPickerTab
    data class StickerPackTab(val stickerPack: StickerPack) : ExpressionPickerTab
}

@Composable
private fun ExpressionPickerTabRow(
    tabs: List<ExpressionPickerTab>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    
    LaunchedEffect(selectedTabIndex) {
        if (selectedTabIndex in tabs.indices) {
            // 简单的居中计算，或者至少滚动到该项
            listState.animateScrollToItem(selectedTabIndex, -100) // 稍微偏移以尽量居中
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(tabs) { index, tab ->
            ExpressionPickerTabItem(
                tab = tab,
                selected = selectedTabIndex == index,
                onClick = { onTabSelected(index) }
            )
        }
    }
}

@Composable
private fun ExpressionPickerTabItem(
    tab: ExpressionPickerTab,
    selected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    YhClickableSurface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier.height(40.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            when (tab) {
                ExpressionPickerTab.DefaultExpressions -> {
                    Text(
                        text = "默认",
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColor,
                        maxLines = 1
                    )
                }

                ExpressionPickerTab.FavoriteExpressions -> {
                    Text(
                        text = "收藏",
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColor,
                        maxLines = 1
                    )
                }

                is ExpressionPickerTab.StickerPackTab -> {
                    val previewSticker = tab.stickerPack.stickerItems.firstOrNull()
                    if (previewSticker != null) {
                        AsyncImage(
                            model = ImageUtils.createStickerImageRequest(
                                context = context,
                                url = previewSticker.getFullUrl()
                            ),
                            contentDescription = tab.stickerPack.name,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Text(
                            text = tab.stickerPack.name.take(1).ifBlank { "包" },
                            style = MaterialTheme.typography.labelLarge,
                            color = contentColor,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * 表情选择器ViewModel
 */
class ExpressionPickerViewModel : ViewModel() {
    private lateinit var expressionRepository: ExpressionRepository
    private lateinit var stickerRepository: StickerRepository
    
    private val _uiState = MutableStateFlow(ExpressionPickerUiState())
    val uiState: StateFlow<ExpressionPickerUiState> = _uiState.asStateFlow()
    
    fun init(context: Context) {
        expressionRepository = RepositoryFactory.getExpressionRepository(context)
        stickerRepository = RepositoryFactory.getStickerRepository(context)
    }
    
    fun loadExpressions() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            expressionRepository.getExpressionList().fold(
                onSuccess = { expressions ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        expressions = expressions
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message
                    )
                }
            )
        }
    }
    
    fun loadStickerPacks() {
        viewModelScope.launch {
            stickerRepository.getStickerPackList().fold(
                onSuccess = { stickerPacks ->
                    _uiState.value = _uiState.value.copy(
                        stickerPacks = stickerPacks
                    )
                },
                onFailure = { error ->
                    // 表情包加载失败不影响个人表情的显示
                    android.util.Log.e("ExpressionPicker", "加载表情包失败: ${error.message}")
                }
            )
        }
    }

    fun loadDefaultExpressions(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val assets = context.assets.list("fengtwemoji")
                if (assets != null) {
                    val sortedAssets = assets.filter { it.endsWith(".svg") }.sorted()
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            defaultExpressions = sortedAssets
                        )
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ExpressionPicker", "加载默认表情失败", e)
            }
        }
    }
}

data class ExpressionPickerUiState(
    val isLoading: Boolean = false,
    val expressions: List<Expression> = emptyList(),
    val stickerPacks: List<StickerPack> = emptyList(),
    val defaultExpressions: List<String> = emptyList(),
    val error: String? = null
)
