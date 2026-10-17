package com.yerbanalytics.backend.lecturas;

import com.yerbanalytics.backend.model.LecturaZonaEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.repository.LecturaZonaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("RegistroLecturas")
class RegistroLecturasTest {

    private LecturaZonaRepository repository;
    private RegistroLecturas registro;

    @BeforeEach
    void setUp() {
        repository = mock(LecturaZonaRepository.class);
        registro = new RegistroLecturas(repository);
    }

    private static ZonaEntity zonaConLecturaCompleta() {
        ZonaEntity z = new ZonaEntity();
        z.setId("MZ-1");
        z.setHumSusRaw(48.0);
        z.setHumAmbRaw(72.0);
        z.setTempRaw(24.0);
        z.setTempSueloRaw(20.0);
        z.setUvRaw(55.0);
        z.setCeRaw(0.8);
        z.setPhSueloRaw(5.6);
        z.setNRaw(150.0);
        z.setPRaw(45.0);
        z.setKRaw(180.0);
        return z;
    }

    private LecturaZonaEntity guardada() {
        ArgumentCaptor<LecturaZonaEntity> captor = ArgumentCaptor.forClass(LecturaZonaEntity.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("guarda las diez métricas vigentes de la zona con la hora del nodo, la de recepción y el origen")
    void guardaElEstadoCompleto() {
        registro.registrar(zonaConLecturaCompleta(), 1_000L, 1_200L, OrigenLectura.PULSO);

        LecturaZonaEntity l = guardada();
        assertThat(l.getZonaId()).isEqualTo("MZ-1");
        assertThat(l.getTs()).isEqualTo(1_000L);
        assertThat(l.getRecibidaTs()).isEqualTo(1_200L);
        assertThat(l.getOrigen()).isEqualTo(OrigenLectura.PULSO);
        assertThat(l.getHumSus()).isEqualTo(48.0);
        assertThat(l.getHumAmb()).isEqualTo(72.0);
        assertThat(l.getTemp()).isEqualTo(24.0);
        assertThat(l.getTempSuelo()).isEqualTo(20.0);
        assertThat(l.getUv()).isEqualTo(55.0);
        assertThat(l.getCe()).isEqualTo(0.8);
        assertThat(l.getPhSuelo()).isEqualTo(5.6);
        assertThat(l.getN()).isEqualTo(150.0);
        assertThat(l.getP()).isEqualTo(45.0);
        assertThat(l.getK()).isEqualTo(180.0);
        assertThat(l.getDpv()).isNull();
    }

    @Test
    @DisplayName("una métrica que la zona nunca reportó queda sin dato, no en cero")
    void metricaNuncaReportadaQuedaNula() {
        ZonaEntity z = zonaConLecturaCompleta();
        z.setPhSueloRaw(null);

        registro.registrar(z, 1_000L, 1_200L, OrigenLectura.OTRA);

        LecturaZonaEntity l = guardada();
        assertThat(l.getPhSuelo()).isNull();
        assertThat(l.getOrigen()).isEqualTo(OrigenLectura.OTRA);
    }
}
