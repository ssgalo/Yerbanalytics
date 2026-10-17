package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.readings.ReadingCheck;
import com.yerbanalytics.backend.readings.ReadingMetrics;
import com.yerbanalytics.backend.readings.ReadingValidationGuard;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Dobles del guard de lectura inválida (S-03) para los tests de la ingesta. */
final class ReadingGuardsDePrueba {

    private ReadingGuardsDePrueba() {
    }

    /** Acepta toda lectura tal cual llega: la ingesta se comporta como antes de S-03. */
    static ReadingValidationGuard aceptaTodo() {
        ReadingValidationGuard guard = mock(ReadingValidationGuard.class);
        when(guard.check(any(ZonaEntity.class), any(ReadingMetrics.class)))
                .thenAnswer(inv -> ReadingCheck.allAccepted(inv.getArgument(1)));
        return guard;
    }
}
