package com.example.ui.components.chat.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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
import com.example.data.repository.CdnManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.ui.settings.ios.IosSettingsColors

@Composable
fun MediaGridItem(
    item: MediaGalleryItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Async resolution: resolveMediaUrlSync can perform VCDN BFF I/O (runBlocking),
    // so resolve on IO dispatcher to never block Compose/Main thread.
    val rawUrl = item.thumbnailUrl ?: item.url
    val resolvedUrl by produceState(rawUrl) {
        value = withContext(Dispatchers.IO) {
            CdnManager.resolveMediaUrl(rawUrl)
        }
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(IosSettingsColors.cell)
            .clickable { onClick() }
    ) {
        when (item.type) {
            MediaType.IMAGE -> {
                AsyncImage(
                    model = resolvedUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            MediaType.VIDEO -> {
                AsyncImage(
                    model = resolvedUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(IosSettingsColors.mediaScrimSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = "Video",
                        tint = IosSettingsColors.label,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            MediaType.DOCUMENT -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val extension = item.url.split(".").lastOrNull()?.uppercase() ?: "FILE"
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = IosSettingsColors.blue,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = extension,
                        color = IosSettingsColors.label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = item.url.split("/").lastOrNull() ?: "Documento",
                        color = IosSettingsColors.secondaryLabel,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            MediaType.AUDIO -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = IosSettingsColors.blue,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Audio",
                        color = IosSettingsColors.label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
