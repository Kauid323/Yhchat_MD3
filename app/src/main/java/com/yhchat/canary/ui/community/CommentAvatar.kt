package com.yhchat.canary.ui.community

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import coil.compose.SubcomposeAsyncImage
import com.yhchat.canary.R
import com.yhchat.canary.ui.components.ImageUtils

/**
 * 评论/楼中楼头像。
 *
 * 用 [ImageUtils.createAvatarImageRequest] 构造请求，不用裸的 `data(url)`
 *  —— 那个方法尊重「省流量模式」，跟着项目其他头像保持一致。
 *  （Referer 由全局 ImageLoader 的拦截器统一加，这里不用管。）
 *
 * 没有头像时（URL 为空，或图片加载失败）显示 `ic_person` 人形图标：
 * 部分老账号（如 player+数字 这类自动生成的 id）后端没有返回头像地址，
 * 不兜底的话那里就是一块空白，跟左侧竖线、楼层号排在一起特别像界面坏了。
 *
 * 为什么不用「昵称首字 + 色块」：那是自己编的视觉，跟官方在同样位置
 * 显示的小图标不一致；同一个 App 里两种默认头像看着像没做完。
 */
@Composable
internal fun CommentAvatar(
    url: String?,
    name: String?,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val trimmedUrl = url?.trim().orEmpty()

    // 先把圆形底和裁剪定好，图片和兜底图标都在里面画，
    // 两种状态的位置、大小完全一致，切换时不会跳
    val base = modifier
        .size(size)
        .clip(CircleShape)

    if (trimmedUrl.isEmpty()) {
        AvatarFallback(base)
        return
    }

    SubcomposeAsyncImage(
        model = ImageUtils.createAvatarImageRequest(
            context = context,
            url = trimmedUrl
        ),
        contentDescription = name,
        contentScale = ContentScale.Crop,
        modifier = base,
        // 加载中和失败都用兜底图标。
        // 加载中也用同一个图标而不是留白：列表滚动时头像陆续出现，
        // 留白会让行内容「先空后满」地跳一下。
        loading = { AvatarFallback(Modifier.fillMaxSize()) },
        error = { AvatarFallback(Modifier.fillMaxSize()) }
    )
}

/**
 * 没有头像时画的东西：浅色圆底 + 人形图标。
 *
 * 用主题的 surfaceVariant 而不是写死灰色，深色模式下才不会出现
 * 一块刺眼的浅灰。
 */
@Composable
private fun AvatarFallback(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_person),
            contentDescription = null,
            // 图标占头像的六成，留出边距，看起来才像「头像里的图」而不是撑满了
            modifier = Modifier.fillMaxSize(0.6f),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}