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
     * Estilo iMessage puro.
     * - SINGLE/FIRST: 3 esquinas redondeadas + cola (4.dp) en el lado del emisor.
     * - MIDDLE/LAST: la esquina SUPERIOR del lado del emisor también se afila,
     *   porque arriba se conecta con el mensaje anterior del mismo bloque
     *   (efecto "pegado" tipico de iOS).  Solo quedan redondeadas las
     *   esquinas exteriores del bloque.
     * Mias: cola en bottomEnd / conexion en topEnd (lado derecho).
     * Entrantes: cola en bottomStart / conexion en topStart (lado izquierdo).
     */
    fun createShape(groupPosition: MessageGroupPosition, isMe: Boolean): Shape {
        return if (isMe) {
            when (groupPosition) {
                MessageGroupPosition.MIDDLE,
                MessageGroupPosition.LAST -> RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 4.dp,
                    bottomStart = 18.dp,
                    bottomEnd = 4.dp
                )
                else -> RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = 18.dp,
                    bottomEnd = 4.dp
                )
            }
        } else {
            when (groupPosition) {
                MessageGroupPosition.MIDDLE,
                MessageGroupPosition.LAST -> RoundedCornerShape(
                    topStart = 4.dp,
                    topEnd = 18.dp,
                    bottomStart = 4.dp,
                    bottomEnd = 18.dp
                )
                else -> RoundedCornerShape(
                    topStart = 4.dp,
                    topEnd = 18.dp,
                    bottomStart = 18.dp,
                    bottomEnd = 18.dp
                )
            }
        }
    }
}
