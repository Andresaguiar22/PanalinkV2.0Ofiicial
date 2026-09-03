package com.example.feature.chat.ui.background

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage

@Composable
fun ChatBackgroundDialog(
    visible: Boolean,
    chatWallpaperState: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    if (visible) {
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
    
                val backgrounds = listOf(
                    "dark_slate" to "Defecto",
                    "https://images.unsplash.com/photo-1557683316-973673baf926" to "Noche Azul",
                    "https://images.unsplash.com/photo-1506744038136-46273834b3fb" to "Paisaje",
                    "https://images.unsplash.com/photo-1579546929518-9e396f3cc809" to "Gradiente",
                    "https://images.unsplash.com/photo-1490750967868-88aa4486c946" to "Floral Suave",
                    "https://images.unsplash.com/photo-1518531933037-91b2f5f229cc" to "Oscuro Arte",
                    "color_solid_green" to "Verde Sólido",
                    "color_solid_blue" to "Azul Profundo"
                )
    
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(350.dp)
                ) {
                    items(backgrounds) { (url, name) ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(0.6f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onSelect(url)
                                }
                                .border(
                                    width = if (chatWallpaperState == url) 2.dp else 0.dp,
                                    color = if (chatWallpaperState == url) Color(0xFF25D366) else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                        ) {
                            if (url.startsWith("http")) {
                                AsyncImage(
                                    model = url,
                                    contentDescription = name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else if (url == "dark_slate") {
                                Box(Modifier.fillMaxSize().background(Color(0xFF0B141A)))
                            } else if (url == "color_solid_green") {
                                Box(Modifier.fillMaxSize().background(Color(0xFF075E54)))
                            } else if (url == "color_solid_blue") {
                                Box(Modifier.fillMaxSize().background(Color(0xFF001A33)))
                            }
    
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .padding(4.dp)
                            ) {
                                Text(name, color = Color.White, fontSize = 10.sp, modifier = Modifier.align(Alignment.Center))
                            }
                        }
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
}
