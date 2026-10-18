package com.yhchat.canary.ui.profile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.yhchat.canary.ui.base.BaseActivity
import com.yhchat.canary.ui.theme.YhchatCanaryTheme
import kotlinx.coroutines.launch

/**
 * 邮箱绑定Activity
 */
class EmailBindingActivity : BaseActivity() {
    private val viewModel: EmailBindingViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return EmailBindingViewModel(this@EmailBindingActivity) as T
            }
        }
    }
    
    companion object {
        fun start(context: Context) {
            val intent = Intent(context, EmailBindingActivity::class.java)
            context.startActivity(intent)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            YhchatCanaryTheme {
                EmailBindingScreen(
                    viewModel = viewModel,
                    onBackClick = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailBindingScreen(
    viewModel: EmailBindingViewModel,
    onBackClick: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    
    var email by remember { mutableStateOf("") }
    var emailVerificationCode by remember { mutableStateOf("") }
    var captchaCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var captchaId by remember { mutableStateOf("") }
    var captchaBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    
    // 获取验证码状态
    var isGettingEmailCode by remember { mutableStateOf(false) }
    var emailCodeCountdown by remember { mutableStateOf(0) }
    
    // 加载验证码
    LaunchedEffect(Unit) {
        viewModel.getCaptcha { id, bitmap ->
            captchaId = id
            captchaBitmap = bitmap
        }
    }
    
    // 倒计时效果
    LaunchedEffect(emailCodeCountdown) {
        if (emailCodeCountdown > 0) {
            kotlinx.coroutines.delay(1000)
            emailCodeCountdown--
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "绑定邮箱",
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
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 说明文字
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "绑定邮箱",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "绑定邮箱后，您可以使用邮箱登录账户，并接收重要通知。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // 人机验证码显示和刷新
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "人机验证码",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        IconButton(
                            onClick = {
                                viewModel.getCaptcha { id, bitmap ->
                                    captchaId = id
                                    captchaBitmap = bitmap
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "刷新验证码"
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 验证码图片
                    captchaBitmap?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "验证码",
                            modifier = Modifier
                                .height(60.dp)
                                .fillMaxWidth()
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 验证码输入框
                    OutlinedTextField(
                        value = captchaCode,
                        onValueChange = { captchaCode = it },
                        label = { Text("请输入验证码") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true
                    )
                }
            }
            
            // 邮箱输入
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("邮箱地址") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = "邮箱"
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                singleLine = true
            )
            
            // 邮箱验证码输入和获取按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = emailVerificationCode,
                    onValueChange = { emailVerificationCode = it },
                    label = { Text("邮箱验证码") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    singleLine = true
                )
                
                Button(
                    onClick = {
                        if (email.isNotBlank() && captchaCode.isNotBlank() && captchaId.isNotBlank()) {
                            isGettingEmailCode = true
                            coroutineScope.launch {
                                viewModel.getEmailVerificationCode(
                                    email = email,
                                    captchaCode = captchaCode,
                                    captchaId = captchaId,
                                    onSuccess = {
                                        isGettingEmailCode = false
                                        emailCodeCountdown = 60
                                        android.widget.Toast.makeText(context, "验证码已发送", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    onError = { error ->
                                        isGettingEmailCode = false
                                        android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_SHORT).show()
                                        // 刷新验证码
                                        viewModel.getCaptcha { id, bitmap ->
                                            captchaId = id
                                            captchaBitmap = bitmap
                                        }
                                    }
                                )
                            }
                        } else {
                            android.widget.Toast.makeText(context, "请先填写邮箱和验证码", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isGettingEmailCode && emailCodeCountdown == 0 && email.isNotBlank() && captchaCode.isNotBlank(),
                    modifier = Modifier.height(56.dp)
                ) {
                    if (isGettingEmailCode) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else if (emailCodeCountdown > 0) {
                        Text("${emailCodeCountdown}s")
                    } else {
                        Text("获取验证码")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 确定绑定按钮
            Button(
                onClick = {
                    if (email.isNotBlank() && emailVerificationCode.isNotBlank()) {
                        isLoading = true
                        coroutineScope.launch {
                            viewModel.bindEmail(
                                email = email,
                                captcha = emailVerificationCode,
                                onSuccess = {
                                    isLoading = false
                                    android.widget.Toast.makeText(context, "邮箱绑定成功", android.widget.Toast.LENGTH_SHORT).show()
                                    (context as? EmailBindingActivity)?.finish()
                                },
                                onError = { error ->
                                    isLoading = false
                                    android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    } else {
                        android.widget.Toast.makeText(context, "请填写完整信息", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = !isLoading && email.isNotBlank() && emailVerificationCode.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "确定绑定",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
