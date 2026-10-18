package com.yhchat.canary.ui.search

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import com.yhchat.canary.ui.chat.ChatActivity
import androidx.compose.material3.Surface
import com.yhchat.canary.ui.theme.YhchatCanaryTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SearchActivity : ComponentActivity() {
    private val viewModel: SearchViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.yhchat.canary.ui.base.SystemBarUtils.setupTransparentSystemBars(this)
        
        setContent {
            YhchatCanaryTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SearchScreen(
                        onBackClick = { finish() },
                        viewModel = viewModel,
                        onItemClick = { chatId, chatType, chatName ->
                            val intent = Intent(this, ChatActivity::class.java).apply {
                                putExtra("chatId", chatId)
                                putExtra("chatType", chatType)
                                putExtra("chatName", chatName)
                            }
                            startActivity(intent)
                        }
                    )
                }
            }
        }
    }
}
