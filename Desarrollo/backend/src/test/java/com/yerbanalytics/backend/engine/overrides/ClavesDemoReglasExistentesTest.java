package com.yerbanalytics.backend.engine.overrides;

import com.yerbanalytics.backend.engine.rules.CicloLecturaRiegoRule;
import com.yerbanalytics.backend.engine.rules.DeficitCriticoRule;
import com.yerbanalytics.backend.engine.rules.FueraDeVentanaRiegoRule;
import com.yerbanalytics.backend.engine.rules.ManualLockRule;
import com.yerbanalytics.backend.engine.rules.PausaTrasAplicacionRule;
import com.yerbanalytics.backend.engine.rules.PosponerPorLluviaRule;
import com.yerbanalytics.backend.engine.rules.RiegoPorDeficitRule;
import com.yerbanalytics.backend.engine.rules.StaleSensorRule;
import com.yerbanalytics.backend.engine.rules.SustratoSaturadoRule;
import com.yerbanalytics.backend.repository.ManualLockRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Las claves de cooldown y de bypass que el simulador ya usa para las reglas existentes: alinear el motor con
 * {@code reglas_v2} no puede renombrarlas ni quitarlas, o los escenarios del simulador dejarían de funcionar.
 */
@DisplayName("Claves de control de demo de las reglas existentes")
class ClavesDemoReglasExistentesTest {

    private static void expect(DemoControllable piece, Set<String> cooldowns, Set<String> bypasses) {
        assertThat(piece.cooldownKeys()).as(piece.name() + " cooldowns").isEqualTo(cooldowns);
        assertThat(piece.bypassKeys()).as(piece.name() + " bypass").isEqualTo(bypasses);
    }

    @Test
    @DisplayName("bloqueo manual: bypass lock")
    void manualLock() {
        expect(new ManualLockRule(mock(ManualLockRepository.class)), Set.of(), Set.of("lock"));
    }

    @Test
    @DisplayName("lectura vencida: sin cooldowns ni bypass (se apaga con enabled=false)")
    void staleSensor() {
        expect(new StaleSensorRule(), Set.of(), Set.of());
    }

    @Test
    @DisplayName("un riego por ciclo: cooldown cycle y bypass in-progress")
    void cycle() {
        expect(new CicloLecturaRiegoRule(), Set.of("cycle"), Set.of("in-progress"));
    }

    @Test
    @DisplayName("R-02: cooldown cap")
    void criticalDeficit() {
        expect(new DeficitCriticoRule(), Set.of("cap"), Set.of());
    }

    @Test
    @DisplayName("R-05: bypass window")
    void outsideWindow() {
        expect(new FueraDeVentanaRiegoRule(), Set.of(), Set.of("window"));
    }

    @Test
    @DisplayName("R-06: cooldown pause")
    void pauseAfterApplication() {
        expect(new PausaTrasAplicacionRule(), Set.of("pause"), Set.of());
    }

    @Test
    @DisplayName("R-01, R-03 y R-04: sin claves propias")
    void rulesWithoutKeys() {
        expect(new RiegoPorDeficitRule(), Set.of(), Set.of());
        expect(new PosponerPorLluviaRule(), Set.of(), Set.of());
        expect(new SustratoSaturadoRule(), Set.of(), Set.of());
    }
}
