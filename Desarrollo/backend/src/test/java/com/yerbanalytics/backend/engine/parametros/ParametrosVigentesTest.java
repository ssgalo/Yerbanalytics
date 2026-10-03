package com.yerbanalytics.backend.engine.parametros;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ParametrosVigentes")
class ParametrosVigentesTest {

    @Test
    void laHuellaEsEstableParaLosMismosValoresYCambiaSiCambiaUno() {
        ParametrosVigentes a = ParametrosVigentes.de(Map.of("riego.a", new ValorParametro.Numero(1)));
        ParametrosVigentes igual = ParametrosVigentes.de(Map.of("riego.a", new ValorParametro.Numero(1)));
        ParametrosVigentes otro = ParametrosVigentes.de(Map.of("riego.a", new ValorParametro.Numero(2)));

        assertThat(a.huella()).isNotBlank().isEqualTo(igual.huella()).isNotEqualTo(otro.huella());
        assertThat(a.huella()).isSameAs(a.huella());
    }
}
