package com.yhchat.canary.ui.group

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import com.yhchat.canary.crash.CrashHandler
import com.yhchat.canary.ui.base.BaseActivity
import com.yhchat.canary.ui.adaptive.YhSurface
import com.yhchat.canary.ui.theme.YhchatCanaryTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class GroupInfoActivity : BaseActivity() {
    
    private val viewModel: GroupInfoViewModel by viewModels()
    
    companion object {
        const val EXTRA_GROUP_ID = "groupId"
        const val EXTRA_GROUP_NAME = "groupName"
        
        fun start(context: android.content.Context, groupId: String, groupName: String) {
            val intent = Intent(context, GroupInfoActivity::class.java).apply {
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
            android.util.Log.e("GroupInfoActivity", "Missing groupId in intent")
            finish()
            return
        }
        
        val groupName = intent.getStringExtra(EXTRA_GROUP_NAME) ?: "群聊"
        
        android.util.Log.d("GroupInfoActivity", "Opening group info: id=$groupId, name=$groupName")
        
        setContent {
            YhchatCanaryTheme {
                YhSurface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    GroupInfoScreenRoot(
                        groupId = groupId,
                        groupName = groupName,
                        viewModel = viewModel,
                        onBackClick = { finish() },
                        onSettingsClick = {
                            try {
                                val intent = Intent(this@GroupInfoActivity, GroupSettingsActivity::class.java).apply {
                                    putExtra(GroupSettingsActivity.EXTRA_GROUP_ID, groupId)
                                    putExtra(GroupSettingsActivity.EXTRA_GROUP_NAME, groupName)
                                }
                                startActivity(intent)
                            } catch (e: Exception) {
                                android.util.Log.e("GroupInfoActivity", "Failed to open settings", e)
                                CrashHandler.showCaughtException(this@GroupInfoActivity, e)
                                android.widget.Toast.makeText(
                                    this@GroupInfoActivity,
                                    "无法打开群聊设置",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onShareClick = {
                            // 分享功能已在GroupInfoScreen中集成
                        },
                        onReportClick = {
                            // 举报功能已在GroupInfoScreen中集成
                        },
                        onSearchChatClick = {
                            try {
                                ChatSearchActivity.start(
                                    context = this@GroupInfoActivity,
                                    chatId = groupId,
                                    chatType = 2, // 群聊类型
                                    chatName = groupName
                                )
                            } catch (e: Exception) {
                                android.util.Log.e("GroupInfoActivity", "Failed to open chat search", e)
                                CrashHandler.showCaughtException(this@GroupInfoActivity, e)
                                android.widget.Toast.makeText(
                                    this@GroupInfoActivity,
                                    "无法打开聊天记录搜索",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                }
            }
        }
    }
}
