package com.yerbanalytics.backend.lecturas;

import com.yerbanalytics.backend.config.ZonaHorariaVivero;
import com.yerbanalytics.backend.engine.parametros.VentanaHoraria;
import com.yerbanalytics.backend.model.LecturaZonaEntity;
import com.yerbanalytics.backend.repository.LecturaZonaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Consultas del historial contra una "base" en memoria que respeta el contrato del repositorio (por zona, por rango,
 * paginado y ordenado), así se prueba la lógica y no el motor SQL.
 */
@DisplayName("HistorialLecturas")
class HistorialLecturasTest {

    private static final String ZONA = "MZ-1";
    /** Medianoche local del 2026-10-14 como referencia. */
    private static final LocalDateTime DIA = LocalDateTime.of(2026, 10, 14, 0, 0);

    private final List<LecturaZonaEntity> base = new ArrayList<>();
    private HistorialLecturas historial;

    @BeforeEach
    void setUp() {
        LecturaZonaRepository repo = mock(LecturaZonaRepository.class);
        when(repo.findByZonaIdAndTsBetweenOrderByTsAsc(anyString(), anyLong(), anyLong())).thenAnswer(inv -> {
            String zona = inv.getArgument(0);
            long desde = inv.getArgument(1);
            long hasta = inv.getArgument(2);
            return base.stream()
                    .filter(l -> l.getZonaId().equals(zona) && l.getTs() >= desde && l.getTs() <= hasta)
                    .sorted(Comparator.comparing(LecturaZonaEntity::getTs)).toList();
        });
        when(repo.findByZonaIdOrderByTsDesc(anyString(), any(Pageable.class))).thenAnswer(inv -> {
            Pageable p = inv.getArgument(1);
            return base.stream().filter(l -> l.getZonaId().equals(inv.getArgument(0)))
                    .sorted(Comparator.comparing(LecturaZonaEntity::getTs).reversed())
                    .limit(p.getPageSize()).toList();
        });
        when(repo.findByZonaIdAndOrigenOrderByTsDesc(anyString(), any(OrigenLectura.class), any(Pageable.class)))
                .thenAnswer(inv -> {
                    Pageable p = inv.getArgument(2);
                    return base.stream()
                            .filter(l -> l.getZonaId().equals(inv.getArgument(0)) && l.getOrigen() == inv.getArgument(1))
                            .sorted(Comparator.comparing(LecturaZonaEntity::getTs).reversed())
                            .limit(p.getPageSize()).toList();
                });
        historial = new HistorialLecturas(repo);
    }

    private static Instant local(int dia, int hora, int min) {
        return DIA.plusDays(dia).plusHours(hora).plusMinutes(min).atZone(ZonaHorariaVivero.ZONA).toInstant();
    }

    private LecturaZonaEntity agregar(String zona, Instant ts, OrigenLectura origen, Double ce, Double uv) {
        LecturaZonaEntity l = new LecturaZonaEntity();
        l.setZonaId(zona);
        l.setTs(ts.toEpochMilli());
        l.setRecibidaTs(ts.toEpochMilli());
        l.setOrigen(origen);
        l.setCe(ce);
        l.setUv(uv);
        base.add(l);
        return l;
    }

    // ----------------------------------------------------------------- ultimas

    @Test
    @DisplayName("ultimas(n) devuelve las N más nuevas en orden cronológico")
    void ultimas() {
        for (int h = 1; h <= 5; h++) {
            agregar(ZONA, local(0, h, 0), OrigenLectura.PULSO, (double) h, null);
        }
        agregar("MZ-2", local(0, 9, 0), OrigenLectura.PULSO, 99.0, null);

        assertThat(historial.ultimas(ZONA, 3, false)).extracting(LecturaZonaEntity::getCe)
                .containsExactly(3.0, 4.0, 5.0);
    }

    @Test
    @DisplayName("ultimas con soloPulso ignora las lecturas de otro origen")
    void ultimasSoloPulso() {
        agregar(ZONA, local(0, 1, 0), OrigenLectura.PULSO, 1.0, null);
        agregar(ZONA, local(0, 2, 0), OrigenLectura.OTRA, 2.0, null);
        agregar(ZONA, local(0, 3, 0), OrigenLectura.PULSO, 3.0, null);

        assertThat(historial.ultimas(ZONA, 5, true)).extracting(LecturaZonaEntity::getCe)
                .containsExactly(1.0, 3.0);
    }

    @Test
    @DisplayName("ultimas sin lecturas devuelve una lista vacía")
    void ultimasVacia() {
        assertThat(historial.ultimas(ZONA, 3, false)).isEmpty();
    }

    // ----------------------------------------------------------------- agregados

    @Test
    @DisplayName("media de 6 lecturas de pulso: una lectura de verificación en el medio no cuenta")
    void mediaSoloPulso() {
        double[] ce = {0.6, 0.8, 1.0, 1.2, 1.4, 1.6};
        for (int i = 0; i < ce.length; i++) {
            agregar(ZONA, local(0, 2 + 4 * i, 0), OrigenLectura.PULSO, ce[i], null);
        }
        agregar(ZONA, local(0, 11, 0), OrigenLectura.OTRA, 9.0, null);

        var ventana = VentanaLecturas.desde(local(0, 0, 0)).soloPulso();

        assertThat(historial.media(ZONA, MetricaLectura.CE, ventana)).hasValueSatisfying(
                m -> assertThat(m).isCloseTo(1.1, org.assertj.core.data.Offset.offset(1e-9)));
        assertThat(historial.conteo(ZONA, MetricaLectura.CE, ventana)).isEqualTo(6);
        assertThat(historial.media(ZONA, MetricaLectura.CE, VentanaLecturas.desde(local(0, 0, 0))))
                .as("sin soloPulso la de verificación sí cuenta").hasValueSatisfying(m -> assertThat(m).isGreaterThan(2.0));
    }

    @Test
    @DisplayName("máximo y mínimo de una ventana temporal")
    void maximoYMinimo() {
        agregar(ZONA, local(0, 10, 0), OrigenLectura.PULSO, null, 70.0);
        agregar(ZONA, local(0, 14, 0), OrigenLectura.PULSO, null, 88.0);
        agregar(ZONA, local(1, 10, 0), OrigenLectura.PULSO, null, 62.0);

        var ventana = VentanaLecturas.desde(local(0, 0, 0));

        assertThat(historial.maximo(ZONA, MetricaLectura.UV, ventana)).contains(88.0);
        assertThat(historial.minimo(ZONA, MetricaLectura.UV, ventana)).contains(62.0);
    }

    @Test
    @DisplayName("la ventana respeta desde y hasta; sin hasta llega hasta lo último")
    void ventanaConHasta() {
        agregar(ZONA, local(0, 10, 0), OrigenLectura.PULSO, null, 70.0);
        agregar(ZONA, local(1, 10, 0), OrigenLectura.PULSO, null, 95.0);

        assertThat(historial.maximo(ZONA, MetricaLectura.UV,
                VentanaLecturas.entre(local(0, 0, 0), local(0, 23, 0)))).contains(70.0);
        assertThat(historial.maximo(ZONA, MetricaLectura.UV, VentanaLecturas.desde(local(0, 0, 0)))).contains(95.0);
    }

    @Test
    @DisplayName("la franja horaria se evalúa en hora local del vivero: luz máxima entre las 10 y las 16 h")
    void franjaHorariaLocal() {
        agregar(ZONA, local(0, 6, 0), OrigenLectura.PULSO, null, 99.0);    // de madrugada: fuera
        agregar(ZONA, local(0, 10, 0), OrigenLectura.PULSO, null, 80.0);
        agregar(ZONA, local(0, 14, 0), OrigenLectura.PULSO, null, 88.0);
        agregar(ZONA, local(0, 18, 0), OrigenLectura.PULSO, null, 97.0);   // de tarde: fuera

        var ventana = VentanaLecturas.desde(local(0, 0, 0)).conFranja(VentanaHoraria.parse("10:00-16:00"));

        assertThat(historial.maximo(ZONA, MetricaLectura.UV, ventana)).contains(88.0);
        assertThat(historial.conteo(ZONA, MetricaLectura.UV, ventana)).isEqualTo(2);
    }

    @Test
    @DisplayName("la lectura de la hora de fin de la franja todavía entra, la del minuto siguiente no")
    void bordeDeLaFranja() {
        agregar(ZONA, local(0, 16, 0), OrigenLectura.PULSO, null, 90.0);
        agregar(ZONA, local(0, 16, 1), OrigenLectura.PULSO, null, 95.0);

        var ventana = VentanaLecturas.desde(local(0, 0, 0)).conFranja(VentanaHoraria.parse("10:00-16:00"));

        assertThat(historial.maximo(ZONA, MetricaLectura.UV, ventana)).contains(90.0);
    }

    // ----------------------------------------------------------------- sin dato

    @Test
    @DisplayName("sin lecturas en la ventana: sin dato, nunca cero")
    void sinLecturas() {
        var ventana = VentanaLecturas.desde(local(0, 0, 0));

        assertThat(historial.maximo(ZONA, MetricaLectura.CE, ventana)).isEmpty();
        assertThat(historial.minimo(ZONA, MetricaLectura.CE, ventana)).isEmpty();
        assertThat(historial.media(ZONA, MetricaLectura.CE, ventana)).isEmpty();
        assertThat(historial.conteo(ZONA, MetricaLectura.CE, ventana)).isZero();
    }

    @Test
    @DisplayName("una lectura sin esa métrica (nula) no cuenta ni baja la media")
    void metricaNulaNoCuenta() {
        agregar(ZONA, local(0, 2, 0), OrigenLectura.PULSO, 1.0, null);
        agregar(ZONA, local(0, 6, 0), OrigenLectura.PULSO, null, 50.0);
        agregar(ZONA, local(0, 10, 0), OrigenLectura.PULSO, 3.0, null);

        var ventana = VentanaLecturas.desde(local(0, 0, 0));

        assertThat(historial.media(ZONA, MetricaLectura.CE, ventana)).contains(2.0);
        assertThat(historial.conteo(ZONA, MetricaLectura.CE, ventana)).isEqualTo(2);
    }

    @Test
    @DisplayName("pocas lecturas: el conteo permite decidir sin dato (4 de las 6 exigidas)")
    void pocasLecturas() {
        for (int i = 0; i < 4; i++) {
            agregar(ZONA, local(0, 2 + 4 * i, 0), OrigenLectura.PULSO, 1.0, null);
        }

        assertThat(historial.conteo(ZONA, MetricaLectura.CE, VentanaLecturas.desde(local(0, 0, 0)).soloPulso()))
                .isLessThan(6);
    }

    @Test
    @DisplayName("no mezcla zonas")
    void otraZona() {
        agregar("MZ-2", local(0, 10, 0), OrigenLectura.PULSO, 5.0, null);

        assertThat(historial.media(ZONA, MetricaLectura.CE, VentanaLecturas.desde(local(0, 0, 0)))).isEmpty();
    }
}
