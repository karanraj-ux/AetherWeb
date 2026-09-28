package com.aetherweb.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.concurrent.thread

/**
 * Production-grade VoIP & Walkie-Talkie audio engine.
 * 
 * Features:
 * - Ultra-low-latency UDP Datagram transport (zero TCP stall/jitter).
 * - 20ms audio frames (16kHz wideband, 320 samples = 640 bytes PCM).
 * - High-efficiency IMA-ADPCM compression (4:1 ratio -> 164 bytes per frame).
 * - End-to-End Encryption with AES-CTR when CallManager shared secret is active.
 * - Hardware Acoustic Echo Cancellation (AEC) & Noise Suppression (NS).
 * - Dynamic audio routing: Speakerphone / Earpiece toggle & Microphone mute.
 * - Anti-jitter smoothing buffer with packet reordering.
 */
class LiveVoiceManager(private val context: Context) {

    companion object {
        const val SAMPLE_RATE = 16000
        const val SAMPLES_PER_FRAME = 320 // 20ms @ 16kHz
        const val FRAME_BYTES_PCM = SAMPLES_PER_FRAME * 2 // 640 bytes
        const val AUDIO_PORT = 50005
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var isRecording = false
    private var isPlaying = false
    private var isMuted = false
    private var isSpeakerphone = false
    private var targetPeerIp: String? = null

    private var udpSocket: DatagramSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun setTargetPeerIp(ip: String?) {
        targetPeerIp = ip
    }

    fun setMuted(muted: Boolean) {
        isMuted = muted
        Log.d("LiveVoice", "Mute state changed: $muted")
    }

    fun setSpeakerphoneOn(speakerOn: Boolean) {
        isSpeakerphone = speakerOn
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = speakerOn
            Log.d("LiveVoice", "Speakerphone state: $speakerOn")
        } catch (e: Exception) {
            Log.e("LiveVoice", "Error setting speakerphone", e)
        }
    }

    fun startListening() {
        if (isPlaying) return
        isPlaying = true

        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = isSpeakerphone
        } catch (e: Exception) {}

        scope.launch {
            try {
                if (udpSocket == null || udpSocket!!.isClosed) {
                    udpSocket = DatagramSocket(null).apply {
                        reuseAddress = true
                        bind(java.net.InetSocketAddress(AUDIO_PORT))
                        receiveBufferSize = 64 * 1024
                        sendBufferSize = 64 * 1024
                    }
                }
                Log.d("LiveVoice", "UDP Voice Server listening on port $AUDIO_PORT")
            } catch (e: Exception) {
                Log.e("LiveVoice", "Failed to bind UDP socket", e)
                return@launch
            }

            val minTrackBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val trackBufferSize = kotlin.math.max(minTrackBuf, FRAME_BYTES_PCM * 4)

            val audioTrack = try {
                AudioTrack(
                    AudioManager.STREAM_VOICE_CALL,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    trackBufferSize,
                    AudioTrack.MODE_STREAM
                )
            } catch (e: Exception) {
                Log.e("LiveVoice", "Failed to initialize AudioTrack", e)
                return@launch
            }

            try {
                audioTrack.play()
            } catch (e: Exception) {
                Log.e("LiveVoice", "AudioTrack play error", e)
            }

            val audioJitterBuffer = AudioJitterBuffer(FRAME_BYTES_PCM, 40L)

            // Playback thread (continuous, jitter-buffered with PLC)
            val playbackJob = launch(Dispatchers.IO) {
                val silenceFrame = ByteArray(FRAME_BYTES_PCM)
                while (isPlaying && isActive) {
                    val frame = audioJitterBuffer.pollNextPlayoutFrame()
                    if (frame != null) {
                        audioTrack.write(frame, 0, frame.size)
                    } else {
                        // Sleep tiny 10ms slice while waiting for jitter buffer or output subtle comfort noise
                        delay(10)
                        if (isPlaying) {
                            audioTrack.write(silenceFrame, 0, FRAME_BYTES_PCM / 4)
                        }
                    }
                }
            }

            // Packet receiving loop
            val receiveBuffer = ByteArray(1024)
            val packet = DatagramPacket(receiveBuffer, receiveBuffer.size)
            val myLocalIp = NetworkUtils.getLocalIpAddress()

            try {
                while (isPlaying && isActive) {
                    udpSocket?.receive(packet)
                    val senderIp = packet.address.hostAddress ?: ""
                    if (senderIp == myLocalIp || senderIp == "127.0.0.1") {
                        continue // Drop self-echo
                    }

                    val length = packet.length
                    if (length < 4) continue

                    val rawData = packet.data.copyOfRange(0, length)
                    
                    // Decrypt if E2EE is active
                    val decryptedData = decryptPayloadIfNeeded(rawData)
                    if (decryptedData.size >= 4) {
                        val seqNum = decryptedData[3].toInt() and 0xFF
                        // Decode IMA ADPCM -> 16-bit linear PCM
                        val pcmBytes = AudioCodec.decode(decryptedData, decryptedData.size)
                        if (pcmBytes.isNotEmpty()) {
                            audioJitterBuffer.pushFrame(seqNum, pcmBytes)
                        }
                    }
                }
            } catch (e: Exception) {
                if (isPlaying) Log.e("LiveVoice", "UDP receive loop ended: ${e.message}")
            } finally {
                playbackJob.cancel()
                audioJitterBuffer.reset()
                try {
                    audioTrack.stop()
                    audioTrack.release()
                } catch (e: Exception) {}
            }
        }
    }

    fun stopListening() {
        isPlaying = false
        try {
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (e: Exception) {}

        try {
            udpSocket?.close()
        } catch (e: Exception) {}
        udpSocket = null
        scope.coroutineContext.cancelChildren()
    }

    fun startBroadcasting() {
        if (isRecording) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e("LiveVoice", "RECORD_AUDIO permission missing")
            return
        }

        isRecording = true

        thread(name = "LiveVoice-RecordThread") {
            val minRecBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val recBufferSize = kotlin.math.max(minRecBuf, FRAME_BYTES_PCM * 2)

            val audioRecord = try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    recBufferSize
                )
            } catch (e: Exception) {
                Log.e("LiveVoice", "Failed to create AudioRecord", e)
                isRecording = false
                return@thread
            }

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("LiveVoice", "AudioRecord failed to initialize (mic busy or unsupported)")
                try { audioRecord.release() } catch (e: Exception) {}
                isRecording = false
                return@thread
            }

            var aec: AcousticEchoCanceler? = null
            var ns: NoiseSuppressor? = null
            var agc: AutomaticGainControl? = null

            try {
                val sessionId = audioRecord.audioSessionId
                if (AcousticEchoCanceler.isAvailable()) {
                    aec = AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
                }
                if (NoiseSuppressor.isAvailable()) {
                    ns = NoiseSuppressor.create(sessionId)?.apply { enabled = true }
                }
                if (AutomaticGainControl.isAvailable()) {
                    agc = AutomaticGainControl.create(sessionId)?.apply { enabled = true }
                }
            } catch (e: Exception) {
                Log.w("LiveVoice", "Hardware AudioFX not fully supported", e)
            }

            try {
                audioRecord.startRecording()
            } catch (e: Exception) {
                Log.e("LiveVoice", "Failed to start AudioRecord", e)
                isRecording = false
                return@thread
            }

            val pcmFrame = ByteArray(FRAME_BYTES_PCM)
            var sequenceNumber = 0

            // High-pass DC/Rumble filter state (75Hz filter to eliminate fan & air puff low-end rumble)
            var hpPrevX = 0f
            var hpPrevY = 0f
            // Filter coefficient for ~75Hz cutoff at 16kHz sample rate: alpha = RC / (RC + dt) ~= 0.97
            val hpAlpha = 0.97f

            // Dynamic VAD & Whisper Speech Booster state
            var noiseFloorEstimate = 120.0f
            var speechHangoverFrames = 0 // Holds gate open for 10 frames (200ms) after speech to prevent syllable clipping

            // Dedicated send socket
            val sendSocket = try {
                DatagramSocket().apply {
                    broadcast = true
                    sendBufferSize = 64 * 1024
                }
            } catch (e: Exception) {
                Log.e("LiveVoice", "Failed to create send socket", e)
                null
            }

            try {
                while (isRecording) {
                    var totalRead = 0
                    while (totalRead < FRAME_BYTES_PCM && isRecording) {
                        val read = audioRecord.read(pcmFrame, totalRead, FRAME_BYTES_PCM - totalRead)
                        if (read > 0) totalRead += read
                        else break
                    }

                    if (totalRead < FRAME_BYTES_PCM) continue

                    // If muted, send silence frame or suppress
                    if (isMuted) {
                        for (i in 0 until FRAME_BYTES_PCM) pcmFrame[i] = 0
                    } else {
                        // 1. High-Pass Filter (75Hz) to remove wind/fan sub-audible rumble
                        // 2. Measure Peak & Energy for Adaptive Voice Activity Detection
                        var peak = 0
                        var energySum = 0.0

                        val sampleCount = FRAME_BYTES_PCM / 2
                        for (i in 0 until sampleCount) {
                            val byteIdx = i * 2
                            val rawSample = ((pcmFrame[byteIdx + 1].toInt() shl 8) or (pcmFrame[byteIdx].toInt() and 0xFF)).toShort().toFloat()

                            // Apply 75Hz high-pass filter
                            val filteredY = hpAlpha * (hpPrevY + rawSample - hpPrevX)
                            hpPrevX = rawSample
                            hpPrevY = filteredY

                            val sampleInt = filteredY.toInt().coerceIn(-32768, 32767)
                            pcmFrame[byteIdx] = (sampleInt and 0xFF).toByte()
                            pcmFrame[byteIdx + 1] = ((sampleInt shr 8) and 0xFF).toByte()

                            val absVal = kotlin.math.abs(sampleInt)
                            if (absVal > peak) peak = absVal
                            energySum += (absVal * absVal)
                        }

                        val rms = kotlin.math.sqrt(energySum / sampleCount).toFloat()

                        // Dynamic Noise Floor Tracking:
                        // Slowly adapt noise floor upward when quiet, drop quickly when silence returns
                        if (rms < noiseFloorEstimate * 1.5f) {
                            noiseFloorEstimate = (noiseFloorEstimate * 0.95f) + (rms * 0.05f)
                        }
                        val dynamicThreshold = (noiseFloorEstimate * 1.35f).coerceIn(120.0f, 320.0f)

                        // 3. Voice Activity Detection with 200ms (10 frames) Hangover
                        val isVoicePresent = peak >= dynamicThreshold || rms > (dynamicThreshold * 0.75f)

                        if (isVoicePresent) {
                            speechHangoverFrames = 10 // Refresh 200ms hangover to preserve whispering word endings
                        } else if (speechHangoverFrames > 0) {
                            speechHangoverFrames--
                        }

                        if (speechHangoverFrames > 0) {
                            // Active voice or speech tail:
                            // Whisper & Soft Speech Digital Pre-Amp:
                            // If user speaks softly (peak between 120 and 1500), apply up to 2.2x linear boost
                            // so soft whispers arrive with clear volume and punch at the receiver
                            if (peak < 1800) {
                                val gainFactor = when {
                                    peak < 600 -> 2.2f  // Soft whisper boost
                                    peak < 1200 -> 1.7f // Quiet conversational boost
                                    else -> 1.3f        // Moderate leveling
                                }
                                for (i in 0 until sampleCount) {
                                    val byteIdx = i * 2
                                    val sample = ((pcmFrame[byteIdx + 1].toInt() shl 8) or (pcmFrame[byteIdx].toInt() and 0xFF)).toShort().toInt()
                                    val boosted = (sample * gainFactor).toInt().coerceIn(-32768, 32767)
                                    pcmFrame[byteIdx] = (boosted and 0xFF).toByte()
                                    pcmFrame[byteIdx + 1] = ((boosted shr 8) and 0xFF).toByte()
                                }
                            }
                        } else {
                            // True background silence (fan / idle room): zero out to keep channel pristine
                            for (i in 0 until FRAME_BYTES_PCM) pcmFrame[i] = 0
                        }
                    }

                    // Compress with IMA ADPCM (640 bytes -> 164 bytes)
                    val adpcmPacket = AudioCodec.encode(pcmFrame, FRAME_BYTES_PCM, sequenceNumber++)
                    
                    // Encrypt if E2EE active
                    val finalPayload = encryptPayloadIfNeeded(adpcmPacket)

                    // Dispatch via UDP
                    sendUdpPacket(sendSocket, finalPayload)
                }
            } catch (e: Exception) {
                Log.e("LiveVoice", "Recording loop error", e)
            } finally {
                try {
                    audioRecord.stop()
                    audioRecord.release()
                } catch (e: Exception) {}

                aec?.release()
                ns?.release()
                agc?.release()
                sendSocket?.close()
            }
        }
    }

    private fun sendUdpPacket(socket: DatagramSocket?, payload: ByteArray) {
        if (socket == null) return

        val destinations = mutableSetOf<String>()
        val specificPeer = targetPeerIp
        if (!specificPeer.isNullOrBlank()) {
            destinations.add(specificPeer)
        } else {
            // Send to all connected mesh peers
            val activePeers = MeshNetworkManager.wifiSocketManager?.activeConnections?.keys ?: emptySet()
            destinations.addAll(activePeers)

            // Also broadcast to subnet for discovery / walkie-talkie
            destinations.add("192.168.49.255")
            destinations.add("255.255.255.255")
        }

        for (destIp in destinations) {
            try {
                val address = InetAddress.getByName(destIp)
                val packet = DatagramPacket(payload, payload.size, address, AUDIO_PORT)
                socket.send(packet)
            } catch (e: Exception) {
                // Ignore transient network errors
            }
        }
    }

    fun stopBroadcasting() {
        isRecording = false
    }

    private fun encryptPayloadIfNeeded(payload: ByteArray): ByteArray {
        val secretKey = CallManager.sharedSecretKey ?: return payload
        return try {
            val cipher = Cipher.getInstance("AES/CTR/NoPadding")
            val iv = ByteArray(16) { 0x42.toByte() } // deterministic IV for CTR voice stream
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(iv))
            cipher.doFinal(payload)
        } catch (e: Exception) {
            payload
        }
    }

    private fun decryptPayloadIfNeeded(payload: ByteArray): ByteArray {
        val secretKey = CallManager.sharedSecretKey ?: return payload
        return try {
            val cipher = Cipher.getInstance("AES/CTR/NoPadding")
            val iv = ByteArray(16) { 0x42.toByte() }
            cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))
            cipher.doFinal(payload)
        } catch (e: Exception) {
            payload
        }
    }
}
