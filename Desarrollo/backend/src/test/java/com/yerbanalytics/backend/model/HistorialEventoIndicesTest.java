package com.yerbanalytics.backend.model;

import com.yerbanalytics.backend.repository.HistorialRepository;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El contexto de riego consulta {@code historial_evento} por zona, tipo y fecha con cada mensaje del nodo: sin un
 * índice que cubra esas columnas, en el orden en que la consulta las usa, es un barrido de toda la tabla.
 * {@code ddl-auto=update} crea los índices declarados en la entidad (no hace falta el script manual).
 */
@DisplayName("HistorialEventoEntity - índices")
class HistorialEventoIndicesTest {

    private static List<Index> indices() {
        return Arrays.asList(HistorialEventoEntity.class.getAnnotation(Table.class).indexes());
    }

    @Test
    @DisplayName("hay un índice (zona_id, tipo, ts): igualdad, lista de tipos y rango, en ese orden")
    void indiceDeLaConsultaDelContextoDeRiego() {
        assertThat(indices()).anySatisfy(i -> {
            assertThat(i.name()).isEqualTo("idx_historial_evento_zona_tipo_ts");   // el mismo nombre que el script manual
            assertThat(i.columnList().replace(" ", "")).isEqualTo("zona_id,tipo,ts");
        });
    }

    @Test
    @DisplayName("hay un índice (tipo, ts) para la reconstrucción de los riegos abiertos y los KPI por tipo")
    void indiceDeLosRiegosRecientes() {
        assertThat(indices()).anySatisfy(i -> assertThat(i.columnList().replace(" ", "")).isEqualTo("tipo,ts"));
    }

    @Test
    @DisplayName("la consulta del contexto de riego sólo mira los tipos que le importan (Riego e Insumo), por zona y fecha")
    void laConsultaUsaLasColumnasDelIndiceYSoloLosTiposDelRiego() throws Exception {
        String jpql = HistorialRepository.class.getMethod("ultimosPorSector", String.class, long.class)
                .getAnnotation(Query.class).value();

        assertThat(jpql).contains("h.zonaId = :zonaId").contains("h.tipo IN ('Riego', 'Insumo')").contains("h.ts >= :desde");
        assertThat(jpql).doesNotContain("'Info'");
    }
}
