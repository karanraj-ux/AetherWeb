package com.aetherweb.app.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aetherweb.app.CallManager
import com.aetherweb.app.ChatMessage
import com.aetherweb.app.MeshNetworkManager
import com.aetherweb.app.MeshState
import com.aetherweb.app.MeshViewModel
import com.aetherweb.app.ui.components.AetherRoom
import com.aetherweb.app.ui.components.AvatarStack
import com.aetherweb.app.ui.components.CallPickerDialog
import com.aetherweb.app.ui.components.LinkIndicator
import com.aetherweb.app.ui.components.QrShareSheet
import com.aetherweb.app.ui.components.RosterSheet
import com.aetherweb.app.ui.components.SosDialog
import com.aetherweb.app.ui.components.visiblePeers
import com.aetherweb.app.ui.theme.AetherBackground
import com.aetherweb.app.ui.theme.FlameAmber
import com.aetherweb.app.ui.theme.SosRed

// ---------------------------------------------------------------------------
// RoomDetailScreen — one Room: Chat | Reels | Games tabs under a room header.
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomDetailScreen(
    viewModel: MeshViewModel,
    room: AetherRoom,
    onBack: () -> Unit
) {
    val uiState: MeshState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedRoomTab by remember { mutableIntStateOf(0) } // 0 Chat, 1 Reels, 2 Games
    var showQr by remember { mutableStateOf(false) }
    var showCallPicker by remember { mutableStateOf(false) }
    var showSos by remember { mutableStateOf(false) }
    var showRoster by remember { mutableStateOf(false) }
    // Mirrors MainChatScreen: tapping a user header opens the contact action dialog;
    // "Direct Chat" routes through viewModel.setDmPeer and ChatTab renders the DM itself.
    var selectedUser by remember { mutableStateOf<ChatMessage?>(null) }

    if (showQr) {
        QrShareSheet(uiState = uiState, viewModel = viewModel, onDismiss = { showQr = false })
    }
    if (showCallPicker) {
        CallPickerDialog(uiState = uiState, onDismiss = { showCallPicker = false })
    }
    if (showSos) {
        SosDialog(uiState = uiState, viewModel = viewModel, onDismiss = { showSos = false })
    }
    if (showRoster) {
        RosterSheet(uiState = uiState, onDismiss = { showRoster = false })
    }
    if (selectedUser != null) {
        RoomContactActionDialog(
            userMsg = selectedUser!!,
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = { selectedUser = null }
        )
    }

    Scaffold(
        containerColor = AetherBackground,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = room.name,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            LinkIndicator(uiState = uiState, onClick = { showRoster = true })
                        }
                        val memberNames = remember(uiState.connectedNodes) {
                            uiState.visiblePeers().map { it.name }
                        }
                        Box(modifier = Modifier.clickable { showRoster = true }) {
                            AvatarStack(names = memberNames)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                },
                actions = {
                    IconButton(onClick = { showQr = true }) {
                        Icon(
                            imageVector = Icons.Default.QrCode,
                            contentDescription = "Share room",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = { showCallPicker = true }) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Call",
                            tint = Color.White
                        )
                    }
                    IconButton(
                        onClick = { showSos = true },
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(40.dp)
                            .background(SosRed, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "SOS",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AetherBackground)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(AetherBackground)
        ) {
            // SOS banner strip
            if (uiState.isEmergencyMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SosRed)
                        .clickable { showSos = true }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SOS ACTIVE — tap to stand down",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            TabRow(
                selectedTabIndex = selectedRoomTab,
                containerColor = AetherBackground,
                contentColor = Color.White,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedRoomTab]),
                        color = FlameAmber
                    )
                }
            ) {
                listOf("Chat", "Reels", "Music", "Games").forEachIndexed { index, title ->
                    Tab(
                        selected = selectedRoomTab == index,
                        onClick = { selectedRoomTab = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedRoomTab == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (selectedRoomTab) {
                    0 -> ChatTab(
                        uiState = uiState,
                        viewModel = viewModel,
                        onUserClick = { selectedUser = it },
                        onNavigateToFeed = { selectedRoomTab = 1 }
                    )
                    1 -> MediaFeedScreen(viewModel = viewModel)
                    2 -> MusicTabScreen()
                    3 -> ArcadeTab(
                        uiState = uiState,
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

/**
 * Contact action dialog — same behavior as MainChatScreen's selectedUser dialog:
 * Direct Chat routes through viewModel.setDmPeer(peerId); ChatTab renders the
 * 1-on-1 DM view itself whenever uiState.activeDmPeerId != null.
 */
@Composable
private fun RoomContactActionDialog(
    userMsg: ChatMessage,
    uiState: MeshState,
    viewModel: MeshViewModel,
    onDismiss: () -> Unit
) {
    val userHandle = uiState.knownUserIds[userMsg.senderId]
        ?: userMsg.senderHandle.ifBlank { "@${userMsg.senderName.lowercase().replace(" ", "")}" }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(FlameAmber.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        userMsg.senderName.take(1).uppercase(),
                        color = FlameAmber,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(userMsg.senderName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(userHandle, fontSize = 12.sp, color = Color(0xFF53BDEB))
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Mesh Peer ID: ${userMsg.senderId.take(12)}...",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val peerId = userMsg.senderId
                            onDismiss()
                            viewModel.setDmPeer(peerId)
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Lock, contentDescription = "Direct Chat", tint = Color(0xFF53BDEB))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Direct Chat (1-on-1 Mesh DM)", fontWeight = FontWeight.Medium)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val peerId = userMsg.senderId
                            val name = userMsg.senderName
                            onDismiss()
                            CallManager.initiateCall(peerId, name) { payload ->
                                MeshNetworkManager.meshRouter.routeLocalMessage(payload)
                                MeshNetworkManager.webServerManager?.broadcastMessage(
                                    payload,
                                    MeshNetworkManager.localNodeId
                                )
                            }
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Call, contentDescription = "Voice Call", tint = FlameAmber)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Voice Call (Local HD Mesh Audio)", fontWeight = FontWeight.Medium)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val peerId = userMsg.senderId
                            val name = userMsg.senderName
                            onDismiss()
                            CallManager.initiateCall(peerId, name) { payload ->
                                MeshNetworkManager.meshRouter.routeLocalMessage(payload)
                                MeshNetworkManager.webServerManager?.broadcastMessage(
                                    payload,
                                    MeshNetworkManager.localNodeId
                                )
                            }
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = "Video Call", tint = FlameAmber)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Video Call (Direct P2P Stream)", fontWeight = FontWeight.Medium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
