package com.yerbanalytics.camara.servicio

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import androidx.camera.core.Preview
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.yerbanalytics.camara.captura.CapturaController
import com.yerbanalytics.camara.captura.ResultadoCaptura
import com.yerbanalytics.camara.captura.Sha256
import com.yerbanalytics.camara.colas.ColaEnvio
import com.yerbanalytics.camara.colas.ColaOrdenes
import com.yerbanalytics.camara.colas.ResultadoEncolar
import com.yerbanalytics.camara.contrato.AlmacenCredenciales
import com.yerbanalytics.camara.contrato.Backoff
import com.yerbanalytics.camara.contrato.CanalOrdenes
import com.yerbanalytics.camara.contrato.ConfigCaptura
import com.yerbanalytics.camara.contrato.ContratoClient
import com.yerbanalytics.camara.contrato.Credencial
import com.yerbanalytics.camara.contrato.EventoCanal
import com.yerbanalytics.camara.contrato.Heartbeat
import com.yerbanalytics.camara.contrato.MetadataImagen
import com.yerbanalytics.camara.contrato.MotivoFallo
import com.yerbanalytics.camara.contrato.Orden
import com.yerbanalytics.camara.contrato.ResultadoSubida
import com.yerbanalytics.camara.contrato.SesionToken
import com.yerbanalytics.camara.registro.Registro
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

data class EstadoDispositivo(
    val vinculado: Boolean = false,
    val dispositivoId: String = "",
    val canal: CanalOrdenes.Estado = CanalOrdenes.Estado.DESCONECTADO,
    val camaraAbierta: Boolean = false,
    val capturando: Boolean = false,
    val capturasOk: Int = 0,
    val capturasError: Int = 0,
    val pendientesEnvio: Int = 0,
    val ordenesEnCola: Int = 0,
    /** El sistema le negó el acceso a cámara al servicio: opera sin poder capturar. */
    val degradado: Boolean = false,
    val detalle: String = "",
) {
    val puedeCapturar: Boolean get() = vinculado && !degradado
}

/**
 * **El servicio ES el dispositivo.** La Activity es apenas una ventana que se asoma.
 *
 * Todo el estado operativo —credenciales, canal, colas, heartbeat, captura— vive acá, en un
 * servicio en primer plano que sobrevive a que la interfaz desaparezca. Es la inversión exacta
 * respecto de la PWA, donde la pestaña visible *era* el dispositivo y el sistema operativo
 * suspendía la cámara al perder el foco.
 *
 * El tipo `camera` se declara al arrancar **con la app visible**: ese es el momento en que el
 * sistema concede el acceso a cámara "en uso", y una vez concedido el servicio lo conserva
 * mientras viva, aunque la pantalla se apague. Declarar el tipo NO abre la cámara: el hardware
 * sigue apagado hasta que llega una orden.
 */
class DispositivoService : LifecycleService() {

    companion object {
        const val ACCION_ARRANCAR = "com.yerbanalytics.camara.ARRANCAR"
        const val ACCION_DETENER = "com.yerbanalytics.camara.DETENER"
        const val ACCION_DISPARO_PRUEBA = "com.yerbanalytics.camara.DISPARO_PRUEBA"
        const val EXTRA_DESDE_ARRANQUE = "desde_arranque"

        /**
         * Cuánto se sostiene la cámara abierta sin trabajo antes de cerrarla. Configurable, no
         * fijo: el valor bueno depende de la cadencia real del riel, que todavía no existe.
         */
        const val VENTANA_INACTIVIDAD_MS = 12_000L

        private const val MAX_REINTENTOS_ENVIO = 4
    }

    inner class Enlace : Binder() {
        val servicio: DispositivoService get() = this@DispositivoService
    }

    private val enlace = Enlace()

    lateinit var registro: Registro
        private set

    private lateinit var almacen: AlmacenCredenciales
    private lateinit var colaOrdenes: ColaOrdenes
    private lateinit var colaEnvio: ColaEnvio
    private lateinit var camara: CapturaController

    private var cliente: ContratoClient? = null
    private var canal: CanalOrdenes? = null
    private var sesion: SesionToken? = null

    private val _estado = MutableStateFlow(EstadoDispositivo())
    val estado: StateFlow<EstadoDispositivo> = _estado.asStateFlow()

    private val _config = MutableStateFlow(ConfigCaptura())
    val config: StateFlow<ConfigCaptura> = _config.asStateFlow()

    private val hayTrabajo = Channel<Unit>(Channel.CONFLATED)
    private val drenajeMutex = Mutex()

    private var bucles = mutableListOf<Job>()
    private var cierrePorInactividad: Job? = null
    private var arrancado = false

    // ------------------------------------------------------------------ Ciclo de vida

    override fun onCreate() {
        super.onCreate()
        registro = Registro(File(filesDir, "logs"))
        almacen = AlmacenCredenciales(applicationContext)
        colaOrdenes = ColaOrdenes(File(filesDir, "ordenes.json"))
        colaEnvio = ColaEnvio(File(filesDir, "pendientes"))
        camara = CapturaController(applicationContext, this, registro)

        Notificaciones.crearCanal(this)
        entrarEnPrimerPlano()

        lifecycleScope.launch {
            camara.abierta.collect { abierta -> _estado.value = _estado.value.copy(camaraAbierta = abierta) }
        }
        lifecycleScope.launch {
            camara.capturando.collect { c -> _estado.value = _estado.value.copy(capturando = c) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACCION_DETENER -> {
                registro.aviso("Dispositivo detenido a pedido del operario")
                stopSelf()
                return START_NOT_STICKY
            }

            ACCION_DISPARO_PRUEBA -> lifecycleScope.launch { disparoDePrueba() }

            else -> {
                if (intent?.getBooleanExtra(EXTRA_DESDE_ARRANQUE, false) == true) {
                    registro.info("Arranque tras reinicio del teléfono")
                }
                arrancar()
            }
        }
        // START_STICKY: si el sistema mata el proceso por memoria, que lo vuelva a levantar.
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return enlace
    }

    override fun onDestroy() {
        bucles.forEach { it.cancel() }
        super.onDestroy()
    }

    // ------------------------------------------------------------------ Primer plano

    /**
     * Entra en primer plano pidiendo el tipo `camera`. Si el sistema lo niega —típicamente por
     * haber arrancado sin interfaz visible tras un reinicio— entra igual como `specialUse` y
     * queda **degradado**: sostiene el canal, pero informa honestamente que no puede capturar.
     *
     * Nunca reporta que puede capturar si no puede: el backend deriva el estado del dispositivo
     * de ese campo, y mentirle sería peor que estar caído.
     */
    private fun entrarEnPrimerPlano(degradar: Boolean = false) {
        val quiereCamara = !degradar && tienePermisoCamara()
        val tipo = when {
            Build.VERSION.SDK_INT >= 34 && quiereCamara ->
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            Build.VERSION.SDK_INT >= 34 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            Build.VERSION.SDK_INT >= 29 && quiereCamara -> ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            else -> 0
        }
        try {
            ServiceCompat.startForeground(this, Notificaciones.ID, notificacionActual(), tipo)
            if (!quiereCamara) marcarDegradado("sin acceso a cámara concedido al servicio")
        } catch (e: Exception) {
            if (quiereCamara) {
                // El sistema negó el tipo cámara: entrar degradado en vez de no entrar.
                entrarEnPrimerPlano(degradar = true)
            } else {
                registro.error("No se pudo entrar en primer plano: ${e.message}")
            }
        }
    }

    /**
     * Al abrirse la interfaz, el servicio recupera el acceso a cámara y sale del modo degradado
     * sin volver a vincularse.
     */
    fun promoverSiHaceFalta() {
        if (!_estado.value.degradado) return
        if (!tienePermisoCamara()) return
        runCatching {
            ServiceCompat.startForeground(
                this,
                Notificaciones.ID,
                notificacionActual(),
                if (Build.VERSION.SDK_INT >= 34) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                },
            )
            _estado.value = _estado.value.copy(degradado = false, detalle = "")
            registro.exito("Acceso a cámara recuperado: el dispositivo opera normalmente")
            actualizarNotificacion()
        }
    }

    private fun marcarDegradado(motivo: String) {
        if (_estado.value.degradado) return
        _estado.value = _estado.value.copy(degradado = true, detalle = motivo)
        registro.aviso("Modo degradado ($motivo): abrí la app una vez para habilitar la cámara")
        actualizarNotificacion()
    }

    private fun tienePermisoCamara() = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.CAMERA,
    ) == PackageManager.PERMISSION_GRANTED

    private fun notificacionActual(): android.app.Notification {
        val e = _estado.value
        val titulo = when {
            !e.vinculado -> "Sin vincular"
            e.degradado -> "Degradado · abrí la app una vez"
            e.canal == CanalOrdenes.Estado.CONECTADO -> "En servicio"
            e.canal == CanalOrdenes.Estado.RECONECTANDO -> "Reconectando…"
            else -> "Desconectado"
        }
        val detalle = buildString {
            append("${e.capturasOk} capturas · ${e.capturasError} fallidas")
            if (e.pendientesEnvio > 0) append(" · ${e.pendientesEnvio} sin enviar")
            if (e.detalle.isNotBlank()) append("\n${e.detalle}")
        }
        return Notificaciones.construir(this, titulo, detalle)
    }

    private fun actualizarNotificacion() {
        runCatching {
            val manager = getSystemService(android.app.NotificationManager::class.java)
            manager?.notify(Notificaciones.ID, notificacionActual())
        }
    }

    // ------------------------------------------------------------------ Arranque

    private fun arrancar() {
        if (arrancado) return
        arrancado = true
        lifecycleScope.launch {
            val credencial = almacen.leer()
            if (credencial == null) {
                registro.aviso("Sin credencial: hay que vincular el dispositivo")
                _estado.value = _estado.value.copy(vinculado = false)
                actualizarNotificacion()
                arrancado = false
                return@launch
            }
            iniciarOperacion(credencial)
        }
    }

    private suspend fun iniciarOperacion(credencial: Credencial) {
        registro.info("Dispositivo ${credencial.dispositivoId} contra ${credencial.baseUrl}")

        val sinAuth = ContratoClient(credencial.baseUrl, proveedor = null)
        val nuevaSesion = SesionToken(
            clienteSinAuth = sinAuth,
            refreshToken = credencial.refreshToken,
            alRevocar = {
                registro.error("El backend rechazó la credencial: hay que volver a vincular")
                almacen.borrar()
                _estado.value = _estado.value.copy(vinculado = false, detalle = "credencial rechazada")
                actualizarNotificacion()
            },
        )
        val nuevoCliente = ContratoClient(credencial.baseUrl, nuevaSesion)
        sesion = nuevaSesion
        cliente = nuevoCliente

        _estado.value = _estado.value.copy(
            vinculado = true,
            dispositivoId = credencial.dispositivoId,
            pendientesEnvio = colaEnvio.tamano,
            ordenesEnCola = colaOrdenes.tamano,
        )

        // Órdenes que sobrevivieron a la muerte del proceso: las vigentes se retoman, las
        // vencidas se descartan dejando constancia.
        colaOrdenes.purgarVencidas(System.currentTimeMillis()).forEach {
            registro.aviso("Orden ${it.ordenId} recuperada pero ya vencida: se descarta")
        }
        if (colaOrdenes.tamano > 0) {
            registro.info("${colaOrdenes.tamano} órdenes recuperadas del disco: se retoman")
            hayTrabajo.trySend(Unit)
        }
        if (colaEnvio.tamano > 0) {
            registro.info("${colaEnvio.tamano} imágenes pendientes de entrega: se reintentan")
        }

        runCatching { _config.value = nuevoCliente.config() }
            .onSuccess { registro.info("Configuración: ${resumen(_config.value)}") }
            .onFailure { registro.aviso("No se pudo leer la configuración: ${it.message}") }

        val nuevoCanal = CanalOrdenes(nuevoCliente, nuevaSesion, registro, { _config.value })
        canal = nuevoCanal

        bucles += nuevoCanal.iniciar(lifecycleScope) { evento -> manejar(evento) }
        bucles += lifecycleScope.launch {
            nuevoCanal.estado.collect { e ->
                _estado.value = _estado.value.copy(canal = e)
                actualizarNotificacion()
            }
        }
        bucles += lanzarBucleCaptura()
        bucles += lanzarBucleHeartbeat()
        bucles += lanzarBucleDrenaje()
        actualizarNotificacion()
    }

    private fun resumen(c: ConfigCaptura) =
        "${c.anchoMax}x${c.altoMax} · calidad ${(c.calidadJpeg * 100).toInt()} · " +
            "warmup ${c.warmupMs}ms · heartbeat ${c.heartbeatSeg}s · cola ${c.maxColaOrdenes}"

    // ------------------------------------------------------------------ Canal

    private fun manejar(evento: EventoCanal) {
        when (evento) {
            is EventoCanal.ConfigRecibida -> {
                _config.value = evento.config
                registro.info("Configuración nueva: ${resumen(evento.config)}")
            }

            is EventoCanal.OrdenRecibida -> encolar(evento.orden)
            else -> Unit
        }
    }

    private fun encolar(orden: Orden) {
        registro.info(
            "Orden ${orden.ordenId.take(8)} · sector ${orden.sectorId} · " +
                "riel ${orden.posicionRiel} · intento ${orden.intento}"
        )
        when (val r = colaOrdenes.encolar(orden, _config.value.maxColaOrdenes)) {
            is ResultadoEncolar.Aceptada -> Unit
            is ResultadoEncolar.Descartada -> {
                // Nunca en silencio: si el backend no se entera, la orden espera su vencimiento
                // en vez de reintentarse enseguida.
                registro.aviso("Cola llena: se descarta la orden ${r.descartada.ordenId.take(8)}")
                lifecycleScope.launch { acusar(r.descartada.ordenId, MotivoFallo.COLA_LLENA) }
            }
        }
        _estado.value = _estado.value.copy(ordenesEnCola = colaOrdenes.tamano)
        hayTrabajo.trySend(Unit)
    }

    // ------------------------------------------------------------------ Captura

    private fun lanzarBucleCaptura(): Job = lifecycleScope.launch {
        while (isActive) {
            val orden = colaOrdenes.tomar()
            if (orden == null) {
                programarCierrePorInactividad()
                hayTrabajo.receive()
                continue
            }
            cierrePorInactividad?.cancel()
            _estado.value = _estado.value.copy(ordenesEnCola = colaOrdenes.tamano)
            procesar(orden)
        }
    }

    private suspend fun procesar(orden: Orden) {
        if (orden.vencida(System.currentTimeMillis())) {
            registro.aviso("Orden ${orden.ordenId.take(8)} ya vencida: no se captura")
            return
        }
        if (!_estado.value.puedeCapturar) {
            registro.aviso("Orden ${orden.ordenId.take(8)} sin cámara disponible")
            acusar(orden.ordenId, MotivoFallo.CAMARA_NO_LISTA, _estado.value.detalle)
            contarError()
            return
        }

        conWakeLock {
            when (val resultado = camara.capturar(_config.value)) {
                is ResultadoCaptura.Fallo -> {
                    acusar(orden.ordenId, resultado.motivo, resultado.detalle)
                    contarError()
                }

                is ResultadoCaptura.Ok -> {
                    val meta = MetadataImagen(
                        ancho = resultado.ancho,
                        alto = resultado.alto,
                        sha256 = Sha256.hex(resultado.jpeg),
                        capturadaEn = resultado.capturadaEn,
                        constraints = resultado.ajustes,
                    )
                    // Primero a disco, después a la red: si el proceso muere en el medio, la
                    // imagen no se pierde.
                    colaEnvio.guardar(orden.ordenId, resultado.jpeg, meta).forEach { d ->
                        registro.aviso("Se desaloja ${d.ordenId.take(8)} por ${d.motivo}")
                        acusar(d.ordenId, MotivoFallo.ENVIO_AGOTADO, "desalojada: ${d.motivo}")
                        contarError()
                    }
                    _estado.value = _estado.value.copy(pendientesEnvio = colaEnvio.tamano)
                    drenar()
                }
            }
        }
    }

    private fun programarCierrePorInactividad() {
        cierrePorInactividad?.cancel()
        cierrePorInactividad = lifecycleScope.launch {
            delay(VENTANA_INACTIVIDAD_MS)
            camara.cerrar()
        }
    }

    private suspend fun disparoDePrueba() {
        if (!tienePermisoCamara()) {
            registro.error("Disparo de prueba: falta el permiso de cámara")
            return
        }
        registro.info("Disparo de prueba (no se envía a ningún lado)")
        when (val r = camara.capturar(_config.value)) {
            is ResultadoCaptura.Ok ->
                registro.exito("Prueba OK · ${r.ancho}x${r.alto} · ${r.jpeg.size / 1024} kB")
            is ResultadoCaptura.Fallo ->
                registro.error("Prueba fallida · ${r.motivo} · ${r.detalle}")
        }
        programarCierrePorInactividad()
    }

    // ------------------------------------------------------------------ Envío

    private fun lanzarBucleDrenaje(): Job = lifecycleScope.launch {
        while (isActive) {
            delay(30_000)
            if (colaEnvio.tamano > 0) drenar()
        }
    }

    private suspend fun drenar() = drenajeMutex.withLock {
        val c = cliente ?: return@withLock
        var intento = 0
        while (intento <= MAX_REINTENTOS_ENVIO) {
            val pendiente = colaEnvio.listar().firstOrNull() ?: break
            val bytes = colaEnvio.bytes(pendiente.ordenId)
            if (bytes == null) {
                colaEnvio.borrar(pendiente.ordenId)
                continue
            }

            when (val r = c.subirImagen(pendiente.ordenId, bytes, pendiente.meta)) {
                is ResultadoSubida.Ok -> {
                    colaEnvio.borrar(pendiente.ordenId)
                    contarOk()
                    intento = 0
                    if (r.yaExistia) {
                        // 409: una subida anterior sí llegó y se perdió la respuesta. El
                        // contrato manda tratarlo como éxito; contarlo como fallo produciría
                        // duplicados o errores falsos ante un corte de red en el momento justo.
                        registro.info("La orden ya tenía imagen (409): se cuenta como éxito")
                    } else {
                        registro.exito("Imagen entregada · captura ${r.captura.capturaId}")
                    }
                }

                is ResultadoSubida.Descartar -> {
                    // La orden no existe, es de otro dispositivo, pesa de más o el hash no dio.
                    registro.error("Imagen descartada (${r.codigo}): ${r.mensaje}")
                    colaEnvio.borrar(pendiente.ordenId)
                    contarError()
                    intento = 0
                }

                is ResultadoSubida.Reintentar -> {
                    colaEnvio.anotarIntento(pendiente.ordenId)
                    intento++
                    if (intento > MAX_REINTENTOS_ENVIO) {
                        // No se acusa ENVIO_AGOTADO: la imagen sigue en disco y se reintenta al
                        // volver la red. Acusar acá haría que el backend reentregue la orden
                        // mientras todavía tenemos la foto, y terminaríamos capturando dos veces.
                        registro.aviso(
                            "Envío pospuesto tras $MAX_REINTENTOS_ENVIO intentos (${r.motivo}): " +
                                "queda en disco"
                        )
                        break
                    }
                    val espera = Backoff.esperaMs(intento)
                    registro.aviso("Envío falló (${r.motivo}): reintento en ${espera / 1000}s")
                    delay(espera)
                }
            }
            _estado.value = _estado.value.copy(pendientesEnvio = colaEnvio.tamano)
            actualizarNotificacion()
        }
    }

    private suspend fun acusar(ordenId: String, motivo: MotivoFallo, detalle: String? = null) {
        val c = cliente ?: return
        runCatching { c.acusarFallo(ordenId, motivo, detalle) }
            .onSuccess { registro.info("Fallo acusado: $motivo") }
            .onFailure { registro.error("No se pudo acusar el fallo: ${it.message}") }
    }

    // ------------------------------------------------------------------ Señal de vida

    private fun lanzarBucleHeartbeat(): Job = lifecycleScope.launch {
        while (isActive) {
            val c = cliente
            val e = _estado.value
            if (c != null && e.vinculado) {
                runCatching {
                    c.heartbeat(
                        Heartbeat(
                            // Honestidad: si no puede capturar, lo dice. El backend deriva el
                            // estado del dispositivo de este campo.
                            capturaListo = e.puedeCapturar,
                            capturasOk = e.capturasOk,
                            capturasError = e.capturasError,
                            pendientesEnvio = colaEnvio.tamano,
                            detalle = e.detalle.ifBlank { null },
                        )
                    )
                }.onFailure { registro.aviso("Heartbeat falló: ${it.message}") }
            }
            delay(_config.value.heartbeatSeg.coerceAtLeast(5) * 1_000L)
        }
    }

    // ------------------------------------------------------------------ Utilidades

    /**
     * Un servicio en primer plano no lo mata el sistema, pero el CPU sí puede dormirse en medio
     * de una captura o de una subida. El WakeLock cubre sólo esa ventana, no todo el día.
     */
    private suspend fun conWakeLock(bloque: suspend () -> Unit) {
        val power = getSystemService(PowerManager::class.java)
        val lock = power?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "yerbanalytics:captura")
        runCatching { lock?.acquire(2 * 60 * 1000L) }
        try {
            bloque()
        } finally {
            runCatching { if (lock?.isHeld == true) lock.release() }
        }
    }

    private fun contarOk() {
        _estado.value = _estado.value.copy(capturasOk = _estado.value.capturasOk + 1)
    }

    private fun contarError() {
        _estado.value = _estado.value.copy(capturasError = _estado.value.capturasError + 1)
        actualizarNotificacion()
    }

    // ------------------------------------------------------------------ API para la UI

    /** La interfaz apareció: se ata el visor y se recupera la cámara si estaba degradado. */
    fun visorVisible(proveedor: Preview.SurfaceProvider?) {
        lifecycleScope.launch { camara.usarVisor(proveedor) }
    }

    /** Tras vincular desde la interfaz, el servicio arranca la operación sin reiniciarse. */
    fun revincular() {
        arrancado = false
        bucles.forEach { it.cancel() }
        bucles = mutableListOf()
        arrancar()
    }
}
