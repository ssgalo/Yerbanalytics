package com.yerbanalytics.backend.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Exclusión mutua del único ESP32 entre la pasada del riel y las secuencias de actuadores: mientras
 * una de las dos corre, la otra no arranca (design add-secuencias-demo-expo §2.4).
 *
 * <p><b>Sin estado propio.</b> El "ocupado" se deriva de la foto {@code volatile} de cada servicio
 * ({@code EN_CURSO} = ocupado), así que no hay nada que liberar y el guardia no puede quedar trabado
 * por un error o un reinicio. Los usos llegan por {@link ObjectProvider} para que la pasada y las
 * secuencias puedan depender del guardia sin ciclo de beans.
 *
 * <p><b>Locks.</b> Siempre guardia → servicio; {@link UsoDelHardware#ocupadoPor()} lee la foto sin
 * lock. Sin deadlock. Lo que el guardia no cubre son los comandos del motor de reglas: ése es el
 * sistema real.
 */
@Component
public class GuardiaHardware {

    private final ObjectProvider<UsoDelHardware> usos;

    public GuardiaHardware(ObjectProvider<UsoDelHardware> usos) {
        this.usos = usos;
    }

    /**
     * Ejecuta {@code iniciar} sólo si ningún uso <em>distinto del solicitante</em> está ocupado, con
     * el lock del guardia tomado: dos pedidos simultáneos no pueden pasar los dos.
     *
     * @param rechazo arma la excepción (la del servicio que pide) a partir del motivo de quien ocupa
     * @throws RuntimeException la que arma {@code rechazo}, sin haber ejecutado {@code iniciar}
     */
    public synchronized <T> T conHardwareLibre(UsoDelHardware solicitante,
                                               Function<String, RuntimeException> rechazo,
                                               Supplier<T> iniciar) {
        usos.stream()
                .filter(uso -> uso != solicitante)
                .map(UsoDelHardware::ocupadoPor)
                .flatMap(java.util.Optional::stream)
                .findFirst()
                .ifPresent(motivo -> {
                    throw rechazo.apply(motivo);
                });
        return iniciar.get();
    }
}
