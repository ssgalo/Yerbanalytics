package com.yerbanalytics.backend.engine.parametros;

import com.yerbanalytics.backend.service.HistorialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Tarea 6.2: el catálogo real rechaza, al guardar vía servicio, lo que las cuatro restricciones prohíben. */
@DisplayName("Restricciones cruzadas de riego (catálogo real)")
@ExtendWith(MockitoExtension.class)
class RestriccionesRiegoTest {

    @Mock
    private ParametroReglaRepository repository;
    @Mock
    private HistorialService historialService;
    @Mock
    private PlatformTransactionManager transactionManager;

    private CatalogoParametros catalogo;
    private CatalogoParametrosService service;

    @BeforeEach
    void setUp() {
        catalogo = new CatalogoParametros(List.of());
        service = new CatalogoParametrosService(catalogo, repository, historialService, transactionManager);
    }

    private static CambioParametro cambio(String clave, String valor) {
        return new CambioParametro(clave, valor);
    }

    private void rechaza(String mensaje, CambioParametro... cambios) {
        assertThatThrownBy(() -> service.guardar(List.of(cambios), "Ana"))
                .isInstanceOfSatisfying(ParametrosInvalidosException.class, ex ->
                        assertThat(ex.getErrores()).extracting(ErrorParametro::mensaje).contains(mensaje));
        verify(repository, never()).save(any());
    }

    @Test
    void criticoMayorOIgualQueElUmbralSeRechaza() {
        rechaza("El umbral crítico debe ser menor que el umbral de riego.",
                cambio("riego.umbral-critico", "40"), cambio("riego.umbral-humedad", "40"));
    }

    @Test
    void umbralMayorOIgualQueElObjetivoSeRechaza() {
        rechaza("El umbral de riego debe ser menor que la humedad objetivo.",
                cambio("riego.umbral-humedad", "60"), cambio("riego.humedad-objetivo", "55"));
    }

    @Test
    void bloqueoPorSaturacionMayorQueLaAlertaSeRechaza() {
        rechaza("El bloqueo por saturación no puede superar la alerta de saturación.",
                cambio("riego.saturacion-bloqueo", "82"));
    }

    @Test
    void volumenMaximoQueNoEntraEnLaValvulaSeRechaza() {
        // 10 L a 20 L/h son 1800 s y la válvula acepta 1200 s.
        rechaza("Con ese caudal, el volumen máximo no se alcanza a regar dentro del límite de la válvula.",
                cambio("riego.volumen-max-evento", "10"), cambio("riego.caudal-emisor", "20"));
    }

    @Test
    void elLimiteExactoDeLaValvulaSeAcepta() {
        // 10 L a 30 L/h son exactamente 1200 s.
        assertThat(violaciones("riego.volumen-max-evento", "10", "riego.caudal-emisor", "30")).isEmpty();
        // Un poco menos de caudal ya no entra.
        assertThat(violaciones("riego.volumen-max-evento", "10", "riego.caudal-emisor", "29.9")).hasSize(1);
    }

    @Test
    void losValoresDeFabricaCumplenLasCuatro() {
        assertThat(catalogo.violaciones(catalogo.fabricas())).isEmpty();
    }

    private List<RestriccionCruzada> violaciones(String k1, String v1, String k2, String v2) {
        java.util.Map<String, ValorParametro> m = new java.util.HashMap<>(catalogo.fabricas().valores());
        for (String[] kv : new String[][]{{k1, v1}, {k2, v2}}) {
            DefinicionParametro d = catalogo.definicion(kv[0]).orElseThrow();
            m.put(kv[0], d.tipo().parsear(kv[1], d));
        }
        return catalogo.violaciones(ParametrosVigentes.de(m));
    }
}
