package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.sensing.PulsoSensadoService;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Dobles del pulso de sensado para los tests de la ingesta. */
final class PulsosDePrueba {

    private PulsosDePrueba() {
    }

    /** Toda telemetría responde a un pulso con motor: la ingesta evalúa las reglas. */
    static PulsoSensadoService conMotor() {
        PulsoSensadoService pulsos = mock(PulsoSensadoService.class);
        when(pulsos.respondePulso(anyString(), anyLong())).thenReturn(true);
        return pulsos;
    }

    /** Ninguna telemetría responde a un pulso con motor: la ingesta sólo actualiza la lectura. */
    static PulsoSensadoService sinMotor() {
        return mock(PulsoSensadoService.class);
    }
}
