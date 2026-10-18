package com.yhchat.canary.ui.chat.ChatComponents.a2ui.renderer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import com.yhchat.canary.ui.chat.ChatComponents.a2ui.model.A2UiChartPoint
import com.yhchat.canary.ui.chat.ChatComponents.a2ui.model.A2UiPaintElement
import com.yhchat.canary.ui.chat.ChatComponents.a2ui.model.A2UiPieChartSlice
import com.yhchat.canary.ui.chat.ChatComponents.a2ui.parser.parseA2UiHexColor
import java.util.Locale
import kotlin.math.sqrt

@Composable
internal fun A2UiLineChart(
    title: String?,
    points: List<A2UiChartPoint>,
    modifier: Modifier = Modifier
) {
    val values = points.map { it.y }
    val hasData = values.isNotEmpty()
    val minY = if (hasData) values.minOrNull() ?: 0f else 0f
    val maxY = if (hasData) values.maxOrNull() ?: 0f else 0f
    val range = (maxY - minY).takeIf { it > 0f } ?: 1f
    
    // 状态管理：选中的点和工具提示
    var selectedPointIndex by remember { mutableStateOf<Int?>(null) }
    var tooltipPosition by remember { mutableStateOf<Offset?>(null) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!title.isNullOrBlank()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
        }
        if (!hasData) {
            Text("No chart data", style = MaterialTheme.typography.bodySmall)
            return
        }
        
        // 图表区域布局
        Box {
            Row {
                // Y轴标签列 - 放在左侧
                Column(
                    modifier = Modifier
                        .width(40.dp)
                        .height(200.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (maxY == maxY.toLong().toFloat()) maxY.toLong().toString() else String.format("%.1f", maxY),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if ((minY + maxY) / 2 == ((minY + maxY) / 2).toLong().toFloat()) ((minY + maxY) / 2).toLong().toString() else String.format("%.1f", (minY + maxY) / 2),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (minY == minY.toLong().toFloat()) minY.toLong().toString() else String.format("%.1f", minY),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // 图表绘制区域
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(200.dp)
                        .pointerInput(points) {
                            detectTapGestures { offset ->
                                val leftPadding = 8.dp.toPx()
                                val rightPadding = 8.dp.toPx()
                                val topPadding = 8.dp.toPx()
                                val bottomPadding = 8.dp.toPx()
                                val plotWidth = (size.width - leftPadding - rightPadding).coerceAtLeast(1f)
                                val plotHeight = (size.height - topPadding - bottomPadding).coerceAtLeast(1f)
                                
                                // 查找最近的点
                                var nearestIndex = -1
                                var nearestDistance = Float.MAX_VALUE
                                
                                points.forEachIndexed { index, point ->
                                    val x = leftPadding + if (points.size == 1) plotWidth / 2 else (index * plotWidth / (points.lastIndex.coerceAtLeast(1)))
                                    val yRatio = (point.y - minY) / range
                                    val y = topPadding + (1f - yRatio) * plotHeight
                                    
                                    val dx = offset.x - x
                                    val dy = offset.y - y
                                    val distance = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                                    
                                    if (distance < 30.dp.toPx() && distance < nearestDistance) {
                                        nearestDistance = distance
                                        nearestIndex = index
                                    }
                                }
                                
                                if (nearestIndex >= 0) {
                                    selectedPointIndex = nearestIndex
                                    tooltipPosition = offset
                                } else {
                                    selectedPointIndex = null
                                    tooltipPosition = null
                                }
                            }
                        }
                ) {
                    val leftPadding = 8.dp.toPx()
                    val rightPadding = 8.dp.toPx()
                    val topPadding = 8.dp.toPx()
                    val bottomPadding = 8.dp.toPx()
                    val plotWidth = (size.width - leftPadding - rightPadding).coerceAtLeast(1f)
                    val plotHeight = (size.height - topPadding - bottomPadding).coerceAtLeast(1f)

                    // 绘制Y轴刻度线
                    val yLabels = listOf(minY, (minY + maxY) / 2, maxY)
                    yLabels.forEach { label ->
                        val yRatio = (label - minY) / range
                        val yPos = topPadding + (1f - yRatio) * plotHeight
                        
                        drawLine(
                            color = Color(0x11000000),
                            start = Offset(leftPadding, yPos),
                            end = Offset(leftPadding + plotWidth, yPos),
                            strokeWidth = 0.5.dp.toPx()
                        )
                    }
                    
                    // 绘制X轴和Y轴
                    drawLine(
                        color = Color(0x33000000),
                        start = Offset(leftPadding, topPadding + plotHeight),
                        end = Offset(leftPadding + plotWidth, topPadding + plotHeight),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = Color(0x33000000),
                        start = Offset(leftPadding, topPadding),
                        end = Offset(leftPadding, topPadding + plotHeight),
                        strokeWidth = 1.dp.toPx()
                    )

                    // 绘制折线和点
                    val path = Path()
                    points.forEachIndexed { index, point ->
                        val x = leftPadding + if (points.size == 1) plotWidth / 2 else (index * plotWidth / (points.lastIndex.coerceAtLeast(1)))
                        val yRatio = (point.y - minY) / range
                        val y = topPadding + (1f - yRatio) * plotHeight
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        
                        // 绘制点，选中的点更大
                        val isSelected = selectedPointIndex == index
                        drawCircle(
                            color = if (isSelected) Color(0xFF0D47A1) else Color(0xFF1E88E5),
                            radius = if (isSelected) 6.dp.toPx() else 4.dp.toPx(),
                            center = Offset(x, y)
                        )
                    }
                    drawPath(
                        path = path,
                        color = Color(0xFF1E88E5),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
            
            // 工具提示
            selectedPointIndex?.let { index ->
                tooltipPosition?.let { position ->
                    val point = points[index]
                    val density = LocalDensity.current
                    Card(
                        modifier = Modifier
                            .offset(
                                x = with(density) { position.x.toDp() } - 40.dp,
                                y = with(density) { position.y.toDp() } - 60.dp
                            )
                            .wrapContentSize(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = point.x,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (point.y == point.y.toLong().toFloat()) point.y.toLong().toString() else String.format("%.1f", point.y),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF1E88E5)
                            )
                        }
                    }
                }
            }
            
            // X轴标签行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 40.dp, top = 200.dp + 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                points.forEachIndexed { index, point ->
                    if (index == 0 || index == points.lastIndex || points.size <= 5 || index % (points.size / 4).coerceAtLeast(1) == 0) {
                        Text(
                            text = point.x,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun A2UiBarChart(
    title: String?,
    points: List<A2UiChartPoint>,
    modifier: Modifier = Modifier
) {
    val hasData = points.isNotEmpty()
    val maxY = (points.maxOfOrNull { it.y } ?: 0f).takeIf { it > 0f } ?: 1f
    
    // 状态管理：选中的条形和工具提示
    var selectedBarIndex by remember { mutableStateOf<Int?>(null) }
    var tooltipPosition by remember { mutableStateOf<Offset?>(null) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!title.isNullOrBlank()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
        }
        if (!hasData) {
            Text("No chart data", style = MaterialTheme.typography.bodySmall)
            return
        }
        
        // 图表区域布局
        Box {
            Row {
                // Y轴标签列 - 放在左侧
                Column(
                    modifier = Modifier
                        .width(40.dp)
                        .height(200.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (maxY == maxY.toLong().toFloat()) maxY.toLong().toString() else String.format("%.1f", maxY),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (maxY / 2 == (maxY / 2).toLong().toFloat()) (maxY / 2).toLong().toString() else String.format("%.1f", maxY / 2),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "0",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // 图表绘制区域
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(200.dp)
                        .pointerInput(points) {
                            detectTapGestures { offset ->
                                val leftPadding = 8.dp.toPx()
                                val rightPadding = 8.dp.toPx()
                                val topPadding = 8.dp.toPx()
                                val bottomPadding = 8.dp.toPx()
                                val plotWidth = (size.width - leftPadding - rightPadding).coerceAtLeast(1f)
                                val plotHeight = (size.height - topPadding - bottomPadding).coerceAtLeast(1f)
                                val slotWidth = plotWidth / points.size.coerceAtLeast(1)
                                val barWidth = slotWidth * 0.6f
                                
                                // 查找点击的条形
                                var clickedIndex = -1
                                
                                points.forEachIndexed { index, point ->
                                    val barHeight = (point.y / maxY) * plotHeight
                                    val x = leftPadding + index * slotWidth + (slotWidth - barWidth) / 2f
                                    val y = topPadding + plotHeight - barHeight
                                    
                                    if (offset.x >= x && offset.x <= x + barWidth && 
                                        offset.y >= y && offset.y <= y + barHeight) {
                                        clickedIndex = index
                                    }
                                }
                                
                                if (clickedIndex >= 0) {
                                    selectedBarIndex = clickedIndex
                                    tooltipPosition = offset
                                } else {
                                    selectedBarIndex = null
                                    tooltipPosition = null
                                }
                            }
                        }
                ) {
                    val leftPadding = 8.dp.toPx()
                    val rightPadding = 8.dp.toPx()
                    val topPadding = 8.dp.toPx()
                    val bottomPadding = 8.dp.toPx()
                    val plotWidth = (size.width - leftPadding - rightPadding).coerceAtLeast(1f)
                    val plotHeight = (size.height - topPadding - bottomPadding).coerceAtLeast(1f)
                    val slotWidth = plotWidth / points.size.coerceAtLeast(1)
                    val barWidth = slotWidth * 0.6f

                    // 绘制Y轴刻度线
                    val yLabels = listOf(0f, maxY / 2, maxY)
                    yLabels.forEach { label ->
                        val yRatio = label / maxY
                        val yPos = topPadding + (1f - yRatio) * plotHeight
                        
                        drawLine(
                            color = Color(0x11000000),
                            start = Offset(leftPadding, yPos),
                            end = Offset(leftPadding + plotWidth, yPos),
                            strokeWidth = 0.5.dp.toPx()
                        )
                    }
                    
                    // 绘制X轴和Y轴
                    drawLine(
                        color = Color(0x33000000),
                        start = Offset(leftPadding, topPadding + plotHeight),
                        end = Offset(leftPadding + plotWidth, topPadding + plotHeight),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = Color(0x33000000),
                        start = Offset(leftPadding, topPadding),
                        end = Offset(leftPadding, topPadding + plotHeight),
                        strokeWidth = 1.dp.toPx()
                    )

                    // 绘制条形
                    points.forEachIndexed { index, point ->
                        val barHeight = (point.y / maxY) * plotHeight
                        val x = leftPadding + index * slotWidth + (slotWidth - barWidth) / 2f
                        val y = topPadding + plotHeight - barHeight
                        
                        // 选中的条形颜色更深
                        val isSelected = selectedBarIndex == index
                        drawRoundRect(
                            color = if (isSelected) Color(0xFF2E7D32) else Color(0xFF43A047),
                            topLeft = Offset(x, y),
                            size = Size(barWidth, barHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx())
                        )
                    }
                }
            }
            
            // 工具提示
            selectedBarIndex?.let { index ->
                tooltipPosition?.let { position ->
                    val point = points[index]
                    val density = LocalDensity.current
                    Card(
                        modifier = Modifier
                            .offset(
                                x = with(density) { position.x.toDp() } - 40.dp,
                                y = with(density) { position.y.toDp() } - 60.dp
                            )
                            .wrapContentSize(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = point.x,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (point.y == point.y.toLong().toFloat()) point.y.toLong().toString() else String.format("%.1f", point.y),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF43A047)
                            )
                        }
                    }
                }
            }
            
            // X轴标签行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 40.dp, top = 200.dp + 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                points.forEachIndexed { index, point ->
                    if (index == 0 || index == points.lastIndex || points.size <= 5 || index % (points.size / 4).coerceAtLeast(1) == 0) {
                        Text(
                            text = point.x,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun A2UiPieChart(
    title: String?,
    slices: List<A2UiPieChartSlice>,
    modifier: Modifier = Modifier,
    showPercentageOnChart: Boolean = false
) {
    val total = slices.sumOf { it.value.toDouble() }.toFloat()
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!title.isNullOrBlank()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
        }
        if (slices.isEmpty() || total <= 0f) {
            Text("No chart data", style = MaterialTheme.typography.bodySmall)
            return
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
        ) {
            val diameter = size.minDimension * 0.75f
            val topLeft = Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f
            )
            val centerX = topLeft.x + diameter / 2
            val centerY = topLeft.y + diameter / 2
            val radius = diameter / 2
            
            var startAngle = -90f
            
            // 绘制饼图
            slices.forEach { slice ->
                val sweep = (slice.value / total) * 360f
                drawArc(
                    color = slice.color,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = true,
                    topLeft = topLeft,
                    size = Size(diameter, diameter)
                )
                
                // 在饼图上显示百分比标签
                if (showPercentageOnChart && sweep > 15f) {
                    val midAngle = Math.toRadians((startAngle + sweep / 2).toDouble())
                    val labelRadius = radius * 0.65f
                    val labelX = centerX + (labelRadius * kotlin.math.cos(midAngle)).toFloat()
                    val labelY = centerY + (labelRadius * kotlin.math.sin(midAngle)).toFloat()
                    val percentage = if (total > 0f) (slice.value / total * 100).toInt() else 0
                    
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 28f
                        isAntiAlias = true
                        textAlign = android.graphics.Paint.Align.CENTER
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                    
                    drawContext.canvas.nativeCanvas.drawText(
                        "$percentage%",
                        labelX,
                        labelY + paint.textSize / 3,
                        paint
                    )
                }
                
                startAngle += sweep
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            slices.forEach { slice ->
                val percentage = if (total > 0f) (slice.value / total * 100).toInt() else 0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(slice.color, RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${slice.label}: ${slice.value} ($percentage%)",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
internal fun A2UiCustomPaint(
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    backgroundColor: Color?,
    elements: List<A2UiPaintElement>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .width(width)
                .height(height)
        ) {
            // Draw background
            backgroundColor?.let {
                drawRect(color = it, size = size)
            }
            
            // Draw each element
            elements.forEach { element ->
                when (element.type.lowercase(Locale.ROOT)) {
                    "rect" -> {
                        val props = element.properties
                        val x = (props["x"] as? Number)?.toFloat() ?: 0f
                        val y = (props["y"] as? Number)?.toFloat() ?: 0f
                        val w = (props["width"] as? Number)?.toFloat() ?: size.width
                        val h = (props["height"] as? Number)?.toFloat() ?: size.height
                        
                        val strokeC = element.strokeColor?.let { parseA2UiHexColor(it) }
                        val strokeW = element.strokeWidth ?: 1f
                        
                        if (element.style == "fill" || element.fillColor != null) {
                            val fillC = element.fillColor?.let { parseA2UiHexColor(it) } ?: strokeC
                            fillC?.let { drawRect(color = it, topLeft = Offset(x, y), size = Size(w, h)) }
                        }
                        
                        strokeC?.let {
                            drawRect(
                                color = it,
                                topLeft = Offset(x, y),
                                size = Size(w, h),
                                style = Stroke(width = strokeW)
                            )
                        }
                    }
                    
                    "line" -> {
                        val props = element.properties
                        val x1 = (props["x1"] as? Number)?.toFloat() ?: 0f
                        val y1 = (props["y1"] as? Number)?.toFloat() ?: 0f
                        val x2 = (props["x2"] as? Number)?.toFloat() ?: size.width
                        val y2 = (props["y2"] as? Number)?.toFloat() ?: size.height
                        val strokeC = element.strokeColor?.let { parseA2UiHexColor(it) } ?: Color.Black
                        val strokeW = element.strokeWidth ?: 1f
                        
                        drawLine(
                            color = strokeC,
                            start = Offset(x1, y1),
                            end = Offset(x2, y2),
                            strokeWidth = strokeW,
                            cap = StrokeCap.Round
                        )
                    }
                    
                    "circle" -> {
                        val props = element.properties
                        val cx = (props["cx"] as? Number)?.toFloat() ?: 0f
                        val cy = (props["cy"] as? Number)?.toFloat() ?: 0f
                        val radius = (props["radius"] as? Number)?.toFloat() ?: 10f
                        
                        val strokeC = element.strokeColor?.let { parseA2UiHexColor(it) }
                        val fillC = element.fillColor?.let { parseA2UiHexColor(it) }
                        val strokeW = element.strokeWidth ?: 2f
                        
                        if (element.style == "fill" || fillC != null) {
                            fillC?.let { drawCircle(color = it, radius = radius, center = Offset(cx, cy)) }
                        } else {
                            strokeC?.let {
                                drawCircle(
                                    color = it,
                                    radius = radius,
                                    center = Offset(cx, cy),
                                    style = Stroke(width = strokeW)
                                )
                            }
                        }
                    }
                    
                    "path" -> {
                        val props = element.properties
                        val smooth = props["smooth"] as? Boolean ?: false
                        val pointsData = props["points"] as? List<*>
                        
                        if (pointsData != null && pointsData.isNotEmpty()) {
                            val points = pointsData.mapNotNull { point ->
                                val pointMap = point as? Map<*, *>
                                val px = (pointMap?.get("x") as? Number)?.toFloat()
                                val py = (pointMap?.get("y") as? Number)?.toFloat()
                                if (px != null && py != null) Offset(px, py) else null
                            }
                            
                            if (points.size >= 2) {
                                val path = Path()
                                val strokeC = element.strokeColor?.let { parseA2UiHexColor(it) } ?: Color.Blue
                                val strokeW = element.strokeWidth ?: 3f
                                
                                if (smooth && points.size >= 3) {
                                    // Draw smooth curve using quadratic bezier
                                    path.moveTo(points[0].x, points[0].y)
                                    for (i in 1 until points.size - 1) {
                                        val controlX = points[i].x
                                        val controlY = points[i].y
                                        val endX = (points[i].x + points[i + 1].x) / 2
                                        val endY = (points[i].y + points[i + 1].y) / 2
                                        path.quadraticTo(controlX, controlY, endX, endY)
                                    }
                                    // Draw to last point
                                    path.lineTo(points.last().x, points.last().y)
                                } else {
                                    // Draw straight lines
                                    path.moveTo(points[0].x, points[0].y)
                                    for (i in 1 until points.size) {
                                        path.lineTo(points[i].x, points[i].y)
                                    }
                                }
                                
                                drawPath(
                                    path = path,
                                    color = strokeC,
                                    style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            }
                        }
                    }
                    
                    "text" -> {
                        val props = element.properties
                        val text = props["text"]?.toString() ?: ""
                        val x = (props["x"] as? Number)?.toFloat() ?: 0f
                        val y = (props["y"] as? Number)?.toFloat() ?: 0f
                        val fontSize = (props["fontSize"] as? Number)?.toFloat() ?: 12f
                        val colorValue = (props["color"] as? String)?.let { parseA2UiHexColor(it) } ?: Color.Black
                        
                        // Parse fontWeight
                        val fontWeight = when (val fw = props["fontWeight"]?.toString()?.lowercase()) {
                            "bold", "700", "800", "900" -> android.graphics.Typeface.BOLD
                            "medium", "500" -> android.graphics.Typeface.DEFAULT
                            "semi-bold", "semibold", "600" -> android.graphics.Typeface.DEFAULT
                            "light", "300" -> android.graphics.Typeface.DEFAULT
                            "thin", "100" -> android.graphics.Typeface.DEFAULT
                            "normal", "400" -> android.graphics.Typeface.DEFAULT
                            else -> android.graphics.Typeface.DEFAULT
                        }
                        val style = if (props["fontWeight"]?.toString()?.lowercase() in listOf("bold", "700", "800", "900")) {
                            android.graphics.Typeface.BOLD
                        } else {
                            android.graphics.Typeface.NORMAL
                        }
                        
                        val paint = android.graphics.Paint().apply {
                            this.color = android.graphics.Color.argb(
                                (colorValue.alpha * 255).toInt(),
                                (colorValue.red * 255).toInt(),
                                (colorValue.green * 255).toInt(),
                                (colorValue.blue * 255).toInt()
                            )
                            textSize = fontSize * density
                            isAntiAlias = true
                            textAlign = android.graphics.Paint.Align.CENTER
                            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, style)
                        }
                        
                        val textHeight = paint.descent() - paint.ascent()
                        
                        drawContext.canvas.nativeCanvas.drawText(
                            text,
                            x,
                            y + textHeight / 4,
                            paint
                        )
                    }
                }
            }
        }
    }
}
