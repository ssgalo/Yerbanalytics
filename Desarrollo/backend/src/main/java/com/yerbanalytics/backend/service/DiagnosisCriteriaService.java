package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.dto.DiagnosisCriteria;
import com.yerbanalytics.backend.dto.SeverityBand;
import com.yerbanalytics.backend.exception.InvalidDiagnosisCriteriaException;
import com.yerbanalytics.backend.model.DiagnosisCriteriaEntity;
import com.yerbanalytics.backend.repository.DiagnosisCriteriaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Criterio agronómico del diagnóstico (design D5): bandas de severidad por estado anómalo y orden
 * de criticidad.
 *
 * <p>El backend sólo lo guarda, lo valida y lo sirve. Lo aplica el módulo de diagnóstico del
 * servicio de inferencia, que lo lee en cada ciclo con su cuenta de rol Servicio; por eso la
 * lectura pide {@code diagnosticos.ver} y no {@code configuracion.ver}. No vive en el catálogo de
 * parámetros del motor: describe el diagnóstico, no una acción, y el motor no se toca.
 */
@Service
public class DiagnosisCriteriaService {

    private static final Logger log = LoggerFactory.getLogger(DiagnosisCriteriaService.class);

    /** Estados anómalos que emite el modelo, en el orden fijo en que salen las bandas. */
    public static final List<String> STATES = List.of("Clorosis", "Estrés solar", "Plaga foliar", "Daño fúngico");

    /** Valores por defecto: {@code mínimo, media desde, alta desde}. */
    static final Map<String, int[]> DEFAULT_BANDS = Map.of(
            "Clorosis", new int[]{5, 10, 20},
            "Estrés solar", new int[]{5, 10, 20},
            "Plaga foliar", new int[]{2, 5, 10},
            "Daño fúngico", new int[]{2, 4, 8});

    /** Criticidad por defecto, del más crítico al menos crítico. */
    static final List<String> DEFAULT_CRITICALITY = List.of("Daño fúngico", "Plaga foliar", "Clorosis", "Estrés solar");

    private final DiagnosisCriteriaRepository repo;
    private final HistorialService historialService;

    public DiagnosisCriteriaService(DiagnosisCriteriaRepository repo, HistorialService historialService) {
        this.repo = repo;
        this.historialService = historialService;
    }

    /** Al arrancar, siembra el criterio por defecto si no hay ninguno guardado. Nunca pisa uno editado. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedOnStartup() {
        if (seedIfEmpty()) {
            log.info("Criterio de diagnóstico sembrado con los valores por defecto");
        }
    }

    /** Siembra los valores por defecto si la tabla está vacía. Devuelve si sembró. */
    @Transactional
    public boolean seedIfEmpty() {
        if (repo.count() > 0) {
            return false;
        }
        repo.saveAll(toEntities(defaults()));
        return true;
    }

    /** El criterio vigente; si todavía no se sembró, el de fábrica. */
    @Transactional(readOnly = true)
    public DiagnosisCriteria get() {
        List<DiagnosisCriteriaEntity> rows = repo.findAll();
        if (rows.isEmpty()) {
            return defaults();
        }
        Map<String, DiagnosisCriteriaEntity> byState = new HashMap<>();
        rows.forEach(r -> byState.put(r.getState(), r));

        List<SeverityBand> bands = new ArrayList<>();
        for (String state : STATES) {
            DiagnosisCriteriaEntity r = byState.get(state);
            if (r != null) {
                bands.add(new SeverityBand(state, r.getMinCount(), r.getMediumFrom(), r.getHighFrom()));
            } else {
                // Una fila faltante (base tocada a mano) no debe romper la lectura del servicio.
                int[] d = DEFAULT_BANDS.get(state);
                bands.add(new SeverityBand(state, d[0], d[1], d[2]));
            }
        }
        List<String> criticality = rows.stream()
                .filter(r -> STATES.contains(r.getState()))
                .sorted(Comparator.comparing(DiagnosisCriteriaEntity::getCriticalityRank))
                .map(DiagnosisCriteriaEntity::getState)
                .collect(Collectors.toCollection(ArrayList::new));
        if (criticality.size() != STATES.size()) {
            criticality = new ArrayList<>(DEFAULT_CRITICALITY);
        }
        return new DiagnosisCriteria(bands, criticality);
    }

    /**
     * Reemplaza el criterio completo y registra la recalibración en el historial. Si algo no es
     * válido no se guarda nada ({@link InvalidDiagnosisCriteriaException}, {@code 400}).
     */
    @Transactional
    public DiagnosisCriteria replace(DiagnosisCriteria criteria, String usuario) {
        DiagnosisCriteria normalized = validate(criteria);
        // Se actualizan las filas en su lugar (y se borra cualquiera ajena a los cuatro estados) en
        // vez de borrar e insertar: así no choca con entidades ya cargadas en la misma transacción.
        Map<String, DiagnosisCriteriaEntity> current = new HashMap<>();
        for (DiagnosisCriteriaEntity r : repo.findAll()) {
            if (STATES.contains(r.getState())) {
                current.put(r.getState(), r);
            } else {
                repo.delete(r);
            }
        }
        for (DiagnosisCriteriaEntity incoming : toEntities(normalized)) {
            DiagnosisCriteriaEntity row = current.getOrDefault(incoming.getState(), incoming);
            row.setMinCount(incoming.getMinCount());
            row.setMediumFrom(incoming.getMediumFrom());
            row.setHighFrom(incoming.getHighFrom());
            row.setCriticalityRank(incoming.getCriticalityRank());
            repo.save(row);
        }
        historialService.registrarCriterioDiagnostico(
                usuario != null && !usuario.isBlank() ? usuario : "un usuario", describe(normalized));
        return get();
    }

    // ------------------------------------------------------------------
    // Validación
    // ------------------------------------------------------------------

    /** Valida y devuelve el criterio con las bandas en el orden fijo y los números como enteros. */
    static DiagnosisCriteria validate(DiagnosisCriteria c) {
        if (c == null || c.severityBands() == null) {
            throw new InvalidDiagnosisCriteriaException("Faltan las bandas de severidad.");
        }
        Map<String, SeverityBand> byState = new LinkedHashMap<>();
        for (SeverityBand b : c.severityBands()) {
            if (b == null || b.state() == null || !STATES.contains(b.state().trim())) {
                throw new InvalidDiagnosisCriteriaException("Las bandas de severidad sólo admiten los estados "
                        + String.join(", ", STATES) + "; llegó '" + (b != null ? b.state() : null) + "'.");
            }
            String state = b.state().trim();
            if (byState.put(state, b) != null) {
                throw new InvalidDiagnosisCriteriaException("El estado " + state + " está repetido en las bandas de severidad.");
            }
        }
        List<String> missing = STATES.stream().filter(s -> !byState.containsKey(s)).toList();
        if (!missing.isEmpty()) {
            throw new InvalidDiagnosisCriteriaException("Faltan las bandas de severidad de: "
                    + String.join(", ", missing) + ".");
        }

        List<SeverityBand> bands = new ArrayList<>();
        for (String state : STATES) {
            SeverityBand b = byState.get(state);
            int minimum = integer(state, "el mínimo", b.minimum());
            int mediumFrom = integer(state, "\"media desde\"", b.mediumFrom());
            int highFrom = integer(state, "\"alta desde\"", b.highFrom());
            if (minimum < 1) {
                throw new InvalidDiagnosisCriteriaException(state + ": el mínimo debe ser al menos 1 hoja.");
            }
            if (minimum >= mediumFrom) {
                throw new InvalidDiagnosisCriteriaException(state + ": el mínimo (" + minimum
                        + ") debe ser menor que \"media desde\" (" + mediumFrom + ").");
            }
            if (mediumFrom >= highFrom) {
                throw new InvalidDiagnosisCriteriaException(state + ": \"media desde\" (" + mediumFrom
                        + ") debe ser menor que \"alta desde\" (" + highFrom + ").");
            }
            bands.add(new SeverityBand(state, minimum, mediumFrom, highFrom));
        }

        List<String> criticality = c.criticality();
        String permutationError = "El orden de criticidad debe incluir cada uno de los estados "
                + String.join(", ", STATES) + " exactamente una vez.";
        if (criticality == null || criticality.size() != STATES.size()) {
            throw new InvalidDiagnosisCriteriaException(permutationError);
        }
        List<String> trimmed = criticality.stream().map(s -> s != null ? s.trim() : null).toList();
        Set<String> seen = new HashSet<>();
        for (String s : trimmed) {
            if (s == null || !STATES.contains(s) || !seen.add(s)) {
                throw new InvalidDiagnosisCriteriaException(permutationError);
            }
        }
        return new DiagnosisCriteria(bands, new ArrayList<>(trimmed));
    }

    private static int integer(String state, String field, Number n) {
        if (n == null) {
            throw new InvalidDiagnosisCriteriaException(state + ": falta " + field + ".");
        }
        double v = n.doubleValue();
        if (Double.isNaN(v) || Double.isInfinite(v) || v != Math.rint(v)
                || v > Integer.MAX_VALUE || v < Integer.MIN_VALUE) {
            throw new InvalidDiagnosisCriteriaException(state + ": " + field
                    + " debe ser una cantidad entera de hojas.");
        }
        return (int) v;
    }

    // ------------------------------------------------------------------

    static DiagnosisCriteria defaults() {
        List<SeverityBand> bands = STATES.stream()
                .map(s -> {
                    int[] d = DEFAULT_BANDS.get(s);
                    return new SeverityBand(s, d[0], d[1], d[2]);
                })
                .toList();
        return new DiagnosisCriteria(bands, DEFAULT_CRITICALITY);
    }

    private static List<DiagnosisCriteriaEntity> toEntities(DiagnosisCriteria c) {
        List<DiagnosisCriteriaEntity> out = new ArrayList<>();
        for (SeverityBand b : c.severityBands()) {
            out.add(new DiagnosisCriteriaEntity(b.state(), b.minimum().intValue(), b.mediumFrom().intValue(),
                    b.highFrom().intValue(), c.criticality().indexOf(b.state()) + 1));
        }
        return out;
    }

    /** Texto del historial: qué quedó vigente. */
    private static String describe(DiagnosisCriteria c) {
        String bands = c.severityBands().stream()
                .map(b -> b.state() + " " + b.minimum() + "/" + b.mediumFrom() + "/" + b.highFrom())
                .collect(Collectors.joining(", "));
        return "Criterio de diagnóstico actualizado. Bandas de severidad (mínimo/media/alta, en hojas): "
                + bands + ". Criticidad: " + String.join(" > ", c.criticality()) + ".";
    }
}
