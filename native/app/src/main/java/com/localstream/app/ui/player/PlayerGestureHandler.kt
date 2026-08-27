@file:Suppress("MatchingDeclarationName")

package com.localstream.app.ui.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerInputScope
import kotlin.math.abs

data class PlayerGestureCallbacks(
    val onTap: () -> Unit,
    val onDoubleTapLeft: () -> Unit,
    val onDoubleTapRight: () -> Unit,
    val onVerticalDragLeft: (Float) -> Unit,
    val onVerticalDragRight: (Float) -> Unit,
    val onHorizontalDrag: (Float) -> Unit,
    val onLongPressStart: () -> Unit = {},
    val onLongPressEnd: () -> Unit = {},
    val onDragStart: () -> Unit = {},
    val onDragEnd: () -> Unit = {},
)

private const val LONG_PRESS_TIMEOUT_MS = 500L

@Suppress("CyclomaticComplexMethod", "LongMethod")
suspend fun PointerInputScope.detectPlayerGestures(
    callbacks: PlayerGestureCallbacks,
) {
    var lastTapTime = 0L
    var lastTapX = 0f

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val startX = down.position.x
        val touchSlop = viewConfiguration.touchSlop
        val pointerId = down.id
        val downTime = System.currentTimeMillis()
        var isDrag = false
        var isLongPress = false
        var dragMode = 0
        var pointerActive = true

        while (pointerActive) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointerId }
            if (change == null || !change.pressed) {
                pointerActive = false
                if (isLongPress) {
                    callbacks.onLongPressEnd()
                } else if (isDrag) {
                    callbacks.onDragEnd()
                } else {
                    lastTapTime = processTapRelease(startX, size.width.toFloat(), lastTapTime, lastTapX, callbacks)
                    lastTapX = startX
                }
            } else {
                val totalDx = change.position.x - startX
                val totalDy = change.position.y - down.position.y
                val movedPastSlop = abs(totalDx) > touchSlop || abs(totalDy) > touchSlop

                if (!isDrag && !isLongPress && !movedPastSlop) {
                    if (System.currentTimeMillis() - downTime >= LONG_PRESS_TIMEOUT_MS) {
                        isLongPress = true
                        callbacks.onLongPressStart()
                    }
                }

                if (!isDrag && !isLongPress && movedPastSlop) {
                    isDrag = true
                    dragMode = if (abs(totalDx) > abs(totalDy)) 1 else if (startX < size.width * 0.5f) 2 else 3
                    callbacks.onDragStart()
                }
                if (isDrag) {
                    change.consume()
                    dispatchDragEvent(dragMode, totalDx / size.width.toFloat(), totalDy / size.height.toFloat(), callbacks)
                }
            }
        }
    }
}

fun processTapRelease(
    startX: Float,
    width: Float,
    lastTapTime: Long,
    lastTapX: Float,
    callbacks: PlayerGestureCallbacks,
): Long {
    val currentTime = System.currentTimeMillis()
    val isDoubleTap = currentTime - lastTapTime < 300L && abs(startX - lastTapX) < 150f
    if (isDoubleTap) {
        dispatchDoubleTap(startX, width, callbacks)
        return 0L
    } else {
        callbacks.onTap()
        return currentTime
    }
}

fun dispatchDoubleTap(
    startX: Float,
    width: Float,
    callbacks: PlayerGestureCallbacks,
) {
    if (startX < width * 0.4f) {
        callbacks.onDoubleTapLeft()
    } else if (startX > width * 0.6f) {
        callbacks.onDoubleTapRight()
    } else {
        callbacks.onTap()
    }
}

fun dispatchDragEvent(
    dragMode: Int,
    ratioX: Float,
    ratioY: Float,
    callbacks: PlayerGestureCallbacks,
) {
    when (dragMode) {
        1 -> callbacks.onHorizontalDrag(ratioX)
        2 -> callbacks.onVerticalDragLeft(-ratioY)
        3 -> callbacks.onVerticalDragRight(-ratioY)
    }
}
