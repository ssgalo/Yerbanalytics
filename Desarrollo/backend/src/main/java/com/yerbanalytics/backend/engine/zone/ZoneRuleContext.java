package com.yerbanalytics.backend.engine.zone;

import com.yerbanalytics.backend.engine.HardwareCoverage;
import com.yerbanalytics.backend.engine.HardwareFaults;
import com.yerbanalytics.backend.engine.MallaZona;
import com.yerbanalytics.backend.engine.overrides.RuleOverrides;
import com.yerbanalytics.backend.engine.weather.WeatherForecast;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Snapshot de lo que una {@link ZoneRule} necesita para decidir: la zona, sus sectores y lo que el motor ya armó una vez
 * por zona para las reglas por sector (hardware mapeado, fallas, bloqueos). Lo que cambia con el tiempo (historial de
 * lecturas, aplicaciones) lo consulta cada regla por su servicio: el snapshot sólo fija el "ahora" y la zona.
 *
 * @param hardware      qué tiene mapeado cada sector (S-04); un sector que no figura no tiene nada
 * @param faults        qué está averiado en cada sector (S-05); un sector que no figura no tiene fallas
 * @param zoneLocked    hay un bloqueo manual vigente sobre la zona entera (S-01)
 * @param lockedSectors sectores con un bloqueo manual propio (S-01)
 * @param overrides     overrides en vigor ({@link RuleOverrides#NONE} en producción)
 * @param forecast      el pronóstico del pulso, el mismo que vieron los sectores; {@code null} si no hay uno cacheado
 * @param malla         la mediasombra de la zona: registrada (S-04) y sana (S-05)
 */
public record ZoneRuleContext(
        ZonaEntity zone,
        List<SectorEntity> sectors,
        Instant now,
        Map<String, HardwareCoverage> hardware,
        Map<String, HardwareFaults> faults,
        boolean zoneLocked,
        Set<String> lockedSectors,
        RuleOverrides overrides,
        WeatherForecast forecast,
        MallaZona malla
) {

    public ZoneRuleContext {
        sectors = sectors == null ? List.of() : List.copyOf(sectors);
        hardware = hardware == null ? Map.of() : Map.copyOf(hardware);
        faults = faults == null ? Map.of() : Map.copyOf(faults);
        lockedSectors = lockedSectors == null ? Set.of() : Set.copyOf(lockedSectors);
        overrides = overrides == null ? RuleOverrides.NONE : overrides;
        malla = malla == null ? MallaZona.OPERATIVA : malla;
    }

    /** Sin pronóstico y con la malla operativa: lo que necesitan las reglas que no miran la mediasombra. */
    public ZoneRuleContext(ZonaEntity zone, List<SectorEntity> sectors, Instant now, Map<String, HardwareCoverage> hardware,
                           Map<String, HardwareFaults> faults, boolean zoneLocked, Set<String> lockedSectors,
                           RuleOverrides overrides) {
        this(zone, sectors, now, hardware, faults, zoneLocked, lockedSectors, overrides, null, MallaZona.OPERATIVA);
    }

    /** Copia con otros overrides: los pone el motor, quien arma el contexto no sabe que existen. */
    public ZoneRuleContext withOverrides(RuleOverrides newOverrides) {
        return new ZoneRuleContext(zone, sectors, now, hardware, faults, zoneLocked, lockedSectors, newOverrides,
                forecast, malla);
    }

    /** Cooldown (minutos) que reemplaza al configurado para {@code key} de la regla, si hay uno; {@code 0} lo elimina. */
    public Optional<Double> cooldownOverrideMinutes(String ruleName, String key) {
        return overrides.cooldownMinutes(ruleName, key);
    }

    /** {@code true} si la guarda {@code key} de la regla no se aplica. {@code false} en producción. */
    public boolean isBypassed(String ruleName, String key) {
        return overrides.isBypassed(ruleName, key);
    }

    /**
     * Por qué a este sector no se le puede ordenar una dosificación autónoma, o vacío si se puede. Son las mismas razones
     * por las que la cadena por sector corta su rama de insumos: bloqueo manual (S-01), sin bomba registrada (S-04) y
     * falla hidráulica (S-05). Un sector sin ninguna de ellas puede quedar igual diferido por las precondiciones de
     * aplicación (ventana, humedad…): eso lo resuelve el {@code ApplicationService}, no esta pregunta.
     */
    public Optional<String> dosingBlock(SectorEntity sector) {
        String id = sector.getId();
        if (zoneLocked || lockedSectors.contains(id)) {
            return Optional.of("bloqueo manual vigente");
        }
        if (!hardware.getOrDefault(id, HardwareCoverage.NONE).canDose()) {
            return Optional.of("sin bomba peristáltica registrada");
        }
        if (faults.getOrDefault(id, HardwareFaults.NONE).hasHydraulic()) {
            return Optional.of("falla hidráulica sin reparar");
        }
        return Optional.empty();
    }

    /** Los sectores de la zona a los que sí se les puede ordenar una dosificación. */
    public List<SectorEntity> dosableSectors() {
        return sectors.stream().filter(s -> dosingBlock(s).isEmpty()).toList();
    }
}
