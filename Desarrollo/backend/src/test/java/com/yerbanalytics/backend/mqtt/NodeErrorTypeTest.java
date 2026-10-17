package com.yerbanalytics.backend.mqtt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * S-05: los tipos de error del nodo son del CONTRATO MQTT. Acá se fijan los valores del cable, se separan los
 * hidráulicos del de la malla y se compara con el espejo de {@code contrato.h}.
 */
@DisplayName("NodeErrorType (contrato de errores del nodo)")
class NodeErrorTypeTest {

    @Test
    @DisplayName("los valores del cable son los del contrato")
    void valoresDelCable() {
        assertThat(NodeErrorType.NO_FLOW.wireValue()).isEqualTo("SIN_CAUDAL");
        assertThat(NodeErrorType.EXCESS_FLOW.wireValue()).isEqualTo("CAUDAL_EXCESIVO");
        assertThat(NodeErrorType.SHADE_LIMIT_SWITCH.wireValue()).isEqualTo("MEDIASOMBRA_FIN_DE_CARRERA");
    }

    @Test
    @DisplayName("fromWire reconoce cada tipo y devuelve vacío para uno desconocido, nulo o con otra capitalización")
    void fromWire() {
        for (NodeErrorType t : NodeErrorType.values()) {
            assertThat(NodeErrorType.fromWire(t.wireValue())).contains(t);
        }
        assertThat(NodeErrorType.fromWire("EXPLOSION")).isEmpty();
        assertThat(NodeErrorType.fromWire("sin_caudal")).isEmpty();
        assertThat(NodeErrorType.fromWire("")).isEmpty();
        assertThat(NodeErrorType.fromWire(null)).isEmpty();
    }

    @Test
    @DisplayName("los de caudal son hidráulicos (del sector); el de la malla es de la macro-zona")
    void alcance() {
        assertThat(NodeErrorType.NO_FLOW.isHydraulic()).isTrue();
        assertThat(NodeErrorType.EXCESS_FLOW.isHydraulic()).isTrue();
        assertThat(NodeErrorType.SHADE_LIMIT_SWITCH.isHydraulic()).isFalse();
    }

    @Test
    @DisplayName("el tópico de errores y su suscripción")
    void topicos() {
        assertThat(ContratoNodo.TOPIC_ERRORS_SUBSCRIPTION).isEqualTo("nursery/zone/+/errores");
        assertThat(String.format(ContratoNodo.TOPIC_ERRORS, "MZ-1")).isEqualTo("nursery/zone/MZ-1/errores");
    }

    @Test
    @DisplayName("contrato.h define exactamente los mismos tipos y el mismo tópico")
    void elFirmwareEspejaLosMismosValores() throws IOException {
        Path contratoH = Path.of("..", "embebido", "comun", "contrato.h");
        assumeTrue(Files.exists(contratoH), "el firmware no está en este checkout");
        String contrato = Files.readString(contratoH);

        Arrays.stream(NodeErrorType.values())
                .forEach(t -> assertThat(contrato).contains("\"" + t.wireValue() + "\""));
        assertThat(contrato).contains("nursery/zone/%s/errores");
    }
}
