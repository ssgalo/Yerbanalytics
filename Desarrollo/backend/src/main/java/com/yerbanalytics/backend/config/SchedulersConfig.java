package com.yerbanalytics.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Dos carriles de tareas programadas, a propósito separados.
 *
 * <p>El default de Spring Boot es <strong>un solo hilo</strong> para todos los {@code @Scheduled}
 * de la aplicación, y acá conviven dos familias de tareas con exigencias incompatibles:
 *
 * <ul>
 *   <li><b>El motor de reglas</b> ({@code NurseryWatchdog}, {@code HistorialService}): barre las
 *       seis zonas y sus 600 sectores, y consulta el pronóstico por HTTP con reintentos. Tarda lo
 *       que tarde, y no pasa nada — es proactivo.</li>
 *   <li><b>El subsistema de captura</b> ({@code CapturaService}): el keep-alive del canal SSE
 *       tiene que salir cada 20 s <em>sí o sí</em>, porque el dispositivo da por muerto un canal
 *       que lleva 45 s callado y reconecta. Y el vencimiento de órdenes marca el plazo que el
 *       contrato le promete a cada orden.</li>
 * </ul>
 *
 * <p>Compartiendo hilo, la primera familia le come los latidos a la segunda: el teléfono
 * reconectaba solo, sin que nada hubiera fallado realmente. El síntoma en el log del dispositivo
 * era {@code "Sin eventos por más de 45s: el canal está muerto aunque no dio error"}.
 *
 * <p><b>Por qué dos schedulers y no un pool más grande.</b> Subir
 * {@code spring.task.scheduling.pool.size} arreglaría el hambre de los latidos, pero también
 * habilitaría a las dos tareas del motor a correr en paralelo entre sí, cosa que hoy no ocurre y
 * de la que nadie se hizo cargo: ambas evalúan y escriben estado de sector. Separar carriles
 * arregla el problema real sin cambiar esa garantía.
 */
@Configuration
public class SchedulersConfig {

    /**
     * Carril del motor de reglas. Se llama {@code taskScheduler} a propósito: es el nombre que
     * Spring resuelve por defecto, así que todo {@code @Scheduled} que no pida otro cae acá.
     * Un hilo, igual que antes — las tareas del motor siguen serializadas entre sí.
     */
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
        s.setPoolSize(1);
        s.setThreadNamePrefix("motor-sched-");
        s.setWaitForTasksToCompleteOnShutdown(true);
        s.setAwaitTerminationSeconds(10);
        return s;
    }

    /**
     * Carril del subsistema de captura, aislado del motor. Dos hilos para que el vencimiento de
     * órdenes y el latido del canal tampoco se estorben entre ellos.
     */
    @Bean
    public TaskScheduler capturaScheduler() {
        ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
        s.setPoolSize(2);
        s.setThreadNamePrefix("captura-sched-");
        s.setWaitForTasksToCompleteOnShutdown(true);
        s.setAwaitTerminationSeconds(10);
        return s;
    }
}
