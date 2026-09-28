package com.aetherweb.app

import kotlin.math.abs

/**
 * High-performance, zero-allocation IMA-ADPCM (Interactive Multimedia Association)
 * audio codec designed for ultra-low-latency real-time voice streaming over UDP.
 *
 * Compresses 16-bit linear PCM down to 4-bit nibbles (4:1 compression ratio).
 * 320 PCM samples (640 bytes = 20ms @ 16kHz) -> 160 bytes ADPCM + 4-byte header = 164 bytes.
 * Each packet includes its own predictor and step index, making every packet 100% independent
 * to prevent error propagation across dropped UDP packets.
 */
object AudioCodec {

    private val STEP_TABLE = intArrayOf(
        7, 8, 9, 10, 11, 12, 13, 14, 16, 17,
        19, 21, 23, 25, 28, 31, 34, 37, 41, 45,
        50, 55, 60, 66, 73, 80, 88, 97, 107, 118,
        130, 143, 157, 173, 190, 209, 230, 253, 279, 307,
        337, 371, 408, 449, 494, 544, 598, 658, 724, 796,
        876, 963, 1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066,
        2272, 2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358,
        5894, 6484, 7132, 7845, 8630, 9493, 10442, 11487, 12635, 13899,
        15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794, 32767
    )

    private val INDEX_TABLE = intArrayOf(
        -1, -1, -1, -1, 2, 4, 6, 8,
        -1, -1, -1, -1, 2, 4, 6, 8
    )

    /**
     * Encodes 16-bit linear PCM byte buffer into an independent IMA ADPCM packet.
     * Output format:
     * [0..1]: Predictor (Short, Little Endian)
     * [2]: Step Index (0..88)
     * [3]: Flags / Sequence marker
     * [4..N]: Compressed nibbles (2 samples per byte)
     */
    fun encode(pcmBytes: ByteArray, length: Int, sequence: Int = 0): ByteArray {
        val sampleCount = length / 2
        val outSize = 4 + (sampleCount + 1) / 2
        val out = ByteArray(outSize)

        if (sampleCount == 0) return out

        var predictor = 0
        var index = 0

        // Write header
        out[0] = 0
        out[1] = 0
        out[2] = 0
        out[3] = (sequence and 0xFF).toByte()

        var outIdx = 4
        var bufferNibble = 0
        var isLowNibble = true

        for (i in 0 until sampleCount) {
            val byteIdx = i * 2
            val sample = ((pcmBytes[byteIdx + 1].toInt() shl 8) or (pcmBytes[byteIdx].toInt() and 0xFF)).toShort().toInt()

            val step = STEP_TABLE[index]
            var diff = sample - predictor
            var delta = 0

            if (diff < 0) {
                delta = 8
                diff = -diff
            }

            var vpdiff = step shr 3
            if (diff >= step) {
                delta = delta or 4
                diff -= step
                vpdiff += step
            }
            val stepDiv2 = step shr 1
            if (diff >= stepDiv2) {
                delta = delta or 2
                diff -= stepDiv2
                vpdiff += stepDiv2
            }
            val stepDiv4 = step shr 2
            if (diff >= stepDiv4) {
                delta = delta or 1
                vpdiff += stepDiv4
            }

            if ((delta and 8) != 0) {
                predictor -= vpdiff
            } else {
                predictor += vpdiff
            }

            if (predictor > 32767) predictor = 32767
            else if (predictor < -32768) predictor = -32768

            index += INDEX_TABLE[delta]
            if (index < 0) index = 0
            else if (index > 88) index = 88

            if (isLowNibble) {
                bufferNibble = delta and 0x0F
                isLowNibble = false
            } else {
                out[outIdx++] = ((delta shl 4) or bufferNibble).toByte()
                isLowNibble = true
            }
        }

        if (!isLowNibble && outIdx < outSize) {
            out[outIdx] = bufferNibble.toByte()
        }

        return out
    }

    /**
     * Decodes IMA ADPCM packet into 16-bit linear PCM byte array.
     */
    fun decode(adpcmPacket: ByteArray, packetLength: Int): ByteArray {
        if (packetLength < 4) return ByteArray(0)

        var predictor = 0
        var index = 0

        val payloadLen = packetLength - 4
        val sampleCount = payloadLen * 2
        val outPcm = ByteArray(sampleCount * 2)
        var outIdx = 0

        for (i in 0 until payloadLen) {
            val byteVal = adpcmPacket[4 + i].toInt() and 0xFF
            val deltaLow = byteVal and 0x0F
            val deltaHigh = (byteVal shr 4) and 0x0F

            // Decode low nibble
            predictor = decodeSample(deltaLow, predictor, index)
            index = updateIndex(deltaLow, index)
            outPcm[outIdx++] = (predictor and 0xFF).toByte()
            outPcm[outIdx++] = ((predictor shr 8) and 0xFF).toByte()

            // Decode high nibble
            predictor = decodeSample(deltaHigh, predictor, index)
            index = updateIndex(deltaHigh, index)
            outPcm[outIdx++] = (predictor and 0xFF).toByte()
            outPcm[outIdx++] = ((predictor shr 8) and 0xFF).toByte()
        }

        return outPcm
    }

    private fun decodeSample(delta: Int, prevPredictor: Int, index: Int): Int {
        val step = STEP_TABLE[index]
        var vpdiff = step shr 3

        if ((delta and 4) != 0) vpdiff += step
        if ((delta and 2) != 0) vpdiff += (step shr 1)
        if ((delta and 1) != 0) vpdiff += (step shr 2)

        var predictor = if ((delta and 8) != 0) {
            prevPredictor - vpdiff
        } else {
            prevPredictor + vpdiff
        }

        if (predictor > 32767) predictor = 32767
        else if (predictor < -32768) predictor = -32768
        return predictor
    }

    private fun updateIndex(delta: Int, prevIndex: Int): Int {
        var index = prevIndex + INDEX_TABLE[delta]
        if (index < 0) index = 0
        else if (index > 88) index = 88
        return index
    }
}
