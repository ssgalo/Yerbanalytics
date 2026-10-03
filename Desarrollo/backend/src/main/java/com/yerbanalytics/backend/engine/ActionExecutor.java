package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.engine.riego.CicloLectura;
import com.yerbanalytics.backend.engine.riego.ColaRiego;
import com.yerbanalytics.backend.engine.riego.SolicitudRiego;
import com.yerbanalytics.backend.engine.traza.OrigenEvaluacion;
import com.yerbanalytics.backend.service.HistorialService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Único punto donde las acciones del motor se convierten en efectos reales.
 *
 * <ul>
 *   <li>{@code ACTIVAR_VALVULA}: <b>no abre la válvula</b>. Encola una solicitud tipada (volumen, duración,
 *       humedad, regla) en la {@link ColaRiego} de la macro-zona; la abre el {@code DespachoRiego}, de a
 *       {@code riego.sectores-simultaneos} válvulas por zona, que publica el comando y registra el riego.
 *       Es la excepción explícita a "el executor materializa todo": el cupo por zona no se puede decidir
 *       sector por sector.</li>
 *   <li>En una evaluación de <b>telemetría</b> una solicitud en cola pertenece a la ronda decidida y se completa:
 *       sólo la retira una <b>cancelación explícita de seguridad</b> ({@link CancelaRiego}: sustrato saturado,
 *       bloqueo manual, sensor sin datos y, para R-01, ventana cerrada o pausa por aplicación). "La humedad se
 *       recuperó" no cancela. El <b>barrido</b> del watchdog evalúa sin lectura fresca y no toca la cola.</li>
 *   <li>{@code ACTIVAR_BOMBA}: actualiza el campo del sector, registra en historial y publica
 *       {@code pump ON}.</li>
 *   <li>{@code MOVER_MEDIASOMBRA}: actualiza {@code actuadorShade} con el porcentaje del motivo
 *       ({@code [apertura=N]}) y publica {@code shade SET}.</li>
 *   <li>{@code ALERTA}: se persiste como evento "Alerta", una sola vez por macro-zona, regla y ciclo de
 *       lectura (el motor evalúa 100 sectores por mensaje: sin esto serían 100 alertas iguales).</li>
 *   <li>{@code NOOP_INFO} y las bloqueantes ({@code ABORT_*}, {@code POSTPONE_RIEGO}): se persiste el motivo
 *       como Registro de Inacción, <b>sólo cuando cambia la decisión del sector</b> (ver abajo); el corte ya lo
 *       aplicó el {@link RuleOrchestrator}.</li>
 * </ul>
 *
 * <p><b>Registro de Inacción sólo ante un cambio.</b> Con el nodo real publicando cada 30 s, una fila "Info" por
 * sector, por regla y por mensaje eran ~1.300 filas por mensaje de una zona (millones por día) que repetían lo mismo.
 * Por cada sector (y por origen: telemetría o barrido) se recuerda la última decisión registrada: el tipo de acción y
 * una clave estable del motivo (el texto sin los números, que cambian en cada lectura) de cada regla. Si la
 * evaluación coincide no se escribe nada; si cambió la decisión de CUALQUIER regla se escribe el conjunto completo de
 * ese sector en esa evaluación, así el DAG del Historial (que pinta los nodos con las filas del sector en ese minuto)
 * sigue teniendo todas las reglas. El barrido tampoco repite lo que ya registró la telemetría. Es estado en memoria:
 * tras un reinicio se registra una vez más y {@link #reiniciarEstado()} lo limpia al regenerar la topología.
 *
 * <p>El tópico de comando tiene la forma {@code nursery/zone/{zonaId}/sector/{sectorId}/command},
 * alineado con el contrato definido en {@code embebido/comun/contrato.h}.
 */
@Service
public class ActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(ActionExecutor.class);

    /** Extrae el porcentaje de apertura del motivo de MOVER_MEDIASOMBRA: {@code [apertura=N]}. */
    private static final Pattern APERTURA_PATTERN = Pattern.compile("\\[apertura=(\\d+)\\]");

    private static final int INTERVALO_SENSADO_DEFAULT_MIN = 240;

    private final HistorialService historialService;
    private final ComandoActuadorPublisher publisher;
    private final ColaRiego cola;

    /**
     * Último ciclo de lectura en que se persistió una alerta, por {@code zona|regla}. En memoria: tras un
     * reinicio una alerta puede repetirse una vez, a cambio de no sumar una tabla.
     */
    private final Map<String, Instant> alertasPorCiclo = new ConcurrentHashMap<>();

    /**
     * Última decisión de inacción registrada por sector: {@code regla → firma (tipo + clave estable del motivo)}.
     * Una por origen: la telemetría y el barrido evalúan con datos distintos (el barrido, sin métricas) y no
     * deben pisarse el estado entre sí.
     */
    private final Map<String, Map<String, String>> inaccionTelemetria = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> inaccionBarrido = new ConcurrentHashMap<>();

    public ActionExecutor(HistorialService historialService, ComandoActuadorPublisher publisher, ColaRiego cola) {
        this.historialService = historialService;
        this.publisher = publisher;
        this.cola = cola;
    }

    /**
     * Materializa la lista de acciones emitidas por el orquestador.
     *
     * @param actions lista de acciones a ejecutar (puede ser vacía)
     * @param ctx     snapshot inmutable del sector para extraer datos de persistencia
     * @param origen  qué disparó la evaluación: sólo la telemetría decide sobre la cola de riego
     */
    public void execute(List<RuleAction> actions, RuleContext ctx, OrigenEvaluacion origen) {
        String oldPump = ctx.sector().getActuadorPump();

        if (origen == OrigenEvaluacion.TELEMETRIA) {
            actualizarCola(actions, ctx);
        }
        registrarInacciones(actions, ctx, origen);

        for (RuleAction action : actions) {
            switch (action.type()) {

                case ACTIVAR_VALVULA ->
                    // El efecto (encolar o retirar) ya se resolvió arriba, una vez por evaluación.
                    log.info("Sector {}: ACTIVAR_VALVULA — {}", ctx.sector().getId(), action.motivo());

                case ACTIVAR_BOMBA -> {
                    log.info("Sector {}: ACTIVAR_BOMBA — {}", ctx.sector().getId(), action.motivo());
                    ctx.sector().setActuadorPump("Dosificando");
                    if (!"Dosificando".equals(oldPump)) {
                        historialService.registrarInsumo(ctx.sector());
                        // --- Downlink: enviar orden física al nodo actuador ---
                        publishCommand(ctx, "pump", "ON", Map.of());
                    }
                }

                case MOVER_MEDIASOMBRA -> {
                    int apertura = parseApertura(action.motivo(), ctx.sector().getActuadorShade());
                    log.info("Sector {}: MOVER_MEDIASOMBRA → {}% — {}",
                            ctx.sector().getId(), apertura, action.motivo());
                    ctx.sector().setActuadorShade(apertura);
                    // Downlink: enviar posición de mediasombra
                    publishCommand(ctx, "shade", "SET", Map.of("targetPct", apertura));
                }

                // Registro de Inacción: el usuario puede ver por qué el motor no actuó. Ya se persistió arriba
                // (sólo si cambió la decisión del sector); acá queda el log.
                case NOOP_INFO -> log.debug("Sector {}: NOOP_INFO — {}", ctx.sector().getId(), action.motivo());

                // El corte ya fue aplicado por el RuleOrchestrator.
                case ABORT_RIEGO, ABORT_INSUMO, ABORT_ALL, POSTPONE_RIEGO ->
                    log.debug("Sector {}: {} — {}", ctx.sector().getId(), action.type(), action.motivo());

                case ALERTA -> persistirAlerta(action, ctx);

                default -> log.warn("Sector {}: acción desconocida '{}'", ctx.sector().getId(), action.type());
            }
        }
    }

    /** Olvida lo que recuerda de lo ya registrado (al regenerar la topología los ids de sector se reutilizan). */
    public void reiniciarEstado() {
        inaccionTelemetria.clear();
        inaccionBarrido.clear();
        alertasPorCiclo.clear();
    }

    // -------------------------------------------------------------------------
    // Registro de Inacción
    // -------------------------------------------------------------------------

    /** Números (con signo y decimales): lo que cambia en cada lectura y no es una decisión distinta. */
    private static final Pattern NUMEROS = Pattern.compile("-?\\d+(?:[.,]\\d+)?");

    private static boolean esInaccion(RuleAction a) {
        return a.type() == ActionType.NOOP_INFO || a.type().isBlocking();
    }

    /** Tipo de acción y motivo sin números: "humedad 44% bajo 45%" y "humedad 40% bajo 45%" son la misma decisión. */
    private static String firma(RuleAction a) {
        String motivo = a.motivo() == null ? "" : NUMEROS.matcher(a.motivo()).replaceAll("#");
        return a.type() + "|" + motivo;
    }

    /**
     * Escribe las filas "Info" del sector sólo si su decisión cambió (cualquier regla): en ese caso, el conjunto
     * completo de la evaluación. Si la escritura falla se propaga y NO se da por registrada: la próxima evaluación
     * lo reintenta.
     */
    private void registrarInacciones(List<RuleAction> actions, RuleContext ctx, OrigenEvaluacion origen) {
        List<RuleAction> inacciones = actions.stream().filter(ActionExecutor::esInaccion).toList();
        if (inacciones.isEmpty()) {
            return;
        }
        // Una regla puede emitir más de una acción de inacción: la clave lleva el orden para no pisarlas.
        Map<String, String> actual = new LinkedHashMap<>();
        for (RuleAction a : inacciones) {
            String clave = a.ruleName();
            for (int n = 2; actual.containsKey(clave); n++) {
                clave = a.ruleName() + "#" + n;
            }
            actual.put(clave, firma(a));
        }
        String sectorId = ctx.sector().getId();
        boolean telemetria = origen == OrigenEvaluacion.TELEMETRIA;
        Map<String, Map<String, String>> propio = telemetria ? inaccionTelemetria : inaccionBarrido;
        if (actual.equals(propio.get(sectorId))) {
            return;
        }
        // El barrido no repite lo que la telemetría ya dejó asentado.
        if (!telemetria && actual.equals(inaccionTelemetria.get(sectorId))) {
            propio.put(sectorId, actual);
            return;
        }
        for (RuleAction a : inacciones) {
            historialService.registrarInaccion(ctx.sector(), a.type(), a.ruleName(), a.motivo());
        }
        propio.put(sectorId, actual);
    }

    // -------------------------------------------------------------------------
    // Cola de riego
    // -------------------------------------------------------------------------

    /**
     * Una solicitud en cola pertenece a la ronda que se decidió y se completa: que la regla ya no pida riego
     * porque la humedad se recuperó NO la cancela (el nodo testigo mide un solo sector; cuando el despacho lo
     * riega la humedad sube y, si eso retirara lo pendiente, los demás sectores de la zona quedarían sin agua).
     *
     * <p>Lo que cambia la cola en una evaluación de telemetría:
     * <ul>
     *   <li>un {@code ACTIVAR_VALVULA} encola o actualiza la solicitud del sector (nunca la duplica) y una
     *       decisión de R-01 NO degrada una de R-02 ya encolada;</li>
     *   <li>una cancelación explícita de seguridad ({@link CancelaRiego} en el {@code ABORT_RIEGO} o
     *       {@code ABORT_ALL}) la retira, si le alcanza.</li>
     * </ul>
     * Todo lo demás (NOOP, el corte de la regla de ciclo, el tope de R-02, R-03) la deja como está. Aun así el
     * despacho revalida los datos vigentes antes de abrir cada válvula.
     */
    private void actualizarCola(List<RuleAction> actions, RuleContext ctx) {
        if (ctx.zona() == null) {
            return;
        }
        String zonaId = ctx.zona().getId();
        String sectorId = ctx.sector().getId();
        RuleAction orden = actions.stream()
                .filter(a -> a.type() == ActionType.ACTIVAR_VALVULA)
                .findFirst().orElse(null);
        SolicitudRiego vigente = cola.solicitudDe(zonaId, sectorId);

        if (orden != null) {
            if (!(orden.detalle() instanceof DetalleRiego detalle)) {
                log.error("Sector {}: ACTIVAR_VALVULA de {} sin detalle de riego: no se encola.", sectorId, orden.ruleName());
                cola.retirar(zonaId, sectorId);
                return;
            }
            if (vigente != null && vigente.esDeficitCritico() && !SolicitudRiego.REGLA_DEFICIT_CRITICO.equals(orden.ruleName())) {
                log.debug("Sector {}: ya tiene un riego de déficit crítico en cola; {} no lo degrada.", sectorId, orden.ruleName());
                return;
            }
            Integer numero = ctx.sector().getN();
            cola.solicitar(new SolicitudRiego(zonaId, sectorId, numero != null ? numero : 0, detalle,
                    orden.ruleName(), ctx.now()));
            return;
        }

        if (vigente == null) {
            return;
        }
        for (RuleAction a : actions) {
            if (a.detalle() instanceof CancelaRiego cancelacion && cancelacion.alcanza(vigente)) {
                if (cola.retirarSiCoincide(vigente)) {
                    log.info("Sector {}: riego pendiente cancelado por {} — {}", sectorId, a.ruleName(), a.motivo());
                }
                return;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Alertas
    // -------------------------------------------------------------------------

    /** Persiste la alerta una sola vez por macro-zona, regla y ciclo de lectura. */
    private void persistirAlerta(RuleAction action, RuleContext ctx) {
        if (!(action.detalle() instanceof DetalleAlerta detalle) || ctx.zona() == null) {
            log.warn("Sector {}: ALERTA de {} sin detalle o sin macro-zona: se ignora.",
                    ctx.sector().getId(), action.ruleName());
            return;
        }
        Instant ciclo = ctx.riego().inicioCiclo() != null ? ctx.riego().inicioCiclo()
                : CicloLectura.inicio(ctx.now(), intervaloSensado(ctx));
        String clave = ctx.zona().getId() + "|" + action.ruleName();
        Instant previo = alertasPorCiclo.put(clave, ciclo);
        if (ciclo.equals(previo)) {
            return;   // ya se avisó en este ciclo
        }
        try {
            historialService.registrarAlerta(ctx.zona().getId(), ctx.zona().getName(), action.ruleName(),
                    detalle, ctx.now().toEpochMilli());
        } catch (RuntimeException e) {
            // Que no quede marcada como avisada: la próxima evaluación del ciclo lo reintenta.
            if (previo == null) {
                alertasPorCiclo.remove(clave, ciclo);
            } else {
                alertasPorCiclo.put(clave, previo);
            }
            log.error("Zona {}: no se pudo registrar la alerta de {}.", ctx.zona().getId(), action.ruleName(), e);
        }
    }

    private static int intervaloSensado(RuleContext ctx) {
        return ctx.config() != null && ctx.config().getIntervaloSensadoMinutos() != null
                ? ctx.config().getIntervaloSensadoMinutos() : INTERVALO_SENSADO_DEFAULT_MIN;
    }

    // -------------------------------------------------------------------------
    // Publicación MQTT
    // -------------------------------------------------------------------------

    /**
     * Publica el comando al tópico del sector a través del {@link ComandoActuadorPublisher}.
     *
     * <p>Si la publicación falla (broker caído, canal lleno, etc.) se registra y NO se frena la
     * ejecución: el estado en base de datos ya fue actualizado y el historial ya fue escrito.
     */
    private void publishCommand(RuleContext ctx, String actuador, String accion, Map<String, Object> parametros) {
        String zonaId   = ctx.zona() != null ? ctx.zona().getId() : "unknown";
        String sectorId = ctx.sector().getId();
        ComandoActuadorPublisher.Resultado r = publisher.publicar(zonaId, sectorId, actuador, accion, parametros);
        if (!r.publicado()) {
            // No propagamos: el comando físico se reintentará cuando el motor vuelva a evaluar.
            log.warn("Sector {}: el comando {} {} no se publicó — {}", sectorId, actuador, accion, r.error());
        }
    }

    // -------------------------------------------------------------------------
    // Parseo de parámetros del motivo
    // -------------------------------------------------------------------------

    /**
     * Extrae el porcentaje de apertura del motivo de la acción {@code MOVER_MEDIASOMBRA}.
     * Si el motivo no contiene el patrón {@code [apertura=N]}, retorna {@code fallback}.
     */
    private static int parseApertura(String motivo, int fallback) {
        if (motivo == null) return fallback;
        Matcher m = APERTURA_PATTERN.matcher(motivo);
        return m.find() ? Integer.parseInt(m.group(1)) : fallback;
    }
}
