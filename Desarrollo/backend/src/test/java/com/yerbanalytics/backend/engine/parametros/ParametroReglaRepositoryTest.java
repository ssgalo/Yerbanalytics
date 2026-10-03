package com.yerbanalytics.backend.engine.parametros;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tarea 2.1. Corre contra la misma PostgreSQL que el resto de los tests del backend (no hay
 * base embebida) y cada test hace rollback, así que no deja filas.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("ParametroReglaRepository")
class ParametroReglaRepositoryTest {

    @Autowired
    private ParametroReglaRepository repository;

    @Test
    void guardaYLeeUnOverridePorClave() {
        repository.saveAndFlush(new ParametroReglaEntity("test.override-uno", "40", "Ana", 1234L));

        ParametroReglaEntity leido = repository.findById("test.override-uno").orElseThrow();

        assertThat(leido.getValor()).isEqualTo("40");
        assertThat(leido.getUpdatedBy()).isEqualTo("Ana");
        assertThat(leido.getUpdatedTs()).isEqualTo(1234L);
    }

    @Test
    void guardarOtraVezLaMismaClaveReemplazaElValor() {
        repository.saveAndFlush(new ParametroReglaEntity("test.override-dos", "40", "Ana", 1L));
        repository.saveAndFlush(new ParametroReglaEntity("test.override-dos", "06:00-18:00", "Beto", 2L));

        assertThat(repository.findById("test.override-dos").orElseThrow().getValor()).isEqualTo("06:00-18:00");
    }

    @Test
    void borrarLaFilaEsRestablecerAFabrica() {
        repository.saveAndFlush(new ParametroReglaEntity("test.override-tres", "1", "Ana", 1L));

        repository.deleteById("test.override-tres");
        repository.flush();

        assertThat(repository.findById("test.override-tres")).isEmpty();
    }
}
