package com.yerbanalytics.backend.repository;

import com.yerbanalytics.backend.engine.RuleContextTestFactory;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El cierre de la dosis (UPDATE dirigido) no puede pisarlo la telemetría: su transacción carga los sectores y los
 * guarda enteros, y si reescribiera {@code actuador_pump} con la copia vieja volvería a "Dosificando".
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("Sector: estado de la bomba frente a la telemetría concurrente")
class SectorPumpStateTest {

    @Autowired
    private SectorRepository sectores;
    @Autowired
    private ZonaRepository zonas;
    @Autowired
    private EntityManager em;

    @Test
    void unCierreDeDosisConfirmadoEntreLaCargaYElGuardadoNoSePisa() {
        ZonaEntity zona = RuleContextTestFactory.zonaBasica("ZT-PUMP");
        zona.setSub("Prueba");
        zonas.saveAndFlush(zona);
        SectorEntity inicial = RuleContextTestFactory.sectorBasico("ZT-PUMP-001");
        inicial.setN(1);
        inicial.setZona(zona);
        inicial.setActuadorPump("Dosificando");
        sectores.saveAndFlush(inicial);
        em.clear();

        // La transacción de telemetría carga el sector (con "Dosificando")...
        SectorEntity cargado = sectores.findById("ZT-PUMP-001").orElseThrow();
        // ...en el medio se confirma el cierre de la dosis (otra transacción en producción)...
        assertThat(sectores.cambiarBomba("ZT-PUMP-001", "Dosificando", "En espera")).isEqualTo(1);
        // ...y la telemetría guarda lo suyo, sin tocar la bomba.
        cargado.setReason("Humedad baja");
        sectores.saveAndFlush(cargado);
        em.clear();

        SectorEntity leido = sectores.findById("ZT-PUMP-001").orElseThrow();
        assertThat(leido.getReason()).isEqualTo("Humedad baja");
        assertThat(leido.getActuadorPump()).isEqualTo("En espera");
    }
}
