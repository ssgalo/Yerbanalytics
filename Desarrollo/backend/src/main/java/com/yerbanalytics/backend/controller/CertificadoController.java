package com.yerbanalytics.backend.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Sirve la CA local para poder instalarla en el dispositivo de captura.
 *
 * <p>Existe por un problema de arranque en frío: el iPhone necesita confiar en la CA para
 * abrir la app de cámara por HTTPS, pero para descargar la CA necesitaría… abrir algo. Este
 * endpoint vive en el puerto <strong>HTTP</strong>, así que el teléfono puede bajarla sin
 * haber confiado en nada todavía.
 *
 * <p>No es un secreto: un certificado de CA es público por definición (lo privado es
 * {@code ca-key.pem}, que nunca sale de la máquina). Servirlo es el patrón habitual de
 * aprovisionamiento en una red controlada.
 *
 * <p>El {@code Content-Type} importa: con {@code application/x-x509-ca-cert} iOS reconoce el
 * archivo y ofrece instalarlo como perfil. Con {@code text/plain} lo muestra como texto y no
 * hay forma de instalarlo desde Safari.
 */
@RestController
@ConditionalOnProperty(name = "yerbanalytics.https.enabled", havingValue = "true")
public class CertificadoController {

    private static final MediaType X509_CA = MediaType.parseMediaType("application/x-x509-ca-cert");

    private final Path ca;

    public CertificadoController(@Value("${yerbanalytics.https.ca:../certs/ca.pem}") String ca) {
        this.ca = Paths.get(ca).toAbsolutePath().normalize();
    }

    @GetMapping("/ca.pem")
    public ResponseEntity<byte[]> descargarCa() throws IOException {
        if (!Files.isReadable(ca)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(X509_CA)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"yerbanalytics-ca.pem\"")
                .body(Files.readAllBytes(ca));
    }
}
