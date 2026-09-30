package com.aetherweb.app.protocol

import android.util.Log
import org.json.JSONObject

/**
 * Resilient, zero-crash codec for encoding and decoding MeshPackets.
 * Automatically recovers from missing/optional fields, preserves legacy compatibility,
 * and falls back to MeshPacket.Raw for unstructured text.
 */
object MeshPacketCodec {
    private const val TAG = "MeshPacketCodec"

    fun encode(packet: MeshPacket): String {
        return packet.toJsonString()
    }

    fun decode(raw: String): MeshPacket {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return MeshPacket.Raw(raw)
        }

        return try {
            val json = JSONObject(trimmed)
            val type = json.optString("type", "")

            when (type) {
                MeshPacket.TYPE_CHAT -> {
                    val message = json.optString("message", "")
                    val senderName = json.optString("senderName", json.optString("sender", ""))
                    val senderHandle = json.optString("senderHandle", "")
                    val recipientId = if (json.has("recipientId") && !json.isNull("recipientId")) json.getString("recipientId") else null
                    val isBurner = json.optBoolean("isBurner", false)
                    val burnerId = json.optString("burnerId", "")
                    val isEmergency = json.optBoolean("isEmergency", false)
                    val timestamp = json.optLong("timestamp", System.currentTimeMillis())

                    MeshPacket.Chat(
                        message = message,
                        senderName = senderName,
                        senderHandle = senderHandle,
                        recipientId = recipientId,
                        isBurner = isBurner,
                        burnerId = burnerId,
                        isEmergency = isEmergency,
                        timestamp = timestamp
                    )
                }

                MeshPacket.TYPE_CANVAS -> {
                    val action = json.optString("action", "move")
                    val id = json.optString("id", "")
                    val color = json.optString("color", "#000000")
                    val x = json.optDouble("x", 0.0).toFloat()
                    val y = json.optDouble("y", 0.0).toFloat()
                    val strokeWidth = json.optDouble("strokeWidth", 10.0).toFloat()
                    val data = json.optString("data", "")

                    MeshPacket.CanvasAction(
                        action = action,
                        id = id,
                        color = color,
                        x = x,
                        y = y,
                        strokeWidth = strokeWidth,
                        data = data
                    )
                }

                MeshPacket.TYPE_CANVAS_SYNC -> {
                    val pathsArr = json.optJSONArray("paths")
                    val paths = mutableListOf<CanvasPathDto>()
                    if (pathsArr != null) {
                        for (i in 0 until pathsArr.length()) {
                            val pObj = pathsArr.optJSONObject(i) ?: continue
                            val cId = pObj.optString("id", "")
                            val colorStr = pObj.optString("color", "#000000")
                            val sw = pObj.optDouble("strokeWidth", 10.0).toFloat()
                            val ptsArr = pObj.optJSONArray("points")
                            val pts = mutableListOf<Pair<Float, Float>>()
                            if (ptsArr != null) {
                                for (j in 0 until ptsArr.length()) {
                                    val ptObj = ptsArr.optJSONObject(j) ?: continue
                                    pts.add(Pair(ptObj.optDouble("x", 0.0).toFloat(), ptObj.optDouble("y", 0.0).toFloat()))
                                }
                            }
                            paths.add(CanvasPathDto(cId, colorStr, pts, sw))
                        }
                    }
                    val bg = json.optString("bg", "")
                    MeshPacket.CanvasSync(paths, bg)
                }

                "tictactoe", "connect4", "chess", "ludo" -> {
                    val stateJson = if (json.has("state")) json.get("state").toString() else "{}"
                    MeshPacket.MiniGameSync(gameType = type, stateJson = stateJson)
                }

                MeshPacket.TYPE_MESH_MUSIC_SYNC -> {
                    MeshPacket.MeshMusicSync(
                        action = json.optString("action", "play"),
                        trackTitle = json.optString("trackTitle", ""),
                        artist = json.optString("artist", ""),
                        uri = json.optString("uri", ""),
                        isStream = json.optBoolean("isStream", false),
                        positionMs = json.optLong("positionMs", 0L),
                        hostName = json.optString("hostName", "DJ"),
                        timestamp = json.optLong("timestamp", System.currentTimeMillis())
                    )
                }

                MeshPacket.TYPE_SOS_BEACON -> {
                    val lat = if (json.has("lat") && !json.isNull("lat")) json.optDouble("lat") else null
                    val lng = if (json.has("lng") && !json.isNull("lng")) json.optDouble("lng") else null
                    MeshPacket.SosBeacon(
                        senderId = json.optString("senderId", ""),
                        senderName = json.optString("senderName", "Unknown Peer"),
                        senderHandle = json.optString("senderHandle", ""),
                        message = json.optString("message", "Immediate Assistance Required!"),
                        lat = lat,
                        lng = lng,
                        timestamp = json.optLong("timestamp", System.currentTimeMillis())
                    )
                }

                MeshPacket.TYPE_SOS_CANCEL -> {
                    MeshPacket.SosCancel(senderId = json.optString("senderId", ""))
                }

                MeshPacket.TYPE_BURNER_SYNC -> {
                    MeshPacket.BurnerSync(
                        action = json.optString("action", "start"),
                        burnerId = json.optString("burnerId", ""),
                        duration = json.optInt("duration", 300),
                        initiator = json.optString("initiator", "Someone"),
                        user = json.optString("user", "")
                    )
                }

                MeshPacket.TYPE_POLL_START -> {
                    val optionsRaw = json.optString("options", "")
                    val options = if (optionsRaw.isNotBlank()) optionsRaw.split("|") else emptyList()
                    MeshPacket.PollStart(
                        id = json.optString("id", ""),
                        question = json.optString("q", ""),
                        options = options,
                        attachmentUrl = json.optString("attachmentUrl", "")
                    )
                }

                "poll" -> { // Alternate poll representation used in sys sync
                    val q = json.optString("q", "")
                    val pId = json.optString("id", "")
                    val optsArr = json.optJSONArray("opts")
                    val opts = mutableListOf<String>()
                    if (optsArr != null) {
                        for (i in 0 until optsArr.length()) {
                            opts.add(optsArr.getString(i))
                        }
                    }
                    val url = json.optString("url", "")
                    MeshPacket.PollStart(id = pId, question = q, options = opts, attachmentUrl = url)
                }

                MeshPacket.TYPE_POLL_VOTE -> {
                    MeshPacket.PollVote(
                        id = json.optString("id", ""),
                        optionIndex = json.optInt("opt", 0)
                    )
                }

                MeshPacket.TYPE_POLL_CLOSE -> {
                    MeshPacket.PollClose(id = json.optString("id", ""))
                }

                MeshPacket.TYPE_FEED_REACTION -> {
                    MeshPacket.FeedReaction(
                        postId = json.optString("postId", ""),
                        liked = json.optBoolean("liked", true),
                        reactorName = json.optString("reactorName", ""),
                        timestamp = json.optLong("timestamp", System.currentTimeMillis())
                    )
                }

                MeshPacket.TYPE_RANDOMIZER -> {
                    MeshPacket.Randomizer(
                        id = json.optString("id", ""),
                        rType = json.optString("rType", ""),
                        prompt = json.optString("prompt", ""),
                        player = json.optString("player", "")
                    )
                }

                MeshPacket.TYPE_SHARED_MEDIA -> {
                    MeshPacket.SharedMedia(
                        mediaType = json.optString("mediaType", "none"),
                        url = json.optString("url", ""),
                        hostId = json.optString("hostId", ""),
                        mode = json.optString("mode", "collaborative"),
                        maxSeats = json.optInt("maxSeats", 0)
                    )
                }

                MeshPacket.TYPE_MEDIA_INVITE -> {
                    MeshPacket.MediaInvite(
                        mediaType = json.optString("mediaType", "none"),
                        url = json.optString("url", "")
                    )
                }

                MeshPacket.TYPE_CLIPBOARD_SYNC -> {
                    MeshPacket.ClipboardSync(
                        text = json.optString("text", ""),
                        sender = json.optString("sender", "Peer"),
                        timestamp = json.optLong("timestamp", System.currentTimeMillis())
                    )
                }

                MeshPacket.TYPE_SYS_HANDSHAKE, MeshPacket.TYPE_HANDSHAKE -> {
                    MeshPacket.SysHandshake(
                        ssid = json.optString("ssid", ""),
                        pwd = json.optString("pwd", ""),
                        ip = json.optString("ip", "")
                    )
                }

                MeshPacket.TYPE_SYS_IDENTITY -> {
                    MeshPacket.SysIdentity(
                        id = json.optString("id", ""),
                        name = json.optString("name", ""),
                        usernameId = json.optString("usernameId", "")
                    )
                }

                MeshPacket.TYPE_SYS_STATE_SYNC -> {
                    val messages = mutableListOf<ChatMessageDto>()
                    val msgArray = json.optJSONArray("messages")
                    if (msgArray != null) {
                        for (i in 0 until msgArray.length()) {
                            val mObj = msgArray.optJSONObject(i) ?: continue
                            messages.add(
                                ChatMessageDto(
                                    id = mObj.optString("id", ""),
                                    senderId = mObj.optString("senderId", ""),
                                    senderName = mObj.optString("senderName", ""),
                                    message = mObj.optString("message", ""),
                                    timestamp = mObj.optLong("timestamp", 0L)
                                )
                            )
                        }
                    }
                    val users = mutableMapOf<String, String>()
                    val usersObj = json.optJSONObject("users")
                    if (usersObj != null) {
                        val keys = usersObj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            users[k] = usersObj.optString(k, "")
                        }
                    }
                    MeshPacket.SysStateSync(messages = messages, users = users)
                }

                MeshPacket.TYPE_SYS_SYNC_REQUEST -> {
                    MeshPacket.SysSyncRequest
                }

                MeshPacket.TYPE_SYS_KICKED -> {
                    MeshPacket.SysKicked(target = json.optString("target", ""))
                }

                MeshPacket.TYPE_SYS_BANNED -> {
                    MeshPacket.SysBanned(target = json.optString("target", ""))
                }

                MeshPacket.TYPE_LOCATION -> {
                    MeshPacket.Location(
                        id = json.optString("id", ""),
                        name = json.optString("name", ""),
                        lat = json.optDouble("lat", 0.0),
                        lng = json.optDouble("lng", 0.0)
                    )
                }

                MeshPacket.TYPE_RADAR_PING -> {
                    MeshPacket.RadarPing(requesterId = json.optString("requester", ""))
                }

                MeshPacket.TYPE_CALL_OFFER -> {
                    MeshPacket.CallOffer(
                        callerId = json.optString("callerId", ""),
                        callerName = json.optString("callerName", "Nearby Peer"),
                        callerIp = json.optString("callerIp", ""),
                        targetPeerId = json.optString("targetPeerId", ""),
                        pubKey = json.optString("pubKey", "")
                    )
                }

                MeshPacket.TYPE_CALL_ANSWER -> {
                    MeshPacket.CallAnswer(
                        callerId = json.optString("callerId", ""),
                        calleeId = json.optString("calleeId", ""),
                        calleeName = json.optString("calleeName", ""),
                        calleeIp = json.optString("calleeIp", ""),
                        pubKey = json.optString("pubKey", "")
                    )
                }

                MeshPacket.TYPE_CALL_END -> {
                    MeshPacket.CallEnd(
                        senderId = json.optString("senderId", ""),
                        reason = json.optString("reason", "")
                    )
                }

                MeshPacket.TYPE_LIFELINE_REQUEST -> {
                    MeshPacket.LifelineRequest(
                        reqId = json.optString("reqId", ""),
                        url = json.optString("url", ""),
                        body = json.optString("body", ""),
                        method = json.optString("method", "GET"),
                        senderId = json.optString("senderId", "")
                    )
                }

                MeshPacket.TYPE_LIFELINE_RESPONSE -> {
                    MeshPacket.LifelineResponse(
                        reqId = json.optString("reqId", ""),
                        status = json.optInt("status", 200),
                        response = json.optString("response", ""),
                        recipientId = json.optString("recipientId", "")
                    )
                }

                MeshPacket.TYPE_CLUSTER_FORWARD -> {
                    MeshPacket.ClusterForward(
                        targetNodeId = json.optString("targetNodeId", ""),
                        payload = json.optString("payload", "")
                    )
                }

                MeshPacket.TYPE_DELIVERY_ACK -> {
                    MeshPacket.DeliveryAck(
                        originalMsgId = json.optString("originalMsgId", "")
                    )
                }

                else -> MeshPacket.Raw(raw)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse JSON packet, falling back to Raw", e)
            MeshPacket.Raw(raw)
        }
    }
}
