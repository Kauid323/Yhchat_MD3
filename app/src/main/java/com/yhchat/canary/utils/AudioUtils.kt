package com.yhchat.canary.utils

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * 音频处理工具类
 */
object AudioUtils {
    
    private const val TAG = "AudioUtils"
    
    private var mediaRecorder: MediaRecorder? = null
    private var recordingFile: File? = null
    
    /**
     * 开始录音
     */
    @Suppress("DEPRECATION")
    suspend fun startRecording(context: Context): File? = withContext(Dispatchers.IO) {
        runCatching {
            val audioDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "voice")
            if (!audioDir.exists()) {
                audioDir.mkdirs()
            }
            
            val fileName = "voice_${System.currentTimeMillis()}.m4a"
            recordingFile = File(audioDir, fileName)
            
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(recordingFile!!.absolutePath)
                prepare()
                start()
            }
            
            recordingFile
        }.onFailure { e ->
            Log.e(TAG, "开始录音失败", e)
        }.getOrNull()
    }
    
    /**
     * 停止录音
     */
    suspend fun stopRecording(): File? = withContext(Dispatchers.IO) {
        runCatching {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            recordingFile
        }.onFailure { e ->
            Log.e(TAG, "停止录音失败", e)
        }.getOrElse { recordingFile }
    }
    
    /**
     * 取消录音
     */
    suspend fun cancelRecording() = withContext(Dispatchers.IO) {
        runCatching {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            recordingFile?.delete()
            recordingFile = null
        }.onFailure { e ->
            Log.e(TAG, "取消录音失败", e)
        }
    }
    
    /**
     * 计算文件MD5
     */
    suspend fun calculateMD5(file: File): String = withContext(Dispatchers.IO) {
        runCatching {
            val md = MessageDigest.getInstance("MD5")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var length: Int
                while (fis.read(buffer).also { length = it } != -1) {
                    md.update(buffer, 0, length)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        }.onFailure { e ->
            Log.e(TAG, "计算MD5失败", e)
        }.getOrDefault("")
    }
    
    /**
     * 重命名文件为 MD5 + 扩展名
     */
    suspend fun renameFileWithMD5(file: File): File? = withContext(Dispatchers.IO) {
        runCatching {
            val md5 = calculateMD5(file)
            if (md5.isEmpty()) return@runCatching null
            
            val extension = file.extension
            val newFileName = "$md5.$extension"
            val newFile = File(file.parent, newFileName)
            
            if (file.renameTo(newFile)) newFile else null
        }.onFailure { e ->
            Log.e(TAG, "重命名文件失败", e)
        }.getOrNull()
    }
    
    /**
     * 从URI复制文件到应用目录并重命名
     */
    suspend fun copyAndRenameFile(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
        runCatching {
            val audioDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "voice")
            if (!audioDir.exists()) {
                audioDir.mkdirs()
            }
            
            // 获取文件扩展名
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri)
            val extension = when (mimeType) {
                "audio/mpeg" -> "mp3"
                "audio/mp4", "audio/m4a" -> "m4a"
                "audio/wav" -> "wav"
                "audio/flac" -> "flac"
                else -> "m4a" // 默认
            }
            
            // 创建临时文件
            val tempFile = File(audioDir, "temp_${System.currentTimeMillis()}.$extension")
            
            contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            // 重命名为MD5
            renameFileWithMD5(tempFile)
        }.onFailure { e ->
            Log.e(TAG, "复制并重命名文件失败", e)
        }.getOrNull()
    }
    
    /**
     * 获取音频文件时长（秒）
     */
    suspend fun getAudioDuration(file: File): Long = withContext(Dispatchers.IO) {
        runCatching {
            val mediaMetadataRetriever = android.media.MediaMetadataRetriever()
            mediaMetadataRetriever.setDataSource(file.absolutePath)
            val duration = mediaMetadataRetriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            mediaMetadataRetriever.release()
            (duration?.toLongOrNull() ?: 0L) / 1000 // 转换为秒
        }.onFailure { e ->
            Log.e(TAG, "获取音频时长失败", e)
        }.getOrDefault(0L)
    }
    
    /**
     * 上传音频到七牛云
     */
    suspend fun uploadAudioToQiniu(
        file: File,
        token: String,
        uploadUrl: String,
        onProgress: (Float) -> Unit = {}
    ): com.yhchat.canary.data.model.QiniuUploadResponse? = withContext(Dispatchers.IO) {
        runCatching {
            val client = OkHttpClient()
            
            val fileKey = file.nameWithoutExtension + "." + file.extension
            
            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("token", token)
                .addFormDataPart("key", fileKey)
                .addFormDataPart(
                    "file", 
                    file.name,
                    file.asRequestBody("audio/*".toMediaType())
                )
                .build()
                
            val requestBody = com.yhchat.canary.utils.upload.ProgressRequestBody(multipartBody, onProgress)
            
            val request = Request.Builder()
                .url(uploadUrl)
                .post(requestBody)
                .build()
            
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string()?.let {
                    com.google.gson.Gson().fromJson(it, com.yhchat.canary.data.model.QiniuUploadResponse::class.java)
                }
            } else {
                Log.e(TAG, "上传音频失败: ${response.code}")
                null
            }
        }.onFailure { e ->
            Log.e(TAG, "上传音频到七牛云异常", e)
        }.getOrNull()
    }
    
    /**
     * 获取七牛云上传域名
     */
    suspend fun getQiniuUploadDomain(accessKey: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val client = OkHttpClient()
            val url = "https://api.qiniu.com/v4/query?ak=$accessKey&bucket=chat68-audio"
            
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string()?.let {
                    val hostsResponse = com.google.gson.Gson().fromJson(it, com.yhchat.canary.data.model.QiniuHostsResponse::class.java)
                    hostsResponse.hosts.firstOrNull()?.up?.domains?.firstOrNull()?.let { domain ->
                        "https://$domain"
                    }
                }
            } else {
                Log.e(TAG, "获取七牛云上传域名失败: ${response.code}")
                null
            }
        }.onFailure { e ->
            Log.e(TAG, "获取七牛云上传域名异常", e)
        }.getOrNull()
    }
    
    /**
     * 将文字转为语音并上传到七牛云
     * @param context Context
     * @param text 要转换的文字
     * @param qiniuToken 七牛云上传Token
     * @param uploadUrl 七牛云上传URL
     * @return 上传结果
     */
    suspend fun textToSpeechAndUpload(
        context: Context,
        text: String,
        qiniuToken: String,
        uploadUrl: String
    ): com.yhchat.canary.data.model.QiniuUploadResponse? = withContext(Dispatchers.IO) {
        // 检查TTS是否开启
        if (!TTSUtils.isTTSEnabled(context)) {
            Log.d(TAG, "TTS功能未开启")
            return@withContext null
        }
        
        Log.d(TAG, "开始TTS转换: $text")
        
        // 初始化TTS
        if (!TTSUtils.initialize(context)) {
            Log.e(TAG, "TTS初始化失败")
            return@withContext null
        }
        
        // 应用用户设置的语速和音调
        TTSUtils.setSpeechRate(TTSUtils.getTTSSpeechRate(context))
        TTSUtils.setPitch(TTSUtils.getTTSPitch(context))
        
        // 转换文字为语音文件
        val ttsFile = TTSUtils.textToSpeechFileWithMD5(context, text)
        if (ttsFile == null) {
            Log.e(TAG, "TTS文件生成失败")
            return@withContext null
        }
        
        Log.d(TAG, "TTS文件生成成功: ${ttsFile.absolutePath}")
        
        // 上传到七牛云
        val result = uploadAudioToQiniu(ttsFile, qiniuToken, uploadUrl)
        
        // 上传完成后清理临时文件
        ttsFile.delete()
        
        result
    }
    
    /**
     * 检查是否应该使用TTS上传
     * @param context Context
     * @return 是否启用TTS
     */
    fun shouldUseTTS(context: Context): Boolean {
        return TTSUtils.isTTSEnabled(context)
    }
}
