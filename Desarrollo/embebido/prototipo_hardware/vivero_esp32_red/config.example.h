// ============================================================
//  config.example.h — plantilla de config.h para vivero_esp32_red
//  Copiar como config.h (en esta misma carpeta) y completar.
//  config.h NO se versiona (está en Desarrollo/embebido/.gitignore).
//
//  Placa: ESP32-S3 DevKitC-1 N16R8 (flash 16 MB, PSRAM octal).
//  Los pines son ESPEJO de Desarrollo/embebido/comun/config.example.h
//  (fuente de verdad del cableado): si cambian allá, cambian acá.
// ============================================================
#pragma once

// Red WiFi de 2,4 GHz (el ESP32 no ve redes de 5 GHz)
#define WIFI_SSID      "mi-red"
#define WIFI_PASSWORD  "mi-clave"

// Broker MQTT: IP LAN de la PC donde corre Mosquitto (docker compose).
// No poner "localhost": para el ESP32, localhost es él mismo.
// En Linux la IP sale de `ip a` (ej. 192.168.0.100).
#define MQTT_HOST      "192.168.1.64"
#define MQTT_PORT      1883

// Prefijo del clientId MQTT; el sketch le agrega la MAC de la placa.
#define MQTT_CLIENT_ID_BASE "riel-esp32"

// ============================================================
//  PINES — ESP32-S3 DevKitC-1 N16R8
// ------------------------------------------------------------
//  No usar: 0, 3, 45, 46 (strapping) · 19, 20 (USB nativo) ·
//  26-32 (flash) · 33-37 (PSRAM octal) · 43, 44 (UART0, consola) ·
//  38 y 48 (LED RGB de la placa, según revisión).
//  Analógico con WiFi encendido: sólo ADC1 (GPIO 1-10).
//  Libre: GPIO 7 (ADC1).
// ============================================================

// ── Riel de la cámara: NEMA 17 + DRV8825 ─────────────────────
#define PIN_RIEL_STEP          14
#define PIN_RIEL_DIR           15
#define PIN_RIEL_SLEEP         16   // LOW = driver dormido (sin ruido ni consumo). RESET del módulo fijo a 3V3 del ESP32
#define PIN_RIEL_FIN_HOME      21   // final de carrera extremo home
#define PIN_RIEL_FIN_OPUESTO   47   // final de carrera extremo opuesto
// Nivel de DIR que mueve el carro hacia HOME. Si va al revés, cambiar HIGH↔LOW.
#define RIEL_DIR_HACIA_HOME    HIGH

// ── Mediasombra: motor DC JGA25-370 + L298N (canal A) ────────
#define PIN_MEDIASOMBRA_IN1              39
#define PIN_MEDIASOMBRA_IN2              40
#define PIN_MEDIASOMBRA_ENA              41   // PWM velocidad
#define PIN_MEDIASOMBRA_FIN_ENROLLADA    42
#define PIN_MEDIASOMBRA_FIN_DESENROLLADA 2

// ── Finales de carrera (riel y mediasombra) ──────────────────
// Cableado: C y NC del microswitch entre el pin y GND, con INPUT_PULLUP.
// 1 = NC (normalmente cerrado) → activado = HIGH. Un cable cortado lee "activado".
// 0 = NO (normalmente abierto) → activado = LOW.
#define FINES_CARRERA_NC       1

// ── Módulo de 4 relés (riego) ────────────────────────────────
#define PIN_RELE_BOMBA_RIEGO   10   // IN1: minibomba de agua (riego)
#define PIN_RELE_BOMBA_INSUMO  11   // IN2: minibomba de insumo (fertilizante/pesticida), compartida
#define PIN_RELE_VALVULA_S1    12   // IN3: electroválvula del aspersor del sector 1
#define PIN_RELE_VALVULA_S2    13   // IN4: electroválvula del aspersor del sector 2
// 1 = el relé se cierra con el IN en LOW (módulos con optoacoplador, lo típico).
#define RELE_ACTIVO_EN_LOW     1

// ── Sensores (no los usa este sketch; espejo para el firmware modular) ──
#define PIN_LDR_AO             1    // ADC1. Módulo alimentado a 3,3 V. DO no se conecta
#define PIN_HCSR04_TRIG        4    // compartido por los dos HC-SR04
#define PIN_HCSR04_ECHO_1      5    // tanque 1 — divisor 1k/2k (ECHO sale a 5 V)
#define PIN_HCSR04_ECHO_2      6    // tanque 2 — divisor 1k/2k
#define PIN_RS485_DE           8    // DE+RE del transceiver (sonda de suelo)
#define PIN_RS485_TX           17   // UART1 TX
#define PIN_RS485_RX           18   // UART1 RX
#define PIN_DHT                9    // DHT11
