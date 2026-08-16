package com.yerbanalytics.camara.contrato

/**
 * Regla que decide si el canal está muerto.
 *
 * Está separada del transporte para poder testearla sin abrir un socket, y porque es la parte
 * que importa: el contrato advierte que **la ausencia de eventos es la única señal disponible**
 * de que el canal murió. Un socket colgado no reporta error, simplemente deja de entregar; si
 * el cliente esperara un error, esperaría para siempre.
 */
object Watchdog {

    /** Múltiplo de la cadencia de heartbeat que se tolera sin recibir nada. */
    const val FACTOR_SILENCIO = 3

    /** Piso de cadencia, para que una configuración chica no dispare reconexiones en loop. */
    const val HEARTBEAT_MINIMO_SEG = 5

    fun silencioMaximoMs(heartbeatSeg: Int): Long =
        heartbeatSeg.coerceAtLeast(HEARTBEAT_MINIMO_SEG) * FACTOR_SILENCIO * 1_000L

    fun canalMuerto(ultimoEventoMs: Long, ahoraMs: Long, heartbeatSeg: Int): Boolean =
        ahoraMs - ultimoEventoMs > silencioMaximoMs(heartbeatSeg)
}
