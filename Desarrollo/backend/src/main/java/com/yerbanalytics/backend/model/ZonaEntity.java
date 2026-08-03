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
}
