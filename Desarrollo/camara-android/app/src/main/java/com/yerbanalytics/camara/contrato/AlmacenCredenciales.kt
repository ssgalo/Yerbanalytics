package com.yerbanalytics.camara.contrato

import android.content.Context
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

    private val kBaseUrl = stringPreferencesKey("base_url")
    private val kDispositivoId = stringPreferencesKey("dispositivo_id")
    private val kRefreshToken = stringPreferencesKey("refresh_token")

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

    /** Se invoca cuando el backend rechaza la credencial: da de baja al dispositivo. */
    suspend fun borrar() {
        context.dataStore.edit { it.clear() }
    }
}
