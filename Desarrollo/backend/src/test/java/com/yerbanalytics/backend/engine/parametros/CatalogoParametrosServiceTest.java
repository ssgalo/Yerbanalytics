package com.yerbanalytics.backend.engine.parametros;

import com.yerbanalytics.backend.dto.CatalogoReglasDto;
import com.yerbanalytics.backend.dto.ParametroDto;
import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.engine.overrides.DemoControllableRegistry;
import com.yerbanalytics.backend.engine.overrides.OperationalRuleStates;
import com.yerbanalytics.backend.engine.overrides.RuleStateRepository;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.model.ConfiguracionOperativaEntity;
import com.yerbanalytics.backend.repository.ConfiguracionOperativaRepository;
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
import java.util.Optional;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.lenient;

/** Tareas 2.2 y 2.3: valor vigente, cache y guardado todo-o-nada. */
@DisplayName("CatalogoParametrosService")
@ExtendWith(MockitoExtension.class)
class CatalogoParametrosServiceTest {

    @Mock
    private ReglaParametroRepository repository;
    @Mock
    private HistorialService historialService;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private ConfiguracionOperativaRepository operativaRepository;

    /**
     * Estado de la "tabla" regla_parametro que simulan los stubs del repositorio. Las copias de la pieza principal
     * ({@link #PIEZA}) van por clave sola, para leer los asserts; las de las demás, por {@code pieza/clave}.
     */
    private final Map<String, ReglaParametroEntity> tabla = new ConcurrentHashMap<>();

    /** La pieza principal de los tests: la que tiene copia de casi todo. */
    private static final String PIEZA = "IrrigationRule";
    /** La otra regla que lee el umbral de riego (con su propia copia). */
    private static final String OTRA = "WeatherOverrideRule";

    private static String k(String regla, String clave) {
        return PIEZA.equals(regla) ? clave : regla + "/" + clave;
    }

    private static ReglaParametroId id(String clave) {
        return new ReglaParametroId(PIEZA, clave);
    }

    /** La vista de la pieza principal en el snapshot vigente. */
    private ParametrosVigentes vista() {
        return service.vigentes().de(PIEZA);
    }

    /** Fábrica del umbral de riego: 42 hasta la conmutación del bloque 10, 45 después. */
    private static final String F_UMBRAL = ParametrosRiego.UMBRAL_HUMEDAD.fabrica();
    private static final double F_UMBRAL_NUM = Double.parseDouble(F_UMBRAL);

    private CatalogoParametros catalogo;
    private CatalogoParametrosService service;

    private static Rule regla(String nombre, int prioridad, RuleBranch rama, DefinicionParametro... params) {
        return new Rule() {
            @Override public int priority() { return prioridad; }
            @Override public String name() { return nombre; }
            @Override public String label() { return "Etiqueta " + nombre; }
            @Override public RuleBranch branch() { return rama; }
            @Override public List<DefinicionParametro> parametros() { return List.of(params); }
            @Override public List<RuleAction> evaluate(RuleContext ctx, Evaluacion ev) { return List.of(); }
        };
    }

    @BeforeEach
    void setUp() {
        List<Rule> reglas = List.of(regla(PIEZA, 10, RuleBranch.RIEGO,
                        ParametrosRiego.UMBRAL_HUMEDAD, ParametrosRiego.LITROS_POR_PUNTO, ParametrosRiego.UMBRAL_CRITICO,
                        ParametrosRiego.HUMEDAD_OBJETIVO, ParametrosRiego.VENTANA_NORMAL,
                        ParametrosDiagnostico.CONFIANZA_MINIMA, ParametrosSeguridad.ANTIGUEDAD_MAX_LECTURA),
                regla(OTRA, 5, RuleBranch.RIEGO, ParametrosRiego.UMBRAL_HUMEDAD),
                regla("ManualLockRule", 1, RuleBranch.GLOBAL));
        List<ConsumidorParametros> consumidores = new ArrayList<>(reglas);
        consumidores.add(consumidor(com.yerbanalytics.backend.sensing.PulsoSensadoService.NAME, Map.of(),
                SensingParameters.RESPONSE_WAIT, SensingParameters.RETRIES, SensingParameters.RETRY_WAIT));
        // Un ejecutor que revalida con la copia de la otra regla (D-2).
        consumidores.add(consumidor("Despacho", Map.of(ParametrosRiego.UMBRAL_HUMEDAD, OTRA),
                ParametrosRiego.SECTORES_SIMULTANEOS));
        catalogo = new CatalogoParametros(
                CatalogoParametros.definicionesReales(),
                CatalogoParametros.restriccionesReales(),
                consumidores);
        RuleStateRepository estados = org.mockito.Mockito.mock(RuleStateRepository.class);
        ReglasCatalogo piezas = new ReglasCatalogo(new DemoControllableRegistry(new ArrayList<>(reglas)),
                new OperationalRuleStates(estados, List.of()));

        lenient().when(repository.findAll()).thenAnswer(i -> new ArrayList<>(tabla.values()));
        lenient().when(repository.save(any(ReglaParametroEntity.class))).thenAnswer(i -> {
            ReglaParametroEntity e = i.getArgument(0);
            tabla.put(k(e.getConsumidor(), e.getClave()), e);
            return e;
        });
        lenient().doAnswer(i -> {
            ReglaParametroId id = i.getArgument(0);
            return tabla.remove(k(id.getConsumidor(), id.getClave()));
        }).when(repository).deleteById(any(ReglaParametroId.class));

        service = new CatalogoParametrosService(catalogo, repository, historialService, transactionManager,
                operativaRepository, piezas);
    }

    private static ConsumidorParametros consumidor(String nombre, Map<DefinicionParametro, String> de,
                                                   DefinicionParametro... params) {
        return new ConsumidorParametros() {
            @Override public String name() { return nombre; }
            @Override public List<DefinicionParametro> parametros() { return List.of(params); }
            @Override public Map<DefinicionParametro, String> parametrosDe() { return de; }
        };
    }

    /** Override de la pieza principal, salvo los plazos de sensado, que son del pulso. */
    private void override(String clave, String valor) {
        override(clave.startsWith("sensado.") ? com.yerbanalytics.backend.sensing.PulsoSensadoService.NAME : PIEZA,
                clave, valor);
    }

    private void override(String regla, String clave, String valor) {
        tabla.put(k(regla, clave), new ReglaParametroEntity(regla, clave, valor, "Ana", 1L));
    }

    private static CambioParametro cambio(String clave, String valor) {
        return new CambioParametro(PIEZA, clave, valor);
    }

    private static CambioParametro cambio(String regla, String clave, String valor) {
        return new CambioParametro(regla, clave, valor);
    }

    // ------------------------------------------------------------------ 2.2

    @Test
    void vigentes_sinOverridesDevuelveFabrica() {
        assertThat(vista().numero("riego.umbral-humedad")).isEqualTo(F_UMBRAL_NUM);
        assertThat(vista().ventana("riego.ventana-normal").canonico()).isEqualTo("06:00-18:00");
    }

    @Test
    void vigentes_conOverrideDevuelveElOverrideYElRestoFabrica() {
        override("riego.umbral-humedad", "50");

        assertThat(vista().numero("riego.umbral-humedad")).isEqualTo(50.0);
        assertThat(vista().numero("riego.humedad-objetivo")).isEqualTo(65.0);
    }

    @Test
    void vigentes_devuelveElMismoObjetoEnDosLlamadasSeguidas_yLeeLaBaseUnaSolaVez() {
        ParametrosVigentes a = vista();
        ParametrosVigentes b = vista();

        assertThat(b).isSameAs(a);
        verify(repository, org.mockito.Mockito.times(1)).findAll();
    }

    @Test
    void vigentes_ignoraOverridesHuerfanosOCorruptosEnVezDeRomperElMotor() {
        override("riego.clave-que-ya-no-existe", "1");
        override("riego.umbral-humedad", "no-es-un-numero");
        override("riego.humedad-objetivo", "70");

        ParametrosVigentes v = vista();

        assertThat(v.numero("riego.umbral-humedad")).isEqualTo(F_UMBRAL_NUM);
        assertThat(v.numero("riego.humedad-objetivo")).isEqualTo(70.0);
    }

    // ------------------------------------------------------------------ 2.3

    @Test
    void guardar_aplicaElCambioAudita_yDevuelveElCatalogoActualizado() {
        CatalogoReglasDto out = service.guardar(List.of(cambio("riego.umbral-humedad", "50")), "Ana");

        ReglaParametroEntity e = tabla.get("riego.umbral-humedad");
        assertThat(e.getValor()).isEqualTo("50");
        assertThat(e.getUpdatedBy()).isEqualTo("Ana");
        assertThat(e.getUpdatedTs()).isNotNull().isPositive();
        ParametroDto p = copia(out, PIEZA, "riego.umbral-humedad");
        assertThat(p.valor()).isEqualTo("50");
        assertThat(p.modificado()).isTrue();
        assertThat(p.updatedBy()).isEqualTo("Ana");
    }

    @Test
    void guardar_laConfianzaMinimaRechazaMasDe95PorCiento() {
        assertThatThrownBy(() -> service.guardar(List.of(cambio("diagnostico.confianza-minima", "97")), "Ana"))
                .isInstanceOfSatisfying(ParametrosInvalidosException.class, ex ->
                        assertThat(ex.getErrores()).extracting(ErrorParametro::clave)
                                .containsExactly("diagnostico.confianza-minima"));

        assertThat(tabla).isEmpty();
    }

    @Test
    void vigentes_unaConfianzaMinimaEditadaPorElAgronomoNoSePisaConLaFabricaNueva() {
        override("diagnostico.confianza-minima", "80");

        assertThat(vista().numero("diagnostico.confianza-minima")).isEqualTo(80.0);
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
        verify(repository, never()).deleteById(any(ReglaParametroId.class));
        verifyNoInteractions(historialService);
    }

    @Test
    void guardar_restriccionCruzadaSeEvaluaSobreElConjuntoResultante() {
        // Subir el objetivo junto con el umbral es válido aunque el umbral solo superaría al objetivo vigente (65).
        service.guardar(List.of(cambio("riego.umbral-humedad", "60"), cambio("riego.humedad-objetivo", "75")), "Ana");
        assertThat(vista().numero("riego.umbral-humedad")).isEqualTo(60.0);

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
        assertThat(vista().numero("riego.umbral-humedad")).isEqualTo(50.0);

        service.guardar(List.of(cambio("riego.umbral-humedad", null)), "Ana");

        assertThat(tabla).doesNotContainKey("riego.umbral-humedad");
        verify(repository).deleteById(id("riego.umbral-humedad"));
        assertThat(vista().numero("riego.umbral-humedad")).isEqualTo(F_UMBRAL_NUM);
    }

    @Test
    void guardar_unValorIgualAFabricaEquivaleARestablecer() {
        override("riego.umbral-humedad", "50");

        service.guardar(List.of(cambio("riego.umbral-humedad", F_UMBRAL)), "Ana");

        assertThat(tabla).doesNotContainKey("riego.umbral-humedad");
    }

    @Test
    void guardar_invalidaElCache() {
        ParametrosVigentes antes = vista();

        service.guardar(List.of(cambio("riego.umbral-humedad", "50")), "Ana");
        ParametrosVigentes despues = vista();

        assertThat(antes.numero("riego.umbral-humedad")).isEqualTo(F_UMBRAL_NUM);
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
        verify(repository, never()).deleteById(any(ReglaParametroId.class));
        verifyNoInteractions(historialService);
    }

    @Test
    void guardar_loteVacioONuloDevuelveElCatalogoSinTocarNada() {
        int total = catalogo.consumidores().stream().mapToInt(c -> c.parametros().size()).sum();
        assertThat(service.guardar(List.of(), "Ana").parametros()).hasSize(total);
        assertThat(service.guardar(null, "Ana").parametros()).hasSize(total);
        verifyNoInteractions(historialService);
    }

    // ------------------------------------------------------------------ catálogo (DTO)

    @Test
    void catalogo_listaUnaEntradaPorCopia_yLasReglasPorPrioridad() {
        override("riego.umbral-humedad", "50");

        CatalogoReglasDto dto = service.catalogo();

        assertThat(dto.parametros()).extracting(p -> p.regla() + "/" + p.clave()).doesNotHaveDuplicates()
                .hasSize(catalogo.consumidores().stream().mapToInt(c -> c.parametros().size()).sum());
        ParametroDto umbral = copia(dto, PIEZA, "riego.umbral-humedad");
        assertThat(umbral.fabrica()).isEqualTo(F_UMBRAL);
        assertThat(umbral.valor()).isEqualTo("50");
        assertThat(umbral.familia()).isEqualTo("RIEGO");
        assertThat(umbral.tipo()).isEqualTo("NUMERO");
        assertThat(umbral.min()).isEqualTo(35.0);
        assertThat(umbral.max()).isEqualTo(60.0);
        assertThat(umbral.modificado()).isTrue();

        // La copia de la otra regla sigue en fábrica: no hay herencia.
        ParametroDto otra = copia(dto, OTRA, "riego.umbral-humedad");
        assertThat(otra.valor()).isEqualTo(F_UMBRAL);
        assertThat(otra.modificado()).isFalse();
        assertThat(otra.updatedBy()).isNull();

        // Un parámetro que nadie declara no tiene copias; lo que se lee de otra pieza (D-2) tampoco es copia propia.
        assertThat(dto.parametros()).noneMatch(p -> p.clave().equals("riego.lluvia-mm"));
        assertThat(dto.parametros()).filteredOn(p -> p.regla().equals("Despacho")).extracting(ParametroDto::clave)
                .containsExactly("riego.sectores-simultaneos");

        assertThat(dto.reglas()).extracting("id").containsExactly("ManualLockRule", OTRA, PIEZA);
        assertThat(dto.reglas().get(1).parametros()).containsExactly("riego.umbral-humedad");
        assertThat(dto.reglas().get(0).parametros()).isEmpty();
        assertThat(dto.reglas().get(2).rama()).isEqualTo("RIEGO");
        assertThat(dto.reglas().get(2).prioridad()).isEqualTo(10);
    }

    private static ParametroDto copia(CatalogoReglasDto dto, String regla, String clave) {
        return dto.parametros().stream().filter(p -> p.regla().equals(regla) && p.clave().equals(clave))
                .findFirst().orElseThrow();
    }

    // ------------------------------------------------------------------ copias por pieza (rediseno-motor-reglas)

    @Test
    void guardar_editarLaCopiaDeUnaReglaNoCambiaLaDeOtra() {
        service.guardar(List.of(cambio(PIEZA, "riego.umbral-humedad", "50")), "Ana");

        assertThat(service.vigentes().de(PIEZA).numero("riego.umbral-humedad")).isEqualTo(50.0);
        assertThat(service.vigentes().de(OTRA).numero("riego.umbral-humedad")).isEqualTo(F_UMBRAL_NUM);
        assertThat(tabla).containsOnlyKeys("riego.umbral-humedad");
        assertThat(tabla.get("riego.umbral-humedad").getConsumidor()).isEqualTo(PIEZA);
    }

    @Test
    void guardar_lasDosCopiasDeUnaClaveSeEditanEnElMismoLote() {
        service.guardar(List.of(cambio(PIEZA, "riego.umbral-humedad", "50"),
                cambio(OTRA, "riego.umbral-humedad", "40")), "Ana");

        assertThat(service.vigentes().de(PIEZA).numero("riego.umbral-humedad")).isEqualTo(50.0);
        assertThat(service.vigentes().de(OTRA).numero("riego.umbral-humedad")).isEqualTo(40.0);
        ArgumentCaptor<String> detalle = ArgumentCaptor.forClass(String.class);
        verify(historialService).registrarConfiguracion(org.mockito.ArgumentMatchers.eq("Ana"), detalle.capture());
        assertThat(detalle.getValue()).contains("riego.umbral-humedad (" + PIEZA + ")")
                .contains("riego.umbral-humedad (" + OTRA + ")");
    }

    @Test
    void guardar_unaClaveQueLaPiezaNoDeclaraSeRechazaYNoGuardaNadaDelLote() {
        assertThatThrownBy(() -> service.guardar(List.of(cambio(PIEZA, "riego.umbral-humedad", "50"),
                cambio(OTRA, "riego.litros-por-punto", "0.3")), "Ana"))
                .isInstanceOfSatisfying(ParametrosInvalidosException.class, ex ->
                        assertThat(ex.getErrores()).singleElement().satisfies(e -> {
                            assertThat(e.regla()).isEqualTo(OTRA);
                            assertThat(e.clave()).isEqualTo("riego.litros-por-punto");
                        }));
        assertThat(tabla).isEmpty();
        verifyNoInteractions(historialService);
    }

    @Test
    void guardar_unaPiezaInexistenteOSinReglaSeRechaza() {
        assertThatThrownBy(() -> service.guardar(List.of(cambio("NoExiste", "riego.umbral-humedad", "50"),
                cambio(null, "riego.umbral-humedad", "50")), "Ana"))
                .isInstanceOfSatisfying(ParametrosInvalidosException.class, ex ->
                        assertThat(ex.getErrores()).extracting(ErrorParametro::regla).containsExactly("NoExiste", null));
        assertThat(tabla).isEmpty();
    }

    @Test
    void guardar_unaClaveQueElEjecutorLeeDeOtraPiezaNoSeEditaEnElEjecutor() {
        assertThatThrownBy(() -> service.guardar(List.of(cambio("Despacho", "riego.umbral-humedad", "50")), "Ana"))
                .isInstanceOf(ParametrosInvalidosException.class);
    }

    @Test
    void vigentes_elEjecutorLeeLaCopiaDeLaPiezaDuena() {
        override(OTRA, "riego.umbral-humedad", "40");
        override(PIEZA, "riego.umbral-humedad", "55");

        assertThat(service.vigentes().de("Despacho").numero("riego.umbral-humedad")).isEqualTo(40.0);
    }

    @Test
    void vigentes_unOverrideDeUnaPiezaQueNoDeclaraLaClaveSeIgnora() {
        override(OTRA, "riego.litros-por-punto", "0.3");

        assertThat(service.vigentes().de(OTRA).numero("riego.litros-por-punto"))
                .isEqualTo(Double.parseDouble(ParametrosRiego.LITROS_POR_PUNTO.fabrica()));
    }

    @Test
    void vigentes_unaPiezaQueElCatalogoNoConoceVeFabrica() {
        override("riego.umbral-humedad", "50");

        assertThat(service.vigentes().de("Desconocida").numero("riego.umbral-humedad")).isEqualTo(F_UMBRAL_NUM);
    }

    @Test
    void vigentes_laHuellaCambiaConElValorDeCualquierCopia() {
        String antes = service.vigentes().huella();
        override(OTRA, "riego.umbral-humedad", "40");
        service.invalidar();

        assertThat(service.vigentes().huella()).isNotEqualTo(antes);
    }

    // ------------------------------------------------------------------ revisión del bloque 1

    @Test
    void cache_unaLecturaQueEmpezoAntesDeUnaInvalidacionNoPublicaSuEstadoViejo() {
        override("riego.umbral-humedad", "50");
        AtomicInteger llamadas = new AtomicInteger();
        // Primera carga: toma la foto vieja y, antes de devolverla, "otro hilo" guarda y invalida.
        org.mockito.Mockito.when(repository.findAll()).thenAnswer(i -> {
            List<ReglaParametroEntity> foto = new ArrayList<>(tabla.values());
            if (llamadas.getAndIncrement() == 0) {
                override("riego.umbral-humedad", "55");
                service.invalidar();
            }
            return foto;
        });

        ParametrosVigentes durante = vista();
        ParametrosVigentes despues = vista();

        assertThat(durante.numero("riego.umbral-humedad")).isEqualTo(50.0);
        assertThat(despues.numero("riego.umbral-humedad")).isEqualTo(55.0);
    }

    @Test
    void guardar_alCerrarLaTransaccionInvalidaLoQueOtroHiloRepoblo() {
        ParametrosPorPieza previo;
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
    void vigentes_dentroDeUnaTransaccionAjenaConCacheFrioCargaUnaSolaVez() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            for (int i = 0; i < 5; i++) {
                service.vigentes();
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(repository, org.mockito.Mockito.times(1)).findAll();
    }

    @Test
    void guardar_noPublicaEnElCacheLoQueLeyoDentroDeSuTransaccion() {
        service.guardar(List.of(cambio("riego.umbral-humedad", "50")), "Ana");
        // guardar cargó una vez para decidir y otra al armar su respuesta; ninguna se publicó
        // (podía incluir datos sin commit): la próxima lectura va a la base.
        verify(repository, org.mockito.Mockito.times(2)).findAll();

        service.vigentes();

        verify(repository, org.mockito.Mockito.times(3)).findAll();
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
        verify(repository).deleteById(id("riego.umbral-humedad"));
    }

    @Test
    void guardar_unOverrideHuerfanoDeUnaClaveInexistenteSigueIgnorandose() {
        override("riego.clave-que-ya-no-existe", "1");

        service.guardar(List.of(cambio("riego.umbral-humedad", "50")), "Ana");

        assertThat(tabla).containsKeys("riego.clave-que-ya-no-existe", "riego.umbral-humedad");
        verify(repository, never()).deleteById(id("riego.clave-que-ya-no-existe"));
    }

    @Test
    void guardar_dosGuardadosConcurrentesSeSerializanYElSegundoValidaContraElResultadoDelPrimero() throws Exception {
        CountDownLatch primeroAdentro = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        AtomicInteger lecturas = new AtomicInteger();
        org.mockito.Mockito.when(repository.findAll()).thenAnswer(i -> {
            List<ReglaParametroEntity> foto = new ArrayList<>(tabla.values());
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

    // ------------------------------------------------------------------ pulso de sensado: umbral efectivo de lectura vieja

    private static final String STALE_KEY = ParametrosSeguridad.ANTIGUEDAD_MAX_LECTURA.clave();

    /** Intervalo del pulso guardado en la fila de configuración operativa. */
    private void pullInterval(Integer minutos) {
        ConfiguracionOperativaEntity op = new ConfiguracionOperativaEntity();
        op.setId(1);
        op.setIntervaloSensadoMinutos(minutos);
        lenient().when(operativaRepository.findById(1)).thenReturn(Optional.of(op));
    }

    @Test
    void vigentes_superponeElIntervaloDelPulsoMasLaVentana() {
        pullInterval(120);

        assertThat(vista().numero(STALE_KEY)).isEqualTo(7380.0);
    }

    @Test
    void vigentes_laVentanaDeRespuestaSaleDeLosParametrosDeSensado() {
        pullInterval(240);
        override("sensado.espera-respuesta", "120");
        override("sensado.reintentos", "3");
        override("sensado.espera-reintento", "2");

        // 240 min + 120 s + 3 reintentos × 2 min (120 s cada uno)
        assertThat(vista().numero(STALE_KEY)).isEqualTo(240 * 60 + 120 + 3 * 120);
    }

    @Test
    void vigentes_intervaloNuloSeLeeComo240() {
        pullInterval(null);

        assertThat(vista().numero(STALE_KEY)).isEqualTo(14580.0);
    }

    @Test
    void vigentes_sinFilaDeConfiguracionSeLeeComo240() {
        assertThat(vista().numero(STALE_KEY)).isEqualTo(14580.0);
    }

    @Test
    void vigentes_unPisoGuardadoMenorQueElPulsoNoGana() {
        pullInterval(60);
        override(STALE_KEY, "200");

        assertThat(vista().numero(STALE_KEY)).isEqualTo(3780.0);
    }

    @Test
    void vigentes_intervaloGuardadoFueraDeRangoSeAcota() {
        pullInterval(99999);

        assertThat(vista().numero(STALE_KEY)).isEqualTo(21780.0);
    }

    @Test
    void catalogo_keepsTheRawFloorForTheEditor() {
        pullInterval(120);

        ParametroDto dto = service.catalogo().parametros().stream()
                .filter(p -> p.clave().equals(STALE_KEY) && p.regla().equals(PIEZA)).findFirst().orElseThrow();

        assertThat(dto.valor()).isEqualTo("90");
        assertThat(dto.modificado()).isFalse();
    }

    @Test
    void invalidarAlTerminar_makesTheNextReadSeeTheNewInterval() {
        pullInterval(60);
        assertThat(vista().numero(STALE_KEY)).isEqualTo(3780.0);

        pullInterval(120);
        assertThat(vista().numero(STALE_KEY)).as("cached until invalidated").isEqualTo(3780.0);

        service.invalidarAlTerminar();

        assertThat(vista().numero(STALE_KEY)).isEqualTo(7380.0);
    }

    @Test
    void invalidarAlTerminar_invalidatesAgainAfterCompletion() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            pullInterval(60);
            service.invalidarAlTerminar();
            service.vigentes();   // repopulates the cache between the save and the commit
            pullInterval(120);

            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
            }

            assertThat(vista().numero(STALE_KEY)).isEqualTo(7380.0);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
