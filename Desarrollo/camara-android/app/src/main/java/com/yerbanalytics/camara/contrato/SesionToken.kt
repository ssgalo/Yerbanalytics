package com.yerbanalytics.camara.contrato

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Token de acceso de vida corta, en memoria, con renovación **proactiva**.
 *
 * El contrato pide renovar antes de la expiración y no esperar a comerse un 401. Se renueva a
 * los 2/3 de la vida declarada: deja margen para un reloj desfasado y para una red lenta sin
 * pedir un token nuevo en cada llamada.
 *
 * Ante un 401 al renovar, la credencial de renovación ya no sirve —dieron de baja el
 * dispositivo, o venció— y se avisa por [alRevocar] para que la app vuelva a pedir el código de
 * vinculación.
 */
class SesionToken(
    private val clienteSinAuth: ContratoClient,
    private val refreshToken: String,
    private val alRevocar: suspend (ErrorRespuesta) -> Unit,
    private val ahora: () -> Long = System::currentTimeMillis,
) : ProveedorToken {

    private val mutex = Mutex()
    private var token: String? = null
    private var renovarDespuesDe: Long = 0

    override suspend fun vigente(): String = mutex.withLock {
        val actual = token
        if (actual != null && ahora() < renovarDespuesDe) actual else pedir()
    }

    override suspend fun renovar(): String = mutex.withLock { pedir() }

    private suspend fun pedir(): String {
        try {
            val respuesta = clienteSinAuth.token(refreshToken)
            token = respuesta.accessToken
            renovarDespuesDe = ahora() + (respuesta.expiraEnSeg.coerceAtLeast(1) * 1000L * 2 / 3)
            return respuesta.accessToken
        } catch (e: ErrorRespuesta) {
            token = null
            renovarDespuesDe = 0
            if (e.codigo == 401) alRevocar(e)
            throw e
        }
    }
}
