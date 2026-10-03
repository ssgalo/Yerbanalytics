package com.yerbanalytics.backend.engine.parametros;

import com.yerbanalytics.backend.dto.CatalogoReglasDto;
import com.yerbanalytics.backend.dto.ParametroDto;
import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.service.HistorialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.http.MediaType;
import com.yerbanalytics.backend.controller.ReglasParametrosController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.lenient;

/** Tareas 2.2 y 2.3: valor vigente, cache y guardado todo-o-nada. */
@DisplayName("CatalogoParametrosService")
@ExtendWith(MockitoExtension.class)
class CatalogoParametrosServiceTest {

    @Mock
    private ParametroReglaRepository repository;
    @Mock
    private HistorialService historialService;
    @Mock
    private PlatformTransactionManager transactionManager;

    /** Estado de la "tabla" parametro_regla que simulan los stubs del repositorio. */
    private final Map<String, ParametroReglaEntity> tabla = new ConcurrentHashMap<>();

    private CatalogoParametros catalogo;
    private CatalogoParametrosService service;

    private static Rule regla(String nombre, int prioridad, RuleBranch rama, DefinicionParametro... params) {
        return new Rule() {
            @Override public int priority() { return prioridad; }
            @Override public String name() { return nombre; }
            @Override public String label() { return "Etiqueta " + nombre; }
            @Override public RuleBranch branch() { return rama; }
            @Override public List<DefinicionParametro> parametros() { return List.of(params); }
            @Override public List<RuleAction> evaluate(RuleContext ctx) { return List.of(); }
        };
    }

    @BeforeEach
    void setUp() {
        catalogo = new CatalogoParametros(
                new ArrayList<DefinicionParametro>(Arrays.asList(ParametrosRiegoV2Fixture.values())),
                ParametrosRiegoV2Fixture.RESTRICCIONES,
                List.of(regla("IrrigationRule", 10, RuleBranch.RIEGO,
                                ParametrosRiegoV2Fixture.UMBRAL_HUMEDAD, ParametrosRiegoV2Fixture.LITROS_POR_PUNTO),
                        regla("WeatherOverrideRule", 5, RuleBranch.RIEGO, ParametrosRiegoV2Fixture.UMBRAL_HUMEDAD),
                        regla("ManualLockRule", 1, RuleBranch.GLOBAL)));

        lenient().when(repository.findAll()).thenAnswer(i -> new ArrayList<>(tabla.values()));
        lenient().when(repository.save(any(ParametroReglaEntity.class))).thenAnswer(i -> {
            ParametroReglaEntity e = i.getArgument(0);
            tabla.put(e.getClave(), e);
            return e;
        });
        lenient().doAnswer(i -> tabla.remove(i.<String>getArgument(0))).when(repository).deleteById(anyString());

        service = new CatalogoParametrosService(catalogo, repository, historialService, transactionManager);
    }

    private void override(String clave, String valor) {
        tabla.put(clave, new ParametroReglaEntity(clave, valor, "Ana", 1L));
    }

    private static CambioParametro cambio(String clave, String valor) {
        return new CambioParametro(clave, valor);
    }

    // ------------------------------------------------------------------ 2.2

    @Test
    void vigentes_sinOverridesDevuelveFabrica() {
        assertThat(service.vigentes().numero("riego.umbral-humedad")).isEqualTo(45.0);
        assertThat(service.vigentes().ventana("riego.ventana-normal").canonico()).isEqualTo("06:00-18:00");
    }

    @Test
    void vigentes_conOverrideDevuelveElOverrideYElRestoFabrica() {
        override("riego.umbral-humedad", "50");

        assertThat(service.vigentes().numero("riego.umbral-humedad")).isEqualTo(50.0);
        assertThat(service.vigentes().numero("riego.humedad-objetivo")).isEqualTo(65.0);
    }

    @Test
    void vigentes_devuelveElMismoObjetoEnDosLlamadasSeguidas_yLeeLaBaseUnaSolaVez() {
        ParametrosVigentes a = service.vigentes();
        ParametrosVigentes b = service.vigentes();

        assertThat(b).isSameAs(a);
        verify(repository, org.mockito.Mockito.times(1)).findAll();
    }

    @Test
    void vigentes_ignoraOverridesHuerfanosOCorruptosEnVezDeRomperElMotor() {
        override("riego.clave-que-ya-no-existe", "1");
        override("riego.umbral-humedad", "no-es-un-numero");
        override("riego.humedad-objetivo", "70");

        ParametrosVigentes v = service.vigentes();

        assertThat(v.numero("riego.umbral-humedad")).isEqualTo(45.0);
        assertThat(v.numero("riego.humedad-objetivo")).isEqualTo(70.0);
    }

    // ------------------------------------------------------------------ 2.3

    @Test
    void guardar_aplicaElCambioAudita_yDevuelveElCatalogoActualizado() {
        CatalogoReglasDto out = service.guardar(List.of(cambio("riego.umbral-humedad", "50")), "Ana");

        ParametroReglaEntity e = tabla.get("riego.umbral-humedad");
        assertThat(e.getValor()).isEqualTo("50");
        assertThat(e.getUpdatedBy()).isEqualTo("Ana");
        assertThat(e.getUpdatedTs()).isNotNull().isPositive();
        ParametroDto p = out.parametros().stream().filter(x -> x.clave().equals("riego.umbral-humedad")).findFirst().orElseThrow();
        assertThat(p.valor()).isEqualTo("50");
        assertThat(p.modificado()).isTrue();
        assertThat(p.updatedBy()).isEqualTo("Ana");
    }

    @Test
    void guardar_normalizaAlFormatoCanonico() {
        service.guardar(List.of(cambio("riego.umbral-humedad", " 50.0 ")), "Ana");

        assertThat(tabla.get("riego.umbral-humedad").getValor()).isEqualTo("50");
    }

    @Test
    void guardar_esTodoONada_unCambioInvalidoNoPersisteNada() {
        override("riego.humedad-objetivo", "70");

        assertThatThrownBy(() -> service.guardar(List.of(
                cambio("riego.umbral-humedad", "50"),
                cambio("riego.litros-por-punto", "0.9"),
                cambio("riego.inexistente", "1")), "Ana"))
                .isInstanceOfSatisfying(ParametrosInvalidosException.class, ex ->
                        assertThat(ex.getErrores()).extracting(ErrorParametro::clave)
                                .containsExactlyInAnyOrder("riego.litros-por-punto", "riego.inexistente"));

        assertThat(tabla).containsOnlyKeys("riego.humedad-objetivo");
        verify(repository, never()).save(any());
        verify(repository, never()).deleteById(anyString());
        verifyNoInteractions(historialService);
    }

    @Test
    void guardar_restriccionCruzadaSeEvaluaSobreElConjuntoResultante() {
        // Subir el objetivo junto con el umbral es válido aunque el umbral solo superaría al objetivo vigente (65).
        service.guardar(List.of(cambio("riego.umbral-humedad", "60"), cambio("riego.humedad-objetivo", "75")), "Ana");
        assertThat(service.vigentes().numero("riego.umbral-humedad")).isEqualTo(60.0);

        // Pero el umbral solo, por encima del objetivo, no.
        tabla.clear();
        service.invalidar();
        assertThatThrownBy(() -> service.guardar(List.of(cambio("riego.umbral-humedad", "60"),
                cambio("riego.humedad-objetivo", "55")), "Ana"))
                .isInstanceOfSatisfying(ParametrosInvalidosException.class, ex ->
                        assertThat(ex.getErrores()).extracting(ErrorParametro::mensaje)
                                .contains("El umbral de riego debe ser menor que la humedad objetivo."));
        assertThat(tabla).isEmpty();
    }

    @Test
    void guardar_restriccionVioladaPorUnSoloCambioApuntaAlaClaveCambiada() {
        assertThatThrownBy(() -> service.guardar(List.of(cambio("riego.umbral-humedad", "35")), "Ana"))
                .isInstanceOfSatisfying(ParametrosInvalidosException.class, ex ->
                        assertThat(ex.getErrores()).extracting(ErrorParametro::clave).containsExactly("riego.umbral-humedad"));
    }

    @Test
    void guardar_claveRepetidaEnElLoteSeRechaza() {
        assertThatThrownBy(() -> service.guardar(List.of(
                cambio("riego.umbral-humedad", "50"), cambio("riego.umbral-humedad", "51")), "Ana"))
                .isInstanceOfSatisfying(ParametrosInvalidosException.class, ex ->
                        assertThat(ex.getErrores()).extracting(ErrorParametro::clave).contains("riego.umbral-humedad"));
    }

    @Test
    void guardar_valorNullBorraElOverride() {
        override("riego.umbral-humedad", "50");
        assertThat(service.vigentes().numero("riego.umbral-humedad")).isEqualTo(50.0);

        service.guardar(List.of(cambio("riego.umbral-humedad", null)), "Ana");

        assertThat(tabla).doesNotContainKey("riego.umbral-humedad");
        verify(repository).deleteById("riego.umbral-humedad");
        assertThat(service.vigentes().numero("riego.umbral-humedad")).isEqualTo(45.0);
    }

    @Test
    void guardar_unValorIgualAFabricaEquivaleARestablecer() {
        override("riego.umbral-humedad", "50");

        service.guardar(List.of(cambio("riego.umbral-humedad", "45")), "Ana");

        assertThat(tabla).doesNotContainKey("riego.umbral-humedad");
    }

    @Test
    void guardar_invalidaElCache() {
        ParametrosVigentes antes = service.vigentes();

        service.guardar(List.of(cambio("riego.umbral-humedad", "50")), "Ana");
        ParametrosVigentes despues = service.vigentes();

        assertThat(antes.numero("riego.umbral-humedad")).isEqualTo(45.0);
        assertThat(despues).isNotSameAs(antes);
        assertThat(despues.numero("riego.umbral-humedad")).isEqualTo(50.0);
    }

    @Test
    void guardar_usuarioEnBlancoUsaElDeLaConfiguracion() {
        service.guardar(List.of(cambio("riego.umbral-humedad", "50")), "  ");

        assertThat(tabla.get("riego.umbral-humedad").getUpdatedBy()).isEqualTo("Ingeniero Agrónomo");
    }

    @Test
    void guardar_asientaElEventoDeConfiguracionConLasClavesCambiadas() {
        service.guardar(List.of(cambio("riego.umbral-humedad", "50"), cambio("riego.litros-por-punto", "0.3")), "Ana");

        ArgumentCaptor<String> detalle = ArgumentCaptor.forClass(String.class);
        verify(historialService).registrarConfiguracion(org.mockito.ArgumentMatchers.eq("Ana"), detalle.capture());
        assertThat(detalle.getValue()).contains("riego.umbral-humedad").contains("riego.litros-por-punto");
    }

    @Test
    void guardar_sinCambiosEfectivosNoEscribeNiAudita() {
        service.guardar(List.of(cambio("riego.umbral-humedad", null), cambio("riego.humedad-objetivo", "65")), "Ana");

        verify(repository, never()).save(any());
        verify(repository, never()).deleteById(anyString());
        verifyNoInteractions(historialService);
    }

    @Test
    void guardar_loteVacioONuloDevuelveElCatalogoSinTocarNada() {
        assertThat(service.guardar(List.of(), "Ana").parametros()).hasSize(15);
        assertThat(service.guardar(null, "Ana").parametros()).hasSize(15);
        verifyNoInteractions(historialService);
    }

    // ------------------------------------------------------------------ catálogo (DTO)

    @Test
    void catalogo_listaCadaParametroUnaVezConSuUsadoPor_yLasReglasPorPrioridad() {
        override("riego.umbral-humedad", "50");

        CatalogoReglasDto dto = service.catalogo();

        assertThat(dto.parametros()).extracting(ParametroDto::clave).doesNotHaveDuplicates().hasSize(15);
        ParametroDto umbral = dto.parametros().stream().filter(p -> p.clave().equals("riego.umbral-humedad")).findFirst().orElseThrow();
        assertThat(umbral.usadoPor()).containsExactly("WeatherOverrideRule", "IrrigationRule");
        assertThat(umbral.fabrica()).isEqualTo("45");
        assertThat(umbral.valor()).isEqualTo("50");
        assertThat(umbral.familia()).isEqualTo("RIEGO");
        assertThat(umbral.tipo()).isEqualTo("NUMERO");
        assertThat(umbral.min()).isEqualTo(35.0);
        assertThat(umbral.max()).isEqualTo(60.0);
        assertThat(umbral.modificado()).isTrue();

        ParametroDto sinUso = dto.parametros().stream().filter(p -> p.clave().equals("riego.lluvia-mm")).findFirst().orElseThrow();
        assertThat(sinUso.usadoPor()).isEmpty();
        assertThat(sinUso.modificado()).isFalse();
        assertThat(sinUso.updatedBy()).isNull();

        assertThat(dto.reglas()).extracting("id").containsExactly("ManualLockRule", "WeatherOverrideRule", "IrrigationRule");
        assertThat(dto.reglas().get(1).parametros()).containsExactly("riego.umbral-humedad");
        assertThat(dto.reglas().get(0).parametros()).isEmpty();
        assertThat(dto.reglas().get(2).rama()).isEqualTo("RIEGO");
        assertThat(dto.reglas().get(2).prioridad()).isEqualTo(10);
    }

    // ------------------------------------------------------------------ revisión del bloque 1

    @Test
    void cache_unaLecturaQueEmpezoAntesDeUnaInvalidacionNoPublicaSuEstadoViejo() {
        override("riego.umbral-humedad", "50");
        AtomicInteger llamadas = new AtomicInteger();
        // Primera carga: toma la foto vieja y, antes de devolverla, "otro hilo" guarda y invalida.
        org.mockito.Mockito.when(repository.findAll()).thenAnswer(i -> {
            List<ParametroReglaEntity> foto = new ArrayList<>(tabla.values());
            if (llamadas.getAndIncrement() == 0) {
                override("riego.umbral-humedad", "55");
                service.invalidar();
            }
            return foto;
        });

        ParametrosVigentes durante = service.vigentes();
        ParametrosVigentes despues = service.vigentes();

        assertThat(durante.numero("riego.umbral-humedad")).isEqualTo(50.0);
        assertThat(despues.numero("riego.umbral-humedad")).isEqualTo(55.0);
    }

    @Test
    void cache_noSeRepueblaMientrasHayUnaTransaccionActiva() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.vigentes();
            service.vigentes();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(repository, org.mockito.Mockito.times(2)).findAll();
    }

    @Test
    void guardar_alCerrarLaTransaccionInvalidaLoQueOtroHiloRepoblo() {
        ParametrosVigentes previo;
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.guardar(List.of(cambio("riego.umbral-humedad", "50")), "Ana");
            // Otro hilo (sin transacción) repuebla el cache entre el guardado y el commit.
            previo = CompletableFuture.supplyAsync(service::vigentes).join();
            for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
                s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        assertThat(service.vigentes()).isNotSameAs(previo);
    }

    @Test
    void guardar_unElementoNuloEnElLoteEsUnErrorDeValidacionNoUnNpe() {
        assertThatThrownBy(() -> service.guardar(Arrays.asList((CambioParametro) null), "Ana"))
                .isInstanceOf(ParametrosInvalidosException.class);
    }

    @Test
    void put_conUnElementoNuloDevuelve400ConElFormatoDeErrores() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ReglasParametrosController(service)).build();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/rules/parametros")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cambios\":[null]}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.errores.length()").value(1));
    }

    @Test
    void guardar_restablecerBorraUnOverrideCorruptoDeLaBase() {
        override("riego.umbral-humedad", "abc");

        service.guardar(List.of(cambio("riego.umbral-humedad", null)), "Ana");

        assertThat(tabla).doesNotContainKey("riego.umbral-humedad");
        verify(repository).deleteById("riego.umbral-humedad");
    }

    @Test
    void guardar_unOverrideHuerfanoDeUnaClaveInexistenteSigueIgnorandose() {
        override("riego.clave-que-ya-no-existe", "1");

        service.guardar(List.of(cambio("riego.umbral-humedad", "50")), "Ana");

        assertThat(tabla).containsKeys("riego.clave-que-ya-no-existe", "riego.umbral-humedad");
        verify(repository, never()).deleteById("riego.clave-que-ya-no-existe");
    }

    @Test
    void guardar_dosGuardadosConcurrentesSeSerializanYElSegundoValidaContraElResultadoDelPrimero() throws Exception {
        CountDownLatch primeroAdentro = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        AtomicInteger lecturas = new AtomicInteger();
        org.mockito.Mockito.when(repository.findAll()).thenAnswer(i -> {
            List<ParametroReglaEntity> foto = new ArrayList<>(tabla.values());
            if (lecturas.incrementAndGet() == 1) {
                primeroAdentro.countDown();
                liberar.await(5, TimeUnit.SECONDS);
            }
            return foto;
        });

        // A: umbral 60 + objetivo 75 (válido). B: objetivo 55 solo — válido contra la base vieja
        // (umbral 45), inválido contra el resultado de A (umbral 60).
        CompletableFuture<Object> a = CompletableFuture.supplyAsync(() ->
                service.guardar(List.of(cambio("riego.umbral-humedad", "60"), cambio("riego.humedad-objetivo", "75")), "Ana"));
        assertThat(primeroAdentro.await(5, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<Object> b = CompletableFuture.supplyAsync(() ->
                service.guardar(List.of(cambio("riego.humedad-objetivo", "55")), "Beto"));

        Thread.sleep(300);
        assertThat(lecturas.get()).as("B no puede leer la base mientras A guarda").isEqualTo(1);
        liberar.countDown();

        a.get(5, TimeUnit.SECONDS);
        assertThatThrownBy(() -> b.get(5, TimeUnit.SECONDS))
                .hasCauseInstanceOf(ParametrosInvalidosException.class);
        assertThat(tabla.get("riego.humedad-objetivo").getValor()).isEqualTo("75");
    }
}
