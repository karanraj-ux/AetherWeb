package com.aetherweb.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

data class RelayState(
    val isInternetAvailable: Boolean = false,
    val isRelayConnected: Boolean = false,
    val activeRelayUrl: String = "",
    val pendingMuleCount: Int = 0,
    val lastSyncTime: Long = 0L,
    val statusSummary: String = "Offline (Local Radio Mesh Only)"
)

object InternetRelayManager {
    private const val TAG = "InternetRelay"

    // Multi-Relay Decentralized Pool (Zero login, public, censorship-resistant)
    private val DEFAULT_RELAYS = listOf(
        "wss://relay.damus.io",
        "wss://relay.primal.net",
        "wss://nostr.mom",
        "wss://relay.nostr.band",
        "wss://eden.nostr.land",
        "wss://purplerelay.com",
        "wss://nos.lol",
        "wss://relay.snort.social"
    )

    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(8, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var activeWebSocket: WebSocket? = null
    private var currentRelayIndex = 0
    private var failoverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _relayState = MutableStateFlow(RelayState())
    val relayState = _relayState.asStateFlow()

    private val _inboundRelayMessages = MutableSharedFlow<JSONObject>(extraBufferCapacity = 50)
    val inboundRelayMessages = _inboundRelayMessages.asSharedFlow()

    // Deduplication cache (message ID -> timestamp)
    private val seenMessageIds = ConcurrentHashMap<String, Long>()
    private const val DEDUP_EXPIRY_MS = 24 * 60 * 60 * 1000L // 24 hours

    // Data Mule Outbox: store envelopes while offline to flush upon reconnecting
    private val muleOutbox = ConcurrentLinkedQueue<String>()

    private var appContext: Context? = null
    private var localNodeId: String = ""
    private var localPublicKey: String = ""
    private var localMailboxId: String = ""

    fun init(context: Context, nodeId: String, publicKey: String) {
        if (appContext != null) return
        appContext = context.applicationContext
        localNodeId = nodeId
        localPublicKey = publicKey
        localMailboxId = computeMailboxHash(publicKey.ifEmpty { nodeId })

        Log.i(TAG, "Initialized Internet Relay. Local Mailbox ID: $localMailboxId")
        registerNetworkCallback(context)
    }

    fun computeMailboxHash(input: String): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(input.toByteArray(Charsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }.take(32)
        } catch (e: Exception) {
            input.hashCode().toString()
        }
    }

    private fun registerNetworkCallback(context: Context) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val builder = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

        connectivityManager.registerNetworkCallback(builder.build(), object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.i(TAG, "🌐 Internet connection established. Starting relay sync...")
                _relayState.value = _relayState.value.copy(
                    isInternetAvailable = true,
                    statusSummary = "Connecting to Relay Pool..."
                )
                scope.launch {
                    connectToNextRelay()
                    flushMuleQueue()
                }
            }

            override fun onLost(network: Network) {
                Log.w(TAG, "Internet lost. Falling back to Pure Radio Mesh.")
                disconnectRelay()
                _relayState.value = _relayState.value.copy(
                    isInternetAvailable = false,
                    isRelayConnected = false,
                    statusSummary = "Offline (Pure Radio Mesh)"
                )
            }
        })

        // Check initial state
        val activeNetwork = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
        val isConnected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        if (isConnected) {
            _relayState.value = _relayState.value.copy(
                isInternetAvailable = true,
                statusSummary = "Connecting to Relay Pool..."
            )
            scope.launch {
                connectToNextRelay()
            }
        }
    }

    @Synchronized
    private fun connectToNextRelay() {
        if (!_relayState.value.isInternetAvailable) return
        disconnectRelay()

        val relayUrl = DEFAULT_RELAYS[currentRelayIndex % DEFAULT_RELAYS.size]
        Log.i(TAG, "Attempting connection to decentralized relay: $relayUrl")

        val request = Request.Builder()
            .url(relayUrl)
            .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:120.0) MeshChat/1.0")
            .build()
        activeWebSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "Connected to relay: $relayUrl")
                _relayState.value = _relayState.value.copy(
                    isRelayConnected = true,
                    activeRelayUrl = relayUrl,
                    statusSummary = "Connected: ${relayUrl.removePrefix("wss://")}"
                )

                // Subscribe to our personal cryptographic mailbox tag
                subscribeToMailbox(webSocket, localMailboxId)
                // Also subscribe to broadcast emergency SOS alerts
                subscribeToEmergencyChannel(webSocket)
                // Drain any pending messages queued while offline
                flushMuleQueue()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleInboundRelayPayload(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "Relay $relayUrl closed: $reason ($code)")
                _relayState.value = _relayState.value.copy(
                    isRelayConnected = false,
                    statusSummary = "Reconnecting..."
                )
                retryFailover()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val errorDesc = response?.let { "HTTP ${it.code}" } ?: t.message ?: "timeout"
                Log.w(TAG, "Relay $relayUrl connection notice: $errorDesc. Cycling to next relay in pool...")
                _relayState.value = _relayState.value.copy(
                    isRelayConnected = false,
                    statusSummary = "Failing over..."
                )
                retryFailover()
            }
        })
    }

    private fun retryFailover() {
        if (_relayState.value.isInternetAvailable) {
            failoverJob?.cancel()
            failoverJob = scope.launch {
                delay(2000L)
                currentRelayIndex = (currentRelayIndex + 1) % DEFAULT_RELAYS.size
                connectToNextRelay()
            }
        }
    }

    private fun disconnectRelay() {
        try {
            activeWebSocket?.close(1000, "Switching or Disconnecting")
            activeWebSocket = null
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting relay", e)
        }
    }

    private fun subscribeToMailbox(ws: WebSocket, mailboxId: String) {
        try {
            // Nostr / Decoupled Pub-Sub Subscription: Filters by Tag '#p'
            val subJson = JSONArray().apply {
                put("REQ")
                put("sub_$mailboxId")
                put(JSONObject().apply {
                    put("kinds", JSONArray().apply { put(4); put(20000) }) // Direct message or ephemeral
                    put("#p", JSONArray().apply { put(mailboxId) })
                    put("since", (System.currentTimeMillis() / 1000L) - (24 * 3600)) // Last 24 hours
                })
            }
            ws.send(subJson.toString())
            Log.d(TAG, "Subscribed to mailbox: $mailboxId")
        } catch (e: Exception) {
            Log.e(TAG, "Error subscribing to mailbox", e)
        }
    }

    private fun subscribeToEmergencyChannel(ws: WebSocket) {
        try {
            val subJson = JSONArray().apply {
                put("REQ")
                put("sub_emergency_sos")
                put(JSONObject().apply {
                    put("kinds", JSONArray().apply { put(20001) }) // Dedicated SOS beacon kind
                    put("since", (System.currentTimeMillis() / 1000L) - 3600) // Last hour
                })
            }
            ws.send(subJson.toString())
            Log.d(TAG, "Subscribed to emergency SOS channel")
        } catch (e: Exception) {
            Log.e(TAG, "Error subscribing to SOS channel", e)
        }
    }

    fun sendRemoteEnvelope(
        targetPublicKeyOrMailbox: String,
        payloadJson: String,
        recipientName: String = "",
        isEmergency: Boolean = false
    ): Boolean {
        val targetMailbox = if (targetPublicKeyOrMailbox.length > 32) {
            computeMailboxHash(targetPublicKeyOrMailbox)
        } else {
            targetPublicKeyOrMailbox
        }

        val envelope = JSONObject().apply {
            put("type", if (isEmergency) "sos_beacon" else "relay_envelope")
            put("msgId", UUID.randomUUID().toString())
            put("fromMailbox", localMailboxId)
            put("fromNodeId", localNodeId)
            put("fromPublicKey", localPublicKey)
            put("toMailbox", targetMailbox)
            put("timestamp", System.currentTimeMillis())
            put("payload", payloadJson)
            put("isEmergency", isEmergency)
        }

        val rawEnvelopeString = envelope.toString()

        // Wrap into Nostr-compatible EVENT message format
        val eventJson = JSONObject().apply {
            put("id", UUID.randomUUID().toString().replace("-", ""))
            put("pubkey", localMailboxId)
            put("created_at", System.currentTimeMillis() / 1000L)
            put("kind", if (isEmergency) 20001 else 4)
            put("tags", JSONArray().apply {
                put(JSONArray().apply { put("p"); put(targetMailbox) })
                if (isEmergency) put(JSONArray().apply { put("t"); put("emergency_sos") })
            })
            put("content", rawEnvelopeString)
        }

        val messageToSend = JSONArray().apply {
            put("EVENT")
            put(eventJson)
        }.toString()

        if (_relayState.value.isRelayConnected && activeWebSocket != null) {
            val sent = activeWebSocket?.send(messageToSend) == true
            if (sent) {
                Log.i(TAG, "Successfully published message to relay for $targetMailbox")
                _relayState.value = _relayState.value.copy(
                    lastSyncTime = System.currentTimeMillis()
                )
                return true
            }
        }

        // If not sent or offline, buffer in Data Mule queue!
        Log.i(TAG, "Offline or relay unready. Queuing into Data Mule Outbox ($targetMailbox)")
        muleOutbox.add(messageToSend)
        _relayState.value = _relayState.value.copy(
            pendingMuleCount = muleOutbox.size
        )
        return false
    }

    private fun flushMuleQueue() {
        if (!_relayState.value.isRelayConnected || activeWebSocket == null) return
        scope.launch {
            var sentCount = 0
            while (muleOutbox.isNotEmpty() && _relayState.value.isRelayConnected) {
                val envelope = muleOutbox.poll() ?: break
                val success = activeWebSocket?.send(envelope) == true
                if (success) {
                    sentCount++
                    delay(50) // gentle throttle
                } else {
                    muleOutbox.add(envelope)
                    break
                }
            }
            if (sentCount > 0) {
                Log.i(TAG, "Flushed $sentCount Data Mule messages to relay.")
                _relayState.value = _relayState.value.copy(
                    pendingMuleCount = muleOutbox.size,
                    lastSyncTime = System.currentTimeMillis()
                )
            }
        }
    }

    private fun handleInboundRelayPayload(text: String) {
        try {
            // Nostr format: ["EVENT", "sub_id", { ...event... }]
            if (text.startsWith("[")) {
                val array = JSONArray(text)
                val type = array.optString(0)
                if (type == "EVENT") {
                    val eventObj = array.optJSONObject(2) ?: return
                    val content = eventObj.optString("content")
                    if (content.isNotEmpty()) {
                        processEnvelopeContent(content)
                    }
                }
            } else if (text.startsWith("{")) {
                processEnvelopeContent(text)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse inbound relay payload", e)
        }
    }

    private fun processEnvelopeContent(rawContent: String) {
        val trimmed = rawContent.trim()
        // Non-JSON strings from other Nostr apps (e.g. NIP-04 ciphertexts, base64 payloads) are safely ignored
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return
        }

        try {
            val envelope = JSONObject(trimmed)
            val msgId = envelope.optString("msgId")
            val toMailbox = envelope.optString("toMailbox")
            val fromMailbox = envelope.optString("fromMailbox")
            val isEmergency = envelope.optBoolean("isEmergency", false)

            // Ignore JSON objects that are not MeshChat envelopes
            if (msgId.isEmpty() && toMailbox.isEmpty() && !isEmergency) {
                return
            }

            // Deduplicate
            if (msgId.isNotEmpty()) {
                if (seenMessageIds.containsKey(msgId)) return
                seenMessageIds[msgId] = System.currentTimeMillis()
            }

            // Verify if addressed to us or broadcast emergency
            if (toMailbox != localMailboxId && !isEmergency) {
                return
            }

            // Drop our own echoed envelopes
            if (fromMailbox == localMailboxId) {
                return
            }

            Log.i(TAG, "Received new remote envelope: $msgId (Emergency: $isEmergency)")
            _inboundRelayMessages.tryEmit(envelope)

            // Auto-send delivery acknowledgment if it's a direct message
            if (!isEmergency && msgId.isNotEmpty() && fromMailbox.isNotEmpty()) {
                sendDeliveryAck(fromMailbox, msgId)
            }

            // Trigger WakeLock and notification if app is in background
            if (!MainActivity.isAppInForeground) {
                appContext?.let { ctx ->
                    val payload = envelope.optString("payload")
                    if (isEmergency) {
                        wakeDeviceForEmergency(ctx)
                        val sName = envelope.optString("senderName", "Remote Node")
                        val sHandle = envelope.optString("senderHandle", fromMailbox.take(6))
                        NotificationHelper.showEmergencySOSNotification(
                            ctx,
                            sName,
                            sHandle,
                            payload.ifEmpty { "DISTRESS BEACON RECEIVED OVER INTERNET RELAY" },
                            0.0,
                            0.0
                        )
                    } else {
                        val senderName = envelope.optString("fromName", "Remote Contact")
                        NotificationHelper.showMessageNotification(
                            context = ctx,
                            senderName = senderName,
                            message = payload,
                            senderId = fromMailbox,
                            isGroup = false,
                            groupTitle = null,
                            isFromWeb = false
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Silently ignore malformed non-MeshChat JSON objects from external Nostr clients
            Log.d(TAG, "Skipping non-MeshChat event: ${e.message}")
        }
    }

    private fun sendDeliveryAck(targetMailbox: String, originalMsgId: String) {
        try {
            val ackJson = JSONObject().apply {
                put("type", "delivery_ack")
                put("originalMsgId", originalMsgId)
                put("status", "DELIVERED")
                put("timestamp", System.currentTimeMillis())
            }
            sendRemoteEnvelope(targetMailbox, ackJson.toString(), isEmergency = false)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending delivery ack", e)
        }
    }

    private fun wakeDeviceForEmergency(context: Context) {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            @Suppress("DEPRECATION")
            val wakeLock = pm?.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "MeshChat:InternetRelaySOSWakeLock"
            )
            wakeLock?.acquire(10000L)
        } catch (e: Exception) {
            Log.e(TAG, "Error waking device for SOS", e)
        }
    }

    // Export cached mule packets for opportunistic offline radio hopping
    fun exportMulePackets(): List<String> = muleOutbox.toList()

    fun ingestMulePackets(packets: List<String>) {
        packets.forEach {
            if (!muleOutbox.contains(it)) {
                muleOutbox.add(it)
            }
        }
        _relayState.value = _relayState.value.copy(pendingMuleCount = muleOutbox.size)
        if (_relayState.value.isRelayConnected) {
            flushMuleQueue()
        }
    }
}
