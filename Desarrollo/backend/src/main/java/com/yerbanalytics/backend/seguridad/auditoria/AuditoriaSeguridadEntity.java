package com.yerbanalytics.backend.seguridad.auditoria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/**
 * Registro de auditoría de seguridad (design D7). Append-only en tres capas:
 *
 * <ol>
 *   <li>Aplicación: {@code @Immutable}, columnas {@code updatable=false} y un repositorio que sólo
 *       sabe dar altas y consultar.</li>
 *   <li>Base: triggers que rechazan {@code UPDATE}, {@code DELETE} y {@code TRUNCATE}
 *       ({@code resources/auditoria-triggers.sql}, instalados al arrancar).</li>
 *   <li>Evidencia: {@code hash = SHA-256(hash_anterior ‖ contenido canónico)}. Quien altere una
 *       fila por fuera del sistema rompe la cadena, y {@code GET /api/auditoria/verificacion} lo
 *       detecta.</li>
 * </ol>
 *
 * <p>Se copian los usernames, no sólo los ids, para que el registro se lea solo aunque el usuario
 * cambie o se dé de baja.
 */
@Entity
@Immutable
@Table(name = "auditoria_seguridad", indexes = {
        @Index(name = "idx_auditoria_objetivo", columnList = "objetivo_ref"),
        @Index(name = "idx_auditoria_ocurrido", columnList = "ocurrido_en")
})
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AuditoriaSeguridadEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** UTC, truncado a milisegundos para que el hash se pueda recalcular igual. */
    @Column(name = "ocurrido_en", nullable = false, updatable = false)
    private Instant ocurridoEn;

    /** Nulo cuando el autor es el sistema (siembra, Administrador inicial). */
    @Column(name = "autor_id", updatable = false)
    private Long autorId;

    @Column(name = "autor_username", length = 40, updatable = false)
    private String autorUsername;

    @Enumerated(EnumType.STRING)
    @Column(name = "objetivo_tipo", nullable = false, length = 20, updatable = false)
    private ObjetivoAuditoria objetivoTipo;

    @Column(name = "objetivo_ref", nullable = false, length = 40, updatable = false)
    private String objetivoRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 40, updatable = false)
    private TipoAuditoria tipo;

    /** JSON canónico (claves ordenadas) con lo anterior y lo nuevo. Nunca contraseñas ni hashes. */
    @Column(name = "detalle", nullable = false, columnDefinition = "text", updatable = false)
    private String detalle;

    @Column(name = "hash_anterior", nullable = false, length = 64, updatable = false)
    private String hashAnterior;

    @Column(name = "hash", nullable = false, length = 64, updatable = false)
    private String hash;
}
