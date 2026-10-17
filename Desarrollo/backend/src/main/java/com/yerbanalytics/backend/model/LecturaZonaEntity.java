package com.yerbanalytics.backend.model;

import com.yerbanalytics.backend.lecturas.OrigenLectura;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Una lectura aceptada de una macro-zona: el ESTADO COMPLETO de sus diez métricas después de aplicarla (también las
 * que no vinieron en el payload, con su valor vigente). Así "qué valía X en ese momento" no obliga a sumar filas
 * parciales. Sólo se inserta; la única forma de perderla es la purga por retención.
 *
 * <p>{@code zonaId} es una columna simple, sin clave foránea: regenerar la topología reemplaza las zonas y no debe
 * arrastrar ni bloquearse por el historial, que se vacía solo por retención. Unidades canónicas de {@link ZonaEntity}
 * ({@code ce} en dS/m, {@code uv} en % de luz).
 */
@Entity
@Table(name = "lectura_zona", indexes = {
        // Toda consulta del historial es "de una zona, por tiempo": igualdad y rango (o las últimas N).
        @Index(name = "idx_lectura_zona_zona_ts", columnList = "zona_id, ts DESC"),
        // La purga borra por tiempo sin mirar la zona.
        @Index(name = "idx_lectura_zona_ts", columnList = "ts")
})
@Getter
@Setter
@NoArgsConstructor
public class LecturaZonaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "zona_id", nullable = false)
    private String zonaId;

    /** Hora de la lectura según el nodo (epoch ms, ya normalizada). */
    @Column(nullable = false)
    private Long ts;

    /** Cuándo la recibió el backend (epoch ms). */
    @Column(name = "recibida_ts", nullable = false)
    private Long recibidaTs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OrigenLectura origen;

    @Column(name = "hum_sus")
    private Double humSus;

    @Column(name = "hum_amb")
    private Double humAmb;

    @Column(name = "temp")
    private Double temp;

    @Column(name = "temp_suelo")
    private Double tempSuelo;

    @Column(name = "uv")
    private Double uv;

    @Column(name = "ce")
    private Double ce;

    @Column(name = "ph_suelo")
    private Double phSuelo;

    @Column(name = "n")
    private Double n;

    @Column(name = "p")
    private Double p;

    @Column(name = "k")
    private Double k;

    /** Déficit de presión de vapor (kPa). Lo calcula {@code add-metrica-dpv}; hasta entonces queda nulo. */
    @Column(name = "dpv")
    private Double dpv;
}
