package com.example.live.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Tokens visuales compartidos por el módulo Live (transmitir + listado).
 *
 * Los colores están muestreados de los mockups aprobados para que ambas pantallas
 * hablen el mismo idioma: verde neón de marca, base nocturna azulada y capas
 * translúcidas tipo glassmorphism.
 */

/** Verde neón de marca: CTA, anillo del avatar del host y FAB. */
val LiveNeon = Color(0xFF5CF8B0)

/** Texto/icono oscuro que se lee sobre [LiveNeon]. */
val LiveOnNeon = Color(0xFF03140C)

/** Base nocturna azulada del fondo (no negro puro). */
val LiveNightBase = Color(0xFF040A15)

/** Rojo del indicador de directo y su halo. */
val LiveLiveRed = Color(0xFFFF3B4E)
val LiveLiveGlow = Color(0xFFFF7A88)

/** Relleno y borde translúcidos de las superficies "glass". */
val LiveGlassFill = Color.White.copy(alpha = 0.06f)
val LiveGlassBorder = Color.White.copy(alpha = 0.10f)

/** Scrim oscuro que se pinta sobre las miniaturas para que el texto se lea. */
val LiveCardScrim = Color(0xFF0B1017)

/** Fondo translúcido de los badges flotantes sobre las miniaturas. */
val LiveBadgeFill = Color(0xFF10151A).copy(alpha = 0.55f)

/**
 * Fondo translúcido del HUD del directo (píldora de estado y botones circulares).
 *
 * Es oscuro a propósito: el HUD va encima del video de cámara, y un relleno claro
 * dejaría el texto blanco sin contraste cuando la escena es brillante.
 */
val LiveHudFill = Color(0xFF10151A).copy(alpha = 0.55f)

/** Borde fino y claro del HUD: separa la superficie flotante del video de fondo. */
val LiveHudBorder = Color.White.copy(alpha = 0.26f)

/** Rojo corporativo de alerta: botón "FINALIZAR" y su halo exterior. */
val LiveEndRed = Color(0xFFEF5350)

/** Esquinas de las tarjetas del listado y del panel de configuración. */
val LiveCardShape = RoundedCornerShape(24.dp)
