package com.yerbanalytics.camara.colas

import com.yerbanalytics.camara.contrato.Contrato
import com.yerbanalytics.camara.contrato.Orden
import kotlinx.serialization.encodeToString
import java.io.File

/** Qué pasó al intentar encolar una orden. */
sealed interface ResultadoEncolar {
    data object Aceptada : ResultadoEncolar

    /**
     * La cola estaba en su tope. La orden [descartada] debe acusarse al backend con motivo
     * `COLA_LLENA`: un descarte silencioso deja la orden esperando su vencimiento en vez de
     * reintentarse enseguida.
     */
    data class Descartada(val descartada: Orden) : ResultadoEncolar
}

/**
 * Cola FIFO de órdenes pendientes de ejecutar, **persistida en disco**.
 *
 * Sobre la persistencia: el backend vence y reintenta las órdenes por su cuenta, así que una
 * cola en memoria sería "suficiente". Pero una orden ya **entregada** por el stream no se le
 * vuelve a drenar a la app al reconectar, así que si el proceso muere con ocho órdenes
 * entregadas y sin ejecutar, esas ocho esperan su vencimiento de a una. En medio de una pasada
 * de 600 sectores es una demora perfectamente evitable, y en Android persistir cuesta nada.
 *
 * Sigue siendo una **optimización, no la garantía**: aunque el teléfono pierda todo, el backend
 * vence la orden y la reintenta.
 *
 * Clase pura de `java.io`: se testea en la JVM sin emulador.
 */
class ColaOrdenes(private val archivo: File) {

    private val pendientes = ArrayDeque<Orden>()

    init {
        runCatching { archivo.parentFile?.mkdirs() }
        cargar()
    }

    val tamano: Int @Synchronized get() = pendientes.size

    @Synchronized
    fun listar(): List<Orden> = pendientes.toList()

    /**
     * Encola respetando [maxCola]. Al llegar al tope descarta **la más vieja**: ante una pasada
     * de riel, la orden más nueva es la que todavía tiene sentido capturar.
     */
    @Synchronized
    fun encolar(orden: Orden, maxCola: Int): ResultadoEncolar {
        if (pendientes.any { it.ordenId == orden.ordenId }) {
            // Reentrega de una orden que ya teníamos: se reemplaza para quedarse con el
            // `intento` nuevo, sin duplicarla.
            pendientes.removeAll { it.ordenId == orden.ordenId }
        }
        pendientes.addLast(orden)
        val tope = maxCola.coerceAtLeast(1)
        var descartada: Orden? = null
        while (pendientes.size > tope) {
            descartada = pendientes.removeFirst()
        }
        persistir()
        return descartada?.let { ResultadoEncolar.Descartada(it) } ?: ResultadoEncolar.Aceptada
    }

    /** Saca la próxima orden a ejecutar, en el orden en que llegaron. */
    @Synchronized
    fun tomar(): Orden? {
        val orden = pendientes.removeFirstOrNull() ?: return null
        persistir()
        return orden
    }

    @Synchronized
    fun quitar(ordenId: String) {
        if (pendientes.removeAll { it.ordenId == ordenId }) persistir()
    }

    @Synchronized
    fun vaciar() {
        pendientes.clear()
        persistir()
    }

    /**
     * Órdenes recuperadas del disco que ya vencieron. No tiene sentido capturarlas —el backend
     * ya las dio por vencidas—, pero sí dejar constancia de que se descartaron.
     */
    @Synchronized
    fun purgarVencidas(ahora: Long): List<Orden> {
        val vencidas = pendientes.filter { it.vencida(ahora) }
        if (vencidas.isNotEmpty()) {
            pendientes.removeAll(vencidas.toSet())
            persistir()
        }
        return vencidas
    }

    private fun cargar() {
        runCatching {
            if (!archivo.exists()) return@runCatching
            val texto = archivo.readText()
            if (texto.isBlank()) return@runCatching
            pendientes.addAll(Contrato.json.decodeFromString<List<Orden>>(texto))
        }
    }

    private fun persistir() {
        runCatching {
            val temporal = File(archivo.parentFile, archivo.name + ".tmp")
            temporal.writeText(Contrato.json.encodeToString(pendientes.toList()))
            if (!temporal.renameTo(archivo)) {
                archivo.writeText(temporal.readText())
                temporal.delete()
            }
        }
    }
}
