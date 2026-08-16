package com.yerbanalytics.camara

import com.yerbanalytics.camara.colas.ColaEnvio
import com.yerbanalytics.camara.colas.ColaOrdenes
import com.yerbanalytics.camara.colas.ResultadoEncolar
import com.yerbanalytics.camara.contrato.MetadataImagen
import com.yerbanalytics.camara.contrato.Orden
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

private fun orden(id: String, venceEn: Long = Long.MAX_VALUE) = Orden(
    ordenId = id,
    sectorId = "S-$id",
    zonaId = "MZ-1",
    posicionRiel = 100,
    emitidaEn = 0,
    venceEn = venceEn,
    intento = 1,
)

class ColaOrdenesTest {

    private lateinit var dir: File
    private lateinit var archivo: File

    @Before
    fun preparar() {
        dir = Files.createTempDirectory("cola-ordenes").toFile()
        archivo = File(dir, "ordenes.json")
    }

    @After
    fun limpiar() {
        dir.deleteRecursively()
    }

    @Test
    fun `las ordenes se ejecutan en el orden en que llegaron`() {
        val cola = ColaOrdenes(archivo)
        cola.encolar(orden("a"), maxCola = 20)
        cola.encolar(orden("b"), maxCola = 20)
        cola.encolar(orden("c"), maxCola = 20)

        assertEquals("a", cola.tomar()?.ordenId)
        assertEquals("b", cola.tomar()?.ordenId)
        assertEquals("c", cola.tomar()?.ordenId)
        assertNull(cola.tomar())
    }

    @Test
    fun `al llegar al tope descarta y avisa cual descarto`() {
        val cola = ColaOrdenes(archivo)
        assertEquals(ResultadoEncolar.Aceptada, cola.encolar(orden("a"), maxCola = 2))
        assertEquals(ResultadoEncolar.Aceptada, cola.encolar(orden("b"), maxCola = 2))

        val resultado = cola.encolar(orden("c"), maxCola = 2)

        // Nunca en silencio: quien llama tiene que poder acusar COLA_LLENA al backend.
        assertTrue(resultado is ResultadoEncolar.Descartada)
        assertEquals("a", (resultado as ResultadoEncolar.Descartada).descartada.ordenId)
        assertEquals(2, cola.tamano)
    }

    @Test
    fun `una reentrega no duplica la orden`() {
        val cola = ColaOrdenes(archivo)
        cola.encolar(orden("a"), maxCola = 20)
        cola.encolar(orden("a").copy(intento = 2), maxCola = 20)

        assertEquals(1, cola.tamano)
        assertEquals(2, cola.listar().first().intento)
    }

    @Test
    fun `la cola sobrevive a la muerte del proceso`() {
        ColaOrdenes(archivo).apply {
            encolar(orden("a"), maxCola = 20)
            encolar(orden("b"), maxCola = 20)
        }

        // Instancia nueva sobre el mismo archivo = servicio que volvió a levantar.
        val recuperada = ColaOrdenes(archivo)

        assertEquals(2, recuperada.tamano)
        assertEquals("a", recuperada.tomar()?.ordenId)
    }

    @Test
    fun `las ordenes vencidas se purgan al recuperar`() {
        ColaOrdenes(archivo).apply {
            encolar(orden("vieja", venceEn = 1_000), maxCola = 20)
            encolar(orden("vigente", venceEn = Long.MAX_VALUE), maxCola = 20)
        }

        val recuperada = ColaOrdenes(archivo)
        val vencidas = recuperada.purgarVencidas(ahora = 5_000)

        assertEquals(listOf("vieja"), vencidas.map { it.ordenId })
        assertEquals(1, recuperada.tamano)
        assertEquals("vigente", recuperada.tomar()?.ordenId)
    }
}

class ColaEnvioTest {

    private lateinit var dir: File

    private fun meta(sha: String = "a".repeat(64)) =
        MetadataImagen(ancho = 1920, alto = 1080, sha256 = sha, capturadaEn = 1)

    @Before
    fun preparar() {
        dir = Files.createTempDirectory("cola-envio").toFile()
    }

    @After
    fun limpiar() {
        dir.deleteRecursively()
    }

    @Test
    fun `guarda y devuelve los bytes tal cual`() {
        val cola = ColaEnvio(dir)
        val jpeg = byteArrayOf(1, 2, 3, 4, 5)

        cola.guardar("orden-1", jpeg, meta())

        assertEquals(1, cola.tamano)
        assertTrue(jpeg.contentEquals(cola.bytes("orden-1")))
        assertNotNull(cola.meta("orden-1"))
    }

    @Test
    fun `la cola sobrevive a la muerte del proceso`() {
        ColaEnvio(dir).guardar("orden-1", byteArrayOf(9, 9), meta())

        val recuperada = ColaEnvio(dir)

        assertEquals(1, recuperada.tamano)
        assertTrue(byteArrayOf(9, 9).contentEquals(recuperada.bytes("orden-1")))
    }

    @Test
    fun `al llegar al tope de cantidad descarta lo mas viejo`() {
        val cola = ColaEnvio(dir, maxItems = 3)
        repeat(4) { i ->
            cola.guardar("orden-$i", byteArrayOf(i.toByte()), meta())
            Thread.sleep(5) // el orden es por marca temporal
        }

        assertEquals(3, cola.tamano)
        assertNull(cola.bytes("orden-0"))
        assertNotNull(cola.bytes("orden-3"))
    }

    @Test
    fun `el desalojo se informa para poder registrarlo`() {
        val cola = ColaEnvio(dir, maxItems = 1)
        cola.guardar("vieja", byteArrayOf(1), meta())
        Thread.sleep(5)

        val desalojos = cola.guardar("nueva", byteArrayOf(2), meta())

        // Descartar en silencio haría creer que nunca se perdió nada.
        assertEquals(1, desalojos.size)
        assertEquals("vieja", desalojos.first().ordenId)
    }

    @Test
    fun `al llegar al tope de tamano descarta lo mas viejo`() {
        val cola = ColaEnvio(dir, maxItems = 100, maxBytes = 1_000)
        cola.guardar("a", ByteArray(600), meta())
        Thread.sleep(5)
        cola.guardar("b", ByteArray(600), meta())

        assertEquals(1, cola.tamano)
        assertNull(cola.bytes("a"))
        assertNotNull(cola.bytes("b"))
    }

    @Test
    fun `drenar borra la imagen entregada`() {
        val cola = ColaEnvio(dir)
        cola.guardar("orden-1", byteArrayOf(1), meta())

        cola.borrar("orden-1")

        assertEquals(0, cola.tamano)
        assertNull(cola.bytes("orden-1"))
    }

    @Test
    fun `un sidecar sin su jpeg se ignora`() {
        val cola = ColaEnvio(dir)
        cola.guardar("orden-1", byteArrayOf(1), meta())
        File(dir, "orden-1.jpg").delete()

        assertEquals(0, cola.tamano)
    }
}
