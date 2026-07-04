package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.dto.SensorSimulado;
import com.yerbanalytics.backend.model.ModoOperacionEntity;
import com.yerbanalytics.backend.model.SensorSimuladoEntity;
import com.yerbanalytics.backend.repository.ModoOperacionRepository;
import com.yerbanalytics.backend.repository.SensorSimuladoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Modo de operación del vivero, conmutable en runtime (herramienta de simulación) y
 * <b>persistido en base de datos</b>: sobrevive al reinicio del backend y a cualquier
 * refresco del frontend.
 *
 * <ul>
 *   <li><b>estático</b>: sistema en reposo; muestra los datos sembrados. Sin ingesta automática.</li>
 *   <li><b>simulación</b>: habilita el envío manual de telemetría por MQTT. Opcionalmente puede
 *       reactivarse el simulador automático.</li>
 * </ul>
 *
 * El modo y los sensores simulados viven en DB (fila única de modo, tabla propia de sensores);
 * el on/off del simulador automático es efímero (en memoria), consistente con su naturaleza de
 * ruido de demostración.
 */
@Service
public class SimulacionService {

    /** Identidad de la fila única de modo. */
    private static final int MODO_ID = 1;

    /** Modo de operación. Los valores serializados espejan el tipo {@code ModoSimulacion} del frontend. */
    public enum Modo {
        ESTATICO("estatico"),
        SIMULACION("simulacion");

        private final String value;

        Modo(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }

        public static Modo fromValue(String raw) {
            if (raw != null) {
                for (Modo m : values()) {
                    if (m.value.equalsIgnoreCase(raw.trim())) {
                        return m;
                    }
                }
            }
            throw new IllegalArgumentException("Modo de simulación inválido: «" + raw + "». Use 'estatico' o 'simulacion'.");
        }
    }

    /** Simulador automático de telemetría (ruido periódico). Apagado por defecto, efímero. */
    private final AtomicBoolean autoSimuladorActivo = new AtomicBoolean(false);

    private final ModoOperacionRepository modoRepository;
    private final SensorSimuladoRepository sensorRepository;

    public SimulacionService(ModoOperacionRepository modoRepository,
                             SensorSimuladoRepository sensorRepository) {
        this.modoRepository = modoRepository;
        this.sensorRepository = sensorRepository;
    }

    /** Modo persistido; si la fila no existe o es inválida, degrada a estático. */
    @Transactional(readOnly = true)
    public Modo getModo() {
        return modoRepository.findById(MODO_ID)
                .map(e -> {
                    try {
                        return Modo.fromValue(e.getModo());
                    } catch (IllegalArgumentException ex) {
                        return Modo.ESTATICO;
                    }
                })
                .orElse(Modo.ESTATICO);
    }

    @Transactional
    public void setModo(Modo nuevo) {
        modoRepository.save(new ModoOperacionEntity(MODO_ID, nuevo.value()));
        // Al volver a estático se apaga el simulador automático: nada debe ingresar en reposo.
        if (nuevo == Modo.ESTATICO) {
            autoSimuladorActivo.set(false);
        }
    }

    public boolean isSimulacion() {
        return getModo() == Modo.SIMULACION;
    }

    public boolean isAutoSimuladorActivo() {
        return autoSimuladorActivo.get();
    }

    public void setAutoSimuladorActivo(boolean activo) {
        autoSimuladorActivo.set(activo);
    }

    /** El simulador automático sólo corre en modo simulación y si fue activado explícitamente. */
    public boolean debeCorrerSimuladorAutomatico() {
        return isSimulacion() && autoSimuladorActivo.get();
    }

    // ------------------------------------------------------------------
    // Sensores simulados (persistidos, desacoplados del registro de hardware)
    // ------------------------------------------------------------------

    /** Lista de sensores simulados, en orden de alta. */
    @Transactional(readOnly = true)
    public List<SensorSimulado> listarSensores() {
        List<SensorSimulado> out = new ArrayList<>();
        for (SensorSimuladoEntity e : sensorRepository.findAllByOrderByIdAsc()) {
            out.add(new SensorSimulado(e.getSerial(), e.getZonaId()));
        }
        return out;
    }

    /**
     * Da de alta un sensor simulado. No toca el registro de hardware.
     *
     * @throws IllegalArgumentException si falta el serial/zona o el serial/MAC ya existe
     */
    @Transactional
    public SensorSimulado crearSensor(String serial, String zonaId) {
        String s = norm(serial);
        String z = norm(zonaId);
        if (s == null) {
            throw new IllegalArgumentException("El serial/MAC del sensor es obligatorio.");
        }
        if (z == null) {
            throw new IllegalArgumentException("La macro-zona del sensor es obligatoria.");
        }
        String key = s.toLowerCase();
        if (sensorRepository.existsBySerialKey(key)) {
            throw new IllegalArgumentException("Ya existe un sensor simulado con el serial/MAC «" + s + "».");
        }
        sensorRepository.save(new SensorSimuladoEntity(key, s, z));
        return new SensorSimulado(s, z);
    }

    /** Baja de un sensor simulado por serial/MAC. Devuelve true si existía. */
    @Transactional
    public boolean eliminarSensor(String serial) {
        String key = norm(serial) == null ? null : norm(serial).toLowerCase();
        if (key == null) {
            return false;
        }
        return sensorRepository.deleteBySerialKey(key) > 0;
    }

    private static String norm(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }
}
