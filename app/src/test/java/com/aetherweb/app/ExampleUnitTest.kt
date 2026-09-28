package com.aetherweb.app

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testAudioCodec_EncodeAndDecode() {
    // Generate a 20ms sine wave @ 16kHz (320 samples = 640 bytes)
    val sampleCount = 320
    val pcmBytes = ByteArray(sampleCount * 2)
    for (i in 0 until sampleCount) {
      val sample = (kotlin.math.sin(2.0 * Math.PI * 440.0 * i / 16000.0) * 10000).toInt().toShort()
      pcmBytes[i * 2] = (sample.toInt() and 0xFF).toByte()
      pcmBytes[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
    }

    // Compress to ADPCM
    val encoded = AudioCodec.encode(pcmBytes, pcmBytes.size, sequence = 1)
    // 320 samples / 2 = 160 bytes + 4 bytes header = 164 bytes (4:1 compression!)
    assertEquals(164, encoded.size)

    // Decompress back to PCM
    val decoded = AudioCodec.decode(encoded, encoded.size)
    assertEquals(pcmBytes.size, decoded.size)

    // Verify signal fidelity after initial ADPCM step adaptation
    var steadyStateError = 0L
    for (i in 15 until sampleCount) {
      val orig = ((pcmBytes[i * 2 + 1].toInt() shl 8) or (pcmBytes[i * 2].toInt() and 0xFF)).toShort()
      val reconstructed = ((decoded[i * 2 + 1].toInt() shl 8) or (decoded[i * 2].toInt() and 0xFF)).toShort()
      val diff = kotlin.math.abs(orig - reconstructed)
      steadyStateError += diff
      assertTrue("Sample $i error $diff exceeds tolerance", diff < 1800)
    }
    val avgError = steadyStateError / (sampleCount - 15)
    assertTrue("Average error $avgError too high", avgError < 600)
  }
}
