package com.yhchat.canary.ui.settings

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.yhchat.canary.crash.CrashHandler
import com.yhchat.canary.ui.theme.YhchatCanaryTheme

class ChangePasswordActivity : ComponentActivity() {
    private val viewModel: ChangePasswordViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ChangePasswordViewModel(this@ChangePasswordActivity) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.yhchat.canary.ui.base.SystemBarUtils.setupTransparentSystemBars(this)
        
        val userEmail = intent.getStringExtra("user_email") ?: ""
        
        setContent {
            YhchatCanaryTheme {
                ChangePasswordScreen(
                    userEmail = userEmail,
                    viewModel = viewModel,
                    onBackClick = { finish() }
                )
            }
        }
    }
    
    companion object {
        fun createIntent(context: Context, userEmail: String): Intent {
            return Intent(context, ChangePasswordActivity::class.java).apply {
                putExtra("user_email", userEmail)
            }
        }
    }
}

@Composable
fun CaptchaImage(
    captchaImage: String?,
    onRefresh: () -> Unit
) {
    if (!captchaImage.isNullOrEmpty()) {
        val context = LocalContext.current
        val imageResult = remember(captchaImage) {
            try {
                // 处理特殊格式: "data:image/png;base64,..." -> 提取base64部分
                val base64Data = when {
                    captchaImage.startsWith("data:image/") -> {
                        val parts = captchaImage.split(";base64,")
                        if (parts.size > 1) parts[1] else captchaImage
                    }
                    captchaImage.startsWith("image//") -> {
                        val parts = captchaImage.split(";base64,")
                        if (parts.size > 1) parts[1] else captchaImage
                    }
                    else -> captchaImage
                }
                
                val imageBytes = Base64.decode(base64Data, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                if (bitmap != null) {
                    Result.success(bitmap)
                } else {
                    Result.failure<Bitmap>(Exception("Failed to decode bitmap"))
                }
            } catch (e: Exception) {
                CrashHandler.showCaughtException(context, e)
                Result.failure<Bitmap>(Exception("Decode error: ${e.message}"))
            }
        }
        
        when {
            imageResult.isSuccess -> {
                Image(
                    bitmap = imageResult.getOrNull()!!.asImageBitmap(),
                    contentDescription = "验证",
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { onRefresh() },
                    contentScale = ContentScale.FillBounds
                )
            }
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { onRefresh() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("加载失败，点击刷新")
                }
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { onRefresh() },
            contentAlignment = Alignment.Center
        ) {
            Text("点击刷新")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasswordScreen(
    userEmail: String,
    viewModel: ChangePasswordViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(userEmail) {
        viewModel.initialize(userEmail)
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {      
        TopAppBar(
            title = {
                Text(
                    text = "更改密码",
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                scrolledContainerColor = MaterialTheme.colorScheme.background
            )
        )
        
        // 内容区域
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 邮箱输入
            OutlinedTextField(
                value = uiState.email,
                onValueChange = { },
                label = { Text("邮箱") },
                modifier = Modifier.fillMaxWidth(),
                enabled = false // 默认邮箱不可编辑
            )
            
            // 图片验证码
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "图片验证码",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 验证码图片
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (uiState.isLoading) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        } else if (uiState.captchaImage != null) {
                            CaptchaImage(
                                captchaImage = uiState.captchaImage,
                                onRefresh = { viewModel.getCaptcha() }
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("点击刷新")
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.captchaCode,
                            onValueChange = { viewModel.updateCaptchaCode(it) },
                            label = { Text("请输入验证码") },
                            modifier = Modifier.weight(1f)
                        )
                        
                        Button(
                            onClick = { viewModel.getCaptcha() },
                            enabled = !uiState.isLoading
                        ) {
                            Text("刷新")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Button(
                        onClick = { viewModel.sendEmailVerificationCode() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isLoading && uiState.captchaCode.isNotEmpty()
                    ) {
                        Text("发送邮箱验证码")
                    }
                }
            }
            
            // 邮箱验证码输入
            OutlinedTextField(
                value = uiState.emailVerificationCode,
                onValueChange = { viewModel.updateEmailVerificationCode(it) },
                label = { Text("邮箱验证") },
                modifier = Modifier.fillMaxWidth()
            )
            
            // 新密码输入            
            OutlinedTextField(
                value = uiState.newPassword,
                onValueChange = { viewModel.updateNewPassword(it) },
                label = { Text("新密码") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (uiState.showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = viewModel::togglePasswordVisibility) {
                        Icon(
                            imageVector = if (uiState.showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (uiState.showPassword) "隐藏密码" else "显示密码"
                        )
                    }
                }
            )
            
            // 确认密码输入
            OutlinedTextField(
                value = uiState.confirmPassword,
                onValueChange = { viewModel.updateConfirmPassword(it) },
                label = { Text("确认密码") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (uiState.showConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = viewModel::toggleConfirmPasswordVisibility) {
                        Icon(
                            imageVector = if (uiState.showConfirmPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (uiState.showConfirmPassword) "隐藏密码" else "显示密码"
                        )
                    }
                }
            )
            
            // 错误信息
            if (uiState.message != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = uiState.message!!,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            
            // 更改密码按钮
            Button(
                onClick = { viewModel.changePassword() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading &&
                    uiState.emailVerificationCode.isNotEmpty() &&
                    uiState.newPassword.isNotEmpty() &&
                    uiState.confirmPassword.isNotEmpty()
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("更改密码")
            }
        }
    }
    
    // 成功
    if (uiState.success) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("更改成功") },
            text = { Text("密码已成功更改，请使用新密码登录") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.consumeSuccess()
                        onBackClick()
                    }
                ) {
                    Text("确定")
                }
            }
        )
    }
}
