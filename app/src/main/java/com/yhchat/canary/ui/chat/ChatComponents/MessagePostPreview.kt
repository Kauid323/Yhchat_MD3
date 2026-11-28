package com.yhchat.canary.ui.chat.ChatComponents

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yhchat.canary.data.di.RepositoryFactory
import com.yhchat.canary.data.model.CommunityPost
import com.yhchat.canary.ui.components.MarkdownText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Oolong 风格的文章浮层预览：以圆角浮层呈现完整文章，内容区独立滚动。
 * 文章详情由 Repository 加载，不创建详情页 ViewModel，避免共享/覆盖详情页状态。
 */
@Composable
internal fun MessagePostPreview(
    postId: String,
    fallbackTitle: String,
    fallbackContent: String,
    fallbackContentType: Int,
    onDismiss: () -> Unit,
    onOpenPost: () -> Unit
) {
    val context = LocalContext.current
    val resolvedPostId = remember(postId) { postId.toIntOrNull()?.takeIf { it > 0 } }
    var post by remember(postId) { mutableStateOf<CommunityPost?>(null) }
    var isLoading by remember(postId) { mutableStateOf(true) }
    var errorMessage by remember(postId) { mutableStateOf<String?>(null) }
    var retryKey by remember(postId) { mutableIntStateOf(0) }
    val enterProgress = remember(postId) { Animatable(0f) }

    LaunchedEffect(postId) {
        enterProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
        )
    }

    LaunchedEffect(postId, retryKey) {
        isLoading = true
        errorMessage = null
        post = null
        val loadedPost = try {
            withContext(Dispatchers.IO) {
                val token = RepositoryFactory.getTokenRepository(context).getTokenSync()
                    ?: error("请先登录后再查看文章")
                RepositoryFactory.getCommunityRepository(context)
                    .getPostDetail(token, resolvedPostId ?: error("文章编号无效"))
                    .getOrThrow()
                    .data
                    .post
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            errorMessage = error.message?.takeIf { it.isNotBlank() } ?: "文章加载失败，请稍后重试"
            null
        }
        post = loadedPost
        isLoading = false
    }

    val title = post?.title?.takeIf { it.isNotBlank() } ?: fallbackTitle.ifBlank { "文章" }
    val body = post?.content ?: fallbackContent
    val contentType = post?.contentType ?: fallbackContentType

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 420.dp)
                .fillMaxHeight(0.72f)
                .graphicsLayer {
                    alpha = enterProgress.value
                    val scale = 0.94f + 0.06f * enterProgress.value
                    scaleX = scale
                    scaleY = scale
                },
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 8.dp,
            shadowElevation = 24.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 22.dp, top = 16.dp, end = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "文章预览",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭预览")
                    }
                }

                androidx.compose.material3.HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isLoading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "正在加载文章…",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    if (errorMessage != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = errorMessage.orEmpty(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(onClick = { retryKey++ }) { Text("重试") }
                        }
                    }

                    // Keep the message's cached body readable while details load or retry.
                    if (body.isNotBlank() || (!isLoading && errorMessage == null)) {
                        renderPostBody(body, contentType)
                    }
                }

                androidx.compose.material3.HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = onDismiss) { Text("关闭") }
                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                    Button(onClick = onOpenPost, enabled = resolvedPostId != null) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("打开文章")
                    }
                }
            }
        }
    }
}

@Composable
private fun renderPostBody(content: String, contentType: Int) {
    if (content.isBlank()) {
        Text(
            text = "暂无正文内容",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
    } else if (contentType == 2) {
        MarkdownText(
            markdown = content,
            textColor = MaterialTheme.colorScheme.onSurface,
            backgroundColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
            enableTextSelection = true
        )
    } else {
        SelectionContainer {
            Text(
                text = content,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
