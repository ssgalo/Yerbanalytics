// ============================================================================
//  contrato.h  -  Contrato MQTT del nodo (fuente única de topics y claves JSON)
// ----------------------------------------------------------------------------
//  Alineado con add-mqtt-telemetry y add-rules-engine. Comando y ack a nivel de
//  sector (el nodo atiende dos sectores); la telemetría usa el topic de zona, que
//  es el que escucha el backend. Más el riel (nursery/rail/*), al final.
//
//    Ingesta   : nursery/zone/{zona}/sector/{sector}/telemetry   (o zona)
//    Comando   : nursery/zone/{zona}/sector/{sector}/command     (QoS 1)
//    Ack       : nursery/zone/{zona}/sector/{sector}/ack
//    Zona      : nursery/zone/{zona}/command                     (LEER_AHORA, QoS 1)
//    Errores   : nursery/zone/{zona}/errores                     (nodo → backend, QoS 1)
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
// Suscripción a comandos: QoS 1. PubSubClient rechaza suscribirse con QoS 2 (devuelve
// false y el nodo quedaba sin recibir comandos), y el backend los publica con QoS 1
// (MqttConfig). La doble ejecución la evitan el dedup por commandId y el lock del backend.
static const uint8_t QOS_COMANDO     = 1;
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
//  Nivel de los tanques (% , HC-SR04). Siempre se envían si la lectura es válida:
//  el backend ignora las claves que no modela (MetricsPayload, ignoreUnknown).
static const char* KEY_NIVEL_AGUA   = "nivelAgua";
static const char* KEY_NIVEL_INSUMO = "nivelInsumo";

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

//  Valores del campo "accion" (sin distinguir mayúsculas). El backend
//  (ComandoActuadorPublisher) manda ON / OFF / SET; se aceptan también los
//  nombres históricos de este contrato.
//    valve ON {durationSec}  → riega el sector: abre su válvula y prende la bomba de riego
//    valve OFF               → corta el riego del sector
//    pump  ON {} | {ml}      → insumo al sector: abre su válvula y prende la bomba de insumo
//                              (sin ml: INSUMO_SEG_POR_DEFECTO; con ml: BOMBA_INSUMO_MS_POR_ML)
//    pump  OFF               → corta el insumo del sector
//    shade SET {targetPct}   → % de APERTURA: >= MEDIASOMBRA_UMBRAL_APERTURA_PCT enrolla, si no desenrolla.
//                              La mediasombra es de la MACRO-ZONA (una por zona, add-rustificacion-por-macrozona):
//                              el backend manda el shade SET por el comando de ZONA (nursery/zone/{zona}/command,
//                              con "actuador":"shade") y el nodo contesta por el ack de zona (nursery/zone/{zona}/ack).
//                              Un shade SET por el tópico de un sector se sigue aceptando (backend anterior).
static const char* ACCION_ON      = "ON";
static const char* ACCION_OFF     = "OFF";
static const char* ACCION_SET     = "SET";
static const char* ACCION_OPEN    = "open";     // histórico = ON (valve)
static const char* ACCION_INJECT  = "inject";   // histórico = ON (pump)
static const char* ACCION_CLOSE   = "close";    // histórico = OFF
static const char* ACCION_MOVE    = "move";     // histórico = SET (shade)

// Duración máxima de apertura de la válvula que el backend puede pedir en "durationSec" (s).
// Fuente de verdad del valor: este archivo. Espejos que deben decir lo mismo:
//   - backend:   ContratoNodo.DURACION_VALVULA_MAX_SEG
//   - simulador: simulador/server/contract.ts (VALVE_MAX_DURATION_SEC)
// El límite local LIMITE_VALVULA_SEG_MAX (config.h) debe ser >= a este valor
// (act_riego.cpp lo verifica al compilar).
#define CONTRATO_VALVULA_DURACION_MAX_SEG 1200

// ----------------------------------------------------------------------------
//  Claves JSON - Ack
// ----------------------------------------------------------------------------
static const char* KEY_STATUS     = "status";
static const char* KEY_DETALLE    = "detalle";
static const char* KEY_TIPO       = "tipo";
static const char* STATUS_SUCCESS = "SUCCESS";
static const char* STATUS_ERROR   = "ERROR";
// Intermedio: no cierra el comando (ver "Ack de actuador" abajo). Espejos: ContratoNodo.ACK_EN_CURSO,
// simulador/server/contract.ts (ACK_STATUSES).
static const char* STATUS_EN_CURSO = "EN_CURSO";

//  Ack de actuador (add-secuencias-demo-expo, design §1.2; ACK intermedios: add-cola-actuadores-nodo)
//    Topic: nursery/zone/{zona}/sector/{sector}/ack. Por cada comando de CARGA (valve/pump ON, shade SET) el nodo
//    publica, en orden y con el mismo commandId:
//        [EN_CURSO en_cola] -> EN_CURSO iniciado -> ack FINAL (SUCCESS | ERROR)
//    El nodo corre a lo sumo MAX_CARGAS_SIMULTANEAS cargas a la vez (la fuente no banca más): el resto espera en una
//    cola en RAM, en orden de llegada. Un comando que se descarta de la cola no tiene 'iniciado': pasa directo a su final.
//    El ACK FINAL se publica al TERMINAR de cumplirlo y es el único que cierra el comando:
//      valve/pump ON   -> al final de la duración (SUCCESS ok), o ERROR cancelado si llega un OFF
//                         (o ERROR reemplazado si llega otro ON para lo mismo).
//      valve/pump OFF  -> ack inmediato (SUCCESS ok), haya o no riego en curso. Sólo el final.
//      shade SET       -> al tocar el final de carrera (SUCCESS ok) o al rendirse (ERROR falla_mecanica).
//      comando inválido -> ERROR inmediato con el tipo que corresponda. Sólo el final.
//      cola llena      -> ERROR cola_llena inmediato (COLA_CARGAS_MAX comandos esperando).
//    ACK INTERMEDIO (status EN_CURSO, detalle.tipo):
//      en_cola   el comando no pudo arrancar al llegar. posicion (1 = el próximo) y esperaMaxSec: cota superior
//                de la espera (lo que le queda a lo que corre + la duración de lo que tiene adelante).
//      iniciado  la carga arrancó de verdad (al llegar o al salir de la cola); lleva la duración efectiva
//                (durationSec, ml o targetPct), ya recortada a los límites locales.
//    PREEMPCIÓN: se decide al llegar el comando.
//      inválido                       ERROR inmediato; no ocupa lugar.
//      valve OFF / pump OFF           actúa YA: corta el tramo en curso de ese sector y actuador y descarta lo
//                                     encolado de lo mismo (cada uno recibe ERROR cancelado). Su ack: SUCCESS ok.
//      shade SET ya en la posición    SUCCESS sin_cambio inmediato (si no hay movimiento en curso).
//      shade SET prioridad INMEDIATA  arranca YA aunque el límite esté lleno y descarta el SET encolado
//                                     (ERROR reemplazado). Lo manda sólo ActuatorShutdown (estado seguro).
//      valve ON / pump ON / shade SET arrancan si hay lugar; si no, a la cola. Si el mismo sector y actuador (o la
//                                     malla) ya corre: lo reemplaza y arranca (ERROR reemplazado al viejo); si
//                                     está encolado: toma su lugar en la fila (ERROR reemplazado al viejo).
//    parametros.prioridad: "INMEDIATA" | ausente. Sólo se honra en shade SET; en valve/pump ON se ignora y
//    cualquier otro valor se trata como ausente (un valor raro nunca debe hacer fallar un cierre de seguridad).
//    Un firmware viejo ignora la clave; un backend viejo no entiende EN_CURSO (desplegar el backend primero).
//    Un commandId encolado cuenta como "en curso" para el dedup: su reentrega se ignora.
//    El ESP32 de vivero_esp32_red publica QoS 0 (PubSubClient); el backend suscribe QoS 1. Sin retain.
//      {"commandId":"<uuid>","status":"SUCCESS","detalle":{"tipo":"ok","durationSec":20}}
//      {"commandId":"<uuid>","status":"ERROR","detalle":{"tipo":"falla_mecanica"}}
//      {"commandId":"<uuid>","status":"EN_CURSO","detalle":{"tipo":"en_cola","posicion":2,"esperaMaxSec":45}}
//      {"commandId":"<uuid>","status":"EN_CURSO","detalle":{"tipo":"iniciado","durationSec":20}}
//      {"commandId":"<uuid>","status":"EN_CURSO","detalle":{"tipo":"iniciado","durationSec":10,"ml":10}}
//      {"commandId":"<uuid>","status":"EN_CURSO","detalle":{"tipo":"iniciado","targetPct":0}}
//    "detalle" es un OBJETO (en el evento del riel es un string).
//    Valores de detalle.tipo (unión de lo que emite el firmware modular y el sketch de la expo):
//      ok                    SUCCESS  comando cumplido
//      sin_cambio            SUCCESS  el actuador ya estaba en el estado pedido
//      limite_excedido       SUCCESS  se cumplió recortado al límite local (duración o volumen)
//      comando_invalido      ERROR    JSON ilegible, accion desconocida o targetPct fuera de {0,100}
//      accion_invalida       ERROR    la acción no aplica al actuador
//      sector_invalido       ERROR    el sector del tópico no es uno de los que atiende el nodo
//      parametros_invalidos  ERROR    parámetros ausentes o fuera de rango (p. ej. targetPct)
//      duracion_invalida     ERROR    durationSec fuera de 1..CONTRATO_VALVULA_DURACION_MAX_SEG
//      actuador_desconocido  ERROR    actuador que este nodo no tiene
//      falla_mecanica        ERROR    la mediasombra no tocó el final de carrera a tiempo
//      reemplazado           ERROR    otro commandId interrumpió; éste se abortó (no es una falla)
//      cancelado             ERROR    un OFF cortó el riego en curso o descartó el encolado (no es una falla)
//      cola_llena            ERROR    la cola de cargas del nodo no tiene lugar (add-cola-actuadores-nodo)
//      en_cola               EN_CURSO la carga espera su turno (intermedio)
//      iniciado              EN_CURSO la carga arrancó (intermedio)
static const char* DETALLE_OK                   = "ok";
static const char* DETALLE_SIN_CAMBIO           = "sin_cambio";
static const char* DETALLE_LIMITE_EXCEDIDO      = "limite_excedido";
static const char* DETALLE_COMANDO_INVALIDO     = "comando_invalido";
static const char* DETALLE_ACCION_INVALIDA      = "accion_invalida";
static const char* DETALLE_SECTOR_INVALIDO      = "sector_invalido";
static const char* DETALLE_PARAMETROS_INVALIDOS = "parametros_invalidos";
static const char* DETALLE_DURACION_INVALIDA    = "duracion_invalida";
static const char* DETALLE_ACTUADOR_DESCONOCIDO = "actuador_desconocido";
static const char* DETALLE_FALLA_MECANICA       = "falla_mecanica";
static const char* DETALLE_REEMPLAZADO          = "reemplazado";
static const char* DETALLE_CANCELADO            = "cancelado";
static const char* DETALLE_COLA_LLENA           = "cola_llena";
static const char* DETALLE_EN_COLA              = "en_cola";
static const char* DETALLE_INICIADO             = "iniciado";

// Claves del ACK intermedio y de la prioridad. "posicion" (KEY_POSICION, más abajo, en la sección del riel) se
// reutiliza: en el ACK es el lugar en la cola (1 = el próximo).
static const char* KEY_ESPERA_MAX      = "esperaMaxSec";   // en detalle de en_cola
static const char* KEY_PRIORIDAD       = "prioridad";      // en parametros de shade SET
static const char* PRIORIDAD_INMEDIATA = "INMEDIATA";

// ----------------------------------------------------------------------------
//  Errores del nodo (add-fallas-esp32, S-05 de reglas_v2)
// ----------------------------------------------------------------------------
//  Topic nursery/zone/{zona}/errores · nodo → backend. El ESP32 detecta la falla, CORTA el
//  actuador por su cuenta y la publica UNA vez por evento (no repite hasta que se resuelve).
//  QoS 1, sin retain. El backend marca el dispositivo como averiado y lo saca de lo autónomo
//  hasta que alguien registre la reparación.
//    {"nodo":"A4:CF:12:9A:00:01","sector":"MZ-1-012","tipo":"SIN_CAUDAL","valor":0,"timestamp":1760000000}
//      nodo       serial/MAC del nodo que informa
//      sector     id del sector; VACÍO ("") en MEDIASOMBRA_FIN_DE_CARRERA (la malla es de la macro-zona)
//      tipo       uno de los ERROR_* de abajo
//      valor      caudal medido en L/h (número) para los de caudal; "CERRADA" | "ABIERTA" (texto)
//                 para el final de carrera que falló en la malla
//      timestamp  mismo criterio que la telemetría (segundos epoch; el backend lo normaliza)
//  ERROR_MEDIASOMBRA_FIN_DE_CARRERA es el mismo hecho que el ack "falla_mecanica": el backend
//  los deduplica y deja una sola alerta.
//  Caudalímetro: el hardware no está instalado en el vivero; el firmware de producción sólo
//  publica los de caudal detrás de ENVIAR_ERRORES_CAUDAL (config.h). Espejos:
//    - backend:   mqtt/ContratoNodo.java (TOPIC_ERRORS*) y mqtt/NodeErrorType.java
//    - simulador: simulador/server/contract.ts (errorsTopic, NODE_ERROR_TYPES)
static const char* KEY_NODO   = "nodo";
static const char* KEY_SECTOR = "sector";
static const char* KEY_VALOR  = "valor";
// KEY_TIPO ("tipo") y KEY_TIMESTAMP se comparten con el ack y la telemetría.
static const char* ERROR_SIN_CAUDAL                 = "SIN_CAUDAL";
static const char* ERROR_CAUDAL_EXCESIVO            = "CAUDAL_EXCESIVO";
static const char* ERROR_MEDIASOMBRA_FIN_DE_CARRERA = "MEDIASOMBRA_FIN_DE_CARRERA";
// Valores de "valor" en ERROR_MEDIASOMBRA_FIN_DE_CARRERA: qué final de carrera falló.
static const char* VALOR_FIN_CERRADA = "CERRADA";   // la malla no llegó a cerrar: quedó abierta
static const char* VALOR_FIN_ABIERTA = "ABIERTA";   // la malla no llegó a abrir: quedó cerrada
static const uint8_t QOS_ERROR = 1;

// ----------------------------------------------------------------------------
//  Comando de zona "leer ahora" (add-secuencias-demo-expo, design §1.3)
// ----------------------------------------------------------------------------
//  Topic nursery/zone/{zona}/command · backend → nodo testigo de la zona (el sensado es por
//  macro-zona). Backend publica QoS 1, el nodo suscribe QoS 1. Sin retain.
//    {"commandId":"<uuid>","accion":"LEER_AHORA","parametros":{}}
//  Sin "actuador": no es un actuador. commandId va igual (log del nodo y dedupe).
//  Respuesta: la telemetría de siempre por nursery/zone/{zona}/telemetry, SIN commandId y SIN ack.
//  Un nodo que no puede leer no publica nada; el backend lo ve como timeout.
//  Correlación: el backend toma la PRIMERA telemetría de esa zona recibida después del pedido,
//  por hora de recepción (no por el "timestamp" del payload: sin NTP son segundos desde el arranque).
//  Espejos que deben decir lo mismo:
//    - backend:   mqtt/ContratoNodo.java (TOPIC_COMANDO_ZONA, ACCION_LEER_AHORA)
//    - simulador: simulador/server/contract.ts (zoneCommandTopic, READ_NOW_ACTION; sin comportamiento)
//    - firmware:  prototipo_hardware/vivero_esp32_red/vivero_esp32_red.ino (ZONA_*)
static const char* ACCION_LEER_AHORA = "LEER_AHORA";
static const uint8_t QOS_COMANDO_ZONA = 1;

// ----------------------------------------------------------------------------
//  Riel de la cámara (add-pasada-riel, design.md §1)
// ----------------------------------------------------------------------------
//  Un solo riel, sin id en el tópico. Lo implementan actuacion/act_riel (firmware
//  modular: nodo_combinado y nodo_actuador) y el sketch prototipo_hardware/vivero_esp32_red
//  (que NO incluye este archivo: lo espeja con #define locales). Espejos que deben decir lo mismo:
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
//  El nodo atiende dos sectores (1 y 2): sus ids reales están en config.h.

static const int CANT_SECTORES = 2;

// Id real del sector 1 o 2 (NODO_SECTOR_1_ID / NODO_SECTOR_2_ID).
inline const char* contratoSectorId(int sector) {
  return (sector == 2) ? NODO_SECTOR_2_ID : NODO_SECTOR_1_ID;
}

// Telemetría: nivel sector (del sector 1) o zona según TELEMETRIA_NIVEL_SECTOR.
inline void contratoTopicTelemetria(char* buf, size_t n) {
#if TELEMETRIA_NIVEL_SECTOR
  snprintf(buf, n, "nursery/zone/%s/sector/%s/telemetry", NODO_ZONA_ID, NODO_SECTOR_1_ID);
#else
  snprintf(buf, n, "nursery/zone/%s/telemetry", NODO_ZONA_ID);
#endif
}

// Comando del sector 1 o 2: el nodo se suscribe a los dos.
inline void contratoTopicComando(char* buf, size_t n, int sector) {
  snprintf(buf, n, "nursery/zone/%s/sector/%s/command", NODO_ZONA_ID, contratoSectorId(sector));
}

// Ack del sector 1 o 2: el nodo publica el resultado en el sector del comando.
inline void contratoTopicAck(char* buf, size_t n, int sector) {
  snprintf(buf, n, "nursery/zone/%s/sector/%s/ack", NODO_ZONA_ID, contratoSectorId(sector));
}

// Errores del nodo (add-fallas-esp32): el nodo publica en el topic de su zona.
inline void contratoTopicErrores(char* buf, size_t n) {
  snprintf(buf, n, "nursery/zone/%s/errores", NODO_ZONA_ID);
}

// Comando de zona: "leer ahora" (nodo testigo) y el shade SET de la mediasombra, que es de la macro-zona.
inline void contratoTopicComandoZona(char* buf, size_t n) {
  snprintf(buf, n, "nursery/zone/%s/command", NODO_ZONA_ID);
}

// Ack de un comando de zona con actuador (la mediasombra): mismo payload que el ack del sector.
// Espejos: ContratoNodo.TOPIC_ACK_ZONA (backend), simulador/server/contract.ts (zoneAckTopic).
inline void contratoTopicAckZona(char* buf, size_t n) {
  snprintf(buf, n, "nursery/zone/%s/ack", NODO_ZONA_ID);
}

// Sector de un Comando/Ack que es de la zona y no de un sector (la mediasombra): su ack va por contratoTopicAckZona.
#define CONTRATO_SECTOR_ZONA 0

#endif  // CONTRATO_H
