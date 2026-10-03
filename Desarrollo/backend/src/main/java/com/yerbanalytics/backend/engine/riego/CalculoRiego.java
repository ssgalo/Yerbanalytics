package com.yerbanalytics.backend.engine.riego;

import com.yerbanalytics.backend.engine.DetalleRiego;
import com.yerbanalytics.backend.mqtt.ContratoNodo;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Cálculo puro del volumen y el tiempo de un riego (design D3 de implement-reglas-riego).
 * Sin estado ni dependencias: se testea con números.
 *
 * <pre>
 * V = min((objetivo − humedad) × litrosPorPunto, volumenMax)      R-01 (déficit)
 * V = volumenMax                                                  R-02 (déficit crítico)
 * V se redondea a 0,01 L (HALF_UP) antes de calcular el tiempo
 * t = ceil(V / caudal × 3600) s; si t &gt; duración máxima del contrato → t = máximo, recortado
 * </pre>
 *
 * <p>Todo en {@link BigDecimal}: 21 × 0,2 en {@code double} es 4,2000000000000002 y daría 505 s en
 * lugar de 504.
 */
public final class CalculoRiego {

    /** Segundos de una hora (el caudal del emisor viene en L/h). */
    private static final BigDecimal SEGUNDOS_POR_HORA = BigDecimal.valueOf(3600);
    private static final BigDecimal DURACION_MAX = BigDecimal.valueOf(ContratoNodo.DURACION_VALVULA_MAX_SEG);

    private CalculoRiego() {
    }

    /**
     * Resultado del cálculo.
     *
     * @param volumenL    litros a aplicar, redondeados a 0,01 L
     * @param duracionSeg segundos de apertura (≤ duración máxima del contrato)
     * @param recortado   {@code true} si la duración calculada superaba el máximo del contrato
     */
    public record PlanRiego(double volumenL, int duracionSeg, boolean recortado) {

        /** El plan como detalle tipado de una acción de riego, con la humedad que lo motivó. */
        public DetalleRiego aDetalle(double humedad) {
            return new DetalleRiego(volumenL, duracionSeg, humedad, recortado);
        }
    }

    /** R-01: {@code V = min((objetivo − humedad) × litrosPorPunto, volumenMax)}; sin déficit, 0 L. */
    public static PlanRiego porDeficit(double humedad, double objetivo, double litrosPorPunto,
                                       double volumenMaxL, double caudalLh) {
        BigDecimal deficit = BigDecimal.valueOf(objetivo).subtract(BigDecimal.valueOf(humedad));
        BigDecimal v = deficit.multiply(BigDecimal.valueOf(litrosPorPunto)).max(BigDecimal.ZERO)
                .min(BigDecimal.valueOf(volumenMaxL));
        return plan(v, caudalLh);
    }

    /** R-02: {@code V = volumenMax}. */
    public static PlanRiego volumenMaximo(double volumenMaxL, double caudalLh) {
        return plan(BigDecimal.valueOf(volumenMaxL), caudalLh);
    }

    /**
     * {@code true} si regar {@code volumenL} litros a {@code caudalLh} L/h entra en la duración máxima
     * del contrato de la válvula ({@link ContratoNodo#DURACION_VALVULA_MAX_SEG}). Exacto en decimal:
     * 10 L a 30 L/h son 1200 s justos y entran.
     */
    public static boolean cabeEnLaValvula(double volumenL, double caudalLh) {
        return BigDecimal.valueOf(volumenL).multiply(SEGUNDOS_POR_HORA)
                .compareTo(BigDecimal.valueOf(caudalLh).multiply(DURACION_MAX)) <= 0;
    }

    private static PlanRiego plan(BigDecimal volumen, double caudalLh) {
        if (!(caudalLh > 0)) {
            throw new IllegalArgumentException("El caudal del emisor debe ser mayor a 0 L/h: " + caudalLh);
        }
        BigDecimal v = volumen.setScale(2, RoundingMode.HALF_UP);
        BigDecimal t = v.multiply(SEGUNDOS_POR_HORA).divide(BigDecimal.valueOf(caudalLh), 0, RoundingMode.CEILING);
        boolean recortado = t.compareTo(DURACION_MAX) > 0;
        return new PlanRiego(v.doubleValue(), recortado ? ContratoNodo.DURACION_VALVULA_MAX_SEG : t.intValueExact(), recortado);
    }
}
