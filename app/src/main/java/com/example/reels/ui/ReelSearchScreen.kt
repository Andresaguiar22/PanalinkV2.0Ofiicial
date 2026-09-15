package com.example.reels.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyRowItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Profile
import com.example.data.model.UserStateWithUser
import com.example.ui.components.PanaAvatar
import com.example.ui.viewmodel.StatesViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * TikTok-style search over reels.
 *
 * - Magnifier key on the floating feed pill opens this screen.
 * - Both the free-text search and the "tap a hashtag" flow land here:
 *   [initialTag] pre-fills the chip/header and queries the hashtags[] column.
 */
@Composable
fun ReelSearchScreen(
    viewModel: StatesViewModel,
    initialTag: String? = null,
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit,
    onHashtagClick: (String) -> Unit,
    onUserClick: (String) -> Unit = {},
) {
    val searchResults by viewModel.searchResults.collectAsState()
    var query by rememberSaveable { mutableStateOf(initialTag?.removePrefix("#") ?: "") }
    var searching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var debounceJob by remember { mutableStateOf<Job?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    // TikTok shows matching ACCOUNTS first, then videos. User hits are stored
    // here (from ProfilesRepository) and rendered as a horizontal account row.
    var userResults by remember { mutableStateOf<List<com.example.data.model.Profile>>(emptyList()) }
    var searchingUsers by remember { mutableStateOf(false) }

    // First composition: if we arrived from a hashtag, run the query immediately.
    // Otherwise the bubble turns into a writable field: request focus and show the
    // keyboard right away (TikTok behaviour).
    LaunchedEffect(Unit) {
        if (initialTag != null) {
            searching = true
            viewModel.searchReels(tag = initialTag.removePrefix("#")) { searching = false }
        } else {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    val profilesRepo = remember { com.example.data.repository.ProfilesRepository() }

    fun runSearch(q: String) {
        val trimmed = q.trim()
        searching = true
        if (trimmed.isEmpty()) {
            viewModel.clearReelSearch()
            userResults = emptyList()
            searching = false
            searchingUsers = false
            return
        }
        if (initialTag == null) {
            searchingUsers = true
            scope.launch {
                profilesRepo.searchProfiles(trimmed)
                    .onSuccess { userResults = it.take(6) }
                    .onFailure { userResults = emptyList() }
                searchingUsers = false
            }
        }
        viewModel.searchReels(query = trimmed) {
            searching = false
        }
    }

    Scaffold(
        containerColor = Color(0xFF0F0F10)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
        ) {
            // Top bar: back + rounded search field (TikTok-ish) + clear.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = Color.White
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(22.dp))
                        .background(Color(0xFF2B2B2B))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        BasicTextField(
                            value = query,
                            onValueChange = { new ->
                                query = new
                                debounceJob?.cancel()
                                debounceJob = scope.launch {
                                    delay(350)
                                    runSearch(new)
                                }
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = Color.White,
                                fontSize = 16.sp
                            ),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
                                imeAction = androidx.compose.ui.text.input.ImeAction.Search
                            ),
                            keyboardActions = KeyboardActions(onSearch = {
                                keyboard?.hide()
                                runSearch(query)
                            }),
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (query.isEmpty()) {
                                        Text(
                                            text = "Buscar vídeos",
                                            color = Color.Gray,
                                            fontSize = 16.sp
                                        )
                                    }
                                    inner()
                                }
                            },
                            modifier = Modifier.weight(1f)
                                .focusRequester(focusRequester)
                        )
                        if (query.isNotEmpty()) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Filled.Clear,
                                contentDescription = "Limpiar",
                                tint = Color.Gray,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable {
                                        query = ""
                                        viewModel.clearReelSearch()
                                        keyboard?.hide()
                                    }
                            )
                        }
                    }
                }
            }
            // Hashtag header: when this screen was opened from a #tag tap we show
            // the chip as a styled header, TikTok's "#resultadostag" way.
            val trimmed = query.trim()
            if (initialTag != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF2B2B2B))
                            .clickable { onHashtagClick(initialTag.removePrefix("#")) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "#${initialTag.removePrefix("#")}",
                            color = Color(0xFF7FB8FF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            } else if (trimmed.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (searching) "Buscando..." else "${searchResults.size} vídeos",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                }
            }

            // TikTok shows matching accounts first: a horizontal row of avatars
            // with the display name below, tappable to open the profile.
            if (initialTag == null && trimmed.isNotEmpty() && userResults.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 4.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    lazyRowItems(userResults) { user ->
                        Column(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onUserClick(user.id) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            PanaAvatar(
                                avatarUrl = user.avatarUrl,
                                userId = user.id,
                                placeholderName = user.displayName,
                                size = 62.dp,
                                borderWidth = 0.dp,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = user.displayName.ifBlank { "pana" },
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 72.dp)
                            )
                        }
                    }
                }
            }

            when {
                searching && searchResults.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFFFE2C55),
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 3.dp
                        )
                    }
                }

                searchResults.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = null,
                                tint = Color.Gray.copy(alpha = 0.6f),
                                modifier = Modifier.size(52.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = if (trimmed.isEmpty() && initialTag == null)
                                    "Busca vídeos por título, hashtag o usuario"
                                else
                                    "No se encontraron vídeos",
                                color = Color.Gray,
                                fontSize = 15.sp
                            )
                        }
                    }
                }

                else -> {
                    ReelSearchGrid(
                        reels = searchResults,
                        onVideoClick = onVideoClick,
                        onHashtagClick = onHashtagClick
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReelSearchGrid(
    reels: List<UserStateWithUser>,
    onVideoClick: (String) -> Unit,
    onHashtagClick: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(reels) { reel ->
            val views = reel.state.viewsCount ?: 0
            val likes = reel.state.likesCount ?: 0
            Column(
                modifier = Modifier
                    .aspectRatio(9f / 16f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E1E1E))
                    .clickable { onVideoClick(reel.state.id) }
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = reel.state.thumbnailUrl
                            ?: reel.state.vcdnPosterUrl
                            ?: reel.state.mediaUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Bottom overlay: views + likes, TikTok style.
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    0.5f to Color.Transparent,
                                    1f to Color.Black.copy(alpha = 0.75f)
                                )
                            )
                            .padding(horizontal = 6.dp, vertical = 5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                text = formatCompact(views),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (likes > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Favorite,
                                    contentDescription = null,
                                    tint = Color(0xFFFF2B54),
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(Modifier.width(2.dp))
                                Text(
                                    text = formatCompact(likes),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatCompact(value: Int): String {
    if (value < 1_000) return value.toString()
    if (value < 1_000_000) {
        val k = value / 1000f
        return if (k < 10) String.format("%.1fK", k) else "${k.toInt()}K"
    }
    val m = value / 1_000_000f
    return if (m < 10) String.format("%.1fM", m) else "${m.toInt()}M"
}