package com.aetherweb.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

object NotificationHelper {
    const val CALL_CHANNEL_ID = "channel_incoming_calls_v3"
    const val MESSAGE_CHANNEL_ID = "channel_mesh_direct_messages_v3"
    const val GROUP_CHANNEL_ID = "channel_mesh_group_messages_v3"
    const val EMERGENCY_CHANNEL_ID = "channel_emergency_sos_v3"

    const val CALL_NOTIFICATION_ID = 2001
    const val EMERGENCY_NOTIFICATION_ID = 9999
    const val SUMMARY_NOTIFICATION_ID = 2999
    const val GROUP_KEY_MESSAGES = "com.aetherweb.app.GROUP_KEY_MESSAGES"

    const val KEY_TEXT_REPLY = "key_text_reply"
    const val EXTRA_SENDER_NAME = "extra_sender_name"
    const val EXTRA_SENDER_ID = "extra_sender_id"
    const val EXTRA_CONVERSATION_KEY = "extra_conversation_key"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    const val EXTRA_IS_GROUP = "extra_is_group"

    const val ACTION_REPLY_MESSAGE = "com.aetherweb.app.ACTION_REPLY_MESSAGE"
    const val ACTION_MARK_READ = "com.aetherweb.app.ACTION_MARK_READ"
    const val ACTION_MUTE_CONVERSATION = "com.aetherweb.app.ACTION_MUTE_CONVERSATION"
    const val ACTION_DECLINE_CALL = "com.aetherweb.app.ACTION_DECLINE_CALL"

    // In-memory conversation message history for WhatsApp-grade MessagingStyle threading
    private val conversationThreads = ConcurrentHashMap<String, MutableList<NotificationCompat.MessagingStyle.Message>>()
    // Muted conversations: conversationKey -> expiry timestamp
    private val mutedConversations = ConcurrentHashMap<String, Long>()

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 1. WhatsApp-Grade Urgent Call Channel
            val callChannel = NotificationChannel(
                CALL_CHANNEL_ID,
                "WhatsApp Mesh Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent incoming voice & video calls over local mesh"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 800, 1000, 800)
                lightColor = 0xFF25D366.toInt()
                enableLights(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }

            // 2. WhatsApp-Grade Direct Message Channel
            val messageChannel = NotificationChannel(
                MESSAGE_CHANNEL_ID,
                "Direct Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High priority direct chats and private messages (WhatsApp-style)"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 150, 100, 150) // WhatsApp signature double buzz
                lightColor = 0xFF25D366.toInt()
                enableLights(true)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }

            // 3. Group & Mesh Room Channel
            val groupChannel = NotificationChannel(
                GROUP_CHANNEL_ID,
                "Mesh Rooms & Groups",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Group messages, public room chat, and browser guest notices"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 100, 80, 100)
                lightColor = 0xFF53BDEB.toInt()
                enableLights(true)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }

            // 4. Life-Safety Emergency SOS Alarm Channel (Bypasses DND, High Importance)
            val emergencyChannel = NotificationChannel(
                EMERGENCY_CHANNEL_ID,
                "Life-Safety Emergency SOS",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical off-grid distress beacons and rescue alerts"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 200, 800, 200, 1000, 300, 1000)
                lightColor = Color.RED
                enableLights(true)
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }

            notificationManager.createNotificationChannel(callChannel)
            notificationManager.createNotificationChannel(messageChannel)
            notificationManager.createNotificationChannel(groupChannel)
            notificationManager.createNotificationChannel(emergencyChannel)
        }
    }

    fun isConversationMuted(key: String): Boolean {
        val exp = mutedConversations[key] ?: return false
        return if (System.currentTimeMillis() < exp) {
            true
        } else {
            mutedConversations.remove(key)
            false
        }
    }

    fun muteConversation(key: String, durationMs: Long = 8 * 3600 * 1000L) {
        mutedConversations[key] = System.currentTimeMillis() + durationMs
    }

    fun clearConversation(key: String) {
        conversationThreads.remove(key)
    }

    /**
     * Parse human readable preview from structured JSON messages (voice, photo, file, call, canvas)
     */
    fun formatMessagePreview(rawMessage: String): String {
        val trimmed = rawMessage.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                val obj = JSONObject(trimmed)
                val type = obj.optString("type", "")
                when (type) {
                    "audio", "voice" -> {
                        val duration = obj.optInt("duration", 0)
                        return if (duration > 0) "🎤 Voice message (${duration}s)" else "🎤 Voice message"
                    }
                    "photo", "image", "shared_media" -> {
                        val cap = obj.optString("caption", "")
                        return if (cap.isNotBlank()) "📷 Photo: $cap" else "📷 Photo"
                    }
                    "file" -> {
                        val fileName = obj.optString("fileName", obj.optString("name", "Document"))
                        return "📎 Document: $fileName"
                    }
                    "call" -> return "📞 Call invite"
                    "game", "game_move" -> return "🎮 TicTacToe invite"
                    "canvas_stroke", "canvas_clear" -> return "🎨 Shared Live Canvas"
                    "sos_beacon" -> return "🚨 EMERGENCY SOS DISTRESS BEACON"
                    "chat" -> return obj.optString("message", rawMessage)
                }
            } catch (e: Exception) {}
        }
        return rawMessage
    }

    /**
     * WhatsApp-Grade Messaging Notification with:
     * - MessagingStyle conversation threading
     * - Direct in-line reply via RemoteInput (type and send right from notification)
     * - Quick "Mark as Read" action
     * - Quick "Mute 8h" action
     * - WhatsApp signature vibration and green accent
     * - WakeLock pulse for lockscreen delivery
     * - Auto Group Summary
     */
    fun showMessageNotification(
        context: Context,
        senderName: String,
        message: String,
        senderId: String = "",
        isGroup: Boolean = false,
        groupTitle: String? = null,
        isFromWeb: Boolean = false
    ) {
        val conversationKey = if (isGroup && !groupTitle.isNullOrBlank()) {
            "group_$groupTitle"
        } else if (senderId.isNotBlank()) {
            "dm_$senderId"
        } else {
            "dm_$senderName"
        }

        if (isConversationMuted(conversationKey)) {
            return
        }

        createNotificationChannels(context)
        pulseWakeLock(context)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Stable notification ID per conversation thread
        val notifId = (conversationKey.hashCode() and 0x7FFFFFFF) % 100000 + 3000

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("OPEN_CONVERSATION", conversationKey)
            putExtra("SENDER_NAME", senderName)
            putExtra("SENDER_ID", senderId)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 1. Direct In-Line Reply RemoteInput (Type in notification shade without opening app)
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel("Reply to $senderName...")
            .build()

        val replyIntent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_REPLY_MESSAGE
            putExtra(EXTRA_SENDER_NAME, senderName)
            putExtra(EXTRA_SENDER_ID, senderId)
            putExtra(EXTRA_CONVERSATION_KEY, conversationKey)
            putExtra(EXTRA_IS_GROUP, isGroup)
            putExtra(EXTRA_NOTIFICATION_ID, notifId)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            notifId + 1000,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            "Reply",
            replyPendingIntent
        ).addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()

        // 2. Mark as Read action
        val markReadIntent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_MARK_READ
            putExtra(EXTRA_CONVERSATION_KEY, conversationKey)
            putExtra(EXTRA_NOTIFICATION_ID, notifId)
        }
        val markReadPendingIntent = PendingIntent.getBroadcast(
            context,
            notifId + 2000,
            markReadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val markReadAction = NotificationCompat.Action.Builder(
            android.R.drawable.checkbox_on_background,
            "Mark as Read",
            markReadPendingIntent
        ).build()

        // 3. Mute 8 Hours action
        val muteIntent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_MUTE_CONVERSATION
            putExtra(EXTRA_CONVERSATION_KEY, conversationKey)
            putExtra(EXTRA_SENDER_NAME, senderName)
            putExtra(EXTRA_NOTIFICATION_ID, notifId)
        }
        val mutePendingIntent = PendingIntent.getBroadcast(
            context,
            notifId + 3000,
            muteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val muteAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_lock_silent_mode,
            "Mute 8h",
            mutePendingIntent
        ).build()

        // 4. WhatsApp MessagingStyle Thread Construction
        val formattedMsg = formatMessagePreview(message)
        val now = System.currentTimeMillis()

        val userPerson = Person.Builder()
            .setName("You")
            .setKey("local_me")
            .build()

        val senderDisplayName = if (isFromWeb) "🌐 $senderName (Web)" else senderName
        val senderPerson = Person.Builder()
            .setName(senderDisplayName)
            .setKey(if (senderId.isNotBlank()) senderId else senderName)
            .build()

        val threadList = conversationThreads.getOrPut(conversationKey) { mutableListOf() }
        val newMessage = NotificationCompat.MessagingStyle.Message(formattedMsg, now, senderPerson)
        synchronized(threadList) {
            threadList.add(newMessage)
            if (threadList.size > 12) {
                threadList.removeAt(0)
            }
        }

        val messagingStyle = NotificationCompat.MessagingStyle(userPerson)
        if (isGroup) {
            messagingStyle.isGroupConversation = true
            messagingStyle.conversationTitle = groupTitle ?: "Mesh Room"
        }
        synchronized(threadList) {
            for (m in threadList) {
                messagingStyle.addMessage(m)
            }
        }

        val channelId = if (isGroup) GROUP_CHANNEL_ID else MESSAGE_CHANNEL_ID

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setColor(0xFF25D366.toInt()) // WhatsApp Emerald Green
            .setContentTitle(if (isGroup) groupTitle ?: "Mesh Room" else senderDisplayName)
            .setContentText(formattedMsg)
            .setStyle(messagingStyle)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY_MESSAGES)
            .setContentIntent(openPendingIntent)
            .addAction(replyAction)
            .addAction(markReadAction)
            .addAction(muteAction)
            .build()

        notificationManager.notify(notifId, notification)

        // Show/Update Group Summary Notification for bundled drawer grouping
        showGroupSummaryNotification(context, notificationManager)
    }

    /**
     * Updates notification after user transmits a direct reply from the notification shade
     */
    fun updateNotificationWithReply(
        context: Context,
        conversationKey: String,
        replyText: String,
        notifId: Int
    ) {
        val threadList = conversationThreads[conversationKey] ?: return
        val userPerson = Person.Builder()
            .setName("You")
            .setKey("local_me")
            .build()

        val replyMessage = NotificationCompat.MessagingStyle.Message(replyText, System.currentTimeMillis(), userPerson)
        synchronized(threadList) {
            threadList.add(replyMessage)
            if (threadList.size > 12) {
                threadList.removeAt(0)
            }
        }

        val messagingStyle = NotificationCompat.MessagingStyle(userPerson)
        synchronized(threadList) {
            for (m in threadList) {
                messagingStyle.addMessage(m)
            }
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val updatedNotification = NotificationCompat.Builder(context, MESSAGE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setColor(0xFF25D366.toInt())
            .setStyle(messagingStyle)
            .setPriority(NotificationCompat.PRIORITY_LOW) // Already replied by user
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY_MESSAGES)
            .setContentIntent(openPendingIntent)
            .build()

        notificationManager.notify(notifId, updatedNotification)
    }

    private fun showGroupSummaryNotification(context: Context, notificationManager: NotificationManager) {
        val summaryIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val summaryPendingIntent = PendingIntent.getActivity(
            context,
            SUMMARY_NOTIFICATION_ID,
            summaryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val summaryNotification = NotificationCompat.Builder(context, MESSAGE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setColor(0xFF25D366.toInt())
            .setContentTitle("Nexus Mesh Chat")
            .setContentText("New messages received")
            .setGroup(GROUP_KEY_MESSAGES)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setContentIntent(summaryPendingIntent)
            .build()

        notificationManager.notify(SUMMARY_NOTIFICATION_ID, summaryNotification)
    }

    fun showIncomingCallNotification(context: Context, callerName: String, callerId: String) {
        createNotificationChannels(context)
        pulseWakeLock(context)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Intent to open full call screen
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("CALL_ACTION", "OPEN")
            putExtra("CALLER_ID", callerId)
            putExtra("CALLER_NAME", callerName)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Decline Intent
        val declineIntent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_DECLINE_CALL
            putExtra(EXTRA_NOTIFICATION_ID, CALL_NOTIFICATION_ID)
        }
        val declinePendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CALL_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentTitle("WhatsApp Voice & Video Call")
            .setContentText("$callerName is calling via local mesh...")
            .setColor(0xFF25D366.toInt())
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .setFullScreenIntent(openPendingIntent, true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", declinePendingIntent)
            .addAction(android.R.drawable.stat_sys_phone_call, "Answer", openPendingIntent)
            .build()

        notificationManager.notify(CALL_NOTIFICATION_ID, notification)
    }

    fun cancelCallNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(CALL_NOTIFICATION_ID)
    }

    fun showEmergencySOSNotification(
        context: Context,
        senderName: String,
        senderHandle: String,
        message: String,
        lat: Double?,
        lng: Double?
    ) {
        createNotificationChannels(context)
        pulseWakeLock(context, durationMs = 15000L)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val fullScreenIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("OPEN_SOS_ALERT", true)
            putExtra("EXTRA_SENDER_NAME", senderName)
            putExtra("EXTRA_SENDER_HANDLE", senderHandle)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            EMERGENCY_NOTIFICATION_ID,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val locationInfo = if (lat != null && lng != null && lat != 0.0 && lng != 0.0) {
            "\n📍 GPS: %.4f, %.4f".format(lat, lng)
        } else ""

        val bigText = "🚨 DISTRESS BEACON ACTIVE from $senderName (@$senderHandle)\n$message$locationInfo\nImmediate assistance requested!"

        val notification = NotificationCompat.Builder(context, EMERGENCY_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setColor(Color.RED)
            .setContentTitle("🚨 EMERGENCY SOS: $senderName")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setVibrate(longArrayOf(0, 800, 200, 800, 200, 1000, 300, 1000))
            .build()

        notificationManager.notify(EMERGENCY_NOTIFICATION_ID, notification)
    }

    fun cancelEmergencySOSNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(EMERGENCY_NOTIFICATION_ID)
    }

    fun dismissNotification(context: Context, notifId: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notifId)
    }

    private fun pulseWakeLock(context: Context, durationMs: Long = 5000L) {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val wl = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MeshChat::WakeNotification")
            wl?.acquire(durationMs)
        } catch (e: Exception) {}
    }
}
