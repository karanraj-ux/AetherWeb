package com.aetherweb.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.RemoteInput
import com.aetherweb.app.data.ChatMessageEntity
import com.aetherweb.app.data.MeshChatDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notifId = intent.getIntExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, -1)
        val conversationKey = intent.getStringExtra(NotificationHelper.EXTRA_CONVERSATION_KEY) ?: ""
        val senderName = intent.getStringExtra(NotificationHelper.EXTRA_SENDER_NAME) ?: "Chat"
        val senderId = intent.getStringExtra(NotificationHelper.EXTRA_SENDER_ID) ?: ""
        val isGroup = intent.getBooleanExtra(NotificationHelper.EXTRA_IS_GROUP, false)

        when (intent.action) {
            NotificationHelper.ACTION_MARK_READ -> {
                if (conversationKey.isNotBlank()) {
                    NotificationHelper.clearConversation(conversationKey)
                }
                if (notifId != -1) {
                    NotificationHelper.dismissNotification(context, notifId)
                }
            }
            NotificationHelper.ACTION_MUTE_CONVERSATION -> {
                if (conversationKey.isNotBlank()) {
                    NotificationHelper.muteConversation(conversationKey)
                    NotificationHelper.clearConversation(conversationKey)
                }
                if (notifId != -1) {
                    NotificationHelper.dismissNotification(context, notifId)
                }
                Toast.makeText(context, "Muted $senderName for 8 hours", Toast.LENGTH_SHORT).show()
            }
            NotificationHelper.ACTION_DECLINE_CALL -> {
                if (notifId != -1) {
                    NotificationHelper.cancelCallNotification(context)
                }
                CallManager.declineCall { payload ->
                    MeshNetworkManager.meshRouter.routeLocalMessage(payload)
                    MeshNetworkManager.webServerManager?.broadcastMessage(payload, MeshNetworkManager.localNodeId)
                }
            }
            NotificationHelper.ACTION_REPLY_MESSAGE -> {
                val remoteInput = RemoteInput.getResultsFromIntent(intent)
                val replyText = remoteInput?.getCharSequence(NotificationHelper.KEY_TEXT_REPLY)?.toString()
                if (!replyText.isNullOrBlank()) {
                    val myUserName = MeshNetworkManager.uiState.value.localUserName
                    val myUserId = MeshNetworkManager.localNodeId
                    val timestamp = System.currentTimeMillis()

                    val chatPacket = com.aetherweb.app.protocol.MeshPacket.Chat(
                        message = replyText,
                        senderName = myUserName,
                        recipientId = if (!isGroup && senderId.isNotBlank()) senderId else null,
                        timestamp = timestamp
                    )
                    val jsonPayload = chatPacket.toJsonString()

                    // 1. Route over offline mesh (Wi-Fi sockets + BLE)
                    MeshNetworkManager.meshRouter.routePacket(chatPacket)

                    // 2. Broadcast to browser WebChat guests if server running
                    MeshNetworkManager.webServerManager?.broadcastMessage(replyText, myUserName)

                    // 3. Relay through Internet Relay if available and targeting a remote mailbox
                    if (!isGroup && senderId.isNotBlank()) {
                        InternetRelayManager.sendRemoteEnvelope(
                            targetPublicKeyOrMailbox = senderId,
                            payloadJson = replyText,
                            recipientName = senderName,
                            isEmergency = false
                        )
                    }

                    // 4. Persist to local Room database
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            MeshChatDatabase.getDatabase(context.applicationContext).chatDao().insertMessage(
                                ChatMessageEntity(
                                    senderId = myUserId,
                                    senderName = myUserName,
                                    message = replyText,
                                    timestamp = timestamp,
                                    isFromMe = true
                                )
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    // 5. Update the notification with in-line reply (WhatsApp behavior)
                    if (conversationKey.isNotBlank() && notifId != -1) {
                        NotificationHelper.updateNotificationWithReply(
                            context = context,
                            conversationKey = conversationKey,
                            replyText = replyText,
                            notifId = notifId
                        )
                    } else if (notifId != -1) {
                        NotificationHelper.dismissNotification(context, notifId)
                    }
                }
            }
        }
    }
}
