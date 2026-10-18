package com.yhchat.canary.ui.login

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import com.yhchat.canary.MainActivity
import com.yhchat.canary.data.di.RepositoryFactory
import com.yhchat.canary.data.repository.AccountRepository
import com.yhchat.canary.data.repository.TokenRepository
import com.yhchat.canary.data.repository.UserRepository
import com.yhchat.canary.ui.base.BaseActivity
import androidx.compose.material3.Surface
import com.yhchat.canary.ui.theme.YhchatCanaryTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LoginActivity : BaseActivity() {

    private val loginViewModel: LoginViewModel by viewModels()
    private lateinit var tokenRepository: TokenRepository
    private lateinit var accountRepository: AccountRepository
    private lateinit var userRepository: UserRepository

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, LoginActivity::class.java)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        tokenRepository = RepositoryFactory.getTokenRepository(this)
        accountRepository = RepositoryFactory.getAccountRepository(this)
        userRepository = RepositoryFactory.getUserRepository(this)

        // Set repositories for ViewModel
        loginViewModel.setTokenRepository(tokenRepository)
        loginViewModel.setAccountRepositories(accountRepository, userRepository)

        setContent {
            YhchatCanaryTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LoginScreen(
                        onLoginSuccess = { token, userId ->
                            handleLoginSuccess(token, userId)
                        },
                        viewModel = loginViewModel
                    )
                }
            }
        }
    }

    private fun handleLoginSuccess(token: String, userId: String) {
        loginViewModel.finalizeLogin(token, userId) {
            val intent = Intent(this@LoginActivity, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }
    }
}
