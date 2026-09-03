package com.example.rooms.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class VoiceRoomDto(val id: String,val name: String,@Json(name = "owner_id") val ownerId: String,val status: String,@Json(name = "max_seats") val maxSeats: Int = 7,val description: String = "",@Json(name = "cover_url") val coverUrl: String? = null,val category: String = "general",val visibility: String = "public",@Json(name = "is_locked") val isLocked: Boolean = false,@Json(name = "created_at") val createdAt: String? = null)
@JsonClass(generateAdapter = true)
data class PublicProfileDto(val id: String,@Json(name = "display_name") val displayName: String? = null,@Json(name = "avatar_url") val avatarUrl: String? = null)
@JsonClass(generateAdapter = true)
data class VoiceRoomMemberDto(val id: String,@Json(name = "room_id") val roomId: String,@Json(name = "user_id") val userId: String,val role: String,@Json(name = "joined_at") val joinedAt: String,@Json(name = "left_at") val leftAt: String? = null)
@JsonClass(generateAdapter = true)
data class VoiceRoomSeatDto(val id: String,@Json(name = "room_id") val roomId: String,@Json(name = "seat_index") val seatIndex: Int,@Json(name = "user_id") val userId: String,@Json(name = "is_muted") val isMuted: Boolean = false,@Json(name = "joined_at") val joinedAt: String? = null)
@JsonClass(generateAdapter = true)
data class VoiceRoomMessageDto(val id: String,@Json(name = "room_id") val roomId: String,@Json(name = "sender_id") val senderId: String,val content: String,@Json(name = "created_at") val createdAt: String)
@JsonClass(generateAdapter = true)
data class VoiceRoomSeatRequestDto(val id: String,@Json(name = "room_id") val roomId: String,@Json(name = "user_id") val userId: String,@Json(name = "requested_seat_index") val requestedSeatIndex: Int? = null,val status: String,@Json(name = "created_at") val createdAt: String)
@JsonClass(generateAdapter = true)
data class MoveSeatRequest(@Json(name = "p_room_id") val roomId: String,@Json(name = "p_target_seat") val seatIndex: Int)
@JsonClass(generateAdapter = true)
data class SetAdminRequest(@Json(name = "p_room_id") val roomId: String,@Json(name = "p_user_id") val userId: String,@Json(name = "p_make_admin") val makeAdmin: Boolean)
@JsonClass(generateAdapter = true)
data class ModerateMuteRequest(@Json(name = "p_room_id") val roomId: String,@Json(name = "p_target_user") val targetUserId: String,@Json(name = "p_muted") val muted: Boolean)
@JsonClass(generateAdapter = true)
data class RequestSeatRequest(@Json(name = "p_room_id") val roomId: String,@Json(name = "p_requested_seat") val seatIndex: Int?)
@JsonClass(generateAdapter = true)
data class ResolveSeatRequest(@Json(name = "p_request_id") val requestId: String,@Json(name = "p_approve") val approve: Boolean,@Json(name = "p_seat_index") val seatIndex: Int?)

interface VoiceRoomApi {
    @GET("rest/v1/voice_rooms") suspend fun listLiveRooms(@Header("apikey") apiKey:String,@Header("Authorization") auth:String,@Query("status") status:String="eq.live",@Query("order") order:String="created_at.desc",@Query("limit") limit:Int=100):Response<List<VoiceRoomDto>>
    @GET("rest/v1/voice_rooms") suspend fun getRoom(@Header("apikey") apiKey:String,@Header("Authorization") auth:String,@Query("id") id:String):Response<List<VoiceRoomDto>>
    @POST("rest/v1/rpc/create_voice_room") suspend fun createRoom(@Header("apikey") apiKey:String,@Header("Authorization") auth:String,@Body body:Map<String,String?>):Response<List<VoiceRoomDto>>
    @POST("rest/v1/rpc/join_voice_room") suspend fun joinRoom(@Header("apikey") apiKey:String,@Header("Authorization") auth:String,@Body body:Map<String,String>):Response<Unit>
    @POST("rest/v1/rpc/leave_voice_room") suspend fun leaveRoom(@Header("apikey") apiKey:String,@Header("Authorization") auth:String,@Body body:Map<String,String>):Response<Unit>
    @POST("rest/v1/rpc/move_voice_room_seat") suspend fun moveSeat(@Header("apikey") apiKey:String,@Header("Authorization") auth:String,@Body body:MoveSeatRequest):Response<List<VoiceRoomSeatDto>>
    @POST("rest/v1/rpc/leave_voice_room_seat") suspend fun leaveSeat(@Header("apikey") apiKey:String,@Header("Authorization") auth:String,@Body body:Map<String,String>):Response<Unit>
    @POST("rest/v1/rpc/request_voice_room_seat") suspend fun requestSeat(@Header("apikey") apiKey:String,@Header("Authorization") auth:String,@Body body:RequestSeatRequest):Response<List<VoiceRoomSeatRequestDto>>
    @POST("rest/v1/rpc/resolve_voice_room_seat_request") suspend fun resolveSeatRequest(@Header("apikey") apiKey:String,@Header("Authorization") auth:String,@Body body:ResolveSeatRequest):Response<List<VoiceRoomSeatDto>>
    @POST("/rest/v1/rpc/set_voice_room_admin") suspend fun setAdmin(@Header("apikey") apiKey:String,@Header("Authorization") auth:String,@Body body:SetAdminRequest):Response<Unit>
    @POST("/rest/v1/rpc/moderate_voice_room_mute") suspend fun moderateMute(@Header("apikey") apiKey:String,@Header("Authorization") auth:String,@Body body:ModerateMuteRequest):Response<Unit>
    @POST("/rest/v1/rpc/moderate_voice_room_kick") suspend fun moderateKick(@Header("apikey") apiKey:String,@Header("Authorization") auth:String,@Body body:Map<String,String>):Response<Unit>
    @POST("/rest/v1/rpc/moderate_voice_room_ban") suspend fun moderateBan(@Header("apikey") apiKey:String,@Header("Authorization") auth:String,@Body body:Map<String,String?>):Response<Unit>
    @POST("/rest/v1/rpc/invite_voice_room_user") suspend fun inviteUser(@Header("apikey") apiKey:String,@Header("Authorization") auth:String,@Body body:Map<String,String>):Response<Unit>
    @GET("rest/v1/public_profiles") suspend fun getPublicProfiles(@Header("apikey") apiKey:String,@Header("Authorization") auth:String,@Query("id") ids:String):Response<List<PublicProfileDto>>
    @GET("rest/v1/voice_room_members") suspend fun getActiveMembers(@Header("apikey") apiKey:String,@Header("Authorization") auth:String,@Query("room_id") roomIdFilter:String,@Query("left_at") leftAt:String="is.null"):Response<List<VoiceRoomMemberDto>>
    @GET("rest/v1/voice_room_members") suspend fun getMembers(@Header("apikey") apiKey:String,@Header("Authorization") auth:String,@Query("room_id") roomId:String,@Query("left_at") leftAt:String="is.null"):Response<List<VoiceRoomMemberDto>>
    @GET("rest/v1/voice_room_seats") suspend fun getSeats(@Header("apikey") apiKey:String,@Header("Authorization") auth:String,@Query("room_id") roomId:String,@Query("order") order:String="seat_index.asc"):Response<List<VoiceRoomSeatDto>>
    @GET("rest/v1/voice_room_seat_requests") suspend fun getSeatRequests(@Header("apikey") apiKey:String,@Header("Authorization") auth:String,@Query("room_id") roomId:String,@Query("status") status:String="in.(pending,approved)"):Response<List<VoiceRoomSeatRequestDto>>
    @GET("rest/v1/voice_room_messages") suspend fun getMessages(@Header("apikey") apiKey:String,@Header("Authorization") auth:String,@Query("room_id") roomId:String,@Query("order") order:String="created_at.desc",@Query("limit") limit:Int=100):Response<List<VoiceRoomMessageDto>>
    @POST("rest/v1/voice_room_messages") @Headers("Prefer: return=representation") suspend fun sendMessage(@Header("apikey") apiKey:String,@Header("Authorization") auth:String,@Body body:Map<String,String>):Response<List<VoiceRoomMessageDto>>
    companion object { fun create(baseUrl:String,moshi:com.squareup.moshi.Moshi,client:okhttp3.OkHttpClient):VoiceRoomApi { val url=if(baseUrl.endsWith('/'))baseUrl else "$baseUrl/"; return Retrofit.Builder().baseUrl(url).client(client).addConverterFactory(MoshiConverterFactory.create(moshi)).build().create(VoiceRoomApi::class.java) } }
}
