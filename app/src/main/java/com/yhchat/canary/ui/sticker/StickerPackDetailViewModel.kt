package com.yhchat.canary.ui.sticker

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhchat.canary.data.model.StickerItem
import com.yhchat.canary.data.model.StickerPackDetailData
import com.yhchat.canary.data.repository.StickerRepository
import com.yhchat.canary.data.repository.TokenRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StickerPackDetailUiState(
    val isLoading: Boolean = false,
    val isUploading: Boolean = false,
    val error: String? = null,
    val stickerPack: StickerPackDetailData? = null,
    val currentUserId: String? = null,
    val isOwner: Boolean = false,
    val isManaging: Boolean = false,
    val showImageViewer: Boolean = false,
    val currentImageIndex: Int = 0,
    val selectedStickerForAction: StickerItem? = null,
    val showStickerActionSheet: Boolean = false
)

@HiltViewModel
class StickerPackDetailViewModel @Inject constructor(
    private val stickerRepository: StickerRepository,
    private val tokenRepository: TokenRepository
) : ViewModel() {

    private val tag = "StickerPackDetailVM"

    private val _uiState = MutableStateFlow(StickerPackDetailUiState())
    val uiState: StateFlow<StickerPackDetailUiState> = _uiState.asStateFlow()

    /**
     * 加载表情包详情并判断是否为当前用户创建
     */
    fun loadStickerPackDetail(stickerPackId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                val currentUid = tokenRepository.getUserIdSync()
                val result = stickerRepository.getStickerPackDetail(stickerPackId.toLong())
                result.fold(
                    onSuccess = { stickerPackData ->
                        val creatorId = stickerPackData.user?.userId ?: stickerPackData.stickerPack.createBy
                        val isOwner = !currentUid.isNullOrEmpty() && currentUid == creatorId
                        Log.d(tag, "Successfully loaded sticker pack: ${stickerPackData.stickerPack.name}, currentUid=$currentUid, creatorId=$creatorId, isOwner=$isOwner")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            stickerPack = stickerPackData,
                            currentUserId = currentUid,
                            isOwner = isOwner,
                            error = null
                        )
                    },
                    onFailure = { error ->
                        Log.e(tag, "Failed to load sticker pack", error)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = error.message ?: "加载失败"
                        )
                    }
                )
            } catch (e: Exception) {
                Log.e(tag, "Exception loading sticker pack", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "加载失败"
                )
            }
        }
    }

    /**
     * 添加表情包到收藏
     */
    fun addStickerPackToFavorites(
        stickerPackId: String,
        onSuccess: (() -> Unit)? = null,
        onFailure: ((String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            try {
                val result = stickerRepository.addStickerPack(stickerPackId.toLong())
                result.fold(
                    onSuccess = {
                        Log.d(tag, "Successfully added sticker pack: $stickerPackId")
                        onSuccess?.invoke()
                    },
                    onFailure = { error ->
                        Log.e(tag, "Failed to add sticker pack", error)
                        val msg = error.message ?: "添加失败"
                        _uiState.value = _uiState.value.copy(error = msg)
                        onFailure?.invoke(msg)
                    }
                )
            } catch (e: Exception) {
                Log.e(tag, "Exception adding sticker pack", e)
                val msg = e.message ?: "添加异常"
                _uiState.value = _uiState.value.copy(error = msg)
                onFailure?.invoke(msg)
            }
        }
    }

    /**
     * 切换管理模式
     */
    fun toggleManageMode(managing: Boolean? = null) {
        _uiState.value = _uiState.value.copy(
            isManaging = managing ?: !_uiState.value.isManaging,
            showStickerActionSheet = false,
            selectedStickerForAction = null
        )
    }

    /**
     * 重命名表情包
     */
    fun renameStickerPack(
        packId: Long,
        newName: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch {
            stickerRepository.renameStickerPack(packId, newName).fold(
                onSuccess = {
                    loadStickerPackDetail(packId.toString())
                    onSuccess()
                },
                onFailure = { error ->
                    onFailure(error.message ?: "重命名失败")
                }
            )
        }
    }

    /**
     * 删除表情包
     */
    fun deleteStickerPack(
        packId: Long,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch {
            stickerRepository.deleteStickerPack(packId).fold(
                onSuccess = {
                    onSuccess()
                },
                onFailure = { error ->
                    onFailure(error.message ?: "删除失败")
                }
            )
        }
    }

    /**
     * 上传并添加表情
     */
    fun uploadAndAddSticker(
        context: Context,
        imageUri: Uri,
        stickerPackId: String,
        stickerName: String? = null,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true)
            stickerRepository.uploadAndAddSticker(
                context = context,
                imageUri = imageUri,
                packId = stickerPackId.toLong(),
                stickerName = stickerName
            ).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(isUploading = false)
                    loadStickerPackDetail(stickerPackId)
                    onSuccess()
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(isUploading = false)
                    onFailure(error.message ?: "添加表情失败")
                }
            )
        }
    }

    /**
     * 选择单个表情触发操作菜单
     */
    fun selectStickerForAction(sticker: StickerItem) {
        _uiState.value = _uiState.value.copy(
            selectedStickerForAction = sticker,
            showStickerActionSheet = true
        )
    }

    /**
     * 关闭表情操作菜单
     */
    fun dismissStickerActionSheet() {
        _uiState.value = _uiState.value.copy(
            showStickerActionSheet = false,
            selectedStickerForAction = null
        )
    }

    /**
     * 重命名单个表情
     */
    fun renameSticker(
        stickerId: Long,
        newName: String,
        stickerPackId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch {
            stickerRepository.renameSticker(stickerId, newName).fold(
                onSuccess = {
                    dismissStickerActionSheet()
                    loadStickerPackDetail(stickerPackId)
                    onSuccess()
                },
                onFailure = { error ->
                    onFailure(error.message ?: "重命名表情失败")
                }
            )
        }
    }

    /**
     * 删除单个表情
     */
    fun removeSticker(
        stickerId: Long,
        stickerPackId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch {
            stickerRepository.removeSticker(stickerId).fold(
                onSuccess = {
                    dismissStickerActionSheet()
                    loadStickerPackDetail(stickerPackId)
                    onSuccess()
                },
                onFailure = { error ->
                    onFailure(error.message ?: "删除表情失败")
                }
            )
        }
    }

    fun openImageViewer(index: Int) {
        _uiState.value = _uiState.value.copy(
            showImageViewer = true,
            currentImageIndex = index
        )
    }

    fun dismissImageViewer() {
        _uiState.value = _uiState.value.copy(
            showImageViewer = false,
            currentImageIndex = 0
        )
    }
}
