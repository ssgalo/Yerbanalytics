package com.yerbanalytics.backend.supply;

import com.yerbanalytics.backend.engine.overrides.RuleOverrides;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametros;
import com.yerbanalytics.backend.engine.parametros.ParametrosVigentes;
import com.yerbanalytics.backend.engine.weather.WeatherForecast;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Precondiciones de aplicación (P-D1…P-D5)")
class ApplicationPreconditionsTest {

    private final ApplicationPreconditions preconditions = new ApplicationPreconditions();
    private final ParametrosVigentes factory = new CatalogoParametros(List.of()).fabricas();

    /** Hora local del vivero (UTC-3) de un día fijo, como instante. */
    private static Instant at(String localTime) {
        return Instant.parse("2026-10-22T" + localTime + ":00-03:00");
    }

    /** Hechos que cumplen todo: 08:00, 22 °C, sustrato 55 %, nada reciente. */
    private static ApplicationFacts ok(Supply supply, String localTime) {
        return new ApplicationFacts(supply, at(localTime), null, null, null, 22.0, 55.0, at("07:30"), null, null);
    }

    private PreconditionResult check(ApplicationFacts f) {
        return preconditions.check(f, factory, RuleOverrides.NONE);
    }

    private PreconditionResult check(ApplicationFacts f, RuleOverrides overrides) {
        return preconditions.check(f, factory, overrides);
    }

    private static ApplicationFacts withLastSame(ApplicationFacts f, Instant last) {
        return new ApplicationFacts(f.supply(), f.now(), f.diagnosis(), last, last, f.airTemp(), f.soilMoisture(),
                f.moistureAt(), f.lastWatering(), f.rain());
    }

    // --- P-D3: ventana (bordes) ---

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({"06:59,DEFER", "07:00,MET", "09:59,MET", "10:00,DEFER", "12:00,DEFER",
            "16:59,DEFER", "17:00,MET", "18:59,MET", "19:00,DEFER"})
    void windowEdges(String localTime, PreconditionResult.Verdict expected) {
        assertThat(check(ok(Supply.FERTILIZER, localTime)).verdict()).isEqualTo(expected);
    }

    @Test
    void outsideTheWindowSaysWhichOnes() {
        assertThat(check(ok(Supply.FERTILIZER, "12:00")).reason()).contains("07:00-10:00").contains("17:00-19:00");
    }

    // --- P-D3: temperatura ---

    @Test
    void exactlyTheMaximumTemperatureIsAllowed() {
        ApplicationFacts f = ok(Supply.FERTILIZER, "08:30");
        assertThat(check(withTemp(f, 30.0)).isMet()).isTrue();
    }

    @Test
    void aboveTheMaximumTemperatureDefers() {
        PreconditionResult r = check(withTemp(ok(Supply.FERTILIZER, "08:30"), 31.0));
        assertThat(r.verdict()).isEqualTo(PreconditionResult.Verdict.DEFER);
        assertThat(r.reason()).contains("31");
    }

    @Test
    void withoutAnyTemperatureNothingBlocks() {
        assertThat(check(withTemp(ok(Supply.FERTILIZER, "08:30"), null)).isMet()).isTrue();
    }

    private static ApplicationFacts withTemp(ApplicationFacts f, Double temp) {
        return new ApplicationFacts(f.supply(), f.now(), f.diagnosis(), f.lastSameSupply(), f.lastAnySupply(), temp,
                f.soilMoisture(), f.moistureAt(), f.lastWatering(), f.rain());
    }

    // --- P-D4: lluvia ---

    private static ApplicationFacts withRain(ApplicationFacts f, WeatherForecast.LluviaPrevista rain) {
        return new ApplicationFacts(f.supply(), f.now(), f.diagnosis(), f.lastSameSupply(), f.lastAnySupply(),
                f.airTemp(), f.soilMoisture(), f.moistureAt(), f.lastWatering(), rain);
    }

    @Test
    void fiveMillimetersOfRainDefers() {
        PreconditionResult r = check(withRain(ok(Supply.FERTILIZER, "08:00"),
                new WeatherForecast.LluviaPrevista(90.0, 5.0, 6)));
        assertThat(r.verdict()).isEqualTo(PreconditionResult.Verdict.DEFER);
        assertThat(r.reason()).contains("P-D4");
    }

    @Test
    void lessThanFiveMillimetersDoesNotBlock() {
        assertThat(check(withRain(ok(Supply.FERTILIZER, "08:00"),
                new WeatherForecast.LluviaPrevista(90.0, 4.9, 6))).isMet()).isTrue();
    }

    @Test
    void withoutForecastRainDoesNotBlock() {
        assertThat(check(withRain(ok(Supply.FERTILIZER, "08:00"), null)).isMet()).isTrue();
    }

    @Test
    void aForecastWithoutMillimetersDoesNotBlock() {
        assertThat(check(withRain(ok(Supply.FERTILIZER, "08:00"),
                new WeatherForecast.LluviaPrevista(90.0, null, 0))).isMet()).isTrue();
    }

    // --- P-D5: humedad ---

    private static ApplicationFacts withMoisture(ApplicationFacts f, Double moisture, Instant watering) {
        return new ApplicationFacts(f.supply(), f.now(), f.diagnosis(), f.lastSameSupply(), f.lastAnySupply(),
                f.airTemp(), moisture, f.moistureAt(), watering, f.rain());
    }

    @Test
    void exactlyTheMinimumMoistureIsAllowed() {
        assertThat(check(withMoisture(ok(Supply.FERTILIZER, "08:00"), 45.0, null)).isMet()).isTrue();
    }

    @Test
    void dryDefersWhenNotWateredSinceTheReading() {
        PreconditionResult r = check(withMoisture(ok(Supply.FERTILIZER, "08:00"), 40.0, null));
        assertThat(r.verdict()).isEqualTo(PreconditionResult.Verdict.DEFER);
        assertThat(r.reason()).contains("P-D5");
    }

    @Test
    void dryButWateredAfterTheReadingIsAllowed() {
        // La lectura es de las 07:30; el sector se regó a las 07:40.
        assertThat(check(withMoisture(ok(Supply.FERTILIZER, "08:00"), 40.0, at("07:40"))).isMet()).isTrue();
    }

    @Test
    void dryAndWateredBeforeTheReadingDefers() {
        assertThat(check(withMoisture(ok(Supply.FERTILIZER, "08:00"), 40.0, at("07:20"))).isMet()).isFalse();
    }

    @Test
    void noMoistureReadingDefers() {
        assertThat(check(withMoisture(ok(Supply.FERTILIZER, "08:00"), null, null)).isMet()).isFalse();
    }

    @Test
    void thereIsNoUpperMoistureLimit() {
        assertThat(check(withMoisture(ok(Supply.FERTILIZER, "08:00"), 100.0, null)).isMet()).isTrue();
    }

    // --- P-D2: intervalo y una por día ---

    @Test
    void fungicideFiveDaysAgoDefersBecauseTheMinimumIsSeven() {
        ApplicationFacts f = withLastSame(ok(Supply.FUNGICIDE, "08:00"), at("08:00").minusSeconds(5 * 86_400L));
        PreconditionResult r = check(f);
        assertThat(r.verdict()).isEqualTo(PreconditionResult.Verdict.DEFER);
        assertThat(r.reason()).contains("P-D2").contains("fitosanitario");
    }

    @Test
    void fungicideSevenDaysAgoIsAllowed() {
        assertThat(check(withLastSame(ok(Supply.FUNGICIDE, "08:00"), at("08:00").minusSeconds(7 * 86_400L))).isMet())
                .isTrue();
    }

    @Test
    void fertilizerFiveDaysAgoIsAllowedButFourIsNot() {
        assertThat(check(withLastSame(ok(Supply.FERTILIZER, "08:00"), at("08:00").minusSeconds(5 * 86_400L))).isMet())
                .isTrue();
        assertThat(check(withLastSame(ok(Supply.FERTILIZER, "08:00"), at("08:00").minusSeconds(4 * 86_400L))).isMet())
                .isFalse();
    }

    @Test
    void anyApplicationTodayBlocksAnotherSupply() {
        ApplicationFacts f = ok(Supply.FERTILIZER, "17:30");
        ApplicationFacts afterFungicideThisMorning = new ApplicationFacts(f.supply(), f.now(), null, null,
                at("08:00"), f.airTemp(), f.soilMoisture(), f.moistureAt(), null, null);
        PreconditionResult r = check(afterFungicideThisMorning);
        assertThat(r.verdict()).isEqualTo(PreconditionResult.Verdict.DEFER);
        assertThat(r.reason()).contains("hoy");
    }

    @Test
    void anApplicationYesterdayDoesNotCountAsToday() {
        ApplicationFacts f = ok(Supply.FERTILIZER, "08:00");
        ApplicationFacts yesterday = new ApplicationFacts(f.supply(), f.now(), null, null,
                at("18:00").minusSeconds(86_400L), f.airTemp(), f.soilMoisture(), f.moistureAt(), null, null);
        assertThat(check(yesterday).isMet()).isTrue();
    }

    // --- P-D1 ---

    @Test
    void anInvalidDiagnosisRejectsInsteadOfDeferring() {
        ApplicationFacts f = ok(Supply.FERTILIZER, "08:00");
        ApplicationFacts withDiagnosis = new ApplicationFacts(f.supply(), f.now(),
                new ValidDiagnosis.Verdict(false, "el diagnóstico tiene 60 h"), null, null, f.airTemp(),
                f.soilMoisture(), f.moistureAt(), null, null);
        PreconditionResult r = check(withDiagnosis);
        assertThat(r.verdict()).isEqualTo(PreconditionResult.Verdict.REJECT);
        assertThat(r.reason()).contains("P-D1").contains("60 h");
    }

    @Test
    void aValidDiagnosisPasses() {
        ApplicationFacts f = ok(Supply.FERTILIZER, "08:00");
        ApplicationFacts withDiagnosis = new ApplicationFacts(f.supply(), f.now(),
                new ValidDiagnosis.Verdict(true, null), null, null, f.airTemp(), f.soilMoisture(), f.moistureAt(),
                null, null);
        assertThat(check(withDiagnosis).isMet()).isTrue();
    }

    // --- Overrides del simulador ---

    private static RuleOverrides bypassing(String... keys) {
        Set<String> bypassed = Set.of(keys);
        return new RuleOverrides() {
            @Override
            public boolean isEnabled(String ruleName) {
                return true;
            }

            @Override
            public Optional<Double> cooldownMinutes(String ruleName, String key) {
                return Optional.empty();
            }

            @Override
            public boolean isBypassed(String ruleName, String key) {
                return ApplicationPreconditions.NAME.equals(ruleName) && bypassed.contains(key);
            }
        };
    }

    private static RuleOverrides withCooldown(String cooldownKey, double minutes) {
        return new RuleOverrides() {
            @Override
            public boolean isEnabled(String ruleName) {
                return true;
            }

            @Override
            public Optional<Double> cooldownMinutes(String ruleName, String key) {
                return cooldownKey.equals(key) ? Optional.of(minutes) : Optional.empty();
            }

            @Override
            public boolean isBypassed(String ruleName, String key) {
                return false;
            }
        };
    }

    @Test
    void bypassWindowIgnoresTheHour() {
        assertThat(check(ok(Supply.FERTILIZER, "12:00"), bypassing(ApplicationPreconditions.BYPASS_WINDOW)).isMet())
                .isTrue();
    }

    @Test
    void bypassTemperatureIgnoresTheHeat() {
        assertThat(check(withTemp(ok(Supply.FERTILIZER, "08:00"), 38.0),
                bypassing(ApplicationPreconditions.BYPASS_TEMPERATURE)).isMet()).isTrue();
    }

    @Test
    void bypassRainIgnoresTheForecast() {
        assertThat(check(withRain(ok(Supply.FERTILIZER, "08:00"), new WeatherForecast.LluviaPrevista(99.0, 20.0, 6)),
                bypassing(ApplicationPreconditions.BYPASS_RAIN)).isMet()).isTrue();
    }

    @Test
    void bypassMoistureIgnoresADrySubstrate() {
        assertThat(check(withMoisture(ok(Supply.FERTILIZER, "08:00"), 10.0, null),
                bypassing(ApplicationPreconditions.BYPASS_MOISTURE)).isMet()).isTrue();
    }

    @Test
    void bypassDailyIgnoresAnApplicationToday() {
        ApplicationFacts f = ok(Supply.FERTILIZER, "08:00");
        ApplicationFacts today = new ApplicationFacts(f.supply(), f.now(), null, null, at("07:10"), f.airTemp(),
                f.soilMoisture(), f.moistureAt(), null, null);
        assertThat(check(today, bypassing(ApplicationPreconditions.BYPASS_DAILY)).isMet()).isTrue();
    }

    @Test
    void bypassDiagnosisIgnoresAnInvalidOne() {
        ApplicationFacts f = ok(Supply.FERTILIZER, "08:00");
        ApplicationFacts invalid = new ApplicationFacts(f.supply(), f.now(), new ValidDiagnosis.Verdict(false, "vieja"),
                null, null, f.airTemp(), f.soilMoisture(), f.moistureAt(), null, null);
        assertThat(check(invalid, bypassing(ApplicationPreconditions.BYPASS_DIAGNOSIS)).isMet()).isTrue();
    }

    @Test
    void aZeroFungicideIntervalRemovesTheWait() {
        ApplicationFacts recent = withLastSame(ok(Supply.FUNGICIDE, "08:00"), at("08:00").minusSeconds(3600));
        // La de hoy ya cuenta como "una por día": se saltea aparte, para aislar el intervalo.
        RuleOverrides ov = new RuleOverrides() {
            @Override
            public boolean isEnabled(String ruleName) {
                return true;
            }

            @Override
            public Optional<Double> cooldownMinutes(String ruleName, String key) {
                return Supply.FUNGICIDE.intervalCooldownKey().equals(key) ? Optional.of(0.0) : Optional.empty();
            }

            @Override
            public boolean isBypassed(String ruleName, String key) {
                return ApplicationPreconditions.BYPASS_DAILY.equals(key);
            }
        };
        assertThat(check(recent, ov).isMet()).isTrue();
    }

    @Test
    void theIntervalCooldownOnlyAffectsItsOwnSupply() {
        ApplicationFacts recentFertilizer = withLastSame(ok(Supply.FERTILIZER, "08:00"), at("08:00").minusSeconds(3600));
        RuleOverrides ov = withCooldown(Supply.FUNGICIDE.intervalCooldownKey(), 0);
        assertThat(check(recentFertilizer, ov).isMet()).isFalse();
    }

    @Test
    void declaresEveryBypassAndCooldownTheSimulatorNeeds() {
        assertThat(preconditions.bypassKeys()).containsExactlyInAnyOrder("diagnosis", "daily", "window", "temperature",
                "rain", "moisture", "stock");
        assertThat(preconditions.cooldownKeys()).containsExactlyInAnyOrder("interval-fertilizer", "interval-fungicide");
    }
}
