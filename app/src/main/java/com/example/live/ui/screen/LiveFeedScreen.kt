package com.example.live.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.live.ui.LiveGlassBorder
import com.example.live.ui.LiveGlassFill
import com.example.live.ui.LiveCardShape
import com.example.live.ui.LiveNeon
import com.example.live.ui.LiveNightBase
import com.example.live.ui.LiveOnNeon
import com.example.live.ui.components.LiveCard
import com.example.live.ui.components.LiveSkeletonCard
import com.example.live.ui.viewmodel.LiveViewModel

private val ListPadding = 16.dp
private val FabSize = 64.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveFeedScreen(
    viewModel: LiveViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToViewer: (String) -> Unit = {},
    onNavigateToBroadcast: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val liveStreams = uiState.activeLives.filter { it.status == "LIVE" }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LiveNightBase)
            .drawBehind {
                // Dos resplandores neón muy tenues sobre la base nocturna: arriba a la
                // derecha (el más marcado) y abajo a la izquierda, como en el diseño.
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(LiveNeon.copy(alpha = 0.34f), Color.Transparent),
                        center = Offset(size.width * 0.98f, size.height * 0.02f),
                        radius = size.width * 1.15f,
                    )
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(LiveNeon.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(-size.width * 0.05f, size.height * 0.72f),
                        radius = size.width * 0.85f,
                    )
                )
            }
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "Panalink Live",
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Regresar")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                    )
                )
            },
            floatingActionButton = {
                LiveBroadcastFab(onClick = onNavigateToBroadcast)
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when {
                    uiState.isLoading && uiState.activeLives.isEmpty() -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = listContentPadding(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(3) { LiveSkeletonCard() }
                        }
                    }

                    liveStreams.isEmpty() -> {
                        EmptyLiveFeed(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = ListPadding)
                        )
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = listContentPadding(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(items = liveStreams, key = { it.id }) { live ->
                                LiveCard(
                                    live = live,
                                    onClick = { onNavigateToViewer(live.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** El padding inferior deja aire para que el FAB no tape la última tarjeta. */
private fun listContentPadding() = PaddingValues(
    start = ListPadding,
    end = ListPadding,
    top = 8.dp,
    bottom = FabSize + 48.dp,
)

/** FAB circular con halo neón: capa difuminada detrás + sombra de color. */
@Composable
private fun LiveBroadcastFab(onClick: () -> Unit) {
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(FabSize)
                .blur(26.dp, BlurredEdgeTreatment.Unbounded)
                .background(LiveNeon.copy(alpha = 0.60f), CircleShape)
        )

        FloatingActionButton(
            onClick = onClick,
            shape = CircleShape,
            containerColor = LiveNeon,
            contentColor = LiveOnNeon,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                focusedElevation = 0.dp,
                hoveredElevation = 0.dp,
            ),
            modifier = Modifier
                .size(FabSize)
                .shadow(
                    elevation = 20.dp,
                    shape = CircleShape,
                    ambientColor = LiveNeon,
                    spotColor = LiveNeon,
                    clip = false,
                )
        ) {
            Icon(
                imageVector = Icons.Rounded.Videocam,
                contentDescription = "Transmitir en Vivo",
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

@Composable
private fun EmptyLiveFeed(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(LiveCardShape)
                .background(LiveGlassFill)
                .border(BorderStroke(1.dp, LiveGlassBorder), LiveCardShape)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Rounded.LiveTv,
                contentDescription = null,
                tint = LiveNeon.copy(alpha = 0.55f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No hay transmisiones en vivo",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Sé el primero en transmitir",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
