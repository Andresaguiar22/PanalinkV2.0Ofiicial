package com.example.live.domain.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * DTOs de request para las funciones RPC de Supabase.
 *
 * Moshi NO soporta `Map<String, Any>` como `@Body` de Retrofit (error
 * "Parameter type must not include a type variable or wildcard"). Estas
 * data classes tipadas permiten serializar correctamente los parámetros.
 */

@JsonClass(generateAdapter = true)
data class LiveSendGiftRequest(
    @Json(name = "p_stream_id") val streamId: String,
    @Json(name = "p_gift_code") val giftCode: String,
    @Json(name = "p_quantity") val quantity: Int
)

@JsonClass(generateAdapter = true)
data class LiveSendLikeRequest(
    @Json(name = "p_stream_id") val streamId: String,
    @Json(name = "p_quantity") val quantity: Int
)

/** live_wallet_balance no requiere parámetros; body vacío. */
@JsonClass(generateAdapter = true)
data class LiveWalletBalanceRequest(
    @Json(name = "p_user_id") val userId: String? = null
)

@JsonClass(generateAdapter = true)
data class LiveSetViewerCountRequest(
    @Json(name = "p_stream_id") val streamId: String,
    @Json(name = "p_count") val count: Int
)

@JsonClass(generateAdapter = true)
data class LiveHeartbeatRequest(
    @Json(name = "p_stream_id") val streamId: String
)

@JsonClass(generateAdapter = true)
data class LiveJoinStreamRequest(
    @Json(name = "p_stream_id") val streamId: String
)