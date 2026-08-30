package com.yhchat.canary.ui.disk.webdav

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhchat.canary.data.api.ApiClient
import com.yhchat.canary.data.di.RepositoryFactory
import com.yhchat.canary.data.model.MountSetting
import com.yhchat.canary.data.model.WebDAVFile
import com.yhchat.canary.utils.RSAEncryptionUtil
import com.yhchat.canary.utils.SardineWebDAVClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WebDAVBrowserViewModel : ViewModel() {
    private lateinit var apiService: com.yhchat.canary.data.api.ApiService
    private lateinit var tokenRepository: com.yhchat.canary.data.repository.TokenRepository

    private val _uiState = MutableStateFlow(WebDAVBrowserUiState())
    val uiState: StateFlow<WebDAVBrowserUiState> = _uiState.asStateFlow()

    fun init(context: Context) {
        apiService = ApiClient.apiService
        tokenRepository = RepositoryFactory.getTokenRepository(context)
    }

    fun loadMountSettings(groupId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val encryptionResult = RSAEncryptionUtil.prepareEncryptionParams()
                if (encryptionResult == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "准备加密参数失败"
                    )
                    return@launch
                }

                val (encryptKey, encryptIv, rawKeyPair) = encryptionResult

                val userToken = tokenRepository.getTokenSync()
                if (userToken.isNullOrEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "用户未登录"
                    )
                    return@launch
                }

                val request = com.yhchat.canary.data.model.MountSettingRequest(
                    groupId = groupId,
                    encryptKey = encryptKey,
                    encryptIv = encryptIv
                )

                val response = apiService.getMountSettingList(userToken, request)

                if (response.isSuccessful && response.body()?.code == 1) {
                    val encryptedMountSettings = response.body()?.data?.list ?: emptyList()

                    val decryptedMountSettings = mutableListOf<MountSetting>()
                    for (mountSetting in encryptedMountSettings) {
                        try {
                            val decryptedPassword = RSAEncryptionUtil.decryptWebDAVPassword(
                                mountSetting.webdavPassword,
                                rawKeyPair.first,
                                rawKeyPair.second
                            ) ?: ""

                            val decryptedMountSetting = mountSetting.copy(
                                webdavPassword = decryptedPassword
                            )
                            decryptedMountSettings.add(decryptedMountSetting)

                            Log.d("WebDAVBrowser", "挂载点 ${mountSetting.mountName} 密码解密成功")
                        } catch (e: Exception) {
                            Log.e("WebDAVBrowser", "挂载点 ${mountSetting.mountName} 密码解密失败", e)
                            decryptedMountSettings.add(mountSetting.copy(webdavPassword = ""))
                        }
                    }

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        mountSettings = decryptedMountSettings,
                        selectedMountIndex = if (decryptedMountSettings.isNotEmpty()) 0 else -1
                    )

                    if (decryptedMountSettings.isNotEmpty()) {
                        val firstMount = decryptedMountSettings[0]
                        loadFiles(firstMount, "")
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = response.body()?.msg ?: "获取挂载点列表失败"
                    )
                }
            } catch (e: Exception) {
                Log.e("WebDAVBrowser", "加载挂载点失败", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "加载失败: ${e.message}"
                )
            }
        }
    }

    fun selectMount(index: Int) {
        if (index in _uiState.value.mountSettings.indices) {
            val selectedMount = _uiState.value.mountSettings[index]

            _uiState.value = _uiState.value.copy(
                selectedMountIndex = index
            )

            val mountState = _uiState.value.mountStates[index]
            if (mountState == null) {
                loadFiles(selectedMount, "")
            }
        }
    }

    fun loadFiles(mountSetting: MountSetting, path: String = "") {
        viewModelScope.launch {
            val mountIndex = _uiState.value.selectedMountIndex

            val currentMountStates = _uiState.value.mountStates.toMutableMap()
            currentMountStates[mountIndex] = currentMountStates[mountIndex]?.copy(
                isLoading = true,
                error = null
            ) ?: MountState(isLoading = true)

            _uiState.value = _uiState.value.copy(
                mountStates = currentMountStates,
                isLoadingFiles = true
            )

            val result = SardineWebDAVClient.listFiles(mountSetting, path)
            result.onSuccess { files ->
                val updatedMountStates = _uiState.value.mountStates.toMutableMap()
                updatedMountStates[mountIndex] = MountState(
                    files = files,
                    currentPath = path,
                    isLoading = false,
                    error = null
                )

                _uiState.value = _uiState.value.copy(
                    mountStates = updatedMountStates,
                    isLoadingFiles = false,
                    files = files,
                    currentPath = path
                )
            }.onFailure { error ->
                val updatedMountStates = _uiState.value.mountStates.toMutableMap()
                updatedMountStates[mountIndex] = updatedMountStates[mountIndex]?.copy(
                    isLoading = false,
                    error = "加载文件失败: ${error.message}"
                ) ?: MountState(
                    isLoading = false,
                    error = "加载文件失败: ${error.message}"
                )

                _uiState.value = _uiState.value.copy(
                    mountStates = updatedMountStates,
                    isLoadingFiles = false
                )
            }
        }
    }

    fun enterFolder(relativePath: String) {
        val selectedMount = _uiState.value.mountSettings.getOrNull(_uiState.value.selectedMountIndex)
        if (selectedMount != null) {
            Log.d("WebDAVBrowser", "enterFolder: relativePath=$relativePath")
            loadFiles(selectedMount, relativePath)
        }
    }

    fun navigateToPath(targetPath: String) {
        val selectedMount = _uiState.value.mountSettings.getOrNull(_uiState.value.selectedMountIndex)
        if (selectedMount != null) {
            loadFiles(selectedMount, targetPath)
        }
    }

    fun navigateUp(): Boolean {
        val selectedIndex = _uiState.value.selectedMountIndex
        val mountState = _uiState.value.mountStates[selectedIndex] ?: return false
        val currentPath = mountState.currentPath
        if (currentPath.isBlank()) return false
        val parentPath = currentPath.substringBeforeLast('/', "")
        val selectedMount = _uiState.value.mountSettings.getOrNull(selectedIndex) ?: return false
        loadFiles(selectedMount, parentPath)
        return true
    }
}

data class WebDAVBrowserUiState(
    val isLoading: Boolean = false,
    val isLoadingFiles: Boolean = false,
    val mountSettings: List<MountSetting> = emptyList(),
    val selectedMountIndex: Int = -1,
    val files: List<WebDAVFile> = emptyList(),
    val currentPath: String = "",
    val error: String? = null,
    val mountStates: Map<Int, MountState> = emptyMap()
)

data class MountState(
    val files: List<WebDAVFile> = emptyList(),
    val currentPath: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)
