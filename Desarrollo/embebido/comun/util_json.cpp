#include "util_json.h"
#include "contrato.h"
#include "config.h"
#include <ArduinoJson.h>

namespace util_json {

size_t serializarTelemetria(const PaqueteTelemetria& p, char* buf, size_t n) {
  JsonDocument doc;
  doc[KEY_MAC]       = p.mac;
  doc[KEY_BATTERY]   = p.battery;
  doc[KEY_SIGNAL]    = p.signal;
  doc[KEY_TIMESTAMP] = p.timestamp;

  JsonObject metrics = doc[KEY_METRICS].to<JsonObject>();
  // --- Métricas base (contrato actual del backend) ---
  if (p.suelo.estado == EstadoLectura::OK) {
    metrics[KEY_HUM_SUS] = p.suelo.humedad;
    metrics[KEY_CE]      = p.suelo.ec;
  }
  if (p.ambiente.estado == EstadoLectura::OK) {
    metrics[KEY_HUM_AMB] = p.ambiente.humedad;
    metrics[KEY_TEMP]    = p.ambiente.temperatura;
  }
  if (p.luz.estado == EstadoLectura::OK) {
    metrics[KEY_UV] = p.luz.valor;
  }

  // --- Métricas extendidas de suelo (solo si el backend las tolera) ---
#if ENVIAR_METRICAS_EXTENDIDAS
  if (p.suelo.estado == EstadoLectura::OK) {
    metrics[KEY_TEMP_SUELO] = p.suelo.temperatura;
    metrics[KEY_PH_SUELO]   = p.suelo.ph;
    metrics[KEY_N]          = p.suelo.nitrogeno;
    metrics[KEY_P]          = p.suelo.fosforo;
    metrics[KEY_K]          = p.suelo.potasio;
    metrics[KEY_SALINIDAD]  = p.suelo.salinidad;
    metrics[KEY_TDS]        = p.suelo.tds;
  }
#endif

  return serializeJson(doc, buf, n);
}

static TipoActuador parsearActuador(const char* s) {
  if (strcmp(s, ACT_VALVE) == 0) return TipoActuador::VALVULA;
  if (strcmp(s, ACT_PUMP) == 0)  return TipoActuador::BOMBA;
  if (strcmp(s, ACT_SHADE) == 0) return TipoActuador::MEDIASOMBRA;
  return TipoActuador::DESCONOCIDO;
}

bool parsearComando(const char* payload, unsigned int len, Comando& out) {
  JsonDocument doc;
  DeserializationError err = deserializeJson(doc, payload, len);
  if (err) {
    Serial.printf("[json] Comando inválido: %s\n", err.c_str());
    return false;
  }

  const char* actuadorStr = doc[KEY_ACTUADOR] | "";
  out.actuador = parsearActuador(actuadorStr);
  if (out.actuador == TipoActuador::DESCONOCIDO) {
    Serial.println("[json] Comando con actuador desconocido");
    return false;
  }

  const char* cmdId = doc[KEY_COMMAND_ID] | "";
  strncpy(out.commandId, cmdId, sizeof(out.commandId) - 1);
  out.commandId[sizeof(out.commandId) - 1] = '\0';

  const char* accionStr = doc[KEY_ACCION] | "";
  strncpy(out.accion, accionStr, sizeof(out.accion) - 1);
  out.accion[sizeof(out.accion) - 1] = '\0';

  JsonObject params = doc[KEY_PARAMETROS];
  out.durationSec = params[KEY_DURATION] | 0L;
  out.ml          = params[KEY_ML]        | 0.0f;
  out.targetPct   = params[KEY_TARGET_PCT]| 0;
  return true;
}

size_t serializarAck(const Ack& a, char* buf, size_t n) {
  JsonDocument doc;
  if (a.commandId[0] != '\0') {
    doc[KEY_COMMAND_ID] = a.commandId;
  }
  doc[KEY_STATUS] = (a.status == EstadoAck::SUCCESS) ? STATUS_SUCCESS : STATUS_ERROR;

  JsonObject detalle = doc[KEY_DETALLE].to<JsonObject>();
  if (a.tipoDetalle[0] != '\0') {
    detalle[KEY_TIPO] = a.tipoDetalle;
  }
  if (a.durationSec > 0) detalle[KEY_DURATION]   = a.durationSec;
  if (a.ml > 0)          detalle[KEY_ML]          = a.ml;
  if (a.targetPct > 0)   detalle[KEY_TARGET_PCT]  = a.targetPct;

  return serializeJson(doc, buf, n);
}

}  // namespace util_json
