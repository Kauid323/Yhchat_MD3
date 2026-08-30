package com.yhchat.canary.ui.components

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.UUID

/**
 * TTS管理器
 */
class TtsManager(private val context: Context) {
    
    private var tts: TextToSpeech? = null
    private var mediaPlayer: MediaPlayer? = null
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying
    
    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    
    // 常量
    private companion object {
        const val TAG = "TtsManager"
        const val ERROR_TTS_NOT_INITIALIZED = "TTS未初始化"
        const val ERROR_EMPTY_TEXT = "文本不能为空"
        const val ERROR_INIT_FAILED = "TTS初始化失败"
        const val ERROR_PLAY_FAILED = "播放出错"
        const val ERROR_SYNTHESIS_FAILED = "合成出错"
        const val ERROR_FILE_NOT_GENERATED = "音频文件未生成"
        const val ERROR_PLAYBACK_FAILED = "播放失败"
    }
    
    /**
     * 初始化TTS引擎
     */
    fun initializeTts(
        engineName: String? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        shutdown()
        
        tts = if (engineName != null) {
            TextToSpeech(context, { status ->
                handleInitResult(status, onSuccess, onError)
            }, engineName)
        } else {
            TextToSpeech(context) { status ->
                handleInitResult(status, onSuccess, onError)
            }
        }
    }
    
    private fun handleInitResult(
        status: Int,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (status == TextToSpeech.SUCCESS) {
            _error.value = null
            onSuccess()
        } else {
            _error.value = ERROR_INIT_FAILED
            onError(ERROR_INIT_FAILED)
        }
    }
    
    /**
     * 播放文本
     */
    fun speak(text: String) {
        if (tts == null) {
            _error.value = ERROR_TTS_NOT_INITIALIZED
            return
        }
        
        if (text.isBlank()) {
            _error.value = ERROR_EMPTY_TEXT
            return
        }
        
        val utteranceId = UUID.randomUUID().toString()
        
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isPlaying.value = true
                _progress.value = 0f
            }
            
            override fun onDone(utteranceId: String?) {
                _isPlaying.value = false
                _progress.value = 1f
            }
            
            @Suppress("OVERRIDE_DEPRECATION")
            override fun onError(utteranceId: String?) {
                _isPlaying.value = false
                _error.value = ERROR_PLAY_FAILED
            }
            
            override fun onError(utteranceId: String?, errorCode: Int) {
                _isPlaying.value = false
                _error.value = "$ERROR_PLAY_FAILED: $errorCode"
            }
            
            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                super.onRangeStart(utteranceId, start, end, frame)
                // 更新进度
                val totalLength = end
                if (totalLength > 0) {
                    _progress.value = start.toFloat() / totalLength
                }
            }
        })
        
        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }
    
    /**
     * 停止播放
     */
    fun stop() {
        tts?.stop()
        _isPlaying.value = false
        _progress.value = 0f
    }
    
    /**
     * 合成音频文件
     */
    fun synthesizeToFile(
        text: String,
        outputFile: File,
        onSuccess: (Uri) -> Unit,
        onError: (String) -> Unit
    ) {
        if (tts == null) {
            onError(ERROR_TTS_NOT_INITIALIZED)
            return
        }
        
        if (text.isBlank()) {
            onError(ERROR_EMPTY_TEXT)
            return
        }
        
        try {
            val utteranceId = UUID.randomUUID().toString()
            
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    // Log.d(TAG, "开始合成音频")
                }
                
                override fun onDone(utteranceId: String?) {
                    // Log.d(TAG, "音频合成完成")
                    if (outputFile.exists()) {
                        onSuccess(Uri.fromFile(outputFile))
                    } else {
                        onError(ERROR_FILE_NOT_GENERATED)
                    }
                }
                
                @Suppress("OVERRIDE_DEPRECATION")
                override fun onError(utteranceId: String?) {
                    onError(ERROR_SYNTHESIS_FAILED)
                }
                
                override fun onError(utteranceId: String?, errorCode: Int) {
                    onError("$ERROR_SYNTHESIS_FAILED: $errorCode")
                }
            })
            
            val params = Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            
            @Suppress("DEPRECATION")
            val result = tts?.synthesizeToFile(text, params, outputFile, utteranceId)
            
            if (result != TextToSpeech.SUCCESS) {
                onError(ERROR_SYNTHESIS_FAILED)
            }
        } catch (e: Exception) {
            Log.e(TAG, "合成音频失败", e)
            onError("$ERROR_SYNTHESIS_FAILED: ${e.message}")
        }
    }
    
    /**
     * 播放音频文件
     */
    fun playAudioFile(uri: Uri, onComplete: () -> Unit) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, uri)
                prepare()
                setOnCompletionListener {
                    _isPlaying.value = false
                    onComplete()
                }
                setOnErrorListener { _, what, extra ->
                    _error.value = "播放错误: what=$what, extra=$extra"
                    _isPlaying.value = false
                    true
                }
                start()
                _isPlaying.value = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "播放音频失败", e)
            _error.value = "$ERROR_PLAYBACK_FAILED: ${e.message}"
        }
    }
    
    /**
     * 停止音频播放
     */
    fun stopAudioPlayback() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        _isPlaying.value = false
    }
    
    /**
     * 关闭TTS引擎
     */
    fun shutdown() {
        stop()
        stopAudioPlayback()
        tts?.shutdown()
        tts = null
    }
}
