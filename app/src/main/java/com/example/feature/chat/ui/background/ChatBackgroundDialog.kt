package com.example.feature.chat.ui.background

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage

@Composable
fun ChatBackgroundDialog(
    visible: Boolean,
    chatWallpaperState: String,
    wallpaperCustomUri: String? = null,
    onDismiss: () -> Unit,
    onSelect: (ChatWallpaperSpec) -> Unit,
) {
    if (!visible) return

    val context = LocalContext.current
    val presetItems = ChatWallpaperSpec.PRESETS

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            onSelect(ChatWallpaperSpec.Custom("custom", "Mi foto", uri.toString()))
        }
    }

    val activeSpec = ChatWallpaperSpec.fromId(chatWallpaperState, wallpaperCustomUri)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2C34)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Seleccionar Fondo",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                WallpaperCard(
                    spec = ChatWallpaperSpec.Custom("custom", "Elegir de mi galería", ""),
                    selected = activeSpec is ChatWallpaperSpec.Custom,
                    onClick = { galleryLauncher.launch("image/*") }
                )

                Spacer(Modifier.height(12.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(400.dp)
                ) {
                    items(presetItems) { spec ->
                        WallpaperCard(
                            spec = spec,
                            selected = activeSpec.id == spec.id,
                            onClick = { onSelect(spec) }
                        )
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End).padding(top = 8.dp)
                ) {
                    Text("Cerrar", color = Color(0xFF25D366))
                }
            }
        }
    }
}

@Composable
private fun WallpaperCard(
    spec: ChatWallpaperSpec,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(0.85f)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) Color(0xFF25D366) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        when (spec) {
            is ChatWallpaperSpec.Gradient -> {
                val base = Brush.linearGradient(
                    colors = listOf(Color(spec.start), Color(spec.end)),
                    start = Offset.Zero,
                    end = Offset.Infinite
                )
                Box(Modifier.fillMaxSize().background(base)) {
                    spec.depthHue?.let { hue ->
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(Color(hue).copy(alpha = 0.55f), Color.Transparent),
                                        radius = 900f
                                    )
                                )
                        )
                    }
                }
            }
            is ChatWallpaperSpec.Solid -> {
                Box(Modifier.fillMaxSize().background(Color(spec.color)))
            }
            is ChatWallpaperSpec.Remote -> {
                AsyncImage(
                    model = spec.url,
                    contentDescription = spec.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            is ChatWallpaperSpec.Custom -> {
                if (spec.uri.startsWith("http") || spec.uri.startsWith("content://")) {
                    AsyncImage(
                        model = spec.uri,
                        contentDescription = spec.label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF8B5CF6), Color(0xFF0F172A))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("＋", color = Color.White, fontSize = 34.sp)
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(4.dp)
        ) {
            Text(spec.label, color = Color.White, fontSize = 10.sp, modifier = Modifier.align(Alignment.Center))
        }
    }
}