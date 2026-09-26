package com.example.ui.components.chat.bubble

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.data.repository.CdnManager
import com.example.ui.components.chat.media.DownloadProgressOverlay
import com.example.ui.settings.ios.IosSettingsColors

/**
 * Componente principal para visualizar imágenes y videos en las burbujas de chat.
 * Soporta elementos individuales, cuadrículas de múltiples imágenes, subtítulos/links y overlays de carga.
 */
@Composable
fun MediaMessageBubble(
    mediaUrls: List<String>,
    isVideo: Boolean = false,
    thumbnailUrl: String? = null,
    durationLabel: String? = null,
    captionText: String? = null,
    bubbleColor: Color = IosSettingsColors.cell,
    isDownloading: Boolean = false,
    isUploading: Boolean = false,
    progress: Float? = null,
    bytesWritten: Long = 0L,
    totalBytes: Long = 0L,
    mediaTypeIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onMediaClick: (index: Int, url: String) -> Unit = { _, _ -> },
    onCancelProgress: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bubbleColor)
            .padding(3.dp)
    ) {
        Column {
            if (mediaUrls.size > 1 && !isVideo) {
                MultiImageGridBubble(
                    imageUrls = mediaUrls,
                    onImageClick = { index, url -> onMediaClick(index, url) }
                )
            } else if (mediaUrls.isNotEmpty()) {
                SingleMediaView(
                    url = mediaUrls.first(),
                    isVideo = isVideo,
                    thumbnailUrl = thumbnailUrl ?: mediaUrls.first(),
                    durationLabel = durationLabel,
                    onMediaClick = { onMediaClick(0, mediaUrls.first()) }
                )
            }

            if (!captionText.isNullOrEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = captionText!!,
                        color = IosSettingsColors.label,
                        fontSize = 15.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }

        // A completed progress value must never keep the loading overlay alive by itself.
        // Active download/upload flags still force visibility, including indeterminate work.
        val showProgressOverlay = isDownloading || isUploading || (progress != null && progress < 1f)
        DownloadProgressOverlay(
            isVisible = showProgressOverlay,
            progress = progress?.coerceIn(0f, 1f),
            isUploading = isUploading,
            onCancelOrRetryClick = onCancelProgress,
            statusText = if (isUploading) null else null,
            bytesWritten = bytesWritten,
            totalBytes = totalBytes,
            mediaTypeIcon = mediaTypeIcon
        )
    }
}

@Composable
private fun CdnCachedImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resolvedUrl by produceState(initialValue = CdnManager.resolveMediaUrlSync(url), key1 = url) {
        value = CdnManager.resolveMediaUrl(url)
    }

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(resolvedUrl.ifBlank { url })
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .crossfade(false)
            .build(),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Crop
    )
}

@Composable
private fun SingleMediaView(
    url: String,
    isVideo: Boolean,
    thumbnailUrl: String,
    durationLabel: String?,
    onMediaClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp, max = 400.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onMediaClick() },
        contentAlignment = Alignment.Center
    ) {
        CdnCachedImage(
            url = if (isVideo) thumbnailUrl else url,
            contentDescription = if (isVideo) "Video preview" else "Imagen",
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp, max = 400.dp)
        )

        if (isVideo) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(IosSettingsColors.mediaScrimSoft.copy(alpha = 0.2f))
            )

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(IosSettingsColors.mediaScrimSoft.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Reproducir Video",
                    tint = IosSettingsColors.label,
                    modifier = Modifier.size(36.dp)
                )
            }

            if (!durationLabel.isNullOrEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(IosSettingsColors.mediaScrim)
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            tint = IosSettingsColors.label,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = durationLabel!!,
                            color = IosSettingsColors.label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MultiImageGridBubble(
    imageUrls: List<String>,
    onImageClick: (index: Int, url: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val count = imageUrls.size

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(RoundedCornerShape(12.dp))
    ) {
        when {
            count == 2 -> {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    GridImageItem(
                        url = imageUrls[0],
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onClick = { onImageClick(0, imageUrls[0]) }
                    )
                    GridImageItem(
                        url = imageUrls[1],
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onClick = { onImageClick(1, imageUrls[1]) }
                    )
                }
            }
            count == 3 -> {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    GridImageItem(
                        url = imageUrls[0],
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onClick = { onImageClick(0, imageUrls[0]) }
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        GridImageItem(
                            url = imageUrls[1],
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            onClick = { onImageClick(1, imageUrls[1]) }
                        )
                        GridImageItem(
                            url = imageUrls[2],
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            onClick = { onImageClick(2, imageUrls[2]) }
                        )
                    }
                }
            }
            else -> {
                val remainingCount = count - 4
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        GridImageItem(
                            url = imageUrls[0],
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            onClick = { onImageClick(0, imageUrls[0]) }
                        )
                        GridImageItem(
                            url = imageUrls[1],
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            onClick = { onImageClick(1, imageUrls[1]) }
                        )
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        GridImageItem(
                            url = imageUrls[2],
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            onClick = { onImageClick(2, imageUrls[2]) }
                        )
                        GridImageItem(
                            url = imageUrls[3],
                            overlayCount = if (remainingCount > 0) remainingCount else null,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            onClick = { onImageClick(3, imageUrls[3]) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GridImageItem(
    url: String,
    modifier: Modifier = Modifier,
    overlayCount: Int? = null,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable { onClick() }
    ) {
        CdnCachedImage(
            url = url,
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )

        if (overlayCount != null && overlayCount > 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(IosSettingsColors.mediaScrim),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+$overlayCount",
                    color = IosSettingsColors.label,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
