#include "sensor_dht.h"
#include "config.h"
#include <DHT.h>

namespace sensor_dht {

static DHT dht(PIN_DHT, DHT11);

void iniciar() {
  dht.begin();
}

LecturaAmbiente leer() {
  LecturaAmbiente r;
  r.humedad     = dht.readHumidity();
  r.temperatura = dht.readTemperature();  // °C

  if (isnan(r.humedad) || isnan(r.temperatura)) {
    Serial.println("[dht] Error de lectura (NaN)");
    r.estado = EstadoLectura::ERROR;
  } else {
    r.estado = EstadoLectura::OK;
  }
  return r;
}

}  // namespace sensor_dht
