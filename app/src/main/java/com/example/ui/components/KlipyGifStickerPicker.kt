package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.PanaApplication
import com.example.data.model.StickerResult
import com.example.data.repository.StickerRepository
import com.example.ui.settings.ios.IosSettingsColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Selector de GIFs/stickers Klipy (sin anuncios) reutilizable para comentarios.
 *
 * Muestra una parrilla ampliada: al entrar con query vacío se pinta trending + una
 * página extra. Los chips superiores son categorías reales de Klipy y su tap cambia
 * la búsqueda para descubrir más GIFs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KlipyGifStickerPicker(
    onSelected: (StickerResult) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = PanaApplication.instance
    var tab by remember { mutableStateOf(0) } // 0 = GIF, 1 = Sticker
    var query by remember { mutableStateOf("") }
    var gifs by remember { mutableStateOf<List<StickerResult>>(emptyList()) }
    var stickers by remember { mutableStateOf<List<StickerResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var categories by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    suspend fun loadGifs(term: String) {
        isLoading = true
        val list = StickerRepository.searchGifs(query = term, limit = 24)
        gifs = list
        isLoading = false
    }

    suspend fun loadStickers(term: String) {
        isLoading = true
        val list = StickerRepository.getStickers(context, query = term.ifEmpty { null }, limit = 24)
        stickers = list
        isLoading = false
    }

    LaunchedEffect(Unit) {
        val cats = StickerRepository.getGifCategories()
        categories = cats
        loadGifs("")
        loadStickers("")
    }

    fun onQueryChange(newQuery: String) {
        query = newQuery
        searchJob?.cancel()
        searchJob = scope.launch {
            delay(300)
            val term = newQuery.trim()
            if (tab == 0) loadGifs(term) else loadStickers(term)
        }
    }

    fun selectCategory(cat: String) {
        selectedCategory = cat
        query = cat
        searchJob?.cancel()
        scope.launch { loadGifs(cat) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = IosSettingsColors.groupBackground,
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .padding(vertical =   8.dp)
                    .size(40.dp, 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.Gray.copy(alpha = 0.5f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .padding(horizontal =   12.dp)
        ) {
            // Header with tabs GIF | Sticker
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Envía un GIF",
                    color = IosSettingsColors.label,
                    fontSize =   16.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = IosSettingsColors.label)
 }
                }
            }

            // Chips: categorías reales Klipy (solo GIF tab,y solo cuando no hay búsqueda manual)
 
            if (tab == 0 && categories.isNotEmpty() && query.isBlank()) {

                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(vertical =   6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories.take(14)) { cat ->
                        val isSelected = selectedCategory == cat
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) IosSettingsColors.blue else IosSettingsColors.cellElevated)
                                .clickable { selectCategory(cat) }
                                .padding(horizontal =   12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = cat.replaceFirstChar { it.titlecase() },
                                color = IosSettingsColors.label,
                                fontSize =   12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // Search field
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical =   6.dp)
                    .background(IosSettingsColors.cellElevated, RoundedCornerShape(20.dp))
                    .padding(horizontal =   12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = query,
                    onValueChange = { onQueryChange(it) },
                    modifier = Modifier.weight(1f),
                    textStyle = androidx.compose.ui.text.TextStyle(color = IosSettingsColors.label, fontSize =   13.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(IosSettingsColors.blue),
                    singleLine = true,
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) Text("Buscar GIFs o stickers...", color = IosSettingsColors.secondaryLabel, fontSize =   13.sp)
                            inner()
                        }
                    }
                )
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            query = ""
                            selectedCategory = null
                            searchJob?.cancel()
                            scope.launch { loadGifs("") }
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Limpiar", tint = IosSettingsColors.secondaryLabel, modifier = Modifier.size(14.dp))
                    }
                } else {
                    Box(modifier = Modifier.size(24.dp))
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Picker grid
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = IosSettingsColors.blue, strokeWidth = 2.dp)
                }
            } else {
                val list = if (tab == 0) gifs else stickers
                if (list.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No se encontraron resultados", color = IosSettingsColors.secondaryLabel, fontSize = 13.sp)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize().padding(bottom =   16.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(list, key = { it.id ?: it.url }) { item ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(IosSettingsColors.cellElevated)
                                    .clickable { onSelected(item) }
                            ) {
                                AsyncImage(
                                    model = item.preview,
                                    contentDescription = item.title ?: "GIF",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }
                }
            }

            // Tab switcher (bottom]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top =   8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf("GIF" to 0, "Stickers" to 1).forEach { (title, idx) ->
                    val isSelected = tab == idx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) IosSettingsColors.cellElevated else Color.Transparent)
                            .clickable {

                                tab = idx
                                searchJob?.cancel()
                        if (idx == 0) { scope.launch { loadGifs(query.ifBlank { "" }) } } else { scope.launch { loadStickers(query.ifBlank { "" }) } }
                            }
                            .padding(vertical =   8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            color = IosSettingsColors.label,
                            fontSize =   13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
