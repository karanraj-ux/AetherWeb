package com.aetherweb.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aetherweb.app.CallManager
import com.aetherweb.app.InternetRelayManager
import com.aetherweb.app.MeshNetworkManager
import com.aetherweb.app.MeshState
import com.aetherweb.app.MeshViewModel
import com.aetherweb.app.ui.theme.AetherBackground
import com.aetherweb.app.ui.theme.AetherCard
import com.aetherweb.app.ui.theme.EmberOrange
import com.aetherweb.app.ui.theme.NostrPurple

/**
 * "You" tab: identity, recent calls, internet bridge status, radios & battery,
 * and about. Dark campfire aesthetic matching the Rooms redesign.
 */
@Composable
fun YouTabScreen(viewModel: MeshViewModel) {
    val uiState: MeshState by viewModel.uiState.collectAsStateWithLifecycle()
    val callLogs by viewModel.callLogs.collectAsStateWithLifecycle()
    val relayState by InternetRelayManager.relayState.collectAsStateWithLifecycle()
    var showEditDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AetherBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "You",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        // a) Identity
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AetherCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(EmberOrange.copy(alpha = 0.22f))
                            .border(2.dp, EmberOrange, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = uiState.localUserName.firstOrNull()?.uppercase() ?: "?",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmberOrange
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = uiState.localUserName,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = uiState.localUsernameId,
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 13.sp
                        )
                        if (uiState.isGhostMode) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "\uD83D\uDC7B Ghost mode on — history stays in RAM",
                                color = Color(0xFFB388FF),
                                fontSize = 12.sp
                            )
                        }
                    }
                    TextButton(onClick = { showEditDialog = true }) {
                        Text("Edit", color = EmberOrange, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // b) Recents (first 8 logs, fixed height — LazyColumn inside LazyColumn)
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AetherCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Recents",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        CallsTabScreen(
                            uiState = uiState,
                            callLogs = callLogs.take(8),
                            onInitiateCall = { peerId, peerName, isVideo ->
                                val peerIp = if (uiState.isHotspotActive) "192.168.49.1" else ""
                                CallManager.initiateCall(peerId, peerName, peerIp) { payload ->
                                    MeshNetworkManager.meshRouter.routeLocalMessage(payload)
                                    MeshNetworkManager.webServerManager?.broadcastMessage(
                                        payload,
                                        MeshNetworkManager.localNodeId
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }

        // c) Bridge — Nostr / internet relay status (read-only; no toggle API exists)
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AetherCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (relayState.isRelayConnected) Icons.Filled.Cloud else Icons.Filled.CloudOff,
                            contentDescription = null,
                            tint = NostrPurple,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Bridge",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    YouSettingRow(
                        icon = Icons.Filled.Cloud,
                        iconTint = NostrPurple,
                        title = "Internet",
                        subtitle = if (relayState.isInternetAvailable) "Available" else "No internet — mesh only",
                        trailing = {
                            StatusPill(
                                text = if (relayState.isInternetAvailable) "Up" else "Down",
                                active = relayState.isInternetAvailable,
                                activeColor = NostrPurple
                            )
                        }
                    )
                    YouSettingRow(
                        icon = Icons.Filled.CloudOff,
                        iconTint = NostrPurple,
                        title = "Nostr relay",
                        subtitle = if (relayState.isRelayConnected) "Connected" else "Idle",
                        trailing = {
                            StatusPill(
                                text = if (relayState.isRelayConnected) "Live" else "Idle",
                                active = relayState.isRelayConnected,
                                activeColor = NostrPurple
                            )
                        }
                    )
                    if (relayState.isRelayConnected && relayState.activeRelayUrl.isNotBlank()) {
                        Text(
                            text = relayState.activeRelayUrl,
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 36.dp)
                        )
                    }
                    if (relayState.pendingMuleCount > 0) {
                        Text(
                            text = "${relayState.pendingMuleCount} message(s) waiting for a path out",
                            color = EmberOrange,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 36.dp)
                        )
                    }
                    Text(
                        text = "The bridge wakes on its own when any path reaches the internet.",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // d) Radios & battery
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AetherCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Radios & battery",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    YouSettingRow(
                        icon = Icons.Filled.Bluetooth,
                        iconTint = EmberOrange,
                        title = "BLE duty cycle",
                        subtitle = uiState.bleDutyCycleLabel,
                        trailing = { StatusPill(text = "Auto", active = true) }
                    )
                    YouSettingRow(
                        icon = Icons.Filled.Speed,
                        iconTint = EmberOrange,
                        title = "Data saver",
                        subtitle = "Throttles + compresses media for slow links",
                        trailing = {
                            Switch(
                                checked = uiState.isDataSaverEnabled,
                                onCheckedChange = { viewModel.toggleDataSaver() }
                            )
                        }
                    )
                    YouSettingRow(
                        icon = Icons.Filled.Wifi,
                        iconTint = EmberOrange,
                        title = "Hotspot",
                        subtitle = if (uiState.isHotspotActive) "On — friends can join over Wi-Fi" else "Off",
                        trailing = {
                            Switch(
                                checked = uiState.isHotspotActive,
                                onCheckedChange = { viewModel.toggleHotspot() }
                            )
                        }
                    )
                }
            }
        }

        // e) About
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AetherCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(16.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "AetherWeb v1.1.0",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Private by design — no accounts, no servers.",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp
                        )
                        Text(
                            text = "SOS location is ephemeral, room-local, never uploaded.",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }

    if (showEditDialog) {
        EditIdentityDialog(
            uiState = uiState,
            onDismiss = { showEditDialog = false },
            onSave = { name, ghost ->
                viewModel.updateUserName(newName = name)
                viewModel.setGhostMode(ghost)
                showEditDialog = false
            }
        )
    }
}

@Composable
private fun EditIdentityDialog(
    uiState: MeshState,
    onDismiss: () -> Unit,
    onSave: (name: String, ghost: Boolean) -> Unit
) {
    var nameInput by remember(uiState.localUserName) { mutableStateOf(uiState.localUserName) }
    var ghostInput by remember(uiState.isGhostMode) { mutableStateOf(uiState.isGhostMode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit identity", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Fire name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { ghostInput = !ghostInput }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Ghost mode", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(
                            "RAM-only history. Vanishes on app close.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = ghostInput, onCheckedChange = { ghostInput = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val finalName = nameInput.trim().ifBlank { uiState.localUserName }
                    onSave(finalName, ghostInput)
                }
            ) { Text("Save", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun YouSettingRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp
            )
        }
        trailing()
    }
}

@Composable
private fun StatusPill(
    text: String,
    active: Boolean,
    activeColor: Color = EmberOrange
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (active) activeColor.copy(alpha = 0.2f)
                else Color.White.copy(alpha = 0.08f)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (active) activeColor else Color.White.copy(alpha = 0.55f)
        )
    }
}
