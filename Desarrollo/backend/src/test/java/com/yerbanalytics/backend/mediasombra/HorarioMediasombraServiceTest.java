package com.yerbanalytics.backend.mediasombra;

import com.yerbanalytics.backend.engine.MallaZona;
import com.yerbanalytics.backend.engine.overrides.RuleOverride;
import com.yerbanalytics.backend.engine.overrides.RuleOverridesStore;
import com.yerbanalytics.backend.model.EtapaPlanRustificacion;
import com.yerbanalytics.backend.model.ManualLockEntity;
import com.yerbanalytics.backend.model.PlanRustificacionEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.repository.ManualLockRepository;
import com.yerbanalytics.backend.repository.PlanRustificacionRepository;
import com.yerbanalytics.backend.repository.ZonaRepository;
import com.yerbanalytics.backend.service.ConfiguracionService;
import com.yerbanalytics.backend.service.HardwareService;
import com.yerbanalytics.backend.service.HistorialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El horario de la mediasombra: actúa sólo en los bordes de la ventana, fuera del motor. */
@DisplayName("Horario de la mediasombra")
class HorarioMediasombraServiceTest {

    private static final ZoneId VIVERO = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final LocalDate HOY = LocalDate.of(2026, 10, 20);

    /** Reloj que se puede mover: la hora local del vivero. */
    private static final class Reloj extends Clock {
        Instant ahora;

        Reloj(LocalTime hora) {
            poner(hora);
        }

        void poner(LocalTime hora) {
            ahora = HOY.atTime(hora).atZone(VIVERO).toInstant();
        }

        @Override
        public ZoneId getZone() {
            return VIVERO;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(ahora, zone);
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }

    private final Reloj reloj = new Reloj(LocalTime.of(7, 0));
    private final List<PlanRustificacionEntity> vigentes = new ArrayList<>();
    private final List<ManualLockEntity> bloqueos = new ArrayList<>();
    private final RuleOverridesStore overrides = new RuleOverridesStore();
    private ZonaEntity zona;
    private HardwareService hardware;
    private MediasombraService mediasombra;
    private HistorialService historial;
    private HorarioMediasombraService horario;

    @BeforeEach
    void setUp() {
        zona = new ZonaEntity();
        zona.setId("MZ-1");
        zona.setName("Macro-zona 1");
        ZonaRepository zonas = mock(ZonaRepository.class);
        when(zonas.findAll()).thenReturn(List.of(zona));
        PlanRustificacionRepository planes = mock(PlanRustificacionRepository.class);
        when(planes.findByCanceladoEnIsNull()).thenAnswer(inv -> List.copyOf(vigentes));
        ManualLockRepository locks = mock(ManualLockRepository.class);
        when(locks.findByActiveTrue()).thenAnswer(inv -> List.copyOf(bloqueos));
        ConfiguracionService configuracion = mock(ConfiguracionService.class);
        when(configuracion.horarioMediasombraPorDefecto())
                .thenReturn(new VentanaDiaria(LocalTime.of(8, 0), LocalTime.of(17, 0)));
        hardware = mock(HardwareService.class);
        when(hardware.mallaDe(anyString())).thenReturn(MallaZona.OPERATIVA);
        mediasombra = mock(MediasombraService.class);
        when(mediasombra.mover(any(), anyBoolean(), anyString(), anyString())).thenAnswer(inv -> {
            zona.setMediasombraAbierta(inv.getArgument(1));
            return true;
        });
        historial = mock(HistorialService.class);
        horario = new HorarioMediasombraService(zonas, planes, locks, configuracion, hardware, mediasombra, historial,
                overrides, reloj);
    }

    private void a(int hora, int minuto) {
        reloj.poner(LocalTime.of(hora, minuto));
        horario.revisar();
    }

    @Test
    @DisplayName("sin plan sigue el horario por defecto: abre al entrar en la ventana y cierra al salir")
    void horarioPorDefecto() {
        a(7, 0);    // arranque: cerrada, y la zona no sabía nada → la cierra
        a(7, 59);
        a(8, 0);    // borde de apertura
        a(12, 0);
        a(17, 0);   // borde de cierre

        verify(mediasombra, times(2)).mover(eq(zona), eq(false), anyString(), contains("Horario por defecto"));
        verify(mediasombra, times(1)).mover(eq(zona), eq(true), anyString(), contains("Horario por defecto"));
    }

    @Test
    @DisplayName("entre bordes no corrige: si el motor la cerró por UV a las 12, queda cerrada hasta el próximo borde")
    void noCorrigeAlMotor() {
        a(9, 0);                         // arranque: abierta
        zona.setMediasombraAbierta(false); // el motor la cerró (pico de UV)
        a(12, 0);
        a(16, 59);

        verify(mediasombra, times(1)).mover(eq(zona), eq(true), anyString(), anyString());
        verify(mediasombra, never()).mover(eq(zona), eq(false), anyString(), anyString());
    }

    @Test
    @DisplayName("al arrancar no reenvía si la zona ya está en la posición programada")
    void arranqueSinCambio() {
        zona.setMediasombraAbierta(true);
        a(9, 0);

        verify(mediasombra, never()).mover(any(), anyBoolean(), anyString(), anyString());
    }

    @Test
    @DisplayName("con plan en curso rige la ventana de su etapa, no la del horario por defecto")
    void conPlan() {
        PlanRustificacionEntity plan = new PlanRustificacionEntity();
        plan.setZonaId("MZ-1");
        plan.setFechaInicio(HOY);
        plan.getEtapas().add(new EtapaPlanRustificacion(1, 7, "10:00", "13:00"));
        vigentes.add(plan);

        a(9, 0);    // por defecto estaría abierta; el plan dice cerrada
        a(10, 0);   // borde de apertura del plan
        a(13, 0);   // borde de cierre del plan

        verify(mediasombra, times(2)).mover(eq(zona), eq(false), anyString(), contains("etapa 1"));
        verify(mediasombra, times(1)).mover(eq(zona), eq(true), anyString(), contains("etapa 1"));
    }

    @Test
    @DisplayName("eliminar el plan es un borde: vuelve el horario por defecto en la revisión siguiente")
    void eliminarElPlan() {
        PlanRustificacionEntity plan = new PlanRustificacionEntity();
        plan.setZonaId("MZ-1");
        plan.setFechaInicio(HOY);
        plan.getEtapas().add(new EtapaPlanRustificacion(1, 7, "14:00", "16:00"));
        vigentes.add(plan);
        a(11, 0);   // el plan la tiene cerrada
        vigentes.clear();
        a(11, 1);   // sin plan: 08–17 → abierta

        verify(mediasombra).mover(eq(zona), eq(true), anyString(), contains("Horario por defecto"));
    }

    @Test
    @DisplayName("con bloqueo manual, sin malla, con falla o en estado seguro no manda nada y lo deja en el historial")
    void impedimentos() {
        a(7, 0);
        ManualLockEntity lock = new ManualLockEntity();
        lock.setZonaId("MZ-1");
        bloqueos.add(lock);
        a(8, 0);    // borde de apertura, bloqueada
        bloqueos.clear();
        when(hardware.mallaDe("MZ-1")).thenReturn(new MallaZona(true, "MEDIASOMBRA_FIN_DE_CARRERA"));
        a(17, 0);   // borde de cierre, malla averiada

        verify(mediasombra, never()).mover(eq(zona), eq(true), anyString(), anyString());
        verify(mediasombra, times(1)).mover(eq(zona), eq(false), anyString(), anyString());   // sólo el del arranque
        verify(historial, times(2)).registrarMediasombra(eq("MZ-1"), any(), eq(HorarioMediasombraService.NAME),
                anyString(), anyString(), anyString(), eq("Omitida"), any(Long.class));
    }

    @Test
    @DisplayName("estado seguro: con el nodo testigo fuera de servicio no mueve la malla")
    void estadoSeguro() {
        a(7, 0);
        zona.setPulsosSinRespuesta(3);
        a(8, 0);

        verify(mediasombra, never()).mover(eq(zona), eq(true), anyString(), anyString());
    }

    @Test
    @DisplayName("apagado desde el simulador no hace nada")
    void apagado() {
        overrides.put(HorarioMediasombraService.NAME, new RuleOverride(false, Map.of(), Set.of()));
        a(9, 0);

        verify(mediasombra, never()).mover(any(), anyBoolean(), anyString(), anyString());
    }

    @Test
    @DisplayName("el reloj del vivero manda aunque el servidor esté en UTC")
    void zonaHorariaDelVivero() {
        // 11:30 UTC son 08:30 en el vivero: dentro de 08–17.
        reloj.ahora = HOY.atTime(11, 30).toInstant(ZoneOffset.UTC);
        horario.revisar();

        verify(mediasombra).mover(eq(zona), eq(true), anyString(), anyString());
    }
}
