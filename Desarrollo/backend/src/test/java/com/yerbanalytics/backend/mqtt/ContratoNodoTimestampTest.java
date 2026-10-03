package com.yerbanalytics.backend.mqtt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El {@code timestamp} del contrato lo publica el firmware en segundos epoch y el simulador en
 * milisegundos: la ingesta lo normaliza por VALOR (no por origen) a milisegundos.
 */
@DisplayName("ContratoNodo - normalización del timestamp")
class ContratoNodoTimestampTest {

    /** 2026-10-03T12:00:00Z */
    private static final long AHORA_MS = 1_791_028_800_000L;
    private static final long AHORA_S = AHORA_MS / 1000;

    @Test
    void segundosEpochPlausiblesSeMultiplicanPorMil() {
        assertThat(ContratoNodo.timestampAMs(AHORA_S - 5, AHORA_MS)).isEqualTo((AHORA_S - 5) * 1000);
    }

    @Test
    void milisegundosSeDejanTalCual() {
        assertThat(ContratoNodo.timestampAMs(AHORA_MS - 5_000, AHORA_MS)).isEqualTo(AHORA_MS - 5_000);
    }

    @Test
    void ausenteNoEsPlausible() {
        assertThat(ContratoNodo.timestampAMs(null, AHORA_MS)).isNull();
    }

    @Test
    void segundosDesdeElArranqueNoSonPlausibles() {
        // Sin NTP el firmware manda millis()/1000: unos pocos miles de segundos.
        assertThat(ContratoNodo.timestampAMs(4_200L, AHORA_MS)).isNull();
        assertThat(ContratoNodo.timestampAMs(0L, AHORA_MS)).isNull();
        assertThat(ContratoNodo.timestampAMs(-1L, AHORA_MS)).isNull();
    }

    @Test
    void anteriorA2020NoEsPlausibleEnNingunaUnidad() {
        assertThat(ContratoNodo.timestampAMs(1_500_000_000L, AHORA_MS)).isNull();
        assertThat(ContratoNodo.timestampAMs(1_500_000_000_000L, AHORA_MS)).isNull();
    }

    @Test
    void unRelojAdelantadoNoEsPlausible() {
        // Una hora de adelanto en segundos y en ms: si se aceptara, bloquearía las lecturas reales.
        assertThat(ContratoNodo.timestampAMs(AHORA_S + 3_600, AHORA_MS)).isNull();
        assertThat(ContratoNodo.timestampAMs(AHORA_MS + 3_600_000, AHORA_MS)).isNull();
    }

    @Test
    void unaPequenaDerivaHaciaElFuturoSeTolera() {
        assertThat(ContratoNodo.timestampAMs(AHORA_S + 30, AHORA_MS)).isEqualTo((AHORA_S + 30) * 1000);
    }
}
