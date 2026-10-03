package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.engine.ReglaTestSupport;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.engine.parametros.ParametrosDiagnostico;
import com.yerbanalytics.backend.repository.CapturaRepository;
import com.yerbanalytics.backend.repository.DiagnosticoRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Un solo valor de confianza mínima para SupplyRule y para decidir si un diagnóstico es concluyente. */
@DisplayName("DiagnosticoService - confianza mínima")
class DiagnosticoServiceConfianzaTest {

    private static DiagnosticoService servicio(CatalogoParametrosService parametros) {
        return new DiagnosticoService(mock(DiagnosticoRepository.class), mock(CapturaRepository.class),
                mock(SectorRepository.class), parametros);
    }

    @Test
    void esConcluyenteUsaElParametroDelCatalogo() {
        CatalogoParametrosService parametros = mock(CatalogoParametrosService.class);
        when(parametros.vigentes()).thenReturn(ReglaTestSupport.fabrica());
        DiagnosticoService s = servicio(parametros);

        assertThat(s.esConcluyente(85.0)).isTrue();
        assertThat(s.esConcluyente(84.9)).isFalse();
    }

    @Test
    void unOverrideDelCatalogoCambiaElCorte() {
        CatalogoParametrosService parametros = mock(CatalogoParametrosService.class);
        when(parametros.vigentes()).thenReturn(ReglaTestSupport.con(Map.of(ParametrosDiagnostico.CONFIANZA_MINIMA, "90")));

        assertThat(servicio(parametros).esConcluyente(85.0)).isFalse();
        assertThat(servicio(parametros).esConcluyente(90.0)).isTrue();
    }
}
