package com.yerbanalytics.backend.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Los espejos del ACK intermedio y de la prioridad (add-cola-actuadores-nodo) dicen lo mismo que contrato.h. */
@DisplayName("ContratoNodo - cola de cargas del nodo")
class ContratoNodoColaTest {

    @Test
    void losValoresDelContratoSonLosAcordados() {
        assertThat(ContratoNodo.ACK_EN_CURSO).isEqualTo("EN_CURSO");
        assertThat(ContratoNodo.ACK_TIPO_EN_COLA).isEqualTo("en_cola");
        assertThat(ContratoNodo.ACK_TIPO_INICIADO).isEqualTo("iniciado");
        assertThat(ContratoNodo.ACK_TIPO_COLA_LLENA).isEqualTo("cola_llena");
        assertThat(ContratoNodo.PARAM_PRIORIDAD).isEqualTo("prioridad");
        assertThat(ContratoNodo.PRIORIDAD_INMEDIATA).isEqualTo("INMEDIATA");
    }

    @Test
    void elFirmwareEspejaLosMismosValores() throws IOException {
        Path contratoH = Path.of("..", "embebido", "comun", "contrato.h");
        assumeTrue(Files.exists(contratoH), "el firmware no está en este checkout");
        String h = Files.readString(contratoH);
        for (String declaracion : new String[]{
                "STATUS_EN_CURSO = \"" + ContratoNodo.ACK_EN_CURSO + "\"",
                "DETALLE_EN_COLA              = \"" + ContratoNodo.ACK_TIPO_EN_COLA + "\"",
                "DETALLE_INICIADO             = \"" + ContratoNodo.ACK_TIPO_INICIADO + "\"",
                "DETALLE_COLA_LLENA           = \"" + ContratoNodo.ACK_TIPO_COLA_LLENA + "\"",
                "KEY_PRIORIDAD       = \"" + ContratoNodo.PARAM_PRIORIDAD + "\"",
                "PRIORIDAD_INMEDIATA = \"" + ContratoNodo.PRIORIDAD_INMEDIATA + "\""}) {
            assertThat(h).as("contrato.h declara " + declaracion).contains(declaracion);
        }
    }

    @Test
    void elAckIntermedioSeLeeDelDetalle() throws IOException {
        var detalle = new ObjectMapper().readTree("{\"tipo\":\"en_cola\",\"posicion\":2,\"esperaMaxSec\":45}");
        AckActuador ack = new AckActuador("c", "EN_CURSO", detalle, "MZ-1", "MZ-1-001");

        assertThat(ack.esIntermedio()).isTrue();
        assertThat(ack.esperaMaxSec()).isEqualTo(45L);
        assertThat(new AckActuador("c", "SUCCESS", detalle, "MZ-1", "MZ-1-001").esIntermedio()).isFalse();
        assertThat(new AckActuador("c", "EN_CURSO", null, "MZ-1", "MZ-1-001").esperaMaxSec()).isZero();
    }
}
