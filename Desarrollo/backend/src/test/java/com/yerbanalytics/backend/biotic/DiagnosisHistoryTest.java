package com.yerbanalytics.backend.biotic;

import com.yerbanalytics.backend.model.DiagnosticoEntity;
import com.yerbanalytics.backend.repository.DiagnosticoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Historial de capturas diagnosticadas de un sector")
class DiagnosisHistoryTest {

    private static final Instant NOW = Instant.parse("2026-10-22T13:00:00Z");

    private final DiagnosticoRepository repository = mock(DiagnosticoRepository.class);
    private final DiagnosisHistory history = new DiagnosisHistory(repository);

    private static DiagnosticoEntity diagnosis(String estado, Double conf, long creadoEn) {
        DiagnosticoEntity d = new DiagnosticoEntity();
        d.setSectorId("MZ-1-045");
        d.setEstado(estado);
        d.setConf(conf);
        d.setCreadoEn(creadoEn);
        return d;
    }

    @Test
    void trae_las_capturas_de_los_ultimos_dias_mas_nueva_primero() {
        long sinceMs = NOW.minusSeconds(4 * 86_400L).toEpochMilli();
        when(repository.findBySectorIdAndCreadoEnGreaterThanEqualOrderByCreadoEnDesc("MZ-1-045", sinceMs))
                .thenReturn(List.of(diagnosis("Daño fúngico", 88.0, NOW.toEpochMilli()),
                        diagnosis("Sano", null, NOW.minusSeconds(86_400).toEpochMilli())));

        List<ConsecutiveCaptures.Capture> captures = history.recent("MZ-1-045", NOW, 4);

        assertThat(captures).extracting(ConsecutiveCaptures.Capture::estado).containsExactly("Daño fúngico", "Sano");
        assertThat(captures.get(0).conf()).isEqualTo(88.0);
        assertThat(captures.get(0).ts()).isEqualTo(NOW.toEpochMilli());
        verify(repository).findBySectorIdAndCreadoEnGreaterThanEqualOrderByCreadoEnDesc("MZ-1-045", sinceMs);
    }

    @Test
    void sin_diagnosticos_la_lista_esta_vacia() {
        assertThat(history.recent("MZ-1-045", NOW, 4)).isEmpty();
    }
}
