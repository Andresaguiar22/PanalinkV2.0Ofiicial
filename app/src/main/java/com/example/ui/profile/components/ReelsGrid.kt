package com.example.ui.profile.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.supabase.SupabaseClient
import com.example.ui.viewmodel.ProfileViewModel
import com.example.ui.settings.ios.IosSettingsColors

@Composable
fun ReelsGrid(viewModel: ProfileViewModel, onNavigateToReel: (String) -> Unit) {
    val reelsState by viewModel.reelsState.collectAsState()
    val currentUid = SupabaseClient.currentUser?.id ?: ""
    var reelToDelete by remember { mutableStateOf<com.example.data.model.UserState?>(null) }

    if (reelsState.isSuccess) {
        val reels = reelsState.getOrNull() ?: emptyList()
        if (reels.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = IosSettingsColors.label.copy(alpha = 0.3f),
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "Aún no has publicado Reels",
                        style = MaterialTheme.typography.bodyMedium,
                        color = IosSettingsColors.secondaryLabel
                    )
                }
            }
        } else {
            val chunkedReels = reels.chunked(3)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
            chunkedReels.forEach { rowReels ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (i in 0 until 3) {
                        if (i < rowReels.size) {
                            val reel = rowReels[i]
                            // FIX (Acción 1): Use cached thumbnailUrl or vcdnPosterUrl for grid
                            // thumbnails — NOT the full video URL. The previous code resolved
                            // mediaUrl (a vcdn:// pointer or HLS stream) via Coil as an image,
                            // which failed silently for videos, leaving gray placeholders
                            // (e.g. image_1000420472.jpg).
                            // FIX (Acción 2): rows persisted before VCDN moved posters to
                            // cdn.vcdn.me keep a dead storage.vcdn.me/.../poster.jpg that 404s.
                            // rememberReelThumbnail detects that and re-resolves the real
                            // poster from the VCDN BFF, so the tile is no longer gray.
                            val resolvedThumbnail = rememberReelThumbnail(
                                thumbnailUrl = reel.state.thumbnailUrl,
                                posterUrl = reel.state.vcdnPosterUrl,
                                mediaUrl = reel.state.mediaUrl,
                                vcdnVideoId = reel.state.vcdnVideoId
                            )
                            val context = LocalContext.current
                            val imageRequest = remember(resolvedThumbnail) {
                                ImageRequest.Builder(context)
                                    // Never fall back to mediaUrl: a vcdn:// pointer is
                                    // not an image URL, so Coil would just fail again.
                                    .data(resolvedThumbnail.ifBlank { null })
                                    .crossfade(true)
                                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                    .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                    .build()
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(0.75f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(IosSettingsColors.cell)
                                    .clickable { onNavigateToReel(reel.state.id) }
                                    .pointerInput(reel.state.id) {
                                        detectTapGestures(
                                            onLongPress = {
                                                reelToDelete = reel.state
                                            }
                                        )
                                    }
                            ) {
                                AsyncImage(
                                    model = imageRequest,
                                    contentDescription = "Reel thumbnail",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )

                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(Color.Transparent, IosSettingsColors.mediaScrimSoft)
                                                )
                                            )
                                    )

                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = null,
                                            tint = IosSettingsColors.label,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = (reel.state.viewsCount ?: 0).toString(),
                                            color = IosSettingsColors.label,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    } else {
        Text("Error al cargar reels: ${reelsState.exceptionOrNull()?.message}", color = Color.Red, modifier = Modifier.padding(16.dp))
    }

    if (reelToDelete != null) {
        AlertDialog(
            onDismissRequest = { reelToDelete = null },
            title = { Text("¿Borrar Reel?", color = IosSettingsColors.label) },
            text = { Text("Esta acción no se puede deshacer.", color = IosSettingsColors.secondaryLabel) },
            containerColor = IosSettingsColors.cellElevated,
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteReel(reelToDelete!!.id, currentUid)
                    reelToDelete = null
                }) {
                    Text("Borrar", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { reelToDelete = null }) {
                    Text("Cancelar", color = IosSettingsColors.label)
                }
            }
        )
    }
}
