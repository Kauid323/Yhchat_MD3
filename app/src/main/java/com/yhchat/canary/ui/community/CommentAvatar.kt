package com.yhchat.canary.ui.community

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import com.yhchat.canary.ui.components.ImageUtils

/**
 * 评论/楼中楼头像。
 *
 * 走 [ImageUtils.createAvatarImageRequest] 而不是裸的 `data(url)`：
 * 那个方法会按域名补 `Referer` / `User-Agent`，并且尊重「省流量模式」
 * （开着时直接不请求图片）。项目里其他地方的头像也是这么做的，
 * 这里跟它们保持一致。
 *
 * 注意：URL 为空时**直接不画**，不做文字占位。
 * 空 URL 是后端没给头像，占位只会用假头像盖住这个事实。
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

    // 空 URL 没什么可加载的。给 null 的话 Coil 会走一次失败的请求，
    // 白白占一个 request slot，直接返回更干净。
    if (trimmedUrl.isEmpty()) return

    AsyncImage(
        model = ImageUtils.createAvatarImageRequest(
            context = context,
            url = trimmedUrl
        ),
        contentDescription = name,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
    )
}