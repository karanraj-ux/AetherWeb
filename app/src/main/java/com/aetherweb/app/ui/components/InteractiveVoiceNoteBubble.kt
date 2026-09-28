package com.aetherweb.app.ui.components

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun InteractiveVoiceNoteBubble(
    audioUrl: String,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember { mutableStateOf(false) }
    var playbackProgress by remember { mutableFloatStateOf(0f) }
    var durationMs by remember { mutableIntStateOf(0) }
    var currentPositionMs by remember { mutableIntStateOf(0) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }

    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val localFile = remember(audioUrl) { com.aetherweb.app.MeshStorageManager.findLocalFile(context, audioUrl) }

    // Waveform bar sample heights
    val waveformBars = remember(audioUrl) {
        val seed = kotlin.math.abs(audioUrl.hashCode())
        val random = java.util.Random(seed.toLong())
        List(22) { 6 + random.nextInt(20) }
    }

    DisposableEffect(audioUrl) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    // Polling progress during playback
    LaunchedEffect(isPlaying) {
        while (isActive && isPlaying) {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    currentPositionMs = player.currentPosition
                    val dur = player.duration.coerceAtLeast(1)
                    durationMs = dur
                    playbackProgress = (currentPositionMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                }
            }
            delay(100)
        }
    }

    Surface(
        color = Color.Black.copy(alpha = 0.28f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Play / Pause Circle with Mic Badge
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(WhatsAppGreen)
                            .clickable {
                                if (isPlaying) {
                                    mediaPlayer?.pause()
                                    isPlaying = false
                                } else {
                                    if (mediaPlayer == null) {
                                        try {
                                            val player = MediaPlayer().apply {
                                                if (localFile != null && localFile.exists()) {
                                                    setDataSource(localFile.absolutePath)
                                                } else {
                                                    setDataSource(audioUrl)
                                                }
                                                prepareAsync()
                                                setOnPreparedListener { p ->
                                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                                        p.playbackParams = p.playbackParams.setSpeed(playbackSpeed)
                                                    }
                                                    durationMs = p.duration
                                                    p.start()
                                                    isPlaying = true
                                                }
                                                setOnCompletionListener {
                                                    isPlaying = false
                                                    playbackProgress = 0f
                                                    currentPositionMs = 0
                                                }
                                                setOnErrorListener { p, _, _ ->
                                                    isPlaying = false
                                                    try {
                                                        p.reset()
                                                        p.release()
                                                    } catch (e: Exception) {}
                                                    mediaPlayer = null
                                                    true
                                                }
                                            }
                                            mediaPlayer = player
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    } else {
                                        mediaPlayer?.start()
                                        isPlaying = true
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1F2C34)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "Voice Note",
                            tint = WhatsAppGreen,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Interactive Waveform Scrubbing Bars
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.5.dp)
                    ) {
                        waveformBars.forEachIndexed { index, height ->
                            val barFraction = index.toFloat() / waveformBars.size.toFloat()
                            val isPlayed = barFraction <= playbackProgress
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(height.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (isPlayed) WhatsAppGreen else WhatsAppSubtleText.copy(alpha = 0.5f))
                                    .clickable {
                                        // User scrubbed to this position!
                                        playbackProgress = barFraction
                                        mediaPlayer?.let { player ->
                                            val dur = player.duration
                                            if (dur > 0) {
                                                val targetMs = (dur * barFraction).toInt()
                                                player.seekTo(targetMs)
                                                currentPositionMs = targetMs
                                            }
                                        }
                                    }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val currentSec = currentPositionMs / 1000
                        val totalSec = if (durationMs > 0) durationMs / 1000 else 0
                        val timeString = String.format("%02d:%02d / %02d:%02d", currentSec / 60, currentSec % 60, totalSec / 60, totalSec % 60)
                        
                        Text(
                            text = timeString,
                            style = MaterialTheme.typography.labelSmall,
                            color = WhatsAppSubtleText,
                            fontSize = 11.sp
                        )

                        // 1x / 1.5x / 2x Speed Pill
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1F2C34),
                            modifier = Modifier.clickable {
                                playbackSpeed = when (playbackSpeed) {
                                    1.0f -> 1.5f
                                    1.5f -> 2.0f
                                    else -> 1.0f
                                }
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                    mediaPlayer?.let { player ->
                                        if (player.isPlaying) {
                                            player.playbackParams = player.playbackParams.setSpeed(playbackSpeed)
                                        }
                                    }
                                }
                            }
                        ) {
                            Text(
                                text = "${playbackSpeed}x",
                                color = WhatsAppGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
