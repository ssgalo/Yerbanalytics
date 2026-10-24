package com.yerbanalytics.backend.engine.parametros;

import com.yerbanalytics.backend.repository.ConfiguracionOperativaRepository;
import com.yerbanalytics.backend.service.HistorialService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** rediseno-motor-reglas D-4: dónde se evalúa una restricción cruzada cuando cada pieza tiene su copia. */
@DisplayName("Restricciones cruzadas con copias por pieza")
class RestriccionesPorPiezaTest {

    private final ReglaParametroRepository repository = mock(ReglaParametroRepository.class);

    private static ConsumidorParametros pieza(String nombre, DefinicionParametro... params) {
        return new ConsumidorParametros() {
            @Override public String name() { return nombre; }
            @Override public List<DefinicionParametro> parametros() { return List.of(params); }
        };
    }

    private CatalogoParametrosService service(ConsumidorParametros... piezas) {
        CatalogoParametros catalogo = new CatalogoParametros(CatalogoParametros.definicionesReales(),
                CatalogoParametros.restriccionesReales(), List.of(piezas));
        return new CatalogoParametrosService(catalogo, repository, mock(HistorialService.class),
                mock(PlatformTransactionManager.class), mock(ConfiguracionOperativaRepository.class),
                mock(ReglasCatalogo.class));
    }

    @Test
    void seEvaluaEnLaPiezaQueDeclaraTodasLasClaves_yElErrorApuntaASuCopia() {
        CatalogoParametrosService service = service(
                pieza("R05", ParametrosRiego.UMBRAL_HUMEDAD, ParametrosRiego.UMBRAL_CRITICO),
                pieza("R01", ParametrosRiego.UMBRAL_HUMEDAD));

        assertThatThrownBy(() -> service.guardar(List.of(
                new CambioParametro("R05", "riego.umbral-humedad", "35")), "Ana"))
                .isInstanceOfSatisfying(ParametrosInvalidosException.class, ex ->
                        assertThat(ex.getErrores()).singleElement().satisfies(e -> {
                            assertThat(e.regla()).isEqualTo("R05");
                            assertThat(e.clave()).isEqualTo("riego.umbral-humedad");
                            assertThat(e.mensaje()).isEqualTo("El umbral crítico debe ser menor que el umbral de riego.");
                        }));
        verify(repository, never()).save(any());
    }

    @Test
    void laCopiaDeOtraPiezaNoEntraEnLaRestriccionDeLaQueLasDeclaraTodas() {
        CatalogoParametrosService service = service(
                pieza("R05", ParametrosRiego.UMBRAL_HUMEDAD, ParametrosRiego.UMBRAL_CRITICO),
                pieza("R01", ParametrosRiego.UMBRAL_HUMEDAD));

        // El umbral de R-01 por debajo del crítico de R-05 no se compara: son copias de piezas distintas.
        assertThatNoException().isThrownBy(() -> service.guardar(List.of(
                new CambioParametro("R01", "riego.umbral-humedad", "35")), "Ana"));
    }

    @Test
    void sinPiezaQueLasDeclareTodasYConVariasDuenas_noSeEvalua() {
        // El crítico de R-02 y el umbral de R-01 (que también lee R-03): el control entre ellos se pierde a propósito.
        CatalogoParametrosService service = service(
                pieza("R02", ParametrosRiego.UMBRAL_CRITICO),
                pieza("R01", ParametrosRiego.UMBRAL_HUMEDAD),
                pieza("R03", ParametrosRiego.UMBRAL_HUMEDAD));

        assertThatNoException().isThrownBy(() -> service.guardar(List.of(
                new CambioParametro("R01", "riego.umbral-humedad", "35")), "Ana"));
    }

    @Test
    void conUnaSolaDuenaPorClave_seEvaluaConElValorDeCadaDuena() {
        // Estrés solar: la luz baja la lee una regla y la alta otra.
        CatalogoParametrosService service = service(
                pieza("FS3", ParametrosEstres.LOW_LIGHT),
                pieza("FS1", ParametrosEstres.HIGH_LIGHT));
        double alta = Double.parseDouble(ParametrosEstres.HIGH_LIGHT.fabrica());
        String porEncima = String.valueOf((int) Math.min(alta + 1, ParametrosEstres.LOW_LIGHT.max()));

        assertThatThrownBy(() -> service.guardar(List.of(
                new CambioParametro("FS3", "estres.luz-baja", porEncima)), "Ana"))
                .isInstanceOfSatisfying(ParametrosInvalidosException.class, ex ->
                        assertThat(ex.getErrores()).anySatisfy(e -> {
                            assertThat(e.regla()).isEqualTo("FS3");
                            assertThat(e.mensaje()).isEqualTo("La luz baja del estrés solar debe ser menor que la luz alta.");
                        }));
    }
}
