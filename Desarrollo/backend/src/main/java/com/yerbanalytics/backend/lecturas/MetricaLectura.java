package com.yerbanalytics.backend.lecturas;

import com.yerbanalytics.backend.model.LecturaZonaEntity;

import java.util.function.Function;

/**
 * Una métrica de la serie. Las reglas piden la que necesitan por este tipo y no por un texto, así una clave mal
 * escrita no compila. La clave es la del contrato MQTT ({@code ZonaEntity.raw}); {@link #DPV} se calcula en la
 * plataforma y no viaja por el contrato.
 */
public enum MetricaLectura {
    HUM_SUS("humSus", LecturaZonaEntity::getHumSus),
    HUM_AMB("humAmb", LecturaZonaEntity::getHumAmb),
    TEMP("temp", LecturaZonaEntity::getTemp),
    TEMP_SUELO("tempSuelo", LecturaZonaEntity::getTempSuelo),
    /** Luz en % (LDR), no radiación UV. */
    UV("uv", LecturaZonaEntity::getUv),
    /** En dS/m. */
    CE("ce", LecturaZonaEntity::getCe),
    PH_SUELO("phSuelo", LecturaZonaEntity::getPhSuelo),
    N("n", LecturaZonaEntity::getN),
    P("p", LecturaZonaEntity::getP),
    K("k", LecturaZonaEntity::getK),
    /** Déficit de presión de vapor en kPa. */
    DPV("dpv", LecturaZonaEntity::getDpv);

    private final String clave;
    private final Function<LecturaZonaEntity, Double> lector;

    MetricaLectura(String clave, Function<LecturaZonaEntity, Double> lector) {
        this.clave = clave;
        this.lector = lector;
    }

    public String clave() {
        return clave;
    }

    /** El valor de esta métrica en la lectura, o {@code null} si la zona nunca la reportó. */
    public Double valorEn(LecturaZonaEntity lectura) {
        return lector.apply(lectura);
    }
}
