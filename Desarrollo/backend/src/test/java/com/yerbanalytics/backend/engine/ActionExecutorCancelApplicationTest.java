package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.engine.riego.ColaRiego;
import com.yerbanalytics.backend.engine.traza.OrigenEvaluacion;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.service.HistorialService;
import com.yerbanalytics.backend.supply.ApplicationService;
import com.yerbanalytics.backend.supply.Supply;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Una regla que corta la rama de insumos y trae el detalle {@link CancelPendingApplication} (F-C1: apareció una causa que el
 * fertilizante no corrige) retira también el pedido que ella u otra regla dejó pendiente.
 */
@DisplayName("ActionExecutor - retiro de la aplicación pendiente")
class ActionExecutorCancelApplicationTest {

    private HistorialService history;
    private ApplicationService applications;
    private ActionExecutor executor;
    private RuleContext ctx;

    @BeforeEach
    void setUp() {
        history = mock(HistorialService.class);
        applications = mock(ApplicationService.class);
        executor = new ActionExecutor(history, mock(ComandoActuadorPublisher.class), new ColaRiego(), applications);
        ZonaEntity zone = RuleContextTestFactory.zonaBasica("MZ-1");
        SectorEntity sector = RuleContextTestFactory.sectorBasico("MZ-1-045");
        sector.setZona(zone);
        ctx = new RuleContext(sector, zone, List.of(), List.of(), RuleContextTestFactory.defaultConfig(), "ok",
                Instant.parse("2026-10-22T10:00:00Z"), null, false);
    }

    @Test
    @DisplayName("el corte de insumos con el detalle retira el pedido pendiente de esa regla y deja el Registro de Inacción")
    void cancelsThePendingApplicationOfTheRule() {
        RuleAction abort = RuleAction.of(ActionType.ABORT_INSUMO, "ChlorosisNonNutritionalRule", "asfixia radicular",
                new CancelPendingApplication(Supply.FERTILIZER, "ChlorosisNutritionalRule"));

        executor.execute(List.of(abort), ctx, OrigenEvaluacion.TELEMETRIA);

        verify(applications).cancelPending("MZ-1-045", Supply.FERTILIZER, "ChlorosisNutritionalRule");
        verify(history).registrarInaccion(any(), org.mockito.ArgumentMatchers.eq(ActionType.ABORT_INSUMO),
                org.mockito.ArgumentMatchers.eq("ChlorosisNonNutritionalRule"), anyString());
    }

    @Test
    @DisplayName("un corte de insumos sin detalle no toca ninguna aplicación")
    void withoutDetailNothingIsCancelled() {
        executor.execute(List.of(RuleAction.of(ActionType.ABORT_INSUMO, "DailyDoseLimitRule", "tope")), ctx,
                OrigenEvaluacion.TELEMETRIA);

        verifyNoInteractions(applications);
    }
}
