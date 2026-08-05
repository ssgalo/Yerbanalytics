package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.config.CapturaProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Unit test puro: el servicio es un POJO, no necesita contexto de Spring ni base de datos.
 */
class AlmacenamientoImagenServiceTest {

    private static final byte[] JPEG = "bytes-de-un-jpeg".getBytes(StandardCharsets.UTF_8);

    private AlmacenamientoImagenService servicio(Path dir) {
        CapturaProperties props = new CapturaProperties();
        props.setDir(dir.toString());
        AlmacenamientoImagenService s = new AlmacenamientoImagenService(props);
        s.verificarDirectorio();
        return s;
    }

    @Test
    void guardar_escribeElArchivoParticionadoPorFecha(@TempDir Path dir) throws IOException {
        AlmacenamientoImagenService s = servicio(dir);
        long ts = Instant.parse("2026-08-04T15:30:00Z").toEpochMilli();

        String relativa = s.guardar("CAP-001", ts, JPEG);

        String particionEsperada = Instant.ofEpochMilli(ts)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        assertEquals(particionEsperada + "/CAP-001.jpg", relativa);
        assertTrue(Files.isRegularFile(dir.resolve(relativa)));
    }

    @Test
    void leer_devuelveLosMismosBytes(@TempDir Path dir) throws IOException {
        AlmacenamientoImagenService s = servicio(dir);
        String relativa = s.guardar("CAP-002", System.currentTimeMillis(), JPEG);

        assertArrayEquals(JPEG, s.leer(relativa));
        assertTrue(s.existe(relativa));
    }

    @Test
    void borrar_esIdempotente(@TempDir Path dir) throws IOException {
        AlmacenamientoImagenService s = servicio(dir);
        String relativa = s.guardar("CAP-003", System.currentTimeMillis(), JPEG);

        s.borrar(relativa);
        assertFalse(s.existe(relativa));
        // Borrar dos veces no es un error.
        s.borrar(relativa);
    }

    @Test
    void sha256_coincideConElHashConocidoDelContenido() {
        // Hash de referencia de la cadena vacía: verifica formato hex en minúsculas y longitud.
        assertEquals(
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                AlmacenamientoImagenService.sha256(new byte[0]));
    }

    @Test
    void sha256_difiereAnteUnByteDistinto() {
        String a = AlmacenamientoImagenService.sha256(JPEG);
        byte[] alterado = JPEG.clone();
        alterado[0] ^= 0x01;

        assertEquals(64, a.length());
        assertFalse(a.equals(AlmacenamientoImagenService.sha256(alterado)));
    }

    @Test
    void resolver_rechazaRutasQueEscapanDeLaRaiz(@TempDir Path dir) {
        AlmacenamientoImagenService s = servicio(dir);

        assertThrows(IllegalArgumentException.class, () -> s.leer("../../etc/passwd"));
    }

    @Test
    void arranque_fallaSiElDirectorioNoEsEscribible(@TempDir Path dir) throws IOException {
        Path soloLectura = Files.createDirectory(dir.resolve("solo-lectura"));
        assumeTrue(soloLectura.toFile().setWritable(false),
                "El sistema de archivos no permite quitar el permiso de escritura");
        assumeTrue(!Files.isWritable(soloLectura),
                "El usuario puede escribir igual (probablemente root o Windows sin ACL)");

        CapturaProperties props = new CapturaProperties();
        props.setDir(soloLectura.toString());
        AlmacenamientoImagenService s = new AlmacenamientoImagenService(props);

        IllegalStateException ex = assertThrows(IllegalStateException.class, s::verificarDirectorio);
        assertTrue(ex.getMessage().contains("no es escribible"));

        soloLectura.toFile().setWritable(true);
    }
}
