package com.yhchat.canary.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.yhchat.canary.ui.adaptive.YhAlertDialog
import com.yhchat.canary.ui.adaptive.YhButton
import com.yhchat.canary.ui.adaptive.YhCircularProgressIndicator
import com.yhchat.canary.ui.adaptive.YhIcon as Icon
import com.yhchat.canary.ui.adaptive.YhIconButton
import com.yhchat.canary.ui.adaptive.YhBottomSheet
import com.yhchat.canary.ui.adaptive.YhOutlinedButton
import com.yhchat.canary.ui.adaptive.YhSurface
import com.yhchat.canary.ui.adaptive.YhText as Text
import com.yhchat.canary.ui.adaptive.YhTextButton
import kotlinx.coroutines.launch

/**
 * 聊天输入栏组件
 */
@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "输入消息...",
    onImageClick: (() -> Unit)? = null,
    onFileClick: (() -> Unit)? = null,
    onCameraClick: (() -> Unit)? = null,
    onVideoClick: (() -> Unit)? = null,
    onDraftChange: ((String) -> Unit)? = null,
    selectedMessageType: Int = 1, // 1-文本, 3-Markdown, 8-HTML
    onMessageTypeChange: ((Int) -> Unit)? = null,
    quotedMessageText: String? = null, // 引用的消息文本
    onClearQuote: (() -> Unit)? = null, // 清除引用
    onExpressionClick: ((com.yhchat.canary.data.model.Expression) -> Unit)? = null,  // 表情点击回调
    onStickerClick: ((com.yhchat.canary.data.model.StickerItem) -> Unit)? = null,  // 表情包贴纸点击回调
    onInstructionClick: ((com.yhchat.canary.data.model.Instruction) -> Unit)? = null,  // 指令点击回调
    groupId: String? = null,  // 群聊ID，用于加载指令
    botId: String? = null,  // 机器人私聊ID，用于加载指令
    selectedInstruction: com.yhchat.canary.data.model.Instruction? = null, // 选中的指令
    onClearInstruction: (() -> Unit)? = null, // 清除指令
    focusRequester: FocusRequester? = null, // 焦点请求器
    shouldShowKeyboard: Boolean = false, // 是否应该显示键盘
    mentionedUsers: Map<String, String> = emptyMap(), // 被@的用户映射 (userId -> userName)
    onAddMentionUser: ((String, String) -> Unit)? = null, // 添加@用户回调 (userId, userName)
    onVoiceMessageSend: ((String, String, Long, Long) -> Unit)? = null, // 语音消息发送回调 (fileKey, fileHash, fileSize, audioDuration)
    chatId: String? = null, // 聊天ID
    chatType: Long = 1L, // 聊天类型：1-用户，2-群聊，3-机器人
    voiceViewModel: VoiceMessageViewModel? = null // 语音消息 ViewModel
) {
    val ctx = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    
    // 读取布局设置
    val showAddButton by rememberBooleanPreference("layout_settings", "input_show_add_button", true)
    val showMicButton by rememberBooleanPreference("layout_settings", "input_show_mic_button", true)
    val showInstructionButton by rememberBooleanPreference("layout_settings", "input_show_instruction_button", true)
    val showExpressionButton by rememberBooleanPreference("layout_settings", "input_show_expression_button", true)

    var showAttachMenu by remember { mutableStateOf(false) }

    var inputLineCount by remember { mutableStateOf(1) }
    var showExpandedEditor by remember { mutableStateOf(false) }
    var expandedEditorText by remember { mutableStateOf(text) }

    // 读取内容设置：是否允许发送空文本消息设置
    val isSendTextAllowEmptySetting by rememberBooleanPreference("send_text_settings", "send_text_allow_empty", false)

    fun insertMentionPlaceholder(text: String, userName: String): String {
        val lastAtIndex = text.lastIndexOf('@')
        if (lastAtIndex == -1) {
            val prefix = if (text.isEmpty() || text.endsWith(" ")) text else "$text "
            return "${prefix}@${userName} "
        }

        val beforeAt = text.substring(0, lastAtIndex + 1)
        val afterAt = text.substring(lastAtIndex + 1)
        val firstWhitespaceIndex = afterAt.indexOfFirst { it.isWhitespace() }
        val tail = if (firstWhitespaceIndex >= 0) afterAt.substring(firstWhitespaceIndex) else ""
        return "${beforeAt}${userName} ${tail}".replace("  ", " ")
    }

    var showExpressionPicker by remember { mutableStateOf(false) }
    var showInstructionPicker by remember { mutableStateOf(false) }
    var isVoiceMode by remember { mutableStateOf(false) }
    
    // 语音相关状态
    val voiceState = voiceViewModel?.voiceState?.collectAsState()?.value
    val isRecording = voiceState?.isRecording ?: false
    val isProcessing = voiceState?.isProcessing ?: false
    val isUploading = voiceState?.isUploading ?: false
    
    // 语音转文字对话框状态
    var showVoiceToTextDialog by remember { mutableStateOf(false) }
    var pendingVoiceFile by remember { mutableStateOf<java.io.File?>(null) }
    var isConvertingToText by remember { mutableStateOf(false) }
    var voiceToTextProgress by remember { mutableStateOf("") }
    
    // 处理返回手势 - 当选择器显示时拦截返回手势
    BackHandler(enabled = showExpressionPicker || showInstructionPicker) {
        when {
            showExpressionPicker -> showExpressionPicker = false
            showInstructionPicker -> showInstructionPicker = false
        }
    }

    LaunchedEffect(text) {
        if (!showExpandedEditor) {
            expandedEditorText = text
        }
    }

    // 文件选择器 - 使用OpenDocument以获取完整权限
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            if (voiceViewModel != null && chatId != null) {
                Log.d("ChatInputBar", "audioPicker result uri=$uri")
                try {
                    ctx.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    Log.d("ChatInputBar", "takePersistableUriPermission failed (may be temporary): ${e.message}")
                }
                voiceViewModel.selectAudioFromStorage(
                    context = ctx,
                    uri = uri,
                    chatId = chatId,
                    chatType = chatType,
                    onSuccess = { fileKey, fileHash, fileSize, duration ->
                        onVoiceMessageSend?.invoke(fileKey, fileHash, fileSize, duration)
                        isVoiceMode = false
                    }
                )
            } else {
                Log.w(
                    "ChatInputBar",
                    "audioPicker ignored: voiceViewModel=${voiceViewModel != null} chatId=$chatId chatType=$chatType groupId=$groupId botId=$botId isVoiceMode=$isVoiceMode",
                    Throwable("ChatInputBar missing voice params")
                )
                Toast.makeText(ctx, "语音功能参数缺失", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // 权限请求
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted && voiceViewModel != null) {
            voiceViewModel.startRecording(ctx)
        } else {
            Toast.makeText(ctx, "需要麦克风权限才能录音", Toast.LENGTH_SHORT).show()
        }
    }
    val currentOnCameraClick by rememberUpdatedState(onCameraClick)
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            currentOnCameraClick?.invoke()
        } else {
            Toast.makeText(ctx, "需要相机权限才能拍照", Toast.LENGTH_SHORT).show()
        }
    }
    
    // 监听错误状态
    LaunchedEffect(voiceState?.error) {
        voiceState?.error?.let { error ->
            Toast.makeText(ctx, error, Toast.LENGTH_SHORT).show()
            voiceViewModel?.clearError()
        }
    }
    val coroutineScope = rememberCoroutineScope()

    data class MentionContext(val startIndex: Int, val keyword: String)
    val mentionContext by remember(text) {
        derivedStateOf {
            val lastAtIndex = text.lastIndexOf('@')
            if (lastAtIndex == -1) {
                null
            } else {
                val afterAt = text.substring(lastAtIndex + 1)
                if (afterAt.any { it.isWhitespace() }) {
                    null
                } else {
                    MentionContext(lastAtIndex, afterAt)
                }
            }
        }
    }
    var lastDismissedMentionIndex by remember { mutableStateOf(-1) }
    var lastMentionTriggerIndex by remember { mutableStateOf(-1) }
    var mentionPickerKeyword by remember { mutableStateOf("") }
    var keepMentionPickerVisible by remember { mutableStateOf(false) }

    LaunchedEffect(mentionContext?.startIndex) {
        mentionContext?.let { context ->
            lastMentionTriggerIndex = context.startIndex
            mentionPickerKeyword = context.keyword
            keepMentionPickerVisible = true
        }
    }

    LaunchedEffect(text) {
        if (!text.contains('@')) {
            keepMentionPickerVisible = false
            lastDismissedMentionIndex = -1
        }
    }

    val showMentionPicker = groupId != null && keepMentionPickerVisible &&
        (mentionContext?.startIndex != lastDismissedMentionIndex || mentionContext == null)

    val chatPrefs = remember {
        ctx.getSharedPreferences("chat_settings", android.content.Context.MODE_PRIVATE)
    }

    var defaultSendMessageType by remember {
        mutableStateOf(
            chatPrefs.getInt("default_send_message_type", 1)
                .let { if (it == 3 || it == 8 || it == 1 || it == 14) it else 1 }
        )
    }

    var longPressSendMarkdownEnabled by remember {
        mutableStateOf(chatPrefs.getBoolean("long_press_send_markdown_enabled", false))
    }

    var longPressSendMarkdownSeconds by remember {
        mutableStateOf(chatPrefs.getInt("long_press_send_markdown_seconds", 3).coerceAtLeast(1))
    }

    DisposableEffect(chatPrefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "long_press_send_markdown_enabled" -> {
                    longPressSendMarkdownEnabled = chatPrefs.getBoolean("long_press_send_markdown_enabled", false)
                }
                "long_press_send_markdown_seconds" -> {
                    longPressSendMarkdownSeconds = chatPrefs.getInt("long_press_send_markdown_seconds", 3).coerceAtLeast(1)
                }
                "default_send_message_type" -> {
                    defaultSendMessageType = chatPrefs.getInt("default_send_message_type", 1)
                        .let { if (it == 3 || it == 8 || it == 1 || it == 14) it else 1 }
                }
            }
        }
        chatPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { chatPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    // 使用 rememberUpdatedState 保证 pointerInput 内部逻辑捕获最新状态，且不触发 pointerInput 重启
    val currentText by rememberUpdatedState(text)
    val currentOnSendMessage by rememberUpdatedState(onSendMessage)
    val currentMtc by rememberUpdatedState(onMessageTypeChange)
    val currentSelectedMessageType by rememberUpdatedState(selectedMessageType)
    val currentLongPressSendMarkdownEnabled by rememberUpdatedState(longPressSendMarkdownEnabled)
    val currentLongPressSendMarkdownSeconds by rememberUpdatedState(longPressSendMarkdownSeconds)

    Column {
        if (showMentionPicker) {
            val currentMentionContext = mentionContext
            val resolvedKeyword = currentMentionContext?.keyword ?: mentionPickerKeyword
            GroupMemberMentionPicker(
                groupId = groupId.orEmpty(),
                keyword = resolvedKeyword,
                selectedIds = mentionedUsers.keys,
                onMemberSelected = { member ->
                    if (mentionedUsers.containsKey(member.userId)) return@GroupMemberMentionPicker
                    onAddMentionUser?.invoke(member.userId, member.name)
                    val newText = insertMentionPlaceholder(text, member.name)
                    onTextChange(newText)
                },
                onDismiss = {
                    keepMentionPickerVisible = false
                    val dismissIndex = currentMentionContext?.startIndex ?: lastMentionTriggerIndex
                    if (dismissIndex >= 0) {
                        lastDismissedMentionIndex = dismissIndex
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        // 表情选择器（显示在输入框上方，贴输入区而不是贴屏幕底部）
        if (showExpressionPicker && onExpressionClick != null) {
            ExpressionPicker(
                onExpressionClick = { expression ->
                    onExpressionClick.invoke(expression)
                    showExpressionPicker = false
                },
                onStickerClick = { stickerItem ->
                    onStickerClick?.invoke(stickerItem)
                    showExpressionPicker = false
                },
                onDefaultExpressionClick = { expressionName ->
                    onTextChange(text + expressionName)
                },
                onDismiss = { showExpressionPicker = false },
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        // 指令选择器（显示在输入框上方）
        if (showInstructionPicker && onInstructionClick != null && (groupId != null || botId != null)) {
            InstructionPicker(
                groupId = groupId,
                botId = botId,
                onInstructionClick = { instruction ->
                    onInstructionClick.invoke(instruction)
                    showInstructionPicker = false
                },
                onDismiss = { showInstructionPicker = false }
            )
        }

        // 主输入栏容器
        Column(
            modifier = modifier.fillMaxWidth()
        ) {
            // 引用消息显示框
            if (quotedMessageText != null) {
                QuotedMessageBar(
                    quotedText = quotedMessageText,
                    onClearQuote = { onClearQuote?.invoke() }
                )
            }
            
            // 指令显示框
            if (selectedInstruction != null) {
                InstructionBar(
                    instruction = selectedInstruction,
                    onClearInstruction = { onClearInstruction?.invoke() }
                )
            }
            
            // 整体输入框背景 - 圆角矩形，包含所有按钮和输入框
            YhSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),  // 大圆角
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),  // 半透明背景
                shadowElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 加号按钮
                    if (showAddButton) {
                        YhIconButton(
                            onClick = { 
                                showAttachMenu = !showAttachMenu
                                showExpressionPicker = false
                                showInstructionPicker = false
                                isVoiceMode = false
                                if (showAttachMenu) {
                                    keyboardController?.hide()
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "附件",
                                tint = if (showAttachMenu) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    
                    // 麦克风按钮
                    if (showMicButton) {
                        YhIconButton(
                            onClick = { 
                                isVoiceMode = !isVoiceMode
                                showAttachMenu = false
                                showExpressionPicker = false
                                showInstructionPicker = false
                                if (isVoiceMode) {
                                    keyboardController?.hide()
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isVoiceMode) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = if (isVoiceMode) "关闭语音" else "语音",
                                tint = if (isVoiceMode) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                        
                    // 输入框或语音按钮区域
                    if (isVoiceMode) {
                        // 语音模式：显示"长按说话"和"从存储选取"按钮
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 长按说话按钮
                            YhSurface(
                                modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .border(
                                    1.dp,
                                    if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(18.dp)
                                )
                                .pointerInput(voiceViewModel, chatId, chatType, isProcessing, isUploading) {
                                    detectTapGestures(
                                        onPress = {
                                            Log.d("ChatInputBar", "voice press down")

                                            if (isProcessing || isUploading) {
                                                Log.d("ChatInputBar", "voice press ignored: isProcessing=$isProcessing isUploading=$isUploading")
                                                return@detectTapGestures
                                            }

                                            if (voiceViewModel == null || chatId == null) {
                                                Log.w(
                                                    "ChatInputBar",
                                                    "press ignored: voiceViewModel=${voiceViewModel != null} chatId=$chatId chatType=$chatType groupId=$groupId botId=$botId isVoiceMode=$isVoiceMode",
                                                    Throwable("ChatInputBar missing voice params")
                                                )
                                                Toast.makeText(ctx, "语音功能参数缺失", Toast.LENGTH_SHORT).show()
                                                return@detectTapGestures
                                            }

                                            val hasPermission = ContextCompat.checkSelfPermission(
                                                ctx,
                                                Manifest.permission.RECORD_AUDIO
                                            ) == PackageManager.PERMISSION_GRANTED

                                            if (!hasPermission) {
                                                Log.d("ChatInputBar", "request RECORD_AUDIO permission")
                                                Toast.makeText(ctx, "请授予麦克风权限", Toast.LENGTH_SHORT).show()
                                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                return@detectTapGestures
                                            }

                                            Log.d("ChatInputBar", "startRecording")
                                            voiceViewModel.startRecording(ctx)

                                            val released = tryAwaitRelease()
                                            if (released) {
                                                Log.d("ChatInputBar", "voice released, show dialog")
                                                // 停止录音但不上传，等待用户选择
                                                voiceViewModel.stopRecordingOnly(ctx) { file ->
                                                    if (file != null) {
                                                        pendingVoiceFile = file
                                                        showVoiceToTextDialog = true
                                                    } else {
                                                        Toast.makeText(ctx, "录音失败", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            } else {
                                                Log.d("ChatInputBar", "cancelRecording")
                                                voiceViewModel.cancelRecording()
                                            }
                                        }
                                    )
                                },
                                color = Color.Transparent,
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = when {
                                                isRecording -> "正在录制..."
                                                isUploading -> "上传中..."
                                                isProcessing -> "处理中..."
                                                else -> "长按说话"
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = when {
                                                isRecording -> MaterialTheme.colorScheme.error
                                                isUploading || isProcessing -> MaterialTheme.colorScheme.primary
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                }
                            }
                            
                            // 从存储选取按钮
                            YhOutlinedButton(
                                onClick = {
                                    audioPickerLauncher.launch(arrayOf("audio/*"))
                                },
                                enabled = !isProcessing && !isUploading && !isRecording,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp),
                                shape = RoundedCornerShape(18.dp),
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (isProcessing || isUploading) "处理中..." else "从存储选取",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isProcessing || isUploading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                    } else {
                        // 文本模式：显示输入框
                        BasicTextField(
                            value = text,
                            onValueChange = { newText: String ->
                                onTextChange(newText)
                                onDraftChange?.invoke(newText)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 36.dp, max = 90.dp)
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                                .pointerInput(Unit) {
                                    detectTapGestures {
                                        focusRequester?.requestFocus()
                                        keyboardController?.show()
                                    }
                                }
                                .onFocusChanged { state ->
                                    if (state.isFocused) {
                                        showExpressionPicker = false
                                    }
                                }
                                .let { modifier ->
                                    if (focusRequester != null) {
                                        modifier.focusRequester(focusRequester)
                                    } else {
                                        modifier
                                    }
                                },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            maxLines = 3,
                            onTextLayout = { result ->
                                inputLineCount = result.lineCount
                            },
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (text.isEmpty()) {
                                        val effectivePlaceholder = when {
                                            selectedInstruction != null && selectedInstruction.hintText.isNotEmpty() -> 
                                                selectedInstruction.hintText
                                            else -> placeholder
                                        }
                                        Text(
                                            text = effectivePlaceholder,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )

                        // 当输入内容过多（超过2行或含换行或字数多）时，显示展开按钮
                        if (inputLineCount >= 2 || text.contains("\n") || text.length > 50) {
                            YhIconButton(
                                onClick = {
                                    expandedEditorText = text
                                    showExpandedEditor = true
                                    showAttachMenu = false
                                    showExpressionPicker = false
                                    showInstructionPicker = false
                                    isVoiceMode = false
                                    keyboardController?.hide()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = "展开输入框",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    
                    // 指令按钮 - 群聊 或 机器人私聊
                    if (showInstructionButton && onInstructionClick != null && (groupId != null || botId != null)) {
                        YhIconButton(
                            onClick = { 
                                showInstructionPicker = !showInstructionPicker
                                showAttachMenu = false
                                showExpressionPicker = false
                                if (showInstructionPicker) {
                                    keyboardController?.hide()
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = "指令",
                                tint = if (showInstructionPicker) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    
                    // 表情按钮
                    if (showExpressionButton) {
                        YhIconButton(
                            onClick = { 
                                showExpressionPicker = !showExpressionPicker
                                showAttachMenu = false
                                showInstructionPicker = false
                                if (showExpressionPicker) {
                                    keyboardController?.hide()
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.EmojiEmotions,
                                contentDescription = "表情",
                                tint = if (showExpressionPicker) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    
                    // 发送按钮 - 圆形背景
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                if (text.isNotEmpty()) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.surfaceVariant,
                                CircleShape
                            )
                            .clip(CircleShape)
                            .pointerInput(currentLongPressSendMarkdownEnabled, currentLongPressSendMarkdownSeconds) {
                                detectTapGestures(
                                    onTap = {
                                        if (currentText.isNotEmpty() || isSendTextAllowEmptySetting) {
                                            Log.d("ChatInputBar", "Send button tapped")
                                            currentOnSendMessage()
                                        }
                                    },
                                    onLongPress = {
                                        val mtc = currentMtc
                                        if ((currentText.isNotEmpty() || isSendTextAllowEmptySetting) && currentLongPressSendMarkdownEnabled && mtc != null) {
                                            Log.d("ChatInputBar", "Send button long pressed -> Markdown")
                                            val previousType = currentSelectedMessageType
                                            coroutineScope.launch {
                                                if (previousType != 3) {
                                                    mtc.invoke(3)
                                                }
                                                currentOnSendMessage()
                                                if (previousType != 3) {
                                                    mtc.invoke(previousType)
                                                }
                                            }
                                        } else if (currentText.isNotEmpty() || isSendTextAllowEmptySetting) {
                                            // 如果没开启长按发送 Markdown，则长按也作为普通发送
                                            Log.d("ChatInputBar", "Send button long pressed (normal send)")
                                            currentOnSendMessage()
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送",
                            tint = if (text.isNotEmpty())
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            
            // 附件菜单 DropdownMenu
            AttachmentMenu(
                expanded = showAttachMenu,
                onDismissRequest = { showAttachMenu = false },
                onImageClick = {
                    onImageClick?.invoke()
                    showAttachMenu = false
                },
                onFileClick = {
                    onFileClick?.invoke()
                    showAttachMenu = false
                },
                onCameraClick = {
                    when {
                        onCameraClick == null -> Unit
                        ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                            onCameraClick.invoke()
                        }
                        else -> {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                    showAttachMenu = false
                },
                onVideoClick = {
                    onVideoClick?.invoke()
                    showAttachMenu = false
                },
                onTextClick = if (onMessageTypeChange != null) {
                    {
                        onMessageTypeChange.invoke(1)
                        showAttachMenu = false
                    }
                } else null,
                onHtmlClick = if (onMessageTypeChange != null) {
                    {
                        onMessageTypeChange.invoke(8)
                        showAttachMenu = false
                    }
                } else null,
                onMarkdownClick = if (onMessageTypeChange != null) {
                    {
                        onMessageTypeChange.invoke(3)
                        showAttachMenu = false
                    }
                } else null,
                onA2UiClick = if (onMessageTypeChange != null) {
                    {
                        onMessageTypeChange.invoke(14)
                        showAttachMenu = false
                    }
                } else null,
                defaultMessageType = defaultSendMessageType,
                onDefaultMessageTypeChange = { newDefaultType ->
                    defaultSendMessageType = newDefaultType
                    chatPrefs.edit().putInt("default_send_message_type", newDefaultType).apply()
                },
                selectedMessageType = selectedMessageType
            )

            if (showExpandedEditor) {
                ExpandedInputEditorSheet(
                    text = expandedEditorText,
                    placeholder = placeholder,
                    selectedInstruction = selectedInstruction,
                    onTextChange = { expandedEditorText = it },
                    onDismiss = {
                        onTextChange(expandedEditorText)
                        onDraftChange?.invoke(expandedEditorText)
                        showExpandedEditor = false
                    }
                )
            }
        }  // 关闭主输入栏容器Column

        // 语音转文字选择对话框
        if (showVoiceToTextDialog && pendingVoiceFile != null) {
            VoiceToTextChoiceDialog(
                isConverting = isConvertingToText,
                progress = voiceToTextProgress,
                onDirectSend = {
                    // 直接发送语音
                    showVoiceToTextDialog = false
                    val file = pendingVoiceFile
                    pendingVoiceFile = null
                    if (file != null && voiceViewModel != null && chatId != null) {
                        voiceViewModel.uploadVoiceFile(
                            context = ctx,
                            file = file,
                            chatId = chatId,
                            chatType = chatType,
                            onSuccess = { fileKey, fileHash, fileSize, duration ->
                                onVoiceMessageSend?.invoke(fileKey, fileHash, fileSize, duration)
                                isVoiceMode = false
                            }
                        )
                    }
                },
                onConvertToText = {
                    // 转文字
                    isConvertingToText = true
                    voiceToTextProgress = "正在识别语音..."
                    val file = pendingVoiceFile
                    if (file != null) {
                        coroutineScope.launch {
                            com.yhchat.canary.utils.STTUtils.speechToTextFromFile(
                                context = ctx,
                                audioFile = file,
                                onProgress = { progress -> voiceToTextProgress = progress }
                            ).fold(
                                onSuccess = { text ->
                                    // 插入到输入框
                                    onTextChange(if (text.isNotBlank()) "$text " else text)
                                    showVoiceToTextDialog = false
                                    pendingVoiceFile = null
                                    isConvertingToText = false
                                    voiceToTextProgress = ""
                                    isVoiceMode = false
                                    // 删除临时文件
                                    file.delete()
                                },
                                onFailure = { error ->
                                    voiceToTextProgress = "识别失败: ${error.message}"
                                    isConvertingToText = false
                                    Toast.makeText(ctx, "语音转文字失败: ${error.message}", Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    }
                },
                onCancel = {
                    showVoiceToTextDialog = false
                    pendingVoiceFile?.delete()
                    pendingVoiceFile = null
                    isConvertingToText = false
                    voiceToTextProgress = ""
                }
            )
        }
    }
}

/**
 * 语音转文字选择对话框
 */
@Composable
private fun VoiceToTextChoiceDialog(
    isConverting: Boolean,
    progress: String,
    onDirectSend: () -> Unit,
    onConvertToText: () -> Unit,
    onCancel: () -> Unit
) {
    YhAlertDialog(
        onDismissRequest = { if (!isConverting) onCancel() },
        title = {
            Text(
                text = if (isConverting) "语音转文字" else "是否转文字？",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            if (isConverting) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    YhCircularProgressIndicator()
                    Text(
                        text = progress,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = "录音完成，请选择发送方式",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            if (!isConverting) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    YhTextButton(onClick = onDirectSend) {
                        Text("直接发送")
                    }
                    YhButton(onClick = onConvertToText) {
                        Text("转文字")
                    }
                }
            }
        },
        dismissButton = {
            if (!isConverting) {
                YhTextButton(onClick = onCancel) {
                    Text("取消")
                }
            }
        }
    )
}

/**
 * 指令显示条
 */
@Composable
fun InstructionBar(
    instruction: com.yhchat.canary.data.model.Instruction,
    onClearInstruction: () -> Unit
) {
    YhSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "/${instruction.name}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (instruction.desc.isNotEmpty()) {
                    Text(
                        text = instruction.desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
            
            YhIconButton(
                onClick = onClearInstruction,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "清除指令",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ExpandedInputEditorSheet(
    text: String,
    placeholder: String,
    selectedInstruction: com.yhchat.canary.data.model.Instruction?,
    onTextChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    YhBottomSheet(
        title = "展开输入",
        onDismissRequest = onDismiss,
        fullScreen = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            YhSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 12.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    BasicTextField(
                        value = text,
                        onValueChange = onTextChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(top = 12.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        decorationBox = { innerTextField ->
                            Box(modifier = Modifier.fillMaxSize()) {
                                if (text.isEmpty()) {
                                    Text(
                                        text = selectedInstruction?.hintText?.takeIf { it.isNotEmpty() } ?: placeholder,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }
            }
        }
    }
}
