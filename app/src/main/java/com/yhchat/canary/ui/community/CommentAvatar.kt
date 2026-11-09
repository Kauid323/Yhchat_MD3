package com.yhchat.canary.ui.community

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext

/**
 * 评论/楼中楼头像，统一处理「加载中、加载失败、URL 为空」三种情况。
 *
 * 直接用 `AsyncImage` 的问题是这三种情况都渲染成**一片空白**：
 * 头像位置什么都没有，看起来像界面坏了。评论列表里这种空白尤其显眼，
 * 因为它跟左侧那条竖线、楼层号排在一起，缺一块特别扎眼。
 *
 * 这里用 `SubcomposeAsyncImage` 分成三种状态：
 *   - 加载中：底色 + 首字（跟失败态一样，避免加载过程中闪一下空白）
 *   - 失败：底色 + 首字
 *   - 成功：正常图片
 * 首字占位至少能看出「这层是谁发的」，比空白信息量大得多，
 * 也是 Material Design 对列表头像的推荐做法。
 */
@Composable
internal fun CommentAvatar(
    url: String?,
    name: String?,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // 头像底色由名字决定，同一个人每次都是同一个颜色 ——
    // 固定色会让所有占位头像长得一样，反而认不出是谁
    val placeholderColor = remember(name) { avatarPlaceholderColor(name) }
    // 首字：中文取第一个字，英文取首字母（大写）
    val initial = remember(name) { avatarInitial(name) }

    val shape = CircleShape
    val trimmedUrl = url?.trim().orEmpty()

    // URL 为空时不必走 Coil，直接画占位
    if (trimmedUrl.isEmpty()) {
        AvatarPlaceholder(
            initial = initial,
            color = placeholderColor,
            size = size,
            modifier = modifier
        )
        return
    }

    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data(trimmedUrl)
            // 淡入一下，避免图片「啪」地出现
            .crossfade(true)
            .build(),
        contentDescription = name,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(shape),
        loading = {
            AvatarPlaceholder(
                initial = initial,
                color = placeholderColor,
                size = size,
                modifier = Modifier.fillMaxSize()
            )
        },
        error = {
            AvatarPlaceholder(
                initial = initial,
                color = placeholderColor,
                size = size,
                modifier = Modifier.fillMaxSize()
            )
        }
    )
}

/** 首字占位：底色 + 一个居中的字。 */
@Composable
private fun AvatarPlaceholder(
    initial: String,
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            // 字号跟着头像尺寸走：36dp 的头像用 15sp，22dp 的用 10sp。
            // 写死一个值会让小头像的字溢出来、大头像的字显得空。
            fontSize = (size.value * 0.42f).sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
    }
}

/**
 * 名字 → 首字。
 *
 * 中文取第一个汉字；英文/数字取首字符大写。
 * 名字为空时给一个「?」，总比什么都不画强 —— 至少说明这里是个头像位。
 */
private fun avatarInitial(name: String?): String {
    val trimmed = name?.trim().orEmpty()
    if (trimmed.isEmpty()) return "?"
    // 跳过开头的空白和常见装饰符号
    val first = trimmed.firstOrNull { it.isLetterOrDigit() } ?: trimmed.first()
    return first.uppercase()
}

/**
 * 名字 → 占位底色。
 *
 * 从一组饱和度偏低、亮度适中的颜色里按名字的 hash 取。
 * 不用随机色：同一个人每次打开都得是同一个颜色，否则「认色找人」就失效了。
 * 也不用主题色：列表里十几个头像全同色，等于没有区分度。
 */
private fun avatarPlaceholderColor(name: String?): Color {
    val palette = listOf(
        Color(0xFF7C6BB0), // 紫
        Color(0xFF5B8CBF), // 蓝
        Color(0xFF4E9A8C), // 青
        Color(0xFF8A9A4E), // 橄榄
        Color(0xFFBF8A5B), // 橙
        Color(0xFFB06B8C), // 玫红
        Color(0xFF6B7FBF), // 靛
        Color(0xFF8C6BB0)  // 紫红
    )
    val key = name?.trim().orEmpty()
    if (key.isEmpty()) return palette.first()
    // 用 hash 取模，负数要转正
    val index = ((key.hashCode() % palette.size) + palette.size) % palette.size
    return palette[index]
}