package com.aetherweb.app

import android.content.Context
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

class CryptoManager {
    private var keyPair: KeyPair = generateFreshKeyPair()

    private fun generateFreshKeyPair(): KeyPair {
        val keyGen = KeyPairGenerator.getInstance("EC")
        keyGen.initialize(256)
        return keyGen.generateKeyPair()
    }

    /**
     * bitchat parity: stable identity across restarts. Loads the EC P-256 signing
     * keypair from EncryptedSharedPreferences (AndroidKeyStore-backed), generating
     * and storing it on first run. Without this, every restart makes you a
     * stranger — dedup, rate-limiter and peer-table state reset.
     *
     * Falls back to plain SharedPreferences when the keystore is unavailable,
     * and keeps the ephemeral keypair if everything fails (never crashes).
     *
     * @return true if a previously-stored identity was restored
     */
    @Synchronized
    fun loadOrCreatePersistentIdentity(context: Context): Boolean {
        val prefs = try {
            val masterKey = androidx.security.crypto.MasterKey.Builder(context, androidx.security.crypto.MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                .build()
            androidx.security.crypto.EncryptedSharedPreferences.create(
                context,
                "aether_mesh_identity",
                masterKey,
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            android.util.Log.w("CryptoManager", "EncryptedSharedPreferences unavailable, using plain prefs", e)
            context.getSharedPreferences("aether_mesh_identity_plain", Context.MODE_PRIVATE)
        }
        return try {
            val privB64 = prefs.getString("ec_priv_pkcs8", null)
            val pubB64 = prefs.getString("ec_pub_x509", null)
            if (!privB64.isNullOrBlank() && !pubB64.isNullOrBlank()) {
                val kf = KeyFactory.getInstance("EC")
                val priv = kf.generatePrivate(
                    java.security.spec.PKCS8EncodedKeySpec(Base64.decode(privB64, Base64.NO_WRAP))
                )
                val pub = kf.generatePublic(X509EncodedKeySpec(Base64.decode(pubB64, Base64.NO_WRAP)))
                keyPair = KeyPair(pub, priv)
                android.util.Log.i("CryptoManager", "Restored persistent mesh identity")
                true
            } else {
                val fresh = generateFreshKeyPair()
                prefs.edit()
                    .putString("ec_priv_pkcs8", Base64.encodeToString(fresh.private.encoded, Base64.NO_WRAP))
                    .putString("ec_pub_x509", Base64.encodeToString(fresh.public.encoded, Base64.NO_WRAP))
                    .apply()
                keyPair = fresh
                android.util.Log.i("CryptoManager", "Generated new persistent mesh identity")
                false
            }
        } catch (e: Exception) {
            android.util.Log.w("CryptoManager", "Persistent identity failed, keeping ephemeral keypair", e)
            false
        }
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
