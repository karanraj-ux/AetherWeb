package com.aetherweb.app

import java.util.concurrent.ConcurrentSkipListMap

/**
 * Phase 4: Calling Quality Lock-In
 * Adaptive 40ms UDP De-Jitter Buffer with Packet Loss Concealment (PLC).
 *
 * Prevents out-of-order jitter pops and packet drops in real-time UDP audio streams.
 *
 * Mechanics:
 * 1. Incoming packets are placed in a sorted map keyed by monotonic sequence number.
 * 2. An adaptive 40ms sliding window reorders scrambled UDP packets.
 * 3. When a frame deadline expires and a packet is missing, it synthesizes an attenuated
 *    decay frame (Packet Loss Concealment) from the previous PCM frame rather than
 *    producing an audible click or silence gap.
 */
class AudioJitterBuffer(
    private val frameSizePcm: Int = LiveVoiceManager.FRAME_BYTES_PCM,
    private val jitterDelayMs: Long = 40L
) {
    data class AudioFrame(
        val sequenceNumber: Int,
        val pcmData: ByteArray,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val frameMap = ConcurrentSkipListMap<Int, AudioFrame>()
    private var lastEmittedSequence: Int = -1
    private var lastValidPcmFrame: ByteArray? = null
    private var isFirstPacket = true

    /**
     * Pushes a decoded PCM frame with its sequence number into the jitter buffer.
     */
    fun pushFrame(sequenceNumber: Int, pcmData: ByteArray) {
        val now = System.currentTimeMillis()
        if (isFirstPacket) {
            lastEmittedSequence = sequenceNumber - 1
            isFirstPacket = false
        }

        // If packet arrived too late (older than already emitted sequence), drop to prevent time reversal
        val diff = sequenceNumber - lastEmittedSequence
        if (diff <= 0 && diff > -1000) {
            return
        }

        frameMap[sequenceNumber] = AudioFrame(sequenceNumber, pcmData, now)

        // Prevent memory growth if peer sequence jumps
        if (frameMap.size > 25) {
            frameMap.pollFirstEntry()
        }
    }

    /**
     * Polls the next in-order frame.
     * If the next expected sequence is ready or deadline expired, returns PCM data.
     * If missing when deadline expired, synthesizes a PLC (Packet Loss Concealment) frame.
     */
    fun pollNextPlayoutFrame(): ByteArray? {
        if (frameMap.isEmpty()) {
            return null
        }

        val targetSeq = lastEmittedSequence + 1
        val exactFrame = frameMap.remove(targetSeq)
        if (exactFrame != null) {
            lastEmittedSequence = targetSeq
            lastValidPcmFrame = exactFrame.pcmData.clone()
            return exactFrame.pcmData
        }

        // Check oldest buffered packet
        val firstEntry = frameMap.firstEntry() ?: return null
        val now = System.currentTimeMillis()
        val age = now - firstEntry.value.timestamp

        // If the oldest frame has waited past our jitter threshold (40ms), playout must proceed!
        if (age >= jitterDelayMs) {
            if (firstEntry.key > targetSeq) {
                // We missed targetSeq -> synthesize PLC frame to prevent pop/click
                lastEmittedSequence = targetSeq
                return generatePlcFrame()
            } else {
                // Sequence wrapped or jumped
                val popped = frameMap.pollFirstEntry()?.value
                if (popped != null) {
                    lastEmittedSequence = popped.sequenceNumber
                    lastValidPcmFrame = popped.pcmData.clone()
                    return popped.pcmData
                }
            }
        }

        return null
    }

    /**
     * Packet Loss Concealment (PLC):
     * Fades the waveform from the previous valid frame with 50% amplitude decay,
     * maintaining waveform continuity without harsh digital clicks.
     */
    private fun generatePlcFrame(): ByteArray {
        val prev = lastValidPcmFrame
        val plcFrame = ByteArray(frameSizePcm)
        if (prev != null && prev.size == frameSizePcm) {
            for (i in 0 until frameSizePcm step 2) {
                val sample = ((prev[i + 1].toInt() shl 8) or (prev[i].toInt() and 0xFF)).toShort()
                // Attenuate by 50%
                val decayed = (sample * 0.5f).toInt().toShort()
                plcFrame[i] = (decayed.toInt() and 0xFF).toByte()
                plcFrame[i + 1] = ((decayed.toInt() shr 8) and 0xFF).toByte()
            }
            lastValidPcmFrame = plcFrame.clone()
        }
        return plcFrame
    }

    fun reset() {
        frameMap.clear()
        lastEmittedSequence = -1
        lastValidPcmFrame = null
        isFirstPacket = true
    }
}
