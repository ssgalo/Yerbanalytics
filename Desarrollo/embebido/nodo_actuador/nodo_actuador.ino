// ============================================================================
//  Yerbanalytics - Firmware ESP32 - NODO ACTUADOR
// ----------------------------------------------------------------------------
//  Nodo de actuación: SOLO escucha comandos y acciona (riego e insumo de los
//  dos sectores, mediasombra y riel de la cámara), publicando un ACK por cada
//  comando y los eventos del riel. No sensa ni publica telemetría. Firmware
//  independiente y optimizado: no compila el subsistema de sensado ni el
//  buffer offline.
//
//  Tareas FreeRTOS:
//    - task_red       : WiFi/MQTT, publica los acks, los eventos del riel y los errores del nodo (S-05).
//    - task_actuacion : consume comandos, acciona -> cola_ack.
//    - riel           : consume comandos del riel, mueve el carro -> cola_evento_riel.
//
//  El callback MQTT solo encola: nunca acciona ni publica (PubSubClient reusa
//  su buffer al publicar).
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
#include "act_riel.h"

// ----------------------------------------------------------------------------
//  Estado global compartido
// ----------------------------------------------------------------------------
static SistemaColas sys;

static char topicComando[CANT_SECTORES + 1][96];   // índice 1 y 2
static char topicAck[CANT_SECTORES + 1][96];
static char topicComandoZona[96];   // la mediasombra es de la macro-zona: su shade SET llega por acá
static char topicAckZona[96];
static char topicErrores[96];

// ----------------------------------------------------------------------------
//  Comando de zona con actuador: la mediasombra es de la macro-zona (una por zona). Se encola como cualquier
//  comando, con sector CONTRATO_SECTOR_ZONA: su ack sale por el tópico de zona. Sólo la mediasombra es de la zona.
// ----------------------------------------------------------------------------
static void encolarComandoDeZona(byte *payload, unsigned int length)
{
  Comando cmd = {};
  cmd.sector = CONTRATO_SECTOR_ZONA;
  bool ok = util_json::parsearComando((const char *)payload, length, cmd);
  if (!ok || cmd.actuador != TipoActuador::MEDIASOMBRA)
  {
    Ack err = {};
    strlcpy(err.commandId, cmd.commandId, sizeof(err.commandId));
    err.sector = CONTRATO_SECTOR_ZONA;
    err.status = EstadoAck::ERROR;
    err.targetPct = -1;
    strlcpy(err.tipoDetalle, "comando_invalido", sizeof(err.tipoDetalle));
    xQueueSend(sys.ack, &err, 0);
    return;
  }
  if (xQueueSend(sys.comandos, &cmd, 0) != pdTRUE)
  {
    Serial.println("[main] Cola de comandos llena; comando de zona descartado");
  }
}

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

  if (strcmp(topic, topicComandoZona) == 0)
  {
    // Este nodo no sensa: del comando de zona sólo le toca el de la mediasombra (con "actuador").
    char commandId[40];
    if (util_json::parsearComandoZona((const char *)payload, length, commandId, sizeof(commandId)) ==
        util_json::ResultadoComandoZona::ACTUADOR)
    {
      encolarComandoDeZona(payload, length);
    }
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
//  Publicación de acks y eventos del riel
// ----------------------------------------------------------------------------
static bool publicarAck(const Ack &a)
{
  char buf[256];
  size_t escritos = util_json::serializarAck(a, buf, sizeof(buf));
  buf[escritos] = '\0';
  if (a.sector == CONTRATO_SECTOR_ZONA)
  {
    // La mediasombra es de la macro-zona: su ack va por el tópico de zona.
    if (!net_mqtt::publicar(topicAckZona, buf, QOS_ACK))
    {
      return false;
    }
    Serial.printf("[main] Ack de zona publicado: %s\n", buf);
    return true;
  }
  int sector = (a.sector >= 1 && a.sector <= CANT_SECTORES) ? a.sector : 1;
  if (!net_mqtt::publicar(topicAck[sector], buf, QOS_ACK))
  {
    return false;
  }
  Serial.printf("[main] Ack sector %d publicado: %s\n", sector, buf);
  return true;
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

// Publica los errores del nodo (S-05) en orden. Uno que no se pudo publicar queda primero en la cola y se reintenta en la
// próxima vuelta (p.ej. al reconectar): la falla se informa una sola vez, no puede perderse.
static void publicarErrores(bool mqttOk)
{
  ErrorNodo e;
  while (mqttOk && xQueuePeek(sys.error, &e, 0) == pdTRUE)
  {
    char mac[18];
    net_wifi::obtenerMac(mac, sizeof(mac));
    const char *sectorId = (e.sector >= 1 && e.sector <= CANT_SECTORES) ? contratoSectorId(e.sector) : "";
    char buf[192];
    size_t escritos = util_json::serializarError(e, mac, sectorId, (uint32_t)(millis() / 1000), buf, sizeof(buf));
    buf[escritos] = '\0';
    if (!net_mqtt::publicar(topicErrores, buf, QOS_ERROR))
    {
      break;
    }
    xQueueReceive(sys.error, &e, 0);
    Serial.printf("[main] Error del nodo publicado: %s\n", buf);
  }
}

// ----------------------------------------------------------------------------
//  task_red: conectividad + publicación de acks y eventos del riel
// ----------------------------------------------------------------------------
static void taskRed(void *pv)
{
  for (;;)
  {
    net_wifi::reconectarSiNecesario();
    bool mqttOk = net_mqtt::reconectarSiNecesario();
    net_mqtt::loop();

    // Acks producidos por task_actuacion (y por el callback, si el comando era inválido).
    // Un ack que no se pudo publicar (red caída) queda primero en la cola y se reintenta en la próxima vuelta:
    // con hasta tres acks por comando (en_cola, iniciado, final) no conviene perderlos (add-cola-actuadores-nodo).
    Ack a;
    while (xQueuePeek(sys.ack, &a, 0) == pdTRUE)
    {
      if (!mqttOk)
      {
        // Sin red los intermedios (informativos) se descartan para que los finales no queden detrás de una cola vieja.
        if (a.status != EstadoAck::EN_CURSO)
        {
          break;
        }
        xQueueReceive(sys.ack, &a, 0);
        continue;
      }
      if (!publicarAck(a))
      {
        break;
      }
      xQueueReceive(sys.ack, &a, 0);
    }

    // Eventos del riel.
    publicarEventosRiel(mqttOk);

    // Errores del nodo (S-05).
    publicarErrores(mqttOk);

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

  // Topics de comando (suscripción) y ack (publicación) de cada sector.
  for (int s = 1; s <= CANT_SECTORES; s++)
  {
    contratoTopicComando(topicComando[s], sizeof(topicComando[s]), s);
    contratoTopicAck(topicAck[s], sizeof(topicAck[s]), s);
  }
  contratoTopicErrores(topicErrores, sizeof(topicErrores));
  contratoTopicComandoZona(topicComandoZona, sizeof(topicComandoZona));
  contratoTopicAckZona(topicAckZona, sizeof(topicAckZona));

  // Recursos FreeRTOS (este nodo consume comandos y produce acks y eventos del riel).
  sys.telemetria = nullptr;
  sys.comandos = xQueueCreate(10, sizeof(Comando));
  sys.ack = xQueueCreate(16, sizeof(Ack));   // hasta 3 por comando: en_cola, iniciado y final
  sys.comandoRiel = xQueueCreate(1, sizeof(ComandoRiel));
  sys.eventoRiel = xQueueCreate(8, sizeof(EventoRiel));
  sys.error = xQueueCreate(4, sizeof(ErrorNodo));

  // Infraestructura común + suscripciones.
  net_wifi::iniciar();
  net_mqtt::iniciar(onMensajeMqtt);
  for (int s = 1; s <= CANT_SECTORES; s++)
  {
    net_mqtt::suscribir(topicComando[s], QOS_COMANDO);
  }
  net_mqtt::suscribir(TOPIC_RIEL_COMANDO, QOS_RIEL_COMANDO);
  net_mqtt::suscribir(topicComandoZona, QOS_COMANDO_ZONA);   // shade SET de la mediasombra (4ª: MAX_SUSCRIPCIONES)

  // Drivers y tareas de actuación.
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
