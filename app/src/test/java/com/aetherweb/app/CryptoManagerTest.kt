package com.aetherweb.app

import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CryptoManagerTest {
    @Test
    fun testCrypto() {
        val crypto = CryptoManager()
        val msg = "123:node1:hello world:999"
        val sig = crypto.sign(msg)
        val pub = crypto.publicKeyBase64
        val isValid = CryptoManager.verify(msg, sig, pub)
        assertTrue(isValid)
    }
}
