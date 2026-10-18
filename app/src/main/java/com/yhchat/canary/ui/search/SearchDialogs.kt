package com.yhchat.canary.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.yhchat.canary.data.model.BotInfo
import com.yhchat.canary.data.model.GroupDetail
import com.yhchat.canary.data.model.UserInfo
import com.yhchat.canary.ui.components.ImageUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GroupInfoDialog(
    group: GroupDetail?,
    onDismiss: () -> Unit,
    onAdd: (GroupDetail) -> Unit,
    isAdding: Boolean
) {
    if (group == null) return
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 群聊头像
                val avatarUrl = group.avatarUrl
                AsyncImage(
                    model = if (!avatarUrl.isNullOrBlank()) {
                        ImageUtils.createAvatarImageRequest(
                            context = LocalContext.current,
                            url = avatarUrl
                        )
                    } else {
                        null
                    },
                    contentDescription = "群聊头像",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 群聊名称
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 群聊信息
                InfoItem(label = "群ID", value = group.groupId)
                InfoItem(label = "成员数", value = "${group.memberCount} 人")
                InfoItem(label = "创建者", value = group.createBy)
                InfoItem(label = "查看历史消息", value = if (group.historyMsgEnabled) "开启" else "关闭")
                
                // 群聊简介
                if (group.introduction.isNotBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "群聊简介",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                        text = group.introduction,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("取消")
                    }
                    
                    Button(
                        onClick = { onAdd(group) },
                        enabled = !isAdding,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isAdding) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("加入群聊")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UserInfoDialog(
    user: UserInfo?,
    onDismiss: () -> Unit,
    onAdd: (UserInfo) -> Unit,
    isAdding: Boolean
) {
    if (user == null) return
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 用户头像
                val avatarUrl = user.avatarUrl
                AsyncImage(
                    model = if (!avatarUrl.isNullOrBlank()) {
                        ImageUtils.createAvatarImageRequest(
                            context = LocalContext.current,
                            url = avatarUrl
                        )
                    } else {
                        null
                    },
                    contentDescription = "用户头像",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 用户名称
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = user.nickname ?: "未知用户",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    
                    if (user.isVip == 1) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.tertiary
                        ) {
                            Text(
                                text = "VIP",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onTertiary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 用户信息
                InfoItem(label = "用户ID", value = user.userId ?: "-")
                user.registerTimeText?.let {
                    InfoItem(label = "注册时间", value = it)
                }
                user.onLineDay?.let {
                    InfoItem(label = "在线天数", value = "$it 天")
                }
                user.continuousOnLineDay?.let {
                    InfoItem(label = "连续在线", value = "$it 天")
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("取消")
                    }
                    
                    Button(
                        onClick = { onAdd(user) },
                        enabled = !isAdding,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isAdding) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("添加好友")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BotInfoDialog(
    bot: BotInfo?,
    onDismiss: () -> Unit,
    onAdd: (BotInfo) -> Unit,
    isAdding: Boolean
) {
    if (bot == null) return
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 机器人头像
                val avatarUrl = bot.avatarUrl
                AsyncImage(
                    model = if (!avatarUrl.isNullOrBlank()) {
                        ImageUtils.createBotImageRequest(
                            context = LocalContext.current,
                            url = avatarUrl
                        )
                    } else {
                        null
                    },
                    contentDescription = "机器人头像",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 机器人名称
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = bot.nickname ?: "未知机器人",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "机器人",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 机器人信息
                InfoItem(label = "机器人ID", value = bot.botId ?: "-")
                bot.headcount?.let {
                    InfoItem(label = "使用人数", value = "$it 人")
                }
                bot.createBy?.let {
                    InfoItem(label = "创建者", value = it)
                }
                bot.createTime?.let {
                    InfoItem(label = "创建时间", value = formatTimestamp(it))
                }
                
                // 机器人简介
                bot.introduction?.let { intro ->
                    if (intro.isNotBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "机器人简介",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = intro,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("取消")
                    }
                    
                    Button(
                        onClick = { onAdd(bot) },
                        enabled = !isAdding,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isAdding) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("添加机器人")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoItem(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val date = Date(timestamp * 1000L)
    val formatter = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault())
    return formatter.format(date)
}
