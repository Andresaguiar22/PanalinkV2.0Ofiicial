package com.example.live.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Scope con vida de APPLICACION (sobrevive al pop de la pantalla del directo).
 *
 * PROBLEMA que resuelve: al finalizar un directo, la navegacion hace pop de
 * `LiveBroadcastScreen` y Compose cancela su `rememberCoroutineScope()`. Si el PATCH
 * que marca `live_streams.status='ENDED'` se lanza en ese scope, se cancela A MITAD
 * de vuelo y el stream queda "fantasma" (LIVE para siempre) en el feed, con el
 * espectador viendo una sala que ya no existe. Por eso el teardown (endLive +
 * leaveRoom) se despacha aqui.
 */
object LiveCleanupScope {
    val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}