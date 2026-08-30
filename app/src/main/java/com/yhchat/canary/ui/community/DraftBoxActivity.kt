package com.yhchat.canary.ui.community

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yhchat.canary.data.di.RepositoryFactory
import com.yhchat.canary.data.model.Draft
import com.yhchat.canary.ui.adaptive.YhCard
import com.yhchat.canary.ui.adaptive.YhCircularProgressIndicator
import com.yhchat.canary.ui.adaptive.YhIcon as Icon
import com.yhchat.canary.ui.adaptive.YhIconButton
import com.yhchat.canary.ui.adaptive.YhScaffold
import com.yhchat.canary.ui.adaptive.YhText as Text
import com.yhchat.canary.ui.adaptive.YhTopBar
import com.yhchat.canary.ui.adaptive.yhTopBarNestedScroll
import com.yhchat.canary.ui.theme.YhchatCanaryTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 草稿箱Activity
 */
class DraftBoxActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.yhchat.canary.ui.base.SystemBarUtils.setupTransparentSystemBars(this)
        
        val token = intent.getStringExtra("token") ?: ""
        
        setContent {
            YhchatCanaryTheme {
                SetSystemNavigationBarColor()
                val draftViewModel: CommunityDraftViewModel = viewModel {
                    CommunityDraftViewModel(
                        communityRepository = RepositoryFactory.getCommunityRepository(this@DraftBoxActivity)
                    )
                }
                DraftBoxScreen(
                    token = token,
                    viewModel = draftViewModel,
                    onBackClick = { finish() },
                    onDraftClick = { draft ->
                        // 返回到CreatePostActivity并加载草稿   
                        val intent = Intent(this@DraftBoxActivity, CreatePostActivity::class.java).apply {
                            putExtra("board_id", draft.boardId)
                            putExtra("board_name", draft.boardName)
                            putExtra("token", token)
                            putExtra("draft_id", draft.id)
                            putExtra("draft_title", draft.title)
                            putExtra("draft_content", draft.content)
                            putExtra("draft_markdown_mode", draft.isMarkdownMode)
                        }
                        startActivity(intent)
                        finish()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
    
    @Composable
    @Suppress("DEPRECATION")
    private fun SetSystemNavigationBarColor() {
        val isLightTheme = !isSystemInDarkTheme()
        
        SideEffect {
            // 状态栏保持透明，让 TopAppBar 绘制到状态栏下方，避免顶部出现一块白色区域
            window.statusBarColor = Color.Transparent.toArgb()
            // 导航栏保持透明
            window.navigationBarColor = Color.Transparent.toArgb()
            
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = isLightTheme
                isAppearanceLightNavigationBars = isLightTheme
            }
        }
    }
}

/**
 * 草稿箱界面 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftBoxScreen(
    token: String,
    viewModel: CommunityDraftViewModel,
    onBackClick: () -> Unit,
    onDraftClick: (Draft) -> Unit,
    modifier: Modifier = Modifier
) {
    val draftBoxState by viewModel.draftBoxState.collectAsState()

    LaunchedEffect(token) {
        viewModel.loadDrafts(token)
    }
    
    YhScaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            YhTopBar(
                title = "草稿箱",
                large = false,
                navigationIcon = {
                    YhIconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
        draftBoxState.error?.let { error ->
            YhCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.errorContainer
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        
        // 草稿列表
        if (draftBoxState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                YhCircularProgressIndicator()
            }
        } else if (draftBoxState.drafts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "暂无草稿",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "在编写文章时保存的草稿会出现在这里",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .yhTopBarNestedScroll(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(draftBoxState.drafts, key = { it.id }) { draft ->
                    DraftItem(
                        draft = draft,
                        onClick = { onDraftClick(draft) },
                        isDeleting = draftBoxState.isDeleting && draftBoxState.deletingDraftId == draft.id,
                        onDelete = {
                            viewModel.deleteDraft(token, draft.id)
                        }
                    )
                }
            }
        }
    }
}
}

/**
 * 草稿箱 */
@Composable
fun DraftItem(
    draft: Draft,
    onClick: () -> Unit,
    isDeleting: Boolean,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    YhCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        cornerRadius = 16.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // 标题
                Text(
                    text = if (draft.title.isNotBlank()) draft.title else "无标题",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (draft.title.isNotBlank()) 
                        MaterialTheme.colorScheme.onSurface 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // 内容预览
                if (draft.content.isNotBlank()) {
                    Text(
                        text = draft.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                }
                
                // 分区和时间信息
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    YhCard(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        cornerRadius = 8.dp
                    ) {
                        Text(
                            text = draft.boardName,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    
                    if (draft.isMarkdownMode) {
                        YhCard(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            cornerRadius = 8.dp
                        ) {
                            Text(
                                text = "MarkDown",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    
                    Text(
                        text = formatTime(draft.updateTime),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // 删除按钮
            YhIconButton(onClick = onDelete, enabled = !isDeleting) {
                if (isDeleting) {
                    YhCircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除草稿",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/**
 * 格式化时间 */
private fun formatTime(timestamp: Long): String {
    val date = Date(timestamp)
    val now = Date()
    val diffInMillis = now.time - date.time
    val diffInHours = diffInMillis / (1000 * 60 * 60)
    
    val nowCal = Calendar.getInstance()
    val msgCal = Calendar.getInstance().apply { time = date }
    val isSameYear = nowCal.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR)

    return when {
        diffInHours < 1 -> "刚刚"
        diffInHours < 24 -> "${diffInHours}小时前"
        diffInHours < 24 * 7 -> "${diffInHours / 24}天前"
        isSameYear -> SimpleDateFormat("MM-dd", Locale.getDefault()).format(date)
        else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
    }
}
