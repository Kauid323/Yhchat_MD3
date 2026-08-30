package com.yhchat.canary.ui.live

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import com.yhchat.canary.ui.adaptive.YhText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yhchat.canary.data.model.LiveRoomItem
import com.yhchat.canary.ui.adaptive.YhBottomSheet
import com.yhchat.canary.ui.adaptive.YhButton
import com.yhchat.canary.ui.adaptive.YhCard
import com.yhchat.canary.ui.adaptive.YhCircularProgressIndicator
import com.yhchat.canary.ui.adaptive.YhTextButton
import com.yhchat.canary.ui.base.SystemBarUtils
import com.yhchat.canary.ui.components.ImageUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun LiveRoomsBottomSheet(
    rooms: List<LiveRoomItem>,
    isLoading: Boolean,
    joiningRoomId: String?,
    error: String?,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onJoin: (LiveRoomItem) -> Unit
) {
    val context = LocalContext.current

    YhBottomSheet(
        show = true,
        title = null,
        onDismissRequest = onDismiss,
    ) {
        val activity = context as? Activity
        val sheetColor = MaterialTheme.colorScheme.surface
        val darkIcons = sheetColor.luminance() > 0.5f
        if (activity != null) {
            SystemBarUtils.ApplyNavigationBarColor(activity, sheetColor, darkIcons)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "群语音房间",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "有房间时才显示入口，点击加入后直接进房间页。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (error != null) {
                YhCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        YhTextButton(onClick = onRetry) {
                            Text("重试")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (isLoading && rooms.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    YhCircularProgressIndicator()
                }
            } else if (rooms.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "当前没有正在进行的语音房间",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(items = rooms, key = { room -> "${room.roomId}:${room.chatId}" }) { room ->
                        YhCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            cornerRadius = 20.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = ImageUtils.createAvatarImageRequest(
                                        context = context,
                                        url = room.avatarUrl
                                    ),
                                    contentDescription = room.nickname,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                )

                                Spacer(modifier = Modifier.size(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = room.title.ifBlank { room.nickname.ifBlank { "语音房间" } },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${room.count} 人在线 · ${formatLiveRoomTime(room.createTime)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.size(12.dp))

                                YhButton(
                                    onClick = { onJoin(room) },
                                    enabled = joiningRoomId == null || joiningRoomId == room.roomId
                                ) {
                                    if (joiningRoomId == room.roomId) {
                                        YhCircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text("加入")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

private fun formatLiveRoomTime(timestampSeconds: Long): String {
    if (timestampSeconds <= 0L) return "刚刚"
    val date = Date(timestampSeconds * 1000)
    val nowCal = Calendar.getInstance()
    val msgCal = Calendar.getInstance().apply { time = date }
    val isSameYear = nowCal.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR)
    val pattern = if (isSameYear) "MM-dd HH:mm" else "yyyy-MM-dd HH:mm"
    val formatter = SimpleDateFormat(pattern, Locale.getDefault())
    return formatter.format(date)
}
