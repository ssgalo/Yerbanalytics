package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.engine.DetalleAlerta;
import com.yerbanalytics.backend.engine.NivelAlerta;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.overrides.DemoControllable;
import com.yerbanalytics.backend.engine.overrides.RuleOverrides;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.engine.parametros.ConsumidorParametros;
import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import com.yerbanalytics.backend.engine.parametros.ParametrosRiego;
import com.yerbanalytics.backend.engine.rules.HydraulicFaultRule;
import com.yerbanalytics.backend.engine.rules.ShadeFaultRule;
import com.yerbanalytics.backend.exception.HardwareConflictException;
import com.yerbanalytics.backend.exception.InvalidHardwareException;
import com.yerbanalytics.backend.model.DispositivoEntity;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.mqtt.NodeError;
import com.yerbanalytics.backend.mqtt.NodeErrorType;
import com.yerbanalytics.backend.repository.DispositivoRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
import com.yerbanalytics.backend.repository.ZonaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fallas de los dispositivos del vivero (S-05, {@code reglas_v2} §4): las marca a partir de lo que informa el nodo y
 * las levanta cuando alguien registra la reparación. Es el único lugar que escribe {@code dispositivo.falla}.
 *
 * <ul>
 *   <li><b>{@link #report}:</b> el error de caudal marca la electroválvula del sector; el de la malla, la
 *       mediasombra de <b>todos</b> los sectores de la macro-zona (la v2 la trata por zona; el hardware la registra por
 *       sector). Deja UNA alerta {@code CRITICAL} con el texto de la v2. Lo que el motor hace con la marca es de
 *       {@link HydraulicFaultRule} y {@link ShadeFaultRule}.</li>
 *   <li><b>{@link #repair}:</b> limpia la marca, asienta quién y qué se reparó en el historial y avisa con una alerta
 *       {@code INFO}. S-06 reutilizará este mismo camino para sus revisiones.</li>
 * </ul>
 *
 * <p><b>Un mismo hecho por dos caminos:</b> la falla de la malla llega por el tópico de errores y por el ack
 * {@code falla_mecanica} del comando (ver {@code SeguimientoComandosService}). Mientras la marca siga puesta con el
 * mismo tipo el segundo aviso es {@link Outcome#DUPLICATE} y no alerta otra vez.
 *
 * <p>Es controlable desde el simulador: apagado no marca ni alerta (el sector no se marca averiado).
 */
@Service
public class HardwareFaultService implements ConsumidorParametros, DemoControllable {

    private static final Logger log = LoggerFactory.getLogger(HardwareFaultService.class);

    /** Nombre técnico: el de su consumo de parámetros y el de sus overrides. */
    public static final String NAME = "HardwareFaultService";

    /** No está en la cadena de reglas por sector: sólo ordena el listado del simulador (después de la lectura inválida). */
    private static final int PRIORITY = 92;

    /** Qué pasó con un error informado. */
    public enum Outcome {
        /** Se marcó la falla (o no había dispositivo registrado que marcar) y se dejó la alerta. */
        REGISTERED,
        /** Ya estaba marcada con el mismo tipo: no se alerta de nuevo. */
        DUPLICATE,
        /** No se hizo nada: servicio apagado, zona o sector que no existen o error mal formado. */
        IGNORED
    }

    private final DispositivoRepository dispositivoRepository;
    private final SectorRepository sectorRepository;
    private final ZonaRepository zonaRepository;
    private final HistorialService historialService;
    private final CatalogoParametrosService parametros;
    private final RuleOverrides overrides;
    private final Clock reloj;

    public HardwareFaultService(DispositivoRepository dispositivoRepository,
                                SectorRepository sectorRepository,
                                ZonaRepository zonaRepository,
                                HistorialService historialService,
                                // Perezoso: el catálogo se arma con la lista de consumidores, y esta clase es uno.
                                @Lazy CatalogoParametrosService parametros,
                                RuleOverrides overrides,
                                Clock relojVivero) {
        this.dispositivoRepository = dispositivoRepository;
        this.sectorRepository = sectorRepository;
        this.zonaRepository = zonaRepository;
        this.historialService = historialService;
        this.parametros = parametros;
        this.overrides = overrides;
        this.reloj = relojVivero;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String label() {
        return "📟 Errores del nodo (S-05)";
    }

    @Override
    public RuleBranch branch() {
        return RuleBranch.GLOBAL;
    }

    @Override
    public int priority() {
        return PRIORITY;
    }

    /** El umbral de riego: con el sustrato por debajo, la alerta de falla hidráulica pide regar a mano. */
    @Override
    public List<DefinicionParametro> parametros() {
        return List.of(ParametrosRiego.UMBRAL_HUMEDAD);
    }

    // ------------------------------------------------------------------
    // El nodo informa una falla
    // ------------------------------------------------------------------

    /** Marca la falla que informa el nodo y deja la alerta. */
    @Transactional
    public Outcome report(NodeError error) {
        if (!overrides.isEnabled(NAME)) {
            log.info("Zona {}: error {} ignorado, el receptor de errores está apagado.", error.zoneId(), error.type());
            return Outcome.IGNORED;
        }
        Optional<ZonaEntity> zona = zonaRepository.findById(error.zoneId());
        if (zona.isEmpty()) {
            log.warn("Error {} de una zona que no existe ({}), se descarta.", error.type(), error.zoneId());
            return Outcome.IGNORED;
        }
        List<String> zoneSectorIds = sectorRepository.findByZonaId(error.zoneId()).stream().map(SectorEntity::getId).toList();

        List<DispositivoEntity> targets;
        if (error.type().isHydraulic()) {
            if (isBlank(error.sectorId()) || !zoneSectorIds.contains(error.sectorId())) {
                log.warn("Zona {}: error {} de un sector que no es de la zona ({}), se descarta.",
                        error.zoneId(), error.type(), error.sectorId());
                return Outcome.IGNORED;
            }
            targets = dispositivoRepository.findBySectorIdAndTipo(error.sectorId(), HardwareService.ELECTROVALVULA);
        } else {
            targets = zoneSectorIds.isEmpty() ? List.of()
                    : dispositivoRepository.findBySectorIdInAndTipoIn(zoneSectorIds, List.of(HardwareService.MEDIASOMBRA));
        }

        String code = error.type().wireValue();
        if (targets.stream().anyMatch(d -> code.equals(d.getFalla()))) {
            log.debug("Zona {}: la falla {} ya estaba marcada, no se alerta de nuevo.", error.zoneId(), code);
            return Outcome.DUPLICATE;
        }
        if (targets.isEmpty()) {
            log.warn("Zona {}: error {} sin dispositivo registrado que marcar (sector {}).",
                    error.zoneId(), code, error.sectorId());
        } else {
            long now = reloj.millis();
            targets.forEach(d -> {
                d.setFalla(code);
                d.setFallaDesde(now);
            });
            dispositivoRepository.saveAll(targets);
        }
        alert(zona.get(), error.type().isHydraulic() ? HydraulicFaultRule.NAME : ShadeFaultRule.NAME,
                NivelAlerta.CRITICAL, alertText(zona.get(), error));
        return Outcome.REGISTERED;
    }

    private String alertText(ZonaEntity zona, NodeError error) {
        NodeErrorType type = error.type();
        if (type.isHydraulic()) {
            String text = "Sector " + error.sectorId() + ": falla hidráulica (" + type.description()
                    + (isBlank(error.value()) ? "" : ", " + error.value() + " L/h") + "). Su electroválvula quedó marcada como "
                    + "averiada: el riego y la dosificación autónomos del sector están suspendidos hasta registrar la reparación.";
            Double humidity = zona.getHumSusRaw();
            double threshold = parametros.vigentes().numero(ParametrosRiego.UMBRAL_HUMEDAD);
            if (humidity != null && humidity < threshold) {
                text += " La humedad de sustrato de la macro-zona es " + Math.round(humidity) + " % (umbral de riego "
                        + Math.round(threshold) + " %): hacer el riego manual del sector.";
            }
            return text;
        }
        String text = "Macro-zona " + zona.getId() + ": falla mecánica en la mediasombra (" + type.description() + "). "
                + "La malla no recibirá comandos nuevos hasta registrar la reparación.";
        if ("CERRADA".equals(error.value())) {
            text += " No llegó a cerrar: la malla puede haber quedado abierta, cubrir a mano.";
        } else if ("ABIERTA".equals(error.value())) {
            text += " No llegó a abrir: la malla puede haber quedado cerrada, revisarla.";
        } else {
            text += " Revisar el motor y los finales de carrera.";
        }
        return text;
    }

    // ------------------------------------------------------------------
    // Alguien registra la reparación
    // ------------------------------------------------------------------

    /**
     * Levanta la falla del dispositivo: lo devuelve a lo autónomo en el siguiente pulso. Reparar la mediasombra levanta la
     * marca de la malla de toda la macro-zona.
     *
     * @throws InvalidHardwareException  si falta el detalle o el dispositivo no existe (400)
     * @throws HardwareConflictException si el dispositivo no tiene una falla registrada (409)
     */
    @Transactional
    public void repair(String deviceId, String detail, String author) {
        String cleanDetail = detail == null ? "" : detail.trim();
        if (cleanDetail.isEmpty()) {
            throw new InvalidHardwareException("El detalle de la reparación es obligatorio.");
        }
        DispositivoEntity device = dispositivoRepository.findById(deviceId)
                .orElseThrow(() -> new InvalidHardwareException("El dispositivo «" + deviceId + "» no existe."));
        String code = device.getFalla();
        if (isBlank(code)) {
            throw new HardwareConflictException("El dispositivo «" + deviceId + "» no tiene una falla registrada.");
        }

        SectorEntity sector = sectorRepository.findById(device.getSectorId()).orElse(null);
        ZonaEntity zona = sector != null ? sector.getZona() : null;
        boolean isShade = HardwareService.MEDIASOMBRA.equals(device.getTipo());

        List<DispositivoEntity> repaired = isShade && zona != null ? shadesWithFault(zona.getId()) : List.of(device);
        repaired.forEach(d -> {
            d.setFalla(null);
            d.setFallaDesde(null);
        });
        dispositivoRepository.saveAll(repaired.isEmpty() ? List.of(device) : repaired);

        String zonaId = zona != null ? zona.getId() : "—";
        String zonaName = zona != null ? zona.getName() : "Sistema";
        String sectorId = isShade || sector == null ? "—" : sector.getId();
        String label = (isShade ? "Mediasombra" : "Electroválvula") + " " + device.getId();
        historialService.registrarReparacion(author, label, sectorId, zonaId, zonaName, code, cleanDetail);
        if (zona != null) {
            alert(zona, isShade ? ShadeFaultRule.NAME : HydraulicFaultRule.NAME, NivelAlerta.INFO,
                    (isShade ? "Macro-zona " + zona.getId() : "Sector " + sectorId) + ": " + author
                            + " registró la reparación (" + NodeErrorType.describe(code) + "). "
                            + "Vuelve a lo autónomo en el siguiente pulso.");
        }
    }

    private List<DispositivoEntity> shadesWithFault(String zoneId) {
        Set<String> sectorIds = sectorRepository.findByZonaId(zoneId).stream()
                .map(SectorEntity::getId).collect(Collectors.toSet());
        return dispositivoRepository.findBySectorIdInAndTipoIn(sectorIds, List.of(HardwareService.MEDIASOMBRA)).stream()
                .filter(d -> !isBlank(d.getFalla())).toList();
    }

    private void alert(ZonaEntity zona, String rule, NivelAlerta level, String text) {
        try {
            historialService.registrarAlerta(zona.getId(), zona.getName(), rule, new DetalleAlerta(level, text), reloj.millis());
        } catch (RuntimeException e) {
            log.error("Zona {}: no se pudo registrar la alerta {} — {}", zona.getId(), rule, text, e);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
