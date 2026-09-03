package com.example.ui.components.chat.voice

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

fun Modifier.voiceGestureDetector(
    enabled: Boolean = true,
    isLocked: Boolean = false,
    lockThresholdY: Float = -70f,      // ~50dp: tirón rápido hacia arriba bloquea
    cancelThresholdX: Float = -70f,    // ~50dp: deslizar a la izquierda cancela
    flingVelocityY: Float = -1.8f,     // px/ms hacia arriba para fling-lock (incluso en 1 frame)
    onPermissionRequired: (() -> Unit)? = null,
    onDrag: ((offsetX: Float, offsetY: Float) -> Unit)? = null,
    onEvent: (VoiceGestureEvent) -> Unit
): Modifier = if (!enabled) this else this.pointerInput(enabled, isLocked) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (isLocked) return@awaitEachGesture

        if (onPermissionRequired != null) {
            onPermissionRequired()
            // consume release
            while (true) {
                val ev = awaitPointerEvent()
                if (ev.changes.none { it.pressed }) break
            }
            return@awaitEachGesture
        }

        down.consume()

        // Touch-and-hold: 200ms
        val isLongPress = withTimeoutOrNull(200L) {
            while (true) {
                val ev = awaitPointerEvent()
                if (ev.changes.any { !it.pressed }) return@withTimeoutOrNull false
            }
            true
        } ?: true
        if (!isLongPress) return@awaitEachGesture

        onEvent(VoiceGestureEvent.StartRecording)

        var totalY = 0f
        var totalX = 0f
        var lastY = down.position.y
        var lastTime = System.nanoTime()
        var recentVelY = 0f
        var handled = false
        var released = false

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull() ?: break
            val now = System.nanoTime()
            val dt = (now - lastTime) / 1_000_000f
            if (!change.pressed) { released = true; break }

            val dy = change.position.y - lastY
            lastY = change.position.y
            lastTime = now
            if (dt > 0.5f) {
                val instVel = dy / dt
                recentVelY = recentVelY * 0.4f + instVel * 0.6f
            }

            change.consume()
            val pos = change.position
            val prev = change.previousPosition
            totalY += (pos.y - prev.y)
            totalX += (pos.x - prev.x)

            val clampedY = totalY.coerceIn(-350f, 0f)
            val clampedX = totalX.coerceIn(-450f, 0f)
            onDrag?.invoke(clampedX, clampedY)

            // Lock: por distancia recorrida O un fling claramente intencional. Antes un
            // mini-flick de ~20px a velocidad -1.8 disparaba el lock accidentalmente,
            // lo que hacia dificil ver el recorrido del candado.
            val flickUp = recentVelY < flingVelocityY * 1.7f && totalY < -30f
            if ((totalY < lockThresholdY || flickUp) && !handled) {
                handled = true
                onDrag?.invoke(0f, 0f)
                onEvent(VoiceGestureEvent.LockRecording)
                // consume hasta que suelte para no disparar Finish
                while (true) {
                    val ev = awaitPointerEvent()
                    if (ev.changes.none { it.pressed }) break
                }
                released = true
                break
            }
            // Cancel: deslizar a la izquierda
            if (totalX < cancelThresholdX && !handled) {
                handled = true
                onDrag?.invoke(0f, 0f)
                onEvent(VoiceGestureEvent.CancelRecording)
                while (true) {
                    val ev = awaitPointerEvent()
                    if (ev.changes.none { it.pressed }) break
                }
                released = true
                break
            }
        }

        onDrag?.invoke(0f, 0f)
        if (!handled && released) {
            onEvent(VoiceGestureEvent.FinishRecording)
        }
    }
}
