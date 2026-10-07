package com.aetherweb.app.protocol

import android.content.Context
import android.os.PowerManager
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.aetherweb.app.*
import com.aetherweb.app.data.ChatMessageEntity
import com.aetherweb.app.data.MeshChatDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Centralized packet dispatcher for all incoming mesh and web socket packets.
 * Replaces duplicated parsing logic and provides unified type-safe execution.
 */
object MeshPacketDispatcher {
    private const val TAG = "MeshPacketDispatcher"
    private val scope = CoroutineScope(Dispatchers.IO)

    fun dispatch(
        packet: MeshPacket,
        senderId: String,
        senderName: String,
        messageId: String,
        timestamp: Long,
        isFromWeb: Boolean,
        context: Context
    ): Boolean {
        val manager = MeshNetworkManager
        val myNodeId = manager.localNodeId

        when (packet) {
            is MeshPacket.Chat -> {
                // Private DM filtering: If recipientId is specified, ensure it is directed to me (or sent by me)
                if (packet.recipientId != null && packet.recipientId != myNodeId && senderId != myNodeId) {
                    return true // Handled (ignored)
                }

                val state = manager._uiState.value

                // Burner room filtering
                val displayMessage = if (state.isBurnerRoomActive && state.burnerRoomId == packet.burnerId) {
                    packet.message
                } else if (state.isBurnerRoomActive && packet.burnerId != state.burnerRoomId) {
                    null // Drop it
                } else if (!state.isBurnerRoomActive && packet.isBurner) {
                    "🔥 [Encrypted Burner Message]"
                } else {
                    packet.message
                }

                if (displayMessage != null) {
                    if (packet.senderHandle.isNotBlank()) {
                        manager._uiState.update { s ->
                            s.copy(knownUserIds = s.knownUserIds + (senderId to packet.senderHandle))
                        }
                    }

                    val resolvedSenderName = when {
                        senderName.isNotBlank() -> senderName
                        packet.senderName.isNotBlank() -> packet.senderName
                        else -> "Peer_${senderId.take(4)}"
                    }

                    val newChatMessage = ChatMessage(
                        id = packet.messageId.ifBlank { messageId.ifBlank { UUID.randomUUID().toString() } },
                        senderName = resolvedSenderName,
                        senderHandle = packet.senderHandle,
                        senderId = senderId,
                        recipientId = packet.recipientId,
                        message = displayMessage,
                        isFromMe = senderId == myNodeId,
                        timestamp = timestamp,
                        isBurner = packet.isBurner,
                        isEmergency = packet.isEmergency
                    )

                    manager._uiState.update { s ->
                        // Dedup: skip if this message is already present. Own broadcasts
                        // loop back through the mesh; without this check every sent
                        // message appears twice. Prefer the packet's messageId.
                        val pid = packet.messageId.ifBlank { messageId }
                        val alreadyPresent = if (pid.isNotBlank()) {
                            s.messages.any { it.id == pid }
                        } else {
                            // Legacy packets without IDs: fuzzy match on content.
                            s.messages.any {
                                it.senderId == senderId &&
                                it.message == displayMessage &&
                                kotlin.math.abs(it.timestamp - timestamp) < 5000
                            }
                        }
                        if (alreadyPresent) s else s.copy(messages = s.messages + newChatMessage)
                    }

                    val isGhostMode = manager._uiState.value.isGhostMode
                    if (!packet.isBurner && !isGhostMode) {
                        scope.launch {
                            try {
                                MeshChatDatabase.getDatabase(context.applicationContext).chatDao().insertMessage(
                                    ChatMessageEntity(
                                        senderId = newChatMessage.senderId,
                                        senderName = newChatMessage.senderName,
                                        message = newChatMessage.message,
                                        timestamp = newChatMessage.timestamp,
                                        isFromMe = newChatMessage.isFromMe
                                    )
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Error saving message to Room", e)
                            }
                        }

                        if (!MainActivity.isAppInForeground) {
                            val isDm = packet.recipientId != null
                            NotificationHelper.showMessageNotification(
                                context = context.applicationContext,
                                senderName = newChatMessage.senderName,
                                message = newChatMessage.message,
                                senderId = newChatMessage.senderId,
                                isGroup = !isDm,
                                groupTitle = if (!isDm) "Mesh Room" else null,
                                isFromWeb = isFromWeb
                            )
                        }
                    }
                }
                return true
            }

            is MeshPacket.CanvasAction -> {
                val state = manager._uiState.value
                if (state.sharedMediaType == "canvas" && state.mediaMode == "broadcast") {
                    if (senderId != state.mediaHostId) {
                        return true
                    }
                }

                val vm = manager._uiState
                val paths = vm.value.canvasPaths.toMutableList()
                val newLasers = vm.value.lasers.toMutableMap()
                var newBg = vm.value.canvasBackground

                when (packet.action) {
                    "laser" -> {
                        newLasers[senderId] = Offset(packet.x, packet.y)
                    }
                    "bg" -> {
                        newBg = packet.data
                    }
                    "clear" -> {
                        paths.clear()
                        newLasers.clear()
                        newBg = ""
                    }
                    "undo" -> {
                        if (paths.isNotEmpty()) paths.removeLast()
                    }
                    "start" -> {
                        val parsedColor = try {
                            Color(android.graphics.Color.parseColor(packet.color))
                        } catch (e: Exception) {
                            Color.Black
                        }
                        paths.add(DrawPath(packet.id, parsedColor, listOf(Offset(packet.x, packet.y)), packet.strokeWidth))
                    }
                    "move", "end" -> {
                        val index = paths.indexOfLast { it.id == packet.id }
                        if (index != -1) {
                            val oldPath = paths[index]
                            val newPoints = if (packet.action == "end") oldPath.points else oldPath.points + Offset(packet.x, packet.y)

                            // Auto snap circle on end
                            if (packet.action == "end" && newPoints.size > 10) {
                                val start = newPoints.first()
                                val end = newPoints.last()
                                val dist = Math.hypot((start.x - end.x).toDouble(), (start.y - end.y).toDouble())
                                if (dist < 0.05) {
                                    var minX = 1f; var maxX = 0f; var minY = 1f; var maxY = 0f
                                    newPoints.forEach { p ->
                                        if (p.x < minX) minX = p.x
                                        if (p.x > maxX) maxX = p.x
                                        if (p.y < minY) minY = p.y
                                        if (p.y > maxY) maxY = p.y
                                    }
                                    val cx = (minX + maxX) / 2f
                                    val cy = (minY + maxY) / 2f
                                    val radius = maxOf(maxX - minX, maxY - minY) / 2f

                                    val circlePoints = mutableListOf<Offset>()
                                    for (i in 0..36) {
                                        val angle = i * 10 * Math.PI / 180.0
                                        circlePoints.add(Offset((cx + radius * Math.cos(angle)).toFloat(), (cy + radius * Math.sin(angle)).toFloat()))
                                    }
                                    paths[index] = oldPath.copy(points = circlePoints)
                                } else {
                                    paths[index] = oldPath.copy(points = newPoints)
                                }
                            } else {
                                paths[index] = oldPath.copy(points = newPoints)
                            }
                        } else {
                            val parsedColor = try {
                                Color(android.graphics.Color.parseColor(packet.color))
                            } catch (e: Exception) {
                                Color.Black
                            }
                            paths.add(DrawPath(packet.id, parsedColor, listOf(Offset(packet.x, packet.y)), packet.strokeWidth))
                        }
                    }
                }
                vm.update { it.copy(canvasPaths = paths, lasers = newLasers, canvasBackground = newBg) }
                return true
            }

            is MeshPacket.CanvasSync -> {
                val newPaths = packet.paths.map { p ->
                    val color = try {
                        Color(android.graphics.Color.parseColor(p.colorHex))
                    } catch (e: Exception) {
                        Color.Black
                    }
                    val points = p.points.map { Offset(it.first, it.second) }
                    DrawPath(p.id, color, points, p.strokeWidth)
                }
                manager._uiState.update { it.copy(canvasPaths = newPaths, canvasBackground = packet.background) }
                return true
            }

            is MeshPacket.MiniGameSync -> {
                when (packet.gameType) {
                    "tictactoe" -> {
                        val state = TicTacToeState.fromJson(packet.stateJson)
                        manager._uiState.update { it.copy(ticTacToeState = state) }
                    }
                    "chess" -> {
                        val state = ChessState.fromJson(packet.stateJson)
                        manager._uiState.update { it.copy(chessState = state) }
                    }
                    "connect4" -> {
                        val state = Connect4State.fromJson(packet.stateJson)
                        manager._uiState.update { it.copy(connect4State = state) }
                    }
                    "ludo" -> {
                        val state = LudoState.fromJson(packet.stateJson)
                        manager._uiState.update { it.copy(ludoState = state) }
                    }
                }
                return true
            }

            is MeshPacket.MeshMusicSync -> {
                MeshMusicManager.handleIncomingSync(packet.toJson())
                return true
            }

            is MeshPacket.SosBeacon -> {
                val beaconAlert = SOSBeaconAlert(
                    senderId = packet.senderId.ifBlank { senderId },
                    senderName = if (packet.senderName.isNotBlank()) packet.senderName else senderName.ifBlank { "Unknown Peer" },
                    senderHandle = packet.senderHandle,
                    message = packet.message,
                    lat = packet.lat,
                    lng = packet.lng,
                    timestamp = packet.timestamp
                )
                manager._uiState.update { it.copy(activeSOSAlert = beaconAlert) }

                try {
                    val pm = context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
                    @Suppress("DEPRECATION")
                    val wl = pm?.newWakeLock(
                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                        "MeshChat:SOSAlarmWakeLock"
                    )
                    wl?.acquire(10000L)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to acquire SOS wake lock", e)
                }

                NotificationHelper.showEmergencySOSNotification(
                    context.applicationContext,
                    beaconAlert.senderName,
                    beaconAlert.senderHandle,
                    beaconAlert.message,
                    beaconAlert.lat,
                    beaconAlert.lng
                )
                return true
            }

            is MeshPacket.SosCancel -> {
                val sId = packet.senderId.ifBlank { senderId }
                manager._uiState.update {
                    if (it.activeSOSAlert?.senderId == sId) it.copy(activeSOSAlert = null) else it
                }
                NotificationHelper.cancelEmergencySOSNotification(context.applicationContext)
                return true
            }

            is MeshPacket.BurnerSync -> {
                val state = manager._uiState.value
                when (packet.action) {
                    "start" -> {
                        if (!state.isBurnerRoomActive) {
                            manager._uiState.update {
                                it.copy(
                                    isBurnerRoomActive = true,
                                    burnerRoomId = packet.burnerId,
                                    burnerRoomCountdown = packet.duration,
                                    activeBurnerKeys = listOf(state.localUserName, packet.initiator)
                                )
                            }
                            val sysMsg = ChatMessage(
                                id = UUID.randomUUID().toString(),
                                senderId = "System",
                                senderName = "System",
                                message = "🔥 You have entered a Burner Room with ${packet.initiator}.\nAll messages will self-destruct in ${packet.duration} seconds.",
                                timestamp = System.currentTimeMillis(),
                                isFromMe = false,
                                isBurner = true
                            )
                            manager._uiState.update { it.copy(messages = it.messages + sysMsg) }
                        }
                    }
                    "leave" -> {
                        if (state.isBurnerRoomActive && state.burnerRoomId == packet.burnerId) {
                            val newKeys = state.activeBurnerKeys.filter { it != packet.user }
                            manager._uiState.update { it.copy(activeBurnerKeys = newKeys) }
                            val sysMsg = ChatMessage(
                                id = UUID.randomUUID().toString(),
                                senderId = "System",
                                senderName = "System",
                                message = "${packet.user} left the burner room.",
                                timestamp = System.currentTimeMillis(),
                                isFromMe = false,
                                isBurner = true
                            )
                            manager._uiState.update { it.copy(messages = it.messages + sysMsg) }
                        }
                    }
                }
                return true
            }

            is MeshPacket.PollStart -> {
                val poll = PollState(packet.id, packet.question, packet.options, emptyMap(), senderId, packet.attachmentUrl)
                manager._uiState.update { it.copy(activePoll = poll) }
                return true
            }

            is MeshPacket.PollVote -> {
                manager._uiState.update { state ->
                    state.activePoll?.let { poll ->
                        if (poll.id == packet.id) {
                            val newVotes = poll.votes.toMutableMap()
                            newVotes[senderId] = packet.optionIndex
                            state.copy(activePoll = poll.copy(votes = newVotes))
                        } else state
                    } ?: state
                }
                return true
            }

            is MeshPacket.PollClose -> {
                manager._uiState.update { it.copy(activePoll = null) }
                return true
            }

            is MeshPacket.Randomizer -> {
                val randState = RandomizerState(packet.id, packet.rType, packet.prompt, packet.player)
                manager._uiState.update { it.copy(activeRandomizer = randState) }
                scope.launch {
                    delay(10000)
                    manager._uiState.update { if (it.activeRandomizer?.id == packet.id) it.copy(activeRandomizer = null) else it }
                }
                return true
            }

            is MeshPacket.SharedMedia -> {
                if (packet.mediaType == "none") {
                    manager._uiState.update { state ->
                        state.copy(sharedMediaType = "none", sharedMediaUrl = "", incomingInvite = null)
                    }
                } else {
                    manager._uiState.update { state ->
                        state.copy(incomingInvite = MediaInvite(packet.mediaType, packet.url, packet.hostId, packet.mode, packet.maxSeats))
                    }
                }
                return true
            }

            is MeshPacket.MediaInvite -> {
                manager._uiState.update { state ->
                    state.copy(incomingInvite = MediaInvite(packet.mediaType, packet.url))
                }
                return true
            }

            is MeshPacket.ClipboardSync -> {
                manager._uiState.update { it.copy(
                    sharedClipboardText = packet.text,
                    sharedClipboardSender = packet.sender,
                    sharedClipboardTimestamp = packet.timestamp
                )}
                manager.webServerManager?.updateClipboard(packet.text, packet.sender)
                return true
            }

            is MeshPacket.FeedReaction -> {
                // Live feed reaction propagation: a peer liked/unliked a reel — apply it locally.
                // MeshRouter dedup guarantees we process each reaction exactly once.
                if (packet.postId.isNotBlank()) {
                    com.aetherweb.app.AetherFeedManager.applyRemoteReaction(packet.postId, packet.liked)
                }
                return true
            }

            is MeshPacket.SysHandshake -> {
                val sysMsg = ChatMessage(
                    senderName = "System",
                    isFromMe = false,
                    message = "High-Speed Mesh Found!\nHost: ${packet.ssid}\nPassword: ${packet.pwd}"
                )
                manager._uiState.update { it.copy(
                    discoveredHostSsid = packet.ssid,
                    discoveredHostPwd = packet.pwd,
                    discoveredHostIp = packet.ip,
                    messages = listOf(sysMsg) + it.messages
                ) }

                val currentState = manager._uiState.value
                if (currentState.autoJoinBeaconEnabled &&
                    !currentState.isHotspotActive &&
                    !currentState.isWifiConnected &&
                    packet.pwd != "PRIVATE"
                ) {
                    DiagnosticLogger.log("BLE Pulse Beacon", "Auto-Join", "Auto-joining mesh room ${packet.ssid} without QR scan", EventStatus.SUCCESS)
                    manager.hotspotManager?.connectToHotspot(packet.ssid, packet.pwd, packet.ip)
                }
                return true
            }

            is MeshPacket.SysIdentity -> {
                val users = manager._uiState.value.knownUsers.toMutableMap()
                val uIds = manager._uiState.value.knownUserIds.toMutableMap()
                users[packet.id] = packet.name
                if (packet.usernameId.isNotEmpty()) uIds[packet.id] = packet.usernameId
                manager._uiState.update { it.copy(knownUsers = users, knownUserIds = uIds) }
                return true
            }

            is MeshPacket.SysStateSync -> {
                val newMessages = packet.messages.map { m ->
                    ChatMessage(
                        id = m.id,
                        senderId = m.senderId,
                        senderName = m.senderName,
                        message = m.message,
                        timestamp = m.timestamp,
                        isFromMe = m.senderId == manager.localNodeId
                    )
                }
                val newUsers = manager._uiState.value.knownUsers.toMutableMap()
                newUsers.putAll(packet.users)
                manager._uiState.update { it.copy(
                    messages = (it.messages + newMessages).distinctBy { msg -> msg.id }.sortedBy { msg -> msg.timestamp },
                    knownUsers = newUsers
                )}
                return true
            }

            is MeshPacket.SysSyncRequest -> {
                val state = manager._uiState.value
                if (state.sharedMediaType != "none" && state.mediaHostId == manager.localNodeId) {
                    manager.meshRouter.routePacket(MeshPacket.MediaInvite(state.sharedMediaType, state.sharedMediaUrl))
                    when (state.sharedMediaType) {
                        "canvas" -> {
                            val dtos = state.canvasPaths.map { p ->
                                val hex = String.format("#%02X%02X%02X", (p.color.red * 255).toInt(), (p.color.green * 255).toInt(), (p.color.blue * 255).toInt())
                                val pts = p.points.map { Pair(it.x, it.y) }
                                CanvasPathDto(p.id, hex, pts, p.strokeWidth)
                            }
                            manager.meshRouter.routePacket(MeshPacket.CanvasSync(dtos, state.canvasBackground))
                        }
                        "chess" -> manager.meshRouter.routePacket(MeshPacket.MiniGameSync("chess", state.chessState.toJson().toString()))
                        "tictactoe" -> manager.meshRouter.routePacket(MeshPacket.MiniGameSync("tictactoe", state.ticTacToeState.toJson()))
                    }
                }
                if (state.activePoll != null && state.activePoll!!.hostId == manager.localNodeId) {
                    val p = state.activePoll!!
                    manager.meshRouter.routePacket(MeshPacket.PollStart(p.id, p.question, p.options, p.attachmentUrl))
                }
                return true
            }

            is MeshPacket.SysKicked, is MeshPacket.SysBanned -> {
                val target = if (packet is MeshPacket.SysKicked) packet.target else (packet as MeshPacket.SysBanned).target
                if (target == myNodeId) {
                    val reason = if (packet is MeshPacket.SysKicked) "kicked" else "banned"
                    manager._uiState.update {
                        it.copy(
                            messages = it.messages + ChatMessage(
                                senderName = "System",
                                senderId = "system",
                                message = "You have been $reason by the room host.",
                                isFromMe = false
                            )
                        )
                    }
                }
                return true
            }

            is MeshPacket.Location -> {
                val locs = manager._uiState.value.userLocations.toMutableMap()
                locs[packet.id] = LocationMessage(id = packet.id, name = packet.name, lat = packet.lat, lng = packet.lng)
                manager._uiState.update { it.copy(userLocations = locs) }
                return true
            }

            is MeshPacket.RadarPing -> {
                val myLoc = manager._uiState.value.userLocations[myNodeId]
                if (myLoc != null) {
                    val replyPacket = MeshPacket.Location(
                        id = myNodeId,
                        name = manager._uiState.value.localUserName,
                        lat = myLoc.lat,
                        lng = myLoc.lng
                    )
                    manager.meshRouter.routePacket(replyPacket)
                    manager.webServerManager?.broadcastMessage(replyPacket.toJsonString(), "Radar")
                }
                return true
            }

            is MeshPacket.CallOffer -> {
                CallManager.handleCallOffer(packet.toJson()) { msg ->
                    manager.meshRouter.routeLocalMessage(msg)
                    manager.webServerManager?.broadcastMessage(msg, myNodeId)
                }
                return true
            }

            is MeshPacket.CallAnswer -> {
                CallManager.handleCallAnswer(packet.toJson())
                return true
            }

            is MeshPacket.CallEnd -> {
                CallManager.handleCallEnd()
                return true
            }

            is MeshPacket.LifelineRequest -> {
                LifelineManager.handleLifelineRequest(context.applicationContext, packet.toJson(), manager.meshRouter)
                return true
            }

            is MeshPacket.LifelineResponse -> {
                if (packet.recipientId == myNodeId) {
                    val preview = if (packet.response.length > 200) packet.response.take(200) + "..." else packet.response
                    val chatMsg = ChatMessage(
                        id = UUID.randomUUID().toString(),
                        senderId = "system",
                        senderName = "Lifeline Bridge",
                        message = "Lifeline Proxy Response (${packet.status}) [ID: ${packet.reqId}]:\n$preview",
                        isFromMe = false
                    )
                    manager._uiState.update { it.copy(messages = it.messages + chatMsg) }
                } else {
                    manager.meshRouter.routePacket(packet)
                    manager.webServerManager?.broadcastMessage(packet.toJsonString(), manager.localNodeId)
                }
                return true
            }

            is MeshPacket.ClusterForward -> {
                if (WifiClusterBridgeManager.hasFastWifiRoute(packet.targetNodeId)) {
                    val fastIp = WifiClusterBridgeManager.getFastRouteIp(packet.targetNodeId)
                    if (fastIp != null) {
                        manager.wifiSocketManager?.broadcastPacket(packet.payload)
                        Log.i(TAG, "Bridge relay forwarded packet to target node ${packet.targetNodeId} at $fastIp")
                    }
                }
                return true
            }

            is MeshPacket.DeliveryAck -> {
                manager._uiState.update { state ->
                    state.copy(
                        messages = state.messages.map { msg ->
                            if (msg.id == packet.originalMsgId) msg.copy(deliveryStatus = DeliveryStatus.DELIVERED) else msg
                        }
                    )
                }
                return true
            }

            is MeshPacket.Raw -> {
                // Not a recognized structured packet; handle as standard chat message
                return false
            }
        }
    }
}
