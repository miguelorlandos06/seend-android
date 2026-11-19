package com.seend.app.data.remote.s3

import java.security.MessageDigest

/**
 * Sobre autenticado TDS3 (y legacy TDS2) + convenciones de llaves del
 * protocolo Seend. Porte del `store.go` del relay y de las key helpers
 * del `internal/s3bridge/store.go` del servidor Go.
 *
 * Formato del sobre:
 *   magic(4) || HMAC-SHA256(secret, key||0x00||blob)(32) || blob
 * donde blob es:
 *   - TDS3: nonce(12) || ciphertext||tag  (AES-GCM)
 *   - TDS2: plaintext (legacy, sin cifrar)
 *
 * El HMAC vincula el sobre a su llave (anti relocalización): dos objetos
 * con el mismo contenido pero distinta llave tienen HMACs distintos.
 */
internal class EnvelopeCodec(
    private val secret: ByteArray,
    private val aesKey: ByteArray?, // null → legacy TDS2 sin cifrar
) {
    fun encryptionEnabled(): Boolean = aesKey != null

    /** Sella data en un sobre TDS3 (o TDS2 si el cifrado está apagado). */
    fun seal(key: String, data: ByteArray): ByteArray {
        val magic: ByteArray
        val blob: ByteArray = if (aesKey != null) {
            val enc = Crypto.encryptAESGCM(aesKey, data)
            magic = MAGIC_SECURE
            enc
        } else {
            magic = MAGIC_LEGACY
            data
        }
        val tag = Crypto.hmacSHA256(secret, listOf(key.toByteArray(Charsets.UTF_8), byteArrayOf(0), blob))
        return magic + tag + blob
    }

    /** Abre y verifica un sobre. Devuelve el plaintext o null si es inválido. */
    fun open(key: String, envelope: ByteArray): ByteArray? {
        if (envelope.size < 4 + TAG_SIZE) return null
        val magic = envelope.copyOfRange(0, 4)
        val isLegacy = magic.contentEquals(MAGIC_LEGACY)
        val isSecure = magic.contentEquals(MAGIC_SECURE)
        if (!isLegacy && !isSecure) return null
        val tag = envelope.copyOfRange(4, 4 + TAG_SIZE)
        val blob = envelope.copyOfRange(4 + TAG_SIZE, envelope.size)

        val expected = Crypto.hmacSHA256(secret, listOf(key.toByteArray(Charsets.UTF_8), byteArrayOf(0), blob))
        if (!MessageDigest.isEqual(tag, expected)) return null

        return when {
            isSecure -> {
                // val local para que el smart-cast de ByteArray? → ByteArray
                // se propague dentro del lambda runCatching.
                val key = aesKey ?: return null
                runCatching { Crypto.decryptAESGCM(key, blob) }.getOrNull()
            }
            else -> blob // TDS2 legacy
        }
    }

    private companion object {
        val MAGIC_LEGACY = "TDS2".toByteArray(Charsets.US_ASCII)
        val MAGIC_SECURE = "TDS3".toByteArray(Charsets.US_ASCII)
        const val TAG_SIZE = 32
    }
}

/**
 * Convenciones de llaves en el bucket s3.todus.cu/stickers.
 *
 * clientId (cid) es un UUID v4 sin guiones (32 hex).
 *
 *   _seend_clients.bin              → registro compartido (lista de cids)
 *   _seend_reg_{cid}.bin            → heartbeat de presencia del cliente
 *   _seend_req_{cid}_{seq:010}.bin  → petición (cliente → servidor)
 *   _seend_reqidx_{cid}.bin         → mayor seq de petición escrito
 *   _seend_res_{cid}_{seq:010}.bin  → respuesta/push (servidor → cliente)
 *   _seend_residx_{cid}.bin         → mayor seq de respuesta escrito
 *
 * El índice evita needing ListObjects: el lector conoce el último seq
 * procesado y lee hasta el valor del índice.
 */
internal object Keys {
    fun reg(cid: String) = "_seend_reg_${cid}.bin"
    fun clients() = "_seend_clients.bin"
    fun req(cid: String, seq: Long) = "_seend_req_${cid}_%010d.bin".format(seq)
    fun reqIdx(cid: String) = "_seend_reqidx_${cid}.bin"
    fun res(cid: String, seq: Long) = "_seend_res_${cid}_%010d.bin".format(seq)
    fun resIdx(cid: String) = "_seend_residx_${cid}.bin"
}

/** Serialización del contador de seq (decimal ASCII), igual que el relay. */
internal object Counter {
    fun encode(v: Long): ByteArray = v.toString().toByteArray(Charsets.US_ASCII)
    fun decode(raw: ByteArray?): Long {
        if (raw == null || raw.isEmpty()) return 0L
        var v = 0L
        for (b in raw) {
            val c = b.toInt() and 0xFF // byte unsigned → Int
            when (c) {
                in '0'.code..'9'.code -> v = v * 10 + (c - '0'.code)
                ' '.code, '\n'.code, '\r'.code, '\t'.code -> continue
                else -> return 0L
            }
        }
        return v
    }
}
