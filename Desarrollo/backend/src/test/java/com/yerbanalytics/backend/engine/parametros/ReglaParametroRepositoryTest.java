package com.yerbanalytics.backend.engine.parametros;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tabla {@code regla_parametro}: un override por copia {@code (consumidor, clave)}. Corre contra la misma PostgreSQL
 * que el resto de los tests del backend (no hay base embebida) y cada test hace rollback, así que no deja filas.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("ReglaParametroRepository")
class ReglaParametroRepositoryTest {

    @Autowired
    private ReglaParametroRepository repository;

    @Test
    void guardaYLeeUnOverridePorCopia() {
        repository.saveAndFlush(new ReglaParametroEntity("TestRuleA", "test.override-uno", "40", "Ana", 1234L));

        ReglaParametroEntity leido = repository.findById(new ReglaParametroId("TestRuleA", "test.override-uno"))
                .orElseThrow();

        assertThat(leido.getValor()).isEqualTo("40");
        assertThat(leido.getUpdatedBy()).isEqualTo("Ana");
        assertThat(leido.getUpdatedTs()).isEqualTo(1234L);
    }

    @Test
    void laMismaClaveEnDosPiezasSonDosFilas() {
        repository.saveAndFlush(new ReglaParametroEntity("TestRuleA", "test.override-dos", "40", "Ana", 1L));
        repository.saveAndFlush(new ReglaParametroEntity("TestRuleB", "test.override-dos", "50", "Beto", 2L));

        assertThat(repository.findById(new ReglaParametroId("TestRuleA", "test.override-dos")).orElseThrow().getValor())
                .isEqualTo("40");
        assertThat(repository.findById(new ReglaParametroId("TestRuleB", "test.override-dos")).orElseThrow().getValor())
                .isEqualTo("50");
    }

    @Test
    void guardarOtraVezLaMismaCopiaReemplazaElValor() {
        repository.saveAndFlush(new ReglaParametroEntity("TestRuleA", "test.override-tres", "40", "Ana", 1L));
        repository.saveAndFlush(new ReglaParametroEntity("TestRuleA", "test.override-tres", "06:00-18:00", "Beto", 2L));

        assertThat(repository.findById(new ReglaParametroId("TestRuleA", "test.override-tres")).orElseThrow().getValor())
                .isEqualTo("06:00-18:00");
    }

    @Test
    void borrarLaFilaEsRestablecerAFabrica() {
        repository.saveAndFlush(new ReglaParametroEntity("TestRuleA", "test.override-cuatro", "1", "Ana", 1L));

        repository.deleteById(new ReglaParametroId("TestRuleA", "test.override-cuatro"));
        repository.flush();

        assertThat(repository.findById(new ReglaParametroId("TestRuleA", "test.override-cuatro"))).isEmpty();
    }
}
