package com.yerbanalytics.backend.mediasombra;

import com.yerbanalytics.backend.dto.NuevoPlanRustificacion;
import com.yerbanalytics.backend.dto.PlanRustificacion;
import com.yerbanalytics.backend.exception.PlanRustificacionConflictoException;
import com.yerbanalytics.backend.exception.PlanRustificacionInvalidoException;
import com.yerbanalytics.backend.model.EtapaPlanRustificacion;
import com.yerbanalytics.backend.model.PlanRustificacionEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.repository.PlanRustificacionRepository;
import com.yerbanalytics.backend.repository.ZonaRepository;
import com.yerbanalytics.backend.service.HistorialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Plan de rustificación por macro-zona")
class PlanRustificacionServiceTest {

    /** 20/10/2026 12:00 en el vivero (UTC-3). */
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-20T15:00:00Z"),
            ZoneId.of("America/Argentina/Buenos_Aires"));
    private static final LocalDate HOY = LocalDate.of(2026, 10, 20);

    private PlanRustificacionRepository planes;
    private HorarioMediasombraService horario;
    private PlanRustificacionService service;

    @BeforeEach
    void setUp() {
        planes = mock(PlanRustificacionRepository.class);
        ZonaRepository zonas = mock(ZonaRepository.class);
        ZonaEntity zona = new ZonaEntity();
        zona.setId("MZ-1");
        zona.setName("Macro-zona 1");
        when(zonas.findById("MZ-1")).thenReturn(Optional.of(zona));
        when(planes.findFirstByZonaIdAndCanceladoEnIsNull(anyString())).thenReturn(Optional.empty());
        horario = mock(HorarioMediasombraService.class);
        service = new PlanRustificacionService(planes, zonas, mock(HistorialService.class), horario, RELOJ);
    }

    private static NuevoPlanRustificacion plan(String inicio, NuevoPlanRustificacion.Etapa... etapas) {
        return new NuevoPlanRustificacion(inicio, List.of(etapas));
    }

    private static NuevoPlanRustificacion.Etapa etapa(int dias, String desde, String hasta) {
        return new NuevoPlanRustificacion.Etapa(dias, desde, hasta);
    }

    private static PlanRustificacionEntity entidad(LocalDate inicio, int... dias) {
        PlanRustificacionEntity p = new PlanRustificacionEntity();
        p.setZonaId("MZ-1");
        p.setFechaInicio(inicio);
        p.setCreadoEn(1L);
        // Etapa 1 de 10 a 13, etapa 2 de 09 a 15, etapa 3 de 08 a 17.
        String[][] ventanas = {{"10:00", "13:00"}, {"09:00", "15:00"}, {"08:00", "17:00"}};
        for (int i = 0; i < dias.length; i++) {
            p.getEtapas().add(new EtapaPlanRustificacion(i + 1, dias[i], ventanas[i][0], ventanas[i][1]));
        }
        return p;
    }

    // ------------------------------------------------------------------ alta

    @Test
    @DisplayName("iniciar guarda el plan con las etapas en orden, lo devuelve en curso y avisa al horario")
    void iniciar() {
        PlanRustificacion creado = service.iniciar("MZ-1",
                plan("2026-10-20", etapa(7, "10:00", "13:00"), etapa(7, "09:00", "15:00")), "Ana");

        ArgumentCaptor<PlanRustificacionEntity> guardado = ArgumentCaptor.forClass(PlanRustificacionEntity.class);
        verify(planes).save(guardado.capture());
        assertThat(guardado.getValue().getEtapas()).extracting(EtapaPlanRustificacion::getOrden).containsExactly(1, 2);
        assertThat(creado.estado()).isEqualTo(PlanRustificacionService.EN_CURSO);
        assertThat(creado.creadoPor()).isEqualTo("Ana");
        assertThat(creado.etapas()).hasSize(2);
        verify(horario).revisarAlConfirmar();
    }

    @Test
    @DisplayName("un plan que empieza mañana queda programado")
    void programado() {
        assertThat(service.iniciar("MZ-1", plan("2026-10-21", etapa(7, "10:00", "13:00")), "Ana").estado())
                .isEqualTo(PlanRustificacionService.PROGRAMADO);
    }

    @Test
    @DisplayName("una zona tiene a lo sumo un plan vigente: el segundo es 409")
    void unoPorZona() {
        when(planes.findFirstByZonaIdAndCanceladoEnIsNull("MZ-1")).thenReturn(Optional.of(entidad(HOY, 7)));

        assertThatThrownBy(() -> service.iniciar("MZ-1", plan("2026-10-20", etapa(7, "10:00", "13:00")), "Ana"))
                .isInstanceOf(PlanRustificacionConflictoException.class);
        verify(planes, never()).save(any());
    }

    @Test
    @DisplayName("rechaza (400) la ventana invertida, la fecha pasada, sin etapas y los días fuera de rango")
    void validaciones() {
        assertThatThrownBy(() -> service.iniciar("MZ-1", plan("2026-10-20", etapa(7, "13:00", "10:00")), "Ana"))
                .isInstanceOf(PlanRustificacionInvalidoException.class).hasMessageContaining("posterior");
        assertThatThrownBy(() -> service.iniciar("MZ-1", plan("2026-10-19", etapa(7, "10:00", "13:00")), "Ana"))
                .isInstanceOf(PlanRustificacionInvalidoException.class).hasMessageContaining("anterior a hoy");
        assertThatThrownBy(() -> service.iniciar("MZ-1", plan("2026-10-20"), "Ana"))
                .isInstanceOf(PlanRustificacionInvalidoException.class).hasMessageContaining("al menos una etapa");
        assertThatThrownBy(() -> service.iniciar("MZ-1", plan("2026-10-20", etapa(0, "10:00", "13:00")), "Ana"))
                .isInstanceOf(PlanRustificacionInvalidoException.class).hasMessageContaining("duración");
        assertThatThrownBy(() -> service.iniciar("MZ-1", plan("2026-10-20", etapa(7, "10", "13:00")), "Ana"))
                .isInstanceOf(PlanRustificacionInvalidoException.class).hasMessageContaining("HH:MM");
        assertThatThrownBy(() -> service.iniciar("MZ-9", plan("2026-10-20", etapa(7, "10:00", "13:00")), "Ana"))
                .isInstanceOf(PlanRustificacionInvalidoException.class).hasMessageContaining("no existe");
        verify(planes, never()).save(any());
    }

    // ------------------------------------------------------------------ baja

    @Test
    @DisplayName("eliminar marca el plan como cancelado y avisa al horario; sin plan es 409")
    void cancelar() {
        PlanRustificacionEntity vigente = entidad(HOY, 7);
        when(planes.findFirstByZonaIdAndCanceladoEnIsNull("MZ-1")).thenReturn(Optional.of(vigente));

        PlanRustificacion cancelado = service.cancelar("MZ-1", "Ana");

        assertThat(cancelado.estado()).isEqualTo(PlanRustificacionService.CANCELADO);
        assertThat(vigente.getCanceladoEn()).isEqualTo(RELOJ.millis());
        verify(horario).revisarAlConfirmar();

        when(planes.findFirstByZonaIdAndCanceladoEnIsNull("MZ-1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.cancelar("MZ-1", "Ana")).isInstanceOf(PlanRustificacionConflictoException.class);
    }

    // ------------------------------------------------------------------ etapa de hoy

    @Test
    @DisplayName("la etapa de hoy sale de sumar los días; antes del inicio no hay")
    void etapaDeHoy() {
        PlanRustificacionEntity p = entidad(HOY, 7, 7, 7);

        assertThat(PlanRustificacionService.etapaDe(p, HOY.minusDays(1))).isEmpty();
        assertThat(PlanRustificacionService.etapaDe(p, HOY).orElseThrow().getOrden()).isEqualTo(1);
        assertThat(PlanRustificacionService.etapaDe(p, HOY.plusDays(6)).orElseThrow().getOrden()).isEqualTo(1);
        assertThat(PlanRustificacionService.etapaDe(p, HOY.plusDays(7)).orElseThrow().getOrden()).isEqualTo(2);
        assertThat(PlanRustificacionService.etapaDe(p, HOY.plusDays(20)).orElseThrow().getOrden()).isEqualTo(3);
    }

    @Test
    @DisplayName("pasada la última etapa sigue rigiendo la última, y el plan sigue en curso hasta eliminarlo")
    void seSostieneLaUltima() {
        PlanRustificacionEntity p = entidad(HOY, 7, 7);

        assertThat(PlanRustificacionService.etapaDe(p, HOY.plusDays(90)).orElseThrow().getOrden()).isEqualTo(2);
        assertThat(PlanRustificacionService.estado(p, HOY.plusDays(90))).isEqualTo(PlanRustificacionService.EN_CURSO);
        assertThat(PlanRustificacionService.ventanaDe(p, HOY.plusDays(90)))
                .contains(new VentanaDiaria(LocalTime.of(9, 0), LocalTime.of(15, 0)));
    }

    @Test
    @DisplayName("un plan eliminado no rige")
    void eliminadoNoRige() {
        PlanRustificacionEntity p = entidad(HOY, 7);
        p.setCanceladoEn(1L);

        assertThat(PlanRustificacionService.estado(p, HOY)).isEqualTo(PlanRustificacionService.CANCELADO);
        assertThat(PlanRustificacionService.etapaDe(p, HOY)).isEmpty();
    }
}
