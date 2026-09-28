package com.aetherweb.app

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.GlobalScope
import java.util.concurrent.ConcurrentHashMap

class MeshRouter(private val localNodeId: String, private val cryptoManager: CryptoManager = CryptoManager()) {
    var localNodeName: String = ""

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

                val groupMap = chunkBuffer.getOrPut(groupId) { ConcurrentHashMap() }
                groupMap[index] = data
                chunkTimestamp[groupId] = System.currentTimeMillis()

                if (groupMap.size == total) {
                    val fullPayload = StringBuilder()
                    for (i in 0 until total) {
                        fullPayload.append(groupMap[i] ?: "")
                    }
                    chunkBuffer.remove(groupId)
                    chunkTimestamp.remove(groupId)
                    
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
        
        if (message.payload.startsWith("CHUNK:")) {
            handleChunk(message)
        } else {
            emitDecrypted(message)
        }

        val newTtl = message.ttl - 1
        if (newTtl > 0) {
            val rebroadcastMessage = message.copy(ttl = newTtl)
            Log.d("MeshRouter", "Rebroadcasting message ${message.messageId} with TTL $newTtl")
            _outboundBroadcasts.tryEmit(rebroadcastMessage)
        } else {
            Log.d("MeshRouter", "Message ${message.messageId} reached max hops (TTL=0). Dropping.")
        }
    }

    fun routePacket(packet: com.aetherweb.app.protocol.MeshPacket) {
        routeLocalMessage(packet.toJsonString())
    }

    fun routeLocalMessage(rawPayload: String) {
        try {
            // Network Throttling & Packet Compression: Compress raw payload if beneficial
            val compressed = com.aetherweb.app.NetworkUtils.compressPayload(rawPayload)
            val payload = com.aetherweb.app.MeshNetworkManager.roomAesKey?.let { 
                "ENC:" + com.aetherweb.app.CryptoManager.encryptAES(compressed, it) 
            } ?: compressed
            val maxChunkSize = 450
            if (payload.length > maxChunkSize) {
                val groupId = java.util.UUID.randomUUID().toString()
                val chunks = payload.chunked(maxChunkSize)
                kotlinx.coroutines.GlobalScope.launch {
                    chunks.forEachIndexed { index, chunkData ->
                        val chunkPayload = "CHUNK:$groupId:$index:${chunks.size}:$chunkData"
                        sendNetworkMessage(chunkPayload)
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

    private fun sendNetworkMessage(payload: String) {
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
                ttl = 3,
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
        
        val chunkIterator = chunkTimestamp.entries.iterator()
        while (chunkIterator.hasNext()) {
            val entry = chunkIterator.next()
            if (now - entry.value > CACHE_EXPIRY_MS) {
                chunkBuffer.remove(entry.key)
                chunkIterator.remove()
            }
        }
    }
}
