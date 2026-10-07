package com.aetherweb.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aetherweb.app.ChessEngine
import com.aetherweb.app.ChessState
import com.aetherweb.app.MeshNetworkManager
import kotlinx.coroutines.launch

@Composable
fun ChessScreen(
    state: ChessState,
    onStateChange: (ChessState) -> Unit,
    modifier: Modifier = Modifier
) {
    val myNodeId = MeshNetworkManager.localNodeId
    
    val isWhite = state.whitePlayerId == myNodeId
    val isBlack = state.blackPlayerId == myNodeId
    val isSpectator = !isWhite && !isBlack
    val isPassAndPlay = state.whitePlayerId.isEmpty() && state.blackPlayerId.isEmpty()
    val isMyTurn = (state.isWhiteTurn && (isWhite || isPassAndPlay)) || (!state.isWhiteTurn && (isBlack || isPassAndPlay))
    val p1 = if (state.whitePlayerId == "bot") "🤖 Bot White" else if (state.whitePlayerId.isNotEmpty()) state.whitePlayerId.take(8) else "White"
    val p2 = if (state.blackPlayerId == "bot") "🤖 Bot Black" else if (state.blackPlayerId.isNotEmpty()) state.blackPlayerId.take(8) else "Black"

    // Bot thinking buffer: human-like pause before bot moves, with stale-state guard.
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val stateRef = androidx.compose.runtime.rememberUpdatedState(state)
    fun triggerBotMove(pending: ChessState, fastMode: Boolean = false) {
        val delayMs = com.aetherweb.app.BotThinking.delayMs(fastMode)
        if (delayMs == 0L) {
            onStateChange(ChessEngine.makeBotMove(pending))
            return
        }
        onStateChange(pending.copy(botThinking = true))
        scope.launch {
            kotlinx.coroutines.delay(delayMs)
            if (stateRef.value.botThinking) {
                onStateChange(ChessEngine.makeBotMove(pending).copy(botThinking = false))
            }
        }
    }

    fun maybeTriggerBotMove(s: ChessState, fastMode: Boolean = false) {
        if (s.winner.isNotEmpty()) { onStateChange(s); return }
        val botToMove = (s.isWhiteTurn && s.whitePlayerId == "bot") ||
                (!s.isWhiteTurn && s.blackPlayerId == "bot")
        if (botToMove) triggerBotMove(s, fastMode) else onStateChange(s)
    }

    var selectedIdx by remember { mutableStateOf<Int?>(null) }

    Column(modifier = modifier.fillMaxSize().background(Color(0xFF222222)), horizontalAlignment = Alignment.CenterHorizontally) {
        
        GameTurnHeader(
            gameName = "♟️ Offline Chess",
            player1Name = p1,
            player1Color = Color(0xFFEEEEEE),
            player2Name = p2,
            player2Color = Color(0xFF769656),
            isPlayer1Turn = state.isWhiteTurn,
            isMyTurn = isMyTurn,
            winner = state.winner
        )

        if (state.botThinking) {
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(com.aetherweb.app.BotThinking.THINKING_LABEL, fontSize = 13.sp, color = Color.Gray)
            }
        }

        // Roles & Controls
        Card(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF333333))
        ) {
            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.winner.isNotEmpty()) {
                    Button(onClick = { onStateChange(ChessEngine.reset()) }, modifier = Modifier.padding(bottom = 6.dp)) {
                        Text("New Match")
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    if (state.whitePlayerId.isEmpty()) {
                        Button(onClick = { onStateChange(ChessEngine.claimRole(state, myNodeId, "white")) }) { Text("Play White") }
                        Button(onClick = { maybeTriggerBotMove(ChessEngine.claimRole(state, "bot", "white")) }) { Text("🤖 Bot White") }
                    } else if (isWhite) {
                        Button(onClick = { onStateChange(ChessEngine.leaveRole(state, myNodeId)) }, colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)) { Text("Leave") }
                    } else {
                        Text(if (state.whitePlayerId == "bot") "🤖 Bot White" else "White: Taken", color = Color.LightGray)
                    }

                    if (state.blackPlayerId.isEmpty()) {
                        Button(onClick = { onStateChange(ChessEngine.claimRole(state, myNodeId, "black")) }) { Text("Play Black") }
                        Button(onClick = { maybeTriggerBotMove(ChessEngine.claimRole(state, "bot", "black")) }) { Text("🤖 Bot Black") }
                    } else if (isBlack) {
                        Button(onClick = { onStateChange(ChessEngine.leaveRole(state, myNodeId)) }, colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)) { Text("Leave") }
                    } else {
                        Text(if (state.blackPlayerId == "bot") "🤖 Bot Black" else "Black: Taken", color = Color.LightGray)
                    }
                }
            }
        }

        // Board
        Box(modifier = Modifier.weight(1f).aspectRatio(1f).padding(8.dp).border(4.dp, Color(0xFF5D4037))) {
            Column(modifier = Modifier.fillMaxSize()) {
                for (row in 0..7) {
                    Row(modifier = Modifier.weight(1f)) {
                        for (col in 0..7) {
                            val isLight = (row + col) % 2 == 0
                            val bgColor = if (isLight) Color(0xFFD7CCC8) else Color(0xFF795548)
                            val idx = row * 8 + col
                            val pieceStr = state.pieces[idx]
                            
                            val isSelected = selectedIdx == idx
                            val cellColor = if (isSelected) Color(0xFFFFF59D) else bgColor

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(cellColor)
                                    .clickable {
                                        val isPassAndPlay = state.whitePlayerId.isEmpty() && state.blackPlayerId.isEmpty()
                                        if ((isSpectator && !isPassAndPlay) || state.winner.isNotEmpty()) return@clickable
                                        
                                        if (selectedIdx == null) {
                                            // Select piece
                                            if (pieceStr.isNotEmpty()) {
                                                val pieceIsWhite = pieceStr.first().isUpperCase()
                                                val canSelect = (isWhite && pieceIsWhite && state.isWhiteTurn) ||
                                                                (isBlack && !pieceIsWhite && !state.isWhiteTurn) ||
                                                                (isPassAndPlay && ((pieceIsWhite && state.isWhiteTurn) || (!pieceIsWhite && !state.isWhiteTurn)))
                                                if (canSelect) {
                                                    selectedIdx = idx
                                                }
                                            }
                                        } else {
                                            // Move piece
                                            if (selectedIdx == idx) {
                                                selectedIdx = null // Deselect
                                            } else {
                                                val newState = ChessEngine.move(state, selectedIdx!!, idx, myNodeId)
                                                maybeTriggerBotMove(newState)
                                                selectedIdx = null
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (pieceStr.isNotEmpty()) {
                                    val pieceChar = getPieceChar(pieceStr)
                                    val isPieceWhite = pieceStr.first().isUpperCase()
                                    Text(
                                        text = pieceChar,
                                        fontSize = 32.sp,
                                        color = if (isPieceWhite) Color.White else Color.Black,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun getPieceChar(piece: String): String {
    return when (piece) {
        "P" -> "♙"
        "R" -> "♖"
        "N" -> "♘"
        "B" -> "♗"
        "Q" -> "♕"
        "K" -> "♔"
        "p" -> "♟"
        "r" -> "♜"
        "n" -> "♞"
        "b" -> "♝"
        "q" -> "♛"
        "k" -> "♚"
        else -> ""
    }
}
