package com.yerbanalytics.backend.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yerbanalytics.backend.seguridad.SeguridadExceptions;
import com.yerbanalytics.backend.seguridad.auditoria.AuditoriaSeguridadEntity;
import com.yerbanalytics.backend.seguridad.auditoria.AuditoriaService;
import com.yerbanalytics.backend.seguridad.auditoria.ObjetivoAuditoria;
import com.yerbanalytics.backend.seguridad.auditoria.TipoAuditoria;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Consulta del registro de auditoría de seguridad (HU-20 CA-03). Sólo lectura: no existe ninguna
 * operación para modificarlo ni borrarlo. Exige {@code auditoria.ver}.
 */
@RestController
@RequestMapping("/api/auditoria")
public class AuditoriaController {

    private final AuditoriaService service;
    private final ObjectMapper json;

    public AuditoriaController(AuditoriaService service, ObjectMapper json) {
        this.service = service;
        this.json = json;
    }

    public record Registro(Long id, Instant ocurridoEn, String autorUsername, ObjetivoAuditoria objetivoTipo,
                           String objetivoRef, TipoAuditoria tipo, JsonNode detalle) {}

    public record Pagina(List<Registro> items, int pagina, int tamanio, long total, int totalPaginas) {}

    @GetMapping
    public Pagina buscar(@RequestParam(required = false) String autor,
                         @RequestParam(required = false) String objetivo,
                         @RequestParam(required = false) String tipo,
                         @RequestParam(required = false) String desde,
                         @RequestParam(required = false) String hasta,
                         @RequestParam(defaultValue = "0") int pagina,
                         @RequestParam(defaultValue = "50") int tamanio) {
        Page<AuditoriaSeguridadEntity> p = service.buscar(
                new AuditoriaService.Filtro(autor, objetivo, tipo(tipo), instante(desde), instante(hasta)),
                pagina, tamanio);
        return new Pagina(p.getContent().stream().map(this::aDto).toList(), p.getNumber(), p.getSize(),
                p.getTotalElements(), p.getTotalPages());
    }

    @GetMapping("/verificacion")
    public AuditoriaService.Verificacion verificar() {
        return service.verificar();
    }

    private Registro aDto(AuditoriaSeguridadEntity a) {
        JsonNode detalle;
        try {
            detalle = json.readTree(a.getDetalle());
        } catch (JsonProcessingException e) {
            // Sólo si alguien alteró la fila por fuera: se muestra tal cual y la verificación lo marca.
            detalle = json.getNodeFactory().textNode(a.getDetalle());
        }
        return new Registro(a.getId(), a.getOcurridoEn(), a.getAutorUsername(), a.getObjetivoTipo(),
                a.getObjetivoRef(), a.getTipo(), detalle);
    }

    private static TipoAuditoria tipo(String tipo) {
        if (tipo == null || tipo.isBlank()) {
            return null;
        }
        try {
            return TipoAuditoria.valueOf(tipo.trim());
        } catch (IllegalArgumentException e) {
            throw new SeguridadExceptions.Invalida("El tipo de cambio '" + tipo + "' no existe.");
        }
    }

    private static Instant instante(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(valor.trim());
        } catch (DateTimeParseException e) {
            throw new SeguridadExceptions.Invalida("Fecha inválida: '" + valor + "' (se espera ISO-8601, p. ej. "
                    + "2026-10-10T00:00:00Z).");
        }
    }
}
