#include "act_mediasombra.h"
#include "config.h"

namespace act_mediasombra {

// Posición actual estimada (% de apertura). Persistiría en NVS en producción;
// para el prototipo se mantiene en memoria.
static int posicionActualPct = 0;

static void detenerMotor() {
  digitalWrite(PIN_MOTOR_MEDIASOMBRA_A, LOW);
  digitalWrite(PIN_MOTOR_MEDIASOMBRA_B, LOW);
}

void iniciar() {
  pinMode(PIN_MOTOR_MEDIASOMBRA_A, OUTPUT);
  pinMode(PIN_MOTOR_MEDIASOMBRA_B, OUTPUT);
  pinMode(PIN_FIN_CARRERA, INPUT_PULLUP);
  pinMode(PIN_SENSOR_CORRIENTE, INPUT);
  detenerMotor();   // estado seguro
}

void ejecutar(const Comando& cmd, Ack& ack) {
  strncpy(ack.commandId, cmd.commandId, sizeof(ack.commandId));

  int objetivo = constrain(cmd.targetPct, 0, 100);
  Serial.printf("[mediasombra] Moviendo de %d%% a %d%%\n", posicionActualPct, objetivo);

  if (objetivo == posicionActualPct) {
    ack.status    = EstadoAck::SUCCESS;
    ack.targetPct = objetivo;
    strncpy(ack.tipoDetalle, "sin_cambio", sizeof(ack.tipoDetalle));
    return;
  }

  // Dirección según abrir (A) o cerrar (B).
  bool abriendo = objetivo > posicionActualPct;
  digitalWrite(PIN_MOTOR_MEDIASOMBRA_A, abriendo ? HIGH : LOW);
  digitalWrite(PIN_MOTOR_MEDIASOMBRA_B, abriendo ? LOW  : HIGH);

  uint32_t inicio = millis();
  bool finCarrera = false;
  bool atasco     = false;

  // Mueve hasta el fin de carrera, controlando sobrecorriente y timeout.
  while (millis() - inicio < (uint32_t) TIMEOUT_FIN_CARRERA_MS) {
    if (digitalRead(PIN_FIN_CARRERA) == LOW) { finCarrera = true; break; }

    int corriente = analogRead(PIN_SENSOR_CORRIENTE);
    if (corriente > LIMITE_CORRIENTE_MOTOR_MAX) {
      atasco = true;
      break;
    }
    vTaskDelay(pdMS_TO_TICKS(50));
  }

  detenerMotor();   // siempre cortar energía al salir del lazo

  if (atasco) {
    Serial.println("[mediasombra] Sobrecorriente: ATASCO");
    ack.status = EstadoAck::ERROR;
    strncpy(ack.tipoDetalle, "falla_mecanica", sizeof(ack.tipoDetalle));
    return;
  }
  if (!finCarrera) {
    Serial.println("[mediasombra] Sin fin de carrera: timeout");
    ack.status = EstadoAck::ERROR;
    strncpy(ack.tipoDetalle, "falla_mecanica", sizeof(ack.tipoDetalle));
    return;
  }

  posicionActualPct = objetivo;
  ack.status    = EstadoAck::SUCCESS;
  ack.targetPct = objetivo;
  strncpy(ack.tipoDetalle, "ok", sizeof(ack.tipoDetalle));
}

}  // namespace act_mediasombra
