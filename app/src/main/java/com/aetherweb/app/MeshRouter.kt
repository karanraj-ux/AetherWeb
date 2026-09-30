package com.aetherweb.app

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.GlobalScope
import java.util.concurrent.ConcurrentHashMap

class MeshRouter(private var localNodeId: String, private val cryptoManager: CryptoManager = CryptoManager()) {
    var localNodeName: String = ""

    companion object {
        /** bitchat parity: maximum flood hops (MESSAGE_TTL_HOPS = 7). */
        const val MAX_TTL_HOPS = 7
    }

    /** Applies a new stable identity (after persistent keypair load) without rebuilding the router. */
    fun updateLocalIdentity(newNodeId: String) {
        localNodeId = newNodeId
        Log.i("MeshRouter", "Local identity updated to $newNodeId")
    }

    data class NetworkMessage(
        val messageId: String,
        val senderId: String,
        val senderName: String = "",
        val ttl: Int,
        val payload: String,
        val timestamp: Long,
        val nonce: Long = 0L,
        val signature: String = "",
        val publicKey: String = ""
    )

    private val blockedUsers = ConcurrentHashMap.newKeySet<String>()

    fun blockUser(userId: String) {
        blockedUsers.add(userId)
        Log.i("MeshRouter", "User $userId blocked.")
    }

    // Token-Bucket Rate Limiter (Denial-of-Service & Broadcast Storm Defense)
    private class TokenBucket(
        private val maxCapacity: Double = 5.0,
        private val refillRatePerSecond: Double = 2.0
    ) {
        private var tokens: Double = maxCapacity
        private var lastRefillTimestamp: Long = System.currentTimeMillis()

        @Synchronized
        fun tryConsume(): Boolean {
            val now = System.currentTimeMillis()
            val elapsedSeconds = (now - lastRefillTimestamp).coerceAtLeast(0) / 1000.0
            lastRefillTimestamp = now
            tokens = (tokens + elapsedSeconds * refillRatePerSecond).coerceAtMost(maxCapacity)
            return if (tokens >= 1.0) {
                tokens -= 1.0
                true
            } else {
                false
            }
        }
    }

    private val peerRateLimiters = ConcurrentHashMap<String, TokenBucket>()

    // BitChat-grade LRU deduplication cache (maximum 2,000 recent message IDs)
    private val MAX_DEDUP_CACHE_SIZE = 2000
    private val messageCache = java.util.Collections.synchronizedMap(
        object : java.util.LinkedHashMap<String, Long>(MAX_DEDUP_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
                return size > MAX_DEDUP_CACHE_SIZE
            }
        }
    )
    private val CACHE_EXPIRY_MS = 10 * 60 * 1000L

    private val _incomingMessages = MutableSharedFlow<NetworkMessage>(
        extraBufferCapacity = 128,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val incomingMessages = _incomingMessages.asSharedFlow()

    private val _outboundBroadcasts = MutableSharedFlow<NetworkMessage>(
        extraBufferCapacity = 128,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val outboundBroadcasts = _outboundBroadcasts.asSharedFlow()

    val localPublicKey: String
        get() = cryptoManager.publicKeyBase64

    private val chunkBuffer = ConcurrentHashMap<String, MutableMap<Int, String>>()
    private val chunkTimestamp = ConcurrentHashMap<String, Long>()
    private val chunkByteSize = ConcurrentHashMap<String, Int>()

    // bitchat parity (FragmentManager): harden CHUNK: reassembly against memory abuse
    private val MAX_CHUNK_PARTS = 256
    private val MAX_CHUNK_SET_BYTES = 1024 * 1024 // 1 MB per reassembly set
    private val MAX_CHUNK_SETS = 64               // max concurrent reassembly sets
    private val CHUNK_SET_TIMEOUT_MS = 30_000L    // 30 s reassembly timeout

    private fun emitDecrypted(message: NetworkMessage) {
        var finalPayload = message.payload
        if (finalPayload.startsWith("ENC:")) {
            val aesKey = com.aetherweb.app.MeshNetworkManager.roomAesKey
            if (aesKey != null) {
                try {
                    finalPayload = com.aetherweb.app.CryptoManager.decryptAES(finalPayload.removePrefix("ENC:"), aesKey)
                } catch (e: Exception) {
                    Log.e("MeshRouter", "Failed to decrypt message, dropping", e)
                    return
                }
            } else {
                Log.w("MeshRouter", "Received encrypted message but we have no AES key. Dropping.")
                return
            }
        }
        if (finalPayload.startsWith("GZ:")) {
            finalPayload = com.aetherweb.app.NetworkUtils.decompressPayload(finalPayload)
        }
        _incomingMessages.tryEmit(message.copy(payload = finalPayload))
    }

    private fun handleChunk(message: NetworkMessage) {
        try {
            val parts = message.payload.split(":", limit = 5)
            if (parts.size == 5 && parts[0] == "CHUNK") {
                val groupId = parts[1]
                val index = parts[2].toInt()
                val total = parts[3].toInt()
                val data = parts[4]

                // bitchat parity: reject abusive reassembly sets before buffering
                if (total > MAX_CHUNK_PARTS || index < 0 || index >= total) {
                    Log.w("MeshRouter", "Dropping CHUNK set $groupId: total=$total out of bounds")
                    return
                }
                if (!chunkBuffer.containsKey(groupId) && chunkBuffer.size >= MAX_CHUNK_SETS) {
                    Log.w("MeshRouter", "Dropping CHUNK set $groupId: too many concurrent sets")
                    return
                }

                val groupMap = chunkBuffer.getOrPut(groupId) { ConcurrentHashMap() }
                if (!groupMap.containsKey(index)) {
                    val newSize = (chunkByteSize[groupId] ?: 0) + data.toByteArray(Charsets.UTF_8).size
                    if (newSize > MAX_CHUNK_SET_BYTES) {
                        Log.w("MeshRouter", "Dropping CHUNK set $groupId: exceeds 1 MB")
                        chunkBuffer.remove(groupId)
                        chunkTimestamp.remove(groupId)
                        chunkByteSize.remove(groupId)
                        return
                    }
                    chunkByteSize[groupId] = newSize
                }
                groupMap[index] = data
                chunkTimestamp[groupId] = System.currentTimeMillis()

                if (groupMap.size == total) {
                    val fullPayload = StringBuilder()
                    for (i in 0 until total) {
                        fullPayload.append(groupMap[i] ?: "")
                    }
                    chunkBuffer.remove(groupId)
                    chunkTimestamp.remove(groupId)
                    chunkByteSize.remove(groupId)

                    val assembledMessage = message.copy(
                        messageId = groupId,
                        payload = fullPayload.toString()
                    )
                    emitDecrypted(assembledMessage)
                }
            }
        } catch (e: Exception) {
            Log.e("MeshRouter", "Error handling chunk", e)
        }
    }

    fun processIncomingPacket(message: NetworkMessage) {
        cleanupCache()

        if (blockedUsers.contains(message.senderId)) {
            Log.d("MeshRouter", "Message dropped from blocked user: ${message.senderId}")
            return
        }

        if (message.senderId == localNodeId) {
            return
        }

        // 1. Anti-Replay Monotonic Time Gate: Drop packets with timestamp drift > 5 minutes (300,000 ms)
        val now = System.currentTimeMillis()
        val driftMs = Math.abs(now - message.timestamp)
        if (driftMs > 5 * 60 * 1000L) {
            Log.w("MeshRouter", "Packet ${message.messageId} dropped: Timestamp drift $driftMs ms exceeds 5-minute threshold (Possible replay attack).")
            return
        }

        // Deduplication & Anti-Replay Nonce Check
        val cacheKey = if (message.nonce != 0L) "${message.messageId}_${message.nonce}" else message.messageId
        if (messageCache.containsKey(cacheKey) || messageCache.containsKey(message.messageId)) {
            Log.d("MeshRouter", "Duplicate or replayed message dropped: ${message.messageId}")
            return
        }

        // 2. Token-Bucket Rate Limiter (Per-peer: 5 burst, 2 refills/sec DoS defense)
        val rateLimiter = peerRateLimiters.getOrPut(message.senderId) { TokenBucket() }
        if (!rateLimiter.tryConsume()) {
            Log.w("MeshRouter", "Rate limit exceeded for sender ${message.senderId}. Dropping packet ${message.messageId} to prevent radio flood.")
            return
        }

        // 3. Cryptographic Identity Anchoring & Zero-Trust Verification
        if (message.publicKey.isBlank() || message.signature.isBlank()) {
            Log.w("MeshRouter", "Packet ${message.messageId} dropped: Missing cryptographic public key or signature.")
            return
        }

        val expectedFingerprint = CryptoManager.computeNodeId(message.publicKey)
        // Verify sender ID matches the public key fingerprint (or prefix)
        val isIdentityAnchored = message.senderId == expectedFingerprint ||
                                 message.senderId.startsWith(expectedFingerprint.take(8)) ||
                                 expectedFingerprint.startsWith(message.senderId.take(8))

        if (!isIdentityAnchored) {
            Log.w("MeshRouter", "Identity mismatch for sender ${message.senderId} vs pubkey fingerprint $expectedFingerprint. Dropping spoofed packet.")
            return
        }

        val dataToVerifyWithNonce = "${message.messageId}:${message.senderId}:${message.payload}:${message.timestamp}:${message.nonce}"
        val dataToVerifyLegacy = "${message.messageId}:${message.senderId}:${message.payload}:${message.timestamp}"
        
        val isValid = (message.nonce != 0L && CryptoManager.verify(dataToVerifyWithNonce, message.signature, message.publicKey)) ||
                      CryptoManager.verify(dataToVerifyLegacy, message.signature, message.publicKey)

        if (!isValid) {
            Log.w("MeshRouter", "Invalid digital signature for message: ${message.messageId} from ${message.senderId}. Dropping forged/tampered message.")
            return
        }

        messageCache[cacheKey] = now
        messageCache[message.messageId] = now
        Log.i("MeshRouter", "New verified identity-anchored message received: ${message.messageId} from ${message.senderId}")

        // bitchat parity: every verified packet refreshes BLE presence ("who's around")
        PresenceManager.onPeerSeen(message.senderId, message.senderName)

        if (message.payload.startsWith("CHUNK:")) {
            handleChunk(message)
        } else {
            emitDecrypted(message)
        }

        val newTtl = message.ttl - 1
        if (newTtl > 0 && shouldRelay(newTtl)) {
            val rebroadcastMessage = message.copy(ttl = newTtl)
            Log.d("MeshRouter", "Rebroadcasting message ${message.messageId} with TTL $newTtl")
            // bitchat parity: 8–26 ms relay jitter prevents synchronized rebroadcast collisions
            GlobalScope.launch {
                delay(java.util.concurrent.ThreadLocalRandom.current().nextLong(8, 27))
                _outboundBroadcasts.tryEmit(rebroadcastMessage)
            }
        } else {
            Log.d("MeshRouter", "Message ${message.messageId} not relayed (TTL=$newTtl).")
        }
    }

    /**
     * bitchat parity (PacketRelayManager): density-aware relay thinning.
     * Small meshes always relay (p=1.0); large crowds thin probabilistically so
     * chat stays smooth in a 200-person protest. Our per-peer token bucket above
     * remains as the per-sender floor.
     */
    private fun shouldRelay(newTtl: Int): Boolean {
        if (newTtl >= 4) return true
        val size = PresenceManager.networkSize()
        if (size <= 10) return true
        val p = when {
            size <= 30 -> 0.85
            size <= 50 -> 0.70
            size <= 100 -> 0.55
            else -> 0.40
        }
        return Math.random() < p
    }

    fun routePacket(packet: com.aetherweb.app.protocol.MeshPacket) {
        routeLocalMessage(packet.toJsonString())
    }

    /**
     * @param ttl initial flood hops (bitchat parity default: [MAX_TTL_HOPS])
     * @param encrypt apply room AES when a room key exists; false for plaintext
     *        presence announces (signed but readable by any nearby device)
     */
    fun routeLocalMessage(rawPayload: String, ttl: Int = MAX_TTL_HOPS, encrypt: Boolean = true) {
        try {
            // Network Throttling & Packet Compression: Compress raw payload if beneficial
            val compressed = com.aetherweb.app.NetworkUtils.compressPayload(rawPayload)
            val payload = if (encrypt) {
                com.aetherweb.app.MeshNetworkManager.roomAesKey?.let {
                    "ENC:" + com.aetherweb.app.CryptoManager.encryptAES(compressed, it)
                } ?: compressed
            } else {
                compressed
            }
            val maxChunkSize = 450
            if (payload.length > maxChunkSize) {
                val groupId = java.util.UUID.randomUUID().toString()
                val chunks = payload.chunked(maxChunkSize)
                kotlinx.coroutines.GlobalScope.launch {
                    chunks.forEachIndexed { index, chunkData ->
                        val chunkPayload = "CHUNK:$groupId:$index:${chunks.size}:$chunkData"
                        sendNetworkMessage(chunkPayload, ttl)
                        kotlinx.coroutines.delay(200)
                    }
                }
            } else {
                sendNetworkMessage(payload)
            }
        } catch(e: Exception) {
            Log.e("MeshRouter", "Error routing local message", e)
        }
    }

    private fun sendNetworkMessage(payload: String, ttl: Int = MAX_TTL_HOPS) {
        try {
            val messageId = java.util.UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()
            val nonce = java.security.SecureRandom().nextLong()
            
            val dataToSign = "$messageId:$localNodeId:$payload:$timestamp:$nonce"
            val signature = cryptoManager.sign(dataToSign)
    
            val networkMessage = NetworkMessage(
                messageId = messageId,
                senderId = localNodeId,
                senderName = localNodeName,
                ttl = ttl,
                payload = payload,
                timestamp = timestamp,
                nonce = nonce,
                signature = signature,
                publicKey = cryptoManager.publicKeyBase64
            )
    
            val cacheKey = "${messageId}_$nonce"
            messageCache[cacheKey] = timestamp
            messageCache[messageId] = timestamp
            Log.i("MeshRouter", "Routing local message for broadcast: $messageId (nonce: $nonce)")
            DiagnosticLogger.log("Message Lifecycle", "Router Processing", "Host phone assigned unique Message ID and signed it: $messageId", EventStatus.SUCCESS)
            _outboundBroadcasts.tryEmit(networkMessage)
        } catch(e: Exception) {
            Log.e("MeshRouter", "Error sending network message", e)
            DiagnosticLogger.log("Message Lifecycle", "Router Processing", "Failed to process message: ${e.message}", EventStatus.ERROR)
        }
    }

    private fun cleanupCache() {
        val now = System.currentTimeMillis()
        synchronized(messageCache) {
            val iterator = messageCache.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (now - entry.value > CACHE_EXPIRY_MS) {
                    iterator.remove()
                }
            }
        }
        
        // bitchat parity: incomplete reassembly sets expire after 30 s (not 10 min)
        val chunkIterator = chunkTimestamp.entries.iterator()
        while (chunkIterator.hasNext()) {
            val entry = chunkIterator.next()
            if (now - entry.value > CHUNK_SET_TIMEOUT_MS) {
                chunkBuffer.remove(entry.key)
                chunkByteSize.remove(entry.key)
                chunkIterator.remove()
            }
        }
    }
}
