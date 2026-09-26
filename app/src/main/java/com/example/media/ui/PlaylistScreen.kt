package com.example.media.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.media.audio.AudioTrackEntity
import com.example.media.ui.components.TrackItem
import com.example.media.playlist.PlaylistEntity
import com.example.ui.settings.ios.IosSettingsColors
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share

/**
 * P6.7 - Playlist Detail Screen
 * Displays playlist cover, tracks list, play all button, and sharing functionality within PanaLink.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    playlist: PlaylistEntity,
    songs: List<AudioTrackEntity>,
    userRole: com.example.media.playlist.PlaylistMemberRole = com.example.media.playlist.PlaylistMemberRole.VIEWER,
    onBackClick: () -> Unit,
    onPlayAllClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onPlayTrackClick: (AudioTrackEntity) -> Unit,
    onSharePlaylistClick: () -> Unit,
    onCollaboratorsClick: () -> Unit,
    onGenerateCoverClick: () -> Unit,
    onRemoveTrackClick: (AudioTrackEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState()
    var showOptionsSheet by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var selectedTrack by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<AudioTrackEntity?>(null) }

    Scaffold(
        containerColor = IosSettingsColors.groupBackground,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Atrás", tint = IosSettingsColors.label)
                    }
                },
                actions = {
                    if (userRole.canGenerateAI()) {
                        IconButton(onClick = onGenerateCoverClick) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = "IA Cover", tint = IosSettingsColors.pink)
                        }
                    }
                    IconButton(onClick = onCollaboratorsClick) {
                        Icon(Icons.Rounded.Group, contentDescription = "Colaboradores", tint = IosSettingsColors.green)
                    }
                    if (userRole.canShare()) {
                        IconButton(onClick = onSharePlaylistClick) {
                            Icon(Icons.Rounded.Share, contentDescription = "Compartir", tint = IosSettingsColors.blue)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding()),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // Hero Section
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(IosSettingsColors.cell)
                            .clickable(enabled = userRole.canEditMetadata()) { onGenerateCoverClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (!playlist.coverPath.isNullOrEmpty()) {
                            AsyncImage(
                                model = playlist.coverPath,
                                contentDescription = playlist.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = IosSettingsColors.blue, modifier = Modifier.size(80.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = playlist.name,
                        color = IosSettingsColors.label,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    
                    Text(
                        text = playlist.description ?: "Playlist de PanaLink",
                        color = IosSettingsColors.secondaryLabel,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                    )

                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Person, contentDescription = null, tint = IosSettingsColors.secondaryLabel, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when(userRole) {
                                com.example.media.playlist.PlaylistMemberRole.OWNER -> "Tú (Propietario)"
                                com.example.media.playlist.PlaylistMemberRole.EDITOR -> "Editor"
                                com.example.media.playlist.PlaylistMemberRole.VIEWER -> "Espectador"
                            },
                            color = IosSettingsColors.secondaryLabel, 
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Icon(Icons.Rounded.AccessTime, contentDescription = null, tint = IosSettingsColors.secondaryLabel, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "${songs.size} canciones", color = IosSettingsColors.secondaryLabel, fontSize = 12.sp)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            IconButton(onClick = { /* Favorite */ }) {
                                Icon(Icons.Rounded.FavoriteBorder, contentDescription = null, tint = IosSettingsColors.label)
                            }
                            IconButton(onClick = { /* Download */ }) {
                                Icon(Icons.Rounded.Download, contentDescription = null, tint = IosSettingsColors.label)
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onShuffleClick) {
                                Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle", tint = IosSettingsColors.label)
                            }
                            FloatingActionButton(
                                onClick = onPlayAllClick,
                                containerColor = IosSettingsColors.blue,
                                contentColor = IosSettingsColors.onAccent,
                                shape = androidx.compose.foundation.shape.CircleShape,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = "Reproducir Todo", modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                }
            }

            // Tracks List
            items(songs) { track ->
                TrackItem(
                    track = track,
                    onClick = { onPlayTrackClick(track) },
                    onTrackOptionsClick = {
                        selectedTrack = it
                        showOptionsSheet = true
                    }
                )
            }
        }
    }

    if (showOptionsSheet && selectedTrack != null) {
        ModalBottomSheet(
            onDismissRequest = { showOptionsSheet = false },
            sheetState = sheetState,
            containerColor = IosSettingsColors.cell
        ) {
            TrackOptionsBottomSheet(
                track = selectedTrack!!,
                userRole = userRole,
                onPlayNext = { /* TODO */ },
                onAddToPlaylist = { /* TODO */ },
                onFavorite = { /* TODO */ },
                onEditMetadata = { /* TODO */ },
                onShare = { /* TODO */ },
                onDelete = { onRemoveTrackClick(selectedTrack!!) },
                onDismiss = { showOptionsSheet = false }
            )
        }
    }
}
