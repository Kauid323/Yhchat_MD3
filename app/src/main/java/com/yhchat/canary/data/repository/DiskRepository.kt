package com.yhchat.canary.data.repository

import android.util.Log
import com.yhchat.canary.data.api.ApiService
import com.yhchat.canary.data.model.CreateFolderRequest
import com.yhchat.canary.data.model.DiskFile
import com.yhchat.canary.data.model.GetFileListRequest
import com.yhchat.canary.data.model.RemoveFileRequest
import com.yhchat.canary.data.model.RenameFileRequest
import com.yhchat.canary.data.model.UploadFileRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 云盘仓库
 */
@Singleton
class DiskRepository @Inject constructor(
    private val apiService: ApiService,
    private val tokenRepository: TokenRepository
) {
    companion object {
        private const val TAG = "DiskRepository"
    }

    /**
     * 创建文件夹
     */
    suspend fun createFolder(
        chatId: String,
        chatType: Int,
        folderName: String,
        parentFolderId: Long = 0
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = tokenRepository.getTokenSync()
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "Token为空")
                return@withContext Result.failure(Exception("未登录"))
            }

            val request = CreateFolderRequest(
                chatId = chatId,
                chatType = chatType,
                folderName = folderName,
                parentFolderId = parentFolderId
            )
            
            val response = apiService.createFolder(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Result.success(true)
                } else {
                    val error = "创建文件夹失败: ${body?.message ?: "未知错误"}"
                    Result.failure(Exception(error))
                }
            } else {
                val error = "创建文件夹失败: ${response.code()}"
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 获取文件列表
     */
    suspend fun getFileList(
        chatId: String,
        chatType: Int,
        folderId: Long = 0,
        sort: String = "name_asc"
    ): Result<List<DiskFile>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = tokenRepository.getTokenSync()
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "Token为空")
                return@withContext Result.failure(Exception("未登录"))
            }

            val request = GetFileListRequest(
                chatId = chatId,
                chatType = chatType,
                folderId = folderId,
                sort = sort
            )
            
            val response = apiService.getFileList(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Result.success(body.data.list)
                } else {
                    val error = "获取文件列表失败: ${response.code()}"
                    Log.e(TAG, error)
                    Result.failure(Exception(error))
                }
            } else {
                val error = "获取文件列表失败: ${response.code()}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 获取文件列表异常", e)
            Result.failure(e)
        }
    }

    /**
     * 上传文件到云盘
     */
    suspend fun uploadFile(
        chatId: String,
        chatType: Int,
        fileSize: Long,
        fileName: String,
        fileMd5: String,
        fileEtag: String,
        qiniuKey: String,
        folderId: Long = 0
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = tokenRepository.getTokenSync()
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "Token为空")
                return@withContext Result.failure(Exception("未登录"))
            }

            val request = UploadFileRequest(
                chatId = chatId,
                chatType = chatType,
                fileSize = fileSize,
                fileName = fileName,
                fileMd5 = fileMd5,
                fileEtag = fileEtag,
                qiniuKey = qiniuKey,
                folderId = folderId
            )
            
            val response = apiService.uploadFileToDisk(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Result.success(true)
                } else {
                    val error = "文件上传失败: ${body?.message ?: "未知错误"}"
                    Log.e(TAG, error)
                    Result.failure(Exception(error))
                }
            } else {
                val error = "文件上传失败: ${response.code()}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 文件上传异常", e)
            Result.failure(e)
        }
    }

    /**
     * 重命名文件
     */
    suspend fun renameFile(
        fileId: Long,
        objectType: Int,
        newName: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = tokenRepository.getTokenSync()
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "Token为空")
                return@withContext Result.failure(Exception("未登录"))
            }

            val request = RenameFileRequest(
                id = fileId,
                objectType = objectType,
                name = newName
            )
            
            val response = apiService.renameFile(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Result.success(true)
                } else {
                    val error = "重命名文件失败: ${body?.message ?: "未知错误"}"
                    Log.e(TAG, error)
                    Result.failure(Exception(error))
                }
            } else {
                val error = "重命名文件失败: ${response.code()}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 重命名文件异常", e)
            Result.failure(e)
        }
    }

    /**
     * 删除文件
     */
    suspend fun removeFile(
        fileId: Long,
        objectType: Int
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = tokenRepository.getTokenSync()
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "Token为空")
                return@withContext Result.failure(Exception("未登录"))
            }

            val request = RemoveFileRequest(
                id = fileId,
                objectType = objectType
            )
            
            val response = apiService.removeFile(token, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 1) {
                    Result.success(true)
                } else {
                    val error = "删除文件失败: ${body?.message ?: "未知错误"}"
                    Log.e(TAG, error)
                    Result.failure(Exception(error))
                }
            } else {
                val error = "删除文件失败: ${response.code()}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 删除文件异常", e)
            Result.failure(e)
        }
    }
}

