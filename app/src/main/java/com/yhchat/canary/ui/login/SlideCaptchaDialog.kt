package com.yhchat.canary.ui.login

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yhchat.canary.data.model.SlideCaptchaData
import kotlin.math.roundToInt

/**
 * 把 `data:image/jpeg;base64,xxxx` 解成位图。
 *
 * 后端返回的是完整 data URI，不能直接丢给 Base64.decode —— 前面那段
 * `data:image/jpeg;base64,` 不是合法的 base64 字符，会直接抛异常。
 * 所以先用 substringAfter 截掉，再做容错解码。
 */
private fun decodeDataUri(uri: String): ImageBitmap? {
    val base64 = uri.substringAfter("base64,", uri)
    if (base64.isBlank()) return null
    return runCatching {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}

/**
 * 滑块验证码弹窗。
 *
 * 交互是「拖碎片 → 确认」，不是「拖到底自动过」：真正的判定在后端，
 * 这里只是把用户拖到的位置换算成底图像素坐标的 x 交上去。所以必须给一个
 * 明确的确认动作，否则松手就发请求的话，用户想微调位置会连续触发好几次。
 *
 * @param onConfirm 回调参数 x 是**底图原始像素**坐标，不是屏幕 dp。
 *                  缩放换算在这里做完了，上层直接透传给接口。
 */
@Composable
fun SlideCaptchaDialog(
    data: SlideCaptchaData,
    onDismiss: () -> Unit,
    onConfirm: (x: Int) -> Unit
) {
    val masterBitmap = remember(data.id) { decodeDataUri(data.masterImage) }
    val tileBitmap = remember(data.id) { decodeDataUri(data.tileImage) }

    // 尺寸以接口给的为准，缺字段时退回位图自身尺寸。
    // 用接口的尺寸而不是位图尺寸，是因为 x 要按后端认知的坐标系上报，
    // 万一图片被后端缩过、位图和 master_size 不一致，按位图算会整体偏移。
    val masterWidth = data.masterSize?.width?.takeIf { it > 0 }
        ?: masterBitmap?.width
        ?: 0
    val masterHeight = data.masterSize?.height?.takeIf { it > 0 }
        ?: masterBitmap?.height
        ?: 0
    val tileWidth = data.tileSize?.width?.takeIf { it > 0 }
        ?: tileBitmap?.width
        ?: 0
    val tileHeight = data.tileSize?.height?.takeIf { it > 0 }
        ?: tileBitmap?.height
        ?: 0

    // 0f..1f 表示碎片从最左滑到最右，换算成 x 时再乘上可移动范围
    var progress by remember(data.id) { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "安全验证",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = "拖动滑块，把碎片拼进底图的缺口",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // 底图 + 碎片。用 BoxWithConstraints 拿到实际显示宽度，
                    // 才能把底图像素坐标按比例换算成 dp —— 写死尺寸在不同
                    // 屏幕密度下会错位。
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val displayWidth = maxWidth
                        val aspect = if (masterWidth > 0) {
                            masterHeight.toFloat() / masterWidth
                        } else {
                            0.733f // 后端常见 300x220，兜底用这个比例
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(displayWidth * aspect)
                                .clip(RoundedCornerShape(12.dp))
                        ) {
                            masterBitmap?.let {
                                Image(
                                    bitmap = it,
                                    contentDescription = "验证码底图",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.FillBounds
                                )
                            }

                            if (tileBitmap != null && masterWidth > 0) {
                                // 底图像素 → dp 的缩放比
                                val scale = displayWidth.value / masterWidth
                                val tileWidthDp = (tileWidth * scale).dp
                                val tileHeightDp = (tileHeight * scale).dp
                                // 碎片最多能滑到右边缘，别让它滑出底图
                                val maxOffsetDp = displayWidth - tileWidthDp
                                val tileYDp = (data.tileY * scale).dp

                                Image(
                                    bitmap = tileBitmap,
                                    contentDescription = "拼图碎片",
                                    modifier = Modifier
                                        .offset(x = maxOffsetDp * progress, y = tileYDp)
                                        .size(tileWidthDp, tileHeightDp),
                                    contentScale = ContentScale.FillBounds
                                )
                            }
                        }
                    }

                    Slider(
                        value = progress,
                        onValueChange = { progress = it },
                        valueRange = 0f..1f
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("取消")
                        }
                        TextButton(
                            onClick = {
                                // 可移动范围是「底图宽 - 碎片宽」，而不是底图宽：
                                // 碎片滑到最右时左边缘只能停在底图右边缘减碎片宽的位置。
                                val maxX = (masterWidth - tileWidth).coerceAtLeast(0)
                                onConfirm((progress * maxX).roundToInt())
                            },
                            // 没拖动就不让提交：x=0 在业务上等于「碎片在最左边」，
                            // 大概率是误触，直接发请求只会白白浪费一次验证机会
                            enabled = progress > 0f
                        ) {
                            Text("确认")
                        }
                    }
                }
            }
        }
    }
}
