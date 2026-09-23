package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.supabase.SupabaseClient

object PanaLinkCyberpunkColors {
    val Background = Color(0xFF0D0F12)
    val Glass = Color(0xB8131A22)
    val Cream = Color(0xFFF5E6C8)
    val Message = Color(0xFFD9E3EF)
    val Cyan = Color(0xFF18E7F5)
    val Magenta = Color(0xFFFF28C8)
    val Purple = Color(0xFF9B5CFF)
    val Gold = Color(0xFFF0D9A6)
}

private val cyberpunkBorderBrush = Brush.linearGradient(
    colors = listOf(
        PanaLinkCyberpunkColors.Magenta,
        PanaLinkCyberpunkColors.Purple,
        PanaLinkCyberpunkColors.Cyan
    )
)

@Composable
fun PanaLinkCyberpunkBackground(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(PanaLinkCyberpunkColors.Background)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val step = 22.dp.toPx()
            var x = 8.dp.toPx()
            while (x < size.width) {
                drawLine(
                    color = PanaLinkCyberpunkColors.Cyan.copy(alpha = 0.035f),
                    start = androidx.compose.ui.geometry.Offset(x, 0f),
                    end = androidx.compose.ui.geometry.Offset(x, size.height),
                    strokeWidth = 1.dp.toPx()
                )
                x += step
            }
            val dash = 5.dp.toPx()
            var column = 12.dp.toPx()
            while (column < size.width) {
                var y = 12.dp.toPx()
                while (y < size.height) {
                    drawLine(
                        color = PanaLinkCyberpunkColors.Magenta.copy(alpha = 0.035f),
                        start = androidx.compose.ui.geometry.Offset(column, y),
                        end = androidx.compose.ui.geometry.Offset(column, y + dash),
                        strokeWidth = 1.dp.toPx()
                    )
                    y += 31.dp.toPx()
                }
                column += 55.dp.toPx()
            }
        }
        Box(
            Modifier
                .align(Alignment.TopStart)
                .size(220.dp)
                .blur(55.dp, BlurredEdgeTreatment.Unbounded)
                .background(
                    Brush.radialGradient(
                        listOf(
                            PanaLinkCyberpunkColors.Magenta.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    )
                )
        )
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .size(260.dp)
                .blur(65.dp, BlurredEdgeTreatment.Unbounded)
                .background(
                    Brush.radialGradient(
                        listOf(
                            PanaLinkCyberpunkColors.Cyan.copy(alpha = 0.10f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

@Composable
fun PanaLinkNeonGlassPanel(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        PanaLinkCyberpunkColors.Glass.copy(alpha = 0.94f),
                        Color(0xC20B1118),
                        PanaLinkCyberpunkColors.Glass.copy(alpha = 0.90f)
                    )
                )
            )
            .border(1.5.dp, cyberpunkBorderBrush, shape)
    ) {
        content()
    }
}

@Composable
fun PanaLinkCyberpunkTopBar(
    onAdd: () -> Unit,
    onSearch: () -> Unit,
    onFolder: () -> Unit,
    onProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pillShape = RoundedCornerShape(26.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "PanaLink",
            color = PanaLinkCyberpunkColors.Cream,
            fontSize = 31.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif,
            style = TextStyle(
                shadow = androidx.compose.ui.graphics.Shadow(
                    color = PanaLinkCyberpunkColors.Gold.copy(alpha = 0.28f),
                    blurRadius = 10f
                )
            )
        )

        Row(
            modifier = Modifier
                .clip(pillShape)
                .background(Color(0xB5161D25))
                .border(
                    1.2.dp,
                    Brush.linearGradient(
                        listOf(
                            PanaLinkCyberpunkColors.Cyan.copy(alpha = 0.8f),
                            PanaLinkCyberpunkColors.Purple.copy(alpha = 0.65f),
                            PanaLinkCyberpunkColors.Magenta.copy(alpha = 0.55f)
                        )
                    ),
                    pillShape
                )
                .padding(horizontal = 4.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onAdd, modifier = Modifier.size(42.dp)) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Crear",
                    tint = PanaLinkCyberpunkColors.Cream
                )
            }
            IconButton(onClick = onSearch, modifier = Modifier.size(42.dp)) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Buscar",
                    tint = PanaLinkCyberpunkColors.Cream
                )
            }
            IconButton(onClick = onFolder, modifier = Modifier.size(42.dp)) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = "Favoritos",
                    tint = PanaLinkCyberpunkColors.Cream
                )
            }
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(42.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onProfile)
            ) {
                com.example.ui.components.PanaAvatar(
                    avatarUrl = SupabaseClient.currentProfile?.avatarUrl,
                    userId = SupabaseClient.currentUser?.id,
                    size = 42.dp,
                    borderWidth = 1.5.dp,
                    borderColor = PanaLinkCyberpunkColors.Cyan,
                    placeholderName = SupabaseClient.currentProfile?.displayName ?: "",
                    contentDescription = "Perfil"
                )
                val myPresence by com.example.data.repository.PresenceRepository.currentUserStatus.collectAsStateWithLifecycle()
                val mySecondaryPresence by com.example.data.repository.PresenceRepository.currentUserSecondaryStatus.collectAsStateWithLifecycle()
                com.example.ui.components.chat.list.PresenceIndicator(
                    status = myPresence.rawValue,
                    secondaryStatus = if (mySecondaryPresence != com.example.data.repository.SecondaryPresenceStatus.NONE) mySecondaryPresence.rawValue else null,
                    size = 9.dp,
                    modifier = Modifier.align(Alignment.BottomEnd)
                )
            }
        }
    }
}
