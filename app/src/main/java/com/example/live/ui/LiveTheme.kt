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
val LiveNeon = Color(0xFF0A84FF)

/** Texto/icono oscuro que se lee sobre [LiveNeon]. */
val LiveOnNeon = Color(0xFFFFFFFF)

/** Base nocturna azulada del fondo (no negro puro). */
val LiveNightBase = Color(0xFF000000)

/** Rojo del indicador de directo y su halo. */
val LiveLiveRed = Color(0xFFFF3B4E)
val LiveLiveGlow = Color(0xFFFF28C8)

/** Relleno y borde translúcidos de las superficies "glass". */
val LiveGlassFill = Color(0xFF131A22).copy(alpha = 0.82f)
val LiveGlassBorder = Color(0xFF0A84FF).copy(alpha = 0.35f)

/** Scrim oscuro que se pinta sobre las miniaturas para que el texto se lea. */
val LiveCardScrim = Color(0xFF0D0F12)

/** Fondo translúcido de los badges flotantes sobre las miniaturas. */
val LiveBadgeFill = Color(0xFF131A22).copy(alpha = 0.78f)

/**
 * Fondo translúcido del HUD del directo (píldora de estado y botones circulares).
 *
 * Es oscuro a propósito: el HUD va encima del video de cámara, y un relleno claro
 * dejaría el texto blanco sin contraste cuando la escena es brillante.
 */
val LiveHudFill = Color(0xFF131A22).copy(alpha = 0.78f)

/** Borde fino y claro del HUD: separa la superficie flotante del video de fondo. */
val LiveHudBorder = Color(0xFF9B5CFF).copy(alpha = 0.55f)

/** Rojo corporativo de alerta: botón "FINALIZAR" y su halo exterior. */
val LiveEndRed = Color(0xFFFF28C8)

/** Esquinas de las tarjetas del listado y del panel de configuración. */
val LiveCardShape = RoundedCornerShape(24.dp)
