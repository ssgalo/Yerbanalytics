// ============================================================================
//  contrato.h  -  Contrato MQTT del nodo (fuente única de topics y claves JSON)
// ----------------------------------------------------------------------------
//  Alineado con add-mqtt-telemetry y add-rules-engine. Tres tópicos a nivel de
//  sector; la telemetría del testigo puro usa el topic de zona.
//
//    Ingesta   : nursery/zone/{zona}/sector/{sector}/telemetry   (o zona)
//    Comando   : nursery/zone/{zona}/sector/{sector}/command     (QoS 2)
//    Ack       : nursery/zone/{zona}/sector/{sector}/ack
//
//  Ningún otro módulo debe hardcodear nombres de topic o claves JSON.
// ============================================================================
#ifndef CONTRATO_H
#define CONTRATO_H

#include <Arduino.h>
#include "config.h"

// ----------------------------------------------------------------------------
//  QoS por canal
// ----------------------------------------------------------------------------
static const uint8_t QOS_TELEMETRIA = 1;
static const uint8_t QOS_COMANDO     = 2;   // exactly-once: evita doble dosis/riego
static const uint8_t QOS_ACK         = 1;

// ----------------------------------------------------------------------------
//  Claves JSON - Telemetría
// ----------------------------------------------------------------------------
static const char* KEY_MAC        = "mac";
static const char* KEY_BATTERY    = "battery";
static const char* KEY_SIGNAL     = "signal";
// timestamp: SEGUNDOS epoch (reloj::ahoraEpoch(), uint32_t). Sin NTP son segundos desde el
// arranque. El backend lo normaliza a ms por valor (ContratoNodo.timestampAMs): segundos
// plausibles se multiplican por 1000; uno inutilizable se reemplaza por la hora de recepción.
// El simulador publica milisegundos y el backend también lo acepta.
static const char* KEY_TIMESTAMP  = "timestamp";
static const char* KEY_METRICS    = "metrics";
//  Métricas base (contrato actual del backend)
static const char* KEY_HUM_SUS    = "humSus";
static const char* KEY_HUM_AMB    = "humAmb";
static const char* KEY_TEMP       = "temp";
static const char* KEY_CE         = "ce";
static const char* KEY_UV         = "uv";
//  Métricas extendidas (bajo flag ENVIAR_METRICAS_EXTENDIDAS)
static const char* KEY_TEMP_SUELO = "tempSuelo";
static const char* KEY_PH_SUELO   = "phSuelo";
static const char* KEY_N          = "n";
static const char* KEY_P          = "p";
static const char* KEY_K          = "k";
static const char* KEY_SALINIDAD  = "salinidad";
static const char* KEY_TDS        = "tds";

// ----------------------------------------------------------------------------
//  Claves JSON - Comando
// ----------------------------------------------------------------------------
static const char* KEY_COMMAND_ID = "commandId";
static const char* KEY_ACTUADOR   = "actuador";
static const char* KEY_ACCION     = "accion";
static const char* KEY_PARAMETROS = "parametros";
static const char* KEY_DURATION   = "durationSec";
static const char* KEY_ML         = "ml";
static const char* KEY_TARGET_PCT = "targetPct";

//  Valores del campo "actuador"
static const char* ACT_VALVE      = "valve";
static const char* ACT_PUMP       = "pump";
static const char* ACT_SHADE      = "shade";

// Duración máxima de apertura de la válvula que el backend puede pedir en "durationSec" (s).
// Fuente de verdad del valor: este archivo. Espejos que deben decir lo mismo:
//   - backend:   ContratoNodo.DURACION_VALVULA_MAX_SEG
//   - simulador: simulador/server/contract.ts (VALVE_MAX_DURATION_SEC)
// El límite local LIMITE_VALVULA_SEG_MAX (config.h) debe ser >= a este valor
// (act_valvula.cpp lo verifica al compilar).
#define CONTRATO_VALVULA_DURACION_MAX_SEG 1200

// ----------------------------------------------------------------------------
//  Claves JSON - Ack
// ----------------------------------------------------------------------------
static const char* KEY_STATUS     = "status";
static const char* KEY_DETALLE    = "detalle";
static const char* KEY_TIPO       = "tipo";
static const char* STATUS_SUCCESS = "SUCCESS";
static const char* STATUS_ERROR   = "ERROR";

// ----------------------------------------------------------------------------
//  Riel de la cámara (add-pasada-riel, design.md §1)
// ----------------------------------------------------------------------------
//  Un solo riel, sin id en el tópico. Lo implementa el sketch
//  prototipo_hardware/vivero_esp32_red (que NO incluye este archivo: lo espeja con
//  #define locales). Espejos que deben decir lo mismo:
//    - backend:   mqtt/ContratoRiel.java
//    - simulador: simulador/server/contract.ts (RAIL_*; sin comportamiento)
//    - firmware:  prototipo_hardware/vivero_esp32_red/vivero_esp32_red.ino (RIEL_*)
//
//  Comando  backend → ESP32 · nursery/rail/command · backend publica QoS 1, ESP32 suscribe QoS 1
//    {"commandId":"<uuid>","actuador":"rail","accion":"IR_A","parametros":{"posicion":1}}
//    {"commandId":"<uuid>","actuador":"rail","accion":"HOME","parametros":{}}
//    posicion: 1 | 2 (lógica; el firmware la traduce a pasos). HOME = homing contra el
//    final de carrera (re-referencia, no "ir al paso 0").
//
//  Evento   ESP32 → backend · nursery/rail/event · ESP32 publica QoS 0 (PubSubClient no
//  publica QoS 1), backend suscribe QoS 1. Sin retain en ninguno de los dos.
//    {"commandId":"<uuid>","status":"ACEPTADO","posicion":null,"pasos":0}
//    {"commandId":"<uuid>","status":"LLEGO","posicion":1,"pasos":21000}
//    {"commandId":"<uuid>","status":"ERROR","posicion":null,"pasos":17345,
//     "codigo":"FIN_DE_CARRERA","detalle":"..."}
//    posicion: 0 home, 1, 2, o null (entre posiciones / sin referencia).
//    pasos: pasos_actuales del firmware (diagnóstico). codigo/detalle: sólo en ERROR.
//
//  Códigos de ERROR:
//    COMANDO_INVALIDO    JSON ilegible, actuador != rail, accion desconocida o posicion ∉ {1,2}
//    HOME_NO_ENCONTRADO  el homing superó MAX_PASOS_HOMING o tocó el final opuesto
//    FIN_DE_CARRERA      un IR_A tocó un final de carrera antes de llegar
//    REEMPLAZADO         llegó otro commandId mientras éste se movía; éste se abortó
//
//  Idempotencia y concurrencia (firmware):
//    1. commandId igual al EN EJECUCIÓN        → se ignora (redelivery QoS 1)
//    2. commandId igual al ÚLTIMO TERMINADO     → se republica su evento final
//    3. commandId visto entre los últimos 4     → se ignora
//    4. otro commandId durante un movimiento    → el último gana: ERROR REEMPLAZADO del
//                                                 viejo y se ejecuta el nuevo
static const char* TOPIC_RIEL_COMANDO = "nursery/rail/command";
static const char* TOPIC_RIEL_EVENTO  = "nursery/rail/event";
static const uint8_t QOS_RIEL_COMANDO = 1;   // publica el backend; el ESP32 suscribe con 1
static const uint8_t QOS_RIEL_EVENTO  = 0;   // publica el ESP32 (PubSubClient: sólo QoS 0)

//  Claves (además de KEY_COMMAND_ID, KEY_ACTUADOR, KEY_ACCION, KEY_PARAMETROS,
//  KEY_STATUS y KEY_DETALLE, que se comparten con actuadores y ack)
static const char* KEY_POSICION   = "posicion";
static const char* KEY_PASOS      = "pasos";
static const char* KEY_CODIGO     = "codigo";

static const char* ACT_RAIL                 = "rail";
static const char* ACCION_RIEL_IR_A         = "IR_A";
static const char* ACCION_RIEL_HOME         = "HOME";
static const char* STATUS_RIEL_ACEPTADO     = "ACEPTADO";
static const char* STATUS_RIEL_LLEGO        = "LLEGO";
static const char* STATUS_RIEL_ERROR        = "ERROR";   // mismo valor que STATUS_ERROR
static const char* CODIGO_RIEL_COMANDO_INVALIDO   = "COMANDO_INVALIDO";
static const char* CODIGO_RIEL_HOME_NO_ENCONTRADO = "HOME_NO_ENCONTRADO";
static const char* CODIGO_RIEL_FIN_DE_CARRERA     = "FIN_DE_CARRERA";
static const char* CODIGO_RIEL_REEMPLAZADO        = "REEMPLAZADO";

// ----------------------------------------------------------------------------
//  Construcción de topics
// ----------------------------------------------------------------------------
//  Se escriben en un buffer provisto por el llamador (sin heap).

// Telemetría: nivel sector o zona según TELEMETRIA_NIVEL_SECTOR.
inline void contratoTopicTelemetria(char* buf, size_t n) {
#if TELEMETRIA_NIVEL_SECTOR
  snprintf(buf, n, "nursery/zone/%s/sector/%s/telemetry", NODO_ZONA_ID, NODO_SECTOR_ID);
#else
  snprintf(buf, n, "nursery/zone/%s/telemetry", NODO_ZONA_ID);
#endif
}

// Comando (siempre a nivel sector): el nodo actuador se suscribe a este topic.
inline void contratoTopicComando(char* buf, size_t n) {
  snprintf(buf, n, "nursery/zone/%s/sector/%s/command", NODO_ZONA_ID, NODO_SECTOR_ID);
}

// Ack (siempre a nivel sector): el nodo publica el resultado del comando.
inline void contratoTopicAck(char* buf, size_t n) {
  snprintf(buf, n, "nursery/zone/%s/sector/%s/ack", NODO_ZONA_ID, NODO_SECTOR_ID);
}

#endif  // CONTRATO_H
