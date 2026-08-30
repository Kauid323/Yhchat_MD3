package com.yhchat.canary.ui.sticker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yhchat.canary.data.model.StickerPack
import com.yhchat.canary.data.repository.StickerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StickerPackManagerUiState(
    val isLoading: Boolean = true,
    val isSavingOrder: Boolean = false,
    val stickerPacks: List<StickerPack> = emptyList(),
    val error: String? = null
)

class StickerPackManagerViewModel(
    private val repository: StickerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StickerPackManagerUiState())
    val uiState: StateFlow<StickerPackManagerUiState> = _uiState.asStateFlow()

    fun loadStickerPacks() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.getStickerPackList().fold(
                onSuccess = { packs ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            stickerPacks = packs.sortedByDescending { pack -> pack.sort },
                            error = null
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "加载失败"
                        )
                    }
                }
            )
        }
    }

    fun removeStickerPack(packId: Long, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        viewModelScope.launch {
            repository.removeStickerPack(packId).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(stickerPacks = it.stickerPacks.filterNot { pack -> pack.id == packId })
                    }
                    syncSortOrder(
                        onSuccess = onSuccess,
                        onFailure = onFailure
                    )
                },
                onFailure = { error ->
                    onFailure(error.message ?: "移除失败")
                }
            )
        }
    }

    fun moveStickerPack(fromIndex: Int, toIndex: Int) {
        _uiState.update { state ->
            val list = state.stickerPacks.toMutableList()
            if (fromIndex !in list.indices || toIndex !in list.indices) {
                return@update state
            }
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            state.copy(stickerPacks = list)
        }
    }

    fun syncSortOrder(onSuccess: (() -> Unit)? = null, onFailure: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            val currentPacks = uiState.value.stickerPacks
            if (currentPacks.isEmpty()) return@launch

            _uiState.update { it.copy(isSavingOrder = true, error = null) }
            val sortList = currentPacks.mapIndexed { index, pack ->
                pack.id to (currentPacks.size - index)
            }

            repository.sortStickerPacks(sortList).fold(
                onSuccess = {
                    _uiState.update { it.copy(isSavingOrder = false) }
                    onSuccess?.invoke()
                },
                onFailure = { error ->
                    val message = error.message ?: "排序失败"
                    _uiState.update { it.copy(isSavingOrder = false, error = message) }
                    onFailure?.invoke(message)
                    loadStickerPacks()
                }
            )
        }
    }

    fun createStickerPack(name: String, onSuccess: (Long) -> Unit, onFailure: (String) -> Unit) {
        viewModelScope.launch {
            repository.createStickerPack(name).fold(
                onSuccess = { packId ->
                    loadStickerPacks()
                    onSuccess(packId)
                },
                onFailure = { error ->
                    onFailure(error.message ?: "创建失败")
                }
            )
        }
    }

    companion object {
        fun factory(repository: StickerRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return StickerPackManagerViewModel(repository) as T
                }
            }
    }
}
