package com.aetherweb.app

import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

class CryptoManager {
    private val keyPair: KeyPair

    init {
        val keyGen = KeyPairGenerator.getInstance("EC")
        keyGen.initialize(256)
        keyPair = keyGen.generateKeyPair()
    }

    val publicKeyBase64: String
        get() = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)

    fun sign(data: String): String {
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(keyPair.private)
        signature.update(data.toByteArray(Charsets.UTF_8))
        val sigBytes = signature.sign()
        return Base64.encodeToString(sigBytes, Base64.NO_WRAP)
    }

    companion object {
        private const val AES_ALGORITHM = "AES/GCM/NoPadding"
        private const val RSA_ALGORITHM = "RSA/ECB/PKCS1Padding"
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_IV_LENGTH = 12

        fun generateRSAKeyPair(): KeyPair {
            val keyGen = KeyPairGenerator.getInstance("RSA")
            keyGen.initialize(2048)
            return keyGen.generateKeyPair()
        }

        fun generateAESKey(): javax.crypto.SecretKey {
            val keyGen = javax.crypto.KeyGenerator.getInstance("AES")
            keyGen.init(256)
            return keyGen.generateKey()
        }

        fun encryptRSA(data: ByteArray, publicKey: PublicKey): String {
            val cipher = javax.crypto.Cipher.getInstance(RSA_ALGORITHM)
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, publicKey)
            return Base64.encodeToString(cipher.doFinal(data), Base64.NO_WRAP)
        }

        fun decryptRSA(base64Data: String, privateKey: PrivateKey): ByteArray {
            val data = Base64.decode(base64Data, Base64.NO_WRAP)
            val cipher = javax.crypto.Cipher.getInstance(RSA_ALGORITHM)
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, privateKey)
            return cipher.doFinal(data)
        }

        fun exportAESKey(secretKey: javax.crypto.SecretKey): String {
            return Base64.encodeToString(secretKey.encoded, Base64.NO_WRAP)
        }

        fun importAESKey(base64Key: String): javax.crypto.SecretKey {
            val decoded = Base64.decode(base64Key, Base64.NO_WRAP)
            return javax.crypto.spec.SecretKeySpec(decoded, 0, decoded.size, "AES")
        }

        fun encryptAES(data: String, secretKey: javax.crypto.SecretKey): String {
            val cipher = javax.crypto.Cipher.getInstance(AES_ALGORITHM)
            val iv = ByteArray(GCM_IV_LENGTH)
            java.security.SecureRandom().nextBytes(iv)
            val spec = javax.crypto.spec.GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey, spec)
            val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + encrypted.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
            return Base64.encodeToString(combined, Base64.NO_WRAP)
        }

        fun decryptAES(base64Data: String, secretKey: javax.crypto.SecretKey): String {
            val combined = Base64.decode(base64Data, Base64.NO_WRAP)
            if (combined.size <= GCM_IV_LENGTH) {
                throw IllegalArgumentException("Ciphertext is too short to contain IV and payload")
            }
            val iv = ByteArray(GCM_IV_LENGTH)
            val encrypted = ByteArray(combined.size - GCM_IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, iv.size)
            System.arraycopy(combined, iv.size, encrypted, 0, encrypted.size)
            val cipher = javax.crypto.Cipher.getInstance(AES_ALGORITHM)
            val spec = javax.crypto.spec.GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKey, spec)
            return String(cipher.doFinal(encrypted), Charsets.UTF_8)
        }

        fun getPublicKeyAsString(publicKey: PublicKey): String {
            return Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
        }

        fun getPublicKeyFromString(base64Str: String, algorithm: String = "RSA"): PublicKey {
            val bytes = Base64.decode(base64Str, Base64.NO_WRAP)
            return KeyFactory.getInstance(algorithm).generatePublic(X509EncodedKeySpec(bytes))
        }

        fun computeNodeId(publicKeyBase64: String): String {
            return try {
                val md = java.security.MessageDigest.getInstance("SHA-256")
                val digest = md.digest(publicKeyBase64.toByteArray(Charsets.UTF_8))
                digest.joinToString("") { "%02x".format(it) }.take(16)
            } catch (e: Exception) {
                publicKeyBase64.take(16)
            }
        }

        fun verify(data: String, signatureBase64: String, publicKeyBase64: String): Boolean {
            return try {
                val pubKeyBytes = Base64.decode(publicKeyBase64, Base64.NO_WRAP)
                val keySpec = X509EncodedKeySpec(pubKeyBytes)
                val keyFactory = KeyFactory.getInstance("EC")
                val publicKey: PublicKey = keyFactory.generatePublic(keySpec)

                val signature = Signature.getInstance("SHA256withECDSA")
                signature.initVerify(publicKey)
                signature.update(data.toByteArray(Charsets.UTF_8))

                val sigBytes = Base64.decode(signatureBase64, Base64.NO_WRAP)
                signature.verify(sigBytes)
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
}
