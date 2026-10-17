// ============================================================================
//  config.example.h  -  Plantilla de configuración del nodo ESP32
// ----------------------------------------------------------------------------
//  Copiar este archivo a "config.h" y completar los valores reales.
//  config.h está en .gitignore: NUNCA se versiona (contiene credenciales).
//
//  Este es el ÚNICO lugar para recalibrar el nodo: credenciales, broker,
//  identidad (zona/sectores), pines, intervalos y flags. La lógica de
//  los módulos no debe contener literales de configuración.
//
//  Placa: ESP32-S3 DevKitC-1 N16R8 (flash 16 MB, PSRAM octal).
//  Este archivo es la fuente de verdad del cableado:
//  prototipo_hardware/vivero_esp32_red/config.example.h repite el bloque de pines
//  con los mismos nombres. Si cambia acá, cambia allá.
// ============================================================================
#ifndef CONFIG_H
#define CONFIG_H

// ----------------------------------------------------------------------------
//  Configuración compartida por los tres firmwares (nodo_sensor / nodo_actuador
//  / nodo_combinado). El tipo de nodo NO se elige acá: se elige compilando el
//  sketch correspondiente (ver README). Cada nodo usa solo la porción de este
//  archivo que le aplica (p.ej. un nodo sensor ignora los pines de actuadores).
// ----------------------------------------------------------------------------

// ----------------------------------------------------------------------------
//  Red WiFi (2,4 GHz: el ESP32 no ve redes de 5 GHz)
// ----------------------------------------------------------------------------
#define WIFI_SSID              "TU_SSID"
#define WIFI_PASSWORD          "TU_PASSWORD"

// ----------------------------------------------------------------------------
//  Broker MQTT
// ----------------------------------------------------------------------------
#define MQTT_HOST              "192.168.0.100"   // IP LAN de la PC del broker (no "localhost")
#define MQTT_PORT              1883
#define MQTT_CLIENT_ID_BASE    "yerbanalytics-esp32"  // se le agrega un sufijo único
#define MQTT_BUFFER_SIZE       1024               // debe alcanzar para el JSON extendido

// ----------------------------------------------------------------------------
//  Identidad espacial del nodo (trazabilidad - HU-18)
// ----------------------------------------------------------------------------
#define NODO_ZONA_ID           "MZ-1"       // macro-zona
// Ids REALES de los sectores en la base (formato {zona}-NNN). El nodo atiende los
// dos: se suscribe al tópico de comando de cada uno y publica el ack en el del sector.
// Sector 1 = válvula 1 (PIN_RELE_VALVULA_S1), sector 2 = válvula 2.
#define NODO_SECTOR_1_ID       "MZ-1-001"
#define NODO_SECTOR_2_ID       "MZ-1-002"

// Nivel del topic de telemetría (flag de compilación: 1 = sí, 0 = no):
//   1 -> nursery/zone/{zona}/sector/{NODO_SECTOR_1_ID}/telemetry  (el backend hoy NO lo escucha)
//   0 -> nursery/zone/{zona}/telemetry                            (lo que escucha el backend)
#define TELEMETRIA_NIVEL_SECTOR   0

// ----------------------------------------------------------------------------
//  Payload extendido de métricas de suelo (pH, N, P, K, salinidad, TDS)
// ----------------------------------------------------------------------------
//  Flag de compilación (1 = sí, 0 = no). El backend ignora las claves que no
//  modela (salinidad, tds), así que es seguro activarlo.
#define ENVIAR_METRICAS_EXTENDIDAS   1

// Errores de caudal (S-05): SIN_CAUDAL / CAUDAL_EXCESIVO en nursery/zone/{zona}/errores. El caudalímetro NO está instalado en
// el vivero (se incorpora en la versión final, reglas_v2 §1.2): con 0 el firmware no compila el punto de integración
// task_actuacion::informarErrorCaudal(). Sin hardware, el backend recibe estos errores sólo del simulador.
// La falla de la mediasombra (MEDIASOMBRA_FIN_DE_CARRERA) se informa siempre: ya hay un final de carrera por sentido.
#define ENVIAR_ERRORES_CAUDAL        0

// ============================================================================
//  PINES — ESP32-S3 DevKitC-1 N16R8
// ----------------------------------------------------------------------------
//  No usar: 0, 3, 45, 46 (strapping) · 19, 20 (USB nativo) ·
//  26-32 (flash) · 33-37 (PSRAM octal) · 43, 44 (UART0, consola) ·
//  38 y 48 (LED RGB de la placa, según revisión).
//  Analógico con WiFi encendido: sólo ADC1 (GPIO 1-10).
//  Libre: GPIO 7 (ADC1; reservado para la batería si se agrega).
// ============================================================================

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

// ── Sensores ─────────────────────────────────────────────────
#define PIN_LDR_AO             1    // ADC1. Módulo alimentado a 3,3 V. DO no se conecta
#define PIN_HCSR04_TRIG        4    // compartido por los dos HC-SR04
#define PIN_HCSR04_ECHO_1      5    // tanque de agua    — divisor 1k/2k (ECHO sale a 5 V)
#define PIN_HCSR04_ECHO_2      6    // tanque de insumo  — divisor 1k/2k
#define PIN_RS485_DE           8    // DE+RE del transceiver (sonda de suelo)
#define PIN_RS485_TX           17   // UART1 TX
#define PIN_RS485_RX           18   // UART1 RX
#define PIN_DHT                9    // DHT11

// ── LED de estado: el RGB de la placa ────────────────────────
// RGB_BUILTIN lo define el core (GPIO 48); digitalWrite lo prende en blanco.
// En la revisión v1.1 de la placa el LED está en el 38 y no prende.
#ifdef RGB_BUILTIN
  #define PIN_LED_ESTADO       RGB_BUILTIN
#else
  #define PIN_LED_ESTADO       48
#endif

// ----------------------------------------------------------------------------
//  Sonda de suelo (RS-485 / Modbus RTU)
// ----------------------------------------------------------------------------
#define RS485_UART             1     // UART1 del S3
#define RS485_BAUD             4800  // 4800 8N1

// ----------------------------------------------------------------------------
//  Sensor de luz (LDR). Calibración ADC crudo -> %. Recalibrar en el S3.
// ----------------------------------------------------------------------------
#define LUZ_ADC_OSCURIDAD      3500
#define LUZ_ADC_BRILLANTE      500

// ----------------------------------------------------------------------------
//  Nivel de los tanques (HC-SR04 mirando hacia abajo desde la tapa)
// ----------------------------------------------------------------------------
//  Distancia sensor→agua con el tanque lleno y vacío, en cm. Nivel % = interpolación.
#define TANQUE_AGUA_DIST_LLENO_CM     3.0f
#define TANQUE_AGUA_DIST_VACIO_CM     20.0f
#define TANQUE_INSUMO_DIST_LLENO_CM   3.0f
#define TANQUE_INSUMO_DIST_VACIO_CM   20.0f
#define HCSR04_TIMEOUT_US             30000UL   // sin eco en este tiempo = lectura inválida (~5 m)

// ----------------------------------------------------------------------------
//  Batería (el prototipo no tiene). 0 = no se mide ni se publica "battery".
// ----------------------------------------------------------------------------
#define BATERIA_INSTALADA      0
#define PIN_BATERIA            7     // ADC1 - divisor resistivo (calibrar)

// ----------------------------------------------------------------------------
//  Riego (válvulas + bombas por relés)
// ----------------------------------------------------------------------------
// La válvula abre este tiempo antes de que arranque la bomba (nunca bombea contra cerrado).
#define RIEGO_VALVULA_ANTES_DE_BOMBA_MS  300
// Apertura continua máx de la válvula (s). Debe ser >= CONTRATO_VALVULA_DURACION_MAX_SEG
// (1200 s, contrato.h): el backend puede pedir hasta eso. Si es menor, no compila (act_riego.cpp).
#define LIMITE_VALVULA_SEG_MAX     1200
// Insumo: el backend manda "pump ON" sin ml → se aplica INSUMO_SEG_POR_DEFECTO.
// Si el comando trae "ml", se convierte a tiempo con BOMBA_INSUMO_MS_POR_ML.
#define INSUMO_SEG_POR_DEFECTO     10
#define BOMBA_INSUMO_MS_POR_ML     1000   // calibrar: ms de bombeo por ml
#define LIMITE_BOMBA_ML_MAX        50     // dosis máx por comando (ml)
#define LIMITE_INSUMO_SEG_MAX      60     // tiempo máx de la bomba de insumo por comando (s)

// ----------------------------------------------------------------------------
//  Mediasombra (dos estados: enrollada / desenrollada)
// ----------------------------------------------------------------------------
// El backend manda targetPct = % de APERTURA. >= umbral → enrollar (deja pasar el sol);
// < umbral → desenrollar (cubre).
#define MEDIASOMBRA_UMBRAL_APERTURA_PCT  50
#define MEDIASOMBRA_VELOCIDAD_PWM        200    // 0-255 (200 ≈ 78 %)
#define TIMEOUT_MEDIASOMBRA_MS           30000  // sin final de carrera en este tiempo => falla mecánica

// ----------------------------------------------------------------------------
//  Riel de la cámara (calibración en pasos)
// ----------------------------------------------------------------------------
#define RIEL_POSICION_SECTOR_1     21000
#define RIEL_POSICION_SECTOR_2     42000
#define RIEL_MAX_PASOS_HOMING      80000   // > largo total del riel en pasos (con margen)
#define RIEL_MEDIO_PERIODO_US      500     // µs en HIGH y en LOW por paso (más alto = más lento y más torque)

// ----------------------------------------------------------------------------
//  Buffer offline (NVS)
// ----------------------------------------------------------------------------
#define BUFFER_OFFLINE_CAPACIDAD   50     // máx de lecturas encoladas (FIFO)

// ----------------------------------------------------------------------------
//  NTP (timestamp real de las lecturas)
// ----------------------------------------------------------------------------
#define NTP_SERVIDOR           "pool.ntp.org"
#define NTP_GMT_OFFSET_SEG     (-3 * 3600)   // Argentina UTC-3
#define NTP_DST_OFFSET_SEG     0

// ----------------------------------------------------------------------------
//  FreeRTOS - prioridades y stacks de las tareas
// ----------------------------------------------------------------------------
#define TASK_STACK_SIZE        6144
#define TASK_PRIO_RIEL         1   // la más baja: genera los pasos con espera activa y las demás la interrumpen
#define TASK_PRIO_RED          2   // WiFi/MQTT
#define TASK_PRIO_SENSADO      3   // muestreo periódico
#define TASK_PRIO_ACTUACION    4   // ejecución de comandos (la más crítica)

#endif  // CONFIG_H
