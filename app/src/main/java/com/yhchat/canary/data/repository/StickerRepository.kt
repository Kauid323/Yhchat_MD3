package com.yhchat.canary.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.yhchat.canary.data.api.ApiService
import com.yhchat.canary.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 贴纸仓库
 */
@Singleton
class StickerRepository @Inject constructor(
    private val apiService: ApiService,
    private val tokenRepository: TokenRepository
) {
    companion object {
        private const val TAG = "StickerRepository"
    }

    /**
     * 获取收藏表情包列表
     */
    suspend fun getStickerPackList(): Result<List<StickerPack>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = tokenRepository.getTokenSync()
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "Token为空")
                return@withContext Result.failure(Exception("未登录"))
            }

            val response = apiService.getStickerPackList(token)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Log.d(TAG, "✅ 获取贴纸包列表成功: ${body.data.stickerPacks.size}个贴纸包")
                    Result.success(body.data.stickerPacks)
                } else {
                    val error = "获取贴纸包列表失败: ${response.code().toString()}"
                    Log.e(TAG, error)
                    Result.failure(Exception(error))
                }
            } else {
                val error = "获取贴纸包列表失败: ${response.code()}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 获取贴纸包列表异常", e)
            Result.failure(e)
        }
    }

    /**
     * 查看表情包详情
     */
    suspend fun getStickerPackDetail(packId: Long): Result<StickerPackDetailData> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = tokenRepository.getTokenSync()
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "Token为空")
                return@withContext Result.failure(Exception("未登录"))
            }

            val request = StickerPackActionRequest(id = packId)
            val response = apiService.getStickerPackDetail(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Log.d(TAG, "✅ 获取贴纸包详情成功")
                    Result.success(body.data)
                        } else {
                    val error = "获取贴纸包详情失败: ${response.code().toString()}"
                    Log.e(TAG, error)
                    Result.failure(Exception(error))
                        }
                    } else {
                val error = "获取贴纸包详情失败: ${response.code()}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 获取贴纸包详情异常", e)
            Result.failure(e)
        }
    }
    
    /**
     * 添加表情包
     */
    suspend fun addStickerPack(packId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = tokenRepository.getTokenSync()
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "Token为空")
                return@withContext Result.failure(Exception("未登录"))
            }

            val request = StickerPackActionRequest(id = packId)
            val response = apiService.addStickerPack(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Log.d(TAG, "✅ 添加贴纸包成功")
                    Result.success(true)
                    } else {
                    val error = body?.message ?: "添加贴纸包失败: ${response.code()}"
                    Log.e(TAG, error)
                    Result.failure(Exception(error))
                    }
            } else {
                val error = "添加贴纸包失败: ${response.code()}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 添加贴纸包异常", e)
            Result.failure(e)
        }
    }
    
    /**
     * 移除收藏表情包
     */
    suspend fun removeStickerPack(packId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = tokenRepository.getTokenSync()
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "Token为空")
                return@withContext Result.failure(Exception("未登录"))
            }

            val request = StickerPackActionRequest(id = packId)
            val response = apiService.removeStickerPack(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Log.d(TAG, "✅ 移除贴纸包成功")
                    Result.success(true)
                } else {
                    val error = body?.message ?: "移除贴纸包失败: ${response.code()}"
                    Log.e(TAG, error)
                    Result.failure(Exception(error))
                }
            } else {
                val error = "移除贴纸包失败: ${response.code()}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 移除贴纸包异常", e)
            Result.failure(e)
        }
    }

    /**
     * 更改收藏表情包的排序
     * @param sortList List of Pair(id, sort) - sort越大排序越靠前
     */
    suspend fun sortStickerPacks(sortList: List<Pair<Long, Int>>): Result<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = tokenRepository.getTokenSync()
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "Token为空")
                return@withContext Result.failure(Exception("未登录"))
            }

            // 构建排序JSON数组字符串
            val jsonArray = JSONArray()
            sortList.forEach { (id, sort) ->
                val jsonObject = JSONObject()
                jsonObject.put("id", id.toString())
                jsonObject.put("sort", sort.toString())
                jsonArray.put(jsonObject)
            }
            val sortString = jsonArray.toString()
            
            val request = StickerPackSortRequest(sort = sortString)
            val response = apiService.sortStickerPacks(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Log.d(TAG, "✅ 贴纸包排序成功")
                    Result.success(true)
                } else {
                    val error = body?.message ?: "贴纸包排序失败: ${response.code()}"
                    Log.e(TAG, error)
                    Result.failure(Exception(error))
                }
            } else {
                val error = "贴纸包排序失败: ${response.code()}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 贴纸包排序异常", e)
            Result.failure(e)
        }
    }

    /**
     * 创建表情包
     */
    suspend fun createStickerPack(name: String): Result<Long> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = tokenRepository.getTokenSync()
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "Token为空")
                return@withContext Result.failure(Exception("未登录"))
            }

            val request = com.yhchat.canary.data.model.CreateStickerPackRequest(name = name)
            val response = apiService.createStickerPack(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1 && body.data != null) {
                    Log.d(TAG, "✅ 创建表情包成功: id=${body.data.id}")
                    Result.success(body.data.id)
                } else {
                    val error = body?.msg ?: "创建表情包失败: ${response.code()}"
                    Log.e(TAG, error)
                    Result.failure(Exception(error))
                }
            } else {
                val error = "创建表情包失败: ${response.code()}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 创建表情包异常", e)
            Result.failure(e)
        }
    }

    /**
     * 重命名表情包
     */
    suspend fun renameStickerPack(packId: Long, newName: String): Result<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = tokenRepository.getTokenSync() ?: return@withContext Result.failure(Exception("未登录"))
            val response = apiService.renameStickerPack(token, RenameStickerPackRequest(id = packId, name = newName))
            if (response.isSuccessful && response.body()?.code == 1) {
                Log.d(TAG, "✅ 重命名表情包成功: $newName")
                Result.success(true)
            } else {
                val error = response.body()?.message ?: "重命名表情包失败: ${response.code()}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 重命名表情包异常", e)
            Result.failure(e)
        }
    }

    /**
     * 删除表情包
     */
    suspend fun deleteStickerPack(packId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = tokenRepository.getTokenSync() ?: return@withContext Result.failure(Exception("未登录"))
            val response = apiService.deleteStickerPack(token, StickerPackActionRequest(id = packId))
            if (response.isSuccessful && response.body()?.code == 1) {
                Log.d(TAG, "✅ 删除表情包成功: id=$packId")
                Result.success(true)
            } else {
                val error = response.body()?.message ?: "删除表情包失败: ${response.code()}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 删除表情包异常", e)
            Result.failure(e)
        }
    }

    /**
     * 重命名表情包里的表情
     */
    suspend fun renameSticker(stickerId: Long, newName: String): Result<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = tokenRepository.getTokenSync() ?: return@withContext Result.failure(Exception("未登录"))
            val response = apiService.renameSticker(token, RenameStickerRequest(id = stickerId, name = newName))
            if (response.isSuccessful && response.body()?.code == 1) {
                Log.d(TAG, "✅ 重命名表情成功: id=$stickerId, name=$newName")
                Result.success(true)
            } else {
                val error = response.body()?.message ?: "重命名表情失败: ${response.code()}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 重命名表情异常", e)
            Result.failure(e)
        }
    }

    /**
     * 删除表情
     */
    suspend fun removeSticker(stickerId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = tokenRepository.getTokenSync() ?: return@withContext Result.failure(Exception("未登录"))
            val response = apiService.removeSticker(token, StickerPackActionRequest(id = stickerId))
            if (response.isSuccessful && response.body()?.code == 1) {
                Log.d(TAG, "✅ 删除表情成功: id=$stickerId")
                Result.success(true)
            } else {
                val error = response.body()?.message ?: "删除表情失败: ${response.code()}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 删除表情异常", e)
            Result.failure(e)
        }
    }

    /**
     * 获取表情专用上传 token
     */
    suspend fun getQiniuStickerToken(): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = tokenRepository.getTokenSync()
            if (token.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("未登录"))
            }
            var response = apiService.getQiniuStickerToken(token)
            if (!response.isSuccessful || response.body()?.code != 1) {
                response = apiService.getQiniuExpressionToken(token)
            }
            if (!response.isSuccessful || response.body()?.code != 1) {
                response = apiService.getQiniuImageToken(token)
            }
            val body = response.body()
            if (response.isSuccessful && body != null && body.code == 1) {
                Result.success(body.data.token)
            } else {
                Result.failure(Exception("获取上传Token失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 获取表情上传token异常", e)
            Result.failure(e)
        }
    }

    /**
     * 上传图片并添加表情到指定表情包
     */
    suspend fun uploadAndAddSticker(
        context: Context,
        imageUri: Uri,
        packId: Long,
        stickerName: String? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = tokenRepository.getTokenSync()
            if (token.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("未登录"))
            }

            // 1. 获取表情上传 Token
            val tokenResult = getQiniuStickerToken()
            val uploadToken = tokenResult.getOrNull()
                ?: return@withContext Result.failure(tokenResult.exceptionOrNull() ?: Exception("获取上传凭证失败"))

            // 2. 读取文件并计算 MD5
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(imageUri) ?: "image/png"
            val extension = when {
                mimeType.contains("webp") -> "webp"
                mimeType.contains("gif") -> "gif"
                mimeType.contains("jpeg") || mimeType.contains("jpg") -> "jpg"
                mimeType.contains("bmp") -> "bmp"
                else -> "png"
            }

            val tempFile = File(context.cacheDir, "sticker_upload_${System.currentTimeMillis()}.$extension")
            val md5 = contentResolver.openInputStream(imageUri)?.use { input ->
                val md = MessageDigest.getInstance("MD5")
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        md.update(buffer, 0, bytesRead)
                    }
                }
                md.digest().joinToString("") { "%02x".format(it) }
            } ?: return@withContext Result.failure(Exception("读取图片文件失败"))

            val fileKey = "sticker/$md5.$extension"
            Log.d(TAG, "📤 表情上传 key: $fileKey, file: ${tempFile.absolutePath}")

            // 3. 上传到七牛云
            val ak = uploadToken.split(":")[0]
            val queryUrl = "https://api.qiniu.com/v4/query?ak=$ak&bucket=chat68"
            val client = OkHttpClient.Builder()
                .connectTimeout(45, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .writeTimeout(45, TimeUnit.SECONDS)
                .build()

            val uploadHost = try {
                val queryReq = Request.Builder().url(queryUrl).get().build()
                client.newCall(queryReq).execute().use { queryResp ->
                    if (queryResp.isSuccessful) {
                        val queryJson = JSONObject(queryResp.body?.string() ?: "{}")
                        queryJson.getJSONArray("hosts").getJSONObject(0).getJSONObject("up").getJSONArray("domains").getString(0)
                    } else "upload-z2.qiniup.com"
                }
            } catch (e: Exception) {
                "upload-z2.qiniup.com"
            }

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("token", uploadToken)
                .addFormDataPart("key", fileKey)
                .addFormDataPart("file", "$md5.$extension", tempFile.asRequestBody(mimeType.toMediaTypeOrNull()))
                .build()

            val uploadReq = Request.Builder()
                .url("https://$uploadHost/")
                .post(requestBody)
                .build()

            val uploadSuccess = client.newCall(uploadReq).execute().use { resp ->
                resp.isSuccessful
            }
            tempFile.delete()

            if (!uploadSuccess) {
                return@withContext Result.failure(Exception("七牛云上传图片失败"))
            }

            // 4. 调用 POST /v1/sticker/add-sticker 添加到表情包
            val nameToUse = stickerName?.takeIf { it.isNotBlank() } ?: (System.currentTimeMillis() / 1000).toString()
            val addReq = AddStickerRequest(
                name = nameToUse,
                url = fileKey,
                stickerPackId = packId
            )
            val addResp = apiService.addSticker(token, addReq)
            if (addResp.isSuccessful && addResp.body()?.code == 1) {
                Log.d(TAG, "✅ 成功添加表情至表情包: $fileKey")
                Result.success(true)
            } else {
                val errorMsg = addResp.body()?.message ?: "添加表情至表情包失败: ${addResp.code()}"
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 上传并添加表情异常", e)
            Result.failure(e)
        }
    }
}
