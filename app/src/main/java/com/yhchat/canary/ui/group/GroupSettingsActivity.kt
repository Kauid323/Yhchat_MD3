package com.yhchat.canary.ui.group

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import com.yhchat.canary.ui.adaptive.YhSurface
import com.yhchat.canary.ui.theme.YhchatCanaryTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class GroupSettingsActivity : ComponentActivity() {
    
    private val viewModel: GroupSettingsViewModel by viewModels()
    
    companion object {
        const val EXTRA_GROUP_ID = "groupId"
        const val EXTRA_GROUP_NAME = "groupName"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.yhchat.canary.ui.base.SystemBarUtils.setupTransparentSystemBars(this)
        
        val groupId = intent.getStringExtra(EXTRA_GROUP_ID) ?: run {
            android.util.Log.e("GroupSettingsActivity", "Missing groupId in intent")
            finish()
            return
        }
        
        val groupName = intent.getStringExtra(EXTRA_GROUP_NAME) ?: "群聊设置"
        
        android.util.Log.d("GroupSettingsActivity", "Opening group settings: id=$groupId, name=$groupName")
        
        setContent {
            YhchatCanaryTheme {
                YhSurface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    GroupSettingsScreenRoot(
                        groupId = groupId,
                        groupName = groupName,
                        viewModel = viewModel,
                        onBackClick = { finish() }
                    )
                }
            }
        }
    }
}
