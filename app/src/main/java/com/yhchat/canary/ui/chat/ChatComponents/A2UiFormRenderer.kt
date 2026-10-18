package com.yhchat.canary.ui.chat.ChatComponents

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yhchat.canary.ui.chat.ChatComponents.a2ui.evaluator.resolveRootComponentId
import com.yhchat.canary.ui.chat.ChatComponents.a2ui.model.A2UiChildren
import com.yhchat.canary.ui.chat.ChatComponents.a2ui.model.A2UiSpec
import com.yhchat.canary.ui.chat.ChatComponents.a2ui.parser.updateA2UiDataModel
import com.yhchat.canary.ui.chat.ChatComponents.a2ui.renderer.RenderA2UiComponent
import androidx.compose.material3.Surface

@Composable
internal fun A2UiFormMessage(
    spec: A2UiSpec,
    modifier: Modifier = Modifier
) {
    var dataModel by remember(spec) { mutableStateOf(spec.dataModel ?: emptyMap()) }
    val rootComponentId = resolveRootComponentId(spec.components) ?: return
    val horizontalScrollState = rememberScrollState()
    val surfaceWidth = remember(spec) { spec.preferredSurfaceWidthDp() }
    
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(horizontalScrollState)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 使用 key 确保当 dataModel 改变时重新渲染
            key(dataModel) {
                RenderA2UiComponent(
                    componentId = rootComponentId,
                    spec = spec,
                    dataModel = dataModel,
                    modifier = Modifier.width(surfaceWidth.dp),
                    onDataModelChange = { path, value ->
                        val updatedModel = updateA2UiDataModel(dataModel, path, value)
                        dataModel = updatedModel
                    }
                )
            }
        }
    }
}

private fun A2UiSpec.preferredSurfaceWidthDp(): Int {
    val explicitRootWidth = resolveRootComponentId(components)
        ?.let { rootId -> components[rootId]?.width?.toPositiveInt() }
    if (explicitRootWidth != null) return explicitRootWidth

    val hasDesktopRows = components.values.any { component ->
        component.component.equals("Row", ignoreCase = true) &&
            (component.justify.equals("spaceBetween", ignoreCase = true) ||
                component.justify.equals("spaceAround", ignoreCase = true) ||
                component.justify.equals("spaceEvenly", ignoreCase = true)) &&
            ((component.children as? A2UiChildren.Static)?.ids?.size ?: 0) >= 3
    }
    return if (hasDesktopRows) 720 else 360
}

private fun Number.toPositiveInt(): Int? {
    val value = when (this) {
        is Int -> this
        is Long -> this.toInt()
        is Float -> this.toInt()
        is Double -> this.toInt()
        else -> this.toString().toIntOrNull()
    } ?: return null
    return value.takeIf { it > 0 }
}
