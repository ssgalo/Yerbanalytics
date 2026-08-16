package com.yerbanalytics.camara.colas

import com.yerbanalytics.camara.contrato.Contrato
import com.yerbanalytics.camara.contrato.MetadataImagen
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File

@Serializable
data class PendienteEnvio(
    val ordenId: String,
    val meta: MetadataImagen,
    val guardadaEn: Long,
    val intentos: Int = 0,
)

/** Lo que se descartó al hacer lugar, para poder registrarlo. */
data class Desalojo(val ordenId: String, val motivo: String)

/**
 * Cola de imágenes capturadas que todavía no se pudieron entregar.
 *
 * El JPEG va al filesystem (`<ordenId>.jpg`) con un sidecar de metadata (`<ordenId>.json`). El
 * índice se reconstruye listando el directorio, así que no hay un archivo de índice que se
 * pueda desincronizar del contenido real.
 *
 * **Acotada en cantidad y en tamaño**, descartando lo más antiguo: el contrato lo exige y la
 * razón es práctica — una cola sin techo termina rompiendo el cliente y llenando el teléfono.
 *
 * Sobrevive al cierre de la app y al reinicio del teléfono, que es lo que una PWA no podía
 * prometer.
 *
 * Clase pura de `java.io`: se testea en la JVM sin emulador.
 */
class ColaEnvio(
    private val dir: File,
    private val maxItems: Int = 50,
    private val maxBytes: Long = 200L * 1024 * 1024,
) {

    init {
        runCatching { dir.mkdirs() }
    }

    @Synchronized
    fun listar(): List<PendienteEnvio> = dir.listFiles { f -> f.name.endsWith(".json") }
        .orEmpty()
        .mapNotNull { leerSidecar(it) }
        .sortedBy { it.guardadaEn }

    val tamano: Int @Synchronized get() = listar().size

    @Synchronized
    fun bytesTotales(): Long = dir.listFiles { f -> f.name.endsWith(".jpg") }
        .orEmpty()
        .sumOf { it.length() }

    /**
     * Guarda una imagen pendiente y hace lugar si hace falta. Devuelve lo que hubo que desalojar
     * para que quien llama lo registre: descartar en silencio haría creer que nunca se perdió
     * nada.
     */
    @Synchronized
    fun guardar(ordenId: String, jpeg: ByteArray, meta: MetadataImagen): List<Desalojo> {
        val desalojos = mutableListOf<Desalojo>()
        runCatching {
            File(dir, "$ordenId.jpg").writeBytes(jpeg)
            escribirSidecar(PendienteEnvio(ordenId, meta, System.currentTimeMillis()))
        }
        // Tope por cantidad
        while (true) {
            val actuales = listar()
            if (actuales.size <= maxItems) break
            val vieja = actuales.first()
            borrar(vieja.ordenId)
            desalojos += Desalojo(vieja.ordenId, "tope de $maxItems imágenes")
        }
        // Tope por tamaño
        while (bytesTotales() > maxBytes) {
            val vieja = listar().firstOrNull() ?: break
            borrar(vieja.ordenId)
            desalojos += Desalojo(vieja.ordenId, "tope de ${maxBytes / 1024 / 1024} MB")
        }
        return desalojos
    }

    @Synchronized
    fun bytes(ordenId: String): ByteArray? =
        File(dir, "$ordenId.jpg").takeIf { it.exists() }?.readBytes()

    @Synchronized
    fun meta(ordenId: String): MetadataImagen? = leerSidecar(File(dir, "$ordenId.json"))?.meta

    @Synchronized
    fun borrar(ordenId: String) {
        File(dir, "$ordenId.jpg").delete()
        File(dir, "$ordenId.json").delete()
    }

    /** Deja registrado un intento fallido, para poder ordenar por insistencia y depurar. */
    @Synchronized
    fun anotarIntento(ordenId: String) {
        val actual = leerSidecar(File(dir, "$ordenId.json")) ?: return
        escribirSidecar(actual.copy(intentos = actual.intentos + 1))
    }

    private fun leerSidecar(archivo: File): PendienteEnvio? = runCatching {
        if (!archivo.exists()) return null
        val p = Contrato.json.decodeFromString<PendienteEnvio>(archivo.readText())
        // Un sidecar sin su JPEG es basura: no sirve para nada reintentarlo.
        if (!File(dir, "${p.ordenId}.jpg").exists()) null else p
    }.getOrNull()

    private fun escribirSidecar(p: PendienteEnvio) {
        runCatching {
            File(dir, "${p.ordenId}.json").writeText(Contrato.json.encodeToString(p))
        }
    }
}
