package com.yhchat.canary.ui.group

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yhchat.canary.data.api.BotGroupPermissionItem
import com.yhchat.canary.data.di.RepositoryFactory
import com.yhchat.canary.data.repository.BotRepository
import com.yhchat.canary.data.repository.FriendRepository
import com.yhchat.canary.data.repository.GroupRepository
import com.yhchat.canary.proto.group.Bot_data
import com.yhchat.canary.ui.contacts.Contact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GroupBotManagementViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val groupRepository: GroupRepository = RepositoryFactory.getGroupRepository(appContext)
    private val botRepository: BotRepository = RepositoryFactory.getBotRepository(appContext)
    private val friendRepository: FriendRepository = RepositoryFactory.getFriendRepository(appContext)

    private val _uiState = MutableStateFlow(GroupBotManagementUiState())
    val uiState: StateFlow<GroupBotManagementUiState> = _uiState.asStateFlow()

    fun onScreenEnter(groupId: String) {
        loadGroupBots(groupId)
        loadMyBots()
    }

    fun loadGroupBots(groupId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            groupRepository.getGroupBots(groupId).fold(
                onSuccess = { bots ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        bots = bots
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

    fun removeBot(botId: String, groupId: String) {
        viewModelScope.launch {
            botRepository.removeGroupBot(botId, groupId).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(pendingRemoveBot = null, userMessage = "操作成功")
                    loadGroupBots(groupId)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(userMessage = error.message ?: "操作失败")
                }
            )
        }
    }

    fun requestRemoveBot(bot: Bot_data) {
        _uiState.value = _uiState.value.copy(pendingRemoveBot = bot)
    }

    fun dismissRemoveBotDialog() {
        _uiState.value = _uiState.value.copy(pendingRemoveBot = null)
    }

    fun consumeUserMessage() {
        _uiState.value = _uiState.value.copy(userMessage = null)
    }

    fun openInviteDialog() {
        _uiState.value = _uiState.value.copy(showInviteBotDialog = true)
    }

    fun dismissInviteDialog() {
        _uiState.value = _uiState.value.copy(showInviteBotDialog = false, inviteSearchQuery = "")
    }

    fun updateInviteSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(inviteSearchQuery = query)
    }

    fun loadMyBots() {
        viewModelScope.launch {
            try {
                friendRepository.getAddressBookList().fold(
                    onSuccess = { addressBookList ->
                        val botsList = mutableListOf<Contact>()
                        addressBookList.dataList.forEach { data ->
                            if (data.listName == "机器人") {
                                data.dataList.forEach { botData ->
                                    botsList.add(
                                        Contact(
                                            chatId = botData.chatId,
                                            name = botData.name,
                                            avatarUrl = botData.avatarUrl,
                                            permissionLevel = botData.permissonLevel
                                        )
                                    )
                                }
                            }
                        }
                        _uiState.value = _uiState.value.copy(myBots = botsList)
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(userMessage = error.message ?: "加载机器人失败")
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(userMessage = e.message ?: "加载机器人失败")
            }
        }
    }

    fun inviteBot(botId: String, groupId: String) {
        viewModelScope.launch {
            groupRepository.inviteToGroup(botId, 3, groupId).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        showInviteBotDialog = false,
                        inviteSearchQuery = "",
                        userMessage = "操作成功"
                    )
                    loadGroupBots(groupId)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(userMessage = error.message ?: "邀请失败")
                }
            )
        }
    }

    fun openPermissionDialog(groupId: String, bot: Bot_data) {
        _uiState.value = _uiState.value.copy(
            selectedPermissionBot = bot,
            botPermissionStates = _uiState.value.botPermissionStates - bot.botId,
            loadingPermissionBotIds = _uiState.value.loadingPermissionBotIds + bot.botId,
            userMessage = null
        )

        viewModelScope.launch {
            groupRepository.getBotGroupPermission(groupId = groupId, botId = bot.botId).fold(
                onSuccess = { permissionData ->
                    _uiState.value = _uiState.value.copy(
                        botPermissionStates = _uiState.value.botPermissionStates + (
                            bot.botId to permissionData.toUiState()
                        ),
                        loadingPermissionBotIds = _uiState.value.loadingPermissionBotIds - bot.botId
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        loadingPermissionBotIds = _uiState.value.loadingPermissionBotIds - bot.botId,
                        userMessage = error.message ?: "获取机器人权限失败"
                    )
                }
            )
        }
    }

    fun closePermissionDialog() {
        _uiState.value = _uiState.value.copy(selectedPermissionBot = null)
    }

    fun refreshPermission(groupId: String, botId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                loadingPermissionBotIds = _uiState.value.loadingPermissionBotIds + botId
            )
            groupRepository.getBotGroupPermission(groupId = groupId, botId = botId).fold(
                onSuccess = { permissionData ->
                    _uiState.value = _uiState.value.copy(
                        botPermissionStates = _uiState.value.botPermissionStates + (
                            botId to permissionData.toUiState()
                        ),
                        loadingPermissionBotIds = _uiState.value.loadingPermissionBotIds - botId
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        loadingPermissionBotIds = _uiState.value.loadingPermissionBotIds - botId,
                        userMessage = error.message ?: "获取机器人权限失败"
                    )
                }
            )
        }
    }

    /**
     * 切换某一项权限。key 直接用服务端下发的 key（allowEditGroupInfo / allowGetGroupMembers ...），
     * 只提交换的这一项，其余保持服务端现状。
     */
    fun toggleBotPermission(
        groupId: String,
        botId: String,
        key: String,
        enabled: Boolean
    ) {
        val currentState = _uiState.value.botPermissionStates[botId] ?: BotGroupPermissionState()
        val nextState = currentState.withPermission(key, enabled)

        _uiState.value = _uiState.value.copy(
            botPermissionStates = _uiState.value.botPermissionStates + (botId to nextState),
            updatingPermissionBotIds = _uiState.value.updatingPermissionBotIds + botId,
            userMessage = null
        )

        viewModelScope.launch {
            groupRepository.editBotGroupPermission(
                groupId = groupId,
                botId = botId,
                permissions = mapOf(key to enabled)
            ).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        updatingPermissionBotIds = _uiState.value.updatingPermissionBotIds - botId
                    )
                },
                onFailure = { error ->
                    // 失败回滚到切换前的状态
                    _uiState.value = _uiState.value.copy(
                        botPermissionStates = _uiState.value.botPermissionStates + (botId to currentState),
                        updatingPermissionBotIds = _uiState.value.updatingPermissionBotIds - botId,
                        userMessage = error.message ?: "更新机器人权限失败"
                    )
                }
            )
        }
    }
}

data class GroupBotManagementUiState(
    val isLoading: Boolean = false,
    val bots: List<Bot_data> = emptyList(),
    val myBots: List<Contact> = emptyList(),
    val showInviteBotDialog: Boolean = false,
    val inviteSearchQuery: String = "",
    val pendingRemoveBot: Bot_data? = null,
    val error: String? = null,
    val userMessage: String? = null,
    val selectedPermissionBot: Bot_data? = null,
    val botPermissionStates: Map<String, BotGroupPermissionState> = emptyMap(),
    val updatingPermissionBotIds: Set<String> = emptySet(),
    val loadingPermissionBotIds: Set<String> = emptySet()
)

/**
 * 一个机器人在本群的管理权限。
 * 权限项不再本地枚举：顺序、名称、描述都跟服务端 group-permission-get 的 permissions 列表走，
 * 本地只维护 key -> enabled 的映射，这样服务端加项不用改客户端。
 */
data class BotGroupPermissionState(
    val items: List<BotGroupPermissionItem> = emptyList(),
    val enabled: Map<String, Boolean> = emptyMap()
) {
    val isEmpty: Boolean get() = items.isEmpty()

    fun isEnabled(key: String): Boolean = enabled[key] ?: false

    fun withPermission(key: String, value: Boolean): BotGroupPermissionState =
        copy(enabled = enabled + (key to value))
}

private fun com.yhchat.canary.data.api.BotGroupPermissionData.toUiState(): BotGroupPermissionState {
    // 新接口下发 permissions 列表时优先用它；老响应只有顶层四个 0/1 字段，兜底转成同样的结构
    val list = permissions?.takeIf { it.isNotEmpty() } ?: legacyPermissions()
    return BotGroupPermissionState(
        items = list,
        enabled = list.associate { it.key to it.enabled }
    )
}

private fun com.yhchat.canary.data.api.BotGroupPermissionData.legacyPermissions(): List<BotGroupPermissionItem> =
    listOf(
        BotGroupPermissionItem("allowEditGroupInfo", "允许修改群信息", "允许机器人修改群信息及消息类型限制", allowEditGroupInfo == 1),
        BotGroupPermissionItem("allowGagMember", "允许禁言用户", "允许机器人禁言或解除成员禁言", allowGagMember == 1),
        BotGroupPermissionItem("allowRemoveMember", "允许移除群成员", "允许机器人将成员移出本群", allowRemoveMember == 1),
        BotGroupPermissionItem("allowGroupTagManage", "允许控制标签组", "允许机器人创建、修改群标签及成员标签关联", allowGroupTagManage == 1),
        BotGroupPermissionItem("allowGetGroupMembers", "允许获取群成员列表", "允许机器人读取本群成员的基本信息；开启隐藏群成员后仍可读取完整名单", allowGetGroupMembers == 1)
    )
