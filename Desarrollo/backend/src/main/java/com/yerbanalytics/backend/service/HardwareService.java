package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.dto.Dispositivo;
import com.yerbanalytics.backend.dto.HardwareData;
import com.yerbanalytics.backend.dto.SectorIncompleto;
import com.yerbanalytics.backend.model.DispositivoEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.repository.DispositivoRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
import com.yerbanalytics.backend.repository.ZonaRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Estado técnico y registro de la flota de hardware (HU-18 / HU-21). El estado operativo
 * se deriva en cada lectura (watchdog + batería + falla) para no almacenar un valor que
 * quede stale, igual que el {@code offline} del sector. La telemetría MQTT alimenta el
 * heartbeat de los nodos testigo vía {@link #actualizarHeartbeat}.
 */
@Service
public class HardwareService {

    public static final String NODO_TESTIGO = "nodo_testigo";
    public static final String ELECTROVALVULA = "electrovalvula";
    public static final String BOMBA = "bomba_peristaltica";
    public static final String MEDIASOMBRA = "mediasombra";

    /** Actuadores que un sector debe tener para considerarse completo (HU-18 CA-04). */
    private static final List<String> ACTUADORES_REQUERIDOS = List.of(ELECTROVALVULA, BOMBA, MEDIASOMBRA);

    private static final Map<String, String> TIPO_LABEL = Map.of(
            NODO_TESTIGO, "Nodo sensor testigo",
            ELECTROVALVULA, "Electroválvula",
            BOMBA, "Bomba peristáltica",
            MEDIASOMBRA, "Mediasombra"
    );

    private static final Pattern ID_PATTERN = Pattern.compile("DEV-(\\d+)");

    // Estados derivados y su presentación.
    private static final String OPERATIVO = "operativo";
    private static final String INTERMITENTE = "intermitente";
    private static final String FUERA_DE_SERVICIO = "fuera_de_servicio";

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

    private final DispositivoRepository dispositivoRepository;
    private final ZonaRepository zonaRepository;
    private final SectorRepository sectorRepository;
    private final int bateriaMinPct;
    private final long watchdogIntermitenteMs;
    private final long watchdogCriticoMs;

    public HardwareService(DispositivoRepository dispositivoRepository,
                           ZonaRepository zonaRepository,
                           SectorRepository sectorRepository,
                           @Value("${yerbanalytics.hardware.bateria-min-pct:20}") int bateriaMinPct,
                           @Value("${yerbanalytics.hardware.watchdog-intermitente-ms:60000}") long watchdogIntermitenteMs,
                           @Value("${yerbanalytics.hardware.watchdog-critico-ms:120000}") long watchdogCriticoMs) {
        this.dispositivoRepository = dispositivoRepository;
        this.zonaRepository = zonaRepository;
        this.sectorRepository = sectorRepository;
        this.bateriaMinPct = bateriaMinPct;
        this.watchdogIntermitenteMs = watchdogIntermitenteMs;
        this.watchdogCriticoMs = watchdogCriticoMs;
    }

    // ------------------------------------------------------------------
    // Lectura
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public HardwareData getHardware() {
        long now = System.currentTimeMillis();
        Map<String, String> zonaNames = new LinkedHashMap<>();
        for (ZonaEntity z : zonaRepository.findAll()) {
            zonaNames.put(z.getId(), z.getName());
        }

        List<DispositivoEntity> entities = dispositivoRepository.findAll();
        entities.sort((a, b) -> a.getId().compareTo(b.getId()));

        List<Dispositivo> dtos = new ArrayList<>();
        int operativos = 0, bateriaBaja = 0, fueraDeServicio = 0, averiados = 0;
        for (DispositivoEntity e : entities) {
            Dispositivo d = toDto(e, now);
            dtos.add(d);
            if (OPERATIVO.equals(d.estado())) operativos++;
            if (FUERA_DE_SERVICIO.equals(d.estado())) fueraDeServicio++;
            if (d.bateriaBaja()) bateriaBaja++;
            if (d.falla() != null) averiados++;
        }

        List<SectorIncompleto> incompletos = sectoresIncompletos(entities, zonaNames);

        return new HardwareData(dtos, dtos.size(), operativos, bateriaBaja, fueraDeServicio, averiados, incompletos);
    }

    private Dispositivo toDto(DispositivoEntity e, long now) {
        boolean esNodo = NODO_TESTIGO.equals(e.getTipo());
        String estado = deriveEstado(e, now, esNodo);
        boolean bateriaBaja = e.getBateria() != null && e.getBateria() < bateriaMinPct;
        String ubicacion = e.getSectorId() != null ? e.getSectorId() : e.getZonaId();
        String luLabel = e.getUltimoUpdate() != null
                ? formatAgo(e.getUltimoUpdate())
                : (esNodo ? "Sin reportes" : "—");
        String[] color = ESTADO_COLOR.get(estado);

        return new Dispositivo(
                e.getId(),
                e.getSerial(),
                e.getTipo(),
                TIPO_LABEL.getOrDefault(e.getTipo(), e.getTipo()),
                e.getZonaId(),
                e.getSectorId(),
                ubicacion,
                e.getBateria(),
                e.getSenal(),
                e.getUltimoUpdate(),
                luLabel,
                estado,
                ESTADO_LABEL.get(estado),
                color[0],
                color[1],
                bateriaBaja,
                blankToNull(e.getFalla())
        );
    }

    /** Estado operativo derivado: la falla manda; el nodo testigo usa el watchdog. */
    private String deriveEstado(DispositivoEntity e, long now, boolean esNodo) {
        if (blankToNull(e.getFalla()) != null) {
            return FUERA_DE_SERVICIO;
        }
        if (esNodo) {
            Long lu = e.getUltimoUpdate();
            if (lu == null || now - lu > watchdogCriticoMs) {
                return FUERA_DE_SERVICIO;
            }
            if (now - lu > watchdogIntermitenteMs) {
                return INTERMITENTE;
            }
            return OPERATIVO;
        }
        // Actuadores: alimentados por relé/PSU, sin heartbeat propio en el MVP.
        return OPERATIVO;
    }

    private List<SectorIncompleto> sectoresIncompletos(List<DispositivoEntity> entities, Map<String, String> zonaNames) {
        // tipos de actuador presentes por sector (sólo sectores con al menos un dispositivo).
        Map<String, List<String>> tiposPorSector = new LinkedHashMap<>();
        for (DispositivoEntity e : entities) {
            if (e.getSectorId() == null) continue;
            tiposPorSector.computeIfAbsent(e.getSectorId(), k -> new ArrayList<>()).add(e.getTipo());
        }
        List<SectorIncompleto> out = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : tiposPorSector.entrySet()) {
            List<String> faltantes = new ArrayList<>();
            for (String req : ACTUADORES_REQUERIDOS) {
                if (!entry.getValue().contains(req)) {
                    faltantes.add(TIPO_LABEL.get(req));
                }
            }
            if (!faltantes.isEmpty()) {
                String sectorId = entry.getKey();
                out.add(new SectorIncompleto(sectorId, zonaNames.getOrDefault(zonaIdDe(sectorId), zonaIdDe(sectorId)), faltantes));
            }
        }
        out.sort((a, b) -> a.sectorId().compareTo(b.sectorId()));
        return out;
    }

    // ------------------------------------------------------------------
    // Alta y recambio (HU-18 CA-02/03 · HU-21 CA-05)
    // ------------------------------------------------------------------

    @Transactional
    public HardwareData registrarDispositivo(Dispositivo dto) {
        String serial = trim(dto.serial());
        if (serial == null) {
            throw new HardwareInvalidoException("El serial/MAC es obligatorio.");
        }
        String tipo = dto.tipo();
        if (tipo == null || !TIPO_LABEL.containsKey(tipo)) {
            throw new HardwareInvalidoException("Tipo de dispositivo inválido.");
        }
        if (dispositivoRepository.findBySerial(serial).isPresent()) {
            throw new HardwareConflictoException("Ya existe un dispositivo con el serial/MAC «" + serial + "».");
        }

        String zonaId = null;
        String sectorId = null;
        if (NODO_TESTIGO.equals(tipo)) {
            zonaId = trim(dto.zonaId());
            if (zonaId == null) {
                throw new HardwareInvalidoException("El nodo testigo debe asociarse a una macro-zona.");
            }
            if (zonaRepository.findById(zonaId).isEmpty()) {
                throw new HardwareInvalidoException("La macro-zona «" + zonaId + "» no existe.");
            }
            if (!dispositivoRepository.findByZonaIdAndTipo(zonaId, NODO_TESTIGO).isEmpty()) {
                throw new HardwareConflictoException("La macro-zona «" + zonaId + "» ya tiene un nodo testigo.");
            }
        } else {
            sectorId = trim(dto.sectorId());
            if (sectorId == null) {
                throw new HardwareInvalidoException("El actuador debe asociarse a un sector.");
            }
            if (sectorRepository.findById(sectorId).isEmpty()) {
                throw new HardwareInvalidoException("El sector «" + sectorId + "» no existe.");
            }
            if (!dispositivoRepository.findBySectorIdAndTipo(sectorId, tipo).isEmpty()) {
                throw new HardwareConflictoException(
                        "El sector «" + sectorId + "» ya tiene asignada una " + TIPO_LABEL.get(tipo) + ".");
            }
        }

        DispositivoEntity e = new DispositivoEntity();
        e.setId(nextId());
        e.setSerial(serial);
        e.setTipo(tipo);
        e.setZonaId(zonaId);
        e.setSectorId(sectorId);
        dispositivoRepository.save(e);
        return getHardware();
    }

    @Transactional
    public HardwareData recambiarDispositivo(String id, Dispositivo dto) {
        DispositivoEntity e = dispositivoRepository.findById(id)
                .orElseThrow(() -> new HardwareInvalidoException("El dispositivo «" + id + "» no existe."));
        String nuevoSerial = trim(dto.serial());
        if (nuevoSerial == null) {
            throw new HardwareInvalidoException("El serial/MAC de la pieza nueva es obligatorio.");
        }
        if (!nuevoSerial.equals(e.getSerial())
                && dispositivoRepository.findBySerial(nuevoSerial).isPresent()) {
            throw new HardwareConflictoException("Ya existe un dispositivo con el serial/MAC «" + nuevoSerial + "».");
        }
        // Reutiliza el registro: pieza nueva, avería limpia, heartbeat reiniciado (HU-21 CA-05).
        e.setSerial(nuevoSerial);
        e.setFalla(null);
        e.setBateria(null);
        e.setSenal(null);
        e.setUltimoUpdate(null);
        dispositivoRepository.save(e);
        return getHardware();
    }

    // ------------------------------------------------------------------
    // Heartbeat por telemetría (HU-21 CA-01)
    // ------------------------------------------------------------------

    /**
     * Actualiza batería/señal/último update del dispositivo registrado cuyo serial/MAC
     * coincide con el de la telemetría. Si ningún dispositivo tiene ese serial/MAC, no hace
     * nada: el equipo emite pero el sistema aún no lo reconoce (los sectores de la zona sí se
     * actualizan por separado, en {@code NurseryService.updateTelemetry}). Es lo que ata un
     * sensor simulado a un nodo registrado con el mismo serial/MAC.
     */
    @Transactional
    public void actualizarHeartbeat(String zonaId, String mac, Integer bateria, Integer senal, Long ts) {
        if (mac == null || mac.isBlank()) {
            return;
        }
        Optional<DispositivoEntity> encontrado = dispositivoRepository.findBySerial(mac.trim());
        if (encontrado.isEmpty()) {
            return;
        }
        DispositivoEntity nodo = encontrado.get();
        if (bateria != null) nodo.setBateria(bateria);
        if (senal != null) nodo.setSenal(senal);
        nodo.setUltimoUpdate(ts != null ? ts : System.currentTimeMillis());
        dispositivoRepository.save(nodo);
    }

    // ------------------------------------------------------------------
    // Internos
    // ------------------------------------------------------------------

    private String nextId() {
        int max = 0;
        for (DispositivoEntity e : dispositivoRepository.findAll()) {
            Matcher m = ID_PATTERN.matcher(e.getId());
            if (m.matches()) {
                max = Math.max(max, Integer.parseInt(m.group(1)));
            }
        }
        return String.format(Locale.US, "DEV-%03d", max + 1);
    }

    private static String zonaIdDe(String sectorId) {
        int last = sectorId.lastIndexOf('-');
        return last > 0 ? sectorId.substring(0, last) : sectorId;
    }

    private static String trim(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    private static String blankToNull(String v) {
        return (v == null || v.isBlank()) ? null : v;
    }

    private static String formatAgo(long timestamp) {
        long diffMs = Math.max(0, System.currentTimeMillis() - timestamp);
        long diffSec = diffMs / 1000;
        if (diffSec < 60) return "hace " + diffSec + " s";
        long diffMin = diffSec / 60;
        if (diffMin < 60) return "hace " + diffMin + " min";
        long diffH = diffMin / 60;
        if (diffH < 24) return "hace " + diffH + " h";
        return "hace " + (diffH / 24) + " d";
    }
}
