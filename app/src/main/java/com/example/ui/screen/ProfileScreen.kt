package com.example.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.supabase.SupabaseClient
import com.example.ui.components.PanaAvatar
import com.example.ui.profile.components.ReelsGrid
import com.example.ui.profile.components.SavedGrid
import com.example.ui.settings.navigation.SettingsNavGraph
import com.example.ui.settings.screens.ProfileEditScreen
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.ProfileUiState
import com.example.ui.viewmodel.ProfileViewModel
import com.example.ui.settings.ios.IosSettingsColors

private val IosBlack = Color(0xFF000000)
private val IosSurface: Color get() = IosSettingsColors.cell
private val IosBlue: Color get() = IosSettingsColors.blue
private val IosGreen: Color get() = IosSettingsColors.green
private val IosPink: Color get() = IosSettingsColors.pink
private val IosGold: Color get() = IosSettingsColors.yellow
private val IosGray: Color get() = IosSettingsColors.gray
private val IosBorder: Color get() = IosSettingsColors.separator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    authViewModel: AuthViewModel,
    onBack: () -> Unit,
    onNavigateToReel: (String) -> Unit = {},
    onOpenPremium: (() -> Unit)? = null
) {
    var showControlCenter by remember { mutableStateOf(false) }
    var isEditingProfile by remember { mutableStateOf(false) }

    if (showControlCenter) {
        SettingsNavGraph(
            onBackToMain = { showControlCenter = false },
            onLogout = { authViewModel.logout() },
            onDeleteAccount = { authViewModel.logout() }
        )
        return
    }

    if (isEditingProfile) {
        ProfileEditScreen(
            onBack = { isEditingProfile = false },
            viewModel = viewModel
        )
        return
    }

    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val currentUid = SupabaseClient.currentUser?.id ?: ""
    val contactIdentifier by viewModel.contactIdentifierState.collectAsStateWithLifecycle()
    val followersCount by viewModel.followersCount.collectAsStateWithLifecycle()
    val followingCount by viewModel.followingCount.collectAsStateWithLifecycle()
    val likesCount by viewModel.totalLikesCount.collectAsStateWithLifecycle()

    var displayName by remember { mutableStateOf("") }
    var selectedAvatarUrl by remember { mutableStateOf("") }
    var selectedCoverUrl by remember { mutableStateOf("") }
    var userPin by remember { mutableStateOf("") }
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.loadProfile()
        viewModel.loadReels(currentUid)
    }

    LaunchedEffect(profileState, contactIdentifier) {
        if (profileState is ProfileUiState.Success) {
            val prof = (profileState as ProfileUiState.Success).profile
            displayName = prof.displayName
            selectedAvatarUrl = prof.avatarUrl ?: ""
            selectedCoverUrl = prof.coverUrl ?: ""
            userPin = contactIdentifier?.pin ?: prof.pin ?: ""
        }
    }

    val reputationState = remember(displayName, selectedAvatarUrl, userPin) {
        when {
            userPin.isNotEmpty() && displayName.isNotEmpty() && selectedAvatarUrl.contains("http") -> "Verificado"
            displayName.isNotEmpty() && selectedAvatarUrl.isNotEmpty() -> "Confiable"
            displayName.isNotEmpty() -> "Nuevo"
            else -> "Limitado"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IosBlack)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Native iPhone-inspired top bar.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(IosBlack.copy(alpha = 0.96f))
                    .padding(top = 10.dp, bottom = 10.dp, start = 14.dp, end = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IosCircleButton(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", onBack)

                Text(
                    text = "Mi Perfil",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onOpenPremium != null) {
                        com.example.premium.ui.CoinChip(onClick = onOpenPremium)
                    }
                    IosCircleButton(
                        Icons.Default.Settings,
                        "Ajustes",
                        onClick = { showControlCenter = true }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                // Cover / hero header.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clickable { isEditingProfile = true }
                ) {
                    if (selectedCoverUrl.isNotEmpty()) {
                        AsyncImage(
                            model = selectedCoverUrl,
                            contentDescription = "Portada",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        listOf(IosSettingsColors.cell, IosSettingsColors.cellElevated, IosSettingsColors.groupBackground)
                                    )
                                )
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, IosBlack.copy(alpha = 0.96f))
                                )
                            )
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    // Avatar with subtle premium aura.
                    Box(
                        modifier = Modifier
                            .offset(y = (-42).dp)
                            .size(96.dp)
                            .background(
                                Brush.linearGradient(listOf(IosGreen, IosBlue, IosPink)),
                                CircleShape
                            )
                            .padding(3.dp)
                            .clickable { isEditingProfile = true }
                    ) {
                        PanaAvatar(
                            avatarUrl = selectedAvatarUrl.ifEmpty { null },
                            size = 90.dp,
                            borderWidth = 0.dp,
                            borderColor = Color.Transparent,
                            contentDescription = "Avatar de Perfil",
                            placeholderName = displayName
                        )
                    }

                    Spacer(modifier = Modifier.height((-25).dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = displayName.ifEmpty { "Pana de Panalink" },
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                        if (reputationState == "Verificado") {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Verificado",
                                tint = IosBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Text(
                        text = SupabaseClient.currentUser?.email ?: "sin_correo@panalink.com",
                        color = IosGray,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // iOS translucent statistics capsule.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                            .border(1.dp, IosBorder, RoundedCornerShape(16.dp))
                            .padding(vertical = 12.dp, horizontal = 10.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IosStatItem("$followersCount", "Seguidores")
                        VerticalDivider(color = IosBorder, modifier = Modifier.height(25.dp))
                        IosStatItem("$followingCount", "Siguiendo")
                        VerticalDivider(color = IosBorder, modifier = Modifier.height(25.dp))
                        IosStatItem("$likesCount", "Me gusta")
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        IosBadge("${if (reputationState == "Verificado") "✓ " else ""}$reputationState", if (reputationState == "Verificado") IosBlue else IosSettingsColors.blue, Modifier.weight(1f))
                        IosBadge("Fundador 👑", IosGold, Modifier.weight(1f))
                        IosActionButton(Icons.Default.Edit, "Editar", false, Modifier.weight(1.05f)) { isEditingProfile = true }
                        IosActionButton(Icons.Default.Settings, "Ajustes", true, Modifier.weight(1.15f)) { showControlCenter = true }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Clean iOS-style tabs.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(IosBlack)
                        .border(0.5.dp, IosBorder),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    IosTabButton(
                        title = "Reels",
                        icon = Icons.Outlined.GridView,
                        isActive = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        modifier = Modifier.weight(1f)
                    )
                    IosTabButton(
                        title = "Guardados",
                        icon = Icons.Outlined.BookmarkBorder,
                        isActive = selectedTabIndex == 1,
                        onClick = {
                            selectedTabIndex = 1
                            viewModel.loadSavedContent()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Keep the real grids/data/navigation; only the surrounding presentation changes.
                if (selectedTabIndex == 0) {
                    ReelsGrid(viewModel = viewModel, onNavigateToReel = onNavigateToReel)
                } else {
                    SavedGrid(viewModel = viewModel, onNavigateToReel = onNavigateToReel)
                }

                Spacer(modifier = Modifier.height(30.dp))
            }
        }

        // iPhone home indicator.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp)
                .width(134.dp)
                .height(5.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.72f))
        )
    }
}

@Composable
private fun IosCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.09f))
            .border(1.dp, IosBorder, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, tint = Color.White, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun IosStatItem(count: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(label, color = IosGray, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun IosBadge(label: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(IosSurface)
            .border(1.dp, IosBorder, RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp, horizontal = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun IosActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isPrimary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val background = if (isPrimary) {
        Brush.linearGradient(listOf(IosSettingsColors.pink, IosPink))
    } else {
        Brush.linearGradient(listOf(IosSurface, IosSurface))
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .border(1.dp, if (isPrimary) Color.Transparent else IosBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 7.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(15.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun IosTabButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = title,
            tint = if (isActive) Color.White else IosGray,
            modifier = Modifier.size(19.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            title,
            color = if (isActive) Color.White else IosGray,
            fontSize = 13.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(if (isActive) 40.dp else 0.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(
                    if (isActive) Brush.horizontalGradient(listOf(IosSettingsColors.pink, IosPink))
                    else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                )
        )
    }
}
