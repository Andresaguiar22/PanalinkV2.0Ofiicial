package com.example.live.domain.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Regalo del catálogo real (tabla public.live_gifts). */
@JsonClass(generateAdapter = true)
data class LiveGift(
    @Json(name = "code") val code: String,
    @Json(name = "name") val name: String,
    @Json(name = "emoji") val emoji: String,
    @Json(name = "coins") val coins: Int,
    @Json(name = "sort_order") val sortOrder: Int = 0
)

/** Envío de regalo persistido (tabla public.live_gift_events). */
@JsonClass(generateAdapter = true)
data class LiveGiftEvent(
    @Json(name = "id") val id: String,
    @Json(name = "stream_id") val streamId: String,
    @Json(name = "sender_id") val senderId: String,
    @Json(name = "gift_code") val giftCode: String,
    @Json(name = "quantity") val quantity: Int = 1,
    @Json(name = "coins_total") val coinsTotal: Long = 0
)

/** Resultado de public.live_send_gift. */
data class LiveGiftResult(
    val ok: Boolean,
    val balance: Int,
    val total: Int,
    val reason: String?
)
