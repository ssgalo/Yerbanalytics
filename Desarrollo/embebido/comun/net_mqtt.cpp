#include "net_mqtt.h"
#include "config.h"
#include <WiFi.h>

namespace net_mqtt {

static WiFiClient   wifiClient;
static PubSubClient cliente(wifiClient);

// Topics a re-suscribir tras cada reconexión.
static const int MAX_SUSCRIPCIONES = 4;
struct Suscripcion { const char* topic; uint8_t qos; };
static Suscripcion suscripciones[MAX_SUSCRIPCIONES];
static int cantSuscripciones = 0;

static unsigned long ultimoIntento = 0;
static const unsigned long INTERVALO_REINTENTO_MS = 3000;

void iniciar(MQTT_CALLBACK_SIGNATURE) {
  cliente.setServer(MQTT_HOST, MQTT_PORT);
  cliente.setBufferSize(MQTT_BUFFER_SIZE);
  cliente.setCallback(callback);
}

void suscribir(const char* topic, uint8_t qos) {
  if (cantSuscripciones < MAX_SUSCRIPCIONES) {
    suscripciones[cantSuscripciones++] = { topic, qos };
    if (cliente.connected()) {
      cliente.subscribe(topic, qos);
    }
  }
}

bool conectado() {
  return cliente.connected();
}

bool reconectarSiNecesario() {
  if (cliente.connected()) {
    return true;
  }
  if (WiFi.status() != WL_CONNECTED) {
    return false;  // sin WiFi no tiene sentido intentar
  }
  unsigned long ahora = millis();
  if (ahora - ultimoIntento < INTERVALO_REINTENTO_MS) {
    return false;
  }
  ultimoIntento = ahora;

  // clientId único para evitar colisiones en el broker.
  char clientId[64];
  snprintf(clientId, sizeof(clientId), "%s-%08X", MQTT_CLIENT_ID_BASE, (uint32_t) esp_random());

  Serial.printf("[mqtt] Conectando como %s...\n", clientId);
  if (cliente.connect(clientId)) {
    Serial.println("[mqtt] Conectado");
    for (int i = 0; i < cantSuscripciones; i++) {
      cliente.subscribe(suscripciones[i].topic, suscripciones[i].qos);
      Serial.printf("[mqtt] Suscrito a %s (QoS %d)\n", suscripciones[i].topic, suscripciones[i].qos);
    }
    return true;
  }
  Serial.printf("[mqtt] Falló, rc=%d\n", cliente.state());
  return false;
}

bool publicar(const char* topic, const char* payload, uint8_t qos) {
  // PubSubClient no expone QoS 1/2 en publish (solo QoS 0/retain). El QoS del
  // canal de comando (QoS 2) lo garantiza el publisher del backend; acá se
  // documenta el nivel deseado y se publica best-effort. Ver README.
  (void) qos;
  if (!cliente.connected()) {
    return false;
  }
  return cliente.publish(topic, payload);
}

void loop() {
  cliente.loop();
}

}  // namespace net_mqtt
