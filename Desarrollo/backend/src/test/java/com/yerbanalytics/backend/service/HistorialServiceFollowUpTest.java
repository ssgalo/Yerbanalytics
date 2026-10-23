package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.effectiveness.EffectivenessKind;
import com.yerbanalytics.backend.effectiveness.FollowUp;
import com.yerbanalytics.backend.effectiveness.LatencyLabel;
import com.yerbanalytics.backend.effectiveness.TreatmentBaseline;
import com.yerbanalytics.backend.engine.RuleContextTestFactory;
import com.yerbanalytics.backend.engine.Severity;
import com.yerbanalytics.backend.model.HistorialEventoEntity;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.repository.HistorialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** Los seguimientos de efectividad que abre el historial junto con cada acción (E-01, E-02, E-03). */
@DisplayName("HistorialService - seguimientos de efectividad")
class HistorialServiceFollowUpTest {

    private static final long DAY = 24 * 3_600_000L;

    private HistorialRepository repository;
    private HistorialService service;
    private SectorEntity sector;

    @BeforeEach
    void setUp() {
        repository = mock(HistorialRepository.class);
        service = new HistorialService(repository, new com.yerbanalytics.backend.effectiveness.FollowUpPolicy() {
            @Override
            public long latencyMs(EffectivenessKind kind) {
                return switch (kind) {
                    case IRRIGATION -> 30 * 60_000L;
                    case CORRECTIVE_FERTILIZER -> 14 * DAY;
                    case FUNGICIDE -> 7 * DAY;
                };
            }
        }, Clock.systemUTC());
        sector = RuleContextTestFactory.sectorBasico("MZ-1-045");
        sector.setZona(RuleContextTestFactory.zonaBasica("MZ-1"));
        sector.setDiagnosisEstado("Daño fúngico");
        sector.setDiagnosisSev("Media");
    }

    private HistorialEventoEntity saved() {
        ArgumentCaptor<HistorialEventoEntity> captor = ArgumentCaptor.forClass(HistorialEventoEntity.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void elFitosanitarioConSeguimientoGuardaElEstadoPrevioYLosLindantes() {
        FollowUp followUp = new FollowUp(EffectivenessKind.FUNGICIDE,
                new TreatmentBaseline("Daño fúngico", Severity.MODERATE, Set.of("MZ-1-046", "MZ-1-044")));

        service.registrarInsumo(sector, 20, "Fitosanitario", "FungalDamageRule", followUp);

        HistorialEventoEntity e = saved();
        assertThat(e.getEfectividadTipo()).isEqualTo("FUNGICIDE");
        assertThat(e.getDiagEstadoAntes()).isEqualTo("Daño fúngico");
        assertThat(e.getSev()).isEqualTo("Media");
        assertThat(e.getVecinosAntes()).isEqualTo("MZ-1-044,MZ-1-046");
        assertThat(e.isEvoShow()).isTrue();
        assertThat(e.getEvoVerdict()).isEqualTo("En seguimiento");
        assertThat(e.getEvoAntes()).isEqualTo("Daño fúngico Media");
        assertThat(e.getEvoLatencia()).isEqualTo("7 d");
        assertThat(e.getLatencyMs()).isEqualTo(7 * DAY);
        assertThat(e.getRes()).isEqualTo("En seguimiento");
    }

    @Test
    void laAplicacionSinSeguimientoSeRegistraPeroNoSeEvalua() {
        service.registrarInsumo(sector, 10, "Fertilizante", "NutritionPlanRule");

        HistorialEventoEntity e = saved();
        assertThat(e.getTipo()).isEqualTo("Insumo");
        assertThat(e.getRegla()).isEqualTo("NutritionPlanRule");
        assertThat(e.getEfectividadTipo()).isNull();
        assertThat(e.isEvoShow()).isFalse();
        assertThat(e.getRes()).isEqualTo("Efectiva");
    }

    @Test
    void laDosificacionHeredadaYaNoAbreUnSeguimientoPorHumedad() {
        service.registrarInsumo(sector);

        assertThat(saved().isEvoShow()).isFalse();
    }

    @Test
    void laRevisionQuedaEnElHistorialConQuienLaHizoYQueConstato() {
        HistorialEventoEntity revisado = new HistorialEventoEntity();
        revisado.setSectorId("MZ-1-045");
        revisado.setZonaId("MZ-1");
        revisado.setZonaName("Macro-zona 1");
        revisado.setEfectividadTipo("FUNGICIDE");
        revisado.setEvoMotivo("El daño llegó a lindantes: MZ-1-046.");

        service.registrarRevisionEfectividad("Ana", revisado, "Era otro hongo");

        HistorialEventoEntity e = saved();
        assertThat(e.getTipo()).isEqualTo("Revisión");
        assertThat(e.getSectorId()).isEqualTo("MZ-1-045");
        assertThat(e.getLectura()).contains("Fitosanitario sin efectividad").contains("MZ-1-046");
        assertThat(e.getDecision()).contains("Ana").contains("Era otro hongo");
        assertThat(e.getAccion()).contains("Bloqueo levantado").contains("en el sector");
        assertThat(e.isEvoShow()).isFalse();
        assertThat(e.isBloqueoRepeticion()).isFalse();
    }

    @Test
    void laEsperaSeEscribeParaElHistorial() {
        assertThat(LatencyLabel.of(30 * 60_000L)).isEqualTo("30 min");
        assertThat(LatencyLabel.of(3 * 3_600_000L)).isEqualTo("3 h");
        assertThat(LatencyLabel.of(14 * DAY)).isEqualTo("14 d");
        assertThat(LatencyLabel.of(0)).isEqualTo("0 min");
    }
}
