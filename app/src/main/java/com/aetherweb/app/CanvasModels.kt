package com.aetherweb.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

data class CanvasMessage(
    val type: String = "canvas",
    val action: String, // "start", "move", "end", "clear", "undo", "laser", "bg"
    val id: String = "",
    val color: String = "#000000",
    val x: Float = 0f,
    val y: Float = 0f,
    val strokeWidth: Float = 10f,
    val data: String = "" // For background URL or shape type
)

data class DrawPath(
    val id: String,
    val color: Color,
    val points: List<Offset>,
    val strokeWidth: Float = 10f,
    val isEraser: Boolean = false,
    val data: String = ""
)

data class PollState(
    val id: String,
    val question: String,
    val options: List<String>,
    val votes: Map<String, Int> = emptyMap(), // voterId -> optionIndex
    val hostId: String,
    val attachmentUrl: String = ""
)

data class RandomizerState(
    val id: String,
    val type: String,
    val prompt: String,
    val playerName: String
)
