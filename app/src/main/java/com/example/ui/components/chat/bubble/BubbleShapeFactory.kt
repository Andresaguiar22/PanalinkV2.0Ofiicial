package com.example.ui.components.chat.bubble

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * BubbleShapeFactory
 * Dynamic shape generator for Panalink Chat Premium 3.0 message bubbles.
 * Produces clean rounded corner shapes without tails based on message group position and sender status.
 */
@Immutable
object BubbleShapeFactory {

    /**
     * Acción 3: Forma sin colas (asymmetric RoundedCornerShape)
     * Tú: RoundedCornerShape(24.dp, 4.dp, 24.dp, 24.dp)
     * Otro: RoundedCornerShape(4.dp, 24.dp, 24.dp, 24.dp)
     */
    fun createShape(groupPosition: MessageGroupPosition, isMe: Boolean): Shape {
        return if (isMe) {
            RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = 20.dp,
                bottomEnd = 4.dp
            )
        } else {
            RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = 4.dp,
                bottomEnd = 20.dp
            )
        }
    }
}
