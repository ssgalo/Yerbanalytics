package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.RuleEffect;
import com.yerbanalytics.backend.engine.overrides.DemoControllable;
import com.yerbanalytics.backend.engine.overrides.DemoControllableRegistry;
import com.yerbanalytics.backend.engine.overrides.OperationalRuleStates;
import com.yerbanalytics.backend.engine.overrides.RuleOverridesStore;
import com.yerbanalytics.backend.engine.overrides.RuleStateEntity;
import com.yerbanalytics.backend.engine.overrides.RuleStateRepository;
import com.yerbanalytics.backend.engine.parametros.ReglasCatalogo;
import com.yerbanalytics.backend.engine.parametros.ReglasEstadoService;
import com.yerbanalytics.backend.service.HistorialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code PUT /api/rules/{name}/estado}: el interruptor operativo de cada regla, con sus validaciones. */
@WebMvcTest(ReglasEstadoController.class)
// Test del controller, sin la cadena de seguridad: los permisos se prueban en seguridad/.
@AutoConfigureMockMvc(addFilters = false)
@Import({ReglasEstadoControllerTest.Piezas.class, DemoControllableRegistry.class, OperationalRuleStates.class,
        RuleOverridesStore.class, ReglasCatalogo.class, ReglasEstadoService.class})
@DisplayName("PUT /api/rules/{name}/estado")
class ReglasEstadoControllerTest {

    private static final String CONSECUENCIA = "Se riega con el sustrato saturado.";

    /** Una pieza de acción y una protección, declaradas como lo haría una regla real. */
    private record Pieza(String name, String label, String code, RuleEffect effect, Optional<String> protection)
            implements DemoControllable {
        @Override public RuleBranch branch() { return RuleBranch.RIEGO; }
        @Override public int priority() { return name.equals("Accion") ? 10 : 5; }
    }

    @TestConfiguration
    static class Piezas {
        @Bean
        DemoControllable accion() {
            return new Pieza("Accion", "Riego por déficit", "R-01", RuleEffect.ACTS, Optional.empty());
        }

        @Bean
        DemoControllable proteccion() {
            return new Pieza("Proteccion", "Sustrato saturado", "R-04", RuleEffect.PROTECTS, Optional.of(CONSECUENCIA));
        }

        @Bean
        RuleStateRepository reglaEstadoRepository() {
            RuleStateRepository repository = mock(RuleStateRepository.class);
            when(repository.findAll()).thenReturn(List.of());
            return repository;
        }
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private RuleStateRepository repository;
    @Autowired
    private OperationalRuleStates states;

    @MockBean
    private HistorialService historial;

    @BeforeEach
    void limpiar() {
        reset(repository, historial);
        when(repository.findAll()).thenReturn(List.of());
        states.reload();
    }

    private org.springframework.test.web.servlet.ResultActions estado(String nombre, String cuerpo) throws Exception {
        return mvc.perform(put("/api/rules/" + nombre + "/estado").header("X-Usuario", "Ana López")
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    @Test
    @DisplayName("apagar una regla de acción sin motivo ni confirmación: 200 y queda guardado quién y cuándo")
    void apagarUnaReglaDeAccion() throws Exception {
        estado("Accion", "{\"activa\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("Accion"))
                .andExpect(jsonPath("$.activa").value(false))
                .andExpect(jsonPath("$.estado.updatedBy").value("Ana López"))
                .andExpect(jsonPath("$.estado.updatedTs").isNumber());

        verify(repository).save(any(RuleStateEntity.class));
        verify(historial).registrarEstadoRegla(eq("Ana López"), eq("Accion"), eq("Riego por déficit"), eq("R-01"),
                eq(false), any(), eq(null));
    }

    @Test
    @DisplayName("apagar una protección sin motivo: 400 MOTIVO_REQUERIDO y la regla sigue prendida")
    void proteccionSinMotivo() throws Exception {
        estado("Proteccion", "{\"activa\":false,\"confirmaProteccion\":true}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MOTIVO_REQUERIDO"));
        estado("Proteccion", "{\"activa\":false,\"motivo\":\"   \",\"confirmaProteccion\":true}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MOTIVO_REQUERIDO"));

        verify(repository, never()).save(any());
        verify(historial, never()).registrarEstadoRegla(anyString(), anyString(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.anyBoolean(), any(), any());
    }

    @Test
    @DisplayName("apagar una protección sin confirmar: 400 CONFIRMACION_REQUERIDA con la consecuencia")
    void proteccionSinConfirmacion() throws Exception {
        estado("Proteccion", "{\"activa\":false,\"motivo\":\"sonda en recalibración\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("CONFIRMACION_REQUERIDA"))
                .andExpect(jsonPath("$.consecuencia").value(CONSECUENCIA));
        estado("Proteccion", "{\"activa\":false,\"motivo\":\"x\",\"confirmaProteccion\":false}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("CONFIRMACION_REQUERIDA"));

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("un motivo de más de 500 caracteres: 400 MOTIVO_DEMASIADO_LARGO")
    void motivoDemasiadoLargo() throws Exception {
        String largo = "a".repeat(501);

        estado("Proteccion", "{\"activa\":false,\"motivo\":\"" + largo + "\",\"confirmaProteccion\":true}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MOTIVO_DEMASIADO_LARGO"));
        // Vale también para una regla que no es protección: el motivo se guarda, no puede ser ilimitado.
        estado("Accion", "{\"activa\":false,\"motivo\":\"" + largo + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MOTIVO_DEMASIADO_LARGO"));
        // 500 justos entran.
        estado("Accion", "{\"activa\":false,\"motivo\":\"" + "a".repeat(500) + "\"}").andExpect(status().isOk());

        verify(repository, times(1)).save(any());
    }

    @Test
    @DisplayName("apagar una protección con motivo y confirmación: 200, con la consecuencia en el catálogo y en el historial")
    void apagarUnaProteccion() throws Exception {
        estado("Proteccion", "{\"activa\":false,\"motivo\":\"sonda en recalibración\",\"confirmaProteccion\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(false))
                .andExpect(jsonPath("$.proteccion").value(CONSECUENCIA))
                .andExpect(jsonPath("$.estado.motivo").value("sonda en recalibración"));

        verify(historial).registrarEstadoRegla("Ana López", "Proteccion", "Sustrato saturado", "R-04", false,
                "sonda en recalibración", CONSECUENCIA);
    }

    @Test
    @DisplayName("prender nunca pide motivo ni confirmación")
    void prenderNoPideNada() throws Exception {
        estado("Proteccion", "{\"activa\":false,\"motivo\":\"x\",\"confirmaProteccion\":true}").andExpect(status().isOk());

        estado("Proteccion", "{\"activa\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(true));

        verify(historial).registrarEstadoRegla("Ana López", "Proteccion", "Sustrato saturado", "R-04", true, null, null);
    }

    @Test
    @DisplayName("nombre desconocido: 404")
    void nombreDesconocido() throws Exception {
        estado("NoExiste", "{\"activa\":false}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("mismo estado que el vigente: 200 sin fila ni evento de historial, ni siquiera pide motivo")
    void mismoEstado() throws Exception {
        estado("Accion", "{\"activa\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(true))
                .andExpect(jsonPath("$.estado").doesNotExist());
        // Una protección ya apagada (por un cambio previo) vuelve a pedirse apagada, sin motivo: es un no-op.
        estado("Proteccion", "{\"activa\":false,\"motivo\":\"x\",\"confirmaProteccion\":true}").andExpect(status().isOk());
        reset(repository, historial);
        when(repository.findAll()).thenReturn(List.of());

        estado("Proteccion", "{\"activa\":false}").andExpect(status().isOk());

        verify(repository, never()).save(any());
        verify(historial, never()).registrarEstadoRegla(anyString(), anyString(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.anyBoolean(), any(), any());
    }

    @Test
    @DisplayName("sin el campo activa: 400 ACTIVA_REQUERIDA")
    void sinActiva() throws Exception {
        estado("Accion", "{\"motivo\":\"x\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ACTIVA_REQUERIDA"));
    }
}
