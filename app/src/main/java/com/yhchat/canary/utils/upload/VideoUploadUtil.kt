package com.yhchat.canary.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import com.yhchat.canary.data.api.QiniuUploadResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * 视频上传工具类
 * 实现视频上传到七牛云的完整流程
 */
object VideoUploadUtil {
    
    private const val TAG = "VideoUploadUtil"
    
    // 视频bucket名称
    private const val VIDEO_BUCKET = "chat68-video"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(180, TimeUnit.SECONDS)  // 视频文件较大，增加超时时间
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .build()
    
    /**
     * 上传视频到七牛云
     * @param context 上下文
     * @param videoUri 视频URI
     * @param uploadToken 七牛上传token（从/v1/misc/qiniu-token-video获取）
     * @param onProgress 上传进度回调 (0.0 - 1.0)
     * @return 上传结果，包含key、hash、avinfo等信息
     */
    suspend fun uploadVideo(
        context: Context,
        videoUri: Uri,
        uploadToken: String,
        onProgress: (Float) -> Unit = {}
    ): Result<QiniuUploadResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "📹 ========== 开始上传视频 ==========")
            Log.d(TAG, "📹 视频URI: $videoUri")
            Log.d(TAG, "📹 上传Token: ${uploadToken.take(30)}...")
            
            // 1. 获取文件信息
            val originalFileName = getFileName(context, videoUri) ?: "video_${System.currentTimeMillis()}.mp4"
            val extension = getFileExtension(originalFileName)
            
            // 2. 获取文件大小
            val fileSizeBytes = getFileSize(context, videoUri)
            Log.d(TAG, "✅ 视频文件大小: $fileSizeBytes bytes (${fileSizeBytes / 1024 / 1024}MB)")
            
            // 3. 检查文件大小限制（建议不超过500MB）
            if (fileSizeBytes > 500 * 1024 * 1024) {
                return@withContext Result.failure(Exception("视频文件过大，请选择小于500MB的视频"))
            }
            
            // 4. 流式计算MD5和复制到临时文件
            val cacheDir = context.cacheDir
            val tempFileName = "temp_video_${System.currentTimeMillis()}.$extension"
            val tempFile = File(cacheDir, tempFileName)
            
            val md5 = calculateMD5AndCopyFile(context, videoUri, tempFile)
            Log.d(TAG, "✅ MD5计算完成: $md5")
            
            // 5. 重命名临时文件为最终文件名
            val finalTempFile = File(cacheDir, "$md5.$extension")
            if (finalTempFile.exists()) {
                finalTempFile.delete()
            }
            tempFile.renameTo(finalTempFile)
            
            // 视频key = MD5.扩展名
            val videoKey = "$md5.$extension"
            Log.d(TAG, "✅ 原始文件名: $originalFileName")
            Log.d(TAG, "✅ 文件扩展名: $extension")
            Log.d(TAG, "✅ 视频key: $videoKey")
            
            // 6. 获取MIME类型
            val mimeType = context.contentResolver.getType(videoUri) 
                ?: getMimeTypeFromExtension(extension)
                ?: "video/mp4"
            Log.d(TAG, "✅ MIME类型: $mimeType")
            Log.d(TAG, "✅ 临时文件: ${finalTempFile.absolutePath}")
            
            // 6. 查询正确的上传host
            Log.d(TAG, "📹 查询上传区域...")
            val ak = uploadToken.split(":")[0]
            val queryUrl = "https://api.qiniu.com/v4/query?ak=$ak&bucket=$VIDEO_BUCKET"
            Log.d(TAG, "📹 查询URL: $queryUrl")
            
            val queryRequest = Request.Builder()
                .url(queryUrl)
                .get()
                .build()
            
            val uploadHost = client.newCall(queryRequest).execute().use { queryResponse ->
                if (!queryResponse.isSuccessful) return@use null
                
                val queryJson = JSONObject(queryResponse.body?.string() ?: "{}")
                Log.d(TAG, "📥 区域查询响应: $queryJson")
                val hosts = queryJson.getJSONArray("hosts")
                val host = hosts.getJSONObject(0)
                val up = host.getJSONObject("up")
                val domains = up.getJSONArray("domains")
                val resultHost = domains.getString(0)
                Log.d(TAG, "✅ 上传host: $resultHost")
                resultHost
            } ?: "upload-cn-east-2.qiniup.com"
            
            // 7. 构建multipart/form-data请求
            Log.d(TAG, "📹 构建multipart/form-data请求体...")
            
            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("token", uploadToken)
                .addFormDataPart("key", videoKey)
                .addFormDataPart(
                    "file",
                    originalFileName,
                    finalTempFile.asRequestBody(mimeType.toMediaTypeOrNull())
                )
                .build()
            
            val requestBody = ProgressRequestBody(multipartBody, onProgress)
            
            Log.d(TAG, "✅ 请求体构建完成")
            
            val uploadUrl = "https://$uploadHost/"
            Log.d(TAG, "📹 上传URL: $uploadUrl")
            
            val request = Request.Builder()
                .url(uploadUrl)
                .addHeader("user-agent", "QiniuDart")
                .addHeader("accept-encoding", "gzip")
                .post(requestBody)
                .build()
            
            Log.d(TAG, "📹 开始上传到七牛云...")
            Log.d(TAG, "📹 请求头:")
            request.headers.forEach {
                Log.d(TAG, "   ${it.first}: ${it.second}")
            }
            
            // 8. 执行上传
            val response = client.newCall(request).execute()
            
            Log.d(TAG, "📥 七牛云响应码: ${response.code}")
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                Log.d(TAG, "✅ 上传成功！响应: $responseBody")
                
                if (responseBody != null) {
                    val json = JSONObject(responseBody)
                    
                    // 解析响应 - 包含key, hash, fsize, avinfo
                    val uploadResponse = QiniuUploadResponse(
                        key = json.getString("key"),
                        hash = json.getString("hash"),
                        fsize = json.getLong("fsize"),
                        avinfo = json.optJSONObject("avinfo")?.let { avinfoJson ->
                            com.yhchat.canary.data.api.QiniuAvInfo(
                                video = avinfoJson.optJSONObject("video")?.let { videoJson ->
                                    com.yhchat.canary.data.api.QiniuVideoInfo(
                                        width = videoJson.optInt("width", 0),
                                        height = videoJson.optInt("height", 0)
                                    )
                                }
                            )
                        }
                    )
                    
                    Log.d(TAG, "✅ ========== 视频上传完成 ==========")
                    Log.d(TAG, "✅ key: ${uploadResponse.key}")
                    Log.d(TAG, "✅ hash: ${uploadResponse.hash}")
                    Log.d(TAG, "✅ size: ${uploadResponse.fsize}")
                    Log.d(TAG, "✅ 视频尺寸: ${uploadResponse.avinfo?.video?.width}x${uploadResponse.avinfo?.video?.height}")
                    
                    // 清理临时文件
                    finalTempFile.delete()
                    
                    Result.success(uploadResponse)
                } else {
                    Log.e(TAG, "❌ 响应体为空")
                    finalTempFile.delete()
                    Result.failure(Exception("上传失败：响应为空"))
                }
            } else {
                val errorBody = response.body?.string()
                Log.e(TAG, "❌ 上传失败: ${response.code}")
                Log.e(TAG, "❌ 错误详情: $errorBody")
                finalTempFile.delete()
                Result.failure(Exception("上传失败: ${response.code} - $errorBody"))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 上传异常", e)
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    /**
     * 计算字节数组的MD5值
     */
    private fun calculateMD5(bytes: ByteArray): String {
        val md5Digest = MessageDigest.getInstance("MD5")
        val md5Bytes = md5Digest.digest(bytes)
        return md5Bytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * 从URI获取文件名
     */
    private fun getFileName(context: Context, uri: Uri): String? {
        var fileName: String? = null
        
        // 优先尝试从ContentProvider获取DISPLAY_NAME
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ 无法从ContentResolver获取文件名", e)
        }
        
        // 如果ContentProvider失败，从URI的path获取并清理
        if (fileName == null) {
            uri.lastPathSegment?.let { segment ->
                // 移除可能的前缀（如 "primary:Download/"）
                fileName = if (segment.contains('/')) {
                    segment.substringAfterLast('/')
                } else if (segment.contains(':')) {
                    segment.substringAfterLast(':')
                } else {
                    segment
                }
            }
        }
        
        // 如果还是空，使用默认名称
        if (fileName.isNullOrBlank()) {
            fileName = "video_${System.currentTimeMillis()}.mp4"
        }
        
        return fileName
    }
    
    /**
     * 获取文件扩展名
     */
    private fun getFileExtension(fileName: String): String {
        val lastDot = fileName.lastIndexOf('.')
        return if (lastDot >= 0 && lastDot < fileName.length - 1) {
            fileName.substring(lastDot + 1)
        } else {
            "mp4"  // 默认视频扩展名
        }
    }
    
    /**
     * 根据扩展名获取MIME类型
     */
    private fun getMimeTypeFromExtension(extension: String): String? {
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
    }
    
    /**
     * 获取文件大小
     */
    private fun getFileSize(context: Context, uri: Uri): Long {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (sizeIndex >= 0) {
                        return cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ 无法从ContentResolver获取文件大小", e)
        }
        
        // 如果ContentResolver失败，尝试通过输入流获取
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                return inputStream.available().toLong()
            }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ 无法通过输入流获取文件大小", e)
        }
        
        return 0L
    }
    
    /**
     * 流式计算MD5并复制文件到临时位置
     */
    private fun calculateMD5AndCopyFile(context: Context, uri: Uri, outputFile: File): String {
        val md5Digest = MessageDigest.getInstance("MD5")
        val buffer = ByteArray(8192) // 8KB缓冲区
        
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            FileOutputStream(outputFile).use { outputStream ->
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    // 更新MD5
                    md5Digest.update(buffer, 0, bytesRead)
                    // 写入输出文件
                    outputStream.write(buffer, 0, bytesRead)
                }
            }
        } ?: throw Exception("无法打开输入流")
        
        val md5Bytes = md5Digest.digest()
        return md5Bytes.joinToString("") { "%02x".format(it) }
    }
}
