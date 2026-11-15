package com.yhchat.canary.ui.community

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yhchat.canary.data.model.CommunityPost
import com.yhchat.canary.data.model.Draft
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 「我的文章」页的内容区：草稿在上，已发布文章在下。
 *
 * 为什么草稿放最上面：草稿是"还没做完的事"，用户进这个页面十有八九是回来继续写的。
 * 混在已发布文章里按时间排序的话，一篇三天前的草稿会被埋在几十篇已发布文章中间。
 *
 * 两者共用一个 LazyColumn 而不是嵌套两个列表：嵌套滚动容器在 Compose 里
 * 高度计算会出问题，且下拉刷新只能作用于外层。
 */
@Composable
internal fun MyPostsContentWithDrafts(
    drafts: List<Draft>,
    draftsLoading: Boolean,
    posts: List<CommunityPost>,
    postsLoading: Boolean,
    error: String?,
    searchQuery: String,
    onDraftClick: (Draft) -> Unit,
    onDraftDelete: (Draft) -> Unit,
    deletingDraftId: String?,
    onPostClick: (CommunityPost) -> Unit,
    onPostEdit: (CommunityPost) -> Unit,
    onDeletePost: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ---- 错误 ----
        error?.let { message ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // ---- 草稿区 ----
        // 有草稿时才出现分区标题，没草稿就不占地方也不显示"草稿 0"
        if (drafts.isNotEmpty() || draftsLoading) {
            item {
                SectionLabel(
                    text = "草稿",
                    count = drafts.size,
                    accent = MaterialTheme.colorScheme.tertiary
                )
            }

            if (draftsLoading && drafts.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            }

            items(drafts, key = { "draft_${it.id}" }) { draft ->
                DraftCard(
                    draft = draft,
                    isDeleting = draft.id == deletingDraftId,
                    onClick = { onDraftClick(draft) },
                    onDelete = { onDraftDelete(draft) }
                )
            }
        }

        // ---- 已发布 ----
        if (posts.isNotEmpty() || postsLoading) {
            item {
                Spacer(modifier = Modifier.height(if (drafts.isNotEmpty()) 8.dp else 0.dp))
                SectionLabel(
                    text = "已发布",
                    count = posts.size,
                    accent = MaterialTheme.colorScheme.primary
                )
            }
        }

        items(posts, key = { "post_${it.id}" }) { post ->
            MyPostItem(
                post = post,
                onClick = { onPostClick(post) },
                onEdit = { onPostEdit(post) },
                onDelete = { onDeletePost(post.id) },
                modifier = Modifier.animateItem()
            )
        }

        // ---- 全空 ----
        if (drafts.isEmpty() && posts.isEmpty() && !postsLoading && !draftsLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "未找到匹配的内容" else "还没有发布过文章",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (postsLoading && posts.isEmpty() && drafts.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

/** 分区标题：文字 + 数量 */
@Composable
private fun SectionLabel(
    text: String,
    count: Int,
    accent: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = accent
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 草稿卡片。
 *
 * 视觉上和已发布文章拉开差距：用虚线般的浅色容器 + 「草稿」标记，
 * 避免用户误以为自己已经发布过。
 */
@Composable
private fun DraftCard(
    draft: Draft,
    isDeleting: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = !isDeleting) { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.EditNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = draft.title.ifBlank { "无标题草稿" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (draft.title.isBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )

                if (draft.content.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = draft.content.replace("\n", " "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MiniChip(text = draft.boardName.ifBlank { "未选分区" })

                    if (draft.isMarkdownMode) {
                        MiniChip(
                            text = "MD",
                            container = MaterialTheme.colorScheme.secondaryContainer,
                            content = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    Text(
                        text = formatDraftTime(draft.updateTime),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onDelete, enabled = !isDeleting) {
                if (isDeleting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除草稿",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniChip(
    text: String,
    container: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primaryContainer,
    content: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    Surface(shape = RoundedCornerShape(6.dp), color = container) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            color = content
        )
    }
}

/** 草稿的相对时间，规则和聊天列表一致 */
private fun formatDraftTime(timestamp: Long): String {
    if (timestamp <= 0L) return "未知时间"
    val date = Date(timestamp)
    val diff = System.currentTimeMillis() - date.time
    val hours = diff / (1000 * 60 * 60)

    val nowCal = Calendar.getInstance()
    val dateCal = Calendar.getInstance().apply { time = date }

    return when {
        hours < 1 -> "刚刚"
        hours < 24 -> "${hours}小时前"
        hours < 24 * 7 -> "${hours / 24}天前"
        nowCal.get(Calendar.YEAR) == dateCal.get(Calendar.YEAR) ->
            SimpleDateFormat("MM-dd", Locale.getDefault()).format(date)
        else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
    }
}
