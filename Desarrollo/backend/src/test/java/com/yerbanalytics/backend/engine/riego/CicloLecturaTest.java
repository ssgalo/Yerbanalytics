package com.yerbanalytics.backend.engine.riego;

import com.yerbanalytics.backend.config.ZonaHorariaVivero;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Tarea 7.2: el ciclo de lectura son franjas de {@code minutos} ancladas a las 02:00 locales. */
@DisplayName("CicloLectura")
class CicloLecturaTest {

    private static Instant local(String fechaHora) {
        return LocalDateTime.parse(fechaHora).atZone(ZonaHorariaVivero.ZONA).toInstant();
    }

    @Test
    void con240LasFranjasSon02_06_10_14_18_22() {
        assertThat(CicloLectura.inicio(local("2026-10-03T01:59:00"), 240)).isEqualTo(local("2026-10-02T22:00:00"));
        assertThat(CicloLectura.inicio(local("2026-10-03T02:00:00"), 240)).isEqualTo(local("2026-10-03T02:00:00"));
        assertThat(CicloLectura.inicio(local("2026-10-03T13:59:59"), 240)).isEqualTo(local("2026-10-03T10:00:00"));
        assertThat(CicloLectura.inicio(local("2026-10-03T14:00:00"), 240)).isEqualTo(local("2026-10-03T14:00:00"));
    }

    @Test
    void conCicloQueNoDividePrecisoElUltimoDelDiaSeCortaALas02() {
        // 300 min: 02, 07, 12, 17, 22 y el de las 22:00 dura 4 h (hasta las 02:00).
        assertThat(CicloLectura.inicio(local("2026-10-03T23:00:00"), 300)).isEqualTo(local("2026-10-03T22:00:00"));
        assertThat(CicloLectura.inicio(local("2026-10-04T01:59:59"), 300)).isEqualTo(local("2026-10-03T22:00:00"));
        assertThat(CicloLectura.inicio(local("2026-10-04T02:30:00"), 300)).isEqualTo(local("2026-10-04T02:00:00"));
    }

    @Test
    void elIntervaloSeAcotaA60_360() {
        Instant t = local("2026-10-03T13:30:00");

        assertThat(CicloLectura.inicio(t, 5)).isEqualTo(CicloLectura.inicio(t, 60));
        assertThat(CicloLectura.inicio(t, 600)).isEqualTo(CicloLectura.inicio(t, 360));
        assertThat(CicloLectura.acotarMinutos(5)).isEqualTo(60);
        assertThat(CicloLectura.acotarMinutos(600)).isEqualTo(360);
        assertThat(CicloLectura.acotarMinutos(0)).isEqualTo(60);
        assertThat(CicloLectura.acotarMinutos(-10)).isEqualTo(60);
        assertThat(CicloLectura.acotarMinutos(240)).isEqualTo(240);
        assertThat(CicloLectura.acotarMinutos(60)).isEqualTo(60);
        assertThat(CicloLectura.acotarMinutos(360)).isEqualTo(360);
    }

    @Test
    void laZonaDelJvmNoCuenta() {
        // 12:00 UTC son las 09:00 locales: el ciclo es el de las 06:00 locales (09:00 UTC).
        assertThat(CicloLectura.inicio(Instant.parse("2026-10-03T12:00:00Z"), 240))
                .isEqualTo(Instant.parse("2026-10-03T09:00:00Z"));
    }

    @Test
    void conIntervaloDeUnaHoraCadaHoraEnPunto() {
        assertThat(CicloLectura.inicio(local("2026-10-03T10:59:59"), 60)).isEqualTo(local("2026-10-03T10:00:00"));
        assertThat(CicloLectura.inicio(local("2026-10-03T11:00:00"), 60)).isEqualTo(local("2026-10-03T11:00:00"));
    }

    // ------------------------------------------------------------------ vencimiento de una solicitud encolada

    @Test
    void unaSolicitudEsValidaEnSuCicloYEnElSiguiente_y_VenceAlEmpezarElTercero() {
        Instant pedida = local("2026-10-03T10:05:00");                       // ciclo 10:00-14:00

        assertThat(CicloLectura.vencida(pedida, local("2026-10-03T10:06:00"), 240)).isFalse();
        assertThat(CicloLectura.vencida(pedida, local("2026-10-03T13:59:59"), 240)).isFalse();   // su ciclo
        assertThat(CicloLectura.vencida(pedida, local("2026-10-03T14:00:00"), 240)).isFalse();   // el siguiente (gracia)
        assertThat(CicloLectura.vencida(pedida, local("2026-10-03T17:59:59"), 240)).isFalse();
        assertThat(CicloLectura.vencida(pedida, local("2026-10-03T18:00:00"), 240)).isTrue();    // el tercero
        assertThat(CicloLectura.vencida(pedida, local("2026-10-04T09:00:00"), 240)).isTrue();
    }

    @Test
    void unaSolicitudPedidaJustoEnElInicioDelCicloEsValidaTodoEseCicloYElSiguiente() {
        Instant pedida = local("2026-10-03T10:00:00");

        assertThat(CicloLectura.vencida(pedida, local("2026-10-03T17:59:59"), 240)).isFalse();
        assertThat(CicloLectura.vencida(pedida, local("2026-10-03T18:00:00"), 240)).isTrue();
    }

    @Test
    void elVencimientoRespetaElCicloQueSeCortaALas02() {
        // 300 min: 02, 07, 12, 17, 22 (el de las 22 dura 4 h). Pedida a las 22:30: vale hasta las 02:00 + 5 h = 07:00.
        Instant pedida = local("2026-10-03T22:30:00");

        assertThat(CicloLectura.vencida(pedida, local("2026-10-04T01:59:59"), 300)).isFalse();
        assertThat(CicloLectura.vencida(pedida, local("2026-10-04T06:59:59"), 300)).isFalse();
        assertThat(CicloLectura.vencida(pedida, local("2026-10-04T07:00:00"), 300)).isTrue();
    }

    @Test
    void elIntervaloFueraDeRangoSeAcotaTambienParaElVencimiento() {
        Instant pedida = local("2026-10-03T10:05:00");

        // 5 min se acota a 60: el ciclo es 10:00-11:00, la gracia llega hasta las 12:00.
        assertThat(CicloLectura.vencida(pedida, local("2026-10-03T11:59:59"), 5)).isFalse();
        assertThat(CicloLectura.vencida(pedida, local("2026-10-03T12:00:00"), 5)).isTrue();
    }
}
