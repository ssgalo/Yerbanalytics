package com.yerbanalytics.backend.readings;

import com.yerbanalytics.backend.engine.overrides.RuleOverrides;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametros;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.engine.parametros.ConsumidorParametros;
import com.yerbanalytics.backend.engine.parametros.ParametroReglaRepository;
import com.yerbanalytics.backend.mqtt.ComandoZonaPublisher;
import com.yerbanalytics.backend.repository.ConfiguracionOperativaRepository;
import com.yerbanalytics.backend.repository.UmbralMetricaRepository;
import com.yerbanalytics.backend.service.ConfiguracionService;
import com.yerbanalytics.backend.service.HistorialService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * El guard es un consumidor de parámetros, y el catálogo se arma con la lista de todos los consumidores. Si el guard
 * dependiera sin {@code @Lazy} de algo que a su vez depende del catálogo (el servicio de configuración lo hace),
 * Spring no podría construir el contexto. Los tests unitarios con dobles no lo ven: este arma el cableado real con
 * las dependencias de infraestructura simuladas.
 */
@DisplayName("ReadingValidationGuard - cableado de Spring")
class ReadingValidationGuardWiringTest {

    @Test
    @DisplayName("el contexto se construye sin ciclos y el catálogo ve al guard como consumidor de los rangos")
    void elContextoSeConstruye() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.registerBean(Clock.class, Clock::systemUTC);
            ctx.registerBean(RuleOverrides.class, () -> RuleOverrides.NONE);
            ctx.registerBean(ComandoZonaPublisher.class, () -> mock(ComandoZonaPublisher.class));
            ctx.registerBean(HistorialService.class, () -> mock(HistorialService.class));
            ctx.registerBean(InvalidReadingEscalation.class, () -> mock(InvalidReadingEscalation.class));
            ctx.registerBean(ParametroReglaRepository.class, () -> mock(ParametroReglaRepository.class));
            ctx.registerBean(ConfiguracionOperativaRepository.class, () -> mock(ConfiguracionOperativaRepository.class));
            ctx.registerBean(UmbralMetricaRepository.class, () -> mock(UmbralMetricaRepository.class));
            ctx.registerBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class));
            ctx.register(CatalogoParametros.class, CatalogoParametrosService.class, ConfiguracionService.class,
                    ReadingValidationGuard.class);

            ctx.refresh();

            CatalogoParametros catalogo = ctx.getBean(CatalogoParametros.class);
            assertThat(catalogo.usadoPor("seguridad.rango-hum-max")).containsExactly(ReadingValidationGuard.NAME);
            assertThat(catalogo.usadoPor("seguridad.relecturas-invalidas")).containsExactly(ReadingValidationGuard.NAME);
            assertThat(ctx.getBeansOfType(ConsumidorParametros.class)).containsKey("readingValidationGuard");
        }
    }
}
