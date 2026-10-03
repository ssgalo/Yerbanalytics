package com.yerbanalytics.backend.engine.traza;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Última traza de evaluación por sector y por origen, en memoria. Diagnóstico en vivo, no
 * auditoría: no toca la base ni {@code historial_evento}, y no sobrevive a un reinicio (el
 * barrido la regenera en minutos). La memoria está acotada por la cantidad de sectores.
 *
 * <p>Telemetría (hilo MQTT) y barrido (hilo del scheduler) escriben en paralelo. Las trazas y
 * {@link Ultimas} son inmutables y {@link ConcurrentHashMap#merge} es atómico por clave, así que
 * ningún origen pisa al otro ni se pierde una escritura.
 */
@Component
public class TrazaEvaluacionStore {

    /** Las dos últimas trazas de un sector, una por origen. */
    private record Ultimas(TrazaEvaluacion telemetria, TrazaEvaluacion barrido) {

        static Ultimas de(TrazaEvaluacion t) {
            return t.origen() == OrigenEvaluacion.TELEMETRIA ? new Ultimas(t, null) : new Ultimas(null, t);
        }

        /** Esta instancia con {@code nueva} reemplazando a la de su mismo origen. */
        Ultimas con(TrazaEvaluacion nueva) {
            return nueva.origen() == OrigenEvaluacion.TELEMETRIA
                    ? new Ultimas(nueva, barrido)
                    : new Ultimas(telemetria, nueva);
        }

        TrazaEvaluacion de(OrigenEvaluacion origen) {
            return origen == OrigenEvaluacion.TELEMETRIA ? telemetria : barrido;
        }

        TrazaEvaluacion masReciente() {
            if (telemetria == null) {
                return barrido;
            }
            if (barrido == null) {
                return telemetria;
            }
            // En empate gana la telemetría: es la que trae lecturas.
            return barrido.ts().isAfter(telemetria.ts()) ? barrido : telemetria;
        }
    }

    private final ConcurrentHashMap<String, Ultimas> porSector = new ConcurrentHashMap<>();

    /** Guarda la traza como la última de su sector y origen. */
    public void guardar(TrazaEvaluacion traza) {
        porSector.merge(traza.sectorId(), Ultimas.de(traza), (previas, nuevas) -> previas.con(traza));
    }

    public Optional<TrazaEvaluacion> ultima(String sectorId, OrigenEvaluacion origen) {
        Ultimas u = porSector.get(sectorId);
        return u == null ? Optional.empty() : Optional.ofNullable(u.de(origen));
    }

    /** La más reciente de las dos, sea cual sea el origen. */
    public Optional<TrazaEvaluacion> masReciente(String sectorId) {
        Ultimas u = porSector.get(sectorId);
        return u == null ? Optional.empty() : Optional.ofNullable(u.masReciente());
    }
}
