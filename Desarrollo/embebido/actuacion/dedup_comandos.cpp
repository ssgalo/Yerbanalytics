#include "dedup_comandos.h"

namespace dedup_comandos {

// Ring buffer pequeño de los últimos comandos recibidos.
static const int MAX_RECORDADOS = 10;

struct Registro {
  char commandId[40];
  bool terminado;
  Ack  ack;   // válido sólo si terminado
};

static Registro registros[MAX_RECORDADOS];
static int siguiente = 0;   // próxima posición de escritura (circular)
static int cantidad  = 0;

static int buscar(const char* commandId) {
  if (commandId == nullptr || commandId[0] == '\0') {
    return -1;
  }
  for (int i = 0; i < cantidad; i++) {
    if (strcmp(registros[i].commandId, commandId) == 0) {
      return i;
    }
  }
  return -1;
}

static Registro* agregar(const char* commandId) {
  Registro& r = registros[siguiente];
  strlcpy(r.commandId, commandId, sizeof(r.commandId));
  r.terminado = false;

  siguiente = (siguiente + 1) % MAX_RECORDADOS;
  if (cantidad < MAX_RECORDADOS) {
    cantidad++;
  }
  return &r;
}

bool yaRecibido(const char* commandId) {
  return buscar(commandId) >= 0;
}

void registrarEnCurso(const char* commandId) {
  if (commandId == nullptr || commandId[0] == '\0' || buscar(commandId) >= 0) {
    return;   // comandos sin id no se deduplican
  }
  agregar(commandId);
}

void registrar(const char* commandId, const Ack& ack) {
  if (commandId == nullptr || commandId[0] == '\0') {
    return;
  }
  int idx = buscar(commandId);
  Registro* r = (idx >= 0) ? &registros[idx] : agregar(commandId);
  r->terminado = true;
  r->ack = ack;
}

bool ackPrevio(const char* commandId, Ack& out) {
  int idx = buscar(commandId);
  if (idx < 0 || !registros[idx].terminado) {
    return false;
  }
  out = registros[idx].ack;
  return true;
}

}  // namespace dedup_comandos
