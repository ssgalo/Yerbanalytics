package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.seguridad.PoliticaSesionEntity;
import com.yerbanalytics.backend.seguridad.PoliticaSesionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tiempo máximo de inactividad (HU-01 CA-03). Exige {@code usuarios.gestionar}. */
@RestController
@RequestMapping("/api/seguridad/politica")
public class PoliticaSesionController {

    private final PoliticaSesionService service;

    public PoliticaSesionController(PoliticaSesionService service) {
        this.service = service;
    }

    public record Politica(int inactividadMin, int minimo, int maximo) {
        static Politica de(int inactividadMin) {
            return new Politica(inactividadMin, PoliticaSesionEntity.INACTIVIDAD_MINIMO,
                    PoliticaSesionEntity.INACTIVIDAD_MAXIMO);
        }
    }

    public record CambioPolitica(Integer inactividadMin) {}

    @GetMapping
    public Politica leer() {
        return Politica.de(service.inactividadMin());
    }

    @PutMapping
    public Politica cambiar(@RequestBody CambioPolitica body) {
        return Politica.de(service.cambiar(body.inactividadMin()));
    }
}
