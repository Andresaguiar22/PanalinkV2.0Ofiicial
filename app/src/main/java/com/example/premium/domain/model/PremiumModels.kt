package com.example.premium.domain.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Modelos del dominio Premium 2.0.
 *
 * Los JSON de respuesta de los RPC de Supabase se parsean con Moshi
 * (patrón ya usado en live_engagement). Las claves de solo-lectura
 * se mapean a data classes tipadas.
 */

/** Feature premium (entitlement que el usuario tiene ACTIVO). */
@JsonClass(generateAdapter = true)
data class Entitlement(
    @Json(name = "id") val id: String,
    @Json(name = "feature_key") val featureKey: String,
    @Json(name = "product_code") val productCode: String,
    @Json(name = "name") val name: String,
    @Json(name = "emoji") val emoji: String,
    @Json(name = "starts_at") val startsAt: String,
    @Json(name = "expires_at") val expiresAt: String,
    @Json(name = "status") val status: String,
    @Json(name = "days_left") val daysLeft: Int
) {
    val isActive: Boolean get() = status == "active" && daysLeft > 0
}

/** Producto del catálogo premium (comprar con monedas). */
@JsonClass(generateAdapter = true)
data class PremiumProduct(
    @Json(name = "code") val code: String,
    @Json(name = "feature_key") val featureKey: String,
    @Json(name = "name") val name: String,
    @Json(name = "emoji") val emoji: String,
    @Json(name = "description") val description: String?,
    @Json(name = "duration_days") val durationDays: Int,
    @Json(name = "price_coins") val priceCoins: Int,
    @Json(name = "trial_days") val trialDays: Int,
    @Json(name = "sort_order") val sortOrder: Int,
    @Json(name = "feature_enabled") val featureEnabled: Boolean
)

/** Feature (flag) de premium, para gating y catálogo. */
@JsonClass(generateAdapter = true)
data class PremiumFeatureInfo(
    @Json(name = "feature_key") val featureKey: String,
    @Json(name = "display_name") val displayName: String,
    @Json(name = "description") val description: String?,
    @Json(name = "icon") val icon: String?,
    @Json(name = "enabled") val enabled: Boolean,
    @Json(name = "minimum_level") val minimumLevel: Int,
    @Json(name = "trial_days") val trialDays: Int
)

/** Saldo completo del usuario (coins/diamonds/tickets/xp/nivel). */
@JsonClass(generateAdapter = true)
data class WalletBalance(
    @Json(name = "coins") val coins: Int = 0,
    @Json(name = "diamonds") val diamonds: Int = 0,
    @Json(name = "tickets") val tickets: Int = 0,
    @Json(name = "xp") val xp: Int = 0,
    @Json(name = "level") val level: Int = 1
)

/** Resultado de una compra premium. */
@JsonClass(generateAdapter = true)
data class PremiumBuyResult(
    @Json(name = "ok") val ok: Boolean,
    @Json(name = "balance") val balance: Int? = null,
    @Json(name = "expires_at") val expiresAt: String? = null,
    @Json(name = "entitlement_id") val entitlementId: String? = null,
    @Json(name = "feature_key") val featureKey: String? = null,
    @Json(name = "reason") val reason: String? = null
)

/** Promoción activa (banner/oferta). */
@JsonClass(generateAdapter = true)
data class PremiumPromotion(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String,
    @Json(name = "subtitle") val subtitle: String?,
    @Json(name = "description") val description: String?,
    @Json(name = "image_url") val imageUrl: String?,
    @Json(name = "banner_url") val bannerUrl: String?,
    @Json(name = "action_type") val actionType: String,
    @Json(name = "action_value") val actionValue: String?,
    @Json(name = "feature_key") val featureKey: String?,
    @Json(name = "price_coins") val priceCoins: Int?,
    @Json(name = "discount_percent") val discountPercent: Int,
    @Json(name = "ends_at") val endsAt: String,
    @Json(name = "priority") val priority: Int
)

/** Evento temporal activo (multiplicadores, etc.). */
@JsonClass(generateAdapter = true)
data class PremiumEvent(
    @Json(name = "code") val code: String,
    @Json(name = "title") val title: String,
    @Json(name = "subtitle") val subtitle: String?,
    @Json(name = "description") val description: String?,
    @Json(name = "emoji") val emoji: String?,
    @Json(name = "image_url") val imageUrl: String?,
    @Json(name = "action_type") val actionType: String,
    @Json(name = "action_value") val actionValue: String?,
    @Json(name = "starts_at") val startsAt: String,
    @Json(name = "ends_at") val endsAt: String,
    @Json(name = "multiplier_coins") val multiplierCoins: Double,
    @Json(name = "multiplier_xp") val multiplierXp: Double
)

/** Misión (diaria/semanal) con progreso. */
@JsonClass(generateAdapter = true)
data class Mission(
    @Json(name = "mission_id") val missionId: String,
    @Json(name = "code") val code: String,
    @Json(name = "title") val title: String,
    @Json(name = "description") val description: String?,
    @Json(name = "scope") val scope: String,
    @Json(name = "reward_currency") val rewardCurrency: String,
    @Json(name = "reward_amount") val rewardAmount: Int,
    @Json(name = "target") val target: Int,
    @Json(name = "progress") val progress: Int,
    @Json(name = "completed") val completed: Boolean,
    @Json(name = "claimed") val claimed: Boolean
)

/** Estado de recompensa diaria / racha. */
@JsonClass(generateAdapter = true)
data class DailyRewardStatus(
    @Json(name = "streak") val streak: Int = 0,
    @Json(name = "claimed_today") val claimedToday: Boolean = false,
    @Json(name = "can_claim_yesterday") val canClaimYesterday: Boolean = false
)

/** Resultado de reclamar la recompensa diaria. */
@JsonClass(generateAdapter = true)
data class DailyRewardClaim(
    @Json(name = "ok") val ok: Boolean,
    @Json(name = "day") val day: Int? = null,
    @Json(name = "streak") val streak: Int? = null,
    @Json(name = "currency") val currency: String? = null,
    @Json(name = "amount") val amount: Int? = null,
    @Json(name = "reason") val reason: String? = null
)

/** Notificación in-app. */
@JsonClass(generateAdapter = true)
data class PremiumNotification(
    @Json(name = "id") val id: String,
    @Json(name = "category") val category: String,
    @Json(name = "priority") val priority: String,
    @Json(name = "title") val title: String,
    @Json(name = "body") val body: String?,
    @Json(name = "payload") val payload: Any? = null,
    @Json(name = "is_read") val isRead: Boolean,
    @Json(name = "created_at") val createdAt: String
)

/** Envuelve una respuesta RPC genérica que devuelve objeto + arrays. */
@JsonClass(generateAdapter = true)
data class PremiumCatalogResponse(
    @Json(name = "ok") val ok: Boolean,
    @Json(name = "products") val products: List<PremiumProduct> = emptyList()
)

@JsonClass(generateAdapter = true)
data class PremiumEntitlementsResponse(
    @Json(name = "ok") val ok: Boolean,
    @Json(name = "entitlements") val entitlements: List<Entitlement> = emptyList()
)

@JsonClass(generateAdapter = true)
data class PremiumPromotionsResponse(
    @Json(name = "ok") val ok: Boolean,
    @Json(name = "promotions") val promotions: List<PremiumPromotion> = emptyList()
)

@JsonClass(generateAdapter = true)
data class PremiumEventsResponse(
    @Json(name = "ok") val ok: Boolean,
    @Json(name = "events") val events: List<PremiumEvent> = emptyList()
)

@JsonClass(generateAdapter = true)
data class MissionsResponse(
    @Json(name = "ok") val ok: Boolean,
    @Json(name = "missions") val missions: List<Mission> = emptyList()
)

@JsonClass(generateAdapter = true)
data class NotificationsResponse(
    @Json(name = "ok") val ok: Boolean,
    @Json(name = "notifications") val notifications: List<PremiumNotification> = emptyList()
)

@JsonClass(generateAdapter = true)
data class WalletResponse(
    @Json(name = "ok") val ok: Boolean,
    @Json(name = "coins") val coins: Int = 0,
    @Json(name = "diamonds") val diamonds: Int = 0,
    @Json(name = "tickets") val tickets: Int = 0,
    @Json(name = "xp") val xp: Int = 0,
    @Json(name = "level") val level: Int = 1
)

@JsonClass(generateAdapter = true)
data class MissionResult(
    @Json(name = "ok") val ok: Boolean,
    @Json(name = "progress") val progress: Int? = null,
    @Json(name = "target") val target: Int? = null,
    @Json(name = "completed") val completed: Boolean? = null,
    @Json(name = "claimed") val claimed: Boolean? = null,
    @Json(name = "reason") val reason: String? = null
)

@JsonClass(generateAdapter = true)
data class MissionClaimAllResult(
    @Json(name = "ok") val ok: Boolean,
    @Json(name = "claimed") val claimed: List<MissionClaimed> = emptyList()
)

@JsonClass(generateAdapter = true)
data class MissionClaimed(
    @Json(name = "mission") val mission: String,
    @Json(name = "title") val title: String,
    @Json(name = "reward") val reward: Int,
    @Json(name = "balance") val balance: Int
)

@JsonClass(generateAdapter = true)
data class ExchangeResult(
    @Json(name = "ok") val ok: Boolean,
    @Json(name = "coins") val coins: Int? = null,
    @Json(name = "reason") val reason: String? = null
)

@JsonClass(generateAdapter = true)
data class SimpleResult(
    @Json(name = "ok") val ok: Boolean,
    @Json(name = "reason") val reason: String? = null
)

/** Transacción del ledger para el WalletScreen. */
@JsonClass(generateAdapter = true)
data class WalletTransaction(
    @Json(name = "id") val id: String,
    @Json(name = "kind") val kind: String,
    @Json(name = "currency") val currency: String,
    @Json(name = "amount") val amount: Int,
    @Json(name = "balance_after") val balanceAfter: Int,
    @Json(name = "description") val description: String?,
    @Json(name = "ref_type") val refType: String?,
    @Json(name = "created_at") val createdAt: String
)

/** Respuesta de wallet_history(). */
@JsonClass(generateAdapter = true)
data class WalletHistoryResponse(
    @Json(name = "ok") val ok: Boolean = false,
    @Json(name = "wallet") val wallet: WalletBalance? = null,
    @Json(name = "transactions") val transactions: List<WalletTransaction> = emptyList(),
    @Json(name = "count") val count: Int = 0
)

/** Cosmético poseído por el usuario. */
@JsonClass(generateAdapter = true)
data class OwnedCosmetic(
    @Json(name = "cosmetic_code") val cosmeticCode: String,
    @Json(name = "source_level") val sourceLevel: Int?,
    @Json(name = "acquired_at") val acquiredAt: String?
)

/** Cosmético desbloqueable ya (por nivel alcanzado) pero aún no reclamado. */
@JsonClass(generateAdapter = true)
data class UnlockedCosmetic(
    @Json(name = "level") val level: Int,
    @Json(name = "cosmetic_code") val cosmeticCode: String
)

/** Respuesta de my_cosmetics(). */
@JsonClass(generateAdapter = true)
data class MyCosmeticsResponse(
    @Json(name = "ok") val ok: Boolean = false,
    @Json(name = "level") val level: Int = 1,
    @Json(name = "equipped") val equipped: String? = null,
    @Json(name = "owned") val owned: List<OwnedCosmetic> = emptyList(),
    @Json(name = "upgradable_now") val upgradableNow: List<UnlockedCosmetic> = emptyList()
)

/** Tier de nivel con recompensa. */
@JsonClass(generateAdapter = true)
data class LevelTierInfo(
    @Json(name = "level") val level: Int,
    @Json(name = "title") val title: String,
    @Json(name = "emoji") val emoji: String,
    @Json(name = "reward_currency") val rewardCurrency: String?,
    @Json(name = "reward_amount") val rewardAmount: Int,
    @Json(name = "cosmetic_code") val cosmeticCode: String?
)

/** Información de nivel actual + próximos hitos. */
@JsonClass(generateAdapter = true)
data class LevelInfo(
    @Json(name = "ok") val ok: Boolean = false,
    @Json(name = "level") val level: Int = 1,
    @Json(name = "xp") val xp: Int = 0,
    @Json(name = "xp_for_next") val xpForNext: Int = 100,
    @Json(name = "current") val current: LevelTierInfo? = null,
    @Json(name = "next") val next: LevelTierInfo? = null,
    @Json(name = "level_tiers") val levelTiers: List<LevelTierInfo> = emptyList()
)