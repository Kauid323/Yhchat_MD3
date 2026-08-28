package com.yhchat.canary.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Recommend
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import com.yhchat.canary.ui.adaptive.YhIcon as Icon
import com.yhchat.canary.ui.adaptive.YhText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yhchat.canary.ui.adaptive.YhAlertDialog
import com.yhchat.canary.ui.adaptive.YhIconButton
import com.yhchat.canary.ui.adaptive.YhRadioButton
import com.yhchat.canary.ui.adaptive.YhScaffold
import com.yhchat.canary.ui.adaptive.YhTextButton
import com.yhchat.canary.ui.adaptive.YhTopBar
import com.yhchat.canary.ui.adaptive.yhTopBarNestedScroll
import com.yhchat.canary.ui.base.BaseActivity
import com.yhchat.canary.ui.theme.YhchatCanaryTheme
/**
 * 布局设置Activity - 管理各界面的显示项
 */
class LayoutSettingsActivity : BaseActivity() {
    
    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, LayoutSettingsActivity::class.java))
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            YhchatCanaryTheme {
                LayoutSettingsScreen(
                    onBackClick = { finish() }
                )
            }
        }
    }
}

/**
 * 布局设置界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayoutSettingsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    
    YhScaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            YhTopBar(
                title = "布局设置",
                navigationIcon = {
                    YhIconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .yhTopBarNestedScroll()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // 会话列表设置
            item { ConversationLayoutSettingsGroup(context) }
            
            // 社区设置
            item { CommunityLayoutSettingsGroup(context) }
            
            // 通讯录设置
            item { ContactsLayoutSettingsGroup(context) }
            
            // 发现设置
            item { DiscoverLayoutSettingsGroup(context) }
            
            // 我的设置
            item { ProfileLayoutSettingsGroup(context) }
            
            // 聊天界面设置
            item { ChatLayoutSettingsGroup(context) }
            
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

/**
 * 会话列表布局设置组
 */
@Composable
private fun ConversationLayoutSettingsGroup(context: Context) {
    val prefs = remember { context.getSharedPreferences("layout_settings", Context.MODE_PRIVATE) }
    val displayPrefs = remember { context.getSharedPreferences("display_settings", Context.MODE_PRIVATE) }
    
    var showAvatar by remember { mutableStateOf(prefs.getBoolean("conversation_show_title", true)) }
    var showSearchBox by remember { mutableStateOf(prefs.getBoolean("conversation_show_search", true)) }
    var showAddButton by remember { mutableStateOf(prefs.getBoolean("conversation_show_add", true)) }
    var showUnreadBadge by remember { mutableStateOf(prefs.getBoolean("conversation_show_unread_badge", true)) }
    var showConversationList by remember { mutableStateOf(prefs.getBoolean("conversation_show_list", true)) }
    var showStickyConversations by remember { mutableStateOf(displayPrefs.getBoolean("show_sticky_conversations", true)) }
    var stickyLayoutMode by remember { mutableStateOf(displayPrefs.getString("sticky_layout_mode", "single_line") ?: "single_line") }
    var stickyDisplayMode by remember { mutableStateOf(displayPrefs.getString("sticky_display_mode", "no_name") ?: "no_name") }
    var stickyAlignment by remember { mutableStateOf(displayPrefs.getString("sticky_alignment", "center") ?: "center") }
    var showStickyCollapseToggle by remember { mutableStateOf(displayPrefs.getBoolean("show_sticky_collapse_toggle", true)) }

    var showAddUser by remember { mutableStateOf(prefs.getBoolean("add_menu_show_user", true)) }
    var showAddGroup by remember { mutableStateOf(prefs.getBoolean("add_menu_show_group", true)) }
    var showScan by remember { mutableStateOf(prefs.getBoolean("add_menu_show_scan", true)) }

    var showLayoutModeDialog by remember { mutableStateOf(false) }
    var showDisplayModeDialog by remember { mutableStateOf(false) }
    var showAlignmentDialog by remember { mutableStateOf(false) }

    val layoutModeOptions = listOf(
        "single_line" to "单行展示",
        "grid" to "平铺",
        "list" to "列表展示"
    )
    val displayModeOptions = listOf(
        "no_name" to "不显示会话名称",
        "with_name" to "显示会话名称",
        "no_avatar" to "不显示会话头像"
    )
    val alignmentOptions = listOf(
        "center" to "居中",
        "start" to "靠左对齐",
        "end" to "靠右对齐"
    )

    val currentLayoutModeLabel = layoutModeOptions.find { it.first == stickyLayoutMode }?.second ?: "单行展示"
    val currentDisplayModeLabel = displayModeOptions.find { it.first == stickyDisplayMode }?.second ?: "不显示会话名称"
    val currentAlignmentLabel = alignmentOptions.find { it.first == stickyAlignment }?.second ?: "居中"
    
    SettingsGroup(
        title = "会话列表 - TopAppBar",
        items = listOf(
            {
                SettingsSwitchItem(
                    icon = Icons.Default.Person,
                    title = "头像显示",
                    subtitle = if (showAvatar) "显示顶部左侧头像" else "隐藏顶部左侧头像",
                    checked = showAvatar,
                    onCheckedChange = {
                        showAvatar = it
                        prefs.edit().putBoolean("conversation_show_title", it).apply()
                    }
                )
            },
            {
                SettingsSwitchItem(
                    icon = Icons.Default.Search,
                    title = "搜索框",
                    subtitle = if (showSearchBox) "显示顶部搜索框" else "隐藏顶部搜索框",
                    checked = showSearchBox,
                    onCheckedChange = {
                        showSearchBox = it
                        prefs.edit().putBoolean("conversation_show_search", it).apply()
                    }
                )
            },
            {
                SettingsSwitchItem(
                    icon = Icons.Default.Add,
                    title = "加号按钮",
                    subtitle = if (showAddButton) "显示加号按钮" else "隐藏加号按钮",
                    checked = showAddButton,
                    onCheckedChange = {
                        showAddButton = it
                        prefs.edit().putBoolean("conversation_show_add", it).apply()
                    }
                )
            }
        )
    )
    
    SettingsGroup(
        title = "会话列表 - 加号菜单",
        items = listOf(
            {
                SettingsSwitchItem(
                    icon = Icons.Default.PersonAdd,
                    title = "添加用户/群聊/机器人",
                    subtitle = if (showAddUser) "显示添加选项" else "隐藏添加选项",
                    checked = showAddUser,
                    onCheckedChange = {
                        showAddUser = it
                        prefs.edit().putBoolean("add_menu_show_user", it).apply()
                    }
                )
            },
            {
                SettingsSwitchItem(
                    icon = Icons.Default.GroupAdd,
                    title = "创建群聊/机器人",
                    subtitle = if (showAddGroup) "显示创建选项" else "隐藏创建选项",
                    checked = showAddGroup,
                    onCheckedChange = {
                        showAddGroup = it
                        prefs.edit().putBoolean("add_menu_show_group", it).apply()
                    }
                )
            },
            {
                SettingsSwitchItem(
                    icon = Icons.Default.QrCodeScanner,
                    title = "扫一扫",
                    subtitle = if (showScan) "显示扫一扫" else "隐藏扫一扫",
                    checked = showScan,
                    onCheckedChange = {
                        showScan = it
                        prefs.edit().putBoolean("add_menu_show_scan", it).apply()
                    }
                )
            }
        )
    )
    
    SettingsGroup(
        title = "会话列表 - 置顶设置",
        items = buildList {
            add {
                SettingsSwitchItem(
                    icon = Icons.Default.PushPin,
                    title = "显示置顶会话",
                    subtitle = if (showStickyConversations) "在会话列表中显示置顶会话" else "不显示置顶会话",
                    checked = showStickyConversations,
                    onCheckedChange = {
                        showStickyConversations = it
                        displayPrefs.edit().putBoolean("show_sticky_conversations", it).apply()
                    }
                )
            }
            if (showStickyConversations) {
                add {
                    SettingsItemCell(
                        icon = Icons.Default.ViewCarousel,
                        title = "布局方式",
                        subtitle = "当前: $currentLayoutModeLabel",
                        onClick = { showLayoutModeDialog = true }
                    )
                }
                if (stickyLayoutMode == "list") {
                    add {
                        SettingsSwitchItem(
                            icon = Icons.Default.List,
                            title = "显示折叠/展开按钮",
                            subtitle = if (showStickyCollapseToggle) "显示置顶会话的折叠与展开栏" else "直接展示所有置顶会话",
                            checked = showStickyCollapseToggle,
                            onCheckedChange = {
                                showStickyCollapseToggle = it
                                displayPrefs.edit().putBoolean("show_sticky_collapse_toggle", it).apply()
                            }
                        )
                    }
                } else {
                    add {
                        SettingsItemCell(
                            icon = Icons.Default.Badge,
                            title = "显示内容",
                            subtitle = "当前: $currentDisplayModeLabel",
                            onClick = { showDisplayModeDialog = true }
                        )
                    }
                    if (stickyLayoutMode == "grid") {
                        add {
                            SettingsItemCell(
                                icon = Icons.Default.FormatAlignLeft,
                                title = "平铺对齐方式",
                                subtitle = "当前: $currentAlignmentLabel",
                                onClick = { showAlignmentDialog = true }
                            )
                        }
                    }
                }
            }
        }
    )

    SettingsGroup(
        title = "会话列表 - 显示项",
        items = listOf(
            {
                SettingsSwitchItem(
                    icon = Icons.Default.NotificationsActive,
                    title = "未读消息红点",
                    subtitle = if (showUnreadBadge) "显示未读消息数量" else "隐藏未读消息数量",
                    checked = showUnreadBadge,
                    onCheckedChange = {
                        showUnreadBadge = it
                        prefs.edit().putBoolean("conversation_show_unread_badge", it).apply()
                    }
                )
            },
            {
                SettingsSwitchItem(
                    icon = Icons.Default.List,
                    title = "会话列表",
                    subtitle = if (showConversationList) "显示会话列表" else "隐藏会话列表",
                    checked = showConversationList,
                    onCheckedChange = {
                        showConversationList = it
                        prefs.edit().putBoolean("conversation_show_list", it).apply()
                    }
                )
            }
        )
    )

    if (showLayoutModeDialog) {
        YhAlertDialog(
            onDismissRequest = { showLayoutModeDialog = false },
            title = { Text("选择置顶布局方式") },
            text = {
                Column {
                    layoutModeOptions.forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    stickyLayoutMode = value
                                    displayPrefs.edit().putString("sticky_layout_mode", value).apply()
                                    showLayoutModeDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            YhRadioButton(
                                selected = stickyLayoutMode == value,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                YhTextButton(onClick = { showLayoutModeDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showDisplayModeDialog) {
        YhAlertDialog(
            onDismissRequest = { showDisplayModeDialog = false },
            title = { Text("选择置顶显示内容") },
            text = {
                Column {
                    displayModeOptions.forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    stickyDisplayMode = value
                                    displayPrefs.edit().putString("sticky_display_mode", value).apply()
                                    showDisplayModeDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            YhRadioButton(
                                selected = stickyDisplayMode == value,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                YhTextButton(onClick = { showDisplayModeDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showAlignmentDialog) {
        YhAlertDialog(
            onDismissRequest = { showAlignmentDialog = false },
            title = { Text("选择平铺对齐方式") },
            text = {
                Column {
                    alignmentOptions.forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    stickyAlignment = value
                                    displayPrefs.edit().putString("sticky_alignment", value).apply()
                                    showAlignmentDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            YhRadioButton(
                                selected = stickyAlignment == value,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                YhTextButton(onClick = { showAlignmentDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 社区布局设置组
 */
@Composable
private fun CommunityLayoutSettingsGroup(context: Context) {
    val prefs = remember { context.getSharedPreferences("layout_settings", Context.MODE_PRIVATE) }
    
    var showHotTab by remember { mutableStateOf(prefs.getBoolean("community_show_hot", true)) }
    var showAllTab by remember { mutableStateOf(prefs.getBoolean("community_show_all", true)) }
    var showFollowingTab by remember { mutableStateOf(prefs.getBoolean("community_show_following", true)) }
    var showMoreTab by remember { mutableStateOf(prefs.getBoolean("community_show_more", true)) }
    var showMyPosts by remember { mutableStateOf(prefs.getBoolean("community_more_my_posts", true)) }
    var showRecommend by remember { mutableStateOf(prefs.getBoolean("community_more_recommend", true)) }
    var showCollect by remember { mutableStateOf(prefs.getBoolean("community_more_collect", true)) }
    var showBlocked by remember { mutableStateOf(prefs.getBoolean("community_more_blocked", true)) }
    var showCreateBoard by remember { mutableStateOf(prefs.getBoolean("community_more_create_board", true)) }
    var showManagedBoards by remember { mutableStateOf(prefs.getBoolean("community_more_managed_boards", true)) }
    
    SettingsGroup(
        title = "社区 - Tab标签",
        items = listOf(
            {
                SettingsSwitchItem(
                    icon = Icons.Default.Whatshot,
                    title = "热门",
                    subtitle = if (showHotTab) "显示热门标签" else "隐藏热门标签",
                    checked = showHotTab,
                    onCheckedChange = {
                        showHotTab = it
                        prefs.edit().putBoolean("community_show_hot", it).apply()
                    }
                )
            },
            {
                SettingsSwitchItem(
                    icon = Icons.Default.Apps,
                    title = "全部",
                    subtitle = if (showAllTab) "显示全部标签" else "隐藏全部标签",
                    checked = showAllTab,
                    onCheckedChange = {
                        showAllTab = it
                        prefs.edit().putBoolean("community_show_all", it).apply()
                    }
                )
            },
            {
                SettingsSwitchItem(
                    icon = Icons.Default.Favorite,
                    title = "关注",
                    subtitle = if (showFollowingTab) "显示关注标签" else "隐藏关注标签",
                    checked = showFollowingTab,
                    onCheckedChange = {
                        showFollowingTab = it
                        prefs.edit().putBoolean("community_show_following", it).apply()
                    }
                )
            },
            {
                SettingsSwitchItem(
                    icon = Icons.Default.MoreHoriz,
                    title = "更多",
                    subtitle = if (showMoreTab) "显示更多标签" else "隐藏更多标签",
                    checked = showMoreTab,
                    onCheckedChange = {
                        showMoreTab = it
                        prefs.edit().putBoolean("community_show_more", it).apply()
                    }
                )
            }
        )
    )
    
    if (showMoreTab) {
        SettingsGroup(
            title = "社区 - 更多Tab项目",
            items = listOf(
                {
                    SettingsSwitchItem(
                        icon = Icons.Default.Article,
                        title = "我的文章",
                        subtitle = if (showMyPosts) "显示我的文章" else "隐藏我的文章",
                        checked = showMyPosts,
                        onCheckedChange = {
                            showMyPosts = it
                            prefs.edit().putBoolean("community_more_my_posts", it).apply()
                        }
                    )
                },
                {
                    SettingsSwitchItem(
                        icon = Icons.Default.Recommend,
                        title = "查看推荐文章",
                        subtitle = if (showRecommend) "显示推荐文章" else "隐藏推荐文章",
                        checked = showRecommend,
                        onCheckedChange = {
                            showRecommend = it
                            prefs.edit().putBoolean("community_more_recommend", it).apply()
                        }
                    )
                },
                {
                    SettingsSwitchItem(
                        icon = Icons.Default.Bookmark,
                        title = "我的收藏",
                        subtitle = if (showCollect) "显示我的收藏" else "隐藏我的收藏",
                        checked = showCollect,
                        onCheckedChange = {
                            showCollect = it
                            prefs.edit().putBoolean("community_more_collect", it).apply()
                        }
                    )
                },
                {
                    SettingsSwitchItem(
                        icon = Icons.Default.Block,
                        title = "被屏蔽的用户",
                        subtitle = if (showBlocked) "显示被屏蔽用户" else "隐藏被屏蔽用户",
                        checked = showBlocked,
                        onCheckedChange = {
                            showBlocked = it
                            prefs.edit().putBoolean("community_more_blocked", it).apply()
                        }
                    )
                },
                {
                    SettingsSwitchItem(
                        icon = Icons.Default.AddBox,
                        title = "新建分区",
                        subtitle = if (showCreateBoard) "显示新建分区" else "隐藏新建分区",
                        checked = showCreateBoard,
                        onCheckedChange = {
                            showCreateBoard = it
                            prefs.edit().putBoolean("community_more_create_board", it).apply()
                        }
                    )
                },
                {
                    SettingsSwitchItem(
                        icon = Icons.Default.Dashboard,
                        title = "我管理的分区",
                        subtitle = if (showManagedBoards) "显示管理的分区" else "隐藏管理的分区",
                        checked = showManagedBoards,
                        onCheckedChange = {
                            showManagedBoards = it
                            prefs.edit().putBoolean("community_more_managed_boards", it).apply()
                        }
                    )
                }
            )
        )
    }
}

/**
 * 通讯录布局设置组
 */
@Composable
private fun ContactsLayoutSettingsGroup(context: Context) {
    val prefs = remember { context.getSharedPreferences("layout_settings", Context.MODE_PRIVATE) }
    
    var showRequests by remember { mutableStateOf(prefs.getBoolean("contacts_show_requests", true)) }
    var showFriends by remember { mutableStateOf(prefs.getBoolean("contacts_show_friends", true)) }
    var showGroups by remember { mutableStateOf(prefs.getBoolean("contacts_show_groups", true)) }
    var showBots by remember { mutableStateOf(prefs.getBoolean("contacts_show_bots", true)) }
    var showMyBots by remember { mutableStateOf(prefs.getBoolean("contacts_show_my_bots", true)) }
    
    SettingsGroup(
        title = "通讯录",
        items = listOf(
            {
                SettingsSwitchItem(
                    icon = Icons.Default.Notifications,
                    title = "申请/邀请列表",
                    subtitle = if (showRequests) "显示申请/邀请" else "隐藏申请/邀请",
                    checked = showRequests,
                    onCheckedChange = {
                        showRequests = it
                        prefs.edit().putBoolean("contacts_show_requests", it).apply()
                    }
                )
            },
            {
                SettingsSwitchItem(
                    icon = Icons.Default.People,
                    title = "好友",
                    subtitle = if (showFriends) "显示好友列表" else "隐藏好友列表",
                    checked = showFriends,
                    onCheckedChange = {
                        showFriends = it
                        prefs.edit().putBoolean("contacts_show_friends", it).apply()
                    }
                )
            },
            {
                SettingsSwitchItem(
                    icon = Icons.Default.Groups,
                    title = "我加入的群聊",
                    subtitle = if (showGroups) "显示群聊列表" else "隐藏群聊列表",
                    checked = showGroups,
                    onCheckedChange = {
                        showGroups = it
                        prefs.edit().putBoolean("contacts_show_groups", it).apply()
                    }
                )
            },
            {
                SettingsSwitchItem(
                    icon = Icons.Default.SmartToy,
                    title = "机器人",
                    subtitle = if (showBots) "显示机器人列表" else "隐藏机器人列表",
                    checked = showBots,
                    onCheckedChange = {
                        showBots = it
                        prefs.edit().putBoolean("contacts_show_bots", it).apply()
                    }
                )
            },
            {
                SettingsSwitchItem(
                    icon = Icons.Default.Build,
                    title = "我创建的机器人",
                    subtitle = if (showMyBots) "显示我的机器人" else "隐藏我的机器人",
                    checked = showMyBots,
                    onCheckedChange = {
                        showMyBots = it
                        prefs.edit().putBoolean("contacts_show_my_bots", it).apply()
                    }
                )
            }
        )
    )
}

/**
 * 发现布局设置组
 */
@Composable
private fun DiscoverLayoutSettingsGroup(context: Context) {
    val prefs = remember { context.getSharedPreferences("layout_settings", Context.MODE_PRIVATE) }
    
    var showBotList by remember { mutableStateOf(prefs.getBoolean("discover_show_bot_list", true)) }
    var showGroupList by remember { mutableStateOf(prefs.getBoolean("discover_show_group_list", true)) }
    var showBanner by remember { mutableStateOf(prefs.getBoolean("discover_show_banner", true)) }
    
    SettingsGroup(
        title = "发现",
        items = listOf(
            {
                SettingsSwitchItem(
                    icon = Icons.Default.SmartToy,
                    title = "机器人列表",
                    subtitle = if (showBotList) "显示机器人商店入口" else "隐藏机器人商店入口",
                    checked = showBotList,
                    onCheckedChange = {
                        showBotList = it
                        prefs.edit().putBoolean("discover_show_bot_list", it).apply()
                    }
                )
            },
            {
                SettingsSwitchItem(
                    icon = Icons.Default.Explore,
                    title = "发现群聊",
                    subtitle = if (showGroupList) "显示发现群聊" else "隐藏发现群聊",
                    checked = showGroupList,
                    onCheckedChange = {
                        showGroupList = it
                        prefs.edit().putBoolean("discover_show_group_list", it).apply()
                    }
                )
            }
        )
    )
    
    SettingsGroup(
        title = "机器人商店",
        items = listOf(
            {
                SettingsSwitchItem(
                    icon = Icons.Default.ViewCarousel,
                    title = "Banner轮播",
                    subtitle = if (showBanner) "显示轮播图" else "隐藏轮播图",
                    checked = showBanner,
                    onCheckedChange = {
                        showBanner = it
                        prefs.edit().putBoolean("discover_show_banner", it).apply()
                    }
                )
            }
        )
    )
}

/**
 * 我的页面布局设置组
 */
@Composable
private fun ProfileLayoutSettingsGroup(context: Context) {
    val prefs = remember { context.getSharedPreferences("layout_settings", Context.MODE_PRIVATE) }
    
    var showUserId by remember { mutableStateOf(prefs.getBoolean("profile_show_user_id", true)) }
    var showVipBadge by remember { mutableStateOf(prefs.getBoolean("profile_show_vip_badge", true)) }
    var showBetaBadge by remember { mutableStateOf(prefs.getBoolean("profile_show_beta_badge", true)) }
    var showPersonalInfo by remember { mutableStateOf(prefs.getBoolean("profile_show_personal_info", true)) }
    var showPhone by remember { mutableStateOf(prefs.getBoolean("profile_show_phone", true)) }
    var showEmail by remember { mutableStateOf(prefs.getBoolean("profile_show_email", true)) }
    var showCoin by remember { mutableStateOf(prefs.getBoolean("profile_show_coin", true)) }
    var showVipExpire by remember { mutableStateOf(prefs.getBoolean("profile_show_vip_expire", true)) }
    var showInviteCode by remember { mutableStateOf(prefs.getBoolean("profile_show_invite_code", true)) }
    
    SettingsGroup(
        title = "我的 - 基本信息",
        items = listOf(
            {
                SettingsSwitchItem(
                    icon = Icons.Default.Badge,
                    title = "用户ID",
                    subtitle = if (showUserId) "显示用户ID" else "隐藏用户ID",
                    checked = showUserId,
                    onCheckedChange = {
                        showUserId = it
                        prefs.edit().putBoolean("profile_show_user_id", it).apply()
                    }
                )
            },
            {
                SettingsSwitchItem(
                    icon = Icons.Default.WorkspacePremium,
                    title = "VIP标识",
                    subtitle = if (showVipBadge) "显示VIP标识" else "隐藏VIP标识",
                    checked = showVipBadge,
                    onCheckedChange = {
                        showVipBadge = it
                        prefs.edit().putBoolean("profile_show_vip_badge", it).apply()
                    }
                )
            },
            {
                SettingsSwitchItem(
                    icon = Icons.Default.BugReport,
                    title = "内测标识",
                    subtitle = if (showBetaBadge) "显示内测标识" else "隐藏内测标识",
                    checked = showBetaBadge,
                    onCheckedChange = {
                        showBetaBadge = it
                        prefs.edit().putBoolean("profile_show_beta_badge", it).apply()
                    }
                )
            }
        )
    )
    
    SettingsGroup(
        title = "我的 - 详细信息",
        items = listOf(
            {
                SettingsSwitchItem(
                    icon = Icons.Default.Person,
                    title = "个人信息",
                    subtitle = if (showPersonalInfo) "显示个人信息项" else "隐藏个人信息项",
                    checked = showPersonalInfo,
                    onCheckedChange = {
                        showPersonalInfo = it
                        prefs.edit().putBoolean("profile_show_personal_info", it).apply()
                    }
                )
            },
            {
                SettingsSwitchItem(
                    icon = Icons.Default.Phone,
                    title = "手机号",
                    subtitle = if (showPhone) "显示手机号" else "隐藏手机号",
                    checked = showPhone,
                    onCheckedChange = {
                        showPhone = it
                        prefs.edit().putBoolean("profile_show_phone", it).apply()
                    }
                )
            },
            {
                SettingsSwitchItem(
                    icon = Icons.Default.Email,
                    title = "邮箱",
                    subtitle = if (showEmail) "显示邮箱" else "隐藏邮箱",
                    checked = showEmail,
                    onCheckedChange = {
                        showEmail = it
                        prefs.edit().putBoolean("profile_show_email", it).apply()
                    }
                )
            },
            {
                SettingsSwitchItem(
                    icon = Icons.Default.MonetizationOn,
                    title = "金币",
                    subtitle = if (showCoin) "显示金币数量" else "隐藏金币数量",
                    checked = showCoin,
                    onCheckedChange = {
                        showCoin = it
                        prefs.edit().putBoolean("profile_show_coin", it).apply()
                    }
                )
            },
            {
                SettingsSwitchItem(
                    icon = Icons.Default.EventAvailable,
                    title = "VIP到期时间",
                    subtitle = if (showVipExpire) "显示VIP到期时间" else "隐藏VIP到期时间",
                    checked = showVipExpire,
                    onCheckedChange = {
                        showVipExpire = it
                        prefs.edit().putBoolean("profile_show_vip_expire", it).apply()
                    }
                )
            },
            {
                SettingsSwitchItem(
                    icon = Icons.Default.CardGiftcard,
                    title = "邀请码",
                    subtitle = if (showInviteCode) "显示邀请码" else "隐藏邀请码",
                    checked = showInviteCode,
                    onCheckedChange = {
                        showInviteCode = it
                        prefs.edit().putBoolean("profile_show_invite_code", it).apply()
                    }
                )
            }
        )
    )
}

/**
 * 聊天界面布局设置组
 */
@Composable
private fun ChatLayoutSettingsGroup(context: Context) {
    val prefs = remember { context.getSharedPreferences("layout_settings", Context.MODE_PRIVATE) }
    val chatPrefs = remember { context.getSharedPreferences("chat_settings", Context.MODE_PRIVATE) }
    
    var forceDisableLargeScreenMode by remember { mutableStateOf(prefs.getBoolean("force_disable_large_screen_mode", false)) }
    var hideTopAppBar by remember { mutableStateOf(prefs.getBoolean("chat_hide_top_app_bar", false)) }
    var hideBackButtonInLargeScreen by remember { mutableStateOf(prefs.getBoolean("chat_hide_back_button_large_screen", true)) }
    var showTtsButton by remember { mutableStateOf(prefs.getBoolean("chat_show_tts_button", true)) }
    var showRefreshButton by remember { mutableStateOf(prefs.getBoolean("chat_show_refresh_button", true)) }
    var showOwnerBadge by remember { mutableStateOf(prefs.getBoolean("chat_show_owner_badge", true)) }
    var showAdminBadge by remember { mutableStateOf(prefs.getBoolean("chat_show_admin_badge", true)) }
    var showMemberTags by remember { mutableStateOf(prefs.getBoolean("chat_show_member_tags", true)) }
    var showAddButton by remember { mutableStateOf(prefs.getBoolean("input_show_add_button", true)) }
    var showMicButton by remember { mutableStateOf(prefs.getBoolean("input_show_mic_button", true)) }
    var showInstructionButton by remember { mutableStateOf(prefs.getBoolean("input_show_instruction_button", true)) }
    var showExpressionButton by remember { mutableStateOf(prefs.getBoolean("input_show_expression_button", true)) }
    var showBotBoard by remember { mutableStateOf(chatPrefs.getBoolean("show_bot_board", true)) }
    var showMenuButtons by remember { mutableStateOf(chatPrefs.getBoolean("show_menu_buttons", true)) }
    
    SettingsGroup(
        title = "聊天界面 - TopAppBar",
        items = listOf(
            {
                SettingsSwitchItem(
                    icon = Icons.Default.Tablet,
                    title = "强制关闭大屏模式",
                    subtitle = if (forceDisableLargeScreenMode) "始终使用手机布局" else "允许自动启用大屏布局",
                    checked = forceDisableLargeScreenMode,
                    onCheckedChange = {
                        forceDisableLargeScreenMode = it
                        prefs.edit().putBoolean("force_disable_large_screen_mode", it).apply()
                    }
                )
            },
            {
                SettingsSwitchItem(
                    icon = Icons.Default.VisibilityOff,
                    title = "隐藏TopAppBar",
                    subtitle = if (hideTopAppBar) "TopAppBar已隐藏" else "TopAppBar正常显示",
                    checked = hideTopAppBar,
                    onCheckedChange = {
                        hideTopAppBar = it
                        prefs.edit().putBoolean("chat_hide_top_app_bar", it).apply()
                    }
                )
            },
            {
                SettingsSwitchItem(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    title = "大屏下隐藏返回键",
                    subtitle = if (hideBackButtonInLargeScreen) "大屏模式下隐藏返回按钮" else "大屏模式下显示返回按钮",
                    checked = hideBackButtonInLargeScreen,
                    onCheckedChange = {
                        hideBackButtonInLargeScreen = it
                        prefs.edit().putBoolean("chat_hide_back_button_large_screen", it).apply()
                    }
                )
            },
            {
                SettingsSwitchItem(
                    icon = Icons.Default.RecordVoiceOver,
                    title = "TTS语音按钮",
                    subtitle = if (showTtsButton) "显示文字转语音按钮" else "隐藏文字转语音按钮",
                    checked = showTtsButton,
                    onCheckedChange = {
                        showTtsButton = it
                        prefs.edit().putBoolean("chat_show_tts_button", it).apply()
                    }
                )
            },
            {
                SettingsSwitchItem(
                    icon = Icons.Default.Refresh,
                    title = "刷新按钮",
                    subtitle = if (showRefreshButton) "显示顶部刷新按钮" else "隐藏顶部刷新按钮",
                    checked = showRefreshButton,
                    onCheckedChange = {
                        showRefreshButton = it
                        prefs.edit().putBoolean("chat_show_refresh_button", it).apply()
                    }
                )
            }
        )
    )
    
    SettingsGroup(
        title = "聊天界面 - 成员标识",
        items = listOf(
            {
                SettingsSwitchItem(
                    icon = Icons.Default.Star,
                    title = "群主标识",
                    subtitle = if (showOwnerBadge) "显示群主标识" else "隐藏群主标识",
                    checked = showOwnerBadge,
                    onCheckedChange = {
                        showOwnerBadge = it
                        prefs.edit().putBoolean("chat_show_owner_badge", it).apply()
                    }
                )
            },
            {
                SettingsSwitchItem(
                    icon = Icons.Default.AdminPanelSettings,
                    title = "管理员标识",
                    subtitle = if (showAdminBadge) "显示管理员标识" else "隐藏管理员标识",
                    checked = showAdminBadge,
                    onCheckedChange = {
                        showAdminBadge = it
                        prefs.edit().putBoolean("chat_show_admin_badge", it).apply()
                    }
                )
            },
            {
                SettingsSwitchItem(
                    icon = Icons.Default.Label,
                    title = "成员标签",
                    subtitle = if (showMemberTags) "显示成员标签" else "隐藏成员标签",
                    checked = showMemberTags,
                    onCheckedChange = {
                        showMemberTags = it
                        prefs.edit().putBoolean("chat_show_member_tags", it).apply()
                    }
                )
            }
        )
    )
    
    SettingsGroup(
        title = "聊天界面 - 输入框按钮",
        items = listOf(
            {
                SettingsSwitchItem(
                    icon = Icons.Default.AddCircle,
                    title = "加号按钮",
                    subtitle = if (showAddButton) "显示加号按钮" else "隐藏加号按钮",
                    checked = showAddButton,
                    onCheckedChange = {
                        showAddButton = it
                        prefs.edit().putBoolean("input_show_add_button", it).apply()
                    }
                )
            },
            {
                SettingsSwitchItem(
                    icon = Icons.Default.Mic,
                    title = "麦克风按钮",
                    subtitle = if (showMicButton) "显示麦克风按钮" else "隐藏麦克风按钮",
                    checked = showMicButton,
                    onCheckedChange = {
                        showMicButton = it
                        prefs.edit().putBoolean("input_show_mic_button", it).apply()
                    }
                )
            },
            {
                SettingsSwitchItem(
                    icon = Icons.Default.Code,
                    title = "指令按钮",
                    subtitle = if (showInstructionButton) "显示指令按钮" else "隐藏指令按钮",
                    checked = showInstructionButton,
                    onCheckedChange = {
                        showInstructionButton = it
                        prefs.edit().putBoolean("input_show_instruction_button", it).apply()
                    }
                )
            },
            {
                SettingsSwitchItem(
                    icon = Icons.Default.EmojiEmotions,
                    title = "表情按钮",
                    subtitle = if (showExpressionButton) "显示表情按钮" else "隐藏表情按钮",
                    checked = showExpressionButton,
                    onCheckedChange = {
                        showExpressionButton = it
                        prefs.edit().putBoolean("input_show_expression_button", it).apply()
                    }
                )
            }
        )
    )

    SettingsGroup(
        title = "聊天界面 - 其他显示",
        items = listOf(
            {
                SettingsSwitchItem(
                    icon = Icons.Default.SmartToy,
                    title = "显示机器人看板",
                    subtitle = if (showBotBoard) "在聊天界面显示群聊机器人看板和单机器人看板" else "隐藏所有机器人看板",
                    checked = showBotBoard,
                    onCheckedChange = {
                        showBotBoard = it
                        chatPrefs.edit().putBoolean("show_bot_board", it).apply()
                    }
                )
            },
            {
                SettingsSwitchItem(
                    icon = Icons.Default.Menu,
                    title = "显示菜单按钮栏",
                    subtitle = if (showMenuButtons) "在聊天输入框上方显示菜单按钮栏" else "隐藏菜单按钮栏",
                    checked = showMenuButtons,
                    onCheckedChange = {
                        showMenuButtons = it
                        chatPrefs.edit().putBoolean("show_menu_buttons", it).apply()
                    }
                )
            }
        )
    )
}
