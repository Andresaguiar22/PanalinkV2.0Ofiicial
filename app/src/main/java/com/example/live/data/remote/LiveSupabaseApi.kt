package com.example.live.data.remote

import com.example.live.domain.model.LiveComment
import com.example.live.domain.model.LiveGift
import com.example.live.domain.model.LiveHeartbeatRequest
import com.example.live.domain.model.LiveJoinStreamRequest
import com.example.live.domain.model.LiveSendGiftRequest
import com.example.live.domain.model.LiveSendLikeRequest
import com.example.live.domain.model.LiveSetViewerCountRequest
import com.example.live.domain.model.LiveStream
import com.example.live.domain.model.LiveStreamStats
import com.example.live.domain.model.LiveWalletBalanceRequest
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface LiveSupabaseApi {
    @GET("rest/v1/live_streams")
    suspend fun getLiveStreams(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Query("status") status: String? = "eq.LIVE",
        @Query("select") select: String = "*,live_stream_stats(*)"
    ): Response<List<LiveStream>>

    @GET("rest/v1/live_streams")
    suspend fun getLiveStreamById(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Query("id") idFilter: String,
        @Query("select") select: String = "*,live_stream_stats(*)"
    ): Response<List<LiveStream>>

    @POST("rest/v1/live_streams")
    @Headers("Prefer: return=representation")
    suspend fun createLiveStream(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Body liveStream: Map<String, String?>
    ): Response<List<LiveStream>>

    @PATCH("rest/v1/live_streams")
    @Headers("Prefer: return=representation")
    suspend fun updateLiveStream(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Query("id") idFilter: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Body updates: Map<String, String?>
    ): Response<List<LiveStream>>

    @GET("rest/v1/live_comments")
    suspend fun getComments(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Query("stream_id") streamIdFilter: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = "created_at.asc"
    ): Response<List<LiveComment>>

    @POST("rest/v1/live_comments")
    @Headers("Prefer: return=representation")
    suspend fun postComment(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Body comment: Map<String, String?>
    ): Response<List<LiveComment>>

    @POST
    suspend fun callEdgeFunction(
        @Url url: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Body body: Map<String, String>
    ): Response<ResponseBody>

    // --- Engagement real: estadísticas, reacciones, regalos y presencia ---------

    @GET("rest/v1/live_stream_stats")
    suspend fun getLiveStreamStats(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Query("stream_id") streamIdFilter: String,
        @Query("select") select: String = "*"
    ): Response<List<LiveStreamStats>>

    @GET("rest/v1/live_gifts")
    suspend fun getGiftCatalog(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Query("select") select: String = "*",
        @Query("is_active") activeFilter: String = "eq.true",
        @Query("order") order: String = "sort_order.asc"
    ): Response<List<LiveGift>>

    @POST("rest/v1/rpc/live_wallet_balance")
    suspend fun rpcWalletBalance(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Body body: LiveWalletBalanceRequest = LiveWalletBalanceRequest()
    ): Response<ResponseBody>

    @POST("rest/v1/rpc/live_send_like")
    suspend fun rpcSendLike(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Body body: LiveSendLikeRequest
    ): Response<ResponseBody>

    @POST("rest/v1/rpc/live_send_gift")
    suspend fun rpcSendGift(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Body body: LiveSendGiftRequest
    ): Response<ResponseBody>

    @POST("rest/v1/rpc/live_set_viewer_count")
    suspend fun rpcSetViewerCount(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Body body: LiveSetViewerCountRequest
    ): Response<ResponseBody>

    @POST("rest/v1/rpc/live_heartbeat")
    suspend fun rpcLiveHeartbeat(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Body body: LiveHeartbeatRequest
    ): Response<ResponseBody>

    @POST("rest/v1/rpc/live_join_stream")
    suspend fun rpcJoinStream(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Body body: LiveJoinStreamRequest
    ): Response<ResponseBody>

    companion object {
        /** live_streams + contadores agregados (relación uno-a-uno). */
        const val LIVE_STREAM_SELECT = "*,live_stream_stats(*)"
    }
}
