package com.yerbanalytics.backend.supply;

import com.yerbanalytics.backend.config.ZonaHorariaVivero;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.overrides.DemoControllable;
import com.yerbanalytics.backend.engine.overrides.RuleOverrides;
import com.yerbanalytics.backend.engine.parametros.ConsumidorParametros;
import com.yerbanalytics.backend.engine.parametros.DeclaredParameters;
import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import com.yerbanalytics.backend.engine.parametros.ParametrosInsumo;
import com.yerbanalytics.backend.engine.parametros.ParametrosVigentes;
import com.yerbanalytics.backend.engine.parametros.VentanaHoraria;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Precondiciones P-D1…P-D5 de una aplicación de insumo (reglas_v2 §8.2), en un solo lugar. Las usan el
 * {@link ApplicationService} al pedir una aplicación y al reintentar las diferidas: la regla DECIDE aplicar, esto
 * dice si es el momento. Pedido y reintento leen los mismos parámetros del catálogo; acá sólo está la condición.
 *
 * <p>Es {@link DemoControllable} para que el simulador pueda apagarlas por completo (el servicio no aplica nada),
 * saltear cada guarda o acortar los intervalos. Sin overrides (producción) rigen todas.
 *
 * <p>Se evalúan en orden y gana la primera que falla. P-D1 <b>rechaza</b> (un diagnóstico viejo no se arregla
 * esperando); el resto <b>difiere</b> a la próxima ventana apta. Un dato que falta no es un dato bueno: sin humedad
 * de sustrato no se aplica; sin pronóstico, en cambio, la lluvia no bloquea (se asume que no llueve), y sin ninguna
 * temperatura tampoco hay con qué bloquear.
 */
@Component
public class ApplicationPreconditions implements DemoControllable, ConsumidorParametros {

    public static final String NAME = "ApplicationPreconditions";

    public static final String BYPASS_DIAGNOSIS = "diagnosis";
    public static final String BYPASS_DAILY = "daily";
    public static final String BYPASS_WINDOW = "window";
    public static final String BYPASS_TEMPERATURE = "temperature";
    public static final String BYPASS_RAIN = "rain";
    public static final String BYPASS_MOISTURE = "moisture";
    /** F-06: el tope de fitosanitarios por sector. */
    public static final String BYPASS_CAP = "cap";
    /** La evalúa el {@link ApplicationService} (el stock no es un dato del sector), pero se declara acá. */
    public static final String BYPASS_STOCK = "stock";

    private static final double MINUTES_PER_DAY = 24.0 * 60;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String label() {
        return "💉 Precondiciones de aplicación";
    }

    @Override
    public RuleBranch branch() {
        return RuleBranch.INSUMO;
    }

    @Override
    public int priority() {
        return 20;
    }

    @Override
    public Set<String> bypassKeys() {
        return Set.of(BYPASS_DIAGNOSIS, BYPASS_CAP, BYPASS_DAILY, BYPASS_WINDOW, BYPASS_TEMPERATURE, BYPASS_RAIN, BYPASS_MOISTURE,
                BYPASS_STOCK);
    }

    @Override
    public Set<String> cooldownKeys() {
        return Set.of(Supply.FERTILIZER.intervalCooldownKey(), Supply.FUNGICIDE.intervalCooldownKey());
    }

    @Override
    public List<DefinicionParametro> parametros() {
        return List.of(ParametrosInsumo.FERTILIZER_INTERVAL, ParametrosInsumo.FUNGICIDE_INTERVAL,
                ParametrosInsumo.FUNGICIDE_CAP,
                ParametrosInsumo.MORNING_WINDOW, ParametrosInsumo.AFTERNOON_WINDOW,
                ParametrosInsumo.MAX_APPLICATION_TEMP, ParametrosInsumo.MIN_APPLICATION_MOISTURE,
                ParametrosInsumo.RAIN_BLOCKS_MM);
    }

    public PreconditionResult check(ApplicationFacts facts, ParametrosVigentes vigentes, RuleOverrides overrides) {
        DeclaredParameters params = DeclaredParameters.of(this, vigentes);
        List<Supplier<PreconditionResult>> checks = List.of(
                () -> diagnosis(facts, overrides),
                () -> effectiveness(facts),
                () -> fungicideCap(facts, params, overrides),
                () -> interval(facts, params, overrides),
                () -> oncePerDay(facts, overrides),
                () -> window(facts, params, overrides),
                () -> temperature(facts, params, overrides),
                () -> rain(facts, params, overrides),
                () -> moisture(facts, params, overrides));
        for (Supplier<PreconditionResult> check : checks) {
            PreconditionResult result = check.get();
            if (!result.isMet()) {
                return result;
            }
        }
        return PreconditionResult.met();
    }

    // --- P-D1 ---

    private PreconditionResult diagnosis(ApplicationFacts f, RuleOverrides overrides) {
        if (f.diagnosis() == null || f.diagnosis().valid() || overrides.isBypassed(NAME, BYPASS_DIAGNOSIS)) {
            return PreconditionResult.met();
        }
        return PreconditionResult.reject("P-D1: " + f.diagnosis().reason());
    }

    // --- S-06 ---

    /**
     * Un tratamiento del mismo tipo que quedó sin efectividad bloquea el siguiente en el sector hasta que alguien registre la
     * revisión. El bloqueo ya viene decidido en los hechos (también su bypass y su apagado, del control de demo): acá sólo se
     * rechaza. Se rechaza y no se difiere: esperar la próxima ventana no lo resuelve.
     */
    private PreconditionResult effectiveness(ApplicationFacts f) {
        return f.effectivenessBlock() == null ? PreconditionResult.met() : PreconditionResult.reject(f.effectivenessBlock());
    }

    // --- F-06 ---

    /** Un sector que ya recibió el tope de fitosanitarios en la ventana no recibe otro hasta que alguien registre la revisión. */
    private PreconditionResult fungicideCap(ApplicationFacts f, DeclaredParameters params, RuleOverrides overrides) {
        double cap = params.number(ParametrosInsumo.FUNGICIDE_CAP);
        if (f.supply() != Supply.FUNGICIDE || f.fungicidesInCapWindow() < cap || overrides.isBypassed(NAME, BYPASS_CAP)) {
            return PreconditionResult.met();
        }
        return PreconditionResult.rejectCap(String.format(
                "F-06: el sector ya recibió %d fitosanitarios dentro de la ventana del tope (máximo %.0f); hace falta registrar la revisión",
                f.fungicidesInCapWindow(), cap));
    }

    // --- P-D2 ---

    private PreconditionResult interval(ApplicationFacts f, DeclaredParameters params, RuleOverrides overrides) {
        if (f.lastSameSupply() == null) {
            return PreconditionResult.met();
        }
        Optional<Double> overridden = overrides.cooldownMinutes(NAME, f.supply().intervalCooldownKey());
        double requiredMinutes = overridden.orElseGet(
                () -> params.number(f.supply().intervalParameter()) * MINUTES_PER_DAY);
        double elapsedMinutes = Duration.between(f.lastSameSupply(), f.now()).toMillis() / 60_000.0;
        if (elapsedMinutes >= requiredMinutes) {
            return PreconditionResult.met();
        }
        return PreconditionResult.defer(String.format(
                "P-D2: el último %s fue hace %.1f d y el mínimo es %.1f d",
                f.supply().label().toLowerCase(), elapsedMinutes / MINUTES_PER_DAY, requiredMinutes / MINUTES_PER_DAY));
    }

    private PreconditionResult oncePerDay(ApplicationFacts f, RuleOverrides overrides) {
        if (f.lastAnySupply() == null || overrides.isBypassed(NAME, BYPASS_DAILY)) {
            return PreconditionResult.met();
        }
        LocalDate lastDay = f.lastAnySupply().atZone(ZonaHorariaVivero.ZONA).toLocalDate();
        LocalDate today = f.now().atZone(ZonaHorariaVivero.ZONA).toLocalDate();
        return lastDay.equals(today)
                ? PreconditionResult.defer("P-D2: el sector ya recibió una aplicación hoy")
                : PreconditionResult.met();
    }

    // --- P-D3 ---

    private PreconditionResult window(ApplicationFacts f, DeclaredParameters params, RuleOverrides overrides) {
        if (overrides.isBypassed(NAME, BYPASS_WINDOW)) {
            return PreconditionResult.met();
        }
        LocalTime local = f.now().atZone(ZonaHorariaVivero.ZONA).toLocalTime();
        VentanaHoraria morning = params.window(ParametrosInsumo.MORNING_WINDOW);
        VentanaHoraria afternoon = params.window(ParametrosInsumo.AFTERNOON_WINDOW);
        if (morning.contiene(local) || afternoon.contiene(local)) {
            return PreconditionResult.met();
        }
        return PreconditionResult.defer("P-D3: fuera de la ventana de aplicación (" + morning.canonico()
                + " y " + afternoon.canonico() + ")");
    }

    private PreconditionResult temperature(ApplicationFacts f, DeclaredParameters params, RuleOverrides overrides) {
        double max = params.number(ParametrosInsumo.MAX_APPLICATION_TEMP);
        if (f.airTemp() == null || f.airTemp() <= max || overrides.isBypassed(NAME, BYPASS_TEMPERATURE)) {
            return PreconditionResult.met();
        }
        return PreconditionResult.defer(String.format("P-D3: %.1f °C supera el máximo de %.0f °C", f.airTemp(), max));
    }

    // --- P-D4 ---

    private PreconditionResult rain(ApplicationFacts f, DeclaredParameters params, RuleOverrides overrides) {
        double mm = params.number(ParametrosInsumo.RAIN_BLOCKS_MM);
        boolean blocks = f.rain() != null && f.rain().mmTotal() != null && f.rain().mmTotal() >= mm;
        if (!blocks || overrides.isBypassed(NAME, BYPASS_RAIN)) {
            return PreconditionResult.met();
        }
        return PreconditionResult.defer(String.format("P-D4: se prevén %.1f mm de lluvia (límite %.0f mm)",
                f.rain().mmTotal(), mm));
    }

    // --- P-D5 ---

    private PreconditionResult moisture(ApplicationFacts f, DeclaredParameters params, RuleOverrides overrides) {
        if (overrides.isBypassed(NAME, BYPASS_MOISTURE)) {
            return PreconditionResult.met();
        }
        double min = params.number(ParametrosInsumo.MIN_APPLICATION_MOISTURE);
        if (f.soilMoisture() == null) {
            return PreconditionResult.defer("P-D5: no hay lectura de humedad de sustrato");
        }
        if (f.soilMoisture() >= min) {
            return PreconditionResult.met();
        }
        boolean wateredSinceReading = f.lastWatering() != null && f.moistureAt() != null
                && f.lastWatering().isAfter(f.moistureAt());
        if (wateredSinceReading) {
            return PreconditionResult.met();
        }
        return PreconditionResult.defer(String.format(
                "P-D5: humedad de sustrato %.0f %% bajo el mínimo de %.0f %% y el sector no se regó desde la lectura",
                f.soilMoisture(), min));
    }
}
