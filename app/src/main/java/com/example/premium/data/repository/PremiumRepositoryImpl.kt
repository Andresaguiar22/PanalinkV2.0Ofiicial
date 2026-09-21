package com.example.premium.data.repository

import android.util.Log
import com.example.data.supabase.SupabaseClient
import com.example.premium.data.remote.PremiumSupabaseApi
import com.example.premium.data.remote.RpcBuyRequest
import com.example.premium.data.remote.RpcExchangeRequest
import com.example.premium.data.remote.RpcMissionProgressRequest
import com.example.premium.data.remote.RpcNotifReadRequest
import com.example.premium.data.remote.RpcWalletHistoryRequest
import com.example.premium.domain.model.*
import com.example.premium.domain.repository.PremiumRepository
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Implementación del repositorio Premium 2.0.
 *
 * Patrón idéntico a LiveRepositoryImpl:
 *  - Retrofit + Moshi + header apikey/Authorization.
 *  - Los RPC devuelven ResponseBody (JSON) y se parsean con el adapter de
 *    SupabaseClient.moshi.
 */
class PremiumRepositoryImpl : PremiumRepository {
    private val TAG = "PanalinkPremium"

    private val api: PremiumSupabaseApi by lazy {
        val baseUrl = if (SupabaseClient.supabaseUrl.endsWith("/")) SupabaseClient.supabaseUrl else "${SupabaseClient.supabaseUrl}/"
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("apikey", SupabaseClient.supabaseAnonKey)
                    .build()
                chain.proceed(request)
            }
            .build()

        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(SupabaseClient.moshi))
            .build()
            .create(PremiumSupabaseApi::class.java)
    }

    private fun getAuthHeader(): String {
        val token = SupabaseClient.currentToken ?: ""
        return if (token.startsWith("Bearer ")) token else "Bearer $token"
    }

    // ---- Helpers de parsing ------------------------------------------------------

    private fun parseJson(raw: String?): Map<String, Any>? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        return try {
            @Suppress("UNCHECKED_CAST")
            SupabaseClient.moshi.adapter(Map::class.java).fromJson(text) as? Map<String, Any>
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo parsear la respuesta RPC: $text", e)
            null
        }
    }

    private fun asBool(map: Map<String, Any>?, key: String): Boolean =
        (map?.get(key) as? Boolean) ?: false

    private fun asInt(map: Map<String, Any>?, key: String): Int =
        (map?.get(key) as? Number)?.toInt() ?: 0

    private fun asStr(map: Map<String, Any>?, key: String): String? =
        map?.get(key) as? String

    private fun asDouble(map: Map<String, Any>?, key: String): Double =
        (map?.get(key) as? Number)?.toDouble() ?: 1.0

    private fun asMapList(map: Map<String, Any>?, key: String): List<Map<String, Any>> {
        @Suppress("UNCHECKED_CAST")
        return (map?.get(key) as? List<Any>)?.filterIsInstance<Map<String, Any>>() ?: emptyList()
    }

    // ---- RPCs -------------------------------------------------------------------

    override suspend fun getWalletBalance(): Result<WalletBalance> {
        return try {
            val response = api.rpcWalletBalance(
                apiKey = SupabaseClient.supabaseAnonKey,
                authorization = getAuthHeader()
            )
            if (response.isSuccessful) {
                val map = parseJson(response.body()?.string())
                Result.success(
                    WalletBalance(
                        coins = asInt(map, "coins"),
                        diamonds = asInt(map, "diamonds"),
                        tickets = asInt(map, "tickets"),
                        xp = asInt(map, "xp"),
                        level = asInt(map, "level")
                    )
                )
            } else {
                Result.failure(Exception("Error wallet: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception wallet_balance_full", e)
            Result.failure(e)
        }
    }

    override suspend fun getCatalog(): Result<List<PremiumProduct>> {
        return try {
            val response = api.rpcCatalog(
                apiKey = SupabaseClient.supabaseAnonKey,
                authorization = getAuthHeader()
            )
            if (response.isSuccessful) {
                val map = parseJson(response.body()?.string())
                Result.success(asMapList(map, "products").mapNotNull { it.toProduct() })
            } else {
                Result.failure(Exception("Error catálogo: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception premium_catalog", e)
            Result.failure(e)
        }
    }

    override suspend fun getMyEntitlements(): Result<List<Entitlement>> {
        return try {
            val response = api.rpcMyEntitlements(
                apiKey = SupabaseClient.supabaseAnonKey,
                authorization = getAuthHeader()
            )
            if (response.isSuccessful) {
                val map = parseJson(response.body()?.string())
                Result.success(asMapList(map, "entitlements").mapNotNull { it.toEntitlement() })
            } else {
                Result.failure(Exception("Error entitlements: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception premium_my_entitlements", e)
            Result.failure(e)
        }
    }

    override suspend fun buyProduct(productCode: String, requestId: String?): Result<PremiumBuyResult> {
        return try {
            val response = api.rpcBuy(
                apiKey = SupabaseClient.supabaseAnonKey,
                authorization = getAuthHeader(),
                body = RpcBuyRequest(productCode, requestId ?: UUID.randomUUID().toString())
            )
            if (response.isSuccessful) {
                val map = parseJson(response.body()?.string())
                val balance = map?.let { if (it.containsKey("balance")) asInt(it, "balance") else null }
                Result.success(
                    PremiumBuyResult(
                        ok = asBool(map, "ok"),
                        balance = balance,
                        expiresAt = asStr(map, "expires_at"),
                        entitlementId = asStr(map, "entitlement_id"),
                        featureKey = asStr(map, "feature_key"),
                        reason = asStr(map, "reason")
                    )
                )
            } else {
                Result.failure(Exception("Error compra: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception premium_buy", e)
            Result.failure(e)
        }
    }

    override suspend fun getPromotions(): Result<List<PremiumPromotion>> {
        return try {
            val response = api.rpcPromotions(
                apiKey = SupabaseClient.supabaseAnonKey,
                authorization = getAuthHeader()
            )
            if (response.isSuccessful) {
                val map = parseJson(response.body()?.string())
                Result.success(asMapList(map, "promotions").mapNotNull { it.toPromotion() })
            } else {
                Result.failure(Exception("Error promos: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception premium_active_promotions", e)
            Result.failure(e)
        }
    }

    override suspend fun getEvents(): Result<List<PremiumEvent>> {
        return try {
            val response = api.rpcEvents(
                apiKey = SupabaseClient.supabaseAnonKey,
                authorization = getAuthHeader()
            )
            if (response.isSuccessful) {
                val map = parseJson(response.body()?.string())
                Result.success(asMapList(map, "events").mapNotNull { it.toEvent() })
            } else {
                Result.failure(Exception("Error eventos: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception premium_active_events", e)
            Result.failure(e)
        }
    }

    override suspend fun claimDailyReward(): Result<DailyRewardClaim> {
        return try {
            val response = api.rpcClaimDailyReward(
                apiKey = SupabaseClient.supabaseAnonKey,
                authorization = getAuthHeader()
            )
            if (response.isSuccessful) {
                val map = parseJson(response.body()?.string())
                Result.success(
                    DailyRewardClaim(
                        ok = asBool(map, "ok"),
                        day = map?.let { if (it.containsKey("day")) asInt(it, "day") else null },
                        streak = map?.let { if (it.containsKey("streak")) asInt(it, "streak") else null },
                        currency = asStr(map, "currency"),
                        amount = map?.let { if (it.containsKey("amount")) asInt(it, "amount") else null },
                        reason = asStr(map, "reason")
                    )
                )
            } else {
                Result.failure(Exception("Error daily reward: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception claim_daily_reward", e)
            Result.failure(e)
        }
    }

    override suspend fun getDailyRewardStatus(): Result<DailyRewardStatus> {
        return try {
            val response = api.rpcDailyRewardStatus(
                apiKey = SupabaseClient.supabaseAnonKey,
                authorization = getAuthHeader()
            )
            if (response.isSuccessful) {
                val map = parseJson(response.body()?.string())
                Result.success(
                    DailyRewardStatus(
                        streak = asInt(map, "streak"),
                        claimedToday = asBool(map, "claimed_today")
                    )
                )
            } else {
                Result.failure(Exception("Error reward status: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception daily_reward_status", e)
            Result.failure(e)
        }
    }

    override suspend fun getMissions(): Result<List<Mission>> {
        return try {
            val response = api.rpcMissions(
                apiKey = SupabaseClient.supabaseAnonKey,
                authorization = getAuthHeader()
            )
            if (response.isSuccessful) {
                val map = parseJson(response.body()?.string())
                Result.success(asMapList(map, "missions").mapNotNull { it.toMission() })
            } else {
                Result.failure(Exception("Error misiones: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception missions_in_progress", e)
            Result.failure(e)
        }
    }

    override suspend fun reportMissionProgress(missionCode: String, increment: Int): Result<MissionResult> {
        return try {
            val response = api.rpcMissionProgress(
                apiKey = SupabaseClient.supabaseAnonKey,
                authorization = getAuthHeader(),
                body = RpcMissionProgressRequest(missionCode, increment)
            )
            if (response.isSuccessful) {
                val map = parseJson(response.body()?.string())
                Result.success(
                    MissionResult(
                        ok = asBool(map, "ok"),
                        progress = map?.let { if (it.containsKey("progress")) asInt(it, "progress") else null },
                        target = map?.let { if (it.containsKey("target")) asInt(it, "target") else null },
                        completed = map?.let { it["completed"] as? Boolean },
                        claimed = map?.let { it["claimed"] as? Boolean },
                        reason = asStr(map, "reason")
                    )
                )
            } else {
                Result.failure(Exception("Error misión: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception mission_progress", e)
            Result.failure(e)
        }
    }

    override suspend fun claimMissionRewards(): Result<MissionClaimAllResult> {
        return try {
            val response = api.rpcMissionClaimAll(
                apiKey = SupabaseClient.supabaseAnonKey,
                authorization = getAuthHeader()
            )
            if (response.isSuccessful) {
                val map = parseJson(response.body()?.string())
                val claimed = asMapList(map, "claimed").map {
                    MissionClaimed(
                        mission = asStr(it, "mission") ?: "",
                        title = asStr(it, "title") ?: "",
                        reward = asInt(it, "reward"),
                        balance = asInt(it, "balance")
                    )
                }
                Result.success(MissionClaimAllResult(ok = asBool(map, "ok"), claimed = claimed))
            } else {
                Result.failure(Exception("Error claim misiones: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception mission_claim_all", e)
            Result.failure(e)
        }
    }

    override suspend fun exchangeDiamonds(amount: Int, requestId: String?): Result<ExchangeResult> {
        return try {
            val response = api.rpcExchange(
                apiKey = SupabaseClient.supabaseAnonKey,
                authorization = getAuthHeader(),
                body = RpcExchangeRequest(amount, requestId ?: UUID.randomUUID().toString())
            )
            if (response.isSuccessful) {
                val map = parseJson(response.body()?.string())
                Result.success(
                    ExchangeResult(
                        ok = asBool(map, "ok"),
                        coins = map?.let { if (it.containsKey("coins")) asInt(it, "coins") else null },
                        reason = asStr(map, "reason")
                    )
                )
            } else {
                Result.failure(Exception("Error exchange: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception diamonds_exchange", e)
            Result.failure(e)
        }
    }

    override suspend fun getNotifications(): Result<List<PremiumNotification>> {
        return try {
            val response = api.rpcNotifications(
                apiKey = SupabaseClient.supabaseAnonKey,
                authorization = getAuthHeader()
            )
            if (response.isSuccessful) {
                val map = parseJson(response.body()?.string())
                Result.success(asMapList(map, "notifications").map {
                    PremiumNotification(
                        id = asStr(it, "id") ?: "",
                        category = asStr(it, "category") ?: "SYSTEM",
                        priority = asStr(it, "priority") ?: "NORMAL",
                        title = asStr(it, "title") ?: "",
                        body = asStr(it, "body"),
                        isRead = asBool(it, "is_read"),
                        createdAt = asStr(it, "created_at") ?: ""
                    )
                })
            } else {
                Result.failure(Exception("Error notificaciones: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception notifications_for_me", e)
            Result.failure(e)
        }
    }

    override suspend fun markNotificationRead(notificationId: String): Result<SimpleResult> {
        return try {
            val response = api.rpcNotificationRead(
                apiKey = SupabaseClient.supabaseAnonKey,
                authorization = getAuthHeader(),
                body = RpcNotifReadRequest(notificationId)
            )
            if (response.isSuccessful) {
                Result.success(SimpleResult(ok = true))
            } else {
                Result.failure(Exception("Error mark read: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception notification_read", e)
            Result.failure(e)
        }
    }

    override suspend fun getWalletHistory(limit: Int, currency: String?): Result<WalletHistoryResponse> {
        return try {
            val response = api.rpcWalletHistory(
                apiKey = SupabaseClient.supabaseAnonKey,
                authorization = getAuthHeader(),
                body = RpcWalletHistoryRequest(limit, currency)
            )
            if (response.isSuccessful) {
                val map = parseJson(response.body()?.string()) ?: emptyMap()
                val walletMap = map["wallet"] as? Map<*, *>
                val wallet = WalletBalance(
                    coins = asInt(walletMap as? Map<String, Any>, "coins"),
                    diamonds = asInt(walletMap as? Map<String, Any>, "diamonds"),
                    tickets = asInt(walletMap as? Map<String, Any>, "tickets"),
                    xp = asInt(walletMap as? Map<String, Any>, "xp"),
                    level = asInt(walletMap as? Map<String, Any>, "level")
                )
                val tx = asMapList(map, "transactions").map {
                    WalletTransaction(
                        id = asStr(it, "id") ?: "",
                        kind = asStr(it, "kind") ?: "",
                        currency = asStr(it, "currency") ?: "coins",
                        amount = asInt(it, "amount"),
                        balanceAfter = asInt(it, "balance_after"),
                        description = asStr(it, "description"),
                        refType = asStr(it, "ref_type"),
                        createdAt = asStr(it, "created_at") ?: ""
                    )
                }
                Result.success(WalletHistoryResponse(ok = true, wallet = wallet, transactions = tx, count = tx.size))
            } else {
                Result.failure(Exception("Error wallet_history: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception wallet_history", e)
            Result.failure(e)
        }
    }

    override suspend fun getLevelInfo(): Result<LevelInfo> {
        return try {
            val response = api.rpcLevelInfo(
                apiKey = SupabaseClient.supabaseAnonKey,
                authorization = getAuthHeader()
            )
            if (response.isSuccessful) {
                val map = parseJson(response.body()?.string()) ?: emptyMap()
                Result.success(
                    LevelInfo(
                        ok = asBool(map, "ok"),
                        level = asInt(map, "level"),
                        xp = asInt(map, "xp"),
                        xpForNext = asInt(map, "xp_for_next"),
                        current = (map["current"] as? Map<*, *>)?.let { tier ->
                            tier as? Map<String, Any>
                        }?.toLevelTier(),
                        next = (map["next"] as? Map<*, *>)?.let { tier ->
                            tier as? Map<String, Any>
                        }?.toLevelTier(),
                        levelTiers = asMapList(map, "level_tiers").mapNotNull { it.toLevelTier() }
                    )
                )
            } else {
                Result.failure(Exception("Error level_info: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception premium_level_info", e)
            Result.failure(e)
        }
    }

    // ---- Mapeo de map -> modelo ---------------------------------------------------

    private fun Map<String, Any>.toLevelTier(): LevelTierInfo? {
        val level = asInt(this, "level")
        if (level <= 0) return null
        return LevelTierInfo(
            level = level,
            title = asStr(this, "title") ?: "Pana",
            emoji = asStr(this, "emoji") ?: "⭐",
            rewardCurrency = asStr(this, "reward_currency"),
            rewardAmount = asInt(this, "reward_amount"),
            cosmeticCode = asStr(this, "cosmetic_code")
        )
    }

    private fun Map<String, Any>.toEntitlement(): Entitlement? {
        val id = asStr(this, "id") ?: return null
        return Entitlement(
            id = id,
            featureKey = asStr(this, "feature_key") ?: "",
            productCode = asStr(this, "product_code") ?: "",
            name = asStr(this, "name") ?: "",
            emoji = asStr(this, "emoji") ?: "",
            startsAt = asStr(this, "starts_at") ?: "",
            expiresAt = asStr(this, "expires_at") ?: "",
            status = asStr(this, "status") ?: "active",
            daysLeft = asInt(this, "days_left")
        )
    }

    private fun Map<String, Any>.toProduct(): PremiumProduct? {
        val code = asStr(this, "code") ?: return null
        return PremiumProduct(
            code = code,
            featureKey = asStr(this, "feature_key") ?: "",
            name = asStr(this, "name") ?: "",
            emoji = asStr(this, "emoji") ?: "",
            description = asStr(this, "description"),
            durationDays = asInt(this, "duration_days"),
            priceCoins = asInt(this, "price_coins"),
            trialDays = asInt(this, "trial_days"),
            sortOrder = asInt(this, "sort_order"),
            featureEnabled = asBool(this, "feature_enabled")
        )
    }

    private fun Map<String, Any>.toPromotion(): PremiumPromotion? {
        val id = asStr(this, "id") ?: return null
        return PremiumPromotion(
            id = id,
            title = asStr(this, "title") ?: "",
            subtitle = asStr(this, "subtitle"),
            description = asStr(this, "description"),
            imageUrl = asStr(this, "image_url"),
            bannerUrl = asStr(this, "banner_url"),
            actionType = asStr(this, "action_type") ?: "shop",
            actionValue = asStr(this, "action_value"),
            featureKey = asStr(this, "feature_key"),
            priceCoins = (this["price_coins"] as? Number)?.toInt(),
            discountPercent = asInt(this, "discount_percent"),
            endsAt = asStr(this, "ends_at") ?: "",
            priority = asInt(this, "priority")
        )
    }

    private fun Map<String, Any>.toEvent(): PremiumEvent? {
        val code = asStr(this, "code") ?: return null
        return PremiumEvent(
            code = code,
            title = asStr(this, "title") ?: "",
            subtitle = asStr(this, "subtitle"),
            description = asStr(this, "description"),
            emoji = asStr(this, "emoji"),
            imageUrl = asStr(this, "image_url"),
            actionType = asStr(this, "action_type") ?: "deep_link",
            actionValue = asStr(this, "action_value"),
            startsAt = asStr(this, "starts_at") ?: "",
            endsAt = asStr(this, "ends_at") ?: "",
            multiplierCoins = asDouble(this, "multiplier_coins"),
            multiplierXp = asDouble(this, "multiplier_xp")
        )
    }

    private fun Map<String, Any>.toMission(): Mission? {
        val code = asStr(this, "code") ?: return null
        return Mission(
            missionId = asStr(this, "mission_id") ?: "",
            code = code,
            title = asStr(this, "title") ?: "",
            description = asStr(this, "description"),
            scope = asStr(this, "scope") ?: "daily",
            rewardCurrency = asStr(this, "reward_currency") ?: "coins",
            rewardAmount = asInt(this, "reward_amount"),
            target = asInt(this, "target"),
            progress = asInt(this, "progress"),
            completed = asBool(this, "completed"),
            claimed = asBool(this, "claimed")
        )
    }
}