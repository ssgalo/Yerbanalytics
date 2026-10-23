package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.dto.Dispositivo;
import com.yerbanalytics.backend.dto.HardwareData;
import com.yerbanalytics.backend.dto.SectorIncompleto;
import com.yerbanalytics.backend.engine.HardwareCoverage;
import com.yerbanalytics.backend.engine.HardwareFaults;
import com.yerbanalytics.backend.engine.MallaZona;
import com.yerbanalytics.backend.model.DispositivoEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.repository.DispositivoRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
import com.yerbanalytics.backend.repository.ZonaRepository;
import com.yerbanalytics.backend.exception.HardwareConflictException;
import com.yerbanalytics.backend.exception.InvalidHardwareException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Estado técnico y registro de la flota de hardware (HU-18 / HU-21). El estado operativo
 * se deriva en cada lectura (pulsos sin respuesta + batería + falla) para no almacenar un valor que
 * quede stale, igual que el {@code offline} del sector. La telemetría MQTT alimenta el
 * heartbeat de los nodos testigo vía {@link #actualizarHeartbeat}.
 */
@Service
public class HardwareService {

    public static final String NODO_TESTIGO = "nodo_testigo";
    public static final String ELECTROVALVULA = "electrovalvula";
    public static final String BOMBA = "bomba_peristaltica";
    public static final String MEDIASOMBRA = "mediasombra";

    /**
     * Actuadores que un sector debe tener para considerarse completo (HU-18 CA-04). La mediasombra no: es de la
     * macro-zona, como el nodo testigo.
     */
    private static final List<String> ACTUADORES_REQUERIDOS = List.of(ELECTROVALVULA, BOMBA);

    /** Equipos de la macro-zona (con {@code zonaId}, sin sector; a lo sumo uno de cada tipo por zona). */
    public static final Set<String> TIPOS_DE_ZONA = Set.of(NODO_TESTIGO, MEDIASOMBRA);

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
            OPERATIVO, new String[]{"#E3F2E8", "#1E6B41"},
            INTERMITENTE, new String[]{"#FBF0D9", "#7A5310"},
            FUERA_DE_SERVICIO, new String[]{"#FBE3DD", "#9A2B17"}
    );

    private final DispositivoRepository dispositivoRepository;
    private final ZonaRepository zonaRepository;
    private final SectorRepository sectorRepository;
    private final int bateriaMinPct;

    public HardwareService(DispositivoRepository dispositivoRepository,
                           ZonaRepository zonaRepository,
                           SectorRepository sectorRepository,
                           @Value("${yerbanalytics.hardware.bateria-min-pct:20}") int bateriaMinPct) {
        this.dispositivoRepository = dispositivoRepository;
        this.zonaRepository = zonaRepository;
        this.sectorRepository = sectorRepository;
        this.bateriaMinPct = bateriaMinPct;
    }

    // ------------------------------------------------------------------
    // Lectura
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public HardwareData getHardware() {
        Map<String, String> zonaNames = new LinkedHashMap<>();
        Map<String, Integer> pulsosSinRespuesta = new LinkedHashMap<>();
        for (ZonaEntity z : zonaRepository.findAll()) {
            zonaNames.put(z.getId(), z.getName());
            pulsosSinRespuesta.put(z.getId(), EscalamientoNodoService.cuenta(z));
        }

        List<DispositivoEntity> entities = dispositivoRepository.findAll();
        entities.sort((a, b) -> a.getId().compareTo(b.getId()));

        List<Dispositivo> dtos = new ArrayList<>();
        int operativos = 0, bateriaBaja = 0, fueraDeServicio = 0, averiados = 0;
        for (DispositivoEntity e : entities) {
            Dispositivo d = toDto(e, pulsosSinRespuesta);
            dtos.add(d);
            if (OPERATIVO.equals(d.estado())) operativos++;
            if (FUERA_DE_SERVICIO.equals(d.estado())) fueraDeServicio++;
            if (d.bateriaBaja()) bateriaBaja++;
            if (d.falla() != null) averiados++;
        }

        List<SectorIncompleto> incompletos = sectoresIncompletos(entities, zonaNames);

        return new HardwareData(dtos, dtos.size(), operativos, bateriaBaja, fueraDeServicio, averiados, incompletos);
    }

    /**
     * Qué tiene mapeado cada sector de la macro-zona (S-04), en DOS consultas: el nodo testigo de la zona y los
     * actuadores de todos sus sectores. Cada sector de {@code sectorIds} figura en el resultado, también los que no
     * tienen ningún dispositivo. Lo arma el motor una vez por zona y evaluación; las reglas sólo lo leen.
     */
    @Transactional(readOnly = true)
    public Map<String, HardwareCoverage> coverageOf(String zonaId, Collection<String> sectorIds) {
        if (sectorIds.isEmpty()) {
            return Map.of();
        }
        boolean zoneNode = !dispositivoRepository.findByZonaIdAndTipo(zonaId, NODO_TESTIGO).isEmpty();
        Map<String, Set<String>> typesBySector = new HashMap<>();
        for (DispositivoEntity d : dispositivoRepository.findBySectorIdInAndTipoIn(
                sectorIds, List.of(ELECTROVALVULA, BOMBA))) {
            typesBySector.computeIfAbsent(d.getSectorId(), k -> new HashSet<>()).add(d.getTipo());
        }
        Map<String, HardwareCoverage> out = new LinkedHashMap<>();
        for (String sectorId : sectorIds) {
            Set<String> types = typesBySector.getOrDefault(sectorId, Set.of());
            out.put(sectorId, new HardwareCoverage(types.contains(ELECTROVALVULA), types.contains(BOMBA), zoneNode));
        }
        return out;
    }

    /**
     * Qué actuadores de cada sector de la macro-zona están marcados como averiados (S-05), en UNA consulta: hoy, la
     * electroválvula. La bomba averiada no se mira: no hay forma de que el nodo la marque y el hardware admite una sola
     * por sector. La mediasombra es de la zona: ver {@link #mallaDe}. Cada sector de {@code sectorIds} figura en el
     * resultado. Lo arma el motor una vez por zona y evaluación; las reglas sólo lo leen.
     */
    @Transactional(readOnly = true)
    public Map<String, HardwareFaults> faultsOf(Collection<String> sectorIds) {
        if (sectorIds.isEmpty()) {
            return Map.of();
        }
        Map<String, String> hydraulicBySector = new HashMap<>();
        for (DispositivoEntity d : dispositivoRepository.findBySectorIdInAndTipoIn(sectorIds, List.of(ELECTROVALVULA))) {
            String falla = blankToNull(d.getFalla());
            if (falla != null && ELECTROVALVULA.equals(d.getTipo())) {
                hydraulicBySector.put(d.getSectorId(), falla);
            }
        }
        Map<String, HardwareFaults> out = new LinkedHashMap<>();
        for (String sectorId : sectorIds) {
            out.put(sectorId, new HardwareFaults(hydraulicBySector.get(sectorId)));
        }
        return out;
    }

    /** La mediasombra de la macro-zona: si está registrada (S-04) y si está averiada (S-05). */
    @Transactional(readOnly = true)
    public MallaZona mallaDe(String zonaId) {
        List<DispositivoEntity> mallas = dispositivoRepository.findByZonaIdAndTipo(zonaId, MEDIASOMBRA);
        String falla = mallas.stream().map(d -> blankToNull(d.getFalla())).filter(f -> f != null).findFirst().orElse(null);
        return new MallaZona(!mallas.isEmpty(), falla);
    }

    private Dispositivo toDto(DispositivoEntity e, Map<String, Integer> pulsosSinRespuesta) {
        boolean esNodo = NODO_TESTIGO.equals(e.getTipo());
        String estado = deriveEstado(e, esNodo, pulsosSinRespuesta.getOrDefault(e.getZonaId(), 0));
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
                blankToNull(e.getFalla()),
                blankToNull(e.getFalla()) != null ? e.getFallaDesde() : null
        );
    }

    /**
     * Estado operativo derivado: la falla manda. El nodo testigo sigue los pulsos de sensado consecutivos sin
     * respuesta de su macro-zona (0 → operativo, 1–2 → intermitente, 3 o más → fuera de servicio), no el tiempo
     * desde su último reporte: entre pulsos el nodo no reporta. Uno que nunca reportó está fuera de servicio.
     */
    private String deriveEstado(DispositivoEntity e, boolean esNodo, int pulsosSinRespuesta) {
        if (blankToNull(e.getFalla()) != null) {
            return FUERA_DE_SERVICIO;
        }
        if (esNodo) {
            if (e.getUltimoUpdate() == null || pulsosSinRespuesta >= EscalamientoNodoService.FUERA_DE_SERVICIO) {
                return FUERA_DE_SERVICIO;
            }
            return pulsosSinRespuesta > 0 ? INTERMITENTE : OPERATIVO;
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
            throw new InvalidHardwareException("El serial/MAC es obligatorio.");
        }
        String tipo = dto.tipo();
        if (tipo == null || !TIPO_LABEL.containsKey(tipo)) {
            throw new InvalidHardwareException("Tipo de dispositivo inválido.");
        }
        if (dispositivoRepository.findBySerial(serial).isPresent()) {
            throw new HardwareConflictException("Ya existe un dispositivo con el serial/MAC «" + serial + "».");
        }

        String zonaId = null;
        String sectorId = null;
        if (TIPOS_DE_ZONA.contains(tipo)) {
            boolean esNodo = NODO_TESTIGO.equals(tipo);
            zonaId = trim(dto.zonaId());
            if (zonaId == null) {
                throw new InvalidHardwareException((esNodo ? "El nodo testigo" : "La mediasombra")
                        + " debe asociarse a una macro-zona.");
            }
            if (zonaRepository.findById(zonaId).isEmpty()) {
                throw new InvalidHardwareException("La macro-zona «" + zonaId + "» no existe.");
            }
            if (!dispositivoRepository.findByZonaIdAndTipo(zonaId, tipo).isEmpty()) {
                throw new HardwareConflictException("La macro-zona «" + zonaId + "» ya tiene "
                        + (esNodo ? "un nodo testigo." : "una mediasombra."));
            }
        } else {
            sectorId = trim(dto.sectorId());
            if (sectorId == null) {
                throw new InvalidHardwareException("El actuador debe asociarse a un sector.");
            }
            if (sectorRepository.findById(sectorId).isEmpty()) {
                throw new InvalidHardwareException("El sector «" + sectorId + "» no existe.");
            }
            if (!dispositivoRepository.findBySectorIdAndTipo(sectorId, tipo).isEmpty()) {
                throw new HardwareConflictException(
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
                .orElseThrow(() -> new InvalidHardwareException("El dispositivo «" + id + "» no existe."));
        String nuevoSerial = trim(dto.serial());
        if (nuevoSerial == null) {
            throw new InvalidHardwareException("El serial/MAC de la pieza nueva es obligatorio.");
        }
        if (!nuevoSerial.equals(e.getSerial())
                && dispositivoRepository.findBySerial(nuevoSerial).isPresent()) {
            throw new HardwareConflictException("Ya existe un dispositivo con el serial/MAC «" + nuevoSerial + "».");
        }
        // Reutiliza el registro: pieza nueva, avería limpia, heartbeat reiniciado (HU-21 CA-05).
        e.setSerial(nuevoSerial);
        e.setFalla(null);
        e.setFallaDesde(null);
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
     * lectura entrante a un nodo registrado con el mismo serial/MAC.
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
