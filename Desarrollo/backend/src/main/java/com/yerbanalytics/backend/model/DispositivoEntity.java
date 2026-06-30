package com.yerbanalytics.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Dispositivo físico instalado en el vivero (HU-18 / HU-21). Registro de primera clase:
 * el nodo testigo se mapea a una macro-zona ({@code zonaId}) y los actuadores
 * (electroválvula, bomba peristáltica, mediasombra) a un sector ({@code sectorId}).
 * El estado operativo NO se almacena: se deriva en runtime a partir del último reporte
 * y de la falla (ver {@code HardwareService}).
 */
@Entity
@Table(name = "dispositivo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DispositivoEntity {

    @Id
    private String id;

    /** Serial o MAC del equipo. Único en toda la flota (HU-18 CA-03). */
    @Column(name = "serial", nullable = false, unique = true)
    private String serial;

    /** nodo_testigo | electrovalvula | bomba_peristaltica | mediasombra */
    @Column(name = "tipo", nullable = false)
    private String tipo;

    /** Macro-zona asociada (sólo para el nodo testigo). */
    @Column(name = "zona_id")
    private String zonaId;

    /** Sector asociado (sólo para los actuadores). */
    @Column(name = "sector_id")
    private String sectorId;

    /** Nivel de batería en % (nodos a batería); null en equipos alimentados por red. */
    @Column(name = "bateria")
    private Integer bateria;

    /** Calidad de señal en dBm; null en equipos sin radio propia. */
    @Column(name = "senal")
    private Integer senal;

    /** Epoch ms del último heartbeat recibido. */
    @Column(name = "ultimo_update")
    private Long ultimoUpdate;

    /** Falla física registrada (p. ej. "Falla Hidráulica"); null si está sano. */
    @Column(name = "falla")
    private String falla;
}
