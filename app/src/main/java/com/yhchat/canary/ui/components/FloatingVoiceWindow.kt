package com.yhchat.canary.ui.components

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.yhchat.canary.ui.adaptive.YhButton
import com.yhchat.canary.ui.adaptive.YhCard
import com.yhchat.canary.ui.adaptive.YhCircularProgressIndicator
import com.yhchat.canary.ui.adaptive.YhFilterChip
import com.yhchat.canary.ui.adaptive.YhHorizontalDivider
import com.yhchat.canary.ui.adaptive.YhIcon as Icon
import com.yhchat.canary.ui.adaptive.YhIconButton
import com.yhchat.canary.ui.adaptive.YhSurface
import com.yhchat.canary.ui.adaptive.YhLinearProgressIndicator
import com.yhchat.canary.ui.adaptive.YhOutlinedButton
import com.yhchat.canary.ui.adaptive.YhOutlinedTextField
import com.yhchat.canary.ui.adaptive.YhSegmentedControl
import com.yhchat.canary.ui.adaptive.YhSlider
import com.yhchat.canary.ui.adaptive.YhText as Text
import com.yhchat.canary.ui.adaptive.YhTextButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * TTS引擎信息数据类（需要在顶层定义，供多个函数使用）
 */
data class TtsEngineInfo(
    val packageName: String,
    val label: String,
    val isInstalled: Boolean
)

/**
 * 浮动语音窗口组件
 * @param chatId 聊天ID
 * @param chatType 聊天类型
 * @param onClose 关闭回调
 * @param onSendAudio 发送音频回调
 */
@Composable
fun FloatingVoiceWindow(
    chatId: String,
    chatType: Int,
    onClose: () -> Unit,
    onSendAudio: (Uri) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // 窗口相关状态
    var windowOffset by remember { mutableStateOf(DpOffset(0.dp, 0.dp)) }
    val windowWidth = 400.dp
    val windowHeight = 600.dp
    
    // Tab选择
    var selectedTab by remember { mutableIntStateOf(0) }
    
    // 保存的音频列表
    var savedAudios by remember { mutableStateOf<List<SavedAudioItem>>(emptyList()) }
    var isLoadingAudios by remember { mutableStateOf(false) }
    
    // TTS相关状态
    var ttsText by remember { mutableStateOf("") }
    var isTtsPlaying by remember { mutableStateOf(false) }
    var ttsProgress by remember { mutableFloatStateOf(0f) }
    var ttsError by remember { mutableStateOf<String?>(null) }
    var isTtsSynthesizing by remember { mutableStateOf(false) }
    var ttsSynthesisError by remember { mutableStateOf<String?>(null) }
    var ttsRetryCount by remember { mutableIntStateOf(0) }
    
    // TTS参数设置
    var ttsSpeechRate by remember { mutableFloatStateOf(1.0f) } // 语速 0.5-2.0
    var ttsPitch by remember { mutableFloatStateOf(1.0f) } // 音调 0.5-2.0
    var ttsVolume by remember { mutableFloatStateOf(1.0f) } // 音量 0.0-1.0
    
    // TTS引擎相关
    var availableTtsEngines by remember { mutableStateOf<List<TtsEngineInfo>>(emptyList()) }
    var selectedTtsEngine by remember { mutableStateOf<String?>(null) }
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    var customEnginePackage by remember { mutableStateOf("") }
    var showEngineSelector by remember { mutableStateOf(false) }
    var isInitializing by remember { mutableStateOf(false) }
    var initTimeout by remember { mutableStateOf(false) }
    
    val openTtsSettings: () -> Unit = remember {
        {
            try {
                // 尝试打开TTS设置页面
                val intent = Intent("com.android.settings.TTS_SETTINGS")
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            } catch (e: Exception) {
                // 如果TTS设置页面不存在，打开通用设置
                try {
                    val intent = Intent(Settings.ACTION_SETTINGS)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                } catch (e2: Exception) {
                    Log.e("FloatingVoiceWindow", "无法打开设置", e2)
                }
            }
            Unit // 显式返回Unit
        }
    }
    
    val retryTtsInitialization = remember {
        {
            ttsRetryCount++
            ttsError = null
        }
    }
    
    // 初始化TTS引擎（使用最佳实践）
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            // Log.d("FloatingVoiceWindow", "🎤 ========== TTS初始化开始 ==========")
            // Log.d("FloatingVoiceWindow", "📱 设备信息: SDK=${Build.VERSION.SDK_INT}, 型号=${Build.MODEL}")
            
            // 1. 检测所有可用TTS引擎
            val engines = detectAllTtsEngines(context)
            withContext(Dispatchers.Main) {
                availableTtsEngines = engines
            }
            
            // 2. 寻找最佳引擎（优先级：Google TTS → 系统默认 → 其他）
            val bestEngine = findBestTtsEngine(engines)
            
            if (bestEngine != null) {
                // Log.d("FloatingVoiceWindow", "🎯 选择最佳引擎: ${bestEngine.label} (${bestEngine.packageName})")
                withContext(Dispatchers.Main) {
                    selectedTtsEngine = bestEngine.packageName
                }
            } else {
                Log.e("FloatingVoiceWindow", "❌ 未找到可用的TTS引擎")
                withContext(Dispatchers.Main) {
                    ttsError = "未检测到TTS引擎，请安装Google文字转语音或其他TTS应用"
                }
            }
        }
    }
    
    // 初始化TTS实例（使用最佳实践）
    DisposableEffect(selectedTtsEngine, ttsRetryCount) {
        val currentEngine = selectedTtsEngine
        var tts: TextToSpeech? = null
        var timeoutJob: kotlinx.coroutines.Job? = null
        
        // Log.d("FloatingVoiceWindow", "🎤 ========== TTS实例初始化 ========== (重试: $ttsRetryCount)")
        // Log.d("FloatingVoiceWindow", "🔧 目标引擎: ${currentEngine ?: "系统默认"}")
        
        isInitializing = true
        initTimeout = false
        
        // 设置15秒超时保护
        timeoutJob = scope.launch {
            kotlinx.coroutines.delay(15000)
            if (isInitializing) {
                Log.e("FloatingVoiceWindow", "⏰ TTS初始化超时（15秒）")
                initTimeout = true
                isInitializing = false
                ttsError = "TTS初始化超时，请检查系统TTS设置"
            }
        }
        
        tts = if (!currentEngine.isNullOrBlank()) {
            // Log.d("FloatingVoiceWindow", "📦 创建指定引擎TTS: $currentEngine")
            try {
                TextToSpeech(context, { status ->
                    timeoutJob?.cancel()
                    isInitializing = false
                    
                    // Log.d("FloatingVoiceWindow", "📡 TTS初始化回调: status=$status (${getStatusName(status)}), engine=$currentEngine")
                
                if (status == TextToSpeech.SUCCESS) {
                    tts?.let { engine ->
                        // Log.d("FloatingVoiceWindow", "✅ TTS引擎初始化成功")
                        
                        // 1. 设置语言
                        val languageResult = engine.setLanguage(Locale.CHINESE)
                        when (languageResult) {
                            TextToSpeech.LANG_AVAILABLE,
                            TextToSpeech.LANG_COUNTRY_AVAILABLE,
                            TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> {
                                // Log.d("FloatingVoiceWindow", "✅ 中文语言设置成功: $languageResult")
                                
                                // 2. 配置音频属性（关键！）
                                configureAudioAttributes(engine)
                                
                                // 3. 应用语音参数
                                engine.setSpeechRate(ttsSpeechRate)
                                engine.setPitch(ttsPitch)
                                // Log.d("FloatingVoiceWindow", "🎚️ 参数配置: 语速=$ttsSpeechRate, 音调=$ttsPitch")
                                
                                // 4. 记录引擎详细信息
                                logEngineDetails(engine)
                                
                                ttsError = null
                                ttsEngine = tts
                                // Log.d("FloatingVoiceWindow", "🎉 TTS完全就绪")
                            }
                            TextToSpeech.LANG_MISSING_DATA -> {
                                Log.e("FloatingVoiceWindow", "❌ 缺少中文语言数据")
                                ttsError = "TTS缺少中文语言数据，请安装中文语音包"
                                ttsEngine = null
                            }
                            TextToSpeech.LANG_NOT_SUPPORTED -> {
                                Log.e("FloatingVoiceWindow", "❌ 不支持中文")
                                ttsError = "当前TTS引擎不支持中文"
                                ttsEngine = null
                            }
                            else -> {
                                Log.e("FloatingVoiceWindow", "❌ 语言设置失败: $languageResult")
                                ttsError = "TTS语言设置失败"
                                ttsEngine = null
                            }
                        }
                    } ?: run {
                        Log.e("FloatingVoiceWindow", "❌ TTS对象为null")
                        ttsError = "TTS对象创建失败"
                    }
                } else {
                    Log.e("FloatingVoiceWindow", "❌ TTS初始化失败: status=$status, engine=$currentEngine")
                    ttsError = "TTS引擎 '$currentEngine' 初始化失败\n\n可能原因：\n• 引擎未正确安装\n• 引擎版本不兼容\n• 缺少必要的系统组件\n\n请尝试：\n1. 重新安装该TTS引擎\n2. 或选择其他TTS引擎\n3. 或点击下方\"使用系统默认\""
                    ttsEngine = null
                }
                }, currentEngine)
            } catch (e: Exception) {
                Log.e("FloatingVoiceWindow", "❌ 创建TTS引擎异常: $currentEngine", e)
                ttsError = "创建TTS引擎失败: ${e.message}\n\n请尝试切换到系统默认引擎"
                null
            }
        } else {
            // Log.d("FloatingVoiceWindow", "📦 创建默认TTS引擎")
            try {
                TextToSpeech(context) { status ->
                    timeoutJob?.cancel()
                    isInitializing = false
                    
                    // Log.d("FloatingVoiceWindow", "📡 TTS初始化回调 (默认引擎): status=$status (${getStatusName(status)})")
                
                if (status == TextToSpeech.SUCCESS) {
                    tts?.let { engine ->
                        // Log.d("FloatingVoiceWindow", "✅ TTS引擎初始化成功 (默认引擎)")
                        
                        // 1. 设置语言
                        val languageResult = engine.setLanguage(Locale.CHINESE)
                        when (languageResult) {
                            TextToSpeech.LANG_AVAILABLE,
                            TextToSpeech.LANG_COUNTRY_AVAILABLE,
                            TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> {
                                // Log.d("FloatingVoiceWindow", "✅ 中文语言设置成功: $languageResult")
                                
                                // 2. 配置音频属性（关键！）
                                configureAudioAttributes(engine)
                                
                                // 3. 应用语音参数
                                engine.setSpeechRate(ttsSpeechRate)
                                engine.setPitch(ttsPitch)
                                // Log.d("FloatingVoiceWindow", "🎚️ 参数配置: 语速=$ttsSpeechRate, 音调=$ttsPitch")
                                
                                // 4. 记录引擎详细信息
                                logEngineDetails(engine)
                                
                                ttsError = null
                                ttsEngine = tts
                                // Log.d("FloatingVoiceWindow", "🎉 TTS完全就绪 (默认引擎)")
                            }
                            TextToSpeech.LANG_MISSING_DATA -> {
                                Log.e("FloatingVoiceWindow", "❌ 缺少中文语言数据")
                                ttsError = "TTS缺少中文语言数据，请安装中文语音包"
                                ttsEngine = null
                            }
                            TextToSpeech.LANG_NOT_SUPPORTED -> {
                                Log.e("FloatingVoiceWindow", "❌ 不支持中文")
                                ttsError = "当前TTS引擎不支持中文"
                                ttsEngine = null
                            }
                            else -> {
                                Log.e("FloatingVoiceWindow", "❌ 语言设置失败: $languageResult")
                                ttsError = "TTS语言设置失败"
                                ttsEngine = null
                            }
                        }
                    } ?: run {
                        Log.e("FloatingVoiceWindow", "❌ TTS对象为null")
                        ttsError = "TTS对象创建失败"
                    }
                } else {
                    Log.e("FloatingVoiceWindow", "❌ TTS初始化失败 (默认引擎): status=$status")
                    ttsError = "系统默认TTS引擎初始化失败\n\n请安装TTS应用或在设置中配置默认TTS引擎"
                    ttsEngine = null
                }
                }
            } catch (e: Exception) {
                Log.e("FloatingVoiceWindow", "❌ 创建默认TTS引擎异常", e)
                ttsError = "创建默认TTS引擎失败: ${e.message}"
                null
            }
        }
        
        onDispose {
            // Log.d("FloatingVoiceWindow", "🔚 TTS实例销毁")
            timeoutJob?.cancel()
            tts?.shutdown()
            isInitializing = false
        }
    }
    
    // 当TTS参数改变时更新引擎设置
    LaunchedEffect(ttsSpeechRate, ttsPitch) {
        ttsEngine?.let {
            it.setSpeechRate(ttsSpeechRate)
            it.setPitch(ttsPitch)
        }
    }
    
    // 当切换到音频列表时加载数据
    LaunchedEffect(selectedTab) {
        if (selectedTab == 0 && savedAudios.isEmpty()) {
            isLoadingAudios = true
            savedAudios = withContext(Dispatchers.IO) {
                loadSavedAudios(context)
            }
            isLoadingAudios = false
        }
    }
    
    // TTS引擎列表在初始化TTS时已经加载，这里不再重复
    
    val density = LocalDensity.current
    
    // 将DpOffset转换为IntOffset
    val intOffset = with(density) {
        IntOffset(
            windowOffset.x.roundToPx(),
            windowOffset.y.roundToPx()
        )
    }
    
    Popup(
        offset = intOffset,
        properties = PopupProperties(focusable = true),
        onDismissRequest = onClose
    ) {
        Box(
            modifier = Modifier
                .width(windowWidth)
                .height(windowHeight)
        ) {
            YhSurface(
                modifier = Modifier
                    .fillMaxSize()
                    .shadow(8.dp, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 标题栏（可拖动）
                WindowTitleBar(
                    onClose = onClose,
                    onDrag = { dragAmount ->
                        // 移动窗口
                        windowOffset = DpOffset(
                            windowOffset.x + dragAmount.x.dp,
                            windowOffset.y + dragAmount.y.dp
                        )
                    }
                )
                
                // Tab选择器
                TabSelector(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )
                
                YhHorizontalDivider()
                
                // 内容区域
                when (selectedTab) {
                    0 -> SavedAudiosTab(
                        audios = savedAudios,
                        isLoading = isLoadingAudios,
                        onRefresh = {
                            scope.launch {
                                isLoadingAudios = true
                                savedAudios = withContext(Dispatchers.IO) {
                                    loadSavedAudios(context)
                                }
                                isLoadingAudios = false
                            }
                        },
                        onAudioSelected = onSendAudio
                    )
                    1 -> TTSTab(
                        text = ttsText,
                        onTextChange = { ttsText = it },
                        isPlaying = isTtsPlaying,
                        progress = ttsProgress,
                        error = ttsError,
                        isSynthesizing = isTtsSynthesizing,
                        synthesisError = ttsSynthesisError,
                        availableEngines = availableTtsEngines,
                        selectedEngine = selectedTtsEngine,
                        onEngineSelected = { selectedTtsEngine = it },
                        speechRate = ttsSpeechRate,
                        onSpeechRateChange = { ttsSpeechRate = it },
                        pitch = ttsPitch,
                        onPitchChange = { ttsPitch = it },
                        volume = ttsVolume,
                        onVolumeChange = { ttsVolume = it },
                        availableTtsEngines = availableTtsEngines,
                        selectedTtsEngine = selectedTtsEngine,
                        onEngineChange = { engine ->
                            selectedTtsEngine = if (engine.isBlank()) null else engine
                            ttsRetryCount++ // 触发重新初始化
                        },
                        customEnginePackage = customEnginePackage,
                        onCustomEnginePackageChange = { customEnginePackage = it },
                        onApplyCustomEngine = {
                            selectedTtsEngine = if (customEnginePackage.isBlank()) null else customEnginePackage
                            ttsRetryCount++ // 触发重新初始化
                        },
                        showEngineSelector = showEngineSelector,
                        onToggleEngineSelector = { showEngineSelector = !showEngineSelector },
                        onSynthesize = {
                            // 实现TTS合成逻辑
                            scope.launch {
                                isTtsSynthesizing = true
                                ttsSynthesisError = null
                                
                                synthesizeTTS(
                                    context = context,
                                    tts = ttsEngine,
                                    text = ttsText,
                                    speechRate = ttsSpeechRate,
                                    pitch = ttsPitch,
                                    volume = ttsVolume,
                                    onProgress = { progress ->
                                        ttsProgress = progress
                                    },
                                    onComplete = { uri ->
                                        isTtsSynthesizing = false
                                        if (uri != null) {
                                            onSendAudio(uri)
                                            onClose()
                                        } else {
                                            ttsSynthesisError = "音频合成失败"
                                        }
                                    }
                                )
                            }
                        },
                        onPlayPreview = {
                            isTtsPlaying = true
                            playTTS(ttsEngine, ttsText, ttsVolume) {
                                isTtsPlaying = false
                            }
                        },
                        onStopPreview = {
                            stopTTS(ttsEngine)
                            isTtsPlaying = false
                        },
                        onOpenTtsSettings = openTtsSettings,
                        onRetryInitialization = retryTtsInitialization
                    )
                }
            }
        }
    }
}
}

/**
 * 窗口标题栏
 */
@Composable
fun WindowTitleBar(
    onClose: () -> Unit,
    onDrag: (Offset) -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount)
                }
            }
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 拖动指示器
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .width(20.dp)
                            .height(2.dp)
                            .background(
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f),
                                RoundedCornerShape(1.dp)
                            )
                    )
                }
            }
            
            Text(
                text = "语音消息",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        
        YhIconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "关闭"
            )
        }
    }
}

/**
 * Tab选择器
 */
@Composable
fun TabSelector(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    YhSegmentedControl(
        labels = listOf("保存的音频", "文字转语音"),
        selectedIndex = selectedTab,
        onSelectedIndexChange = onTabSelected,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    )
}

/**
 * 保存的音频Tab
 */
@Composable
fun SavedAudiosTab(
    audios: List<SavedAudioItem>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onAudioSelected: (Uri) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            YhCircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        } else if (audios.isEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("暂无保存的音频")
                Spacer(modifier = Modifier.height(8.dp))
                YhButton(onClick = onRefresh) {
                    Text("刷新")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(audios) { audio ->
                    AudioItemCard(
                        audio = audio,
                        onClick = { onAudioSelected(audio.uri) }
                    )
                }
            }
        }
    }
}

/**
 * 音频项卡片
 */
@Composable
fun AudioItemCard(
    audio: SavedAudioItem,
    onClick: () -> Unit
) {
    YhCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = audio.displayName,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatDateTime(audio.dateAdded),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "发送",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * TTS Tab
 */
@Composable
fun TTSTab(
    text: String,
    onTextChange: (String) -> Unit,
    isPlaying: Boolean,
    progress: Float,
    error: String?,
    isSynthesizing: Boolean,
    synthesisError: String?,
    availableEngines: List<TtsEngineInfo>,
    selectedEngine: String?,
    onEngineSelected: (String) -> Unit,
    speechRate: Float,
    onSpeechRateChange: (Float) -> Unit,
    pitch: Float,
    onPitchChange: (Float) -> Unit,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    availableTtsEngines: List<TtsEngineInfo>,
    selectedTtsEngine: String?,
    onEngineChange: (String) -> Unit,
    customEnginePackage: String,
    onCustomEnginePackageChange: (String) -> Unit,
    onApplyCustomEngine: () -> Unit,
    showEngineSelector: Boolean,
    onToggleEngineSelector: () -> Unit,
    onSynthesize: () -> Unit,
    onPlayPreview: () -> Unit,
    onStopPreview: () -> Unit,
    onOpenTtsSettings: () -> Unit,
    onRetryInitialization: () -> Unit
) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState) // 添加垂直滚动
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 输入框
        YhOutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp), // 固定高度，替代weight
            label = { Text("输入要转换的文字") },
            placeholder = { Text("请输入文字...") },
            maxLines = 8
        )
        
        // TTS引擎选择器
        TtsEngineSelector(
            availableEngines = availableTtsEngines,
            selectedEngine = selectedTtsEngine,
            onEngineChange = onEngineChange,
            customEnginePackage = customEnginePackage,
            onCustomEnginePackageChange = onCustomEnginePackageChange,
            onApplyCustomEngine = onApplyCustomEngine,
            showEngineSelector = showEngineSelector,
            onToggleEngineSelector = onToggleEngineSelector
        )
        
        // TTS状态指示器（成功时显示）
        if (error == null) {
            YhCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Check,
                        contentDescription = "就绪",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "TTS已就绪",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = selectedTtsEngine?.let { engine ->
                                availableTtsEngines.find { it.packageName == engine }?.label ?: "默认"
                            } ?: "系统默认",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "${availableTtsEngines.count { it.isInstalled }} 个已安装",
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
        
        // 错误提示
        if (error != null) {
            YhCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.errorContainer
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Warning,
                            contentDescription = "错误",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "TTS初始化失败",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    // 根据错误类型提供解决方案
                    when {
                        error.contains("缺少中文语言数据") || error.contains("不支持中文") -> {
                            Text(
                                text = "💡 解决方案：",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "1. 点击下方\"打开TTS设置\"按钮\n2. 选择一个支持中文的TTS引擎\n3. 下载中文语音包\n4. 返回后点击\"重试初始化\"",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        error.contains("初始化失败") || error.contains("引擎") -> {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "💡 解决方案：",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "• 尝试选择其他TTS引擎\n• 点击\"使用系统默认\"切换\n• 或重新安装当前TTS引擎",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        error.contains("未找到TTS引擎") || error.contains("未检测到") -> {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "💡 解决方案：",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "请先安装TTS应用:\n" +
                                          "• Google 文字转语音 (推荐)\n" +
                                          "• Multi TTS (离线TTS)\n" +
                                          "• 小米TTS / 华为TTS\n" +
                                          "• 科大讯飞TTS\n\n" +
                                          "安装后重新打开本窗口",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        error.contains("超时") -> {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "⏰ TTS初始化超时",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "• 检查TTS引擎是否正常运行\n• 尝试重启设备\n• 或更换其他TTS引擎",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            YhButton(
                                onClick = onRetryInitialization,
                                modifier = Modifier.weight(1f),
                                containerColor = MaterialTheme.colorScheme.error
                            ) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("重试")
                            }
                            
                            YhOutlinedButton(
                                onClick = onOpenTtsSettings,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.Settings,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("设置")
                            }
                        }
                        
                        // 快速尝试其他引擎（如果有多个可用引擎）
                        if (availableTtsEngines.size > 1) {
                            YhHorizontalDivider()
                            Text(
                                text = "快速尝试其他引擎:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                availableTtsEngines.filter { it.isInstalled }.take(5).forEach { engine ->
                                    if (engine.packageName != selectedTtsEngine) {
                                        YhFilterChip(
                                            selected = false,
                                            onClick = {
                                                onEngineChange(engine.packageName)
                                            },
                                            label = { 
                                                Text(
                                                    engine.label,
                                                    style = MaterialTheme.typography.labelSmall
                                                ) 
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        if (synthesisError != null) {
            Text(
                text = synthesisError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        
        // 进度条
        if (isPlaying && progress > 0) {
            YhLinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // TTS参数控制
        YhCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "语音参数",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // 语速控制
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "语速",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = String.format("%.1fx", speechRate),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    YhSlider(
                        value = speechRate,
                        onValueChange = onSpeechRateChange,
                        valueRange = 0.5f..2.0f,
                        steps = 14, // 0.1的间隔
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                // 音调控制
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "音调",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = String.format("%.1fx", pitch),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    YhSlider(
                        value = pitch,
                        onValueChange = onPitchChange,
                        valueRange = 0.5f..2.0f,
                        steps = 14,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                // 音量控制
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "音量",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "${(volume * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    YhSlider(
                        value = volume,
                        onValueChange = onVolumeChange,
                        valueRange = 0.0f..1.0f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                // 重置按钮
                YhTextButton(
                    onClick = {
                        onSpeechRateChange(1.0f)
                        onPitchChange(1.0f)
                        onVolumeChange(1.0f)
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("重置为默认值")
                }
            }
        }
        
        // 操作按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            YhButton(
                onClick = if (isPlaying) onStopPreview else onPlayPreview,
                enabled = text.isNotBlank() && error == null,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "停止" else "播放"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isPlaying) "停止" else "预览")
            }
            
            YhButton(
                onClick = onSynthesize,
                enabled = text.isNotBlank() && !isSynthesizing && error == null,
                modifier = Modifier.weight(1f)
            ) {
                if (isSynthesizing) {
                    YhCircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "合成并发送"
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("合成并发送")
            }
        }
        
        // TTS设置
        YhTextButton(
            onClick = onOpenTtsSettings,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("打开TTS设置")
        }
    }
}

/**
 * 加载保存的音频文件
 */
suspend fun loadSavedAudios(context: Context): List<SavedAudioItem> {
    return withContext(Dispatchers.IO) {
        val audioList = mutableListOf<SavedAudioItem>()
        
        try {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.DATA
            )
            
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )
            
            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val durationColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dateColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val dataColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                
                while (it.moveToNext()) {
                    val id = it.getLong(idColumn)
                    val name = it.getString(nameColumn)
                    val duration = it.getLong(durationColumn)
                    val size = it.getLong(sizeColumn)
                    val dateAdded = it.getLong(dateColumn)
                    val path = it.getString(dataColumn)
                    
                    val uri = Uri.withAppendedPath(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )
                    
                    audioList.add(
                        SavedAudioItem(
                            uri = uri,
                            displayName = name,
                            duration = duration,
                            size = size,
                            dateAdded = dateAdded,
                            path = path
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("FloatingVoiceWindow", "加载音频文件失败", e)
        }
        
        audioList
    }
}

/**
 * TTS引擎选择器组件
 */
@Composable
fun TtsEngineSelector(
    availableEngines: List<TtsEngineInfo>,
    selectedEngine: String?,
    onEngineChange: (String) -> Unit,
    customEnginePackage: String,
    onCustomEnginePackageChange: (String) -> Unit,
    onApplyCustomEngine: () -> Unit,
    showEngineSelector: Boolean,
    onToggleEngineSelector: () -> Unit
) {
    YhCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 标题和展开/收起按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleEngineSelector() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "TTS引擎",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "TTS引擎设置",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
                Icon(
                    imageVector = if (showEngineSelector) 
                        Icons.Default.Close 
                    else 
                        Icons.Default.Add,
                    contentDescription = if (showEngineSelector) "收起" else "展开",
                    modifier = Modifier.size(20.dp)
                )
            }
            
            // 当前使用的引擎
            if (!showEngineSelector) {
                val engineLabel = if (selectedEngine != null) {
                    availableEngines.find { it.packageName == selectedEngine }?.label ?: selectedEngine
                } else {
                    "系统默认"
                }
                Text(
                    text = "当前: $engineLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            
            // 引擎选择区域（展开时显示）
            if (showEngineSelector) {
                YhHorizontalDivider()
                
                // 自动识别的引擎列表
                Text(
                    text = "可用的TTS引擎 (${availableEngines.size + 1}个)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 添加"系统默认"选项
                    YhSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                onEngineChange("") // 空字符串表示系统默认
                            },
                        color = if (selectedEngine == null) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.Transparent
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "系统默认",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "使用系统配置的默认TTS引擎",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            if (selectedEngine == null) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "已选中",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    
                    if (availableEngines.isNotEmpty()) {
                        availableEngines.forEach { engine ->
                            YhSurface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onEngineChange(engine.packageName) },
                                color = if (selectedEngine == engine.packageName) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else if (!engine.isInstalled) {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                } else {
                                    Color.Transparent
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = engine.label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = if (engine.isInstalled) {
                                                MaterialTheme.colorScheme.onSurface
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                            }
                                        )
                                        Text(
                                            text = engine.packageName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    
                                    if (selectedEngine == engine.packageName) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "已选中",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else if (!engine.isInstalled) {
                                        Text(
                                            text = "未安装",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                YhHorizontalDivider()
                
                // 自定义引擎包名输入
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "自定义引擎包名",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    YhOutlinedTextField(
                        value = customEnginePackage,
                        onValueChange = onCustomEnginePackageChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("引擎包名") },
                        placeholder = { Text("例如: com.google.android.tts") },
                        singleLine = true,
                        trailingIcon = {
                            if (customEnginePackage.isNotBlank()) {
                                YhIconButton(onClick = { onCustomEnginePackageChange("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "清除"
                                    )
                                }
                            }
                        }
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        YhButton(
                            onClick = onApplyCustomEngine,
                            enabled = customEnginePackage.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("应用自定义引擎")
                        }
                        
                        YhOutlinedButton(
                            onClick = {
                                onCustomEnginePackageChange("")
                                onEngineChange("") // 空字符串表示系统默认
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("使用系统默认")
                        }
                    }
                    
                    // 常用引擎快捷按钮
                    Text(
                        text = "常用引擎",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "Google TTS" to "com.google.android.tts",
                            "小米TTS" to "com.xiaomi.mibrain.speech",
                            "华为TTS" to "com.huawei.tts",
                            "Multi TTS" to "org.nobody.multitts"
                        ).forEach { (label, packageName) ->
                            val engineInfo = availableEngines.find { it.packageName == packageName }
                            YhFilterChip(
                                selected = selectedEngine == packageName,
                                onClick = {
                                    onCustomEnginePackageChange(packageName)
                                    onEngineChange(packageName)
                                },
                                label = { 
                                    Text(
                                        label + if (engineInfo?.isInstalled == true) " ✓" else "",
                                        style = MaterialTheme.typography.labelSmall
                                    ) 
                                },
                                enabled = engineInfo?.isInstalled == true
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 获取默认TTS引擎
 */
fun getDefaultTtsEngine(context: Context): String? {
    return try {
        Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.TTS_DEFAULT_SYNTH
        )
    } catch (e: Exception) {
        Log.e("FloatingVoiceWindow", "获取默认TTS引擎失败", e)
        null
    }
}

/**
 * 检测所有TTS引擎（包括已安装和已知但未安装的）
 * 参考指南：使用系统API查询 + 已知引擎列表
 */
fun detectAllTtsEngines(context: Context): List<TtsEngineInfo> {
    val engines = mutableListOf<TtsEngineInfo>()
    
    try {
        // Log.d("FloatingVoiceWindow", "🔍 开始检测TTS引擎...")
        // Log.d("FloatingVoiceWindow", "📱 SDK版本: ${Build.VERSION.SDK_INT}")
        
        // 1. 查询系统已安装的TTS服务
        val ttsServiceIntent = Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
        val resolveInfoList = context.packageManager.queryIntentServices(ttsServiceIntent, 0)
        
        // Log.d("FloatingVoiceWindow", "📋 系统查询到 ${resolveInfoList.size} 个TTS服务")
        
        if (resolveInfoList.isEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Log.e("FloatingVoiceWindow", "⚠️ Android 11+ 未检测到TTS引擎！请检查AndroidManifest.xml的<queries>配置")
        }
        
        val installedPackages = mutableSetOf<String>()
        
        for (resolveInfo in resolveInfoList) {
            val serviceInfo = resolveInfo.serviceInfo
            val packageName = serviceInfo.packageName
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            val appName = context.packageManager.getApplicationLabel(appInfo).toString()
            
            engines.add(TtsEngineInfo(
                packageName = packageName,
                label = appName,
                isInstalled = true
            ))
            installedPackages.add(packageName)
            
            // Log.d("FloatingVoiceWindow", "  ✅ ${appName} ($packageName)")
        }
        
        // 2. 添加已知但可能未被系统API检测到的引擎
        val knownEngines = listOf(
            "com.google.android.tts" to "Google 文字转语音",
            "com.xiaomi.mibrain.speech" to "小米TTS",
            "com.huawei.tts" to "华为TTS",
            "com.samsung.SMT" to "三星TTS",
            "org.nobody.multitts" to "Multi TTS",
            "cn.sherpa.onnx" to "Sherpa-onnx TTS",
            "com.iflytek.tts" to "科大讯飞TTS"
        )
        
        for ((packageName, label) in knownEngines) {
            if (!installedPackages.contains(packageName)) {
                // 检查是否真的安装了
                val isInstalled = try {
                    context.packageManager.getPackageInfo(packageName, 0)
                    true
                } catch (e: PackageManager.NameNotFoundException) {
                    false
                }
                
                engines.add(TtsEngineInfo(
                    packageName = packageName,
                    label = label,
                    isInstalled = isInstalled
                ))
                
                if (isInstalled) {
                    // Log.d("FloatingVoiceWindow", "  ✅ 已知引擎: $label ($packageName)")
                }
            }
        }
        
        // Log.d("FloatingVoiceWindow", "✅ 总共检测到 ${engines.count { it.isInstalled }} 个已安装引擎")
        
    } catch (e: Exception) {
        Log.e("FloatingVoiceWindow", "❌ 检测TTS引擎失败", e)
    }
    
    return engines
}

/**
 * 寻找最佳TTS引擎
 * 策略：Google TTS → 系统默认 → 其他已安装引擎
 */
fun findBestTtsEngine(engines: List<TtsEngineInfo>): TtsEngineInfo? {
    // Log.d("FloatingVoiceWindow", "🔍 寻找最佳TTS引擎...")
    
    // 1. 优先选择Google TTS
    engines.find { it.packageName == "com.google.android.tts" && it.isInstalled }?.let {
        // Log.d("FloatingVoiceWindow", "🎯 找到Google TTS，优先使用")
        return it
    }
    
    // 2. 选择第一个已安装的引擎
    engines.find { it.isInstalled }?.let {
        // Log.d("FloatingVoiceWindow", "🎯 使用第一个可用引擎: ${it.label}")
        return it
    }
    
    Log.w("FloatingVoiceWindow", "⚠️ 未找到可用引擎")
    return null
}

/**
 * 配置音频属性（关键！确保正确的声音输出）
 * 参考指南第4.4节
 */
fun configureAudioAttributes(tts: TextToSpeech) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            tts.setAudioAttributes(audioAttributes)
            // Log.d("FloatingVoiceWindow", "🔊 音频属性配置: USAGE_MEDIA + CONTENT_TYPE_SPEECH")
        } else {
            // Android 5.0以下使用流类型（已废弃但仍然可用）
            try {
                // 使用反射调用已废弃的setStreamType方法
                val method = TextToSpeech::class.java.getMethod("setStreamType", Int::class.javaPrimitiveType)
                method.invoke(tts, AudioManager.STREAM_MUSIC)
                // Log.d("FloatingVoiceWindow", "🔊 音频流类型: STREAM_MUSIC (反射调用)")
            } catch (e: Exception) {
                Log.w("FloatingVoiceWindow", "⚠️ 设置音频流类型失败", e)
            }
        }
    } catch (e: Exception) {
        Log.e("FloatingVoiceWindow", "❌ 配置音频属性失败", e)
    }
}

/**
 * 记录TTS引擎详细信息（用于调试）
 */
fun logEngineDetails(tts: TextToSpeech) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val voice = tts.voice
            // Log.d("FloatingVoiceWindow", "🎤 当前语音: ${voice?.name ?: "未知"}")
            // Log.d("FloatingVoiceWindow", "🌍 语言: ${voice?.locale ?: "未知"}")
            
            val voices = tts.voices
            // Log.d("FloatingVoiceWindow", "📝 可用语音数: ${voices?.size ?: 0}")
            voices?.take(3)?.forEachIndexed { index, v ->
                // Log.d("FloatingVoiceWindow", "  [$index] ${v.name} (${v.locale})")
            }
        } else {
            // Log.d("FloatingVoiceWindow", "📱 API < 21，无法获取详细语音信息")
        }
    } catch (e: Exception) {
        Log.e("FloatingVoiceWindow", "❌ 获取引擎详情失败", e)
    }
}

/**
 * 获取状态码名称（用于日志）
 */
fun getStatusName(status: Int): String {
    return when (status) {
        TextToSpeech.SUCCESS -> "SUCCESS(0)"
        TextToSpeech.ERROR -> "ERROR(-1)"
        else -> "UNKNOWN($status)"
    }
}

/**
 * 检查TTS引擎是否支持指定语言
 */
fun checkTtsLanguageSupport(tts: TextToSpeech, locale: Locale): Boolean {
    return try {
        val result = tts.isLanguageAvailable(locale)
        when (result) {
            TextToSpeech.LANG_AVAILABLE,
            TextToSpeech.LANG_COUNTRY_AVAILABLE,
            TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> {
                // Log.d("FloatingVoiceWindow", "✅ 支持 ${locale.displayName}")
                true
            }
            TextToSpeech.LANG_MISSING_DATA -> {
                Log.w("FloatingVoiceWindow", "⚠️ 缺少 ${locale.displayName} 语言数据")
                false
            }
            TextToSpeech.LANG_NOT_SUPPORTED -> {
                Log.w("FloatingVoiceWindow", "❌ 不支持 ${locale.displayName}")
                false
            }
            else -> {
                Log.w("FloatingVoiceWindow", "⚠️ ${locale.displayName} 语言检查返回未知结果: $result")
                false
            }
        }
    } catch (e: Exception) {
        Log.e("FloatingVoiceWindow", "❌ 检查语言支持失败", e)
        false
    }
}

/**
 * 合成TTS音频
 */
suspend fun synthesizeTTS(
    context: Context,
    tts: TextToSpeech?,
    text: String,
    speechRate: Float,
    pitch: Float,
    volume: Float,
    onProgress: (Float) -> Unit,
    onComplete: (Uri?) -> Unit
) = withContext(Dispatchers.IO) {
    if (tts == null) {
        withContext(Dispatchers.Main) {
            onComplete(null)
        }
        return@withContext
    }
    
    try {
        // 设置TTS参数
        tts.setSpeechRate(speechRate)
        tts.setPitch(pitch)
        
        // 创建音频文件
        val audioDir = File(context.getExternalFilesDir(null), "TTS")
        if (!audioDir.exists()) {
            audioDir.mkdirs()
        }
        
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val audioFile = File(audioDir, "tts_${timestamp}.wav")
        
        // Log.d("FloatingVoiceWindow", "开始合成音频到: ${audioFile.absolutePath}")
        
        // 创建合成参数
        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "synthesis_${timestamp}")
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
        
        // 设置合成监听器
        var synthesisComplete = false
        var synthesisSuccess = false
        
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                // Log.d("FloatingVoiceWindow", "TTS合成开始")
                onProgress(0.0f)
            }
            
            override fun onDone(utteranceId: String?) {
                // Log.d("FloatingVoiceWindow", "TTS合成完成")
                synthesisComplete = true
                synthesisSuccess = true
                onProgress(1.0f)
            }
            
            @Suppress("OVERRIDE_DEPRECATION")
            override fun onError(utteranceId: String?) {
                Log.e("FloatingVoiceWindow", "TTS合成失败")
                synthesisComplete = true
                synthesisSuccess = false
            }
            
            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.e("FloatingVoiceWindow", "TTS合成失败，错误码: $errorCode")
                synthesisComplete = true
                synthesisSuccess = false
            }
            
            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                super.onRangeStart(utteranceId, start, end, frame)
                // 更新进度
                val progress = start.toFloat() / end.toFloat()
                onProgress(progress)
            }
        })
        
        // 开始合成
        // Log.d("FloatingVoiceWindow", "📝 开始调用synthesizeToFile API")
        @Suppress("DEPRECATION")
        val result = tts.synthesizeToFile(text, params, audioFile, params.getString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID))
        
        when (result) {
            TextToSpeech.SUCCESS -> {
                // Log.d("FloatingVoiceWindow", "✅ synthesizeToFile调用成功，等待合成完成...")
            }
            TextToSpeech.ERROR -> {
                Log.e("FloatingVoiceWindow", "❌ synthesizeToFile调用失败: ERROR")
                withContext(Dispatchers.Main) {
                    onComplete(null)
                }
                return@withContext
            }
            else -> {
                Log.e("FloatingVoiceWindow", "❌ synthesizeToFile返回未知结果: $result")
                withContext(Dispatchers.Main) {
                    onComplete(null)
                }
                return@withContext
            }
        }
        
        // 等待合成完成（最多30秒）
        var waitTime = 0
        // Log.d("FloatingVoiceWindow", "⏳ 等待TTS合成完成...")
        while (!synthesisComplete && waitTime < 30000) {
            kotlinx.coroutines.delay(100)
            waitTime += 100
            
            // 每2秒记录一次等待状态
            if (waitTime % 2000 == 0) {
                // Log.d("FloatingVoiceWindow", "⏳ 已等待 ${waitTime/1000}秒...")
            }
        }
        
        // 检查合成结果
        // Log.d("FloatingVoiceWindow", "📊 合成状态: complete=$synthesisComplete, success=$synthesisSuccess")
        // Log.d("FloatingVoiceWindow", "📊 文件状态: exists=${audioFile.exists()}, size=${if (audioFile.exists()) audioFile.length() else 0}")
        
        withContext(Dispatchers.Main) {
            when {
                !synthesisComplete -> {
                    Log.e("FloatingVoiceWindow", "❌ TTS合成超时（30秒）")
                    onComplete(null)
                }
                !synthesisSuccess -> {
                    Log.e("FloatingVoiceWindow", "❌ TTS合成失败")
                    onComplete(null)
                }
                !audioFile.exists() -> {
                    Log.e("FloatingVoiceWindow", "❌ 音频文件未生成")
                    onComplete(null)
                }
                audioFile.length() == 0L -> {
                    Log.e("FloatingVoiceWindow", "❌ 音频文件为空")
                    onComplete(null)
                }
                else -> {
                    val uri = Uri.fromFile(audioFile)
                    // Log.d("FloatingVoiceWindow", "🎉 音频合成成功: ${audioFile.absolutePath}")
                    // Log.d("FloatingVoiceWindow", "📦 文件大小: ${audioFile.length()} 字节 (${audioFile.length() / 1024}KB)")
                    onComplete(uri)
                }
            }
        }
        
    } catch (e: Exception) {
        Log.e("FloatingVoiceWindow", "TTS合成异常", e)
        withContext(Dispatchers.Main) {
            onComplete(null)
        }
    }
}

/**
 * 播放TTS
 */
fun playTTS(
    tts: TextToSpeech?, 
    text: String, 
    volume: Float = 1.0f,
    onComplete: () -> Unit = {}
) {
    if (tts == null || text.isBlank()) return
    
    val utteranceId = UUID.randomUUID().toString()
    
    // 设置播放监听器
    tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            // Log.d("FloatingVoiceWindow", "TTS播放开始")
        }
        
        override fun onDone(utteranceId: String?) {
            // Log.d("FloatingVoiceWindow", "TTS播放完成")
            onComplete()
        }
        
        @Suppress("OVERRIDE_DEPRECATION")
        override fun onError(utteranceId: String?) {
            Log.e("FloatingVoiceWindow", "TTS播放出错")
            onComplete()
        }
        
        override fun onError(utteranceId: String?, errorCode: Int) {
            Log.e("FloatingVoiceWindow", "TTS播放出错，错误码: $errorCode")
            onComplete()
        }
    })
    
    // 创建播放参数
    val params = Bundle()
    params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
    
    tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
}

/**
 * 停止TTS播放
 */
fun stopTTS(tts: TextToSpeech?) {
    tts?.stop()
}

/**
 * 格式化日期时间
 */
fun formatDateTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp * 1000))
}
