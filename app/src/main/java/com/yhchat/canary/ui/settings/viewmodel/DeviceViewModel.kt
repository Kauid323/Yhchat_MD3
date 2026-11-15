package com.yhchat.canary.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhchat.canary.data.di.RepositoryFactory
import com.yhchat.canary.data.model.DeviceInfo
import com.yhchat.canary.data.repository.DeviceRepository
import com.yhchat.canary.data.repository.TokenRepository
import com.yhchat.canary.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 设备管理 ViewModel。
 *
 * 数据来源有两层：
 *   1. 本地记录（已知设备 / 历史设备）—— 打开页面立刻能显示；
 *   2. 服务端在线列表 —— 网络回来后再覆盖，并回写本地。
 *
 * 这样即使没网，用户也能看到"这台设备上次什么时候用过"，
 * 而不是看到一个空白页。
 */
@HiltViewModel
class DeviceViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val deviceRepository: DeviceRepository
) : ViewModel() {

    private val _deviceState = MutableStateFlow(DeviceState())
    val deviceState: StateFlow<DeviceState> = _deviceState.asStateFlow()

    private var tokenRepository: TokenRepository? = null

    fun init(context: Context) {
        val repo = RepositoryFactory.getTokenRepository(context.applicationContext)
        tokenRepository = repo
        userRepository.setTokenRepository(repo)
    }

    fun setTokenRepository(tokenRepository: TokenRepository) {
        this.tokenRepository = tokenRepository
        userRepository.setTokenRepository(tokenRepository)
    }

    /** 当前账号 ID，设备记录按它隔离 */
    private fun currentUserId(): String? =
        tokenRepository?.getUserIdSync()?.takeIf { it.isNotBlank() }

    /**
     * 加载设备列表。
     *
     * 先本地、后网络：本地记录立即上屏，在线接口回来后合并覆盖。
     * 网络失败时保留本地记录，只提示错误，不清空列表。
     */
    fun loadOnlineDevices() {
        viewModelScope.launch {
            val userId = currentUserId()

            // 1. 先上本地记录，避免打开就一片空白
            if (userId != null) {
                val local = deviceRepository.getLocalDevices(userId)
                if (local.isNotEmpty()) {
                    _deviceState.value = _deviceState.value.copy(devices = local)
                }
            }

            _deviceState.value = _deviceState.value.copy(isLoading = true, error = null)

            // 2. 拉在线列表
            userRepository.getOnlineDevices()
                .onSuccess { online ->
                    val merged = if (userId != null) {
                        // 回写本地并合并成「在线 + 历史」
                        deviceRepository.mergeOnlineDevices(userId, online)
                    } else {
                        online.map { it.copy(isOnline = true) }
                    }
                    _deviceState.value = _deviceState.value.copy(
                        isLoading = false,
                        devices = merged,
                        error = null,
                        lastSyncAt = System.currentTimeMillis()
                    )
                }
                .onFailure { error ->
                    _deviceState.value = _deviceState.value.copy(
                        isLoading = false,
                        // 网络失败不清空已有列表（可能已经有本地记录）
                        error = error.message ?: "获取在线设备失败"
                    )
                }
        }
    }

    /**
     * 让指定设备下线。
     *
     * 成功后并不删除本地记录 —— 设备会变成「历史设备」继续留在列表里，
     * 用户还能看到它上次的登录时间。
     */
    fun offlineDevice(device: DeviceInfo) {
        viewModelScope.launch {
            _deviceState.value = _deviceState.value.copy(operatingDeviceIds = _deviceState.value.operatingDeviceIds + device.deviceId)

            userRepository.offlineDevice(device.deviceId).fold(
                onSuccess = {
                    val userId = currentUserId()
                    if (userId != null) {
                        deviceRepository.markOffline(userId, device.deviceId)
                    }
                    // 本地把这条标成离线，不用等下次刷新
                    _deviceState.value = _deviceState.value.copy(
                        devices = _deviceState.value.devices.map {
                            if (it.deviceId == device.deviceId) it.copy(isOnline = false) else it
                        },
                        operatingDeviceIds = _deviceState.value.operatingDeviceIds - device.deviceId,
                        userMessage = "已下线 ${device.getPlatformDisplayName()}"
                    )
                    // 之后重新拉一次，确保与服务端一致
                    reloadQuietly()
                },
                onFailure = { error ->
                    _deviceState.value = _deviceState.value.copy(
                        operatingDeviceIds = _deviceState.value.operatingDeviceIds - device.deviceId,
                        userMessage = error.message ?: "下线失败"
                    )
                }
            )
        }
    }

    /** 静默刷新：不显示 loading，失败也不弹错误 */
    private fun reloadQuietly() {
        viewModelScope.launch {
            val userId = currentUserId()
            userRepository.getOnlineDevices()
                .onSuccess { online ->
                    val merged = if (userId != null) {
                        deviceRepository.mergeOnlineDevices(userId, online)
                    } else {
                        online.map { it.copy(isOnline = true) }
                    }
                    _deviceState.value = _deviceState.value.copy(devices = merged)
                }
        }
    }

    fun clearError() {
        _deviceState.value = _deviceState.value.copy(error = null)
    }

    fun consumeUserMessage() {
        _deviceState.value = _deviceState.value.copy(userMessage = null)
    }

    fun clearDevices() {
        _deviceState.value = DeviceState()
    }
}

/**
 * 设备状态
 */
data class DeviceState(
    val isLoading: Boolean = false,
    val devices: List<DeviceInfo> = emptyList(),
    val error: String? = null,
    /** 一次性提示（下线成功/失败） */
    val userMessage: String? = null,
    /** 正在执行下线操作的设备 ID，用于禁用按钮 + 显示进度 */
    val operatingDeviceIds: Set<String> = emptySet(),
    /** 最近一次成功同步的时间（毫秒） */
    val lastSyncAt: Long? = null
) {
    val onlineDevices: List<DeviceInfo> get() = devices.filter { it.isOnline }
    val historyDevices: List<DeviceInfo> get() = devices.filter { !it.isOnline }
}
