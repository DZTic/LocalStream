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
    val onDragStart: () -> Unit = {},
    val onDragEnd: () -> Unit = {},
)

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
        var isDrag = false
        var dragMode = 0
        var pointerActive = true
        var lastX = startX
        var lastY = down.position.y

        while (pointerActive) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointerId }
            if (change == null || !change.pressed) {
                pointerActive = false
                if (isDrag) {
                    callbacks.onDragEnd()
                } else {
                    lastTapTime = processTapRelease(startX, size.width.toFloat(), lastTapTime, lastTapX, callbacks)
                    lastTapX = startX
                }
            } else {
                val totalDx = change.position.x - startX
                val totalDy = change.position.y - down.position.y
                if (!isDrag && (abs(totalDx) > touchSlop || abs(totalDy) > touchSlop)) {
                    isDrag = true
                    dragMode = if (abs(totalDx) > abs(totalDy)) 1 else if (startX < size.width * 0.5f) 2 else 3
                    lastX = change.position.x
                    lastY = change.position.y
                    callbacks.onDragStart()
                }
                if (isDrag) {
                    val dx = change.position.x - lastX
                    val dy = change.position.y - lastY
                    lastX = change.position.x
                    lastY = change.position.y
                    change.consume()
                    dispatchDragEvent(dragMode, dx / size.width.toFloat(), dy / size.height.toFloat(), callbacks)
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
