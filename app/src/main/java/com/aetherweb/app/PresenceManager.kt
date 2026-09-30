package com.aetherweb.app

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * bitchat parity: BLE presence announcements + peer table.
 *
 * Every device broadcasts a small signed (plaintext, not room-encrypted) ANNOUNCE
 * packet every 30 s so the mesh knows "who's around" even when nobody is chatting.
 * Any verified packet also refreshes presence, so the table fills from live traffic.
 *
 * The reachable-mesh size feeds [MeshRouter]'s density-aware relay thinning —
 * the mechanism that keeps chat smooth in a 200-person crowd.
 *
 * Deliberately NOT copied from bitchat: geohash location channels (at odds with
 * our burner/panic-wipe privacy posture) and Noise_XX session handshakes.
 */
object PresenceManager {
    const val ANNOUNCE_PREFIX = "ANNOUNCE:"
    const val ANNOUNCE_INTERVAL_MS = 30_000L
    const val PEER_EXPIRY_MS = 3 * 60 * 1000L // 3-minute stale expiry (bitchat PeerManager)

    data class PeerInfo(val peerId: String, var nickname: String, var lastSeen: Long)

    private val peers = ConcurrentHashMap<String, PeerInfo>()
    private var announcerJob: Job? = null

    /** Called for every signature-verified packet (announce or traffic). */
    fun onPeerSeen(peerId: String, nickname: String) {
        if (peerId.isBlank()) return
        val now = System.currentTimeMillis()
        val existing = peers[peerId]
        if (existing != null) {
            existing.lastSeen = now
            if (nickname.isNotBlank()) existing.nickname = nickname
        } else {
            peers[peerId] = PeerInfo(peerId, nickname.ifBlank { "Peer_${peerId.take(4)}" }, now)
            Log.i("PresenceManager", "New peer discovered: $peerId ($nickname) — mesh size ${peers.size}")
        }
    }

    fun onPeerLeft(peerId: String) {
        if (peers.remove(peerId) != null) {
            Log.i("PresenceManager", "Peer left: $peerId — mesh size ${peers.size}")
        }
    }

    fun sweepExpired() {
        val now = System.currentTimeMillis()
        peers.entries.removeIf { now - it.value.lastSeen > PEER_EXPIRY_MS }
    }

    /** Reachable mesh size — input for density-aware relay thinning. */
    fun networkSize(): Int {
        sweepExpired()
        return peers.size
    }

    fun snapshot(): List<PeerInfo> {
        sweepExpired()
        return peers.values.sortedByDescending { it.lastSeen }
    }

    /**
     * Handles an incoming ANNOUNCE payload. Returns true when the payload was an
     * announce (caller should skip chat display / packet dispatch for it).
     */
    fun handleIncomingAnnounce(payload: String, senderId: String): Boolean {
        if (!payload.startsWith(ANNOUNCE_PREFIX)) return false
        try {
            val json = JSONObject(payload.removePrefix(ANNOUNCE_PREFIX))
            if (json.optBoolean("leave", false)) {
                onPeerLeft(senderId)
            } else {
                onPeerSeen(senderId, json.optString("name", ""))
            }
        } catch (e: Exception) {
            onPeerSeen(senderId, "")
        }
        return true
    }

    fun buildAnnouncePayload(displayName: String, leaving: Boolean = false): String {
        val json = JSONObject().apply {
            put("v", 1)
            put("name", displayName)
            if (leaving) put("leave", true)
        }
        return ANNOUNCE_PREFIX + json.toString()
    }

    /** 30 s foreground announce loop with an immediate announce on start (bitchat behavior). */
    fun startAnnouncer(scope: CoroutineScope, router: MeshRouter, nameProvider: () -> String) {
        if (announcerJob?.isActive == true) return
        announcerJob = scope.launch {
            try {
                router.routeLocalMessage(buildAnnouncePayload(nameProvider()), encrypt = false)
            } catch (e: Exception) {
                Log.w("PresenceManager", "Initial announce failed", e)
            }
            while (isActive) {
                delay(ANNOUNCE_INTERVAL_MS)
                try {
                    router.routeLocalMessage(buildAnnouncePayload(nameProvider()), encrypt = false)
                } catch (e: Exception) {
                    Log.w("PresenceManager", "Announce failed", e)
                }
            }
        }
        Log.i("PresenceManager", "Presence announcer started (30 s cadence)")
    }

    fun stopAnnouncer() {
        announcerJob?.cancel()
        announcerJob = null
    }

    /** Best-effort LEAVE broadcast so peer tables prune promptly instead of 3-min expiry. */
    fun sendLeave(router: MeshRouter, displayName: String) {
        try {
            router.routeLocalMessage(buildAnnouncePayload(displayName, leaving = true), encrypt = false)
        } catch (e: Exception) {
            Log.w("PresenceManager", "Leave announce failed", e)
        }
    }
}
