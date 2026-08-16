package com.yerbanalytics.camara.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.yerbanalytics.camara.contrato.AlmacenCredenciales
import com.yerbanalytics.camara.contrato.ContratoClient
import com.yerbanalytics.camara.contrato.Credencial
import com.yerbanalytics.camara.servicio.DispositivoService
import kotlinx.coroutines.launch

/**
 * Ventana al dispositivo. **No es el dispositivo**: puede morir sin que la captura se entere.
 *
 * Su trabajo real es el que no se puede hacer desde un servicio: pedir los permisos, arrancar el
 * servicio con la app visible —que es lo que le concede el acceso a cámara para después operar
 * con la pantalla apagada— y mostrar el log.
 */
class MainActivity : ComponentActivity() {

    private var servicio by mutableStateOf<DispositivoService?>(null)
    private var vinculando by mutableStateOf(false)
    private var errorVinculacion by mutableStateOf<String?>(null)
    private var yaVinculado by mutableStateOf(false)

    private lateinit var almacen: AlmacenCredenciales

    private val conexion = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            servicio = (binder as? DispositivoService.Enlace)?.servicio
            servicio?.promoverSiHaceFalta()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            servicio = null
        }
    }

    private val pedirPermisos = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { concedidos ->
        if (concedidos[Manifest.permission.CAMERA] == true) {
            // El servicio se arranca DESPUÉS de tener el permiso y con la app visible: así el
            // sistema le concede el tipo `camera` y lo conserva con la pantalla apagada.
            arrancarServicio()
        } else {
            arrancarServicio() // igual arranca: opera degradado y lo dice
        }
        proponerExencionDeBateria()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        almacen = AlmacenCredenciales(applicationContext)

        lifecycleScope.launch { yaVinculado = almacen.leer() != null }

        setContent {
            TemaCamara {
                val s = servicio
                val estado = s?.estado?.collectAsState()?.value
                val config = s?.config?.collectAsState()?.value
                val entradas = s?.registro?.entradas?.collectAsState()?.value.orEmpty()

                if (estado != null && estado.vinculado) {
                    PantallaOperacion(
                        estado = estado,
                        entradas = entradas,
                        resolucion = config?.let {
                            "${it.anchoMax}x${it.altoMax} · q${(it.calidadJpeg * 100).toInt()}"
                        } ?: "—",
                        onVisor = { proveedor -> s.visorVisible(proveedor) },
                        onPrueba = {
                            startService(
                                Intent(this, DispositivoService::class.java)
                                    .setAction(DispositivoService.ACCION_DISPARO_PRUEBA)
                            )
                        },
                        onExportarLog = { compartirLog() },
                    )
                } else {
                    PantallaVinculacion(
                        ocupado = vinculando,
                        error = errorVinculacion,
                        onVincular = ::vincular,
                    )
                }
            }
        }

        pedirPermisosNecesarios()
    }

    override fun onStart() {
        super.onStart()
        bindService(
            Intent(this, DispositivoService::class.java),
            conexion,
            Context.BIND_AUTO_CREATE,
        )
    }

    override fun onResume() {
        super.onResume()
        // Si el servicio quedó degradado tras un reinicio del teléfono, este es el momento en
        // que puede recuperar el acceso a cámara: la app está visible.
        servicio?.promoverSiHaceFalta()
    }

    override fun onStop() {
        runCatching { unbindService(conexion) }
        super.onStop()
    }

    // ------------------------------------------------------------------ Permisos

    private fun pedirPermisosNecesarios() {
        val faltantes = buildList {
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.CAMERA)
            }
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (faltantes.isEmpty()) {
            arrancarServicio()
            proponerExencionDeBateria()
        } else {
            pedirPermisos.launch(faltantes.toTypedArray())
        }
    }

    /**
     * Un servicio en primer plano no lo mata el sistema, pero Doze puede cortarle la red a un
     * teléfono quieto — y un teléfono montado en un riel está siempre quieto. Sin esta exención
     * el dispositivo deja de responder solo, y el síntoma no dice nada.
     */
    private fun proponerExencionDeBateria() {
        val power = getSystemService(PowerManager::class.java) ?: return
        if (power.isIgnoringBatteryOptimizations(packageName)) return
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName"),
                )
            )
        }
    }

    private fun arrancarServicio() {
        val intent = Intent(this, DispositivoService::class.java)
            .setAction(DispositivoService.ACCION_ARRANCAR)
        ContextCompat.startForegroundService(this, intent)
    }

    // ------------------------------------------------------------------ Vinculación

    private fun vincular(baseUrl: String, codigo: String, nombre: String) {
        vinculando = true
        errorVinculacion = null
        lifecycleScope.launch {
            val url = if (baseUrl.startsWith("http")) baseUrl else "http://$baseUrl"
            val plataforma = "Android ${Build.VERSION.RELEASE} (${Build.MANUFACTURER} ${Build.MODEL})"
            runCatching {
                ContratoClient(url, proveedor = null).enrolar(codigo, nombre, plataforma)
            }.onSuccess { respuesta ->
                almacen.guardar(Credencial(url, respuesta.dispositivoId, respuesta.refreshToken))
                yaVinculado = true
                servicio?.revincular() ?: arrancarServicio()
            }.onFailure { e ->
                errorVinculacion = when {
                    e.message?.contains("401") == true ->
                        "El código no existe, ya se usó o venció. Generá uno nuevo."
                    else -> "No se pudo contactar al backend: ${e.message}"
                }
            }
            vinculando = false
        }
    }

    private fun compartirLog() {
        val s = servicio ?: return
        lifecycleScope.launch {
            val contenido = runCatching { s.registro.exportar() }.getOrDefault("")
            val envio = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Log del dispositivo de captura")
                putExtra(Intent.EXTRA_TEXT, contenido.takeLast(400_000))
            }
            runCatching { startActivity(Intent.createChooser(envio, "Exportar log")) }
        }
    }
}
