package com.aetherweb.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AetherRoot(viewModel: MeshViewModel) {
    var tab by remember { mutableStateOf("Rooms") }
    var openRoom by remember { mutableStateOf<AetherRoom?>(null) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val musicState by MeshMusicManager.state.collectAsStateWithLifecycle()

    BackHandler(enabled = openRoom != null) { openRoom = null }

    Scaffold(
        bottomBar = {
            Column {
                // Persistent mini-player: the silent-disco heartbeat, always one tap away.
                val track = musicState.currentTrack
                if (track != null) {
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
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding).fillMaxSize()) {
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
