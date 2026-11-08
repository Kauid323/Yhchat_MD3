package com.yhchat.canary.ui.settings

import android.content.Context
import com.yhchat.canary.data.repository.NavigationRepository
import com.yhchat.canary.ui.share.ExternalShareManagerActivity

/**
 * 设置项的可搜索索引。
 *
 * 搜索要能搜到「二级页里的设置」，但二级页各自是独立 Activity，运行期没法枚举
 * 它们内部的条目。所以在这里维护一份清单：
 *
 *   - [SettingsEntry.ScrollTo]：# 一级页上已经存在的条目，点击后回到设置首页并滚到它
 *   - [SettingsEntry.OpenPage]：二级页里的条目，点击后直接打开那个二级页
 *
 * 二级页的条目没法精确滚到（它们不在同一个滚动容器里），所以标记成
 * [SettingsEntry.OpenPage]，点击后跳到页面顶部。这样至少「搜得到、点得进」。
 *
 * ## 维护约定
 *
 * 往设置里加条目时，顺手在这里补一条。字段含义：
 *   - `title`     —— 条目上显示的文字（搜索主要匹配它）
 *   - `keywords`  —— 同义词、拼音、简称。用户不会记得原文怎么写，
 *                    所以「音量」要能搜到「语速」，「暗色」要能搜到「主题模式」
 *   - `section`   —— 归属的一级分组名，显示在结果行副标题里
 *   - `page`      —— 二级页名。空表示一级页自己的条目
 */
internal enum class SettingsPage(val label: String) {
    Layout("布局设置"),
    Navigation("底部导航栏设置"),
    Html("HTML设置"),
    Display("显示设置"),
    Theme("主题设置"),
    Personalization("个性化"),
    Content("内容"),
    Tts("文字转语音"),
    OnlineDevices("在线设备"),
    AppInfo("应用详情"),
    SavedAudios("已保存的语音"),
    Share("外部分享"),
}

/**
 * 一条可搜索的设置项。
 *
 * @param title 条目文字
 * @param section 归属分组，搜索结果第二行显示它
 * @param page 打开后落到哪个二级页；null 表示就在设置首页
 * @param keywords 额外匹配词（同义词 / 简称 / 拼音）
 * @param scrollKey 一级页上该条目所在分组，用于点击后滚过去
 */
internal data class SettingsEntry(
    val title: String,
    val section: String,
    val page: SettingsPage?,
    val keywords: List<String>,
    val scrollKey: String? = null
)

/**
 * 一级页上出现过的分组，用来算滚动位置。
 * 名字必须跟 [SettingsScreen] 里 `SettingsGroup(title = ...)` 一致。
 */
internal object SettingsSection {
    const val Interface = "界面"
    const val Account = "账户"
    const val Content = "内容"
    const val Share = "分享"
    const val YhTools = "云湖工具"
    const val Notification = "通知"
    const val Display = "显示设置"
    const val Theme = "主题设置"
    const val Personalization = "个性化"
    const val Tts = "文字转语音"
    const val About = "关于"
    const val AccountManage = "账户管理"

    /**
     * 各分组在首页 LazyColumn 里的 item 下标。
     *
     * 首页是 13 个 `item { }` 平铺出来的，没有 key。搜索命中首页条目时用它
     * 滚到对应位置。**首页加了新分组就要更新这里**，否则会滚偏。
     *
     * 顺序对照 SettingsScreen 里的 `item { ... }`：
     *   0 界面 / 1 账户 / 2 内容 / 3 分享 / 4 云湖工具 / 5 云湖工具箱面板 /
     *   6 通知 / 7 显示设置 / 8 文字转语音 / 9 主题设置 / 10 个性化 /
     *   11 关于 / 12 账户管理 / 13 底部 Spacer
     */
    private val itemIndexOf: Map<String, Int> = mapOf(
        Interface to 0,
        Account to 1,
        Content to 2,
        Share to 3,
        YhTools to 4,
        Notification to 6,
        Display to 7,
        Tts to 8,
        Theme to 9,
        Personalization to 10,
        About to 11,
        AccountManage to 12
    )

    /** 分组对应的 item 下标；未知分组返回 null，调用方就不滚动。 */
    fun itemIndex(section: String): Int? = itemIndexOf[section]
}

/**
 * 全量索引。
 *
 * 顺序按「用户最可能找的」排，不按页面顺序 —— 搜索结果的排序会参考这个顺序
 * （见 [SettingsSearch]），越靠前越优先。
 */
internal val SettingsSearchIndex: List<SettingsEntry> = buildList {
    // ==================== 一级页：界面 ====================
    add(
        SettingsEntry(
            title = "布局设置",
            section = SettingsSection.Interface,
            page = SettingsPage.Layout,
            keywords = listOf("布局", "界面布局", "显示项", "自定义", "layout"),
            scrollKey = SettingsSection.Interface
        )
    )
    add(
        SettingsEntry(
            title = "底部导航栏设置",
            section = SettingsSection.Interface,
            page = SettingsPage.Navigation,
            keywords = listOf("导航", "导航栏", "底部", "标签栏", "tab", "navbar"),
            scrollKey = SettingsSection.Interface
        )
    )
    add(
        SettingsEntry(
            title = "聊天界面动画",
            section = SettingsSection.Interface,
            page = null,
            keywords = listOf("动画", "聊天动画", "动效", "animation"),
            scrollKey = SettingsSection.Interface
        )
    )
    add(
        SettingsEntry(
            title = "消息列表拖拽动画",
            section = SettingsSection.Interface,
            page = null,
            keywords = listOf("拖拽", "消息列表", "位置动画", "drag"),
            scrollKey = SettingsSection.Interface
        )
    )
    add(
        SettingsEntry(
            title = "聊天上下文弹窗",
            section = SettingsSection.Interface,
            page = null,
            keywords = listOf("上下文", "长按", "弹窗", "消息菜单", "context menu"),
            scrollKey = SettingsSection.Interface
        )
    )

    // ==================== 一级页：账户 ====================
    add(
        SettingsEntry(
            title = "在线设备",
            section = SettingsSection.Account,
            page = SettingsPage.OnlineDevices,
            keywords = listOf("设备", "登录设备", "在线", "devices"),
            scrollKey = SettingsSection.Account
        )
    )
    add(
        SettingsEntry(
            title = "切换账号",
            section = SettingsSection.Account,
            page = null,
            keywords = listOf("账号", "切换", "多账号", "account", "switch"),
            scrollKey = SettingsSection.Account
        )
    )
    add(
        SettingsEntry(
            title = "修改密码",
            section = SettingsSection.Account,
            page = null,
            keywords = listOf("密码", "改密码", "password"),
            scrollKey = SettingsSection.Account
        )
    )
    add(
        SettingsEntry(
            title = "黑名单",
            section = SettingsSection.Account,
            page = null,
            keywords = listOf("屏蔽", "拉黑", "黑名单", "block", "blacklist"),
            scrollKey = SettingsSection.Account
        )
    )
    add(
        SettingsEntry(
            title = "网易云账号",
            section = SettingsSection.Account,
            page = null,
            keywords = listOf("网易云", "音乐", "ncm", "登录"),
            scrollKey = SettingsSection.Account
        )
    )

    // ==================== 一级页：内容 ====================
    add(
        SettingsEntry(
            title = "HTML设置",
            section = SettingsSection.Content,
            page = SettingsPage.Html,
            keywords = listOf("html", "网页", "javascript", "网页设置"),
            scrollKey = SettingsSection.Content
        )
    )
    add(
        SettingsEntry(
            title = "上传原图",
            section = SettingsSection.Content,
            page = null,
            keywords = listOf("原图", "画质", "上传", "图片质量"),
            scrollKey = SettingsSection.Content
        )
    )
    add(
        SettingsEntry(
            title = "已保存的语音",
            section = SettingsSection.Content,
            page = SettingsPage.SavedAudios,
            keywords = listOf("语音", "音频", "录音", "audio"),
            scrollKey = SettingsSection.Content
        )
    )
    add(
        SettingsEntry(
            title = "允许发送空文本消息",
            section = SettingsSection.Content,
            page = null,
            keywords = listOf("空消息", "空文本", "空白消息"),
            scrollKey = SettingsSection.Content
        )
    )

    // ==================== 一级页：分享 ====================
    add(
        SettingsEntry(
            title = "外部分享",
            section = SettingsSection.Share,
            page = SettingsPage.Share,
            keywords = listOf("分享", "外部", "share"),
            scrollKey = SettingsSection.Share
        )
    )

    // ==================== 一级页：云湖工具 ====================
    add(
        SettingsEntry(
            title = "云湖工具箱",
            section = SettingsSection.YhTools,
            page = null,
            keywords = listOf("工具箱", "工具", "社区", "表情包", "websocket", "快捷入口"),
            scrollKey = SettingsSection.YhTools
        )
    )

    // ==================== 一级页：通知 ====================
    add(
        SettingsEntry(
            title = "强制关闭音乐通知",
            section = SettingsSection.Notification,
            page = null,
            keywords = listOf("通知", "音乐", "关闭通知", "notification"),
            scrollKey = SettingsSection.Notification
        )
    )

    // ==================== 一级页：显示设置 ====================
    add(
        SettingsEntry(
            title = "显示内联表情",
            section = SettingsSection.Display,
            page = null,
            keywords = listOf("表情", "内联", "emoji", "表情渲染"),
            scrollKey = SettingsSection.Display
        )
    )
    add(
        SettingsEntry(
            title = "选择表情后自动收回",
            section = SettingsSection.Display,
            page = null,
            keywords = listOf("表情面板", "自动收回", "表情"),
            scrollKey = SettingsSection.Display
        )
    )
    add(
        SettingsEntry(
            title = "长按发送自动发 Markdown",
            section = SettingsSection.Display,
            page = null,
            keywords = listOf("长按", "markdown", "发送", "md"),
            scrollKey = SettingsSection.Display
        )
    )
    add(
        SettingsEntry(
            title = "字体大小",
            section = SettingsSection.Display,
            page = null,
            keywords = listOf("字体", "字号", "文字大小", "font", "缩放"),
            scrollKey = SettingsSection.Display
        )
    )
    add(
        SettingsEntry(
            title = "组件大小",
            section = SettingsSection.Display,
            page = null,
            keywords = listOf("组件", "缩放", "界面大小", "dpi", "scale"),
            scrollKey = SettingsSection.Display
        )
    )
    add(
        SettingsEntry(
            title = "内存阈值自动清理",
            section = SettingsSection.Display,
            page = null,
            keywords = listOf("内存", "清理", "内存清理", "memory"),
            scrollKey = SettingsSection.Display
        )
    )
    add(
        SettingsEntry(
            title = "省流量模式",
            section = SettingsSection.Display,
            page = null,
            keywords = listOf("省流量", "流量", "data saver", "图片压缩"),
            scrollKey = SettingsSection.Display
        )
    )
    add(
        SettingsEntry(
            title = "禁用 WebSocket",
            section = SettingsSection.Display,
            page = null,
            keywords = listOf("websocket", "长连接", "网络", "ws"),
            scrollKey = SettingsSection.Display
        )
    )
    add(
        SettingsEntry(
            title = "WebP压缩质量",
            section = SettingsSection.Display,
            page = null,
            keywords = listOf("webp", "压缩", "图片质量", "画质"),
            scrollKey = SettingsSection.Display
        )
    )
    add(
        SettingsEntry(
            title = "HTML消息显示原文",
            section = SettingsSection.Display,
            page = null,
            keywords = listOf("html", "原文", "源码", "raw"),
            scrollKey = SettingsSection.Display
        )
    )
    add(
        SettingsEntry(
            title = "Markdown消息显示原文",
            section = SettingsSection.Display,
            page = null,
            keywords = listOf("markdown", "原文", "源码", "raw", "md"),
            scrollKey = SettingsSection.Display
        )
    )
    add(
        SettingsEntry(
            title = "A2UI消息显示原文",
            section = SettingsSection.Display,
            page = null,
            keywords = listOf("a2ui", "原文", "源码", "raw"),
            scrollKey = SettingsSection.Display
        )
    )
    add(
        SettingsEntry(
            title = "长文自动折叠",
            section = SettingsSection.Display,
            page = null,
            keywords = listOf("长文", "折叠", "收起", "长消息"),
            scrollKey = SettingsSection.Display
        )
    )

    // ==================== 一级页：主题设置 ====================
    add(
        SettingsEntry(
            title = "主题模式",
            section = SettingsSection.Theme,
            page = null,
            keywords = listOf("主题", "深色", "暗色", "浅色", "夜间", "dark", "light", "mode"),
            scrollKey = SettingsSection.Theme
        )
    )

    // ==================== 一级页：个性化 ====================
    add(
        SettingsEntry(
            title = "聊天背景",
            section = SettingsSection.Personalization,
            page = null,
            keywords = listOf("背景", "聊天背景", "壁纸", "background", "wallpaper"),
            scrollKey = SettingsSection.Personalization
        )
    )
    add(
        SettingsEntry(
            title = "主题颜色",
            section = SettingsSection.Personalization,
            page = null,
            keywords = listOf("主题色", "颜色", "配色", "accent", "color"),
            scrollKey = SettingsSection.Personalization
        )
    )

    // ==================== 一级页：TTS ====================
    add(
        SettingsEntry(
            title = "启用TTS功能",
            section = SettingsSection.Tts,
            page = null,
            keywords = listOf("tts", "文字转语音", "语音朗读", "朗读"),
            scrollKey = SettingsSection.Tts
        )
    )
    add(
        SettingsEntry(
            title = "语速",
            section = SettingsSection.Tts,
            page = null,
            keywords = listOf("语速", "速度", "朗读速度", "speech rate"),
            scrollKey = SettingsSection.Tts
        )
    )
    add(
        SettingsEntry(
            title = "音调",
            section = SettingsSection.Tts,
            page = null,
            keywords = listOf("音调", "音高", "pitch"),
            scrollKey = SettingsSection.Tts
        )
    )

    // ==================== 一级页：关于 / 账户管理 ====================
    add(
        SettingsEntry(
            title = "应用详情",
            section = SettingsSection.About,
            page = SettingsPage.AppInfo,
            keywords = listOf("版本", "关于", "开发者", "app info", "version"),
            scrollKey = SettingsSection.About
        )
    )
    add(
        SettingsEntry(
            title = "退出登录",
            section = SettingsSection.AccountManage,
            page = null,
            keywords = listOf("退出", "登出", "logout", "sign out"),
            scrollKey = SettingsSection.AccountManage
        )
    )

    // ==================== 二级页：布局设置 ====================
    addAll(
        layoutPageEntries()
    )

    // ==================== 二级页：HTML 设置 ====================
    addAll(
        htmlPageEntries()
    )

    // ==================== 二级页：导航栏 ====================
    add(
        SettingsEntry(
            title = "自定义导航栏",
            section = SettingsPage.Navigation.label,
            page = SettingsPage.Navigation,
            keywords = listOf("导航项", "显示", "隐藏", "排序", "拖拽")
        )
    )
}

/**
 * 布局设置二级页的条目。
 *
 * 这些标题取自 `LayoutSettingsActivity` 里的 `SettingsRow(title = ...)`，
 * 分组名就是那一页的分区标题。
 */
private fun layoutPageEntries(): List<SettingsEntry> {
    val page = SettingsPage.Layout
    val sectionOf = page.label
    fun entry(title: String, vararg keywords: String) = SettingsEntry(
        title = title,
        section = sectionOf,
        page = page,
        keywords = keywords.toList()
    )

    return listOf(
        // 会话列表
        entry("头像显示", "会话列表", "头像", "topbar"),
        entry("搜索框", "会话列表", "搜索", "search"),
        entry("加号按钮", "会话列表", "加号", "plus", "新建"),
        entry("添加用户/群聊/机器人", "加号菜单", "添加", "好友", "群聊"),
        entry("创建群聊/机器人", "加号菜单", "创建", "群聊", "机器人"),
        entry("扫一扫", "加号菜单", "扫一扫", "二维码", "qr", "扫码"),
        entry("显示置顶会话", "置顶", "置顶", "pin"),
        entry("布局方式", "置顶", "布局", "排列"),
        entry("显示折叠/展开按钮", "置顶", "折叠", "展开"),
        entry("显示内容", "置顶", "内容", "显示"),
        entry("平铺对齐方式", "置顶", "平铺", "对齐"),
        entry("未读消息红点", "显示项", "红点", "未读", "badge"),

        // 社区
        entry("社区 Tab 标签", "社区", "tab", "标签"),
        entry("热门", "社区", "热门", "tab"),
        entry("全部", "社区", "全部", "tab"),
        entry("关注", "社区", "关注", "tab"),
        entry("更多", "社区", "更多", "tab"),
        entry("我的文章", "社区更多", "我的文章", "文章"),
        entry("查看推荐文章", "社区更多", "推荐", "文章"),
        entry("我的收藏", "社区更多", "收藏", "collection"),
        entry("被屏蔽的用户", "社区更多", "屏蔽", "黑名单"),
        entry("新建分区", "社区更多", "分区", "板块", "board"),
        entry("我管理的分区", "社区更多", "管理", "分区", "板块"),

        // 通讯录 / 发现 / 机器人商店
        entry("申请/邀请列表", "通讯录", "申请", "邀请", "好友申请"),
        entry("好友", "通讯录", "好友", "friend"),
        entry("我加入的群聊", "通讯录", "群聊", "joined"),
        entry("机器人", "通讯录", "机器人", "bot"),
        entry("我创建的机器人", "通讯录", "机器人", "创建", "bot"),
        entry("机器人列表", "发现", "机器人", "bot"),
        entry("发现群聊", "发现", "群聊", "发现"),
        entry("Banner轮播", "机器人商店", "banner", "轮播"),

        // 我的
        entry("用户ID", "我的", "id", "用户"),
        entry("VIP标识", "我的", "vip", "标识"),
        entry("内测标识", "我的", "内测", "beta", "标识"),
        entry("个人信息", "我的", "资料", "信息"),
        entry("手机号", "我的", "手机", "phone"),
        entry("邮箱", "我的", "邮箱", "email"),
        entry("金币", "我的", "金币", "coin"),
        entry("VIP到期时间", "我的", "vip", "到期"),
        entry("邀请码", "我的", "邀请码", "invite"),

        // 聊天界面
        entry("强制关闭大屏模式", "聊天", "大屏", "平板", "tablet"),
        entry("隐藏TopAppBar", "聊天", "顶栏", "topbar", "隐藏"),
        entry("大屏下隐藏返回键", "聊天", "返回键", "大屏", "back"),
        entry("TTS语音按钮", "聊天", "tts", "语音按钮"),
        entry("刷新按钮", "聊天", "刷新", "refresh"),
        entry("群主标识", "成员标识", "群主", "标识", "owner"),
        entry("管理员标识", "成员标识", "管理员", "标识", "admin"),
        entry("成员标签", "成员标识", "标签", "level"),
        entry("麦克风按钮", "输入框", "麦克风", "语音输入", "mic"),
        entry("指令按钮", "输入框", "指令", "command"),
        entry("表情按钮", "输入框", "表情", "emoji"),
        entry("显示机器人看板", "聊天其他", "机器人", "看板", "board"),
        entry("显示菜单按钮栏", "聊天其他", "菜单", "按钮栏")
    )
}

/** HTML 设置二级页的条目。 */
private fun htmlPageEntries(): List<SettingsEntry> {
    val page = SettingsPage.Html
    fun entry(title: String, vararg keywords: String) = SettingsEntry(
        title = title,
        section = page.label,
        page = page,
        keywords = keywords.toList()
    )

    return listOf(
        entry("启用JavaScript", "javascript", "js", "脚本"),
        entry("允许缩放", "缩放", "手势缩放", "zoom"),
        entry("加载图片", "图片", "加载图片", "image"),
        entry("缓存模式", "缓存", "cache"),
        entry("User Agent", "ua", "标识", "useragent")
    )
}

/**
 * 二级页的启动器。
 *
 * 索引里只存 [SettingsPage]，真正跳转集中在这里 —— 加二级页时只改这一处，
 * 不用回到索引里逐条改 lambda。
 *
 * @param navigationRepository 导航栏设置页需要它；为空时点了不跳（那个入口在首页
 *        本来也只在有 repository 时才可用）
 */
internal fun openSettingsPage(
    context: Context,
    page: SettingsPage,
    navigationRepository: NavigationRepository? = null
) {
    when (page) {
        SettingsPage.Layout -> LayoutSettingsActivity.start(context)
        SettingsPage.Html -> HtmlSettingsActivity.start(context)
        SettingsPage.OnlineDevices -> OnlineDevicesActivity.start(context)
        SettingsPage.AppInfo -> AppInfoActivity.start(context)
        SettingsPage.SavedAudios -> SavedAudiosActivity.start(context)
        SettingsPage.Share -> ExternalShareManagerActivity.start(context)
        SettingsPage.Navigation -> navigationRepository?.let {
            NavigationSettingsActivity.start(context, it)
        }
        // 剩下几个是首页上的分组名，不是真正的二级页，由索引里 page = null 的条目覆盖
        SettingsPage.Display,
        SettingsPage.Theme,
        SettingsPage.Personalization,
        SettingsPage.Content,
        SettingsPage.Tts -> Unit
    }
}