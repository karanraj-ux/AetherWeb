package com.aetherweb.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aetherweb.app.MeshMusicManager
import com.aetherweb.app.MeshViewModel
import com.aetherweb.app.ui.components.AetherRoom
import com.aetherweb.app.ui.theme.EmberOrange
import com.aetherweb.app.ui.theme.FlameAmber

/**
 * AetherWeb root: Rooms are the home. Bottom nav = Rooms | Music | Games | You.
 * Opening a room pushes RoomDetailScreen; system back closes it first.
 * An active game takes over fullscreen (minimizable); the mini-player only
 * shows while audio is actually playing (or paused and not dismissed).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AetherRoot(viewModel: MeshViewModel) {
    var tab by remember { mutableStateOf("Rooms") }
    var openRoom by remember { mutableStateOf<AetherRoom?>(null) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val musicState by MeshMusicManager.state.collectAsStateWithLifecycle()

    // ---- Active game overlay (restored from legacy MainChatScreen) ----
    val isGameActive = uiState.sharedMediaType != "none" && uiState.sharedMediaType.isNotEmpty()
    var isGameMinimized by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.sharedMediaType) {
        if (isGameActive) isGameMinimized = false
    }
    val showGameOverlay = isGameActive && !isGameMinimized

    BackHandler(enabled = showGameOverlay) { isGameMinimized = true }
    BackHandler(enabled = openRoom != null && !showGameOverlay) { openRoom = null }

    // ---- Mini-player visibility ----
    val track = musicState.currentTrack
    var miniDismissedTrackId by remember { mutableStateOf<String?>(null) }
    val onMusicTab = tab == "Music" && openRoom == null
    val showMiniPlayer = track != null &&
        !showGameOverlay &&
        !onMusicTab &&
        (musicState.isPlaying || miniDismissedTrackId != track.id)

    Scaffold(
        bottomBar = {
            if (!showGameOverlay) {
                Column {
                    if (showMiniPlayer && track != null) {
                        Surface(
                            tonalElevation = 6.dp,
                            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { tab = "Music"; openRoom = null }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (musicState.isPartyModeHost || musicState.isPartyModeListener) FlameAmber.copy(alpha = 0.25f)
                                            else EmberOrange.copy(alpha = 0.18f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.MusicNote, null,
                                        tint = if (musicState.isPartyModeHost || musicState.isPartyModeListener) FlameAmber else EmberOrange,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        track.title, fontWeight = FontWeight.SemiBold,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        when {
                                            musicState.isPartyModeHost -> "Broadcasting to room"
                                            musicState.isPartyModeListener -> "Synced • ${musicState.partyHostName}"
                                            else -> track.artist
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { MeshMusicManager.togglePlayPause() }) {
                                    Icon(
                                        if (musicState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (musicState.isPlaying) "Pause" else "Play"
                                    )
                                }
                                IconButton(onClick = { miniDismissedTrackId = track.id }) {
                                    Icon(
                                        Icons.Default.Close, contentDescription = "Dismiss",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    NavigationBar {
                        NavigationBarItem(
                            selected = tab == "Rooms" && openRoom == null,
                            onClick = { tab = "Rooms"; openRoom = null },
                            icon = { Icon(Icons.Default.Groups, "Rooms") },
                            label = { Text("Rooms") }
                        )
                        NavigationBarItem(
                            selected = tab == "Music",
                            onClick = { tab = "Music"; openRoom = null },
                            icon = { Icon(Icons.Default.MusicNote, "Music") },
                            label = { Text("Music") }
                        )
                        NavigationBarItem(
                            selected = tab == "Games",
                            onClick = { tab = "Games"; openRoom = null },
                            icon = { Icon(Icons.Default.SportsEsports, "Games") },
                            label = { Text("Games") }
                        )
                        NavigationBarItem(
                            selected = tab == "You",
                            onClick = { tab = "You"; openRoom = null },
                            icon = { Icon(Icons.Default.Person, "You") },
                            label = { Text("You") }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding).fillMaxSize()) {
            if (showGameOverlay) {
                // Fullscreen game view (restored legacy behavior)
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                isGameMinimized = true
                                tab = "Games"
                                openRoom = null
                            }
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Minimize",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Minimize", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            text = uiState.sharedMediaType.uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = {
                                viewModel.setSharedMedia("none", "", "collaborative", 0)
                                tab = "Games"
                                openRoom = null
                            }
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "End game",
                                tint = Color(0xFFEF5350)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("End", color = Color(0xFFEF5350))
                        }
                    }
                    SharedMediaScreen(
                        uiState = uiState,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
            } else {
                val room = openRoom
                if (room != null) {
                    RoomDetailScreen(viewModel = viewModel, room = room, onBack = { openRoom = null })
                } else when (tab) {
                    "Rooms" -> RoomsTabScreen(viewModel = viewModel, onOpenRoom = { openRoom = it })
                    "Music" -> MusicTabScreen()
                    "Games" -> ArcadeTab(uiState = uiState, viewModel = viewModel)
                    "You" -> YouTabScreen(viewModel = viewModel)
                }
            }
        }
    }
}
