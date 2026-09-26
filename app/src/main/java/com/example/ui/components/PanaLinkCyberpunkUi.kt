package com.example.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.supabase.SupabaseClient
import com.example.ui.settings.ios.IosSettingsColors

object PanaLinkCyberpunkColors {
    val Background: Color get() = IosSettingsColors.groupBackground
    val Glass: Color get() = IosSettingsColors.cell
    val Cream: Color get() = IosSettingsColors.label
    val Message: Color get() = IosSettingsColors.secondaryLabel
    val Cyan: Color get() = IosSettingsColors.blue
    val Magenta: Color get() = IosSettingsColors.blue
    val Purple: Color get() = IosSettingsColors.blue
    val Gold: Color get() = IosSettingsColors.blue
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
                        IosSettingsColors.groupBackground,
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
fun PaniOSChatsTopBar(
    onEdit: () -> Unit,
    onCamera: () -> Unit,
    onCompose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint = IosSettingsColors.green

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        IosSettingsColors.groupBackground,
                        IosSettingsColors.cell,
                        IosSettingsColors.groupBackground
                    )
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Fila de acciones: Editar a la izquierda, camara + lapiz a la derecha.

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Editar",
                color = tint,
                fontSize = 17.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onEdit)
                    .padding(vertical = 6.dp, horizontal = 2.dp)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onCamera)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoCamera,
                        contentDescription = "Escanear QR",
                        tint = tint,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onCompose)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Nuevo chat",
                        tint = tint,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(24.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Large Title iOS
        Text(
            text = "PanaLink",
            color = IosSettingsColors.label,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = 0.2.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(PanaLinkCyberpunkColors.Background)
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "PanaLink",
            color = IosSettingsColors.blue,
            fontSize = 31.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            style = TextStyle(
                shadow = androidx.compose.ui.graphics.Shadow(
                    color = IosSettingsColors.blue,
                    blurRadius = 12f
                )
            )
        )

        // Píldora superior: agrupa las acciones y el perfil en una sola pieza
        // visual, con un halo verde suave para dar jerarquía sin recargar la barra.
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            IosSettingsColors.cell,
                            IosSettingsColors.cellElevated,
                            IosSettingsColors.cell
                        )
                    )
                )
                .border(
                    1.dp,
                    IosSettingsColors.blue,
                    RoundedCornerShape(28.dp)
                )
                .padding(horizontal = 5.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .blur(16.dp, BlurredEdgeTreatment.Unbounded)
                    .background(
                        IosSettingsColors.blue.copy(alpha = 0.2f),
                        RoundedCornerShape(28.dp)
                    )
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                IconButton(onClick = onAdd, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Crear", tint = IosSettingsColors.label)
                }
                IconButton(onClick = onSearch, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.Default.Search, contentDescription = "Buscar", tint = IosSettingsColors.label)
                }
                IconButton(onClick = onFolder, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.Default.Folder, contentDescription = "Favoritos", tint = IosSettingsColors.label)
                }
                Box(
                    modifier = Modifier
                        .padding(start = 3.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onProfile)
                ) {
                    com.example.ui.components.PanaAvatar(
                        avatarUrl = SupabaseClient.currentProfile?.avatarUrl,
                        userId = SupabaseClient.currentUser?.id,
                        size = 40.dp,
                        borderWidth = 1.5.dp,
                        borderColor = IosSettingsColors.blue,
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
}

@Composable
fun PaniOSSearchBar(
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(IosSettingsColors.cell)
            .clickable(onClick = onSearchClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Buscar",
            tint = IosSettingsColors.secondaryLabel,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Buscar",
            color = IosSettingsColors.secondaryLabel,
            fontSize = 17.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
@Composable
private fun PaniOSToolboxIcon(
    modifier: Modifier = Modifier
) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "toolbox")
    val wobble by transition.animateFloat(
        initialValue = -2.2f,
        targetValue = 2.2f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(
                durationMillis = 520,
                easing = androidx.compose.animation.core.FastOutSlowInEasing
            ),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "wobble"
    )
    val squeeze by transition.animateFloat(
        initialValue = 0.985f,
        targetValue = 1.015f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(
                durationMillis = 700,
                easing = androidx.compose.animation.core.FastOutSlowInEasing
            ),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "squeeze"
    )

    androidx.compose.foundation.Canvas(
        modifier = modifier
            .graphicsLayer {
                rotationZ = wobble
                scaleX = squeeze
                scaleY = squeeze
            }
    ) {
        val stroke = size.minDimension * 0.075f
        val body = androidx.compose.ui.geometry.Rect(
            left = size.width * 0.16f,
            top = size.height * 0.36f,
            right = size.width * 0.84f,
            bottom = size.height * 0.82f
        )
        val bodyRadius = size.minDimension * 0.14f
        drawRoundRect(
            color = IosSettingsColors.cell,
            topLeft = body.topLeft,
            size = body.size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(bodyRadius, bodyRadius)
        )
        drawRoundRect(
            color = IosSettingsColors.secondaryLabel,
            topLeft = body.topLeft,
            size = body.size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(bodyRadius, bodyRadius),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
        )

        drawRoundRect(
            color = IosSettingsColors.cell,
            topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.34f, size.height * 0.20f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.32f, size.height * 0.25f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.minDimension * 0.09f, size.minDimension * 0.09f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
        )

        drawLine(
            color = IosSettingsColors.blue,
            start = androidx.compose.ui.geometry.Offset(size.width * 0.20f, size.height * 0.50f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.80f, size.height * 0.50f),
            strokeWidth = stroke,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )

        drawCircle(
            color = IosSettingsColors.green,
            radius = size.minDimension * 0.055f,
            center = androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.62f)
        )
    }
}

@Composable
fun PaniOSUnifiedTopBar(
    onEdit: (() -> Unit)? = null,
    onCamera: (() -> Unit)? = null,
    onCompose: () -> Unit = {},
    onSearch: (() -> Unit)? = null,
    onFolder: (() -> Unit)? = null,
    onNotifications: (() -> Unit)? = null,
    unreadNotificationCount: Int = 0,
    onToolbox: () -> Unit = {},
    onProfile: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val tint = IosSettingsColors.green

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        IosSettingsColors.groupBackground,
                        IosSettingsColors.cell,
                        IosSettingsColors.groupBackground
                    )
                )
            )
            .statusBarsPadding()
            .padding(horizontal =  16.dp, vertical =  8.dp)
    ) {

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            // Logo giratorio del inicio (esquina superior izquierda, reemplaza al "PanaLink")
            com.example.ui.screen.AnimatedPanaWelcomeLogo(
                logoSize =  34.dp,
                modifier = Modifier
                    .size(66.dp)
                    .align(Alignment.CenterStart)
            )

            Row(
                modifier = Modifier
                    .wrapContentWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                IosSettingsColors.separator,
                                IosSettingsColors.separator,
                                IosSettingsColors.separator
                            )
                        )
                    )
                    .border(1.dp, IosSettingsColors.separator, RoundedCornerShape(22.dp))
                    .padding(horizontal =  5.dp, vertical =  3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    if (onEdit != null) {
                        IconButton(onClick = onEdit, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Editar", tint = tint, modifier = Modifier.size(22.dp))
                        }
                    }
                        if (onCamera != null) {
                            IconButton(onClick = onCamera, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Rounded.PhotoCamera, contentDescription = "Escanear QR", tint = tint, modifier = Modifier.size(22.dp))
                            }
                        }
                        if (onCompose != {}) {
                            IconButton(onClick = onCompose, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Filled.Add, contentDescription = "Crear", tint = tint, modifier = Modifier.size(22.dp))
                            }
                        }
                        if (onSearch != null) {
                            IconButton(onClick = onSearch, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Filled.Search, contentDescription = "Buscar", tint = tint, modifier = Modifier.size(22.dp))
                            }
                        }
                        if (onFolder != null) {
                            IconButton(onClick = onFolder, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Filled.Folder, contentDescription = "Favoritos", tint = tint, modifier = Modifier.size(22.dp))
                            }
                        }
                        if (onNotifications != null) {
                            Box {
                                IconButton(onClick = onNotifications, modifier = Modifier.size(40.dp)) {
                                    Icon(Icons.Filled.Notifications, contentDescription = "Notificaciones", tint = tint, modifier = Modifier.size(22.dp))
                                }
                                if (unreadNotificationCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x =  2.dp, y =  2.dp)
                                            .size(15.dp)
                                            .background(IosSettingsColors.red, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (unreadNotificationCount >  9) "9+" else unreadNotificationCount.toString(),
                                            color = IosSettingsColors.onAccent,
                                            fontSize =  9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Avatar + estado: espejo visual del logo, ambos parten de un círculo de 40.dp.
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier
                        .width(96.dp)
                        .align(Alignment.CenterEnd)
                        .offset(y = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clickable(onClick = onProfile)
                    ) {
                        // The avatar is clipped, the container is NOT: clipping the
                        // container also clipped the presence dot at the avatar edge,
                        // which is why it looked cut off inside the photo.
                        com.example.ui.components.PanaAvatar(
                            avatarUrl = SupabaseClient.currentProfile?.avatarUrl,
                            userId = SupabaseClient.currentUser?.id,
                            size = 40.dp,
                            borderWidth =  1.5.dp,
                            borderColor = IosSettingsColors.green,
                            placeholderName = SupabaseClient.currentProfile?.displayName ?: "",
                            contentDescription = "Perfil",
                            modifier = Modifier.clip(CircleShape)
                        )
                        val myPresence by com.example.data.repository.PresenceRepository.currentUserStatus.collectAsStateWithLifecycle()
                        val mySecondaryPresence by com.example.data.repository.PresenceRepository.currentUserSecondaryStatus.collectAsStateWithLifecycle()
                        // Border matches the bar background so the dot reads as a
                        // separate badge instead of a blob stuck on the photo.
                        com.example.ui.components.chat.list.PresenceIndicator(
                            status = myPresence.rawValue,
                            secondaryStatus = if (mySecondaryPresence != com.example.data.repository.SecondaryPresenceStatus.NONE) mySecondaryPresence.rawValue else null,
                            size =  12.dp,
                            borderColor = IosSettingsColors.groupBackground,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x =  1.dp, y =  1.dp)
                                .zIndex(1f)
                        )
                    }
                    PaniOSStatusLabel()
                }
            }
        }
    }

@Composable
private fun PaniOSStatusLabel() {
    val myPresence by com.example.data.repository.PresenceRepository.currentUserStatus.collectAsStateWithLifecycle()
    val uid = SupabaseClient.currentUser?.id
    val label = remember(myPresence, uid) {
        if (myPresence == com.example.data.repository.UserPresenceStatus.ONLINE) {
            "En línea"
        } else {
            val lastOffline = uid?.let { uid ->
                com.example.util.PresenceHistoryTracker.getHistoryForUser(uid)
                    .lastOrNull { it.status == com.example.data.repository.UserPresenceStatus.OFFLINE }
            }
            when {
                lastOffline == null ->"Desconectado"
                else ->{
                    val mins = ((System.currentTimeMillis() - lastOffline.timestamp) / 60000L).coerceAtLeast(0L).toInt()
                    when {
                        mins < 1 ->"hace 1 min"
                        mins < 60 ->"hace $mins min"
                        else ->"hace ${mins / 60} h"
                    }
                }
            }
        }
    }
    Text(
        text = label,
        color = if (myPresence == com.example.data.repository.UserPresenceStatus.ONLINE) IosSettingsColors.green else IosSettingsColors.secondaryLabel,
        fontSize =  10.sp,
        fontWeight = FontWeight.Medium,
        maxLines =  1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top =  2.dp)
    )
}
