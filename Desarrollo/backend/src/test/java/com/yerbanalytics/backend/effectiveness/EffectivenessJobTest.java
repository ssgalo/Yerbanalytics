package com.yerbanalytics.backend.effectiveness;

import com.yerbanalytics.backend.config.ZonaHorariaVivero;
import com.yerbanalytics.backend.engine.DetalleAlerta;
import com.yerbanalytics.backend.engine.NivelAlerta;
import com.yerbanalytics.backend.engine.ReglaTestSupport;
import com.yerbanalytics.backend.engine.RuleContextTestFactory;
import com.yerbanalytics.backend.engine.overrides.RuleOverrides;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.model.HistorialEventoEntity;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.mqtt.ComandoZonaPublisher;
import com.yerbanalytics.backend.repository.HistorialRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
import com.yerbanalytics.backend.repository.ZonaRepository;
import com.yerbanalytics.backend.service.HardwareService;
import com.yerbanalytics.backend.service.HistorialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("EffectivenessJob: resuelve los seguimientos abiertos (E-01, E-02, E-03, F-05)")
class EffectivenessJobTest {

    private static final Instant NOW = Instant.parse("2026-10-22T12:00:00Z");
    private static final long MINUTE = 60_000L;
    private static final long DAY = 24 * 60 * MINUTE;

    private Clock clock;
    private RuleOverrides overrides;
    private HistorialRepository history;
    private SectorRepository sectors;
    private ZonaRepository zones;
    private HardwareService hardware;
    private ComandoZonaPublisher publisher;
    private VerificationReads verification;
    private SectorNeighbors neighbors;
    private HistorialService historial;
    private EffectivenessJob job;
    private ZonaEntity zone;
    private SectorEntity sector;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(NOW, ZonaHorariaVivero.ZONA);
        overrides = mock(RuleOverrides.class);
        when(overrides.isEnabled(anyString())).thenReturn(true);
        when(overrides.cooldownMinutes(anyString(), anyString())).thenReturn(Optional.empty());
        history = mock(HistorialRepository.class);
        sectors = mock(SectorRepository.class);
        zones = mock(ZonaRepository.class);
        hardware = mock(HardwareService.class);
        when(hardware.witnessSector(anyString())).thenReturn(Optional.empty());
        publisher = mock(ComandoZonaPublisher.class);
        when(publisher.leerAhora(anyString())).thenReturn(new ComandoZonaPublisher.Resultado(true, "c-1", null));
        verification = new VerificationReads();
        neighbors = mock(SectorNeighbors.class);
        historial = mock(HistorialService.class);
        CatalogoParametrosService catalog = mock(CatalogoParametrosService.class);
        when(catalog.vigentes()).thenReturn(ReglaTestSupport.fabrica());
        EffectivenessService service = new EffectivenessService(catalog, overrides, history, neighbors);
        job = new EffectivenessJob(service, history, sectors, zones, hardware, publisher, verification, neighbors, historial,
                clock);

        zone = RuleContextTestFactory.zonaBasica("MZ-1");
        zone.setName("Macro-zona 1");
        sector = RuleContextTestFactory.sectorBasico("MZ-1-045");
        sector.setZona(zone);
        when(zones.findById("MZ-1")).thenReturn(Optional.of(zone));
        when(sectors.findById("MZ-1-045")).thenReturn(Optional.of(sector));
        when(history.findByEvoShowTrueAndEvoEvaluadoTsIsNullAndEfectividadTipoIsNull()).thenReturn(List.of());
    }

    private HistorialEventoEntity irrigation(long ts, int valveSeconds, double before) {
        HistorialEventoEntity e = new HistorialEventoEntity();
        e.setId("ev-riego");
        e.setSectorId("MZ-1-045");
        e.setZonaId("MZ-1");
        e.setZonaName("Macro-zona 1");
        e.setTipo("Riego");
        e.setTs(ts);
        e.setDuracionSeg(valveSeconds);
        e.setEfectividadTipo(EffectivenessKind.IRRIGATION.name());
        e.setValorAntes(before);
        e.setEvoShow(true);
        e.setEvoVerdict("En seguimiento");
        return e;
    }

    private HistorialEventoEntity treatment(EffectivenessKind kind, long ts, String state, String severity, String neighborIds) {
        HistorialEventoEntity e = new HistorialEventoEntity();
        e.setId("ev-trat");
        e.setSectorId("MZ-1-045");
        e.setZonaId("MZ-1");
        e.setZonaName("Macro-zona 1");
        e.setTipo("Insumo");
        e.setTs(ts);
        e.setSev(severity);
        e.setEfectividadTipo(kind.name());
        e.setDiagEstadoAntes(state);
        e.setVecinosAntes(neighborIds);
        e.setEvoShow(true);
        e.setEvoVerdict("En seguimiento");
        return e;
    }

    private void open(HistorialEventoEntity... events) {
        when(history.findByEvoShowTrueAndEvoEvaluadoTsIsNullAndEfectividadTipoIsNotNull()).thenReturn(List.of(events));
    }

    private void diagnose(String state, String severity, Instant at) {
        sector.setDiagnosisEstado(state);
        sector.setDiagnosisSev(severity);
        sector.setDiagnosisConf(92.0);
        sector.setDiagnosisTs(at.toEpochMilli());
    }

    @Test
    void apagadoNoEvaluaNada() {
        when(overrides.isEnabled(EffectivenessService.NAME)).thenReturn(false);
        open(irrigation(NOW.toEpochMilli() - 3_600_000L, 100, 44));

        job.evaluatePending();

        verify(publisher, never()).leerAhora(anyString());
        verify(history, never()).saveAll(any());
    }

    @Nested
    @DisplayName("E-01 riego")
    class Irrigation {

        @Test
        void antesDeLosTreintaMinutosDelCierreNoHaceNada() {
            // Orden hace 20 min, válvula 5 min: cierra hace 15 → faltan 15 min.
            open(irrigation(NOW.toEpochMilli() - 20 * MINUTE, 300, 44));

            job.evaluatePending();

            verify(publisher, never()).leerAhora(anyString());
        }

        @Test
        void alVencerPideLaLecturaDeVerificacionALaZonaUnaSolaVez() {
            open(irrigation(NOW.toEpochMilli() - 40 * MINUTE, 300, 44));

            job.evaluatePending();
            job.evaluatePending();

            verify(publisher).leerAhora("MZ-1");
            assertThat(verification.needsRequest("MZ-1", NOW.toEpochMilli())).isFalse();
        }

        @Test
        void siElPedidoNoSePublicaNoQuedaMarcadoYSeReintenta() {
            when(publisher.leerAhora(anyString())).thenReturn(new ComandoZonaPublisher.Resultado(false, "c-1", "broker caído"));
            open(irrigation(NOW.toEpochMilli() - 40 * MINUTE, 300, 44));

            job.evaluatePending();

            assertThat(verification.needsRequest("MZ-1", NOW.toEpochMilli())).isTrue();
        }

        @Test
        void conLaLecturaContestadaEvaluaConLaHumedadDeLaVerificacion() {
            HistorialEventoEntity riego = irrigation(NOW.toEpochMilli() - 40 * MINUTE, 300, 44);
            open(riego);
            long requestedAt = NOW.toEpochMilli() - 2 * MINUTE;
            verification.expect("MZ-1", requestedAt);
            verification.consume("MZ-1", requestedAt + 30_000);
            zone.setRaw("humSus", 63.0);
            zone.setHumSusTs(requestedAt + 30_000);

            job.evaluatePending();

            assertThat(riego.getEvoVerdict()).isEqualTo("Efectiva");
            assertThat(riego.getEvoAhora()).isEqualTo("63");
            assertThat(riego.getEvoDelta()).isEqualTo("+19");
            assertThat(riego.getEvoEvaluadoTs()).isEqualTo(NOW.toEpochMilli());
            assertThat(riego.isBloqueoRepeticion()).isFalse();
            verify(history).saveAll(any());
            assertThat(verification.answeredAfter("MZ-1")).isEmpty();
        }

        @Test
        void unaSubidaChicaDejaElRiegoSinEfectividadYBloqueando() {
            HistorialEventoEntity riego = irrigation(NOW.toEpochMilli() - 40 * MINUTE, 300, 44);
            open(riego);
            long requestedAt = NOW.toEpochMilli() - 2 * MINUTE;
            verification.expect("MZ-1", requestedAt);
            verification.consume("MZ-1", requestedAt + 30_000);
            zone.setRaw("humSus", 48.0);
            zone.setHumSusTs(requestedAt + 30_000);

            job.evaluatePending();

            assertThat(riego.getEvoVerdict()).isEqualTo("Sin efectividad");
            assertThat(riego.getEvoDelta()).isEqualTo("+4");
            assertThat(riego.isBloqueoRepeticion()).isTrue();
            assertThat(riego.getEvoMotivo()).contains("44").contains("48");
            verify(historial).registrarAlerta(eq("MZ-1"), eq("Macro-zona 1"), eq("EffectivenessService"),
                    org.mockito.ArgumentMatchers.argThat((DetalleAlerta d) -> d.nivel() == NivelAlerta.CRITICAL
                            && d.texto().contains("toda la macro-zona MZ-1") && d.texto().contains("revisión")), anyLong());
        }

        @Test
        void siLaHumedadDeLaVerificacionNoEntroSeEsperaOtraLectura() {
            HistorialEventoEntity riego = irrigation(NOW.toEpochMilli() - 40 * MINUTE, 300, 44);
            open(riego);
            long requestedAt = NOW.toEpochMilli() - 2 * MINUTE;
            verification.expect("MZ-1", requestedAt);
            verification.consume("MZ-1", requestedAt + 30_000);
            zone.setRaw("humSus", 63.0);
            zone.setHumSusTs(requestedAt - 60_000);   // la sonda no entró: la humedad es la vieja

            job.evaluatePending();

            assertThat(riego.getEvoEvaluadoTs()).isNull();
        }

        @Test
        void conElSectorDelNodoTestigoRegistradoSoloSeEvaluaElRiegoDeEseSector() {
            when(hardware.witnessSector("MZ-1")).thenReturn(Optional.of("MZ-1-001"));
            HistorialEventoEntity riego = irrigation(NOW.toEpochMilli() - 40 * MINUTE, 300, 44);
            open(riego);

            job.evaluatePending();

            assertThat(riego.isEvoShow()).as("deja de ser un seguimiento").isFalse();
            assertThat(riego.getEvoEvaluadoTs()).isNull();
            verify(publisher, never()).leerAhora(anyString());
        }

        @Test
        void elSimuladorPuedeEliminarLaEsperaDeLaVerificacion() {
            when(overrides.cooldownMinutes(EffectivenessService.NAME, EffectivenessService.COOLDOWN_VERIFICATION))
                    .thenReturn(Optional.of(0.0));
            open(irrigation(NOW.toEpochMilli() - 2 * MINUTE, 60, 44));

            job.evaluatePending();

            verify(publisher).leerAhora("MZ-1");
        }
    }

    @Nested
    @DisplayName("E-02 y E-03 tratamientos")
    class Treatments {

        @Test
        void fertilizanteQueMejoroALosCatorceDiasEsEfectivo() {
            HistorialEventoEntity e = treatment(EffectivenessKind.CORRECTIVE_FERTILIZER, NOW.toEpochMilli() - 15 * DAY,
                    "Clorosis", "Alta", "");
            open(e);
            diagnose("Clorosis", "Media", NOW.minusSeconds(3600));

            job.evaluatePending();

            assertThat(e.getEvoVerdict()).isEqualTo("Efectiva");
            assertThat(e.isBloqueoRepeticion()).isFalse();
        }

        @Test
        void fertilizanteSinMejoraALosCatorceDiasEsSinEfectividadYBloquea() {
            HistorialEventoEntity e = treatment(EffectivenessKind.CORRECTIVE_FERTILIZER, NOW.toEpochMilli() - 15 * DAY,
                    "Clorosis", "Alta", "");
            open(e);
            diagnose("Clorosis", "Alta", NOW.minusSeconds(3600));

            job.evaluatePending();

            assertThat(e.getEvoVerdict()).isEqualTo("Sin efectividad");
            assertThat(e.isBloqueoRepeticion()).isTrue();
            verify(historial).registrarAlerta(eq("MZ-1"), eq("Macro-zona 1"), eq("EffectivenessService"),
                    org.mockito.ArgumentMatchers.argThat((DetalleAlerta d) -> d.nivel() == NivelAlerta.CRITICAL
                            && d.texto().contains("en el sector MZ-1-045")), anyLong());
        }

        @Test
        void antesDeLaEsperaYSinCuraNoSeEvalua() {
            HistorialEventoEntity e = treatment(EffectivenessKind.CORRECTIVE_FERTILIZER, NOW.toEpochMilli() - 5 * DAY,
                    "Clorosis", "Alta", "");
            open(e);
            diagnose("Clorosis", "Media", NOW.minusSeconds(3600));

            job.evaluatePending();

            assertThat(e.getEvoEvaluadoTs()).isNull();
        }

        @Test
        void fitosanitarioQueSePropagoEsSinEfectividad() {
            HistorialEventoEntity e = treatment(EffectivenessKind.FUNGICIDE, NOW.toEpochMilli() - 8 * DAY,
                    "Daño fúngico", "Media", "MZ-1-044");
            open(e);
            diagnose("Daño fúngico", "Media", NOW.minusSeconds(3600));
            when(neighbors.affectedAround(any(SectorEntity.class), any())).thenReturn(Set.of("MZ-1-044", "MZ-1-046"));

            job.evaluatePending();

            assertThat(e.getEvoVerdict()).isEqualTo("Sin efectividad");
            assertThat(e.getEvoMotivo()).contains("MZ-1-046");
        }

        @Test
        void fitosanitarioEstableEsEfectivoAunqueElDanoSigaVisible() {
            HistorialEventoEntity e = treatment(EffectivenessKind.FUNGICIDE, NOW.toEpochMilli() - 8 * DAY,
                    "Daño fúngico", "Media", "MZ-1-044");
            open(e);
            diagnose("Daño fúngico", "Media", NOW.minusSeconds(3600));
            when(neighbors.affectedAround(any(SectorEntity.class), any())).thenReturn(Set.of("MZ-1-044"));

            job.evaluatePending();

            assertThat(e.getEvoVerdict()).isEqualTo("Efectiva");
        }

        @Test
        void sinDiagnosticoVigenteVencidaLaEsperaSigueAbiertoDuranteLaGracia() {
            HistorialEventoEntity e = treatment(EffectivenessKind.FUNGICIDE, NOW.toEpochMilli() - 8 * DAY,
                    "Daño fúngico", "Media", "");
            open(e);
            diagnose("Daño fúngico", "Media", NOW.minusSeconds(200 * 3600));   // viejo

            job.evaluatePending();

            assertThat(e.getEvoEvaluadoTs()).isNull();
        }

        @Test
        void sinDiagnosticoVigenteDespuesDeLaGraciaEsSinEfectividadPorFaltaDeDato() {
            HistorialEventoEntity e = treatment(EffectivenessKind.FUNGICIDE, NOW.toEpochMilli() - 10 * DAY,
                    "Daño fúngico", "Media", "");
            open(e);
            diagnose("Daño fúngico", "Media", NOW.minusSeconds(200 * 3600));

            job.evaluatePending();

            assertThat(e.getEvoVerdict()).isEqualTo("Sin efectividad");
            assertThat(e.getEvoMotivo()).contains("falta de dato");
        }

        @Test
        void elSimuladorPuedeEliminarLaLatenciaDelFitosanitario() {
            when(overrides.cooldownMinutes(EffectivenessService.NAME, EffectivenessService.COOLDOWN_FUNGICIDE))
                    .thenReturn(Optional.of(0.0));
            HistorialEventoEntity e = treatment(EffectivenessKind.FUNGICIDE, NOW.toEpochMilli() - MINUTE,
                    "Daño fúngico", "Media", "");
            open(e);
            diagnose("Daño fúngico", "Media", NOW.minusSeconds(30));
            when(neighbors.affectedAround(any(SectorEntity.class), any())).thenReturn(Set.of());

            job.evaluatePending();

            assertThat(e.getEvoVerdict()).isEqualTo("Efectiva");
        }
    }

    @Nested
    @DisplayName("F-05 sano después de un tratamiento")
    class HealthyAfterTreatment {

        @Test
        void unSectorQueVuelveASanoCierraElSeguimientoComoEfectivoYAvisaConInfo() {
            HistorialEventoEntity e = treatment(EffectivenessKind.FUNGICIDE, NOW.toEpochMilli() - 4 * DAY,
                    "Daño fúngico", "Alta", "");
            open(e);
            diagnose("Sano", "—", NOW.minusSeconds(3600));

            job.evaluatePending();

            assertThat(e.getEvoVerdict()).isEqualTo("Efectiva");
            assertThat(e.getEvoMotivo()).contains("Sano");
            verify(historial).registrarAlerta(eq("MZ-1"), eq("Macro-zona 1"), eq("EffectivenessService"),
                    org.mockito.ArgumentMatchers.argThat((DetalleAlerta d) -> d.nivel() == NivelAlerta.INFO
                            && d.texto().contains("MZ-1-045")), anyLong());
        }

        @Test
        void unDiagnosticoSanoAnteriorAlTratamientoNoCuenta() {
            HistorialEventoEntity e = treatment(EffectivenessKind.FUNGICIDE, NOW.toEpochMilli() - 4 * DAY,
                    "Daño fúngico", "Alta", "");
            open(e);
            diagnose("Sano", "—", NOW.minusSeconds(6 * DAY / 1000));

            job.evaluatePending();

            assertThat(e.getEvoEvaluadoTs()).isNull();
            verify(historial, never()).registrarAlerta(anyString(), anyString(), anyString(), any(), anyLong());
        }
    }

    @Nested
    @DisplayName("Seguimientos de antes de la evaluación v2")
    class Legacy {

        @Test
        void unRiegoAbiertoPasaAEvaluarseComoRiego() {
            HistorialEventoEntity old = new HistorialEventoEntity();
            old.setTipo("Riego");
            old.setEvoShow(true);
            when(history.findByEvoShowTrueAndEvoEvaluadoTsIsNullAndEfectividadTipoIsNull()).thenReturn(List.of(old));

            job.evaluatePending();

            assertThat(old.getEfectividadTipo()).isEqualTo("IRRIGATION");
            assertThat(old.isEvoShow()).isTrue();
        }

        @Test
        void unSeguimientoGenericoDeOtroTipoSeCierraSinVeredicto() {
            HistorialEventoEntity old = new HistorialEventoEntity();
            old.setTipo("Insumo");
            old.setEvoShow(true);
            when(history.findByEvoShowTrueAndEvoEvaluadoTsIsNullAndEfectividadTipoIsNull()).thenReturn(List.of(old));

            job.evaluatePending();

            assertThat(old.isEvoShow()).isFalse();
            assertThat(old.getEfectividadTipo()).isNull();
        }
    }
}
