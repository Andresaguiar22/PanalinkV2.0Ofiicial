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
    cancelThresholdX: Float = -240f, // ≈ mitad de la píldora: se elimina a mitad del recorrido al bote
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
        // Selector de eje: una vez que el dedo decide dirección, se fija en ese
        // eje para que el micrófono siga SIEMPRE una sola línea recta (sin
        // zigzag). null = sin decidir aún; 0 = vertical (lock); 1 = horizontal (delete).
        var axis: Int? = null

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull() ?: break
            if (!change.pressed) { released = true; break }

            change.consume()
            val pos = change.position
            val prev = change.previousPosition
            totalY += (pos.y - prev.y)
            totalX += (pos.x - prev.x)

            // Decidir eje dominante (si aún no se decidió).
            if (axis == null) {
                val ay = kotlin.math.abs(totalY)
                val ax = kotlin.math.abs(totalX)
                if (ay > ax * 1.5f) {
                    axis = 0 // vertical (hacia el candado)
                } else if (ax > ay * 1.5f) {
                    axis = 1 // horizontal (hacia el bote)
                }
            }

            var outX = totalX
            var outY = totalY
            if (axis == 0) {
                outX = 0f
            } else if (axis == 1) {
                outY = 0f
            } else {
                // Aún sin decidir: lineal puro sin jitter; totalX y totalY ya son
                // proporcionales al gesto real, sin smoothing que cause deriva.
            }

            val clampedY = outY.coerceIn(-1600f, 0f)
            val clampedX = outX.coerceIn(cancelThresholdX * 1.2f, 0f)
            onDrag?.invoke(clampedX, clampedY)

            // Lock: SOLO por la distancia recorrida hasta el candado (lockThresholdY)
            // y en eje vertical decidido.
            if (axis == 0 && outY <= lockThresholdY && !handled) {
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
            // Cancel/Delete: deslizar a la izquierda hasta la mitad de la píldora.
            if (axis == 1 && outX <= cancelThresholdX && !handled) {
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
