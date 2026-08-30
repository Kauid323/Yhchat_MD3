package com.yhchat.canary.ui.community

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.MonetizationOn
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.yhchat.canary.crash.CrashHandler
import com.yhchat.canary.data.di.RepositoryFactory
import com.yhchat.canary.data.model.CommunityBoard
import com.yhchat.canary.data.model.CommunityComment
import com.yhchat.canary.data.model.CommunityPost
import com.yhchat.canary.ui.adaptive.YhAlertDialog
import com.yhchat.canary.ui.adaptive.YhBottomSheet
import com.yhchat.canary.ui.adaptive.YhButton
import com.yhchat.canary.ui.adaptive.YhCard
import com.yhchat.canary.ui.adaptive.YhCircularProgressIndicator
import com.yhchat.canary.ui.adaptive.YhDropdownSelector
import com.yhchat.canary.ui.adaptive.YhDropdownMenu
import com.yhchat.canary.ui.adaptive.YhDropdownMenuItem
import com.yhchat.canary.ui.adaptive.YhFilterChip
import com.yhchat.canary.ui.adaptive.YhHorizontalDivider
import com.yhchat.canary.ui.adaptive.YhIcon as Icon
import com.yhchat.canary.ui.adaptive.YhIconButton
import com.yhchat.canary.ui.adaptive.YhOutlinedTextField
import com.yhchat.canary.ui.adaptive.YhPullToRefresh
import com.yhchat.canary.ui.adaptive.YhScaffold
import com.yhchat.canary.ui.adaptive.YhSurface
import com.yhchat.canary.ui.adaptive.YhCommunityActionBarColor
import com.yhchat.canary.ui.adaptive.YhCommunityActionChipContainerColor
import com.yhchat.canary.ui.adaptive.YhCommunityActionChipContentColor
import com.yhchat.canary.ui.adaptive.YhText as Text
import com.yhchat.canary.ui.adaptive.YhTextButton
import com.yhchat.canary.ui.adaptive.YhTopBar
import com.yhchat.canary.ui.adaptive.YhTopAppBar
import com.yhchat.canary.ui.adaptive.yhTopBarNestedScroll
import com.yhchat.canary.ui.base.BaseActivity
import com.yhchat.canary.ui.components.ImageUtils
import com.yhchat.canary.ui.components.ImageViewer
import com.yhchat.canary.ui.components.MarkdownText
import com.yhchat.canary.ui.theme.YhchatCanaryTheme
import com.yhchat.canary.ui.user.UserDetailActivity
import com.yhchat.canary.utils.UnifiedLinkHandler
import dagger.hilt.android.AndroidEntryPoint
import java.text.DecimalFormat
import java.util.regex.Pattern

private fun TextUnit.scaledOrUnspecified(multiplier: Float): TextUnit {
    return if (this == TextUnit.Unspecified) {
        TextUnit.Unspecified
    } else {
        this * multiplier
    }
}

/**
 * 文章详情Activity
 */
@AndroidEntryPoint
class PostDetailActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // 首先尝试处理深度链接
        var postId = intent.getIntExtra("post_id", 0)
        var postTitle = intent.getStringExtra("post_title") ?: "文章详情"
        var token = intent.getStringExtra("token") ?: ""
        
        // 处理深度链接
        if (postId == 0 && intent.data != null) {
            val deepLinkPostId = UnifiedLinkHandler.extractPostIdFromLink(intent.data.toString())
            if (deepLinkPostId != null) {
                postId = deepLinkPostId
                postTitle = "文章详情"
                // 可能需要从其他地方获取token，这里先留空
            }
        }
        
        setContent {
            YhchatCanaryTheme {
                val viewModel: PostDetailViewModel = viewModel {
                    PostDetailViewModel(
                        communityRepository = RepositoryFactory.getCommunityRepository(this@PostDetailActivity),
                        tokenRepository = RepositoryFactory.getTokenRepository(this@PostDetailActivity),
                        userRepository = RepositoryFactory.getUserRepository(this@PostDetailActivity),
                        friendRepository = RepositoryFactory.getFriendRepository(this@PostDetailActivity),
                        messageRepository = RepositoryFactory.getMessageRepository(this@PostDetailActivity)
                    )
                }
                
                PostDetailScreen(
                    postId = postId,
                    postTitle = postTitle,
                    viewModel = viewModel,
                    onBackClick = { finish() }
                )
            }
        }
    }
}

/**
 * 文章内容卡片
 */
@Composable
fun PostContentCard(
    post: CommunityPost,
    board: CommunityBoard? = null,
    token: String = "",
    onLikeClick: () -> Unit,
    onCollectClick: () -> Unit,
    onCommentClick: () -> Unit = {},
    onRewardClick: () -> Unit = {},
    onReportClick: () -> Unit = {},
    searchQuery: String = "",
    onSearchResultCountChanged: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showImageViewer by remember { mutableStateOf(false) }
    var currentImageUrl by remember { mutableStateOf("") }
    val trimmedSearchQuery = searchQuery.trim()

    LaunchedEffect(post.id, post.title, post.content, trimmedSearchQuery) {
        val totalMatches = if (trimmedSearchQuery.isBlank()) {
            0
        } else {
            countKeywordMatches(post.title, trimmedSearchQuery) + countKeywordMatches(post.content, trimmedSearchQuery)
        }
        onSearchResultCountChanged(totalMatches)
    }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 作者信息
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = post.senderAvatar,
                contentDescription = post.senderNickname,
                modifier = Modifier
                    .size(48.dp)
                    .clickable {
                        UserDetailActivity.start(context = context, userId = post.senderId, userName = post.senderNickname)
                    },
                contentScale = ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = post.senderNickname,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (post.isVip == 1) {
                        Spacer(modifier = Modifier.width(8.dp))
                        YhSurface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "VIP",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
                Text(
                    text = post.createTimeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            YhTextButton(onClick = onReportClick) {
                Text("举报")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 文章标题 - 支持选择复制
        SelectionContainer {
            if (trimmedSearchQuery.isBlank()) {
                Text(
                    text = post.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                HighlightedArticleText(
                    text = post.title,
                    keyword = trimmedSearchQuery,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // 文章内容 - 支持选择复制
        if (post.contentType == 2) {
            if (trimmedSearchQuery.isBlank()) {
                MarkdownText(
                    markdown = post.content,
                    textColor = MaterialTheme.colorScheme.onSurface,
                    onImageClick = { imageUrl ->
                        currentImageUrl = imageUrl
                        showImageViewer = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enableHtmlRendering = true
                )
            } else {
                SelectionContainer {
                    HighlightedArticleText(
                        text = stripMarkdownForSearchDisplay(post.content),
                        keyword = trimmedSearchQuery,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight.scaledOrUnspecified(1.3f),
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
            // 普通文本内容，或搜索态下统一高亮
            SelectionContainer {
                if (trimmedSearchQuery.isBlank()) {
                    ArticleLinkText(
                        text = post.content,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight.scaledOrUnspecified(1.3f),
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    HighlightedArticleText(
                        text = post.content,
                        keyword = trimmedSearchQuery,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight.scaledOrUnspecified(1.3f),
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 群聊来源信息
        post.group?.let { group ->
            if (!group.groupId.isNullOrEmpty() && group.groupId != "0") {
                YhCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            // 使用统一链接处理器跳转到群聊添加页面
                            val groupLink = "yunhu://chat-add?id=${group.groupId}&type=group"
                            UnifiedLinkHandler.handleLink(context, groupLink)
                        },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = group.avatarUrl,
                            contentDescription = group.name,
                            modifier = Modifier.size(32.dp),
                            contentScale = ContentScale.Crop
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "来自 ${group.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
        
        // 分区信息卡片
        board?.let { boardInfo ->
            YhCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        // 跳转到分区详情页
                        val intent = Intent(context, BoardDetailActivity::class.java).apply {
                            putExtra("board_id", boardInfo.id)
                            putExtra("board_name", boardInfo.name)
                            putExtra("token", token)
                        }
                        context.startActivity(intent)
                    },
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = ImageUtils.createBoardImageRequest(
                            context = context,
                            url = boardInfo.avatar
                        ),
                        contentDescription = boardInfo.name,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Crop
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = boardInfo.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${boardInfo.memberNum} 成员 · ${boardInfo.postNum} 文章",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "进入分区",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
    
    // 图片预览器
    if (showImageViewer) {
        ImageViewer(
            imageUrl = currentImageUrl,
            onDismiss = { showImageViewer = false }
        )
    }
    
}

@Composable
fun PostBottomActionBarDuo3(
    post: CommunityPost,
    onLikeClick: () -> Unit,
    onCollectClick: () -> Unit,
    onRewardClick: () -> Unit,
    onCommentInputToggle: () -> Unit,
    isSearchExpanded: Boolean,
    searchText: String,
    searchResultCount: Int,
    onSearchTextChange: (String) -> Unit,
    onSearchToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchExpandSettled by remember { mutableStateOf(false) }
    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) {
            searchExpandSettled = false
            kotlinx.coroutines.delay(170)
            searchExpandSettled = true
        } else {
            searchExpandSettled = false
        }
    }
    val searchFieldWidth by animateDpAsState(
        targetValue = when {
            !isSearchExpanded -> 0.dp
            searchExpandSettled -> 228.dp
            else -> 196.dp
        },
        animationSpec = tween(170),
        label = "postDetailSearchFieldWidth"
    )
    val searchFieldAlpha by animateFloatAsState(
        targetValue = if (isSearchExpanded) 1f else 0f,
        animationSpec = tween(180),
        label = "postDetailSearchFieldAlpha"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = !isSearchExpanded,
                enter = expandHorizontally(
                    expandFrom = Alignment.Start,
                    animationSpec = tween(220)
                ) + fadeIn(animationSpec = tween(180)),
                exit = shrinkHorizontally(
                    shrinkTowards = Alignment.Start,
                    animationSpec = tween(220)
                ) + fadeOut(animationSpec = tween(160))
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(YhCommunityActionBarColor())
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    YhSurface(
                        modifier = Modifier.clickable { onLikeClick() },
                        shape = RoundedCornerShape(24.dp),
                        color = YhCommunityActionChipContainerColor(post.isLiked == "1")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (post.isLiked == "1") Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                                contentDescription = "点赞",
                                tint = YhCommunityActionChipContentColor(post.isLiked == "1"),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = post.likeNum.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = YhCommunityActionChipContentColor(post.isLiked == "1")
                            )
                        }
                    }

                    YhSurface(
                        modifier = Modifier.clickable { onCollectClick() },
                        shape = RoundedCornerShape(24.dp),
                        color = YhCommunityActionChipContainerColor(post.isCollected == 1)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (post.isCollected == 1) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = "收藏",
                                tint = YhCommunityActionChipContentColor(post.isCollected == 1),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = post.collectNum.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = YhCommunityActionChipContentColor(post.isCollected == 1)
                            )
                        }
                    }

                    YhSurface(
                        modifier = Modifier.clickable { onRewardClick() },
                        shape = RoundedCornerShape(24.dp),
                        color = YhCommunityActionChipContainerColor(post.isReward == 1)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (post.isReward == 1) Icons.Filled.MonetizationOn else Icons.Outlined.MonetizationOn,
                                contentDescription = "打赏",
                                tint = YhCommunityActionChipContentColor(post.isReward == 1),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = formatRewardAmount(post.amountNum),
                                style = MaterialTheme.typography.labelMedium,
                                color = YhCommunityActionChipContentColor(post.isReward == 1)
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(YhCommunityActionBarColor())
                .height(40.dp)
                .padding(start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Box(
                modifier = Modifier
                    .width(searchFieldWidth)
                    .height(32.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                YhSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .padding(end = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Transparent,
                    shadowElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = searchText,
                            onValueChange = onSearchTextChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .alpha(searchFieldAlpha),
                            decorationBox = { innerTextField ->
                                if (searchText.isEmpty()) {
                                    Text(
                                        text = "搜索正文",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                                innerTextField()
                            }
                        )
                        Text(
                            text = searchResultCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.alpha(searchFieldAlpha)
                        )
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = !isSearchExpanded,
                enter = fadeIn(animationSpec = tween(180)),
                exit = fadeOut(animationSpec = tween(140))
            ) {
                YhButton(
                    onClick = onCommentInputToggle,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Comment,
                        contentDescription = "评论",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = post.commentNum.toString(),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            YhIconButton(
                onClick = onSearchToggle,
                modifier = Modifier.size(32.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Crossfade(
                    targetState = isSearchExpanded,
                    animationSpec = tween(180),
                    label = "post_detail_search_toggle"
                ) { expanded ->
                    Icon(
                        imageVector = if (expanded) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = if (expanded) "关闭搜索" else "搜索",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * 评论项
 */
@Composable
fun CommentItem(
    comment: CommunityComment,
    canDelete: Boolean = false,
    onLikeClick: (Int) -> Unit = {},
    onReplyClick: (Int) -> Unit = {},
    onDeleteClick: (CommunityComment) -> Unit = {},
    onReportClick: (CommunityComment) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    YhCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 16.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                AsyncImage(
                    model = comment.senderAvatar,
                    contentDescription = comment.senderNickname,
                    modifier = Modifier
                        .size(32.dp)
                        .clickable {
                            UserDetailActivity.start(context = context, userId = comment.senderId, userName = comment.senderNickname)
                        },
                    contentScale = ContentScale.Crop
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = comment.senderNickname,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        if (comment.isVip == 1) {
                            Spacer(modifier = Modifier.width(4.dp))
                            YhSurface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = "VIP",
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                    
                    Text(
                        text = comment.createTimeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // 评论内容 - 支持选择复制
                    SelectionContainer {
                        Text(
                            text = comment.content,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 评论操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 点赞按钮
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onLikeClick(comment.id) }
                    ) {
                        Icon(
                            imageVector = if (comment.isLiked == "1") Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                            contentDescription = "点赞",
                            tint = if (comment.isLiked == "1") 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        if (comment.likeNum > 0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = comment.likeNum.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (comment.isLiked == "1") 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    // 回复按钮
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onReplyClick(comment.id) }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Comment,
                            contentDescription = "回复",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        if (comment.repliesNum > 0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = comment.repliesNum.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (canDelete) {
                        YhTextButton(
                            onClick = { onDeleteClick(comment) },
                        ) {
                            Text("删除", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    YhTextButton(
                        onClick = { onReportClick(comment) },
                    ) {
                        Text("举报")
                    }
                }
            }
            
            // 显示回复
            if (!comment.replies.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier.padding(start = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    comment.replies.forEach { reply ->
                        CommentReplyItem(reply = reply)
                    }
                }
            }
        }
    }
}

/**
 * 评论回复项
 */
@Composable
fun CommentReplyItem(
    reply: CommunityComment,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        AsyncImage(
            model = reply.senderAvatar,
            contentDescription = reply.senderNickname,
            modifier = Modifier
                .size(24.dp)
                .clickable {
                    UserDetailActivity.start(context = context, userId = reply.senderId, userName = reply.senderNickname)
                },
            contentScale = ContentScale.Crop
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = reply.senderNickname,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                if (reply.isVip == 1) {
                    Spacer(modifier = Modifier.width(4.dp))
                    YhSurface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "VIP",
                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = reply.createTimeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Text(
                text = reply.content,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}


/**
 * 打赏对话框
 */
@Composable
fun RewardDialog(
    onDismiss: () -> Unit,
    onReward: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    var rewardAmount by remember { mutableStateOf("") }
    val predefinedAmounts = listOf(0.1, 0.5, 1.0, 2.0, 5.0, 10.0)
    
    YhAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "打赏文章",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "感谢作者的精彩内容，给予一些支持吧！",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    predefinedAmounts.take(3).forEach { amount ->
                        YhFilterChip(
                            onClick = { rewardAmount = amount.toString() },
                            label = { Text("${amount}币") },
                            selected = rewardAmount == amount.toString(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    predefinedAmounts.drop(3).forEach { amount ->
                        YhFilterChip(
                            onClick = { rewardAmount = amount.toString() },
                            label = { Text("${amount}币") },
                            selected = rewardAmount == amount.toString(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                YhOutlinedTextField(
                    value = rewardAmount,
                    onValueChange = { 
                        if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d*$"))) {
                            rewardAmount = it
                        }
                    },
                    label = { Text("自定义金额") },
                    placeholder = { Text("输入打赏金额") },
                    suffix = { Text("币") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            YhTextButton(
                onClick = {
                    val amount = rewardAmount.toDoubleOrNull()
                    if (amount != null && amount > 0) {
                        onReward(amount)
                    }
                },
                enabled = rewardAmount.toDoubleOrNull()?.let { it > 0 } == true
            ) {
                Text("确认打赏")
            }
        },
        dismissButton = {
            YhTextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 分享对话框
 */
@Composable
fun ShareDialog(
    postId: Int,
    onDismiss: () -> Unit,
    onShareToFriend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val webLink = "www.yhchat.com/c/p/$postId"
    val deepLink = "yunhu://post-detail?id=$postId"
    
    YhAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "分享文章",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "选择分享方式：",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                YhCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "网页链接",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = webLink,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        YhButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("文章链接", webLink)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "网页链接已复制", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "复制",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("复制链接")
                        }
                    }
                }
                
                YhCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "应用内链接",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = deepLink,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        YhButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("文章链接", deepLink)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "应用内链接已复制", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "复制",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("复制链接")
                        }
                    }
                }
                
                YhCard(
                    modifier = Modifier.fillMaxWidth().clickable { 
                        onDismiss()
                        onShareToFriend()
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "分享给好友",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "分享给好友",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        },
        confirmButton = {
            YhTextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

/**
 * 文章详情界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    postId: Int,
    postTitle: String,
    viewModel: PostDetailViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 获取状态
    val postDetailState by viewModel.postDetailState.collectAsState()
    val commentListState by viewModel.commentListState.collectAsState()
    val followingBoardListState by viewModel.followingBoardListState.collectAsState()
    val communityReportState by viewModel.communityReportState.collectAsState()
    
    // 评论输入状态
    var commentText by remember { mutableStateOf("") }
    var showCommentInput by remember { mutableStateOf(false) }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var searchResultCount by remember { mutableStateOf(0) }
    
    // 打赏对话框状态
    var showRewardDialog by remember { mutableStateOf(false) }
    
    // 分享对话框状态
    var showShareDialog by remember { mutableStateOf(false) }
    // 分享给好友BottomSheet状态
    var showShareToFriendSheet by remember { mutableStateOf(false) }
    var showMoveBoardDialog by remember { mutableStateOf(false) }
    var pendingMoveBoard by remember { mutableStateOf<CommunityBoard?>(null) }
    var topMenuExpanded by remember { mutableStateOf(false) }
    
    // Token状态
    var currentToken by remember { mutableStateOf("") }
    var isTokenLoaded by remember { mutableStateOf(false) }
    var reportTarget by remember { mutableStateOf<CommunityReportTarget?>(null) }
    var pendingDeleteComment by remember { mutableStateOf<CommunityComment?>(null) }
    
    // 删除确认对话框状态
    var showDeleteDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val canManagePost = postDetailState.post != null &&
            (postDetailState.post?.senderId == postDetailState.currentUserId || postDetailState.isAdmin == 1)

    LaunchedEffect(showMoveBoardDialog) {
        if (showMoveBoardDialog) {
            viewModel.loadFollowingBoardsWithToken()
        }
    }
    
    // 加载数据
    LaunchedEffect(postId) {
        if (postId > 0) {
            viewModel.loadPostDetailWithToken(postId)
            // 获取token
            currentToken = viewModel.getTokenAsync()
            isTokenLoaded = true
            android.util.Log.d("PostDetailActivity", "🔑 Token加载完成: ${currentToken.take(10)}...")
        }
    }
    
    // 错误处理
    LaunchedEffect(postDetailState.error, commentListState.error) {
        postDetailState.error?.let { error ->
            // 可以在这里显示Snackbar或其他错误提示
            viewModel.clearError()
        }
        commentListState.error?.let { error ->
            // 可以在这里显示Snackbar或其他错误提示
            viewModel.clearError()
        }
    }

    LaunchedEffect(communityReportState.success, communityReportState.error) {
        if (communityReportState.success) {
            Toast.makeText(context, "举报成功", Toast.LENGTH_SHORT).show()
            reportTarget = null
            viewModel.clearCommunityReportState()
        }
        communityReportState.error?.let { error ->
            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
            viewModel.clearCommunityReportState()
        }
    }
    
    
    YhScaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // 统一底部操作栏
            postDetailState.post?.let { post ->
                PostBottomActionBarDuo3(
                    post = post,
                    onLikeClick = {
                        viewModel.likePostWithToken(postId)
                    },
                    onCollectClick = {
                        viewModel.collectPostWithToken(postId)
                    },
                    onRewardClick = {
                        showRewardDialog = true
                    },
                    onCommentInputToggle = {
                        showCommentInput = !showCommentInput
                    },
                    isSearchExpanded = isSearchExpanded,
                    searchText = searchText,
                    searchResultCount = searchResultCount,
                    onSearchTextChange = { searchText = it },
                    onSearchToggle = {
                        if (isSearchExpanded) {
                            searchText = ""
                        }
                        isSearchExpanded = !isSearchExpanded
                    }
                )
            }
        },
        topBar = {
            YhTopBar(
                title = "文章详情",
                navigationIcon = {
                    YhIconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    YhIconButton(onClick = { topMenuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "更多操作"
                        )
                    }
                    YhDropdownMenu(
                        expanded = topMenuExpanded,
                        onDismissRequest = { topMenuExpanded = false }
                    ) {
                        YhDropdownMenuItem(
                            text = { Text("分享") },
                            onClick = {
                                topMenuExpanded = false
                                showShareDialog = true
                            }
                        )
                        if (canManagePost) {
                            YhDropdownMenuItem(
                                text = { Text("修改") },
                                onClick = {
                                    topMenuExpanded = false
                                    postDetailState.post?.let { post ->
                                        val intent = Intent(context, EditPostActivity::class.java).apply {
                                            putExtra("post_id", post.id)
                                            putExtra("token", if (isTokenLoaded) currentToken else "")
                                            putExtra("original_title", post.title)
                                            putExtra("original_content", post.content)
                                            putExtra("content_type", post.contentType)
                                            putExtra("board_id", post.baId)
                                            putExtra("board_name", postDetailState.board?.name ?: "")
                                        }
                                        context.startActivity(intent)
                                    }
                                }
                            )
                            YhDropdownMenuItem(
                                text = { Text("移动文章") },
                                onClick = {
                                    topMenuExpanded = false
                                    showMoveBoardDialog = true
                                }
                            )
                            YhDropdownMenuItem(
                                text = {
                                    Text(
                                        if ((postDetailState.post?.isSticky ?: 0) == 1) {
                                            "取消置顶"
                                        } else {
                                            "置顶文章"
                                        }
                                    )
                                },
                                onClick = {
                                    topMenuExpanded = false
                                    val post = postDetailState.post ?: return@YhDropdownMenuItem
                                    val sticky = post.isSticky != 1
                                    viewModel.togglePostStickyWithToken(
                                        postId = post.id,
                                        sticky = sticky,
                                        onSuccess = {
                                            Toast.makeText(
                                                context,
                                                if (sticky) "置顶成功" else "已取消置顶",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        onError = { msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            )
                            YhDropdownMenuItem(
                                text = { Text("删除文章", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    topMenuExpanded = false
                                    showDeleteDialog = true
                                }
                            )
                        }
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
            
            // 错误提示
            postDetailState.error?.let { error ->
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
            
            // 加载状态
            if (postDetailState.isLoading && postDetailState.post == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    YhCircularProgressIndicator()
                }
            } else {
                postDetailState.post?.let { post ->
                    YhPullToRefresh(
                        isRefreshing = postDetailState.isRefreshing,
                        onRefresh = { viewModel.refreshPostDetailWithToken(postId) },
                        modifier = Modifier.weight(1f)
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .yhTopBarNestedScroll(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // 文章内容
                            item {
                                PostContentCard(
                                    post = post,
                                    board = postDetailState.board,
                                    token = if (isTokenLoaded) currentToken else "",
                                    onLikeClick = {
                                        viewModel.likePostWithToken(postId)
                                    },
                                    onCollectClick = {
                                        viewModel.collectPostWithToken(postId)
                                    },
                                    onRewardClick = {
                                        showRewardDialog = true
                                    },
                                    onReportClick = {
                                        reportTarget = CommunityReportTarget(
                                            typ = 1,
                                            targetId = post.id,
                                            title = "举报文章"
                                        )
                                    },
                                    searchQuery = searchText,
                                    onSearchResultCountChanged = { count ->
                                        searchResultCount = count
                                    }
                                )
                            }
                            
                        }
                    }
                    
                }
            }
            
            // 打赏对话框
            if (showRewardDialog) {
                RewardDialog(
                    onDismiss = { showRewardDialog = false },
                    onReward = { amount ->
                        viewModel.rewardPostWithToken(postId, amount)
                        showRewardDialog = false
                    }
                )
            }

            reportTarget?.let { target ->
                CommunityReportDialog(
                    target = target,
                    isSubmitting = communityReportState.isSubmitting,
                    onDismiss = {
                        if (!communityReportState.isSubmitting) {
                            reportTarget = null
                            viewModel.clearCommunityReportState()
                        }
                    },
                    onSubmit = { reason, content, imageUri ->
                        viewModel.submitCommunityReport(
                            context = context,
                            typ = target.typ,
                            id = target.targetId,
                            content = content,
                            reason = reason,
                            imageUri = imageUri
                        )
                    }
                )
            }
            
            // 分享对话框
            if (showShareDialog) {
                ShareDialog(
                    postId = postId,
                    onDismiss = { showShareDialog = false },
                    onShareToFriend = { showShareToFriendSheet = true }
                )
            }

            if (showCommentInput) {
                YhBottomSheet(
                    show = true,
                    title = "评论 (${commentListState.comments.size})",
                    onDismissRequest = { showCommentInput = false },
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "评论 (${commentListState.comments.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            YhTextButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("帖子原文", postDetailState.post?.content ?: "")
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "原文已复制", Toast.LENGTH_SHORT).show()
                                }
                            ) { Text("复制原文") }
                        }

                        YhOutlinedTextField(
                            value = commentText,
                            onValueChange = { commentText = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("写下你的评论...") },
                            maxLines = 3,
                            singleLine = false,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            trailingIcon = {
                                if (commentText.isNotBlank()) {
                                    YhIconButton(
                                        onClick = {
                                            viewModel.commentPostWithToken(postId, commentText)
                                            commentText = ""
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "发送",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            },
                            shape = RoundedCornerShape(16.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(commentListState.comments) { comment ->
                                CommentItem(
                                    comment = comment,
                                    canDelete = comment.senderId == postDetailState.currentUserId || postDetailState.isAdmin == 1 || commentListState.isAdmin == 1,
                                    onLikeClick = { commentId ->
                                        viewModel.likeCommentWithToken(postId, commentId)
                                    },
                                    onReplyClick = {
                                    },
                                    onDeleteClick = { selectedComment ->
                                        pendingDeleteComment = selectedComment
                                    },
                                    onReportClick = { selectedComment ->
                                        reportTarget = CommunityReportTarget(
                                            typ = 2,
                                            targetId = selectedComment.id,
                                            title = "举报评论"
                                        )
                                    }
                                )
                            }

                            if (commentListState.hasMore) {
                                item {
                                    LaunchedEffect(Unit) {
                                        if (!commentListState.isLoading) {
                                            viewModel.loadMoreCommentsWithToken(postId)
                                        }
                                    }
                                    if (commentListState.isLoading) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            YhCircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                strokeWidth = 2.dp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 分享给好友BottomSheet
            if (showShareToFriendSheet) {
                postDetailState.post?.let { post ->
                    ShareToFriendBottomSheet(
                        post = post,
                        viewModel = viewModel,
                        onDismiss = { showShareToFriendSheet = false }
                    )
                }
            }

            pendingDeleteComment?.let { comment ->
                YhAlertDialog(
                    onDismissRequest = { pendingDeleteComment = null },
                    title = { Text("删除评论") },
                    text = { Text("确定要删除这条评论吗？删除后无法恢复。") },
                    confirmButton = {
                        YhTextButton(
                            onClick = {
                                viewModel.deleteCommentWithToken(postId, comment.id)
                                pendingDeleteComment = null
                            }
                        ) {
                            Text("删除", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        YhTextButton(onClick = { pendingDeleteComment = null }) {
                            Text("取消")
                        }
                    }
                )
            }
            
            if (showMoveBoardDialog) {
                YhAlertDialog(
                    onDismissRequest = { showMoveBoardDialog = false },
                    title = { Text("移动文章") },
                    text = {
                        when {
                            followingBoardListState.isLoading -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    YhCircularProgressIndicator()
                                }
                            }

                            followingBoardListState.error != null -> {
                                Text(
                                    text = followingBoardListState.error ?: "加载关注分区失败",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            followingBoardListState.boards.isEmpty() -> {
                                Text("暂无已关注分区")
                            }

                            else -> {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 320.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(followingBoardListState.boards) { board ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { pendingMoveBoard = board }
                                                .padding(vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AsyncImage(
                                                model = board.avatar,
                                                contentDescription = board.name,
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(RoundedCornerShape(6.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = board.name,
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        YhTextButton(onClick = { showMoveBoardDialog = false }) {
                            Text("关闭")
                        }
                    }
                )
            }

            pendingMoveBoard?.let { board ->
                YhAlertDialog(
                    onDismissRequest = { pendingMoveBoard = null },
                    title = { Text("确认移动") },
                    text = { Text("确认将文章移动到「${board.name}」吗？") },
                    confirmButton = {
                        YhTextButton(
                            onClick = {
                                pendingMoveBoard = null
                                viewModel.movePostWithToken(
                                    postId = postId,
                                    targetBaId = board.id,
                                    onSuccess = {
                                        showMoveBoardDialog = false
                                        Toast.makeText(context, "移动成功", Toast.LENGTH_SHORT).show()
                                    },
                                    onError = { msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        ) {
                            Text("确认")
                        }
                    },
                    dismissButton = {
                        YhTextButton(onClick = { pendingMoveBoard = null }) {
                            Text("取消")
                        }
                    }
                )
            }
            
            // 删除确认对话框
            if (showDeleteDialog) {
                YhAlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = { Text("删除文章") },
                    text = { Text("确定要删除这篇文章吗？删除后无法恢复。") },
                    confirmButton = {
                        YhTextButton(
                            onClick = {
                                showDeleteDialog = false
                                viewModel.deletePost(if (isTokenLoaded) currentToken else "", postId) {
                                    // 删除成功，关闭页面
                                    (context as? android.app.Activity)?.finish()
                                }
                            }
                        ) {
                            Text("删除", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        YhTextButton(onClick = { showDeleteDialog = false }) {
                            Text("取消")
                        }
                    }
                )
            }
        }
    }
}

/**
 * 文章链接文本组件 - 支持 HTTP/HTTPS 链接点击，遇到中文停止识别
 */
@Composable
fun ArticleLinkText(
    text: String,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    linkColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // HTTP/HTTPS 链接正则表达式 - 遇到中文字符停止
    val urlPattern = Pattern.compile(
        "https?://[^\\s\\u4e00-\\u9fff]+",
        Pattern.CASE_INSENSITIVE
    )
    
    val annotatedString = buildAnnotatedString {
        val matcher = urlPattern.matcher(text)
        var lastEnd = 0
        
        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()
            val url = matcher.group()
            
            // 添加链接前的普通文本
            if (start > lastEnd) {
                append(text.substring(lastEnd, start))
            }
            
            // 添加链接文本 - 醒目的样式
            pushStringAnnotation(tag = "URL", annotation = url)
            withStyle(
                style = SpanStyle(
                    color = linkColor,
                    textDecoration = TextDecoration.Underline,
                    fontWeight = FontWeight.Medium
                )
            ) {
                append(url)
            }
            pop()
            
            lastEnd = end
        }
        
        // 添加剩余的普通文本
        if (lastEnd < text.length) {
            append(text.substring(lastEnd))
        }
    }
    
    @Suppress("DEPRECATION")
    ClickableText(
        text = annotatedString,
        style = style.copy(color = MaterialTheme.colorScheme.onSurface), // 确保使用正确的主题颜色
        modifier = modifier,
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    val url = annotation.item
                    
                    // 使用统一的链接处理器
                    if (UnifiedLinkHandler.isHandleableLink(url)) {
                        UnifiedLinkHandler.handleLink(context, url)
                    } else {
                        // 使用系统浏览器打开其他链接
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            CrashHandler.showCaughtException(context, e)
                            Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
        }
    )
}

@Composable
private fun HighlightedArticleText(
    text: String,
    keyword: String,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    val highlightColor = MaterialTheme.colorScheme.tertiaryContainer
    val highlighted = remember(text, keyword, highlightColor) {
        buildAnnotatedString {
            if (keyword.isBlank()) {
                append(text)
                return@buildAnnotatedString
            }
            val source = text
            val lowerSource = source.lowercase()
            val lowerKeyword = keyword.lowercase()
            var cursor = 0
            while (cursor < source.length) {
                val idx = lowerSource.indexOf(lowerKeyword, cursor)
                if (idx < 0) {
                    append(source.substring(cursor))
                    break
                }
                if (idx > cursor) {
                    append(source.substring(cursor, idx))
                }
                withStyle(SpanStyle(background = highlightColor, fontWeight = FontWeight.SemiBold)) {
                    append(source.substring(idx, idx + keyword.length))
                }
                cursor = idx + keyword.length
            }
        }
    }
    Text(
        text = highlighted,
        style = style,
        modifier = modifier
    )
}

private fun countKeywordMatches(text: String, keyword: String): Int {
    if (keyword.isBlank() || text.isBlank()) return 0
    val source = text.lowercase()
    val target = keyword.lowercase()
    var cursor = 0
    var count = 0
    while (cursor < source.length) {
        val idx = source.indexOf(target, cursor)
        if (idx < 0) break
        count += 1
        cursor = idx + target.length
    }
    return count
}

private fun formatRewardAmount(amount: Double): String {
    val formatter = DecimalFormat("0.#")
    return formatter.format(amount)
}

private fun stripMarkdownForSearchDisplay(markdown: String): String {
    if (markdown.isBlank()) return markdown
    var text = markdown
    text = text.replace(Regex("```[\\s\\S]*?```"), " ")
    text = text.replace(Regex("`([^`]+)`"), "$1")
    text = text.replace(Regex("!\\[([^\\]]*)\\]\\(([^)]+)\\)"), "$1")
    text = text.replace(Regex("\\[([^\\]]+)\\]\\(([^)]+)\\)"), "$1")
    text = text.replace(Regex("(?m)^\\s{0,3}#{1,6}\\s*"), "")
    text = text.replace(Regex("(?m)^\\s{0,3}>\\s?"), "")
    text = text.replace(Regex("(?m)^\\s*[-*+]\\s+"), "")
    text = text.replace(Regex("(?m)^\\s*\\d+\\.\\s+"), "")
    text = text.replace(Regex("(\\*\\*|__)(.*?)\\1"), "$2")
    text = text.replace(Regex("(\\*|_)(.*?)\\1"), "$2")
    text = text.replace(Regex("~~(.*?)~~"), "$1")
    text = text.replace(Regex("<[^>]+>"), " ")
    return text.replace(Regex("[ \\t\\x0B\\f\\r]+"), " ").replace(Regex("\\n{3,}"), "\n\n").trim()
}


/**
 * 社区举报对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommunityReportDialog(
    target: CommunityReportTarget,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (reason: String, content: String, imageUri: Uri?) -> Unit
) {
    val context = LocalContext.current
    var reportContent by remember { mutableStateOf("") }
    var selectedReason by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
    }

    YhAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(target.title)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                YhDropdownSelector(
                    items = COMMUNITY_REPORT_REASONS,
                    selectedIndex = COMMUNITY_REPORT_REASONS.indexOf(selectedReason),
                    onSelectedIndexChange = { index ->
                        selectedReason = COMMUNITY_REPORT_REASONS.getOrNull(index).orEmpty()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = "举报原因",
                    placeholder = "请选择举报原因"
                )

                YhOutlinedTextField(
                    value = reportContent,
                    onValueChange = { reportContent = it },
                    label = { Text("补充说明") },
                    placeholder = { Text("选填，例如“不小心举报的”") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done
                    )
                )

                YhCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { imagePickerLauncher.launch("image/*") },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (selectedImageUri != null) {
                            AsyncImage(
                                model = selectedImageUri,
                                contentDescription = "举报图片",
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "点击更换图片",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Image,
                                contentDescription = "添加图片",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "点击添加举报图片（可选）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            YhTextButton(
                onClick = {
                    if (selectedReason.isBlank()) {
                        Toast.makeText(context, "请选择举报原因", Toast.LENGTH_SHORT).show()
                        return@YhTextButton
                    }
                    onSubmit(selectedReason, reportContent, selectedImageUri)
                },
                enabled = !isSubmitting
            ) {
                if (isSubmitting) {
                    YhCircularProgressIndicator(modifier = Modifier.size(16.dp))
                } else {
                    Text("提交举报")
                }
            }
        },
        dismissButton = {
            YhTextButton(
                onClick = onDismiss,
                enabled = !isSubmitting
            ) {
                Text("取消")
            }
        }
    )
}

private data class CommunityReportTarget(
    val typ: Int,
    val targetId: Int,
    val title: String
)

private val COMMUNITY_REPORT_REASONS = listOf(
    "色情低俗",
    "时政不实消息",
    "垃圾广告",
    "青少年不宜",
    "辱骂攻击",
    "侵犯权益",
    "违法犯罪",
    "开盒网暴",
    "其他"
)
