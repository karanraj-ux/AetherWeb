package com.aetherweb.app.protocol

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MeshPacketProtocolTest {

    @Test
    fun testChatPacketSerializationAndDeserialization() {
        val original = MeshPacket.Chat(
            message = "Hello offline mesh!",
            senderName = "Alice",
            senderHandle = "@alice_42",
            recipientId = "node_bob_123",
            isBurner = false,
            isEmergency = false
        )

        val encoded = MeshPacketCodec.encode(original)
        val decoded = MeshPacketCodec.decode(encoded)

        assertTrue(decoded is MeshPacket.Chat)
        val chat = decoded as MeshPacket.Chat
        assertEquals("Hello offline mesh!", chat.message)
        assertEquals("Alice", chat.senderName)
        assertEquals("@alice_42", chat.senderHandle)
        assertEquals("node_bob_123", chat.recipientId)
        assertFalse(chat.isBurner)
        assertFalse(chat.isEmergency)
    }

    @Test
    fun testBurnerChatPacket() {
        val burner = MeshPacket.Chat(
            message = "Self-destructing secret",
            senderName = "Agent",
            isBurner = true,
            burnerId = "burner_room_99"
        )
        val encoded = MeshPacketCodec.encode(burner)
        val decoded = MeshPacketCodec.decode(encoded) as MeshPacket.Chat

        assertEquals("Self-destructing secret", decoded.message)
        assertTrue(decoded.isBurner)
        assertEquals("burner_room_99", decoded.burnerId)
    }

    @Test
    fun testCanvasActionPacket() {
        val original = MeshPacket.CanvasAction(
            action = "start",
            id = "stroke_1",
            color = "#FF0000",
            x = 0.45f,
            y = 0.72f,
            strokeWidth = 14f,
            data = ""
        )
        val encoded = MeshPacketCodec.encode(original)
        val decoded = MeshPacketCodec.decode(encoded) as MeshPacket.CanvasAction

        assertEquals("start", decoded.action)
        assertEquals("stroke_1", decoded.id)
        assertEquals("#FF0000", decoded.color)
        assertEquals(0.45f, decoded.x, 0.001f)
        assertEquals(0.72f, decoded.y, 0.001f)
        assertEquals(14f, decoded.strokeWidth, 0.001f)
    }

    @Test
    fun testMiniGameSyncPacket() {
        val game = MeshPacket.MiniGameSync(
            gameType = "chess",
            stateJson = "{\"turn\":\"WHITE\",\"fen\":\"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR\"}"
        )
        val encoded = MeshPacketCodec.encode(game)
        val decoded = MeshPacketCodec.decode(encoded) as MeshPacket.MiniGameSync

        assertEquals("chess", decoded.gameType)
        assertTrue(decoded.stateJson.contains("WHITE"))
    }

    @Test
    fun testSosBeaconAndCancel() {
        val beacon = MeshPacket.SosBeacon(
            senderId = "node_emergency_1",
            senderName = "RescueUnit",
            senderHandle = "@rescue_1",
            message = "Bridge collapsed, need assistance!",
            lat = 37.7749,
            lng = -122.4194
        )
        val encBeacon = MeshPacketCodec.encode(beacon)
        val decBeacon = MeshPacketCodec.decode(encBeacon) as MeshPacket.SosBeacon

        assertEquals("node_emergency_1", decBeacon.senderId)
        assertEquals("RescueUnit", decBeacon.senderName)
        assertEquals(37.7749, decBeacon.lat!!, 0.0001)
        assertEquals(-122.4194, decBeacon.lng!!, 0.0001)

        val cancel = MeshPacket.SosCancel(senderId = "node_emergency_1")
        val decCancel = MeshPacketCodec.decode(MeshPacketCodec.encode(cancel)) as MeshPacket.SosCancel
        assertEquals("node_emergency_1", decCancel.senderId)
    }

    @Test
    fun testPollPackets() {
        val poll = MeshPacket.PollStart(
            id = "poll_42",
            question = "Should we relocate the mesh relay?",
            options = listOf("Yes", "No", "Needs Discussion"),
            attachmentUrl = "mesh://relay.png"
        )
        val enc = MeshPacketCodec.encode(poll)
        val dec = MeshPacketCodec.decode(enc) as MeshPacket.PollStart

        assertEquals("poll_42", dec.id)
        assertEquals(3, dec.options.size)
        assertEquals("Needs Discussion", dec.options[2])

        val vote = MeshPacket.PollVote(id = "poll_42", optionIndex = 1)
        val decVote = MeshPacketCodec.decode(MeshPacketCodec.encode(vote)) as MeshPacket.PollVote
        assertEquals(1, decVote.optionIndex)
    }

    @Test
    fun testMeshMusicSyncPacket() {
        val music = MeshPacket.MeshMusicSync(
            action = "play",
            trackTitle = "Cyber City",
            artist = "SynthWave",
            uri = "content://music/123",
            positionMs = 45000L,
            hostName = "DJ_Host"
        )
        val dec = MeshPacketCodec.decode(MeshPacketCodec.encode(music)) as MeshPacket.MeshMusicSync

        assertEquals("play", dec.action)
        assertEquals("Cyber City", dec.trackTitle)
        assertEquals(45000L, dec.positionMs)
        assertEquals("DJ_Host", dec.hostName)
    }

    @Test
    fun testResilientFallbackOnMalformedOrRawInput() {
        val rawText = "Just a plain string message without JSON"
        val decoded = MeshPacketCodec.decode(rawText)
        assertTrue(decoded is MeshPacket.Raw)
        assertEquals(rawText, (decoded as MeshPacket.Raw).rawContent)

        val malformedJson = "{broken:json without quotes"
        val decodedMalformed = MeshPacketCodec.decode(malformedJson)
        assertTrue(decodedMalformed is MeshPacket.Raw)

        val unknownTypeJson = "{\"type\":\"future_alien_protocol\",\"foo\":\"bar\"}"
        val decodedUnknown = MeshPacketCodec.decode(unknownTypeJson)
        assertTrue(decodedUnknown is MeshPacket.Raw)
    }

    @Test
    fun testCallPackets() {
        val offer = MeshPacket.CallOffer(
            callerId = "node_1",
            callerName = "Alice",
            callerIp = "192.168.49.1",
            targetPeerId = "node_2",
            pubKey = "base64PubKey=="
        )
        val decOffer = MeshPacketCodec.decode(MeshPacketCodec.encode(offer)) as MeshPacket.CallOffer
        assertEquals("node_1", decOffer.callerId)
        assertEquals("Alice", decOffer.callerName)
        assertEquals("192.168.49.1", decOffer.callerIp)
        assertEquals("node_2", decOffer.targetPeerId)

        val answer = MeshPacket.CallAnswer(
            callerId = "node_1",
            calleeId = "node_2",
            calleeName = "Bob",
            calleeIp = "192.168.49.2",
            pubKey = "base64ResponderKey=="
        )
        val decAnswer = MeshPacketCodec.decode(MeshPacketCodec.encode(answer)) as MeshPacket.CallAnswer
        assertEquals("node_2", decAnswer.calleeId)
        assertEquals("Bob", decAnswer.calleeName)
        assertEquals("192.168.49.2", decAnswer.calleeIp)

        val end = MeshPacket.CallEnd(senderId = "node_1", reason = "user_hung_up")
        val decEnd = MeshPacketCodec.decode(MeshPacketCodec.encode(end)) as MeshPacket.CallEnd
        assertEquals("node_1", decEnd.senderId)
        assertEquals("user_hung_up", decEnd.reason)
    }

    @Test
    fun testLifelinePackets() {
        val req = MeshPacket.LifelineRequest(
            reqId = "req_99",
            url = "https://example.com/api",
            method = "POST",
            body = "{\"data\":\"test\"}",
            senderId = "node_offline"
        )
        val decReq = MeshPacketCodec.decode(MeshPacketCodec.encode(req)) as MeshPacket.LifelineRequest
        assertEquals("req_99", decReq.reqId)
        assertEquals("https://example.com/api", decReq.url)
        assertEquals("POST", decReq.method)
        assertEquals("node_offline", decReq.senderId)

        val res = MeshPacket.LifelineResponse(
            reqId = "req_99",
            status = 200,
            response = "{\"success\":true}",
            recipientId = "node_offline"
        )
        val decRes = MeshPacketCodec.decode(MeshPacketCodec.encode(res)) as MeshPacket.LifelineResponse
        assertEquals("req_99", decRes.reqId)
        assertEquals(200, decRes.status)
        assertEquals("node_offline", decRes.recipientId)
    }

    @Test
    fun testSystemAndSyncPackets() {
        val handshake = MeshPacket.SysHandshake(ssid = "MeshAP", pwd = "secretpassword", ip = "192.168.49.1")
        val decHs = MeshPacketCodec.decode(MeshPacketCodec.encode(handshake)) as MeshPacket.SysHandshake
        assertEquals("MeshAP", decHs.ssid)
        assertEquals("secretpassword", decHs.pwd)
        assertEquals("192.168.49.1", decHs.ip)

        val identity = MeshPacket.SysIdentity(id = "node_a", name = "AliceNode", usernameId = "@alice")
        val decId = MeshPacketCodec.decode(MeshPacketCodec.encode(identity)) as MeshPacket.SysIdentity
        assertEquals("node_a", decId.id)
        assertEquals("AliceNode", decId.name)
        assertEquals("@alice", decId.usernameId)

        val kick = MeshPacket.SysKicked(target = "spammer_node")
        val decKick = MeshPacketCodec.decode(MeshPacketCodec.encode(kick)) as MeshPacket.SysKicked
        assertEquals("spammer_node", decKick.target)

        val ban = MeshPacket.SysBanned(target = "bad_actor")
        val decBan = MeshPacketCodec.decode(MeshPacketCodec.encode(ban)) as MeshPacket.SysBanned
        assertEquals("bad_actor", decBan.target)

        val burnerSync = MeshPacket.BurnerSync(action = "set_timer", burnerId = "b1", duration = 300, initiator = "Admin")
        val decBurner = MeshPacketCodec.decode(MeshPacketCodec.encode(burnerSync)) as MeshPacket.BurnerSync
        assertEquals("set_timer", decBurner.action)
        assertEquals("b1", decBurner.burnerId)
        assertEquals(300, decBurner.duration)

        val clip = MeshPacket.ClipboardSync(text = "Secret WiFi Password", sender = "Admin")
        val decClip = MeshPacketCodec.decode(MeshPacketCodec.encode(clip)) as MeshPacket.ClipboardSync
        assertEquals("Secret WiFi Password", decClip.text)
        assertEquals("Admin", decClip.sender)

        val media = MeshPacket.SharedMedia(mediaType = "image", url = "mesh://photo_1.jpg", hostId = "host_1")
        val decMedia = MeshPacketCodec.decode(MeshPacketCodec.encode(media)) as MeshPacket.SharedMedia
        assertEquals("mesh://photo_1.jpg", decMedia.url)
        assertEquals("image", decMedia.mediaType)
    }
}
