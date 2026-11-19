package com.seend.app.data.remote.s3

import android.util.Log
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * S3Store: cliente HTTP anónimo del bucket s3.todus.cu/stickers con
 * sellado/apertura de sobres TDS3. Porte del `store.go` del relay y del
 * `internal/s3bridge/store.go` del servidor Go.
 *
 * - get(key): GET anónimo → abre el sobre → plaintext (o null si
 *   ausente/forbidden/ratelimited/HMAC inválido).
 * - put(key, data): sella data y la sube.
 * - delete(key): borra el objeto (best-effort).
 *
 * El bucket público s3.todus.cu/stickers admite PUT/GET/DELETE anónimos;
 * el disfraz de transporte es peticiones S3 con User-Agent tipo curl.
 */
internal class S3Store(
    httpClient: OkHttpClient,
    baseUrl: String,
    private val codec: EnvelopeCodec,
    userAgent: String,
) {
    private val base = baseUrl.trimEnd('/')
    private val ua = userAgent.ifBlank { "curl/8.23.0" }

    private val client: OkHttpClient = httpClient.newBuilder()
        .retryOnConnectionFailure(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .pingInterval(0, TimeUnit.SECONDS) // sin ping WS para HTTP plano
        .build()

    private fun buildUrl(key: String): String {
        val parts = key.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8") }
        return "$base/$parts"
    }

    /** GET → abre el sobre → plaintext, o null si ausente/inválido. */
    fun get(key: String): ByteArray? {
        val url = buildUrl(key)
        var attempt = 0
        while (attempt <= RETRIES) {
            val req = Request.Builder().url(url).header("User-Agent", ua).get().build()
            try {
                client.newCall(req).execute().use { resp ->
                    when {
                        resp.code == 200 -> {
                            val body = resp.body?.bytes() ?: return null
                            return codec.open(key, body)
                        }
                        resp.code == 404 -> return null
                        resp.code == 401 || resp.code == 403 -> return null
                        resp.code == 429 || resp.code >= 500 -> {
                            Log.w(TAG, "get $key rate/5xx ${resp.code}")
                        }
                        else -> {
                            Log.w(TAG, "get $key status ${resp.code}")
                            return null
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "get $key net err: ${e.message}")
            }
            backoff(attempt)
            attempt++
        }
        return null
    }

    /** Sella data y la sube. true si se almacenó. */
    fun put(key: String, data: ByteArray): Boolean {
        val url = buildUrl(key)
        val sealed = codec.seal(key, data)
        val body = sealed.toRequestBody("application/octet-stream".toMediaTypeOrNull())
        var attempt = 0
        while (attempt <= RETRIES) {
            val req = Request.Builder().url(url).header("User-Agent", ua).put(body).build()
            try {
                client.newCall(req).execute().use { resp ->
                    if (resp.code in 200..299) return true
                    if (resp.code == 429 || resp.code >= 500) {
                        Log.w(TAG, "put $key rate/5xx ${resp.code}")
                        backoff(attempt); attempt++; return@use
                    }
                    Log.w(TAG, "put $key status ${resp.code}")
                    return false
                }
            } catch (e: Exception) {
                Log.w(TAG, "put $key net err: ${e.message}")
                backoff(attempt); attempt++
            }
        }
        return false
    }

    /** DELETE best-effort. */
    fun delete(key: String): Boolean {
        val url = buildUrl(key)
        val req = Request.Builder().url(url).header("User-Agent", ua).delete().build()
        return try {
            client.newCall(req).execute().use { resp ->
                resp.code == 200 || resp.code == 202 || resp.code == 204 || resp.code == 404
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun backoff(attempt: Int) {
        Thread.sleep((100L * (attempt + 1)).coerceAtMost(800))
    }

    private companion object {
        const val TAG = "SeendS3"
        const val RETRIES = 3
    }
}
