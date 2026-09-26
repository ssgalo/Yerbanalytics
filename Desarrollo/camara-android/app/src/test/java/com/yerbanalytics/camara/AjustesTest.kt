package com.yerbanalytics.camara

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.yerbanalytics.camara.contrato.ClavesCredencial
import com.yerbanalytics.camara.contrato.ValidadorUrl
import com.yerbanalytics.camara.contrato.mutarSoloUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidadorUrlTest {

    @Test
    fun `acepta http con host y puerto`() {
        val r = ValidadorUrl.validar("http://192.168.1.100:8000") as ValidadorUrl.Resultado.Valida
        assertEquals("http://192.168.1.100:8000", r.normalizada)
    }

    @Test
    fun `acepta https sin puerto explicito`() {
        val r = ValidadorUrl.validar("https://vivero.local") as ValidadorUrl.Resultado.Valida
        assertEquals("https://vivero.local", r.normalizada)
    }

    @Test
    fun `recorta espacios y la barra final`() {
        val r = ValidadorUrl.validar("  http://192.168.1.100:8000/  ") as ValidadorUrl.Resultado.Valida
        assertEquals("http://192.168.1.100:8000", r.normalizada)
    }

    @Test
    fun `rechaza vacio`() {
        assertTrue(ValidadorUrl.validar("   ") is ValidadorUrl.Resultado.Invalida)
    }

    @Test
    fun `rechaza sin esquema`() {
        // Error típico: pegar sólo host y puerto, sin http:// adelante.
        assertTrue(ValidadorUrl.validar("192.168.1.100:8000") is ValidadorUrl.Resultado.Invalida)
    }

    @Test
    fun `rechaza esquema no http`() {
        assertTrue(ValidadorUrl.validar("ftp://192.168.1.100") is ValidadorUrl.Resultado.Invalida)
    }

    @Test
    fun `rechaza sin host`() {
        assertTrue(ValidadorUrl.validar("http://:8000") is ValidadorUrl.Resultado.Invalida)
    }

    @Test
    fun `rechaza puerto fuera de rango`() {
        assertTrue(ValidadorUrl.validar("http://192.168.1.100:99999") is ValidadorUrl.Resultado.Invalida)
    }

    @Test
    fun `rechaza url mal formada`() {
        assertTrue(ValidadorUrl.validar("http://[url-rota") is ValidadorUrl.Resultado.Invalida)
    }
}

class AlmacenCredencialesActualizarUrlTest {

    /**
     * Ejercita exactamente la función que usa `AlmacenCredenciales.actualizarUrl` por dentro
     * del `DataStore.edit`. No se testea `actualizarUrl` en sí porque requiere un `Context` de
     * Android real, y el proyecto no tiene Robolectric — pero `mutarSoloUrl` es la lógica real,
     * no una reimplementación paralela: opera sobre el mismo `MutablePreferences` que entrega
     * DataStore, con las mismas claves de [ClavesCredencial].
     */
    @Test
    fun `actualizar la URL conserva dispositivoId y refreshToken`() {
        val prefs = mutablePreferencesOf(
            ClavesCredencial.BASE_URL to "http://192.168.1.50:8000",
            ClavesCredencial.DISPOSITIVO_ID to "CAM-001",
            ClavesCredencial.REFRESH_TOKEN to "token-secreto",
        )

        mutarSoloUrl(prefs, "http://192.168.1.99:8000")

        assertEquals("http://192.168.1.99:8000", prefs[ClavesCredencial.BASE_URL])
        assertEquals("CAM-001", prefs[ClavesCredencial.DISPOSITIVO_ID])
        assertEquals("token-secreto", prefs[ClavesCredencial.REFRESH_TOKEN])
    }

    @Test
    fun `no toca ninguna otra clave`() {
        val prefs = mutablePreferencesOf(
            ClavesCredencial.BASE_URL to "http://viejo:8000",
            ClavesCredencial.DISPOSITIVO_ID to "CAM-002",
            ClavesCredencial.REFRESH_TOKEN to "abc",
        )
        val clavesAntes = prefs.asMap().keys

        mutarSoloUrl(prefs, "http://nuevo:9000")

        assertEquals(clavesAntes, prefs.asMap().keys)
    }
}
