package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.dto.Dispositivo;
import com.yerbanalytics.backend.engine.HardwareFaults;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** S-05: qué actuadores de cada sector de una macro-zona están marcados como averiados, en UNA consulta. */
@DisplayName("HardwareService.faultsOf - fallas por sector (S-05)")
class HardwareServiceFaultsTest {

    private static final List<String> SECTORES = List.of("MZ-1-001", "MZ-1-002", "MZ-1-003");

    private DispositivoRepository dispositivos;
    private HardwareService service;
    private final List<DispositivoEntity> actuadores = new ArrayList<>();

    @BeforeEach
    void setUp() {
        dispositivos = mock(DispositivoRepository.class);
        service = new HardwareService(dispositivos, mock(ZonaRepository.class), mock(SectorRepository.class), 20);
        when(dispositivos.findBySectorIdInAndTipoIn(anyCollection(), anyCollection())).thenAnswer(inv -> List.copyOf(actuadores));
    }

    private void actuador(String tipo, String sectorId, String falla) {
        DispositivoEntity d = new DispositivoEntity();
        d.setId("DEV-" + tipo + "-" + sectorId);
        d.setSerial(tipo + "-" + sectorId);
        d.setTipo(tipo);
        d.setSectorId(sectorId);
        d.setFalla(falla);
        actuadores.add(d);
    }

    @Test
    @DisplayName("sin ningún dispositivo averiado, todos los sectores figuran sin fallas")
    void sinFallas() {
        actuador(HardwareService.ELECTROVALVULA, "MZ-1-001", null);
        actuador(HardwareService.MEDIASOMBRA, "MZ-1-001", null);

        Map<String, HardwareFaults> faults = service.faultsOf(SECTORES);

        assertThat(faults).containsOnlyKeys(SECTORES);
        assertThat(faults.values()).allSatisfy(f -> assertThat(f).isEqualTo(HardwareFaults.NONE));
    }

    @Test
    @DisplayName("la falla de la electroválvula es del sector averiado y de ningún otro")
    void fallaHidraulicaPorSector() {
        actuador(HardwareService.ELECTROVALVULA, "MZ-1-001", "SIN_CAUDAL");
        actuador(HardwareService.ELECTROVALVULA, "MZ-1-002", null);

        Map<String, HardwareFaults> faults = service.faultsOf(SECTORES);

        assertThat(faults.get("MZ-1-001").hydraulic()).isEqualTo("SIN_CAUDAL");
        assertThat(faults.get("MZ-1-002")).isEqualTo(HardwareFaults.NONE);
        assertThat(faults.get("MZ-1-003")).isEqualTo(HardwareFaults.NONE);
    }

    @Test
    @DisplayName("la falla de la mediasombra de un sector es de la macro-zona: la ven todos sus sectores")
    void fallaDeLaMallaEsDeLaZona() {
        actuador(HardwareService.MEDIASOMBRA, "MZ-1-002", "MEDIASOMBRA_FIN_DE_CARRERA");

        Map<String, HardwareFaults> faults = service.faultsOf(SECTORES);

        assertThat(faults.values()).allSatisfy(f -> {
            assertThat(f.shade()).isEqualTo("MEDIASOMBRA_FIN_DE_CARRERA");
            assertThat(f.hydraulic()).isNull();
        });
    }

    @Test
    @DisplayName("una falla en blanco no cuenta, y la bomba averiada no saca al sector del riego")
    void enBlancoYBomba() {
        actuador(HardwareService.ELECTROVALVULA, "MZ-1-001", "  ");
        actuador(HardwareService.BOMBA, "MZ-1-001", "Falla de bomba");

        assertThat(service.faultsOf(SECTORES).get("MZ-1-001")).isEqualTo(HardwareFaults.NONE);
    }

    @Test
    @DisplayName("una sola consulta para toda la zona, y sin sectores no consulta nada")
    void unaConsulta() {
        service.faultsOf(SECTORES);
        verify(dispositivos, times(1)).findBySectorIdInAndTipoIn(anyCollection(), anyCollection());

        assertThat(service.faultsOf(List.of())).isEmpty();
        verify(dispositivos, times(1)).findBySectorIdInAndTipoIn(anyCollection(), anyCollection());
    }

    @Test
    @DisplayName("el panel de hardware cuenta el averiado y expone desde cuándo")
    void elPanelMuestraLaFalla() {
        DispositivoEntity valvula = new DispositivoEntity();
        valvula.setId("DEV-101");
        valvula.setSerial("EV-1");
        valvula.setTipo(HardwareService.ELECTROVALVULA);
        valvula.setSectorId("MZ-1-001");
        valvula.setFalla("SIN_CAUDAL");
        valvula.setFallaDesde(1_760_000_000_000L);
        when(dispositivos.findAll()).thenReturn(new ArrayList<>(List.of(valvula)));
        ZonaRepository zonas = mock(ZonaRepository.class);
        when(zonas.findAll()).thenReturn(List.of());
        HardwareService conPanel = new HardwareService(dispositivos, zonas, mock(SectorRepository.class), 20);

        var data = conPanel.getHardware();

        assertThat(data.averiados()).isEqualTo(1);
        Dispositivo d = data.dispositivos().get(0);
        assertThat(d.falla()).isEqualTo("SIN_CAUDAL");
        assertThat(d.fallaDesde()).isEqualTo(1_760_000_000_000L);
        assertThat(d.estado()).isEqualTo("fuera_de_servicio");
    }
}
