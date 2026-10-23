package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.engine.ReglaTestSupport;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Ayudas para armar un {@link HistorialService} en los tests. */
final class HistorialTestSupport {

    private HistorialTestSupport() {}

    /** Un catálogo que devuelve los valores de fábrica (seguimiento de 2 min y delta de 5). */
    static CatalogoParametrosService parametrosDeFabrica() {
        CatalogoParametrosService parametros = mock(CatalogoParametrosService.class);
        when(parametros.vigentes()).thenReturn(ReglaTestSupport.fabrica());
        return parametros;
    }
}
