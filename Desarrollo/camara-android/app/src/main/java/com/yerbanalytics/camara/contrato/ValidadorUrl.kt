package com.yerbanalytics.camara.contrato

import java.net.URI
import java.net.URISyntaxException

/**
 * Valida el formato de la URL del backend antes de guardarla en Ajustes.
 *
 * Se apoya en `java.net.URI` en vez de una regex a mano: el formato de una URL tiene casos
 * borde (IPv6 entre corchetes, userinfo, query) que ya resuelve la biblioteca estándar. Acá
 * sólo se decide qué partes son obligatorias para este contrato en particular: esquema
 * `http`/`https` y host no vacío. El puerto es opcional (el contrato no fija uno), pero si
 * viene explícito tiene que estar en rango.
 *
 * Es una clase de JVM puro, sin dependencias de Android, a propósito: así se puede testear sin
 * Robolectric ni un `Context` real, que es infraestructura que este proyecto no tiene.
 */
object ValidadorUrl {

    sealed interface Resultado {
        /** [normalizada] es la URL sin espacios ni barra final, lista para persistir. */
        data class Valida(val normalizada: String) : Resultado
        data class Invalida(val motivo: String) : Resultado
    }

    fun validar(entrada: String): Resultado {
        val texto = entrada.trim()
        if (texto.isBlank()) return Resultado.Invalida("La URL no puede estar vacía")

        // Olvidarse el esquema es, de lejos, el error más frecuente al tipear esto en un
        // teléfono — y `URI` lo rechaza con una excepción genérica ("192.168.1.100:8000"
        // lanza URISyntaxException), así que el motivo real se perdería detrás de un
        // "URL mal formada". Se detecta antes para poder decir qué falta exactamente.
        if (!texto.contains("://")) {
            return Resultado.Invalida(
                "Falta el esquema http:// o https:// (ej: http://192.168.1.100:8000)"
            )
        }

        val uri = try {
            URI(texto)
        } catch (e: URISyntaxException) {
            return Resultado.Invalida("URL mal formada: ${e.reason}")
        }

        val esquema = uri.scheme?.lowercase()
        if (esquema != "http" && esquema != "https") {
            return Resultado.Invalida(
                "Falta el esquema http:// o https:// (ej: http://192.168.1.100:8000)"
            )
        }
        if (uri.host.isNullOrBlank()) {
            return Resultado.Invalida("Falta el host (ej: 192.168.1.100)")
        }
        // URI no lanza si el puerto escrito está fuera de rango (p.ej. ":99999"): lo parsea
        // como entero y listo. El chequeo de rango va a mano.
        if (uri.port != -1 && uri.port !in 1..65535) {
            return Resultado.Invalida("Puerto fuera de rango: ${uri.port}")
        }

        return Resultado.Valida(texto.trimEnd('/'))
    }
}
