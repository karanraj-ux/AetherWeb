package com.aetherweb.app

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

enum class CallState {
    IDLE, INCOMING, OUTGOING, ACTIVE
}

data class CallSession(
    val state: CallState = CallState.IDLE,
    val peerId: String = "",
    val peerName: String = "",
    val peerIp: String = "",
    val isEncrypted: Boolean = false,
    val connectionType: String = "Wi-Fi Direct",
    val callDurationSeconds: Int = 0,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isVideoEnabled: Boolean = false,
    val isMinimized: Boolean = false
)

object CallManager {
    private val _callSession = MutableStateFlow(CallSession())
    val callSession: StateFlow<CallSession> = _callSession.asStateFlow()

    private var myKeyPair: KeyPair? = null
    var sharedSecretKey: SecretKey? = null
    private var peerPublicKeyStrTemp: String? = null
    private var timerJob: Job? = null
    private var callTimeoutJob: Job? = null

    private var liveVoiceManager: LiveVoiceManager? = null
    private var liveVideoManager: LiveVideoManager? = null
    private var appContext: Context? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun init(context: Context) {
        appContext = context.applicationContext
        if (liveVoiceManager == null) {
            liveVoiceManager = LiveVoiceManager(context.applicationContext)
        }
        if (liveVideoManager == null) {
            liveVideoManager = LiveVideoManager(context.applicationContext)
        }
    }

    fun getLiveVideoManager(): LiveVideoManager? = liveVideoManager
    fun getLiveVoiceManager(): LiveVoiceManager? = liveVoiceManager

    fun initiateCall(peerId: String, peerName: String, peerIp: String = "", sendMessage: (String) -> Unit) {
        if (_callSession.value.state != CallState.IDLE) return

        myKeyPair = generateECKeyPair()
        val pubKeyBase64 = Base64.encodeToString(myKeyPair!!.public.encoded, Base64.NO_WRAP)
        val myIp = NetworkUtils.getLocalIpAddress()

        _callSession.update {
            CallSession(
                state = CallState.OUTGOING,
                peerId = peerId,
                peerName = peerName,
                peerIp = peerIp,
                isEncrypted = false,
                connectionType = "Wi-Fi Direct"
            )
        }

        // Outgoing call timeout (45s)
        callTimeoutJob?.cancel()
        callTimeoutJob = scope.launch {
            delay(45_000)
            if (_callSession.value.state == CallState.OUTGOING) {
                Log.i("CallManager", "Outgoing call timed out after 45s with no answer.")
                endCall(sendMessage)
            }
        }

        val offerPacket = com.aetherweb.app.protocol.MeshPacket.CallOffer(
            callerId = MeshNetworkManager.localNodeId,
            callerName = MeshNetworkManager.uiState.value.localUserName,
            callerIp = myIp,
            targetPeerId = peerId,
            pubKey = pubKeyBase64
        )
        sendMessage(offerPacket.toJsonString())
    }

    fun handleCallOffer(payload: JSONObject, sendMessage: (String) -> Unit) {
        val targetPeerId = payload.optString("targetPeerId")
        // If targeted to a specific peer and it's not us, ignore
        if (targetPeerId.isNotBlank() && targetPeerId != MeshNetworkManager.localNodeId) {
            return
        }

        if (_callSession.value.state != CallState.IDLE) {
            // Send busy signal
            val busyPacket = com.aetherweb.app.protocol.MeshPacket.CallEnd(
                senderId = MeshNetworkManager.localNodeId,
                reason = "busy"
            )
            sendMessage(busyPacket.toJsonString())
            return
        }

        val callerId = payload.optString("callerId", "Unknown")
        val callerName = payload.optString("callerName", "Nearby Peer")
        val callerIp = payload.optString("callerIp", "")
        val callerPubKeyStr = payload.optString("pubKey", "")

        peerPublicKeyStrTemp = callerPubKeyStr

        _callSession.update {
            CallSession(
                state = CallState.INCOMING,
                peerId = callerId,
                peerName = callerName,
                peerIp = callerIp,
                isEncrypted = false,
                connectionType = "Wi-Fi Direct"
            )
        }

        // Incoming call timeout (45s) -> auto-decline to missed call
        callTimeoutJob?.cancel()
        callTimeoutJob = scope.launch {
            delay(45_000)
            if (_callSession.value.state == CallState.INCOMING) {
                Log.i("CallManager", "Incoming call timed out after 45s.")
                resetCall()
            }
        }

        appContext?.let { ctx ->
            if (!MainActivity.isAppInForeground) {
                NotificationHelper.showIncomingCallNotification(ctx, callerName, callerId)
            }
        }
    }

    fun answerCall(sendMessage: (String) -> Unit) {
        callTimeoutJob?.cancel()
        callTimeoutJob = null
        appContext?.let { NotificationHelper.cancelCallNotification(it) }
        val peerPubKeyStr = peerPublicKeyStrTemp
        myKeyPair = generateECKeyPair()
        val myPubKeyBase64 = Base64.encodeToString(myKeyPair!!.public.encoded, Base64.NO_WRAP)
        val myIp = NetworkUtils.getLocalIpAddress()

        if (!peerPubKeyStr.isNullOrBlank()) {
            try {
                val peerPubKey = getPublicKeyFromString(peerPubKeyStr)
                sharedSecretKey = generateSharedSecret(myKeyPair!!.private, peerPubKey)
            } catch (e: Exception) {
                Log.e("CallManager", "Key exchange error", e)
            }
        }

        _callSession.update {
            it.copy(
                state = CallState.ACTIVE,
                isEncrypted = (sharedSecretKey != null),
                callDurationSeconds = 0
            )
        }

        val answerPacket = com.aetherweb.app.protocol.MeshPacket.CallAnswer(
            calleeId = MeshNetworkManager.localNodeId,
            calleeIp = myIp,
            pubKey = myPubKeyBase64
        )
        val payloadObj = answerPacket.toJson().apply {
            put("responderId", MeshNetworkManager.localNodeId)
            put("responderIp", myIp)
        }
        sendMessage(payloadObj.toString())

        startAudioSession()
    }

    fun handleCallAnswer(payload: JSONObject) {
        if (_callSession.value.state != CallState.OUTGOING) return
        val responderPubKeyStr = payload.optString("pubKey", "")
        val responderIp = payload.optString("responderIp", "")

        if (responderPubKeyStr.isNotBlank() && myKeyPair != null) {
            try {
                val responderPubKey = getPublicKeyFromString(responderPubKeyStr)
                sharedSecretKey = generateSharedSecret(myKeyPair!!.private, responderPubKey)
            } catch (e: Exception) {
                Log.e("CallManager", "Key exchange answer error", e)
            }
        }

        _callSession.update {
            it.copy(
                state = CallState.ACTIVE,
                peerIp = if (responderIp.isNotBlank()) responderIp else it.peerIp,
                isEncrypted = (sharedSecretKey != null),
                callDurationSeconds = 0
            )
        }

        startAudioSession()
    }

    private fun startAudioSession() {
        startCallTimer()
        val peerIp = _callSession.value.peerIp.takeIf { it.isNotBlank() }
        liveVoiceManager?.let { vm ->
            vm.setTargetPeerIp(peerIp)
            vm.setMuted(_callSession.value.isMuted)
            vm.setSpeakerphoneOn(_callSession.value.isSpeakerOn)
            vm.startListening()
            vm.startBroadcasting()
        }
        if (_callSession.value.isVideoEnabled) {
            liveVideoManager?.startVideoSession(peerIp)
        }
    }

    private fun startCallTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive && _callSession.value.state == CallState.ACTIVE) {
                delay(1000)
                _callSession.update { it.copy(callDurationSeconds = it.callDurationSeconds + 1) }
            }
        }
    }

    fun toggleMute() {
        val newMute = !_callSession.value.isMuted
        _callSession.update { it.copy(isMuted = newMute) }
        liveVoiceManager?.setMuted(newMute)
    }

    fun toggleSpeaker() {
        val newSpeaker = !_callSession.value.isSpeakerOn
        _callSession.update { it.copy(isSpeakerOn = newSpeaker) }
        liveVoiceManager?.setSpeakerphoneOn(newSpeaker)
    }

    fun toggleVideo() {
        val newVideo = !_callSession.value.isVideoEnabled
        _callSession.update { it.copy(isVideoEnabled = newVideo) }
        val peerIp = _callSession.value.peerIp.takeIf { it.isNotBlank() }
        if (newVideo && _callSession.value.state == CallState.ACTIVE) {
            liveVideoManager?.startVideoSession(peerIp)
        } else {
            liveVideoManager?.stopVideoSession()
        }
    }

    fun toggleMinimize() {
        _callSession.update { it.copy(isMinimized = !it.isMinimized) }
    }

    fun setMinimized(minimized: Boolean) {
        _callSession.update { it.copy(isMinimized = minimized) }
    }

    fun endCall(sendMessage: (String) -> Unit) {
        val endPacket = com.aetherweb.app.protocol.MeshPacket.CallEnd(
            senderId = MeshNetworkManager.localNodeId
        )
        sendMessage(endPacket.toJsonString())
        resetCall()
    }

    fun handleCallEnd() {
        resetCall()
    }

    fun declineCall(sendMessage: (String) -> Unit) {
        endCall(sendMessage)
    }

    private fun resetCall() {
        val session = _callSession.value
        appContext?.let { ctx ->
            NotificationHelper.cancelCallNotification(ctx)
            if (session.state != CallState.IDLE) {
                val callType = when {
                    session.state == CallState.OUTGOING -> "OUTGOING"
                    session.state == CallState.INCOMING && session.callDurationSeconds == 0 -> "MISSED"
                    else -> "INCOMING"
                }
                scope.launch(Dispatchers.IO) {
                    try {
                        com.aetherweb.app.data.MeshChatDatabase.getDatabase(ctx).callLogDao().insertCallLog(
                            com.aetherweb.app.data.CallLogEntity(
                                peerId = session.peerId,
                                peerName = session.peerName.ifBlank { "Nearby Peer" },
                                callType = callType,
                                durationSeconds = session.callDurationSeconds,
                                isVideo = session.isVideoEnabled,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        timerJob?.cancel()
        timerJob = null
        callTimeoutJob?.cancel()
        callTimeoutJob = null
        liveVoiceManager?.stopBroadcasting()
        liveVoiceManager?.stopListening()
        liveVoiceManager?.setTargetPeerIp(null)
        liveVideoManager?.stopVideoSession()
        liveVideoManager?.setTargetPeerIp(null)

        _callSession.update { CallSession() }
        myKeyPair = null
        sharedSecretKey = null
        peerPublicKeyStrTemp = null
    }

    private fun generateECKeyPair(): KeyPair {
        val keyGen = KeyPairGenerator.getInstance("EC")
        keyGen.initialize(256)
        return keyGen.generateKeyPair()
    }

    private fun getPublicKeyFromString(base64Str: String): PublicKey {
        val bytes = Base64.decode(base64Str, Base64.NO_WRAP)
        return KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(bytes))
    }

    private fun generateSharedSecret(privateKey: java.security.PrivateKey, publicKey: PublicKey): SecretKey {
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(privateKey)
        keyAgreement.doPhase(publicKey, true)
        val secretBytes = keyAgreement.generateSecret()
        val derivedKey = java.security.MessageDigest.getInstance("SHA-256").digest(secretBytes)
        return SecretKeySpec(derivedKey, "AES")
    }
}
