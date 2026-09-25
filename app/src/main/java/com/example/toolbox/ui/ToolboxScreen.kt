package com.example.toolbox.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.filled.*
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
import com.example.ui.settings.ios.IosSettingsColors

private data class ToolboxCategory(val id: String, val title: String, val subtitle: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
private data class ToolboxItem(val id: String, val title: String, val description: String, val category: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val accent: Color = Color(0xFF0A84FF))

private val categories = listOf(
    ToolboxCategory("all", "Todas", "Todo tu arsenal", Icons.Default.GridView),
    ToolboxCategory("protection", "Protección", "Mensajes y contenido", Icons.Default.Shield),
    ToolboxCategory("ghost", "Invisibilidad", "Tu presencia, bajo control", Icons.Default.VisibilityOff),
    ToolboxCategory("presence", "Presencia", "Actividad y conexión", Icons.Default.Circle),
    ToolboxCategory("stories", "Estados", "Control de visualización", Icons.Default.Visibility),
    ToolboxCategory("privacy", "Privacidad", "Chats y notificaciones", Icons.Default.Lock),
    ToolboxCategory("organize", "Organización", "Una pantalla más limpia", Icons.Default.Tune),
    ToolboxCategory("personalize", "Personalización", "Haz PanaLink tuyo", Icons.Default.AutoAwesome),
    ToolboxCategory("focus", "Concentración", "Menos interrupciones", Icons.Default.DoNotDisturbOn),
    ToolboxCategory("calls", "Llamadas", "Control y privacidad", Icons.Default.Call)
)

private val toolboxItems = listOf(
    ToolboxItem("anti_delete_messages", "Escudo de Mensajes", "Protege los mensajes recibidos mientras la herramienta esté activa.", "protection", Icons.Default.DeleteOutline),
    ToolboxItem("anti_temporary", "Resguardo de Temporales", "Conserva contenido temporal dentro de tu espacio protegido.", "protection", Icons.Default.Timer),
    ToolboxItem("delete_alert", "Alerta de Eliminación", "Recibe una alerta cuando un mensaje sea eliminado.", "protection", Icons.Default.NotificationsActive),
    ToolboxItem("ghost_mode", "Modo Fantasma", "Centraliza tus controles de invisibilidad en un solo acceso.", "ghost", Icons.Default.VisibilityOff),
    ToolboxItem("silent_read", "Lectura Silenciosa", "Controla las señales de lectura de tus conversaciones.", "ghost", Icons.Default.MarkEmailRead),
    ToolboxItem("hidden_typing", "Escritura Invisible", "Oculta la señal de escritura mientras redactas.", "ghost", Icons.Default.Edit),
    ToolboxItem("hidden_recording", "Grabación Invisible", "Controla la señal de grabación de audio.", "ghost", Icons.Default.MicOff),
    ToolboxItem("frozen_presence", "Última Visita Congelada", "Mantén fija la información de última visita que muestras.", "ghost", Icons.Default.AcUnit),
    ToolboxItem("online_alert", "Alerta de Presencia", "Recibe una alerta cuando un contacto seleccionado aparezca activo.", "presence", Icons.Default.Notifications),
    ToolboxItem("presence_dot", "Punto de Presencia", "Muestra un indicador de disponibilidad junto a las conversaciones.", "presence", Icons.Default.Circle),
    ToolboxItem("presence_chat", "Presencia en Conversaciones", "Muestra información de disponibilidad en tu lista de chats.", "presence", Icons.Default.ChatBubbleOutline),
    ToolboxItem("anonymous_status", "Vista Silenciosa", "Controla las señales asociadas a la visualización de estados.", "stories", Icons.Default.VisibilityOff),
    ToolboxItem("status_view_alert", "Alerta de Visualización", "Recibe una notificación cuando alguien visualice tu estado.", "stories", Icons.Default.RemoveRedEye),
    ToolboxItem("copy_status", "Texto Copiable", "Mantén pulsado para copiar el texto de un estado.", "stories", Icons.Default.ContentCopy),
    ToolboxItem("status_advance", "Control de Avance", "Decide si el siguiente estado se abre automáticamente.", "stories", Icons.Default.SkipNext),
    ToolboxItem("private_vault", "Bóveda de Chats", "Protege conversaciones seleccionadas dentro de un espacio privado.", "privacy", Icons.Default.Lock),
    ToolboxItem("private_contact", "Privacidad por Contacto", "Personaliza controles de privacidad para contactos concretos.", "privacy", Icons.Default.Person),
    ToolboxItem("private_notifications", "Notificaciones Privadas", "Controla cuánto contenido aparece en tus notificaciones.", "privacy", Icons.Default.NotificationsOff),
    ToolboxItem("chat_separator", "Separador de Conversaciones", "Organiza chats, grupos, estados y llamadas en secciones claras.", "organize", Icons.Default.ViewAgenda),
    ToolboxItem("smart_filters", "Filtros Inteligentes", "Filtra rápidamente por no leídos, privados, grupos o favoritos.", "organize", Icons.Default.FilterList),
    ToolboxItem("flex_search", "Búsqueda Flexible", "Elige el estilo de búsqueda que prefieras en PanaLink.", "organize", Icons.Default.Search),
    ToolboxItem("identity_home", "Identidad en Inicio", "Muestra tu nombre en la cabecera de la pantalla principal.", "personalize", Icons.Default.Badge),
    ToolboxItem("bio_home", "Bio en Inicio", "Muestra una pequeña biografía bajo tu nombre.", "personalize", Icons.Default.Description),
    ToolboxItem("quick_actions", "Acciones Rápidas", "Añade accesos directos a las acciones que más utilizas.", "personalize", Icons.Default.Bolt),
    ToolboxItem("focus_mode", "Modo Concentración", "Reduce interrupciones y centraliza tus controles de silencio.", "focus", Icons.Default.DoNotDisturbOn),
    ToolboxItem("call_filter", "Filtro de Llamadas", "Controla qué llamadas pueden generar una interrupción.", "calls", Icons.Default.Call),
    ToolboxItem("call_summary", "Resumen de Llamada", "Muestra información adicional al finalizar una llamada.", "calls", Icons.Default.CallEnd)
)

private val durations = listOf(3 to 30, 5 to 45, 10 to 100)

@Composable
fun ToolboxScreen(onBack: () -> Unit) {
    var selectedCategory by remember { mutableStateOf("all") }
    var selectedItem by remember { mutableStateOf(toolboxItems.first()) }
    var selectedDuration by remember { mutableStateOf(durations.first()) }
    val visibleItems = if (selectedCategory == "all") toolboxItems else toolboxItems.filter { it.category == selectedCategory }

    LaunchedEffect(selectedCategory) {
        if (selectedCategory != "all") visibleItems.firstOrNull()?.let { selectedItem = it }
    }

    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Atrás", tint = Color(0xFF0A84FF)) }
            Column(Modifier.weight(1f)) {
                Text("Caja de herramientas", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Herramientas para tu experiencia PanaLink", color = Color(0xFF8E8E93), fontSize = 13.sp)
            }
            Box(Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFF1C1C1E)).border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("0", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(5.dp))
                    Text("●", color = IosSettingsColors.yellow, fontSize = 12.sp)
                }
            }
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 30.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Color(0xFF1C1C1E)).border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(26.dp)).padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(56.dp).background(Color(0xFF0A84FF).copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Build, null, tint = Color(0xFF0A84FF), modifier = Modifier.size(29.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Tu arsenal personal", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Text("Activa herramientas temporales y personaliza cómo vives PanaLink.", color = IosSettingsColors.secondaryLabel, fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.take(5).forEach { category ->
                        val selected = selectedCategory == category.id
                        Box(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (selected) Color(0xFF0A84FF) else Color(0xFF1C1C1E)).border(1.dp, if (selected) Color(0xFF0A84FF) else Color(0x22FFFFFF), RoundedCornerShape(14.dp)).clickable { selectedCategory = category.id }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(category.icon, null, tint = if (selected) Color.White else IosSettingsColors.secondaryLabel, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.height(3.dp))
                                Text(category.title, color = if (selected) Color.White else IosSettingsColors.secondaryLabel, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(if (selectedCategory == "all") "Todas las herramientas" else categories.first { it.id == selectedCategory }.title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text("${visibleItems.size} herramientas disponibles", color = Color(0xFF8E8E93), fontSize = 12.sp)
                    }
                    Text("TEMPORALES", color = Color(0xFF0A84FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            items(visibleItems, key = { it.id }) { item ->
                val selected = item.id == selectedItem.id
                val scale by animateFloatAsState(if (selected) 1f else .985f, tween(180, easing = FastOutSlowInEasing), label = "toolboxScale")
                Row(Modifier.fillMaxWidth().scale(scale).clip(RoundedCornerShape(20.dp)).background(if (selected) Color(0xFF1C1C1E) else Color(0xFF111113)).border(1.dp, if (selected) item.accent.copy(alpha = .38f) else Color(0x1FFFFFFF), RoundedCornerShape(20.dp)).clickable { selectedItem = item }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).background(item.accent.copy(alpha = if (selected) .14f else .08f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(item.icon, null, tint = if (selected) item.accent else IosSettingsColors.secondaryLabel, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(item.description, color = Color(0xFF8E8E93), fontSize = 12.sp, lineHeight = 17.sp)
                    }
                    Box(Modifier.size(7.dp).background(if (selected) item.accent else Color(0xFF48484A), CircleShape))
                }
            }

            item {
                AnimatedContent(targetState = selectedItem.id, transitionSpec = {
                    (fadeIn(tween(180)) + scaleIn(tween(180), initialScale = .985f)) togetherWith fadeOut(tween(120))
                }, label = "toolboxDetail") {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(25.dp)).background(Color(0xFF1C1C1E)).border(1.dp, selectedItem.accent.copy(alpha = .25f), RoundedCornerShape(25.dp)).padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(44.dp).background(selectedItem.accent.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(selectedItem.icon, null, tint = selectedItem.accent, modifier = Modifier.size(22.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(selectedItem.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                                Text("Herramienta temporal", color = Color(0xFF8E8E93), fontSize = 12.sp)
                            }
                            Text("PREVIEW", color = Color(0xFF8E8E93), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("Duración", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(9.dp))
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF2C2C2E)).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            durations.forEach { duration ->
                                val selected = selectedDuration == duration
                                Box(Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(if (selected) selectedItem.accent else Color.Transparent).clickable { selectedDuration = duration }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("${duration.first} días", color = if (selected) Color.White else IosSettingsColors.secondaryLabel, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text("${duration.second} monedas", color = if (selected) Color.White else Color(0xFF8E8E93), fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(15.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Precio seleccionado", color = Color(0xFF8E8E93), fontSize = 11.sp)
                                Text("${selectedDuration.second} monedas", color = IosSettingsColors.yellow, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                            }
                            Box(Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFF0A84FF)).padding(horizontal = 17.dp, vertical = 11.dp)) {
                                Text("Vista previa", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Spacer(Modifier.height(9.dp))
                        Text("Maqueta visual · las compras y activaciones se conectarán en una fase posterior.", color = Color(0xFF636366), fontSize = 10.sp, lineHeight = 14.sp)
                    }
                }
            }
        }
    }
}
