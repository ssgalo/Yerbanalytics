package com.yerbanalytics.backend.mediasombra;

import com.yerbanalytics.backend.config.ZonaHorariaVivero;
import com.yerbanalytics.backend.dto.NuevoPlanRustificacion;
import com.yerbanalytics.backend.dto.PlanRustificacion;
import com.yerbanalytics.backend.exception.PlanRustificacionConflictoException;
import com.yerbanalytics.backend.exception.PlanRustificacionInvalidoException;
import com.yerbanalytics.backend.model.EtapaPlanRustificacion;
import com.yerbanalytics.backend.model.PlanRustificacionEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.repository.PlanRustificacionRepository;
import com.yerbanalytics.backend.repository.ZonaRepository;
import com.yerbanalytics.backend.service.HistorialService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Planes de rustificación por macro-zona (add-rustificacion-por-macrozona): se inician y eliminan desde el detalle de
 * la zona. Es la autoridad sobre su estado y sobre la ventana que rige cada día; quien abre y cierra la malla según esa
 * ventana es el {@link HorarioMediasombraService}, fuera del motor de reglas.
 *
 * <p><b>Estado</b>, calculado con la fecha del vivero: {@code programado} antes de la fecha de inicio (rige el horario
 * por defecto), {@code en_curso} desde ese día y <b>también pasada la última etapa</b> (sigue rigiendo la última hasta
 * que alguien elimina el plan), {@code cancelado} una vez eliminado.
 */
@Service
public class PlanRustificacionService {

    public static final String PROGRAMADO = "programado";
    public static final String EN_CURSO = "en_curso";
    public static final String CANCELADO = "cancelado";

    /** Tope de días de una etapa: un plan de rustificación dura semanas, no meses (el mismo que el dashboard). */
    public static final int MAX_DIAS_ETAPA = 60;
    /** Tope de etapas: más es un error de carga, no un plan. */
    public static final int MAX_ETAPAS = 20;

    private static final Pattern HORA_HH_MM = Pattern.compile("([01]\\d|2[0-3]):[0-5]\\d");

    private final PlanRustificacionRepository planes;
    private final ZonaRepository zonas;
    private final HistorialService historial;
    private final HorarioMediasombraService horario;
    private final Clock reloj;

    public PlanRustificacionService(PlanRustificacionRepository planes, ZonaRepository zonas,
                                    HistorialService historial, @Lazy HorarioMediasombraService horario,
                                    Clock relojVivero) {
        this.planes = planes;
        this.zonas = zonas;
        this.historial = historial;
        this.horario = horario;
        this.reloj = relojVivero;
    }

    // ------------------------------------------------------------------
    // API
    // ------------------------------------------------------------------

    /** El plan vigente de la zona, o vacío si no tiene (rige el horario por defecto). */
    @Transactional(readOnly = true)
    public Optional<PlanRustificacion> vigente(String zonaId) {
        zona(zonaId);
        return planes.findFirstByZonaIdAndCanceladoEnIsNull(zonaId).map(p -> toDto(p, hoy()));
    }

    /**
     * Inicia el plan de la zona. A lo sumo uno vigente por zona.
     *
     * @throws PlanRustificacionInvalidoException  si la zona no existe o el plan está mal armado (400)
     * @throws PlanRustificacionConflictoException si la zona ya tiene un plan vigente (409)
     */
    @Transactional
    public PlanRustificacion iniciar(String zonaId, NuevoPlanRustificacion nuevo, String usuario) {
        ZonaEntity zona = zona(zonaId);
        LocalDate hoy = hoy();
        LocalDate inicio = validar(nuevo, hoy);
        if (planes.findFirstByZonaIdAndCanceladoEnIsNull(zonaId).isPresent()) {
            throw new PlanRustificacionConflictoException("La macro-zona " + zonaId
                    + " ya tiene un plan de rustificación vigente. Eliminalo antes de iniciar otro.");
        }

        PlanRustificacionEntity plan = new PlanRustificacionEntity();
        plan.setId(UUID.randomUUID().toString());
        plan.setZonaId(zonaId);
        plan.setFechaInicio(inicio);
        List<EtapaPlanRustificacion> etapas = new ArrayList<>();
        int orden = 1;
        for (NuevoPlanRustificacion.Etapa e : nuevo.etapas()) {
            etapas.add(new EtapaPlanRustificacion(orden++, e.dias(), e.abiertaDesde(), e.abiertaHasta()));
        }
        plan.setEtapas(etapas);
        plan.setCreadoPor(usuario);
        plan.setCreadoEn(reloj.millis());
        planes.save(plan);

        historial.registrarPlanRustificacion(autor(usuario), zonaId, zona.getName(),
                "Plan de rustificación iniciado: " + etapas.size() + " etapa(s), " + totalDias(etapas)
                        + " días desde el " + inicio + ".",
                "La mediasombra de la macro-zona sigue el plan; al terminar la última etapa la sostiene hasta eliminarlo.");
        horario.revisarAlConfirmar();
        return toDto(plan, hoy);
    }

    /**
     * Elimina el plan vigente de la zona: vuelve a regir el horario por defecto.
     *
     * @throws PlanRustificacionConflictoException si la zona no tiene un plan vigente (409)
     */
    @Transactional
    public PlanRustificacion cancelar(String zonaId, String usuario) {
        ZonaEntity zona = zona(zonaId);
        PlanRustificacionEntity plan = planes.findFirstByZonaIdAndCanceladoEnIsNull(zonaId)
                .orElseThrow(() -> new PlanRustificacionConflictoException(
                        "La macro-zona " + zonaId + " no tiene un plan de rustificación vigente."));
        plan.setCanceladoEn(reloj.millis());
        planes.save(plan);

        historial.registrarPlanRustificacion(autor(usuario), zonaId, zona.getName(),
                "Plan de rustificación eliminado.",
                "La mediasombra de la macro-zona vuelve al horario por defecto.");
        horario.revisarAlConfirmar();
        return toDto(plan, hoy());
    }

    // ------------------------------------------------------------------
    // Lo que lee el horario de la mediasombra
    // ------------------------------------------------------------------

    /** Estado del plan en la fecha {@code hoy}. */
    public static String estado(PlanRustificacionEntity plan, LocalDate hoy) {
        if (plan.getCanceladoEn() != null) {
            return CANCELADO;
        }
        return hoy.isBefore(plan.getFechaInicio()) ? PROGRAMADO : EN_CURSO;
    }

    /**
     * La etapa que rige en la fecha {@code hoy}: la que corresponde por días o, pasada la última, la última. Vacío si el
     * plan todavía no empezó, se eliminó o no tiene etapas.
     */
    public static Optional<EtapaPlanRustificacion> etapaDe(PlanRustificacionEntity plan, LocalDate hoy) {
        if (!EN_CURSO.equals(estado(plan, hoy)) || plan.getEtapas().isEmpty()) {
            return Optional.empty();
        }
        long transcurridos = ChronoUnit.DAYS.between(plan.getFechaInicio(), hoy);
        long acumulado = 0;
        for (EtapaPlanRustificacion e : plan.getEtapas()) {
            acumulado += e.getDias();
            if (transcurridos < acumulado) {
                return Optional.of(e);
            }
        }
        return Optional.of(plan.getEtapas().get(plan.getEtapas().size() - 1));
    }

    /** La ventana de la etapa que rige hoy, si el plan está en curso. */
    public static Optional<VentanaDiaria> ventanaDe(PlanRustificacionEntity plan, LocalDate hoy) {
        return etapaDe(plan, hoy).map(e -> new VentanaDiaria(LocalTime.parse(e.getAbiertaDesde()),
                LocalTime.parse(e.getAbiertaHasta())));
    }

    // ------------------------------------------------------------------
    // Validación
    // ------------------------------------------------------------------

    private static LocalDate validar(NuevoPlanRustificacion nuevo, LocalDate hoy) {
        if (nuevo == null || nuevo.fechaInicio() == null) {
            throw new PlanRustificacionInvalidoException("Elegí la fecha de inicio del plan (fechaInicio, YYYY-MM-DD).");
        }
        LocalDate inicio;
        try {
            inicio = LocalDate.parse(nuevo.fechaInicio());
        } catch (DateTimeParseException e) {
            throw new PlanRustificacionInvalidoException("La fecha de inicio debe tener el formato YYYY-MM-DD.");
        }
        if (inicio.isBefore(hoy)) {
            throw new PlanRustificacionInvalidoException("La fecha de inicio no puede ser anterior a hoy.");
        }
        List<NuevoPlanRustificacion.Etapa> etapas = nuevo.etapas();
        if (etapas == null || etapas.isEmpty()) {
            throw new PlanRustificacionInvalidoException("El plan necesita al menos una etapa.");
        }
        if (etapas.size() > MAX_ETAPAS) {
            throw new PlanRustificacionInvalidoException("El plan admite hasta " + MAX_ETAPAS + " etapas.");
        }
        for (int i = 0; i < etapas.size(); i++) {
            NuevoPlanRustificacion.Etapa e = etapas.get(i);
            String cual = "Etapa " + (i + 1) + ": ";
            if (e == null || e.dias() == null || e.dias() < 1 || e.dias() > MAX_DIAS_ETAPA) {
                throw new PlanRustificacionInvalidoException(cual + "la duración debe ser un entero entre 1 y "
                        + MAX_DIAS_ETAPA + " días.");
            }
            if (!horaValida(e.abiertaDesde()) || !horaValida(e.abiertaHasta())) {
                throw new PlanRustificacionInvalidoException(cual + "cada hora debe tener el formato HH:MM.");
            }
            if (!LocalTime.parse(e.abiertaHasta()).isAfter(LocalTime.parse(e.abiertaDesde()))) {
                throw new PlanRustificacionInvalidoException(cual
                        + "la hora de cierre debe ser posterior a la de apertura.");
            }
        }
        return inicio;
    }

    private static boolean horaValida(String hora) {
        return hora != null && HORA_HH_MM.matcher(hora).matches();
    }

    // ------------------------------------------------------------------

    private ZonaEntity zona(String zonaId) {
        return zonas.findById(zonaId).orElseThrow(() ->
                new PlanRustificacionInvalidoException("La macro-zona «" + zonaId + "» no existe."));
    }

    private LocalDate hoy() {
        return LocalDate.now(reloj.withZone(ZonaHorariaVivero.ZONA));
    }

    private static int totalDias(List<EtapaPlanRustificacion> etapas) {
        return etapas.stream().mapToInt(EtapaPlanRustificacion::getDias).sum();
    }

    private static String autor(String usuario) {
        return usuario != null && !usuario.isBlank() ? usuario : "Ingeniero Agrónomo";
    }

    private static PlanRustificacion toDto(PlanRustificacionEntity p, LocalDate hoy) {
        return new PlanRustificacion(
                p.getId(),
                p.getZonaId(),
                p.getFechaInicio().toString(),
                p.getEtapas().stream()
                        .map(e -> new PlanRustificacion.EtapaRustificacion(e.getOrden(), e.getDias(),
                                e.getAbiertaDesde(), e.getAbiertaHasta()))
                        .toList(),
                estado(p, hoy),
                p.getCreadoPor(),
                p.getCreadoEn(),
                p.getCanceladoEn());
    }
}
