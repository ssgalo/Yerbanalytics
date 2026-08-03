#include "dedup_comandos.h"

namespace dedup_comandos {

// Ring buffer pequeño de los últimos comandos ejecutados.
static const int MAX_RECORDADOS = 10;

struct Registro {
  char commandId[40];
  Ack  ack;
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

bool yaEjecutado(const char* commandId) {
  return buscar(commandId) >= 0;
}

void registrar(const char* commandId, const Ack& ack) {
  if (commandId == nullptr || commandId[0] == '\0') {
    return;   // comandos sin id no se deduplican
  }
  Registro& r = registros[siguiente];
  strncpy(r.commandId, commandId, sizeof(r.commandId) - 1);
  r.commandId[sizeof(r.commandId) - 1] = '\0';
  r.ack = ack;

  siguiente = (siguiente + 1) % MAX_RECORDADOS;
  if (cantidad < MAX_RECORDADOS) {
    cantidad++;
  }
}

bool ackPrevio(const char* commandId, Ack& out) {
  int idx = buscar(commandId);
  if (idx < 0) {
    return false;
  }
  out = registros[idx].ack;
  return true;
}

}  // namespace dedup_comandos
