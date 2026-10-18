package com.yhchat.canary.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yhchat.canary.data.di.RepositoryFactory
import com.yhchat.canary.data.model.GroupMemberInfo
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import kotlinx.coroutines.delay

@Composable
fun GroupMemberMentionPicker(
    groupId: String,
    keyword: String,
    selectedIds: Set<String>,
    onMemberSelected: (GroupMemberInfo) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val groupRepository = remember { RepositoryFactory.getGroupRepository(context) }
    var searchText by remember(keyword) { mutableStateOf(keyword) }
    var members by remember { mutableStateOf<List<GroupMemberInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var currentPage by remember { mutableStateOf(1) }
    var hasMore by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }

    BackHandler { onDismiss() }

    LaunchedEffect(groupId, searchText) {
        isLoading = true
        errorText = null
        currentPage = 1
        delay(200)
        groupRepository.getGroupMembers(groupId = groupId, keywords = searchText, page = 1).fold(
            onSuccess = { list ->
                members = list
                hasMore = list.size >= 50
                isLoading = false
            },
            onFailure = { error ->
                errorText = error.message ?: "加载失败"
                members = emptyList()
                isLoading = false
            }
        )
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "关闭")
                }
                Text("选择群成员", style = MaterialTheme.typography.titleSmall)
                Button(onClick = onDismiss) {
                    Text("确定")
                }
            }

            HorizontalDivider()

            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                placeholder = { Text("搜索群成员") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                singleLine = true
            )

            HorizontalDivider()

            when {
                isLoading -> {
                    Text(
                        text = "加载中...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                errorText != null -> {
                    Text(
                        text = errorText ?: "加载失败",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                members.isEmpty() -> {
                    Text(
                        text = "没有匹配的群成员",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                    ) {
                        items(members, key = { member -> member.userId }) { member ->
                            GroupMemberMentionRow(
                                member = member,
                                isSelected = selectedIds.contains(member.userId),
                                onSelected = onMemberSelected
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupMemberMentionRow(
    member: GroupMemberInfo,
    isSelected: Boolean,
    onSelected: (GroupMemberInfo) -> Unit
) {
    val badgeText = when (member.permissionLevel) {
        100 -> "群主"
        2 -> "管理员"
        else -> null
    }
    val badgeColor = when (member.permissionLevel) {
        100 -> Color(0xFFFF9800)
        2 -> Color(0xFF2196F3)
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isSelected) { onSelected(member) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { checked ->
                if (checked && !isSelected) {
                    onSelected(member)
                }
            }
        )

        AsyncImage(
            model = ImageUtils.createAvatarImageRequest(LocalContext.current, member.avatarUrl),
            contentDescription = member.name,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = member.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (badgeText != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = badgeColor
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
