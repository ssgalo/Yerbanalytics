package com.yerbanalytics.camara.contrato

import com.yerbanalytics.camara.registro.Registro
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.atomic.AtomicLong

/**
 * Canal de órdenes por Server-Sent Events.
 *
 * Obligaciones del contrato que resuelve esta clase (§5.1):
 *
 * - **Sostener el canal abierto** mientras el dispositivo esté operativo.
 * - **Reconectar solo** ante cortes, con espera creciente y jitter.
 * - **No dar por vivo un canal que no da error.** Un socket colgado no falla: simplemente deja
 *   de entregar. Por eso hay un watchdog por ausencia de eventos — el backend manda `ping`
 *   periódico justamente para que se pueda distinguir "callado" de "muerto".
 *
 * La credencial va en el header, nunca por query string (ver [ContratoClient]).
 */
class CanalOrdenes(
    private val cliente: ContratoClient,
    private val proveedor: ProveedorToken,
    private val registro: Registro,
    private val config: () -> ConfigCaptura,
    private val http: OkHttpClient = ContratoClient.clienteStream(),
) {

    enum class Estado { CONECTADO, RECONECTANDO, DESCONECTADO }

    private val _estado = MutableStateFlow(Estado.DESCONECTADO)
    val estado: StateFlow<Estado> = _estado.asStateFlow()

    fun iniciar(scope: CoroutineScope, alRecibir: (EventoCanal) -> Unit): Job = scope.launch {
        var intento = 0
        while (isActive) {
            val cierre = CompletableDeferred<String?>()
            val ultimoEvento = AtomicLong(System.currentTimeMillis())
            var abrio = false
            var fuente: EventSource? = null
            var watchdog: Job? = null

            try {
                val token = proveedor.vigente()
                val peticion = Request.Builder()
                    .url("${cliente.base}/ordenes/stream")
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "text/event-stream")
                    .header("Cache-Control", "no-cache")
                    .get()
                    .build()

                val escucha = object : EventSourceListener() {
                    override fun onOpen(eventSource: EventSource, response: Response) {
                        abrio = true
                        ultimoEvento.set(System.currentTimeMillis())
                        _estado.value = Estado.CONECTADO
                        registro.exito("Canal de órdenes abierto")
                    }

                    override fun onEvent(
                        eventSource: EventSource,
                        id: String?,
                        type: String?,
                        data: String,
                    ) {
                        ultimoEvento.set(System.currentTimeMillis())
                        when (val evento = ParserEventos.parsear(type, data)) {
                            is EventoCanal.Ping -> Unit // keep-alive: sólo renueva el watchdog
                            is EventoCanal.Desconocido ->
                                registro.info("Evento desconocido '${evento.tipo}': se ignora")
                            is EventoCanal.Ilegible ->
                                registro.aviso("Evento '${evento.tipo}' ilegible: ${evento.motivo}")
                            else -> alRecibir(evento)
                        }
                    }

                    override fun onClosed(eventSource: EventSource) {
                        cierre.complete("el backend cerró el canal")
                    }

                    override fun onFailure(
                        eventSource: EventSource,
                        t: Throwable?,
                        response: Response?,
                    ) {
                        val codigo = response?.code
                        response?.close()
                        cierre.complete(
                            when {
                                codigo != null -> "HTTP $codigo"
                                else -> t?.message ?: "conexión interrumpida"
                            }
                        )
                    }
                }

                fuente = EventSources.createFactory(http).newEventSource(peticion, escucha)

                // Watchdog: la ausencia prolongada de eventos es la señal de canal muerto.
                watchdog = launch {
                    while (isActive) {
                        delay(1_000)
                        val heartbeatSeg = config().heartbeatSeg
                        if (Watchdog.canalMuerto(
                                ultimoEvento.get(),
                                System.currentTimeMillis(),
                                heartbeatSeg,
                            )
                        ) {
                            val seg = Watchdog.silencioMaximoMs(heartbeatSeg) / 1000
                            registro.aviso(
                                "Sin eventos por más de ${seg}s: el canal está muerto aunque no " +
                                    "dio error. Se reconecta."
                            )
                            cierre.complete("watchdog")
                            break
                        }
                    }
                }

                val motivo = cierre.await()
                if (motivo != null && motivo.contains("401")) {
                    // El token venció mientras el stream estaba abierto: renovar antes de volver.
                    runCatching { proveedor.renovar() }
                }
                registro.aviso("Canal cerrado: $motivo")
            } catch (e: Exception) {
                registro.error("No se pudo abrir el canal: ${e.message}")
            } finally {
                watchdog?.cancel()
                fuente?.cancel()
            }

            if (!isActive) break

            _estado.value = Estado.RECONECTANDO
            intento = if (abrio) 1 else intento + 1
            val espera = Backoff.esperaMs(intento)
            registro.info("Reintento de canal en ${espera / 1000}s")
            delay(espera)
        }
        _estado.value = Estado.DESCONECTADO
    }
}
