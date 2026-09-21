package com.example.premium.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

/**
 * Interfaz Retrofit de los RPC del sistema Premium 2.0.
 *
 * Convenciones del repo:
 *  - Header `apikey` (anon key) y `Authorization` (Bearer JWT del usuario).
 *  - Los RPC se llaman vía `POST rest/v1/rpc/{function}`.
 *  - Los parámetros se serializan con data classes tipadas (Moshi).
 */

// ---- Bodies de request (Moshi tipado, igual que LiveRpcDtos) ----

@JsonClass(generateAdapter = true)
data class RpcBuyRequest(
    @Json(name = "p_product_code") val productCode: String,
    @Json(name = "p_request_id") val requestId: String?
)

@JsonClass(generateAdapter = true)
data class RpcExchangeRequest(
    @Json(name = "p_amount") val amount: Int,
    @Json(name = "p_request_id") val requestId: String?
)

@JsonClass(generateAdapter = true)
data class RpcMissionProgressRequest(
    @Json(name = "p_mission_code") val missionCode: String,
    @Json(name = "p_increment") val increment: Int
)

/** Body vacío `{}` para RPC sin parámetros. */
class RpcEmptyRequest

@JsonClass(generateAdapter = true)
data class RpcNotifReadRequest(
    @Json(name = "p_notification_id") val notificationId: String
)


/** Interface Retrofit de RPC Premium. */
interface PremiumSupabaseApi {

    @POST("rest/v1/rpc/wallet_balance_full")
    suspend fun rpcWalletBalance(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Body body: RpcEmptyRequest = RpcEmptyRequest()
    ): Response<ResponseBody>

    @POST("rest/v1/rpc/premium_buy")
    suspend fun rpcBuy(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Body body: RpcBuyRequest
    ): Response<ResponseBody>

    @POST("rest/v1/rpc/premium_catalog")
    suspend fun rpcCatalog(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Body body: RpcEmptyRequest = RpcEmptyRequest()
    ): Response<ResponseBody>

    @POST("rest/v1/rpc/premium_my_entitlements")
    suspend fun rpcMyEntitlements(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Body body: RpcEmptyRequest = RpcEmptyRequest()
    ): Response<ResponseBody>

    @POST("rest/v1/rpc/premium_active_promotions")
    suspend fun rpcPromotions(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Body body: RpcEmptyRequest = RpcEmptyRequest()
    ): Response<ResponseBody>

    @POST("rest/v1/rpc/premium_active_events")
    suspend fun rpcEvents(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Body body: RpcEmptyRequest = RpcEmptyRequest()
    ): Response<ResponseBody>

    @POST("rest/v1/rpc/claim_daily_reward")
    suspend fun rpcClaimDailyReward(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Body body: RpcEmptyRequest = RpcEmptyRequest()
    ): Response<ResponseBody>

    @POST("rest/v1/rpc/daily_reward_status")
    suspend fun rpcDailyRewardStatus(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Body body: RpcEmptyRequest = RpcEmptyRequest()
    ): Response<ResponseBody>

    @POST("rest/v1/rpc/missions_in_progress")
    suspend fun rpcMissions(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Body body: RpcEmptyRequest = RpcEmptyRequest()
    ): Response<ResponseBody>

    @POST("rest/v1/rpc/mission_progress")
    suspend fun rpcMissionProgress(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Body body: RpcMissionProgressRequest
    ): Response<ResponseBody>

    @POST("rest/v1/rpc/mission_claim_all")
    suspend fun rpcMissionClaimAll(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Body body: RpcEmptyRequest = RpcEmptyRequest()
    ): Response<ResponseBody>

    @POST("rest/v1/rpc/diamonds_exchange")
    suspend fun rpcExchange(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Body body: RpcExchangeRequest
    ): Response<ResponseBody>

    @POST("rest/v1/rpc/notifications_for_me")
    suspend fun rpcNotifications(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Body body: RpcEmptyRequest = RpcEmptyRequest()
    ): Response<ResponseBody>

    @POST("rest/v1/rpc/notification_read")
    suspend fun rpcNotificationRead(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Body body: RpcNotifReadRequest
    ): Response<ResponseBody>

    @POST("rest/v1/rpc/wallet_history")
    suspend fun rpcWalletHistory(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Body body: RpcWalletHistoryRequest
    ): Response<ResponseBody>

    @POST("rest/v1/rpc/premium_level_info")
    suspend fun rpcLevelInfo(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Body body: RpcEmptyRequest = RpcEmptyRequest()
    ): Response<ResponseBody>
}

@JsonClass(generateAdapter = true)
data class RpcWalletHistoryRequest(
    @Json(name = "p_limit") val limit: Int = 100,
    @Json(name = "p_currency") val currency: String? = null
)