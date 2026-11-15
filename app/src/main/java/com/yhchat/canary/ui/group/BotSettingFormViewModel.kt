package com.yhchat.canary.ui.group

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yhchat.canary.data.di.RepositoryFactory
import com.yhchat.canary.data.model.BotSettingItem
import com.yhchat.canary.data.model.BotSettingSubmit
import com.yhchat.canary.data.model.BotSettingType
import com.yhchat.canary.data.model.controlType
import com.yhchat.canary.data.repository.GroupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 机器人设置表单的 ViewModel。
 *
 * 表单结构完全由服务端下发，这里只做三件事：
 *   1. 把 settingsJson 拉下来；
 *   2. 维护每一项的「编辑中的值」（用户改了但还没保存）；
 *   3. 保存时把所有项一起提交。
 *
 * 编辑值单独存一份而不是原地改 [BotSettingItem]，是因为保存失败时要能回滚 ——
 * 原地改的话原值就丢了，用户改了半天一点就全白改。
 */
class BotSettingFormViewModel(application: Application) : AndroidViewModel(application) {

    private val groupRepository: GroupRepository =
        RepositoryFactory.getGroupRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(BotSettingFormUiState())
    val uiState: StateFlow<BotSettingFormUiState> = _uiState.asStateFlow()

    fun load(botId: String, groupId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            groupRepository.getBotUserSettings(botId = botId, chatId = groupId).fold(
                onSuccess = { items ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = null,
                        // 保持服务端下发的顺序，界面按它渲染
                        items = items,
                        // 每个控件各自持一份编辑值
                        switches = items
                            .filter { it.controlType == BotSettingType.Switch }
                            .associate { it.id to it.switchValue },
                        texts = items
                            .filter {
                                it.controlType == BotSettingType.TextArea ||
                                    it.controlType == BotSettingType.Text
                            }
                            .associate { it.id to it.textValue },
                        radios = items
                            // radio 和 select 共用同一个索引通道：语义一样，只是展示方式不同
                            .filter {
                                it.controlType == BotSettingType.Radio ||
                                    it.controlType == BotSettingType.Select
                            }
                            .associate { it.id to it.currentSelectIndex },
                        checkboxes = items
                            .filter { it.controlType == BotSettingType.Checkbox }
                            .associate { it.id to it.checkboxStates }
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "获取机器人设置失败"
                    )
                }
            )
        }
    }

    fun setSwitch(itemId: String, value: Boolean) {
        _uiState.value = _uiState.value.copy(
            switches = _uiState.value.switches + (itemId to value),
            isDirty = true
        )
    }

    fun setText(itemId: String, value: String) {
        _uiState.value = _uiState.value.copy(
            texts = _uiState.value.texts + (itemId to value),
            isDirty = true
        )
    }

    fun setRadio(itemId: String, index: Int) {
        _uiState.value = _uiState.value.copy(
            radios = _uiState.value.radios + (itemId to index),
            isDirty = true
        )
    }

    /** 切换多选里的某一项 */
    fun toggleCheckbox(itemId: String, index: Int, checked: Boolean) {
        val current = _uiState.value.checkboxes[itemId].orEmpty()
        val next = current.toMutableList()
        while (next.size <= index) next.add(false)
        next[index] = checked
        _uiState.value = _uiState.value.copy(
            checkboxes = _uiState.value.checkboxes + (itemId to next),
            isDirty = true
        )
    }

    fun save(botId: String, groupId: String, onSaved: () -> Unit) {
        if (_uiState.value.isSaving) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, userMessage = null)

            val state = _uiState.value
            val submits = state.items.map { item ->
                when (item.controlType) {
                    BotSettingType.Switch -> BotSettingSubmit(
                        id = item.id,
                        type = item.type.ifBlank { "switch" },
                        label = item.label,
                        booleanValue = state.switches[item.id] ?: item.switchValue
                    )

                    // radio 和 select 的提交格式完全一致，共用一套
                    BotSettingType.Radio, BotSettingType.Select -> {
                        val index = state.radios[item.id] ?: item.currentSelectIndex
                        val options = item.options
                        BotSettingSubmit(
                            id = item.id,
                            type = item.type.ifBlank {
                                if (item.controlType == BotSettingType.Select) "select" else "radio"
                            },
                            label = item.label,
                            // 未选中时真实请求里给的是 -1，不是 0
                            selectIndex = index,
                            // 选项文本要跟着索引走：用户换了选项，selectValue 必须同步，
                            // 只发索引的话服务端存下来的是旧文本。
                            // index 为 -1（未选）时保留原来的 selectValue。
                            selectValue = if (index >= 0) {
                                options.getOrNull(index).orEmpty()
                            } else {
                                item.currentSelectValue
                            }
                        )
                    }

                    BotSettingType.Checkbox -> {
                        val states = state.checkboxes[item.id] ?: item.checkboxStates
                        // 长度必须和选项数对齐，否则服务端保存后勾选会错位
                        val aligned = List(item.options.size) { i -> states.getOrNull(i) ?: false }
                        BotSettingSubmit(
                            id = item.id,
                            type = item.type.ifBlank { "checkbox" },
                            label = item.label,
                            checkboxStates = aligned
                        )
                    }

                    else -> BotSettingSubmit(
                        id = item.id,
                        type = item.type.ifBlank { "textarea" },
                        label = item.label,
                        textValue = state.texts[item.id] ?: item.textValue
                    )
                }
            }

            groupRepository.saveBotSettings(
                botId = botId,
                groupId = groupId,
                items = submits
            ).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        isDirty = false,
                        userMessage = "保存成功"
                    )
                    onSaved()
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        userMessage = error.message ?: "保存失败"
                    )
                }
            )
        }
    }

    fun consumeUserMessage() {
        _uiState.value = _uiState.value.copy(userMessage = null)
    }
}

data class BotSettingFormUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isDirty: Boolean = false,
    val error: String? = null,
    val userMessage: String? = null,
    /** 服务端下发的表单项，保持原顺序 */
    val items: List<BotSettingItem> = emptyList(),
    /** 开关的编辑值 */
    val switches: Map<String, Boolean> = emptyMap(),
    /** 文本 / 多行文本的编辑值 */
    val texts: Map<String, String> = emptyMap(),
    /** 单选的编辑值（索引）。radio 和 select 共用；-1 表示未选中 */
    val radios: Map<String, Int> = emptyMap(),
    /** 多选的编辑值，与该项的选项列表一一对应 */
    val checkboxes: Map<String, List<Boolean>> = emptyMap()
)
