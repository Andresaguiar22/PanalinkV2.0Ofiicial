package com.example.rooms.signaling

import android.util.Log
import com.example.data.supabase.SupabaseClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SupabaseVoiceRoomSignaling(private val myUserId:String): VoiceRoomSignaling {
    companion object { private const val TAG="VoiceRoomSignaling"; private const val BROADCAST_TOPIC_PREFIX="realtime:voice_room:"; fun broadcastTopic(roomId:String)="$BROADCAST_TOPIC_PREFIX$roomId" }
    private val scope=CoroutineScope(Dispatchers.IO+SupervisorJob())
    private val client=OkHttpClient.Builder().connectTimeout(30,TimeUnit.SECONDS).readTimeout(0,TimeUnit.SECONDS).pingInterval(20,TimeUnit.SECONDS).retryOnConnectionFailure(true).build()
    private var webSocket:WebSocket?=null; private var heartbeatJob:Job?=null; private var reconnectJob:Job?=null; private var currentRoomId:String?=null; private var intentionallyClosed=false; private var refCounter=0; private val reconnectPolicy=VoiceRoomReconnectPolicy()
    private val _tableEvents=MutableSharedFlow<VoiceRoomSignaling.TableEvent>(extraBufferCapacity=256); override val tableEvents:SharedFlow<VoiceRoomSignaling.TableEvent> = _tableEvents
    private val _signalEvents=MutableSharedFlow<VoiceRoomSignaling.SignalEvent>(extraBufferCapacity=128); override val signalEvents:SharedFlow<VoiceRoomSignaling.SignalEvent> = _signalEvents
    private val _connectionState=MutableStateFlow(false); override val connectionState:StateFlow<Boolean> = _connectionState

    override suspend fun joinRoom(roomId:String){intentionallyClosed=false;currentRoomId=roomId;connect()}
    override suspend fun leaveRoom(){intentionallyClosed=true;currentRoomId=null;reconnectJob?.cancel();heartbeatJob?.cancel();try{webSocket?.close(1000,"leave_room")}catch(_:Exception){};webSocket=null;_connectionState.value=false}

    private fun connect(){
        val roomId=currentRoomId?:return; val token=SupabaseClient.currentToken
        var wsUrl=SupabaseClient.supabaseUrl.replace("https://","wss://").replace("http://","ws://").removeSuffix("/")+"/realtime/v1/websocket?apikey=${SupabaseClient.supabaseAnonKey}&vsn=1.0.0"
        if(!token.isNullOrEmpty())wsUrl+="&token=$token"
        webSocket=client.newWebSocket(Request.Builder().url(wsUrl).build(),object:WebSocketListener(){
            override fun onOpen(ws:WebSocket,response:Response){reconnectPolicy.reset();_connectionState.value=true;joinChannels(ws,roomId,token);startHeartbeat(ws)}
            override fun onMessage(ws:WebSocket,text:String){handleFrame(text)}
            override fun onFailure(ws:WebSocket,t:Throwable,response:Response?){Log.e(TAG,"WS fallo: ${t.message}");_connectionState.value=false;scheduleReconnect()}
            override fun onClosed(ws:WebSocket,code:Int,reason:String){_connectionState.value=false;if(!intentionallyClosed)scheduleReconnect()}
        })
    }

    private fun joinChannels(ws:WebSocket,roomId:String,token:String?){
        listOf("voice_room_seats","voice_room_members","voice_room_messages","voice_room_seat_requests","voice_room_bans","voice_room_invites","voice_room_decor","voice_room_entrance_events").forEach{ws.send(buildPgChangeJoin(it,roomId,token).toString())}
        ws.send(buildBroadcastJoin(roomId,token).toString())
    }
    private fun buildPgChangeJoin(table:String,roomId:String,token:String?)=JSONObject().apply{
        put("topic","realtime:public:$table");put("event","phx_join");put("payload",JSONObject().apply{put("config",JSONObject().apply{put("postgres_changes",org.json.JSONArray().apply{put(JSONObject().apply{put("event","*");put("schema","public");put("table",table);put("filter","room_id=eq.$roomId")})})});if(!token.isNullOrEmpty()){put("user_token",token);put("access_token",token)}});put("ref","vr_${refCounter++}")
    }
    private fun buildBroadcastJoin(roomId:String,token:String?)=JSONObject().apply{put("topic",broadcastTopic(roomId));put("event","phx_join");put("payload",JSONObject().apply{put("config",JSONObject().apply{put("broadcast",JSONObject().apply{put("ack",false);put("self",false)});put("private",true)});if(!token.isNullOrEmpty()){put("user_token",token);put("access_token",token)}});put("ref","vr_${refCounter++}")}

    private fun handleFrame(text:String){try{val obj=JSONObject(text);val event=obj.optString("event");val topic=obj.optString("topic","");if(event=="postgres_changes"){
        val payload=obj.optJSONObject("payload")?:return;val data=payload.optJSONObject("data")?:return;val table=data.optString("table");if(!table.startsWith("voice_room"))return;val type=data.optString("type");val record=when(type){"DELETE"->data.optJSONObject("old_record");else->data.optJSONObject("record")}?:return;scope.launch{_tableEvents.emit(VoiceRoomSignaling.TableEvent(table,type,record))}
    }else if(event=="broadcast"&&topic.startsWith(BROADCAST_TOPIC_PREFIX)){
        val payload=obj.optJSONObject("payload")?:return;val eventName=payload.optString("event");val body=payload.optJSONObject("payload")?:return;val from=body.optString("from");val to=body.optString("to");if(to.isNotEmpty()&&to!=myUserId)return;if(from==myUserId)return;if(eventName in setOf("offer","answer","ice","peer_reset"))scope.launch{_signalEvents.emit(VoiceRoomSignaling.SignalEvent(eventName,from,to,body))}
    }}catch(e:Exception){Log.e(TAG,"Error parseando frame",e)}}

    private fun sendBroadcast(eventName:String,body:JSONObject){val roomId=currentRoomId?:return;webSocket?.send(JSONObject().apply{put("topic",broadcastTopic(roomId));put("event","broadcast");put("payload",JSONObject().apply{put("type","broadcast");put("event",eventName);put("payload",body)});put("ref","vr_${refCounter++}")}.toString())}
    override suspend fun sendOffer(roomId:String,toUserId:String,sdp:String)=sendBroadcast("offer",JSONObject().apply{put("from",myUserId);put("to",toUserId);put("sdp",sdp)})
    override suspend fun sendAnswer(roomId:String,toUserId:String,sdp:String)=sendBroadcast("answer",JSONObject().apply{put("from",myUserId);put("to",toUserId);put("sdp",sdp)})
    override suspend fun sendIceCandidate(roomId:String,toUserId:String,sdpMid:String,sdpMLineIndex:Int,candidate:String)=sendBroadcast("ice",JSONObject().apply{put("from",myUserId);put("to",toUserId);put("sdpMid",sdpMid);put("sdpMLineIndex",sdpMLineIndex);put("candidate",candidate)})
    override suspend fun sendPeerReset(roomId:String,toUserId:String)=sendBroadcast("peer_reset",JSONObject().apply{put("from",myUserId);put("to",toUserId)})
    private fun startHeartbeat(ws:WebSocket){heartbeatJob?.cancel();heartbeatJob=scope.launch{while(isActive){delay(30000);try{ws.send(JSONObject().apply{put("topic","phoenix");put("event","heartbeat");put("payload",JSONObject());put("ref","vr_hb_${System.currentTimeMillis()}")}.toString())}catch(_:Exception){}}}}
    private fun scheduleReconnect(){if(!reconnectPolicy.shouldReconnect(intentionallyClosed,currentRoomId!=null)||reconnectJob?.isActive==true)return;val d=reconnectPolicy.nextDelayMs();reconnectJob=scope.launch{delay(d);if(reconnectPolicy.shouldReconnect(intentionallyClosed,currentRoomId!=null))connect()}}
}
