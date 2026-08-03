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

// ----------------------------------------------------------------------------
//  Claves JSON - Ack
// ----------------------------------------------------------------------------
static const char* KEY_STATUS     = "status";
static const char* KEY_DETALLE    = "detalle";
static const char* KEY_TIPO       = "tipo";
static const char* STATUS_SUCCESS = "SUCCESS";
static const char* STATUS_ERROR   = "ERROR";

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
