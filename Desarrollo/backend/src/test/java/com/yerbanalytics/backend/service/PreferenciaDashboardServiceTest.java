package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.model.PreferenciaDashboardEntity;
import com.yerbanalytics.backend.repository.PreferenciaDashboardRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("PreferenciaDashboardService")
class PreferenciaDashboardServiceTest {

    private final PreferenciaDashboardRepository repo = mock(PreferenciaDashboardRepository.class);
    private final PreferenciaDashboardService service = new PreferenciaDashboardService(repo);

    @Test
    void sinFilaLaSeccionEstaOculta() {
        when(repo.findById(1L)).thenReturn(Optional.empty());

        assertThat(service.demoExpoVisible()).isFalse();
    }

    @Test
    void leeElValorGuardado() {
        when(repo.findById(1L)).thenReturn(Optional.of(new PreferenciaDashboardEntity(1L, true)));

        assertThat(service.demoExpoVisible()).isTrue();
    }

    @Test
    void cambiarCreaLaFilaUnicaYDevuelveElValor() {
        when(repo.findById(1L)).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThat(service.cambiarDemoExpo(true)).isTrue();

        ArgumentCaptor<PreferenciaDashboardEntity> c = ArgumentCaptor.forClass(PreferenciaDashboardEntity.class);
        verify(repo).save(c.capture());
        assertThat(c.getValue().getId()).isEqualTo(1L);
        assertThat(c.getValue().isDemoExpoVisible()).isTrue();
    }

    @Test
    void cambiarActualizaLaFilaExistente() {
        PreferenciaDashboardEntity fila = new PreferenciaDashboardEntity(1L, true);
        when(repo.findById(1L)).thenReturn(Optional.of(fila));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThat(service.cambiarDemoExpo(false)).isFalse();
        assertThat(fila.isDemoExpoVisible()).isFalse();
    }
}
