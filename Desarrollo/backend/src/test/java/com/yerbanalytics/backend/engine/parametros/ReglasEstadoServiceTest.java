package com.yerbanalytics.backend.engine.parametros;

import com.yerbanalytics.backend.dto.ReglaCatalogoDto;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.RuleEffect;
import com.yerbanalytics.backend.engine.overrides.DemoControllable;
import com.yerbanalytics.backend.engine.overrides.DemoControllableRegistry;
import com.yerbanalytics.backend.engine.overrides.OperationalRuleStates;
import com.yerbanalytics.backend.engine.overrides.RuleStateEntity;
import com.yerbanalytics.backend.engine.overrides.RuleStateRepository;
import com.yerbanalytics.backend.service.HistorialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ReglasEstadoService")
class ReglasEstadoServiceTest {

    private record Pieza(String name, Optional<String> protection) implements DemoControllable {
        @Override public String label() { return name; }
        @Override public RuleEffect effect() { return protection.isPresent() ? RuleEffect.PROTECTS : RuleEffect.ACTS; }
        @Override public RuleBranch branch() { return RuleBranch.RIEGO; }
        @Override public int priority() { return 5; }
    }

    private RuleStateRepository repository;
    private HistorialService historial;
    private OperationalRuleStates states;
    private ReglasEstadoService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(RuleStateRepository.class);
        when(repository.findAll()).thenReturn(List.of());
        historial = mock(HistorialService.class);
        states = new OperationalRuleStates(repository, List.of("Retirada"));
        ReglasCatalogo catalogo = mock(ReglasCatalogo.class);
        when(catalogo.regla(any(DemoControllable.class), any())).thenReturn(mock(ReglaCatalogoDto.class));
        when(catalogo.regla(anyString())).thenReturn(Optional.of(mock(ReglaCatalogoDto.class)));
        DemoControllableRegistry registry = new DemoControllableRegistry(List.of(
                new Pieza("Accion", Optional.empty()),
                new Pieza("Retirada", Optional.of("consecuencia"))));
        ObjectProvider<PlatformTransactionManager> sinTransaccion = mock(ObjectProvider.class);
        service = new ReglasEstadoService(registry, states, historial, catalogo, sinTransaccion);
    }

    @Test
    @DisplayName("una retirada sin fila pedida apagada por una persona guarda la fila y deja el evento")
    void retiradaApagadaExplicitamente() {
        service.cambiar("Retirada", false, "lo confirmo apagada", true, "Ana");

        verify(repository).save(any(RuleStateEntity.class));
        verify(historial).registrarEstadoRegla(org.mockito.ArgumentMatchers.eq("Ana"), org.mockito.ArgumentMatchers.eq("Retirada"), org.mockito.ArgumentMatchers.eq("Retirada"), any(), org.mockito.ArgumentMatchers.eq(false),
                org.mockito.ArgumentMatchers.eq("lo confirmo apagada"), org.mockito.ArgumentMatchers.eq("consecuencia"));
        assertThat(states.state("Retirada")).isPresent();
    }

    @Test
    @DisplayName("con fila del mismo estado sigue siendo un no-op: ni fila ni evento")
    void conFilaMismoEstadoNoHaceNada() {
        service.cambiar("Retirada", false, "primero", true, "Ana");

        service.cambiar("Retirada", false, null, null, "Ana");

        verify(repository, times(1)).save(any(RuleStateEntity.class));
        verify(historial, times(1)).registrarEstadoRegla(anyString(), anyString(), anyString(), anyString(),
                anyBoolean(), any(), any());
    }

    @Test
    @DisplayName("prender una regla ya prendida sin fila no escribe nada")
    void prenderLoPrendidoNoHaceNada() {
        service.cambiar("Accion", true, null, null, "Ana");

        verify(repository, never()).save(any());
        verify(historial, never()).registrarEstadoRegla(anyString(), anyString(), anyString(), anyString(),
                anyBoolean(), any(), any());
    }

    @Test
    @DisplayName("pedidos simultáneos para la misma regla: el segundo ve el cambio del primero y no duplica el evento")
    void seSerializaPorRegla() throws Exception {
        int hilos = 8;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch largada = new CountDownLatch(1);
        List<java.util.concurrent.Future<?>> futuros = new ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            futuros.add(pool.submit(() -> {
                largada.await();
                return service.cambiar("Accion", false, null, null, "Ana");
            }));
        }
        largada.countDown();
        for (var f : futuros) {
            f.get(10, TimeUnit.SECONDS);
        }
        pool.shutdownNow();

        verify(repository, times(1)).save(any(RuleStateEntity.class));
        verify(historial, times(1)).registrarEstadoRegla(anyString(), anyString(), anyString(), anyString(),
                anyBoolean(), any(), any());
    }
}
