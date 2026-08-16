package com.yerbanalytics.camara.contrato

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Modelos del contrato `camara/v1`.
 *
 * Fuente de verdad: `Desarrollo/contratos/camara/v1/openapi.yaml`. Si algo de acá no coincide
 * con ese archivo, lo que está mal es esto.
 *
 * Todos los campos opcionales tienen valor por defecto y el parser ignora las claves que no
 * conoce: es la obligación §5.6 del contrato (tolerancia a la evolución), lo que permite al
 * backend agregar campos opcionales sin romper esta app.
 */
object Contrato {
    /** Namespace versionado. Ninguna petición de esta app sale de acá. */
    const val NAMESPACE = "/api/camara/v1"

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        isLenient = true
    }
}

@Serializable
data class EnrolarRequest(
    val codigo: String,
    val nombre: String,
    val plataforma: String? = null,
)

@Serializable
data class EnrolarResponse(
    val dispositivoId: String,
    val refreshToken: String,
)

@Serializable
data class TokenRequest(val refreshToken: String)

@Serializable
data class TokenResponse(
    val accessToken: String,
    val expiraEnSeg: Int,
)

/**
 * Configuración de captura. **Propiedad del backend**: ninguno de estos valores se fija en el
 * código del cliente (§5.5 del contrato). Los defaults de acá sólo cubren el hueco entre el
 * arranque del servicio y la primera respuesta de `/config`.
 */
@Serializable
data class ConfigCaptura(
    val anchoMax: Int = 1920,
    val altoMax: Int = 1080,
    val calidadJpeg: Float = 0.85f,
    val warmupMs: Int = 1500,
    val heartbeatSeg: Int = 15,
    val timeoutOrdenSeg: Int = 60,
    val maxColaOrdenes: Int = 20,
)

@Serializable
data class Orden(
    val ordenId: String,
    val sectorId: String = "",
    val zonaId: String = "",
    val posicionRiel: Int = 0,
    val emitidaEn: Long = 0,
    val venceEn: Long = 0,
    val intento: Int = 1,
) {
    fun vencida(ahora: Long): Boolean = venceEn > 0 && ahora >= venceEn
}

@Serializable
data class MetadataImagen(
    val ancho: Int,
    val alto: Int,
    val sha256: String,
    val capturadaEn: Long,
    /**
     * Ajustes que la cámara pudo aplicar realmente. Formato libre: el backend lo registra sin
     * interpretarlo. Es la evidencia para juzgar la consistencia fotométrica entre capturas.
     */
    val constraints: Map<String, String>? = null,
)

@Serializable
data class CapturaCreada(
    val capturaId: String = "",
    val ordenId: String = "",
    val imagenUrl: String = "",
)

/**
 * Conjunto **cerrado** de motivos. Un motivo fuera de esta lista lo rechaza el backend con 400,
 * a propósito: permite clasificar los fallos sin interpretar texto libre.
 */
@Serializable
enum class MotivoFallo {
    CAMARA_NO_LISTA,
    EXPORTACION_FALLIDA,
    COLA_LLENA,
    TIMEOUT_LOCAL,
    ENVIO_AGOTADO,
    ERROR_DESCONOCIDO,
}

@Serializable
data class AcuseFallo(
    val motivo: MotivoFallo,
    val detalle: String? = null,
)

@Serializable
data class Heartbeat(
    val capturaListo: Boolean,
    val capturasOk: Int = 0,
    val capturasError: Int = 0,
    val pendientesEnvio: Int = 0,
    val detalle: String? = null,
)

@Serializable
data class ErrorContrato(val error: String = "")
