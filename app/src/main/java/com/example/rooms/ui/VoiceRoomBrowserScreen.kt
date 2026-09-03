package com.example.rooms.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.rooms.model.VoiceRoom

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceRoomBrowserScreen(onBack:()->Unit,onEnterRoom:(String)->Unit,viewModel:VoiceRoomBrowserViewModel=viewModel()) {
    val state by viewModel.uiState.collectAsState()
    var showCreate by remember{mutableStateOf(false)}
    LaunchedEffect(state.createdRoom){state.createdRoom?.let{onEnterRoom(it.id)}}
    LaunchedEffect(Unit){viewModel.refresh()}
    Scaffold(topBar={TopAppBar(title={Text("Salas de voz",fontWeight=FontWeight.Bold)},navigationIcon={IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Volver")}},colors=TopAppBarDefaults.topAppBarColors(containerColor=Color(0xFF2C1912),titleContentColor=Color.White))},containerColor=Color(0xFF1A120E)){padding->
        Box(Modifier.fillMaxSize()) {
            if(state.isLoading && state.rooms.isEmpty()) CircularProgressIndicator(Modifier.align(Alignment.Center),color=Color(0xFF4ADEAF))
            LazyVerticalGrid(columns=GridCells.Fixed(2),modifier=Modifier.fillMaxSize().padding(padding).padding(12.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                items(state.rooms,key={it.id}){room->RoomCard(room,state.memberCounts[room.id]?:0){onEnterRoom(room.id)}}
            }
            if(state.rooms.isEmpty()&&!state.isLoading) Text("No hay salas activas ahora",color=Color.Gray,modifier=Modifier.align(Alignment.Center).padding(bottom=80.dp))
            FloatingActionButton(onClick={showCreate=true},modifier=Modifier.align(Alignment.BottomEnd).padding(20.dp),containerColor=Color(0xFF4ADEAF)){Icon(Icons.Default.Add,"Crear sala",tint=Color(0xFF1A120E))}
            if(showCreate) VoiceRoomCreateDialog(onDismiss={showCreate=false},onCreate={showCreate=false;viewModel.createRoom(it)})
        }
    }
}

@Composable
private fun RoomCard(room:VoiceRoom,members:Int,onClick:()->Unit){
    Card(shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF3A231A)),modifier=Modifier.fillMaxWidth().clickable(onClick=onClick)){
        Column {
            Box(Modifier.fillMaxWidth().height(88.dp).clip(RoundedCornerShape(topStart=16.dp,topEnd=16.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF5E3A28),Color(0xFF3E2417))))){
                if(!room.coverUrl.isNullOrBlank()) AsyncImage(model=room.coverUrl,contentDescription=room.name,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())
                if(room.isPrivate) Surface(color=Color(0xCC1A120E),shape=RoundedCornerShape(bottomEnd=10.dp)){Row(Modifier.padding(horizontal=8.dp,vertical=5.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Lock,null,tint=Color.White,modifier=Modifier.size(12.dp));Spacer(Modifier.width(4.dp));Text("Privada",color=Color.White,fontSize=10.sp)}}
            }
            Column(Modifier.padding(10.dp)){
                Text(room.name,color=Color.White,fontWeight=FontWeight.Bold,fontSize=14.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(room.category.replaceFirstChar{it.uppercase()},color=Color(0xFFB8A99A),fontSize=10.sp)
                if(room.description.isNotBlank()) Text(room.description,color=Color(0xFFD8CDC4),fontSize=11.sp,maxLines=2,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=4.dp))
                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Person,null,tint=Color(0xBAB3FFD4),modifier=Modifier.size(13.dp));Spacer(Modifier.width(3.dp));Text("$members",color=Color.White,fontSize=11.sp,fontWeight=FontWeight.SemiBold);Spacer(Modifier.width(8.dp));Text("${room.maxSeats} sillones",color=Color.Gray,fontSize=10.sp)}
            }
        }
    }
}
