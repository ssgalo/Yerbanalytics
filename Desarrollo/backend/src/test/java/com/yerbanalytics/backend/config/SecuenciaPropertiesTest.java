package com.yerbanalytics.backend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/** Los cuatro tiempos de las secuencias (design add-secuencias-demo-expo §2.2) llegan desde application.properties. */
@SpringBootTest
@DisplayName("SecuenciaProperties")
class SecuenciaPropertiesTest {

    @Autowired
    private SecuenciaProperties props;

    @Test
    void losValoresDeApplicationPropertiesSonLosDelDesign() {
        assertThat(props.getTimeoutAckValvulaSeg()).isEqualTo(10);
        assertThat(props.getTimeoutAckMediasombraSeg()).isEqualTo(45);
        assertThat(props.getTimeoutLecturaSeg()).isEqualTo(20);
        assertThat(props.getTickMs()).isEqualTo(1000);
    }

    @Test
    void losDefaultsDeLaClaseCoincidenConLosDeApplicationProperties() {
        SecuenciaProperties porDefecto = new SecuenciaProperties();

        assertThat(porDefecto.getTimeoutAckValvulaSeg()).isEqualTo(10);
        assertThat(porDefecto.getTimeoutAckMediasombraSeg()).isEqualTo(45);
        assertThat(porDefecto.getTimeoutLecturaSeg()).isEqualTo(20);
        assertThat(porDefecto.getTickMs()).isEqualTo(1000);
    }
}
