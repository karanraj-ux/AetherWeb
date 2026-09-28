package com.aetherweb.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val senderId: String,
    val senderName: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isFromMe: Boolean,
    val messageType: String = "text",
    val fileUrl: String? = null,
    val isEncrypted: Boolean = false
)

@Entity(tableName = "call_logs")
data class CallLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val peerId: String,
    val peerName: String,
    val callType: String, // "INCOMING", "OUTGOING", "MISSED"
    val timestamp: Long = System.currentTimeMillis(),
    val durationSeconds: Int = 0,
    val isVideo: Boolean = false
)
