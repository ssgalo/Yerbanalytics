package com.yerbanalytics.backend.mqtt;

import com.yerbanalytics.backend.engine.riego.CalculoRiego;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * La duración máxima de la válvula es del CONTRATO MQTT (D13): el backend nunca pide más y el
 * firmware no la recorta. Está espejada en {@code contrato.h}; acá se fija y se compara con el espejo.
 */
@DisplayName("ContratoNodo - duración máxima de la válvula")
class ContratoNodoValvulaTest {

    @Test
    void laDuracionMaximaEs1200Segundos() {
        assertThat(ContratoNodo.DURACION_VALVULA_MAX_SEG).isEqualTo(1200);
    }

    @Test
    void calculoRiegoUsaElMaximoDelContratoComoTope() {
        // 10 L a 5 L/h serían 7200 s: se recorta al máximo del contrato.
        var plan = CalculoRiego.volumenMaximo(10.0, 5.0);
        assertThat(plan.recortado()).isTrue();
        assertThat(plan.duracionSeg()).isEqualTo(ContratoNodo.DURACION_VALVULA_MAX_SEG);
    }

    @Test
    void elFirmwareEspejaElMismoValor() throws IOException {
        Path contratoH = Path.of("..", "embebido", "comun", "contrato.h");
        assumeTrue(Files.exists(contratoH), "el firmware no está en este checkout");
        Matcher m = Pattern.compile("#define\\s+CONTRATO_VALVULA_DURACION_MAX_SEG\\s+(\\d+)")
                .matcher(Files.readString(contratoH));
        assertThat(m.find()).as("contrato.h define CONTRATO_VALVULA_DURACION_MAX_SEG").isTrue();
        assertThat(Integer.parseInt(m.group(1))).isEqualTo(ContratoNodo.DURACION_VALVULA_MAX_SEG);
    }
}
