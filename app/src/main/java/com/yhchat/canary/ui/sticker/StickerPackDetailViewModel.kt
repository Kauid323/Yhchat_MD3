package com.yhchat.canary.ui.sticker

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhchat.canary.data.model.StickerPackDetailData
import com.yhchat.canary.data.repository.StickerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StickerPackDetailUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val stickerPack: StickerPackDetailData? = null,
    val showImageViewer: Boolean = false,
    val currentImageIndex: Int = 0
)

@HiltViewModel
class StickerPackDetailViewModel @Inject constructor(
    private val stickerRepository: StickerRepository
) : ViewModel() {

    private val tag = "StickerPackDetailVM"

    private val _uiState = MutableStateFlow(StickerPackDetailUiState())
    val uiState: StateFlow<StickerPackDetailUiState> = _uiState.asStateFlow()

    /**
     * 加载表情包详情
     */
    fun loadStickerPackDetail(stickerPackId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                val result = stickerRepository.getStickerPackDetail(stickerPackId.toLong())
                result.fold(
                    onSuccess = { stickerPack ->
                        Log.d(tag, "Successfully loaded sticker pack: ${stickerPack.stickerPack.name}")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            stickerPack = stickerPack,
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
    fun addStickerPackToFavorites(stickerPackId: String) {
        viewModelScope.launch {
            try {
                val result = stickerRepository.addStickerPack(stickerPackId.toLong())
                result.fold(
                    onSuccess = {
                        Log.d(tag, "Successfully added sticker pack: $stickerPackId")
                        // 可以在这里显示成功提示
                    },
                    onFailure = { error ->
                        Log.e(tag, "Failed to add sticker pack", error)
                        _uiState.value = _uiState.value.copy(error = error.message)
                    }
                )
            } catch (e: Exception) {
                Log.e(tag, "Exception adding sticker pack", e)
                _uiState.value = _uiState.value.copy(error = e.message)
            }
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
