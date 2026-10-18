package com.yhchat.canary.ui.disk.webdav

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import com.yhchat.canary.data.model.MountSetting
import com.yhchat.canary.ui.base.BaseActivity
import com.yhchat.canary.ui.theme.YhchatCanaryTheme

class WebDAVSettingsActivity : BaseActivity() {
    private val viewModel: WebDAVSettingsViewModel by viewModels()

    companion object {
        private const val EXTRA_GROUP_ID = "extra_group_id"
        private const val EXTRA_GROUP_NAME = "extra_group_name"

        fun intent(context: Context, groupId: String, groupName: String): Intent {
            return Intent(context, WebDAVSettingsActivity::class.java).apply {
                putExtra(EXTRA_GROUP_ID, groupId)
                putExtra(EXTRA_GROUP_NAME, groupName)
            }
        }

        fun start(context: Context, groupId: String, groupName: String) {
            context.startActivity(intent(context, groupId, groupName))
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
                WebDAVSettingsScreen(
                    groupId = groupId,
                    groupName = groupName,
                    viewModel = viewModel,
                    onBackClick = { finish() }
                )
            }
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebDAVSettingsScreen(
    groupId: String,
    groupName: String,
    viewModel: WebDAVSettingsViewModel,
    onBackClick: () -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var showInputDialog by remember { mutableStateOf(false) }
    var editingMount by remember { mutableStateOf<MountSetting?>(null) }
    var showDeleteDialog by remember { mutableStateOf<MountSetting?>(null) }

    LaunchedEffect(Unit) {
        viewModel.init(context)
        viewModel.loadMountSettings(groupId)
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearSuccessMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "$groupName 的 WebDAV 设置",
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
                    IconButton(onClick = {
                        editingMount = null
                        showInputDialog = true
                    }) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "添加挂载点")
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
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.mountSettings.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "暂无挂载点", style = MaterialTheme.typography.bodyLarge)
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.mountSettings, key = { it.id }) { mount ->
                        MountSettingCard(
                            mount = mount,
                            onEdit = {
                                editingMount = mount
                                showInputDialog = true
                            },
                            onDelete = {
                                showDeleteDialog = mount
                            }
                        )
                    }
                }
            }
        }
    }

    if (showInputDialog) {
        val mount = editingMount
        MountSettingInputDialog(
            title = if (mount == null) "添加挂载点" else "编辑挂载点",
            mount = mount,
            isSubmitting = uiState.isSubmitting,
            onDismiss = {
                showInputDialog = false
                editingMount = null
            },
            onConfirm = { formState ->
                viewModel.saveMountSetting(groupId, formState, mount?.id)
                showInputDialog = false
                editingMount = null
            }
        )
    }

    showDeleteDialog?.let { mount ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("删除挂载点") },
            text = { Text("确定删除挂载点 \"${mount.mountName}\" 吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMountSetting(groupId, mount.id)
                    showDeleteDialog = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun MountSettingCard(
    mount: MountSetting,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(mount.mountName, style = MaterialTheme.typography.titleMedium)
            Text("地址: ${mount.webdavUrl}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("目录: ${mount.webdavRootPath}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("用户名: ${mount.webdavUserName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onEdit) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = "编辑")
                }
                IconButton(onClick = onDelete) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun MountSettingInputDialog(
    title: String,
    mount: MountSetting?,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (MountFormState) -> Unit
) {
    var mountName by remember { mutableStateOf(mount?.mountName ?: "") }
    var webdavUrl by remember { mutableStateOf(mount?.webdavUrl ?: "") }
    var webdavUserName by remember { mutableStateOf(mount?.webdavUserName ?: "") }
    var webdavPassword by remember { mutableStateOf(mount?.webdavPassword ?: "") }
    var webdavRootPath by remember { mutableStateOf(mount?.webdavRootPath ?: "") }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MountTextField(valueState = mountName, label = "挂载名称") { mountName = it }
                MountTextField(valueState = webdavUrl, label = "WebDAV URL") { webdavUrl = it }
                MountTextField(valueState = webdavUserName, label = "用户名") { webdavUserName = it }
                MountTextField(valueState = webdavPassword, label = "密码") { webdavPassword = it }
                MountTextField(valueState = webdavRootPath, label = "根目录") { webdavRootPath = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        MountFormState(
                            mountName = mountName.trim(),
                            webdavUrl = webdavUrl.trim(),
                            webdavUserName = webdavUserName.trim(),
                            webdavPassword = webdavPassword.trim(),
                            webdavRootPath = webdavRootPath.trim()
                        )
                    )
                },
                enabled = !isSubmitting
            ) {
                Text(if (isSubmitting) "提交中..." else "保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun MountTextField(
    valueState: String,
    label: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = valueState,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth()
    )
}
