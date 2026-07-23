// ============================================================================
//  util_json.h  -  Serialización/parseo de payloads (ArduinoJson)
// ============================================================================
#ifndef UTIL_JSON_H
#define UTIL_JSON_H

#include <Arduino.h>
#include "tipos.h"

namespace util_json {

// Serializa un PaqueteTelemetria al contrato de ingesta. Incluye las métricas
// extendidas solo si ENVIAR_METRICAS_EXTENDIDAS está activo. Escribe en buf.
// Devuelve la cantidad de bytes escritos.
size_t serializarTelemetria(const PaqueteTelemetria& p, char* buf, size_t n);

// Parsea un payload de comando. Devuelve true si es válido y completa 'out'.
bool parsearComando(const char* payload, unsigned int len, Comando& out);

// Serializa un Ack (status SUCCESS/ERROR + detalle). Escribe en buf.
size_t serializarAck(const Ack& a, char* buf, size_t n);

}  // namespace util_json

#endif  // UTIL_JSON_H
