package com.yerbanalytics.backend.seguridad;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * La cookie de sesión (design D2): {@code HttpOnly} (fuera del alcance de un XSS),
 * {@code SameSite=Strict} (bloquea CSRF desde otros sitios; {@code localhost:5173} →
 * {@code localhost:8000} es <em>same-site</em>, así que al dashboard no le estorba) y
 * {@code Path=/api}.
 *
 * <p>{@code Secure} se enciende por propiedad: apagado por defecto porque el dashboard corre por
 * HTTP en la LAN. Ojo: si el dashboard va por {@code http} y la API por {@code https}, el navegador
 * los considera sitios distintos y la cookie no viaja. Tienen que compartir esquema y host.
 */
@Component
public class CookieSesion {

    public static final String NOMBRE = "YERBA_SESION";

    private final boolean secure;

    public CookieSesion(@Value("${yerbanalytics.auth.cookie-secure:false}") boolean secure) {
        this.secure = secure;
    }

    /** Cookie de sesión del navegador (sin {@code Max-Age}): la vigencia la decide el servidor. */
    public ResponseCookie emitir(String valor) {
        return base(valor).build();
    }

    public ResponseCookie borrar() {
        return base("").maxAge(Duration.ZERO).build();
    }

    public static String leer(HttpServletRequest req) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (NOMBRE.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                return c.getValue();
            }
        }
        return null;
    }

    private ResponseCookie.ResponseCookieBuilder base(String valor) {
        return ResponseCookie.from(NOMBRE, valor)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/api");
    }
}
