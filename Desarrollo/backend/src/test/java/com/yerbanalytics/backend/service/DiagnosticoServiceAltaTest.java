package com.yerbanalytics.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yerbanalytics.backend.dto.Detection;
import com.yerbanalytics.backend.dto.Diagnostico;
import com.yerbanalytics.backend.exception.DiagnosisNotFoundException;
import com.yerbanalytics.backend.model.CapturaEntity;
import com.yerbanalytics.backend.model.DiagnosticoEntity;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.repository.CapturaRepository;
import com.yerbanalytics.backend.repository.DiagnosticoRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
import com.yerbanalytics.backend.service.DiagnosticoService.NuevoDiagnostico;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Alta de diagnósticos con el contrato de {@code add-diagnostico-por-detecciones} (design D7):
 * coherencia estado / severidad / nivel de confianza / score y validación de las detecciones.
 * Sin base: los repositorios son mocks, así que corre igual con o sin la migración manual.
 */
@DisplayName("DiagnosticoService - alta (design D7)")
class DiagnosticoServiceAltaTest {

    private DiagnosticoRepository repo;
    private SectorRepository sectorRepo;
    private DiagnosticoService service;
    private SectorEntity sector;

    private static final List<Double> CAJA = List.of(0.12, 0.30, 0.18, 0.37);

    @BeforeEach
    void setUp() {
        repo = mock(DiagnosticoRepository.class);
        CapturaRepository capturaRepo = mock(CapturaRepository.class);
        sectorRepo = mock(SectorRepository.class);
        service = new DiagnosticoService(repo, capturaRepo, sectorRepo, new ObjectMapper());

        CapturaEntity captura = new CapturaEntity();
        captura.setId("CAP-1");
        captura.setSectorId("MZ-2-005");
        captura.setZonaId("MZ-2");
        when(capturaRepo.findById("CAP-1")).thenReturn(Optional.of(captura));

        sector = new SectorEntity();
        sector.setId("MZ-2-005");
        sector.setDiagnosisEstado("Sin diagnóstico");
        sector.setDiagnosisSev("—");
        when(sectorRepo.existsById("MZ-2-005")).thenReturn(true);
        when(sectorRepo.findById("MZ-2-005")).thenReturn(Optional.of(sector));
        when(repo.save(any(DiagnosticoEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static NuevoDiagnostico alta(String estado, String sev, String confidence, Double conf) {
        return alta(estado, sev, confidence, conf, null);
    }

    private static NuevoDiagnostico alta(String estado, String sev, String confidence, Double conf,
                                         List<Detection> detections) {
        return new NuevoDiagnostico("CAP-1", null, null, estado, sev, confidence, conf, detections, null);
    }

    private static NuevoDiagnostico altaConRecorte(List<Double> crop) {
        return new NuevoDiagnostico("CAP-1", null, null, "Sano", null, "Alta", 91.0,
                List.of(new Detection("Sano", 0.91, CAJA)), crop);
    }

    private void rechaza(NuevoDiagnostico d) {
        assertThatThrownBy(() -> service.alta(d)).isInstanceOf(IllegalArgumentException.class);
        verify(repo, never()).save(any());
    }

    @Nested
    @DisplayName("Tabla de coherencia")
    class Coherencia {

        @ParameterizedTest
        @ValueSource(strings = {"Clorosis", "Estrés solar", "Plaga foliar", "Daño fúngico", "Daño biótico", "Ácaro"})
        void anomaloCompleto_seRegistra(String estado) {
            Diagnostico d = service.alta(alta(estado, "Baja", "Media", 62.0));
            assertThat(d.estado()).isEqualTo(estado);
            assertThat(d.sev()).isEqualTo("Baja");
            assertThat(d.confidence()).isEqualTo("Media");
            assertThat(d.conf()).isEqualTo(62.0);
            assertThat(d.concluyente()).isTrue();
        }

        @Test
        void anomaloSinSeveridad_400() {
            rechaza(alta("Clorosis", null, "Media", 62.0));
        }

        @Test
        void anomaloConSeveridadGuion_cuentaComoSinSeveridad_400() {
            rechaza(alta("Clorosis", "—", "Media", 62.0));
        }

        @Test
        void anomaloSinConfianza_400() {
            rechaza(alta("Clorosis", "Baja", null, 62.0));
        }

        @Test
        void anomaloSinScore_400() {
            rechaza(alta("Clorosis", "Baja", "Media", null));
        }

        @Test
        void sano_sinSeveridad_seRegistraConGuion() {
            Diagnostico d = service.alta(alta("Sano", null, "Alta", 91.0));
            assertThat(d.sev()).isEqualTo("—");
            assertThat(d.confidence()).isEqualTo("Alta");
            assertThat(d.concluyente()).isTrue();
        }

        @Test
        void sano_conSeveridadGuion_seAcepta() {
            assertThat(service.alta(alta("Sano", "—", "Alta", 91.0)).sev()).isEqualTo("—");
        }

        @Test
        void sanoConSeveridad_400() {
            rechaza(alta("Sano", "Baja", "Alta", 91.0));
        }

        @Test
        void sanoSinConfianza_400() {
            rechaza(alta("Sano", null, null, 91.0));
        }

        @Test
        void sanoSinScore_400() {
            rechaza(alta("Sano", null, "Alta", null));
        }

        @Test
        void noConcluyente_sinNada_seRegistraNoConcluyente() {
            Diagnostico d = service.alta(alta("No concluyente", null, null, null));
            assertThat(d.conf()).isNull();
            assertThat(d.confidence()).isNull();
            assertThat(d.sev()).isEqualTo("—");
            assertThat(d.concluyente()).isFalse();
        }

        @Test
        void noConcluyenteConConfianza_400() {
            rechaza(alta("No concluyente", null, "Baja", null));
        }

        @Test
        void noConcluyenteConScore_400() {
            rechaza(alta("No concluyente", null, null, 40.0));
        }

        @Test
        void noConcluyenteConSeveridad_400() {
            rechaza(alta("No concluyente", "Baja", null, null));
        }

        @Test
        void anomaloConConfianzaBaja_esConcluyente() {
            // D9: concluyente depende del estado, no de un umbral sobre el score.
            assertThat(service.alta(alta("Clorosis", "Baja", "Baja", 12.0)).concluyente()).isTrue();
        }
    }

    @Nested
    @DisplayName("Niveles y rangos")
    class Niveles {

        @Test
        void nivelDeConfianzaDesconocido_400() {
            rechaza(alta("Clorosis", "Baja", "Altísima", 62.0));
        }

        @Test
        void severidadDesconocida_400() {
            rechaza(alta("Clorosis", "Grave", "Media", 62.0));
        }

        @ParameterizedTest
        @ValueSource(doubles = {-1, 100.01, 250.5})
        void scoreFueraDeRango_400(double conf) {
            rechaza(alta("Clorosis", "Baja", "Media", conf));
        }

        @Test
        void estadoFueraDeLaTaxonomia_400() {
            rechaza(alta("Se ve feo", "Baja", "Media", 62.0));
        }

        @Test
        void sinCaptura_400() {
            assertThatThrownBy(() -> service.alta(new NuevoDiagnostico(null, null, null, "Sano", null, "Alta", 90.0, null, null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Detecciones")
    class Detecciones {

        @Test
        void sinDetecciones_quedaConCero() {
            Diagnostico d = service.alta(alta("Clorosis", "Baja", "Media", 62.0));
            assertThat(d.detectionCount()).isZero();
        }

        @Test
        void conDetecciones_seGuardanComoJsonConSuCantidad() throws Exception {
            List<Detection> dets = List.of(
                    new Detection("Clorosis", 0.62, CAJA),
                    new Detection("Sano", 0.81, List.of(0.5, 0.5, 0.6, 0.62)));
            Diagnostico d = service.alta(alta("Clorosis", "Baja", "Media", 62.0, dets));

            assertThat(d.detectionCount()).isEqualTo(2);
            ArgumentCaptor<DiagnosticoEntity> captor = ArgumentCaptor.forClass(DiagnosticoEntity.class);
            verify(repo).save(captor.capture());
            DiagnosticoEntity e = captor.getValue();
            assertThat(e.getDetectionCount()).isEqualTo(2);
            List<Detection> leidas = Arrays.asList(new ObjectMapper().readValue(e.getDetections(), Detection[].class));
            assertThat(leidas).containsExactlyElementsOf(dets);
        }

        @Test
        void cajaFueraDeRango_rechazaElAltaCompleta() {
            rechaza(alta("Clorosis", "Baja", "Media", 62.0, List.of(
                    new Detection("Clorosis", 0.62, CAJA),
                    new Detection("Clorosis", 0.70, List.of(0.1, 0.2, 1.2, 0.4)))));
        }

        @Test
        void cajaConX1MayorOIgualQueX2_400() {
            rechaza(alta("Clorosis", "Baja", "Media", 62.0,
                    List.of(new Detection("Clorosis", 0.62, List.of(0.4, 0.2, 0.4, 0.5)))));
        }

        @Test
        void cajaConY1MayorQueY2_400() {
            rechaza(alta("Clorosis", "Baja", "Media", 62.0,
                    List.of(new Detection("Clorosis", 0.62, List.of(0.1, 0.6, 0.4, 0.5)))));
        }

        @Test
        void cajaDeTresValores_400() {
            rechaza(alta("Clorosis", "Baja", "Media", 62.0,
                    List.of(new Detection("Clorosis", 0.62, List.of(0.1, 0.2, 0.4)))));
        }

        @Test
        void cajaConUnNulo_400() {
            rechaza(alta("Clorosis", "Baja", "Media", 62.0,
                    List.of(new Detection("Clorosis", 0.62, Arrays.asList(0.1, null, 0.4, 0.5)))));
        }

        @Test
        void scoreDeDeteccionFueraDeRango_400() {
            rechaza(alta("Clorosis", "Baja", "Media", 62.0,
                    List.of(new Detection("Clorosis", 1.2, CAJA))));
        }

        @Test
        void claseFueraDeLaTaxonomia_400() {
            rechaza(alta("Clorosis", "Baja", "Media", 62.0,
                    List.of(new Detection("colapso_hongos", 0.5, CAJA))));
        }

        @Test
        void hasta2000Detecciones_seAceptan() {
            List<Detection> dets = new ArrayList<>(Collections.nCopies(2000, new Detection("Clorosis", 0.5, CAJA)));
            assertThat(service.alta(alta("Clorosis", "Alta", "Media", 50.0, dets)).detectionCount()).isEqualTo(2000);
        }

        @Test
        void masDe2000Detecciones_400() {
            List<Detection> dets = new ArrayList<>(Collections.nCopies(2001, new Detection("Clorosis", 0.5, CAJA)));
            rechaza(alta("Clorosis", "Alta", "Media", 50.0, dets));
        }
    }

    @Test
    void elAltaActualizaElDiagnosticoVigenteDelSectorConSuConfianza() {
        service.alta(alta("Clorosis", "Baja", "Media", 62.0));

        assertThat(sector.getDiagnosisEstado()).isEqualTo("Clorosis");
        assertThat(sector.getDiagnosisSev()).isEqualTo("Baja");
        assertThat(sector.getDiagnosisConfidence()).isEqualTo("Media");
        assertThat(sector.getDiagnosisConf()).isEqualTo(62.0);
        assertThat(sector.getDiagnosisTs()).isNotNull();
        verify(sectorRepo).save(sector);
    }

    @Test
    void noConcluyente_dejaElSectorSinScoreNiConfianza() {
        sector.setDiagnosisConf(90.0);
        sector.setDiagnosisConfidence("Alta");

        service.alta(alta("No concluyente", null, null, null));

        assertThat(sector.getDiagnosisEstado()).isEqualTo("No concluyente");
        assertThat(sector.getDiagnosisConf()).isNull();
        assertThat(sector.getDiagnosisConfidence()).isNull();
        assertThat(sector.getDiagnosisSev()).isEqualTo("—");
    }

    @Test
    void consultaDeDetecciones_deUnoInexistente_lanzaNoEncontrado() {
        when(repo.findById("DX-99999")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.detecciones("DX-99999")).isInstanceOf(DiagnosisNotFoundException.class);
    }

    @Test
    void consultaDeDetecciones_deUnoViejoSinColumna_devuelveVacio() {
        DiagnosticoEntity viejo = new DiagnosticoEntity();
        viejo.setId("DX-00001");
        when(repo.findById("DX-00001")).thenReturn(Optional.of(viejo));
        assertThat(service.detecciones("DX-00001").detections()).isEmpty();
        assertThat(service.detecciones("DX-00001").crop()).isNull();
        assertThat(viejo.getDetectionCount()).isZero();
    }

    @Nested
    @DisplayName("Recorte (add-tiling-inferencia, D5)")
    class Recorte {

        private DiagnosticoEntity guardado() {
            ArgumentCaptor<DiagnosticoEntity> captor = ArgumentCaptor.forClass(DiagnosticoEntity.class);
            verify(repo).save(captor.capture());
            return captor.getValue();
        }

        @Test
        void conRecorte_seGuardaYSeDevuelveConLasDetecciones() {
            List<Double> crop = List.of(0.2574, 0.2542, 1.0, 1.0);
            service.alta(altaConRecorte(crop));

            DiagnosticoEntity e = guardado();
            assertThat(e.getCrop()).isEqualTo("[0.2574,0.2542,1.0,1.0]");
            when(repo.findById(e.getId())).thenReturn(Optional.of(e));
            assertThat(service.detecciones(e.getId()).crop()).containsExactlyElementsOf(crop);
            assertThat(service.detecciones(e.getId()).detections()).hasSize(1);
        }

        @Test
        void sinRecorte_quedaSinRecorte() {
            service.alta(altaConRecorte(null));

            DiagnosticoEntity e = guardado();
            assertThat(e.getCrop()).isNull();
            when(repo.findById(e.getId())).thenReturn(Optional.of(e));
            assertThat(service.detecciones(e.getId()).crop()).isNull();
        }

        @Test
        void recorteFueraDeRango_400QueNombraElCampo() {
            assertThatThrownBy(() -> service.alta(altaConRecorte(List.of(0.1, 0.2, 1.2, 0.9))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("crop");
            verify(repo, never()).save(any());
        }

        @Test
        void recorteConX1MayorOIgualQueX2_400() {
            rechaza(altaConRecorte(List.of(0.5, 0.2, 0.5, 0.9)));
        }

        @Test
        void recorteConY1MayorQueY2_400() {
            rechaza(altaConRecorte(List.of(0.1, 0.9, 0.5, 0.2)));
        }

        @Test
        void recorteDeTresValores_400() {
            rechaza(altaConRecorte(List.of(0.1, 0.2, 0.5)));
        }

        @Test
        void recorteConUnNulo_400() {
            rechaza(altaConRecorte(Arrays.asList(0.1, null, 0.5, 0.9)));
        }
    }

    @Test
    void concluyente_esEstadoDistintoDeNoConcluyente() {
        assertThat(DiagnosticoService.esConcluyente("Clorosis")).isTrue();
        assertThat(DiagnosticoService.esConcluyente("Sano")).isTrue();
        assertThat(DiagnosticoService.esConcluyente("No concluyente")).isFalse();
    }
}
