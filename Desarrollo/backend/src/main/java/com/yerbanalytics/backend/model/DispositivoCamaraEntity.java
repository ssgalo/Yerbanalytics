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
 * Dispositivo de captura enrolado (la PWA sobre el iPhone hoy; una app Android mañana).
 *
 * <p>Deliberadamente separado de {@link DispositivoEntity}: aquel registra el hardware del
 * vivero (nodos testigo y actuadores) mapeado a sectores y macro-zonas, con batería y señal.
 * Un dispositivo de captura no tiene nada de eso — tiene credenciales, un canal abierto y
 * contadores de captura.
 *
 * <p>El estado operativo NO se almacena: se deriva del silencio desde el último heartbeat,
 * igual que hace {@code HardwareService} con los nodos.
 */
@Entity
@Table(name = "dispositivo_camara")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DispositivoCamaraEntity {

    @Id
    private String id;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    /**
     * Descripción libre de la plataforma del cliente, sólo informativa. El backend NO
     * condiciona su comportamiento a este valor: el contrato es el mismo para todos.
     */
    @Column(name = "plataforma")
    private String plataforma;

    /**
     * Huella (SHA-256) de la credencial de renovación. Nunca se guarda el valor en claro:
     * si la fila se filtra, no se puede reconstruir la credencial.
     */
    @Column(name = "refresh_token_hash", nullable = false, length = 64)
    private String refreshTokenHash;

    /** Epoch ms del último heartbeat. Null si nunca reportó. */
    @Column(name = "ultimo_heartbeat")
    private Long ultimoHeartbeat;

    @Column(name = "capturas_ok", nullable = false)
    private Integer capturasOk;

    @Column(name = "capturas_error", nullable = false)
    private Integer capturasError;

    /** Si el dispositivo declaró estar en condiciones de capturar en su último heartbeat. */
    @Column(name = "captura_listo", nullable = false)
    private Boolean capturaListo;

    @Column(name = "creado_en", nullable = false)
    private Long creadoEn;

    /** Baja lógica: revoca la credencial sin perder el historial de capturas. */
    @Column(name = "revocado", nullable = false)
    private Boolean revocado;
}
