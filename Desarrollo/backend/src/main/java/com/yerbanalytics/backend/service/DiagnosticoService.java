package com.yerbanalytics.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yerbanalytics.backend.constant.NurseryConstants;
import com.yerbanalytics.backend.dto.DetectionsResponse;
import com.yerbanalytics.backend.dto.Detection;
import com.yerbanalytics.backend.dto.Diagnostico;
import com.yerbanalytics.backend.exception.DiagnosisNotFoundException;
import com.yerbanalytics.backend.model.CapturaEntity;
import com.yerbanalytics.backend.model.DiagnosticoEntity;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.repository.CapturaRepository;
import com.yerbanalytics.backend.repository.DiagnosticoRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Alta y consulta de diagnósticos.
 *
 * <p><strong>Camino único de escritura.</strong> Este servicio es el único lugar por el que
 * entra un diagnóstico al sistema, y no distingue quién lo emite. Lo llaman el simulador, con
 * valores cargados a mano, y el servicio de inferencia, con la salida del módulo de diagnóstico.
 * Es la misma operación, con las mismas validaciones y el mismo resultado.
 *
 * <p>Por eso <strong>no hay marca de origen</strong>: una bandera que distinguiera el ensayo
 * de la operación real volvería infiel el ensayo. El recorrido que se prueba a mano hoy es
 * literalmente el que corre solo.
 *
 * <p>El backend no recalcula el diagnóstico ni valida que "cumpla" el criterio agronómico: lo
 * elige el servicio de inferencia, y la carga manual debe poder registrar cualquier combinación
 * coherente (design D5). Lo que sí valida es la coherencia entre estado, severidad, nivel de
 * confianza y score (design D7).
 */
@Service
public class DiagnosticoService {

    public static final String SANO = "Sano";
    public static final String NO_CONCLUYENTE = "No concluyente";

    /** Taxonomía de la plataforma. Un estado fuera de acá se rechaza con 400. */
    public static final Set<String> ESTADOS = Set.of(
            SANO, "Clorosis", "Estrés solar", "Daño biótico", NO_CONCLUYENTE,
            "Ácaro", "Plaga foliar", "Daño fúngico");

    /** Estados anómalos: llevan severidad, nivel de confianza y score. */
    public static final Set<String> ESTADOS_ANOMALOS = Set.of(
            "Clorosis", "Estrés solar", "Daño biótico", "Ácaro", "Plaga foliar", "Daño fúngico");

    /** Niveles válidos de severidad y de confianza. */
    public static final List<String> NIVELES = List.of("Baja", "Media", "Alta");

    /** Tope de detecciones por alta: protege el tamaño de la fila (design, Risks). */
    public static final int MAX_DETECCIONES = 2000;

    private static final TypeReference<List<Detection>> LISTA_DETECCIONES = new TypeReference<>() {};
    private static final TypeReference<List<Double>> CAJA = new TypeReference<>() {};

    private final DiagnosticoRepository repo;
    private final CapturaRepository capturaRepo;
    private final SectorRepository sectorRepo;
    private final ObjectMapper json;

    public DiagnosticoService(DiagnosticoRepository repo,
                              CapturaRepository capturaRepo,
                              SectorRepository sectorRepo,
                              ObjectMapper json) {
        this.repo = repo;
        this.capturaRepo = capturaRepo;
        this.sectorRepo = sectorRepo;
        this.json = json;
    }

    /** Datos de un alta, tal como llegan del cuerpo de {@code POST /api/diagnosticos}. */
    public record NuevoDiagnostico(
            String capturaId, String sectorId, String zonaId,
            String estado, String sev, String confidence, Double conf,
            List<Detection> detections, List<Double> crop) {}

    /**
     * Da de alta un diagnóstico. El sector y la macro-zona pueden venir explícitos; si no, se
     * toman de la captura, que es lo habitual (una captura ya sabe de qué sector es).
     */
    @Transactional
    public Diagnostico alta(NuevoDiagnostico d) {
        if (d == null || d.capturaId() == null || d.capturaId().isBlank()) {
            throw new IllegalArgumentException(
                    "La captura es obligatoria: todo diagnóstico nace del análisis de una imagen.");
        }
        CapturaEntity captura = capturaRepo.findById(d.capturaId().trim())
                .orElseThrow(() -> new IllegalArgumentException(
                        "La captura '" + d.capturaId() + "' no existe."));

        if (d.estado() == null || !ESTADOS.contains(d.estado().trim())) {
            throw new IllegalArgumentException(
                    "Estado fuera de la taxonomía. Los válidos son: "
                            + ESTADOS.stream().sorted().collect(Collectors.joining(", ")));
        }
        String estado = d.estado().trim();
        String sev = sinSeveridad(d.sev()) ? null : d.sev().trim();
        String confidence = d.confidence() == null || d.confidence().isBlank() ? null : d.confidence().trim();
        validarCoherencia(estado, sev, confidence, d.conf());
        List<Detection> detecciones = validarDetecciones(d.detections());
        List<Double> crop = validarRecorte(d.crop());

        String sectorFinal = (d.sectorId() != null && !d.sectorId().isBlank())
                ? d.sectorId().trim() : captura.getSectorId();
        if (!sectorRepo.existsById(sectorFinal)) {
            throw new IllegalArgumentException(
                    "El sector '" + sectorFinal + "' no existe en la topología vigente.");
        }
        String zonaFinal = (d.zonaId() != null && !d.zonaId().isBlank())
                ? d.zonaId().trim() : captura.getZonaId();

        String sevFinal = sev != null ? sev : NurseryConstants.EMDASH;

        DiagnosticoEntity e = new DiagnosticoEntity();
        e.setId(siguienteId());
        e.setSectorId(sectorFinal);
        e.setZonaId(zonaFinal);
        e.setCapturaId(captura.getId());
        e.setEstado(estado);
        e.setConf(d.conf());
        e.setSev(sevFinal);
        e.setConfidence(confidence);
        e.setDetections(aJson(detecciones));
        e.setDetectionCount(detecciones.size());
        e.setCrop(crop != null ? aJson(crop) : null);
        e.setCreadoEn(System.currentTimeMillis());
        repo.save(e);

        // El diagnóstico del sector sólo lo cambia el alta (la ingesta de telemetría no lo toca). La hora de
        // llegada es la que el motor compara con la del último usado para saber si es nuevo.
        SectorEntity sector = sectorRepo.findById(sectorFinal)
                .orElseThrow(() -> new IllegalArgumentException("Sector no encontrado"));
        sector.setDiagnosisEstado(estado);
        sector.setDiagnosisConf(d.conf());
        sector.setDiagnosisSev(sevFinal);
        sector.setDiagnosisConfidence(confidence);
        sector.setDiagnosisTs(e.getCreadoEn());
        sectorRepo.save(sector);

        return aDto(e, CapturaService.imagenUrl(captura));
    }

    @Transactional(readOnly = true)
    public List<Diagnostico> listar() {
        List<DiagnosticoEntity> todos = repo.findAllByOrderByCreadoEnDesc();
        Map<String, String> urls = imagenUrls(todos);
        List<Diagnostico> out = new ArrayList<>();
        for (DiagnosticoEntity e : todos) {
            out.add(aDto(e, urls.get(e.getCapturaId())));
        }
        return out;
    }

    /**
     * Ruta versionada de la imagen de cada diagnóstico, por id de captura, en una sola consulta.
     * Una captura que ya no existe no figura: su diagnóstico queda sin imagen y la vista usa el
     * respaldo visual.
     */
    @Transactional(readOnly = true)
    public Map<String, String> imagenUrls(Collection<DiagnosticoEntity> diagnosticos) {
        Set<String> ids = diagnosticos.stream().map(DiagnosticoEntity::getCapturaId).collect(Collectors.toSet());
        Map<String, String> out = new HashMap<>();
        capturaRepo.findAllById(ids).forEach(c -> out.put(c.getId(), CapturaService.imagenUrl(c)));
        return out;
    }

    /** Los persistidos, del más reciente al más antiguo. Lo consume {@code NurseryService}. */
    @Transactional(readOnly = true)
    public List<DiagnosticoEntity> entidadesRecientes() {
        return repo.findAllByOrderByCreadoEnDesc();
    }

    /**
     * Las detecciones de un diagnóstico persistido y su recorte. Un diagnóstico sin detecciones (carga
     * manual o anterior a la columna) devuelve la lista vacía, y uno sin recorte, {@code crop: null};
     * un id que no existe, {@link DiagnosisNotFoundException}.
     */
    @Transactional(readOnly = true)
    public DetectionsResponse detecciones(String id) {
        DiagnosticoEntity e = repo.findById(id)
                .orElseThrow(() -> new DiagnosisNotFoundException("El diagnóstico '" + id + "' no existe."));
        return new DetectionsResponse(deJson(e.getDetections(), LISTA_DETECCIONES, List.of()),
                deJson(e.getCrop(), CAJA, null));
    }

    /**
     * Concluyente = el estado no es No concluyente (design D9). La decisión de no concluir la toma
     * el módulo de diagnóstico; si el motor actúa o no con la confianza es asunto del motor.
     */
    public static boolean esConcluyente(String estado) {
        return estado != null && !NO_CONCLUYENTE.equals(estado);
    }

    // ------------------------------------------------------------------
    // Validaciones (design D7)
    // ------------------------------------------------------------------

    /** Ausente, {@code null}, en blanco o {@code "—"}: sin severidad. */
    private static boolean sinSeveridad(String sev) {
        return sev == null || sev.isBlank() || NurseryConstants.EMDASH.equals(sev.trim());
    }

    private static void validarCoherencia(String estado, String sev, String confidence, Double conf) {
        if (sev != null && !NIVELES.contains(sev)) {
            throw new IllegalArgumentException("La severidad debe ser Baja, Media o Alta (o no venir).");
        }
        if (confidence != null && !NIVELES.contains(confidence)) {
            throw new IllegalArgumentException("El nivel de confianza (confidence) debe ser Baja, Media o Alta.");
        }
        if (conf != null && (conf.isNaN() || conf < 0 || conf > 100)) {
            throw new IllegalArgumentException("El score (conf) debe ser un porcentaje entre 0 y 100.");
        }

        if (NO_CONCLUYENTE.equals(estado)) {
            if (sev != null || confidence != null || conf != null) {
                throw new IllegalArgumentException(
                        "Un diagnóstico No concluyente no lleva severidad (sev), nivel de confianza (confidence) ni score (conf).");
            }
            return;
        }
        if (confidence == null) {
            throw new IllegalArgumentException(
                    "Falta el nivel de confianza (confidence): es obligatorio salvo en No concluyente.");
        }
        if (conf == null) {
            throw new IllegalArgumentException(
                    "Falta el score (conf): es obligatorio salvo en No concluyente.");
        }
        if (SANO.equals(estado)) {
            if (sev != null) {
                throw new IllegalArgumentException("Un diagnóstico Sano no lleva severidad (sev).");
            }
        } else if (sev == null) {
            throw new IllegalArgumentException(
                    "Falta la severidad (sev): es obligatoria en un estado anómalo (" + estado + ").");
        }
    }

    private static List<Detection> validarDetecciones(List<Detection> detecciones) {
        if (detecciones == null) {
            return List.of();
        }
        if (detecciones.size() > MAX_DETECCIONES) {
            throw new IllegalArgumentException("Un diagnóstico admite como máximo " + MAX_DETECCIONES
                    + " detecciones (llegaron " + detecciones.size() + ").");
        }
        List<Detection> out = new ArrayList<>(detecciones.size());
        for (int i = 0; i < detecciones.size(); i++) {
            Detection det = detecciones.get(i);
            String donde = "La detección " + (i + 1);
            if (det == null) {
                throw new IllegalArgumentException(donde + " está vacía.");
            }
            String label = det.label() != null ? det.label().trim() : null;
            if (label == null || !ESTADOS.contains(label) || NO_CONCLUYENTE.equals(label)) {
                throw new IllegalArgumentException(donde + " tiene una clase fuera de la taxonomía (label): "
                        + det.label() + ".");
            }
            Double score = det.score();
            if (score == null || score.isNaN() || score < 0 || score > 1) {
                throw new IllegalArgumentException(donde + " debe tener un score entre 0 y 1.");
            }
            List<Double> box = det.box();
            if (!esCajaNormalizada(box)) {
                throw new IllegalArgumentException(donde
                        + " debe tener una caja (box) de cuatro coordenadas normalizadas entre 0 y 1.");
            }
            if (box.get(0) >= box.get(2) || box.get(1) >= box.get(3)) {
                throw new IllegalArgumentException(donde
                        + " tiene una caja inválida: debe cumplir x1 < x2 e y1 < y2.");
            }
            out.add(new Detection(label, score, List.copyOf(box)));
        }
        return out;
    }

    /**
     * El recorte es opcional (la carga manual no lo manda); si viene, tiene el formato de una caja:
     * {@code [x1, y1, x2, y2]} normalizado, con {@code x1 < x2} e {@code y1 < y2}.
     */
    private static List<Double> validarRecorte(List<Double> crop) {
        if (crop == null) {
            return null;
        }
        if (!esCajaNormalizada(crop) || crop.get(0) >= crop.get(2) || crop.get(1) >= crop.get(3)) {
            throw new IllegalArgumentException("El recorte (crop) debe ser [x1, y1, x2, y2] normalizado entre 0 y 1,"
                    + " con x1 < x2 e y1 < y2.");
        }
        return List.copyOf(crop);
    }

    private static boolean esCajaNormalizada(List<Double> box) {
        return box != null && box.size() == 4
                && box.stream().noneMatch(v -> v == null || v.isNaN() || v < 0 || v > 1);
    }

    // ------------------------------------------------------------------

    private String aJson(List<?> valor) {
        try {
            return json.writeValueAsString(valor);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudieron serializar las detecciones", ex);
        }
    }

    /** El recorte guardado con el diagnóstico, o {@code null} si no tiene. */
    public List<Double> recorte(DiagnosticoEntity e) {
        return deJson(e.getCrop(), CAJA, null);
    }

    private <T> T deJson(String valor, TypeReference<T> tipo, T vacio) {
        if (valor == null || valor.isBlank()) {
            return vacio;
        }
        try {
            return json.readValue(valor, tipo);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Las detecciones guardadas no son un JSON válido", ex);
        }
    }

    private Diagnostico aDto(DiagnosticoEntity e, String imagenUrl) {
        return new Diagnostico(
                e.getId(), e.getSectorId(), e.getZonaId(), e.getCapturaId(),
                imagenUrl,
                e.getEstado(), e.getConf(), e.getSev(),
                esConcluyente(e.getEstado()), e.getCreadoEn(),
                e.getConfidence(), e.getDetectionCount());
    }

    /**
     * Prefijo distinto al {@code DG-###} que genera {@code NurseryService} para las cards
     * derivadas de sectores: los dos conjuntos conviven en la misma lista y sus identificadores
     * no deben colisionar.
     */
    private String siguienteId() {
        return String.format(Locale.US, "DX-%05d", repo.count() + 1);
    }
}
