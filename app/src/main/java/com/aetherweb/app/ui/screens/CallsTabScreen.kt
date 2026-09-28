package com.aetherweb.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aetherweb.app.CallManager
import com.aetherweb.app.MeshNetworkManager
import com.aetherweb.app.MeshState
import com.aetherweb.app.MeshNode
import com.aetherweb.app.data.CallLogEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallsTabScreen(
    uiState: MeshState,
    callLogs: List<CallLogEntity>,
    onInitiateCall: (nodeId: String, name: String, isVideo: Boolean) -> Unit
) {
    var showSelectContactSheet by remember { mutableStateOf(false) }
    val timeFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp)
        ) {
            // Connected Peers Quick Bar
            item {
                Text(
                    text = "Connected Peers Nearby (${uiState.connectedNodes.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFF25D366),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            if (uiState.connectedNodes.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Sensors,
                                contentDescription = null,
                                tint = Color(0xFF8696A0),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Scan or join a Wi-Fi hotspot to call nearby friends directly without Internet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(uiState.connectedNodes.filter { it.name.isNotBlank() }) { node ->
                    val displayName = uiState.knownUsers[node.id] ?: node.name
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Color(0xFF25D366).copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = displayName.take(1).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF25D366),
                                    fontSize = 18.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(displayName, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(7.dp).background(Color(0xFF25D366), CircleShape))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Available for HD Voice Call", fontSize = 12.sp, color = Color(0xFF8696A0))
                                }
                            }
                            // Voice Call Icon
                            IconButton(
                                onClick = { onInitiateCall(node.id, displayName, false) },
                                modifier = Modifier.testTag("quick_call_btn_${node.id}")
                            ) {
                                Icon(
                                    Icons.Default.Call,
                                    contentDescription = "Audio Call",
                                    tint = Color(0xFF25D366)
                                )
                            }
                            // Video Call Icon
                            IconButton(
                                onClick = { onInitiateCall(node.id, displayName, true) },
                                modifier = Modifier.testTag("quick_video_btn_${node.id}")
                            ) {
                                Icon(
                                    Icons.Default.Videocam,
                                    contentDescription = "Video Call",
                                    tint = Color(0xFF25D366)
                                )
                            }
                        }
                    }
                }
            }

            // Recent Calls Section
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Recent Calls",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            if (callLogs.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.PhoneCallback,
                            contentDescription = null,
                            tint = Color(0xFF8696A0),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No recent call history",
                            color = Color(0xFF8696A0),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Calls made over offline Wi-Fi will appear here.",
                            color = Color(0xFF8696A0).copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                items(callLogs) { log ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onInitiateCall(log.peerId, log.peerName, log.isVideo)
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Avatar
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = log.peerName.take(1).uppercase().ifBlank { "P" },
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = log.peerName.ifBlank { "Nearby Peer" },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (log.callType == "MISSED") Color(0xFFEA4335) else MaterialTheme.colorScheme.onSurface
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                when (log.callType) {
                                    "INCOMING" -> Icon(
                                        Icons.AutoMirrored.Filled.CallReceived,
                                        contentDescription = "Incoming",
                                        tint = Color(0xFF25D366),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    "OUTGOING" -> Icon(
                                        Icons.AutoMirrored.Filled.CallMade,
                                        contentDescription = "Outgoing",
                                        tint = Color(0xFF25D366),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    else -> Icon(
                                        Icons.AutoMirrored.Filled.CallMissed,
                                        contentDescription = "Missed",
                                        tint = Color(0xFFEA4335),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                val dateStr = timeFormat.format(Date(log.timestamp))
                                val durStr = if (log.durationSeconds > 0) {
                                    " • ${log.durationSeconds / 60}m ${log.durationSeconds % 60}s"
                                } else ""
                                Text(
                                    text = "$dateStr$durStr",
                                    fontSize = 13.sp,
                                    color = Color(0xFF8696A0)
                                )
                            }
                        }

                        // Call back action
                        IconButton(
                            onClick = { onInitiateCall(log.peerId, log.peerName, log.isVideo) }
                        ) {
                            Icon(
                                imageVector = if (log.isVideo) Icons.Default.Videocam else Icons.Default.Call,
                                contentDescription = "Call back",
                                tint = Color(0xFF25D366)
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                }
            }
        }

        // WhatsApp Signature Green Floating Action Button
        FloatingActionButton(
            onClick = { showSelectContactSheet = true },
            containerColor = Color(0xFF25D366),
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("fab_new_call")
        ) {
            Icon(Icons.Default.Call, contentDescription = "New Call", modifier = Modifier.size(24.dp))
        }

        // Select Contact Dialog / Sheet
        if (showSelectContactSheet) {
            AlertDialog(
                onDismissRequest = { showSelectContactSheet = false },
                title = {
                    Text("Select Contact to Call", fontWeight = FontWeight.Bold)
                },
                text = {
                    if (uiState.connectedNodes.isEmpty()) {
                        Text("No peers connected right now. Ensure another phone is on the same hotspot or mesh.", color = Color(0xFF8696A0))
                    } else {
                        Column {
                            uiState.connectedNodes.forEach { peer ->
                                val name = uiState.knownUsers[peer.id] ?: peer.name
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showSelectContactSheet = false
                                            onInitiateCall(peer.id, name, false)
                                        }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(Color(0xFF25D366).copy(alpha = 0.2f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(name.take(1).uppercase(), color = Color(0xFF25D366), fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(name, fontWeight = FontWeight.SemiBold)
                                        Text("Direct Wi-Fi Call", fontSize = 12.sp, color = Color(0xFF8696A0))
                                    }
                                    Icon(Icons.Default.Call, contentDescription = null, tint = Color(0xFF25D366))
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSelectContactSheet = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
