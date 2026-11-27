package com.yhchat.canary.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.abs

/** Adapted from Oolong PhotoViewerOverlay: down-drag wins only after vertical touch slop.
 * Horizontal movement remains available to the pager and two fingers remain available to zoom.
 */
internal fun Modifier.viewerDismissGesture(
    enabled: () -> Boolean,
    onDrag: (Offset) -> Unit,
    onRelease: (dismiss: Boolean) -> Unit
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (!enabled()) return@awaitEachGesture
        val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
        var total = Offset.Zero
        var dragging = false
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } > 1) break
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                tracker.addPosition(change.uptimeMillis, change.position)
                if (!change.pressed) {
                    if (dragging) {
                        change.consume()
                        val dismiss = total.y > 96 * density || (total.y > viewConfiguration.touchSlop && tracker.calculateVelocity().y > 900 * density)
                        dragging = false
                        onRelease(dismiss)
                    }
                    break
                }
                if (change.isConsumed && !dragging) break
                total += change.positionChange()
                if (!dragging) {
                    val slop = viewConfiguration.touchSlop
                    if (total.y > slop && total.y > abs(total.x) * 1.5f) dragging = true
                    else if (abs(total.x) > slop || total.y < -slop) break
                }
                if (dragging) {
                    change.consume()
                    onDrag(Offset(total.x, total.y.coerceAtLeast(0f)))
                }
            }
        } finally {
            if (dragging) onRelease(false)
        }
    }
}
