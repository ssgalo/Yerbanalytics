#include "act_bomba.h"
#include "config.h"

namespace act_bomba {

void iniciar() {
  pinMode(PIN_BOMBA, OUTPUT);
  digitalWrite(PIN_BOMBA, LOW);   // apagada (estado seguro)
}

void ejecutar(const Comando& cmd, Ack& ack) {
  strncpy(ack.commandId, cmd.commandId, sizeof(ack.commandId));

  float ml = cmd.ml;
  bool limiteExcedido = false;

  // Límite de seguridad local: recorta la dosis al máximo permitido.
  if (ml > LIMITE_BOMBA_ML_MAX) {
    Serial.printf("[bomba] Dosis %.1fml excede el límite; se recorta a %dml\n",
                  ml, LIMITE_BOMBA_ML_MAX);
    ml = LIMITE_BOMBA_ML_MAX;
    limiteExcedido = true;
  }
  if (ml <= 0) {
    ack.status = EstadoAck::ERROR;
    strncpy(ack.tipoDetalle, "dosis_invalida", sizeof(ack.tipoDetalle));
    return;
  }

  // Convertir volumen a tiempo de bombeo (calibración en config.h).
  uint32_t tiempoMs = (uint32_t)(ml * BOMBA_MS_POR_ML);
  Serial.printf("[bomba] Inyectando %.1fml (%ums)\n", ml, tiempoMs);

  digitalWrite(PIN_BOMBA, HIGH);
  vTaskDelay(pdMS_TO_TICKS(tiempoMs));
  digitalWrite(PIN_BOMBA, LOW);   // estado seguro

  ack.status = EstadoAck::SUCCESS;
  ack.ml     = ml;
  strncpy(ack.tipoDetalle, limiteExcedido ? "limite_excedido" : "ok", sizeof(ack.tipoDetalle));
}

}  // namespace act_bomba
