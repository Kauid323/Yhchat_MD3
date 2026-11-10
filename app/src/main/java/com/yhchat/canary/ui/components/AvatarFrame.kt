package com.yhchat.canary.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yhchat.canary.R

/**
 * 头像外面的装饰框。
 *
 * 做法是把一张 PNG 叠在头像上面：PNG 中心是透明的圆，四周是装饰
 * （猫头、麦穗、枫叶之类）。头像画在下面，框盖上去就只露出装饰部分。
 *
 * 尺寸关系是这个组件唯一的难点：
 *
 *   头像直径 : 框内圆直径 = 1 : 0.57
 *
 * 素材是 512×512，其中内圈直径约占 290px（56.6%）。所以框的整体尺寸
 * 必须是头像的 1/0.566 ≈ 1.77 倍 —— 这样框的内圆才正好跟头像一样大，
 * 装饰往外伸的部分才不会被裁掉。
 *
 * 用 [FrameScale] 这个常量而不是让调用方自己算：调用方只关心「头像是多大」，
 * 换算错了就表现为装饰压到脸上或者框比头小一圈，而且这种错很难一眼看出来。
 */
@Composable
fun AvatarFrameOverlay(
    avatarSize: Dp,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(id = R.drawable.avatar_frame_halloween),
        contentDescription = null,
        modifier = modifier.size(avatarSize * FrameScale)
    )
}

/**
 * 框外径相对头像直径的倍数。
 *
 * 512 的素材里内圆直径约 290，290/512 ≈ 0.566，倒数约 1.77。
 * 取 1.8 而不是 1.77 是为了留一点点余量：装饰的最外沿（麦穗尖、枫叶）
 * 贴近画布边缘，1.77 时抗锯齿会让边缘有点被切掉的感觉。
 */
private const val FrameScale = 1.8f

/**
 * 头像 + 可选装饰框。
 *
 * 调用方给头像内容和尺寸，是否显示框由参数控制。头像不显示时不画框 ——
 * 空头像上飘一圈装饰比什么都不画更奇怪。
 *
 * 框用 `drawnOver` 的等价做法（Box 里后者盖前者）画在头像上方，
 * 而不是下层：素材的装饰是往外的，画在下层会被头像的光圈挡住边缘。
 *
 * @param showFrame 是否显示装饰框
 * @param avatarSize 头像本身的直径。框会自动放大到 [FrameScale] 倍
 */
@Composable
fun FramedAvatar(
    avatarSize: Dp,
    showFrame: Boolean,
    modifier: Modifier = Modifier,
    avatar: @Composable () -> Unit
) {
    Box(
        // 外层按「框」的尺寸留位，不然框会溢出到相邻元素上，
        // 在列表里尤其明显（装饰压到下一行文字上）
        modifier = modifier.size(if (showFrame) avatarSize * FrameScale else avatarSize),
        contentAlignment = Alignment.Center
    ) {
        avatar()
        if (showFrame) {
            AvatarFrameOverlay(
                avatarSize = avatarSize,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}