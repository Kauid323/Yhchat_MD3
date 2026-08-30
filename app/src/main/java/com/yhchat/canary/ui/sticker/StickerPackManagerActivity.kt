package com.yhchat.canary.ui.sticker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.yhchat.canary.data.di.RepositoryFactory
import com.yhchat.canary.data.model.StickerPack
import com.yhchat.canary.ui.adaptive.YhAlertDialog
import com.yhchat.canary.ui.adaptive.YhCard
import com.yhchat.canary.ui.adaptive.YhCircularProgressIndicator
import com.yhchat.canary.ui.adaptive.YhDropdownMenu
import com.yhchat.canary.ui.adaptive.YhDropdownMenuItem
import com.yhchat.canary.ui.adaptive.YhIcon as Icon
import com.yhchat.canary.ui.adaptive.YhIconButton
import com.yhchat.canary.ui.adaptive.YhOutlinedTextField
import com.yhchat.canary.ui.adaptive.YhScaffold
import com.yhchat.canary.ui.adaptive.YhSurface
import com.yhchat.canary.ui.adaptive.YhTextButton
import com.yhchat.canary.ui.adaptive.YhText as Text
import com.yhchat.canary.ui.adaptive.YhTopBar
import com.yhchat.canary.ui.adaptive.yhTopBarNestedScroll
import com.yhchat.canary.ui.base.SystemBarUtils
import com.yhchat.canary.ui.theme.YhchatCanaryTheme
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.rememberReorderableLazyListState

class StickerPackManagerActivity : ComponentActivity() {
    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, StickerPackManagerActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SystemBarUtils.setupTransparentSystemBars(this)

        val repository = RepositoryFactory.getStickerRepository(this)

        setContent {
            YhchatCanaryTheme {
                SystemBarUtils.SetSystemNavigationBarColor(this@StickerPackManagerActivity)
                val managerViewModel: StickerPackManagerViewModel = viewModel(
                    factory = StickerPackManagerViewModel.factory(repository)
                )
                YhSurface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    StickerPackManagerScreen(
                        viewModel = managerViewModel,
                        onBackClick = { finish() }
                    )
                }
            }
        }
    }
}

@Composable
private fun StickerPackManagerScreen(
    viewModel: StickerPackManagerViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        viewModel.moveStickerPack(from.index, to.index)
    }

    var menuExpanded by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var createPackName by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadStickerPacks()
    }

    YhScaffold(
        topBar = {
            YhTopBar(
                title = "表情包设置",
                large = false,
                navigationIcon = {
                    YhIconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    Box {
                        YhIconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "更多操作"
                            )
                        }
                        YhDropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            YhDropdownMenuItem(
                                text = { Text("创建表情包") },
                                onClick = {
                                    menuExpanded = false
                                    createPackName = ""
                                    showCreateDialog = true
                                }
                            )
                            YhDropdownMenuItem(
                                text = { Text("导入表情包") },
                                onClick = {
                                    menuExpanded = false
                                    Toast.makeText(context, "导入表情包功能开发中", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    YhCircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                uiState.error != null && uiState.stickerPacks.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = uiState.error ?: "加载失败",
                            color = MaterialTheme.colorScheme.error
                        )
                        YhTextButton(onClick = { viewModel.loadStickerPacks() }) {
                            Text("重试")
                        }
                    }
                }

                else -> {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (uiState.error != null) {
                            Text(
                                text = uiState.error ?: "",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        if (uiState.isSavingOrder) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                YhCircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "正在保存排序",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp)
                                .yhTopBarNestedScroll(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(
                                items = uiState.stickerPacks,
                                key = { _, item -> item.id }
                            ) { index, stickerPack ->
                                ReorderableItem(
                                    state = reorderableState,
                                    key = stickerPack.id
                                ) { isDragging ->
                                    val elevation = animateDpAsState(
                                        targetValue = if (isDragging) 8.dp else 2.dp,
                                        label = "StickerPackCardElevation"
                                    ).value

                                    StickerPackManagerItem(
                                        stickerPack = stickerPack,
                                        index = index,
                                        canMoveUp = index > 0,
                                        canMoveDown = index < uiState.stickerPacks.lastIndex,
                                        elevation = elevation,
                                        onClick = {
                                            StickerPackDetailActivity.start(context, stickerPack.id.toString())
                                        },
                                        onReorderFinished = { viewModel.syncSortOrder() },
                                        onMoveUp = {
                                            viewModel.moveStickerPack(index, index - 1)
                                            viewModel.syncSortOrder()
                                        },
                                        onMoveDown = {
                                            viewModel.moveStickerPack(index, index + 1)
                                            viewModel.syncSortOrder()
                                        },
                                        onRemove = {
                                            viewModel.removeStickerPack(
                                                packId = stickerPack.id,
                                                onSuccess = {
                                                    Toast.makeText(context, "已移除", Toast.LENGTH_SHORT).show()
                                                },
                                                onFailure = { message ->
                                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        }
                                    )
                                }
                            }

                            item {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        YhAlertDialog(
            onDismissRequest = {
                if (!isCreating) {
                    showCreateDialog = false
                }
            },
            title = {
                Text(
                    text = "创建表情包",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "请输入表情包名称",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    YhOutlinedTextField(
                        value = createPackName,
                        onValueChange = { createPackName = it },
                        placeholder = { Text("表情包名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isCreating
                    )
                }
            },
            confirmButton = {
                YhTextButton(
                    onClick = {
                        val name = createPackName.trim()
                        if (name.isEmpty()) {
                            Toast.makeText(context, "表情包名称不能为空", Toast.LENGTH_SHORT).show()
                            return@YhTextButton
                        }
                        isCreating = true
                        viewModel.createStickerPack(
                            name = name,
                            onSuccess = { packId ->
                                isCreating = false
                                showCreateDialog = false
                                Toast.makeText(context, "创建成功", Toast.LENGTH_SHORT).show()
                                StickerPackDetailActivity.start(context, packId.toString())
                            },
                            onFailure = { errorMsg ->
                                isCreating = false
                                Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    enabled = !isCreating && createPackName.isNotBlank()
                ) {
                    if (isCreating) {
                        YhCircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("创建")
                    }
                }
            },
            dismissButton = {
                YhTextButton(
                    onClick = { showCreateDialog = false },
                    enabled = !isCreating
                ) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun ReorderableCollectionItemScope.StickerPackManagerItem(
    stickerPack: StickerPack,
    index: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    elevation: Dp,
    onClick: () -> Unit,
    onReorderFinished: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    val preview = stickerPack.stickerItems.firstOrNull()

    YhCard(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = elevation, shape = RoundedCornerShape(20.dp)),
        cornerRadius = 20.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "拖拽排序",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = with(this@StickerPackManagerItem) {
                        Modifier
                            .draggableHandle(
                                onDragStopped = onReorderFinished
                            )
                            .size(22.dp)
                    }
                )
                Text(
                    text = (index + 1).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            if (preview != null) {
                AsyncImage(
                    model = preview.getFullUrl(),
                    contentDescription = stickerPack.name,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text("表")
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stickerPack.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${stickerPack.stickerItems.size} 个表情",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                YhIconButton(
                    onClick = onMoveUp,
                    enabled = canMoveUp
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "上移"
                    )
                }
                YhIconButton(
                    onClick = onMoveDown,
                    enabled = canMoveDown
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "下移"
                    )
                }
            }

            YhIconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "移除"
                )
            }
        }
    }
}
