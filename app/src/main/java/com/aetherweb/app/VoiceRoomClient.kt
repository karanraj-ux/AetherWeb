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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Collections
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.sqrt

/**
 * Voice Room Phase 3 — app-side voice client.
 *
 * Used by BOTH the host phone (connects to its own server at 127.0.0.1, so the
 * SFU treats everyone uniformly) and app peers (connect to the host's WiFi IP).
 * Web guests use the browser equivalent in WebPortalTemplate.kt.
 *
 * Audio: 16kHz mono PCM16, 20ms frames (640 bytes), same constants as
 * LiveVoiceManager. Capture is VAD-gated (silence is never transmitted) and
 * runs through hardware AEC/NS/AGC when available.
 */
class VoiceRoomClient(private val context: Context) {

    companion object {
        private const val TAG = "VoiceRoomClient"
        const val SAMPLE_RATE = 16000
        const val SAMPLES_PER_FRAME = 320 // 20ms @ 16kHz
        const val FRAME_BYTES_PCM = SAMPLES_PER_FRAME * 2 // 640
        const val FRAME_TYPE_AUDIO: Byte = 0x01
        const val VAD_THRESHOLD = 600 // PCM RMS energy
        const val VAD_HANGOVER_FRAMES = 25 // ~500ms

        /**
         * Phase 4: best-effort host IP for app peers. On an Android hotspot the
         * gateway is the host's IP (usually x.x.x.1); derive it from our own.
         */
        fun guessHostIp(): String {
            return try {
                val own = NetworkUtils.getLocalIpAddress()
                if (own.contains(".")) own.substringBeforeLast(".") + ".1" else "192.168.49.1"
            } catch (e: Exception) {
                "192.168.49.1"
            }
        }
    }

    private var ws: WebSocket? = null
    private var httpClient: OkHttpClient? = null

    @Volatile private var micOn = false
    @Volatile private var running = false

    private var recordThread: Thread? = null
    private var playThread: Thread? = null
    private val playQueue = LinkedBlockingQueue<ByteArray>(64)
    private val mutedByMe = Collections.synchronizedSet(mutableSetOf<String>())
    @Volatile var deafen = false

    private var selfId = ""
    private var selfName = ""
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun isConnected(): Boolean = running

    /**
     * Connect to the voice SFU.
     * @param hostIp  hotspot/host IP, or "127.0.0.1" when this phone IS the host
     * @param selfId stable sender id (host: "host-self", app peer: node id, web: pid)
     * @param kind   "host" | "member"
     */
    fun connect(hostIp: String, selfId: String, selfName: String, selfEmoji: String, kind: String) {
        if (running) return
        this.selfId = selfId
        this.selfName = selfName
        running = true

        val url = "ws://$hostIp:8080/ws-voice" +
            "?pid=" + URLEncoder.encode(selfId, "UTF-8") +
            "&sender=" + URLEncoder.encode(selfName, "UTF-8") +
            "&emoji=" + URLEncoder.encode(selfEmoji, "UTF-8") +
            "&kind=" + URLEncoder.encode(kind, "UTF-8")

        httpClient = OkHttpClient.Builder()
            .pingInterval(30, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder().url(url).build()
        ws = httpClient!!.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Voice WS open: $url")
                sendControl(JSONObject().apply {
                    put("type", "join")
                    put("name", selfName)
                    put("emoji", selfEmoji)
                    put("pid", selfId)
                })
                startPlayback()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val obj = JSONObject(text)
                    if (obj.optString("type") == "voice_state") {
                        applyVoiceState(obj.optJSONArray("members"))
                    }
                } catch (e: Exception) { /* ignore */ }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (deafen) return
                val data = bytes.toByteArray()
                if (data.size < 3 || data[0] != FRAME_TYPE_AUDIO) return
                val idLen = data[1].toInt() and 0xFF
                if (data.size < 2 + idLen) return
                val fromId = String(data, 2, idLen, Charsets.UTF_8)
                if (fromId == selfId) return // never play our own frames
                if (mutedByMe.contains(fromId)) return
                val pcm = data.copyOfRange(2 + idLen, data.size)
                // Bounded queue: drop oldest on overflow to keep latency low.
                if (!playQueue.offer(pcm)) {
                    playQueue.poll()
                    playQueue.offer(pcm)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Voice WS failure", t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Voice WS closed: $reason")
            }
        })
        // Register self locally so the dashboard shows us immediately.
        VoiceRoomManager.upsertMember(
            VoiceMember(id = selfId, name = selfName, emoji = selfEmoji, kind = kind, isSelf = true)
        )
    }

    fun setMicOn(on: Boolean) {
        if (on == micOn) {
            // Still notify the server so remote mic icons stay correct.
            sendControl(JSONObject().apply { put("type", "mic"); put("on", on) })
            return
        }
        if (on && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "RECORD_AUDIO not granted; mic stays off")
            return
        }
        micOn = on
        sendControl(JSONObject().apply { put("type", "mic"); put("on", on) })
        if (on) startCapture() else stopCapture()
    }

    fun setMutedByMe(id: String, muted: Boolean) {
        if (muted) mutedByMe.add(id) else mutedByMe.remove(id)
    }

    fun setDeafen(deaf: Boolean) {
        deafen = deaf
    }

    fun disconnect() {
        running = false
        micOn = false
        try {
            sendControl(JSONObject().apply { put("type", "leave") })
        } catch (e: Exception) { /* ignore */ }
        try { ws?.close(1000, "leave") } catch (e: Exception) { /* ignore */ }
        ws = null
        try { httpClient?.dispatcher?.executorService?.shutdown() } catch (e: Exception) { /* ignore */ }
        httpClient = null
        stopCapture()
        stopPlayback()
        VoiceRoomManager.removeMember(selfId)
    }

    private fun sendControl(obj: JSONObject) {
        try { ws?.send(obj.toString()) } catch (e: Exception) { /* ignore */ }
    }

    private fun applyVoiceState(members: org.json.JSONArray?) {
        if (members == null) return
        try {
            VoiceRoomManager.markActiveAsGuest()
            val seen = mutableSetOf<String>()
            for (i in 0 until members.length()) {
                val o = members.getJSONObject(i)
                val id = o.optString("id")
                if (id.isBlank()) continue
                seen.add(id)
                val local = VoiceRoomManager.state.value.members[id]
                VoiceRoomManager.upsertMember(
                    VoiceMember(
                        id = id,
                        name = o.optString("name", local?.name ?: "Guest"),
                        emoji = o.optString("emoji", local?.emoji ?: ""),
                        kind = o.optString("kind", local?.kind ?: "member"),
                        micOn = o.optBoolean("micOn", false),
                        mutedByHost = o.optBoolean("mutedByHost", false),
                        speaking = o.optBoolean("speaking", false),
                        mutedByMe = local?.mutedByMe ?: mutedByMe.contains(id),
                        isSelf = local?.isSelf ?: (id == selfId)
                    )
                )
            }
            // Drop members the server no longer lists (except self).
            VoiceRoomManager.state.value.members.keys
                .filter { it !in seen && it != selfId }
                .forEach { VoiceRoomManager.removeMember(it) }
        } catch (e: Exception) { /* ignore malformed state */ }
    }

    // ---------------- Capture (mic -> SFU) ----------------

    private fun startCapture() {
        if (recordThread?.isAlive == true) return
        recordThread = thread(name = "VoiceRoom-Record", isDaemon = true) {
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            val rec = try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minBuf, FRAME_BYTES_PCM * 2)
                )
            } catch (e: Exception) {
                Log.e(TAG, "AudioRecord create failed", e)
                return@thread
            }
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord not initialized")
                try { rec.release() } catch (e: Exception) { /* ignore */ }
                return@thread
            }
            var aec: AcousticEchoCanceler? = null
            var ns: NoiseSuppressor? = null
            var agc: AutomaticGainControl? = null
            try {
                val sid = rec.audioSessionId
                if (AcousticEchoCanceler.isAvailable()) aec = AcousticEchoCanceler.create(sid)?.apply { enabled = true }
                if (NoiseSuppressor.isAvailable()) ns = NoiseSuppressor.create(sid)?.apply { enabled = true }
                if (AutomaticGainControl.isAvailable()) agc = AutomaticGainControl.create(sid)?.apply { enabled = true }
            } catch (e: Exception) { Log.w(TAG, "AudioFX unavailable", e) }
            try { rec.startRecording() } catch (e: Exception) {
                Log.e(TAG, "startRecording failed", e)
                try { rec.release() } catch (ex: Exception) { /* ignore */ }
                return@thread
            }

            val idBytes = selfId.toByteArray(Charsets.UTF_8).let { it.copyOf(minOf(it.size, 255)) }
            val pcm = ByteArray(FRAME_BYTES_PCM)
            var hpPrevX = 0f
            var hpPrevY = 0f
            val hpAlpha = 0.97f
            var hangover = 0

            while (micOn && running) {
                val read = rec.read(pcm, 0, pcm.size)
                if (read <= 0) continue
                // High-pass DC filter + RMS energy in one pass.
                var sum = 0L
                var i = 0
                while (i + 1 < read) {
                    var s = (pcm[i].toInt() and 0xFF) or (pcm[i + 1].toInt() shl 8)
                    val x = s / 32768f
                    val y = hpAlpha * (hpPrevY + x - hpPrevX)
                    hpPrevX = x
                    hpPrevY = y
                    s = (y * 32768f).toInt().coerceIn(-32768, 32767)
                    pcm[i] = (s and 0xFF).toByte()
                    pcm[i + 1] = ((s shr 8) and 0xFF).toByte()
                    sum += (s * s).toLong()
                    i += 2
                }
                val rms = if (read > 1) sqrt(sum.toDouble() / (read / 2)).toInt() else 0
                if (rms > VAD_THRESHOLD) hangover = VAD_HANGOVER_FRAMES
                else if (hangover > 0) hangover--
                if (hangover == 0) continue // VAD gate: silence is never transmitted

                val frame = ByteArray(2 + idBytes.size + read)
                frame[0] = FRAME_TYPE_AUDIO
                frame[1] = idBytes.size.toByte()
                System.arraycopy(idBytes, 0, frame, 2, idBytes.size)
                System.arraycopy(pcm, 0, frame, 2 + idBytes.size, read)
                try { ws?.send(frame.toByteString()) } catch (e: Exception) { /* ignore */ }
            }
            try { rec.stop() } catch (e: Exception) { /* ignore */ }
            try { rec.release() } catch (e: Exception) { /* ignore */ }
            try { aec?.release() } catch (e: Exception) { /* ignore */ }
            try { ns?.release() } catch (e: Exception) { /* ignore */ }
            try { agc?.release() } catch (e: Exception) { /* ignore */ }
        }
    }

    private fun stopCapture() {
        recordThread?.join(500)
        recordThread = null
    }

    // ---------------- Playback (SFU -> speaker) ----------------

    private fun startPlayback() {
        if (playThread?.isAlive == true) return
        playThread = thread(name = "VoiceRoom-Play", isDaemon = true) {
            try {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                audioManager.isSpeakerphoneOn = true
            } catch (e: Exception) { /* ignore */ }
            val minBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            val track = try {
                AudioTrack(
                    AudioManager.STREAM_VOICE_CALL,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minBuf, FRAME_BYTES_PCM * 4),
                    AudioTrack.MODE_STREAM
                )
            } catch (e: Exception) {
                Log.e(TAG, "AudioTrack create failed", e)
                return@thread
            }
            try { track.play() } catch (e: Exception) { /* ignore */ }
            val silence = ByteArray(FRAME_BYTES_PCM / 4)
            while (running) {
                val frame = playQueue.poll(50, TimeUnit.MILLISECONDS)
                try {
                    if (frame != null) track.write(frame, 0, frame.size)
                    else if (running) track.write(silence, 0, silence.size)
                } catch (e: Exception) { /* ignore */ }
            }
            try { track.stop() } catch (e: Exception) { /* ignore */ }
            try { track.release() } catch (e: Exception) { /* ignore */ }
        }
    }

    private fun stopPlayback() {
        playThread?.join(500)
        playThread = null
        playQueue.clear()
    }
}
