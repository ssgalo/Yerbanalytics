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
 * Preferencias de visualización del dashboard. Fila única ({@code id = 1}); sin fila, los valores
 * son los de fábrica. Es entidad aparte de {@link ConfiguracionOperativaEntity} a propósito: esa la
 * lee el motor y su cambio queda auditado (HU-15), y una preferencia de pantalla no tiene nada que
 * ver con eso. La crea Hibernate ({@code ddl-auto=update}): no necesita migración manual.
 */
@Entity
@Table(name = "preferencia_dashboard")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PreferenciaDashboardEntity {

    public static final long ID_UNICO = 1L;

    @Id
    private Long id;

    /** Si la pestaña "Demo Expo" se muestra en el menú. */
    @Column(name = "demo_expo_visible", nullable = false)
    private boolean demoExpoVisible;
}
