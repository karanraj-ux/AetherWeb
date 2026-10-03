package com.aetherweb.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aetherweb.app.MeshNetworkManager
import org.json.JSONObject
import org.json.JSONArray

// TIC-TAC-TOE
@Composable
fun TicTacToeScreen(
    state: com.aetherweb.app.TicTacToeState,
    onStateChange: (com.aetherweb.app.TicTacToeState) -> Unit,
    modifier: Modifier = Modifier
) {
    val myNodeId = MeshNetworkManager.localNodeId
    val isX = state.xPlayerId == myNodeId
    val isO = state.oPlayerId == myNodeId

    val isPassAndPlay = state.xPlayerId.isEmpty() && state.oPlayerId.isEmpty()
    val isMyTurn = (state.isXTurn && (isX || isPassAndPlay)) || (!state.isXTurn && (isO || isPassAndPlay))
    val p1 = if (state.xPlayerId == "bot") "🤖 Bot X" else if (state.xPlayerId.isNotEmpty()) state.xPlayerId.take(8) else "Player X"
    val p2 = if (state.oPlayerId == "bot") "🤖 Bot O" else if (state.oPlayerId.isNotEmpty()) state.oPlayerId.take(8) else "Player O"

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        GameTurnHeader(
            gameName = "⭕ Tic-Tac-Toe",
            player1Name = p1,
            player1Color = Color(0xFF25D366),
            player2Name = p2,
            player2Color = Color(0xFF53BDEB),
            isPlayer1Turn = state.isXTurn,
            isMyTurn = isMyTurn,
            winner = state.winner
        )

        Card(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.winner.isNotEmpty()) {
                    Button(onClick = { onStateChange(com.aetherweb.app.TicTacToeState()) }, modifier = Modifier.padding(bottom = 6.dp)) { Text("Play Again") }
                }
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    if (state.xPlayerId.isEmpty() && state.oPlayerId != myNodeId) {
                        Button(onClick = { onStateChange(state.copy(xPlayerId = myNodeId)) }) { Text("Play X") }
                        if (state.oPlayerId == myNodeId) Button(onClick = { onStateChange(state.copy(xPlayerId = "bot").maybeBotMove()) }) { Text("Bot X") }
                    } else if (state.xPlayerId.isEmpty() && state.oPlayerId == myNodeId) {
                        Button(onClick = { onStateChange(state.copy(xPlayerId = "bot").maybeBotMove()) }) { Text("Add Bot X") }
                    } else {
                        Text(if(isX) "You are X" else "X: Taken", color = if(isX) MaterialTheme.colorScheme.primary else Color.LightGray)
                    }
                    if (state.oPlayerId.isEmpty() && state.xPlayerId != myNodeId) {
                        Button(onClick = { onStateChange(state.copy(oPlayerId = myNodeId)) }) { Text("Play O") }
                    } else if (state.oPlayerId.isEmpty() && state.xPlayerId == myNodeId) {
                        Button(onClick = { onStateChange(state.copy(oPlayerId = "bot")) }) { Text("Add Bot O") }
                    } else {
                        Text(if(isO) "You are O" else "O: Taken", color = if(isO) MaterialTheme.colorScheme.secondary else Color.LightGray)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        Box(modifier = Modifier.size(300.dp).background(MaterialTheme.colorScheme.onBackground).padding(4.dp)) {
            Column(modifier = Modifier.fillMaxSize()) {
                for (row in 0..2) {
                    Row(modifier = Modifier.weight(1f)) {
                        for (col in 0..2) {
                            val idx = row * 3 + col
                            val cell = state.board[idx]
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(2.dp)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .clickable {
                                        if (state.winner.isNotEmpty() || cell.isNotEmpty()) return@clickable
                                        val isPassAndPlay = state.xPlayerId.isEmpty() && state.oPlayerId.isEmpty()
                                        if (state.isXTurn && (isX || isPassAndPlay)) {
                                            val newBoard = state.board.toMutableList()
                                            newBoard[idx] = "X"
                                            val nextState = state.copy(board = newBoard, isXTurn = false).checkWinner()
                                            if (!nextState.isXTurn && nextState.oPlayerId == "bot" && nextState.winner.isEmpty()) {
                                                onStateChange(nextState.makeBotMove())
                                            } else {
                                                onStateChange(nextState)
                                            }
                                        } else if (!state.isXTurn && (isO || isPassAndPlay)) {
                                            val newBoard = state.board.toMutableList()
                                            newBoard[idx] = "O"
                                            val nextState = state.copy(board = newBoard, isXTurn = true).checkWinner()
                                            if (nextState.isXTurn && nextState.xPlayerId == "bot" && nextState.winner.isEmpty()) {
                                                onStateChange(nextState.makeBotMove())
                                            } else {
                                                onStateChange(nextState)
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = cell, 
                                    fontSize = 48.sp, 
                                    color = if (cell == "X") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// CONNECT 4
@Composable
fun Connect4Screen(
    state: com.aetherweb.app.Connect4State,
    onStateChange: (com.aetherweb.app.Connect4State) -> Unit,
    modifier: Modifier = Modifier
) {
    val myNodeId = MeshNetworkManager.localNodeId
    val isRed = state.redPlayerId == myNodeId
    val isYellow = state.yellowPlayerId == myNodeId

    val isPassAndPlay = state.redPlayerId.isEmpty() && state.yellowPlayerId.isEmpty()
    val isMyTurn = (state.isRedTurn && (isRed || isPassAndPlay)) || (!state.isRedTurn && (isYellow || isPassAndPlay))
    val p1 = if (state.redPlayerId == "bot") "🤖 Bot Red" else if (state.redPlayerId.isNotEmpty()) state.redPlayerId.take(8) else "Red"
    val p2 = if (state.yellowPlayerId == "bot") "🤖 Bot Yellow" else if (state.yellowPlayerId.isNotEmpty()) state.yellowPlayerId.take(8) else "Yellow"

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        GameTurnHeader(
            gameName = "🔴 Connect 4",
            player1Name = p1,
            player1Color = Color(0xFFEF4444),
            player2Name = p2,
            player2Color = Color(0xFFFFB300),
            isPlayer1Turn = state.isRedTurn,
            isMyTurn = isMyTurn,
            winner = state.winner
        )

        Card(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.winner.isNotEmpty()) {
                    Button(onClick = { onStateChange(com.aetherweb.app.Connect4State()) }, modifier = Modifier.padding(bottom = 6.dp)) { Text("Play Again") }
                }
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    if (state.redPlayerId.isEmpty()) {
                        Button(onClick = { onStateChange(state.copy(redPlayerId = myNodeId)) }) { Text("Play Red") }
                        Button(onClick = { onStateChange(state.copy(redPlayerId = "bot").maybeBotMove()) }) { Text("🤖 Bot Red") }
                    } else if (isRed) {
                        Button(onClick = { onStateChange(state.copy(redPlayerId = "")) }, colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)) { Text("Leave") }
                    } else {
                        Text(if (state.redPlayerId == "bot") "🤖 Bot Red" else "Red: Taken", color = if(isRed) MaterialTheme.colorScheme.secondary else Color.LightGray)
                    }
                    if (state.yellowPlayerId.isEmpty()) {
                        Button(onClick = { onStateChange(state.copy(yellowPlayerId = myNodeId)) }) { Text("Play Yellow") }
                        Button(onClick = { onStateChange(state.copy(yellowPlayerId = "bot")) }) { Text("🤖 Bot Yellow") }
                    } else if (isYellow) {
                        Button(onClick = { onStateChange(state.copy(yellowPlayerId = "")) }, colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)) { Text("Leave") }
                    } else {
                        Text(if (state.yellowPlayerId == "bot") "🤖 Bot Yellow" else "Yellow: Taken", color = if(isYellow) Color(0xFFFFB300) else Color.LightGray)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Box(modifier = Modifier.aspectRatio(7f/6f).fillMaxWidth(0.9f).background(Color(0xFF1565C0)).padding(8.dp)) {
            Row(modifier = Modifier.fillMaxSize()) {
                val isPassAndPlay = state.redPlayerId.isEmpty() && state.yellowPlayerId.isEmpty()
                for (col in 0..6) {
                    Column(modifier = Modifier.weight(1f).clickable {
                        if (state.winner.isNotEmpty()) return@clickable
                        val canMove = (state.isRedTurn && (isRed || isPassAndPlay)) ||
                                      (!state.isRedTurn && (isYellow || isPassAndPlay))
                        if (canMove) {
                            // Find lowest empty slot in this column
                            for (row in 5 downTo 0) {
                                if (state.board[row][col] == 0) {
                                    val newBoard = state.board.map { it.toMutableList() }.toMutableList()
                                    newBoard[row][col] = if (state.isRedTurn) 1 else 2
                                    val nextState = state.copy(board = newBoard, isRedTurn = !state.isRedTurn).checkWinner()
                                    if (nextState.winner.isEmpty() && ((nextState.isRedTurn && nextState.redPlayerId == "bot") || (!nextState.isRedTurn && nextState.yellowPlayerId == "bot"))) {
                                        onStateChange(nextState.makeBotMove())
                                    } else {
                                        onStateChange(nextState)
                                    }
                                    break
                                }
                            }
                        }
                    }) {
                        for (row in 0..5) {
                            val cell = state.board[row][col]
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(4.dp)
                                    .background(
                                        color = when(cell) {
                                            1 -> Color.Red
                                            2 -> Color(0xFFFFB300)
                                            else -> MaterialTheme.colorScheme.background
                                        },
                                        shape = CircleShape
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}
