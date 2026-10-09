package com.yerbanalytics.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.yerbanalytics.backend.config.SecuenciaProperties;
import com.yerbanalytics.backend.dto.IniciarSecuencia;
import com.yerbanalytics.backend.dto.LecturaSecuencia;
import com.yerbanalytics.backend.dto.ParametrosSecuencia;
import com.yerbanalytics.backend.dto.PasoSecuencia;
import com.yerbanalytics.backend.dto.Secuencia;
import com.yerbanalytics.backend.engine.ComandoActuadorPublisher;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.mqtt.AckActuador;
import com.yerbanalytics.backend.mqtt.ComandoZonaPublisher;
import com.yerbanalytics.backend.mqtt.ContratoNodo;
import com.yerbanalytics.backend.mqtt.MqttTelemetryPayload;
import com.yerbanalytics.backend.repository.ZonaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Orquestador de las secuencias guionadas de la Demo Expo: <b>riego</b> (abrir la válvula, esperar,
 * cerrar), <b>mediasombra</b> (desplegar, esperar, enrollar) y <b>lectura</b> (pedir la lectura, esperar
 * la telemetría, mostrarla). Calcado de {@link PasadaRielService}, a propósito una máquina aparte: la
 * pasada está probada con hardware y sus pasos (captura, {@code ACEPTADO}, republicar) no se parecen a
 * los de un actuador.
 *
 * <p>Comandan el actuador <strong>directo</strong> por {@link ComandoActuadorPublisher}, sin pasar por
 * el motor de reglas, y no hay ningún {@code if (demo)}: son una capacidad más del backend. Una a la
 * vez, y ninguna mientras corre una pasada del riel ({@link GuardiaHardware}: hay un único ESP32).
 * El estado vive <strong>en memoria</strong>; un reinicio a mitad la pierde (la bomba se apaga sola a
 * los {@code durationSec}).
 *
 * <p><b>Hilos.</b> {@link #iniciar}, {@link #cancelar} y {@link #tick} son {@code synchronized};
 * {@link #registrarAck} y {@link #registrarTelemetria}, que corren en hilos de Paho, <em>sólo encolan</em>.
 * El {@link #tick} consume, publica y avanza. La lectura ({@link #estado}) usa una foto inmutable
 * publicada tras cada mutación, sin lock.
 *
 * <p><b>Qué pasa ante una falla.</b> Siempre se llega al paso seguro: si falla el ABRIR o la espera se
 * cierra la válvula igual, porque un ACK perdido no prueba que la bomba no arrancó (idem la mediasombra,
 * que se enrolla). Si falla el paso seguro no se reintenta. Un comando sin ACK no se republica: el ACK
 * de actuador no tiene {@code ACEPTADO}, así que "no llegó" y "se está moviendo" no se distinguen.
 * Ver {@code openspec/changes/add-secuencias-demo-expo/design.md} §2.3.
 */
@Service
public class SecuenciaService implements UsoDelHardware {

    private static final Logger log = LoggerFactory.getLogger(SecuenciaService.class);

    // Tipos de secuencia y de paso, y estados: Strings, como en la pasada, porque viajan tal cual en el JSON.
    static final String RIEGO = "RIEGO";
    static final String MEDIASOMBRA = "MEDIASOMBRA";
    static final String LECTURA = "LECTURA";

    private static final String ABRIR = "ABRIR";
    private static final String ESPERAR = "ESPERAR";
    private static final String CERRAR = "CERRAR";
    private static final String DESPLEGAR = "DESPLEGAR";
    private static final String ENROLLAR = "ENROLLAR";
    private static final String PEDIR = "PEDIR";
    private static final String ESPERAR_TELEMETRIA = "ESPERAR_TELEMETRIA";
    private static final String MOSTRAR = "MOSTRAR";

    private static final String PENDIENTE = "PENDIENTE";
    private static final String EN_CURSO = "EN_CURSO";
    private static final String OK = "OK";
    private static final String ERROR = "ERROR";
    private static final String OMITIDO = "OMITIDO";

    private static final String COMPLETADA = "COMPLETADA";
    private static final String FALLIDA = "FALLIDA";
    private static final String CANCELADA = "CANCELADA";

    private static final String CANCELADA_POR_OPERADOR = "Cancelada por el operador";

    private static final int DURACION_RIEGO_DEFECTO_SEG = 10;
    private static final int ESPERA_MEDIASOMBRA_DEFECTO_SEG = 10;
    private static final int ESPERA_MEDIASOMBRA_MAX_SEG = 600;

    /** Apertura de la mediasombra: 0 = desplegada, 100 = enrollada. El hardware de la expo es binario. */
    private static final int APERTURA_DESPLEGADA = 0;
    private static final int APERTURA_ENROLLADA = 100;

    /** Las 10 métricas del contrato, en el orden con el que viajan en {@code lectura.metricas}. */
    private static final List<String> METRICAS = List.of(
            "humSus", "humAmb", "temp", "tempSuelo", "uv", "ce", "phSuelo", "n", "p", "k");

    private static final Pattern NUMERO_FINAL = Pattern.compile("(\\d+)$");

    private final ComandoActuadorPublisher actuadores;
    private final ComandoZonaPublisher zonaPublisher;
    private final ZonaRepository zonaRepo;
    private final GuardiaHardware guardia;
    private final SecuenciaProperties props;
    private final Clock reloj;

    /** ACKs pendientes de procesar. Sin lock a propósito: ver nota de hilos. */
    private final Queue<AckActuador> acks = new ConcurrentLinkedQueue<>();

    /** Telemetrías pendientes, ya selladas con la hora de recepción. */
    private final Queue<LecturaRecibida> lecturas = new ConcurrentLinkedQueue<>();

    /** Estado mutable de la última secuencia. Protegido por {@code this}. */
    private SecuenciaInterna actual;

    /** Foto inmutable de {@link #actual}, para leer sin lock. */
    private volatile Secuencia foto;

    public SecuenciaService(ComandoActuadorPublisher actuadores,
                            ComandoZonaPublisher zonaPublisher,
                            ZonaRepository zonaRepo,
                            GuardiaHardware guardia,
                            SecuenciaProperties props,
                            Clock relojVivero) {
        this.actuadores = actuadores;
        this.zonaPublisher = zonaPublisher;
        this.zonaRepo = zonaRepo;
        this.guardia = guardia;
        this.props = props;
        this.reloj = relojVivero;
    }

    // ==================================================================
    // API
    // ==================================================================

    /**
     * Valida, arma y arranca una secuencia. Las precondiciones se evalúan en este orden y la primera
     * que falle rechaza sin publicar nada: pedido inválido (400), pasada o secuencia en curso (409),
     * topología (409).
     */
    public Secuencia iniciar(IniciarSecuencia pedido) {
        ParametrosSecuencia parametros = validar(pedido);
        String tipo = pedido.tipo();
        return guardia.conHardwareLibre(this, SecuenciaRechazadaException::new,
                () -> iniciarSecuencia(tipo, parametros));
    }

    /** El hardware está ocupado sólo mientras la secuencia está {@code EN_CURSO}; lee la foto, sin lock. */
    @Override
    public Optional<String> ocupadoPor() {
        Secuencia f = foto;
        return f != null && EN_CURSO.equals(f.estado())
                ? Optional.of("Hay una secuencia de " + f.tipo().toLowerCase(Locale.ROOT) + " en curso.")
                : Optional.empty();
    }

    /**
     * Cancela la secuencia en curso. Riego y mediasombra: lo que falta se omite y arranca el paso
     * seguro (cerrar la válvula, enrollar la mediasombra); al cerrarse la secuencia queda CANCELADA. La
     * lectura no tiene nada que dejar seguro: queda CANCELADA al instante.
     */
    public synchronized Secuencia cancelar() {
        if (actual == null || !EN_CURSO.equals(actual.estado)) {
            throw new SecuenciaRechazadaException("No hay una secuencia en curso.");
        }
        if (actual.cancelacionSolicitada) {
            throw new SecuenciaRechazadaException("La secuencia ya se está cancelando.");
        }
        SecuenciaInterna p = actual;
        p.cancelacionSolicitada = true;
        log.info("Secuencia {} ({}): cancelación solicitada", p.id, p.tipo);

        if (LECTURA.equals(p.tipo)) {
            for (Paso s : p.pasos) {
                if (PENDIENTE.equals(s.estado) || EN_CURSO.equals(s.estado)) {
                    omitir(s, CANCELADA_POR_OPERADOR);
                }
            }
            finalizar(p);
        } else {
            Paso enCurso = enCurso(p);
            if (enCurso != null && !esSeguro(p, enCurso)) {
                omitir(enCurso, CANCELADA_POR_OPERADOR);
                omitirPendientesSalvoElSeguro(p, CANCELADA_POR_OPERADOR);
                entrar(p, ultimo(p));
            }
        }
        publicarFoto();
        return foto;
    }

    /** Llamado desde el hilo de Paho: sólo encola. Los ACK de comandos ajenos se descartan en el tick. */
    public void registrarAck(AckActuador ack) {
        if (ack != null) {
            acks.add(ack);
        }
    }

    /** Llamado desde el hilo de Paho: sólo encola, ya sellada con la hora de recepción. */
    public void registrarTelemetria(String zonaId, MqttTelemetryPayload payload, long recibidaEn) {
        if (zonaId != null && payload != null) {
            lecturas.add(new LecturaRecibida(zonaId, payload, recibidaEn));
        }
    }

    /** La última secuencia (en curso o terminada). Vacío si no hubo ninguna desde el arranque. */
    public Optional<Secuencia> estado() {
        return Optional.ofNullable(foto);
    }

    /** Un paso de la máquina por segundo, en su propio carril: no compite con el motor de reglas. */
    @Scheduled(fixedDelayString = "${yerbanalytics.secuencia.tick-ms:1000}", scheduler = "secuenciaScheduler")
    public synchronized void tick() {
        try {
            avanzar();
        } catch (RuntimeException e) {
            log.error("Secuencia: falló el tick — {}", e.getMessage(), e);
        }
    }

    // ==================================================================
    // Validación y arranque
    // ==================================================================

    /** Devuelve los parámetros normalizados (con defaults y sólo los que aplican al tipo). */
    private ParametrosSecuencia validar(IniciarSecuencia pedido) {
        String tipo = pedido == null ? null : pedido.tipo();
        ParametrosSecuencia pedidos = pedido == null || pedido.parametros() == null
                ? new ParametrosSecuencia(null, null) : pedido.parametros();
        if (tipo == null) {
            throw new SecuenciaInvalidaException("Falta el tipo de secuencia (RIEGO, MEDIASOMBRA o LECTURA).");
        }
        switch (tipo) {
            case RIEGO -> {
                int max = ContratoNodo.DURACION_VALVULA_MAX_SEG - props.getTimeoutAckValvulaSeg();
                int duracion = pedidos.duracionSeg() != null ? pedidos.duracionSeg() : DURACION_RIEGO_DEFECTO_SEG;
                if (duracion < 1 || duracion > max) {
                    throw new SecuenciaInvalidaException(
                            "duracionSeg tiene que estar entre 1 y " + max + " (recibí " + duracion + ").");
                }
                return new ParametrosSecuencia(duracion, null);
            }
            case MEDIASOMBRA -> {
                int espera = pedidos.esperaSeg() != null ? pedidos.esperaSeg() : ESPERA_MEDIASOMBRA_DEFECTO_SEG;
                if (espera < 0 || espera > ESPERA_MEDIASOMBRA_MAX_SEG) {
                    throw new SecuenciaInvalidaException("esperaSeg tiene que estar entre 0 y "
                            + ESPERA_MEDIASOMBRA_MAX_SEG + " (recibí " + espera + ").");
                }
                return new ParametrosSecuencia(null, espera);
            }
            case LECTURA -> {
                return new ParametrosSecuencia(null, null);
            }
            default -> throw new SecuenciaInvalidaException(
                    "Tipo de secuencia desconocido: " + tipo + " (RIEGO, MEDIASOMBRA o LECTURA).");
        }
    }

    private synchronized Secuencia iniciarSecuencia(String tipo, ParametrosSecuencia parametros) {
        if (actual != null && EN_CURSO.equals(actual.estado)) {
            throw new SecuenciaRechazadaException("Ya hay una secuencia en curso.");
        }
        ZonaEntity zona = zonaDestino();
        String sectorId = null;
        if (!LECTURA.equals(tipo)) {
            sectorId = zona.getSectors().stream().map(SectorEntity::getId).min(Comparator.naturalOrder())
                    .orElseThrow(() -> new SecuenciaRechazadaException(
                            "La zona " + zona.getId() + " no tiene sectores."));
        }

        SecuenciaInterna p = new SecuenciaInterna(UUID.randomUUID().toString(), tipo, zona.getId(), sectorId,
                parametros, reloj.millis());
        switch (tipo) {
            case RIEGO -> {
                p.duracionValvulaSeg = parametros.duracionSeg() + props.getTimeoutAckValvulaSeg();
                p.pasos.add(new Paso(1, ABRIR));
                p.pasos.add(new Paso(2, ESPERAR));
                p.pasos.add(new Paso(3, CERRAR));
            }
            case MEDIASOMBRA -> {
                p.pasos.add(new Paso(1, DESPLEGAR));
                p.pasos.add(new Paso(2, ESPERAR));
                p.pasos.add(new Paso(3, ENROLLAR));
            }
            default -> {
                p.pasos.add(new Paso(1, PEDIR));
                p.pasos.add(new Paso(2, ESPERAR_TELEMETRIA));
                p.pasos.add(new Paso(3, MOSTRAR));
            }
        }
        actual = p;

        log.info("Secuencia {} ({}): iniciada en {}{}", p.id, tipo, p.zonaId, sectorId != null ? "/" + sectorId : "");
        entrar(p, p.pasos.get(0));
        publicarFoto();
        return foto;
    }

    /**
     * La macro-zona de menor número (orden numérico del sufijo, no lexicográfico: MZ-10 va después de
     * MZ-9), el mismo criterio que la pasada.
     */
    private ZonaEntity zonaDestino() {
        return zonaRepo.findAllWithSectors().stream()
                .min(Comparator.comparingInt((ZonaEntity z) -> numeroFinal(z.getId())).thenComparing(ZonaEntity::getId))
                .orElseThrow(() -> new SecuenciaRechazadaException("No hay topología configurada."));
    }

    private static int numeroFinal(String id) {
        Matcher m = NUMERO_FINAL.matcher(id);
        return m.find() ? Integer.parseInt(m.group(1)) : Integer.MAX_VALUE;
    }

    // ==================================================================
    // Máquina de estados
    // ==================================================================

    private void avanzar() {
        List<AckActuador> acksPendientes = new ArrayList<>();
        for (AckActuador a; (a = acks.poll()) != null; ) {
            acksPendientes.add(a);
        }
        List<LecturaRecibida> lecturasPendientes = new ArrayList<>();
        for (LecturaRecibida l; (l = lecturas.poll()) != null; ) {
            lecturasPendientes.add(l);
        }
        SecuenciaInterna p = actual;
        if (p == null || !EN_CURSO.equals(p.estado)) {
            return;   // lo que llegue de una secuencia terminada o perdida no le importa a nadie
        }

        Paso antes = enCurso(p);
        for (AckActuador a : acksPendientes) {
            aplicarAck(p, a);
            if (!EN_CURSO.equals(p.estado)) {
                break;
            }
        }
        // Un paso por tick: si el ACK cerró el paso, el siguiente ya arrancó y se revisa en el próximo
        // (así la espera arranca desde cero y un comando recién publicado no vence en el mismo tick).
        if (EN_CURSO.equals(p.estado) && antes != null && enCurso(p) == antes) {
            revisar(p, antes, lecturasPendientes);
        }
        publicarFoto();
    }

    /** Arranca un paso. Puede cerrarlo al instante (falla o paso sin espera) y encadenar el siguiente. */
    private void entrar(SecuenciaInterna p, Paso paso) {
        paso.estado = EN_CURSO;
        paso.iniciadoEn = reloj.millis();
        switch (paso.tipo) {
            case ABRIR -> publicarComando(p, paso, "valve", "ON", Map.of("durationSec", p.duracionValvulaSeg));
            case CERRAR -> publicarComando(p, paso, "valve", "OFF", Map.of());
            case DESPLEGAR -> publicarComando(p, paso, "shade", "SET", Map.of("targetPct", APERTURA_DESPLEGADA));
            case ENROLLAR -> publicarComando(p, paso, "shade", "SET", Map.of("targetPct", APERTURA_ENROLLADA));
            case ESPERAR -> paso.esperaHasta = reloj.millis() + esperaSeg(p) * 1000L;
            case PEDIR -> {
                p.pedidoEn = reloj.millis();
                ComandoZonaPublisher.Resultado r = zonaPublisher.leerAhora(p.zonaId);
                paso.commandId = r.commandId();
                if (r.publicado()) {
                    cerrarYSeguir(p, paso, OK, null, null);
                } else {
                    cerrarYSeguir(p, paso, ERROR, "PUBLICACION_FALLIDA", detallePublicacion(r.error()));
                }
            }
            case MOSTRAR -> cerrarYSeguir(p, paso, OK, null, null);
            default -> { /* ESPERAR_TELEMETRIA: espera a que llegue la lectura */ }
        }
    }

    private void publicarComando(SecuenciaInterna p, Paso paso, String actuador, String accion,
                                 Map<String, Object> parametros) {
        ComandoActuadorPublisher.Resultado r = actuadores.publicar(p.zonaId, p.sectorId, actuador, accion, parametros);
        paso.commandId = r.commandId();
        paso.publicadoEn = reloj.millis();
        if (!r.publicado()) {
            String detalle = detallePublicacion(r.error());
            cerrarYSeguir(p, paso, ERROR, "PUBLICACION_FALLIDA",
                    CERRAR.equals(paso.tipo) ? detalle + avisoDeApagadoSolo(p) : detalle);
        }
    }

    private void aplicarAck(SecuenciaInterna p, AckActuador ack) {
        Paso paso = enCurso(p);
        if (paso == null || paso.commandId == null || !esDeComando(paso)
                || !Objects.equals(paso.commandId, ack.commandId())) {
            log.debug("ACK ignorado (commandId {} no es el del paso en curso)", ack.commandId());
            return;
        }
        if (ContratoNodo.STATUS_SUCCESS.equals(ack.status())) {
            cerrarYSeguir(p, paso, OK, null, null);
            return;
        }
        // ERROR, o un status que no conocemos: se trata como ERROR con el tipo del detalle.
        String tipo = tipoDelDetalle(ack.detalle());
        String codigo = tipo != null ? tipo.toUpperCase(Locale.ROOT) : String.valueOf(ack.status());
        cerrarYSeguir(p, paso, ERROR, codigo, detalleDeError(p, paso, codigo, tipo));
    }

    private void revisar(SecuenciaInterna p, Paso paso, List<LecturaRecibida> recibidas) {
        long ahora = reloj.millis();
        switch (paso.tipo) {
            case ESPERAR -> {
                if (ahora >= paso.esperaHasta) {
                    cerrarYSeguir(p, paso, OK, null, null);
                }
            }
            case ESPERAR_TELEMETRIA -> revisarLectura(p, paso, recibidas);
            default -> {
                int timeoutSeg = timeoutAckSeg(paso);
                if (ahora - paso.publicadoEn >= timeoutSeg * 1000L) {
                    cerrarYSeguir(p, paso, ERROR, "ACTUADOR_SIN_RESPUESTA", detalleDeError(p, paso,
                            "ACTUADOR_SIN_RESPUESTA", null));
                }
            }
        }
    }

    private void revisarLectura(SecuenciaInterna p, Paso paso, List<LecturaRecibida> recibidas) {
        for (LecturaRecibida l : recibidas) {
            // Una telemetría de otra zona, o anterior al pedido, no es la respuesta a este pedido.
            if (p.zonaId.equals(l.zonaId) && l.recibidaEn >= p.pedidoEn) {
                // Inmutable: la foto la comparte con los hilos que leen sin lock.
                p.lectura = new LecturaSecuencia(l.recibidaEn, Collections.unmodifiableMap(metricasDe(l.payload)));
                cerrarYSeguir(p, paso, OK, null, null);
                return;
            }
        }
        if (reloj.millis() - paso.iniciadoEn >= props.getTimeoutLecturaSeg() * 1000L) {
            cerrarYSeguir(p, paso, ERROR, "SIN_LECTURA", "La zona " + p.zonaId + " no publicó una lectura en "
                    + props.getTimeoutLecturaSeg() + " s. ¿El nodo tiene sensores configurados?");
        }
    }

    /** Cierra el paso y aplica la transición de la secuencia. */
    private void cerrarYSeguir(SecuenciaInterna p, Paso paso, String estado, String codigo, String detalle) {
        cerrar(paso, estado, codigo, detalle);
        if (ERROR.equals(estado) && p.error == null) {
            p.error = detalle;
        }
        if (LECTURA.equals(p.tipo)) {
            if (ERROR.equals(estado)) {
                omitirPendientes(p, null);
                finalizar(p);
            } else if (paso == ultimo(p)) {
                finalizar(p);
            } else {
                entrar(p, p.pasos.get(paso.n));
            }
        } else if (esSeguro(p, paso)) {
            finalizar(p);   // el paso seguro no se reintenta, salga como salga
        } else if (ERROR.equals(estado)) {
            // Se cierra igual: un ACK perdido no prueba que la bomba no arrancó.
            omitirPendientesSalvoElSeguro(p, null);
            entrar(p, ultimo(p));
        } else {
            entrar(p, p.pasos.get(paso.n));
        }
    }

    private void cerrar(Paso paso, String estado, String codigo, String detalle) {
        paso.estado = estado;
        paso.codigoError = codigo;
        paso.detalle = detalle;
        paso.terminadoEn = reloj.millis();
    }

    private void omitir(Paso paso, String detalle) {
        cerrar(paso, OMITIDO, null, detalle);
    }

    private void omitirPendientes(SecuenciaInterna p, String detalle) {
        for (Paso s : p.pasos) {
            if (PENDIENTE.equals(s.estado)) {
                s.estado = OMITIDO;
                s.detalle = detalle;
            }
        }
    }

    private void omitirPendientesSalvoElSeguro(SecuenciaInterna p, String detalle) {
        for (Paso s : p.pasos) {
            if (PENDIENTE.equals(s.estado) && !esSeguro(p, s)) {
                s.estado = OMITIDO;
                s.detalle = detalle;
            }
        }
    }

    private void finalizar(SecuenciaInterna p) {
        p.finalizadaEn = reloj.millis();
        boolean hayError = p.pasos.stream().anyMatch(s -> ERROR.equals(s.estado));
        p.estado = p.cancelacionSolicitada ? CANCELADA : hayError ? FALLIDA : COMPLETADA;
        log.info("Secuencia {} ({}): {}{}", p.id, p.tipo, p.estado, p.error != null ? " — " + p.error : "");
    }

    // ==================================================================
    // Detalles legibles y métricas
    // ==================================================================

    /** El último paso de riego y mediasombra (cerrar / enrollar) es el que deja el actuador seguro. */
    private static boolean esSeguro(SecuenciaInterna p, Paso paso) {
        return !LECTURA.equals(p.tipo) && paso == ultimo(p);
    }

    private static boolean esDeComando(Paso paso) {
        return switch (paso.tipo) {
            case ABRIR, CERRAR, DESPLEGAR, ENROLLAR -> true;
            default -> false;
        };
    }

    private static boolean esValvula(Paso paso) {
        return ABRIR.equals(paso.tipo) || CERRAR.equals(paso.tipo);
    }

    private int timeoutAckSeg(Paso paso) {
        return esValvula(paso) ? props.getTimeoutAckValvulaSeg() : props.getTimeoutAckMediasombraSeg();
    }

    private static int esperaSeg(SecuenciaInterna p) {
        return RIEGO.equals(p.tipo) ? p.parametros.duracionSeg() : p.parametros.esperaSeg();
    }

    private static String tipoDelDetalle(JsonNode detalle) {
        if (detalle == null || !detalle.isObject()) {
            return null;
        }
        JsonNode tipo = detalle.get("tipo");
        return tipo == null || tipo.isNull() ? null : tipo.asText();
    }

    private static String detallePublicacion(String error) {
        return "No se pudo publicar el comando al broker: " + error;
    }

    /** Texto legible del error (design §2.5). Para CERRAR avisa que el nodo apaga la bomba solo. */
    private String detalleDeError(SecuenciaInterna p, Paso paso, String codigo, String tipoOriginal) {
        String detalle = switch (codigo) {
            case "ACTUADOR_SIN_RESPUESTA" -> (esValvula(paso) ? "La válvula" : "La mediasombra")
                    + " no respondió en " + timeoutAckSeg(paso)
                    + " s. ¿El ESP32 está encendido y conectado al broker?";
            case "FALLA_MECANICA" -> "La mediasombra no llegó al final de carrera.";
            case "REEMPLAZADO" -> "El movimiento fue interrumpido por otro comando.";
            case "COMANDO_INVALIDO", "DURACION_INVALIDA", "ACTUADOR_DESCONOCIDO" ->
                    "El ESP32 rechazó el comando (" + tipoOriginal + ").";
            default -> "El actuador informó un error: " + Objects.toString(tipoOriginal, codigo);
        };
        return CERRAR.equals(paso.tipo) ? detalle + avisoDeApagadoSolo(p) : detalle;
    }

    private static String avisoDeApagadoSolo(SecuenciaInterna p) {
        return " El nodo apaga la bomba solo a los " + p.duracionValvulaSeg + " s.";
    }

    private static Map<String, Double> metricasDe(MqttTelemetryPayload payload) {
        MqttTelemetryPayload.MetricsPayload m = payload.metrics();
        Map<String, Double> valores = new LinkedHashMap<>();
        for (String clave : METRICAS) {
            valores.put(clave, null);
        }
        if (m != null) {
            valores.put("humSus", m.humSus());
            valores.put("humAmb", m.humAmb());
            valores.put("temp", m.temp());
            valores.put("tempSuelo", m.tempSuelo());
            valores.put("uv", m.uv());                                   // % de luz, no índice UV
            valores.put("ce", ContratoNodo.ceADsPorM(m.ce()));           // el nodo manda µS/cm
            valores.put("phSuelo", m.phSuelo());
            valores.put("n", m.n());
            valores.put("p", m.p());
            valores.put("k", m.k());
        }
        return valores;
    }

    private static Paso enCurso(SecuenciaInterna p) {
        return p.pasos.stream().filter(s -> EN_CURSO.equals(s.estado)).findFirst().orElse(null);
    }

    private static Paso ultimo(SecuenciaInterna p) {
        return p.pasos.get(p.pasos.size() - 1);
    }

    private void publicarFoto() {
        SecuenciaInterna p = actual;
        List<PasoSecuencia> pasos = new ArrayList<>();
        for (Paso s : p.pasos) {
            pasos.add(new PasoSecuencia(s.n, s.tipo, s.estado, s.codigoError, s.detalle, s.commandId,
                    s.esperaHasta, s.iniciadoEn, s.terminadoEn));
        }
        foto = new Secuencia(p.id, p.tipo, p.estado, p.zonaId, p.sectorId, p.parametros, p.iniciadaEn,
                p.finalizadaEn, p.cancelacionSolicitada, p.error, p.lectura, List.copyOf(pasos));
    }

    // ==================================================================
    // Estado interno (mutable, sólo se toca con el lock)
    // ==================================================================

    private record LecturaRecibida(String zonaId, MqttTelemetryPayload payload, long recibidaEn) {
    }

    private static final class SecuenciaInterna {
        final String id;
        final String tipo;
        final String zonaId;
        final String sectorId;
        final ParametrosSecuencia parametros;
        final long iniciadaEn;
        final List<Paso> pasos = new ArrayList<>();
        String estado = EN_CURSO;
        Long finalizadaEn;
        boolean cancelacionSolicitada;
        String error;
        LecturaSecuencia lectura;
        /** durationSec del {@code valve ON}: el nodo apaga solo a ese plazo. Sólo riego. */
        int duracionValvulaSeg;
        /** Hora del pedido de lectura; la respuesta es la primera telemetría recibida desde acá. */
        long pedidoEn;

        SecuenciaInterna(String id, String tipo, String zonaId, String sectorId, ParametrosSecuencia parametros,
                         long iniciadaEn) {
            this.id = id;
            this.tipo = tipo;
            this.zonaId = zonaId;
            this.sectorId = sectorId;
            this.parametros = parametros;
            this.iniciadaEn = iniciadaEn;
        }
    }

    private static final class Paso {
        final int n;
        final String tipo;
        String estado = PENDIENTE;
        String codigoError;
        String detalle;
        String commandId;
        Long esperaHasta;
        Long iniciadoEn;
        Long terminadoEn;
        long publicadoEn;

        Paso(int n, String tipo) {
            this.n = n;
            this.tipo = tipo;
        }
    }
}
