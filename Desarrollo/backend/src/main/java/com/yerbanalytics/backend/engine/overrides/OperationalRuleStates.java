package com.yerbanalytics.backend.engine.overrides;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Estado operativo de las piezas del motor: lo que el vivero decidió prender o apagar, persistido en
 * {@code regla_estado} y vigente también en modo {@code REAL}.
 *
 * <ul>
 *   <li><b>Sin fila rige la siembra</b>: una pieza está apagada si figura en {@code yerbanalytics.reglas.retiradas}
 *       (piezas que siguen en el código pero no deben actuar porque lo que las reemplaza todavía no está) y prendida si
 *       no. No se insertan filas al arrancar: la fila aparece cuando alguien cambia el interruptor, así "de fábrica" y
 *       "decidido por una persona" se distinguen. Una retirada prendida a mano sigue prendida aunque cambie la lista.</li>
 *   <li><b>Cache en memoria</b>: el motor pregunta por pieza y por sector en cada pulso, no puede ir a la base cada vez.
 *       Se carga la primera vez que se consulta y cada cambio la actualiza <i>después del commit</i> (si la transacción
 *       se revierte, no cambia).</li>
 *   <li><b>Filas huérfanas</b> (una pieza que ya no existe): se ignoran y se avisa al arrancar; no se borran, por si la
 *       pieza vuelve.</li>
 * </ul>
 *
 * <p>El estado <i>efectivo</i> que consulta el motor es {@link EffectiveRuleOverrides}, y coincide con éste: la demo no
 * tiene interruptor de encendido, sólo cooldowns y bypass.
 */
@Component
public class OperationalRuleStates {

    private static final Logger log = LoggerFactory.getLogger(OperationalRuleStates.class);

    private final RuleStateRepository repository;
    private final Set<String> retired;
    private volatile Map<String, RuleState> cache;

    public OperationalRuleStates(RuleStateRepository repository,
                                 @Value("${yerbanalytics.reglas.retiradas:SupplyRule,DailyDoseLimitRule}")
                                 List<String> retired) {
        this.repository = repository;
        this.retired = retired.stream().map(String::trim).filter(n -> !n.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * {@code true} si la pieza corre según el vivero: nunca si está eliminada; si no, el interruptor decidido por una
     * persona o, sin decisión, la siembra.
     */
    public boolean isActive(String name) {
        RuleState row = states().get(name);
        if (row != null && row.deleted()) {
            return false;
        }
        return row != null && row.switchDecided() ? row.active() : activeByDefault(name);
    }

    /** El estado que tiene sin que nadie lo haya decidido: apagada si es una retirada. */
    public boolean activeByDefault(String name) {
        return !retired.contains(name);
    }

    /** El último cambio del interruptor de la pieza; vacío si nunca se decidió (aunque tenga nombre propio). */
    public Optional<RuleState> state(String name) {
        return Optional.ofNullable(states().get(name)).filter(RuleState::switchDecided);
    }

    /** La fila de la pieza, con o sin decisión del interruptor (nombre propio, eliminada); vacío si no tiene. */
    public Optional<RuleState> row(String name) {
        return Optional.ofNullable(states().get(name));
    }

    /** Nombres de las piezas apagadas en el vivero: las filas apagadas o eliminadas y las retiradas sin decisión. */
    public Set<String> inactiveNames() {
        Map<String, RuleState> rows = states();
        Set<String> out = new HashSet<>();
        rows.keySet().stream().filter(n -> !isActive(n)).forEach(out::add);
        retired.stream().filter(n -> !rows.containsKey(n)).forEach(out::add);
        return out;
    }

    /**
     * Guarda la decisión del interruptor (conserva el nombre propio y la marca de eliminada) y, cuando la transacción
     * confirma, actualiza el cache. Sin transacción (tests) lo hace en el acto.
     */
    @Transactional
    public RuleState set(String name, boolean active, String reason, String user) {
        // El cache se carga ANTES de escribir: una lectura después podría ver la fila sin commit.
        RuleState previous = states().get(name);
        return write(new RuleStateEntity(name, active, reason, user, System.currentTimeMillis(),
                previous != null ? previous.label() : null, previous != null && previous.deleted() ? Boolean.TRUE : null));
    }

    /**
     * Guarda el nombre propio ({@code null} = el de fábrica) sin tocar el interruptor: si la pieza no tenía fila, la que
     * se crea no decide el encendido (rige la siembra).
     */
    @Transactional
    public RuleState setLabel(String name, String label) {
        RuleState previous = states().get(name);
        RuleStateEntity row = previous != null
                ? new RuleStateEntity(name, previous.active(), previous.reason(), previous.updatedBy(),
                        previous.updatedTs(), label, previous.deleted() ? Boolean.TRUE : null)
                : new RuleStateEntity(name, activeByDefault(name), null, null, null, label, null);
        return write(row);
    }

    /**
     * Elimina ({@code true}) o restaura ({@code false}) la pieza. En los dos casos queda apagada: eliminar la apaga, y
     * restaurar no la prende (prenderla es otra decisión). Conserva el nombre propio.
     */
    @Transactional
    public RuleState setDeleted(String name, boolean deleted, String user) {
        RuleState previous = states().get(name);
        return write(new RuleStateEntity(name, false, null, user, System.currentTimeMillis(),
                previous != null ? previous.label() : null, deleted ? Boolean.TRUE : null));
    }

    private RuleState write(RuleStateEntity row) {
        String name = row.getNombre();
        repository.save(row);
        RuleState state = RuleState.of(row);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // Sobre el cache vigente al confirmar, no el de antes: un reload() intermedio lo reemplazó.
                    states().put(name, state);
                }
            });
        } else {
            states().put(name, state);
        }
        return state;
    }

    /** Vuelve a leer la base (para tests y para recuperarse de un cambio hecho por fuera). */
    public void reload() {
        cache = load();
    }

    /** Avisa de las filas cuya pieza ya no existe. No las borra. */
    public void logOrphans(Collection<String> knownNames) {
        states().keySet().stream().filter(n -> !knownNames.contains(n)).sorted().forEach(n ->
                log.warn("Hay un estado guardado de la regla '{}', que ya no existe: se ignora (si la regla vuelve, "
                        + "vuelve con lo que se decidió).", n));
    }

    private Map<String, RuleState> states() {
        Map<String, RuleState> loaded = cache;
        if (loaded == null) {
            synchronized (this) {
                if (cache == null) {
                    cache = load();
                }
                loaded = cache;
            }
        }
        return loaded;
    }

    private Map<String, RuleState> load() {
        Map<String, RuleState> out = new ConcurrentHashMap<>();
        repository.findAll().forEach(row -> out.put(row.getNombre(), RuleState.of(row)));
        return out;
    }
}
