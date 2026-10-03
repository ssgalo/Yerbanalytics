package com.yerbanalytics.backend.repository;

import com.yerbanalytics.backend.model.HistorialEventoEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tareas 3.3 a 3.5. Corre contra la PostgreSQL de dev con rollback por test (no deja filas).
 * Las zonas y sectores son ficticios (prefijo {@code ZT-}), así no se mezclan con los reales.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("Historial y zona: columnas del riego")
class HistorialRepositoryTest {

    private static final String ZONA = "ZT-1";

    @Autowired
    private HistorialRepository repository;
    @Autowired
    private ZonaRepository zonaRepository;

    private static HistorialEventoEntity evento(String id, String sector, String zona, String tipo, long ts,
                                                String regla, Integer duracionSeg) {
        HistorialEventoEntity e = new HistorialEventoEntity();
        e.setId(id);
        e.setSectorId(sector);
        e.setZonaId(zona);
        e.setZonaName("Zona " + zona);
        e.setTipo(tipo);
        e.setTs(ts);
        e.setLectura("l");
        e.setDecision("d");
        e.setAccion("a");
        e.setRes("Efectiva");
        e.setSev("—");
        e.setEvoShow(false);
        e.setBloqueoRepeticion(false);
        e.setRegla(regla);
        e.setDuracionSeg(duracionSeg);
        return e;
    }

    // ------------------------------------------------------------------ 3.3

    @Test
    void elEventoGuardaYLeeReglaAlertaVolumenYDuracion() {
        HistorialEventoEntity e = evento("t33-a", "ZT-1-001", ZONA, "Riego", 1_000L,
                "RiegoPorDeficitRule", 504);
        e.setVolumenL(4.2);
        e.setAlerta("CRITICAL");
        repository.saveAndFlush(e);

        HistorialEventoEntity leido = repository.findById("t33-a").orElseThrow();

        assertThat(leido.getRegla()).isEqualTo("RiegoPorDeficitRule");
        assertThat(leido.getAlerta()).isEqualTo("CRITICAL");
        assertThat(leido.getVolumenL()).isEqualTo(4.2);
        assertThat(leido.getDuracionSeg()).isEqualTo(504);
    }

    @Test
    void losCamposNuevosSonNulosPorDefecto() {
        repository.saveAndFlush(evento("t33-b", "ZT-1-001", ZONA, "Info", 1_000L, null, null));

        HistorialEventoEntity leido = repository.findById("t33-b").orElseThrow();

        assertThat(leido.getRegla()).isNull();
        assertThat(leido.getAlerta()).isNull();
        assertThat(leido.getVolumenL()).isNull();
        assertThat(leido.getDuracionSeg()).isNull();
    }

    @Test
    void laZonaGuardaLaMarcaDeLaUltimaHumedadDeSustrato() {
        ZonaEntity z = new ZonaEntity();
        z.setId("ZT-HUM");
        z.setName("Zona de prueba");
        z.setSub("Prueba");
        assertThat(z.getHumSusTs()).isNull();
        z.setHumSusTs(1_700_000_000_000L);
        zonaRepository.saveAndFlush(z);

        assertThat(zonaRepository.findById("ZT-HUM").orElseThrow().getHumSusTs()).isEqualTo(1_700_000_000_000L);
    }

    // ------------------------------------------------------------------ 3.4

    @Test
    void ultimosPorSectorDevuelveElMaxTsPorSectorTipoYRegla() {
        repository.saveAll(List.of(
                evento("t34-1", "ZT-1-001", ZONA, "Riego", 1_000L, "RiegoPorDeficitRule", 480),
                evento("t34-2", "ZT-1-001", ZONA, "Riego", 3_000L, "RiegoPorDeficitRule", 480),
                evento("t34-3", "ZT-1-001", ZONA, "Riego", 2_000L, "DeficitCriticoRule", 720),
                evento("t34-4", "ZT-1-001", ZONA, "Insumo", 2_500L, null, null),
                evento("t34-5", "ZT-1-002", ZONA, "Riego", 1_500L, "RiegoPorDeficitRule", 480),
                evento("t34-6", "ZT-1-001", ZONA, "Info", 9_000L, null, null),
                evento("t34-7", "ZT-9-001", "ZT-9", "Riego", 8_000L, "RiegoPorDeficitRule", 480)));
        repository.flush();

        List<HistorialRepository.UltimoEvento> filas = repository.ultimosPorSector(ZONA, 0L);

        Map<String, Long> porClave = filas.stream().collect(Collectors.toMap(
                f -> f.getSectorId() + "|" + f.getTipo() + "|" + f.getRegla(), HistorialRepository.UltimoEvento::getTs));
        assertThat(porClave).containsOnly(
                Map.entry("ZT-1-001|Riego|RiegoPorDeficitRule", 3_000L),
                Map.entry("ZT-1-001|Riego|DeficitCriticoRule", 2_000L),
                Map.entry("ZT-1-001|Insumo|null", 2_500L),
                Map.entry("ZT-1-002|Riego|RiegoPorDeficitRule", 1_500L));
    }

    @Test
    void ultimosPorSectorIgnoraLosEventosAnterioresADesde() {
        repository.saveAll(List.of(
                evento("t34-8", "ZT-1-001", ZONA, "Riego", 999L, "RiegoPorDeficitRule", 480),
                evento("t34-9", "ZT-1-001", ZONA, "Riego", 1_000L, "DeficitCriticoRule", 720)));
        repository.flush();

        List<HistorialRepository.UltimoEvento> filas = repository.ultimosPorSector(ZONA, 1_000L);

        assertThat(filas).extracting(HistorialRepository.UltimoEvento::getRegla)
                .containsExactly("DeficitCriticoRule");
    }

    // ------------------------------------------------------------------ 3.5

    @Test
    void riegosDesdeDevuelveLosRiegosConDuracionYTsDesdeElCorte() {
        repository.saveAll(List.of(
                evento("t35-1", "ZT-1-001", ZONA, "Riego", 5_000L, "RiegoPorDeficitRule", 480),
                evento("t35-2", "ZT-1-002", ZONA, "Riego", 4_999L, "RiegoPorDeficitRule", 480),
                evento("t35-3", "ZT-1-003", ZONA, "Riego", 6_000L, null, null),
                evento("t35-4", "ZT-1-004", ZONA, "Insumo", 6_000L, null, 30)));
        repository.flush();

        List<HistorialEventoEntity> riegos = repository.riegosDesde(5_000L);

        assertThat(riegos).extracting(HistorialEventoEntity::getId)
                .contains("t35-1")
                .doesNotContain("t35-2", "t35-3", "t35-4");
    }
}
