#include "task_actuacion.h"
#include "config.h"
#include "act_riego.h"
#include "act_mediasombra.h"
#include "act_riel.h"
#include "contrato.h"
#include "dedup_comandos.h"

namespace task_actuacion {

// Cada cuánto se atienden los actuadores (fin de riego, final de carrera de la mediasombra).
static const uint32_t CICLO_MS = 50;

void iniciarDrivers() {
  act_riego::iniciar();
  act_mediasombra::iniciar();
  act_riel::iniciar();
}

// Registra el ack final y lo entrega a la tarea de red.
static void entregar(SistemaColas* sys, const Ack& ack) {
  dedup_comandos::registrar(ack.commandId, ack);
  if (xQueueSend(sys->ack, &ack, pdMS_TO_TICKS(1000)) != pdTRUE) {
    Serial.println("[actuacion] No se pudo encolar el ack");
  }
}

// Encola un error del nodo para que la tarea de red lo publique (S-05). Sin cola (nodo sensor) no hay a quién avisar.
static void informarError(SistemaColas* sys, const ErrorNodo& error) {
  if (sys->error == nullptr) {
    return;
  }
  if (xQueueSend(sys->error, &error, pdMS_TO_TICKS(1000)) != pdTRUE) {
    Serial.println("[actuacion] No se pudo encolar el error");
  }
}

// La mediasombra se rindió: si es una falla del final de carrera que todavía no se informó, la publica como error del
// nodo (es el mismo hecho que el ack falla_mecanica; el backend los deduplica). Sin sector: la malla es de la macro-zona.
static void informarFallaDeLaMalla(SistemaColas* sys) {
  char valor[16];
  if (!act_mediasombra::tomarFallaPendiente(valor, sizeof(valor))) {
    return;
  }
  const act_mediasombra::EstadoFinesDeCarrera fines = act_mediasombra::estadoFines();
  Serial.printf("[actuacion] Falla de la mediasombra: no tocó el final de '%s' (cerrada=%d, abierta=%d)\n",
                valor, fines.cerrada, fines.abierta);

  ErrorNodo error = {};
  strlcpy(error.tipo, ERROR_MEDIASOMBRA_FIN_DE_CARRERA, sizeof(error.tipo));
  error.sector = 0;
  error.valorNumerico = false;
  strlcpy(error.valor, valor, sizeof(error.valor));
  informarError(sys, error);
}

#if ENVIAR_ERRORES_CAUDAL
void informarErrorCaudal(SistemaColas* sys, int sector, bool excesivo, float caudalLph) {
  ErrorNodo error = {};
  strlcpy(error.tipo, excesivo ? ERROR_CAUDAL_EXCESIVO : ERROR_SIN_CAUDAL, sizeof(error.tipo));
  error.sector = sector;
  error.valorNumerico = true;
  error.caudal = caudalLph;
  informarError(sys, error);
}
#endif

// Despacha el comando. true si 'ack' ya tiene la respuesta; false si quedó en curso.
static bool despachar(const Comando& cmd, Ack& ack) {
  switch (cmd.actuador) {
    case TipoActuador::VALVULA:
    case TipoActuador::BOMBA:
      return act_riego::comenzar(cmd, ack);
    case TipoActuador::MEDIASOMBRA:
      return act_mediasombra::comenzar(cmd, ack);
    default:
      ack = {};
      strlcpy(ack.commandId, cmd.commandId, sizeof(ack.commandId));
      ack.sector = cmd.sector;
      ack.status = EstadoAck::ERROR;
      ack.targetPct = -1;
      strlcpy(ack.tipoDetalle, "actuador_desconocido", sizeof(ack.tipoDetalle));
      return true;
  }
}

void tarea(void* pvParameters) {
  SistemaColas* sys = (SistemaColas*) pvParameters;
  Comando cmd;
  Ack ack;

  for (;;) {
    // Espera un comando hasta CICLO_MS (cede el núcleo mientras tanto).
    if (xQueueReceive(sys->comandos, &cmd, pdMS_TO_TICKS(CICLO_MS)) == pdTRUE) {
      // Defensa en profundidad: un commandId ya recibido no se vuelve a ejecutar.
      if (dedup_comandos::yaRecibido(cmd.commandId)) {
        if (dedup_comandos::ackPrevio(cmd.commandId, ack)) {
          Serial.printf("[actuacion] commandId '%s' duplicado; se reenvía el ack previo\n", cmd.commandId);
          if (xQueueSend(sys->ack, &ack, pdMS_TO_TICKS(1000)) != pdTRUE) {
            Serial.println("[actuacion] No se pudo encolar el ack");
          }
        } else {
          Serial.printf("[actuacion] commandId '%s' repetido (en curso): lo ignoro\n", cmd.commandId);
        }
      } else {
        dedup_comandos::registrarEnCurso(cmd.commandId);
        if (despachar(cmd, ack)) {
          entregar(sys, ack);
        }
      }
    }

    // Lo que terminó (o fue reemplazado) desde la última vuelta.
    while (act_riego::atender(ack)) {
      entregar(sys, ack);
    }
    while (act_mediasombra::atender(ack)) {
      entregar(sys, ack);
    }
    informarFallaDeLaMalla(sys);
  }
}

}  // namespace task_actuacion
