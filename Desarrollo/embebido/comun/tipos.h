// ============================================================================
//  tipos.h  -  Modelo de datos del firmware (structs y enums del dominio)
// ----------------------------------------------------------------------------
//  Tipado explícito para todo el sistema: lecturas de sensores, paquete de
//  telemetría, comandos y acks, más el contenedor de recursos FreeRTOS que se
//  comparte entre tareas.
// ============================================================================
#ifndef TIPOS_H
#define TIPOS_H

#include <Arduino.h>
#include <freertos/FreeRTOS.h>
#include <freertos/queue.h>

// ----------------------------------------------------------------------------
//  Estado de validez de una lectura
// ----------------------------------------------------------------------------
enum class EstadoLectura { OK, ERROR };

// ----------------------------------------------------------------------------
//  Lecturas de sensores (cada driver reporta su propio struct + validez)
// ----------------------------------------------------------------------------
struct LecturaLuz {
  float valor;              // valor que alimenta el campo "uv" (hoy: % de luz del LDR)
  EstadoLectura estado;
};

struct LecturaAmbiente {
  float humedad;            // %RH
  float temperatura;        // °C (aire)
  EstadoLectura estado;
};

struct LecturaSuelo {
  float humedad;            // %RH  -> humSus
  float temperatura;        // °C   -> tempSuelo
  float ec;                 // µS/cm -> ce
  float ph;                 // pH
  float nitrogeno;          // mg/kg
  float fosforo;            // mg/kg
  float potasio;            // mg/kg
  float salinidad;          // mg/L
  float tds;                // mg/L
  EstadoLectura estado;
};

// ----------------------------------------------------------------------------
//  Paquete de telemetría (lo que se serializa y publica)
// ----------------------------------------------------------------------------
struct PaqueteTelemetria {
  char  mac[18];            // "AA:BB:CC:DD:EE:FF"
  int   battery;            // %
  int   signal;             // dBm (RSSI)
  uint32_t timestamp;       // epoch en segundos (hora real de la lectura)
  LecturaLuz     luz;
  LecturaAmbiente ambiente;
  LecturaSuelo   suelo;
};

// ----------------------------------------------------------------------------
//  Comando de actuación (recibido del backend)
// ----------------------------------------------------------------------------
enum class TipoActuador { VALVULA, BOMBA, MEDIASOMBRA, DESCONOCIDO };

struct Comando {
  char  commandId[40];      // id para deduplicación (opcional; "" si no vino)
  TipoActuador actuador;
  char  accion[16];         // open/close/inject/move
  long  durationSec;        // parámetro riego
  float ml;                 // parámetro dosificación
  int   targetPct;          // parámetro mediasombra
};

// ----------------------------------------------------------------------------
//  Ack (resultado de un comando, publicado por el nodo)
// ----------------------------------------------------------------------------
enum class EstadoAck { SUCCESS, ERROR };

struct Ack {
  char  commandId[40];
  EstadoAck status;
  char  tipoDetalle[24];    // p.ej. "falla_hidraulica", "ok", "limite_excedido"
  long  durationSec;        // datos de cierre (según actuador)
  float ml;
  int   targetPct;
};

// ----------------------------------------------------------------------------
//  Colas de comunicación entre tareas
// ----------------------------------------------------------------------------
struct SistemaColas {
  QueueHandle_t telemetria;   // PaqueteTelemetria  (sensado -> red)
  QueueHandle_t comandos;     // Comando            (callback -> actuación)
  QueueHandle_t ack;          // Ack                (actuación -> red)
};

#endif  // TIPOS_H
