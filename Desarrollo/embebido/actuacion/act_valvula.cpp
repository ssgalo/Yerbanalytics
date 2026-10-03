#include "act_valvula.h"
#include "config.h"
#include "contrato.h"

#ifndef CAUDALIMETRO_INSTALADO
#error "Falta CAUDALIMETRO_INSTALADO en config.h: copiá el bloque de config.example.h (0 = sin caudalímetro)"
#endif

// El límite local es la última barrera, pero no puede quedar por debajo de lo que el
// contrato permite pedir: recortaría los riegos largos en silencio. Un config.h viejo
// (120 s) falla acá al compilar.
static_assert(LIMITE_VALVULA_SEG_MAX >= CONTRATO_VALVULA_DURACION_MAX_SEG,
              "LIMITE_VALVULA_SEG_MAX (config.h) debe ser >= CONTRATO_VALVULA_DURACION_MAX_SEG (contrato.h)");

namespace act_valvula {

static volatile uint32_t pulsosCaudal = 0;

// ISR del caudalímetro: cuenta pulsos para confirmar que hay flujo.
static void IRAM_ATTR onPulsoCaudal() {
  pulsosCaudal++;
}

void iniciar() {
  pinMode(PIN_RELE_VALVULA, OUTPUT);
  digitalWrite(PIN_RELE_VALVULA, LOW);   // cerrada (estado seguro)
  pinMode(PIN_CAUDALIMETRO, INPUT_PULLUP);
  attachInterrupt(digitalPinToInterrupt(PIN_CAUDALIMETRO), onPulsoCaudal, FALLING);
}

void ejecutar(const Comando& cmd, Ack& ack) {
  strncpy(ack.commandId, cmd.commandId, sizeof(ack.commandId));

  // Límite de seguridad local: recorta la duración al máximo permitido.
  long duracion = cmd.durationSec;
  if (duracion > LIMITE_VALVULA_SEG_MAX) {
    Serial.printf("[valvula] Duración %lds excede el límite; se recorta a %ds\n",
                  duracion, LIMITE_VALVULA_SEG_MAX);
    duracion = LIMITE_VALVULA_SEG_MAX;
  }
  if (duracion <= 0) {
    ack.status = EstadoAck::ERROR;
    strncpy(ack.tipoDetalle, "duracion_invalida", sizeof(ack.tipoDetalle));
    return;
  }

  // Abrir válvula.
  pulsosCaudal = 0;
  digitalWrite(PIN_RELE_VALVULA, HIGH);
  Serial.printf("[valvula] Abierta por %lds\n", duracion);

  uint32_t inicio = millis();

#if CAUDALIMETRO_INSTALADO
  // Verificar que haya flujo dentro del timeout (detección de falla hidráulica).
  bool hayFlujo = false;
  while (millis() - inicio < (uint32_t) TIMEOUT_CAUDAL_MS) {
    if (pulsosCaudal > 0) { hayFlujo = true; break; }
    vTaskDelay(pdMS_TO_TICKS(100));
  }
  if (!hayFlujo) {
    digitalWrite(PIN_RELE_VALVULA, LOW);   // cierre inmediato
    Serial.println("[valvula] Sin flujo: FALLA HIDRÁULICA");
    ack.status = EstadoAck::ERROR;
    strncpy(ack.tipoDetalle, "falla_hidraulica", sizeof(ack.tipoDetalle));
    return;
  }
#endif

  // Mantener abierta el resto de la duración.
  long restanteMs = duracion * 1000L - (long)(millis() - inicio);
  if (restanteMs > 0) {
    vTaskDelay(pdMS_TO_TICKS(restanteMs));
  }

  // Cerrar (estado seguro).
  digitalWrite(PIN_RELE_VALVULA, LOW);
  Serial.println("[valvula] Cerrada");

  ack.status      = EstadoAck::SUCCESS;
  ack.durationSec = duracion;
  strncpy(ack.tipoDetalle, "ok", sizeof(ack.tipoDetalle));
}

}  // namespace act_valvula
