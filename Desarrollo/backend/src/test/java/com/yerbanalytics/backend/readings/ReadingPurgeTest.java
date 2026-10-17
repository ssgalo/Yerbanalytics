package com.yerbanalytics.backend.readings;

import com.yerbanalytics.backend.config.ZonaHorariaVivero;
import com.yerbanalytics.backend.engine.ReglaTestSupport;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametros;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.engine.parametros.ReadingParameters;
import com.yerbanalytics.backend.engine.parametros.TipoParametro;
import com.yerbanalytics.backend.engine.parametros.ValorParametroInvalidoException;
import com.yerbanalytics.backend.engine.riego.RelojDePrueba;
import com.yerbanalytics.backend.repository.ZoneReadingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Retención del historial de lecturas")
class ReadingPurgeTest {

    private static final Instant NOW = Instant.parse("2026-10-14T06:30:00Z");   // 03:30 en el vivero

    private ZoneReadingRepository repository;
    private CatalogoParametrosService parameters;
    private ReadingPurge purge;

    @BeforeEach
    void setUp() {
        repository = mock(ZoneReadingRepository.class);
        parameters = mock(CatalogoParametrosService.class);
        when(parameters.vigentes()).thenReturn(ReglaTestSupport.fabrica());
        purge = new ReadingPurge(repository, parameters, new RelojDePrueba(NOW));
    }

    // --------------------------------------------------------- el parámetro

    @Test
    @DisplayName("readings.retention-days: entero en días, fábrica 14, entre 3 y 90")
    void parameterDefinition() {
        var def = ReadingParameters.RETENTION_DAYS;

        assertThat(def.clave()).isEqualTo("readings.retention-days");
        assertThat(def.tipo()).isEqualTo(TipoParametro.ENTERO);
        assertThat(def.unidad()).isEqualTo("días");
        assertThat(def.fabrica()).isEqualTo("14");
        assertThat(def.min()).isEqualTo(3.0);
        assertThat(def.max()).isEqualTo(90.0);
        assertThat(new CatalogoParametros(List.of()).definicion("readings.retention-days")).isPresent();
    }

    @Test
    @DisplayName("una retención de 1 día se rechaza por estar bajo el mínimo de 3; 91 por superar el máximo")
    void parameterRange() {
        var def = ReadingParameters.RETENTION_DAYS;

        assertThatThrownBy(() -> def.tipo().parsear("1", def)).isInstanceOf(ValorParametroInvalidoException.class);
        assertThatThrownBy(() -> def.tipo().parsear("91", def)).isInstanceOf(ValorParametroInvalidoException.class);
        assertThat(def.tipo().parsear("3", def)).isNotNull();
        assertThat(def.tipo().parsear("90", def)).isNotNull();
    }

    @Test
    @DisplayName("el catálogo registra a la purga como quien lee el parámetro")
    void usedBy() {
        var catalog = new CatalogoParametros(List.of(purge));

        assertThat(catalog.usadoPor("readings.retention-days")).containsExactly("ReadingPurge");
    }

    // --------------------------------------------------------- la purga

    @Test
    @DisplayName("con la retención de fábrica borra lo anterior a hace 14 días")
    void deletesWhatIsOlderThanTheRetention() {
        when(repository.deleteByTsLessThan(Instant.parse("2026-09-30T06:30:00Z").toEpochMilli())).thenReturn(7);

        assertThat(purge.purge()).isEqualTo(7);
    }

    @Test
    @DisplayName("lo de hace 5 días queda: el corte es 14 días atrás, no 5")
    void keepsWhatIsNewer() {
        purge.purge();

        long cutoff = Instant.parse("2026-09-30T06:30:00Z").toEpochMilli();
        verify(repository).deleteByTsLessThan(cutoff);
        assertThat(NOW.minus(Duration.ofDays(5)).toEpochMilli()).isGreaterThan(cutoff);
    }

    @Test
    @DisplayName("usa la retención editada en el catálogo")
    void usesTheCurrentRetention() {
        when(parameters.vigentes()).thenReturn(ReglaTestSupport.con(Map.of(ReadingParameters.RETENTION_DAYS, "30")));

        purge.purge();

        verify(repository).deleteByTsLessThan(NOW.minus(Duration.ofDays(30)).toEpochMilli());
    }

    @Test
    @DisplayName("corre todos los días a las 03:30, hora del vivero")
    void scheduledAt0330() throws Exception {
        Scheduled s = ReadingPurge.class.getMethod("scheduledPurge").getAnnotation(Scheduled.class);

        assertThat(s).isNotNull();
        assertThat(s.cron()).isEqualTo("0 30 3 * * *");
        assertThat(s.zone()).isEqualTo(ZonaHorariaVivero.ZONA.getId());
    }

    @Test
    @DisplayName("la tarea programada no deja escapar una falla de la base")
    void theTaskDoesNotThrow() {
        when(repository.deleteByTsLessThan(Instant.parse("2026-09-30T06:30:00Z").toEpochMilli()))
                .thenThrow(new IllegalStateException("base caída"));

        purge.scheduledPurge();   // no lanza: se reintenta mañana
    }
}
