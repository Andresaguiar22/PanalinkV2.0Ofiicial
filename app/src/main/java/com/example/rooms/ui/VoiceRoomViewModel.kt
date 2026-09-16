package com.example.rooms.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.LiveKitFeatureGate
import com.example.data.supabase.SupabaseClient
import com.example.rooms.model.*
import com.example.rooms.repository.VoiceRoomRepository
import com.example.rooms.signaling.SupabaseVoiceRoomSignaling
import com.example.rooms.signaling.VoiceRoomSignaling
import com.example.rooms.webrtc.VoiceRoomAudioManager
import com.example.rooms.webrtc.VoiceRoomEngine
import com.example.rooms.webrtc.VoiceRoomWebRtcEngine
import com.example.rooms.webrtc.LiveKitVoiceRoomEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.webrtc.IceCandidate

class VoiceRoomViewModel(app:Application):AndroidViewModel(app){
    companion object{private const val TAG="VoiceRoomVM"}
    private val repository=VoiceRoomRepository.getInstance();private val myId:String get()=SupabaseClient.currentUser?.id?:""
    private val _uiState=MutableStateFlow(VoiceRoomUiState(myUserId=myId));val uiState:StateFlow<VoiceRoomUiState> = _uiState
    private var signaling:VoiceRoomSignaling?=null;private var rtcEngine:VoiceRoomEngine?=null;private var audioManager:VoiceRoomAudioManager?=null;private var roomId:String?=null;private var hasAudioPermission=false;private var hadConnection=false
    private val cleanupScope=CoroutineScope(Dispatchers.IO+SupervisorJob())
    private var audioRecord:android.media.AudioRecord?=null
    private var audioLevelsJob:kotlinx.coroutines.Job?=null

    fun enterRoom(targetRoomId:String?=null){if(roomId!=null)return;viewModelScope.launch{_uiState.update{it.copy(isJoining=true,error=null)};if(targetRoomId==null){_uiState.update{it.copy(isJoining=false,error="Selecciona una sala")};return@launch};repository.getRoomById(targetRoomId).onSuccess{setupRoom(it)}.onFailure{e->_uiState.update{it.copy(isJoining=false,error=e.message?:"No se pudo cargar la sala")}}}}
    private suspend fun setupRoom(room:VoiceRoom){roomId=room.id;val joined=repository.joinRoom(room.id);if(joined.isFailure){roomId=null;_uiState.update{it.copy(isJoining=false,error=joined.exceptionOrNull()?.message?:"No se pudo entrar a la sala")};return};_uiState.update{it.copy(room=room)};startAudio();startSignaling(room.id);refreshSnapshot(room.id);applyRoomDecor();recordMyEntrance();if(roomId!=null)_uiState.update{it.copy(isJoining=false)}}
    fun leaveRoom(){val id=roomId?:return;cleanupScope.launch{repository.leaveRoom(id)};teardown()}
    private fun teardown(){val sig=signaling;cleanupScope.launch{sig?.leaveRoom()};rtcEngine?.release();rtcEngine=null;audioManager?.exitRoomAudio();audioManager=null;signaling=null;roomId=null;hadConnection=false;stopAudioLevelCapture();_uiState.value=VoiceRoomUiState(myUserId=myId)}
    override fun onCleared(){val id=roomId;val sig=signaling;if(id!=null)cleanupScope.launch{repository.leaveRoom(id);sig?.leaveRoom()};rtcEngine?.release();audioManager?.exitRoomAudio();stopAudioLevelCapture();cleanupScope.cancel()}

    fun onSeatClicked(seatIndex:Int,hasRecordPermission:Boolean){val state=_uiState.value;val id=roomId?:return;hasAudioPermission=hasRecordPermission;if(seatIndex !in 0..8)return;if(seatIndex==0&&!state.isAdmin)return;val mine=state.mySeat;if(seatIndex==0&&mine!=null&&mine.index==0&&state.isHost){_uiState.update{it.copy(error="El anfitrión no puede dejar su sillón")};return};viewModelScope.launch{if(mine!=null){if(mine.index==seatIndex){repository.leaveSeat(id).onSuccess{refreshSnapshot(id);stopAudioLevelCapture();rtcEngine?.setMicEnabled(false);audioManager?.exitRoomAudio();_uiState.update{it.copy(isMicEnabled=false)}}.onFailure{e->_uiState.update{it.copy(error=e.message?:"No se pudo dejar el sillón")}}}else if(state.seats.getOrNull(seatIndex)?.isOccupied!=true||state.isAdmin){stopAudioLevelCapture();repository.moveSeat(id,seatIndex).onSuccess{refreshSnapshot(id);if(hasAudioPermission){audioManager?.enterRoomAudio();rtcEngine?.setMicEnabled(true);_uiState.update{it.copy(isMicEnabled=true)};startAudioLevelCapture()}}.onFailure{e->_uiState.update{it.copy(error=e.message?:"No se pudo cambiar de sillón")}}}}else if(state.seats.getOrNull(seatIndex)?.isOccupied!=true||state.isAdmin){repository.moveSeat(id,seatIndex).onSuccess{refreshSnapshot(id);if(hasAudioPermission){audioManager?.enterRoomAudio();rtcEngine?.setMicEnabled(true);_uiState.update{it.copy(isMicEnabled=true)};startAudioLevelCapture()}}.onFailure{e->_uiState.update{it.copy(error=e.message?:"No se pudo ocupar el sillón")}}}}}
    fun requestAnySeat(){val id=roomId?:return;if(_uiState.value.isSeated||_uiState.value.pendingSeatRequest!=null)return;viewModelScope.launch{repository.requestSeat(id,null).onSuccess{refreshSnapshot(id)}.onFailure{e->_uiState.update{it.copy(error=e.message?:"No se pudo solicitar un sillón")}}}}
    fun approveSeatRequest(requestId:String,seatIndex:Int?=null){viewModelScope.launch{repository.resolveSeatRequest(requestId,true,seatIndex).onSuccess{roomId?.let{refreshSnapshot(it)}}.onFailure{e->_uiState.update{it.copy(error=e.message?:"No se pudo aprobar la solicitud")}}}}
    fun denySeatRequest(requestId:String){viewModelScope.launch{repository.resolveSeatRequest(requestId,false,null).onSuccess{roomId?.let{refreshSnapshot(it)}}}}

    fun onAudioPermissionResult(granted:Boolean){hasAudioPermission=granted;if(granted&&_uiState.value.isSeated&&!(_uiState.value.mySeat?.isMuted?:false)){audioManager?.enterRoomAudio();rtcEngine?.setMicEnabled(true);_uiState.update{it.copy(isMicEnabled=true)};startAudioLevelCapture()}else stopAudioLevelCapture()}
    fun toggleMute(){val id=roomId?:return;val seat=_uiState.value.mySeat?:return;val muted=!seat.isMuted;viewModelScope.launch{repository.moderateMute(id,myId,muted)};rtcEngine?.setMicEnabled(!muted&&hasAudioPermission);if(muted)audioManager?.exitRoomAudio()else if(hasAudioPermission&&_uiState.value.isSeated)audioManager?.enterRoomAudio();_uiState.update{it.copy(seats=VoiceRoomSeatReducer.setMuted(it.seats,myId,muted),isMicEnabled=!muted&&hasAudioPermission)};if(muted)stopAudioLevelCapture()else if(hasAudioPermission&&_uiState.value.isSeated)startAudioLevelCapture()}
    fun moderateMute(userId:String,muted:Boolean){val id=roomId?:return;if(!_uiState.value.isAdmin)return;viewModelScope.launch{repository.moderateMute(id,userId,muted).onSuccess{_uiState.update{it.copy(seats=VoiceRoomSeatReducer.setMuted(it.seats,userId,muted))}}}}
    fun kickUser(userId:String){val id=roomId?:return;if(!_uiState.value.isAdmin)return;viewModelScope.launch{repository.kick(id,userId).onFailure{e->_uiState.update{it.copy(error=e.message)}}}}
    fun banUser(userId:String,reason:String?=null){val id=roomId?:return;if(!_uiState.value.isAdmin)return;viewModelScope.launch{repository.ban(id,userId,reason).onFailure{e->_uiState.update{it.copy(error=e.message)}}}}
    fun setAdmin(userId:String,makeAdmin:Boolean){val id=roomId?:return;if(!_uiState.value.isHost)return;viewModelScope.launch{repository.setAdmin(id,userId,makeAdmin).onSuccess{refreshSnapshot(id)}}}
    fun inviteUser(userId:String){val id=roomId?:return;if(!_uiState.value.isAdmin)return;viewModelScope.launch{repository.invite(id,userId)}}
    fun openSettings(){if(!_uiState.value.isAdmin)return;viewModelScope.launch{val id=roomId?:return@launch;_uiState.update{it.copy(showSettings=true)};repository.getBannedUsers(id).onSuccess{bans->_uiState.update{it.copy(bannedUsers=bans)}}.onFailure{e->_uiState.update{it.copy(settingsMessage=e.message?:"No se pudieron cargar los baneados")}}}}
    fun closeSettings(){_uiState.update{it.copy(showSettings=false,settingsMessage=null)}}
    fun updateRoomSettings(name:String?,description:String?,coverUrl:String?,category:String?,visibility:String?,isLocked:Boolean?){val id=roomId?:return;if(!_uiState.value.isHost)return;_uiState.update{it.copy(isSettingsSaving=true,settingsMessage=null)};viewModelScope.launch{repository.updateRoomSettings(id,name,description,coverUrl,category,visibility,isLocked).onSuccess{room->_uiState.update{s->s.copy(room=room,isSettingsSaving=false,settingsMessage="Ajustes guardados")}}.onFailure{e->_uiState.update{it.copy(isSettingsSaving=false,settingsMessage=e.message?:"No se pudieron guardar los ajustes")}}}}
    fun deleteRoom(onDeleted:()->Unit){val id=roomId?:return;if(!_uiState.value.isHost)return;_uiState.update{it.copy(isSettingsSaving=true)};viewModelScope.launch{repository.deleteRoom(id).onSuccess{_uiState.update{it.copy(showSettings=false)};teardown();onDeleted()}.onFailure{e->_uiState.update{it.copy(isSettingsSaving=false,settingsMessage=e.message?:"No se pudo borrar la sala")}}}}
    fun removeBan(userId:String){val id=roomId?:return;if(!_uiState.value.isAdmin)return;viewModelScope.launch{repository.removeBan(id,userId).onSuccess{repository.getBannedUsers(id).onSuccess{bans->_uiState.update{it.copy(bannedUsers=bans)}}}}}
    fun clearSettingsMessage(){_uiState.update{it.copy(settingsMessage=null)}}
    fun sendMessage(text:String){val id=roomId?:return;val value=text.trim();if(value.isEmpty())return;viewModelScope.launch{repository.sendMessage(id,value).onFailure{_uiState.update{it.copy(error="No se pudo enviar el mensaje")}}}}
    fun clearError(){_uiState.update{it.copy(error=null)}}
    fun loadRoomDecor(){val id=roomId?:return;viewModelScope.launch{repository.getRoomDecor(id).onSuccess{dec->
        if(dec!=null){_uiState.update{it.copy(entranceCode=dec.entranceCode?:"sparkle",pendantCode=dec.pendantCode?:"none")}}
    }.onFailure{/* decor opcional: no romper la sala */}}}
    private suspend fun applyRoomDecor(){val id=roomId?:return;val dec=runCatching{repository.getRoomDecor(id)}.getOrNull()?.getOrNull();if(dec!=null){_uiState.update{s->s.copy(entranceCode=dec.entranceCode?:"sparkle",pendantCode=dec.pendantCode?:"none")}}}
    fun closeToolbox(){_uiState.update{it.copy(showToolbox=false)}}
    fun openToolbox(){if(!_uiState.value.isAdmin)return;loadRoomDecor();_uiState.update{it.copy(showToolbox=true)}}
    fun setEntrance(code:String){val id=roomId?:return;if(!_uiState.value.isAdmin)return;_uiState.update{it.copy(entranceCode=code)};viewModelScope.launch{repository.setRoomEntrance(id,code).onFailure{e->_uiState.update{it.copy(error=e.message?:"No se pudo guardar la entrada")}}}}
    fun setPendant(code:String){val id=roomId?:return;if(!_uiState.value.isAdmin)return;_uiState.update{it.copy(pendantCode=code)};viewModelScope.launch{repository.setRoomPendant(id,code).onFailure{e->_uiState.update{it.copy(error=e.message?:"No se pudo guardar el colgante")}}}}
    /** Guarda el colgante PERSONAL del usuario: viaja en su perfil y se ve en
     *  cualquier sala a la que entre (no depende del dueño de la sala). */
    fun setMyPendant(code:String){if(code.isBlank())return;val uid=myId;if(uid.isEmpty())return;_uiState.update{it.copy(myPendantCode=code,seats=VoiceRoomSeatReducer.updatePendant(it.seats,uid,code))};viewModelScope.launch{repository.setMyPendant(code).onFailure{e->_uiState.update{it.copy(error=e.message?:"No se pudo guardar tu colgante")}}}}
    fun onEntranceEvent(event:VoiceRoomEntranceEvent){_uiState.update{it.copy(entranceEvent=event)}}
    fun clearEntranceEvent(){_uiState.update{it.copy(entranceEvent=null)}}
    fun recordMyEntrance(){val id=roomId?:return;val code=_uiState.value.entranceCode;if(code.isBlank()||code=="none")return;viewModelScope.launch{repository.recordEntrance(id,code).onFailure{/* no crítico: si falla, los joins locales igual muestran el mensaje */}}}

    private fun startAudio(){if(rtcEngine==null){val id=roomId?:return;val l=object:VoiceRoomEngine.Listener{override fun onLocalIceCandidate(toUserId:String,candidate:IceCandidate){viewModelScope.launch{signaling?.sendIceCandidate(roomId?:return@launch,toUserId,candidate.sdpMid?:"",candidate.sdpMLineIndex,candidate.sdp)}};override fun onPeerSpeaking(userId:String,speaking:Boolean){_uiState.update{it.copy(seats=VoiceRoomSeatReducer.setSpeaking(it.seats,userId,speaking))}};override fun onPeerConnectionStateChanged(userId:String,connected:Boolean){Log.d(TAG,"peer=$userId connected=$connected")};override suspend fun sendOfferTo(userId:String,sdp:String){signaling?.sendOffer(roomId?:return,userId,sdp)};override suspend fun sendAnswerTo(userId:String,sdp:String){signaling?.sendAnswer(roomId?:return,userId,sdp)}};rtcEngine=if(LiveKitFeatureGate.isEnabled())LiveKitVoiceRoomEngine(getApplication(),myId,id,l) else VoiceRoomWebRtcEngine(getApplication(),myId,l);rtcEngine!!.initialize()};if(audioManager==null)audioManager=VoiceRoomAudioManager(getApplication());rtcEngine?.setMicEnabled(_uiState.value.isSeated&&hasAudioPermission&&!(_uiState.value.mySeat?.isMuted?:false));if(_uiState.value.isSeated&&hasAudioPermission)startAudioLevelCapture()}
    private fun startSignaling(id:String){val sig=SupabaseVoiceRoomSignaling(myId);signaling=sig;viewModelScope.launch{sig.joinRoom(id)};viewModelScope.launch{sig.tableEvents.collect{onTableEvent(it)}};viewModelScope.launch{sig.signalEvents.collect{onSignalEvent(it)}};viewModelScope.launch{sig.connectionState.collect{connected->if(connected){if(hadConnection)onSignalingReconnected();hadConnection=true}}}}
    private fun onSignalingReconnected(){val id=roomId?:return;viewModelScope.launch{_uiState.value.seats.mapNotNull{it.userId}.filter{it!=myId}.forEach{signaling?.sendPeerReset(id,it)};rtcEngine?.resetPeers();refreshSnapshot(id)}}

    private suspend fun enrichMembersAndSeats(id:String,members:List<VoiceRoomMember>):Map<String,com.example.rooms.data.PublicProfileDto>{val ids=members.map{it.userId} + _uiState.value.seats.mapNotNull{it.userId};return repository.getPublicProfiles(ids).getOrDefault(emptyMap())}
    private suspend fun refreshSnapshot(id:String){
        if(roomId!=id)return
        repository.getRoomById(id).onSuccess{room->_uiState.update{s->s.copy(room=room)}}
        val membersResult=repository.getMembers(id)
        var profileMap=emptyMap<String,com.example.rooms.data.PublicProfileDto>()
        membersResult.onSuccess{members->
            profileMap=enrichMembersAndSeats(id,members)
            val enriched=members.map{m->m.copy(displayName=profileMap[m.userId]?.displayName,avatarUrl=profileMap[m.userId]?.avatarUrl)}
            val me=enriched.firstOrNull{it.userId==myId}
            if(me==null){_uiState.update{it.copy(error="Ya no perteneces a esta sala")};teardown();return@onSuccess}
            val myProfile=profileMap[myId]
            if(myProfile?.pendantCode.isNullOrBlank().not()&&myProfile?.pendantCode!="none"){_uiState.update{it.copy(members=enriched,myRole=me.role,memberCount=enriched.size,myPendantCode=myProfile?.pendantCode)}}
            else{_uiState.update{it.copy(members=enriched,myRole=me.role,memberCount=enriched.size)}}
        }
        if(roomId!=id)return
        repository.getSeats(id).onSuccess{dtos->profileMap=repository.getPublicProfiles(dtos.map{it.userId}).getOrDefault(profileMap);var seats=VoiceRoomUiState.emptySeats();dtos.forEach{dto->val p=profileMap[dto.userId];seats=VoiceRoomSeatReducer.occupy(seats,dto.seatIndex,dto.userId,p?.displayName,p?.avatarUrl,p?.pendantCode);if(dto.isMuted)seats=VoiceRoomSeatReducer.setMuted(seats,dto.userId,true)};val previous=_uiState.value.seats.mapNotNull{it.userId}.toSet();val current=dtos.map{it.userId}.toSet();previous.filter{it!=myId&&it !in current}.forEach{rtcEngine?.onPeerLeft(it)};_uiState.update{it.copy(seats=seats)};dtos.filter{it.userId!=myId}.forEach{rtcEngine?.onPeerJoined(it.userId)}}
        repository.getSeatRequests(id).onSuccess{reqs->_uiState.update{s->s.copy(seatRequests=reqs)}}
        repository.getMessages(id).onSuccess{dtos->
            val names=repository.getPublicProfiles(dtos.map{it.senderId}).getOrDefault(emptyMap())
            val merged=(dtos.map{d->VoiceRoomMessage(d.id,d.roomId,d.senderId,names[d.senderId]?.displayName,d.content,d.createdAt)}+_uiState.value.messages.filter{it.isSystem})
                .distinctBy{it.id}.takeLast(VoiceRoomMessagesReducer.HISTORY_CAP)
            _uiState.update{it.copy(messages=merged)}
        }
    }

    private fun announceJoin(userId:String,joinedAt:String){viewModelScope.launch{val id=roomId?:return@launch;if(userId==myId)return@launch;val p=repository.getPublicProfiles(listOf(userId)).getOrDefault(emptyMap())[userId];val name=p?.displayName?.takeIf{it.isNotBlank()}?:"Usuario";val system=VoiceRoomMessage("join:$userId:$joinedAt",id,"","", "$name se unió a la sala",joinedAt,true);_uiState.update{s->s.copy(messages=VoiceRoomMessagesReducer.append(s.messages,system))}}}
    private fun announceLeave(userId:String,leftAt:String){viewModelScope.launch{val id=roomId?:return@launch;val p=repository.getPublicProfiles(listOf(userId)).getOrDefault(emptyMap())[userId];val name=p?.displayName?.takeIf{it.isNotBlank()}?:"Usuario";val system=VoiceRoomMessage("leave:$userId:$leftAt",id,"","", "$name salió de la sala",leftAt,true);_uiState.update{s->s.copy(messages=VoiceRoomMessagesReducer.append(s.messages,system))}}}

    private fun onTableEvent(ev:VoiceRoomSignaling.TableEvent){val id=roomId?:return;when(ev.table){"voice_room_seats"->when(ev.eventType){"INSERT"->{val idx=ev.record.optInt("seat_index",-1);val uid=ev.record.optString("user_id");if(idx>=0&&uid.isNotEmpty()){viewModelScope.launch{val p=repository.getPublicProfiles(listOf(uid)).getOrDefault(emptyMap())[uid];_uiState.update{s->s.copy(seats=VoiceRoomSeatReducer.occupy(s.seats,idx,uid,p?.displayName,p?.avatarUrl,p?.pendantCode))};if(p?.displayName.isNullOrBlank()){delay(1000);refreshSnapshot(id)}};if(uid!=myId)rtcEngine?.onPeerJoined(uid)}};"UPDATE"->{val idx=ev.record.optInt("seat_index",-1);val uid=ev.record.optString("user_id");_uiState.update{s->val hadUid=s.seats.any{it.userId==uid};var seats=s.seats;val prev=s.seats.firstOrNull{old->old.userId==uid};if(uid.isNotEmpty())seats=VoiceRoomSeatReducer.release(seats,uid);if(idx>=0){seats=VoiceRoomSeatReducer.releaseSeat(seats,idx);if(uid.isNotEmpty())seats=VoiceRoomSeatReducer.occupy(seats,idx,uid,null,null,prev?.pendantCode)};if(uid.isNotEmpty())seats=VoiceRoomSeatReducer.setMuted(seats,uid,ev.record.optBoolean("is_muted",false));if(uid.isNotEmpty()&&uid!=myId&&!hadUid)rtcEngine?.onPeerJoined(uid);if(uid==myId)rtcEngine?.setMicEnabled(ev.record.optBoolean("is_muted",false).not()&&hasAudioPermission);s.copy(seats=seats)}};"DELETE"->{val uid=ev.record.optString("user_id");if(uid.isNotEmpty()){_uiState.update{s->s.copy(seats=VoiceRoomSeatReducer.release(s.seats,uid),isMicEnabled=if(uid==myId)false else s.isMicEnabled)};if(uid==myId)rtcEngine?.setMicEnabled(false) else rtcEngine?.onPeerLeft(uid)}}};"voice_room_members"->when(ev.eventType){"INSERT"->{val uid=ev.record.optString("user_id");if(uid.isNotEmpty())announceJoin(uid,ev.record.optString("joined_at"));viewModelScope.launch{refreshSnapshot(id)}};"DELETE"->{val uid=ev.record.optString("user_id");if(uid.isNotEmpty())announceLeave(uid,ev.record.optString("left_at").ifBlank{SupabaseClient.getNowIsoString()});viewModelScope.launch{refreshSnapshot(id)}};else->viewModelScope.launch{refreshSnapshot(id)}};"voice_room_seat_requests"->viewModelScope.launch{repository.getSeatRequests(id).onSuccess{reqs->_uiState.update{s->s.copy(seatRequests=reqs)}};repository.getSeats(id).onSuccess{dtos->var seats=VoiceRoomUiState.emptySeats();dtos.forEach{d->seats=VoiceRoomSeatReducer.occupy(seats,d.seatIndex,d.userId,null,null);if(d.isMuted)seats=VoiceRoomSeatReducer.setMuted(seats,d.userId,true)};_uiState.update{it.copy(seats=seats)}}};"voice_room_bans"->if(ev.eventType=="INSERT"&&ev.record.optString("user_id")==myId)leaveRoom();"voice_room_decor"->{if(ev.eventType=="INSERT"||ev.eventType=="UPDATE"){val ent=ev.record.optString("entrance_code");val pend=ev.record.optString("pendant_code");if(ent.isNotEmpty()||pend.isNotEmpty()){_uiState.update{it.copy(entranceCode=ent.ifEmpty{it.entranceCode},pendantCode=pend.ifEmpty{it.pendantCode})}}}};"voice_room_entrance_events"->if(ev.eventType=="INSERT"){val uid=ev.record.optString("user_id");val code=ev.record.optString("entrance_code");if(uid.isNotEmpty()&&uid!=myId&&code.isNotEmpty()){viewModelScope.launch{val p=repository.getPublicProfiles(listOf(uid)).getOrDefault(emptyMap())[uid];_uiState.update{s->s.copy(entranceEvent=VoiceRoomEntranceEvent(uid,code,p?.displayName?.takeIf{it.isNotBlank()}?:"Usuario",p?.avatarUrl))}}}};"voice_room_messages"->if(ev.eventType=="INSERT"){val m=VoiceRoomMessage(ev.record.optString("id"),ev.record.optString("room_id"),ev.record.optString("sender_id"),null,ev.record.optString("content"),ev.record.optString("created_at"));viewModelScope.launch{val p=repository.getPublicProfiles(listOf(m.senderId)).getOrDefault(emptyMap())[m.senderId];_uiState.update{s->s.copy(messages=VoiceRoomMessagesReducer.append(s.messages,m.copy(senderName=p?.displayName)))}}}}}
    private fun onSignalEvent(ev:VoiceRoomSignaling.SignalEvent){val engine=rtcEngine?:return;viewModelScope.launch{when(ev.type){"offer"->engine.onRemoteOffer(ev.fromUserId,ev.payload.optString("sdp"));"answer"->engine.onRemoteAnswer(ev.fromUserId,ev.payload.optString("sdp"));"ice"->engine.onRemoteIceCandidate(ev.fromUserId,ev.payload.optString("sdpMid"),ev.payload.optInt("sdpMLineIndex"),ev.payload.optString("candidate"));"peer_reset"->{engine.onPeerLeft(ev.fromUserId);engine.onPeerJoined(ev.fromUserId)}}}}

    /**
     * Niveles de audio para el visualizador (solo camino legacy P2P).
     *
     * CON LiveKit NO se abre un AudioRecord propio: el SDK ya captura el micrófono
     * con cancelación de eco/ruido (ver LiveKitVoiceRoomEngine.buildRoomOptions).
     * Abrir un segundo micrófono en paralelo (AudioRecord/MIC) producía eco de la
     * propia voz al hablar y "chorro" metálico en varios dispositivos.
     */
    private fun startAudioLevelCapture() {
        if (LiveKitFeatureGate.isEnabled()) return
        if (audioLevelsJob != null) return
        val id = roomId ?: return
        if (!hasAudioPermission) return
        audioLevelsJob = cleanupScope.launch(Dispatchers.IO) {
            try {
                val sampleRate = 44100
                val channelConfig = android.media.AudioFormat.CHANNEL_IN_MONO
                val audioFormat = android.media.AudioFormat.ENCODING_PCM_16BIT
                val minBuf = android.media.AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
                val buffer = java.lang.reflect.Array.newInstance(java.lang.Short.TYPE, minBuf) as ShortArray
                audioRecord = android.media.AudioRecord(android.media.MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, minBuf)
                audioRecord?.startRecording()
                while (isActive && audioRecord != null) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        var sum = 0.0
                        for (i in 0 until read) { val v = buffer[i].toDouble(); sum += v * v }
                        val rms = kotlin.math.sqrt(sum / read)
                        val normalized = (rms / 32768.0).coerceIn(0.0, 1.0).toFloat()
                        val levels = List(4) { normalized }
                        _uiState.update { s -> s.copy(seats = VoiceRoomSeatReducer.setAudioLevels(s.seats, myId, levels)) }
                    }
                    delay(80)
                }
            } catch (e: Exception) { Log.w(TAG, "audio levels capture failed", e) }
        }
    }

    private fun stopAudioLevelCapture() {
        audioLevelsJob?.cancel()
        audioLevelsJob = null
        try { audioRecord?.stop(); audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
    }
}
