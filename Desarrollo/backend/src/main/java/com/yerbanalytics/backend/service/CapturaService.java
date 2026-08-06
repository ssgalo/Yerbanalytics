package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.config.CapturaProperties;
import com.yerbanalytics.backend.dto.CapturaCreada;
import com.yerbanalytics.backend.dto.EstadoOrden;
import com.yerbanalytics.backend.dto.OrdenCapturaDto;
import com.yerbanalytics.backend.model.CapturaEntity;
import com.yerbanalytics.backend.model.OrdenCapturaEntity;
import com.yerbanalytics.backend.model.OrdenCapturaEntity.Estado;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.repository.CapturaRepository;
import com.yerbanalytics.backend.repository.OrdenCapturaRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Ciclo de vida de la orden de captura (HU-04 CA-01).
 *
 * <p>La orden es la entidad de primera clase, no la imagen: es lo que permite saber en qué
 * posición del riel se tomó una foto. La correlación es <strong>explícita</strong> — la
 * subida cita el {@code ordenId} — y no implícita por sector y timestamp, que sería frágil:
 * dos órdenes seguidas sobre el mismo sector se volverían indistinguibles y el reloj del
 * dispositivo no es autoridad.
 *
 * <p>Este servicio no sabe quién emite las órdenes. Hoy las emite el panel de simulación;
 * mañana un planificador o el motor de reglas. Todos entran por {@link #emitirOrden}.
 */
@Service
public class CapturaService {

    private static final Logger log = LoggerFactory.getLogger(CapturaService.class);

    /** Comentario de relleno para forzar el vaciado de buffers en proxies intermedios. */
    private static final String RELLENO_ANTIBUFFER = " ".repeat(2048);

    /** Conjunto cerrado de motivos del contrato. Uno fuera de la lista se rechaza con 400. */
    public static final Set<String> MOTIVOS_FALLO = Set.of(
            "CAMARA_NO_LISTA", "EXPORTACION_FALLIDA", "COLA_LLENA",
            "TIMEOUT_LOCAL", "ENVIO_AGOTADO", "ERROR_DESCONOCIDO");

    /**
     * Emisores SSE abiertos, por dispositivo. En memoria: una conexión no sobrevive a un
     * reinicio de todos modos, y el cliente reconecta solo. Lo que sí sobrevive es la orden,
     * que está en la base.
     */
    private final Map<String, SseEmitter> emisores = new ConcurrentHashMap<>();

    /**
     * Instante en que cada canal se abrió. Se usa para elegir destinatario: ante varios
     * canales abiertos, el más reciente es el que tiene evidencia más fresca de estar vivo.
     */
    private final Map<String, Long> aperturas = new ConcurrentHashMap<>();

    private final OrdenCapturaRepository ordenRepo;
    private final CapturaRepository capturaRepo;
    private final SectorRepository sectorRepo;
    private final AlmacenamientoImagenService almacenamiento;
    private final DispositivoCamaraService dispositivos;
    private final CapturaProperties props;

    public CapturaService(OrdenCapturaRepository ordenRepo,
                          CapturaRepository capturaRepo,
                          SectorRepository sectorRepo,
                          AlmacenamientoImagenService almacenamiento,
                          DispositivoCamaraService dispositivos,
                          CapturaProperties props) {
        this.ordenRepo = ordenRepo;
        this.capturaRepo = capturaRepo;
        this.sectorRepo = sectorRepo;
        this.almacenamiento = almacenamiento;
        this.dispositivos = dispositivos;
        this.props = props;
    }

    // ==================================================================
    // Emisión
    // ==================================================================

    /**
     * Crea una orden y la empuja al dispositivo si hay uno con el canal abierto. Si no lo hay,
     * la orden queda {@code PENDIENTE} y se drena cuando alguien conecte: una orden emitida
     * con el teléfono caído no se pierde.
     *
     * <p>Sin {@code @Transactional}: el despacho escribe en el stream y puede bloquear. Ver la
     * nota de {@link #drenarPendientes}. El alta es un único {@code save}, que Spring Data ya
     * ejecuta en su propia transacción.
     */
    public EstadoOrden emitirOrden(String sectorId, Integer posicionRiel) {
        if (sectorId == null || sectorId.isBlank()) {
            throw new IllegalArgumentException("El sector es obligatorio.");
        }
        if (posicionRiel == null) {
            throw new IllegalArgumentException("La posición de riel es obligatoria.");
        }
        SectorEntity sector = sectorRepo.findById(sectorId.trim())
                .orElseThrow(() -> new IllegalArgumentException(
                        "El sector '" + sectorId + "' no existe en la topología vigente."));

        long ahora = System.currentTimeMillis();
        OrdenCapturaEntity o = new OrdenCapturaEntity();
        o.setId(UUID.randomUUID().toString());
        o.setSectorId(sector.getId());
        o.setZonaId(sector.getZona().getId());
        o.setPosicionRiel(posicionRiel);
        o.setEstado(Estado.PENDIENTE);
        o.setIntentos(1);
        o.setCreadaEn(ahora);
        o.setVenceEn(ahora + props.getTimeoutOrdenSeg() * 1000L);
        ordenRepo.save(o);

        despachar(o);
        return aEstadoOrden(ordenRepo.findById(o.getId()).orElse(o));
    }

    // ==================================================================
    // Canal de órdenes (SSE)
    // ==================================================================

    /**
     * Abre el canal del dispositivo y le drena las órdenes pendientes, en orden de creación.
     *
     * <p>Se eligió SSE sobre MQTT-over-WebSockets y sobre un WebSocket propio porque es HTTP
     * plano —lo implementa igual un navegador que un cliente nativo—, trae la semántica de
     * reconexión definida por el protocolo, y el canal sólo va hacia abajo. Ver
     * {@code contratos/camara/v1/README.md}.
     */
    public SseEmitter abrirCanal(String dispositivoId) {
        SseEmitter previo = emisores.remove(dispositivoId);
        if (previo != null) {
            // Reconexión: el cliente abrió otro canal sin cerrar el anterior.
            previo.complete();
        }

        SseEmitter emitter = new SseEmitter(props.getSseTimeoutMs());
        emitter.onCompletion(() -> descolgar(dispositivoId, emitter));
        emitter.onTimeout(() -> descolgar(dispositivoId, emitter));
        emitter.onError(e -> descolgar(dispositivoId, emitter));
        emisores.put(dispositivoId, emitter);
        aperturas.put(dispositivoId, System.currentTimeMillis());

        // Un evento inmediato al abrir. Sin esto los headers de la respuesta no se envían
        // hasta el primer evento real, y un cliente puede quedarse hasta un keep-alive entero
        // sin saber si el canal quedó establecido: EventSource no dispara su 'open' y la UI
        // muestra "conectando" durante veinte segundos.
        try {
            // Relleno inicial. Varios proxies (túneles, CDNs, nginx con buffering) acumulan la
            // respuesta hasta juntar unos kilobytes antes de reenviar nada, y los eventos de
            // este canal son de decenas de bytes: la conexión queda abierta y muda, que es un
            // síntoma malísimo de diagnosticar. Un comentario SSE de 2 kB fuerza el vaciado.
            // Los clientes lo ignoran por definición del protocolo.
            emitter.send(SseEmitter.event().comment(RELLENO_ANTIBUFFER));
            emitter.send(SseEmitter.event().name("ping").data(Map.of("ts", System.currentTimeMillis())));
        } catch (Exception e) {
            descolgar(dispositivoId, emitter);
            emitter.completeWithError(e);
            return emitter;
        }

        log.info("Canal de órdenes abierto para {}", dispositivoId);
        drenarPendientes(dispositivoId);
        return emitter;
    }

    /**
     * Entrega al dispositivo las órdenes que quedaron esperando mientras estaba desconectado.
     *
     * <p><strong>Sin {@code @Transactional} a propósito.</strong> Escribir en el stream puede
     * bloquear —si el dispositivo dejó de leer, el buffer del socket se llena— y hacerlo
     * dentro de una transacción retendría una conexión JDBC durante todo ese bloqueo. Un solo
     * teléfono lento alcanzaría para agotar el pool y tumbar la plataforma entera. La lectura
     * de pendientes y cada actualización de estado son transacciones cortas e independientes;
     * el I/O ocurre entre medio, sin conexión tomada.
     */
    public void drenarPendientes(String dispositivoId) {
        for (OrdenCapturaEntity o : ordenRepo.findByEstadoOrderByCreadaEnAsc(Estado.PENDIENTE)) {
            entregar(o, dispositivoId);
        }
    }

    /** Keep-alive: sin esto, proxies y NAT cierran la conexión ociosa. */
    @Scheduled(fixedDelayString = "${yerbanalytics.capturas.sse-keep-alive-ms:20000}")
    public void latido() {
        long ts = System.currentTimeMillis();
        emisores.forEach((id, emitter) -> {
            try {
                emitter.send(SseEmitter.event().name("ping").data(Map.of("ts", ts)));
            } catch (Exception e) {
                descolgar(id, emitter);
            }
        });
    }

    /** Avisa a los dispositivos que la configuración cambió, para que la apliquen en caliente. */
    public void notificarConfig(Object config) {
        emisores.forEach((id, emitter) -> {
            try {
                emitter.send(SseEmitter.event().name("config").data(config));
            } catch (Exception e) {
                descolgar(id, emitter);
            }
        });
    }

    /**
     * Entrega la orden al primer canal que efectivamente la acepte.
     *
     * <p>No alcanza con tomar un emisor del mapa: un cliente que se fue sin cerrar deja su
     * emisor registrado, y el contenedor recién se entera cuando alguien intenta escribirle.
     * Entregarle una orden a un canal muerto la deja marcada como ENTREGADA a un dispositivo
     * que jamás la va a responder, y sólo se recupera cuando vence. Por eso se sondea antes:
     * si el canal no acepta ni un comentario, se descarta y se prueba con el siguiente.
     *
     * <p>Si no queda ninguno vivo, la orden se queda {@code PENDIENTE} y se drena cuando
     * alguien conecte.
     */
    private void despachar(OrdenCapturaEntity o) {
        // Del canal más reciente al más antiguo: el que conectó último es el que tiene
        // evidencia más fresca de estar vivo. Con un solo dispositivo —el caso del piloto—
        // el orden es irrelevante, pero evita entregarle órdenes a una sesión abandonada
        // cuando el operario reabre la app sin cerrar la anterior.
        List<String> porRecencia = emisores.keySet().stream()
                .sorted(Comparator.comparingLong(
                        (String id) -> aperturas.getOrDefault(id, 0L)).reversed())
                .toList();

        for (String dispositivoId : porRecencia) {
            if (!canalVivo(dispositivoId)) {
                continue;
            }
            entregar(o, dispositivoId);
            if (o.getEstado() == Estado.ENTREGADA) {
                return;
            }
        }
    }

    /** Sondea el canal con un comentario SSE; si falla, lo da de baja. */
    private boolean canalVivo(String dispositivoId) {
        SseEmitter emitter = emisores.get(dispositivoId);
        if (emitter == null) {
            return false;
        }
        try {
            emitter.send(SseEmitter.event().comment("probe"));
            return true;
        } catch (Exception e) {
            log.info("Canal de {} caído: se da de baja", dispositivoId);
            descolgar(dispositivoId, emitter);
            return false;
        }
    }

    private void entregar(OrdenCapturaEntity o, String dispositivoId) {
        SseEmitter emitter = emisores.get(dispositivoId);
        if (emitter == null) {
            return;
        }
        try {
            emitter.send(SseEmitter.event()
                    .name("orden")
                    .id(o.getId())
                    .data(new OrdenCapturaDto(
                            o.getId(), o.getSectorId(), o.getZonaId(), o.getPosicionRiel(),
                            o.getCreadaEn(), o.getVenceEn(), o.getIntentos())));

            o.setEstado(Estado.ENTREGADA);
            o.setDispositivoId(dispositivoId);
            o.setEntregadaEn(System.currentTimeMillis());
            // El plazo se cuenta desde la entrega, no desde la creación: una orden que esperó
            // dos horas a que el dispositivo conectara merece su plazo completo.
            o.setVenceEn(System.currentTimeMillis() + props.getTimeoutOrdenSeg() * 1000L);
            ordenRepo.save(o);
        } catch (IOException | IllegalStateException e) {
            log.warn("No se pudo entregar la orden {} a {}: {}", o.getId(), dispositivoId, e.getMessage());
            descolgar(dispositivoId, emitter);
        }
    }

    // ==================================================================
    // Recepción de la imagen
    // ==================================================================

    /**
     * Correlación estricta. Los rechazos son parte del contrato:
     * <ul>
     *   <li>404 — la orden no existe.</li>
     *   <li>409 — la orden ya tiene captura. Devuelve el {@code capturaId} existente, porque
     *       el contrato obliga al cliente a tratarlo como éxito: es el reintento tras un
     *       timeout de red cuya primera subida sí llegó.</li>
     *   <li>403 — la orden se entregó a otro dispositivo.</li>
     *   <li>422 — el hash no coincide; la imagen se descarta y la orden se reencola.</li>
     * </ul>
     *
     * <p>El {@code noRollbackFor} sobre {@link ImagenCorruptaException} no es un detalle: sin
     * él, la excepción revierte la transacción y con ella el reencolado de la orden, que
     * quedaría marcada como entregada sin imagen y sin reintento. Las demás excepciones de este
     * método (404, 409, 403) no modifican nada, así que sí deben revertir.
     */
    @Transactional(noRollbackFor = ImagenCorruptaException.class)
    public CapturaCreada recibirImagen(String ordenId, String dispositivoId, byte[] bytes,
                                       int ancho, int alto, String sha256Declarado,
                                       Long capturadaEn, String constraintsJson) {

        OrdenCapturaEntity o = ordenRepo.findById(ordenId)
                .orElseThrow(() -> new OrdenInexistenteException("La orden '" + ordenId + "' no existe."));

        // Idempotencia antes que nada: si ya hay captura, es un reintento y es un éxito.
        Optional<CapturaEntity> yaExiste = capturaRepo.findByOrdenId(ordenId);
        if (yaExiste.isPresent()) {
            CapturaEntity c = yaExiste.get();
            throw new OrdenYaResueltaException(
                    new CapturaCreada(c.getId(), ordenId, imagenUrl(c.getId())));
        }

        if (o.getDispositivoId() != null && !o.getDispositivoId().equals(dispositivoId)) {
            throw new DispositivoAjenoException(
                    "La orden fue entregada a otro dispositivo.");
        }
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("La imagen está vacía.");
        }
        if (sha256Declarado == null || sha256Declarado.isBlank()) {
            throw new IllegalArgumentException("El sha256 de la imagen es obligatorio.");
        }

        String sha256Real = AlmacenamientoImagenService.sha256(bytes);
        if (!sha256Real.equalsIgnoreCase(sha256Declarado.trim())) {
            // Corrupta en tránsito: se descarta y la orden vuelve al circuito de reintento.
            // Sin despacho inmediato: este método sí es transaccional (archivo + fila deben ir
            // juntos) y despachar escribiría en el stream con una conexión JDBC tomada. La
            // reentrega la hace el barrido de vencerOrdenes en el siguiente tick.
            reencolarOFallar(o, "ERROR_DESCONOCIDO",
                    "Hash declarado no coincide con los bytes recibidos", false);
            throw new ImagenCorruptaException(
                    "El hash de la imagen no coincide con el declarado. La orden se reintentará.");
        }

        long ahora = System.currentTimeMillis();
        String capturaId = siguienteCapturaId();

        // Archivo primero, fila después: un archivo huérfano es basura recolectable, pero una
        // fila apuntando a un archivo inexistente sería un 404 a la vista del usuario.
        String ruta;
        try {
            ruta = almacenamiento.guardar(capturaId, ahora, bytes);
        } catch (IOException e) {
            throw new AlmacenamientoFallidoException(
                    "No se pudo escribir la imagen en disco: " + e.getMessage());
        }

        CapturaEntity c = new CapturaEntity();
        c.setId(capturaId);
        c.setOrdenId(ordenId);
        c.setSectorId(o.getSectorId());
        c.setZonaId(o.getZonaId());
        c.setPosicionRiel(o.getPosicionRiel());
        c.setDispositivoId(dispositivoId != null ? dispositivoId : "desconocido");
        c.setAncho(ancho);
        c.setAlto(alto);
        c.setBytes((long) bytes.length);
        c.setSha256(sha256Real);
        c.setCapturadaEn(capturadaEn);
        c.setRecibidaEn(ahora);
        c.setRutaArchivo(ruta);
        c.setConstraintsJson(recortar(constraintsJson, 2000));
        capturaRepo.save(c);

        o.setEstado(Estado.RECIBIDA);
        ordenRepo.save(o);

        if (dispositivoId != null) {
            dispositivos.contarCaptura(dispositivoId, true);
        }
        if (constraintsJson != null && !constraintsJson.isBlank()) {
            // Evidencia para evaluar si la consistencia fotométrica alcanza para entrenar.
            log.info("Captura {} — constraints aplicados: {}", capturaId, constraintsJson);
        }
        return new CapturaCreada(capturaId, ordenId, imagenUrl(capturaId));
    }

    // ==================================================================
    // Fallos, vencimiento y reintentos
    // ==================================================================

    /** Sin {@code @Transactional}: el reencolado despacha, y despachar escribe en el stream. */
    public void acusarFallo(String ordenId, String dispositivoId, String motivo, String detalle) {
        if (motivo == null || !MOTIVOS_FALLO.contains(motivo)) {
            throw new IllegalArgumentException(
                    "Motivo de fallo desconocido. Los válidos son: "
                            + MOTIVOS_FALLO.stream().sorted().collect(Collectors.joining(", ")));
        }
        OrdenCapturaEntity o = ordenRepo.findById(ordenId)
                .orElseThrow(() -> new OrdenInexistenteException("La orden '" + ordenId + "' no existe."));

        if (o.getEstado().esTerminal()) {
            throw new OrdenTerminalException("La orden ya está en un estado terminal y no admite acuse.");
        }
        if (o.getDispositivoId() != null && dispositivoId != null
                && !o.getDispositivoId().equals(dispositivoId)) {
            throw new DispositivoAjenoException("La orden fue entregada a otro dispositivo.");
        }

        // Sin reentrega inmediata: el dispositivo acaba de decir que no pudo, y devolverle la
        // orden en el mismo instante sólo consigue que falle otra vez. Así los tres intentos
        // se agotaban en menos de un segundo y la orden terminaba en ERROR sin que el equipo
        // tuviera ninguna chance de recuperarse. La reentrega la hace el barrido periódico,
        // que además espacia los reintentos sin necesidad de un backoff propio.
        reencolarOFallar(o, motivo, detalle, false);
        if (dispositivoId != null) {
            dispositivos.contarCaptura(dispositivoId, false);
        }
    }

    /**
     * Barrido de órdenes vencidas. Corre periódicamente en vez de con un timer por orden:
     * así el vencimiento sobrevive a un reinicio del backend y no depende de que el
     * dispositivo avise. Un cliente que se cae sin decir nada no deja órdenes colgadas.
     */
    @Scheduled(fixedDelayString = "${yerbanalytics.capturas.watchdog-interval-ms:10000}")
    public void vencerOrdenes() {
        long ahora = System.currentTimeMillis();
        List<OrdenCapturaEntity> vencidas =
                ordenRepo.findByEstadoAndVenceEnLessThan(Estado.ENTREGADA, ahora);
        for (OrdenCapturaEntity o : vencidas) {
            log.info("Orden {} vencida sin imagen (intento {})", o.getId(), o.getIntentos());
            reencolarOFallar(o, "TIMEOUT_LOCAL", "Venció el plazo sin recibir imagen ni acuse");
        }

        // Red de seguridad: una orden puede quedar PENDIENTE sin haberse despachado nunca —
        // porque no había ningún canal abierto, o porque se reencoló desde un contexto que no
        // podía despachar (ver recibirImagen). Sin este barrido dependería de que alguien
        // reconecte para salir de ahí.
        if (!emisores.isEmpty()) {
            for (OrdenCapturaEntity o : ordenRepo.findByEstadoOrderByCreadaEnAsc(Estado.PENDIENTE)) {
                despachar(o);
            }
        }
    }

    /**
     * Devuelve la orden al circuito de reintento mientras queden intentos; agotados, la manda
     * al estado terminal {@code ERROR}. Los estados terminales no pasan por acá.
     */
    private void reencolarOFallar(OrdenCapturaEntity o, String motivo, String detalle) {
        reencolarOFallar(o, motivo, detalle, true);
    }

    private void reencolarOFallar(OrdenCapturaEntity o, String motivo, String detalle,
                                  boolean despacharAhora) {
        o.setMotivoFallo(motivo);
        o.setDetalleFallo(recortar(detalle, 500));

        if (o.getIntentos() >= props.getMaxIntentos()) {
            o.setEstado(Estado.ERROR);
            ordenRepo.save(o);
            log.warn("Orden {} agotó sus {} intentos — ERROR ({})", o.getId(), o.getIntentos(), motivo);
            return;
        }

        o.setIntentos(o.getIntentos() + 1);
        o.setEstado(Estado.PENDIENTE);
        o.setDispositivoId(null);
        o.setEntregadaEn(null);
        o.setVenceEn(System.currentTimeMillis() + props.getTimeoutOrdenSeg() * 1000L);
        ordenRepo.save(o);

        if (despacharAhora) {
            despachar(o);
        }
    }

    // ==================================================================
    // Consulta
    // ==================================================================

    @Transactional(readOnly = true)
    public EstadoOrden consultarOrden(String ordenId) {
        OrdenCapturaEntity o = ordenRepo.findById(ordenId)
                .orElseThrow(() -> new OrdenInexistenteException("La orden '" + ordenId + "' no existe."));
        return aEstadoOrden(o);
    }

    @Transactional(readOnly = true)
    public List<EstadoOrden> ultimasOrdenes() {
        List<EstadoOrden> out = new ArrayList<>();
        for (OrdenCapturaEntity o : ordenRepo.findTop50ByOrderByCreadaEnDesc()) {
            out.add(aEstadoOrden(o));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Optional<CapturaEntity> buscarCaptura(String capturaId) {
        return capturaRepo.findById(capturaId);
    }

    public byte[] leerImagen(CapturaEntity c) throws IOException {
        return almacenamiento.leer(c.getRutaArchivo());
    }

    public boolean hayCanalAbierto() {
        return !emisores.isEmpty();
    }

    /**
     * Cierra todos los canales abiertos. Los clientes reconectan solos y sus órdenes siguen en
     * la base, así que es una operación segura: sirve para un apagado ordenado y para forzar
     * que la flota vuelva a negociar el canal.
     */
    public void cerrarCanales() {
        emisores.values().forEach(SseEmitter::complete);
        emisores.clear();
        aperturas.clear();
    }

    /** Da de baja un canal, manteniendo emisores y aperturas consistentes. */
    private void descolgar(String dispositivoId, SseEmitter emitter) {
        if (emisores.remove(dispositivoId, emitter)) {
            aperturas.remove(dispositivoId);
        }
    }

    private EstadoOrden aEstadoOrden(OrdenCapturaEntity o) {
        String capturaId = capturaRepo.findByOrdenId(o.getId()).map(CapturaEntity::getId).orElse(null);
        return new EstadoOrden(
                o.getId(), o.getSectorId(), o.getZonaId(), o.getPosicionRiel(),
                o.getEstado().name(), o.getIntentos(), o.getMotivoFallo(), o.getDetalleFallo(),
                capturaId, capturaId != null ? imagenUrl(capturaId) : null,
                o.getCreadaEn(), o.getEntregadaEn(), o.getVenceEn());
    }

    public static String imagenUrl(String capturaId) {
        return "/api/capturas/" + capturaId + "/imagen";
    }

    private String siguienteCapturaId() {
        return String.format(Locale.US, "CAP-%06d", capturaRepo.count() + 1);
    }

    private static String recortar(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    // ------------------------------------------------------------------
    // Errores → códigos HTTP del contrato
    // ------------------------------------------------------------------

    /** 404 */
    public static class OrdenInexistenteException extends RuntimeException {
        public OrdenInexistenteException(String m) { super(m); }
    }

    /** 409 en la subida: lleva la captura ya existente, que el cliente trata como éxito. */
    public static class OrdenYaResueltaException extends RuntimeException {
        private final transient CapturaCreada captura;
        public OrdenYaResueltaException(CapturaCreada captura) {
            super("La orden ya recibió su imagen.");
            this.captura = captura;
        }
        public CapturaCreada getCaptura() { return captura; }
    }

    /** 409 en el acuse de fallo. */
    public static class OrdenTerminalException extends RuntimeException {
        public OrdenTerminalException(String m) { super(m); }
    }

    /** 403 */
    public static class DispositivoAjenoException extends RuntimeException {
        public DispositivoAjenoException(String m) { super(m); }
    }

    /** 422 */
    public static class ImagenCorruptaException extends RuntimeException {
        public ImagenCorruptaException(String m) { super(m); }
    }

    /** 500 */
    public static class AlmacenamientoFallidoException extends RuntimeException {
        public AlmacenamientoFallidoException(String m) { super(m); }
    }
}
