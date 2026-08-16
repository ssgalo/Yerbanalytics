package com.yerbanalytics.camara

import com.yerbanalytics.camara.contrato.Backoff
import com.yerbanalytics.camara.contrato.EventoCanal
import com.yerbanalytics.camara.contrato.ParserEventos
import com.yerbanalytics.camara.contrato.Watchdog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackoffTest {

    @Test
    fun `crece exponencialmente y respeta el tope`() {
        // Con aleatorio = 1.0 el jitter da siempre el techo, así que se ve la progresión pura.
        val techo = { n: Int -> Backoff.esperaMs(n, aleatorio = { 1.0 }) }
        assertEquals(1_000L, techo(1))
        assertEquals(2_000L, techo(2))
        assertEquals(4_000L, techo(3))
        assertEquals(8_000L, techo(4))
        // No supera el tope por más que crezca el intento
        assertEquals(Backoff.TOPE_MS, techo(50))
    }

    @Test
    fun `el jitter mantiene la espera entre la mitad y el techo`() {
        // Sin jitter, varios dispositivos que pierden la red juntos vuelven todos en el mismo
        // instante y le pegan al backend en manada.
        val piso = Backoff.esperaMs(4, aleatorio = { 0.0 })
        val techo = Backoff.esperaMs(4, aleatorio = { 1.0 })
        assertEquals(4_000L, piso)
        assertEquals(8_000L, techo)
        repeat(200) {
            val v = Backoff.esperaMs(4)
            assertTrue("espera fuera de rango: $v", v in piso..techo)
        }
    }

    @Test
    fun `un intento invalido no rompe`() {
        assertTrue(Backoff.esperaMs(0) > 0)
        assertTrue(Backoff.esperaMs(-5) > 0)
    }
}

class WatchdogTest {

    @Test
    fun `un canal que entrega eventos esta vivo`() {
        assertFalse(Watchdog.canalMuerto(ultimoEventoMs = 1_000, ahoraMs = 20_000, heartbeatSeg = 15))
    }

    @Test
    fun `el silencio prolongado da el canal por muerto`() {
        // 15s de cadencia: se toleran 45s de silencio.
        assertFalse(Watchdog.canalMuerto(0, 45_000, 15))
        assertTrue(Watchdog.canalMuerto(0, 45_001, 15))
    }

    @Test
    fun `una cadencia chica no dispara reconexiones en loop`() {
        // Con heartbeatSeg = 1 el umbral real es el piso (5s x 3), no 3s.
        assertEquals(15_000L, Watchdog.silencioMaximoMs(1))
        assertFalse(Watchdog.canalMuerto(0, 10_000, 1))
    }
}

class ParserEventosTest {

    @Test
    fun `parsea una orden`() {
        val datos = """{"ordenId":"018f3a2b","sectorId":"S-042","zonaId":"MZ-1",""" +
            """"posicionRiel":1420,"emitidaEn":1754308800000,"venceEn":1754308860000,"intento":1}"""
        val evento = ParserEventos.parsear("orden", datos)
        assertTrue(evento is EventoCanal.OrdenRecibida)
        val orden = (evento as EventoCanal.OrdenRecibida).orden
        assertEquals("018f3a2b", orden.ordenId)
        assertEquals("S-042", orden.sectorId)
        assertEquals(1420, orden.posicionRiel)
    }

    @Test
    fun `una orden con campos desconocidos no rompe`() {
        // §5.6 del contrato: agregar un campo opcional no debe romper clientes viejos.
        val datos = """{"ordenId":"abc","sectorId":"S-1","zonaId":"MZ-1","posicionRiel":10,""" +
            """"emitidaEn":1,"venceEn":2,"intento":1,"campoQueNoExisteTodavia":"valor",""" +
            """"otroObjeto":{"a":1}}"""
        val evento = ParserEventos.parsear("orden", datos)
        assertTrue(evento is EventoCanal.OrdenRecibida)
        assertEquals("abc", (evento as EventoCanal.OrdenRecibida).orden.ordenId)
    }

    @Test
    fun `parsea la configuracion`() {
        val datos = """{"anchoMax":3840,"altoMax":2160,"calidadJpeg":0.9,"warmupMs":700,""" +
            """"heartbeatSeg":30,"timeoutOrdenSeg":90,"maxColaOrdenes":10}"""
        val evento = ParserEventos.parsear("config", datos)
        assertTrue(evento is EventoCanal.ConfigRecibida)
        val config = (evento as EventoCanal.ConfigRecibida).config
        assertEquals(3840, config.anchoMax)
        assertEquals(30, config.heartbeatSeg)
    }

    @Test
    fun `el ping se reconoce y no lleva payload util`() {
        assertEquals(EventoCanal.Ping, ParserEventos.parsear("ping", """{"ts":1754308820000}"""))
    }

    @Test
    fun `un evento de un tipo nuevo se ignora sin fallar`() {
        // El contrato clasifica "agregar un evento nuevo" como cambio COMPATIBLE: un cliente
        // que no lo conoce tiene que ignorarlo, no morirse.
        val evento = ParserEventos.parsear("telemetriaDelRiel", """{"lo que sea":true}""")
        assertTrue(evento is EventoCanal.Desconocido)
        assertEquals("telemetriaDelRiel", (evento as EventoCanal.Desconocido).tipo)
    }

    @Test
    fun `un evento conocido con cuerpo roto se reporta pero no propaga excepcion`() {
        val evento = ParserEventos.parsear("orden", "{esto no es json")
        assertTrue(evento is EventoCanal.Ilegible)
    }
}
