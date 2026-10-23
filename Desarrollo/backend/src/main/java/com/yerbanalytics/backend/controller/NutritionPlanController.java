package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.dto.NewNutritionInterval;
import com.yerbanalytics.backend.dto.NutritionPlan;
import com.yerbanalytics.backend.exception.InvalidNutritionIntervalException;
import com.yerbanalytics.backend.exception.NutritionZoneNotFoundException;
import com.yerbanalytics.backend.nutrition.NutritionPlanService;
import com.yerbanalytics.backend.seguridad.UsuarioSesion;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Plan de nutrición de una macro-zona (reglas_v2 §8.4): el intervalo entre aplicaciones, que cada zona puede fijar
 * (3–30 días). El plan mismo se lee del snapshot ({@code GET /api/nursery}, {@code zonas[].nutritionPlan}); lo ejecuta el
 * motor, no esta API. Quién cambia el intervalo sale de la sesión, no del cuerpo.
 */
@RestController
@RequestMapping("/api/zonas/{zoneId}/plan-nutricion")
public class NutritionPlanController {

    private final NutritionPlanService plan;

    public NutritionPlanController(NutritionPlanService plan) {
        this.plan = plan;
    }

    @PutMapping
    public ResponseEntity<NutritionPlan> setInterval(
            @PathVariable String zoneId,
            @RequestBody(required = false) NewNutritionInterval body,
            @RequestHeader(value = "X-Usuario", required = false) String user) {
        Integer days = body == null ? null : body.intervalDays();
        return ResponseEntity.ok(plan.setInterval(zoneId, days, UsuarioSesion.autorDelCambio(user)));
    }

    @ExceptionHandler({InvalidNutritionIntervalException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, String>> handleInvalid(Exception ex) {
        String message = ex instanceof InvalidNutritionIntervalException ? ex.getMessage()
                : "El intervalo debe ser un número entero de días (intervalDays).";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", message));
    }

    @ExceptionHandler(NutritionZoneNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NutritionZoneNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }
}
