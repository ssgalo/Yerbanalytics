package com.yerbanalytics.backend.config;

import com.yerbanalytics.backend.service.CapturaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * El aislamiento del canal de las cámaras respecto del motor de reglas es una garantía
 * operativa, no un detalle de configuración: si los dos vuelven a compartir hilo, el keep-alive
 * del SSE se queda sin salir y los dispositivos reconectan solos sin que nada haya fallado.
 *
 * <p>Es fácil de romper sin darse cuenta —alcanza con borrar el {@code scheduler} de una
 * anotación, o con que alguien defina un {@code TaskScheduler} de más y Spring deje de resolver
 * el default por nombre—, así que queda fijado acá.
 */
@SpringBootTest
class SchedulersConfigTest {

    @Autowired
    private TaskScheduler taskScheduler;

    @Autowired
    private SchedulersConfig config;

    @Test
    void hayDosCarrilesDistintos() {
        TaskScheduler captura = config.capturaScheduler();

        assertNotNull(taskScheduler, "El carril del motor tiene que resolverse por nombre");
        assertNotSame(taskScheduler, captura,
                "El motor y la captura no pueden compartir scheduler");
    }

    @Test
    void lasTareasDeCapturaPidenElCarrilDeCaptura() throws NoSuchMethodException {
        for (String metodo : new String[] {"latido", "vencerOrdenes"}) {
            Method m = CapturaService.class.getMethod(metodo);
            Scheduled anotacion = m.getAnnotation(Scheduled.class);

            assertNotNull(anotacion, metodo + " debe seguir siendo una tarea programada");
            assertEquals("capturaScheduler", anotacion.scheduler(),
                    metodo + " debe correr en el carril de captura, no en el del motor");
        }
    }
}
