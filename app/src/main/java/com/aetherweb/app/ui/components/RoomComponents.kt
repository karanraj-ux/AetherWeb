package com.aetherweb.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.aetherweb.app.CallManager
import com.aetherweb.app.MeshNode
import com.aetherweb.app.MeshState
import com.aetherweb.app.MeshViewModel
import com.aetherweb.app.QRCodeImage
import com.aetherweb.app.ui.theme.AetherBackground
import com.aetherweb.app.ui.theme.AetherCard
import com.aetherweb.app.ui.theme.AetherSurface
import com.aetherweb.app.ui.theme.EmberOrange
import com.aetherweb.app.ui.theme.FlameAmber
import com.aetherweb.app.ui.theme.NostrPurple
import com.aetherweb.app.ui.theme.SosRed
import com.aetherweb.app.ui.theme.flameGradient

// ---------------------------------------------------------------------------
// Room models — shared across the redesign.
// ---------------------------------------------------------------------------

enum class RoomKind { PUBLIC_MESH, BURNER, NEARBY_HOTSPOT }

data class AetherRoom(
    val id: String,
    val name: String,
    val kind: RoomKind
)

// ---------------------------------------------------------------------------
// LinkIndicator — ember / flame / offline pill derived from MeshState.
// ---------------------------------------------------------------------------

@Composable
fun LinkIndicator(uiState: MeshState, onClick: () -> Unit) {
    val (dotColor, label) = when {
        uiState.isHotspotActive || uiState.isWifiConnected || uiState.isWebServerRunning ->
            FlameAmber to "Room live"
        uiState.connectedNodes.isNotEmpty() || uiState.isScanning ->
            EmberOrange to "Campfire"
        else -> Color.Gray to "Offline"
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(AetherCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(label, color = dotColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------------------------------------------------------------------
// RoomCard — 20dp card with per-kind ember/flame accent.
// ---------------------------------------------------------------------------

@Composable
fun RoomCard(room: AetherRoom, memberCount: Int, subtitle: String, onClick: () -> Unit) {
    val accent = when (room.kind) {
        RoomKind.PUBLIC_MESH -> FlameAmber
        RoomKind.BURNER -> EmberOrange
        RoomKind.NEARBY_HOTSPOT -> NostrPurple
    }
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = AetherCard),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(78.dp)
                    .background(accent)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text(
                    room.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Group,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("$memberCount in room", color = Color(0xFF9AA0B4), fontSize = 12.sp)
                }
                if (subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(subtitle, color = Color(0xFF6B7280), fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// SosDialog — confirm / stand-down logic, restyled dark with SosRed.
// ---------------------------------------------------------------------------

@Composable
fun SosDialog(uiState: MeshState, viewModel: MeshViewModel, onDismiss: () -> Unit) {
    var reasonInput by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AetherSurface,
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = "SOS Warning",
                tint = SosRed,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                if (uiState.isEmergencyMode) "Active SOS Beacon" else "Broadcast SOS Beacon",
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Column {
                if (uiState.isEmergencyMode) {
                    Text(
                        "Your Emergency Beacon is currently broadcasting coordinates and distress signal to all nearby mesh peers.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFD7DAE2)
                    )
                } else {
                    Text(
                        "This sends an immediate high-priority distress broadcast with your peer ID and coordinates to all discovered Wi-Fi & BLE mesh nodes in range.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFD7DAE2)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = reasonInput,
                        onValueChange = { reasonInput = it },
                        label = { Text("Emergency Note (optional)") },
                        placeholder = { Text("e.g. Need medical assistance / lost") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = SosRed,
                            focusedBorderColor = SosRed,
                            unfocusedBorderColor = Color(0xFF3A3A48),
                            focusedLabelColor = SosRed,
                            unfocusedLabelColor = Color(0xFF9AA0B4),
                            focusedPlaceholderColor = Color(0xFF6B7280),
                            unfocusedPlaceholderColor = Color(0xFF6B7280)
                        )
                    )
                }
            }
        },
        confirmButton = {
            if (uiState.isEmergencyMode) {
                Button(
                    onClick = {
                        viewModel.cancelSOS()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AetherCard,
                        contentColor = Color.White
                    )
                ) {
                    Text("Cancel / Stand Down", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = {
                        val reason = if (reasonInput.isNotBlank()) reasonInput.trim()
                        else "Immediate Assistance Required!"
                        viewModel.triggerSOS(customMessage = reason)
                        reasonInput = ""
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SosRed,
                        contentColor = Color.White
                    )
                ) {
                    Text("BROADCAST SOS", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss", color = Color(0xFF9AA0B4))
            }
        }
    )
}

// ---------------------------------------------------------------------------
// CallPickerDialog — peer list + CallManager.initiateCall logic.
// ---------------------------------------------------------------------------

@Composable
fun CallPickerDialog(uiState: MeshState, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AetherSurface,
        title = {
            Text("Select Contact to Call", fontWeight = FontWeight.Bold, color = Color.White)
        },
        text = {
            Column {
                if (uiState.connectedNodes.isEmpty()) {
                    Text(
                        "No peers in range. Start a Room or join a nearby one to call.",
                        color = Color(0xFF9AA0B4),
                        fontSize = 13.sp
                    )
                } else {
                    uiState.connectedNodes.forEach { node ->
                        val displayName = uiState.knownUsers[node.id] ?: node.name
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDismiss()
                                    val peerIp = if (uiState.isHotspotActive) "192.168.49.1" else ""
                                    CallManager.initiateCall(node.id, displayName, peerIp) { payload ->
                                        com.aetherweb.app.MeshNetworkManager.meshRouter.routeLocalMessage(payload)
                                        com.aetherweb.app.MeshNetworkManager.webServerManager?.broadcastMessage(
                                            payload,
                                            com.aetherweb.app.MeshNetworkManager.localNodeId
                                        )
                                    }
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(EmberOrange.copy(alpha = 0.18f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    displayName.take(1).uppercase(),
                                    color = EmberOrange,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(displayName, fontWeight = FontWeight.SemiBold, color = Color.White)
                                Text("Online via mesh", fontSize = 12.sp, color = Color(0xFF9AA0B4))
                            }
                            Icon(Icons.Default.Call, contentDescription = "Call", tint = EmberOrange)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF9AA0B4))
            }
        }
    )
}

// ---------------------------------------------------------------------------
// QrShareSheet — Start Room button + two QR tabs + Stop Room.
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrShareSheet(uiState: MeshState, viewModel: MeshViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableIntStateOf(0) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AetherSurface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Share Room", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Friends don't need the app — they scan with their camera and join from the browser.",
                color = Color(0xFF9AA0B4),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (!uiState.isHotspotActive) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(flameGradient())
                        .clickable { viewModel.toggleHotspot(isPrivate = false) }
                        .padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Wifi, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Room", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            } else {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = AetherCard,
                    contentColor = Color.White
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Mesh Wi-Fi QR", fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.Default.Wifi, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("WebChat QR", fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.Default.Language, contentDescription = null) }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (selectedTab == 0) {
                        val wifiQr = "WIFI:T:WPA;S:${uiState.hotspotSsid};P:${uiState.hotspotPassword};;"
                        QRCodeImage(
                            content = wifiQr,
                            modifier = Modifier.size(220.dp).padding(12.dp)
                        )
                    } else {
                        val url = "http://${uiState.hotspotIp}:8080"
                        QRCodeImage(
                            content = url,
                            modifier = Modifier.size(220.dp).padding(12.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                if (selectedTab == 0) {
                    Text(
                        "Network: ${uiState.hotspotSsid}",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text("Password: ${uiState.hotspotPassword}", color = Color(0xFF9AA0B4), fontSize = 13.sp)
                } else {
                    Text("http://${uiState.hotspotIp}:8080", color = Color(0xFF9AA0B4), fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.toggleHotspot() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SosRed,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Stop Room", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// RosterSheet — member list with avatar circles, plus a "you" row.
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
/**
 * Peers excluding this device itself. Some chipsets deliver our own BLE
 * advertisement back to our scan callback, which would otherwise double-count
 * us in every "N in room" label.
 */
@Composable
fun MeshState.visiblePeers(): List<MeshNode> {
    val context = LocalContext.current
    val selfAddr = remember {
        try {
            val bm = context.getSystemService(android.content.Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
            bm?.adapter?.address
        } catch (e: SecurityException) { null }
    }
    return remember(connectedNodes, selfAddr) {
        if (selfAddr.isNullOrBlank()) connectedNodes
        else connectedNodes.filter { it.id != selfAddr }
    }
}

@Composable
fun RosterSheet(uiState: MeshState, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val members = uiState.visiblePeers().filter { it.name.isNotBlank() }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AetherSurface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text("Room Roster", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("${members.size + 1} in room", color = Color(0xFF9AA0B4), fontSize = 13.sp)
            Spacer(modifier = Modifier.height(12.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item {
                    RosterRow(
                        name = uiState.localUserName,
                        handle = uiState.localUsernameId,
                        isYou = true
                    )
                }
                items(members, key = { it.id }) { node ->
                    val displayName = uiState.knownUsers[node.id] ?: node.name
                    val handle = uiState.knownUserIds[node.id]
                        ?: "@${node.name.lowercase().replace(" ", "")}"
                    RosterRow(name = displayName, handle = handle, isYou = false)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RosterRow(name: String, handle: String, isYou: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(avatarColorFor(name)),
            contentAlignment = Alignment.Center
        ) {
            Text(name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name.ifBlank { "Unknown" },
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                if (isYou) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "you",
                        color = AetherBackground,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(EmberOrange)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Text(handle, color = Color(0xFF9AA0B4), fontSize = 12.sp)
        }
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(EmberOrange)
        )
    }
}

// ---------------------------------------------------------------------------
// AvatarStack — overlapping initial circles, max 4 + "+N".
// ---------------------------------------------------------------------------

@Composable
fun AvatarStack(names: List<String>, modifier: Modifier = Modifier) {
    val shown = names.take(4)
    val extra = (names.size - shown.size).coerceAtLeast(0)
    val circles = shown.size + if (extra > 0) 1 else 0
    if (circles == 0) return
    val step = 22
    Box(
        modifier = modifier
            .width((circles * step + 10).dp)
            .height(32.dp)
    ) {
        shown.forEachIndexed { i, name ->
            Box(
                modifier = Modifier
                    .offset(x = (i * step).dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(avatarColorFor(name))
                    .border(2.dp, AetherBackground, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    name.take(1).uppercase(),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (extra > 0) {
            Box(
                modifier = Modifier
                    .offset(x = (shown.size * step).dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(AetherCard)
                    .border(2.dp, AetherBackground, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("+$extra", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun avatarColorFor(name: String): Color {
    val palette = listOf(
        EmberOrange,
        FlameAmber,
        NostrPurple,
        Color(0xFF3B82F6),
        Color(0xFF10B981),
        Color(0xFFEC4899)
    )
    val idx = (name.hashCode() and Int.MAX_VALUE) % palette.size
    return palette[idx]
}
