package com.aetherweb.app.protocol

import org.json.JSONArray
import org.json.JSONObject

/**
 * Type-safe unified sealed protocol hierarchy for all mesh payloads.
 * Eliminates ad-hoc JSON building, loose string keys, and fragile untyped parsing.
 */
sealed class MeshPacket(val packetType: String) {
    abstract fun toJson(): JSONObject
    fun toJsonString(): String = toJson().toString()

    data class Chat(
        val message: String,
        val senderName: String = "",
        val senderHandle: String = "",
        val recipientId: String? = null,
        val isBurner: Boolean = false,
        val burnerId: String = "",
        val isEmergency: Boolean = false,
        val timestamp: Long = System.currentTimeMillis()
    ) : MeshPacket(TYPE_CHAT) {
        override fun toJson(): JSONObject = JSONObject().apply {
            put("type", TYPE_CHAT)
            put("message", message)
            if (senderName.isNotBlank()) put("senderName", senderName)
            if (senderHandle.isNotBlank()) put("senderHandle", senderHandle)
            if (!recipientId.isNullOrBlank()) put("recipientId", recipientId)
            if (isBurner) put("isBurner", true)
            if (burnerId.isNotBlank()) put("burnerId", burnerId)
            if (isEmergency) put("isEmergency", true)
            put("timestamp", timestamp)
        }
    }

    data class CanvasAction(
        val action: String, // "start", "move", "end", "laser", "bg", "clear", "undo"
        val id: String = "",
        val color: String = "#000000",
        val x: Float = 0f,
        val y: Float = 0f,
        val strokeWidth: Float = 10f,
        val data: String = ""
    ) : MeshPacket(TYPE_CANVAS) {
        override fun toJson(): JSONObject = JSONObject().apply {
            put("type", TYPE_CANVAS)
            put("action", action)
            put("id", id)
            put("color", color)
            put("x", x.toDouble())
            put("y", y.toDouble())
            put("strokeWidth", strokeWidth.toDouble())
            put("data", data)
        }
    }

    data class CanvasSync(
        val paths: List<CanvasPathDto> = emptyList(),
        val background: String = ""
    ) : MeshPacket(TYPE_CANVAS_SYNC) {
        override fun toJson(): JSONObject = JSONObject().apply {
            put("type", TYPE_CANVAS_SYNC)
            val arr = JSONArray()
            paths.forEach { p ->
                val pObj = JSONObject().apply {
                    put("id", p.id)
                    put("color", p.colorHex)
                    put("strokeWidth", p.strokeWidth.toDouble())
                    val pts = JSONArray()
                    p.points.forEach { pt ->
                        pts.put(JSONObject().apply {
                            put("x", pt.first.toDouble())
                            put("y", pt.second.toDouble())
                        })
                    }
                    put("points", pts)
                }
                arr.put(pObj)
            }
            put("paths", arr)
            put("bg", background)
        }
    }

    data class MiniGameSync(
        val gameType: String, // "tictactoe", "connect4", "chess", "ludo"
        val stateJson: String
    ) : MeshPacket(gameType) {
        override fun toJson(): JSONObject = JSONObject().apply {
            put("type", gameType)
            try {
                put("state", JSONObject(stateJson))
            } catch (e: Exception) {
                put("state", stateJson)
            }
        }
    }

    data class MeshMusicSync(
        val action: String,
        val trackTitle: String,
        val artist: String,
        val uri: String,
        val isStream: Boolean = false,
        val positionMs: Long = 0L,
        val hostName: String = "",
        val timestamp: Long = System.currentTimeMillis()
    ) : MeshPacket(TYPE_MESH_MUSIC_SYNC) {
        override fun toJson(): JSONObject = JSONObject().apply {
            put("type", TYPE_MESH_MUSIC_SYNC)
            put("action", action)
            put("trackTitle", trackTitle)
            put("artist", artist)
            put("uri", uri)
            put("isStream", isStream)
            put("positionMs", positionMs)
            put("hostName", hostName)
            put("timestamp", timestamp)
        }
    }

    data class SosBeacon(
        val senderId: String,
        val senderName: String,
        val senderHandle: String = "",
        val message: String = "Immediate Assistance Required!",
        val lat: Double? = null,
        val lng: Double? = null,
        val timestamp: Long = System.currentTimeMillis()
    ) : MeshPacket(TYPE_SOS_BEACON) {
        override fun toJson(): JSONObject = JSONObject().apply {
            put("type", TYPE_SOS_BEACON)
            put("senderId", senderId)
            put("senderName", senderName)
            if (senderHandle.isNotBlank()) put("senderHandle", senderHandle)
            put("message", message)
            lat?.let { put("lat", it) }
            lng?.let { put("lng", it) }
            put("timestamp", timestamp)
        }
    }

    data class SosCancel(
        val senderId: String
    ) : MeshPacket(TYPE_SOS_CANCEL) {
        override fun toJson(): JSONObject = JSONObject().apply {
            put("type", TYPE_SOS_CANCEL)
            put("senderId", senderId)
        }
    }

    data class BurnerSync(
        val action: String, // "start", "leave"
        val burnerId: String,
        val duration: Int = 300,
        val initiator: String = "Someone",
        val user: String = ""
    ) : MeshPacket(TYPE_BURNER_SYNC) {
        override fun toJson(): JSONObject = JSONObject().apply {
            put("type", TYPE_BURNER_SYNC)
            put("action", action)
            put("burnerId", burnerId)
            put("duration", duration)
            put("initiator", initiator)
            if (user.isNotBlank()) put("user", user)
        }
    }

    data class PollStart(
        val id: String,
        val question: String,
        val options: List<String>,
        val attachmentUrl: String = ""
    ) : MeshPacket(TYPE_POLL_START) {
        override fun toJson(): JSONObject = JSONObject().apply {
            put("type", TYPE_POLL_START)
            put("id", id)
            put("q", question)
            put("options", options.joinToString("|"))
            if (attachmentUrl.isNotBlank()) put("attachmentUrl", attachmentUrl)
        }
    }

    data class PollVote(
        val id: String,
        val optionIndex: Int
    ) : MeshPacket(TYPE_POLL_VOTE) {
        override fun toJson(): JSONObject = JSONObject().apply {
            put("type", TYPE_POLL_VOTE)
            put("id", id)
            put("opt", optionIndex)
        }
    }

    data class PollClose(
        val id: String = ""
    ) : MeshPacket(TYPE_POLL_CLOSE) {
        override fun toJson(): JSONObject = JSONObject().apply {
            put("type", TYPE_POLL_CLOSE)
            if (id.isNotBlank()) put("id", id)
        }
    }

    data class Randomizer(
        val id: String,
        val rType: String,
        val prompt: String,
        val player: String
    ) : MeshPacket(TYPE_RANDOMIZER) {
        override fun toJson(): JSONObject = JSONObject().apply {
            put("type", TYPE_RANDOMIZER)
            put("id", id)
            put("rType", rType)
            put("prompt", prompt)
            put("player", player)
        }
    }

    data class SharedMedia(
        val mediaType: String,
        val url: String = "",
        val hostId: String = "",
        val mode: String = "collaborative",
        val maxSeats: Int = 0
    ) : MeshPacket(TYPE_SHARED_MEDIA) {
        override fun toJson(): JSONObject = JSONObject().apply {
            put("type", TYPE_SHARED_MEDIA)
            put("mediaType", mediaType)
            put("url", url)
            if (hostId.isNotBlank()) put("hostId", hostId)
            put("mode", mode)
            put("maxSeats", maxSeats)
        }
    }

    data class MediaInvite(
        val mediaType: String,
        val url: String = ""
    ) : MeshPacket(TYPE_MEDIA_INVITE) {
        override fun toJson(): JSONObject = JSONObject().apply {
            put("type", TYPE_MEDIA_INVITE)
            put("mediaType", mediaType)
            put("url", url)
        }
    }

    data class ClipboardSync(
        val text: String,
        val sender: String = "Peer",
        val timestamp: Long = System.currentTimeMillis()
    ) : MeshPacket(TYPE_CLIPBOARD_SYNC) {
        override fun toJson(): JSONObject = JSONObject().apply {
            put("type", TYPE_CLIPBOARD_SYNC)
            put("text", text)
            put("sender", sender)
            put("timestamp", timestamp)
        }
    }

    data class SysHandshake(
        val ssid: String,
        val pwd: String,
        val ip: String
    ) : MeshPacket(TYPE_SYS_HANDSHAKE) {
        override fun toJson(): JSONObject = JSONObject().apply {
            put("type", TYPE_SYS_HANDSHAKE)
            put("ssid", ssid)
            put("pwd", pwd)
            put("ip", ip)
        }
    }

    data class SysIdentity(
        val id: String,
        val name: String,
        val usernameId: String = ""
    ) : MeshPacket(TYPE_SYS_IDENTITY) {
        override fun toJson(): JSONObject = JSONObject().apply {
            put("type", TYPE_SYS_IDENTITY)
            put("id", id)
            put("name", name)
            if (usernameId.isNotBlank()) put("usernameId", usernameId)
        }
    }

    data class SysStateSync(
        val messages: List<ChatMessageDto> = emptyList(),
        val users: Map<String, String> = emptyMap()
    ) : MeshPacket(TYPE_SYS_STATE_SYNC) {
        override fun toJson(): JSONObject = JSONObject().apply {
            put("type", TYPE_SYS_STATE_SYNC)
            val msgArr = JSONArray()
            messages.forEach { msg ->
                msgArr.put(JSONObject().apply {
                    put("id", msg.id)
                    put("senderId", msg.senderId)
                    put("senderName", msg.senderName)
                    put("message", msg.message)
                    put("timestamp", msg.timestamp)
                })
            }
            put("messages", msgArr)
            val uObj = JSONObject()
            users.forEach { (k, v) -> uObj.put(k, v) }
            put("users", uObj)
        }
    }

    object SysSyncRequest : MeshPacket(TYPE_SYS_SYNC_REQUEST) {
        override fun toJson(): JSONObject = JSONObject().apply {
            put("type", TYPE_SYS_SYNC_REQUEST)
        }
    }

    data class SysKicked(
        val target: String
    ) : MeshPacket(TYPE_SYS_KICKED) {
        override fun toJson(): JSONObject = JSONObject().apply {
            put("type", TYPE_SYS_KICKED)
            put("target", target)
        }
    }

    data class SysBanned(
        val target: String
    ) : MeshPacket(TYPE_SYS_BANNED) {
        override fun toJson(): JSONObject = JSONObject().apply {
            put("type", TYPE_SYS_BANNED)
            put("target", target)
        }
    }

    data class Location(
        val id: String,
        val name: String,
        val lat: Double,
        val lng: Double
    ) : MeshPacket(TYPE_LOCATION) {
        override fun toJson(): JSONObject = JSONObject().apply {
            put("type", TYPE_LOCATION)
            put("id", id)
            put("name", name)
            put("lat", lat)
            put("lng", lng)
        }
    }

    data class RadarPing(
        val requesterId: String = ""
    ) : MeshPacket(TYPE_RADAR_PING) {
        override fun toJson(): JSONObject = JSONObject().apply {
            put("type", TYPE_RADAR_PING)
            if (requesterId.isNotBlank()) put("requester", requesterId)
        }
    }

    data class CallOffer(
        val callerId: String,
        val callerName: String,
        val callerIp: String = "",
        val targetPeerId: String = "",
        val pubKey: String = ""
    ) : MeshPacket(TYPE_CALL_OFFER) {
        override fun toJson(): JSONObject = JSONObject().apply {
            put("type", TYPE_CALL_OFFER)
            put("callerId", callerId)
            put("callerName", callerName)
            if (callerIp.isNotBlank()) put("callerIp", callerIp)
            if (targetPeerId.isNotBlank()) put("targetPeerId", targetPeerId)
            if (pubKey.isNotBlank()) put("pubKey", pubKey)
        }
    }

    data class CallAnswer(
        val callerId: String = "",
        val calleeId: String = "",
        val calleeName: String = "",
        val calleeIp: String = "",
        val pubKey: String = ""
    ) : MeshPacket(TYPE_CALL_ANSWER) {
        override fun toJson(): JSONObject = JSONObject().apply {
            put("type", TYPE_CALL_ANSWER)
            if (callerId.isNotBlank()) put("callerId", callerId)
            if (calleeId.isNotBlank()) put("calleeId", calleeId)
            if (calleeName.isNotBlank()) put("calleeName", calleeName)
            if (calleeIp.isNotBlank()) put("calleeIp", calleeIp)
            if (pubKey.isNotBlank()) put("pubKey", pubKey)
        }
    }

    data class CallEnd(
        val senderId: String = "",
        val reason: String = ""
    ) : MeshPacket(TYPE_CALL_END) {
        override fun toJson(): JSONObject = JSONObject().apply {
            put("type", TYPE_CALL_END)
            if (senderId.isNotBlank()) put("senderId", senderId)
            if (reason.isNotBlank()) put("reason", reason)
        }
    }

    data class LifelineRequest(
        val reqId: String,
        val url: String,
        val body: String = "",
        val method: String = "GET",
        val senderId: String
    ) : MeshPacket(TYPE_LIFELINE_REQUEST) {
        override fun toJson(): JSONObject = JSONObject().apply {
            put("type", TYPE_LIFELINE_REQUEST)
            put("reqId", reqId)
            put("url", url)
            if (body.isNotBlank()) put("body", body)
            put("method", method)
            put("senderId", senderId)
        }
    }

    data class LifelineResponse(
        val reqId: String,
        val status: Int,
        val response: String,
        val recipientId: String
    ) : MeshPacket(TYPE_LIFELINE_RESPONSE) {
        override fun toJson(): JSONObject = JSONObject().apply {
            put("type", TYPE_LIFELINE_RESPONSE)
            put("reqId", reqId)
            put("status", status)
            put("response", response)
            put("recipientId", recipientId)
        }
    }

    data class ClusterForward(
        val targetNodeId: String,
        val payload: String
    ) : MeshPacket(TYPE_CLUSTER_FORWARD) {
        override fun toJson(): JSONObject = JSONObject().apply {
            put("type", TYPE_CLUSTER_FORWARD)
            put("targetNodeId", targetNodeId)
            put("payload", payload)
        }
    }

    data class DeliveryAck(
        val originalMsgId: String
    ) : MeshPacket(TYPE_DELIVERY_ACK) {
        override fun toJson(): JSONObject = JSONObject().apply {
            put("type", TYPE_DELIVERY_ACK)
            put("originalMsgId", originalMsgId)
        }
    }

    data class Raw(
        val rawContent: String
    ) : MeshPacket(TYPE_RAW) {
        override fun toJson(): JSONObject = JSONObject().apply {
            put("type", TYPE_RAW)
            put("content", rawContent)
        }
    }

    companion object {
        const val TYPE_CHAT = "chat"
        const val TYPE_CANVAS = "canvas"
        const val TYPE_CANVAS_SYNC = "canvas_sync"
        const val TYPE_MESH_MUSIC_SYNC = "mesh_music_sync"
        const val TYPE_SOS_BEACON = "sos_beacon"
        const val TYPE_SOS_CANCEL = "sos_cancel"
        const val TYPE_BURNER_SYNC = "burner_sync"
        const val TYPE_POLL_START = "poll_start"
        const val TYPE_POLL_VOTE = "poll_vote"
        const val TYPE_POLL_CLOSE = "poll_close"
        const val TYPE_RANDOMIZER = "randomizer"
        const val TYPE_SHARED_MEDIA = "shared_media"
        const val TYPE_MEDIA_INVITE = "media_invite"
        const val TYPE_CLIPBOARD_SYNC = "clipboard_sync"
        const val TYPE_SYS_HANDSHAKE = "sys_handshake"
        const val TYPE_HANDSHAKE = "handshake"
        const val TYPE_SYS_IDENTITY = "sys_identity"
        const val TYPE_SYS_STATE_SYNC = "sys_state_sync"
        const val TYPE_SYS_SYNC_REQUEST = "sys_sync_request"
        const val TYPE_SYS_KICKED = "sys_kicked"
        const val TYPE_SYS_BANNED = "sys_banned"
        const val TYPE_LOCATION = "location"
        const val TYPE_RADAR_PING = "radar_ping"
        const val TYPE_CALL_OFFER = "call_offer"
        const val TYPE_CALL_ANSWER = "call_answer"
        const val TYPE_CALL_END = "call_end"
        const val TYPE_LIFELINE_REQUEST = "lifeline_request"
        const val TYPE_LIFELINE_RESPONSE = "lifeline_response"
        const val TYPE_CLUSTER_FORWARD = "cluster_forward"
        const val TYPE_DELIVERY_ACK = "delivery_ack"
        const val TYPE_RAW = "raw"
    }
}

data class CanvasPathDto(
    val id: String,
    val colorHex: String,
    val points: List<Pair<Float, Float>>,
    val strokeWidth: Float
)

data class ChatMessageDto(
    val id: String,
    val senderId: String,
    val senderName: String,
    val message: String,
    val timestamp: Long
)
