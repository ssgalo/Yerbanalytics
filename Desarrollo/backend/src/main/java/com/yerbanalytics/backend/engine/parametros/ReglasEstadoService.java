package com.yerbanalytics.backend.engine.parametros;

import com.yerbanalytics.backend.dto.ReglaCatalogoDto;
import com.yerbanalytics.backend.engine.overrides.DemoControllable;
import com.yerbanalytics.backend.engine.overrides.DemoControllableRegistry;
import com.yerbanalytics.backend.engine.overrides.OperationalRuleStates;
import com.yerbanalytics.backend.engine.overrides.RuleState;
import com.yerbanalytics.backend.engine.overrides.UnknownControllableException;
import com.yerbanalytics.backend.service.HistorialService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prender o apagar una regla desde Motor de reglas: valida, guarda quién, cuándo y por qué en {@code regla_estado}, y deja
 * el evento en el historial. El backend es la autoridad de las exigencias (motivo y confirmación al apagar una
 * protección) aunque la interfaz ya las pida.
 */
@Service
public class ReglasEstadoService {

    /** Largo máximo del motivo. */
    static final int MOTIVO_MAX = 500;

    /** Largo máximo del nombre propio, después de recortar. */
    static final int ETIQUETA_MAX = 80;

    private final DemoControllableRegistry registry;
    private final OperationalRuleStates states;
    private final HistorialService historial;
    private final ReglasCatalogo catalogo;
    private final TransactionTemplate transaccion;
    /** Un candado por regla (el conjunto de nombres es el de las piezas registradas, acotado). */
    private final Map<String, Object> candados = new ConcurrentHashMap<>();

    public ReglasEstadoService(DemoControllableRegistry registry, OperationalRuleStates states,
                               HistorialService historial, ReglasCatalogo catalogo,
                               ObjectProvider<PlatformTransactionManager> transactionManager) {
        PlatformTransactionManager tm = transactionManager.getIfAvailable();
        this.transaccion = tm != null ? new TransactionTemplate(tm) : null;
        this.registry = registry;
        this.states = states;
        this.historial = historial;
        this.catalogo = catalogo;
    }

    /**
     * Cambia el estado operativo de una pieza. Si ya está en ese estado no escribe nada ni registra nada, salvo un
     * pedido explícito de apagar una pieza que está apagada sólo por la siembra ({@code yerbanalytics.reglas.retiradas},
     * sin fila): es una decisión de una persona y queda registrada (fila y evento). Prender nunca pide motivo ni
     * confirmación; si el motivo viene, se guarda.
     *
     * <p>Se serializa por regla, <i>incluido el commit</i>: la comprobación de "mismo estado", la escritura y el evento
     * de historial son atómicos respecto de otro pedido para la misma regla.
     *
     * @param usuario quien firma el cambio
     * @return la pieza con su estado ya cambiado
     * @throws UnknownControllableException  si la pieza no existe
     * @throws ReglaEstadoInvalidoException si falta {@code activa}, el motivo es muy largo o, al apagar una protección,
     *                                       falta el motivo o la confirmación
     */
    public ReglaCatalogoDto cambiar(String name, Boolean activa, String motivo, Boolean confirmaProteccion, String usuario) {
        DemoControllable pieza = registry.find(name).orElseThrow(() -> new UnknownControllableException(name));
        // El candado va por fuera de la transacción: tiene que cubrir también el commit.
        synchronized (candados.computeIfAbsent(name, k -> new Object())) {
            if (transaccion == null) {
                return aplicar(pieza, activa, motivo, confirmaProteccion, usuario);
            }
            return transaccion.execute(status -> aplicar(pieza, activa, motivo, confirmaProteccion, usuario));
        }
    }

    private ReglaCatalogoDto aplicar(DemoControllable pieza, Boolean activa, String motivo, Boolean confirmaProteccion,
                                     String usuario) {
        String name = pieza.name();
        if (activa == null) {
            throw new ReglaEstadoInvalidoException("ACTIVA_REQUERIDA", "Falta indicar si la regla se prende o se apaga.", null);
        }
        if (activa && eliminada(name)) {
            throw new ReglaConflictoException("REGLA_ELIMINADA",
                    "La regla está eliminada: hay que restaurarla antes de prenderla.");
        }
        // Mismo estado que el vigente: no se escribe nada, ni se pide motivo (no hay nada que justificar). Excepto apagar
        // lo que sólo la siembra tiene apagado: ahí una persona lo decide y se registra.
        boolean apagadaSoloPorLaSiembra = !activa && states.state(name).isEmpty();
        if (states.isActive(name) == activa && !apagadaSoloPorLaSiembra) {
            return catalogo.regla(name).orElseThrow();
        }

        String motivoLimpio = motivo == null || motivo.isBlank() ? null : motivo.strip();
        if (motivoLimpio != null && motivoLimpio.length() > MOTIVO_MAX) {
            throw new ReglaEstadoInvalidoException("MOTIVO_DEMASIADO_LARGO",
                    "El motivo no puede superar los " + MOTIVO_MAX + " caracteres.", null);
        }
        String consecuencia = pieza.protection().orElse(null);
        if (!activa && consecuencia != null) {
            if (motivoLimpio == null) {
                throw new ReglaEstadoInvalidoException("MOTIVO_REQUERIDO",
                        "Apagar una protección exige indicar el motivo.", null);
            }
            if (!Boolean.TRUE.equals(confirmaProteccion)) {
                throw new ReglaEstadoInvalidoException("CONFIRMACION_REQUERIDA",
                        "Apagar una protección exige confirmar la consecuencia.", consecuencia);
            }
        }

        RuleState nuevo = states.set(name, activa, motivoLimpio, usuario);
        historial.registrarEstadoRegla(usuario, name, etiquetaVigente(pieza), pieza.code(), activa, motivoLimpio,
                !activa ? consecuencia : null);
        return catalogo.regla(pieza, nuevo);
    }

    /**
     * Le da a la pieza un nombre propio, o restablece el de fábrica con {@code null} o vacío (rediseno-motor-reglas D-5).
     * No toca el interruptor. Si el nombre no cambia no escribe ni registra nada.
     *
     * @throws UnknownControllableException  si la pieza no existe
     * @throws ReglaEstadoInvalidoException si el nombre recortado supera {@link #ETIQUETA_MAX} caracteres
     */
    public ReglaCatalogoDto renombrar(String name, String etiqueta, String usuario) {
        DemoControllable pieza = registry.find(name).orElseThrow(() -> new UnknownControllableException(name));
        String limpia = etiqueta == null || etiqueta.isBlank() ? null : etiqueta.strip();
        if (limpia != null && limpia.length() > ETIQUETA_MAX) {
            throw new ReglaEstadoInvalidoException("ETIQUETA_DEMASIADO_LARGA",
                    "El nombre no puede superar los " + ETIQUETA_MAX + " caracteres.", null);
        }
        // Igual al de fábrica es restablecer: "nombre propio" significa "distinto del de fábrica".
        String nueva = limpia == null || limpia.equals(pieza.label()) ? null : limpia;
        return serializado(name, () -> {
            String anterior = etiquetaVigente(pieza);
            String previa = states.row(name).map(RuleState::label).orElse(null);
            if (java.util.Objects.equals(previa, nueva)) {
                return catalogo.regla(name).orElseThrow();
            }
            RuleState fila = states.setLabel(name, nueva);
            historial.registrarNombreRegla(usuario, name, pieza.code(), anterior, nueva != null ? nueva : pieza.label(),
                    nueva == null);
            return catalogo.regla(pieza, fila);
        });
    }

    /**
     * Saca la pieza de la vista y la apaga, en una transacción. Ya eliminada: no escribe nada.
     *
     * @throws UnknownControllableException si la pieza no existe
     * @throws ReglaConflictoException      si es una protección: sólo se apaga, con motivo
     */
    public ReglaCatalogoDto eliminar(String name, String usuario) {
        DemoControllable pieza = registry.find(name).orElseThrow(() -> new UnknownControllableException(name));
        if (pieza.protection().isPresent()) {
            throw new ReglaConflictoException("PROTECCION_NO_ELIMINABLE",
                    "Una protección no se elimina: sólo se apaga, indicando el motivo.");
        }
        return serializado(name, () -> {
            if (eliminada(name)) {
                return catalogo.regla(name).orElseThrow();
            }
            RuleState fila = states.setDeleted(name, true, usuario);
            historial.registrarEliminacionRegla(usuario, name, etiquetaVigente(pieza), pieza.code(), true);
            return catalogo.regla(pieza, fila);
        });
    }

    /**
     * Devuelve a la vista una pieza eliminada, apagada: prenderla es otra decisión. Si no estaba eliminada no escribe
     * nada.
     *
     * @throws UnknownControllableException si la pieza no existe
     */
    public ReglaCatalogoDto restaurar(String name, String usuario) {
        DemoControllable pieza = registry.find(name).orElseThrow(() -> new UnknownControllableException(name));
        return serializado(name, () -> {
            if (!eliminada(name)) {
                return catalogo.regla(name).orElseThrow();
            }
            RuleState fila = states.setDeleted(name, false, usuario);
            historial.registrarEliminacionRegla(usuario, name, etiquetaVigente(pieza), pieza.code(), false);
            return catalogo.regla(pieza, fila);
        });
    }

    private boolean eliminada(String name) {
        return states.row(name).map(RuleState::deleted).orElse(false);
    }

    /** El nombre que ve el vivero: el propio o, si no tiene, el de fábrica. */
    private String etiquetaVigente(DemoControllable pieza) {
        return states.row(pieza.name()).map(RuleState::label).orElse(pieza.label());
    }

    /** Mismo candado por regla que {@link #cambiar}, también alrededor del commit. */
    private ReglaCatalogoDto serializado(String name, java.util.function.Supplier<ReglaCatalogoDto> accion) {
        synchronized (candados.computeIfAbsent(name, k -> new Object())) {
            if (transaccion == null) {
                return accion.get();
            }
            return transaccion.execute(status -> accion.get());
        }
    }
}
