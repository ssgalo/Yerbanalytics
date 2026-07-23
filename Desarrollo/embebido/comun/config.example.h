// ============================================================================
//  config.example.h  -  Plantilla de configuración del nodo ESP32
// ----------------------------------------------------------------------------
//  Copiar este archivo a "config.h" y completar los valores reales.
//  config.h está en .gitignore: NUNCA se versiona (contiene credenciales).
//
//  Este es el ÚNICO lugar para recalibrar el nodo: credenciales, broker,
//  identidad (zona/sector), pines, intervalos y flags. La lógica de
//  los módulos no debe contener literales de configuración.
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
//  Red WiFi
// ----------------------------------------------------------------------------
#define WIFI_SSID              "TU_SSID"
#define WIFI_PASSWORD          "TU_PASSWORD"

// ----------------------------------------------------------------------------
//  Broker MQTT
// ----------------------------------------------------------------------------
#define MQTT_HOST              "192.168.0.100"   // IP/host del broker
#define MQTT_PORT              1883
#define MQTT_CLIENT_ID_BASE    "yerbanalytics-esp32"  // se le agrega un sufijo único
#define MQTT_BUFFER_SIZE       1024               // debe alcanzar para el JSON extendido

// ----------------------------------------------------------------------------
//  Identidad espacial del nodo (trazabilidad - HU-18)
// ----------------------------------------------------------------------------
#define NODO_ZONA_ID           "MZ-1"    // macro-zona
#define NODO_SECTOR_ID         "S-001"   // sector (usado por command/ack y, si aplica, telemetría)

// Nivel del topic de telemetría (flag de compilación: 1 = sí, 0 = no):
//   1 -> nursery/zone/{zona}/sector/{sector}/telemetry  (nodo combinado)
//   0 -> nursery/zone/{zona}/telemetry                  (testigo puro, backend actual)
#define TELEMETRIA_NIVEL_SECTOR   0

// ----------------------------------------------------------------------------
//  Intervalo de muestreo (alineado con SENSOR_POLLING_INTERVAL del backend)
// ----------------------------------------------------------------------------
#define SENSOR_POLLING_INTERVAL_MS   30000UL   // 30 s (ajustar según ingeniería)

// ----------------------------------------------------------------------------
//  Payload extendido de métricas de suelo (pH, N, P, K, salinidad, TDS)
// ----------------------------------------------------------------------------
//  Flag de compilación (1 = sí, 0 = no):
//  0 -> envía SOLO las 5 métricas base (100% compatible con el backend
//       actual, que falla ante campos desconocidos).
//  1 -> agrega las métricas extendidas. Requiere que el backend tolere
//       campos desconocidos o extienda MqttTelemetryPayload. Ver README.
#define ENVIAR_METRICAS_EXTENDIDAS   0

// ----------------------------------------------------------------------------
//  Pines - Sensores  (basados en sensores_unificados.ino)
// ----------------------------------------------------------------------------
#define PIN_SENSOR_LUZ         39    // ADC1 - fotoresistor (LDR)
#define PIN_LED_ESTADO         5     // LED de estado / actividad

#define PIN_RS485_RX           16    // UART2 RX  (sonda de suelo Modbus)
#define PIN_RS485_TX           17    // UART2 TX
#define PIN_RS485_DE           4     // DE/RE del transceiver RS-485
#define RS485_BAUD             4800  // 4800 8N1

#define PIN_DHT                18    // DHT11 (ambiente)

#define PIN_BATERIA            34    // ADC1 - divisor de batería (opcional)

// Calibración del sensor de luz (ADC crudo -> %). Ajustar en campo.
#define LUZ_ADC_OSCURIDAD      3500
#define LUZ_ADC_BRILLANTE      500

// ----------------------------------------------------------------------------
//  Pines - Actuadores
// ----------------------------------------------------------------------------
#define PIN_RELE_VALVULA       25    // electroválvula de riego
#define PIN_CAUDALIMETRO       35    // feedback de flujo (entrada por pulsos)

#define PIN_BOMBA              26    // bomba peristáltica (dosificación)

#define PIN_MOTOR_MEDIASOMBRA_A  32  // driver motor (dirección A)
#define PIN_MOTOR_MEDIASOMBRA_B  33  // driver motor (dirección B)
#define PIN_FIN_CARRERA          27  // fin de carrera de la mediasombra
#define PIN_SENSOR_CORRIENTE     36  // ADC - sensado de corriente del motor

// ----------------------------------------------------------------------------
//  Límites de seguridad locales (última barrera física)
// ----------------------------------------------------------------------------
#define LIMITE_VALVULA_SEG_MAX     120    // apertura continua máx (s)
#define LIMITE_BOMBA_ML_MAX        50     // dosis máx por comando (ml)
#define LIMITE_CORRIENTE_MOTOR_MAX 3000   // umbral ADC de sobrecorriente
#define TIMEOUT_FIN_CARRERA_MS     15000  // sin fin de carrera => atasco
#define TIMEOUT_CAUDAL_MS          10000  // sin flujo => falla hidráulica

// Factor de calibración de la bomba peristáltica (ms de bombeo por ml).
#define BOMBA_MS_POR_ML            1000

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
#define TASK_PRIO_RED          2   // WiFi/MQTT
#define TASK_PRIO_SENSADO      3   // muestreo periódico
#define TASK_PRIO_ACTUACION    4   // ejecución de comandos (la más crítica)

#endif  // CONFIG_H
