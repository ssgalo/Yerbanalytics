package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.dto.DiagnosisCriteria;
import com.yerbanalytics.backend.dto.SeverityBand;
import com.yerbanalytics.backend.exception.InvalidDiagnosisCriteriaException;
import com.yerbanalytics.backend.model.DiagnosisCriteriaEntity;
import com.yerbanalytics.backend.repository.DiagnosisCriteriaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Criterio agronómico del diagnóstico: sembrado, lectura, validación y registro (design D5). */
@DisplayName("DiagnosisCriteriaService")
class DiagnosisCriteriaServiceTest {

    /** Base en memoria: el repositorio guarda en un mapa. */
    private final Map<String, DiagnosisCriteriaEntity> tabla = new HashMap<>();
    private DiagnosisCriteriaRepository repo;
    private HistorialService historial;
    private DiagnosisCriteriaService service;

    @BeforeEach
    void setUp() {
        repo = mock(DiagnosisCriteriaRepository.class);
        historial = mock(HistorialService.class);
        when(repo.count()).thenAnswer(inv -> (long) tabla.size());
        when(repo.findAll()).thenAnswer(inv -> new ArrayList<>(tabla.values()));
        when(repo.save(any(DiagnosisCriteriaEntity.class))).thenAnswer(inv -> {
            DiagnosisCriteriaEntity e = inv.getArgument(0);
            tabla.put(e.getState(), e);
            return e;
        });
        when(repo.saveAll(any())).thenAnswer(inv -> {
            Iterable<DiagnosisCriteriaEntity> it = inv.getArgument(0);
            it.forEach(e -> tabla.put(e.getState(), e));
            return it;
        });
        service = new DiagnosisCriteriaService(repo, historial);
    }

    private static DiagnosisCriteria criterio(List<SeverityBand> bandas, List<String> criticidad) {
        return new DiagnosisCriteria(bandas, criticidad);
    }

    private static List<SeverityBand> bandasPorDefecto() {
        return new ArrayList<>(List.of(
                new SeverityBand("Clorosis", 5, 10, 20),
                new SeverityBand("Estrés solar", 5, 10, 20),
                new SeverityBand("Plaga foliar", 2, 5, 10),
                new SeverityBand("Daño fúngico", 2, 4, 8)));
    }

    private static final List<String> CRITICIDAD = List.of("Daño fúngico", "Plaga foliar", "Clorosis", "Estrés solar");

    private void rechaza(DiagnosisCriteria c) {
        assertThatThrownBy(() -> service.replace(c, "Ana")).isInstanceOf(InvalidDiagnosisCriteriaException.class);
        verify(historial, never()).registrarCriterioDiagnostico(anyString(), anyString());
    }

    @Test
    void baseVacia_siembraLosValoresPorDefecto() {
        assertThat(service.seedIfEmpty()).isTrue();

        assertThat(tabla).hasSize(4);
        assertThat(tabla.get("Daño fúngico").getMinCount()).isEqualTo(2);
        assertThat(tabla.get("Daño fúngico").getMediumFrom()).isEqualTo(4);
        assertThat(tabla.get("Daño fúngico").getHighFrom()).isEqualTo(8);
        assertThat(tabla.get("Daño fúngico").getCriticalityRank()).isEqualTo(1);
        assertThat(tabla.get("Estrés solar").getCriticalityRank()).isEqualTo(4);

        DiagnosisCriteria c = service.get();
        assertThat(c.severityBands()).extracting(SeverityBand::state)
                .containsExactly("Clorosis", "Estrés solar", "Plaga foliar", "Daño fúngico");
        assertThat(c.severityBands().get(2)).isEqualTo(new SeverityBand("Plaga foliar", 2, 5, 10));
        assertThat(c.criticality()).containsExactlyElementsOf(CRITICIDAD);
    }

    @Test
    void criterioYaEditado_noSePisa() {
        tabla.put("Clorosis", new DiagnosisCriteriaEntity("Clorosis", 7, 12, 30, 1));
        assertThat(service.seedIfEmpty()).isFalse();
        assertThat(tabla.get("Clorosis").getMinCount()).isEqualTo(7);
    }

    @Test
    void sinSembrar_laLecturaDevuelveLosPorDefecto() {
        DiagnosisCriteria c = service.get();
        assertThat(c.severityBands()).hasSize(4);
        assertThat(c.criticality()).containsExactlyElementsOf(CRITICIDAD);
    }

    @Test
    void reemplazoValido_guardaDevuelveYRegistraLaRecalibracion() {
        service.seedIfEmpty();
        List<SeverityBand> bandas = bandasPorDefecto();
        bandas.set(3, new SeverityBand("Daño fúngico", 2, 3, 8));
        // Llegan en otro orden: salen en el fijo.
        java.util.Collections.reverse(bandas);
        List<String> criticidad = List.of("Daño fúngico", "Clorosis", "Plaga foliar", "Estrés solar");

        DiagnosisCriteria guardado = service.replace(criterio(bandas, criticidad), "Ana");

        assertThat(guardado.severityBands()).extracting(SeverityBand::state)
                .containsExactly("Clorosis", "Estrés solar", "Plaga foliar", "Daño fúngico");
        assertThat(guardado.severityBands().get(3)).isEqualTo(new SeverityBand("Daño fúngico", 2, 3, 8));
        assertThat(guardado.criticality()).containsExactlyElementsOf(criticidad);
        assertThat(tabla.get("Clorosis").getCriticalityRank()).isEqualTo(2);

        ArgumentCaptor<String> detalle = ArgumentCaptor.forClass(String.class);
        verify(historial).registrarCriterioDiagnostico(eq("Ana"), detalle.capture());
        assertThat(detalle.getValue()).contains("Daño fúngico 2/3/8").contains("Daño fúngico > Clorosis");
    }

    @Test
    void bandasNoCrecientes_400() {
        List<SeverityBand> bandas = bandasPorDefecto();
        bandas.set(0, new SeverityBand("Clorosis", 10, 5, 20));
        rechaza(criterio(bandas, CRITICIDAD));
    }

    @Test
    void mediaIgualAAlta_400() {
        List<SeverityBand> bandas = bandasPorDefecto();
        bandas.set(0, new SeverityBand("Clorosis", 5, 20, 20));
        rechaza(criterio(bandas, CRITICIDAD));
    }

    @Test
    void minimoCero_400() {
        List<SeverityBand> bandas = bandasPorDefecto();
        bandas.set(0, new SeverityBand("Clorosis", 0, 10, 20));
        rechaza(criterio(bandas, CRITICIDAD));
    }

    @Test
    void valorNoEntero_400() {
        List<SeverityBand> bandas = bandasPorDefecto();
        bandas.set(0, new SeverityBand("Clorosis", 2.5, 10, 20));
        rechaza(criterio(bandas, CRITICIDAD));
    }

    @Test
    void valorFaltante_400() {
        List<SeverityBand> bandas = bandasPorDefecto();
        bandas.set(0, new SeverityBand("Clorosis", 5, null, 20));
        rechaza(criterio(bandas, CRITICIDAD));
    }

    @Test
    void estadoFaltante_400() {
        List<SeverityBand> bandas = bandasPorDefecto();
        bandas.remove(1);
        rechaza(criterio(bandas, CRITICIDAD));
    }

    @Test
    void estadoQueSobra_400() {
        List<SeverityBand> bandas = bandasPorDefecto();
        bandas.add(new SeverityBand("Sano", 1, 2, 3));
        rechaza(criterio(bandas, CRITICIDAD));
    }

    @Test
    void estadoRepetido_400() {
        List<SeverityBand> bandas = bandasPorDefecto();
        bandas.set(1, new SeverityBand("Clorosis", 5, 10, 20));
        rechaza(criterio(bandas, CRITICIDAD));
    }

    @Test
    void criticidadQueRepiteUnEstado_400() {
        rechaza(criterio(bandasPorDefecto(), List.of("Daño fúngico", "Daño fúngico", "Clorosis", "Estrés solar")));
    }

    @Test
    void criticidadQueOmiteUnEstado_400() {
        rechaza(criterio(bandasPorDefecto(), List.of("Daño fúngico", "Plaga foliar", "Clorosis")));
    }

    @Test
    void criticidadConUnEstadoAjeno_400() {
        rechaza(criterio(bandasPorDefecto(), List.of("Daño fúngico", "Plaga foliar", "Clorosis", "Sano")));
    }

    @Test
    void sinCuerpo_400() {
        rechaza(null);
    }
}
