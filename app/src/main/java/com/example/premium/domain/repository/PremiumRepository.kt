package com.example.premium.domain.repository

import com.example.premium.domain.model.*

/**
 * Repositorio Premium 2.0 (RPCs de Supabase).
 * Todas las operaciones son atómicas en el servidor (security definer).
 */
interface PremiumRepository {
    suspend fun getWalletBalance(): Result<WalletBalance>
    suspend fun getCatalog(): Result<List<PremiumProduct>>
    suspend fun getMyEntitlements(): Result<List<Entitlement>>
    suspend fun buyProduct(productCode: String, requestId: String? = null): Result<PremiumBuyResult>
    suspend fun getPromotions(): Result<List<PremiumPromotion>>
    suspend fun getEvents(): Result<List<PremiumEvent>>
    suspend fun claimDailyReward(): Result<DailyRewardClaim>
    suspend fun getDailyRewardStatus(): Result<DailyRewardStatus>
    suspend fun getMissions(): Result<List<Mission>>
    suspend fun reportMissionProgress(missionCode: String, increment: Int = 1): Result<MissionResult>
    suspend fun claimMissionRewards(): Result<MissionClaimAllResult>
    suspend fun exchangeDiamonds(amount: Int, requestId: String? = null): Result<ExchangeResult>
    suspend fun getNotifications(): Result<List<PremiumNotification>>
    suspend fun markNotificationRead(notificationId: String): Result<SimpleResult>
    suspend fun getWalletHistory(limit: Int = 100, currency: String? = null): Result<WalletHistoryResponse>
    suspend fun getLevelInfo(): Result<LevelInfo>
}