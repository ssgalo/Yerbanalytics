#include "sensor_luz.h"
#include "config.h"

namespace sensor_luz {

void iniciar() {
  pinMode(PIN_LDR_AO, INPUT);
}

LecturaLuz leer() {
  LecturaLuz r;
  int crudo = analogRead(PIN_LDR_AO);

  // Mapeo LDR: valor crudo (oscuridad->brillante) a 0-100%.
  long pct = map(crudo, LUZ_ADC_OSCURIDAD, LUZ_ADC_BRILLANTE, 0, 100);
  pct = constrain(pct, 0, 100);

  r.valor  = (float) pct;
  r.estado = EstadoLectura::OK;   // el ADC siempre devuelve algo; no hay "sin respuesta"
  return r;
}

}  // namespace sensor_luz
