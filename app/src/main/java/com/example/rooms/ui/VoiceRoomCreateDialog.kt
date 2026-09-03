package com.example.rooms.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.supabase.SupabaseClient
import com.example.rooms.repository.CreateRoomRequest

private val ScreenBg = Color(0xFFF2F2F4)
private val RowBg = Color.White
private val TitleColor = Color(0xFF6E6E73)
private val TextColor = Color(0xFF1B1B1F)
private val ValueColor = Color(0xFF9E9EA3)
private val RoomAccent2 = Color(0xFF4ADEAF)

@Composable
fun VoiceRoomCreateDialog(onDismiss:()->Unit,onCreate:(CreateRoomRequest)->Unit){
 var name by remember{mutableStateOf("")}
 var description by remember{mutableStateOf("")}
 var coverUrl by remember{mutableStateOf(SupabaseClient.currentProfile?.avatarUrl.orEmpty())}
 var category by remember{mutableStateOf("general")}
 var privateRoom by remember{mutableStateOf(false)}
 var editingField by remember{mutableStateOf<String?>(null)} // "name","description","cover"
 var showCategorySheet by remember{mutableStateOf(false)}

 val categories=listOf("general" to "General","chat" to "Charlar","meeting" to "Reunión","work" to "Trabajo","dating" to "Enamorados","friends" to "Conocer gente","music" to "Música","gaming" to "Gaming")
 val categoryLabel=categories.firstOrNull{it.first==category}?.second?:"General"
 val canCreate=name.trim().length>=2

 Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)){
  Surface(Modifier.fillMaxSize(),color=ScreenBg){
   Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())){
    // Header
    Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically){
     IconButton(onClick=onDismiss,modifier=Modifier.size(40.dp)){Icon(Icons.Default.ArrowBack,"Volver",tint=TextColor)}
     Spacer(Modifier.width(4.dp));Text("Crear mi sala",color=TextColor,fontSize=20.sp,fontWeight=FontWeight.Bold)
    }

    CreateSectionTitle("Información básica")
    Surface(color=RowBg,shape=RoundedCornerShape(22.dp),shadowElevation=1.dp,modifier=Modifier.padding(horizontal=16.dp)){
     Column {
      RowItem(
       title="Foto de portada",
       onClick={editingField="cover"},
       trailing={Box(Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFE9E9EC)),contentAlignment=Alignment.Center){if(coverUrl.isNotBlank())AsyncImage(model=coverUrl,contentDescription=null,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())else Icon(Icons.Default.Public,null,tint=ValueColor,modifier=Modifier.size(16.dp))}}
      )
      RowDivider()
      RowItem(title="Nombre",onClick={editingField="name"},value=name.ifBlank{"Toca para nombrar"},isError=name.isNotBlank()&&name.trim().length<2)
      RowDivider()
      RowItem(title="Anuncio",onClick={editingField="description"},value=description.ifBlank{"Descripción breve"})
      RowDivider()
      RowItem(title="Modo de sala",onClick={showCategorySheet=true},value=categoryLabel)
     }
    }

    CreateSectionTitle("Administración")
    Surface(color=RowBg,shape=RoundedCornerShape(22.dp),shadowElevation=1.dp,modifier=Modifier.padding(horizontal=16.dp)){
     Column {
      RowItem(
       title="Sala privada",
       subtitle="Solo invitados podrán entrar",
       trailing={Switch(checked=privateRoom,onCheckedChange={privateRoom=it},colors=SwitchDefaults.colors(checkedThumbColor=RoomAccent2,checkedTrackColor=RoomAccent2.copy(.35f),uncheckedThumbColor=Color.White,uncheckedTrackColor=Color(0xFFC2C2C7)))}
      )
     }
    }

    // Placeholder para editar un campo tipo hoja inline
    if(editingField!=null){
     CreateSectionTitle(if(editingField=="name")"Editar nombre" else if(editingField=="cover")"Portada (URL)" else "Editar anuncio")
     Surface(color=RowBg,shape=RoundedCornerShape(22.dp),shadowElevation=1.dp,modifier=Modifier.padding(horizontal=16.dp)){
      Column(Modifier.padding(vertical=6.dp)){
       OutlinedTextField(
        value=when(editingField){"name"->name;"description"->description;else->coverUrl},
        onValueChange={v->when(editingField){"name"->name=v.take(80);"description"->description=v.take(280);else->coverUrl=v.take(500)}},
        modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp),
        singleLine=editingField!="description",
        minLines=if(editingField=="description")2 else 1,
        maxLines=if(editingField=="description")4 else 1,
        placeholder={Text(if(editingField=="cover")"https://..." else "")},
        colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=RoomAccent2,unfocusedBorderColor=Color(0xFFC2C2C7),focusedTextColor=TextColor,unfocusedTextColor=TextColor)
       )
       Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),horizontalArrangement=Arrangement.End){
        TextButton(onClick={editingField=null}){Text("Listo",color=RoomAccent2,fontWeight=FontWeight.Bold)}
       }
      }
     }
    }

    Spacer(Modifier.height(20.dp))
    Text("Puedes tener hasta 3 salas activas.",color=ValueColor,fontSize=11.sp,modifier=Modifier.padding(horizontal=24.dp).align(Alignment.CenterHorizontally))
    // Boton Crear
    Button(
     onClick={onCreate(CreateRoomRequest(name.trim(),description.trim(),coverUrl.trim().ifEmpty{null},category,if(privateRoom)"private" else "public"))},
     enabled=canCreate,
     modifier=Modifier.fillMaxWidth().padding(horizontal=24.dp),
     colors=ButtonDefaults.buttonColors(containerColor=RoomAccent2,contentColor=Color(0xFF13302A)),
     shape=RoundedCornerShape(24.dp)
    ){Text("Crear y entrar",fontWeight=FontWeight.Bold,fontSize=15.sp)}
    Spacer(Modifier.height(20.dp))
   }
  }

  // Hoja de categorias
  if(showCategorySheet){
   AlertDialog(onDismissRequest={showCategorySheet=false},title={Text("Modo de sala",fontWeight=FontWeight.Bold)},text={Column{categories.forEach{(key,label)->Row(Modifier.fillMaxWidth().clickable{category=key;showCategorySheet=false}.padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically){Icon(if(category==key)Icons.Default.Check else Icons.Default.Public,null,tint=if(category==key)RoomAccent2 else Color.Gray,modifier=Modifier.size(18.dp));Spacer(Modifier.width(10.dp));Text(label,color=TextColor,fontSize=15.sp)}}}},confirmButton={TextButton(onClick={showCategorySheet=false}){Text("Cerrar")}})
  }
 }
}

@Composable
private fun CreateSectionTitle(text:String){
 Text(text,color=TitleColor,fontWeight=FontWeight.Bold,fontSize=13.sp,modifier=Modifier.padding(horizontal=20.dp,vertical=8.dp))
}

@Composable
private fun RowDivider(){
 HorizontalDivider(color=Color(0xFFEBEBEF),modifier=Modifier.padding(horizontal=20.dp))
}

 @Composable
private fun RowItem(
 title:String,
 onClick:(()->Unit)?=null,
 value:String?=null,
 subtitle:String?=null,
 isError:Boolean=false,
 trailing:(@Composable ()->Unit)?=null
){
 val clickModifier=if(onClick!=null)Modifier.clickable(onClick=onClick)else Modifier
 Row(Modifier.fillMaxWidth().then(clickModifier).padding(horizontal=20.dp,vertical=15.dp),verticalAlignment=Alignment.CenterVertically){
  Column(Modifier.weight(1f)){
   Text(title,color=TextColor,fontSize=15.sp,fontWeight=FontWeight.Medium)
   if(subtitle!=null){Spacer(Modifier.height(2.dp));Text(subtitle,color=ValueColor,fontSize=11.sp)}
  }
  Spacer(Modifier.width(8.dp))
  if(trailing!=null){trailing()}
  else{
   Row(verticalAlignment=Alignment.CenterVertically){
    if(value!=null)Text(value,color=if(isError)Color(0xFFD32F2F) else ValueColor,fontSize=14.sp,maxLines=1)
    if(onClick!=null)Spacer(Modifier.width(3.dp))
    if(onClick!=null)Icon(Icons.Default.ChevronRight,null,tint=ValueColor,modifier=Modifier.size(16.dp))
   }
  }
 }
}
