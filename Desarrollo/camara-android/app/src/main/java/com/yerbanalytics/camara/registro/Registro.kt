package com.yerbanalytics.camara.registro

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

enum class Nivel { INFO, EXITO, AVISO, ERROR }

data class Entrada(val ts: Long, val nivel: Nivel, val texto: String) {
    fun formateada(): String = "${horaDe(ts)} ${nivel.name.padEnd(5)} $texto"

    companion object {
        private val formato = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        fun horaDe(ts: Long): String = synchronized(formato) { formato.format(Date(ts)) }
    }
}

/**
 * Log del dispositivo: ventana en memoria para la pantalla + archivo rotativo en disco.
 *
 * **Por qué en pantalla:** depurar con las herramientas de desarrollo enchufadas a un teléfono
 * montado en un riel no es practicable. Todo lo que pasa —cámara, canal, captura, envío— tiene
 * que poder leerse desde el propio teléfono.
 *
 * **Por qué además en archivo:** cuando algo falla a las cuatro de la mañana en medio de una
 * pasada, lo que se necesita es el historial, y para entonces la ventana en memoria ya se dio
 * vuelta varias veces. Un buffer de unos cientos de líneas es lo que se puede hacer en una
 * pestaña de navegador, no lo que corresponde a un equipo de producción.
 */
class Registro(
    private val dirLogs: File,
    private val maxEnMemoria: Int = 400,
    private val maxBytesPorArchivo: Long = 1L * 1024 * 1024,
    private val maxArchivos: Int = 5,
    private val escritor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "registro-yerbanalytics").apply { isDaemon = true }
    },
) {

    private val _entradas = MutableStateFlow<List<Entrada>>(emptyList())
    val entradas: StateFlow<List<Entrada>> = _entradas.asStateFlow()

    private val archivoActual: File get() = File(dirLogs, "camara.log")
    private val formatoArchivo = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    init {
        runCatching { dirLogs.mkdirs() }
    }

    fun info(texto: String) = agregar(Nivel.INFO, texto)
    fun exito(texto: String) = agregar(Nivel.EXITO, texto)
    fun aviso(texto: String) = agregar(Nivel.AVISO, texto)
    fun error(texto: String) = agregar(Nivel.ERROR, texto)

    fun agregar(nivel: Nivel, texto: String) {
        val entrada = Entrada(System.currentTimeMillis(), nivel, texto)
        _entradas.value = (_entradas.value + entrada).takeLast(maxEnMemoria)
        escritor.execute { persistir(entrada) }
    }

    /** Contenido de todos los archivos, del más viejo al más nuevo. Para compartir/exportar. */
    fun exportar(): String = buildString {
        for (i in maxArchivos - 1 downTo 1) {
            val f = File(dirLogs, "camara.$i.log")
            if (f.exists()) append(f.readText())
        }
        if (archivoActual.exists()) append(archivoActual.readText())
    }

    private fun persistir(entrada: Entrada) {
        runCatching {
            if (archivoActual.exists() && archivoActual.length() >= maxBytesPorArchivo) rotar()
            val linea = "${formatoArchivo.format(Date(entrada.ts))} " +
                "${entrada.nivel.name.padEnd(5)} ${entrada.texto}\n"
            archivoActual.appendText(linea)
        }
    }

    /** camara.log -> camara.1.log -> ... -> camara.N.log, y el más viejo se descarta. */
    private fun rotar() {
        File(dirLogs, "camara.${maxArchivos - 1}.log").takeIf { it.exists() }?.delete()
        for (i in maxArchivos - 2 downTo 1) {
            val origen = File(dirLogs, "camara.$i.log")
            if (origen.exists()) origen.renameTo(File(dirLogs, "camara.${i + 1}.log"))
        }
        archivoActual.renameTo(File(dirLogs, "camara.1.log"))
    }
}
