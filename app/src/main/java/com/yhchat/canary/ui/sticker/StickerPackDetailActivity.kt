package com.yhchat.canary.ui.sticker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yhchat.canary.data.model.StickerItem
import com.yhchat.canary.data.model.StickerPackDetailData
import com.yhchat.canary.ui.adaptive.YhAlertDialog
import com.yhchat.canary.ui.adaptive.YhBottomSheet
import com.yhchat.canary.ui.adaptive.YhButton
import com.yhchat.canary.ui.adaptive.YhCard
import com.yhchat.canary.ui.adaptive.YhCircularProgressIndicator
import com.yhchat.canary.ui.adaptive.YhDropdownMenu
import com.yhchat.canary.ui.adaptive.YhDropdownMenuItem
import com.yhchat.canary.ui.adaptive.YhIcon as Icon
import com.yhchat.canary.ui.adaptive.YhIconButton
import com.yhchat.canary.ui.adaptive.YhOutlinedTextField
import com.yhchat.canary.ui.adaptive.YhScaffold
import com.yhchat.canary.ui.adaptive.YhText as Text
import com.yhchat.canary.ui.adaptive.YhTextButton
import com.yhchat.canary.ui.adaptive.YhTopBar
import com.yhchat.canary.ui.adaptive.yhTopBarNestedScroll
import com.yhchat.canary.ui.components.ImageViewer
import com.yhchat.canary.ui.theme.YhchatCanaryTheme
import com.yhchat.canary.ui.user.UserDetailActivity
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class StickerPackDetailActivity : ComponentActivity() {
    private val viewModel: StickerPackDetailViewModel by viewModels()

    companion object {
        private const val EXTRA_STICKER_PACK_ID = "sticker_pack_id"

        fun start(context: Context, stickerPackId: String) {
            val intent = Intent(context, StickerPackDetailActivity::class.java)
            intent.putExtra(EXTRA_STICKER_PACK_ID, stickerPackId)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.yhchat.canary.ui.base.SystemBarUtils.setupTransparentSystemBars(this)

        val stickerPackId = intent.getStringExtra(EXTRA_STICKER_PACK_ID) ?: ""

        setContent {
            YhchatCanaryTheme {
                com.yhchat.canary.ui.base.SystemBarUtils.SetSystemNavigationBarColor(this@StickerPackDetailActivity)
                StickerPackDetailScreen(
                    stickerPackId = stickerPackId,
                    viewModel = viewModel,
                    onBackClick = { finish() }
                )
            }
        }
    }
}

@Composable
fun StickerPackDetailScreen(
    stickerPackId: String,
    viewModel: StickerPackDetailViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showMoreMenu by remember { mutableStateOf(false) }
    var showRenamePackDialog by remember { mutableStateOf(false) }
    var renamePackText by remember { mutableStateOf("") }
    var showDeletePackDialog by remember { mutableStateOf(false) }
    var showRenameStickerDialog by remember { mutableStateOf(false) }
    var renameStickerText by remember { mutableStateOf("") }
    var showDeleteStickerDialog by remember { mutableStateOf(false) }
    var isOperating by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            Toast.makeText(context, "正在上传并添加表情...", Toast.LENGTH_SHORT).show()
            viewModel.uploadAndAddSticker(
                context = context,
                imageUri = uri,
                stickerPackId = stickerPackId,
                onSuccess = {
                    Toast.makeText(context, "添加表情成功", Toast.LENGTH_SHORT).show()
                },
                onFailure = { error ->
                    Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    BackHandler(enabled = uiState.isManaging) {
        viewModel.toggleManageMode(false)
    }

    LaunchedEffect(stickerPackId) {
        viewModel.loadStickerPackDetail(stickerPackId)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "JiggleTransition")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = -2.8f,
        targetValue = 2.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 110, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "JiggleRotation"
    )
    val offsetX by infiniteTransition.animateFloat(
        initialValue = -1.2f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 95, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "JiggleTranslation"
    )

    YhScaffold(
        topBar = {
            YhTopBar(
                title = if (uiState.isManaging) "管理表情包" else "表情包详情",
                large = false,
                navigationIcon = {
                    YhIconButton(
                        onClick = {
                            if (uiState.isManaging) {
                                viewModel.toggleManageMode(false)
                            } else {
                                onBackClick()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    if (uiState.stickerPack != null) {
                        if (uiState.isManaging) {
                            YhTextButton(onClick = { viewModel.toggleManageMode(false) }) {
                                Text("完成", fontWeight = FontWeight.Bold)
                            }
                        } else if (uiState.isOwner) {
                            Box {
                                YhIconButton(onClick = { showMoreMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "更多操作"
                                    )
                                }
                                YhDropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false }
                                ) {
                                    YhDropdownMenuItem(
                                        text = { Text("重命名表情包") },
                                        onClick = {
                                            showMoreMenu = false
                                            renamePackText = uiState.stickerPack?.stickerPack?.name.orEmpty()
                                            showRenamePackDialog = true
                                        }
                                    )
                                    YhDropdownMenuItem(
                                        text = { Text("删除表情包") },
                                        onClick = {
                                            showMoreMenu = false
                                            showDeletePackDialog = true
                                        }
                                    )
                                    YhDropdownMenuItem(
                                        text = { Text("添加表情") },
                                        onClick = {
                                            showMoreMenu = false
                                            imagePickerLauncher.launch("image/*")
                                        }
                                    )
                                    YhDropdownMenuItem(
                                        text = { Text("管理") },
                                        onClick = {
                                            showMoreMenu = false
                                            viewModel.toggleManageMode(true)
                                        }
                                    )
                                }
                            }
                        } else {
                            YhIconButton(
                                onClick = {
                                    viewModel.addStickerPackToFavorites(
                                        stickerPackId = stickerPackId,
                                        onSuccess = {
                                            Toast.makeText(context, "已添加到表情包", Toast.LENGTH_SHORT).show()
                                        },
                                        onFailure = { error ->
                                            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "添加表情包"
                                )
                            }
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
                    YhCircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.error != null && uiState.stickerPack == null -> {
                    Text(
                        text = uiState.error ?: "加载失败",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                uiState.stickerPack != null -> {
                    val stickerPackData = uiState.stickerPack!!
                    StickerPackDetailContent(
                        stickerPackData = stickerPackData,
                        isManaging = uiState.isManaging,
                        rotationAngle = rotationAngle,
                        offsetX = offsetX,
                        onImageClick = { imageIndex ->
                            viewModel.openImageViewer(imageIndex)
                        },
                        onStickerManageClick = { sticker ->
                            viewModel.selectStickerForAction(sticker)
                        },
                        onCreatorClick = { userId ->
                            UserDetailActivity.start(context = context, userId = userId)
                        }
                    )
                }
            }

            if (uiState.isUploading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    YhCard(
                        cornerRadius = 16.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            YhCircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Text("正在上传表情...")
                        }
                    }
                }
            }
        }
    }

    if (uiState.showStickerActionSheet && uiState.selectedStickerForAction != null) {
        val selectedSticker = uiState.selectedStickerForAction!!
        YhBottomSheet(
            show = true,
            title = "表情操作",
            onDismissRequest = { viewModel.dismissStickerActionSheet() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AsyncImage(
                    model = selectedSticker.toStickerImageUrl(),
                    contentDescription = selectedSticker.name,
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit
                )

                Text(
                    text = selectedSticker.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    YhButton(
                        onClick = {
                            renameStickerText = selectedSticker.name
                            showRenameStickerDialog = true
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("重命名")
                    }

                    YhButton(
                        onClick = {
                            showDeleteStickerDialog = true
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    if (showRenamePackDialog) {
        YhAlertDialog(
            onDismissRequest = { if (!isOperating) showRenamePackDialog = false },
            title = { Text("重命名表情包", fontWeight = FontWeight.Bold) },
            text = {
                YhOutlinedTextField(
                    value = renamePackText,
                    onValueChange = { renamePackText = it },
                    placeholder = { Text("输入新表情包名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isOperating
                )
            },
            confirmButton = {
                YhTextButton(
                    onClick = {
                        val newName = renamePackText.trim()
                        if (newName.isEmpty()) {
                            Toast.makeText(context, "表情包名称不能为空", Toast.LENGTH_SHORT).show()
                            return@YhTextButton
                        }
                        val packId = uiState.stickerPack?.stickerPack?.id ?: return@YhTextButton
                        isOperating = true
                        viewModel.renameStickerPack(
                            packId = packId,
                            newName = newName,
                            onSuccess = {
                                isOperating = false
                                showRenamePackDialog = false
                                Toast.makeText(context, "重命名成功", Toast.LENGTH_SHORT).show()
                            },
                            onFailure = { error ->
                                isOperating = false
                                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    enabled = !isOperating && renamePackText.isNotBlank()
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                YhTextButton(
                    onClick = { showRenamePackDialog = false },
                    enabled = !isOperating
                ) {
                    Text("取消")
                }
            }
        )
    }

    if (showDeletePackDialog) {
        YhAlertDialog(
            onDismissRequest = { if (!isOperating) showDeletePackDialog = false },
            title = { Text("删除表情包", fontWeight = FontWeight.Bold) },
            text = { Text("确定要删除此表情包吗？此操作无法撤销。") },
            confirmButton = {
                YhTextButton(
                    onClick = {
                        val packId = uiState.stickerPack?.stickerPack?.id ?: return@YhTextButton
                        isOperating = true
                        viewModel.deleteStickerPack(
                            packId = packId,
                            onSuccess = {
                                isOperating = false
                                showDeletePackDialog = false
                                Toast.makeText(context, "表情包已删除", Toast.LENGTH_SHORT).show()
                                (context as? Activity)?.finish()
                            },
                            onFailure = { error ->
                                isOperating = false
                                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    enabled = !isOperating
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                YhTextButton(
                    onClick = { showDeletePackDialog = false },
                    enabled = !isOperating
                ) {
                    Text("取消")
                }
            }
        )
    }

    if (showRenameStickerDialog && uiState.selectedStickerForAction != null) {
        val targetSticker = uiState.selectedStickerForAction!!
        YhAlertDialog(
            onDismissRequest = { if (!isOperating) showRenameStickerDialog = false },
            title = { Text("重命名表情", fontWeight = FontWeight.Bold) },
            text = {
                YhOutlinedTextField(
                    value = renameStickerText,
                    onValueChange = { renameStickerText = it },
                    placeholder = { Text("输入表情名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isOperating
                )
            },
            confirmButton = {
                YhTextButton(
                    onClick = {
                        val newName = renameStickerText.trim()
                        if (newName.isEmpty()) {
                            Toast.makeText(context, "表情名称不能为空", Toast.LENGTH_SHORT).show()
                            return@YhTextButton
                        }
                        isOperating = true
                        viewModel.renameSticker(
                            stickerId = targetSticker.id,
                            newName = newName,
                            stickerPackId = stickerPackId,
                            onSuccess = {
                                isOperating = false
                                showRenameStickerDialog = false
                                Toast.makeText(context, "重命名成功", Toast.LENGTH_SHORT).show()
                            },
                            onFailure = { error ->
                                isOperating = false
                                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    enabled = !isOperating && renameStickerText.isNotBlank()
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                YhTextButton(
                    onClick = { showRenameStickerDialog = false },
                    enabled = !isOperating
                ) {
                    Text("取消")
                }
            }
        )
    }

    if (showDeleteStickerDialog && uiState.selectedStickerForAction != null) {
        val targetSticker = uiState.selectedStickerForAction!!
        YhAlertDialog(
            onDismissRequest = { if (!isOperating) showDeleteStickerDialog = false },
            title = { Text("删除表情", fontWeight = FontWeight.Bold) },
            text = { Text("确定要从表情包中删除表情「${targetSticker.name}」吗？") },
            confirmButton = {
                YhTextButton(
                    onClick = {
                        isOperating = true
                        viewModel.removeSticker(
                            stickerId = targetSticker.id,
                            stickerPackId = stickerPackId,
                            onSuccess = {
                                isOperating = false
                                showDeleteStickerDialog = false
                                Toast.makeText(context, "表情已删除", Toast.LENGTH_SHORT).show()
                            },
                            onFailure = { error ->
                                isOperating = false
                                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    enabled = !isOperating
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                YhTextButton(
                    onClick = { showDeleteStickerDialog = false },
                    enabled = !isOperating
                ) {
                    Text("取消")
                }
            }
        )
    }

    val previewImageUrls = remember(uiState.stickerPack) {
        uiState.stickerPack
            ?.stickerPack
            ?.stickerItems
            ?.map { sticker -> sticker.toStickerImageUrl() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
    }
    if (uiState.showImageViewer && previewImageUrls.isNotEmpty()) {
        ImageViewer(
            imageUrls = previewImageUrls,
            initialIndex = uiState.currentImageIndex.coerceIn(0, previewImageUrls.lastIndex),
            onDismiss = viewModel::dismissImageViewer
        )
    }
}

@Composable
fun StickerPackDetailContent(
    stickerPackData: StickerPackDetailData,
    isManaging: Boolean = false,
    rotationAngle: Float = 0f,
    offsetX: Float = 0f,
    onImageClick: (Int) -> Unit = {},
    onStickerManageClick: (StickerItem) -> Unit = {},
    onCreatorClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val stickerPack = stickerPackData.stickerPack
    val creator = stickerPackData.user
    val stickerItems = stickerPack.stickerItems.orEmpty()

    LazyVerticalGrid(
        modifier = Modifier
            .fillMaxSize()
            .yhTopBarNestedScroll()
            .padding(horizontal = 16.dp),
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            YhCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = stickerPack.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = Int.MAX_VALUE,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    clipboardManager.setText(buildAnnotatedString { append(stickerPack.name) })
                                    Toast.makeText(context, "已复制表情包名称", Toast.LENGTH_SHORT).show()
                                }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "ID: ${stickerPack.id}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable {
                                clipboardManager.setText(buildAnnotatedString { append(stickerPack.id.toString()) })
                                Toast.makeText(context, "已复制表情包ID", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (creator != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onCreatorClick(creator.userId) }
                        ) {
                            AsyncImage(
                                model = creator.avatarUrl,
                                contentDescription = "创建者头像",
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(16.dp))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = creator.nickname,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "创建者",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "${stickerItems.size}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "表情数量",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column {
                            Text(
                                text = "${stickerPack.userCount}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "使用人数",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column {
                            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            Text(
                                text = dateFormat.format(Date(stickerPack.createTime * 1000)),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "创建时间",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "表情列表",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (isManaging) {
                    Text(
                        text = "点击表情进行编辑或删除",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        items(stickerItems.size) { index ->
            val sticker = stickerItems[index]
            StickerItemView(
                sticker = sticker,
                index = index,
                isManaging = isManaging,
                rotationAngle = rotationAngle,
                offsetX = offsetX,
                onClick = {
                    if (isManaging) {
                        onStickerManageClick(sticker)
                    } else {
                        onImageClick(index)
                    }
                }
            )
        }
    }
}

@Composable
fun StickerItemView(
    sticker: StickerItem,
    index: Int = 0,
    isManaging: Boolean = false,
    rotationAngle: Float = 0f,
    offsetX: Float = 0f,
    onClick: () -> Unit = {}
) {
    val imageUrl = sticker.toStickerImageUrl()

    val jiggleModifier = if (isManaging) {
        Modifier.graphicsLayer {
            this.rotationZ = if (index % 2 == 0) rotationAngle else -rotationAngle
            this.translationX = if (index % 2 == 0) offsetX else -offsetX
        }
    } else {
        Modifier
    }

    YhCard(
        modifier = Modifier
            .aspectRatio(0.8f)
            .then(jiggleModifier)
            .clickable(onClick = onClick),
        cornerRadius = 12.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = sticker.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Text(
                text = sticker.name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
            )
        }
    }
}

private fun StickerItem.toStickerImageUrl(): String {
    return if (url.startsWith("http")) {
        url
    } else {
        "https://chat-img.jwznb.com/$url"
    }
}

