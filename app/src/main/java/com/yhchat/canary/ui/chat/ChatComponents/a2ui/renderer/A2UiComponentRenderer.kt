@file:OptIn(ExperimentalMaterial3Api::class)
package com.yhchat.canary.ui.chat.ChatComponents.a2ui.renderer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yhchat.canary.ui.chat.ChatComponents.a2ui.evaluator.buildChoicePickerValue
import com.yhchat.canary.ui.chat.ChatComponents.a2ui.evaluator.combineScopePath
import com.yhchat.canary.ui.chat.ChatComponents.a2ui.evaluator.executeA2UiAction
import com.yhchat.canary.ui.chat.ChatComponents.a2ui.evaluator.getValueAtPath
import com.yhchat.canary.ui.chat.ChatComponents.a2ui.evaluator.horizontalAlignmentFor
import com.yhchat.canary.ui.chat.ChatComponents.a2ui.evaluator.horizontalArrangementFor
import com.yhchat.canary.ui.chat.ChatComponents.a2ui.evaluator.isA2UiOptionSelected
import com.yhchat.canary.ui.chat.ChatComponents.a2ui.evaluator.resolveA2UiValue
import com.yhchat.canary.ui.chat.ChatComponents.a2ui.evaluator.resolveBoundPath
import com.yhchat.canary.ui.chat.ChatComponents.a2ui.evaluator.textColorFor
import com.yhchat.canary.ui.chat.ChatComponents.a2ui.evaluator.textStyleFor
import com.yhchat.canary.ui.chat.ChatComponents.a2ui.evaluator.validateA2UiInput
import com.yhchat.canary.ui.chat.ChatComponents.a2ui.evaluator.verticalAlignmentFor
import com.yhchat.canary.ui.chat.ChatComponents.a2ui.evaluator.verticalArrangementFor
import com.yhchat.canary.ui.chat.ChatComponents.a2ui.model.A2UiChildren
import com.yhchat.canary.ui.chat.ChatComponents.a2ui.model.A2UiComponent
import com.yhchat.canary.ui.chat.ChatComponents.a2ui.model.A2UiSpec
import com.yhchat.canary.ui.chat.ChatComponents.a2ui.model.A2UiSpacing
import com.yhchat.canary.ui.chat.ChatComponents.a2ui.model.A2UiValue
import com.yhchat.canary.ui.chat.ChatComponents.a2ui.model.toDp
import com.yhchat.canary.ui.chat.ChatComponents.a2ui.model.toHeightDp
import com.yhchat.canary.ui.chat.ChatComponents.a2ui.parser.parseA2UiChartPoints
import com.yhchat.canary.ui.chat.ChatComponents.a2ui.parser.parseA2UiHexColor
import com.yhchat.canary.ui.chat.ChatComponents.a2ui.parser.parseA2UiPaintElements
import com.yhchat.canary.ui.chat.ChatComponents.a2ui.parser.parseA2UiPieSlices
import com.yhchat.canary.ui.chat.ChatComponents.a2ui.parser.parseColor
import com.yhchat.canary.ui.components.MarkdownText
import com.yhchat.canary.ui.components.ImageViewer
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RenderA2UiComponent(
    componentId: String,
    spec: A2UiSpec,
    dataModel: Map<String, Any?>,
    modifier: Modifier = Modifier,
    scopePath: String? = null,
    parentAxis: String? = null,
    parentAlign: String? = null,
    onDataModelChange: (String, Any?) -> Unit
) {
    val component = spec.components[componentId] ?: return
    val context = LocalContext.current
    val baseModifier = modifier.applyA2UiLayout(component)

    val componentType = component.component.lowercase(Locale.ROOT)
    when {
        componentType == "column" -> {
            Column(
                modifier = baseModifier,
                verticalArrangement = verticalArrangementFor(component.justify),
                horizontalAlignment = horizontalAlignmentFor(component.align)
            ) {
                RenderA2UiColumnChildren(
                    component = component,
                    spec = spec,
                    dataModel = dataModel,
                    scopePath = scopePath,
                    parentAlign = component.align ?: parentAlign,
                    onDataModelChange = onDataModelChange
                )
            }
        }

        componentType == "row" -> {
            Row(
                modifier = baseModifier,
                horizontalArrangement = horizontalArrangementFor(component.justify),
                verticalAlignment = verticalAlignmentFor(component.align)
            ) {
                RenderA2UiRowChildren(
                    component = component,
                    spec = spec,
                    dataModel = dataModel,
                    scopePath = scopePath,
                    parentAlign = component.align ?: parentAlign,
                    onDataModelChange = onDataModelChange
                )
            }
        }

        componentType == "list" -> {
            val direction = component.direction?.lowercase(Locale.ROOT)
            if (direction == "horizontal" || direction == "row") {
                FlowRow(
                    modifier = baseModifier,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RenderA2UiFlowRowChildren(
                        component = component,
                        spec = spec,
                        dataModel = dataModel,
                        scopePath = scopePath,
                        parentAlign = component.align ?: parentAlign,
                        onDataModelChange = onDataModelChange
                    )
                }
            } else {
                Column(
                    modifier = baseModifier,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = horizontalAlignmentFor(component.align)
                ) {
                    RenderA2UiListChildren(
                        component = component,
                        spec = spec,
                        dataModel = dataModel,
                        scopePath = scopePath,
                        parentAlign = component.align ?: parentAlign,
                        onDataModelChange = onDataModelChange
                    )
                }
            }
        }

        componentType == "card" || componentType == "container" -> {
            Card(
                modifier = modifier.applyA2UiLayout(component, includePadding = false),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(component.padding?.toPaddingModifier() ?: Modifier.padding(12.dp)),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    component.child?.let { childId ->
                        RenderA2UiComponent(
                            componentId = childId,
                            spec = spec,
                            dataModel = dataModel,
                            scopePath = scopePath,
                            parentAlign = component.align ?: parentAlign,
                            onDataModelChange = onDataModelChange
                        )
                    }
                }
            }
        }

        componentType == "icon" -> {
            val iconName = component.name?.lowercase(Locale.ROOT).orEmpty()
            val imageVector = when (iconName) {
                "shoppingcart", "shopping_cart", "cart" -> Icons.Filled.ShoppingCart
                "add", "plus" -> Icons.Filled.Add
                "check", "done" -> Icons.Filled.Check
                "close", "cancel" -> Icons.Filled.Close
                "delete", "trash" -> Icons.Filled.Delete
                "favorite", "heart" -> Icons.Filled.Favorite
                "home" -> Icons.Filled.Home
                "info" -> Icons.Filled.Info
                "morevert", "more_vert", "more" -> Icons.Filled.MoreVert
                "notifications", "notification", "bell" -> Icons.Filled.Notifications
                "person", "user" -> Icons.Filled.Person
                "search" -> Icons.Filled.Search
                "settings" -> Icons.Filled.Settings
                "star" -> Icons.Filled.Star
                "warning", "alert" -> Icons.Filled.Warning
                else -> Icons.Filled.Info
            }
            Icon(
                imageVector = imageVector,
                contentDescription = component.name,
                modifier = baseModifier.size(component.size?.toDp() ?: 24.dp),
                tint = component.color?.let { parseColor(it) } ?: MaterialTheme.colorScheme.onSurface
            )
        }

        componentType == "text" || componentType == "label" || componentType == "paragraph" || componentType == "markdown" -> {
            val effectiveAlign = component.align ?: parentAlign
            val textAlign = when (effectiveAlign) {
                "center" -> TextAlign.Center
                "right" -> TextAlign.Right
                "left" -> TextAlign.Left
                else -> TextAlign.Start
            }
            
            // 在 Row 内使用 wrapContentWidth，在 Column 内使用 fillMaxWidth
            val textModifier = if (parentAxis == "row") {
                baseModifier.wrapContentWidth()
            } else {
                baseModifier.fillMaxWidth()
            }
            
            val resolvedText = resolveA2UiValue(
                spec,
                dataModel,
                component.text ?: component.label ?: component.description,
                scopePath
            )?.toString().orEmpty()

            val renderAsMarkdown = componentType == "markdown" ||
                component.variant?.equals("markdown", ignoreCase = true) == true ||
                component.usageHint?.equals("markdown", ignoreCase = true) == true

            if (renderAsMarkdown) {
                MarkdownText(
                    markdown = resolvedText,
                    modifier = textModifier,
                    textColor = textColorFor(component.variant ?: component.usageHint),
                    backgroundColor = Color.Transparent,
                    enableTextSelection = false,
                    persistRenderState = true
                )
            } else {
                Text(
                    text = resolvedText,
                    modifier = textModifier,
                    style = textStyleFor(component.variant ?: component.usageHint),
                    color = textColorFor(component.variant ?: component.usageHint),
                    textAlign = textAlign
                )
            }
        }

        componentType == "image" -> {
            val imageUrl = resolveA2UiValue(spec, dataModel, component.url, scopePath)?.toString().orEmpty()
            var showImageViewer by remember { mutableStateOf(false) }
            var loadError by remember { mutableStateOf(false) }
            
            if (imageUrl.isNotBlank()) {
                // 根据 fit 属性或 variant 决定 ContentScale
                val contentScale = when {
                    component.fit != null -> when (component.fit.lowercase(Locale.ROOT)) {
                        "cover", "crop" -> ContentScale.Crop
                        "fill", "stretch" -> ContentScale.FillBounds
                        "none", "inside" -> ContentScale.Inside
                        else -> ContentScale.Fit
                    }
                    component.variant == "largeFeature" -> ContentScale.Crop
                    component.variant == "avatar" -> ContentScale.Crop
                    else -> ContentScale.Fit
                }
                
                // 根据 variant 和父布局决定图片尺寸和形状
                val isAvatar = component.variant == "avatar"
                val imageSize = when {
                    isAvatar -> 48.dp
                    parentAxis == "row" -> {
                        when (component.variant) {
                            "smallFeature" -> 80.dp
                            "largeFeature", "header" -> 120.dp
                            else -> 100.dp
                        }
                    }
                    else -> {
                        when (component.variant) {
                            "smallFeature" -> 80.dp
                            "largeFeature", "header" -> 150.dp
                            else -> 120.dp
                        }
                    }
                }
                
                val shape = if (isAvatar) CircleShape else RoundedCornerShape(12.dp)
                
                Box(
                    modifier = baseModifier
                        .then(
                            if (parentAxis == "row" || isAvatar) {
                                Modifier.size(imageSize)
                            } else {
                                Modifier
                                    .fillMaxWidth()
                                    .height(imageSize)
                            }
                        )
                        .clip(shape)
                        .background(
                            if (loadError) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            else Color.Transparent
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(shape)
                            .clickable { if (!isAvatar) showImageViewer = true },
                        contentScale = contentScale,
                        onError = { loadError = true }
                    )
                    
                    // 加载失败时显示占位图标
                    if (loadError) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = "图片加载失败",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(if (isAvatar) 16.dp else 24.dp)
                            )
                            if (!isAvatar) {
                                Text(
                                    text = "加载失败",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
                
                if (showImageViewer && !loadError && !isAvatar) {
                    ImageViewer(
                        imageUrl = imageUrl,
                        onDismiss = { showImageViewer = false }
                    )
                }
            }
        }

        componentType == "button" -> {
            val enabled = (resolveA2UiValue(spec, dataModel, component.value, scopePath) as? Boolean) ?: true
            val buttonText = resolveA2UiValue(spec, dataModel, component.text ?: component.label, scopePath)
                ?.toString().orEmpty()
            
            // 按钮在 Column 内应该自动适配宽度，在 Row 内应该自动适配宽度
            val buttonModifier = if (parentAxis == "row") {
                baseModifier.wrapContentWidth()
            } else {
                baseModifier.wrapContentWidth()
            }
            
            when {
                component.variant.equals("borderless", ignoreCase = true) ||
                    component.variant.equals("text", ignoreCase = true) -> {
                    TextButton(
                        onClick = {
                            executeA2UiAction(context, spec, dataModel, scopePath, component.action, component.actionId)
                        },
                        enabled = enabled,
                        modifier = buttonModifier
                    ) {
                        component.child?.let { childId ->
                            RenderA2UiComponent(
                                componentId = childId,
                                spec = spec,
                                dataModel = dataModel,
                                scopePath = scopePath,
                                parentAxis = "row",
                                onDataModelChange = onDataModelChange
                            )
                        } ?: Text(text = buttonText)
                    }
                }

                component.variant.equals("primary", ignoreCase = true) || component.primary == true -> {
                    Button(
                        onClick = {
                            executeA2UiAction(context, spec, dataModel, scopePath, component.action, component.actionId)
                        },
                        enabled = enabled,
                        modifier = buttonModifier
                    ) {
                        component.child?.let { childId ->
                            RenderA2UiComponent(
                                componentId = childId,
                                spec = spec,
                                dataModel = dataModel,
                                scopePath = scopePath,
                                parentAxis = "row",
                                onDataModelChange = onDataModelChange
                            )
                        } ?: Text(text = buttonText)
                    }
                }

                else -> {
                    OutlinedButton(
                        onClick = {
                            executeA2UiAction(context, spec, dataModel, scopePath, component.action, component.actionId)
                        },
                        enabled = enabled,
                        modifier = buttonModifier
                    ) {
                        component.child?.let { childId ->
                            RenderA2UiComponent(
                                componentId = childId,
                                spec = spec,
                                dataModel = dataModel,
                                scopePath = scopePath,
                                parentAxis = "row",
                                onDataModelChange = onDataModelChange
                            )
                        } ?: Text(text = buttonText)
                    }
                }
            }
        }

        componentType == "textfield" || componentType == "datetimeinput" || componentType == "input" -> {
            val label = when (val rawLabel = component.label) {
                is A2UiValue.Literal -> rawLabel.value?.toString().orEmpty()
                is A2UiValue.Path -> resolveA2UiValue(spec, dataModel, rawLabel, scopePath)?.toString().orEmpty()
                is A2UiValue.Function -> resolveA2UiValue(spec, dataModel, rawLabel, scopePath)?.toString().orEmpty()
                null -> ""
            }
            val valuePath = resolveBoundPath(component.value, scopePath)
            val currentValue = resolveA2UiValue(spec, dataModel, component.value, scopePath)?.toString().orEmpty()
            val placeholder = resolveA2UiValue(spec, dataModel, component.placeholder, scopePath)?.toString().orEmpty()
            val description = resolveA2UiValue(spec, dataModel, component.description, scopePath)?.toString().orEmpty()
            val fieldVariant = component.variant ?: component.textFieldType
            val isDateTimeInput = componentType.equals("datetimeinput", ignoreCase = true)
            val enableDate = component.enableDate == true || (isDateTimeInput && component.variant?.contains("date") != false)
            val enableTime = component.enableTime == true || (isDateTimeInput && component.variant?.contains("time") != false)
            
            val keyboardType = when (fieldVariant?.lowercase(Locale.ROOT)) {
                "number" -> KeyboardType.Number
                "password", "obscured" -> KeyboardType.Password
                else -> KeyboardType.Text
            }
            val visualTransformation = if (
                fieldVariant.equals("password", ignoreCase = true) ||
                fieldVariant.equals("obscured", ignoreCase = true)
            ) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            }
            var textFieldValue by rememberSaveable(component.id, scopePath) { mutableStateOf(currentValue) }
            var errorMessage by rememberSaveable(component.id, scopePath) { mutableStateOf<String?>(null) }
            var showDatePicker by remember { mutableStateOf(false) }
            var showTimePicker by remember { mutableStateOf(false) }
            
            // Parse current date/time value
            val calendar = remember {
                try {
                    if (currentValue.isNotBlank()) {
                        val parser = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        parser.isLenient = false
                        parser.parse(currentValue)?.let { java.util.Calendar.getInstance().apply { time = it } }
                    } else null
                } catch (e: Exception) { null }
            } ?: java.util.Calendar.getInstance()
            
            var selectedYear by rememberSaveable(component.id, scopePath) { mutableStateOf(calendar.get(java.util.Calendar.YEAR)) }
            var selectedMonth by rememberSaveable(component.id, scopePath) { mutableStateOf(calendar.get(java.util.Calendar.MONTH)) }
            var selectedDay by rememberSaveable(component.id, scopePath) { mutableStateOf(calendar.get(java.util.Calendar.DAY_OF_MONTH)) }
            var selectedHour by rememberSaveable(component.id, scopePath) { mutableStateOf(calendar.get(java.util.Calendar.HOUR_OF_DAY)) }
            var selectedMinute by rememberSaveable(component.id, scopePath) { mutableStateOf(calendar.get(java.util.Calendar.MINUTE)) }

            LaunchedEffect(currentValue) {
                if (textFieldValue != currentValue) {
                    textFieldValue = currentValue
                    try {
                        val parser = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        parser.isLenient = false
                        parser.parse(currentValue)?.let { date ->
                            val cal = java.util.Calendar.getInstance().apply { time = date }
                            selectedYear = cal.get(java.util.Calendar.YEAR)
                            selectedMonth = cal.get(java.util.Calendar.MONTH)
                            selectedDay = cal.get(java.util.Calendar.DAY_OF_MONTH)
                            selectedHour = cal.get(java.util.Calendar.HOUR_OF_DAY)
                            selectedMinute = cal.get(java.util.Calendar.MINUTE)
                        }
                    } catch (e: Exception) { }
                }
            }
            
            // Date picker dialog
            if (showDatePicker) {
                val dialog = android.app.DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        selectedYear = year
                        selectedMonth = month
                        selectedDay = day
                        if (enableTime) {
                            showTimePicker = true
                        } else {
                            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            val cal = java.util.Calendar.getInstance()
                            cal.set(selectedYear, selectedMonth, selectedDay, 0, 0)
                            textFieldValue = formatter.format(cal.time)
                            if (valuePath != null) {
                                onDataModelChange(valuePath, textFieldValue)
                            }
                        }
                        showDatePicker = false
                    },
                    selectedYear,
                    selectedMonth,
                    selectedDay
                )
                dialog.setOnDismissListener { showDatePicker = false }
                dialog.show()
                showDatePicker = false
            }
            
            // Time picker dialog
            if (showTimePicker) {
                val dialog = android.app.TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        selectedHour = hour
                        selectedMinute = minute
                        val formatter = SimpleDateFormat(
                            if (enableDate) "yyyy-MM-dd HH:mm" else "HH:mm", 
                            Locale.getDefault()
                        )
                        val cal = java.util.Calendar.getInstance()
                        cal.set(selectedYear, selectedMonth, selectedDay, selectedHour, selectedMinute)
                        textFieldValue = formatter.format(cal.time)
                        if (valuePath != null) {
                            onDataModelChange(valuePath, textFieldValue)
                        }
                        showTimePicker = false
                    },
                    selectedHour,
                    selectedMinute,
                    true
                )
                dialog.setOnDismissListener { showTimePicker = false }
                dialog.show()
                showTimePicker = false
            }

            OutlinedTextField(
                value = textFieldValue,
                onValueChange = { newValue ->
                    textFieldValue = newValue
                    errorMessage = validateA2UiInput(
                        component = component,
                        input = newValue,
                        spec = spec,
                        dataModel = dataModel,
                        scopePath = scopePath
                    )
                    if (valuePath != null) {
                        onDataModelChange(valuePath, newValue)
                    }
                },
                modifier = baseModifier
                    .then(
                        if (parentAxis == "row") {
                            Modifier.width(200.dp) // 在行布局中使用固定宽度
                        } else {
                            Modifier.fillMaxWidth()
                        }
                    )
                    .then(
                        if (isDateTimeInput) {
                            Modifier.clickable { 
                                if (enableDate) {
                                    showDatePicker = true
                                } else if (enableTime) {
                                    showTimePicker = true
                                }
                            }
                        } else Modifier
                    ),
                label = {
                    if (label.isNotBlank()) {
                        Text(if (component.required == true) "$label *" else label)
                    }
                },
                placeholder = {
                    if (placeholder.isNotBlank()) {
                        Text(placeholder)
                    }
                },
                trailingIcon = if (isDateTimeInput) {
                    {
                        IconButton(onClick = { 
                            if (enableDate) {
                                showDatePicker = true
                            } else if (enableTime) {
                                showTimePicker = true
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.CalendarMonth,
                                contentDescription = "选择日期时间",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else null,
                supportingText = if (parentAxis != "row") {
                    {
                        when {
                            errorMessage != null -> Text(
                                text = errorMessage.orEmpty(),
                                color = MaterialTheme.colorScheme.error
                            )

                            isDateTimeInput -> {
                                val parts = buildList {
                                    if (enableDate) add("date")
                                    if (enableTime) add("time")
                                }
                                if (parts.isNotEmpty()) {
                                    Text("点击图标选择 ${parts.joinToString(" + ")}")
                                }
                            }

                            description.isNotBlank() -> Text(description)
                        }
                    }
                } else null, // 在行布局中不显示支持文本以节省空间
                singleLine = !fieldVariant.equals("longText", ignoreCase = true),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                visualTransformation = visualTransformation,
                isError = errorMessage != null
            )
        }

        componentType == "choicepicker" -> {
            val label = resolveA2UiValue(spec, dataModel, component.label, scopePath)?.toString()
            val currentValue = resolveA2UiValue(spec, dataModel, component.value, scopePath)
            val valuePath = resolveBoundPath(component.value, scopePath)
            val displayStyle = component.displayStyle?.lowercase(Locale.ROOT)
            val isMultiple = component.variant.equals("multipleSelection", ignoreCase = true) ||
                component.multiple == true ||
                displayStyle == "checkbox"
            Column(
                modifier = baseModifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!label.isNullOrBlank()) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    component.options.forEach { option ->
                        val selected = isA2UiOptionSelected(currentValue, option.value)
                        if (displayStyle == "checkbox") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (valuePath != null) {
                                            onDataModelChange(
                                                valuePath,
                                                buildChoicePickerValue(currentValue, option.value, true)
                                            )
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selected,
                                    onCheckedChange = {
                                        if (valuePath != null) {
                                            onDataModelChange(
                                                valuePath,
                                                buildChoicePickerValue(currentValue, option.value, true)
                                            )
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(option.label)
                            }
                        } else if (!isMultiple && (
                                displayStyle == "radio" ||
                                    component.variant.equals("mutuallyExclusive", ignoreCase = true)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (valuePath != null) {
                                            onDataModelChange(valuePath, option.value)
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick = {
                                        if (valuePath != null) {
                                            onDataModelChange(valuePath, option.value)
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(option.label)
                            }
                        } else if (displayStyle == "chips") {
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    if (valuePath != null) {
                                        onDataModelChange(
                                            valuePath,
                                            buildChoicePickerValue(currentValue, option.value, isMultiple)
                                        )
                                    }
                                },
                                label = { Text(option.label) }
                            )
                        } else {
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    if (valuePath != null) {
                                        onDataModelChange(
                                            valuePath,
                                            buildChoicePickerValue(currentValue, option.value, isMultiple)
                                        )
                                    }
                                },
                                label = { Text(option.label) }
                            )
                        }
                    }
                }
            }
        }

        componentType == "modal" || componentType == "dialog" || componentType == "bottomsheet" -> {
            val triggerId = component.trigger ?: component.entryPointChild ?: component.child
            val contentId = component.content ?: component.contentChild ?: component.child
            var visible by rememberSaveable(component.id, scopePath) { mutableStateOf(false) }

            if (triggerId != null) {
                Box(
                    modifier = baseModifier.clickable { visible = true }
                ) {
                    RenderA2UiComponent(
                        componentId = triggerId,
                        spec = spec,
                        dataModel = dataModel,
                        scopePath = scopePath,
                        parentAxis = parentAxis,
                        parentAlign = parentAlign,
                        onDataModelChange = onDataModelChange
                    )
                }
            }

            if (visible && contentId != null) {
                ModalBottomSheet(
                    onDismissRequest = { visible = false },
                    sheetState = rememberModalBottomSheetState()
                ) {
                    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
                    val sheetColor = MaterialTheme.colorScheme.surface
                    val darkIcons = sheetColor.luminance() > 0.5f
                    if (activity != null) {
                        com.yhchat.canary.ui.base.SystemBarUtils.ApplyNavigationBarColor(activity, sheetColor, darkIcons)
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .padding(bottom = 32.dp)
                    ) {
                        RenderA2UiComponent(
                            componentId = contentId,
                            spec = spec,
                            dataModel = dataModel,
                            scopePath = scopePath,
                            onDataModelChange = onDataModelChange
                        )
                    }
                }
            }
        }

        componentType == "slider" || componentType == "range" -> {
            val valuePath = resolveBoundPath(component.value, scopePath)
            val currentValue = (resolveA2UiValue(spec, dataModel, component.value, scopePath) as? Number)?.toFloat() ?: 0f
            val min = component.min?.toFloat() ?: 0f
            val max = component.max?.toFloat() ?: 100f
            
            Column(modifier = baseModifier.fillMaxWidth()) {
                component.label?.let {
                    Text(
                        text = resolveA2UiValue(spec, dataModel, it, scopePath)?.toString().orEmpty(),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Slider(
                    value = currentValue.coerceIn(min, max),
                    onValueChange = { newValue ->
                        if (valuePath != null) {
                            onDataModelChange(valuePath, newValue)
                        }
                    },
                    valueRange = min..max,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        componentType == "audioplayer" || componentType == "audio" -> {
            val url = resolveA2UiValue(spec, dataModel, component.url, scopePath)?.toString().orEmpty()
            val description = resolveA2UiValue(spec, dataModel, component.description ?: component.text, scopePath)?.toString().orEmpty()
            if (url.isNotBlank()) {
                A2UiAudioPlayer(
                    playerId = component.id,
                    url = url,
                    description = description,
                    modifier = baseModifier
                )
            }
        }

        componentType == "videoplayer" || componentType == "video" -> {
            val url = resolveA2UiValue(spec, dataModel, component.url, scopePath)?.toString().orEmpty()
            val poster = resolveA2UiValue(spec, dataModel, component.poster, scopePath)?.toString().orEmpty()
            if (url.isNotBlank()) {
                A2UiVideoPlayer(
                    playerId = component.id,
                    url = url,
                    poster = poster,
                    fit = component.fit ?: "contain",
                    width = component.width.toDp(),
                    height = component.height.toHeightDp(),
                    modifier = baseModifier
                )
            }
        }

        componentType == "linechart" -> {
            val chartData = resolveA2UiValue(spec, dataModel, component.dataValue, scopePath)
            val title = resolveA2UiValue(spec, dataModel, component.title, scopePath)?.toString()

            Column(
                modifier = baseModifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!title.isNullOrBlank()) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                val points = parseA2UiChartPoints(chartData)
                if (points.isNotEmpty()) {
                    A2UiLineChart(title = null, points = points)
                }
            }
        }

        componentType == "barchart" -> {
            val chartData = resolveA2UiValue(spec, dataModel, component.dataValue, scopePath)
            val title = resolveA2UiValue(spec, dataModel, component.title, scopePath)?.toString()

            Column(
                modifier = baseModifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!title.isNullOrBlank()) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                val points = parseA2UiChartPoints(chartData)
                if (points.isNotEmpty()) {
                    A2UiBarChart(title = null, points = points)
                }
            }
        }

        componentType == "piechart" -> {
            val chartData = resolveA2UiValue(spec, dataModel, component.dataValue, scopePath)
            val title = resolveA2UiValue(spec, dataModel, component.title, scopePath)?.toString()

            Column(
                modifier = baseModifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!title.isNullOrBlank()) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                val slices = parseA2UiPieSlices(chartData)
                if (slices.isNotEmpty()) {
                    A2UiPieChart(title = null, slices = slices, showPercentageOnChart = true)
                }
            }
        }

        componentType == "chart" || componentType.contains("chart") -> {
            val chartType = component.variant ?: "bar"
            val chartData = resolveA2UiValue(spec, dataModel, component.dataValue, scopePath)
            val title = resolveA2UiValue(spec, dataModel, component.title, scopePath)?.toString()
            
            Column(
                modifier = baseModifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!title.isNullOrBlank()) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                when (chartType.lowercase(Locale.ROOT)) {
                    "pie" -> {
                        val slices = parseA2UiPieSlices(chartData)
                        if (slices.isNotEmpty()) {
                            A2UiPieChart(title = null, slices = slices)
                        }
                    }
                    "line" -> {
                        val points = parseA2UiChartPoints(chartData)
                        if (points.isNotEmpty()) {
                            A2UiLineChart(title = null, points = points)
                        }
                    }
                    else -> {
                        val points = parseA2UiChartPoints(chartData)
                        if (points.isNotEmpty()) {
                            A2UiBarChart(title = null, points = points)
                        }
                    }
                }
            }
        }

        componentType == "custompaint" || componentType == "canvas" -> {
            val elements = parseA2UiPaintElements(component.elements)
            val bgColor = component.backgroundColor?.let { parseA2UiHexColor(it) }
            A2UiCustomPaint(
                width = component.width.toDp(),
                height = component.height.toHeightDp(),
                backgroundColor = bgColor,
                elements = elements,
                modifier = baseModifier
            )
        }

        componentType == "tabview" || componentType == "tabs" -> {
            val tabs = component.tabs ?: emptyList()
            val activeTabPath = resolveBoundPath(component.activeTab, scopePath)
            val initialActiveTabIndex = (resolveA2UiValue(spec, dataModel, component.activeTab, scopePath) as? Number)?.toInt() ?: 0
            var localActiveTabIndex by rememberSaveable(component.id, scopePath) { mutableStateOf(initialActiveTabIndex) }
            val activeTabIndex = (if (activeTabPath != null) initialActiveTabIndex else localActiveTabIndex)
                .coerceIn(0, tabs.lastIndex.coerceAtLeast(0))
            
            Column(modifier = baseModifier.fillMaxWidth()) {
                if (tabs.isNotEmpty()) {
                    val tabLabels = tabs.mapIndexed { index, tabMap ->
                        tabMap["label"]?.toString() ?: "Tab ${index + 1}"
                    }
                    SecondaryTabRow(
                        selectedTabIndex = activeTabIndex
                    ) {
                        tabLabels.forEachIndexed { index, tabLabel ->
                            Tab(
                                selected = activeTabIndex == index,
                                onClick = {
                                    if (activeTabPath != null) {
                                        onDataModelChange(activeTabPath, index)
                                    } else {
                                        localActiveTabIndex = index
                                    }
                                },
                                text = { Text(tabLabel) }
                            )
                        }
                    }
                }
                
                val currentTab = tabs.getOrNull(activeTabIndex)
                val contentId = currentTab?.get("content")?.toString()
                if (contentId != null) {
                    key(activeTabIndex) {
                        Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            RenderA2UiComponent(
                                componentId = contentId,
                                spec = spec,
                                dataModel = dataModel,
                                scopePath = scopePath,
                                onDataModelChange = onDataModelChange
                            )
                        }
                    }
                }
            }
        }

        componentType == "divider" || componentType == "separator" -> {
            if (parentAxis == "row") {
                VerticalDivider(
                    modifier = Modifier.height(24.dp).padding(horizontal = 8.dp),
                    color = component.color?.let { parseColor(it) } ?: MaterialTheme.colorScheme.outlineVariant
                )
            } else {
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    color = component.color?.let { parseColor(it) } ?: MaterialTheme.colorScheme.outlineVariant
                )
            }
        }

        componentType == "spacer" -> {
            val size = component.size?.toDp() ?: 8.dp
            if (parentAxis == "row") {
                Spacer(modifier = Modifier.width(size))
            } else {
                Spacer(modifier = Modifier.height(size))
            }
        }

        componentType == "progress" || componentType == "indicator" -> {
            val progress = component.progressValue ?: 0f
            @Suppress("UNUSED_VARIABLE")
            val color = component.color?.let { parseColor(it) } ?: MaterialTheme.colorScheme.primary
            if (component.variant == "circular") {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = baseModifier.size(40.dp)
                )
            } else {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = baseModifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun Modifier.applyA2UiLayout(component: A2UiComponent, includePadding: Boolean = true): Modifier {
    return this
        .then(component.margin?.toPaddingModifier() ?: Modifier)
        .then(
            when {
                component.width != null && component.height != null -> Modifier
                    .width(component.width.toDp())
                    .height(component.height.toHeightDp())
                component.width != null -> Modifier.width(component.width.toDp())
                component.height != null -> Modifier.height(component.height.toHeightDp())
                else -> Modifier
            }
        )
        .then(if (includePadding) component.padding?.toPaddingModifier() ?: Modifier else Modifier)
}

private fun A2UiSpacing.toPaddingModifier(): Modifier {
    return Modifier.padding(
        start = start.toSpacingDp(),
        top = top.toSpacingDp(),
        end = end.toSpacingDp(),
        bottom = bottom.toSpacingDp()
    )
}

private fun Number?.toSpacingDp() = this?.toDp() ?: 0.dp

@Composable
private fun RenderA2UiListChildren(
    component: A2UiComponent,
    spec: A2UiSpec,
    dataModel: Map<String, Any?>,
    scopePath: String?,
    parentAlign: String?,
    onDataModelChange: (String, Any?) -> Unit
) {
    when (val children = component.children) {
        is A2UiChildren.Static -> {
            children.ids.filter { spec.components.containsKey(it) }.forEach { childId ->
                RenderA2UiComponent(
                    componentId = childId,
                    spec = spec,
                    dataModel = dataModel,
                    modifier = Modifier.fillMaxWidth(),
                    scopePath = scopePath,
                    parentAxis = "column",
                    parentAlign = parentAlign,
                    onDataModelChange = onDataModelChange
                )
            }
        }

        is A2UiChildren.Template -> {
            val resolved = getValueAtPath(dataModel, combineScopePath(scopePath, children.path))
            when (resolved) {
                is List<*> -> {
                    resolved.forEachIndexed { index, _ ->
                        RenderA2UiComponent(
                            componentId = children.componentId,
                            spec = spec,
                            dataModel = dataModel,
                            modifier = Modifier.fillMaxWidth(),
                            scopePath = combineScopePath(scopePath, "${children.path.trim('/')}/$index"),
                            parentAxis = "column",
                            parentAlign = parentAlign,
                            onDataModelChange = onDataModelChange
                        )
                    }
                }

                is Map<*, *> -> {
                    RenderA2UiComponent(
                        componentId = children.componentId,
                        spec = spec,
                        dataModel = dataModel,
                        modifier = Modifier.fillMaxWidth(),
                        scopePath = combineScopePath(scopePath, children.path),
                        parentAxis = "column",
                        parentAlign = parentAlign,
                        onDataModelChange = onDataModelChange
                    )
                }
            }
        }

        null -> {
            component.child?.takeIf { spec.components.containsKey(it) }?.let { childId ->
                RenderA2UiComponent(
                    componentId = childId,
                    spec = spec,
                    dataModel = dataModel,
                    modifier = Modifier.fillMaxWidth(),
                    scopePath = scopePath,
                    parentAxis = "column",
                    parentAlign = parentAlign,
                    onDataModelChange = onDataModelChange
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.RenderA2UiColumnChildren(
    component: A2UiComponent,
    spec: A2UiSpec,
    dataModel: Map<String, Any?>,
    scopePath: String?,
    parentAlign: String?,
    onDataModelChange: (String, Any?) -> Unit
) {
    when (val children = component.children) {
        is A2UiChildren.Static -> {
            children.ids.filter { spec.components.containsKey(it) }.forEachIndexed { index, childId ->
                if (index > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
                val childModifier = spec.components[childId]
                    ?.weight
                    ?.let { Modifier.weight(it.toFloat(), fill = false) }
                    ?: Modifier
                RenderA2UiComponent(
                    componentId = childId,
                    spec = spec,
                    dataModel = dataModel,
                    modifier = childModifier,
                    scopePath = scopePath,
                    parentAxis = "column",
                    parentAlign = parentAlign,
                    onDataModelChange = onDataModelChange
                )
            }
        }

        is A2UiChildren.Template -> {
            val resolved = getValueAtPath(dataModel, combineScopePath(scopePath, children.path))
            when (resolved) {
                is List<*> -> {
                    resolved.forEachIndexed { index, _ ->
                        val childScope = combineScopePath(scopePath, "${children.path.trim('/')}/$index")
                        RenderA2UiComponent(
                            componentId = children.componentId,
                            spec = spec,
                            dataModel = dataModel,
                            modifier = Modifier.fillMaxWidth(),
                            scopePath = childScope,
                            parentAxis = "column",
                            parentAlign = parentAlign,
                            onDataModelChange = onDataModelChange
                        )
                    }
                }

                is Map<*, *> -> {
                    RenderA2UiComponent(
                        componentId = children.componentId,
                        spec = spec,
                        dataModel = dataModel,
                        modifier = Modifier.fillMaxWidth(),
                        scopePath = combineScopePath(scopePath, children.path),
                        parentAxis = "column",
                        parentAlign = parentAlign,
                        onDataModelChange = onDataModelChange
                    )
                }
            }
        }

        null -> {
            component.child?.let { childId ->
                RenderA2UiComponent(
                    componentId = childId,
                    spec = spec,
                    dataModel = dataModel,
                    scopePath = scopePath,
                    parentAxis = "column",
                    parentAlign = parentAlign,
                    onDataModelChange = onDataModelChange
                )
            }
        }
    }
}

@Composable
private fun RowScope.RenderA2UiRowChildren(
    component: A2UiComponent,
    spec: A2UiSpec,
    dataModel: Map<String, Any?>,
    scopePath: String?,
    parentAlign: String?,
    onDataModelChange: (String, Any?) -> Unit
) {
    when (val children = component.children) {
        is A2UiChildren.Static -> {
            children.ids.filter { spec.components.containsKey(it) }.forEach { childId ->
                val childComponent = spec.components[childId]
                val weightModifier = childComponent?.weight
                    ?.let { Modifier.weight(it.toFloat(), fill = false) }
                    ?: Modifier.wrapContentWidth()
                RenderA2UiComponent(
                    componentId = childId,
                    spec = spec,
                    dataModel = dataModel,
                    modifier = weightModifier,
                    scopePath = scopePath,
                    parentAxis = "row",
                    parentAlign = parentAlign,
                    onDataModelChange = onDataModelChange
                )
            }
        }

        is A2UiChildren.Template -> {
            val resolved = getValueAtPath(dataModel, combineScopePath(scopePath, children.path))
            when (resolved) {
                is List<*> -> {
                    resolved.forEachIndexed { index, _ ->
                        val childScope = combineScopePath(scopePath, "${children.path.trim('/')}/$index")
                        RenderA2UiComponent(
                            componentId = children.componentId,
                            spec = spec,
                            dataModel = dataModel,
                            modifier = Modifier.wrapContentWidth(),
                            scopePath = childScope,
                            parentAxis = "row",
                            parentAlign = parentAlign,
                            onDataModelChange = onDataModelChange
                        )
                    }
                }

                is Map<*, *> -> {
                    RenderA2UiComponent(
                        componentId = children.componentId,
                        spec = spec,
                        dataModel = dataModel,
                        modifier = Modifier.wrapContentWidth(),
                        scopePath = combineScopePath(scopePath, children.path.trim('/')),
                        parentAxis = "row",
                        parentAlign = parentAlign,
                        onDataModelChange = onDataModelChange
                    )
                }
            }
        }

        null -> {
            component.child?.let { childId ->
                RenderA2UiComponent(
                    componentId = childId,
                    spec = spec,
                    dataModel = dataModel,
                    modifier = Modifier.wrapContentWidth(),
                    scopePath = scopePath,
                    parentAxis = "row",
                    parentAlign = parentAlign,
                    onDataModelChange = onDataModelChange
                )
            }
        }
    }
}

@Composable
private fun RenderA2UiFlowRowChildren(
    component: A2UiComponent,
    spec: A2UiSpec,
    dataModel: Map<String, Any?>,
    scopePath: String?,
    parentAlign: String?,
    onDataModelChange: (String, Any?) -> Unit
) {
    when (val children = component.children) {
        is A2UiChildren.Static -> {
            children.ids.filter { spec.components.containsKey(it) }.forEach { childId ->
                RenderA2UiComponent(
                    componentId = childId,
                    spec = spec,
                    dataModel = dataModel,
                    modifier = Modifier.wrapContentWidth(),
                    scopePath = scopePath,
                    parentAxis = "row",
                    parentAlign = parentAlign,
                    onDataModelChange = onDataModelChange
                )
            }
        }

        else -> {
            component.child?.let { childId ->
                RenderA2UiComponent(
                    componentId = childId,
                    spec = spec,
                    dataModel = dataModel,
                    modifier = Modifier.wrapContentWidth(),
                    scopePath = scopePath,
                    parentAxis = "row",
                    parentAlign = parentAlign,
                    onDataModelChange = onDataModelChange
                )
            }
        }
    }
}
