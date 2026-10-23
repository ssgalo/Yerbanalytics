package com.yerbanalytics.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Macro-zona del vivero. Además de agrupar sectores, es la <b>dueña de la lectura
 * sensada</b>: el alcance define un único nodo sensor testigo por macro-zona, así que las
 * métricas pertenecen a la zona y no a cada sector (antes se replicaban en los 100
 * sectores, lo que sugería una instrumentación por sector que no existe).
 *
 * <p>Unidades canónicas de lo persistido: {@code ceRaw} en dS/m (la sonda emite µS/cm y la
 * ingesta convierte) y {@code uvRaw} en % de luminosidad (el nodo mide luz con un LDR, no
 * radiación UV; la clave del contrato MQTT se conserva).
 */
@Entity
@Table(name = "zona")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ZonaEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String sub;

    @OneToMany(mappedBy = "zona", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SectorEntity> sectors = new ArrayList<>();

    // ------------------------------------------------------------------
    // Lectura del nodo testigo — ambiente
    // ------------------------------------------------------------------

    @Column(name = "hum_sus_raw")
    private Double humSusRaw;

    @Column(name = "hum_amb_raw")
    private Double humAmbRaw;

    /** Temperatura del aire (DHT11). */
    @Column(name = "temp_raw")
    private Double tempRaw;

    /** Temperatura del sustrato (sonda RS-485) — distinta de la del aire. */
    @Column(name = "temp_suelo_raw")
    private Double tempSueloRaw;

    /** Luminosidad en % (LDR). */
    @Column(name = "uv_raw")
    private Double uvRaw;

    // ------------------------------------------------------------------
    // Lectura del nodo testigo — nutrición del sustrato
    // ------------------------------------------------------------------

    /** Conductividad eléctrica en dS/m. */
    @Column(name = "ce_raw")
    private Double ceRaw;

    @Column(name = "ph_suelo_raw")
    private Double phSueloRaw;

    @Column(name = "n_raw")
    private Double nRaw;

    @Column(name = "p_raw")
    private Double pRaw;

    @Column(name = "k_raw")
    private Double kRaw;

    // ------------------------------------------------------------------
    // Métrica derivada (no viaja por el contrato MQTT)
    // ------------------------------------------------------------------

    /**
     * Déficit de presión de vapor en kPa, calculado por la ingesta con la temperatura y la humedad ambiental de la
     * misma lectura. No es una de las diez métricas sensadas: {@link #raw} y {@link #setRaw} no la conocen.
     */
    @Column(name = "dpv")
    private Double dpvRaw;

    /** Epoch ms de la lectura de la que salió {@link #dpvRaw}; queda vieja si una lectura no trae T o HR. */
    @Column(name = "dpv_ts")
    private Long dpvTs;

    // ------------------------------------------------------------------
    // Estado del nodo testigo
    // ------------------------------------------------------------------

    @Column(name = "nodo_mac")
    private String nodoMac;

    /** Batería del nodo (%). */
    @Column(name = "nodo_battery")
    private Integer nodoBattery;

    /** Calidad de señal WiFi del nodo (dBm RSSI). */
    @Column(name = "nodo_signal")
    private Integer nodoSignal;

    @Column(name = "last_reading_time")
    private Long lastReadingTime;

    /**
     * Epoch ms de la última humedad de sustrato recibida. Se escribe sólo cuando el payload trae
     * {@code humSus}: si la sonda falla el nodo sigue publicando el resto y {@link #lastReadingTime}
     * se refresca, pero {@link #humSusRaw} queda congelado; esta marca delata la humedad vieja.
     */
    @Column(name = "hum_sus_ts")
    private Long humSusTs;

    /**
     * Pulsos de sensado consecutivos sin respuesta del nodo testigo (ver {@code EscalamientoNodoService}).
     * Con 3 o más el nodo está fuera de servicio. Nullable: se lee como 0. Vuelve a 0 con cualquier telemetría.
     */
    @Column(name = "pulsos_sin_respuesta")
    private Integer pulsosSinRespuesta;

    // ------------------------------------------------------------------
    // Plan de nutrición (reglas_v2 §8.4)
    // ------------------------------------------------------------------

    /**
     * Días entre dos aplicaciones del plan en esta macro-zona. {@code null} = no fijó el suyo: rige el valor del
     * catálogo ({@code nutricion.intervalo-plan}).
     */
    @Column(name = "nutrition_plan_interval_days")
    private Integer nutritionPlanIntervalDays;

    /** Epoch ms en que se completó la última tanda del plan (todos los sectores aplicados o descartados). */
    @Column(name = "last_plan_application_ts")
    private Long lastPlanApplicationTs;

    /** Epoch ms desde el que el plan está vencido por adelantamiento (N-02); {@code null} = rige el intervalo. */
    @Column(name = "nutrition_plan_due_ts")
    private Long nutritionPlanDueTs;

    /** Epoch ms en que arrancó la tanda en curso; {@code null} = no hay una abierta. */
    @Column(name = "nutrition_batch_start_ts")
    private Long nutritionBatchStartTs;

    /**
     * Valor crudo de una métrica por su clave del contrato, o {@code null} si el nodo nunca
     * la reportó. Evita que cada consumidor repita la cadena de {@code if}s por clave.
     */
    public Double raw(String metricKey) {
        if (metricKey == null) {
            return null;
        }
        return switch (metricKey) {
            case "humSus" -> humSusRaw;
            case "humAmb" -> humAmbRaw;
            case "temp" -> tempRaw;
            case "tempSuelo" -> tempSueloRaw;
            case "uv" -> uvRaw;
            case "ce" -> ceRaw;
            case "phSuelo" -> phSueloRaw;
            case "n" -> nRaw;
            case "p" -> pRaw;
            case "k" -> kRaw;
            default -> null;
        };
    }

    /**
     * Escribe el valor crudo de una métrica por su clave del contrato (la inversa de {@link #raw}), en la unidad
     * canónica de la zona. No toca {@link #humSusTs}: esa marca la pone la ingesta sólo cuando la humedad entró.
     *
     * @throws IllegalArgumentException si la clave no es una de las diez métricas
     */
    public void setRaw(String metricKey, Double value) {
        switch (metricKey == null ? "" : metricKey) {
            case "humSus" -> humSusRaw = value;
            case "humAmb" -> humAmbRaw = value;
            case "temp" -> tempRaw = value;
            case "tempSuelo" -> tempSueloRaw = value;
            case "uv" -> uvRaw = value;
            case "ce" -> ceRaw = value;
            case "phSuelo" -> phSueloRaw = value;
            case "n" -> nRaw = value;
            case "p" -> pRaw = value;
            case "k" -> kRaw = value;
            default -> throw new IllegalArgumentException("Métrica desconocida: " + metricKey);
        }
    }
}
