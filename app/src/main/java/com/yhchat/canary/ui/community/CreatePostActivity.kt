@file:OptIn(ExperimentalMaterial3Api::class)

package com.yhchat.canary.ui.community

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Drafts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import com.yhchat.canary.R
import com.yhchat.canary.data.di.RepositoryFactory
import com.yhchat.canary.ui.theme.YhchatCanaryTheme

/**
 * 创建文章Activity
 */
class CreatePostActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.yhchat.canary.ui.base.SystemBarUtils.setupTransparentSystemBars(this)
        
        val boardId = intent.getIntExtra("board_id", 0)
        val boardName = intent.getStringExtra("board_name").orEmpty()
        val token = intent.getStringExtra("token") ?: ""
        
        // 草稿相关参数
        val draftId = intent.getStringExtra("draft_id")
        val draftTitle = intent.getStringExtra("draft_title") ?: ""
        val draftContent = intent.getStringExtra("draft_content") ?: ""
        val draftMarkdownMode = intent.getBooleanExtra("draft_markdown_mode", false)
        
        setContent {
            YhchatCanaryTheme {
                SetSystemNavigationBarColor()
                val viewModel: CreatePostViewModel = viewModel {
                    CreatePostViewModel(
                        communityRepository = RepositoryFactory.getCommunityRepository(this@CreatePostActivity),
                        tokenRepository = RepositoryFactory.getTokenRepository(this@CreatePostActivity)
                    )
                }
                val draftViewModel: CommunityDraftViewModel = viewModel {
                    CommunityDraftViewModel(
                        communityRepository = RepositoryFactory.getCommunityRepository(this@CreatePostActivity)
                    )
                }
                
                CreatePostScreen(
                    boardId = boardId,
                    boardName = boardName,
                    token = token,
                    draftId = draftId,
                    viewModel = viewModel,
                    draftViewModel = draftViewModel,
                    onBackClick = { finish() },
                    onPostCreated = { finish() },
                    onDraftBoxClick = {
                        // 跳转到草稿箱Activity
                        val intent = Intent(this@CreatePostActivity, DraftBoxActivity::class.java).apply {
                            putExtra("token", token)
                        }
                        startActivity(intent)
                    },
                    draftTitle = draftTitle,
                    draftContent = draftContent,
                    draftMarkdownMode = draftMarkdownMode,
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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                var flags = window.decorView.systemUiVisibility
                flags = if (isLightTheme) {
                    flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                } else {
                    flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    flags = if (isLightTheme) {
                        flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                    } else {
                        flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                    }
                }
                window.decorView.systemUiVisibility = flags
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val appearance = if (isLightTheme) {
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                } else {
                    0
                }
                window.insetsController?.setSystemBarsAppearance(
                    appearance,
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                )
            }
        }
    }
}

/**
 * 创建文章界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePostScreen(
    boardId: Int,
    boardName: String,
    token: String,
    draftId: String? = null,
    viewModel: CreatePostViewModel,
    draftViewModel: CommunityDraftViewModel,
    onBackClick: () -> Unit,
    onPostCreated: () -> Unit,
    onDraftBoxClick: () -> Unit,
    draftTitle: String = "",
    draftContent: String = "",
    draftMarkdownMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    var title by remember { mutableStateOf(draftTitle) }
    // 正文用 TextFieldValue 而不是 String：Markdown 工具栏要往光标位置插符号，
    // 纯 String 拿不到 selection，只能追加到末尾，那样工具栏基本没法用。
    var content by remember { mutableStateOf(TextFieldValue(draftContent)) }
    var isMarkdownMode by remember { mutableStateOf(draftMarkdownMode) }

    // 插图上传要 context。Compose 里必须从 LocalContext 取，
    // 之前直接写了 `context = context`，但这个作用域里根本没有这个变量，
    // Kotlin 就把它当成了对某个叫 context() 的函数的调用。
    val context = androidx.compose.ui.platform.LocalContext.current

    // 输入框是否已获得焦点。Markdown 工具栏只在编辑正文时贴在框上面，
    // 没聚焦时它不该占着屏幕。
    var contentFocused by remember { mutableStateOf(false) }

    // 插图相关：点图片按钮时的光标位置、上传进度
    var pendingInsertPosition by remember { mutableStateOf<Int?>(null) }
    val insertState by viewModel.insertImagesState.collectAsState()

    // 滚动状态：输入时把内容滚到底，否则写满一屏之后新输入的文字在屏幕外，
    // 得手动划一下才看得到
    val scrollState = rememberScrollState()
    val scrollScope = rememberCoroutineScope()

    // 系统图片选择器（支持多选）。
    // 用 PickMultipleVisualMedia 而不是老的 GetContent：前者是系统照片选择器，
    // 不需要任何读取权限，低版本也能用（走兼容实现）。
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 9),
        onResult = { uris ->
            if (uris.isEmpty()) return@rememberLauncherForActivityResult
            val anchor = pendingInsertPosition
            viewModel.uploadImages(
                context = context,
                uriList = uris
            ) { snippets ->
                // 全部传完后一次性插入，避免正文里图片一个个冒出来打乱顺序
                var updated = content
                snippets.forEach { snippet ->
                    updated = insertAtCursor(updated, snippet, anchor)
                }
                content = updated
                pendingInsertPosition = null
                // 插完把焦点还给输入框，方便继续写
                contentFocused = true
            }
        }
    )
    
    val createPostState by viewModel.createPostState.collectAsState()
    val draftSaveState by draftViewModel.draftSaveState.collectAsState()
    
    // 控制退出确认对话框
    var showExitDialog by remember { mutableStateOf(false) }
    var pendingExitAfterDraftSave by remember { mutableStateOf(false) }
    val displayBoardName = boardName.ifBlank { "发布文章" }

    // 本页有两种进入方式，靠 draftId 区分：
    //   - 有 draftId：从「我的文章」点草稿进来，是在接着写草稿
    //   - 无 draftId：从分区点「发布」进来，是全新发布
    // 两个地方的区别只有三处：标题文案、发布后是否删原草稿、返回时草稿按钮的措辞。
    // 集中成一个变量，避免这三处各判一次、以后改漏。
    val isEditingDraft = !draftId.isNullOrBlank()
    
    // 监听创建结果
    LaunchedEffect(createPostState.isSuccess) {
        if (createPostState.isSuccess) {
            // 草稿发布成功后就删掉，否则「我的文章」里会同时出现
            // 一条草稿和一条新发布的文章，看起来像发重了
            if (isEditingDraft && draftId != null) {
                draftViewModel.deleteDraft(token, draftId)
            }
            onPostCreated()
        }
    }

    LaunchedEffect(draftSaveState.savedDraft?.id, draftSaveState.error) {
        if (pendingExitAfterDraftSave && draftSaveState.savedDraft != null) {
            pendingExitAfterDraftSave = false
            showExitDialog = false
            draftViewModel.clearDraftSaveState()
            onBackClick()
        }
        if (pendingExitAfterDraftSave && draftSaveState.error != null) {
            pendingExitAfterDraftSave = false
        }
    }
    
    // 处理返回按键
    BackHandler {
        if (title.isNotBlank() || content.text.isNotBlank()) {
            showExitDialog = true
        } else {
            onBackClick()
        }
    }
    
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
        TopAppBar(
            title = {
                Text(
                    // 有 draftId 说明是接着写草稿，标题要说清楚，否则用户会以为
                    // 自己点进了一个新的发布页、担心把草稿弄丢
                    text = if (isEditingDraft) {
                        "编辑草稿"
                    } else {
                        "发布到 $displayBoardName"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = {
                    if (title.isNotBlank() || content.text.isNotBlank()) {
                        showExitDialog = true
                    } else {
                        onBackClick()
                    }
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回"
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = { isMarkdownMode = !isMarkdownMode }
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (isMarkdownMode) R.drawable.ic_markdown else R.drawable.ic_markdown_outline
                        ),
                        contentDescription = "Markdown"
                    )
                }

                // 草稿箱
                IconButton(
                    onClick = onDraftBoxClick
                ) {
                    Icon(
                        imageVector = Icons.Default.Drafts,
                        contentDescription = "草稿箱"
                    )
                }
                
                // 发布按钮
                IconButton(
                    onClick = {
                        if (title.isNotBlank() && content.text.isNotBlank()) {
                            viewModel.createPost(
                                token = token,
                                boardId = boardId,
                                title = title.trim(),
                                content = content.text.trim(),
                                contentType = if (isMarkdownMode) 2 else 1
                            )
                        }
                    },
                    enabled = title.isNotBlank() && content.text.isNotBlank() && !createPostState.isLoading
                ) {
                    if (createPostState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "发布"
                        )
                    }
                }
            },
            scrollBehavior = scrollBehavior,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                scrolledContainerColor = MaterialTheme.colorScheme.background
            )
        )
        }
    ) { innerPadding ->
        // 用 Box 承载「内容 + 底部悬浮工具栏」两层。
        //
        // 关键在 imePadding：键盘弹出时它会把整个 Box 的可用高度压缩，
        // 底部对齐的工具栏就自然"浮"到键盘上方 —— 这正是用户要的跟随效果。
        // 项目里 CommentInputBar 用的也是 WindowInsets.ime 这一套。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // 给底部工具栏留出高度，否则正文最后几行会被工具栏盖住
                .padding(
                    bottom = if (isMarkdownMode && contentFocused) MarkdownToolbarHeight else 0.dp
                )
        ) {
        
        // 错误提示
        createPostState.error?.let { error ->
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

        draftSaveState.error?.let { error ->
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
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(scrollState)
                // 左右各 16dp 保持版式；上下收窄，避免和工具栏之间空一段
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("文章标题") },
                placeholder = { Text("请输入文章标题...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Column(modifier = Modifier.fillMaxWidth()) {
                    // 插图时的进度条：图片上传可能要几秒，没有任何反馈会让人以为没点上
                    if (insertState.isUploading) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "正在上传图片 ${insertState.uploaded}/${insertState.total}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { insertState.fraction },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // 正文输入：无边框、默认高度，内容变多自动长高，且不滚动截断。
                    //
                    // 用 BasicTextField + 自己的装饰，而不是 OutlinedTextField：
                    //   - OutlinedTextField 自带描边和 label，想要"无边框"必须把
                    //     colors 里的 border/label 全设成透明，既别扭又容易漏；
                    //   - 它内部是裁切滚动的，长文本来回滚动，作者看不到全文，
                    //     而写文章时"一眼看到全文"比"固定高度省地方"重要得多。
                    BasicTextField(
                        value = content,
                        onValueChange = { new ->
                            content = new
                            // 每次输入后滚到底部，让刚写的那行保持可见。
                            //
                            // 为什么用 scrollState 而不是 LazyColumn：
                            // 这里其实只有一个会无限长高的输入框，不是一堆独立条目。
                            // LazyColumn 只能"把某个 item 顶部对齐视口顶部"，
                            // 没有"滚到底"这个概念；而 ScrollState 的 maxValue
                            // 就是内容底边，scrollTo 到它才是真正的"跟着输入走"。
                            scrollScope.launch {
                                // 先让出一帧：输入框高度变化要到下一次布局才反映到
                                // maxValue 上，不 yield 的话这里读到的是改之前的旧高度，
                                // 结果就是"永远差一截"，写长文时还是看不到最后一行。
                                yield()
                                scrollState.animateScrollTo(scrollState.maxValue)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { contentFocused = it.isFocused },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(
                            MaterialTheme.colorScheme.primary
                        ),
                        // 不设 maxLines / 不套固定高度：内容多高它就多高，
                        // 外层 Column + verticalScroll 负责滚动，全文始终能看全
                        minLines = 8,
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                if (content.text.isEmpty()) {
                                    Text(
                                        text = if (isMarkdownMode)
                                            "支持 Markdown 语法，如：\n# 标题\n**粗体**  *斜体*\n- 列表\n`代码`"
                                        else
                                            "请输入文章内容…",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }
            }
        }

        // 底部悬浮 Markdown 工具栏。
        //
        // 必须是 Box 的直接子项才能用 Modifier.align —— 上一版把它写在 Column 里面，
        // ColumnScope 里没有 align(BottomCenter)，编译报 "cannot be called in this
        // context with an implicit receiver"。所以 Column 要在这里先闭合。
        //
        // 它盖在内容之上（Box 的后一个子项），所以是"最上层"；
        // 外层 Box 上的 imePadding 让它在键盘弹出时自动上提，
        // 停靠在键盘上沿，手感跟输入框附属面板一致。
        if (isMarkdownMode && contentFocused) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                MarkdownToolbar(
                    value = content,
                    onValueChange = { content = it },
                    visible = true,
                    onPickImagesClick = {
                        // 记下点按钮这一刻的光标位置。
                        // 图片是异步上传的，等传完光标可能已经变了（甚至失焦重置），
                        // 所以必须现在就把位置存下来，插入时按它来。
                        pendingInsertPosition = content.selection.min
                        imagePickerLauncher.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    }
                )
            }
        }
        }
    }
    
    // 退出确认对话框
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = {
                Text(if (isEditingDraft) "保存草稿修改" else "保存草稿")
            },
            text = {
                Text(
                    if (isEditingDraft) {
                        "您对这份草稿的修改还没保存，是否保存后退出"
                    } else {
                        "您有未保存的内容，是否保存为草稿"
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (!draftSaveState.isSaving && (title.isNotBlank() || content.text.isNotBlank())) {
                            pendingExitAfterDraftSave = true
                            draftViewModel.saveDraft(
                                token = token,
                                boardId = boardId,
                                boardName = boardName,
                                title = title.trim(),
                                content = content.text.trim(),
                                contentType = if (isMarkdownMode) 2 else 1,
                                draftId = draftId
                            )
                        }
                    },
                    enabled = !draftSaveState.isSaving
                ) {
                    if (draftSaveState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("保存")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (!draftSaveState.isSaving) {
                            showExitDialog = false
                            onBackClick()
                        }
                    },
                    enabled = !draftSaveState.isSaving
                ) {
                    Text("不保存")
                }
            }
        )
    }
}
