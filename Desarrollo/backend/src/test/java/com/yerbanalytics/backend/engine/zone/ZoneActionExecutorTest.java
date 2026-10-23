package com.yerbanalytics.backend.engine.zone;

import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.DetalleAlerta;
import com.yerbanalytics.backend.engine.DetalleNutritionPlan;
import com.yerbanalytics.backend.engine.DetalleOutbreakTreatment;
import com.yerbanalytics.backend.engine.NivelAlerta;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.overrides.RuleOverrides;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.nutrition.NutritionPlanService;
import com.yerbanalytics.backend.mediasombra.MediasombraService;
import com.yerbanalytics.backend.service.HistorialService;
import com.yerbanalytics.backend.supply.ApplicationOutcome;
import com.yerbanalytics.backend.supply.ApplicationService;
import com.yerbanalytics.backend.supply.Supply;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("ZoneActionExecutor · efectos de las reglas de macro-zona")
class ZoneActionExecutorTest {

    private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");

    private NutritionPlanService plan;
    private HistorialService history;
    private ApplicationService applications;
    private SectorEntity neighbor;
    private ZoneActionExecutor executor;
    private ZoneRuleContext ctx;

    @BeforeEach
    void setUp() {
        plan = mock(NutritionPlanService.class);
        history = mock(HistorialService.class);
        applications = mock(ApplicationService.class);
        when(applications.request(any(), any(), anyString(), anyBoolean())).thenReturn(ApplicationOutcome.applied());
        executor = new ZoneActionExecutor(plan, history, applications, mock(MediasombraService.class));
        ZonaEntity zone = new ZonaEntity();
        zone.setId("MZ-2");
        zone.setName("Macro-zona 2");
        neighbor = new SectorEntity();
        neighbor.setId("MZ-2-009");
        neighbor.setZona(zone);
        ctx = new ZoneRuleContext(zone, List.of(neighbor), NOW, Map.of(), Map.of(), false, Set.of(), RuleOverrides.NONE);
    }

    @Test
    void laTransicionDelPlanLaEjecutaElServicioDelPlanConLaReglaQueLaPidio() {
        RuleAction action = new RuleAction(ActionType.UPDATE_NUTRITION_PLAN, "NutritionPlanRule", "tanda",
                new DetalleNutritionPlan(DetalleNutritionPlan.Change.START));

        executor.execute(List.of(action), ctx);

        verify(plan).apply(DetalleNutritionPlan.Change.START, ctx, "NutritionPlanRule");
    }

    @Test
    void unaTransicionSinDetalleNoHaceNada() {
        executor.execute(List.of(new RuleAction(ActionType.UPDATE_NUTRITION_PLAN, "NutritionPlanRule", "sin detalle")), ctx);

        verifyNoInteractions(plan);
    }

    @Test
    void laAlertaQuedaEnElHistorialDeLaMacroZonaConLaHoraDeLaEvaluacion() {
        DetalleAlerta alert = new DetalleAlerta(NivelAlerta.INFO, "CE baja");

        executor.execute(List.of(new RuleAction(ActionType.ALERTA, "LowEcRule", "CE baja", alert)), ctx);

        verify(history).registrarAlerta("MZ-2", "Macro-zona 2", "LowEcRule", alert, NOW.toEpochMilli());
    }

    @Test
    void unaAlertaSinDetalleSeIgnora() {
        executor.execute(List.of(new RuleAction(ActionType.ALERTA, "LowEcRule", "sin detalle")), ctx);

        verify(history, never()).registrarAlerta(anyString(), anyString(), anyString(), any(), anyLong());
    }

    @Test
    void elRegistroDeInaccionDeUnaZonaNoSePersiste() {
        executor.execute(List.of(RuleAction.noopInfo("NutritionPlanRule", "Todavía no")), ctx);

        verifyNoInteractions(plan, history);
    }

    @Test
    void unaAccionQueUnaReglaDeZonaNoDebeEmitirSeIgnora() {
        executor.execute(List.of(RuleAction.of(ActionType.ACTIVAR_VALVULA, "X", "riego")), ctx);

        verifyNoInteractions(plan, history);
    }

    @Test
    void elTratamientoDeUnLindanteSePideAlServicioDeAplicacionSinExigirDiagnostico() {
        RuleAction order = RuleAction.of(ActionType.ACTIVAR_BOMBA, "OutbreakRule", "lindante de un foco",
                new DetalleOutbreakTreatment("MZ-2-009", Supply.FUNGICIDE));

        executor.execute(List.of(order), ctx);

        verify(applications).request(neighbor, Supply.FUNGICIDE, "OutbreakRule", false);
    }

    @Test
    void unTratamientoDeUnSectorQueNoEsDeLaZonaSeIgnora() {
        RuleAction order = RuleAction.of(ActionType.ACTIVAR_BOMBA, "OutbreakRule", "lindante",
                new DetalleOutbreakTreatment("MZ-9-001", Supply.FUNGICIDE));

        executor.execute(List.of(order), ctx);

        verifyNoInteractions(applications);
    }

    @Test
    void unTratamientoSinDetalleSeIgnora() {
        executor.execute(List.of(RuleAction.of(ActionType.ACTIVAR_BOMBA, "OutbreakRule", "sin detalle")), ctx);

        verifyNoInteractions(applications);
    }

    @Test
    void ejecutaTodasLasAccionesEnOrden() {
        DetalleAlerta alert = new DetalleAlerta(NivelAlerta.INFO, "t");
        RuleAction advance = new RuleAction(ActionType.UPDATE_NUTRITION_PLAN, "LowEcRule", "m",
                new DetalleNutritionPlan(DetalleNutritionPlan.Change.ADVANCE));
        RuleAction alertAction = new RuleAction(ActionType.ALERTA, "LowEcRule", "t", alert);

        executor.execute(List.of(advance, alertAction), ctx);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(plan, history);
        order.verify(plan).apply(eq(DetalleNutritionPlan.Change.ADVANCE), eq(ctx), eq("LowEcRule"));
        order.verify(history).registrarAlerta(eq("MZ-2"), eq("Macro-zona 2"), eq("LowEcRule"), eq(alert), anyLong());
    }
}
