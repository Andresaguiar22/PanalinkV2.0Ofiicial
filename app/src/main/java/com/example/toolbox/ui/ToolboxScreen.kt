package com.example.toolbox.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class ToolboxItem(val id: String, val title: String, val description: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
private val toolboxItems = listOf(
    ToolboxItem("anti_delete_messages", "Anti eliminar mensajes", "Conserva una copia local de mensajes que PanaLink haya recibido mientras la protección esté activa.", Icons.Default.DeleteOutline),
    ToolboxItem("anti_delete_stories", "Anti eliminar historias", "Mantén disponibles historias recibidas mientras estén protegidas y dentro de la retención de PanaLink.", Icons.Default.VisibilityOff),
    ToolboxItem("private_archive", "Archivo protegido", "Prepara un espacio para contenido que quieras conservar y consultar de forma privada.", Icons.Default.Lock),
    ToolboxItem("activity_history", "Historial reforzado", "Consulta un historial ampliado de actividad que la aplicación pueda conservar de forma legítima.", Icons.Default.History)
)
private val durations = listOf(3 to 30, 5 to 45, 10 to 100)

@Composable
fun ToolboxScreen(onBack: () -> Unit) {
    var selectedItem by remember { mutableStateOf(toolboxItems.first()) }
    var selectedDuration by remember { mutableStateOf(durations.first()) }
    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Atrás", tint = Color(0xFF0A84FF)) }
            Column(Modifier.weight(1f)) {
                Text("Caja de herramientas", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Protecciones temporales", color = Color(0xFF8E8E93), fontSize = 13.sp)
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFF1C1C1E)).border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(24.dp)).padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(54.dp).background(Color(0xFF0A84FF).copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Shield, null, tint = Color(0xFF0A84FF), modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("Tus herramientas", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Text("Activa protecciones por tiempo limitado usando monedas.", color = Color(0xFFAEAEB2), fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }
                }
            }
            items(toolboxItems, key = { it.id }) { item ->
                val selected = item.id == selectedItem.id
                val scale by animateFloatAsState(if (selected) 1f else .985f, tween(180, easing = FastOutSlowInEasing), label = "toolboxScale")
                Row(Modifier.fillMaxWidth().scale(scale).clip(RoundedCornerShape(20.dp)).background(if (selected) Color(0xFF1C1C1E) else Color(0xFF111113)).border(1.dp, if (selected) Color(0x550A84FF) else Color(0x1FFFFFFF), RoundedCornerShape(20.dp)).clickable { selectedItem = item }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).background(if (selected) Color(0xFF0A84FF).copy(alpha = .13f) else Color(0xFF2C2C2E), CircleShape), contentAlignment = Alignment.Center) { Icon(item.icon, null, tint = if (selected) Color(0xFF0A84FF) else Color(0xFFAEAEB2), modifier = Modifier.size(23.dp)) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) { Text(item.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold); Text(item.description, color = Color(0xFF8E8E93), fontSize = 12.sp, lineHeight = 17.sp) }
                    Box(Modifier.size(8.dp).background(if (selected) Color(0xFF0A84FF) else Color(0xFF48484A), CircleShape))
                }
            }
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFF1C1C1E)).border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(24.dp)).padding(18.dp)) {
                    AnimatedContent(targetState = selectedItem.id, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "toolboxDetail") {
                        Text("Duración", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF2C2C2E)).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        durations.forEach { duration ->
                            val selected = selectedDuration == duration
                            Box(Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(if (selected) Color(0xFF0A84FF) else Color.Transparent).clickable { selectedDuration = duration }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(duration.first.toString() + " días", color = if (selected) Color.White else Color(0xFFAEAEB2), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text(duration.second.toString() + " monedas", color = if (selected) Color.White else Color(0xFF8E8E93), fontSize = 10.sp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(selectedItem.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium); Text("Precio seleccionado", color = Color(0xFF8E8E93), fontSize = 12.sp) }
                        Text(selectedDuration.second.toString(), color = Color(0xFFFFCC00), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(5.dp)); Text("monedas", color = Color(0xFFAEAEB2), fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("La compra se conectará al sistema de monedas y a los beneficios temporales de PanaLink.", color = Color(0xFF636366), fontSize = 11.sp, lineHeight = 15.sp)
                }
            }
        }
    }
}