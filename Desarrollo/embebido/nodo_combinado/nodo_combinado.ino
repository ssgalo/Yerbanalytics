// ============================================================================
//  Yerbanalytics - Firmware ESP32 - NODO COMBINADO (PROTOTIPO)
// ----------------------------------------------------------------------------
//  Un solo ESP32 con sensado Y actuación: el firmware del prototipo. Publica
//  telemetría, escucha comandos y acciona (válvula, bomba, mediasombra).
//
//  Tareas FreeRTOS:
//    - task_red       : WiFi/MQTT, publica telemetría/acks, drena el buffer offline.
//    - task_sensado   : muestreo periódico -> cola_telemetria.
//    - task_actuacion : consume comandos, acciona -> cola_ack.
//
//  El callback MQTT solo encola comandos; la ejecución vive en task_actuacion.
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
#include "task_actuacion.h"

// ----------------------------------------------------------------------------
//  Estado global compartido
// ----------------------------------------------------------------------------
static SistemaColas sys;

static char topicTelemetria[96];
static char topicComando[96];
static char topicAck[96];

// ----------------------------------------------------------------------------
//  Callback MQTT: parsea el comando y lo encola (no ejecuta acá).
// ----------------------------------------------------------------------------
static void onMensajeMqtt(char *topic, byte *payload, unsigned int length)
{
  if (strcmp(topic, topicComando) != 0)
  {
    return;
  }
  Comando cmd = {};
  if (!util_json::parsearComando((const char *)payload, length, cmd))
  {
    // Comando malformado: publicar ack de error inmediato.
    Ack err = {};
    err.status = EstadoAck::ERROR;
    strncpy(err.tipoDetalle, "comando_invalido", sizeof(err.tipoDetalle));
    char buf[256];
    size_t escritos = util_json::serializarAck(err, buf, sizeof(buf));
    buf[escritos] = '\0';
    net_mqtt::publicar(topicAck, buf, QOS_ACK);
    return;
  }
  if (xQueueSend(sys.comandos, &cmd, 0) != pdTRUE)
  {
    Serial.println("[main] Cola de comandos llena; comando descartado");
  }
}

// ----------------------------------------------------------------------------
//  Publicación de telemetría y acks
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

static void publicarAck(const Ack &a)
{
  char buf[256];
  size_t escritos = util_json::serializarAck(a, buf, sizeof(buf));
  buf[escritos] = '\0';
  net_mqtt::publicar(topicAck, buf, QOS_ACK);
  Serial.printf("[main] Ack publicado: %s\n", buf);
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

    // Acks producidos por task_actuacion.
    Ack a;
    while (xQueueReceive(sys.ack, &a, 0) == pdTRUE)
    {
      publicarAck(a);
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
  Serial.println("\n[main] Yerbanalytics ESP32 - NODO COMBINADO (prototipo)");

  pinMode(PIN_LED_ESTADO, OUTPUT);
  digitalWrite(PIN_LED_ESTADO, LOW);

  // Topics: telemetría (zona/sector según config), comando y ack (nivel sector).
  contratoTopicTelemetria(topicTelemetria, sizeof(topicTelemetria));
  contratoTopicComando(topicComando, sizeof(topicComando));
  contratoTopicAck(topicAck, sizeof(topicAck));

  // Recursos FreeRTOS compartidos.
  sys.telemetria = xQueueCreate(10, sizeof(PaqueteTelemetria));
  sys.comandos = xQueueCreate(10, sizeof(Comando));
  sys.ack = xQueueCreate(10, sizeof(Ack));

  // Infraestructura común + suscripción a comandos.
  net_wifi::iniciar();
  net_mqtt::iniciar(onMensajeMqtt);
  reloj::iniciar();
  buffer_offline::iniciar();
  net_mqtt::suscribir(topicComando, QOS_COMANDO);

  // Drivers y tareas de ambos subsistemas.
  task_sensado::iniciarDrivers();
  xTaskCreate(task_sensado::tarea, "sensado", TASK_STACK_SIZE, &sys, TASK_PRIO_SENSADO, nullptr);
  Serial.println("[main] Tarea de sensado creada");

  task_actuacion::iniciarDrivers();
  xTaskCreate(task_actuacion::tarea, "actuacion", TASK_STACK_SIZE, &sys, TASK_PRIO_ACTUACION, nullptr);
  Serial.println("[main] Tarea de actuación creada");

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
