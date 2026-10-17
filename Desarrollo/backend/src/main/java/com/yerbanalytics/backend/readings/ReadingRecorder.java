package com.yerbanalytics.backend.readings;

import com.yerbanalytics.backend.model.ZoneReadingEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.repository.ZoneReadingRepository;
import org.springframework.stereotype.Service;

/**
 * Escribe el historial: una fila por lectura aceptada, con el estado completo de la zona después de aplicarla.
 *
 * <p>No abre su propia transacción: corre dentro de la de la ingesta ({@code NurseryService.updateTelemetry}), de modo
 * que si falla el guardado la lectura de la zona tampoco se aplica. Preferible a una serie con huecos que haría fallar
 * en silencio "2 lecturas seguidas". Las consultas son de {@link ReadingHistory}.
 */
@Service
public class ReadingRecorder {

    private final ZoneReadingRepository repository;

    public ReadingRecorder(ZoneReadingRepository repository) {
        this.repository = repository;
    }

    /**
     * @param zone       la zona con la lectura YA aplicada (sus métricas vigentes, en las unidades de la plataforma)
     * @param tsMs       hora de la lectura según el nodo, normalizada a ms
     * @param receivedMs cuándo la recibió el backend
     * @param origin     si respondía a un pulso de sensado
     */
    public void record(ZonaEntity zone, long tsMs, long receivedMs, ReadingOrigin origin) {
        ZoneReadingEntity reading = new ZoneReadingEntity();
        reading.setZoneId(zone.getId());
        reading.setTs(tsMs);
        reading.setReceivedTs(receivedMs);
        reading.setOrigin(origin);
        reading.setHumSus(zone.getHumSusRaw());
        reading.setHumAmb(zone.getHumAmbRaw());
        reading.setTemp(zone.getTempRaw());
        reading.setTempSuelo(zone.getTempSueloRaw());
        reading.setUv(zone.getUvRaw());
        reading.setCe(zone.getCeRaw());
        reading.setPhSuelo(zone.getPhSueloRaw());
        reading.setN(zone.getNRaw());
        reading.setP(zone.getPRaw());
        reading.setK(zone.getKRaw());
        repository.save(reading);
    }
}
