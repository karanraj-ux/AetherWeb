package com.aetherweb.app.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aetherweb.app.MeshState
import com.aetherweb.app.MeshViewModel
import com.aetherweb.app.ui.components.AetherRoom
import com.aetherweb.app.ui.components.LinkIndicator
import com.aetherweb.app.ui.components.QrShareSheet
import com.aetherweb.app.ui.components.RoomCard
import com.aetherweb.app.ui.components.RoomKind
import com.aetherweb.app.ui.components.RosterSheet
import com.aetherweb.app.ui.theme.AetherBackground
import com.aetherweb.app.ui.theme.EmberOrange
import com.aetherweb.app.ui.theme.flameGradient

// ---------------------------------------------------------------------------
// RoomsTabScreen — the Rooms home tab.
// Sections: top app bar, Start-a-Room hero, This device, Nearby, Join with code.
// ---------------------------------------------------------------------------

@Composable
fun RoomsTabScreen(
    viewModel: MeshViewModel,
    onOpenRoom: (AetherRoom) -> Unit
) {
    // Explicit MeshState type for clarity.
    val uiState: MeshState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showQrSheet by remember { mutableStateOf(false) }
    var showRoster by remember { mutableStateOf(false) }
    var joinCode by remember { mutableStateOf("") }

    val publicRoom = remember { AetherRoom(id = "public", name = "Public Mesh", kind = RoomKind.PUBLIC_MESH) }

    if (showQrSheet) {
        QrShareSheet(uiState = uiState, viewModel = viewModel, onDismiss = { showQrSheet = false })
    }
    if (showRoster) {
        RosterSheet(uiState = uiState, onDismiss = { showRoster = false })
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AetherBackground),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Top app bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Rooms",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                )
                LinkIndicator(uiState = uiState, onClick = { showRoster = true })
            }
        }

        // Hero card — Start a Room
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(flameGradient())
                    .padding(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Whatshot,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Start a Room",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Friends join from a QR code — no app install needed.",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = { showQrSheet = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    )
                ) {
                    Text(text = "Start a Room", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }

        // This device
        item {
            Column {
                RoomsSectionTitle("This device")
                Spacer(modifier = Modifier.height(8.dp))
                RoomCard(
                    room = publicRoom,
                    memberCount = uiState.connectedNodes.size + 1,
                    subtitle = "Campfire chat with everyone nearby",
                    onClick = { onOpenRoom(publicRoom) }
                )
                if (uiState.isBurnerRoomActive) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val secs = uiState.burnerRoomCountdown.coerceAtLeast(0)
                    val mmss = "%d:%02d".format(secs / 60, secs % 60)
                    val burnerRoom = remember(uiState.burnerRoomId) {
                        AetherRoom(
                            id = uiState.burnerRoomId.ifBlank { "burner" },
                            name = "Burner Room",
                            kind = RoomKind.BURNER
                        )
                    }
                    RoomCard(
                        room = burnerRoom,
                        memberCount = uiState.connectedNodes.size + 1,
                        subtitle = "Vanishes in $mmss",
                        onClick = { onOpenRoom(burnerRoom) }
                    )
                }
            }
        }

        // Nearby
        item {
            Column {
                RoomsSectionTitle("Nearby")
                Spacer(modifier = Modifier.height(8.dp))
                val ssid = uiState.discoveredHostSsid
                if (ssid != null && !uiState.isHotspotActive && !uiState.isWifiConnected) {
                    val nearbyRoom = remember(ssid) {
                        AetherRoom(id = "nearby-$ssid", name = ssid, kind = RoomKind.NEARBY_HOTSPOT)
                    }
                    RoomCard(
                        room = nearbyRoom,
                        memberCount = 0,
                        subtitle = "High-speed Room mesh found",
                        onClick = { joinDiscoveredHotspot(context, viewModel) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { joinDiscoveredHotspot(context, viewModel) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(text = "Join", fontWeight = FontWeight.Bold)
                    }
                } else {
                    // Ember empty state
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Whatshot,
                            contentDescription = null,
                            tint = EmberOrange.copy(alpha = 0.45f),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No rooms nearby. Start the fire.",
                            color = Color(0xFF9AA0B4),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Join with code
        item {
            Column {
                RoomsSectionTitle("Join with code")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = joinCode,
                    onValueChange = { joinCode = it },
                    label = { Text("Room code or SSID") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { joinDiscoveredHotspot(context, viewModel) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(text = "Join", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun RoomsSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = Color(0xFF9AA0B4),
        fontWeight = FontWeight.SemiBold
    )
}

/** Same join path MainChatScreen uses for a discovered host hotspot. */
private fun joinDiscoveredHotspot(context: Context, viewModel: MeshViewModel) {
    viewModel.connectToDiscoveredHotspot()
    Toast.makeText(context, "Requesting connection...", Toast.LENGTH_SHORT).show()
}
