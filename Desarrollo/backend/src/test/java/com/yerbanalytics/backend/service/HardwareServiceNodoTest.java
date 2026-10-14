package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.dto.Dispositivo;
import com.yerbanalytics.backend.model.DispositivoEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.repository.DispositivoRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
import com.yerbanalytics.backend.repository.ZonaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Estado del nodo testigo por pulsos sin respuesta, no por tiempo sin reportar (spec hardware-persistencia). */
@DisplayName("HardwareService - nodo testigo")
class HardwareServiceNodoTest {

    private ZonaRepository zonas;
    private DispositivoRepository dispositivos;
    private HardwareService service;
    private ZonaEntity mz1;
    private DispositivoEntity nodo;

    @BeforeEach
    void setUp() {
        zonas = mock(ZonaRepository.class);
        dispositivos = mock(DispositivoRepository.class);
        service = new HardwareService(dispositivos, zonas, mock(SectorRepository.class), 20);
        mz1 = new ZonaEntity();
        mz1.setId("MZ-1");
        mz1.setName("Macro-zona 1");
        nodo = new DispositivoEntity();
        nodo.setId("DEV-001");
        nodo.setSerial("AA:BB");
        nodo.setTipo(HardwareService.NODO_TESTIGO);
        nodo.setZonaId("MZ-1");
        nodo.setBateria(80);
        when(zonas.findAll()).thenReturn(List.of(mz1));
        when(dispositivos.findAll()).thenAnswer(inv -> new ArrayList<>(List.of(nodo)));
    }

    private String estado() {
        Dispositivo d = service.getHardware().dispositivos().get(0);
        return d.estado();
    }

    @Test
    void entrePulsosElNodoSigueOperativoAunqueHagaHorasQueNoReporta() {
        nodo.setUltimoUpdate(System.currentTimeMillis() - 3 * 3_600_000L);
        mz1.setPulsosSinRespuesta(0);

        assertThat(estado()).isEqualTo("operativo");
    }

    @Test
    void unoODosPulsosSinRespuestaEsSenalIntermitente() {
        nodo.setUltimoUpdate(System.currentTimeMillis());
        mz1.setPulsosSinRespuesta(1);
        assertThat(estado()).isEqualTo("intermitente");

        mz1.setPulsosSinRespuesta(2);
        assertThat(estado()).isEqualTo("intermitente");
    }

    @Test
    void tresPulsosSinRespuestaEsFueraDeServicio() {
        nodo.setUltimoUpdate(System.currentTimeMillis());
        mz1.setPulsosSinRespuesta(3);

        assertThat(estado()).isEqualTo("fuera_de_servicio");
    }

    @Test
    void unNodoQueNuncaReportoEstaFueraDeServicio() {
        nodo.setUltimoUpdate(null);

        assertThat(estado()).isEqualTo("fuera_de_servicio");
    }

    @Test
    void laBateriaBajaNoLoSacaDeOperacion() {
        nodo.setUltimoUpdate(System.currentTimeMillis());
        nodo.setBateria(10);

        Dispositivo d = service.getHardware().dispositivos().get(0);
        assertThat(d.estado()).isEqualTo("operativo");
        assertThat(d.bateriaBaja()).isTrue();
    }
}
