package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.config.CapturaProperties;
import com.yerbanalytics.backend.constant.NurseryConstants;
import com.yerbanalytics.backend.dto.Diagnostico;
import com.yerbanalytics.backend.model.CapturaEntity;
import com.yerbanalytics.backend.model.DiagnosticoEntity;
import com.yerbanalytics.backend.repository.CapturaRepository;
import com.yerbanalytics.backend.repository.DiagnosticoRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Alta y consulta de diagnósticos.
 *
 * <p><strong>Camino único de escritura.</strong> Este servicio es el único lugar por el que
 * entra un diagnóstico al sistema, y no distingue quién lo emite. Hoy lo llama el panel de
 * simulación con valores cargados a mano; mañana lo llamará el servicio de inferencia con la
 * salida del modelo. Es la misma operación, con las mismas validaciones y el mismo resultado.
 *
 * <p>Por eso <strong>no hay marca de origen</strong>: una bandera que distinguiera el ensayo
 * de la operación real volvería infiel el ensayo. El recorrido que se prueba a mano hoy es
 * literalmente el que va a correr solo.
 *
 * <p>El modelo es Keras/Python y no corre dentro del JVM aunque comparta la máquina, así que
 * el diagnóstico va a entrar por HTTP de todas formas: este camino existe con o sin simulador.
 */
@Service
public class DiagnosticoService {

    /** Taxonomía de la plataforma. Un estado fuera de acá se rechaza con 400. */
    public static final Set<String> ESTADOS = Set.of(
            "Sano", "Clorosis", "Estrés solar", "Daño biótico", "No concluyente",
            "Ácaro", "Plaga foliar", "Daño fúngico");

    private final DiagnosticoRepository repo;
    private final CapturaRepository capturaRepo;
    private final SectorRepository sectorRepo;
    private final CapturaProperties props;

    public DiagnosticoService(DiagnosticoRepository repo,
                              CapturaRepository capturaRepo,
                              SectorRepository sectorRepo,
                              CapturaProperties props) {
        this.repo = repo;
        this.capturaRepo = capturaRepo;
        this.sectorRepo = sectorRepo;
        this.props = props;
    }

    /**
     * Da de alta un diagnóstico. El sector y la macro-zona pueden venir explícitos; si no, se
     * toman de la captura, que es lo habitual (una captura ya sabe de qué sector es).
     */
    @Transactional
    public Diagnostico alta(String capturaId, String sectorId, String zonaId,
                            String estado, Double conf, String sev) {

        if (capturaId == null || capturaId.isBlank()) {
            throw new IllegalArgumentException(
                    "La captura es obligatoria: todo diagnóstico nace del análisis de una imagen.");
        }
        CapturaEntity captura = capturaRepo.findById(capturaId.trim())
                .orElseThrow(() -> new IllegalArgumentException(
                        "La captura '" + capturaId + "' no existe."));

        if (estado == null || !ESTADOS.contains(estado.trim())) {
            throw new IllegalArgumentException(
                    "Estado fuera de la taxonomía. Los válidos son: "
                            + ESTADOS.stream().sorted().collect(Collectors.joining(", ")));
        }
        if (conf == null || conf < 0 || conf > 100) {
            throw new IllegalArgumentException("El nivel de confianza debe ser un porcentaje entre 0 y 100.");
        }

        String sectorFinal = (sectorId != null && !sectorId.isBlank())
                ? sectorId.trim() : captura.getSectorId();
        if (!sectorRepo.existsById(sectorFinal)) {
            throw new IllegalArgumentException(
                    "El sector '" + sectorFinal + "' no existe en la topología vigente.");
        }
        String zonaFinal = (zonaId != null && !zonaId.isBlank())
                ? zonaId.trim() : captura.getZonaId();

        String sevFinal = (sev != null && !sev.isBlank()) ? sev.trim() : NurseryConstants.EMDASH;

        DiagnosticoEntity e = new DiagnosticoEntity();
        e.setId(siguienteId());
        e.setSectorId(sectorFinal);
        e.setZonaId(zonaFinal);
        e.setCapturaId(captura.getId());
        e.setEstado(estado.trim());
        e.setConf(conf);
        e.setSev(sevFinal);
        e.setCreadoEn(System.currentTimeMillis());
        repo.save(e);

        return aDto(e);
    }

    @Transactional(readOnly = true)
    public List<Diagnostico> listar() {
        List<Diagnostico> out = new ArrayList<>();
        for (DiagnosticoEntity e : repo.findAllByOrderByCreadoEnDesc()) {
            out.add(aDto(e));
        }
        return out;
    }

    /** Los persistidos, del más reciente al más antiguo. Lo consume {@code NurseryService}. */
    @Transactional(readOnly = true)
    public List<DiagnosticoEntity> entidadesRecientes() {
        return repo.findAllByOrderByCreadoEnDesc();
    }

    /** Umbral de confianza mínima para considerar el diagnóstico concluyente (HU-04 CA-03). */
    public boolean esConcluyente(double conf) {
        return conf >= props.getConfianzaMinima();
    }

    private Diagnostico aDto(DiagnosticoEntity e) {
        return new Diagnostico(
                e.getId(), e.getSectorId(), e.getZonaId(), e.getCapturaId(),
                CapturaService.imagenUrl(e.getCapturaId()),
                e.getEstado(), e.getConf(), e.getSev(),
                esConcluyente(e.getConf()), e.getCreadoEn());
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
