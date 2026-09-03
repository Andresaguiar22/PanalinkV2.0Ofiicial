package com.example.feature.chat.ui.contact

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Profile
import com.example.ui.components.PanaAvatar
import com.example.ui.theme.bounceClick

@Composable
fun ChatContactDetailSheet(
    visible: Boolean,
    otherUser: Profile?,
    onDismiss: () -> Unit,
) {
    // Simulated Contact Detail Custom Bottom Sheet
    AnimatedVisibility(
    visible = visible,
    enter = slideInVertically(initialOffsetY = { it }),
    exit = slideOutVertically(targetOffsetY = { it }),
    modifier = Modifier.fillMaxSize()
    ) {
    val context = LocalContext.current
    val otherName = otherUser?.displayName ?: ""
    val otherBio = otherUser?.status?.takeIf { it.isNotEmpty() } ?: "¡Hola! Estoy usando PanaLink para hablar de pana. 🇻🇪"
    val otherPin = otherUser?.pin ?: (otherUser?.id?.take(6)?.uppercase() ?: "PANA12")
    
    
    Box(
    modifier = Modifier
    .fillMaxSize()
    .background(Color.Black.copy(alpha = 0.6f))
    .clickable(onClick = onDismiss),
    contentAlignment = Alignment.BottomCenter
    ) {
    Card(
    colors = CardDefaults.cardColors(containerColor = Color(0xFF101D24)),
    shape = RoundedCornerShape(24.dp, 24.dp, 0.dp, 0.dp),
    modifier = Modifier
    .fillMaxWidth()
    .clickable(enabled = false) {} // block click propagation
    ) {
    Column(
    modifier = Modifier
    .fillMaxWidth()
    .padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally
    ) {
    // Accent drag bar
    Box(
    modifier = Modifier
    .width(40.dp)
    .height(4.dp)
    .background(Color.Gray.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
    )
    
    Spacer(modifier = Modifier.height(20.dp))
    
    // Large circular avatar
    com.example.ui.components.PanaAvatar(
    avatarUrl = otherUser?.avatarUrl,
    userId = otherUser?.id,
    size = 104.dp,
    borderWidth = 0.dp,
    placeholderName = otherName
    )
    
    Spacer(modifier = Modifier.height(16.dp))
    
    Text(
    text = otherName,
    color = Color.White,
    fontSize = 20.sp,
    fontWeight = FontWeight.Bold
    )
    
    Text(
    text = "en línea",
    color = Color(0xFF25D366),
    fontSize = 12.sp,
    fontWeight = FontWeight.Medium
    )
    
    Spacer(modifier = Modifier.height(24.dp))
    
    // Info Cards
    Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
    // Bio Info Row
    Row(
    modifier = Modifier
    .fillMaxWidth()
    .background(Color(0xFF1F2C34), RoundedCornerShape(12.dp))
    .padding(16.dp),
    verticalAlignment = Alignment.CenterVertically
    ) {
    Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF00A884), modifier = Modifier.size(24.dp))
    Spacer(modifier = Modifier.width(16.dp))
    Column {
    Text(
    text = "Bio y PIN PanaLink",
    color = Color(0xFF8596A0),
    fontSize = 11.sp
    )
    Text(otherBio, color = Color.White, fontSize = 14.sp)
    
    Spacer(modifier = Modifier.height(4.dp))
    Text(otherPin, color = Color(0xFF8596A0), fontSize = 13.sp)
    }
    }
    }
    
    Spacer(modifier = Modifier.height(24.dp))
    
    // Actions Buttons Row
    Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
    FilledTonalButton(
    onClick = {
    val shareIntent = android.content.Intent().apply {
    action = android.content.Intent.ACTION_SEND
    putExtra(android.content.Intent.EXTRA_TEXT, "Contacto Pana: $otherName\nPIN: $otherPin\n¡Agregado de pana desde PanaLink! 🇻🇪")
    type = "text/plain"
    }
    context.startActivity(android.content.Intent.createChooser(shareIntent, "Compartir contacto de pana"))
    },
    colors = ButtonDefaults.filledTonalButtonColors(
    containerColor = Color(0xFF00A884),
    contentColor = Color.White
    ),
    modifier = Modifier
    .weight(1f)
    .height(48.dp)
    .bounceClick(),
    shape = RoundedCornerShape(12.dp)
    ) {
    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(modifier = Modifier.width(8.dp))
    Text("Compartir Contacto", fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
    
    Button(
    onClick = onDismiss,
    colors = ButtonDefaults.buttonColors(
    containerColor = Color(0xFF1F2C34),
    contentColor = Color.White
    ),
    modifier = Modifier
    .weight(1f)
    .height(48.dp)
    .bounceClick(),
    shape = RoundedCornerShape(12.dp)
    ) {
    Text("Cerrar", fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
    }
    }
    }
    }
    }
}
