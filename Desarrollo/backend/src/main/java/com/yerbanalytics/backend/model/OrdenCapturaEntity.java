package com.yerbanalytics.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Orden de captura de una imagen cenital (HU-04 CA-01).
 *
 * <p>Es la entidad de primera clase del flujo, no la imagen: la orden es lo que permite saber
 * <em>en qué posición del riel</em> se tomó una foto. La imagen se sube citando el
 * {@code id} de la orden, y el backend rechaza toda subida que no pueda correlacionarse.
 *
 * <p>El estado se persiste (a diferencia del estado operativo del hardware, que se deriva)
 * porque el watchdog necesita saber qué órdenes están en vuelo aunque el backend se reinicie.
 */
@Entity
@Table(name = "orden_captura")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrdenCapturaEntity {

    /**
     * Estados de la orden. {@code RECIBIDA} y {@code ERROR} son terminales: el watchdog los
     * ignora y no se reintentan.
     *
     * <pre>
     *   PENDIENTE --despacho--> ENTREGADA --imagen--> RECIBIDA   (terminal)
     *                               |
     *                               +--acuse--> FALLIDA  --+
     *                               +--plazo--> VENCIDA  --+--> reintento (PENDIENTE)
     *                                                       \-> ERROR (terminal, sin intentos)
     * </pre>
     */
    public enum Estado {
        PENDIENTE, ENTREGADA, RECIBIDA, FALLIDA, VENCIDA, ERROR;

        public boolean esTerminal() {
            return this == RECIBIDA || this == ERROR;
        }
    }

    /** UUID. Es la clave de correlación que el dispositivo cita al subir la imagen. */
    @Id
    private String id;

    @Column(name = "sector_id", nullable = false)
    private String sectorId;

    @Column(name = "zona_id", nullable = false)
    private String zonaId;

    /** Posición del riel donde debe tomarse la captura. */
    @Column(name = "posicion_riel", nullable = false)
    private Integer posicionRiel;

    /** Dispositivo al que se entregó. Null mientras la orden está pendiente de despacho. */
    @Column(name = "dispositivo_id")
    private String dispositivoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 16)
    private Estado estado;

    /** Número de intento en curso, empezando en 1. */
    @Column(name = "intentos", nullable = false)
    private Integer intentos;

    /** Motivo del último fallo, del conjunto cerrado del contrato. Null si nunca falló. */
    @Column(name = "motivo_fallo")
    private String motivoFallo;

    /** Detalle libre del último fallo, complementario al motivo. */
    @Column(name = "detalle_fallo", length = 500)
    private String detalleFallo;

    @Column(name = "creada_en", nullable = false)
    private Long creadaEn;

    @Column(name = "entregada_en")
    private Long entregadaEn;

    /** Epoch ms a partir del cual el watchdog la vence. Se recalcula en cada reentrega. */
    @Column(name = "vence_en", nullable = false)
    private Long venceEn;
}
