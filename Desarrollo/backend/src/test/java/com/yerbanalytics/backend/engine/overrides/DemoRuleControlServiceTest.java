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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("DemoRuleControlService")
class DemoRuleControlServiceTest {

    private RuleOverridesStore store;
    private DemoRuleControlService service;

    @BeforeEach
    void setUp() {
        store = new RuleOverridesStore();
        RuleStateRepository repository = mock(RuleStateRepository.class);
        // "Plain" está apagada en el vivero; "ZoneShading", prendida.
        when(repository.findAll()).thenReturn(List.of(new RuleStateEntity("Plain", false, "recalibrando", "Ana", 1L)));
        var effective = new EffectiveRuleOverrides(store, new OperationalRuleStates(repository, List.of()));
        var sinClaves = new FakeControllable("Plain", "Plain", RuleBranch.RIEGO, 5, Set.of(), Set.of());
        service = new DemoRuleControlService(
                new DemoControllableRegistry(List.of(FakeControllable.of("ZoneShading", 50), sinClaves)), store,
                effective);
    }

    @Test
    @DisplayName("una pieza sin override informa el estado operativo y el efectivo que rige")
    void sinOverrideInformaElOperativo() {
        var plain = service.list().stream().filter(e -> e.controllable().name().equals("Plain")).findFirst().orElseThrow();

        assertThat(plain.operationalActive()).isFalse();
        assertThat(plain.effectiveEnabled()).isFalse();
        assertThat(plain.override().enabled()).isNull();
    }

    @Test
    @DisplayName("prender por demo una apagada en el vivero la hace correr y el operativo sigue apagado")
    void prenderPorDemoGana() {
        var estado = service.update("Plain", true, null, null);

        assertThat(estado.effectiveEnabled()).isTrue();
        assertThat(estado.operationalActive()).isFalse();
    }

    @Test
    @DisplayName("cambiar sólo cooldowns no fuerza el estado de una apagada en el vivero")
    void cooldownsNoForzanElEstado() {
        var estado = service.update("Plain", null, Map.of(), Set.of());

        assertThat(estado.effectiveEnabled()).isFalse();
        assertThat(estado.override().enabled()).isNull();
    }

    @Test
    @DisplayName("limpiar la capa de demo devuelve el estado operativo")
    void clearDevuelveElOperativo() {
        service.update("Plain", true, null, null);

        store.clear();

        assertThat(service.list().stream().filter(e -> e.controllable().name().equals("Plain")).findFirst().orElseThrow()
                .effectiveEnabled()).isFalse();
    }

    @Test
    @DisplayName("apagar una pieza que no es Rule queda registrado en el store")
    void apagar() {
        var estado = service.update("ZoneShading", false, null, null);

        assertThat(estado.override().enabled()).isFalse();
        assertThat(store.explicitEnabled("ZoneShading")).contains(false);
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
