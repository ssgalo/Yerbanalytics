package com.yerbanalytics.camara.captura

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import com.yerbanalytics.camara.contrato.ConfigCaptura
import com.yerbanalytics.camara.contrato.MotivoFallo
import com.yerbanalytics.camara.registro.Registro
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface ResultadoCaptura {
    data class Ok(
        val jpeg: ByteArray,
        val ancho: Int,
        val alto: Int,
        val capturadaEn: Long,
        val ajustes: Map<String, String>,
    ) : ResultadoCaptura

    data class Fallo(val motivo: MotivoFallo, val detalle: String) : ResultadoCaptura
}

/**
 * Cámara del dispositivo, en **standby por ráfaga**.
 *
 * En reposo no hay ningún caso de uso atado: el hardware está apagado y el indicador de cámara
 * en uso del sistema, apagado también. Ante la primera orden se abre, se mantiene abierta
 * mientras siga llegando trabajo, y se cierra al vencer una ventana de inactividad.
 *
 * **Por qué no se abre y cierra por foto.** Una pasada de riel son 600 órdenes seguidas. Abrir y
 * cerrar 600 veces cuesta latencia y, sobre todo, reinicia el 3A (exposición, foco y balance de
 * blancos) en cada disparo — que es lo que más ensucia la consistencia fotométrica entre
 * imágenes de la misma pasada. Con la sesión sostenida, los sectores contiguos comparten
 * convergencia.
 *
 * **Por qué no se sostiene siempre.** Entre pasadas —donde el teléfono pasa la mayor parte del
 * día— tener la cámara prendida consume batería y deja el indicador encendido sin motivo.
 *
 * **El calentamiento es por convergencia, no por reloj.** El `warmupMs` del contrato existe
 * porque en un canvas de navegador no hay forma de saber si la exposición convergió y hay que
 * descartar fotogramas por tiempo. CameraX sí lo sabe: se pide una medición y se espera su
 * resultado, usando `warmupMs` como **techo**. Normalmente sale antes, y en la segunda captura
 * de una ráfaga la convergencia ya está hecha.
 */
class CapturaController(
    private val context: Context,
    private val duenio: LifecycleOwner,
    private val registro: Registro,
) {

    private val ejecutorCaptura = Executors.newSingleThreadExecutor { r ->
        Thread(r, "captura-yerbanalytics").apply { isDaemon = true }
    }

    private var proveedor: ProcessCameraProvider? = null
    private var camara: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var preview: Preview? = null

    /** Configuración con la que se ató la sesión actual, para detectar cuándo quedó obsoleta. */
    private var configAtada: ConfigCaptura? = null

    /** El visor que ofrece la interfaz ahora mismo (null si la pantalla no está visible). */
    private var proveedorSuperficie: Preview.SurfaceProvider? = null

    /**
     * El visor que quedó **realmente atado** en la sesión viva. Puede diferir del anterior: el
     * visor aparece y desaparece con la pantalla, y la sesión sólo lo incorpora al (re)atarse.
     * Sin este dato, una sesión atada a ciegas nunca se enteraba de que después apareció un visor.
     */
    private var visorAtado: Preview.SurfaceProvider? = null
    private var convergido = false

    private val _abierta = MutableStateFlow(false)
    val abierta: StateFlow<Boolean> = _abierta.asStateFlow()

    private val _capturando = MutableStateFlow(false)
    val capturando: StateFlow<Boolean> = _capturando.asStateFlow()

    /**
     * El visor sólo existe cuando la pantalla está visible. Con la pantalla apagada se ata
     * únicamente [ImageCapture]: CameraX no necesita una superficie de dibujo para capturar, y
     * no habría dónde dibujarla.
     */
    suspend fun usarVisor(nuevo: Preview.SurfaceProvider?) {
        if (proveedorSuperficie === nuevo) return
        proveedorSuperficie = nuevo
        // Nunca se rearma en medio de un disparo. Si la cámara está capturando, la diferencia
        // entre el visor ofrecido y el atado queda pendiente y la resuelve `capturar` al terminar.
        if (_abierta.value && !_capturando.value) rearmarVisor()
    }

    suspend fun capturar(config: ConfigCaptura): ResultadoCaptura {
        _capturando.value = true
        try {
            val captura = try {
                atar(config, forzar = false)
            } catch (e: Exception) {
                registro.error("No se pudo abrir la cámara: ${e.message}")
                return ResultadoCaptura.Fallo(
                    MotivoFallo.CAMARA_NO_LISTA,
                    e.message ?: e::class.java.simpleName,
                )
            }

            esperarConvergencia(config.warmupMs.toLong())

            return try {
                val jpeg = disparar(captura)
                val (ancho, alto) = dimensionesDe(jpeg)
                registro.exito("Captura lista: ${ancho}x$alto · ${jpeg.size / 1024} kB")
                ResultadoCaptura.Ok(
                    jpeg = jpeg,
                    ancho = ancho,
                    alto = alto,
                    capturadaEn = System.currentTimeMillis(),
                    ajustes = ajustesEfectivos(),
                )
            } catch (e: Exception) {
                registro.error("La captura no pudo exportarse: ${e.message}")
                ResultadoCaptura.Fallo(
                    MotivoFallo.EXPORTACION_FALLIDA,
                    e.message ?: e::class.java.simpleName,
                )
            }
        } finally {
            _capturando.value = false
            // El visor pudo aparecer —o irse— mientras el disparo estaba en curso: recién ahora
            // es seguro rearmar la sesión para que la incorpore. Si la corrutina se canceló no se
            // toca nada: rearmar exige suspender, y ya no hay dónde.
            if (_abierta.value && currentCoroutineContext().isActive) rearmarVisor()
        }
    }

    /**
     * Vuelve a atar la sesión si el visor ofrecido no es el que quedó atado. Es una operación
     * cara —reinicia el 3A— así que sólo ocurre cuando la diferencia existe de verdad.
     */
    private suspend fun rearmarVisor() {
        if (visorAtado === proveedorSuperficie) return
        val config = configAtada ?: return
        runCatching { atar(config, forzar = true) }
            .onFailure { registro.error("No se pudo rearmar el visor: ${it.message}") }
    }

    /** Cierra la cámara y libera el hardware. Lo invoca el servicio al vencer la inactividad. */
    suspend fun cerrar() = withContext(Dispatchers.Main) {
        if (!_abierta.value) return@withContext
        runCatching { proveedor?.unbindAll() }
        camara = null
        imageCapture = null
        preview = null
        configAtada = null
        visorAtado = null
        convergido = false
        _abierta.value = false
        registro.info("Cámara cerrada por inactividad: el hardware queda libre")
    }

    // ------------------------------------------------------------------ Interno

    private suspend fun atar(config: ConfigCaptura, forzar: Boolean): ImageCapture =
        withContext(Dispatchers.Main) {
            val actual = imageCapture
            val visorAlDia = visorAtado === proveedorSuperficie
            if (!forzar && actual != null && !sesionObsoleta(config) && visorAlDia) {
                return@withContext actual
            }

            val cp = proveedor ?: ProcessCameraProvider.getInstance(context).esperar().also {
                proveedor = it
            }

            if (actual != null) {
                val porque = if (visorAlDia) "la configuración cambió" else "cambió el visor"
                registro.info("Se rearma la sesión de cámara: $porque")
            }
            cp.unbindAll()
            convergido = false

            val nuevo = construirImageCapture(config)
            val visor = proveedorSuperficie
            val casos = buildList {
                add(nuevo)
                if (visor != null) {
                    val p = Preview.Builder().build().apply { surfaceProvider = visor }
                    preview = p
                    add(p)
                } else {
                    preview = null
                }
            }.toTypedArray()

            camara = cp.bindToLifecycle(duenio, CameraSelector.DEFAULT_BACK_CAMERA, *casos)
            imageCapture = nuevo
            configAtada = config
            visorAtado = visor
            _abierta.value = true

            val pedida = resolucionPedida(config)
            registro.info(
                "Cámara abierta · pedida ${pedida.width}x${pedida.height} · " +
                    "calidad ${(config.calidadJpeg * 100).toInt()}" +
                    if (visor != null) " · con visor" else ""
            )
            nuevo
        }

    /**
     * La resolución es parte del modo de captura y la calidad se fija al construir el caso de
     * uso: ninguna de las dos se puede cambiar sobre una sesión viva. Un cambio de config marca
     * la sesión como obsoleta y la próxima orden la vuelve a atar, sin reinicio ni intervención
     * física sobre el teléfono.
     */
    private fun sesionObsoleta(config: ConfigCaptura): Boolean {
        val atada = configAtada ?: return true
        return atada.anchoMax != config.anchoMax ||
            atada.altoMax != config.altoMax ||
            atada.calidadJpeg != config.calidadJpeg
    }

    private fun construirImageCapture(config: ConfigCaptura): ImageCapture {
        val objetivo = resolucionPedida(config)
        val selector = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(objetivo, ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
            )
            .build()
        return ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setResolutionSelector(selector)
            .setJpegQuality((config.calidadJpeg * 100).toInt().coerceIn(1, 100))
            .build()
    }

    /**
     * El límite del backend viene expresado en apaisado (1920x1080), pero el sensor puede
     * entregar en otra orientación. Se compara lado largo con lado largo y corto con corto, para
     * no tirar resolución por una diferencia de orientación.
     */
    private fun resolucionPedida(config: ConfigCaptura): Size {
        val largo = maxOf(config.anchoMax, config.altoMax)
        val corto = minOf(config.anchoMax, config.altoMax)
        return Size(largo, corto)
    }

    /**
     * Espera a que converjan exposición, foco y balance de blancos, con [techoMs] como plazo
     * máximo. Si no converge, dispara igual: una foto con la exposición sin cerrar es mejor que
     * ninguna, y el contrato le da 60 segundos a la orden.
     *
     * **El fracaso no se cachea.** [convergido] se levanta sólo cuando la medición terminó de
     * verdad: si venció el plazo, la próxima captura de la ráfaga vuelve a intentar. Dar por
     * convergida una sesión que no convergió arrastraba el error a las 600 fotos de la pasada —
     * la primera salía con la exposición abierta y ninguna de las siguientes reintentaba, que es
     * exactamente lo contrario de la consistencia fotométrica que la ráfaga viene a conseguir.
     */
    private suspend fun esperarConvergencia(techoMs: Long) {
        if (convergido) return
        val control = camara?.cameraControl ?: return
        val inicio = System.currentTimeMillis()
        val punto = SurfaceOrientedMeteringPointFactory(1f, 1f).createPoint(0.5f, 0.5f)
        val accion = FocusMeteringAction.Builder(
            punto,
            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB,
        ).disableAutoCancel().build()

        val resultado = withTimeoutOrNull(techoMs.coerceAtLeast(50)) {
            runCatching { control.startFocusAndMetering(accion).esperar() }.getOrNull()
        }
        val transcurrido = System.currentTimeMillis() - inicio
        if (resultado == null) {
            registro.aviso(
                "3A no convergió en ${techoMs}ms: se dispara igual y se reintenta en la próxima"
            )
        } else {
            registro.info("3A convergió en ${transcurrido}ms")
            convergido = true
        }
    }

    private suspend fun disparar(captura: ImageCapture): ByteArray =
        suspendCancellableCoroutine { cont ->
            captura.takePicture(
                ejecutorCaptura,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        try {
                            cont.resume(bytesDe(image))
                        } catch (e: Exception) {
                            cont.resumeWithException(e)
                        } finally {
                            image.close()
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        cont.resumeWithException(exception)
                    }
                },
            )
        }

    private fun bytesDe(image: ImageProxy): ByteArray {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return bytes
    }

    /** Dimensiones reales del JPEG que se va a enviar, no las que se pidieron. */
    private fun dimensionesDe(jpeg: ByteArray): Pair<Int, Int> {
        val opciones = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, opciones)
        return opciones.outWidth to opciones.outHeight
    }

    /**
     * Lo que la cámara pudo aplicar realmente. Viaja en `constraints` de la metadata: el backend
     * lo registra sin interpretarlo, y sirve para juzgar si la consistencia fotométrica entre
     * capturas alcanza para entrenar el modelo.
     */
    private fun ajustesEfectivos(): Map<String, String> {
        val info = camara?.cameraInfo ?: return emptyMap()
        return buildMap {
            runCatching {
                put("compensacionExposicion", info.exposureState.exposureCompensationIndex.toString())
                put("exposicionSoportada", info.exposureState.isExposureCompensationSupported.toString())
                put("zoom", (info.zoomState.value?.zoomRatio ?: 1f).toString())
                put("torch", (info.torchState.value ?: 0).toString())
                put("rotacion", info.sensorRotationDegrees.toString())
                put("convergencia3A", if (convergido) "aplicada" else "no aplicada")
                put("sesionSostenida", _abierta.value.toString())
            }
        }
    }
}

/** Puente de `ListenableFuture` a corrutinas, para no sumar una dependencia por dos usos. */
private suspend fun <T> ListenableFuture<T>.esperar(): T = suspendCancellableCoroutine { cont ->
    addListener(
        {
            try {
                @Suppress("BlockingMethodInNonBlockingContext")
                cont.resume(get())
            } catch (e: Exception) {
                cont.resumeWithException(e)
            }
        },
        Runnable::run,
    )
    cont.invokeOnCancellation { cancel(false) }
}
