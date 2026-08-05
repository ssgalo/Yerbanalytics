package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.config.CapturaProperties;
import com.yerbanalytics.backend.dto.ConfigCaptura;
import com.yerbanalytics.backend.dto.DispositivoCamara;
import com.yerbanalytics.backend.model.DispositivoCamaraEntity;
import com.yerbanalytics.backend.repository.DispositivoCamaraRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro, credenciales y estado operativo de los dispositivos de captura.
 *
 * <p>El estado operativo NO se almacena: se deriva del silencio desde el último heartbeat,
 * exactamente como hace {@link HardwareService} con los nodos testigo. Guardar un estado
 * derivado lo dejaría stale en cuanto el dispositivo se cae sin avisar, que es justamente el
 * caso que interesa detectar.
 */
@Service
public class DispositivoCamaraService {

    public static final String OPERATIVO = "operativo";
    public static final String INTERMITENTE = "intermitente";
    public static final String FUERA_DE_SERVICIO = "fuera_de_servicio";

    private static final Map<String, String> ESTADO_LABEL = Map.of(
            OPERATIVO, "Operativo",
            INTERMITENTE, "Señal intermitente",
            FUERA_DE_SERVICIO, "Fuera de servicio"
    );
    private static final Map<String, String[]> ESTADO_COLOR = Map.of(
            OPERATIVO, new String[]{"#E7F1EA", "#2E7A4F"},
            INTERMITENTE, new String[]{"#FBF0DC", "#A66A12"},
            FUERA_DE_SERVICIO, new String[]{"#FBE6E0", "#A8331C"}
    );

    /**
     * Códigos de vinculación vivos. En memoria a propósito: son de un solo uso y viven diez
     * minutos, así que persistirlos sólo agregaría una tabla que habría que limpiar. Perderlos
     * en un reinicio es intrascendente — se genera otro.
     */
    private final Map<String, CodigoVinculacion> codigos = new ConcurrentHashMap<>();

    private record CodigoVinculacion(String codigo, long expiraEn) {}

    private final DispositivoCamaraRepository repo;
    private final TokenService tokenService;
    private final CapturaProperties props;

    public DispositivoCamaraService(DispositivoCamaraRepository repo,
                                    TokenService tokenService,
                                    CapturaProperties props) {
        this.repo = repo;
        this.tokenService = tokenService;
        this.props = props;
    }

    // ------------------------------------------------------------------
    // Vinculación y enrolamiento
    // ------------------------------------------------------------------

    /** Emite un código de un solo uso. Ruta de plataforma: NO es parte del contrato. */
    public CodigoEmitido generarCodigoVinculacion() {
        purgarCodigosVencidos();
        String codigo = tokenService.generarCodigoVinculacion();
        long expiraEn = System.currentTimeMillis() + props.getCodigoVidaSeg() * 1000L;
        codigos.put(codigo, new CodigoVinculacion(codigo, expiraEn));
        return new CodigoEmitido(codigo, expiraEn);
    }

    public record CodigoEmitido(String codigo, long expiraEn) {}

    public record Enrolado(String dispositivoId, String refreshToken) {}

    /**
     * Consume el código y registra el dispositivo. El código se invalida al usarse; un segundo
     * intento con el mismo código falla.
     */
    @Transactional
    public Enrolado enrolar(String codigo, String nombre, String plataforma) {
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException("El código de vinculación es obligatorio.");
        }
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre del dispositivo es obligatorio.");
        }

        String normalizado = codigo.trim().toUpperCase(Locale.ROOT);
        CodigoVinculacion cv = codigos.remove(normalizado);
        if (cv == null) {
            throw new CredencialInvalidaException("Código de vinculación inexistente o ya utilizado.");
        }
        if (cv.expiraEn() < System.currentTimeMillis()) {
            throw new CredencialInvalidaException("El código de vinculación venció. Generá uno nuevo.");
        }

        String refreshToken = tokenService.generarRefreshToken();
        DispositivoCamaraEntity e = new DispositivoCamaraEntity();
        e.setId(siguienteId());
        e.setNombre(nombre.trim());
        e.setPlataforma(plataforma != null ? plataforma.trim() : null);
        e.setRefreshTokenHash(tokenService.huella(refreshToken));
        e.setCapturasOk(0);
        e.setCapturasError(0);
        e.setCapturaListo(false);
        e.setCreadoEn(System.currentTimeMillis());
        e.setRevocado(false);
        repo.save(e);

        return new Enrolado(e.getId(), refreshToken);
    }

    /** Canjea la credencial de renovación por un token de acceso de vida corta. */
    @Transactional(readOnly = true)
    public String emitirAccessToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("La credencial de renovación es obligatoria.");
        }
        String huella = tokenService.huella(refreshToken);
        DispositivoCamaraEntity e = repo.findByRevocadoFalseOrderByCreadoEnAsc().stream()
                .filter(d -> d.getRefreshTokenHash().equals(huella))
                .findFirst()
                .orElseThrow(() -> new CredencialInvalidaException(
                        "Credencial de renovación inválida o revocada. Volvé a vincular el dispositivo."));
        return tokenService.emitir(e.getId());
    }

    /** Baja lógica: revoca la credencial sin perder el historial de capturas. */
    @Transactional
    public void revocar(String dispositivoId) {
        repo.findById(dispositivoId).ifPresent(d -> {
            d.setRevocado(true);
            repo.save(d);
        });
    }

    public boolean existeActivo(String dispositivoId) {
        return repo.findById(dispositivoId).map(d -> !Boolean.TRUE.equals(d.getRevocado())).orElse(false);
    }

    // ------------------------------------------------------------------
    // Operación
    // ------------------------------------------------------------------

    public ConfigCaptura config() {
        return new ConfigCaptura(
                props.getAnchoMax(),
                props.getAltoMax(),
                props.getCalidadJpeg(),
                props.getWarmupMs(),
                props.getHeartbeatSeg(),
                props.getTimeoutOrdenSeg(),
                props.getMaxColaOrdenes()
        );
    }

    @Transactional
    public void registrarHeartbeat(String dispositivoId, boolean capturaListo,
                                   Integer capturasOk, Integer capturasError) {
        repo.findById(dispositivoId).ifPresent(d -> {
            d.setUltimoHeartbeat(System.currentTimeMillis());
            d.setCapturaListo(capturaListo);
            if (capturasOk != null) d.setCapturasOk(capturasOk);
            if (capturasError != null) d.setCapturasError(capturasError);
            repo.save(d);
        });
    }

    /** Suma al contador del servidor, que es independiente del que reporta el cliente. */
    @Transactional
    public void contarCaptura(String dispositivoId, boolean exitosa) {
        repo.findById(dispositivoId).ifPresent(d -> {
            if (exitosa) {
                d.setCapturasOk(d.getCapturasOk() + 1);
            } else {
                d.setCapturasError(d.getCapturasError() + 1);
            }
            repo.save(d);
        });
    }

    @Transactional(readOnly = true)
    public List<DispositivoCamara> listar() {
        long ahora = System.currentTimeMillis();
        List<DispositivoCamara> out = new ArrayList<>();
        for (DispositivoCamaraEntity d : repo.findByRevocadoFalseOrderByCreadoEnAsc()) {
            String estado = derivarEstado(d.getUltimoHeartbeat(), ahora);
            String[] color = ESTADO_COLOR.get(estado);
            out.add(new DispositivoCamara(
                    d.getId(),
                    d.getNombre(),
                    d.getPlataforma(),
                    estado,
                    ESTADO_LABEL.get(estado),
                    color[0],
                    color[1],
                    d.getUltimoHeartbeat(),
                    ago(d.getUltimoHeartbeat(), ahora),
                    Boolean.TRUE.equals(d.getCapturaListo()),
                    d.getCapturasOk(),
                    d.getCapturasError()
            ));
        }
        return out;
    }

    /**
     * Estado por umbrales de silencio. Enterarse de que el dispositivo se cayó recién cuando
     * falla una captura es demasiado tarde: para entonces ya se perdió una pasada del riel.
     */
    private String derivarEstado(Long ultimoHeartbeat, long ahora) {
        if (ultimoHeartbeat == null) {
            return FUERA_DE_SERVICIO;
        }
        long silencio = ahora - ultimoHeartbeat;
        if (silencio >= props.getWatchdogCriticoMs()) {
            return FUERA_DE_SERVICIO;
        }
        if (silencio >= props.getWatchdogIntermitenteMs()) {
            return INTERMITENTE;
        }
        return OPERATIVO;
    }

    private static String ago(Long ts, long ahora) {
        if (ts == null) {
            return "nunca reportó";
        }
        long seg = Math.max(0, (ahora - ts) / 1000);
        if (seg < 60) return "hace " + seg + " s";
        long min = seg / 60;
        if (min < 60) return "hace " + min + " min";
        long h = min / 60;
        if (h < 24) return "hace " + h + " h";
        return "hace " + (h / 24) + " d";
    }

    private String siguienteId() {
        int n = repo.findAll().size() + 1;
        return String.format(Locale.US, "CAM-%03d", n);
    }

    private void purgarCodigosVencidos() {
        long ahora = System.currentTimeMillis();
        codigos.values().removeIf(c -> c.expiraEn() < ahora);
    }

    /** Código o credencial inválidos → 401. */
    public static class CredencialInvalidaException extends RuntimeException {
        public CredencialInvalidaException(String msg) {
            super(msg);
        }
    }

    public Optional<DispositivoCamaraEntity> buscar(String id) {
        return repo.findById(id);
    }
}
