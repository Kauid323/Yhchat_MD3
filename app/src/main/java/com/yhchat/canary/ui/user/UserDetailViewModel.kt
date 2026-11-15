package com.yhchat.canary.ui.user

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhchat.canary.data.di.RepositoryFactory
import com.yhchat.canary.data.model.BoardsByCreateItem
import com.yhchat.canary.data.model.GroupMemberInfo
import com.yhchat.canary.data.model.RemarkExtraEntry
import com.yhchat.canary.data.model.RemarkInfo
import com.yhchat.canary.data.model.RemarkInfoJsonAdapter
import com.yhchat.canary.data.model.UserDetail
import com.yhchat.canary.data.repository.CacheRepository
import com.yhchat.canary.data.repository.CommunityRepository
import com.yhchat.canary.data.repository.FriendRepository
import com.yhchat.canary.data.repository.GroupRepository
import com.yhchat.canary.data.repository.TokenRepository
import com.yhchat.canary.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import com.yhchat.canary.proto.user.get_user_send

data class UserDetailUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val userDetail: UserDetail? = null,
    val createdBoards: List<BoardsByCreateItem> = emptyList(),
    val isLoadingBoards: Boolean = false,
    /** 作者创建的表情包（第三方目录服务，失败时为空，不影响页面其它部分） */
    val createdStickerPacks: List<com.yhchat.canary.data.model.StickerCatalogPack> = emptyList(),
    val isLoadingStickerPacks: Boolean = false,
    val token: String = "",
    val isInAddressBook: Boolean = false,
    val isCheckingAddressBook: Boolean = true,
    val isAddingFriend: Boolean = false,
    val isNoNotify: Boolean = false,
    val isSettingNoNotify: Boolean = false,
    val isProcessingMemberAction: Boolean = false,
    val targetUserPermission: Int = 0,
    val showAddFriendDialog: Boolean = false,
    val addFriendRemark: String = "",
    val showUserInfoDialog: Boolean = false,
    val showGagDialog: Boolean = false,
    val showMoreSheet: Boolean = false,
    val showReportDialog: Boolean = false,
    val showDeleteFriendDialog: Boolean = false,
    val showShareDialog: Boolean = false,
    val showImageViewer: Boolean = false,
    val currentImageUrl: String = "",
    val actionMessage: String? = null,
    val friendDeleted: Boolean = false,
    val remarkEditorConfig: RemarkEditorConfig? = null,
    val isSavingRemark: Boolean = false
)

data class RemarkEditorConfig(
    val mode: RemarkEditorMode,
    val friendId: String,
    val targetIndex: Int? = null,
    val initialPrimary: String = "",
    val initialKey: String = "",
    val initialValue: String = ""
)

sealed class RemarkEditorMode {
    object Name : RemarkEditorMode()
    object Phone : RemarkEditorMode()
    data class Other(val isNew: Boolean) : RemarkEditorMode()
}

class UserDetailViewModel(context: Context) : ViewModel() {
    private val appContext = context.applicationContext
    private val tokenRepository: TokenRepository = RepositoryFactory.getTokenRepository(appContext)
    private val userRepository: UserRepository = RepositoryFactory.getUserRepository(appContext).also {
        it.setTokenRepository(tokenRepository)
    }
    private val communityRepository: CommunityRepository = RepositoryFactory.getCommunityRepository(appContext)
    private val friendRepository: FriendRepository = RepositoryFactory.getFriendRepository(appContext)
    private val groupRepository: GroupRepository = RepositoryFactory.getGroupRepository(appContext)
    private val stickerCatalogRepository =
        com.yhchat.canary.data.repository.StickerCatalogRepository()

    private val _uiState = MutableStateFlow(UserDetailUiState())
    val uiState: StateFlow<UserDetailUiState> = _uiState.asStateFlow()

    fun consumeActionMessage() {
        _uiState.update { it.copy(actionMessage = null) }
    }

    fun consumeFriendDeleted() {
        _uiState.update { it.copy(friendDeleted = false) }
    }

    fun openAddFriendDialog() {
        _uiState.update { it.copy(showAddFriendDialog = true) }
    }

    fun dismissAddFriendDialog() {
        _uiState.update { it.copy(showAddFriendDialog = false, addFriendRemark = "") }
    }

    fun updateAddFriendRemark(remark: String) {
        _uiState.update { it.copy(addFriendRemark = remark) }
    }

    fun confirmAddFriend(userId: String) {
        val state = _uiState.value
        if (state.isAddingFriend) return
        sendFriendRequest(userId, state.addFriendRemark)
        _uiState.update { it.copy(showAddFriendDialog = false, addFriendRemark = "") }
    }

    fun openUserInfoDialog() {
        _uiState.update { it.copy(showUserInfoDialog = true) }
    }

    fun dismissUserInfoDialog() {
        _uiState.update { it.copy(showUserInfoDialog = false) }
    }

    fun openGagDialog() {
        _uiState.update { it.copy(showGagDialog = true) }
    }

    fun dismissGagDialog() {
        _uiState.update { it.copy(showGagDialog = false) }
    }

    fun openMoreSheet() {
        _uiState.update { it.copy(showMoreSheet = true) }
    }

    fun dismissMoreSheet() {
        _uiState.update { it.copy(showMoreSheet = false) }
    }

    fun openReportDialog() {
        _uiState.update { it.copy(showReportDialog = true) }
    }

    fun dismissReportDialog() {
        _uiState.update { it.copy(showReportDialog = false) }
    }

    fun openDeleteFriendDialog() {
        _uiState.update { it.copy(showDeleteFriendDialog = true) }
    }

    fun dismissDeleteFriendDialog() {
        _uiState.update { it.copy(showDeleteFriendDialog = false) }
    }

    fun openShareDialog() {
        _uiState.update { it.copy(showShareDialog = true) }
    }

    fun dismissShareDialog() {
        _uiState.update { it.copy(showShareDialog = false) }
    }

    fun openImageViewer(imageUrl: String) {
        _uiState.update { it.copy(showImageViewer = true, currentImageUrl = imageUrl) }
    }

    fun dismissImageViewer() {
        _uiState.update { it.copy(showImageViewer = false, currentImageUrl = "") }
    }

    fun openNameEditor(friendId: String) {
        val currentName = _uiState.value.userDetail?.remarkInfo?.remarkName.orEmpty()
        _uiState.update {
            it.copy(
                remarkEditorConfig = RemarkEditorConfig(
                    mode = RemarkEditorMode.Name,
                    friendId = friendId,
                    initialPrimary = currentName
                )
            )
        }
    }

    fun openPhoneEditor(friendId: String) {
        val currentPhone = _uiState.value.userDetail?.remarkInfo?.phoneNumber.orEmpty()
        _uiState.update {
            it.copy(
                remarkEditorConfig = RemarkEditorConfig(
                    mode = RemarkEditorMode.Phone,
                    friendId = friendId,
                    initialPrimary = currentPhone
                )
            )
        }
    }

    fun openOtherRemarkEditor(friendId: String, index: Int?, key: String, value: String) {
        _uiState.update {
            it.copy(
                remarkEditorConfig = RemarkEditorConfig(
                    mode = RemarkEditorMode.Other(isNew = index == null),
                    friendId = friendId,
                    targetIndex = index,
                    initialKey = key,
                    initialValue = value
                )
            )
        }
    }

    fun dismissRemarkEditor() {
        _uiState.update { it.copy(remarkEditorConfig = null) }
    }

    fun saveRemark(
        friendId: String,
        remarkName: String,
        phone: String,
        others: List<RemarkExtraEntry>
    ) {
        if (_uiState.value.isSavingRemark) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingRemark = true) }
            userRepository.saveUserRemarks(friendId, remarkName, phone, others).fold(
                onSuccess = {
                    val updatedRemark = RemarkInfo(
                        remarkName = remarkName,
                        phoneNumber = phone,
                        extraRemark = RemarkInfoJsonAdapter.encode(others),
                        extraRemarks = others
                    )
                    val updatedDetail = _uiState.value.userDetail?.copy(remarkInfo = updatedRemark)
                    _uiState.update {
                        it.copy(
                            isSavingRemark = false,
                            userDetail = updatedDetail,
                            actionMessage = "备注已更新",
                            remarkEditorConfig = null
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isSavingRemark = false,
                            actionMessage = error.message ?: "备注更新失败"
                        )
                    }
                }
            )
        }
    }

    fun load(userId: String, groupId: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val localToken = tokenRepository.getTokenSync() ?: ""
                _uiState.update { it.copy(token = localToken) }

                val cachedConversation = CacheRepository(appContext)
                    .getCachedConversationsSync()
                    .firstOrNull { it.chatId == userId && it.chatType == 1 }
                _uiState.update { it.copy(isNoNotify = cachedConversation?.doNotDisturb == 1) }

                _uiState.update { it.copy(isCheckingAddressBook = true) }
                friendRepository.getAddressBookList().fold(
                    onSuccess = { addressBook ->
                        val allIds = addressBook.dataList.flatMap { it.dataList }.map { it.chatId }.toSet()
                        _uiState.update {
                            it.copy(
                                isInAddressBook = allIds.contains(userId),
                                isCheckingAddressBook = false
                            )
                        }
                    },
                    onFailure = {
                        _uiState.update {
                            it.copy(
                                isInAddressBook = false,
                                isCheckingAddressBook = false
                            )
                        }
                    }
                )

                val requestProto = get_user_send.newBuilder()
                    .setId(userId)
                    .build()

                val requestBody = requestProto.toByteArray()
                    .toRequestBody("application/x-protobuf".toMediaTypeOrNull())

                userRepository.getUserDetail(localToken, requestBody).fold(
                    onSuccess = { detail ->
                        _uiState.update { it.copy(isLoading = false, userDetail = detail) }
                    },
                    onFailure = { throwable ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = throwable.message ?: "加载失败"
                            )
                        }
                    }
                )

                _uiState.update { it.copy(isLoadingBoards = true) }
                communityRepository.listBoardsByCreate(token = localToken, userId = userId).fold(
                    onSuccess = { resp ->
                        _uiState.update {
                            it.copy(
                                isLoadingBoards = false,
                                createdBoards = resp.data.boards
                            )
                        }
                    },
                    onFailure = {
                        _uiState.update { it.copy(isLoadingBoards = false) }
                    }
                )

                // 作者创建的表情包。走第三方目录服务，和云湖接口完全独立。
                //
                // 用独立协程启动，不 await：第三方服务响应可能很慢甚至挂住，
                // 串在这条主链路上会把后面的群成员加载一起拖死，表现就是
                // 「详情页一直转圈」。它拿到结果自己更新状态即可。
                _uiState.update { it.copy(isLoadingStickerPacks = true) }
                viewModelScope.launch {
                    // 再套一层超时：OkHttp 的超时管不到 DNS 卡住之类的场景，
                    // 这里兜底 12 秒，避免 loading 状态永远不结束。
                    val packs = withTimeoutOrNull(12_000L) {
                        stickerCatalogRepository.getPacksByAuthor(userId)
                    }.orEmpty()
                    _uiState.update {
                        it.copy(
                            isLoadingStickerPacks = false,
                            createdStickerPacks = packs
                        )
                    }
                }

                if (groupId != null) {
                    var page = 1
                    var hasMore = true
                    val allMembers = mutableListOf<GroupMemberInfo>()

                    while (hasMore) {
                        groupRepository.getGroupMembers(groupId, page = page, size = 50).fold(
                            onSuccess = { members ->
                                allMembers.addAll(members)
                                hasMore = members.size >= 50
                                page++
                            },
                            onFailure = {
                                hasMore = false
                            }
                        )
                    }

                    _uiState.update {
                        it.copy(targetUserPermission = allMembers.find { m -> m.userId == userId }?.permissionLevel ?: 0)
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "加载失败"
                    )
                }
            }
        }
    }

    fun sendFriendRequest(userId: String, remark: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAddingFriend = true) }
            val token = tokenRepository.getTokenSync() ?: ""
            friendRepository.applyFriend(
                token = token,
                chatId = userId,
                chatType = 1,
                remark = remark
            )
            _uiState.update {
                it.copy(
                    isAddingFriend = false,
                    actionMessage = "好友申请已发送"
                )
            }
        }
    }

    fun setNoNotify(userId: String, checked: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isNoNotify = checked, isSettingNoNotify = true) }
            friendRepository.setNoNotify(
                chatId = userId,
                noNotify = if (checked) 1 else 0
            ).fold(
                onSuccess = {
                    CacheRepository(appContext).updateConversationDoNotDisturb(
                        chatId = userId,
                        doNotDisturb = if (checked) 1 else 0
                    )
                    _uiState.update {
                        it.copy(
                            isSettingNoNotify = false,
                            actionMessage = "设置成功"
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isNoNotify = !checked,
                            isSettingNoNotify = false,
                            actionMessage = "设置失败：${error.message}"
                        )
                    }
                }
            )
        }
    }

    fun setMemberRole(groupId: String, userId: String, role: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingMemberAction = true) }
            groupRepository.setMemberRole(groupId, userId, role)
            _uiState.update {
                it.copy(
                    isProcessingMemberAction = false,
                    targetUserPermission = role
                )
            }
        }
    }

    fun removeMember(groupId: String, userId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingMemberAction = true) }
            groupRepository.removeMember(groupId, userId)
            _uiState.update {
                it.copy(
                    isProcessingMemberAction = false,
                    actionMessage = "已踢出群聊"
                )
            }
        }
    }

    fun gagMember(groupId: String, userId: String, gagTime: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingMemberAction = true) }
            groupRepository.gagMember(groupId, userId, gagTime)
            _uiState.update {
                it.copy(
                    isProcessingMemberAction = false,
                    actionMessage = "禁言设置已更新"
                )
            }
        }
    }

    fun deleteFriend(userId: String) {
        viewModelScope.launch {
            userRepository.deleteFriend(userId, 1).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            actionMessage = "已删除好友",
                            friendDeleted = true
                        )
                    }
                },
                onFailure = { exception ->
                    _uiState.update {
                        it.copy(actionMessage = "删除好友失败: ${exception.message}")
                    }
                }
            )
        }
    }
}
