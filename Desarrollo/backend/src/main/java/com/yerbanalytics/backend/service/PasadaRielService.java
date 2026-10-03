package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.config.PasadaProperties;
import com.yerbanalytics.backend.dto.DiagnosticoPaso;
import com.yerbanalytics.backend.dto.EstadoOrden;
import com.yerbanalytics.backend.dto.Pasada;
import com.yerbanalytics.backend.dto.PasoPasada;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.mqtt.ComandoRielPublisher;
import com.yerbanalytics.backend.mqtt.ContratoRiel;
import com.yerbanalytics.backend.mqtt.EventoRiel;
import com.yerbanalytics.backend.repository.DiagnosticoRepository;
import com.yerbanalytics.backend.repository.ZonaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Orquestador de la pasada del riel: mueve el riel, pide la foto de cada sector y vuelve a home.
 * Es la capacidad que {@link CapturaService} anunciaba como "futuro planificador de pasadas": emite
 * las órdenes por {@link CapturaService#emitirOrden}, igual que cualquier otro emisor.
 *
 * <p>Una pasada son 5 pasos fijos (MOVER 1, CAPTURAR sector A, MOVER 2, CAPTURAR sector B, HOME),
 * una sola a la vez. El estado vive <strong>en memoria</strong>: dura ~2 minutos, nadie más la
 * consulta, y lo que importa registrar (órdenes, capturas, diagnósticos) ya lo persisten sus
 * servicios. Un reinicio a mitad la pierde, y se puede iniciar otra: {@code IR_A} es absoluto.
 *
 * <p><b>Hilos.</b> Todo lo que muta ({@link #iniciar}, {@link #cancelar}, {@link #tick}) es
 * {@code synchronized}. {@link #registrarEvento}, que corre en el hilo de Paho, <em>sólo encola</em>:
 * no toma el lock ni hace I/O, así que un {@code emitirOrden} lento (escribe en el SSE) no lo frena.
 * El {@link #tick} consume los eventos, consulta las órdenes y avanza la máquina. La lectura
 * ({@link #estado}) usa una foto inmutable publicada tras cada mutación, sin lock.
 *
 * <p>Qué pasa ante una falla, a propósito asimétrico: si falla una FOTO se sigue con el resto (el
 * riel está bien); si falla un MOVIMIENTO se omite lo pendiente y se manda a home; si falla HOME no
 * se reintenta. Ver {@code openspec/changes/add-pasada-riel/design.md} §2.2.
 */
@Service
public class PasadaRielService {

    private static final Logger log = LoggerFactory.getLogger(PasadaRielService.class);

    // Tipos y estados: Strings, igual que EstadoOrden, porque viajan tal cual en el JSON.
    private static final String MOVER = "MOVER";
    private static final String CAPTURAR = "CAPTURAR";
    private static final String HOME = "HOME";

    private static final String PENDIENTE = "PENDIENTE";
    private static final String EN_CURSO = "EN_CURSO";
    private static final String OK = "OK";
    private static final String ERROR = "ERROR";
    private static final String OMITIDO = "OMITIDO";

    private static final String COMPLETADA = "COMPLETADA";
    private static final String FALLIDA = "FALLIDA";
    private static final String CANCELADA = "CANCELADA";

    private static final String RECIBIDA = "RECIBIDA";
    private static final String CANCELADA_POR_OPERADOR = "Cancelada por el operador";

    private static final Pattern NUMERO_FINAL = Pattern.compile("(\\d+)$");

    private final CapturaService capturaService;
    private final ComandoRielPublisher rielPublisher;
    private final ZonaRepository zonaRepo;
    private final DiagnosticoRepository diagnosticoRepo;
    private final PasadaProperties props;
    private final Clock reloj;

    /** Eventos del riel pendientes de procesar. Sin lock a propósito: ver nota de hilos. */
    private final Queue<EventoRiel> eventos = new ConcurrentLinkedQueue<>();

    /** Estado mutable de la última pasada. Protegido por {@code this}. */
    private PasadaInterna actual;

    /** Foto inmutable de {@link #actual}, para leer sin lock. */
    private volatile Pasada foto;

    public PasadaRielService(CapturaService capturaService,
                             ComandoRielPublisher rielPublisher,
                             ZonaRepository zonaRepo,
                             DiagnosticoRepository diagnosticoRepo,
                             PasadaProperties props,
                             Clock relojVivero) {
        this.capturaService = capturaService;
        this.rielPublisher = rielPublisher;
        this.zonaRepo = zonaRepo;
        this.diagnosticoRepo = diagnosticoRepo;
        this.props = props;
        this.reloj = relojVivero;
    }

    // ==================================================================
    // API
    // ==================================================================

    /**
     * Arma y arranca una pasada. Las precondiciones se evalúan en este orden y la primera que
     * falle rechaza sin publicar nada: pasada en curso, topología, dispositivo de captura.
     */
    public synchronized Pasada iniciar() {
        if (actual != null && EN_CURSO.equals(actual.estado)) {
            throw new PasadaRechazadaException("Ya hay una pasada en curso.");
        }
        List<SectorEntity> sectores = sectoresDeLaPasada();
        if (!capturaService.hayCanalAbierto()) {
            throw new PasadaRechazadaException("No hay ningún dispositivo de captura conectado.");
        }

        PasadaInterna p = new PasadaInterna(UUID.randomUUID().toString(), reloj.millis());
        p.pasos.add(new Paso(1, MOVER, 1, null));
        p.pasos.add(new Paso(2, CAPTURAR, 1, sectores.get(0).getId()));
        p.pasos.add(new Paso(3, MOVER, 2, null));
        p.pasos.add(new Paso(4, CAPTURAR, 2, sectores.get(1).getId()));
        p.pasos.add(new Paso(5, HOME, 0, null));
        actual = p;

        log.info("Pasada {}: iniciada ({} y {})", p.id, sectores.get(0).getId(), sectores.get(1).getId());
        entrar(p, p.pasos.get(0));
        publicarFoto();
        return foto;
    }

    /**
     * Cancela la pasada en curso: lo que falta se omite y se manda el riel a home (el firmware aborta
     * el movimiento en curso, ver design §1.3-4). Al terminar el HOME la pasada queda CANCELADA. Una
     * orden de captura ya emitida no se cancela (no hay API): si el celular la completa, la foto se
     * archiva y se diagnostica como cualquier otra.
     */
    public synchronized Pasada cancelar() {
        if (actual == null || !EN_CURSO.equals(actual.estado)) {
            throw new PasadaRechazadaException("No hay una pasada en curso.");
        }
        if (actual.cancelacionSolicitada) {
            throw new PasadaRechazadaException("La pasada ya se está cancelando.");
        }
        PasadaInterna p = actual;
        p.cancelacionSolicitada = true;
        log.info("Pasada {}: cancelación solicitada", p.id);

        Paso enCurso = enCurso(p);
        if (enCurso != null && !HOME.equals(enCurso.tipo)) {
            cerrar(enCurso, OMITIDO, null, CANCELADA_POR_OPERADOR);
            for (Paso s : p.pasos) {
                if (PENDIENTE.equals(s.estado) && !HOME.equals(s.tipo)) {
                    s.estado = OMITIDO;
                    s.detalle = CANCELADA_POR_OPERADOR;
                }
            }
            entrar(p, ultimo(p));
        }
        publicarFoto();
        return foto;
    }

    /**
     * Llamado desde el hilo de Paho: sólo encola. Los eventos de comandos ajenos se descartan en el
     * {@link #tick}.
     */
    public void registrarEvento(EventoRiel evento) {
        if (evento != null) {
            eventos.add(evento);
        }
    }

    /** Última pasada (en curso o terminada), con el diagnóstico de cada foto si ya existe. */
    public Optional<Pasada> estado() {
        Pasada f = foto;
        if (f == null) {
            return Optional.empty();
        }
        List<PasoPasada> pasos = new ArrayList<>();
        for (PasoPasada s : f.pasos()) {
            pasos.add(s.capturaId() == null ? s : s.conDiagnostico(diagnosticoDe(s.capturaId())));
        }
        return Optional.of(new Pasada(f.id(), f.estado(), f.iniciadaEn(), f.finalizadaEn(),
                f.cancelacionSolicitada(), f.error(), List.copyOf(pasos)));
    }

    /** Un paso de la máquina por segundo, en su propio carril: no compite con el motor de reglas. */
    @Scheduled(fixedDelayString = "${yerbanalytics.pasada.tick-ms:1000}", scheduler = "pasadaScheduler")
    public synchronized void tick() {
        try {
            avanzar();
        } catch (RuntimeException e) {
            log.error("Pasada: falló el tick — {}", e.getMessage(), e);
        }
    }

    // ==================================================================
    // Máquina de estados
    // ==================================================================

    private void avanzar() {
        List<EventoRiel> pendientes = new ArrayList<>();
        for (EventoRiel e; (e = eventos.poll()) != null; ) {
            pendientes.add(e);
        }
        PasadaInterna p = actual;
        if (p == null || !EN_CURSO.equals(p.estado)) {
            return;   // los eventos de una pasada terminada o perdida no le importan a nadie
        }

        Paso antes = enCurso(p);
        for (EventoRiel e : pendientes) {
            aplicarEvento(p, e);
            if (!EN_CURSO.equals(p.estado)) {
                break;
            }
        }
        // Un paso por tick: si el evento cerró el paso, el siguiente ya arrancó y se revisa en el
        // próximo (así una orden recién emitida no se consulta en el mismo tick que la emitió).
        if (EN_CURSO.equals(p.estado) && enCurso(p) == antes && antes != null) {
            if (CAPTURAR.equals(antes.tipo)) {
                revisarCaptura(p, antes);
            } else {
                revisarMovimiento(p, antes);
            }
        }
        publicarFoto();
    }

    /** Arranca un paso. Puede cerrarlo al instante si falla, y por lo tanto encadenar el siguiente. */
    private void entrar(PasadaInterna p, Paso paso) {
        paso.estado = EN_CURSO;
        paso.iniciadoEn = reloj.millis();
        switch (paso.tipo) {
            case CAPTURAR -> {
                try {
                    EstadoOrden orden = capturaService.emitirOrden(paso.sectorId, paso.posicion);
                    paso.ordenId = orden.ordenId();
                    paso.estadoOrden = orden.estado();
                } catch (RuntimeException e) {
                    cerrarYSeguir(p, paso, ERROR, "ORDEN_FALLIDA",
                            "El celular no pudo sacar la foto: " + e.getMessage());
                }
            }
            case MOVER -> publicarComando(p, paso, rielPublisher.irA(paso.posicion));
            default -> publicarComando(p, paso, rielPublisher.home());
        }
    }

    private void publicarComando(PasadaInterna p, Paso paso, ComandoRielPublisher.Resultado r) {
        paso.commandId = r.commandId();
        paso.publicadoEn = reloj.millis();
        if (!r.publicado()) {
            cerrarYSeguir(p, paso, ERROR, "PUBLICACION_FALLIDA",
                    "No se pudo publicar el comando al broker: " + r.error());
        }
    }

    private void aplicarEvento(PasadaInterna p, EventoRiel ev) {
        Paso paso = enCurso(p);
        if (paso == null || CAPTURAR.equals(paso.tipo) || !Objects.equals(paso.commandId, ev.commandId())) {
            log.debug("Riel: evento ignorado (commandId {} no es el del paso en curso)", ev.commandId());
            return;
        }
        if (ContratoRiel.STATUS_ACEPTADO.equals(ev.status())) {
            paso.aceptado = true;
        } else if (ContratoRiel.STATUS_LLEGO.equals(ev.status())) {
            cerrarYSeguir(p, paso, OK, null, null);
        } else {
            // ERROR, o un status/código que no conocemos: se trata como ERROR con ese código.
            String codigo = ev.codigo() != null ? ev.codigo() : String.valueOf(ev.status());
            cerrarYSeguir(p, paso, ERROR, codigo, detalleDeError(paso, codigo, ev.detalle()));
        }
    }

    private void revisarMovimiento(PasadaInterna p, Paso paso) {
        long transcurrido = reloj.millis() - paso.publicadoEn;
        long aceptacionMs = props.getTimeoutAceptacionSeg() * 1000L;
        if (transcurrido > props.getTimeoutMovimientoSeg() * 1000L) {
            cerrarYSeguir(p, paso, ERROR, "TIMEOUT_MOVIMIENTO", "El riel no llegó a " + destino(paso)
                    + " en " + props.getTimeoutMovimientoSeg() + " s.");
        } else if (!paso.aceptado) {
            if (transcurrido >= 2 * aceptacionMs) {
                cerrarYSeguir(p, paso, ERROR, "RIEL_SIN_RESPUESTA",
                        "El riel no respondió. ¿El ESP32 está encendido y conectado al broker?");
            } else if (transcurrido >= aceptacionMs && !paso.republicado) {
                // Mismo commandId: para el firmware es un redelivery, no un segundo movimiento.
                paso.republicado = true;
                if (MOVER.equals(paso.tipo)) {
                    rielPublisher.irA(paso.posicion, paso.commandId);
                } else {
                    rielPublisher.home(paso.commandId);
                }
                log.info("Pasada {}: el riel no respondió en {} s, se republicó el comando {}",
                        p.id, props.getTimeoutAceptacionSeg(), paso.commandId);
            }
        }
    }

    private void revisarCaptura(PasadaInterna p, Paso paso) {
        try {
            EstadoOrden orden = capturaService.consultarOrden(paso.ordenId);
            paso.estadoOrden = orden.estado();
            if (RECIBIDA.equals(orden.estado())) {
                paso.capturaId = orden.capturaId();
                paso.imagenUrl = orden.imagenUrl() != null
                        ? orden.imagenUrl() : CapturaService.imagenUrl(orden.capturaId());
                cerrarYSeguir(p, paso, OK, null, null);
                return;
            }
            if (ERROR.equals(orden.estado())) {
                String motivo = Objects.toString(orden.motivoFallo(), "");
                String detalle = Objects.toString(orden.detalleFallo(), "");
                cerrarYSeguir(p, paso, ERROR, "ORDEN_FALLIDA",
                        ("El celular no pudo sacar la foto: " + motivo + " " + detalle).trim());
                return;
            }
        } catch (RuntimeException e) {
            cerrarYSeguir(p, paso, ERROR, "ORDEN_FALLIDA", "El celular no pudo sacar la foto: " + e.getMessage());
            return;
        }
        if (reloj.millis() - paso.iniciadoEn > props.getTimeoutCapturaSeg() * 1000L) {
            // La orden sigue su vida en CapturaService: si la foto llega tarde, igual se diagnostica.
            cerrarYSeguir(p, paso, ERROR, "TIMEOUT_CAPTURA", "La foto del sector " + paso.sectorId
                    + " no llegó en " + props.getTimeoutCapturaSeg() + " s.");
        }
    }

    /** Cierra el paso y aplica la transición de la pasada. */
    private void cerrarYSeguir(PasadaInterna p, Paso paso, String estado, String codigo, String detalle) {
        cerrar(paso, estado, codigo, detalle);
        if (ERROR.equals(estado) && p.error == null) {
            p.error = detalle;
        }
        if (HOME.equals(paso.tipo)) {
            finalizar(p);
        } else if (MOVER.equals(paso.tipo) && ERROR.equals(estado)) {
            // El riel no está donde se esperaba: no tiene sentido sacar fotos. Se va a home.
            for (Paso s : p.pasos) {
                if (PENDIENTE.equals(s.estado) && !HOME.equals(s.tipo)) {
                    s.estado = OMITIDO;
                }
            }
            entrar(p, ultimo(p));
        } else {
            // OK, o una foto fallida (el riel está bien): al siguiente.
            entrar(p, p.pasos.get(paso.n));
        }
    }

    private void cerrar(Paso paso, String estado, String codigo, String detalle) {
        paso.estado = estado;
        paso.codigoError = codigo;
        paso.detalle = detalle;
        paso.terminadoEn = reloj.millis();
    }

    private void finalizar(PasadaInterna p) {
        p.finalizadaEn = reloj.millis();
        boolean hayError = p.pasos.stream().anyMatch(s -> ERROR.equals(s.estado));
        p.estado = p.cancelacionSolicitada ? CANCELADA : hayError ? FALLIDA : COMPLETADA;
        log.info("Pasada {}: {}{}", p.id, p.estado, p.error != null ? " — " + p.error : "");
    }

    // ==================================================================
    // Detalles legibles, topología y foto
    // ==================================================================

    private static String destino(Paso paso) {
        return HOME.equals(paso.tipo) ? "home" : "la posición " + paso.posicion;
    }

    private static String detalleDeError(Paso paso, String codigo, String detalleEvento) {
        return switch (codigo) {
            case ContratoRiel.CODIGO_FIN_DE_CARRERA ->
                    "El riel tocó un final de carrera antes de llegar a " + destino(paso) + ".";
            case ContratoRiel.CODIGO_HOME_NO_ENCONTRADO -> "El riel no encontró el final de carrera de home.";
            case ContratoRiel.CODIGO_COMANDO_INVALIDO -> "El ESP32 rechazó el comando: " + detalleEvento;
            case ContratoRiel.CODIGO_REEMPLAZADO -> "El movimiento fue interrumpido por otro comando.";
            default -> ("El riel informó un error: " + codigo + " " + Objects.toString(detalleEvento, "")).trim();
        };
    }

    /**
     * La macro-zona de menor número (orden numérico del sufijo, no lexicográfico: MZ-10 va después
     * de MZ-9) y sus sectores por id. Posición 1 es el primero; posición 2, el segundo.
     */
    private List<SectorEntity> sectoresDeLaPasada() {
        ZonaEntity zona = zonaRepo.findAllWithSectors().stream()
                .min(Comparator.comparingInt((ZonaEntity z) -> numeroFinal(z.getId())).thenComparing(ZonaEntity::getId))
                .orElseThrow(() -> new PasadaRechazadaException("No hay topología configurada."));
        List<SectorEntity> sectores = zona.getSectors().stream()
                .sorted(Comparator.comparing(SectorEntity::getId))
                .toList();
        if (sectores.size() < 2) {
            throw new PasadaRechazadaException("La pasada necesita al menos 2 sectores en " + zona.getId()
                    + " (hay " + sectores.size() + ").");
        }
        return sectores;
    }

    private static int numeroFinal(String id) {
        Matcher m = NUMERO_FINAL.matcher(id);
        return m.find() ? Integer.parseInt(m.group(1)) : Integer.MAX_VALUE;
    }

    private DiagnosticoPaso diagnosticoDe(String capturaId) {
        return diagnosticoRepo.findFirstByCapturaIdOrderByCreadoEnDesc(capturaId)
                .map(d -> new DiagnosticoPaso(d.getEstado(), d.getConf(), d.getSev(), d.getCreadoEn()))
                .orElse(null);
    }

    private static Paso enCurso(PasadaInterna p) {
        return p.pasos.stream().filter(s -> EN_CURSO.equals(s.estado)).findFirst().orElse(null);
    }

    private static Paso ultimo(PasadaInterna p) {
        return p.pasos.get(p.pasos.size() - 1);
    }

    private void publicarFoto() {
        PasadaInterna p = actual;
        List<PasoPasada> pasos = new ArrayList<>();
        for (Paso s : p.pasos) {
            pasos.add(new PasoPasada(s.n, s.tipo, s.posicion, s.sectorId, s.estado, s.codigoError, s.detalle,
                    s.commandId, s.ordenId, s.estadoOrden, s.capturaId, s.imagenUrl, null,
                    s.iniciadoEn, s.terminadoEn));
        }
        foto = new Pasada(p.id, p.estado, p.iniciadaEn, p.finalizadaEn, p.cancelacionSolicitada, p.error,
                List.copyOf(pasos));
    }

    // ==================================================================
    // Estado interno (mutable, sólo se toca con el lock)
    // ==================================================================

    private static final class PasadaInterna {
        final String id;
        final long iniciadaEn;
        final List<Paso> pasos = new ArrayList<>();
        String estado = EN_CURSO;
        Long finalizadaEn;
        boolean cancelacionSolicitada;
        String error;

        PasadaInterna(String id, long iniciadaEn) {
            this.id = id;
            this.iniciadaEn = iniciadaEn;
        }
    }

    private static final class Paso {
        final int n;
        final String tipo;
        final int posicion;
        final String sectorId;
        String estado = PENDIENTE;
        String codigoError;
        String detalle;
        String commandId;
        String ordenId;
        String estadoOrden;
        String capturaId;
        String imagenUrl;
        Long iniciadoEn;
        Long terminadoEn;
        long publicadoEn;
        boolean aceptado;
        boolean republicado;

        Paso(int n, String tipo, int posicion, String sectorId) {
            this.n = n;
            this.tipo = tipo;
            this.posicion = posicion;
            this.sectorId = sectorId;
        }
    }
}
