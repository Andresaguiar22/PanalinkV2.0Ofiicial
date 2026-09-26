package com.example.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Profile
import com.example.ui.viewmodel.ChatsViewModel
import com.example.ui.viewmodel.UserSearchUiState
import com.example.ui.settings.ios.IosSettingsColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchUsersScreen(
    viewModel: ChatsViewModel,
    onBack: () -> Unit,
    onChatOpened: (String, String) -> Unit // chatId, otherUserId
) {
    var query by remember { mutableStateOf("") }
    val searchState by viewModel.searchState.collectAsState()

    // Load all active or clear search on init
    LaunchedEffect(Unit) {
        viewModel.searchUsers("")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = query,
                        onValueChange = { 
                            query = it
                            viewModel.searchUsers(it)
                        },
                        placeholder = { Text("Buscar panas...", color = IosSettingsColors.secondaryLabel) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_users_input"),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = IosSettingsColors.label,
                            focusedTextColor = IosSettingsColors.label,
                            unfocusedTextColor = IosSettingsColors.label
                        ),
                        singleLine = true,
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { 
                                    query = ""
                                    viewModel.searchUsers("")
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Limpiar", tint = IosSettingsColors.label)
                                }
                            }
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = IosSettingsColors.label)
                    }
                },
                actions = {
                    Box(modifier = Modifier.padding(end = 12.dp)) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = IosSettingsColors.tertiaryLabel,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = IosSettingsColors.groupBackground
                )
            )
        },
        containerColor = IosSettingsColors.groupBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = searchState) {
                is UserSearchUiState.Idle -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = IosSettingsColors.cellElevated, modifier = Modifier.size(80.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Escribe un nombre para buscar",
                            color = IosSettingsColors.secondaryLabel,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Busca a tus panas para empezar a mensajear.",
                            color = IosSettingsColors.tertiaryLabel,
                            fontSize = 13.sp
                        )
                    }
                }
                is UserSearchUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = IosSettingsColors.blue
                    )
                }
                is UserSearchUiState.Success -> {
                    if (state.users.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("No se encontraron panas con \"$query\"", color = IosSettingsColors.secondaryLabel)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(state.users) { user ->
                                UserSearchResultItem(
                                    user = user,
                                    onClick = {
                                        viewModel.createChat(user) { chat ->
                                            onChatOpened(chat.id, user.id)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                is UserSearchUiState.Error -> {
                    Text(
                        text = state.message,
                        color = Color.Red,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

@Composable
fun UserSearchResultItem(
    user: Profile,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = user.avatarUrl,
            contentDescription = null,
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = user.displayName,
                color = IosSettingsColors.label,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}
