package com.yhchat.canary.ui.disk.webdav

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yhchat.canary.data.model.WebDAVFile
import com.yhchat.canary.ui.base.BaseActivity
import com.yhchat.canary.ui.theme.YhchatCanaryTheme
import com.yhchat.canary.utils.WebDAVDownloadManager

/**
 * WebDAV 浏览器 Activity
 */
class WebDAVBrowserActivity : BaseActivity() {
    private val viewModel: WebDAVBrowserViewModel by viewModels()
    
    companion object {
        const val EXTRA_GROUP_ID = "extra_group_id"
        const val EXTRA_GROUP_NAME = "extra_group_name"
        
        fun start(context: Context, groupId: String, groupName: String) {
            val intent = Intent(context, WebDAVBrowserActivity::class.java).apply {
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
                WebDAVBrowserScreen(
                    groupId = groupId,
                    groupName = groupName,
                    viewModel = viewModel,
                    onBackClick = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WebDAVBrowserScreen(
    groupId: String,
    groupName: String,
    viewModel: WebDAVBrowserViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // 下载对话框状态
    var showDownloadDialog by remember { mutableStateOf(false) }
    var fileToDownload by remember { mutableStateOf<WebDAVFile?>(null) }
    
    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.loadMountSettings(groupId)
    }

    LaunchedEffect(Unit) {
        viewModel.init(context)
        viewModel.loadMountSettings(groupId)
    }
    
    val uiState by viewModel.uiState.collectAsState()
    val currentMountState = uiState.mountStates[uiState.selectedMountIndex]
    val currentPath = currentMountState?.currentPath.orEmpty()

    BackHandler {
        val handled = viewModel.navigateUp()
        if (!handled) {
            onBackClick()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "$groupName 的 WebDAV",
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
                    IconButton(onClick = { WebDAVDownloadListActivity.start(context) }) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = "下载列表")
                    }
                    IconButton(onClick = {
                        val intent = WebDAVSettingsActivity.intent(context, groupId, groupName)
                        settingsLauncher.launch(intent)
                    }) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "WebDAV 设置")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
        ) {
            when {
                // 初始加载挂载点列表时显示加载中
                uiState.isLoading && uiState.mountSettings.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                
                // 加载挂载点列表失败时显示错误（此时还没有挂载点列表）
                uiState.error != null && uiState.mountSettings.isEmpty() && !uiState.isLoading -> {
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
                            Button(onClick = { viewModel.loadMountSettings(groupId) }) {
                                Text("重试")
                            }
                        }
                    }
                }
                
                // 没有挂载点
                uiState.mountSettings.isEmpty() && !uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Text(
                                text = "暂无 WebDAV 挂载点",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                // 有挂载点列表时，始终显示分类栏和文件列表（即使文件加载失败）
                else -> {
                    // 分类栏（HorizontalPager）
                    val pagerState = rememberPagerState(
                        initialPage = uiState.selectedMountIndex.coerceAtLeast(0),
                        pageCount = { uiState.mountSettings.size }
                    )
                    
                    // 同步 pager 状态和 viewModel 状态
                    LaunchedEffect(pagerState.currentPage) {
                        if (pagerState.currentPage != uiState.selectedMountIndex) {
                            viewModel.selectMount(pagerState.currentPage)
                        }
                    }
                    
                    LaunchedEffect(uiState.selectedMountIndex) {
                        if (uiState.selectedMountIndex >= 0 && 
                            uiState.selectedMountIndex != pagerState.currentPage &&
                            uiState.selectedMountIndex < uiState.mountSettings.size) {
                            pagerState.animateScrollToPage(uiState.selectedMountIndex)
                        }
                    }
                    
                    // 分类标签栏
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp),
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
                            itemsIndexed(uiState.mountSettings) { index, mountSetting ->
                                FilterChip(
                                    selected = uiState.selectedMountIndex == index,
                                    onClick = {
                                        viewModel.selectMount(index)
                                    },
                                    label = { Text(mountSetting.mountName) }
                                )
                            }
                        }
                    }
                    
                    // 面包屑路径
                    WebDAVBreadcrumbBar(
                        currentPath = currentPath,
                        onNavigate = { targetPath ->
                            viewModel.navigateToPath(targetPath)
                        }
                    )
                    
                    // 文件内容区域 - 使用 HorizontalPager
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { pageIndex ->
                        // 每个页面显示对应挂载点的文件列表
                        WebDAVFileListPage(
                            uiState = uiState,
                            viewModel = viewModel,
                            context = context,
                            pageIndex = pageIndex,
                            isCurrentPage = pageIndex == uiState.selectedMountIndex,
                            onFileClick = { file ->
                                // 显示下载确认对话框
                                fileToDownload = file
                                showDownloadDialog = true
                            }
                        )
                    }
                }
            }
        }
    }
    
    // 下载确认对话框
    if (showDownloadDialog && fileToDownload != null) {
        AlertDialog(
            onDismissRequest = {
                showDownloadDialog = false
                fileToDownload = null
            },
            title = { Text("下载文件") },
            text = { 
                Column {
                    Text("确定要下载文件 \"${fileToDownload!!.name}\" 吗？")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "文件大小: ${fileToDownload!!.getFormattedSize()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "下载目录: /storage/emulated/0/Download/yhchat",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        fileToDownload?.let { file ->
                            WebDAVDownloadManager.startDownload(context, file)
                            Toast.makeText(context, "已加入下载列表", Toast.LENGTH_SHORT).show()
                        }
                        showDownloadDialog = false
                        fileToDownload = null
                    }
                ) {
                    Text("开始下载")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDownloadDialog = false
                        fileToDownload = null
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun WebDAVFileListPage(
    uiState: WebDAVBrowserUiState,
    viewModel: WebDAVBrowserViewModel,
    context: android.content.Context,
    pageIndex: Int,
    isCurrentPage: Boolean,
    onFileClick: (WebDAVFile) -> Unit
) {
    // 获取当前页面对应的挂载点状态
    val mountState = uiState.mountStates[pageIndex]
    
    when {
        mountState?.isLoading == true -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        
        mountState?.error != null -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = mountState.error ?: "加载失败",
                        color = MaterialTheme.colorScheme.error
                    )
                    Button(onClick = { 
                        val selectedMount = uiState.mountSettings.getOrNull(pageIndex)
                        if (selectedMount != null) {
                            viewModel.loadFiles(selectedMount, mountState.currentPath)
                        }
                    }) {
                        Text("重试")
                    }
                }
            }
        }
        
        mountState?.files?.isEmpty() == true -> {
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
        
        mountState != null -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(mountState.files, key = { it.path }) { file ->
                    WebDAVFileCard(
                        file = file,
                        onClick = {
                            if (file.isDirectory) {
                                viewModel.enterFolder(file.path)
                            } else {
                                // 调用文件点击回调
                                onFileClick(file)
                            }
                        },
                        onLongClick = {
                            // TODO: 显示文件操作菜单
                            Toast.makeText(context, "长按: ${file.name}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
        
        else -> {
            // 还没有加载过该挂载点，显示空状态
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "滑动到此页面以加载文件",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WebDAVFileCard(
    file: WebDAVFile,
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
                imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = if (file.isDirectory) 
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
                Text(
                    text = file.getFormattedSize(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WebDAVBreadcrumbBar(
    currentPath: String,
    onNavigate: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    val segments = currentPath.split('/').filter { it.isNotBlank() }
    var cumulativePath = ""
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "根目录",
            color = if (segments.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (segments.isEmpty()) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.clickable { onNavigate("") }
        )
        segments.forEachIndexed { index, segment ->
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(16.dp)
            )
            cumulativePath = if (cumulativePath.isEmpty()) segment else "$cumulativePath/$segment"
            val isLast = index == segments.lastIndex
            Text(
                text = segment,
                color = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.clickable { onNavigate(cumulativePath) },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
