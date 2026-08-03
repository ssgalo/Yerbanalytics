package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.dto.DisposicionTopologia;
import com.yerbanalytics.backend.dto.NuevaTopologia;
import com.yerbanalytics.backend.dto.TopologiaVivero;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.TopologiaLayoutEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.repository.DispositivoRepository;
import com.yerbanalytics.backend.repository.HistorialRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
import com.yerbanalytics.backend.repository.TopologiaLayoutRepository;
import com.yerbanalytics.backend.repository.ZonaRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Generación dinámica de la topología del vivero (HU-18 CA-01). El Administrador define
 * N macro-zonas × M sectores y el servicio crea la grilla lógica con identificadores
 * únicos (`MZ-{z}` / `MZ-{z}-{NNN}`) y los defaults offline idénticos al seed, dejándola
 * disponible para el mapa de producción.
 *
 * <p>Genera sobre un vivero vacío; si ya hay topología, rechaza (409) salvo que el request
 * pida regenerar, en cuyo caso reemplaza la grilla limpiando las referencias colgantes
 * (dispositivos e historial referencian sector/zona por string, sin FK).
 */
@Service
public class TopologiaService {

    /** Límites operativos de la grilla (evitan generaciones absurdas). */
    static final int MAX_MACRO_ZONAS = 50;
    static final int MAX_SECTORES_POR_ZONA = 500;

    /** Identidad de la fila única de disposición visual. */
    private static final int LAYOUT_ID = 1;

    /** Disposición visual por defecto (reproduce la presentación previa al cambio). */
    static final int DEFAULT_MACRO_ZONAS_POR_FILA = 3;
    static final int DEFAULT_SECTORES_POR_FILA = 10;

    private static final String[] SUBS = {"Sector norte", "Sector centro", "Sector sur"};

    // Defaults offline, idénticos al seed de data.sql (sector "Fuera de servicio").
    private static final String STATUS_OFFLINE = "offline";
    private static final String COLOR_OFFLINE = "#A9B2AB";
    private static final String LABEL_OFFLINE = "Fuera de servicio";
    private static final String DIAG_OFFLINE = "Sin diagnóstico";
    private static final String SEV_OFFLINE = "—";
    private static final String VALVE_OFFLINE = "Cerrada";
    private static final String PUMP_OFFLINE = "En espera";

    private final ZonaRepository zonaRepository;
    private final SectorRepository sectorRepository;
    private final DispositivoRepository dispositivoRepository;
    private final HistorialRepository historialRepository;
    private final TopologiaLayoutRepository layoutRepository;

    public TopologiaService(ZonaRepository zonaRepository,
                            SectorRepository sectorRepository,
                            DispositivoRepository dispositivoRepository,
                            HistorialRepository historialRepository,
                            TopologiaLayoutRepository layoutRepository) {
        this.zonaRepository = zonaRepository;
        this.sectorRepository = sectorRepository;
        this.dispositivoRepository = dispositivoRepository;
        this.historialRepository = historialRepository;
        this.layoutRepository = layoutRepository;
    }

    @Transactional(readOnly = true)
    public TopologiaVivero getTopologia() {
        List<ZonaEntity> zonas = zonaRepository.findAll();
        long totalSectores = sectorRepository.count();
        int macroZonas = zonas.size();
        int sectoresPorMacroZona = macroZonas > 0 ? (int) (totalSectores / macroZonas) : 0;
        TopologiaLayoutEntity layout = loadLayout();
        return new TopologiaVivero(macroZonas, sectoresPorMacroZona, (int) totalSectores, macroZonas > 0,
                clamp(layout.getMacroZonasPorFila(), macroZonas),
                clamp(layout.getSectoresPorFila(), sectoresPorMacroZona));
    }

    /**
     * Actualiza la disposición visual por fila (HU-18 CA-01) sin tocar la grilla. Valida que los
     * valores sean enteros positivos dentro de los límites de la topología actual.
     */
    @Transactional
    public TopologiaVivero actualizarDisposicion(DisposicionTopologia dto) {
        long totalSectores = sectorRepository.count();
        int macroZonas = (int) zonaRepository.count();
        int sectoresPorMacroZona = macroZonas > 0 ? (int) (totalSectores / macroZonas) : 0;
        validarDisposicion(dto.macroZonasPorFila(), dto.sectoresPorFila(), macroZonas, sectoresPorMacroZona);

        TopologiaLayoutEntity layout = loadLayout();
        layout.setId(LAYOUT_ID);
        layout.setMacroZonasPorFila(dto.macroZonasPorFila());
        layout.setSectoresPorFila(dto.sectoresPorFila());
        layoutRepository.save(layout);

        return new TopologiaVivero(macroZonas, sectoresPorMacroZona, (int) totalSectores, macroZonas > 0,
                dto.macroZonasPorFila(), dto.sectoresPorFila());
    }

    /** Disposición visual actual (fila única); defaults si nunca se configuró. */
    private TopologiaLayoutEntity loadLayout() {
        return layoutRepository.findById(LAYOUT_ID).orElseGet(() ->
                new TopologiaLayoutEntity(LAYOUT_ID, DEFAULT_MACRO_ZONAS_POR_FILA, DEFAULT_SECTORES_POR_FILA));
    }

    /** Disposición acotada al rango válido [1, max]; con grilla vacía deja el valor configurado. */
    private static int clamp(int value, int max) {
        if (value < 1) {
            return 1;
        }
        return max > 0 ? Math.min(value, max) : value;
    }

    private void validarDisposicion(int macroZonasPorFila, int sectoresPorFila,
                                    int macroZonas, int sectoresPorMacroZona) {
        if (macroZonasPorFila <= 0 || (macroZonas > 0 && macroZonasPorFila > macroZonas)) {
            throw new TopologiaInvalidaException(
                    "Las macro-zonas por fila deben estar entre 1 y " + Math.max(1, macroZonas) + ".");
        }
        if (sectoresPorFila <= 0 || (sectoresPorMacroZona > 0 && sectoresPorFila > sectoresPorMacroZona)) {
            throw new TopologiaInvalidaException(
                    "Los sectores por fila deben estar entre 1 y " + Math.max(1, sectoresPorMacroZona) + ".");
        }
    }

    @Transactional
    public TopologiaVivero generar(NuevaTopologia dto) {
        int macroZonas = dto.macroZonas();
        int sectoresPorMacroZona = dto.sectoresPorMacroZona();
        if (macroZonas <= 0 || macroZonas > MAX_MACRO_ZONAS) {
            throw new TopologiaInvalidaException(
                    "La cantidad de macro-zonas debe estar entre 1 y " + MAX_MACRO_ZONAS + ".");
        }
        if (sectoresPorMacroZona <= 0 || sectoresPorMacroZona > MAX_SECTORES_POR_ZONA) {
            throw new TopologiaInvalidaException(
                    "La cantidad de sectores por macro-zona debe estar entre 1 y " + MAX_SECTORES_POR_ZONA + ".");
        }

        boolean existeTopologia = zonaRepository.count() > 0;
        if (existeTopologia && !dto.regenerar()) {
            throw new TopologiaConflictoException(
                    "El vivero ya tiene una topología cargada. Confirmá la regeneración para reemplazarla.");
        }
        if (existeTopologia) {
            // Borrado en bloque (una sentencia DELETE por tabla), no entidad por entidad:
            //  - es mucho más rápido → ventana de bloqueo mínima, sin quedarse "colgado" si un
            //    proceso en segundo plano (p. ej. la evaluación de historial) toca una fila;
            //  - evita el chequeo de versión por fila de deleteAll() que, ante una modificación
            //    o borrado concurrente, lanzaba ObjectOptimisticLockingFailureException.
            // El borrado en bloque no dispara el cascade JPA, así que se eliminan los sectores
            // explícitamente antes que las zonas (sector.zona_id → zona). Dispositivos e
            // historial referencian sector/zona por string (sin FK): se limpian aparte.
            dispositivoRepository.deleteAllInBatch();
            historialRepository.deleteAllInBatch();
            sectorRepository.deleteAllInBatch();
            zonaRepository.deleteAllInBatch();
            zonaRepository.flush();
        }

        List<ZonaEntity> zonas = new ArrayList<>();
        List<SectorEntity> sectores = new ArrayList<>();
        for (int z = 1; z <= macroZonas; z++) {
            String zonaId = "MZ-" + z;
            ZonaEntity zona = new ZonaEntity();
            zona.setId(zonaId);
            zona.setName("Macro-zona " + z);
            zona.setSub(SUBS[(z - 1) % SUBS.length]);
            zonas.add(zona);

            for (int n = 1; n <= sectoresPorMacroZona; n++) {
                sectores.add(nuevoSectorOffline(zona, zonaId, n));
            }
        }
        zonaRepository.saveAll(zonas);
        sectorRepository.saveAll(sectores);

        // Persiste la disposición visual del payload, acotada a las nuevas cantidades.
        int mzPorFila = clamp(dto.macroZonasPorFila() > 0 ? dto.macroZonasPorFila() : DEFAULT_MACRO_ZONAS_POR_FILA,
                macroZonas);
        int secPorFila = clamp(dto.sectoresPorFila() > 0 ? dto.sectoresPorFila() : DEFAULT_SECTORES_POR_FILA,
                sectoresPorMacroZona);
        TopologiaLayoutEntity layout = loadLayout();
        layout.setId(LAYOUT_ID);
        layout.setMacroZonasPorFila(mzPorFila);
        layout.setSectoresPorFila(secPorFila);
        layoutRepository.save(layout);

        return new TopologiaVivero(macroZonas, sectoresPorMacroZona, sectores.size(), true, mzPorFila, secPorFila);
    }

    /** Sector en estado offline, replicando los defaults del seed (id `MZ-{z}-{NNN}`). */
    private SectorEntity nuevoSectorOffline(ZonaEntity zona, String zonaId, int n) {
        String sectorId = String.format(Locale.US, "%s-%03d", zonaId, n);
        SectorEntity s = new SectorEntity();
        s.setId(sectorId);
        s.setZona(zona);
        s.setN(n);
        s.setStatus(STATUS_OFFLINE);
        s.setColor(COLOR_OFFLINE);
        s.setStatusLabel(LABEL_OFFLINE);
        s.setTip(sectorId + " · " + LABEL_OFFLINE);
        s.setReason(LABEL_OFFLINE);
        s.setDiagnosisEstado(DIAG_OFFLINE);
        s.setDiagnosisConf(null);
        s.setDiagnosisSev(SEV_OFFLINE);
        s.setActuadorValve(VALVE_OFFLINE);
        s.setActuadorPump(PUMP_OFFLINE);
        s.setActuadorShade(0);
        // Sin lecturas que inicializar: viven en la macro-zona, no en el sector.
        return s;
    }
}
