#include "util_json.h"
#include "contrato.h"
#include "config.h"
#include <ArduinoJson.h>

namespace util_json {

size_t serializarTelemetria(const PaqueteTelemetria& p, char* buf, size_t n) {
  JsonDocument doc;
  doc[KEY_MAC]       = p.mac;
#if BATERIA_INSTALADA
  doc[KEY_BATTERY]   = p.battery;
#endif
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

  // --- Métricas extendidas de suelo ---
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

  // --- Nivel de los tanques (el backend todavía no las modela: las ignora) ---
  if (p.tanques.estadoAgua == EstadoLectura::OK) {
    metrics[KEY_NIVEL_AGUA] = p.tanques.nivelAgua;
  }
  if (p.tanques.estadoInsumo == EstadoLectura::OK) {
    metrics[KEY_NIVEL_INSUMO] = p.tanques.nivelInsumo;
  }

  return serializeJson(doc, buf, n);
}

static TipoActuador parsearActuador(const char* s) {
  if (strcmp(s, ACT_VALVE) == 0) return TipoActuador::VALVULA;
  if (strcmp(s, ACT_PUMP) == 0)  return TipoActuador::BOMBA;
  if (strcmp(s, ACT_SHADE) == 0) return TipoActuador::MEDIASOMBRA;
  return TipoActuador::DESCONOCIDO;
}

static TipoAccion parsearAccion(const char* s) {
  if (strcasecmp(s, ACCION_ON) == 0 || strcasecmp(s, ACCION_OPEN) == 0 ||
      strcasecmp(s, ACCION_INJECT) == 0) {
    return TipoAccion::ENCENDER;
  }
  if (strcasecmp(s, ACCION_OFF) == 0 || strcasecmp(s, ACCION_CLOSE) == 0) {
    return TipoAccion::APAGAR;
  }
  if (strcasecmp(s, ACCION_SET) == 0 || strcasecmp(s, ACCION_MOVE) == 0) {
    return TipoAccion::POSICIONAR;
  }
  return TipoAccion::DESCONOCIDA;
}

bool parsearComando(const char* payload, unsigned int len, Comando& out) {
  JsonDocument doc;
  DeserializationError err = deserializeJson(doc, payload, len);
  if (err) {
    Serial.printf("[json] Comando inválido: %s\n", err.c_str());
    return false;
  }

  // El commandId se copia antes de validar: el ack de error lo necesita.
  const char* cmdId = doc[KEY_COMMAND_ID] | "";
  strlcpy(out.commandId, cmdId, sizeof(out.commandId));

  const char* actuadorStr = doc[KEY_ACTUADOR] | "";
  out.actuador = parsearActuador(actuadorStr);
  if (out.actuador == TipoActuador::DESCONOCIDO) {
    Serial.println("[json] Comando con actuador desconocido");
    return false;
  }

  const char* accionStr = doc[KEY_ACCION] | "";
  out.accion = parsearAccion(accionStr);
  if (out.accion == TipoAccion::DESCONOCIDA) {
    Serial.printf("[json] Comando con acción desconocida: '%s'\n", accionStr);
    return false;
  }

  JsonObject params = doc[KEY_PARAMETROS];
  out.durationSec = params[KEY_DURATION]  | 0L;
  out.ml          = params[KEY_ML]        | 0.0f;
  out.targetPct   = params[KEY_TARGET_PCT] | -1;

  // Prioridad: sólo "INMEDIATA" cuenta; cualquier otro valor es como si no viniera (con log, sin fallar).
  out.prioridadInmediata = false;
  if (!params[KEY_PRIORIDAD].isNull()) {
    const char* prioridad = params[KEY_PRIORIDAD] | "";
    if (strcmp(prioridad, PRIORIDAD_INMEDIATA) == 0) {
      out.prioridadInmediata = true;
    } else {
      Serial.printf("[json] parametros.prioridad '%s' desconocida: se ignora\n", prioridad);
    }
  }
  return true;
}

ResultadoComandoZona parsearComandoZona(const char* payload, unsigned int len, char* commandId, size_t n) {
  if (n > 0) {
    commandId[0] = '\0';
  }
  JsonDocument doc;
  DeserializationError err = deserializeJson(doc, payload, len);
  if (err) {
    Serial.printf("[json] Comando de zona inválido: %s\n", err.c_str());
    return ResultadoComandoZona::INVALIDO;
  }
  if (n > 0) {
    strlcpy(commandId, doc[KEY_COMMAND_ID] | "", n);
  }
  if (!doc[KEY_ACTUADOR].isNull()) {
    return ResultadoComandoZona::ACTUADOR;
  }
  const char* accion = doc[KEY_ACCION] | "";
  if (strcasecmp(accion, ACCION_LEER_AHORA) == 0) {
    return ResultadoComandoZona::LEER_AHORA;
  }
  Serial.printf("[json] Comando de zona con acción desconocida: '%s'\n", accion);
  return ResultadoComandoZona::ACCION_DESCONOCIDA;
}

size_t serializarAck(const Ack& a, char* buf, size_t n) {
  JsonDocument doc;
  if (a.commandId[0] != '\0') {
    doc[KEY_COMMAND_ID] = a.commandId;
  }
  switch (a.status) {
    case EstadoAck::SUCCESS:  doc[KEY_STATUS] = STATUS_SUCCESS;  break;
    case EstadoAck::EN_CURSO: doc[KEY_STATUS] = STATUS_EN_CURSO; break;
    default:                  doc[KEY_STATUS] = STATUS_ERROR;    break;
  }

  JsonObject detalle = doc[KEY_DETALLE].to<JsonObject>();
  if (a.tipoDetalle[0] != '\0') {
    detalle[KEY_TIPO] = a.tipoDetalle;
  }
  if (a.durationSec > 0) detalle[KEY_DURATION]   = a.durationSec;
  if (a.ml > 0)          detalle[KEY_ML]          = a.ml;
  if (a.targetPct >= 0)  detalle[KEY_TARGET_PCT]  = a.targetPct;
  if (a.status == EstadoAck::EN_CURSO && a.posicion > 0) {
    detalle[KEY_POSICION]   = a.posicion;     // sólo en_cola
    detalle[KEY_ESPERA_MAX] = a.esperaMaxSec;
  }

  return serializeJson(doc, buf, n);
}

size_t serializarError(const ErrorNodo& e, const char* nodo, const char* sectorId, uint32_t timestamp, char* buf, size_t n) {
  JsonDocument doc;
  doc[KEY_NODO] = nodo;
  doc[KEY_SECTOR] = sectorId;
  doc[KEY_TIPO] = e.tipo;
  if (e.valorNumerico) {
    doc[KEY_VALOR] = e.caudal;
  } else {
    doc[KEY_VALOR] = e.valor;
  }
  doc[KEY_TIMESTAMP] = timestamp;

  return serializeJson(doc, buf, n);
}

void parsearComandoRiel(const char* payload, unsigned int len, ComandoRiel& out) {
  out.commandId[0] = '\0';
  out.valido = false;
  out.accion = AccionRiel::NINGUNA;
  out.posicion = 0;
  out.detalle[0] = '\0';

  JsonDocument doc;
  DeserializationError err = deserializeJson(doc, payload, len);
  if (err) {
    snprintf(out.detalle, sizeof(out.detalle), "JSON ilegible: %s", err.c_str());
    return;
  }

  const char* id       = doc[KEY_COMMAND_ID] | "";
  const char* actuador = doc[KEY_ACTUADOR] | "";
  const char* accion   = doc[KEY_ACCION] | "";
  int posicion         = doc[KEY_PARAMETROS][KEY_POSICION] | -1;

  size_t largoId = strlen(id);
  if (largoId == 0 || largoId >= sizeof(out.commandId)) {
    strlcpy(out.detalle, "commandId ausente o de mas de 39 caracteres", sizeof(out.detalle));
    return;
  }
  strlcpy(out.commandId, id, sizeof(out.commandId));

  if (strcmp(actuador, ACT_RAIL) != 0) {
    strlcpy(out.detalle, "actuador distinto de rail", sizeof(out.detalle));
  } else if (strcmp(accion, ACCION_RIEL_HOME) == 0) {
    out.accion = AccionRiel::HOME;
    out.valido = true;
  } else if (strcmp(accion, ACCION_RIEL_IR_A) == 0) {
    if (posicion == 1 || posicion == 2) {
      out.accion = AccionRiel::IR_A;
      out.posicion = posicion;
      out.valido = true;
    } else {
      strlcpy(out.detalle, "parametros.posicion tiene que ser 1 o 2", sizeof(out.detalle));
    }
  } else {
    strlcpy(out.detalle, "accion desconocida (se espera IR_A o HOME)", sizeof(out.detalle));
  }
}

size_t serializarEventoRiel(const EventoRiel& e, char* buf, size_t n) {
  JsonDocument doc;
  doc[KEY_COMMAND_ID] = e.commandId;
  doc[KEY_STATUS] = e.status;
  if (e.posicion < 0) {
    doc[KEY_POSICION] = nullptr;
  } else {
    doc[KEY_POSICION] = e.posicion;
  }
  doc[KEY_PASOS] = e.pasos;
  if (e.codigo[0] != '\0') {
    doc[KEY_CODIGO] = e.codigo;
  }
  if (e.detalle[0] != '\0') {
    doc[KEY_DETALLE] = e.detalle;
  }
  return serializeJson(doc, buf, n);
}

}  // namespace util_json
