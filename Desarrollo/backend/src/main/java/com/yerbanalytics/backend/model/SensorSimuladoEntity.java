package com.yerbanalytics.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Sensor simulado persistido (dashboard de simulación): un emisor de telemetría identificado
 * por su serial/MAC y asignado a una macro-zona. Sobrevive al reinicio del backend y es
 * <b>independiente</b> del registro de hardware (tabla propia, sin relación con
 * {@code dispositivo}).
 *
 * <p>{@code serialKey} guarda el serial normalizado (minúsculas) y garantiza unicidad
 * case-insensitive; {@code serial} conserva el serial tal como lo cargó el usuario para
 * mostrarlo. El orden de alta se preserva ordenando por {@code id} (autoincremental).
 */
@Entity
@Table(name = "sensor_simulado")
@Getter
@Setter
@NoArgsConstructor
public class SensorSimuladoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Serial normalizado (minúsculas): clave de unicidad case-insensitive. */
    @Column(name = "serial_key", nullable = false, unique = true)
    private String serialKey;

    /** Serial/MAC tal como lo cargó el usuario (para mostrar). */
    @Column(name = "serial", nullable = false)
    private String serial;

    @Column(name = "zona_id", nullable = false)
    private String zonaId;

    public SensorSimuladoEntity(String serialKey, String serial, String zonaId) {
        this.serialKey = serialKey;
        this.serial = serial;
        this.zonaId = zonaId;
    }
}
