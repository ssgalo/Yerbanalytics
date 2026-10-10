#include "sensor_tanques.h"
#include "config.h"

namespace sensor_tanques {

static const float VELOCIDAD_SONIDO_CM_US = 0.0343f;   // a 20 °C
static const uint32_t PAUSA_ENTRE_SENSORES_MS = 60;    // deja apagar el eco del primero

// Dispara el TRIG compartido y mide el ECHO indicado. Devuelve cm, o -1 sin eco.
static float medirCm(int pinEcho) {
  digitalWrite(PIN_HCSR04_TRIG, LOW);
  delayMicroseconds(2);
  digitalWrite(PIN_HCSR04_TRIG, HIGH);
  delayMicroseconds(10);
  digitalWrite(PIN_HCSR04_TRIG, LOW);

  unsigned long duracion = pulseIn(pinEcho, HIGH, HCSR04_TIMEOUT_US);
  if (duracion == 0) {
    return -1.0f;
  }
  return (duracion * VELOCIDAD_SONIDO_CM_US) / 2.0f;   // ida y vuelta
}

// Distancia → % de llenado (lleno = distancia chica), limitado a 0-100.
static float aPorcentaje(float cm, float distLleno, float distVacio) {
  float pct = (distVacio - cm) * 100.0f / (distVacio - distLleno);
  return constrain(pct, 0.0f, 100.0f);
}

void iniciar() {
  pinMode(PIN_HCSR04_TRIG, OUTPUT);
  digitalWrite(PIN_HCSR04_TRIG, LOW);
  pinMode(PIN_HCSR04_ECHO_1, INPUT);
  pinMode(PIN_HCSR04_ECHO_2, INPUT);
}

LecturaTanques leer() {
  LecturaTanques r;

  float cmAgua = medirCm(PIN_HCSR04_ECHO_1);
  vTaskDelay(pdMS_TO_TICKS(PAUSA_ENTRE_SENSORES_MS));
  float cmInsumo = medirCm(PIN_HCSR04_ECHO_2);

  if (cmAgua < 0) {
    Serial.println("[tanques] Agua: sin eco");
    r.nivelAgua = 0;
    r.estadoAgua = EstadoLectura::ERROR;
  } else {
    r.nivelAgua = aPorcentaje(cmAgua, TANQUE_AGUA_DIST_LLENO_CM, TANQUE_AGUA_DIST_VACIO_CM);
    r.estadoAgua = EstadoLectura::OK;
  }

  if (cmInsumo < 0) {
    Serial.println("[tanques] Insumo: sin eco");
    r.nivelInsumo = 0;
    r.estadoInsumo = EstadoLectura::ERROR;
  } else {
    r.nivelInsumo = aPorcentaje(cmInsumo, TANQUE_INSUMO_DIST_LLENO_CM, TANQUE_INSUMO_DIST_VACIO_CM);
    r.estadoInsumo = EstadoLectura::OK;
  }

  Serial.printf("[tanques] Agua %.1f cm (%.0f%%) · Insumo %.1f cm (%.0f%%)\n",
                cmAgua, r.nivelAgua, cmInsumo, r.nivelInsumo);
  return r;
}

}  // namespace sensor_tanques
