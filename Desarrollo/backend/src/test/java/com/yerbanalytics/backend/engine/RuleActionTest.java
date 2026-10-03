package com.yerbanalytics.backend.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Tarea 3.1: la acción tipada es opcional y retrocompatible. */
@DisplayName("RuleAction tipada")
class RuleActionTest {

    @Test
    void ofYNoopInfoSiguenDandoDetalleNulo() {
        assertThat(RuleAction.of(ActionType.ABORT_RIEGO, "R", "m").detalle()).isNull();
        assertThat(RuleAction.noopInfo("R", "m").detalle()).isNull();
        assertThat(new RuleAction(ActionType.NOOP_INFO, "R", "m").detalle()).isNull();
    }

    @Test
    void unaAccionConDetalleDeRiegoLoConserva() {
        DetalleRiego d = new DetalleRiego(4.2, 504, 44.0, false);

        RuleAction a = RuleAction.of(ActionType.ACTIVAR_VALVULA, "RiegoPorDeficitRule", "Regar 4,2 L", d);

        assertThat(a.detalle()).isSameAs(d);
        assertThat(a.type()).isEqualTo(ActionType.ACTIVAR_VALVULA);
        assertThat(a.ruleName()).isEqualTo("RiegoPorDeficitRule");
        assertThat(a.motivo()).isEqualTo("Regar 4,2 L");
    }

    @Test
    void unaAccionConDetalleDeAlertaLoConserva() {
        DetalleAlerta d = new DetalleAlerta(NivelAlerta.CRITICAL, "Déficit hídrico crítico");

        RuleAction a = RuleAction.of(ActionType.ALERTA, "DeficitCriticoRule", "Déficit", d);

        assertThat(a.detalle()).isEqualTo(d);
        assertThat(((DetalleAlerta) a.detalle()).nivel()).isEqualTo(NivelAlerta.CRITICAL);
    }

    @Test
    void alertaNoEsBloqueante() {
        assertThat(ActionType.ALERTA.isBlocking()).isFalse();
    }
}
