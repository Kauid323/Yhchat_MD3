package com.yhchat.canary.ui.community

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** Adapted from Oolong ForumTabRow / GlassTabRow: capsule, 4dp inset and sliding selection bubble.
 * Uses this app's Material 3 palette in place of Oolong's ExtendedTheme and glass-renderer dependencies.
 */
@Composable
internal fun BoardPostTabs(typ: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val selectedIndex = if (typ == 2) 0 else 1
    val position by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 500f),
        label = "boardTabBubble"
    )
    val bubbleColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.9f))
            .padding(4.dp)
            .selectableGroup()
            .drawBehind {
                val tabWidth = size.width / 2
                val x = if (layoutDirection == LayoutDirection.Ltr) position * tabWidth
                    else (1f - position) * tabWidth
                drawRoundRect(bubbleColor, Offset(x, 0f), Size(tabWidth, size.height), CornerRadius(size.height / 2))
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(2 to "最热", 1 to "最新").forEach { (value, title) ->
            val selected = typ == value
            val color by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "boardTabText"
            )
            Box(
                modifier = Modifier.widthIn(min = 80.dp).heightIn(min = 48.dp)
                    .clip(CircleShape)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(value) })
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = color)
            }
        }
    }
}
