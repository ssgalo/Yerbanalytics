// ============================================================================
//  Yerbanalytics - Firmware ESP32 - NODO ACTUADOR
// ----------------------------------------------------------------------------
//  Nodo de actuación (por sector): SOLO escucha comandos y acciona (válvula,
//  bomba, mediasombra), publicando un ACK por cada uno. No sensa ni publica
//  telemetría. Firmware independiente y optimizado: no compila el subsistema
//  de sensado ni el buffer offline.
//
//  Tareas FreeRTOS:
//    - task_red       : WiFi/MQTT, publica los acks producidos.
//    - task_actuacion : consume comandos, acciona -> cola_ack.
//
//  El callback MQTT solo encola comandos; la ejecución vive en task_actuacion
//  (nunca se acciona en el contexto del callback).
//  Sin deep sleep: el nodo debe permanecer despierto para atender comandos.
// ============================================================================
#include <Arduino.h>

#include "config.h"
#include "tipos.h"
#include "contrato.h"
#include "net_wifi.h"
#include "net_mqtt.h"
#include "util_json.h"

#include "task_actuacion.h"

// ----------------------------------------------------------------------------
//  Estado global compartido
// ----------------------------------------------------------------------------
static SistemaColas sys;

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
//  Publicación de acks
// ----------------------------------------------------------------------------
static void publicarAck(const Ack &a)
{
  char buf[256];
  size_t escritos = util_json::serializarAck(a, buf, sizeof(buf));
  buf[escritos] = '\0';
  net_mqtt::publicar(topicAck, buf, QOS_ACK);
  Serial.printf("[main] Ack publicado: %s\n", buf);
}

// ----------------------------------------------------------------------------
//  task_red: conectividad + publicación de acks
// ----------------------------------------------------------------------------
static void taskRed(void *pv)
{
  for (;;)
  {
    net_wifi::reconectarSiNecesario();
    net_mqtt::reconectarSiNecesario();
    net_mqtt::loop();

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
  Serial.println("\n[main] Yerbanalytics ESP32 - NODO ACTUADOR");

  pinMode(PIN_LED_ESTADO, OUTPUT);
  digitalWrite(PIN_LED_ESTADO, LOW);

  // Topics de comando (suscripción) y ack (publicación), a nivel sector.
  contratoTopicComando(topicComando, sizeof(topicComando));
  contratoTopicAck(topicAck, sizeof(topicAck));

  // Recursos FreeRTOS (este nodo consume comandos y produce acks).
  sys.telemetria = nullptr;
  sys.comandos = xQueueCreate(10, sizeof(Comando));
  sys.ack = xQueueCreate(10, sizeof(Ack));

  // Infraestructura común + suscripción a comandos.
  net_wifi::iniciar();
  net_mqtt::iniciar(onMensajeMqtt);
  net_mqtt::suscribir(topicComando, QOS_COMANDO);

  // Drivers y tarea de actuación.
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
