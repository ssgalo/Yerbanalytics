package com.yerbanalytics.backend.seguridad.auditoria;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.MapperFeature;
import com.yerbanalytics.backend.seguridad.Hashes;
import com.yerbanalytics.backend.seguridad.UsuarioSesion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Escritura y consulta del registro de auditoría de seguridad (HU-20 CA-03, design D7).
 *
 * <p>{@link #registrar} exige una transacción en curso ({@code MANDATORY}): el registro se escribe
 * en la misma transacción que el cambio, así que si la auditoría falla, el cambio se revierte, y
 * si el cambio falla, no queda registro.
 */
@Service
public class AuditoriaService {

    /** {@code hash_anterior} del primer registro de la cadena. */
    public static final String GENESIS = "0".repeat(64);

    /**
     * Clave del {@code pg_advisory_xact_lock} que serializa las altas: sin él, dos escrituras
     * concurrentes leerían el mismo "último hash" y la cadena se bifurcaría. Las escrituras de
     * auditoría son pocas, así que no hay contención real.
     */
    private static final long CANDADO_CADENA = 0x59455242_41554449L; // "YERBAUDI"

    /** JSON canónico: claves ordenadas, para que el mismo detalle produzca siempre el mismo texto. */
    private static final ObjectMapper CANONICO = JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .findAndAddModules()
            .build();

    private final AuditoriaSeguridadRepository repo;
    private final JdbcTemplate jdbc;
    private final Clock reloj;

    public AuditoriaService(AuditoriaSeguridadRepository repo, JdbcTemplate jdbc, Clock reloj) {
        this.repo = repo;
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    // ------------------------------------------------------------------
    // Escritura
    // ------------------------------------------------------------------

    /** Registra un cambio hecho por el usuario de la petición en curso. */
    @Transactional(propagation = Propagation.MANDATORY)
    public AuditoriaSeguridadEntity registrar(TipoAuditoria tipo, ObjetivoAuditoria objetivo, String ref,
                                              Map<String, ?> detalle) {
        Autor autor = autorActual();
        return escribir(autor.id(), autor.username(), tipo, objetivo, ref, detalle);
    }

    /** Registra un cambio hecho por el sistema (siembra de la matriz, Administrador inicial). */
    @Transactional(propagation = Propagation.MANDATORY)
    public AuditoriaSeguridadEntity registrarComoSistema(TipoAuditoria tipo, ObjetivoAuditoria objetivo,
                                                         String ref, Map<String, ?> detalle) {
        return escribir(null, null, tipo, objetivo, ref, detalle);
    }

    private AuditoriaSeguridadEntity escribir(Long autorId, String autorUsername, TipoAuditoria tipo,
                                              ObjetivoAuditoria objetivo, String ref, Map<String, ?> detalle) {
        jdbc.execute("SELECT pg_advisory_xact_lock(" + CANDADO_CADENA + ")");

        String anterior = repo.findTopByOrderByIdDesc().map(AuditoriaSeguridadEntity::getHash).orElse(GENESIS);
        Instant ahora = Instant.now(reloj).truncatedTo(ChronoUnit.MILLIS);
        String detalleJson = json(detalle);
        String hash = Hashes.sha256(anterior + contenidoCanonico(ahora, autorId, autorUsername, objetivo, ref,
                tipo, detalleJson));

        return repo.save(new AuditoriaSeguridadEntity(null, ahora, autorId, autorUsername, objetivo, ref, tipo,
                detalleJson, anterior, hash));
    }

    /**
     * Lo que entra al hash. Un arreglo JSON y no una concatenación: así ningún separador puede
     * aparecer dentro de un campo y volver ambigua la frontera entre dos.
     */
    static String contenidoCanonico(Instant ocurridoEn, Long autorId, String autorUsername, ObjetivoAuditoria objetivo,
                                    String ref, TipoAuditoria tipo, String detalleJson) {
        List<Object> campos = new ArrayList<>();
        campos.add(ocurridoEn.toEpochMilli());
        campos.add(autorId);
        campos.add(autorUsername);
        campos.add(objetivo.name());
        campos.add(ref);
        campos.add(tipo.name());
        campos.add(detalleJson);
        return json(campos);
    }

    private static String json(Object valor) {
        try {
            return CANONICO.writeValueAsString(valor);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el detalle de auditoría", e);
        }
    }

    private record Autor(Long id, String username) {}

    /**
     * Autor de la petición en curso. Fuera de una sesión de usuario (p. ej. un test con un usuario
     * simulado) se usa el nombre de la autenticación sin id; sin autenticación, el sistema.
     */
    private static Autor autorActual() {
        return UsuarioSesion.actual()
                .map(u -> new Autor(u.usuarioId(), u.username()))
                .orElseGet(() -> {
                    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                    return auth != null && auth.isAuthenticated() ? new Autor(null, auth.getName()) : new Autor(null, null);
                });
    }

    // ------------------------------------------------------------------
    // Consulta
    // ------------------------------------------------------------------

    public record Filtro(String autor, String objetivo, TipoAuditoria tipo, Instant desde, Instant hasta) {}

    @Transactional(readOnly = true)
    public Page<AuditoriaSeguridadEntity> buscar(Filtro f, int pagina, int tamanio) {
        return repo.buscar(
                minusculas(f.autor()),
                minusculas(f.objetivo()),
                f.tipo(),
                f.desde() != null ? f.desde() : Instant.EPOCH,
                f.hasta() != null ? f.hasta() : Instant.parse("9999-12-31T23:59:59Z"),
                PageRequest.of(Math.max(0, pagina), Math.min(Math.max(1, tamanio), 200)));
    }

    private static String minusculas(String s) {
        return s == null || s.isBlank() ? null : s.trim().toLowerCase();
    }

    public record Verificacion(boolean integra, long verificados, Long primerIdRoto) {}

    /**
     * Recorre la cadena desde el primer registro. Detecta una fila alterada (su hash ya no coincide
     * con su contenido) y una fila borrada o insertada por fuera (el {@code hash_anterior} de la
     * siguiente ya no apunta al hash de la anterior).
     *
     * <p>Es evidencia de alteración, no prueba criptográfica: quien tenga acceso total a la base
     * podría recalcular la cadena entera. Para eso habría que anclar el último hash fuera de ella.
     */
    @Transactional(readOnly = true)
    public Verificacion verificar() {
        String anterior = GENESIS;
        long verificados = 0;
        for (AuditoriaSeguridadEntity r : repo.findAllByOrderByIdAsc()) {
            String esperado = Hashes.sha256(r.getHashAnterior() + contenidoCanonico(r.getOcurridoEn(),
                    r.getAutorId(), r.getAutorUsername(), r.getObjetivoTipo(), r.getObjetivoRef(), r.getTipo(),
                    r.getDetalle()));
            if (!r.getHashAnterior().equals(anterior) || !esperado.equals(r.getHash())) {
                return new Verificacion(false, verificados, r.getId());
            }
            anterior = r.getHash();
            verificados++;
        }
        return new Verificacion(true, verificados, null);
    }
}
