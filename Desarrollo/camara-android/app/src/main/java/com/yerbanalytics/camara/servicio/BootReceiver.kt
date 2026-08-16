package com.yerbanalytics.camara.servicio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Levanta el dispositivo solo tras un reinicio del teléfono, para que un corte de luz en el
 * vivero no deje la cámara muda hasta que alguien suba al riel a tocar la pantalla.
 *
 * El servicio puede arrancar acá en **modo degradado**: el sistema puede negarle el acceso a
 * cámara a un servicio que arrancó sin interfaz visible. En ese caso sostiene el canal e informa
 * honestamente que no puede capturar (ver [DispositivoService]).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val accion = intent.action ?: return
        if (accion != Intent.ACTION_BOOT_COMPLETED && accion != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        val arranque = Intent(context, DispositivoService::class.java).apply {
            action = DispositivoService.ACCION_ARRANCAR
            putExtra(DispositivoService.EXTRA_DESDE_ARRANQUE, true)
        }
        runCatching { ContextCompat.startForegroundService(context, arranque) }
    }
}
