#include "buffer_offline.h"
#include "config.h"
#include <Preferences.h>

// ----------------------------------------------------------------------------
//  Implementación: ring buffer en NVS.
//  - "head" / "tail" / "count" persistidos como enteros.
//  - Cada paquete se guarda como blob binario bajo la clave "p<indice>".
//  El PaqueteTelemetria es POD (sin punteros), así que se persiste tal cual.
// ----------------------------------------------------------------------------
namespace buffer_offline {

static Preferences prefs;
static const char* NVS_NS       = "buf_offline";
static const char* KEY_HEAD     = "head";   // índice del más antiguo
static const char* KEY_TAIL     = "tail";   // próximo índice libre
static const char* KEY_COUNT    = "count";

static uint16_t head  = 0;
static uint16_t tail  = 0;
static uint16_t count = 0;

static void clavePaquete(char* buf, size_t n, uint16_t idx) {
  snprintf(buf, n, "p%u", idx);
}

static void persistirIndices() {
  prefs.putUShort(KEY_HEAD, head);
  prefs.putUShort(KEY_TAIL, tail);
  prefs.putUShort(KEY_COUNT, count);
}

void iniciar() {
  prefs.begin(NVS_NS, false);
  head  = prefs.getUShort(KEY_HEAD, 0);
  tail  = prefs.getUShort(KEY_TAIL, 0);
  count = prefs.getUShort(KEY_COUNT, 0);
  Serial.printf("[buffer] Iniciado. Pendientes: %u\n", count);
}

bool encolar(const PaqueteTelemetria& p) {
  // Si está lleno, descarta el más antiguo (FIFO).
  if (count >= BUFFER_OFFLINE_CAPACIDAD) {
    Serial.println("[buffer] Lleno: se descarta el registro más antiguo");
    head = (head + 1) % BUFFER_OFFLINE_CAPACIDAD;
    count--;
  }

  char clave[8];
  clavePaquete(clave, sizeof(clave), tail);
  prefs.putBytes(clave, &p, sizeof(PaqueteTelemetria));

  tail = (tail + 1) % BUFFER_OFFLINE_CAPACIDAD;
  count++;
  persistirIndices();
  Serial.printf("[buffer] Encolado. Pendientes: %u\n", count);
  return true;
}

bool verMasAntiguo(PaqueteTelemetria& out) {
  if (count == 0) {
    return false;
  }
  char clave[8];
  clavePaquete(clave, sizeof(clave), head);
  size_t leidos = prefs.getBytes(clave, &out, sizeof(PaqueteTelemetria));
  return leidos == sizeof(PaqueteTelemetria);
}

void removerMasAntiguo() {
  if (count == 0) {
    return;
  }
  char clave[8];
  clavePaquete(clave, sizeof(clave), head);
  prefs.remove(clave);

  head = (head + 1) % BUFFER_OFFLINE_CAPACIDAD;
  count--;
  persistirIndices();
}

bool vacio() { return count == 0; }
uint16_t cantidad() { return count; }

}  // namespace buffer_offline
