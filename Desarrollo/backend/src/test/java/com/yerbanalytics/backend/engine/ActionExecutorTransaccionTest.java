package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.engine.riego.ColaRiego;
import com.yerbanalytics.backend.engine.traza.OrigenEvaluacion;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.service.HistorialService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El estado en memoria del executor ("inacción ya registrada", "alerta del ciclo ya enviada") se marca recién cuando la
 * transacción de la telemetría o del barrido CONFIRMA. Si se marcara antes y la transacción revirtiera, la memoria diría
 * "registrado" para filas que no existen y no se volverían a escribir.
 *
 * <p>La transacción se simula con la sincronización de Spring: {@code initSynchronization} es lo que activa
 * {@code @Transactional}, y el test dispara {@code afterCommit}/{@code afterCompletion} como lo haría el gestor.
 */
@DisplayName("ActionExecutor - estado en memoria tras el commit")
class ActionExecutorTransaccionTest {

    private static final Instant AHORA = RiegoCtx.instante("10:05");

    private HistorialService historial;
    private ActionExecutor executor;

    @BeforeEach
    void setUp() {
        historial = mock(HistorialService.class);
        ComandoActuadorPublisher publisher = mock(ComandoActuadorPublisher.class);
        when(publisher.publicar(any(), any(), any(), any(), any()))
                .thenReturn(new ComandoActuadorPublisher.Resultado(true, "c", null));
        executor = new ActionExecutor(historial, publisher, new ColaRiego());
    }

    @AfterEach
    void limpiar() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static RuleContext ctx(String sectorId) {
        ZonaEntity zona = RuleContextTestFactory.zonaBasica("MZ-2");
        zona.setName("Macro-zona 2");
        SectorEntity sector = RuleContextTestFactory.sectorBasico(sectorId);
        sector.setZona(zona);
        return new RuleContext(sector, zona, List.of(), List.of(), RuleContextTestFactory.defaultConfig(),
                "ok", AHORA, null, false);
    }

    private static List<RuleAction> decision() {
        return List.of(RuleAction.noopInfo("RiegoPorDeficitRule", "Humedad de sustrato 40% sin déficit."),
                RuleAction.of(ActionType.ALERTA, "SustratoSaturadoRule", "motivo",
                        new DetalleAlerta(NivelAlerta.WARNING, "Sustrato saturado")));
    }

    private void inaccionesEscritas(int n) {
        verify(historial, times(n)).registrarInaccion(any(), any(), anyString(), anyString());
    }

    private void alertasEscritas(int n) {
        verify(historial, times(n)).registrarAlerta(anyString(), any(), anyString(), any(), anyLong());
    }

    /** Termina la transacción simulada: confirma o revierte. */
    private void terminar(boolean confirma) {
        List<TransactionSynchronization> sincronizaciones = new ArrayList<>(TransactionSynchronizationManager.getSynchronizations());
        TransactionSynchronizationManager.clearSynchronization();
        for (TransactionSynchronization s : sincronizaciones) {
            if (confirma) {
                s.afterCommit();
            }
            s.afterCompletion(confirma ? TransactionSynchronization.STATUS_COMMITTED : TransactionSynchronization.STATUS_ROLLED_BACK);
        }
    }

    @Test
    @DisplayName("la transacción revierte: la inacción y la alerta NO quedan marcadas y la siguiente evaluación las vuelve a escribir")
    void rollback_noMarcaNada() {
        TransactionSynchronizationManager.initSynchronization();
        executor.execute(decision(), ctx("MZ-2-001"), OrigenEvaluacion.TELEMETRIA);
        terminar(false);                                       // rollback

        // Sin transacción (inmediato): si las marcas hubieran quedado, no se escribiría nada.
        executor.execute(decision(), ctx("MZ-2-001"), OrigenEvaluacion.TELEMETRIA);

        inaccionesEscritas(2);                                 // 1 de la evaluación revertida + 1 de la nueva
        alertasEscritas(2);
    }

    @Test
    @DisplayName("la transacción confirma: recién ahí quedan marcadas y la siguiente evaluación idéntica no escribe")
    void commit_marca() {
        TransactionSynchronizationManager.initSynchronization();
        executor.execute(decision(), ctx("MZ-2-001"), OrigenEvaluacion.TELEMETRIA);
        terminar(true);                                        // commit

        executor.execute(decision(), ctx("MZ-2-001"), OrigenEvaluacion.TELEMETRIA);

        inaccionesEscritas(1);
        alertasEscritas(1);
    }

    @Test
    @DisplayName("dentro de la MISMA transacción la alerta de 100 sectores de la zona se escribe una sola vez (aunque aún no haya commit)")
    void dentroDeLaTransaccion_laAlertaSeDeduplica() {
        TransactionSynchronizationManager.initSynchronization();
        for (int n = 1; n <= 100; n++) {
            executor.execute(decision(), ctx(String.format("MZ-2-%03d", n)), OrigenEvaluacion.TELEMETRIA);
        }
        alertasEscritas(1);
        terminar(true);

        executor.execute(decision(), ctx("MZ-2-050"), OrigenEvaluacion.TELEMETRIA);
        alertasEscritas(1);                                    // ya confirmada: no se repite en el ciclo
    }

    @Test
    @DisplayName("un rollback tras 100 sectores libera todo: la transacción siguiente vuelve a escribir la alerta y las filas")
    void rollbackDeLaZonaEntera() {
        TransactionSynchronizationManager.initSynchronization();
        for (int n = 1; n <= 100; n++) {
            executor.execute(decision(), ctx(String.format("MZ-2-%03d", n)), OrigenEvaluacion.TELEMETRIA);
        }
        terminar(false);

        TransactionSynchronizationManager.initSynchronization();
        executor.execute(decision(), ctx("MZ-2-001"), OrigenEvaluacion.TELEMETRIA);
        terminar(true);

        alertasEscritas(2);
        inaccionesEscritas(100 + 1);                           // 100 sectores con 1 inacción c/u + el sector reintentado
    }

    @Test
    @DisplayName("si falla la escritura de la alerta dentro de la transacción, no queda marcada")
    void fallaLaAlerta_noQuedaMarcada() {
        org.mockito.Mockito.doThrow(new IllegalStateException("base caída")).doNothing().when(historial)
                .registrarAlerta(anyString(), any(), anyString(), any(), anyLong());
        TransactionSynchronizationManager.initSynchronization();
        executor.execute(decision(), ctx("MZ-2-001"), OrigenEvaluacion.TELEMETRIA);   // la primera escritura falla
        executor.execute(decision(), ctx("MZ-2-002"), OrigenEvaluacion.TELEMETRIA);   // se reintenta con el sector siguiente
        terminar(true);

        alertasEscritas(2);
    }
}
