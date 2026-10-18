package com.yhchat.canary.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Environment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.yhchat.canary.data.di.RepositoryFactory
import com.yhchat.canary.data.repository.ShareRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 分享对话框
 */
@Composable
fun ShareDialog(
    chatId: String,
    chatType: Int,  // 1-用户，2-群聊，3-机器人
    chatName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val viewModel = remember { ShareViewModel() }
    
    LaunchedEffect(Unit) {
        viewModel.init(context)
        viewModel.createShareLink(chatId, chatType, chatName)
    }
    
    val uiState by viewModel.uiState.collectAsState()
    
    val scope = rememberCoroutineScope()
    var isSavingImage by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "分享${when(chatType) {
                            1 -> "用户"
                            2 -> "群聊"
                            3 -> "机器人"
                            else -> "对象"
                        }}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = chatName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Default.QrCode,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        },
        text = {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when {
                    uiState.isLoading -> {
                        CircularProgressIndicator()
                        Text("生成分享链接中...")
                    }
                    
                    uiState.error != null -> {
                        Text(
                            text = uiState.error ?: "",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    
            uiState.shareLink != null -> {
                val shareData = uiState.shareLink
                        
                        // 云湖内链
                        val chatTypeText = when(chatType) {
                            1 -> "user"
                            2 -> "group"
                            3 -> "bot"
                            else -> "unknown"
                        }
                        val yunhuLink = "yunhu://chat-add?id=$chatId&type=$chatTypeText"
                        
                        // 分享文本（带ID）
                        val shareText = "访问链接加入云湖${when(chatType) {
                            1 -> "用户"
                            2 -> "群聊"
                            3 -> "机器人"
                            else -> "对象"
                        }}【$chatName】\n${shareData?.shareUrl}share?key=${shareData?.key}&ts=${shareData?.ts}\n${when(chatType) {
                            1 -> "用户ID"
                            2 -> "群ID"
                            3 -> "机器人ID"
                            else -> "ID"
                        }}: $chatId"
                        
                        val shareImageUrl = shareData?.getShareImageUrl()
                        
                        // 分享图片卡片
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Column {
                                // 分享图片
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(220.dp)
                                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                ) {
                                    AsyncImage(
                                        model = shareImageUrl,
                                        contentDescription = "分享图片",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    
                                    // 保存图片按钮
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                isSavingImage = true
                                                val result = saveShareImage(context, shareImageUrl ?: "")
                                                isSavingImage = false
                                                android.widget.Toast.makeText(
                                                    context,
                                                    if (result) "图片已保存" else "保存失败",
                                                    android.widget.Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                                        ) {
                                            if (isSavingImage) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier
                                                        .padding(12.dp)
                                                        .size(20.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.Download,
                                                    contentDescription = "保存图片",
                                                    modifier = Modifier.padding(12.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                                
                                // 提示文字
                                Text(
                                    text = "长按图片或点击下载按钮保存",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                        .align(Alignment.CenterHorizontally)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // 操作按钮组
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 复制链接按钮
                            OutlinedButton(
                                onClick = { copyToClipboard(context, shareText) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("复制链接")
                            }
                            
                            // 分享按钮
                            Button(
                                onClick = { shareToSystem(context, shareText, chatName) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("分享")
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // 链接信息卡片
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                // 外部分享链接
                                LinkInfoRow(
                                    label = "分享链接",
                                    value = "${shareData?.shareUrl}share?key=${shareData?.key}&ts=${shareData?.ts}",
                                    onCopy = { copyToClipboard(context, "${shareData?.shareUrl}share?key=${shareData?.key}&ts=${shareData?.ts}") }
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider()
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // 云湖内链
                                LinkInfoRow(
                                    label = "云湖内链",
                                    value = yunhuLink,
                                    onCopy = { copyToClipboard(context, yunhuLink) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

/**
 * 复制到剪贴板
 */
private fun copyToClipboard(context: Context, text: String) {
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("分享链接", text)
    clipboardManager.setPrimaryClip(clip)
    android.widget.Toast.makeText(context, "链接已复制", android.widget.Toast.LENGTH_SHORT).show()
}

/**
 * 分享到系统
 */
private fun shareToSystem(context: Context, shareUrl: String, chatName: String) {
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, "加入我的$chatName：$shareUrl")
        type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, "分享到")
    context.startActivity(shareIntent)
}

/**
 * 保存分享图片到本地
 */
private suspend fun saveShareImage(context: Context, imageUrl: String): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            // 使用Coil下载图片
            val imageLoader = context.imageLoader
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .build()
            
            val result = imageLoader.execute(request)
            if (result is SuccessResult) {
                val bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    ?: return@withContext false
                
                // 保存到Pictures目录
                val fileName = "share_${System.currentTimeMillis()}.jpg"
                val file = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    // Android 10+ 使用MediaStore
                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, fileName)
                        put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/YhChat")
                    }
                    
                    val uri = context.contentResolver.insert(
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        values
                    ) ?: return@withContext false
                    
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                    }
                    true
                } else {
                    // Android 9及以下使用传统方式
                    val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    val yhchatDir = File(picturesDir, "YhChat")
                    if (!yhchatDir.exists()) {
                        yhchatDir.mkdirs()
                    }
                    
                    val file = File(yhchatDir, fileName)
                    FileOutputStream(file).use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                    }
                    
                    // 通知系统扫描新文件
                    MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/jpeg"), null)
                    true
                }
                
                file
            } else {
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("ShareDialog", "保存图片失败", e)
            false
        }
    }
}

/**
 * 分享ViewModel
 */
class ShareViewModel : ViewModel() {
    private lateinit var shareRepository: ShareRepository
    
    private val _uiState = MutableStateFlow(ShareUiState())
    val uiState: StateFlow<ShareUiState> = _uiState.asStateFlow()
    
    fun init(context: Context) {
        shareRepository = RepositoryFactory.getShareRepository(context)
    }
    
    fun createShareLink(chatId: String, chatType: Int, chatName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            shareRepository.createShareLink(chatId, chatType, chatName).fold(
                onSuccess = { shareResponse ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        shareLink = shareResponse
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
}

data class ShareUiState(
    val isLoading: Boolean = false,
    val shareLink: com.yhchat.canary.data.model.ShareData? = null,
    val error: String? = null
)

/**
 * 链接信息行组件
 */
@Composable
private fun LinkInfoRow(
    label: String,
    value: String,
    onCopy: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = onCopy) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("复制", style = MaterialTheme.typography.labelMedium)
            }
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
