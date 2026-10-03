package com.aetherweb.app

import org.json.JSONArray
import org.json.JSONObject

data class ChessState(
    val pieces: List<String> = initialBoard(),
    val isWhiteTurn: Boolean = true,
    val whitePlayerId: String = "",
    val blackPlayerId: String = "",
    val winner: String = "" // "", "White", "Black", "Draw"
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        val arr = JSONArray()
        pieces.forEach { arr.put(it) }
        obj.put("pieces", arr)
        obj.put("isWhiteTurn", isWhiteTurn)
        obj.put("whitePlayerId", whitePlayerId)
        obj.put("blackPlayerId", blackPlayerId)
        obj.put("winner", winner)
        return obj
    }

    companion object {
        fun initialBoard(): List<String> {
            val b = MutableList(64) { "" }
            val backWhite = listOf("R", "N", "B", "Q", "K", "B", "N", "R")
            val backBlack = listOf("r", "n", "b", "q", "k", "b", "n", "r")
            for (i in 0..7) {
                b[i] = backBlack[i]
                b[8 + i] = "p"
                b[48 + i] = "P"
                b[56 + i] = backWhite[i]
            }
            return b
        }

        fun fromJson(json: String): ChessState {
            val obj = JSONObject(json)
            val arr = obj.getJSONArray("pieces")
            val p = MutableList(64) { "" }
            for (i in 0 until arr.length()) p[i] = arr.getString(i)
            return ChessState(
                pieces = p,
                isWhiteTurn = obj.getBoolean("isWhiteTurn"),
                whitePlayerId = obj.optString("whitePlayerId", ""),
                blackPlayerId = obj.optString("blackPlayerId", ""),
                winner = obj.optString("winner", "")
            )
        }
    }
}

object ChessEngine {
    fun claimRole(state: ChessState, nodeId: String, role: String): ChessState {
        // Only allow claiming if it's empty or already claimed by this user
        return when (role) {
            "white" -> if (state.whitePlayerId.isEmpty() || state.whitePlayerId == nodeId) state.copy(whitePlayerId = nodeId) else state
            "black" -> if (state.blackPlayerId.isEmpty() || state.blackPlayerId == nodeId) state.copy(blackPlayerId = nodeId) else state
            else -> state
        }
    }

    fun leaveRole(state: ChessState, nodeId: String): ChessState {
        var s = state
        if (s.whitePlayerId == nodeId) s = s.copy(whitePlayerId = "")
        if (s.blackPlayerId == nodeId) s = s.copy(blackPlayerId = "")
        return s
    }

    fun isValidMove(state: ChessState, fromIdx: Int, toIdx: Int, piece: String, isWhite: Boolean, targetPiece: String): Boolean {
        val fRow = fromIdx / 8
        val fCol = fromIdx % 8
        val tRow = toIdx / 8
        val tCol = toIdx % 8
        val dRow = tRow - fRow
        val dCol = tCol - fCol
        val p = piece.lowercase()

        // Path collision check helper
        fun isPathClear(dr: Int, dc: Int): Boolean {
            val stepR = if (dr == 0) 0 else dr / Math.abs(dr)
            val stepC = if (dc == 0) 0 else dc / Math.abs(dc)
            var r = fRow + stepR
            var c = fCol + stepC
            while (r != tRow || c != tCol) {
                if (state.pieces[r * 8 + c].isNotEmpty()) return false
                r += stepR
                c += stepC
            }
            return true
        }

        when (p) {
            "p" -> {
                val dir = if (isWhite) -1 else 1
                val startRow = if (isWhite) 6 else 1
                // Forward move
                if (dCol == 0 && targetPiece.isEmpty()) {
                    if (dRow == dir) return true
                    if (fRow == startRow && dRow == 2 * dir && state.pieces[(fRow + dir) * 8 + fCol].isEmpty()) return true
                }
                // Capture
                if (Math.abs(dCol) == 1 && dRow == dir && targetPiece.isNotEmpty()) return true
                return false
            }
            "r" -> {
                if (dRow != 0 && dCol != 0) return false
                return isPathClear(dRow, dCol)
            }
            "b" -> {
                if (Math.abs(dRow) != Math.abs(dCol)) return false
                return isPathClear(dRow, dCol)
            }
            "q" -> {
                if (dRow != 0 && dCol != 0 && Math.abs(dRow) != Math.abs(dCol)) return false
                return isPathClear(dRow, dCol)
            }
            "k" -> {
                return Math.abs(dRow) <= 1 && Math.abs(dCol) <= 1
            }
            "n" -> {
                return (Math.abs(dRow) == 2 && Math.abs(dCol) == 1) || (Math.abs(dRow) == 1 && Math.abs(dCol) == 2)
            }
        }
        return false
    }

    fun move(state: ChessState, fromIdx: Int, toIdx: Int, nodeId: String): ChessState {
        if (state.winner.isNotEmpty()) return state
        
        val piece = state.pieces[fromIdx]
        if (piece.isEmpty()) return state
        
        val isWhitePiece = piece.first().isUpperCase()
        
        val isPassAndPlay = state.whitePlayerId.isEmpty() && state.blackPlayerId.isEmpty()
        if (isWhitePiece && (!state.isWhiteTurn || (!isPassAndPlay && state.whitePlayerId != nodeId))) return state
        if (!isWhitePiece && (state.isWhiteTurn || (!isPassAndPlay && state.blackPlayerId != nodeId))) return state
        
        val targetPiece = state.pieces[toIdx]
        if (targetPiece.isNotEmpty()) {
            val targetIsWhite = targetPiece.first().isUpperCase()
            if (isWhitePiece == targetIsWhite) return state
        }
        
        if (!isValidMove(state, fromIdx, toIdx, piece, isWhitePiece, targetPiece)) return state

        return maybeBotMove(applyMove(state, fromIdx, toIdx, piece, targetPiece))
    }

    /**
     * If the side to move is a bot, apply its move immediately.
     * Safe to call after any state change (seat claim, reset, move) — the bot
     * used to only respond to human moves, so a bot playing first (White)
     * would stall the game forever.
     */
    fun maybeBotMove(state: ChessState): ChessState {
        if (state.winner.isNotEmpty()) return state
        val botToMove = (state.isWhiteTurn && state.whitePlayerId == "bot") ||
                (!state.isWhiteTurn && state.blackPlayerId == "bot")
        return if (botToMove) makeBotMove(state) else state
    }

    fun applyMove(state: ChessState, fromIdx: Int, toIdx: Int, piece: String, targetPiece: String): ChessState {
        val newPieces = state.pieces.toMutableList()
        newPieces[toIdx] = piece
        newPieces[fromIdx] = ""
        
        var newWinner = ""
        if (targetPiece == "K") newWinner = "Black"
        if (targetPiece == "k") newWinner = "White"
        
        return state.copy(
            pieces = newPieces,
            isWhiteTurn = !state.isWhiteTurn,
            winner = newWinner
        )
    }
    
    
    fun makeBotMove(state: ChessState): ChessState {
        if (state.winner.isNotEmpty()) return state
        val isWhite = state.isWhiteTurn
        
        data class Move(val from: Int, val to: Int, val piece: String, val targetPiece: String, val score: Int)
        val validMoves = mutableListOf<Move>()
        
        for (fromIdx in 0..63) {
            val piece = state.pieces[fromIdx]
            if (piece.isEmpty()) continue
            val pieceIsWhite = piece[0].isUpperCase()
            if (pieceIsWhite != isWhite) continue
            
            for (toIdx in 0..63) {
                if (fromIdx == toIdx) continue
                val targetPiece = state.pieces[toIdx]
                if (targetPiece.isNotEmpty() && targetPiece[0].isUpperCase() == isWhite) continue
                
                if (isValidMove(state, fromIdx, toIdx, piece, isWhite, targetPiece)) {
                    var score = 0
                    if (targetPiece.isNotEmpty()) {
                        score = when (targetPiece.lowercase()) {
                            "q" -> 90
                            "r" -> 50
                            "b", "n" -> 30
                            "p" -> 10
                            "k" -> 1000
                            else -> 0
                        }
                    }
                    validMoves.add(Move(fromIdx, toIdx, piece, targetPiece, score))
                }
            }
        }
        
        if (validMoves.isEmpty()) {
            return state.copy(winner = "Draw") // Or someone lost, simplified
        }
        
        // Pick move: sort by score descending, take highest, break ties randomly
        validMoves.shuffle()
        val bestMove = validMoves.maxByOrNull { it.score } ?: validMoves.first()
        
        return applyMove(state, bestMove.from, bestMove.to, bestMove.piece, bestMove.targetPiece)
    }

    fun reset(): ChessState = ChessState()
}
