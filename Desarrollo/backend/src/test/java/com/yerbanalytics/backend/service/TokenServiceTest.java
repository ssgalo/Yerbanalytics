package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.config.CapturaProperties;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit test puro: TokenService no toca la base ni el contexto de Spring. */
class TokenServiceTest {

    private TokenService servicio(String secreto, int vidaSeg) {
        CapturaProperties p = new CapturaProperties();
        p.setJwtSecret(secreto);
        p.setTokenVidaSeg(vidaSeg);
        return new TokenService(p);
    }

    @Test
    void emitir_yVerificar_devuelveElDispositivo() {
        TokenService s = servicio("secreto-de-prueba", 900);

        String token = s.emitir("CAM-001");

        assertEquals(Optional.of("CAM-001"), s.verificar(token));
    }

    @Test
    void verificar_rechazaTokenVencido() throws InterruptedException {
        // Vida negativa: el token nace expirado, sin tener que esperar en el test.
        TokenService s = servicio("secreto-de-prueba", -1);

        String token = s.emitir("CAM-001");

        assertTrue(s.verificar(token).isEmpty(), "Un token vencido no debe verificar");
    }

    @Test
    void verificar_rechazaTokenFirmadoConOtroSecreto() {
        String tokenAjeno = servicio("otro-secreto", 900).emitir("CAM-001");

        assertTrue(servicio("secreto-de-prueba", 900).verificar(tokenAjeno).isEmpty());
    }

    @Test
    void verificar_rechazaBasuraYNulos() {
        TokenService s = servicio("secreto-de-prueba", 900);

        assertTrue(s.verificar(null).isEmpty());
        assertTrue(s.verificar("").isEmpty());
        assertTrue(s.verificar("no-es-un-jwt").isEmpty());
    }

    @Test
    void refreshToken_esDistintoCadaVezYSuHuellaEsEstable() {
        TokenService s = servicio("secreto-de-prueba", 900);

        String a = s.generarRefreshToken();
        String b = s.generarRefreshToken();

        assertNotEquals(a, b);
        assertEquals(s.huella(a), s.huella(a), "La huella del mismo valor debe ser estable");
        assertNotEquals(s.huella(a), s.huella(b));
        assertEquals(64, s.huella(a).length());
        assertFalse(s.huella(a).contains(a), "La huella no debe contener el valor en claro");
    }

    @Test
    void codigoVinculacion_esLegibleYSinCaracteresAmbiguos() {
        TokenService s = servicio("secreto-de-prueba", 900);

        for (int i = 0; i < 50; i++) {
            String codigo = s.generarCodigoVinculacion();
            assertTrue(codigo.matches("[2-9A-Z]{4}-[2-9A-Z]{4}"), "Formato inesperado: " + codigo);
            // Lo tipea una persona: nada de 0/O ni 1/I.
            assertFalse(codigo.contains("0") || codigo.contains("O")
                    || codigo.contains("1") || codigo.contains("I"), "Carácter ambiguo en " + codigo);
        }
    }
}
