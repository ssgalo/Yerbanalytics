package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.dto.Dispositivo;
import com.yerbanalytics.backend.engine.RuleContextTestFactory;
import com.yerbanalytics.backend.exception.InvalidHardwareException;
import com.yerbanalytics.backend.model.DispositivoEntity;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.repository.DispositivoRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
import com.yerbanalytics.backend.repository.ZonaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El sector donde está el nodo testigo (v2 §12.10, «posición del nodo testigo»): lo usa E-01 para elegir qué riego verificar. */
@DisplayName("HardwareService - sector del nodo testigo")
class HardwareServiceWitnessSectorTest {

    private ZonaRepository zonas;
    private DispositivoRepository dispositivos;
    private SectorRepository sectores;
    private HardwareService service;
    private final List<DispositivoEntity> guardados = new ArrayList<>();

    @BeforeEach
    void setUp() {
        zonas = mock(ZonaRepository.class);
        dispositivos = mock(DispositivoRepository.class);
        sectores = mock(SectorRepository.class);
        service = new HardwareService(dispositivos, zonas, sectores, 20);
        ZonaEntity mz1 = RuleContextTestFactory.zonaBasica("MZ-1");
        when(zonas.findById("MZ-1")).thenReturn(Optional.of(mz1));
        when(zonas.findAll()).thenReturn(List.of(mz1));
        when(dispositivos.findAll()).thenAnswer(inv -> new ArrayList<>(guardados));
        when(dispositivos.findByZonaIdAndTipo("MZ-1", HardwareService.NODO_TESTIGO)).thenAnswer(inv ->
                guardados.stream().filter(d -> HardwareService.NODO_TESTIGO.equals(d.getTipo())).toList());
        when(dispositivos.save(org.mockito.ArgumentMatchers.any(DispositivoEntity.class))).thenAnswer(inv -> {
            guardados.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        SectorEntity s45 = RuleContextTestFactory.sectorBasico("MZ-1-045");
        s45.setZona(mz1);
        when(sectores.findById("MZ-1-045")).thenReturn(Optional.of(s45));
        SectorEntity s2 = RuleContextTestFactory.sectorBasico("MZ-2-001");
        s2.setZona(RuleContextTestFactory.zonaBasica("MZ-2"));
        when(sectores.findById("MZ-2-001")).thenReturn(Optional.of(s2));
    }

    private static Dispositivo nodo(String sectorId) {
        return new Dispositivo(null, "AA:BB:CC", HardwareService.NODO_TESTIGO, null, "MZ-1", sectorId, null, null, null,
                null, null, null, null, null, null, false, null, null);
    }

    @Test
    void elNodoTestigoPuedeRegistrarseConElSectorDondeEstaInstalado() {
        service.registrarDispositivo(nodo("MZ-1-045"));

        ArgumentCaptor<DispositivoEntity> saved = ArgumentCaptor.forClass(DispositivoEntity.class);
        verify(dispositivos).save(saved.capture());
        assertThat(saved.getValue().getZonaId()).isEqualTo("MZ-1");
        assertThat(saved.getValue().getSectorId()).isEqualTo("MZ-1-045");
    }

    @Test
    void elSectorDelNodoEsOpcional() {
        service.registrarDispositivo(nodo(null));

        assertThat(guardados).singleElement().satisfies(d -> assertThat(d.getSectorId()).isNull());
        assertThat(service.witnessSector("MZ-1")).isEmpty();
    }

    @Test
    void unSectorQueNoExisteSeRechaza() {
        assertThatThrownBy(() -> service.registrarDispositivo(nodo("MZ-1-999")))
                .isInstanceOf(InvalidHardwareException.class)
                .hasMessageContaining("MZ-1-999");
    }

    @Test
    void unSectorDeOtraMacroZonaSeRechaza() {
        assertThatThrownBy(() -> service.registrarDispositivo(nodo("MZ-2-001")))
                .isInstanceOf(InvalidHardwareException.class)
                .hasMessageContaining("MZ-2-001")
                .hasMessageContaining("MZ-1");
    }

    @Test
    void witnessSectorDevuelveElSectorDelNodoDeLaZona() {
        service.registrarDispositivo(nodo("MZ-1-045"));

        assertThat(service.witnessSector("MZ-1")).contains("MZ-1-045");
        assertThat(service.witnessSector("MZ-3")).isEmpty();
    }

    @Test
    void elSectorDelNodoNoLoHaceFigurarComoUnSectorConHardwareIncompleto() {
        service.registrarDispositivo(nodo("MZ-1-045"));

        assertThat(service.getHardware().incompletos()).isEmpty();
    }
}
