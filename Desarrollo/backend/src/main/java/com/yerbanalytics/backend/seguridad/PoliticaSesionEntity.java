package com.yerbanalytics.backend.seguridad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Fila única con la política de sesión. Va aparte de {@code configuracion_operativa} a propósito:
 * ésa es agronómica y la edita otro rol.
 */
@Entity
@Table(name = "politica_sesion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PoliticaSesionEntity {

    /** Siempre 1: hay una sola política. */
    public static final long ID = 1L;

    public static final int INACTIVIDAD_DEFAULT_MIN = 60;
    public static final int INACTIVIDAD_MINIMO = 5;
    public static final int INACTIVIDAD_MAXIMO = 480;

    @Id
    private Long id;

    @Column(name = "inactividad_min", nullable = false)
    private int inactividadMin;
}
