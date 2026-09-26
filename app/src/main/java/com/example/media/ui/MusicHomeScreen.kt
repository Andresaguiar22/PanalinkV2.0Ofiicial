package com.example.media.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.example.media.player.ui.MusicViewModel
import com.example.ui.settings.ios.IosSettingsColors

/**
 * P6.7 - Music Home Screen
 * Primary dashboard for PanaLink Audio Studio & Playlist Engine.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicHomeScreen(
    onBackClick: () -> Unit,
    onPlaylistClick: (String) -> Unit,
    onInvitationsClick: () -> Unit,
    onPlayTrack: (AudioTrackEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    // In a real app, these would be injected or obtained from a ViewModelFactory
    // For this prototype, we'll use a simplified approach
    val context = androidx.compose.ui.platform.LocalContext.current
    val audioRepository = remember { com.example.media.audio.AudioRepository(com.example.data.database.PanalinkDatabase.getDatabase(context).audioDao()) }
    val audioLibraryManager = remember { com.example.media.audio.AudioLibraryManager(audioRepository) }
    val playlistDao = remember { com.example.data.database.PanalinkDatabase.getDatabase(context).playlistDao() }
    val collaboratorDao = remember { com.example.data.database.PanalinkDatabase.getDatabase(context).collaboratorDao() }
    val playlistRepository = remember { com.example.media.playlist.PlaylistRepository(playlistDao, collaboratorDao) }
    val audioImportManager = remember { com.example.media.audio.AudioImportManager(context.applicationContext, audioRepository) }
    val currentUserId = com.example.data.supabase.SupabaseClient.currentUser?.id ?: ""

    val viewModel: MusicViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return MusicViewModel(audioLibraryManager, playlistRepository, audioImportManager, currentUserId) as T
            }
        }
    )

    val playlists by viewModel.playlists.collectAsState()
    val recentTracks by viewModel.recentTracks.collectAsState()
    val favoriteTracks by viewModel.favoriteTracks.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val filteredTracks by viewModel.filteredTracks.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val allTracks by viewModel.allTracks.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val importedCount by viewModel.importedCount.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Explorar, 1: Canciones, 2: Playlists, 3: Álbumes, 4: Artistas
    var selectedTrack by remember { mutableStateOf<AudioTrackEntity?>(null) }
    var showTrackOptions by remember { mutableStateOf(false) }

    // Song picker: lets the user upload music from the device gallery (SAF, multiple selection)
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (!uris.isNullOrEmpty()) viewModel.importAudios(uris)
    }

    LaunchedEffect(importedCount) {
        importedCount?.let { count ->
            android.widget.Toast.makeText(
                context,
                if (count > 0) "🎵 $count canción(es) agregada(s) a tu biblioteca" else "No se pudo importar el audio",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            viewModel.clearImportedCount()
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = IosSettingsColors.blue)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("PanaLink Music", color = IosSettingsColors.label, fontWeight = FontWeight.Bold)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = IosSettingsColors.label)
                        }
                    },
                    actions = {
                        // Import songs from the device gallery
                        IconButton(
                            onClick = { importLauncher.launch(arrayOf("audio/*")) },
                            enabled = !isImporting
                        ) {
                            if (isImporting) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = IosSettingsColors.blue, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.FileUpload, contentDescription = "Subir canciones", tint = IosSettingsColors.blue)
                            }
                        }
                        IconButton(onClick = onInvitationsClick) {
                            Icon(Icons.Default.Notifications, contentDescription = "Invitaciones", tint = IosSettingsColors.label)
                        }
                        IconButton(onClick = { showCreateDialog = true }) {
                            Icon(Icons.Default.AddCircleOutline, contentDescription = "Nueva Playlist", tint = IosSettingsColors.blue)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = IosSettingsColors.groupBackground.copy(alpha = 0.92f))
                )
                
                // Professional Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Buscar canciones, artistas...", color = IosSettingsColors.secondaryLabel) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = IosSettingsColors.secondaryLabel) },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = IosSettingsColors.cellElevated,
                        unfocusedContainerColor = IosSettingsColors.cell,
                        focusedBorderColor = IosSettingsColors.blue,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = IosSettingsColors.label,
                        unfocusedTextColor = IosSettingsColors.label
                    ),
                    singleLine = true
                )

                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = IosSettingsColors.groupBackground,
                    contentColor = IosSettingsColors.blue,
                    edgePadding = 16.dp,
                    divider = {}
                ) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                        Text("Explorar", modifier = Modifier.padding(12.dp))
                    }
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                        Text("Canciones", modifier = Modifier.padding(12.dp))
                    }
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }) {
                        Text("Playlists", modifier = Modifier.padding(12.dp))
                    }
                    Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }) {
                        Text("Álbumes", modifier = Modifier.padding(12.dp))
                    }
                    Tab(selected = selectedTab == 4, onClick = { selectedTab = 4 }) {
                        Text("Artistas", modifier = Modifier.padding(12.dp))
                    }
                }
            }
        },
        containerColor = IosSettingsColors.groupBackground,
        modifier = modifier
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (searchQuery.isNotEmpty()) {
                SearchResults(
                    tracks = filteredTracks, 
                    onTrackClick = onPlayTrack,
                    onTrackOptionsClick = {
                        selectedTrack = it
                        showTrackOptions = true
                    }
                )
            } else {
                when (selectedTab) {
                    0 -> ExploreSection(
                        playlists = playlists,
                        recentTracks = recentTracks,
                        favoriteTracks = favoriteTracks,
                        onPlaylistClick = onPlaylistClick,
                        onPlayTrack = onPlayTrack,
                        onTrackOptionsClick = { 
                            selectedTrack = it
                            showTrackOptions = true
                        },
                        onCreatePlaylist = { showCreateDialog = true }
                    )
                    1 -> AllSongsSection(
                        tracks = allTracks,
                        isImporting = isImporting,
                        onImportClick = { importLauncher.launch(arrayOf("audio/*")) },
                        onPlayTrack = onPlayTrack,
                        onTrackOptionsClick = {
                            selectedTrack = it
                            showTrackOptions = true
                        }
                    )
                    2 -> PlaylistGrid(playlists = playlists, onClick = onPlaylistClick)
                    3 -> AlbumGrid(albums = albums)
                    4 -> ArtistGrid(artists = artists)
                }
            }
        }
    }

    if (showTrackOptions && selectedTrack != null) {
        ModalBottomSheet(
            onDismissRequest = { showTrackOptions = false },
            containerColor = IosSettingsColors.cell
        ) {
            TrackOptionsBottomSheet(
                track = selectedTrack!!,
                userRole = com.example.media.playlist.PlaylistMemberRole.OWNER,
                onPlayNext = { onPlayTrack(selectedTrack!!) },
                onAddToPlaylist = { /* handled from playlist screens */ },
                onFavorite = { viewModel.toggleFavorite(selectedTrack!!) },
                onEditMetadata = { /* open editor */ },
                onShare = { /* share track */ },
                onDelete = { viewModel.deleteTrack(selectedTrack!!) },
                onDismiss = { showTrackOptions = false }
            )
        }
    }

    if (showCreateDialog) {
        var playlistName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Nueva Playlist") },
            text = {
                TextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    placeholder = { Text("Nombre de la playlist") }
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (playlistName.isNotBlank()) {
                        viewModel.createPlaylist(playlistName)
                        showCreateDialog = false
                    }
                }) {
                    Text("Crear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun ExploreSection(
    playlists: List<PlaylistEntity>,
    recentTracks: List<AudioTrackEntity>,
    favoriteTracks: List<AudioTrackEntity>,
    onPlaylistClick: (String) -> Unit,
    onPlayTrack: (AudioTrackEntity) -> Unit,
    onTrackOptionsClick: (AudioTrackEntity) -> Unit,
    onCreatePlaylist: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item { Spacer(modifier = Modifier.height(10.dp)) }
        item {
            SectionHeader("Mis Playlists", onSeeAll = {})
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                item { CreatePlaylistCard(onClick = onCreatePlaylist) }
                items(playlists) { playlist ->
                    PlaylistCard(playlist = playlist, onClick = { onPlaylistClick(playlist.id) })
                }
            }
        }

        item {
            SectionHeader("Escuchado recientemente")
            if (recentTracks.isEmpty()) {
                Text("No hay actividad reciente", color = IosSettingsColors.secondaryLabel, fontSize = 14.sp)
            }
        }
        
        items(recentTracks) { track ->
            TrackItem(track = track, onClick = { onPlayTrack(track) }, onTrackOptionsClick = onTrackOptionsClick)
        }

        item {
            SectionHeader("Tus favoritos")
            if (favoriteTracks.isEmpty()) {
                Text("No tienes favoritos aún", color = IosSettingsColors.secondaryLabel, fontSize = 14.sp)
            }
        }
        
        items(favoriteTracks) { track ->
            TrackItem(track = track, onClick = { onPlayTrack(track) }, onTrackOptionsClick = onTrackOptionsClick)
        }
        
        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

@Composable
fun AllSongsSection(
    tracks: List<AudioTrackEntity>,
    isImporting: Boolean,
    onImportClick: () -> Unit,
    onPlayTrack: (AudioTrackEntity) -> Unit,
    onTrackOptionsClick: (AudioTrackEntity) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${tracks.size} canción(es)",
                    color = IosSettingsColors.label,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Button(
                    onClick = onImportClick,
                    enabled = !isImporting,
                    colors = ButtonDefaults.buttonColors(containerColor = IosSettingsColors.blue),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    if (isImporting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = IosSettingsColors.label, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Importando...", color = IosSettingsColors.label, fontSize = 13.sp)
                    } else {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Subir canciones", color = IosSettingsColors.label, fontSize = 13.sp)
                    }
                }
            }
        }

        if (tracks.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.LibraryMusic,
                        contentDescription = null,
                        tint = IosSettingsColors.secondaryLabel.copy(alpha = 0.4f),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Tu biblioteca está vacía", color = IosSettingsColors.secondaryLabel, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Sube tus canciones favoritas desde tu galería",
                        color = IosSettingsColors.secondaryLabel.copy(alpha = 0.7f),
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    OutlinedButton(onClick = onImportClick) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, tint = IosSettingsColors.blue)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Elegir archivos de audio", color = IosSettingsColors.blue)
                    }
                }
            }
        }

        items(tracks, key = { it.id }) { track ->
            TrackItem(track = track, onClick = { onPlayTrack(track) }, onTrackOptionsClick = onTrackOptionsClick)
        }

        item { Spacer(modifier = Modifier.height(90.dp)) }
    }
}

@Composable
fun SearchResults(tracks: List<AudioTrackEntity>, onTrackClick: (AudioTrackEntity) -> Unit, onTrackOptionsClick: (AudioTrackEntity) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item { 
            Text("Resultados de búsqueda", color = IosSettingsColors.label, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 16.dp)) 
        }
        items(tracks) { track ->
            TrackItem(track = track, onClick = { onTrackClick(track) }, onTrackOptionsClick = onTrackOptionsClick)
        }
    }
}

@Composable
fun SectionHeader(title: String, onSeeAll: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = IosSettingsColors.label, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (onSeeAll != null) {
            TextButton(onClick = onSeeAll) {
                Text("Ver todas", color = IosSettingsColors.blue)
            }
        }
    }
}

@Composable
fun CreatePlaylistCard(onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = IosSettingsColors.cell),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.size(140.dp).clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = IosSettingsColors.label, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text("Nueva", color = IosSettingsColors.label)
        }
    }
}

@Composable
fun PlaylistCard(playlist: PlaylistEntity, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = IosSettingsColors.cell),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.width(140.dp).clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier.size(120.dp).clip(RoundedCornerShape(8.dp)).background(IosSettingsColors.cellElevated)
            ) {
                if (!playlist.coverPath.isNullOrEmpty()) {
                    AsyncImage(model = playlist.coverPath, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(48.dp).align(Alignment.Center), tint = IosSettingsColors.blue)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(playlist.name, color = IosSettingsColors.label, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Playlist", color = IosSettingsColors.secondaryLabel, fontSize = 12.sp)
        }
    }
}

@Composable
fun PlaylistGrid(playlists: List<PlaylistEntity>, onClick: (String) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        items(playlists.chunked(2)) { rowPlaylists ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                rowPlaylists.forEach { playlist ->
                    Box(modifier = Modifier.weight(1f)) {
                        PlaylistCard(playlist = playlist, onClick = { onClick(playlist.id) })
                    }
                }
                if (rowPlaylists.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun AlbumGrid(albums: List<String>) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        items(albums) { album ->
            Card(
                colors = CardDefaults.cardColors(containerColor = IosSettingsColors.cell),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Album, contentDescription = null, tint = IosSettingsColors.blue)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(album, color = IosSettingsColors.label, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ArtistGrid(artists: List<String>) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        items(artists) { artist ->
            Card(
                colors = CardDefaults.cardColors(containerColor = IosSettingsColors.cell),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = IosSettingsColors.blue)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(artist, color = IosSettingsColors.label, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
