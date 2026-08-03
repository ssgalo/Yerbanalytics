#include "task_actuacion.h"
#include "config.h"
#include "act_valvula.h"
#include "act_bomba.h"
#include "act_mediasombra.h"
#include "dedup_comandos.h"

namespace task_actuacion {

void iniciarDrivers() {
  act_valvula::iniciar();
  act_bomba::iniciar();
  act_mediasombra::iniciar();
}

static Ack ackVacio(const Comando& cmd) {
  Ack a = {};
  strncpy(a.commandId, cmd.commandId, sizeof(a.commandId));
  a.status = EstadoAck::ERROR;
  return a;
}

static void ejecutarComando(const Comando& cmd, Ack& ack) {
  switch (cmd.actuador) {
    case TipoActuador::VALVULA:
      act_valvula::ejecutar(cmd, ack);
      break;
    case TipoActuador::BOMBA:
      act_bomba::ejecutar(cmd, ack);
      break;
    case TipoActuador::MEDIASOMBRA:
      act_mediasombra::ejecutar(cmd, ack);
      break;
    default:
      ack.status = EstadoAck::ERROR;
      strncpy(ack.tipoDetalle, "actuador_desconocido", sizeof(ack.tipoDetalle));
      break;
  }
}

void tarea(void* pvParameters) {
  SistemaColas* sys = (SistemaColas*) pvParameters;
  Comando cmd;

  for (;;) {
    // Bloquea hasta que llegue un comando (cede el núcleo mientras espera).
    if (xQueueReceive(sys->comandos, &cmd, portMAX_DELAY) != pdTRUE) {
      continue;
    }

    Ack ack = ackVacio(cmd);

    // Defensa en profundidad: si ya se ejecutó este commandId, reenviar su ack.
    if (dedup_comandos::yaEjecutado(cmd.commandId)) {
      Serial.printf("[actuacion] commandId '%s' duplicado; se reenvía el ack previo\n",
                    cmd.commandId);
      if (!dedup_comandos::ackPrevio(cmd.commandId, ack)) {
        continue;
      }
    } else {
      ejecutarComando(cmd, ack);
      dedup_comandos::registrar(cmd.commandId, ack);
    }

    // Entregar el ack a la tarea de red para su publicación.
    if (xQueueSend(sys->ack, &ack, pdMS_TO_TICKS(1000)) != pdTRUE) {
      Serial.println("[actuacion] No se pudo encolar el ack");
    }
  }
}

}  // namespace task_actuacion
