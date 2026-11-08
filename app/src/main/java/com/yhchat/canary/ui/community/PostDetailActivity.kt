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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.yhchat.canary.crash.CrashHandler
import com.yhchat.canary.data.di.RepositoryFactory
import com.yhchat.canary.data.model.CommunityBoard
import com.yhchat.canary.data.model.CommunityComment
import com.yhchat.canary.data.model.CommunityPost
import com.yhchat.canary.ui.base.BaseActivity
import com.yhchat.canary.ui.components.ImageUtils
import com.yhchat.canary.ui.components.ImageViewer
import com.yhchat.canary.ui.components.MarkdownText
import com.yhchat.canary.ui.share.SendToChatActivity
import com.yhchat.canary.ui.share.SendToChatContent
import com.yhchat.canary.ui.components.LocalPageScroll
import com.yhchat.canary.ui.components.pageScroll
import com.yhchat.canary.ui.components.rememberPageScrollState
import com.yhchat.canary.ui.components.rememberScrollProgress
import com.yhchat.canary.ui.theme.YhchatCanaryTheme
import com.yhchat.canary.ui.user.UserDetailActivity
import com.yhchat.canary.utils.UnifiedLinkHandler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
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
                        friendRepository = RepositoryFactory.getFriendRepository(this@PostDetailActivity)
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
                        Surface(
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

            TextButton(onClick = onReportClick) {
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
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            // 使用统一链接处理器跳转到群聊添加页面
                            val groupLink = "yunhu://chat-add?id=${group.groupId}&type=group"
                            UnifiedLinkHandler.handleLink(context, groupLink)
                        },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
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

    // 注意：这一层（以及 bottomBar 那一层）**不能有任何背景色/底色**，
    // 文章要能从按钮之间和按钮周围露出来，只有按钮自己是实心的（挡住它自己那一小块）。
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
                        // 真悬浮：按钮自己是不透明的 + 阴影，内容从它下面/周围滚过去
                        .shadow(elevation = 8.dp, shape = RoundedCornerShape(50))
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        modifier = Modifier.clickable { onLikeClick() },
                        shape = RoundedCornerShape(24.dp),
                        color = if (post.isLiked == "1") {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (post.isLiked == "1") Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                                contentDescription = "点赞",
                                tint = if (post.isLiked == "1") {
                                    Color.White
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = post.likeNum.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (post.isLiked == "1") {
                                    Color.White
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.clickable { onCollectClick() },
                        shape = RoundedCornerShape(24.dp),
                        color = if (post.isCollected == 1) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (post.isCollected == 1) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = "收藏",
                                tint = if (post.isCollected == 1) {
                                    Color.White
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = post.collectNum.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (post.isCollected == 1) {
                                    Color.White
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.clickable { onRewardClick() },
                        shape = RoundedCornerShape(24.dp),
                        color = if (post.isReward == 1) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (post.isReward == 1) Icons.Filled.MonetizationOn else Icons.Outlined.MonetizationOn,
                                contentDescription = "打赏",
                                tint = if (post.isReward == 1) {
                                    Color.White
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = formatRewardAmount(post.amountNum),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (post.isReward == 1) {
                                    Color.White
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                // 真悬浮：按钮自己是不透明的 + 阴影，内容从它下面/周围滚过去
                .shadow(elevation = 8.dp, shape = RoundedCornerShape(50))
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
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
                Surface(
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

            IconButton(
                onClick = onSearchToggle,
                modifier = Modifier.size(32.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
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

/* -------------------------------------------------------------------------- */
/*  评论区：楼层样式                                                           */
/* -------------------------------------------------------------------------- */

/** 楼层左侧栏：楼栏宽 Gutter，整体从 GutterStart 开始，竖线落在楼栏中线上。 */
private val FloorGutter = 52.dp
private val FloorAvatarSize = 36.dp
private val FloorGutterStart = 4.dp

/** 回复的小头像，以及它在第一行上的中心高度（分支从这里拐出去）。 */
private val ReplyAvatarSize = 22.dp
private val ReplyAvatarCenter = 12.dp
private val ReplyBranchRadius = 12.dp

/** 竖线：细，用文字色的一层淡染，不抢内容。 */
private val FloorRuleWidth = 1.25.dp
private const val FloorRuleAlpha = 0.16f

@Composable
private fun floorRuleColor(): Color =
    MaterialTheme.colorScheme.onSurface.copy(alpha = FloorRuleAlpha)

/**
 * 评论项，按论坛楼层的样式排：
 * 左栏是作者头像 + 压在头像下沿的楼层号，一条细竖线从头像中心一路往下串到下一层；
 * 右边是楼层正文。楼层里的回复挂在竖线的小分支上。
 */
@Composable
fun CommentItem(
    comment: CommunityComment,
    floor: Int,
    startsRule: Boolean = false,
    isLz: Boolean = false,
    canDelete: Boolean = false,
    onLikeClick: (Int) -> Unit = {},
    onReplyClick: (Int) -> Unit = {},
    onDeleteClick: (CommunityComment) -> Unit = {},
    onReportClick: (CommunityComment) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val rule = floorRuleColor()
    val replies = comment.replies.orEmpty()
    val density = LocalDensity.current

    // 整层（楼层 + 回复）自己的布局坐标，回复靠它换算「我相对这层顶部有多高」
    var floorCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // 每条回复的小头像中心在整层里的 y（像素），回复布局完就报上来
    val branchYs = remember { mutableStateMapOf<Int, Float>() }

    // 整层是**一张 Canvas + 一层内容**：
    //   0.0 ─┬─ 楼层正文 …… ↓
    //        ├─→ 回复1
    //        ├─→ 回复2
    //        └─→（到此为止，线不再往下拖）
    //
    // 竖线是**一条连续 Path**，不是每层各画一段再拼 —— 拼接会在
    // 楼层与回复、回复与回复之间留下缝，那正是之前看着「断掉」的原因。
    Box(
        modifier = modifier
            .fillMaxWidth()
            // 右侧留 16dp 给正文，左边由楼栏自己控制（从 4dp 起）
            .padding(end = 16.dp)
            .onGloballyPositioned { floorCoords = it }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val x = (FloorGutterStart + FloorGutter / 2).toPx()
            val top = if (startsRule) (10.dp + FloorAvatarSize / 2).toPx() else 0f
            val r = ReplyBranchRadius.toPx()
            val endX = (FloorGutterStart + FloorGutter).toPx()

            // 主线：从起点一路到底，**不中断**。这是「线连着」的根本 ——
            // 只要它不被 moveTo 打断，楼层和所有回复之间就没有缝。
            drawLine(
                color = rule,
                start = Offset(x, top),
                end = Offset(x, size.height),
                strokeWidth = FloorRuleWidth.toPx()
            )

            // 分支：每条回复一条，从主线拐出去。单独画，不跟主线抢同一个 Path。
            branchYs.values.forEach { y ->
                val branch = Path().apply {
                    moveTo(x, y - r)
                    arcTo(
                        rect = Rect(x, y - 2 * r, x + 2 * r, y),
                        startAngleDegrees = 180f,
                        sweepAngleDegrees = -90f,
                        forceMoveTo = false
                    )
                    lineTo(endX, y)
                }
                drawPath(branch, rule, style = Stroke(FloorRuleWidth.toPx()))
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            // ===== 楼层本体 =====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                // 左栏只占位（竖线由上面的整层 Canvas 画），头像和楼层号画在上面
                Box(
                    modifier = Modifier
                        .padding(start = FloorGutterStart)
                        .width(FloorGutter)
                        .height(IntrinsicSize.Min)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 10.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    UserDetailActivity.start(
                                        context = context,
                                        userId = comment.senderId,
                                        userName = comment.senderNickname
                                    )
                                }
                                .padding(bottom = 14.dp)
                        ) {
                            AsyncImage(
                                model = comment.senderAvatar,
                                contentDescription = comment.senderNickname,
                                modifier = Modifier
                                    .size(FloorAvatarSize)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        }

                        // 楼层号：压在头像下沿
                        FloorBadge(floor = floor)
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // ===== 右侧：楼层正文 =====
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 10.dp, bottom = 4.dp)
                ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = comment.senderNickname,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (comment.isVip == 1) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "VIP",
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    // 楼主标：跟「只看楼主」呼应，一眼看出哪几层是他自己
                    if (isLz) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "楼主",
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = comment.createTimeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                // 正文 - 支持选择复制
                SelectionContainer {
                    Text(
                        text = comment.content,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 楼层操作：点赞、回复在左，删除/举报在右，都压成小字，不抢正文
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onLikeClick(comment.id) }
                        ) {
                            Icon(
                                imageVector = if (comment.isLiked == "1") Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                                contentDescription = "点赞",
                                tint = if (comment.isLiked == "1") {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(15.dp)
                            )
                            if (comment.likeNum > 0) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = comment.likeNum.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (comment.isLiked == "1") {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onReplyClick(comment.id) }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Comment,
                                contentDescription = "回复",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                            if (comment.repliesNum > 0) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = comment.repliesNum.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                        Spacer(modifier = Modifier.weight(1f))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (canDelete) {
                                FloorAction("删除", color = MaterialTheme.colorScheme.error) {
                                    onDeleteClick(comment)
                                }
                            }
                            FloorAction("举报") { onReportClick(comment) }
                        }
                    }
                }
            }

            // ===== 楼层里的回复：挂到同一条竖线的分支上 =====
            replies.forEachIndexed { index, reply ->
                CommentReplyItem(
                    reply = reply,
                    isLast = index == replies.lastIndex,
                    // 上报「这条回复的小头像中心，相对整层顶部有多高」。
                    // 用 LayoutCoordinates 直接换算，不靠累加高度猜 —— 文本折行、
                    // 图片、多行内容都会让高度不固定，猜出来的 y 就会歪。
                    onPlaced = { replyCoords ->
                        val floor = floorCoords
                        if (floor != null && floor.isAttached && replyCoords.isAttached) {
                            // LayoutCoordinates 本身不带 density，用 Item 里取好的那个换算 dp→px
                            val avatarCenterPx = with(density) { ReplyAvatarCenter.toPx() }
                            val y = floor.localPositionOf(replyCoords, Offset.Zero).y + avatarCenterPx
                            if (branchYs[reply.id] != y) branchYs[reply.id] = y
                        }
                    }
                )
            }
        }
    }
}

/**
 * 楼层号牌，形如 `15L`。
 *
 * 对照截图：一块**实心白/灰底的小圆角牌**，压在头像下沿，比头像略宽；
 * 数字是深色粗体。不是描边胶囊，也不做「嵌进头像」的缺口 —— 就是一块独立的牌子。
 * 宽度上限取楼栏宽，四位数以上的楼层保持单行不撑破左栏。
 */
@Composable
private fun FloorBadge(floor: Int) {
    Box(
        modifier = Modifier
            .widthIn(min = FloorAvatarSize - 4.dp, max = FloorGutter)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "${floor}L",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

/** 楼层操作的小字按钮，比 TextButton 更安静。 */
@Composable
private fun FloorAction(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    )
}

/**
 * 楼层里的一条回复。
 *
 * **自己不画竖线** —— 整层的竖线和所有分支由 `CommentItem` 那张 Canvas
 * 用一条 Path 画完，这里只上报「小头像中心相对这层顶部的高度」（[onPlaced]），
 * 分支就从那个 y 拐出去。这样楼层与回复、回复与回复之间的竖线是**连着**的，
 * 不会各画一段拼出缝来。
 *
 * 左边留出 [FloorGutter] 给分支走线，内容从楼栏之后开始 ——
 * 所以整层的正文是**统一左边距**，不是每层各缩各的。
 */
@Composable
fun CommentReplyItem(
    reply: CommunityComment,
    isLast: Boolean = false,
    onPlaced: (LayoutCoordinates) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            // 把自己的布局坐标交出去，整层用它换算分支该从哪个 y 拐
            .onGloballyPositioned { onPlaced(it) }
    ) {
        // 楼栏：只占位，让内容从竖线右边开始
        Spacer(
            modifier = Modifier
                .padding(start = FloorGutterStart)
                .width(FloorGutter)
        )

        Row(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            AsyncImage(
                model = reply.senderAvatar,
                contentDescription = reply.senderNickname,
                modifier = Modifier
                    .size(ReplyAvatarSize)
                    // 跟第一行文字居中对齐
                    .padding(top = ReplyAvatarCenter - ReplyAvatarSize / 2)
                    .clip(CircleShape)
                    .clickable {
                        UserDetailActivity.start(
                            context = context,
                            userId = reply.senderId,
                            userName = reply.senderNickname
                        )
                    },
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(8.dp))

            // 截图里回复是「昵称: 内容」排在同一段里，时间单独一行在下面
            Column(modifier = Modifier.weight(1f)) {
                SelectionContainer {
                    // 昵称 + 冒号 + 内容 拼成一段，昵称加粗，读起来就是一问一答的一行
                    Text(
                        text = buildAnnotatedString {
                            withStyle(
                                SpanStyle(
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                append(reply.senderNickname)
                                if (reply.isVip == 1) append(" VIP")
                                append(": ")
                            }
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                                append(reply.content)
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 20.sp
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = reply.createTimeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * 评论区标题：左边「评论 + 条数」，右边「复制原文」。
 * 承接文章正文，所以上方留一点空白，跟正文形成段落感，不加分割线。
 */
@Composable
private fun CommentSectionHeader(
    total: Int,
    visibleCount: Int,
    filterLzOnly: Boolean,
    onFilterChange: (Boolean) -> Unit,
    onCopyPost: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "评论",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (filterLzOnly) "$visibleCount/$total" else "$total",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        // 「只看楼主」放在评论区这儿：它筛的是本篇评论，放在顶栏反而离内容太远
        CommentFilterToggle(
            filterLzOnly = filterLzOnly,
            onFilterChange = onFilterChange
        )
    }
}

/**
 * 评论区里的「全部 / 只看楼主」。
 *
 * 两格胶囊，选中是实底、未选中只有文字 —— 跟 Oolong 的 `SegmentedControl` 一个观感。
 * 放在评论区标题行右侧，因为它管的是评论，不是整页。
 */
@Composable
private fun CommentFilterToggle(
    filterLzOnly: Boolean,
    onFilterChange: (Boolean) -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .height(28.dp)
                .padding(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CommentFilterChip(
                text = "全部",
                selected = !filterLzOnly,
                onClick = { onFilterChange(false) }
            )
            CommentFilterChip(
                text = "只看楼主",
                selected = filterLzOnly,
                onClick = { onFilterChange(true) }
            )
        }
    }
}

/** 分段控件里的一格。 */
@Composable
private fun CommentFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.surface else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1
        )
    }
}

/**
 * 评论输入行：紧贴评论区标题下方，正文底部也能直接写。
 */
@Composable
private fun CommentInputRow(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        placeholder = { Text("写下你的评论…") },
        maxLines = 3,
        singleLine = false,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        trailingIcon = {
            if (value.isNotBlank()) {
                IconButton(onClick = onSend) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "发送",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

/** 评论区空态。 */
@Composable
private fun CommentEmptyState(lzOnly: Boolean = false) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (lzOnly) "楼主还没有评论" else "还没有评论",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (lzOnly) "切到「全部」看看其他人的回复" else "来说点什么，占个 1L",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

/** 评论区加载更多页脚。 */
@Composable
private fun CommentLoadMoreFooter(isLoading: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = "继续上滑加载更多",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
    
    AlertDialog(
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
                        FilterChip(
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
                        FilterChip(
                            onClick = { rewardAmount = amount.toString() },
                            label = { Text("${amount}币") },
                            selected = rewardAmount == amount.toString(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                OutlinedTextField(
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
            TextButton(
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
            TextButton(onClick = onDismiss) {
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
    
    AlertDialog(
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
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
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
                        Button(
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
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
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
                        Button(
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
                
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        onDismiss()
                        onShareToFriend()
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
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
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

/* -------------------------------------------------------------------------- */
/*  详情页顶栏                                                                */
/* -------------------------------------------------------------------------- */

/** 顶栏里按钮/胶囊的高度。 */
private val DetailChromeHeight = 40.dp

/**
 * 文章详情的顶栏，机制跟分区页一致：**玻璃底 + 随滚动收起**。
 *
 * 三块内容：圆形返回、分区名胶囊（点它进分区）、更多菜单。
 * 「只看楼主」已经移到评论区 —— 它筛的是评论，放在顶栏离内容太远。
 *
 * 背景是一层从上往下淡出到透明的玻璃，正文穿过去是柔的，
 * 不会再被一条实心通栏压出一刀切的灰带。
 *
 * @param hiddenFraction 0 显示、1 收起，由滚动驱动
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostDetailTopBar(
    boardName: String,
    boardAvatar: String?,
    hiddenFraction: () -> Float,
    onBackClick: () -> Unit,
    onBoardClick: () -> Unit,
    menuExpanded: Boolean,
    onMenuClick: () -> Unit,
    onMenuDismiss: () -> Unit,
    menuContent: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier
) {
    val statusBar = WindowInsets.statusBars
    val page = LocalPageScroll.current

    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            Box(
                modifier = Modifier
                    .layoutId(DetailBarLayer.Background)
                    .detailGlassBackground()
            )
            Column(
                modifier = Modifier
                    .layoutId(DetailBarLayer.Foreground)
                    .windowInsetsPadding(statusBar)
                    .onGloballyPositioned { coords ->
                        if (page != null) {
                            val row = page.boundsOf(coords)
                            page.barRowTop = row.top
                            page.barRowBottom = row.bottom
                        }
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(DetailBarRowHeight)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DetailNavButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // 分区名胶囊
                    Surface(
                        onClick = onBoardClick,
                        modifier = Modifier.weight(1f),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.86f),
                        shadowElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(DetailChromeHeight)
                                .padding(start = 6.dp, end = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = ImageUtils.createBoardImageRequest(
                                    context = LocalContext.current,
                                    url = boardAvatar.orEmpty()
                                ),
                                contentDescription = boardName,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = boardName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // 更多菜单
                    DetailNavButton(onClick = onMenuClick) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "更多操作",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = onMenuDismiss,
                            content = menuContent
                        )
                    }
                }
            }
        }
    ) { measurables, constraints ->
        val loose = constraints.copy(minHeight = 0)
        val foreground = measurables.first { it.layoutId == DetailBarLayer.Foreground }.measure(loose)
        val statusTop = statusBar.getTop(this)
        val hidden = ((foreground.height - statusTop) * hiddenFraction()).toInt()
        val background = measurables.first { it.layoutId == DetailBarLayer.Background }
            .measure(Constraints.fixed(foreground.width, foreground.height + DetailBarEdgeExtend.roundToPx()))

        layout(foreground.width, foreground.height) {
            background.place(0, 0)
            if (hiddenFraction() < 1f) {
                foreground.placeWithLayer(0, -hidden) { alpha = 1f - hiddenFraction() }
            }
        }
    }
}

/** 详情页顶栏的两层。 */
private enum class DetailBarLayer { Background, Foreground }

/** 详情页顶栏行高 —— 跟分区页保持一致，收起距离才对得上。 */
private val DetailBarRowHeight = 56.dp

/** 玻璃往下伸出顶栏多少，让雾在硬边之前就开始。 */
private val DetailBarEdgeExtend = 12.dp

/**
 * 详情页顶栏的玻璃底：从上往下淡出到透明。
 * 和分区页是同一种做法，两页切换时观感一致。
 */
@Composable
private fun Modifier.detailGlassBackground(): Modifier {
    val base = MaterialTheme.colorScheme.surface
    return this.drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                0f to base.copy(alpha = 0.92f),
                0.65f to base.copy(alpha = 0.70f),
                1f to base.copy(alpha = 0f)
            )
        )
    }
}

/** 详情页顶栏上的圆形按钮。 */
@Composable
private fun DetailNavButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(DetailChromeHeight),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.86f),
        shadowElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
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
    var isSearchExpanded by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var searchResultCount by remember { mutableStateOf(0) }
    // 「只看楼主」：筛的是本篇评论，楼主 = 这篇文章的作者（post.senderId），不是分区创建者
    var filterLzOnly by rememberSaveable { mutableStateOf(false) }
    // 详情页主列表，点底部「评论」按钮时滚到评论标题那一项
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // 整页滚动状态：顶栏和内容共享，顶栏靠它算收起进度
    val pageScroll = rememberPageScrollState()

    // 顶栏收起进度：内容往上滑它走向 1，往回滑一点立刻回来。
    // 不设 enabled —— 详情页第一屏就是正文，往上滑就该收，没有「头部还没走」这回事。
    val detailAway = rememberScrollProgress(
        away = DetailBarRowHeight,
        page = pageScroll
    )
    
    // 打赏对话框状态
    var showRewardDialog by remember { mutableStateOf(false) }
    
    // 分享对话框状态
    var showShareDialog by remember { mutableStateOf(false) }
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
    
    
    // 顶栏已改成三件式悬浮块，没有可折叠的 TopAppBar，也就不需要 scrollBehavior：
    // 留着一个没绑定控件的 nestedScroll 反而会吞掉列表的滑动事件。

    // 「只看楼主」：只留楼主（这篇的作者）本人的评论。
    // 楼层号按原始顺序算，过滤后不重新编号，看起来仍是 1L、5L、17L 这样跳着走。
    val visibleComments = remember(commentListState.comments, filterLzOnly, postDetailState.post?.senderId) {
        val all = commentListState.comments
        val lzId = postDetailState.post?.senderId
        if (!filterLzOnly || lzId.isNullOrEmpty()) all
        else all.filter { it.senderId == lzId }
    }

    // 每条例子的原始楼层（在全部评论里的下标 + 1），过滤后仍用这个号
    val floorIndexOf = remember(commentListState.comments) {
        commentListState.comments.mapIndexed { index, c -> c.id to index + 1 }.toMap()
    }

    Scaffold(
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
                        // 评论区现在在正文下面，点评论就滚过去（评论标题是第 2 项：0=正文，1=标题）
                        coroutineScope.launch {
                            listState.animateScrollToItem(1)
                        }
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
        // 顶栏：玻璃底 + 随滚动收起，跟分区页同一套机制。
        // 「只看楼主」已移到评论区（它筛的是评论，放顶栏离内容太远）。
        topBar = {
            PostDetailTopBar(
                boardName = postDetailState.board?.name
                    ?: postDetailState.post?.group?.name
                    ?: "文章详情",
                boardAvatar = postDetailState.board?.avatar
                    ?: postDetailState.post?.group?.avatarUrl,
                hiddenFraction = { detailAway.value },
                onBackClick = onBackClick,
                onBoardClick = {
                    postDetailState.board?.let { board ->
                        val intent = Intent(context, BoardDetailActivity::class.java).apply {
                            putExtra("board_id", board.id)
                            putExtra("board_name", board.name)
                            putExtra("token", if (isTokenLoaded) currentToken else "")
                        }
                        context.startActivity(intent)
                    }
                },
                menuExpanded = topMenuExpanded,
                onMenuClick = { topMenuExpanded = true },
                onMenuDismiss = { topMenuExpanded = false },
                menuContent = {
                    DropdownMenuItem(
                        text = { Text("分享") },
                        onClick = {
                            topMenuExpanded = false
                            showShareDialog = true
                        }
                    )
                    if (canManagePost) {
                        DropdownMenuItem(
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
                        DropdownMenuItem(
                            text = { Text("移动文章") },
                            onClick = {
                                topMenuExpanded = false
                                showMoveBoardDialog = true
                            }
                        )
                        DropdownMenuItem(
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
                                val post = postDetailState.post ?: return@DropdownMenuItem
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
                        DropdownMenuItem(
                            text = { Text("删除文章", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                topMenuExpanded = false
                                showDeleteDialog = true
                            }
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                // 只让出顶部（标题栏）的空间，底部不再让位：
                // 互动条现在是"真悬浮"，文章内容要从它下面滚过去
                .padding(
                    top = paddingValues.calculateTopPadding(),
                    start = paddingValues.calculateStartPadding(LocalLayoutDirection.current),
                    end = paddingValues.calculateEndPadding(LocalLayoutDirection.current)
                )
        ) {
            
            // 错误提示
            postDetailState.error?.let { error ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
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
                    CircularProgressIndicator()
                }
            } else {
                postDetailState.post?.let { post ->
                    PullToRefreshBox(
                        isRefreshing = postDetailState.isRefreshing,
                        onRefresh = { viewModel.refreshPostDetailWithToken(postId) },
                        state = rememberPullToRefreshState(),
                        modifier = Modifier.weight(1f)
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                // 内容上报滚动位置给顶栏
                                .pageScroll(listState),
                            // 顶部留出顶栏高度，正文从栏下开始；
                            // 底部多留出药丸互动条的高度，滚到底时最后一段不会被按钮压住。
                            //
                            // **左右不留 padding**：评论区的楼层要自己控制左边距
                            // （楼栏从 4dp 起、正文跟在楼栏后面），外层再给 16dp
                            // 就会让评论比正文多缩一截，看着像"整块往右偏移"。
                            // 正文和评论标题各自加 16dp，各自负责自己的对齐。
                            contentPadding = PaddingValues(
                                top = 16.dp,
                                bottom = 140.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
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

                            // ===== 评论区：直接接在文章下方 =====
                            // 「只看楼主」时只留楼主本人的评论。楼主就是这篇的作者，
                            // 楼层号在所有评论里的原始位置不变（第几条就是几楼），
                            // 所以过滤后不会重新编号，看起来仍是 1L、5L、17L 这样跳着走。
                            item(key = "comment_header") {
                                CommentSectionHeader(
                                    total = if (commentListState.total > 0) {
                                        commentListState.total
                                    } else {
                                        commentListState.comments.size
                                    },
                                    visibleCount = visibleComments.size,
                                    filterLzOnly = filterLzOnly,
                                    onFilterChange = { filterLzOnly = it }
                                )
                            }

                            item(key = "comment_input") {
                                CommentInputRow(
                                    value = commentText,
                                    onValueChange = { commentText = it },
                                    onSend = {
                                        if (commentText.isNotBlank()) {
                                            viewModel.commentPostWithToken(postId, commentText)
                                            commentText = ""
                                        }
                                    }
                                )
                            }

                            if (visibleComments.isEmpty() && !commentListState.isLoading) {
                                item(key = "comment_empty") {
                                    CommentEmptyState(
                                        lzOnly = filterLzOnly,
                                    )
                                }
                            }

                            itemsIndexed(
                                items = visibleComments,
                                key = { _, comment -> "comment_${comment.id}" }
                            ) { index, comment ->
                                CommentItem(
                                    comment = comment,
                                    // 楼层 = 它在全部评论里的原始位置 + 1，过滤时也保持原楼号
                                    floor = floorIndexOf[comment.id] ?: (index + 1),
                                    // 第一层的竖线从头像中心开始，后面的层从顶部接上
                                    startsRule = index == 0,
                                    isLz = comment.senderId == post.senderId,
                                    canDelete = comment.senderId == postDetailState.currentUserId ||
                                        postDetailState.isAdmin == 1 ||
                                        commentListState.isAdmin == 1,
                                    onLikeClick = { commentId ->
                                        viewModel.likeCommentWithToken(postId, commentId)
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

                            if (commentListState.isLoading && commentListState.comments.isEmpty()) {
                                item(key = "comment_loading") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                }
                            }

                            if (commentListState.hasMore) {
                                item(key = "comment_load_more") {
                                    // 评论接口不支持按作者过滤（CommentListRequest 只有 postId/size/page），
                                    // 「只看楼主」是前端过滤：某一页里楼主可能一条都没有，
                                    // 所以这里不依赖可见列表，只要还有下一页就继续拉，
                                    // 否则翻到一页全是别人时列表会显得「卡住不动」。
                                    LaunchedEffect(commentListState.currentPage, filterLzOnly) {
                                        if (!commentListState.isLoading) {
                                            viewModel.loadMoreCommentsWithToken(postId)
                                        }
                                    }
                                    val pending = filterLzOnly && visibleComments.isEmpty()
                                    CommentLoadMoreFooter(
                                        isLoading = commentListState.isLoading || pending
                                    )
                                }
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
                    onShareToFriend = {
                        // 分享给好友：打开「选择会话」Activity（不再是 bottom sheet）
                        postDetailState.post?.let { post ->
                            SendToChatActivity.start(
                                context = context,
                                content = SendToChatContent.Post(
                                    postId = post.id.toString(),
                                    title = post.title,
                                    content = post.content,
                                    postType = post.contentType.toString()
                                )
                            )
                        }
                    }
                )
            }

            pendingDeleteComment?.let { comment ->
                AlertDialog(
                    onDismissRequest = { pendingDeleteComment = null },
                    title = { Text("删除评论") },
                    text = { Text("确定要删除这条评论吗？删除后无法恢复。") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.deleteCommentWithToken(postId, comment.id)
                                pendingDeleteComment = null
                            }
                        ) {
                            Text("删除", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingDeleteComment = null }) {
                            Text("取消")
                        }
                    }
                )
            }
            
            if (showMoveBoardDialog) {
                AlertDialog(
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
                                    CircularProgressIndicator()
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
                        TextButton(onClick = { showMoveBoardDialog = false }) {
                            Text("关闭")
                        }
                    }
                )
            }

            pendingMoveBoard?.let { board ->
                AlertDialog(
                    onDismissRequest = { pendingMoveBoard = null },
                    title = { Text("确认移动") },
                    text = { Text("确认将文章移动到「${board.name}」吗？") },
                    confirmButton = {
                        TextButton(
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
                        TextButton(onClick = { pendingMoveBoard = null }) {
                            Text("取消")
                        }
                    }
                )
            }
            
            // 删除确认对话框
            if (showDeleteDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = { Text("删除文章") },
                    text = { Text("确定要删除这篇文章吗？删除后无法恢复。") },
                    confirmButton = {
                        TextButton(
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
                        TextButton(onClick = { showDeleteDialog = false }) {
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(target.title)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                var reasonMenuExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = reasonMenuExpanded,
                    onExpandedChange = { reasonMenuExpanded = !reasonMenuExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedReason,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("举报原因") },
                        placeholder = { Text("请选择举报原因") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = reasonMenuExpanded)
                        },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = reasonMenuExpanded,
                        onDismissRequest = { reasonMenuExpanded = false }
                    ) {
                        COMMUNITY_REPORT_REASONS.forEach { reason ->
                            DropdownMenuItem(
                                text = { Text(reason) },
                                onClick = {
                                    selectedReason = reason
                                    reasonMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
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

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { imagePickerLauncher.launch("image/*") },
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
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
            TextButton(
                onClick = {
                    if (selectedReason.isBlank()) {
                        Toast.makeText(context, "请选择举报原因", Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    onSubmit(selectedReason, reportContent, selectedImageUri)
                },
                enabled = !isSubmitting
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                } else {
                    Text("提交举报")
                }
            }
        },
        dismissButton = {
            TextButton(
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
