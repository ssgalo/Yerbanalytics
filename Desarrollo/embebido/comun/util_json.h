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

// Parsea un payload de comando. Devuelve true si es válido y completa 'out'
// (salvo out.sector, que lo pone el llamador según el tópico).
bool parsearComando(const char* payload, unsigned int len, Comando& out);

// Resultado de parsear un comando de zona (nursery/zone/{zona}/command).
enum class ResultadoComandoZona { LEER_AHORA, ACTUADOR, ACCION_DESCONOCIDA, INVALIDO };

// Parsea un comando de zona. 'commandId' (>= 40 bytes) recibe el id para el log ("" si no vino).
// LEER_AHORA (sin distinguir mayúsculas) o ACTUADOR: trae "actuador" (hoy sólo la mediasombra, que es de la
// macro-zona), y se parsea como un comando de actuador con parsearComando.
ResultadoComandoZona parsearComandoZona(const char* payload, unsigned int len, char* commandId, size_t n);

// Serializa un Ack (status SUCCESS/ERROR + detalle). Escribe en buf.
size_t serializarAck(const Ack& a, char* buf, size_t n);

// Serializa un error del nodo al contrato de errores ({nodo, sector, tipo, valor, timestamp}). 'sectorId' es el id real
// del sector ("" en el error de la mediasombra). Escribe en buf.
size_t serializarError(const ErrorNodo& e, const char* nodo, const char* sectorId, uint32_t timestamp, char* buf, size_t n);

// Parsea un comando del riel. Siempre completa 'out': si no es válido,
// out.valido = false y out.detalle dice por qué.
void parsearComandoRiel(const char* payload, unsigned int len, ComandoRiel& out);

// Serializa un evento del riel. posicion < 0 → null; codigo/detalle vacíos → se omiten.
size_t serializarEventoRiel(const EventoRiel& e, char* buf, size_t n);

}  // namespace util_json

#endif  // UTIL_JSON_H
