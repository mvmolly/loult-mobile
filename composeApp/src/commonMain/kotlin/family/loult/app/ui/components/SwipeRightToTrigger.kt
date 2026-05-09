package family.loult.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Wraps [content] so that a horizontal right-swipe past [threshold] fires
 * [onTrigger]. The row never actually gets removed; it animates back to its
 * resting position. [background] is rendered beneath the content during the
 * drag, useful for an action hint (icon + label).
 */
@Composable
fun SwipeRightToTrigger(
    onTrigger: () -> Unit,
    modifier: Modifier = Modifier,
    threshold: Dp = 96.dp,
    background: @Composable (progress: Float) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val thresholdPx = with(density) { threshold.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.matchParentSize()) {
            val progress = (offsetX.value / thresholdPx).coerceIn(0f, 1f)
            background(progress)
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(offsetX.value.toInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                offsetX.snapTo((offsetX.value + dragAmount).coerceAtLeast(0f))
                            }
                        },
                        onDragEnd = {
                            val fired = offsetX.value >= thresholdPx
                            scope.launch { offsetX.animateTo(0f) }
                            if (fired) onTrigger()
                        },
                        onDragCancel = {
                            scope.launch { offsetX.animateTo(0f) }
                        },
                    )
                },
        ) {
            content()
        }
    }
}
