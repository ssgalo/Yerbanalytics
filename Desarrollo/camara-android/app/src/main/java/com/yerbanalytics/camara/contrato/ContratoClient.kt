package com.yerbanalytics.camara.contrato

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Falla que trae un código HTTP del contrato. */
class ErrorRespuesta(val codigo: Int, val mensaje: String) : IOException("HTTP $codigo: $mensaje")

/**
 * Resultado de subir una imagen, con la semántica exacta que define el contrato.
 *
 * El `409` NO es un error: significa que una subida anterior sí llegó y se perdió la respuesta.
 * Tratarlo como éxito es lo que evita duplicados y falsos fallos ante un corte de red en el
 * momento justo (§5.3).
 */
sealed interface ResultadoSubida {
    data class Ok(val captura: CapturaCreada, val yaExistia: Boolean) : ResultadoSubida

    /** 400/403/404/413/422: la imagen no sirve o no es de este dispositivo. Se descarta. */
    data class Descartar(val codigo: Int, val mensaje: String) : ResultadoSubida

    /** Red caída o 5xx: la imagen se conserva y se reintenta. */
    data class Reintentar(val motivo: String) : ResultadoSubida
}

/** Provee el token de acceso vigente. Separado para poder testear el cliente sin credenciales. */
interface ProveedorToken {
    suspend fun vigente(): String

    /** Fuerza una renovación. Devuelve el token nuevo, o lanza si la credencial ya no sirve. */
    suspend fun renovar(): String
}

/**
 * Cliente de los siete endpoints del contrato `camara/v1`.
 *
 * **Toda** la superficie que esta app toca del backend pasa por acá. Si alguna vez hiciera falta
 * un endpoint fuera de [Contrato.NAMESPACE], el problema sería del contrato y no de la app.
 *
 * La credencial viaja SIEMPRE en el header `Authorization`, incluido el canal de órdenes. El
 * contrato admite `?token=` en el stream como concesión a `EventSource` del navegador, que no
 * puede fijar headers; OkHttp sí puede, así que esa alternativa no se usa nunca. De paso, el
 * token no queda escrito en los access logs del backend.
 */
class ContratoClient(
    baseUrl: String,
    private val proveedor: ProveedorToken?,
    val http: OkHttpClient = clientePorDefecto(),
) {

    /** `http://192.168.1.56:8000/api/camara/v1` — sin barra final. */
    val base: String = baseUrl.trimEnd('/') + Contrato.NAMESPACE

    private val jsonMedia = "application/json".toMediaType()
    private val jpegMedia = "image/jpeg".toMediaType()

    companion object {
        fun clientePorDefecto(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS) // subir un JPEG por WiFi de vivero puede tardar
            .retryOnConnectionFailure(true)
            .build()

        /**
         * Cliente para el canal SSE: sin timeout de lectura, porque un stream sano puede pasar
         * largo rato sin escribir nada. Quien detecta que murió es el watchdog de pings, no un
         * timeout del socket.
         */
        fun clienteStream(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    // ---------------------------------------------------------------- Credenciales

    /** Único endpoint del contrato que no lleva token. */
    suspend fun enrolar(codigo: String, nombre: String, plataforma: String): EnrolarResponse =
        withContext(Dispatchers.IO) {
            val cuerpo = Contrato.json.encodeToString(EnrolarRequest(codigo, nombre, plataforma))
            val req = Request.Builder()
                .url("$base/enrolar")
                .post(cuerpo.toRequestBody(jsonMedia))
                .build()
            http.newCall(req).execute().use { r ->
                val texto = r.body.string()
                if (!r.isSuccessful) throw ErrorRespuesta(r.code, mensajeDe(texto, r.code))
                Contrato.json.decodeFromString<EnrolarResponse>(texto)
            }
        }

    suspend fun token(refreshToken: String): TokenResponse = withContext(Dispatchers.IO) {
        val cuerpo = Contrato.json.encodeToString(TokenRequest(refreshToken))
        val req = Request.Builder()
            .url("$base/token")
            .post(cuerpo.toRequestBody(jsonMedia))
            .build()
        http.newCall(req).execute().use { r ->
            val texto = r.body.string()
            if (!r.isSuccessful) throw ErrorRespuesta(r.code, mensajeDe(texto, r.code))
            Contrato.json.decodeFromString<TokenResponse>(texto)
        }
    }

    // ---------------------------------------------------------------- Operación

    suspend fun config(): ConfigCaptura = withContext(Dispatchers.IO) {
        autenticado { token ->
            Request.Builder()
                .url("$base/config")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
        }.use { r ->
            val texto = r.body.string()
            if (!r.isSuccessful) throw ErrorRespuesta(r.code, mensajeDe(texto, r.code))
            Contrato.json.decodeFromString<ConfigCaptura>(texto)
        }
    }

    suspend fun heartbeat(latido: Heartbeat) = withContext(Dispatchers.IO) {
        val cuerpo = Contrato.json.encodeToString(latido)
        autenticado { token ->
            Request.Builder()
                .url("$base/heartbeat")
                .header("Authorization", "Bearer $token")
                .post(cuerpo.toRequestBody(jsonMedia))
                .build()
        }.use { r ->
            if (!r.isSuccessful) {
                throw ErrorRespuesta(r.code, mensajeDe(r.body.string(), r.code))
            }
        }
    }

    // ---------------------------------------------------------------- Captura

    /**
     * La correlación con el riel es explícita: el [ordenId] de la URL es lo que permite al
     * backend saber en qué posición se tomó la imagen. No hay correlación implícita por sector
     * ni por timestamp.
     */
    suspend fun subirImagen(
        ordenId: String,
        jpeg: ByteArray,
        meta: MetadataImagen,
    ): ResultadoSubida = withContext(Dispatchers.IO) {
        val metaJson = Contrato.json.encodeToString(meta)
        try {
            autenticado { token ->
                val cuerpo = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("imagen", "captura.jpg", jpeg.toRequestBody(jpegMedia))
                    .addFormDataPart("meta", null, metaJson.toRequestBody(jsonMedia))
                    .build()
                Request.Builder()
                    .url("$base/ordenes/$ordenId/imagen")
                    .header("Authorization", "Bearer $token")
                    .post(cuerpo)
                    .build()
            }.use { r ->
                val texto = r.body.string()
                when {
                    r.code == 201 -> ResultadoSubida.Ok(decodificarCaptura(texto, ordenId), false)
                    // Regla de idempotencia del contrato: 409 es ÉXITO.
                    r.code == 409 -> ResultadoSubida.Ok(decodificarCaptura(texto, ordenId), true)
                    r.code in 400..499 ->
                        ResultadoSubida.Descartar(r.code, mensajeDe(texto, r.code))
                    else -> ResultadoSubida.Reintentar("HTTP ${r.code}")
                }
            }
        } catch (e: ErrorRespuesta) {
            if (e.codigo in 400..499) ResultadoSubida.Descartar(e.codigo, e.mensaje)
            else ResultadoSubida.Reintentar(e.mensaje)
        } catch (e: IOException) {
            ResultadoSubida.Reintentar(e.message ?: "red no disponible")
        }
    }

    /**
     * El contrato obliga a acusar TODO fallo, incluidas las órdenes descartadas por cola llena.
     * Un descarte silencioso deja la orden esperando su vencimiento en vez de reintentarse ya.
     */
    suspend fun acusarFallo(ordenId: String, motivo: MotivoFallo, detalle: String? = null) =
        withContext(Dispatchers.IO) {
            val cuerpo = Contrato.json.encodeToString(AcuseFallo(motivo, detalle?.take(500)))
            autenticado { token ->
                Request.Builder()
                    .url("$base/ordenes/$ordenId/fallo")
                    .header("Authorization", "Bearer $token")
                    .post(cuerpo.toRequestBody(jsonMedia))
                    .build()
            }.use { r ->
                // 409 = la orden ya está en estado terminal. No hay nada que hacer ni que
                // reportar: el backend ya la resolvió por su cuenta.
                if (!r.isSuccessful && r.code != 409) {
                    throw ErrorRespuesta(r.code, mensajeDe(r.body.string(), r.code))
                }
            }
        }

    // ---------------------------------------------------------------- Interno

    /**
     * Ejecuta con el token vigente y, ante un 401, renueva y reintenta **una sola vez** (§3 del
     * contrato). Si la renovación también falla, propaga: quien llama descarta la credencial.
     */
    private suspend fun autenticado(construir: (String) -> Request): Response {
        val p = proveedor ?: error("Esta operación requiere credenciales")
        val primera = http.newCall(construir(p.vigente())).execute()
        if (primera.code != 401) return primera
        primera.close()
        val nuevo = p.renovar()
        return http.newCall(construir(nuevo)).execute()
    }

    private fun decodificarCaptura(texto: String, ordenId: String): CapturaCreada = try {
        Contrato.json.decodeFromString<CapturaCreada>(texto)
    } catch (_: Exception) {
        CapturaCreada(ordenId = ordenId)
    }

    private fun mensajeDe(texto: String, codigo: Int): String = try {
        Contrato.json.decodeFromString<ErrorContrato>(texto).error.ifBlank { "HTTP $codigo" }
    } catch (_: Exception) {
        texto.take(200).ifBlank { "HTTP $codigo" }
    }
}
