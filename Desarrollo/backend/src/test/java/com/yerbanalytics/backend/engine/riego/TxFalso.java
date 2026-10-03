package com.yerbanalytics.backend.engine.riego;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.List;

/** Administrador de transacciones de test: anota begin / commit / rollback en una lista compartida. */
final class TxFalso implements PlatformTransactionManager {

    private final List<String> eventos;

    TxFalso(List<String> eventos) {
        this.eventos = eventos;
    }

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) {
        eventos.add("begin");
        return new SimpleTransactionStatus();
    }

    @Override
    public void commit(TransactionStatus status) {
        eventos.add("commit");
    }

    @Override
    public void rollback(TransactionStatus status) {
        eventos.add("rollback");
    }
}
