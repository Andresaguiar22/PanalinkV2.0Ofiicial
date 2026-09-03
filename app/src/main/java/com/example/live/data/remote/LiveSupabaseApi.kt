package com.example.live.data.remote

import com.example.live.domain.model.LiveComment
import com.example.live.domain.model.LiveStream
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface LiveSupabaseApi {
    @GET("rest/v1/live_streams")
    suspend fun getLiveStreams(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Query("status") status: String? = "eq.LIVE",
        @Query("select") select: String = "*"
    ): Response<List<LiveStream>>

    @GET("rest/v1/live_streams")
    suspend fun getLiveStreamById(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Query("id") idFilter: String,
        @Query("select") select: String = "*"
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
}
