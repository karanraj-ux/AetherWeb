package com.aetherweb.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Voice Room Phase 2 — Discord-style voice room state.
 *
 * Single source of truth for the voice dashboard UI (app bottom sheet + web panel).
 * Roles:
 *   host   -> this phone when it runs the hotspot/server (god powers: mute/kick/mute-all)
 *   member -> app user with the permanent identity
 *   temp   -> web guest with the temp profile (name + emoji from the browser)
 *
 * Networking (SFU audio pipe) lands in Phase 3; server-side enforcement in Phase 4.
 * Until then these are local UI-state operations so the dashboard is fully usable.
 */
data class VoiceMember(
    val id: String,
    val name: String,
    val emoji: String = "",
    val kind: String = "member", // "host" | "member" | "temp"
    val micOn: Boolean = false,
    val mutedByHost: Boolean = false,
    val speaking: Boolean = false,
    val mutedByMe: Boolean = false,
    val isSelf: Boolean = false
)

data class VoiceRoomState(
    val active: Boolean = false,
    val isHost: Boolean = false,
    val deafen: Boolean = false,
    val members: Map<String, VoiceMember> = emptyMap()
) {
    val memberCount: Int get() = members.size
}

object VoiceRoomManager {
    private val _state = MutableStateFlow(VoiceRoomState())
    val state: StateFlow<VoiceRoomState> = _state.asStateFlow()

    /**
     * Phase 3/4: the live audio pipe (VoiceRoomClient). Set when the client connects;
     * null when the dashboard is UI-only. All control calls below delegate to it.
     */
    var audioClient: VoiceRoomClient? = null

    /** Host (hotspot phone) starts the room. */
    fun openAsHost(hostId: String, hostName: String) {
        _state.update {
            VoiceRoomState(
                active = true,
                isHost = true,
                members = mapOf(
                    hostId to VoiceMember(
                        id = hostId, name = hostName, kind = "host", isSelf = true
                    )
                )
            )
        }
    }

    fun closeRoom() {
        _state.value = VoiceRoomState()
    }

    fun upsertMember(member: VoiceMember) {
        _state.update { it.copy(members = it.members + (member.id to member)) }
    }

    fun removeMember(id: String) {
        _state.update { it.copy(members = it.members - id) }
    }

    fun updateMember(id: String, transform: (VoiceMember) -> VoiceMember) {
        _state.update { s ->
            val m = s.members[id] ?: return@update s
            s.copy(members = s.members + (id to transform(m)))
        }
    }

    // --- Self controls (delegate to the audio pipe when connected) ---
    fun setSelfMic(on: Boolean) {
        updateSelf { it.copy(micOn = on && !it.mutedByHost) }
        try { audioClient?.setMicOn(on) } catch (e: Exception) { /* UI-only mode */ }
    }

    fun setMutedByMe(id: String, muted: Boolean) {
        updateMember(id) { it.copy(mutedByMe = muted) }
        try { audioClient?.setMutedByMe(id, muted) } catch (e: Exception) { /* UI-only mode */ }
    }

    fun setDeafen(deaf: Boolean) {
        _state.update { it.copy(deafen = deaf) }
        try { audioClient?.setDeafen(deaf) } catch (e: Exception) { /* UI-only mode */ }
    }

    fun setSpeaking(id: String, speaking: Boolean) {
        updateMember(id) { it.copy(speaking = speaking) }
    }

    // --- Host controls (server-side enforcement lands in Phase 4) ---
    fun hostSetMute(id: String, muted: Boolean) {
        updateMember(id) { it.copy(mutedByHost = muted, micOn = if (muted) false else it.micOn) }
    }

    fun hostMuteAll() {
        _state.update { s ->
            s.copy(members = s.members.mapValues { (_, m) ->
                if (m.isSelf || m.kind == "host") m
                else m.copy(mutedByHost = true, micOn = false)
            })
        }
    }

    private fun updateSelf(transform: (VoiceMember) -> VoiceMember) {
        _state.update { s ->
            val self = s.members.values.firstOrNull { it.isSelf } ?: return@update s
            s.copy(members = s.members + (self.id to transform(self)))
        }
    }
}
