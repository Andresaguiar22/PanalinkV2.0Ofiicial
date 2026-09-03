@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.ui.screen

import com.example.ui.components.*
import com.example.util.*

import androidx.compose.foundation.BorderStroke
import com.example.ui.components.FeedPostCard
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import com.example.ui.viewmodel.StatesViewModel
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import coil.compose.AsyncImage
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.animation.core.*
import androidx.compose.animation.*
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.asImageBitmap
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.identity.model.toIdentityUiState
import androidx.navigation.NavGraph.Companion.findStartDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.data.model.*
import com.example.data.supabase.SupabaseClient
import com.example.ui.viewmodel.*
import com.example.ui.theme.shimmerEffect
import com.example.ui.theme.getAvatarGradient
import com.example.ui.components.PanalinkPullToRefreshBox
import com.example.ui.theme.bounceClick
import com.example.ui.components.chat.list.ChatPreviewCard
import com.example.util.ChatListScrollManager
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.text.SimpleDateFormat
import java.util.*

import com.example.ui.viewmodel.NotificationsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingUploadsBanner(
    pendingUploadsViewModel: com.example.ui.viewmodel.PendingUploadsViewModel
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activeUploads by pendingUploadsViewModel.activeUploads.collectAsState()
    val progressMap by pendingUploadsViewModel.uploadProgressMap.collectAsState()
    if (activeUploads.isEmpty()) return

    val failedUpload = activeUploads.firstOrNull { it.status == "failed" }
    val uploading = activeUploads.firstOrNull { it.status == "uploading" } ?: activeUploads.firstOrNull { it.status != "failed" } ?: activeUploads.first()
    val info = progressMap[uploading.id]
    val percent = info?.progressPercent ?: if (uploading.status == "uploading") 0 else 0
    if (failedUpload != null) {
    val info = progressMap[failedUpload.id]
    val fPercent = 0
    val fLabel = when (failedUpload.uploadType) {
        "REEL" -> "Fallo al subir reel"
        "STATE" -> "Fallo al subir historia"
        "PROFILE" -> "Fallo al subir foto de perfil"
        "PROFILE_COVER" -> "Fallo al subir portada"
        else -> "Fallo al subir"
    }
    val fileExists = java.io.File(failedUpload.localFilePath).exists()
    val fText = if (fileExists) fLabel else "$fLabel — archivo local no existe"
    com.example.ui.components.MiniUploadBar(
        label = fText,
        percent = fPercent,
        color = Color(0xFFFF453A),
        onRetry = if (fileExists) { { pendingUploadsViewModel.retryUpload(context, failedUpload.id) } } else null,
        onDiscard = { pendingUploadsViewModel.dismissUpload(failedUpload.id) }
    )
    } else {
        val label = when (uploading.uploadType) {
            "REEL" -> "Subiendo reel"
            "STATE" -> "Subiendo historia"
            "PROFILE" -> "Subiendo foto de perfil"
            "PROFILE_COVER" -> "Subiendo portada"
            else -> "Subiendo"
        }
    val extra = activeUploads.size - 1
    val text = if (extra > 0) "$label (+$extra)" else label
    com.example.ui.components.MiniUploadBar(
        label = text,
        percent = percent,
        onCancel = { pendingUploadsViewModel.cancelUpload(context, uploading.id, deleteLocalFile = true) },
        color = Color(0xFF00C6FF)
    )
}
}


@Composable
fun LocalStatusChip(status: String) {
    val (bgColor, textColor, label) = when (status) {
        "uploading" -> Triple(Color(0xFF0C2A4A), Color(0xFF64D2FF), "Subiendo")
        "pending" -> Triple(Color(0xFF332B00), Color(0xFFFFD60A), "En cola")
        "failed" -> Triple(Color(0xFF3B0D0C), Color(0xFFFF453A), "Fallido")
        "completed" -> Triple(Color(0xFF0F3818), Color(0xFF30D158), "Publicado")
        "cancelled" -> Triple(Color(0xFF262628), Color(0xFF8E8E93), "Cancelado")
        else -> Triple(Color.DarkGray, Color.White, status)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(label, color = textColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

fun localFormatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes.toDouble() / (1024 * 1024)
    return if (mb < 0.1) {
        val kb = bytes.toDouble() / 1024
        String.format("%.1f KB", kb)
    } else {
        String.format("%.1f MB", mb)
    }
}


@Composable
fun PendingPostCard(post: com.example.data.database.PendingPostEntity) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.Gray.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("P", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Subiendo publicación...", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("En cola local", color = Color.Gray, fontSize = 11.sp)
                }
            }
            if (!post.content.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(post.content, color = Color.LightGray, fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = Color(0xFF00FF85),
                trackColor = Color(0xFF2C2C2E)
            )
        }
    }
}
