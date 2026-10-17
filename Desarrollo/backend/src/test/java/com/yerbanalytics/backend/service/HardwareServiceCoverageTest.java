package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.engine.HardwareCoverage;
import com.yerbanalytics.backend.model.DispositivoEntity;
import com.yerbanalytics.backend.repository.DispositivoRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
import com.yerbanalytics.backend.repository.ZonaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** S-04: qué actuadores tiene mapeados cada sector de una macro-zona, en dos consultas y no una por sector. */
@DisplayName("HardwareService.coverageOf - hardware mapeado por sector (S-04)")
class HardwareServiceCoverageTest {

    private static final List<String> SECTORES = List.of("MZ-1-001", "MZ-1-002", "MZ-1-003");

    private DispositivoRepository dispositivos;
    private HardwareService service;
    private final List<DispositivoEntity> nodos = new ArrayList<>();
    private final List<DispositivoEntity> actuadores = new ArrayList<>();

    @BeforeEach
    void setUp() {
        dispositivos = mock(DispositivoRepository.class);
        service = new HardwareService(dispositivos, mock(ZonaRepository.class), mock(SectorRepository.class), 20);
        when(dispositivos.findByZonaIdAndTipo(anyString(), anyString())).thenAnswer(inv -> List.copyOf(nodos));
        when(dispositivos.findBySectorIdInAndTipoIn(anyCollection(), anyCollection())).thenAnswer(inv -> List.copyOf(actuadores));
    }

    private static DispositivoEntity actuador(String tipo, String sectorId) {
        DispositivoEntity d = new DispositivoEntity();
        d.setId("DEV-" + tipo + "-" + sectorId);
        d.setSerial(tipo + "-" + sectorId);
        d.setTipo(tipo);
        d.setSectorId(sectorId);
        return d;
    }

    private void nodoTestigo() {
        DispositivoEntity n = new DispositivoEntity();
        n.setId("DEV-001");
        n.setSerial("AA:BB");
        n.setTipo(HardwareService.NODO_TESTIGO);
        n.setZonaId("MZ-1");
        nodos.add(n);
    }

    @Test
    @DisplayName("un sector con electroválvula, bomba y mediasombra y el nodo de su zona está completo")
    void sectorCompleto() {
        nodoTestigo();
        actuadores.add(actuador(HardwareService.ELECTROVALVULA, "MZ-1-001"));
        actuadores.add(actuador(HardwareService.BOMBA, "MZ-1-001"));
        actuadores.add(actuador(HardwareService.MEDIASOMBRA, "MZ-1-001"));

        HardwareCoverage c = service.coverageOf("MZ-1", SECTORES).get("MZ-1-001");

        assertThat(c).isEqualTo(new HardwareCoverage(true, true, true, true));
        assertThat(c.canIrrigate()).isTrue();
        assertThat(c.canDose()).isTrue();
        assertThat(c.canShade()).isTrue();
    }

    @Test
    @DisplayName("cada sector de la zona figura en el resultado, también los que no tienen ningún dispositivo")
    void todosLosSectoresFiguran() {
        nodoTestigo();
        actuadores.add(actuador(HardwareService.ELECTROVALVULA, "MZ-1-001"));

        Map<String, HardwareCoverage> cobertura = service.coverageOf("MZ-1", SECTORES);

        assertThat(cobertura).containsOnlyKeys(SECTORES.toArray(String[]::new));
        assertThat(cobertura.get("MZ-1-003")).isEqualTo(new HardwareCoverage(false, false, true, false));
    }

    @Test
    @DisplayName("sin electroválvula no se puede regar; sin bomba no se puede dosificar, pero se riega")
    void faltaUnActuador() {
        nodoTestigo();
        actuadores.add(actuador(HardwareService.BOMBA, "MZ-1-001"));          // sin electroválvula
        actuadores.add(actuador(HardwareService.ELECTROVALVULA, "MZ-1-002")); // sin bomba

        Map<String, HardwareCoverage> cobertura = service.coverageOf("MZ-1", SECTORES);

        assertThat(cobertura.get("MZ-1-001").canIrrigate()).isFalse();
        assertThat(cobertura.get("MZ-1-001").canDose()).isTrue();
        assertThat(cobertura.get("MZ-1-002").canIrrigate()).isTrue();
        assertThat(cobertura.get("MZ-1-002").canDose()).isFalse();
    }

    @Test
    @DisplayName("sin nodo testigo en la zona ningún sector puede regar, aunque tenga su electroválvula")
    void sinNodoTestigo() {
        actuadores.add(actuador(HardwareService.ELECTROVALVULA, "MZ-1-001"));

        HardwareCoverage c = service.coverageOf("MZ-1", SECTORES).get("MZ-1-001");

        assertThat(c.valve()).isTrue();
        assertThat(c.zoneNode()).isFalse();
        assertThat(c.canIrrigate()).isFalse();
    }

    @Test
    @DisplayName("la mediasombra se exige a la zona: alcanza con una registrada en cualquiera de sus sectores")
    void mediasombraDeLaZona() {
        nodoTestigo();
        actuadores.add(actuador(HardwareService.MEDIASOMBRA, "MZ-1-003"));

        Map<String, HardwareCoverage> cobertura = service.coverageOf("MZ-1", SECTORES);

        assertThat(cobertura.values()).allSatisfy(c -> assertThat(c.canShade()).isTrue());
    }

    @Test
    @DisplayName("una zona sin ninguna mediasombra no puede mover la malla en ningún sector")
    void zonaSinMediasombra() {
        nodoTestigo();
        actuadores.add(actuador(HardwareService.ELECTROVALVULA, "MZ-1-001"));

        assertThat(service.coverageOf("MZ-1", SECTORES).values())
                .allSatisfy(c -> assertThat(c.canShade()).isFalse());
    }

    @Test
    @DisplayName("un dispositivo con falla sigue estando mapeado: reportar fallas es de S-05")
    void laFallaNoDesmapea() {
        nodoTestigo();
        DispositivoEntity valvula = actuador(HardwareService.ELECTROVALVULA, "MZ-1-001");
        valvula.setFalla("Falla Hidráulica");
        actuadores.add(valvula);

        assertThat(service.coverageOf("MZ-1", SECTORES).get("MZ-1-001").valve()).isTrue();
    }

    @Test
    @DisplayName("lee la base dos veces por zona (nodo y actuadores), no una por sector")
    void dosConsultas() {
        service.coverageOf("MZ-1", SECTORES);

        verify(dispositivos, times(1)).findByZonaIdAndTipo("MZ-1", HardwareService.NODO_TESTIGO);
        verify(dispositivos, times(1)).findBySectorIdInAndTipoIn(anyCollection(), anyCollection());
    }

    @Test
    @DisplayName("sin sectores no hay nada que consultar")
    void sinSectores() {
        assertThat(service.coverageOf("MZ-1", List.of())).isEmpty();
    }
}
