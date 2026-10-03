package com.aetherweb.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aetherweb.app.MeshMusicManager
import com.aetherweb.app.MusicTrack

@Composable
fun MusicTabScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val playerState by MeshMusicManager.state.collectAsState()

    var showAddStreamDialog by remember { mutableStateOf(false) }
    var streamTitleInput by remember { mutableStateOf("") }
    var streamUrlInput by remember { mutableStateOf("") }

    // Multi-file picker for local MP3/audio files
    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        uris.forEach { uri ->
            MeshMusicManager.addTrackFromUri(context, uri, null)
        }
    }

    // Vinyl rotation animation
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl_spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin_angle"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // TOP ACTION BAR
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Mesh Jukebox",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (playerState.isPartyModeHost) "📻 DJ Mode (Broadcasting)" else if (playerState.isPartyModeListener) "🎧 Party Listener (Synced)" else "Local & Mesh Player",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (playerState.isPartyModeHost || playerState.isPartyModeListener) Color(0xFF25D366) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = { audioPicker.launch("audio/*") }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Files", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { showAddStreamDialog = true }) {
                    Icon(Icons.Default.Radio, contentDescription = "Add Stream URL", tint = MaterialTheme.colorScheme.secondary)
                }
                IconButton(onClick = { MeshMusicManager.scanLocalCacheTracks(context) }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Rescan", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Playback error banner (streams fail silently otherwise)
        val playbackError = playerState.lastError
        if (playbackError != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF4A1D1D)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning, contentDescription = null,
                        tint = Color(0xFFFF8A80), modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        playbackError, color = Color(0xFFFFCDD2),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { MeshMusicManager.clearError() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color(0xFFFFCDD2))
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // PARTY MODE / SILENT DISCO BAR
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (playerState.isPartyModeHost) Color(0xFF005D4B) else MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = if (playerState.isPartyModeHost) Icons.Default.WifiTethering else Icons.Default.Headphones,
                        contentDescription = "Party Mode",
                        tint = if (playerState.isPartyModeHost) Color(0xFF25D366) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (playerState.isPartyModeHost) "DJ Broadcast Active" else "Party Mode Sync",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (playerState.isPartyModeHost) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (playerState.isPartyModeHost) "Nearby mesh peers play in sync" else "Sync audio with DJ",
                            fontSize = 11.sp,
                            color = if (playerState.isPartyModeHost) Color(0xFFE0E0E0) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { MeshMusicManager.togglePartyModeHost() },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (playerState.isPartyModeHost) Color(0xFF25D366) else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(if (playerState.isPartyModeHost) "DJ: ON" else "Be DJ", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Switch(
                        checked = playerState.isPartyModeListener,
                        onCheckedChange = { MeshMusicManager.togglePartyModeListener() },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF25D366), checkedTrackColor = Color(0xFF005D4B))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // NOW PLAYING HERO CARD (VINYL DISC & CONTROLS)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Vinyl record / visualizer
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .rotate(if (playerState.isPlaying) rotation else 0f)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFF333333), Color(0xFF111111), Color(0xFF000000))
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = if (playerState.isPlaying) Color(0xFF25D366) else Color(0xFF4FC3F7),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (playerState.isPlaying) Icons.Default.GraphicEq else Icons.Default.Audiotrack,
                            contentDescription = "Track Art",
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Title & Artist
                Text(
                    text = playerState.currentTrack?.title ?: "No track selected",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = playerState.currentTrack?.artist ?: "Tap a song below to start",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                // PROGRESS SLIDER & TIMESTAMPS
                val pos = playerState.currentPositionMs.toFloat()
                val dur = playerState.durationMs.toFloat().coerceAtLeast(1f)
                val sliderValue = (pos / dur).coerceIn(0f, 1f)

                if (playerState.currentTrack?.isStream == true) {
                    val streamLive = playerState.isPlaying
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(8.dp).background(if (streamLive) Color(0xFF25D366) else Color.Gray, CircleShape))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (streamLive) "LIVE RADIO STREAM" else "STREAM — TAP PLAY",
                            color = if (streamLive) Color(0xFF25D366) else Color.Gray,
                            fontWeight = FontWeight.Bold, fontSize = 11.sp
                        )
                    }
                } else {
                    Slider(
                        value = sliderValue,
                        onValueChange = { frac ->
                            val targetMs = (frac * dur).toLong()
                            MeshMusicManager.seekTo(targetMs)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF25D366),
                            activeTrackColor = Color(0xFF25D366),
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatDuration(playerState.currentPositionMs), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatDuration(playerState.durationMs), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // CONTROLS ROW
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { MeshMusicManager.toggleShuffle() }) {
                        Icon(
                            Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (playerState.isShuffle) Color(0xFF25D366) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = { MeshMusicManager.skipPrevious() }) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF25D366))
                            .clickable { MeshMusicManager.togglePlayPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    IconButton(onClick = { MeshMusicManager.skipNext() }) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    IconButton(onClick = { MeshMusicManager.toggleRepeat() }) {
                        Icon(
                            Icons.Default.Repeat,
                            contentDescription = "Repeat",
                            tint = if (playerState.isRepeat) Color(0xFF25D366) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // PLAYLIST HEADER
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Playlist (${playerState.playlist.size} tracks)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Tap to Play",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // TRACK LIST
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(playerState.playlist) { idx, track ->
                val isSelected = track.id == playerState.currentTrack?.id
                TrackRowItem(
                    track = track,
                    index = idx + 1,
                    isSelected = isSelected,
                    isPlaying = isSelected && playerState.isPlaying,
                    onClick = { MeshMusicManager.playTrack(track) }
                )
            }
        }
    }

    // DIALOG: ADD CUSTOM STREAM URL
    if (showAddStreamDialog) {
        AlertDialog(
            onDismissRequest = { showAddStreamDialog = false },
            title = { Text("Add Audio Stream URL") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Paste an Icecast, Shoutcast, or direct MP3/M4A online stream URL to play or broadcast:", fontSize = 12.sp)
                    OutlinedTextField(
                        value = streamTitleInput,
                        onValueChange = { streamTitleInput = it },
                        label = { Text("Stream Title") },
                        placeholder = { Text("e.g. Synthwave FM") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = streamUrlInput,
                        onValueChange = { streamUrlInput = it },
                        label = { Text("Stream URL (http/https)") },
                        placeholder = { Text("https://example.com/radio.mp3") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Text("Presets:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = {
                            streamTitleInput = "Lofi Beats"
                            streamUrlInput = "https://stream.zeno.fm/f3wvbbqmdg8uv"
                        }) {
                            Text("Lofi", fontSize = 11.sp)
                        }
                        OutlinedButton(onClick = {
                            streamTitleInput = "Nightride FM"
                            streamUrlInput = "https://stream.nightride.fm/nightride.m4a"
                        }) {
                            Text("Nightride", fontSize = 11.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (streamUrlInput.isNotBlank()) {
                            MeshMusicManager.addStreamUrl(streamTitleInput, streamUrlInput)
                            showAddStreamDialog = false
                            streamTitleInput = ""
                            streamUrlInput = ""
                        }
                    }
                ) {
                    Text("Add & Play")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddStreamDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun TrackRowItem(
    track: MusicTrack,
    index: Int,
    isSelected: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF1F2C34) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(
                        color = if (isSelected) Color(0xFF25D366) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isPlaying) {
                    Icon(Icons.Default.GraphicEq, contentDescription = "Playing", tint = Color.Black, modifier = Modifier.size(18.dp))
                } else {
                    Text(
                        text = index.toString(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 14.sp,
                    color = if (isSelected) Color(0xFF25D366) else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (track.isStream) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF005D4B), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("STREAM", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF25D366))
                }
            } else if (track.durationMs > 0) {
                Text(
                    text = formatDuration(track.durationMs),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format("%d:%02d", min, sec)
}
