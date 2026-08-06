package com.yerbanalytics.backend.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.yerbanalytics.backend.config.CapturaProperties;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Emisión y verificación de los tokens de acceso de los dispositivos de captura.
 *
 * <p>Espacio de credenciales propio y acotado: no hay usuarios en la plataforma (HU-01 sigue
 * sin implementar) y esto autentica dispositivos, no personas. Cuando llegue el login habrá
 * que unificar; hasta entonces esto no le impone nada al resto de la API.
 *
 * <p>El token de acceso es de vida corta a propósito. El código de un cliente distribuido es
 * inspeccionable, así que la credencial de larga duración ({@code refreshToken}) se obtiene en
 * runtime tras el enrolamiento y nunca vive en el fuente del cliente.
 */
@Service
public class TokenService {

    private static final String ISSUER = "yerbanalytics";
    private static final String CLAIM_TIPO = "tipo";
    private static final String TIPO_CAMARA = "camara";

    private final Algorithm algoritmo;
    private final JWTVerifier verificador;
    private final int vidaSeg;
    private final SecureRandom random = new SecureRandom();

    public TokenService(CapturaProperties props) {
        this.algoritmo = Algorithm.HMAC256(props.getJwtSecret());
        this.verificador = JWT.require(algoritmo)
                .withIssuer(ISSUER)
                .withClaim(CLAIM_TIPO, TIPO_CAMARA)
                .build();
        this.vidaSeg = props.getTokenVidaSeg();
    }

    /** Emite un token de acceso cuyo sujeto es el dispositivo. */
    public String emitir(String dispositivoId) {
        Instant ahora = Instant.now();
        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(dispositivoId)
                .withClaim(CLAIM_TIPO, TIPO_CAMARA)
                .withIssuedAt(ahora)
                .withExpiresAt(ahora.plusSeconds(vidaSeg))
                .sign(algoritmo);
    }

    public int getVidaSeg() {
        return vidaSeg;
    }

    /**
     * Devuelve el id del dispositivo si el token es válido y no expiró, o vacío en cualquier
     * otro caso. No distingue el motivo del rechazo a propósito: al cliente sólo le sirve
     * saber que tiene que renovar.
     */
    public Optional<String> verificar(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            DecodedJWT jwt = verificador.verify(token);
            return Optional.ofNullable(jwt.getSubject());
        } catch (JWTVerificationException e) {
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------------
    // Credencial de renovación
    // ------------------------------------------------------------------

    /** Genera una credencial de renovación opaca de 256 bits. */
    public String generarRefreshToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Huella de la credencial. Se persiste esto, nunca el valor en claro: si la fila se
     * filtra, no se puede reconstruir la credencial.
     */
    public String huella(String refreshToken) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(refreshToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", e);
        }
    }

    /**
     * Código de vinculación legible, del estilo {@code 7F3K-2M9Q}. Sin vocales ni caracteres
     * ambiguos (0/O, 1/I): lo tipea una persona en un teléfono montado en un riel.
     */
    public String generarCodigoVinculacion() {
        final String alfabeto = "23456789ACDEFGHJKLMNPQRSTUVWXYZ";
        StringBuilder sb = new StringBuilder(9);
        for (int i = 0; i < 8; i++) {
            if (i == 4) {
                sb.append('-');
            }
            sb.append(alfabeto.charAt(random.nextInt(alfabeto.length())));
        }
        return sb.toString();
    }
}
