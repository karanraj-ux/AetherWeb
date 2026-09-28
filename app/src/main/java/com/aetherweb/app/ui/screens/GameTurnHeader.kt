package com.aetherweb.app.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Universal Game Turn Header & Chance Indicator:
 * 1. Shows who has the active chance to play with a pulsing avatar ring.
 * 2. 30-Second Turn Countdown Timer (Turns red when <7s).
 * 3. Pause / Resume functionality to keep gameplay organized.
 * 4. Works across Chess, Connect 4, Tic-Tac-Toe, and Ludo.
 */
@Composable
fun GameTurnHeader(
    gameName: String,
    player1Name: String,
    player1Color: Color,
    player2Name: String,
    player2Color: Color,
    isPlayer1Turn: Boolean,
    isMyTurn: Boolean,
    winner: String = "",
    onTimeExpired: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var timeLeftSeconds by remember(isPlayer1Turn, winner) { mutableIntStateOf(30) }
    var isTimerPaused by remember { mutableStateOf(false) }

    // Pulsing animation for active turn
    val infiniteTransition = rememberInfiniteTransition(label = "turn_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    // Countdown effect
    LaunchedEffect(isPlayer1Turn, isTimerPaused, winner) {
        if (winner.isNotEmpty() || isTimerPaused) return@LaunchedEffect
        timeLeftSeconds = 30
        while (timeLeftSeconds > 0) {
            delay(1000L)
            timeLeftSeconds--
        }
        if (timeLeftSeconds == 0) {
            onTimeExpired?.invoke()
        }
    }

    val timerProgress = (timeLeftSeconds / 30f).coerceIn(0f, 1f)
    val timerColor = when {
        timeLeftSeconds <= 7 -> Color(0xFFEF4444) // Urgent red
        timeLeftSeconds <= 15 -> Color(0xFFF59E0B) // Amber warning
        else -> Color(0xFF25D366) // Green optimal
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1F2C34)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // TOP STATUS BADGE
            if (winner.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF005D4B))
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "🏆 $winner",
                        color = Color(0xFF25D366),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = gameName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )

                    // PAUSE / RESUME BUTTON
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF111B21))
                            .clickable { isTimerPaused = !isTimerPaused }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isTimerPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = "Pause Timer",
                            tint = if (isTimerPaused) Color(0xFF25D366) else Color.LightGray,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isTimerPaused) "Resume" else "Pause",
                            fontSize = 10.sp,
                            color = if (isTimerPaused) Color(0xFF25D366) else Color.LightGray,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // CHANCE INDICATOR
                    val activeName = if (isPlayer1Turn) player1Name else player2Name
                    val chanceText = if (isMyTurn) "👉 YOUR CHANCE" else "⏳ $activeName's Turn"
                    Text(
                        text = chanceText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (isMyTurn) Color(0xFF25D366) else Color(0xFF53BDEB)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // PLAYERS & TIMER ROW
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // PLAYER 1
                    PlayerAvatarBadge(
                        name = player1Name,
                        color = player1Color,
                        isActive = isPlayer1Turn,
                        pulseAlpha = if (isPlayer1Turn) pulseAlpha else 0f
                    )

                    // CENTER 30S COUNTDOWN RING
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(48.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = { timerProgress },
                            modifier = Modifier.size(48.dp),
                            color = timerColor,
                            trackColor = Color(0xFF33444D),
                            strokeWidth = 4.dp
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${timeLeftSeconds}s",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = timerColor
                            )
                        }
                    }

                    // PLAYER 2
                    PlayerAvatarBadge(
                        name = player2Name,
                        color = player2Color,
                        isActive = !isPlayer1Turn,
                        pulseAlpha = if (!isPlayer1Turn) pulseAlpha else 0f
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerAvatarBadge(
    name: String,
    color: Color,
    isActive: Boolean,
    pulseAlpha: Float
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .border(
                    width = if (isActive) 3.dp else 1.dp,
                    color = if (isActive) color.copy(alpha = pulseAlpha) else Color.DarkGray,
                    shape = CircleShape
                )
                .background(color.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.take(2).uppercase(),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = color
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = name,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            fontSize = 11.sp,
            color = if (isActive) Color.White else Color.LightGray
        )
    }
}
