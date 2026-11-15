package com.yhchat.canary.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yhchat.canary.utils.NtfyNotifier

/**
 * ntfy 转发的设置组。
 *
 * 单独拆成一个文件而不是继续往 SettingsScreen 里堆：那边已经两千多行，
 * 再加三个对话框和两个输入框会彻底读不动。
 */
@Composable
internal fun NtfySettingsGroup(context: Context) {
    var showKeyDialog by remember { mutableStateOf(false) }
    var showServerDialog by remember { mutableStateOf(false) }

    // 这几个值会跟 SharedPreferences 同步，但开关之后要立刻反映到副标题上，
    // 所以用可变的 state 自己持一份，而不是每次重组都去读盘
    var enabled by remember { mutableStateOf(NtfyNotifier.isEnabled(context)) }
    var topic by remember { mutableStateOf(NtfyNotifier.getTopic(context)) }
    var server by remember { mutableStateOf(NtfyNotifier.getServer(context)) }

    SettingsGroup(
        title = "外部推送 (ntfy)",
        items = listOf(
            {
                SettingsSwitchItem(
                    icon = Icons.Default.Notifications,
                    title = "ntfy 通知",
                    subtitle = when {
                        enabled && topic.isBlank() -> "已开启，但还没生成密钥"
                        enabled -> "已开启，新消息会转发到 $server"
                        else -> "关闭。开启后即使应用被回收也能收到提醒"
                    },
                    checked = enabled,
                    onCheckedChange = { checked ->
                        // 开启时如果还没有密钥，顺手生成一个并弹窗告诉用户，
                        // 否则开了也发不出去（没有 topic 不知道往哪 POST）
                        if (checked && topic.isBlank()) {
                            val generated = NtfyNotifier.generateTopicKey()
                            NtfyNotifier.saveTopic(context, generated)
                            topic = generated
                            NtfyNotifier.setEnabled(context, true)
                            enabled = true
                            showKeyDialog = true
                        } else {
                            NtfyNotifier.setEnabled(context, checked)
                            enabled = checked
                        }
                    }
                )
            },
            {
                SettingsItemCell(
                    icon = Icons.Default.VpnKey,
                    title = "获取 ntfy 密钥",
                    subtitle = if (topic.isBlank()) {
                        "点此生成一个 8-16 位随机密钥"
                    } else {
                        "当前密钥：$topic"
                    },
                    onClick = {
                        // 每次点都重新生成：旧密钥一旦泄露，用户需要一个"换掉"的动作，
                        // 而"显示当前那个"可以直接从副标题看到
                        val generated = NtfyNotifier.generateTopicKey()
                        NtfyNotifier.saveTopic(context, generated)
                        topic = generated
                        showKeyDialog = true
                    }
                )
            },
            {
                SettingsItemCell(
                    icon = Icons.Default.Cloud,
                    title = "自定义 ntfy 服务器",
                    subtitle = server,
                    onClick = { showServerDialog = true }
                )
            },
            {
                SettingsItemCell(
                    icon = Icons.Default.CloudUpload,
                    title = "发送测试通知",
                    subtitle = if (topic.isBlank()) {
                        "先生成密钥再测试"
                    } else {
                        "往当前密钥发一条测试消息"
                    },
                    onClick = {
                        if (topic.isBlank()) {
                            Toast.makeText(context, "请先生成 ntfy 密钥", Toast.LENGTH_SHORT).show()
                        } else {
                            NtfyNotifier.sendTest(context)
                            Toast.makeText(context, "已发送，请查看 ntfy app", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        )
    )

    if (showKeyDialog) {
        NtfyKeyDialog(
            topic = topic,
            server = server,
            onRegenerate = {
                val generated = NtfyNotifier.generateTopicKey()
                NtfyNotifier.saveTopic(context, generated)
                topic = generated
            },
            onDismiss = { showKeyDialog = false }
        )
    }

    if (showServerDialog) {
        NtfyServerDialog(
            initial = server,
            onConfirm = { input ->
                val normalized = NtfyNotifier.normalizeServer(input)
                NtfyNotifier.saveServer(context, normalized)
                server = normalized
                showServerDialog = false
                if (normalized != input.trim().trimEnd('/')) {
                    // 用户填的地址被规整过（补了 scheme 或退回默认），说一声，
                    // 不然他会以为自己填的没生效
                    Toast.makeText(
                        context,
                        "已使用：$normalized",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onDismiss = { showServerDialog = false }
        )
    }
}

/**
 * 密钥展示弹窗。
 *
 * 密钥要能被复制出来 —— 用户得把它手输或粘贴到 ntfy app 里订阅，
 * 只展示不给复制等于逼着人抄。
 */
@Composable
private fun NtfyKeyDialog(
    topic: String,
    server: String,
    onRegenerate: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ntfy 密钥") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = topic,
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp, horizontal = 12.dp)
                    )
                }

                Text(
                    text = "打开 ntfy app → 添加订阅 → 服务器填 $server，" +
                        "主题(topic)填上面这串，确定之后本应用的新消息就会推送到这里。",
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = "这串就是口令，不要发给别人：谁拿到它谁就能收到你的通知。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("ntfy topic", topic))
                    Toast.makeText(context, "已复制密钥", Toast.LENGTH_SHORT).show()
                }
            ) {
                Text("复制")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onRegenerate) {
                    Text("重新生成")
                }
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
            }
        }
    )
}

/** 自定义服务器弹窗。默认 ntfy.sh，自建的填自己的域名即可 */
@Composable
private fun NtfyServerDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var input by rememberSaveable { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ntfy 服务器") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("服务器地址") },
                    placeholder = { Text(NtfyNotifier.DEFAULT_SERVER) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
                Text(
                    text = "默认 ${NtfyNotifier.DEFAULT_SERVER}。自建服务器填自己的地址，" +
                        "不写 https:// 会自动补上。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(input) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    input = NtfyNotifier.DEFAULT_SERVER
                }
            ) {
                Text("恢复默认")
            }
        }
    )
}
