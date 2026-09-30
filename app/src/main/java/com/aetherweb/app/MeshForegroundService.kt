package com.aetherweb.app

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

object MeshNetworkManager {
    val _uiState = MutableStateFlow(MeshState())
    val uiState: StateFlow<MeshState> = _uiState.asStateFlow()

    val cryptoManager = CryptoManager()
    val localNodeId = CryptoManager.computeNodeId(cryptoManager.publicKeyBase64)
    val meshRouter = MeshRouter(localNodeId, cryptoManager)
    
    var roomAesKey: javax.crypto.SecretKey? = null
    var myRsaKeyPair: java.security.KeyPair? = null

    fun initializeKeys() {
        if (myRsaKeyPair == null) {
            myRsaKeyPair = CryptoManager.generateRSAKeyPair()
        }
        if (roomAesKey == null) {
            roomAesKey = CryptoManager.generateAESKey()
        }
    }

    @SuppressLint("StaticFieldLeak")
    var webServerManager: WebServerManager? = null
    @SuppressLint("StaticFieldLeak")
    var hotspotManager: HotspotManager? = null
    @SuppressLint("StaticFieldLeak")
    var bleMeshManager: BleMeshManager? = null
    @SuppressLint("StaticFieldLeak")
    var wifiSocketManager: WifiSocketManager? = null
    @SuppressLint("StaticFieldLeak")
    var highSpeedFileTransferManager: HighSpeedFileTransferManager? = null
    
    var isInitialized = false

    fun initialize(context: Context) {
        if (isInitialized) return
        isInitialized = true
        meshRouter.localNodeName = android.os.Build.MODEL

        val appContext = context.applicationContext

        webServerManager = WebServerManager(
            context = appContext,
            onClientConnected = { ip ->
                val state = _uiState.value
                if (!state.approvedSpectators.contains(ip)) {
                    if (!state.pendingSpectators.contains(ip)) {
                        _uiState.value = state.copy(pendingSpectators = state.pendingSpectators + ip)
                    }
                }
            },
            isApproved = { ip -> _uiState.value.approvedSpectators.contains(ip) },
            getChatHistory = {
                _uiState.value.messages.takeLast(50).map { Pair(it.senderName, it.message) }
            },
            onMessageReceived = msgLambda@ { text, sender ->
                try {
                    val packet = com.aetherweb.app.protocol.MeshPacketCodec.decode(text)
                    if (packet !is com.aetherweb.app.protocol.MeshPacket.Raw) {
                        val handled = com.aetherweb.app.protocol.MeshPacketDispatcher.dispatch(
                            packet = packet,
                            senderId = "web_client",
                            senderName = sender,
                            messageId = java.util.UUID.randomUUID().toString(),
                            timestamp = System.currentTimeMillis(),
                            isFromWeb = true,
                            context = appContext
                        )
                        if (handled) {
                            meshRouter.routeLocalMessage(text)
                            com.aetherweb.app.MeshNetworkManager.webServerManager?.broadcastMessage(text, sender)
                            return@msgLambda
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }

                DiagnosticLogger.log("Message Lifecycle", "Host Received", "Host phone successfully parsed the JSON payload", EventStatus.SUCCESS)
                val webMsg = ChatMessage(
                    senderName = sender,
                    senderId = "web_client",
                    message = text,
                    isFromMe = false
 )
                _uiState.update { it.copy(
                    messages = it.messages + webMsg
 ) }
                AetherFeedManager.handleIncomingChatMessage(text, sender)
                meshRouter.routeLocalMessage("${sender}: $text")
                webServerManager?.broadcastMessage(text, sender)
            }
 )

        hotspotManager = HotspotManager(appContext)
        bleMeshManager = BleMeshManager(appContext)
        wifiSocketManager = WifiSocketManager(appContext, localNodeId)
        
        initializeKeys()
        CallManager.init(appContext)
        if (highSpeedFileTransferManager == null) {
            highSpeedFileTransferManager = HighSpeedFileTransferManager(appContext).apply {
                startServer()
            }
        }
        
        wifiSocketManager?.start()
        
        InternetRelayManager.init(appContext, localNodeId, meshRouter.localPublicKey)
        
        _uiState.value = _uiState.value.copy(
            messages = listOf(
                ChatMessage(
                    senderName = "System",
                    senderId = "system",
                    message = "Network Ready.",
                    isFromMe = false
 )
 )
 )
    }
}

class MeshForegroundService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    fun pulseWakeLock(durationMs: Long = 10_000L) {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock?.acquire(durationMs)
        } catch (e: Exception) {}
    }

    private fun updateWifiLockState(shouldHold: Boolean) {
        try {
            if (shouldHold) {
                if (wifiLock?.isHeld != true) {
                    wifiLock?.acquire()
                    Log.d("MeshForegroundService", "Acquired WifiLock for active high-speed networking")
                }
            } else {
                if (wifiLock?.isHeld == true) {
                    wifiLock?.release()
                    Log.d("MeshForegroundService", "Released WifiLock to conserve battery")
                }
            }
        } catch (e: Exception) {}
    }

    override fun onCreate() {
        super.onCreate()
        
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "MeshChatServiceChannel")
            .setContentTitle("MeshChat Network Alive")
            .setContentText("Routing messages in the background...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(1, notification)
        }
        
        scope.launch {
            try {
                MeshNetworkManager.initialize(applicationContext)

                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MeshChat::WakeLock")
                wakeLock?.acquire(15_000L) // 15-second initialization wake lock, then release to deep sleep

                val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, "MeshChat::WifiLock")

                startObserving()
            } catch (e: Exception) {
                Log.e("MeshForegroundService", "Initialization error", e)
            }
        }
    }

    private fun startObserving() {
        val manager = MeshNetworkManager
        
        scope.launch {
            var lastIp = ""
            while (true) {
                val ip = NetworkUtils.getLocalIpAddress()
                if (ip != lastIp) {
                    if (ip != "127.0.0.1" && ip.isNotEmpty()) {
                        DiagnosticLogger.log("Network", "IP Assignment", "OS granted valid local IP: $ip", EventStatus.SUCCESS)
                    } else if (ip == "127.0.0.1") {
                        DiagnosticLogger.log("Network", "IP Assignment", "No external IP, using loopback (127.0.0.1)", EventStatus.INFO)
                    }
                    lastIp = ip
                }
                com.aetherweb.app.MeshNetworkManager._uiState.value = com.aetherweb.app.MeshNetworkManager._uiState.value.copy(hotspotIp = ip)
                kotlinx.coroutines.delay(3000)
            }
        }
        
        scope.launch {
            InternetRelayManager.inboundRelayMessages.collect { envelope ->
                try {
                    val payload = envelope.optString("payload")
                    val fromMailbox = envelope.optString("fromMailbox")
                    val fromNodeId = envelope.optString("fromNodeId", fromMailbox)
                    val fromPublicKey = envelope.optString("fromPublicKey", "")

                    if (payload.startsWith("{")) {
                        val obj = org.json.JSONObject(payload)
                        val type = obj.optString("type")
                        if (type == "delivery_ack") {
                            val ackMsgId = obj.optString("originalMsgId")
                            com.aetherweb.app.MeshNetworkManager._uiState.update { state ->
                                state.copy(
                                    messages = state.messages.map { msg ->
                                        if (msg.id == ackMsgId) msg.copy(deliveryStatus = DeliveryStatus.DELIVERED) else msg
                                    }
                                )
                            }
                            return@collect
                        }
                    }

                    val networkMessage = com.aetherweb.app.MeshRouter.NetworkMessage(
                        messageId = envelope.optString("msgId", java.util.UUID.randomUUID().toString()),
                        senderId = fromNodeId,
                        senderName = envelope.optString("fromName", "Remote Node"),
                        ttl = 1,
                        payload = payload,
                        timestamp = envelope.optLong("timestamp", System.currentTimeMillis()),
                        signature = "",
                        publicKey = fromPublicKey
                    )
                    manager.meshRouter.processIncomingPacket(networkMessage)
                } catch (e: Exception) {
                    Log.e("MeshForegroundService", "Error handling inbound relay envelope", e)
                }
            }
        }
        
        scope.launch {
            manager.meshRouter.incomingMessages.collect { networkMessage ->
                DiagnosticLogger.logMeshToWebHop(networkMessage.messageId, networkMessage.senderId, networkMessage.payload)
                var handledSpecial = false
                try {
                    val packet = com.aetherweb.app.protocol.MeshPacketCodec.decode(networkMessage.payload)
                    handledSpecial = com.aetherweb.app.protocol.MeshPacketDispatcher.dispatch(
                        packet = packet,
                        senderId = networkMessage.senderId,
                        senderName = networkMessage.senderName,
                        messageId = networkMessage.messageId,
                        timestamp = networkMessage.timestamp,
                        isFromWeb = false,
                        context = applicationContext
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                com.aetherweb.app.MeshNetworkManager.webServerManager?.broadcastMessage(
                    networkMessage.payload,
                    if (networkMessage.senderName.isNotBlank()) networkMessage.senderName else "Peer_${networkMessage.senderId.take(4)}"
                )

                if (!handledSpecial) {
                    val newChatMessage = ChatMessage(
                        id = networkMessage.messageId,
                        senderName = if (networkMessage.senderName.isNotBlank()) networkMessage.senderName else "Peer_${networkMessage.senderId.take(4)}",
                        senderId = networkMessage.senderId,
                        message = networkMessage.payload,
                        isFromMe = false,
                        timestamp = networkMessage.timestamp
                    )
                    com.aetherweb.app.MeshNetworkManager._uiState.update { it.copy(
                        messages = it.messages + newChatMessage
                    ) }
                    AetherFeedManager.handleIncomingChatMessage(newChatMessage.message, newChatMessage.senderName)
                    val isGhostMode = com.aetherweb.app.MeshNetworkManager._uiState.value.isGhostMode
                    if (!isGhostMode) {
                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            try {
                                com.aetherweb.app.data.MeshChatDatabase.getDatabase(applicationContext).chatDao().insertMessage(
                                    com.aetherweb.app.data.ChatMessageEntity(
                                        senderId = newChatMessage.senderId,
                                        senderName = newChatMessage.senderName,
                                        message = newChatMessage.message,
                                        timestamp = newChatMessage.timestamp,
                                        isFromMe = false
                                    )
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        if (!MainActivity.isAppInForeground) {
                            NotificationHelper.showMessageNotification(
                                context = applicationContext,
                                senderName = newChatMessage.senderName,
                                message = newChatMessage.message,
                                senderId = newChatMessage.senderId,
                                isGroup = true,
                                groupTitle = "Mesh & WebChat",
                                isFromWeb = false
                            )
                        }
                    }
                }
                DiagnosticLogger.logMeshToWebHop(networkMessage.messageId, networkMessage.senderId, networkMessage.payload)
            }
        }

        scope.launch {
            manager.meshRouter.outboundBroadcasts.collect { networkMessage ->
                try {
                    pulseWakeLock(8_000L) // Keep CPU awake during packet dispatch
                    val hasWifi = manager.wifiSocketManager?.hasActiveConnections() == true || MeshNetworkManager._uiState.value.isHotspotActive
                    updateWifiLockState(hasWifi)

                    val jsonObj = org.json.JSONObject().apply {
                        put("messageId", networkMessage.messageId)
                        put("senderId", networkMessage.senderId)
                        put("senderName", networkMessage.senderName)
                        put("ttl", networkMessage.ttl)
                        put("payload", networkMessage.payload)
                        put("timestamp", networkMessage.timestamp)
                        put("nonce", networkMessage.nonce)
                        put("signature", networkMessage.signature)
                        put("publicKey", networkMessage.publicKey)
                    }
                    val json = jsonObj.toString()
                    val safePayloadPreview = if (networkMessage.payload.length > 80) networkMessage.payload.take(80) + "..." else networkMessage.payload
                    
                    val originSource = if (networkMessage.senderId == manager.localNodeId) "Local App -> Mesh" else "Web/Other -> Mesh"

                    if (manager.wifiSocketManager?.hasActiveConnections() == true) {
                        DiagnosticLogger.logHop(originSource, networkMessage.messageId, "Interface: Wi-Fi TCP, Payload: $safePayloadPreview")
                        DiagnosticLogger.log("Message Lifecycle", "Wi-Fi Broadcast", "Attempting to send to all connected TCP sockets", EventStatus.PENDING)
                        manager.wifiSocketManager?.broadcastPacket(json)
                        DiagnosticLogger.log("Message Lifecycle", "Wi-Fi Broadcast", "Successfully broadcast to TCP sockets", EventStatus.SUCCESS)
                    } else {
                        DiagnosticLogger.log("Message Lifecycle", "Wi-Fi Broadcast", "No active TCP connections to broadcast to", EventStatus.INFO)
                    }

                    // SMART ROUTING LOGIC
                    val isHighBandwidth = networkMessage.payload.contains("\"type\":\"call_offer\"") || 
                                          networkMessage.payload.contains("\"type\":\"call_answer\"") || 
                                          networkMessage.payload.contains("\"type\":\"call_audio\"") ||
                                          networkMessage.payload.contains("\"type\":\"shared_media\"") ||
                                          networkMessage.payload.length > 1024

                    if (isHighBandwidth && manager.wifiSocketManager?.hasActiveConnections() == true) {
                        DiagnosticLogger.log("Message Lifecycle", "Smart Routing", "High-bandwidth packet detected. Bypassing BLE to prevent congestion. Using Wi-Fi exclusively.", EventStatus.INFO)
                    } else {
                        scope.launch {
                            DiagnosticLogger.logHop(originSource, networkMessage.messageId, "Interface: BLE Mesh, Payload: $safePayloadPreview")
                            DiagnosticLogger.log("Message Lifecycle", "BLE Broadcast", "Attempting to send to nearby Bluetooth nodes", EventStatus.PENDING)
                            manager.bleMeshManager?.broadcastPacket(json)
                            DiagnosticLogger.log("Message Lifecycle", "BLE Broadcast", "Successfully broadcast via BLE GATT", EventStatus.SUCCESS)
                            kotlinx.coroutines.delay(2500)
                        }
                    }
                } catch(e: Exception) {
                    Log.e("MeshForeground", "Error broadcasting outbound packet", e)
                    DiagnosticLogger.log("Message Lifecycle", "Broadcast Error", "Error broadcasting outbound packet: ${e.message}", EventStatus.ERROR)
                }
            }
        }
        scope.launch {
            var currentBleNodes = emptyList<MeshNode>()
            var currentWifiNodes = emptyMap<String, String>()

            fun updateCombinedNodes() {
                val wifiNodes = currentWifiNodes.map { (ip, name) -> 
                    MeshNode(id = ip, name = name, signalStrength = 100) 
                }
                val combined = (currentBleNodes + wifiNodes).distinctBy { it.id }
                val isWifi = currentWifiNodes.isNotEmpty()
                com.aetherweb.app.MeshNetworkManager._uiState.value = com.aetherweb.app.MeshNetworkManager._uiState.value.copy(
                    connectedNodes = combined,
                    isWifiConnected = isWifi
                )
                // Battery Optimization: If Wi-Fi is connected, adapt BLE duty cycle to ECO_SAVER (10% duty cycle)
                if (isWifi) {
                    manager.bleMeshManager?.setDutyCycleMode(com.aetherweb.app.BleMeshManager.BleDutyCycle.ECO_SAVER)
                } else if (!com.aetherweb.app.MeshNetworkManager._uiState.value.isEmergencyMode) {
                    manager.bleMeshManager?.setDutyCycleMode(com.aetherweb.app.BleMeshManager.BleDutyCycle.BALANCED)
                }
            }

            // Storage Footprint: Enforce 100MB threshold with periodic automatic background pruner sweeps
            scope.launch {
                while (isActive) {
                    try {
                        val result = com.aetherweb.app.MeshStorageManager.pruneCacheSweep(applicationContext)
                        val summary = "Cache: ${com.aetherweb.app.MeshStorageManager.formatFileSize(result.currentCacheSizeBytes)} / 100 MB (${result.currentFileCount} files)"
                        com.aetherweb.app.MeshNetworkManager._uiState.update { 
                            it.copy(
                                cacheSizeBytes = result.currentCacheSizeBytes,
                                cacheFileCount = result.currentFileCount,
                                lastStorageSweepSummary = summary
                            )
                        }
                    } catch (e: Exception) {
                        Log.e("MeshForegroundService", "Cache sweep failed", e)
                    }
                    kotlinx.coroutines.delay(15 * 60 * 1000L) // Automatic sweep every 15 minutes
                }
            }

            scope.launch {
                manager.bleMeshManager?.activeNodes?.collect { nodes ->
                    currentBleNodes = nodes
                    updateCombinedNodes()
                }
            }
            
            scope.launch {
                manager.wifiSocketManager?.activeWifiNodes?.collect { nodesMap ->
                    currentWifiNodes = nodesMap
                    updateCombinedNodes()
                }
            }
        }


        scope.launch {
            manager.hotspotManager?.hotspotState?.collect { hotspotInfo ->
                com.aetherweb.app.MeshNetworkManager._uiState.value = com.aetherweb.app.MeshNetworkManager._uiState.value.copy(
                    isHotspotActive = hotspotInfo.isActive,
                    hotspotSsid = hotspotInfo.ssid,
                    hotspotPassword = hotspotInfo.password,
                    hotspotError = hotspotInfo.error
 )
                if (hotspotInfo.isActive) {
                    scope.launch {
                        var newIp = NetworkUtils.getLocalIpAddress()
                        var retries = 0
                        while (newIp == "127.0.0.1" && retries < 15) {
                            kotlinx.coroutines.delay(1000)
                            newIp = NetworkUtils.getLocalIpAddress()
                            retries++
                        }
                        com.aetherweb.app.MeshNetworkManager._uiState.value = com.aetherweb.app.MeshNetworkManager._uiState.value.copy(hotspotIp = newIp)
                        com.aetherweb.app.LocalDnsServer.start(newIp)
                        if (newIp != "127.0.0.1") {
                            // Phase 3: Initialize local cluster topology
                            WifiClusterBridgeManager.initializeLocalCluster(manager.localNodeId, newIp)

                            val hs = JSONObject()
                            hs.put("type", "sys_handshake")
                            hs.put("ssid", hotspotInfo.ssid)
                            hs.put("pwd", hotspotInfo.password)
                            hs.put("ip", newIp)
                            hs.put("isPrivate", com.aetherweb.app.MeshNetworkManager.uiState.value.isPrivateRoom)
                            com.aetherweb.app.MeshNetworkManager.meshRouter.routeLocalMessage(hs.toString())
                        }
                        
                        com.aetherweb.app.MeshNetworkManager.webServerManager?.stopServer()
                        kotlinx.coroutines.delay(1000)
                        com.aetherweb.app.MeshNetworkManager.webServerManager?.startServer()
                        com.aetherweb.app.MeshNetworkManager._uiState.update { it.copy(
                            messages = it.messages + ChatMessage(
                                senderName = "System",
                                senderId = "system",
                                message = "Local Hook Active. Web Server is running on " + newIp + ". Tell others to scan QR code to connect.",
                                isFromMe = false
                            ),
                            isWebServerRunning = true
                        ) }
                    }
                } else {
                    // Ensure it is running even without hotspot
                    com.aetherweb.app.MeshNetworkManager.webServerManager?.startServer(8080)
                    com.aetherweb.app.MeshNetworkManager._uiState.update { it.copy(isWebServerRunning = true) }
                }
            }
        }

        scope.launch {
            manager.bleMeshManager?.incomingPackets?.collect { json ->
                processIncomingJson(json)
            }
        }
        scope.launch {
            manager.wifiSocketManager?.incomingPackets?.collect { json ->
                processIncomingJson(json)
            }
        }

        // Phase 3: Periodic Cluster Topology Synchronization (every 20s)
        scope.launch {
            while (isActive) {
                try {
                    val currentState = MeshNetworkManager.uiState.value
                    val currentIp = currentState.hotspotIp.ifEmpty { NetworkUtils.getLocalIpAddress() }
                    if (currentIp != "127.0.0.1") {
                        val activePeers = manager.wifiSocketManager?.activeConnections?.size ?: 0
                        val beaconJson = WifiClusterBridgeManager.createClusterBeacon(
                            localNodeId = manager.localNodeId,
                            currentIp = currentIp,
                            isHost = currentState.isHotspotActive,
                            peerCount = activePeers
                        )
                        // Broadcast over UDP & Wi-Fi sockets
                        manager.wifiSocketManager?.broadcastPacket(beaconJson)
                    }
                } catch (e: Exception) {}
                kotlinx.coroutines.delay(20000L)
            }
        }
    }

    private fun processIncomingJson(json: String) {
        pulseWakeLock(8_000L) // Ensure CPU completes processing and routing before sleeping
        if (json.startsWith("SYS_WIFI_PEER_JOINED:")) {
            val ip = json.removePrefix("SYS_WIFI_PEER_JOINED:")
            // We are the host, let's sync state to this new peer
            val currentState = MeshNetworkManager._uiState.value
            if (currentState.isHotspotActive) {
                // Send chat history (last 50 messages)
                val recentMessages = currentState.messages.takeLast(50)
                val syncObj = JSONObject().apply {
                    put("type", "sys_state_sync")
                    val msgArray = org.json.JSONArray()
                    recentMessages.forEach { msg ->
                        val mObj = JSONObject().apply {
                            put("id", msg.id)
                            put("senderId", msg.senderId)
                            put("senderName", msg.senderName)
                            put("message", msg.message)
                            put("timestamp", msg.timestamp)
                        }
                        msgArray.put(mObj)
                    }
                    put("messages", msgArray)
                    
                    // Add known users
                    val usersObj = JSONObject()
                    currentState.knownUsers.forEach { (k, v) -> usersObj.put(k, v) }
                    put("users", usersObj)
                }
                
                MeshNetworkManager.meshRouter.routeLocalMessage(syncObj.toString())
                MeshNetworkManager.meshRouter.routeLocalMessage("{\"type\":\"sys_sync_request\"}")
            }
            return
        } else if (json == "SYS_ROOM_CLOSED") {
            MeshNetworkManager._uiState.update { 
                it.copy(
                    isEmergencyMode = false,
                    messages = it.messages + ChatMessage(id = java.util.UUID.randomUUID().toString(), senderId = "System", senderName = "System", message = "The Host has closed the room. Disconnected.", timestamp = System.currentTimeMillis(), isFromMe = false)
                )
            }
            return
        } else if (json.startsWith("SYS_TRIGGER_SECONDARY_CLUSTER:")) {
            // Phase 3: Autonomous Cluster-of-Clusters Topology
            // If primary cluster saturated and this node is not hosting, trigger secondary cluster
            val currentState = MeshNetworkManager._uiState.value
            if (!currentState.isHotspotActive && !currentState.isWifiConnected) {
                Log.i("MeshService", "Triggering secondary cluster expansion (Cluster Beta)")
                DiagnosticLogger.log("Cluster Expansion", "Autonomous Hotspot", "Starting secondary hotspot to bridge saturated cluster", EventStatus.SUCCESS)
                MeshNetworkManager.hotspotManager?.startHotspot()
            }
            return
        }

        try {
            val obj = JSONObject(json)
            if (obj.has("type") && obj.getString("type") == "cluster_forward") {
                val targetNodeId = obj.getString("targetNodeId")
                val payload = obj.getString("payload")
                // If we are a bridge node and have a fast route to targetNodeId, forward immediately over Wi-Fi
                if (WifiClusterBridgeManager.hasFastWifiRoute(targetNodeId)) {
                    val fastIp = WifiClusterBridgeManager.getFastRouteIp(targetNodeId)
                    if (fastIp != null) {
                        MeshNetworkManager.wifiSocketManager?.broadcastPacket(payload)
                        Log.i("MeshService", "Bridge relay forwarded packet to target node $targetNodeId at $fastIp")
                    }
                }
                return
            }
            if (obj.has("type") && obj.getString("type") == "handshake") {
                val ssid = obj.getString("ssid")
                val pwd = obj.getString("pwd")
                val ip = obj.getString("ip")
                val currentState = MeshNetworkManager._uiState.value
                MeshNetworkManager._uiState.value = currentState.copy(
                    discoveredHostSsid = ssid,
                    discoveredHostPwd = pwd,
                    discoveredHostIp = ip
                )
                
                // BitChat-style BLE pulse beacon: Auto-join rooms without scanning QR if auto-join is enabled & room is public
                if (currentState.autoJoinBeaconEnabled && 
                    !currentState.isHotspotActive && 
                    !currentState.isWifiConnected && 
                    pwd != "PRIVATE") {
                    Log.i("MeshService", "BitChat pulse beacon received for $ssid - auto-joining...")
                    DiagnosticLogger.log("BLE Pulse Beacon", "Auto-Join", "Auto-joining mesh room $ssid without QR scan", EventStatus.SUCCESS)
                    MeshNetworkManager.hotspotManager?.connectToHotspot(ssid, pwd, ip)
                }
                return
            }
            val msg = MeshRouter.NetworkMessage(
                messageId = obj.getString("messageId"),
                senderId = obj.getString("senderId"),
                senderName = obj.optString("senderName", ""),
                ttl = obj.getInt("ttl"),
                payload = obj.getString("payload"),
                timestamp = obj.getLong("timestamp"),
                nonce = obj.optLong("nonce", 0L),
                signature = obj.optString("signature", ""),
                publicKey = obj.optString("publicKey", "")
            )
            MeshNetworkManager.meshRouter.processIncomingPacket(msg)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "MeshChatServiceChannel",
                "MeshChat Background Service",
                NotificationManager.IMPORTANCE_LOW
 )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "PULSE_BLE_CHUNK") {
            val chunk = intent.getStringExtra("chunk")
            if (chunk != null) {
                MeshNetworkManager.bleMeshManager?.pulseBleChunk(chunk)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (e: Exception) {}
        try { if (wifiLock?.isHeld == true) wifiLock?.release() } catch (e: Exception) {}
        wakeLock = null
        wifiLock = null
        scope.cancel()
        MeshNetworkManager.hotspotManager?.stopHotspot()
        com.aetherweb.app.LocalDnsServer.stop()
        MeshNetworkManager.webServerManager?.stopServer()
        MeshNetworkManager.bleMeshManager?.stopScanning()
        MeshNetworkManager.bleMeshManager?.stopAdvertising()
        MeshNetworkManager.wifiSocketManager?.stop()
        super.onDestroy()
    }
}
