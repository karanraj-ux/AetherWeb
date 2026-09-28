package com.aetherweb.app

import java.nio.ByteBuffer
import java.util.UUID

/**
 * BitChat-Grade Binary Packet Protocol:
 * Provides ultra-compact, low-overhead binary framing for BLE Link Layer transmission.
 *
 * Header Format (32 bytes):
 * - Magic (2 bytes): 0xBC, 0x01 (BitChat Mesh v1)
 * - Flags (1 byte): 0x01 (Encrypted), 0x02 (Compressed), 0x04 (Control), 0x08 (Multi-hop)
 * - TTL (1 byte): Hop countdown (e.g., 3)
 * - Packet ID / Group ID (16 bytes): UUID most + least significant bits
 * - Total Chunks (2 bytes): Short (1..65535)
 * - Chunk Index (2 bytes): Short (0..Total-1)
 * - Payload Length (2 bytes): Short (0..65535)
 * - CRC16 (2 bytes): Integrity checksum over header + payload
 * - Body: N bytes of binary payload
 */
object BlePacketFramer {
    const val MAGIC_BYTE_1: Byte = 0xBC.toByte()
    const val MAGIC_BYTE_2: Byte = 0x01.toByte()
    const val HEADER_SIZE = 28 // 2 + 1 + 1 + 16 + 2 + 2 + 2 + 2

    const val FLAG_ENCRYPTED: Byte = 0x01
    const val FLAG_COMPRESSED: Byte = 0x02
    const val FLAG_CHUNK: Byte = 0x04

    data class FramedChunk(
        val flags: Byte,
        val ttl: Int,
        val groupId: String,
        val totalChunks: Int,
        val chunkIndex: Int,
        val payload: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as FramedChunk
            if (flags != other.flags) return false
            if (ttl != other.ttl) return false
            if (groupId != other.groupId) return false
            if (totalChunks != other.totalChunks) return false
            if (chunkIndex != other.chunkIndex) return false
            if (!payload.contentEquals(other.payload)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = flags.toInt()
            result = 31 * result + ttl
            result = 31 * result + groupId.hashCode()
            result = 31 * result + totalChunks
            result = 31 * result + chunkIndex
            result = 31 * result + payload.contentHashCode()
            return result
        }
    }

    /**
     * Encodes a chunk into a compact binary frame with CRC16 integrity verification.
     */
    fun encodeChunk(
        groupId: UUID,
        totalChunks: Int,
        chunkIndex: Int,
        ttl: Int,
        flags: Byte,
        chunkPayload: ByteArray
    ): ByteArray {
        val payloadLen = chunkPayload.size
        val totalLen = HEADER_SIZE + payloadLen
        val buffer = ByteBuffer.allocate(totalLen)

        buffer.put(MAGIC_BYTE_1)
        buffer.put(MAGIC_BYTE_2)
        buffer.put(flags)
        buffer.put((ttl and 0xFF).toByte())
        buffer.putLong(groupId.mostSignificantBits)
        buffer.putLong(groupId.leastSignificantBits)
        buffer.putShort(totalChunks.toShort())
        buffer.putShort(chunkIndex.toShort())
        buffer.putShort(payloadLen.toShort())

        // Calculate CRC16 over the payload
        val crc = computeCrc16(chunkPayload)
        buffer.putShort(crc.toShort())

        buffer.put(chunkPayload)
        return buffer.array()
    }

    /**
     * Decodes a binary frame into a FramedChunk, verifying Magic and CRC16 checksum.
     */
    fun decodeChunk(frame: ByteArray): FramedChunk? {
        if (frame.size < HEADER_SIZE) return null
        val buffer = ByteBuffer.wrap(frame)

        val m1 = buffer.get()
        val m2 = buffer.get()
        if (m1 != MAGIC_BYTE_1 || m2 != MAGIC_BYTE_2) {
            return null // Not a binary framed packet
        }

        val flags = buffer.get()
        val ttl = buffer.get().toInt() and 0xFF
        val msb = buffer.getLong()
        val lsb = buffer.getLong()
        val groupId = UUID(msb, lsb).toString()
        val totalChunks = buffer.getShort().toInt() and 0xFFFF
        val chunkIndex = buffer.getShort().toInt() and 0xFFFF
        val payloadLen = buffer.getShort().toInt() and 0xFFFF
        val expectedCrc = buffer.getShort().toInt() and 0xFFFF

        if (buffer.remaining() < payloadLen) {
            return null // Incomplete frame
        }

        val payload = ByteArray(payloadLen)
        buffer.get(payload)

        val actualCrc = computeCrc16(payload)
        if (actualCrc != expectedCrc) {
            return null // Corrupted frame dropped
        }

        return FramedChunk(
            flags = flags,
            ttl = ttl,
            groupId = groupId,
            totalChunks = totalChunks,
            chunkIndex = chunkIndex,
            payload = payload
        )
    }

    /**
     * Computes CCITT-CRC16 checksum for packet verification.
     */
    fun computeCrc16(bytes: ByteArray): Int {
        var crc = 0xFFFF
        for (b in bytes) {
            var data = (b.toInt() xor (crc and 0xFF)) and 0xFF
            data = data xor ((data shl 4) and 0xFF)
            crc = ((crc ushr 8) xor (data shl 8) xor (data shl 3) xor (data ushr 4)) and 0xFFFF
        }
        return crc
    }
}
