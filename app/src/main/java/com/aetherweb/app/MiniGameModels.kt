package com.aetherweb.app

import org.json.JSONObject
import org.json.JSONArray

data class TicTacToeState(
    val board: List<String> = List(9) { "" },
    val isXTurn: Boolean = true,
    val xPlayerId: String = "",
    val oPlayerId: String = "",
    val winner: String = "",
    val botThinking: Boolean = false
) {
    
    /**
     * If the side to move is a bot, apply its move immediately.
     * Call after claiming a bot seat — otherwise a bot playing X stalls,
     * since the old code only triggered bot moves after a human move.
     */
    fun maybeBotMove(): TicTacToeState {
        if (winner.isNotEmpty()) return this
        return if ((isXTurn && xPlayerId == "bot") || (!isXTurn && oPlayerId == "bot")) makeBotMove() else this
    }

    fun makeBotMove(): TicTacToeState {
        if (!board.contains("")) return this
        if (winner.isNotEmpty()) return this
        
        // Simple random bot for now, or just pick first available
        // Wait, minimax is better. Let's do a simple one:
        val avail = board.indices.filter { board[it].isEmpty() }
        if (avail.isEmpty()) return this
        
        val botPiece = if (isXTurn) "X" else "O"
        
        // Win?
        for (i in avail) {
            val test = board.toMutableList()
            test[i] = botPiece
            if (this.copy(board = test).checkWinner().winner == botPiece) {
                test[i] = botPiece
                return this.copy(board = test, isXTurn = !isXTurn).checkWinner()
            }
        }
        
        // Block?
        val humanPiece = if (isXTurn) "O" else "X"
        for (i in avail) {
            val test = board.toMutableList()
            test[i] = humanPiece
            if (this.copy(board = test).checkWinner().winner == humanPiece) {
                val real = board.toMutableList()
                real[i] = botPiece
                return this.copy(board = real, isXTurn = !isXTurn).checkWinner()
            }
        }
        
        // Random center or available
        val move = if (board[4].isEmpty()) 4 else avail.random()
        val next = board.toMutableList()
        next[move] = botPiece
        return this.copy(board = next, isXTurn = !isXTurn).checkWinner()
    }

    fun checkWinner(): TicTacToeState {
        val lines = listOf(
            listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),
            listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),
            listOf(0, 4, 8), listOf(2, 4, 6)
        )
        for (line in lines) {
            if (board[line[0]].isNotEmpty() && board[line[0]] == board[line[1]] && board[line[1]] == board[line[2]]) {
                return this.copy(winner = board[line[0]])
            }
        }
        if (!board.contains("")) return this.copy(winner = "Draw")
        return this
    }

    fun toJson(): String {
        val obj = JSONObject()
        val arr = JSONArray()
        board.forEach { arr.put(it) }
        obj.put("board", arr)
        obj.put("isXTurn", isXTurn)
        obj.put("xPlayerId", xPlayerId)
        obj.put("oPlayerId", oPlayerId)
        obj.put("winner", winner)
        return obj.toString()
    }

    companion object {
        fun fromJson(json: String): TicTacToeState {
            val obj = JSONObject(json)
            val arr = obj.getJSONArray("board")
            val board = List(9) { i -> arr.getString(i) }
            return TicTacToeState(
                board = board,
                isXTurn = obj.getBoolean("isXTurn"),
                xPlayerId = obj.getString("xPlayerId"),
                oPlayerId = obj.getString("oPlayerId"),
                winner = obj.getString("winner")
            )
        }
    }
}

data class Connect4State(
    val board: List<List<Int>> = List(6) { List(7) { 0 } }, // 0 empty, 1 red, 2 yellow
    val isRedTurn: Boolean = true,
    val redPlayerId: String = "",
    val yellowPlayerId: String = "",
    val winner: String = "",
    val botThinking: Boolean = false
) {
    
    /**
     * If the side to move is a bot, apply its move immediately.
     * Call after claiming a bot seat — otherwise a bot playing Red stalls,
     * since the old code only triggered bot moves after a human move.
     */
    fun maybeBotMove(): Connect4State {
        if (winner.isNotEmpty()) return this
        return if ((isRedTurn && redPlayerId == "bot") || (!isRedTurn && yellowPlayerId == "bot")) makeBotMove() else this
    }

    fun makeBotMove(): Connect4State {
        if (winner.isNotEmpty()) return this
        
        // Find available columns
        val availCols = mutableListOf<Int>()
        for (c in 0..6) {
            if (board[0][c] == 0) availCols.add(c)
        }
        if (availCols.isEmpty()) return this
        
        val botPiece = if (isRedTurn) 1 else 2
        val humanPiece = if (isRedTurn) 2 else 1
        
        fun dropPiece(testBoard: List<MutableList<Int>>, col: Int, piece: Int): Boolean {
            for (r in 5 downTo 0) {
                if (testBoard[r][col] == 0) {
                    testBoard[r][col] = piece
                    return true
                }
            }
            return false
        }
        
        // Win?
        for (c in availCols) {
            val test = board.map { it.toMutableList() }
            if (dropPiece(test, c, botPiece)) {
                if (this.copy(board = test).checkWinner().winner.isNotEmpty()) {
                    return this.copy(board = test, isRedTurn = !isRedTurn).checkWinner()
                }
            }
        }
        
        // Block?
        for (c in availCols) {
            val test = board.map { it.toMutableList() }
            if (dropPiece(test, c, humanPiece)) {
                if (this.copy(board = test).checkWinner().winner.isNotEmpty()) {
                    val real = board.map { it.toMutableList() }
                    dropPiece(real, c, botPiece)
                    return this.copy(board = real, isRedTurn = !isRedTurn).checkWinner()
                }
            }
        }
        
        // Random
        val move = availCols.random()
        val next = board.map { it.toMutableList() }
        dropPiece(next, move, botPiece)
        return this.copy(board = next, isRedTurn = !isRedTurn).checkWinner()
    }

    fun checkWinner(): Connect4State {
        // Horizontal
        for (r in 0..5) {
            for (c in 0..3) {
                if (board[r][c] != 0 && board[r][c] == board[r][c+1] && board[r][c] == board[r][c+2] && board[r][c] == board[r][c+3]) {
                    return this.copy(winner = if(board[r][c] == 1) "Red" else "Yellow")
                }
            }
        }
        // Vertical
        for (r in 0..2) {
            for (c in 0..6) {
                if (board[r][c] != 0 && board[r][c] == board[r+1][c] && board[r][c] == board[r+2][c] && board[r][c] == board[r+3][c]) {
                    return this.copy(winner = if(board[r][c] == 1) "Red" else "Yellow")
                }
            }
        }
        // Diagonal /
        for (r in 3..5) {
            for (c in 0..3) {
                if (board[r][c] != 0 && board[r][c] == board[r-1][c+1] && board[r][c] == board[r-2][c+2] && board[r][c] == board[r-3][c+3]) {
                    return this.copy(winner = if(board[r][c] == 1) "Red" else "Yellow")
                }
            }
        }
        // Diagonal \
        for (r in 0..2) {
            for (c in 0..3) {
                if (board[r][c] != 0 && board[r][c] == board[r+1][c+1] && board[r][c] == board[r+2][c+2] && board[r][c] == board[r+3][c+3]) {
                    return this.copy(winner = if(board[r][c] == 1) "Red" else "Yellow")
                }
            }
        }
        
        // Draw
        if (board[0].all { it != 0 }) return this.copy(winner = "Draw")
        return this
    }

    fun toJson(): String {
        val obj = JSONObject()
        val boardArr = JSONArray()
        for (r in 0..5) {
            val rowArr = JSONArray()
            for (c in 0..6) {
                rowArr.put(board[r][c])
            }
            boardArr.put(rowArr)
        }
        obj.put("board", boardArr)
        obj.put("isRedTurn", isRedTurn)
        obj.put("redPlayerId", redPlayerId)
        obj.put("yellowPlayerId", yellowPlayerId)
        obj.put("winner", winner)
        return obj.toString()
    }

    companion object {
        fun fromJson(json: String): Connect4State {
            val obj = JSONObject(json)
            val boardArr = obj.getJSONArray("board")
            val board = mutableListOf<List<Int>>()
            for (r in 0..5) {
                val rowArr = boardArr.getJSONArray(r)
                val row = mutableListOf<Int>()
                for (c in 0..6) {
                    row.add(rowArr.getInt(c))
                }
                board.add(row)
            }
            return Connect4State(
                board = board,
                isRedTurn = obj.getBoolean("isRedTurn"),
                redPlayerId = obj.getString("redPlayerId"),
                yellowPlayerId = obj.getString("yellowPlayerId"),
                winner = obj.getString("winner")
            )
        }
    }
}
