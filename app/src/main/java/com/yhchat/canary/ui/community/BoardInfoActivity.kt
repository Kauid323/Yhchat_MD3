@file:OptIn(ExperimentalMaterial3Api::class)

package com.yhchat.canary.ui.community

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Paid
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.yhchat.canary.data.di.RepositoryFactory
import com.yhchat.canary.data.model.BaMember
import com.yhchat.canary.data.model.CommunityBoard
import com.yhchat.canary.ui.base.BaseActivity
import com.yhchat.canary.ui.components.ImageUtils
import com.yhchat.canary.ui.settings.SettingsGroup
import com.yhchat.canary.ui.theme.YhchatCanaryTheme

/**
 * 分区信息页。
 *
 * 和 [BoardDetailActivity] 分开：那个是「文章流」，这个是「这个分区是什么」——
 * 谁建的、谁在管、能不能发言。混在一页会让文章流永远排在一个巨大的信息块后面。
 */
class BoardInfoActivity : BaseActivity() {

    companion object {
        private const val EXTRA_BOARD_ID = "board_id"
        private const val EXTRA_BOARD_NAME = "board_name"
        private const val EXTRA_TOKEN = "token"

        fun start(
            context: Context,
            boardId: Int,
            boardName: String,
            token: String
        ) {
            context.startActivity(
                Intent(context, BoardInfoActivity::class.java).apply {
                    putExtra(EXTRA_BOARD_ID, boardId)
                    putExtra(EXTRA_BOARD_NAME, boardName)
                    putExtra(EXTRA_TOKEN, token)
                }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val boardId = intent.getIntExtra(EXTRA_BOARD_ID, 0)
        val boardName = intent.getStringExtra(EXTRA_BOARD_NAME).orEmpty()
        val tokenFromIntent = intent.getStringExtra(EXTRA_TOKEN).orEmpty()

        setContent {
            YhchatCanaryTheme {
                val infoViewModel: BoardInfoViewModel = viewModel {
                    BoardInfoViewModel(
                        communityRepository = RepositoryFactory.getCommunityRepository(this@BoardInfoActivity),
                        tokenRepository = RepositoryFactory.getTokenRepository(this@BoardInfoActivity)
                    )
                }
                // 管理弹窗要的是社区主 ViewModel（改分区/删分区都在它上面），
                // 单独建一个实例，不和列表页共享状态，避免改完之后列表还显示旧名字
                val manageViewModel: CommunityViewModel = viewModel {
                    CommunityViewModel(
                        communityRepository = RepositoryFactory.getCommunityRepository(this@BoardInfoActivity)
                    )
                }

                BoardInfoScreen(
                    boardId = boardId,
                    boardName = boardName,
                    token = tokenFromIntent,
                    viewModel = infoViewModel,
                    manageViewModel = manageViewModel,
                    onBack = { finish() }
                )
            }
        }
    }
}

@Composable
private fun BoardInfoScreen(
    boardId: Int,
    boardName: String,
    token: String,
    viewModel: BoardInfoViewModel,
    manageViewModel: CommunityViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    var showManageSheet by remember { mutableStateOf(false) }

    LaunchedEffect(boardId, token) {
        if (boardId > 0 && token.isNotBlank()) {
            viewModel.load(token, boardId)
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        rememberTopAppBarState()
    )

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = state.board?.name ?: boardName.ifBlank { "分区信息" },
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                state.isLoading && state.board == null -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                state.error != null && state.board == null -> {
                    ErrorState(
                        message = state.error.orEmpty(),
                        onRetry = { viewModel.load(token, boardId) },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                state.board != null -> {
                    BoardInfoContent(
                        board = state.board!!,
                        state = state,
                        onManageClick = { showManageSheet = true }
                    )
                }
            }
        }
    }

    val board = state.board
    if (showManageSheet && board != null) {
        ManageBoardSheet(
            token = token,
            board = board,
            viewModel = manageViewModel,
            onDismiss = { showManageSheet = false },
            onBoardUpdated = { updated -> viewModel.updateBoard(updated) }
        )
    }
}

@Composable
private fun BoardInfoContent(
    board: CommunityBoard,
    state: BoardInfoState,
    onManageClick: () -> Unit
) {
    // 显式标出 @Composable 类型：SettingsGroup 的 items 是
    // List<@Composable () -> Unit>，靠 map 的返回值推断拿不到这个注解，
    // 里面的 MemberRow(...) 会被当成非 Composable 上下文而报错
    val creatorItems: List<@Composable () -> Unit> = listOf(
        {
            val creator = state.creator
            if (creator != null) {
                MemberRow(
                    member = creator,
                    roleLabel = "创建者",
                    isMe = creator.userId == state.currentUserId
                )
            } else {
                UnknownMemberRow()
            }
        }
    )

    val managerItems: List<@Composable () -> Unit> = state.managers.map { manager ->
        {
            MemberRow(
                member = manager,
                roleLabel = "管理者",
                isMe = manager.userId == state.currentUserId
            )
        }
    }

    val infoItems: List<@Composable () -> Unit> = listOf(
        {
            InfoRow(
                icon = Icons.Outlined.DateRange,
                label = "创建时间",
                value = board.createTimeText.ifBlank { "—" }
            )
        },
        {
            InfoRow(
                icon = Icons.Outlined.Visibility,
                label = "可见范围",
                value = when (board.visibleRange) {
                    1 -> "仅分区所有者可见"
                    else -> "所有人可见"
                }
            )
        },
        {
            InfoRow(
                icon = Icons.Outlined.EditNote,
                label = "发布权限",
                value = when (board.publishAuthority) {
                    1 -> "仅所有者发文，任何人可评论"
                    2 -> "仅所有者发文和评论"
                    else -> "所有人都可以发文和评论"
                }
            )
        },
        {
            InfoRow(
                icon = Icons.Outlined.Paid,
                label = "加入门槛",
                value = if (board.needCoin > 0) "${board.needCoin} 金币" else "免费加入"
            )
        }
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item { BoardHero(board) }

        item {
            SettingsGroup(title = "创建者", items = creatorItems)
        }

        if (managerItems.isNotEmpty()) {
            item {
                SettingsGroup(title = "管理者", items = managerItems)
            }
        }

        item {
            SettingsGroup(title = "分区信息", items = infoItems)
        }

        // 只有创建者/管理者才看得到这个按钮。位置放在信息之后而不是顶栏：
        // 这是低频的危险操作入口，放在首屏最显眼的地方容易误触
        if (state.canManage) {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    FilledTonalButton(
                        onClick = onManageClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("管理分区", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/**
 * 顶部信息块。
 *
 * 用一整块 28dp 圆角的 tonal Surface 承载，而不是「头像 + 一堆散落的 Text」：
 * Expressive 的层级是靠大色块和圆角表达的，散着放会显得像调试界面。
 */
@Composable
private fun BoardHero(board: CommunityBoard) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = ImageUtils.createBoardImageRequest(
                        context = LocalContext.current,
                        url = board.avatar
                    ),
                    contentDescription = board.name,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(24.dp)),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = board.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Pill(
                            text = "ID ${board.id}",
                            container = MaterialTheme.colorScheme.surfaceContainerHighest,
                            content = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Pill(
                            text = if (board.isFollowed == "1") "已关注" else "未关注",
                            container = if (board.isFollowed == "1") {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                            content = if (board.isFollowed == "1") {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                StatCell(
                    value = board.memberNum.toString(),
                    label = "成员",
                    modifier = Modifier.weight(1f)
                )
                StatCell(
                    value = board.postNum.toString(),
                    label = "文章",
                    modifier = Modifier.weight(1f)
                )
                StatCell(
                    value = board.groupNum.toString(),
                    label = "群聊",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatCell(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 小胶囊标签。用 Surface + Text 拼，比 Chip 轻，也不会带点击涟漪 */
@Composable
private fun Pill(
    text: String,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color
) {
    Surface(
        shape = CircleShape,
        color = container
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/**
 * 创建者/管理者的一行。
 *
 * 标「我」是为了让用户一眼确认自己的权限从哪来 —— 尤其是管理者有十几个的时候，
 * 找不到自己的 id 会怀疑按钮是不是错的。
 */
@Composable
private fun MemberRow(
    member: BaMember,
    roleLabel: String,
    isMe: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ImageUtils.createAvatarImageRequest(
                context = LocalContext.current,
                url = member.avatarUrl
            ),
            contentDescription = member.nickname,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = member.nickname,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "ID ${member.userId}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (isMe) {
            Spacer(modifier = Modifier.width(8.dp))
            Pill(
                text = "我",
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Pill(
            text = roleLabel,
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun UnknownMemberRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Groups,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "创建者信息不可见",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onRetry) {
            Text("重试")
        }
    }
}
