package com.aetherweb.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.viewinterop.AndroidView
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.ui.platform.LocalContext
import com.aetherweb.app.CallManager
import com.aetherweb.app.CallState
import com.aetherweb.app.LiveVideoState
import com.aetherweb.app.ProximitySensorHelper

@Composable
fun CallScreenOverlay(
    sendMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val callSession by CallManager.callSession.collectAsState()
    val videoManager = CallManager.getLiveVideoManager()
    val videoState by (videoManager?.state?.collectAsState() ?: remember { mutableStateOf(LiveVideoState()) })
    var showMoreMenu by remember { mutableStateOf(false) }

    val proximityHelper = remember { ProximitySensorHelper(context) }
    val isNearEar by proximityHelper.isNear.collectAsState()

    DisposableEffect(callSession.state, callSession.isVideoEnabled) {
        if (callSession.state == CallState.ACTIVE && !callSession.isVideoEnabled) {
            proximityHelper.start()
        } else {
            proximityHelper.stop()
        }
        onDispose {
            proximityHelper.stop()
        }
    }

    if (callSession.state == CallState.IDLE) return

    // Screen Blackout during audio calls when held to ear (saves battery and avoids cheek touches)
    if (isNearEar && !callSession.isVideoEnabled && callSession.state == CallState.ACTIVE) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(enabled = false) {}
        )
        return
    }

    // If minimized, display floating top banner over whatever screen the user is on
    if (callSession.isMinimized) {
        CallMinimizedPill(
            durationSeconds = callSession.callDurationSeconds,
            peerName = callSession.peerName,
            isMuted = callSession.isMuted,
            onExpand = { CallManager.setMinimized(false) },
            onEndCall = { CallManager.endCall(sendMessage) }
        )
        return
    }

    // Full-Screen WhatsApp Style Call View
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B141B)) // Deep dark WhatsApp canvas
            .testTag("call_screen_overlay")
    ) {
        // Subtle background doodle & acoustic radial aura
        CallBackgroundPattern()

        // Remote Video Feed (Hardware H.264 Decoder Surface)
        if (callSession.isVideoEnabled) {
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                videoManager?.attachRemoteSurface(Surface(st))
                            }
                            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                videoManager?.attachRemoteSurface(null)
                                return true
                            }
                            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Local Camera Preview Floating Picture-in-Picture (PiP)
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 64.dp, end = 16.dp)
                    .size(width = 112.dp, height = 164.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.Black,
                shadowElevation = 10.dp,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF25D366).copy(alpha = 0.6f))
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { ctx ->
                            TextureView(ctx).apply {
                                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                        if (videoState.isFrontCamera) {
                                            scaleX = -1f // Mirror front camera preview like WhatsApp
                                        } else {
                                            scaleX = 1f
                                        }
                                        videoManager?.attachLocalPreview(this@apply)
                                    }
                                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                        videoManager?.attachLocalPreview(null)
                                        return true
                                    }
                                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {
                                        if (videoState.isFrontCamera && scaleX != -1f) {
                                            scaleX = -1f
                                        } else if (!videoState.isFrontCamera && scaleX != 1f) {
                                            scaleX = 1f
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Flip camera button
                    IconButton(
                        onClick = { videoManager?.toggleCameraFacing() },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .size(32.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FlipCameraIos,
                            contentDescription = "Flip Camera",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Minimize Button
                IconButton(
                    onClick = { CallManager.toggleMinimize() },
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFF1F2C34).copy(alpha = 0.6f), CircleShape)
                        .testTag("call_minimize_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Minimize Call",
                        tint = Color.White
                    )
                }

                // Call Security & Participant Header
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "E2EE",
                            modifier = Modifier.size(14.dp),
                            tint = Color(0xFF8696A0)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "End-to-end encrypted",
                            fontSize = 12.sp,
                            color = Color(0xFF8696A0),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Add Participant button
                IconButton(
                    onClick = {
                        // Future multi-peer call expansion
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFF1F2C34).copy(alpha = 0.6f), CircleShape)
                        .testTag("call_add_participant_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = "Add Participant",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Peer Name
            Text(
                text = callSession.peerName.ifBlank { "Nearby Peer" },
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Call State / Timer Display
            when (callSession.state) {
                CallState.OUTGOING -> {
                    Text(
                        text = "Ringing...",
                        fontSize = 15.sp,
                        color = Color(0xFF8696A0)
                    )
                }
                CallState.INCOMING -> {
                    Text(
                        text = "Incoming WhatsApp Voice Call",
                        fontSize = 15.sp,
                        color = Color(0xFF25D366),
                        fontWeight = FontWeight.Medium
                    )
                }
                CallState.ACTIVE -> {
                    val minutes = callSession.callDurationSeconds / 60
                    val seconds = callSession.callDurationSeconds % 60
                    val formattedTime = String.format("%02d:%02d", minutes, seconds)
                    Text(
                        text = formattedTime,
                        fontSize = 15.sp,
                        color = Color(0xFF8696A0),
                        fontWeight = FontWeight.SemiBold
                    )
                }
                else -> {}
            }

            // Connection & Codec Metrics Badge with 100m+ Adaptive Status
            if (callSession.state == CallState.ACTIVE) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1F2C34).copy(alpha = 0.85f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (callSession.isVideoEnabled) Color(0xFF25D366).copy(alpha = 0.5f) else Color.Transparent
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (videoState.packetLossPercent > 25) Color(0xFFEA4335) else Color(0xFF25D366),
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (callSession.isVideoEnabled) {
                                "⚡ ${videoState.resolution} • ${videoState.networkQuality} • ${videoState.latencyMs}ms • Wi-Fi Direct"
                            } else {
                                "${callSession.connectionType} • Ultra-low latency VoIP"
                            },
                            fontSize = 11.sp,
                            color = if (callSession.isVideoEnabled) Color(0xFF25D366) else Color(0xFF8696A0),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Centerpiece: Avatar Aura for Voice OR Waiting Indicator for Video
            if (!callSession.isVideoEnabled) {
                CallAvatarAura(
                    peerName = callSession.peerName,
                    isActive = callSession.state == CallState.ACTIVE && !callSession.isMuted
                )
            } else if (!videoState.isReceiving && callSession.state == CallState.ACTIVE) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1F2C34).copy(alpha = 0.85f),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF25D366)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Awaiting peer H.264 video feed over UDP...",
                            fontSize = 13.sp,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1.2f))

            // Bottom Actions (WhatsApp Signature Dock)
            if (callSession.state == CallState.INCOMING) {
                // Incoming Call Action Bar (Answer / Decline)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp, vertical = 40.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Decline Button
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FloatingActionButton(
                            onClick = { CallManager.declineCall(sendMessage) },
                            containerColor = Color(0xFFEA4335),
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier
                                .size(68.dp)
                                .testTag("decline_call_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = "Decline Call",
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Decline", color = Color(0xFF8696A0), fontSize = 13.sp)
                    }

                    // Answer Button
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FloatingActionButton(
                            onClick = { CallManager.answerCall(sendMessage) },
                            containerColor = Color(0xFF25D366),
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier
                                .size(68.dp)
                                .testTag("answer_call_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "Answer Call",
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Answer", color = Color(0xFF25D366), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                // Active / Outgoing Floating Dock (5 buttons like the user's reference image)
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 28.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(36.dp),
                    color = Color(0xFF1F2C34).copy(alpha = 0.95f),
                    shadowElevation = 12.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. More options (...)
                        IconButton(
                            onClick = { showMoreMenu = true },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0xFF2A3942), CircleShape)
                                .testTag("call_more_options_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                tint = Color.White
                            )
                        }

                        // 2. Video Toggle
                        IconButton(
                            onClick = { CallManager.toggleVideo() },
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    if (callSession.isVideoEnabled) Color.White else Color(0xFF2A3942),
                                    CircleShape
                                )
                                .testTag("call_video_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (callSession.isVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                contentDescription = "Video Mode",
                                tint = if (callSession.isVideoEnabled) Color.Black else Color.White
                            )
                        }

                        // 3. Speakerphone Toggle
                        IconButton(
                            onClick = { CallManager.toggleSpeaker() },
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    if (callSession.isSpeakerOn) Color.White else Color(0xFF2A3942),
                                    CircleShape
                                )
                                .testTag("call_speaker_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (callSession.isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                                contentDescription = "Speakerphone",
                                tint = if (callSession.isSpeakerOn) Color.Black else Color.White
                            )
                        }

                        // 4. Microphone Mute Toggle
                        IconButton(
                            onClick = { CallManager.toggleMute() },
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    if (callSession.isMuted) Color.White else Color(0xFF2A3942),
                                    CircleShape
                                )
                                .testTag("call_mute_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (callSession.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "Mute",
                                tint = if (callSession.isMuted) Color.Black else Color.White
                            )
                        }

                        // 5. End Call Button (Big Red Circle)
                        FloatingActionButton(
                            onClick = { CallManager.endCall(sendMessage) },
                            containerColor = Color(0xFFEA4335),
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier
                                .size(56.dp)
                                .testTag("end_call_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = "End Call",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // More Options Bottom Dialog
    if (showMoreMenu) {
        Dialog(onDismissRequest = { showMoreMenu = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF1F2C34),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Call Settings & Audio Route",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    ListItem(
                        headlineContent = { Text("Output: ${if (callSession.isSpeakerOn) "Speakerphone" else "Ear Piece"}", color = Color.White) },
                        leadingContent = { Icon(Icons.Default.VolumeUp, contentDescription = null, tint = Color(0xFF25D366)) },
                        modifier = Modifier.clickable {
                            CallManager.toggleSpeaker()
                            showMoreMenu = false
                        }
                    )

                    if (callSession.isVideoEnabled) {
                        ListItem(
                            headlineContent = { Text("Flip Camera: ${if (videoState.isFrontCamera) "Front (Selfie)" else "Rear"}", color = Color.White) },
                            leadingContent = { Icon(Icons.Default.FlipCameraIos, contentDescription = null, tint = Color(0xFF25D366)) },
                            modifier = Modifier.clickable {
                                videoManager?.toggleCameraFacing()
                                showMoreMenu = false
                            }
                        )
                    }

                    ListItem(
                        headlineContent = { Text("100m+ Range Engine", color = Color.White) },
                        supportingContent = { Text("${videoState.distanceMode} • ${videoState.networkQuality}", color = Color(0xFF8696A0), fontSize = 12.sp) },
                        leadingContent = { Icon(Icons.Default.Wifi, contentDescription = null, tint = Color(0xFF25D366)) }
                    )

                    ListItem(
                        headlineContent = { Text("Acoustic Echo Cancellation", color = Color.White) },
                        supportingContent = { Text("Hardware AEC & Noise Gate Active", color = Color(0xFF8696A0), fontSize = 12.sp) },
                        leadingContent = { Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Color(0xFF25D366)) }
                    )

                    ListItem(
                        headlineContent = { Text("E2EE Encryption", color = Color.White) },
                        supportingContent = { Text("ECDH P-256 + AES-CTR Voice Stream", color = Color(0xFF8696A0), fontSize = 12.sp) },
                        leadingContent = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF25D366)) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { showMoreMenu = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Done", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CallAvatarAura(peerName: String, isActive: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "CallAura")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isActive) 1.15f else 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(260.dp)
    ) {
        // Outer glowing acoustic wave rings
        if (isActive) {
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .scale(pulseScale)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF25D366).copy(alpha = 0.15f),
                                Color(0xFF25D366).copy(alpha = 0.03f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )
            Box(
                modifier = Modifier
                    .size(210.dp)
                    .scale(pulseScale * 0.95f)
                    .background(
                        Color(0xFF25D366).copy(alpha = 0.1f),
                        shape = CircleShape
                    )
            )
        }

        // Main Center Avatar Circle
        Box(
            modifier = Modifier
                .size(170.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF2A3942), Color(0xFF111B21))
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = peerName.take(1).uppercase().ifBlank { "P" },
                fontSize = 68.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun CallBackgroundPattern() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        // Draw subtle ambient dark radial lighting
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF182229), Color(0xFF0B141B)),
                center = Offset(w / 2, h * 0.35f),
                radius = w * 0.9f
            )
        )
    }
}

@Composable
fun CallMinimizedPill(
    durationSeconds: Int,
    peerName: String,
    isMuted: Boolean,
    onExpand: () -> Unit,
    onEndCall: () -> Unit
) {
    val minutes = durationSeconds / 60
    val seconds = durationSeconds % 60
    val formattedTime = String.format("%02d:%02d", minutes, seconds)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF1F2C34),
            shadowElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF25D366).copy(alpha = 0.6f)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpand() }
                .testTag("call_minimized_pill")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pulsing Green Live Icon
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Color(0xFF25D366), CircleShape)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = peerName.ifBlank { "Call Active" },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "$formattedTime • Tap to return",
                        color = Color(0xFF8696A0),
                        fontSize = 12.sp
                    )
                }

                if (isMuted) {
                    Icon(
                        imageVector = Icons.Default.MicOff,
                        contentDescription = "Muted",
                        tint = Color(0xFFEA4335),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                // Quick Hang up Button
                IconButton(
                    onClick = onEndCall,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFFEA4335), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "Hang Up",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
