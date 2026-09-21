package com.example.premium.domain

/**
 * Reglas de acceso premium reutilizables.
 *
 * Evita duplicar la lógica "¿tiene la feature? sí -> permitir; no -> bloquear"
 * en cada pantalla. Cada feature decide qué bloquea:
 *
 *  - CREAR (story/reel/post/live/sala): acción premium de creación.
 *  - USAR (ver/consumir): SIEMPRE permitido (freemium).
 *
 * Uso:
 *   PremiumAccess.run(PremiumFeatures.STORY,
 *       allowed = { abrirEditor() },
 *       blocked = { irAlCentroPremium() })
 */
object PremiumAccess {

    /** Ejecuta [allowed] si el usuario tiene la feature; si no, [blocked]. */
    fun run(
        feature: String,
        allowed: () -> Unit,
        blocked: () -> Unit
    ) {
        if (PremiumManager.hasFeature(feature)) allowed() else blocked()
    }

    /** Comprueba si la feature está activa (útil en composables). */
    fun has(feature: String): Boolean = PremiumManager.hasFeature(feature)
}