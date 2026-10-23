package com.yerbanalytics.backend.mediasombra;

import com.yerbanalytics.backend.config.ZonaHorariaVivero;
import com.yerbanalytics.backend.engine.MallaZona;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.overrides.DemoControllable;
import com.yerbanalytics.backend.engine.overrides.RuleOverrides;
import com.yerbanalytics.backend.model.EtapaPlanRustificacion;
import com.yerbanalytics.backend.model.ManualLockEntity;
import com.yerbanalytics.backend.model.PlanRustificacionEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.repository.ManualLockRepository;
import com.yerbanalytics.backend.repository.PlanRustificacionRepository;
import com.yerbanalytics.backend.repository.ZonaRepository;
import com.yerbanalytics.backend.service.ConfiguracionService;
import com.yerbanalytics.backend.service.EscalamientoNodoService;
import com.yerbanalytics.backend.service.HardwareService;
import com.yerbanalytics.backend.service.HistorialService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Abre y cierra la mediasombra de cada macro-zona según su plan de rustificación o, sin plan en curso, según el horario
 * por defecto de la configuración (add-rustificacion-por-macrozona). <b>No es parte del motor de reglas</b>: no depende
 * del pulso de sensado ni de lo que se mide, sólo del reloj del vivero.
 *
 * <p><b>Actúa sólo en los bordes.</b> Cada revisión calcula la posición programada de cada zona (abierta dentro de la
 * ventana de hoy, cerrada fuera) y ordena sólo cuando cambia respecto de la revisión anterior: al abrir la ventana, al
 * cerrarla, al iniciar o eliminar un plan, al cambiar el horario por defecto o de etapa. Entre bordes no corrige nada,
 * así que el motor puede mover la malla independientemente del plan (p. ej. cerrarla a las 12 por un pico de UV en una
 * ventana 10–15) y queda así hasta el próximo borde. Al arrancar el backend, la primera revisión pone cada zona en su
 * posición programada si la última orden registrada no coincide.
 *
 * <p>No manda nada si la zona tiene un bloqueo manual (S-01), no tiene mediasombra registrada (S-04), la tiene averiada
 * (S-05) o está en estado seguro (nodo testigo fuera de servicio): lo deja en el historial. Pasado el impedimento, la
 * malla espera al próximo borde.
 *
 * <p>Es {@link DemoControllable}: el simulador puede apagarlo.
 */
@Service
public class HorarioMediasombraService implements DemoControllable {

    private static final Logger log = LoggerFactory.getLogger(HorarioMediasombraService.class);

    public static final String NAME = "HorarioMediasombraService";
    /** No está en la cadena de reglas: sólo ordena el listado del simulador. */
    private static final int PRIORITY = 93;

    private final ZonaRepository zonas;
    private final PlanRustificacionRepository planes;
    private final ManualLockRepository bloqueos;
    private final ConfiguracionService configuracion;
    private final HardwareService hardware;
    private final MediasombraService mediasombra;
    private final HistorialService historial;
    private final RuleOverrides overrides;
    private final Clock reloj;

    /** Posición programada en la revisión anterior, por zona. En memoria: tras un reinicio la primera revisión la arma. */
    private final Map<String, Boolean> programada = new ConcurrentHashMap<>();

    public HorarioMediasombraService(ZonaRepository zonas, PlanRustificacionRepository planes,
                                     ManualLockRepository bloqueos, ConfiguracionService configuracion,
                                     HardwareService hardware, MediasombraService mediasombra,
                                     HistorialService historial, RuleOverrides overrides, Clock relojVivero) {
        this.zonas = zonas;
        this.planes = planes;
        this.bloqueos = bloqueos;
        this.configuracion = configuracion;
        this.hardware = hardware;
        this.mediasombra = mediasombra;
        this.historial = historial;
        this.overrides = overrides;
        this.reloj = relojVivero;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String label() {
        return "🕗 Horario de la mediasombra (plan de rustificación u horario por defecto)";
    }

    @Override
    public RuleBranch branch() {
        return RuleBranch.MEDIASOMBRA;
    }

    @Override
    public int priority() {
        return PRIORITY;
    }

    /** Lo que rige hoy en una zona: la ventana y de dónde sale. */
    record Programa(VentanaDiaria ventana, String origen) {
    }

    /** Revisión periódica. */
    @Scheduled(initialDelayString = "${yerbanalytics.mediasombra.horario-demora-inicial-ms:15000}",
            fixedDelayString = "${yerbanalytics.mediasombra.horario-revision-ms:60000}")
    public void revisarPeriodicamente() {
        try {
            revisar();
        } catch (RuntimeException e) {
            log.error("Horario de la mediasombra: la revisión falló; se reintenta en la próxima.", e);
        }
    }

    /**
     * Revisa apenas termine la transacción en curso (o ya, si no hay una): lo llaman el alta y la baja de un plan, para
     * que la malla no espere hasta un minuto.
     */
    public void revisarAlConfirmar() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    revisarPeriodicamente();
                }
            });
        } else {
            revisarPeriodicamente();
        }
    }

    /** Una revisión: para cada zona, la posición programada; si cambió, la ordena. */
    public synchronized void revisar() {
        if (!overrides.isEnabled(NAME)) {
            return;
        }
        ZonedDateTime ahora = ZonedDateTime.now(reloj.withZone(ZonaHorariaVivero.ZONA));
        LocalDate hoy = ahora.toLocalDate();
        LocalTime hora = ahora.toLocalTime();
        VentanaDiaria porDefecto = configuracion.horarioMediasombraPorDefecto();
        Map<String, PlanRustificacionEntity> vigentes = planes.findByCanceladoEnIsNull().stream()
                .collect(Collectors.toMap(PlanRustificacionEntity::getZonaId, p -> p, (a, b) -> a, HashMap::new));
        Set<String> zonasBloqueadas = bloqueos.findByActiveTrue().stream()
                .filter(b -> b.getSectorId() == null && b.getZonaId() != null)
                .map(ManualLockEntity::getZonaId)
                .collect(Collectors.toSet());

        for (ZonaEntity zona : zonas.findAll()) {
            Programa programa = programaDe(vigentes.get(zona.getId()), hoy, porDefecto);
            boolean abierta = programa.ventana().abiertaA(hora);
            Boolean anterior = programada.put(zona.getId(), abierta);
            if (anterior != null && anterior == abierta) {
                continue;   // sin borde: la última orden (del horario o del motor) sigue en pie
            }
            boolean arranque = anterior == null;
            if (arranque && Boolean.valueOf(abierta).equals(zona.getMediasombraAbierta())) {
                continue;   // al arrancar, ya está donde corresponde
            }
            String motivo = programa.origen() + ": mediasombra " + MediasombraService.nombre(abierta)
                    + " (ventana " + programa.ventana().texto() + ").";
            String impedimento = impedimento(zona, zonasBloqueadas.contains(zona.getId()));
            if (impedimento != null) {
                log.info("Zona {}: el horario pide la mediasombra {}, pero no se manda: {}.", zona.getId(),
                        MediasombraService.nombre(abierta), impedimento);
                if (!arranque) {
                    historial.registrarMediasombra(zona.getId(), zona.getName(), NAME, motivo,
                            "No se mueve la mediasombra: " + impedimento + ".",
                            "Sin orden al nodo; la malla espera al próximo borde de la ventana.", "Omitida",
                            reloj.millis());
                }
                continue;
            }
            mediasombra.mover(zona, abierta, NAME, motivo);
        }
    }

    /** La ventana de hoy en la zona: la de la etapa del plan en curso o, si no hay, la del horario por defecto. */
    static Programa programaDe(PlanRustificacionEntity plan, LocalDate hoy, VentanaDiaria porDefecto) {
        if (plan != null) {
            Optional<EtapaPlanRustificacion> etapa = PlanRustificacionService.etapaDe(plan, hoy);
            Optional<VentanaDiaria> ventana = PlanRustificacionService.ventanaDe(plan, hoy);
            if (etapa.isPresent() && ventana.isPresent()) {
                return new Programa(ventana.get(), "Plan de rustificación, etapa " + etapa.get().getOrden());
            }
        }
        return new Programa(porDefecto, "Horario por defecto");
    }

    /** Por qué no se le puede mandar la orden a la malla de la zona, o {@code null} si se puede. */
    private String impedimento(ZonaEntity zona, boolean bloqueada) {
        MallaZona malla = hardware.mallaDe(zona.getId());
        String motivo = malla.motivoSinComando(bloqueada);
        if (motivo != null) {
            return motivo;
        }
        if (EscalamientoNodoService.fueraDeServicio(zona)) {
            return "la macro-zona está en estado seguro (nodo testigo fuera de servicio)";
        }
        return null;
    }
}
