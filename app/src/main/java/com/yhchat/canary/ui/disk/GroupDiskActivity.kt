package com.yhchat.canary.ui.disk

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yhchat.canary.crash.CrashHandler
import com.yhchat.canary.data.model.DiskFile
import com.yhchat.canary.ui.base.BaseActivity
import com.yhchat.canary.ui.disk.webdav.WebDAVBrowserActivity
import com.yhchat.canary.ui.theme.YhchatCanaryTheme

/**
 * 群网盘Activity
 */
class GroupDiskActivity : BaseActivity() {
    private val viewModel: GroupDiskViewModel by viewModels()
    
    companion object {
        const val EXTRA_GROUP_ID = "extra_group_id"
        const val EXTRA_GROUP_NAME = "extra_group_name"
        
        fun start(context: Context, groupId: String, groupName: String) {
            val intent = Intent(context, GroupDiskActivity::class.java).apply {
                putExtra(EXTRA_GROUP_ID, groupId)
                putExtra(EXTRA_GROUP_NAME, groupName)
            }
            context.startActivity(intent)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val groupId = intent.getStringExtra(EXTRA_GROUP_ID) ?: ""
        val groupName = intent.getStringExtra(EXTRA_GROUP_NAME) ?: "群聊"
        
        if (groupId.isEmpty()) {
            finish()
            return
        }
        
        setContent {
            YhchatCanaryTheme {
                GroupDiskScreen(
                    groupId = groupId,
                    groupName = groupName,
                    viewModel = viewModel,
                    onBackClick = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GroupDiskScreen(
    groupId: String,
    groupName: String,
    viewModel: GroupDiskViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val context = LocalContext.current
    
    LaunchedEffect(Unit) {
        viewModel.init(context)
        viewModel.loadFiles(groupId, 0)
    }
    
    val uiState by viewModel.uiState.collectAsState()
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var selectedFile by remember { mutableStateOf<DiskFile?>(null) }
    var showFileMenu by remember { mutableStateOf(false) }
    var showDownloadConfirmDialog by remember { mutableStateOf(false) }
    var fileToDownload by remember { mutableStateOf<DiskFile?>(null) }
    
    // 监听当前文件夹变化，重新加载
    LaunchedEffect(uiState.currentFolderId) {
        viewModel.loadFiles(groupId, uiState.currentFolderId)
    }
    
    // 处理安卓返回键
    BackHandler {
        if (uiState.currentFolderId != 0L) {
            // 如果不在根目录，返回上一级目录
            if (uiState.breadcrumbs.isNotEmpty()) {
                // 返回到面包屑中的上一级目录
                val parentBreadcrumb = uiState.breadcrumbs.dropLast(1).lastOrNull()
                if (parentBreadcrumb != null) {
                    viewModel.navigateToFolder(parentBreadcrumb.id)
                } else {
                    // 返回根目录
                    viewModel.navigateToFolder(0)
                }
            } else {
                // 返回根目录
                viewModel.navigateToFolder(0)
            }
        } else {
            // 在根目录时，退出Activity
            onBackClick()
        }
    }
    
    // 文件选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.uploadFile(context, it, groupId)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$groupName 的网盘", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            WebDAVBrowserActivity.start(context, groupId, groupName)
                        },
                        enabled = !uiState.isLoading
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = "WebDAV"
                        )
                    }
                    IconButton(
                        onClick = { showCreateFolderDialog = true },
                        enabled = !uiState.isLoading
                    ) {
                        Icon(
                            imageVector = Icons.Default.CreateNewFolder,
                            contentDescription = "创建文件夹"
                        )
                    }
                    IconButton(
                        onClick = { filePickerLauncher.launch("*/*") },
                        enabled = !uiState.isUploading
                    ) {
                        Icon(
                            imageVector = Icons.Default.Upload,
                            contentDescription = "上传文件"
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            if (!uiState.isLoading && !uiState.isUploading) {
                FloatingActionButton(
                    onClick = { viewModel.loadFiles(groupId) }
                ) {
                    Icon(Icons.Default.Refresh, "刷新")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 面包屑导航
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 根目录
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { 
                                viewModel.navigateToFolder(0)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "根目录",
                                modifier = Modifier.size(20.dp),
                                tint = if (uiState.currentFolderId == 0L) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "根目录",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (uiState.currentFolderId == 0L) 
                                    FontWeight.Bold 
                                else 
                                    FontWeight.Normal,
                                color = if (uiState.currentFolderId == 0L) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    // 面包屑路径
                    itemsIndexed(uiState.breadcrumbs) { index, breadcrumb ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = breadcrumb.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (index == uiState.breadcrumbs.lastIndex) 
                                    FontWeight.Bold 
                                else 
                                    FontWeight.Normal,
                                color = if (index == uiState.breadcrumbs.lastIndex) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clickable {
                                    viewModel.navigateToFolder(breadcrumb.id)
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                
                uiState.error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = uiState.error ?: "加载失败",
                                color = MaterialTheme.colorScheme.error
                            )
                            Button(onClick = { viewModel.loadFiles(groupId) }) {
                                Text("重试")
                            }
                        }
                    }
                }
                
                uiState.files.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Text(
                                text = "文件夹为空",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.files) { file ->
                            DiskFileCard(
                                file = file,
                                onClick = {
                                    if (file.objectType == 1) {
                                        // 文件夹 - 进入
                                        viewModel.enterFolder(file.id, file.name)
                                    } else {
                                        // 文件 - 显示下载确认对话框
                                        fileToDownload = file
                                        showDownloadConfirmDialog = true
                                    }
                                },
                                onLongClick = {
                                    selectedFile = file
                                    showFileMenu = true
                                }
                            )
                        }
                    }
                }
            }
            
            // 上传进度
            if (uiState.isUploading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "上传中...",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
    
    // 创建文件夹对话框
    if (showCreateFolderDialog) {
        CreateFolderDialog(
            onConfirm = { folderName ->
                viewModel.createFolder(groupId, folderName)
                showCreateFolderDialog = false
            },
            onDismiss = { showCreateFolderDialog = false }
        )
    }
    
    // 文件操作菜单
    if (showFileMenu && selectedFile != null) {
        FileActionDialog(
            file = selectedFile!!,
            onRename = {
                showFileMenu = false
                showRenameDialog = true
            },
            onDelete = {
                viewModel.deleteFile(selectedFile!!.id, selectedFile!!.objectType)
                showFileMenu = false
                selectedFile = null
            },
            onDownload = {
                downloadFileWithService(context, selectedFile!!)
            },
            onDismiss = {
                showFileMenu = false
                selectedFile = null
            }
        )
    }
    
    // 重命名对话框
    if (showRenameDialog && selectedFile != null) {
        RenameFileDialog(
            currentName = selectedFile!!.name,
            onConfirm = { newName ->
                viewModel.renameFile(selectedFile!!.id, selectedFile!!.objectType, newName)
                showRenameDialog = false
                selectedFile = null
            },
            onDismiss = {
                showRenameDialog = false
                selectedFile = null
            }
        )
    }
    
    // 下载确认对话框
    if (showDownloadConfirmDialog && fileToDownload != null) {
        DownloadConfirmDialog(
            file = fileToDownload!!,
            onConfirm = {
                downloadFileWithService(context, fileToDownload!!)
                showDownloadConfirmDialog = false
                fileToDownload = null
            },
            onDismiss = {
                showDownloadConfirmDialog = false
                fileToDownload = null
            }
        )
    }
    
    // 操作成功提示
    LaunchedEffect(uiState.operationSuccess) {
        if (uiState.operationSuccess) {
            Toast.makeText(context, "操作成功", Toast.LENGTH_SHORT).show()
            viewModel.resetOperationSuccess()
            viewModel.loadFiles(groupId)
        }
    }
}

/**
 * 使用FileDownloadService下载文件
 */
private fun downloadFileWithService(context: Context, file: DiskFile) {
    if (file.qiniuKey.isBlank()) {
        Toast.makeText(context, "文件链接无效", Toast.LENGTH_SHORT).show()
        return
    }
    
    try {
        com.yhchat.canary.service.FileDownloadService.startDownload(
            context = context,
            fileUrl = file.qiniuKey,
            fileName = file.name,
            fileSize = file.fileSize,
            autoOpen = false
        )
        Toast.makeText(context, "开始下载: ${file.name}", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        CrashHandler.showCaughtException(context, e)
        Toast.makeText(context, "下载失败: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DiskFileCard(
    file: DiskFile,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (file.isFolder()) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = if (file.isFolder()) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.secondary
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = file.getFormattedSize(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (file.uploadByName.isNotEmpty()) {
                        Text(
                            text = "by ${file.uploadByName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateFolderDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var folderName by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("创建文件夹") },
        text = {
            OutlinedTextField(
                value = folderName,
                onValueChange = { folderName = it },
                label = { Text("文件夹名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (folderName.isNotBlank()) onConfirm(folderName) },
                enabled = folderName.isNotBlank()
            ) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun FileActionDialog(
    file: DiskFile,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("文件操作") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("选中: ${file.name}")
                Spacer(modifier = Modifier.height(8.dp))
                
                // 如果是文件（objectType=2），显示下载选项
                if (file.objectType == 2) {
                    TextButton(
                        onClick = {
                            onDownload()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("下载")
                    }
                }
                
                TextButton(
                    onClick = onRename,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("重命名")
                }
                
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Delete, 
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun DownloadConfirmDialog(
    file: DiskFile,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认下载") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("确定要下载以下文件吗？")
                Spacer(modifier = Modifier.height(8.dp))
                
                // 文件信息
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "文件名：${file.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "大小：${formatFileSize(file.fileSize)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "上传者：${file.uploadByName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "文件将保存到 : /storage/emulated/0/Download/yhchat/ 目录下",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("下载")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun RenameFileDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf(currentName) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名") },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("新名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (newName.isNotBlank()) onConfirm(newName) },
                enabled = newName.isNotBlank()
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 格式化文件大小
 */
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> String.format("%.2f KB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024))
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
    }
}
