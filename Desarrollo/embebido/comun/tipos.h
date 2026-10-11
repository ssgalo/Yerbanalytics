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

struct LecturaTanques {
  float nivelAgua;          // % (0 vacío - 100 lleno)
  float nivelInsumo;        // %
  EstadoLectura estadoAgua;     // cada HC-SR04 falla por separado
  EstadoLectura estadoInsumo;
};

// ----------------------------------------------------------------------------
//  Paquete de telemetría (lo que se serializa y publica)
// ----------------------------------------------------------------------------
//  POD: el buffer offline lo persiste tal cual en NVS.
struct PaqueteTelemetria {
  char  mac[18];            // "AA:BB:CC:DD:EE:FF"
  int   battery;            // % (sólo se publica con BATERIA_INSTALADA)
  int   signal;             // dBm (RSSI)
  uint32_t timestamp;       // epoch en segundos (hora real de la lectura)
  LecturaLuz     luz;
  LecturaAmbiente ambiente;
  LecturaSuelo   suelo;
  LecturaTanques tanques;
};

// ----------------------------------------------------------------------------
//  Comando de actuación (recibido del backend)
// ----------------------------------------------------------------------------
enum class TipoActuador { VALVULA, BOMBA, MEDIASOMBRA, DESCONOCIDO };

// Qué pide el campo "accion" (normalizado: ON/open/inject, OFF/close, SET/move).
enum class TipoAccion { ENCENDER, APAGAR, POSICIONAR, DESCONOCIDA };

struct Comando {
  char  commandId[40];      // id para deduplicación (opcional; "" si no vino)
  int   sector;             // 1 o 2: según el tópico por el que llegó
  TipoActuador actuador;
  TipoAccion   accion;
  long  durationSec;        // parámetro riego (0 si no vino)
  float ml;                 // parámetro dosificación (0 si no vino)
  int   targetPct;          // parámetro mediasombra (-1 si no vino)
};

// ----------------------------------------------------------------------------
//  Ack (resultado de un comando, publicado por el nodo)
// ----------------------------------------------------------------------------
enum class EstadoAck { SUCCESS, ERROR };

struct Ack {
  char  commandId[40];
  int   sector;             // 1 o 2: el ack se publica en el tópico de este sector
  EstadoAck status;
  char  tipoDetalle[24];    // p.ej. "ok", "limite_excedido", "falla_mecanica", "reemplazado"
  long  durationSec;        // datos de cierre (según actuador)
  float ml;
  int   targetPct;
};

// ----------------------------------------------------------------------------
//  Riel de la cámara (contrato.h, sección "Riel")
// ----------------------------------------------------------------------------
enum class AccionRiel { NINGUNA, IR_A, HOME };

// Comando del riel ya validado por el callback MQTT. Si no es válido, 'detalle'
// dice por qué (se responde ERROR COMANDO_INVALIDO).
struct ComandoRiel {
  char  commandId[40];      // "" si no vino o es demasiado largo
  bool  valido;
  AccionRiel accion;
  int   posicion;           // 1 o 2 (sólo IR_A)
  char  detalle[96];
};

// Evento del riel a publicar en nursery/rail/event.
struct EventoRiel {
  char  commandId[40];
  char  status[12];         // ACEPTADO / LLEGO / ERROR
  int   posicion;           // 0 home, 1, 2, o -1 = null
  long  pasos;
  char  codigo[24];         // "" si no es ERROR
  char  detalle[96];        // "" si no es ERROR
};

// ----------------------------------------------------------------------------
//  Colas de comunicación entre tareas
// ----------------------------------------------------------------------------
struct SistemaColas {
  QueueHandle_t telemetria;   // PaqueteTelemetria  (sensado -> red)
  QueueHandle_t comandos;     // Comando            (callback -> actuación)
  QueueHandle_t ack;          // Ack                (actuación/callback -> red)
  QueueHandle_t comandoRiel;  // ComandoRiel, largo 1 (callback -> riel; xQueueOverwrite: el último gana)
  QueueHandle_t eventoRiel;   // EventoRiel         (riel -> red)
};

#endif  // TIPOS_H
