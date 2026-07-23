#include "task_sensado.h"
#include "config.h"
#include "reloj.h"
#include "net_wifi.h"
#include "sensor_luz.h"
#include "sensor_dht.h"
#include "sensor_suelo.h"

namespace task_sensado {

void iniciarDrivers() {
  sensor_luz::iniciar();
  sensor_dht::iniciar();
  sensor_suelo::iniciar();
  pinMode(PIN_BATERIA, INPUT);
}

// Lee el nivel de batería como porcentaje.
// TODO(hardware): calibrar según el divisor resistivo y la química de la batería.
static int leerBateria() {
  int crudo = analogRead(PIN_BATERIA);
  // ADC de 12 bits (0-4095) mapeado a 0-100% (placeholder hasta calibrar).
  long pct = map(crudo, 0, 4095, 0, 100);
  return (int) constrain(pct, 0, 100);
}

static PaqueteTelemetria armarPaquete() {
  PaqueteTelemetria p;
  net_wifi::obtenerMac(p.mac, sizeof(p.mac));
  p.battery   = leerBateria();
  p.signal    = net_wifi::rssi();
  p.timestamp = reloj::ahoraEpoch();
  p.luz       = sensor_luz::leer();
  p.ambiente  = sensor_dht::leer();
  p.suelo     = sensor_suelo::leer();
  return p;
}

void tarea(void* pvParameters) {
  SistemaColas* sys = (SistemaColas*) pvParameters;

  for (;;) {
    PaqueteTelemetria p = armarPaquete();

    // Entrega a la tarea de red. Si la cola está llena (red lenta), no bloquea:
    // la tarea de red o el buffer offline se encargan del backlog.
    if (xQueueSend(sys->telemetria, &p, 0) != pdTRUE) {
      Serial.println("[sensado] Cola de telemetría llena; se omite este ciclo");
    } else {
      Serial.printf("[sensado] Lectura tomada (ts=%u)\n", p.timestamp);
    }

    // Cede el núcleo hasta el próximo ciclo (ahorro de energía por scheduling).
    vTaskDelay(pdMS_TO_TICKS(SENSOR_POLLING_INTERVAL_MS));
  }
}

}  // namespace task_sensado
