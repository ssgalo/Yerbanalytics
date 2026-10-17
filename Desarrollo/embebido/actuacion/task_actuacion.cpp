#include "task_actuacion.h"
#include "config.h"
#include "act_riego.h"
#include "act_mediasombra.h"
#include "act_riel.h"
#include "contrato.h"
#include "dedup_comandos.h"
#include "cola_cargas.h"

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
  // Espera corta y acotada: con la red caída la cola de ack se llena y esta tarea no puede quedar bloqueada (frenaría
  // los fines de riego y el ciclo de 50 ms). Un final perdido lo cubre el plazo del seguimiento del backend.
  if (xQueueSend(sys->ack, &ack, pdMS_TO_TICKS(20)) != pdTRUE) {
    Serial.println("[actuacion] No se pudo encolar el ack final (cola llena)");
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

// ----------------------------------------------------------------------------
//  Cola de cargas (add-cola-actuadores-nodo): el nodo corre a lo sumo MAX_CARGAS_SIMULTANEAS cargas a la vez.
//  La decisión (arranca / espera / reemplaza / descarta) es lógica pura en cola_cargas; acá se la conecta con los
//  drivers y los ACK. Los comandos que esperan viven en 'enEspera', indexados por el casillero que da la cola.
// ----------------------------------------------------------------------------
static Comando enEspera[cola_cargas::COLA_CARGAS_MAX];

static int tipoDeCarga(const Comando& cmd) {
  switch (cmd.actuador) {
    case TipoActuador::VALVULA: return cola_cargas::TIPO_RIEGO;
    case TipoActuador::BOMBA:   return cola_cargas::TIPO_INSUMO;
    default:                    return cola_cargas::TIPO_MEDIASOMBRA;
  }
}

// Para la cola: ¿el driver todavía tiene activa esta carga?
static bool sigueActiva(int sector, int tipo) {
  if (tipo == cola_cargas::TIPO_MEDIASOMBRA) {
    return act_mediasombra::moviendoseAhora();
  }
  return act_riego::activo(sector, tipo);
}

static void ackBase(Ack& a, const Comando& cmd, EstadoAck status, const char* detalle) {
  a = {};
  strlcpy(a.commandId, cmd.commandId, sizeof(a.commandId));
  a.sector = cmd.sector;
  a.status = status;
  a.targetPct = -1;
  strlcpy(a.tipoDetalle, detalle, sizeof(a.tipoDetalle));
}

// Un ACK intermedio sale por la misma cola que los finales, pero NO entra al dedup como "terminado".
static void entregarIntermedio(SistemaColas* sys, const Ack& ack) {
  // Sin espera: un intermedio es informativo y nunca debe frenar la tarea de actuación (se descarta si no hay lugar).
  if (xQueueSend(sys->ack, &ack, 0) != pdTRUE) {
    Serial.println("[actuacion] Cola de ack llena: se descarta el ack intermedio");
  }
}

// Decide si el comando es una carga que necesita lugar (ON válido / SET que mueve la malla) y cuánto durará (para la
// espera de lo que viene detrás). Todo lo demás (inválido, OFF, sin_cambio) tiene respuesta inmediata y no ocupa lugar.
static bool esCarga(const Comando& cmd, uint32_t& duracionMs) {
  switch (cmd.actuador) {
    case TipoActuador::VALVULA:
    case TipoActuador::BOMBA: {
      float ml;
      return act_riego::evaluar(cmd, duracionMs, ml) == nullptr;
    }
    case TipoActuador::MEDIASOMBRA:
      if (cmd.accion != TipoAccion::POSICIONAR || cmd.targetPct < 0 || act_mediasombra::yaEnPosicion(cmd)) {
        return false;
      }
      duracionMs = (uint32_t) TIMEOUT_MEDIASOMBRA_MS;
      return true;
    default:
      return false;
  }
}

// Arranca una carga que ya tiene su lugar: llama al driver y avisa 'iniciado'. Si el driver responde de una vez
// (p. ej. la malla ya llegó a la posición mientras esperaba), sólo sale el final.
static void arrancar(SistemaColas* sys, const Comando& cmd) {
  Ack ack;
  if (despachar(cmd, ack)) {
    entregar(sys, ack);
    return;
  }
  ackBase(ack, cmd, EstadoAck::EN_CURSO, DETALLE_INICIADO);
  if (cmd.actuador == TipoActuador::MEDIASOMBRA) {
    ack.targetPct = (cmd.targetPct >= MEDIASOMBRA_UMBRAL_APERTURA_PCT) ? 100 : 0;
  } else {
    uint32_t duracionMs = 0;
    float ml = 0;
    act_riego::evaluar(cmd, duracionMs, ml);
    ack.durationSec = (long) ((duracionMs + 999UL) / 1000UL);
    ack.ml = ml;
  }
  entregarIntermedio(sys, ack);
}

// ACK final de un comando que salió de la cola sin correr (cancelado por un OFF, o reemplazado por otro).
static void descartar(SistemaColas* sys, const cola_cargas::Descartado& d, const char* motivo) {
  Comando cmd = {};
  strlcpy(cmd.commandId, d.commandId, sizeof(cmd.commandId));
  cmd.sector = d.sector;
  Ack ack;
  ackBase(ack, cmd, EstadoAck::ERROR, motivo);
  Serial.printf("[actuacion] commandId '%s' sale de la cola sin correr (%s)\n", d.commandId, motivo);
  entregar(sys, ack);
}

// Llega un comando nuevo (ya filtrado por el dedup).
static void alLlegar(SistemaColas* sys, const Comando& cmd) {
  Ack ack;
  const bool esMalla = (cmd.actuador == TipoActuador::MEDIASOMBRA);

  if (cmd.prioridadInmediata && !esMalla) {
    Serial.println("[actuacion] parametros.prioridad se ignora fuera de shade SET");
  }

  // valve OFF / pump OFF: actúan YA y sacan de la cola lo del mismo sector y actuador.
  if (!esMalla && cmd.accion == TipoAccion::APAGAR && cmd.actuador != TipoActuador::DESCONOCIDO) {
    const cola_cargas::Cancelacion x = cola_cargas::cancelar(cmd.sector, tipoDeCarga(cmd));
    for (int i = 0; i < x.cantDescartados; i++) {
      descartar(sys, x.descartados[i], DETALLE_CANCELADO);
    }
    despachar(cmd, ack);   // corta el tramo en curso (su 'cancelado' sale por atender()) y responde SUCCESS ok
    entregar(sys, ack);
    return;
  }

  uint32_t duracionMs = 0;
  if (!esCarga(cmd, duracionMs)) {
    // Inválido o sin_cambio: respuesta inmediata, sin lugar en la cola.
    despachar(cmd, ack);
    if (esMalla && ack.status == EstadoAck::SUCCESS) {
      // La malla ya está donde se pide: un SET que esperaba con otra posición ya no corresponde.
      const cola_cargas::Cancelacion x = cola_cargas::cancelar(cmd.sector, cola_cargas::TIPO_MEDIASOMBRA);
      for (int i = 0; i < x.cantDescartados; i++) {
        descartar(sys, x.descartados[i], DETALLE_REEMPLAZADO);
      }
    }
    entregar(sys, ack);
    return;
  }

  cola_cargas::Carga carga = {};
  strlcpy(carga.commandId, cmd.commandId, sizeof(carga.commandId));
  carga.sector = cmd.sector;
  carga.tipo = tipoDeCarga(cmd);
  carga.duracionMs = duracionMs;

  const cola_cargas::Llegada l = cola_cargas::llegar(carga, esMalla && cmd.prioridadInmediata, millis());
  for (int i = 0; i < l.cantDescartados; i++) {
    descartar(sys, l.descartados[i], l.descartados[i].motivo);
  }

  switch (l.resultado) {
    case cola_cargas::Resultado::ARRANCA:
      arrancar(sys, cmd);
      break;
    case cola_cargas::Resultado::EN_COLA:
      enEspera[l.slot] = cmd;
      ackBase(ack, cmd, EstadoAck::EN_CURSO, DETALLE_EN_COLA);
      ack.posicion = l.posicion;
      ack.esperaMaxSec = (long) ((l.esperaMaxMs + 999UL) / 1000UL);
      Serial.printf("[actuacion] commandId '%s' en cola (posición %d, espera máx %lds)\n", cmd.commandId, ack.posicion,
                    ack.esperaMaxSec);
      entregarIntermedio(sys, ack);
      break;
    case cola_cargas::Resultado::COLA_LLENA:
      Serial.printf("[actuacion] commandId '%s': cola de cargas llena\n", cmd.commandId);
      ackBase(ack, cmd, EstadoAck::ERROR, DETALLE_COLA_LLENA);
      entregar(sys, ack);
      break;
  }
}

// Suelta los lugares de lo que ya terminó y arranca lo que sigue en la fila, en orden de llegada.
static void avanzarCola(SistemaColas* sys) {
  cola_cargas::Carga carga;
  for (;;) {
    cola_cargas::liberarTerminadas(sigueActiva, millis());
    if (!cola_cargas::proximo(millis(), carga)) {
      break;
    }
    arrancar(sys, enEspera[carga.slot]);
  }
}

void tarea(void* pvParameters) {
  SistemaColas* sys = (SistemaColas*) pvParameters;
  Comando cmd;
  Ack ack;

  cola_cargas::reiniciar();

  for (;;) {
    // Espera un comando hasta CICLO_MS (cede el núcleo mientras tanto).
    if (xQueueReceive(sys->comandos, &cmd, pdMS_TO_TICKS(CICLO_MS)) == pdTRUE) {
      // Defensa en profundidad: un commandId ya recibido no se vuelve a ejecutar.
      // Un comando encolado cuenta como "en curso": su reentrega no lo encola dos veces.
      if (dedup_comandos::yaRecibido(cmd.commandId)) {
        if (dedup_comandos::ackPrevio(cmd.commandId, ack)) {
          Serial.printf("[actuacion] commandId '%s' duplicado; se reenvía el ack previo\n", cmd.commandId);
          if (xQueueSend(sys->ack, &ack, 0) != pdTRUE) {
            Serial.println("[actuacion] No se pudo encolar el ack");
          }
        } else {
          Serial.printf("[actuacion] commandId '%s' repetido (en curso o en cola): lo ignoro\n", cmd.commandId);
        }
      } else {
        dedup_comandos::registrarEnCurso(cmd.commandId);
        alLlegar(sys, cmd);
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

    // Con lugar libre, arranca lo que esperaba.
    avanzarCola(sys);
  }
}

}  // namespace task_actuacion
