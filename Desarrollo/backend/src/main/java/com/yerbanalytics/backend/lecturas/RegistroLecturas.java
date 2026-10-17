package com.yerbanalytics.backend.lecturas;

import com.yerbanalytics.backend.model.LecturaZonaEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.repository.LecturaZonaRepository;
import org.springframework.stereotype.Service;

/**
 * Escribe el historial: una fila por lectura aceptada, con el estado completo de la zona después de aplicarla.
 *
 * <p>No abre su propia transacción: corre dentro de la de la ingesta ({@code NurseryService.updateTelemetry}), de modo
 * que si falla el guardado la lectura de la zona tampoco se aplica. Preferible a una serie con huecos que haría fallar
 * en silencio "2 lecturas seguidas". Las consultas son de {@link HistorialLecturas}.
 */
@Service
public class RegistroLecturas {

    private final LecturaZonaRepository repository;

    public RegistroLecturas(LecturaZonaRepository repository) {
        this.repository = repository;
    }

    /**
     * @param zona       la zona con la lectura YA aplicada (sus métricas vigentes, en las unidades de la plataforma)
     * @param tsMs       hora de la lectura según el nodo, normalizada a ms
     * @param recibidaMs cuándo la recibió el backend
     * @param origen     si respondía a un pulso de sensado
     */
    public void registrar(ZonaEntity zona, long tsMs, long recibidaMs, OrigenLectura origen) {
        LecturaZonaEntity lectura = new LecturaZonaEntity();
        lectura.setZonaId(zona.getId());
        lectura.setTs(tsMs);
        lectura.setRecibidaTs(recibidaMs);
        lectura.setOrigen(origen);
        lectura.setHumSus(zona.getHumSusRaw());
        lectura.setHumAmb(zona.getHumAmbRaw());
        lectura.setTemp(zona.getTempRaw());
        lectura.setTempSuelo(zona.getTempSueloRaw());
        lectura.setUv(zona.getUvRaw());
        lectura.setCe(zona.getCeRaw());
        lectura.setPhSuelo(zona.getPhSueloRaw());
        lectura.setN(zona.getNRaw());
        lectura.setP(zona.getPRaw());
        lectura.setK(zona.getKRaw());
        repository.save(lectura);
    }
}
