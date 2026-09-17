package com.example.rooms.repository

import com.example.data.supabase.SupabaseClient
import com.example.data.supabase.SessionManager
import com.example.rooms.data.VoiceRoomApi
import com.example.rooms.data.VoiceRoomDto
import com.example.rooms.data.VoiceRoomMessageDto
import com.example.rooms.data.VoiceRoomSeatDto
import com.example.rooms.data.VoiceRoomSeatRequestDto
import com.example.rooms.data.PublicProfileDto
import com.example.rooms.data.UpdateRoomSettingsRequest
import com.example.rooms.data.VoiceRoomBanDto
import com.example.rooms.model.VoiceRoom
import com.example.rooms.model.VoiceRoomMember
import com.example.rooms.model.VoiceRoomMessage
import com.example.rooms.model.VoiceRoomSeatRequest
import com.example.util.Resilience
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class VoiceRoomRepository private constructor() {
    companion object { @Volatile private var instance:VoiceRoomRepository?=null; fun getInstance():VoiceRoomRepository=instance?: synchronized(this){instance?:VoiceRoomRepository().also{instance=it}} }
    private val client=OkHttpClient.Builder().connectTimeout(30,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).build()
    private val api:VoiceRoomApi by lazy{VoiceRoomApi.create(SupabaseClient.supabaseUrl,SupabaseClient.moshi,client)}
    private val apiKey get()=SupabaseClient.supabaseAnonKey
    private val auth get()="Bearer ${SupabaseClient.currentToken?:SupabaseClient.supabaseAnonKey}"
    private val myId get()=SupabaseClient.currentUser?.id?:""
    suspend fun listLiveRooms():Result<List<VoiceRoom>> = runCatching{val res=Resilience.retry{api.listLiveRooms(apiKey,auth)};if(!res.isSuccessful)error("listLiveRooms HTTP ${res.code()}: ${res.errorBody()?.string()}");res.body().orEmpty().map{it.toModel()}}
    suspend fun memberCounts(roomIds:List<String>):Result<Map<String,Int>> = runCatching{if(roomIds.isEmpty())return@runCatching emptyMap();val res=Resilience.retry{api.getActiveMembers(apiKey,auth,"in.(${roomIds.joinToString(",")})")};if(!res.isSuccessful)return@runCatching emptyMap();res.body().orEmpty().groupingBy{it.roomId}.eachCount()}
    suspend fun getRoomById(roomId:String):Result<VoiceRoom> = runCatching{val res=Resilience.retry{api.getRoom(apiKey,auth,"eq.$roomId")};if(!res.isSuccessful)error("getRoomById HTTP ${res.code()}");res.body()?.firstOrNull()?.toModel()?:error("Sala no encontrada")}
    suspend fun createRoom(request:CreateRoomRequest):Result<VoiceRoom> = runCatching{require(request.name.trim().length in 2..80){"El nombre debe tener entre 2 y 80 caracteres"};require(request.description.length<=280){"La descripción no puede superar 280 caracteres"};val body=mapOf<String,String?>("p_name" to request.name.trim(),"p_description" to request.description.trim(),"p_cover_url" to request.coverUrl?.trim(),"p_category" to request.category,"p_visibility" to request.visibility);val res=Resilience.retry{api.createRoom(apiKey,auth,body)};if(!res.isSuccessful)error("createRoom HTTP ${res.code()}: ${res.errorBody()?.string()}");res.body()?.firstOrNull()?.toModel()?:error("No se pudo crear la sala")}
    suspend fun joinRoom(roomId:String):Result<Unit> = runCatching{val res=api.joinRoom(apiKey,auth,mapOf("p_room_id" to roomId));if(!res.isSuccessful)error("joinRoom HTTP ${res.code()}: ${res.errorBody()?.string()}")}
    suspend fun leaveRoom(roomId:String):Result<Unit> = runCatching{val res=api.leaveRoom(apiKey,auth,mapOf("p_room_id" to roomId));if(!res.isSuccessful)error("leaveRoom HTTP ${res.code()}: ${res.errorBody()?.string()}")}
    suspend fun moveSeat(roomId:String,seatIndex:Int):Result<VoiceRoomSeatDto?> = runCatching{val res=api.moveSeat(apiKey,auth,com.example.rooms.data.MoveSeatRequest(roomId,seatIndex));if(!res.isSuccessful)error("moveSeat HTTP ${res.code()}: ${res.errorBody()?.string()}");res.body()?.firstOrNull()}
    suspend fun leaveSeat(roomId:String):Result<Unit> = runCatching{val res=api.leaveSeat(apiKey,auth,mapOf("p_room_id" to roomId));if(!res.isSuccessful)error("leaveSeat HTTP ${res.code()}: ${res.errorBody()?.string()}")}
    suspend fun requestSeat(roomId:String,seatIndex:Int?):Result<VoiceRoomSeatRequest> = runCatching{val res=api.requestSeat(apiKey,auth,com.example.rooms.data.RequestSeatRequest(roomId,seatIndex));if(!res.isSuccessful)error("requestSeat HTTP ${res.code()}: ${res.errorBody()?.string()}");res.body()?.firstOrNull()?.toModel()?:error("Solicitud no creada")}
    suspend fun getSeatRequests(roomId:String):Result<List<VoiceRoomSeatRequest>> = runCatching{val res=api.getSeatRequests(apiKey,auth,"eq.$roomId");if(!res.isSuccessful)error("getSeatRequests HTTP ${res.code()}");res.body().orEmpty().map{it.toModel()}}
    suspend fun resolveSeatRequest(requestId:String,approve:Boolean,seatIndex:Int?):Result<Unit> = runCatching{val res=api.resolveSeatRequest(apiKey,auth,com.example.rooms.data.ResolveSeatRequest(requestId,approve,seatIndex));if(!res.isSuccessful)error("resolveSeatRequest HTTP ${res.code()}: ${res.errorBody()?.string()}")}
    suspend fun setAdmin(roomId:String,userId:String,makeAdmin:Boolean):Result<Unit> = runCatching{val res=api.setAdmin(apiKey,auth,com.example.rooms.data.SetAdminRequest(roomId,userId,makeAdmin));if(!res.isSuccessful)error("setAdmin HTTP ${res.code()}")}
    suspend fun moderateMute(roomId:String,userId:String,muted:Boolean):Result<Unit> = runCatching{val res=api.moderateMute(apiKey,auth,com.example.rooms.data.ModerateMuteRequest(roomId,userId,muted));if(!res.isSuccessful)error("moderateMute HTTP ${res.code()}: ${res.errorBody()?.string()}")}
    suspend fun kick(roomId:String,userId:String):Result<Unit> = runCatching{val res=api.moderateKick(apiKey,auth,mapOf("p_room_id" to roomId,"p_target_user" to userId));if(!res.isSuccessful)error("kick HTTP ${res.code()}: ${res.errorBody()?.string()}")}
    suspend fun ban(roomId:String,userId:String,reason:String?):Result<Unit> = runCatching{val res=api.moderateBan(apiKey,auth,mapOf("p_room_id" to roomId,"p_target_user" to userId,"p_reason" to reason));if(!res.isSuccessful)error("ban HTTP ${res.code()}: ${res.errorBody()?.string()}")}
    suspend fun invite(roomId:String,userId:String):Result<Unit> = runCatching{val res=api.inviteUser(apiKey,auth,mapOf("p_room_id" to roomId,"p_user_id" to userId));if(!res.isSuccessful)error("invite HTTP ${res.code()}")}
    suspend fun updateRoomSettings(roomId:String,name:String?,description:String?,coverUrl:String?,category:String?,visibility:String?,isLocked:Boolean?):Result<VoiceRoom> = runCatching{name?.let{require(it.trim().length in 2..80){"El nombre debe tener entre 2 y 80 caracteres"}};description?.let{require(it.length<=280){"La descripción no puede superar 280 caracteres"}};if(visibility!=null)require(visibility in setOf("public","private")){"Visibilidad inválida"};val res=Resilience.retry{api.updateRoomSettings(apiKey,auth,UpdateRoomSettingsRequest(roomId,name?.trim(),description?.trim(),coverUrl?.trim()?.ifEmpty{null},category,visibility,isLocked))};if(!res.isSuccessful)error("updateRoomSettings HTTP ${res.code()}: ${res.errorBody()?.string()}");res.body()?.firstOrNull()?.toModel()?:error("No se pudo actualizar la sala")}
    suspend fun deleteRoom(roomId:String):Result<Unit> = runCatching{val res=Resilience.retry{api.deleteRoom(apiKey,auth,mapOf("p_room_id" to roomId))};if(!res.isSuccessful)error("deleteRoom HTTP ${res.code()}: ${res.errorBody()?.string()}")}
    suspend fun getBannedUsers(roomId:String):Result<List<VoiceRoomBanDto>> = runCatching{val res=Resilience.retry{api.getBannedUsers(apiKey,auth,mapOf("p_room_id" to roomId))};if(!res.isSuccessful)error("getBannedUsers HTTP ${res.code()}");res.body().orEmpty()}
    suspend fun removeBan(roomId:String,userId:String):Result<Unit> = runCatching{val res=Resilience.retry{api.removeBan(apiKey,auth,mapOf("p_room_id" to roomId,"p_user_id" to userId))};if(!res.isSuccessful)error("removeBan HTTP ${res.code()}")}
    suspend fun getRoomDecor(roomId:String):Result<com.example.rooms.data.VoiceRoomDecorDto?> = runCatching{val res=Resilience.retry{api.getRoomDecor(apiKey,auth,mapOf("p_room_id" to roomId))};if(!res.isSuccessful)error("getRoomDecor HTTP ${res.code()}");res.body()?.firstOrNull()}
    suspend fun setRoomEntrance(roomId:String,code:String):Result<com.example.rooms.data.VoiceRoomDecorDto?> = runCatching{val res=Resilience.retry{api.setRoomEntrance(apiKey,auth,mapOf("p_room_id" to roomId,"p_code" to code))};if(!res.isSuccessful)error("setRoomEntrance HTTP ${res.code()}: ${res.errorBody()?.string()}");res.body()?.firstOrNull()}
    suspend fun setRoomPendant(roomId:String,code:String):Result<com.example.rooms.data.VoiceRoomDecorDto?> = runCatching{val res=Resilience.retry{api.setRoomPendant(apiKey,auth,mapOf("p_room_id" to roomId,"p_code" to code))};if(!res.isSuccessful)error("setRoomPendant HTTP ${res.code()}: ${res.errorBody()?.string()}");res.body()?.firstOrNull()}
    /** Guarda el colgante personal en `profiles` (RLS: solo el propio usuario). */
    suspend fun setMyPendant(code:String):Result<Unit> = runCatching{
        val uid=myId;if(uid.isEmpty())error("Sesión no iniciada")
        var res=Resilience.retry{api.updateProfileMap(apiKey,auth,"eq.$uid",mapOf("pendant_code" to code))}
        if(res.code()==401||res.code()==403){
            if(SessionManager.refreshSession()){
                val refreshedToken=SupabaseClient.currentToken
                val refreshedAuth = if(refreshedToken.isNullOrEmpty()) auth else "Bearer $refreshedToken"
                res=Resilience.retry{api.updateProfileMap(apiKey,refreshedAuth,"eq.$uid",mapOf("pendant_code" to code))}
            }
        }
        if(!res.isSuccessful)error("setMyPendant HTTP ${res.code()}: ${res.errorBody()?.string()}")
    }
    suspend fun recordEntrance(roomId:String,code:String):Result<Unit> = runCatching{val res=Resilience.retry{api.recordEntrance(apiKey,auth,mapOf("p_room_id" to roomId,"p_code" to code))};if(!res.isSuccessful)error("recordEntrance HTTP ${res.code()}: ${res.errorBody()?.string()}")}
    suspend fun getPublicProfiles(userIds:List<String>):Result<Map<String,PublicProfileDto>> = runCatching{val ids=userIds.filter{it.isNotBlank()}.distinct();if(ids.isEmpty())return@runCatching emptyMap();val res=api.getPublicProfiles(apiKey,auth,"in.(${ids.joinToString(",")})");if(!res.isSuccessful)error("getPublicProfiles HTTP ${res.code()}");res.body().orEmpty().associateBy{it.id}}
    suspend fun getSeats(roomId:String):Result<List<VoiceRoomSeatDto>> = runCatching{val res=api.getSeats(apiKey,auth,"eq.$roomId");if(!res.isSuccessful)error("getSeats HTTP ${res.code()}");res.body().orEmpty()}
    suspend fun getMembers(roomId:String):Result<List<VoiceRoomMember>> = runCatching{val res=api.getMembers(apiKey,auth,"eq.$roomId");if(!res.isSuccessful)error("getMembers HTTP ${res.code()}");res.body().orEmpty().map{VoiceRoomMember(it.id,it.roomId,it.userId,it.role,it.joinedAt)} }
    suspend fun getMemberCount(roomId:String):Result<Int> = runCatching{getMembers(roomId).getOrThrow().size}
    suspend fun getMessages(roomId:String):Result<List<VoiceRoomMessageDto>> = runCatching{val res=api.getMessages(apiKey,auth,"eq.$roomId");if(!res.isSuccessful)error("getMessages HTTP ${res.code()}");res.body().orEmpty().asReversed()}
    suspend fun sendMessage(roomId:String,content:String):Result<VoiceRoomMessageDto> = runCatching{val res=api.sendMessage(apiKey,auth,mapOf("room_id" to roomId,"sender_id" to myId,"content" to content.trim()));if(!res.isSuccessful)error("sendMessage HTTP ${res.code()}: ${res.errorBody()?.string()}");res.body()!!.first()}
    private fun VoiceRoomDto.toModel()=VoiceRoom(id,name,ownerId,status,maxSeats,description,coverUrl,category,visibility,isLocked)
    private fun VoiceRoomSeatRequestDto.toModel()=VoiceRoomSeatRequest(id,roomId,userId,requestedSeatIndex,status,createdAt)
    fun VoiceRoomMessageDto.toModel(senderName:String?=null)=VoiceRoomMessage(id,roomId,senderId,senderName,content,createdAt)
}
