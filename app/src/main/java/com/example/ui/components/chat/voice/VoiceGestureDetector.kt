package com.example.ui.components.chat.voice

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull

fun Modifier.voiceGestureDetector(
    enabled: Boolean = true,
    isLocked: Boolean = false,
    lockThresholdY: Float = -240f,   // ≈ hasta el candado visible: solo se activa al subir del todo
    cancelThresholdX: Float = -70f,  // ~50dp: deslizar a la izquierda cancela
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
        var handled = false
        var released = false

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull() ?: break
            if (!change.pressed) { released = true; break }

            change.consume()
            val pos = change.position
            val prev = change.previousPosition
            totalY += (pos.y - prev.y)
            totalX += (pos.x - prev.x)

            val clampedY = totalY.coerceIn(-1600f, 0f)
            val clampedX = totalX.coerceIn(-450f, 0f)
            onDrag?.invoke(clampedX, clampedY)

            // Lock: SOLO por la distancia recorrida hasta el candado (lockThresholdY).
            // Sin shortcut por velocidad: antes un mini-flick de ~30px disparaba el
            // lock sin alcanzar el candado, y el usuario nunca veía el recorrido.
            if (totalY <= lockThresholdY && !handled) {
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
