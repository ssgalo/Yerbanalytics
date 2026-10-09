package com.yerbanalytics.backend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** El único ESP32 lo comandan la pasada y las secuencias: entre ellas, de a una (design §2.4). */
@DisplayName("GuardiaHardware")
class GuardiaHardwareTest {

    /** Un {@link ObjectProvider} con los usos dados: lo mínimo que usa el guardia es {@code stream()}. */
    @SuppressWarnings("unchecked")
    static GuardiaHardware guardiaCon(UsoDelHardware... usos) {
        ObjectProvider<UsoDelHardware> provider = mock(ObjectProvider.class);
        when(provider.stream()).thenAnswer(i -> Stream.of(usos));
        return new GuardiaHardware(provider);
    }

    private static UsoDelHardware libre() {
        return Optional::empty;
    }

    private static UsoDelHardware ocupado(String motivo) {
        return () -> Optional.of(motivo);
    }

    private static final java.util.function.Function<String, RuntimeException> RECHAZO = IllegalStateException::new;

    @Test
    void conTodoLibreEjecutaYDevuelveElResultado() {
        UsoDelHardware yo = libre();
        GuardiaHardware guardia = guardiaCon(yo, libre());

        String r = guardia.conHardwareLibre(yo, RECHAZO, () -> "arrancó");

        assertThat(r).isEqualTo("arrancó");
    }

    @Test
    void siOtroUsoEstaOcupadoLanzaElRechazoConSuMotivoYNoEjecuta() {
        UsoDelHardware yo = libre();
        GuardiaHardware guardia = guardiaCon(yo, ocupado("Hay una pasada del riel en curso."));
        AtomicInteger ejecuciones = new AtomicInteger();

        assertThatThrownBy(() -> guardia.conHardwareLibre(yo, RECHAZO, ejecuciones::incrementAndGet))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Hay una pasada del riel en curso.");
        assertThat(ejecuciones).hasValue(0);
    }

    @Test
    void elSolicitanteNoSeBloqueaASiMismo() {
        UsoDelHardware yo = ocupado("estoy ocupado yo");
        GuardiaHardware guardia = guardiaCon(yo, libre());

        String r = guardia.conHardwareLibre(yo, RECHAZO, () -> "ok");

        assertThat(r).isEqualTo("ok");
    }

    @Test
    void dosHilosSimultaneosNoPuedenArrancarLosDos() throws Exception {
        // El "uso" de cada hilo pasa a ocupado recién dentro de iniciar(), como hacen los servicios.
        AtomicReference<Boolean> aOcupado = new AtomicReference<>(false);
        AtomicReference<Boolean> bOcupado = new AtomicReference<>(false);
        UsoDelHardware a = () -> aOcupado.get() ? Optional.of("A en curso") : Optional.empty();
        UsoDelHardware b = () -> bOcupado.get() ? Optional.of("B en curso") : Optional.empty();
        GuardiaHardware guardia = guardiaCon(a, b);
        AtomicInteger ejecutaron = new AtomicInteger();
        CountDownLatch salida = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> fa = pool.submit(() -> intentar(guardia, a, aOcupado, ejecutaron, salida));
            Future<?> fb = pool.submit(() -> intentar(guardia, b, bOcupado, ejecutaron, salida));
            salida.countDown();
            fa.get();
            fb.get();
        } finally {
            pool.shutdownNow();
        }

        assertThat(ejecutaron).hasValue(1);
    }

    private static void intentar(GuardiaHardware guardia, UsoDelHardware yo, AtomicReference<Boolean> flag,
                                 AtomicInteger ejecutaron, CountDownLatch salida) {
        try {
            salida.await();
            guardia.conHardwareLibre(yo, RECHAZO, () -> {
                ejecutaron.incrementAndGet();
                Thread.yield();
                flag.set(true);
                return null;
            });
        } catch (IllegalStateException rechazado) {
            // el otro ganó: es lo esperado
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void recorreTodosLosUsosYSeQuedaConElPrimerMotivo() {
        UsoDelHardware yo = libre();
        GuardiaHardware guardia = guardiaCon(yo, libre(), ocupado("segundo"), ocupado("tercero"));

        assertThatThrownBy(() -> guardia.conHardwareLibre(yo, RECHAZO, () -> "x"))
                .hasMessage("segundo");
    }
}
