package com.yerbanalytics.backend.service.mock;

import com.yerbanalytics.backend.dto.NurseryData;
import com.yerbanalytics.backend.dto.Stats;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NurseryGeneratorTest {

    private static final int SEED = 20260613;
    private final NurseryGenerator generator = new NurseryGenerator();

    @Test
    void esDeterministico_mismaSemilla_mismasStats() {
        Stats a = generator.build(SEED).stats();
        Stats b = generator.build(SEED).stats();
        assertEquals(a, b);
    }

    @Test
    void genera600SectoresEn6MacroZonas() {
        NurseryData data = generator.build(SEED);
        assertEquals(6, data.zonas().size());
        data.zonas().forEach(z -> assertEquals(100, z.sectors().size()));
        assertEquals(600, data.sectors().size());
    }

    @Test
    void statsAgregadasSonCoherentes() {
        Stats stats = generator.build(SEED).stats();
        assertEquals(600, stats.total());
        assertEquals(600, stats.sano() + stats.warning() + stats.critical() + stats.offline());
        assertEquals(stats.warning() + stats.critical(), stats.alerta());
        assertEquals((int) Math.round((stats.sano() / 600.0) * 100), stats.sanoPct().intValue());
        assertTrue(stats.diagCount() > 0);
    }

    @Test
    void indexaSectoresPorId() {
        NurseryData data = generator.build(SEED);
        assertEquals(600, data.byId().size());
        assertNotNull(data.byId().get("MZ-1-001"));
        assertEquals(data.sectors().get(0), data.byId().get("MZ-1-001"));
    }

    @Test
    void diagnosticosNoIncluyenSanoNiSinDiagnostico() {
        NurseryData data = generator.build(SEED);
        data.diagnoses().forEach(d -> {
            assertTrue(!"Sano".equals(d.estado()));
            assertTrue(!"Sin diagn\u00f3stico".equals(d.estado()));
        });
    }
}
