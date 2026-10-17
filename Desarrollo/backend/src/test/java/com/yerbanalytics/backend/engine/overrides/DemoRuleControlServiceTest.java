package com.yerbanalytics.backend.engine.overrides;

import com.yerbanalytics.backend.engine.RuleBranch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DemoRuleControlService")
class DemoRuleControlServiceTest {

    private RuleOverridesStore store;
    private DemoRuleControlService service;

    @BeforeEach
    void setUp() {
        store = new RuleOverridesStore();
        var sinClaves = new FakeControllable("Plain", "Plain", RuleBranch.RIEGO, 5, Set.of(), Set.of());
        service = new DemoRuleControlService(
                new DemoControllableRegistry(List.of(FakeControllable.of("ZoneShading", 50), sinClaves)), store);
    }

    @Test
    @DisplayName("apagar una pieza que no es Rule queda registrado en el store")
    void apagar() {
        var estado = service.update("ZoneShading", false, null, null);

        assertThat(estado.override().enabled()).isFalse();
        assertThat(store.isEnabled("ZoneShading")).isFalse();
    }

    @Test
    @DisplayName("un campo ausente conserva el valor actual")
    void camposAusentesConservan() {
        service.update("ZoneShading", null, Map.of("interval", 5.0), Set.of("window"));
        service.update("ZoneShading", false, null, null);

        var override = store.get("ZoneShading");
        assertThat(override.enabled()).isFalse();
        assertThat(override.cooldownMinutes()).containsEntry("interval", 5.0);
        assertThat(override.bypassed()).containsExactly("window");
    }

    @Test
    @DisplayName("nombre desconocido → UnknownControllableException")
    void nombreDesconocido() {
        assertThatThrownBy(() -> service.update("NoExiste", false, null, null))
                .isInstanceOf(UnknownControllableException.class);
    }

    @Test
    @DisplayName("clave de cooldown no declarada → InvalidOverrideKeyException y no se guarda nada")
    void cooldownNoDeclarado() {
        assertThatThrownBy(() -> service.update("ZoneShading", false, Map.of("otra", 1.0), null))
                .isInstanceOf(InvalidOverrideKeyException.class)
                .hasMessageContaining("otra");
        assertThat(store.snapshot()).isEmpty();
    }

    @Test
    @DisplayName("clave de bypass no declarada → InvalidOverrideKeyException")
    void bypassNoDeclarado() {
        assertThatThrownBy(() -> service.update("ZoneShading", null, null, Set.of("otra")))
                .isInstanceOf(InvalidOverrideKeyException.class);
    }

    @Test
    @DisplayName("cooldowns a cero alcanza a todos los controlables con claves y respeta el resto del override")
    void cooldownsACero() {
        service.update("ZoneShading", false, null, Set.of("window"));

        var estados = service.zeroCooldowns();

        assertThat(estados).hasSize(2);
        var override = store.get("ZoneShading");
        assertThat(override.cooldownMinutes()).containsEntry("interval", 0.0);
        assertThat(override.enabled()).isFalse();
        assertThat(override.bypassed()).containsExactly("window");
        assertThat(store.snapshot()).doesNotContainKey("Plain");
    }
}
