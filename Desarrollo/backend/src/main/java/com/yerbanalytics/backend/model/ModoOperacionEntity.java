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
 * Modo de operación del vivero (dashboard de simulación), persistido para sobrevivir al
 * reinicio del backend y a cualquier refresco del frontend. Fila única ({@code id = 1});
 * el modo es global. Los valores serializados (`estatico`/`simulacion`) espejan el tipo
 * {@code ModoSimulacion} del frontend.
 */
@Entity
@Table(name = "modo_operacion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ModoOperacionEntity {

    @Id
    private Integer id;

    @Column(name = "modo", nullable = false)
    private String modo;
}
