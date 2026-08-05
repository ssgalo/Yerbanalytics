package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.config.CapturaProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Guarda los JPEG de las capturas en el filesystem, particionados por fecha:
 * {@code <dir>/AAAA/MM/DD/<capturaId>.jpg}.
 *
 * <p>Fuera de la base a propósito: 600 sectores × varios ciclos diarios son unos cuantos GB
 * por semana, que como {@code bytea} entrarían en el {@code pg_dump}, en la replicación y en
 * la memoria del pool de conexiones. En disco, el backup de imágenes tiene su propio ciclo de
 * vida y la purga por antigüedad es un borrado de directorio.
 *
 * <p>El contra conocido es que se pierde la atomicidad transaccional entre el archivo y la
 * fila. Se ordena a propósito: <strong>primero el archivo, después la fila</strong>. Un
 * archivo huérfano es basura recolectable; una fila apuntando a un archivo inexistente sería
 * un 404 a la vista del usuario.
 */
@Service
public class AlmacenamientoImagenService {

    private static final Logger log = LoggerFactory.getLogger(AlmacenamientoImagenService.class);
    private static final DateTimeFormatter PARTICION = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final Path raiz;

    public AlmacenamientoImagenService(CapturaProperties props) {
        this.raiz = Paths.get(props.getDir()).toAbsolutePath().normalize();
    }

    /**
     * Falla el arranque si el directorio no existe y no puede crearse, o no es escribible.
     * Es deliberado: mejor no arrancar que descubrirlo en la primera subida, cuando ya hay una
     * imagen en vuelo y una pasada del riel perdida.
     */
    @PostConstruct
    void verificarDirectorio() {
        try {
            Files.createDirectories(raiz);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "No se pudo crear el directorio de capturas '" + raiz + "'. "
                            + "Revisá 'yerbanalytics.capturas.dir' y los permisos del proceso.", e);
        }
        if (!Files.isWritable(raiz)) {
            throw new IllegalStateException(
                    "El directorio de capturas '" + raiz + "' no es escribible. "
                            + "Revisá los permisos del usuario que corre la aplicación.");
        }
        log.info("Directorio de capturas: {}", raiz);
    }

    /**
     * Escribe el JPEG y devuelve su ruta <em>relativa</em> a la raíz, que es lo que se
     * persiste. Guardar la ruta relativa permite mover el directorio de capturas sin
     * reescribir la base.
     */
    public String guardar(String capturaId, long recibidaEn, byte[] bytes) throws IOException {
        String particion = Instant.ofEpochMilli(recibidaEn)
                .atZone(ZoneId.systemDefault())
                .format(PARTICION);
        String relativa = particion + "/" + capturaId + ".jpg";

        Path destino = raiz.resolve(relativa);
        Files.createDirectories(destino.getParent());
        Files.write(destino, bytes);
        return relativa;
    }

    /** Lee los bytes de una captura. Lanza {@link IOException} si el archivo ya no está. */
    public byte[] leer(String rutaRelativa) throws IOException {
        return Files.readAllBytes(resolver(rutaRelativa));
    }

    public boolean existe(String rutaRelativa) {
        return Files.isRegularFile(resolver(rutaRelativa));
    }

    /** Borra el archivo. No falla si ya no está: borrar dos veces no es un error. */
    public void borrar(String rutaRelativa) throws IOException {
        Files.deleteIfExists(resolver(rutaRelativa));
    }

    /** SHA-256 hexadecimal en minúsculas, el formato que declara el contrato. */
    public static String sha256(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", e);
        }
    }

    /**
     * Resuelve una ruta relativa contra la raíz, rechazando cualquier intento de escapar del
     * directorio de capturas. La ruta viene de la base, no del cliente, pero verificar cuesta
     * una comparación y evita que un dato corrupto se convierta en una lectura arbitraria.
     */
    private Path resolver(String rutaRelativa) {
        Path resuelta = raiz.resolve(rutaRelativa).normalize();
        if (!resuelta.startsWith(raiz)) {
            throw new IllegalArgumentException("Ruta de captura fuera del directorio raíz: " + rutaRelativa);
        }
        return resuelta;
    }

    public Path getRaiz() {
        return raiz;
    }
}
