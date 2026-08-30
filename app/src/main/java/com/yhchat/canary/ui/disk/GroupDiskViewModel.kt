package com.yhchat.canary.ui.disk

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhchat.canary.data.di.RepositoryFactory
import com.yhchat.canary.data.model.DiskFile
import com.yhchat.canary.data.repository.DiskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GroupDiskViewModel : ViewModel() {
    private lateinit var diskRepository: DiskRepository
    private lateinit var apiService: com.yhchat.canary.data.api.ApiService

    private val _uiState = MutableStateFlow(GroupDiskUiState())
    val uiState: StateFlow<GroupDiskUiState> = _uiState.asStateFlow()

    fun init(context: Context) {
        diskRepository = RepositoryFactory.getDiskRepository(context)
        apiService = RepositoryFactory.apiService
    }

    fun loadFiles(groupId: String, folderId: Long = 0) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            diskRepository.getFileList(
                chatId = groupId,
                chatType = 2,
                folderId = folderId
            ).fold(
                onSuccess = { files ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        files = files,
                        currentFolderId = folderId
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message
                    )
                }
            )
        }
    }

    fun createFolder(groupId: String, folderName: String) {
        viewModelScope.launch {
            diskRepository.createFolder(
                chatId = groupId,
                chatType = 2,
                folderName = folderName,
                parentFolderId = uiState.value.currentFolderId
            ).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(operationSuccess = true)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(error = error.message)
                }
            )
        }
    }

    fun uploadFile(context: Context, fileUri: Uri, groupId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true, error = null)

            try {
                val tag = "GroupDiskUpload"
                android.util.Log.d(tag, "📤 ========== 开始群网盘文件上传 ==========")
                android.util.Log.d(tag, "📤 群组ID: $groupId")
                android.util.Log.d(tag, "📤 当前文件夹ID: ${_uiState.value.currentFolderId}")

                val tokenRepo = RepositoryFactory.getTokenRepository(context)
                val userToken = tokenRepo.getTokenSync()
                if (userToken.isNullOrEmpty()) {
                    throw Exception("用户未登录")
                }

                android.util.Log.d(tag, "📤 获取七牛上传token...")
                val tokenResponse = apiService.getQiniuFileToken(userToken)
                if (!tokenResponse.isSuccessful || tokenResponse.body()?.code != 1) {
                    throw Exception("获取上传token失败: ${tokenResponse.body()?.msg}")
                }

                val qiniuToken = tokenResponse.body()?.data?.token
                    ?: throw Exception("上传token为空")
                android.util.Log.d(tag, "✅ 七牛Token获取成功")

                val fileName = getFileNameFromUri(context, fileUri) ?: "未命名文件"
                android.util.Log.d(tag, "📤 文件名: $fileName")

                android.util.Log.d(tag, "📤 开始上传文件到七牛云...")
                val uploadResult = com.yhchat.canary.utils.FileUploadUtil.uploadFile(
                    context = context,
                    fileUri = fileUri,
                    uploadToken = qiniuToken
                )

                uploadResult.fold(
                    onSuccess = { uploadResponse ->
                        android.util.Log.d(tag, "✅ 七牛云上传成功！")
                        android.util.Log.d(tag, "   key: ${uploadResponse.key}")
                        android.util.Log.d(tag, "   hash: ${uploadResponse.hash}")
                        android.util.Log.d(tag, "   size: ${uploadResponse.fsize}")

                        val fileMd5 = uploadResponse.key.substringAfter("disk/").substringBefore(".")
                        android.util.Log.d(tag, "✅ 文件MD5: $fileMd5")

                        android.util.Log.d(tag, "📤 记录文件上传信息...")
                        val uploadFileRequest = com.yhchat.canary.data.model.UploadFileRequest(
                            chatId = groupId,
                            chatType = 2,
                            fileSize = uploadResponse.fsize,
                            fileName = fileName,
                            fileMd5 = fileMd5,
                            fileEtag = uploadResponse.hash,
                            qiniuKey = uploadResponse.key,
                            folderId = _uiState.value.currentFolderId
                        )

                        val recordResponse = apiService.uploadFileToDisk(userToken, uploadFileRequest)
                        if (recordResponse.isSuccessful && recordResponse.body()?.code == 1) {
                            android.util.Log.d(tag, "✅ 文件上传记录成功！")
                            android.util.Log.d(tag, "✅ ========== 群网盘文件上传完成 ==========")

                            _uiState.value = _uiState.value.copy(
                                isUploading = false,
                                operationSuccess = true
                            )
                        } else {
                            android.util.Log.e(tag, "❌ 文件上传记录失败: ${recordResponse.body()?.message}")
                            throw Exception("文件上传记录失败: ${recordResponse.body()?.message}")
                        }
                    },
                    onFailure = { error ->
                        android.util.Log.e(tag, "❌ 七牛云上传失败", error)
                        throw error
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("GroupDiskUpload", "❌ 上传失败", e)
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    error = "上传失败: ${e.message}"
                )
            }
        }
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var fileName: String? = null

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
            android.util.Log.w("GroupDiskUpload", "⚠️ 无法从ContentResolver获取文件名", e)
        }

        if (fileName == null) {
            uri.lastPathSegment?.let { segment ->
                fileName = if (segment.contains('/')) {
                    segment.substringAfterLast('/')
                } else if (segment.contains(':')) {
                    segment.substringAfterLast(':')
                } else {
                    segment
                }
            }
        }

        return fileName
    }

    fun renameFile(fileId: Long, objectType: Int, newName: String) {
        viewModelScope.launch {
            diskRepository.renameFile(fileId, objectType, newName).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(operationSuccess = true)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(error = error.message)
                }
            )
        }
    }

    fun deleteFile(fileId: Long, objectType: Int) {
        viewModelScope.launch {
            diskRepository.removeFile(fileId, objectType).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(operationSuccess = true)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(error = error.message)
                }
            )
        }
    }

    fun resetOperationSuccess() {
        _uiState.value = _uiState.value.copy(operationSuccess = false)
    }

    fun enterFolder(folderId: Long, folderName: String) {
        val newBreadcrumbs = _uiState.value.breadcrumbs.toMutableList()
        newBreadcrumbs.add(Breadcrumb(folderId, folderName))

        _uiState.value = _uiState.value.copy(
            currentFolderId = folderId,
            breadcrumbs = newBreadcrumbs
        )
    }

    fun navigateToFolder(folderId: Long) {
        if (folderId == 0L) {
            _uiState.value = _uiState.value.copy(
                currentFolderId = 0,
                breadcrumbs = emptyList()
            )
        } else {
            val breadcrumbIndex = _uiState.value.breadcrumbs.indexOfFirst { it.id == folderId }
            if (breadcrumbIndex != -1) {
                val newBreadcrumbs = _uiState.value.breadcrumbs.take(breadcrumbIndex + 1)
                _uiState.value = _uiState.value.copy(
                    currentFolderId = folderId,
                    breadcrumbs = newBreadcrumbs
                )
            }
        }
    }
}

data class Breadcrumb(
    val id: Long,
    val name: String
)

data class GroupDiskUiState(
    val isLoading: Boolean = false,
    val isUploading: Boolean = false,
    val files: List<DiskFile> = emptyList(),
    val currentFolderId: Long = 0,
    val breadcrumbs: List<Breadcrumb> = emptyList(),
    val operationSuccess: Boolean = false,
    val error: String? = null
)
