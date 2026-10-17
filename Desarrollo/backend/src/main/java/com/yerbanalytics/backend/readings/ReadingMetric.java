package com.yerbanalytics.backend.readings;

import com.yerbanalytics.backend.model.ZoneReadingEntity;

import java.util.function.Function;

/**
 * Una métrica de la serie. Las reglas piden la que necesitan por este tipo y no por un texto, así una clave mal
 * escrita no compila. La clave es la del contrato MQTT ({@code ZonaEntity.raw}); {@link #DPV} se calcula en la
 * plataforma y no viaja por el contrato.
 */
public enum ReadingMetric {
    HUM_SUS("humSus", ZoneReadingEntity::getHumSus),
    HUM_AMB("humAmb", ZoneReadingEntity::getHumAmb),
    TEMP("temp", ZoneReadingEntity::getTemp),
    TEMP_SUELO("tempSuelo", ZoneReadingEntity::getTempSuelo),
    /** Luz en % (LDR), no radiación UV. */
    UV("uv", ZoneReadingEntity::getUv),
    /** En dS/m. */
    CE("ce", ZoneReadingEntity::getCe),
    PH_SUELO("phSuelo", ZoneReadingEntity::getPhSuelo),
    N("n", ZoneReadingEntity::getN),
    P("p", ZoneReadingEntity::getP),
    K("k", ZoneReadingEntity::getK),
    /** Déficit de presión de vapor en kPa. */
    DPV("dpv", ZoneReadingEntity::getDpv);

    private final String key;
    private final Function<ZoneReadingEntity, Double> reader;

    ReadingMetric(String key, Function<ZoneReadingEntity, Double> reader) {
        this.key = key;
        this.reader = reader;
    }

    public String key() {
        return key;
    }

    /** El valor de esta métrica en la lectura, o {@code null} si la zona nunca la reportó. */
    public Double valueIn(ZoneReadingEntity reading) {
        return reader.apply(reading);
    }
}
