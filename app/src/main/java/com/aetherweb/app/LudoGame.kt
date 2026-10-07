package com.aetherweb.app

import org.json.JSONArray
import org.json.JSONObject

data class LudoState(
    val turn: Int = 0,
    val diceValue: Int = 1,
    val hasRolled: Boolean = false,
    val pieces: Map<Int, List<Int>> = mapOf(
        0 to listOf(0, 0, 0, 0),
        1 to listOf(0, 0, 0, 0),
        2 to listOf(0, 0, 0, 0),
        3 to listOf(0, 0, 0, 0)
    ),
    val winner: Int = -1,
    val playerIds: Map<Int, String> = mapOf(0 to "", 1 to "", 2 to "", 3 to ""),
    val botThinking: Boolean = false
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("turn", turn)
        obj.put("diceValue", diceValue)
        obj.put("hasRolled", hasRolled)
        obj.put("winner", winner)
        val idObj = JSONObject()
        for (i in 0..3) idObj.put(i.toString(), playerIds[i] ?: "")
        obj.put("playerIds", idObj)
        val pObj = JSONObject()
        for (i in 0..3) {
            val arr = JSONArray()
            pieces[i]?.forEach { arr.put(it) }
            pObj.put(i.toString(), arr)
        }
        obj.put("pieces", pObj)
        return obj
    }

    companion object {
        fun fromJson(json: String): LudoState {
            val obj = JSONObject(json)
            val pObj = obj.getJSONObject("pieces")
            val p = mutableMapOf<Int, List<Int>>()
            for (i in 0..3) {
                val arr = pObj.getJSONArray(i.toString())
                p[i] = listOf(arr.getInt(0), arr.getInt(1), arr.getInt(2), arr.getInt(3))
            }
            return LudoState(
                turn = obj.getInt("turn"),
                diceValue = obj.getInt("diceValue"),
                hasRolled = obj.getBoolean("hasRolled"),
                winner = obj.optInt("winner", -1),
                pieces = p
            )
        }
    }
}

object LudoEngine {
    val trackMap = arrayOf(
        1 to 6, 2 to 6, 3 to 6, 4 to 6, 5 to 6,
        6 to 5, 6 to 4, 6 to 3, 6 to 2, 6 to 1, 6 to 0,
        7 to 0, 8 to 0,
        8 to 1, 8 to 2, 8 to 3, 8 to 4, 8 to 5,
        9 to 6, 10 to 6, 11 to 6, 12 to 6, 13 to 6, 14 to 6,
        14 to 7, 14 to 8,
        13 to 8, 12 to 8, 11 to 8, 10 to 8, 9 to 8,
        8 to 9, 8 to 10, 8 to 11, 8 to 12, 8 to 13, 8 to 14,
        7 to 14, 6 to 14,
        6 to 13, 6 to 12, 6 to 11, 6 to 10, 6 to 9,
        5 to 8, 4 to 8, 3 to 8, 2 to 8, 1 to 8, 0 to 8,
        0 to 7, 0 to 6
    )

    val redHomeStretch = arrayOf(1 to 7, 2 to 7, 3 to 7, 4 to 7, 5 to 7)
    val greenHomeStretch = arrayOf(7 to 1, 7 to 2, 7 to 3, 7 to 4, 7 to 5)
    val yellowHomeStretch = arrayOf(13 to 7, 12 to 7, 11 to 7, 10 to 7, 9 to 7)
    val blueHomeStretch = arrayOf(7 to 13, 7 to 12, 7 to 11, 7 to 10, 7 to 9)

    fun getCoordinate(player: Int, pieceIndex: Int, relativePos: Int): Pair<Int, Int> {
        if (relativePos == 0) {
            val bases = listOf(
                listOf(2 to 2, 3 to 2, 2 to 3, 3 to 3),
                listOf(11 to 2, 12 to 2, 11 to 3, 12 to 3),
                listOf(11 to 11, 12 to 11, 11 to 12, 12 to 12),
                listOf(2 to 11, 3 to 11, 2 to 12, 3 to 12)
            )
            return bases[player][pieceIndex]
        } else if (relativePos <= 51) {
            val offsets = listOf(0, 13, 26, 39)
            val absPos = ((relativePos - 1 + offsets[player]) % 52)
            return trackMap[absPos]
        } else if (relativePos <= 56) {
            val stretchIdx = relativePos - 52
            val stretches = listOf(redHomeStretch, arrayOf(7 to 1, 7 to 2, 7 to 3, 7 to 4, 7 to 5), yellowHomeStretch, blueHomeStretch)
            return stretches[player][stretchIdx]
        } else {
            return 7 to 7
        }
    }

    fun rollDice(state: LudoState): LudoState {
        if (state.hasRolled || state.winner != -1) return state
        val roll = (1..6).random()
        
        val canMove = state.pieces[state.turn]?.any { pos ->
            if (pos == 0) roll == 6 else (pos + roll <= 57)
        } ?: false
        
        return if (!canMove) {
            state.copy(diceValue = roll, hasRolled = false, turn = if(roll == 6) state.turn else (state.turn + 1) % 4)
        } else {
            state.copy(diceValue = roll, hasRolled = true)
        }
    }

    
    fun makeBotMove(state: LudoState): LudoState {
        if (state.winner != -1) return state
        
        var currentState = state
        while (currentState.playerIds[currentState.turn] == "bot") {
            if (!currentState.hasRolled) {
                currentState = rollDice(currentState)
            }
            if (currentState.hasRolled) {
                val player = currentState.turn
                val pieces = currentState.pieces[player]!!
                val roll = currentState.diceValue
                
                // Find a piece to move
                val validIndices = pieces.indices.filter { idx ->
                    val pos = pieces[idx]
                    if (pos == 0) roll == 6 else (pos + roll <= 57)
                }
                
                if (validIndices.isNotEmpty()) {
                    // Simple logic: favor moving pieces out of base, then just random
                    val outOfBase = validIndices.filter { pieces[it] == 0 }
                    val pick = if (outOfBase.isNotEmpty()) outOfBase.first() else validIndices.random()
                    currentState = movePiece(currentState, player, pick)
                } else {
                    // Should theoretically not happen if rollDice clears hasRolled when no moves,
                    // but just in case, pass turn
                    currentState = currentState.copy(hasRolled = false, turn = if (roll == 6) currentState.turn else (currentState.turn + 1) % 4)
                }
            } else {
                // If rollDice didn't result in hasRolled=true, it means turn was passed
                // The loop will continue if the next turn is also a bot
            }
        }
        return currentState
    }

    fun movePiece(state: LudoState, player: Int, pieceIndex: Int): LudoState {
        if (player != state.turn || !state.hasRolled || state.winner != -1) return state
        val pos = state.pieces[player]!![pieceIndex]
        val roll = state.diceValue
        
        var newPos = pos
        if (pos == 0) {
            if (roll == 6) newPos = 1 else return state
        } else {
            if (pos + roll > 57) return state
            newPos = pos + roll
        }

        val mutablePieces = state.pieces.mapValues { it.value.toMutableList() }.toMutableMap()
        mutablePieces[player]!![pieceIndex] = newPos
        
        val safeSpots = listOf(1, 9, 14, 22, 27, 35, 40, 48)
        if (newPos <= 51) {
            val absPos = ((newPos - 1 + listOf(0, 13, 26, 39)[player]) % 52) + 1
            if (absPos !in safeSpots) {
                for (other in 0..3) {
                    if (other == player) continue
                    for (i in 0..3) {
                        val otherPos = mutablePieces[other]!![i]
                        if (otherPos in 1..51) {
                            val otherAbsPos = ((otherPos - 1 + listOf(0, 13, 26, 39)[other]) % 52) + 1
                            if (otherAbsPos == absPos) {
                                mutablePieces[other]!![i] = 0 // Capture
                            }
                        }
                    }
                }
            }
        }

        var winner = state.winner
        if (mutablePieces[player]!!.all { it == 57 }) {
            winner = player
        }

        val nextTurn = if (roll == 6) state.turn else (state.turn + 1) % 4
        return state.copy(pieces = mutablePieces, hasRolled = false, turn = nextTurn, winner = winner)
    }
}
