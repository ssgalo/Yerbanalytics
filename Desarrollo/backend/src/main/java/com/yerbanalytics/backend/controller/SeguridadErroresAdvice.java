package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.seguridad.SeguridadExceptions;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Errores de los controllers de seguridad → {@code {"error": mensaje}} con su código. */
@RestControllerAdvice(assignableTypes = {AuthController.class, UsuarioController.class, RolController.class,
        PoliticaSesionController.class, AuditoriaController.class})
public class SeguridadErroresAdvice {

    @ExceptionHandler(SeguridadExceptions.Invalida.class)
    public ResponseEntity<Map<String, String>> invalida(SeguridadExceptions.Invalida ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(SeguridadExceptions.Conflicto.class)
    public ResponseEntity<Map<String, String>> conflicto(SeguridadExceptions.Conflicto ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(SeguridadExceptions.NoEncontrado.class)
    public ResponseEntity<Map<String, String>> noEncontrado(SeguridadExceptions.NoEncontrado ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    /** Dos administradores editaron al mismo usuario a la vez: gana el primero. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> concurrencia(ObjectOptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "Otro administrador modificó este usuario al mismo tiempo. Recargá y reintentá."));
    }
}
