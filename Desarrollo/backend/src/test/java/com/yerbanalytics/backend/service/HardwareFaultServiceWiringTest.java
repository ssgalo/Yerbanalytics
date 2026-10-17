package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.engine.overrides.DemoControllable;
import com.yerbanalytics.backend.engine.overrides.DemoControllableRegistry;
import com.yerbanalytics.backend.engine.overrides.RuleOverrides;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametros;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.engine.parametros.ParametroReglaRepository;
import com.yerbanalytics.backend.engine.rules.HydraulicFaultRule;
import com.yerbanalytics.backend.engine.rules.ShadeFaultRule;
import com.yerbanalytics.backend.repository.ConfiguracionOperativaRepository;
import com.yerbanalytics.backend.repository.DispositivoRepository;
import com.yerbanalytics.backend.repository.RustificacionEtapaRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
import com.yerbanalytics.backend.repository.UmbralMetricaRepository;
import com.yerbanalytics.backend.repository.ZonaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * El servicio de fallas es un consumidor de parámetros (el umbral de riego del texto de la alerta) y el catálogo se arma
 * con la lista de todos los consumidores: sin {@code @Lazy} Spring no podría construir el contexto. Los tests con dobles
 * no lo ven; este arma el cableado real con la infraestructura simulada (los {@code @SpringBootTest} del repo necesitan
 * PostgreSQL). También verifica que las dos reglas y el servicio aparecen como piezas controlables del simulador.
 */
@DisplayName("HardwareFaultService - cableado de Spring")
class HardwareFaultServiceWiringTest {

    @Test
    @DisplayName("el contexto se construye sin ciclos y el catálogo ve al servicio y a las reglas de falla")
    void elContextoSeConstruye() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.registerBean(Clock.class, Clock::systemUTC);
            ctx.registerBean(RuleOverrides.class, () -> RuleOverrides.NONE);
            ctx.registerBean(HistorialService.class, () -> mock(HistorialService.class));
            ctx.registerBean(DispositivoRepository.class, () -> mock(DispositivoRepository.class));
            ctx.registerBean(SectorRepository.class, () -> mock(SectorRepository.class));
            ctx.registerBean(ZonaRepository.class, () -> mock(ZonaRepository.class));
            ctx.registerBean(ParametroReglaRepository.class, () -> mock(ParametroReglaRepository.class));
            ctx.registerBean(ConfiguracionOperativaRepository.class, () -> mock(ConfiguracionOperativaRepository.class));
            ctx.registerBean(UmbralMetricaRepository.class, () -> mock(UmbralMetricaRepository.class));
            ctx.registerBean(RustificacionEtapaRepository.class, () -> mock(RustificacionEtapaRepository.class));
            ctx.registerBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class));
            ctx.register(CatalogoParametros.class, CatalogoParametrosService.class, ConfiguracionService.class,
                    HardwareFaultService.class, HydraulicFaultRule.class, ShadeFaultRule.class,
                    DemoControllableRegistry.class);

            ctx.refresh();

            CatalogoParametros catalogo = ctx.getBean(CatalogoParametros.class);
            assertThat(catalogo.usadoPor("riego.umbral-humedad")).contains(HardwareFaultService.NAME);
            assertThat(ctx.getBean(DemoControllableRegistry.class).all()).extracting(DemoControllable::name)
                    .contains("HardwareFaultService", "HydraulicFaultRule", "ShadeFaultRule");
        }
    }
}
