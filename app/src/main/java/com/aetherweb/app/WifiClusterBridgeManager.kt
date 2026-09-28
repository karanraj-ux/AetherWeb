package com.aetherweb.app

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Phase 3: Wi-Fi Multi-Cluster Mesh Bridging
 *
 * Implements:
 * 1. Autonomous Cluster-of-Clusters Topology:
 *    - Detects when a local cluster/hotspot approaches capacity (default threshold >= 7 connected peers).
 *    - Automatically signals or triggers secondary cluster formation ("Cluster Beta") on an adjacent node.
 *    - Establishes Bridge Relay nodes in the overlap zone to interconnect multiple AP clusters.
 *
 * 2. Dynamic Subnet & Collision-Free IP Routing with Path Metrics:
 *    - Maps peer public keys & Node IDs to their dynamic IP addresses, subnets, and cluster IDs.
 *    - Evaluates path metrics: High-speed Wi-Fi Direct/AP (Low Latency / High Bandwidth) vs BLE Whisper Mesh (Long Range / Low Power).
 *    - Routes heavy packets (HD video, files, canvas vectors) across the fastest Wi-Fi path, with instant fallback to BLE when bridges move.
 */
object WifiClusterBridgeManager {

    private const val TAG = "WifiClusterBridge"
    const val CLUSTER_CAPACITY_THRESHOLD = 7 // Max peers per Wi-Fi AP before secondary cluster trigger

    data class ClusterNodeRoute(
        val nodeId: String,
        val ipAddress: String,
        val port: Int = 8888,
        val clusterId: String,
        val isDirectWifi: Boolean = true,
        val hopCount: Int = 1,
        val lastSeen: Long = System.currentTimeMillis()
    )

    // Dynamic routing table: NodeId -> ClusterNodeRoute
    private val routingTable = ConcurrentHashMap<String, ClusterNodeRoute>()

    // Cluster topology state: ClusterID -> List of member NodeIds
    private val clusterMemberships = ConcurrentHashMap<String, MutableSet<String>>()

    // Bridge Status StateFlow for UI / Diagnostics
    data class BridgeState(
        val currentClusterId: String = "cluster_local",
        val isBridgeRelay: Boolean = false,
        val activeClustersCount: Int = 1,
        val totalBridgedPeers: Int = 0,
        val secondaryClusterTriggered: Boolean = false
    )

    private val _bridgeState = MutableStateFlow(BridgeState())
    val bridgeState: StateFlow<BridgeState> = _bridgeState.asStateFlow()

    fun initializeLocalCluster(localNodeId: String, localIp: String) {
        val clusterId = "cluster_${localNodeId.take(6)}"
        _bridgeState.value = _bridgeState.value.copy(currentClusterId = clusterId)
        registerPeerRoute(localNodeId, localIp, 8888, clusterId, isDirect = true, hops = 0)
        Log.i(TAG, "Initialized local cluster: $clusterId at $localIp")
    }

    /**
     * Registers or updates a route to a peer with path metrics.
     */
    fun registerPeerRoute(
        nodeId: String,
        ipAddress: String,
        port: Int = 8888,
        clusterId: String,
        isDirect: Boolean = true,
        hops: Int = 1
    ) {
        val route = ClusterNodeRoute(
            nodeId = nodeId,
            ipAddress = ipAddress,
            port = port,
            clusterId = clusterId,
            isDirectWifi = isDirect,
            hopCount = hops,
            lastSeen = System.currentTimeMillis()
        )
        routingTable[nodeId] = route
        val members = clusterMemberships.getOrPut(clusterId) { ConcurrentHashMap.newKeySet() }
        members.add(nodeId)

        updateBridgeMetrics()
    }

    /**
     * Removes stale or disconnected routes.
     */
    fun removePeerRoute(nodeId: String) {
        val route = routingTable.remove(nodeId)
        if (route != null) {
            clusterMemberships[route.clusterId]?.remove(nodeId)
            updateBridgeMetrics()
        }
    }

    /**
     * Path Metric evaluation:
     * Returns true if a fast direct Wi-Fi route is available to this target peer.
     */
    fun hasFastWifiRoute(targetNodeId: String): Boolean {
        val route = routingTable[targetNodeId] ?: return false
        val isFresh = (System.currentTimeMillis() - route.lastSeen) < 45_000L
        return route.isDirectWifi && isFresh
    }

    /**
     * Retrieves the best IP address for direct high-speed transfer (video, files).
     */
    fun getFastRouteIp(targetNodeId: String): String? {
        val route = routingTable[targetNodeId] ?: return null
        return if (route.isDirectWifi) route.ipAddress else null
    }

    /**
     * Checks if the current local cluster has saturated and needs to trigger
     * an adjacent node to start a secondary cluster ("Cluster Beta").
     */
    fun checkClusterCapacity(activePeersCount: Int, isHost: Boolean): Boolean {
        if (isHost && activePeersCount >= CLUSTER_CAPACITY_THRESHOLD) {
            if (!_bridgeState.value.secondaryClusterTriggered) {
                _bridgeState.value = _bridgeState.value.copy(secondaryClusterTriggered = true)
                Log.w(TAG, "Cluster saturated ($activePeersCount peers). Signaling secondary cluster expansion.")
                DiagnosticLogger.log("Cluster Bridge", "Capacity Saturated", "Local AP has $activePeersCount peers. Triggering secondary cluster.", EventStatus.PENDING)
                return true
            }
        } else if (activePeersCount < CLUSTER_CAPACITY_THRESHOLD - 2) {
            if (_bridgeState.value.secondaryClusterTriggered) {
                _bridgeState.value = _bridgeState.value.copy(secondaryClusterTriggered = false)
            }
        }
        return false
    }

    /**
     * Marks this device as a Bridge Relay node interconnecting multiple clusters.
     */
    fun setBridgeRelayStatus(isBridge: Boolean) {
        _bridgeState.value = _bridgeState.value.copy(isBridgeRelay = isBridge)
        Log.i(TAG, "Bridge Relay mode: $isBridge")
        DiagnosticLogger.log("Cluster Bridge", "Bridge Relay", if (isBridge) "Node acting as Cross-Cluster Bridge" else "Standard Node", EventStatus.SUCCESS)
    }

    private fun updateBridgeMetrics() {
        val activeClusters = clusterMemberships.keys.size.coerceAtLeast(1)
        val totalPeers = routingTable.size
        val isBridge = activeClusters > 1

        _bridgeState.value = _bridgeState.value.copy(
            activeClustersCount = activeClusters,
            totalBridgedPeers = totalPeers,
            isBridgeRelay = isBridge
        )
    }

    /**
     * Generates a Cluster Beacon JSON packet to inform neighbors about cluster topology.
     */
    fun createClusterBeacon(localNodeId: String, currentIp: String, isHost: Boolean, peerCount: Int): String {
        val obj = org.json.JSONObject().apply {
            put("type", "cluster_beacon")
            put("clusterId", _bridgeState.value.currentClusterId)
            put("hostNodeId", localNodeId)
            put("hostIp", currentIp)
            put("isHost", isHost)
            put("peerCount", peerCount)
            put("isSaturated", peerCount >= CLUSTER_CAPACITY_THRESHOLD)
            put("timestamp", System.currentTimeMillis())
        }
        return obj.toString()
    }

    /**
     * Parses incoming cluster beacon from a neighboring node.
     */
    fun handleClusterBeacon(beaconJson: String, senderIp: String): Boolean {
        try {
            val obj = org.json.JSONObject(beaconJson)
            if (obj.optString("type") != "cluster_beacon") return false

            val clusterId = obj.getString("clusterId")
            val hostNodeId = obj.getString("hostNodeId")
            val hostIp = obj.optString("hostIp", senderIp)
            val isSaturated = obj.optBoolean("isSaturated", false)
            val peerCount = obj.optInt("peerCount", 0)

            registerPeerRoute(
                nodeId = hostNodeId,
                ipAddress = hostIp,
                port = 8888,
                clusterId = clusterId,
                isDirect = true,
                hops = 1
            )

            // If neighbor cluster is saturated and we are NOT connected to any Wi-Fi hotspot,
            // we are eligible to act as a secondary cluster host or bridge!
            if (isSaturated && !MeshNetworkManager.uiState.value.isHotspotActive && !MeshNetworkManager.uiState.value.isWifiConnected) {
                Log.i(TAG, "Neighbor cluster $clusterId is saturated with $peerCount peers. Available to bridge or form secondary cluster.")
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing cluster beacon", e)
        }
        return false
    }

    /**
     * Formulates a cross-cluster bridged message. If the destination peer is on another cluster,
     * this packages the message with the target cluster ID so intermediate bridges forward it.
     */
    fun createBridgedPacket(targetNodeId: String, originalJson: String): String? {
        val route = routingTable[targetNodeId] ?: return null
        val obj = org.json.JSONObject().apply {
            put("type", "cluster_forward")
            put("targetClusterId", route.clusterId)
            put("targetNodeId", targetNodeId)
            put("payload", originalJson)
            put("timestamp", System.currentTimeMillis())
        }
        return obj.toString()
    }
}
