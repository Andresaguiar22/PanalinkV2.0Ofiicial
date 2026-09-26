package com.example.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.chat.search.ChatSearchResultItem
import com.example.ui.viewmodel.ChatSearchUiState
import com.example.ui.viewmodel.ChatSearchViewModel
import com.example.ui.settings.ios.IosSettingsColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSearchScreen(
    chatId: String,
    onBack: () -> Unit,
    onResultClick: (String) -> Unit, // messageId
    viewModel: ChatSearchViewModel = viewModel()
) {
    val query by viewModel.query.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(chatId) {
        viewModel.initSearch(chatId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = query,
                        onValueChange = { viewModel.onQueryChange(it) },
                        placeholder = { Text("Buscar en el chat...", color = IosSettingsColors.secondaryLabel) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = IosSettingsColors.blue,
                            focusedTextColor = IosSettingsColors.label,
                            unfocusedTextColor = IosSettingsColors.label
                        ),
                        singleLine = true,
                        trailingIcon = {
                            if (query.isNotBlank()) {
                                IconButton(onClick = { viewModel.onQueryChange("") }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Limpiar", tint = IosSettingsColors.label)
                                }
                            }
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = IosSettingsColors.label)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = IosSettingsColors.cell.copy(alpha = 0.72f)
                )
            )
        },
        containerColor = IosSettingsColors.groupBackground
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = uiState) {
                is ChatSearchUiState.Idle -> {
                    SearchEmptyState(
                        icon = Icons.Rounded.Search,
                        message = "Busca mensajes en esta conversación"
                    )
                }
                is ChatSearchUiState.Searching -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = IosSettingsColors.blue
                    )
                }
                is ChatSearchUiState.Empty -> {
                    SearchEmptyState(
                        icon = Icons.Rounded.Search,
                        message = "No se encontraron resultados para \"$query\""
                    )
                }
                is ChatSearchUiState.Error -> {
                    Text(
                        text = state.message,
                        color = Color.Red,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is ChatSearchUiState.Results -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(
                            items = state.messages,
                            key = { index, message -> "${message.id}_$index" }
                        ) { _, message ->
                            ChatSearchResultItem(
                                message = message,
                                onClick = { onResultClick(message.id) }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = IosSettingsColors.separator
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchEmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = IosSettingsColors.label.copy(alpha = 0.1f),
            modifier = Modifier.size(100.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            color = IosSettingsColors.tertiaryLabel,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
