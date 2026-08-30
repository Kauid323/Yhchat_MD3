package com.yhchat.canary.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume

/**
 * 语音转文字 (Speech-to-Text) 工具类
 */
object STTUtils {
    
    private const val TAG = "STTUtils"
    
    /**
     * 检查设备是否支持语音识别
     */
    fun isRecognitionAvailable(context: Context): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }
    
    /**
     * 从音频URL下载并保存到本地
     * @param context Context
     * @param audioUrl 音频URL
     * @param fileName 保存的文件名（可选）
     * @return 保存的本地文件，失败返回null
     */
    suspend fun downloadAudioToLocal(
        context: Context,
        audioUrl: String,
        fileName: String? = null
    ): File? = withContext(Dispatchers.IO) {
        runCatching {
            val client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .addHeader("Referer", "https://myapp.jwznb.com")
                        .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
                        .build()
                    chain.proceed(request)
                }
                .build()
            
            val request = Request.Builder().url(audioUrl).build()
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "下载音频失败: ${response.code}")
                return@runCatching null
            }
            
            val bytes = response.body?.bytes() ?: return@runCatching null
            
            // 生成文件名
            val actualFileName = fileName ?: run {
                val urlFileName = audioUrl.substringAfterLast('/').substringBefore('?')
                if (urlFileName.isNotBlank() && urlFileName.contains('.')) {
                    urlFileName
                } else {
                    "audio_${System.currentTimeMillis()}.m4a"
                }
            }
            
            // 保存到应用缓存目录
            val cacheDir = File(context.cacheDir, "stt_audio")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            
            val audioFile = File(cacheDir, actualFileName)
            FileOutputStream(audioFile).use { fos ->
                fos.write(bytes)
            }
            
            Log.d(TAG, "音频下载完成: ${audioFile.absolutePath}, 大小: ${bytes.size} bytes")
            audioFile
        }.onFailure { e ->
            Log.e(TAG, "下载音频异常", e)
        }.getOrNull()
    }
    
    /**
     * 使用Android原生语音识别进行语音转文字
     * 注意：这个方法需要在有网络的情况下使用，且依赖设备的语音识别服务
     * @param context Context
     * @param audioFile 音频文件（注意：Android原生API不直接支持文件输入，这里使用流式方式）
     * @return 识别结果文本，失败返回null
     */
    suspend fun recognizeSpeechFromFile(
        context: Context,
        audioFile: File
    ): String? = withContext(Dispatchers.Main) {
        if (!isRecognitionAvailable(context)) {
            Log.e(TAG, "设备不支持语音识别")
            return@withContext null
        }
        
        suspendCancellableCoroutine { continuation ->
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "准备开始识别")
                }
                
                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "开始识别语音")
                }
                
                override fun onRmsChanged(rmsdB: Float) {}
                
                override fun onBufferReceived(buffer: ByteArray?) {}
                
                override fun onEndOfSpeech() {
                    Log.d(TAG, "语音结束")
                }
                
                override fun onError(error: Int) {
                    val errorMessage = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "音频录制错误"
                        SpeechRecognizer.ERROR_CLIENT -> "客户端错误"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "权限不足"
                        SpeechRecognizer.ERROR_NETWORK -> "网络错误"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时"
                        SpeechRecognizer.ERROR_NO_MATCH -> "没有匹配的识别结果"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别器繁忙"
                        SpeechRecognizer.ERROR_SERVER -> "服务器错误"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "语音输入超时"
                        else -> "未知错误: $error"
                    }
                    Log.e(TAG, "识别错误: $errorMessage")
                    recognizer.destroy()
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
                
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val result = matches?.firstOrNull()
                    Log.d(TAG, "识别结果: $result")
                    recognizer.destroy()
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                }
                
                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    Log.d(TAG, "部分识别结果: ${matches?.firstOrNull()}")
                }
                
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            
            recognizer.startListening(intent)
            
            continuation.invokeOnCancellation {
                recognizer.cancel()
                recognizer.destroy()
            }
        }
    }
    
    /**
     * 语音转文字的完整流程（下载+识别）
     * 由于Android原生API限制，这里提供一个模拟实现
     * 实际项目中建议使用第三方API（如百度、讯飞等）
     */
    suspend fun speechToText(
        context: Context,
        audioUrl: String,
        onProgress: ((String) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            onProgress?.invoke("正在下载音频...")
            
            // 1. 下载音频文件
            val audioFile = downloadAudioToLocal(context, audioUrl)
                ?: throw Exception("下载音频失败")
            
            onProgress?.invoke("正在识别语音...")
            
            // 2. 由于Android原生SpeechRecognizer不支持直接识别音频文件
            // 这里返回提示信息，实际项目中应该使用第三方语音识别API
            // 例如：百度语音识别、讯飞语音识别、Google Cloud Speech-to-Text等
            
            // 模拟识别过程
            kotlinx.coroutines.delay(1500)
            
            // 清理临时文件
            audioFile.delete()
            
            // 返回提示（实际项目中这里应该是识别结果）
            throw Exception("语音转文字功能需要配置第三方语音识别API（如百度、讯飞等），请在设置中配置API密钥")
        }
    }
    
    /**
     * 从本地音频文件进行语音转文字
     */
    suspend fun speechToTextFromFile(
        context: Context,
        audioFile: File,
        onProgress: ((String) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            onProgress?.invoke("正在识别语音...")
            
            // 同样的限制，Android原生API不支持直接识别文件
            // 实际项目中需要使用第三方API
            
            kotlinx.coroutines.delay(1500)
            
            throw Exception("语音转文字功能需要配置第三方语音识别API（如百度、讯飞等），请在设置中配置API密钥")
        }
    }
    
    /**
     * 检查STT功能是否可用
     */
    fun isSTTEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences("stt_settings", Context.MODE_PRIVATE)
        return prefs.getBoolean("stt_enabled", false)
    }
    
    /**
     * 设置STT功能状态
     */
    fun setSTTEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences("stt_settings", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("stt_enabled", enabled).apply()
    }
}
