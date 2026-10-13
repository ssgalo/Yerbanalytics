package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.repository.HistorialRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La consulta del historial siempre lleva tope: el registro acumula cientos de miles de filas "Info" y una consulta
 * sin límite cargaba la tabla entera en memoria y colgaba al backend y al navegador.
 */
@DisplayName("HistorialService - tope de la consulta")
class HistorialServiceLimiteTest {

    private final HistorialRepository repo = mock(HistorialRepository.class);

    private HistorialService service() {
        return new HistorialService(repo, mock(SectorRepository.class), mock(ConfiguracionService.class),
                120000, "2 min", 5, Clock.systemUTC());
    }

    private int topeConsultado(Integer pedido) {
        when(repo.findFiltered(any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        service().getHistorial(null, null, null, null, null, pedido);
        ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
        verify(repo).findFiltered(any(), any(), any(), any(), any(), pagina.capture());
        assertThat(pagina.getValue().getPageNumber()).isZero();
        return pagina.getValue().getPageSize();
    }

    @Test
    @DisplayName("sin límite pedido, devuelve a lo sumo el tope por defecto")
    void sinLimite_usaElDefault() {
        assertThat(topeConsultado(null)).isEqualTo(HistorialService.LIMITE_DEFAULT);
    }

    @ParameterizedTest(name = "pedido {0} → tope {1}")
    @CsvSource({"50, 50", "0, 1", "-3, 1", "1000000, " + HistorialService.LIMITE_MAXIMO})
    @DisplayName("el límite pedido se respeta dentro de [1, máximo]")
    void limitePedido_seAcota(int pedido, int esperado) {
        assertThat(topeConsultado(pedido)).isEqualTo(esperado);
    }
}
