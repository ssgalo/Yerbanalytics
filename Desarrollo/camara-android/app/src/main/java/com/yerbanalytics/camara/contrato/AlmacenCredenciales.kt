package com.yerbanalytics.camara.contrato

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "dispositivo")

data class Credencial(
    val baseUrl: String,
    val dispositivoId: String,
    val refreshToken: String,
)

/**
 * Claves de DataStore, en un objeto aparte de [AlmacenCredenciales] para que [mutarSoloUrl]
 * pueda testearse sin `Context`: las claves y el `MutablePreferences` de DataStore son clases
 * de JVM puro, y este proyecto no tiene Robolectric para instanciar un `Context` en tests.
 */
internal object ClavesCredencial {
    val BASE_URL = stringPreferencesKey("base_url")
    val DISPOSITIVO_ID = stringPreferencesKey("dispositivo_id")
    val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
}

/**
 * El cambio real de [AlmacenCredenciales.actualizarUrl]: toca sólo `base_url` en las
 * preferencias que ya entregó `DataStore.edit`. `dispositivo_id` y `refresh_token` ni se leen
 * ni se escriben, así que quedan como estaban por construcción, no por cuidado manual.
 */
internal fun mutarSoloUrl(prefs: MutablePreferences, nuevaUrl: String) {
    prefs[ClavesCredencial.BASE_URL] = nuevaUrl
}

/**
 * Credencial del dispositivo, persistida localmente.
 *
 * El código de una app distribuida es inspeccionable, así que **nada de larga duración se
 * escribe en el fuente**: el `refreshToken` se obtiene en tiempo de ejecución al enrolarse y
 * queda acá. El backend guarda sólo su huella y no puede devolverlo después.
 *
 * El token de acceso NO se persiste: dura 15 minutos y vive en memoria.
 *
 * La URL del backend también vive acá, y por eso cambiar de máquina o de IP no requiere
 * recompilar: se tipea al vincular.
 */
class AlmacenCredenciales(private val context: Context) {

    private val kBaseUrl = ClavesCredencial.BASE_URL
    private val kDispositivoId = ClavesCredencial.DISPOSITIVO_ID
    private val kRefreshToken = ClavesCredencial.REFRESH_TOKEN

    suspend fun leer(): Credencial? {
        val p = context.dataStore.data.first()
        val base = p[kBaseUrl] ?: return null
        val id = p[kDispositivoId] ?: return null
        val refresh = p[kRefreshToken] ?: return null
        if (base.isBlank() || refresh.isBlank()) return null
        return Credencial(base, id, refresh)
    }

    suspend fun guardar(credencial: Credencial) {
        context.dataStore.edit { p ->
            p[kBaseUrl] = credencial.baseUrl
            p[kDispositivoId] = credencial.dispositivoId
            p[kRefreshToken] = credencial.refreshToken
        }
    }

    /**
     * Actualiza SÓLO la URL del backend, conservando `dispositivo_id` y `refresh_token`.
     *
     * No es un caso particular de [guardar]: la gracia es justamente que no se toque el resto
     * de la credencial. El backend valida el `refreshToken` por su huella, no por la URL desde
     * la que llega (ver `SesionToken`), así que este cambio es puro acomodo de almacenamiento
     * del lado del cliente — para el backend sigue siendo el mismo dispositivo.
     *
     * No valida el formato de [nuevaUrl]: eso es responsabilidad de `ValidadorUrl`, antes de
     * llegar acá. Este método sólo persiste.
     */
    suspend fun actualizarUrl(nuevaUrl: String) {
        context.dataStore.edit { p -> mutarSoloUrl(p, nuevaUrl) }
    }

    /** Se invoca cuando el backend rechaza la credencial: da de baja al dispositivo. */
    suspend fun borrar() {
        context.dataStore.edit { it.clear() }
    }
}
