package com.aetherweb.app

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class MusicTrack(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val artist: String = "Offline Audio",
    val durationMs: Long = 0L,
    val uriString: String,
    val isStream: Boolean = false,
    val localFilePath: String? = null
)

data class MusicPlayerState(
    val currentTrack: MusicTrack? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playlist: List<MusicTrack> = emptyList(),
    val currentIndex: Int = -1,
    val isPartyModeHost: Boolean = false,   // Broadcaster DJ
    val isPartyModeListener: Boolean = false, // Synced to mesh DJ
    val partyHostName: String = "",
    val isShuffle: Boolean = false,
    val isRepeat: Boolean = false,
    val volume: Float = 1.0f
)

/**
 * Universal Mesh Music Engine:
 * 1. Plays local downloaded files (MP3, M4A, WAV, FLAC, OGG).
 * 2. Plays live internet audio streams & radio URLs.
 * 3. Broadcasts "Party Mode / Silent Disco" synchronization packets over local Mesh WiFi / Hotspot.
 * 4. Enables all nearby Android phones & web guests to listen in sync!
 */
object MeshMusicManager {
    private const val TAG = "MeshMusicManager"

    private val _state = MutableStateFlow(MusicPlayerState())
    val state: StateFlow<MusicPlayerState> = _state.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var appContext: Context? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())

    private val progressRunnable = object : Runnable {
        override fun run() {
            try {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        val pos = mp.currentPosition.toLong()
                        val dur = mp.duration.toLong().coerceAtLeast(0L)
                        _state.update { it.copy(currentPositionMs = pos, durationMs = if (dur > 0) dur else it.durationMs) }

                        // If DJ Host, broadcast periodic sync packet every 5 seconds
                        if (_state.value.isPartyModeHost && pos % 5000 < 500) {
                            broadcastSyncPacket("sync", pos)
                        }
                    }
                }
            } catch (e: Exception) {}
            handler.postDelayed(this, 500)
        }
    }

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        loadDefaultSampleTracks()
        scanLocalCacheTracks(context)
        handler.post(progressRunnable)
    }

    private fun loadDefaultSampleTracks() {
        val samples = listOf(
            MusicTrack(
                title = "Lo-Fi Beats (Offline Chill)",
                artist = "Mesh Audio Beats",
                durationMs = 180000L,
                uriString = "https://stream.zeno.fm/f3wvbbqmdg8uv",
                isStream = true
            ),
            MusicTrack(
                title = "Cyberwave Radio (Live Stream)",
                artist = "Synthwave Network",
                durationMs = 0L,
                uriString = "https://stream.nightride.fm/nightride.m4a",
                isStream = true
            )
        )
        _state.update { it.copy(playlist = samples, currentIndex = 0, currentTrack = samples.firstOrNull()) }
    }

    fun scanLocalCacheTracks(context: Context) {
        scope.launch(Dispatchers.IO) {
            val list = mutableListOf<MusicTrack>()
            val cacheFiles = MeshStorageManager.getCacheDir(context).listFiles()
            cacheFiles?.filter { MeshStorageManager.isAudioFile(it.name) }?.forEach { f ->
                list.add(
                    MusicTrack(
                        title = f.nameWithoutExtension.replace("web_audio_", "Voice Note ").replace("_", " "),
                        artist = "Local Device File",
                        durationMs = 0L,
                        uriString = f.toURI().toString(),
                        isStream = false,
                        localFilePath = f.absolutePath
                    )
                )
            }
            if (list.isNotEmpty()) {
                _state.update { current ->
                    val combined = (current.playlist + list).distinctBy { it.uriString }
                    current.copy(playlist = combined)
                }
            }
        }
    }

    fun addTrackFromUri(context: Context, uri: Uri, displayName: String?) {
        scope.launch(Dispatchers.IO) {
            val name = displayName ?: (uri.lastPathSegment ?: "Offline Track")
            val track = MusicTrack(
                title = name.substringBeforeLast("."),
                artist = "Local Audio",
                uriString = uri.toString(),
                isStream = false
            )
            _state.update { it.copy(playlist = it.playlist + track) }
            if (_state.value.currentTrack == null) {
                playTrack(track)
            }
        }
    }

    fun addStreamUrl(title: String, url: String) {
        val track = MusicTrack(
            title = if (title.isBlank()) "Online Radio Stream" else title,
            artist = "Internet Stream",
            uriString = url.trim(),
            isStream = true
        )
        _state.update { it.copy(playlist = it.playlist + track) }
        playTrack(track)
    }

    fun playTrack(track: MusicTrack) {
        val index = _state.value.playlist.indexOfFirst { it.id == track.id }
        _state.update { it.copy(currentTrack = track, currentIndex = if (index != -1) index else it.currentIndex) }

        scope.launch(Dispatchers.IO) {
            try {
                releasePlayer()
                val ctx = appContext ?: return@launch
                val mp = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    if (track.uriString.startsWith("content://") || track.uriString.startsWith("file://")) {
                        setDataSource(ctx, Uri.parse(track.uriString))
                    } else if (track.localFilePath != null) {
                        setDataSource(track.localFilePath)
                    } else {
                        setDataSource(track.uriString)
                    }
                    setOnPreparedListener { preparedMp ->
                        preparedMp.start()
                        val dur = preparedMp.duration.toLong().coerceAtLeast(0L)
                        _state.update { it.copy(isPlaying = true, durationMs = dur) }
                        if (_state.value.isPartyModeHost) {
                            broadcastSyncPacket("play", 0L)
                        }
                    }
                    setOnCompletionListener {
                        onTrackCompleted()
                    }
                    setOnErrorListener { _, what, extra ->
                        Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                        _state.update { it.copy(isPlaying = false) }
                        true
                    }
                    prepareAsync()
                }
                mediaPlayer = mp
            } catch (e: Exception) {
                Log.e(TAG, "Error playing track ${track.title}", e)
                _state.update { it.copy(isPlaying = false) }
            }
        }
    }

    fun togglePlayPause() {
        val mp = mediaPlayer
        if (mp != null) {
            if (mp.isPlaying) {
                mp.pause()
                _state.update { it.copy(isPlaying = false) }
                if (_state.value.isPartyModeHost) broadcastSyncPacket("pause", mp.currentPosition.toLong())
            } else {
                mp.start()
                _state.update { it.copy(isPlaying = true) }
                if (_state.value.isPartyModeHost) broadcastSyncPacket("play", mp.currentPosition.toLong())
            }
        } else {
            val track = _state.value.currentTrack ?: _state.value.playlist.firstOrNull()
            if (track != null) playTrack(track)
        }
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.let { mp ->
            try {
                mp.seekTo(positionMs.toInt())
                _state.update { it.copy(currentPositionMs = positionMs) }
                if (_state.value.isPartyModeHost) broadcastSyncPacket("seek", positionMs)
            } catch (e: Exception) {}
        }
    }

    fun skipNext() {
        val list = _state.value.playlist
        if (list.isEmpty()) return
        val nextIdx = if (_state.value.isShuffle) {
            list.indices.random()
        } else {
            (_state.value.currentIndex + 1) % list.size
        }
        playTrack(list[nextIdx])
    }

    fun skipPrevious() {
        val list = _state.value.playlist
        if (list.isEmpty()) return
        val prevIdx = if (_state.value.currentIndex - 1 < 0) list.size - 1 else _state.value.currentIndex - 1
        playTrack(list[prevIdx])
    }

    fun toggleShuffle() {
        _state.update { it.copy(isShuffle = !it.isShuffle) }
    }

    fun toggleRepeat() {
        _state.update { it.copy(isRepeat = !it.isRepeat) }
    }

    fun togglePartyModeHost() {
        val newHost = !_state.value.isPartyModeHost
        _state.update { it.copy(isPartyModeHost = newHost, isPartyModeListener = false) }
        if (newHost) {
            broadcastSyncPacket("host_active", _state.value.currentPositionMs)
        }
    }

    fun togglePartyModeListener() {
        val newListener = !_state.value.isPartyModeListener
        _state.update { it.copy(isPartyModeListener = newListener, isPartyModeHost = false) }
    }

    private fun onTrackCompleted() {
        if (_state.value.isRepeat) {
            _state.value.currentTrack?.let { playTrack(it) }
        } else {
            skipNext()
        }
    }

    private fun releasePlayer() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {}
        mediaPlayer = null
    }

    /**
     * Broadcasts sync packet to mesh network so all nearby phones & web guests play the same audio.
     */
    private fun broadcastSyncPacket(action: String, positionMs: Long) {
        val track = _state.value.currentTrack ?: return
        val hostName = MeshNetworkManager.localNodeId
        val musicPacket = com.aetherweb.app.protocol.MeshPacket.MeshMusicSync(
            action = action,
            trackTitle = track.title,
            artist = track.artist,
            uri = track.uriString,
            isStream = track.isStream,
            positionMs = positionMs,
            hostName = hostName
        )
        MeshNetworkManager.meshRouter.routePacket(musicPacket)
        MeshNetworkManager.webServerManager?.broadcastMessage(musicPacket.toJsonString(), hostName)
    }

    /**
     * Handles incoming sync packets from the DJ Host.
     */
    fun handleIncomingSync(json: JSONObject) {
        if (_state.value.isPartyModeHost) return // Do not override if we are the host
        if (!_state.value.isPartyModeListener) return // Only listen if Party Mode is active

        try {
            val action = json.optString("action")
            val title = json.optString("trackTitle")
            val artist = json.optString("artist")
            val uri = json.optString("uri")
            val isStream = json.optBoolean("isStream")
            val pos = json.optLong("positionMs", 0L)
            val host = json.optString("hostName", "DJ")

            _state.update { it.copy(partyHostName = host) }

            val current = _state.value.currentTrack
            if (current == null || current.uriString != uri) {
                // Switch to host's track
                val syncedTrack = MusicTrack(
                    title = title,
                    artist = "$artist (via $host)",
                    uriString = uri,
                    isStream = isStream
                )
                playTrack(syncedTrack)
                seekTo(pos)
            } else {
                when (action) {
                    "pause" -> {
                        mediaPlayer?.takeIf { it.isPlaying }?.pause()
                        _state.update { it.copy(isPlaying = false) }
                    }
                    "play" -> {
                        mediaPlayer?.takeIf { !it.isPlaying }?.start()
                        _state.update { it.copy(isPlaying = true) }
                    }
                    "seek", "sync" -> {
                        mediaPlayer?.let { mp ->
                            val diff = Math.abs(mp.currentPosition - pos)
                            if (diff > 1200) { // re-sync if drift is more than 1.2s
                                mp.seekTo(pos.toInt())
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling incoming music sync", e)
        }
    }
}
