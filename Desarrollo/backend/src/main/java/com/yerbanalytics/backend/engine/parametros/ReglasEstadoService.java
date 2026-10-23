package com.yerbanalytics.backend.engine.parametros;

import com.yerbanalytics.backend.dto.ReglaCatalogoDto;
import com.yerbanalytics.backend.engine.overrides.DemoControllable;
import com.yerbanalytics.backend.engine.overrides.DemoControllableRegistry;
import com.yerbanalytics.backend.engine.overrides.OperationalRuleStates;
import com.yerbanalytics.backend.engine.overrides.RuleState;
import com.yerbanalytics.backend.engine.overrides.UnknownControllableException;
import com.yerbanalytics.backend.service.HistorialService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prender o apagar una regla desde Motor de reglas: valida, guarda quién, cuándo y por qué en {@code regla_estado}, y deja
 * el evento en el historial. El backend es la autoridad de las exigencias (motivo y confirmación al apagar una
 * protección) aunque la interfaz ya las pida.
 */
@Service
public class ReglasEstadoService {

    /** Largo máximo del motivo. */
    static final int MOTIVO_MAX = 500;

    private final DemoControllableRegistry registry;
    private final OperationalRuleStates states;
    private final HistorialService historial;
    private final ReglasCatalogo catalogo;

    public ReglasEstadoService(DemoControllableRegistry registry, OperationalRuleStates states,
                               HistorialService historial, ReglasCatalogo catalogo) {
        this.registry = registry;
        this.states = states;
        this.historial = historial;
        this.catalogo = catalogo;
    }

    /**
     * Cambia el estado operativo de una pieza. Si ya está en ese estado no escribe nada ni registra nada. Prender nunca
     * pide motivo ni confirmación; si el motivo viene, se guarda.
     *
     * @param usuario quien firma el cambio
     * @return la pieza con su estado ya cambiado
     * @throws UnknownControllableException  si la pieza no existe
     * @throws ReglaEstadoInvalidoException si falta {@code activa}, el motivo es muy largo o, al apagar una protección,
     *                                       falta el motivo o la confirmación
     */
    @Transactional
    public ReglaCatalogoDto cambiar(String name, Boolean activa, String motivo, Boolean confirmaProteccion, String usuario) {
        DemoControllable pieza = registry.find(name).orElseThrow(() -> new UnknownControllableException(name));
        if (activa == null) {
            throw new ReglaEstadoInvalidoException("ACTIVA_REQUERIDA", "Falta indicar si la regla se prende o se apaga.", null);
        }
        // Mismo estado que el vigente: no se escribe nada, ni se pide motivo (no hay nada que justificar).
        if (states.isActive(name) == activa) {
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
        historial.registrarEstadoRegla(usuario, name, pieza.label(), pieza.code(), activa, motivoLimpio,
                !activa ? consecuencia : null);
        return catalogo.regla(pieza, nuevo);
    }
}
