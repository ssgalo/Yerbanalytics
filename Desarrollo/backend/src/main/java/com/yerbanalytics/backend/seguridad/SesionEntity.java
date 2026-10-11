package com.yerbanalytics.backend.seguridad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Sesión de usuario del lado del servidor (design D1).
 *
 * <p>La PK es el SHA-256 del identificador que viaja en la cookie, nunca el valor: un volcado de
 * la base no sirve para secuestrar sesiones. Revocar o vencer una sesión es fijar
 * {@code cerradaEn} y {@code motivoCierre}; la fila queda hasta el barrido diario para poder
 * responder con el motivo correcto.
 */
@Entity
@Table(name = "sesion", indexes = @Index(name = "idx_sesion_usuario", columnList = "usuario_id"))
@Getter
@Setter
@NoArgsConstructor
public class SesionEntity {

    @Id
    @Column(name = "id_hash", length = 64)
    private String idHash;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    @Column(name = "creada_en", nullable = false, updatable = false)
    private Instant creadaEn;

    @Column(name = "ultima_actividad", nullable = false)
    private Instant ultimaActividad;

    @Column(name = "cerrada_en")
    private Instant cerradaEn;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_cierre", length = 20)
    private MotivoCierre motivoCierre;

    @Column(name = "ip", length = 64)
    private String ip;

    @Column(name = "agente", length = 255)
    private String agente;

    public boolean abierta() {
        return cerradaEn == null;
    }
}
