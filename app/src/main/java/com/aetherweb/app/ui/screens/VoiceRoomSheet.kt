package com.aetherweb.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.aetherweb.app.VoiceMember
import com.aetherweb.app.VoiceRoomClient
import com.aetherweb.app.VoiceRoomManager

/**
 * Voice Room Phase 2 — Discord-style voice dashboard bottom sheet.
 * Opened from the voice button in the chat room TopAppBar (app) and mirrored
 * by the voice panel in the web portal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceRoomSheet(
    onDismiss: () -> Unit,
    canHost: Boolean,
    hostName: String,
    peerId: String
) {
    val context = LocalContext.current
    val roomState by VoiceRoomManager.state.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Voice Room Phase 4: runtime mic permission (same pattern as CallScreen).
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) VoiceRoomManager.setSelfMic(true)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1F2C34),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .background(Color(0xFF555555), RoundedCornerShape(2.dp))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Voice Room",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE9EDEF)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${roomState.memberCount} in room",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF8696A0)
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFFE9EDEF))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (!roomState.active) {
                Text(
                    text = "A live voice channel for everyone in this room — app and web guests together.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF8696A0)
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (canHost) {
                    Button(
                        onClick = {
                            // Phase 4: host starts the room AND joins its own SFU as a client,
                            // so the forwarding path treats everyone uniformly.
                            VoiceRoomManager.openAsHost("host-self", hostName)
                            val client = VoiceRoomClient(context)
                            client.connect("127.0.0.1", "host-self", hostName, "", "host")
                            VoiceRoomManager.attachAudioClient(client)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Voice Room", color = Color.White)
                    }
                } else {
                    // Phase 4: app peer joins the host's room via the hotspot gateway.
                    val joined = roomState.members.values.any { it.isSelf }
                    if (!joined) {
                        Button(
                            onClick = {
                                val client = VoiceRoomClient(context)
                                client.connect(
                                    VoiceRoomClient.guessHostIp(), peerId, hostName, "", "member"
                                )
                                VoiceRoomManager.attachAudioClient(client)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Join Voice Room", color = Color.White)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Text(
                        text = if (joined) "Connected to the voice room."
                        else "Voice room is hosted by the hotspot phone. Join when the host starts it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF8696A0)
                    )
                }
            } else {
                val self = roomState.members.values.firstOrNull { it.isSelf }

                // Self controls: mic toggle + deafen.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val micOn = self?.micOn == true
                    val locked = self?.mutedByHost == true
                    Button(
                        onClick = {
                            // Phase 4: request runtime mic permission like calls do.
                            if (!micOn && ContextCompat.checkSelfPermission(
                                    context, android.Manifest.permission.RECORD_AUDIO
                                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                            } else {
                                VoiceRoomManager.setSelfMic(!micOn)
                            }
                        },
                        enabled = !locked,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (micOn) Color(0xFF25D366) else Color(0xFF374045)
                        )
                    ) {
                        Icon(
                            if (micOn) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = "Toggle mic",
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (locked) "Muted by host" else if (micOn) "Mic on" else "Mic off",
                            color = Color.White
                        )
                    }
                    IconButton(onClick = { VoiceRoomManager.setDeafen(!roomState.deafen) }) {
                        Icon(
                            if (roomState.deafen) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = "Deafen",
                            tint = if (roomState.deafen) Color(0xFFEF5350) else Color(0xFFE9EDEF)
                        )
                    }
                    if (roomState.isHost) {
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = { VoiceRoomManager.hostMuteAll() }) {
                            Text("Mute all", color = Color(0xFFFFB74D))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(roomState.members.values.toList()) { member ->
                        VoiceMemberRow(member = member, isHost = roomState.isHost)
                    }
                }

                if (roomState.isHost) {
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(onClick = { VoiceRoomManager.closeRoom() }) {
                        Text("End voice room", color = Color(0xFFEF5350))
                    }
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(onClick = { VoiceRoomManager.closeRoom() }) {
                        Text("Leave voice room", color = Color(0xFFEF5350))
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun VoiceMemberRow(member: VoiceMember, isHost: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(
                width = if (member.speaking) 2.dp else 0.dp,
                color = if (member.speaking) Color(0xFF25D366) else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .background(Color(0xFF182229), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFF005D4B)),
            contentAlignment = Alignment.Center
        ) {
            if (member.emoji.isNotBlank()) {
                Text(text = member.emoji, style = MaterialTheme.typography.titleMedium)
            } else {
                Icon(Icons.Default.Person, contentDescription = null, tint = Color.White)
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = member.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFE9EDEF)
            )
            Text(
                text = buildString {
                    append(
                        when (member.kind) {
                            "host" -> "HOST"
                            "temp" -> "WEB GUEST"
                            else -> "MEMBER"
                        }
                    )
                    if (member.mutedByHost) append(" • muted by host")
                    if (member.mutedByMe) append(" • muted for you")
                },
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF8696A0)
            )
        }
        Icon(
            if (member.micOn) Icons.Default.Mic else Icons.Default.MicOff,
            contentDescription = null,
            tint = if (member.micOn) Color(0xFF25D366) else Color(0xFF8696A0),
            modifier = Modifier.size(20.dp)
        )
        if (!member.isSelf) {
            IconButton(onClick = { VoiceRoomManager.setMutedByMe(member.id, !member.mutedByMe) }) {
                Icon(
                    if (member.mutedByMe) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = "Mute for me",
                    tint = if (member.mutedByMe) Color(0xFFEF5350) else Color(0xFFE9EDEF)
                )
            }
        }
        if (isHost && !member.isSelf && member.kind != "host") {
            IconButton(onClick = { VoiceRoomManager.hostSetMute(member.id, !member.mutedByHost) }) {
                Icon(
                    if (member.mutedByHost) Icons.Default.Mic else Icons.Default.MicOff,
                    contentDescription = "Host mute",
                    tint = if (member.mutedByHost) Color(0xFFFFB74D) else Color(0xFFE9EDEF)
                )
            }
            IconButton(onClick = { VoiceRoomManager.kickMember(member.id) }) {
                Icon(Icons.Default.Close, contentDescription = "Kick", tint = Color(0xFFEF5350))
            }
        }
    }
}
