package com.yhchat.canary.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.yhchat.canary.R

/**
 * 头像框开关的 prefs 键。
 *
 * 放在这里而不是设置页里：写入方（设置页）和读取方（聊天头像）
 * 必须用同一个字符串，写错一个字符就变成「开关能拨但界面没反应」，
 * 而这种错不会报编译错误，只能靠人工对比字符串才能发现。
 *
 * 从 components 层导出、让 settings 层去 import，方向是对的 ——
 * 组件不应该反过来依赖某个具体页面。
 */
const val AvatarFramePrefsKey = "show_avatar_frame"

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
 */
private const val FrameScale = 1.8f

/**
 * 头像 + 可选装饰框。
 *
 * 调用方给头像内容和尺寸，是否显示框由参数控制。
 *
 * 框画在头像**上层**（Box 里后者盖前者）：素材的装饰是往外的，
 * 画在下层会被头像挡住边缘。
 *
 * @param showFrame 是否显示装饰框
 * @param avatarSize 头像本身的直径。框会按 [frameScale] 放大
 * @param frameScale 框相对头像的倍数。默认 [FrameScale] 是按素材内圆
 *        精确算出来的；聊天列表那种地方头像只有 40dp，用默认值会让整行
 *        高出一大截，需要传小一点（1.35 左右）—— 代价是框的内圆比头像小，
 *        装饰环会盖住头像最外一圈，视觉上仍然是「框住」的效果
 * @param reserveFrameSpace 外层是否按框的尺寸留位。
 *        true：给框留出完整空间，适合独立摆放的头像（如个人资料页）；
 *        false：外层保持头像尺寸，框溢出到外面。列表里用这个可以避免
 *        一行因为加框而变高
 */
@Composable
fun FramedAvatar(
    avatarSize: Dp,
    showFrame: Boolean,
    modifier: Modifier = Modifier,
    frameScale: Float = FrameScale,
    reserveFrameSpace: Boolean = true,
    avatar: @Composable () -> Unit
) {
    val frameSize = avatarSize * frameScale
    Box(
        modifier = modifier.size(
            if (showFrame && reserveFrameSpace) frameSize else avatarSize
        ),
        contentAlignment = Alignment.Center
    ) {
        avatar()
        if (showFrame) {
            Image(
                painter = painterResource(id = R.drawable.avatar_frame_halloween),
                contentDescription = null,
                modifier = Modifier.size(frameSize)
            )
        }
    }
}