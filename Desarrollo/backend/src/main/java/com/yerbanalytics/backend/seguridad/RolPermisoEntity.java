package com.yerbanalytics.backend.seguridad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Una casilla tildada de la matriz: el rol tiene el permiso. El permiso se guarda por su código
 * ({@code reglas.editar}), el mismo que viaja en la API.
 */
@Entity
@Table(name = "rol_permiso")
@IdClass(RolPermisoEntity.Clave.class)
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RolPermisoEntity {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "rol", length = 30)
    private Rol rol;

    @Id
    @Column(name = "permiso", length = 40)
    private String permiso;

    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Clave implements Serializable {
        private Rol rol;
        private String permiso;
    }
}
