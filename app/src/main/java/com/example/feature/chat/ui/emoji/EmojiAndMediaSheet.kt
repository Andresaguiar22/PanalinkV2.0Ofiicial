package com.example.feature.chat.ui.emoji

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.model.StickerResult
import com.example.feature.chat.presentation.EmojiMediaUiState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items

data class EmojiCategory(val name: String, val icon: String, val emojis: List<String>)

@Composable
fun EmojiAndMediaSheet(
    emojiMedia: EmojiMediaUiState,
    isGhostMode: Boolean,
    onToggleGhostMode: () -> Unit,
    onEmojiSelected: (String) -> Unit,
    onStickerSelected: (StickerResult) -> Unit,
    onBackspace: () -> Unit,
    onTabSelected: (Int) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val selectedTab = emojiMedia.selectedTab // 0 = Emoji, 1 = GIF, 2 = Sticker
    val searchQuery = emojiMedia.searchQuery
    val isSearchActive = emojiMedia.isSearchActive
    var selectedEmojiCategory by remember { mutableStateOf(0) }

    // Emoji categories setup
    val emojiCategories = remember {
        listOf(
            EmojiCategory("Smileys", "😀", listOf(
                "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚", "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🥸", "🤩", "🥳", "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😣", "😖", "😫", "😩", "🥺", "😢", "😭", "😤", "😠", "😡", "🤬", "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰", "😥", "😓", "🤗", "🤔", "🫣", "🤭", "🤫", "🤥", "😶", "😶‍🌫️", "😐", "😑", "😬", "🫨", "🫠"
            )),
            EmojiCategory("Animales", "🐾", listOf(
                "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵", "🐔", "🐧", "🐦", "🦆", "🦅", "🦉", "🦇", "🐺", "🐗", "🐴", "🦄", "🐝", "🪱", "🐛", "🦋", "🐌", "🐞", "🐜", "🦟", "🦗", "🕷", "🕸", "🦂", "🐢", "🐍", "🦎", "🐙", "🦑", "🦞", "🦀", "🐡", "🐠", "🐟", "🐬", "🐳", "🐋", "🦈"
            )),
            EmojiCategory("Comida", "🍔", listOf(
                "🍏", "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🍈", "🍒", "🍑", "🍍", "🥥", "🥝", "🍅", "🥑", "🥦", "🥬", "🥒", "🌶️", "🌽", "🥕", "🧅", "🥔", "🍠", "🥐", "🥯", "🍞", "🥖", "🥨", "🥞", "🧀", "🍖", "🍗", "🥩", "🥓", "🍔", "🍟", "🍕", "🌭", "🥪", "🌮", "🌯", "🍳", "🥘", "🍲", "🫕"
            )),
            EmojiCategory("Deportes", "⚽", listOf(
                "⚽", "🏀", "🏈", "⚾", "🥎", "🎾", "🏐", "🏉", "🎱", "🏓", "🏸", "🥅", "🏒", "🏑", "🏏", "⛳", "🏹", "🎣", "🥊", "🥋", "🎽", "🛹", "🛷", "🎿", "🏂", "🏋️", "🤸", "🤺", "🤼", "🤽", "🤾", "🤹", "🧘", "🏆", "🥇", "🥈", "🥉", "🏅", "🎖", "🎫", "🎟", "🎪"
            )),
            EmojiCategory("Vehículos", "🚗", listOf(
                "🚗", "🚕", "🚙", "🚌", "🚎", "🏎", "🚓", "🚑", "🚒", "🚐", "🚚", "🚛", "🚜", "🛵", "🏍", "🛺", "🚲", "🛴", "🚏", "🛣", "🛤", "⛽", "🚨", "🚥", "🚦", "🛑", "🚧", "⚓", "⛵", "🚤", "🛳", "🛥", "🚢", "✈️", "🛩", "🚀", "🛸", "🛰"
            )),
            EmojiCategory("Objetos", "💡", listOf(
                "⌚", "📱", "💻", "⌨", "🖥", "🖨", "🖱", "🕹", "💾", "💿", "📀", "📸", "📹", "🎥", "👓", "🕶", "🔬", "🔭", "📡", "🕯", "💡", "🔦", "🏮", "📔", "📕", "📖", "📗", "📘", "📙", "📚", "📓", "📝", "✉️", "📧", "📨", "📩", "📦", "📫", "📪", "📬", "📭", "📮", "📯", "📜", "📂"
            )),
            EmojiCategory("Símbolos", "❤️", listOf(
                "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔", "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💟", "☮️", "✝️", "☪️", "🕉️", "☸️", "✡️", "🔯", "☯️", "☦️", "🛐", "⛎", "🫵", "👍", "👎", "✊", "👊", "🤛", "🤜", "🤞", "✌️", "🤟", "🤘", "👌", "🤌", "🤏", "👈", "👉", "👆", "👇"
            )),
            EmojiCategory("Banderas", "🚩", listOf(
                "🏳️", "🏴", "🏁", "🚩", "🏳️‍🌈", "🏳️‍⚧️", "🇺🇸", "🇻🇪", "🇪🇸", "🇲🇽", "🇨🇴", "🇦🇷", "🇧🇷", "🇨🇱", "🇵🇪", "🇮🇹", "🇫🇷", "🇩🇪", "🇬🇧", "🪦", "🇨🇦", "🇦🇺", "🇬🇷"
            ))
        )
    }

    var previewSticker by remember { mutableStateOf<StickerResult?>(null) }

    // Zoom/Preview Dialog
    if (previewSticker != null) {
        Dialog(
            onDismissRequest = { previewSticker = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable { previewSticker = null },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(260.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF1F2C34))
                        .padding(20.dp)
                ) {
                    AsyncImage(
                        model = previewSticker!!.url,
                        contentDescription = "Preview",
                        modifier = Modifier
                            .size(180.dp)
                            .padding(8.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Vista Previa",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Toca fuera de la tarjeta para cerrar",
                        color = Color(0xFF00A884),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp)
            .background(Color(0xFF111B21))
    ) {
        // Handle (drag indicator bar)
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 8.dp)
                .size(40.dp, 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.Gray.copy(alpha = 0.5f))
        )

        // Unified Header Row or Search Active Row
        if (isSearchActive) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                IconButton(onClick = {
                    onSearchActiveChange(false)
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Atrás",
                        tint = Color(0xFF8596A0)
                    )
                }

                androidx.compose.foundation.text.BasicTextField(
                    value = searchQuery,
                    onValueChange = { onSearchQueryChange(it) },
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF202C33), RoundedCornerShape(24.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 15.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFF00A884)),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (searchQuery.isEmpty()) {
                                val hintText = when (selectedTab) {
                                    0 -> "Buscar emoji..."
                                    1 -> "Buscar GIF..."
                                    else -> "Buscar stickers..."
                                }
                                Text(hintText, color = Color(0xFF8596A0), fontSize = 15.sp)
                            }
                            innerTextField()
                        }
                    }
                )

                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Limpiar", tint = Color(0xFF8596A0))
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Search Button
                IconButton(onClick = { onSearchActiveChange(true) }) {
                    Icon(Icons.Default.Search, contentDescription = "Buscar", tint = Color(0xFF8596A0))
                }

                // Center: Unified selector of 3 tabs (Emoji, GIF, Sticker)
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF202C33),
                    modifier = Modifier
                        .width(220.dp)
                        .height(38.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("Emoji", "GIF", "Sticker").forEachIndexed { index, title ->
                            val isSelected = selectedTab == index
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(if (isSelected) Color(0xFF374248) else Color.Transparent)
                                    .clickable { onTabSelected(index) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (index == 0) {
                                    Icon(
                                        imageVector = Icons.Default.SentimentSatisfied,
                                        contentDescription = "Emojis",
                                        tint = if (isSelected) Color.White else Color(0xFF8596A0),
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else if (index == 1) {
                                    Text(
                                        text = "GIF",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (isSelected) Color.White else Color(0xFF8596A0)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.StickyNote2,
                                        contentDescription = "Stickers",
                                        tint = if (isSelected) Color.White else Color(0xFF8596A0),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Botón Modo Fantasma (mensaje de una vista) — despeja el campo de escritura
                IconButton(onClick = onToggleGhostMode) {
                    Icon(
                        imageVector = if (isGhostMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Mensaje de una vista",
                        tint = if (isGhostMode) Color(0xFFBB86FC) else Color(0xFF8596A0),
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Right: Backspace/Clear character
                IconButton(onClick = onBackspace) {
                    Icon(Icons.Default.Backspace, contentDescription = "Borrar", tint = Color(0xFF8596A0))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Grid Content depending on active tab
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 8.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    // Pestaña Emoji
                    val currentEmojis = remember(selectedEmojiCategory, searchQuery) {
                        if (searchQuery.isNotEmpty()) {
                            emojiCategories.flatMap { it.emojis }
                        } else {
                            emojiCategories[selectedEmojiCategory].emojis
                        }
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(7),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(currentEmojis) { emoji ->
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .clip(CircleShape)
                                        .clickable { onEmojiSelected(emoji) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = emoji, fontSize = 28.sp)
                                }
                            }
                        }

                        // Bottom categories selector bar (ONLY when search is NOT active)
                        if (!isSearchActive) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF1F2C34))
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceAround,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                emojiCategories.forEachIndexed { idx, category ->
                                    val isCatSelected = selectedEmojiCategory == idx
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(if (isCatSelected) Color(0xFF00A884) else Color.Transparent)
                                            .clickable { selectedEmojiCategory = idx },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = category.icon, fontSize = 18.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // Pestaña GIF
                    if (emojiMedia.isGifsLoading) {
                        CircularProgressIndicator(
                            color = Color(0xFF00A884),
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else if (emojiMedia.gifs.isEmpty()) {
                        Text(
                            text = "No se encontraron GIFs",
                            color = Color(0xFF8596A0),
                            fontSize = 13.sp,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(emojiMedia.gifs) { gif ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF202C33))
                                        .clickable {
                                            onStickerSelected(gif)
                                        }
                                ) {
                                    AsyncImage(
                                        model = gif.preview,
                                        contentDescription = "GIF",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // Pestaña Sticker
                    com.example.features.stickers.presentation.StickerPanel(
                        modifier = Modifier.fillMaxSize(),
                        onStickerSelected = { sticker ->
                            onStickerSelected(StickerResult(url = sticker.imageUrl, preview = sticker.imageUrl))
                        }
                    )
                }
            }
        }
    }
}
