package io.rovly.pitchee.ui.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
internal fun SwipeBackContainer(
    enabled: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val edgeWidthPx = with(density) { 28.dp.toPx() }
    val triggerDistancePx = with(density) { 72.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(enabled, onBack) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var tracking = down.position.x <= edgeWidthPx
                    var travel = 0f

                    while (tracking) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed) break

                        if (change.pressed) {
                            val delta = change.position.x - change.previousPosition.x
                            if (delta > 0f) {
                                travel += delta
                                change.consume()
                                if (travel >= triggerDistancePx) {
                                    tracking = false
                                    onBack()
                                }
                            } else if (delta < 0f) {
                                travel = (travel + delta).coerceAtLeast(0f)
                            }
                        } else {
                            break
                        }
                    }
                }
            },
    ) {
        content()
    }
}
