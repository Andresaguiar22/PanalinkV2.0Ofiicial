package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubble
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.ui.settings.ios.IosSettingsColors

// Colores iOS del mockup
private val IosTabTextGray = IosSettingsColors.secondaryLabel
private val PanalinkTabTint = IosSettingsColors.blue

@Composable
fun PanaLinkFloatingBottomBar(
    currentPage: Int,
    onPageSelected: (Int) -> Unit,
    totalUnreadCount: Int = 0,
    badgeCounts: Map<Int, Int> = emptyMap(),
    urgentBadgeTabs: Set<Int> = emptySet()
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .padding(bottom =8.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IosBottomNavItem(
                title = "Chats",
                icon = Icons.Outlined.ChatBubble,
                isActive = currentPage == 0,
                badgeCount = if (currentPage == 0) totalUnreadCount else badgeCounts[0] ?: 0,
                urgent = urgentBadgeTabs.contains(0),
                onClick = { onPageSelected(0) }
            )
            IosBottomNavItem(
                title = "Momentos",
                icon = Icons.Outlined.DonutLarge,
                isActive = currentPage == 1,
                badgeCount = badgeCounts[1] ?: 0,
                urgent = urgentBadgeTabs.contains(1),
                onClick = { onPageSelected(1) }
            )
            IosBottomNavItem(
                title = "Clips",
                icon = Icons.Outlined.PlayArrow,
                isActive = currentPage == 2,
                badgeCount = badgeCounts[2] ?: 0,
                urgent = urgentBadgeTabs.contains(2),
                onClick = { onPageSelected(2) }
            )
            IosBottomNavItem(
                title = "Llamadas",
                icon = Icons.Outlined.Call,
                isActive = currentPage == 3,
                badgeCount = badgeCounts[3] ?: 0,
                urgent = urgentBadgeTabs.contains(3),
                onClick = { onPageSelected(3) }
            )
            IosBottomNavItem(
                title = "Gente",
                icon = Icons.Outlined.People,
                isActive = currentPage == 4,
                badgeCount = badgeCounts[4] ?: 0,
                urgent = urgentBadgeTabs.contains(4),
                onClick = { onPageSelected(4) }
            )
        }
    }
}

@Composable
private fun IosBottomNavItem(
    title: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    badgeCount: Int = 0,
    urgent: Boolean = false
) {
    val color = if (isActive) PanalinkTabTint else IosTabTextGray
val glowAlpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = tween(durationMillis = 420),
        label = "TabGlow"
    )

    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(width = 46.dp, height = 38.dp),
            contentAlignment = Alignment.Center
        ) {
// Iluminación suave desde abajo hacia arriba (solo el icono seleccionado)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(46.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                PanalinkTabTint.copy(alpha = 0.10f),
                                PanalinkTabTint.copy(alpha = 0.30f)
                            )
                        )
                    )
                    .border(
                        1.dp,
                        PanalinkTabTint.copy(alpha = 0.38f),
                        RoundedCornerShape(12.dp)
                    )
                    .graphicsLayer { alpha = glowAlpha }
            )
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = color,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(26.dp)
            )
            if (badgeCount > 0) {
                val badgeColor = if (urgent) IosSettingsColors.red else PanalinkTabTint
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 7.dp, y = (-4).dp)
                        .defaultMinSize(minWidth = 16.dp)
                        .height(16.dp)
                        .clip(CircleShape)
                        .background(badgeColor)
                        .padding(horizontal = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                        color = IosSettingsColors.onAccent,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = title,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
