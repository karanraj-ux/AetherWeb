package com.aetherweb.app.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aetherweb.app.LudoEngine
import com.aetherweb.app.LudoState
import com.aetherweb.app.MeshNetworkManager
import kotlinx.coroutines.delay

/**
 * Studio-Grade Mesh Ludo:
 * 1. Chance indicator with active player pulse & ID.
 * 2. 30s turn countdown with pause/resume and auto-move fallback.
 * 3. Live token score tracking (Home X/4).
 * 4. Pass & Play and solo 🤖 Bot claiming.
 */
@Composable
fun LudoScreen(
    state: LudoState,
    onStateChange: (LudoState) -> Unit,
    modifier: Modifier = Modifier
) {
    val playerColors = listOf(
        Color(0xFFEF4444), // Red
        Color(0xFF10B981), // Green
        Color(0xFFF59E0B), // Yellow
        Color(0xFF3B82F6)  // Blue
    )
    val playerNames = listOf("Red", "Green", "Yellow", "Blue")
    val myNodeId = MeshNetworkManager.localNodeId
    val isPassAndPlay = state.playerIds.values.none { it == myNodeId }
    val isMyTurn = (state.playerIds[state.turn] == myNodeId) || isPassAndPlay

    // Timer logic
    var timeLeftSeconds by remember(state.turn, state.hasRolled, state.winner) { mutableIntStateOf(30) }
    var isTimerPaused by remember { mutableStateOf(false) }

    LaunchedEffect(state.turn, state.hasRolled, isTimerPaused, state.winner) {
        if (state.winner != -1 || isTimerPaused) return@LaunchedEffect
        timeLeftSeconds = 30
        while (timeLeftSeconds > 0) {
            delay(1000L)
            timeLeftSeconds--
        }
        // Time expired: auto action
        if (timeLeftSeconds == 0 && state.winner == -1) {
            if (!state.hasRolled) {
                val next = LudoEngine.rollDice(state)
                if (next.playerIds[next.turn] == "bot") {
                    onStateChange(LudoEngine.makeBotMove(next))
                } else {
                    onStateChange(next)
                }
            } else {
                // Pass turn to next player
                val next = state.copy(hasRolled = false, turn = (state.turn + 1) % 4)
                if (next.playerIds[next.turn] == "bot") {
                    onStateChange(LudoEngine.makeBotMove(next))
                } else {
                    onStateChange(next)
                }
            }
        }
    }

    val timerProgress = (timeLeftSeconds / 30f).coerceIn(0f, 1f)
    val timerColor = when {
        timeLeftSeconds <= 7 -> Color(0xFFEF4444)
        timeLeftSeconds <= 15 -> Color(0xFFF59E0B)
        else -> Color(0xFF10B981)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B141B)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // TOP CHANCE & TIME LIMIT HEADER
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2C34)),
            border = androidx.compose.foundation.BorderStroke(1.dp, playerColors[state.turn].copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Active player chance avatar
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(playerColors[state.turn].copy(alpha = pulseAlpha), CircleShape)
                            .border(2.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            playerNames[state.turn].take(1),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (state.winner != -1) "🏆 ${playerNames[state.winner]} WINS!" else "${playerNames[state.turn]}'s Chance",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val pid = state.playerIds[state.turn] ?: ""
                            val label = if (pid == "bot") "🤖 Bot" else if (pid == myNodeId) "You" else if (pid.isNotEmpty()) pid.take(4) else "Open"
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = playerColors[state.turn].copy(alpha = 0.2f)
                            ) {
                                Text(
                                    label,
                                    color = playerColors[state.turn],
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            if (isMyTurn) "👉 Your turn to make a move" else "Waiting for peer move...",
                            fontSize = 11.sp,
                            color = if (isMyTurn) Color(0xFF34D399) else Color(0xFF94A3B8)
                        )
                    }
                }

                // Turn Countdown Timer & Pause
                if (state.winner == -1) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(36.dp)) {
                            CircularProgressIndicator(
                                progress = { timerProgress },
                                color = timerColor,
                                trackColor = Color(0xFF33444D),
                                strokeWidth = 3.dp,
                                modifier = Modifier.fillMaxSize()
                            )
                            Text(
                                "$timeLeftSeconds",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = timerColor,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        IconButton(
                            onClick = { isTimerPaused = !isTimerPaused },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                if (isTimerPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = "Pause Timer",
                                tint = if (isTimerPaused) Color(0xFF10B981) else Color.LightGray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = { onStateChange(LudoState()) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("New Match", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // PLAYER SLOTS & LIVE SCORECARD (HOME TOKENS)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            for (i in 0..3) {
                val score = state.pieces[i]?.count { it >= 57 } ?: 0
                val pid = state.playerIds[i] ?: ""
                val isCurrent = state.turn == i

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isCurrent) playerColors[i].copy(alpha = 0.22f) else Color(0xFF131C21),
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (isCurrent) 1.5.dp else 1.dp,
                        color = if (isCurrent) playerColors[i] else Color(0xFF202C33)
                    ),
                    modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).background(playerColors[i], CircleShape))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(playerNames[i], fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Text("Home: $score/4", fontSize = 9.sp, color = Color(0xFF8696A0))

                        if (pid.isEmpty()) {
                            Row(modifier = Modifier.padding(top = 2.dp)) {
                                Text(
                                    "Claim",
                                    fontSize = 9.sp,
                                    color = playerColors[i],
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable {
                                            val newIds = state.playerIds.toMutableMap()
                                            newIds[i] = myNodeId
                                            onStateChange(state.copy(playerIds = newIds))
                                        }
                                        .padding(horizontal = 2.dp)
                                )
                                Text("•", fontSize = 9.sp, color = Color.Gray)
                                Text(
                                    "Bot",
                                    fontSize = 9.sp,
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable {
                                            val newIds = state.playerIds.toMutableMap()
                                            newIds[i] = "bot"
                                            val next = state.copy(playerIds = newIds)
                                            if (next.turn == i) {
                                                onStateChange(LudoEngine.makeBotMove(next))
                                            } else {
                                                onStateChange(next)
                                            }
                                        }
                                        .padding(horizontal = 2.dp)
                                )
                            }
                        } else {
                            Text(
                                if (pid == myNodeId) "You" else if (pid == "bot") "🤖 Bot" else pid.take(4),
                                fontSize = 9.sp,
                                color = if (pid == myNodeId) Color(0xFF34D399) else Color.LightGray
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // LUDO BOARD SURFACE
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .aspectRatio(1f)
                .padding(6.dp)
                .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                .border(2.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
        ) {
            val cellSize = maxWidth / 15f

            Canvas(modifier = Modifier.fillMaxSize()) {
                val cellPx = cellSize.toPx()
                for (row in 0..14) {
                    for (col in 0..14) {
                        val bgColor = getCellColor(col, row)
                        drawRect(
                            color = bgColor,
                            topLeft = Offset(col * cellPx, row * cellPx),
                            size = Size(cellPx, cellPx)
                        )
                        drawRect(
                            color = Color(0x33000000),
                            topLeft = Offset(col * cellPx, row * cellPx),
                            size = Size(cellPx, cellPx),
                            style = Stroke(width = 1f)
                        )
                    }
                }

                // Draw home base center triangles
                val centerPx = 7.5f * cellPx
                drawCircle(color = Color.White.copy(alpha = 0.2f), radius = cellPx * 0.8f, center = Offset(centerPx, centerPx))
            }

            // PIECE OVERLAYS WITH MOVABLE GLOW
            val piecesHere = mutableMapOf<Pair<Int, Int>, MutableList<Pair<Int, Int>>>()
            for (p in 0..3) {
                for (i in 0..3) {
                    val coord = LudoEngine.getCoordinate(p, i, state.pieces[p]!![i])
                    piecesHere.getOrPut(coord) { mutableListOf() }.add(p to i)
                }
            }

            piecesHere.forEach { (coord, pieces) ->
                val (col, row) = coord
                pieces.forEachIndexed { index, pair ->
                    val (p, idx) = pair
                    val piecePos = state.pieces[p]!![idx]
                    val canThisPieceMove = state.turn == p && state.hasRolled && isMyTurn &&
                            (if (piecePos == 0) state.diceValue == 6 else (piecePos + state.diceValue <= 57))

                    Box(
                        modifier = Modifier
                            .offset(
                                x = (cellSize * col) + (cellSize * 0.12f) + (index * 4).dp,
                                y = (cellSize * row) + (cellSize * 0.12f) + (index * 4).dp
                            )
                            .size(cellSize * 0.76f)
                            .shadow(if (canThisPieceMove) 8.dp else 2.dp, CircleShape)
                            .background(playerColors[p], CircleShape)
                            .border(
                                width = if (canThisPieceMove) 2.5.dp else 1.5.dp,
                                color = if (canThisPieceMove) Color.White else Color(0xCC000000),
                                shape = CircleShape
                            )
                            .clickable {
                                if (canThisPieceMove) {
                                    val next = LudoEngine.movePiece(state, p, idx)
                                    if (next.playerIds[next.turn] == "bot") {
                                        onStateChange(LudoEngine.makeBotMove(next))
                                    } else {
                                        onStateChange(next)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(cellSize * 0.32f)
                                .background(Color.White.copy(alpha = 0.85f), CircleShape)
                        )
                    }
                }
            }
        }

        // BOTTOM TACTICAL DICE DECK
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2C34)),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF33444D))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        if (!state.hasRolled) "ROLL THE DICE" else "SELECT HIGHLIGHTED PIECE",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (!state.hasRolled) Color(0xFF25D366) else Color(0xFFF59E0B)
                    )
                    Text(
                        if (!state.hasRolled) {
                            if (isMyTurn) "Tap dice to generate your move" else "Waiting for opponent to roll..."
                        } else {
                            "Move token forward by ${state.diceValue} steps"
                        },
                        fontSize = 11.sp,
                        color = Color(0xFF8696A0)
                    )
                }

                // 3D TACTICAL DICE BUTTON
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (!state.hasRolled && isMyTurn) playerColors[state.turn] else Color(0xFF2A3942)
                        )
                        .border(
                            2.dp,
                            if (!state.hasRolled && isMyTurn) Color.White else Color(0xFF374955),
                            RoundedCornerShape(14.dp)
                        )
                        .clickable(enabled = !state.hasRolled && isMyTurn) {
                            val next = LudoEngine.rollDice(state)
                            if (next.playerIds[next.turn] == "bot") {
                                onStateChange(LudoEngine.makeBotMove(next))
                            } else {
                                onStateChange(next)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (state.hasRolled || state.diceValue > 0) {
                        Text(
                            state.diceValue.toString(),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    } else {
                        Icon(
                            Icons.Default.Casino,
                            contentDescription = "Roll Dice",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }
        }
    }
}

fun getCellColor(col: Int, row: Int): Color {
    val red = Color(0xFFB91C1C)
    val green = Color(0xFF047857)
    val yellow = Color(0xFFB45309)
    val blue = Color(0xFF1D4ED8)
    val empty = Color(0xFF1E293B)
    val safeStar = Color(0xFF334155)

    // Base quadrants
    if (col in 0..5 && row in 0..5) return red
    if (col in 9..14 && row in 0..5) return green
    if (col in 9..14 && row in 9..14) return yellow
    if (col in 0..5 && row in 9..14) return blue
    if (col in 6..8 && row in 6..8) return Color(0xFF0F172A)

    // Safe home paths
    if (col in 1..5 && row == 7) return red.copy(alpha = 0.85f)
    if (col == 7 && row in 1..5) return green.copy(alpha = 0.85f)
    if (col in 9..13 && row == 7) return yellow.copy(alpha = 0.85f)
    if (col == 7 && row in 9..13) return blue.copy(alpha = 0.85f)

    // Starting arrow squares
    if (col == 1 && row == 6) return red
    if (col == 8 && row == 1) return green
    if (col == 13 && row == 8) return yellow
    if (col == 6 && row == 13) return blue

    // Safe star zones
    if ((col == 6 && row == 2) || (col == 12 && row == 6) || (col == 8 && row == 12) || (col == 2 && row == 8)) {
        return safeStar
    }

    return empty
}
