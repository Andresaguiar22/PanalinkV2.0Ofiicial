package com.example.rooms.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.rooms.model.VoiceRoomMember
import com.example.rooms.model.VoiceRoomSeat

private val RoomBg=Color(0xFF120C0A)
private val RoomAccent=Color(0xFF4ADEAF)
private val RoomGold=Color(0xFFF6C66B)

@Composable
fun VoiceRoomScreenV2(roomId:String,onBack:()->Unit,viewModel:VoiceRoomViewModel=viewModel()){
 com.example.util.KeepScreenOn()
 val state by viewModel.uiState.collectAsState()
 val context=androidx.compose.ui.platform.LocalContext.current
 var moderationTarget by remember{mutableStateOf<String?>(null)}
 var showRequests by remember{mutableStateOf(false)}
 var hasMic by remember{mutableStateOf(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)}
 val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->hasMic=granted;viewModel.onAudioPermissionResult(granted)}
 LaunchedEffect(roomId){viewModel.enterRoom(roomId)}
 DisposableEffect(Unit){onDispose{val activity=context as? android.app.Activity;if(activity?.isChangingConfigurations!=true)viewModel.leaveRoom()}}

 val memberById=remember(state.members){state.members.associateBy{it.userId}}
 val adminCanModerate={seat:VoiceRoomSeat->state.isAdmin&&seat.isOccupied&&seat.userId!=state.myUserId}

 Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF351715),RoomBg)))){
  Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()){
   // Header compacto estilo Starmaker: badge + nombre + ID + contador + acciones
   RoomHeader(
    room=state.room,
    memberCount=state.memberCount,
    showRequestsBadge=state.isAdmin&&state.seatRequests.any{it.status=="pending"},
    onOpenRequests={showRequests=true},
    onClose={viewModel.leaveRoom();onBack()}
   )
   if(state.isJoining)LinearProgressIndicator(Modifier.fillMaxWidth(),color=RoomAccent)
   state.error?.let{Text(it,color=Color(0xFFFF8A80),fontSize=11.sp,modifier=Modifier.padding(horizontal=16.dp,vertical=3.dp))}

   // Anfitrion centrado arriba, sillones pequenos ordenados (estilo Starmaker)
   HostSeatV2(
    seat=state.seats.getOrNull(0),
    mine=(state.seats.getOrNull(0)?.userId==state.myUserId),
    canModerate=(state.seats.getOrNull(0)?.let{adminCanModerate(it)}==true),
    onClick={viewModel.onSeatClicked(0,hasMic)},
    onAdmin={state.seats.getOrNull(0)?.userId?.let{moderationTarget=it}}
   )
   Spacer(Modifier.height(6.dp))
   Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),horizontalArrangement=Arrangement.SpaceEvenly){
    (1..4).forEach{idx->
     val seat=state.seats.getOrNull(idx)
     GuestSeatV2(seat=seat,index=idx,adminAction=(seat?.let{adminCanModerate(it)}==true),onClick={viewModel.onSeatClicked(idx,hasMic)},onAdmin={seat?.userId?.let{moderationTarget=it}})
    }
   }
   Spacer(Modifier.height(6.dp))
   Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),horizontalArrangement=Arrangement.SpaceEvenly){
    (5..8).forEach{idx->
     val seat=state.seats.getOrNull(idx)
     GuestSeatV2(seat=seat,index=idx,adminAction=(seat?.let{adminCanModerate(it)}==true),onClick={viewModel.onSeatClicked(idx,hasMic)},onAdmin={seat?.userId?.let{moderationTarget=it}})
    }
   }

   HorizontalDivider(color=Color(0x26FFFFFF),modifier=Modifier.padding(top=8.dp))

   // Chat: avatar + nombre + burbuja (aprovecha todo el ancho, estilo Starmaker)
   val listState=rememberLazyListState()
   LaunchedEffect(state.messages.size){if(state.messages.isNotEmpty())listState.animateScrollToItem(state.messages.size-1)}
   LazyColumn(state=listState,modifier=Modifier.weight(1f).fillMaxWidth().padding(horizontal=10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
    items(state.messages,key={it.id}){m->
     if(m.isSystem){
      Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){
       Text(m.content,color=RoomGold,fontSize=11.sp,modifier=Modifier.background(Color(0x332A1812),RoundedCornerShape(10.dp)).padding(horizontal=10.dp,vertical=4.dp))
      }
     }else{
      RoomMessageBubble(senderName=m.senderName?:m.senderId.take(8),avatarUrl=memberById[m.senderId]?.avatarUrl,content=m.content)
     }
    }
   }

   // Entrada con boton de micro/sillon integrado (estilo Starmaker)
   Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
    MicSeatButton(
     isSeated=state.isSeated,
     isMuted=state.mySeat?.isMuted==true,
     pendingRequest=state.pendingSeatRequest!=null,
     needsPermission=(!hasMic&&state.mySeat?.isMuted!=true),
     onRequestSeat={viewModel.requestAnySeat()},
     onToggleMute={viewModel.toggleMute()},
     onEnableMic={permission.launch(Manifest.permission.RECORD_AUDIO)}
    )
    var text by remember{mutableStateOf("")}
    OutlinedTextField(
     value=text,
     onValueChange={text=it.take(2000)},
     modifier=Modifier.weight(1f),
     singleLine=true,
     placeholder={Text("Vamos a platicar...",fontSize=13.sp,color=Color.Gray)},
     textStyle=LocalTextStyle.current.copy(fontSize=14.sp),
     colors=OutlinedTextFieldDefaults.colors(
      focusedTextColor=Color.White,unfocusedTextColor=Color.White,
      focusedBorderColor=RoomAccent,unfocusedBorderColor=Color(0x33FFFFFF),
      focusedContainerColor=Color(0x1F000000),unfocusedContainerColor=Color(0x1F000000)
     ),
     shape=RoundedCornerShape(24.dp)
    )
    IconButton(onClick={viewModel.sendMessage(text);text=""},enabled=text.isNotBlank(),modifier=Modifier.size(40.dp)){
     Icon(Icons.Default.Send,"Enviar",tint=if(text.isNotBlank())RoomAccent else Color.Gray)
    }
   }
  }
 }
 if(moderationTarget!=null)ModerationDialog(targetUserId=moderationTarget!!,isHost=state.isHost,targetIsAdmin=state.members.firstOrNull{it.userId==moderationTarget}?.role=="admin",targetMuted=state.seats.firstOrNull{it.userId==moderationTarget}?.isMuted==true,onDismiss={moderationTarget=null},onMute={viewModel.moderateMute(moderationTarget!!,true);moderationTarget=null},onUnmute={viewModel.moderateMute(moderationTarget!!,false);moderationTarget=null},onKick={viewModel.kickUser(moderationTarget!!);moderationTarget=null},onBan={viewModel.banUser(moderationTarget!!);moderationTarget=null},onAdmin={viewModel.setAdmin(moderationTarget!!,true);moderationTarget=null},onRemoveAdmin={viewModel.setAdmin(moderationTarget!!,false);moderationTarget=null})
 if(showRequests)SeatRequestsDialog(state.seatRequests.filter{it.status=="pending"},{id->viewModel.approveSeatRequest(id)},{id->viewModel.denySeatRequest(id)},{showRequests=false})
}

@Composable
private fun RoomHeader(
 room:com.example.rooms.model.VoiceRoom?,
 memberCount:Int,
 showRequestsBadge:Boolean,
 onOpenRequests:()->Unit,
 onClose:()->Unit
){
 Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
  Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF2E1A12)),contentAlignment=Alignment.Center){
   Icon(Icons.Default.Headset,null,tint=RoomAccent,modifier=Modifier.size(20.dp))
  }
  Spacer(Modifier.width(8.dp))
  Column(Modifier.weight(1f)){
   Text(room?.name?:"Sala",color=Color.White,fontWeight=FontWeight.Bold,fontSize=15.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
   Text("ID: ${room?.id?.take(10)?:"…"}",color=RoomGold.copy(.8f),fontSize=11.sp)
  }
  Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0x33000000)).padding(horizontal=8.dp,vertical=4.dp)){
   Icon(Icons.Default.Group,"Miembros",tint=Color.White.copy(.8f),modifier=Modifier.size(14.dp))
   Spacer(Modifier.width(3.dp))
   Text("$memberCount",color=Color.White,fontSize=11.sp,fontWeight=FontWeight.Bold)
  }
  if(room?.isPrivate==true){Spacer(Modifier.width(4.dp));Icon(Icons.Default.Lock,"Privada",tint=RoomGold,modifier=Modifier.size(15.dp))}
  if(showRequestsBadge){
   IconButton(onClick=onOpenRequests,modifier=Modifier.size(38.dp)){
    BadgedBox(badge={Badge{Text("!")}}){Icon(Icons.Default.People,"Solicitudes",tint=RoomAccent)}
   }
  }
  IconButton(onClick=onClose,modifier=Modifier.size(38.dp)){
   Icon(Icons.Default.Close,"Salir",tint=Color.White)
  }
 }
}

@Composable
private fun HostSeatV2(seat:VoiceRoomSeat?,mine:Boolean,canModerate:Boolean,onClick:()->Unit,onAdmin:()->Unit){
 Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally){
  Text("👑 Anfitrión",color=RoomGold,fontSize=11.sp,fontWeight=FontWeight.Bold)
  Spacer(Modifier.height(4.dp))
  SeatCircle(seat=seat,size=56.dp,labelEmpty="👤",onClick=onClick)
  Spacer(Modifier.height(2.dp))
  Text(if(seat?.isOccupied==true)(if(mine)"Tú" else seat.displayName?:"Anfitrión")else "Anfitrión",color=if(seat?.isOccupied==true)Color.White else Color.Gray,fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
  if(canModerate)TextButton(onClick=onAdmin,contentPadding=PaddingValues(0.dp),modifier=Modifier.height(20.dp)){Text("Admin",fontSize=9.sp,color=RoomGold)}
 }
}

@Composable
private fun GuestSeatV2(seat:VoiceRoomSeat?,index:Int,adminAction:Boolean,onClick:()->Unit,onAdmin:()->Unit){
 Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.width(76.dp)){
  SeatCircle(seat=seat,size=56.dp,labelEmpty="NO. $index",onClick=onClick)
  Spacer(Modifier.height(2.dp))
  Text(if(seat?.isOccupied==true)seat.displayName?:"Pana" else "NO. $index",color=if(seat?.isOccupied==true)Color.White else Color.Gray,fontSize=10.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
  if(adminAction)TextButton(onClick=onAdmin,contentPadding=PaddingValues(0.dp),modifier=Modifier.height(20.dp)){Text("Admin",fontSize=9.sp,color=RoomGold)}
 }
}

// Circulo de sillon: vacio = icono Chair en circulo translucido (estilo Starmaker),
// ocupado = avatar con borde; insignia roja de mic-off si esta silenciado.
@Composable
private fun SeatCircle(seat:VoiceRoomSeat?,size:Dp,labelEmpty:String,onClick:()->Unit){
 val speaking=seat?.isSpeaking==true
 val transition=rememberInfiniteTransition(label="speakingAura")
 val scale by transition.animateFloat(initialValue=1f,targetValue=1.18f,animationSpec=infiniteRepeatable(tween(700),RepeatMode.Reverse),label="auraScale")
 val alpha by transition.animateFloat(initialValue=0.55f,targetValue=0f,animationSpec=infiniteRepeatable(tween(700),RepeatMode.Reverse),label="auraAlpha")
 Box{
  if(speaking){
   Box(Modifier.matchParentSize().clip(CircleShape).background(RoomAccent.copy(alpha=alpha)),contentAlignment=Alignment.Center){
    Box(Modifier.fillMaxSize(scale).clip(CircleShape).background(RoomAccent))
   }
  }
  Box(Modifier.size(size).clip(CircleShape).background(if(seat?.isOccupied==true)Color(0xFF4A2C21) else Color(0x29FFFFFF)).border(if(speaking)2.dp else 0.dp,RoomAccent,CircleShape).clickable(onClick=onClick),contentAlignment=Alignment.Center){
   if(seat?.avatarUrl?.isNotBlank()==true){
    AsyncImage(model=seat.avatarUrl,contentDescription=null,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())
   }else if(seat?.isOccupied==true){
    Text(seat.displayName?.take(1)?.uppercase()?:"👤",color=RoomGold,fontSize=20.sp,fontWeight=FontWeight.Bold)
   }else{
    Icon(Icons.Default.Chair,null,tint=Color.White.copy(.55f),modifier=Modifier.size(size*0.44f))
   }
  }
  if(seat?.isMuteBadgeVisible()==true){
   Box(Modifier.size(16.dp).clip(CircleShape).background(Color(0xFFE85D5D)).align(Alignment.TopEnd),contentAlignment=Alignment.Center){
    Icon(Icons.Default.MicOff,null,tint=Color.White,modifier=Modifier.size(10.dp))
   }
  }
 }
}
private fun VoiceRoomSeat?.isMuteBadgeVisible():Boolean=this!=null&&this.isOccupied&&this.isMuted

@Composable
private fun RoomMessageBubble(senderName:String,avatarUrl:String?,content:String){
 Row(verticalAlignment=Alignment.Top){
  Box(Modifier.size(26.dp).clip(CircleShape).background(Color(0xFF4A2C21)),contentAlignment=Alignment.Center){
   if(!avatarUrl.isNullOrBlank())AsyncImage(model=avatarUrl,contentDescription=null,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())
   else Text(senderName.take(1).uppercase(),color=RoomGold,fontSize=11.sp,fontWeight=FontWeight.Bold)
  }
  Spacer(Modifier.width(6.dp))
  Column{
   Text(senderName,color=RoomGold,fontSize=10.sp,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
   Spacer(Modifier.height(2.dp))
   Box(Modifier.background(Color(0x332A1812),RoundedCornerShape(10.dp)).padding(horizontal=10.dp,vertical=6.dp)){
    Text(content,color=Color.White,fontSize=13.sp)
   }
  }
 }
}

// Boton circular de accion: pedir sillon / mutear / activar permiso de micro.
@Composable
private fun MicSeatButton(
 isSeated:Boolean,
 isMuted:Boolean,
 pendingRequest:Boolean,
 needsPermission:Boolean,
 onRequestSeat:()->Unit,
 onToggleMute:()->Unit,
 onEnableMic:()->Unit
){
 val needsMic=needsPermission
 val icon=if(needsMic||isMuted&&(isSeated))Icons.Default.MicOff else Icons.Default.Mic
 val tint=when{
  needsMic->RoomAccent
  !isSeated->(if(pendingRequest)Color.Gray else RoomAccent)
  isMuted->Color(0xFFFF8A80)
  else->RoomAccent
 }
 val enabled=needsMic||isSeated||!pendingRequest
 Box(
  Modifier.size(42.dp).clip(CircleShape).background(Color(0x334ADEAF)).clickable(enabled=enabled){
   if(needsMic)onEnableMic()
   else if(isSeated)onToggleMute()
   else onRequestSeat()
  },
  contentAlignment=Alignment.Center
 ){Icon(icon,contentDescription=null,tint=tint,modifier=Modifier.size(20.dp))}
}

@Composable private fun ModerationDialog(targetUserId:String,isHost:Boolean,targetIsAdmin:Boolean,targetMuted:Boolean,onDismiss:()->Unit,onMute:()->Unit,onUnmute:()->Unit,onKick:()->Unit,onBan:()->Unit,onAdmin:()->Unit,onRemoveAdmin:()->Unit){AlertDialog(onDismissRequest=onDismiss,title={Text("Administrar usuario")},text={Column{Text(targetUserId.take(12),fontSize=11.sp,color=Color.Gray);if(!targetIsAdmin||isHost){TextButton(onClick=if(targetMuted)onUnmute else onMute){Text(if(targetMuted)"Desmutear" else "Mutear")};TextButton(onClick=onKick){Text("Expulsar")};TextButton(onClick=onBan){Text("Bloquear")}};if(isHost&&!targetIsAdmin)TextButton(onClick=onAdmin){Text("Dar administración")};if(isHost&&targetIsAdmin)TextButton(onClick=onRemoveAdmin){Text("Quitar administración")}}},confirmButton={TextButton(onDismiss){Text("Cerrar")}})}
@Composable private fun SeatRequestsDialog(requests:List<com.example.rooms.model.VoiceRoomSeatRequest>,onApprove:(String)->Unit,onDeny:(String)->Unit,onDismiss:()->Unit){AlertDialog(onDismissRequest=onDismiss,title={Text("Solicitudes de sillón")},text={Column{if(requests.isEmpty())Text("No hay solicitudes pendientes")else requests.forEach{r->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(r.userId.take(10),color=Color.White);Text(if(r.requestedSeatIndex==null)"Cualquier sillón"else"Sillón ${r.requestedSeatIndex}",fontSize=11.sp,color=Color.Gray)};IconButton(onClick={onApprove(r.id)}){Icon(Icons.Default.Check,"Aprobar",tint=RoomAccent)};IconButton(onClick={onDeny(r.id)}){Icon(Icons.Default.Close,"Denegar",tint=Color(0xFFFF7B72))}}}}},confirmButton={TextButton(onDismiss){Text("Cerrar")}})}
