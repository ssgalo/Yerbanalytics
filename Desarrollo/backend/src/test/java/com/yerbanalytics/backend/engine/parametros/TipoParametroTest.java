package com.yerbanalytics.backend.engine.parametros;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tarea 1.2: parseo y validación de un texto contra su definición. */
@DisplayName("TipoParametro.parsear")
class TipoParametroTest {

    private static final DefinicionParametro UMBRAL = DefinicionDePrueba.numero("riego.umbral", "45", 35.0, 60.0, 0);
    private static final DefinicionParametro LITROS = DefinicionDePrueba.numero("riego.litros", "0.2", 0.1, 0.5, 2);
    private static final DefinicionParametro SECTORES = DefinicionDePrueba.entero("riego.sectores", "10", 1.0, 100.0);

    @Test
    void numero_aceptaValorEnRangoYDevuelveNumero() {
        assertThat(TipoParametro.NUMERO.parsear("40", UMBRAL)).isEqualTo(new ValorParametro.Numero(40));
        assertThat(TipoParametro.NUMERO.parsear(" 40.0 ", UMBRAL)).isEqualTo(new ValorParametro.Numero(40));
    }

    @Test
    void numero_conDosDecimalesEsValidoYConTresNo() {
        assertThat(TipoParametro.NUMERO.parsear("0.2", LITROS)).isEqualTo(new ValorParametro.Numero(0.2));
        assertThat(TipoParametro.NUMERO.parsear("0.25", LITROS)).isEqualTo(new ValorParametro.Numero(0.25));
        assertThatThrownBy(() -> TipoParametro.NUMERO.parsear("0.255", LITROS))
                .isInstanceOf(ValorParametroInvalidoException.class)
                .hasMessageContaining("2 decimales");
    }

    @Test
    void numero_fueraDeRangoIndicaElRango() {
        assertThatThrownBy(() -> TipoParametro.NUMERO.parsear("70", UMBRAL))
                .isInstanceOf(ValorParametroInvalidoException.class)
                .hasMessageContaining("35").hasMessageContaining("60");
        assertThatThrownBy(() -> TipoParametro.NUMERO.parsear("34.9", LITROS))
                .isInstanceOf(ValorParametroInvalidoException.class);
    }

    @Test
    void numero_noNumericoOVacio() {
        for (String malo : new String[]{"abc", "", "  ", "NaN", "Infinity", "1e400x"}) {
            assertThatThrownBy(() -> TipoParametro.NUMERO.parsear(malo, UMBRAL))
                    .as("'%s'", malo)
                    .isInstanceOf(ValorParametroInvalidoException.class);
        }
        assertThatThrownBy(() -> TipoParametro.NUMERO.parsear(null, UMBRAL))
                .isInstanceOf(ValorParametroInvalidoException.class);
    }

    @Test
    void entero_conDecimalesSeRechaza() {
        assertThatThrownBy(() -> TipoParametro.ENTERO.parsear("10.5", SECTORES))
                .isInstanceOf(ValorParametroInvalidoException.class)
                .hasMessageContaining("entero");
        assertThat(TipoParametro.ENTERO.parsear("10", SECTORES)).isEqualTo(new ValorParametro.Numero(10));
        assertThat(TipoParametro.ENTERO.parsear("10.0", SECTORES)).isEqualTo(new ValorParametro.Numero(10));
        assertThatThrownBy(() -> TipoParametro.ENTERO.parsear("101", SECTORES))
                .isInstanceOf(ValorParametroInvalidoException.class);
    }

    @Test
    void hora_yVentana() {
        DefinicionParametro hora = DefinicionDePrueba.hora("x.hora", "06:00");
        DefinicionParametro ventana = DefinicionDePrueba.ventana("x.ventana", "06:00-18:00");

        assertThat(TipoParametro.HORA.parsear("07:15", hora))
                .isEqualTo(new ValorParametro.Hora(LocalTime.of(7, 15)));
        assertThat(TipoParametro.VENTANA_HORARIA.parsear("18:00-06:00", ventana))
                .isEqualTo(new VentanaHoraria(LocalTime.of(18, 0), LocalTime.of(6, 0)));

        assertThatThrownBy(() -> TipoParametro.VENTANA_HORARIA.parsear("25:00-18:00", ventana))
                .isInstanceOf(ValorParametroInvalidoException.class)
                .hasMessageContaining("HH:mm-HH:mm");
        assertThatThrownBy(() -> TipoParametro.HORA.parsear("7", hora))
                .isInstanceOf(ValorParametroInvalidoException.class)
                .hasMessageContaining("HH:mm");
    }
}
