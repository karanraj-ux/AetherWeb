package com.aetherweb.app

import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

data class RadarNode(
    val id: String,
    val name: String,
    val distanceMeters: Double,
    val bearingDegrees: Double,
    val xPos: Float,
    val yPos: Float
)

/**
 * Tactical Sweeping Neon Mesh Radar:
 * 1. Continuous trailing phosphor beam with 360-degree sweep.
 * 2. Concentric distance rings (25m, 50m, 100m, 150m) and cardinal markers (N, E, S, W).
 * 3. Pulsing node blips with interactive quick-action drawers (Call, Video, DM).
 * 4. 100% Offline with zero external mapping servers required.
 */
@Composable
fun RadarView(
    userLocations: Map<String, LocationMessage>,
    myId: String,
    knownUserNames: Map<String, String> = emptyMap(),
    isEcoMode: Boolean = false,
    onToggleEco: (Boolean) -> Unit = {},
    onRefreshLocation: () -> Unit = {},
    onNodeAction: (nodeId: String, nodeName: String, action: String) -> Unit = { _, _, _ -> }
) {
    val myLocation = userLocations[myId]
    var selectedNode by remember { mutableStateOf<RadarNode?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF060D13))) {
        if (myLocation == null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.Center)
            ) {
                CircularProgressIndicator(color = Color(0xFF10B981), strokeWidth = 3.dp, modifier = Modifier.size(52.dp))
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    "Calibrating Mesh Sonar...",
                    color = Color(0xFFE2E8F0),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Acquiring local GPS coordinates for radial mapping",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onRefreshLocation,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF10B981))
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Trigger Instant Fix")
                }
            }
            return@Box
        }

        val infiniteTransition = rememberInfiniteTransition(label = "RadarSweep")
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(if (isEcoMode) 6000 else 3000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "RadarRotation"
        )

        val pulseAnimation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "RadarPulse"
        )

        val maxDistanceMeters = 150.0
        val detectedNodes = remember(userLocations, myLocation) {
            userLocations.filter { it.key != myId }.map { (id, loc) ->
                val dist = haversine(myLocation.lat, myLocation.lng, loc.lat, loc.lng)
                val bear = bearing(myLocation.lat, myLocation.lng, loc.lat, loc.lng)
                val fallbackName = if (loc.name.isNotBlank()) loc.name else "Peer ${id.take(4)}"
                val nodeName = knownUserNames[id] ?: fallbackName
                RadarNode(id, nodeName, dist, bear, 0f, 0f)
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val widthPx = constraints.maxWidth.toFloat()
            val heightPx = constraints.maxHeight.toFloat()
            val center = Offset(widthPx / 2, heightPx / 2)
            val maxRadius = (widthPx / 2.15f).coerceAtMost(heightPx / 2.35f)

            Canvas(modifier = Modifier.fillMaxSize()) {
                // Outer pulsing perimeter aura
                drawCircle(
                    color = Color(0xFF10B981).copy(alpha = 0.12f * (1f - pulseAnimation)),
                    radius = maxRadius + (pulseAnimation * 50f),
                    center = center
                )

                // Concentric range rings
                val rings = listOf(0.16f, 0.33f, 0.66f, 1.0f)
                rings.forEachIndexed { idx, frac ->
                    val ringR = maxRadius * frac
                    drawCircle(
                        color = Color(0xFF1E293B).copy(alpha = 0.8f),
                        radius = ringR,
                        center = center,
                        style = Stroke(width = if (idx == rings.lastIndex) 2f else 1f)
                    )
                }

                // Crosshairs
                drawLine(
                    color = Color(0xFF1E293B),
                    start = Offset(center.x, center.y - maxRadius),
                    end = Offset(center.x, center.y + maxRadius),
                    strokeWidth = 1.2f
                )
                drawLine(
                    color = Color(0xFF1E293B),
                    start = Offset(center.x - maxRadius, center.y),
                    end = Offset(center.x + maxRadius, center.y),
                    strokeWidth = 1.2f
                )

                // Sweeping neon beam with phosphor trailing glow
                rotate(degrees = rotation, pivot = center) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFF10B981).copy(alpha = 0.04f),
                                Color(0xFF10B981).copy(alpha = 0.5f)
                            ),
                            center = center
                        ),
                        startAngle = -90f,
                        sweepAngle = 90f,
                        useCenter = true,
                        topLeft = Offset(center.x - maxRadius, center.y - maxRadius),
                        size = Size(maxRadius * 2, maxRadius * 2)
                    )
                    // High-intensity front sweep ray
                    drawLine(
                        color = Color(0xFF34D399),
                        start = center,
                        end = Offset(center.x, center.y - maxRadius),
                        strokeWidth = 3f
                    )
                }

                // Local Device Center Beacon
                drawCircle(color = Color(0xFF10B981).copy(alpha = 0.25f), radius = 24f, center = center)
                drawCircle(color = Color(0xFF10B981), radius = 9f, center = center)
                drawCircle(color = Color.White, radius = 4f, center = center)
            }

            val density = androidx.compose.ui.platform.LocalDensity.current

            // CARDINAL DIRECTION MARKERS (N, E, S, W)
            Text(
                "N",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF10B981),
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = with(density) { -(maxRadius + 18f).toDp() })
            )
            Text(
                "S",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64748B),
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = with(density) { (maxRadius + 18f).toDp() })
            )
            Text(
                "W",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64748B),
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = with(density) { -(maxRadius + 18f).toDp() })
            )
            Text(
                "E",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64748B),
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = with(density) { (maxRadius + 18f).toDp() })
            )

            // INTERACTIVE DETECTED NODE BLIPS
            detectedNodes.forEach { node ->
                val clampedDist = node.distanceMeters.coerceAtMost(maxDistanceMeters)
                val radiusPx = (clampedDist / maxDistanceMeters) * maxRadius
                val angleRad = Math.toRadians(node.bearingDegrees - 90).toFloat()
                val x: Float = center.x + (radiusPx * cos(angleRad.toDouble())).toFloat()
                val y: Float = center.y + (radiusPx * sin(angleRad.toDouble())).toFloat()

                val xDp = with(density) { x.toDp() } - 20.dp
                val yDp = with(density) { y.toDp() } - 20.dp

                val blipAngle = (node.bearingDegrees + 360) % 360
                val currentAngle = (rotation + 360) % 360
                val diff = (currentAngle - blipAngle + 360) % 360
                val isSwept = diff < 80

                Box(
                    modifier = Modifier
                        .offset(x = xDp, y = yDp)
                        .size(40.dp)
                        .clickable {
                            selectedNode = node.copy(xPos = x, yPos = y)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Swept aura glow
                    if (isSwept) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF25D366).copy(alpha = 0.35f))
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF25D366))
                            .border(2.dp, Color.White, CircleShape)
                    )
                }
            }
        }

        // TOP TACTICAL HEADER
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xD90F172A),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFF10B981), CircleShape))
                    Text(
                        "MESH RADAR (2.4GHz / BLE)",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp
                    )
                    Text("•", color = Color.Gray)
                    Text(
                        "${detectedNodes.size} Peer${if (detectedNodes.size == 1) "" else "s"}",
                        color = Color(0xFF10B981),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // BOTTOM PEER ACTION DRAWER
        selectedNode?.let { node ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xF20F172A)),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0xFF10B981).copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.MyLocation, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(node.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                                Text(
                                    "Distance: ${node.distanceMeters.toInt()}m • Bearing: ${node.bearingDegrees.toInt()}°",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }

                        IconButton(onClick = { selectedNode = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onNodeAction(node.id, node.name, "call"); selectedNode = null },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Call, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Call", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Button(
                            onClick = { onNodeAction(node.id, node.name, "video"); selectedNode = null },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Videocam, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Video", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Button(
                            onClick = { onNodeAction(node.id, node.name, "chat"); selectedNode = null },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Forum, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Chat", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0 // Earth radius in meters
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    return r * c
}

private fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val deltaLambda = Math.toRadians(lon2 - lon1)
    val y = sin(deltaLambda) * cos(phi2)
    val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
    val theta = kotlin.math.atan2(y, x)
    return (Math.toDegrees(theta) + 360) % 360
}
