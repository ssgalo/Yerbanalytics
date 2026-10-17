package com.yerbanalytics.backend.readings;

import com.yerbanalytics.backend.engine.DetalleAlerta;
import com.yerbanalytics.backend.engine.NivelAlerta;
import com.yerbanalytics.backend.model.InvalidReadingEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.repository.InvalidReadingRepository;
import com.yerbanalytics.backend.service.HistorialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * S-03, escalamiento de la alerta: la primera falla de una métrica en un ciclo es {@code WARNING}; si vuelve a
 * fallar en el ciclo siguiente, {@code CRITICAL}; una lectura válida reinicia la cuenta.
 */
@DisplayName("InvalidReadingEscalation - WARNING → CRITICAL por métrica (S-03)")
class InvalidReadingEscalationTest {

    // Con el intervalo de fábrica (240 min, ancla 02:00 local, UTC−3) los ciclos son 10:00, 14:00 y 18:00 locales.
    private static final Instant CICLO_10 = Instant.parse("2026-10-17T13:00:00Z");
    private static final Instant CICLO_14 = Instant.parse("2026-10-17T17:00:00Z");
    private static final Instant CICLO_18 = Instant.parse("2026-10-17T21:00:00Z");

    private final Map<String, InvalidReadingEntity> tabla = new HashMap<>();
    private InvalidReadingRepository repo;
    private HistorialService historial;
    private InvalidReadingEscalation escalation;
    private ZonaEntity zona;

    @BeforeEach
    void setUp() {
        repo = mock(InvalidReadingRepository.class);
        when(repo.findById(any())).thenAnswer(inv -> Optional.ofNullable(tabla.get(inv.<String>getArgument(0))));
        when(repo.save(any())).thenAnswer(inv -> {
            InvalidReadingEntity e = inv.getArgument(0);
            tabla.put(e.getId(), e);
            return e;
        });
        doAnswer(inv -> {
            String zonaId = inv.getArgument(0);
            Iterable<String> metricas = inv.getArgument(1);
            metricas.forEach(m -> tabla.remove(InvalidReadingEntity.idOf(zonaId, m)));
            return null;
        }).when(repo).deleteByZoneIdAndMetricIn(any(), any());
        historial = mock(HistorialService.class);
        escalation = new InvalidReadingEscalation(repo, historial, Clock.fixed(CICLO_10, ZoneOffset.UTC));
        zona = new ZonaEntity();
        zona.setId("MZ-2");
        zona.setName("Macro-zona 2");
    }

    private void descarta(String metrica, Instant ciclo, Instant cicloAnterior) {
        escalation.registerDiscard(zona, metrica, "Temperatura del aire", ciclo, cicloAnterior);
    }

    private List<DetalleAlerta> alertas(int veces) {
        ArgumentCaptor<DetalleAlerta> det = ArgumentCaptor.forClass(DetalleAlerta.class);
        verify(historial, times(veces)).registrarAlerta(eq("MZ-2"), eq("Macro-zona 2"),
                eq(ReadingValidationGuard.NAME), det.capture(), anyLong());
        return det.getAllValues();
    }

    @Test
    @DisplayName("la primera falla deja un WARNING 'Lectura inválida en [métrica]'")
    void primeraFalla() {
        descarta("temp", CICLO_10, CICLO_10.minusSeconds(4 * 3600));

        List<DetalleAlerta> alertas = alertas(1);
        assertThat(alertas.get(0).nivel()).isEqualTo(NivelAlerta.WARNING);
        assertThat(alertas.get(0).texto()).startsWith("Lectura inválida en Temperatura del aire");
        assertThat(tabla.get("MZ-2:temp").getStreak()).isEqualTo(1);
    }

    @Test
    @DisplayName("la misma métrica en el ciclo siguiente: CRITICAL 'Sensor de [métrica] con falla: revisar'")
    void dosCiclosSeguidos() {
        descarta("temp", CICLO_10, CICLO_10.minusSeconds(4 * 3600));
        descarta("temp", CICLO_14, CICLO_10);

        List<DetalleAlerta> alertas = alertas(2);
        assertThat(alertas).extracting(DetalleAlerta::nivel).containsExactly(NivelAlerta.WARNING, NivelAlerta.CRITICAL);
        assertThat(alertas.get(1).texto()).startsWith("Sensor de Temperatura del aire con falla: revisar");
        assertThat(tabla.get("MZ-2:temp").getStreak()).isEqualTo(2);
    }

    @Test
    @DisplayName("un tercer ciclo seguido sigue siendo CRITICAL (la falla persiste)")
    void tercerCiclo() {
        descarta("temp", CICLO_10, CICLO_10.minusSeconds(4 * 3600));
        descarta("temp", CICLO_14, CICLO_10);
        descarta("temp", CICLO_18, CICLO_14);

        assertThat(alertas(3)).extracting(DetalleAlerta::nivel)
                .containsExactly(NivelAlerta.WARNING, NivelAlerta.CRITICAL, NivelAlerta.CRITICAL);
    }

    @Test
    @DisplayName("fallar otra vez en el MISMO ciclo no suma una alerta ni cuenta como ciclo siguiente")
    void mismoCiclo() {
        descarta("temp", CICLO_10, CICLO_10.minusSeconds(4 * 3600));
        descarta("temp", CICLO_10, CICLO_10.minusSeconds(4 * 3600));

        alertas(1);
        assertThat(tabla.get("MZ-2:temp").getStreak()).isEqualTo(1);
    }

    @Test
    @DisplayName("si entre una falla y la otra hubo un ciclo sin fallar, vuelve a empezar con WARNING")
    void noConsecutivos() {
        descarta("temp", CICLO_10, CICLO_10.minusSeconds(4 * 3600));
        descarta("temp", CICLO_18, CICLO_14);   // el ciclo de las 14:00 no falló (o no hubo lectura)

        assertThat(alertas(2)).extracting(DetalleAlerta::nivel).containsExactly(NivelAlerta.WARNING, NivelAlerta.WARNING);
        assertThat(tabla.get("MZ-2:temp").getStreak()).isEqualTo(1);
    }

    @Test
    @DisplayName("una lectura válida reinicia la cuenta: sólo existe el WARNING de la primera falla")
    void seRecupera() {
        descarta("temp", CICLO_10, CICLO_10.minusSeconds(4 * 3600));
        escalation.registerValid("MZ-2", List.of("temp", "humSus"));
        descarta("temp", CICLO_14, CICLO_10);

        // Sin el reinicio, la segunda falla sería CRITICAL.
        assertThat(alertas(2)).extracting(DetalleAlerta::nivel).containsExactly(NivelAlerta.WARNING, NivelAlerta.WARNING);
    }

    @Test
    @DisplayName("la cuenta es por métrica y por zona: otra métrica no hereda la racha")
    void porMetrica() {
        descarta("temp", CICLO_10, CICLO_10.minusSeconds(4 * 3600));
        escalation.registerDiscard(zona, "phSuelo", "pH del sustrato", CICLO_14, CICLO_10);

        assertThat(alertas(2)).extracting(DetalleAlerta::nivel).containsExactly(NivelAlerta.WARNING, NivelAlerta.WARNING);
    }

    @Test
    @DisplayName("sin métricas válidas que reiniciar no toca la base")
    void sinNadaQueReiniciar() {
        escalation.registerValid("MZ-2", List.of());

        verifyNoInteractions(repo);
    }
}
