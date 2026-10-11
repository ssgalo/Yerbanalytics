package com.yerbanalytics.backend.seguridad;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Sesiones opacas del lado del servidor (design D1 y D4).
 *
 * <p>La autoridad sobre el vencimiento es este servicio, no el reloj del cliente: cada petición
 * compara {@code ahora − ultima_actividad} contra la política vigente, de forma perezosa (no hay un
 * job que las cierre). Sólo cuenta como actividad lo explícito —el login, una petición que no sea
 * {@code GET} y {@code POST /api/auth/actividad}—; los sondeos del dashboard no la renuevan.
 */
@Service
public class SesionService {

    private static final Logger log = LoggerFactory.getLogger(SesionService.class);

    /** Para no escribir en cada petición: la actividad se registra si cambió más que esto. */
    static final Duration UMBRAL_ACTIVIDAD = Duration.ofSeconds(15);

    /** Antigüedad a partir de la cual el barrido borra sesiones cerradas o inactivas. */
    static final Duration RETENCION = Duration.ofDays(7);

    private final SesionRepository sesiones;
    private final UsuarioRepository usuarios;
    private final PoliticaSesionService politica;
    private final Clock reloj;
    private final SecureRandom random = new SecureRandom();

    public SesionService(SesionRepository sesiones, UsuarioRepository usuarios, PoliticaSesionService politica,
                         Clock reloj) {
        this.sesiones = sesiones;
        this.usuarios = usuarios;
        this.politica = politica;
        this.reloj = reloj;
    }

    /** Abre una sesión y devuelve el identificador en claro, que sólo viaja en la cookie. */
    @Transactional
    public String crear(Long usuarioId, String ip, String agente) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String id = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        Instant ahora = Instant.now(reloj);
        SesionEntity s = new SesionEntity();
        s.setIdHash(Hashes.sha256(id));
        s.setUsuarioId(usuarioId);
        s.setCreadaEn(ahora);
        s.setUltimaActividad(ahora);
        s.setIp(recortar(ip, 64));
        s.setAgente(recortar(agente, 255));
        sesiones.save(s);
        return id;
    }

    /** Resultado de resolver una cookie: o una sesión válida, o el motivo del rechazo. */
    public sealed interface Resolucion {
        record Valida(UsuarioSesion usuario) implements Resolucion {}

        record Rechazada(MotivoRechazo motivo) implements Resolucion {}
    }

    /** El {@code motivo} del {@code 401}, tal como viaja en el cuerpo. */
    public enum MotivoRechazo {
        SIN_SESION, SESION_EXPIRADA, SESION_REVOCADA
    }

    /**
     * Resuelve la cookie de una petición. Si la sesión venció, la cierra en el acto; si la
     * petición cuenta como actividad, la registra.
     */
    @Transactional
    public Resolucion resolver(String idEnClaro, boolean esActividad) {
        Optional<SesionEntity> encontrada = sesiones.findById(Hashes.sha256(idEnClaro));
        if (encontrada.isEmpty()) {
            return new Resolucion.Rechazada(MotivoRechazo.SIN_SESION);
        }
        SesionEntity s = encontrada.get();
        if (!s.abierta()) {
            return new Resolucion.Rechazada(motivoDe(s.getMotivoCierre()));
        }

        Instant ahora = Instant.now(reloj);
        Duration maximo = Duration.ofMinutes(politica.inactividadMin());
        if (Duration.between(s.getUltimaActividad(), ahora).compareTo(maximo) > 0) {
            cerrar(s, MotivoCierre.EXPIRADA, ahora);
            return new Resolucion.Rechazada(MotivoRechazo.SESION_EXPIRADA);
        }

        UsuarioEntity u = usuarios.findById(s.getUsuarioId()).orElse(null);
        if (u == null || u.getEstado() != EstadoUsuario.ACTIVO) {
            // No debería pasar: suspender o dar de baja ya revoca. Es la red de seguridad.
            cerrar(s, MotivoCierre.REVOCADA, ahora);
            return new Resolucion.Rechazada(MotivoRechazo.SESION_REVOCADA);
        }

        if (esActividad && Duration.between(s.getUltimaActividad(), ahora).compareTo(UMBRAL_ACTIVIDAD) > 0) {
            s.setUltimaActividad(ahora);
        }
        return new Resolucion.Valida(new UsuarioSesion(u.getId(), u.getUsername(), u.getNombre(), u.getRol(),
                s.getIdHash(), u.isDebeCambiarClave()));
    }

    /** Logout: cierra la sesión de la cookie. Idempotente. */
    @Transactional
    public void cerrarPorLogout(String sesionHash) {
        sesiones.findById(sesionHash)
                .filter(SesionEntity::abierta)
                .ifPresent(s -> cerrar(s, MotivoCierre.LOGOUT, Instant.now(reloj)));
    }

    /** Revoca las sesiones abiertas de un usuario, salvo la indicada (puede ser null). */
    @Transactional
    public int revocarDeUsuario(Long usuarioId, String exceptoHash) {
        return sesiones.cerrarDeUsuario(usuarioId, exceptoHash, MotivoCierre.REVOCADA, Instant.now(reloj));
    }

    /** Revoca las sesiones abiertas de quienes tienen un rol, salvo la indicada (la del autor). */
    @Transactional
    public int revocarDeRol(Rol rol, String exceptoHash) {
        return sesiones.cerrarDeRol(rol, exceptoHash, MotivoCierre.REVOCADA, Instant.now(reloj));
    }

    /** Barrido diario (3:30): las filas viejas sólo servían para responder el motivo correcto. */
    @Scheduled(cron = "${yerbanalytics.auth.barrido-cron:0 30 3 * * *}")
    @Transactional
    public void barrer() {
        int borradas = sesiones.borrarAnterioresA(Instant.now(reloj).minus(RETENCION));
        if (borradas > 0) {
            log.info("Barrido de sesiones: {} sesiones cerradas o vencidas borradas", borradas);
        }
    }

    private static void cerrar(SesionEntity s, MotivoCierre motivo, Instant ahora) {
        s.setCerradaEn(ahora);
        s.setMotivoCierre(motivo);
    }

    private static MotivoRechazo motivoDe(MotivoCierre m) {
        if (m == MotivoCierre.EXPIRADA) {
            return MotivoRechazo.SESION_EXPIRADA;
        }
        if (m == MotivoCierre.REVOCADA) {
            return MotivoRechazo.SESION_REVOCADA;
        }
        return MotivoRechazo.SIN_SESION;
    }

    private static String recortar(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
