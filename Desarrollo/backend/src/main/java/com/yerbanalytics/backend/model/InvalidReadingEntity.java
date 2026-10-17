package com.yerbanalytics.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Última vez que una métrica de una macro-zona se descartó por lectura inválida (S-03) y cuántos ciclos de lectura
 * seguidos lleva fallando. Es lo que permite pasar de {@code WARNING} a {@code CRITICAL} al segundo ciclo: tiene
 * que sobrevivir a un reinicio, así que no vive en memoria.
 *
 * <p>Una fila por (zona, métrica); una lectura válida la borra. {@code zoneId} es una columna simple, sin clave
 * foránea: regenerar la topología reemplaza las zonas y no debe bloquearse por esto (una fila vieja nunca es
 * "el ciclo anterior" de nadie, así que se apaga sola).
 */
@Entity
@Table(name = "invalid_reading")
@Getter
@Setter
@NoArgsConstructor
public class InvalidReadingEntity {

    /** {@code zona:métrica}, ver {@link #idOf}. */
    @Id
    private String id;

    @Column(name = "zone_id", nullable = false)
    private String zoneId;

    /** Clave de la métrica en el contrato ({@code temp}, {@code phSuelo}…). */
    @Column(nullable = false)
    private String metric;

    /** Inicio (epoch ms) del ciclo de lectura en que se descartó por última vez. */
    @Column(name = "cycle_start", nullable = false)
    private Long cycleStart;

    /** Ciclos consecutivos en que falló, contando el de {@link #cycleStart}. */
    @Column(nullable = false)
    private Integer streak;

    public InvalidReadingEntity(String zoneId, String metric, long cycleStart, int streak) {
        this.id = idOf(zoneId, metric);
        this.zoneId = zoneId;
        this.metric = metric;
        this.cycleStart = cycleStart;
        this.streak = streak;
    }

    public static String idOf(String zoneId, String metric) {
        return zoneId + ":" + metric;
    }
}
