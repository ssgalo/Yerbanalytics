package com.yerbanalytics.backend.engine.overrides;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("OperationalRuleStates")
class OperationalRuleStatesTest {

    private RuleStateRepository repository;
    private ListAppender<ILoggingEvent> logs;
    private final Logger logger = (Logger) LoggerFactory.getLogger(OperationalRuleStates.class);

    @BeforeEach
    void setUp() {
        repository = mock(RuleStateRepository.class);
        when(repository.findAll()).thenReturn(List.of());
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static RuleStateEntity fila(String nombre, boolean activa) {
        return new RuleStateEntity(nombre, activa, "porque sí", "Ana", 1_000L);
    }

    private OperationalRuleStates states(String... retiradas) {
        return new OperationalRuleStates(repository, List.of(retiradas));
    }

    @Test
    @DisplayName("sin fila rige la siembra: las retiradas apagadas, el resto prendido")
    void sinFilaRigeLaSiembra() {
        OperationalRuleStates states = states("SupplyRule", " DailyDoseLimitRule ", "");

        assertThat(states.isActive("SupplyRule")).isFalse();
        assertThat(states.isActive("DailyDoseLimitRule")).isFalse();
        assertThat(states.isActive("RiegoPorDeficitRule")).isTrue();
        assertThat(states.activeByDefault("SupplyRule")).isFalse();
        assertThat(states.activeByDefault("RiegoPorDeficitRule")).isTrue();
        assertThat(states.state("SupplyRule")).isEmpty();
    }

    @Test
    @DisplayName("con fila rige la fila, aunque contradiga la siembra")
    void conFilaRigeLaFila() {
        when(repository.findAll()).thenReturn(List.of(fila("SupplyRule", true), fila("RiegoPorDeficitRule", false)));
        OperationalRuleStates states = states("SupplyRule");

        assertThat(states.isActive("SupplyRule")).isTrue();
        assertThat(states.activeByDefault("SupplyRule")).isFalse();
        assertThat(states.isActive("RiegoPorDeficitRule")).isFalse();
        assertThat(states.state("RiegoPorDeficitRule")).hasValueSatisfying(e -> {
            assertThat(e.updatedBy()).isEqualTo("Ana");
            assertThat(e.reason()).isEqualTo("porque sí");
        });
    }

    @Test
    @DisplayName("una retirada prendida a mano sigue prendida aunque se la quite de la lista")
    void prendidaALaManoSobreviveALaLista() {
        when(repository.findAll()).thenReturn(List.of(fila("SupplyRule", true)));

        assertThat(states(/* ya no figura */).isActive("SupplyRule")).isTrue();
    }

    @Test
    @DisplayName("los nombres apagados juntan filas apagadas y retiradas sin fila")
    void nombresApagados() {
        when(repository.findAll()).thenReturn(List.of(fila("RiegoPorDeficitRule", false), fila("SupplyRule", true)));

        assertThat(states("SupplyRule", "DailyDoseLimitRule").inactiveNames())
                .containsExactlyInAnyOrder("RiegoPorDeficitRule", "DailyDoseLimitRule");
    }

    @Test
    @DisplayName("una fila huérfana se ignora y se loguea; no se borra")
    void filaHuerfana() {
        when(repository.findAll()).thenReturn(List.of(fila("FollowUpRule", false), fila("SupplyRule", true)));
        OperationalRuleStates states = states();

        states.logOrphans(Set.of("SupplyRule", "RiegoPorDeficitRule"));

        assertThat(logs.list).anySatisfy(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.WARN);
            assertThat(e.getFormattedMessage()).contains("FollowUpRule");
        });
        assertThat(states.isActive("SupplyRule")).isTrue();
        verify(repository, never()).delete(any());
        verify(repository, never()).deleteById(any());
    }

    @Test
    @DisplayName("set persiste quién, cuándo y por qué")
    void setPersiste() {
        OperationalRuleStates states = states();

        RuleState guardada = states.set("RiegoPorDeficitRule", false, "sonda en recalibración", "Ana");

        ArgumentCaptor<RuleStateEntity> fila = ArgumentCaptor.forClass(RuleStateEntity.class);
        verify(repository).save(fila.capture());
        assertThat(fila.getValue().getNombre()).isEqualTo("RiegoPorDeficitRule");
        assertThat(fila.getValue().isActiva()).isFalse();
        assertThat(fila.getValue().getMotivo()).isEqualTo("sonda en recalibración");
        assertThat(fila.getValue().getUpdatedBy()).isEqualTo("Ana");
        assertThat(fila.getValue().getUpdatedTs()).isPositive();
        assertThat(guardada.active()).isFalse();
        assertThat(guardada.reason()).isEqualTo("sonda en recalibración");
        assertThat(guardada.updatedBy()).isEqualTo("Ana");
    }

    @Test
    @DisplayName("sin transacción el cache se actualiza en el acto")
    void sinTransaccion() {
        OperationalRuleStates states = states();

        states.set("RiegoPorDeficitRule", false, null, "Ana");

        assertThat(states.isActive("RiegoPorDeficitRule")).isFalse();
    }

    @Test
    @DisplayName("con transacción el cache cambia después del commit")
    void cacheDespuesDelCommit() {
        OperationalRuleStates states = states();
        TransactionSynchronizationManager.initSynchronization();

        states.set("RiegoPorDeficitRule", false, null, "Ana");
        assertThat(states.isActive("RiegoPorDeficitRule")).as("antes del commit").isTrue();

        commit();
        assertThat(states.isActive("RiegoPorDeficitRule")).as("después del commit").isFalse();
    }

    @Test
    @DisplayName("con rollback el cache no cambia")
    void rollbackNoCambiaElCache() {
        OperationalRuleStates states = states();
        TransactionSynchronizationManager.initSynchronization();

        states.set("RiegoPorDeficitRule", false, null, "Ana");
        for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
            s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }

        assertThat(states.isActive("RiegoPorDeficitRule")).isTrue();
    }

    private static void commit() {
        for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
            s.afterCommit();
            s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        }
    }
}
