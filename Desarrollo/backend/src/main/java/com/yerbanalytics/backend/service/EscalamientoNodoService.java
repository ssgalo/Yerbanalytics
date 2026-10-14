package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.engine.DetalleAlerta;
import com.yerbanalytics.backend.engine.NivelAlerta;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.repository.ZonaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Escalamiento por pulsos consecutivos sin respuesta del nodo testigo (design D4). La cuenta se persiste en la
 * zona ({@link ZonaEntity#getPulsosSinRespuesta()}) para que un reinicio en medio de una racha no la pierda.
 *
 * <p>1.º y 2.º pulso sin respuesta → alerta {@code WARNING}; 3.º → {@code CRITICAL} y el nodo queda fuera de
 * servicio; del 4.º en adelante, sin alertas. Cualquier telemetría de la zona vuelve la cuenta a 0 y, si el nodo
 * estaba fuera de servicio, deja una alerta {@code INFO}.
 */
@Service
public class EscalamientoNodoService {

    private static final Logger log = LoggerFactory.getLogger(EscalamientoNodoService.class);

    /** Nombre de "regla" con que las alertas del pulso quedan en el historial. */
    public static final String REGLA = "PulsoSensado";

    /** Pulsos consecutivos sin respuesta a partir de los cuales el nodo está fuera de servicio. */
    public static final int FUERA_DE_SERVICIO = 3;

    private final ZonaRepository zonaRepository;
    private final HistorialService historialService;
    private final Clock reloj;

    public EscalamientoNodoService(ZonaRepository zonaRepository, HistorialService historialService,
                                   Clock relojVivero) {
        this.zonaRepository = zonaRepository;
        this.historialService = historialService;
        this.reloj = relojVivero;
    }

    /** {@code true} si la cuenta de la zona la pone fuera de servicio. */
    public static boolean fueraDeServicio(ZonaEntity zona) {
        return cuenta(zona) >= FUERA_DE_SERVICIO;
    }

    /** Pulsos consecutivos sin respuesta; una fila sin valor se lee como 0. */
    public static int cuenta(ZonaEntity zona) {
        Integer n = zona.getPulsosSinRespuesta();
        return n != null ? n : 0;
    }

    /** Un pulso cerró sin respuesta de {@code zonaId}: suma uno a la cuenta y registra la alerta que corresponda. */
    @Transactional
    public void registrarSinRespuesta(String zonaId) {
        ZonaEntity zona = zonaRepository.findById(zonaId).orElse(null);
        if (zona == null) {
            return;   // la topología cambió en medio del pulso
        }
        int n = cuenta(zona) + 1;
        zona.setPulsosSinRespuesta(n);
        zonaRepository.save(zona);

        NivelAlerta nivel = n < FUERA_DE_SERVICIO ? NivelAlerta.WARNING
                : n == FUERA_DE_SERVICIO ? NivelAlerta.CRITICAL : null;
        if (nivel == null) {
            log.debug("Zona {}: {} pulsos seguidos sin respuesta, ya fuera de servicio.", zonaId, n);
            return;
        }
        String texto = nivel == NivelAlerta.CRITICAL
                ? "El nodo testigo de " + zonaId + " no respondió a " + n + " pulsos de sensado seguidos: "
                        + "queda fuera de servicio."
                : "El nodo testigo de " + zonaId + " no respondió al pulso de sensado ("
                        + n + (n == 1 ? " pulso" : " pulsos seguidos") + " sin respuesta).";
        historialService.registrarAlerta(zonaId, zona.getName(), REGLA, new DetalleAlerta(nivel, texto), reloj.millis());
    }

    /**
     * Llegó telemetría de la zona: la cuenta vuelve a 0. Se invoca dentro de la transacción de la ingesta, con la
     * zona ya cargada; no la guarda (lo hace la ingesta). Devuelve {@code true} si la cuenta cambió.
     */
    public boolean reiniciar(ZonaEntity zona) {
        int previa = cuenta(zona);
        if (previa == 0) {
            return false;
        }
        zona.setPulsosSinRespuesta(0);
        if (previa >= FUERA_DE_SERVICIO) {
            historialService.registrarAlerta(zona.getId(), zona.getName(), REGLA,
                    new DetalleAlerta(NivelAlerta.INFO,
                            "El nodo testigo de " + zona.getId() + " volvió a responder."),
                    reloj.millis());
        }
        return true;
    }
}
