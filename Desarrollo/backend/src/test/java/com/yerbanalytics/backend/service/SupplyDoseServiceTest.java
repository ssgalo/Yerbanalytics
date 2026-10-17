package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.model.HistorialEventoEntity;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.repository.HistorialRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El estado "Dosificando" de la bomba se cierra con el ack final y, si no llega, por tiempo. */
@DisplayName("SupplyDoseService")
class SupplyDoseServiceTest {

    private static final long MARGEN_MS = 90_000L;
    private static final long T0 = 1_800_000_000_000L;

    private final SectorRepository sectores = mock(SectorRepository.class);
    private final HistorialRepository historial = mock(HistorialRepository.class);
    private final Clock reloj = mock(Clock.class);
    private SupplyDoseService servicio;

    @BeforeEach
    void setUp() {
        when(reloj.millis()).thenReturn(T0);
        servicio = new SupplyDoseService(sectores, historial, reloj, MARGEN_MS);
    }

    private static SectorEntity dosificando(String id) {
        SectorEntity s = new SectorEntity();
        s.setId(id);
        s.setActuadorPump("Dosificando");
        return s;
    }

    private void ultimaDosis(String sectorId, long ts, Integer duracionSeg) {
        HistorialEventoEntity e = new HistorialEventoEntity();
        e.setSectorId(sectorId);
        e.setTipo("Insumo");
        e.setTs(ts);
        e.setDuracionSeg(duracionSeg);
        when(historial.findFirstBySectorIdAndTipoOrderByTsDesc(sectorId, "Insumo")).thenReturn(Optional.of(e));
    }

    @Test
    @DisplayName("el ack final de la bomba (ok, cancelado o error) la deja En espera")
    void elAckFinalCierraLaDosis() {
        for (String tipo : List.of("ok", "cancelado", "comando_invalido")) {
            servicio.onActuatorStopped(new ActuatorStopped("MZ-1", "MZ-1-001", "pump", "p-1", tipo));
        }

        verify(sectores, org.mockito.Mockito.times(3))
                .cambiarBomba("MZ-1-001", SupplyDoseService.DOSIFICANDO, SupplyDoseService.EN_ESPERA);
    }

    @Test
    @DisplayName("el corte de la válvula no toca la bomba")
    void laValvulaNoCierraLaBomba() {
        servicio.onActuatorStopped(new ActuatorStopped("MZ-1", "MZ-1-001", "valve", "v-1", "cancelado"));

        verify(sectores, never()).cambiarBomba(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("sin ack, la dosis se da por terminada pasada su duración más el margen")
    void sinAckCierraPorTiempo() {
        when(sectores.findByActuadorPump("Dosificando")).thenReturn(List.of(dosificando("MZ-1-001")));
        ultimaDosis("MZ-1-001", T0, 10);

        when(reloj.millis()).thenReturn(T0 + 10_000L + MARGEN_MS - 1);
        servicio.closeExpired();
        verify(sectores, never()).cambiarBomba(anyString(), anyString(), anyString());

        when(reloj.millis()).thenReturn(T0 + 10_000L + MARGEN_MS);
        servicio.closeExpired();
        verify(sectores).cambiarBomba("MZ-1-001", "Dosificando", "En espera");
    }

    @Test
    @DisplayName("una dosis sin duración registrada se acota con el tope del firmware")
    void sinDuracionUsaElTopeDelFirmware() {
        when(sectores.findByActuadorPump("Dosificando")).thenReturn(List.of(dosificando("MZ-1-001")));
        ultimaDosis("MZ-1-001", T0, null);

        when(reloj.millis()).thenReturn(T0 + 50_000L + MARGEN_MS - 1);
        servicio.closeExpired();
        verify(sectores, never()).cambiarBomba(anyString(), anyString(), anyString());

        when(reloj.millis()).thenReturn(T0 + 50_000L + MARGEN_MS);
        servicio.closeExpired();
        verify(sectores).cambiarBomba("MZ-1-001", "Dosificando", "En espera");
    }

    @Test
    @DisplayName("un Dosificando sin ninguna dosis en el historial es un estado huérfano: se cierra")
    void sinDosisEnElHistorialSeCierra() {
        when(sectores.findByActuadorPump("Dosificando")).thenReturn(List.of(dosificando("MZ-1-001")));
        when(historial.findFirstBySectorIdAndTipoOrderByTsDesc("MZ-1-001", "Insumo")).thenReturn(Optional.empty());

        servicio.closeExpired();

        verify(sectores).cambiarBomba("MZ-1-001", "Dosificando", "En espera");
    }

    // ------------------------------------------------------------------ add-cola-actuadores-nodo (D-10)

    private static ActuatorProgress progreso(String sectorId, ActuatorProgress.Phase fase, long atMs, long esperaMs) {
        return new ActuatorProgress("MZ-1", sectorId, "pump", "p-1", fase, atMs, esperaMs);
    }

    @Test
    @DisplayName("con la dosis en la cola del nodo la bomba figura En cola mientras la base diga Dosificando")
    void enColaSeMuestraEnCola() {
        servicio.onActuatorProgress(progreso("MZ-1-001", ActuatorProgress.Phase.QUEUED, T0, 30_000L));

        assertThat(servicio.estadoBomba("MZ-1-001", "Dosificando")).isEqualTo("En cola");
        assertThat(servicio.estadoBomba("MZ-1-001", "En espera")).as("sólo pisa el Dosificando").isEqualTo("En espera");
        assertThat(servicio.estadoBomba("MZ-1-002", "Dosificando")).as("otro sector, sin fase").isEqualTo("Dosificando");
    }

    @Test
    @DisplayName("con iniciado figura Dosificando")
    void iniciadoSeMuestraDosificando() {
        servicio.onActuatorProgress(progreso("MZ-1-001", ActuatorProgress.Phase.QUEUED, T0, 30_000L));
        servicio.onActuatorProgress(progreso("MZ-1-001", ActuatorProgress.Phase.STARTED, T0 + 30_000L, 0L));

        assertThat(servicio.estadoBomba("MZ-1-001", "Dosificando")).isEqualTo("Dosificando");
    }

    @Test
    @DisplayName("iniciado: el respaldo por tiempo cuenta desde el arranque y no desde la orden")
    void iniciadoCierraPorTiempoDesdeElArranque() {
        when(sectores.findByActuadorPump("Dosificando")).thenReturn(List.of(dosificando("MZ-1-001")));
        ultimaDosis("MZ-1-001", T0, 10);
        servicio.onActuatorProgress(progreso("MZ-1-001", ActuatorProgress.Phase.STARTED, T0 + 40_000L, 0L));

        when(reloj.millis()).thenReturn(T0 + 40_000L + 10_000L + MARGEN_MS - 1);
        servicio.closeExpired();
        verify(sectores, never()).cambiarBomba(anyString(), anyString(), anyString());

        when(reloj.millis()).thenReturn(T0 + 40_000L + 10_000L + MARGEN_MS);
        servicio.closeExpired();
        verify(sectores).cambiarBomba("MZ-1-001", "Dosificando", "En espera");
    }

    @Test
    @DisplayName("en cola: el plazo suma la espera máxima")
    void enColaCierraPorTiempoConLaEspera() {
        when(sectores.findByActuadorPump("Dosificando")).thenReturn(List.of(dosificando("MZ-1-001")));
        ultimaDosis("MZ-1-001", T0, 10);
        servicio.onActuatorProgress(progreso("MZ-1-001", ActuatorProgress.Phase.QUEUED, T0, 30_000L));

        when(reloj.millis()).thenReturn(T0 + 30_000L + 10_000L + MARGEN_MS - 1);
        servicio.closeExpired();
        verify(sectores, never()).cambiarBomba(anyString(), anyString(), anyString());

        when(reloj.millis()).thenReturn(T0 + 30_000L + 10_000L + MARGEN_MS);
        servicio.closeExpired();
        verify(sectores).cambiarBomba("MZ-1-001", "Dosificando", "En espera");
    }

    @Test
    @DisplayName("el ack final limpia la fase en memoria")
    void elFinalLimpiaLaFase() {
        servicio.onActuatorProgress(progreso("MZ-1-001", ActuatorProgress.Phase.QUEUED, T0, 30_000L));

        servicio.onActuatorStopped(new ActuatorStopped("MZ-1", "MZ-1-001", "pump", "p-1", "ok"));

        assertThat(servicio.estadoBomba("MZ-1-001", "Dosificando")).isEqualTo("Dosificando");
    }

    @Test
    @DisplayName("el progreso de la válvula o de la malla no toca la bomba")
    void soloElProgresoDeLaBombaCuenta() {
        servicio.onActuatorProgress(new ActuatorProgress("MZ-1", "MZ-1-001", "valve", "v-1",
                ActuatorProgress.Phase.QUEUED, T0, 30_000L));

        assertThat(servicio.estadoBomba("MZ-1-001", "Dosificando")).isEqualTo("Dosificando");
    }

    @Test
    @DisplayName("una fase En cola anterior a la última orden de dosis es de una dosis vieja (reemplazada) y se ignora")
    void unaFaseVieja_NoMuestraEnCola() {
        servicio.onActuatorProgress(progreso("MZ-1-001", ActuatorProgress.Phase.QUEUED, T0, 30_000L));
        ultimaDosis("MZ-1-001", T0 + 5_000L, 10);   // se ordenó otra dosis después: la vieja terminó con 'reemplazado'

        assertThat(servicio.estadoBomba("MZ-1-001", "Dosificando")).isEqualTo("Dosificando");
    }
}
