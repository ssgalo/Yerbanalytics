// ============================================================================
//  Yerbanalytics - Firmware ESP32 - NODO COMBINADO (PROTOTIPO)
// ----------------------------------------------------------------------------
//  Un solo ESP32-S3 con sensado Y actuación: el firmware del prototipo. Publica
//  telemetría, atiende los comandos de los dos sectores (riego, insumo,
//  mediasombra) y mueve el riel de la cámara.
//
//  Tareas FreeRTOS:
//    - task_red       : WiFi/MQTT, publica telemetría/acks/eventos del riel, drena el buffer offline.
//    - task_sensado   : muestreo periódico -> cola_telemetria.
//    - task_actuacion : consume comandos, acciona -> cola_ack.
//    - riel           : consume comandos del riel, mueve el carro -> cola_evento_riel.
//
//  El callback MQTT solo encola (comandos, comando del riel, ack de comando
//  inválido): nunca acciona ni publica (PubSubClient reusa su buffer al publicar).
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
#include "act_riel.h"

// ----------------------------------------------------------------------------
//  Estado global compartido
// ----------------------------------------------------------------------------
static SistemaColas sys;

static char topicTelemetria[96];
static char topicComando[CANT_SECTORES + 1][96];   // índice 1 y 2
static char topicAck[CANT_SECTORES + 1][96];

// ----------------------------------------------------------------------------
//  Callback MQTT: parsea y encola (no ejecuta ni publica acá).
// ----------------------------------------------------------------------------
static void onMensajeMqtt(char *topic, byte *payload, unsigned int length)
{
  if (strcmp(topic, TOPIC_RIEL_COMANDO) == 0)
  {
    ComandoRiel cr;
    util_json::parsearComandoRiel((const char *)payload, length, cr);
    xQueueOverwrite(sys.comandoRiel, &cr); // el último gana
    return;
  }

  int sector = 0;
  for (int s = 1; s <= CANT_SECTORES; s++)
  {
    if (strcmp(topic, topicComando[s]) == 0)
    {
      sector = s;
    }
  }
  if (sector == 0)
  {
    return;
  }

  Comando cmd = {};
  cmd.sector = sector;
  if (!util_json::parsearComando((const char *)payload, length, cmd))
  {
    // Comando malformado: ack de error (lo publica task_red).
    Ack err = {};
    strlcpy(err.commandId, cmd.commandId, sizeof(err.commandId));
    err.sector = sector;
    err.status = EstadoAck::ERROR;
    err.targetPct = -1;
    strlcpy(err.tipoDetalle, "comando_invalido", sizeof(err.tipoDetalle));
    xQueueSend(sys.ack, &err, 0);
    return;
  }
  if (xQueueSend(sys.comandos, &cmd, 0) != pdTRUE)
  {
    Serial.println("[main] Cola de comandos llena; comando descartado");
  }
}

// ----------------------------------------------------------------------------
//  Publicación de telemetría, acks y eventos del riel
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
  int sector = (a.sector >= 1 && a.sector <= CANT_SECTORES) ? a.sector : 1;
  net_mqtt::publicar(topicAck[sector], buf, QOS_ACK);
  Serial.printf("[main] Ack sector %d publicado: %s\n", sector, buf);
}

// Publica los eventos del riel en orden. Uno que no se pudo publicar queda
// primero en la cola y se reintenta en la próxima vuelta (p.ej. al reconectar).
static void publicarEventosRiel(bool mqttOk)
{
  EventoRiel e;
  while (mqttOk && xQueuePeek(sys.eventoRiel, &e, 0) == pdTRUE)
  {
    char buf[320];
    size_t escritos = util_json::serializarEventoRiel(e, buf, sizeof(buf));
    buf[escritos] = '\0';
    if (!net_mqtt::publicar(TOPIC_RIEL_EVENTO, buf, QOS_RIEL_EVENTO))
    {
      break;
    }
    xQueueReceive(sys.eventoRiel, &e, 0);
    Serial.printf("[main] Evento del riel publicado: %s\n", buf);
  }
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

    // Acks producidos por task_actuacion (y por el callback, si el comando era inválido).
    Ack a;
    while (xQueueReceive(sys.ack, &a, 0) == pdTRUE)
    {
      publicarAck(a);
    }

    // Eventos del riel.
    publicarEventosRiel(mqttOk);

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

  // Topics: telemetría (zona/sector según config); comando y ack de cada sector.
  contratoTopicTelemetria(topicTelemetria, sizeof(topicTelemetria));
  for (int s = 1; s <= CANT_SECTORES; s++)
  {
    contratoTopicComando(topicComando[s], sizeof(topicComando[s]), s);
    contratoTopicAck(topicAck[s], sizeof(topicAck[s]), s);
  }

  // Recursos FreeRTOS compartidos.
  sys.telemetria = xQueueCreate(10, sizeof(PaqueteTelemetria));
  sys.comandos = xQueueCreate(10, sizeof(Comando));
  sys.ack = xQueueCreate(10, sizeof(Ack));
  sys.comandoRiel = xQueueCreate(1, sizeof(ComandoRiel));
  sys.eventoRiel = xQueueCreate(8, sizeof(EventoRiel));

  // Infraestructura común + suscripciones.
  net_wifi::iniciar();
  net_mqtt::iniciar(onMensajeMqtt);
  reloj::iniciar();
  buffer_offline::iniciar();
  for (int s = 1; s <= CANT_SECTORES; s++)
  {
    net_mqtt::suscribir(topicComando[s], QOS_COMANDO);
  }
  net_mqtt::suscribir(TOPIC_RIEL_COMANDO, QOS_RIEL_COMANDO);

  // Drivers y tareas de ambos subsistemas.
  task_sensado::iniciarDrivers();
  xTaskCreate(task_sensado::tarea, "sensado", TASK_STACK_SIZE, &sys, TASK_PRIO_SENSADO, nullptr);
  Serial.println("[main] Tarea de sensado creada");

  task_actuacion::iniciarDrivers();
  xTaskCreate(task_actuacion::tarea, "actuacion", TASK_STACK_SIZE, &sys, TASK_PRIO_ACTUACION, nullptr);
  Serial.println("[main] Tarea de actuación creada");

  // Riel: en el núcleo de la aplicación (el 0 queda para el WiFi).
  xTaskCreatePinnedToCore(act_riel::tarea, "riel", TASK_STACK_SIZE, &sys, TASK_PRIO_RIEL, nullptr, 1);
  Serial.println("[main] Tarea del riel creada");

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
