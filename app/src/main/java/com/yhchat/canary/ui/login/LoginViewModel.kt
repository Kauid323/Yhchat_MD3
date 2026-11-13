package com.yhchat.canary.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhchat.canary.data.api.ApiService
import com.yhchat.canary.data.model.CaptchaData
import com.yhchat.canary.data.model.LoginData
import com.yhchat.canary.data.model.SlideCaptchaData
import com.yhchat.canary.data.model.UserProfile
import com.yhchat.canary.data.repository.AccountRepository
import com.yhchat.canary.data.repository.TokenRepository
import com.yhchat.canary.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 登录ViewModel
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val apiService: ApiService
) : ViewModel() {

    private val userRepository: UserRepository
    private var tokenRepository: TokenRepository? = null
    private var accountRepository: AccountRepository? = null
    private var profileRepository: UserRepository? = null

    init {
        userRepository = UserRepository(apiService, null)
        // 注意：tokenRepository 会在后续通过 setTokenRepository 方法设置
    }


    fun setTokenRepository(tokenRepository: TokenRepository?) {
        this.tokenRepository = tokenRepository
        if (tokenRepository != null) {
            userRepository.setTokenRepository(tokenRepository)
        }
    }

    fun setAccountRepositories(
        accountRepository: AccountRepository,
        userRepository: UserRepository
    ) {
        this.accountRepository = accountRepository
        this.profileRepository = userRepository
    }

    // UI状态
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()
    
    // 验证码数据
    private val _captchaData = MutableStateFlow<CaptchaData?>(null)
    val captchaData: StateFlow<CaptchaData?> = _captchaData.asStateFlow()

    /**
     * 滑块验证码。null 表示当前没有待用的滑块，界面据此决定是否弹窗。
     */
    private val _slideCaptcha = MutableStateFlow<SlideCaptchaData?>(null)
    val slideCaptcha: StateFlow<SlideCaptchaData?> = _slideCaptcha.asStateFlow()
    
    /**
     * 获取验证码
     */
    fun getCaptcha() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            userRepository.getCaptcha()
                .onSuccess { captcha ->
                    _captchaData.value = captcha
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message
                    )
                }
        }
    }

    /**
     * 获取滑块验证码。
     *
     * 和 [getCaptcha] 是两条独立的通道：图形码走 v1/user/captcha，
     * 滑块走 v1/captcha/get。这里不覆盖 _captchaData，否则图形码那张图
     * 会被顶掉，用户长按弹滑块、取消之后图形码就没了。
     */
    fun loadSlideCaptcha() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            userRepository.getSlideCaptcha()
                .onSuccess { slide ->
                    _slideCaptcha.value = slide
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "获取滑块验证码失败: ${error.message}"
                    )
                }
        }
    }

    /**
     * 关闭滑块弹窗时清掉这份数据。
     *
     * 滑块 id 是一次性的：验证过一次或超时后就不能再用，
     * 留着下次长按直接复用会拿到「验证已过期」。
     */
    fun clearSlideCaptcha() {
        _slideCaptcha.value = null
    }

    /**
     * 用滑块验证码获取短信验证码。
     *
     * @param x 碎片在底图上的最终横坐标（底图原始像素）
     */
    fun getSmsCaptchaBySlide(mobile: String, x: Int) {
        if (mobile.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "请输入手机号")
            return
        }

        val slide = _slideCaptcha.value
        if (slide == null) {
            _uiState.value = _uiState.value.copy(error = "滑块验证已失效，请重新长按验证码")
            return
        }

        // 先清掉再发请求：滑块 id 是一次性的，留着的话
        //  1) 弹窗不会关，用户能连点两次，第二次必然失败；
        //  2) 失败后同一张图再拖一次也过不了。
        // 清空后弹窗立即消失，失败时用户重新长按会拿一张新的。
        _slideCaptcha.value = null

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            userRepository.getSmsCaptchaBySlide(mobile, slide.id, x)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = null,
                        smsSuccess = true
                    )
                }
                .onFailure { error ->
                    // 这里刻意不动 smsSuccess：界面上的倒计时是
                    // LaunchedEffect(smsSuccess) 驱动的，把它置回 false 会
                    // 让正在跑的倒计时协程被取消，秒数永远停在当前值。
                    // 之前发过一条短信的话，那个倒计时本来就该继续走。
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "获取短信验证码失败: ${error.message}"
                    )
                }
        }
    }

    /**
     * 获取短信验证码
     */
    fun getSmsCaptcha(mobile: String, captchaCode: String) {
        if (mobile.isBlank() || captchaCode.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "请输入手机号和图片验证码")
            return
        }

        val captchaData = _captchaData.value
        if (captchaData == null) {
            _uiState.value = _uiState.value.copy(error = "请先获取图片验证码")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            userRepository.getSmsCaptcha(mobile, captchaCode, captchaData.id)
                .onSuccess { response ->
                    // 检查响应是否包含success字符
                    val isSuccess = response.toString().contains("success", ignoreCase = true)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = null,
                        smsSuccess = isSuccess
                    )
                    if (isSuccess) {
                        println("短信验证码发送成功")
                    }
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "获取短信验证码失败: ${error.message}",
                        smsSuccess = false
                    )
                }
        }
    }
    
    /**
     * 验证码登录
     */
    fun loginWithCaptcha(mobile: String, captcha: String) {
        if (mobile.isBlank() || captcha.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "请输入手机号和验证码")
            return
        }
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            userRepository.verificationLogin(mobile, captcha, generateDeviceId())
                .onSuccess { loginData ->
                    println("登录成功: $loginData")
                    completeLogin(loginData.token)
                }
                .onFailure { error ->
                    println("登录失败: ${error.message}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "登录失败: ${error.message}"
                    )
                }
        }
    }
    
    /**
     * 邮箱登录
     */
    fun loginWithEmail(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "请输入邮箱和密码")
            return
        }
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            userRepository.emailLogin(email, password, generateDeviceId())
                .onSuccess { loginData ->
                    println("邮箱登录成功: $loginData")
                    completeLogin(loginData.token)
                }
                .onFailure { error ->
                    println("邮箱登录失败: ${error.message}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "登录失败: ${error.message}"
                    )
                }
        }
    }
    
    /**
     * Token登录
     */
    fun loginWithToken(token: String) {
        if (token.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "请输入Token")
            return
        }
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                completeLogin(token)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Token登录失败: ${e.message}"
                )
            }
        }
    }

    private suspend fun completeLogin(token: String) {
        val tokenRepository = this.tokenRepository
        tokenRepository?.saveToken(token)

        val profileRepository = this.profileRepository ?: userRepository
        profileRepository.setTokenRepository(tokenRepository)

        val profile = profileRepository.getUserProfile().getOrNull()
        val resolvedUserId = profile?.userId?.takeIf { it.isNotBlank() }
            ?: "user_${token.takeLast(8)}"

        tokenRepository?.saveToken(token, resolvedUserId)
        saveCurrentAccount(token, resolvedUserId, profile)

        _uiState.value = _uiState.value.copy(
            isLoading = false,
            loginSuccess = true,
            loginData = LoginData(token = token),
            loggedInUserId = resolvedUserId,
            loggedInAccountName = profile?.nickname.orEmpty(),
            loggedInAvatarUrl = profile?.avatarUrl
        )
    }

    private suspend fun saveCurrentAccount(
        token: String,
        userId: String,
        profile: UserProfile?
    ) {
        val accountRepository = this.accountRepository ?: return
        accountRepository.saveAccount(
            userId = userId,
            name = profile?.nickname?.takeIf { it.isNotBlank() } ?: "User $userId",
            avatarUrl = profile?.avatarUrl,
            displayId = userId,
            token = token
        )
    }
    
    /**
     * 清除错误信息
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun finalizeLogin(token: String, userId: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                val accountRepository = this@LoginViewModel.accountRepository
                val profileRepository = this@LoginViewModel.profileRepository

                tokenRepository?.saveToken(token, userId)
                if (accountRepository != null && profileRepository != null) {
                    profileRepository.getUserProfile().onSuccess { profile ->
                        saveCurrentAccount(token, profile.userId, profile)
                        tokenRepository?.saveToken(token, profile.userId)
                    }.onFailure {
                        accountRepository.saveAccount(
                            userId = userId,
                            name = "User $userId",
                            avatarUrl = null,
                            displayId = userId,
                            token = token
                        )
                    }
                }
            } catch (_: Exception) {
            } finally {
                onComplete()
            }
        }
    }
    
    /**
     * 生成设备ID
     */
    private fun generateDeviceId(): String {
        return java.util.UUID.randomUUID().toString()
    }
}

/**
 * 登录UI状态
 */
data class LoginUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val loginSuccess: Boolean = false,
    val loginData: LoginData? = null,
    val loggedInUserId: String? = null,
    val loggedInAccountName: String = "",
    val loggedInAvatarUrl: String? = null,
    val smsSuccess: Boolean = false
)
