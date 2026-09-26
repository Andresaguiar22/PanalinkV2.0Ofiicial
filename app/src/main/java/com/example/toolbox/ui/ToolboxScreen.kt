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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.RemoveRedEye
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search

private data class ToolboxCategory(val id: String, val title: String, val subtitle: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
private data class ToolboxItem(val id: String, val title: String, val description: String, val category: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val accent: Color = IosSettingsColors.blue)

private val categories = listOf(
    ToolboxCategory("all", "Todas", "Todo tu arsenal", Icons.Rounded.GridView),
    ToolboxCategory("protection", "Protección", "Mensajes y contenido", Icons.Rounded.Shield),
    ToolboxCategory("ghost", "Invisibilidad", "Tu presencia, bajo control", Icons.Rounded.VisibilityOff),
    ToolboxCategory("presence", "Presencia", "Actividad y conexión", Icons.Rounded.Circle),
    ToolboxCategory("stories", "Estados", "Control de visualización", Icons.Rounded.Visibility),
    ToolboxCategory("privacy", "Privacidad", "Chats y notificaciones", Icons.Rounded.Lock),
    ToolboxCategory("organize", "Organización", "Una pantalla más limpia", Icons.Rounded.Tune),
    ToolboxCategory("personalize", "Personalización", "Haz PanaLink tuyo", Icons.Rounded.AutoAwesome),
    ToolboxCategory("focus", "Concentración", "Menos interrupciones", Icons.Rounded.DoNotDisturbOn),
    ToolboxCategory("calls", "Llamadas", "Control y privacidad", Icons.Rounded.Call)
)

private val toolboxItems = listOf(
    ToolboxItem("anti_delete_messages", "Escudo de Mensajes", "Protege los mensajes recibidos mientras la herramienta esté activa.", "protection", Icons.Rounded.DeleteOutline),
    ToolboxItem("anti_temporary", "Resguardo de Temporales", "Conserva contenido temporal dentro de tu espacio protegido.", "protection", Icons.Rounded.Timer),
    ToolboxItem("delete_alert", "Alerta de Eliminación", "Recibe una alerta cuando un mensaje sea eliminado.", "protection", Icons.Rounded.NotificationsActive),
    ToolboxItem("ghost_mode", "Modo Fantasma", "Centraliza tus controles de invisibilidad en un solo acceso.", "ghost", Icons.Rounded.VisibilityOff),
    ToolboxItem("silent_read", "Lectura Silenciosa", "Controla las señales de lectura de tus conversaciones.", "ghost", Icons.Rounded.MarkEmailRead),
    ToolboxItem("hidden_typing", "Escritura Invisible", "Oculta la señal de escritura mientras redactas.", "ghost", Icons.Rounded.Edit),
    ToolboxItem("hidden_recording", "Grabación Invisible", "Controla la señal de grabación de audio.", "ghost", Icons.Rounded.MicOff),
    ToolboxItem("frozen_presence", "Última Visita Congelada", "Mantén fija la información de última visita que muestras.", "ghost", Icons.Rounded.AcUnit),
    ToolboxItem("online_alert", "Alerta de Presencia", "Recibe una alerta cuando un contacto seleccionado aparezca activo.", "presence", Icons.Rounded.Notifications),
    ToolboxItem("presence_dot", "Punto de Presencia", "Muestra un indicador de disponibilidad junto a las conversaciones.", "presence", Icons.Rounded.Circle),
    ToolboxItem("presence_chat", "Presencia en Conversaciones", "Muestra información de disponibilidad en tu lista de chats.", "presence", Icons.Rounded.ChatBubbleOutline),
    ToolboxItem("anonymous_status", "Vista Silenciosa", "Controla las señales asociadas a la visualización de estados.", "stories", Icons.Rounded.VisibilityOff),
    ToolboxItem("status_view_alert", "Alerta de Visualización", "Recibe una notificación cuando alguien visualice tu estado.", "stories", Icons.Rounded.RemoveRedEye),
    ToolboxItem("copy_status", "Texto Copiable", "Mantén pulsado para copiar el texto de un estado.", "stories", Icons.Rounded.ContentCopy),
    ToolboxItem("status_advance", "Control de Avance", "Decide si el siguiente estado se abre automáticamente.", "stories", Icons.Rounded.SkipNext),
    ToolboxItem("private_vault", "Bóveda de Chats", "Protege conversaciones seleccionadas dentro de un espacio privado.", "privacy", Icons.Rounded.Lock),
    ToolboxItem("private_contact", "Privacidad por Contacto", "Personaliza controles de privacidad para contactos concretos.", "privacy", Icons.Rounded.Person),
    ToolboxItem("private_notifications", "Notificaciones Privadas", "Controla cuánto contenido aparece en tus notificaciones.", "privacy", Icons.Rounded.NotificationsOff),
    ToolboxItem("chat_separator", "Separador de Conversaciones", "Organiza chats, grupos, estados y llamadas en secciones claras.", "organize", Icons.Rounded.ViewAgenda),
    ToolboxItem("smart_filters", "Filtros Inteligentes", "Filtra rápidamente por no leídos, privados, grupos o favoritos.", "organize", Icons.Rounded.FilterList),
    ToolboxItem("flex_search", "Búsqueda Flexible", "Elige el estilo de búsqueda que prefieras en PanaLink.", "organize", Icons.Rounded.Search),
    ToolboxItem("identity_home", "Identidad en Inicio", "Muestra tu nombre en la cabecera de la pantalla principal.", "personalize", Icons.Rounded.Badge),
    ToolboxItem("bio_home", "Bio en Inicio", "Muestra una pequeña biografía bajo tu nombre.", "personalize", Icons.Rounded.Description),
    ToolboxItem("quick_actions", "Acciones Rápidas", "Añade accesos directos a las acciones que más utilizas.", "personalize", Icons.Rounded.Bolt),
    ToolboxItem("focus_mode", "Modo Concentración", "Reduce interrupciones y centraliza tus controles de silencio.", "focus", Icons.Rounded.DoNotDisturbOn),
    ToolboxItem("call_filter", "Filtro de Llamadas", "Controla qué llamadas pueden generar una interrupción.", "calls", Icons.Rounded.Call),
    ToolboxItem("call_summary", "Resumen de Llamada", "Muestra información adicional al finalizar una llamada.", "calls", Icons.Rounded.CallEnd)
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

    Column(Modifier.fillMaxSize().background(IosSettingsColors.groupBackground).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = IosSettingsColors.blue) }
            Column(Modifier.weight(1f)) {
                Text("Caja de herramientas", color = IosSettingsColors.label, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Herramientas para tu experiencia PanaLink", color = IosSettingsColors.secondaryLabel, fontSize = 13.sp)
            }
            Box(Modifier.clip(RoundedCornerShape(14.dp)).background(IosSettingsColors.cell).border(1.dp, IosSettingsColors.separator, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("0", color = IosSettingsColors.label, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(5.dp))
                    Text("●", color = IosSettingsColors.yellow, fontSize = 12.sp)
                }
            }
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 30.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(IosSettingsColors.cell).border(1.dp, IosSettingsColors.separator, RoundedCornerShape(26.dp)).padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(56.dp).background(IosSettingsColors.blue.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Build, null, tint = IosSettingsColors.blue, modifier = Modifier.size(29.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Tu arsenal personal", color = IosSettingsColors.label, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Text("Activa herramientas temporales y personaliza cómo vives PanaLink.", color = IosSettingsColors.secondaryLabel, fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.take(5).forEach { category ->
                        val selected = selectedCategory == category.id
                        Box(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (selected) IosSettingsColors.blue else IosSettingsColors.cell).border(1.dp, if (selected) IosSettingsColors.blue else IosSettingsColors.separator, RoundedCornerShape(14.dp)).clickable { selectedCategory = category.id }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(category.icon, null, tint = if (selected) IosSettingsColors.label else IosSettingsColors.secondaryLabel, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.height(3.dp))
                                Text(category.title, color = if (selected) IosSettingsColors.label else IosSettingsColors.secondaryLabel, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(if (selectedCategory == "all") "Todas las herramientas" else categories.first { it.id == selectedCategory }.title, color = IosSettingsColors.label, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text("${visibleItems.size} herramientas disponibles", color = IosSettingsColors.secondaryLabel, fontSize = 12.sp)
                    }
                    Text("TEMPORALES", color = IosSettingsColors.blue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            items(visibleItems, key = { it.id }) { item ->
                val selected = item.id == selectedItem.id
                val scale by animateFloatAsState(if (selected) 1f else .985f, tween(180, easing = FastOutSlowInEasing), label = "toolboxScale")
                Row(Modifier.fillMaxWidth().scale(scale).clip(RoundedCornerShape(20.dp)).background(if (selected) IosSettingsColors.cell else IosSettingsColors.cellElevated).border(1.dp, if (selected) item.accent.copy(alpha = .38f) else IosSettingsColors.separator.copy(alpha = 0.6f), RoundedCornerShape(20.dp)).clickable { selectedItem = item }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).background(item.accent.copy(alpha = if (selected) .14f else .08f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(item.icon, null, tint = if (selected) item.accent else IosSettingsColors.secondaryLabel, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, color = IosSettingsColors.label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(item.description, color = IosSettingsColors.secondaryLabel, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                    Box(Modifier.size(7.dp).background(if (selected) item.accent else IosSettingsColors.tertiaryLabel, CircleShape))
                }
            }

            item {
                AnimatedContent(targetState = selectedItem.id, transitionSpec = {
                    (fadeIn(tween(180)) + scaleIn(tween(180), initialScale = .985f)) togetherWith fadeOut(tween(120))
                }, label = "toolboxDetail") {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(25.dp)).background(IosSettingsColors.cell).border(1.dp, selectedItem.accent.copy(alpha = .25f), RoundedCornerShape(25.dp)).padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(44.dp).background(selectedItem.accent.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(selectedItem.icon, null, tint = selectedItem.accent, modifier = Modifier.size(22.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(selectedItem.title, color = IosSettingsColors.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                                Text("Herramienta temporal", color = IosSettingsColors.secondaryLabel, fontSize = 12.sp)
                            }
                            Text("PREVIEW", color = IosSettingsColors.secondaryLabel, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("Duración", color = IosSettingsColors.label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(9.dp))
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(IosSettingsColors.cellElevated).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            durations.forEach { duration ->
                                val selected = selectedDuration == duration
                                Box(Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(if (selected) selectedItem.accent else Color.Transparent).clickable { selectedDuration = duration }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("${duration.first} días", color = if (selected) IosSettingsColors.label else IosSettingsColors.secondaryLabel, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text("${duration.second} monedas", color = if (selected) IosSettingsColors.label else IosSettingsColors.secondaryLabel, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(15.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Precio seleccionado", color = IosSettingsColors.secondaryLabel, fontSize = 11.sp)
                                Text("${selectedDuration.second} monedas", color = IosSettingsColors.yellow, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                            }
                            Box(Modifier.clip(RoundedCornerShape(14.dp)).background(IosSettingsColors.blue).padding(horizontal = 17.dp, vertical = 11.dp)) {
                                Text("Vista previa", color = IosSettingsColors.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Spacer(Modifier.height(9.dp))
                        Text("Maqueta visual · las compras y activaciones se conectarán en una fase posterior.", color = IosSettingsColors.tertiaryLabel, fontSize = 10.sp, lineHeight = 14.sp)
                    }
                }
            }
        }
    }
}
