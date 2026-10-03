package com.yerbanalytics.backend.engine;

import java.time.Instant;

/**
 * Lo que las reglas de riego necesitan saber del pasado reciente del sector, armado UNA vez por
 * zona con una consulta agrupada (no una consulta por sector y por regla).
 *
 * @param inicioCiclo           inicio del ciclo de lectura en curso
 * @param ultimoRiegoMs         epoch ms del último riego despachado al sector (cualquier regla)
 * @param ultimoRiegoCriticoMs  epoch ms del último riego del sector ordenado por R-02
 * @param ultimaAplicacionMs    epoch ms de la última aplicación de insumo (fertilizante o fitosanitario)
 * @param riegoEnCursoHastaMs   epoch ms en que termina el riego en curso del sector; {@code null} si no hay
 * @param humSusTs              epoch ms de la última humedad de sustrato recibida de la zona
 */
public record ContextoRiego(
        Instant inicioCiclo,
        Long ultimoRiegoMs,
        Long ultimoRiegoCriticoMs,
        Long ultimaAplicacionMs,
        Long riegoEnCursoHastaMs,
        Long humSusTs
) {

    private static final ContextoRiego VACIO = new ContextoRiego(null, null, null, null, null, null);

    /** Sin historia: el barrido (que no riega) y los tests que no la necesitan. */
    public static ContextoRiego vacio() {
        return VACIO;
    }
}
