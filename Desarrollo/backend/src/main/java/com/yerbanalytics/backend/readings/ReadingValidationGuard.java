package com.yerbanalytics.backend.readings;

import com.yerbanalytics.backend.dto.MetricSpec;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.overrides.DemoControllable;
import com.yerbanalytics.backend.engine.overrides.RuleOverrides;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.engine.parametros.ConsumidorParametros;
import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import com.yerbanalytics.backend.engine.parametros.ParametrosSeguridad;
import com.yerbanalytics.backend.engine.parametros.ParametrosVigentes;
import com.yerbanalytics.backend.engine.riego.CicloLectura;
import com.yerbanalytics.backend.model.ConfiguracionOperativaEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.mqtt.ComandoZonaPublisher;
import com.yerbanalytics.backend.sensing.SensingCadence;
import com.yerbanalytics.backend.service.ConfiguracionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * S-03, el guard de la ingesta (reglas_v2 §4). Antes de escribir una lectura en la zona valida cada métrica contra
 * su rango físico ({@link ReadingValidator}) y decide qué hacer con las que no lo cumplen:
 *
 * <ol>
 *   <li><b>Relectura:</b> pide {@code LEER_AHORA} enseguida, hasta {@code seguridad.relecturas-invalidas} veces
 *       (una sola vez por lectura, aunque haya varias métricas inválidas). La métrica no entra a la zona.</li>
 *   <li><b>Corrige:</b> si una relectura llega válida, esa se usa y la cuenta de esa métrica se reinicia.</li>
 *   <li><b>Descarte:</b> si la lectura y todas sus relecturas son inválidas, <i>esa</i> métrica se descarta en el
 *       ciclo y {@link InvalidReadingEscalation} deja la alerta. Las demás siguen su camino.</li>
 * </ol>
 *
 * <p>Las relecturas se cuentan por (zona, métrica) <b>dentro del ciclo de lectura</b>: un pedido que nunca fue
 * contestado no deja al ciclo siguiente con menos relecturas. El contador vive en memoria; si hay un reinicio
 * en medio se vuelve a empezar, que sólo cuesta una relectura de más.
 *
 * <p>Es controlable desde el simulador: apagado acepta cualquier valor, y el bypass {@code retries} no pide
 * relecturas (el primer valor inválido ya se descarta).
 */
@Service
public class ReadingValidationGuard implements ConsumidorParametros, DemoControllable {

    private static final Logger log = LoggerFactory.getLogger(ReadingValidationGuard.class);

    /** Nombre técnico: el de su consumo de parámetros, el de sus overrides y el de sus alertas en el historial. */
    public static final String NAME = "ReadingValidationGuard";

    /** Bypass: no se piden relecturas; el primer valor inválido ya se descarta. */
    public static final String BYPASS_RETRIES = "retries";

    /** No está en la cadena de reglas por sector: sólo ordena el listado del simulador (después del escalamiento). */
    private static final int PRIORITY = 91;

    /** Relecturas ya pedidas de una métrica en un ciclo. */
    private record Rereads(long cycleMs, int count) {
    }

    private final InvalidReadingEscalation escalation;
    private final ComandoZonaPublisher zonePublisher;
    private final CatalogoParametrosService parametros;
    private final ConfiguracionService configuracion;
    private final RuleOverrides overrides;
    private final Clock reloj;

    /** Guardado por {@code this}: la ingesta de dos zonas puede llegar a la vez. */
    private final Map<String, Rereads> rereads = new HashMap<>();

    public ReadingValidationGuard(InvalidReadingEscalation escalation,
                                  ComandoZonaPublisher zonePublisher,
                                  // Perezoso: el catálogo se arma con la lista de consumidores, y esta clase es uno.
                                  @Lazy CatalogoParametrosService parametros,
                                  ConfiguracionService configuracion,
                                  RuleOverrides overrides,
                                  Clock relojVivero) {
        this.escalation = escalation;
        this.zonePublisher = zonePublisher;
        this.parametros = parametros;
        this.configuracion = configuracion;
        this.overrides = overrides;
        this.reloj = relojVivero;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String label() {
        return "🧪 Lectura inválida (S-03)";
    }

    @Override
    public RuleBranch branch() {
        return RuleBranch.GLOBAL;
    }

    @Override
    public int priority() {
        return PRIORITY;
    }

    @Override
    public Set<String> bypassKeys() {
        return Set.of(BYPASS_RETRIES);
    }

    @Override
    public List<DefinicionParametro> parametros() {
        return List.of(ParametrosSeguridad.RANGO_HUM_MIN, ParametrosSeguridad.RANGO_HUM_MAX,
                ParametrosSeguridad.RANGO_TEMP_MIN, ParametrosSeguridad.RANGO_TEMP_MAX,
                ParametrosSeguridad.RANGO_PH_MIN, ParametrosSeguridad.RANGO_PH_MAX,
                ParametrosSeguridad.RANGO_CE_MIN, ParametrosSeguridad.RANGO_CE_MAX,
                ParametrosSeguridad.RANGO_LUZ_MIN, ParametrosSeguridad.RANGO_LUZ_MAX,
                ParametrosSeguridad.RELECTURAS_INVALIDAS);
    }

    /**
     * Valida la lectura de {@code zona}. Pide la relectura si corresponde y deja las alertas; quien llama sólo
     * escribe en la zona lo que vuelve en {@link ReadingCheck#accepted()}.
     */
    public ReadingCheck check(ZonaEntity zona, ReadingMetrics reading) {
        if (!overrides.isEnabled(NAME)) {
            return ReadingCheck.allAccepted(reading);
        }
        ParametrosVigentes vigentes = parametros.vigentes();
        Set<String> invalid = ReadingValidator.invalidMetrics(reading, vigentes);
        String zoneId = zona.getId();

        List<String> valid = reading.keys().stream().filter(k -> !invalid.contains(k)).toList();
        forgetRereads(zoneId, valid);
        escalation.registerValid(zoneId, valid);
        if (invalid.isEmpty()) {
            return ReadingCheck.allAccepted(reading);
        }

        Instant now = reloj.instant();
        int intervalMinutes = intervalMinutes();
        Instant cycle = CicloLectura.inicio(now, intervalMinutes);
        int allowed = overrides.isBypassed(NAME, BYPASS_RETRIES)
                ? 0 : (int) vigentes.numero(ParametrosSeguridad.RELECTURAS_INVALIDAS);

        Set<String> pending = new LinkedHashSet<>();
        Set<String> discarded = new LinkedHashSet<>();
        for (String key : invalid) {
            (takeReread(zoneId, key, cycle, allowed) ? pending : discarded).add(key);
        }
        if (!discarded.isEmpty()) {
            Instant previousCycle = CicloLectura.inicioDelCicloAnterior(now, intervalMinutes);
            Map<String, String> labels = labels();
            for (String key : discarded) {
                escalation.registerDiscard(zona, key, labels.getOrDefault(key, key), cycle, previousCycle);
            }
        }
        if (!pending.isEmpty()) {
            requestReread(zoneId);
        }
        return new ReadingCheck(reading.without(invalid), pending, discarded);
    }

    /** Cuenta una relectura de la métrica en el ciclo si todavía hay cupo; si no, olvida la cuenta (se descarta). */
    private synchronized boolean takeReread(String zoneId, String metric, Instant cycle, int allowed) {
        String key = zoneId + ":" + metric;
        Rereads current = rereads.get(key);
        int done = current != null && current.cycleMs() == cycle.toEpochMilli() ? current.count() : 0;
        if (done < allowed) {
            rereads.put(key, new Rereads(cycle.toEpochMilli(), done + 1));
            return true;
        }
        rereads.remove(key);
        return false;
    }

    private synchronized void forgetRereads(String zoneId, List<String> metrics) {
        metrics.forEach(m -> rereads.remove(zoneId + ":" + m));
    }

    /** Un {@code LEER_AHORA} a la zona. Una falla al publicar no frena la ingesta: la lectura inválida igual queda afuera. */
    private void requestReread(String zoneId) {
        try {
            ComandoZonaPublisher.Resultado r = zonePublisher.leerAhora(zoneId);
            if (!r.publicado()) {
                log.warn("Zona {}: no se pudo pedir la relectura por un valor inválido ({}).", zoneId, r.error());
            }
        } catch (RuntimeException e) {
            log.warn("Zona {}: falló el pedido de relectura por un valor inválido. {}", zoneId, e.getMessage());
        }
    }

    private int intervalMinutes() {
        ConfiguracionOperativaEntity op = configuracion.getConfiguracionOperativa();
        return SensingCadence.intervalMinutes(op != null ? op.getIntervaloSensadoMinutos() : null);
    }

    /** Etiqueta legible de cada métrica (la de su umbral configurado), para el texto de la alerta. */
    private Map<String, String> labels() {
        Map<String, String> out = new HashMap<>();
        for (MetricSpec spec : configuracion.getEffectiveSpecs()) {
            out.put(spec.key(), spec.label());
        }
        return out;
    }
}
