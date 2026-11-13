@file:OptIn(ExperimentalMaterial3Api::class)

package com.yhchat.canary.ui.community

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.yhchat.canary.data.model.CommunityBoard
import com.yhchat.canary.utils.ImageUploadUtil
import kotlinx.coroutines.launch

/**
 * 分区管理底部弹窗。
 *
 * 从 CommunityTabScreen 里拆出来，因为「我的分区」列表和分区详情页都要用同一份，
 * 而原来它和 MoreTabContent 的一堆状态（关注数、编辑态、删除确认）缠在同一个
 * 函数体里，无法复用。
 *
 * 内部自己管全部编辑态，外部只需要给一个 [board] 和收 [onBoardUpdated]：
 * 这样两个调用方都不用重复维护那 8 个 mutableStateOf。
 */
@Composable
fun ManageBoardSheet(
    token: String,
    board: CommunityBoard,
    viewModel: CommunityViewModel,
    onDismiss: () -> Unit,
    onBoardUpdated: (CommunityBoard) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 编辑保存成功后要立刻反映到弹窗里的头像和名字，
    // 所以留一份本地副本；用 board.id 做 key，避免父级重组时把刚改好的值冲掉
    var currentBoard by remember(board.id) { mutableStateOf(board) }

    var followersTotal by remember(board.id) { mutableStateOf(0) }

    var visibleRange by remember(board.id) { mutableStateOf(0) }
    var publishAuthority by remember(board.id) { mutableStateOf(0) }
    var isManaging by remember(board.id) { mutableStateOf(false) }
    var manageError by remember(board.id) { mutableStateOf<String?>(null) }

    var isEditingBoard by remember(board.id) { mutableStateOf(false) }
    var editedBoardName by remember(board.id) { mutableStateOf(board.name) }
    var editedAvatarUrl by remember(board.id) { mutableStateOf(board.avatar) }
    var isUploadingAvatar by remember(board.id) { mutableStateOf(false) }
    var editError by remember(board.id) { mutableStateOf<String?>(null) }

    var showDeleteBoardDialog by remember(board.id) { mutableStateOf(false) }
    var isDeletingBoard by remember(board.id) { mutableStateOf(false) }
    var deleteError by remember(board.id) { mutableStateOf<String?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val selected = uri ?: return@rememberLauncherForActivityResult
        coroutineScope.launch {
            isUploadingAvatar = true
            editError = null
            try {
                val uploadToken = ImageUploadUtil.getQiniuUploadToken(context, token)
                if (uploadToken.isNotEmpty()) {
                    ImageUploadUtil.uploadImage(context, selected, uploadToken).fold(
                        onSuccess = { response ->
                            editedAvatarUrl = "https://chat-img.jwznb.com/${response.key}"
                        },
                        onFailure = { e ->
                            editError = e.message ?: "图片上传失败"
                        }
                    )
                } else {
                    editError = "获取上传token失败"
                }
            } catch (e: Exception) {
                editError = e.message ?: "图片上传失败"
            } finally {
                isUploadingAvatar = false
            }
        }
    }

    // 关注人数单独一个接口，进弹窗时才拉
    LaunchedEffect(board.id) {
        viewModel.loadBaFollowerTotal(
            token = token,
            baId = board.id,
            onSuccess = { total -> followersTotal = total },
            onError = { followersTotal = 0 }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "管理分区",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .clickable(enabled = isEditingBoard && !isUploadingAvatar) {
                            imagePickerLauncher.launch("image/*")
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val avatarToShow = if (isEditingBoard) editedAvatarUrl else currentBoard.avatar
                    if (avatarToShow.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(avatarToShow)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        androidx.compose.material3.Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = CircleShape
                        ) {}
                    }

                    if (isUploadingAvatar) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isEditingBoard) editedBoardName else currentBoard.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "ID: ${currentBoard.id}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "关注列表 $followersTotal >",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clickable {
                                FollowersListActivity.start(
                                    context = context,
                                    token = token,
                                    baId = currentBoard.id,
                                    baName = currentBoard.name
                                )
                            }
                    )
                }

                TextButton(
                    onClick = {
                        isEditingBoard = !isEditingBoard
                        editedBoardName = currentBoard.name
                        editedAvatarUrl = currentBoard.avatar
                        editError = null
                    }
                ) {
                    Text(if (isEditingBoard) "取消修改" else "修改")
                }
            }

            if (isEditingBoard) {
                OutlinedTextField(
                    value = editedBoardName,
                    onValueChange = { input ->
                        if (input.length <= 10) {
                            editedBoardName = input
                        }
                    },
                    label = { Text("分区名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isUploadingAvatar
                )

                editError?.let { err ->
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Button(
                    onClick = {
                        val name = editedBoardName.trim()
                        val avatar = editedAvatarUrl.trim()
                        if (name.isBlank()) {
                            editError = "分区名称不能为空"
                            return@Button
                        }
                        if (avatar.isBlank()) {
                            editError = "分区头像不能为空"
                            return@Button
                        }
                        isManaging = true
                        viewModel.editBoard(
                            token = token,
                            baId = currentBoard.id,
                            name = name,
                            avatar = avatar,
                            onSuccess = {
                                isManaging = false
                                isEditingBoard = false
                                val updated = currentBoard.copy(name = name, avatar = avatar)
                                currentBoard = updated
                                onBoardUpdated(updated)
                            },
                            onError = { msg ->
                                isManaging = false
                                editError = msg
                            }
                        )
                    },
                    enabled = !isManaging && !isUploadingAvatar,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isManaging) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("保存中")
                    } else {
                        Text("保存修改")
                    }
                }
            }

            HorizontalDivider()

            Text(
                text = "可见范围",
                style = MaterialTheme.typography.titleMedium
            )
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = visibleRange == 0,
                        onClick = { visibleRange = 0 }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("所有人可见")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = visibleRange == 1,
                        onClick = { visibleRange = 1 }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("仅分区所有者可见")
                }
            }

            Text(
                text = "发布权限",
                style = MaterialTheme.typography.titleMedium
            )
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = publishAuthority == 0,
                        onClick = { publishAuthority = 0 }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("所有人都可以发文章和评论")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = publishAuthority == 1,
                        onClick = { publishAuthority = 1 }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("仅所有者发文章，任何人可评论")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = publishAuthority == 2,
                        onClick = { publishAuthority = 2 }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("仅所有者发文章和评论")
                }
            }

            manageError?.let { err ->
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Button(
                onClick = {
                    isManaging = true
                    viewModel.manageBoard(
                        token = token,
                        baId = currentBoard.id,
                        visibleRange = visibleRange,
                        publishAuthority = publishAuthority,
                        onSuccess = {
                            isManaging = false
                            onDismiss()
                        },
                        onError = { msg ->
                            isManaging = false
                            manageError = msg
                        }
                    )
                },
                enabled = !isManaging && !isUploadingAvatar,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isManaging) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("提交中")
                } else {
                    Text("保存")
                }
            }

            HorizontalDivider()

            deleteError?.let { err ->
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Button(
                onClick = {
                    deleteError = null
                    showDeleteBoardDialog = true
                },
                enabled = !isManaging && !isUploadingAvatar && !isDeletingBoard,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isDeletingBoard) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("删除中", color = MaterialTheme.colorScheme.onError)
                } else {
                    Text("删除分区", color = MaterialTheme.colorScheme.onError)
                }
            }
        }

        if (showDeleteBoardDialog) {
            AlertDialog(
                onDismissRequest = {
                    if (!isDeletingBoard) {
                        showDeleteBoardDialog = false
                    }
                },
                title = { Text("删除分区") },
                text = {
                    Text("确定要删除分区「${currentBoard.name}」吗？删除后不可恢复。")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            isDeletingBoard = true
                            deleteError = null
                            viewModel.deleteBoard(
                                token = token,
                                baId = currentBoard.id,
                                onSuccess = {
                                    isDeletingBoard = false
                                    showDeleteBoardDialog = false
                                    onDismiss()
                                },
                                onError = { msg ->
                                    isDeletingBoard = false
                                    deleteError = msg
                                }
                            )
                        },
                        enabled = !isDeletingBoard
                    ) {
                        Text("删除", color = MaterialTheme.colorScheme.onError)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showDeleteBoardDialog = false },
                        enabled = !isDeletingBoard
                    ) {
                        Text("取消")
                    }
                }
            )
        }
    }
}
