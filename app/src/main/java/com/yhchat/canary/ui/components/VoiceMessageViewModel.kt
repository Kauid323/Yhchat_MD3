package com.yhchat.canary.ui.components

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhchat.canary.data.api.ApiService
import com.yhchat.canary.data.repository.TokenRepository
import com.yhchat.canary.utils.AudioUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.*

/**
 * 语音消息 ViewModel
 */
class VoiceMessageViewModel(
    private val apiService: ApiService,
    private val tokenRepository: TokenRepository
) : ViewModel() {

    private val tag = "VoiceMessageVM"
    
    private val _voiceState = MutableStateFlow(VoiceState())
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()
    
    data class VoiceState(
        val isRecording: Boolean = false,
        val isUploading: Boolean = false,
        val recordingTime: Long = 0L,
        val error: String? = null,
        val isProcessing: Boolean = false
    )
    
    private var recordingFile: File? = null
    
    /**
     * 开始录音
     */
    fun startRecording(context: Context) {
        viewModelScope.launch {
            try {
                Log.d(tag, "startRecording")
                _voiceState.value = _voiceState.value.copy(isRecording = true, recordingTime = System.currentTimeMillis())
                recordingFile = AudioUtils.startRecording(context)
                if (recordingFile == null) {
                    _voiceState.value = _voiceState.value.copy(
                        isRecording = false,
                        error = "录音失败，请检查麦克风权限"
                    )
                }
            } catch (e: Exception) {
                _voiceState.value = _voiceState.value.copy(
                    isRecording = false,
                    error = "录音失败: ${e.message}"
                )
            }
        }
    }
    
    /**
     * 停止录音并上传
     */
    fun stopRecordingAndUpload(
        context: Context,
        chatId: String,
        chatType: Long,
        onSuccess: (String, String, Long, Long) -> Unit
    ) {
        viewModelScope.launch {
            try {
                Log.d(tag, "stopRecordingAndUpload chatId=$chatId chatType=$chatType")
                _voiceState.value = _voiceState.value.copy(isRecording = false, isProcessing = true)
                
                val file = AudioUtils.stopRecording()
                if (file == null || !file.exists()) {
                    _voiceState.value = _voiceState.value.copy(
                        isProcessing = false,
                        error = "录音文件保存失败"
                    )
                    return@launch
                }
                
                // 检查录音时长
                val duration = AudioUtils.getAudioDuration(file)
                Log.d(tag, "record duration=$duration sec file=${file.absolutePath}")
                if (duration < 1) {
                    _voiceState.value = _voiceState.value.copy(
                        isProcessing = false,
                        error = "录音时间太短"
                    )
                    file.delete()
                    return@launch
                }
                
                // 重命名文件为 MD5
                val renamedFile = AudioUtils.renameFileWithMD5(file)
                if (renamedFile == null) {
                    _voiceState.value = _voiceState.value.copy(
                        isProcessing = false,
                        error = "文件处理失败"
                    )
                    file.delete()
                    return@launch
                }

                Log.d(tag, "renamed file=${renamedFile.name}")
                
                // 上传并发送
                uploadAndSendVoice(renamedFile, chatId, chatType, duration, onSuccess)
                
            } catch (e: Exception) {
                Log.e(tag, "stopRecordingAndUpload failed", e)
                _voiceState.value = _voiceState.value.copy(
                    isProcessing = false,
                    error = "处理录音失败: ${e.message}"
                )
            }
        }
    }
    
    /**
     * 取消录音
     */
    fun cancelRecording() {
        viewModelScope.launch {
            AudioUtils.cancelRecording()
            _voiceState.value = _voiceState.value.copy(isRecording = false, isProcessing = false)
        }
    }
    
    /**
     * 停止录音但不上传，返回录音文件
     * 用于语音转文字功能
     */
    fun stopRecordingOnly(context: Context, onResult: (File?) -> Unit) {
        viewModelScope.launch {
            try {
                Log.d(tag, "stopRecordingOnly")
                _voiceState.value = _voiceState.value.copy(isRecording = false, isProcessing = true)
                
                val file = AudioUtils.stopRecording()
                if (file == null || !file.exists()) {
                    _voiceState.value = _voiceState.value.copy(isProcessing = false)
                    onResult(null)
                    return@launch
                }
                
                // 检查录音时长
                val duration = AudioUtils.getAudioDuration(file)
                Log.d(tag, "record duration=$duration sec file=${file.absolutePath}")
                if (duration < 1) {
                    _voiceState.value = _voiceState.value.copy(isProcessing = false)
                    file.delete()
                    onResult(null)
                    return@launch
                }
                
                _voiceState.value = _voiceState.value.copy(isProcessing = false)
                onResult(file)
                
            } catch (e: Exception) {
                Log.e(tag, "stopRecordingOnly failed", e)
                _voiceState.value = _voiceState.value.copy(isProcessing = false)
                onResult(null)
            }
        }
    }
    
    /**
     * 上传已有的语音文件
     * 用于语音转文字后直接发送
     */
    fun uploadVoiceFile(
        context: Context,
        file: File,
        chatId: String,
        chatType: Long,
        onSuccess: (String, String, Long, Long) -> Unit
    ) {
        viewModelScope.launch {
            try {
                Log.d(tag, "uploadVoiceFile file=${file.name} chatId=$chatId chatType=$chatType")
                _voiceState.value = _voiceState.value.copy(isProcessing = true)
                
                val duration = AudioUtils.getAudioDuration(file)
                
                // 重命名文件为 MD5
                val renamedFile = AudioUtils.renameFileWithMD5(file)
                if (renamedFile == null) {
                    _voiceState.value = _voiceState.value.copy(
                        isProcessing = false,
                        error = "文件处理失败"
                    )
                    file.delete()
                    return@launch
                }
                
                Log.d(tag, "renamed file=${renamedFile.name}")
                
                // 上传并发送
                uploadAndSendVoice(renamedFile, chatId, chatType, duration, onSuccess)
                
            } catch (e: Exception) {
                Log.e(tag, "uploadVoiceFile failed", e)
                _voiceState.value = _voiceState.value.copy(
                    isProcessing = false,
                    error = "上传语音失败: ${e.message}"
                )
            }
        }
    }
    
    /**
     * 从存储选择音频文件
     */
    fun selectAudioFromStorage(
        context: Context,
        uri: Uri,
        chatId: String,
        chatType: Long,
        onSuccess: (String, String, Long, Long) -> Unit
    ) {
        viewModelScope.launch {
            try {
                Log.d(tag, "selectAudioFromStorage uri=$uri chatId=$chatId chatType=$chatType")
                _voiceState.value = _voiceState.value.copy(isProcessing = true)
                
                val file = AudioUtils.copyAndRenameFile(context, uri)
                if (file == null) {
                    _voiceState.value = _voiceState.value.copy(
                        isProcessing = false,
                        error = "文件处理失败"
                    )
                    return@launch
                }
                
                val duration = AudioUtils.getAudioDuration(file)
                Log.d(tag, "picked duration=$duration sec file=${file.absolutePath}")
                if (duration < 1) {
                    _voiceState.value = _voiceState.value.copy(
                        isProcessing = false,
                        error = "音频文件无效"
                    )
                    file.delete()
                    return@launch
                }
                
                // 上传并发送
                uploadAndSendVoice(file, chatId, chatType, duration, onSuccess)
                
            } catch (e: Exception) {
                Log.e(tag, "selectAudioFromStorage failed", e)
                _voiceState.value = _voiceState.value.copy(
                    isProcessing = false,
                    error = "处理音频文件失败: ${e.message}"
                )
            }
        }
    }
    
    /**
     * 上传音频并发送语音消息
     */
    private suspend fun uploadAndSendVoice(
        file: File,
        chatId: String,
        chatType: Long,
        duration: Long,
        onSuccess: (String, String, Long, Long) -> Unit
    ) {
        try {
            Log.d(tag, "uploadAndSendVoice file=${file.name} size=${file.length()} duration=$duration")
            _voiceState.value = _voiceState.value.copy(isUploading = true)
            
            val token = tokenRepository.getTokenSync()
            if (token.isNullOrEmpty()) {
                _voiceState.value = _voiceState.value.copy(
                    isUploading = false,
                    isProcessing = false,
                    error = "用户未登录"
                )
                return
            }
            
            // 1. 获取七牛云上传 token
            val tokenResponse = apiService.getQiniuAudioToken(token)
            if (!tokenResponse.isSuccessful || tokenResponse.body()?.code != 1) {
                Log.w(tag, "getQiniuAudioToken failed http=${tokenResponse.code()} body=${tokenResponse.body()?.msg}")
                _voiceState.value = _voiceState.value.copy(
                    isUploading = false,
                    isProcessing = false,
                    error = "获取上传凭证失败"
                )
                file.delete()
                return
            }
            
            val qiniuToken = tokenResponse.body()!!.data.token
            Log.d(tag, "qiniuToken prefix=${qiniuToken.split(":").firstOrNull()}")
            
            // 2. 获取上传域名 (从 token 提取 ak 并请求查询)
            val accessKey = qiniuToken.split(":").firstOrNull()
            if (accessKey.isNullOrEmpty()) {
                _voiceState.value = _voiceState.value.copy(
                    isUploading = false,
                    isProcessing = false,
                    error = "上传凭证格式错误"
                )
                file.delete()
                return
            }
            
            val uploadDomain = AudioUtils.getQiniuUploadDomain(accessKey)
            if (uploadDomain.isNullOrEmpty()) {
                Log.w(tag, "getQiniuUploadDomain failed accessKey=$accessKey")
                _voiceState.value = _voiceState.value.copy(
                    isUploading = false,
                    isProcessing = false,
                    error = "获取上传域名失败"
                )
                file.delete()
                return
            }
            
            // 3. 上传文件 (Multipart: token, key, file)
            val uploadResponse = AudioUtils.uploadAudioToQiniu(file, qiniuToken, uploadDomain)
            if (uploadResponse == null) {
                Log.w(tag, "uploadAudioToQiniu failed")
                _voiceState.value = _voiceState.value.copy(
                    isUploading = false,
                    isProcessing = false,
                    error = "文件上传失败"
                )
                file.delete()
                return
            }
            
            // 4. 发送语音消息
            Log.d(tag, "upload ok key=${uploadResponse.key} hash=${uploadResponse.hash} size=${uploadResponse.fsize}")
            sendVoiceMessage(
                chatId = chatId,
                chatType = chatType,
                fileKey = uploadResponse.key,
                fileHash = uploadResponse.hash,
                fileSize = uploadResponse.fsize,
                duration = duration,
                fileExtension = file.extension,
                onSuccess = onSuccess
            )
            
            // 清理本地文件
            file.delete()
            
        } catch (e: Exception) {
            Log.e(tag, "uploadAndSendVoice failed", e)
            _voiceState.value = _voiceState.value.copy(
                isUploading = false,
                isProcessing = false,
                error = "上传失败: ${e.message}"
            )
            file.delete()
        }
    }
    
    /**
     * 发送语音消息 (按用户指定 proto 结构)
     */
    private suspend fun sendVoiceMessage(
        chatId: String,
        chatType: Long,
        fileKey: String,
        fileHash: String,
        fileSize: Long,
        duration: Long,
        fileExtension: String,
        onSuccess: (String, String, Long, Long) -> Unit
    ) {
        try {
            val token = tokenRepository.getTokenSync()
            if (token.isNullOrEmpty()) {
                _voiceState.value = _voiceState.value.copy(isUploading = false, isProcessing = false, error = "用户未登录")
                return
            }
            
            val msgId = UUID.randomUUID().toString().replace("-", "")

            // 构造 Content (对应 proto 中的 Content 消息，标签为 5)
            val contentData = com.yhchat.canary.proto.send_message_send.Content.newBuilder()
                .setAudio(fileKey)
                .setAudioTime(duration)
                .build()

            // 构造 Media (标签为 9)
            val media = com.yhchat.canary.proto.send_message_send.Media.newBuilder()
                .setFileKey(fileKey)
                .setFileHash(fileHash)
                .setFileType("video/mp4") // 必须固定为 video/mp4 才会显示音频
                .setFileSize(fileSize)
                .setFileKey2(fileKey)
                .setFileSuffix(fileExtension.lowercase(Locale.getDefault()))
                .build()

            // 构造完整的发送消息请求
            val sendMessage = com.yhchat.canary.proto.send_message_send.newBuilder()
                .setMsgId(msgId)
                .setChatId(chatId)
                .setChatType(chatType)
                .setContent(contentData)
                .setContentType(11) // 11 为语音消息
                .setMedia(media)
                .build()

            val requestBody = sendMessage.toByteArray().toRequestBody("application/x-protobuf".toMediaType())
            
            Log.d(tag, "Sending voice message: chatId=$chatId, key=$fileKey, msgId=$msgId")
            val response = apiService.sendMessage(token, requestBody)
            
            if (!response.isSuccessful) {
                _voiceState.value = _voiceState.value.copy(isUploading = false, isProcessing = false, error = "发送请求失败: ${response.code()}")
                return
            }

            // 解析响应 (send_message -> status -> code/msg)
            val respBytes = response.body()?.bytes()
            val sendResp = try {
                if (respBytes != null) com.yhchat.canary.proto.send_message.parseFrom(respBytes) else null
            } catch (e: Exception) {
                Log.e(tag, "Parse response failed", e)
                null
            }

            val status = sendResp?.status
            if (status != null) {
                Log.d(tag, "Response status: code=${status.code}, msg=${status.msg}")
                if (status.code == 1) {
                    Log.d(tag, "Voice message sent successfully")
                    _voiceState.value = _voiceState.value.copy(isUploading = false, isProcessing = false)
                    onSuccess(fileKey, fileHash, fileSize, duration)
                } else {
                    val errMsg = if (status.msg.isNullOrEmpty()) "发送失败(code=${status.code})" else status.msg
                    _voiceState.value = _voiceState.value.copy(isUploading = false, isProcessing = false, error = errMsg)
                }
            } else {
                Log.w(tag, "sendMessage response has no status, treating as error")
                _voiceState.value = _voiceState.value.copy(isUploading = false, isProcessing = false, error = "解析响应失败")
            }
            
        } catch (e: Exception) {
            Log.e(tag, "sendVoiceMessage fatal error", e)
            _voiceState.value = _voiceState.value.copy(isUploading = false, isProcessing = false, error = "发送错误: ${e.message}")
        }
    }

    fun clearError() {
        _voiceState.value = _voiceState.value.copy(error = null)
    }
}
