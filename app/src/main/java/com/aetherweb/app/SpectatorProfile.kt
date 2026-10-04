package com.aetherweb.app

/**
 * Voice Room Phase 1 — Temp/permanent profiles for web-guest approval.
 *
 * The host used to see only a raw IP ("A web spectator (a8f3...) wants to join").
 * Now every web-guest request carries a profile so the host knows WHO is asking.
 *
 * kind = "temp"   -> web guest: name+emoji picked in the browser, stored in localStorage.
 * kind = "member" -> app user with the persistent identity (shown with a member badge).
 * request = "chat" | "voice": what the guest is asking to join.
 */
data class SpectatorRequest(
    val ip: String,
    val name: String,
    val emoji: String,
    val pid: String,
    val kind: String = "temp",
    val request: String = "chat"
) {
    /** One-line label for approval dialogs, e.g. "Rahul 🎧 (web guest) wants to join voice". */
    fun describe(): String {
        val who = if (kind == "member") "$name $emoji (member)" else "$name $emoji (web guest)"
        return "$who wants to join $request"
    }
}
