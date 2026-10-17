package com.yerbanalytics.backend.lecturas;

import com.yerbanalytics.backend.config.ZonaHorariaVivero;
import com.yerbanalytics.backend.engine.ReglaTestSupport;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametros;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.engine.parametros.ParametrosLecturas;
import com.yerbanalytics.backend.engine.parametros.TipoParametro;
import com.yerbanalytics.backend.engine.parametros.ValorParametroInvalidoException;
import com.yerbanalytics.backend.engine.riego.RelojDePrueba;
import com.yerbanalytics.backend.repository.LecturaZonaRepository;
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
class PurgaLecturasTest {

    private static final Instant AHORA = Instant.parse("2026-10-14T06:30:00Z");   // 03:30 en el vivero

    private LecturaZonaRepository repository;
    private CatalogoParametrosService parametros;
    private PurgaLecturas purga;

    @BeforeEach
    void setUp() {
        repository = mock(LecturaZonaRepository.class);
        parametros = mock(CatalogoParametrosService.class);
        when(parametros.vigentes()).thenReturn(ReglaTestSupport.fabrica());
        purga = new PurgaLecturas(repository, parametros, new RelojDePrueba(AHORA));
    }

    // --------------------------------------------------------- el parámetro

    @Test
    @DisplayName("lecturas.retencion-dias: entero en días, fábrica 14, entre 3 y 90")
    void definicionDelParametro() {
        var def = ParametrosLecturas.RETENCION_DIAS;

        assertThat(def.clave()).isEqualTo("lecturas.retencion-dias");
        assertThat(def.tipo()).isEqualTo(TipoParametro.ENTERO);
        assertThat(def.unidad()).isEqualTo("días");
        assertThat(def.fabrica()).isEqualTo("14");
        assertThat(def.min()).isEqualTo(3.0);
        assertThat(def.max()).isEqualTo(90.0);
        assertThat(new CatalogoParametros(List.of()).definicion("lecturas.retencion-dias")).isPresent();
    }

    @Test
    @DisplayName("una retención de 1 día se rechaza por estar bajo el mínimo de 3; 91 por superar el máximo")
    void rangoDelParametro() {
        var def = ParametrosLecturas.RETENCION_DIAS;

        assertThatThrownBy(() -> def.tipo().parsear("1", def)).isInstanceOf(ValorParametroInvalidoException.class);
        assertThatThrownBy(() -> def.tipo().parsear("91", def)).isInstanceOf(ValorParametroInvalidoException.class);
        assertThat(def.tipo().parsear("3", def)).isNotNull();
        assertThat(def.tipo().parsear("90", def)).isNotNull();
    }

    @Test
    @DisplayName("el catálogo registra a la purga como quien lee el parámetro")
    void usadoPor() {
        var catalogo = new CatalogoParametros(List.of(purga));

        assertThat(catalogo.usadoPor("lecturas.retencion-dias")).containsExactly("PurgaLecturas");
    }

    // --------------------------------------------------------- la purga

    @Test
    @DisplayName("con la retención de fábrica borra lo anterior a hace 14 días")
    void borraLoAnteriorALaRetencion() {
        when(repository.deleteByTsLessThan(Instant.parse("2026-09-30T06:30:00Z").toEpochMilli())).thenReturn(7);

        assertThat(purga.purgar()).isEqualTo(7);
    }

    @Test
    @DisplayName("lo de hace 5 días queda: el corte es 14 días atrás, no 5")
    void respetaLoNuevo() {
        purga.purgar();

        long corte = Instant.parse("2026-09-30T06:30:00Z").toEpochMilli();
        verify(repository).deleteByTsLessThan(corte);
        assertThat(AHORA.minus(Duration.ofDays(5)).toEpochMilli()).isGreaterThan(corte);
    }

    @Test
    @DisplayName("usa la retención editada en el catálogo")
    void usaLaRetencionVigente() {
        when(parametros.vigentes()).thenReturn(ReglaTestSupport.con(Map.of(ParametrosLecturas.RETENCION_DIAS, "30")));

        purga.purgar();

        verify(repository).deleteByTsLessThan(AHORA.minus(Duration.ofDays(30)).toEpochMilli());
    }

    @Test
    @DisplayName("corre todos los días a las 03:30, hora del vivero")
    void programadaALas0330() throws Exception {
        Scheduled s = PurgaLecturas.class.getMethod("purgarProgramada").getAnnotation(Scheduled.class);

        assertThat(s).isNotNull();
        assertThat(s.cron()).isEqualTo("0 30 3 * * *");
        assertThat(s.zone()).isEqualTo(ZonaHorariaVivero.ZONA.getId());
    }

    @Test
    @DisplayName("la tarea programada no deja escapar una falla de la base")
    void laTareaNoLanzaExcepciones() {
        when(repository.deleteByTsLessThan(Instant.parse("2026-09-30T06:30:00Z").toEpochMilli()))
                .thenThrow(new IllegalStateException("base caída"));

        purga.purgarProgramada();   // no lanza: se reintenta mañana
    }
}
