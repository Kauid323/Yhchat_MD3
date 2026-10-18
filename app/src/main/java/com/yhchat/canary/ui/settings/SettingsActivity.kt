package com.yhchat.canary.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yhchat.canary.MainActivity
import com.yhchat.canary.data.di.RepositoryFactory
import com.yhchat.canary.data.repository.NavigationRepository
import com.yhchat.canary.data.repository.TokenRepository
import com.yhchat.canary.ui.base.BaseActivity
import androidx.compose.material3.Surface
import com.yhchat.canary.ui.theme.YhchatCanaryTheme

/**
 * 设置页面Activity
 */
class SettingsActivity : BaseActivity() {
    
    companion object {
        private const val EXTRA_HAS_NAVIGATION_REPO = "has_navigation_repo"
        private const val EXTRA_HAS_TOKEN_REPO = "has_token_repo"
        
        /**
         * 启动设置Activity
         */
        fun start(
            context: Context, 
            navigationRepository: NavigationRepository? = null,
            tokenRepository: TokenRepository? = null
        ) {
            val intent = Intent(context, SettingsActivity::class.java).apply {
                putExtra(EXTRA_HAS_NAVIGATION_REPO, navigationRepository != null)
                putExtra(EXTRA_HAS_TOKEN_REPO, tokenRepository != null)
            }
            context.startActivity(intent)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val hasNavigationRepo = intent.getBooleanExtra(EXTRA_HAS_NAVIGATION_REPO, false)
        val hasTokenRepo = intent.getBooleanExtra(EXTRA_HAS_TOKEN_REPO, false)
        
        // 重新创建repository实例
        val navigationRepository = if (hasNavigationRepo) {
            RepositoryFactory.getNavigationRepository(this)
        } else null
        
        val tokenRepository = if (hasTokenRepo) {
            RepositoryFactory.getTokenRepository(this)
        } else null
        
        val accountRepository = RepositoryFactory.getAccountRepository(this)
        
        setContent {
            YhchatCanaryTheme {
                val settingsViewModel: SettingsViewModel = viewModel()
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SettingsScreen(
                        navigationRepository = navigationRepository,
                        tokenRepository = tokenRepository,
                        accountRepository = accountRepository,
                        onLogout = {
                            settingsViewModel.logout {
                                val intent = Intent(this@SettingsActivity, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                }
                                startActivity(intent)
                                finish()
                            }
                        },
                        onBackClick = {
                            finish()
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
