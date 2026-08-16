package com.yerbanalytics.camara.contrato

/** Lo que puede llegar por el canal de órdenes. */
sealed interface EventoCanal {
    data class OrdenRecibida(val orden: Orden) : EventoCanal
    data class ConfigRecibida(val config: ConfigCaptura) : EventoCanal
    data object Ping : EventoCanal

    /**
     * Evento cuyo tipo esta versión no conoce. Se ignora **sin fallar**: es lo que permite al
     * backend agregar eventos nuevos sin romper clientes viejos (§5.6 y §6 del contrato).
     */
    data class Desconocido(val tipo: String?) : EventoCanal

    /** Llegó un evento conocido con un cuerpo que no se pudo interpretar. */
    data class Ilegible(val tipo: String?, val motivo: String) : EventoCanal
}

/**
 * Traduce un evento crudo del stream a [EventoCanal]. Separado del transporte a propósito: es
 * lógica pura y se testea sin levantar un servidor ni un teléfono.
 */
object ParserEventos {

    fun parsear(tipo: String?, datos: String): EventoCanal = when (tipo) {
        "orden" -> decodificar(tipo, datos) { EventoCanal.OrdenRecibida(Contrato.json.decodeFromString(it)) }
        "config" -> decodificar(tipo, datos) { EventoCanal.ConfigRecibida(Contrato.json.decodeFromString(it)) }
        "ping" -> EventoCanal.Ping
        else -> EventoCanal.Desconocido(tipo)
    }

    private inline fun decodificar(
        tipo: String?,
        datos: String,
        bloque: (String) -> EventoCanal,
    ): EventoCanal = try {
        bloque(datos)
    } catch (e: Exception) {
        EventoCanal.Ilegible(tipo, e.message ?: e::class.java.simpleName)
    }
}
