package com.yhchat.canary.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yhchat.canary.ui.components.rememberBooleanPreference
import com.yhchat.canary.utils.splitfile.SplitFileSettings

/**
 * 非 VIP 分块上传设置。
 *
 * 普通账号单文件有大小上限。开启后，选择大文件时会自动切成多块分别上传，
 * 再生成一个很小的 `.splitfiles` 清单文件发出去，接收端据此还原。
 */
@Composable
internal fun SplitUploadSettingsGroup(context: Context) {
    val enabled by rememberBooleanPreference(
        preferencesName = SPLIT_UPLOAD_PREFS,
        key = KEY_SPLIT_UPLOAD_ENABLED,
        defaultValue = false
    )

    // 分块大小：滑块 + 输入框双向同步。
    // 滑块给"大致调"，输入框给"精确填"，两者都有人用。
    var chunkMb by remember {
        mutableFloatStateOf(SplitFileSettings.chunkSizeMb(context).toFloat())
    }
    var chunkText by remember {
        mutableStateOf(SplitFileSettings.chunkSizeMb(context).toString())
    }

    fun persistChunkMb(value: Int) {
        val clamped = value.coerceIn(
            SplitFileSettings.MIN_CHUNK_MB,
            SplitFileSettings.MAX_CHUNK_MB
        )
        SplitFileSettings.setChunkSizeMb(context, clamped)
        chunkMb = clamped.toFloat()
        chunkText = clamped.toString()
    }

    SettingsGroup(
        title = "大文件发送",
        items = listOf(
            {
                SettingsSwitchItem(
                    icon = Icons.Default.ContentCut,
                    title = "非 VIP 分块上传",
                    subtitle = if (enabled) {
                        "已开启。发送大文件时会切成多块上传，接收端收到 .splitfiles 清单后可还原"
                    } else {
                        "关闭。开启后可绕过单文件大小限制，代价是接收端需要本客户端才能还原"
                    },
                    checked = enabled,
                    onCheckedChange = { value ->
                        context.getSharedPreferences(SPLIT_UPLOAD_PREFS, Context.MODE_PRIVATE)
                            .edit()
                            .putBoolean(KEY_SPLIT_UPLOAD_ENABLED, value)
                            .apply()
                    }
                )
            },
            {
                // 分块大小设置。放在开关下面，但开关关着时仍然可见可调 ——
                // 用户可以先把参数配好再开开关，不必来回切换。
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "分块大小",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "范围 ${SplitFileSettings.MIN_CHUNK_MB}–${SplitFileSettings.MAX_CHUNK_MB} MB，默认 ${SplitFileSettings.DEFAULT_CHUNK_MB} MB",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        OutlinedTextField(
                            value = chunkText,
                            onValueChange = { raw ->
                                // 只允许数字，最多两位
                                val digits = raw.filter { it.isDigit() }.take(2)
                                chunkText = digits
                                digits.toIntOrNull()?.let { v ->
                                    if (v in SplitFileSettings.MIN_CHUNK_MB..SplitFileSettings.MAX_CHUNK_MB) {
                                        chunkMb = v.toFloat()
                                    }
                                }
                            },
                            modifier = Modifier.width(88.dp),
                            singleLine = true,
                            suffix = { Text("MB") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Slider(
                        value = chunkMb,
                        onValueChange = { v ->
                            chunkMb = v
                            chunkText = v.toInt().toString()
                        },
                        onValueChangeFinished = {
                            persistChunkMb(chunkMb.toInt())
                        },
                        valueRange = SplitFileSettings.MIN_CHUNK_MB.toFloat()..
                            SplitFileSettings.MAX_CHUNK_MB.toFloat(),
                        // 整数档位：1MB 一档
                        steps = SplitFileSettings.MAX_CHUNK_MB - SplitFileSettings.MIN_CHUNK_MB - 1
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                val parsed = chunkText.toIntOrNull()
                                if (parsed == null ||
                                    parsed !in SplitFileSettings.MIN_CHUNK_MB..SplitFileSettings.MAX_CHUNK_MB
                                ) {
                                    Toast.makeText(
                                        context,
                                        "分块大小需在 ${SplitFileSettings.MIN_CHUNK_MB}–${SplitFileSettings.MAX_CHUNK_MB} MB 之间",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    // 输入非法时回滚显示，避免输入框停在一个无效值上
                                    chunkText = SplitFileSettings.chunkSizeMb(context).toString()
                                    chunkMb = SplitFileSettings.chunkSizeMb(context).toFloat()
                                } else {
                                    persistChunkMb(parsed)
                                    Toast.makeText(
                                        context,
                                        "分块大小已设为 $parsed MB",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        ) { Text("应用") }

                        TextButton(onClick = {
                            persistChunkMb(SplitFileSettings.DEFAULT_CHUNK_MB)
                        }) { Text("恢复默认") }
                    }

                    if (enabled) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "块越小上传轮次越多、清单越长；块越大单次上传越容易超时。建议保持默认。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        )
    )
}

const val SPLIT_UPLOAD_PREFS = "split_upload_settings"
const val KEY_SPLIT_UPLOAD_ENABLED = "split_upload_enabled"

/** 供发送流程读取 */
fun isSplitUploadEnabled(context: Context): Boolean {
    return context.getSharedPreferences(SPLIT_UPLOAD_PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_SPLIT_UPLOAD_ENABLED, false)
}
