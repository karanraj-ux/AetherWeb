package com.aetherweb.app

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

class WifiSocketManager(
    private val context: Context,
    private val localNodeId: String
) {
    private val TCP_PORT = 8888
    private val UDP_PORT = 8889
    private val SERVICE_TYPE = "_meshchat._tcp."
    private val SERVICE_NAME = "MeshChat_${localNodeId.take(4)}"

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    
    private var serverSocket: ServerSocket? = null
    private var udpSocket: DatagramSocket? = null
    private var multicastLock: android.net.wifi.WifiManager.MulticastLock? = null

    
    // Connected peers: map of IP address to Socket and PrintWriter
    val activeConnections = ConcurrentHashMap<String, Pair<Socket, PrintWriter>>()
    val activeWifiNodes = kotlinx.coroutines.flow.MutableStateFlow<Map<String, String>>(emptyMap())
    private val peerNames = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val lastHeartbeatMap = ConcurrentHashMap<String, Long>()

    private val _incomingPackets = MutableSharedFlow<String>(extraBufferCapacity = 50)
    val incomingPackets = _incomingPackets.asSharedFlow()

    private val nsdManager: NsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun start() {
        MeshNetworkManager.initializeKeys()
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            multicastLock = wifiManager.createMulticastLock("MeshChat:MulticastLock")
            multicastLock?.setReferenceCounted(true)
            multicastLock?.acquire()
        } catch (e: Exception) {}
        
        startTcpServer()
        startUdpDiscovery()
        registerService()
        discoverServices()
        pollGateway()
        startHeartbeatEngine()
    }

    private fun startHeartbeatEngine() {
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                val now = System.currentTimeMillis()
                
                // Adaptive Duty Cycle for Wi-Fi Socket Keep-Alives:
                // Only send UDP ping if no traffic was exchanged in the last 10 seconds.
                try {
                    val pingMsg = "SYS_HEARTBEAT_PING:${NetworkUtils.getLocalIpAddress()}".toByteArray()
                    activeConnections.keys.forEach { ip ->
                        val lastSeen = lastHeartbeatMap[ip] ?: 0L
                        if (now - lastSeen >= 10000) {
                            val packet = DatagramPacket(pingMsg, pingMsg.size, java.net.InetAddress.getByName(ip), UDP_PORT)
                            udpSocket?.send(packet)
                        }
                    }
                } catch (e: Exception) {}
                
                activeConnections.keys.toList().forEach { ip ->
                    // Check timeout (adaptive 30s threshold)
                    val lastSeen = lastHeartbeatMap[ip] ?: now
                    if (now - lastSeen > 30000) {
                        Log.d("WifiSocketManager", "Peer $ip timed out (30s). Disconnecting.")
                        disconnectClient(ip)
                    }
                }
                
                kotlinx.coroutines.delay(12000) // Adaptive 12s heartbeat interval to conserve battery
            }
        }
    }

    private fun startUdpDiscovery() {
        scope.launch {
            try {
                udpSocket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(java.net.InetSocketAddress(UDP_PORT))
                    broadcast = true
                }
                
                // Receiver loop
                launch {
                    val buffer = ByteArray(1024)
                    while (isActive) {
                        try {
                            val packet = DatagramPacket(buffer, buffer.size)
                            udpSocket?.receive(packet)
                            val message = String(packet.data, 0, packet.length)
                            val senderIp = packet.address.hostAddress
                            if (message.startsWith("MESH_DISCOVERY:") && senderIp != null) {
                                val port = message.split(":")[1].toIntOrNull() ?: TCP_PORT
                                if (!activeConnections.containsKey(senderIp) && senderIp != NetworkUtils.getLocalIpAddress()) {
                                    connectToPeer(senderIp, port)
                                }
                            } else if (message.startsWith("SYS_HEARTBEAT_PING:") && senderIp != null) {
                                lastHeartbeatMap[senderIp] = System.currentTimeMillis()
                                val pongMsg = "SYS_HEARTBEAT_PONG:${NetworkUtils.getLocalIpAddress()}".toByteArray()
                                val pongPacket = DatagramPacket(pongMsg, pongMsg.size, java.net.InetAddress.getByName(senderIp), UDP_PORT)
                                udpSocket?.send(pongPacket)
                            } else if (message.startsWith("SYS_HEARTBEAT_PONG:") && senderIp != null) {
                                lastHeartbeatMap[senderIp] = System.currentTimeMillis()
                            } else if (message.startsWith("{\"type\":\"cluster_beacon\"") && senderIp != null) {
                                val shouldExpand = WifiClusterBridgeManager.handleClusterBeacon(message, senderIp)
                                if (shouldExpand) {
                                    _incomingPackets.tryEmit("SYS_TRIGGER_SECONDARY_CLUSTER:$senderIp")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("WifiSocketManager", "UDP Receive error", e)
                        }
                    }
                }
                
                // Sender loop
                launch {
                    while (isActive) {
                        try {
                            if (udpSocket != null) {
                                val msg = "MESH_DISCOVERY:$TCP_PORT"
                                val data = msg.toByteArray()
                                val packet = DatagramPacket(data, data.size, InetAddress.getByName("255.255.255.255"), UDP_PORT)
                                udpSocket?.send(packet)
                                
                                // Also try common subnet broadcasts
                                val localIp = NetworkUtils.getLocalIpAddress()
                                if (localIp != "127.0.0.1") {
                                    val parts = localIp.split(".")
                                    if (parts.size == 4) {
                                        val subnetBcast = "${parts[0]}.${parts[1]}.${parts[2]}.255"
                                        val subnetPacket = DatagramPacket(data, data.size, InetAddress.getByName(subnetBcast), UDP_PORT)
                                        udpSocket?.send(subnetPacket)
                                    }
                                }
                            }
                        } catch (e: Exception) {}
                        kotlinx.coroutines.delay(3000)
                    }
                }
            } catch (e: Exception) {
                Log.e("WifiSocketManager", "UDP Setup error", e)
            }
        }
    }

    fun connectToPeer(ip: String, port: Int, network: android.net.Network? = null) {
        if (activeConnections.containsKey(ip)) return
        scope.launch {
            try {
                Log.d("WifiSocketManager", "Connecting to discovered peer: $ip:$port")
                val socket = Socket()
                network?.bindSocket(socket)
                socket.connect(java.net.InetSocketAddress(ip, port), 2000)
                handleNewConnection(ip, socket)
            } catch (e: Exception) {}
        }
    }
    private fun pollGateway() {
        scope.launch {
            while (isActive) {
                if (activeConnections.isEmpty()) {
                    try {
                        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
                        val dhcp = wifiManager.dhcpInfo
                        if (dhcp != null) {
                            val gateways = listOf(dhcp.gateway, dhcp.serverAddress)
                            for (g in gateways) {
                                if (g != 0) {
                                    val ipBytes = byteArrayOf(
                                        (g and 0xff).toByte(),
                                        (g shr 8 and 0xff).toByte(),
                                        (g shr 16 and 0xff).toByte(),
                                        (g shr 24 and 0xff).toByte()
                                    )
                                    val ip = java.net.InetAddress.getByAddress(ipBytes).hostAddress
                                    if (ip != null && ip != "0.0.0.0" && ip != "127.0.0.1" && !activeConnections.containsKey(ip)) {
                                        connectToPeer(ip, TCP_PORT)
                                    }
                                }
                            }
                        }
                        
                        val commonHotspotIps = listOf("192.168.43.1", "192.168.49.1", "192.168.1.1", "192.168.0.1", "10.0.0.1")
                        for (hip in commonHotspotIps) {
                            if (!activeConnections.containsKey(hip) && hip != NetworkUtils.getLocalIpAddress()) {
                                connectToPeer(hip, TCP_PORT)
                            }
                        }
                        
                        val myIp = NetworkUtils.getLocalIpAddress()
                        if (myIp != "127.0.0.1") {
                            val parts = myIp.split(".")
                            if (parts.size == 4) {
                                val possibleGateways = listOf(
                                    "${parts[0]}.${parts[1]}.${parts[2]}.1",
                                    "${parts[0]}.${parts[1]}.${parts[2]}.254",
                                    "${parts[0]}.${parts[1]}.${parts[2]}.43"
                                )
                                for (pg in possibleGateways) {
                                    if (!activeConnections.containsKey(pg) && pg != myIp) {
                                        connectToPeer(pg, TCP_PORT)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {}
                }
                kotlinx.coroutines.delay(5000)
            }
        }
    }


    private fun startTcpServer() {
        scope.launch {
            try {
                if (MeshNetworkManager.roomAesKey == null) {
                    MeshNetworkManager.roomAesKey = CryptoManager.generateAESKey()
                }
                serverSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(java.net.InetSocketAddress(TCP_PORT))
                }
                Log.d("WifiSocketManager", "TCP Server listening on $TCP_PORT")
                while (isActive) {
                    val socket = serverSocket?.accept() ?: break
                    val clientIp = socket.inetAddress.hostAddress ?: "Unknown"
                    handleNewConnection(clientIp, socket)
                }
            } catch (e: Exception) {
                Log.e("WifiSocketManager", "TCP Server error", e)
            }
        }
    }

    private fun registerService() {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = SERVICE_NAME
            serviceType = SERVICE_TYPE
            port = TCP_PORT
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {}
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceUnregistered(arg0: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }

        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {}
    }

    private fun discoverServices() {
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType == SERVICE_TYPE && service.serviceName != SERVICE_NAME) {
                    try {
                        nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                                val host = serviceInfo.host
                                val port = serviceInfo.port
                                if (host != null && host.hostAddress != NetworkUtils.getLocalIpAddress()) {
                                    connectToPeer(host.hostAddress!!, port)
                                }
                            }
                        })
                    } catch (e: Exception) {}
                }
            }
            override fun onServiceLost(service: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                try { nsdManager.stopServiceDiscovery(this) } catch (e: Exception) {}
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {}
    }
    private fun handleNewConnection(ip: String, socket: Socket) {
        scope.launch {
            try {
                val out = PrintWriter(socket.getOutputStream(), true)
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                
                activeConnections[ip] = Pair(socket, out)
                lastHeartbeatMap[ip] = System.currentTimeMillis()
                val myName = android.os.Build.MODEL
                val rsaPair = MeshNetworkManager.myRsaKeyPair ?: run {
                    MeshNetworkManager.initializeKeys()
                    MeshNetworkManager.myRsaKeyPair ?: CryptoManager.generateRSAKeyPair()
                }
                val myPubKey = CryptoManager.getPublicKeyAsString(rsaPair.public)
                out.println("WIFI_HANDSHAKE_V2:$myName:$myPubKey")
                
                if (!peerNames.containsKey(ip)) peerNames[ip] = ip
                activeWifiNodes.value = peerNames.toMap()
                DiagnosticLogger.log("WiFi Direct", "Connected", "Peer connected from $ip", EventStatus.SUCCESS)

                // Register in WifiClusterBridgeManager routing table
                val currentClusterId = WifiClusterBridgeManager.bridgeState.value.currentClusterId
                WifiClusterBridgeManager.registerPeerRoute(
                    nodeId = ip,
                    ipAddress = ip,
                    port = TCP_PORT,
                    clusterId = currentClusterId,
                    isDirect = true,
                    hops = 1
                )
                WifiClusterBridgeManager.checkClusterCapacity(activeConnections.size, MeshNetworkManager.uiState.value.isHotspotActive)
                
                while (isActive) {
                    val line = reader.readLine() ?: break
                    lastHeartbeatMap[ip] = System.currentTimeMillis()
                    
                    if (line == "SYS_HEARTBEAT_PING") {
                        out.println("SYS_HEARTBEAT_PONG")
                    } else if (line == "SYS_HEARTBEAT_PONG") {
                        // Already updated lastHeartbeatMap above
                    } else if (line.startsWith("WIFI_HANDSHAKE_V2:")) {
                        val parts = line.split(":", limit = 3)
                        if (parts.size == 3) {
                            val peerName = parts[1]
                            val peerPubKeyBase64 = parts[2]
                            peerNames[ip] = peerName
                            activeWifiNodes.value = peerNames.toMap()
                            
                            val aesKey = MeshNetworkManager.roomAesKey
                            if (aesKey != null && MeshNetworkManager.uiState.value.isHotspotActive) {
                                // Host puts peer in Waiting Room
                                val pendingUser = com.aetherweb.app.PendingUser(java.util.UUID.randomUUID().toString(), peerName, peerPubKeyBase64, ip)
                                MeshNetworkManager._uiState.update { it.copy(pendingUsers = it.pendingUsers + pendingUser) }
                                out.println("SYS_WAITING_FOR_APPROVAL")
                            } else {
                                // If not Host, just acknowledge.
                                _incomingPackets.tryEmit("SYS_WIFI_PEER_JOINED:$ip")
                            }
                        }
                    } else if (line.startsWith("SYS_WIFI_AES_KEY:")) {
                        val encryptedAes = line.removePrefix("SYS_WIFI_AES_KEY:")
                        try {
                            val decryptedAesBytes = CryptoManager.decryptRSA(encryptedAes, MeshNetworkManager.myRsaKeyPair!!.private)
                            val exportedAes = String(decryptedAesBytes)
                            MeshNetworkManager.roomAesKey = CryptoManager.importAESKey(exportedAes)
                            Log.d("WifiSocketManager", "Successfully received and imported Room AES Key")
                        } catch (e: Exception) {
                            Log.e("WifiSocketManager", "Failed to decrypt AES key from peer", e)
                        }
                    } else if (line.startsWith("WIFI_HANDSHAKE:")) {
                        peerNames[ip] = line.removePrefix("WIFI_HANDSHAKE:")
                        activeWifiNodes.value = peerNames.toMap()
                        _incomingPackets.tryEmit("SYS_WIFI_PEER_JOINED:$ip")
                    } else {
                        _incomingPackets.tryEmit(line)
                    }
                }
            } catch (e: Exception) {
            } finally {
                disconnectClient(ip)
            }
        }
    }

    fun hasActiveConnections(): Boolean {
        return activeConnections.isNotEmpty()
    }

    fun approvePeer(ip: String, peerPubKeyBase64: String) {
        scope.launch {
            val out = activeConnections[ip]?.second
            val aesKey = MeshNetworkManager.roomAesKey
            if (out != null && aesKey != null) {
                try {
                    val peerPubKey = CryptoManager.getPublicKeyFromString(peerPubKeyBase64)
                    val exportedAes = CryptoManager.exportAESKey(aesKey)
                    val encryptedAes = CryptoManager.encryptRSA(exportedAes.toByteArray(), peerPubKey)
                    out.println("SYS_WIFI_AES_KEY:$encryptedAes")
                    _incomingPackets.tryEmit("SYS_WIFI_PEER_JOINED:$ip")
                } catch (e: Exception) {
                    Log.e("WifiSocketManager", "Failed to encrypt AES key for peer", e)
                }
            }
        }
    }

    fun disconnectClient(ip: String) {
        activeConnections[ip]?.first?.let {
            try { it.close() } catch (e: Exception) {}
        }
        activeConnections.remove(ip)
        peerNames.remove(ip)
        lastHeartbeatMap.remove(ip)
        activeWifiNodes.value = peerNames.toMap()
        WifiClusterBridgeManager.removePeerRoute(ip)
        WifiClusterBridgeManager.checkClusterCapacity(activeConnections.size, MeshNetworkManager.uiState.value.isHotspotActive)
    }

    fun broadcastPacket(payload: String) {
        val deadIps = mutableListOf<String>()
        activeConnections.forEach { (ip, pair) ->
            try {
                val safePayload = payload.replace("\n", "")
                pair.second.println(safePayload)
                if (pair.second.checkError()) {
                    deadIps.add(ip)
                }
            } catch (e: Exception) {
                deadIps.add(ip)
            }
        }
        deadIps.forEach { disconnectClient(it) }
    }

    fun stop() {
        tearDown()
    }
    
    private fun tearDown() {
        try {
            registrationListener?.let { nsdManager.unregisterService(it) }
        } catch (e: Exception) {}
        registrationListener = null

        try {
            discoveryListener?.let { nsdManager.stopServiceDiscovery(it) }
        } catch (e: Exception) {}
        discoveryListener = null
        
        broadcastPacket("SYS_ROOM_CLOSED")

        activeConnections.values.forEach { (socket, _) ->
            try { socket.close() } catch (e: Exception) {}
        }
                activeConnections.clear()
        activeWifiNodes.value = emptyMap()
        peerNames.clear()
        try { multicastLock?.release() } catch (e: Exception) {}

        
        try { serverSocket?.close() } catch (e: Exception) {}
        serverSocket = null
        
        try { udpSocket?.close() } catch (e: Exception) {}
        udpSocket = null
    }
}
