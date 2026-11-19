package com.seend.app.data.remote.s3

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Criptografía del transporte S3 — porte literal de
 * `todus_relay_vps/crypto.go`. Es idéntica al lado del servidor Go
 * (internal/s3bridge/crypto.go) para que un sobre sellado por la APK
 * pueda abrirse allí y viceversa.
 *
 * - Clave AES-256 derivada del secreto compartido vía HMAC-SHA256 con
 *   un info fijo ("todus-relay-v1/encryption-key").
 * - AES-GCM con nonce aleatorio fresco de 12 bytes; formato del blob:
 *   `nonce(12) || ciphertext||tag`.
 */
internal object Crypto {

    private const val INFO = "todus-relay-v1/encryption-key"
    private const val NONCE_SIZE = 12
    private const val GCM_TAG_BITS = 128

    private val rng: SecureRandom = SecureRandom()

    /** deriveAESKey: HMAC-SHA256(secret, INFO) → 32 bytes (AES-256). */
    fun deriveAESKey(secret: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        mac.update(INFO.toByteArray(Charsets.UTF_8))
        return mac.doFinal() // 32 bytes
    }

    /** encryptAESGCM: devuelve nonce(12) || ciphertext||tag. */
    fun encryptAESGCM(key: ByteArray, plaintext: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE_SIZE)
        rng.nextBytes(nonce)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
        val ct = cipher.doFinal(plaintext) // incluye el tag GCM al final
        return nonce + ct
    }

    /** decryptAESGCM: espera nonce(12) || ciphertext||tag. */
    fun decryptAESGCM(key: ByteArray, blob: ByteArray): ByteArray {
        require(blob.size > NONCE_SIZE) { "blob demasiado corto para AES-GCM" }
        val nonce = blob.copyOfRange(0, NONCE_SIZE)
        val ct = blob.copyOfRange(NONCE_SIZE, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
        return cipher.doFinal(ct)
    }

    /** hmacSHA256: tag de 32 bytes sobre data, con la clave secret. */
    fun hmacSHA256(secret: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        mac.update(data)
        return mac.doFinal()
    }

    /** hmacSHA256 sobre varios trozos sin allocations extra. */
    fun hmacSHA256(secret: ByteArray, parts: List<ByteArray>): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        for (p in parts) mac.update(p)
        return mac.doFinal()
    }
}
