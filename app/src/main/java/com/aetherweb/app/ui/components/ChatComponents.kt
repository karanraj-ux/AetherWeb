package com.aetherweb.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aetherweb.app.ChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// WhatsApp Palette Constants
val WhatsAppGreen = Color(0xFF25D366)
val WhatsAppTealDark = Color(0xFF075E54)
val WhatsAppOutgoingDark = Color(0xFF005D4B) // WhatsApp dark mode outgoing bubble
val WhatsAppIncomingDark = Color(0xFF1F2C34) // WhatsApp dark mode incoming bubble
val WhatsAppSystemBubble = Color(0xFF182229)
val WhatsAppSubtleText = Color(0xFF8696A0)
val WhatsAppCheckmarkBlue = Color(0xFF53BDEB)
val WhatsAppChatBackgroundDark = Color(0xFF0B141B) // Authentic WhatsApp dark wallpaper background

val SenderPalette = listOf(
    Color(0xFF25D366), // WhatsApp Green
    Color(0xFF53BDEB), // WhatsApp Cyan
    Color(0xFFE542A3), // Pink
    Color(0xFFFF9800), // Orange
    Color(0xFF9C27B0), // Purple
    Color(0xFF00BCD4), // Teal
    Color(0xFFFF5252)  // Coral
)

@Composable
fun WhatsAppDatePill(
    text: String = "Today",
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = WhatsAppSystemBubble.copy(alpha = 0.95f),
            shape = RoundedCornerShape(8.dp),
            shadowElevation = 1.dp
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = WhatsAppSubtleText,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    onUserClick: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onSaveFile: ((String) -> Unit)? = null,
    onSaveImage: ((String) -> Unit)? = null,
    isMentioned: Boolean = false,
    knownUserIds: Map<String, String> = emptyMap(),
    isFirstInThread: Boolean = true,
    isLastInThread: Boolean = true
) {
    val isSystem = message.senderName == "System" || message.senderId == "System"
    val isFromMe = message.isFromMe

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val formattedTime = remember(message.timestamp) {
        if (message.timestamp > 0) timeFormat.format(Date(message.timestamp)) else ""
    }

    if (isSystem) {
        // WhatsApp System Pill
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = WhatsAppSystemBubble.copy(alpha = 0.95f),
                shape = RoundedCornerShape(8.dp),
                shadowElevation = 1.dp
            ) {
                Text(
                    text = message.message,
                    style = MaterialTheme.typography.labelSmall,
                    color = WhatsAppSubtleText,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }
        return
    }

    // WhatsApp Message Bubble
    val align = if (isFromMe) Alignment.End else Alignment.Start
    val isEmergency = message.isEmergency || message.message.startsWith("🚨 [SOS") || message.message.startsWith("[SOS EMERGENCY")
    val bubbleColor = when {
        isEmergency -> Color(0xFF5A1A1A) // High-priority SOS red tint
        isMentioned -> Color(0xFF3B3B18)
        isFromMe -> WhatsAppOutgoingDark
        else -> WhatsAppIncomingDark
    }

    // Authentic WhatsApp threaded asymmetric corner radius
    // When grouped in a thread:
    // For Outgoing: topEnd is sharp (2.dp) only on first message; bottomEnd is sharp (2.dp) on middle messages
    // For Incoming: topStart is sharp (2.dp) only on first message; bottomStart is sharp (2.dp) on middle messages
    val bubbleShape = if (isFromMe) {
        RoundedCornerShape(
            topStart = 14.dp,
            topEnd = if (isFirstInThread) 2.dp else 12.dp,
            bottomStart = 14.dp,
            bottomEnd = if (isLastInThread) 14.dp else 4.dp
        )
    } else {
        RoundedCornerShape(
            topStart = if (isFirstInThread) 2.dp else 12.dp,
            topEnd = 14.dp,
            bottomStart = if (isLastInThread) 14.dp else 4.dp,
            bottomEnd = 14.dp
        )
    }

    val senderColor = remember(message.senderId) {
        SenderPalette[kotlin.math.abs(message.senderId.hashCode()) % SenderPalette.size]
    }

    val senderHandle = remember(message.senderHandle, message.senderId, knownUserIds) {
        if (message.senderHandle.isNotBlank()) message.senderHandle
        else knownUserIds[message.senderId] ?: "@${message.senderName.lowercase().replace("[^a-z0-9_]".toRegex(), "")}"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (isFromMe) 52.dp else 4.dp,
                end = if (isFromMe) 4.dp else 52.dp,
                top = if (isFirstInThread) 4.dp else 1.dp,
                bottom = if (isLastInThread) 4.dp else 1.dp
            ),
        horizontalAlignment = align
    ) {
        Surface(
            color = bubbleColor,
            shape = bubbleShape,
            shadowElevation = 1.dp,
            modifier = Modifier.widthIn(min = 72.dp, max = 340.dp)
        ) {
            Column(
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 4.dp)
            ) {
                // Sender Header for Incoming group chats: only shown on first message in sender thread
                if (!isFromMe && isFirstInThread) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { onUserClick() }
                            .padding(bottom = 2.dp)
                    ) {
                        Text(
                            text = message.senderName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = senderColor
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = senderHandle,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF8696A0),
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.VerifiedUser,
                            contentDescription = "Mesh Verified",
                            modifier = Modifier.size(11.dp),
                            tint = senderColor.copy(alpha = 0.7f)
                        )
                        if (message.recipientId != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF203540)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = WhatsAppCheckmarkBlue, modifier = Modifier.size(9.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("Direct", fontSize = 9.sp, color = WhatsAppCheckmarkBlue, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else if (isFromMe && message.recipientId != null && isFirstInThread) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 2.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF8696A0), modifier = Modifier.size(10.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Direct 1-1 Message", fontSize = 10.sp, color = Color(0xFF8696A0), fontWeight = FontWeight.SemiBold)
                    }
                }

                val urlRegex = "(http|https)://[^\\s]+".toRegex()
                val match = urlRegex.find(message.message)
                val textWithoutUrl = if (match != null && message.message.startsWith("Shared a file: ")) {
                    ""
                } else {
                    message.message
                }

                // Main Message Content / Game Challenge Cards
                val isGameInvite = textWithoutUrl.startsWith("🎮 [GAME CHALLENGE]:")
                if (isGameInvite) {
                    val gameType = when {
                        textWithoutUrl.contains("Connect 4", ignoreCase = true) -> "connect4"
                        textWithoutUrl.contains("Tic-Tac-Toe", ignoreCase = true) -> "tictactoe"
                        textWithoutUrl.contains("Chess", ignoreCase = true) -> "chess"
                        else -> "tictactoe"
                    }
                    val gameTitle = when (gameType) {
                        "connect4" -> "Connect 4 Match"
                        "chess" -> "Chess Showdown"
                        else -> "Tic-Tac-Toe Duel"
                    }
                    val gameIcon = when (gameType) {
                        "connect4" -> "🔴"
                        "chess" -> "♟️"
                        else -> "❌"
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1F2C34),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(gameIcon, fontSize = 24.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        gameTitle,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        "Mesh Peer-to-Peer Match",
                                        color = WhatsAppSubtleText,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { onOpenUrl("meshgame://$gameType") },
                                colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (isFromMe) "Resume / Watch Game" else "Accept Challenge & Play",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                } else if (textWithoutUrl.isNotEmpty()) {
                    SelectionContainer {
                        Text(
                            text = textWithoutUrl,
                            color = Color(0xFFE9EDEF),
                            fontSize = 15.sp,
                            lineHeight = 20.sp
                        )
                    }
                }

                // Embedded Media / Voice Note / File Handling
                if (match != null) {
                    val url = match.value
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val localFile = remember(url) { com.aetherweb.app.MeshStorageManager.findLocalFile(context, url) }
                    
                    val isImage = remember(url) { com.aetherweb.app.MeshStorageManager.isImageFile(url) }
                    val isAudio = remember(url) { com.aetherweb.app.MeshStorageManager.isAudioFile(url) }

                    Spacer(modifier = Modifier.height(6.dp))
                    if (isImage) {
                        // Direct local file resolution: prioritize local disk file to eliminate blank boxes
                        val imageModel: Any = localFile ?: url
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF101920))
                        ) {
                            AsyncImage(
                                model = imageModel,
                                contentDescription = "Shared Image",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 120.dp, max = 260.dp)
                                    .clickable { onOpenUrl(url) },
                                contentScale = ContentScale.Crop
                            )
                            
                            // Top-right download/save to gallery action button
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.65f),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .clickable {
                                        onSaveImage?.invoke(url) ?: onOpenUrl(url)
                                    }
                            ) {
                                Icon(
                                    Icons.Default.ArrowDownward,
                                    contentDescription = "Save to Gallery",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .padding(6.dp)
                                        .size(16.dp)
                                )
                            }
                        }
                    } else if (isAudio) {
                        InteractiveVoiceNoteBubble(
                            audioUrl = url,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        // Rich WhatsApp-style File / Document card
                        val fileName = remember(url) { com.aetherweb.app.MeshStorageManager.sanitizeFileName(url) }
                        val ext = remember(fileName) { com.aetherweb.app.MeshStorageManager.getFileExtension(fileName).uppercase() }
                        val mimeType = remember(fileName) { com.aetherweb.app.MeshStorageManager.getMimeType(fileName) }

                        val isPdf = ext == "PDF" || mimeType.contains("pdf")
                        val isApk = ext == "APK" || mimeType.contains("package-archive")
                        val isZip = ext in listOf("ZIP", "RAR", "7Z", "TAR", "GZ")

                        val fileTypeColor = when {
                            isPdf -> Color(0xFFEF5350) // Red
                            isApk -> Color(0xFF26A69A) // Teal
                            isZip -> Color(0xFF42A5F5) // Blue
                            else -> WhatsAppGreen
                        }

                        val fileSizeDisplay = remember(localFile, message.message) {
                            if (localFile != null && localFile.length() > 0) {
                                com.aetherweb.app.MeshStorageManager.formatFileSize(localFile.length())
                            } else {
                                val sizeRegex = "\\(([0-9.]+\\s*(?:MB|KB|GB|B))\\)".toRegex(RegexOption.IGNORE_CASE)
                                sizeRegex.find(message.message)?.groupValues?.get(1) ?: (if (ext.isNotEmpty()) ext else "FILE")
                            }
                        }

                        Surface(
                            color = Color(0xFF141E24),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenUrl(url) }
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = fileTypeColor.copy(alpha = 0.2f),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.InsertDriveFile,
                                            contentDescription = ext,
                                            tint = fileTypeColor,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = fileName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFFE9EDEF),
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (ext.isNotEmpty()) {
                                            Surface(
                                                shape = RoundedCornerShape(3.dp),
                                                color = fileTypeColor.copy(alpha = 0.25f)
                                            ) {
                                                Text(
                                                    text = ext,
                                                    color = fileTypeColor,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        Text(
                                            text = fileSizeDisplay,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = WhatsAppSubtleText,
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                IconButton(
                                    onClick = {
                                        onSaveFile?.invoke(url) ?: onOpenUrl(url)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ArrowDownward,
                                        contentDescription = "Save to Downloads",
                                        tint = WhatsAppGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // WhatsApp-style timestamp and delivery ticks at bottom-right
                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (message.isBurner) {
                        Text("🔥 ", fontSize = 10.sp)
                    }
                    Text(
                        text = formattedTime,
                        fontSize = 11.sp,
                        color = WhatsAppSubtleText
                    )
                    if (isFromMe) {
                        Spacer(modifier = Modifier.width(4.dp))
                        when (message.deliveryStatus) {
                            com.aetherweb.app.DeliveryStatus.PENDING -> {
                                Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = "Sending...",
                                    modifier = Modifier.size(13.dp),
                                    tint = WhatsAppSubtleText
                                )
                            }
                            com.aetherweb.app.DeliveryStatus.SENT -> {
                                Icon(
                                    Icons.Default.Done,
                                    contentDescription = "Sent",
                                    modifier = Modifier.size(14.dp),
                                    tint = WhatsAppSubtleText
                                )
                            }
                            com.aetherweb.app.DeliveryStatus.DELIVERED -> {
                                Icon(
                                    Icons.Default.DoneAll,
                                    contentDescription = "Delivered",
                                    modifier = Modifier.size(15.dp),
                                    tint = WhatsAppCheckmarkBlue
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatInput(
    text: String,
    onTextChange: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    onFilePick: () -> Unit,
    onCameraClick: () -> Unit = {},
    onMicClick: () -> Unit,
    onCancelRecording: () -> Unit = onMicClick,
    isRecording: Boolean = false,
    modifier: Modifier = Modifier
) {
    var recordTimerSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordTimerSeconds = 0
            while (isRecording) {
                kotlinx.coroutines.delay(1000)
                recordTimerSeconds++
            }
        } else {
            recordTimerSeconds = 0
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isRecording) {
            // WhatsApp Recording Bar
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF1F2C34),
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "RecBlink")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 0.2f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "RecDot"
                    )

                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color.Red.copy(alpha = alpha))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = String.format("%02d:%02d", recordTimerSeconds / 60, recordTimerSeconds % 60),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    TextButton(onClick = onCancelRecording) {
                        Text("Cancel", color = Color(0xFFEF5350), fontSize = 13.sp)
                    }
                }
            }
        } else {
            // WhatsApp Pill Input Box
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF1F2C34),
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // WhatsApp Emoji Icon
                    IconButton(
                        onClick = { /* Emoji picker or focus */ },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.SentimentSatisfiedAlt,
                            contentDescription = "Emoji",
                            tint = WhatsAppSubtleText
                        )
                    }

                    // Text field
                    TextField(
                        value = text,
                        onValueChange = onTextChange,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("message_input"),
                        placeholder = {
                            Text(
                                "Message",
                                color = WhatsAppSubtleText,
                                fontSize = 16.sp
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = Color(0xFFE9EDEF),
                            unfocusedTextColor = Color(0xFFE9EDEF),
                            cursorColor = WhatsAppGreen
                        ),
                        maxLines = 5
                    )

                    // Attach file icon
                    IconButton(
                        onClick = onFilePick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.AttachFile,
                            contentDescription = "Attach File",
                            tint = WhatsAppSubtleText
                        )
                    }

                    // Camera icon (WhatsApp style)
                    IconButton(
                        onClick = onCameraClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = "Camera",
                            tint = WhatsAppSubtleText
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // WhatsApp Circular Action Button (Send / Mic)
        val isTyping = text.isNotBlank()
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(WhatsAppGreen)
                .clickable {
                    if (isTyping) {
                        onSendMessage(text)
                    } else {
                        onMicClick()
                    }
                }
                .testTag("send_button"),
            contentAlignment = Alignment.Center
        ) {
            if (isTyping) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send Message",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            } else if (isRecording) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send Voice Note",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = "Record Voice Note",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
