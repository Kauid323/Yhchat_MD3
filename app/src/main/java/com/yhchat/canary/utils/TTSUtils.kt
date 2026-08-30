package com.yhchat.canary.utils

import android.content.Context
import android.os.Build
import android.os.Environment
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume

/**
 * 文字转语音 (TTS) 工具类
 */
object TTSUtils {
    
    private const val TAG = "TTSUtils"
    
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    
    /**
     * 初始化TTS引擎
     */
    suspend fun initialize(context: Context): Boolean = suspendCancellableCoroutine { continuation ->
        if (isInitialized && tts != null) {
            continuation.resume(true)
            return@suspendCancellableCoroutine
        }
        
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // 设置语言为中文
                val result = tts?.setLanguage(Locale.CHINESE)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // 尝试简体中文
                    val zhCNResult = tts?.setLanguage(Locale.SIMPLIFIED_CHINESE)
                    if (zhCNResult == TextToSpeech.LANG_MISSING_DATA || zhCNResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.w(TAG, "中文语言包不可用，使用默认语言")
                        // 使用默认语言
                        tts?.setLanguage(Locale.getDefault())
                    }
                }
                
                // 设置语速和音调
                tts?.setSpeechRate(1.0f)
                tts?.setPitch(1.0f)
                
                isInitialized = true
                Log.d(TAG, "TTS初始化成功")
                continuation.resume(true)
            } else {
                Log.e(TAG, "TTS初始化失败: $status")
                isInitialized = false
                continuation.resume(false)
            }
        }
        
        continuation.invokeOnCancellation {
            // 如果协程被取消，不关闭TTS，保留供后续使用
            Log.d(TAG, "TTS初始化协程被取消")
        }
    }
    
    /**
     * 将文字转换为语音文件
     * @param context Context
     * @param text 要转换的文字
     * @param outputDir 输出目录，默认为应用音频目录
     * @return 生成的音频文件，失败返回null
     */
    suspend fun textToSpeechFile(
        context: Context,
        text: String,
        outputDir: File? = null
    ): File? = withContext(Dispatchers.IO) {
        if (text.isBlank()) {
            Log.w(TAG, "文字内容为空")
            return@withContext null
        }
        
        // 确保TTS已初始化
        if (!isInitialized) {
            if (!initialize(context)) {
                Log.e(TAG, "TTS初始化失败，无法转换")
                return@withContext null
            }
        }
        
        val ttsEngine = tts ?: run {
            Log.e(TAG, "TTS引擎为空")
            return@withContext null
        }
        
        // 创建输出目录
        val audioDir = outputDir ?: File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "tts")
        if (!audioDir.exists()) {
            audioDir.mkdirs()
        }
        
        // 生成输出文件
        val outputFile = File(audioDir, "tts_${System.currentTimeMillis()}.wav")
        
        // 使用协程等待TTS完成
        val result = suspendCancellableCoroutine<Boolean> { continuation ->
            val utteranceId = "tts_${System.currentTimeMillis()}"
            
            ttsEngine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d(TAG, "TTS开始合成: $utteranceId")
                }
                
                override fun onDone(utteranceId: String?) {
                    Log.d(TAG, "TTS合成完成: $utteranceId")
                    if (continuation.isActive) {
                        continuation.resume(true)
                    }
                }
                
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "TTS合成错误: $utteranceId")
                    if (continuation.isActive) {
                        continuation.resume(false)
                    }
                }
                
                override fun onError(utteranceId: String?, errorCode: Int) {
                    Log.e(TAG, "TTS合成错误: $utteranceId, errorCode: $errorCode")
                    if (continuation.isActive) {
                        continuation.resume(false)
                    }
                }
            })
            
            // 合成到文件
            val synthesizeResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ttsEngine.synthesizeToFile(text, null, outputFile, utteranceId)
            } else {
                @Suppress("DEPRECATION")
                val params = HashMap<String, String>()
                params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = utteranceId
                ttsEngine.synthesizeToFile(text, params, outputFile.absolutePath)
            }
            
            if (synthesizeResult != TextToSpeech.SUCCESS) {
                Log.e(TAG, "TTS合成请求失败: $synthesizeResult")
                if (continuation.isActive) {
                    continuation.resume(false)
                }
            }
            
            continuation.invokeOnCancellation {
                ttsEngine.stop()
                Log.d(TAG, "TTS合成被取消")
            }
        }
        
        if (result && outputFile.exists() && outputFile.length() > 0) {
            Log.d(TAG, "TTS文件生成成功: ${outputFile.absolutePath}, 大小: ${outputFile.length()} bytes")
            outputFile
        } else {
            outputFile.delete()
            Log.e(TAG, "TTS文件生成失败")
            null
        }
    }
    
    /**
     * 将文字转换为语音文件并重命名为MD5格式
     * @param context Context
     * @param text 要转换的文字
     * @return 生成的音频文件（已重命名为MD5），失败返回null
     */
    suspend fun textToSpeechFileWithMD5(
        context: Context,
        text: String
    ): File? = withContext(Dispatchers.IO) {
        val ttsFile = textToSpeechFile(context, text) ?: return@withContext null
        
        // 使用AudioUtils重命名为MD5
        AudioUtils.renameFileWithMD5(ttsFile)
    }
    
    /**
     * 检查TTS是否可用
     */
    fun isAvailable(): Boolean = isInitialized && tts != null
    
    /**
     * 获取可用的TTS语言列表
     */
    fun getAvailableLanguages(): Set<Locale>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts?.availableLanguages
        } else {
            null
        }
    }
    
    /**
     * 设置语速
     * @param rate 语速，1.0为正常速度
     */
    fun setSpeechRate(rate: Float) {
        tts?.setSpeechRate(rate.coerceIn(0.1f, 4.0f))
    }
    
    /**
     * 设置音调
     * @param pitch 音调，1.0为正常音调
     */
    fun setPitch(pitch: Float) {
        tts?.setPitch(pitch.coerceIn(0.1f, 2.0f))
    }
    
    /**
     * 释放TTS资源
     */
    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        Log.d(TAG, "TTS资源已释放")
    }
    
    /**
     * 检查TTS开关是否开启
     */
    fun isTTSEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences("tts_settings", Context.MODE_PRIVATE)
        return prefs.getBoolean("tts_enabled", false)
    }
    
    /**
     * 设置TTS开关状态
     */
    fun setTTSEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences("tts_settings", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("tts_enabled", enabled).apply()
    }
    
    /**
     * 获取TTS语速设置
     */
    fun getTTSSpeechRate(context: Context): Float {
        val prefs = context.getSharedPreferences("tts_settings", Context.MODE_PRIVATE)
        return prefs.getFloat("tts_speech_rate", 1.0f)
    }
    
    /**
     * 设置TTS语速
     */
    fun setTTSSpeechRate(context: Context, rate: Float) {
        val prefs = context.getSharedPreferences("tts_settings", Context.MODE_PRIVATE)
        prefs.edit().putFloat("tts_speech_rate", rate).apply()
        setSpeechRate(rate)
    }
    
    /**
     * 获取TTS音调设置
     */
    fun getTTSPitch(context: Context): Float {
        val prefs = context.getSharedPreferences("tts_settings", Context.MODE_PRIVATE)
        return prefs.getFloat("tts_pitch", 1.0f)
    }
    
    /**
     * 设置TTS音调
     */
    fun setTTSPitch(context: Context, pitch: Float) {
        val prefs = context.getSharedPreferences("tts_settings", Context.MODE_PRIVATE)
        prefs.edit().putFloat("tts_pitch", pitch).apply()
        setPitch(pitch)
    }
}
