package com.seend.app.data.remote

import com.seend.app.data.model.HttpError
import com.seend.app.data.model.UploadPrepareRequest
import com.seend.app.data.model.UploadPrepareResponse
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * ApiClient: llamadas HTTP (no WebSocket) al servidor.
 *
 * Endpoints usados por la app:
 *  - POST /upload/prepare → pide una URL firmada/pública de S3.
 *  - PUT a la URL devuelta → sube el avatar directamente a S3.
 */
class ApiClient(
    private val baseUrl: String,
    private val httpClient: OkHttpClient,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    /**
     * Llama a /upload/prepare y devuelve la respuesta con las URLs.
     * Lanza [ApiException] si la respuesta no es 2xx.
     */
    fun prepareUpload(token: String, mime: String, bytes: Long): UploadPrepareResponse {
        val body = UploadPrepareRequest(
            token = token,
            tipoArchivo = "avatar",
            bytes = bytes,
            mime = mime,
        )
        val bodyStr = json.encodeToString(body)

        val req = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/upload/prepare")
            .post(bodyStr.toRequestBody(JSON_MEDIA))
            .build()

        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val raw = resp.body?.string().orEmpty()
                val msg = try {
                    json.decodeFromString<HttpError>(raw).error
                } catch (_: Throwable) { raw.ifEmpty { "HTTP ${resp.code}" } }
                throw ApiException(resp.code, msg)
            }
            val raw = resp.body?.string().orEmpty()
            return json.decodeFromString<UploadPrepareResponse>(raw)
        }
    }

    /**
     * Sube un archivo a [urlPut] vía PUT con el Content-Type indicado.
     * Usado para subir el avatar a S3 (la URL la devuelve /upload/prepare).
     */
    fun putFile(urlPut: String, contentType: String, bytes: ByteArray): Boolean {
        val req = Request.Builder()
            .url(urlPut)
            .put(bytes.toRequestBody(contentType.toMediaType()))
            .build()
        httpClient.newCall(req).execute().use { resp ->
            return resp.isSuccessful
        }
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}

/** Excepción tipada para fallos del API. */
class ApiException(val status: Int, val reason: String) : Exception("API $status: $reason")
