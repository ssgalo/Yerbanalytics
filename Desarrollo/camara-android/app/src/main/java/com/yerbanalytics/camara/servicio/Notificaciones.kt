package com.yerbanalytics.camara.servicio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.yerbanalytics.camara.R
import com.yerbanalytics.camara.ui.MainActivity

/**
 * Notificación permanente del dispositivo.
 *
 * No es decorativa: es lo que sostiene vivo al servicio en primer plano, y es la única
 * superficie visible cuando el teléfono está montado en el riel con la pantalla apagada. Por eso
 * dice el estado real, incluido el modo degradado.
 */
object Notificaciones {

    const val CANAL = "estado_dispositivo"
    const val ID = 1

    fun crearCanal(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val canal = NotificationChannel(
            CANAL,
            context.getString(R.string.canal_notificaciones),
            // Baja: tiene que estar siempre, no tiene que sonar nunca.
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.canal_notificaciones_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(canal)
    }

    fun construir(context: Context, titulo: String, detalle: String): Notification {
        val abrir = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(context, CANAL)
            .setContentTitle(titulo)
            .setContentText(detalle)
            .setStyle(Notification.BigTextStyle().bigText(detalle))
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentIntent(abrir)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }
}
