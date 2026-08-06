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
 * Metadata de una imagen cenital recibida. Los bytes del JPEG NO viven acá: se guardan en el
 * filesystem bajo {@code yerbanalytics.capturas.dir}, particionados por fecha, y esta fila
 * conserva la ruta.
 *
 * <p>Guardar el JPEG como {@code bytea} metería varios GB por semana en el {@code pg_dump} y
 * en la replicación (600 sectores × varios ciclos por día). En disco, el backup de imágenes
 * tiene su propio ciclo de vida.
 *
 * <p>El sector, la zona y la posición de riel se copian de la orden al recibirse: la captura
 * es un registro histórico y no debe cambiar si la orden se modifica después.
 */
@Entity
@Table(name = "captura")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CapturaEntity {

    @Id
    private String id;

    /** Orden que originó la captura. Único: una orden produce a lo sumo una captura. */
    @Column(name = "orden_id", nullable = false, unique = true)
    private String ordenId;

    @Column(name = "sector_id", nullable = false)
    private String sectorId;

    @Column(name = "zona_id", nullable = false)
    private String zonaId;

    @Column(name = "posicion_riel", nullable = false)
    private Integer posicionRiel;

    @Column(name = "dispositivo_id", nullable = false)
    private String dispositivoId;

    @Column(name = "ancho", nullable = false)
    private Integer ancho;

    @Column(name = "alto", nullable = false)
    private Integer alto;

    /** Tamaño del JPEG en bytes. */
    @Column(name = "bytes", nullable = false)
    private Long bytes;

    /** SHA-256 hexadecimal de los bytes. Verificado contra lo recibido; sirve de ETag. */
    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    /** Epoch ms según el reloj del dispositivo. Informativo. */
    @Column(name = "capturada_en")
    private Long capturadaEn;

    /** Epoch ms según el reloj del backend, que es la autoridad. */
    @Column(name = "recibida_en", nullable = false)
    private Long recibidaEn;

    /** Ruta del JPEG relativa al directorio raíz de capturas. */
    @Column(name = "ruta_archivo", nullable = false)
    private String rutaArchivo;

    /**
     * Ajustes de cámara que el cliente pudo aplicar realmente (JSON crudo del contrato). Se
     * registra sin interpretarlo: es la evidencia para evaluar la consistencia fotométrica
     * entre capturas.
     */
    @Column(name = "constraints_json", length = 2000)
    private String constraintsJson;
}
