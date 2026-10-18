package com.yhchat.canary.ui.group

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import com.yhchat.canary.ui.base.BaseActivity
import androidx.compose.material3.Surface
import com.yhchat.canary.ui.theme.YhchatCanaryTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class GroupMembersActivity : BaseActivity() {
    
    private val viewModel: GroupInfoViewModel by viewModels()
    
    companion object {
        const val EXTRA_GROUP_ID = "groupId"
        const val EXTRA_GROUP_NAME = "groupName"
        
        fun start(context: Context, groupId: String, groupName: String) {
            val intent = Intent(context, GroupMembersActivity::class.java).apply {
                putExtra(EXTRA_GROUP_ID, groupId)
                putExtra(EXTRA_GROUP_NAME, groupName)
            }
            context.startActivity(intent)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val groupId = intent.getStringExtra(EXTRA_GROUP_ID) ?: run {
            android.util.Log.e("GroupMembersActivity", "Missing groupId in intent")
            finish()
            return
        }
        
        val groupName = intent.getStringExtra(EXTRA_GROUP_NAME) ?: "群成员"
        
        setContent {
            YhchatCanaryTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GroupMembersScreen(
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
