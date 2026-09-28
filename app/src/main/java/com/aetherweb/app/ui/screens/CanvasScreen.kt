package com.aetherweb.app.ui.screens

import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aetherweb.app.DrawPath
import java.util.UUID

enum class CanvasTool {
    PEN, HIGHLIGHTER, LASER, ERASER
}

/**
 * Studio-Grade Collaborative Smartboard Canvas:
 * 1. Smooth Bézier Curve Interpolation (quadTo) for fluid digital ink.
 * 2. Apple Pencil-style floating dock pill at bottom center.
 * 3. Neon Highlighter & Presentation Laser modes.
 * 4. Multi-user synchronized whiteboard.
 */
@Composable
fun CanvasScreen(
    canvasPaths: List<DrawPath>,
    lasers: Map<String, Offset>,
    backgroundUrl: String = "",
    onSendCanvasMessage: (action: String, id: String, color: String, x: Float, y: Float, strokeWidth: Float, data: String) -> Unit,
    onUploadBackground: (Uri) -> Unit = {},
    onSaveCanvas: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var activeTool by remember { mutableStateOf(CanvasTool.PEN) }
    var currentColorHex by remember { mutableStateOf("#000000") }
    var currentStrokeWidth by remember { mutableFloatStateOf(8f) }
    var showStrokeSlider by remember { mutableStateOf(false) }

    var currentPathId by remember { mutableStateOf("") }
    var lastSentOffset by remember { mutableStateOf(Offset.Zero) }

    val bgLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { onUploadBackground(it) }
    }

    val palette = listOf(
        "#000000", // Black
        "#25D366", // Mesh Neon Green
        "#53BDEB", // Cyber Blue
        "#EF4444", // Crimson Red
        "#F59E0B", // Sunset Amber
        "#A855F7", // Purple Neon
        "#FFFFFF"  // Pure White
    )

    // Drag / Touch logic with sub-pixel sampling
    val dragLogic: suspend PointerInputScope.() -> Unit = {
        val canvasSize = size
        detectDragGestures(
            onDragStart = { offset ->
                currentPathId = "mesh-${UUID.randomUUID().toString().take(8)}"
                lastSentOffset = offset
                when (activeTool) {
                    CanvasTool.LASER -> {
                        onSendCanvasMessage("laser", "", currentColorHex, offset.x / canvasSize.width, offset.y / canvasSize.height, 12f, "")
                    }
                    CanvasTool.ERASER -> {
                        onSendCanvasMessage("start", currentPathId, "ERASER", offset.x / canvasSize.width, offset.y / canvasSize.height, currentStrokeWidth * 2.5f, "")
                    }
                    CanvasTool.HIGHLIGHTER -> {
                        onSendCanvasMessage("start", currentPathId, currentColorHex, offset.x / canvasSize.width, offset.y / canvasSize.height, currentStrokeWidth * 2.8f, "hl")
                    }
                    CanvasTool.PEN -> {
                        onSendCanvasMessage("start", currentPathId, currentColorHex, offset.x / canvasSize.width, offset.y / canvasSize.height, currentStrokeWidth, "")
                    }
                }
            },
            onDrag = { change, _ ->
                change.consume()
                val dist = (change.position - lastSentOffset).getDistance()
                if (dist > 3.5f) {
                    lastSentOffset = change.position
                    val nx = (change.position.x / canvasSize.width).coerceIn(0f, 1f)
                    val ny = (change.position.y / canvasSize.height).coerceIn(0f, 1f)
                    when (activeTool) {
                        CanvasTool.LASER -> {
                            onSendCanvasMessage("laser", "", currentColorHex, nx, ny, 12f, "")
                        }
                        CanvasTool.ERASER -> {
                            onSendCanvasMessage("move", currentPathId, "ERASER", nx, ny, currentStrokeWidth * 2.5f, "")
                        }
                        CanvasTool.HIGHLIGHTER -> {
                            onSendCanvasMessage("move", currentPathId, currentColorHex, nx, ny, currentStrokeWidth * 2.8f, "hl")
                        }
                        CanvasTool.PEN -> {
                            onSendCanvasMessage("move", currentPathId, currentColorHex, nx, ny, currentStrokeWidth, "")
                        }
                    }
                }
            },
            onDragEnd = {
                if (activeTool != CanvasTool.LASER) {
                    onSendCanvasMessage("end", currentPathId, currentColorHex, 0f, 0f, currentStrokeWidth, "")
                }
            },
            onDragCancel = {
                if (activeTool != CanvasTool.LASER) {
                    onSendCanvasMessage("end", currentPathId, currentColorHex, 0f, 0f, currentStrokeWidth, "")
                }
            }
        )
    }

    // Bézier smoothed drawing engine
    val drawContent: DrawScope.() -> Unit = {
        canvasPaths.forEach { drawPath ->
            val pts = drawPath.points
            if (pts.isNotEmpty()) {
                val path = Path()
                val isHighlighter = drawPath.data == "hl"
                val strokeColor = if (isHighlighter) {
                    drawPath.color.copy(alpha = 0.38f)
                } else {
                    drawPath.color
                }

                if (pts.size == 1) {
                    path.moveTo(pts[0].x * size.width, pts[0].y * size.height)
                    path.lineTo(pts[0].x * size.width + 0.1f, pts[0].y * size.height + 0.1f)
                } else if (pts.size == 2) {
                    path.moveTo(pts[0].x * size.width, pts[0].y * size.height)
                    path.lineTo(pts[1].x * size.width, pts[1].y * size.height)
                } else {
                    path.moveTo(pts[0].x * size.width, pts[0].y * size.height)
                    for (i in 1 until pts.size - 1) {
                        val current = pts[i]
                        val next = pts[i + 1]
                        val currentX = current.x * size.width
                        val currentY = current.y * size.height
                        val nextX = next.x * size.width
                        val nextY = next.y * size.height
                        val midX = (currentX + nextX) / 2f
                        val midY = (currentY + nextY) / 2f
                        path.quadraticBezierTo(currentX, currentY, midX, midY)
                    }
                    val last = pts.last()
                    path.lineTo(last.x * size.width, last.y * size.height)
                }

                drawPath(
                    path = path,
                    color = strokeColor,
                    style = Stroke(
                        width = drawPath.strokeWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    ),
                    blendMode = if (drawPath.isEraser) BlendMode.Clear else BlendMode.SrcOver
                )
            }
        }

        // Live Lasers with glowing aura
        lasers.values.forEach { offset ->
            val center = Offset(offset.x * size.width, offset.y * size.height)
            drawCircle(color = Color(0xFFFF1744).copy(alpha = 0.25f), radius = 24f, center = center)
            drawCircle(color = Color(0xFFFF5252), radius = 14f, center = center)
            drawCircle(color = Color.White, radius = 6f, center = center)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        // BOARD SURFACE
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp)
                .clipToBounds()
                .background(Color(0xFF1E293B))
                .graphicsLayer { alpha = 0.99f }
                .pointerInput(listOf(activeTool, currentColorHex, currentStrokeWidth)) {
                    dragLogic()
                }
        ) {
            if (backgroundUrl.isNotBlank()) {
                AsyncImage(
                    model = backgroundUrl,
                    contentDescription = "Board Background",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
            Canvas(modifier = Modifier.fillMaxSize(), onDraw = drawContent)
        }

        // TOP HEADER BADGE
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
                .background(Color(0xCC0F172A), RoundedCornerShape(20.dp))
                .border(1.dp, Color(0xFF334155), RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.size(8.dp).background(Color(0xFF10B981), CircleShape))
            Text("Mesh Smartboard Studio", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("•", color = Color.Gray)
            Text(
                when (activeTool) {
                    CanvasTool.PEN -> "Pen (${currentStrokeWidth.toInt()}px)"
                    CanvasTool.HIGHLIGHTER -> "Highlighter"
                    CanvasTool.LASER -> "Laser Pointer"
                    CanvasTool.ERASER -> "Eraser"
                },
                fontSize = 11.sp,
                color = Color(0xFF94A3B8)
            )
        }

        // STROKE THICKNESS POPOVER
        AnimatedVisibility(
            visible = showStrokeSlider,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 90.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xF01E293B)),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Size", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Slider(
                        value = currentStrokeWidth,
                        onValueChange = { currentStrokeWidth = it },
                        valueRange = 2f..48f,
                        modifier = Modifier.width(140.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF10B981),
                            activeTrackColor = Color(0xFF10B981),
                            inactiveTrackColor = Color(0xFF334155)
                        )
                    )
                    Text("${currentStrokeWidth.toInt()}px", fontSize = 12.sp, color = Color.LightGray)
                }
            }
        }

        // APPLE PENCIL-STYLE FLOATING BOTTOM DOCK
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .shadow(16.dp, RoundedCornerShape(32.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xF20F172A)),
            shape = RoundedCornerShape(32.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 1. TOOL SELECTORS
                ToolDockButton(
                    icon = Icons.Default.Edit,
                    label = "Pen",
                    isSelected = activeTool == CanvasTool.PEN,
                    onClick = { activeTool = CanvasTool.PEN }
                )
                ToolDockButton(
                    icon = Icons.Default.Highlight,
                    label = "Glow",
                    isSelected = activeTool == CanvasTool.HIGHLIGHTER,
                    onClick = { activeTool = CanvasTool.HIGHLIGHTER }
                )
                ToolDockButton(
                    icon = Icons.Default.AutoAwesome,
                    label = "Laser",
                    isSelected = activeTool == CanvasTool.LASER,
                    onClick = { activeTool = CanvasTool.LASER }
                )
                ToolDockButton(
                    icon = Icons.Default.Clear,
                    label = "Eraser",
                    isSelected = activeTool == CanvasTool.ERASER,
                    onClick = { activeTool = CanvasTool.ERASER }
                )

                // DIVIDER
                Box(modifier = Modifier.size(width = 1.dp, height = 24.dp).background(Color(0xFF334155)))

                // 2. PALETTE
                palette.take(4).forEach { hex ->
                    val color = Color(AndroidColor.parseColor(hex))
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (currentColorHex == hex) 2.5.dp else 0.dp,
                                color = if (currentColorHex == hex) Color(0xFF10B981) else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { currentColorHex = hex; activeTool = CanvasTool.PEN }
                    )
                }

                // DIVIDER
                Box(modifier = Modifier.size(width = 1.dp, height = 24.dp).background(Color(0xFF334155)))

                // 3. SIZE & ACTIONS
                IconButton(
                    onClick = { showStrokeSlider = !showStrokeSlider },
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size((currentStrokeWidth.coerceIn(6f, 20f)).dp)
                            .background(Color.White, CircleShape)
                    )
                }

                IconButton(
                    onClick = { onSendCanvasMessage("undo", "", "#000000", 0f, 0f, 10f, "") },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Undo", tint = Color.LightGray, modifier = Modifier.size(18.dp))
                }

                IconButton(
                    onClick = { onSendCanvasMessage("clear", "", "#000000", 0f, 0f, 10f, "") },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                }

                IconButton(
                    onClick = { bgLauncher.launch("image/*") },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.Image, contentDescription = "Background", tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                }

                IconButton(
                    onClick = { onSaveCanvas() },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Save Canvas", tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun ToolDockButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) Color(0xFF10B981) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) Color.Black else Color.LightGray,
            modifier = Modifier.size(18.dp)
        )
    }
}
