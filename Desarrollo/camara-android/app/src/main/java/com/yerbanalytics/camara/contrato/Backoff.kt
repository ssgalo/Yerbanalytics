package com.yerbanalytics.camara.contrato

import kotlin.math.min
import kotlin.math.pow

/**
 * Espera creciente con jitter, para reconectar el canal y para reintentar envíos.
 *
 * El jitter no es decorativo: sin él, varios dispositivos que pierden la red a la vez vuelven
 * todos juntos en el mismo instante y le pegan al backend en manada.
 *
 * Función pura con la fuente de azar inyectable, para poder testearla.
 */
object Backoff {

    const val BASE_MS = 1_000L
    const val TOPE_MS = 60_000L

    /**
     * @param intento arranca en 1
     * @param aleatorio devuelve [0,1). Inyectable para tests.
     */
    fun esperaMs(
        intento: Int,
        baseMs: Long = BASE_MS,
        topeMs: Long = TOPE_MS,
        aleatorio: () -> Double = { Math.random() },
    ): Long {
        val n = intento.coerceAtLeast(1)
        val exponencial = baseMs.toDouble() * 2.0.pow((n - 1).coerceAtMost(16))
        val techo = min(exponencial, topeMs.toDouble())
        // Jitter completo: cualquier valor entre la mitad del techo y el techo.
        val piso = techo / 2.0
        return (piso + aleatorio() * (techo - piso)).toLong().coerceAtLeast(0)
    }
}
