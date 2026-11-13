package com.yhchat.canary.ui.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhchat.canary.data.model.BaMember
import com.yhchat.canary.data.model.CommunityBoard
import com.yhchat.canary.data.repository.CommunityRepository
import com.yhchat.canary.data.repository.TokenRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 分区信息页的状态。
 *
 * 和 [BoardDetailState] 的区别：那个只关心文章流需要的分区字段，
 * 这里要额外带上创建者和管理者列表，以及「我是不是管理员」这个判定结果。
 */
data class BoardInfoState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val board: CommunityBoard? = null,
    val creator: BaMember? = null,
    val managers: List<BaMember> = emptyList(),
    val currentUserId: String = ""
) {
    /**
     * 当前用户能不能管理这个分区。
     *
     * 判定依据是「我的 userId 出现在 baCreater 或 baMangers 里」。
     * 没有用 `ba.isAdmin` 那个字段 —— 它的取值含义后端没公开，
     * 用 id 比对是唯一能自己验证的方式。
     */
    val canManage: Boolean
        get() {
            val me = currentUserId
            if (me.isBlank()) return false
            return creator?.userId == me || managers.any { it.userId == me }
        }

    /** 创建者也算管理员的一种，界面上要分开标注 */
    fun isCreator(userId: String): Boolean = creator?.userId == userId

    fun isManager(userId: String): Boolean =
        managers.any { it.userId == userId }
}

@HiltViewModel
class BoardInfoViewModel @Inject constructor(
    private val communityRepository: CommunityRepository,
    private val tokenRepository: TokenRepository
) : ViewModel() {

    private val _state = MutableStateFlow(BoardInfoState())
    val state: StateFlow<BoardInfoState> = _state.asStateFlow()

    /**
     * 拉分区信息。
     *
     * currentUserId 每次刷新都重新读一次：账号切换后如果沿用旧值，
     * 管理按钮会出现在不该出现的账号上。
     */
    fun load(token: String, boardId: Int) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            val myId = runCatching { tokenRepository.getUserIdSync() }.getOrNull().orEmpty()

            communityRepository.getBoardInfo(token, boardId)
                .onSuccess { response ->
                    val data = response.data
                    _state.value = BoardInfoState(
                        isLoading = false,
                        error = null,
                        board = data.board,
                        creator = data.creator,
                        managers = data.managers,
                        currentUserId = myId
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = error.message ?: "加载分区信息失败"
                    )
                }
        }
    }

    /** 管理弹窗里改完名字/头像后，把新数据同步回来，不用整页重拉 */
    fun updateBoard(board: CommunityBoard) {
        _state.value = _state.value.copy(board = board)
    }
}
