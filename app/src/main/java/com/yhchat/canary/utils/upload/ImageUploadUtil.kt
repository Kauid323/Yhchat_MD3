package com.yhchat.canary.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.Log
import com.yhchat.canary.data.api.QiniuUploadResponse
import com.yhchat.canary.utils.upload.ProgressRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * 图片上传工具类
 * 参考Python实现：tool.py中的upload方法
 */
object ImageUploadUtil {
    
    private const val TAG = "ImageUploadUtil"
    
    // 七牛bucket映射
    private const val IMAGE_BUCKET = "chat68"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private data class PreparedImageUpload(
        val tempFile: File,
        val mimeType: String,
        val width: Int,
        val height: Int,
        val md5: String
    )
    
    /**
     * 压缩图片为WebP格式并落到临时文件，避免大字节数组常驻内存（自动校正EXIF方向）
     */
    private suspend fun prepareCompressedWebP(
        context: Context,
        imageUri: Uri,
        quality: Int = 95
    ): PreparedImageUpload = withContext(Dispatchers.IO) {
        Log.d(TAG, "🗜️ 开始压缩图片为WebP格式，质量: $quality%")

        val rotation = getExifRotationDegrees(context, imageUri)
        if (rotation != 0) {
            Log.d(TAG, "🧭 检测到图片EXIF旋转: $rotation°，将自动校正方向")
        }

        // 先获取图片尺寸，按需下采样避免超大图OOM
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(imageUri)?.use {
            BitmapFactory.decodeStream(it, null, boundsOptions)
        }
        val maxDim = 4096
        var sampleSize = 1
        while (boundsOptions.outWidth / sampleSize > maxDim || boundsOptions.outHeight / sampleSize > maxDim) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }
        val inputStream = context.contentResolver.openInputStream(imageUri)
            ?: throw Exception("无法读取图片")
        val decodedBitmap = inputStream.use { BitmapFactory.decodeStream(it, null, decodeOptions) }

        if (decodedBitmap == null) {
            throw Exception("无法解码图片")
        }

        val finalBitmap = rotateBitmapIfRequired(decodedBitmap, rotation)

        Log.d(TAG, "✅ 纠正方向后的图片尺寸: ${finalBitmap.width}x${finalBitmap.height}")

        val tempFile = File(
            context.cacheDir,
            "image_upload_${System.currentTimeMillis()}_${imageUri.hashCode()}.webp"
        )
        try {
            val success = FileOutputStream(tempFile).use { outputStream ->
                finalBitmap.compress(Bitmap.CompressFormat.WEBP, quality, outputStream)
            }
            if (!success) {
                throw Exception("WebP压缩失败")
            }

            val md5 = calculateMD5(tempFile)
            Log.d(TAG, "✅ WebP压缩完成，压缩后大小: ${tempFile.length()} bytes")

            PreparedImageUpload(
                tempFile = tempFile,
                mimeType = "image/webp",
                width = finalBitmap.width,
                height = finalBitmap.height,
                md5 = md5
            )
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        } finally {
            finalBitmap.recycle()
        }
    }

    /**
     * 以流式方式准备原图上传文件，避免整图 readBytes
     */
    private suspend fun prepareOriginalImage(
        context: Context,
        imageUri: Uri
    ): PreparedImageUpload = withContext(Dispatchers.IO) {
        Log.d(TAG, "📖 开始读取原图")

        val mimeType = context.contentResolver.getType(imageUri) ?: "image/jpeg"
        val extension = when (mimeType) {
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/bmp" -> "bmp"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val tempFile = File(
            context.cacheDir,
            "image_upload_${System.currentTimeMillis()}_${imageUri.hashCode()}.$extension"
        )

        val inputStream = context.contentResolver.openInputStream(imageUri)
            ?: throw Exception("无法读取图片")
        try {
            val (md5, totalBytes) = copyToTempFileAndCalculateMD5(inputStream, tempFile)
            val (width, height) = decodeImageBounds(tempFile)

            Log.d(TAG, "✅ 原图读取完成，大小: $totalBytes bytes, MIME: $mimeType")

            PreparedImageUpload(
                tempFile = tempFile,
                mimeType = mimeType,
                width = width,
                height = height,
                md5 = md5
            )
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    /**
     * 上传图片到七牛云（支持原图上传或WebP压缩）
     * 参考Python实现：tool.py中的upload方法
     * @param context 上下文
     * @param imageUri 图片URI
     * @param uploadToken 七牛上传token
     * @return 上传结果，包含key、hash等信息
     */
    suspend fun uploadImage(
        context: Context,
        imageUri: Uri,
        uploadToken: String,
        onProgress: (Float) -> Unit = {}
    ): Result<QiniuUploadResponse> = withContext(Dispatchers.IO) {
        var preparedImage: PreparedImageUpload? = null
        try {
            Log.d(TAG, "📤 ========== 开始上传图片 ==========")
            Log.d(TAG, "📤 图片URI: $imageUri")
            
            // 1. 获取图片上传设置
            val sharedPrefs = context.getSharedPreferences("image_settings", Context.MODE_PRIVATE)
            val uploadOriginal = sharedPrefs.getBoolean("upload_original_image", false)
            val webpQuality = sharedPrefs.getInt("webp_quality", 95)
            
            Log.d(TAG, "⚙️ 上传设置 - 原图上传: $uploadOriginal, WebP质量: $webpQuality")
            
            // 2. 根据设置决定是否压缩
            preparedImage = if (uploadOriginal) {
                // 上传原图
                prepareOriginalImage(context, imageUri)
            } else {
                // 压缩为WebP格式
                prepareCompressedWebP(context, imageUri, webpQuality)
            }
            val prepared = preparedImage
                ?: return@withContext Result.failure(Exception("图片预处理失败"))

            Log.d(
                TAG,
                "✅ 图片处理完成，大小: ${prepared.tempFile.length()} bytes, MIME: ${prepared.mimeType}"
            )
            Log.d(TAG, "✅ MD5计算完成: ${prepared.md5}")
            
            // 4. 根据MIME类型确定文件扩展名
            val extension = when (prepared.mimeType) {
                "image/webp" -> "webp"
                "image/png" -> "png"
                "image/gif" -> "gif"
                "image/bmp" -> "bmp"
                "image/jpeg" -> "jpg"
                else -> "jpg" // 默认为jpg
            }
            
            // 文件key = MD5.扩展名
            val fileKey = "${prepared.md5}.$extension"
            Log.d(TAG, "✅ 文件key: $fileKey")
            Log.d(TAG, "✅ MIME类型: ${prepared.mimeType}")
            Log.d(TAG, "✅ 图片尺寸: ${prepared.width}x${prepared.height}")
            
            // 6. 获取上传host - 参考Python实现
            // uhost = httpx.get(f"https://api.qiniu.com/v4/query?ak={utoken.split(':')[0]}&bucket={bucket}").json()["hosts"][0]["up"]["domains"][0]
            val ak = uploadToken.split(":")[0]
            val queryUrl = "https://api.qiniu.com/v4/query?ak=$ak&bucket=$IMAGE_BUCKET"
            Log.d(TAG, "📤 查询上传host: $queryUrl")
            
            val queryRequest = Request.Builder()
                .url(queryUrl)
                .get()
                .build()

            val uploadHost = client.newCall(queryRequest).execute().use { queryResponse ->
                if (queryResponse.isSuccessful) {
                    val queryJson = JSONObject(queryResponse.body?.string() ?: "{}")
                    val hosts = queryJson.getJSONArray("hosts")
                    val host = hosts.getJSONObject(0)
                    val up = host.getJSONObject("up")
                    val domains = up.getJSONArray("domains")
                    domains.getString(0)
                } else {
                    Log.w(TAG, "⚠️ 查询host失败，使用默认: upload-z2.qiniup.com")
                    "upload-z2.qiniup.com"
                }
            }
            
            Log.d(TAG, "✅ 上传host: $uploadHost")
            Log.d(TAG, "✅ 临时文件: ${prepared.tempFile.absolutePath}")
            
            // 8. 构建multipart/form-data请求 - 参考Python实现
            // params = {
            //     "token": (None, utoken),
            //     "key": (None, name),
            //     "file": (name, file)
            // }
            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("token", uploadToken)
                .addFormDataPart("key", fileKey)
                .addFormDataPart(
                    "file",
                    fileKey,
                    prepared.tempFile.asRequestBody(prepared.mimeType.toMediaTypeOrNull())
                )
                .build()
                
            val requestBody = ProgressRequestBody(multipartBody, onProgress)
            
            Log.d(TAG, "📤 开始上传到七牛云...")
            Log.d(TAG, "📤 上传URL: https://$uploadHost")
            
            val request = Request.Builder()
                .url("https://$uploadHost")
                .addHeader("user-agent", "QiniuDart")
                .addHeader("accept-encoding", "gzip")
                .post(requestBody)
                .build()
            
            // 9. 执行上传
            client.newCall(request).execute().use { response ->
                Log.d(TAG, "📥 七牛云响应码: ${response.code}")

                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    Log.d(TAG, "✅ 上传成功！响应: $responseBody")

                    if (responseBody != null) {
                        val json = JSONObject(responseBody)

                        // 解析响应
                        val uploadResponse = QiniuUploadResponse(
                            key = json.getString("key"),
                            hash = json.getString("hash"),
                            fsize = json.getLong("fsize"),
                            avinfo = if (json.has("avinfo")) {
                                val avinfo = json.getJSONObject("avinfo")
                                val videoInfo = if (avinfo.has("video")) {
                                    val video = avinfo.getJSONObject("video")
                                    com.yhchat.canary.data.api.QiniuVideoInfo(
                                        width = video.optInt("width", prepared.width),
                                        height = video.optInt("height", prepared.height)
                                    )
                                } else null
                                com.yhchat.canary.data.api.QiniuAvInfo(video = videoInfo)
                            } else {
                                com.yhchat.canary.data.api.QiniuAvInfo(
                                    video = com.yhchat.canary.data.api.QiniuVideoInfo(
                                        width = prepared.width,
                                        height = prepared.height
                                    )
                                )
                            }
                        )

                        Log.d(TAG, "✅ ========== 上传完成 ==========")
                        Log.d(TAG, "✅ key: ${uploadResponse.key}")
                        Log.d(TAG, "✅ hash: ${uploadResponse.hash}")
                        Log.d(TAG, "✅ size: ${uploadResponse.fsize}")

                        Result.success(uploadResponse)
                    } else {
                        Log.e(TAG, "❌ 响应体为空")
                        Result.failure(Exception("上传失败：响应为空"))
                    }
                } else {
                    val errorBody = response.body?.string()
                    Log.e(TAG, "❌ 上传失败: ${response.code}")
                    Log.e(TAG, "❌ 错误详情: $errorBody")
                    Result.failure(Exception("上传失败: ${response.code}"))
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 上传异常", e)
            e.printStackTrace()
            Result.failure(e)
        } finally {
            preparedImage?.tempFile?.let { file ->
                if (file.exists() && !file.delete()) {
                    Log.w(TAG, "⚠️ 临时图片删除失败: ${file.absolutePath}")
                }
            }
        }
    }
    
    /**
     * 获取七牛云上传token
     */
    suspend fun getQiniuUploadToken(context: Context, token: String): String = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔑 开始获取七牛云上传token")
            Log.d(TAG, "🔑 用户token: ${token.take(10)}...")
            
            val request = Request.Builder()
                .url("https://chat-go.jwzhd.com/v1/misc/qiniu-token")
                .addHeader("token", token)
                .addHeader("Content-Type", "application/json")
                .get()
                .build()
            
            Log.d(TAG, "🔑 发送请求到: ${request.url}")
            
            client.newCall(request).execute().use { response ->
                val responseCode = response.code
                val responseBody = response.body?.string()

                Log.d(TAG, "🔑 响应码: $responseCode")
                Log.d(TAG, "🔑 响应体: $responseBody")

                if (response.isSuccessful && responseBody != null) {
                    val jsonObject = JSONObject(responseBody)
                    val code = jsonObject.optInt("code", 0)
                    if (code == 1) {
                        val dataObject = jsonObject.optJSONObject("data")
                        val uploadToken = dataObject?.optString("token", null)
                        Log.d(TAG, "🔑 获取到上传token: ${uploadToken?.take(20)}...")
                        uploadToken ?: ""
                    } else {
                        val msg = jsonObject.optString("msg", "未知错误")
                        Log.e(TAG, "🔑 API返回错误: code=$code, msg=$msg")
                        ""
                    }
                } else {
                    Log.e(TAG, "🔑 获取token失败: $responseCode - $responseBody")
                    ""
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "🔑 获取token异常: ${e.message}", e)
            ""
        }
    }
    
    /**
     * 以流式方式复制文件并计算MD5
     */
    private fun copyToTempFileAndCalculateMD5(inputStream: InputStream, tempFile: File): Pair<String, Long> {
        val md5Digest = MessageDigest.getInstance("MD5")
        var totalBytes = 0L
        inputStream.use { input ->
            FileOutputStream(tempFile).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    md5Digest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                    totalBytes += read
                }
                output.flush()
            }
        }
        val md5 = md5Digest.digest().joinToString("") { "%02x".format(it) }
        return md5 to totalBytes
    }

    private fun decodeImageBounds(file: File): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        FileInputStream(file).use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
        val rotation = getExifRotationDegrees(file)
        return if (rotation == 90 || rotation == 270) {
            options.outHeight to options.outWidth
        } else {
            options.outWidth to options.outHeight
        }
    }

    private fun getExifRotationDegrees(context: Context, uri: Uri): Int {
        return try {
            val orientation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val exif = ExifInterface(inputStream)
                    exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                } ?: ExifInterface.ORIENTATION_NORMAL
            } else {
                if (uri.scheme == "file" && uri.path != null) {
                    val exif = ExifInterface(uri.path!!)
                    exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                } else {
                    ExifInterface.ORIENTATION_NORMAL
                }
            }

            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ 读取EXIF旋转角度失败: ${e.message}")
            0
        }
    }

    private fun getExifRotationDegrees(file: File): Int {
        return try {
            val exif = ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (e: Exception) {
            0
        }
    }

    private fun rotateBitmapIfRequired(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        return try {
            val matrix = Matrix().apply {
                postRotate(degrees.toFloat())
            }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) {
                bitmap.recycle()
            }
            rotated
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ 旋转Bitmap失败: ${e.message}", e)
            bitmap
        }
    }

    private fun calculateMD5(file: File): String {
        val md5Digest = MessageDigest.getInstance("MD5")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                md5Digest.update(buffer, 0, read)
            }
        }
        return md5Digest.digest().joinToString("") { "%02x".format(it) }
    }
}
