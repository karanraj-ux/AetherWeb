package com.aetherweb.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.aetherweb.app.*
import com.aetherweb.app.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainChatScreen(
    viewModel: MeshViewModel,
    onLaunchLocationPermission: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val diagnosticEvents by viewModel.diagnosticEvents.collectAsStateWithLifecycle()
    val callLogs by viewModel.callLogs.collectAsStateWithLifecycle()

    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.values.any { it }) {
            // Location permission granted; location tracking will start on-demand when entering Radar tab
            viewModel.pingRadarLocation()
        }
    }
        
    var currentTab by remember { mutableStateOf("Chat") }
    var showHotspotDialog by remember { mutableStateOf(false) }
    var selectedQrTab by remember { mutableIntStateOf(0) }
    var showCrossPlatformHubDialog by remember { mutableStateOf(false) }
    var selectedHubTab by remember { mutableIntStateOf(0) }
    var clipboardDraftInput by remember { mutableStateOf("") }
    var showImportBackupDialog by remember { mutableStateOf(false) }
    var importBackupJsonText by remember { mutableStateOf("") }
    var selectedFileForQr by remember { mutableStateOf<java.io.File?>(null) }
    var showDiagnosticsDialog by remember { mutableStateOf(false) }
    var selectedUser by remember { mutableStateOf<ChatMessage?>(null) }
    var showPanicWipeConfirmDialog by remember { mutableStateOf(false) }
    var showCallPickerSheet by remember { mutableStateOf(false) }
    var callPickerIsVideo by remember { mutableStateOf(false) }
    var showNoPeersDialog by remember { mutableStateOf(false) }
    var showCallOptionDialog by remember { mutableStateOf(false) }
    var showRoomPrivacyDialog by remember { mutableStateOf(false) }
    var showSafetySosDialog by remember { mutableStateOf(false) }

    val cdnFilePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: android.net.Uri? ->
        if (uri != null) {
            val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
            scope.launch {
                try {
                    val name = com.aetherweb.app.MeshStorageManager.sanitizeFileName(uri.lastPathSegment ?: "shared_file_${System.currentTimeMillis()}")
                    context.contentResolver.openInputStream(uri)?.use { inStream ->
                        com.aetherweb.app.MeshStorageManager.saveStreamToCache(context, name, inStream)
                    }
                    val downloadUrl = "http://${com.aetherweb.app.NetworkUtils.getLocalIpAddress()}:8080/files/${android.net.Uri.encode(name)}"
                    com.aetherweb.app.MeshNetworkManager.webServerManager?.broadcastMessage("Shared a file: $downloadUrl", uiState.localUserName)
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Hosted on Offline CDN: $name", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

        // Incoming Media Invite
    if (uiState.incomingInvite != null) {
        AlertDialog(
            onDismissRequest = { viewModel.rejectInvite() },
            title = { Text("Game/Media Invite") },
            text = { Text("Someone started a shared session (${uiState.incomingInvite?.type}). Do you want to join?") },
            confirmButton = {
                Button(onClick = { viewModel.acceptInvite() }) { Text("Join") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.rejectInvite() }) { Text("Ignore") }
            }
        )
    }

    if (uiState.pendingSpectators.isNotEmpty()) {
        val spectatorId = uiState.pendingSpectators.first()
        AlertDialog(
            onDismissRequest = { viewModel.rejectSpectator(spectatorId) },
            title = { Text("Web Spectator Request") },
            text = { Text("A web spectator ($spectatorId) wants to join the mesh.") },
            confirmButton = {
                TextButton(onClick = { viewModel.approveSpectator(spectatorId) }) { Text("Approve") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.rejectSpectator(spectatorId) }) { Text("Reject", color = Color.Red) }
            }
        )
    }

    LaunchedEffect(uiState.isHotspotActive) {
        if (uiState.isHotspotActive) showHotspotDialog = true
        else showHotspotDialog = false
    }

    LaunchedEffect(uiState.webClientConnectedEvent) {
        if (uiState.webClientConnectedEvent) {
            showHotspotDialog = false
            viewModel.clearWebClientConnectedEvent()
        }
    }

    // Check if a game is active in fullscreen
        var isGameMinimized by remember { mutableStateOf(false) }
    val isGameActive = uiState.sharedMediaType != "none" && uiState.sharedMediaType.isNotEmpty()
    
    LaunchedEffect(uiState.sharedMediaType) {
        if (uiState.sharedMediaType != "none") {
            isGameMinimized = false
        }
    }

    var showTopMenu by remember { mutableStateOf(false) }
    var showRoomSetupDialog by remember { mutableStateOf(false) }
    var showRosterDialog by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var profileNameInput by remember { mutableStateOf(uiState.localUserName) }
    var profileUsernameIdInput by remember { mutableStateOf(uiState.localUsernameId) }
    var profileIsPermanent by remember { mutableStateOf(uiState.isUsernamePermanent) }
    var profileIsGhost by remember { mutableStateOf(uiState.isGhostMode) }

    var showSOSConfirmDialog by remember { mutableStateOf(false) }
    var sosCustomReasonInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            if (!isGameActive || isGameMinimized) {
                val isDmActive = uiState.activeDmPeerId != null
                val dmPeerId = uiState.activeDmPeerId
                val dmPeerName = if (dmPeerId != null) (uiState.knownUsers[dmPeerId] ?: "Peer_${dmPeerId.take(5)}") else ""
                val dmPeerHandle = if (dmPeerId != null) (uiState.knownUserIds[dmPeerId] ?: "@${dmPeerName.lowercase().replace(" ", "")}") else ""

                TopAppBar(
                    title = {
                        if (isDmActive) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF203540)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = WhatsAppGreen,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = dmPeerName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFE9EDEF)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            Icons.Default.Lock,
                                            contentDescription = "Private DM",
                                            tint = WhatsAppCheckmarkBlue,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                    Text(
                                        text = "$dmPeerHandle • Private 1-on-1 Mesh",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = WhatsAppCheckmarkBlue,
                                        maxLines = 1
                                    )
                                }
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(WhatsAppOutgoingDark)
                                        .clickable { showProfileDialog = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.AccountCircle,
                                        contentDescription = "Profile",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(
                                    modifier = Modifier.clickable { showRosterDialog = true }
                                ) {
                                    Text(
                                        text = "MeshChat Room",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE9EDEF),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = buildString {
                                            append("${uiState.connectedNodes.size + 1} online • ${uiState.localUserName}")
                                            if (uiState.isGhostMode) append(" • 👻 Ghost")
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (uiState.isGhostMode) Color(0xFFB388FF) else WhatsAppGreen,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        if (isDmActive) {
                            IconButton(onClick = { viewModel.clearDmPeer() }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back to Public Room", tint = Color.White)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (uiState.isEmergencyMode) Color(0xFFB71C1C) else Color(0xFF1F2C34)
                    ),
                    actions = {
                        // Alert indicator when emergency beacon is active
                        if (uiState.isEmergencyMode) {
                            IconButton(onClick = { showSafetySosDialog = true }) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = "Active Emergency SOS",
                                    tint = Color.Yellow
                                )
                            }
                        }
                        // Consolidated Mesh Call Button (Voice or Video selection)
                        IconButton(
                            onClick = {
                                if (uiState.connectedNodes.isEmpty()) {
                                    showNoPeersDialog = true
                                } else {
                                    showCallOptionDialog = true
                                }
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.Call, contentDescription = "Start Call", tint = Color(0xFFE9EDEF), modifier = Modifier.size(22.dp))
                        }
                        // Unified QR / Share Button
                        IconButton(
                            onClick = { showHotspotDialog = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.QrCode2,
                                contentDescription = "Invite & Share",
                                tint = if (uiState.isHotspotActive) WhatsAppGreen else Color(0xFFE9EDEF),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        // Standardized 4-Item Menu
                        IconButton(onClick = { showTopMenu = true }) {
                            Icon(Icons.Default.MoreVert, "More Options", tint = Color(0xFFE9EDEF))
                        }
                        DropdownMenu(
                            expanded = showTopMenu,
                            onDismissRequest = { showTopMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.QrCode2, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text("Invite & Connect", fontWeight = FontWeight.SemiBold)
                                            Text("QR Codes, WebChat & Share APK", style = MaterialTheme.typography.labelSmall, color = WhatsAppSubtleText)
                                        }
                                    }
                                },
                                onClick = {
                                    showTopMenu = false
                                    showHotspotDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Security, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text("Room & Privacy", fontWeight = FontWeight.SemiBold)
                                            Text("Burner Mode, Clear History, Leave", style = MaterialTheme.typography.labelSmall, color = WhatsAppSubtleText)
                                        }
                                    }
                                },
                                onClick = {
                                    showTopMenu = false
                                    showRoomPrivacyDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text("Safety & SOS", fontWeight = FontWeight.SemiBold, color = Color(0xFFFF5252))
                                            Text(if (uiState.isEmergencyMode) "Active SOS Beacon (Tap to manage)" else "Emergency Beacon & Panic Wipe", style = MaterialTheme.typography.labelSmall, color = WhatsAppSubtleText)
                                        }
                                    }
                                },
                                onClick = {
                                    showTopMenu = false
                                    showSafetySosDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Tune, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text("Tools & Storage", fontWeight = FontWeight.SemiBold)
                                            Text("Clipboard, 100MB Cache, Battery", style = MaterialTheme.typography.labelSmall, color = WhatsAppSubtleText)
                                        }
                                    }
                                },
                                onClick = {
                                    showTopMenu = false
                                    selectedHubTab = 1
                                    showCrossPlatformHubDialog = true
                                }
                            )
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (!isGameActive || isGameMinimized) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.background,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = currentTab == "Chat",
                        onClick = { currentTab = "Chat" },
                        icon = { Icon(Icons.Default.Chat, "Chat") },
                        label = { Text("Chats") }
                    )
                    NavigationBarItem(
                        selected = currentTab == "Media" || currentTab == "Music",
                        onClick = { currentTab = "Media" },
                        icon = { Icon(Icons.Default.VideoLibrary, "Stream") },
                        label = { Text("Stream") }
                    )
                    NavigationBarItem(
                        selected = currentTab == "Calls",
                        onClick = { currentTab = "Calls" },
                        icon = {
                            val missedCount = callLogs.count { it.callType == "MISSED" }
                            if (missedCount > 0) {
                                BadgedBox(
                                    badge = { Badge { Text(missedCount.toString()) } }
                                ) {
                                    Icon(Icons.Default.Call, "Calls")
                                }
                            } else {
                                Icon(Icons.Default.Call, "Calls")
                            }
                        },
                        label = { Text("Calls") }
                    )
                    NavigationBarItem(
                        selected = currentTab == "Radar",
                        onClick = { currentTab = "Radar" },
                        icon = { Icon(Icons.Default.LocationOn, "Radar") },
                        label = { Text("Radar") }
                    )
                    NavigationBarItem(
                        selected = currentTab == "Arcade",
                        onClick = { currentTab = "Arcade" },
                        icon = { Icon(Icons.Default.Edit, "Games") },
                        label = { Text("Games") }
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (isGameActive && !isGameMinimized) {
                BackHandler {
                    isGameMinimized = true
                }
                // Game Fullscreen View
                Column(modifier = Modifier.fillMaxSize()) {
                    // Action Buttons for Game (Top Bar)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(8.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.TextButton(
                            onClick = { 
                                isGameMinimized = true
                                currentTab = "Arcade" 
                            }
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back to Chat", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Minimize", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        
                        Text(
                            text = uiState.sharedMediaType.uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        androidx.compose.material3.TextButton(
                            onClick = { 
                                // End the game globally
                                viewModel.setSharedMedia("none", "", "collaborative", 0)
                                currentTab = "Arcade" 
                            }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "End Session", tint = androidx.compose.ui.graphics.Color(0xFFEF5350))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("End", color = androidx.compose.ui.graphics.Color(0xFFEF5350))
                        }
                    }
                    SharedMediaScreen(
                        uiState = uiState,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
            } else {
                // Tab Views
                when (currentTab) {
                    "Chat" -> {
                        ChatTab(
                            uiState = uiState,
                            viewModel = viewModel,
                            onUserClick = { selectedUser = it },
                            onNavigateToFeed = { currentTab = "Media" }
                        )
                    }
                    "Music", "Media" -> {
                        MediaFeedScreen(viewModel = viewModel)
                    }
                    "Calls" -> {
                        CallsTabScreen(
                            uiState = uiState,
                            callLogs = callLogs,
                            onInitiateCall = { peerId, peerName, isVideo ->
                                val peerIp = if (uiState.isHotspotActive) "192.168.49.1" else ""
                                CallManager.initiateCall(peerId, peerName, peerIp) { payload ->
                                    com.aetherweb.app.MeshNetworkManager.meshRouter.routeLocalMessage(payload)
                                    com.aetherweb.app.MeshNetworkManager.webServerManager?.broadcastMessage(payload, com.aetherweb.app.MeshNetworkManager.localNodeId)
                                }
                            }
                        )
                    }
                    "Radar" -> {
                        RadarScreen(viewModel)
                    }
                    "Arcade" -> {
                        ArcadeTab(uiState, viewModel)
                    }
                }
            }
        }
        
        if (showRoomSetupDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showRoomSetupDialog = false },
                title = { Text("Create Mesh Room") },
                text = { Text("Do you want this room to be Public (open to everyone nearby) or Private (requires users to manually enter the password)?") },
                confirmButton = {
                    Button(onClick = {
                        viewModel.toggleHotspot(isPrivate = false)
                        showRoomSetupDialog = false
                        showHotspotDialog = true
                    }) { Text("Public Room") }
                },
                dismissButton = {
                    OutlinedButton(onClick = {
                        viewModel.toggleHotspot(isPrivate = true)
                        showRoomSetupDialog = false
                        showHotspotDialog = true
                    }) { Text("Private Room") }
                }
            )
        }
        
        if (showProfileDialog) {
            AlertDialog(
                onDismissRequest = { showProfileDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = WhatsAppGreen,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Profile & Username ID", fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(top = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // WhatsApp Style Circular Avatar Header
                        Box(contentAlignment = Alignment.BottomEnd) {
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(CircleShape)
                                    .background(WhatsAppOutgoingDark)
                                    .border(2.dp, WhatsAppGreen, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = profileNameInput.firstOrNull()?.uppercase() ?: "M",
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(WhatsAppGreen),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.CameraAlt,
                                    contentDescription = "Edit photo",
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        Text(
                            text = "🟢 Active on Mesh & WebChat",
                            style = MaterialTheme.typography.labelSmall,
                            color = WhatsAppGreen,
                            fontWeight = FontWeight.SemiBold
                        )

                        // Display Name (WhatsApp Style)
                        OutlinedTextField(
                            value = profileNameInput,
                            onValueChange = { profileNameInput = it },
                            label = { Text("Display Name (WhatsApp)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null, tint = WhatsAppGreen)
                            },
                            supportingText = {
                                Text("Visible to everyone in mesh group chats and room roster", fontSize = 11.sp)
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = WhatsAppGreen,
                                focusedLabelColor = WhatsAppGreen,
                                cursorColor = WhatsAppGreen
                            )
                        )

                        // Username ID (@handle - Instagram Style)
                        OutlinedTextField(
                            value = profileUsernameIdInput,
                            onValueChange = { input ->
                                val clean = input.trim().lowercase().replace("[^a-z0-9_@]".toRegex(), "")
                                profileUsernameIdInput = if (clean.startsWith("@") || clean.isEmpty()) clean else "@$clean"
                            },
                            label = { Text("Username ID (Instagram Handle)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = {
                                Icon(Icons.Default.AlternateEmail, contentDescription = null, tint = Color(0xFF53BDEB))
                            },
                            trailingIcon = {
                                TextButton(
                                    onClick = {
                                        profileUsernameIdInput = viewModel.generateRandomHandle()
                                    }
                                ) {
                                    Text("🎲 Roll", color = WhatsAppGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            },
                            supportingText = {
                                Text("Your unique handle for @mentions, tagging, and direct calling", fontSize = 11.sp)
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF53BDEB),
                                focusedLabelColor = Color(0xFF53BDEB),
                                cursorColor = Color(0xFF53BDEB)
                            )
                        )

                        // Profile Mode Selection (WhatsApp Permanent vs Instagram/Guest Temporary)
                        Text(
                            text = "ACCOUNT IDENTITY MODE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = WhatsAppSubtleText,
                            modifier = Modifier.align(Alignment.Start)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Permanent Card (WhatsApp)
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { profileIsPermanent = true },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (profileIsPermanent) WhatsAppOutgoingDark else Color(0xFF1F2C34)
                                ),
                                border = if (profileIsPermanent) androidx.compose.foundation.BorderStroke(2.dp, WhatsAppGreen) else null
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            if (profileIsPermanent) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                            contentDescription = null,
                                            tint = if (profileIsPermanent) WhatsAppGreen else WhatsAppSubtleText,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "Permanent",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color(0xFFE9EDEF)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "WhatsApp style. Saved locally across reboots.",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = WhatsAppSubtleText,
                                        lineHeight = 13.sp
                                    )
                                }
                            }

                            // Temporary Card (Instagram/Guest Burner)
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { profileIsPermanent = false },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (!profileIsPermanent) Color(0xFF332712) else Color(0xFF1F2C34)
                                ),
                                border = if (!profileIsPermanent) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFF9800)) else null
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            if (!profileIsPermanent) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                            contentDescription = null,
                                            tint = if (!profileIsPermanent) Color(0xFFFF9800) else WhatsAppSubtleText,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "Temporary",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color(0xFFE9EDEF)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Guest / Burner. Identity wiped on exit.",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = WhatsAppSubtleText,
                                        lineHeight = 13.sp
                                    )
                                }
                            }
                        }

                        // Ghost Mode Storage Setting (Ephemeral RAM-Only vs Stored in Room)
                        Text(
                            text = "CHAT STORAGE & HISTORY PRIVACY",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = WhatsAppSubtleText,
                            modifier = Modifier.align(Alignment.Start)
                        )

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { profileIsGhost = !profileIsGhost },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (profileIsGhost) Color(0xFF261D38) else Color(0xFF1F2C34)
                            ),
                            border = if (profileIsGhost) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFB388FF)) else null
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (profileIsGhost) Icons.Default.VisibilityOff else Icons.Default.Save,
                                    contentDescription = null,
                                    tint = if (profileIsGhost) Color(0xFFB388FF) else WhatsAppSubtleText,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            if (profileIsGhost) "👻 Ghost Mode (Active)" else "💾 Persistent Storage (Default)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (profileIsGhost) Color(0xFFD1C4E9) else Color(0xFFE9EDEF)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        if (profileIsGhost) "RAM-only! Zero Room SQLite disk writes. Vanishes on app close."
                                        else "Messages saved locally in Room database across app restarts.",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = WhatsAppSubtleText,
                                        lineHeight = 13.sp
                                    )
                                }
                                Switch(
                                    checked = profileIsGhost,
                                    onCheckedChange = { profileIsGhost = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFFB388FF),
                                        checkedTrackColor = Color(0xFF5E35B1),
                                        uncheckedThumbColor = WhatsAppSubtleText,
                                        uncheckedTrackColor = Color(0xFF121B22)
                                    )
                                )
                            }
                        }

                        // Live Chat Preview Card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = WhatsAppChatBackgroundDark),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFF1F2C34), RoundedCornerShape(12.dp))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    "LIVE CHAT PREVIEW",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = WhatsAppSubtleText,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    color = WhatsAppOutgoingDark,
                                    shape = RoundedCornerShape(topStart = 14.dp, topEnd = 2.dp, bottomStart = 14.dp, bottomEnd = 14.dp),
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = if (profileNameInput.isNotBlank()) profileNameInput else "Me",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = WhatsAppGreen
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (profileUsernameIdInput.isNotBlank()) profileUsernameIdInput else "@user",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF8696A0),
                                                fontSize = 11.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Connected to offline mesh chat!",
                                            color = Color(0xFFE9EDEF),
                                            fontSize = 14.sp
                                        )
                                        Row(
                                            modifier = Modifier.align(Alignment.End),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("12:00", fontSize = 10.sp, color = WhatsAppSubtleText)
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Icon(
                                                Icons.Default.DoneAll,
                                                contentDescription = null,
                                                modifier = Modifier.size(13.dp),
                                                tint = WhatsAppCheckmarkBlue
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (profileNameInput.isNotBlank()) {
                                viewModel.updateUserName(
                                    newName = profileNameInput.trim(),
                                    newUsernameId = profileUsernameIdInput.trim(),
                                    isPermanent = profileIsPermanent
                                )
                            }
                            viewModel.setGhostMode(profileIsGhost)
                            showProfileDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen)
                    ) {
                        Text("Save & Apply", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showProfileDialog = false }) { Text("Cancel") }
                }
            )
        }
        
        if (showRosterDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showRosterDialog = false },
                title = { Text("Room Roster") },
                text = {
                    LazyColumn {
                        items(uiState.connectedNodes.filter { it.name.isNotBlank() }) { node ->
                            val displayName = uiState.knownUsers[node.id] ?: node.name
                            val handle = uiState.knownUserIds[node.id] ?: "@${node.name.lowercase().replace(" ", "")}"
                            ListItem(
                                headlineContent = { Text(displayName, fontWeight = FontWeight.SemiBold) },
                                supportingContent = { Text(handle, color = Color(0xFF25D366), style = MaterialTheme.typography.labelSmall) },
                                leadingContent = {
                                    Box(
                                        modifier = Modifier.size(12.dp).background(Color(0xFF25D366), shape = androidx.compose.foundation.shape.CircleShape)
                                    )
                                },
                                trailingContent = {
                                    Row(horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                        androidx.compose.material3.IconButton(onClick = {
                                            viewModel.setDmPeer(node.id)
                                            showRosterDialog = false
                                        }) {
                                            androidx.compose.material3.Icon(
                                                androidx.compose.material.icons.Icons.Default.Lock,
                                                contentDescription = "Direct Message",
                                                tint = Color(0xFF53BDEB)
                                            )
                                        }

                                        androidx.compose.material3.IconButton(onClick = {
                                            com.aetherweb.app.CallManager.initiateCall(node.id, displayName) { payload ->
                                                com.aetherweb.app.MeshNetworkManager.meshRouter.routeLocalMessage(payload)
                                                com.aetherweb.app.MeshNetworkManager.webServerManager?.broadcastMessage(payload, com.aetherweb.app.MeshNetworkManager.localNodeId)
                                            }
                                        }) {
                                            androidx.compose.material3.Icon(
                                                androidx.compose.material.icons.Icons.Default.Call,
                                                contentDescription = "Voice Call",
                                                tint = androidx.compose.ui.graphics.Color(0xFF25D366)
                                            )
                                        }

                                        if (uiState.isHotspotActive) {
                                            androidx.compose.material3.TextButton(onClick = {
                                                viewModel.kickUser(node.id)
                                                android.widget.Toast.makeText(context, "${node.name} kicked", android.widget.Toast.LENGTH_SHORT).show()
                                            }) {
                                                Text("Kick", color = MaterialTheme.colorScheme.error)
                                            }
                                            androidx.compose.material3.TextButton(onClick = {
                                                viewModel.banUser(node.id)
                                                android.widget.Toast.makeText(context, "${node.name} banned", android.widget.Toast.LENGTH_SHORT).show()
                                            }) {
                                                Text("Ban", color = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { showRosterDialog = false }) { Text("Close") }
                }
            )
        }

        if (showPanicWipeConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showPanicWipeConfirmDialog = false },
                title = { Text("🚨 Emergency Panic Wipe", fontWeight = FontWeight.Bold, color = Color(0xFFEA4335)) },
                text = { Text("This will permanently destroy all local Room message history, call records, transferred media files, and cryptographic keys from this device with zero trace. This cannot be undone.") },
                confirmButton = {
                    Button(
                        onClick = {
                            showPanicWipeConfirmDialog = false
                            viewModel.emergencyPanicWipe(context)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA4335))
                    ) {
                        Text("ERASE & PURGE ALL")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showPanicWipeConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showNoPeersDialog) {
            AlertDialog(
                onDismissRequest = { showNoPeersDialog = false },
                title = { Text("No Nearby Peers Connected") },
                text = { Text("To make an HD WhatsApp voice call, connect another phone using the Share / Hotspot icon, or scan for nearby Wi-Fi Direct mesh nodes.") },
                confirmButton = {
                    Button(onClick = { showNoPeersDialog = false }) {
                        Text("Got It")
                    }
                }
            )
        }

        if (showCallPickerSheet) {
            AlertDialog(
                onDismissRequest = { showCallPickerSheet = false },
                title = { Text("Select Contact to Call", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        uiState.connectedNodes.forEach { node ->
                            val displayName = uiState.knownUsers[node.id] ?: node.name
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showCallPickerSheet = false
                                        val peerIp = if (uiState.isHotspotActive) "192.168.49.1" else ""
                                        CallManager.initiateCall(node.id, displayName, peerIp) { payload ->
                                            com.aetherweb.app.MeshNetworkManager.meshRouter.routeLocalMessage(payload)
                                            com.aetherweb.app.MeshNetworkManager.webServerManager?.broadcastMessage(payload, com.aetherweb.app.MeshNetworkManager.localNodeId)
                                        }
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color(0xFF25D366).copy(alpha = 0.2f), androidx.compose.foundation.shape.CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(displayName.take(1).uppercase(), color = Color(0xFF25D366), fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(displayName, fontWeight = FontWeight.SemiBold)
                                    Text("Online via Wi-Fi Mesh", fontSize = 12.sp, color = Color(0xFF8696A0))
                                }
                                Icon(if (callPickerIsVideo) Icons.Default.Videocam else Icons.Default.Call, contentDescription = null, tint = Color(0xFF25D366))
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showCallPickerSheet = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Consolidated Call Chooser Dialog (Voice vs Video)
        if (showCallOptionDialog) {
            AlertDialog(
                onDismissRequest = { showCallOptionDialog = false },
                title = { Text("Start Mesh Call", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Select call mode with connected mesh peers:", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showCallOptionDialog = false
                                    if (uiState.connectedNodes.size == 1) {
                                        val peer = uiState.connectedNodes.first()
                                        val peerName = uiState.knownUsers[peer.id] ?: peer.name
                                        val peerIp = if (uiState.isHotspotActive) "192.168.49.1" else ""
                                        CallManager.initiateCall(peer.id, peerName, peerIp) { payload ->
                                            com.aetherweb.app.MeshNetworkManager.meshRouter.routeLocalMessage(payload)
                                            com.aetherweb.app.MeshNetworkManager.webServerManager?.broadcastMessage(payload, com.aetherweb.app.MeshNetworkManager.localNodeId)
                                        }
                                    } else {
                                        callPickerIsVideo = false
                                        showCallPickerSheet = true
                                    }
                                },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(WhatsAppGreen.copy(alpha = 0.2f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Call, contentDescription = null, tint = WhatsAppGreen)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Voice Call", fontWeight = FontWeight.Bold)
                                    Text("Low-bandwidth encrypted Opus mesh audio", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                            }
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showCallOptionDialog = false
                                    if (uiState.connectedNodes.size == 1) {
                                        val peer = uiState.connectedNodes.first()
                                        val peerName = uiState.knownUsers[peer.id] ?: peer.name
                                        val peerIp = if (uiState.isHotspotActive) "192.168.49.1" else ""
                                        CallManager.initiateCall(peer.id, peerName, peerIp) { payload ->
                                            com.aetherweb.app.MeshNetworkManager.meshRouter.routeLocalMessage(payload)
                                            com.aetherweb.app.MeshNetworkManager.webServerManager?.broadcastMessage(payload, com.aetherweb.app.MeshNetworkManager.localNodeId)
                                        }
                                    } else {
                                        callPickerIsVideo = true
                                        showCallPickerSheet = true
                                    }
                                },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color(0xFF53BDEB).copy(alpha = 0.2f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Videocam, contentDescription = null, tint = Color(0xFF53BDEB))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Video Call", fontWeight = FontWeight.Bold)
                                    Text("P2P high-definition mesh video stream", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showCallOptionDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Room & Privacy Dialog (Burner Room, Clear History, Disconnect)
        if (showRoomPrivacyDialog) {
            AlertDialog(
                onDismissRequest = { showRoomPrivacyDialog = false },
                title = { Text("Room & Privacy Options", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showRoomPrivacyDialog = false
                                    viewModel.startBurnerRoom(300)
                                },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = Color(0xFFFF9800))
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Start Burner Room 🔥", fontWeight = FontWeight.Bold)
                                    Text("Self-destructs all messages after 5 minutes", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                            }
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showRoomPrivacyDialog = false
                                    viewModel.clearChat()
                                },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color(0xFFE9EDEF))
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Clear Chat History", fontWeight = FontWeight.Bold)
                                    Text("Remove all local messages from screen", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                            }
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showRoomPrivacyDialog = false
                                    viewModel.disconnectAndCleanup()
                                },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ExitToApp, contentDescription = null, tint = Color(0xFFEA4335))
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Leave Room & Reset", fontWeight = FontWeight.Bold, color = Color(0xFFEA4335))
                                    Text("Disconnect from current mesh network", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showRoomPrivacyDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }

        // Safety & SOS Dialog
        if (showSafetySosDialog) {
            AlertDialog(
                onDismissRequest = { showSafetySosDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFFFF5252))
                        Spacer(Modifier.width(8.dp))
                        Text("Safety & Emergency SOS", fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showSafetySosDialog = false
                                    showSOSConfirmDialog = true
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (uiState.isEmergencyMode) Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF5252))
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        if (uiState.isEmergencyMode) "Cancel Active SOS Beacon 🚨" else "Broadcast SOS Beacon 🚨",
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFF5252)
                                    )
                                    Text("Sends emergency GPS beacon across all mesh hops", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                            }
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.toggleEmergencySiren(context)
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (uiState.isEmergencySirenActive) Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = if (uiState.isEmergencySirenActive) Color.Red else Color.Gray)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        if (uiState.isEmergencySirenActive) "Stop Rescue Siren ⏹️" else "Start Rescue Siren 📢",
                                        fontWeight = FontWeight.Bold,
                                        color = if (uiState.isEmergencySirenActive) Color.Red else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text("Plays max-volume audio distress tone for search & rescue", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                            }
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showSafetySosDialog = false
                                    showPanicWipeConfirmDialog = true
                                },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
                        ) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color(0xFFEA4335))
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Emergency Panic Wipe 🗑️", fontWeight = FontWeight.Bold, color = Color(0xFFEA4335))
                                    Text("Zero-trace instant wipe of all keys and messages", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSafetySosDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }

        // Contact Action Dialog when tapping a user header on any message
        if (selectedUser != null) {
            val userMsg = selectedUser!!
            val userHandle = uiState.knownUserIds[userMsg.senderId] ?: userMsg.senderHandle.ifBlank { "@${userMsg.senderName.lowercase().replace(" ", "")}" }
            AlertDialog(
                onDismissRequest = { selectedUser = null },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(WhatsAppOutgoingDark, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                userMsg.senderName.take(1).uppercase(),
                                color = WhatsAppGreen,
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
                            style = MaterialTheme.typography.labelSmall,
                            color = WhatsAppSubtleText
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val peerId = userMsg.senderId
                                    selectedUser = null
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
                                    selectedUser = null
                                    CallManager.initiateCall(peerId, name) { payload ->
                                        com.aetherweb.app.MeshNetworkManager.meshRouter.routeLocalMessage(payload)
                                        com.aetherweb.app.MeshNetworkManager.webServerManager?.broadcastMessage(payload, com.aetherweb.app.MeshNetworkManager.localNodeId)
                                    }
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Call, contentDescription = "Voice Call", tint = WhatsAppGreen)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Voice Call (Local HD Mesh Audio)", fontWeight = FontWeight.Medium)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val peerId = userMsg.senderId
                                    val name = userMsg.senderName
                                    selectedUser = null
                                    CallManager.initiateCall(peerId, name) { payload ->
                                        com.aetherweb.app.MeshNetworkManager.meshRouter.routeLocalMessage(payload)
                                        com.aetherweb.app.MeshNetworkManager.webServerManager?.broadcastMessage(payload, com.aetherweb.app.MeshNetworkManager.localNodeId)
                                    }
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Videocam, contentDescription = "Video Call", tint = WhatsAppGreen)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Video Call (Direct P2P Stream)", fontWeight = FontWeight.Medium)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selectedUser = null }) {
                        Text("Close")
                    }
                }
            )
        }
        
        // --- Dialogs (Hotspot, Diagnostics, etc) ---
        if (showHotspotDialog) {
            val context = LocalContext.current
            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager

            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showHotspotDialog = false },
                properties = androidx.compose.ui.window.DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = false
                )
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Header with navigation & Hotspot state
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { showHotspotDialog = false }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "2 Separate QR Codes",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (uiState.isHotspotActive) "🟢 Mesh Wi-Fi & WebChat Active" else "⚪ Mesh Hotspot Stopped",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (uiState.isHotspotActive) WhatsAppGreen else Color.Gray
                                )
                            }
                            if (uiState.isHotspotActive) {
                                TextButton(
                                    onClick = { viewModel.toggleHotspot() },
                                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                                ) {
                                    Text("Stop", fontWeight = FontWeight.Bold)
                                }
                            } else {
                                TextButton(
                                    onClick = { viewModel.toggleHotspot(isPrivate = false) },
                                    colors = ButtonDefaults.textButtonColors(contentColor = WhatsAppGreen)
                                ) {
                                    Text("Start", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Separated 2 Tabs: [1. Mesh Wi-Fi QR] and [2. WebChat Browser QR]
                        TabRow(
                            selectedTabIndex = selectedQrTab,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Tab(
                                selected = selectedQrTab == 0,
                                onClick = { selectedQrTab = 0 },
                                text = { Text("1. Mesh Wi-Fi QR", fontWeight = FontWeight.Bold) },
                                icon = { Icon(Icons.Default.Wifi, contentDescription = "Wi-Fi QR") }
                            )
                            Tab(
                                selected = selectedQrTab == 1,
                                onClick = { selectedQrTab = 1 },
                                text = { Text("2. WebChat QR", fontWeight = FontWeight.Bold) },
                                icon = { Icon(Icons.Default.Language, contentDescription = "WebChat QR") }
                            )
                        }

                        Spacer(Modifier.height(20.dp))

                        if (selectedQrTab == 0) {
                            // --- TAB 1: QR CODE 1 - JOIN MESH WI-FI ---
                            Text(
                                "QR Code 1: Join Mesh Wi-Fi",
                                style = MaterialTheme.typography.titleLarge,
                                color = WhatsAppGreen,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Scan with any Phone Camera or Wi-Fi settings to connect to the offline mesh network.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )

                            Spacer(Modifier.height(12.dp))

                            if (uiState.isHotspotActive) {
                                // Wi-Fi QR Code Box
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.padding(8.dp)
                                ) {
                                    val wifiQr = "WIFI:T:WPA;S:${uiState.hotspotSsid};P:${uiState.hotspotPassword};;"
                                    com.aetherweb.app.QRCodeImage(
                                        content = wifiQr,
                                        modifier = Modifier.size(220.dp).padding(12.dp)
                                    )
                                }

                                Spacer(Modifier.height(16.dp))

                                // Feature A: Auto Connect via BLE Beacon (Zero Typing)
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.BluetoothSearching, contentDescription = null, tint = WhatsAppGreen)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Auto-Connect via BLE (Zero Typing)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "Nearby Android phones with Nexus installed auto-detect this broadcast and join Wi-Fi without typing passwords.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.height(10.dp))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = {
                                                    viewModel.pulseAllBleHotspotCredentials()
                                                    android.widget.Toast.makeText(context, "Pulsing Wi-Fi credentials over BLE...", android.widget.Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.Sensors, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Auto-Pulse BLE")
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    viewModel.pulseBleChunk("S:" + uiState.hotspotSsid)
                                                    android.widget.Toast.makeText(context, "Pulsed SSID", android.widget.Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("Pulse SSID")
                                            }
                                        }
                                    }
                                }

                                Spacer(Modifier.height(10.dp))

                                // Feature B: Manual Wi-Fi Share & Credentials
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Wifi, contentDescription = null, tint = WhatsAppGreen)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Manual Wi-Fi Credentials", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                        }
                                        Spacer(Modifier.height(10.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text("Network (SSID)", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                                Text(uiState.hotspotSsid, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("SSID", uiState.hotspotSsid))
                                                    android.widget.Toast.makeText(context, "SSID Copied", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            ) { Text("Copy SSID") }
                                        }
                                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text("Password", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                                Text(uiState.hotspotPassword, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Password", uiState.hotspotPassword))
                                                    android.widget.Toast.makeText(context, "Password Copied", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            ) { Text("Copy Pwd") }
                                        }
                                        Spacer(Modifier.height(12.dp))
                                        Button(
                                            onClick = {
                                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(android.content.Intent.EXTRA_SUBJECT, "Nexus Mesh Wi-Fi Access")
                                                    putExtra(
                                                        android.content.Intent.EXTRA_TEXT,
                                                        "Nexus Offline Mesh Wi-Fi Connection:\n\n• Network (SSID): ${uiState.hotspotSsid}\n• Password: ${uiState.hotspotPassword}\n\nConnect to this Wi-Fi to chat and share files offline!"
                                                    )
                                                }
                                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Wi-Fi Credentials"))
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Share Wi-Fi Info (Apps / SMS)")
                                        }
                                    }
                                }
                            } else {
                                // Hotspot is stopped
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.Gray)
                                        Spacer(Modifier.height(12.dp))
                                        Text("Mesh Wi-Fi is Inactive", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            "Start the Mesh Room to create an instant private Wi-Fi network and generate the Wi-Fi QR code.",
                                            style = MaterialTheme.typography.bodySmall,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.height(16.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Button(
                                                onClick = { viewModel.toggleHotspot(isPrivate = false) }
                                            ) {
                                                Text("Start Public Room")
                                            }
                                            OutlinedButton(
                                                onClick = { viewModel.toggleHotspot(isPrivate = true) }
                                            ) {
                                                Text("Start Private Room")
                                            }
                                        }
                                    }
                                }

                                // Show Discovered Host if available
                                if (uiState.discoveredHostSsid != null && !uiState.isWifiConnected) {
                                    Spacer(Modifier.height(12.dp))
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Text("Discovered Nearby Mesh Host via BLE", fontWeight = FontWeight.Bold)
                                            Text("Host: ${uiState.discoveredHostSsid}", style = MaterialTheme.typography.bodySmall)
                                            Spacer(Modifier.height(8.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Button(
                                                    onClick = {
                                                        viewModel.connectToDiscoveredHotspot()
                                                        android.widget.Toast.makeText(context, "Connecting...", android.widget.Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text("Auto-Connect")
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        val clip = android.content.ClipData.newPlainText("Wi-Fi Password", uiState.discoveredHostPwd ?: "")
                                                        clipboard.setPrimaryClip(clip)
                                                        android.widget.Toast.makeText(context, "Password Copied", android.widget.Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text("Copy Pwd")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // --- TAB 2: QR CODE 2 - OPEN WEBCHAT IN BROWSER ---
                            Text(
                                "QR Code 2: Open WebChat in Browser",
                                style = MaterialTheme.typography.titleLarge,
                                color = WhatsAppGreen,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Zero installation needed! iPhone, Mac, Windows, Linux, and Android can chat via web browser.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )

                            Spacer(Modifier.height(12.dp))

                            if (uiState.hotspotIp.isNotEmpty()) {
                                val url = "http://${uiState.hotspotIp}:8080"

                                // WebChat QR Code Box
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.padding(8.dp)
                                ) {
                                    com.aetherweb.app.QRCodeImage(
                                        content = url,
                                        modifier = Modifier.size(220.dp).padding(12.dp)
                                    )
                                }

                                Spacer(Modifier.height(16.dp))

                                // WebChat Portal Links
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Text("Browser Web Addresses", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                        Spacer(Modifier.height(6.dp))
                                        Text("Primary Link (DNS Captive Portal):", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                        Text("http://nexus.run", fontWeight = FontWeight.Bold, color = WhatsAppGreen, style = MaterialTheme.typography.titleMedium)
                                        Spacer(Modifier.height(6.dp))
                                        Text("Direct IP Link:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                        Text(url, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Spacer(Modifier.height(12.dp))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(
                                                onClick = {
                                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("WebChat URL", url))
                                                    android.widget.Toast.makeText(context, "URL Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Copy Link")
                                            }
                                            Button(
                                                onClick = {
                                                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(android.content.Intent.EXTRA_SUBJECT, "Join Nexus Offline WebChat")
                                                        putExtra(
                                                            android.content.Intent.EXTRA_TEXT,
                                                            "Join Nexus Offline WebChat:\n1. Connect to Wi-Fi '${uiState.hotspotSsid}'\n2. Open your browser and go to http://nexus.run or $url\n\nNo installation required!"
                                                        )
                                                    }
                                                    context.startActivity(android.content.Intent.createChooser(shareIntent, "Share WebChat Link"))
                                                },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Share Link")
                                            }
                                        }
                                    }
                                }

                                Spacer(Modifier.height(10.dp))

                                // Direct APK Sharing Card
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Text("Share Full Android App", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "Send the full Android APK directly to other Android devices via Bluetooth, Nearby Share, or Wi-Fi Direct.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.height(10.dp))
                                        Button(
                                            onClick = { viewModel.shareAppApk(context) },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.Share, contentDescription = "Share APK", modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Share APK (Bluetooth / Nearby Share)")
                                        }
                                    }
                                }
                            } else if (uiState.isHotspotActive) {
                                Column(
                                    modifier = Modifier.padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(color = WhatsAppGreen)
                                    Spacer(Modifier.height(16.dp))
                                    Text("Binding IP address & web server...", fontWeight = FontWeight.Medium)
                                }
                            } else {
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.Gray)
                                        Spacer(Modifier.height(12.dp))
                                        Text("WebChat Server Stopped", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            "Start the Mesh Room to launch the local web server and generate the WebChat portal QR code.",
                                            style = MaterialTheme.typography.bodySmall,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.height(16.dp))
                                        Button(
                                            onClick = { viewModel.toggleHotspot(isPrivate = false) }
                                        ) {
                                            Text("Start Mesh Room & WebChat")
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(56.dp))
                    }
                }
            }
        }

        // =====================================================================
        // PHASE 4: CROSS-PLATFORM UTILITIES HUB DIALOG (Clipboard, CDN, Speed Test, Backup)
        // =====================================================================
        if (showCrossPlatformHubDialog) {
            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager

            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showCrossPlatformHubDialog = false },
                properties = androidx.compose.ui.window.DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = false
                )
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { showCrossPlatformHubDialog = false }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "Cross-Platform Utilities",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Phone ⇄ PC, Mac & Browser Sync",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = WhatsAppGreen
                                )
                            }
                            IconButton(
                                onClick = { showDiagnosticsDialog = true }
                            ) {
                                Icon(Icons.Default.BugReport, contentDescription = "Diagnostics", tint = Color.Gray)
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Consolidated 4 Hub Tabs (Fits without horizontal scrolling)
                        TabRow(
                            selectedTabIndex = selectedHubTab.coerceIn(0, 3),
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Tab(
                                selected = selectedHubTab == 0,
                                onClick = { selectedHubTab = 0 },
                                text = { Text("Clipboard", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                icon = { Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                            Tab(
                                selected = selectedHubTab == 1,
                                onClick = { selectedHubTab = 1 },
                                text = { Text("CDN Files", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                icon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                            Tab(
                                selected = selectedHubTab == 2,
                                onClick = { selectedHubTab = 2 },
                                text = { Text("Optimizer", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                icon = { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                            Tab(
                                selected = selectedHubTab == 3,
                                onClick = { selectedHubTab = 3 },
                                text = { Text("Backup/SOS", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                icon = { Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        when (selectedHubTab) {
                            0 -> {
                                // --- TAB 0: UNIVERSAL SHARED CLIPBOARD ---
                                Text(
                                    "📋 Universal Shared Clipboard",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = WhatsAppGreen
                                )
                                Text(
                                    "Instant offline clipboard sync between Android and connected PC, Mac & iPhone browsers.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )

                                Spacer(Modifier.height(12.dp))

                                // Current Synced Clipboard Box
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Current Synchronized Text:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                            if (uiState.sharedClipboardSender.isNotBlank()) {
                                                Text(
                                                    "by ${uiState.sharedClipboardSender}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = WhatsAppGreen
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.background,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = if (uiState.sharedClipboardText.isNotBlank()) uiState.sharedClipboardText else "(No text synchronized yet)",
                                                modifier = Modifier.padding(12.dp),
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                        Spacer(Modifier.height(12.dp))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = {
                                                    if (uiState.sharedClipboardText.isNotBlank()) {
                                                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("MeshClipboard", uiState.sharedClipboardText))
                                                        android.widget.Toast.makeText(context, "Copied to phone clipboard!", android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.weight(1f),
                                                enabled = uiState.sharedClipboardText.isNotBlank()
                                            ) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Copy")
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    val primary = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                                                    if (!primary.isNullOrBlank()) {
                                                        viewModel.updateSharedClipboard(primary)
                                                        android.widget.Toast.makeText(context, "Pushed phone clipboard to mesh!", android.widget.Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        android.widget.Toast.makeText(context, "Phone clipboard is empty", android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Paste Phone Clip")
                                            }
                                        }
                                    }
                                }

                                Spacer(Modifier.height(16.dp))

                                // Compose new text card
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Text("Push New Text to All Devices:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                        Spacer(Modifier.height(8.dp))
                                        OutlinedTextField(
                                            value = clipboardDraftInput,
                                            onValueChange = { clipboardDraftInput = it },
                                            placeholder = { Text("Paste code, links, passwords, or notes to push...") },
                                            modifier = Modifier.fillMaxWidth().height(100.dp)
                                        )
                                        Spacer(Modifier.height(10.dp))
                                        Button(
                                            onClick = {
                                                if (clipboardDraftInput.isNotBlank()) {
                                                    viewModel.updateSharedClipboard(clipboardDraftInput)
                                                    clipboardDraftInput = ""
                                                    android.widget.Toast.makeText(context, "Pushed to all PC, Mac & Mobile devices!", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            enabled = clipboardDraftInput.isNotBlank()
                                        ) {
                                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Broadcast Text (Web + Mesh)")
                                        }
                                    }
                                }
                            }
                            1 -> {
                                // --- TAB 1: LOCAL CDN & OFFLINE AIRDROP ---
                                Text(
                                    "📁 Local CDN & Offline AirDrop",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = WhatsAppGreen
                                )
                                Text(
                                    "Host documents, videos, and photos offline. Anyone connected to Wi-Fi can download with 0 internet data.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )

                                Spacer(Modifier.height(12.dp))

                                Button(
                                    onClick = { cdnFilePickerLauncher.launch("*/*") },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Host File on Offline CDN")
                                }

                                Spacer(Modifier.height(14.dp))

                                val cachedFiles = remember(showCrossPlatformHubDialog) {
                                    com.aetherweb.app.MeshStorageManager.getCacheDir(context).listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList()
                                }

                                if (cachedFiles.isEmpty()) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(24.dp).fillMaxWidth(),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(40.dp), tint = Color.Gray)
                                            Spacer(Modifier.height(8.dp))
                                            Text("No hosted files yet", fontWeight = FontWeight.Bold)
                                            Text("Click 'Host File on Offline CDN' above to share files.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                        }
                                    }
                                } else {
                                    Text(
                                        "Hosted Files (${cachedFiles.size}):",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(Modifier.height(8.dp))

                                    cachedFiles.forEach { file ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(file.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                                        Text(
                                                            com.aetherweb.app.MeshStorageManager.formatFileSize(file.length()),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = Color.Gray
                                                        )
                                                    }
                                                    IconButton(onClick = { selectedFileForQr = file }) {
                                                        Icon(Icons.Default.QrCode, contentDescription = "Show QR", tint = WhatsAppGreen)
                                                    }
                                                }
                                                Spacer(Modifier.height(8.dp))
                                                val fileUrl = "http://${if (uiState.hotspotIp.isNotBlank()) uiState.hotspotIp else com.aetherweb.app.NetworkUtils.getLocalIpAddress()}:8080/files/${android.net.Uri.encode(file.name)}"
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    OutlinedButton(
                                                        onClick = {
                                                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("File URL", fileUrl))
                                                            android.widget.Toast.makeText(context, "URL Copied!", android.widget.Toast.LENGTH_SHORT).show()
                                                        },
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Text("Copy Link", fontSize = 12.sp)
                                                    }
                                                    Button(
                                                        onClick = {
                                                            val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                                type = com.aetherweb.app.MeshStorageManager.getMimeType(file.name)
                                                                putExtra(
                                                                    android.content.Intent.EXTRA_STREAM,
                                                                    androidx.core.content.FileProvider.getUriForFile(
                                                                        context,
                                                                        "${context.packageName}.fileprovider",
                                                                        file
                                                                    )
                                                                )
                                                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                            }
                                                            context.startActivity(android.content.Intent.createChooser(sendIntent, "Share ${file.name}"))
                                                        },
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Text("AirDrop / Share", fontSize = 12.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            2 -> {
                                // --- TAB 2: SYSTEM OPTIMIZATION, STORAGE & BENCHMARK ---
                                Text(
                                    "⚡ System & Network Optimization",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = WhatsAppGreen
                                )
                                Text(
                                    "Adaptive battery duty cycles, automated 100MB storage pruner, packet compression, and speed benchmarks.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )

                                Spacer(Modifier.height(14.dp))

                                // CARD 1: ADAPTIVE BATTERY SAVER (Streamlined, zero jargon!)
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        val isEcoActive = uiState.bleDutyCycleLabel.contains("Saver") || uiState.bleDutyCycleLabel.contains("10%")
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = WhatsAppGreen)
                                                Spacer(Modifier.width(8.dp))
                                                Column {
                                                    Text("Adaptive Battery Saver", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                                    Text(
                                                        if (isEcoActive) "Eco Saver Active (~80% radio power saved)" else "Balanced Dynamic Mode",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = WhatsAppGreen
                                                    )
                                                }
                                            }

                                            Switch(
                                                checked = isEcoActive,
                                                onCheckedChange = { checked ->
                                                    if (checked) {
                                                        viewModel.setBleDutyCycle(com.aetherweb.app.BleMeshManager.BleDutyCycle.ECO_SAVER)
                                                    } else {
                                                        viewModel.setBleDutyCycle(com.aetherweb.app.BleMeshManager.BleDutyCycle.BALANCED)
                                                    }
                                                }
                                            )
                                        }

                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            "Automatically adapts BLE sniffing duty cycles and WebSocket sleep pulses to extend battery life up to 3x without dropping mesh messages.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(Modifier.height(14.dp))

                                // CARD 2: STORAGE FOOTPRINT (100MB MEDIA THRESHOLD)
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Storage, contentDescription = null, tint = WhatsAppGreen)
                                            Spacer(Modifier.width(8.dp))
                                            Column {
                                                Text("Storage Footprint (100MB Cap)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                                Text("Automated background sweeps every 15m", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                            }
                                        }

                                        Spacer(Modifier.height(10.dp))
                                        val maxBytes = 100 * 1024 * 1024L
                                        val currentBytes = uiState.cacheSizeBytes
                                        val percent = (currentBytes.toFloat() / maxBytes.toFloat()).coerceIn(0f, 1f)

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                "Cache Used: ${com.aetherweb.app.MeshStorageManager.formatFileSize(currentBytes)}",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                "Cap: 100 MB (${uiState.cacheFileCount} files)",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = Color.Gray
                                            )
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        LinearProgressIndicator(
                                            progress = { percent },
                                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                            color = if (percent > 0.85f) Color.Red else WhatsAppGreen,
                                            trackColor = Color.DarkGray
                                        )

                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            "Files older than 24h and temp voice notes older than 6h are pruned. When media cache exceeds 100MB, oldest media is automatically evicted until below 60MB.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Spacer(Modifier.height(12.dp))
                                        Button(
                                            onClick = { viewModel.runStorageSweep(context) },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Enforce 100MB Cap / Sweep Now")
                                        }

                                        if (uiState.lastStorageSweepSummary != null) {
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                "Last Sweep: ${uiState.lastStorageSweepSummary}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = WhatsAppGreen
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(14.dp))

                                // CARD 3: NETWORK THROTTLING & PACKET COMPRESSION (2G / SATELLITE MODE)
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (uiState.isDataSaverEnabled) Color(0xFF1B3A2B) else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.SatelliteAlt,
                                                    contentDescription = null,
                                                    tint = if (uiState.isDataSaverEnabled) WhatsAppGreen else Color.Gray
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Column {
                                                    Text(
                                                        "Weak 2G / Satellite Mode",
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        color = if (uiState.isDataSaverEnabled) Color.White else MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        if (uiState.isDataSaverEnabled) "Payload Compression & Throttling ACTIVE" else "Standard Full-Speed Mode",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (uiState.isDataSaverEnabled) WhatsAppGreen else Color.Gray
                                                    )
                                                }
                                            }

                                            Switch(
                                                checked = uiState.isDataSaverEnabled,
                                                onCheckedChange = { viewModel.toggleDataSaver() }
                                            )
                                        }

                                        Spacer(Modifier.height(10.dp))
                                        Text(
                                            "Compresses media payloads and downsamples photos by up to 98% (e.g. 5MB photo becomes ~50KB) to ensure smooth delivery over weak 2G or high-latency satellite connections without stalling or timeouts.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Spacer(Modifier.height(10.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                            AssistChip(
                                                onClick = {},
                                                label = { Text("⚡ GZIP Packet Comp: ON", fontSize = 11.sp) },
                                                colors = AssistChipDefaults.assistChipColors(labelColor = WhatsAppGreen)
                                            )
                                            AssistChip(
                                                onClick = {},
                                                label = { Text("🖼️ Media Downsample: ${if (uiState.isDataSaverEnabled) "800px (60%)" else "1600px"}", fontSize = 11.sp) }
                                            )
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        AssistChip(
                                            onClick = {},
                                            label = { Text("📦 Rate Throttling: ${if (uiState.isDataSaverEnabled) "32KB Paced (2G/Sat)" else "1MB Stream"}", fontSize = 11.sp) }
                                        )
                                    }
                                }

                                Spacer(Modifier.height(14.dp))

                                // CARD 4: MESH SPEED & LATENCY BENCHMARK
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Text("Active Link Status", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                        Spacer(Modifier.height(6.dp))
                                        Text("• Mode: " + if (uiState.isHotspotActive) "🟢 Wi-Fi AP Host (Room Active)" else if (uiState.isWifiConnected) "🔵 Wi-Fi Client Connected" else "⚪ BLE Mesh Beacon", style = MaterialTheme.typography.bodySmall)
                                        Text("• Local IP: " + if (uiState.hotspotIp.isNotBlank()) uiState.hotspotIp else com.aetherweb.app.NetworkUtils.getLocalIpAddress(), style = MaterialTheme.typography.bodySmall)
                                        Text("• Web Server Port: 8080 (HTTP & WebSockets)", style = MaterialTheme.typography.bodySmall)
                                        Text("• Mesh Socket Port: 8888 (P2P Packets)", style = MaterialTheme.typography.bodySmall)
                                        Text("• Connected Mesh Nodes: ${uiState.connectedNodes.size}", style = MaterialTheme.typography.bodySmall)
                                        val bridgeState = com.aetherweb.app.WifiClusterBridgeManager.bridgeState.value
                                        Text("• Wi-Fi Multi-Cluster: ${if (bridgeState.isBridgeRelay) "🌉 Active Bridge Relay (${bridgeState.activeClustersCount} Clusters)" else "🌐 Cluster (${bridgeState.currentClusterId})"}", style = MaterialTheme.typography.bodySmall, color = if (bridgeState.isBridgeRelay) WhatsAppGreen else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }

                                Spacer(Modifier.height(12.dp))

                                Button(
                                    onClick = { viewModel.runMeshSpeedTest() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Run Latency & Throughput Benchmark")
                                }

                                if (uiState.meshSpeedResult != null) {
                                    Spacer(Modifier.height(14.dp))
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(Icons.Default.NetworkCheck, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(36.dp))
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                uiState.meshSpeedResult!!,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                }
                            }
                            3 -> {
                                // --- TAB 3: BACKUP & EMERGENCY SOS RESCUE ---
                                Text(
                                    "💾 Backup & Survival Utilities",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = WhatsAppGreen
                                )
                                Text(
                                    "Export all offline chat history or activate life-safety distress alarms in disaster areas.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )

                                Spacer(Modifier.height(14.dp))

                                // Backup Card
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Text("Offline Chat Backup", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                        Spacer(Modifier.height(4.dp))
                                        Text("Export or restore messages and timestamps to standard JSON.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                        Spacer(Modifier.height(10.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = { viewModel.exportChatBackup(context) },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Export JSON")
                                            }
                                            OutlinedButton(
                                                onClick = { showImportBackupDialog = true },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Import Backup")
                                            }
                                        }
                                    }
                                }

                                Spacer(Modifier.height(16.dp))

                                // Emergency Siren Card
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (uiState.isEmergencySirenActive) Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.NotificationsActive,
                                                contentDescription = null,
                                                tint = if (uiState.isEmergencySirenActive) Color.Red else Color.Gray
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                "🚨 Emergency Rescue Siren",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = if (uiState.isEmergencySirenActive) Color.Red else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            "Emits a high-volume alarm tone to alert nearby rescue personnel and signal your location in disaster/collapsed structure situations.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        Button(
                                            onClick = { viewModel.toggleEmergencySiren(context) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (uiState.isEmergencySirenActive) Color.Red else Color(0xFFD32F2F)
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                if (uiState.isEmergencySirenActive) "⏹️ STOP EMERGENCY SIREN" else "🚨 START LOUD RESCUE SIREN",
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(56.dp))
                    }
                }
            }
        }

        // QR Code Dialog for individual hosted file
        if (selectedFileForQr != null) {
            val file = selectedFileForQr!!
            val fileDownloadUrl = "http://${if (uiState.hotspotIp.isNotBlank()) uiState.hotspotIp else com.aetherweb.app.NetworkUtils.getLocalIpAddress()}:8080/files/${android.net.Uri.encode(file.name)}"
            AlertDialog(
                onDismissRequest = { selectedFileForQr = null },
                title = { Text(file.name, maxLines = 1, fontWeight = FontWeight.Bold) },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text("Scan to download this file directly via offline CDN:", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(12.dp))
                        com.aetherweb.app.QRCodeImage(
                            content = fileDownloadUrl,
                            modifier = Modifier.size(200.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(fileDownloadUrl, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                },
                confirmButton = {
                    Button(onClick = { selectedFileForQr = null }) { Text("Close") }
                }
            )
        }

        // Import Backup JSON Dialog
        if (showImportBackupDialog) {
            AlertDialog(
                onDismissRequest = { showImportBackupDialog = false },
                title = { Text("Import Chat Backup") },
                text = {
                    Column {
                        Text("Paste backup JSON content to restore messages:", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = importBackupJsonText,
                            onValueChange = { importBackupJsonText = it },
                            placeholder = { Text("Paste JSON here...") },
                            modifier = Modifier.fillMaxWidth().height(140.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (importBackupJsonText.isNotBlank()) {
                                viewModel.importChatBackup(context, importBackupJsonText)
                                importBackupJsonText = ""
                                showImportBackupDialog = false
                            }
                        },
                        enabled = importBackupJsonText.isNotBlank()
                    ) {
                        Text("Restore")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showImportBackupDialog = false }) { Text("Cancel") }
                }
            )
        }

        if (showDiagnosticsDialog) {
            AlertDialog(
                onDismissRequest = { showDiagnosticsDialog = false },
                title = { Text("Diagnostic Logs") },
                text = {
                    val logs = com.aetherweb.app.DiagnosticLogger.events.collectAsState().value
                    androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.heightIn(max = 400.dp).fillMaxWidth()) {
                        items(count = logs.size) { i ->
                            val log = logs[i]
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text("[${log.timestamp}] ${log.component} - ${log.action}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(log.detail, fontSize = 12.sp)
                                HorizontalDivider()
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showDiagnosticsDialog = false }) { Text("Close") } }
            )
        }

        if (showSOSConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showSOSConfirmDialog = false },
                icon = {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "SOS Warning",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = {
                    Text(
                        if (uiState.isEmergencyMode) "Active SOS Beacon" else "Broadcast SOS Beacon",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column {
                        if (uiState.isEmergencyMode) {
                            Text(
                                "Your Emergency Beacon is currently broadcasting coordinates and distress signal to all nearby mesh peers.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            Text(
                                "This sends an immediate high-priority distress broadcast with your peer ID and coordinates to all discovered Wi-Fi & BLE mesh nodes in range.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            androidx.compose.material3.OutlinedTextField(
                                value = sosCustomReasonInput,
                                onValueChange = { sosCustomReasonInput = it },
                                label = { Text("Emergency Note (optional)") },
                                placeholder = { Text("e.g. Need medical assistance / lost") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                },
                confirmButton = {
                    if (uiState.isEmergencyMode) {
                        Button(
                            onClick = {
                                viewModel.cancelSOS()
                                showSOSConfirmDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen)
                        ) {
                            Text("Cancel / Stand Down", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = {
                                val reason = if (sosCustomReasonInput.isNotBlank()) sosCustomReasonInput.trim() else "Immediate Assistance Required!"
                                viewModel.triggerSOS(customMessage = reason)
                                showSOSConfirmDialog = false
                                sosCustomReasonInput = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                        ) {
                            Text("BROADCAST SOS", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSOSConfirmDialog = false }) {
                        Text("Dismiss")
                    }
                }
            )
        }
    }
}

@Composable
fun ChatTab(
    uiState: MeshState,
    viewModel: MeshViewModel,
    onUserClick: (ChatMessage) -> Unit,
    onNavigateToFeed: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }
    
    var isRecording by remember { mutableStateOf(false) }
    val audioRecorder = remember { com.aetherweb.app.AudioRecorderHelper(context) }
    
    val recordPermissionLauncher = rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            isRecording = true
            audioRecorder.startRecording()
        } else {
            android.widget.Toast.makeText(context, "Microphone permission required", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    var privateRoomInput by remember { mutableStateOf("") }
    
    val photoPicker = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.shareFile(it) }
    }

    if (fullScreenImageUrl != null) {
        val currentImageUrl = fullScreenImageUrl!!
        val localFile = remember(currentImageUrl) { com.aetherweb.app.MeshStorageManager.findLocalFile(context, currentImageUrl) }
        val displayModel: Any = localFile ?: currentImageUrl
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { fullScreenImageUrl = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                coil.compose.AsyncImage(
                    model = displayModel,
                    contentDescription = "Full Screen Image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
                // Top control bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { fullScreenImageUrl = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = currentImageUrl.substringAfterLast("/").ifEmpty { "Mesh Photo" },
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            viewModel.saveImageToGallery(context, currentImageUrl)
                        }
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Save to Gallery", tint = WhatsAppGreen)
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WhatsAppChatBackgroundDark)
    ) {
        if (uiState.discoveredHostSsid != null && !uiState.isHotspotActive && !uiState.isWifiConnected) {
            val context = androidx.compose.ui.platform.LocalContext.current
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.padding(16.dp).fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Wifi, contentDescription = "Wi-Fi")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("High-Speed Mesh Found!", fontWeight = FontWeight.Bold)
                    }
                    if (uiState.discoveredHostPwd == "PRIVATE") {
                        Text("Host: ${uiState.discoveredHostSsid}\nPassword: [Private Room]", modifier = Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.error)
                        androidx.compose.material3.OutlinedTextField(
                            value = privateRoomInput,
                            onValueChange = { privateRoomInput = it },
                            label = { Text("Enter Room Password") },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                        Row(modifier = Modifier.padding(top = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    if (privateRoomInput.isNotBlank()) {
                                        viewModel.connectToDiscoveredHotspot(passwordOverride = privateRoomInput)
                                        android.widget.Toast.makeText(context, "Requesting connection...", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = privateRoomInput.isNotBlank()
                            ) {
                                Text("Connect")
                            }
                        }
                    } else {
                        Text("Host: ${uiState.discoveredHostSsid}\nPassword: ${uiState.discoveredHostPwd}", modifier = Modifier.padding(top = 4.dp))
                        
                        Row(modifier = Modifier.padding(top = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                viewModel.connectToDiscoveredHotspot()
                                android.widget.Toast.makeText(context, "Requesting connection...", android.widget.Toast.LENGTH_SHORT).show()
                            }, modifier = Modifier.weight(1f)) {
                                Text("Auto-Connect")
                            }
                            
                            OutlinedButton(onClick = {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Wi-Fi Password", uiState.discoveredHostPwd)
                                clipboard.setPrimaryClip(clip)
                                android.widget.Toast.makeText(context, "Password copied! Select ${uiState.discoveredHostSsid} in settings.", android.widget.Toast.LENGTH_LONG).show()
                                
                                val intent = android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
                                context.startActivity(intent)
                            }, modifier = Modifier.weight(1f)) {
                                Text("Copy Password")
                            }
                        }
                    }
                }
            }
        }
        
        if (uiState.hotspotError != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.padding(16.dp).fillMaxWidth()
            ) {
                Text(uiState.hotspotError, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(16.dp))
            }
        }
        
        // Emergency SOS Broadcast Beacon Alert Banner
        if (uiState.activeSOSAlert != null) {
            val alert = uiState.activeSOSAlert!!
            Card(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFB71C1C))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "Emergency Alert",
                                tint = Color.Yellow,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "🚨 EMERGENCY SOS BEACON",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            )
                        }
                        IconButton(
                            onClick = { viewModel.dismissSOSAlert() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss Alert", tint = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "From: ${alert.senderName} (${alert.senderHandle.ifBlank { "@peer" }})",
                        color = Color.Yellow,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        alert.message,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    if (alert.lat != null && alert.lng != null) {
                        Text(
                            "📍 Location: %.5f, %.5f".format(alert.lat, alert.lng),
                            color = Color(0xFFFFD54F),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                viewModel.setDmPeer(alert.senderId)
                            }
                        ) {
                            Text("Direct Reply", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                CallManager.initiateCall(alert.senderId, alert.senderName) { payload ->
                                    com.aetherweb.app.MeshNetworkManager.meshRouter.routeLocalMessage(payload)
                                    com.aetherweb.app.MeshNetworkManager.webServerManager?.broadcastMessage(payload, com.aetherweb.app.MeshNetworkManager.localNodeId)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFFB71C1C))
                        ) {
                            Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Call Node", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Active 1-on-1 DM Top Header Pill
        if (uiState.activeDmPeerId != null) {
            val dmPeerId = uiState.activeDmPeerId!!
            val peerName = uiState.knownUsers[dmPeerId] ?: "Peer_${dmPeerId.take(5)}"
            val peerHandle = uiState.knownUserIds[dmPeerId] ?: "@${peerName.lowercase().replace(" ", "")}"

            Surface(
                color = Color(0xFF102A38),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = WhatsAppCheckmarkBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                "Direct Chat with $peerName",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                "$peerHandle • Only visible to both of you",
                                color = WhatsAppCheckmarkBlue,
                                fontSize = 11.sp
                            )
                        }
                    }
                    TextButton(onClick = { viewModel.clearDmPeer() }) {
                        Text("Exit DM", color = Color(0xFFE9EDEF), fontSize = 12.sp)
                    }
                }
            }
        }

        // Active SOS Beacon Local Sender Banner
        if (uiState.isEmergencyMode) {
            Card(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFD32F2F))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Yellow)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Broadcasting Emergency SOS Beacon", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Pinging all nearby mesh nodes", color = Color(0xFFFFCDD2), fontSize = 11.sp)
                        }
                    }
                    Button(
                        onClick = { viewModel.cancelSOS() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Red)
                    ) {
                        Text("Cancel SOS", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // Active Game Banner in Chat
        if (uiState.chessState.whitePlayerId.isNotEmpty()) {
            Card(
                modifier = Modifier.padding(16.dp).fillMaxWidth().clickable {
                    // Quick jump back to active game
                    if (uiState.chessState.whitePlayerId.isNotEmpty()) {
                        viewModel.setSharedMedia("chess", "", "game", 2)
                    }
                },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Active Game", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Game in progress! Tap to join.", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                }
            }
        }

        
        // Burner Room Banner
        if (uiState.isBurnerRoomActive) {
            Card(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = "Burner Room", tint = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("🔥 BURNER ROOM ACTIVE", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                        }
                        Text("${uiState.burnerRoomCountdown}s", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Participants: ${uiState.activeBurnerKeys.joinToString(", ")}", color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { viewModel.leaveBurnerRoom() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)) {
                        Text("LEAVE & DESTROY")
                    }
                }
            }
        }
        
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            reverseLayout = true
        ) {
            if (uiState.activePoll != null) {
                item {
                    com.aetherweb.app.ui.screens.PollOverlay(
                        poll = uiState.activePoll!!,
                        onVote = { viewModel.votePoll(uiState.activePoll!!.id, it) },
                        onClose = { viewModel.closePoll() }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            val chronList = if (uiState.activeDmPeerId != null) {
                val peerId = uiState.activeDmPeerId!!
                val myId = com.aetherweb.app.MeshNetworkManager.localNodeId
                uiState.messages.filter { msg ->
                    // Show in DM thread if it's an exchange between me and peer
                    (msg.senderId == peerId && (msg.recipientId == myId || msg.recipientId == null)) ||
                    (msg.isFromMe && msg.recipientId == peerId)
                }
            } else {
                // Public room: show broadcast messages (messages without a private recipient or system messages)
                uiState.messages.filter { msg ->
                    msg.recipientId == null || msg.isEmergency
                }
            }
            if (chronList.isNotEmpty()) {
                item {
                    val pillLabel = if (uiState.activeDmPeerId != null) "🔒 Private 1-on-1 Direct Chat" else "Mesh Chat Room • End-to-End Local"
                    WhatsAppDatePill(pillLabel)
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
            itemsIndexed(
                items = chronList.reversed(),
                key = { index, msg -> "${msg.id}_${msg.timestamp}_$index" }
            ) { _, msg ->
                val chronIndex = chronList.indexOfFirst { it.id == msg.id }
                val prevMsg = if (chronIndex > 0) chronList[chronIndex - 1] else null
                val nextMsg = if (chronIndex != -1 && chronIndex < chronList.lastIndex) chronList[chronIndex + 1] else null

                val isSystem = msg.senderName == "System" || msg.senderId == "System"
                val isFirstInThread = if (isSystem) true else {
                    prevMsg == null || prevMsg.senderId != msg.senderId || prevMsg.senderName == "System" ||
                        (msg.timestamp - prevMsg.timestamp > 120_000L)
                }
                val isLastInThread = if (isSystem) true else {
                    nextMsg == null || nextMsg.senderId != msg.senderId || nextMsg.senderName == "System" ||
                        (nextMsg.timestamp - msg.timestamp > 120_000L)
                }

                val isMentioned = msg.message.contains("@${uiState.localUserName}", ignoreCase = true) || 
                    (uiState.localUsernameId.isNotBlank() && msg.message.contains(uiState.localUsernameId, ignoreCase = true))
                ChatBubble(
                    message = msg,
                    knownUserIds = uiState.knownUserIds,
                    isMentioned = isMentioned,
                    isFirstInThread = isFirstInThread,
                    isLastInThread = isLastInThread,
                    onUserClick = { onUserClick(msg) },
                    onSaveFile = { url -> viewModel.saveFileToDevice(context, url) },
                    onSaveImage = { url -> viewModel.saveImageToGallery(context, url) },
                    onOpenUrl = { url -> 
                        if (url.startsWith("meshgame://")) {
                            val game = url.removePrefix("meshgame://")
                            viewModel.setSharedMedia(game, "", "game", 2)
                        } else if (url == "meshfeed://open") {
                            onNavigateToFeed()
                        } else {
                            val isImage = com.aetherweb.app.MeshStorageManager.isImageFile(url)
                            if (isImage) {
                                fullScreenImageUrl = url
                            } else {
                                viewModel.downloadAndOpenFile(context, url)
                            }
                        }
                    }
                )
                if (isLastInThread) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
        var text by remember { mutableStateOf("") }
        var showPollBuilder by remember { mutableStateOf(false) }
        val fileLauncher = rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
            uri?.let { viewModel.shareFile(it) }
        }

        if (showPollBuilder) {
            PollBuilderDialog(
                onDismiss = { showPollBuilder = false },
                onStartPoll = { q, opts, uri ->
                    viewModel.startPoll(q, opts, uri)
                    showPollBuilder = false
                }
            )
        }

        ChatInput(
            text = text,
            onTextChange = { text = it },
            onSendMessage = { msg ->
                viewModel.sendMessage(
                    text = msg,
                    recipientId = uiState.activeDmPeerId
                )
                text = ""
            },
            onFilePick = { fileLauncher.launch("*/*") },
            onPollClick = { showPollBuilder = true },
            onCameraClick = {
                photoPicker.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                    )
                )
            },
            onMicClick = {
                if (isRecording) {
                    isRecording = false
                    val file = audioRecorder.stopRecording()
                    if (file != null && file.exists()) {
                        try {
                            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            viewModel.shareFile(uri)
                        } catch (e: Exception) {
                            android.util.Log.e("AudioRecorder", "Failed to get URI", e)
                            android.widget.Toast.makeText(context, "Failed to attach audio", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        isRecording = true
                        audioRecorder.startRecording()
                    } else {
                        recordPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    }
                }
            },
            isRecording = isRecording
        )
    }
}

@Composable
fun ArcadeTab(uiState: MeshState, viewModel: MeshViewModel) {
    var showPollBuilder by remember { mutableStateOf(false) }
    val ip = if (uiState.hotspotIp.isNotEmpty()) uiState.hotspotIp else "127.0.0.1"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B141B))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // TOP HEADER
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Mesh Arcade & Studio",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    "100% Offline multiplayer & creative tools",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8696A0)
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF005D4B))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("8 APPS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF25D366))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 1. HERO BENTO CARD: COLLABORATIVE SMARTBOARD STUDIO
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2C34)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF25D366).copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF25D366).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🎨", fontSize = 22.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Smartboard Studio",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                "Bézier digital ink & live laser pointer",
                                fontSize = 11.sp,
                                color = Color(0xFF8696A0)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { viewModel.setSharedMedia("canvas", "", "collaborative", 0) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Draw Together", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { viewModel.setSharedMedia("canvas", "", "broadcast", 0) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF33444D)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Presenter Mode", fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. CODE IDE — CREATE TOGETHER (SHOWCASE)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    viewModel.updateLocalSharedMedia("web", "http://$ip:8080/ide", com.aetherweb.app.MeshNetworkManager.localNodeId, "local", 0, true)
                },
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131C21)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF33444D))
        ) {
            Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF38BDF8).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("💻", fontSize = 24.sp)
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Code IDE",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        "Write HTML, JS & Python together — runs fully offline, no internet needed",
                        fontSize = 11.sp,
                        color = Color(0xFF8696A0)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. BENTO GRID 2-COLUMN TILES (CHESS & CONNECT 4)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BentoGameTile(
                icon = "♟️",
                title = "Cyber Chess",
                subtitle = "AI Bot & Mesh Online",
                badge = "2 Players",
                accentColor = Color(0xFF10B981),
                modifier = Modifier.weight(1f),
                onClick = { viewModel.setSharedMedia("chess", "", "collaborative", 2) }
            )

            BentoGameTile(
                icon = "🔴",
                title = "Connect 4",
                subtitle = "Gravity Drop AI",
                badge = "Pass & Play",
                accentColor = Color(0xFFF59E0B),
                modifier = Modifier.weight(1f),
                onClick = { viewModel.setSharedMedia("connect4", "", "collaborative", 2) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 4. BENTO GRID 2-COLUMN TILES (TIC-TAC-TOE & LUDO)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BentoGameTile(
                icon = "⭕",
                title = "Tic-Tac-Toe",
                subtitle = "Minimax AI Engine",
                badge = "Fast Match",
                accentColor = Color(0xFF38BDF8),
                modifier = Modifier.weight(1f),
                onClick = { viewModel.setSharedMedia("tictactoe", "", "collaborative", 2) }
            )

            BentoGameTile(
                icon = "🎲",
                title = "Ludo Star",
                subtitle = "4-Player Mesh Board",
                badge = "Up to 4",
                accentColor = Color(0xFFA855F7),
                modifier = Modifier.weight(1f),
                onClick = { viewModel.setSharedMedia("ludo", "", "collaborative", 4) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 5. BENTO GRID 2-COLUMN TILES (8-BALL POOL & SNAKE)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BentoGameTile(
                icon = "🎱",
                title = "Physics Pool",
                subtitle = "Live Billiards Table",
                badge = "Web & App",
                accentColor = Color(0xFF3B82F6),
                modifier = Modifier.weight(1f),
                onClick = { viewModel.updateLocalSharedMedia("web", "http://$ip:8080/pool", com.aetherweb.app.MeshNetworkManager.localNodeId, "collaborative", 2, true) }
            )

            BentoGameTile(
                icon = "🐍",
                title = "Retro Snake",
                subtitle = "Classic Offline Arcade",
                badge = "Solo Score",
                accentColor = Color(0xFF22C55E),
                modifier = Modifier.weight(1f),
                onClick = { viewModel.updateLocalSharedMedia("web", "http://$ip:8080/snake", com.aetherweb.app.MeshNetworkManager.localNodeId, "local", 0, true) }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 6. PARTY TOOLS ROW (POLL & RANDOMIZER)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BentoGameTile(
                icon = "📊",
                title = "Live Poll",
                subtitle = "Audience voting",
                badge = "Interactive",
                accentColor = Color(0xFFEC4899),
                modifier = Modifier.weight(1f),
                onClick = { showPollBuilder = true }
            )

            BentoGameTile(
                icon = "🎯",
                title = "Truth / Dare",
                subtitle = "Party challenge",
                badge = "Icebreaker",
                accentColor = Color(0xFFEAB308),
                modifier = Modifier.weight(1f),
                onClick = { viewModel.rollRandomizer() }
            )
        }

        if (showPollBuilder) {
            com.aetherweb.app.ui.screens.PollBuilderDialog(
                onDismiss = { showPollBuilder = false },
                onStartPoll = { q, opts, uri ->
                    viewModel.startPoll(q, opts, uri)
                    showPollBuilder = false
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 7. POCKET CDN FULL WIDTH TILE
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    viewModel.updateLocalSharedMedia("web", "http://$ip:8080/", com.aetherweb.app.MeshNetworkManager.localNodeId, "local", 0, true)
                },
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131C21)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF33444D))
        ) {
            Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF25D366).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🌐", fontSize = 24.sp)
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Pocket CDN & Web Portal",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        "Multi-tab offline browser with IDE, WebChat & Arcade for Wi-Fi guests",
                        fontSize = 11.sp,
                        color = Color(0xFF8696A0)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))


        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun BentoGameTile(
    icon: String,
    title: String,
    subtitle: String,
    badge: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131C21)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF33444D))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(icon, fontSize = 22.sp)
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = accentColor.copy(alpha = 0.18f)
                ) {
                    Text(
                        badge,
                        color = accentColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                subtitle,
                fontSize = 11.sp,
                color = Color(0xFF8696A0),
                maxLines = 1
            )
        }
    }
}

@Composable
fun RadarScreen(viewModel: MeshViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var isEcoMode by remember { mutableStateOf(false) }

    // On-demand hardware lifecycle: start location tracking ONLY while RadarScreen is displayed
    // and stop immediately upon exiting to preserve 100% idle battery.
    DisposableEffect(isEcoMode) {
        viewModel.startLocationTracking(context, ecoMode = isEcoMode)
        viewModel.pingRadarLocation()
        onDispose {
            viewModel.stopLocationTracking()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        com.aetherweb.app.RadarView(
            userLocations = uiState.userLocations,
            myId = MeshNetworkManager.localNodeId,
            knownUserNames = uiState.knownUsers,
            isEcoMode = isEcoMode,
            onToggleEco = { newEco -> isEcoMode = newEco },
            onRefreshLocation = { viewModel.pingRadarLocation() },
            onNodeAction = { nodeId, nodeName, action ->
                if (action == "call") {
                    val peerIp = if (uiState.isHotspotActive) "192.168.49.1" else ""
                    com.aetherweb.app.CallManager.initiateCall(nodeId, nodeName, peerIp) { payload ->
                        com.aetherweb.app.MeshNetworkManager.meshRouter.routeLocalMessage(payload)
                        com.aetherweb.app.MeshNetworkManager.webServerManager?.broadcastMessage(payload, com.aetherweb.app.MeshNetworkManager.localNodeId)
                    }
                } else if (action == "chat") {
                    viewModel.sendMessage("@$nodeName ")
                }
            }
        )
    }
}


