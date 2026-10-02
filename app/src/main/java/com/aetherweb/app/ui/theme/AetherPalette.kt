package com.aetherweb.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Aether link palette — additions for the Rooms redesign.
// Do NOT edit Color.kt / Theme.kt; these live alongside as the new accents.
// ---------------------------------------------------------------------------

// BLE / Campfire mode — low-power mesh presence, always-on ember.
val EmberOrange = Color(0xFFFF7A1A)

// Wi-Fi / Room mode — full-room fire, used as a gradient.
val FlameAmber = Color(0xFFFFB020)
val FlameRed = Color(0xFFFF3D00)

// Functional accents.
val SosRed = Color(0xFFFF3B30)
val NostrPurple = Color(0xFF9D4DFF)

// Dark-first surfaces for the redesign.
val AetherBackground = Color(0xFF0B0B10)
val AetherSurface = Color(0xFF14141C)
val AetherCard = Color(0xFF1B1B26)

// Full-room fire gradient (Wi-Fi / Room live state).
@Composable
fun flameGradient(): Brush = Brush.linearGradient(listOf(FlameAmber, FlameRed))
