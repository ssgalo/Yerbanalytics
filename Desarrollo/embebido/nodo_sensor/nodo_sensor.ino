// ============================================================================
//  Yerbanalytics - Firmware ESP32 - NODO SENSOR
// ----------------------------------------------------------------------------
//  Nodo testigo (por macro-zona): SOLO sensa y publica telemetría. No escucha
//  comandos ni acciona nada. Firmware independiente y optimizado para el rol:
//  no compila ni el subsistema de actuación ni su callback de comandos.
//
//  Tareas FreeRTOS:
//    - task_red     : WiFi/MQTT, publica telemetría, drena el buffer offline.
//    - task_sensado : muestreo periódico -> cola_telemetria.
//
//  Sin deep sleep: el ahorro de energía se logra cediendo el núcleo con
//  vTaskDelay entre ciclos.
// ============================================================================
#include <Arduino.h>

#include "config.h"
#include "tipos.h"
#include "contrato.h"
#include "net_wifi.h"
#include "net_mqtt.h"
#include "reloj.h"
#include "util_json.h"

#include "buffer_offline.h"
#include "task_sensado.h"

// ----------------------------------------------------------------------------
//  Estado global compartido
// ----------------------------------------------------------------------------
static SistemaColas sys;

static char topicTelemetria[96];

// ----------------------------------------------------------------------------
//  Publicación de telemetría
// ----------------------------------------------------------------------------
static bool publicarTelemetria(const PaqueteTelemetria &p)
{
  char buf[MQTT_BUFFER_SIZE];
  size_t escritos = util_json::serializarTelemetria(p, buf, sizeof(buf));
  if (escritos == 0 || escritos >= sizeof(buf))
  {
    Serial.println("[main] Telemetría no cabe en el buffer");
    return false;
  }
  buf[escritos] = '\0';
  return net_mqtt::publicar(topicTelemetria, buf, QOS_TELEMETRIA);
}

// Drena el buffer offline en orden cronológico, borrando cada registro tras
// publicarlo con éxito (sin duplicados).
static void drenarBufferOffline()
{
  PaqueteTelemetria p;
  while (buffer_offline::verMasAntiguo(p))
  {
    if (publicarTelemetria(p))
    {
      buffer_offline::removerMasAntiguo();
      Serial.printf("[main] Reenviado del buffer (quedan %u)\n", buffer_offline::cantidad());
    }
    else
    {
      break; // sigue sin poder publicar; reintenta en el próximo ciclo
    }
  }
}

// ----------------------------------------------------------------------------
//  task_red: conectividad + publicación + drenaje
// ----------------------------------------------------------------------------
static void taskRed(void *pv)
{
  for (;;)
  {
    net_wifi::reconectarSiNecesario();
    bool mqttOk = net_mqtt::reconectarSiNecesario();
    net_mqtt::loop();
    reloj::sincronizarSiHayRed();

    // Al reconectar, primero drena lo pendiente en flash (orden cronológico).
    if (mqttOk)
    {
      drenarBufferOffline();
    }

    // Telemetría nueva producida por task_sensado.
    PaqueteTelemetria p;
    while (xQueueReceive(sys.telemetria, &p, 0) == pdTRUE)
    {
      if (!(mqttOk && publicarTelemetria(p)))
      {
        buffer_offline::encolar(p); // sin red: persistir con su timestamp original
      }
    }

    vTaskDelay(pdMS_TO_TICKS(100));
  }
}

// ----------------------------------------------------------------------------
//  setup
// ----------------------------------------------------------------------------
void setup()
{
  Serial.begin(115200);
  delay(500);
  Serial.println("\n[main] Yerbanalytics ESP32 - NODO SENSOR");

  pinMode(PIN_LED_ESTADO, OUTPUT);
  digitalWrite(PIN_LED_ESTADO, LOW);

  // Topic de telemetría (según config: zona o sector).
  contratoTopicTelemetria(topicTelemetria, sizeof(topicTelemetria));

  // Recursos FreeRTOS (este nodo solo produce telemetría).
  sys.telemetria = xQueueCreate(10, sizeof(PaqueteTelemetria));
  sys.comandos = nullptr;
  sys.ack = nullptr;

  // Infraestructura común. Sin suscripciones: callback nulo (nunca se invoca).
  net_wifi::iniciar();
  net_mqtt::iniciar(nullptr);
  reloj::iniciar();
  buffer_offline::iniciar();

  // Drivers y tarea de sensado.
  task_sensado::iniciarDrivers();
  xTaskCreate(task_sensado::tarea, "sensado", TASK_STACK_SIZE, &sys, TASK_PRIO_SENSADO, nullptr);
  Serial.println("[main] Tarea de sensado creada");

  // Tarea de red.
  xTaskCreate(taskRed, "red", TASK_STACK_SIZE, &sys, TASK_PRIO_RED, nullptr);
  Serial.println("[main] Tarea de red creada");
}

// ----------------------------------------------------------------------------
//  loop: vacío. Todo el trabajo vive en las tareas FreeRTOS.
// ----------------------------------------------------------------------------
void loop()
{
  vTaskDelay(pdMS_TO_TICKS(1000));
}
