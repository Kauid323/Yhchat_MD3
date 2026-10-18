package com.yhchat.canary.ui.group

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yhchat.canary.data.api.GroupTag
import com.yhchat.canary.ui.theme.YhchatCanaryTheme
import dagger.hilt.android.AndroidEntryPoint
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.util.Locale

@AndroidEntryPoint
class GroupTagManagementActivity : ComponentActivity() {
    
    private val viewModel: GroupTagManagementViewModel by viewModels()
    
    companion object {
        private const val EXTRA_GROUP_ID = "group_id"
        private const val EXTRA_GROUP_NAME = "group_name"
        
        fun start(context: Context, groupId: String, groupName: String) {
            val intent = Intent(context, GroupTagManagementActivity::class.java).apply {
                putExtra(EXTRA_GROUP_ID, groupId)
                putExtra(EXTRA_GROUP_NAME, groupName)
            }
            context.startActivity(intent)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.yhchat.canary.ui.base.SystemBarUtils.setupTransparentSystemBars(this)
        
        val groupId = intent.getStringExtra(EXTRA_GROUP_ID) ?: run {
            finish()
            return
        }
        val groupName = intent.getStringExtra(EXTRA_GROUP_NAME) ?: "群聊"
        
        setContent {
            YhchatCanaryTheme {
                GroupTagManagementScreen(
                    groupId = groupId,
                    groupName = groupName,
                    viewModel = viewModel,
                    onBackClick = { finish() },
                    onTagClick = { tag ->
                        GroupTagDetailActivity.start(this, groupId, tag.id, tag.tag)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupTagManagementScreen(
    groupId: String,
    groupName: String,
    viewModel: GroupTagManagementViewModel,
    onBackClick: () -> Unit,
    onTagClick: (GroupTag) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        viewModel.moveTag(from.index, to.index)
    }
    
    LaunchedEffect(groupId) {
        viewModel.loadTags(groupId)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "标签管理",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    if (uiState.hasPendingSort) {
                        TextButton(
                            onClick = { viewModel.submitTagSort(groupId) },
                            enabled = !uiState.isSorting
                        ) {
                            if (uiState.isSorting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("保存排序")
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.showCreateDialog() }) {
                Icon(Icons.Default.Add, contentDescription = "创建标签")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading && uiState.tags.isEmpty() -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.error != null && uiState.tags.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = uiState.error ?: "加载失败",
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadTags(groupId) }) {
                            Text("重试")
                        }
                    }
                }
                uiState.tags.isEmpty() -> {
                    Text(
                        text = "暂无标签\n点击右下角按钮创建标签",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    uiState.sortError?.let { sortError ->
                        Text(
                            text = sortError,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(uiState.tags, key = { _, tag -> tag.id }) { _, tag ->
                            ReorderableItem(reorderableState, key = tag.id) { isDragging ->
                                val elevation = animateDpAsState(
                                    targetValue = if (isDragging) 8.dp else 2.dp,
                                    label = "tagCardElevation"
                                )
                                TagCard(
                                    tag = tag,
                                    onClick = { onTagClick(tag) },
                                    onEditClick = { viewModel.showEditDialog(tag) },
                                    onDeleteClick = { viewModel.requestDeleteTag(tag) },
                                    elevation = elevation.value
                                )
                            }
                        }

                    }
                }
            }
        }
    }
    
    // 创建/编辑标签对话框
    if (uiState.showCreateDialog) {
        TagEditDialog(
            tag = uiState.editingTag,
            tagName = uiState.editingTagName,
            tagColor = uiState.editingTagColor,
            tagDesc = uiState.editingTagDesc,
            isSaving = uiState.isSaving,
            error = uiState.saveError,
            onTagNameChange = viewModel::updateTagName,
            onTagColorChange = viewModel::updateTagColor,
            onTagDescChange = viewModel::updateTagDesc,
            onConfirm = { viewModel.saveTag(groupId) },
            onDismiss = viewModel::dismissDialog
        )
    }

    uiState.pendingDeleteTag?.let { pendingTag ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteTagDialog,
            title = { Text("删除标签") },
            text = { Text("确定要删除标签 ${pendingTag.tag} 吗？") },
            confirmButton = {
                Button(onClick = { viewModel.confirmDeleteTag(groupId) }) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteTagDialog) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun ReorderableCollectionItemScope.TagCard(
    tag: GroupTag,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    elevation: androidx.compose.ui.unit.Dp = 2.dp
) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "拖拽排序",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.draggableHandle()
            )

            Spacer(modifier = Modifier.width(12.dp))

            // 标签颜色指示器
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = try {
                            Color(tag.color.toColorInt())
                        } catch (e: Exception) {
                            MaterialTheme.colorScheme.primary
                        },
                        shape = CircleShape
                    )
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // 标签信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tag.tag,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (tag.desc.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = tag.desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // 操作按钮
            IconButton(onClick = onEditClick) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "编辑",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun TagEditDialog(
    tag: GroupTag?,
    tagName: String,
    tagColor: String,
    tagDesc: String,
    isSaving: Boolean,
    error: String?,
    onTagNameChange: (String) -> Unit,
    onTagColorChange: (String) -> Unit,
    onTagDescChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val predefinedColors = listOf(
        "#F44336", "#E91E63", "#9C27B0", "#673AB7",
        "#3F51B5", "#2196F3", "#03A9F4", "#00BCD4",
        "#009688", "#4CAF50", "#8BC34A", "#CDDC39",
        "#FFC107", "#FF9800", "#FF5722", "#795548"
    )
    
    AlertDialog(
        onDismissRequest = if (!isSaving) onDismiss else { {} },
        title = { Text(if (tag != null) "编辑标签" else "创建标签") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = tagName,
                    onValueChange = onTagNameChange,
                    label = { Text("标签名称") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving,
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = tagDesc,
                    onValueChange = onTagDescChange,
                    label = { Text("标签描述（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving,
                    maxLines = 3
                )
                
                // 颜色选择
                Column {
                    Text(
                        text = "标签颜色",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 颜色网格
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        predefinedColors.chunked(8).forEach { rowColors ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowColors.forEach { color ->
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(
                                                color = Color(color.toColorInt()),
                                                shape = CircleShape
                                            )
                                            .clickable(enabled = !isSaving) {
                                                onTagColorChange(color)
                                            }
                                            .then(
                                                if (color == tagColor) {
                                                    Modifier.padding(4.dp)
                                                } else Modifier
                                            )
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val isCustomColorInvalid = tagColor.isNotBlank() && !isValidColorValue(tagColor)

                    OutlinedTextField(
                        value = tagColor,
                        onValueChange = { onTagColorChange(normalizeColorToRgbHex(it)) },
                        label = { Text("自定义颜色值") },
                        placeholder = { Text("#2196F3") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSaving,
                        singleLine = true,
                        isError = isCustomColorInvalid,
                        supportingText = {
                            if (isCustomColorInvalid) {
                                Text("颜色格式无效，示例：#2196F3")
                            } else {
                                Text("会自动转换为 #RRGGBB")
                            }
                        }
                    )
                }
                
                // 错误提示
                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            val canSubmit = !isSaving && tagName.isNotBlank() && isValidColorValue(tagColor)
            Button(
                onClick = onConfirm,
                enabled = canSubmit
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(if (tag != null) "保存" else "创建")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSaving
            ) {
                Text("取消")
            }
        }
    )
}

private fun isValidColorValue(color: String): Boolean {
    if (color.isBlank()) return false
    return RGB_HEX_REGEX.matches(normalizeColorToRgbHex(color))
}

private fun normalizeColorToRgbHex(input: String): String {
    val raw = input.trim()
    if (raw.isBlank()) return raw
    val upperRaw = raw.uppercase(Locale.US)
    return try {
        val colorInt = upperRaw.toColorInt()
        String.format(Locale.US, "#%06X", (colorInt and 0x00FFFFFF))
    } catch (_: Exception) {
        upperRaw
    }
}

private val RGB_HEX_REGEX = Regex("^#[0-9A-F]{6}$")
